use crate::instance::{CreateInstanceParams, InstanceRecord, LoaderKind, create_instance};
use crate::nbt_servers::read_servers_dat;
use nl_platform::LauncherPaths;
use serde::Deserialize;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

const SENSITIVE_FILENAMES: &[&str] = &[
    "accounts.json",
    "launcher_accounts.json",
    "launcher_accounts_microsoft_store.json",
    "launcher_profiles.json",
    "tlauncherprofiles.json",
    "tlauncher-2.0.properties",
    "usercache.json",
    "realms_persistence.json",
    "app.db",
    "app.db-wal",
    "app.db-shm",
    "vault.enc",
    "launcher_msa_credentials.bin",
    "launcher_entitlements.json",
    "credentials.json",
    "tokens.json",
    "session.json",
];

const SENSITIVE_EXTENSIONS: &[&str] = &["db", "sqlite", "sqlite3", "db-wal", "db-shm", "enc", "key"];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum ExternalLauncherKind {
    PrismLauncher,
    ModrinthApp,
    TLauncher,
    LegacyLauncher,
    LunarClient,
    OfficialMinecraft,
    CustomFolder,
}

impl ExternalLauncherKind {
    pub fn display_name(self) -> &'static str {
        match self {
            Self::PrismLauncher => "Prism Launcher",
            Self::ModrinthApp => "Modrinth App",
            Self::TLauncher => "TLauncher",
            Self::LegacyLauncher => "Legacy Launcher",
            Self::LunarClient => "Lunar Client",
            Self::OfficialMinecraft => "Minecraft Launcher",
            Self::CustomFolder => "Custom Folder",
        }
    }

    pub fn id(self) -> &'static str {
        match self {
            Self::PrismLauncher => "prism",
            Self::ModrinthApp => "modrinth",
            Self::TLauncher => "tlauncher",
            Self::LegacyLauncher => "legacy",
            Self::LunarClient => "lunar",
            Self::OfficialMinecraft => "official",
            Self::CustomFolder => "custom",
        }
    }
}

#[derive(Debug, Clone)]
pub struct DetectedProfile {
    pub id: String,
    pub launcher: ExternalLauncherKind,
    pub name: String,
    pub mc_version: String,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
    pub game_dir: PathBuf,
    pub mod_count: usize,
    pub resourcepack_count: usize,
    pub shader_count: usize,
    pub server_count: usize,
    pub world_count: usize,
    pub screenshot_count: usize,
    pub has_options_txt: bool,
    pub has_config_dir: bool,
    pub proprietary_hud_notice: bool,
}

#[derive(Debug, Clone)]
pub struct DetectedLauncher {
    pub kind: ExternalLauncherKind,
    pub root_dir: PathBuf,
    pub profiles: Vec<DetectedProfile>,
}

#[derive(Debug, Clone)]
pub struct MigrationSelection {
    pub custom_name: String,
    pub copy_mods: bool,
    pub copy_config: bool,
    pub copy_resourcepacks: bool,
    pub copy_shaderpacks: bool,
    pub copy_options_txt: bool,
    pub copy_servers_dat: bool,
    pub copy_saves: bool,
    pub copy_screenshots: bool,
    pub enable_nightclient: bool,
}

impl Default for MigrationSelection {
    fn default() -> Self {
        Self {
            custom_name: String::new(),
            copy_mods: true,
            copy_config: true,
            copy_resourcepacks: true,
            copy_shaderpacks: true,
            copy_options_txt: true,
            copy_servers_dat: true,
            copy_saves: false,
            copy_screenshots: false,
            enable_nightclient: true,
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct MigrationResult {
    pub instance_id: String,
    pub instance_name: String,
    pub files_copied: usize,
    pub bytes_copied: u64,
    pub skipped_nightclient_duplicates: usize,
}

#[derive(Debug, Clone, Default)]
pub struct MigrationSearchRoots {
    pub prism_roots: Vec<PathBuf>,
    pub modrinth_roots: Vec<PathBuf>,
    pub tlauncher_dot_dirs: Vec<PathBuf>,
    pub minecraft_dirs: Vec<PathBuf>,
    pub legacy_game_dirs: Vec<PathBuf>,
    pub lunar_dirs: Vec<PathBuf>,
}

impl MigrationSearchRoots {
    pub fn from_base_dirs(home: Option<&Path>, roaming: Option<&Path>, local: Option<&Path>) -> Self {
        let mut roots = Self::default();

        if let Some(roam) = roaming {
            roots.prism_roots.push(roam.join("PrismLauncher"));
            roots.prism_roots.push(roam.join("PolyMC"));
            roots.modrinth_roots.push(roam.join("ModrinthApp"));
            roots.modrinth_roots.push(roam.join("com.modrinth.theseus"));
            roots.tlauncher_dot_dirs.push(roam.join(".tlauncher"));
            roots.minecraft_dirs.push(roam.join(".minecraft"));
            roots.minecraft_dirs.push(roam.join("minecraft"));
            roots
                .legacy_game_dirs
                .push(roam.join(".tlauncher").join("legacy").join("Minecraft").join("game"));
            roots
                .legacy_game_dirs
                .push(roam.join("tlauncher").join("legacy").join("Minecraft").join("game"));
            roots.legacy_game_dirs.push(roam.join(".legacylauncher"));
        }

        if let Some(loc) = local {
            roots.prism_roots.push(loc.join("PrismLauncher"));
            roots.modrinth_roots.push(loc.join("ModrinthApp"));
        }

        if let Some(h) = home {
            let xdg_data = std::env::var_os("XDG_DATA_HOME")
                .filter(|v| !v.is_empty())
                .map(PathBuf::from)
                .unwrap_or_else(|| h.join(".local").join("share"));
            let xdg_config = std::env::var_os("XDG_CONFIG_HOME")
                .filter(|v| !v.is_empty())
                .map(PathBuf::from)
                .unwrap_or_else(|| h.join(".config"));

            roots.prism_roots.push(xdg_data.join("PrismLauncher"));
            roots.prism_roots.push(
                h.join(".var")
                    .join("app")
                    .join("org.prismlauncher.PrismLauncher")
                    .join("data")
                    .join("PrismLauncher"),
            );
            roots.prism_roots.push(xdg_data.join("PolyMC"));

            roots.modrinth_roots.push(xdg_data.join("ModrinthApp"));
            roots.modrinth_roots.push(xdg_config.join("ModrinthApp"));
            roots.modrinth_roots.push(xdg_config.join("com.modrinth.theseus"));
            roots.modrinth_roots.push(
                h.join(".var")
                    .join("app")
                    .join("com.modrinth.ModrinthApp")
                    .join("data")
                    .join("ModrinthApp"),
            );

            roots.tlauncher_dot_dirs.push(h.join(".tlauncher"));
            roots.minecraft_dirs.push(h.join(".minecraft"));
            roots
                .legacy_game_dirs
                .push(h.join(".tlauncher").join("legacy").join("Minecraft").join("game"));
            roots.legacy_game_dirs.push(h.join(".legacylauncher"));
            roots.lunar_dirs.push(h.join(".lunarclient"));

            #[cfg(target_os = "macos")]
            {
                let app_sup = h.join("Library").join("Application Support");
                roots.prism_roots.push(app_sup.join("PrismLauncher"));
                roots.modrinth_roots.push(app_sup.join("ModrinthApp"));
                roots.modrinth_roots.push(app_sup.join("com.modrinth.theseus"));
                roots.minecraft_dirs.push(app_sup.join("minecraft"));
                roots.legacy_game_dirs.push(
                    app_sup
                        .join("tlauncher")
                        .join("legacy")
                        .join("Minecraft")
                        .join("game"),
                );
            }
        }

        dedup_paths(&mut roots.prism_roots);
        dedup_paths(&mut roots.modrinth_roots);
        dedup_paths(&mut roots.tlauncher_dot_dirs);
        dedup_paths(&mut roots.minecraft_dirs);
        dedup_paths(&mut roots.legacy_game_dirs);
        dedup_paths(&mut roots.lunar_dirs);
        roots
    }

    pub fn system_default() -> Self {
        if let Ok(override_root) = std::env::var("NIGHTLAUNCHER_MIGRATION_ROOT") {
            let trimmed = override_root.trim();
            if !trimmed.is_empty() {
                let base = PathBuf::from(trimmed);
                let roaming = base.join("roaming");
                let local = base.join("local");
                let home = base.join("home");
                let mut r = Self::from_base_dirs(
                    Some(if home.is_dir() { &home } else { &base }),
                    Some(if roaming.is_dir() { &roaming } else { &base }),
                    Some(if local.is_dir() { &local } else { &base }),
                );
                r.prism_roots.push(base.join("PrismLauncher"));
                r.modrinth_roots.push(base.join("ModrinthApp"));
                r.tlauncher_dot_dirs.push(base.join(".tlauncher"));
                r.minecraft_dirs.push(base.join(".minecraft"));
                r.legacy_game_dirs
                    .push(base.join(".tlauncher").join("legacy").join("Minecraft").join("game"));
                r.lunar_dirs.push(base.join(".lunarclient"));
                dedup_paths(&mut r.prism_roots);
                dedup_paths(&mut r.modrinth_roots);
                dedup_paths(&mut r.tlauncher_dot_dirs);
                dedup_paths(&mut r.minecraft_dirs);
                dedup_paths(&mut r.legacy_game_dirs);
                dedup_paths(&mut r.lunar_dirs);
                return r;
            }
        }

        let home = std::env::var_os("USERPROFILE")
            .or_else(|| std::env::var_os("HOME"))
            .map(PathBuf::from);
        let roaming = std::env::var_os("APPDATA").map(PathBuf::from);
        let local = std::env::var_os("LOCALAPPDATA").map(PathBuf::from);
        Self::from_base_dirs(home.as_deref(), roaming.as_deref(), local.as_deref())
    }
}

pub fn discover_external_launchers() -> Vec<DetectedLauncher> {
    discover_external_launchers_in(&MigrationSearchRoots::system_default())
}

pub fn discover_external_launchers_in(roots: &MigrationSearchRoots) -> Vec<DetectedLauncher> {
    let mut found = Vec::new();
    let mut seen_roots = BTreeSet::new();

    for root in &roots.prism_roots {
        if let Some(l) = scan_prism_root(root)
            && seen_roots.insert(canonical_key(&l.root_dir))
        {
            found.push(l);
        }
    }

    for root in &roots.modrinth_roots {
        if let Some(l) = scan_modrinth_root(root)
            && seen_roots.insert(canonical_key(&l.root_dir))
        {
            found.push(l);
        }
    }

    for game_dir in &roots.legacy_game_dirs {
        if let Some(l) = scan_versioned_mc_root(game_dir, ExternalLauncherKind::LegacyLauncher)
            && seen_roots.insert(canonical_key(&l.root_dir))
        {
            found.push(l);
        }
    }

    let has_tlauncher_dir = roots.tlauncher_dot_dirs.iter().any(|p| p.is_dir());
    let mut primary_mc_dir: Option<PathBuf> = None;

    for mc_dir in &roots.minecraft_dirs {
        if !mc_dir.is_dir() {
            continue;
        }
        if primary_mc_dir.is_none() {
            primary_mc_dir = Some(mc_dir.clone());
        }
        let is_tl = has_tlauncher_dir
            || mc_dir.join("TLauncherAdditional.json").is_file()
            || mc_dir.join("TlauncherProfiles.json").is_file();
        let kind = if is_tl {
            ExternalLauncherKind::TLauncher
        } else {
            ExternalLauncherKind::OfficialMinecraft
        };
        if let Some(l) = scan_versioned_mc_root(mc_dir, kind)
            && seen_roots.insert(canonical_key(&l.root_dir))
        {
            found.push(l);
        }
    }

    for lunar_dir in &roots.lunar_dirs {
        if let Some(l) = scan_lunar_root(lunar_dir, primary_mc_dir.as_deref())
            && seen_roots.insert(canonical_key(&l.root_dir))
        {
            found.push(l);
        }
    }

    found
}

pub fn inspect_custom_folder(path: &Path) -> Option<DetectedLauncher> {
    if !path.is_dir() {
        return None;
    }
    if let Some(prism) = scan_prism_root(path) {
        return Some(prism);
    }
    if let Some(mr) = scan_modrinth_root(path) {
        return Some(mr);
    }
    if (path.join("instance.cfg").is_file() || path.join("mmc-pack.json").is_file())
        && let Some(p) = parse_prism_instance_dir(path)
    {
        return Some(DetectedLauncher {
            kind: ExternalLauncherKind::PrismLauncher,
            root_dir: path.to_path_buf(),
            profiles: vec![p],
        });
    }
    if path.join("versions").is_dir() {
        let kind = if path.join("TLauncherAdditional.json").is_file() {
            ExternalLauncherKind::TLauncher
        } else {
            ExternalLauncherKind::CustomFolder
        };
        if let Some(l) = scan_versioned_mc_root(path, kind) {
            return Some(l);
        }
    }

    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("Imported Instance")
        .to_string();
    let (mc_ver, loader, loader_ver) = infer_profile_metadata_safe(path);
    let profile = build_profile_stats(
        format!("custom:{}", path.display()),
        ExternalLauncherKind::CustomFolder,
        name,
        mc_ver,
        loader,
        loader_ver,
        path.to_path_buf(),
        false,
    );
    Some(DetectedLauncher {
        kind: ExternalLauncherKind::CustomFolder,
        root_dir: path.to_path_buf(),
        profiles: vec![profile],
    })
}

pub fn execute_migration(
    paths: &LauncherPaths,
    profile: &DetectedProfile,
    selection: &MigrationSelection,
) -> std::io::Result<(InstanceRecord, MigrationResult)> {
    let chosen_name = if selection.custom_name.trim().is_empty() {
        profile.name.clone()
    } else {
        selection.custom_name.trim().to_string()
    };

    let record = create_instance(
        paths,
        CreateInstanceParams {
            name: chosen_name.clone(),
            mc_version: profile.mc_version.clone(),
            loader: profile.loader,
            loader_version: profile.loader_version.clone(),
            nightclient_enabled: selection.enable_nightclient
                && profile.loader == LoaderKind::Fabric,
            group: Some(profile.launcher.display_name().to_string()),
        },
    )?;

    let mut result = MigrationResult {
        instance_id: record.config.id.clone(),
        instance_name: record.config.name.clone(),
        files_copied: 0,
        bytes_copied: 0,
        skipped_nightclient_duplicates: 0,
    };

    let src = &profile.game_dir;
    let dst = &record.dir;

    if selection.copy_mods {
        copy_mods_dir(&src.join("mods"), &dst.join("mods"), &mut result)?;
    }
    if selection.copy_config {
        copy_safe_dir(&src.join("config"), &dst.join("config"), 0, &mut result)?;
    }
    if selection.copy_resourcepacks {
        copy_safe_dir(
            &src.join("resourcepacks"),
            &dst.join("resourcepacks"),
            0,
            &mut result,
        )?;
    }
    if selection.copy_shaderpacks {
        copy_safe_dir(
            &src.join("shaderpacks"),
            &dst.join("shaderpacks"),
            0,
            &mut result,
        )?;
    }
    if selection.copy_saves {
        copy_safe_dir(&src.join("saves"), &dst.join("saves"), 0, &mut result)?;
    }
    if selection.copy_screenshots {
        copy_safe_dir(
            &src.join("screenshots"),
            &dst.join("screenshots"),
            0,
            &mut result,
        )?;
    }
    if selection.copy_options_txt {
        copy_single_safe_file(&src.join("options.txt"), &dst.join("options.txt"), &mut result)?;
    }
    if selection.copy_servers_dat {
        copy_single_safe_file(&src.join("servers.dat"), &dst.join("servers.dat"), &mut result)?;
    }

    Ok((record, result))
}

fn scan_prism_root(root: &Path) -> Option<DetectedLauncher> {
    let instances_dir = root.join("instances");
    if !instances_dir.is_dir() {
        return None;
    }
    let mut profiles = Vec::new();
    let entries = fs::read_dir(&instances_dir).ok()?;
    for entry in entries.flatten() {
        let dir = entry.path();
        if !dir.is_dir() || is_symlink(&dir) {
            continue;
        }
        let Some(fname) = dir.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if fname.starts_with('.') || fname.starts_with('_') {
            continue;
        }
        if let Some(p) = parse_prism_instance_dir(&dir) {
            profiles.push(p);
        }
    }
    if profiles.is_empty() {
        return None;
    }
    profiles.sort_by_key(|a| a.name.to_lowercase());
    Some(DetectedLauncher {
        kind: ExternalLauncherKind::PrismLauncher,
        root_dir: root.to_path_buf(),
        profiles,
    })
}

fn parse_prism_instance_dir(dir: &Path) -> Option<DetectedProfile> {
    let cfg_path = dir.join("instance.cfg");
    let pack_path = dir.join("mmc-pack.json");
    if !cfg_path.is_file() && !pack_path.is_file() {
        return None;
    }

    let mut name = dir
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("Prism Instance")
        .to_string();
    let mut cfg_mc_ver: Option<String> = None;

    if let Ok(text) = fs::read_to_string(&cfg_path) {
        for line in text.lines() {
            if let Some((k, v)) = line.split_once('=') {
                let key = k.trim();
                let val = v.trim();
                if key == "name" && !val.is_empty() {
                    name = val.to_string();
                } else if key == "IntendedVersion" && !val.is_empty() {
                    cfg_mc_ver = Some(val.to_string());
                }
            }
        }
    }

    #[derive(Deserialize)]
    struct MmcPack {
        #[serde(default)]
        components: Vec<MmcComponent>,
    }
    #[derive(Deserialize)]
    struct MmcComponent {
        uid: String,
        #[serde(default)]
        version: String,
    }

    let mut mc_version = cfg_mc_ver.unwrap_or_default();
    let mut loader = LoaderKind::Vanilla;
    let mut loader_version: Option<String> = None;

    if let Ok(raw) = fs::read(&pack_path)
        && let Ok(pack) = serde_json::from_slice::<MmcPack>(&raw)
    {
        for comp in pack.components {
            match comp.uid.as_str() {
                "net.minecraft" if !comp.version.is_empty() => {
                    mc_version = comp.version;
                }
                "net.fabricmc.fabric-loader" => {
                    loader = LoaderKind::Fabric;
                    if !comp.version.is_empty() {
                        loader_version = Some(comp.version);
                    }
                }
                "org.quiltmc.quilt-loader" => {
                    loader = LoaderKind::Quilt;
                    if !comp.version.is_empty() {
                        loader_version = Some(comp.version);
                    }
                }
                "net.neoforged" => {
                    loader = LoaderKind::NeoForge;
                    if !comp.version.is_empty() {
                        loader_version = Some(comp.version);
                    }
                }
                "net.minecraftforge" => {
                    loader = LoaderKind::Forge;
                    if !comp.version.is_empty() {
                        loader_version = Some(comp.version);
                    }
                }
                _ => {}
            }
        }
    }

    let game_dir = if dir.join(".minecraft").is_dir() {
        dir.join(".minecraft")
    } else if dir.join("minecraft").is_dir() {
        dir.join("minecraft")
    } else {
        dir.to_path_buf()
    };

    if mc_version.is_empty() {
        let (inferred_mc, inferred_loader, inferred_lv) = infer_profile_metadata_safe(&game_dir);
        mc_version = inferred_mc;
        if loader == LoaderKind::Vanilla && inferred_loader != LoaderKind::Vanilla {
            loader = inferred_loader;
            loader_version = inferred_lv;
        }
    }

    Some(build_profile_stats(
        format!("prism:{}", dir.display()),
        ExternalLauncherKind::PrismLauncher,
        name,
        mc_version,
        loader,
        loader_version,
        game_dir,
        false,
    ))
}

fn scan_modrinth_root(root: &Path) -> Option<DetectedLauncher> {
    let profiles_dir = root.join("profiles");
    if !profiles_dir.is_dir() {
        return None;
    }

    let mut profiles = Vec::new();
    let entries = fs::read_dir(&profiles_dir).ok()?;
    for entry in entries.flatten() {
        let dir = entry.path();
        if !dir.is_dir() || is_symlink(&dir) {
            continue;
        }
        let Some(fname) = dir.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if fname.starts_with('.') {
            continue;
        }

        let mut name = fname.to_string();
        let mut mc_version = String::new();
        let mut loader = LoaderKind::Vanilla;
        let mut loader_version: Option<String> = None;

        let legacy_profile_json = dir.join("profile.json");
        if legacy_profile_json.is_file()
            && let Ok(raw) = fs::read(&legacy_profile_json)
            && let Ok(val) = serde_json::from_slice::<serde_json::Value>(&raw)
        {
            if let Some(n) = val
                .get("metadata")
                .and_then(|m| m.get("name"))
                .or_else(|| val.get("name"))
                .and_then(|v| v.as_str())
                .filter(|s| !s.trim().is_empty())
            {
                name = n.trim().to_string();
            }
            if let Some(v) = val
                .get("metadata")
                .and_then(|m| m.get("game_version"))
                .or_else(|| val.get("game_version"))
                .and_then(|v| v.as_str())
                .filter(|s| !s.trim().is_empty())
            {
                mc_version = v.trim().to_string();
            }
            if let Some(l) = val
                .get("metadata")
                .and_then(|m| m.get("loader"))
                .or_else(|| val.get("loader"))
                .and_then(|v| v.as_str())
            {
                loader = parse_loader_str(l);
            }
            if let Some(lv) = val
                .get("metadata")
                .and_then(|m| m.get("loader_version"))
                .and_then(|m| m.get("id").or(Some(m)))
                .or_else(|| val.get("loader_version"))
                .and_then(|v| v.as_str())
                .filter(|s| !s.trim().is_empty())
            {
                loader_version = Some(lv.trim().to_string());
            }
        }

        if mc_version.is_empty() || loader == LoaderKind::Vanilla {
            let (inferred_mc, inferred_loader, inferred_lv) = infer_profile_metadata_safe(&dir);
            if mc_version.is_empty() {
                mc_version = inferred_mc;
            }
            if loader == LoaderKind::Vanilla && inferred_loader != LoaderKind::Vanilla {
                loader = inferred_loader;
                if loader_version.is_none() {
                    loader_version = inferred_lv;
                }
            }
        }

        profiles.push(build_profile_stats(
            format!("modrinth:{}", dir.display()),
            ExternalLauncherKind::ModrinthApp,
            name,
            mc_version,
            loader,
            loader_version,
            dir,
            false,
        ));
    }

    if profiles.is_empty() {
        return None;
    }
    profiles.sort_by_key(|a| a.name.to_lowercase());
    Some(DetectedLauncher {
        kind: ExternalLauncherKind::ModrinthApp,
        root_dir: root.to_path_buf(),
        profiles,
    })
}

fn scan_versioned_mc_root(root: &Path, kind: ExternalLauncherKind) -> Option<DetectedLauncher> {
    if !root.is_dir() {
        return None;
    }
    let versions_dir = root.join("versions");
    let mut profiles = Vec::new();

    if versions_dir.is_dir()
        && let Ok(entries) = fs::read_dir(&versions_dir)
    {
        for entry in entries.flatten() {
            let vdir = entry.path();
            if !vdir.is_dir() || is_symlink(&vdir) {
                continue;
            }
            let Some(folder_name) = vdir.file_name().and_then(|n| n.to_str()) else {
                continue;
            };
            if folder_name.starts_with('.') {
                continue;
            }
            let json_path = vdir.join(format!("{folder_name}.json"));
            if !json_path.is_file() {
                continue;
            }
            let Ok(raw) = fs::read(&json_path) else {
                continue;
            };
            let Ok(val) = serde_json::from_slice::<serde_json::Value>(&raw) else {
                continue;
            };
            let (mc_ver, loader, loader_ver) = parse_version_json_meta(folder_name, &val);
            let isolated = vdir.join("mods").is_dir()
                || vdir.join("config").is_dir()
                || vdir.join("options.txt").is_file()
                || vdir.join("servers.dat").is_file();
            let game_dir = if isolated { vdir.clone() } else { root.to_path_buf() };

            profiles.push(build_profile_stats(
                format!("{}:{}", kind.id(), vdir.display()),
                kind,
                folder_name.to_string(),
                mc_ver,
                loader,
                loader_ver,
                game_dir,
                false,
            ));
        }
    }

    if profiles.is_empty() {
        let has_any_content = root.join("mods").is_dir()
            || root.join("resourcepacks").is_dir()
            || root.join("shaderpacks").is_dir()
            || root.join("servers.dat").is_file()
            || root.join("options.txt").is_file();
        if !has_any_content {
            return None;
        }
        let (mc_ver, loader, loader_ver) = infer_profile_metadata_safe(root);
        profiles.push(build_profile_stats(
            format!("{}:{}", kind.id(), root.display()),
            kind,
            kind.display_name().to_string(),
            mc_ver,
            loader,
            loader_ver,
            root.to_path_buf(),
            false,
        ));
    }

    profiles.sort_by(|a, b| b.mc_version.cmp(&a.mc_version).then_with(|| a.name.cmp(&b.name)));
    Some(DetectedLauncher {
        kind,
        root_dir: root.to_path_buf(),
        profiles,
    })
}

fn scan_lunar_root(lunar_dir: &Path, shared_mc_dir: Option<&Path>) -> Option<DetectedLauncher> {
    if !lunar_dir.is_dir() {
        return None;
    }

    let mut versions = BTreeSet::new();
    let multiver = lunar_dir.join("offline").join("multiver");
    if multiver.is_dir()
        && let Ok(entries) = fs::read_dir(&multiver)
    {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if let Some(ver) = extract_mc_version_token(&name) {
                versions.insert(ver);
            }
        }
    }

    let settings_game = lunar_dir.join("settings").join("game");
    if settings_game.is_dir()
        && let Ok(entries) = fs::read_dir(&settings_game)
    {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() {
                let name = entry.file_name().to_string_lossy().to_string();
                if let Some(ver) = extract_mc_version_token(&name) {
                    versions.insert(ver);
                }
            }
        }
    }

    let game_dir = shared_mc_dir
        .filter(|d| d.is_dir())
        .map(Path::to_path_buf)
        .unwrap_or_else(|| lunar_dir.to_path_buf());

    if versions.is_empty() {
        let has_lunar_marker = multiver.is_dir()
            || lunar_dir.join("textures").is_dir()
            || lunar_dir.join("profiles").is_dir()
            || settings_game.is_dir();
        if !has_lunar_marker {
            return None;
        }
        let (inferred_mc, _, _) = infer_profile_metadata_safe(&game_dir);
        versions.insert(inferred_mc);
    }

    let mut profiles: Vec<DetectedProfile> = versions
        .into_iter()
        .rev()
        .map(|ver| {
            let ver_profile_dir = lunar_dir.join("profiles").join(&ver);
            let effective_dir = if ver_profile_dir.is_dir() {
                ver_profile_dir
            } else {
                game_dir.clone()
            };
            build_profile_stats(
                format!("lunar:{}:{}", lunar_dir.display(), ver),
                ExternalLauncherKind::LunarClient,
                format!("Lunar {ver}"),
                ver,
                LoaderKind::Fabric,
                None,
                effective_dir,
                true,
            )
        })
        .collect();

    profiles.sort_by(|a, b| b.mc_version.cmp(&a.mc_version));
    Some(DetectedLauncher {
        kind: ExternalLauncherKind::LunarClient,
        root_dir: lunar_dir.to_path_buf(),
        profiles,
    })
}

fn parse_version_json_meta(
    folder_name: &str,
    val: &serde_json::Value,
) -> (String, LoaderKind, Option<String>) {
    let id_str = val
        .get("id")
        .and_then(|v| v.as_str())
        .unwrap_or(folder_name);
    let inherits = val.get("inheritsFrom").and_then(|v| v.as_str());

    let mc_ver = inherits
        .and_then(extract_mc_version_token)
        .or_else(|| extract_mc_version_token(id_str))
        .or_else(|| extract_mc_version_token(folder_name))
        .unwrap_or_else(|| "1.21.4".to_string());

    let mut loader = LoaderKind::Vanilla;
    let mut loader_version = None;

    let lower_id = format!("{} {}", folder_name, id_str).to_ascii_lowercase();
    if lower_id.contains("fabric") {
        loader = LoaderKind::Fabric;
    } else if lower_id.contains("quilt") {
        loader = LoaderKind::Quilt;
    } else if lower_id.contains("neoforge") {
        loader = LoaderKind::NeoForge;
    } else if lower_id.contains("forge") {
        loader = LoaderKind::Forge;
    }

    if let Some(libs) = val.get("libraries").and_then(|v| v.as_array()) {
        for lib in libs {
            let Some(name) = lib.get("name").and_then(|v| v.as_str()) else {
                continue;
            };
            if let Some(ver) = name.strip_prefix("net.fabricmc:fabric-loader:") {
                loader = LoaderKind::Fabric;
                loader_version = Some(ver.split(':').next().unwrap_or(ver).to_string());
                break;
            }
            if let Some(ver) = name.strip_prefix("org.quiltmc:quilt-loader:") {
                loader = LoaderKind::Quilt;
                loader_version = Some(ver.split(':').next().unwrap_or(ver).to_string());
                break;
            }
            if let Some(ver) = name.strip_prefix("net.neoforged:neoforge:") {
                loader = LoaderKind::NeoForge;
                loader_version = Some(ver.split(':').next().unwrap_or(ver).to_string());
                break;
            }
            if let Some(ver) = name.strip_prefix("net.minecraftforge:forge:") {
                loader = LoaderKind::Forge;
                let clean = ver.split(':').next().unwrap_or(ver);
                let lv = clean.split_once('-').map(|(_, r)| r).unwrap_or(clean);
                loader_version = Some(lv.to_string());
                break;
            }
        }
    }

    (mc_ver, loader, loader_version)
}

fn infer_profile_metadata_safe(game_dir: &Path) -> (String, LoaderKind, Option<String>) {
    let mut mc_ver: Option<String> = None;
    let mut loader = LoaderKind::Vanilla;
    let mut loader_ver: Option<String> = None;

    let remapped = game_dir.join(".fabric").join("remappedJars");
    if remapped.is_dir()
        && let Ok(entries) = fs::read_dir(&remapped)
    {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().to_string();
            if let Some(rest) = fname.strip_prefix("minecraft-") {
                let mut parts = rest.splitn(2, '-');
                if let Some(v) = parts.next().and_then(extract_mc_version_token) {
                    mc_ver = Some(v);
                    loader = LoaderKind::Fabric;
                    if let Some(lv) = parts.next()
                        && !lv.is_empty()
                    {
                        loader_ver = Some(lv.to_string());
                    }
                    break;
                }
            }
        }
    }

    if mc_ver.is_none() {
        let options_txt = game_dir.join("options.txt");
        if let Ok(text) = fs::read_to_string(&options_txt) {
            for line in text.lines() {
                if let Some(val) = line.strip_prefix("version:")
                    && let Ok(dv) = val.trim().parse::<u32>()
                    && let Some(mapped) = data_version_to_mc(dv)
                {
                    mc_ver = Some(mapped.to_string());
                    break;
                }
            }
        }
    }

    if loader == LoaderKind::Vanilla {
        let mods_dir = game_dir.join("mods");
        if mods_dir.is_dir()
            && let Ok(entries) = fs::read_dir(&mods_dir)
        {
            for entry in entries.flatten().take(24) {
                let p = entry.path();
                if p.extension().and_then(|e| e.to_str()) != Some("jar") {
                    continue;
                }
                if let Some(detected) = inspect_mod_jar_loader(&p) {
                    loader = detected;
                    break;
                }
            }
        }
    }

    if mc_ver.is_none()
        && let Some(fname) = game_dir.file_name().and_then(|n| n.to_str())
    {
        mc_ver = extract_mc_version_token(fname);
    }

    (mc_ver.unwrap_or_else(|| "1.21.4".to_string()), loader, loader_ver)
}

fn inspect_mod_jar_loader(jar_path: &Path) -> Option<LoaderKind> {
    let file = fs::File::open(jar_path).ok()?;
    let mut zip = zip::ZipArchive::new(file).ok()?;
    if zip.by_name("quilt.mod.json").is_ok() {
        return Some(LoaderKind::Quilt);
    }
    if zip.by_name("fabric.mod.json").is_ok() {
        return Some(LoaderKind::Fabric);
    }
    if zip.by_name("META-INF/neoforge.mods.toml").is_ok() {
        return Some(LoaderKind::NeoForge);
    }
    if zip.by_name("META-INF/mods.toml").is_ok() {
        return Some(LoaderKind::Forge);
    }
    None
}

fn is_nightclient_mod_jar(jar_path: &Path) -> bool {
    let Ok(file) = fs::File::open(jar_path) else {
        return false;
    };
    let Ok(mut zip) = zip::ZipArchive::new(file) else {
        return false;
    };
    let Ok(mut entry) = zip.by_name("fabric.mod.json") else {
        return false;
    };
    let mut text = String::new();
    if entry.read_to_string(&mut text).is_err() {
        return false;
    }
    let Ok(val) = serde_json::from_str::<serde_json::Value>(&text) else {
        return false;
    };
    val.get("id")
        .and_then(|v| v.as_str())
        .is_some_and(|id| id.eq_ignore_ascii_case("nightclient"))
}

fn is_sensitive_file(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    if SENSITIVE_FILENAMES.contains(&lower.as_str()) {
        return true;
    }
    if lower.contains("account")
        || lower.contains("token")
        || lower.contains("credential")
        || lower.contains("secret")
        || lower.contains("password")
    {
        return true;
    }
    if let Some(ext) = Path::new(&lower).extension().and_then(|e| e.to_str())
        && SENSITIVE_EXTENSIONS.contains(&ext)
    {
        return true;
    }
    false
}

fn is_symlink(path: &Path) -> bool {
    fs::symlink_metadata(path)
        .map(|m| m.file_type().is_symlink())
        .unwrap_or(false)
}

fn copy_mods_dir(src: &Path, dst: &Path, result: &mut MigrationResult) -> std::io::Result<()> {
    if !src.is_dir() || is_symlink(src) {
        return Ok(());
    }
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)?.flatten() {
        let p = entry.path();
        if is_symlink(&p) || !p.is_file() {
            continue;
        }
        let Some(fname) = p.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if fname.starts_with('.') || is_sensitive_file(fname) {
            continue;
        }
        let lower = fname.to_ascii_lowercase();
        if !(lower.ends_with(".jar") || lower.ends_with(".jar.disabled")) {
            continue;
        }
        if lower.ends_with(".jar") && is_nightclient_mod_jar(&p) {
            result.skipped_nightclient_duplicates += 1;
            continue;
        }
        let target = dst.join(fname);
        let bytes = fs::copy(&p, &target)?;
        result.files_copied += 1;
        result.bytes_copied = result.bytes_copied.saturating_add(bytes);
    }
    Ok(())
}

fn copy_safe_dir(
    src: &Path,
    dst: &Path,
    depth: usize,
    result: &mut MigrationResult,
) -> std::io::Result<()> {
    if depth > 16 || !src.is_dir() || is_symlink(src) {
        return Ok(());
    }
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)?.flatten() {
        let p = entry.path();
        if is_symlink(&p) {
            continue;
        }
        let Some(fname) = p.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if fname.starts_with('.') || is_sensitive_file(fname) {
            continue;
        }
        let target = dst.join(fname);
        if p.is_dir() {
            copy_safe_dir(&p, &target, depth + 1, result)?;
        } else if p.is_file() {
            let bytes = fs::copy(&p, &target)?;
            result.files_copied += 1;
            result.bytes_copied = result.bytes_copied.saturating_add(bytes);
        }
    }
    Ok(())
}

fn copy_single_safe_file(
    src: &Path,
    dst: &Path,
    result: &mut MigrationResult,
) -> std::io::Result<()> {
    if !src.is_file() || is_symlink(src) {
        return Ok(());
    }
    if let Some(parent) = dst.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::copy(src, dst)?;
    result.files_copied += 1;
    result.bytes_copied = result.bytes_copied.saturating_add(bytes);
    Ok(())
}

#[allow(clippy::too_many_arguments)]
fn build_profile_stats(
    id: String,
    launcher: ExternalLauncherKind,
    name: String,
    mc_version: String,
    loader: LoaderKind,
    loader_version: Option<String>,
    game_dir: PathBuf,
    proprietary_hud_notice: bool,
) -> DetectedProfile {
    let mod_count = count_entries(&game_dir.join("mods"), |p, name| {
        p.is_file()
            && !name.starts_with('.')
            && (name.ends_with(".jar") || name.ends_with(".jar.disabled"))
    });
    let resourcepack_count = count_entries(&game_dir.join("resourcepacks"), |p, name| {
        !name.starts_with('.') && (p.is_dir() || name.ends_with(".zip"))
    });
    let shader_count = count_entries(&game_dir.join("shaderpacks"), |p, name| {
        !name.starts_with('.') && (p.is_dir() || name.ends_with(".zip"))
    });
    let world_count = count_entries(&game_dir.join("saves"), |p, name| {
        p.is_dir() && !name.starts_with('.')
    });
    let screenshot_count = count_entries(&game_dir.join("screenshots"), |p, name| {
        p.is_file() && !name.starts_with('.') && name.to_ascii_lowercase().ends_with(".png")
    });
    let server_count = read_servers_dat(&game_dir).len();
    let has_options_txt = game_dir.join("options.txt").is_file();
    let has_config_dir = count_entries(&game_dir.join("config"), |_, n| !n.starts_with('.')) > 0;

    DetectedProfile {
        id,
        launcher,
        name,
        mc_version,
        loader,
        loader_version,
        game_dir,
        mod_count,
        resourcepack_count,
        shader_count,
        server_count,
        world_count,
        screenshot_count,
        has_options_txt,
        has_config_dir,
        proprietary_hud_notice,
    }
}

fn count_entries<F>(dir: &Path, pred: F) -> usize
where
    F: Fn(&Path, &str) -> bool,
{
    let Ok(entries) = fs::read_dir(dir) else {
        return 0;
    };
    entries
        .flatten()
        .filter(|e| {
            let p = e.path();
            !is_symlink(&p)
                && p.file_name()
                    .and_then(|n| n.to_str())
                    .is_some_and(|n| pred(&p, n))
        })
        .count()
}

fn parse_loader_str(raw: &str) -> LoaderKind {
    match raw.trim().to_ascii_lowercase().as_str() {
        "fabric" => LoaderKind::Fabric,
        "quilt" => LoaderKind::Quilt,
        "neoforge" => LoaderKind::NeoForge,
        "forge" => LoaderKind::Forge,
        _ => LoaderKind::Vanilla,
    }
}

fn extract_mc_version_token(raw: &str) -> Option<String> {
    let bytes = raw.as_bytes();
    for i in 0..bytes.len() {
        let starts_one = bytes[i..].starts_with(b"1.");
        let starts_twenty_six = bytes[i..].starts_with(b"26.");
        if !(starts_one || starts_twenty_six) {
            continue;
        }
        if i > 0 && bytes[i - 1].is_ascii_digit() {
            continue;
        }
        let mut end = i;
        let mut dots = 0usize;
        while end < bytes.len() {
            let b = bytes[end];
            if b.is_ascii_digit() {
                end += 1;
            } else if b == b'.' && dots < 2 && end + 1 < bytes.len() && bytes[end + 1].is_ascii_digit()
            {
                dots += 1;
                end += 1;
            } else {
                break;
            }
        }
        let candidate = &raw[i..end];
        if candidate.starts_with("1.") && candidate.len() >= 4 {
            let minor = candidate[2..].split('.').next().and_then(|s| s.parse::<u32>().ok());
            if minor.is_some_and(|m| (7..=25).contains(&m)) {
                return Some(candidate.to_string());
            }
        } else if candidate.starts_with("26.") && candidate.len() >= 4 {
            return Some(candidate.to_string());
        }
    }
    None
}

fn data_version_to_mc(dv: u32) -> Option<&'static str> {
    match dv {
        2586 => Some("1.16.5"),
        2975 => Some("1.18.2"),
        3337 => Some("1.19.4"),
        3463 => Some("1.20"),
        3465 => Some("1.20.1"),
        3578 => Some("1.20.2"),
        3698 => Some("1.20.3"),
        3700 => Some("1.20.4"),
        3837 => Some("1.20.5"),
        3839 => Some("1.20.6"),
        3953 => Some("1.21"),
        3955 => Some("1.21.1"),
        4080 => Some("1.21.2"),
        4082 => Some("1.21.3"),
        4189 => Some("1.21.4"),
        4325 => Some("1.21.5"),
        4435 => Some("1.21.6"),
        4438 => Some("1.21.7"),
        4440 => Some("1.21.8"),
        4554 => Some("1.21.9"),
        4556 => Some("1.21.10"),
        4671 => Some("1.21.11"),
        _ => None,
    }
}

fn canonical_key(path: &Path) -> PathBuf {
    fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf())
}

fn dedup_paths(list: &mut Vec<PathBuf>) {
    let mut seen = BTreeSet::new();
    list.retain(|p| seen.insert(p.clone()));
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn make_mod_jar(path: &Path, mod_id: &str) {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).unwrap();
        }
        let f = fs::File::create(path).unwrap();
        let mut zw = zip::ZipWriter::new(f);
        zw.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zw.write_all(format!(r#"{{"schemaVersion":1,"id":"{mod_id}"}}"#).as_bytes())
            .unwrap();
        zw.finish().unwrap();
    }

    #[test]
    fn discovers_all_five_launchers_and_migrates_safely_without_touching_auth_files() {
        let sandbox = tempfile::tempdir().unwrap();
        let root = sandbox.path();

        let prism_inst = root.join("PrismLauncher/instances/HolyWorld");
        fs::create_dir_all(prism_inst.join(".minecraft/mods")).unwrap();
        fs::create_dir_all(prism_inst.join(".minecraft/resourcepacks")).unwrap();
        fs::create_dir_all(prism_inst.join(".minecraft/config")).unwrap();
        fs::write(prism_inst.join("instance.cfg"), "name=HolyWorld PvP\n").unwrap();
        fs::write(
            prism_inst.join("mmc-pack.json"),
            r#"{"components":[
                {"uid":"net.minecraft","version":"1.21.4"},
                {"uid":"net.fabricmc.fabric-loader","version":"0.16.10"}
            ]}"#,
        )
        .unwrap();
        make_mod_jar(&prism_inst.join(".minecraft/mods/sodium.jar"), "sodium");
        make_mod_jar(&prism_inst.join(".minecraft/mods/nightclient-old.jar"), "nightclient");
        fs::write(prism_inst.join(".minecraft/resourcepacks/pvp.zip"), b"zip").unwrap();
        fs::write(prism_inst.join(".minecraft/options.txt"), "fov:0.25\n").unwrap();
        fs::write(prism_inst.join(".minecraft/config/accounts.json"), r#"{"token":"SECRET"}"#).unwrap();
        fs::write(prism_inst.join(".minecraft/config/sodium.json"), r#"{"ok":true}"#).unwrap();
        fs::write(root.join("PrismLauncher/accounts.json"), r#"{"token":"SECRET"}"#).unwrap();

        let mr_prof = root.join("ModrinthApp/profiles/My Modrinth Pack");
        fs::create_dir_all(mr_prof.join(".fabric/remappedJars/minecraft-1.21.1-0.16.9")).unwrap();
        fs::create_dir_all(mr_prof.join("mods")).unwrap();
        make_mod_jar(&mr_prof.join("mods/lithium.jar"), "lithium");
        fs::write(root.join("ModrinthApp/app.db"), b"SQLITE_AUTH_DECOY").unwrap();

        let tl_mc = root.join(".minecraft");
        let tl_ver = tl_mc.join("versions/Fabric 1.21.4");
        fs::create_dir_all(tl_ver.join("mods")).unwrap();
        fs::write(tl_mc.join("TLauncherAdditional.json"), r#"{}"#).unwrap();
        fs::write(
            tl_ver.join("Fabric 1.21.4.json"),
            r#"{"id":"Fabric 1.21.4","inheritsFrom":"1.21.4","libraries":[{"name":"net.fabricmc:fabric-loader:0.16.10"}]}"#,
        )
        .unwrap();
        make_mod_jar(&tl_ver.join("mods/iris.jar"), "iris");

        let legacy_ver = root.join(".tlauncher/legacy/Minecraft/game/versions/1.20.1-forge-47.3.0");
        fs::create_dir_all(legacy_ver.join("mods")).unwrap();
        fs::write(
            legacy_ver.join("1.20.1-forge-47.3.0.json"),
            r#"{"id":"1.20.1-forge-47.3.0","inheritsFrom":"1.20.1","libraries":[{"name":"net.minecraftforge:forge:1.20.1-47.3.0"}]}"#,
        )
        .unwrap();

        let lunar = root.join(".lunarclient");
        fs::create_dir_all(lunar.join("offline/multiver/lunar-prod-optifine-1.21.4")).unwrap();
        fs::create_dir_all(lunar.join("settings/game")).unwrap();
        fs::write(lunar.join("settings/game/accounts.json"), r#"{"secret":1}"#).unwrap();

        let search = MigrationSearchRoots::from_base_dirs(Some(root), Some(root), Some(root));
        let detected = discover_external_launchers_in(&search);
        let kinds: BTreeSet<ExternalLauncherKind> = detected.iter().map(|d| d.kind).collect();
        assert!(kinds.contains(&ExternalLauncherKind::PrismLauncher));
        assert!(kinds.contains(&ExternalLauncherKind::ModrinthApp));
        assert!(kinds.contains(&ExternalLauncherKind::TLauncher));
        assert!(kinds.contains(&ExternalLauncherKind::LegacyLauncher));
        assert!(kinds.contains(&ExternalLauncherKind::LunarClient));

        let mr = detected
            .iter()
            .find(|d| d.kind == ExternalLauncherKind::ModrinthApp)
            .unwrap();
        assert_eq!(mr.profiles[0].mc_version, "1.21.1");
        assert_eq!(mr.profiles[0].loader, LoaderKind::Fabric);
        assert_eq!(mr.profiles[0].loader_version.as_deref(), Some("0.16.9"));

        let lunar_det = detected
            .iter()
            .find(|d| d.kind == ExternalLauncherKind::LunarClient)
            .unwrap();
        assert!(lunar_det.profiles[0].proprietary_hud_notice);
        assert_eq!(lunar_det.profiles[0].mc_version, "1.21.4");

        let prism = detected
            .iter()
            .find(|d| d.kind == ExternalLauncherKind::PrismLauncher)
            .unwrap();
        let target_home = tempfile::tempdir().unwrap();
        let paths = LauncherPaths::new(target_home.path().to_path_buf());
        paths.ensure_all().unwrap();

        let (rec, res) =
            execute_migration(&paths, &prism.profiles[0], &MigrationSelection::default()).unwrap();
        assert_eq!(rec.config.mc_version, "1.21.4");
        assert_eq!(rec.config.loader, LoaderKind::Fabric);
        assert_eq!(res.skipped_nightclient_duplicates, 1);
        assert!(rec.dir.join("mods/sodium.jar").is_file());
        assert!(!rec.dir.join("mods/nightclient-old.jar").exists());
        assert!(rec.dir.join("resourcepacks/pvp.zip").is_file());
        assert!(rec.dir.join("config/sodium.json").is_file());
        assert!(!rec.dir.join("config/accounts.json").exists());
        assert!(rec.dir.join("options.txt").is_file());
    }
}
