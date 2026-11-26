use nl_net::{DownloadItem, ExpectedHash, NetClient, NetError, compute_sha1_file};
use nl_platform::{read_json_with_backup, write_json_atomic};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use thiserror::Error;
use tokio_util::sync::CancellationToken;

const MODRINTH_API: &str = "https://api.modrinth.com/v2";

pub const OPTIMIZATION_PACK_SLUGS: &[&str] = &[
    "sodium",
    "lithium",
    "ferrite-core",
    "entityculling",
    "immediatelyfast",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ContentKind {
    #[default]
    Mod,
    ResourcePack,
    Shader,
    Modpack,
}

impl ContentKind {
    pub const ALL: [ContentKind; 4] = [
        ContentKind::Mod,
        ContentKind::ResourcePack,
        ContentKind::Shader,
        ContentKind::Modpack,
    ];

    pub fn project_type(self) -> &'static str {
        match self {
            ContentKind::Mod => "mod",
            ContentKind::ResourcePack => "resourcepack",
            ContentKind::Shader => "shader",
            ContentKind::Modpack => "modpack",
        }
    }

    pub fn install_subdir(self) -> &'static str {
        match self {
            ContentKind::Mod => "mods",
            ContentKind::ResourcePack => "resourcepacks",
            ContentKind::Shader => "shaderpacks",
            ContentKind::Modpack => "",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ModrinthSort {
    #[default]
    Relevance,
    Downloads,
    Updated,
    Newest,
}

impl ModrinthSort {
    pub fn as_str(self) -> &'static str {
        match self {
            ModrinthSort::Relevance => "relevance",
            ModrinthSort::Downloads => "downloads",
            ModrinthSort::Updated => "updated",
            ModrinthSort::Newest => "newest",
        }
    }
}

fn null_default<'de, D, T>(deserializer: D) -> Result<T, D::Error>
where
    D: serde::Deserializer<'de>,
    T: Default + serde::Deserialize<'de>,
{
    Ok(Option::<T>::deserialize(deserializer)?.unwrap_or_default())
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthSearchHit {
    pub project_id: String,
    #[serde(default, deserialize_with = "null_default")]
    pub slug: String,
    #[serde(default, deserialize_with = "null_default")]
    pub title: String,
    #[serde(default, deserialize_with = "null_default")]
    pub description: String,
    #[serde(default, deserialize_with = "null_default")]
    pub author: String,
    #[serde(default, deserialize_with = "null_default")]
    pub downloads: u64,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub project_type: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthGalleryItem {
    #[serde(default, deserialize_with = "null_default")]
    pub url: String,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub description: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct ModrinthLicense {
    #[serde(default, deserialize_with = "null_default")]
    pub id: String,
    #[serde(default, deserialize_with = "null_default")]
    pub name: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthProjectDetails {
    pub id: String,
    #[serde(default, deserialize_with = "null_default")]
    pub slug: String,
    #[serde(default, deserialize_with = "null_default")]
    pub title: String,
    #[serde(default, deserialize_with = "null_default")]
    pub description: String,
    #[serde(default, deserialize_with = "null_default")]
    pub body: String,
    #[serde(default, deserialize_with = "null_default")]
    pub downloads: u64,
    #[serde(default, deserialize_with = "null_default")]
    pub followers: u64,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub project_type: String,
    #[serde(default, deserialize_with = "null_default")]
    pub categories: Vec<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub loaders: Vec<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub game_versions: Vec<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub client_side: String,
    #[serde(default, deserialize_with = "null_default")]
    pub server_side: String,
    #[serde(default)]
    pub license: Option<ModrinthLicense>,
    #[serde(default)]
    pub source_url: Option<String>,
    #[serde(default)]
    pub issues_url: Option<String>,
    #[serde(default)]
    pub wiki_url: Option<String>,
    #[serde(default)]
    pub discord_url: Option<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub gallery: Vec<ModrinthGalleryItem>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct ModrinthFileHashes {
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub sha512: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthVersionFile {
    pub url: String,
    pub filename: String,
    #[serde(default, deserialize_with = "null_default")]
    pub primary: bool,
    #[serde(default, deserialize_with = "null_default")]
    pub size: u64,
    #[serde(default, deserialize_with = "null_default")]
    pub hashes: ModrinthFileHashes,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthDependency {
    #[serde(default)]
    pub version_id: Option<String>,
    #[serde(default)]
    pub project_id: Option<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub dependency_type: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthVersion {
    pub id: String,
    pub project_id: String,
    #[serde(default, deserialize_with = "null_default")]
    pub name: String,
    #[serde(default, deserialize_with = "null_default")]
    pub version_number: String,
    #[serde(default, deserialize_with = "null_default")]
    pub version_type: String,
    #[serde(default, deserialize_with = "null_default")]
    pub date_published: String,
    #[serde(default, deserialize_with = "null_default")]
    pub downloads: u64,
    #[serde(default, deserialize_with = "null_default")]
    pub game_versions: Vec<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub loaders: Vec<String>,
    #[serde(default, deserialize_with = "null_default")]
    pub files: Vec<ModrinthVersionFile>,
    #[serde(default, deserialize_with = "null_default")]
    pub dependencies: Vec<ModrinthDependency>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InstalledContentEntry {
    pub project_id: String,
    pub version_id: String,
    pub filename: String,
    pub sha1: String,
    pub explicit: bool,
    #[serde(default)]
    pub required_by: Vec<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct ContentRegistry {
    pub entries: BTreeMap<String, InstalledContentEntry>,
}

impl ContentRegistry {
    pub fn load(instance_dir: &Path) -> Self {
        read_json_with_backup(&instance_dir.join(".nightlauncher-content.json"))
            .unwrap_or_default()
    }

    pub fn save(&self, instance_dir: &Path) -> std::io::Result<()> {
        write_json_atomic(&instance_dir.join(".nightlauncher-content.json"), self)
    }

    pub fn orphaned_dependencies_if_removed(&self, project_id: &str) -> Vec<&InstalledContentEntry> {
        self.entries
            .values()
            .filter(|e| {
                !e.explicit
                    && !e.required_by.is_empty()
                    && e.required_by.iter().all(|r| r == project_id)
            })
            .collect()
    }
}

#[derive(Debug, Clone)]
pub struct ModrinthUpdateCandidate {
    pub current_filename: String,
    pub project_id: String,
    pub latest_version: ModrinthVersion,
}

#[derive(Debug, Clone, Default)]
pub struct MigrationReport {
    pub migrated: Vec<String>,
    pub incompatible: Vec<String>,
    pub copied_local: Vec<String>,
}

#[derive(Debug, Error)]
pub enum ModrinthError {
    #[error("network error: {0}")]
    Net(#[from] NetError),
    #[error("http error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("no compatible version of '{project}' for Minecraft {mc_version} ({loader})")]
    NoCompatibleVersion {
        project: String,
        mc_version: String,
        loader: String,
    },
    #[error("unsafe filename from remote API: {0}")]
    UnsafeFilename(String),
}

fn validate_remote_filename(raw: &str) -> Result<&str, ModrinthError> {
    if raw.is_empty()
        || raw == "."
        || raw == ".."
        || raw.contains('/')
        || raw.contains('\\')
        || raw.contains(':')
        || raw.contains('\0')
    {
        return Err(ModrinthError::UnsafeFilename(raw.to_string()));
    }
    let mut comps = Path::new(raw).components();
    match (comps.next(), comps.next()) {
        (Some(std::path::Component::Normal(_)), None) => Ok(raw),
        _ => Err(ModrinthError::UnsafeFilename(raw.to_string())),
    }
}

#[derive(Debug, Clone)]
pub struct ModrinthSearchQuery {
    pub query: String,
    pub kind: ContentKind,
    pub mc_version: Option<String>,
    pub loader: Option<String>,
    pub sort: ModrinthSort,
    pub limit: u32,
    pub offset: u32,
}

pub async fn search_modrinth(
    net: &NetClient,
    params: &ModrinthSearchQuery,
    cancel: &CancellationToken,
) -> Result<Vec<ModrinthSearchHit>, ModrinthError> {
    #[derive(Deserialize)]
    struct SearchResp {
        hits: Vec<ModrinthSearchHit>,
    }

    let mut facets: Vec<Vec<String>> = vec![vec![format!(
        "project_type:{}",
        params.kind.project_type()
    )]];
    if let Some(mc) = params.mc_version.as_deref().filter(|s| !s.is_empty()) {
        facets.push(vec![format!("versions:{mc}")]);
    }
    if params.kind == ContentKind::Mod
        && let Some(ldr) = params
            .loader
            .as_deref()
            .filter(|s| !s.is_empty() && *s != "vanilla")
    {
        facets.push(vec![format!("categories:{ldr}")]);
    }
    let facets_json = serde_json::to_string(&facets).unwrap_or_else(|_| "[]".into());

    let mut url = url::Url::parse(&format!("{MODRINTH_API}/search")).unwrap();
    url.query_pairs_mut()
        .append_pair("query", &params.query)
        .append_pair("facets", &facets_json)
        .append_pair("index", params.sort.as_str())
        .append_pair("limit", &params.limit.to_string())
        .append_pair("offset", &params.offset.to_string());

    let resp: SearchResp = net.fetch_json(url.as_str(), cancel).await?;
    Ok(resp.hits)
}

pub async fn fetch_project_icon(
    net: &NetClient,
    icons_dir: &Path,
    project_id: &str,
    icon_url: &str,
    cancel: &CancellationToken,
) -> Option<PathBuf> {
    let dest = icons_dir.join(format!("mr_{}.png", nl_platform::sanitize_filename(project_id)));
    if dest.is_file() {
        return Some(dest);
    }
    let bytes = net.fetch_bytes(icon_url, cancel).await.ok()?;
    let img = image::load_from_memory(&bytes).ok()?;
    let _ = fs::create_dir_all(icons_dir);
    let resized = img.thumbnail(64, 64);
    resized.save(&dest).ok()?;
    Some(dest)
}

pub async fn download_modpack_mrpack(
    net: &NetClient,
    cache_dir: &Path,
    project_id_or_slug: &str,
    mc_version: Option<&str>,
    cancel: &CancellationToken,
) -> Result<PathBuf, ModrinthError> {
    let mut url =
        url::Url::parse(&format!("{MODRINTH_API}/project/{project_id_or_slug}/version")).unwrap();
    if let Some(mc) = mc_version.filter(|s| !s.is_empty()) {
        url.query_pairs_mut()
            .append_pair("game_versions", &serde_json::to_string(&[mc]).unwrap());
    }
    let mut versions: Vec<ModrinthVersion> = net.fetch_json(url.as_str(), cancel).await?;
    if versions.is_empty() && mc_version.is_some() {
        let fallback_url = format!("{MODRINTH_API}/project/{project_id_or_slug}/version");
        versions = net.fetch_json(&fallback_url, cancel).await?;
    }
    let chosen = versions
        .into_iter()
        .next()
        .ok_or_else(|| ModrinthError::NoCompatibleVersion {
            project: project_id_or_slug.to_string(),
            mc_version: mc_version.unwrap_or("any").to_string(),
            loader: "modpack".to_string(),
        })?;

    let primary_file = chosen
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| chosen.files.first())
        .ok_or_else(|| ModrinthError::NoCompatibleVersion {
            project: project_id_or_slug.to_string(),
            mc_version: mc_version.unwrap_or("any").to_string(),
            loader: "modpack".to_string(),
        })?;

    let safe_name = validate_remote_filename(&primary_file.filename)?;
    let dest = cache_dir.join("modpacks").join(safe_name);
    let item = DownloadItem {
        urls: vec![primary_file.url.clone()],
        dest: dest.clone(),
        expected_hash: primary_file.hashes.sha1.clone().map(ExpectedHash::Sha1),
        expected_size: Some(primary_file.size),
        label: primary_file.filename.clone(),
    };
    net.download_file(&item, cancel, |_| {}).await?;
    Ok(dest)
}

pub async fn fetch_remote_image(
    net: &NetClient,
    cache_dir: &Path,
    image_url: &str,
    max_dim: u32,
    cancel: &CancellationToken,
) -> Option<PathBuf> {
    let trimmed = image_url.trim();
    if !(trimmed.starts_with("https://") || trimmed.starts_with("http://")) {
        return None;
    }
    let lower = trimmed.to_ascii_lowercase();
    if lower.ends_with(".svg") || lower.contains(".svg?") || lower.ends_with(".gif") {
        return None;
    }
    use sha2::{Digest, Sha256};
    let hash = hex::encode(&Sha256::digest(trimmed.as_bytes())[..12]);
    let dest = cache_dir.join(format!("img_{hash}.png"));
    if dest.is_file() {
        return Some(dest);
    }
    let bytes = net.fetch_bytes(trimmed, cancel).await.ok()?;
    let img = image::load_from_memory(&bytes).ok()?;
    let _ = fs::create_dir_all(cache_dir);
    let final_img = if img.width() > max_dim || img.height() > max_dim {
        img.thumbnail(max_dim, max_dim)
    } else {
        img
    };
    final_img.save(&dest).ok()?;
    Some(dest)
}

pub async fn fetch_project_versions(
    net: &NetClient,
    project_id_or_slug: &str,
    kind: ContentKind,
    mc_version: Option<&str>,
    loader: Option<&str>,
    cancel: &CancellationToken,
) -> Result<Vec<ModrinthVersion>, ModrinthError> {
    let query_versions = |mc: Option<&str>, ldr: Option<&str>| {
        let mut url =
            url::Url::parse(&format!("{MODRINTH_API}/project/{project_id_or_slug}/version"))
                .unwrap();
        {
            let mut pairs = url.query_pairs_mut();
            if let Some(m) = mc.filter(|s| !s.is_empty()) {
                pairs.append_pair("game_versions", &serde_json::to_string(&[m]).unwrap());
            }
            if kind == ContentKind::Mod
                && let Some(l) = ldr.filter(|s| !s.is_empty() && *s != "vanilla")
            {
                pairs.append_pair("loaders", &serde_json::to_string(&[l]).unwrap());
            }
        }
        url
    };

    let url = query_versions(mc_version, loader);
    let mut versions: Vec<ModrinthVersion> = net.fetch_json(url.as_str(), cancel).await?;
    if versions.is_empty() && kind == ContentKind::Mod && loader == Some("quilt") {
        let fallback_url = query_versions(mc_version, Some("fabric"));
        versions = net.fetch_json(fallback_url.as_str(), cancel).await?;
    }
    if versions.is_empty() && kind != ContentKind::Mod && mc_version.is_some() {
        let fallback_url = query_versions(None, None);
        versions = net.fetch_json(fallback_url.as_str(), cancel).await?;
    }
    Ok(versions)
}

pub async fn install_modrinth_version(
    net: &NetClient,
    instance_dir: &Path,
    version: &ModrinthVersion,
    kind: ContentKind,
    mc_version: &str,
    loader: &str,
    cancel: &CancellationToken,
) -> Result<Vec<String>, ModrinthError> {
    let primary_file = version
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| version.files.first())
        .ok_or_else(|| ModrinthError::NoCompatibleVersion {
            project: version.project_id.clone(),
            mc_version: mc_version.to_string(),
            loader: loader.to_string(),
        })?;

    let target_dir = instance_dir.join(kind.install_subdir());
    fs::create_dir_all(&target_dir)?;

    let mut registry = ContentRegistry::load(instance_dir);
    let prev_filename = registry
        .entries
        .get(&version.project_id)
        .map(|e| e.filename.clone());

    let safe_name = validate_remote_filename(&primary_file.filename)?;
    let dest = target_dir.join(safe_name);
    let item = DownloadItem {
        urls: vec![primary_file.url.clone()],
        dest: dest.clone(),
        expected_hash: primary_file.hashes.sha1.clone().map(ExpectedHash::Sha1),
        expected_size: Some(primary_file.size),
        label: safe_name.to_string(),
    };
    net.download_file(&item, cancel, |_| {}).await?;

    if let Some(prev) = prev_filename
        && prev != safe_name
        && validate_remote_filename(&prev).is_ok()
    {
        let _ = fs::remove_file(target_dir.join(&prev));
    }

    let sha1 = primary_file
        .hashes
        .sha1
        .clone()
        .unwrap_or_else(|| compute_sha1_file(&dest).unwrap_or_default());

    let entry = registry
        .entries
        .entry(version.project_id.clone())
        .or_insert_with(|| InstalledContentEntry {
            project_id: version.project_id.clone(),
            version_id: version.id.clone(),
            filename: primary_file.filename.clone(),
            sha1: sha1.clone(),
            explicit: true,
            required_by: Vec::new(),
        });
    entry.version_id = version.id.clone();
    entry.filename = primary_file.filename.clone();
    entry.sha1 = sha1;
    entry.explicit = true;
    let _ = registry.save(instance_dir);

    let mut installed_files = vec![primary_file.filename.clone()];
    if kind == ContentKind::Mod {
        let mut ctx = InstallCtx {
            net,
            instance_dir,
            mc_version,
            loader,
            cancel,
            registry,
            visited: BTreeSet::from([version.project_id.clone()]),
            installed_files: Vec::new(),
        };
        for dep in &version.dependencies {
            if dep.dependency_type != "required" {
                continue;
            }
            let dep_pid = match dep.project_id.as_deref() {
                Some(pid) if !pid.trim().is_empty() => Some(pid.to_string()),
                _ => match dep.version_id.as_deref() {
                    Some(vid) if !vid.trim().is_empty() => {
                        let ver_url = format!("{MODRINTH_API}/version/{vid}");
                        net.fetch_json::<ModrinthVersion>(&ver_url, cancel)
                            .await
                            .ok()
                            .map(|v| v.project_id)
                    }
                    _ => None,
                },
            };
            if let Some(pid) = dep_pid {
                let _ = install_recursive(
                    &mut ctx,
                    &pid,
                    ContentKind::Mod,
                    Some(&version.project_id),
                )
                .await;
            }
        }
        let _ = ctx.registry.save(instance_dir);
        installed_files.extend(ctx.installed_files);
    }

    Ok(installed_files)
}

pub async fn get_project_details(
    net: &NetClient,
    id_or_slug: &str,
    cancel: &CancellationToken,
) -> Result<ModrinthProjectDetails, ModrinthError> {
    let url = format!("{MODRINTH_API}/project/{id_or_slug}");
    Ok(net.fetch_json(&url, cancel).await?)
}

pub async fn install_modrinth_project(
    net: &NetClient,
    instance_dir: &Path,
    project_id_or_slug: &str,
    kind: ContentKind,
    mc_version: &str,
    loader: &str,
    cancel: &CancellationToken,
) -> Result<Vec<String>, ModrinthError> {
    let mut ctx = InstallCtx {
        net,
        instance_dir,
        mc_version,
        loader,
        cancel,
        registry: ContentRegistry::load(instance_dir),
        visited: BTreeSet::new(),
        installed_files: Vec::new(),
    };

    install_recursive(&mut ctx, project_id_or_slug, kind, None).await?;

    let _ = ctx.registry.save(instance_dir);
    Ok(ctx.installed_files)
}

struct InstallCtx<'a> {
    net: &'a NetClient,
    instance_dir: &'a Path,
    mc_version: &'a str,
    loader: &'a str,
    cancel: &'a CancellationToken,
    registry: ContentRegistry,
    visited: BTreeSet<String>,
    installed_files: Vec<String>,
}

fn install_recursive<'ctx, 'a: 'ctx>(
    ctx: &'ctx mut InstallCtx<'a>,
    project_id_or_slug: &'ctx str,
    kind: ContentKind,
    parent_project_id: Option<&'ctx str>,
) -> futures_box::BoxFuture<'ctx, Result<(), ModrinthError>> {
    Box::pin(async move {
        if !ctx.visited.insert(project_id_or_slug.to_string()) {
            return Ok(());
        }

        let versions = fetch_project_versions(
            ctx.net,
            project_id_or_slug,
            kind,
            Some(ctx.mc_version),
            Some(ctx.loader),
            ctx.cancel,
        )
        .await?;
        let chosen = versions
            .into_iter()
            .next()
            .ok_or_else(|| ModrinthError::NoCompatibleVersion {
                project: project_id_or_slug.to_string(),
                mc_version: ctx.mc_version.to_string(),
                loader: ctx.loader.to_string(),
            })?;

        let primary_file = chosen
            .files
            .iter()
            .find(|f| f.primary)
            .or_else(|| chosen.files.first())
            .ok_or_else(|| ModrinthError::NoCompatibleVersion {
                project: project_id_or_slug.to_string(),
                mc_version: ctx.mc_version.to_string(),
                loader: ctx.loader.to_string(),
            })?;

        let target_dir = ctx.instance_dir.join(kind.install_subdir());
        fs::create_dir_all(&target_dir)?;

        let prev_filename = ctx
            .registry
            .entries
            .get(&chosen.project_id)
            .map(|e| e.filename.clone());

        let safe_name = validate_remote_filename(&primary_file.filename)?;
        let dest = target_dir.join(safe_name);
        let item = DownloadItem {
            urls: vec![primary_file.url.clone()],
            dest: dest.clone(),
            expected_hash: primary_file.hashes.sha1.clone().map(ExpectedHash::Sha1),
            expected_size: Some(primary_file.size),
            label: safe_name.to_string(),
        };
        ctx.net.download_file(&item, ctx.cancel, |_| {}).await?;

        if let Some(prev) = prev_filename
            && prev != safe_name
            && validate_remote_filename(&prev).is_ok()
        {
            let _ = fs::remove_file(target_dir.join(&prev));
        }

        ctx.installed_files.push(primary_file.filename.clone());

        let sha1 = primary_file
            .hashes
            .sha1
            .clone()
            .unwrap_or_else(|| compute_sha1_file(&dest).unwrap_or_default());

        let entry = ctx
            .registry
            .entries
            .entry(chosen.project_id.clone())
            .or_insert_with(|| InstalledContentEntry {
                project_id: chosen.project_id.clone(),
                version_id: chosen.id.clone(),
                filename: primary_file.filename.clone(),
                sha1: sha1.clone(),
                explicit: parent_project_id.is_none(),
                required_by: Vec::new(),
            });
        entry.version_id = chosen.id.clone();
        entry.filename = primary_file.filename.clone();
        entry.sha1 = sha1;
        if let Some(parent) = parent_project_id {
            if !entry.required_by.iter().any(|p| p == parent) {
                entry.required_by.push(parent.to_string());
            }
        } else {
            entry.explicit = true;
        }

        if kind == ContentKind::Mod {
            let current_pid = chosen.project_id.clone();
            for dep in chosen.dependencies {
                if dep.dependency_type != "required" {
                    continue;
                }
                let dep_pid = match dep.project_id {
                    Some(pid) if !pid.trim().is_empty() => Some(pid),
                    _ => match dep.version_id {
                        Some(vid) if !vid.trim().is_empty() => {
                            let ver_url = format!("{MODRINTH_API}/version/{vid}");
                            ctx.net
                                .fetch_json::<ModrinthVersion>(&ver_url, ctx.cancel)
                                .await
                                .ok()
                                .map(|v| v.project_id)
                        }
                        _ => None,
                    },
                };
                if let Some(pid) = dep_pid {
                    let _ = install_recursive(
                        ctx,
                        &pid,
                        ContentKind::Mod,
                        Some(&current_pid),
                    )
                    .await;
                }
            }
        }

        Ok(())
    })
}

pub async fn install_optimization_pack(
    net: &NetClient,
    instance_dir: &Path,
    mc_version: &str,
    loader: &str,
    cancel: &CancellationToken,
) -> (Vec<String>, Vec<String>) {
    let mut installed = Vec::new();
    let mut skipped = Vec::new();
    for slug in OPTIMIZATION_PACK_SLUGS {
        match install_modrinth_project(
            net,
            instance_dir,
            slug,
            ContentKind::Mod,
            mc_version,
            loader,
            cancel,
        )
        .await
        {
            Ok(files) => installed.extend(files),
            Err(_) => skipped.push((*slug).to_string()),
        }
    }
    (installed, skipped)
}

pub async fn check_mod_updates(
    net: &NetClient,
    instance_dir: &Path,
    mc_version: &str,
    loader: &str,
    cancel: &CancellationToken,
) -> Result<Vec<ModrinthUpdateCandidate>, ModrinthError> {
    if loader.trim().is_empty() || loader == "vanilla" {
        return Ok(Vec::new());
    }
    let mods_dir = instance_dir.join("mods");
    let Ok(entries) = fs::read_dir(&mods_dir) else {
        return Ok(Vec::new());
    };

    let mut hash_to_file: BTreeMap<String, String> = BTreeMap::new();
    for entry in entries.flatten() {
        let p = entry.path();
        if p.is_file()
            && p.extension().and_then(|e| e.to_str()) == Some("jar")
            && let Some(name) = p.file_name().and_then(|n| n.to_str())
            && !name.starts_with('.')
            && let Ok(sha1) = compute_sha1_file(&p)
        {
            hash_to_file.insert(sha1, name.to_string());
        }
    }

    if hash_to_file.is_empty() {
        return Ok(Vec::new());
    }

    let hashes: Vec<String> = hash_to_file.keys().cloned().collect();
    let payload = serde_json::json!({
        "hashes": hashes,
        "algorithm": "sha1",
        "loaders": [loader],
        "game_versions": [mc_version],
    });

    let req = net
        .inner()
        .post(format!("{MODRINTH_API}/version_files/update"))
        .json(&payload);

    let resp = tokio::select! {
        _ = cancel.cancelled() => return Err(ModrinthError::Net(NetError::Cancelled)),
        r = req.send() => r?,
    };
    if !resp.status().is_success() {
        return Ok(Vec::new());
    }
    let map: BTreeMap<String, ModrinthVersion> = resp.json().await.unwrap_or_default();

    let mut updates = Vec::new();
    for (old_sha1, latest) in map {
        let latest_sha1 = latest
            .files
            .iter()
            .find(|f| f.primary)
            .or_else(|| latest.files.first())
            .and_then(|f| f.hashes.sha1.as_deref());
        if latest_sha1 != Some(old_sha1.as_str())
            && let Some(current_filename) = hash_to_file.get(&old_sha1)
        {
            updates.push(ModrinthUpdateCandidate {
                current_filename: current_filename.clone(),
                project_id: latest.project_id.clone(),
                latest_version: latest,
            });
        }
    }
    Ok(updates)
}

pub async fn migrate_mods_to_version(
    net: &NetClient,
    src_instance: &Path,
    dst_instance: &Path,
    target_mc: &str,
    target_loader: &str,
    cancel: &CancellationToken,
) -> Result<MigrationReport, ModrinthError> {
    let mut report = MigrationReport::default();
    let src_mods = src_instance.join("mods");
    let dst_mods = dst_instance.join("mods");
    let Ok(entries) = fs::read_dir(&src_mods) else {
        return Ok(report);
    };
    fs::create_dir_all(&dst_mods)?;

    let mut hash_to_path: BTreeMap<String, PathBuf> = BTreeMap::new();
    for entry in entries.flatten() {
        let p = entry.path();
        if p.is_file()
            && p.extension().and_then(|e| e.to_str()) == Some("jar")
            && let Ok(sha1) = compute_sha1_file(&p)
        {
            hash_to_path.insert(sha1, p);
        }
    }
    if hash_to_path.is_empty() {
        return Ok(report);
    }

    let hashes: Vec<String> = hash_to_path.keys().cloned().collect();
    let payload = serde_json::json!({
        "hashes": hashes,
        "algorithm": "sha1",
        "loaders": [target_loader],
        "game_versions": [target_mc],
    });
    let resp = net
        .inner()
        .post(format!("{MODRINTH_API}/version_files/update"))
        .json(&payload)
        .send()
        .await?;
    let matched: BTreeMap<String, ModrinthVersion> = if resp.status().is_success() {
        resp.json().await.unwrap_or_default()
    } else {
        BTreeMap::new()
    };

    for (sha1, src_path) in hash_to_path {
        let orig_name = src_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("mod.jar")
            .to_string();
        if let Some(ver) = matched.get(&sha1)
            && let Some(file) = ver.files.iter().find(|f| f.primary).or_else(|| ver.files.first())
            && let Ok(safe_name) = validate_remote_filename(&file.filename)
        {
            let item = DownloadItem {
                urls: vec![file.url.clone()],
                dest: dst_mods.join(safe_name),
                expected_hash: file.hashes.sha1.clone().map(ExpectedHash::Sha1),
                expected_size: Some(file.size),
                label: safe_name.to_string(),
            };
            if net.download_file(&item, cancel, |_| {}).await.is_ok() {
                report.migrated.push(safe_name.to_string());
                continue;
            }
        }
        report.incompatible.push(orig_name);
    }

    Ok(report)
}

mod futures_box {
    use std::future::Future;
    use std::pin::Pin;
    pub type BoxFuture<'a, T> = Pin<Box<dyn Future<Output = T> + Send + 'a>>;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_path_traversal_remote_filenames() {
        assert!(validate_remote_filename("sodium-fabric-0.6.0.jar").is_ok());
        assert!(validate_remote_filename("../evil.jar").is_err());
        assert!(validate_remote_filename(r"..\evil.jar").is_err());
        assert!(validate_remote_filename("sub/mod.jar").is_err());
        assert!(validate_remote_filename("C:evil.jar").is_err());
        assert!(validate_remote_filename("").is_err());
        assert!(validate_remote_filename("..").is_err());
    }
}
