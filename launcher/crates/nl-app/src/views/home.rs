use crate::app::{
    ActiveModal, App, CloneInstanceForm, InstanceTab, Message, ModDetailsTab, PendingDelete,
};
use crate::i18n::I18nCatalog;
use crate::icons::{
    SVG_ALERT, SVG_BOOK, SVG_CHECK, SVG_CHEVRON_DOWN, SVG_CHEVRON_LEFT, SVG_CHEVRON_RIGHT,
    SVG_CLOCK, SVG_CLONE,
    SVG_CLOSE, SVG_COFFEE, SVG_COPY, SVG_CPU, SVG_DOWNLOAD, SVG_EDIT, SVG_EXTERNAL, SVG_FILE_TEXT,
    SVG_FOLDER, SVG_HEART, SVG_IMAGE, SVG_MESSAGE, SVG_MODRINTH, SVG_MORE, SVG_NIGHTCLIENT,
    SVG_PALETTE, SVG_PLAY, SVG_PLUS, SVG_PUZZLE, SVG_REFRESH, SVG_SEARCH, SVG_SERVER, SVG_SHADERS,
    SVG_SLIDERS, SVG_STOP, SVG_SWATCH, SVG_TAG, SVG_TERMINAL, SVG_TRASH, SVG_UPLOAD, SVG_VANILLA,
    SVG_WRENCH, SVG_ZAP, icon_svg, loader_icon_svg,
};
use crate::markdown_view;
use crate::theme::{Palette, TextExt, rgba, truncate_ellipsis};
use crate::widgets::{
    center_box, loader_segmented_track, loader_version_picker_row, subtle_row_divider,
    toggle_switch, vcenter,
};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{
    Button, Column, Row, Space, column, container, image, mouse_area, pick_list, row, scrollable,
    slider, stack, text, text_input,
};
use iced::{Alignment, Background, Border, Color, Element, Length, Padding};
use nl_content::{ContentKind, InstalledMod, ModrinthSort};
use nl_core::{CrashDiagnosis, InstanceRecord, InstanceStatus, LoaderKind};
use nl_java::PerformanceProfile;
use nl_night::VisualsCompatibility;

pub fn view_home(app: &App) -> Element<'_, Message> {
    if app.instances.is_empty() {
        return view_empty_workspace(app);
    }

    let Some(inst) = app.selected_instance().or_else(|| app.instances.first()) else {
        return view_empty_workspace(app);
    };

    let header = view_instance_header(app, inst);
    let nc_strip = view_nightclient_strip(app, inst);
    let tab_strip = view_tab_strip(app, inst);

    let body: Element<'_, Message> = match app.instance_tab {
        InstanceTab::Mods | InstanceTab::ResourcePacks | InstanceTab::Shaders => {
            if app.browse_content_active {
                if app.browse_detail.is_some() || app.browse_detail_loading {
                    view_modrinth_detail(app)
                } else {
                    view_modrinth_browse(app, inst)
                }
            } else {
                view_installed_list(app, inst)
            }
        }
        InstanceTab::Themes => view_themes_card(app, inst),
        InstanceTab::Screenshots => view_screenshots_card(app, inst),
        InstanceTab::Console => view_console_card(app, inst),
        InstanceTab::Options => view_options_card(app, inst),
    };

    let mut page_col = column![header, Space::new().height(10)]
        .width(Length::Fill)
        .height(Length::Fill);

    if let Some(strip) = nc_strip {
        page_col = page_col.push(strip).push(Space::new().height(10));
    }

    page_col = page_col.push(tab_strip).push(Space::new().height(10));

    if let Some(InstanceStatus::Crashed { diagnosis, .. }) = app.statuses.get(&inst.config.id) {
        page_col = page_col
            .push(view_crash_banner(app, &inst.config.id, diagnosis))
            .push(Space::new().height(10));
    }

    page_col = page_col.push(body);

    container(page_col)
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(Padding::new(0.0).left(22.0).right(22.0).bottom(18.0))
        .into()
}

fn view_empty_workspace(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    container(
        container(
            column![
                container(icon_svg(SVG_VANILLA, 22.0, p.text_secondary))
                    .width(44)
                    .height(44)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center)
                    .style(p.monogram_tile_style(true, 11.0)),
                Space::new().height(4),
                text(app.tr("empty.title"))
                    .size(18)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                text(app.tr("empty.subtitle"))
                    .size(13)
                    .shaped()
                    .color(p.text_muted),
                Space::new().height(8),
                row![
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_DOWNLOAD, 13.5, p.text_secondary),
                            text(app.tr("empty.migrate_launcher"))
                                .size(12.5)
                                .medium()
                                .nowrap(),
                        ]
                        .spacing(6)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::OpenMigrationWizard)
                    .style(p.secondary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(13.0).right(14.0)),
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_MODRINTH, 13.5, p.text_secondary),
                            text(app.tr("empty.import_mrpack")).size(12.5).medium().nowrap(),
                        ]
                        .spacing(6)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::ImportMrpackPick)
                    .style(p.secondary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(13.0).right(14.0)),
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_PLUS, 13.5, p.cta_text),
                            text(app.tr("empty.new_instance"))
                                .size(12.5)
                                .medium()
                                .nowrap()
                                .color(p.cta_text),
                        ]
                        .spacing(6)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::OpenCreateModal)
                    .style(p.primary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(14.0).right(16.0)),
                ]
                .spacing(8)
                .align_y(Alignment::Center),
            ]
            .spacing(6)
            .align_x(Alignment::Center),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center)
        .style(p.card_style()),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .padding(Padding::new(4.0).left(22.0).right(22.0).bottom(18.0))
    .into()
}

fn view_instance_header<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;
    let is_compact = app.is_compact();
    let mem_mb = inst
        .config
        .memory_mb_override
        .unwrap_or(app.config.default_memory_mb);
    let mem_gb = format!("{:.1}", mem_mb as f32 / 1024.0);

    let meta_badge = |ic: &'static str, txt: String| -> Element<'a, Message> {
        row![
            icon_svg(ic, 12.0, p.text_dim),
            text(txt).size(12).nowrap().color(p.text_muted),
        ]
        .spacing(4.5)
        .align_y(Alignment::Center)
        .into()
    };

    let loader_text = if !is_compact
        && inst.config.loader != LoaderKind::Vanilla
        && let Some(ref lv) = inst.config.loader_version
    {
        format!("{} {lv}", inst.config.loader.display_name())
    } else {
        inst.config.loader.display_name().to_string()
    };

    let mut meta_row = row![
        meta_badge(SVG_VANILLA, inst.config.mc_version.clone()),
        meta_badge(loader_icon_svg(inst.config.loader), loader_text),
    ]
    .spacing(if is_compact { 10 } else { 14 })
    .align_y(Alignment::Center);

    if inst.config.loader != LoaderKind::Vanilla || !app.installed_mods.is_empty() {
        meta_row = meta_row.push(meta_badge(
            SVG_PUZZLE,
            app.tr_fmt(
                "header.mods_count",
                &[("count", &app.installed_mods.len().to_string())],
            ),
        ));
    }
    meta_row = meta_row.push(meta_badge(
        SVG_CPU,
        app.tr_fmt("header.memory_gb", &[("gb", &mem_gb)]),
    ));

    let emblem = container(icon_svg(
        loader_icon_svg(inst.config.loader),
        20.0,
        p.text_primary,
    ))
    .width(42)
    .height(42)
    .align_x(Horizontal::Center)
    .align_y(Vertical::Center)
    .style(p.monogram_tile_style(true, 10.0));

    let folder_btn = Button::new(
        container(icon_svg(SVG_FOLDER, 15.0, p.text_secondary))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center),
    )
    .on_press(Message::OpenInstanceFolder(inst.config.id.clone()))
    .style(p.secondary_button())
    .width(Length::Fixed(38.0))
    .height(Length::Fixed(38.0))
    .padding(0);

    let more_btn = Button::new(
        container(icon_svg(SVG_MORE, 15.0, p.text_secondary))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center),
    )
    .on_press(Message::OpenModal(ActiveModal::InstanceContextMenu(
        inst.config.id.clone(),
    )))
    .style(p.secondary_button())
    .width(Length::Fixed(38.0))
    .height(Length::Fixed(38.0))
    .padding(0);

    let status = app.statuses.get(&inst.config.id);
    let play_btn: Element<'a, Message> = match status {
        Some(InstanceStatus::Preparing { .. }) => Button::new(vcenter(
            text(app.tr("header.preparing"))
                .size(12.5)
                .mono_medium()
                .nowrap()
                .color(p.text_primary),
        ))
        .on_press(Message::CancelPrepare(inst.config.id.clone()))
        .style(p.secondary_button())
        .height(Length::Fixed(38.0))
        .padding(Padding::new(0.0).left(16.0).right(16.0))
        .into(),
        Some(InstanceStatus::Running { .. }) => {
            let elapsed_str = app
                .elapsed_secs(&inst.config.id)
                .map(|s| app.i18n.format_elapsed(s));
            let mut inner = row![
                icon_svg(SVG_STOP, 11.0, p.accent_red),
                text(app.tr("header.stop"))
                    .size(12.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
            ]
            .spacing(7)
            .align_y(Alignment::Center);
            if let Some(elapsed) = elapsed_str {
                inner = inner.push(
                    row![
                        icon_svg(SVG_CLOCK, 11.5, p.accent_green),
                        text(elapsed)
                            .size(11.5)
                            .mono_medium()
                            .nowrap()
                            .color(p.accent_green),
                    ]
                    .spacing(4)
                    .align_y(Alignment::Center),
                );
            }
            Button::new(vcenter(inner))
                .on_press(Message::StopInstance {
                    id: inst.config.id.clone(),
                    force: false,
                })
                .style(p.secondary_button())
                .height(Length::Fixed(38.0))
                .padding(Padding::new(0.0).left(14.0).right(15.0))
                .into()
        }
        Some(InstanceStatus::Stopping) => Button::new(vcenter(
            row![
                icon_svg(SVG_STOP, 11.0, p.accent_orange),
                text(app.tr("header.force_kill"))
                    .size(12.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::StopInstance {
            id: inst.config.id.clone(),
            force: true,
        })
        .style(p.secondary_button())
        .height(Length::Fixed(38.0))
        .padding(Padding::new(0.0).left(14.0).right(16.0))
        .into(),
        _ => {
            let play_main = Button::new(vcenter(
                row![
                    icon_svg(SVG_PLAY, 12.0, p.cta_text),
                    text(app.tr("header.play"))
                        .size(13.5)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(8)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::LaunchInstance {
                id: inst.config.id.clone(),
                quick_play: None,
            })
            .style(p.primary_split_left())
            .height(Length::Fixed(38.0))
            .padding(Padding::new(0.0).left(18.0).right(15.0));

            let split_sep = container(Space::new())
                .width(Length::Fixed(1.0))
                .height(Length::Fixed(38.0))
                .style(move |_| container::Style {
                    background: Some(Background::Color(Color {
                        a: 0.22,
                        ..p.cta_text
                    })),
                    ..Default::default()
                });

            let servers_toggle = Button::new(
                container(icon_svg(SVG_CHEVRON_DOWN, 13.5, p.cta_text))
                    .width(Length::Fill)
                    .height(Length::Fill)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center),
            )
            .on_press(Message::ToggleServersPopover)
            .style(p.primary_split_right(app.servers_popover_open))
            .width(Length::Fixed(32.0))
            .height(Length::Fixed(38.0))
            .padding(0);

            row![play_main, split_sep, servers_toggle]
                .spacing(0)
                .align_y(Alignment::Center)
                .into()
        }
    };

    let name_display = truncate_ellipsis(&inst.config.name, if is_compact { 22 } else { 36 });

    row![
        emblem,
        container(
            column![
                text(name_display)
                    .size(21)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                meta_row,
            ]
            .spacing(3)
            .width(Length::Fill),
        )
        .width(Length::Fill)
        .clip(true),
        row![folder_btn, more_btn, play_btn]
            .spacing(8)
            .align_y(Alignment::Center),
    ]
    .spacing(12)
    .align_y(Alignment::Center)
    .width(Length::Fill)
    .into()
}

fn view_nightclient_strip<'a>(
    app: &'a App,
    inst: &'a InstanceRecord,
) -> Option<Element<'a, Message>> {
    if inst.config.loader != LoaderKind::Fabric {
        return None;
    }
    let p = app.palette;
    let compat = app.visuals_compat_for(inst);

    match compat {
        VisualsCompatibility::Ready => {
            let armed = inst.config.nightclient_enabled;
            let mut content = row![
                icon_svg(
                    SVG_NIGHTCLIENT,
                    14.5,
                    if armed { p.accent_green } else { p.text_muted },
                ),
                text(app.tr("nc.title"))
                    .size(12)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                Space::new().width(Length::Fill),
            ]
            .spacing(8)
            .align_y(Alignment::Center)
            .width(Length::Fill);

            if armed && !app.is_compact() {
                let turbo_on = inst.config.turbo_mode || app.config.default_turbo_mode;
                content = content.push(
                    Button::new(vcenter(
                        row![
                            icon_svg(
                                SVG_ZAP,
                                11.5,
                                if turbo_on { p.accent_green } else { p.text_muted },
                            ),
                            text(app.tr("options.turbo_badge"))
                                .size(11)
                                .medium()
                                .nowrap()
                                .color(if turbo_on { p.accent_green } else { p.text_muted }),
                        ]
                        .spacing(4)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::ToggleInstanceTurbo(
                        inst.config.id.clone(),
                        !inst.config.turbo_mode,
                    ))
                    .style(p.ghost_button(5.0))
                    .height(Length::Fixed(24.0))
                    .padding(Padding::new(0.0).left(7.0).right(7.0)),
                );

                let profile_label = if inst.config.nightclient_shared_profile {
                    app.tr("nc.shared_profile")
                } else {
                    app.tr("nc.isolated_profile")
                };
                content = content.push(
                    Button::new(vcenter(
                        text(profile_label).size(11).nowrap().color(p.text_muted),
                    ))
                    .on_press(Message::ToggleInstanceSharedProfile(
                        inst.config.id.clone(),
                        !inst.config.nightclient_shared_profile,
                    ))
                    .style(p.ghost_button(5.0))
                    .height(Length::Fixed(24.0))
                    .padding(Padding::new(0.0).left(7.0).right(7.0)),
                );
            }

            content = content.push(toggle_switch(
                armed,
                true,
                Message::ToggleInstanceVisuals(inst.config.id.clone(), !armed),
                p,
            ));

            Some(
                container(content)
                    .width(Length::Fill)
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(12.0).right(10.0))
                    .align_y(Vertical::Center)
                    .style(p.card_style())
                    .into(),
            )
        }
        VisualsCompatibility::NeedsFabricUpgrade { required, .. } => {
            let notice = app.tr_fmt("nc.status.needs_upgrade", &[("required", &required)]);
            let content = row![
                icon_svg(SVG_NIGHTCLIENT, 14.5, p.text_muted),
                text(app.tr("nc.title"))
                    .size(12)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                text(notice).size(11).nowrap().color(p.text_muted),
                Space::new().width(Length::Fill),
                Button::new(vcenter(
                    text(app.tr("nc.action.upgrade_fabric"))
                        .size(11.5)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                ))
                .on_press(Message::UpgradeInstanceFabric(
                    inst.config.id.clone(),
                    required,
                ))
                .style(p.secondary_button())
                .height(Length::Fixed(24.0))
                .padding(Padding::new(0.0).left(9.0).right(9.0)),
            ]
            .spacing(8)
            .align_y(Alignment::Center)
            .width(Length::Fill);

            Some(
                container(content)
                    .width(Length::Fill)
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(12.0).right(10.0))
                    .align_y(Vertical::Center)
                    .style(p.card_style())
                    .into(),
            )
        }
        VisualsCompatibility::NeedsSwitchToFabric { .. }
        | VisualsCompatibility::IncompatibleLoader { .. }
        | VisualsCompatibility::UnsupportedMcVersion { .. } => None,
    }
}

fn view_tab_strip<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;
    let is_compact = app.is_compact();
    let tab_text_size = if is_compact { 11.5 } else { 12.5 };
    let tab_icon_size = if is_compact { 12.0 } else { 13.0 };
    let tab_spacing = if is_compact { 4.0 } else { 5.5 };
    let tab_pad_x = if is_compact { 7.0 } else { 10.0 };

    let tab_item = |tab: InstanceTab, ic: &'static str, key: &'a str, count: Option<usize>| {
        let active = app.instance_tab == tab;
        let icon_color = if active { p.text_primary } else { p.text_muted };
        let label_text = if active {
            text(app.tr(key))
                .size(tab_text_size)
                .medium()
                .nowrap()
                .color(p.text_primary)
        } else {
            text(app.tr(key))
                .size(tab_text_size)
                .nowrap()
                .color(p.text_muted)
        };

        let mut inner = row![icon_svg(ic, tab_icon_size, icon_color), label_text]
            .spacing(tab_spacing)
            .align_y(Alignment::Center);

        if let Some(c) = count
            && c > 0
        {
            inner = inner.push(
                text(c.to_string())
                    .size(if is_compact { 10.5 } else { 11.0 })
                    .mono()
                    .nowrap()
                    .color(if active { p.text_secondary } else { p.text_dim }),
            );
        }

        Button::new(vcenter(inner))
            .on_press(Message::SelectInstanceTab(tab))
            .style(p.segmented_button(active))
            .height(Length::Fixed(28.0))
            .padding(Padding::new(0.0).left(tab_pad_x).right(tab_pad_x))
    };

    let tabs_track = container(
        row![
            tab_item(InstanceTab::Mods, SVG_PUZZLE, "tab.mods", Some(app.installed_mods.len())),
            tab_item(
                InstanceTab::ResourcePacks,
                SVG_PALETTE,
                "tab.packs",
                Some(app.installed_packs.len()),
            ),
            tab_item(
                InstanceTab::Shaders,
                SVG_SHADERS,
                "tab.shaders",
                Some(app.installed_shaders.len()),
            ),
            tab_item(InstanceTab::Themes, SVG_SWATCH, "tab.themes", None),
            tab_item(
                InstanceTab::Screenshots,
                SVG_IMAGE,
                "tab.screenshots",
                Some(app.screenshots.len()),
            ),
            tab_item(InstanceTab::Console, SVG_TERMINAL, "tab.console", None),
            tab_item(InstanceTab::Options, SVG_SLIDERS, "tab.options", None),
        ]
        .spacing(2)
        .align_y(Alignment::Center),
    )
    .padding(3)
    .style(p.segmented_track_style());

    let mut strip = column![tabs_track].spacing(8).width(Length::Fill);

    if let Some(InstanceStatus::Preparing { stage, progress }) = app.statuses.get(&inst.config.id)
    {
        let stage_detail = app.i18n.stage_label(stage);
        let filled = ((progress.clamp(0.03, 1.0) * 100.0).round() as u16).clamp(2, 100);
        let rest = 100u16.saturating_sub(filled);
        let mut bar_row = row![
            container(Space::new().height(2))
                .width(Length::FillPortion(filled))
                .height(2)
                .style(move |_| container::Style {
                    background: Some(Background::Color(p.accent_green)),
                    border: Border {
                        radius: 1.0.into(),
                        ..Default::default()
                    },
                    ..Default::default()
                }),
        ];
        if rest > 0 {
            bar_row = bar_row.push(
                container(Space::new().height(2))
                    .width(Length::FillPortion(rest))
                    .height(2)
                    .style(move |_| container::Style {
                        background: Some(Background::Color(p.hairline)),
                        ..Default::default()
                    }),
            );
        }
        strip = strip.push(
            column![
                row![
                    text(stage_detail).size(11.5).nowrap().color(p.text_secondary),
                    Space::new().width(Length::Fill),
                    text(format!("{}%", (progress * 100.0).round() as u32))
                        .size(11)
                        .mono()
                        .color(p.text_muted),
                ]
                .align_y(Alignment::Center),
                bar_row.width(Length::Fill),
            ]
            .spacing(4),
        );
    }

    strip.into()
}

fn view_crash_banner<'a>(
    app: &'a App,
    instance_id: &'a str,
    diag: &'a CrashDiagnosis,
) -> Element<'a, Message> {
    let p = app.palette;
    let (title, summary) = app.i18n.crash_copy(&diag.kind);

    let mut fixes = Row::new().spacing(6).align_y(Alignment::Center);
    if let Some(ref fix) = diag.fix_action {
        fixes = fixes.push(
            Button::new(vcenter(
                text(app.i18n.crash_fix_label(fix))
                    .size(11.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
            ))
            .on_press(Message::ApplyCrashFix(instance_id.to_string(), fix.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(28.0))
            .padding([0, 10]),
        );
    }
    fixes = fixes
        .push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_EXTERNAL, 11.5, p.text_secondary),
                    text(app.tr("crash.upload_logs"))
                        .size(11.5)
                        .medium()
                        .nowrap()
                        .color(p.text_secondary),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::UploadCrashLog(instance_id.to_string()))
            .style(p.ghost_button(6.0))
            .height(Length::Fixed(28.0))
            .padding([0, 8]),
        )
        .push(
            Button::new(
                container(icon_svg(SVG_CLOSE, 12.5, p.text_muted))
                    .width(Length::Fill)
                    .height(Length::Fill)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center),
            )
            .on_press(Message::DismissCrash(instance_id.to_string()))
            .style(p.ghost_button(6.0))
            .width(Length::Fixed(26.0))
            .height(Length::Fixed(26.0))
            .padding(0),
        );

    container(
        row![
            icon_svg(SVG_ALERT, 16.0, p.accent_red),
            column![
                text(title).size(13).medium().nowrap().color(p.text_primary),
                text(summary).size(11.5).shaped().color(p.text_muted),
            ]
            .spacing(2)
            .width(Length::Fill),
            fixes,
        ]
        .spacing(12)
        .align_y(Alignment::Center),
    )
    .width(Length::Fill)
    .padding([10, 14])
    .style(p.card_style())
    .into()
}

fn card_toolbar<'a>(content: Element<'a, Message>, p: Palette) -> Element<'a, Message> {
    column![
        container(content)
            .width(Length::Fill)
            .padding(Padding::new(10.0).left(12.0).right(12.0))
            .align_y(Vertical::Center),
        subtle_row_divider(p),
    ]
    .width(Length::Fill)
    .into()
}

fn view_installed_list<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;
    let kind = app.current_content_kind();
    let (filter_key, empty_key, empty_icon, items): (&str, &str, &'static str, &[InstalledMod]) =
        match kind {
            ContentKind::Mod => (
                "content.filter.mods",
                "content.empty.mods",
                SVG_PUZZLE,
                &app.installed_mods,
            ),
            ContentKind::ResourcePack => (
                "content.filter.packs",
                "content.empty.packs",
                SVG_PALETTE,
                &app.installed_packs,
            ),
            ContentKind::Shader => (
                "content.filter.shaders",
                "content.empty.shaders",
                SVG_SHADERS,
                &app.installed_shaders,
            ),
            ContentKind::Modpack => (
                "content.filter.mods",
                "content.empty.mods",
                SVG_PUZZLE,
                &app.installed_mods,
            ),
        };

    let filter_box = container(
        row![
            icon_svg(SVG_SEARCH, 13.0, p.text_dim),
            text_input(app.tr(filter_key), &app.content_filter)
                .on_input(Message::ContentFilterChanged)
                .style(p.bare_text_input_style())
                .padding(Padding::new(0.0).left(7.0).right(8.0))
                .size(12)
                .width(Length::Fill),
        ]
        .align_y(Alignment::Center)
        .height(Length::Fill)
        .padding(Padding::new(0.0).left(10.0)),
    )
    .width(Length::Fill)
    .height(Length::Fixed(32.0))
    .align_y(Vertical::Center)
    .style(p.input_shell_style());

    let mut tools = row![filter_box].spacing(8).align_y(Alignment::Center);

    if kind == ContentKind::Mod {
        tools = tools.push(
            Button::new(
                container(icon_svg(
                    SVG_REFRESH,
                    14.0,
                    if app.checking_updates {
                        p.accent_green
                    } else {
                        p.text_secondary
                    },
                ))
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center),
            )
            .on_press_maybe(
                (!app.checking_updates)
                    .then_some(Message::CheckContentUpdates(inst.config.id.clone())),
            )
            .style(p.secondary_button())
            .width(Length::Fixed(32.0))
            .height(Length::Fixed(32.0))
            .padding(0),
        );
    }

    tools = tools
        .push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_UPLOAD, 13.0, p.text_secondary),
                    text(app.tr("content.import"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::ImportContentFilePick(inst.config.id.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding(Padding::new(0.0).left(10.0).right(12.0)),
        )
        .push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_MODRINTH, 13.0, p.text_primary),
                    text(app.tr("content.modrinth"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                ]
                .spacing(5.5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::SetBrowseModrinth(true))
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding(Padding::new(0.0).left(10.0).right(12.0)),
        );

    let toolbar = card_toolbar(tools.width(Length::Fill).into(), p);

    let q = app.content_filter.trim().to_lowercase();
    let visible: Vec<&InstalledMod> = items
        .iter()
        .filter(|f| {
            q.is_empty()
                || f.name.to_lowercase().contains(&q)
                || f.filename.to_lowercase().contains(&q)
                || f.description.to_lowercase().contains(&q)
        })
        .collect();

    if visible.is_empty() {
        let title_str = if items.is_empty() {
            app.tr(empty_key)
        } else {
            app.tr("content.empty.no_matches")
        };
        let empty_body = container(
            column![
                container(icon_svg(empty_icon, 20.0, p.text_secondary))
                    .width(42)
                    .height(42)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center)
                    .style(p.monogram_tile_style(true, 10.0)),
                Space::new().height(4),
                text(title_str)
                    .size(15)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                text(app.tr("content.empty.hint"))
                    .size(12.5)
                    .shaped()
                    .color(p.text_muted),
                Space::new().height(6),
                row![
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_UPLOAD, 12.5, p.text_secondary),
                            text(app.tr("content.import_file"))
                                .size(12)
                                .medium()
                                .nowrap(),
                        ]
                        .spacing(6)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::ImportContentFilePick(inst.config.id.clone()))
                    .style(p.secondary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(13.0).right(14.0)),
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_MODRINTH, 13.0, p.cta_text),
                            text(app.tr("content.browse_modrinth"))
                                .size(12)
                                .medium()
                                .nowrap()
                                .color(p.cta_text),
                        ]
                        .spacing(6)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::SetBrowseModrinth(true))
                    .style(p.primary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(14.0).right(16.0)),
                ]
                .spacing(8),
            ]
            .spacing(6)
            .align_x(Alignment::Center),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(20)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center);

        return container(
            column![toolbar, empty_body]
                .width(Length::Fill)
                .height(Length::Fill),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .clip(true)
        .style(p.card_style())
        .into();
    }

    let is_compact = app.is_compact();
    let title_budget = if is_compact { 24 } else { 38 };
    let desc_budget = if is_compact { 38 } else { 68 };

    let mut list = Column::new()
        .spacing(0)
        .width(Length::Fill)
        .padding(Padding::new(0.0).right(10.0));

    for (idx, file) in visible.into_iter().enumerate() {
        if idx > 0 {
            list = list.push(subtle_row_divider(p));
        }

        let is_hovered = app.hovered_content_file.as_deref() == Some(file.filename.as_str());
        let icon_el: Element<'a, Message> = if let Some(ref icon_path) = file.icon_path {
            container(
                image(image::Handle::from_path(icon_path))
                    .width(32)
                    .height(32)
                    .content_fit(iced::ContentFit::Cover)
                    .border_radius(7.0),
            )
            .width(32)
            .height(32)
            .style(move |_| container::Style {
                border: Border {
                    radius: 7.0.into(),
                    color: p.hairline,
                    width: 1.0,
                },
                ..Default::default()
            })
            .into()
        } else {
            let initial = file
                .name
                .chars()
                .find(|c| c.is_alphanumeric())
                .unwrap_or('M')
                .to_uppercase()
                .to_string();
            let seed = file.name.clone();
            container(
                text(initial)
                    .size(12)
                    .mono_medium()
                    .nowrap()
                    .color(if file.enabled { p.text_primary } else { p.text_dim }),
            )
            .width(32)
            .height(32)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .style(move |_| p.avatar_style_for_name(&seed))
            .into()
        };

        let mut title_line = row![
            text(truncate_ellipsis(&file.name, title_budget))
                .size(13)
                .medium()
                .nowrap()
                .color(if file.enabled { p.text_primary } else { p.text_dim }),
        ]
        .spacing(8)
        .align_y(Alignment::Center);

        if !file.enabled {
            title_line = title_line.push(
                container(
                    text(app.tr("content.disabled"))
                        .size(10.5)
                        .nowrap()
                        .color(p.text_muted),
                )
                .padding(Padding::new(1.5).left(6.0).right(6.0))
                .style(p.monogram_tile_style(false, 5.0)),
            );
        }

        let sub_line: Element<'a, Message> = if !file.description.trim().is_empty() {
            let first_line = file.description.lines().next().unwrap_or(&file.description);
            text(truncate_ellipsis(first_line, desc_budget))
                .size(11.5)
                .nowrap()
                .color(if file.enabled { p.text_muted } else { p.text_dim })
                .into()
        } else {
            text(truncate_ellipsis(&file.filename, desc_budget))
                .size(11)
                .mono()
                .nowrap()
                .color(p.text_dim)
                .into()
        };

        let mut right_col = row![].spacing(10).align_y(Alignment::Center);
        if !file.version.trim().is_empty() {
            right_col = right_col.push(
                text(truncate_ellipsis(&file.version, if is_compact { 12 } else { 18 }))
                    .size(11.5)
                    .mono()
                    .nowrap()
                    .color(p.text_muted),
            );
        }

        right_col = right_col
            .push(toggle_switch(
                file.enabled,
                false,
                Message::ToggleContentFile(file.path.clone()),
                p,
            ))
            .push(
                Button::new(
                    container(icon_svg(
                        SVG_TRASH,
                        14.0,
                        if is_hovered { p.accent_red } else { p.text_dim },
                    ))
                    .width(Length::Fill)
                    .height(Length::Fill)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center),
                )
                .on_press(Message::RequestDelete(PendingDelete::ContentFile {
                    path: file.path.clone(),
                    name: file.name.clone(),
                }))
                .style(p.ghost_button(6.0))
                .width(Length::Fixed(28.0))
                .height(Length::Fixed(28.0))
                .padding(0),
            );

        let row_box = container(
            row![
                icon_el,
                container(column![title_line, sub_line].spacing(2).width(Length::Fill))
                    .width(Length::Fill)
                    .clip(true),
                right_col,
            ]
            .spacing(12)
            .align_y(Alignment::Center)
            .width(Length::Fill),
        )
        .height(Length::Fixed(54.0))
        .padding(Padding::new(0.0).left(14.0).right(10.0))
        .align_y(Vertical::Center)
        .style(move |_| container::Style {
            background: Some(Background::Color(if is_hovered {
                p.bg_row_hover
            } else {
                Color::TRANSPARENT
            })),
            ..Default::default()
        });

        let fname = file.filename.clone();
        list = list.push(
            mouse_area(row_box)
                .on_enter(Message::ToggleModRowHover(Some(fname)))
                .on_exit(Message::ToggleModRowHover(None)),
        );
    }

    container(
        column![
            toolbar,
            scrollable(list)
                .height(Length::Fill)
                .width(Length::Fill)
                .style(p.custom_scrollbar_style()),
        ]
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

fn view_modrinth_browse<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;
    let kind = app.current_content_kind();

    let back_btn = Button::new(vcenter(
        row![
            icon_svg(SVG_CHEVRON_LEFT, 14.0, p.text_secondary),
            text(app.tr("browse.back"))
                .size(12)
                .medium()
                .nowrap()
                .color(p.text_primary),
        ]
        .spacing(4)
        .align_y(Alignment::Center),
    ))
    .on_press(Message::SetBrowseModrinth(false))
    .style(p.secondary_button())
    .height(Length::Fixed(32.0))
    .padding(Padding::new(0.0).left(8.0).right(11.0));

    let search_box = container(
        row![
            icon_svg(SVG_SEARCH, 13.0, p.text_dim),
            text_input(app.tr("browse.search_placeholder"), &app.browse_query)
                .on_input(Message::BrowseQueryChanged)
                .on_submit(Message::BrowseSearchSubmit)
                .style(p.bare_text_input_style())
                .padding(Padding::new(0.0).left(7.0).right(8.0))
                .size(12)
                .width(Length::Fill),
        ]
        .align_y(Alignment::Center)
        .height(Length::Fill)
        .padding(Padding::new(0.0).left(10.0)),
    )
    .width(Length::Fill)
    .height(Length::Fixed(32.0))
    .align_y(Vertical::Center)
    .style(p.input_shell_style());

    let sort_btn = |s: ModrinthSort, key: &'a str| {
        let active = app.browse_sort == s;
        Button::new(vcenter(text(app.tr(key)).size(11.5).medium().nowrap()))
            .on_press(Message::BrowseSortChanged(s))
            .style(p.segmented_button(active))
            .height(Length::Fixed(26.0))
            .padding([0, 9])
    };

    let sort_track = container(
        row![
            sort_btn(ModrinthSort::Downloads, "browse.sort.downloads"),
            sort_btn(ModrinthSort::Updated, "browse.sort.updated"),
            sort_btn(ModrinthSort::Relevance, "browse.sort.relevance"),
        ]
        .spacing(2),
    )
    .padding(3)
    .style(p.segmented_track_style());

    let mut tools = row![back_btn, search_box, sort_track]
        .spacing(8)
        .align_y(Alignment::Center);

    if kind == ContentKind::Mod {
        tools = tools.push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_ZAP, 13.0, p.text_secondary),
                    text(app.tr("browse.fps_pack")).size(12).medium().nowrap(),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press_maybe(
                (!app.installing_fps_pack).then_some(Message::InstallFpsPack(inst.config.id.clone())),
            )
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding([0, 10]),
        );
    }

    let toolbar = card_toolbar(tools.width(Length::Fill).into(), p);

    let body: Element<'a, Message> = if app.browse_loading {
        container(text(app.tr("browse.loading")).size(12.5).color(p.text_muted))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .into()
    } else if app.browse_hits.is_empty() {
        container(text(app.tr("browse.empty")).size(12.5).color(p.text_muted))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .into()
    } else {
        let installed_ids = app.installed_project_ids();
        let mut list = Column::new()
            .spacing(0)
            .width(Length::Fill)
            .padding(Padding::new(0.0).right(10.0));

        for (idx, hit) in app.browse_hits.iter().enumerate() {
            if idx > 0 {
                list = list.push(subtle_row_divider(p));
            }
            let installing = app
                .installing_projects
                .get(&hit.project_id)
                .copied()
                .unwrap_or(false);
            let already_installed = installed_ids.contains(&hit.slug)
                || installed_ids.contains(&hit.project_id);

            let icon_el: Element<'a, Message> = if let Some(path) = app.project_icons.get(&hit.project_id) {
                container(
                    image(image::Handle::from_path(path))
                        .width(34)
                        .height(34)
                        .content_fit(iced::ContentFit::Cover)
                        .border_radius(7.0),
                )
                .width(34)
                .height(34)
                .style(move |_| container::Style {
                    border: Border {
                        radius: 7.0.into(),
                        color: p.hairline,
                        width: 1.0,
                    },
                    ..Default::default()
                })
                .into()
            } else {
                let initial = hit
                    .title
                    .chars()
                    .find(|c| c.is_alphanumeric())
                    .unwrap_or('M')
                    .to_uppercase()
                    .to_string();
                let seed = hit.title.clone();
                container(text(initial).size(12).mono_medium().color(p.text_primary))
                    .width(34)
                    .height(34)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center)
                    .style(move |_| p.avatar_style_for_name(&seed))
                    .into()
            };

            let action_btn: Element<'a, Message> = if already_installed {
                container(
                    row![
                        icon_svg(SVG_CHECK, 12.5, p.accent_green),
                        text(app.tr("browse.installed"))
                            .size(11.5)
                            .medium()
                            .nowrap()
                            .color(p.accent_green),
                    ]
                    .spacing(5)
                    .align_y(Alignment::Center),
                )
                .padding([0, 10])
                .into()
            } else {
                Button::new(vcenter(
                    text(if installing {
                        app.tr("browse.installing")
                    } else {
                        app.tr("browse.install")
                    })
                    .size(12)
                    .medium()
                    .nowrap(),
                ))
                .on_press_maybe(if installing {
                    None
                } else {
                    Some(Message::BrowseInstallProject(hit.project_id.clone(), kind))
                })
                .style(p.secondary_button())
                .height(Length::Fixed(30.0))
                .padding([0, 12])
                .into()
            };

            let open_area = mouse_area(
                row![
                    icon_el,
                    container(
                        column![
                            row![
                                text(truncate_ellipsis(&hit.title, 34))
                                    .size(13)
                                    .medium()
                                    .nowrap()
                                    .color(p.text_primary),
                                text(app.tr_fmt("browse.by_author", &[("author", &hit.author)]))
                                    .size(11.5)
                                    .nowrap()
                                    .color(p.text_dim),
                            ]
                            .spacing(8)
                            .align_y(Alignment::Center),
                            text(truncate_ellipsis(&hit.description, 72))
                                .size(11.5)
                                .nowrap()
                                .color(p.text_muted),
                        ]
                        .spacing(2)
                        .width(Length::Fill),
                    )
                    .width(Length::Fill)
                    .clip(true),
                    text(I18nCatalog::format_downloads(hit.downloads))
                        .size(11.5)
                        .mono()
                        .nowrap()
                        .color(p.text_muted),
                ]
                .spacing(12)
                .align_y(Alignment::Center)
                .width(Length::Fill),
            )
            .on_press(Message::BrowseOpenProject(hit.project_id.clone()))
            .interaction(iced::mouse::Interaction::Pointer);

            let row_item = container(
                row![open_area, action_btn]
                    .spacing(12)
                    .align_y(Alignment::Center)
                    .width(Length::Fill),
            )
            .height(Length::Fixed(58.0))
            .padding(Padding::new(0.0).left(14.0).right(12.0))
            .align_y(Vertical::Center);

            list = list.push(row_item);
        }

        scrollable(list)
            .height(Length::Fill)
            .width(Length::Fill)
            .style(p.custom_scrollbar_style())
            .into()
    };

    container(
        column![toolbar, body]
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

fn view_modrinth_detail(app: &App) -> Element<'_, Message> {
    let p = app.palette;

    let back_btn = Button::new(vcenter(
        row![
            icon_svg(SVG_CHEVRON_LEFT, 14.0, p.text_secondary),
            text(app.tr("browse.back")).size(12).medium().nowrap(),
        ]
        .spacing(4)
        .align_y(Alignment::Center),
    ))
    .on_press(Message::BrowseCloseProject)
    .style(p.secondary_button())
    .height(Length::Fixed(30.0))
    .padding(Padding::new(0.0).left(8.0).right(11.0));

    let Some(ref detail) = app.browse_detail else {
        let toolbar = card_toolbar(
            row![back_btn, Space::new().width(Length::Fill)]
                .align_y(Alignment::Center)
                .into(),
            p,
        );
        let body = container(
            text(app.tr("browse.detail.loading"))
                .size(13)
                .color(p.text_muted),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center);

        return container(
            column![toolbar, body]
                .width(Length::Fill)
                .height(Length::Fill),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .clip(true)
        .style(p.card_style())
        .into();
    };

    let installed_ids = app.installed_project_ids();
    let already_installed =
        installed_ids.contains(&detail.id) || installed_ids.contains(&detail.slug);
    let installing = app
        .installing_projects
        .get(&detail.id)
        .copied()
        .unwrap_or(false);

    let icon_handle = detail
        .icon_url
        .as_ref()
        .and_then(|u| app.remote_images.get(u))
        .map(|r| r.handle.clone())
        .or_else(|| {
            app.project_icons
                .get(&detail.id)
                .map(image::Handle::from_path)
        });

    let icon_el: Element<'_, Message> = if let Some(handle) = icon_handle {
        container(
            image(handle)
                .width(42)
                .height(42)
                .content_fit(iced::ContentFit::Cover)
                .border_radius(9.0),
        )
        .width(42)
        .height(42)
        .style(move |_| container::Style {
            border: Border {
                radius: 9.0.into(),
                color: p.hairline,
                width: 1.0,
            },
            ..Default::default()
        })
        .into()
    } else {
        let initial = detail
            .title
            .chars()
            .find(|c| c.is_alphanumeric())
            .unwrap_or('M')
            .to_uppercase()
            .to_string();
        let seed = detail.title.clone();
        container(text(initial).size(15).mono_medium().color(p.text_primary))
            .width(42)
            .height(42)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .style(move |_| p.avatar_style_for_name(&seed))
            .into()
    };

    let primary_action: Element<'_, Message> = if already_installed {
        container(
            row![
                icon_svg(SVG_CHECK, 13.0, p.accent_green),
                text(app.tr("browse.installed"))
                    .size(12)
                    .medium()
                    .nowrap()
                    .color(p.accent_green),
            ]
            .spacing(5)
            .align_y(Alignment::Center),
        )
        .height(Length::Fixed(32.0))
        .padding([0, 12])
        .align_y(Vertical::Center)
        .style(p.inset_panel_style())
        .into()
    } else {
        Button::new(vcenter(
            row![
                icon_svg(SVG_DOWNLOAD, 13.0, p.cta_text),
                text(if installing {
                    app.tr("browse.installing")
                } else {
                    app.tr("browse.install")
                })
                .size(12)
                .medium()
                .nowrap()
                .color(p.cta_text),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        ))
        .on_press_maybe(if installing {
            None
        } else {
            Some(Message::BrowseInstallProject(
                detail.id.clone(),
                app.current_content_kind(),
            ))
        })
        .style(p.primary_button())
        .height(Length::Fixed(32.0))
        .padding([0, 14])
        .into()
    };

    let top_row = row![
        back_btn,
        icon_el,
        container(
            column![
                text(truncate_ellipsis(&detail.title, 44))
                    .size(15.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                text(truncate_ellipsis(&detail.description, 88))
                    .size(12)
                    .nowrap()
                    .color(p.text_muted),
            ]
            .spacing(2)
            .width(Length::Fill),
        )
        .width(Length::Fill)
        .clip(true),
        primary_action,
    ]
    .spacing(12)
    .align_y(Alignment::Center)
    .width(Length::Fill);

    let mut meta_row = row![
        row![
            icon_svg(SVG_DOWNLOAD, 12.0, p.text_dim),
            text(I18nCatalog::format_downloads(detail.downloads))
                .size(11.5)
                .mono()
                .nowrap()
                .color(p.text_muted),
        ]
        .spacing(4)
        .align_y(Alignment::Center),
    ]
    .spacing(12)
    .align_y(Alignment::Center);

    if detail.followers > 0 {
        meta_row = meta_row.push(
            row![
                icon_svg(SVG_HEART, 11.5, p.text_dim),
                text(I18nCatalog::format_downloads(detail.followers))
                    .size(11.5)
                    .mono()
                    .nowrap()
                    .color(p.text_muted),
            ]
            .spacing(4)
            .align_y(Alignment::Center),
        );
    }

    if let Some(ref lic) = detail.license
        && !lic.id.trim().is_empty()
    {
        meta_row = meta_row.push(
            row![
                icon_svg(SVG_TAG, 11.5, p.text_dim),
                text(&lic.id).size(11).mono().nowrap().color(p.text_dim),
            ]
            .spacing(4)
            .align_y(Alignment::Center),
        );
    }

    let side_badge = match (detail.client_side.as_str(), detail.server_side.as_str()) {
        ("required" | "optional", "required" | "optional") => Some(format!(
            "{} / {}",
            app.tr("browse.detail.client"),
            app.tr("browse.detail.server")
        )),
        ("required" | "optional", _) => Some(app.tr("browse.detail.client").to_string()),
        (_, "required" | "optional") => Some(app.tr("browse.detail.server").to_string()),
        _ => None,
    };
    if let Some(sb) = side_badge {
        meta_row = meta_row.push(
            container(text(sb).size(10.5).nowrap().color(p.text_muted))
                .padding(Padding::new(2.0).left(6.0).right(6.0))
                .style(p.monogram_tile_style(false, 5.0)),
        );
    }

    meta_row = meta_row.push(Space::new().width(Length::Fill));

    let ext_link = |ic: &'static str, label_key: &'static str, url: String| {
        Button::new(vcenter(
            row![
                icon_svg(ic, 11.5, p.text_muted),
                text(app.tr(label_key))
                    .size(11)
                    .medium()
                    .nowrap()
                    .color(p.text_secondary),
            ]
            .spacing(4)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::OpenUrl(url))
        .style(p.ghost_button(5.0))
        .height(Length::Fixed(24.0))
        .padding([0, 7])
    };

    let mr_type = if detail.project_type.is_empty() {
        "project"
    } else {
        detail.project_type.as_str()
    };
    meta_row = meta_row.push(ext_link(
        SVG_MODRINTH,
        "browse.detail.open_modrinth",
        format!("https://modrinth.com/{mr_type}/{}", detail.slug),
    ));
    if let Some(ref src) = detail.source_url
        && !src.trim().is_empty()
    {
        meta_row = meta_row.push(ext_link(SVG_EXTERNAL, "browse.detail.source", src.clone()));
    }
    if let Some(ref wiki) = detail.wiki_url
        && !wiki.trim().is_empty()
    {
        meta_row = meta_row.push(ext_link(SVG_BOOK, "browse.detail.wiki", wiki.clone()));
    }
    if let Some(ref discord) = detail.discord_url
        && !discord.trim().is_empty()
    {
        meta_row = meta_row.push(ext_link(
            SVG_MESSAGE,
            "browse.detail.discord",
            discord.clone(),
        ));
    }

    let sub_tab_btn = |tab: ModDetailsTab, ic: &'static str, label: String| {
        let active = app.browse_detail_tab == tab;
        Button::new(vcenter(
            row![
                icon_svg(ic, 12.5, if active { p.text_primary } else { p.text_muted }),
                text(label).size(12).medium().nowrap(),
            ]
            .spacing(5)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::BrowseSelectDetailTab(tab))
        .style(p.segmented_button(active))
        .height(Length::Fixed(26.0))
        .padding([0, 10])
    };

    let mut tab_row = row![sub_tab_btn(
        ModDetailsTab::Description,
        SVG_FILE_TEXT,
        app.tr("browse.detail.description").to_string(),
    )]
    .spacing(2);

    if !detail.gallery.is_empty() {
        tab_row = tab_row.push(sub_tab_btn(
            ModDetailsTab::Gallery,
            SVG_IMAGE,
            format!("{} ({})", app.tr("browse.detail.gallery"), detail.gallery.len()),
        ));
    }

    let ver_label = if app.browse_detail_versions.is_empty() {
        app.tr("browse.detail.versions").to_string()
    } else {
        format!(
            "{} ({})",
            app.tr("browse.detail.versions"),
            app.browse_detail_versions.len()
        )
    };
    tab_row = tab_row.push(sub_tab_btn(ModDetailsTab::Versions, SVG_TAG, ver_label));

    let sub_tabs_bar = row![
        container(tab_row).padding(3).style(p.segmented_track_style()),
        Space::new().width(Length::Fill),
    ]
    .align_y(Alignment::Center);

    let header_block = column![
        container(column![top_row, meta_row, sub_tabs_bar].spacing(10))
            .width(Length::Fill)
            .padding(Padding::new(12.0).left(14.0).right(14.0)),
        subtle_row_divider(p),
    ]
    .width(Length::Fill);

    let tab_body: Element<'_, Message> = match app.browse_detail_tab {
        ModDetailsTab::Description => {
            let content_w = (app.window_size.width - 310.0).clamp(360.0, 740.0);
            let md_el = markdown_view::render_markdown(
                &app.browse_detail_blocks,
                &app.browse_detail_expanded,
                &app.remote_images,
                content_w,
                p,
                Message::MarkdownLinkClicked,
                Message::BrowseToggleMarkdownDetails,
            );
            scrollable(container(md_el).padding(16).width(Length::Fill))
                .height(Length::Fill)
                .width(Length::Fill)
                .style(p.custom_scrollbar_style())
                .into()
        }
        ModDetailsTab::Gallery => {
            if detail.gallery.is_empty() {
                container(
                    text(app.tr("browse.detail.no_gallery"))
                        .size(12.5)
                        .color(p.text_muted),
                )
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center)
                .into()
            } else {
                let total = detail.gallery.len();
                let active_idx = app.browse_detail_gallery_idx.min(total - 1);
                let current = &detail.gallery[active_idx];
                let stage_max_w = (app.window_size.width - 340.0).clamp(320.0, 740.0);
                let stage_max_h = 340.0;

                let featured: Element<'_, Message> =
                    if let Some(img) = app.remote_images.get(&current.url) {
                        let (dw, dh) = markdown_view::fit_image_size(
                            img.width,
                            img.height,
                            stage_max_w,
                            stage_max_h,
                        );
                        container(
                            image(img.handle.clone())
                                .width(Length::Fixed(dw))
                                .height(Length::Fixed(dh))
                                .content_fit(iced::ContentFit::Contain)
                                .border_radius(8.0),
                        )
                        .width(Length::Fill)
                        .height(Length::Fixed((dh + 16.0).max(200.0)))
                        .align_x(Horizontal::Center)
                        .align_y(Vertical::Center)
                        .style(p.inset_panel_style())
                        .padding(8)
                        .clip(true)
                        .into()
                    } else {
                        container(
                            text(app.tr("browse.detail.loading"))
                                .size(12)
                                .color(p.text_muted),
                        )
                        .width(Length::Fill)
                        .height(Length::Fixed(220.0))
                        .align_x(Horizontal::Center)
                        .align_y(Vertical::Center)
                        .style(p.inset_panel_style())
                        .into()
                    };

                let mut gal_col = column![featured].spacing(10).width(Length::Fill);

                let has_title = current.title.as_deref().is_some_and(|t| !t.trim().is_empty());
                if has_title || total > 1 {
                    let mut caption_bar = Row::new()
                        .spacing(8)
                        .align_y(Alignment::Center)
                        .width(Length::Fill);
                    if let Some(ref title) = current.title
                        && !title.trim().is_empty()
                    {
                        caption_bar = caption_bar.push(
                            text(title)
                                .size(13)
                                .medium()
                                .shaped()
                                .color(p.text_primary),
                        );
                    }
                    caption_bar = caption_bar.push(Space::new().width(Length::Fill));
                    if total > 1 {
                        let prev_idx = (active_idx + total - 1) % total;
                        let next_idx = (active_idx + 1) % total;
                        caption_bar = caption_bar
                            .push(
                                text(format!("{} / {}", active_idx + 1, total))
                                    .size(11.5)
                                    .mono()
                                    .nowrap()
                                    .color(p.text_muted),
                            )
                            .push(
                                Button::new(center_box(icon_svg(
                                    SVG_CHEVRON_LEFT,
                                    13.0,
                                    p.text_secondary,
                                )))
                                .on_press(Message::BrowseSelectGalleryIndex(prev_idx))
                                .style(p.secondary_button())
                                .width(Length::Fixed(26.0))
                                .height(Length::Fixed(26.0))
                                .padding(0),
                            )
                            .push(
                                Button::new(center_box(icon_svg(
                                    SVG_CHEVRON_RIGHT,
                                    13.0,
                                    p.text_secondary,
                                )))
                                .on_press(Message::BrowseSelectGalleryIndex(next_idx))
                                .style(p.secondary_button())
                                .width(Length::Fixed(26.0))
                                .height(Length::Fixed(26.0))
                                .padding(0),
                            );
                    }
                    gal_col = gal_col.push(caption_bar);
                }

                if let Some(ref desc) = current.description
                    && !desc.trim().is_empty()
                {
                    gal_col = gal_col.push(
                        text(desc)
                            .size(12)
                            .shaped()
                            .color(p.text_muted),
                    );
                }

                if total > 1 {
                    let mut thumbs = Row::new()
                        .spacing(8)
                        .align_y(Alignment::Center)
                        .padding(Padding::new(0.0).bottom(10.0));
                    for (idx, item) in detail.gallery.iter().enumerate() {
                        let is_sel = idx == active_idx;
                        let thumb_inner: Element<'_, Message> =
                            if let Some(img) = app.remote_images.get(&item.url) {
                                container(
                                    image(img.handle.clone())
                                        .width(Length::Fill)
                                        .height(Length::Fill)
                                        .content_fit(iced::ContentFit::Cover)
                                        .border_radius(5.0),
                                )
                                .width(84)
                                .height(52)
                                .clip(true)
                                .into()
                            } else {
                                container(text(format!("#{}", idx + 1)).size(11).mono().color(p.text_dim))
                                    .width(84)
                                    .height(52)
                                    .align_x(Horizontal::Center)
                                    .align_y(Vertical::Center)
                                    .into()
                            };
                        thumbs = thumbs.push(
                            Button::new(thumb_inner)
                                .on_press(Message::BrowseSelectGalleryIndex(idx))
                                .style(p.segmented_button(is_sel))
                                .padding(3),
                        );
                    }
                    gal_col = gal_col.push(
                        scrollable(thumbs)
                            .direction(iced::widget::scrollable::Direction::Horizontal(
                                iced::widget::scrollable::Scrollbar::new(),
                            ))
                            .width(Length::Fill)
                            .style(p.custom_scrollbar_style()),
                    );
                }

                scrollable(container(gal_col).padding(16).width(Length::Fill))
                    .height(Length::Fill)
                    .width(Length::Fill)
                    .style(p.custom_scrollbar_style())
                    .into()
            }
        }
        ModDetailsTab::Versions => {
            if app.browse_detail_versions_loading {
                container(
                    text(app.tr("browse.detail.loading"))
                        .size(12.5)
                        .color(p.text_muted),
                )
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center)
                .into()
            } else if app.browse_detail_versions.is_empty() {
                container(
                    text(app.tr("browse.detail.no_versions"))
                        .size(12.5)
                        .color(p.text_muted),
                )
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center)
                .into()
            } else {
                let kind = app.current_content_kind();
                let mut vlist = Column::new()
                    .spacing(0)
                    .width(Length::Fill)
                    .padding(Padding::new(0.0).right(10.0));

                for (idx, ver) in app.browse_detail_versions.iter().take(40).enumerate() {
                    if idx > 0 {
                        vlist = vlist.push(subtle_row_divider(p));
                    }
                    let (type_key, type_col) = match ver.version_type.as_str() {
                        "beta" => ("browse.detail.beta", p.accent_orange),
                        "alpha" => ("browse.detail.alpha", p.accent_red),
                        _ => ("browse.detail.release", p.accent_green),
                    };
                    let ver_installed = installed_ids.contains(&ver.id);
                    let ver_installing = app
                        .installing_projects
                        .get(&ver.id)
                        .copied()
                        .unwrap_or(false);

                    let mc_summary = if ver.game_versions.len() > 3 {
                        format!(
                            "{} .. {}",
                            ver.game_versions.first().map(String::as_str).unwrap_or(""),
                            ver.game_versions.last().map(String::as_str).unwrap_or("")
                        )
                    } else {
                        ver.game_versions.join(", ")
                    };
                    let meta = if ver.loaders.is_empty() {
                        mc_summary
                    } else {
                        format!("{} · {}", ver.loaders.join(", "), mc_summary)
                    };

                    let action_el: Element<'_, Message> = if ver_installed {
                        container(
                            row![
                                icon_svg(SVG_CHECK, 12.0, p.accent_green),
                                text(app.tr("browse.installed"))
                                    .size(11.5)
                                    .medium()
                                    .nowrap()
                                    .color(p.accent_green),
                            ]
                            .spacing(5)
                            .align_y(Alignment::Center),
                        )
                        .padding([0, 10])
                        .into()
                    } else {
                        Button::new(vcenter(
                            text(if ver_installing {
                                app.tr("browse.installing")
                            } else {
                                app.tr("browse.install")
                            })
                            .size(11.5)
                            .medium()
                            .nowrap(),
                        ))
                        .on_press_maybe(if ver_installing {
                            None
                        } else {
                            Some(Message::BrowseInstallVersion {
                                project_id: detail.id.clone(),
                                version: ver.clone(),
                                kind,
                            })
                        })
                        .style(p.secondary_button())
                        .height(Length::Fixed(28.0))
                        .padding([0, 11])
                        .into()
                    };

                    let v_row = container(
                        row![
                            container(
                                text(app.tr(type_key))
                                    .size(10.5)
                                    .medium()
                                    .nowrap()
                                    .color(type_col),
                            )
                            .padding(Padding::new(2.5).left(7.0).right(7.0))
                            .style(move |_| container::Style {
                                background: Some(Background::Color(Color { a: 0.12, ..type_col })),
                                border: Border {
                                    color: Color { a: 0.28, ..type_col },
                                    width: 1.0,
                                    radius: 5.0.into(),
                                },
                                ..Default::default()
                            }),
                            container(
                                column![
                                    row![
                                        text(truncate_ellipsis(&ver.name, 36))
                                            .size(12.5)
                                            .medium()
                                            .nowrap()
                                            .color(p.text_primary),
                                        text(&ver.version_number)
                                            .size(11)
                                            .mono()
                                            .nowrap()
                                            .color(p.text_dim),
                                    ]
                                    .spacing(8)
                                    .align_y(Alignment::Center),
                                    text(truncate_ellipsis(&meta, 56))
                                        .size(11)
                                        .mono()
                                        .nowrap()
                                        .color(p.text_muted),
                                ]
                                .spacing(2)
                                .width(Length::Fill),
                            )
                            .width(Length::Fill)
                            .clip(true),
                            text(I18nCatalog::format_downloads(ver.downloads))
                                .size(11)
                                .mono()
                                .nowrap()
                                .color(p.text_muted),
                            action_el,
                        ]
                        .spacing(10)
                        .align_y(Alignment::Center)
                        .width(Length::Fill),
                    )
                    .height(Length::Fixed(52.0))
                    .padding(Padding::new(0.0).left(14.0).right(12.0))
                    .align_y(Vertical::Center);

                    vlist = vlist.push(v_row);
                }

                scrollable(vlist)
                    .height(Length::Fill)
                    .width(Length::Fill)
                    .style(p.custom_scrollbar_style())
                    .into()
            }
        }
    };

    container(
        column![header_block, tab_body]
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

fn view_themes_card<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;

    let toolbar = card_toolbar(
        row![
            Button::new(vcenter(
                row![
                    icon_svg(SVG_PLUS, 13.0, p.cta_text),
                    text(app.tr("themes.import"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::ImportNcThemePick)
            .style(p.primary_button())
            .height(Length::Fixed(32.0))
            .padding([0, 12]),
            Space::new().width(Length::Fill),
            row![
                text(app.tr("themes.shared_configs"))
                    .size(12)
                    .nowrap()
                    .color(p.text_secondary),
                toggle_switch(
                    inst.config.nightclient_shared_profile,
                    false,
                    Message::ToggleInstanceSharedProfile(
                        inst.config.id.clone(),
                        !inst.config.nightclient_shared_profile,
                    ),
                    p,
                ),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
            row![
                text(app.tr("themes.sync_palette"))
                    .size(12)
                    .nowrap()
                    .color(p.text_secondary),
                toggle_switch(
                    app.config.sync_ui_with_nc_theme,
                    false,
                    Message::ToggleSyncUiWithTheme(!app.config.sync_ui_with_nc_theme),
                    p,
                ),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
        ]
        .spacing(16)
        .align_y(Alignment::Center)
        .into(),
        p,
    );

    let mut list = Column::new()
        .spacing(0)
        .width(Length::Fill)
        .padding(Padding::new(0.0).right(10.0));

    for (idx, th) in app.installed_themes.iter().enumerate() {
        if idx > 0 {
            list = list.push(subtle_row_divider(p));
        }
        let active = app.config.active_theme_id == th.id;
        let swatches = row![
            swatch_tile(rgba(th.tokens.background), p.hairline_strong),
            swatch_tile(rgba(th.tokens.surface), p.hairline_strong),
            swatch_tile(rgba(th.tokens.raised), p.hairline_strong),
            swatch_tile(rgba(th.tokens.accent), p.hairline_strong),
        ]
        .spacing(4);

        let right_el: Element<'a, Message> = if active {
            row![
                icon_svg(SVG_CHECK, 13.0, p.accent_green),
                text(app.tr("themes.active"))
                    .size(12)
                    .medium()
                    .nowrap()
                    .color(p.accent_green),
            ]
            .spacing(5)
            .align_y(Alignment::Center)
            .into()
        } else {
            Button::new(vcenter(
                text(app.tr("themes.apply")).size(12).medium().nowrap(),
            ))
            .on_press(Message::SelectNcTheme(th.id.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(30.0))
            .padding([0, 12])
            .into()
        };

        list = list.push(
            container(
                row![
                    swatches,
                    column![
                        text(&th.name)
                            .size(13)
                            .medium()
                            .nowrap()
                            .color(p.text_primary),
                        text(format!(
                            "#{:02X}{:02X}{:02X} · #{:02X}{:02X}{:02X} · {}px",
                            th.tokens.background[0],
                            th.tokens.background[1],
                            th.tokens.background[2],
                            th.tokens.accent[0],
                            th.tokens.accent[1],
                            th.tokens.accent[2],
                            th.tokens.border_radius as u32
                        ))
                        .size(11)
                        .mono()
                        .color(p.text_muted),
                    ]
                    .spacing(2)
                    .width(Length::Fill),
                    right_el,
                ]
                .spacing(14)
                .align_y(Alignment::Center),
            )
            .height(Length::Fixed(58.0))
            .padding(Padding::new(0.0).left(14.0).right(14.0))
            .align_y(Vertical::Center),
        );
    }

    container(
        column![
            toolbar,
            scrollable(list)
                .height(Length::Fill)
                .width(Length::Fill)
                .style(p.custom_scrollbar_style()),
        ]
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

fn swatch_tile<'a>(fill: Color, border_color: Color) -> Element<'a, Message> {
    container(Space::new().width(20).height(20))
        .style(move |_| container::Style {
            background: Some(Background::Color(fill)),
            border: Border {
                color: border_color,
                width: 1.0,
                radius: 5.0.into(),
            },
            ..Default::default()
        })
        .into()
}

fn view_screenshots_card<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;

    let toolbar = card_toolbar(
        row![
            Space::new().width(Length::Fill),
            Button::new(vcenter(
                row![
                    icon_svg(SVG_REFRESH, 13.0, p.text_secondary),
                    text(app.tr("screenshots.refresh")).size(12).medium().nowrap(),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::RefreshScreenshots)
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding([0, 11]),
            Button::new(vcenter(
                row![
                    icon_svg(SVG_FOLDER, 13.0, p.text_secondary),
                    text(app.tr("screenshots.open_folder")).size(12).medium().nowrap(),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::OpenScreenshotsFolder(inst.config.id.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding([0, 11]),
        ]
        .spacing(8)
        .align_y(Alignment::Center)
        .into(),
        p,
    );

    let body: Element<'a, Message> = if app.screenshots.is_empty() {
        container(
            column![
                container(icon_svg(SVG_IMAGE, 20.0, p.text_secondary))
                    .width(42)
                    .height(42)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center)
                    .style(p.monogram_tile_style(true, 10.0)),
                Space::new().height(4),
                text(app.tr("screenshots.empty"))
                    .size(15)
                    .medium()
                    .color(p.text_primary),
                text(app.tr("screenshots.empty_hint"))
                    .size(12.5)
                    .shaped()
                    .color(p.text_muted),
            ]
            .spacing(6)
            .align_x(Alignment::Center),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center)
        .into()
    } else {
        let cols = if app.is_compact() { 2 } else { 3 };
        let mut grid = Column::new().spacing(12).padding(14).width(Length::Fill);
        for chunk in app.screenshots.chunks(cols) {
            let mut r = Row::new().spacing(12).width(Length::Fill);
            for shot in chunk {
                let subline = if shot.width > 0 && shot.height > 0 && !shot.modified_label.is_empty() {
                    format!("{}×{} · {}", shot.width, shot.height, shot.modified_label)
                } else if shot.width > 0 && shot.height > 0 {
                    format!("{}×{}", shot.width, shot.height)
                } else {
                    shot.modified_label.clone()
                };
                let thumb_frame = container(
                    image(image::Handle::from_path(&shot.thumbnail_path))
                        .width(Length::Fill)
                        .height(Length::Fill)
                        .content_fit(iced::ContentFit::Cover)
                        .border_radius(6.0),
                )
                .width(Length::Fill)
                .height(Length::Fixed(148.0))
                .clip(true)
                .style(move |_| container::Style {
                    border: Border {
                        radius: 6.0.into(),
                        color: p.hairline_subtle,
                        width: 1.0,
                    },
                    ..Default::default()
                });

                let tile = container(
                    column![
                        Button::new(thumb_frame)
                            .on_press(Message::OpenModal(ActiveModal::ScreenshotLightbox(
                                shot.path.clone(),
                            )))
                            .style(p.ghost_button(6.0))
                            .padding(0)
                            .width(Length::Fill),
                        row![
                            container(
                                column![
                                    text(truncate_ellipsis(&shot.filename, 22))
                                        .size(11.5)
                                        .mono()
                                        .nowrap()
                                        .color(p.text_primary),
                                    text(subline)
                                        .size(10.5)
                                        .mono()
                                        .nowrap()
                                        .color(p.text_dim),
                                ]
                                .spacing(2),
                            )
                            .width(Length::Fill)
                            .clip(true),
                            Button::new(center_box(icon_svg(SVG_COPY, 12.5, p.text_muted)))
                                .on_press(Message::CopyScreenshot(shot.path.clone()))
                                .style(p.ghost_button(5.0))
                                .width(Length::Fixed(24.0))
                                .height(Length::Fixed(24.0))
                                .padding(0),
                            Button::new(center_box(icon_svg(SVG_TRASH, 12.5, p.text_dim)))
                                .on_press(Message::RequestDelete(PendingDelete::Screenshot {
                                    path: shot.path.clone(),
                                    name: shot.filename.clone(),
                                }))
                                .style(p.ghost_button(5.0))
                                .width(Length::Fixed(24.0))
                                .height(Length::Fixed(24.0))
                                .padding(0),
                        ]
                        .spacing(4)
                        .align_y(Alignment::Center),
                    ]
                    .spacing(8),
                )
                .width(Length::FillPortion(1))
                .padding(8)
                .style(p.inset_panel_style());
                r = r.push(tile);
            }
            for _ in chunk.len()..cols {
                r = r.push(Space::new().width(Length::FillPortion(1)));
            }
            grid = grid.push(r);
        }
        scrollable(grid)
            .height(Length::Fill)
            .width(Length::Fill)
            .style(p.custom_scrollbar_style())
            .into()
    };

    container(
        column![toolbar, body]
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

fn view_console_card<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;

    let filter_box = container(
        row![
            icon_svg(SVG_SEARCH, 13.0, p.text_dim),
            text_input(app.tr("console.filter_placeholder"), &app.console_filter)
                .on_input(Message::ConsoleFilterChanged)
                .style(p.bare_text_input_style())
                .padding(Padding::new(0.0).left(7.0).right(8.0))
                .size(12)
                .width(Length::Fill),
        ]
        .align_y(Alignment::Center)
        .height(Length::Fill)
        .padding(Padding::new(0.0).left(10.0)),
    )
    .width(Length::Fill)
    .height(Length::Fixed(32.0))
    .align_y(Vertical::Center)
    .style(p.input_shell_style());

    let toolbar = card_toolbar(
        row![
            filter_box,
            Button::new(vcenter(
                row![
                    icon_svg(SVG_COPY, 13.0, p.text_secondary),
                    text(app.tr("console.copy")).size(12).medium().nowrap(),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::CopyConsole(inst.config.id.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding([0, 11]),
            Button::new(vcenter(
                text(app.tr("console.clear")).size(12).medium().nowrap(),
            ))
            .on_press(Message::ClearConsole(inst.config.id.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(32.0))
            .padding([0, 11]),
        ]
        .spacing(8)
        .align_y(Alignment::Center)
        .into(),
        p,
    );

    let q = app.console_filter.trim().to_lowercase();
    let lines = app.logs.get(&inst.config.id);
    let body: Element<'a, Message> = match lines {
        Some(buf) if !buf.is_empty() => {
            let mut col = Column::new().spacing(2).padding(12);
            for line in buf.iter().rev().take(400).rev() {
                if !q.is_empty() && !line.to_lowercase().contains(&q) {
                    continue;
                }
                let color = if line.contains("ERROR") || line.contains("Exception") {
                    p.accent_red
                } else if line.contains("WARN") {
                    p.accent_orange
                } else {
                    p.text_secondary
                };
                col = col.push(text(line).size(11.5).mono().shaped().color(color));
            }
            container(
                scrollable(col)
                    .height(Length::Fill)
                    .width(Length::Fill)
                    .style(p.custom_scrollbar_style()),
            )
            .width(Length::Fill)
            .height(Length::Fill)
            .style(p.inset_panel_style())
            .into()
        }
        _ => container(text(app.tr("console.empty")).size(12.5).color(p.text_dim))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .into(),
    };

    container(
        column![
            toolbar,
            container(body)
                .width(Length::Fill)
                .height(Length::Fill)
                .padding(10),
        ]
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

fn view_options_card<'a>(app: &'a App, inst: &'a InstanceRecord) -> Element<'a, Message> {
    let p = app.palette;
    let id = inst.config.id.clone();

    let (draft_loader, draft_lv) = app
        .options_loader_draft
        .as_ref()
        .filter(|(did, _, _)| did == &inst.config.id)
        .map(|(_, l, v)| (*l, v.clone()))
        .unwrap_or((inst.config.loader, inst.config.loader_version.clone()));

    let loader_changed =
        draft_loader != inst.config.loader || draft_lv != inst.config.loader_version;

    let mut loader_section = column![
        text(app.tr("options.loader_section"))
            .size(12)
            .medium()
            .color(p.text_muted),
        loader_segmented_track(draft_loader, Message::OptionsDraftLoader, p),
    ]
    .spacing(10);

    if draft_loader != LoaderKind::Vanilla {
        let key = (draft_loader, inst.config.mc_version.clone());
        let loading = app.loader_versions_loading.contains(&key);
        let label = app.tr_fmt(
            if loading {
                "options.loader_loading"
            } else {
                "options.loader_version"
            },
            &[("loader", draft_loader.display_name())],
        );
        let (opts, sel) = app.loader_version_dropdown_options(
            draft_loader,
            &inst.config.mc_version,
            draft_lv.as_deref(),
        );
        loader_section = loader_section.push(loader_version_picker_row(
            draft_loader,
            label,
            opts,
            sel,
            230.0,
            Message::OptionsDraftLoaderVersionLabel,
            p,
        ));
    }

    if loader_changed {
        loader_section = loader_section.push(
            row![
                Space::new().width(Length::Fill),
                Button::new(vcenter(
                    row![
                        icon_svg(SVG_CHECK, 13.0, p.cta_text),
                        text(app.tr("options.loader_apply"))
                            .size(12)
                            .medium()
                            .nowrap()
                            .color(p.cta_text),
                    ]
                    .spacing(6)
                    .align_y(Alignment::Center),
                ))
                .on_press(Message::ApplyOptionsLoader(id.clone()))
                .style(p.primary_button())
                .height(Length::Fixed(32.0))
                .padding([0, 14]),
            ],
        );
    }

    let max_ram = (app.system_ram_mb as u32).clamp(4096, 32768);
    let active_mem = inst
        .config
        .memory_mb_override
        .unwrap_or(app.config.default_memory_mb);
    let mem_gb = format!("{:.1}", active_mem as f32 / 1024.0);
    let id_mem_slider = id.clone();
    let id_mem_reset = id.clone();

    let mem_row = row![
        icon_svg(SVG_CPU, 15.0, p.text_muted),
        text(app.tr("options.memory"))
            .size(13)
            .medium()
            .color(p.text_primary)
            .width(Length::Fill),
        if inst.config.memory_mb_override.is_some() {
            Button::new(vcenter(
                text(app.tr("options.gc_global"))
                    .size(11.5)
                    .nowrap()
                    .color(p.text_muted),
            ))
            .on_press(Message::SetInstanceMemory(id_mem_reset, None))
            .style(p.ghost_button(6.0))
            .height(Length::Fixed(28.0))
            .padding([0, 8])
        } else {
            Button::new(Space::new().width(0)).padding(0).style(p.ghost_button(0.0))
        },
        container(
            slider(1024..=max_ram, active_mem.clamp(1024, max_ram), move |v| {
                Message::SetInstanceMemory(id_mem_slider.clone(), Some(v))
            })
            .step(512u32)
            .style(p.clean_slider_style()),
        )
        .width(Length::Fixed(164.0)),
        container(
            text(if inst.config.memory_mb_override.is_some() {
                app.tr_fmt("header.memory_gb", &[("gb", &mem_gb)])
            } else {
                app.tr_fmt("options.memory_global", &[("gb", &mem_gb)])
            })
            .size(12)
            .mono_medium()
            .nowrap()
            .color(p.text_primary),
        )
        .width(Length::Fixed(116.0))
        .align_x(Horizontal::Right),
    ]
    .spacing(10)
    .align_y(Alignment::Center);

    let (java_opts, java_sel) =
        app.java_dropdown_options(inst.config.java_path_override.as_deref(), false);
    let id_java = id.clone();
    let java_picker = container(stack![
        pick_list(java_opts, Some(java_sel), move |label| {
            Message::SetInstanceJavaLabel(id_java.clone(), label)
        })
        .handle(pick_list::Handle::None)
        .text_shaping(iced::widget::text::Shaping::Advanced)
        .style(p.pick_list_style())
        .menu_style(p.custom_menu_style())
        .text_size(12)
        .padding(Padding::new(7.0).left(10.0).right(28.0))
        .width(Length::Fixed(240.0)),
        container(icon_svg(SVG_CHEVRON_DOWN, 14.0, p.text_muted))
            .width(Length::Fixed(240.0))
            .height(Length::Fill)
            .padding(Padding::new(0.0).right(10.0))
            .align_x(Horizontal::Right)
            .align_y(Vertical::Center),
    ])
    .height(Length::Fixed(32.0));

    let java_row = row![
        icon_svg(SVG_COFFEE, 15.0, p.text_muted),
        text(app.tr("options.java"))
            .size(13)
            .medium()
            .color(p.text_primary)
            .width(Length::Fill),
        java_picker,
    ]
    .spacing(10)
    .align_y(Alignment::Center);

    let gc_btn = |prof: Option<PerformanceProfile>, key: &'a str| {
        let active = inst.config.performance_profile_override == prof;
        Button::new(vcenter(text(app.tr(key)).size(11.5).medium().nowrap()))
            .on_press(Message::SetInstanceProfile(id.clone(), prof))
            .style(p.segmented_button(active))
            .height(Length::Fixed(26.0))
            .padding([0, 9])
    };
    let gc_row = row![
        icon_svg(SVG_ZAP, 15.0, p.text_muted),
        text(app.tr("options.gc_profile"))
            .size(13)
            .medium()
            .color(p.text_primary)
            .width(Length::Fill),
        container(
            row![
                gc_btn(None, "options.gc_global"),
                gc_btn(Some(PerformanceProfile::Smooth), "settings.perf.smooth"),
                gc_btn(Some(PerformanceProfile::ZeroPause), "settings.perf.zero_pause"),
                gc_btn(Some(PerformanceProfile::PeakFps), "settings.perf.peak_fps"),
            ]
            .spacing(2),
        )
        .padding(3)
        .style(p.segmented_track_style()),
    ]
    .spacing(10)
    .align_y(Alignment::Center);

    let turbo_on = inst.config.turbo_mode || app.config.default_turbo_mode;
    let mut turbo_controls = row![
        toggle_switch(
            turbo_on,
            true,
            Message::ToggleInstanceTurbo(id.clone(), !inst.config.turbo_mode),
            p,
        ),
    ]
    .spacing(8)
    .align_y(Alignment::Center);
    if inst.config.loader == LoaderKind::Fabric {
        turbo_controls = row![
            Button::new(vcenter(
                row![
                    icon_svg(SVG_ZAP, 12.0, p.text_secondary),
                    text(app.tr("options.turbo_install_fps"))
                        .size(11.5)
                        .medium()
                        .nowrap(),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::InstallFpsPack(id.clone()))
            .style(p.secondary_button())
            .height(Length::Fixed(28.0))
            .padding([0, 9]),
            toggle_switch(
                turbo_on,
                true,
                Message::ToggleInstanceTurbo(id.clone(), !inst.config.turbo_mode),
                p,
            ),
        ]
        .spacing(8)
        .align_y(Alignment::Center);
    }
    let turbo_row = row![
        icon_svg(
            SVG_ZAP,
            15.0,
            if turbo_on { p.accent_green } else { p.text_muted },
        ),
        text(app.tr("options.turbo_mode"))
            .size(13)
            .medium()
            .color(p.text_primary)
            .width(Length::Fill),
        turbo_controls,
    ]
    .spacing(10)
    .align_y(Alignment::Center);

    let clone_form = CloneInstanceForm {
        source_id: id.clone(),
        new_name: format!("{} Copy", inst.config.name),
        target_mc: inst.config.mc_version.clone(),
        target_loader: inst.config.loader,
        target_loader_version: inst.config.loader_version.clone(),
        version_filter: String::new(),
        version_expanded: false,
        show_snapshots: false,
        migrate_mods: true,
    };

    let act_btn = |ic: &'static str, key: &'a str, msg: Message| {
        Button::new(vcenter(
            row![
                icon_svg(ic, 13.0, p.text_secondary),
                text(app.tr(key)).size(12).medium().nowrap(),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        ))
        .on_press(msg)
        .style(p.secondary_button())
        .height(Length::Fixed(32.0))
        .padding([0, 11])
    };

    let actions_row = row![
        act_btn(
            SVG_EDIT,
            "options.rename",
            Message::OpenModal(ActiveModal::RenameInstance {
                id: id.clone(),
                draft: inst.config.name.clone(),
            }),
        ),
        act_btn(SVG_WRENCH, "options.repair", Message::RepairInstance(id.clone())),
        act_btn(
            SVG_CLONE,
            "options.clone",
            Message::OpenModal(ActiveModal::CloneInstance(clone_form)),
        ),
        act_btn(
            SVG_UPLOAD,
            "options.export",
            Message::ExportInstanceMrpack(id.clone()),
        ),
        Space::new().width(Length::Fill),
        Button::new(vcenter(
            row![
                icon_svg(SVG_TRASH, 13.0, p.accent_red),
                text(app.tr("options.delete"))
                    .size(12)
                    .medium()
                    .nowrap()
                    .color(p.accent_red),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::RequestDelete(PendingDelete::Instance {
            id,
            name: inst.config.name.clone(),
        }))
        .style(p.subtle_danger_button())
        .height(Length::Fixed(32.0))
        .padding([0, 12]),
    ]
    .spacing(8)
    .align_y(Alignment::Center);

    let content = column![
        loader_section,
        subtle_row_divider(p),
        column![
            text(app.tr("options.runtime_section"))
                .size(12)
                .medium()
                .color(p.text_muted),
            mem_row,
            java_row,
            gc_row,
            turbo_row,
        ]
        .spacing(12),
        subtle_row_divider(p),
        column![
            text(app.tr("options.actions_section"))
                .size(12)
                .medium()
                .color(p.text_muted),
            actions_row,
        ]
        .spacing(10),
    ]
    .spacing(16)
    .padding(16);

    container(
        scrollable(content)
            .height(Length::Fill)
            .width(Length::Fill)
            .style(p.custom_scrollbar_style()),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .clip(true)
    .style(p.card_style())
    .into()
}

pub fn view_servers_popover(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    let Some(inst) = app.selected_instance().or_else(|| app.instances.first()) else {
        return Space::new().into();
    };
    let inst_id = inst.config.id.clone();
    let servers = app
        .servers
        .get(&inst_id)
        .map(Vec::as_slice)
        .unwrap_or(&[]);

    let header = row![
        icon_svg(SVG_SERVER, 13.5, p.text_secondary),
        text(app.tr("quick_play.title"))
            .size(12.5)
            .medium()
            .nowrap()
            .color(p.text_primary)
            .width(Length::Fill),
        text(servers.len().to_string())
            .size(11)
            .mono_medium()
            .nowrap()
            .color(p.text_dim),
    ]
    .spacing(7)
    .align_y(Alignment::Center);

    let body: Element<'_, Message> = if servers.is_empty() {
        container(
            column![
                text(app.tr("quick_play.empty_title"))
                    .size(12)
                    .medium()
                    .color(p.text_secondary),
                text(app.tr("quick_play.empty_hint"))
                    .size(11)
                    .shaped()
                    .color(p.text_dim),
            ]
            .spacing(3),
        )
        .width(Length::Fill)
        .padding([10, 6])
        .into()
    } else {
        let mut list = Column::new()
            .spacing(3)
            .width(Length::Fill)
            .padding(Padding::new(0.0).right(4.0));
        for s in servers {
            let name_disp = truncate_ellipsis(&s.name, 26);
            let addr_disp = truncate_ellipsis(&s.address, 30);
            list = list.push(
                Button::new(
                    row![
                        column![
                            text(name_disp)
                                .size(12.5)
                                .medium()
                                .nowrap()
                                .color(p.text_primary),
                            text(addr_disp)
                                .size(10.5)
                                .mono()
                                .nowrap()
                                .color(p.text_muted),
                        ]
                        .spacing(1)
                        .width(Length::Fill),
                        icon_svg(SVG_PLAY, 10.5, p.accent_green),
                    ]
                    .spacing(8)
                    .align_y(Alignment::Center)
                    .width(Length::Fill),
                )
                .on_press(Message::LaunchInstance {
                    id: inst_id.clone(),
                    quick_play: Some(s.address.clone()),
                })
                .style(p.ghost_button(7.0))
                .width(Length::Fill)
                .padding([7, 9]),
            );
        }
        container(scrollable(list).style(p.custom_scrollbar_style()))
            .max_height(260.0)
            .width(Length::Fill)
            .into()
    };

    let card = mouse_area(
        container(
            column![header, subtle_row_divider(p), body]
                .spacing(8)
                .width(Length::Fill),
        )
        .width(Length::Fixed(316.0))
        .padding(10)
        .style(p.popover_panel_style()),
    )
    .on_press(Message::NoOp)
    .on_right_press(Message::NoOp);

    let backdrop = mouse_area(container(Space::new()).width(Length::Fill).height(Length::Fill))
        .on_press(Message::CloseServersPopover)
        .on_right_press(Message::CloseServersPopover);

    let positioned = container(
        column![
            Space::new().height(Length::Fixed(88.0)),
            row![Space::new().width(Length::Fill), card],
        ]
        .width(Length::Fill)
        .height(Length::Fill),
    )
    .width(Length::Fill)
    .height(Length::Fill)
    .padding(Padding::new(0.0).right(22.0));

    stack![backdrop, positioned].into()
}
