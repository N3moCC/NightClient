use chrono::{DateTime, Local};
use sha2::{Digest, Sha256};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

#[derive(Debug, Clone)]
pub struct ScreenshotEntry {
    pub filename: String,
    pub path: PathBuf,
    pub thumbnail_path: PathBuf,
    pub width: u32,
    pub height: u32,
    pub size_bytes: u64,
    pub modified_label: String,
    pub modified_secs: u64,
}

pub fn scan_screenshots(instance_dir: &Path, thumb_cache_dir: &Path) -> Vec<ScreenshotEntry> {
    let shots_dir = instance_dir.join("screenshots");
    let Ok(entries) = fs::read_dir(&shots_dir) else {
        return Vec::new();
    };
    let _ = fs::create_dir_all(thumb_cache_dir);

    let mut out = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let ext = path
            .extension()
            .and_then(|e| e.to_str())
            .map(|s| s.to_ascii_lowercase())
            .unwrap_or_default();
        if !matches!(ext.as_str(), "png" | "jpg" | "jpeg" | "webp") {
            continue;
        }
        let Ok(meta) = fs::metadata(&path) else {
            continue;
        };
        let modified = meta.modified().ok();
        let modified_secs = modified
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let modified_label = modified
            .map(|t| {
                let dt: DateTime<Local> = t.into();
                dt.format("%Y-%m-%d %H:%M").to_string()
            })
            .unwrap_or_default();

        let filename = path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("screenshot.png")
            .to_string();

        let key = format!("{}:{}:{}", path.display(), meta.len(), modified_secs);
        let hash = hex::encode(&Sha256::digest(key.as_bytes())[..12]);
        let thumb_path = thumb_cache_dir.join(format!("{hash}_v2.png"));

        let (width, height) = if thumb_path.is_file() {
            image::image_dimensions(&path).unwrap_or((0, 0))
        } else if let Ok(img) = image::open(&path) {
            let dims = (img.width(), img.height());
            let thumb = img.thumbnail(640, 360);
            let _ = thumb.save(&thumb_path);
            dims
        } else {
            (0, 0)
        };

        out.push(ScreenshotEntry {
            filename,
            thumbnail_path: if thumb_path.is_file() {
                thumb_path
            } else {
                path.clone()
            },
            path,
            width,
            height,
            size_bytes: meta.len(),
            modified_label,
            modified_secs,
        });
    }

    out.sort_by_key(|entry| std::cmp::Reverse(entry.modified_secs));
    out
}
