use std::fs;
use std::path::Path;
use std::time::SystemTime;

const SHARED_FILES: &[&str] = &["settings.json", "skin.nc", "cosmetics.json"];

pub fn sync_before_launch(
    shared_dir: &Path,
    instance_dir: &Path,
    active_theme_file: Option<&Path>,
) -> std::io::Result<()> {
    let target_dir = instance_dir.join("config").join("nightclient");
    fs::create_dir_all(&target_dir)?;
    fs::create_dir_all(shared_dir)?;

    for filename in SHARED_FILES {
        let src = shared_dir.join(filename);
        let dst = target_dir.join(filename);
        if src.is_file() && is_newer_or_missing(&src, &dst) {
            fs::copy(&src, &dst)?;
        }
    }

    if let Some(theme_src) = active_theme_file
        && theme_src.is_file()
    {
        let skin_dst = target_dir.join("skin.nc");
        let _ = fs::copy(theme_src, skin_dst);
    }

    Ok(())
}

pub fn sync_after_exit(shared_dir: &Path, instance_dir: &Path) -> std::io::Result<()> {
    let source_dir = instance_dir.join("config").join("nightclient");
    if !source_dir.is_dir() {
        return Ok(());
    }
    fs::create_dir_all(shared_dir)?;

    for filename in SHARED_FILES {
        let src = source_dir.join(filename);
        let dst = shared_dir.join(filename);
        if src.is_file() && is_newer_or_missing(&src, &dst) {
            fs::copy(&src, &dst)?;
        }
    }
    Ok(())
}

fn is_newer_or_missing(src: &Path, dst: &Path) -> bool {
    let Ok(src_time) = fs::metadata(src).and_then(|m| m.modified()) else {
        return false;
    };
    let Ok(dst_time) = fs::metadata(dst).and_then(|m| m.modified()) else {
        return true;
    };
    src_time > dst_time && src_time != SystemTime::UNIX_EPOCH
}
