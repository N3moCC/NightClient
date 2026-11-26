use crate::instance::LoaderKind;
use crate::mojang::{VersionProfile, ensure_vanilla_profile, merge_profiles};
use nl_net::{DownloadItem, NetClient, NetError};
use nl_platform::LauncherPaths;
use serde::Deserialize;
use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};
use thiserror::Error;
use tokio_util::sync::CancellationToken;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LoaderVersionOption {
    pub version: String,
    pub stable: bool,
    pub recommended: bool,
}

#[derive(Debug, Error)]
pub enum LoaderError {
    #[error("network error: {0}")]
    Net(#[from] NetError),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("no {loader} version available for Minecraft {mc_version}")]
    NoLoaderVersion { loader: String, mc_version: String },
    #[error("{loader} headless installer failed: {detail}")]
    InstallerFailed { loader: String, detail: String },
}

pub async fn fetch_loader_versions(
    net: &NetClient,
    paths: &LauncherPaths,
    loader: LoaderKind,
    mc_version: &str,
    cancel: &CancellationToken,
) -> Result<Vec<LoaderVersionOption>, LoaderError> {
    match loader {
        LoaderKind::Vanilla => Ok(Vec::new()),
        LoaderKind::Fabric => {
            #[derive(Deserialize)]
            struct FabricEntry {
                loader: FabricLoaderMeta,
            }
            #[derive(Deserialize)]
            struct FabricLoaderMeta {
                version: String,
                stable: bool,
            }
            let url = format!("https://meta.fabricmc.net/v2/versions/loader/{mc_version}");
            let list: Vec<FabricEntry> = net
                .fetch_json_cached(&url, &paths.http_cache_dir(), cancel)
                .await?;
            let mut marked_rec = false;
            Ok(list
                .into_iter()
                .map(|e| {
                    let rec = if !marked_rec && e.loader.stable {
                        marked_rec = true;
                        true
                    } else {
                        false
                    };
                    LoaderVersionOption {
                        version: e.loader.version,
                        stable: e.loader.stable,
                        recommended: rec,
                    }
                })
                .collect())
        }
        LoaderKind::Quilt => {
            #[derive(Deserialize)]
            struct QuiltEntry {
                loader: QuiltLoaderMeta,
            }
            #[derive(Deserialize)]
            struct QuiltLoaderMeta {
                version: String,
            }
            let url = format!("https://meta.quiltmc.org/v3/versions/loader/{mc_version}");
            let mut list: Vec<QuiltEntry> = net
                .fetch_json_cached(&url, &paths.http_cache_dir(), cancel)
                .await?;
            list.sort_by(|a, b| compare_version_desc(&a.loader.version, &b.loader.version));
            let mut marked_rec = false;
            Ok(list
                .into_iter()
                .map(|e| {
                    let stable = !e.loader.version.contains("beta")
                        && !e.loader.version.contains("alpha");
                    let rec = if !marked_rec && stable {
                        marked_rec = true;
                        true
                    } else {
                        false
                    };
                    LoaderVersionOption {
                        version: e.loader.version,
                        stable,
                        recommended: rec,
                    }
                })
                .collect())
        }
        LoaderKind::NeoForge => {
            #[derive(Deserialize)]
            struct NeoResp {
                versions: Vec<String>,
            }
            let mut raw_versions: Vec<String> = if mc_version == "1.20.1" {
                let url =
                    "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/forge";
                let resp: NeoResp = net
                    .fetch_json_cached(url, &paths.http_cache_dir(), cancel)
                    .await?;
                resp.versions
                    .into_iter()
                    .filter_map(|v| v.strip_prefix("1.20.1-").map(str::to_string))
                    .collect()
            } else {
                let url =
                    "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";
                let resp: NeoResp = net
                    .fetch_json_cached(url, &paths.http_cache_dir(), cancel)
                    .await?;
                let prefix = neoforge_prefix_for_mc(mc_version);
                resp.versions
                    .into_iter()
                    .filter(|v| !prefix.is_empty() && v.starts_with(&prefix))
                    .collect()
            };
            raw_versions.sort_by(|a, b| compare_version_desc(a, b));
            let mut marked_rec = false;
            Ok(raw_versions
                .into_iter()
                .map(|v| {
                    let stable = !v.contains("beta") && !v.contains("alpha");
                    let rec = if !marked_rec && stable {
                        marked_rec = true;
                        true
                    } else {
                        false
                    };
                    LoaderVersionOption {
                        version: v,
                        stable,
                        recommended: rec,
                    }
                })
                .collect())
        }
        LoaderKind::Forge => {
            #[derive(Deserialize)]
            struct PromoResp {
                promos: std::collections::BTreeMap<String, String>,
            }
            let promo_url =
                "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
            let promo: Option<PromoResp> = net
                .fetch_json_cached(promo_url, &paths.http_cache_dir(), cancel)
                .await
                .ok();
            let recommended = promo
                .as_ref()
                .and_then(|p| p.promos.get(&format!("{mc_version}-recommended")))
                .cloned();
            let latest = promo
                .as_ref()
                .and_then(|p| p.promos.get(&format!("{mc_version}-latest")))
                .cloned();

            let mut out = Vec::new();
            let mut seen = BTreeSet::new();

            if let Some(ref rec) = recommended
                && seen.insert(rec.clone())
            {
                out.push(LoaderVersionOption {
                    version: rec.clone(),
                    stable: true,
                    recommended: true,
                });
            }
            if let Some(ref lat) = latest
                && seen.insert(lat.clone())
            {
                out.push(LoaderVersionOption {
                    version: lat.clone(),
                    stable: false,
                    recommended: recommended.is_none(),
                });
            }

            let meta_url =
                "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
            if let Ok(bytes) = net.fetch_bytes(meta_url, cancel).await {
                let xml = String::from_utf8_lossy(&bytes);
                let parsed = parse_forge_maven_xml(&xml, mc_version);
                for ver in parsed {
                    if seen.insert(ver.clone()) {
                        out.push(LoaderVersionOption {
                            version: ver,
                            stable: true,
                            recommended: false,
                        });
                    }
                }
            }
            Ok(out)
        }
    }
}

pub fn parse_forge_maven_xml(xml: &str, mc_version: &str) -> Vec<String> {
    let prefix = format!("{mc_version}-");
    let mut versions = Vec::new();
    let mut rest = xml;
    while let Some(start) = rest.find("<version>") {
        let after = &rest[start + 9..];
        let Some(end) = after.find("</version>") else {
            break;
        };
        let raw = after[..end].trim();
        if let Some(forge_ver) = raw.strip_prefix(&prefix)
            && !forge_ver.is_empty()
        {
            versions.push(forge_ver.to_string());
        }
        rest = &after[end + 10..];
    }
    versions.reverse();
    versions
}

fn compare_version_desc(a: &str, b: &str) -> std::cmp::Ordering {
    let split = |s: &str| -> (Vec<u32>, bool, Vec<u32>) {
        let (base, pre) = match s.split_once('-') {
            Some((l, r)) => (l, Some(r)),
            None => (s, None),
        };
        let parse_nums = |part: &str| -> Vec<u32> {
            part.split(|c: char| !c.is_ascii_digit())
                .filter(|p| !p.is_empty())
                .filter_map(|p| p.parse::<u32>().ok())
                .collect()
        };
        (
            parse_nums(base),
            pre.is_none(),
            pre.map(parse_nums).unwrap_or_default(),
        )
    };
    let (ba, sa, pa) = split(a);
    let (bb, sb, pb) = split(b);
    bb.cmp(&ba)
        .then_with(|| sb.cmp(&sa))
        .then_with(|| pb.cmp(&pa))
        .then_with(|| b.cmp(a))
}

fn find_installed_profile_json(
    versions_dir: &Path,
    preferred_ids: &[String],
    version_token: &str,
) -> Option<PathBuf> {
    for id in preferred_ids {
        let candidate = versions_dir.join(id).join(format!("{id}.json"));
        if candidate.is_file() {
            return Some(candidate);
        }
    }
    let entries = fs::read_dir(versions_dir).ok()?;
    for entry in entries.flatten() {
        let dir = entry.path();
        if !dir.is_dir() {
            continue;
        }
        let Some(name) = dir.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        let exact_match = name == version_token
            || name.ends_with(&format!("-{version_token}"))
            || name.contains(&format!("-{version_token}-"));
        if exact_match {
            let json = dir.join(format!("{name}.json"));
            if json.is_file() {
                return Some(json);
            }
        }
    }
    None
}

pub async fn resolve_loader_profile(
    net: &NetClient,
    paths: &LauncherPaths,
    mc_version: &str,
    loader: LoaderKind,
    requested_loader_version: Option<&str>,
    java_executable_for_installer: Option<&Path>,
    cancel: &CancellationToken,
) -> Result<(VersionProfile, Option<String>), LoaderError> {
    let vanilla = ensure_vanilla_profile(net, paths, mc_version, cancel).await?;
    if loader == LoaderKind::Vanilla {
        return Ok((vanilla, None));
    }

    let chosen_version = match requested_loader_version.filter(|s| !s.trim().is_empty()) {
        Some(v) => v.to_string(),
        None => {
            let available = fetch_loader_versions(net, paths, loader, mc_version, cancel).await?;
            available
                .iter()
                .find(|v| v.recommended)
                .or_else(|| available.iter().find(|v| v.stable))
                .or_else(|| available.first())
                .map(|v| v.version.clone())
                .ok_or_else(|| LoaderError::NoLoaderVersion {
                    loader: loader.display_name().to_string(),
                    mc_version: mc_version.to_string(),
                })?
        }
    };

    match loader {
        LoaderKind::Vanilla => Ok((vanilla, None)),
        LoaderKind::Fabric => {
            let profile_id = format!("fabric-loader-{chosen_version}-{mc_version}");
            let local = paths
                .versions_dir()
                .join(&profile_id)
                .join(format!("{profile_id}.json"));
            let child = if let Ok(raw) = fs::read(&local)
                && let Ok(parsed) = serde_json::from_slice::<VersionProfile>(&raw)
            {
                parsed
            } else {
                let url = format!(
                    "https://meta.fabricmc.net/v2/versions/loader/{mc_version}/{chosen_version}/profile/json"
                );
                let parsed: VersionProfile = net.fetch_json(&url, cancel).await?;
                let _ = nl_platform::write_json_atomic(&local, &parsed);
                parsed
            };
            Ok((merge_profiles(vanilla, child), Some(chosen_version)))
        }
        LoaderKind::Quilt => {
            let profile_id = format!("quilt-loader-{chosen_version}-{mc_version}");
            let local = paths
                .versions_dir()
                .join(&profile_id)
                .join(format!("{profile_id}.json"));
            let child = if let Ok(raw) = fs::read(&local)
                && let Ok(parsed) = serde_json::from_slice::<VersionProfile>(&raw)
            {
                parsed
            } else {
                let url = format!(
                    "https://meta.quiltmc.org/v3/versions/loader/{mc_version}/{chosen_version}/profile/json"
                );
                let parsed: VersionProfile = net.fetch_json(&url, cancel).await?;
                let _ = nl_platform::write_json_atomic(&local, &parsed);
                parsed
            };
            Ok((merge_profiles(vanilla, child), Some(chosen_version)))
        }
        LoaderKind::NeoForge => {
            let preferred_ids = if mc_version == "1.20.1" {
                vec![
                    format!("forge-1.20.1-{chosen_version}"),
                    format!("1.20.1-forge-{chosen_version}"),
                    format!("neoforge-{chosen_version}"),
                ]
            } else {
                vec![format!("neoforge-{chosen_version}")]
            };
            let local = match find_installed_profile_json(
                &paths.versions_dir(),
                &preferred_ids,
                &chosen_version,
            ) {
                Some(existing) => existing,
                None => {
                    let (installer_url, filename) = if mc_version == "1.20.1" {
                        (
                            format!(
                                "https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-{chosen_version}/forge-1.20.1-{chosen_version}-installer.jar"
                            ),
                            format!("neoforge-1.20.1-{chosen_version}-installer.jar"),
                        )
                    } else {
                        (
                            format!(
                                "https://maven.neoforged.net/releases/net/neoforged/neoforge/{chosen_version}/neoforge-{chosen_version}-installer.jar"
                            ),
                            format!("neoforge-{chosen_version}-installer.jar"),
                        )
                    };
                    run_headless_installer(
                        net,
                        paths,
                        "NeoForge",
                        &installer_url,
                        &filename,
                        java_executable_for_installer,
                        cancel,
                    )
                    .await?;
                    find_installed_profile_json(
                        &paths.versions_dir(),
                        &preferred_ids,
                        &chosen_version,
                    )
                    .ok_or_else(|| LoaderError::InstallerFailed {
                        loader: "NeoForge".to_string(),
                        detail: format!(
                            "profile JSON for NeoForge {chosen_version} not found after install"
                        ),
                    })?
                }
            };
            let bytes = fs::read(&local)?;
            let child: VersionProfile = serde_json::from_slice(&bytes)?;
            Ok((merge_profiles(vanilla, child), Some(chosen_version)))
        }
        LoaderKind::Forge => {
            let preferred_ids = vec![format!("{mc_version}-forge-{chosen_version}")];
            let local = match find_installed_profile_json(
                &paths.versions_dir(),
                &preferred_ids,
                &chosen_version,
            ) {
                Some(existing) => existing,
                None => {
                    let installer_url = format!(
                        "https://maven.minecraftforge.net/net/minecraftforge/forge/{mc_version}-{chosen_version}/forge-{mc_version}-{chosen_version}-installer.jar"
                    );
                    run_headless_installer(
                        net,
                        paths,
                        "Forge",
                        &installer_url,
                        &format!("forge-{mc_version}-{chosen_version}-installer.jar"),
                        java_executable_for_installer,
                        cancel,
                    )
                    .await?;
                    find_installed_profile_json(
                        &paths.versions_dir(),
                        &preferred_ids,
                        &chosen_version,
                    )
                    .ok_or_else(|| LoaderError::InstallerFailed {
                        loader: "Forge".to_string(),
                        detail: format!(
                            "profile JSON for Forge {chosen_version} not found after install"
                        ),
                    })?
                }
            };
            let bytes = fs::read(&local)?;
            let child: VersionProfile = serde_json::from_slice(&bytes)?;
            Ok((merge_profiles(vanilla, child), Some(chosen_version)))
        }
    }
}

fn console_java_exe(java: &Path) -> PathBuf {
    #[cfg(target_os = "windows")]
    {
        if java
            .file_name()
            .and_then(|n| n.to_str())
            .is_some_and(|n| n.eq_ignore_ascii_case("javaw.exe"))
        {
            let candidate = java.with_file_name("java.exe");
            if candidate.is_file() {
                return candidate;
            }
        }
    }
    java.to_path_buf()
}

async fn run_headless_installer(
    net: &NetClient,
    paths: &LauncherPaths,
    loader_name: &str,
    installer_url: &str,
    filename: &str,
    java_exe: Option<&Path>,
    cancel: &CancellationToken,
) -> Result<(), LoaderError> {
    let java = java_exe.ok_or_else(|| LoaderError::InstallerFailed {
        loader: loader_name.to_string(),
        detail: "Java runtime is required to run the loader installer".to_string(),
    })?;

    let installer_dir = paths.cache_dir().join("installers");
    fs::create_dir_all(&installer_dir)?;
    let installer_path = installer_dir.join(filename);
    let item = DownloadItem::single(installer_url, installer_path.clone());
    net.download_file(&item, cancel, |_| {}).await?;

    let stub = paths.root.join("launcher_profiles.json");
    if !stub.exists() {
        let _ = fs::write(&stub, br#"{"profiles":{}}"#);
    }

    let java_owned = console_java_exe(java);
    let mut cmd = tokio::process::Command::new(&java_owned);
    #[cfg(target_os = "windows")]
    {
        cmd.creation_flags(0x0800_0000);
    }
    cmd.current_dir(&installer_dir)
        .arg("-jar")
        .arg(&installer_path)
        .arg("--installClient")
        .arg(&paths.root)
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .kill_on_drop(true);

    let mut child = cmd.spawn()?;
    let mut out_pipe = child.stdout.take();
    let mut err_pipe = child.stderr.take();
    let out_task = tokio::spawn(async move {
        let mut buf = Vec::new();
        if let Some(ref mut r) = out_pipe {
            let _ = tokio::io::AsyncReadExt::read_to_end(r, &mut buf).await;
        }
        buf
    });
    let err_task = tokio::spawn(async move {
        let mut buf = Vec::new();
        if let Some(ref mut r) = err_pipe {
            let _ = tokio::io::AsyncReadExt::read_to_end(r, &mut buf).await;
        }
        buf
    });

    let status = tokio::select! {
        _ = cancel.cancelled() => {
            let _ = child.kill().await;
            let _ = child.wait().await;
            out_task.abort();
            err_task.abort();
            return Err(LoaderError::Net(NetError::Cancelled));
        }
        res = child.wait() => res?,
    };
    let stdout_bytes = out_task.await.unwrap_or_default();
    let stderr_bytes = err_task.await.unwrap_or_default();
    if !status.success() {
        let stderr = String::from_utf8_lossy(&stderr_bytes);
        let stdout = String::from_utf8_lossy(&stdout_bytes);
        return Err(LoaderError::InstallerFailed {
            loader: loader_name.to_string(),
            detail: format!("{stdout}\n{stderr}").trim().to_string(),
        });
    }
    Ok(())
}

fn neoforge_prefix_for_mc(mc_version: &str) -> String {
    if let Some(rest) = mc_version.strip_prefix("1.") {
        let mut parts = rest.split('.');
        let minor = parts.next().unwrap_or("21");
        let patch = parts.next().unwrap_or("0");
        format!("{minor}.{patch}.")
    } else {
        format!("{mc_version}.")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_mc_versions_to_neoforge_prefixes() {
        assert_eq!(neoforge_prefix_for_mc("1.21.4"), "21.4.");
        assert_eq!(neoforge_prefix_for_mc("1.21"), "21.0.");
        assert_eq!(neoforge_prefix_for_mc("26.1"), "26.1.");
    }

    #[test]
    fn profile_lookup_requires_exact_version_boundary() {
        let dir = tempfile::tempdir().unwrap();
        let vdir = dir.path();
        let newer = vdir.join("neoforge-21.4.161");
        fs::create_dir_all(&newer).unwrap();
        fs::write(newer.join("neoforge-21.4.161.json"), b"{}").unwrap();
        assert!(find_installed_profile_json(vdir, &[], "21.4.1").is_none());
        let exact = vdir.join("neoforge-21.4.1");
        fs::create_dir_all(&exact).unwrap();
        fs::write(exact.join("neoforge-21.4.1.json"), b"{}").unwrap();
        assert_eq!(
            find_installed_profile_json(vdir, &[], "21.4.1"),
            Some(exact.join("neoforge-21.4.1.json"))
        );
    }

    #[test]
    fn parses_forge_maven_metadata_for_target_mc() {
        let xml = r#"
            <metadata><versioning><versions>
                <version>1.20.1-47.2.0</version>
                <version>1.20.1-47.3.0</version>
                <version>1.21.4-54.0.10</version>
            </versions></versioning></metadata>
        "#;
        assert_eq!(
            parse_forge_maven_xml(xml, "1.20.1"),
            vec!["47.3.0".to_string(), "47.2.0".to_string()]
        );
    }

    #[test]
    fn sorts_quilt_and_neoforge_versions_descending_with_stable_first() {
        let mut versions = vec![
            "0.20.0-beta.9",
            "0.24.0",
            "0.30.2-beta.1",
            "0.30.2",
            "0.31.0-beta.6",
            "0.17.3",
        ];
        versions.sort_by(|a, b| compare_version_desc(a, b));
        assert_eq!(
            versions,
            vec![
                "0.31.0-beta.6",
                "0.30.2",
                "0.30.2-beta.1",
                "0.24.0",
                "0.20.0-beta.9",
                "0.17.3",
            ]
        );
    }
}
