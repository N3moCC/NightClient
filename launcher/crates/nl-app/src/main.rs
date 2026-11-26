#![windows_subsystem = "windows"]

mod app;
mod blur;
mod i18n;
mod icons;
mod markdown_view;
mod theme;
mod views;
mod widgets;

use app::App;
use iced::window;
use nl_platform::{
    InstanceLock, LauncherPaths, ensure_linux_desktop_integration, parse_deep_link,
};

fn main() -> iced::Result {
    init_tracing();
    nl_night::cleanup_stale_old_binary();

    let mut deep_link = None;
    let mut restarted = false;
    let mut force_welcome = std::env::var("NIGHTLAUNCHER_FORCE_WELCOME")
        .ok()
        .is_some_and(|v| v == "1" || v.eq_ignore_ascii_case("true"));
    for arg in std::env::args().skip(1) {
        if arg == "--restarted" {
            restarted = true;
        } else if arg == "--welcome" {
            force_welcome = true;
        } else if let Some(dl) = parse_deep_link(&arg) {
            deep_link = Some(dl);
        }
    }

    let paths = LauncherPaths::new(LauncherPaths::default_root());
    let _ = paths.ensure_all();

    let mut lock_guard = None;
    let attempts = if restarted { 20 } else { 1 };
    for attempt in 0..attempts {
        match InstanceLock::acquire(&paths.lock_file()) {
            Ok(Some(guard)) => {
                lock_guard = Some(guard);
                break;
            }
            Ok(None) => {
                if attempt + 1 < attempts {
                    std::thread::sleep(std::time::Duration::from_millis(100));
                    continue;
                }
                tracing::warn!("NightClient Launcher is already running");
                return Ok(());
            }
            Err(err) => {
                tracing::warn!("Could not acquire launcher lock: {err}");
                break;
            }
        }
    }
    let _lock = lock_guard;

    if let Ok(exe) = std::env::current_exe() {
        ensure_linux_desktop_integration(&exe, icons::APP_ICON_PNG);
    }

    let first = run_app(deep_link.clone(), force_welcome, true, true);
    if first.is_ok() {
        return first;
    }
    let second = run_app(deep_link.clone(), force_welcome, true, false);
    if second.is_ok() {
        return second;
    }
    run_app(deep_link, force_welcome, false, false)
}

fn run_app(
    deep_link: Option<nl_platform::DeepLink>,
    force_welcome: bool,
    transparent: bool,
    blur: bool,
) -> iced::Result {
    #[cfg_attr(
        not(any(target_os = "linux", target_os = "windows")),
        allow(unused_mut)
    )]
    let mut window_settings = window::Settings {
        size: iced::Size::new(1120.0, 720.0),
        min_size: Some(iced::Size::new(820.0, 560.0)),
        position: window::Position::Centered,
        resizable: true,
        decorations: false,
        transparent,
        blur,
        icon: decode_window_icon(),
        ..window::Settings::default()
    };

    #[cfg(target_os = "linux")]
    {
        window_settings.platform_specific.application_id = nl_platform::LINUX_APP_ID.to_string();
    }

    #[cfg(target_os = "windows")]
    {
        window_settings.platform_specific.undecorated_shadow = false;
        window_settings.platform_specific.corner_preference =
            window::settings::platform::CornerPreference::Round;
    }

    iced::application(
        move || App::boot(deep_link.clone(), force_welcome),
        App::update,
        App::view,
    )
    .title(App::title)
    .theme(App::theme)
    .subscription(App::subscription)
    .font(icons::FONT_GEIST_REGULAR)
    .font(icons::FONT_GEIST_MEDIUM)
    .font(icons::FONT_GEIST_MONO_REGULAR)
    .font(icons::FONT_GEIST_MONO_MEDIUM)
    .default_font(theme::FONT_SANS)
    .window(window_settings)
    .antialiasing(true)
    .run()
}

fn init_tracing() {
    let mut filter = tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| {
        tracing_subscriber::EnvFilter::new(
            "warn,nl_app=info,nl_core=info,nl_net=info,nl_night=info",
        )
    });
    let rust_log = std::env::var("RUST_LOG").unwrap_or_default();
    if !rust_log.contains("iced_winit") {
        for directive in [
            "iced_winit=warn",
            "iced_wgpu=warn",
            "wgpu_hal=error",
            "wgpu_core=error",
            "fontdb=error",
        ] {
            if let Ok(d) = directive.parse() {
                filter = filter.add_directive(d);
            }
        }
    }
    tracing_subscriber::fmt().with_env_filter(filter).init();
}

fn decode_window_icon() -> Option<window::Icon> {
    let img = image::load_from_memory(icons::APP_ICON_PNG).ok()?.to_rgba8();
    let (w, h) = img.dimensions();
    window::icon::from_rgba(img.into_raw(), w, h).ok()
}
