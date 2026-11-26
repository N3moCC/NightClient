use std::io::Read;
use std::os::unix::ffi::OsStringExt;
use std::os::unix::io::AsRawFd;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use sctk::data_device_manager::ReadPipe;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WaylandDndEvent {
    Hovered,
    Left,
    Dropped(Vec<PathBuf>),
}

type DndCallback = Arc<dyn Fn(WaylandDndEvent) + Send + Sync>;

static DND_LISTENER: Mutex<Option<DndCallback>> = Mutex::new(None);

pub fn register_dnd_listener<F>(callback: F)
where
    F: Fn(WaylandDndEvent) + Send + Sync + 'static,
{
    if let Ok(mut slot) = DND_LISTENER.lock() {
        *slot = Some(Arc::new(callback));
    }
}

pub(crate) fn emit_dnd_event(event: WaylandDndEvent) {
    let cb = DND_LISTENER.lock().ok().and_then(|g| g.clone());
    if let Some(cb) = cb {
        cb(event);
    }
}

pub(crate) fn pick_file_mime(mimes: &[String]) -> Option<String> {
    if let Some(m) = mimes.iter().find(|m| {
        let lower = m.to_ascii_lowercase();
        lower == "text/uri-list" || lower.starts_with("text/uri-list;")
    }) {
        return Some(m.clone());
    }
    if let Some(m) = mimes.iter().find(|m| {
        let lower = m.to_ascii_lowercase();
        lower == "x-special/gnome-copied-files" || lower == "application/x-kde-urilist"
    }) {
        return Some(m.clone());
    }
    None
}

pub(crate) fn read_pipe_with_timeout(mut pipe: ReadPipe, timeout_ms: u64) -> Vec<u8> {
    const MAX_BYTES: usize = 4 * 1024 * 1024;
    let fd = pipe.as_raw_fd();

    unsafe {
        let flags = libc::fcntl(fd, libc::F_GETFL);
        if flags >= 0 {
            libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
        }
    }

    let deadline = Instant::now() + Duration::from_millis(timeout_ms);
    let mut out = Vec::new();
    let mut chunk = [0u8; 8192];

    loop {
        let Some(remaining) = deadline.checked_duration_since(Instant::now()) else {
            break;
        };
        let wait_ms = remaining.as_millis().min(i32::MAX as u128) as i32;
        let mut pfd = libc::pollfd {
            fd,
            events: libc::POLLIN,
            revents: 0,
        };
        let ready = unsafe { libc::poll(&mut pfd, 1, wait_ms) };
        if ready < 0 {
            let err = std::io::Error::last_os_error();
            if err.kind() == std::io::ErrorKind::Interrupted {
                continue;
            }
            break;
        }
        if ready == 0 {
            break;
        }
        if (pfd.revents & (libc::POLLIN | libc::POLLHUP)) != 0 {
            loop {
                match pipe.read(&mut chunk) {
                    Ok(0) => return out,
                    Ok(n) => {
                        let room = MAX_BYTES.saturating_sub(out.len());
                        if room == 0 {
                            return out;
                        }
                        out.extend_from_slice(&chunk[..n.min(room)]);
                    }
                    Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                    Err(e) if e.kind() == std::io::ErrorKind::Interrupted => continue,
                    Err(_) => return out,
                }
            }
        }
        if (pfd.revents & (libc::POLLERR | libc::POLLNVAL)) != 0 {
            break;
        }
    }

    out
}

pub(crate) fn parse_file_uris(raw: &[u8]) -> Vec<PathBuf> {
    let text = String::from_utf8_lossy(raw);
    let mut paths = Vec::new();

    for raw_line in text.lines() {
        let line = raw_line.trim();
        if line.is_empty()
            || line.starts_with('#')
            || line.eq_ignore_ascii_case("copy")
            || line.eq_ignore_ascii_case("cut")
        {
            continue;
        }
        let Some(after_scheme) = line
            .strip_prefix("file://")
            .or_else(|| line.strip_prefix("FILE://"))
        else {
            continue;
        };

        let encoded_path = if after_scheme.starts_with('/') {
            after_scheme
        } else if after_scheme.starts_with("localhost/") {
            &after_scheme["localhost".len()..]
        } else if let Some(slash_idx) = after_scheme.find('/') {
            &after_scheme[slash_idx..]
        } else {
            continue;
        };

        let decoded = percent_decode_bytes(encoded_path.as_bytes());
        if decoded.is_empty() {
            continue;
        }
        let path = PathBuf::from(std::ffi::OsString::from_vec(decoded));
        if !paths.contains(&path) {
            paths.push(path);
        }
    }

    paths
}

fn percent_decode_bytes(input: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(input.len());
    let mut i = 0;
    while i < input.len() {
        if input[i] == b'%'
            && i + 2 < input.len()
            && let (Some(hi), Some(lo)) = (hex_val(input[i + 1]), hex_val(input[i + 2]))
        {
            out.push((hi << 4) | lo);
            i += 3;
            continue;
        }
        out.push(input[i]);
        i += 1;
    }
    out
}

fn hex_val(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_uri_list_with_spaces_cyrillic_and_gnome_format() {
        let payload = b"# comment\r\nfile:///home/user/My%20Mod%20%2B1.21.jar\r\nfile://localhost/home/user/%D0%A1%D0%B1%D0%BE%D1%80%D0%BA%D0%B0.mrpack\nfile:///home/user/My%20Mod%20%2B1.21.jar\n";
        let paths = parse_file_uris(payload);
        assert_eq!(
            paths,
            vec![
                PathBuf::from("/home/user/My Mod +1.21.jar"),
                PathBuf::from("/home/user/Сборка.mrpack"),
            ]
        );

        let gnome = b"copy\nfile:///tmp/shader%20pack.zip\n";
        assert_eq!(
            parse_file_uris(gnome),
            vec![PathBuf::from("/tmp/shader pack.zip")]
        );
    }

    #[test]
    fn picks_uri_list_before_gnome_fallback() {
        let mimes = vec![
            "text/plain".to_string(),
            "x-special/gnome-copied-files".to_string(),
            "text/uri-list".to_string(),
        ];
        assert_eq!(pick_file_mime(&mimes).as_deref(), Some("text/uri-list"));

        let gnome_only = vec!["x-special/gnome-copied-files".to_string()];
        assert_eq!(
            pick_file_mime(&gnome_only).as_deref(),
            Some("x-special/gnome-copied-files")
        );

        let text_only = vec!["text/plain;charset=utf-8".to_string()];
        assert_eq!(pick_file_mime(&text_only), None);
    }
}
