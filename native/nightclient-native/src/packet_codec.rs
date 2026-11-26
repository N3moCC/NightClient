use flate2::{Compress, Compression, Decompress, FlushCompress, FlushDecompress, Status};
use std::cell::RefCell;
use std::sync::atomic::{AtomicU64, Ordering};

pub static PACKETS_INFLATED: AtomicU64 = AtomicU64::new(0);
pub static BYTES_INFLATED: AtomicU64 = AtomicU64::new(0);
pub static PACKETS_DEFLATED: AtomicU64 = AtomicU64::new(0);
pub static BYTES_DEFLATED: AtomicU64 = AtomicU64::new(0);

thread_local! {
    static TLS_DECOMPRESS: RefCell<Decompress> = RefCell::new(Decompress::new(true));
    static TLS_COMPRESS: RefCell<Compress> = RefCell::new(Compress::new(Compression::fast(), true));
    static TLS_COMPRESS_LEVEL: RefCell<u32> = const { RefCell::new(1) };
}

pub fn inflate_zlib_into(src: &[u8], dst: &mut [u8]) -> i32 {
    if src.is_empty() || dst.is_empty() {
        return -1;
    }

    TLS_DECOMPRESS.with(|cell| {
        let mut dec = cell.borrow_mut();
        dec.reset(true);
        let before_out = dec.total_out();
        match dec.decompress(src, dst, FlushDecompress::Finish) {
            Ok(Status::StreamEnd) => {
                let written = (dec.total_out() - before_out) as usize;
                if written != dst.len() {
                    return -2;
                }
                PACKETS_INFLATED.fetch_add(1, Ordering::Relaxed);
                BYTES_INFLATED.fetch_add(written as u64, Ordering::Relaxed);
                written as i32
            }
            Ok(_) => -3,
            Err(_) => -4,
        }
    })
}

pub fn deflate_zlib_into(src: &[u8], dst: &mut [u8], level: u32) -> i32 {
    if src.is_empty() || dst.is_empty() {
        return -1;
    }
    let clamped = level.clamp(1, 9);
    TLS_COMPRESS.with(|cell| {
        let mut comp = cell.borrow_mut();
        TLS_COMPRESS_LEVEL.with(|lvl_cell| {
            let mut cur_lvl = lvl_cell.borrow_mut();
            if *cur_lvl != clamped {
                *comp = Compress::new(Compression::new(clamped), true);
                *cur_lvl = clamped;
            } else {
                comp.reset();
            }
        });

        let before_out = comp.total_out();
        match comp.compress(src, dst, FlushCompress::Finish) {
            Ok(Status::StreamEnd) => {
                let written = (comp.total_out() - before_out) as usize;
                if written == 0 || written > dst.len() {
                    return -2;
                }
                PACKETS_DEFLATED.fetch_add(1, Ordering::Relaxed);
                BYTES_DEFLATED.fetch_add(src.len() as u64, Ordering::Relaxed);
                written as i32
            }
            Ok(_) => -4,
            Err(_) => -5,
        }
    })
}

#[inline]
pub fn compress_bound(src_len: usize) -> usize {
    src_len + (src_len >> 12) + (src_len >> 14) + 64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_chunk_like_payload() {
        let mut payload = Vec::with_capacity(65536);
        for i in 0..16384u32 {
            payload.extend_from_slice(&(i % 251).to_le_bytes());
        }
        let mut compressed = vec![0u8; compress_bound(payload.len())];
        let c_len = deflate_zlib_into(&payload, &mut compressed, 4);
        assert!(c_len > 0, "deflate failed: {c_len}");
        let mut restored = vec![0u8; payload.len()];
        let d_len = inflate_zlib_into(&compressed[..c_len as usize], &mut restored);
        assert_eq!(d_len, payload.len() as i32);
        assert_eq!(restored, payload);
    }

    #[test]
    fn rejects_corrupt_checksum() {
        let payload = b"NightClient packet codec integrity test payload repeated repeated repeated!";
        let mut compressed = vec![0u8; compress_bound(payload.len())];
        let c_len = deflate_zlib_into(payload, &mut compressed, 1) as usize;
        compressed[c_len - 1] ^= 0xFF;
        let mut restored = vec![0u8; payload.len()];
        assert!(inflate_zlib_into(&compressed[..c_len], &mut restored) < 0);
    }
}
