use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

const MANAGED_FALLBACK_NAME: &str = ".nightclient-managed.jar";

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InjectionPlan {
    FabricAddModsFlag(String),
    ManagedModCopy(PathBuf),
}

#[cfg(any(target_os = "windows", test))]
fn strip_verbatim_prefix(raw: &str) -> String {
    if let Some(unc) = raw.strip_prefix(r"\\?\UNC\") {
        format!(r"\\{unc}")
    } else if let Some(rest) = raw.strip_prefix(r"\\?\") {
        rest.to_string()
    } else {
        raw.to_string()
    }
}

pub fn plan_injection(jar_path: &Path, instance_dir: &Path) -> std::io::Result<InjectionPlan> {
    let abs = fs::canonicalize(jar_path).unwrap_or_else(|_| jar_path.to_path_buf());
    #[cfg(target_os = "windows")]
    let path_str = strip_verbatim_prefix(&abs.to_string_lossy());
    #[cfg(not(target_os = "windows"))]
    let path_str = abs.to_string_lossy().to_string();

    #[cfg(target_os = "windows")]
    let conflicts_with_separator = path_str.contains(';');
    #[cfg(not(target_os = "windows"))]
    let conflicts_with_separator = path_str.contains(':');

    if !conflicts_with_separator {
        cleanup_managed_mod(instance_dir);
        Ok(InjectionPlan::FabricAddModsFlag(format!(
            "-Dfabric.addMods={path_str}"
        )))
    } else {
        let mods_dir = instance_dir.join("mods");
        fs::create_dir_all(&mods_dir)?;
        let dst = mods_dir.join(MANAGED_FALLBACK_NAME);
        fs::copy(&abs, &dst)?;
        Ok(InjectionPlan::ManagedModCopy(dst))
    }
}

pub fn cleanup_managed_mod(instance_dir: &Path) {
    let dst = instance_dir.join("mods").join(MANAGED_FALLBACK_NAME);
    if dst.exists() {
        let _ = fs::remove_file(dst);
    }
}

pub fn detect_duplicate_nightclient_jars(instance_dir: &Path) -> Vec<PathBuf> {
    let mods_dir = instance_dir.join("mods");
    let Ok(entries) = fs::read_dir(mods_dir) else {
        return Vec::new();
    };
    let mut duplicates = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let Some(name) = path.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if !name.ends_with(".jar") || name == MANAGED_FALLBACK_NAME {
            continue;
        }
        if jar_declares_nightclient(&path) {
            duplicates.push(path);
        }
    }
    duplicates
}

fn jar_declares_nightclient(jar_path: &Path) -> bool {
    let Ok(file) = fs::File::open(jar_path) else {
        return false;
    };
    let Ok(mut archive) = zip::ZipArchive::new(file) else {
        return false;
    };
    let Ok(mut meta) = archive.by_name("fabric.mod.json") else {
        return false;
    };
    let mut text = String::new();
    if meta.read_to_string(&mut text).is_err() {
        return false;
    }
    let Ok(json) = serde_json::from_str::<serde_json::Value>(&text) else {
        return false;
    };
    json.get("id")
        .and_then(|v| v.as_str())
        .map(|id| id.eq_ignore_ascii_case("nightclient"))
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn plans_fabric_addmods_and_detects_duplicate_jar() {
        let dir = tempfile::tempdir().unwrap();
        let jar = dir.path().join("nightclient-1.4.0.jar");
        fs::write(&jar, b"fake").unwrap();

        let inst = dir.path().join("inst");
        fs::create_dir_all(inst.join("mods")).unwrap();

        let plan = plan_injection(&jar, &inst).unwrap();
        assert!(matches!(plan, InjectionPlan::FabricAddModsFlag(ref f) if f.starts_with("-Dfabric.addMods=")));

        let dup = inst.join("mods/nightclient-manual.jar");
        let f = fs::File::create(&dup).unwrap();
        let mut zw = zip::ZipWriter::new(f);
        zw.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zw.write_all(br#"{"schemaVersion":1,"id":"nightclient","version":"1.4"}"#)
            .unwrap();
        zw.finish().unwrap();

        let found = detect_duplicate_nightclient_jars(&inst);
        assert_eq!(found, vec![dup]);
    }

    #[test]
    fn strips_windows_verbatim_prefixes() {
        assert_eq!(
            strip_verbatim_prefix(r"\\?\C:\Users\Test\nightclient.jar"),
            r"C:\Users\Test\nightclient.jar"
        );
        assert_eq!(
            strip_verbatim_prefix(r"\\?\UNC\server\share\nightclient.jar"),
            r"\\server\share\nightclient.jar"
        );
        assert_eq!(
            strip_verbatim_prefix("/home/user/nightclient.jar"),
            "/home/user/nightclient.jar"
        );
    }
}
