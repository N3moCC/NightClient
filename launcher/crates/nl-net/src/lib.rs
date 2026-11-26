use futures::stream::{FuturesUnordered, StreamExt};
use nl_platform::{check_disk_space, DiskSpaceError};
use reqwest::header::{ETAG, IF_NONE_MATCH, RANGE};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use sha1::Sha1;
use sha2::{Digest, Sha256};
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use thiserror::Error;
use tokio::io::AsyncWriteExt;
use tokio::sync::Semaphore;
use tokio_util::sync::CancellationToken;

pub const USER_AGENT: &str = concat!(
    "NightClientLauncher/",
    env!("CARGO_PKG_VERSION"),
    " (by N3moCC; +https://github.com/N3moCC/NightClient)"
);

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct NetConfig {
    pub proxy_url: Option<String>,
    pub max_concurrency: usize,
    pub stall_timeout_secs: u64,
    pub connect_timeout_secs: u64,
}

impl Default for NetConfig {
    fn default() -> Self {
        Self {
            proxy_url: None,
            max_concurrency: 10,
            stall_timeout_secs: 30,
            connect_timeout_secs: 15,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ExpectedHash {
    Sha1(String),
    Sha256(String),
    Md5(String),
}

#[derive(Debug, Clone)]
pub struct DownloadItem {
    pub urls: Vec<String>,
    pub dest: PathBuf,
    pub expected_hash: Option<ExpectedHash>,
    pub expected_size: Option<u64>,
    pub label: String,
}

impl DownloadItem {
    pub fn single(url: impl Into<String>, dest: PathBuf) -> Self {
        let u = url.into();
        let label = dest
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("file")
            .to_string();
        Self {
            urls: vec![u],
            dest,
            expected_hash: None,
            expected_size: None,
            label,
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct BatchProgress {
    pub completed_files: usize,
    pub total_files: usize,
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub bytes_per_sec: u64,
    pub current_label: String,
}

#[derive(Debug, Error)]
pub enum NetError {
    #[error("network request failed: {0}")]
    Http(#[from] reqwest::Error),
    #[error("http status {status} for {url}")]
    HttpStatus { status: u16, url: String },
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("json parse error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("checksum mismatch for {path}: expected {expected}, got {actual}")]
    HashMismatch {
        path: String,
        expected: String,
        actual: String,
    },
    #[error("download stalled after {secs}s for {url}")]
    Stalled { url: String, secs: u64 },
    #[error("operation cancelled")]
    Cancelled,
    #[error("{0}")]
    DiskSpace(#[from] DiskSpaceError),
    #[error("no download URLs provided for {0}")]
    NoUrls(String),
    #[error("invalid proxy configuration: {0}")]
    InvalidProxy(String),
}

#[derive(Clone)]
pub struct NetClient {
    client: reqwest::Client,
    config: NetConfig,
}

impl NetClient {
    pub fn new(config: NetConfig) -> Result<Self, NetError> {
        let mut builder = reqwest::Client::builder()
            .user_agent(USER_AGENT)
            .connect_timeout(Duration::from_secs(config.connect_timeout_secs.max(5)))
            .pool_max_idle_per_host(16);

        if let Some(proxy_str) = config
            .proxy_url
            .as_deref()
            .map(str::trim)
            .filter(|s| !s.is_empty())
        {
            let proxy = reqwest::Proxy::all(proxy_str)
                .map_err(|e| NetError::InvalidProxy(e.to_string()))?;
            builder = builder.proxy(proxy);
        }

        let client = builder.build()?;
        Ok(Self { client, config })
    }

    pub fn inner(&self) -> &reqwest::Client {
        &self.client
    }

    pub fn config(&self) -> &NetConfig {
        &self.config
    }

    pub async fn fetch_json<T: DeserializeOwned>(
        &self,
        url: &str,
        cancel: &CancellationToken,
    ) -> Result<T, NetError> {
        let bytes = self.fetch_bytes(url, cancel).await?;
        Ok(serde_json::from_slice(&bytes)?)
    }

    pub async fn fetch_bytes(
        &self,
        url: &str,
        cancel: &CancellationToken,
    ) -> Result<Vec<u8>, NetError> {
        let mut last_err = None;
        for attempt in 0..3u32 {
            if cancel.is_cancelled() {
                return Err(NetError::Cancelled);
            }
            let fut = self.client.get(url).send();
            let resp = tokio::select! {
                _ = cancel.cancelled() => return Err(NetError::Cancelled),
                res = fut => res,
            };
            match resp {
                Ok(r) if r.status().is_success() => {
                    let bytes_fut = r.bytes();
                    let body = tokio::select! {
                        _ = cancel.cancelled() => return Err(NetError::Cancelled),
                        b = bytes_fut => b?,
                    };
                    return Ok(body.to_vec());
                }
                Ok(r) => {
                    let status = r.status().as_u16();
                    last_err = Some(NetError::HttpStatus {
                        status,
                        url: url.to_string(),
                    });
                    if (400..500).contains(&status) && status != 429 && status != 408 {
                        break;
                    }
                }
                Err(e) => {
                    last_err = Some(NetError::Http(e));
                }
            }
            let backoff = Duration::from_millis(350 * (1u64 << attempt));
            tokio::select! {
                _ = cancel.cancelled() => return Err(NetError::Cancelled),
                _ = tokio::time::sleep(backoff) => {}
            }
        }
        Err(last_err.unwrap_or(NetError::Cancelled))
    }

    pub async fn fetch_json_cached<T: DeserializeOwned>(
        &self,
        url: &str,
        cache_dir: &Path,
        cancel: &CancellationToken,
    ) -> Result<T, NetError> {
        let key = short_sha256(url.as_bytes());
        let body_path = cache_dir.join(format!("{key}.json"));
        let etag_path = cache_dir.join(format!("{key}.etag"));
        let existing_etag = fs::read_to_string(&etag_path).ok();

        let mut req = self.client.get(url);
        if let Some(etag) = existing_etag.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
            req = req.header(IF_NONE_MATCH, etag);
        }

        let send_res = tokio::select! {
            _ = cancel.cancelled() => return Err(NetError::Cancelled),
            r = req.send() => r,
        };

        match send_res {
            Ok(resp) if resp.status() == reqwest::StatusCode::NOT_MODIFIED => {
                if let Ok(cached) = fs::read(&body_path)
                    && let Ok(parsed) = serde_json::from_slice::<T>(&cached)
                {
                    return Ok(parsed);
                }
            }
            Ok(resp) if resp.status().is_success() => {
                let new_etag = resp
                    .headers()
                    .get(ETAG)
                    .and_then(|v| v.to_str().ok())
                    .map(str::to_string);
                let bytes = tokio::select! {
                    _ = cancel.cancelled() => return Err(NetError::Cancelled),
                    b = resp.bytes() => b?,
                };
                let parsed: T = serde_json::from_slice(&bytes)?;
                let _ = fs::create_dir_all(cache_dir);
                let _ = nl_platform::atomic_write(&body_path, &bytes);
                if let Some(etag) = new_etag {
                    let _ = nl_platform::atomic_write(&etag_path, etag.as_bytes());
                }
                return Ok(parsed);
            }
            Ok(resp) => {
                let status = resp.status().as_u16();
                if let Ok(cached) = fs::read(&body_path)
                    && let Ok(parsed) = serde_json::from_slice::<T>(&cached)
                {
                    tracing::warn!(
                        "HTTP {status} for {url}; serving cached copy from {}",
                        body_path.display()
                    );
                    return Ok(parsed);
                }
                return Err(NetError::HttpStatus {
                    status,
                    url: url.to_string(),
                });
            }
            Err(err) => {
                if let Ok(cached) = fs::read(&body_path)
                    && let Ok(parsed) = serde_json::from_slice::<T>(&cached)
                {
                    tracing::warn!("Offline or network error for {url} ({err}); using cache");
                    return Ok(parsed);
                }
                return Err(NetError::Http(err));
            }
        }

        self.fetch_json(url, cancel).await
    }

    pub async fn download_file<F>(
        &self,
        item: &DownloadItem,
        cancel: &CancellationToken,
        mut on_delta: F,
    ) -> Result<u64, NetError>
    where
        F: FnMut(u64),
    {
        if item.urls.is_empty() {
            return Err(NetError::NoUrls(item.dest.display().to_string()));
        }

        if verify_file_sync(&item.dest, item.expected_hash.as_ref(), item.expected_size) {
            let size = fs::metadata(&item.dest).map(|m| m.len()).unwrap_or(0);
            if size > 0 {
                on_delta(size);
            }
            return Ok(size);
        }

        if let Some(parent) = item.dest.parent() {
            fs::create_dir_all(parent)?;
        }
        if let Some(needed) = item.expected_size {
            check_disk_space(&item.dest, needed.saturating_add(16 * 1024 * 1024))?;
        }

        let part_path = part_path_for(&item.dest);
        let stall_duration = Duration::from_secs(self.config.stall_timeout_secs.max(5));
        let mut last_err = None;

        for (url_idx, url) in item.urls.iter().enumerate() {
            if url_idx > 0 {
                let _ = fs::remove_file(&part_path);
            }
            for attempt in 0..3u32 {
                if cancel.is_cancelled() {
                    return Err(NetError::Cancelled);
                }

                let existing_len = fs::metadata(&part_path).map(|m| m.len()).unwrap_or(0);
                let mut req = self.client.get(url);
                if existing_len > 0 {
                    req = req.header(RANGE, format!("bytes={existing_len}-"));
                }

                let resp_res = tokio::select! {
                    _ = cancel.cancelled() => return Err(NetError::Cancelled),
                    r = req.send() => r,
                };

                let resp = match resp_res {
                    Ok(r) => r,
                    Err(e) => {
                        last_err = Some(NetError::Http(e));
                        sleep_backoff(attempt, cancel).await?;
                        continue;
                    }
                };

                let status = resp.status();
                let (mut file, mut written) = if status == reqwest::StatusCode::PARTIAL_CONTENT
                    && existing_len > 0
                {
                    let f = tokio::fs::OpenOptions::new()
                        .append(true)
                        .open(&part_path)
                        .await?;
                    (f, existing_len)
                } else if status.is_success() {
                    let f = tokio::fs::File::create(&part_path).await?;
                    (f, 0u64)
                } else if status == reqwest::StatusCode::RANGE_NOT_SATISFIABLE {
                    let _ = fs::remove_file(&part_path);
                    continue;
                } else {
                    let code = status.as_u16();
                    last_err = Some(NetError::HttpStatus {
                        status: code,
                        url: url.clone(),
                    });
                    if (400..500).contains(&code) && code != 429 && code != 408 {
                        break;
                    }
                    sleep_backoff(attempt, cancel).await?;
                    continue;
                };

                let mut stream = resp.bytes_stream();
                let mut stream_err: Option<NetError> = None;

                loop {
                    let next_chunk = tokio::select! {
                        _ = cancel.cancelled() => {
                            stream_err = Some(NetError::Cancelled);
                            break;
                        }
                        chunk = tokio::time::timeout(stall_duration, stream.next()) => chunk,
                    };

                    match next_chunk {
                        Err(_) => {
                            stream_err = Some(NetError::Stalled {
                                url: url.clone(),
                                secs: stall_duration.as_secs(),
                            });
                            break;
                        }
                        Ok(None) => break,
                        Ok(Some(Err(e))) => {
                            stream_err = Some(NetError::Http(e));
                            break;
                        }
                        Ok(Some(Ok(bytes))) => {
                            file.write_all(&bytes).await?;
                            let len = bytes.len() as u64;
                            written = written.saturating_add(len);
                            on_delta(len);
                        }
                    }
                }

                if let Some(err) = stream_err {
                    let _ = file.flush().await;
                    if matches!(err, NetError::Cancelled) {
                        return Err(NetError::Cancelled);
                    }
                    last_err = Some(err);
                    sleep_backoff(attempt, cancel).await?;
                    continue;
                }

                file.flush().await?;
                drop(file);

                if let Some(ref expected) = item.expected_hash {
                    let actual = compute_hash(&part_path, expected)?;
                    let expected_hex = match expected {
                        ExpectedHash::Sha1(h)
                        | ExpectedHash::Sha256(h)
                        | ExpectedHash::Md5(h) => h,
                    };
                    if !actual.eq_ignore_ascii_case(expected_hex) {
                        let _ = fs::remove_file(&part_path);
                        last_err = Some(NetError::HashMismatch {
                            path: item.dest.display().to_string(),
                            expected: expected_hex.clone(),
                            actual,
                        });
                        sleep_backoff(attempt, cancel).await?;
                        continue;
                    }
                } else if let Some(exp_size) = item.expected_size
                    && written != exp_size
                {
                    let _ = fs::remove_file(&part_path);
                    last_err = Some(NetError::HashMismatch {
                        path: item.dest.display().to_string(),
                        expected: format!("{exp_size} bytes"),
                        actual: format!("{written} bytes"),
                    });
                    sleep_backoff(attempt, cancel).await?;
                    continue;
                }

                #[cfg(target_os = "windows")]
                if item.dest.exists() {
                    let _ = fs::remove_file(&item.dest);
                }
                fs::rename(&part_path, &item.dest)?;
                return Ok(written);
            }
        }

        Err(last_err.unwrap_or_else(|| NetError::NoUrls(item.dest.display().to_string())))
    }

    pub async fn download_batch<F>(
        &self,
        mut items: Vec<DownloadItem>,
        cancel: &CancellationToken,
        on_progress: F,
    ) -> Result<(), NetError>
    where
        F: Fn(BatchProgress) + Send + Sync + 'static,
    {
        let mut seen = std::collections::HashSet::with_capacity(items.len());
        items.retain(|i| seen.insert(i.dest.clone()));
        if items.is_empty() {
            return Ok(());
        }

        let total_files = items.len();
        let total_bytes: u64 = items
            .iter()
            .map(|i| i.expected_size.unwrap_or(0))
            .sum();

        let semaphore = Arc::new(Semaphore::new(self.config.max_concurrency.clamp(1, 32)));
        let completed_files = Arc::new(AtomicUsize::new(0));
        let downloaded_bytes = Arc::new(AtomicU64::new(0));
        let started_at = Instant::now();
        let on_progress = Arc::new(on_progress);

        let mut tasks = FuturesUnordered::new();
        for item in items {
            let client = self.clone();
            let sem = Arc::clone(&semaphore);
            let done_files = Arc::clone(&completed_files);
            let dl_bytes = Arc::clone(&downloaded_bytes);
            let cb = Arc::clone(&on_progress);
            let token = cancel.clone();

            tasks.push(async move {
                let _permit = tokio::select! {
                    _ = token.cancelled() => return Err(NetError::Cancelled),
                    p = sem.acquire_owned() => p.map_err(|_| NetError::Cancelled)?,
                };
                let label = item.label.clone();
                let dl_ref = Arc::clone(&dl_bytes);
                let cb_ref = Arc::clone(&cb);
                let done_ref = Arc::clone(&done_files);

                client
                    .download_file(&item, &token, move |delta| {
                        let so_far = dl_ref.fetch_add(delta, Ordering::Relaxed) + delta;
                        let elapsed = started_at.elapsed().as_secs_f64().max(0.05);
                        cb_ref(BatchProgress {
                            completed_files: done_ref.load(Ordering::Relaxed),
                            total_files,
                            downloaded_bytes: so_far,
                            total_bytes: total_bytes.max(so_far),
                            bytes_per_sec: (so_far as f64 / elapsed) as u64,
                            current_label: label.clone(),
                        });
                    })
                    .await?;

                let finished = done_files.fetch_add(1, Ordering::Relaxed) + 1;
                let so_far = dl_bytes.load(Ordering::Relaxed);
                let elapsed = started_at.elapsed().as_secs_f64().max(0.05);
                cb(BatchProgress {
                    completed_files: finished,
                    total_files,
                    downloaded_bytes: so_far,
                    total_bytes: total_bytes.max(so_far),
                    bytes_per_sec: (so_far as f64 / elapsed) as u64,
                    current_label: item.label,
                });
                Ok::<(), NetError>(())
            });
        }

        while let Some(res) = tasks.next().await {
            res?;
        }
        Ok(())
    }
}

pub fn verify_file_sync(
    path: &Path,
    expected_hash: Option<&ExpectedHash>,
    expected_size: Option<u64>,
) -> bool {
    let Ok(meta) = fs::metadata(path) else {
        return false;
    };
    if !meta.is_file() {
        return false;
    }
    if let Some(size) = expected_size
        && meta.len() != size
    {
        return false;
    }
    if let Some(expected) = expected_hash {
        let Ok(actual) = compute_hash(path, expected) else {
            return false;
        };
        let target = match expected {
            ExpectedHash::Sha1(h) | ExpectedHash::Sha256(h) | ExpectedHash::Md5(h) => h,
        };
        return actual.eq_ignore_ascii_case(target);
    }
    meta.len() > 0
}

pub fn compute_sha1_file(path: &Path) -> std::io::Result<String> {
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha1::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

pub fn compute_sha256_file(path: &Path) -> std::io::Result<String> {
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

fn compute_hash(path: &Path, kind: &ExpectedHash) -> std::io::Result<String> {
    match kind {
        ExpectedHash::Sha1(_) => compute_sha1_file(path),
        ExpectedHash::Sha256(_) => compute_sha256_file(path),
        ExpectedHash::Md5(_) => {
            let mut file = fs::File::open(path)?;
            let mut hasher = md5::Md5::new();
            let mut buf = [0u8; 64 * 1024];
            loop {
                let n = file.read(&mut buf)?;
                if n == 0 {
                    break;
                }
                hasher.update(&buf[..n]);
            }
            Ok(hex::encode(hasher.finalize()))
        }
    }
}

fn part_path_for(dest: &Path) -> PathBuf {
    let name = dest
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("download");
    dest.with_file_name(format!("{name}.part"))
}

fn short_sha256(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    hex::encode(&digest[..12])
}

async fn sleep_backoff(attempt: u32, cancel: &CancellationToken) -> Result<(), NetError> {
    let delay = Duration::from_millis(400 * (1u64 << attempt));
    tokio::select! {
        _ = cancel.cancelled() => Err(NetError::Cancelled),
        _ = tokio::time::sleep(delay) => Ok(()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verifies_sha1_and_sha256() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("sample.bin");
        fs::write(&path, b"nightclient").unwrap();
        let sha1 = compute_sha1_file(&path).unwrap();
        let sha256 = compute_sha256_file(&path).unwrap();
        assert!(verify_file_sync(&path, Some(&ExpectedHash::Sha1(sha1)), Some(11)));
        assert!(verify_file_sync(
            &path,
            Some(&ExpectedHash::Sha256(sha256)),
            Some(11)
        ));
        assert!(!verify_file_sync(
            &path,
            Some(&ExpectedHash::Sha1("0000".into())),
            Some(11)
        ));
    }
}
