use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use nl_net::{DownloadItem, ExpectedHash, NetClient, NetError};
use nl_platform::{LauncherPaths, read_json_with_backup, write_json_atomic};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use thiserror::Error;
use tokio_util::sync::CancellationToken;

pub const DEFAULT_MANIFEST_URL: &str = "";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum NightClientChannel {
    #[default]
    Stable,
    Beta,
}

impl NightClientChannel {
    pub fn key(self) -> &'static str {
        match self {
            NightClientChannel::Stable => "stable",
            NightClientChannel::Beta => "beta",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NightClientRelease {
    pub version: String,
    pub jar_url: String,
    #[serde(default)]
    pub mirror_urls: Vec<String>,
    #[serde(default)]
    pub sha256: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    #[serde(default)]
    pub signature_ed25519: Option<String>,
    pub min_fabric_loader: String,
    pub supported_mc: Vec<String>,
    #[serde(default)]
    pub known_conflicts: Vec<String>,
    #[serde(default)]
    pub changelog: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NightClientManifest {
    pub schema_version: u32,
    pub channels: BTreeMap<String, NightClientRelease>,
}

impl NightClientManifest {
    pub fn release(&self, channel: NightClientChannel) -> Option<&NightClientRelease> {
        self.channels
            .get(channel.key())
            .or_else(|| self.channels.get("stable"))
    }

    pub fn supported_range_label(&self, channel: NightClientChannel) -> String {
        let Some(rel) = self.release(channel) else {
            return "1.20 – 26.3".to_string();
        };
        match (rel.supported_mc.first(), rel.supported_mc.last()) {
            (Some(a), Some(b)) if a != b => format!("{a} – {b}"),
            (Some(a), _) => a.clone(),
            _ => "1.20 – 26.3".to_string(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum VisualsCompatibility {
    Ready,
    NeedsFabricUpgrade { current: String, required: String },
    NeedsSwitchToFabric { required_loader: String },
    IncompatibleLoader { loader: String },
    UnsupportedMcVersion { mc_version: String, supported_range: String },
}

impl VisualsCompatibility {
    pub fn is_mc_supported(&self) -> bool {
        !matches!(self, VisualsCompatibility::UnsupportedMcVersion { .. })
    }
}

#[derive(Debug, Error)]
pub enum NightManifestError {
    #[error("network error: {0}")]
    Net(#[from] NetError),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("no NightClient release found for selected channel")]
    MissingRelease,
    #[error("invalid ed25519 signature for NightClient artifact")]
    InvalidSignature,
    #[error("NightClient JAR for Minecraft {mc_version} was not found on GitHub Releases (https://github.com/N3moCC/NightClient/releases). Check your internet connection or try again later.")]
    JarUnavailable { mc_version: String },
}

pub fn default_embedded_manifest() -> NightClientManifest {
    let supported = vec![
        "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1",
        "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9",
        "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
    ]
    .into_iter()
    .map(str::to_string)
    .collect();

    let stable = NightClientRelease {
        version: "1.4.0".to_string(),
        jar_url: String::new(),
        mirror_urls: Vec::new(),
        sha256: None,
        size: None,
        signature_ed25519: None,
        min_fabric_loader: "0.19.5".to_string(),
        supported_mc: supported,
        known_conflicts: Vec::new(),
        changelog: "Universal JAR for Minecraft 1.20 – 26.3 (OpenGL & Vulkan)".to_string(),
    };

    let mut channels = BTreeMap::new();
    channels.insert("stable".to_string(), stable);
    NightClientManifest {
        schema_version: 1,
        channels,
    }
}

pub fn evaluate_compatibility(
    manifest: &NightClientManifest,
    channel: NightClientChannel,
    mc_version: &str,
    loader_id: &str,
    loader_version: Option<&str>,
) -> VisualsCompatibility {
    let Some(rel) = manifest.release(channel) else {
        return VisualsCompatibility::UnsupportedMcVersion {
            mc_version: mc_version.to_string(),
            supported_range: "1.20 – 26.3".to_string(),
        };
    };

    if !rel.supported_mc.iter().any(|v| v == mc_version) {
        return VisualsCompatibility::UnsupportedMcVersion {
            mc_version: mc_version.to_string(),
            supported_range: manifest.supported_range_label(channel),
        };
    }

    match loader_id.to_ascii_lowercase().as_str() {
        "fabric" => match loader_version.map(str::trim).filter(|s| !s.is_empty()) {
            None => VisualsCompatibility::Ready,
            Some(cur) if version_gte(cur, &rel.min_fabric_loader) => VisualsCompatibility::Ready,
            Some(cur) => VisualsCompatibility::NeedsFabricUpgrade {
                current: cur.to_string(),
                required: rel.min_fabric_loader.clone(),
            },
        },
        "vanilla" => VisualsCompatibility::NeedsSwitchToFabric {
            required_loader: rel.min_fabric_loader.clone(),
        },
        other => VisualsCompatibility::IncompatibleLoader {
            loader: other.to_string(),
        },
    }
}

pub async fn fetch_or_cached_manifest(
    net: &NetClient,
    paths: &LauncherPaths,
    manifest_url: Option<&str>,
    cancel: &CancellationToken,
) -> NightClientManifest {
    if let Some(url) = manifest_url.map(str::trim).filter(|s| !s.is_empty())
        && let Ok(remote) = net
            .fetch_json_cached::<NightClientManifest>(url, &paths.http_cache_dir(), cancel)
            .await
    {
        let _ = write_json_atomic(&paths.nightclient_manifest_cache(), &remote);
        return remote;
    }

    if let Some(cached) =
        read_json_with_backup::<NightClientManifest>(&paths.nightclient_manifest_cache())
    {
        return cached;
    }

    default_embedded_manifest()
}

pub struct JarResolveRequest<'a> {
    pub manifest: &'a NightClientManifest,
    pub channel: NightClientChannel,
    pub mc_version: &'a str,
    pub local_override: Option<&'a Path>,
    pub public_key_hex: Option<&'a str>,
}

pub async fn resolve_or_download_jar(
    net: &NetClient,
    paths: &LauncherPaths,
    req: &JarResolveRequest<'_>,
    cancel: &CancellationToken,
) -> Result<PathBuf, NightManifestError> {
    let mc_version = req.mc_version;
    if let Some(local) = req.local_override {
        if local.is_file() {
            return Ok(local.to_path_buf());
        }
        if local.is_dir()
            && let Some(found) = find_matching_jar_in_dir(local, mc_version)
        {
            return Ok(found);
        }
    }

    if let Some(dev_jar) = discover_local_workspace_jar(mc_version) {
        return Ok(dev_jar);
    }

    let rel = req
        .manifest
        .release(req.channel)
        .ok_or(NightManifestError::MissingRelease)?;

    if let Some(cached_match) = find_matching_jar_in_dir(&paths.nightclient_cache_dir(), mc_version)
        && rel.jar_url.trim().is_empty()
    {
        return Ok(cached_match);
    }

    let dest = paths
        .nightclient_cache_dir()
        .join(format!("nightclient-{}.jar", rel.version));

    if dest.is_file() && rel.jar_url.trim().is_empty() {
        return Ok(dest);
    }

    if rel.jar_url.trim().is_empty() {
        return Err(NightManifestError::JarUnavailable {
            mc_version: mc_version.to_string(),
        });
    }

    let mut urls = vec![rel.jar_url.clone()];
    urls.extend(rel.mirror_urls.clone());

    let item = DownloadItem {
        urls,
        dest: dest.clone(),
        expected_hash: rel.sha256.clone().map(ExpectedHash::Sha256),
        expected_size: rel.size,
        label: format!("NightClient {}", rel.version),
    };

    net.download_file(&item, cancel, |_| {}).await?;

    if let (Some(sig_hex), Some(pk_hex)) = (&rel.signature_ed25519, req.public_key_hex) {
        let bytes = std::fs::read(&dest)?;
        if !verify_ed25519_signature(&bytes, sig_hex, pk_hex) {
            let _ = std::fs::remove_file(&dest);
            return Err(NightManifestError::InvalidSignature);
        }
    }

    Ok(dest)
}

pub fn verify_ed25519_signature(payload: &[u8], sig_hex: &str, pubkey_hex: &str) -> bool {
    let Ok(pk_bytes) = hex::decode(pubkey_hex.trim()) else {
        return false;
    };
    let Ok(sig_bytes) = hex::decode(sig_hex.trim()) else {
        return false;
    };
    let Ok(pk_arr): Result<[u8; 32], _> = pk_bytes.try_into() else {
        return false;
    };
    let Ok(sig_arr): Result<[u8; 64], _> = sig_bytes.try_into() else {
        return false;
    };
    let Ok(vk) = VerifyingKey::from_bytes(&pk_arr) else {
        return false;
    };
    let sig = Signature::from_bytes(&sig_arr);
    vk.verify(payload, &sig).is_ok()
}

fn discover_local_workspace_jar(mc_version: &str) -> Option<PathBuf> {
    let mut roots: Vec<PathBuf> = Vec::new();
    let mut push_ancestors = |start: PathBuf| {
        let mut cur = Some(start.as_path());
        for _ in 0..6 {
            let Some(p) = cur else { break };
            let owned = p.to_path_buf();
            if !roots.contains(&owned) {
                roots.push(owned);
            }
            cur = p.parent();
        }
    };

    if let Ok(cwd) = std::env::current_dir() {
        push_ancestors(cwd);
    }
    if let Ok(exe) = std::env::current_exe()
        && let Some(parent) = exe.parent()
    {
        push_ancestors(parent.to_path_buf());
    }

    for root in &roots {
        for sub in [
            "build/distributions",
            "build/universal",
            "dist",
            "modules/universal/build/libs",
            "build/adapters",
        ] {
            let dir = root.join(sub);
            if let Some(found) = find_matching_jar_in_dir(&dir, mc_version) {
                return Some(found);
            }
        }
    }
    None
}

fn find_matching_jar_in_dir(dir: &Path, mc_version: &str) -> Option<PathBuf> {
    let entries = std::fs::read_dir(dir).ok()?;
    let mc_suffix = format!("+mc{}.jar", mc_version.to_ascii_lowercase());
    let mut exact_adapters: Vec<PathBuf> = Vec::new();
    let mut universal_jars: Vec<PathBuf> = Vec::new();

    for entry in entries.flatten() {
        let p = entry.path();
        if !p.is_file() {
            continue;
        }
        let Some(name) = p.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        let lower = name.to_ascii_lowercase();
        if !lower.contains("nightclient") || !lower.ends_with(".jar") || lower.contains("sources") {
            continue;
        }
        if lower.ends_with(&mc_suffix) {
            exact_adapters.push(p);
        } else if !lower.contains("+mc") {
            universal_jars.push(p);
        }
    }

    exact_adapters.sort();
    if let Some(found) = exact_adapters.pop() {
        return Some(found);
    }
    universal_jars.sort();
    universal_jars.pop()
}

pub fn version_gte(a: &str, b: &str) -> bool {
    let parse = |s: &str| -> Vec<u32> {
        s.split(|c: char| !c.is_ascii_digit())
            .filter(|p| !p.is_empty())
            .filter_map(|p| p.parse::<u32>().ok())
            .collect()
    };
    let va = parse(a);
    let vb = parse(b);
    let max_len = va.len().max(vb.len());
    for i in 0..max_len {
        let xa = va.get(i).copied().unwrap_or(0);
        let xb = vb.get(i).copied().unwrap_or(0);
        if xa != xb {
            return xa > xb;
        }
    }
    true
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn evaluates_visuals_compatibility_cases() {
        let m = default_embedded_manifest();
        assert_eq!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "1.21.4", "fabric", Some("0.19.5")),
            VisualsCompatibility::Ready
        );
        assert_eq!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "26.3", "fabric", Some("0.20.1")),
            VisualsCompatibility::Ready
        );
        assert!(matches!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "1.21.4", "fabric", Some("0.16.10")),
            VisualsCompatibility::NeedsFabricUpgrade { .. }
        ));
        assert!(matches!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "1.21.4", "vanilla", None),
            VisualsCompatibility::NeedsSwitchToFabric { .. }
        ));
        assert!(matches!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "1.21.4", "neoforge", Some("21.4.0")),
            VisualsCompatibility::IncompatibleLoader { .. }
        ));
        assert_eq!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "1.21.4", "fabric", None),
            VisualsCompatibility::Ready
        );
        assert!(matches!(
            evaluate_compatibility(&m, NightClientChannel::Stable, "1.16.5", "fabric", Some("0.19.5")),
            VisualsCompatibility::UnsupportedMcVersion { .. }
        ));

        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join("NightClient-1.4+mc1.21.1.jar"), b"a").unwrap();
        std::fs::write(dir.path().join("NightClient-1.4+mc1.21.11.jar"), b"b").unwrap();
        let found = find_matching_jar_in_dir(dir.path(), "1.21.1").unwrap();
        assert!(found.ends_with("NightClient-1.4+mc1.21.1.jar"));
        assert!(find_matching_jar_in_dir(dir.path(), "1.21.4").is_none());
    }
}
