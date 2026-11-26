use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, PartialEq)]
pub struct NcThemeTokens {
    pub theme_name: String,
    pub background: [u8; 4],
    pub surface: [u8; 4],
    pub raised: [u8; 4],
    pub text_primary: [u8; 4],
    pub text_muted: [u8; 4],
    pub accent: [u8; 4],
    pub border: [u8; 4],
    pub border_radius: f32,
    pub transition_ms: u32,
}

impl Default for NcThemeTokens {
    fn default() -> Self {
        Self {
            theme_name: "NightClient".to_string(),
            background: [0x10, 0x12, 0x16, 0xFF],
            surface: [0x17, 0x1A, 0x20, 0xFF],
            raised: [0x22, 0x26, 0x2F, 0xFF],
            text_primary: [0xF4, 0xF6, 0xFB, 0xFF],
            text_muted: [0xA0, 0xA7, 0xB3, 0xFF],
            accent: [0xF4, 0xF6, 0xFB, 0xFF],
            border: [0xFF, 0xFF, 0xFF, 0x20],
            border_radius: 12.0,
            transition_ms: 220,
        }
    }
}

#[derive(Debug, Clone)]
pub struct InstalledNcTheme {
    pub id: String,
    pub name: String,
    pub path: Option<PathBuf>,
    pub tokens: NcThemeTokens,
}

pub fn parse_nc_tokens(source: &str) -> NcThemeTokens {
    let mut tokens = NcThemeTokens::default();
    let mut in_defaults = false;
    let mut depth = 0i32;

    for raw_line in source.lines() {
        let line = raw_line.split("//").next().unwrap_or("").trim();
        if line.is_empty() {
            continue;
        }
        if !in_defaults {
            if line.starts_with("defaults") && line.contains('{') {
                in_defaults = true;
                depth = 1;
            }
            continue;
        }
        for ch in line.chars() {
            if ch == '{' {
                depth += 1;
            } else if ch == '}' {
                depth -= 1;
            }
        }
        if depth <= 0 {
            in_defaults = false;
            continue;
        }

        if let Some((k, v)) = line.split_once('=') {
            let key = k.trim();
            let val = v.trim().trim_matches('"').trim_end_matches(';');
            match key {
                "themeName" if !val.is_empty() => tokens.theme_name = val.to_string(),
                "background" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.background = c;
                    }
                }
                "surface" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.surface = c;
                    }
                }
                "raised" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.raised = c;
                    }
                }
                "color" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.text_primary = c;
                    }
                }
                "muted" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.text_muted = c;
                    }
                }
                "accent" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.accent = c;
                    }
                }
                "borderColor" => {
                    if let Some(c) = parse_hex_rgba(val) {
                        tokens.border = c;
                    }
                }
                "borderRadius" => {
                    if let Ok(r) = val.trim_end_matches("px").parse::<f32>() {
                        tokens.border_radius = r.clamp(4.0, 20.0);
                    }
                }
                "transitionDuration" => {
                    if let Ok(ms) = val.trim_end_matches("ms").parse::<u32>() {
                        tokens.transition_ms = ms.clamp(80, 400);
                    }
                }
                _ => {}
            }
        }
    }

    tokens
}

fn parse_hex_rgba(raw: &str) -> Option<[u8; 4]> {
    let hex = raw.trim().strip_prefix('#')?;
    match hex.len() {
        6 => {
            let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
            let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
            let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
            Some([r, g, b, 0xFF])
        }
        8 => {
            let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
            let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
            let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
            let a = u8::from_str_radix(&hex[6..8], 16).ok()?;
            Some([r, g, b, a])
        }
        _ => None,
    }
}

pub fn list_installed_themes(themes_dir: &Path) -> Vec<InstalledNcTheme> {
    let mut themes = vec![InstalledNcTheme {
        id: "default".to_string(),
        name: "NightClient".to_string(),
        path: None,
        tokens: NcThemeTokens::default(),
    }];

    let Ok(entries) = fs::read_dir(themes_dir) else {
        return themes;
    };

    for entry in entries.flatten() {
        let p = entry.path();
        if p.is_file() && p.extension().and_then(|e| e.to_str()) == Some("nc") {
            if let Ok(src) = fs::read_to_string(&p) {
                let tokens = parse_nc_tokens(&src);
                let stem = p
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("custom")
                    .to_string();
                let name = if tokens.theme_name == "NightClient" {
                    stem.clone()
                } else {
                    tokens.theme_name.clone()
                };
                themes.push(InstalledNcTheme {
                    id: stem,
                    name,
                    path: Some(p),
                    tokens,
                });
            }
        } else if p.is_dir() {
            let defaults = p.join("theme-defaults.nc");
            if defaults.is_file()
                && let Ok(src) = fs::read_to_string(&defaults)
            {
                let tokens = parse_nc_tokens(&src);
                let id = p
                    .file_name()
                    .and_then(|s| s.to_str())
                    .unwrap_or("custom")
                    .to_string();
                themes.push(InstalledNcTheme {
                    id: id.clone(),
                    name: tokens.theme_name.clone(),
                    path: Some(defaults),
                    tokens,
                });
            }
        }
    }

    themes
}

pub fn install_nc_theme_file(source: &Path, themes_dir: &Path) -> Result<InstalledNcTheme, String> {
    if !source.is_file() {
        return Err("Theme file does not exist".into());
    }
    let content = fs::read_to_string(source).map_err(|e| e.to_string())?;
    let tokens = parse_nc_tokens(&content);
    fs::create_dir_all(themes_dir).map_err(|e| e.to_string())?;
    let filename = source
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("custom.nc");
    let dest = themes_dir.join(nl_platform::sanitize_filename(filename));
    nl_platform::atomic_write(&dest, content.as_bytes()).map_err(|e| e.to_string())?;
    let id = dest
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("custom")
        .to_string();
    Ok(InstalledNcTheme {
        id,
        name: tokens.theme_name.clone(),
        path: Some(dest),
        tokens,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_real_nightclient_defaults_block() {
        let sample = r#"
defaults {
  themeName = "Obsidian Dusk"
  background = #0E1015
  surface = #161922
  raised = #202533
  color = #F1F4FA
  muted = #8E97A8
  accent = #DCE6FF
  borderColor = #FFFFFF18
  borderRadius = 10px
  transitionDuration = 200ms
}
"#;
        let t = parse_nc_tokens(sample);
        assert_eq!(t.theme_name, "Obsidian Dusk");
        assert_eq!(t.background, [0x0E, 0x10, 0x15, 0xFF]);
        assert_eq!(t.border, [0xFF, 0xFF, 0xFF, 0x18]);
        assert_eq!(t.border_radius, 10.0);
        assert_eq!(t.transition_ms, 200);
    }
}
