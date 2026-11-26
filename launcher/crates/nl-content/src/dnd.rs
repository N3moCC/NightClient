use std::fs;
use std::path::Path;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DroppedFileAction {
    InstallMod,
    InstallResourcePack,
    InstallShaderPack,
    ImportMrpack,
    InstallNightTheme,
    Unsupported,
}

pub fn classify_dropped_file(path: &Path) -> DroppedFileAction {
    if path.is_dir() {
        return classify_directory(path);
    }

    let file_name_lower = path
        .file_name()
        .and_then(|n| n.to_str())
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_default();

    if file_name_lower.ends_with(".jar") || file_name_lower.ends_with(".jar.disabled") {
        return DroppedFileAction::InstallMod;
    }

    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_default();

    match ext.as_str() {
        "nc" => DroppedFileAction::InstallNightTheme,
        "mrpack" => DroppedFileAction::ImportMrpack,
        "zip" => classify_zip(path),
        _ => DroppedFileAction::Unsupported,
    }
}

fn classify_directory(dir: &Path) -> DroppedFileAction {
    if dir.join("shaders").is_dir() && !dir.join("assets").is_dir() {
        return DroppedFileAction::InstallShaderPack;
    }
    if dir.join("pack.mcmeta").is_file() {
        return DroppedFileAction::InstallResourcePack;
    }
    if dir.join("shaders").is_dir() {
        return DroppedFileAction::InstallShaderPack;
    }
    DroppedFileAction::Unsupported
}

fn classify_zip(path: &Path) -> DroppedFileAction {
    let Ok(file) = fs::File::open(path) else {
        return DroppedFileAction::Unsupported;
    };
    let Ok(zip) = zip::ZipArchive::new(file) else {
        return DroppedFileAction::Unsupported;
    };

    let mut has_pack_mcmeta = false;
    let mut has_assets = false;
    let mut has_shaderpack_files = false;

    for raw_name in zip.file_names() {
        let name = raw_name.replace('\\', "/");
        let trimmed = name.trim_start_matches("./").trim_start_matches('/');
        let lower = trimmed.to_ascii_lowercase();

        if lower == "pack.mcmeta" || (lower.ends_with("/pack.mcmeta") && lower.matches('/').count() == 1) {
            has_pack_mcmeta = true;
        }

        if lower.starts_with("assets/")
            || (lower.contains("/assets/") && !lower.starts_with("shaders/"))
        {
            has_assets = true;
        }

        let is_top_or_nested_shaders = lower.starts_with("shaders/")
            || (lower.contains("/shaders/")
                && !lower.contains("assets/")
                && lower.split("/shaders/").next().is_some_and(|prefix| !prefix.contains('/')));

        if is_top_or_nested_shaders
            && (lower.ends_with(".fsh")
                || lower.ends_with(".vsh")
                || lower.ends_with(".gsh")
                || lower.ends_with(".csh")
                || lower.ends_with(".glsl")
                || lower.ends_with("shaders.properties")
                || lower.ends_with("/shaders/"))
        {
            has_shaderpack_files = true;
        }
    }

    if has_shaderpack_files && !has_assets {
        DroppedFileAction::InstallShaderPack
    } else if has_pack_mcmeta {
        DroppedFileAction::InstallResourcePack
    } else if has_shaderpack_files {
        DroppedFileAction::InstallShaderPack
    } else {
        DroppedFileAction::Unsupported
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn make_zip(path: &Path, entries: &[&str]) {
        let f = fs::File::create(path).unwrap();
        let mut zw = zip::ZipWriter::new(f);
        let opts = zip::write::SimpleFileOptions::default();
        for name in entries {
            zw.start_file(*name, opts).unwrap();
            zw.write_all(b"{}").unwrap();
        }
        zw.finish().unwrap();
    }

    #[test]
    fn classifies_large_resource_pack_past_200_entries() {
        let dir = tempfile::tempdir().unwrap();
        let rp = dir.path().join("BigPack.zip");
        let mut names: Vec<String> = (0..260)
            .map(|i| format!("assets/minecraft/textures/block/tex_{i:04}.png"))
            .collect();
        names.push("assets/minecraft/shaders/core/rendertype.fsh".to_string());
        names.push("pack.mcmeta".to_string());
        let refs: Vec<&str> = names.iter().map(String::as_str).collect();
        make_zip(&rp, &refs);

        assert_eq!(classify_dropped_file(&rp), DroppedFileAction::InstallResourcePack);
    }

    #[test]
    fn classifies_shaderpack_even_with_pack_mcmeta_or_nested_folder() {
        let dir = tempfile::tempdir().unwrap();
        let sh = dir.path().join("Complementary.zip");
        make_zip(
            &sh,
            &[
                "Complementary/pack.mcmeta",
                "Complementary/shaders/shaders.properties",
                "Complementary/shaders/gbuffers_terrain.fsh",
            ],
        );
        assert_eq!(classify_dropped_file(&sh), DroppedFileAction::InstallShaderPack);
    }

    #[test]
    fn classifies_jar_disabled_mrpack_and_nc() {
        let dir = tempfile::tempdir().unwrap();
        let jar = dir.path().join("sodium.jar.disabled");
        fs::write(&jar, b"zip").unwrap();
        assert_eq!(classify_dropped_file(&jar), DroppedFileAction::InstallMod);

        let mr = dir.path().join("pack.mrpack");
        fs::write(&mr, b"zip").unwrap();
        assert_eq!(classify_dropped_file(&mr), DroppedFileAction::ImportMrpack);

        let nc = dir.path().join("midnight.nc");
        fs::write(&nc, b"{}").unwrap();
        assert_eq!(classify_dropped_file(&nc), DroppedFileAction::InstallNightTheme);
    }
}
