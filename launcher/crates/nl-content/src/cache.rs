use rusqlite::{Connection, params};
use sha2::{Digest, Sha256};
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

#[derive(Debug, Clone)]
pub struct InstalledMod {
    pub mod_id: String,
    pub name: String,
    pub version: String,
    pub description: String,
    pub authors: String,
    pub filename: String,
    pub path: PathBuf,
    pub enabled: bool,
    pub file_size: u64,
    pub icon_path: Option<PathBuf>,
}

pub struct ModMetaCache {
    db_path: PathBuf,
    icons_dir: PathBuf,
}

impl ModMetaCache {
    pub fn new(db_path: PathBuf, icons_dir: PathBuf) -> Self {
        Self { db_path, icons_dir }
    }

    fn open(&self) -> rusqlite::Result<Connection> {
        if let Some(parent) = self.db_path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let conn = Connection::open(&self.db_path)?;
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA busy_timeout = 2000;
             CREATE TABLE IF NOT EXISTS mod_meta (
                path TEXT PRIMARY KEY,
                file_size INTEGER NOT NULL,
                mtime_secs INTEGER NOT NULL,
                mod_id TEXT NOT NULL,
                name TEXT NOT NULL,
                version TEXT NOT NULL,
                description TEXT NOT NULL,
                authors TEXT NOT NULL,
                icon_path TEXT
            );",
        )?;
        Ok(conn)
    }

    pub fn scan_mods_dir(&self, mods_dir: &Path) -> Vec<InstalledMod> {
        let Ok(entries) = fs::read_dir(mods_dir) else {
            return Vec::new();
        };
        let mut conn = self.open().ok();
        let mut mods = Vec::new();
        let mut to_insert: Vec<(String, i64, i64, InstalledMod)> = Vec::new();

        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_file() {
                continue;
            }
            let Some(filename) = path.file_name().and_then(|n| n.to_str()) else {
                continue;
            };
            if filename.starts_with('.') {
                continue;
            }
            let lower = filename.to_ascii_lowercase();
            let enabled = if lower.ends_with(".jar") {
                true
            } else if lower.ends_with(".jar.disabled") {
                false
            } else {
                continue;
            };

            let Ok(meta) = fs::metadata(&path) else {
                continue;
            };
            let file_size = meta.len();
            let mtime_secs = meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_secs())
                .unwrap_or(0);
            let key = path.to_string_lossy().to_string();

            if let Some(ref c) = conn
                && let Ok(cached) = c.query_row(
                    "SELECT mod_id, name, version, description, authors, icon_path
                     FROM mod_meta WHERE path = ?1 AND file_size = ?2 AND mtime_secs = ?3",
                    params![&key, file_size as i64, mtime_secs as i64],
                    |row| {
                        Ok((
                            row.get::<_, String>(0)?,
                            row.get::<_, String>(1)?,
                            row.get::<_, String>(2)?,
                            row.get::<_, String>(3)?,
                            row.get::<_, String>(4)?,
                            row.get::<_, Option<String>>(5)?,
                        ))
                    },
                )
            {
                let icon_path = cached
                    .5
                    .map(PathBuf::from)
                    .filter(|p| p.is_file());
                mods.push(InstalledMod {
                    mod_id: cached.0,
                    name: cached.1,
                    version: cached.2,
                    description: cached.3,
                    authors: cached.4,
                    filename: filename.to_string(),
                    path,
                    enabled,
                    file_size,
                    icon_path,
                });
                continue;
            }

            let parsed = parse_mod_jar(&path, &self.icons_dir, filename, enabled, file_size);
            to_insert.push((key, file_size as i64, mtime_secs as i64, parsed.clone()));
            mods.push(parsed);
        }

        if !to_insert.is_empty()
            && let Some(ref mut c) = conn
            && let Ok(tx) = c.transaction()
        {
            if let Ok(mut stmt) = tx.prepare(
                "INSERT OR REPLACE INTO mod_meta
                 (path, file_size, mtime_secs, mod_id, name, version, description, authors, icon_path)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            ) {
                for (key, file_size, mtime_secs, parsed) in &to_insert {
                    let icon_str = parsed
                        .icon_path
                        .as_ref()
                        .map(|p| p.to_string_lossy().to_string());
                    let _ = stmt.execute(params![
                        key,
                        file_size,
                        mtime_secs,
                        &parsed.mod_id,
                        &parsed.name,
                        &parsed.version,
                        &parsed.description,
                        &parsed.authors,
                        icon_str,
                    ]);
                }
            }
            let _ = tx.commit();
        }

        mods.sort_by_key(|a| a.name.to_lowercase());
        mods
    }
}

fn parse_mod_jar(
    jar_path: &Path,
    icons_dir: &Path,
    filename: &str,
    enabled: bool,
    file_size: u64,
) -> InstalledMod {
    let fallback_name = filename
        .trim_end_matches(".disabled")
        .trim_end_matches(".jar")
        .to_string();

    let mut info = InstalledMod {
        mod_id: fallback_name.to_lowercase(),
        name: fallback_name,
        version: String::new(),
        description: String::new(),
        authors: String::new(),
        filename: filename.to_string(),
        path: jar_path.to_path_buf(),
        enabled,
        file_size,
        icon_path: None,
    };

    let Ok(file) = fs::File::open(jar_path) else {
        return info;
    };
    let Ok(mut zip) = zip::ZipArchive::new(file) else {
        return info;
    };

    let mut icon_entry: Option<String> = None;

    if let Ok(mut entry) = zip.by_name("fabric.mod.json") {
        let mut text = String::new();
        if entry.read_to_string(&mut text).is_ok()
            && let Ok(json) = serde_json::from_str::<serde_json::Value>(&text)
        {
            if let Some(id) = json["id"].as_str() {
                info.mod_id = id.to_string();
            }
            if let Some(name) = json["name"].as_str() {
                info.name = name.to_string();
            }
            if let Some(ver) = json["version"].as_str() {
                info.version = ver.to_string();
            }
            if let Some(desc) = json["description"].as_str() {
                info.description = desc.lines().next().unwrap_or("").trim().to_string();
            }
            if let Some(authors) = json["authors"].as_array() {
                let list: Vec<String> = authors
                    .iter()
                    .filter_map(|a| {
                        a.as_str()
                            .map(str::to_string)
                            .or_else(|| a["name"].as_str().map(str::to_string))
                    })
                    .collect();
                info.authors = list.join(", ");
            }
            icon_entry = json["icon"].as_str().map(str::to_string);
        }
    }
    else if let Ok(mut entry) = zip.by_name("quilt.mod.json") {
        let mut text = String::new();
        if entry.read_to_string(&mut text).is_ok()
            && let Ok(json) = serde_json::from_str::<serde_json::Value>(&text)
        {
            let ql = &json["quilt_loader"];
            if let Some(id) = ql["id"].as_str() {
                info.mod_id = id.to_string();
            }
            if let Some(ver) = ql["version"].as_str() {
                info.version = ver.to_string();
            }
            let meta = &ql["metadata"];
            if let Some(name) = meta["name"].as_str() {
                info.name = name.to_string();
            }
            if let Some(desc) = meta["description"].as_str() {
                info.description = desc.lines().next().unwrap_or("").trim().to_string();
            }
            icon_entry = meta["icon"].as_str().map(str::to_string);
        }
    }
    else {
        for toml_path in ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"] {
            if let Ok(mut entry) = zip.by_name(toml_path) {
                let mut text = String::new();
                if entry.read_to_string(&mut text).is_ok() {
                    parse_forge_mods_toml(&text, &mut info, &mut icon_entry);
                }
                break;
            }
        }
    }

    if let Some(icon_rel) = icon_entry {
        let clean = icon_rel.trim_start_matches('/');
        if let Ok(mut entry) = zip.by_name(clean) {
            let mut bytes = Vec::new();
            if entry.read_to_end(&mut bytes).is_ok() && !bytes.is_empty() {
                let hash = hex::encode(&Sha256::digest(&bytes)[..10]);
                let out = icons_dir.join(format!("{hash}.png"));
                if !out.is_file() {
                    let _ = fs::create_dir_all(icons_dir);
                    if let Ok(img) = image::load_from_memory(&bytes) {
                        let resized = img.thumbnail(64, 64);
                        let _ = resized.save(&out);
                    }
                }
                if out.is_file() {
                    info.icon_path = Some(out);
                }
            }
        }
    }

    info
}

fn parse_forge_mods_toml(text: &str, info: &mut InstalledMod, icon_entry: &mut Option<String>) {
    let mut in_mods_block = false;
    for raw in text.lines() {
        let line = raw.split('#').next().unwrap_or("").trim();
        if line == "[[mods]]" {
            if in_mods_block {
                break;
            }
            in_mods_block = true;
            continue;
        }
        if !in_mods_block {
            continue;
        }
        if let Some((k, v)) = line.split_once('=') {
            let key = k.trim();
            let val = v.trim().trim_matches('"').trim_matches('\'');
            match key {
                "modId" if !val.is_empty() => info.mod_id = val.to_string(),
                "displayName" if !val.is_empty() => info.name = val.to_string(),
                "version" if !val.is_empty() && !val.contains("${") => {
                    info.version = val.to_string();
                }
                "authors" if !val.is_empty() => info.authors = val.to_string(),
                "logoFile" if !val.is_empty() => *icon_entry = Some(val.to_string()),
                _ => {}
            }
        }
    }
}

pub fn toggle_mod_enabled(path: &Path) -> std::io::Result<PathBuf> {
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::InvalidInput, "invalid filename"))?;
    let new_name = if let Some(stripped) = name.strip_suffix(".disabled") {
        stripped.to_string()
    } else {
        format!("{name}.disabled")
    };
    let dest = path.with_file_name(new_name);
    fs::rename(path, &dest)?;
    Ok(dest)
}

pub fn delete_mod_file(path: &Path) -> std::io::Result<()> {
    if path.exists() {
        fs::remove_file(path)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn caches_and_reloads_fabric_mod_metadata() {
        let dir = tempfile::tempdir().unwrap();
        let mods_dir = dir.path().join("mods");
        fs::create_dir_all(&mods_dir).unwrap();
        let jar = mods_dir.join("sodium.jar");
        let f = fs::File::create(&jar).unwrap();
        let mut zw = zip::ZipWriter::new(f);
        zw.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zw.write_all(br#"{"id":"sodium","name":"Sodium","version":"0.6.0","authors":["jellysquid3"]}"#)
            .unwrap();
        zw.finish().unwrap();

        let cache = ModMetaCache::new(dir.path().join("meta.sqlite"), dir.path().join("icons"));
        let first = cache.scan_mods_dir(&mods_dir);
        assert_eq!(first.len(), 1);
        assert_eq!(first[0].mod_id, "sodium");
        assert_eq!(first[0].name, "Sodium");

        let second = cache.scan_mods_dir(&mods_dir);
        assert_eq!(second.len(), 1);
        assert_eq!(second[0].version, "0.6.0");
    }
}
