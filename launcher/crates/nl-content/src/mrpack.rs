use nl_net::{BatchProgress, DownloadItem, ExpectedHash, NetClient, NetError, compute_sha1_file};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fs;
use std::io::{Read, Write};
use std::path::Path;
use thiserror::Error;
use tokio_util::sync::CancellationToken;

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MrpackIndex {
    #[serde(rename = "formatVersion")]
    format_version: u32,
    game: String,
    #[serde(rename = "versionId")]
    version_id: String,
    name: String,
    #[serde(default)]
    summary: String,
    files: Vec<MrpackFile>,
    dependencies: BTreeMap<String, String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MrpackFile {
    path: String,
    hashes: MrpackHashes,
    #[serde(default)]
    downloads: Vec<String>,
    #[serde(rename = "fileSize")]
    file_size: u64,
    #[serde(default)]
    env: Option<MrpackEnv>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MrpackHashes {
    sha1: String,
    #[serde(default)]
    sha512: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MrpackEnv {
    client: Option<String>,
    server: Option<String>,
}

#[derive(Debug, Clone)]
pub struct MrpackSummary {
    pub name: String,
    pub mc_version: String,
    pub loader: String,
    pub loader_version: Option<String>,
    pub file_count: usize,
}

#[derive(Debug, Error)]
pub enum MrpackError {
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("zip error: {0}")]
    Zip(#[from] zip::result::ZipError),
    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("network error: {0}")]
    Net(#[from] NetError),
    #[error("mrpack missing required minecraft version dependency")]
    MissingMinecraftVersion,
    #[error("unsafe path in mrpack: {0}")]
    UnsafePath(String),
}

fn is_safe_relative_path(raw: &str) -> bool {
    if raw.is_empty() || raw.contains('\\') || raw.contains(':') || raw.contains('\0') {
        return false;
    }
    let rel = Path::new(raw);
    if rel.is_absolute() {
        return false;
    }
    let mut has_normal = false;
    for comp in rel.components() {
        match comp {
            std::path::Component::Normal(_) => has_normal = true,
            _ => return false,
        }
    }
    has_normal
}

pub async fn import_mrpack<F>(
    net: &NetClient,
    mrpack_path: &Path,
    instance_dir: &Path,
    cancel: &CancellationToken,
    on_progress: F,
) -> Result<MrpackSummary, MrpackError>
where
    F: Fn(BatchProgress) + Send + Sync + 'static,
{
    let file = fs::File::open(mrpack_path)?;
    let mut zip = zip::ZipArchive::new(file)?;

    let index: MrpackIndex = {
        let mut entry = zip.by_name("modrinth.index.json")?;
        let mut text = String::new();
        entry.read_to_string(&mut text)?;
        serde_json::from_str(&text)?
    };

    let mc_version = index
        .dependencies
        .get("minecraft")
        .cloned()
        .ok_or(MrpackError::MissingMinecraftVersion)?;

    let (loader, loader_version) = if let Some(v) = index.dependencies.get("fabric-loader") {
        ("fabric".to_string(), Some(v.clone()))
    } else if let Some(v) = index.dependencies.get("quilt-loader") {
        ("quilt".to_string(), Some(v.clone()))
    } else if let Some(v) = index.dependencies.get("neoforge") {
        ("neoforge".to_string(), Some(v.clone()))
    } else if let Some(v) = index.dependencies.get("forge") {
        ("forge".to_string(), Some(v.clone()))
    } else {
        ("vanilla".to_string(), None)
    };

    fs::create_dir_all(instance_dir)?;

    let mut downloads = Vec::new();
    for f in &index.files {
        if let Some(ref env) = f.env
            && env.client.as_deref() == Some("unsupported")
        {
            continue;
        }
        if !is_safe_relative_path(&f.path) {
            return Err(MrpackError::UnsafePath(f.path.clone()));
        }
        let rel = Path::new(&f.path);
        downloads.push(DownloadItem {
            urls: f.downloads.clone(),
            dest: instance_dir.join(rel),
            expected_hash: Some(ExpectedHash::Sha1(f.hashes.sha1.clone())),
            expected_size: Some(f.file_size),
            label: f.path.clone(),
        });
    }

    let file_count = downloads.len();
    net.download_batch(downloads, cancel, on_progress).await?;

    for prefix in ["overrides/", "client-overrides/"] {
        for idx in 0..zip.len() {
            let mut entry = zip.by_index(idx)?;
            let name = entry.name().to_string();
            let Some(stripped) = name.strip_prefix(prefix) else {
                continue;
            };
            if stripped.is_empty() {
                continue;
            }
            let check_path = stripped.trim_end_matches('/');
            if check_path.is_empty() || !is_safe_relative_path(check_path) {
                continue;
            }
            let rel = Path::new(check_path);
            let dest = instance_dir.join(rel);
            if entry.is_dir() {
                fs::create_dir_all(&dest)?;
            } else {
                if let Some(parent) = dest.parent() {
                    fs::create_dir_all(parent)?;
                }
                let mut out = fs::File::create(&dest)?;
                std::io::copy(&mut entry, &mut out)?;
            }
        }
    }

    Ok(MrpackSummary {
        name: index.name,
        mc_version,
        loader,
        loader_version,
        file_count,
    })
}

pub fn export_mrpack(
    instance_dir: &Path,
    name: &str,
    mc_version: &str,
    loader: &str,
    loader_version: Option<&str>,
    output_path: &Path,
) -> Result<(), MrpackError> {
    let mut deps = BTreeMap::new();
    deps.insert("minecraft".to_string(), mc_version.to_string());
    if let Some(lv) = loader_version {
        let key = match loader {
            "fabric" => Some("fabric-loader"),
            "quilt" => Some("quilt-loader"),
            "neoforge" => Some("neoforge"),
            "forge" => Some("forge"),
            _ => None,
        };
        if let Some(k) = key {
            deps.insert(k.to_string(), lv.to_string());
        }
    }

    let index = MrpackIndex {
        format_version: 1,
        game: "minecraft".to_string(),
        version_id: "1.0.0".to_string(),
        name: name.to_string(),
        summary: "Exported from NightClient Launcher".to_string(),
        files: Vec::new(),
        dependencies: deps,
    };

    let out = fs::File::create(output_path)?;
    let mut zw = zip::ZipWriter::new(out);
    let opts = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);

    zw.start_file("modrinth.index.json", opts)?;
    zw.write_all(&serde_json::to_vec_pretty(&index)?)?;

    for subdir in ["mods", "config", "resourcepacks", "shaderpacks"] {
        let dir = instance_dir.join(subdir);
        if dir.is_dir() {
            add_dir_to_zip(&mut zw, &dir, Path::new("overrides").join(subdir).as_path(), opts)?;
        }
    }
    let options_txt = instance_dir.join("options.txt");
    if options_txt.is_file() {
        zw.start_file("overrides/options.txt", opts)?;
        let _ = compute_sha1_file(&options_txt);
        zw.write_all(&fs::read(&options_txt)?)?;
    }

    zw.finish()?;
    Ok(())
}

fn add_dir_to_zip<W: Write + std::io::Seek>(
    zw: &mut zip::ZipWriter<W>,
    src_dir: &Path,
    zip_prefix: &Path,
    opts: zip::write::SimpleFileOptions,
) -> Result<(), MrpackError> {
    for entry in fs::read_dir(src_dir)?.flatten() {
        let p = entry.path();
        let name = entry.file_name();
        let name_str = name.to_string_lossy();
        if name_str.starts_with('.') {
            continue;
        }
        let target = zip_prefix.join(&name);
        if p.is_dir() {
            add_dir_to_zip(zw, &p, &target, opts)?;
        } else if p.is_file() {
            let zip_name = target.to_string_lossy().replace('\\', "/");
            zw.start_file(zip_name, opts)?;
            let mut f = fs::File::open(&p)?;
            std::io::copy(&mut f, zw)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_unsafe_mrpack_paths() {
        assert!(is_safe_relative_path("mods/sodium.jar"));
        assert!(is_safe_relative_path("config/sodium-options.json"));
        assert!(!is_safe_relative_path("../escape.jar"));
        assert!(!is_safe_relative_path(r"mods\..\escape.jar"));
        assert!(!is_safe_relative_path("/etc/passwd"));
        assert!(!is_safe_relative_path("C:/Windows/win.ini"));
        assert!(!is_safe_relative_path("C:evil.dll"));
        assert!(!is_safe_relative_path(""));
    }
}
