use crate::config::LauncherConfig;
use crate::crash::{CrashDiagnosis, analyze_crash, has_fresh_crash_report};
use crate::instance::{InstanceRecord, LoaderKind};
use crate::loaders::resolve_loader_profile;
use crate::mojang::{
    AssetIndexFile, ensure_vanilla_profile, expand_profile_arguments, extract_native_jar,
    required_java_for_profile, resolve_libraries,
};
use crate::redact::redact_log_line;
use chrono::Utc;
use nl_auth::{Account, resolve_launch_credentials};
use nl_java::{
    JavaRuntime, build_jvm_args, install_java_runtime, probe_java_runtime, scan_java_runtimes,
    select_best_runtime, split_shell_args,
};
use nl_net::{DownloadItem, ExpectedHash, NetClient, verify_file_sync};
use nl_night::{
    InjectionPlan, JarResolveRequest, NightClientManifest, VisualsCompatibility,
    cleanup_managed_mod, detect_duplicate_nightclient_jars, evaluate_compatibility, plan_injection,
    resolve_or_download_jar, sync_after_exit, sync_before_launch,
};
use nl_platform::{
    LaunchEnvironment, LauncherPaths, SecretStore, configure_game_command, current_mojang_os,
    terminate_process,
};
use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::Write;
use std::path::PathBuf;
use std::process::Stdio;
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

const SHUTDOWN_QUIET_GRACE: Duration = Duration::from_secs(5);
const SHUTDOWN_HARD_CEILING: Duration = Duration::from_secs(20);

#[derive(Debug, Clone)]
pub enum LaunchStage {
    Authenticating { username: String },
    CheckingJava,
    ResolvingProfile { mc_version: String, loader: LoaderKind },
    InstallingJava { major: u32, downloaded_mb: f32, total_mb: f32 },
    VerifyingFiles,
    DownloadingFiles { completed: usize, total: usize, mb_per_sec: f32, current: String },
    PreparingNightClient,
    StartingJvm { runtime_label: String },
}

#[derive(Debug, Clone, Default)]
pub struct LaunchOptions {
    pub quick_play_server: Option<String>,
    pub active_nc_theme_path: Option<PathBuf>,
    pub repair_files: bool,
    pub repair_only: bool,
}

#[derive(Debug, Clone)]
pub struct LaunchRequest {
    pub instance: InstanceRecord,
    pub account: Account,
    pub config: LauncherConfig,
    pub night_manifest: NightClientManifest,
    pub options: LaunchOptions,
}

#[derive(Debug, Clone)]
pub enum LaunchEvent {
    Progress {
        instance_id: String,
        stage: LaunchStage,
        progress: f32,
    },
    LogLine {
        instance_id: String,
        line: String,
    },
    RepairCompleted {
        instance_id: String,
        repaired_files: usize,
    },
    Started {
        instance_id: String,
        pid: u32,
    },
    Exited {
        instance_id: String,
        exit_code: Option<i32>,
        playtime_secs: u64,
        diagnosis: Option<CrashDiagnosis>,
    },
    Failed {
        instance_id: String,
        message: String,
    },
}

pub async fn prepare_and_launch_instance(
    paths: LauncherPaths,
    net: NetClient,
    mut req: LaunchRequest,
    cancel: CancellationToken,
    user_stopped: Arc<std::sync::atomic::AtomicBool>,
    tx: mpsc::UnboundedSender<LaunchEvent>,
) {
    let instance_id = req.instance.config.id.clone();
    let emit = |stage: LaunchStage, progress: f32| {
        let _ = tx.send(LaunchEvent::Progress {
            instance_id: instance_id.clone(),
            stage,
            progress: progress.clamp(0.0, 1.0),
        });
    };

    if let Err(err) = run_pipeline(&paths, &net, &mut req, &cancel, &user_stopped, &tx, &emit).await
        && !cancel.is_cancelled()
    {
        let _ = tx.send(LaunchEvent::Failed {
            instance_id,
            message: err,
        });
    }
}

async fn run_pipeline(
    paths: &LauncherPaths,
    net: &NetClient,
    req: &mut LaunchRequest,
    cancel: &CancellationToken,
    user_stopped: &Arc<std::sync::atomic::AtomicBool>,
    tx: &mpsc::UnboundedSender<LaunchEvent>,
    emit: &impl Fn(LaunchStage, f32),
) -> Result<(), String> {
    let instance_id = req.instance.config.id.clone();
    let instance_dir = req.instance.dir.clone();
    paths.ensure_all().map_err(|e| e.to_string())?;
    if !instance_dir.is_dir() {
        return Err("Instance folder no longer exists".to_string());
    }

    let creds = if req.options.repair_only {
        None
    } else {
        emit(
            LaunchStage::Authenticating {
                username: req.account.username.clone(),
            },
            0.04,
        );
        let secrets = SecretStore::new(paths.vault_file());
        Some(
            resolve_launch_credentials(
                net,
                &secrets,
                &mut req.account,
                req.config.ms_client_id.as_deref(),
                cancel,
            )
            .await
            .map_err(|e| e.to_string())?,
        )
    };

    emit(LaunchStage::CheckingJava, 0.08);
    let mut runtimes = scan_java_runtimes(&paths.runtimes_dir(), &paths.java_probes_file());

    emit(
        LaunchStage::ResolvingProfile {
            mc_version: req.instance.config.mc_version.clone(),
            loader: req.instance.config.loader,
        },
        0.10,
    );
    let vanilla_profile =
        ensure_vanilla_profile(net, paths, &req.instance.config.mc_version, cancel)
            .await
            .map_err(|e| e.to_string())?;
    let required_java = required_java_for_profile(&vanilla_profile);

    let chosen_runtime = if let Some(override_path) = req
        .instance
        .config
        .java_path_override
        .as_deref()
        .filter(|p| !p.trim().is_empty())
        .or_else(|| {
            req.config
                .default_java_path
                .as_deref()
                .filter(|p| !p.trim().is_empty())
        }) {
        resolve_override_runtime(override_path, &runtimes, required_java)
    } else if let Some(rt) = select_best_runtime(&runtimes, required_java) {
        rt.clone()
    } else {
        emit(
            LaunchStage::InstallingJava {
                major: required_java,
                downloaded_mb: 0.0,
                total_mb: 0.0,
            },
            0.14,
        );
        let tx_java = tx.clone();
        let iid = instance_id.clone();
        let installed = install_java_runtime(
            net,
            &paths.runtimes_dir(),
            required_java,
            cancel,
            move |done, total| {
                let frac = if total > 0 {
                    done as f32 / total as f32
                } else {
                    0.0
                };
                let _ = tx_java.send(LaunchEvent::Progress {
                    instance_id: iid.clone(),
                    stage: LaunchStage::InstallingJava {
                        major: required_java,
                        downloaded_mb: done as f32 / 1_048_576.0,
                        total_mb: total as f32 / 1_048_576.0,
                    },
                    progress: 0.14 + frac * 0.12,
                });
            },
        )
        .await
        .map_err(|e| e.to_string())?;
        runtimes.push(installed.clone());
        installed
    };

    emit(
        LaunchStage::ResolvingProfile {
            mc_version: req.instance.config.mc_version.clone(),
            loader: req.instance.config.loader,
        },
        0.28,
    );
    let (profile, resolved_loader_ver) = resolve_loader_profile(
        net,
        paths,
        &req.instance.config.mc_version,
        req.instance.config.loader,
        req.instance.config.loader_version.as_deref(),
        Some(chosen_runtime.path.as_path()),
        cancel,
    )
    .await
    .map_err(|e| e.to_string())?;

    emit(LaunchStage::VerifyingFiles, 0.32);
    let mut download_queue: Vec<DownloadItem> = Vec::new();

    let mc_ver = &req.instance.config.mc_version;
    let client_jar = paths
        .versions_dir()
        .join(mc_ver)
        .join(format!("{mc_ver}.jar"));
    if let Some(ref dl) = profile.downloads
        && let Some(ref client) = dl.client
    {
        let expected_hash = client.sha1.clone().map(ExpectedHash::Sha1);
        if req.options.repair_files
            || !verify_file_sync(&client_jar, expected_hash.as_ref(), client.size)
        {
            download_queue.push(DownloadItem {
                urls: vec![client.url.clone()],
                dest: client_jar.clone(),
                expected_hash,
                expected_size: client.size,
                label: format!("client-{mc_ver}.jar"),
            });
        }
    }

    let resolved_libs = resolve_libraries(&profile, &paths.libraries_dir());
    for lib in &resolved_libs {
        if let Some(ref dl) = lib.download
            && (req.options.repair_files
                || !verify_file_sync(&dl.dest, dl.expected_hash.as_ref(), dl.expected_size))
        {
            download_queue.push(dl.clone());
        }
    }

    let mut asset_index_id = profile
        .assets
        .clone()
        .unwrap_or_else(|| mc_ver.to_string());
    if let Some(ref idx) = profile.asset_index {
        asset_index_id = idx.id.clone();
        let idx_dest = paths
            .assets_dir()
            .join("indexes")
            .join(format!("{}.json", idx.id));
        let idx_item = DownloadItem {
            urls: vec![idx.url.clone()],
            dest: idx_dest.clone(),
            expected_hash: idx.sha1.clone().map(ExpectedHash::Sha1),
            expected_size: idx.size,
            label: format!("assets-{}.json", idx.id),
        };
        net.download_file(&idx_item, cancel, |_| {})
            .await
            .map_err(|e| e.to_string())?;

        if let Ok(raw) = fs::read(&idx_dest)
            && let Ok(parsed) = serde_json::from_slice::<AssetIndexFile>(&raw)
        {
            let objects_dir = paths.assets_dir().join("objects");
            for obj in parsed.objects.values() {
                if obj.hash.len() < 2 {
                    continue;
                }
                let prefix = &obj.hash[..2];
                let dest = objects_dir.join(prefix).join(&obj.hash);
                let expected = ExpectedHash::Sha1(obj.hash.clone());
                let needs_dl = if req.options.repair_files {
                    !verify_file_sync(&dest, Some(&expected), Some(obj.size))
                } else {
                    !dest.is_file()
                };
                if needs_dl {
                    download_queue.push(DownloadItem {
                        urls: vec![format!(
                            "https://resources.download.minecraft.net/{prefix}/{}",
                            obj.hash
                        )],
                        dest,
                        expected_hash: Some(expected),
                        expected_size: Some(obj.size),
                        label: obj.hash[..8].to_string(),
                    });
                }
            }
        }
    }

    let mut seen_dests: HashSet<PathBuf> = HashSet::with_capacity(download_queue.len());
    download_queue.retain(|item| seen_dests.insert(item.dest.clone()));

    let repaired_count = download_queue.len();
    if !download_queue.is_empty() {
        let tx_dl = tx.clone();
        let iid = instance_id.clone();
        net.download_batch(download_queue, cancel, move |p| {
            let frac = if p.total_files > 0 {
                p.completed_files as f32 / p.total_files as f32
            } else {
                1.0
            };
            let _ = tx_dl.send(LaunchEvent::Progress {
                instance_id: iid.clone(),
                stage: LaunchStage::DownloadingFiles {
                    completed: p.completed_files,
                    total: p.total_files,
                    mb_per_sec: p.bytes_per_sec as f32 / 1_048_576.0,
                    current: p.current_label,
                },
                progress: 0.34 + frac * 0.50,
            });
        })
        .await
        .map_err(|e| e.to_string())?;
    }

    let natives_dir = paths.versions_dir().join(&profile.id).join("natives");
    fs::create_dir_all(&natives_dir).map_err(|e| e.to_string())?;
    for lib in &resolved_libs {
        if lib.extract_to_natives && lib.local_path.is_file() {
            let _ = extract_native_jar(&lib.local_path, &natives_dir);
        }
    }

    if req.options.repair_only {
        let _ = tx.send(LaunchEvent::RepairCompleted {
            instance_id,
            repaired_files: repaired_count,
        });
        return Ok(());
    }

    let creds = creds.ok_or_else(|| "missing account credentials".to_string())?;

    let mut nightclient_jvm_flag: Option<String> = None;
    if req.instance.config.nightclient_enabled {
        emit(LaunchStage::PreparingNightClient, 0.88);
        let compat = evaluate_compatibility(
            &req.night_manifest,
            req.config.nightclient_channel,
            mc_ver,
            req.instance.config.loader.id(),
            resolved_loader_ver
                .as_deref()
                .or(req.instance.config.loader_version.as_deref()),
        );
        match compat {
            VisualsCompatibility::Ready => {
                let duplicates = detect_duplicate_nightclient_jars(&instance_dir);
                if !duplicates.is_empty() {
                    return Err(format!(
                        "Duplicate NightClient JAR detected in mods/: {}",
                        duplicates[0].file_name().unwrap_or_default().to_string_lossy()
                    ));
                }

                let local_override = std::env::var("NIGHTLAUNCHER_LOCAL_JAR")
                    .ok()
                    .filter(|s| !s.trim().is_empty())
                    .map(PathBuf::from);
                let jar_path = resolve_or_download_jar(
                    net,
                    paths,
                    &JarResolveRequest {
                        manifest: &req.night_manifest,
                        channel: req.config.nightclient_channel,
                        mc_version: mc_ver,
                        local_override: local_override.as_deref(),
                        public_key_hex: None,
                    },
                    cancel,
                )
                .await
                .map_err(|e| e.to_string())?;

                match plan_injection(&jar_path, &instance_dir).map_err(|e| e.to_string())? {
                    InjectionPlan::FabricAddModsFlag(flag) => nightclient_jvm_flag = Some(flag),
                    InjectionPlan::ManagedModCopy(_) => {}
                }

                if req.instance.config.nightclient_shared_profile {
                    let _ = sync_before_launch(
                        &paths.nightclient_shared_config_dir(),
                        &instance_dir,
                        req.options.active_nc_theme_path.as_deref(),
                    );
                }
            }
            other => {
                return Err(format!(
                    "NightClient visuals incompatible with this instance: {other:?}"
                ));
            }
        }
    } else {
        cleanup_managed_mod(&instance_dir);
    }

    emit(
        LaunchStage::StartingJvm {
            runtime_label: chosen_runtime.version_label.clone(),
        },
        0.94,
    );
    let cp_sep = current_mojang_os().classpath_separator();
    let mut cp_entries: Vec<String> = resolved_libs
        .iter()
        .filter(|l| !l.extract_to_natives && l.local_path.is_file())
        .map(|l| l.local_path.to_string_lossy().to_string())
        .collect();
    if client_jar.is_file() {
        cp_entries.push(client_jar.to_string_lossy().to_string());
    }
    let classpath = cp_entries.join(cp_sep);

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", creds.username.clone());
    vars.insert("version_name", profile.id.clone());
    vars.insert("game_directory", instance_dir.to_string_lossy().to_string());
    vars.insert("assets_root", paths.assets_dir().to_string_lossy().to_string());
    vars.insert("assets_index_name", asset_index_id);
    vars.insert("auth_uuid", creds.uuid.clone());
    vars.insert("auth_access_token", creds.access_token.clone());
    vars.insert("user_type", creds.user_type.to_string());
    vars.insert(
        "version_type",
        if req.instance.config.nightclient_enabled {
            "NightClient".to_string()
        } else {
            profile.kind.clone().unwrap_or_else(|| "release".to_string())
        },
    );
    vars.insert("natives_directory", natives_dir.to_string_lossy().to_string());
    vars.insert("launcher_name", "NightClientLauncher".to_string());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").to_string());
    vars.insert("classpath", classpath.clone());
    vars.insert(
        "library_directory",
        paths.libraries_dir().to_string_lossy().to_string(),
    );
    vars.insert("classpath_separator", cp_sep.to_string());

    let (mut profile_jvm_args, mut game_args) = expand_profile_arguments(&profile, &vars);

    if chosen_runtime.major >= 9 && classpath.len() > 8000 {
        let argfile_path = instance_dir.join(".nightclient-cp.args");
        if write_classpath_argfile(&argfile_path, &classpath).is_ok() {
            replace_cp_with_argfile(&mut profile_jvm_args, &argfile_path);
        }
    }

    let memory_mb = req
        .instance
        .config
        .memory_mb_override
        .unwrap_or(req.config.default_memory_mb);
    let mut perf_profile = req
        .instance
        .config
        .performance_profile_override
        .unwrap_or(req.config.performance_profile);
    let turbo_active = req.instance.config.nightclient_enabled
        && (req.instance.config.turbo_mode || req.config.default_turbo_mode);
    if turbo_active && perf_profile == nl_java::PerformanceProfile::Stock {
        perf_profile = if chosen_runtime.major >= 21
            && chosen_runtime.supports_gen_zgc
            && memory_mb >= 6144
        {
            nl_java::PerformanceProfile::ZeroPause
        } else {
            nl_java::PerformanceProfile::Smooth
        };
    }
    let combined_extra_jvm = format!(
        "{} {}",
        req.config.extra_jvm_args, req.instance.config.extra_jvm_args
    );

    let mut final_args =
        build_jvm_args(&chosen_runtime, perf_profile, memory_mb, &combined_extra_jvm);
    if turbo_active {
        final_args.push("-Dnightclient.turbo=true".to_string());
        final_args.push("-Dnightclient.native.enabled=true".to_string());
        if let Some(native_lib) = discover_local_native_lib(paths) {
            final_args.push(format!(
                "-Dnightclient.native.path={}",
                native_lib.to_string_lossy()
            ));
        }
        let _ = tx.send(LaunchEvent::LogLine {
            instance_id: instance_id.clone(),
            line: "[NightClient Turbo] Rust JNI engine armed (GPU instancing + PBO + SIMD Netty codec)".to_string(),
        });
    }
    if let Some(nc_flag) = nightclient_jvm_flag {
        final_args.push(nc_flag);
    }
    final_args.extend(profile_jvm_args);
    final_args.push(
        profile
            .main_class
            .clone()
            .unwrap_or_else(|| "net.minecraft.client.main.Main".to_string()),
    );

    if let Some(ref srv) = req.options.quick_play_server
        && !srv.trim().is_empty()
    {
        append_quick_play_args(&mut game_args, mc_ver, srv.trim());
    }

    let combined_extra_game = format!(
        "{} {}",
        req.config.extra_game_args, req.instance.config.extra_game_args
    );
    game_args.extend(split_shell_args(&combined_extra_game));
    final_args.extend(game_args);

    let env = LaunchEnvironment {
        gpu_preference: req.config.gpu_preference,
        use_gamemode: req.config.use_gamemode,
        use_mangohud: req.config.use_mangohud,
        extra_env: Vec::new(),
    };
    let mut cmd = configure_game_command(
        &chosen_runtime.path.to_string_lossy(),
        &final_args,
        &env,
    );
    cmd.current_dir(&instance_dir)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    let mut child = cmd
        .spawn()
        .map_err(|e| format!("Failed to spawn Java ({}): {e}", chosen_runtime.path.display()))?;
    let pid = child.id().unwrap_or(0);
    let started_sys = SystemTime::now();
    let started_inst = Instant::now();

    let _ = tx.send(LaunchEvent::Started {
        instance_id: instance_id.clone(),
        pid,
    });

    let log_dir = instance_dir.join("logs");
    let _ = fs::create_dir_all(&log_dir);
    let process_log_file = fs::File::create(log_dir.join("launcher-process.log")).ok();
    let shared_log_file = Arc::new(std::sync::Mutex::new(process_log_file));
    let tail_buf = Arc::new(std::sync::Mutex::new(Vec::<String>::new()));
    let stopping_seen = Arc::new(std::sync::atomic::AtomicBool::new(false));
    let watchdog_cleaned = Arc::new(std::sync::atomic::AtomicBool::new(false));
    let last_output_ms = Arc::new(std::sync::atomic::AtomicU64::new(0));

    let spawn_reader = |reader: Option<tokio::process::ChildStdout>,
                        err_reader: Option<tokio::process::ChildStderr>| {
        let tx_l = tx.clone();
        let iid = instance_id.clone();
        let lf = Arc::clone(&shared_log_file);
        let tb = Arc::clone(&tail_buf);
        let stop_flag = Arc::clone(&stopping_seen);
        let last_out = Arc::clone(&last_output_ms);

        tokio::spawn(async move {
            let handle_line = |raw: String| {
                let clean = redact_log_line(&raw);
                if is_client_shutdown_line(&clean) {
                    stop_flag.store(true, std::sync::atomic::Ordering::Relaxed);
                }
                last_out.store(
                    started_inst.elapsed().as_millis() as u64,
                    std::sync::atomic::Ordering::Relaxed,
                );
                if let Ok(mut guard) = lf.lock()
                    && let Some(ref mut f) = *guard
                {
                    let _ = writeln!(f, "{clean}");
                }
                if let Ok(mut buf) = tb.lock() {
                    if buf.len() >= 400 {
                        buf.remove(0);
                    }
                    buf.push(clean.clone());
                }
                let _ = tx_l.send(LaunchEvent::LogLine {
                    instance_id: iid.clone(),
                    line: clean,
                });
            };

            if let Some(out) = reader {
                let mut br = BufReader::new(out);
                let mut raw = Vec::new();
                while br.read_until(b'\n', &mut raw).await.unwrap_or(0) > 0 {
                    while matches!(raw.last(), Some(b'\n' | b'\r')) {
                        raw.pop();
                    }
                    handle_line(String::from_utf8_lossy(&raw).into_owned());
                    raw.clear();
                }
            }
            if let Some(err) = err_reader {
                let mut br = BufReader::new(err);
                let mut raw = Vec::new();
                while br.read_until(b'\n', &mut raw).await.unwrap_or(0) > 0 {
                    while matches!(raw.last(), Some(b'\n' | b'\r')) {
                        raw.pop();
                    }
                    handle_line(String::from_utf8_lossy(&raw).into_owned());
                    raw.clear();
                }
            }
        })
    };

    let t_out = spawn_reader(child.stdout.take(), None);
    let t_err = spawn_reader(None, child.stderr.take());

    let wd_stop = Arc::clone(&stopping_seen);
    let wd_cleaned = Arc::clone(&watchdog_cleaned);
    let wd_last = Arc::clone(&last_output_ms);
    let wd_cancel = CancellationToken::new();
    let wd_cancel_clone = wd_cancel.clone();
    tokio::spawn(async move {
        let mut stop_detected_at: Option<Instant> = None;
        let mut sigterm_sent = false;
        loop {
            tokio::select! {
                _ = wd_cancel_clone.cancelled() => break,
                _ = tokio::time::sleep(Duration::from_millis(500)) => {}
            }
            if wd_stop.load(std::sync::atomic::Ordering::Relaxed) && pid > 0 {
                let first = *stop_detected_at.get_or_insert_with(Instant::now);
                let quiet_ms = started_inst
                    .elapsed()
                    .as_millis()
                    .saturating_sub(wd_last.load(std::sync::atomic::Ordering::Relaxed) as u128);
                if !sigterm_sent && quiet_ms >= SHUTDOWN_QUIET_GRACE.as_millis() {
                    tracing::warn!("Watchdog: JVM pid {pid} hung after Stopping, sending SIGTERM");
                    wd_cleaned.store(true, std::sync::atomic::Ordering::Relaxed);
                    terminate_process(pid, false);
                    sigterm_sent = true;
                } else if first.elapsed() >= SHUTDOWN_HARD_CEILING {
                    tracing::warn!("Watchdog: JVM pid {pid} ignored SIGTERM, sending SIGKILL");
                    wd_cleaned.store(true, std::sync::atomic::Ordering::Relaxed);
                    terminate_process(pid, true);
                    break;
                }
            }
        }
    });

    let status = child.wait().await.map_err(|e| e.to_string())?;
    wd_cancel.cancel();
    let _ = t_out.await;
    let _ = t_err.await;

    let playtime_secs = started_inst.elapsed().as_secs();
    let exit_code = status.code();

    if req.instance.config.nightclient_enabled && req.instance.config.nightclient_shared_profile {
        let _ = sync_after_exit(&paths.nightclient_shared_config_dir(), &instance_dir);
    }

    req.instance.config.last_played_at = Some(Utc::now());
    req.instance.config.playtime_secs = req
        .instance
        .config
        .playtime_secs
        .saturating_add(playtime_secs);
    if req.instance.config.loader != LoaderKind::Vanilla && resolved_loader_ver.is_some() {
        req.instance.config.loader_version = resolved_loader_ver;
    }
    if instance_dir.is_dir() && instance_dir.join(crate::instance::INSTANCE_FILE_NAME).is_file() {
        let _ = crate::instance::save_instance_config(&instance_dir, &req.instance.config);
    }

    let mut clean_shutdown_seen = stopping_seen.load(std::sync::atomic::Ordering::Relaxed);
    if !clean_shutdown_seen {
        let latest_log = instance_dir.join("logs/latest.log");
        if fs::metadata(&latest_log)
            .and_then(|m| m.modified())
            .is_ok_and(|mtime| mtime >= started_sys)
            && let Ok(bytes) = fs::read(&latest_log)
        {
            let slice = if bytes.len() > 8192 {
                &bytes[bytes.len() - 8192..]
            } else {
                &bytes
            };
            let tail = String::from_utf8_lossy(slice);
            if tail.lines().any(is_client_shutdown_line) {
                clean_shutdown_seen = true;
            }
        }
    }

    let was_user_stopped = user_stopped.load(std::sync::atomic::Ordering::Relaxed);
    let was_watchdog = watchdog_cleaned.load(std::sync::atomic::Ordering::Relaxed);
    let fresh_crash = has_fresh_crash_report(&instance_dir, started_sys);

    let diagnosis = if is_normal_game_exit(
        exit_code,
        status.success(),
        was_user_stopped,
        clean_shutdown_seen,
        was_watchdog,
        fresh_crash,
    ) {
        None
    } else {
        let tail_text = tail_buf
            .lock()
            .map(|b| b.join("\n"))
            .unwrap_or_default();
        Some(analyze_crash(&instance_dir, started_sys, &tail_text, exit_code))
    };

    let _ = tx.send(LaunchEvent::Exited {
        instance_id,
        exit_code,
        playtime_secs,
        diagnosis,
    });

    Ok(())
}

fn format_java_argfile_token(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len() + 2);
    out.push('"');
    for ch in raw.chars() {
        if ch == '\\' || ch == '"' {
            out.push('\\');
        }
        out.push(ch);
    }
    out.push('"');
    out
}

fn write_classpath_argfile(path: &std::path::Path, classpath: &str) -> std::io::Result<()> {
    let content = format!("-cp\n{}\n", format_java_argfile_token(classpath));
    fs::write(path, content)
}

fn replace_cp_with_argfile(jvm_args: &mut Vec<String>, argfile_path: &std::path::Path) {
    let mut i = 0;
    while i + 1 < jvm_args.len() {
        if jvm_args[i] == "-cp" || jvm_args[i] == "-classpath" {
            jvm_args[i] = format!("@{}", argfile_path.to_string_lossy());
            jvm_args.remove(i + 1);
            return;
        }
        i += 1;
    }
}

fn supports_quick_play(mc_version: &str) -> bool {
    if let Some(rest) = mc_version.strip_prefix("1.")
        && let Some(minor_str) = rest.split('.').next()
        && let Ok(minor) = minor_str.parse::<u32>()
    {
        return minor >= 20;
    }
    true
}

fn append_quick_play_args(game_args: &mut Vec<String>, mc_version: &str, address: &str) {
    if supports_quick_play(mc_version) {
        game_args.push("--quickPlayMultiplayer".to_string());
        game_args.push(address.to_string());
    } else if let Some(rest) = address.strip_prefix('[')
        && let Some((host, tail)) = rest.split_once(']')
    {
        game_args.push("--server".to_string());
        game_args.push(host.to_string());
        if let Some(port) = tail.strip_prefix(':')
            && !port.is_empty()
        {
            game_args.push("--port".to_string());
            game_args.push(port.to_string());
        }
    } else if let Some((host, port)) = address.rsplit_once(':')
        && !host.is_empty()
        && port.chars().all(|c| c.is_ascii_digit())
    {
        game_args.push("--server".to_string());
        game_args.push(host.to_string());
        game_args.push("--port".to_string());
        game_args.push(port.to_string());
    } else {
        game_args.push("--server".to_string());
        game_args.push(address.to_string());
    }
}

fn discover_local_native_lib(paths: &LauncherPaths) -> Option<PathBuf> {
    let lib_name = if cfg!(target_os = "windows") {
        "nightclient_native.dll"
    } else if cfg!(target_os = "macos") {
        "libnightclient_native.dylib"
    } else {
        "libnightclient_native.so"
    };

    let mut candidates = vec![paths.root.join("natives").join(lib_name)];
    if let Ok(exe) = std::env::current_exe()
        && let Some(dir) = exe.parent()
    {
        candidates.push(dir.join(lib_name));
        candidates.push(dir.join("natives").join(lib_name));
        let mut walk = Some(dir);
        for _ in 0..5 {
            if let Some(p) = walk {
                candidates.push(
                    p.join("native")
                        .join("nightclient-native")
                        .join("target")
                        .join("release")
                        .join(lib_name),
                );
                walk = p.parent();
            }
        }
    }
    candidates.into_iter().find(|p| p.is_file())
}

fn resolve_override_runtime(
    override_path: &str,
    runtimes: &[JavaRuntime],
    required_java: u32,
) -> JavaRuntime {
    let trimmed = override_path.trim();
    let override_pb = PathBuf::from(trimmed);
    let canonical = fs::canonicalize(&override_pb).ok();

    if let Some(found) = runtimes.iter().find(|r| {
        r.path == override_pb
            || canonical.as_ref().is_some_and(|c| {
                r.path == *c || fs::canonicalize(&r.path).ok().as_ref() == Some(c)
            })
    }) {
        return found.clone();
    }

    if let Some(probed) = probe_java_runtime(&override_pb) {
        return probed;
    }

    JavaRuntime {
        path: override_pb,
        major: required_java,
        version_label: format!("Java {required_java}"),
        supports_shenandoah: false,
        supports_gen_shenandoah: false,
        supports_gen_zgc: false,
        supports_compact_headers: false,
    }
}

pub fn is_client_shutdown_line(line: &str) -> bool {
    if line.contains("[CHAT]") || line.contains("Stopping server") {
        return false;
    }
    let has_client_thread = line.contains("[Render thread/INFO]")
        || line.contains("[Client thread/INFO]")
        || line.contains("[main/INFO]");
    (has_client_thread && (line.contains("Stopping!") || line.contains("Sound engine shut down")))
        || line.trim_end().ends_with("]: Stopping!")
}

pub fn is_normal_game_exit(
    exit_code: Option<i32>,
    success: bool,
    user_stopped: bool,
    clean_shutdown_seen: bool,
    watchdog_cleaned_up: bool,
    has_fresh_crash_artifact: bool,
) -> bool {
    if success || user_stopped || watchdog_cleaned_up {
        return true;
    }
    if has_fresh_crash_artifact {
        return false;
    }
    if clean_shutdown_seen {
        return true;
    }
    matches!(
        exit_code,
        None | Some(0) | Some(130) | Some(143) | Some(-1073741510) | Some(1073807364)
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normal_user_quit_and_stop_are_not_reported_as_crashes() {
        assert!(is_normal_game_exit(Some(1), false, true, false, false, false));
        assert!(is_normal_game_exit(None, false, true, false, false, false));
        assert!(is_normal_game_exit(Some(143), false, false, true, true, false));
        assert!(is_normal_game_exit(Some(-1), false, false, true, false, false));
        assert!(is_normal_game_exit(Some(143), false, false, false, false, false));
        assert!(is_normal_game_exit(None, false, false, false, false, false));
        assert!(!is_normal_game_exit(Some(1), false, false, false, false, false));
        assert!(!is_normal_game_exit(Some(1), false, false, true, false, true));
    }

    #[test]
    fn client_shutdown_marker_ignores_chat_and_integrated_server() {
        assert!(is_client_shutdown_line("[22:41:10] [Render thread/INFO]: Stopping!"));
        assert!(is_client_shutdown_line("[22:41:10] [Client thread/INFO]: Stopping!"));
        assert!(!is_client_shutdown_line("[22:41:10] [Render thread/INFO]: [CHAT] <Steve> Stopping!"));
        assert!(!is_client_shutdown_line("[22:41:10] [Server thread/INFO]: Stopping server"));
    }

    #[test]
    fn override_runtime_uses_cache_probes_uncached_and_falls_back() {
        let cached = JavaRuntime {
            path: PathBuf::from("/opt/custom-jdk-25/bin/java"),
            major: 25,
            version_label: "Java 25 (25.0.5.1)".into(),
            supports_shenandoah: true,
            supports_gen_shenandoah: true,
            supports_gen_zgc: true,
            supports_compact_headers: true,
        };
        let resolved_cached = resolve_override_runtime(
            "/opt/custom-jdk-25/bin/java",
            std::slice::from_ref(&cached),
            21,
        );
        assert_eq!(resolved_cached.major, 25);
        assert!(resolved_cached.supports_compact_headers);
        assert!(resolved_cached.supports_gen_zgc);

        let fallback = resolve_override_runtime("/nonexistent/path/to/java", &[], 21);
        assert_eq!(fallback.path, PathBuf::from("/nonexistent/path/to/java"));
        assert_eq!(fallback.major, 21);
        assert!(!fallback.supports_compact_headers);
        assert!(!fallback.supports_gen_zgc);

        let real_jdk25 = "/usr/lib/jvm/java-25-openjdk/bin/java";
        if std::path::Path::new(real_jdk25).is_file() {
            let probed = resolve_override_runtime(real_jdk25, &[], 21);
            assert_eq!(probed.major, 25);
            assert!(probed.supports_compact_headers);
            assert!(probed.supports_gen_zgc);
        }
    }

    #[test]
    fn quick_play_uses_legacy_flags_for_pre_1_20() {
        let mut modern = Vec::new();
        append_quick_play_args(&mut modern, "1.21.4", "play.holyworld.ru:25565");
        assert_eq!(modern, vec!["--quickPlayMultiplayer", "play.holyworld.ru:25565"]);

        let mut legacy = Vec::new();
        append_quick_play_args(&mut legacy, "1.19.4", "play.holyworld.ru:25565");
        assert_eq!(legacy, vec!["--server", "play.holyworld.ru", "--port", "25565"]);

        let mut modern_yr = Vec::new();
        append_quick_play_args(&mut modern_yr, "26.1", "mc.example.net");
        assert_eq!(modern_yr, vec!["--quickPlayMultiplayer", "mc.example.net"]);

        assert_eq!(
            format_java_argfile_token(r#"C:\Users\My Name\a.jar;C:\b.jar"#),
            r#""C:\\Users\\My Name\\a.jar;C:\\b.jar""#
        );
    }
}
