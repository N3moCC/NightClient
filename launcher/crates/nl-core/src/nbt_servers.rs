use flate2::read::GzDecoder;
use std::fs;
use std::io::{Cursor, Read};
use std::path::Path;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SavedServer {
    pub name: String,
    pub address: String,
}

pub fn read_servers_dat(instance_dir: &Path) -> Vec<SavedServer> {
    let path = instance_dir.join("servers.dat");
    let Ok(raw) = fs::read(&path) else {
        return Vec::new();
    };
    let bytes = if raw.starts_with(&[0x1F, 0x8B]) {
        let mut dec = GzDecoder::new(&raw[..]);
        let mut out = Vec::new();
        if dec.read_to_end(&mut out).is_err() {
            return Vec::new();
        }
        out
    } else {
        raw
    };
    parse_servers_nbt(&bytes).unwrap_or_default()
}

pub fn parse_servers_nbt(bytes: &[u8]) -> Option<Vec<SavedServer>> {
    let mut cur = Cursor::new(bytes);
    let root_tag = read_u8(&mut cur)?;
    if root_tag != 10 {
        return None;
    }
    let _root_name = read_string(&mut cur)?;
    let mut servers = Vec::new();

    loop {
        let tag = read_u8(&mut cur)?;
        if tag == 0 {
            break;
        }
        let name = read_string(&mut cur)?;
        if tag == 9 && name == "servers" {
            let elem_tag = read_u8(&mut cur)?;
            let count = read_i32(&mut cur)?;
            if elem_tag == 10 && count > 0 {
                for _ in 0..count.min(256) {
                    if let Some(srv) = read_server_compound(&mut cur) {
                        servers.push(srv);
                    }
                }
            } else {
                for _ in 0..count.max(0) {
                    skip_payload(&mut cur, elem_tag)?;
                }
            }
        } else {
            skip_payload(&mut cur, tag)?;
        }
    }

    Some(servers)
}

fn read_server_compound(cur: &mut Cursor<&[u8]>) -> Option<SavedServer> {
    let mut name = String::new();
    let mut ip = String::new();
    loop {
        let tag = read_u8(cur)?;
        if tag == 0 {
            break;
        }
        let field = read_string(cur)?;
        if tag == 8 && field == "name" {
            name = read_string(cur)?;
        } else if tag == 8 && field == "ip" {
            ip = read_string(cur)?;
        } else {
            skip_payload(cur, tag)?;
        }
    }
    if ip.trim().is_empty() {
        return None;
    }
    if name.trim().is_empty() {
        name = ip.clone();
    }
    Some(SavedServer { name, address: ip })
}

fn skip_payload(cur: &mut Cursor<&[u8]>, tag: u8) -> Option<()> {
    match tag {
        0 => Some(()),
        1 => skip_bytes(cur, 1),
        2 => skip_bytes(cur, 2),
        3 | 5 => skip_bytes(cur, 4),
        4 | 6 => skip_bytes(cur, 8),
        7 => {
            let len = read_i32(cur)?.max(0) as usize;
            skip_bytes(cur, len)
        }
        8 => {
            let _ = read_string(cur)?;
            Some(())
        }
        9 => {
            let elem = read_u8(cur)?;
            let len = read_i32(cur)?.max(0);
            for _ in 0..len {
                skip_payload(cur, elem)?;
            }
            Some(())
        }
        10 => {
            loop {
                let t = read_u8(cur)?;
                if t == 0 {
                    break;
                }
                let _ = read_string(cur)?;
                skip_payload(cur, t)?;
            }
            Some(())
        }
        11 => {
            let len = read_i32(cur)?.max(0) as usize;
            skip_bytes(cur, len.checked_mul(4)?)
        }
        12 => {
            let len = read_i32(cur)?.max(0) as usize;
            skip_bytes(cur, len.checked_mul(8)?)
        }
        _ => None,
    }
}

fn read_u8(cur: &mut Cursor<&[u8]>) -> Option<u8> {
    let mut b = [0u8; 1];
    cur.read_exact(&mut b).ok()?;
    Some(b[0])
}

fn read_u16(cur: &mut Cursor<&[u8]>) -> Option<u16> {
    let mut b = [0u8; 2];
    cur.read_exact(&mut b).ok()?;
    Some(u16::from_be_bytes(b))
}

fn read_i32(cur: &mut Cursor<&[u8]>) -> Option<i32> {
    let mut b = [0u8; 4];
    cur.read_exact(&mut b).ok()?;
    Some(i32::from_be_bytes(b))
}

fn read_string(cur: &mut Cursor<&[u8]>) -> Option<String> {
    let len = read_u16(cur)? as usize;
    let mut buf = vec![0u8; len];
    cur.read_exact(&mut buf).ok()?;
    Some(String::from_utf8_lossy(&buf).into_owned())
}

fn skip_bytes(cur: &mut Cursor<&[u8]>, n: usize) -> Option<()> {
    let pos = cur.position() as usize;
    let end = pos.checked_add(n)?;
    if end > cur.get_ref().len() {
        return None;
    }
    cur.set_position(end as u64);
    Some(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_valid_servers_dat_nbt() {
        let mut buf = Vec::new();
        buf.push(10);
        buf.extend_from_slice(&0u16.to_be_bytes());
        buf.push(9);
        buf.extend_from_slice(&7u16.to_be_bytes());
        buf.extend_from_slice(b"servers");
        buf.push(10);
        buf.extend_from_slice(&1i32.to_be_bytes());
        buf.push(8);
        buf.extend_from_slice(&4u16.to_be_bytes());
        buf.extend_from_slice(b"name");
        buf.extend_from_slice(&9u16.to_be_bytes());
        buf.extend_from_slice(b"HolyWorld");
        buf.push(8);
        buf.extend_from_slice(&2u16.to_be_bytes());
        buf.extend_from_slice(b"ip");
        buf.extend_from_slice(&15u16.to_be_bytes());
        buf.extend_from_slice(b"mc.holyworld.ru");
        buf.push(0);
        buf.push(0);

        let parsed = parse_servers_nbt(&buf).unwrap();
        assert_eq!(
            parsed,
            vec![SavedServer {
                name: "HolyWorld".into(),
                address: "mc.holyworld.ru".into()
            }]
        );
    }
}
