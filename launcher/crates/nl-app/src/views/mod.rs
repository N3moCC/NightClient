pub mod account_dialogs;
pub mod dialogs;
pub mod home;
pub mod navigation;
pub mod settings;
pub mod welcome;

use crate::app::{App, Message, Page};
use crate::icons::{SVG_CHECK, SVG_CLOSE, icon_svg};
use crate::theme::TextExt;
use crate::widgets::{vcenter, vertical_hairline};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{Button, Space, column, container, mouse_area, row, stack, text};
use iced::{Alignment, Element, Length, Padding, window};

pub fn view_app(app: &App) -> Element<'_, Message> {
    let p = app.palette;
    let radius = app.corner_radius;

    let content_shell: Element<'_, Message> = if let Some(ref welcome_state) = app.welcome {
        welcome::view_welcome(app, welcome_state)
    } else {
        let body: Element<'_, Message> = match app.page {
            Page::Workspace => home::view_home(app),
            Page::Settings => settings::view_settings(app),
        };

        let sidebar = navigation::view_sidebar(app);
        let right_canvas = container(
            column![navigation::view_canvas_top_bar(app), body]
                .width(Length::Fill)
                .height(Length::Fill),
        )
        .width(Length::Fill)
        .height(Length::Fill);

        row![sidebar, vertical_hairline(p), right_canvas]
            .width(Length::Fill)
            .height(Length::Fill)
            .into()
    };

    let edge = 6.0;
    let handle = |w: Length, h: Length, dir: window::Direction| {
        mouse_area(container(Space::new()).width(w).height(h)).on_press(Message::ResizeWindow(dir))
    };

    let resize_overlay = column![
        row![
            handle(Length::Fixed(edge), Length::Fixed(edge), window::Direction::NorthWest),
            handle(Length::Fill, Length::Fixed(edge), window::Direction::North),
            handle(Length::Fixed(edge), Length::Fixed(edge), window::Direction::NorthEast),
        ]
        .width(Length::Fill),
        row![
            handle(Length::Fixed(edge), Length::Fill, window::Direction::West),
            Space::new().width(Length::Fill),
            handle(Length::Fixed(edge), Length::Fill, window::Direction::East),
        ]
        .width(Length::Fill)
        .height(Length::Fill),
        row![
            handle(Length::Fixed(edge), Length::Fixed(edge), window::Direction::SouthWest),
            handle(Length::Fill, Length::Fixed(edge), window::Direction::South),
            handle(Length::Fixed(edge), Length::Fixed(edge), window::Direction::SouthEast),
        ]
        .width(Length::Fill),
    ]
    .width(Length::Fill)
    .height(Length::Fill);

    let inset = if radius > 0.0 { 1.0 } else { 0.0 };
    let padded_shell = container(content_shell)
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(inset);

    let mut root: Element<'_, Message> = container(stack![padded_shell, resize_overlay])
        .width(Length::Fill)
        .height(Length::Fill)
        .clip(true)
        .style(p.root_window_style(radius))
        .into();

    if app.account_popover_open {
        root = stack![root, account_dialogs::view_account_popover(app)].into();
    }

    if app.servers_popover_open && app.welcome.is_none() {
        root = stack![root, home::view_servers_popover(app)].into();
    }

    if app.ms_login_busy {
        root = stack![root, account_dialogs::view_microsoft_login_modal(app)].into();
    }

    if let Some(ref modal) = app.modal {
        root = stack![root, dialogs::view_modal(app, modal)].into();
    }

    if app.dragging_files {
        let drop_overlay = container(
            container(
                text(app.tr("content.empty.hint"))
                    .size(13)
                    .medium()
                    .shaped()
                    .color(p.text_primary),
            )
            .padding([14, 20])
            .style(p.dialog_panel_style()),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center)
        .style(move |_| container::Style {
            background: Some(iced::Background::Color(p.scrim)),
            ..Default::default()
        });
        root = stack![root, drop_overlay].into();
    }

    if let Some(ref toast) = app.toast {
        let (ic, ic_col) = if toast.is_error {
            (SVG_CLOSE, p.accent_red)
        } else {
            (SVG_CHECK, p.accent_green)
        };
        let pill = container(
            row![
                icon_svg(ic, 14.0, ic_col),
                text(&toast.message)
                    .size(12.5)
                    .shaped()
                    .color(p.text_primary),
                Button::new(vcenter(icon_svg(SVG_CLOSE, 12.0, p.text_muted)))
                    .on_press(Message::DismissToast)
                    .style(p.ghost_button(5.0))
                    .height(Length::Fixed(22.0))
                    .padding([0, 4]),
            ]
            .spacing(10)
            .align_y(Alignment::Center),
        )
        .max_width(480.0)
        .padding([8, 12])
        .style(p.toast_panel_style());

        let toast_layer = container(
            column![
                Space::new().height(Length::Fill),
                row![Space::new().width(Length::Fill), pill],
            ]
            .width(Length::Fill)
            .height(Length::Fill),
        )
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(Padding::new(16.0));

        root = stack![root, toast_layer].into();
    }

    root
}
