use crate::app::{
    ActiveModal, App, CloneInstanceForm, CreateInstanceForm, Message, PendingDelete,
};
use crate::icons::{
    SVG_ALERT, SVG_CHECK, SVG_CHEVRON_DOWN, SVG_CHEVRON_LEFT, SVG_CHEVRON_RIGHT, SVG_CHEVRON_UP,
    SVG_CLONE, SVG_CLOSE, SVG_COPY, SVG_DOWNLOAD, SVG_EDIT, SVG_EXTERNAL, SVG_EYE, SVG_FOLDER,
    SVG_MODRINTH, SVG_NIGHTCLIENT, SVG_PLAY, SVG_PLUS, SVG_SEARCH, SVG_TRASH, SVG_UPLOAD,
    SVG_VANILLA, SVG_WRENCH, icon_svg,
};
use crate::markdown_view::{fit_image_size, render_markdown};
use crate::theme::{TextExt, truncate_ellipsis};
use crate::widgets::{
    center_box, hairline_divider, loader_segmented_track, loader_version_picker_row,
    subtle_row_divider, toggle_switch, vcenter,
};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{
    Button, Column, Space, column, container, image, mouse_area, row, scrollable, stack, text,
    text_input,
};
use iced::{Alignment, Background, Border, Element, Length, Padding};
use nl_core::LoaderKind;
use std::path::Path;

pub fn view_modal<'a>(app: &'a App, modal: &'a ActiveModal) -> Element<'a, Message> {
    match modal {
        ActiveModal::CreateInstance(form) => view_create_modal(app, form),
        ActiveModal::CloneInstance(form) => view_clone_modal(app, form),
        ActiveModal::RenameInstance { draft, .. } => view_rename_modal(app, draft),
        ActiveModal::InstanceContextMenu(id) => view_context_modal(app, id),
        ActiveModal::ScreenshotLightbox(path) => view_lightbox_modal(app, path),
        ActiveModal::ConfirmDelete(pending) => view_confirm_delete_modal(app, pending),
        ActiveModal::LauncherUpdate => view_launcher_update_modal(app),
    }
}

fn modal_backdrop<'a>(
    app: &'a App,
    dialog: Element<'a, Message>,
    inner_click_msg: Message,
) -> Element<'a, Message> {
    let p = app.palette;
    let scrim = mouse_area(
        container(Space::new())
            .width(Length::Fill)
            .height(Length::Fill)
            .style(move |_| container::Style {
                background: Some(Background::Color(p.scrim)),
                ..Default::default()
            }),
    )
    .on_press(Message::CloseModal)
    .on_right_press(Message::CloseModal);

    let shielded = mouse_area(dialog)
        .on_press(inner_click_msg)
        .on_right_press(Message::NoOp);

    stack![
        scrim,
        container(shielded)
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .padding(24),
    ]
    .into()
}

fn close_icon_btn<'a>(app: &'a App, msg: Message) -> Element<'a, Message> {
    let p = app.palette;
    Button::new(
        container(icon_svg(SVG_CLOSE, 13.5, p.text_muted))
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center),
    )
    .on_press(msg)
    .style(p.ghost_button(6.0))
    .width(Length::Fixed(28.0))
    .height(Length::Fixed(28.0))
    .padding(0)
    .into()
}

fn view_create_modal<'a>(app: &'a App, form: &'a CreateInstanceForm) -> Element<'a, Message> {
    let p = app.palette;
    let ver_display = if form.mc_version.trim().is_empty() {
        "1.21.4"
    } else {
        form.mc_version.trim()
    };

    let auto_placeholder = app.tr_fmt(
        "modal.create.name_placeholder",
        &[
            ("loader", form.loader.display_name()),
            ("version", ver_display),
        ],
    );

    let visuals_eligible = app.is_visuals_eligible(form.loader, ver_display);

    let mut body = column![
        row![
            icon_svg(SVG_PLUS, 15.5, p.text_secondary),
            text(app.tr("modal.create.title"))
                .size(16)
                .medium()
                .nowrap()
                .color(p.text_primary),
            Space::new().width(Length::Fill),
            close_icon_btn(app, Message::CloseModal),
        ]
        .spacing(8)
        .align_y(Alignment::Center),
        text_input(&auto_placeholder, &form.name)
            .on_input(Message::CreateFormName)
            .on_submit(Message::SubmitCreateInstance)
            .style(p.text_input_style())
            .padding(Padding::new(8.0).left(10.0).right(10.0))
            .size(13),
        loader_segmented_track(form.loader, Message::CreateFormLoader, p),
        version_selector(
            app,
            VersionSelectorProps {
                current_val: ver_display,
                filter: &form.version_filter,
                expanded: form.version_expanded,
                show_snapshots: form.show_snapshots,
                toggle_msg: Message::CreateFormToggleVersionList,
                toggle_snapshots_msg: Message::CreateFormShowSnapshots(!form.show_snapshots),
            },
            Message::CreateFormVersionFilter,
            Message::CreateFormMcVersion,
        ),
    ]
    .spacing(12);

    if form.loader != LoaderKind::Vanilla {
        let key = (form.loader, ver_display.to_string());
        let loading = app.loader_versions_loading.contains(&key);
        let label = app.tr_fmt(
            if loading {
                "options.loader_loading"
            } else {
                "options.loader_version"
            },
            &[("loader", form.loader.display_name())],
        );
        let (options, selected) = app.loader_version_dropdown_options(
            form.loader,
            ver_display,
            form.loader_version.as_deref(),
        );
        body = body.push(loader_version_picker_row(
            form.loader,
            label,
            options,
            selected,
            210.0,
            Message::CreateFormLoaderVersionLabel,
            p,
        ));
    }

    if visuals_eligible {
        let nc_row = container(
            row![
                icon_svg(
                    SVG_NIGHTCLIENT,
                    15.0,
                    if form.nightclient_enabled {
                        p.accent_green
                    } else {
                        p.text_muted
                    },
                ),
                text(app.tr("modal.create.nc_label"))
                    .size(12.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary)
                    .width(Length::Fill),
                toggle_switch(
                    form.nightclient_enabled,
                    true,
                    Message::CreateFormNightClient(!form.nightclient_enabled),
                    p,
                ),
            ]
            .spacing(10)
            .align_y(Alignment::Center),
        )
        .width(Length::Fill)
        .padding([8, 12])
        .style(p.inset_panel_style());
        body = body.push(nc_row);
    }

    body = body.push(hairline_divider(p)).push(
        row![
            Button::new(vcenter(
                row![
                    icon_svg(SVG_MODRINTH, 13.5, p.text_muted),
                    text(app.tr("modal.create.import_mrpack"))
                        .size(12)
                        .nowrap()
                        .color(p.text_secondary),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::ImportMrpackPick)
            .style(p.ghost_button(6.0))
            .height(Length::Fixed(34.0))
            .padding(Padding::new(0.0).left(8.0).right(10.0)),
            Space::new().width(Length::Fill),
            Button::new(vcenter(
                row![
                    icon_svg(SVG_PLUS, 13.0, p.cta_text),
                    text(app.tr("modal.create.submit"))
                        .size(12.5)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(5)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::SubmitCreateInstance)
            .style(p.primary_button())
            .height(Length::Fixed(34.0))
            .padding(Padding::new(0.0).left(14.0).right(16.0)),
        ]
        .align_y(Alignment::Center),
    );

    let inner_msg = if form.version_expanded {
        Message::CreateFormCloseVersionList
    } else {
        Message::NoOp
    };

    modal_backdrop(
        app,
        container(body)
            .width(Length::Fixed(448.0))
            .padding(18)
            .style(p.dialog_panel_style())
            .into(),
        inner_msg,
    )
}

struct VersionSelectorProps<'a> {
    current_val: &'a str,
    filter: &'a str,
    expanded: bool,
    show_snapshots: bool,
    toggle_msg: Message,
    toggle_snapshots_msg: Message,
}

fn version_selector<'a, FFilter, FSelect>(
    app: &'a App,
    props: VersionSelectorProps<'a>,
    on_filter: FFilter,
    on_select: FSelect,
) -> Element<'a, Message>
where
    FFilter: Fn(String) -> Message + 'a,
    FSelect: Fn(String) -> Message + 'a,
{
    let VersionSelectorProps {
        current_val,
        filter,
        expanded,
        show_snapshots,
        toggle_msg,
        toggle_snapshots_msg,
    } = props;
    let p = app.palette;
    let chevron = if expanded {
        SVG_CHEVRON_UP
    } else {
        SVG_CHEVRON_DOWN
    };

    let mode_label = if app.mojang_loading {
        app.tr("modal.create.loading")
    } else if show_snapshots {
        app.tr("modal.create.all")
    } else {
        app.tr("modal.create.release")
    };

    let trigger = Button::new(
        vcenter(
            row![
                icon_svg(SVG_VANILLA, 13.5, p.text_muted),
                container(
                    text(current_val)
                        .size(12)
                        .mono_medium()
                        .nowrap()
                        .color(p.text_primary),
                )
                .width(Length::Fill)
                .clip(true),
                text(mode_label).size(11).nowrap().color(p.text_dim),
                icon_svg(chevron, 14.0, p.text_muted),
            ]
            .spacing(8)
            .align_y(Alignment::Center)
            .width(Length::Fill),
        )
        .width(Length::Fill),
    )
    .on_press(toggle_msg)
    .height(Length::Fixed(34.0))
    .padding(Padding::new(0.0).left(10.0).right(10.0))
    .style(move |_, status| {
        let bg = match status {
            iced::widget::button::Status::Hovered => p.bg_hover,
            _ => p.bg_inset,
        };
        iced::widget::button::Style {
            background: Some(Background::Color(bg)),
            text_color: p.text_primary,
            border: Border {
                color: if expanded {
                    p.hairline_strong
                } else {
                    p.hairline
                },
                width: 1.0,
                radius: if expanded {
                    iced::border::Radius::new(0.0).top_left(7.0).top_right(7.0)
                } else {
                    7.0.into()
                },
            },
            ..Default::default()
        }
    })
    .width(Length::Fill);

    if !expanded {
        return trigger.into();
    }

    let filter_lower = filter.to_ascii_lowercase();
    let versions: Vec<String> = if app.mojang_versions.is_empty() {
        app.night_manifest
            .release(app.config.nightclient_channel)
            .map(|r| r.supported_mc.iter().rev().cloned().collect())
            .unwrap_or_else(|| vec!["1.21.4".into(), "26.3".into()])
    } else {
        app.mojang_versions
            .iter()
            .filter(|v| show_snapshots || v.kind == "release")
            .map(|v| v.id.clone())
            .filter(|id| filter_lower.is_empty() || id.to_ascii_lowercase().contains(&filter_lower))
            .take(60)
            .collect()
    };

    let mut list_col = Column::new()
        .spacing(2)
        .padding(Padding::new(4.0).right(12.0));

    for v in versions {
        let selected = v == current_val;
        let has_nc = app
            .night_manifest
            .release(app.config.nightclient_channel)
            .map(|r| r.supported_mc.iter().any(|x| x == &v))
            .unwrap_or(false);

        let mut row_el = row![
            container(
                text(v.clone())
                    .size(12)
                    .mono()
                    .nowrap()
                    .color(if selected {
                        p.text_primary
                    } else {
                        p.text_secondary
                    }),
            )
            .width(Length::Fill)
            .clip(true),
        ]
        .spacing(6)
        .align_y(Alignment::Center)
        .width(Length::Fill);

        if has_nc {
            row_el = row_el.push(icon_svg(SVG_NIGHTCLIENT, 11.0, p.text_dim));
        }

        if selected {
            row_el = row_el.push(
                container(Space::new().width(5))
                    .width(5)
                    .height(5)
                    .style(move |_| container::Style {
                        background: Some(Background::Color(p.accent_green)),
                        border: Border {
                            radius: 2.5.into(),
                            ..Default::default()
                        },
                        ..Default::default()
                    }),
            );
        }

        list_col = list_col.push(
            Button::new(vcenter(row_el).width(Length::Fill))
                .on_press(on_select(v))
                .height(Length::Fixed(28.0))
                .padding(Padding::new(0.0).left(8.0).right(8.0))
                .style(p.segmented_button(selected))
                .width(Length::Fill),
        );
    }

    let filter_row = container(
        row![
            icon_svg(SVG_SEARCH, 12.0, p.text_dim),
            text_input(app.tr("modal.create.version_filter"), filter)
                .on_input(on_filter)
                .style(p.bare_text_input_style())
                .padding(Padding::new(0.0).left(6.0).right(6.0))
                .size(11.5)
                .width(Length::Fill),
        ]
        .align_y(Alignment::Center)
        .height(Length::Fill)
        .padding(Padding::new(0.0).left(8.0)),
    )
    .height(Length::Fixed(28.0))
    .width(Length::Fill);

    let snap_key = if show_snapshots {
        "modal.create.hide_snapshots"
    } else {
        "modal.create.show_snapshots"
    };

    let footer_btn = Button::new(
        vcenter(
            row![
                icon_svg(SVG_EYE, 13.0, p.text_muted),
                text(app.tr(snap_key)).size(11).nowrap().color(p.text_muted),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        )
        .width(Length::Fill),
    )
    .on_press(toggle_snapshots_msg)
    .height(Length::Fixed(28.0))
    .padding(Padding::new(0.0).left(10.0).right(10.0))
    .style(p.ghost_button(0.0))
    .width(Length::Fill);

    let dropdown_box = mouse_area(
        container(column![
            filter_row,
            subtle_row_divider(p),
            scrollable(list_col)
                .height(Length::Fixed(140.0))
                .style(p.custom_scrollbar_style()),
            subtle_row_divider(p),
            footer_btn,
        ])
        .width(Length::Fill)
        .style(move |_| container::Style {
            background: Some(Background::Color(p.bg_inset)),
            border: Border {
                color: p.hairline_strong,
                width: 1.0,
                radius: iced::border::Radius::new(0.0)
                    .bottom_left(7.0)
                    .bottom_right(7.0),
            },
            ..Default::default()
        }),
    )
    .on_press(Message::NoOp);

    column![trigger, dropdown_box].width(Length::Fill).into()
}

fn view_clone_modal<'a>(app: &'a App, form: &'a CloneInstanceForm) -> Element<'a, Message> {
    let p = app.palette;
    let src_line = app
        .instances
        .iter()
        .find(|i| i.config.id == form.source_id)
        .map(|src| {
            app.tr_fmt(
                "modal.clone.from",
                &[
                    ("name", &truncate_ellipsis(&src.config.name, 20)),
                    ("mc", &src.config.mc_version),
                    ("loader", src.config.loader.display_name()),
                ],
            )
        })
        .unwrap_or_default();

    let mut body = column![
        row![
            icon_svg(SVG_CLONE, 15.5, p.text_secondary),
            text(app.tr("modal.clone.title"))
                .size(16)
                .medium()
                .nowrap()
                .color(p.text_primary),
            Space::new().width(Length::Fill),
            close_icon_btn(app, Message::CloseModal),
        ]
        .spacing(8)
        .align_y(Alignment::Center),
        text(src_line).size(12).mono().nowrap().color(p.text_muted),
        text_input(app.tr("modal.clone.name_placeholder"), &form.new_name)
            .on_input(Message::CloneFormName)
            .on_submit(Message::SubmitCloneInstance)
            .style(p.text_input_style())
            .padding(Padding::new(8.0).left(10.0).right(10.0))
            .size(13),
        loader_segmented_track(form.target_loader, Message::CloneFormLoader, p),
        version_selector(
            app,
            VersionSelectorProps {
                current_val: &form.target_mc,
                filter: &form.version_filter,
                expanded: form.version_expanded,
                show_snapshots: form.show_snapshots,
                toggle_msg: Message::CloneFormToggleVersionList,
                toggle_snapshots_msg: Message::CloneFormShowSnapshots(!form.show_snapshots),
            },
            Message::CloneFormVersionFilter,
            Message::CloneFormMc,
        ),
    ]
    .spacing(12);

    if form.target_loader != LoaderKind::Vanilla {
        let key = (form.target_loader, form.target_mc.clone());
        let loading = app.loader_versions_loading.contains(&key);
        let label = app.tr_fmt(
            if loading {
                "options.loader_loading"
            } else {
                "options.loader_version"
            },
            &[("loader", form.target_loader.display_name())],
        );
        let (options, selected) = app.loader_version_dropdown_options(
            form.target_loader,
            &form.target_mc,
            form.target_loader_version.as_deref(),
        );
        body = body.push(loader_version_picker_row(
            form.target_loader,
            label,
            options,
            selected,
            210.0,
            Message::CloneFormLoaderVersionLabel,
            p,
        ));
    }

    body = body
        .push(
            row![
                text(app.tr("modal.clone.migrate_mods"))
                    .size(12)
                    .shaped()
                    .color(p.text_secondary)
                    .width(Length::Fill),
                toggle_switch(
                    form.migrate_mods,
                    false,
                    Message::CloneFormMigrateMods(!form.migrate_mods),
                    p,
                ),
            ]
            .spacing(10)
            .align_y(Alignment::Center),
        )
        .push(hairline_divider(p))
        .push(
            row![
                Space::new().width(Length::Fill),
                Button::new(vcenter(
                    text(app.tr("modal.cancel")).size(12.5).medium().nowrap(),
                ))
                .on_press(Message::CloseModal)
                .style(p.secondary_button())
                .height(Length::Fixed(34.0))
                .padding(Padding::new(0.0).left(14.0).right(14.0)),
                Button::new(vcenter(
                    text(app.tr("modal.clone.submit"))
                        .size(12.5)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ))
                .on_press(Message::SubmitCloneInstance)
                .style(p.primary_button())
                .height(Length::Fixed(34.0))
                .padding(Padding::new(0.0).left(16.0).right(16.0)),
            ]
            .spacing(8),
        );

    let inner_msg = if form.version_expanded {
        Message::CloneFormCloseVersionList
    } else {
        Message::NoOp
    };

    modal_backdrop(
        app,
        container(body)
            .width(Length::Fixed(448.0))
            .padding(18)
            .style(p.dialog_panel_style())
            .into(),
        inner_msg,
    )
}

fn view_rename_modal<'a>(app: &'a App, draft: &'a str) -> Element<'a, Message> {
    let p = app.palette;
    let body = column![
        row![
            icon_svg(SVG_EDIT, 15.0, p.text_secondary),
            text(app.tr("modal.rename.title"))
                .size(16)
                .medium()
                .nowrap()
                .color(p.text_primary),
            Space::new().width(Length::Fill),
            close_icon_btn(app, Message::CloseModal),
        ]
        .spacing(8)
        .align_y(Alignment::Center),
        text_input(app.tr("modal.rename.placeholder"), draft)
            .on_input(Message::RenameDraftChanged)
            .on_submit(Message::SubmitRename)
            .style(p.text_input_style())
            .padding(Padding::new(8.0).left(10.0).right(10.0))
            .size(13),
        hairline_divider(p),
        row![
            Space::new().width(Length::Fill),
            Button::new(vcenter(
                text(app.tr("modal.cancel")).size(12.5).medium().nowrap(),
            ))
            .on_press(Message::CloseModal)
            .style(p.secondary_button())
            .height(Length::Fixed(34.0))
            .padding(Padding::new(0.0).left(14.0).right(14.0)),
            Button::new(vcenter(
                text(app.tr("modal.rename.save"))
                    .size(12.5)
                    .medium()
                    .nowrap()
                    .color(p.cta_text),
            ))
            .on_press(Message::SubmitRename)
            .style(p.primary_button())
            .height(Length::Fixed(34.0))
            .padding(Padding::new(0.0).left(16.0).right(16.0)),
        ]
        .spacing(8),
    ]
    .spacing(12);

    modal_backdrop(
        app,
        container(body)
            .width(Length::Fixed(376.0))
            .padding(18)
            .style(p.dialog_panel_style())
            .into(),
        Message::NoOp,
    )
}

fn view_context_modal<'a>(app: &'a App, instance_id: &'a str) -> Element<'a, Message> {
    let p = app.palette;
    let Some(inst) = app.instances.iter().find(|i| i.config.id == instance_id) else {
        return Space::new().into();
    };

    let item = |ic: &'static str, label_key: &'a str, danger: bool, msg: Message| {
        let col = if danger { p.accent_red } else { p.text_primary };
        let ic_col = if danger { p.accent_red } else { p.text_muted };
        Button::new(
            vcenter(
                row![
                    icon_svg(ic, 14.0, ic_col),
                    text(app.tr(label_key)).size(12.5).nowrap().color(col),
                ]
                .spacing(10)
                .align_y(Alignment::Center),
            )
            .width(Length::Fill),
        )
        .on_press(msg)
        .style(p.ghost_button(6.0))
        .height(Length::Fixed(34.0))
        .padding(Padding::new(0.0).left(10.0).right(10.0))
        .width(Length::Fill)
    };

    let id = inst.config.id.clone();
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

    let dialog = container(
        column![
            row![
                text(truncate_ellipsis(&inst.config.name, 26))
                    .size(14)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                Space::new().width(Length::Fill),
                close_icon_btn(app, Message::CloseModal),
            ]
            .align_y(Alignment::Center),
            hairline_divider(p),
            item(
                SVG_PLAY,
                "modal.context.launch",
                false,
                Message::LaunchInstance {
                    id: id.clone(),
                    quick_play: None,
                },
            ),
            item(
                SVG_FOLDER,
                "modal.context.open_folder",
                false,
                Message::OpenInstanceFolder(id.clone()),
            ),
            item(
                SVG_EDIT,
                "modal.context.rename",
                false,
                Message::OpenModal(ActiveModal::RenameInstance {
                    id: id.clone(),
                    draft: inst.config.name.clone(),
                }),
            ),
            item(
                SVG_WRENCH,
                "modal.context.repair",
                false,
                Message::RepairInstance(id.clone()),
            ),
            item(
                SVG_CLONE,
                "modal.context.clone",
                false,
                Message::OpenModal(ActiveModal::CloneInstance(clone_form)),
            ),
            item(
                SVG_UPLOAD,
                "modal.context.export",
                false,
                Message::ExportInstanceMrpack(id.clone()),
            ),
            hairline_divider(p),
            item(
                SVG_TRASH,
                "modal.context.delete",
                true,
                Message::RequestDelete(PendingDelete::Instance {
                    id,
                    name: inst.config.name.clone(),
                }),
            ),
        ]
        .spacing(4),
    )
    .width(Length::Fixed(272.0))
    .padding(12)
    .style(p.dialog_panel_style());

    modal_backdrop(app, dialog.into(), Message::NoOp)
}

fn view_lightbox_modal<'a>(app: &'a App, path: &'a Path) -> Element<'a, Message> {
    let p = app.palette;
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("screenshot.png");

    let dialog_w = (app.window_size.width - 72.0).clamp(560.0, 960.0);
    let stage_w = (dialog_w - 40.0).max(320.0);
    let stage_h = (app.window_size.height - 156.0).clamp(260.0, 600.0);

    let matched = app
        .screenshots
        .iter()
        .enumerate()
        .find(|(_, s)| s.path == path);
    let total = app.screenshots.len();
    let (img_w, img_h) = matched
        .map(|(_, s)| (s.width, s.height))
        .filter(|&(w, h)| w > 0 && h > 0)
        .map(|(w, h)| fit_image_size(w, h, stage_w - 16.0, stage_h - 16.0))
        .unwrap_or((stage_w - 16.0, stage_h - 16.0));

    let mut header = row![
        container(
            text(truncate_ellipsis(name, 34))
                .size(13)
                .mono_medium()
                .nowrap()
                .color(p.text_primary),
        )
        .clip(true),
    ]
    .spacing(8)
    .align_y(Alignment::Center)
    .width(Length::Fill);

    if let Some((idx, shot)) = matched {
        if shot.width > 0 && shot.height > 0 {
            header = header.push(
                container(
                    text(format!("{}×{}", shot.width, shot.height))
                        .size(11)
                        .mono()
                        .nowrap()
                        .color(p.text_dim),
                )
                .padding(Padding::new(2.0).left(6.0).right(6.0))
                .style(p.monogram_tile_style(false, 5.0)),
            );
        }
        if total > 1 {
            header = header.push(
                text(format!("{} / {}", idx + 1, total))
                    .size(11.5)
                    .mono()
                    .nowrap()
                    .color(p.text_muted),
            );
        }
    }

    header = header.push(Space::new().width(Length::Fill));

    if total > 1 && matched.is_some() {
        header = header
            .push(
                Button::new(center_box(icon_svg(
                    SVG_CHEVRON_LEFT,
                    14.0,
                    p.text_secondary,
                )))
                .on_press(Message::StepLightboxScreenshot(-1))
                .style(p.secondary_button())
                .width(Length::Fixed(30.0))
                .height(Length::Fixed(30.0))
                .padding(0),
            )
            .push(
                Button::new(center_box(icon_svg(
                    SVG_CHEVRON_RIGHT,
                    14.0,
                    p.text_secondary,
                )))
                .on_press(Message::StepLightboxScreenshot(1))
                .style(p.secondary_button())
                .width(Length::Fixed(30.0))
                .height(Length::Fixed(30.0))
                .padding(0),
            );
    }

    header = header
        .push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_COPY, 13.0, p.text_secondary),
                    text(app.tr("screenshots.copy")).size(12).medium().nowrap(),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::CopyScreenshot(path.to_path_buf()))
            .style(p.secondary_button())
            .height(Length::Fixed(30.0))
            .padding([0, 10]),
        )
        .push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_TRASH, 13.0, p.accent_red),
                    text(app.tr("screenshots.delete"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.accent_red),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press(Message::RequestDelete(PendingDelete::Screenshot {
                path: path.to_path_buf(),
                name: name.to_string(),
            }))
            .style(p.subtle_danger_button())
            .height(Length::Fixed(30.0))
            .padding([0, 10]),
        )
        .push(close_icon_btn(app, Message::CloseModal));

    let stage = container(
        image(image::Handle::from_path(path))
            .width(Length::Fixed(img_w))
            .height(Length::Fixed(img_h))
            .content_fit(iced::ContentFit::Contain)
            .border_radius(6.0),
    )
    .width(Length::Fill)
    .height(Length::Fixed(stage_h))
    .align_x(Horizontal::Center)
    .align_y(Vertical::Center)
    .padding(8)
    .clip(true)
    .style(p.inset_panel_style());

    let dialog = container(column![header, stage].spacing(12).width(Length::Fill))
        .width(Length::Fixed(dialog_w))
        .padding(16)
        .style(p.dialog_panel_style());

    modal_backdrop(app, dialog.into(), Message::NoOp)
}

fn view_confirm_delete_modal<'a>(app: &'a App, pending: &'a PendingDelete) -> Element<'a, Message> {
    let p = app.palette;
    let message = match pending {
        PendingDelete::Instance { name, .. } => {
            app.tr_fmt("confirm.delete.instance", &[("name", name)])
        }
        PendingDelete::ContentFile { name, .. } => {
            app.tr_fmt("confirm.delete.mod", &[("name", name)])
        }
        PendingDelete::Screenshot { name, .. } => {
            app.tr_fmt("confirm.delete.screenshot", &[("name", name)])
        }
        PendingDelete::Account { name, .. } => {
            app.tr_fmt("confirm.delete.account", &[("name", name)])
        }
    };

    let dialog = container(
        column![
            row![
                icon_svg(SVG_ALERT, 16.0, p.accent_red),
                text(app.tr("confirm.delete.title"))
                    .size(15.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                Space::new().width(Length::Fill),
                close_icon_btn(app, Message::CloseModal),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
            text(message).size(13).shaped().color(p.text_secondary),
            hairline_divider(p),
            row![
                Space::new().width(Length::Fill),
                Button::new(vcenter(
                    text(app.tr("modal.cancel")).size(12.5).medium().nowrap(),
                ))
                .on_press(Message::CloseModal)
                .style(p.secondary_button())
                .height(Length::Fixed(34.0))
                .padding(Padding::new(0.0).left(14.0).right(14.0)),
                Button::new(vcenter(
                    row![
                        icon_svg(SVG_TRASH, 13.0, iced::Color::WHITE),
                        text(app.tr("confirm.delete.confirm"))
                            .size(12.5)
                            .medium()
                            .nowrap()
                            .color(iced::Color::WHITE),
                    ]
                    .spacing(6)
                    .align_y(Alignment::Center),
                ))
                .on_press(Message::ConfirmDeleteExecute)
                .style(p.danger_button())
                .height(Length::Fixed(34.0))
                .padding(Padding::new(0.0).left(14.0).right(16.0)),
            ]
            .spacing(8),
        ]
        .spacing(14),
    )
    .width(Length::Fixed(396.0))
    .padding(18)
    .style(p.dialog_panel_style());

    modal_backdrop(app, dialog.into(), Message::NoOp)
}

fn view_launcher_update_modal<'a>(app: &'a App) -> Element<'a, Message> {
    let p = app.palette;
    let dialog_w = (app.window_size.width - 96.0).clamp(440.0, 620.0);
    let body_max_h = (app.window_size.height - 260.0).clamp(200.0, 400.0);

    let Some(info) = app.launcher_update.as_ref() else {
        let fallback_url = nl_night::GITHUB_RELEASES_PAGE.to_string();
        let dialog = container(
            column![
                row![
                    icon_svg(SVG_NIGHTCLIENT, 18.0, p.accent_brand),
                    text(app.tr("update.modal.title_changelog"))
                        .size(15.5)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                    Space::new().width(Length::Fill),
                    close_icon_btn(app, Message::CloseModal),
                ]
                .spacing(8)
                .align_y(Alignment::Center),
                text(if app.checking_launcher_update {
                    app.tr("settings.updates_checking").to_string()
                } else {
                    app.tr_fmt(
                        "update.modal.up_to_date",
                        &[("version", app.launcher_version())],
                    )
                })
                .size(13)
                .shaped()
                .color(p.text_secondary),
                hairline_divider(p),
                row![
                    Button::new(vcenter(
                        row![
                            icon_svg(SVG_EXTERNAL, 12.5, p.text_secondary),
                            text(app.tr("update.modal.open_github"))
                                .size(12.5)
                                .medium()
                                .nowrap()
                                .color(p.text_primary),
                        ]
                        .spacing(6)
                        .align_y(Alignment::Center),
                    ))
                    .on_press(Message::OpenUrl(fallback_url))
                    .style(p.secondary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(12.0).right(14.0)),
                    Space::new().width(Length::Fill),
                    Button::new(vcenter(
                        text(app.tr("modal.close")).size(12.5).medium().nowrap(),
                    ))
                    .on_press(Message::CloseModal)
                    .style(p.secondary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(14.0).right(14.0)),
                ]
                .spacing(8)
                .align_y(Alignment::Center),
            ]
            .spacing(14),
        )
        .width(Length::Fixed(460.0))
        .padding(18)
        .style(p.dialog_panel_style());

        return modal_backdrop(app, dialog.into(), Message::NoOp);
    };

    let title_key = if info.is_update_available {
        "update.modal.title"
    } else {
        "update.modal.title_changelog"
    };

    let version_line = if info.is_update_available {
        app.tr_fmt(
            "update.modal.version_transition",
            &[
                ("current", &info.current_version),
                ("latest", &info.latest_version),
            ],
        )
    } else {
        format!("v{}", info.latest_version)
    };

    let channel_label = if info.prerelease {
        app.tr("update.modal.prerelease")
    } else {
        app.tr("update.modal.stable")
    };

    let channel_pill = container(
        text(channel_label)
            .size(10.5)
            .medium()
            .nowrap()
            .color(if info.prerelease {
                p.accent_orange
            } else {
                p.accent_green
            }),
    )
    .padding(Padding::new(2.0).left(7.0).right(7.0))
    .style(p.inset_panel_style());

    let mut meta_row = row![
        text(version_line)
            .size(12)
            .mono_medium()
            .nowrap()
            .color(p.text_secondary),
        channel_pill,
    ]
    .spacing(8)
    .align_y(Alignment::Center);

    if !info.published_at.is_empty() {
        meta_row = meta_row.push(
            text(&info.published_at)
                .size(11.5)
                .mono()
                .nowrap()
                .color(p.text_dim),
        );
    }

    let header = column![
        row![
            icon_svg(SVG_NIGHTCLIENT, 18.0, p.accent_brand),
            text(app.tr(title_key))
                .size(16)
                .medium()
                .nowrap()
                .color(p.text_primary),
            Space::new().width(Length::Fill),
            close_icon_btn(app, Message::CloseModal),
        ]
        .spacing(8)
        .align_y(Alignment::Center),
        meta_row,
    ]
    .spacing(6);

    let changelog_content: Element<'_, Message> = if app.update_changelog_blocks.is_empty() {
        text(app.tr("update.modal.empty_changelog"))
            .size(12.5)
            .shaped()
            .color(p.text_muted)
            .into()
    } else {
        render_markdown(
            &app.update_changelog_blocks,
            &app.update_changelog_expanded,
            &app.remote_images,
            dialog_w - 64.0,
            p,
            Message::OpenUrl,
            Message::ToggleChangelogDetails,
        )
    };

    let changelog_box = container(
        scrollable(
            container(changelog_content)
                .width(Length::Fill)
                .padding(Padding::new(12.0).left(14.0).right(14.0)),
        )
        .height(Length::Fixed(body_max_h))
        .width(Length::Fill)
        .style(p.custom_scrollbar_style()),
    )
    .width(Length::Fill)
    .style(p.inset_panel_style());

    let mut content_col = column![header, changelog_box].spacing(12).width(Length::Fill);

    if app.installing_launcher_update {
        let pct = (app.launcher_update_progress * 100.0).round() as u32;
        let label = app.tr_fmt("update.modal.downloading", &[("pct", &pct.to_string())]);
        let bar_fill_w = ((dialog_w - 36.0) * app.launcher_update_progress.clamp(0.04, 1.0)).max(6.0);
        let progress_track = container(
            container(Space::new().width(Length::Fixed(bar_fill_w)).height(Length::Fixed(6.0)))
                .style(move |_| container::Style {
                    background: Some(Background::Color(p.accent_brand)),
                    border: Border {
                        radius: 3.0.into(),
                        ..Default::default()
                    },
                    ..Default::default()
                }),
        )
        .width(Length::Fill)
        .height(Length::Fixed(6.0))
        .style(move |_| container::Style {
            background: Some(Background::Color(p.bg_inset)),
            border: Border {
                radius: 3.0.into(),
                ..Default::default()
            },
            ..Default::default()
        });
        content_col = content_col.push(
            column![
                text(label).size(12).mono().color(p.text_secondary),
                progress_track,
            ]
            .spacing(6)
            .width(Length::Fill),
        );
    } else if let Some(ref err) = app.launcher_update_error {
        content_col = content_col.push(
            row![
                icon_svg(SVG_ALERT, 13.5, p.accent_red),
                text(err).size(12).shaped().color(p.accent_red),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        );
    } else if info.is_update_available && info.download_url.is_none() {
        content_col = content_col.push(
            text(app.tr("update.modal.no_binary"))
                .size(12)
                .shaped()
                .color(p.text_muted),
        );
    } else if !info.is_update_available {
        content_col = content_col.push(
            row![
                icon_svg(SVG_CHECK, 13.0, p.accent_green),
                text(app.tr_fmt(
                    "update.modal.up_to_date",
                    &[("version", app.launcher_version())],
                ))
                .size(12)
                .color(p.text_secondary),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        );
    }

    let mut footer_row = row![
        Button::new(vcenter(
            row![
                icon_svg(SVG_EXTERNAL, 12.5, p.text_secondary),
                text(app.tr("update.modal.open_github"))
                    .size(12.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
            ]
            .spacing(6)
            .align_y(Alignment::Center),
        ))
        .on_press(Message::OpenUrl(info.html_url.clone()))
        .style(p.secondary_button())
        .height(Length::Fixed(34.0))
        .padding(Padding::new(0.0).left(12.0).right(14.0)),
        Space::new().width(Length::Fill),
    ]
    .spacing(8)
    .align_y(Alignment::Center);

    if info.is_update_available {
        footer_row = footer_row
            .push(
                Button::new(vcenter(
                    text(app.tr("update.modal.decline"))
                        .size(12.5)
                        .medium()
                        .nowrap(),
                ))
                .on_press_maybe(if app.installing_launcher_update {
                    None
                } else {
                    Some(Message::DismissLauncherUpdate(info.tag_name.clone()))
                })
                .style(p.secondary_button())
                .height(Length::Fixed(34.0))
                .padding(Padding::new(0.0).left(12.0).right(12.0)),
            )
            .push(
                Button::new(vcenter(
                    text(app.tr("update.modal.later"))
                        .size(12.5)
                        .medium()
                        .nowrap(),
                ))
                .on_press(Message::CloseModal)
                .style(p.secondary_button())
                .height(Length::Fixed(34.0))
                .padding(Padding::new(0.0).left(12.0).right(12.0)),
            );
    } else {
        footer_row = footer_row.push(
            Button::new(vcenter(
                text(app.tr("modal.close")).size(12.5).medium().nowrap(),
            ))
            .on_press(Message::CloseModal)
            .style(p.secondary_button())
            .height(Length::Fixed(34.0))
            .padding(Padding::new(0.0).left(14.0).right(14.0)),
        );
    }

    if info.is_update_available && info.download_url.is_some() {
        footer_row = footer_row.push(
            Button::new(vcenter(
                row![
                    icon_svg(SVG_DOWNLOAD, 13.0, p.cta_text),
                    text(app.tr("update.modal.install_restart"))
                        .size(12.5)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(6)
                .align_y(Alignment::Center),
            ))
            .on_press_maybe(if app.installing_launcher_update {
                None
            } else {
                Some(Message::StartLauncherUpdate)
            })
            .style(p.primary_button())
            .height(Length::Fixed(34.0))
            .padding(Padding::new(0.0).left(14.0).right(16.0)),
        );
    }

    content_col = content_col
        .push(hairline_divider(p))
        .push(footer_row);

    let dialog = container(content_col)
        .width(Length::Fixed(dialog_w))
        .padding(18)
        .style(p.dialog_panel_style());

    modal_backdrop(app, dialog.into(), Message::NoOp)
}
