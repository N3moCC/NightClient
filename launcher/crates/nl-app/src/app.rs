use crate::blur;
use crate::i18n::{I18nCatalog, LocaleMeta};
use crate::icons::APP_ICON_PNG;
use crate::markdown_view::{MdBlock, RemoteImage, extract_image_urls, parse_modrinth_markdown};
use crate::theme::Palette;
use crate::views;
use iced::futures::SinkExt;
use iced::widget::image;
use iced::{Element, Event, Size, Subscription, Task, Theme, keyboard, window};
use nl_auth::{
    Account, AccountBook, DeviceCodePrompt, complete_device_code_login, create_offline_account,
    default_steve_head_png, fetch_avatar_png, load_accounts, remove_account_secrets,
    resolve_client_id, run_pkce_browser_login, save_accounts, start_device_code_login,
};
use nl_content::{
    ContentKind, ContentRegistry, DroppedFileAction, InstalledMod, ModMetaCache,
    ModrinthProjectDetails, ModrinthSearchHit, ModrinthSearchQuery, ModrinthSort, ModrinthVersion,
    ScreenshotEntry, check_mod_updates, classify_dropped_file, delete_mod_file,
    download_modpack_mrpack, export_mrpack, fetch_project_icon, fetch_project_versions,
    fetch_remote_image, get_project_details, import_mrpack, install_modrinth_project,
    install_modrinth_version, install_optimization_pack, migrate_mods_to_version,
    scan_screenshots, search_modrinth, toggle_mod_enabled,
};
use nl_core::{
    CrashFixAction, CreateInstanceParams, DetectedLauncher, InstanceRecord, InstanceSort,
    InstanceStatus, LaunchEvent, LaunchOptions, LaunchRequest, LaunchStage, LauncherConfig,
    LoaderKind, LoaderVersionOption, ManifestVersionEntry, MigrationResult, MigrationSelection,
    SavedServer, ThemeMode, clone_instance, create_instance, delete_instance,
    discover_external_launchers, execute_migration, fetch_loader_versions, fetch_version_manifest,
    inspect_custom_folder, load_config, prepare_and_launch_instance, read_servers_dat,
    rename_instance, save_config, save_instance_config, scan_instances, switch_instance_loader,
    upload_to_mclogs,
};
use nl_java::{JavaRuntime, PerformanceProfile, install_java_runtime, scan_java_runtimes};
use nl_net::NetClient;
use nl_night::{
    GitHubReleaseBundle, InstalledNcTheme, LauncherUpdateInfo, NightClientChannel,
    NightClientManifest, VisualsCompatibility, default_embedded_manifest,
    detect_duplicate_nightclient_jars, download_and_apply_launcher_update, evaluate_compatibility,
    fetch_github_releases_bundle, install_nc_theme_file, list_installed_themes,
};
#[cfg(target_os = "linux")]
use nl_platform::GpuPreference;
use nl_platform::{
    DeepLink, LauncherPaths, SecretBackend, SecretStore, copy_image_to_clipboard,
    install_app_shortcut, terminate_process, total_memory_mb,
};
use std::collections::{BTreeSet, HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Page {
    Workspace,
    Settings,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InstanceTab {
    Mods,
    ResourcePacks,
    Shaders,
    Themes,
    Screenshots,
    Console,
    Options,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModDetailsTab {
    Description,
    Gallery,
    Versions,
}

#[derive(Debug, Clone)]
pub enum PendingDelete {
    Instance { id: String, name: String },
    ContentFile { path: PathBuf, name: String },
    Screenshot { path: PathBuf, name: String },
    Account { id: String, name: String },
}

#[derive(Debug, Clone)]
pub enum ActiveModal {
    CreateInstance(CreateInstanceForm),
    CloneInstance(CloneInstanceForm),
    RenameInstance { id: String, draft: String },
    InstanceContextMenu(String),
    ScreenshotLightbox(PathBuf),
    ConfirmDelete(PendingDelete),
    LauncherUpdate,
}

#[derive(Debug, Clone)]
pub struct CreateInstanceForm {
    pub name: String,
    pub mc_version: String,
    pub version_filter: String,
    pub version_expanded: bool,
    pub show_snapshots: bool,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
    pub nightclient_enabled: bool,
}

impl Default for CreateInstanceForm {
    fn default() -> Self {
        Self {
            name: String::new(),
            mc_version: "1.21.4".to_string(),
            version_filter: String::new(),
            version_expanded: false,
            show_snapshots: false,
            loader: LoaderKind::Fabric,
            loader_version: None,
            nightclient_enabled: true,
        }
    }
}

#[derive(Debug, Clone)]
pub struct CloneInstanceForm {
    pub source_id: String,
    pub new_name: String,
    pub target_mc: String,
    pub target_loader: LoaderKind,
    pub target_loader_version: Option<String>,
    pub version_filter: String,
    pub version_expanded: bool,
    pub show_snapshots: bool,
    pub migrate_mods: bool,
}

#[derive(Debug, Clone)]
pub enum WelcomeStep {
    Intro,
    Scanning,
    Pick,
    Importing { name: String },
    Done(MigrationResult),
}

#[derive(Debug, Clone)]
pub struct WelcomeState {
    pub step: WelcomeStep,
    pub detected: Vec<DetectedLauncher>,
    pub selected_launcher: Option<usize>,
    pub selected_profile: Option<usize>,
    pub selection: MigrationSelection,
    pub last_imported_id: Option<String>,
}

impl WelcomeState {
    pub fn intro() -> Self {
        Self {
            step: WelcomeStep::Intro,
            detected: Vec::new(),
            selected_launcher: None,
            selected_profile: None,
            selection: MigrationSelection::default(),
            last_imported_id: None,
        }
    }

    pub fn sync_selection_from_current(&mut self) {
        let Some(prof) = self
            .selected_launcher
            .and_then(|li| self.detected.get(li))
            .and_then(|l| self.selected_profile.and_then(|pi| l.profiles.get(pi)))
        else {
            return;
        };
        self.selection = MigrationSelection {
            custom_name: prof.name.clone(),
            copy_mods: prof.mod_count > 0,
            copy_config: prof.has_config_dir,
            copy_resourcepacks: prof.resourcepack_count > 0,
            copy_shaderpacks: prof.shader_count > 0,
            copy_options_txt: prof.has_options_txt,
            copy_servers_dat: prof.server_count > 0,
            copy_saves: false,
            copy_screenshots: false,
            enable_nightclient: prof.loader == LoaderKind::Fabric,
        };
    }
}

#[derive(Debug, Clone)]
pub struct Toast {
    pub message: String,
    pub is_error: bool,
}

#[derive(Debug, Clone)]
pub struct InstanceScanSnapshot {
    pub seq: u64,
    pub instance_id: String,
    pub mods: Vec<InstalledMod>,
    pub packs: Vec<InstalledMod>,
    pub shaders: Vec<InstalledMod>,
    pub screenshots: Vec<ScreenshotEntry>,
    pub servers: Vec<SavedServer>,
    pub registry_ids: BTreeSet<String>,
}

#[derive(Debug, Clone, Default)]
pub struct DroppedFilesSummary {
    pub added_names: Vec<String>,
    pub mrpacks: Vec<PathBuf>,
    pub installed_theme: Option<(String, String)>,
    pub unsupported_count: usize,
    pub missing_instance: bool,
    pub first_error: Option<String>,
}

#[derive(Debug, Clone)]
pub enum Message {
    TitleBarPressed,
    MinimizeWindow,
    MaximizeWindow,
    CloseWindow,
    ResizeWindow(window::Direction),
    WindowResized(Size),
    WindowMaximizedState(bool),
    BlurApplied,

    SelectPage(Page),
    SelectInstance(String),
    SelectInstanceTab(InstanceTab),
    SidebarFilterChanged(String),
    CycleInstanceSort,
    RefreshAll,

    NoOp,
    OpenCreateModal,
    OpenModal(ActiveModal),
    CloseModal,
    RequestDelete(PendingDelete),
    ConfirmDeleteExecute,
    ToggleAccountPopover,
    CloseAccountPopover,

    CreateFormName(String),
    CreateFormMcVersion(String),
    CreateFormToggleVersionList,
    CreateFormCloseVersionList,
    CreateFormVersionFilter(String),
    CreateFormShowSnapshots(bool),
    CreateFormLoader(LoaderKind),
    CreateFormLoaderVersionLabel(String),
    CreateFormNightClient(bool),
    SubmitCreateInstance,

    LoaderVersionsLoaded {
        loader: LoaderKind,
        mc_version: String,
        result: Result<Vec<LoaderVersionOption>, String>,
    },

    OptionsDraftLoader(LoaderKind),
    OptionsDraftLoaderVersionLabel(String),
    ApplyOptionsLoader(String),

    RenameDraftChanged(String),
    SubmitRename,
    CloneFormName(String),
    CloneFormMc(String),
    CloneFormToggleVersionList,
    CloneFormCloseVersionList,
    CloneFormVersionFilter(String),
    CloneFormShowSnapshots(bool),
    CloneFormLoader(LoaderKind),
    CloneFormLoaderVersionLabel(String),
    CloneFormMigrateMods(bool),
    SubmitCloneInstance,
    CloneFinished(Result<InstanceRecord, String>),

    OpenInstanceFolder(String),
    OpenInstanceModsFolder(String),
    ToggleInstanceVisuals(String, bool),
    ToggleInstanceSharedProfile(String, bool),
    ToggleInstanceTurbo(String, bool),
    UpgradeInstanceFabric(String, String),
    SetInstanceMemory(String, Option<u32>),
    SetInstanceJavaLabel(String, String),
    SetInstanceProfile(String, Option<PerformanceProfile>),
    RepairInstance(String),
    ExportInstanceMrpack(String),
    ExportMrpackPicked(String, Option<PathBuf>),
    ExportMrpackFinished(Result<PathBuf, String>),
    ImportMrpackPick,
    ImportMrpackPicked(Option<PathBuf>),
    MrpackImported(Result<InstanceRecord, String>),

    LaunchInstance {
        id: String,
        quick_play: Option<String>,
    },
    CancelPrepare(String),
    StopInstance {
        id: String,
        force: bool,
    },
    LaunchStreamBatch(Vec<LaunchEvent>),
    Tick,
    DismissCrash(String),
    UploadCrashLog(String),
    CrashLogUploaded(Result<String, String>),
    ApplyCrashFix(String, CrashFixAction),

    InstanceDataRefreshed(InstanceScanSnapshot),
    DroppedFilesProcessed(DroppedFilesSummary),
    ContentFilterChanged(String),
    ToggleModRowHover(Option<String>),
    ToggleContentFile(PathBuf),
    ImportContentFilePick(String),
    ImportContentFilesPicked(String, Vec<PathBuf>),
    CheckContentUpdates(String),
    ContentUpdatesFinished(Result<usize, String>),
    InstallFpsPack(String),
    FpsPackFinished(Vec<String>),

    SetBrowseModrinth(bool),
    BrowseQueryChanged(String),
    BrowseSortChanged(ModrinthSort),
    BrowseSearchSubmit,
    BrowseSearchFinished(Result<Vec<ModrinthSearchHit>, String>),
    ProjectIconLoaded(String, Option<PathBuf>),
    BrowseOpenProject(String),
    BrowseProjectLoaded(Result<ModrinthProjectDetails, String>),
    BrowseVersionsLoaded(String, Result<Vec<ModrinthVersion>, String>),
    BrowseSelectDetailTab(ModDetailsTab),
    BrowseSelectGalleryIndex(usize),
    BrowseToggleMarkdownDetails(usize),
    RemoteImageLoaded(String, Option<(Vec<u8>, u32, u32)>),
    BrowseCloseProject,
    BrowseInstallProject(String, ContentKind),
    BrowseInstallVersion {
        project_id: String,
        version: ModrinthVersion,
        kind: ContentKind,
    },
    BrowseInstallFinished(String, Result<Vec<String>, String>),
    MarkdownLinkClicked(String),

    RefreshScreenshots,
    StepLightboxScreenshot(isize),
    CopyScreenshot(PathBuf),
    CopyScreenshotFinished(Result<(), String>),
    OpenScreenshotsFolder(String),
    ConsoleFilterChanged(String),
    ClearConsole(String),
    CopyConsole(String),

    SelectNcTheme(String),
    ToggleSyncUiWithTheme(bool),
    ImportNcThemePick,
    ImportNcThemePicked(Option<PathBuf>),

    SelectAccount(String),
    OfflineNameChanged(String),
    SubmitOfflineAccount,
    StartMicrosoftBrowserLogin,
    StartMicrosoftDeviceLogin,
    CancelMicrosoftLogin,
    DeviceCodeReady(Result<DeviceCodePrompt, String>),
    MicrosoftLoginFinished(Result<(Account, SecretBackend), String>),
    AvatarLoaded(String, Option<Vec<u8>>),

    SelectLocale(LocaleMeta),
    SetThemeMode(ThemeMode),
    SetDefaultMemoryMb(u32),
    SetDefaultProfile(PerformanceProfile),
    ToggleDefaultTurbo(bool),
    SetDefaultJavaLabel(String),
    SetExtraJvmArgs(String),
    SetExtraGameArgs(String),
    #[cfg(target_os = "linux")]
    SetGpuPreference(GpuPreference),
    #[cfg(target_os = "linux")]
    ToggleGamemode(bool),
    #[cfg(target_os = "linux")]
    ToggleMangohud(bool),
    ToggleMinimizeOnLaunch(bool),
    SetNightClientChannel(NightClientChannel),
    CheckLauncherUpdates(bool),
    GitHubReleasesLoaded {
        bundle: GitHubReleaseBundle,
        manual: bool,
    },
    OpenLauncherUpdateModal,
    DismissLauncherUpdate(String),
    ToggleChangelogDetails(usize),
    StartLauncherUpdate,
    LauncherUpdateProgress(f32),
    LauncherUpdateFinished(Result<PathBuf, String>),
    SetProxyUrl(String),
    InstallJavaMajor(u32),
    JavaInstalled(u32, Result<JavaRuntime, String>),
    InstallDesktopShortcut,
    OpenDataFolder,
    OpenLocalesFolder,

    JavaScanned(Vec<JavaRuntime>),
    MojangVersionsLoaded(Result<Vec<ManifestVersionEntry>, String>),
    SystemEvent(Event),
    #[cfg(target_os = "linux")]
    WaylandDnd(smithay_clipboard::WaylandDndEvent),
    CopyText(String),
    OpenUrl(String),
    DismissToast,

    ToggleServersPopover,
    CloseServersPopover,

    OpenMigrationWizard,
    WelcomeBackToIntro,
    WelcomeSkip,
    WelcomeStartScan,
    WelcomeScanFinished(Vec<DetectedLauncher>),
    WelcomeBrowseFolder,
    WelcomeFolderPicked(Option<PathBuf>),
    WelcomeFolderInspected(Option<DetectedLauncher>),
    WelcomeSelectLauncher(usize),
    WelcomeSelectProfile(usize),
    WelcomeCustomNameChanged(String),
    WelcomeToggleOption(&'static str),
    WelcomeStartImport,
    WelcomeImportFinished(Result<(InstanceRecord, MigrationResult), String>),
    WelcomeImportAnother,
    WelcomeFinish,
}

pub struct App {
    pub paths: LauncherPaths,
    pub net: NetClient,
    pub config: LauncherConfig,
    pub i18n: I18nCatalog,
    pub palette: Palette,
    pub page: Page,
    pub instance_tab: InstanceTab,

    pub window_size: Size,
    pub corner_radius: f32,
    pub is_maximized: bool,
    pub last_titlebar_click: Instant,

    pub instances: Vec<InstanceRecord>,
    pub selected_instance_id: Option<String>,
    pub sidebar_filter: String,
    pub instance_sort: InstanceSort,
    pub statuses: HashMap<String, InstanceStatus>,
    pub logs: HashMap<String, Vec<String>>,
    pub console_filter: String,
    pub servers: HashMap<String, Vec<SavedServer>>,

    pub options_loader_draft: Option<(String, LoaderKind, Option<String>)>,

    pub loader_versions_cache: HashMap<(LoaderKind, String), Vec<LoaderVersionOption>>,
    pub loader_versions_loading: HashSet<(LoaderKind, String)>,

    launch_tx: mpsc::UnboundedSender<LaunchEvent>,
    launch_rx: Arc<Mutex<Option<mpsc::UnboundedReceiver<LaunchEvent>>>>,
    #[cfg(target_os = "linux")]
    dnd_rx: Arc<Mutex<Option<mpsc::UnboundedReceiver<smithay_clipboard::WaylandDndEvent>>>>,
    prepare_cancels: HashMap<String, CancellationToken>,
    running_pids: HashMap<String, u32>,
    pub running_started: HashMap<String, Instant>,
    user_stopped_flags: HashMap<String, Arc<AtomicBool>>,
    pub tick_now: Instant,

    pub content_filter: String,
    pub hovered_content_file: Option<String>,
    pub installed_mods: Vec<InstalledMod>,
    pub installed_packs: Vec<InstalledMod>,
    pub installed_shaders: Vec<InstalledMod>,
    pub screenshots: Vec<ScreenshotEntry>,
    installed_registry_ids: BTreeSet<String>,
    refresh_seq: u64,
    last_focus_refresh: Instant,
    pub checking_updates: bool,
    pub installing_fps_pack: bool,

    pub browse_content_active: bool,
    pub browse_query: String,
    pub browse_sort: ModrinthSort,
    pub browse_hits: Vec<ModrinthSearchHit>,
    pub browse_loading: bool,
    pub browse_detail_loading: bool,
    pub browse_detail: Option<ModrinthProjectDetails>,
    pub browse_detail_blocks: Vec<MdBlock>,
    pub browse_detail_tab: ModDetailsTab,
    pub browse_detail_versions: Vec<ModrinthVersion>,
    pub browse_detail_versions_loading: bool,
    pub browse_detail_expanded: HashSet<usize>,
    pub browse_detail_gallery_idx: usize,
    pub project_icons: HashMap<String, PathBuf>,
    pub remote_images: HashMap<String, RemoteImage>,
    remote_images_inflight: HashSet<String>,
    pub installing_projects: HashMap<String, bool>,

    pub account_book: AccountBook,
    pub avatar_handles: HashMap<String, image::Handle>,
    pub default_avatar_handle: image::Handle,
    pub account_popover_open: bool,
    pub offline_name_input: String,
    pub ms_login_busy: bool,
    pub device_code_prompt: Option<DeviceCodePrompt>,
    ms_login_cancel: Option<CancellationToken>,
    pub secret_backend: SecretBackend,

    pub java_runtimes: Vec<JavaRuntime>,
    pub installing_java_major: Option<u32>,
    pub system_ram_mb: u64,
    pub mojang_versions: Vec<ManifestVersionEntry>,
    pub mojang_loading: bool,
    pub night_manifest: NightClientManifest,
    pub installed_themes: Vec<InstalledNcTheme>,
    pub launcher_update: Option<LauncherUpdateInfo>,
    pub checking_launcher_update: bool,
    pub update_changelog_blocks: Vec<MdBlock>,
    pub update_changelog_expanded: HashSet<usize>,
    pub installing_launcher_update: bool,
    pub launcher_update_progress: f32,
    pub launcher_update_error: Option<String>,
    update_cancel: Option<CancellationToken>,

    pub modal: Option<ActiveModal>,
    pub servers_popover_open: bool,
    pub welcome: Option<WelcomeState>,
    pub toast: Option<Toast>,
    pub dragging_files: bool,
}

impl App {
    pub fn launcher_version(&self) -> &'static str {
        env!("CARGO_PKG_VERSION")
    }

    pub fn boot(deep_link: Option<DeepLink>, force_welcome: bool) -> (Self, Task<Message>) {
        let paths = LauncherPaths::new(LauncherPaths::default_root());
        let _ = paths.ensure_all();

        let mut config = load_config(&paths);
        let i18n = I18nCatalog::load(Some(&paths.locales_dir()), &config.locale);
        if let Some(meta) = i18n.active_meta() {
            config.locale = meta.code;
        }

        let net = NetClient::new(config.net.clone())
            .unwrap_or_else(|_| NetClient::new(Default::default()).unwrap());

        let mut instances = scan_instances(&paths);
        if !config.onboarding_completed && !instances.is_empty() && !force_welcome {
            config.onboarding_completed = true;
            let _ = save_config(&paths, &config);
        }
        let show_welcome = force_welcome || (!config.onboarding_completed && instances.is_empty());
        let selected_instance_id = config
            .selected_instance_id
            .clone()
            .filter(|id| instances.iter().any(|i| &i.config.id == id))
            .or_else(|| instances.first().map(|i| i.config.id.clone()));

        let account_book = load_accounts(&paths);
        let secret_backend = SecretStore::new(paths.vault_file()).detect_backend();
        let night_manifest = default_embedded_manifest();
        let installed_themes = list_installed_themes(&paths.nightclient_themes_dir());

        let active_tokens = installed_themes
            .iter()
            .find(|t| t.id == config.active_theme_id)
            .map(|t| &t.tokens);
        let palette = Palette::resolve(config.theme_mode, config.sync_ui_with_nc_theme, active_tokens);

        let default_avatar_handle = image::Handle::from_bytes(default_steve_head_png());
        let mut avatar_handles = HashMap::new();
        for acc in &account_book.accounts {
            let cached = paths.skins_cache_dir().join(format!("{}_v2.png", acc.id));
            if let Ok(bytes) = std::fs::read(&cached) {
                avatar_handles.insert(acc.id.clone(), image::Handle::from_bytes(bytes));
            }
        }

        let (launch_tx, launch_rx) = mpsc::unbounded_channel();
        #[cfg(target_os = "linux")]
        let dnd_rx = {
            let (dnd_tx, rx) = mpsc::unbounded_channel();
            smithay_clipboard::register_dnd_listener(move |ev| {
                let _ = dnd_tx.send(ev);
            });
            Arc::new(Mutex::new(Some(rx)))
        };

        let mut app = Self {
            paths,
            net,
            config,
            i18n,
            palette,
            page: Page::Workspace,
            instance_tab: InstanceTab::Mods,
            window_size: Size::new(1120.0, 720.0),
            corner_radius: 12.0,
            is_maximized: false,
            last_titlebar_click: Instant::now() - Duration::from_secs(5),
            instances: Vec::new(),
            selected_instance_id,
            sidebar_filter: String::new(),
            instance_sort: InstanceSort::LastPlayed,
            statuses: HashMap::new(),
            logs: HashMap::new(),
            console_filter: String::new(),
            servers: HashMap::new(),
            options_loader_draft: None,
            loader_versions_cache: HashMap::new(),
            loader_versions_loading: HashSet::new(),
            launch_tx,
            launch_rx: Arc::new(Mutex::new(Some(launch_rx))),
            #[cfg(target_os = "linux")]
            dnd_rx,
            prepare_cancels: HashMap::new(),
            running_pids: HashMap::new(),
            running_started: HashMap::new(),
            user_stopped_flags: HashMap::new(),
            tick_now: Instant::now(),
            content_filter: String::new(),
            hovered_content_file: None,
            installed_mods: Vec::new(),
            installed_packs: Vec::new(),
            installed_shaders: Vec::new(),
            screenshots: Vec::new(),
            installed_registry_ids: BTreeSet::new(),
            refresh_seq: 0,
            last_focus_refresh: Instant::now() - Duration::from_secs(10),
            checking_updates: false,
            installing_fps_pack: false,
            browse_content_active: false,
            browse_query: String::new(),
            browse_sort: ModrinthSort::Downloads,
            browse_hits: Vec::new(),
            browse_loading: false,
            browse_detail_loading: false,
            browse_detail: None,
            browse_detail_blocks: Vec::new(),
            browse_detail_tab: ModDetailsTab::Description,
            browse_detail_versions: Vec::new(),
            browse_detail_versions_loading: false,
            browse_detail_expanded: HashSet::new(),
            browse_detail_gallery_idx: 0,
            project_icons: HashMap::new(),
            remote_images: HashMap::new(),
            remote_images_inflight: HashSet::new(),
            installing_projects: HashMap::new(),
            account_book,
            avatar_handles,
            default_avatar_handle,
            account_popover_open: false,
            offline_name_input: String::new(),
            ms_login_busy: false,
            device_code_prompt: None,
            ms_login_cancel: None,
            secret_backend,
            java_runtimes: Vec::new(),
            installing_java_major: None,
            system_ram_mb: total_memory_mb(),
            mojang_versions: Vec::new(),
            mojang_loading: false,
            night_manifest,
            installed_themes,
            launcher_update: None,
            checking_launcher_update: true,
            update_changelog_blocks: Vec::new(),
            update_changelog_expanded: HashSet::new(),
            installing_launcher_update: false,
            launcher_update_progress: 0.0,
            launcher_update_error: None,
            update_cancel: None,
            modal: None,
            servers_popover_open: false,
            welcome: if show_welcome {
                Some(WelcomeState::intro())
            } else {
                None
            },
            toast: None,
            dragging_files: false,
        };

        let mut tasks = Vec::new();
        instances.sort_by_key(|a| std::cmp::Reverse(a.config.last_played_at));
        app.instances = instances;
        app.sync_options_loader_draft();
        tasks.push(app.spawn_refresh_selected_instance());

        tasks.push(blur::apply_window_blur(
            app.window_size.width as i32,
            app.window_size.height as i32,
            app.corner_radius as i32,
            app.palette.is_dark,
            Message::BlurApplied,
        ));

        let runtimes_dir = app.paths.runtimes_dir();
        let probes_file = app.paths.java_probes_file();
        tasks.push(Task::perform(
            async move {
                tokio::task::spawn_blocking(move || {
                    scan_java_runtimes(&runtimes_dir, &probes_file)
                })
                .await
                .unwrap_or_default()
            },
            Message::JavaScanned,
        ));

        app.mojang_loading = true;
        let net_v = app.net.clone();
        let paths_v = app.paths.clone();
        tasks.push(Task::perform(
            async move {
                let cancel = CancellationToken::new();
                fetch_version_manifest(&net_v, &paths_v, &cancel)
                    .await
                    .map(|m| m.versions)
                    .map_err(|e| e.to_string())
            },
            Message::MojangVersionsLoaded,
        ));

        let net_n = app.net.clone();
        let paths_n = app.paths.clone();
        let ch_n = app.config.nightclient_channel;
        let cur_ver = env!("CARGO_PKG_VERSION");
        tasks.push(Task::perform(
            async move {
                let cancel = CancellationToken::new();
                fetch_github_releases_bundle(&net_n, &paths_n, ch_n, cur_ver, &cancel).await
            },
            |bundle| Message::GitHubReleasesLoaded {
                bundle,
                manual: false,
            },
        ));

        for acc in app.account_book.accounts.clone() {
            let net_a = app.net.clone();
            let skins_dir = app.paths.skins_cache_dir();
            let id = acc.id.clone();
            tasks.push(Task::perform(
                async move {
                    let cancel = CancellationToken::new();
                    let path = fetch_avatar_png(&net_a, &skins_dir, &acc, &cancel).await;
                    let bytes = match path {
                        Some(p) => tokio::task::spawn_blocking(move || std::fs::read(&p).ok())
                            .await
                            .ok()
                            .flatten(),
                        None => None,
                    };
                    (id, bytes)
                },
                |(id, bytes)| Message::AvatarLoaded(id, bytes),
            ));
        }

        if let Some(inst) = app.selected_instance().cloned()
            && inst.config.loader != LoaderKind::Vanilla
        {
            tasks.push(app.ensure_loader_versions_task(
                inst.config.loader,
                inst.config.mc_version,
            ));
        }

        if let Some(action) = deep_link {
            match action {
                DeepLink::LaunchInstance(target) => {
                    if let Some(inst) = app.instances.iter().find(|i| {
                        i.config.id == target || i.config.name.eq_ignore_ascii_case(&target)
                    }) {
                        let id = inst.config.id.clone();
                        tasks.push(Task::done(Message::LaunchInstance {
                            id,
                            quick_play: None,
                        }));
                    }
                }
                DeepLink::JoinServer(addr) => {
                    if let Some(inst) = app.selected_instance() {
                        let id = inst.config.id.clone();
                        tasks.push(Task::done(Message::LaunchInstance {
                            id,
                            quick_play: Some(addr),
                        }));
                    }
                }
                DeepLink::InstallTheme(url) => {
                    app.show_toast(url, false);
                }
            }
        }

        (app, Task::batch(tasks))
    }

    pub fn title(&self) -> String {
        self.tr("app.title").to_string()
    }

    pub fn theme(&self) -> Theme {
        self.palette.iced_theme()
    }

    pub fn tr<'a>(&'a self, key: &'a str) -> &'a str {
        self.i18n.tr(key)
    }

    pub fn tr_fmt(&self, key: &str, args: &[(&str, &str)]) -> String {
        self.i18n.tr_fmt(key, args)
    }

    pub fn selected_instance(&self) -> Option<&InstanceRecord> {
        let id = self.selected_instance_id.as_deref()?;
        self.instances.iter().find(|i| i.config.id == id)
    }

    pub fn active_account(&self) -> Option<&Account> {
        let id = self.account_book.active_account_id.as_deref()?;
        self.account_book.accounts.iter().find(|a| a.id == id)
    }

    pub fn avatar_handle_for(&self, account_id: &str) -> &image::Handle {
        self.avatar_handles
            .get(account_id)
            .unwrap_or(&self.default_avatar_handle)
    }

    pub fn is_visuals_eligible(&self, loader: LoaderKind, mc_version: &str) -> bool {
        loader == LoaderKind::Fabric
            && self
                .night_manifest
                .release(self.config.nightclient_channel)
                .is_some_and(|r| r.supported_mc.iter().any(|v| v == mc_version))
    }

    pub fn elapsed_secs(&self, instance_id: &str) -> Option<u64> {
        if matches!(
            self.statuses.get(instance_id),
            Some(InstanceStatus::Running { .. })
        ) {
            self.running_started
                .get(instance_id)
                .map(|started| self.tick_now.saturating_duration_since(*started).as_secs())
        } else {
            None
        }
    }

    pub fn visuals_compat_for(&self, inst: &InstanceRecord) -> VisualsCompatibility {
        evaluate_compatibility(
            &self.night_manifest,
            self.config.nightclient_channel,
            &inst.config.mc_version,
            inst.config.loader.id(),
            inst.config.loader_version.as_deref(),
        )
    }

    pub fn current_content_kind(&self) -> ContentKind {
        match self.instance_tab {
            InstanceTab::ResourcePacks => ContentKind::ResourcePack,
            InstanceTab::Shaders => ContentKind::Shader,
            _ => ContentKind::Mod,
        }
    }

    pub fn is_compact(&self) -> bool {
        self.window_size.width < 940.0
    }

    pub fn loader_version_dropdown_options(
        &self,
        loader: LoaderKind,
        mc_version: &str,
        current: Option<&str>,
    ) -> (Vec<String>, String) {
        let auto_label = self.tr("options.loader_auto").to_string();
        let mut opts = vec![auto_label.clone()];
        let key = (loader, mc_version.to_string());
        if let Some(list) = self.loader_versions_cache.get(&key) {
            for item in list.iter().take(40) {
                if !opts.contains(&item.version) {
                    opts.push(item.version.clone());
                }
            }
        }
        let selected = match current {
            Some(v) if !v.trim().is_empty() => {
                let s = v.trim().to_string();
                if !opts.contains(&s) {
                    opts.insert(1, s.clone());
                }
                s
            }
            _ => auto_label,
        };
        (opts, selected)
    }

    pub fn java_dropdown_options(
        &self,
        current_path: Option<&str>,
        global_mode: bool,
    ) -> (Vec<String>, String) {
        let auto_label = if global_mode {
            self.tr("settings.java_auto").to_string()
        } else {
            self.tr("options.java_auto").to_string()
        };
        let mut opts = vec![auto_label.clone()];
        let mut selected = auto_label;
        for rt in &self.java_runtimes {
            let label = format!("Java {} ({})", rt.major, rt.path.to_string_lossy());
            if current_path == Some(rt.path.to_string_lossy().as_ref()) {
                selected = label.clone();
            }
            if !opts.contains(&label) {
                opts.push(label);
            }
        }
        (opts, selected)
    }

    fn resolve_java_label_to_path(&self, label: &str) -> Option<String> {
        for rt in &self.java_runtimes {
            let candidate = format!("Java {} ({})", rt.major, rt.path.to_string_lossy());
            if candidate == label {
                return Some(rt.path.to_string_lossy().to_string());
            }
        }
        None
    }

    fn persist_config(&self) {
        let _ = save_config(&self.paths, &self.config);
    }

    fn refresh_palette(&mut self) {
        let active_tokens = self
            .installed_themes
            .iter()
            .find(|t| t.id == self.config.active_theme_id)
            .map(|t| &t.tokens);
        self.palette = Palette::resolve(
            self.config.theme_mode,
            self.config.sync_ui_with_nc_theme,
            active_tokens,
        );
    }

    pub fn show_toast(&mut self, message: impl Into<String>, is_error: bool) {
        self.toast = Some(Toast {
            message: message.into(),
            is_error,
        });
    }

    fn sort_instances(&mut self) {
        match self.instance_sort {
            InstanceSort::LastPlayed => {
                self.instances
                    .sort_by_key(|a| std::cmp::Reverse(a.config.last_played_at));
            }
            InstanceSort::Name => {
                self.instances.sort_by_key(|a| a.config.name.to_lowercase());
            }
            InstanceSort::Playtime => {
                self.instances
                    .sort_by_key(|a| std::cmp::Reverse(a.config.playtime_secs));
            }
        }
    }

    fn sync_options_loader_draft(&mut self) {
        self.options_loader_draft = self.selected_instance().map(|inst| {
            (
                inst.config.id.clone(),
                inst.config.loader,
                inst.config.loader_version.clone(),
            )
        });
    }

    fn ensure_loader_versions_task(
        &mut self,
        loader: LoaderKind,
        mc_version: String,
    ) -> Task<Message> {
        if loader == LoaderKind::Vanilla {
            return Task::none();
        }
        let key = (loader, mc_version.clone());
        if self.loader_versions_cache.contains_key(&key)
            || !self.loader_versions_loading.insert(key)
        {
            return Task::none();
        }
        let net = self.net.clone();
        let paths = self.paths.clone();
        let mc_req = mc_version.clone();
        Task::perform(
            async move {
                let cancel = CancellationToken::new();
                fetch_loader_versions(&net, &paths, loader, &mc_req, &cancel)
                    .await
                    .map_err(|e| e.to_string())
            },
            move |result| Message::LoaderVersionsLoaded {
                loader,
                mc_version,
                result,
            },
        )
    }

    pub fn spawn_refresh_selected_instance(&mut self) -> Task<Message> {
        let Some(inst) = self.selected_instance().cloned() else {
            self.refresh_seq = self.refresh_seq.wrapping_add(1);
            self.installed_mods.clear();
            self.installed_packs.clear();
            self.installed_shaders.clear();
            self.screenshots.clear();
            self.installed_registry_ids.clear();
            return Task::none();
        };

        self.refresh_seq = self.refresh_seq.wrapping_add(1);
        let seq = self.refresh_seq;
        let instance_id = inst.config.id;
        let inst_dir = inst.dir;
        let mod_cache_db = self.paths.mod_cache_db();
        let icons_dir = self.paths.icons_cache_dir();
        let thumbs_dir = self.paths.thumbnails_cache_dir();

        Task::perform(
            async move {
                tokio::task::spawn_blocking(move || {
                    let cache = ModMetaCache::new(mod_cache_db, icons_dir);
                    let mods = cache.scan_mods_dir(&inst_dir.join("mods"));
                    let packs = scan_simple_pack_dir(&inst_dir.join("resourcepacks"));
                    let shaders = scan_simple_pack_dir(&inst_dir.join("shaderpacks"));
                    let screenshots = scan_screenshots(&inst_dir, &thumbs_dir);
                    let servers = read_servers_dat(&inst_dir);
                    let mut registry_ids = BTreeSet::new();
                    let reg = ContentRegistry::load(&inst_dir);
                    for (pid, entry) in reg.entries {
                        if !pid.is_empty() {
                            registry_ids.insert(pid);
                        }
                        if !entry.version_id.is_empty() {
                            registry_ids.insert(entry.version_id);
                        }
                    }
                    InstanceScanSnapshot {
                        seq,
                        instance_id,
                        mods,
                        packs,
                        shaders,
                        screenshots,
                        servers,
                        registry_ids,
                    }
                })
                .await
                .ok()
            },
            |snap| match snap {
                Some(s) => Message::InstanceDataRefreshed(s),
                None => Message::NoOp,
            },
        )
    }

    pub fn subscription(&self) -> Subscription<Message> {
        let events = iced::event::listen_with(filter_system_event);
        let launch_stream = Subscription::run_with(
            LaunchStreamKey(Arc::as_ptr(&self.launch_rx) as usize, self.launch_rx.clone()),
            build_launch_stream,
        );
        let mut subs = vec![events, launch_stream];

        #[cfg(target_os = "linux")]
        {
            subs.push(Subscription::run_with(
                DndStreamKey(Arc::as_ptr(&self.dnd_rx) as usize, self.dnd_rx.clone()),
                build_dnd_stream,
            ));
        }

        let any_running = !self.running_pids.is_empty()
            || self
                .statuses
                .values()
                .any(|s| matches!(s, InstanceStatus::Running { .. }));
        if any_running {
            subs.push(iced::time::every(Duration::from_secs(1)).map(|_| Message::Tick));
        }
        Subscription::batch(subs)
    }

    pub fn view(&self) -> Element<'_, Message> {
        views::view_app(self)
    }

    pub fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            Message::TitleBarPressed => {
                let now = Instant::now();
                let prev = self.last_titlebar_click;
                self.last_titlebar_click = now;
                if now.duration_since(prev) < Duration::from_millis(360) {
                    return window::latest().and_then(window::toggle_maximize);
                }
                window::latest().and_then(window::drag)
            }
            Message::MinimizeWindow => {
                window::latest().and_then(|id| window::minimize(id, true))
            }
            Message::MaximizeWindow => window::latest().and_then(window::toggle_maximize),
            Message::CloseWindow => {
                if let Some(c) = self.ms_login_cancel.take() {
                    c.cancel();
                }
                if let Some(c) = self.update_cancel.take() {
                    c.cancel();
                }
                for (_, c) in self.prepare_cancels.drain() {
                    c.cancel();
                }
                window::latest().and_then(window::close)
            }
            Message::ResizeWindow(dir) => {
                window::latest().and_then(move |id| window::drag_resize(id, dir))
            }
            Message::WindowResized(size) => {
                self.window_size = size;
                let w = size.width as i32;
                let h = size.height as i32;
                let r = self.corner_radius as i32;
                Task::batch([
                    window::latest()
                        .and_then(window::is_maximized)
                        .map(Message::WindowMaximizedState),
                    blur::apply_window_blur(w, h, r, self.palette.is_dark, Message::BlurApplied),
                ])
            }
            Message::WindowMaximizedState(maxed) => {
                if self.is_maximized == maxed {
                    return Task::none();
                }
                self.is_maximized = maxed;
                self.corner_radius = if maxed { 0.0 } else { 12.0 };
                blur::apply_window_blur(
                    self.window_size.width as i32,
                    self.window_size.height as i32,
                    self.corner_radius as i32,
                    self.palette.is_dark,
                    Message::BlurApplied,
                )
            }
            Message::BlurApplied | Message::NoOp => Task::none(),

            Message::SelectPage(page) => {
                self.page = page;
                self.account_popover_open = false;
                self.servers_popover_open = false;
                Task::none()
            }
            Message::SelectInstance(id) => {
                self.page = Page::Workspace;
                self.account_popover_open = false;
                self.servers_popover_open = false;
                self.selected_instance_id = Some(id.clone());
                self.config.selected_instance_id = Some(id);
                self.browse_content_active = false;
                self.browse_hits.clear();
                self.browse_detail = None;
                self.browse_detail_loading = false;
                self.browse_detail_blocks.clear();
                self.browse_detail_versions.clear();
                self.browse_detail_expanded.clear();
                self.content_filter.clear();
                self.persist_config();
                self.sync_options_loader_draft();
                let mut tasks = vec![self.spawn_refresh_selected_instance()];
                if let Some(inst) = self.selected_instance().cloned()
                    && inst.config.loader != LoaderKind::Vanilla
                {
                    tasks.push(self.ensure_loader_versions_task(
                        inst.config.loader,
                        inst.config.mc_version,
                    ));
                }
                Task::batch(tasks)
            }
            Message::SelectInstanceTab(tab) => {
                self.instance_tab = tab;
                self.browse_content_active = false;
                self.browse_hits.clear();
                self.browse_detail = None;
                self.browse_detail_loading = false;
                self.browse_detail_blocks.clear();
                self.browse_detail_versions.clear();
                self.browse_detail_expanded.clear();
                self.content_filter.clear();
                let mut tasks = vec![self.spawn_refresh_selected_instance()];
                if tab == InstanceTab::Options
                    && let Some(inst) = self.selected_instance().cloned()
                {
                    self.sync_options_loader_draft();
                    if inst.config.loader != LoaderKind::Vanilla {
                        tasks.push(self.ensure_loader_versions_task(
                            inst.config.loader,
                            inst.config.mc_version,
                        ));
                    }
                }
                Task::batch(tasks)
            }
            Message::SidebarFilterChanged(q) => {
                self.sidebar_filter = q;
                Task::none()
            }
            Message::CycleInstanceSort => {
                self.instance_sort = match self.instance_sort {
                    InstanceSort::LastPlayed => InstanceSort::Name,
                    InstanceSort::Name => InstanceSort::Playtime,
                    InstanceSort::Playtime => InstanceSort::LastPlayed,
                };
                self.sort_instances();
                Task::none()
            }
            Message::RefreshAll => {
                self.instances = scan_instances(&self.paths);
                self.sort_instances();
                self.installed_themes =
                    list_installed_themes(&self.paths.nightclient_themes_dir());
                self.spawn_refresh_selected_instance()
            }

            Message::OpenCreateModal => {
                self.account_popover_open = false;
                let form = CreateInstanceForm::default();
                let loader = form.loader;
                let mc = form.mc_version.clone();
                self.modal = Some(ActiveModal::CreateInstance(form));
                self.ensure_loader_versions_task(loader, mc)
            }
            Message::OpenModal(modal) => {
                self.account_popover_open = false;
                let preload = match &modal {
                    ActiveModal::CloneInstance(f) => Some((f.target_loader, f.target_mc.clone())),
                    _ => None,
                };
                self.modal = Some(modal);
                if let Some((loader, mc)) = preload {
                    return self.ensure_loader_versions_task(loader, mc);
                }
                Task::none()
            }
            Message::CloseModal => {
                self.modal = None;
                Task::none()
            }
            Message::RequestDelete(pending) => {
                self.account_popover_open = false;
                self.modal = Some(ActiveModal::ConfirmDelete(pending));
                Task::none()
            }
            Message::ConfirmDeleteExecute => {
                let Some(ActiveModal::ConfirmDelete(pending)) = self.modal.take() else {
                    return Task::none();
                };
                match pending {
                    PendingDelete::Instance { id, .. } => {
                        if let Some(tok) = self.prepare_cancels.remove(&id) {
                            tok.cancel();
                        }
                        if let Some(flag) = self.user_stopped_flags.get(&id) {
                            flag.store(true, Ordering::SeqCst);
                        }
                        let mut was_running = false;
                        if let Some(pid) = self.running_pids.remove(&id) {
                            was_running = true;
                            terminate_process(pid, true);
                        }
                        self.running_started.remove(&id);
                        self.statuses.remove(&id);

                        if let Some(pos) = self.instances.iter().position(|i| i.config.id == id) {
                            let dir = self.instances[pos].dir.clone();
                            let mut delete_res = delete_instance(&dir);
                            if delete_res.is_err() && was_running {
                                for _ in 0..10 {
                                    std::thread::sleep(Duration::from_millis(150));
                                    delete_res = delete_instance(&dir);
                                    if delete_res.is_ok() {
                                        break;
                                    }
                                }
                            }
                            if let Err(e) = delete_res {
                                let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                                self.show_toast(msg, true);
                            } else {
                                self.instances.remove(pos);
                                if self.selected_instance_id.as_deref() == Some(id.as_str()) {
                                    self.selected_instance_id =
                                        self.instances.first().map(|i| i.config.id.clone());
                                    self.config.selected_instance_id =
                                        self.selected_instance_id.clone();
                                    self.persist_config();
                                }
                                self.sync_options_loader_draft();
                                self.show_toast(self.tr("toast.instance_deleted").to_string(), false);
                                return self.spawn_refresh_selected_instance();
                            }
                        }
                    }
                    PendingDelete::ContentFile { path, name } => match delete_mod_file(&path) {
                        Ok(()) => {
                            let msg = self.tr_fmt("toast.file_deleted", &[("name", &name)]);
                            self.show_toast(msg, false);
                            return self.spawn_refresh_selected_instance();
                        }
                        Err(e) => {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                            self.show_toast(msg, true);
                        }
                    },
                    PendingDelete::Screenshot { path, name } => match std::fs::remove_file(&path) {
                        Ok(()) => {
                            let msg = self.tr_fmt("toast.file_deleted", &[("name", &name)]);
                            self.show_toast(msg, false);
                            return self.spawn_refresh_selected_instance();
                        }
                        Err(e) => {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                            self.show_toast(msg, true);
                        }
                    },
                    PendingDelete::Account { id, .. } => {
                        let secrets = SecretStore::new(self.paths.vault_file());
                        remove_account_secrets(&secrets, &id);
                        self.account_book.accounts.retain(|a| a.id != id);
                        self.avatar_handles.remove(&id);
                        if self.account_book.active_account_id.as_deref() == Some(id.as_str()) {
                            self.account_book.active_account_id =
                                self.account_book.accounts.first().map(|a| a.id.clone());
                        }
                        let _ = save_accounts(&self.paths, &self.account_book);
                        self.show_toast(self.tr("toast.account_deleted").to_string(), false);
                    }
                }
                Task::none()
            }
            Message::ToggleAccountPopover => {
                self.account_popover_open = !self.account_popover_open;
                Task::none()
            }
            Message::CloseAccountPopover => {
                self.account_popover_open = false;
                Task::none()
            }

            Message::CreateFormName(name) => {
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.name = name;
                }
                Task::none()
            }
            Message::CreateFormToggleVersionList => {
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.version_expanded = !f.version_expanded;
                }
                Task::none()
            }
            Message::CreateFormCloseVersionList => {
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.version_expanded = false;
                }
                Task::none()
            }
            Message::CreateFormVersionFilter(q) => {
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.version_filter = q;
                }
                Task::none()
            }
            Message::CreateFormShowSnapshots(show) => {
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.show_snapshots = show;
                }
                Task::none()
            }
            Message::CreateFormMcVersion(mc) => {
                let eligible = self.is_visuals_eligible(LoaderKind::Fabric, &mc);
                let mut fetch_pair = None;
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.mc_version = mc.clone();
                    f.version_expanded = false;
                    f.loader_version = None;
                    if !eligible || f.loader != LoaderKind::Fabric {
                        f.nightclient_enabled = false;
                    }
                    if f.loader != LoaderKind::Vanilla {
                        fetch_pair = Some((f.loader, mc));
                    }
                }
                if let Some((loader, ver)) = fetch_pair {
                    return self.ensure_loader_versions_task(loader, ver);
                }
                Task::none()
            }
            Message::CreateFormLoader(loader) => {
                let mut fetch_pair = None;
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.loader = loader;
                    f.loader_version = None;
                    if loader != LoaderKind::Fabric {
                        f.nightclient_enabled = false;
                    }
                    if loader != LoaderKind::Vanilla {
                        fetch_pair = Some((loader, f.mc_version.clone()));
                    }
                }
                if let Some((l, ver)) = fetch_pair {
                    return self.ensure_loader_versions_task(l, ver);
                }
                Task::none()
            }
            Message::CreateFormLoaderVersionLabel(label) => {
                let auto_label = self.tr("options.loader_auto").to_string();
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.loader_version = if label == auto_label { None } else { Some(label) };
                }
                Task::none()
            }
            Message::CreateFormNightClient(enabled) => {
                let eligible = if let Some(ActiveModal::CreateInstance(f)) = &self.modal {
                    self.is_visuals_eligible(f.loader, &f.mc_version)
                } else {
                    false
                };
                if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                    f.nightclient_enabled = enabled && eligible;
                }
                Task::none()
            }
            Message::SubmitCreateInstance => {
                let Some(ActiveModal::CreateInstance(form)) = self.modal.clone() else {
                    return Task::none();
                };
                if form.version_expanded {
                    if let Some(ActiveModal::CreateInstance(f)) = &mut self.modal {
                        f.version_expanded = false;
                    }
                    return Task::none();
                }
                let name = if form.name.trim().is_empty() {
                    format!("{} {}", form.loader.display_name(), form.mc_version)
                } else {
                    form.name.trim().to_string()
                };
                let loader_version = form.loader_version.or_else(|| {
                    self.loader_versions_cache
                        .get(&(form.loader, form.mc_version.clone()))
                        .and_then(|list| {
                            list.iter()
                                .find(|o| o.recommended)
                                .or_else(|| list.first())
                                .map(|o| o.version.clone())
                        })
                });
                let nightclient_enabled =
                    form.nightclient_enabled && self.is_visuals_eligible(form.loader, &form.mc_version);
                let params = CreateInstanceParams {
                    name,
                    mc_version: form.mc_version,
                    loader: form.loader,
                    loader_version,
                    group: None,
                    nightclient_enabled,
                };
                match create_instance(&self.paths, params) {
                    Ok(rec) => {
                        let id = rec.config.id.clone();
                        self.instances.insert(0, rec);
                        self.sort_instances();
                        self.selected_instance_id = Some(id.clone());
                        self.config.selected_instance_id = Some(id);
                        self.modal = None;
                        self.page = Page::Workspace;
                        self.persist_config();
                        self.sync_options_loader_draft();
                        self.show_toast(self.tr("toast.instance_created").to_string(), false);
                        return self.spawn_refresh_selected_instance();
                    }
                    Err(e) => {
                        let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                        self.show_toast(msg, true);
                    }
                }
                Task::none()
            }

            Message::LoaderVersionsLoaded {
                loader,
                mc_version,
                result,
            } => {
                self.loader_versions_loading
                    .remove(&(loader, mc_version.clone()));
                if let Ok(list) = result {
                    self.loader_versions_cache.insert((loader, mc_version), list);
                }
                Task::none()
            }

            Message::OptionsDraftLoader(loader) => {
                let mut fetch_pair = None;
                if let Some(inst) = self.selected_instance().cloned() {
                    let next_ver = if loader == inst.config.loader {
                        inst.config.loader_version.clone()
                    } else {
                        None
                    };
                    self.options_loader_draft = Some((inst.config.id.clone(), loader, next_ver));
                    if loader != LoaderKind::Vanilla {
                        fetch_pair = Some((loader, inst.config.mc_version));
                    }
                }
                if let Some((l, ver)) = fetch_pair {
                    return self.ensure_loader_versions_task(l, ver);
                }
                Task::none()
            }
            Message::OptionsDraftLoaderVersionLabel(label) => {
                let auto_label = self.tr("options.loader_auto").to_string();
                if let Some((_, _, ref mut ver)) = self.options_loader_draft {
                    *ver = if label == auto_label { None } else { Some(label) };
                }
                Task::none()
            }
            Message::ApplyOptionsLoader(id) => {
                if let Some((draft_id, loader, ver)) = self.options_loader_draft.clone()
                    && draft_id == id
                    && let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id)
                {
                    let resolved = if loader == LoaderKind::Vanilla {
                        None
                    } else {
                        ver.or_else(|| {
                            self.loader_versions_cache
                                .get(&(loader, inst.config.mc_version.clone()))
                                .and_then(|list| {
                                    list.iter()
                                        .find(|o| o.recommended)
                                        .or_else(|| list.first())
                                        .map(|o| o.version.clone())
                                })
                        })
                    };
                    if loader != LoaderKind::Fabric {
                        inst.config.nightclient_enabled = false;
                    }
                    if let Err(e) = switch_instance_loader(inst, loader, resolved) {
                        let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                        self.show_toast(msg, true);
                    } else {
                        self.sync_options_loader_draft();
                        self.show_toast(self.tr("toast.loader_updated").to_string(), false);
                    }
                }
                Task::none()
            }

            Message::RenameDraftChanged(draft) => {
                if let Some(ActiveModal::RenameInstance {
                    draft: ref mut d, ..
                }) = self.modal
                {
                    *d = draft;
                }
                Task::none()
            }
            Message::SubmitRename => {
                let Some(ActiveModal::RenameInstance { id, draft }) = self.modal.clone() else {
                    return Task::none();
                };
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    match rename_instance(inst, &draft) {
                        Ok(()) => {
                            self.modal = None;
                            self.sort_instances();
                            self.show_toast(self.tr("toast.instance_renamed").to_string(), false);
                        }
                        Err(e) => {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::CloneFormName(name) => {
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.new_name = name;
                }
                Task::none()
            }
            Message::CloneFormToggleVersionList => {
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.version_expanded = !f.version_expanded;
                }
                Task::none()
            }
            Message::CloneFormCloseVersionList => {
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.version_expanded = false;
                }
                Task::none()
            }
            Message::CloneFormVersionFilter(q) => {
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.version_filter = q;
                }
                Task::none()
            }
            Message::CloneFormShowSnapshots(show) => {
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.show_snapshots = show;
                }
                Task::none()
            }
            Message::CloneFormMc(mc) => {
                let mut fetch_pair = None;
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.target_mc = mc.clone();
                    f.version_expanded = false;
                    f.target_loader_version = None;
                    if f.target_loader != LoaderKind::Vanilla {
                        fetch_pair = Some((f.target_loader, mc));
                    }
                }
                if let Some((l, ver)) = fetch_pair {
                    return self.ensure_loader_versions_task(l, ver);
                }
                Task::none()
            }
            Message::CloneFormLoader(loader) => {
                let mut fetch_pair = None;
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.target_loader = loader;
                    f.target_loader_version = None;
                    if loader != LoaderKind::Vanilla {
                        fetch_pair = Some((loader, f.target_mc.clone()));
                    }
                }
                if let Some((l, ver)) = fetch_pair {
                    return self.ensure_loader_versions_task(l, ver);
                }
                Task::none()
            }
            Message::CloneFormLoaderVersionLabel(label) => {
                let auto_label = self.tr("options.loader_auto").to_string();
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.target_loader_version = if label == auto_label { None } else { Some(label) };
                }
                Task::none()
            }
            Message::CloneFormMigrateMods(v) => {
                if let Some(ActiveModal::CloneInstance(f)) = &mut self.modal {
                    f.migrate_mods = v;
                }
                Task::none()
            }
            Message::SubmitCloneInstance => {
                let Some(ActiveModal::CloneInstance(form)) = self.modal.clone() else {
                    return Task::none();
                };
                let Some(source) = self
                    .instances
                    .iter()
                    .find(|i| i.config.id == form.source_id)
                    .cloned()
                else {
                    return Task::none();
                };
                self.modal = None;
                let paths = self.paths.clone();
                let net = self.net.clone();
                let resolved_lv = form.target_loader_version.or_else(|| {
                    self.loader_versions_cache
                        .get(&(form.target_loader, form.target_mc.clone()))
                        .and_then(|list| {
                            list.iter()
                                .find(|o| o.recommended)
                                .or_else(|| list.first())
                                .map(|o| o.version.clone())
                        })
                });
                Task::perform(
                    async move {
                        let copy_verbatim = !form.migrate_mods
                            || (source.config.mc_version == form.target_mc
                                && source.config.loader == form.target_loader);
                        let mut new_inst = clone_instance(
                            &paths,
                            &source,
                            form.new_name,
                            Some(form.target_mc.clone()),
                            Some(form.target_loader),
                            copy_verbatim,
                        )
                        .map_err(|e| e.to_string())?;
                        if resolved_lv.is_some() {
                            let _ = switch_instance_loader(
                                &mut new_inst,
                                form.target_loader,
                                resolved_lv,
                            );
                        }
                        if form.migrate_mods && !copy_verbatim {
                            let cancel = CancellationToken::new();
                            let _ = migrate_mods_to_version(
                                &net,
                                &source.dir,
                                &new_inst.dir,
                                &form.target_mc,
                                form.target_loader.id(),
                                &cancel,
                            )
                            .await;
                        }
                        Ok(new_inst)
                    },
                    Message::CloneFinished,
                )
            }
            Message::CloneFinished(res) => {
                match res {
                    Ok(rec) => {
                        let id = rec.config.id.clone();
                        self.instances.insert(0, rec);
                        self.sort_instances();
                        self.selected_instance_id = Some(id.clone());
                        self.config.selected_instance_id = Some(id);
                        self.persist_config();
                        self.sync_options_loader_draft();
                        self.show_toast(self.tr("toast.instance_created").to_string(), false);
                        return self.spawn_refresh_selected_instance();
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }

            Message::OpenInstanceFolder(id) => {
                self.modal = None;
                if let Some(inst) = self.instances.iter().find(|i| i.config.id == id) {
                    open_detached(inst.dir.as_os_str());
                }
                Task::none()
            }
            Message::OpenInstanceModsFolder(id) => {
                if let Some(inst) = self.instances.iter().find(|i| i.config.id == id) {
                    let mods_dir = inst.dir.join("mods");
                    let _ = std::fs::create_dir_all(&mods_dir);
                    open_detached(mods_dir.as_os_str());
                }
                Task::none()
            }
            Message::ToggleInstanceVisuals(id, enabled) => {
                let eligible = self
                    .instances
                    .iter()
                    .find(|i| i.config.id == id)
                    .is_some_and(|i| self.is_visuals_eligible(i.config.loader, &i.config.mc_version));
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    inst.config.nightclient_enabled = enabled && eligible;
                    let _ = save_instance_config(&inst.dir, &inst.config);
                }
                Task::none()
            }
            Message::ToggleInstanceSharedProfile(id, shared) => {
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    inst.config.nightclient_shared_profile = shared;
                    let _ = save_instance_config(&inst.dir, &inst.config);
                }
                Task::none()
            }
            Message::ToggleInstanceTurbo(id, turbo) => {
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    inst.config.turbo_mode = turbo;
                    let _ = save_instance_config(&inst.dir, &inst.config);
                }
                Task::none()
            }
            Message::UpgradeInstanceFabric(id, version) => {
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    let _ = switch_instance_loader(inst, LoaderKind::Fabric, Some(version));
                    self.sync_options_loader_draft();
                    self.show_toast(self.tr("toast.loader_updated").to_string(), false);
                }
                Task::none()
            }
            Message::SetInstanceMemory(id, mem) => {
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    inst.config.memory_mb_override = mem;
                    let _ = save_instance_config(&inst.dir, &inst.config);
                }
                Task::none()
            }
            Message::SetInstanceJavaLabel(id, label) => {
                let path = self.resolve_java_label_to_path(&label);
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    inst.config.java_path_override = path;
                    let _ = save_instance_config(&inst.dir, &inst.config);
                }
                Task::none()
            }
            Message::SetInstanceProfile(id, prof) => {
                if let Some(inst) = self.instances.iter_mut().find(|i| i.config.id == id) {
                    inst.config.performance_profile_override = prof;
                    let _ = save_instance_config(&inst.dir, &inst.config);
                }
                Task::none()
            }
            Message::RepairInstance(id) => {
                self.modal = None;
                self.start_launch_or_repair(id, None, true)
            }
            Message::ExportInstanceMrpack(id) => {
                self.modal = None;
                let Some(inst) = self.instances.iter().find(|i| i.config.id == id).cloned() else {
                    return Task::none();
                };
                let default_name = format!("{}.mrpack", inst.config.name);
                Task::perform(
                    async move {
                        let picked = rfd::AsyncFileDialog::new()
                            .add_filter("Modrinth Modpack", &["mrpack"])
                            .set_file_name(default_name)
                            .save_file()
                            .await
                            .map(|f| f.path().to_path_buf());
                        (id, picked)
                    },
                    |(id, path)| Message::ExportMrpackPicked(id, path),
                )
            }
            Message::ExportMrpackPicked(id, dest) => {
                if let Some(dest) = dest
                    && let Some(inst) = self.instances.iter().find(|i| i.config.id == id).cloned()
                {
                    return Task::perform(
                        async move {
                            tokio::task::spawn_blocking(move || {
                                export_mrpack(
                                    &inst.dir,
                                    &inst.config.name,
                                    &inst.config.mc_version,
                                    inst.config.loader.id(),
                                    inst.config.loader_version.as_deref(),
                                    &dest,
                                )
                                .map(|()| dest)
                                .map_err(|e| e.to_string())
                            })
                            .await
                            .unwrap_or_else(|e| Err(e.to_string()))
                        },
                        Message::ExportMrpackFinished,
                    );
                }
                Task::none()
            }
            Message::ExportMrpackFinished(res) => {
                match res {
                    Ok(dest) => {
                        let msg = self.tr_fmt(
                            "toast.mrpack_exported",
                            &[("path", &dest.display().to_string())],
                        );
                        self.show_toast(msg, false);
                    }
                    Err(e) => {
                        let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                        self.show_toast(msg, true);
                    }
                }
                Task::none()
            }
            Message::ImportMrpackPick => Task::perform(
                async move {
                    rfd::AsyncFileDialog::new()
                        .add_filter("Modrinth Modpack", &["mrpack"])
                        .pick_file()
                        .await
                        .map(|f| f.path().to_path_buf())
                },
                Message::ImportMrpackPicked,
            ),
            Message::ImportMrpackPicked(path) => {
                if let Some(path) = path {
                    self.modal = None;
                    return self.spawn_mrpack_import(path);
                }
                Task::none()
            }
            Message::MrpackImported(res) => {
                match res {
                    Ok(rec) => {
                        let name = rec.config.name.clone();
                        let id = rec.config.id.clone();
                        self.instances.insert(0, rec);
                        self.sort_instances();
                        self.selected_instance_id = Some(id.clone());
                        self.config.selected_instance_id = Some(id);
                        self.persist_config();
                        self.sync_options_loader_draft();
                        let msg = self.tr_fmt("toast.mrpack_imported", &[("name", &name)]);
                        self.show_toast(msg, false);
                        return self.spawn_refresh_selected_instance();
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }

            Message::LaunchInstance { id, quick_play } => {
                self.modal = None;
                self.servers_popover_open = false;
                self.start_launch_or_repair(id, quick_play, false)
            }
            Message::CancelPrepare(id) => {
                if let Some(tok) = self.prepare_cancels.remove(&id) {
                    tok.cancel();
                }
                self.statuses.insert(id, InstanceStatus::Idle);
                Task::none()
            }
            Message::StopInstance { id, force } => {
                if let Some(flag) = self.user_stopped_flags.get(&id) {
                    flag.store(true, Ordering::SeqCst);
                }
                if let Some(&pid) = self.running_pids.get(&id) {
                    terminate_process(pid, force);
                    self.statuses.insert(id, InstanceStatus::Stopping);
                }
                Task::none()
            }
            Message::Tick => {
                self.tick_now = Instant::now();
                Task::none()
            }
            Message::DismissCrash(id) => {
                if matches!(self.statuses.get(&id), Some(InstanceStatus::Crashed { .. })) {
                    self.statuses.insert(id, InstanceStatus::Idle);
                }
                Task::none()
            }
            Message::LaunchStreamBatch(events) => {
                let mut should_refresh = false;
                for ev in events {
                    match ev {
                        LaunchEvent::Progress {
                            instance_id,
                            stage,
                            progress,
                        } => {
                            self.statuses
                                .insert(instance_id, InstanceStatus::Preparing { stage, progress });
                        }
                        LaunchEvent::LogLine { instance_id, line } => {
                            let buf = self.logs.entry(instance_id).or_default();
                            buf.push(line);
                            if buf.len() > 2500 {
                                let drop_n = buf.len() - 2500;
                                buf.drain(0..drop_n);
                            }
                        }
                        LaunchEvent::Started { instance_id, pid } => {
                            self.prepare_cancels.remove(&instance_id);
                            self.running_pids.insert(instance_id.clone(), pid);
                            let now = Instant::now();
                            self.tick_now = now;
                            self.running_started.insert(instance_id.clone(), now);
                            self.statuses.insert(
                                instance_id,
                                InstanceStatus::Running {
                                    pid,
                                    started_at: chrono::Utc::now(),
                                },
                            );
                        }
                        LaunchEvent::Exited {
                            instance_id,
                            exit_code,
                            playtime_secs,
                            diagnosis,
                        } => {
                            self.prepare_cancels.remove(&instance_id);
                            self.running_pids.remove(&instance_id);
                            self.running_started.remove(&instance_id);
                            self.user_stopped_flags.remove(&instance_id);
                            if let Some(inst) = self
                                .instances
                                .iter_mut()
                                .find(|i| i.config.id == instance_id)
                            {
                                inst.config.playtime_secs += playtime_secs;
                                inst.config.last_played_at = Some(chrono::Utc::now());
                                let _ = save_instance_config(&inst.dir, &inst.config);
                            }
                            if let Some(diag) = diagnosis {
                                self.statuses.insert(
                                    instance_id.clone(),
                                    InstanceStatus::Crashed {
                                        exit_code,
                                        diagnosis: diag,
                                    },
                                );
                            } else {
                                self.statuses.insert(instance_id.clone(), InstanceStatus::Idle);
                            }
                            if self.selected_instance_id.as_deref() == Some(instance_id.as_str()) {
                                should_refresh = true;
                            }
                        }
                        LaunchEvent::RepairCompleted { instance_id, .. } => {
                            self.prepare_cancels.remove(&instance_id);
                            self.user_stopped_flags.remove(&instance_id);
                            self.statuses.insert(instance_id, InstanceStatus::Idle);
                            self.show_toast(self.tr("toast.repair_complete").to_string(), false);
                        }
                        LaunchEvent::Failed {
                            instance_id,
                            message,
                        } => {
                            self.prepare_cancels.remove(&instance_id);
                            self.running_pids.remove(&instance_id);
                            self.running_started.remove(&instance_id);
                            self.user_stopped_flags.remove(&instance_id);
                            self.statuses.insert(instance_id, InstanceStatus::Idle);
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &message)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                if should_refresh {
                    return self.spawn_refresh_selected_instance();
                }
                Task::none()
            }
            Message::UploadCrashLog(id) => {
                let text = self
                    .logs
                    .get(&id)
                    .map(|l| l.join("\n"))
                    .unwrap_or_default();
                let net = self.net.clone();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        upload_to_mclogs(&net, &text, &cancel)
                            .await
                            .map_err(|e| e.to_string())
                    },
                    Message::CrashLogUploaded,
                )
            }
            Message::CrashLogUploaded(res) => {
                match res {
                    Ok(url) => {
                        copy_text_to_clipboard(&url);
                        let msg = self.tr_fmt("toast.logs_uploaded", &[("url", &url)]);
                        self.show_toast(msg, false);
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::ApplyCrashFix(id, fix) => match fix {
                CrashFixAction::IncreaseMemory { target_mb } => {
                    self.statuses.insert(id.clone(), InstanceStatus::Idle);
                    self.update(Message::SetInstanceMemory(id, Some(target_mb)))
                }
                CrashFixAction::InstallAndUseJava { major } => {
                    self.statuses.insert(id, InstanceStatus::Idle);
                    self.update(Message::InstallJavaMajor(major))
                }
                CrashFixAction::RemoveDuplicateNightClient => {
                    if let Some(inst) = self.instances.iter().find(|i| i.config.id == id) {
                        for dup in detect_duplicate_nightclient_jars(&inst.dir) {
                            let _ = std::fs::remove_file(dup);
                        }
                    }
                    self.statuses.insert(id, InstanceStatus::Idle);
                    self.spawn_refresh_selected_instance()
                }
                CrashFixAction::InstallMod { mod_id } => {
                    self.statuses.insert(id, InstanceStatus::Idle);
                    self.update(Message::BrowseInstallProject(mod_id, ContentKind::Mod))
                }
                CrashFixAction::OpenModsFolder => {
                    self.update(Message::OpenInstanceModsFolder(id))
                }
            },

            Message::InstanceDataRefreshed(snap) => {
                if snap.seq != self.refresh_seq
                    || self.selected_instance_id.as_deref() != Some(snap.instance_id.as_str())
                {
                    return Task::none();
                }
                self.installed_mods = snap.mods;
                self.installed_packs = snap.packs;
                self.installed_shaders = snap.shaders;
                self.screenshots = snap.screenshots;
                self.installed_registry_ids = snap.registry_ids;
                self.servers.insert(snap.instance_id.clone(), snap.servers);

                if let Some(rec) = self
                    .instances
                    .iter_mut()
                    .find(|i| i.config.id == snap.instance_id)
                {
                    rec.mod_count = self.installed_mods.len();
                    rec.resource_pack_count = self.installed_packs.len();
                    rec.shader_count = self.installed_shaders.len();
                    rec.screenshot_count = self.screenshots.len();
                }
                Task::none()
            }
            Message::DroppedFilesProcessed(summary) => {
                let mut tasks: Vec<Task<Message>> = summary
                    .mrpacks
                    .into_iter()
                    .map(|p| self.spawn_mrpack_import(p))
                    .collect();

                if let Some((th_id, th_name)) = summary.installed_theme {
                    self.installed_themes =
                        list_installed_themes(&self.paths.nightclient_themes_dir());
                    self.config.active_theme_id = th_id;
                    self.refresh_palette();
                    self.persist_config();
                    let msg = self.tr_fmt("toast.theme_imported", &[("name", &th_name)]);
                    self.show_toast(msg, false);
                }

                if let Some(err) = summary.first_error {
                    let msg = self.tr_fmt("toast.error_prefix", &[("err", &err)]);
                    self.show_toast(msg, true);
                } else if summary.added_names.len() == 1 {
                    let msg = self.tr_fmt(
                        "toast.mod_installed",
                        &[("name", &summary.added_names[0])],
                    );
                    self.show_toast(msg, false);
                } else if summary.added_names.len() > 1 {
                    let count = summary.added_names.len().to_string();
                    let msg = self.tr_fmt("toast.files_added", &[("count", &count)]);
                    self.show_toast(msg, false);
                } else if summary.missing_instance {
                    self.show_toast(self.tr("toast.drop_no_instance").to_string(), true);
                } else if summary.unsupported_count > 0 && tasks.is_empty() {
                    self.show_toast(self.tr("toast.drop_unsupported").to_string(), true);
                }

                if !summary.added_names.is_empty() {
                    tasks.push(self.spawn_refresh_selected_instance());
                }
                Task::batch(tasks)
            }
            Message::ContentFilterChanged(q) => {
                self.content_filter = q;
                Task::none()
            }
            Message::ToggleModRowHover(file) => {
                self.hovered_content_file = file;
                Task::none()
            }
            Message::ToggleContentFile(path) => {
                if let Err(e) = toggle_mod_enabled(&path) {
                    let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                    self.show_toast(msg, true);
                }
                self.spawn_refresh_selected_instance()
            }
            Message::ImportContentFilePick(id) => Task::perform(
                async move {
                    let files = rfd::AsyncFileDialog::new()
                        .add_filter("Minecraft Content", &["jar", "zip", "mrpack", "nc"])
                        .pick_files()
                        .await
                        .unwrap_or_default()
                        .into_iter()
                        .map(|f| f.path().to_path_buf())
                        .collect();
                    (id, files)
                },
                |(id, files)| Message::ImportContentFilesPicked(id, files),
            ),
            Message::ImportContentFilesPicked(id, files) => {
                if !files.is_empty() {
                    return self.handle_dropped_paths(Some(&id), files);
                }
                Task::none()
            }
            Message::CheckContentUpdates(id) => {
                let Some(inst) = self.instances.iter().find(|i| i.config.id == id).cloned() else {
                    return Task::none();
                };
                self.checking_updates = true;
                let net = self.net.clone();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        let candidates = check_mod_updates(
                            &net,
                            &inst.dir,
                            &inst.config.mc_version,
                            inst.config.loader.id(),
                            &cancel,
                        )
                        .await
                        .map_err(|e| e.to_string())?;
                        let mut updated = 0usize;
                        for cand in candidates {
                            if install_modrinth_project(
                                &net,
                                &inst.dir,
                                &cand.project_id,
                                ContentKind::Mod,
                                &inst.config.mc_version,
                                inst.config.loader.id(),
                                &cancel,
                            )
                            .await
                            .is_ok()
                            {
                                updated += 1;
                            }
                        }
                        Ok(updated)
                    },
                    Message::ContentUpdatesFinished,
                )
            }
            Message::ContentUpdatesFinished(res) => {
                self.checking_updates = false;
                match res {
                    Ok(0) => {
                        self.show_toast(self.tr("toast.mods_up_to_date").to_string(), false);
                    }
                    Ok(n) => {
                        let msg = self.tr_fmt("toast.mods_updated", &[("count", &n.to_string())]);
                        self.show_toast(msg, false);
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                self.spawn_refresh_selected_instance()
            }
            Message::InstallFpsPack(id) => {
                let Some(inst) = self.instances.iter().find(|i| i.config.id == id).cloned() else {
                    return Task::none();
                };
                self.installing_fps_pack = true;
                let net = self.net.clone();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        let (installed, _skipped) = install_optimization_pack(
                            &net,
                            &inst.dir,
                            &inst.config.mc_version,
                            inst.config.loader.id(),
                            &cancel,
                        )
                        .await;
                        installed
                    },
                    Message::FpsPackFinished,
                )
            }
            Message::FpsPackFinished(files) => {
                self.installing_fps_pack = false;
                let msg = self.tr_fmt(
                    "toast.fps_pack_installed",
                    &[("count", &files.len().to_string())],
                );
                self.show_toast(msg, false);
                self.spawn_refresh_selected_instance()
            }

            Message::SetBrowseModrinth(active) => {
                self.browse_content_active = active;
                self.browse_detail = None;
                self.browse_detail_loading = false;
                self.browse_detail_blocks.clear();
                self.browse_detail_versions.clear();
                self.browse_detail_expanded.clear();
                if !active {
                    self.content_filter.clear();
                    return self.spawn_refresh_selected_instance();
                } else if self.browse_hits.is_empty() {
                    return self.trigger_modrinth_search();
                }
                Task::none()
            }
            Message::BrowseQueryChanged(q) => {
                self.browse_query = q;
                Task::none()
            }
            Message::BrowseSortChanged(sort) => {
                self.browse_sort = sort;
                self.trigger_modrinth_search()
            }
            Message::BrowseSearchSubmit => self.trigger_modrinth_search(),
            Message::BrowseSearchFinished(res) => {
                self.browse_loading = false;
                match res {
                    Ok(hits) => {
                        let mut icon_tasks = Vec::new();
                        for hit in hits.iter().take(18) {
                            if let Some(ref url) = hit.icon_url
                                && !self.project_icons.contains_key(&hit.project_id)
                            {
                                let net = self.net.clone();
                                let cache_dir = self.paths.icons_cache_dir();
                                let pid = hit.project_id.clone();
                                let icon_url = url.clone();
                                icon_tasks.push(Task::perform(
                                    async move {
                                        let cancel = CancellationToken::new();
                                        let p = fetch_project_icon(
                                            &net, &cache_dir, &pid, &icon_url, &cancel,
                                        )
                                        .await;
                                        (pid, p)
                                    },
                                    |(pid, p)| Message::ProjectIconLoaded(pid, p),
                                ));
                            }
                        }
                        self.browse_hits = hits;
                        return Task::batch(icon_tasks);
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::ProjectIconLoaded(pid, path) => {
                if let Some(p) = path {
                    self.project_icons.insert(pid, p);
                }
                Task::none()
            }
            Message::BrowseOpenProject(pid) => {
                self.browse_detail = None;
                self.browse_detail_loading = true;
                self.browse_detail_blocks.clear();
                self.browse_detail_versions.clear();
                self.browse_detail_versions_loading = true;
                self.browse_detail_expanded.clear();
                self.browse_detail_gallery_idx = 0;
                self.browse_detail_tab = ModDetailsTab::Description;

                let net_d = self.net.clone();
                let pid_d = pid.clone();
                let detail_task = Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        get_project_details(&net_d, &pid_d, &cancel)
                            .await
                            .map_err(|e| e.to_string())
                    },
                    Message::BrowseProjectLoaded,
                );

                let kind = self.current_content_kind();
                let (mc_ver, loader) = self
                    .selected_instance()
                    .map(|i| {
                        let l = if i.config.loader == LoaderKind::Vanilla {
                            None
                        } else {
                            Some(i.config.loader.id().to_string())
                        };
                        (Some(i.config.mc_version.clone()), l)
                    })
                    .unwrap_or((None, None));
                let net_v = self.net.clone();
                let pid_v = pid.clone();
                let versions_task = Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        let res = fetch_project_versions(
                            &net_v,
                            &pid_v,
                            kind,
                            mc_ver.as_deref(),
                            loader.as_deref(),
                            &cancel,
                        )
                        .await
                        .map_err(|e| e.to_string());
                        (pid_v, res)
                    },
                    |(p, res)| Message::BrowseVersionsLoaded(p, res),
                );

                Task::batch([detail_task, versions_task])
            }
            Message::BrowseProjectLoaded(res) => {
                self.browse_detail_loading = false;
                match res {
                    Ok(detail) => {
                        let blocks = parse_modrinth_markdown(&detail.body);
                        let mut urls_to_fetch: Vec<String> = extract_image_urls(&blocks);
                        if let Some(ref icon_url) = detail.icon_url
                            && !urls_to_fetch.contains(icon_url)
                        {
                            urls_to_fetch.push(icon_url.clone());
                        }
                        for g in detail.gallery.iter().take(10) {
                            if !urls_to_fetch.contains(&g.url) {
                                urls_to_fetch.push(g.url.clone());
                            }
                        }
                        self.browse_detail_blocks = blocks;
                        self.browse_detail = Some(detail);
                        let tasks = self.queue_remote_image_fetches(urls_to_fetch);
                        return Task::batch(tasks);
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::BrowseVersionsLoaded(pid, res) => {
                self.browse_detail_versions_loading = false;
                if self
                    .browse_detail
                    .as_ref()
                    .is_none_or(|d| d.id == pid || d.slug == pid)
                    && let Ok(versions) = res
                {
                    self.browse_detail_versions = versions;
                }
                Task::none()
            }
            Message::BrowseSelectDetailTab(tab) => {
                self.browse_detail_tab = tab;
                Task::none()
            }
            Message::BrowseSelectGalleryIndex(idx) => {
                self.browse_detail_gallery_idx = idx;
                Task::none()
            }
            Message::BrowseToggleMarkdownDetails(id) => {
                if !self.browse_detail_expanded.remove(&id) {
                    self.browse_detail_expanded.insert(id);
                }
                Task::none()
            }
            Message::RemoteImageLoaded(url, loaded) => {
                self.remote_images_inflight.remove(&url);
                if let Some((bytes, width, height)) = loaded {
                    self.remote_images.insert(
                        url,
                        RemoteImage {
                            handle: image::Handle::from_bytes(bytes),
                            width,
                            height,
                        },
                    );
                }
                Task::none()
            }
            Message::BrowseCloseProject => {
                self.browse_detail = None;
                self.browse_detail_loading = false;
                self.browse_detail_blocks.clear();
                self.browse_detail_versions.clear();
                self.browse_detail_expanded.clear();
                self.browse_detail_gallery_idx = 0;
                self.browse_detail_tab = ModDetailsTab::Description;
                Task::none()
            }
            Message::BrowseInstallProject(pid, kind) => {
                let Some(inst) = self.selected_instance().cloned() else {
                    return Task::none();
                };
                self.installing_projects.insert(pid.clone(), true);
                let net = self.net.clone();
                if kind == ContentKind::Modpack {
                    let cache_dir = self.paths.cache_dir();
                    let paths = self.paths.clone();
                    let pid_clone = pid.clone();
                    return Task::perform(
                        async move {
                            let cancel = CancellationToken::new();
                            let mrpack = download_modpack_mrpack(
                                &net,
                                &cache_dir,
                                &pid_clone,
                                None,
                                &cancel,
                            )
                            .await
                            .map_err(|e| e.to_string())?;
                            let rec =
                                import_mrpack_into_new_instance(&net, &paths, &mrpack, &cancel)
                                    .await;
                            let _ = std::fs::remove_file(&mrpack);
                            rec
                        },
                        Message::MrpackImported,
                    );
                }

                let pid_out = pid.clone();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        let res = install_modrinth_project(
                            &net,
                            &inst.dir,
                            &pid,
                            kind,
                            &inst.config.mc_version,
                            inst.config.loader.id(),
                            &cancel,
                        )
                        .await
                        .map_err(|e| e.to_string());
                        (pid_out, res)
                    },
                    |(pid, res)| Message::BrowseInstallFinished(pid, res),
                )
            }
            Message::BrowseInstallVersion {
                project_id,
                version,
                kind,
            } => {
                let Some(inst) = self.selected_instance().cloned() else {
                    return Task::none();
                };
                let key = version.id.clone();
                self.installing_projects.insert(project_id.clone(), true);
                self.installing_projects.insert(key.clone(), true);
                let net = self.net.clone();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        let res = install_modrinth_version(
                            &net,
                            &inst.dir,
                            &version,
                            kind,
                            &inst.config.mc_version,
                            inst.config.loader.id(),
                            &cancel,
                        )
                        .await
                        .map_err(|e| e.to_string());
                        (key, res)
                    },
                    |(k, res)| Message::BrowseInstallFinished(k, res),
                )
            }
            Message::BrowseInstallFinished(pid, res) => {
                self.installing_projects.remove(&pid);
                if let Some(ref detail) = self.browse_detail {
                    self.installing_projects.remove(&detail.id);
                }
                match res {
                    Ok(files) => {
                        if let Some(first) = files.first() {
                            let msg = self.tr_fmt("toast.mod_installed", &[("name", first)]);
                            self.show_toast(msg, false);
                        }
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                self.spawn_refresh_selected_instance()
            }
            Message::MarkdownLinkClicked(url) => {
                open_url_safe(url);
                Task::none()
            }

            Message::RefreshScreenshots => self.spawn_refresh_selected_instance(),
            Message::StepLightboxScreenshot(delta) => {
                if let Some(ActiveModal::ScreenshotLightbox(ref current)) = self.modal
                    && !self.screenshots.is_empty()
                    && let Some(idx) = self.screenshots.iter().position(|s| &s.path == current)
                {
                    let len = self.screenshots.len() as isize;
                    let next = ((idx as isize + delta).rem_euclid(len)) as usize;
                    self.modal = Some(ActiveModal::ScreenshotLightbox(
                        self.screenshots[next].path.clone(),
                    ));
                }
                Task::none()
            }
            Message::CopyScreenshot(path) => Task::perform(
                async move {
                    tokio::task::spawn_blocking(move || copy_image_to_clipboard(&path))
                        .await
                        .unwrap_or_else(|e| Err(e.to_string()))
                },
                Message::CopyScreenshotFinished,
            ),
            Message::CopyScreenshotFinished(res) => {
                match res {
                    Ok(()) => self.show_toast(self.tr("toast.copied").to_string(), false),
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::OpenScreenshotsFolder(id) => {
                if let Some(inst) = self.instances.iter().find(|i| i.config.id == id) {
                    let dir = inst.dir.join("screenshots");
                    let _ = std::fs::create_dir_all(&dir);
                    open_detached(dir.as_os_str());
                }
                Task::none()
            }
            Message::ConsoleFilterChanged(q) => {
                self.console_filter = q;
                Task::none()
            }
            Message::ClearConsole(id) => {
                self.logs.remove(&id);
                Task::none()
            }
            Message::CopyConsole(id) => {
                let text = self
                    .logs
                    .get(&id)
                    .map(|l| l.join("\n"))
                    .unwrap_or_default();
                copy_text_to_clipboard(&text);
                self.show_toast(self.tr("toast.copied").to_string(), false);
                Task::none()
            }

            Message::SelectNcTheme(id) => {
                self.config.active_theme_id = id;
                self.refresh_palette();
                self.persist_config();
                Task::none()
            }
            Message::ToggleSyncUiWithTheme(enabled) => {
                self.config.sync_ui_with_nc_theme = enabled;
                self.refresh_palette();
                self.persist_config();
                Task::none()
            }
            Message::ImportNcThemePick => Task::perform(
                async move {
                    rfd::AsyncFileDialog::new()
                        .add_filter("NightClient Theme", &["nc"])
                        .pick_file()
                        .await
                        .map(|f| f.path().to_path_buf())
                },
                Message::ImportNcThemePicked,
            ),
            Message::ImportNcThemePicked(path) => {
                if let Some(path) = path {
                    match install_nc_theme_file(&path, &self.paths.nightclient_themes_dir()) {
                        Ok(th) => {
                            let name = th.name.clone();
                            self.installed_themes =
                                list_installed_themes(&self.paths.nightclient_themes_dir());
                            self.config.active_theme_id = th.id;
                            self.refresh_palette();
                            self.persist_config();
                            let msg = self.tr_fmt("toast.theme_imported", &[("name", &name)]);
                            self.show_toast(msg, false);
                        }
                        Err(e) => {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }

            Message::SelectAccount(id) => {
                self.account_book.active_account_id = Some(id);
                let _ = save_accounts(&self.paths, &self.account_book);
                self.account_popover_open = false;
                Task::none()
            }
            Message::OfflineNameChanged(name) => {
                self.offline_name_input = name;
                Task::none()
            }
            Message::SubmitOfflineAccount => {
                match create_offline_account(&self.offline_name_input) {
                    Ok(acc) => {
                        let name = acc.username.clone();
                        let id = acc.id.clone();
                        let acc_clone = acc.clone();
                        self.upsert_account(acc);
                        self.offline_name_input.clear();
                        self.account_popover_open = false;
                        let msg = self.tr_fmt("toast.account_added", &[("name", &name)]);
                        self.show_toast(msg, false);
                        let net = self.net.clone();
                        let skins = self.paths.skins_cache_dir();
                        return Task::perform(
                            async move {
                                let cancel = CancellationToken::new();
                                let p = fetch_avatar_png(&net, &skins, &acc_clone, &cancel).await;
                                let bytes = match p {
                                    Some(path) => tokio::task::spawn_blocking(move || {
                                        std::fs::read(&path).ok()
                                    })
                                    .await
                                    .ok()
                                    .flatten(),
                                    None => None,
                                };
                                (id, bytes)
                            },
                            |(id, bytes)| Message::AvatarLoaded(id, bytes),
                        );
                    }
                    Err(e) => {
                        let msg = self.tr_fmt("toast.error_prefix", &[("err", &e.to_string())]);
                        self.show_toast(msg, true);
                    }
                }
                Task::none()
            }
            Message::StartMicrosoftBrowserLogin => {
                let Some(cid) = resolve_client_id(self.config.ms_client_id.as_deref()) else {
                    self.show_toast(self.tr("toast.ms_client_id_missing").to_string(), true);
                    return Task::none();
                };
                self.account_popover_open = false;
                self.ms_login_busy = true;
                self.device_code_prompt = None;
                let cancel = CancellationToken::new();
                self.ms_login_cancel = Some(cancel.clone());
                let net = self.net.clone();
                let secrets = SecretStore::new(self.paths.vault_file());
                Task::perform(
                    async move {
                        run_pkce_browser_login(
                            &net,
                            &secrets,
                            &cid,
                            |url| {
                                open_detached(url);
                            },
                            &cancel,
                        )
                        .await
                        .map_err(|e| e.to_string())
                    },
                    Message::MicrosoftLoginFinished,
                )
            }
            Message::StartMicrosoftDeviceLogin => {
                let Some(cid) = resolve_client_id(self.config.ms_client_id.as_deref()) else {
                    self.show_toast(self.tr("toast.ms_client_id_missing").to_string(), true);
                    return Task::none();
                };
                self.account_popover_open = false;
                self.ms_login_busy = true;
                let cancel = CancellationToken::new();
                self.ms_login_cancel = Some(cancel.clone());
                let net = self.net.clone();
                Task::perform(
                    async move {
                        start_device_code_login(&net, &cid, &cancel)
                            .await
                            .map_err(|e| e.to_string())
                    },
                    Message::DeviceCodeReady,
                )
            }
            Message::CancelMicrosoftLogin => {
                if let Some(c) = self.ms_login_cancel.take() {
                    c.cancel();
                }
                self.ms_login_busy = false;
                self.device_code_prompt = None;
                Task::none()
            }
            Message::DeviceCodeReady(res) => match res {
                Ok(prompt) => {
                    self.device_code_prompt = Some(prompt.clone());
                    let Some(cid) = resolve_client_id(self.config.ms_client_id.as_deref()) else {
                        return Task::none();
                    };
                    let cancel = self.ms_login_cancel.clone().unwrap_or_default();
                    let net = self.net.clone();
                    let secrets = SecretStore::new(self.paths.vault_file());
                    Task::perform(
                        async move {
                            complete_device_code_login(&net, &secrets, &cid, &prompt, &cancel)
                                .await
                                .map_err(|e| e.to_string())
                        },
                        Message::MicrosoftLoginFinished,
                    )
                }
                Err(e) => {
                    self.ms_login_busy = false;
                    let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                    self.show_toast(msg, true);
                    Task::none()
                }
            },
            Message::MicrosoftLoginFinished(res) => {
                self.ms_login_busy = false;
                self.device_code_prompt = None;
                self.ms_login_cancel = None;
                match res {
                    Ok((acc, backend)) => {
                        self.secret_backend = backend;
                        let name = acc.username.clone();
                        let id = acc.id.clone();
                        let acc_clone = acc.clone();
                        self.upsert_account(acc);
                        let msg = self.tr_fmt("toast.account_added", &[("name", &name)]);
                        self.show_toast(msg, false);
                        let net = self.net.clone();
                        let skins = self.paths.skins_cache_dir();
                        return Task::perform(
                            async move {
                                let cancel = CancellationToken::new();
                                let p = fetch_avatar_png(&net, &skins, &acc_clone, &cancel).await;
                                let bytes = match p {
                                    Some(path) => tokio::task::spawn_blocking(move || {
                                        std::fs::read(&path).ok()
                                    })
                                    .await
                                    .ok()
                                    .flatten(),
                                    None => None,
                                };
                                (id, bytes)
                            },
                            |(id, bytes)| Message::AvatarLoaded(id, bytes),
                        );
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::AvatarLoaded(id, bytes) => {
                if let Some(bytes) = bytes {
                    self.avatar_handles
                        .insert(id, image::Handle::from_bytes(bytes));
                }
                Task::none()
            }

            Message::SelectLocale(meta) => {
                let actual = self.i18n.set_locale(&meta.code);
                self.config.locale = actual;
                self.persist_config();
                Task::none()
            }
            Message::SetThemeMode(mode) => {
                self.config.theme_mode = mode;
                self.refresh_palette();
                self.persist_config();
                blur::apply_window_blur(
                    self.window_size.width as i32,
                    self.window_size.height as i32,
                    self.corner_radius as i32,
                    self.palette.is_dark,
                    Message::BlurApplied,
                )
            }
            Message::SetDefaultMemoryMb(mb) => {
                self.config.default_memory_mb = mb;
                self.persist_config();
                Task::none()
            }
            Message::SetDefaultProfile(prof) => {
                self.config.performance_profile = prof;
                self.persist_config();
                Task::none()
            }
            Message::ToggleDefaultTurbo(turbo) => {
                self.config.default_turbo_mode = turbo;
                self.persist_config();
                Task::none()
            }
            Message::SetDefaultJavaLabel(label) => {
                self.config.default_java_path = self.resolve_java_label_to_path(&label);
                self.persist_config();
                Task::none()
            }
            Message::SetExtraJvmArgs(args) => {
                self.config.extra_jvm_args = args;
                self.persist_config();
                Task::none()
            }
            Message::SetExtraGameArgs(args) => {
                self.config.extra_game_args = args;
                self.persist_config();
                Task::none()
            }
            #[cfg(target_os = "linux")]
            Message::SetGpuPreference(gpu) => {
                self.config.gpu_preference = gpu;
                self.persist_config();
                Task::none()
            }
            #[cfg(target_os = "linux")]
            Message::ToggleGamemode(v) => {
                self.config.use_gamemode = v;
                self.persist_config();
                Task::none()
            }
            #[cfg(target_os = "linux")]
            Message::ToggleMangohud(v) => {
                self.config.use_mangohud = v;
                self.persist_config();
                Task::none()
            }
            Message::ToggleMinimizeOnLaunch(v) => {
                self.config.minimize_on_launch = v;
                self.persist_config();
                Task::none()
            }
            Message::SetNightClientChannel(ch) => {
                self.config.nightclient_channel = ch;
                self.persist_config();
                self.update(Message::CheckLauncherUpdates(false))
            }
            Message::CheckLauncherUpdates(manual) => {
                if self.checking_launcher_update {
                    return Task::none();
                }
                self.checking_launcher_update = true;
                self.launcher_update_error = None;
                let net = self.net.clone();
                let paths = self.paths.clone();
                let ch = self.config.nightclient_channel;
                let cur_ver = self.launcher_version();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        fetch_github_releases_bundle(&net, &paths, ch, cur_ver, &cancel).await
                    },
                    move |bundle| Message::GitHubReleasesLoaded { bundle, manual },
                )
            }
            Message::GitHubReleasesLoaded { bundle, manual } => {
                self.checking_launcher_update = false;
                self.night_manifest = bundle.manifest;
                self.launcher_update = bundle.launcher_update;
                self.update_changelog_expanded.clear();
                self.update_changelog_blocks = match &self.launcher_update {
                    Some(info) if !info.changelog.trim().is_empty() => {
                        parse_modrinth_markdown(&info.changelog)
                    }
                    _ => Vec::new(),
                };
                if manual {
                    if !bundle.fetched_online {
                        self.show_toast(self.tr("toast.update_offline").to_string(), true);
                    } else if let Some(ref info) = self.launcher_update {
                        if info.is_update_available {
                            if self.config.dismissed_update_tag.as_deref()
                                == Some(info.tag_name.as_str())
                            {
                                self.config.dismissed_update_tag = None;
                                self.persist_config();
                            }
                            let msg = self.tr_fmt(
                                "toast.update_found",
                                &[("version", &info.latest_version)],
                            );
                            self.show_toast(msg, false);
                            self.modal = Some(ActiveModal::LauncherUpdate);
                        } else {
                            let msg = self.tr_fmt(
                                "toast.update_up_to_date",
                                &[("version", self.launcher_version())],
                            );
                            self.show_toast(msg, false);
                        }
                    } else {
                        let msg = self.tr_fmt(
                            "toast.update_up_to_date",
                            &[("version", self.launcher_version())],
                        );
                        self.show_toast(msg, false);
                    }
                }
                Task::none()
            }
            Message::OpenLauncherUpdateModal => {
                self.account_popover_open = false;
                self.launcher_update_error = None;
                self.modal = Some(ActiveModal::LauncherUpdate);
                if self.launcher_update.is_none() && !self.checking_launcher_update {
                    return self.update(Message::CheckLauncherUpdates(false));
                }
                Task::none()
            }
            Message::DismissLauncherUpdate(tag) => {
                self.config.dismissed_update_tag = Some(tag);
                self.persist_config();
                if matches!(self.modal, Some(ActiveModal::LauncherUpdate)) {
                    self.modal = None;
                }
                Task::none()
            }
            Message::ToggleChangelogDetails(idx) => {
                if !self.update_changelog_expanded.remove(&idx) {
                    self.update_changelog_expanded.insert(idx);
                }
                Task::none()
            }
            Message::StartLauncherUpdate => {
                if self.installing_launcher_update {
                    return Task::none();
                }
                let Some(info) = self.launcher_update.clone() else {
                    return Task::none();
                };
                self.installing_launcher_update = true;
                self.launcher_update_progress = 0.0;
                self.launcher_update_error = None;
                let cancel = CancellationToken::new();
                self.update_cancel = Some(cancel.clone());
                let net = self.net.clone();
                Task::stream(iced::stream::channel(
                    32,
                    move |mut output: iced::futures::channel::mpsc::Sender<Message>| async move {
                        let mut last_pct: i32 = -1;
                        let res = download_and_apply_launcher_update(
                            &net,
                            &info,
                            &cancel,
                            |done, total| {
                                let frac = if total > 0 {
                                    (done as f32 / total as f32).clamp(0.0, 1.0)
                                } else {
                                    0.0
                                };
                                let pct = (frac * 100.0) as i32;
                                if pct != last_pct {
                                    last_pct = pct;
                                    let _ = output.try_send(Message::LauncherUpdateProgress(frac));
                                }
                            },
                        )
                        .await
                        .map_err(|e| e.to_string());
                        let _ = output.send(Message::LauncherUpdateFinished(res)).await;
                    },
                ))
            }
            Message::LauncherUpdateProgress(frac) => {
                self.launcher_update_progress = frac.clamp(0.0, 1.0);
                Task::none()
            }
            Message::LauncherUpdateFinished(res) => {
                self.installing_launcher_update = false;
                self.update_cancel = None;
                match res {
                    Ok(exe) => {
                        let _ = std::process::Command::new(&exe)
                            .arg("--restarted")
                            .spawn();
                        std::process::exit(0);
                    }
                    Err(err) => {
                        self.launcher_update_error = Some(err.clone());
                        let msg = self.tr_fmt("toast.error_prefix", &[("err", &err)]);
                        self.show_toast(msg, true);
                        Task::none()
                    }
                }
            }
            Message::SetProxyUrl(url) => {
                let trimmed = url.trim().to_string();
                self.config.net.proxy_url = if trimmed.is_empty() { None } else { Some(trimmed) };
                if let Ok(client) = NetClient::new(self.config.net.clone()) {
                    self.net = client;
                }
                self.persist_config();
                Task::none()
            }
            Message::InstallJavaMajor(major) => {
                self.installing_java_major = Some(major);
                let net = self.net.clone();
                let runtimes_dir = self.paths.runtimes_dir();
                Task::perform(
                    async move {
                        let cancel = CancellationToken::new();
                        let res = install_java_runtime(&net, &runtimes_dir, major, &cancel, |_, _| {})
                            .await
                            .map_err(|e| e.to_string());
                        (major, res)
                    },
                    |(major, res)| Message::JavaInstalled(major, res),
                )
            }
            Message::JavaInstalled(major, res) => {
                self.installing_java_major = None;
                match res {
                    Ok(rt) => {
                        if !self.java_runtimes.iter().any(|r| r.path == rt.path) {
                            self.java_runtimes.push(rt);
                            self.java_runtimes.sort_by_key(|a| std::cmp::Reverse(a.major));
                        }
                        let msg = self.tr_fmt(
                            "toast.java_installed",
                            &[("major", &major.to_string())],
                        );
                        self.show_toast(msg, false);
                    }
                    Err(e) => {
                        if !e.eq_ignore_ascii_case("login cancelled") {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::InstallDesktopShortcut => {
                if let Ok(exe) = std::env::current_exe() {
                    match install_app_shortcut(&exe, APP_ICON_PNG) {
                        Ok(_) => {
                            self.show_toast(self.tr("toast.shortcut_installed").to_string(), false);
                        }
                        Err(e) => {
                            let msg = self.tr_fmt("toast.error_prefix", &[("err", &e)]);
                            self.show_toast(msg, true);
                        }
                    }
                }
                Task::none()
            }
            Message::OpenDataFolder => {
                open_detached(self.paths.root.as_os_str());
                Task::none()
            }
            Message::OpenLocalesFolder => {
                let dir = self.paths.locales_dir();
                let _ = std::fs::create_dir_all(&dir);
                open_detached(dir.as_os_str());
                Task::none()
            }

            Message::JavaScanned(runtimes) => {
                self.java_runtimes = runtimes;
                Task::none()
            }
            Message::MojangVersionsLoaded(res) => {
                self.mojang_loading = false;
                if let Ok(versions) = res {
                    self.mojang_versions = versions;
                }
                Task::none()
            }
            Message::SystemEvent(ev) => match ev {
                Event::Window(window::Event::Resized(size)) => {
                    self.update(Message::WindowResized(size))
                }
                Event::Window(window::Event::Focused) => {
                    if self.last_focus_refresh.elapsed() >= Duration::from_millis(1500) {
                        self.last_focus_refresh = Instant::now();
                        return self.spawn_refresh_selected_instance();
                    }
                    Task::none()
                }
                Event::Window(window::Event::FileHovered(_)) => {
                    self.dragging_files = true;
                    Task::none()
                }
                Event::Window(window::Event::FilesHoveredLeft) => {
                    self.dragging_files = false;
                    Task::none()
                }
                Event::Window(window::Event::FileDropped(path)) => {
                    self.dragging_files = false;
                    self.handle_dropped_paths(None, vec![path])
                }
                Event::Keyboard(keyboard::Event::KeyPressed {
                    key: keyboard::Key::Named(keyboard::key::Named::ArrowLeft),
                    ..
                }) => {
                    if matches!(self.modal, Some(ActiveModal::ScreenshotLightbox(_))) {
                        return self.update(Message::StepLightboxScreenshot(-1));
                    }
                    if self.browse_detail.is_some()
                        && self.browse_detail_tab == ModDetailsTab::Gallery
                        && let Some(ref d) = self.browse_detail
                        && d.gallery.len() > 1
                    {
                        let len = d.gallery.len();
                        let next = (self.browse_detail_gallery_idx + len - 1) % len;
                        self.browse_detail_gallery_idx = next;
                    }
                    Task::none()
                }
                Event::Keyboard(keyboard::Event::KeyPressed {
                    key: keyboard::Key::Named(keyboard::key::Named::ArrowRight),
                    ..
                }) => {
                    if matches!(self.modal, Some(ActiveModal::ScreenshotLightbox(_))) {
                        return self.update(Message::StepLightboxScreenshot(1));
                    }
                    if self.browse_detail.is_some()
                        && self.browse_detail_tab == ModDetailsTab::Gallery
                        && let Some(ref d) = self.browse_detail
                        && d.gallery.len() > 1
                    {
                        let len = d.gallery.len();
                        let next = (self.browse_detail_gallery_idx + 1) % len;
                        self.browse_detail_gallery_idx = next;
                    }
                    Task::none()
                }
                Event::Keyboard(keyboard::Event::KeyPressed {
                    key: keyboard::Key::Named(keyboard::key::Named::Escape),
                    ..
                }) => {
                    if self.modal.is_some() {
                        self.modal = None;
                    } else if self.ms_login_busy {
                        return self.update(Message::CancelMicrosoftLogin);
                    } else if self.servers_popover_open {
                        self.servers_popover_open = false;
                    } else if self.account_popover_open {
                        self.account_popover_open = false;
                    } else if self.browse_detail.is_some() || self.browse_detail_loading {
                        return self.update(Message::BrowseCloseProject);
                    } else if self.browse_content_active {
                        return self.update(Message::SetBrowseModrinth(false));
                    } else if self.page == Page::Settings {
                        self.page = Page::Workspace;
                    }
                    Task::none()
                }
                _ => Task::none(),
            },
            #[cfg(target_os = "linux")]
            Message::WaylandDnd(ev) => match ev {
                smithay_clipboard::WaylandDndEvent::Hovered => {
                    self.dragging_files = true;
                    Task::none()
                }
                smithay_clipboard::WaylandDndEvent::Left => {
                    self.dragging_files = false;
                    Task::none()
                }
                smithay_clipboard::WaylandDndEvent::Dropped(paths) => {
                    self.dragging_files = false;
                    self.handle_dropped_paths(None, paths)
                }
            },
            Message::CopyText(s) => {
                copy_text_to_clipboard(&s);
                self.show_toast(self.tr("toast.copied").to_string(), false);
                Task::none()
            }
            Message::OpenUrl(url) => {
                open_url_safe(url);
                Task::none()
            }
            Message::DismissToast => {
                self.toast = None;
                Task::none()
            }

            Message::ToggleServersPopover => {
                self.account_popover_open = false;
                self.servers_popover_open = !self.servers_popover_open;
                Task::none()
            }
            Message::CloseServersPopover => {
                self.servers_popover_open = false;
                Task::none()
            }

            Message::OpenMigrationWizard => {
                self.account_popover_open = false;
                self.servers_popover_open = false;
                self.modal = None;
                let mut st = WelcomeState::intro();
                st.step = WelcomeStep::Scanning;
                self.welcome = Some(st);
                Task::perform(
                    async move {
                        tokio::task::spawn_blocking(discover_external_launchers)
                            .await
                            .unwrap_or_default()
                    },
                    Message::WelcomeScanFinished,
                )
            }
            Message::WelcomeBackToIntro => {
                if let Some(ref mut w) = self.welcome {
                    w.step = WelcomeStep::Intro;
                }
                Task::none()
            }
            Message::WelcomeSkip => {
                self.welcome = None;
                if !self.config.onboarding_completed {
                    self.config.onboarding_completed = true;
                    self.persist_config();
                }
                Task::none()
            }
            Message::WelcomeStartScan => {
                if let Some(ref mut w) = self.welcome {
                    w.step = WelcomeStep::Scanning;
                }
                Task::perform(
                    async move {
                        tokio::task::spawn_blocking(discover_external_launchers)
                            .await
                            .unwrap_or_default()
                    },
                    Message::WelcomeScanFinished,
                )
            }
            Message::WelcomeScanFinished(detected) => {
                if let Some(ref mut w) = self.welcome {
                    w.detected = detected;
                    w.selected_launcher = if w.detected.is_empty() { None } else { Some(0) };
                    w.selected_profile = w
                        .detected
                        .first()
                        .and_then(|l| if l.profiles.is_empty() { None } else { Some(0) });
                    w.sync_selection_from_current();
                    w.step = WelcomeStep::Pick;
                }
                Task::none()
            }
            Message::WelcomeBrowseFolder => Task::perform(
                async move {
                    rfd::AsyncFileDialog::new()
                        .pick_folder()
                        .await
                        .map(|h| h.path().to_path_buf())
                },
                Message::WelcomeFolderPicked,
            ),
            Message::WelcomeFolderPicked(path) => {
                let Some(path) = path else {
                    return Task::none();
                };
                Task::perform(
                    async move {
                        tokio::task::spawn_blocking(move || inspect_custom_folder(&path))
                            .await
                            .ok()
                            .flatten()
                    },
                    Message::WelcomeFolderInspected,
                )
            }
            Message::WelcomeFolderInspected(found) => {
                if let Some(det) = found
                    && let Some(ref mut w) = self.welcome
                {
                    let idx = w.detected.len();
                    w.detected.push(det);
                    w.selected_launcher = Some(idx);
                    w.selected_profile = w
                        .detected
                        .get(idx)
                        .and_then(|l| if l.profiles.is_empty() { None } else { Some(0) });
                    w.sync_selection_from_current();
                    w.step = WelcomeStep::Pick;
                }
                Task::none()
            }
            Message::WelcomeSelectLauncher(idx) => {
                if let Some(ref mut w) = self.welcome
                    && idx < w.detected.len()
                {
                    w.selected_launcher = Some(idx);
                    w.selected_profile = if w.detected[idx].profiles.is_empty() {
                        None
                    } else {
                        Some(0)
                    };
                    w.sync_selection_from_current();
                }
                Task::none()
            }
            Message::WelcomeSelectProfile(pi) => {
                if let Some(ref mut w) = self.welcome
                    && let Some(li) = w.selected_launcher
                    && let Some(launcher) = w.detected.get(li)
                    && pi < launcher.profiles.len()
                {
                    w.selected_profile = Some(pi);
                    w.sync_selection_from_current();
                }
                Task::none()
            }
            Message::WelcomeCustomNameChanged(name) => {
                if let Some(ref mut w) = self.welcome {
                    w.selection.custom_name = name;
                }
                Task::none()
            }
            Message::WelcomeToggleOption(key) => {
                if let Some(ref mut w) = self.welcome {
                    match key {
                        "mods" => w.selection.copy_mods = !w.selection.copy_mods,
                        "config" => w.selection.copy_config = !w.selection.copy_config,
                        "resourcepacks" => {
                            w.selection.copy_resourcepacks = !w.selection.copy_resourcepacks;
                        }
                        "shaderpacks" => {
                            w.selection.copy_shaderpacks = !w.selection.copy_shaderpacks;
                        }
                        "options" => w.selection.copy_options_txt = !w.selection.copy_options_txt,
                        "servers" => w.selection.copy_servers_dat = !w.selection.copy_servers_dat,
                        "saves" => w.selection.copy_saves = !w.selection.copy_saves,
                        "screenshots" => {
                            w.selection.copy_screenshots = !w.selection.copy_screenshots;
                        }
                        "nightclient" => {
                            w.selection.enable_nightclient = !w.selection.enable_nightclient;
                        }
                        _ => {}
                    }
                }
                Task::none()
            }
            Message::WelcomeStartImport => {
                let Some((prof, mut sel)) = self.welcome.as_ref().and_then(|w| {
                    let prof = w
                        .selected_launcher
                        .and_then(|li| w.detected.get(li))
                        .and_then(|l| w.selected_profile.and_then(|pi| l.profiles.get(pi)))
                        .cloned()?;
                    Some((prof, w.selection.clone()))
                }) else {
                    return Task::none();
                };
                if !self.is_visuals_eligible(prof.loader, &prof.mc_version) {
                    sel.enable_nightclient = false;
                }
                let display_name = if sel.custom_name.trim().is_empty() {
                    prof.name.clone()
                } else {
                    sel.custom_name.trim().to_string()
                };
                if let Some(ref mut w) = self.welcome {
                    w.step = WelcomeStep::Importing { name: display_name };
                }
                let paths = self.paths.clone();
                Task::perform(
                    async move {
                        tokio::task::spawn_blocking(move || {
                            execute_migration(&paths, &prof, &sel).map_err(|e| e.to_string())
                        })
                        .await
                        .unwrap_or_else(|e| Err(e.to_string()))
                    },
                    Message::WelcomeImportFinished,
                )
            }
            Message::WelcomeImportFinished(res) => match res {
                Ok((rec, report)) => {
                    let id = rec.config.id.clone();
                    self.instances.insert(0, rec);
                    self.sort_instances();
                    self.selected_instance_id = Some(id.clone());
                    self.config.selected_instance_id = Some(id.clone());
                    self.config.onboarding_completed = true;
                    self.persist_config();
                    self.sync_options_loader_draft();
                    if let Some(ref mut w) = self.welcome {
                        w.last_imported_id = Some(id);
                        w.step = WelcomeStep::Done(report);
                    }
                    self.spawn_refresh_selected_instance()
                }
                Err(err) => {
                    if let Some(ref mut w) = self.welcome {
                        w.step = WelcomeStep::Pick;
                    }
                    let msg = self.tr_fmt("toast.error_prefix", &[("err", &err)]);
                    self.show_toast(msg, true);
                    Task::none()
                }
            },
            Message::WelcomeImportAnother => {
                if let Some(ref mut w) = self.welcome {
                    w.step = WelcomeStep::Pick;
                }
                Task::none()
            }
            Message::WelcomeFinish => {
                self.welcome = None;
                self.page = Page::Workspace;
                if !self.config.onboarding_completed {
                    self.config.onboarding_completed = true;
                    self.persist_config();
                }
                self.spawn_refresh_selected_instance()
            }
        }
    }

    fn upsert_account(&mut self, acc: Account) {
        let id = acc.id.clone();
        if let Some(existing) = self.account_book.accounts.iter_mut().find(|a| a.id == id) {
            *existing = acc;
        } else {
            self.account_book.accounts.push(acc);
        }
        self.account_book.active_account_id = Some(id);
        let _ = save_accounts(&self.paths, &self.account_book);
    }

    fn start_launch_or_repair(
        &mut self,
        id: String,
        quick_play: Option<String>,
        repair_only: bool,
    ) -> Task<Message> {
        if self.statuses.get(&id).is_some_and(InstanceStatus::is_busy)
            || self.prepare_cancels.contains_key(&id)
            || self.running_pids.contains_key(&id)
        {
            return Task::none();
        }
        let Some(inst) = self.instances.iter().find(|i| i.config.id == id).cloned() else {
            return Task::none();
        };
        let Some(acc) = self.active_account().cloned() else {
            self.account_popover_open = true;
            self.show_toast(self.tr("toast.no_account").to_string(), true);
            return Task::none();
        };

        let active_nc_theme_path = self
            .installed_themes
            .iter()
            .find(|t| t.id == self.config.active_theme_id)
            .and_then(|t| t.path.clone());

        let cancel = CancellationToken::new();
        self.prepare_cancels.insert(id.clone(), cancel.clone());
        self.statuses.insert(
            id.clone(),
            InstanceStatus::Preparing {
                stage: LaunchStage::CheckingJava,
                progress: 0.0,
            },
        );
        let user_stopped = Arc::new(AtomicBool::new(false));
        self.user_stopped_flags
            .insert(id.clone(), user_stopped.clone());
        self.logs.entry(id.clone()).or_default().clear();

        let paths = self.paths.clone();
        let net = self.net.clone();
        let req = LaunchRequest {
            instance: inst,
            account: acc,
            config: self.config.clone(),
            night_manifest: self.night_manifest.clone(),
            options: LaunchOptions {
                quick_play_server: quick_play,
                active_nc_theme_path,
                repair_files: repair_only,
                repair_only,
            },
        };
        let tx = self.launch_tx.clone();
        tokio::spawn(async move {
            prepare_and_launch_instance(paths, net, req, cancel, user_stopped, tx).await;
        });

        if !repair_only && self.config.minimize_on_launch {
            return window::latest().and_then(|wid| window::minimize(wid, true));
        }
        Task::none()
    }

    fn trigger_modrinth_search(&mut self) -> Task<Message> {
        self.browse_loading = true;
        let kind = self.current_content_kind();
        let query_text = self.browse_query.clone();
        let sort = self.browse_sort;
        let (mc_ver, loader) = self
            .selected_instance()
            .map(|i| {
                let l = if i.config.loader == LoaderKind::Vanilla {
                    None
                } else {
                    Some(i.config.loader.id().to_string())
                };
                (Some(i.config.mc_version.clone()), l)
            })
            .unwrap_or((None, None));
        let net = self.net.clone();
        Task::perform(
            async move {
                let cancel = CancellationToken::new();
                search_modrinth(
                    &net,
                    &ModrinthSearchQuery {
                        query: query_text,
                        kind,
                        mc_version: mc_ver,
                        loader,
                        sort,
                        limit: 24,
                        offset: 0,
                    },
                    &cancel,
                )
                .await
                .map_err(|e| e.to_string())
            },
            Message::BrowseSearchFinished,
        )
    }

    fn handle_dropped_paths(
        &mut self,
        explicit_instance_id: Option<&str>,
        files: Vec<PathBuf>,
    ) -> Task<Message> {
        if files.is_empty() {
            return Task::none();
        }
        let target_inst_dir = explicit_instance_id
            .and_then(|id| self.instances.iter().find(|i| i.config.id == id))
            .or_else(|| self.selected_instance())
            .map(|i| i.dir.clone());
        let themes_dir = self.paths.nightclient_themes_dir();

        Task::perform(
            async move {
                tokio::task::spawn_blocking(move || {
                    process_dropped_files(target_inst_dir.as_deref(), &themes_dir, files)
                })
                .await
                .unwrap_or_default()
            },
            Message::DroppedFilesProcessed,
        )
    }

    fn spawn_mrpack_import(&self, mrpack_path: PathBuf) -> Task<Message> {
        let net = self.net.clone();
        let paths = self.paths.clone();
        Task::perform(
            async move {
                let cancel = CancellationToken::new();
                import_mrpack_into_new_instance(&net, &paths, &mrpack_path, &cancel).await
            },
            Message::MrpackImported,
        )
    }

    fn queue_remote_image_fetches(&mut self, urls: Vec<String>) -> Vec<Task<Message>> {
        let mut tasks = Vec::new();
        for url in urls.into_iter().take(24) {
            if self.remote_images.contains_key(&url)
                || !self.remote_images_inflight.insert(url.clone())
            {
                continue;
            }
            let net = self.net.clone();
            let cache_dir = self.paths.thumbnails_cache_dir();
            let u = url.clone();
            tasks.push(Task::perform(
                async move {
                    let cancel = CancellationToken::new();
                    let path = fetch_remote_image(&net, &cache_dir, &u, 960, &cancel).await;
                    let loaded = match path {
                        Some(p) => tokio::task::spawn_blocking(move || {
                            let bytes = std::fs::read(&p).ok()?;
                            let (w, h) = ::image::ImageReader::new(std::io::Cursor::new(&bytes))
                                .with_guessed_format()
                                .ok()
                                .and_then(|r| r.into_dimensions().ok())
                                .unwrap_or((0, 0));
                            Some((bytes, w, h))
                        })
                        .await
                        .ok()
                        .flatten(),
                        None => None,
                    };
                    (u, loaded)
                },
                |(u, loaded)| Message::RemoteImageLoaded(u, loaded),
            ));
        }
        tasks
    }

    pub fn installed_project_ids(&self) -> BTreeSet<String> {
        let mut ids: BTreeSet<String> = self
            .installed_mods
            .iter()
            .chain(self.installed_packs.iter())
            .chain(self.installed_shaders.iter())
            .filter(|f| !f.mod_id.is_empty())
            .map(|f| f.mod_id.clone())
            .collect();
        ids.extend(self.installed_registry_ids.iter().cloned());
        ids
    }
}

fn process_dropped_files(
    target_inst_dir: Option<&Path>,
    themes_dir: &Path,
    files: Vec<PathBuf>,
) -> DroppedFilesSummary {
    let mut summary = DroppedFilesSummary::default();

    for path in files {
        match classify_dropped_file(&path) {
            DroppedFileAction::ImportMrpack => summary.mrpacks.push(path),
            DroppedFileAction::InstallNightTheme => match install_nc_theme_file(&path, themes_dir) {
                Ok(th) => {
                    summary.installed_theme = Some((th.id, th.name));
                }
                Err(e) => {
                    if summary.first_error.is_none() {
                        summary.first_error = Some(e);
                    }
                }
            },
            DroppedFileAction::InstallMod => {
                install_dropped_into_subdir(target_inst_dir, "mods", &path, &mut summary);
            }
            DroppedFileAction::InstallResourcePack => {
                install_dropped_into_subdir(target_inst_dir, "resourcepacks", &path, &mut summary);
            }
            DroppedFileAction::InstallShaderPack => {
                install_dropped_into_subdir(target_inst_dir, "shaderpacks", &path, &mut summary);
            }
            DroppedFileAction::Unsupported => {
                summary.unsupported_count += 1;
            }
        }
    }

    summary
}

fn install_dropped_into_subdir(
    target_inst_dir: Option<&Path>,
    subdir: &str,
    src: &Path,
    summary: &mut DroppedFilesSummary,
) {
    let Some(inst_dir) = target_inst_dir else {
        summary.missing_instance = true;
        return;
    };
    let Some(fname) = src.file_name() else {
        return;
    };
    let dir = inst_dir.join(subdir);
    if let Err(e) = std::fs::create_dir_all(&dir) {
        if summary.first_error.is_none() {
            summary.first_error = Some(e.to_string());
        }
        return;
    }
    let dst = dir.join(fname);

    if let (Ok(cs), Ok(cd)) = (std::fs::canonicalize(src), std::fs::canonicalize(&dst))
        && cs == cd
    {
        summary.added_names.push(fname.to_string_lossy().to_string());
        return;
    }

    let copy_res = if src.is_dir() {
        copy_dir_all(src, &dst)
    } else {
        std::fs::copy(src, &dst).map(|_| ())
    };

    match copy_res {
        Ok(()) => summary.added_names.push(fname.to_string_lossy().to_string()),
        Err(e) => {
            if summary.first_error.is_none() {
                summary.first_error = Some(e.to_string());
            }
        }
    }
}

fn copy_dir_all(src: &Path, dst: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dst)?;
    for entry in std::fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        let target = dst.join(entry.file_name());
        if ty.is_dir() {
            copy_dir_all(&entry.path(), &target)?;
        } else if ty.is_file() {
            std::fs::copy(entry.path(), target)?;
        }
    }
    Ok(())
}

async fn import_mrpack_into_new_instance(
    net: &NetClient,
    paths: &LauncherPaths,
    mrpack_path: &Path,
    cancel: &CancellationToken,
) -> Result<InstanceRecord, String> {
    let fallback_name = mrpack_path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("Imported Pack")
        .to_string();
    let mut rec = create_instance(
        paths,
        CreateInstanceParams {
            name: fallback_name,
            mc_version: "1.21.4".into(),
            loader: LoaderKind::Fabric,
            loader_version: None,
            group: None,
            nightclient_enabled: false,
        },
    )
    .map_err(|e| e.to_string())?;
    let summary = match import_mrpack(net, mrpack_path, &rec.dir, cancel, |_| {}).await {
        Ok(s) => s,
        Err(e) => {
            let _ = delete_instance(&rec.dir);
            return Err(e.to_string());
        }
    };
    rec.config.name = summary.name;
    rec.config.mc_version = summary.mc_version;
    rec.config.loader = LoaderKind::from_id(&summary.loader);
    rec.config.loader_version = summary.loader_version;
    if let Err(e) = save_instance_config(&rec.dir, &rec.config) {
        let _ = delete_instance(&rec.dir);
        return Err(e.to_string());
    }
    Ok(rec)
}

fn scan_simple_pack_dir(dir: &Path) -> Vec<InstalledMod> {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return Vec::new();
    };
    let mut out = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let filename = entry.file_name().to_string_lossy().to_string();
        let enabled = !filename.ends_with(".disabled");
        let clean = filename.strip_suffix(".disabled").unwrap_or(&filename);
        let name = clean
            .strip_suffix(".zip")
            .or_else(|| clean.strip_suffix(".ZIP"))
            .unwrap_or(clean)
            .to_string();
        let file_size = entry.metadata().map(|m| m.len()).unwrap_or(0);
        out.push(InstalledMod {
            mod_id: String::new(),
            name,
            version: String::new(),
            description: String::new(),
            authors: String::new(),
            filename,
            path,
            enabled,
            file_size,
            icon_path: None,
        });
    }
    out.sort_by_key(|a| a.name.to_lowercase());
    out
}

fn open_url_safe(url: String) {
    let trimmed = url.trim();
    if trimmed.starts_with("https://") || trimmed.starts_with("http://") {
        open_detached(trimmed.to_string());
    }
}

fn open_detached(target: impl Into<std::ffi::OsString>) {
    let owned = target.into();
    let _ = std::thread::Builder::new()
        .name("open-detached".to_string())
        .spawn(move || {
            let _ = open::that_detached(owned);
        });
}

fn filter_system_event(
    ev: Event,
    _status: iced::event::Status,
    _window: window::Id,
) -> Option<Message> {
    match ev {
        Event::Window(
            window::Event::Resized(_)
            | window::Event::Focused
            | window::Event::FileHovered(_)
            | window::Event::FilesHoveredLeft
            | window::Event::FileDropped(_),
        ) => Some(Message::SystemEvent(ev)),
        Event::Keyboard(keyboard::Event::KeyPressed {
            key:
                keyboard::Key::Named(
                    keyboard::key::Named::Escape
                    | keyboard::key::Named::ArrowLeft
                    | keyboard::key::Named::ArrowRight,
                ),
            ..
        }) => Some(Message::SystemEvent(ev)),
        _ => None,
    }
}

fn copy_text_to_clipboard(text: &str) {
    let owned = text.to_string();
    let _ = std::thread::Builder::new()
        .name("clipboard-copy".to_string())
        .spawn(move || {
            copy_text_to_clipboard_blocking(&owned);
        });
}

fn copy_text_to_clipboard_blocking(text: &str) {
    #[cfg(target_os = "linux")]
    {
        use std::io::Write;
        use std::process::{Command, Stdio};
        let try_cmd = |prog: &str, args: &[&str]| -> bool {
            if let Ok(mut child) = Command::new(prog)
                .args(args)
                .stdin(Stdio::piped())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .spawn()
            {
                if let Some(mut stdin) = child.stdin.take() {
                    let _ = stdin.write_all(text.as_bytes());
                    drop(stdin);
                }
                let _ = child.wait();
                return true;
            }
            false
        };
        if std::env::var_os("WAYLAND_DISPLAY").is_some() && try_cmd("wl-copy", &[]) {
            return;
        }
        let _ = try_cmd("xclip", &["-selection", "clipboard"]);
    }
    #[cfg(target_os = "macos")]
    {
        use std::io::Write;
        use std::process::{Command, Stdio};
        if let Ok(mut child) = Command::new("pbcopy")
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
        {
            if let Some(mut stdin) = child.stdin.take() {
                let _ = stdin.write_all(text.as_bytes());
            }
            let _ = child.wait();
        }
    }
    #[cfg(target_os = "windows")]
    {
        set_windows_clipboard_unicode(text);
    }
}

#[cfg(target_os = "windows")]
fn set_windows_clipboard_unicode(text: &str) {
    use core::ffi::c_void;
    const CF_UNICODETEXT: u32 = 13;
    const GMEM_MOVEABLE: u32 = 0x0002;
    #[link(name = "user32")]
    unsafe extern "system" {
        fn OpenClipboard(hwnd: *mut c_void) -> i32;
        fn EmptyClipboard() -> i32;
        fn SetClipboardData(format: u32, mem: *mut c_void) -> *mut c_void;
        fn CloseClipboard() -> i32;
    }
    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn GlobalAlloc(flags: u32, bytes: usize) -> *mut c_void;
        fn GlobalLock(mem: *mut c_void) -> *mut c_void;
        fn GlobalUnlock(mem: *mut c_void) -> i32;
        fn GlobalFree(mem: *mut c_void) -> *mut c_void;
    }
    let wide: Vec<u16> = text.encode_utf16().chain(std::iter::once(0)).collect();
    let bytes = wide.len().saturating_mul(2);
    unsafe {
        let mut opened = false;
        for _ in 0..5 {
            if OpenClipboard(std::ptr::null_mut()) != 0 {
                opened = true;
                break;
            }
            std::thread::sleep(Duration::from_millis(15));
        }
        if !opened {
            return;
        }
        if EmptyClipboard() == 0 {
            CloseClipboard();
            return;
        }
        let hmem = GlobalAlloc(GMEM_MOVEABLE, bytes);
        if hmem.is_null() {
            CloseClipboard();
            return;
        }
        let ptr = GlobalLock(hmem) as *mut u16;
        if ptr.is_null() {
            GlobalFree(hmem);
            CloseClipboard();
            return;
        }
        std::ptr::copy_nonoverlapping(wide.as_ptr(), ptr, wide.len());
        GlobalUnlock(hmem);
        if SetClipboardData(CF_UNICODETEXT, hmem).is_null() {
            GlobalFree(hmem);
        }
        CloseClipboard();
    }
}

#[derive(Clone)]
struct LaunchStreamKey(usize, Arc<Mutex<Option<mpsc::UnboundedReceiver<LaunchEvent>>>>);

impl std::hash::Hash for LaunchStreamKey {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.0.hash(state);
    }
}

fn build_launch_stream(
    key: &LaunchStreamKey,
) -> iced::futures::stream::BoxStream<'static, Message> {
    let rx_opt = key.1.lock().ok().and_then(|mut g| g.take());
    Box::pin(iced::stream::channel(
        64,
        move |mut output: iced::futures::channel::mpsc::Sender<Message>| async move {
            if let Some(mut rx) = rx_opt {
                while let Some(first) = rx.recv().await {
                    let is_log = matches!(first, LaunchEvent::LogLine { .. });
                    let mut batch = vec![first];

                    if is_log {
                        let deadline = tokio::time::Instant::now() + Duration::from_millis(50);
                        while batch.len() < 128 {
                            match rx.try_recv() {
                                Ok(ev) => {
                                    let stop = !matches!(ev, LaunchEvent::LogLine { .. });
                                    batch.push(ev);
                                    if stop {
                                        break;
                                    }
                                }
                                Err(mpsc::error::TryRecvError::Empty) => {
                                    match tokio::time::timeout_at(deadline, rx.recv()).await {
                                        Ok(Some(ev)) => {
                                            let stop = !matches!(ev, LaunchEvent::LogLine { .. });
                                            batch.push(ev);
                                            if stop {
                                                break;
                                            }
                                        }
                                        Ok(None) | Err(_) => break,
                                    }
                                }
                                Err(mpsc::error::TryRecvError::Disconnected) => break,
                            }
                        }
                    } else {
                        while batch.len() < 64 {
                            match rx.try_recv() {
                                Ok(ev) => batch.push(ev),
                                Err(_) => break,
                            }
                        }
                    }

                    let _ = output.send(Message::LaunchStreamBatch(batch)).await;
                }
            }
            std::future::pending::<()>().await;
        },
    ))
}

#[cfg(target_os = "linux")]
#[derive(Clone)]
struct DndStreamKey(
    usize,
    Arc<Mutex<Option<mpsc::UnboundedReceiver<smithay_clipboard::WaylandDndEvent>>>>,
);

#[cfg(target_os = "linux")]
impl std::hash::Hash for DndStreamKey {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.0.hash(state);
    }
}

#[cfg(target_os = "linux")]
fn build_dnd_stream(
    key: &DndStreamKey,
) -> iced::futures::stream::BoxStream<'static, Message> {
    let rx_opt = key.1.lock().ok().and_then(|mut g| g.take());
    Box::pin(iced::stream::channel(
        32,
        move |mut output: iced::futures::channel::mpsc::Sender<Message>| async move {
            if let Some(mut rx) = rx_opt {
                while let Some(ev) = rx.recv().await {
                    let _ = output.send(Message::WaylandDnd(ev)).await;
                }
            }
            std::future::pending::<()>().await;
        },
    ))
}
