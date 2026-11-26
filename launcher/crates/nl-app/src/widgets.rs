use crate::icons::{SVG_CHEVRON_DOWN, icon_svg, loader_icon_svg};
use crate::theme::{Palette, TextExt};
use iced::alignment::{Horizontal, Vertical};
use iced::widget::{Button, Container, Row, Space, container, image, pick_list, row, stack, text};
use iced::{Alignment, Background, Border, Color, Element, Length, Padding};
use nl_core::LoaderKind;

pub fn vcenter<'a, M: 'a>(content: impl Into<Element<'a, M>>) -> Container<'a, M> {
    container(content)
        .height(Length::Fill)
        .align_y(Vertical::Center)
}

pub fn center_box<'a, M: 'a>(content: impl Into<Element<'a, M>>) -> Container<'a, M> {
    container(content)
        .width(Length::Fill)
        .height(Length::Fill)
        .align_x(Horizontal::Center)
        .align_y(Vertical::Center)
}

pub fn hairline_divider<'a, M: 'a>(p: Palette) -> Element<'a, M> {
    container(Space::new().height(1))
        .width(Length::Fill)
        .height(1)
        .style(move |_| container::Style {
            background: Some(Background::Color(p.hairline)),
            ..Default::default()
        })
        .into()
}

pub fn subtle_row_divider<'a, M: 'a>(p: Palette) -> Element<'a, M> {
    container(Space::new().height(1))
        .width(Length::Fill)
        .height(1)
        .style(move |_| container::Style {
            background: Some(Background::Color(p.hairline_subtle)),
            ..Default::default()
        })
        .into()
}

pub fn vertical_hairline<'a, M: 'a>(p: Palette) -> Element<'a, M> {
    container(Space::new().width(1))
        .width(1)
        .height(Length::Fill)
        .style(move |_| container::Style {
            background: Some(Background::Color(p.hairline)),
            ..Default::default()
        })
        .into()
}

pub fn toggle_switch<'a, M: Clone + 'a>(
    enabled: bool,
    accent_green: bool,
    on_press: M,
    p: Palette,
) -> Element<'a, M> {
    let knob_color = if enabled {
        Color::WHITE
    } else {
        p.text_muted
    };
    let knob = container(Space::new().width(14))
        .width(14)
        .height(14)
        .style(move |_| container::Style {
            background: Some(Background::Color(knob_color)),
            border: Border {
                radius: 7.0.into(),
                ..Default::default()
            },
            ..Default::default()
        });

    let align = if enabled {
        Horizontal::Right
    } else {
        Horizontal::Left
    };

    Button::new(
        container(knob)
            .width(Length::Fill)
            .height(Length::Fill)
            .align_x(align)
            .align_y(Vertical::Center),
    )
    .on_press(on_press)
    .style(p.toggle_switch_style(enabled, accent_green))
    .width(Length::Fixed(36.0))
    .height(Length::Fixed(20.0))
    .padding(Padding::new(0.0).left(3.0).right(3.0))
    .into()
}

pub fn loader_segmented_track<'a, M, F>(
    current: LoaderKind,
    on_select: F,
    p: Palette,
) -> Element<'a, M>
where
    M: Clone + 'a,
    F: Fn(LoaderKind) -> M + Copy + 'a,
{
    let items = LoaderKind::ALL
        .iter()
        .fold(Row::new().spacing(2).width(Length::Fill), |acc, &loader| {
            let active = current == loader;
            let icon_color = if active { p.text_primary } else { p.text_muted };
            acc.push(
                Button::new(
                    container(
                        row![
                            icon_svg(loader_icon_svg(loader), 13.0, icon_color),
                            text(loader.display_name()).size(11.5).medium().nowrap(),
                        ]
                        .spacing(5)
                        .align_y(Alignment::Center),
                    )
                    .width(Length::Fill)
                    .height(Length::Fill)
                    .align_x(Horizontal::Center)
                    .align_y(Vertical::Center),
                )
                .on_press(on_select(loader))
                .style(p.segmented_button(active))
                .height(Length::Fixed(28.0))
                .padding(Padding::new(0.0).left(4.0).right(4.0))
                .width(Length::Fill),
            )
        });

    container(items)
        .width(Length::Fill)
        .padding(3)
        .style(p.segmented_track_style())
        .into()
}

pub fn loader_version_picker_row<'a, M, F>(
    loader: LoaderKind,
    label: String,
    options: Vec<String>,
    selected: String,
    picker_w: f32,
    on_select: F,
    p: Palette,
) -> Element<'a, M>
where
    M: Clone + 'static,
    F: Fn(String) -> M + 'a,
{
    let picker = container(stack![
        pick_list(options, Some(selected), on_select)
            .handle(pick_list::Handle::None)
            .text_shaping(iced::widget::text::Shaping::Advanced)
            .style(p.pick_list_style())
            .menu_style(p.custom_menu_style())
            .text_size(12)
            .padding(Padding::new(7.0).left(10.0).right(28.0))
            .width(Length::Fixed(picker_w)),
        container(icon_svg(SVG_CHEVRON_DOWN, 14.0, p.text_muted))
            .width(Length::Fixed(picker_w))
            .height(Length::Fill)
            .padding(Padding::new(0.0).right(10.0))
            .align_x(Horizontal::Right)
            .align_y(Vertical::Center),
    ])
    .height(Length::Fixed(32.0));

    row![
        icon_svg(loader_icon_svg(loader), 13.5, p.text_muted),
        container(text(label).size(12).nowrap().color(p.text_secondary))
            .width(Length::Fill)
            .clip(true),
        picker,
    ]
    .spacing(8)
    .align_y(Alignment::Center)
    .width(Length::Fill)
    .into()
}

pub fn player_skin_badge<'a, M: 'a>(
    handle: &'a image::Handle,
    size: f32,
    p: Palette,
) -> Element<'a, M> {
    container(
        image(handle.clone())
            .width(size)
            .height(size)
            .content_fit(iced::ContentFit::Cover)
            .border_radius(5.0)
            .filter_method(image::FilterMethod::Nearest),
    )
    .width(size)
    .height(size)
    .style(move |_| container::Style {
        border: Border {
            radius: 5.0.into(),
            color: p.hairline_strong,
            width: 1.0,
        },
        ..Default::default()
    })
    .into()
}
