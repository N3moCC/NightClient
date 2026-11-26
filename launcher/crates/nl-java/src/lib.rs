use nl_net::{DownloadItem, ExpectedHash, NetClient, NetError};
use nl_platform::{
    configure_background_command, current_arch, current_mojang_os, read_json_with_backup,
    write_json_atomic, MojangOs,
};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::UNIX_EPOCH;
use thiserror::Error;
use tokio_util::sync::CancellationToken;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum PerformanceProfile {
    #[default]
    Smooth,
    ZeroPause,
    PeakFps,
    Stock,
}

impl PerformanceProfile {
    pub const ALL: [PerformanceProfile; 4] = [
        PerformanceProfile::Smooth,
        PerformanceProfile::ZeroPause,
        PerformanceProfile::PeakFps,
        PerformanceProfile::Stock,
    ];
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JavaRuntime {
    pub path: PathBuf,
    pub major: u32,
    pub version_label: String,
    pub supports_shenandoah: bool,
    pub supports_gen_shenandoah: bool,
    pub supports_gen_zgc: bool,
    pub supports_compact_headers: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct CachedProbe {
    mtime_secs: u64,
    file_size: u64,
    runtime: JavaRuntime,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct ProbeCache {
    entries: BTreeMap<String, CachedProbe>,
}

#[derive(Debug, Error)]
pub enum JavaError {
    #[error("network error while installing Java: {0}")]
    Net(#[from] NetError),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("no Adoptium release found for Java {major} on {os}/{arch}")]
    NoRelease { major: u32, os: String, arch: String },
    #[error("failed to extract Java archive: {0}")]
    Extract(String),
}

pub fn scan_java_runtimes(runtimes_dir: &Path, probe_cache_path: &Path) -> Vec<JavaRuntime> {
    let mut cache: ProbeCache = read_json_with_backup(probe_cache_path).unwrap_or_default();
    let mut candidates: BTreeSet<PathBuf> = BTreeSet::new();

    collect_managed_runtimes(runtimes_dir, &mut candidates);
    collect_system_runtimes(&mut candidates);

    let mut result = Vec::new();
    let mut seen_canonical: BTreeSet<PathBuf> = BTreeSet::new();
    let mut cache_dirty = false;

    for exe in candidates {
        let canonical = fs::canonicalize(&exe).unwrap_or(exe);
        if !seen_canonical.insert(canonical.clone()) {
            continue;
        }
        let Ok(meta) = fs::metadata(&canonical) else {
            continue;
        };
        if !meta.is_file() {
            continue;
        }
        let mtime_secs = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let file_size = meta.len();
        let key = canonical.to_string_lossy().to_string();

        if let Some(cached) = cache.entries.get(&key)
            && cached.mtime_secs == mtime_secs
            && cached.file_size == file_size
        {
            result.push(cached.runtime.clone());
            continue;
        }

        if let Some(probed) = probe_java_runtime(&canonical) {
            cache.entries.insert(
                key,
                CachedProbe {
                    mtime_secs,
                    file_size,
                    runtime: probed.clone(),
                },
            );
            cache_dirty = true;
            result.push(probed);
        }
    }

    if cache_dirty {
        let _ = write_json_atomic(probe_cache_path, &cache);
    }

    result.sort_by(|a, b| b.major.cmp(&a.major).then_with(|| a.path.cmp(&b.path)));
    result
}

pub fn select_best_runtime(runtimes: &[JavaRuntime], required_major: u32) -> Option<&JavaRuntime> {
    if let Some(exact) = runtimes.iter().find(|r| r.major == required_major) {
        return Some(exact);
    }
    if required_major <= 8 {
        return None;
    }
    runtimes
        .iter()
        .filter(|r| r.major > required_major)
        .min_by_key(|r| r.major)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MemoryWarning {
    HighSystemRatio { requested_mb: u32, system_mb: u64 },
    ExcessiveHeap { requested_mb: u32 },
    LowHeapForZeroPause { requested_mb: u32, recommended_mb: u32 },
}

#[derive(Debug, Clone)]
pub struct MemoryAdvice {
    pub clamped_mb: u32,
    pub warning: Option<MemoryWarning>,
}

pub fn evaluate_memory_mb(requested_mb: u32, system_ram_mb: u64) -> MemoryAdvice {
    let clamped = requested_mb.max(1024);
    let warning = if u64::from(clamped) > (system_ram_mb * 3 / 4) && system_ram_mb >= 4096 {
        Some(MemoryWarning::HighSystemRatio {
            requested_mb: clamped,
            system_mb: system_ram_mb,
        })
    } else if clamped > 12288 {
        Some(MemoryWarning::ExcessiveHeap {
            requested_mb: clamped,
        })
    } else {
        None
    };
    MemoryAdvice {
        clamped_mb: clamped,
        warning,
    }
}

pub fn evaluate_profile_memory_warning(
    profile: PerformanceProfile,
    requested_mb: u32,
) -> Option<MemoryWarning> {
    let clamped = requested_mb.max(1024);
    if profile == PerformanceProfile::ZeroPause && clamped <= 4096 {
        Some(MemoryWarning::LowHeapForZeroPause {
            requested_mb: clamped,
            recommended_mb: 6144,
        })
    } else {
        None
    }
}

pub fn evaluate_memory_for_profile(
    requested_mb: u32,
    system_ram_mb: u64,
    profile: PerformanceProfile,
) -> MemoryAdvice {
    let mut advice = evaluate_memory_mb(requested_mb, system_ram_mb);
    if advice.warning.is_none() {
        advice.warning = evaluate_profile_memory_warning(profile, advice.clamped_mb);
    }
    advice
}

pub fn build_jvm_args(
    runtime: &JavaRuntime,
    profile: PerformanceProfile,
    memory_mb: u32,
    extra_jvm_args: &str,
) -> Vec<String> {
    let mem = memory_mb.max(1024);
    let xms = match profile {
        PerformanceProfile::Stock => (mem / 2).clamp(512, 2048).min(mem),
        PerformanceProfile::Smooth
        | PerformanceProfile::PeakFps
        | PerformanceProfile::ZeroPause => mem.min(8192),
    };

    let mut args = vec![
        format!("-Xms{xms}M"),
        format!("-Xmx{mem}M"),
        "-Dfile.encoding=UTF-8".to_string(),
    ];

    if current_mojang_os() == MojangOs::Osx {
        args.push("-XstartOnFirstThread".to_string());
    }

    if profile != PerformanceProfile::Stock {
        args.extend([
            "-XX:+UnlockExperimentalVMOptions".to_string(),
            "-XX:+DisableExplicitGC".to_string(),
            "-XX:+AlwaysPreTouch".to_string(),
            "-XX:+PerfDisableSharedMem".to_string(),
        ]);
        #[cfg(target_os = "linux")]
        args.push("-XX:+UseTransparentHugePages".to_string());
    }

    let use_compact_headers = runtime.major >= 25 && runtime.supports_compact_headers;

    match profile {
        PerformanceProfile::Stock => {}
        PerformanceProfile::Smooth => {
            append_tuned_g1(&mut args, mem, 30, 30, 40);
            if use_compact_headers {
                args.push("-XX:+UseCompactObjectHeaders".to_string());
            }
        }
        PerformanceProfile::PeakFps => {
            append_tuned_g1(&mut args, mem, 45, 35, 45);
            if use_compact_headers {
                args.push("-XX:+UseCompactObjectHeaders".to_string());
            }
        }
        PerformanceProfile::ZeroPause => {
            if runtime.major >= 21 && runtime.supports_gen_zgc {
                args.push("-XX:+UseZGC".to_string());
                if runtime.major < 24 {
                    args.push("-XX:+ZGenerational".to_string());
                }
                args.push("-XX:-ZUncommit".to_string());
            } else if runtime.supports_shenandoah {
                args.push("-XX:+UseShenandoahGC".to_string());
                if runtime.supports_gen_shenandoah {
                    args.push("-XX:ShenandoahGCMode=generational".to_string());
                }
            } else {
                append_tuned_g1(&mut args, mem, 25, 30, 40);
            }
            if use_compact_headers {
                args.push("-XX:+UseCompactObjectHeaders".to_string());
            }
        }
    }

    for token in split_shell_args(extra_jvm_args) {
        args.push(token);
    }
    args
}

fn append_tuned_g1(
    args: &mut Vec<String>,
    memory_mb: u32,
    max_gc_pause_ms: u32,
    new_size_pct: u32,
    max_new_size_pct: u32,
) {
    let region_mb = if memory_mb >= 8192 { 16 } else { 8 };
    args.extend([
        "-XX:+UseG1GC".to_string(),
        "-XX:+ParallelRefProcEnabled".to_string(),
        format!("-XX:MaxGCPauseMillis={max_gc_pause_ms}"),
        format!("-XX:G1NewSizePercent={new_size_pct}"),
        format!("-XX:G1MaxNewSizePercent={max_new_size_pct}"),
        format!("-XX:G1HeapRegionSize={region_mb}M"),
        "-XX:G1ReservePercent=15".to_string(),
        "-XX:InitiatingHeapOccupancyPercent=20".to_string(),
        "-XX:G1MixedGCCountTarget=4".to_string(),
        "-XX:SoftRefLRUPolicyMSPerMB=50".to_string(),
    ]);
}

pub fn split_shell_args(raw: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut quote: Option<char> = None;
    for ch in raw.chars() {
        match quote {
            Some(q) if ch == q => quote = None,
            Some(_) => cur.push(ch),
            None if ch == '"' || ch == '\'' => quote = Some(ch),
            None if ch.is_whitespace() => {
                if !cur.is_empty() {
                    out.push(std::mem::take(&mut cur));
                }
            }
            None => cur.push(ch),
        }
    }
    if !cur.is_empty() {
        out.push(cur);
    }
    out
}

#[derive(Debug, Deserialize)]
struct AdoptiumAsset {
    binary: AdoptiumBinary,
}

#[derive(Debug, Deserialize)]
struct AdoptiumBinary {
    package: AdoptiumPackage,
}

#[derive(Debug, Deserialize)]
struct AdoptiumPackage {
    link: String,
    name: String,
    checksum: Option<String>,
    size: Option<u64>,
}

pub async fn install_java_runtime<F>(
    net: &NetClient,
    runtimes_dir: &Path,
    major: u32,
    cancel: &CancellationToken,
    on_progress: F,
) -> Result<JavaRuntime, JavaError>
where
    F: Fn(u64, u64) + Send + Sync + 'static,
{
    let os = current_mojang_os().as_adoptium_str();
    let arch = current_arch().as_adoptium_str();
    let image_type = if major >= 17 { "jre" } else { "jdk" };

    let api_url = format!(
        "https://api.adoptium.net/v3/assets/latest/{major}/hotspot?architecture={arch}&image_type={image_type}&os={os}&vendor=eclipse"
    );

    let mut assets: Vec<AdoptiumAsset> = net.fetch_json(&api_url, cancel).await.unwrap_or_default();
    if assets.is_empty() && image_type == "jre" {
        let jdk_url = format!(
            "https://api.adoptium.net/v3/assets/latest/{major}/hotspot?architecture={arch}&image_type=jdk&os={os}&vendor=eclipse"
        );
        assets = net.fetch_json(&jdk_url, cancel).await?;
    }

    let pkg = assets
        .into_iter()
        .next()
        .map(|a| a.binary.package)
        .ok_or_else(|| JavaError::NoRelease {
            major,
            os: os.to_string(),
            arch: arch.to_string(),
        })?;

    fs::create_dir_all(runtimes_dir)?;
    let archive_path = runtimes_dir.join(&pkg.name);
    let total = pkg.size.unwrap_or(0);
    let mut downloaded = 0u64;

    let item = DownloadItem {
        urls: vec![pkg.link],
        dest: archive_path.clone(),
        expected_hash: pkg.checksum.map(ExpectedHash::Sha256),
        expected_size: pkg.size,
        label: pkg.name.clone(),
    };

    net.download_file(&item, cancel, |delta| {
        downloaded = downloaded.saturating_add(delta);
        on_progress(downloaded, total.max(downloaded));
    })
    .await?;

    let target_dir = runtimes_dir.join(format!("java-{major}"));
    if target_dir.exists() {
        let _ = fs::remove_dir_all(&target_dir);
    }
    fs::create_dir_all(&target_dir)?;

    let archive_clone = archive_path.clone();
    let target_clone = target_dir.clone();
    tokio::task::spawn_blocking(move || extract_archive_strip_root(&archive_clone, &target_clone))
        .await
        .map_err(|e| JavaError::Extract(e.to_string()))??;

    let _ = fs::remove_file(&archive_path);

    let java_exe = find_java_executable_in(&target_dir)
        .ok_or_else(|| JavaError::Extract("java executable missing after extraction".into()))?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(&java_exe, fs::Permissions::from_mode(0o755));
    }

    probe_java_runtime(&java_exe)
        .ok_or_else(|| JavaError::Extract("installed Java runtime failed version probe".into()))
}

fn extract_archive_strip_root(archive_path: &Path, dest_dir: &Path) -> Result<(), JavaError> {
    let name = archive_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();

    if name.ends_with(".zip") {
        let file = fs::File::open(archive_path)?;
        let mut zip = zip::ZipArchive::new(file).map_err(|e| JavaError::Extract(e.to_string()))?;
        for idx in 0..zip.len() {
            let mut entry = zip
                .by_index(idx)
                .map_err(|e| JavaError::Extract(e.to_string()))?;
            let Some(enclosed) = entry.enclosed_name() else {
                continue;
            };
            let rel = strip_first_component(&enclosed);
            if rel.as_os_str().is_empty() {
                continue;
            }
            let out_path = dest_dir.join(&rel);
            if entry.is_dir() {
                fs::create_dir_all(&out_path)?;
            } else {
                if let Some(parent) = out_path.parent() {
                    fs::create_dir_all(parent)?;
                }
                let mut out = fs::File::create(&out_path)?;
                std::io::copy(&mut entry, &mut out)?;
                #[cfg(unix)]
                if let Some(mode) = entry.unix_mode() {
                    use std::os::unix::fs::PermissionsExt;
                    let _ = fs::set_permissions(&out_path, fs::Permissions::from_mode(mode));
                }
            }
        }
        Ok(())
    } else if name.ends_with(".tar.gz") || name.ends_with(".tgz") {
        let file = fs::File::open(archive_path)?;
        let gz = flate2::read::GzDecoder::new(file);
        let mut tar = tar::Archive::new(gz);
        for entry_res in tar.entries()? {
            let mut entry = entry_res?;
            let path = entry.path()?.into_owned();
            if path
                .components()
                .any(|c| matches!(c, std::path::Component::ParentDir | std::path::Component::RootDir))
            {
                continue;
            }
            let rel = strip_first_component(&path);
            if rel.as_os_str().is_empty() {
                continue;
            }
            let out_path = dest_dir.join(&rel);
            if entry.header().entry_type().is_dir() {
                fs::create_dir_all(&out_path)?;
            } else if entry.header().entry_type().is_file() {
                if let Some(parent) = out_path.parent() {
                    fs::create_dir_all(parent)?;
                }
                entry.unpack(&out_path)?;
            }
        }
        Ok(())
    } else {
        Err(JavaError::Extract(format!(
            "unsupported archive format: {name}"
        )))
    }
}

fn strip_first_component(path: &Path) -> PathBuf {
    let mut comps = path.components();
    comps.next();
    comps.as_path().to_path_buf()
}

fn java_binary_name() -> &'static str {
    #[cfg(target_os = "windows")]
    {
        "javaw.exe"
    }
    #[cfg(not(target_os = "windows"))]
    {
        "java"
    }
}

fn find_java_executable_in(dir: &Path) -> Option<PathBuf> {
    let candidates = [
        dir.join("bin").join(java_binary_name()),
        dir.join("bin").join("java"),
        dir.join("bin").join("java.exe"),
        dir.join("Contents/Home/bin/java"),
    ];
    candidates.into_iter().find(|p| p.is_file())
}

fn collect_managed_runtimes(runtimes_dir: &Path, out: &mut BTreeSet<PathBuf>) {
    let Ok(entries) = fs::read_dir(runtimes_dir) else {
        return;
    };
    for entry in entries.flatten() {
        let p = entry.path();
        if p.is_dir()
            && let Some(exe) = find_java_executable_in(&p)
        {
            out.insert(exe);
        }
    }
}

fn collect_system_runtimes(out: &mut BTreeSet<PathBuf>) {
    if let Ok(java_home) = std::env::var("JAVA_HOME")
        && let Some(exe) = find_java_executable_in(Path::new(&java_home))
    {
        out.insert(exe);
    }

    if let Ok(path_var) = std::env::var("PATH") {
        for dir in std::env::split_paths(&path_var) {
            let exe = dir.join(java_binary_name());
            if exe.is_file() {
                out.insert(exe);
            }
        }
    }

    #[cfg(target_os = "linux")]
    {
        for base in ["/usr/lib/jvm", "/usr/lib64/jvm", "/opt"] {
            if let Ok(entries) = fs::read_dir(base) {
                for entry in entries.flatten() {
                    if let Some(exe) = find_java_executable_in(&entry.path()) {
                        out.insert(exe);
                    }
                }
            }
        }
    }

    #[cfg(target_os = "macos")]
    {
        if let Ok(entries) = fs::read_dir("/Library/Java/JavaVirtualMachines") {
            for entry in entries.flatten() {
                if let Some(exe) = find_java_executable_in(&entry.path()) {
                    out.insert(exe);
                }
            }
        }
    }

    #[cfg(target_os = "windows")]
    {
        for base in [
            "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Java",
            "C:\\Program Files\\Microsoft",
            "C:\\Program Files\\Zulu",
        ] {
            if let Ok(entries) = fs::read_dir(base) {
                for entry in entries.flatten() {
                    if let Some(exe) = find_java_executable_in(&entry.path()) {
                        out.insert(exe);
                    }
                }
            }
        }
    }
}

pub fn probe_java_runtime(exe: &Path) -> Option<JavaRuntime> {
    let probe_exe = {
        #[cfg(target_os = "windows")]
        {
            let java_console = exe.with_file_name("java.exe");
            if java_console.is_file() {
                java_console
            } else {
                exe.to_path_buf()
            }
        }
        #[cfg(not(target_os = "windows"))]
        {
            exe.to_path_buf()
        }
    };

    let mut cmd = Command::new(&probe_exe);
    configure_background_command(&mut cmd);
    let output = cmd.arg("-version").output().ok()?;
    let combined = format!(
        "{}\n{}",
        String::from_utf8_lossy(&output.stderr),
        String::from_utf8_lossy(&output.stdout)
    );
    let (major, version_label) = parse_java_version_output(&combined)?;

    let supports_shenandoah =
        major >= 17 && probe_flag(&probe_exe, &["-XX:+UseShenandoahGC", "-version"]);
    let supports_gen_shenandoah = major >= 24
        && supports_shenandoah
        && probe_flag(
            &probe_exe,
            &[
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseShenandoahGC",
                "-XX:ShenandoahGCMode=generational",
                "-version",
            ],
        );
    let supports_gen_zgc = major >= 21
        && probe_flag(
            &probe_exe,
            if major >= 24 {
                &["-XX:+UseZGC", "-XX:-ZUncommit", "-version"]
            } else {
                &["-XX:+UseZGC", "-XX:+ZGenerational", "-XX:-ZUncommit", "-version"]
            },
        );
    let supports_compact_headers = major >= 25
        && probe_flag(&probe_exe, &["-XX:+UseCompactObjectHeaders", "-version"]);

    Some(JavaRuntime {
        path: exe.to_path_buf(),
        major,
        version_label,
        supports_shenandoah,
        supports_gen_shenandoah,
        supports_gen_zgc,
        supports_compact_headers,
    })
}

fn probe_flag(exe: &Path, args: &[&str]) -> bool {
    let mut cmd = Command::new(exe);
    configure_background_command(&mut cmd);
    cmd.args(args)
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}

pub fn parse_java_version_output(text: &str) -> Option<(u32, String)> {
    for line in text.lines() {
        let Some(start) = line.find('"') else {
            continue;
        };
        let Some(end) = line[start + 1..].find('"') else {
            continue;
        };
        let ver = &line[start + 1..start + 1 + end];
        let mut parts = ver.split('.');
        let Some(first) = parts.next().and_then(|p| p.parse::<u32>().ok()) else {
            continue;
        };
        let major = if first == 1 {
            let Some(second) = parts.next().and_then(|p| p.parse::<u32>().ok()) else {
                continue;
            };
            second
        } else {
            first
        };
        return Some((major, format!("Java {major} ({ver})")));
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    fn runtime_fixture(
        major: u32,
        shenandoah: bool,
        gen_shenandoah: bool,
        gen_zgc: bool,
        compact_headers: bool,
    ) -> JavaRuntime {
        JavaRuntime {
            path: PathBuf::from(format!("/usr/lib/jvm/java-{major}/bin/java")),
            major,
            version_label: format!("Java {major}"),
            supports_shenandoah: shenandoah,
            supports_gen_shenandoah: gen_shenandoah,
            supports_gen_zgc: gen_zgc,
            supports_compact_headers: compact_headers,
        }
    }

    fn has(args: &[String], flag: &str) -> bool {
        args.iter().any(|a| a == flag)
    }

    fn count_prefix(args: &[String], prefix: &str) -> usize {
        args.iter().filter(|a| a.starts_with(prefix)).count()
    }

    #[test]
    fn parses_legacy_and_modern_java_versions() {
        assert_eq!(
            parse_java_version_output("openjdk version \"1.8.0_402\""),
            Some((8, "Java 8 (1.8.0_402)".into()))
        );
        assert_eq!(
            parse_java_version_output("openjdk version \"21.0.5\" 2024-10-15"),
            Some((21, "Java 21 (21.0.5)".into()))
        );
        assert_eq!(
            parse_java_version_output("openjdk version \"25.0.1\""),
            Some((25, "Java 25 (25.0.1)".into()))
        );
        assert_eq!(
            parse_java_version_output(
                "Picked up JAVA_TOOL_OPTIONS: \"-Dfile.encoding=UTF-8\"\nopenjdk version \"21.0.5\" 2024-10-15"
            ),
            Some((21, "Java 21 (21.0.5)".into()))
        );
    }

    #[test]
    fn stock_profile_emits_minimal_flags_only() {
        let rt25 = runtime_fixture(25, true, true, true, true);
        let args = build_jvm_args(&rt25, PerformanceProfile::Stock, 4096, "-Dfoo=bar");
        assert!(has(&args, "-Xms2048M"));
        assert!(has(&args, "-Xmx4096M"));
        assert!(has(&args, "-Dfile.encoding=UTF-8"));
        assert!(has(&args, "-Dfoo=bar"));
        assert!(!has(&args, "-XX:+UnlockExperimentalVMOptions"));
        assert!(!has(&args, "-XX:+AlwaysPreTouch"));
        assert!(!has(&args, "-XX:+UseG1GC"));
        assert!(!has(&args, "-XX:+UseZGC"));
        assert!(!has(&args, "-XX:+UseCompactObjectHeaders"));
    }

    #[test]
    fn tuned_profiles_match_xms_to_xmx_up_to_8gb() {
        let rt25 = runtime_fixture(25, true, true, true, true);
        for profile in [
            PerformanceProfile::Smooth,
            PerformanceProfile::PeakFps,
            PerformanceProfile::ZeroPause,
        ] {
            let args_4g = build_jvm_args(&rt25, profile, 4096, "");
            assert!(has(&args_4g, "-Xms4096M"));
            assert!(has(&args_4g, "-Xmx4096M"));

            let args_6g = build_jvm_args(&rt25, profile, 6144, "");
            assert!(has(&args_6g, "-Xms6144M"));
            assert!(has(&args_6g, "-Xmx6144M"));

            let args_8g = build_jvm_args(&rt25, profile, 8192, "");
            assert!(has(&args_8g, "-Xms8192M"));
            assert!(has(&args_8g, "-Xmx8192M"));

            let args_12g = build_jvm_args(&rt25, profile, 12288, "");
            assert!(has(&args_12g, "-Xms8192M"));
            assert!(has(&args_12g, "-Xmx12288M"));
        }
    }

    #[test]
    fn smooth_profile_uses_tuned_g1_across_java_17_21_25() {
        let rt17 = runtime_fixture(17, true, false, false, false);
        let rt21 = runtime_fixture(21, true, false, true, false);
        let rt25 = runtime_fixture(25, true, true, true, true);

        for rt in [&rt17, &rt21, &rt25] {
            let args = build_jvm_args(rt, PerformanceProfile::Smooth, 4096, "-Dfoo=bar");
            assert!(has(&args, "-Xms4096M"));
            assert!(has(&args, "-Xmx4096M"));
            assert!(has(&args, "-XX:+UnlockExperimentalVMOptions"));
            assert!(has(&args, "-XX:+DisableExplicitGC"));
            assert!(has(&args, "-XX:+AlwaysPreTouch"));
            assert!(has(&args, "-XX:+PerfDisableSharedMem"));
            #[cfg(target_os = "linux")]
            assert!(has(&args, "-XX:+UseTransparentHugePages"));
            assert!(has(&args, "-XX:+UseG1GC"));
            assert!(has(&args, "-XX:+ParallelRefProcEnabled"));
            assert!(has(&args, "-XX:MaxGCPauseMillis=30"));
            assert!(has(&args, "-XX:G1NewSizePercent=30"));
            assert!(has(&args, "-XX:G1MaxNewSizePercent=40"));
            assert!(has(&args, "-XX:G1HeapRegionSize=8M"));
            assert!(has(&args, "-XX:G1ReservePercent=15"));
            assert!(has(&args, "-XX:InitiatingHeapOccupancyPercent=20"));
            assert!(has(&args, "-XX:G1MixedGCCountTarget=4"));
            assert!(has(&args, "-XX:SoftRefLRUPolicyMSPerMB=50"));
            assert!(!has(&args, "-XX:+UseShenandoahGC"));
            assert!(!has(&args, "-XX:+UseZGC"));
            assert!(has(&args, "-Dfoo=bar"));
            assert_eq!(count_prefix(&args, "-XX:MaxGCPauseMillis="), 1);

            if rt.major >= 25 && rt.supports_compact_headers {
                assert!(has(&args, "-XX:+UseCompactObjectHeaders"));
            } else {
                assert!(!has(&args, "-XX:+UseCompactObjectHeaders"));
            }
        }

        let args_8g = build_jvm_args(&rt25, PerformanceProfile::Smooth, 8192, "");
        assert!(has(&args_8g, "-XX:G1HeapRegionSize=16M"));
    }

    #[test]
    fn peak_fps_profile_uses_throughput_tuned_g1() {
        let rt17 = runtime_fixture(17, false, false, false, false);
        let rt21 = runtime_fixture(21, true, false, true, false);
        let rt25 = runtime_fixture(25, true, true, true, true);

        for rt in [&rt17, &rt21, &rt25] {
            let args = build_jvm_args(rt, PerformanceProfile::PeakFps, 6144, "");
            assert!(has(&args, "-Xms6144M"));
            assert!(has(&args, "-Xmx6144M"));
            assert!(has(&args, "-XX:+UseG1GC"));
            assert!(has(&args, "-XX:MaxGCPauseMillis=45"));
            assert!(has(&args, "-XX:G1NewSizePercent=35"));
            assert!(has(&args, "-XX:G1MaxNewSizePercent=45"));
            assert!(has(&args, "-XX:G1HeapRegionSize=8M"));
            assert!(has(&args, "-XX:InitiatingHeapOccupancyPercent=20"));
            assert!(has(&args, "-XX:G1MixedGCCountTarget=4"));
            assert!(has(&args, "-XX:SoftRefLRUPolicyMSPerMB=50"));
            assert_eq!(
                has(&args, "-XX:+UseCompactObjectHeaders"),
                rt.major >= 25 && rt.supports_compact_headers
            );
        }
    }

    #[test]
    fn zero_pause_profile_selects_gen_zgc_and_fallbacks_cleanly() {
        let rt25 = runtime_fixture(25, true, true, true, true);
        let args25 = build_jvm_args(&rt25, PerformanceProfile::ZeroPause, 6144, "");
        assert!(has(&args25, "-Xms6144M"));
        assert!(has(&args25, "-Xmx6144M"));
        assert!(has(&args25, "-XX:+DisableExplicitGC"));
        assert!(has(&args25, "-XX:+AlwaysPreTouch"));
        assert!(has(&args25, "-XX:+PerfDisableSharedMem"));
        #[cfg(target_os = "linux")]
        assert!(has(&args25, "-XX:+UseTransparentHugePages"));
        assert!(has(&args25, "-XX:+UseZGC"));
        assert!(!has(&args25, "-XX:+ZGenerational"));
        assert!(has(&args25, "-XX:-ZUncommit"));
        assert!(has(&args25, "-XX:+UseCompactObjectHeaders"));

        let rt21 = runtime_fixture(21, true, false, true, false);
        let args21 = build_jvm_args(&rt21, PerformanceProfile::ZeroPause, 6144, "");
        assert!(has(&args21, "-XX:+UseZGC"));
        assert!(has(&args21, "-XX:+ZGenerational"));
        assert!(has(&args21, "-XX:-ZUncommit"));
        assert!(!has(&args21, "-XX:+UseCompactObjectHeaders"));

        let rt17_sh = runtime_fixture(17, true, false, false, false);
        let args17_sh = build_jvm_args(&rt17_sh, PerformanceProfile::ZeroPause, 4096, "");
        assert!(has(&args17_sh, "-XX:+UseShenandoahGC"));
        assert!(!has(&args17_sh, "-XX:+UseZGC"));
        assert!(!has(&args17_sh, "-XX:+UseCompactObjectHeaders"));

        let rt17_g1 = runtime_fixture(17, false, false, false, false);
        let args17_g1 = build_jvm_args(&rt17_g1, PerformanceProfile::ZeroPause, 4096, "");
        assert!(has(&args17_g1, "-XX:+UseG1GC"));
        assert!(has(&args17_g1, "-XX:MaxGCPauseMillis=25"));
        assert_eq!(count_prefix(&args17_g1, "-XX:MaxGCPauseMillis="), 1);
        assert!(!has(&args17_g1, "-XX:+UseCompactObjectHeaders"));
    }

    #[test]
    fn compact_headers_gated_to_java_25_and_supported_flag() {
        let rt24_flag_true = runtime_fixture(24, true, true, true, true);
        let rt25_flag_false = runtime_fixture(25, true, true, true, false);
        for profile in [
            PerformanceProfile::Smooth,
            PerformanceProfile::PeakFps,
            PerformanceProfile::ZeroPause,
        ] {
            assert!(!has(
                &build_jvm_args(&rt24_flag_true, profile, 4096, ""),
                "-XX:+UseCompactObjectHeaders"
            ));
            assert!(!has(
                &build_jvm_args(&rt25_flag_false, profile, 4096, ""),
                "-XX:+UseCompactObjectHeaders"
            ));
        }
    }

    #[test]
    fn memory_advisory_flags_low_ram_for_zero_pause() {
        assert_eq!(
            evaluate_profile_memory_warning(PerformanceProfile::ZeroPause, 4096),
            Some(MemoryWarning::LowHeapForZeroPause {
                requested_mb: 4096,
                recommended_mb: 6144,
            })
        );
        assert_eq!(
            evaluate_profile_memory_warning(PerformanceProfile::ZeroPause, 6144),
            None
        );
        assert_eq!(
            evaluate_profile_memory_warning(PerformanceProfile::Smooth, 4096),
            None
        );
        assert_eq!(
            evaluate_memory_for_profile(4096, 16384, PerformanceProfile::ZeroPause).warning,
            Some(MemoryWarning::LowHeapForZeroPause {
                requested_mb: 4096,
                recommended_mb: 6144,
            })
        );
        assert_eq!(evaluate_memory_mb(4096, 16384).warning, None);
    }

    #[test]
    fn real_installed_jdks_accept_generated_flags_cleanly() {
        let candidates = [
            "/usr/lib/jvm/java-17-openjdk/bin/java",
            "/usr/lib/jvm/java-21-openjdk/bin/java",
            "/usr/lib/jvm/java-25-openjdk/bin/java",
        ];
        for path in candidates {
            let p = Path::new(path);
            if !p.is_file() {
                continue;
            }
            let rt = probe_java_runtime(p).expect("installed JDK should probe cleanly");
            for profile in PerformanceProfile::ALL {
                let mut args = build_jvm_args(&rt, profile, 1024, "");
                for arg in &mut args {
                    if arg.starts_with("-Xms") {
                        *arg = "-Xms256M".to_string();
                    } else if arg.starts_with("-Xmx") {
                        *arg = "-Xmx256M".to_string();
                    }
                }
                args.push("-version".to_string());
                let output = Command::new(&rt.path)
                    .args(&args)
                    .output()
                    .expect("failed to execute java");
                assert!(
                    output.status.success(),
                    "JDK {} failed for {profile:?} with args {args:?}:\nstderr: {}",
                    rt.version_label,
                    String::from_utf8_lossy(&output.stderr)
                );
            }
        }
    }
}
