use directories::ProjectDirs;
use serde::de::DeserializeOwned;
use serde::Serialize;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

static ATOMIC_TMP_SEQ: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone)]
pub struct LauncherPaths {
    pub root: PathBuf,
}

impl LauncherPaths {
    pub fn default_root() -> PathBuf {
        if let Ok(override_dir) = std::env::var("NIGHTLAUNCHER_HOME") {
            let trimmed = override_dir.trim();
            if !trimmed.is_empty() {
                return PathBuf::from(trimmed);
            }
        }
        if let Some(proj) = ProjectDirs::from("dev", "nightclient", "NightClientLauncher") {
            return proj.data_local_dir().to_path_buf();
        }
        dirs_fallback()
    }

    pub fn new(root: PathBuf) -> Self {
        Self { root }
    }

    pub fn config_file(&self) -> PathBuf {
        self.root.join("launcher.json")
    }

    pub fn accounts_file(&self) -> PathBuf {
        self.root.join("accounts.json")
    }

    pub fn vault_file(&self) -> PathBuf {
        self.root.join("vault.enc")
    }

    pub fn lock_file(&self) -> PathBuf {
        self.root.join(".launcher.lock")
    }

    pub fn instances_dir(&self) -> PathBuf {
        self.root.join("instances")
    }

    pub fn libraries_dir(&self) -> PathBuf {
        self.root.join("libraries")
    }

    pub fn assets_dir(&self) -> PathBuf {
        self.root.join("assets")
    }

    pub fn versions_dir(&self) -> PathBuf {
        self.root.join("versions")
    }

    pub fn runtimes_dir(&self) -> PathBuf {
        self.root.join("runtimes")
    }

    pub fn cache_dir(&self) -> PathBuf {
        self.root.join("cache")
    }

    pub fn http_cache_dir(&self) -> PathBuf {
        self.cache_dir().join("http")
    }

    pub fn mod_cache_db(&self) -> PathBuf {
        self.cache_dir().join("mod-metadata.sqlite")
    }

    pub fn icons_cache_dir(&self) -> PathBuf {
        self.cache_dir().join("icons")
    }

    pub fn skins_cache_dir(&self) -> PathBuf {
        self.cache_dir().join("skins")
    }

    pub fn thumbnails_cache_dir(&self) -> PathBuf {
        self.cache_dir().join("thumbnails")
    }

    pub fn java_probes_file(&self) -> PathBuf {
        self.cache_dir().join("java-probes.json")
    }

    pub fn nightclient_dir(&self) -> PathBuf {
        self.root.join("nightclient")
    }

    pub fn nightclient_cache_dir(&self) -> PathBuf {
        self.nightclient_dir().join("jars")
    }

    pub fn nightclient_manifest_cache(&self) -> PathBuf {
        self.nightclient_dir().join("manifest.json")
    }

    pub fn nightclient_shared_config_dir(&self) -> PathBuf {
        self.nightclient_dir().join("shared-config")
    }

    pub fn nightclient_themes_dir(&self) -> PathBuf {
        self.nightclient_dir().join("themes")
    }

    pub fn logs_dir(&self) -> PathBuf {
        self.root.join("logs")
    }

    pub fn locales_dir(&self) -> PathBuf {
        self.root.join("locales")
    }

    pub fn ensure_all(&self) -> std::io::Result<()> {
        for dir in [
            &self.root,
            &self.instances_dir(),
            &self.libraries_dir(),
            &self.assets_dir(),
            &self.versions_dir(),
            &self.runtimes_dir(),
            &self.cache_dir(),
            &self.http_cache_dir(),
            &self.icons_cache_dir(),
            &self.skins_cache_dir(),
            &self.thumbnails_cache_dir(),
            &self.nightclient_dir(),
            &self.nightclient_cache_dir(),
            &self.nightclient_shared_config_dir(),
            &self.nightclient_themes_dir(),
            &self.locales_dir(),
            &self.logs_dir(),
        ] {
            fs::create_dir_all(dir)?;
        }
        Ok(())
    }
}

fn dirs_fallback() -> PathBuf {
    #[cfg(target_os = "windows")]
    if let Ok(app) = std::env::var("APPDATA") {
        return PathBuf::from(app).join("NightClientLauncher");
    }
    if let Ok(home) = std::env::var("HOME") {
        return PathBuf::from(home).join(".nightclient-launcher");
    }
    PathBuf::from(".nightclient-launcher")
}

pub fn sanitize_filename(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    for ch in raw.trim().chars() {
        let forbidden = matches!(
            ch,
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' | '\0'
        ) || ch.is_control();
        if forbidden {
            out.push('_');
        } else {
            out.push(ch);
        }
    }
    let trimmed = out.trim().trim_matches('.').to_string();
    let lower = trimmed.to_ascii_lowercase();
    let reserved = matches!(
        lower.as_str(),
        "" | "con"
            | "prn"
            | "aux"
            | "nul"
            | "com1"
            | "com2"
            | "com3"
            | "com4"
            | "com5"
            | "com6"
            | "com7"
            | "com8"
            | "com9"
            | "lpt1"
            | "lpt2"
            | "lpt3"
            | "lpt4"
            | "lpt5"
            | "lpt6"
            | "lpt7"
            | "lpt8"
            | "lpt9"
    );
    if reserved {
        if trimmed.is_empty() {
            "instance".to_string()
        } else {
            format!("_{trimmed}")
        }
    } else {
        trimmed
    }
}

pub fn atomic_write(path: &Path, data: &[u8]) -> std::io::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let seq = ATOMIC_TMP_SEQ.fetch_add(1, Ordering::Relaxed);
    let tmp_name = format!(
        ".{}.{}.{}.tmp",
        path.file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("file"),
        std::process::id(),
        seq
    );
    let tmp_path = path.with_file_name(tmp_name);
    {
        let mut f = fs::File::create(&tmp_path)?;
        f.write_all(data)?;
        f.sync_all()?;
    }

    if path.exists() {
        let bak_path = backup_path(path);
        let _ = fs::copy(path, &bak_path);
    }

    #[cfg(target_os = "windows")]
    {
        if path.exists() {
            let _ = fs::remove_file(path);
        }
    }
    fs::rename(&tmp_path, path)
}

pub fn write_json_atomic<T: Serialize>(path: &Path, value: &T) -> std::io::Result<()> {
    let bytes = serde_json::to_vec_pretty(value)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::InvalidData, e))?;
    atomic_write(path, &bytes)
}

pub fn read_json_with_backup<T: DeserializeOwned>(path: &Path) -> Option<T> {
    if let Ok(raw) = fs::read(path)
        && let Ok(parsed) = serde_json::from_slice::<T>(&raw)
    {
        return Some(parsed);
    }
    let bak = backup_path(path);
    if let Ok(raw) = fs::read(&bak)
        && let Ok(parsed) = serde_json::from_slice::<T>(&raw)
    {
        tracing::warn!("Recovered corrupted JSON from backup: {}", bak.display());
        let _ = atomic_write(path, &raw);
        return Some(parsed);
    }
    None
}

fn backup_path(path: &Path) -> PathBuf {
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("file");
    path.with_file_name(format!("{name}.bak"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitizes_windows_reserved_and_unicode_paths() {
        assert_eq!(sanitize_filename("CON"), "_CON");
        assert_eq!(sanitize_filename("1.21.4 / PvP: тест"), "1.21.4 _ PvP_ тест");
        assert_eq!(sanitize_filename("..."), "instance");
    }

    #[test]
    fn atomic_write_recovers_from_backup() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("cfg.json");
        write_json_atomic(&path, &serde_json::json!({ "ok": 42 })).unwrap();
        write_json_atomic(&path, &serde_json::json!({ "ok": 99 })).unwrap();
        fs::write(&path, b"{not valid json").unwrap();
        let recovered: serde_json::Value = read_json_with_backup(&path).unwrap();
        assert_eq!(recovered["ok"], 42);
    }
}
