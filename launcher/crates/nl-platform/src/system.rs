use fs2::FileExt;
use std::fs::{self, File, OpenOptions};
use std::path::{Path, PathBuf};
use thiserror::Error;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MojangOs {
    Linux,
    Windows,
    Osx,
}

impl MojangOs {
    pub fn as_mojang_str(self) -> &'static str {
        match self {
            MojangOs::Linux => "linux",
            MojangOs::Windows => "windows",
            MojangOs::Osx => "osx",
        }
    }

    pub fn as_adoptium_str(self) -> &'static str {
        match self {
            MojangOs::Linux => "linux",
            MojangOs::Windows => "windows",
            MojangOs::Osx => "mac",
        }
    }

    pub fn classpath_separator(self) -> &'static str {
        match self {
            MojangOs::Windows => ";",
            MojangOs::Linux | MojangOs::Osx => ":",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CpuArch {
    X86_64,
    Aarch64,
    X86,
}

impl CpuArch {
    pub fn as_adoptium_str(self) -> &'static str {
        match self {
            CpuArch::X86_64 => "x64",
            CpuArch::Aarch64 => "aarch64",
            CpuArch::X86 => "x32",
        }
    }

    pub fn as_mojang_arch(self) -> &'static str {
        match self {
            CpuArch::X86_64 => "x64",
            CpuArch::Aarch64 => "arm64",
            CpuArch::X86 => "x86",
        }
    }
}

pub fn current_mojang_os() -> MojangOs {
    #[cfg(target_os = "windows")]
    {
        MojangOs::Windows
    }
    #[cfg(target_os = "macos")]
    {
        MojangOs::Osx
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    {
        MojangOs::Linux
    }
}

pub fn current_arch() -> CpuArch {
    #[cfg(target_arch = "aarch64")]
    {
        CpuArch::Aarch64
    }
    #[cfg(target_arch = "x86")]
    {
        CpuArch::X86
    }
    #[cfg(not(any(target_arch = "aarch64", target_arch = "x86")))]
    {
        CpuArch::X86_64
    }
}

pub struct InstanceLock {
    _file: File,
}

impl InstanceLock {
    pub fn acquire(lock_path: &Path) -> std::io::Result<Option<Self>> {
        if let Some(parent) = lock_path.parent() {
            fs::create_dir_all(parent)?;
        }
        let file = OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .truncate(false)
            .open(lock_path)?;
        match file.try_lock_exclusive() {
            Ok(()) => Ok(Some(Self { _file: file })),
            Err(_) => Ok(None),
        }
    }
}

#[derive(Debug, Error)]
#[error("insufficient disk space: need {needed_mb} MB, available {available_mb} MB")]
pub struct DiskSpaceError {
    pub needed_mb: u64,
    pub available_mb: u64,
}

pub fn available_disk_bytes(path: &Path) -> Option<u64> {
    let probe = if path.exists() {
        path.to_path_buf()
    } else {
        path.ancestors()
            .find(|a| a.exists())
            .map(Path::to_path_buf)?
    };
    fs2::available_space(&probe).ok()
}

pub fn check_disk_space(path: &Path, required_bytes: u64) -> Result<(), DiskSpaceError> {
    if required_bytes == 0 {
        return Ok(());
    }
    if let Some(avail) = available_disk_bytes(path)
        && avail < required_bytes
    {
        return Err(DiskSpaceError {
            needed_mb: required_bytes / (1024 * 1024),
            available_mb: avail / (1024 * 1024),
        });
    }
    Ok(())
}

pub fn total_memory_mb() -> u64 {
    #[cfg(target_os = "linux")]
    {
        if let Ok(content) = fs::read_to_string("/proc/meminfo") {
            for line in content.lines() {
                if let Some(rest) = line.strip_prefix("MemTotal:") {
                    let kb: u64 = rest
                        .split_whitespace()
                        .next()
                        .and_then(|s| s.parse().ok())
                        .unwrap_or(0);
                    if kb > 0 {
                        return kb / 1024;
                    }
                }
            }
        }
        8192
    }
    #[cfg(target_os = "macos")]
    {
        let mut cmd = std::process::Command::new("sysctl");
        cmd.args(["-n", "hw.memsize"]);
        if let Ok(out) = cmd.output()
            && let Ok(s) = String::from_utf8(out.stdout)
            && let Ok(bytes) = s.trim().parse::<u64>()
        {
            return bytes / (1024 * 1024);
        }
        8192
    }
    #[cfg(target_os = "windows")]
    {
        #[repr(C)]
        struct MemoryStatusEx {
            dw_length: u32,
            dw_memory_load: u32,
            ull_total_phys: u64,
            ull_avail_phys: u64,
            ull_total_page_file: u64,
            ull_avail_page_file: u64,
            ull_total_virtual: u64,
            ull_avail_virtual: u64,
            ull_avail_extended_virtual: u64,
        }
        #[link(name = "kernel32")]
        unsafe extern "system" {
            fn GlobalMemoryStatusEx(lp_buffer: *mut MemoryStatusEx) -> i32;
        }
        let mut st = MemoryStatusEx {
            dw_length: std::mem::size_of::<MemoryStatusEx>() as u32,
            dw_memory_load: 0,
            ull_total_phys: 0,
            ull_avail_phys: 0,
            ull_total_page_file: 0,
            ull_avail_page_file: 0,
            ull_total_virtual: 0,
            ull_avail_virtual: 0,
            ull_avail_extended_virtual: 0,
        };
        let ok = unsafe { GlobalMemoryStatusEx(&mut st) };
        if ok != 0 && st.ull_total_phys > 0 {
            return st.ull_total_phys / (1024 * 1024);
        }
        8192
    }
    #[cfg(not(any(target_os = "linux", target_os = "macos", target_os = "windows")))]
    {
        8192
    }
}

pub fn copy_image_to_clipboard(image_path: &Path) -> Result<(), String> {
    if !image_path.is_file() {
        return Err(format!("Image not found: {}", image_path.display()));
    }
    #[cfg(target_os = "linux")]
    {
        let bytes = fs::read(image_path).map_err(|e| e.to_string())?;
        if std::env::var("WAYLAND_DISPLAY").is_ok() {
            use std::io::Write;
            use std::process::{Command, Stdio};
            use std::time::{Duration, Instant};
            if let Ok(mut child) = Command::new("wl-copy")
                .args(["--type", "image/png"])
                .stdin(Stdio::piped())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .spawn()
            {
                if let Some(mut stdin) = child.stdin.take() {
                    let _ = stdin.write_all(&bytes);
                }
                let deadline = Instant::now() + Duration::from_secs(2);
                loop {
                    match child.try_wait() {
                        Ok(Some(status)) => {
                            if status.success() {
                                return Ok(());
                            }
                            break;
                        }
                        Ok(None) => {
                            if Instant::now() >= deadline {
                                return Ok(());
                            }
                            std::thread::sleep(Duration::from_millis(15));
                        }
                        Err(_) => break,
                    }
                }
            }
        }
        use std::process::{Command, Stdio};
        let status = Command::new("xclip")
            .args([
                "-selection",
                "clipboard",
                "-t",
                "image/png",
                "-i",
                &image_path.to_string_lossy(),
            ])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Install wl-copy (Wayland) or xclip (X11) to copy images".to_string())?;
        if status.success() {
            Ok(())
        } else {
            Err("Clipboard command failed".to_string())
        }
    }
    #[cfg(target_os = "macos")]
    {
        let script = format!(
            "set the clipboard to (read (POSIX file \"{}\") as «class PNGf»)",
            image_path.to_string_lossy().replace('"', "\\\"")
        );
        let status = std::process::Command::new("osascript")
            .args(["-e", &script])
            .status()
            .map_err(|e| e.to_string())?;
        if status.success() {
            Ok(())
        } else {
            Err("osascript clipboard copy failed".to_string())
        }
    }
    #[cfg(target_os = "windows")]
    {
        let script = format!(
            "Add-Type -AssemblyName System.Windows.Forms; \
             [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('{}'))",
            image_path.to_string_lossy().replace('\'', "''")
        );
        let mut cmd = std::process::Command::new("powershell");
        crate::process::configure_background_command(&mut cmd);
        let status = cmd
            .args(["-NoProfile", "-STA", "-Command", &script])
            .status()
            .map_err(|e| e.to_string())?;
        if status.success() {
            Ok(())
        } else {
            Err("Windows clipboard copy failed".to_string())
        }
    }
    #[cfg(not(any(target_os = "linux", target_os = "macos", target_os = "windows")))]
    {
        Err("Clipboard image copy unsupported on this platform".to_string())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DeepLink {
    LaunchInstance(String),
    JoinServer(String),
    InstallTheme(String),
}

pub fn parse_deep_link(uri: &str) -> Option<DeepLink> {
    let rest = uri.strip_prefix("nightclient://")?;
    let (action, payload) = rest.split_once('/')?;
    let payload = payload.trim();
    if payload.is_empty() {
        return None;
    }
    match action {
        "launch" => Some(DeepLink::LaunchInstance(payload.to_string())),
        "join" => Some(DeepLink::JoinServer(payload.to_string())),
        "theme" => Some(DeepLink::InstallTheme(payload.to_string())),
        _ => None,
    }
}

pub const LINUX_APP_ID: &str = "dev.nightclient.launcher";

pub fn install_app_shortcut(
    binary_path: &Path,
    icon_png_bytes: &[u8],
) -> Result<PathBuf, String> {
    #[cfg(target_os = "linux")]
    {
        let home = std::env::var("HOME").map_err(|_| "HOME not set".to_string())?;
        let base = PathBuf::from(home).join(".local/share");
        let hicolor_root = base.join("icons/hicolor");
        let icon_dir = hicolor_root.join("256x256/apps");
        let app_dir = base.join("applications");
        fs::create_dir_all(&icon_dir).map_err(|e| e.to_string())?;
        fs::create_dir_all(&app_dir).map_err(|e| e.to_string())?;

        let icon_path = icon_dir.join(format!("{LINUX_APP_ID}.png"));
        let needs_icon = fs::metadata(&icon_path)
            .map(|m| m.len() != icon_png_bytes.len() as u64)
            .unwrap_or(true);
        if needs_icon {
            fs::write(&icon_path, icon_png_bytes).map_err(|e| e.to_string())?;
        }

        let legacy_desktop = app_dir.join("nightclient-launcher.desktop");
        if legacy_desktop.is_file() {
            let _ = fs::remove_file(&legacy_desktop);
        }

        let desktop_path = app_dir.join(format!("{LINUX_APP_ID}.desktop"));
        let content = format!(
            "[Desktop Entry]\n\
             Type=Application\n\
             Name=NightClient Launcher\n\
             Comment=Minecraft instance launcher with integrated NightClient visuals\n\
             Exec=\"{}\" %u\n\
             Icon={LINUX_APP_ID}\n\
             Categories=Game;\n\
             Terminal=false\n\
             StartupNotify=true\n\
             StartupWMClass={LINUX_APP_ID}\n\
             MimeType=x-scheme-handler/nightclient;\n",
            binary_path.display()
        );
        let existing = fs::read_to_string(&desktop_path).unwrap_or_default();
        if existing != content {
            fs::write(&desktop_path, content).map_err(|e| e.to_string())?;
        }

        if needs_icon || existing.is_empty() {
            let _ = std::process::Command::new("gtk-update-icon-cache")
                .args(["-q", "-t", "-f"])
                .arg(&hicolor_root)
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn();
            let _ = std::process::Command::new("update-desktop-database")
                .arg("-q")
                .arg(&app_dir)
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn();
        }
        Ok(desktop_path)
    }
    #[cfg(target_os = "windows")]
    {
        let _ = icon_png_bytes;
        let appdata = std::env::var("APPDATA").map_err(|_| "APPDATA not set".to_string())?;
        let programs = PathBuf::from(appdata)
            .join("Microsoft")
            .join("Windows")
            .join("Start Menu")
            .join("Programs");
        fs::create_dir_all(&programs).map_err(|e| e.to_string())?;
        let lnk_path = programs.join("NightClient Launcher.lnk");
        let exe_escaped = binary_path.to_string_lossy().replace('\'', "''");
        let lnk_escaped = lnk_path.to_string_lossy().replace('\'', "''");
        let work_dir = binary_path
            .parent()
            .map(|p| p.to_string_lossy().replace('\'', "''"))
            .unwrap_or_default();
        let script = format!(
            "$ws = New-Object -ComObject WScript.Shell; \
             $s = $ws.CreateShortcut('{lnk_escaped}'); \
             $s.TargetPath = '{exe_escaped}'; \
             $s.WorkingDirectory = '{work_dir}'; \
             $s.IconLocation = '{exe_escaped},0'; \
             $s.Description = 'NightClient Launcher'; \
             $s.Save()"
        );
        let mut cmd = std::process::Command::new("powershell");
        crate::process::configure_background_command(&mut cmd);
        let status = cmd
            .args(["-NoProfile", "-Command", &script])
            .status()
            .map_err(|e| e.to_string())?;
        if status.success() {
            Ok(lnk_path)
        } else {
            Err("Failed to create Windows Start Menu shortcut".to_string())
        }
    }
    #[cfg(target_os = "macos")]
    {
        let _ = icon_png_bytes;
        let home = std::env::var("HOME").map_err(|_| "HOME not set".to_string())?;
        let apps = PathBuf::from(home).join("Applications");
        fs::create_dir_all(&apps).map_err(|e| e.to_string())?;
        let link = apps.join("NightClient Launcher");
        if link.exists() {
            let _ = fs::remove_file(&link);
        }
        std::os::unix::fs::symlink(binary_path, &link).map_err(|e| e.to_string())?;
        Ok(link)
    }
    #[cfg(not(any(target_os = "linux", target_os = "windows", target_os = "macos")))]
    {
        let _ = (binary_path, icon_png_bytes);
        Err("Shortcut installation is unsupported on this OS".to_string())
    }
}

pub fn ensure_linux_desktop_integration(binary_path: &Path, icon_png_bytes: &[u8]) {
    #[cfg(target_os = "linux")]
    {
        let _ = install_app_shortcut(binary_path, icon_png_bytes);
    }
    #[cfg(not(target_os = "linux"))]
    {
        let _ = (binary_path, icon_png_bytes);
    }
}

pub fn install_linux_desktop_entry(
    binary_path: &Path,
    icon_png_bytes: &[u8],
) -> Result<PathBuf, String> {
    install_app_shortcut(binary_path, icon_png_bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_deep_links() {
        assert_eq!(
            parse_deep_link("nightclient://join/mc.holyworld.ru"),
            Some(DeepLink::JoinServer("mc.holyworld.ru".into()))
        );
        assert_eq!(
            parse_deep_link("nightclient://launch/pvp-1.21.4"),
            Some(DeepLink::LaunchInstance("pvp-1.21.4".into()))
        );
        assert_eq!(parse_deep_link("https://example.com"), None);
    }

    #[test]
    fn single_instance_lock_prevents_second_acquire() {
        let dir = tempfile::tempdir().unwrap();
        let lock_path = dir.path().join("launcher.lock");
        let first = InstanceLock::acquire(&lock_path).unwrap();
        assert!(first.is_some());
        let second = InstanceLock::acquire(&lock_path).unwrap();
        assert!(second.is_none());
        drop(first);
        let third = InstanceLock::acquire(&lock_path).unwrap();
        assert!(third.is_some());
    }
}
