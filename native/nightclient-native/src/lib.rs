#![allow(non_snake_case, clippy::missing_safety_doc)]

mod gl_api;
mod gpu_instancer;
mod occlusion_simd;
mod packet_codec;
mod pbo_uploader;

use gl_api::{GLint, GLsizei, GLuint, GlApi, REQUIRED_FN_COUNT};
use gpu_instancer::{
    ENTITY_DRAW_CALLS, ENTITY_INSTANCES_DRAWN, EntityInstance, EntityVertex, FramePassParams,
    GpuInstancer, TEXT_DRAW_CALLS, TEXT_INSTANCES_DRAWN, TextInstance, TextVertex,
};
use packet_codec::{BYTES_DEFLATED, BYTES_INFLATED, PACKETS_DEFLATED, PACKETS_INFLATED};
use parking_lot::Mutex;
use pbo_uploader::{PBO_BYTES, PBO_UPLOADS, PboUploader};
use std::ffi::c_void;
use std::sync::atomic::Ordering;

pub type JniEnv = *mut c_void;
pub type JClass = *mut c_void;
pub type JBoolean = u8;
pub type JInt = i32;
pub type JLong = i64;

const ABI_VERSION: JInt = 1;

struct GlEngineState {
    gl: GlApi,
    instancer: Option<GpuInstancer>,
    pbo: Option<PboUploader>,
}

static GL_STATE: Mutex<Option<GlEngineState>> = Mutex::new(None);

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nAbiVersion(
    _env: JniEnv,
    _cls: JClass,
) -> JInt {
    ABI_VERSION
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nInitGl(
    _env: JniEnv,
    _cls: JClass,
    fn_ptrs_addr: JLong,
    fn_count: JInt,
) -> JInt {
    if fn_ptrs_addr == 0 || fn_count <= 0 || (fn_count as usize) < REQUIRED_FN_COUNT {
        return 0;
    }
    let ptrs = std::slice::from_raw_parts(fn_ptrs_addr as *const i64, fn_count as usize);
    let Some(gl) = GlApi::from_ptrs(ptrs) else {
        return 0;
    };

    let mut guard = GL_STATE.lock();
    if let Some(mut old) = guard.take() {
        if let Some(ref mut inst) = old.instancer {
            inst.destroy(&old.gl);
        }
        if let Some(ref mut pbo) = old.pbo {
            pbo.destroy(&old.gl);
        }
    }

    let pbo = PboUploader::new(&gl);
    let instancer = match GpuInstancer::new(&gl) {
        Ok(i) => Some(i),
        Err(err) => {
            eprintln!("[NightNative] GPU instancer disabled (falling back cleanly): {err}");
            None
        }
    };

    let mut caps: JInt = 1;
    if pbo.is_some() {
        caps |= 2;
    }
    if instancer.is_some() {
        caps |= 4;
    }
    *guard = Some(GlEngineState { gl, instancer, pbo });
    caps
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nResetGpuMeshes(
    _env: JniEnv,
    _cls: JClass,
) {
    let mut guard = GL_STATE.lock();
    if let Some(ref mut st) = *guard {
        if let Some(ref mut inst) = st.instancer {
            inst.reset_meshes();
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nShutdownGl(
    _env: JniEnv,
    _cls: JClass,
) {
    let mut guard = GL_STATE.lock();
    if let Some(mut old) = guard.take() {
        if let Some(ref mut inst) = old.instancer {
            inst.destroy(&old.gl);
        }
        if let Some(ref mut pbo) = old.pbo {
            pbo.destroy(&old.gl);
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nRegisterEntityMesh(
    _env: JniEnv,
    _cls: JClass,
    verts_addr: JLong,
    vert_count: JInt,
) -> JInt {
    if verts_addr == 0 || vert_count <= 0 || (vert_count & 3) != 0 {
        return 0;
    }
    let mut guard = GL_STATE.lock();
    let Some(ref mut st) = *guard else {
        return 0;
    };
    let Some(ref mut inst) = st.instancer else {
        return 0;
    };
    let verts = std::slice::from_raw_parts(verts_addr as *const EntityVertex, vert_count as usize);
    inst.register_entity_mesh(&st.gl, verts)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nRegisterTextMesh(
    _env: JniEnv,
    _cls: JClass,
    verts_addr: JLong,
    vert_count: JInt,
) -> JInt {
    if verts_addr == 0 || vert_count <= 0 || (vert_count & 3) != 0 {
        return 0;
    }
    let mut guard = GL_STATE.lock();
    let Some(ref mut st) = *guard else {
        return 0;
    };
    let Some(ref mut inst) = st.instancer else {
        return 0;
    };
    let verts = std::slice::from_raw_parts(verts_addr as *const TextVertex, vert_count as usize);
    inst.register_text_mesh(&st.gl, verts)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nFlushFrame(
    _env: JniEnv,
    _cls: JClass,
    params_addr: JLong,
    entities_addr: JLong,
    entity_count: JInt,
    texts_addr: JLong,
    text_count: JInt,
) {
    if params_addr == 0 || (entity_count <= 0 && text_count <= 0) {
        return;
    }
    let mut guard = GL_STATE.lock();
    let Some(ref mut st) = *guard else {
        return;
    };
    let Some(ref mut inst) = st.instancer else {
        return;
    };

    let params = &*(params_addr as *const FramePassParams);
    let entities = if entity_count > 0 && entities_addr != 0 {
        std::slice::from_raw_parts(entities_addr as *const EntityInstance, entity_count as usize)
    } else {
        &[]
    };
    let texts = if text_count > 0 && texts_addr != 0 {
        std::slice::from_raw_parts(texts_addr as *const TextInstance, text_count as usize)
    } else {
        &[]
    };
    inst.flush_frame(&st.gl, params, entities, texts);
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nUploadTexture2dPbo(
    _env: JniEnv,
    _cls: JClass,
    gl_tex_id: JInt,
    mip_level: JInt,
    dst_x: JInt,
    dst_y: JInt,
    width: JInt,
    height: JInt,
    src_ptr: JLong,
    src_stride_px: JInt,
    src_x: JInt,
    src_y: JInt,
    components: JInt,
) -> JBoolean {
    if src_ptr == 0
        || gl_tex_id <= 0
        || mip_level < 0
        || dst_x < 0
        || dst_y < 0
        || width <= 0
        || height <= 0
        || src_stride_px <= 0
        || src_x < 0
        || src_y < 0
        || components <= 0
    {
        return 0;
    }
    let mut guard = GL_STATE.lock();
    let Some(ref mut st) = *guard else {
        return 0;
    };
    let Some(ref mut pbo) = st.pbo else {
        return 0;
    };
    let ok = pbo.upload_tex_sub_image_2d(
        &st.gl,
        gl_tex_id as GLuint,
        mip_level as GLint,
        dst_x as GLint,
        dst_y as GLint,
        width as GLsizei,
        height as GLsizei,
        src_ptr as *const u8,
        src_stride_px as usize,
        src_x.max(0) as usize,
        src_y.max(0) as usize,
        components.max(0) as usize,
    );
    u8::from(ok)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nZlibInflate(
    _env: JniEnv,
    _cls: JClass,
    src_addr: JLong,
    src_len: JInt,
    dst_addr: JLong,
    dst_len: JInt,
) -> JInt {
    if src_addr == 0 || dst_addr == 0 || src_len <= 0 || dst_len <= 0 {
        return -1;
    }
    let src = std::slice::from_raw_parts(src_addr as *const u8, src_len as usize);
    let dst = std::slice::from_raw_parts_mut(dst_addr as *mut u8, dst_len as usize);
    packet_codec::inflate_zlib_into(src, dst)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nZlibDeflate(
    _env: JniEnv,
    _cls: JClass,
    src_addr: JLong,
    src_len: JInt,
    dst_addr: JLong,
    dst_cap: JInt,
    level: JInt,
) -> JInt {
    if src_addr == 0 || dst_addr == 0 || src_len <= 0 || dst_cap <= 0 {
        return -1;
    }
    let src = std::slice::from_raw_parts(src_addr as *const u8, src_len as usize);
    let dst = std::slice::from_raw_parts_mut(dst_addr as *mut u8, dst_cap as usize);
    packet_codec::deflate_zlib_into(src, dst, level.clamp(1, 9) as u32)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nZlibCompressBound(
    _env: JniEnv,
    _cls: JClass,
    src_len: JInt,
) -> JInt {
    if src_len <= 0 {
        return 64;
    }
    packet_codec::compress_bound(src_len as usize).min(i32::MAX as usize) as JInt
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nFrustumCullSpheres(
    _env: JniEnv,
    _cls: JClass,
    planes_addr: JLong,
    spheres_addr: JLong,
    count: JInt,
    out_vis_addr: JLong,
) -> JInt {
    if planes_addr == 0 || spheres_addr == 0 || out_vis_addr == 0 || count <= 0 {
        return 0;
    }
    let planes = &*(planes_addr as *const [f32; 24]);
    let spheres = std::slice::from_raw_parts(spheres_addr as *const f32, (count as usize) * 4);
    let out = std::slice::from_raw_parts_mut(out_vis_addr as *mut u8, count as usize);
    occlusion_simd::frustum_cull_batch_spheres(planes, spheres, out) as JInt
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nReadStats(
    _env: JniEnv,
    _cls: JClass,
    out_longs_addr: JLong,
    len: JInt,
) {
    if out_longs_addr == 0 || len < 10 {
        return;
    }
    let out = std::slice::from_raw_parts_mut(out_longs_addr as *mut i64, len as usize);
    out[0] = ENTITY_INSTANCES_DRAWN.load(Ordering::Relaxed) as i64;
    out[1] = ENTITY_DRAW_CALLS.load(Ordering::Relaxed) as i64;
    out[2] = TEXT_INSTANCES_DRAWN.load(Ordering::Relaxed) as i64;
    out[3] = TEXT_DRAW_CALLS.load(Ordering::Relaxed) as i64;
    out[4] = PBO_UPLOADS.load(Ordering::Relaxed) as i64;
    out[5] = PBO_BYTES.load(Ordering::Relaxed) as i64;
    out[6] = PACKETS_INFLATED.load(Ordering::Relaxed) as i64;
    out[7] = BYTES_INFLATED.load(Ordering::Relaxed) as i64;
    out[8] = PACKETS_DEFLATED.load(Ordering::Relaxed) as i64;
    out[9] = BYTES_DEFLATED.load(Ordering::Relaxed) as i64;
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_nightclient_platform_opt_NightNative_nResetStats(
    _env: JniEnv,
    _cls: JClass,
) {
    ENTITY_INSTANCES_DRAWN.store(0, Ordering::Relaxed);
    ENTITY_DRAW_CALLS.store(0, Ordering::Relaxed);
    TEXT_INSTANCES_DRAWN.store(0, Ordering::Relaxed);
    TEXT_DRAW_CALLS.store(0, Ordering::Relaxed);
    PBO_UPLOADS.store(0, Ordering::Relaxed);
    PBO_BYTES.store(0, Ordering::Relaxed);
    PACKETS_INFLATED.store(0, Ordering::Relaxed);
    BYTES_INFLATED.store(0, Ordering::Relaxed);
    PACKETS_DEFLATED.store(0, Ordering::Relaxed);
    BYTES_DEFLATED.store(0, Ordering::Relaxed);
}
