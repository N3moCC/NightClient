use crate::gl_api::*;
use std::ffi::{CStr, CString, c_char, c_void};
use std::mem::size_of;
use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};

pub static ENTITY_INSTANCES_DRAWN: AtomicU64 = AtomicU64::new(0);
pub static ENTITY_DRAW_CALLS: AtomicU64 = AtomicU64::new(0);
pub static TEXT_INSTANCES_DRAWN: AtomicU64 = AtomicU64::new(0);
pub static TEXT_DRAW_CALLS: AtomicU64 = AtomicU64::new(0);

const RING_SLOTS: usize = 3;
const MAX_ENTITY_INSTANCES: usize = 32768;
const MAX_TEXT_INSTANCES: usize = 16384;
const MAX_INDIRECT_CMDS: usize = 8192;
const TEX_SLOTS_PER_BATCH: usize = 16;

#[allow(dead_code)]
pub const ENT_FLAG_CULL_FACE: u32 = 1 << 0;
#[allow(dead_code)]
pub const ENT_FLAG_CUTOUT_01: u32 = 1 << 1;
#[allow(dead_code)]
pub const ENT_FLAG_CUTOUT_05: u32 = 1 << 2;
#[allow(dead_code)]
pub const ENT_FLAG_NO_CARDINAL_LIGHT: u32 = 1 << 3;
#[allow(dead_code)]
pub const ENT_FLAG_PER_FACE_LIGHT: u32 = 1 << 4;
#[allow(dead_code)]
pub const ENT_FLAG_EMISSIVE: u32 = 1 << 5;
#[allow(dead_code)]
pub const ENT_FLAG_NO_OVERLAY: u32 = 1 << 6;
pub const ENT_FLAG_BLEND: u32 = 1 << 7;

pub const TXT_FLAG_SEE_THROUGH: u32 = 1 << 0;
pub const TXT_FLAG_POLYGON_OFFSET: u32 = 1 << 1;
pub const TXT_FLAG_NO_TEX: u32 = 1 << 2;
#[allow(dead_code)]
pub const TXT_FLAG_INTENSITY: u32 = 1 << 3;

#[repr(C)]
#[derive(Clone, Copy)]
pub struct EntityVertex {
    pub px: f32,
    pub py: f32,
    pub pz: f32,
    pub u: f32,
    pub v: f32,
    pub nx: f32,
    pub ny: f32,
    pub nz: f32,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct EntityInstance {
    pub pose: [f32; 16],
    pub uv_rect: [f32; 4],
    pub mesh_id: u32,
    pub gl_tex_id: u32,
    pub packed_light: u32,
    pub packed_overlay: u32,
    pub argb_color: u32,
    pub flags: u32,
    pub tex_slot: u32,
    pub _pad: u32,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct TextVertex {
    pub px: f32,
    pub py: f32,
    pub pz: f32,
    pub u: f32,
    pub v: f32,
    pub abgr: u32,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct TextInstance {
    pub pose: [f32; 16],
    pub mesh_id: u32,
    pub gl_tex_id: u32,
    pub packed_light: u32,
    pub flags: u32,
    pub tex_slot: u32,
    pub _pad: [u32; 3],
}

#[repr(C)]
#[derive(Clone, Copy)]
struct DrawElementsIndirectCommand {
    count: u32,
    instance_count: u32,
    first_index: u32,
    base_vertex: i32,
    base_instance: u32,
}

#[derive(Clone, Copy)]
struct MeshRecord {
    first_index: u32,
    index_count: u32,
    base_vertex: i32,
}

struct MegaGeometry {
    vbo: GLuint,
    ebo: GLuint,
    vbo_cap_verts: usize,
    ebo_cap_indices: usize,
    vbo_used_verts: usize,
    ebo_used_indices: usize,
    vert_stride: usize,
    meshes: Vec<MeshRecord>,
}

impl MegaGeometry {
    unsafe fn new(gl: &GlApi, vert_stride: usize, init_verts: usize, init_indices: usize) -> Self {
        let mut bufs = [0u32; 2];
        (gl.glGenBuffers)(2, bufs.as_mut_ptr());
        let (vbo, ebo) = (bufs[0], bufs[1]);
        let mut prev_vbo = 0;
        (gl.glGetIntegerv)(GL_ARRAY_BUFFER_BINDING, &mut prev_vbo);

        (gl.glBindBuffer)(GL_ARRAY_BUFFER, vbo);
        (gl.glBufferData)(
            GL_ARRAY_BUFFER,
            (init_verts * vert_stride) as GLsizeiptr,
            ptr::null(),
            GL_STATIC_DRAW,
        );
        (gl.glBindBuffer)(GL_COPY_WRITE_BUFFER, ebo);
        (gl.glBufferData)(
            GL_COPY_WRITE_BUFFER,
            (init_indices * size_of::<u32>()) as GLsizeiptr,
            ptr::null(),
            GL_STATIC_DRAW,
        );

        (gl.glBindBuffer)(GL_ARRAY_BUFFER, prev_vbo as GLuint);
        (gl.glBindBuffer)(GL_COPY_WRITE_BUFFER, 0);

        Self {
            vbo,
            ebo,
            vbo_cap_verts: init_verts,
            ebo_cap_indices: init_indices,
            vbo_used_verts: 0,
            ebo_used_indices: 0,
            vert_stride,
            meshes: vec![MeshRecord {
                first_index: 0,
                index_count: 0,
                base_vertex: 0,
            }],
        }
    }

    unsafe fn reset(&mut self) {
        self.vbo_used_verts = 0;
        self.ebo_used_indices = 0;
        self.meshes.truncate(1);
    }

    unsafe fn destroy(&mut self, gl: &GlApi) {
        let bufs = [self.vbo, self.ebo];
        (gl.glDeleteBuffers)(2, bufs.as_ptr());
        self.vbo = 0;
        self.ebo = 0;
    }

    unsafe fn grow_buffer(
        gl: &GlApi,
        buf: &mut GLuint,
        old_bytes: usize,
        new_bytes: usize,
        vao_to_rebind: GLuint,
        is_ebo: bool,
    ) {
        let mut new_buf: GLuint = 0;
        (gl.glGenBuffers)(1, &mut new_buf);
        (gl.glBindBuffer)(GL_COPY_READ_BUFFER, *buf);
        (gl.glBindBuffer)(GL_COPY_WRITE_BUFFER, new_buf);
        (gl.glBufferData)(
            GL_COPY_WRITE_BUFFER,
            new_bytes as GLsizeiptr,
            ptr::null(),
            GL_STATIC_DRAW,
        );
        if old_bytes > 0 {
            (gl.glCopyBufferSubData)(
                GL_COPY_READ_BUFFER,
                GL_COPY_WRITE_BUFFER,
                0,
                0,
                old_bytes as GLsizeiptr,
            );
        }
        (gl.glBindBuffer)(GL_COPY_READ_BUFFER, 0);
        (gl.glBindBuffer)(GL_COPY_WRITE_BUFFER, 0);

        let old = *buf;
        *buf = new_buf;
        let _ = (vao_to_rebind, is_ebo);
        (gl.glDeleteBuffers)(1, &old);
    }

    unsafe fn register_quads(
        &mut self,
        gl: &GlApi,
        vertex_bytes: &[u8],
        vert_count: usize,
        vao: GLuint,
        rebind_vao: impl FnOnce(&GlApi, GLuint, GLuint, GLuint),
    ) -> i32 {
        if vert_count == 0 || (vert_count & 3) != 0 {
            return 0;
        }
        let quad_count = vert_count >> 2;
        let idx_count = quad_count * 6;

        let mut prev_vao = 0;
        let mut prev_vbo = 0;
        let mut prev_ebo = 0;
        (gl.glGetIntegerv)(GL_VERTEX_ARRAY_BINDING, &mut prev_vao);
        (gl.glGetIntegerv)(GL_ARRAY_BUFFER_BINDING, &mut prev_vbo);
        (gl.glGetIntegerv)(GL_ELEMENT_ARRAY_BUFFER_BINDING, &mut prev_ebo);

        (gl.glBindVertexArray)(0);

        let mut grew = false;
        if self.vbo_used_verts + vert_count > self.vbo_cap_verts {
            let new_cap = (self.vbo_cap_verts * 2).max(self.vbo_used_verts + vert_count + 4096);
            Self::grow_buffer(
                gl,
                &mut self.vbo,
                self.vbo_used_verts * self.vert_stride,
                new_cap * self.vert_stride,
                vao,
                false,
            );
            self.vbo_cap_verts = new_cap;
            grew = true;
        }
        if self.ebo_used_indices + idx_count > self.ebo_cap_indices {
            let new_cap = (self.ebo_cap_indices * 2).max(self.ebo_used_indices + idx_count + 8192);
            Self::grow_buffer(
                gl,
                &mut self.ebo,
                self.ebo_used_indices * size_of::<u32>(),
                new_cap * size_of::<u32>(),
                vao,
                true,
            );
            self.ebo_cap_indices = new_cap;
            grew = true;
        }

        let base_vertex = self.vbo_used_verts as i32;
        let first_index = self.ebo_used_indices as u32;

        (gl.glBindBuffer)(GL_ARRAY_BUFFER, self.vbo);
        (gl.glBufferSubData)(
            GL_ARRAY_BUFFER,
            (self.vbo_used_verts * self.vert_stride) as GLintptr,
            (vert_count * self.vert_stride) as GLsizeiptr,
            vertex_bytes.as_ptr() as *const c_void,
        );

        let mut indices = Vec::with_capacity(idx_count);
        for q in 0..quad_count as u32 {
            let b = q * 4;
            indices.push(b);
            indices.push(b + 1);
            indices.push(b + 2);
            indices.push(b + 2);
            indices.push(b + 3);
            indices.push(b);
        }
        (gl.glBindBuffer)(GL_COPY_WRITE_BUFFER, self.ebo);
        (gl.glBufferSubData)(
            GL_COPY_WRITE_BUFFER,
            (self.ebo_used_indices * size_of::<u32>()) as GLintptr,
            (idx_count * size_of::<u32>()) as GLsizeiptr,
            indices.as_ptr() as *const c_void,
        );
        (gl.glBindBuffer)(GL_COPY_WRITE_BUFFER, 0);

        self.vbo_used_verts += vert_count;
        self.ebo_used_indices += idx_count;

        if grew {
            rebind_vao(gl, vao, self.vbo, self.ebo);
        }

        (gl.glBindVertexArray)(prev_vao as GLuint);
        (gl.glBindBuffer)(GL_ARRAY_BUFFER, prev_vbo as GLuint);
        if prev_vao != 0 {
            (gl.glBindBuffer)(GL_ELEMENT_ARRAY_BUFFER, prev_ebo as GLuint);
        }

        let id = self.meshes.len() as i32;
        self.meshes.push(MeshRecord {
            first_index,
            index_count: idx_count as u32,
            base_vertex,
        });
        id
    }
}

struct RingBuffer {
    handle: GLuint,
    mapped: *mut u8,
    slot_bytes: usize,
    persistent: bool,
    fences: [GLsync; RING_SLOTS],
}

unsafe impl Send for RingBuffer {}

impl RingBuffer {
    unsafe fn new(gl: &GlApi, target: GLenum, slot_bytes: usize) -> Option<Self> {
        let mut handle: GLuint = 0;
        (gl.glGenBuffers)(1, &mut handle);
        if handle == 0 {
            return None;
        }
        (gl.glBindBuffer)(target, handle);

        let total_bytes = slot_bytes * RING_SLOTS;
        let mut mapped = ptr::null_mut();
        let mut persistent = false;

        if let Some(buf_storage) = gl.glBufferStorage {
            while (gl.glGetError)() != GL_NO_ERROR {}
            let flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
            buf_storage(target, total_bytes as GLsizeiptr, ptr::null(), flags);
            if (gl.glGetError)() == GL_NO_ERROR {
                let ptr_void = (gl.glMapBufferRange)(target, 0, total_bytes as GLsizeiptr, flags);
                if !ptr_void.is_null() {
                    mapped = ptr_void as *mut u8;
                    persistent = true;
                }
            }
        }
        if !persistent {
            (gl.glBufferData)(
                target,
                total_bytes as GLsizeiptr,
                ptr::null(),
                GL_STREAM_DRAW,
            );
        }
        (gl.glBindBuffer)(target, 0);
        Some(Self {
            handle,
            mapped,
            slot_bytes,
            persistent,
            fences: [ptr::null_mut(); RING_SLOTS],
        })
    }

    unsafe fn wait_slot(&mut self, gl: &GlApi, slot: usize) {
        let f = self.fences[slot];
        if !f.is_null() {
            let s = (gl.glClientWaitSync)(f, GL_SYNC_FLUSH_COMMANDS_BIT, 50_000_000);
            if s != GL_ALREADY_SIGNALED && s != GL_CONDITION_SATISFIED {
                let _ = (gl.glClientWaitSync)(f, GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000);
            }
            (gl.glDeleteSync)(f);
            self.fences[slot] = ptr::null_mut();
        }
    }

    unsafe fn fence_slot(&mut self, gl: &GlApi, slot: usize) {
        if !self.fences[slot].is_null() {
            (gl.glDeleteSync)(self.fences[slot]);
        }
        self.fences[slot] = (gl.glFenceSync)(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    unsafe fn write_slot(&self, gl: &GlApi, target: GLenum, slot: usize, data: &[u8]) -> usize {
        let offset = slot * self.slot_bytes;
        if self.persistent && !self.mapped.is_null() {
            ptr::copy_nonoverlapping(data.as_ptr(), self.mapped.add(offset), data.len());
        } else {
            (gl.glBindBuffer)(target, self.handle);
            (gl.glBufferSubData)(
                target,
                offset as GLintptr,
                data.len() as GLsizeiptr,
                data.as_ptr() as *const c_void,
            );
        }
        offset
    }

    unsafe fn destroy(&mut self, gl: &GlApi, target: GLenum) {
        for f in &mut self.fences {
            if !f.is_null() {
                (gl.glDeleteSync)(*f);
                *f = ptr::null_mut();
            }
        }
        if self.handle != 0 {
            if self.persistent {
                (gl.glBindBuffer)(target, self.handle);
                (gl.glUnmapBuffer)(target);
                (gl.glBindBuffer)(target, 0);
            }
            (gl.glDeleteBuffers)(1, &self.handle);
            self.handle = 0;
            self.mapped = ptr::null_mut();
        }
    }
}

struct SavedGlState {
    draw_fbo: GLint,
    viewport: [GLint; 4],
    program: GLint,
    vao: GLint,
    vbo: GLint,
    ebo: GLint,
    indirect_buf: GLint,
    ssbo0: GLint,
    ubo_buf: [GLint; 3],
    ubo_start: [i64; 3],
    ubo_size: [i64; 3],
    active_tex: GLint,
    tex_2d: [GLint; 18],
    samplers: [GLint; 18],
    blend: bool,
    blend_src_rgb: GLint,
    blend_dst_rgb: GLint,
    blend_src_alpha: GLint,
    blend_dst_alpha: GLint,
    depth_test: bool,
    depth_mask: GLboolean,
    depth_func: GLint,
    cull_face: bool,
    scissor_test: bool,
    polygon_offset_fill: bool,
    color_mask: [GLboolean; 4],
}

impl SavedGlState {
    unsafe fn capture(gl: &GlApi) -> Self {
        let mut s = Self {
            draw_fbo: 0,
            viewport: [0; 4],
            program: 0,
            vao: 0,
            vbo: 0,
            ebo: 0,
            indirect_buf: 0,
            ssbo0: 0,
            ubo_buf: [0; 3],
            ubo_start: [0; 3],
            ubo_size: [0; 3],
            active_tex: GL_TEXTURE0 as GLint,
            tex_2d: [0; 18],
            samplers: [0; 18],
            blend: false,
            blend_src_rgb: GL_SRC_ALPHA as GLint,
            blend_dst_rgb: GL_ONE_MINUS_SRC_ALPHA as GLint,
            blend_src_alpha: GL_ONE as GLint,
            blend_dst_alpha: GL_ZERO as GLint,
            depth_test: true,
            depth_mask: GL_TRUE,
            depth_func: GL_LEQUAL as GLint,
            cull_face: true,
            scissor_test: false,
            polygon_offset_fill: false,
            color_mask: [GL_TRUE; 4],
        };
        (gl.glGetIntegerv)(GL_DRAW_FRAMEBUFFER_BINDING, &mut s.draw_fbo);
        (gl.glGetIntegerv)(GL_VIEWPORT, s.viewport.as_mut_ptr());
        (gl.glGetIntegerv)(GL_CURRENT_PROGRAM, &mut s.program);
        (gl.glGetIntegerv)(GL_VERTEX_ARRAY_BINDING, &mut s.vao);
        (gl.glGetIntegerv)(GL_ARRAY_BUFFER_BINDING, &mut s.vbo);
        (gl.glGetIntegerv)(GL_ELEMENT_ARRAY_BUFFER_BINDING, &mut s.ebo);
        (gl.glGetIntegerv)(GL_DRAW_INDIRECT_BUFFER_BINDING, &mut s.indirect_buf);
        (gl.glGetIntegeri_v)(GL_SHADER_STORAGE_BUFFER_BINDING, 0, &mut s.ssbo0);
        for i in 0..3u32 {
            (gl.glGetIntegeri_v)(GL_UNIFORM_BUFFER_BINDING, i, &mut s.ubo_buf[i as usize]);
            (gl.glGetInteger64i_v)(GL_UNIFORM_BUFFER_START, i, &mut s.ubo_start[i as usize]);
            (gl.glGetInteger64i_v)(GL_UNIFORM_BUFFER_SIZE, i, &mut s.ubo_size[i as usize]);
        }
        (gl.glGetIntegerv)(GL_ACTIVE_TEXTURE, &mut s.active_tex);
        for u in 0..18u32 {
            (gl.glActiveTexture)(GL_TEXTURE0 + u);
            (gl.glGetIntegerv)(GL_TEXTURE_BINDING_2D, &mut s.tex_2d[u as usize]);
            (gl.glGetIntegerv)(GL_SAMPLER_BINDING, &mut s.samplers[u as usize]);
        }
        s.blend = (gl.glIsEnabled)(GL_BLEND) != 0;
        (gl.glGetIntegerv)(GL_BLEND_SRC_RGB, &mut s.blend_src_rgb);
        (gl.glGetIntegerv)(GL_BLEND_DST_RGB, &mut s.blend_dst_rgb);
        (gl.glGetIntegerv)(GL_BLEND_SRC_ALPHA, &mut s.blend_src_alpha);
        (gl.glGetIntegerv)(GL_BLEND_DST_ALPHA, &mut s.blend_dst_alpha);
        s.depth_test = (gl.glIsEnabled)(GL_DEPTH_TEST) != 0;
        (gl.glGetBooleanv)(GL_DEPTH_WRITEMASK, &mut s.depth_mask);
        (gl.glGetIntegerv)(GL_DEPTH_FUNC, &mut s.depth_func);
        s.cull_face = (gl.glIsEnabled)(GL_CULL_FACE) != 0;
        s.scissor_test = (gl.glIsEnabled)(GL_SCISSOR_TEST) != 0;
        s.polygon_offset_fill = (gl.glIsEnabled)(GL_POLYGON_OFFSET_FILL) != 0;
        (gl.glGetBooleanv)(GL_COLOR_WRITEMASK, s.color_mask.as_mut_ptr());
        s
    }

    unsafe fn restore(&self, gl: &GlApi) {
        (gl.glBindFramebuffer)(GL_DRAW_FRAMEBUFFER, self.draw_fbo as GLuint);
        (gl.glViewport)(
            self.viewport[0],
            self.viewport[1],
            self.viewport[2],
            self.viewport[3],
        );
        (gl.glUseProgram)(self.program as GLuint);
        (gl.glBindVertexArray)(self.vao as GLuint);
        (gl.glBindBuffer)(GL_ARRAY_BUFFER, self.vbo as GLuint);
        (gl.glBindBuffer)(GL_ELEMENT_ARRAY_BUFFER, self.ebo as GLuint);
        (gl.glBindBuffer)(GL_DRAW_INDIRECT_BUFFER, self.indirect_buf as GLuint);
        (gl.glBindBufferBase)(GL_SHADER_STORAGE_BUFFER, 0, self.ssbo0 as GLuint);
        for i in 0..3u32 {
            let buf = self.ubo_buf[i as usize] as GLuint;
            let sz = self.ubo_size[i as usize];
            if buf != 0 && sz > 0 {
                (gl.glBindBufferRange)(
                    GL_UNIFORM_BUFFER,
                    i,
                    buf,
                    self.ubo_start[i as usize] as GLintptr,
                    sz as GLsizeiptr,
                );
            } else {
                (gl.glBindBufferBase)(GL_UNIFORM_BUFFER, i, buf);
            }
        }
        for u in 0..18u32 {
            (gl.glActiveTexture)(GL_TEXTURE0 + u);
            (gl.glBindTexture)(GL_TEXTURE_2D, self.tex_2d[u as usize] as GLuint);
            (gl.glBindSampler)(u, self.samplers[u as usize] as GLuint);
        }
        (gl.glActiveTexture)(self.active_tex as GLenum);
        set_enabled(gl, GL_BLEND, self.blend);
        (gl.glBlendFuncSeparate)(
            self.blend_src_rgb as GLenum,
            self.blend_dst_rgb as GLenum,
            self.blend_src_alpha as GLenum,
            self.blend_dst_alpha as GLenum,
        );
        set_enabled(gl, GL_DEPTH_TEST, self.depth_test);
        (gl.glDepthMask)(self.depth_mask);
        (gl.glDepthFunc)(self.depth_func as GLenum);
        set_enabled(gl, GL_CULL_FACE, self.cull_face);
        set_enabled(gl, GL_SCISSOR_TEST, self.scissor_test);
        set_enabled(gl, GL_POLYGON_OFFSET_FILL, self.polygon_offset_fill);
        (gl.glColorMask)(
            self.color_mask[0],
            self.color_mask[1],
            self.color_mask[2],
            self.color_mask[3],
        );
    }
}

#[inline(always)]
unsafe fn set_enabled(gl: &GlApi, cap: GLenum, on: bool) {
    if on {
        (gl.glEnable)(cap);
    } else {
        (gl.glDisable)(cap);
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct FramePassParams {
    pub proj_offset: i64,
    pub proj_size: i64,
    pub light_offset: i64,
    pub light_size: i64,
    pub fog_offset: i64,
    pub fog_size: i64,
    pub fbo_id: u32,
    pub viewport_w: i32,
    pub viewport_h: i32,
    pub proj_ubo: u32,
    pub light_ubo: u32,
    pub fog_ubo: u32,
    pub overlay_tex_id: u32,
    pub lightmap_tex_id: u32,
    pub model_view: [f32; 16],
}

const _: () = assert!(size_of::<FramePassParams>() == 144);
const _: () = assert!(size_of::<EntityVertex>() == 32);
const _: () = assert!(size_of::<EntityInstance>() == 112);
const _: () = assert!(size_of::<TextVertex>() == 24);
const _: () = assert!(size_of::<TextInstance>() == 96);

pub struct GpuInstancer {
    entity_prog: GLuint,
    entity_u_mv: GLint,
    text_prog: GLuint,
    text_u_mv: GLint,
    entity_vao: GLuint,
    text_vao: GLuint,
    instance_id_vbo: GLuint,
    nearest_sampler: GLuint,

    entity_geom: MegaGeometry,
    text_geom: MegaGeometry,

    entity_ssbo: RingBuffer,
    text_ssbo: RingBuffer,
    indirect_buf: RingBuffer,
    ring_slot: usize,

    sorted_entities: Vec<EntityInstance>,
    sorted_texts: Vec<TextInstance>,
    cmds: Vec<DrawElementsIndirectCommand>,
}

unsafe impl Send for GpuInstancer {}

impl GpuInstancer {
    pub unsafe fn new(gl: &GlApi) -> Result<Self, String> {
        if !gl.supports_persistent_ssbo_mdi() {
            return Err("OpenGL 4.3/4.4 MDI/BufferStorage not available".to_string());
        }

        let mut prev_vao = 0;
        let mut prev_vbo = 0;
        let mut prev_ebo = 0;
        let mut prev_prog = 0;
        (gl.glGetIntegerv)(GL_VERTEX_ARRAY_BINDING, &mut prev_vao);
        (gl.glGetIntegerv)(GL_ARRAY_BUFFER_BINDING, &mut prev_vbo);
        (gl.glGetIntegerv)(GL_ELEMENT_ARRAY_BUFFER_BINDING, &mut prev_ebo);
        (gl.glGetIntegerv)(GL_CURRENT_PROGRAM, &mut prev_prog);

        let entity_prog = compile_program(gl, ENTITY_VERT_SRC, ENTITY_FRAG_SRC)?;
        let text_prog = match compile_program(gl, TEXT_VERT_SRC, TEXT_FRAG_SRC) {
            Ok(p) => p,
            Err(e) => {
                (gl.glDeleteProgram)(entity_prog);
                return Err(e);
            }
        };

        let entity_u_mv = configure_program_uniforms(gl, entity_prog, true);
        let text_u_mv = configure_program_uniforms(gl, text_prog, false);

        let mut instance_id_vbo: GLuint = 0;
        (gl.glGenBuffers)(1, &mut instance_id_vbo);
        let ids: Vec<u32> = (0..MAX_ENTITY_INSTANCES as u32).collect();
        (gl.glBindVertexArray)(0);
        (gl.glBindBuffer)(GL_ARRAY_BUFFER, instance_id_vbo);
        (gl.glBufferData)(
            GL_ARRAY_BUFFER,
            (ids.len() * size_of::<u32>()) as GLsizeiptr,
            ids.as_ptr() as *const c_void,
            GL_STATIC_DRAW,
        );

        let entity_geom = MegaGeometry::new(gl, size_of::<EntityVertex>(), 65536, 98304);
        let text_geom = MegaGeometry::new(gl, size_of::<TextVertex>(), 65536, 98304);

        let mut vaos = [0u32; 2];
        (gl.glGenVertexArrays)(2, vaos.as_mut_ptr());
        let (entity_vao, text_vao) = (vaos[0], vaos[1]);

        setup_entity_vao(gl, entity_vao, entity_geom.vbo, entity_geom.ebo, instance_id_vbo);
        setup_text_vao(gl, text_vao, text_geom.vbo, text_geom.ebo, instance_id_vbo);

        let mut nearest_sampler: GLuint = 0;
        (gl.glGenSamplers)(1, &mut nearest_sampler);
        (gl.glSamplerParameteri)(nearest_sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        (gl.glSamplerParameteri)(nearest_sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        (gl.glSamplerParameteri)(nearest_sampler, GL_TEXTURE_WRAP_S, GL_REPEAT);
        (gl.glSamplerParameteri)(nearest_sampler, GL_TEXTURE_WRAP_T, GL_REPEAT);

        let entity_ssbo = RingBuffer::new(
            gl,
            GL_SHADER_STORAGE_BUFFER,
            MAX_ENTITY_INSTANCES * size_of::<EntityInstance>(),
        )
        .ok_or_else(|| "Failed to allocate entity SSBO ring".to_string())?;
        let text_ssbo = RingBuffer::new(
            gl,
            GL_SHADER_STORAGE_BUFFER,
            MAX_TEXT_INSTANCES * size_of::<TextInstance>(),
        )
        .ok_or_else(|| "Failed to allocate text SSBO ring".to_string())?;
        let indirect_buf = RingBuffer::new(
            gl,
            GL_DRAW_INDIRECT_BUFFER,
            MAX_INDIRECT_CMDS * size_of::<DrawElementsIndirectCommand>(),
        )
        .ok_or_else(|| "Failed to allocate indirect command ring".to_string())?;

        (gl.glUseProgram)(prev_prog as GLuint);
        (gl.glBindVertexArray)(prev_vao as GLuint);
        (gl.glBindBuffer)(GL_ARRAY_BUFFER, prev_vbo as GLuint);
        if prev_vao != 0 {
            (gl.glBindBuffer)(GL_ELEMENT_ARRAY_BUFFER, prev_ebo as GLuint);
        }

        Ok(Self {
            entity_prog,
            entity_u_mv,
            text_prog,
            text_u_mv,
            entity_vao,
            text_vao,
            instance_id_vbo,
            nearest_sampler,
            entity_geom,
            text_geom,
            entity_ssbo,
            text_ssbo,
            indirect_buf,
            ring_slot: 0,
            sorted_entities: Vec::with_capacity(4096),
            sorted_texts: Vec::with_capacity(2048),
            cmds: Vec::with_capacity(512),
        })
    }

    pub unsafe fn reset_meshes(&mut self) {
        self.entity_geom.reset();
        self.text_geom.reset();
    }

    pub unsafe fn destroy(&mut self, gl: &GlApi) {
        self.entity_ssbo.destroy(gl, GL_SHADER_STORAGE_BUFFER);
        self.text_ssbo.destroy(gl, GL_SHADER_STORAGE_BUFFER);
        self.indirect_buf.destroy(gl, GL_DRAW_INDIRECT_BUFFER);
        self.entity_geom.destroy(gl);
        self.text_geom.destroy(gl);
        let vaos = [self.entity_vao, self.text_vao];
        (gl.glDeleteVertexArrays)(2, vaos.as_ptr());
        (gl.glDeleteBuffers)(1, &self.instance_id_vbo);
        (gl.glDeleteSamplers)(1, &self.nearest_sampler);
        (gl.glDeleteProgram)(self.entity_prog);
        (gl.glDeleteProgram)(self.text_prog);
    }

    pub unsafe fn register_entity_mesh(&mut self, gl: &GlApi, verts: &[EntityVertex]) -> i32 {
        let bytes = std::slice::from_raw_parts(
            verts.as_ptr() as *const u8,
            std::mem::size_of_val(verts),
        );
        let id_vbo = self.instance_id_vbo;
        let vao = self.entity_vao;
        self.entity_geom
            .register_quads(gl, bytes, verts.len(), vao, |g, v, vbo, ebo| {
                setup_entity_vao(g, v, vbo, ebo, id_vbo);
            })
    }

    pub unsafe fn register_text_mesh(&mut self, gl: &GlApi, verts: &[TextVertex]) -> i32 {
        let bytes = std::slice::from_raw_parts(
            verts.as_ptr() as *const u8,
            std::mem::size_of_val(verts),
        );
        let id_vbo = self.instance_id_vbo;
        let vao = self.text_vao;
        self.text_geom
            .register_quads(gl, bytes, verts.len(), vao, |g, v, vbo, ebo| {
                setup_text_vao(g, v, vbo, ebo, id_vbo);
            })
    }

    pub unsafe fn flush_frame(
        &mut self,
        gl: &GlApi,
        params: &FramePassParams,
        entities: &[EntityInstance],
        texts: &[TextInstance],
    ) {
        if entities.is_empty() && texts.is_empty() {
            return;
        }
        let ent_count = entities.len().min(MAX_ENTITY_INSTANCES);
        let txt_count = texts.len().min(MAX_TEXT_INSTANCES);
        let slot = self.ring_slot;
        self.ring_slot = (self.ring_slot + 1) % RING_SLOTS;

        let saved = SavedGlState::capture(gl);

        if ent_count > 0 {
            self.entity_ssbo.wait_slot(gl, slot);
        }
        if txt_count > 0 {
            self.text_ssbo.wait_slot(gl, slot);
        }
        self.indirect_buf.wait_slot(gl, slot);

        (gl.glBindFramebuffer)(GL_DRAW_FRAMEBUFFER, params.fbo_id);
        if params.viewport_w > 0 && params.viewport_h > 0 {
            (gl.glViewport)(0, 0, params.viewport_w, params.viewport_h);
        }
        (gl.glDisable)(GL_SCISSOR_TEST);
        (gl.glColorMask)(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);

        bind_ubo(gl, 0, params.proj_ubo, params.proj_offset, params.proj_size);
        bind_ubo(gl, 1, params.light_ubo, params.light_offset, params.light_size);
        bind_ubo(gl, 2, params.fog_ubo, params.fog_offset, params.fog_size);

        (gl.glActiveTexture)(GL_TEXTURE0 + 16);
        (gl.glBindTexture)(GL_TEXTURE_2D, params.overlay_tex_id);
        (gl.glBindSampler)(16, 0);
        (gl.glActiveTexture)(GL_TEXTURE0 + 17);
        (gl.glBindTexture)(GL_TEXTURE_2D, params.lightmap_tex_id);
        (gl.glBindSampler)(17, 0);

        let mut indirect_cursor = 0usize;

        if ent_count > 0 {
            self.draw_entities(
                gl,
                slot,
                params,
                &entities[..ent_count],
                &mut indirect_cursor,
            );
            self.entity_ssbo.fence_slot(gl, slot);
        }

        if txt_count > 0 {
            self.draw_texts(
                gl,
                slot,
                params,
                &texts[..txt_count],
                &mut indirect_cursor,
            );
            self.text_ssbo.fence_slot(gl, slot);
        }

        self.indirect_buf.fence_slot(gl, slot);
        saved.restore(gl);
    }

    unsafe fn draw_entities(
        &mut self,
        gl: &GlApi,
        slot: usize,
        params: &FramePassParams,
        raw: &[EntityInstance],
        indirect_cursor: &mut usize,
    ) {
        self.sorted_entities.clear();
        let max_mesh = self.entity_geom.meshes.len() as u32;
        for inst in raw {
            if inst.mesh_id > 0 && inst.mesh_id < max_mesh && inst.gl_tex_id != 0 {
                self.sorted_entities.push(*inst);
            }
        }
        if self.sorted_entities.is_empty() {
            return;
        }
        self.sorted_entities.sort_unstable_by_key(|e| {
            (
                (e.flags & ENT_FLAG_BLEND) != 0,
                (e.flags & ENT_FLAG_CULL_FACE) != 0,
                e.gl_tex_id,
                e.mesh_id,
            )
        });

        struct BatchSpan {
            blend: bool,
            cull: bool,
            textures: [GLuint; TEX_SLOTS_PER_BATCH],
            tex_count: usize,
            cmd_start: usize,
            cmd_count: usize,
        }

        let mut batches: Vec<BatchSpan> = Vec::with_capacity(8);
        self.cmds.clear();

        let total = self.sorted_entities.len();
        let mut i = 0usize;
        while i < total && self.cmds.len() < MAX_INDIRECT_CMDS {
            let blend = (self.sorted_entities[i].flags & ENT_FLAG_BLEND) != 0;
            let cull = (self.sorted_entities[i].flags & ENT_FLAG_CULL_FACE) != 0;
            let mut textures = [0u32; TEX_SLOTS_PER_BATCH];
            let mut tex_count = 0usize;
            let cmd_start = self.cmds.len();

            while i < total && self.cmds.len() < MAX_INDIRECT_CMDS {
                let e_blend = (self.sorted_entities[i].flags & ENT_FLAG_BLEND) != 0;
                let e_cull = (self.sorted_entities[i].flags & ENT_FLAG_CULL_FACE) != 0;
                if e_blend != blend || e_cull != cull {
                    break;
                }
                let tex = self.sorted_entities[i].gl_tex_id;
                let slot_idx = if let Some(pos) = textures[..tex_count].iter().position(|&t| t == tex) {
                    pos
                } else if tex_count < TEX_SLOTS_PER_BATCH {
                    let pos = tex_count;
                    textures[pos] = tex;
                    tex_count += 1;
                    pos
                } else {
                    break;
                };

                let mesh_id = self.sorted_entities[i].mesh_id;
                let run_start = i;
                while i < total {
                    let cur = &mut self.sorted_entities[i];
                    if ((cur.flags & ENT_FLAG_BLEND) != 0) != blend
                        || ((cur.flags & ENT_FLAG_CULL_FACE) != 0) != cull
                        || cur.gl_tex_id != tex
                        || cur.mesh_id != mesh_id
                    {
                        break;
                    }
                    cur.tex_slot = slot_idx as u32;
                    i += 1;
                }
                let mesh = self.entity_geom.meshes[mesh_id as usize];
                self.cmds.push(DrawElementsIndirectCommand {
                    count: mesh.index_count,
                    instance_count: (i - run_start) as u32,
                    first_index: mesh.first_index,
                    base_vertex: mesh.base_vertex,
                    base_instance: run_start as u32,
                });
            }

            let cmd_count = self.cmds.len() - cmd_start;
            if cmd_count > 0 {
                batches.push(BatchSpan {
                    blend,
                    cull,
                    textures,
                    tex_count,
                    cmd_start,
                    cmd_count,
                });
            }
        }

        if batches.is_empty() {
            return;
        }

        let inst_bytes = std::slice::from_raw_parts(
            self.sorted_entities.as_ptr() as *const u8,
            self.sorted_entities.len() * size_of::<EntityInstance>(),
        );
        let ssbo_offset = self
            .entity_ssbo
            .write_slot(gl, GL_SHADER_STORAGE_BUFFER, slot, inst_bytes);

        let cmd_bytes_len = self.cmds.len() * size_of::<DrawElementsIndirectCommand>();
        let cmd_bytes =
            std::slice::from_raw_parts(self.cmds.as_ptr() as *const u8, cmd_bytes_len);
        let indirect_base_offset =
            self.indirect_buf
                .write_slot(gl, GL_DRAW_INDIRECT_BUFFER, slot, cmd_bytes);

        (gl.glUseProgram)(self.entity_prog);
        (gl.glUniformMatrix4fv)(self.entity_u_mv, 1, GL_FALSE, params.model_view.as_ptr());
        (gl.glBindVertexArray)(self.entity_vao);
        (gl.glBindBufferRange)(
            GL_SHADER_STORAGE_BUFFER,
            0,
            self.entity_ssbo.handle,
            ssbo_offset as GLintptr,
            inst_bytes.len() as GLsizeiptr,
        );
        (gl.glBindBuffer)(GL_DRAW_INDIRECT_BUFFER, self.indirect_buf.handle);

        (gl.glEnable)(GL_DEPTH_TEST);
        (gl.glDepthMask)(GL_TRUE);
        (gl.glDepthFunc)(GL_LEQUAL);
        (gl.glDisable)(GL_POLYGON_OFFSET_FILL);
        (gl.glBlendFuncSeparate)(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        let mdi = gl.glMultiDrawElementsIndirect.unwrap();
        let mut draws = 0u64;
        for b in &batches {
            set_enabled(gl, GL_BLEND, b.blend);
            set_enabled(gl, GL_CULL_FACE, b.cull);
            for (u, &tex) in b.textures.iter().enumerate().take(b.tex_count) {
                (gl.glActiveTexture)(GL_TEXTURE0 + u as u32);
                (gl.glBindTexture)(GL_TEXTURE_2D, tex);
                (gl.glBindSampler)(u as u32, self.nearest_sampler);
            }
            let byte_off =
                indirect_base_offset + b.cmd_start * size_of::<DrawElementsIndirectCommand>();
            mdi(
                GL_TRIANGLES,
                GL_UNSIGNED_INT,
                byte_off as *const c_void,
                b.cmd_count as GLsizei,
                size_of::<DrawElementsIndirectCommand>() as GLsizei,
            );
            draws += 1;
        }

        *indirect_cursor += self.cmds.len();
        ENTITY_INSTANCES_DRAWN.fetch_add(self.sorted_entities.len() as u64, Ordering::Relaxed);
        ENTITY_DRAW_CALLS.fetch_add(draws, Ordering::Relaxed);
    }

    unsafe fn draw_texts(
        &mut self,
        gl: &GlApi,
        slot: usize,
        params: &FramePassParams,
        raw: &[TextInstance],
        indirect_cursor: &mut usize,
    ) {
        self.sorted_texts.clear();
        let max_mesh = self.text_geom.meshes.len() as u32;
        for inst in raw {
            if inst.mesh_id > 0 && inst.mesh_id < max_mesh {
                self.sorted_texts.push(*inst);
            }
        }
        if self.sorted_texts.is_empty() {
            return;
        }

        #[inline(always)]
        fn text_layer(flags: u32) -> u8 {
            let is_bg = (flags & TXT_FLAG_NO_TEX) != 0;
            if (flags & TXT_FLAG_SEE_THROUGH) != 0 {
                if is_bg { 0 } else { 1 }
            } else if (flags & TXT_FLAG_POLYGON_OFFSET) != 0 {
                if is_bg { 4 } else { 5 }
            } else if is_bg {
                2
            } else {
                3
            }
        }

        self.sorted_texts.sort_by_key(|t| {
            let layer = text_layer(t.flags);
            if layer <= 1 {
                (layer, 0u32, 0u32)
            } else {
                (layer, t.gl_tex_id, t.mesh_id)
            }
        });

        struct TextBatch {
            layer: u8,
            textures: [GLuint; TEX_SLOTS_PER_BATCH],
            tex_count: usize,
            cmd_start: usize,
            cmd_count: usize,
        }

        let mut batches: Vec<TextBatch> = Vec::with_capacity(8);
        self.cmds.clear();

        let remaining_cmds = MAX_INDIRECT_CMDS.saturating_sub(*indirect_cursor);
        let total = self.sorted_texts.len();
        let mut i = 0usize;
        while i < total && self.cmds.len() < remaining_cmds {
            let layer = text_layer(self.sorted_texts[i].flags);
            let mut textures = [0u32; TEX_SLOTS_PER_BATCH];
            let mut tex_count = 0usize;
            let cmd_start = self.cmds.len();

            while i < total && self.cmds.len() < remaining_cmds {
                if text_layer(self.sorted_texts[i].flags) != layer {
                    break;
                }
                let tex = self.sorted_texts[i].gl_tex_id;
                let slot_idx = if tex == 0 {
                    0
                } else if let Some(pos) = textures[..tex_count].iter().position(|&t| t == tex) {
                    pos
                } else if tex_count < TEX_SLOTS_PER_BATCH {
                    let pos = tex_count;
                    textures[pos] = tex;
                    tex_count += 1;
                    pos
                } else {
                    break;
                };

                let mesh_id = self.sorted_texts[i].mesh_id;
                let run_start = i;
                while i < total {
                    let cur = &mut self.sorted_texts[i];
                    if text_layer(cur.flags) != layer
                        || cur.gl_tex_id != tex
                        || cur.mesh_id != mesh_id
                    {
                        break;
                    }
                    cur.tex_slot = slot_idx as u32;
                    i += 1;
                }
                let mesh = self.text_geom.meshes[mesh_id as usize];
                self.cmds.push(DrawElementsIndirectCommand {
                    count: mesh.index_count,
                    instance_count: (i - run_start) as u32,
                    first_index: mesh.first_index,
                    base_vertex: mesh.base_vertex,
                    base_instance: run_start as u32,
                });
            }

            let cmd_count = self.cmds.len() - cmd_start;
            if cmd_count > 0 {
                batches.push(TextBatch {
                    layer,
                    textures,
                    tex_count,
                    cmd_start,
                    cmd_count,
                });
            }
        }

        if batches.is_empty() {
            return;
        }

        let inst_bytes = std::slice::from_raw_parts(
            self.sorted_texts.as_ptr() as *const u8,
            self.sorted_texts.len() * size_of::<TextInstance>(),
        );
        let ssbo_offset = self
            .text_ssbo
            .write_slot(gl, GL_SHADER_STORAGE_BUFFER, slot, inst_bytes);

        let cmd_byte_off_in_slot = *indirect_cursor * size_of::<DrawElementsIndirectCommand>();
        let cmd_bytes = std::slice::from_raw_parts(
            self.cmds.as_ptr() as *const u8,
            self.cmds.len() * size_of::<DrawElementsIndirectCommand>(),
        );
        let slot_base = slot * self.indirect_buf.slot_bytes;
        if self.indirect_buf.persistent && !self.indirect_buf.mapped.is_null() {
            ptr::copy_nonoverlapping(
                cmd_bytes.as_ptr(),
                self.indirect_buf.mapped.add(slot_base + cmd_byte_off_in_slot),
                cmd_bytes.len(),
            );
        } else {
            (gl.glBindBuffer)(GL_DRAW_INDIRECT_BUFFER, self.indirect_buf.handle);
            (gl.glBufferSubData)(
                GL_DRAW_INDIRECT_BUFFER,
                (slot_base + cmd_byte_off_in_slot) as GLintptr,
                cmd_bytes.len() as GLsizeiptr,
                cmd_bytes.as_ptr() as *const c_void,
            );
        }

        (gl.glUseProgram)(self.text_prog);
        (gl.glUniformMatrix4fv)(self.text_u_mv, 1, GL_FALSE, params.model_view.as_ptr());
        (gl.glBindVertexArray)(self.text_vao);
        (gl.glBindBufferRange)(
            GL_SHADER_STORAGE_BUFFER,
            0,
            self.text_ssbo.handle,
            ssbo_offset as GLintptr,
            inst_bytes.len() as GLsizeiptr,
        );
        (gl.glBindBuffer)(GL_DRAW_INDIRECT_BUFFER, self.indirect_buf.handle);

        (gl.glEnable)(GL_BLEND);
        (gl.glBlendFuncSeparate)(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        (gl.glDisable)(GL_CULL_FACE);
        (gl.glDepthFunc)(GL_LEQUAL);

        let mdi = gl.glMultiDrawElementsIndirect.unwrap();
        let mut draws = 0u64;
        for b in &batches {
            match b.layer {
                0 | 1 => {
                    (gl.glDisable)(GL_DEPTH_TEST);
                    (gl.glDepthMask)(GL_FALSE);
                    (gl.glDisable)(GL_POLYGON_OFFSET_FILL);
                }
                2 => {
                    (gl.glEnable)(GL_DEPTH_TEST);
                    (gl.glDepthMask)(GL_FALSE);
                    (gl.glDisable)(GL_POLYGON_OFFSET_FILL);
                }
                3 => {
                    (gl.glEnable)(GL_DEPTH_TEST);
                    (gl.glDepthMask)(GL_TRUE);
                    (gl.glDisable)(GL_POLYGON_OFFSET_FILL);
                }
                4 => {
                    (gl.glEnable)(GL_DEPTH_TEST);
                    (gl.glDepthMask)(GL_FALSE);
                    (gl.glEnable)(GL_POLYGON_OFFSET_FILL);
                    (gl.glPolygonOffset)(-1.0, -10.0);
                }
                _ => {
                    (gl.glEnable)(GL_DEPTH_TEST);
                    (gl.glDepthMask)(GL_TRUE);
                    (gl.glEnable)(GL_POLYGON_OFFSET_FILL);
                    (gl.glPolygonOffset)(-1.0, -10.0);
                }
            }
            for (u, &tex) in b.textures.iter().enumerate().take(b.tex_count) {
                (gl.glActiveTexture)(GL_TEXTURE0 + u as u32);
                (gl.glBindTexture)(GL_TEXTURE_2D, tex);
                (gl.glBindSampler)(u as u32, self.nearest_sampler);
            }
            let byte_off = slot_base
                + cmd_byte_off_in_slot
                + b.cmd_start * size_of::<DrawElementsIndirectCommand>();
            mdi(
                GL_TRIANGLES,
                GL_UNSIGNED_INT,
                byte_off as *const c_void,
                b.cmd_count as GLsizei,
                size_of::<DrawElementsIndirectCommand>() as GLsizei,
            );
            draws += 1;
        }

        *indirect_cursor += self.cmds.len();
        TEXT_INSTANCES_DRAWN.fetch_add(self.sorted_texts.len() as u64, Ordering::Relaxed);
        TEXT_DRAW_CALLS.fetch_add(draws, Ordering::Relaxed);
    }
}

unsafe fn bind_ubo(gl: &GlApi, slot: GLuint, buf: GLuint, offset: i64, size: i64) {
    if buf == 0 {
        return;
    }
    if size > 0 {
        (gl.glBindBufferRange)(
            GL_UNIFORM_BUFFER,
            slot,
            buf,
            offset as GLintptr,
            size as GLsizeiptr,
        );
    } else {
        (gl.glBindBufferBase)(GL_UNIFORM_BUFFER, slot, buf);
    }
}

unsafe fn setup_entity_vao(
    gl: &GlApi,
    vao: GLuint,
    vbo: GLuint,
    ebo: GLuint,
    instance_id_vbo: GLuint,
) {
    (gl.glBindVertexArray)(vao);
    (gl.glBindBuffer)(GL_ARRAY_BUFFER, vbo);
    (gl.glBindBuffer)(GL_ELEMENT_ARRAY_BUFFER, ebo);
    let stride = size_of::<EntityVertex>() as GLsizei;
    (gl.glEnableVertexAttribArray)(0);
    (gl.glVertexAttribPointer)(0, 3, GL_FLOAT, GL_FALSE, stride, ptr::null());
    (gl.glEnableVertexAttribArray)(1);
    (gl.glVertexAttribPointer)(1, 2, GL_FLOAT, GL_FALSE, stride, 12 as *const c_void);
    (gl.glEnableVertexAttribArray)(2);
    (gl.glVertexAttribPointer)(2, 3, GL_FLOAT, GL_FALSE, stride, 20 as *const c_void);
    (gl.glBindBuffer)(GL_ARRAY_BUFFER, instance_id_vbo);
    (gl.glEnableVertexAttribArray)(3);
    (gl.glVertexAttribIPointer)(3, 1, GL_UNSIGNED_INT, 4, ptr::null());
    (gl.glVertexAttribDivisor)(3, 1);
    (gl.glBindVertexArray)(0);
}

unsafe fn setup_text_vao(
    gl: &GlApi,
    vao: GLuint,
    vbo: GLuint,
    ebo: GLuint,
    instance_id_vbo: GLuint,
) {
    (gl.glBindVertexArray)(vao);
    (gl.glBindBuffer)(GL_ARRAY_BUFFER, vbo);
    (gl.glBindBuffer)(GL_ELEMENT_ARRAY_BUFFER, ebo);
    let stride = size_of::<TextVertex>() as GLsizei;
    (gl.glEnableVertexAttribArray)(0);
    (gl.glVertexAttribPointer)(0, 3, GL_FLOAT, GL_FALSE, stride, ptr::null());
    (gl.glEnableVertexAttribArray)(1);
    (gl.glVertexAttribPointer)(1, 2, GL_FLOAT, GL_FALSE, stride, 12 as *const c_void);
    (gl.glEnableVertexAttribArray)(2);
    (gl.glVertexAttribIPointer)(2, 1, GL_UNSIGNED_INT, stride, 20 as *const c_void);
    (gl.glBindBuffer)(GL_ARRAY_BUFFER, instance_id_vbo);
    (gl.glEnableVertexAttribArray)(3);
    (gl.glVertexAttribIPointer)(3, 1, GL_UNSIGNED_INT, 4, ptr::null());
    (gl.glVertexAttribDivisor)(3, 1);
    (gl.glBindVertexArray)(0);
}

unsafe fn configure_program_uniforms(gl: &GlApi, prog: GLuint, has_overlay: bool) -> GLint {
    (gl.glUseProgram)(prog);
    for (name, binding) in [
        (c"Projection", 0u32),
        (c"Lighting", 1u32),
        (c"Fog", 2u32),
    ] {
        let idx = (gl.glGetUniformBlockIndex)(prog, name.as_ptr());
        if idx != 0xFFFF_FFFF {
            (gl.glUniformBlockBinding)(prog, idx, binding);
        }
    }
    let tex_loc = (gl.glGetUniformLocation)(prog, c"uTex".as_ptr());
    if tex_loc >= 0 {
        let units: [GLint; 16] = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
        (gl.glUniform1iv)(tex_loc, 16, units.as_ptr());
    }
    if has_overlay {
        let ov_loc = (gl.glGetUniformLocation)(prog, c"uOverlayTex".as_ptr());
        if ov_loc >= 0 {
            (gl.glUniform1i)(ov_loc, 16);
        }
    }
    let lm_loc = (gl.glGetUniformLocation)(prog, c"uLightMap".as_ptr());
    if lm_loc >= 0 {
        (gl.glUniform1i)(lm_loc, 17);
    }
    (gl.glGetUniformLocation)(prog, c"uModelViewMat".as_ptr())
}

unsafe fn compile_shader(gl: &GlApi, kind: GLenum, src: &str) -> Result<GLuint, String> {
    let shader = (gl.glCreateShader)(kind);
    let csrc = CString::new(src).map_err(|e| e.to_string())?;
    let ptrs = [csrc.as_ptr()];
    (gl.glShaderSource)(shader, 1, ptrs.as_ptr(), ptr::null());
    (gl.glCompileShader)(shader);
    let mut ok: GLint = 0;
    (gl.glGetShaderiv)(shader, GL_COMPILE_STATUS, &mut ok);
    if ok == 0 {
        let mut len: GLint = 0;
        (gl.glGetShaderiv)(shader, GL_INFO_LOG_LENGTH, &mut len);
        let mut buf = vec![0u8; (len.max(1)) as usize];
        let mut written: GLsizei = 0;
        (gl.glGetShaderInfoLog)(shader, len.max(1), &mut written, buf.as_mut_ptr() as *mut c_char);
        (gl.glDeleteShader)(shader);
        let log = CStr::from_bytes_until_nul(&buf)
            .map(|c| c.to_string_lossy().into_owned())
            .unwrap_or_else(|_| String::from_utf8_lossy(&buf).into_owned());
        return Err(format!("Shader compile error: {log}"));
    }
    Ok(shader)
}

unsafe fn compile_program(gl: &GlApi, vs_src: &str, fs_src: &str) -> Result<GLuint, String> {
    let vs = compile_shader(gl, GL_VERTEX_SHADER, vs_src)?;
    let fs = match compile_shader(gl, GL_FRAGMENT_SHADER, fs_src) {
        Ok(f) => f,
        Err(e) => {
            (gl.glDeleteShader)(vs);
            return Err(e);
        }
    };
    let prog = (gl.glCreateProgram)();
    (gl.glAttachShader)(prog, vs);
    (gl.glAttachShader)(prog, fs);
    (gl.glLinkProgram)(prog);
    (gl.glDeleteShader)(vs);
    (gl.glDeleteShader)(fs);

    let mut ok: GLint = 0;
    (gl.glGetProgramiv)(prog, GL_LINK_STATUS, &mut ok);
    if ok == 0 {
        let mut len: GLint = 0;
        (gl.glGetProgramiv)(prog, GL_INFO_LOG_LENGTH, &mut len);
        let mut buf = vec![0u8; (len.max(1)) as usize];
        let mut written: GLsizei = 0;
        (gl.glGetProgramInfoLog)(prog, len.max(1), &mut written, buf.as_mut_ptr() as *mut c_char);
        (gl.glDeleteProgram)(prog);
        let log = CStr::from_bytes_until_nul(&buf)
            .map(|c| c.to_string_lossy().into_owned())
            .unwrap_or_else(|_| String::from_utf8_lossy(&buf).into_owned());
        return Err(format!("Program link error: {log}"));
    }
    Ok(prog)
}

const ENTITY_VERT_SRC: &str = r#"#version 430 core

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec2 inUv;
layout(location = 2) in vec3 inNormal;
layout(location = 3) in uint inInstanceId;

struct EntityInstance {
    mat4 pose;
    vec4 uvRect;
    uint meshId;
    uint glTexId;
    uint packedLight;
    uint packedOverlay;
    uint argbColor;
    uint flags;
    uint texSlot;
    uint pad0;
};

layout(std430, binding = 0) readonly buffer InstanceBuffer {
    EntityInstance instances[];
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
};

uniform mat4 uModelViewMat;
uniform sampler2D uOverlayTex;
uniform sampler2D uLightMap;

out float vSphericalDist;
out float vCylindricalDist;
out vec4 vColorFront;
out vec4 vColorBack;
out vec4 vLightMap;
out vec4 vOverlay;
out vec2 vTexCoord;
flat out uint vTexSlot;
flat out uint vFlags;

vec4 unpackArgb(uint c) {
    return vec4(
        float((c >> 16u) & 0xFFu),
        float((c >> 8u) & 0xFFu),
        float(c & 0xFFu),
        float((c >> 24u) & 0xFFu)
    ) * (1.0 / 255.0);
}

vec4 mixLightSeparate(vec2 l, vec4 col) {
    vec2 lv = max(vec2(0.0), l);
    float acc = min(1.0, (lv.x + lv.y) * 0.6 + 0.4);
    return vec4(col.rgb * acc, col.a);
}

void main() {
    EntityInstance inst = instances[inInstanceId];
    vec4 posWorld = inst.pose * vec4(inPos, 1.0);
    gl_Position = ProjMat * uModelViewMat * posWorld;

    vSphericalDist = length(posWorld.xyz);
    vCylindricalDist = max(length(posWorld.xz), abs(posWorld.y));

    mat3 m3 = mat3(inst.pose);
    float det = determinant(m3);
    vec3 nrm = (abs(det) > 1e-8) ? normalize(transpose(inverse(m3)) * inNormal) : inNormal;

    vec4 baseColor = unpackArgb(inst.argbColor);
    uint flags = inst.flags;
    if ((flags & 16u) != 0u) {
        vec2 l = vec2(dot(Light0_Direction, nrm), dot(Light1_Direction, nrm));
        vColorFront = mixLightSeparate(l, baseColor);
        vColorBack = mixLightSeparate(-l, baseColor);
    } else if ((flags & 8u) != 0u) {
        vColorFront = baseColor;
        vColorBack = baseColor;
    } else {
        vec2 l = vec2(dot(Light0_Direction, nrm), dot(Light1_Direction, nrm));
        vec4 lit = mixLightSeparate(l, baseColor);
        vColorFront = lit;
        vColorBack = lit;
    }

    if ((flags & 32u) != 0u) {
        vLightMap = vec4(1.0);
    } else {
        ivec2 uv2 = ivec2(int(inst.packedLight & 0xFFFFu), int((inst.packedLight >> 16u) & 0xFFFFu));
        vLightMap = texelFetch(uLightMap, uv2 / 16, 0);
    }

    if ((flags & 64u) != 0u) {
        vOverlay = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        ivec2 uv1 = ivec2(int(inst.packedOverlay & 0xFFFFu), int((inst.packedOverlay >> 16u) & 0xFFFFu));
        vOverlay = texelFetch(uOverlayTex, uv1, 0);
    }

    vTexCoord = inst.uvRect.xy + inUv * inst.uvRect.zw;
    vTexSlot = inst.texSlot;
    vFlags = flags;
}
"#;

const ENTITY_FRAG_SRC: &str = r#"#version 430 core

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

uniform sampler2D uTex[16];

in float vSphericalDist;
in float vCylindricalDist;
in vec4 vColorFront;
in vec4 vColorBack;
in vec4 vLightMap;
in vec4 vOverlay;
in vec2 vTexCoord;
flat in uint vTexSlot;
flat in uint vFlags;

out vec4 fragColor;

vec4 sampleSlot(uint slot, vec2 uv) {
    switch (slot) {
        case 0u:  return texture(uTex[0], uv);
        case 1u:  return texture(uTex[1], uv);
        case 2u:  return texture(uTex[2], uv);
        case 3u:  return texture(uTex[3], uv);
        case 4u:  return texture(uTex[4], uv);
        case 5u:  return texture(uTex[5], uv);
        case 6u:  return texture(uTex[6], uv);
        case 7u:  return texture(uTex[7], uv);
        case 8u:  return texture(uTex[8], uv);
        case 9u:  return texture(uTex[9], uv);
        case 10u: return texture(uTex[10], uv);
        case 11u: return texture(uTex[11], uv);
        case 12u: return texture(uTex[12], uv);
        case 13u: return texture(uTex[13], uv);
        case 14u: return texture(uTex[14], uv);
        default:  return texture(uTex[15], uv);
    }
}

float linearFog(float d, float s, float e) {
    if (d <= s) return 0.0;
    if (d >= e) return 1.0;
    return (d - s) / (e - s);
}

void main() {
    vec4 color = sampleSlot(vTexSlot, vTexCoord);
    if ((vFlags & 2u) != 0u && color.a < 0.1) discard;
    if ((vFlags & 4u) != 0u && color.a < 0.5) discard;

    vec4 vcol = ((vFlags & 16u) != 0u) ? (gl_FrontFacing ? vColorFront : vColorBack) : vColorFront;
    color *= vcol;
    if ((vFlags & 64u) == 0u) {
        color.rgb = mix(vOverlay.rgb, color.rgb, vOverlay.a);
    }
    if ((vFlags & 32u) == 0u) {
        color *= vLightMap;
    }

    float fogV = max(
        linearFog(vSphericalDist, FogEnvironmentalStart, FogEnvironmentalEnd),
        linearFog(vCylindricalDist, FogRenderDistanceStart, FogRenderDistanceEnd)
    );
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fogV * FogColor.a), color.a);
}
"#;

const TEXT_VERT_SRC: &str = r#"#version 430 core

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec2 inUv;
layout(location = 2) in uint inAbgr;
layout(location = 3) in uint inInstanceId;

struct TextInstance {
    mat4 pose;
    uint meshId;
    uint glTexId;
    uint packedLight;
    uint flags;
    uint texSlot;
    uint pad0;
    uint pad1;
    uint pad2;
};

layout(std430, binding = 0) readonly buffer TextInstanceBuffer {
    TextInstance instances[];
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

uniform mat4 uModelViewMat;
uniform sampler2D uLightMap;

out float vSphericalDist;
out float vCylindricalDist;
out vec4 vColor;
out vec2 vTexCoord;
flat out uint vTexSlot;
flat out uint vFlags;

vec4 unpackAbgr(uint c) {
    return vec4(
        float(c & 0xFFu),
        float((c >> 8u) & 0xFFu),
        float((c >> 16u) & 0xFFu),
        float((c >> 24u) & 0xFFu)
    ) * (1.0 / 255.0);
}

void main() {
    TextInstance inst = instances[inInstanceId];
    vec4 posWorld = inst.pose * vec4(inPos, 1.0);
    gl_Position = ProjMat * uModelViewMat * posWorld;

    vSphericalDist = length(posWorld.xyz);
    vCylindricalDist = max(length(posWorld.xz), abs(posWorld.y));

    vec4 baseColor = unpackAbgr(inAbgr);
    if ((inst.flags & 1u) != 0u) {
        vColor = baseColor;
    } else {
        ivec2 uv2 = ivec2(int(inst.packedLight & 0xFFFFu), int((inst.packedLight >> 16u) & 0xFFFFu));
        vec4 lm = texelFetch(uLightMap, uv2 / 16, 0);
        vColor = baseColor * lm;
    }
    vTexCoord = inUv;
    vTexSlot = inst.texSlot;
    vFlags = inst.flags;
}
"#;

const TEXT_FRAG_SRC: &str = r#"#version 430 core

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

uniform sampler2D uTex[16];

in float vSphericalDist;
in float vCylindricalDist;
in vec4 vColor;
in vec2 vTexCoord;
flat in uint vTexSlot;
flat in uint vFlags;

out vec4 fragColor;

vec4 sampleSlot(uint slot, vec2 uv) {
    switch (slot) {
        case 0u:  return texture(uTex[0], uv);
        case 1u:  return texture(uTex[1], uv);
        case 2u:  return texture(uTex[2], uv);
        case 3u:  return texture(uTex[3], uv);
        case 4u:  return texture(uTex[4], uv);
        case 5u:  return texture(uTex[5], uv);
        case 6u:  return texture(uTex[6], uv);
        case 7u:  return texture(uTex[7], uv);
        case 8u:  return texture(uTex[8], uv);
        case 9u:  return texture(uTex[9], uv);
        case 10u: return texture(uTex[10], uv);
        case 11u: return texture(uTex[11], uv);
        case 12u: return texture(uTex[12], uv);
        case 13u: return texture(uTex[13], uv);
        case 14u: return texture(uTex[14], uv);
        default:  return texture(uTex[15], uv);
    }
}

float linearFog(float d, float s, float e) {
    if (d <= s) return 0.0;
    if (d >= e) return 1.0;
    return (d - s) / (e - s);
}

void main() {
    vec4 color;
    if ((vFlags & 4u) != 0u) {
        color = vColor;
    } else if ((vFlags & 8u) != 0u) {
        color = sampleSlot(vTexSlot, vTexCoord).rrrr * vColor;
    } else {
        color = sampleSlot(vTexSlot, vTexCoord) * vColor;
    }
    if (color.a < 0.1) discard;

    if ((vFlags & 1u) != 0u) {
        fragColor = color;
    } else {
        float fogV = max(
            linearFog(vSphericalDist, FogEnvironmentalStart, FogEnvironmentalEnd),
            linearFog(vCylindricalDist, FogRenderDistanceStart, FogRenderDistanceEnd)
        );
        fragColor = vec4(mix(color.rgb, FogColor.rgb, fogV * FogColor.a), color.a);
    }
}
"#;
