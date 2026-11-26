use iced::font::{Family, Stretch, Style, Weight};
use iced::widget::scrollable::{AutoScroll, Rail, Scroller};
use iced::widget::text::{Shaping, Wrapping};
use iced::widget::{button, container, overlay, pick_list, slider, text_input};
use iced::{Background, Border, Color, Font, Shadow, Theme, Vector};
use nl_core::ThemeMode;
use nl_night::NcThemeTokens;

pub const FONT_SANS: Font = Font::with_name("Geist");
pub const FONT_SANS_MEDIUM: Font = Font {
    family: Family::Name("Geist"),
    weight: Weight::Medium,
    stretch: Stretch::Normal,
    style: Style::Normal,
};
pub const FONT_MONO: Font = Font::with_name("Geist Mono");
pub const FONT_MONO_MEDIUM: Font = Font {
    family: Family::Name("Geist Mono"),
    weight: Weight::Medium,
    stretch: Stretch::Normal,
    style: Style::Normal,
};

pub trait TextExt {
    fn medium(self) -> Self;
    fn mono(self) -> Self;
    fn mono_medium(self) -> Self;
    fn nowrap(self) -> Self;
    fn shaped(self) -> Self;
}

impl TextExt for iced::widget::Text<'_> {
    fn medium(self) -> Self {
        self.font(FONT_SANS_MEDIUM).shaping(Shaping::Advanced)
    }

    fn mono(self) -> Self {
        self.font(FONT_MONO).shaping(Shaping::Advanced)
    }

    fn mono_medium(self) -> Self {
        self.font(FONT_MONO_MEDIUM).shaping(Shaping::Advanced)
    }

    fn nowrap(self) -> Self {
        self.wrapping(Wrapping::None).shaping(Shaping::Advanced)
    }

    fn shaped(self) -> Self {
        self.wrapping(Wrapping::Word).shaping(Shaping::Advanced)
    }
}

pub fn truncate_ellipsis(text: &str, max_chars: usize) -> String {
    let trimmed = text.trim();
    if max_chars < 4 {
        return trimmed.chars().take(max_chars).collect();
    }
    if trimmed.chars().count() <= max_chars {
        return trimmed.to_string();
    }
    let mut out: String = trimmed.chars().take(max_chars - 1).collect();
    let cleaned = out.trim_end();
    if cleaned.chars().count() >= max_chars / 2 {
        out = cleaned.to_string();
    }
    out.push('…');
    out
}

pub fn rgba(c: [u8; 4]) -> Color {
    Color::from_rgba(
        c[0] as f32 / 255.0,
        c[1] as f32 / 255.0,
        c[2] as f32 / 255.0,
        c[3] as f32 / 255.0,
    )
}

#[derive(Debug, Clone, Copy)]
pub struct Palette {
    pub is_dark: bool,
    pub bg_canvas: Color,
    pub bg_sidebar: Color,
    pub bg_card: Color,
    pub bg_modal: Color,
    pub bg_raised: Color,
    pub bg_hover: Color,
    pub bg_row_hover: Color,
    pub bg_inset: Color,
    pub hairline: Color,
    pub hairline_subtle: Color,
    pub hairline_strong: Color,
    pub text_primary: Color,
    pub text_secondary: Color,
    pub text_muted: Color,
    pub text_dim: Color,
    pub cta_bg: Color,
    pub cta_hover: Color,
    pub cta_pressed: Color,
    pub cta_text: Color,
    pub accent_green: Color,
    pub accent_red: Color,
    pub accent_orange: Color,
    pub accent_brand: Color,
    pub scrim: Color,
}

impl Palette {
    pub fn dark() -> Self {
        Self {
            is_dark: true,
            bg_canvas: Color::from_rgba(0.078, 0.082, 0.096, 0.90),
            bg_sidebar: Color::from_rgba(0.036, 0.039, 0.049, 0.62),
            bg_card: Color::from_rgba(0.128, 0.133, 0.156, 0.84),
            bg_modal: Color::from_rgb(0.110, 0.114, 0.133),
            bg_raised: Color::from_rgba(0.165, 0.172, 0.200, 0.94),
            bg_hover: Color::from_rgba(0.212, 0.220, 0.258, 0.96),
            bg_row_hover: Color::from_rgba(1.0, 1.0, 1.0, 0.010),
            bg_inset: Color::from_rgba(0.044, 0.047, 0.058, 0.84),
            hairline: Color::from_rgba(1.0, 1.0, 1.0, 0.020),
            hairline_subtle: Color::from_rgba(1.0, 1.0, 1.0, 0.010),
            hairline_strong: Color::from_rgba(1.0, 1.0, 1.0, 0.038),
            text_primary: Color::from_rgb(0.949, 0.949, 0.961),
            text_secondary: Color::from_rgb(0.706, 0.714, 0.753),
            text_muted: Color::from_rgb(0.529, 0.541, 0.588),
            text_dim: Color::from_rgb(0.408, 0.420, 0.467),
            cta_bg: Color::from_rgb(0.949, 0.949, 0.961),
            cta_hover: Color::WHITE,
            cta_pressed: Color::from_rgb(0.855, 0.858, 0.878),
            cta_text: Color::from_rgb(0.067, 0.071, 0.086),
            accent_green: Color::from_rgb(0.286, 0.745, 0.525),
            accent_red: Color::from_rgb(0.898, 0.306, 0.325),
            accent_orange: Color::from_rgb(0.886, 0.694, 0.416),
            accent_brand: Color::from_rgb(0.949, 0.949, 0.961),
            scrim: Color::from_rgba(0.0, 0.0, 0.0, 0.58),
        }
    }

    pub fn light() -> Self {
        Self {
            is_dark: false,
            bg_canvas: Color::from_rgba(0.957, 0.961, 0.973, 0.96),
            bg_sidebar: Color::from_rgba(0.918, 0.925, 0.945, 0.94),
            bg_card: Color::from_rgba(1.0, 1.0, 1.0, 0.95),
            bg_modal: Color::from_rgb(1.0, 1.0, 1.0),
            bg_raised: Color::from_rgb(0.945, 0.949, 0.965),
            bg_hover: Color::from_rgb(0.902, 0.910, 0.933),
            bg_row_hover: Color::from_rgba(0.0, 0.0, 0.0, 0.032),
            bg_inset: Color::from_rgb(0.922, 0.929, 0.949),
            hairline: Color::from_rgba(0.06, 0.08, 0.14, 0.10),
            hairline_subtle: Color::from_rgba(0.06, 0.08, 0.14, 0.06),
            hairline_strong: Color::from_rgba(0.06, 0.08, 0.14, 0.16),
            text_primary: Color::from_rgb(0.082, 0.094, 0.125),
            text_secondary: Color::from_rgb(0.278, 0.302, 0.365),
            text_muted: Color::from_rgb(0.420, 0.447, 0.514),
            text_dim: Color::from_rgb(0.545, 0.569, 0.631),
            cta_bg: Color::from_rgb(0.098, 0.106, 0.137),
            cta_hover: Color::from_rgb(0.157, 0.169, 0.212),
            cta_pressed: Color::from_rgb(0.055, 0.063, 0.082),
            cta_text: Color::WHITE,
            accent_green: Color::from_rgb(0.090, 0.545, 0.345),
            accent_red: Color::from_rgb(0.765, 0.188, 0.220),
            accent_orange: Color::from_rgb(0.722, 0.478, 0.118),
            accent_brand: Color::from_rgb(0.098, 0.106, 0.137),
            scrim: Color::from_rgba(0.05, 0.07, 0.12, 0.36),
        }
    }

    pub fn resolve(mode: ThemeMode, sync_nc: bool, tokens: Option<&NcThemeTokens>) -> Self {
        let mut base = match mode {
            ThemeMode::Dark => Self::dark(),
            ThemeMode::Light => Self::light(),
        };
        if sync_nc
            && let Some(t) = tokens
        {
            let accent = rgba(t.accent);
            let lum = 0.2126 * accent.r + 0.7152 * accent.g + 0.0722 * accent.b;
            if (base.is_dark && lum > 0.32) || (!base.is_dark && lum < 0.55) {
                base.accent_brand = accent;
            }
        }
        base
    }

    pub fn iced_theme(self) -> Theme {
        let base = if self.is_dark {
            Theme::Dark.palette()
        } else {
            Theme::Light.palette()
        };
        Theme::custom(
            "NightClientTheme".to_string(),
            iced::theme::Palette {
                background: Color::TRANSPARENT,
                text: self.text_primary,
                primary: self.cta_bg,
                success: self.accent_green,
                danger: self.accent_red,
                ..base
            },
        )
    }

    pub fn root_window_style(self, corner_radius: f32) -> impl Fn(&Theme) -> container::Style {
        let has_border = corner_radius > 0.0;
        move |_| container::Style {
            background: Some(Background::Color(self.bg_canvas)),
            border: Border {
                color: if has_border {
                    self.hairline_strong
                } else {
                    Color::TRANSPARENT
                },
                width: if has_border { 1.0 } else { 0.0 },
                radius: corner_radius.into(),
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn sidebar_surface_style(self, corner_radius: f32) -> impl Fn(&Theme) -> container::Style {
        let inner_radius = (corner_radius - 1.0).max(0.0);
        move |_| container::Style {
            background: Some(Background::Color(self.bg_sidebar)),
            border: Border {
                radius: iced::border::Radius::new(0.0)
                    .top_left(inner_radius)
                    .bottom_left(inner_radius),
                width: 0.0,
                color: Color::TRANSPARENT,
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn card_style(self) -> impl Fn(&Theme) -> container::Style {
        move |_| container::Style {
            background: Some(Background::Color(self.bg_card)),
            border: Border {
                color: self.hairline,
                width: 1.0,
                radius: 10.0.into(),
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn inset_panel_style(self) -> impl Fn(&Theme) -> container::Style {
        move |_| container::Style {
            background: Some(Background::Color(self.bg_inset)),
            border: Border {
                color: self.hairline,
                width: 1.0,
                radius: 8.0.into(),
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn segmented_track_style(self) -> impl Fn(&Theme) -> container::Style {
        self.inset_panel_style()
    }

    pub fn input_shell_style(self) -> impl Fn(&Theme) -> container::Style {
        move |_| container::Style {
            background: Some(Background::Color(self.bg_inset)),
            border: Border {
                color: self.hairline_strong,
                width: 1.0,
                radius: 7.0.into(),
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn dialog_panel_style(self) -> impl Fn(&Theme) -> container::Style {
        move |_| container::Style {
            background: Some(Background::Color(self.bg_modal)),
            border: Border {
                color: self.hairline_strong,
                width: 1.0,
                radius: 12.0.into(),
            },
            shadow: Shadow {
                color: Color::from_rgba(0.0, 0.0, 0.0, if self.is_dark { 0.56 } else { 0.20 }),
                offset: Vector::new(0.0, 14.0),
                blur_radius: 36.0,
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn popover_panel_style(self) -> impl Fn(&Theme) -> container::Style {
        move |_| container::Style {
            background: Some(Background::Color(self.bg_modal)),
            border: Border {
                color: self.hairline_strong,
                width: 1.0,
                radius: 10.0.into(),
            },
            shadow: Shadow {
                color: Color::from_rgba(0.0, 0.0, 0.0, if self.is_dark { 0.50 } else { 0.16 }),
                offset: Vector::new(0.0, 8.0),
                blur_radius: 26.0,
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn toast_panel_style(self) -> impl Fn(&Theme) -> container::Style {
        move |_| container::Style {
            background: Some(Background::Color(self.bg_modal)),
            border: Border {
                color: self.hairline_strong,
                width: 1.0,
                radius: 8.0.into(),
            },
            shadow: Shadow {
                color: Color::from_rgba(0.0, 0.0, 0.0, if self.is_dark { 0.45 } else { 0.14 }),
                offset: Vector::new(0.0, 6.0),
                blur_radius: 20.0,
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn monogram_tile_style(self, active: bool, radius: f32) -> impl Fn(&Theme) -> container::Style {
        move |_| {
            let bg = if self.is_dark {
                if active {
                    Color::from_rgba(1.0, 1.0, 1.0, 0.028)
                } else {
                    Color::from_rgba(1.0, 1.0, 1.0, 0.012)
                }
            } else if active {
                Color::from_rgba(0.06, 0.08, 0.14, 0.08)
            } else {
                Color::from_rgba(0.06, 0.08, 0.14, 0.04)
            };
            container::Style {
                background: Some(Background::Color(bg)),
                border: Border {
                    radius: radius.into(),
                    width: 1.0,
                    color: if active {
                        self.hairline_strong
                    } else {
                        self.hairline_subtle
                    },
                },
                text_color: Some(if active {
                    self.text_primary
                } else {
                    self.text_secondary
                }),
                ..Default::default()
            }
        }
    }

    pub fn avatar_style_for_name(self, name: &str) -> container::Style {
        let hash: u32 = name.bytes().fold(0, |acc, b| acc.wrapping_add(b as u32));
        let dark_fills = [
            Color::from_rgb(0.155, 0.180, 0.172),
            Color::from_rgb(0.160, 0.172, 0.205),
            Color::from_rgb(0.190, 0.174, 0.156),
            Color::from_rgb(0.176, 0.162, 0.198),
        ];
        let light_fills = [
            Color::from_rgb(0.86, 0.90, 0.88),
            Color::from_rgb(0.86, 0.88, 0.94),
            Color::from_rgb(0.92, 0.89, 0.85),
            Color::from_rgb(0.90, 0.87, 0.93),
        ];
        let fill = if self.is_dark {
            dark_fills[(hash as usize) % dark_fills.len()]
        } else {
            light_fills[(hash as usize) % light_fills.len()]
        };
        container::Style {
            background: Some(Background::Color(fill)),
            border: Border {
                color: self.hairline,
                width: 1.0,
                radius: 7.0.into(),
            },
            text_color: Some(self.text_primary),
            ..Default::default()
        }
    }

    pub fn primary_button(self) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| match status {
            button::Status::Hovered => button::Style {
                background: Some(Background::Color(self.cta_hover)),
                text_color: self.cta_text,
                border: Border {
                    radius: 8.0.into(),
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            },
            button::Status::Pressed => button::Style {
                background: Some(Background::Color(self.cta_pressed)),
                text_color: self.cta_text,
                border: Border {
                    radius: 8.0.into(),
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            },
            button::Status::Disabled => button::Style {
                background: Some(Background::Color(self.bg_raised)),
                text_color: self.text_dim,
                border: Border {
                    radius: 8.0.into(),
                    color: self.hairline,
                    width: 1.0,
                },
                snap: true,
                ..Default::default()
            },
            _ => button::Style {
                background: Some(Background::Color(self.cta_bg)),
                text_color: self.cta_text,
                border: Border {
                    radius: 8.0.into(),
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            },
        }
    }

    pub fn primary_split_left(self) -> impl Fn(&Theme, button::Status) -> button::Style {
        let radius = iced::border::Radius::new(0.0)
            .top_left(8.0)
            .bottom_left(8.0);
        move |_, status| {
            let bg = match status {
                button::Status::Hovered => self.cta_hover,
                button::Status::Pressed => self.cta_pressed,
                button::Status::Disabled => self.bg_raised,
                _ => self.cta_bg,
            };
            let tc = if matches!(status, button::Status::Disabled) {
                self.text_dim
            } else {
                self.cta_text
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color: tc,
                border: Border {
                    radius,
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn primary_split_right(self, active: bool) -> impl Fn(&Theme, button::Status) -> button::Style {
        let radius = iced::border::Radius::new(0.0)
            .top_right(8.0)
            .bottom_right(8.0);
        move |_, status| {
            let bg = match status {
                button::Status::Hovered => self.cta_hover,
                button::Status::Pressed => self.cta_pressed,
                button::Status::Disabled => self.bg_raised,
                _ if active => self.cta_hover,
                _ => self.cta_bg,
            };
            let tc = if matches!(status, button::Status::Disabled) {
                self.text_dim
            } else {
                self.cta_text
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color: tc,
                border: Border {
                    radius,
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn secondary_button(self) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| match status {
            button::Status::Hovered => button::Style {
                background: Some(Background::Color(self.bg_hover)),
                text_color: self.text_primary,
                border: Border {
                    radius: 7.0.into(),
                    color: Color {
                        a: if self.is_dark { 0.075 } else { 0.22 },
                        ..self.text_primary
                    },
                    width: 1.0,
                },
                snap: true,
                ..Default::default()
            },
            button::Status::Pressed => button::Style {
                background: Some(Background::Color(self.bg_raised)),
                text_color: self.text_primary,
                border: Border {
                    radius: 7.0.into(),
                    color: self.hairline_strong,
                    width: 1.0,
                },
                snap: true,
                ..Default::default()
            },
            button::Status::Disabled => button::Style {
                background: Some(Background::Color(self.bg_inset)),
                text_color: self.text_dim,
                border: Border {
                    radius: 7.0.into(),
                    color: self.hairline_subtle,
                    width: 1.0,
                },
                snap: true,
                ..Default::default()
            },
            _ => button::Style {
                background: Some(Background::Color(self.bg_raised)),
                text_color: self.text_primary,
                border: Border {
                    radius: 7.0.into(),
                    color: self.hairline_strong,
                    width: 1.0,
                },
                snap: true,
                ..Default::default()
            },
        }
    }

    pub fn ghost_button(self, radius: f32) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let (bg, text_color) = match status {
                button::Status::Hovered => (
                    Color {
                        a: if self.is_dark { 0.024 } else { 0.06 },
                        ..self.text_primary
                    },
                    self.text_primary,
                ),
                button::Status::Pressed => (
                    Color {
                        a: if self.is_dark { 0.040 } else { 0.10 },
                        ..self.text_primary
                    },
                    self.text_primary,
                ),
                button::Status::Disabled => (Color::TRANSPARENT, self.text_dim),
                _ => (Color::TRANSPARENT, self.text_secondary),
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color,
                border: Border {
                    radius: radius.into(),
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn subtle_danger_button(self) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let (bg, border_color) = match status {
                button::Status::Hovered => (
                    Color { a: 0.16, ..self.accent_red },
                    Color { a: 0.42, ..self.accent_red },
                ),
                button::Status::Pressed => (
                    Color { a: 0.24, ..self.accent_red },
                    Color { a: 0.52, ..self.accent_red },
                ),
                _ => (self.bg_raised, self.hairline_strong),
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color: self.accent_red,
                border: Border {
                    radius: 7.0.into(),
                    color: border_color,
                    width: 1.0,
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn danger_button(self) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let bg = match status {
                button::Status::Hovered => Color {
                    r: (self.accent_red.r * 1.08).min(1.0),
                    g: (self.accent_red.g * 1.08).min(1.0),
                    b: (self.accent_red.b * 1.08).min(1.0),
                    a: 1.0,
                },
                button::Status::Pressed => Color {
                    r: self.accent_red.r * 0.84,
                    g: self.accent_red.g * 0.84,
                    b: self.accent_red.b * 0.84,
                    a: 1.0,
                },
                _ => self.accent_red,
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color: Color::WHITE,
                border: Border {
                    radius: 8.0.into(),
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn sidebar_instance(self, active: bool) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let (bg, border_color, text_color) = if active {
                (
                    if self.is_dark {
                        Color::from_rgba(1.0, 1.0, 1.0, 0.025)
                    } else {
                        Color::from_rgba(0.06, 0.08, 0.14, 0.08)
                    },
                    self.hairline_strong,
                    self.text_primary,
                )
            } else {
                match status {
                    button::Status::Hovered => (
                        if self.is_dark {
                            Color::from_rgba(1.0, 1.0, 1.0, 0.014)
                        } else {
                            Color::from_rgba(0.06, 0.08, 0.14, 0.045)
                        },
                        Color::TRANSPARENT,
                        self.text_primary,
                    ),
                    button::Status::Pressed => (
                        if self.is_dark {
                            Color::from_rgba(1.0, 1.0, 1.0, 0.022)
                        } else {
                            Color::from_rgba(0.06, 0.08, 0.14, 0.07)
                        },
                        self.hairline,
                        self.text_primary,
                    ),
                    _ => (Color::TRANSPARENT, Color::TRANSPARENT, self.text_secondary),
                }
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color,
                border: Border {
                    radius: 8.0.into(),
                    color: border_color,
                    width: if active { 1.0 } else { 0.0 },
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn segmented_button(self, active: bool) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let (bg, border_color, text_color) = if active {
                (
                    if self.is_dark {
                        self.bg_hover
                    } else {
                        self.bg_card
                    },
                    self.hairline,
                    self.text_primary,
                )
            } else {
                match status {
                    button::Status::Hovered => (
                        Color {
                            a: if self.is_dark { 0.015 } else { 0.045 },
                            ..self.text_primary
                        },
                        Color::TRANSPARENT,
                        self.text_primary,
                    ),
                    _ => (Color::TRANSPARENT, Color::TRANSPARENT, self.text_muted),
                }
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color,
                border: Border {
                    radius: 6.0.into(),
                    color: border_color,
                    width: if active { 1.0 } else { 0.0 },
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn window_control(self, is_close: bool) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let (bg, text_color) = match status {
                button::Status::Hovered => {
                    if is_close {
                        (self.accent_red, Color::WHITE)
                    } else {
                        (
                            Color {
                                a: if self.is_dark { 0.026 } else { 0.07 },
                                ..self.text_primary
                            },
                            self.text_primary,
                        )
                    }
                }
                button::Status::Pressed => {
                    if is_close {
                        (Color::from_rgb(0.75, 0.22, 0.24), Color::WHITE)
                    } else {
                        (
                            Color {
                                a: if self.is_dark { 0.042 } else { 0.11 },
                                ..self.text_primary
                            },
                            self.text_primary,
                        )
                    }
                }
                _ => (Color::TRANSPARENT, self.text_muted),
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color,
                border: Border {
                    radius: 6.0.into(),
                    ..Default::default()
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn toggle_switch_style(
        self,
        enabled: bool,
        accent_green: bool,
    ) -> impl Fn(&Theme, button::Status) -> button::Style {
        move |_, status| {
            let hovered = matches!(status, button::Status::Hovered | button::Status::Pressed);
            let (bg, border_color, border_w) = if enabled {
                if accent_green {
                    let fill = Color {
                        a: if hovered { 1.0 } else { 0.92 },
                        ..self.accent_green
                    };
                    (fill, Color::TRANSPARENT, 0.0)
                } else if self.is_dark {
                    (
                        Color::from_rgba(1.0, 1.0, 1.0, if hovered { 0.16 } else { 0.11 }),
                        Color::TRANSPARENT,
                        0.0,
                    )
                } else {
                    (
                        Color::from_rgba(0.08, 0.10, 0.15, if hovered { 0.88 } else { 0.78 }),
                        Color::TRANSPARENT,
                        0.0,
                    )
                }
            } else {
                (
                    if hovered { self.bg_hover } else { self.bg_inset },
                    self.hairline_strong,
                    1.0,
                )
            };
            button::Style {
                background: Some(Background::Color(bg)),
                text_color: self.text_primary,
                border: Border {
                    radius: 10.0.into(),
                    color: border_color,
                    width: border_w,
                },
                snap: true,
                ..Default::default()
            }
        }
    }

    pub fn text_input_style(self) -> impl Fn(&Theme, text_input::Status) -> text_input::Style {
        move |_, status| {
            let border_color = match status {
                text_input::Status::Focused { .. } => Color {
                    a: if self.is_dark { 0.11 } else { 0.34 },
                    ..self.text_primary
                },
                text_input::Status::Hovered => Color {
                    a: if self.is_dark { 0.065 } else { 0.22 },
                    ..self.text_primary
                },
                _ => self.hairline_strong,
            };
            text_input::Style {
                background: Background::Color(self.bg_inset),
                border: Border {
                    color: border_color,
                    width: 1.0,
                    radius: 7.0.into(),
                },
                icon: self.text_muted,
                placeholder: self.text_dim,
                value: self.text_primary,
                selection: Color { a: 0.22, ..self.text_primary },
            }
        }
    }

    pub fn bare_text_input_style(self) -> impl Fn(&Theme, text_input::Status) -> text_input::Style {
        move |_, _| text_input::Style {
            background: Background::Color(Color::TRANSPARENT),
            border: Border::default(),
            icon: self.text_muted,
            placeholder: self.text_dim,
            value: self.text_primary,
            selection: Color { a: 0.22, ..self.text_primary },
        }
    }

    pub fn pick_list_style(self) -> impl Fn(&Theme, pick_list::Status) -> pick_list::Style {
        move |_, status| {
            let (border_color, bg) = match status {
                pick_list::Status::Hovered => (
                    Color { a: 0.22, ..self.text_primary },
                    self.bg_hover,
                ),
                pick_list::Status::Opened { .. } => (
                    Color { a: 0.28, ..self.text_primary },
                    self.bg_hover,
                ),
                _ => (self.hairline_strong, self.bg_raised),
            };
            pick_list::Style {
                text_color: self.text_primary,
                placeholder_color: self.text_dim,
                handle_color: self.text_muted,
                background: Background::Color(bg),
                border: Border {
                    color: border_color,
                    width: 1.0,
                    radius: 7.0.into(),
                },
            }
        }
    }

    pub fn custom_menu_style(self) -> impl Fn(&Theme) -> overlay::menu::Style {
        move |_| overlay::menu::Style {
            text_color: self.text_primary,
            background: Background::Color(self.bg_modal),
            border: Border {
                color: self.hairline_strong,
                width: 1.0,
                radius: 8.0.into(),
            },
            selected_text_color: self.text_primary,
            selected_background: Background::Color(self.bg_hover),
            shadow: Shadow {
                color: Color::from_rgba(0.0, 0.0, 0.0, if self.is_dark { 0.50 } else { 0.16 }),
                offset: Vector::new(0.0, 6.0),
                blur_radius: 20.0,
            },
        }
    }

    pub fn clean_slider_style(self) -> impl Fn(&Theme, slider::Status) -> slider::Style {
        move |_, status| {
            let handle_color = match status {
                slider::Status::Hovered | slider::Status::Dragged => {
                    if self.is_dark { Color::WHITE } else { Color::BLACK }
                }
                slider::Status::Active => self.text_primary,
            };
            slider::Style {
                rail: slider::Rail {
                    backgrounds: (
                        Background::Color(self.text_primary),
                        Background::Color(self.hairline_strong),
                    ),
                    width: 4.0,
                    border: Border {
                        radius: 2.0.into(),
                        ..Default::default()
                    },
                },
                handle: slider::Handle {
                    shape: slider::HandleShape::Circle { radius: 7.0 },
                    background: Background::Color(handle_color),
                    border_width: 0.0,
                    border_color: Color::TRANSPARENT,
                },
            }
        }
    }

    pub fn custom_scrollbar_style(
        self,
    ) -> impl Fn(&Theme, iced::widget::scrollable::Status) -> iced::widget::scrollable::Style {
        move |_, _| iced::widget::scrollable::Style {
            container: container::Style::default(),
            vertical_rail: Rail {
                background: None,
                border: Border::default(),
                scroller: Scroller {
                    background: Background::Color(Color {
                        a: if self.is_dark { 0.055 } else { 0.22 },
                        ..self.text_primary
                    }),
                    border: Border {
                        radius: 3.0.into(),
                        ..Default::default()
                    },
                },
            },
            horizontal_rail: Rail {
                background: None,
                border: Border::default(),
                scroller: Scroller {
                    background: Background::Color(Color {
                        a: if self.is_dark { 0.055 } else { 0.22 },
                        ..self.text_primary
                    }),
                    border: Border {
                        radius: 3.0.into(),
                        ..Default::default()
                    },
                },
            },
            gap: None,
            auto_scroll: AutoScroll {
                background: Background::Color(self.bg_modal),
                border: Border {
                    radius: 4.0.into(),
                    ..Default::default()
                },
                shadow: Shadow::default(),
                icon: self.text_muted,
            },
        }
    }
}
