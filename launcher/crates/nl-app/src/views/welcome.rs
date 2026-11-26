use crate::app::{App, Message, WelcomeState, WelcomeStep};
use crate::icons::{
    SVG_ALERT, SVG_CHECK, SVG_CHEVRON_LEFT, SVG_CHEVRON_RIGHT, SVG_DOWNLOAD, SVG_FOLDER, SVG_GLOBE,
    SVG_MOON, SVG_NIGHTCLIENT, SVG_PLUS, SVG_REFRESH, SVG_SHADERS, SVG_SHIELD_CHECK, icon_svg,
    loader_icon_svg,
};
use crate::theme::{TextExt, truncate_ellipsis};
use crate::views::navigation;
use crate::widgets::{center_box, hairline_divider, subtle_row_divider, toggle_switch, vcenter};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{
    Button, Column, Space, column, container, row, scrollable, text, text_input,
};
use iced::{Alignment, Background, Border, Color, Element, Length, Padding};
use nl_core::{DetectedProfile, ThemeMode};

pub fn view_welcome<'a>(app: &'a App, state: &'a WelcomeState) -> Element<'a, Message> {
    let top_bar = navigation::view_canvas_top_bar(app);

    let body: Element<'a, Message> = match &state.step {
        WelcomeStep::Intro => view_intro_step(app),
        WelcomeStep::Scanning => view_scanning_step(app),
        WelcomeStep::Pick => view_pick_step(app, state),
        WelcomeStep::Importing { name } => view_importing_step(app, name),
        WelcomeStep::Done(res) => view_done_step(app, res),
    };

    column![top_bar, body]
        .width(Length::Fill)
        .height(Length::Fill)
        .into()
}

fn view_top_utility_bar(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    let locales = app.i18n.available_locales();
    let active_code = app.config.locale.as_str();

    let mut lang_row = row![icon_svg(SVG_GLOBE, 12.5, p.text_dim)]
        .spacing(4)
        .align_y(Alignment::Center);
    for loc in locales {
        let active = loc.code.eq_ignore_ascii_case(active_code);
        lang_row = lang_row.push(
            Button::new(vcenter(
                text(loc.code.to_ascii_uppercase())
                    .size(11)
                    .mono_medium()
                    .nowrap()
                    .color(if active { p.text_primary } else { p.text_muted }),
            ))
            .on_press(Message::SelectLocale(loc.clone()))
            .style(p.segmented_button(active))
            .height(Length::Fixed(24.0))
            .padding([0, 8]),
        );
    }

    let is_dark = app.config.theme_mode == ThemeMode::Dark;
    let next_theme = if is_dark {
        ThemeMode::Light
    } else {
        ThemeMode::Dark
    };
    let theme_btn = Button::new(center_box(icon_svg(
        if is_dark { SVG_SHADERS } else { SVG_MOON },
        13.0,
        p.text_secondary,
    )))
    .on_press(Message::SetThemeMode(next_theme))
    .style(p.secondary_button())
    .width(Length::Fixed(30.0))
    .height(Length::Fixed(28.0))
    .padding(0);

    row![
        row![
            icon_svg(SVG_NIGHTCLIENT, 18.0, p.accent_brand),
            text("NightClient")
                .size(14)
                .medium()
                .nowrap()
                .color(p.text_primary),
        ]
        .spacing(8)
        .align_y(Alignment::Center),
        Space::new().width(Length::Fill),
        container(lang_row)
            .padding(Padding::new(2.0).left(6.0).right(3.0))
            .style(p.segmented_track_style()),
        theme_btn,
    ]
    .spacing(8)
    .align_y(Alignment::Center)
    .width(Length::Fill)
    .into()
}

fn view_intro_step(app: &App) -> Element<'_, Message> {
    let p = app.palette;

    let privacy_pill = container(
        row![
            icon_svg(SVG_SHIELD_CHECK, 14.0, p.accent_green),
            text(app.tr("welcome.privacy_note"))
                .size(11.5)
                .shaped()
                .color(p.text_secondary),
        ]
        .spacing(8)
        .align_y(Alignment::Center),
    )
    .width(Length::Fill)
    .padding([9, 12])
    .style(move |_| container::Style {
        background: Some(Background::Color(Color {
            a: if p.is_dark { 0.10 } else { 0.11 },
            ..p.accent_green
        })),
        border: Border {
            color: Color {
                a: 0.28,
                ..p.accent_green
            },
            width: 1.0,
            radius: 8.0.into(),
        },
        ..Default::default()
    });

    let migrate_card = container(
        column![
            row![
                container(icon_svg(SVG_DOWNLOAD, 18.0, p.accent_brand))
                    .width(38)
                    .height(38)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center)
                    .style(p.monogram_tile_style(true, 9.0)),
                column![
                    text(app.tr("welcome.card.migrate_title"))
                        .size(15)
                        .medium()
                        .color(p.text_primary),
                    text(app.tr("welcome.card.migrate_launchers"))
                        .size(11.5)
                        .color(p.text_muted),
                ]
                .spacing(2),
            ]
            .spacing(12)
            .align_y(Alignment::Center),
            text(app.tr("welcome.card.migrate_desc"))
                .size(12.5)
                .shaped()
                .color(p.text_secondary),
            privacy_pill,
            Space::new().height(Length::Fill),
            Button::new(center_box(
                row![
                    icon_svg(SVG_DOWNLOAD, 13.5, p.cta_text),
                    text(app.tr("welcome.card.migrate_cta"))
                        .size(13)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(7)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::WelcomeStartScan)
            .style(p.primary_button())
            .height(Length::Fixed(38.0))
            .width(Length::Fill),
        ]
        .spacing(12)
        .height(Length::Fill),
    )
    .width(Length::FillPortion(1))
    .height(Length::Fixed(272.0))
    .padding(18)
    .style(p.card_style());

    let fresh_card = container(
        column![
            row![
                container(icon_svg(SVG_PLUS, 18.0, p.text_secondary))
                    .width(38)
                    .height(38)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center)
                    .style(p.monogram_tile_style(false, 9.0)),
                column![
                    text(app.tr("welcome.card.fresh_title"))
                        .size(15)
                        .medium()
                        .color(p.text_primary),
                    text(app.tr("welcome.card.fresh_sub"))
                        .size(11.5)
                        .color(p.text_muted),
                ]
                .spacing(2),
            ]
            .spacing(12)
            .align_y(Alignment::Center),
            text(app.tr("welcome.card.fresh_desc"))
                .size(12.5)
                .shaped()
                .color(p.text_secondary),
            Space::new().height(Length::Fill),
            Button::new(center_box(
                row![
                    text(app.tr("welcome.card.fresh_cta"))
                        .size(13)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                    icon_svg(SVG_CHEVRON_RIGHT, 13.5, p.text_secondary),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::WelcomeSkip)
            .style(p.secondary_button())
            .height(Length::Fixed(38.0))
            .width(Length::Fill),
        ]
        .spacing(12)
        .height(Length::Fill),
    )
    .width(Length::FillPortion(1))
    .height(Length::Fixed(272.0))
    .padding(18)
    .style(p.card_style());

    let hero = column![
        container(icon_svg(SVG_NIGHTCLIENT, 28.0, p.accent_brand))
            .width(56)
            .height(56)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .style(p.monogram_tile_style(true, 14.0)),
        Space::new().height(4),
        text(app.tr("welcome.title"))
            .size(24)
            .medium()
            .color(p.text_primary),
        text(app.tr("welcome.subtitle"))
            .size(13.5)
            .shaped()
            .color(p.text_muted),
    ]
    .spacing(6)
    .align_x(Alignment::Center);

    let center_block = container(
        column![
            view_top_utility_bar(app),
            Space::new().height(Length::Fill),
            hero,
            Space::new().height(22),
            row![migrate_card, fresh_card].spacing(14).width(Length::Fill),
            Space::new().height(Length::Fill),
        ]
        .align_x(Alignment::Center)
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .max_width(820.0)
    .width(Length::Fill)
    .height(Length::Fill);

    container(center_block)
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .padding(Padding::new(0.0).left(28.0).right(28.0).bottom(24.0))
        .into()
}

fn view_scanning_step(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    container(
        column![
            view_top_utility_bar(app),
            Space::new().height(Length::Fill),
            container(
                column![
                    icon_svg(SVG_REFRESH, 22.0, p.text_secondary),
                    text(app.tr("welcome.scanning_title"))
                        .size(16)
                        .medium()
                        .color(p.text_primary),
                    text(app.tr("welcome.scanning_desc"))
                        .size(12.5)
                        .shaped()
                        .color(p.text_muted),
                ]
                .spacing(8)
                .align_x(Alignment::Center),
            )
            .padding(28)
            .style(p.card_style()),
            Space::new().height(Length::Fill),
        ]
        .align_x(Alignment::Center)
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .padding(Padding::new(0.0).left(28.0).right(28.0).bottom(24.0))
    .into()
}

fn view_pick_step<'a>(app: &'a App, state: &'a WelcomeState) -> Element<'a, Message> {
    let p = app.palette;

    let mut launchers_col = Column::new().spacing(4).width(Length::Fill);
    for (idx, det) in state.detected.iter().enumerate() {
        let active = state.selected_launcher == Some(idx);
        launchers_col = launchers_col.push(
            Button::new(
                row![
                    column![
                        text(det.kind.display_name())
                            .size(13)
                            .medium()
                            .nowrap()
                            .color(if active { p.text_primary } else { p.text_secondary }),
                        text(app.tr_fmt(
                            "welcome.profile_count",
                            &[("count", &det.profiles.len().to_string())],
                        ))
                        .size(11)
                        .nowrap()
                        .color(p.text_dim),
                    ]
                    .spacing(1)
                    .width(Length::Fill),
                    icon_svg(
                        SVG_CHEVRON_RIGHT,
                        12.0,
                        if active { p.text_primary } else { p.text_dim },
                    ),
                ]
                .spacing(8)
                .align_y(Alignment::Center)
                .width(Length::Fill),
            )
            .on_press(Message::WelcomeSelectLauncher(idx))
            .style(p.sidebar_instance(active))
            .width(Length::Fill)
            .padding([8, 10]),
        );
    }

    let left_panel = container(
        column![
            text(app.tr("welcome.detected_launchers"))
                .size(11.5)
                .medium()
                .color(p.text_muted),
            container(
                scrollable(launchers_col)
                    .height(Length::Fill)
                    .style(p.custom_scrollbar_style()),
            )
            .height(Length::Fill)
            .width(Length::Fill),
            hairline_divider(p),
            Button::new(center_box(
                row![
                    icon_svg(SVG_FOLDER, 13.0, p.text_secondary),
                    text(app.tr("welcome.browse_folder"))
                        .size(12)
                        .medium()
                        .nowrap(),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::WelcomeBrowseFolder)
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .width(Length::Fill),
            Button::new(center_box(
                row![
                    icon_svg(SVG_REFRESH, 12.5, p.text_muted),
                    text(app.tr("welcome.rescan"))
                        .size(12)
                        .nowrap()
                        .color(p.text_secondary),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::WelcomeStartScan)
            .style(p.ghost_button(7.0))
            .height(Length::Fixed(30.0))
            .width(Length::Fill),
        ]
        .spacing(8)
        .height(Length::Fill),
    )
    .width(Length::Fixed(232.0))
    .height(Length::Fill)
    .padding(12)
    .style(p.card_style());

    let selected_launcher = state
        .selected_launcher
        .and_then(|i| state.detected.get(i));
    let selected_profile = selected_launcher
        .and_then(|l| state.selected_profile.and_then(|pi| l.profiles.get(pi)));

    let right_body: Element<'a, Message> = if let Some(launcher) = selected_launcher {
        let mut prof_list = Column::new().spacing(4).width(Length::Fill);
        for (pi, prof) in launcher.profiles.iter().enumerate() {
            let active = state.selected_profile == Some(pi);
            let stats = format_profile_stats(app, prof);
            prof_list = prof_list.push(
                Button::new(
                    row![
                        container(icon_svg(
                            loader_icon_svg(prof.loader),
                            15.0,
                            if active { p.text_primary } else { p.text_secondary },
                        ))
                        .width(32)
                        .height(32)
                        .align_x(Horizontal::Center)
                        .align_y(Vertical::Center)
                        .style(p.monogram_tile_style(active, 7.0)),
                        column![
                            row![
                                text(truncate_ellipsis(&prof.name, 34))
                                    .size(13)
                                    .medium()
                                    .nowrap()
                                    .color(p.text_primary),
                                container(
                                    text(&prof.mc_version)
                                        .size(10.5)
                                        .mono_medium()
                                        .nowrap()
                                        .color(p.text_secondary),
                                )
                                .padding([2, 6])
                                .style(p.inset_panel_style()),
                                text(prof.loader.display_name())
                                    .size(11)
                                    .nowrap()
                                    .color(p.text_muted),
                            ]
                            .spacing(7)
                            .align_y(Alignment::Center),
                            text(stats).size(11).nowrap().color(p.text_dim),
                        ]
                        .spacing(2)
                        .width(Length::Fill),
                        if active {
                            icon_svg(SVG_CHECK, 14.0, p.accent_green)
                        } else {
                            icon_svg(SVG_CHEVRON_RIGHT, 12.0, p.text_dim)
                        },
                    ]
                    .spacing(10)
                    .align_y(Alignment::Center)
                    .width(Length::Fill),
                )
                .on_press(Message::WelcomeSelectProfile(pi))
                .style(p.sidebar_instance(active))
                .width(Length::Fill)
                .padding([8, 10]),
            );
        }

        let mut detail = column![
            row![
                text(app.tr_fmt(
                    "welcome.builds_in",
                    &[("launcher", launcher.kind.display_name())],
                ))
                .size(12)
                .medium()
                .color(p.text_muted)
                .width(Length::Fill),
                text(truncate_ellipsis(&launcher.root_dir.to_string_lossy(), 42))
                    .size(10.5)
                    .mono()
                    .color(p.text_dim),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
            container(
                scrollable(prof_list).style(p.custom_scrollbar_style()),
            )
            .max_height(190.0)
            .width(Length::Fill),
        ]
        .spacing(8);

        if let Some(prof) = selected_profile {
            detail = detail.push(subtle_row_divider(p));

            if prof.proprietary_hud_notice {
                detail = detail.push(
                    container(
                        row![
                            icon_svg(SVG_ALERT, 15.0, p.accent_orange),
                            text(app.tr("welcome.lunar_hud_warning"))
                                .size(11.5)
                                .shaped()
                                .color(p.text_primary),
                        ]
                        .spacing(9)
                        .align_y(Alignment::Center),
                    )
                    .width(Length::Fill)
                    .padding([9, 12])
                    .style(move |_| container::Style {
                        background: Some(Background::Color(Color {
                            a: if p.is_dark { 0.12 } else { 0.14 },
                            ..p.accent_orange
                        })),
                        border: Border {
                            color: Color {
                                a: 0.35,
                                ..p.accent_orange
                            },
                            width: 1.0,
                            radius: 8.0.into(),
                        },
                        ..Default::default()
                    }),
                );
            }

            let name_row = row![
                text(app.tr("welcome.instance_name"))
                    .size(12)
                    .medium()
                    .color(p.text_secondary)
                    .width(Length::Fixed(130.0)),
                text_input(&prof.name, &state.selection.custom_name)
                    .on_input(Message::WelcomeCustomNameChanged)
                    .style(p.text_input_style())
                    .padding(Padding::new(6.0).left(10.0).right(10.0))
                    .size(12.5)
                    .width(Length::Fill),
            ]
            .spacing(10)
            .align_y(Alignment::Center);

            let opt_row = |label: String, checked: bool, enabled: bool, key: &'static str| {
                container(
                    row![
                        text(label)
                            .size(12)
                            .nowrap()
                            .color(if enabled {
                                p.text_primary
                            } else {
                                p.text_dim
                            })
                            .width(Length::Fill),
                        toggle_switch(
                            checked && enabled,
                            false,
                            if enabled {
                                Message::WelcomeToggleOption(key)
                            } else {
                                Message::NoOp
                            },
                            p,
                        ),
                    ]
                    .spacing(8)
                    .align_y(Alignment::Center),
                )
                .width(Length::FillPortion(1))
                .padding([6, 10])
                .style(p.inset_panel_style())
            };

            let toggles = column![
                row![
                    opt_row(
                        format!("{} ({})", app.tr("tab.mods"), prof.mod_count),
                        state.selection.copy_mods,
                        prof.mod_count > 0,
                        "mods",
                    ),
                    opt_row(
                        app.tr("welcome.opt.configs").to_string(),
                        state.selection.copy_config,
                        prof.has_config_dir,
                        "config",
                    ),
                ]
                .spacing(8),
                row![
                    opt_row(
                        format!("{} ({})", app.tr("tab.resourcepacks"), prof.resourcepack_count),
                        state.selection.copy_resourcepacks,
                        prof.resourcepack_count > 0,
                        "resourcepacks",
                    ),
                    opt_row(
                        format!("{} ({})", app.tr("tab.shaders"), prof.shader_count),
                        state.selection.copy_shaderpacks,
                        prof.shader_count > 0,
                        "shaderpacks",
                    ),
                ]
                .spacing(8),
                row![
                    opt_row(
                        format!("{} ({})", app.tr("welcome.opt.servers"), prof.server_count),
                        state.selection.copy_servers_dat,
                        prof.server_count > 0,
                        "servers",
                    ),
                    opt_row(
                        app.tr("welcome.opt.options_txt").to_string(),
                        state.selection.copy_options_txt,
                        prof.has_options_txt,
                        "options",
                    ),
                ]
                .spacing(8),
                row![
                    opt_row(
                        format!("{} ({})", app.tr("welcome.opt.worlds"), prof.world_count),
                        state.selection.copy_saves,
                        prof.world_count > 0,
                        "saves",
                    ),
                    opt_row(
                        format!("{} ({})", app.tr("tab.screenshots"), prof.screenshot_count),
                        state.selection.copy_screenshots,
                        prof.screenshot_count > 0,
                        "screenshots",
                    ),
                ]
                .spacing(8),
            ]
            .spacing(6);

            detail = detail.push(name_row).push(toggles);

            if app.is_visuals_eligible(prof.loader, &prof.mc_version) {
                detail = detail.push(
                    container(
                        row![
                            icon_svg(
                                SVG_NIGHTCLIENT,
                                15.0,
                                if state.selection.enable_nightclient {
                                    p.accent_green
                                } else {
                                    p.text_muted
                                },
                            ),
                            text(app.tr("modal.create.nc_label"))
                                .size(12)
                                .medium()
                                .color(p.text_primary)
                                .width(Length::Fill),
                            toggle_switch(
                                state.selection.enable_nightclient,
                                true,
                                Message::WelcomeToggleOption("nightclient"),
                                p,
                            ),
                        ]
                        .spacing(8)
                        .align_y(Alignment::Center),
                    )
                    .width(Length::Fill)
                    .padding([7, 10])
                    .style(p.inset_panel_style()),
                );
            }
        }

        scrollable(detail)
            .height(Length::Fill)
            .width(Length::Fill)
            .style(p.custom_scrollbar_style())
            .into()
    } else {
        container(
            column![
                icon_svg(SVG_FOLDER, 24.0, p.text_dim),
                text(app.tr("welcome.none_found_title"))
                    .size(15)
                    .medium()
                    .color(p.text_primary),
                text(app.tr("welcome.none_found_desc"))
                    .size(12.5)
                    .shaped()
                    .color(p.text_muted),
                Space::new().height(6),
                Button::new(vcenter(
                    row![
                        icon_svg(SVG_FOLDER, 13.5, p.cta_text),
                        text(app.tr("welcome.browse_folder"))
                            .size(12.5)
                            .medium()
                            .nowrap()
                            .color(p.cta_text),
                    ]
                    .spacing(6)
                    .align_y(Alignment::Center),
                ))
                .on_press(Message::WelcomeBrowseFolder)
                .style(p.primary_button())
                .height(Length::Fixed(36.0))
                .padding([0, 16]),
            ]
            .spacing(8)
            .align_x(Alignment::Center),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center)
        .into()
    };

    let right_panel = container(right_body)
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(14)
        .style(p.card_style());

    let mut import_btn = Button::new(vcenter(
        row![
            icon_svg(SVG_DOWNLOAD, 13.5, p.cta_text),
            text(app.tr("welcome.import_cta"))
                .size(13)
                .medium()
                .nowrap()
                .color(p.cta_text),
        ]
        .spacing(7)
        .align_y(Alignment::Center),
    ))
    .style(p.primary_button())
    .height(Length::Fixed(36.0))
    .padding([0, 18]);

    if selected_profile.is_some() {
        import_btn = import_btn.on_press(Message::WelcomeStartImport);
    }

    let footer = row![
        Button::new(vcenter(
            row![
                icon_svg(SVG_CHEVRON_LEFT, 13.0, p.text_secondary),
                text(app.tr("welcome.back")).size(12.5).medium().nowrap(),
            ]
            .spacing(5)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::WelcomeBackToIntro)
        .style(p.secondary_button())
        .height(Length::Fixed(34.0))
        .padding([0, 12]),
        Space::new().width(Length::Fill),
        Button::new(vcenter(
            text(app.tr("welcome.skip"))
                .size(12.5)
                .nowrap()
                .color(p.text_muted),
        ))
        .on_press(Message::WelcomeSkip)
        .style(p.ghost_button(7.0))
        .height(Length::Fixed(34.0))
        .padding([0, 12]),
        import_btn,
    ]
    .spacing(10)
    .align_y(Alignment::Center)
    .width(Length::Fill);

    container(
        column![
            view_top_utility_bar(app),
            row![
                icon_svg(SVG_SHIELD_CHECK, 13.0, p.accent_green),
                text(app.tr("welcome.privacy_short"))
                    .size(11.5)
                    .color(p.text_muted),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
            row![left_panel, right_panel]
                .spacing(12)
                .width(Length::Fill)
                .height(Length::Fill),
            footer,
        ]
        .spacing(12)
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .padding(Padding::new(0.0).left(24.0).right(24.0).bottom(18.0))
    .into()
}

fn view_importing_step<'a>(app: &'a App, name: &'a str) -> Element<'a, Message> {
    let p = app.palette;
    container(
        column![
            view_top_utility_bar(app),
            Space::new().height(Length::Fill),
            container(
                column![
                    icon_svg(SVG_DOWNLOAD, 24.0, p.accent_brand),
                    text(app.tr_fmt("welcome.importing_title", &[("name", name)]))
                        .size(16)
                        .medium()
                        .color(p.text_primary),
                    text(app.tr("welcome.importing_desc"))
                        .size(12.5)
                        .color(p.text_muted),
                ]
                .spacing(8)
                .align_x(Alignment::Center),
            )
            .padding(28)
            .style(p.card_style()),
            Space::new().height(Length::Fill),
        ]
        .align_x(Alignment::Center)
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .padding(Padding::new(0.0).left(28.0).right(28.0).bottom(24.0))
    .into()
}

fn view_done_step<'a>(app: &'a App, res: &'a nl_core::MigrationResult) -> Element<'a, Message> {
    let p = app.palette;
    let mb = format!("{:.1}", res.bytes_copied as f64 / 1_048_576.0);

    let mut card_col = column![
        container(icon_svg(SVG_CHECK, 22.0, p.accent_green))
            .width(48)
            .height(48)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .style(p.monogram_tile_style(true, 12.0)),
        text(app.tr("welcome.done_title"))
            .size(18)
            .medium()
            .color(p.text_primary),
        text(app.tr_fmt(
            "welcome.done_summary",
            &[
                ("name", &res.instance_name),
                ("files", &res.files_copied.to_string()),
                ("mb", &mb),
            ],
        ))
        .size(13)
        .shaped()
        .color(p.text_secondary),
    ]
    .spacing(8)
    .align_x(Alignment::Center);

    if res.skipped_nightclient_duplicates > 0 {
        card_col = card_col.push(
            text(app.tr_fmt(
                "welcome.done_skipped_nc",
                &[("count", &res.skipped_nightclient_duplicates.to_string())],
            ))
            .size(11.5)
            .color(p.text_muted),
        );
    }

    card_col = card_col.push(Space::new().height(10)).push(
        row![
            Button::new(vcenter(
                text(app.tr("welcome.import_another"))
                    .size(12.5)
                    .medium()
                    .nowrap(),
            ))
            .on_press(Message::WelcomeImportAnother)
            .style(p.secondary_button())
            .height(Length::Fixed(36.0))
            .padding([0, 14]),
            Button::new(vcenter(
                text(app.tr("welcome.open_launcher"))
                    .size(12.5)
                    .medium()
                    .nowrap()
                    .color(p.cta_text),
            ))
            .on_press(Message::WelcomeFinish)
            .style(p.primary_button())
            .height(Length::Fixed(36.0))
            .padding([0, 18]),
        ]
        .spacing(10)
        .align_y(Alignment::Center),
    );

    container(
        column![
            view_top_utility_bar(app),
            Space::new().height(Length::Fill),
            container(card_col)
                .max_width(480.0)
                .padding(28)
                .style(p.card_style()),
            Space::new().height(Length::Fill),
        ]
        .align_x(Alignment::Center)
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .padding(Padding::new(0.0).left(28.0).right(28.0).bottom(24.0))
    .into()
}

fn format_profile_stats(app: &App, prof: &DetectedProfile) -> String {
    let mut parts = Vec::new();
    if prof.mod_count > 0 {
        parts.push(app.tr_fmt("header.mods_count", &[("count", &prof.mod_count.to_string())]));
    }
    if prof.resourcepack_count > 0 {
        parts.push(format!("{} {}", prof.resourcepack_count, app.tr("tab.resourcepacks")));
    }
    if prof.shader_count > 0 {
        parts.push(format!("{} {}", prof.shader_count, app.tr("tab.shaders")));
    }
    if prof.server_count > 0 {
        parts.push(format!("{} {}", prof.server_count, app.tr("welcome.opt.servers")));
    }
    if prof.world_count > 0 {
        parts.push(format!("{} {}", prof.world_count, app.tr("welcome.opt.worlds")));
    }
    if parts.is_empty() {
        app.tr("welcome.empty_profile").to_string()
    } else {
        parts.join(" · ")
    }
}
