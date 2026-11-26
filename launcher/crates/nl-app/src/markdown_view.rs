use crate::icons::{SVG_CHEVRON_DOWN, SVG_CHEVRON_RIGHT, SVG_EXTERNAL, icon_svg};
use crate::theme::{FONT_MONO, FONT_SANS, FONT_SANS_MEDIUM, Palette, TextExt};
use iced::alignment::{Horizontal, Vertical};
use iced::font::{Family, Stretch, Style, Weight};
use iced::widget::{Button, Column, Row, Space, column, container, image, rich_text, row, span, text};
use iced::{Alignment, Background, Border, Color, Element, Font, Length, Padding};
use std::collections::{HashMap, HashSet};

const FONT_SANS_ITALIC: Font = Font {
    family: Family::Name("Geist"),
    weight: Weight::Normal,
    stretch: Stretch::Normal,
    style: Style::Italic,
};

const FONT_SANS_BOLD_ITALIC: Font = Font {
    family: Family::Name("Geist"),
    weight: Weight::Medium,
    stretch: Stretch::Normal,
    style: Style::Italic,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CalloutKind {
    Note,
    Tip,
    Important,
    Warning,
    Caution,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InlineSpan {
    pub text: String,
    pub bold: bool,
    pub italic: bool,
    pub code: bool,
    pub strike: bool,
    pub link: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ListItem {
    pub checked: Option<bool>,
    pub indent: u8,
    pub spans: Vec<InlineSpan>,
}

#[derive(Debug, Clone)]
pub struct RemoteImage {
    pub handle: image::Handle,
    pub width: u32,
    pub height: u32,
}

pub fn fit_image_size(src_w: u32, src_h: u32, max_w: f32, max_h: f32) -> (f32, f32) {
    if src_w == 0 || src_h == 0 || max_w <= 0.0 || max_h <= 0.0 {
        return (max_w.max(1.0), max_h.max(1.0));
    }
    let sw = src_w as f32;
    let sh = src_h as f32;
    let scale = (max_w / sw).min(max_h / sh).min(1.0);
    ((sw * scale).round().max(1.0), (sh * scale).round().max(1.0))
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InlineMediaItem {
    pub url: String,
    pub alt: String,
    pub link: Option<String>,
    pub width_attr: Option<u32>,
    pub height_attr: Option<u32>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MdBlock {
    Heading {
        level: u8,
        spans: Vec<InlineSpan>,
    },
    Paragraph {
        spans: Vec<InlineSpan>,
        centered: bool,
    },
    CodeBlock {
        lang: Option<String>,
        code: String,
    },
    Blockquote {
        callout: Option<CalloutKind>,
        blocks: Vec<MdBlock>,
    },
    List {
        ordered: bool,
        items: Vec<ListItem>,
    },
    Table {
        headers: Vec<Vec<InlineSpan>>,
        rows: Vec<Vec<Vec<InlineSpan>>>,
    },
    Details {
        id: usize,
        summary: String,
        blocks: Vec<MdBlock>,
    },
    MediaRow {
        items: Vec<InlineMediaItem>,
        centered: bool,
    },
    BadgeRow {
        items: Vec<(String, String)>,
        centered: bool,
    },
    Divider,
}

pub fn parse_modrinth_markdown(raw: &str) -> Vec<MdBlock> {
    let stripped = strip_comments_and_ignored_blocks(raw);
    let mut details_counter = 0usize;
    parse_blocks(&stripped, &mut details_counter)
}

pub fn extract_image_urls(blocks: &[MdBlock]) -> Vec<String> {
    let mut out = Vec::new();
    let mut seen = HashSet::new();
    collect_images_rec(blocks, &mut seen, &mut out);
    out
}

fn collect_images_rec(blocks: &[MdBlock], seen: &mut HashSet<String>, out: &mut Vec<String>) {
    for b in blocks {
        match b {
            MdBlock::MediaRow { items, .. } => {
                for item in items {
                    if !is_svg_badge_url(&item.url)
                        && !is_non_raster_url(&item.url)
                        && seen.insert(item.url.clone())
                    {
                        out.push(item.url.clone());
                    }
                }
            }
            MdBlock::Blockquote { blocks, .. } | MdBlock::Details { blocks, .. } => {
                collect_images_rec(blocks, seen, out);
            }
            _ => {}
        }
    }
}

fn strip_comments_and_ignored_blocks(input: &str) -> String {
    let mut s = input.replace("\r\n", "\n").replace('\r', "\n");
    while let Some(start) = s.find("<!--") {
        if let Some(rel_end) = s[start + 4..].find("-->") {
            s.replace_range(start..start + 4 + rel_end + 3, "");
        } else {
            s.truncate(start);
            break;
        }
    }
    for tag in ["style", "script"] {
        let open = format!("<{tag}");
        let close = format!("</{tag}>");
        loop {
            let lower = s.to_ascii_lowercase();
            let Some(start) = lower.find(&open) else {
                break;
            };
            if let Some(rel_end) = lower[start..].find(&close) {
                s.replace_range(start..start + rel_end + close.len(), "");
            } else {
                s.truncate(start);
                break;
            }
        }
    }
    s
}

fn parse_blocks(input: &str, details_counter: &mut usize) -> Vec<MdBlock> {
    let mut blocks = Vec::new();
    let mut cursor = 0usize;

    while cursor < input.len() {
        let rest = &input[cursor..];
        let lower = rest.to_ascii_lowercase();

        let next_details = find_html_block(&lower, rest, "details");
        let next_table = find_html_block(&lower, rest, "table");
        let next_pre = find_html_block(&lower, rest, "pre");

        let earliest = [
            next_details.map(|r| (r.0, r.1, "details")),
            next_table.map(|r| (r.0, r.1, "table")),
            next_pre.map(|r| (r.0, r.1, "pre")),
        ]
        .into_iter()
        .flatten()
        .min_by_key(|(start, _, _)| *start);

        if let Some((start, end, kind)) = earliest {
            if start > 0 {
                parse_markdown_lines(&rest[..start], details_counter, &mut blocks);
            }
            let chunk = &rest[start..end];
            match kind {
                "details" => {
                    if let Some(det) = parse_html_details(chunk, details_counter) {
                        blocks.push(det);
                    }
                }
                "table" => {
                    if let Some(tbl) = parse_html_table(chunk) {
                        blocks.push(tbl);
                    }
                }
                "pre" => {
                    let inner = strip_outer_tag(chunk);
                    let code_inner = if inner.trim_start().to_ascii_lowercase().starts_with("<code") {
                        strip_outer_tag(inner.trim())
                    } else {
                        inner
                    };
                    let decoded = decode_html_entities(&strip_all_html_tags(code_inner));
                    if !decoded.trim().is_empty() {
                        blocks.push(MdBlock::CodeBlock {
                            lang: None,
                            code: decoded.trim_matches('\n').to_string(),
                        });
                    }
                }
                _ => {}
            }
            cursor += end;
        } else {
            parse_markdown_lines(rest, details_counter, &mut blocks);
            break;
        }
    }

    merge_adjacent_badge_rows(blocks)
}

fn find_html_block(lower: &str, original: &str, tag: &str) -> Option<(usize, usize)> {
    let open_pat = format!("<{tag}");
    let close_pat = format!("</{tag}>");
    let mut search_from = 0usize;
    while let Some(rel) = lower[search_from..].find(&open_pat) {
        let pos = search_from + rel;
        if original[..pos].matches("```").count() % 2 == 1 {
            search_from = pos + open_pat.len();
            continue;
        }
        let after_tag = lower.as_bytes().get(pos + open_pat.len()).copied();
        if !matches!(after_tag, Some(b'>' | b' ' | b'\t' | b'\n' | b'/')) {
            search_from = pos + open_pat.len();
            continue;
        }
        let mut depth = 0usize;
        let mut scan = pos;
        while scan < lower.len() {
            let next_open = lower[scan..].find(&open_pat).map(|i| scan + i);
            let next_close = lower[scan..].find(&close_pat).map(|i| scan + i);
            match (next_open, next_close) {
                (Some(o), Some(c)) if o < c => {
                    let b = lower.as_bytes().get(o + open_pat.len()).copied();
                    if matches!(b, Some(b'>' | b' ' | b'\t' | b'\n' | b'/')) {
                        depth += 1;
                    }
                    scan = o + open_pat.len();
                }
                (_, Some(c)) => {
                    depth = depth.saturating_sub(1);
                    let end = c + close_pat.len();
                    if depth == 0 {
                        return Some((pos, end));
                    }
                    scan = end;
                }
                _ => break,
            }
        }
        return None;
    }
    None
}

fn strip_outer_tag(chunk: &str) -> &str {
    let Some(open_end) = chunk.find('>') else {
        return chunk;
    };
    let Some(close_start) = chunk.rfind("</") else {
        return &chunk[open_end + 1..];
    };
    if close_start > open_end {
        &chunk[open_end + 1..close_start]
    } else {
        ""
    }
}

fn parse_html_details(chunk: &str, details_counter: &mut usize) -> Option<MdBlock> {
    let inner = strip_outer_tag(chunk);
    let lower = inner.to_ascii_lowercase();
    let (summary, body) = if let Some((s_start, s_end)) = find_html_block(&lower, inner, "summary") {
        let sum_raw = strip_outer_tag(&inner[s_start..s_end]);
        let sum_clean = decode_html_entities(&strip_all_html_tags(sum_raw))
            .trim()
            .to_string();
        let rest = format!("{}\n{}", &inner[..s_start], &inner[s_end..]);
        (sum_clean, rest)
    } else {
        ("Details".to_string(), inner.to_string())
    };
    let id = *details_counter;
    *details_counter += 1;
    let blocks = parse_blocks(&body, details_counter);
    if summary.is_empty() && blocks.is_empty() {
        return None;
    }
    Some(MdBlock::Details {
        id,
        summary: if summary.is_empty() {
            "Details".to_string()
        } else {
            summary
        },
        blocks,
    })
}

fn parse_html_table(chunk: &str) -> Option<MdBlock> {
    let mut headers: Vec<Vec<InlineSpan>> = Vec::new();
    let mut rows: Vec<Vec<Vec<InlineSpan>>> = Vec::new();
    let mut rest = chunk;

    loop {
        let lower = rest.to_ascii_lowercase();
        let Some((tr_start, tr_end)) = find_html_block(&lower, rest, "tr") else {
            break;
        };
        let tr_inner = strip_outer_tag(&rest[tr_start..tr_end]);
        let mut cells = Vec::new();
        let mut cell_rest = tr_inner;
        loop {
            let clower = cell_rest.to_ascii_lowercase();
            let th = find_html_block(&clower, cell_rest, "th");
            let td = find_html_block(&clower, cell_rest, "td");
            let next = [th, td].into_iter().flatten().min_by_key(|(s, _)| *s);
            let Some((cs, ce)) = next else {
                break;
            };
            let cell_inner = strip_outer_tag(&cell_rest[cs..ce]).trim();
            cells.push(parse_inline_spans(cell_inner));
            cell_rest = &cell_rest[ce..];
        }
        if !cells.is_empty() {
            if headers.is_empty() {
                headers = cells;
            } else {
                rows.push(cells);
            }
        }
        rest = &rest[tr_end..];
    }

    if headers.is_empty() && rows.is_empty() {
        return None;
    }
    Some(MdBlock::Table { headers, rows })
}

fn normalize_inline_block_html(input: &str) -> String {
    let mut out = String::with_capacity(input.len() + 64);
    let mut chars = input.chars().peekable();
    let mut in_fence = false;
    let mut at_line_start = true;

    while let Some(c) = chars.next() {
        if at_line_start && c == '`' {
            let mut ticks = String::from("`");
            while chars.peek() == Some(&'`') {
                ticks.push(chars.next().unwrap());
            }
            if ticks.len() >= 3 {
                in_fence = !in_fence;
            }
            out.push_str(&ticks);
            at_line_start = false;
            continue;
        }

        if c == '\n' {
            out.push('\n');
            at_line_start = true;
            continue;
        }
        if !c.is_whitespace() {
            at_line_start = false;
        }

        if !in_fence && c == '<' {
            let mut raw_tag = String::new();
            let mut closed = false;
            let mut cloned = chars.clone();
            for ch in cloned.by_ref() {
                if ch == '>' {
                    closed = true;
                    break;
                }
                if ch == '<' || raw_tag.len() > 600 {
                    break;
                }
                raw_tag.push(ch);
            }
            let trimmed = raw_tag.trim();
            let looks_like_tag = closed
                && !trimmed.is_empty()
                && trimmed
                    .chars()
                    .next()
                    .is_some_and(|first| first.is_ascii_alphabetic() || first == '/');

            if looks_like_tag {
                for ch in chars.by_ref() {
                    if ch == '>' {
                        break;
                    }
                }
                let lower = trimmed.to_ascii_lowercase();
                let tag_name = lower
                    .trim_start_matches('/')
                    .split(|ch: char| ch.is_whitespace() || ch == '/')
                    .next()
                    .unwrap_or("");
                let is_closing = lower.starts_with('/');

                match tag_name {
                    "br" => out.push('\n'),
                    "hr" => out.push_str("\n---\n"),
                    "h1" if !is_closing => out.push_str("\n# "),
                    "h2" if !is_closing => out.push_str("\n## "),
                    "h3" if !is_closing => out.push_str("\n### "),
                    "h4" | "h5" | "h6" if !is_closing => out.push_str("\n#### "),
                    "h1" | "h2" | "h3" | "h4" | "h5" | "h6" => out.push_str("\n\n"),
                    "center" => {
                        if !is_closing {
                            out.push_str("\n<<CENTER_ON>>\n");
                        } else {
                            out.push_str("\n<<CENTER_OFF>>\n");
                        }
                    }
                    "p" | "div" | "section" | "article" | "header" | "footer" => {
                        if !is_closing
                            && extract_html_attr(trimmed, "align")
                                .is_some_and(|a| a.eq_ignore_ascii_case("center"))
                        {
                            out.push_str("\n<<CENTER_ON>>\n");
                        } else if is_closing {
                            out.push_str("\n<<BLOCK_CLOSE>>\n");
                        } else {
                            out.push_str("\n\n");
                        }
                    }
                    "ul" | "ol" => out.push('\n'),
                    "li" if !is_closing => out.push_str("\n- "),
                    "li" => out.push('\n'),
                    "blockquote" if !is_closing => out.push_str("\n> "),
                    "blockquote" => out.push_str("\n\n"),
                    "img" if is_closing => {}
                    "a" | "img" | "b" | "strong" | "i" | "em" | "code" | "s" | "del" | "strike"
                    | "kbd" | "sub" | "sup" | "mark" => {
                        out.push('<');
                        out.push_str(trimmed);
                        out.push('>');
                    }
                    _ => {}
                }
                continue;
            }
        }

        out.push(c);
    }

    out
}

fn parse_markdown_lines(input: &str, details_counter: &mut usize, out: &mut Vec<MdBlock>) {
    let normalized = normalize_inline_block_html(input);
    let lines: Vec<&str> = normalized.lines().collect();
    let mut i = 0usize;
    let mut center_depth = 0usize;

    while i < lines.len() {
        let raw_line = lines[i];
        let trimmed = raw_line.trim();

        if trimmed.is_empty() {
            i += 1;
            continue;
        }
        if trimmed == "<<CENTER_ON>>" {
            center_depth += 1;
            i += 1;
            continue;
        }
        if trimmed == "<<CENTER_OFF>>" || trimmed == "<<BLOCK_CLOSE>>" {
            center_depth = center_depth.saturating_sub(1);
            i += 1;
            continue;
        }

        if trimmed.starts_with("```") || trimmed.starts_with("~~~") {
            let fence = if trimmed.starts_with("```") { "```" } else { "~~~" };
            let lang_str = trimmed.trim_start_matches(fence).trim();
            let lang = (!lang_str.is_empty()).then(|| lang_str.to_string());
            let mut code_lines = Vec::new();
            i += 1;
            while i < lines.len() {
                if lines[i].trim().starts_with(fence) {
                    i += 1;
                    break;
                }
                code_lines.push(lines[i]);
                i += 1;
            }
            out.push(MdBlock::CodeBlock {
                lang,
                code: code_lines.join("\n"),
            });
            continue;
        }

        let centered = center_depth > 0;
        let line_content = trimmed;

        if is_horizontal_rule(line_content) {
            out.push(MdBlock::Divider);
            i += 1;
            continue;
        }

        if let Some((level, heading_text)) = parse_atx_heading(line_content) {
            let spans = parse_inline_spans(heading_text);
            if !spans.is_empty() {
                out.push(MdBlock::Heading { level, spans });
            }
            i += 1;
            continue;
        }

        if let Some(mut raw_items) = parse_media_only_items(line_content) {
            i += 1;
            while i < lines.len() {
                let nt = lines[i].trim();
                if nt.is_empty()
                    || nt == "<<CENTER_ON>>"
                    || nt == "<<CENTER_OFF>>"
                    || nt == "<<BLOCK_CLOSE>>"
                {
                    break;
                }
                if let Some(more) = parse_media_only_items(nt) {
                    raw_items.extend(more);
                    i += 1;
                } else {
                    break;
                }
            }
            push_media_items(raw_items, centered, out);
            continue;
        }

        if i + 1 < lines.len()
            && line_content.contains('|')
            && is_gfm_table_separator(lines[i + 1].trim())
        {
            let headers = split_gfm_table_row(line_content)
                .into_iter()
                .map(|c| parse_inline_spans(&c))
                .collect::<Vec<_>>();
            i += 2;
            let mut rows = Vec::new();
            while i < lines.len() {
                let r = lines[i].trim();
                if r.is_empty() || !r.contains('|') {
                    break;
                }
                rows.push(
                    split_gfm_table_row(r)
                        .into_iter()
                        .map(|c| parse_inline_spans(&c))
                        .collect(),
                );
                i += 1;
            }
            out.push(MdBlock::Table { headers, rows });
            continue;
        }

        if line_content.starts_with('>') {
            let mut quote_lines = Vec::new();
            while i < lines.len() {
                let lt = lines[i].trim();
                if let Some(rest) = lt.strip_prefix('>') {
                    quote_lines.push(rest.strip_prefix(' ').unwrap_or(rest));
                    i += 1;
                } else {
                    break;
                }
            }
            let mut callout = None;
            if let Some(first) = quote_lines.first().copied() {
                let f = first.trim();
                let kind = match f.to_ascii_uppercase().as_str() {
                    "[!NOTE]" => Some(CalloutKind::Note),
                    "[!TIP]" => Some(CalloutKind::Tip),
                    "[!IMPORTANT]" => Some(CalloutKind::Important),
                    "[!WARNING]" => Some(CalloutKind::Warning),
                    "[!CAUTION]" => Some(CalloutKind::Caution),
                    _ => None,
                };
                if kind.is_some() {
                    callout = kind;
                    quote_lines.remove(0);
                }
            }
            let inner = quote_lines.join("\n");
            let inner_blocks = parse_blocks(&inner, details_counter);
            if !inner_blocks.is_empty() {
                out.push(MdBlock::Blockquote {
                    callout,
                    blocks: inner_blocks,
                });
            }
            continue;
        }

        if let Some((ordered, first_item)) = parse_list_line(raw_line) {
            let mut items = vec![first_item];
            i += 1;
            while i < lines.len() {
                if let Some((same_ord, item)) = parse_list_line(lines[i])
                    && same_ord == ordered
                {
                    items.push(item);
                    i += 1;
                } else {
                    break;
                }
            }
            out.push(MdBlock::List { ordered, items });
            continue;
        }

        if i + 1 < lines.len() {
            let next_t = lines[i + 1].trim();
            if next_t.len() >= 3 && next_t.chars().all(|c| c == '=') {
                out.push(MdBlock::Heading {
                    level: 1,
                    spans: parse_inline_spans(line_content),
                });
                i += 2;
                continue;
            }
            if next_t.len() >= 3 && next_t.chars().all(|c| c == '-') {
                out.push(MdBlock::Heading {
                    level: 2,
                    spans: parse_inline_spans(line_content),
                });
                i += 2;
                continue;
            }
        }

        let mut para = String::from(line_content);
        i += 1;
        while i < lines.len() {
            let nt = lines[i].trim();
            if nt.is_empty()
                || nt == "<<CENTER_ON>>"
                || nt == "<<CENTER_OFF>>"
                || nt == "<<BLOCK_CLOSE>>"
                || nt.starts_with('#')
                || nt.starts_with("```")
                || nt.starts_with("~~~")
                || nt.starts_with('>')
                || is_horizontal_rule(nt)
                || parse_list_line(lines[i]).is_some()
                || parse_media_only_items(nt).is_some()
            {
                break;
            }
            if i + 1 < lines.len() && nt.contains('|') && is_gfm_table_separator(lines[i + 1].trim())
            {
                break;
            }
            para.push(' ');
            para.push_str(nt);
            i += 1;
        }

        let spans = parse_inline_spans(&para);
        if !spans.is_empty() {
            out.push(MdBlock::Paragraph { spans, centered });
        }
    }
}

fn merge_adjacent_badge_rows(blocks: Vec<MdBlock>) -> Vec<MdBlock> {
    let mut merged: Vec<MdBlock> = Vec::with_capacity(blocks.len());
    for mut b in blocks {
        if let MdBlock::BadgeRow {
            ref mut items,
            centered,
        } = b
            && let Some(MdBlock::BadgeRow {
                items: prev,
                centered: prev_c,
            }) = merged.last_mut()
            && *prev_c == centered
        {
            prev.append(items);
            continue;
        }
        merged.push(b);
    }
    merged
}

fn is_horizontal_rule(s: &str) -> bool {
    let clean: String = s.chars().filter(|c| !c.is_whitespace()).collect();
    clean.len() >= 3
        && (clean.chars().all(|c| c == '-')
            || clean.chars().all(|c| c == '*')
            || clean.chars().all(|c| c == '_'))
}

fn parse_atx_heading(s: &str) -> Option<(u8, &str)> {
    let hashes = s.bytes().take_while(|&b| b == b'#').count();
    if !(1..=6).contains(&hashes) {
        return None;
    }
    let rest = s[hashes..].strip_prefix(' ')?;
    let cleaned = rest.trim().trim_end_matches('#').trim();
    Some((hashes as u8, cleaned))
}

fn is_gfm_table_separator(line: &str) -> bool {
    if !line.contains('-') || !line.contains('|') {
        return false;
    }
    let cells = split_gfm_table_row(line);
    !cells.is_empty()
        && cells.iter().all(|c| {
            let t = c.trim();
            !t.is_empty() && t.chars().all(|ch| matches!(ch, '-' | ':'))
        })
}

fn split_gfm_table_row(line: &str) -> Vec<String> {
    let trimmed = line.trim().trim_start_matches('|').trim_end_matches('|');
    trimmed
        .split('|')
        .map(|s| s.trim().to_string())
        .collect()
}

fn parse_list_line(raw: &str) -> Option<(bool, ListItem)> {
    let leading_spaces = raw.bytes().take_while(|&b| b == b' ').count();
    let indent = (leading_spaces / 2).min(4) as u8;
    let t = raw.trim_start();

    let (ordered, body) = if let Some(rest) = t
        .strip_prefix("- ")
        .or_else(|| t.strip_prefix("* "))
        .or_else(|| t.strip_prefix("+ "))
    {
        (false, rest)
    } else {
        let digits = t.bytes().take_while(|b| b.is_ascii_digit()).count();
        if digits > 0
            && let Some(after) = t[digits..].strip_prefix(". ").or_else(|| t[digits..].strip_prefix(") "))
        {
            (true, after)
        } else {
            return None;
        }
    };

    let (checked, text_part) = if let Some(rest) = body.strip_prefix("[x] ").or_else(|| body.strip_prefix("[X] ")) {
        (Some(true), rest)
    } else if let Some(rest) = body.strip_prefix("[ ] ") {
        (Some(false), rest)
    } else {
        (None, body)
    };

    let spans = parse_inline_spans(text_part);
    if spans.is_empty() {
        return None;
    }
    Some((
        ordered,
        ListItem {
            checked,
            indent,
            spans,
        },
    ))
}

fn parse_media_only_items(line: &str) -> Option<Vec<InlineMediaItem>> {
    let mut rest = line.trim();
    if rest.is_empty() {
        return None;
    }
    let mut items: Vec<InlineMediaItem> = Vec::new();

    while !rest.is_empty() {
        rest = skip_media_separators(rest);
        if rest.is_empty() {
            break;
        }
        if rest.starts_with("[![")
            && let Some((alt, img_url, href, consumed)) = parse_md_linked_image(rest)
        {
            items.push(InlineMediaItem {
                url: img_url,
                alt: decode_html_entities(&alt).trim().to_string(),
                link: Some(href),
                width_attr: None,
                height_attr: None,
            });
            rest = &rest[consumed..];
            continue;
        }
        if rest.starts_with("![")
            && let Some((alt, img_url, consumed)) = parse_md_image(rest)
        {
            items.push(InlineMediaItem {
                url: img_url,
                alt: decode_html_entities(&alt).trim().to_string(),
                link: None,
                width_attr: None,
                height_attr: None,
            });
            rest = &rest[consumed..];
            continue;
        }
        if rest.to_ascii_lowercase().starts_with("<a ")
            && let Some((item, consumed)) = parse_html_linked_image(rest)
        {
            items.push(item);
            rest = &rest[consumed..];
            continue;
        }
        if rest.to_ascii_lowercase().starts_with("<img")
            && let Some((item, consumed)) = parse_html_img(rest)
        {
            items.push(item);
            rest = &rest[consumed..];
            continue;
        }
        return None;
    }

    if items.is_empty() { None } else { Some(items) }
}

fn skip_media_separators(mut s: &str) -> &str {
    loop {
        let trimmed = s.trim_start();
        if let Some(rest) = trimmed
            .strip_prefix("&nbsp;")
            .or_else(|| trimmed.strip_prefix("|"))
            .or_else(|| trimmed.strip_prefix('·'))
            .or_else(|| trimmed.strip_prefix('•'))
        {
            s = rest;
            continue;
        }
        return trimmed;
    }
}

fn push_media_items(items: Vec<InlineMediaItem>, centered: bool, out: &mut Vec<MdBlock>) {
    let mut raster_row: Vec<InlineMediaItem> = Vec::new();
    let mut svg_badges: Vec<(String, String)> = Vec::new();

    for item in items {
        if is_svg_badge_url(&item.url) || is_non_raster_url(&item.url) {
            if !raster_row.is_empty() {
                out.push(MdBlock::MediaRow {
                    items: std::mem::take(&mut raster_row),
                    centered,
                });
            }
            if let Some(href) = item.link {
                let label = badge_label(&item.alt, &href);
                if !label.is_empty() {
                    svg_badges.push((label, href));
                }
            }
        } else {
            if !svg_badges.is_empty() {
                out.push(MdBlock::BadgeRow {
                    items: std::mem::take(&mut svg_badges),
                    centered,
                });
            }
            raster_row.push(item);
        }
    }

    if !raster_row.is_empty() {
        out.push(MdBlock::MediaRow {
            items: raster_row,
            centered,
        });
    }
    if !svg_badges.is_empty() {
        out.push(MdBlock::BadgeRow {
            items: svg_badges,
            centered,
        });
    }
}

fn parse_md_image(s: &str) -> Option<(String, String, usize)> {
    let rest = s.strip_prefix("![")?;
    let close_b = rest.find(']')?;
    let alt = rest[..close_b].to_string();
    let after_b = rest[close_b + 1..].strip_prefix('(')?;
    let close_p = find_balanced_paren(after_b)?;
    let url = after_b[..close_p]
        .split_whitespace()
        .next()
        .unwrap_or("")
        .trim_matches(|c| c == '<' || c == '>')
        .to_string();
    let consumed = 2 + close_b + 1 + 1 + close_p + 1;
    Some((alt, url, consumed))
}

fn parse_md_linked_image(s: &str) -> Option<(String, String, String, usize)> {
    let inner = s.strip_prefix('[')?;
    let (alt, img_url, img_len) = parse_md_image(inner)?;
    let after_img = &inner[img_len..];
    let after_b = after_img.strip_prefix(']')?.strip_prefix('(')?;
    let close_p = find_balanced_paren(after_b)?;
    let href = after_b[..close_p]
        .split_whitespace()
        .next()
        .unwrap_or("")
        .to_string();
    let consumed = 1 + img_len + 2 + close_p + 1;
    Some((alt, img_url, href, consumed))
}

fn parse_px_dimension(raw: &str) -> Option<u32> {
    let trimmed = raw.trim().trim_end_matches("px").trim();
    if trimmed.ends_with('%') {
        return None;
    }
    trimmed
        .parse::<f32>()
        .ok()
        .filter(|&v| v > 0.0 && v <= 4096.0)
        .map(|v| v.round() as u32)
}

fn parse_style_px(style: &str, prop: &str) -> Option<u32> {
    for decl in style.split(';') {
        let Some((k, v)) = decl.split_once(':') else {
            continue;
        };
        if k.trim().eq_ignore_ascii_case(prop) {
            return parse_px_dimension(v);
        }
    }
    None
}

fn parse_html_img(s: &str) -> Option<(InlineMediaItem, usize)> {
    let end = s.find('>')?;
    let tag = &s[1..end];
    let src = extract_html_attr(tag, "src")?;
    let alt = extract_html_attr(tag, "alt")
        .map(|a| decode_html_entities(&a).trim().to_string())
        .unwrap_or_default();
    let style = extract_html_attr(tag, "style");
    let width_attr = extract_html_attr(tag, "width")
        .and_then(|v| parse_px_dimension(&v))
        .or_else(|| style.as_deref().and_then(|st| parse_style_px(st, "width")));
    let height_attr = extract_html_attr(tag, "height")
        .and_then(|v| parse_px_dimension(&v))
        .or_else(|| style.as_deref().and_then(|st| parse_style_px(st, "height")));
    Some((
        InlineMediaItem {
            url: src,
            alt,
            link: None,
            width_attr,
            height_attr,
        },
        end + 1,
    ))
}

fn parse_html_linked_image(s: &str) -> Option<(InlineMediaItem, usize)> {
    let open_end = s.find('>')?;
    let href = extract_html_attr(&s[1..open_end], "href")?;
    let lower = s.to_ascii_lowercase();
    let close_a = lower[open_end + 1..].find("</a>")?;
    let inner = s[open_end + 1..open_end + 1 + close_a].trim();
    if !inner.to_ascii_lowercase().starts_with("<img") {
        return None;
    }
    let (mut item, img_len) = parse_html_img(inner)?;
    if !inner[img_len..].trim().is_empty() {
        return None;
    }
    item.link = Some(href);
    Some((item, open_end + 1 + close_a + 4))
}

fn find_balanced_paren(s: &str) -> Option<usize> {
    let mut depth = 0usize;
    for (idx, ch) in s.char_indices() {
        match ch {
            '(' => depth += 1,
            ')' => {
                if depth == 0 {
                    return Some(idx);
                }
                depth -= 1;
            }
            _ => {}
        }
    }
    None
}

fn is_svg_badge_url(url: &str) -> bool {
    let l = url.to_ascii_lowercase();
    l.contains("shields.io")
        || l.contains("badgen.net")
        || l.contains("img.shields")
        || (l.contains("devins-badges") && (l.contains("/svg/") || l.ends_with(".svg")))
}

fn is_non_raster_url(url: &str) -> bool {
    let l = url.to_ascii_lowercase();
    l.ends_with(".svg") || l.contains(".svg?") || l.ends_with(".gif")
}

fn is_badge_hint(item: &InlineMediaItem) -> bool {
    let u = item.url.to_ascii_lowercase();
    if u.contains("badge")
        || u.contains("devins-badges")
        || u.contains("bstats.org")
        || u.contains("ko-fi.com/img")
    {
        return true;
    }
    if item.height_attr.is_some_and(|h| h <= 64) || item.width_attr.is_some_and(|w| w <= 280) {
        return true;
    }
    if item.link.is_none() {
        return false;
    }
    let alt = item.alt.to_ascii_lowercase();
    let badge_keywords = [
        "ko-fi",
        "kofi",
        "coffee",
        "donate",
        "patreon",
        "sponsor",
        "support",
        "discord",
        "available on",
        "requires",
        "supports",
        "fabric",
        "quilt",
        "neoforge",
        "forge",
        "modrinth",
        "curseforge",
        "github",
        "crowdin",
        "bisect",
        "wiki",
        "docs",
        "youtube",
        "bluesky",
        "twitter",
        "twitch",
    ];
    badge_keywords.iter().any(|k| alt.contains(k))
}

fn badge_label(alt: &str, href: &str) -> String {
    let clean = alt.trim();
    if !clean.is_empty() && clean.chars().count() <= 30 {
        return clean.to_string();
    }
    let l = href.to_ascii_lowercase();
    if l.contains("discord.") || l.contains("/discord") {
        "Discord".into()
    } else if l.contains("github.com") {
        "GitHub".into()
    } else if l.contains("modrinth.com") {
        "Modrinth".into()
    } else if l.contains("curseforge.com") {
        "CurseForge".into()
    } else if l.contains("ko-fi.com") || l.contains("/donate") {
        "Donate".into()
    } else if l.contains("patreon.com") {
        "Patreon".into()
    } else if l.contains("wiki") || l.contains("docs") {
        "Wiki".into()
    } else {
        "Link".into()
    }
}

pub fn parse_inline_spans(input: &str) -> Vec<InlineSpan> {
    let mut out = Vec::new();
    let style = InlineStyle::default();
    parse_inline_rec(input, style, &mut out);
    let mut merged: Vec<InlineSpan> = Vec::with_capacity(out.len());
    for sp in out {
        if sp.text.is_empty() {
            continue;
        }
        if let Some(prev) = merged.last_mut()
            && prev.bold == sp.bold
            && prev.italic == sp.italic
            && prev.code == sp.code
            && prev.strike == sp.strike
            && prev.link == sp.link
        {
            prev.text.push_str(&sp.text);
        } else {
            merged.push(sp);
        }
    }
    if let Some(first) = merged.first_mut() {
        let trimmed = first.text.trim_start().to_string();
        first.text = trimmed;
    }
    if let Some(last) = merged.last_mut() {
        let trimmed = last.text.trim_end().to_string();
        last.text = trimmed;
    }
    merged.retain(|s| !s.text.is_empty());
    merged
}

#[derive(Debug, Clone, Default)]
struct InlineStyle {
    bold: bool,
    italic: bool,
    code: bool,
    strike: bool,
    link: Option<String>,
}

fn push_text(out: &mut Vec<InlineSpan>, txt: &str, style: &InlineStyle) {
    let decoded = decode_html_entities(txt);
    if !decoded.is_empty() {
        out.push(InlineSpan {
            text: decoded,
            bold: style.bold,
            italic: style.italic,
            code: style.code,
            strike: style.strike,
            link: style.link.clone(),
        });
    }
}

fn parse_inline_rec(input: &str, style: InlineStyle, out: &mut Vec<InlineSpan>) {
    let bytes = input.as_bytes();
    let mut i = 0usize;
    let mut plain_start = 0usize;

    let flush_plain = |out: &mut Vec<InlineSpan>, from: usize, to: usize, st: &InlineStyle| {
        if to > from {
            push_text(out, &input[from..to], st);
        }
    };

    while i < bytes.len() {
        let rest = &input[i..];

        if bytes[i] == b'\\'
            && i + 1 < bytes.len()
            && matches!(
                bytes[i + 1],
                b'\\' | b'*' | b'_' | b'`' | b'[' | b']' | b'(' | b')' | b'~' | b'#' | b'<' | b'>'
            )
        {
            flush_plain(out, plain_start, i, &style);
            push_text(out, &input[i + 1..i + 2], &style);
            i += 2;
            plain_start = i;
            continue;
        }

        if bytes[i] == b'`' {
            let ticks = rest.bytes().take_while(|&b| b == b'`').count();
            let delim = &rest[..ticks];
            if let Some(rel_end) = rest[ticks..].find(delim) {
                flush_plain(out, plain_start, i, &style);
                let mut code_st = style.clone();
                code_st.code = true;
                let inner = &rest[ticks..ticks + rel_end];
                out.push(InlineSpan {
                    text: inner.to_string(),
                    bold: code_st.bold,
                    italic: code_st.italic,
                    code: true,
                    strike: code_st.strike,
                    link: code_st.link,
                });
                i += ticks + rel_end + ticks;
                plain_start = i;
                continue;
            }
        }

        if bytes[i] == b'<' {
            let lower_rest = rest.to_ascii_lowercase();
            if (lower_rest.starts_with("<https://") || lower_rest.starts_with("<http://"))
                && let Some(end_gt) = rest.find('>')
            {
                let url = rest[1..end_gt].to_string();
                flush_plain(out, plain_start, i, &style);
                let mut link_st = style.clone();
                link_st.link = Some(url.clone());
                push_text(out, &url, &link_st);
                i += end_gt + 1;
                plain_start = i;
                continue;
            }
            if lower_rest.starts_with("<a ")
                && let Some(open_end) = rest.find('>')
                && let Some(close_rel) = lower_rest[open_end + 1..].find("</a>")
            {
                flush_plain(out, plain_start, i, &style);
                let href = extract_html_attr(&rest[1..open_end], "href");
                let inner = &rest[open_end + 1..open_end + 1 + close_rel];
                let mut link_st = style.clone();
                if let Some(h) = href {
                    link_st.link = Some(h);
                }
                parse_inline_rec(inner, link_st, out);
                i += open_end + 1 + close_rel + 4;
                plain_start = i;
                continue;
            }
            if lower_rest.starts_with("<img")
                && let Some((item, consumed)) = parse_html_img(rest)
            {
                flush_plain(out, plain_start, i, &style);
                if !item.alt.is_empty() && style.link.is_some() {
                    push_text(out, &item.alt, &style);
                }
                i += consumed;
                plain_start = i;
                continue;
            }
            if let Some((consumed, inner, tag_kind)) = parse_paired_inline_html(rest) {
                flush_plain(out, plain_start, i, &style);
                let mut next_st = style.clone();
                match tag_kind {
                    "b" | "strong" => next_st.bold = true,
                    "i" | "em" => next_st.italic = true,
                    "code" | "kbd" => next_st.code = true,
                    "s" | "del" | "strike" => next_st.strike = true,
                    _ => {}
                }
                if next_st.code {
                    push_text(out, &strip_all_html_tags(inner), &next_st);
                } else {
                    parse_inline_rec(inner, next_st, out);
                }
                i += consumed;
                plain_start = i;
                continue;
            }
            if let Some(gt) = rest.find('>')
                && gt <= 80
                && rest[1..gt]
                    .trim()
                    .chars()
                    .next()
                    .is_some_and(|c| c == '/' || c.is_ascii_alphabetic())
            {
                flush_plain(out, plain_start, i, &style);
                i += gt + 1;
                plain_start = i;
                continue;
            }
        }

        if rest.starts_with("![")
            && let Some((alt, _url, consumed)) = parse_md_image(rest)
        {
            flush_plain(out, plain_start, i, &style);
            if !alt.trim().is_empty() && style.link.is_some() {
                push_text(out, alt.trim(), &style);
            }
            i += consumed;
            plain_start = i;
            continue;
        }

        if bytes[i] == b'['
            && let Some((label, url, consumed)) = parse_md_link(rest)
        {
            flush_plain(out, plain_start, i, &style);
            let mut link_st = style.clone();
            link_st.link = Some(url);
            parse_inline_rec(&label, link_st, out);
            i += consumed;
            plain_start = i;
            continue;
        }

        if rest.starts_with("***")
            && let Some(rel_end) = rest[3..].find("***")
            && rel_end > 0
        {
            flush_plain(out, plain_start, i, &style);
            let mut st = style.clone();
            st.bold = true;
            st.italic = true;
            parse_inline_rec(&rest[3..3 + rel_end], st, out);
            i += 3 + rel_end + 3;
            plain_start = i;
            continue;
        }

        if rest.starts_with("**") || (rest.starts_with("__") && is_word_boundary_before(input, i)) {
            let delim = &rest[..2];
            if let Some(rel_end) = rest[2..].find(delim)
                && rel_end > 0
                && !rest[2..2 + rel_end].starts_with(' ')
            {
                flush_plain(out, plain_start, i, &style);
                let mut st = style.clone();
                st.bold = true;
                parse_inline_rec(&rest[2..2 + rel_end], st, out);
                i += 2 + rel_end + 2;
                plain_start = i;
                continue;
            }
        }

        if rest.starts_with("~~")
            && let Some(rel_end) = rest[2..].find("~~")
            && rel_end > 0
        {
            flush_plain(out, plain_start, i, &style);
            let mut st = style.clone();
            st.strike = true;
            parse_inline_rec(&rest[2..2 + rel_end], st, out);
            i += 2 + rel_end + 2;
            plain_start = i;
            continue;
        }

        if (bytes[i] == b'*' || (bytes[i] == b'_' && is_word_boundary_before(input, i)))
            && i + 1 < bytes.len()
            && !bytes[i + 1].is_ascii_whitespace()
            && bytes[i + 1] != bytes[i]
        {
            let delim = bytes[i] as char;
            if let Some(rel_end) = find_closing_italic(&rest[1..], delim) {
                flush_plain(out, plain_start, i, &style);
                let mut st = style.clone();
                st.italic = true;
                parse_inline_rec(&rest[1..1 + rel_end], st, out);
                i += 1 + rel_end + 1;
                plain_start = i;
                continue;
            }
        }

        if style.link.is_none()
            && (rest.starts_with("https://") || rest.starts_with("http://"))
        {
            let url_len = rest
                .find(|c: char| c.is_whitespace() || matches!(c, '<' | '>' | '"' | '\''))
                .unwrap_or(rest.len());
            let mut url_str = &rest[..url_len];
            while let Some(last) = url_str.chars().last() {
                if matches!(last, '.' | ',' | ';' | ':' | '!' | '?' | ')') {
                    url_str = &url_str[..url_str.len() - last.len_utf8()];
                } else {
                    break;
                }
            }
            if url_str.len() > 10 {
                flush_plain(out, plain_start, i, &style);
                let mut link_st = style.clone();
                link_st.link = Some(url_str.to_string());
                push_text(out, url_str, &link_st);
                i += url_str.len();
                plain_start = i;
                continue;
            }
        }

        let ch_len = rest.chars().next().map(char::len_utf8).unwrap_or(1);
        i += ch_len;
    }

    flush_plain(out, plain_start, bytes.len(), &style);
}

fn is_word_boundary_before(s: &str, idx: usize) -> bool {
    if idx == 0 {
        return true;
    }
    s[..idx]
        .chars()
        .next_back()
        .is_none_or(|c| !c.is_alphanumeric())
}

fn find_closing_italic(rest: &str, delim: char) -> Option<usize> {
    let bytes = rest.as_bytes();
    for (idx, ch) in rest.char_indices() {
        if ch == delim {
            if idx == 0 {
                continue;
            }
            if bytes[idx - 1].is_ascii_whitespace() {
                continue;
            }
            if delim == '_'
                && rest[idx + 1..]
                    .chars()
                    .next()
                    .is_some_and(|n| n.is_alphanumeric())
            {
                continue;
            }
            return Some(idx);
        }
    }
    None
}

fn parse_md_link(s: &str) -> Option<(String, String, usize)> {
    let mut depth = 0usize;
    let mut close_bracket = None;
    for (idx, ch) in s.char_indices() {
        match ch {
            '[' => depth += 1,
            ']' => {
                depth = depth.saturating_sub(1);
                if depth == 0 {
                    close_bracket = Some(idx);
                    break;
                }
            }
            _ => {}
        }
    }
    let cb = close_bracket?;
    let after = s[cb + 1..].strip_prefix('(')?;
    let cp = find_balanced_paren(after)?;
    let label = s[1..cb].to_string();
    let url = after[..cp]
        .split_whitespace()
        .next()
        .unwrap_or("")
        .trim_matches(|c| c == '<' || c == '>')
        .to_string();
    if url.is_empty() {
        return None;
    }
    let consumed = cb + 1 + 1 + cp + 1;
    Some((label, url, consumed))
}

fn parse_paired_inline_html(s: &str) -> Option<(usize, &str, &'static str)> {
    let lower = s.to_ascii_lowercase();
    for tag in ["strong", "b", "em", "i", "code", "kbd", "del", "strike", "s"] {
        let open_prefix = format!("<{tag}");
        if lower.starts_with(&open_prefix) {
            let next_b = lower.as_bytes().get(open_prefix.len()).copied();
            if !matches!(next_b, Some(b'>' | b' ')) {
                continue;
            }
            let open_end = s.find('>')?;
            let close_pat = format!("</{tag}>");
            let close_rel = lower[open_end + 1..].find(&close_pat)?;
            let inner = &s[open_end + 1..open_end + 1 + close_rel];
            let consumed = open_end + 1 + close_rel + close_pat.len();
            return Some((consumed, inner, tag));
        }
    }
    None
}

fn extract_html_attr(tag: &str, attr: &str) -> Option<String> {
    let key = format!("{attr}=");
    let lower = tag.to_ascii_lowercase();
    let idx = lower.find(&key)?;
    let after_eq = tag[idx + key.len()..].trim_start();
    let quote = after_eq.chars().next()?;
    if quote == '"' || quote == '\'' {
        let content = &after_eq[1..];
        let end = content.find(quote)?;
        Some(decode_html_entities(&content[..end]))
    } else {
        let end = after_eq
            .find(|c: char| c.is_whitespace() || c == '>' || c == '/')
            .unwrap_or(after_eq.len());
        Some(decode_html_entities(&after_eq[..end]))
    }
}

fn strip_all_html_tags(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut in_tag = false;
    for ch in input.chars() {
        if ch == '<' {
            in_tag = true;
        } else if ch == '>' && in_tag {
            in_tag = false;
        } else if !in_tag {
            out.push(ch);
        }
    }
    out
}

pub fn decode_html_entities(s: &str) -> String {
    if !s.contains('&') {
        return s.to_string();
    }
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(amp) = rest.find('&') {
        out.push_str(&rest[..amp]);
        rest = &rest[amp..];
        if let Some(semi) = rest[1..].find(';')
            && semi <= 12
        {
            let entity = &rest[1..1 + semi];
            let decoded: Option<String> = match entity {
                "amp" => Some("&".into()),
                "lt" => Some("<".into()),
                "gt" => Some(">".into()),
                "quot" => Some("\"".into()),
                "apos" => Some("'".into()),
                "nbsp" => Some(" ".into()),
                "ndash" => Some("-".into()),
                "mdash" => Some("-".into()),
                "hellip" => Some("...".into()),
                "copy" => Some("(c)".into()),
                "bull" => Some("·".into()),
                "times" => Some("×".into()),
                "rarr" => Some("→".into()),
                "larr" => Some("←".into()),
                "trade" => Some("™".into()),
                "reg" => Some("®".into()),
                "lsquo" | "rsquo" => Some("'".into()),
                "ldquo" | "rdquo" => Some("\"".into()),
                e if e.starts_with("#x") || e.starts_with("#X") => {
                    u32::from_str_radix(&e[2..], 16)
                        .ok()
                        .and_then(char::from_u32)
                        .map(|c| c.to_string())
                }
                e if e.starts_with('#') => e[1..]
                    .parse::<u32>()
                    .ok()
                    .and_then(char::from_u32)
                    .map(|c| c.to_string()),
                _ => None,
            };
            if let Some(val) = decoded {
                out.push_str(&val);
                rest = &rest[1 + semi + 1..];
                continue;
            }
        }
        out.push('&');
        rest = &rest[1..];
    }
    out.push_str(rest);
    out
}

pub fn render_markdown<'a, M>(
    blocks: &'a [MdBlock],
    expanded_details: &'a HashSet<usize>,
    remote_images: &'a HashMap<String, RemoteImage>,
    content_max_width: f32,
    p: Palette,
    on_link: impl Fn(String) -> M + Copy + 'a,
    on_toggle_details: impl Fn(usize) -> M + Copy + 'a,
) -> Element<'a, M>
where
    M: Clone + 'a,
{
    let mut col = Column::new().spacing(10).width(Length::Fill);
    for block in blocks {
        col = col.push(render_block(
            block,
            expanded_details,
            remote_images,
            content_max_width,
            p,
            on_link,
            on_toggle_details,
        ));
    }
    col.into()
}

fn render_block<'a, M>(
    block: &'a MdBlock,
    expanded_details: &'a HashSet<usize>,
    remote_images: &'a HashMap<String, RemoteImage>,
    content_max_width: f32,
    p: Palette,
    on_link: impl Fn(String) -> M + Copy + 'a,
    on_toggle_details: impl Fn(usize) -> M + Copy + 'a,
) -> Element<'a, M>
where
    M: Clone + 'a,
{
    match block {
        MdBlock::Heading { level, spans } => {
            let size = match level {
                1 => 18.0,
                2 => 15.5,
                3 => 13.5,
                _ => 12.5,
            };
            let heading = render_rich_spans(spans, size, p.text_primary, true, p, on_link);
            if *level <= 2 {
                column![
                    heading,
                    container(Space::new().height(1))
                        .width(Length::Fill)
                        .height(1)
                        .style(move |_| container::Style {
                            background: Some(Background::Color(p.hairline_subtle)),
                            ..Default::default()
                        }),
                ]
                .spacing(5)
                .width(Length::Fill)
                .into()
            } else {
                heading
            }
        }
        MdBlock::Paragraph { spans, centered } => {
            let el = render_rich_spans(spans, 13.0, p.text_secondary, false, p, on_link);
            if *centered {
                container(el)
                    .width(Length::Fill)
                    .align_x(Horizontal::Center)
                    .into()
            } else {
                el
            }
        }
        MdBlock::CodeBlock { lang, code } => {
            let mut inner = Column::new().spacing(6).width(Length::Fill);
            if let Some(l) = lang {
                inner = inner.push(
                    text(l)
                        .size(10.5)
                        .mono_medium()
                        .nowrap()
                        .color(p.text_dim),
                );
            }
            inner = inner.push(
                text(code)
                    .size(12)
                    .mono()
                    .shaped()
                    .color(p.text_primary),
            );
            container(inner)
                .width(Length::Fill)
                .padding([10, 12])
                .style(p.inset_panel_style())
                .into()
        }
        MdBlock::Blockquote { callout, blocks } => {
            let (bar_color, label) = match callout {
                Some(CalloutKind::Note) => (Color::from_rgb(0.45, 0.65, 0.98), Some("Note")),
                Some(CalloutKind::Tip) => (p.accent_green, Some("Tip")),
                Some(CalloutKind::Important) => (Color::from_rgb(0.68, 0.54, 0.96), Some("Important")),
                Some(CalloutKind::Warning) => (p.accent_orange, Some("Warning")),
                Some(CalloutKind::Caution) => (p.accent_red, Some("Caution")),
                None => (p.hairline_strong, None),
            };
            let mut inner = Column::new().spacing(6).width(Length::Fill);
            if let Some(lbl) = label {
                inner = inner.push(text(lbl).size(12).medium().nowrap().color(bar_color));
            }
            for b in blocks {
                inner = inner.push(render_block(
                    b,
                    expanded_details,
                    remote_images,
                    (content_max_width - 28.0).max(240.0),
                    p,
                    on_link,
                    on_toggle_details,
                ));
            }
            container(
                row![
                    container(Space::new().width(3))
                        .width(3)
                        .height(Length::Fill)
                        .style(move |_| container::Style {
                            background: Some(Background::Color(bar_color)),
                            border: Border {
                                radius: 1.5.into(),
                                ..Default::default()
                            },
                            ..Default::default()
                        }),
                    container(inner).width(Length::Fill),
                ]
                .spacing(10)
                .width(Length::Fill),
            )
            .width(Length::Fill)
            .padding([8, 12])
            .style(p.inset_panel_style())
            .into()
        }
        MdBlock::List { ordered, items } => {
            let mut col = Column::new().spacing(4).width(Length::Fill);
            for (idx, item) in items.iter().enumerate() {
                let marker = if let Some(checked) = item.checked {
                    if checked { "[x]".to_string() } else { "[ ]".to_string() }
                } else if *ordered {
                    format!("{}.", idx + 1)
                } else {
                    "·".to_string()
                };
                let left_pad = (item.indent as f32) * 14.0;
                col = col.push(
                    container(
                        row![
                            text(marker)
                                .size(12.5)
                                .mono()
                                .nowrap()
                                .color(if item.checked == Some(true) {
                                    p.accent_green
                                } else {
                                    p.text_dim
                                }),
                            render_rich_spans(
                                &item.spans,
                                13.0,
                                p.text_secondary,
                                false,
                                p,
                                on_link,
                            ),
                        ]
                        .spacing(8)
                        .align_y(Alignment::Start)
                        .width(Length::Fill),
                    )
                    .padding(Padding::new(0.0).left(left_pad))
                    .width(Length::Fill),
                );
            }
            col.into()
        }
        MdBlock::Table { headers, rows } => {
            let mut tbl = Column::new().spacing(0).width(Length::Fill);
            if !headers.is_empty() {
                let mut hdr = Row::new().spacing(0).width(Length::Fill);
                for cell in headers {
                    hdr = hdr.push(
                        container(render_rich_spans(
                            cell,
                            12.0,
                            p.text_primary,
                            true,
                            p,
                            on_link,
                        ))
                        .width(Length::FillPortion(1))
                        .padding([7, 10]),
                    );
                }
                tbl = tbl.push(
                    container(hdr)
                        .width(Length::Fill)
                        .style(move |_| container::Style {
                            background: Some(Background::Color(p.bg_raised)),
                            ..Default::default()
                        }),
                );
            }
            for (r_idx, r) in rows.iter().enumerate() {
                tbl = tbl.push(
                    container(Space::new().height(1))
                        .width(Length::Fill)
                        .height(1)
                        .style(move |_| container::Style {
                            background: Some(Background::Color(p.hairline_subtle)),
                            ..Default::default()
                        }),
                );
                let mut row_el = Row::new().spacing(0).width(Length::Fill);
                let cols = headers.len().max(r.len()).max(1);
                for c_idx in 0..cols {
                    let cell_el: Element<'a, M> = if let Some(spans) = r.get(c_idx) {
                        render_rich_spans(spans, 12.0, p.text_secondary, false, p, on_link)
                    } else {
                        Space::new().into()
                    };
                    row_el = row_el.push(
                        container(cell_el)
                            .width(Length::FillPortion(1))
                            .padding([6, 10]),
                    );
                }
                let striped = r_idx % 2 == 1;
                tbl = tbl.push(
                    container(row_el)
                        .width(Length::Fill)
                        .style(move |_| container::Style {
                            background: striped.then_some(Background::Color(p.bg_row_hover)),
                            ..Default::default()
                        }),
                );
            }
            container(tbl)
                .width(Length::Fill)
                .clip(true)
                .style(p.inset_panel_style())
                .into()
        }
        MdBlock::Details {
            id,
            summary,
            blocks,
        } => {
            let open = expanded_details.contains(id);
            let chevron = if open {
                SVG_CHEVRON_DOWN
            } else {
                SVG_CHEVRON_RIGHT
            };
            let trigger = Button::new(
                row![
                    icon_svg(chevron, 13.0, p.text_muted),
                    text(summary)
                        .size(12.5)
                        .medium()
                        .shaped()
                        .color(p.text_primary),
                ]
                .spacing(6)
                .align_y(Alignment::Center)
                .width(Length::Fill),
            )
            .on_press(on_toggle_details(*id))
            .style(p.ghost_button(6.0))
            .padding([8, 10])
            .width(Length::Fill);

            let mut card = column![trigger].width(Length::Fill);
            if open {
                card = card.push(
                    container(Space::new().height(1))
                        .width(Length::Fill)
                        .height(1)
                        .style(move |_| container::Style {
                            background: Some(Background::Color(p.hairline_subtle)),
                            ..Default::default()
                        }),
                );
                let mut inner = Column::new().spacing(8).width(Length::Fill);
                for b in blocks {
                    inner = inner.push(render_block(
                        b,
                        expanded_details,
                        remote_images,
                        (content_max_width - 24.0).max(240.0),
                        p,
                        on_link,
                        on_toggle_details,
                    ));
                }
                card = card.push(container(inner).padding([10, 12]).width(Length::Fill));
            }
            container(card)
                .width(Length::Fill)
                .clip(true)
                .style(p.inset_panel_style())
                .into()
        }
        MdBlock::MediaRow { items, centered } => {
            render_media_row(items, *centered, remote_images, content_max_width, p, on_link)
        }
        MdBlock::BadgeRow { items, centered } => {
            render_badge_pills(items, *centered, p, on_link)
        }
        MdBlock::Divider => container(Space::new().height(1))
            .width(Length::Fill)
            .height(1)
            .style(move |_| container::Style {
                background: Some(Background::Color(p.hairline)),
                ..Default::default()
            })
            .into(),
    }
}

fn render_badge_pills<'a, M>(
    badges: &'a [(String, String)],
    centered: bool,
    p: Palette,
    on_link: impl Fn(String) -> M + Copy + 'a,
) -> Element<'a, M>
where
    M: Clone + 'a,
{
    let mut r = Row::new().spacing(6).align_y(Alignment::Center);
    for (label, href) in badges.iter().take(12) {
        r = r.push(render_single_badge_pill(label, href, p, on_link));
    }
    let wrapped = r
        .wrap()
        .vertical_spacing(6)
        .align_x(if centered {
            Horizontal::Center
        } else {
            Horizontal::Left
        });
    container(wrapped)
        .width(Length::Fill)
        .align_x(if centered {
            Horizontal::Center
        } else {
            Horizontal::Left
        })
        .into()
}

fn render_single_badge_pill<'a, M>(
    label: &str,
    href: &str,
    p: Palette,
    on_link: impl Fn(String) -> M + Copy + 'a,
) -> Element<'a, M>
where
    M: Clone + 'a,
{
    Button::new(
        container(
            row![
                text(label.to_string())
                    .size(11.5)
                    .medium()
                    .nowrap()
                    .color(p.text_primary),
                icon_svg(SVG_EXTERNAL, 11.0, p.text_muted),
            ]
            .spacing(5)
            .align_y(Alignment::Center),
        )
        .height(Length::Fill)
        .align_y(Vertical::Center),
    )
    .on_press(on_link(href.to_string()))
    .style(p.secondary_button())
    .height(Length::Fixed(28.0))
    .padding([0, 10])
    .into()
}

fn render_media_row<'a, M>(
    items: &'a [InlineMediaItem],
    centered: bool,
    remote_images: &'a HashMap<String, RemoteImage>,
    content_max_width: f32,
    p: Palette,
    on_link: impl Fn(String) -> M + Copy + 'a,
) -> Element<'a, M>
where
    M: Clone + 'a,
{
    let avail_w = content_max_width.clamp(280.0, 760.0);

    if items.len() == 1 {
        let item = &items[0];
        let loaded = remote_images.get(&item.url);
        let looks_like_badge = is_badge_hint(item)
            || loaded.is_some_and(|img| img.height <= 68 && img.width <= 460);

        if !looks_like_badge {
            let body: Element<'a, M> = if let Some(img) = loaded {
                let cap_w = item
                    .width_attr
                    .map(|w| (w as f32).min(avail_w))
                    .unwrap_or(avail_w);
                let cap_h = item
                    .height_attr
                    .map(|h| (h as f32).min(380.0))
                    .unwrap_or(340.0);
                let (dw, dh) = fit_image_size(img.width, img.height, cap_w, cap_h);
                container(
                    image(img.handle.clone())
                        .width(Length::Fixed(dw))
                        .height(Length::Fixed(dh))
                        .content_fit(iced::ContentFit::Contain)
                        .border_radius(8.0),
                )
                .width(Length::Fixed(dw))
                .height(Length::Fixed(dh))
                .clip(true)
                .style(move |_| container::Style {
                    border: Border {
                        radius: 8.0.into(),
                        color: p.hairline_subtle,
                        width: 1.0,
                    },
                    ..Default::default()
                })
                .into()
            } else if let Some(ref href) = item.link {
                render_single_badge_pill(&badge_label(&item.alt, href), href, p, on_link)
            } else if !item.alt.is_empty() {
                text(&item.alt).size(11.5).shaped().color(p.text_dim).into()
            } else {
                Space::new().height(0).into()
            };

            let clickable: Element<'a, M> = if let Some(ref href) = item.link
                && loaded.is_some()
            {
                Button::new(body)
                    .on_press(on_link(href.clone()))
                    .style(p.ghost_button(8.0))
                    .padding(0)
                    .into()
            } else {
                body
            };

            return container(clickable)
                .width(Length::Fill)
                .align_x(if centered {
                    Horizontal::Center
                } else {
                    Horizontal::Left
                })
                .into();
        }
    }

    let mut row_items = Row::new().spacing(8).align_y(Alignment::Center);
    let mut any_rendered = false;

    for item in items {
        let el: Element<'a, M> = if let Some(img) = remote_images.get(&item.url) {
            let is_compact = is_badge_hint(item)
                || (items.len() >= 2 && item.link.is_some() && img.height <= 140)
                || (img.height <= 68 && img.width <= 460);

            let (dw, dh) = if is_compact {
                let target_h = item
                    .height_attr
                    .map(|h| (h as f32).clamp(18.0, 52.0))
                    .unwrap_or(36.0);
                let target_w = item
                    .width_attr
                    .map(|w| (w as f32).clamp(24.0, avail_w.min(320.0)))
                    .unwrap_or_else(|| avail_w.min(320.0));
                fit_image_size(img.width, img.height, target_w, target_h)
            } else {
                let cap_w = item
                    .width_attr
                    .map(|w| (w as f32).min(avail_w))
                    .unwrap_or(if items.len() >= 2 {
                        ((avail_w - 8.0) / 2.0).max(160.0)
                    } else {
                        avail_w
                    });
                let cap_h = item
                    .height_attr
                    .map(|h| (h as f32).min(240.0))
                    .unwrap_or(160.0);
                fit_image_size(img.width, img.height, cap_w, cap_h)
            };

            let img_widget = image(img.handle.clone())
                .width(Length::Fixed(dw))
                .height(Length::Fixed(dh))
                .content_fit(iced::ContentFit::Contain)
                .border_radius(if is_compact { 6.0 } else { 8.0 });

            if let Some(ref href) = item.link {
                Button::new(img_widget)
                    .on_press(on_link(href.clone()))
                    .style(p.ghost_button(if is_compact { 6.0 } else { 8.0 }))
                    .padding(0)
                    .into()
            } else {
                img_widget.into()
            }
        } else if let Some(ref href) = item.link {
            render_single_badge_pill(&badge_label(&item.alt, href), href, p, on_link)
        } else if !item.alt.is_empty() {
            text(&item.alt).size(11.5).shaped().color(p.text_dim).into()
        } else {
            continue;
        };

        any_rendered = true;
        row_items = row_items.push(el);
    }

    if !any_rendered {
        return Space::new().height(0).into();
    }

    let wrapped = row_items
        .wrap()
        .vertical_spacing(8)
        .align_x(if centered {
            Horizontal::Center
        } else {
            Horizontal::Left
        });

    container(wrapped)
        .width(Length::Fill)
        .align_x(if centered {
            Horizontal::Center
        } else {
            Horizontal::Left
        })
        .into()
}

fn render_rich_spans<'a, M>(
    spans: &'a [InlineSpan],
    base_size: f32,
    base_color: Color,
    force_bold: bool,
    p: Palette,
    on_link: impl Fn(String) -> M + 'a,
) -> Element<'a, M>
where
    M: Clone + 'a,
{
    let link_color = if p.is_dark {
        Color::from_rgb(0.490, 0.690, 0.980)
    } else {
        Color::from_rgb(0.145, 0.360, 0.840)
    };
    let code_bg = if p.is_dark {
        Color::from_rgba(1.0, 1.0, 1.0, 0.08)
    } else {
        Color::from_rgba(0.06, 0.08, 0.14, 0.07)
    };

    let built: Vec<iced::widget::text::Span<'a, String, Font>> = spans
        .iter()
        .map(|s| {
            let bold = force_bold || s.bold;
            let font = if s.code {
                FONT_MONO
            } else if bold && s.italic {
                FONT_SANS_BOLD_ITALIC
            } else if bold {
                FONT_SANS_MEDIUM
            } else if s.italic {
                FONT_SANS_ITALIC
            } else {
                FONT_SANS
            };
            let color = if s.link.is_some() {
                link_color
            } else if s.code || bold {
                p.text_primary
            } else {
                base_color
            };
            let mut sp = span(&s.text)
                .font(font)
                .size(if s.code { base_size - 0.5 } else { base_size })
                .color(color);
            if s.strike {
                sp = sp.strikethrough(true);
            }
            if s.code {
                sp = sp.background(code_bg);
            }
            if let Some(ref href) = s.link {
                sp = sp.link(href.clone()).underline(true);
            }
            sp
        })
        .collect();

    rich_text(built)
        .on_link_click(on_link)
        .size(base_size)
        .width(Length::Fill)
        .into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_real_modrinth_mixed_html_and_markdown() {
        let doc = r#"
<p align="center">
  <a href="https://discord.gg/example"><img src="https://img.shields.io/discord/123" alt="Discord"></a>
  <a href="https://github.com/example/mod"><img src="https://img.shields.io/github/stars/a/b" alt="GitHub"></a>
</p>

# Sodium &amp; Rendering

Supports `fabric_api` and **high FPS** with *zero_stutter* and ~~old hacks~~.
Visit [Modrinth](https://modrinth.com/mod/sodium) or <a href="https://example.com">Docs</a>.

> [!WARNING]
> Do not mix **OptiFine** with Sodium.

| Feature | Status |
| --- | --- |
| Vulkan | Ready |
| OpenGL | Ready |

<details>
<summary>FAQ &#x27;Performance&#x27;</summary>
- [x] Works on `1.21.4`
- [ ] Legacy `1.12.2`
</details>

```toml
version = "0.6.13"
```
"#;
        let blocks = parse_modrinth_markdown(doc);

        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::BadgeRow { items, centered: true } if items.len() == 2
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::Heading { level: 1, spans } if spans[0].text == "Sodium & Rendering"
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::Blockquote {
                callout: Some(CalloutKind::Warning),
                ..
            }
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::Table { headers, rows } if headers.len() == 2 && rows.len() == 2
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::Details { summary, blocks, .. }
                if summary == "FAQ 'Performance'" && !blocks.is_empty()
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::CodeBlock { lang: Some(l), .. } if l == "toml"
        )));

        let para_spans = parse_inline_spans("Use `fabric_api` and my_custom_mod_id with _italic_");
        let joined: String = para_spans.iter().map(|s| s.text.as_str()).collect();
        assert_eq!(joined, "Use fabric_api and my_custom_mod_id with italic");
        assert!(para_spans.iter().any(|s| s.code && s.text == "fabric_api"));
        assert!(para_spans.iter().any(|s| s.italic && s.text == "italic"));
    }

    #[test]
    fn parses_sodium_cached_badges_and_centered_img_closing_tag() {
        let sodium = r#"
[![Buy me a coffee](https://cdn.modrinth.com/data/cached_images/b888841adf2a7d4d76c5274d634c205ee88e9e20.png)](https://caffeinemc.net/donate)
[![Join us on Discord](https://cdn.modrinth.com/data/cached_images/de7f2f606702569cd98fc1f6a0b29dbf817dd870.png)](https://caffeinemc.net/discord)

<center>
 <img src="https://cdn.modrinth.com/data/AANobbMI/images/5ca2e8f6c72f3d543dd3dc95b706964c5678d80e.png" alt="Sodium 0.4 Comparison" height="300"></img>
</center>

[![Available on Fabric](https://cdn.modrinth.com/data/cached_images/513035d1231a86dff137c74e04a5e64633593f10.png)](https://modrinth.com/mod/sodium/versions?l=fabric)
[![Available on Quilt](https://cdn.modrinth.com/data/cached_images/a9ee492fd1a5fcbcc14d4105aa27be63f2518529.png)](https://modrinth.com/mod/sodium/versions?l=quilt)
[![Available on NeoForge](https://cdn.modrinth.com/data/cached_images/b9ae07fbc26c08220b4856cb244f86904d0c172c.png)](https://modrinth.com/mod/sodium/versions?l=neoforge)
"#;
        let blocks = parse_modrinth_markdown(sodium);

        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::MediaRow { items, centered: false } if items.len() == 2 && is_badge_hint(&items[0])
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::MediaRow { items, centered: true }
                if items.len() == 1
                    && items[0].alt == "Sodium 0.4 Comparison"
                    && items[0].height_attr == Some(300)
        )));
        assert!(blocks.iter().any(|b| matches!(
            b,
            MdBlock::MediaRow { items, centered: false } if items.len() == 3
        )));

        assert_eq!(fit_image_size(218, 41, 320.0, 36.0), (191.0, 36.0));
        assert_eq!(fit_image_size(120, 20, 320.0, 36.0), (120.0, 20.0));
        assert_eq!(fit_image_size(858, 484, 660.0, 340.0), (603.0, 340.0));
    }
}
