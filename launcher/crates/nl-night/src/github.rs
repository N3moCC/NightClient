use crate::manifest::{
    NightClientChannel, NightClientManifest, NightClientRelease, default_embedded_manifest,
};
use nl_net::{DownloadItem, ExpectedHash, NetClient, NetError};
use nl_platform::{LauncherPaths, write_json_atomic};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap};
use std::path::{Path, PathBuf};
use thiserror::Error;
use tokio_util::sync::CancellationToken;

pub const GITHUB_REPO: &str = "N3moCC/NightClient";
pub const GITHUB_RELEASES_API: &str =
    "https://api.github.com/repos/N3moCC/NightClient/releases?per_page=15";
pub const GITHUB_RELEASES_PAGE: &str = "https://github.com/N3moCC/NightClient/releases";

#[derive(Debug, Clone, Deserialize)]
pub struct GitHubRelease {
    pub tag_name: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub body: Option<String>,
    pub html_url: String,
    #[serde(default)]
    pub draft: bool,
    #[serde(default)]
    pub prerelease: bool,
    #[serde(default)]
    pub published_at: Option<String>,
    #[serde(default)]
    pub assets: Vec<GitHubAsset>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GitHubAsset {
    pub name: String,
    pub browser_download_url: String,
    #[serde(default)]
    pub size: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct LauncherUpdateInfo {
    pub current_version: String,
    pub latest_version: String,
    pub tag_name: String,
    pub title: String,
    pub changelog: String,
    pub published_at: String,
    pub html_url: String,
    pub prerelease: bool,
    pub download_url: Option<String>,
    pub asset_name: Option<String>,
    pub asset_size: Option<u64>,
    pub asset_sha256: Option<String>,
    pub is_update_available: bool,
}

#[derive(Debug, Clone)]
pub struct GitHubReleaseBundle {
    pub manifest: NightClientManifest,
    pub launcher_update: Option<LauncherUpdateInfo>,
    pub fetched_online: bool,
}

#[derive(Debug, Error)]
pub enum LauncherUpdateError {
    #[error("network error: {0}")]
    Net(#[from] NetError),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("no downloadable binary for this platform in release {tag}")]
    MissingPlatformAsset { tag: String },
    #[error("cannot write update next to executable ({path}); download manually from GitHub")]
    NotWritable { path: String },
}

pub async fn fetch_github_releases_bundle(
    net: &NetClient,
    paths: &LauncherPaths,
    channel: NightClientChannel,
    current_launcher_version: &str,
    cancel: &CancellationToken,
) -> GitHubReleaseBundle {
    match net
        .fetch_json_cached::<Vec<GitHubRelease>>(
            GITHUB_RELEASES_API,
            &paths.http_cache_dir(),
            cancel,
        )
        .await
    {
        Ok(releases) if !releases.is_empty() => {
            let mut checksums_by_tag: HashMap<String, HashMap<String, String>> = HashMap::new();
            let stable_rel = select_release(&releases, NightClientChannel::Stable);
            let beta_rel = select_release(&releases, NightClientChannel::Beta);

            let mut tags_to_hash: Vec<&GitHubRelease> = Vec::new();
            if let Some(r) = stable_rel {
                tags_to_hash.push(r);
            }
            if let Some(r) = beta_rel
                && tags_to_hash.iter().all(|x| x.tag_name != r.tag_name)
            {
                tags_to_hash.push(r);
            }

            for rel in tags_to_hash {
                if let Some(sums_asset) = rel
                    .assets
                    .iter()
                    .find(|a| a.name.eq_ignore_ascii_case("SHA256SUMS.txt"))
                    && let Ok(bytes) = net
                        .fetch_bytes(&sums_asset.browser_download_url, cancel)
                        .await
                    && let Ok(text) = std::str::from_utf8(&bytes)
                {
                    checksums_by_tag.insert(rel.tag_name.clone(), parse_sha256sums(text));
                }
            }

            let manifest = build_manifest_from_releases(&releases, &checksums_by_tag);
            let _ = write_json_atomic(&paths.nightclient_manifest_cache(), &manifest);

            let active_rel = select_release(&releases, channel).or(stable_rel).or(beta_rel);
            let launcher_update = active_rel.map(|rel| {
                let hashes = checksums_by_tag.get(&rel.tag_name);
                build_launcher_update_info(rel, hashes, current_launcher_version)
            });

            GitHubReleaseBundle {
                manifest,
                launcher_update,
                fetched_online: true,
            }
        }
        _ => {
            let manifest = nl_platform::read_json_with_backup::<NightClientManifest>(
                &paths.nightclient_manifest_cache(),
            )
            .unwrap_or_else(default_embedded_manifest);
            GitHubReleaseBundle {
                manifest,
                launcher_update: None,
                fetched_online: false,
            }
        }
    }
}

pub fn select_release(
    releases: &[GitHubRelease],
    channel: NightClientChannel,
) -> Option<&GitHubRelease> {
    match channel {
        NightClientChannel::Stable => releases.iter().find(|r| !r.draft && !r.prerelease),
        NightClientChannel::Beta => releases.iter().find(|r| !r.draft),
    }
}

pub fn build_manifest_from_releases(
    releases: &[GitHubRelease],
    checksums_by_tag: &HashMap<String, HashMap<String, String>>,
) -> NightClientManifest {
    let base = default_embedded_manifest();
    let template = base.release(NightClientChannel::Stable).cloned();
    let mut channels: BTreeMap<String, NightClientRelease> = BTreeMap::new();

    if let Some(rel) = select_release(releases, NightClientChannel::Stable)
        && let Some(entry) = release_to_mod_entry(
            rel,
            checksums_by_tag.get(&rel.tag_name),
            template.as_ref(),
        )
    {
        channels.insert("stable".to_string(), entry);
    }

    if let Some(rel) = select_release(releases, NightClientChannel::Beta)
        && let Some(entry) = release_to_mod_entry(
            rel,
            checksums_by_tag.get(&rel.tag_name),
            template.as_ref(),
        )
    {
        channels.insert("beta".to_string(), entry);
    }

    if channels.is_empty() {
        return base;
    }

    if !channels.contains_key("stable")
        && let Some(beta) = channels.get("beta").cloned()
    {
        channels.insert("stable".to_string(), beta);
    }

    NightClientManifest {
        schema_version: 1,
        channels,
    }
}

fn release_to_mod_entry(
    rel: &GitHubRelease,
    checksums: Option<&HashMap<String, String>>,
    template: Option<&NightClientRelease>,
) -> Option<NightClientRelease> {
    let version = strip_v_prefix(&rel.tag_name).to_string();
    let jar_asset = pick_mod_jar_asset(&rel.assets);
    let (jar_url, sha256, size) = match jar_asset {
        Some(asset) => (
            asset.browser_download_url.clone(),
            checksums.and_then(|m| m.get(&asset.name).cloned()),
            (asset.size > 0).then_some(asset.size),
        ),
        None => (String::new(), None, None),
    };

    let default_mc = template
        .map(|t| t.supported_mc.clone())
        .unwrap_or_else(|| {
            default_embedded_manifest()
                .release(NightClientChannel::Stable)
                .map(|r| r.supported_mc.clone())
                .unwrap_or_default()
        });
    let min_fabric = template
        .map(|t| t.min_fabric_loader.clone())
        .unwrap_or_else(|| "0.19.5".to_string());

    Some(NightClientRelease {
        version,
        jar_url,
        mirror_urls: Vec::new(),
        sha256,
        size,
        signature_ed25519: None,
        min_fabric_loader: min_fabric,
        supported_mc: default_mc,
        known_conflicts: Vec::new(),
        changelog: rel.body.clone().unwrap_or_default(),
    })
}

pub fn pick_mod_jar_asset(assets: &[GitHubAsset]) -> Option<&GitHubAsset> {
    let candidates: Vec<&GitHubAsset> = assets
        .iter()
        .filter(|a| {
            let lower = a.name.to_ascii_lowercase();
            lower.ends_with(".jar")
                && lower.contains("nightclient")
                && !lower.contains("sources")
                && !lower.contains("javadoc")
                && !lower.contains("+mc")
        })
        .collect();

    if let Some(full) = candidates.iter().find(|a| {
        let lower = a.name.to_ascii_lowercase();
        !lower.contains("no-native")
            && !lower.contains("nonative")
            && !lower.contains("pure-java")
            && !lower.contains("store")
    }) {
        return Some(*full);
    }

    candidates.first().copied()
}

pub fn current_launcher_asset_name() -> &'static str {
    #[cfg(all(target_os = "linux", target_arch = "x86_64"))]
    {
        "nightclient-launcher-linux-x64"
    }
    #[cfg(all(target_os = "windows", target_arch = "x86_64"))]
    {
        "nightclient-launcher-windows-x64.exe"
    }
    #[cfg(all(target_os = "macos", target_arch = "aarch64"))]
    {
        "nightclient-launcher-macos-arm64"
    }
    #[cfg(all(target_os = "macos", target_arch = "x86_64"))]
    {
        "nightclient-launcher-macos-x64"
    }
    #[cfg(not(any(
        all(target_os = "linux", target_arch = "x86_64"),
        all(target_os = "windows", target_arch = "x86_64"),
        all(target_os = "macos", target_arch = "aarch64"),
        all(target_os = "macos", target_arch = "x86_64"),
    )))]
    {
        ""
    }
}

pub fn pick_launcher_asset<'a>(
    assets: &'a [GitHubAsset],
    target_name: &str,
) -> Option<&'a GitHubAsset> {
    if target_name.is_empty() {
        return None;
    }
    assets
        .iter()
        .find(|a| a.name.eq_ignore_ascii_case(target_name))
}

pub fn build_launcher_update_info(
    rel: &GitHubRelease,
    checksums: Option<&HashMap<String, String>>,
    current_launcher_version: &str,
) -> LauncherUpdateInfo {
    let latest_version = strip_v_prefix(&rel.tag_name).to_string();
    let title = rel
        .name
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or(&rel.tag_name)
        .to_string();
    let published_at = rel
        .published_at
        .as_deref()
        .and_then(|s| s.split('T').next())
        .unwrap_or("")
        .to_string();
    let asset = pick_launcher_asset(&rel.assets, current_launcher_asset_name());
    let (download_url, asset_name, asset_size, asset_sha256) = match asset {
        Some(a) => (
            Some(a.browser_download_url.clone()),
            Some(a.name.clone()),
            (a.size > 0).then_some(a.size),
            checksums.and_then(|m| m.get(&a.name).cloned()),
        ),
        None => (None, None, None, None),
    };
    let is_update_available = version_gt(&latest_version, current_launcher_version);

    LauncherUpdateInfo {
        current_version: current_launcher_version.to_string(),
        latest_version,
        tag_name: rel.tag_name.clone(),
        title,
        changelog: rel.body.clone().unwrap_or_default(),
        published_at,
        html_url: rel.html_url.clone(),
        prerelease: rel.prerelease,
        download_url,
        asset_name,
        asset_size,
        asset_sha256,
        is_update_available,
    }
}

pub fn parse_sha256sums(content: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let mut parts = trimmed.splitn(2, |c: char| c.is_ascii_whitespace());
        let Some(hash) = parts.next() else {
            continue;
        };
        let Some(rest) = parts.next() else {
            continue;
        };
        if hash.len() != 64 || !hash.bytes().all(|b| b.is_ascii_hexdigit()) {
            continue;
        }
        let file = rest.trim().trim_start_matches('*');
        let basename = Path::new(file)
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or(file);
        if !basename.is_empty() {
            map.insert(basename.to_string(), hash.to_ascii_lowercase());
        }
    }
    map
}

pub fn strip_v_prefix(tag: &str) -> &str {
    let trimmed = tag.trim();
    trimmed
        .strip_prefix('v')
        .or_else(|| trimmed.strip_prefix('V'))
        .unwrap_or(trimmed)
}

pub fn version_gt(candidate: &str, current: &str) -> bool {
    let parse_parts = |raw: &str| -> (Vec<u32>, Option<String>) {
        let cleaned = strip_v_prefix(raw);
        let (core, pre) = match cleaned.split_once('-') {
            Some((c, p)) => (c, Some(p.trim().to_ascii_lowercase())),
            None => (cleaned, None),
        };
        let nums: Vec<u32> = core
            .split('.')
            .filter_map(|p| p.parse::<u32>().ok())
            .collect();
        (nums, pre)
    };

    let (ca, pa) = parse_parts(candidate);
    let (cb, pb) = parse_parts(current);
    if ca.is_empty() || cb.is_empty() {
        return false;
    }

    let max_len = ca.len().max(cb.len());
    for i in 0..max_len {
        let a = ca.get(i).copied().unwrap_or(0);
        let b = cb.get(i).copied().unwrap_or(0);
        if a != b {
            return a > b;
        }
    }

    match (pa, pb) {
        (None, Some(_)) => true,
        (Some(_), None) => false,
        (Some(a), Some(b)) => a > b,
        (None, None) => false,
    }
}

pub async fn download_and_apply_launcher_update<F>(
    net: &NetClient,
    update: &LauncherUpdateInfo,
    cancel: &CancellationToken,
    mut on_progress: F,
) -> Result<PathBuf, LauncherUpdateError>
where
    F: FnMut(u64, u64),
{
    let Some(url) = update
        .download_url
        .as_deref()
        .filter(|s| !s.trim().is_empty())
    else {
        return Err(LauncherUpdateError::MissingPlatformAsset {
            tag: update.tag_name.clone(),
        });
    };

    let current_exe = std::env::current_exe()?;
    let exe_dir = current_exe
        .parent()
        .ok_or_else(|| LauncherUpdateError::NotWritable {
            path: current_exe.display().to_string(),
        })?;

    let probe = exe_dir.join(".nightclient-update-probe.tmp");
    match std::fs::File::create(&probe) {
        Ok(_) => {
            let _ = std::fs::remove_file(&probe);
        }
        Err(_) => {
            return Err(LauncherUpdateError::NotWritable {
                path: exe_dir.display().to_string(),
            });
        }
    }

    let staged = exe_dir.join(".nightclient-launcher-update.bin");
    let _ = std::fs::remove_file(&staged);
    let _ = std::fs::remove_file(staged.with_file_name(".nightclient-launcher-update.bin.part"));

    let total = update.asset_size.unwrap_or(0);
    let mut downloaded = 0u64;
    let item = DownloadItem {
        urls: vec![url.to_string()],
        dest: staged.clone(),
        expected_hash: update.asset_sha256.clone().map(ExpectedHash::Sha256),
        expected_size: update.asset_size,
        label: update
            .asset_name
            .clone()
            .unwrap_or_else(|| "nightclient-launcher".to_string()),
    };

    net.download_file(&item, cancel, |delta| {
        downloaded = downloaded.saturating_add(delta);
        on_progress(downloaded, total.max(downloaded));
    })
    .await?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mut perms = std::fs::metadata(&staged)?.permissions();
        perms.set_mode(0o755);
        std::fs::set_permissions(&staged, perms)?;
        std::fs::rename(&staged, &current_exe)?;
    }

    #[cfg(windows)]
    {
        let backup = old_binary_path(&current_exe);
        let _ = std::fs::remove_file(&backup);
        std::fs::rename(&current_exe, &backup)?;
        if let Err(err) = std::fs::rename(&staged, &current_exe) {
            let _ = std::fs::rename(&backup, &current_exe);
            return Err(LauncherUpdateError::Io(err));
        }
    }

    Ok(current_exe)
}

pub fn cleanup_stale_old_binary() {
    if let Ok(exe) = std::env::current_exe() {
        let backup = old_binary_path(&exe);
        if backup.is_file() {
            let _ = std::fs::remove_file(backup);
        }
    }
}

fn old_binary_path(exe: &Path) -> PathBuf {
    let name = exe
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("nightclient-launcher");
    exe.with_file_name(format!("{name}.old"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_releases() -> Vec<GitHubRelease> {
        vec![
            GitHubRelease {
                tag_name: "v1.5.0-beta.1".to_string(),
                name: Some("NightClient 1.5.0 Beta 1".to_string()),
                body: Some("## Beta\n- New Vulkan pass".to_string()),
                html_url: "https://github.com/N3moCC/NightClient/releases/tag/v1.5.0-beta.1"
                    .to_string(),
                draft: false,
                prerelease: true,
                published_at: Some("2026-11-25T18:00:00Z".to_string()),
                assets: vec![
                    GitHubAsset {
                        name: "NightClient-1.5.0-beta.1.jar".to_string(),
                        browser_download_url: "https://example.invalid/beta.jar".to_string(),
                        size: 12_000_000,
                    },
                    GitHubAsset {
                        name: "NightClient-1.5.0-beta.1-no-native.jar".to_string(),
                        browser_download_url: "https://example.invalid/beta-nonative.jar"
                            .to_string(),
                        size: 10_000_000,
                    },
                ],
            },
            GitHubRelease {
                tag_name: "v1.4.2".to_string(),
                name: Some("NightClient 1.4.2".to_string()),
                body: Some("## Changes\n- Added LiteAPI".to_string()),
                html_url: "https://github.com/N3moCC/NightClient/releases/tag/v1.4.2".to_string(),
                draft: false,
                prerelease: false,
                published_at: Some("2026-11-24T12:00:00Z".to_string()),
                assets: vec![
                    GitHubAsset {
                        name: "NightClient-1.4.2-no-native.jar".to_string(),
                        browser_download_url: "https://example.invalid/stable-nonative.jar"
                            .to_string(),
                        size: 10_000_000,
                    },
                    GitHubAsset {
                        name: "NightClient-1.4.2.jar".to_string(),
                        browser_download_url: "https://example.invalid/stable.jar".to_string(),
                        size: 11_500_000,
                    },
                    GitHubAsset {
                        name: "nightclient-launcher-linux-x64".to_string(),
                        browser_download_url: "https://example.invalid/launcher-linux".to_string(),
                        size: 18_000_000,
                    },
                ],
            },
        ]
    }

    #[test]
    fn parses_github_releases_for_stable_and_beta_channels() {
        let releases = sample_releases();
        let mut sums_map = HashMap::new();
        let hash_stable = "a".repeat(64);
        let mut stable_hashes = HashMap::new();
        stable_hashes.insert("NightClient-1.4.2.jar".to_string(), hash_stable.clone());
        sums_map.insert("v1.4.2".to_string(), stable_hashes);

        let manifest = build_manifest_from_releases(&releases, &sums_map);
        let stable = manifest.release(NightClientChannel::Stable).unwrap();
        assert_eq!(stable.version, "1.4.2");
        assert_eq!(stable.jar_url, "https://example.invalid/stable.jar");
        assert_eq!(stable.sha256.as_deref(), Some(hash_stable.as_str()));

        let beta = manifest.release(NightClientChannel::Beta).unwrap();
        assert_eq!(beta.version, "1.5.0-beta.1");
        assert_eq!(beta.jar_url, "https://example.invalid/beta.jar");
    }

    #[test]
    fn parses_sha256sums_correctly() {
        let h1 = "0123456789abcdef".repeat(4);
        let h2 = "fedcba9876543210".repeat(4);
        let text = format!("{h1}  NightClient-1.4.2.jar\n{h2} *nightclient-launcher-linux-x64\ninvalid line\n");
        let parsed = parse_sha256sums(&text);
        assert_eq!(parsed.get("NightClient-1.4.2.jar").map(String::as_str), Some(h1.as_str()));
        assert_eq!(
            parsed.get("nightclient-launcher-linux-x64").map(String::as_str),
            Some(h2.as_str())
        );
        assert_eq!(parsed.len(), 2);
    }

    #[test]
    fn compares_semver_and_prerelease_versions_correctly() {
        assert!(version_gt("1.0.0", "0.1.0"));
        assert!(version_gt("v0.2.0", "0.1.9"));
        assert!(version_gt("1.0.0", "1.0.0-beta.1"));
        assert!(!version_gt("1.0.0-beta.1", "1.0.0"));
        assert!(version_gt("1.0.0-beta.2", "1.0.0-beta.1"));
        assert!(!version_gt("0.1.0", "0.1.0"));
        assert!(!version_gt("0.0.9", "0.1.0"));
    }
}
