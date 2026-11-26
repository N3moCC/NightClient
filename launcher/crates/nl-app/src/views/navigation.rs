use crate::app::{App, Message, Page};
use crate::icons::{
    SVG_CHEVRONS_UP_DOWN, SVG_DOWNLOAD, SVG_NIGHTCLIENT, SVG_PLUS, SVG_REFRESH, SVG_SEARCH,
    SVG_SETTINGS, SVG_SHIELD_CHECK, SVG_SORT, SVG_USER, SVG_WIN_CLOSE, SVG_WIN_MAXIMIZE,
    SVG_WIN_MINIMIZE, SVG_WIN_RESTORE, icon_svg, loader_icon_svg,
};
use crate::theme::{TextExt, truncate_ellipsis};
use crate::widgets::{hairline_divider, player_skin_badge, vcenter};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{
    Button, Column, Space, column, container, mouse_area, row, scrollable, stack, text, text_input,
};
use iced::{Alignment, Background, Border, Element, Length, Padding};
use nl_auth::AccountKind;
use nl_core::{InstanceRecord, InstanceSort};
use nl_night::VisualsCompatibility;

pub const SIDEBAR_WIDTH: f32 = 240.0;
pub const SIDEBAR_COMPACT_WIDTH: f32 = 216.0;

pub fn effective_sidebar_width(app: &App) -> f32 {
    if app.window_size.width > 0.0 && app.window_size.width < 900.0 {
        SIDEBAR_COMPACT_WIDTH
    } else {
        SIDEBAR_WIDTH
    }
}

pub fn view_canvas_top_bar(app: &App) -> Element<'_, Message> {
    let p = app.palette;

    let drag_area = mouse_area(container(Space::new()).width(Length::Fill).height(Length::Fill))
        .on_press(Message::TitleBarPressed);

    let win_btn = |ic: &'static str, msg: Message, is_close: bool| {
        Button::new(
            container(icon_svg(ic, 12.0, p.text_muted))
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center),
        )
        .on_press(msg)
        .style(p.window_control(is_close))
        .width(Length::Fixed(30.0))
        .height(Length::Fixed(26.0))
        .padding(0)
    };

    let max_icon = if app.is_maximized {
        SVG_WIN_RESTORE
    } else {
        SVG_WIN_MAXIMIZE
    };

    let btn_group = if cfg!(target_os = "macos") {
        row![
            win_btn(SVG_WIN_CLOSE, Message::CloseWindow, true),
            win_btn(SVG_WIN_MINIMIZE, Message::MinimizeWindow, false),
            win_btn(max_icon, Message::MaximizeWindow, false),
            Space::new().width(Length::Fill),
        ]
        .spacing(4)
        .align_y(Alignment::Center)
    } else {
        row![
            Space::new().width(Length::Fill),
            win_btn(SVG_WIN_MINIMIZE, Message::MinimizeWindow, false),
            win_btn(max_icon, Message::MaximizeWindow, false),
            win_btn(SVG_WIN_CLOSE, Message::CloseWindow, true),
        ]
        .spacing(4)
        .align_y(Alignment::Center)
    };

    let controls = container(btn_group)
        .width(Length::Fill)
        .height(Length::Fill)
        .align_y(Vertical::Center)
        .padding(Padding::new(0.0).left(10.0).right(10.0));

    container(stack![drag_area, controls])
        .width(Length::Fill)
        .height(42)
        .into()
}

pub fn view_sidebar(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    let radius = app.corner_radius;
    let sidebar_w = effective_sidebar_width(app);

    let brand = mouse_area(
        row![
            icon_svg(SVG_NIGHTCLIENT, 19.0, p.accent_brand),
            text("NightClient")
                .size(14)
                .medium()
                .nowrap()
                .color(p.text_primary),
            Space::new().width(Length::Fill),
        ]
        .spacing(8)
        .align_y(Alignment::Center)
        .width(Length::Fill),
    )
    .on_press(Message::TitleBarPressed);

    let refresh_btn = Button::new(
        container(icon_svg(SVG_REFRESH, 14.0, p.text_muted))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center),
    )
    .on_press(Message::RefreshAll)
    .style(p.ghost_button(6.0))
    .width(Length::Fixed(28.0))
    .height(Length::Fixed(28.0))
    .padding(0);

    let add_btn = Button::new(
        container(icon_svg(SVG_PLUS, 14.0, p.text_primary))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center),
    )
    .on_press(Message::OpenCreateModal)
    .style(p.secondary_button())
    .width(Length::Fixed(28.0))
    .height(Length::Fixed(28.0))
    .padding(0);

    let header_row = container(
        row![brand, refresh_btn, add_btn]
            .spacing(4)
            .align_y(Alignment::Center),
    )
    .height(42)
    .padding(Padding::new(0.0).left(14.0).right(12.0))
    .align_y(Vertical::Center);

    let show_filter = app.instances.len() > 4 || !app.sidebar_filter.is_empty();
    let filter_bar: Option<Element<'_, Message>> = if show_filter {
        let sort_key = match app.instance_sort {
            InstanceSort::LastPlayed => "sidebar.sort.recent",
            InstanceSort::Name => "sidebar.sort.name",
            InstanceSort::Playtime => "sidebar.sort.playtime",
        };
        let search_box = container(
            row![
                icon_svg(SVG_SEARCH, 13.0, p.text_dim),
                text_input(app.tr("sidebar.filter_placeholder"), &app.sidebar_filter)
                    .on_input(Message::SidebarFilterChanged)
                    .style(p.bare_text_input_style())
                    .padding(Padding::new(0.0).left(6.0).right(6.0))
                    .size(12)
                    .width(Length::Fill),
            ]
            .align_y(Alignment::Center)
            .height(Length::Fill)
            .padding(Padding::new(0.0).left(8.0)),
        )
        .width(Length::Fill)
        .height(Length::Fixed(30.0))
        .align_y(Vertical::Center)
        .style(p.input_shell_style());

        Some(
            container(
                row![
                    search_box,
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_SORT, 12.0, p.text_muted),
                            text(app.tr(sort_key))
                                .size(11)
                                .mono()
                                .nowrap()
                                .color(p.text_muted),
                        ]
                        .spacing(4)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::CycleInstanceSort)
                    .style(p.ghost_button(6.0))
                    .height(Length::Fixed(30.0))
                    .padding(Padding::new(0.0).left(6.0).right(6.0)),
                ]
                .spacing(4)
                .align_y(Alignment::Center),
            )
            .padding(Padding::new(0.0).left(12.0).right(12.0))
            .into(),
        )
    } else {
        None
    };

    let q = app.sidebar_filter.trim().to_lowercase();
    let filtered: Vec<&InstanceRecord> = app
        .instances
        .iter()
        .filter(|inst| {
            q.is_empty()
                || inst.config.name.to_lowercase().contains(&q)
                || inst.config.mc_version.to_lowercase().contains(&q)
                || inst.config.loader.display_name().to_lowercase().contains(&q)
        })
        .collect();

    let name_budget = if sidebar_w <= SIDEBAR_COMPACT_WIDTH { 15 } else { 18 };
    let meta_budget = if sidebar_w <= SIDEBAR_COMPACT_WIDTH { 19 } else { 22 };

    let mut list_col = Column::new()
        .spacing(4)
        .width(Length::Fill)
        .padding(Padding::new(0.0).left(12.0).right(12.0));

    if filtered.is_empty() {
        let empty_key = if app.instances.is_empty() {
            "sidebar.empty"
        } else {
            "sidebar.no_matches"
        };
        list_col = list_col.push(
            container(text(app.tr(empty_key)).size(12).nowrap().color(p.text_dim))
                .padding(Padding::new(16.0).left(8.0))
                .width(Length::Fill),
        );
    } else {
        for inst in filtered {
            list_col = list_col.push(sidebar_instance_row(app, inst, name_budget, meta_budget));
        }
    }

    let instance_scroll = scrollable(list_col)
        .height(Length::Fill)
        .width(Length::Fill)
        .style(p.custom_scrollbar_style());

    let footer = container(view_sidebar_footer(app, sidebar_w))
        .padding(Padding::new(10.0).left(12.0).right(12.0).bottom(12.0))
        .width(Length::Fill);

    let mut body = column![header_row].spacing(6).width(Length::Fill);
    if let Some(fb) = filter_bar {
        body = body.push(fb);
    }

    let mut root_col = column![body, instance_scroll]
        .spacing(8)
        .width(Length::Fill)
        .height(Length::Fill);

    if let Some(ref info) = app.launcher_update
        && info.is_update_available
        && app.config.dismissed_update_tag.as_deref() != Some(info.tag_name.as_str())
    {
        let label = app.tr_fmt("update.sidebar_banner", &[("version", &info.latest_version)]);
        let update_btn = container(
            row![
                Button::new(vcenter(
                    row![
                        icon_svg(SVG_DOWNLOAD, 13.0, p.accent_brand),
                        text(label)
                            .size(12)
                            .medium()
                            .nowrap()
                            .color(p.text_primary),
                        Space::new().width(Length::Fill),
                    ]
                    .spacing(8)
                    .align_y(Alignment::Center)
                    .width(Length::Fill),
                ))
                .on_press(Message::OpenLauncherUpdateModal)
                .style(p.secondary_button())
                .height(Length::Fixed(32.0))
                .width(Length::Fill)
                .padding(Padding::new(0.0).left(10.0).right(8.0)),
                Button::new(
                    container(icon_svg(SVG_WIN_CLOSE, 10.5, p.text_muted))
                        .width(Length::Fill)
                        .height(Length::Fill)
                        .align_x(Horizontal::Center)
                        .align_y(Vertical::Center),
                )
                .on_press(Message::DismissLauncherUpdate(info.tag_name.clone()))
                .style(p.secondary_button())
                .width(Length::Fixed(30.0))
                .height(Length::Fixed(32.0))
                .padding(0),
            ]
            .spacing(6)
            .align_y(Alignment::Center)
            .width(Length::Fill),
        )
        .padding(Padding::new(0.0).left(12.0).right(12.0))
        .width(Length::Fill);
        root_col = root_col.push(update_btn);
    }

    root_col = root_col
        .push(container(hairline_divider(p)).padding(Padding::new(0.0).left(12.0).right(12.0)))
        .push(footer);

    container(root_col)
        .width(Length::Fixed(sidebar_w))
        .height(Length::Fill)
        .style(p.sidebar_surface_style(radius))
        .into()
}

fn sidebar_instance_row<'a>(
    app: &'a App,
    inst: &'a InstanceRecord,
    name_budget: usize,
    meta_budget: usize,
) -> Element<'a, Message> {
    let p = app.palette;
    let is_selected = app.page != Page::Settings
        && app.selected_instance_id.as_deref() == Some(inst.config.id.as_str());
    let elapsed = app.elapsed_secs(&inst.config.id);
    let nc_armed = inst.config.nightclient_enabled
        && app.visuals_compat_for(inst) == VisualsCompatibility::Ready;

    let icon_color = if is_selected {
        p.text_primary
    } else {
        p.text_muted
    };

    let tile = container(icon_svg(loader_icon_svg(inst.config.loader), 15.0, icon_color))
        .width(30)
        .height(30)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center)
        .style(p.monogram_tile_style(is_selected, 7.0));

    let meta_text = format!(
        "{} · {}",
        inst.config.loader.display_name(),
        inst.config.mc_version
    );

    let display_name = truncate_ellipsis(&inst.config.name, name_budget);
    let display_meta = truncate_ellipsis(&meta_text, meta_budget);

    let title = if is_selected {
        text(display_name)
            .size(13)
            .medium()
            .nowrap()
            .color(p.text_primary)
    } else {
        text(display_name)
            .size(13)
            .nowrap()
            .color(p.text_secondary)
    };

    let subtitle = text(display_meta)
        .size(11)
        .nowrap()
        .color(if is_selected { p.text_muted } else { p.text_dim });

    let info_col = container(column![title, subtitle].spacing(1).width(Length::Fill))
        .width(Length::Fill)
        .clip(true);

    let mut row_items = row![tile, info_col]
        .spacing(10)
        .align_y(Alignment::Center)
        .width(Length::Fill);

    if let Some(secs) = elapsed {
        row_items = row_items.push(
            row![
                container(Space::new().width(6))
                    .width(6)
                    .height(6)
                    .style(move |_| container::Style {
                        background: Some(Background::Color(p.accent_green)),
                        border: Border {
                            radius: 3.0.into(),
                            ..Default::default()
                        },
                        ..Default::default()
                    }),
                text(app.i18n.format_elapsed(secs))
                    .size(10.5)
                    .mono_medium()
                    .nowrap()
                    .color(p.accent_green),
            ]
            .spacing(4)
            .align_y(Alignment::Center),
        );
    } else if nc_armed {
        row_items = row_items.push(icon_svg(
            SVG_NIGHTCLIENT,
            12.5,
            if is_selected { p.text_secondary } else { p.text_dim },
        ));
    }

    let id_click = inst.config.id.clone();
    let id_ctx = inst.config.id.clone();
    let btn = Button::new(vcenter(row_items).width(Length::Fill))
        .on_press(Message::SelectInstance(id_click))
        .style(p.sidebar_instance(is_selected))
        .padding(Padding::new(0.0).left(8.0).right(10.0))
        .height(Length::Fixed(46.0))
        .width(Length::Fill);

    mouse_area(btn)
        .on_right_press(Message::OpenModal(
            crate::app::ActiveModal::InstanceContextMenu(id_ctx),
        ))
        .into()
}

fn view_sidebar_footer(app: &App, sidebar_w: f32) -> Element<'_, Message> {
    let p = app.palette;
    let player_budget = if sidebar_w <= SIDEBAR_COMPACT_WIDTH { 10 } else { 13 };

    let (display_name, kind_icon, kind_label, avatar_handle) =
        if let Some(acc) = app.active_account() {
            let (ic, key) = match acc.kind {
                AccountKind::Microsoft => (SVG_SHIELD_CHECK, "sidebar.account_microsoft"),
                AccountKind::Offline => (SVG_USER, "sidebar.account_offline"),
            };
            (
                truncate_ellipsis(&acc.username, player_budget),
                ic,
                app.tr(key),
                app.avatar_handle_for(&acc.id),
            )
        } else {
            (
                app.tr("sidebar.account_none").to_string(),
                SVG_USER,
                app.tr("sidebar.account_offline"),
                &app.default_avatar_handle,
            )
        };

    let avatar = player_skin_badge(avatar_handle, 26.0, p);

    let account_btn = Button::new(
        vcenter(
            row![
                avatar,
                container(
                    column![
                        text(display_name)
                            .size(12.5)
                            .medium()
                            .nowrap()
                            .color(p.text_primary),
                        row![
                            icon_svg(kind_icon, 10.5, p.text_dim),
                            text(kind_label).size(10.5).nowrap().color(p.text_dim),
                        ]
                        .spacing(4)
                        .align_y(Alignment::Center),
                    ]
                    .spacing(1)
                    .width(Length::Fill),
                )
                .width(Length::Fill)
                .clip(true),
                icon_svg(SVG_CHEVRONS_UP_DOWN, 14.0, p.text_muted),
            ]
            .spacing(9)
            .align_y(Alignment::Center)
            .width(Length::Fill),
        )
        .width(Length::Fill),
    )
    .on_press(Message::ToggleAccountPopover)
    .style(p.sidebar_instance(app.account_popover_open))
    .padding(Padding::new(0.0).left(8.0).right(8.0))
    .height(Length::Fixed(42.0))
    .width(Length::Fill);

    let on_settings = app.page == Page::Settings;
    let next_page = if on_settings {
        Page::Workspace
    } else {
        Page::Settings
    };

    let settings_btn = Button::new(
        container(icon_svg(
            SVG_SETTINGS,
            16.0,
            if on_settings {
                p.text_primary
            } else {
                p.text_secondary
            },
        ))
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center),
    )
    .on_press(Message::SelectPage(next_page))
    .style(move |theme, status| {
        if on_settings {
            let mut s = (p.secondary_button())(theme, status);
            s.background = Some(Background::Color(p.bg_hover));
            s
        } else {
            (p.ghost_button(8.0))(theme, status)
        }
    })
    .width(Length::Fixed(42.0))
    .height(Length::Fixed(42.0))
    .padding(0);

    row![account_btn, settings_btn]
        .spacing(6)
        .align_y(Alignment::Center)
        .width(Length::Fill)
        .into()
}
