#[inline(always)]
pub fn frustum_cull_batch_spheres(
    planes: &[f32; 24],
    positions: &[f32],
    out_visible: &mut [u8],
) -> usize {
    let n = (positions.len() / 4).min(out_visible.len());
    let mut visible = 0usize;
    let p0 = [planes[0], planes[1], planes[2], planes[3]];
    let p1 = [planes[4], planes[5], planes[6], planes[7]];
    let p2 = [planes[8], planes[9], planes[10], planes[11]];
    let p3 = [planes[12], planes[13], planes[14], planes[15]];
    let p4 = [planes[16], planes[17], planes[18], planes[19]];
    let p5 = [planes[20], planes[21], planes[22], planes[23]];

    for (i, vis) in out_visible.iter_mut().enumerate().take(n) {
        let base = i * 4;
        let x = positions[base];
        let y = positions[base + 1];
        let z = positions[base + 2];
        let neg_r = -positions[base + 3];
        let ok = (p0[0] * x + p0[1] * y + p0[2] * z + p0[3] >= neg_r)
            && (p1[0] * x + p1[1] * y + p1[2] * z + p1[3] >= neg_r)
            && (p2[0] * x + p2[1] * y + p2[2] * z + p2[3] >= neg_r)
            && (p3[0] * x + p3[1] * y + p3[2] * z + p3[3] >= neg_r)
            && (p4[0] * x + p4[1] * y + p4[2] * z + p4[3] >= neg_r)
            && (p5[0] * x + p5[1] * y + p5[2] * z + p5[3] >= neg_r);
        *vis = if ok { 1 } else { 0 };
        visible += usize::from(ok);
    }
    visible
}

#[allow(dead_code, clippy::too_many_arguments)]
pub fn transform_flat_cubes_36b(
    flat_quads: &[f32],
    pose16: &[f32; 16],
    normal9: &[f32; 9],
    trusted_normals: bool,
    packed_light: u32,
    packed_overlay: u32,
    abgr: u32,
    uv_rect: [f32; 4],
    out_bytes: &mut [u8],
) -> usize {
    let quad_count = flat_quads.len() / 23;
    let needed = quad_count * 4 * 36;
    if quad_count == 0 || out_bytes.len() < needed {
        return 0;
    }

    let m00 = pose16[0];
    let m01 = pose16[1];
    let m02 = pose16[2];
    let m10 = pose16[4];
    let m11 = pose16[5];
    let m12 = pose16[6];
    let m20 = pose16[8];
    let m21 = pose16[9];
    let m22 = pose16[10];
    let m30 = pose16[12];
    let m31 = pose16[13];
    let m32 = pose16[14];

    let n00 = normal9[0];
    let n01 = normal9[1];
    let n02 = normal9[2];
    let n10 = normal9[3];
    let n11 = normal9[4];
    let n12 = normal9[5];
    let n20 = normal9[6];
    let n21 = normal9[7];
    let n22 = normal9[8];

    let u0 = uv_rect[0];
    let v0 = uv_rect[1];
    let us = uv_rect[2];
    let vs = uv_rect[3];
    let scale_uv = u0 != 0.0 || v0 != 0.0 || us != 1.0 || vs != 1.0;

    let mut out_off = 0usize;
    for q in 0..quad_count {
        let base = q * 23;
        let fnx = flat_quads[base];
        let fny = flat_quads[base + 1];
        let fnz = flat_quads[base + 2];
        let mut nx = n00 * fnx + n10 * fny + n20 * fnz;
        let mut ny = n01 * fnx + n11 * fny + n21 * fnz;
        let mut nz = n02 * fnx + n12 * fny + n22 * fnz;
        if !trusted_normals {
            let len_sq = nx * nx + ny * ny + nz * nz;
            if len_sq > 1e-12 {
                let inv = len_sq.sqrt().recip();
                nx *= inv;
                ny *= inv;
                nz *= inv;
            }
        }
        let packed_normal = pack_normal(nx, ny, nz);

        for v in 0..4 {
            let vo = base + 3 + v * 5;
            let x = flat_quads[vo];
            let y = flat_quads[vo + 1];
            let z = flat_quads[vo + 2];
            let rx = m00 * x + m10 * y + m20 * z + m30;
            let ry = m01 * x + m11 * y + m21 * z + m31;
            let rz = m02 * x + m12 * y + m22 * z + m32;
            let mut tu = flat_quads[vo + 3];
            let mut tv = flat_quads[vo + 4];
            if scale_uv {
                tu = u0 + tu * us;
                tv = v0 + tv * vs;
            }
            let dst = &mut out_bytes[out_off..out_off + 36];
            dst[0..4].copy_from_slice(&rx.to_le_bytes());
            dst[4..8].copy_from_slice(&ry.to_le_bytes());
            dst[8..12].copy_from_slice(&rz.to_le_bytes());
            dst[12..16].copy_from_slice(&abgr.to_le_bytes());
            dst[16..20].copy_from_slice(&tu.to_le_bytes());
            dst[20..24].copy_from_slice(&tv.to_le_bytes());
            dst[24..28].copy_from_slice(&packed_overlay.to_le_bytes());
            dst[28..32].copy_from_slice(&packed_light.to_le_bytes());
            dst[32..36].copy_from_slice(&packed_normal.to_le_bytes());
            out_off += 36;
        }
    }
    quad_count * 4
}

#[allow(dead_code)]
#[inline(always)]
fn pack_normal(nx: f32, ny: f32, nz: f32) -> u32 {
    let bx = (nx.clamp(-1.0, 1.0) * 127.0) as i32 as u32 & 0xFF;
    let by = (ny.clamp(-1.0, 1.0) * 127.0) as i32 as u32 & 0xFF;
    let bz = (nz.clamp(-1.0, 1.0) * 127.0) as i32 as u32 & 0xFF;
    bx | (by << 8) | (bz << 16)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn frustum_batch_basic() {
        let planes: [f32; 24] = [
            1.0, 0.0, 0.0, 10.0, -1.0, 0.0, 0.0, 10.0, 0.0, 1.0, 0.0, 10.0, 0.0, -1.0, 0.0, 10.0,
            0.0, 0.0, 1.0, 10.0, 0.0, 0.0, -1.0, 10.0,
        ];
        let pos = [0.0f32, 0.0, 0.0, 1.0, 20.0, 0.0, 0.0, 1.0];
        let mut vis = [0u8; 2];
        assert_eq!(frustum_cull_batch_spheres(&planes, &pos, &mut vis), 1);
        assert_eq!(vis, [1, 0]);
    }
}
