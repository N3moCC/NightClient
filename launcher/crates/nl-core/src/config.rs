use nl_java::PerformanceProfile;
use nl_net::NetConfig;
use nl_night::NightClientChannel;
use nl_platform::{
    GpuPreference, LauncherPaths, read_json_with_backup, total_memory_mb, write_json_atomic,
};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ThemeMode {
    #[default]
    Dark,
    Light,
}

#[cfg(target_os = "windows")]
fn detect_windows_locale() -> Option<String> {
    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn GetUserDefaultLocaleName(lp_locale_name: *mut u16, cch_locale_name: i32) -> i32;
    }
    let mut buf = [0u16; 85];
    let len = unsafe { GetUserDefaultLocaleName(buf.as_mut_ptr(), buf.len() as i32) };
    if len <= 1 {
        return None;
    }
    let name = String::from_utf16_lossy(&buf[..(len as usize - 1)]);
    let code = name
        .split(['_', '-', '.', '@'])
        .next()
        .unwrap_or("")
        .to_ascii_lowercase();
    if code.len() >= 2 && code.chars().all(|c| c.is_ascii_alphabetic()) {
        Some(code)
    } else {
        None
    }
}

pub fn detect_system_locale() -> String {
    #[cfg(target_os = "windows")]
    if let Some(code) = detect_windows_locale() {
        return code;
    }
    for key in ["LC_ALL", "LC_MESSAGES", "LANG"] {
        if let Ok(val) = std::env::var(key) {
            let trimmed = val.trim();
            if !trimmed.is_empty() && trimmed != "C" && trimmed != "POSIX" {
                let code = trimmed
                    .split(['_', '-', '.', '@'])
                    .next()
                    .unwrap_or("")
                    .to_ascii_lowercase();
                if code.len() >= 2 && code.chars().all(|c| c.is_ascii_alphabetic()) {
                    return code;
                }
            }
        }
    }
    "en".to_string()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct LauncherConfig {
    pub schema_version: u32,
    #[serde(alias = "language")]
    pub locale: String,
    pub theme_mode: ThemeMode,
    pub active_theme_id: String,
    pub sync_ui_with_nc_theme: bool,
    pub default_memory_mb: u32,
    pub performance_profile: PerformanceProfile,
    pub default_turbo_mode: bool,
    pub default_java_path: Option<String>,
    pub extra_jvm_args: String,
    pub extra_game_args: String,
    pub gpu_preference: GpuPreference,
    pub use_gamemode: bool,
    pub use_mangohud: bool,
    pub minimize_on_launch: bool,
    pub nightclient_channel: NightClientChannel,
    pub ms_client_id: Option<String>,
    pub net: NetConfig,
    pub selected_instance_id: Option<String>,
    pub dismissed_update_tag: Option<String>,
    pub onboarding_completed: bool,
}

impl Default for LauncherConfig {
    fn default() -> Self {
        let sys_mb = total_memory_mb();
        let recommended = ((sys_mb / 3).clamp(2048, 6144) / 512) * 512;
        Self {
            schema_version: 3,
            locale: detect_system_locale(),
            theme_mode: ThemeMode::Dark,
            active_theme_id: "default".to_string(),
            sync_ui_with_nc_theme: false,
            default_memory_mb: recommended as u32,
            performance_profile: PerformanceProfile::Smooth,
            default_turbo_mode: false,
            default_java_path: None,
            extra_jvm_args: String::new(),
            extra_game_args: String::new(),
            gpu_preference: GpuPreference::Auto,
            use_gamemode: false,
            use_mangohud: false,
            minimize_on_launch: false,
            nightclient_channel: NightClientChannel::Stable,
            ms_client_id: None,
            net: NetConfig::default(),
            selected_instance_id: None,
            dismissed_update_tag: None,
            onboarding_completed: false,
        }
    }
}

pub fn load_config(paths: &LauncherPaths) -> LauncherConfig {
    read_json_with_backup(&paths.config_file()).unwrap_or_default()
}

pub fn save_config(paths: &LauncherPaths, config: &LauncherConfig) -> std::io::Result<()> {
    write_json_atomic(&paths.config_file(), config)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_valid_system_locale_code() {
        let code = detect_system_locale();
        assert!(code.len() >= 2);
        assert!(code.chars().all(|c| c.is_ascii_lowercase()));
    }
}
