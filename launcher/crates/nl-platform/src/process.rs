use serde::{Deserialize, Serialize};
use std::process::Command;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum GpuPreference {
    #[default]
    Auto,
    Discrete,
}

#[derive(Debug, Clone, Copy, Default)]
pub struct LinuxWrappers {
    pub has_gamemode: bool,
    pub has_mangohud: bool,
}

#[derive(Debug, Clone, Default)]
pub struct LaunchEnvironment {
    pub gpu_preference: GpuPreference,
    pub use_gamemode: bool,
    pub use_mangohud: bool,
    pub extra_env: Vec<(String, String)>,
}

pub fn configure_background_command(cmd: &mut Command) {
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    #[cfg(not(target_os = "windows"))]
    let _ = cmd;
}

pub fn detect_linux_wrappers() -> LinuxWrappers {
    #[cfg(target_os = "linux")]
    {
        LinuxWrappers {
            has_gamemode: command_exists("gamemoderun"),
            has_mangohud: command_exists("mangohud"),
        }
    }
    #[cfg(not(target_os = "linux"))]
    {
        LinuxWrappers::default()
    }
}

#[cfg(target_os = "linux")]
fn command_exists(bin: &str) -> bool {
    let Ok(path_var) = std::env::var("PATH") else {
        return false;
    };
    for dir in std::env::split_paths(&path_var) {
        let candidate = dir.join(bin);
        if candidate.is_file() {
            return true;
        }
    }
    false
}

pub fn configure_game_command(
    java_executable: &str,
    jvm_and_game_args: &[String],
    env: &LaunchEnvironment,
) -> tokio::process::Command {
    #[cfg(target_os = "linux")]
    let mut cmd = {
        let wrappers = detect_linux_wrappers();
        let mut chain: Vec<String> = Vec::new();
        if env.use_gamemode && wrappers.has_gamemode {
            chain.push("gamemoderun".to_string());
        }
        if env.use_mangohud && wrappers.has_mangohud {
            chain.push("mangohud".to_string());
        }
        if chain.is_empty() {
            let mut c = tokio::process::Command::new(java_executable);
            c.args(jvm_and_game_args);
            c
        } else {
            let mut c = tokio::process::Command::new(&chain[0]);
            for extra in &chain[1..] {
                c.arg(extra);
            }
            c.arg(java_executable);
            c.args(jvm_and_game_args);
            c
        }
    };

    #[cfg(not(target_os = "linux"))]
    let mut cmd = {
        let mut c = tokio::process::Command::new(java_executable);
        c.args(jvm_and_game_args);
        c
    };

    #[cfg(target_os = "windows")]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    #[cfg(target_os = "linux")]
    {
        if env.gpu_preference == GpuPreference::Discrete {
            if has_nvidia_gpu() {
                cmd.env("__NV_PRIME_RENDER_OFFLOAD", "1");
                cmd.env("__NV_PRIME_RENDER_OFFLOAD_PROVIDER", "NVIDIA-G0");
                cmd.env("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
                cmd.env("__VK_LAYER_NV_optimus", "NVIDIA_only");
            } else {
                cmd.env("DRI_PRIME", "1");
            }
        }
    }

    for (k, v) in &env.extra_env {
        cmd.env(k, v);
    }

    cmd
}

#[cfg(target_os = "linux")]
fn has_nvidia_gpu() -> bool {
    std::path::Path::new("/proc/driver/nvidia/version").exists()
        || std::path::Path::new("/dev/nvidia0").exists()
}

pub fn terminate_process(pid: u32, force: bool) -> bool {
    #[cfg(unix)]
    {
        let sig = if force { libc::SIGKILL } else { libc::SIGTERM };
        let rc = unsafe { libc::kill(pid as libc::pid_t, sig) };
        rc == 0
    }
    #[cfg(target_os = "windows")]
    {
        let mut cmd = Command::new("taskkill");
        configure_background_command(&mut cmd);
        cmd.arg("/PID").arg(pid.to_string()).arg("/T");
        if force {
            cmd.arg("/F");
        }
        cmd.status().map(|s| s.success()).unwrap_or(false)
    }
    #[cfg(not(any(unix, target_os = "windows")))]
    {
        let _ = (pid, force);
        false
    }
}
