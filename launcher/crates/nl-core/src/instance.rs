use crate::crash::CrashDiagnosis;
use crate::launch::LaunchStage;
use chrono::{DateTime, Utc};
use nl_java::PerformanceProfile;
use nl_platform::{LauncherPaths, read_json_with_backup, sanitize_filename, write_json_atomic};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use uuid::Uuid;

pub const INSTANCE_FILE_NAME: &str = "instance.json";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Default, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LoaderKind {
    #[default]
    Fabric,
    Vanilla,
    Quilt,
    NeoForge,
    Forge,
}

impl LoaderKind {
    pub const ALL: [LoaderKind; 5] = [
        LoaderKind::Fabric,
        LoaderKind::Vanilla,
        LoaderKind::Quilt,
        LoaderKind::NeoForge,
        LoaderKind::Forge,
    ];

    pub fn id(self) -> &'static str {
        match self {
            LoaderKind::Vanilla => "vanilla",
            LoaderKind::Fabric => "fabric",
            LoaderKind::Quilt => "quilt",
            LoaderKind::NeoForge => "neoforge",
            LoaderKind::Forge => "forge",
        }
    }

    pub fn display_name(self) -> &'static str {
        match self {
            LoaderKind::Vanilla => "Vanilla",
            LoaderKind::Fabric => "Fabric",
            LoaderKind::Quilt => "Quilt",
            LoaderKind::NeoForge => "NeoForge",
            LoaderKind::Forge => "Forge",
        }
    }

    pub fn from_id(id: &str) -> Self {
        match id.to_ascii_lowercase().as_str() {
            "fabric" => LoaderKind::Fabric,
            "quilt" => LoaderKind::Quilt,
            "neoforge" => LoaderKind::NeoForge,
            "forge" => LoaderKind::Forge,
            _ => LoaderKind::Vanilla,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum InstanceSort {
    #[default]
    LastPlayed,
    Name,
    Playtime,
}

impl InstanceSort {
    pub const ALL: [InstanceSort; 3] = [
        InstanceSort::LastPlayed,
        InstanceSort::Name,
        InstanceSort::Playtime,
    ];
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct InstanceConfig {
    pub schema_version: u32,
    pub id: String,
    pub name: String,
    pub mc_version: String,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
    pub group: Option<String>,
    pub created_at: DateTime<Utc>,
    pub last_played_at: Option<DateTime<Utc>>,
    pub playtime_secs: u64,
    pub memory_mb_override: Option<u32>,
    pub java_path_override: Option<String>,
    pub performance_profile_override: Option<PerformanceProfile>,
    pub extra_jvm_args: String,
    pub extra_game_args: String,
    pub nightclient_enabled: bool,
    pub nightclient_shared_profile: bool,
    pub nightclient_pinned_version: Option<String>,
    pub turbo_mode: bool,
}

impl Default for InstanceConfig {
    fn default() -> Self {
        Self {
            schema_version: 1,
            id: Uuid::new_v4().simple().to_string(),
            name: "New Instance".to_string(),
            mc_version: "1.21.4".to_string(),
            loader: LoaderKind::Fabric,
            loader_version: None,
            group: None,
            created_at: Utc::now(),
            last_played_at: None,
            playtime_secs: 0,
            memory_mb_override: None,
            java_path_override: None,
            performance_profile_override: None,
            extra_jvm_args: String::new(),
            extra_game_args: String::new(),
            nightclient_enabled: true,
            nightclient_shared_profile: true,
            nightclient_pinned_version: None,
            turbo_mode: false,
        }
    }
}

#[derive(Debug, Clone)]
pub struct InstanceRecord {
    pub config: InstanceConfig,
    pub dir: PathBuf,
    pub mod_count: usize,
    pub resource_pack_count: usize,
    pub shader_count: usize,
    pub screenshot_count: usize,
}

#[derive(Debug, Clone, Default)]
pub enum InstanceStatus {
    #[default]
    Idle,
    Preparing {
        stage: LaunchStage,
        progress: f32,
    },
    Running {
        pid: u32,
        started_at: DateTime<Utc>,
    },
    Stopping,
    Crashed {
        exit_code: Option<i32>,
        diagnosis: CrashDiagnosis,
    },
}

impl InstanceStatus {
    pub fn is_busy(&self) -> bool {
        matches!(
            self,
            InstanceStatus::Preparing { .. }
                | InstanceStatus::Running { .. }
                | InstanceStatus::Stopping
        )
    }
}

#[derive(Debug, Clone)]
pub struct CreateInstanceParams {
    pub name: String,
    pub mc_version: String,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
    pub group: Option<String>,
    pub nightclient_enabled: bool,
}

pub fn scan_instances(paths: &LauncherPaths) -> Vec<InstanceRecord> {
    let dir = paths.instances_dir();
    let Ok(entries) = fs::read_dir(&dir) else {
        return Vec::new();
    };
    let mut list = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        let cfg_path = path.join(INSTANCE_FILE_NAME);
        if let Some(config) = read_json_with_backup::<InstanceConfig>(&cfg_path) {
            list.push(build_record(config, path));
        }
    }
    list.sort_by(|a, b| {
        b.config
            .last_played_at
            .cmp(&a.config.last_played_at)
            .then_with(|| b.config.created_at.cmp(&a.config.created_at))
    });
    list
}

pub fn create_instance(
    paths: &LauncherPaths,
    params: CreateInstanceParams,
) -> std::io::Result<InstanceRecord> {
    let base_name = sanitize_filename(if params.name.trim().is_empty() {
        &params.mc_version
    } else {
        params.name.trim()
    });
    let instances_dir = paths.instances_dir();
    fs::create_dir_all(&instances_dir)?;

    let mut folder = instances_dir.join(&base_name);
    let mut suffix = 2u32;
    while folder.exists() {
        folder = instances_dir.join(format!("{base_name}-{suffix}"));
        suffix += 1;
    }

    for sub in ["mods", "resourcepacks", "shaderpacks", "screenshots", "config"] {
        fs::create_dir_all(folder.join(sub))?;
    }

    let config = InstanceConfig {
        schema_version: 1,
        id: Uuid::new_v4().simple().to_string(),
        name: if params.name.trim().is_empty() {
            format!("{} {}", params.mc_version, params.loader.display_name())
        } else {
            params.name.trim().to_string()
        },
        mc_version: params.mc_version,
        loader: params.loader,
        loader_version: params.loader_version,
        group: params.group.filter(|g| !g.trim().is_empty()),
        created_at: Utc::now(),
        last_played_at: None,
        playtime_secs: 0,
        memory_mb_override: None,
        java_path_override: None,
        performance_profile_override: None,
        extra_jvm_args: String::new(),
        extra_game_args: String::new(),
        nightclient_enabled: params.nightclient_enabled,
        nightclient_shared_profile: true,
        nightclient_pinned_version: None,
        turbo_mode: false,
    };

    save_instance_config(&folder, &config)?;
    Ok(build_record(config, folder))
}

pub fn save_instance_config(instance_dir: &Path, config: &InstanceConfig) -> std::io::Result<()> {
    write_json_atomic(&instance_dir.join(INSTANCE_FILE_NAME), config)
}

pub fn delete_instance(instance_dir: &Path) -> std::io::Result<()> {
    if instance_dir.is_dir() {
        fs::remove_dir_all(instance_dir)?;
    }
    Ok(())
}

pub fn rename_instance(
    record: &mut InstanceRecord,
    new_name: &str,
) -> std::io::Result<()> {
    let trimmed = new_name.trim();
    if trimmed.is_empty() {
        return Ok(());
    }
    record.config.name = trimmed.to_string();
    save_instance_config(&record.dir, &record.config)
}

pub fn switch_instance_loader(
    record: &mut InstanceRecord,
    new_loader: LoaderKind,
    new_loader_version: Option<String>,
) -> std::io::Result<()> {
    record.config.loader = new_loader;
    record.config.loader_version = new_loader_version;
    save_instance_config(&record.dir, &record.config)
}

pub fn clone_instance(
    paths: &LauncherPaths,
    source: &InstanceRecord,
    new_name: String,
    target_mc: Option<String>,
    target_loader: Option<LoaderKind>,
    copy_mods_verbatim: bool,
) -> std::io::Result<InstanceRecord> {
    let mc = target_mc.unwrap_or_else(|| source.config.mc_version.clone());
    let loader = target_loader.unwrap_or(source.config.loader);
    let same_target = mc == source.config.mc_version && loader == source.config.loader;

    let created = create_instance(
        paths,
        CreateInstanceParams {
            name: new_name,
            mc_version: mc,
            loader,
            loader_version: if same_target {
                source.config.loader_version.clone()
            } else {
                None
            },
            group: source.config.group.clone(),
            nightclient_enabled: source.config.nightclient_enabled && loader == LoaderKind::Fabric,
        },
    )?;

    for file in ["options.txt", "servers.dat"] {
        let src = source.dir.join(file);
        if src.is_file() {
            let _ = fs::copy(&src, created.dir.join(file));
        }
    }

    let mut dirs = vec!["saves", "config", "resourcepacks", "shaderpacks"];
    if copy_mods_verbatim {
        dirs.push("mods");
    }
    for sub in dirs {
        let src = source.dir.join(sub);
        if src.is_dir() {
            let _ = copy_dir_recursive(&src, &created.dir.join(sub));
        }
    }

    Ok(build_record(created.config, created.dir))
}

fn copy_dir_recursive(src: &Path, dst: &Path) -> std::io::Result<()> {
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)?.flatten() {
        let p = entry.path();
        let target = dst.join(entry.file_name());
        if p.is_dir() {
            copy_dir_recursive(&p, &target)?;
        } else if p.is_file() {
            fs::copy(&p, &target)?;
        }
    }
    Ok(())
}

fn build_record(config: InstanceConfig, dir: PathBuf) -> InstanceRecord {
    let mod_count = count_matching(&dir.join("mods"), |n| {
        let l = n.to_ascii_lowercase();
        !l.starts_with('.') && (l.ends_with(".jar") || l.ends_with(".jar.disabled"))
    });
    let resource_pack_count = count_matching(&dir.join("resourcepacks"), |n| !n.starts_with('.'));
    let shader_count = count_matching(&dir.join("shaderpacks"), |n| !n.starts_with('.'));
    let screenshot_count = count_matching(&dir.join("screenshots"), |n| {
        let l = n.to_ascii_lowercase();
        l.ends_with(".png") || l.ends_with(".jpg") || l.ends_with(".jpeg") || l.ends_with(".webp")
    });
    InstanceRecord {
        config,
        dir,
        mod_count,
        resource_pack_count,
        shader_count,
        screenshot_count,
    }
}

fn count_matching(dir: &Path, pred: impl Fn(&str) -> bool) -> usize {
    let Ok(entries) = fs::read_dir(dir) else {
        return 0;
    };
    entries
        .flatten()
        .filter(|e| {
            e.file_name()
                .to_str()
                .map(&pred)
                .unwrap_or(false)
        })
        .count()
}
