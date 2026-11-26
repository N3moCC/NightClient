use crate::app::{App, Message, PendingDelete};
use crate::icons::{
    SVG_CLOSE, SVG_COPY, SVG_EXTERNAL, SVG_MICROSOFT, SVG_PLUS, SVG_SHIELD_CHECK, SVG_USER,
    icon_svg,
};
use crate::theme::{TextExt, truncate_ellipsis};
use crate::widgets::{hairline_divider, player_skin_badge, vcenter};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{
    Button, Column, Space, column, container, mouse_area, row, scrollable, stack, text, text_input,
};
use iced::{Alignment, Background, Element, Length, Padding};
use nl_auth::AccountKind;
use nl_platform::SecretBackend;

pub fn view_account_popover(app: &App) -> Element<'_, Message> {
    let p = app.palette;

    let mut list = Column::new()
        .spacing(2)
        .width(Length::Fill)
        .padding(Padding::new(0.0).right(4.0));

    for acc in &app.account_book.accounts {
        let active = app.account_book.active_account_id.as_deref() == Some(acc.id.as_str());
        let (kind_icon, kind_key) = match acc.kind {
            AccountKind::Microsoft => (SVG_SHIELD_CHECK, "sidebar.account_microsoft"),
            AccountKind::Offline => (SVG_USER, "sidebar.account_offline"),
        };

        let display_name = truncate_ellipsis(&acc.username, 14);
        let select_btn = Button::new(
            vcenter(
                row![
                    player_skin_badge(app.avatar_handle_for(&acc.id), 22.0, p),
                    container(
                        text(display_name)
                            .size(12.5)
                            .medium()
                            .nowrap()
                            .color(if active {
                                p.text_primary
                            } else {
                                p.text_secondary
                            }),
                    )
                    .width(Length::Fill)
                    .clip(true),
                    row![
                        icon_svg(kind_icon, 10.5, p.text_dim),
                        text(app.tr(kind_key)).size(10.5).nowrap().color(p.text_dim),
                    ]
                    .spacing(3.5)
                    .align_y(Alignment::Center),
                ]
                .spacing(8)
                .align_y(Alignment::Center)
                .width(Length::Fill),
            )
            .width(Length::Fill),
        )
        .on_press(Message::SelectAccount(acc.id.clone()))
        .style(p.sidebar_instance(active))
        .padding(Padding::new(0.0).left(8.0).right(8.0))
        .height(Length::Fixed(34.0))
        .width(Length::Fill);

        let delete_btn = Button::new(
            container(icon_svg(SVG_CLOSE, 12.0, p.text_dim))
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center),
        )
        .on_press(Message::RequestDelete(PendingDelete::Account {
            id: acc.id.clone(),
            name: acc.username.clone(),
        }))
        .style(p.ghost_button(6.0))
        .width(Length::Fixed(24.0))
        .height(Length::Fixed(24.0))
        .padding(0);

        list = list.push(
            row![select_btn, delete_btn]
                .spacing(2)
                .align_y(Alignment::Center),
        );
    }

    let accounts_scroll = container(scrollable(list).style(p.custom_scrollbar_style()))
        .max_height(152.0)
        .width(Length::Fill);

    let local_input_box = container(
        row![
            icon_svg(SVG_USER, 13.0, p.text_dim),
            text_input(app.tr("account.local_placeholder"), &app.offline_name_input)
                .on_input(Message::OfflineNameChanged)
                .on_submit(Message::SubmitOfflineAccount)
                .style(p.bare_text_input_style())
                .padding(Padding::new(0.0).left(6.0).right(8.0))
                .size(12)
                .width(Length::Fill),
        ]
        .align_y(Alignment::Center)
        .height(Length::Fill)
        .padding(Padding::new(0.0).left(8.0)),
    )
    .width(Length::Fill)
    .height(Length::Fixed(32.0))
    .align_y(Vertical::Center)
    .style(p.input_shell_style());

    let offline_row = row![
        local_input_box,
        Button::new(
            container(icon_svg(SVG_PLUS, 14.0, p.text_primary))
                .width(Length::Fill)
                .height(Length::Fill)
                .align_x(Horizontal::Center)
                .align_y(Vertical::Center),
        )
        .on_press(Message::SubmitOfflineAccount)
        .style(p.secondary_button())
        .width(Length::Fixed(32.0))
        .height(Length::Fixed(32.0))
        .padding(0),
    ]
    .spacing(6)
    .align_y(Alignment::Center);

    let ms_row = row![
        Button::new(
            container(
                row![
                    icon_svg(SVG_MICROSOFT, 12.0, p.cta_text),
                    text(app.tr("account.sign_in_microsoft"))
                        .size(12)
                        .medium()
                        .nowrap()
                        .color(p.cta_text),
                ]
                .spacing(7)
                .align_y(Alignment::Center),
            )
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center),
        )
        .on_press(Message::StartMicrosoftBrowserLogin)
        .style(p.primary_button())
        .height(Length::Fixed(34.0))
        .padding(Padding::new(0.0).left(10.0).right(10.0))
        .width(Length::Fill),
        Button::new(vcenter(
            text(app.tr("account.device_code_btn"))
                .size(11.5)
                .medium()
                .nowrap()
                .color(p.text_secondary),
        ))
        .on_press(Message::StartMicrosoftDeviceLogin)
        .style(p.secondary_button())
        .height(Length::Fixed(34.0))
        .padding(Padding::new(0.0).left(9.0).right(9.0)),
    ]
    .spacing(6)
    .align_y(Alignment::Center);

    let storage_key = match app.secret_backend {
        SecretBackend::OsKeyring => {
            if cfg!(target_os = "windows") {
                "account.storage.keyring.windows"
            } else if cfg!(target_os = "macos") {
                "account.storage.keyring.macos"
            } else {
                "account.storage.keyring.linux"
            }
        }
        SecretBackend::EncryptedVault => "account.storage.vault",
    };

    let storage_line = row![
        icon_svg(SVG_SHIELD_CHECK, 11.0, p.text_dim),
        text(app.tr(storage_key)).size(10.5).nowrap().color(p.text_dim),
    ]
    .spacing(5)
    .align_y(Alignment::Center);

    let card = mouse_area(
        container(
            column![
                accounts_scroll,
                hairline_divider(p),
                offline_row,
                ms_row,
                storage_line,
            ]
            .spacing(8)
            .width(Length::Fill),
        )
        .width(Length::Fixed(308.0))
        .padding(10)
        .style(p.popover_panel_style()),
    )
    .on_press(Message::NoOp)
    .on_right_press(Message::NoOp);

    let backdrop = mouse_area(container(Space::new()).width(Length::Fill).height(Length::Fill))
        .on_press(Message::CloseAccountPopover)
        .on_right_press(Message::CloseAccountPopover);

    stack![
        backdrop,
        container(
            column![Space::new().height(Length::Fill), card]
                .width(Length::Fill)
                .height(Length::Fill),
        )
        .padding(Padding::new(0.0).left(12.0).bottom(60.0))
        .width(Length::Fill)
        .height(Length::Fill),
    ]
    .into()
}

pub fn view_microsoft_login_modal(app: &App) -> Element<'_, Message> {
    let p = app.palette;

    let body_content: Element<'_, Message> = if let Some(ref dc) = app.device_code_prompt {
        column![
            text(app.tr("account.ms_modal.code_hint"))
                .size(12.5)
                .shaped()
                .color(p.text_secondary),
            row![
                container(
                    text(&dc.verification_uri)
                        .size(12)
                        .mono()
                        .nowrap()
                        .color(p.text_muted),
                )
                .width(Length::Fill)
                .clip(true),
                Button::new(vcenter(
                    row![
                        text(app.tr("account.ms_modal.open"))
                            .size(12)
                            .medium()
                            .nowrap()
                            .color(p.cta_text),
                        icon_svg(SVG_EXTERNAL, 12.0, p.cta_text),
                    ]
                    .spacing(6)
                    .align_y(Alignment::Center),
                ))
                .on_press(Message::OpenUrl(dc.verification_uri.clone()))
                .style(p.primary_button())
                .height(Length::Fixed(32.0))
                .padding(Padding::new(0.0).left(12.0).right(12.0)),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
            row![
                container(
                    text(&dc.user_code)
                        .size(22)
                        .mono_medium()
                        .nowrap()
                        .color(p.text_primary),
                )
                .padding(10)
                .style(p.inset_panel_style())
                .align_x(Horizontal::Center)
                .width(Length::Fill),
                Button::new(vcenter(
                    row![
                        icon_svg(SVG_COPY, 13.5, p.text_secondary),
                        text(app.tr("account.ms_modal.copy"))
                            .size(12)
                            .medium()
                            .nowrap(),
                    ]
                    .spacing(6)
                    .align_y(Alignment::Center),
                ))
                .on_press(Message::CopyText(dc.user_code.clone()))
                .style(p.secondary_button())
                .height(Length::Fixed(44.0))
                .padding(Padding::new(0.0).left(14.0).right(16.0)),
            ]
            .spacing(8)
            .align_y(Alignment::Center),
        ]
        .spacing(12)
        .into()
    } else {
        text(app.tr("account.ms_modal.browser_hint"))
            .size(13)
            .shaped()
            .color(p.text_secondary)
            .into()
    };

    let dialog = mouse_area(
        container(
            column![
                row![
                    icon_svg(SVG_MICROSOFT, 15.0, p.text_secondary),
                    text(app.tr("account.ms_modal.title"))
                        .size(16)
                        .medium()
                        .nowrap()
                        .color(p.text_primary),
                    Space::new().width(Length::Fill),
                    Button::new(
                        container(icon_svg(SVG_CLOSE, 13.0, p.text_muted))
                            .width(Length::Fill)
                            .height(Length::Fill)
                            .align_x(Horizontal::Center)
                            .align_y(Vertical::Center),
                    )
                    .on_press(Message::CancelMicrosoftLogin)
                    .style(p.ghost_button(6.0))
                    .width(Length::Fixed(26.0))
                    .height(Length::Fixed(26.0))
                    .padding(0),
                ]
                .spacing(8)
                .align_y(Alignment::Center),
                body_content,
                hairline_divider(p),
                row![
                    Space::new().width(Length::Fill),
                    Button::new(vcenter(
                        text(app.tr("account.ms_modal.cancel"))
                            .size(12.5)
                            .medium()
                            .nowrap(),
                    ))
                    .on_press(Message::CancelMicrosoftLogin)
                    .style(p.secondary_button())
                    .height(Length::Fixed(34.0))
                    .padding(Padding::new(0.0).left(14.0).right(14.0)),
                ],
            ]
            .spacing(14),
        )
        .width(Length::Fixed(412.0))
        .padding(18)
        .style(p.dialog_panel_style()),
    )
    .on_press(Message::NoOp)
    .on_right_press(Message::NoOp);

    let scrim = mouse_area(
        container(Space::new())
            .width(Length::Fill)
            .height(Length::Fill)
            .style(move |_| container::Style {
                background: Some(Background::Color(p.scrim)),
                ..Default::default()
            }),
    )
    .on_press(Message::CancelMicrosoftLogin)
    .on_right_press(Message::CancelMicrosoftLogin);

    stack![
        scrim,
        container(dialog)
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(Horizontal::Center)
            .align_y(Vertical::Center)
            .padding(24),
    ]
    .into()
}
