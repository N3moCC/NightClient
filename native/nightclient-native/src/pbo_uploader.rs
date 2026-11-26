use crate::gl_api::*;
use std::ffi::c_void;
use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};

pub static PBO_UPLOADS: AtomicU64 = AtomicU64::new(0);
pub static PBO_BYTES: AtomicU64 = AtomicU64::new(0);

const RING_SEGMENTS: usize = 8;
const SEGMENT_SIZE: usize = 2 * 1024 * 1024;
const RING_SIZE: usize = RING_SEGMENTS * SEGMENT_SIZE;
const MAX_SINGLE_UPLOAD: usize = SEGMENT_SIZE - 256;

pub struct PboUploader {
    pbo: GLuint,
    mapped: *mut u8,
    persistent: bool,
    cursor: usize,
    segment_fences: [GLsync; RING_SEGMENTS],
}

unsafe impl Send for PboUploader {}

impl PboUploader {
    pub unsafe fn new(gl: &GlApi) -> Option<Self> {
        let mut pbo: GLuint = 0;
        (gl.glGenBuffers)(1, &mut pbo);
        if pbo == 0 {
            return None;
        }

        let mut prev_pbo: GLint = 0;
        (gl.glGetIntegerv)(GL_PIXEL_UNPACK_BUFFER_BINDING, &mut prev_pbo);
        (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, pbo);

        let mut mapped = ptr::null_mut();
        let mut persistent = false;

        if let Some(buf_storage) = gl.glBufferStorage {
            while (gl.glGetError)() != GL_NO_ERROR {}
            let flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
            buf_storage(
                GL_PIXEL_UNPACK_BUFFER,
                RING_SIZE as GLsizeiptr,
                ptr::null(),
                flags,
            );
            if (gl.glGetError)() == GL_NO_ERROR {
                let ptr_void = (gl.glMapBufferRange)(
                    GL_PIXEL_UNPACK_BUFFER,
                    0,
                    RING_SIZE as GLsizeiptr,
                    flags,
                );
                if !ptr_void.is_null() {
                    mapped = ptr_void as *mut u8;
                    persistent = true;
                }
            }
        }

        if !persistent {
            (gl.glBufferData)(
                GL_PIXEL_UNPACK_BUFFER,
                SEGMENT_SIZE as GLsizeiptr,
                ptr::null(),
                GL_STREAM_DRAW,
            );
        }

        (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, prev_pbo as GLuint);

        Some(Self {
            pbo,
            mapped,
            persistent,
            cursor: 0,
            segment_fences: [ptr::null_mut(); RING_SEGMENTS],
        })
    }

    pub unsafe fn destroy(&mut self, gl: &GlApi) {
        for f in &mut self.segment_fences {
            if !f.is_null() {
                (gl.glDeleteSync)(*f);
                *f = ptr::null_mut();
            }
        }
        if self.pbo != 0 {
            if self.persistent {
                let mut prev_pbo: GLint = 0;
                (gl.glGetIntegerv)(GL_PIXEL_UNPACK_BUFFER_BINDING, &mut prev_pbo);
                (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, self.pbo);
                (gl.glUnmapBuffer)(GL_PIXEL_UNPACK_BUFFER);
                (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, prev_pbo as GLuint);
            }
            (gl.glDeleteBuffers)(1, &self.pbo);
            self.pbo = 0;
            self.mapped = ptr::null_mut();
        }
    }

    #[allow(clippy::too_many_arguments)]
    pub unsafe fn upload_tex_sub_image_2d(
        &mut self,
        gl: &GlApi,
        gl_tex_id: GLuint,
        mip_level: GLint,
        dst_x: GLint,
        dst_y: GLint,
        width: GLsizei,
        height: GLsizei,
        src_ptr: *const u8,
        src_stride_px: usize,
        src_x: usize,
        src_y: usize,
        components: usize,
    ) -> bool {
        if self.pbo == 0
            || gl_tex_id == 0
            || mip_level < 0
            || dst_x < 0
            || dst_y < 0
            || src_ptr.is_null()
            || width <= 0
            || height <= 0
            || src_stride_px < src_x.saturating_add(width as usize)
        {
            return false;
        }
        let gl_format = match components {
            1 => GL_RED,
            2 => GL_RG,
            3 => GL_RGB,
            4 => GL_RGBA,
            _ => return false,
        };

        let row_bytes = (width as usize) * components;
        let total_bytes = row_bytes.saturating_mul(height as usize);
        if total_bytes == 0 || total_bytes > MAX_SINGLE_UPLOAD {
            return false;
        }

        let mut prev_pbo: GLint = 0;
        let mut prev_active_tex: GLint = 0;
        let mut prev_tex_2d: GLint = 0;
        let mut prev_row_len: GLint = 0;
        let mut prev_skip_rows: GLint = 0;
        let mut prev_skip_px: GLint = 0;
        let mut prev_align: GLint = 0;
        (gl.glGetIntegerv)(GL_PIXEL_UNPACK_BUFFER_BINDING, &mut prev_pbo);
        (gl.glGetIntegerv)(GL_ACTIVE_TEXTURE, &mut prev_active_tex);
        (gl.glGetIntegerv)(GL_TEXTURE_BINDING_2D, &mut prev_tex_2d);
        (gl.glGetIntegerv)(GL_UNPACK_ROW_LENGTH, &mut prev_row_len);
        (gl.glGetIntegerv)(GL_UNPACK_SKIP_ROWS, &mut prev_skip_rows);
        (gl.glGetIntegerv)(GL_UNPACK_SKIP_PIXELS, &mut prev_skip_px);
        (gl.glGetIntegerv)(GL_UNPACK_ALIGNMENT, &mut prev_align);

        (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, self.pbo);
        (gl.glBindTexture)(GL_TEXTURE_2D, gl_tex_id);
        (gl.glPixelStorei)(GL_UNPACK_ROW_LENGTH, 0);
        (gl.glPixelStorei)(GL_UNPACK_SKIP_ROWS, 0);
        (gl.glPixelStorei)(GL_UNPACK_SKIP_PIXELS, 0);
        (gl.glPixelStorei)(GL_UNPACK_ALIGNMENT, if components == 4 { 4 } else { 1 });

        let src_row_stride_bytes = src_stride_px * components;
        let src_base = src_ptr.add(src_y * src_row_stride_bytes + src_x * components);

        if self.persistent && !self.mapped.is_null() {
            let prev_seg = (self.cursor / SEGMENT_SIZE).min(RING_SEGMENTS - 1);
            let aligned = (self.cursor + 63) & !63;
            let offset = if aligned >= RING_SIZE {
                self.fence_segment(gl, prev_seg);
                self.wait_segment(gl, 0);
                0
            } else {
                let cur_seg = aligned / SEGMENT_SIZE;
                if cur_seg != prev_seg {
                    self.fence_segment(gl, prev_seg);
                    self.wait_segment(gl, cur_seg);
                    aligned
                } else if aligned + total_bytes > (cur_seg + 1) * SEGMENT_SIZE {
                    self.fence_segment(gl, cur_seg);
                    let next_seg = (cur_seg + 1) % RING_SEGMENTS;
                    self.wait_segment(gl, next_seg);
                    next_seg * SEGMENT_SIZE
                } else {
                    if !self.segment_fences[cur_seg].is_null() {
                        self.wait_segment(gl, cur_seg);
                    }
                    aligned
                }
            };

            let dst_base = self.mapped.add(offset);
            copy_rect(src_base, dst_base, row_bytes, src_row_stride_bytes, height as usize);

            (gl.glTexSubImage2D)(
                GL_TEXTURE_2D,
                mip_level,
                dst_x,
                dst_y,
                width,
                height,
                gl_format,
                GL_UNSIGNED_BYTE,
                offset as *const c_void,
            );
            let next_cursor = offset + total_bytes;
            if next_cursor >= RING_SIZE {
                self.fence_segment(gl, RING_SEGMENTS - 1);
                self.cursor = 0;
            } else {
                self.cursor = next_cursor;
            }
        } else {
            (gl.glBufferData)(
                GL_PIXEL_UNPACK_BUFFER,
                total_bytes as GLsizeiptr,
                ptr::null(),
                GL_STREAM_DRAW,
            );
            let ptr_void = (gl.glMapBufferRange)(
                GL_PIXEL_UNPACK_BUFFER,
                0,
                total_bytes as GLsizeiptr,
                GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT | GL_MAP_UNSYNCHRONIZED_BIT,
            );
            if ptr_void.is_null() {
                (gl.glPixelStorei)(GL_UNPACK_ROW_LENGTH, prev_row_len);
                (gl.glPixelStorei)(GL_UNPACK_SKIP_ROWS, prev_skip_rows);
                (gl.glPixelStorei)(GL_UNPACK_SKIP_PIXELS, prev_skip_px);
                (gl.glPixelStorei)(GL_UNPACK_ALIGNMENT, prev_align);
                (gl.glBindTexture)(GL_TEXTURE_2D, prev_tex_2d as GLuint);
                (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, prev_pbo as GLuint);
                return false;
            }
            copy_rect(
                src_base,
                ptr_void as *mut u8,
                row_bytes,
                src_row_stride_bytes,
                height as usize,
            );
            (gl.glUnmapBuffer)(GL_PIXEL_UNPACK_BUFFER);
            (gl.glTexSubImage2D)(
                GL_TEXTURE_2D,
                mip_level,
                dst_x,
                dst_y,
                width,
                height,
                gl_format,
                GL_UNSIGNED_BYTE,
                ptr::null(),
            );
        }

        (gl.glPixelStorei)(GL_UNPACK_ROW_LENGTH, prev_row_len);
        (gl.glPixelStorei)(GL_UNPACK_SKIP_ROWS, prev_skip_rows);
        (gl.glPixelStorei)(GL_UNPACK_SKIP_PIXELS, prev_skip_px);
        (gl.glPixelStorei)(GL_UNPACK_ALIGNMENT, prev_align);
        (gl.glBindTexture)(GL_TEXTURE_2D, prev_tex_2d as GLuint);
        (gl.glActiveTexture)(prev_active_tex as GLenum);
        (gl.glBindBuffer)(GL_PIXEL_UNPACK_BUFFER, prev_pbo as GLuint);

        PBO_UPLOADS.fetch_add(1, Ordering::Relaxed);
        PBO_BYTES.fetch_add(total_bytes as u64, Ordering::Relaxed);
        true
    }

    #[inline]
    unsafe fn fence_segment(&mut self, gl: &GlApi, seg: usize) {
        if !self.segment_fences[seg].is_null() {
            (gl.glDeleteSync)(self.segment_fences[seg]);
        }
        self.segment_fences[seg] = (gl.glFenceSync)(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    #[inline]
    unsafe fn wait_segment(&mut self, gl: &GlApi, seg: usize) {
        let fence = self.segment_fences[seg];
        if !fence.is_null() {
            let status = (gl.glClientWaitSync)(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 50_000_000);
            if status != GL_ALREADY_SIGNALED && status != GL_CONDITION_SATISFIED {
                let _ = (gl.glClientWaitSync)(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000);
            }
            (gl.glDeleteSync)(fence);
            self.segment_fences[seg] = ptr::null_mut();
        }
    }
}

#[inline(always)]
unsafe fn copy_rect(
    src: *const u8,
    dst: *mut u8,
    row_bytes: usize,
    src_stride_bytes: usize,
    rows: usize,
) {
    if row_bytes == src_stride_bytes {
        ptr::copy_nonoverlapping(src, dst, row_bytes * rows);
    } else {
        for r in 0..rows {
            ptr::copy_nonoverlapping(
                src.add(r * src_stride_bytes),
                dst.add(r * row_bytes),
                row_bytes,
            );
        }
    }
}
