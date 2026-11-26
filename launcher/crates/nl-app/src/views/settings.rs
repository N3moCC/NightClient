use crate::app::{App, Message, Page};
use crate::icons::{
    SVG_ALERT, SVG_CHECK, SVG_CHEVRON_DOWN, SVG_CODE, SVG_COFFEE, SVG_CPU, SVG_DOWNLOAD,
    SVG_EXTERNAL, SVG_FILE_TEXT, SVG_FOLDER, SVG_GLOBE, SVG_MONITOR, SVG_MOON, SVG_NIGHTCLIENT,
    SVG_REFRESH, SVG_SETTINGS, SVG_SHADERS, SVG_SLIDERS, SVG_ZAP, icon_svg,
};
use crate::theme::{FONT_MONO, Palette, TextExt, truncate_ellipsis};
use crate::widgets::{subtle_row_divider, toggle_switch, vcenter};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{
    Button, column, container, pick_list, row, scrollable, slider, stack, text, text_input,
};
use iced::{Alignment, Element, Length, Padding};
use nl_core::ThemeMode;
use nl_java::{
    PerformanceProfile, build_jvm_args, evaluate_memory_mb, evaluate_profile_memory_warning,
};
use nl_night::NightClientChannel;
#[cfg(target_os = "linux")]
use nl_platform::GpuPreference;

pub fn view_settings(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    let is_compact = app.is_compact();
    let picker_w = if is_compact { 220.0 } else { 276.0 };
    let input_w = if is_compact { 236.0 } else { 324.0 };
    let slider_w = if is_compact { 130.0 } else { 180.0 };

    let header = row![
        container(icon_svg(SVG_SETTINGS, 19.0, p.text_primary))
            .width(40)
            .height(40)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .style(p.monogram_tile_style(true, 10.0)),
        container(
            column![
                text(app.tr("settings.title"))
                    .size(21)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                text(app.tr("settings.subtitle"))
                    .size(12)
                    .shaped()
                    .color(p.text_muted),
            ]
            .spacing(2)
            .width(Length::Fill),
        )
        .width(Length::Fill),
        Button::new(vcenter(
            row![
                icon_svg(SVG_CHECK, 13.5, p.text_primary),
                text(app.tr("settings.done")).size(12.5).medium().nowrap(),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::SelectPage(Page::Workspace))
        .style(p.secondary_button())
        .height(Length::Fixed(34.0))
        .padding(Padding::new(0.0).left(12.0).right(14.0)),
    ]
    .spacing(12)
    .align_y(Alignment::Center)
    .width(Length::Fill);

    let locales = app.i18n.available_locales().to_vec();
    let active_locale = app.i18n.active_meta();
    let lang_picker = dropdown_box(
        pick_list(locales, active_locale, Message::SelectLocale)
            .handle(pick_list::Handle::None)
            .text_shaping(iced::widget::text::Shaping::Advanced)
            .style(p.pick_list_style())
            .menu_style(p.custom_menu_style())
            .text_size(12)
            .padding(Padding::new(7.0).left(10.0).right(28.0))
            .width(Length::Fixed(picker_w))
            .into(),
        picker_w,
        p,
    );

    let lang_row = setting_row(
        SVG_GLOBE,
        app.tr("settings.language"),
        Some((app.tr("settings.language_desc").to_string(), false)),
        lang_picker,
        p,
    );

    let dark_active = app.config.theme_mode == ThemeMode::Dark;
    let theme_seg = container(
        row![
            Button::new(vcenter(
                row![
                    icon_svg(SVG_MOON, 12.0, if dark_active { p.text_primary } else { p.text_muted }),
                    text(app.tr("settings.theme.dark")).size(12).medium().nowrap(),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::SetThemeMode(ThemeMode::Dark))
            .style(p.segmented_button(dark_active))
            .height(Length::Fixed(26.0))
            .padding([0, 12]),
            Button::new(vcenter(
                row![
                    icon_svg(SVG_SHADERS, 12.0, if !dark_active { p.text_primary } else { p.text_muted }),
                    text(app.tr("settings.theme.light")).size(12).medium().nowrap(),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::SetThemeMode(ThemeMode::Light))
            .style(p.segmented_button(!dark_active))
            .height(Length::Fixed(26.0))
            .padding([0, 12]),
        ]
        .spacing(2),
    )
    .padding(3)
    .style(p.segmented_track_style());

    let theme_row = setting_row(
        SVG_MOON,
        app.tr("settings.theme"),
        Some((app.tr("settings.theme_desc").to_string(), false)),
        theme_seg.into(),
        p,
    );

    let minimize_row = setting_row(
        SVG_MONITOR,
        app.tr("settings.minimize"),
        Some((app.tr("settings.minimize_desc").to_string(), false)),
        toggle_switch(
            app.config.minimize_on_launch,
            false,
            Message::ToggleMinimizeOnLaunch(!app.config.minimize_on_launch),
            p,
        ),
        p,
    );

    let root_display = truncate_ellipsis(
        &app.paths.root.to_string_lossy(),
        if is_compact { 26 } else { 42 },
    );
    let data_row = setting_row(
        SVG_FOLDER,
        app.tr("settings.data_dir"),
        Some((root_display, true)),
        row![
            compact_icon_btn(SVG_EXTERNAL, app.tr("settings.open"), Message::OpenDataFolder, p),
            compact_icon_btn(SVG_GLOBE, app.tr("settings.open_locales"), Message::OpenLocalesFolder, p),
        ]
        .spacing(6)
        .align_y(Alignment::Center)
        .into(),
        p,
    );

    let (shortcut_title_key, shortcut_desc_key) = if cfg!(target_os = "windows") {
        ("settings.shortcut.windows", "settings.shortcut_desc.windows")
    } else if cfg!(target_os = "macos") {
        ("settings.shortcut.macos", "settings.shortcut_desc.macos")
    } else {
        ("settings.shortcut.linux", "settings.shortcut_desc.linux")
    };

    let shortcut_row = setting_row(
        SVG_DOWNLOAD,
        app.tr(shortcut_title_key),
        Some((app.tr(shortcut_desc_key).to_string(), false)),
        compact_icon_btn(
            SVG_DOWNLOAD,
            app.tr("settings.install_shortcut"),
            Message::InstallDesktopShortcut,
            p,
        ),
        p,
    );

    let migrate_row = setting_row(
        SVG_DOWNLOAD,
        app.tr("settings.migrate_launcher"),
        Some((app.tr("settings.migrate_launcher_desc").to_string(), false)),
        compact_icon_btn(
            SVG_DOWNLOAD,
            app.tr("settings.migrate_open"),
            Message::OpenMigrationWizard,
            p,
        ),
        p,
    );

    let general_group = settings_section(
        app.tr("settings.section.general"),
        column![
            lang_row,
            subtle_row_divider(p),
            theme_row,
            subtle_row_divider(p),
            minimize_row,
            subtle_row_divider(p),
            data_row,
            subtle_row_divider(p),
            migrate_row,
            subtle_row_divider(p),
            shortcut_row,
        ]
        .into(),
        p,
    );

    let max_ram = (app.system_ram_mb as u32).clamp(4096, 32768);
    let mem_mb = app.config.default_memory_mb.clamp(1024, max_ram);
    let mem_gb = format!("{:.1}", mem_mb as f32 / 1024.0);
    let mem_row = setting_row(
        SVG_CPU,
        app.tr("settings.default_memory"),
        Some((app.tr("settings.default_memory_desc").to_string(), false)),
        row![
            container(
                slider(1024..=max_ram, mem_mb, Message::SetDefaultMemoryMb)
                    .step(512u32)
                    .style(p.clean_slider_style()),
            )
            .width(Length::Fixed(slider_w)),
            container(
                text(app.tr_fmt("header.memory_gb", &[("gb", &mem_gb)]))
                    .size(12.5)
                    .mono_medium()
                    .nowrap()
                    .color(p.text_primary),
            )
            .width(Length::Fixed(54.0))
            .align_x(Horizontal::Right),
        ]
        .spacing(10)
        .align_y(Alignment::Center)
        .into(),
        p,
    );

    let (java_opts, java_sel) =
        app.java_dropdown_options(app.config.default_java_path.as_deref(), true);
    let java_picker = dropdown_box(
        pick_list(java_opts, Some(java_sel), Message::SetDefaultJavaLabel)
            .handle(pick_list::Handle::None)
            .text_shaping(iced::widget::text::Shaping::Advanced)
            .style(p.pick_list_style())
            .menu_style(p.custom_menu_style())
            .text_size(12)
            .padding(Padding::new(7.0).left(10.0).right(28.0))
            .width(Length::Fixed(picker_w))
            .into(),
        picker_w,
        p,
    );

    let java_row = setting_row(
        SVG_COFFEE,
        app.tr("settings.java_runtime"),
        Some((app.tr("settings.java_runtime_desc").to_string(), false)),
        java_picker,
        p,
    );

    let temurin_row = setting_row(
        SVG_DOWNLOAD,
        app.tr("settings.java_install"),
        None,
        row![
            temurin_btn(app, 17),
            temurin_btn(app, 21),
            temurin_btn(app, 25),
        ]
        .spacing(6)
        .align_y(Alignment::Center)
        .into(),
        p,
    );

    let mut java_col = column![mem_row];
    if let Some(warn) = evaluate_memory_mb(mem_mb, app.system_ram_mb).warning {
        java_col = java_col.push(
            container(
                row![
                    icon_svg(SVG_ALERT, 12.0, p.accent_orange),
                    text(app.i18n.memory_warning(&warn))
                        .size(11)
                        .shaped()
                        .color(p.accent_orange),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            )
            .padding(Padding::new(0.0).left(42.0).right(16.0).bottom(8.0)),
        );
    }
    java_col = java_col
        .push(subtle_row_divider(p))
        .push(java_row)
        .push(subtle_row_divider(p))
        .push(temurin_row);

    let java_group = settings_section(app.tr("settings.section.java"), java_col.into(), p);

    let perf_seg = container(
        row![
            perf_seg_btn(app, PerformanceProfile::Smooth, "settings.perf.smooth"),
            perf_seg_btn(app, PerformanceProfile::ZeroPause, "settings.perf.zero_pause"),
            perf_seg_btn(app, PerformanceProfile::PeakFps, "settings.perf.peak_fps"),
            perf_seg_btn(app, PerformanceProfile::Stock, "settings.perf.stock"),
        ]
        .spacing(2),
    )
    .padding(3)
    .style(p.segmented_track_style());

    let preview_rt = app
        .config
        .default_java_path
        .as_deref()
        .filter(|p| !p.trim().is_empty())
        .and_then(|override_path| {
            app.java_runtimes
                .iter()
                .find(|rt| rt.path.to_string_lossy() == override_path)
        })
        .or_else(|| app.java_runtimes.first());

    let preview_flags = if let Some(rt) = preview_rt {
        build_jvm_args(
            rt,
            app.config.performance_profile,
            mem_mb,
            &app.config.extra_jvm_args,
        )
        .join(" ")
    } else {
        let xms = if app.config.performance_profile == PerformanceProfile::Stock {
            (mem_mb / 2).clamp(512, 2048).min(mem_mb)
        } else {
            mem_mb.min(8192)
        };
        format!("-Xms{xms}M -Xmx{mem_mb}M -Dfile.encoding=UTF-8")
    };

    let flags_strip = container(
        container(
            text(preview_flags)
                .size(11)
                .mono()
                .shaped()
                .width(Length::Fill)
                .color(p.text_dim),
        )
        .width(Length::Fill)
        .padding([8, 10])
        .style(p.inset_panel_style()),
    )
    .width(Length::Fill)
    .padding(Padding::new(0.0).left(14.0).right(16.0).bottom(10.0));

    let extra_jvm_row = setting_row(
        SVG_CODE,
        app.tr("settings.extra_jvm"),
        Some((app.tr("settings.extra_jvm_desc").to_string(), false)),
        text_input("-XX:+UseZGC", &app.config.extra_jvm_args)
            .on_input(Message::SetExtraJvmArgs)
            .font(FONT_MONO)
            .style(p.text_input_style())
            .padding(Padding::new(7.0).left(10.0).right(10.0))
            .size(12)
            .width(Length::Fixed(input_w))
            .into(),
        p,
    );

    let extra_game_row = setting_row(
        SVG_SLIDERS,
        app.tr("settings.extra_game"),
        Some((app.tr("settings.extra_game_desc").to_string(), false)),
        text_input("--fullscreen", &app.config.extra_game_args)
            .on_input(Message::SetExtraGameArgs)
            .font(FONT_MONO)
            .style(p.text_input_style())
            .padding(Padding::new(7.0).left(10.0).right(10.0))
            .size(12)
            .width(Length::Fixed(input_w))
            .into(),
        p,
    );

    let mut launch_col = column![
        setting_row(SVG_ZAP, app.tr("settings.perf_profile"), None, perf_seg.into(), p),
        flags_strip,
    ];
    if let Some(warn) = evaluate_profile_memory_warning(app.config.performance_profile, mem_mb) {
        launch_col = launch_col.push(
            container(
                row![
                    icon_svg(SVG_ALERT, 12.0, p.accent_orange),
                    text(app.i18n.memory_warning(&warn))
                        .size(11)
                        .shaped()
                        .color(p.accent_orange),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            )
            .padding(Padding::new(0.0).left(16.0).right(16.0).bottom(10.0)),
        );
    }
    let turbo_default_row = setting_row(
        SVG_ZAP,
        app.tr("settings.turbo_default"),
        Some((app.tr("settings.turbo_default_desc").to_string(), false)),
        toggle_switch(
            app.config.default_turbo_mode,
            true,
            Message::ToggleDefaultTurbo(!app.config.default_turbo_mode),
            p,
        ),
        p,
    );

    let launch_col = launch_col
        .push(subtle_row_divider(p))
        .push(turbo_default_row)
        .push(subtle_row_divider(p))
        .push(extra_jvm_row)
        .push(subtle_row_divider(p))
        .push(extra_game_row);

    #[cfg(target_os = "linux")]
    let launch_col = {
        let gpu_seg = container(
            row![
                gpu_seg_btn(app, GpuPreference::Auto, "settings.gpu.auto"),
                gpu_seg_btn(app, GpuPreference::Discrete, "settings.gpu.discrete"),
            ]
            .spacing(2),
        )
        .padding(3)
        .style(p.segmented_track_style());

        launch_col
            .push(subtle_row_divider(p))
            .push(setting_row(
                SVG_MONITOR,
                app.tr("settings.gpu"),
                Some((app.tr("settings.gpu_desc").to_string(), false)),
                gpu_seg.into(),
                p,
            ))
            .push(subtle_row_divider(p))
            .push(setting_row(
                SVG_ZAP,
                app.tr("settings.gamemode"),
                Some((app.tr("settings.gamemode_desc").to_string(), false)),
                toggle_switch(
                    app.config.use_gamemode,
                    false,
                    Message::ToggleGamemode(!app.config.use_gamemode),
                    p,
                ),
                p,
            ))
            .push(subtle_row_divider(p))
            .push(setting_row(
                SVG_MONITOR,
                app.tr("settings.mangohud"),
                Some((app.tr("settings.mangohud_desc").to_string(), false)),
                toggle_switch(
                    app.config.use_mangohud,
                    false,
                    Message::ToggleMangohud(!app.config.use_mangohud),
                    p,
                ),
                p,
            ))
    };

    let launch_group = settings_section(app.tr("settings.section.launch"), launch_col.into(), p);

    let stable_active = app.config.nightclient_channel == NightClientChannel::Stable;
    let channel_seg = container(
        row![
            Button::new(vcenter(
                text(app.tr("settings.channel.stable")).size(12).medium().nowrap(),
            ))
            .on_press(Message::SetNightClientChannel(NightClientChannel::Stable))
            .style(p.segmented_button(stable_active))
            .height(Length::Fixed(26.0))
            .padding([0, 12]),
            Button::new(vcenter(
                text(app.tr("settings.channel.beta")).size(12).medium().nowrap(),
            ))
            .on_press(Message::SetNightClientChannel(NightClientChannel::Beta))
            .style(p.segmented_button(!stable_active))
            .height(Length::Fixed(26.0))
            .padding([0, 12]),
        ]
        .spacing(2),
    )
    .padding(3)
    .style(p.segmented_track_style());

    let update_subtitle = if app.checking_launcher_update {
        app.tr("settings.updates_checking").to_string()
    } else if let Some(ref info) = app.launcher_update
        && info.is_update_available
    {
        app.tr_fmt(
            "settings.updates_available",
            &[
                ("current", &info.current_version),
                ("latest", &info.latest_version),
            ],
        )
    } else {
        app.tr_fmt(
            "settings.updates_current",
            &[("version", app.launcher_version())],
        )
    };

    let has_update = app
        .launcher_update
        .as_ref()
        .is_some_and(|u| u.is_update_available);

    let mut update_controls = row![].spacing(6).align_y(Alignment::Center);
    if app.launcher_update.is_some() {
        update_controls = update_controls.push(compact_icon_btn(
            SVG_FILE_TEXT,
            app.tr("settings.updates_changelog"),
            Message::OpenLauncherUpdateModal,
            p,
        ));
    }
    if has_update {
        update_controls = update_controls.push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_DOWNLOAD, 12.5, p.cta_text),
                    text(app.tr("settings.updates_install"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::OpenLauncherUpdateModal)
            .style(p.primary_button())
            .height(Length::Fixed(32.0))
            .padding(Padding::new(0.0).left(11.0).right(12.0)),
        );
    } else {
        update_controls = update_controls.push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_REFRESH, 12.5, p.text_secondary),
                    text(app.tr("settings.updates_check"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press_maybe(if app.checking_launcher_update {
                None
            } else {
                Some(Message::CheckLauncherUpdates(true))
            })
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding(Padding::new(0.0).left(10.0).right(11.0)),
        );
    }

    let night_group = settings_section(
        app.tr("settings.section.nightclient"),
        column![
            setting_row(
                SVG_NIGHTCLIENT,
                app.tr("settings.channel"),
                Some((app.tr("settings.channel_desc").to_string(), false)),
                channel_seg.into(),
                p,
            ),
            subtle_row_divider(p),
            setting_row(
                SVG_DOWNLOAD,
                app.tr("settings.updates"),
                Some((update_subtitle, false)),
                update_controls.into(),
                p,
            ),
            subtle_row_divider(p),
            setting_row(
                SVG_GLOBE,
                app.tr("settings.proxy"),
                None,
                text_input(
                    app.tr("settings.proxy_placeholder"),
                    app.config.net.proxy_url.as_deref().unwrap_or(""),
                )
                .on_input(Message::SetProxyUrl)
                .font(FONT_MONO)
                .style(p.text_input_style())
                .padding(Padding::new(7.0).left(10.0).right(10.0))
                .size(12)
                .width(Length::Fixed(input_w))
                .into(),
                p,
            ),
        ]
        .into(),
        p,
    );

    let body = column![
        header,
        general_group,
        java_group,
        launch_group,
        night_group,
    ]
    .spacing(16)
    .width(Length::Fill);

    scrollable(
        container(
            container(body)
                .width(Length::Fill)
                .max_width(820.0)
                .padding(Padding::new(0.0).left(22.0).right(22.0).bottom(24.0)),
        )
        .width(Length::Fill)
        .center_x(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .style(p.custom_scrollbar_style())
    .into()
}

fn settings_section<'a>(
    title: &'a str,
    content: Element<'a, Message>,
    p: Palette,
) -> Element<'a, Message> {
    column![
        text(title)
            .size(12)
            .medium()
            .nowrap()
            .color(p.text_muted),
        container(content)
            .width(Length::Fill)
            .style(p.card_style()),
    ]
    .spacing(6)
    .width(Length::Fill)
    .into()
}

fn setting_row<'a>(
    icon: &'static str,
    label: &'a str,
    sub: Option<(String, bool)>,
    control: Element<'a, Message>,
    p: Palette,
) -> Element<'a, Message> {
    let mut text_col = column![text(label).size(13).medium().nowrap().color(p.text_primary)]
        .spacing(2)
        .width(Length::Fill);

    if let Some((desc, mono)) = sub {
        let t = if mono {
            text(desc).size(11.5).mono().nowrap().color(p.text_muted)
        } else {
            text(desc).size(11.5).shaped().color(p.text_muted)
        };
        text_col = text_col.push(t);
    }

    container(
        row![
            icon_svg(icon, 15.0, p.text_muted),
            container(text_col).width(Length::Fill).clip(true),
            control,
        ]
        .spacing(12)
        .align_y(Alignment::Center)
        .width(Length::Fill),
    )
    .width(Length::Fill)
    .padding(Padding::new(11.0).left(14.0).right(14.0))
    .into()
}

fn dropdown_box<'a>(
    picker: Element<'a, Message>,
    width: f32,
    p: Palette,
) -> Element<'a, Message> {
    container(stack![
        picker,
        container(icon_svg(SVG_CHEVRON_DOWN, 14.0, p.text_muted))
            .width(Length::Fixed(width))
            .height(Length::Fill)
            .padding(Padding::new(0.0).right(10.0))
            .align_x(Horizontal::Right)
            .align_y(Vertical::Center),
    ])
    .height(Length::Fixed(32.0))
    .into()
}

fn compact_icon_btn<'a>(
    icon: &'static str,
    label: &'a str,
    msg: Message,
    p: Palette,
) -> Element<'a, Message> {
    Button::new(vcenter(
        row![
            icon_svg(icon, 12.5, p.text_secondary),
            text(label).size(12).medium().nowrap().color(p.text_primary),
        ]
        .spacing(5)
        .align_y(Alignment::Center),
    ))
    .on_press(msg)
    .style(p.secondary_button())
    .height(Length::Fixed(32.0))
    .padding(Padding::new(0.0).left(10.0).right(11.0))
    .into()
}

fn temurin_btn<'a>(app: &'a App, major: u32) -> Element<'a, Message> {
    let p = app.palette;
    let busy = app.installing_java_major == Some(major);
    let label = if busy {
        app.tr_fmt("settings.java_installing", &[("major", &major.to_string())])
    } else {
        format!("Java {major}")
    };
    Button::new(vcenter(
        text(label).size(12).mono_medium().nowrap().color(p.text_primary),
    ))
    .on_press_maybe(if app.installing_java_major.is_some() {
        None
    } else {
        Some(Message::InstallJavaMajor(major))
    })
    .style(p.secondary_button())
    .height(Length::Fixed(30.0))
    .padding(Padding::new(0.0).left(10.0).right(10.0))
    .into()
}

fn perf_seg_btn<'a>(
    app: &'a App,
    prof: PerformanceProfile,
    label_key: &'a str,
) -> Element<'a, Message> {
    let p = app.palette;
    let active = app.config.performance_profile == prof;
    Button::new(vcenter(text(app.tr(label_key)).size(11.5).medium().nowrap()))
        .on_press(Message::SetDefaultProfile(prof))
        .style(p.segmented_button(active))
        .height(Length::Fixed(26.0))
        .padding([0, 10])
        .into()
}

#[cfg(target_os = "linux")]
fn gpu_seg_btn<'a>(app: &'a App, gpu: GpuPreference, label_key: &'a str) -> Element<'a, Message> {
    let p = app.palette;
    let active = app.config.gpu_preference == gpu;
    Button::new(vcenter(text(app.tr(label_key)).size(12).medium().nowrap()))
        .on_press(Message::SetGpuPreference(gpu))
        .style(p.segmented_button(active))
        .height(Length::Fixed(26.0))
        .padding([0, 10])
        .into()
}
