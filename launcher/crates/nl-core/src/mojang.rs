use nl_net::{DownloadItem, ExpectedHash, NetClient, NetError};
use nl_platform::{CpuArch, LauncherPaths, MojangOs, current_arch, current_mojang_os};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::path::{Path, PathBuf};
use tokio_util::sync::CancellationToken;

pub const MOJANG_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct MojangVersionManifest {
    pub latest: LatestVersions,
    pub versions: Vec<ManifestVersionEntry>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct LatestVersions {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ManifestVersionEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    pub sha1: Option<String>,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct VersionProfile {
    pub id: String,
    #[serde(rename = "inheritsFrom", default)]
    pub inherits_from: Option<String>,
    #[serde(rename = "type", default)]
    pub kind: Option<String>,
    #[serde(rename = "mainClass", default)]
    pub main_class: Option<String>,
    #[serde(rename = "minecraftArguments", default)]
    pub minecraft_arguments: Option<String>,
    #[serde(default)]
    pub arguments: Option<ArgumentsSection>,
    #[serde(rename = "assetIndex", default)]
    pub asset_index: Option<AssetIndexInfo>,
    #[serde(default)]
    pub assets: Option<String>,
    #[serde(default)]
    pub downloads: Option<DownloadsSection>,
    #[serde(rename = "javaVersion", default)]
    pub java_version: Option<JavaVersionInfo>,
    #[serde(default)]
    pub libraries: Vec<LibraryEntry>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct ArgumentsSection {
    #[serde(default)]
    pub game: Vec<ArgumentSpec>,
    #[serde(default)]
    pub jvm: Vec<ArgumentSpec>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(untagged)]
pub enum ArgumentSpec {
    Simple(String),
    Conditional {
        #[serde(default)]
        rules: Vec<Rule>,
        value: StringOrVec,
    },
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(untagged)]
pub enum StringOrVec {
    One(String),
    Many(Vec<String>),
}

impl StringOrVec {
    pub fn push_into(&self, out: &mut Vec<String>) {
        match self {
            StringOrVec::One(s) => out.push(s.clone()),
            StringOrVec::Many(v) => out.extend(v.iter().cloned()),
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AssetIndexInfo {
    pub id: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct DownloadsSection {
    pub client: Option<ArtifactInfo>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ArtifactInfo {
    pub path: Option<String>,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct JavaVersionInfo {
    pub component: Option<String>,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct LibraryEntry {
    pub name: String,
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub downloads: Option<LibraryDownloads>,
    #[serde(default)]
    pub natives: Option<BTreeMap<String, String>>,
    #[serde(default)]
    pub rules: Vec<Rule>,
    #[serde(default)]
    pub extract: Option<ExtractRule>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct LibraryDownloads {
    pub artifact: Option<ArtifactInfo>,
    #[serde(default)]
    pub classifiers: BTreeMap<String, ArtifactInfo>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
pub struct ExtractRule {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<BTreeMap<String, bool>>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndexFile {
    pub objects: BTreeMap<String, AssetObject>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

pub async fn fetch_version_manifest(
    net: &NetClient,
    paths: &LauncherPaths,
    cancel: &CancellationToken,
) -> Result<MojangVersionManifest, NetError> {
    net.fetch_json_cached(MOJANG_MANIFEST_URL, &paths.http_cache_dir(), cancel)
        .await
}

pub async fn ensure_vanilla_profile(
    net: &NetClient,
    paths: &LauncherPaths,
    mc_version: &str,
    cancel: &CancellationToken,
) -> Result<VersionProfile, NetError> {
    let local = paths
        .versions_dir()
        .join(mc_version)
        .join(format!("{mc_version}.json"));
    if let Ok(raw) = fs::read(&local)
        && let Ok(parsed) = serde_json::from_slice::<VersionProfile>(&raw)
    {
        return Ok(parsed);
    }

    let manifest = fetch_version_manifest(net, paths, cancel).await?;
    let entry = manifest
        .versions
        .into_iter()
        .find(|v| v.id == mc_version)
        .ok_or_else(|| NetError::NoUrls(format!("Minecraft version {mc_version}")))?;

    let item = DownloadItem {
        urls: vec![entry.url],
        dest: local.clone(),
        expected_hash: entry.sha1.map(ExpectedHash::Sha1),
        expected_size: None,
        label: format!("{mc_version}.json"),
    };
    net.download_file(&item, cancel, |_| {}).await?;
    let bytes = fs::read(&local)?;
    Ok(serde_json::from_slice(&bytes)?)
}

pub fn required_java_for_profile(profile: &VersionProfile) -> u32 {
    if let Some(ref jv) = profile.java_version {
        return jv.major_version;
    }
    if profile.id.starts_with("26.") {
        25
    } else if profile.id.starts_with("1.21") || profile.id == "1.20.5" || profile.id == "1.20.6" {
        21
    } else if profile.id.starts_with("1.18")
        || profile.id.starts_with("1.19")
        || profile.id.starts_with("1.20")
    {
        17
    } else {
        8
    }
}

pub fn merge_profiles(base: VersionProfile, child: VersionProfile) -> VersionProfile {
    let mut merged = base;
    merged.id = child.id;
    if child.main_class.is_some() {
        merged.main_class = child.main_class;
    }
    if child.minecraft_arguments.is_some() {
        merged.minecraft_arguments = child.minecraft_arguments;
    }
    if child.java_version.is_some() {
        merged.java_version = child.java_version;
    }
    if child.asset_index.is_some() {
        merged.asset_index = child.asset_index;
    }
    if child.assets.is_some() {
        merged.assets = child.assets;
    }

    if let Some(child_args) = child.arguments {
        let base_args = merged.arguments.get_or_insert_with(ArgumentsSection::default);
        base_args.jvm.extend(child_args.jvm);
        base_args.game.extend(child_args.game);
    }

    let mut child_coords = std::collections::HashSet::new();
    for lib in &child.libraries {
        if let Some(ga) = maven_group_artifact(&lib.name) {
            child_coords.insert(ga);
        }
    }
    let mut combined = child.libraries;
    for lib in merged.libraries {
        let shadowed = maven_group_artifact(&lib.name)
            .map(|ga| child_coords.contains(&ga))
            .unwrap_or(false);
        if !shadowed {
            combined.push(lib);
        }
    }
    merged.libraries = combined;
    merged
}

pub fn rules_allow(rules: &[Rule], os: MojangOs, arch: CpuArch) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        if let Some(ref features) = rule.features
            && features.values().any(|&v| v)
        {
            continue;
        }
        let os_matches = match &rule.os {
            None => true,
            Some(cond) => {
                let name_ok = cond
                    .name
                    .as_deref()
                    .map(|n| n == os.as_mojang_str())
                    .unwrap_or(true);
                let arch_ok = cond
                    .arch
                    .as_deref()
                    .map(|a| {
                        a == arch.as_mojang_arch()
                            || (a == "aarch64" && arch == CpuArch::Aarch64)
                            || (a == "x86_64" && arch == CpuArch::X86_64)
                    })
                    .unwrap_or(true);
                name_ok && arch_ok
            }
        };
        if os_matches {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

pub fn maven_coord_to_rel_path(coord: &str) -> Option<String> {
    let (main, ext) = match coord.split_once('@') {
        Some((m, e)) => (m, e),
        None => (coord, "jar"),
    };
    let parts: Vec<&str> = main.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let group_path = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let file = if parts.len() >= 4 && !parts[3].is_empty() {
        format!("{artifact}-{version}-{}.{ext}", parts[3])
    } else {
        format!("{artifact}-{version}.{ext}")
    };
    Some(format!("{group_path}/{artifact}/{version}/{file}"))
}

fn maven_group_artifact(coord: &str) -> Option<String> {
    let mut parts = coord.split(':');
    let g = parts.next()?;
    let a = parts.next()?;
    let classifier = parts.nth(1).unwrap_or("");
    Some(format!("{g}:{a}:{classifier}"))
}

#[derive(Debug, Clone)]
pub struct ResolvedLibrary {
    pub local_path: PathBuf,
    pub download: Option<DownloadItem>,
    pub extract_to_natives: bool,
}

pub fn resolve_libraries(
    profile: &VersionProfile,
    libraries_dir: &Path,
) -> Vec<ResolvedLibrary> {
    let os = current_mojang_os();
    let arch = current_arch();
    let mut out = Vec::new();
    let mut seen = std::collections::HashSet::new();

    for lib in &profile.libraries {
        if !rules_allow(&lib.rules, os, arch) {
            continue;
        }

        if let Some(ref dl) = lib.downloads
            && let Some(ref art) = dl.artifact
        {
            let rel = art
                .path
                .clone()
                .or_else(|| maven_coord_to_rel_path(&lib.name));
            if let Some(rel) = rel {
                let dest = libraries_dir.join(&rel);
                if seen.insert(dest.clone()) {
                    let download = if !art.url.is_empty() {
                        Some(DownloadItem {
                            urls: vec![art.url.clone()],
                            dest: dest.clone(),
                            expected_hash: art.sha1.clone().map(ExpectedHash::Sha1),
                            expected_size: art.size,
                            label: lib.name.clone(),
                        })
                    } else {
                        None
                    };
                    out.push(ResolvedLibrary {
                        local_path: dest,
                        download,
                        extract_to_natives: false,
                    });
                }
            }
        } else if let Some(rel) = maven_coord_to_rel_path(&lib.name) {
            let dest = libraries_dir.join(&rel);
            if seen.insert(dest.clone()) {
                let base_url = lib
                    .url
                    .as_deref()
                    .unwrap_or("https://libraries.minecraft.net/");
                let url = format!("{}/{}", base_url.trim_end_matches('/'), rel);
                out.push(ResolvedLibrary {
                    local_path: dest.clone(),
                    download: Some(DownloadItem {
                        urls: vec![url],
                        dest,
                        expected_hash: None,
                        expected_size: None,
                        label: lib.name.clone(),
                    }),
                    extract_to_natives: false,
                });
            }
        }

        if let Some(ref natives_map) = lib.natives
            && let Some(raw_classifier) = natives_map.get(os.as_mojang_str())
        {
            let arch_bits = if arch == CpuArch::X86 { "32" } else { "64" };
            let classifier = raw_classifier.replace("${arch}", arch_bits);
            if let Some(ref dl) = lib.downloads
                && let Some(art) = dl.classifiers.get(&classifier)
            {
                let rel = art
                    .path
                    .clone()
                    .unwrap_or_else(|| format!("natives/{classifier}.jar"));
                let dest = libraries_dir.join(&rel);
                if seen.insert(dest.clone()) {
                    out.push(ResolvedLibrary {
                        local_path: dest.clone(),
                        download: Some(DownloadItem {
                            urls: vec![art.url.clone()],
                            dest,
                            expected_hash: art.sha1.clone().map(ExpectedHash::Sha1),
                            expected_size: art.size,
                            label: format!("{}:{classifier}", lib.name),
                        }),
                        extract_to_natives: true,
                    });
                }
            }
        }
    }

    out
}

pub fn extract_native_jar(jar_path: &Path, natives_dir: &Path) -> std::io::Result<()> {
    let file = fs::File::open(jar_path)?;
    let Ok(mut zip) = zip::ZipArchive::new(file) else {
        return Ok(());
    };
    fs::create_dir_all(natives_dir)?;
    for idx in 0..zip.len() {
        let Ok(mut entry) = zip.by_index(idx) else {
            continue;
        };
        let name = entry.name().to_string();
        if entry.is_dir() || name.starts_with("META-INF/") {
            continue;
        }
        let lower = name.to_ascii_lowercase();
        if !(lower.ends_with(".so")
            || lower.ends_with(".dll")
            || lower.ends_with(".dylib")
            || lower.ends_with(".jnilib"))
        {
            continue;
        }
        if let Some(fname) = Path::new(&name).file_name() {
            let out_path = natives_dir.join(fname);
            if out_path.is_file()
                && fs::metadata(&out_path).is_ok_and(|m| m.len() == entry.size())
            {
                continue;
            }
            let tmp_path = natives_dir.join(format!(
                ".{}.{}.tmp",
                fname.to_string_lossy(),
                std::process::id()
            ));
            let write_res = (|| -> std::io::Result<()> {
                let mut out = fs::File::create(&tmp_path)?;
                std::io::copy(&mut entry, &mut out)?;
                Ok(())
            })();
            if write_res.is_ok() {
                if fs::rename(&tmp_path, &out_path).is_err() {
                    let _ = fs::remove_file(&tmp_path);
                }
            } else {
                let _ = fs::remove_file(&tmp_path);
            }
        }
    }
    Ok(())
}

pub fn expand_profile_arguments(
    profile: &VersionProfile,
    vars: &HashMap<&str, String>,
) -> (Vec<String>, Vec<String>) {
    let os = current_mojang_os();
    let arch = current_arch();
    let mut jvm_args = Vec::new();
    let mut game_args = Vec::new();

    if let Some(ref section) = profile.arguments {
        for spec in &section.jvm {
            collect_spec(spec, os, arch, vars, &mut jvm_args);
        }
        for spec in &section.game {
            collect_spec(spec, os, arch, vars, &mut game_args);
        }
    } else if let Some(ref legacy) = profile.minecraft_arguments {
        for tok in legacy.split_whitespace() {
            game_args.push(substitute_vars(tok, vars));
        }
    }

    if !jvm_args.iter().any(|a| a == "-cp" || a == "-classpath") {
        if let Some(natives) = vars.get("natives_directory") {
            jvm_args.push(format!("-Djava.library.path={natives}"));
        }
        if let Some(cp) = vars.get("classpath") {
            jvm_args.push("-cp".to_string());
            jvm_args.push(cp.clone());
        }
    }

    (jvm_args, game_args)
}

fn collect_spec(
    spec: &ArgumentSpec,
    os: MojangOs,
    arch: CpuArch,
    vars: &HashMap<&str, String>,
    out: &mut Vec<String>,
) {
    match spec {
        ArgumentSpec::Simple(s) => out.push(substitute_vars(s, vars)),
        ArgumentSpec::Conditional { rules, value } => {
            if rules_allow(rules, os, arch) {
                let mut tmp = Vec::new();
                value.push_into(&mut tmp);
                for s in tmp {
                    out.push(substitute_vars(&s, vars));
                }
            }
        }
    }
}

fn substitute_vars(template: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = template.to_string();
    for (k, v) in vars {
        let needle = format!("${{{k}}}");
        if out.contains(&needle) {
            out = out.replace(&needle, v);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_three_representative_mc_versions_correctly() {
        let p120: VersionProfile = serde_json::from_str(
            r#"{
                "id": "1.20.4",
                "mainClass": "net.minecraft.client.main.Main",
                "javaVersion": { "majorVersion": 17 },
                "libraries": [
                    { "name": "org.lwjgl:lwjgl:3.3.2" },
                    { "name": "org.lwjgl:lwjgl-macos:3.3.2", "rules": [{"action":"allow","os":{"name":"osx"}}] }
                ],
                "arguments": {
                    "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"],
                    "game": ["--username", "${auth_player_name}", "--version", "${version_name}"]
                }
            }"#,
        )
        .unwrap();
        assert_eq!(required_java_for_profile(&p120), 17);

        let p121: VersionProfile = serde_json::from_str(
            r#"{
                "id": "1.21.4",
                "mainClass": "net.minecraft.client.main.Main",
                "javaVersion": { "majorVersion": 21 },
                "libraries": [{ "name": "org.ow2.asm:asm:9.6" }]
            }"#,
        )
        .unwrap();
        let fabric: VersionProfile = serde_json::from_str(
            r#"{
                "id": "fabric-loader-0.19.5-1.21.4",
                "inheritsFrom": "1.21.4",
                "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "libraries": [
                    { "name": "org.ow2.asm:asm:9.7.1", "url": "https://maven.fabricmc.net/" },
                    { "name": "net.fabricmc:fabric-loader:0.19.5", "url": "https://maven.fabricmc.net/" }
                ]
            }"#,
        )
        .unwrap();
        let merged = merge_profiles(p121, fabric);
        assert_eq!(required_java_for_profile(&merged), 21);
        assert_eq!(
            merged.main_class.as_deref(),
            Some("net.fabricmc.loader.impl.launch.knot.KnotClient")
        );
        assert!(merged.libraries.iter().any(|l| l.name == "org.ow2.asm:asm:9.7.1"));
        assert!(!merged.libraries.iter().any(|l| l.name == "org.ow2.asm:asm:9.6"));

        let p26: VersionProfile = serde_json::from_str(
            r#"{
                "id": "26.2",
                "mainClass": "net.minecraft.client.main.Main",
                "javaVersion": { "majorVersion": 25 }
            }"#,
        )
        .unwrap();
        assert_eq!(required_java_for_profile(&p26), 25);
        assert_eq!(
            maven_coord_to_rel_path("net.fabricmc:fabric-loader:0.19.5").as_deref(),
            Some("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar")
        );
    }
}
