#![allow(non_snake_case, dead_code)]

use std::ffi::{c_char, c_int, c_uint, c_void};

pub type GLenum = c_uint;
pub type GLboolean = u8;
pub type GLbitfield = c_uint;
pub type GLint = c_int;
pub type GLuint = c_uint;
pub type GLsizei = c_int;
pub type GLfloat = f32;
pub type GLsizeiptr = isize;
pub type GLintptr = isize;
pub type GLuint64 = u64;
pub type GLsync = *mut c_void;

pub const GL_FALSE: GLboolean = 0;
pub const GL_TRUE: GLboolean = 1;
pub const GL_NO_ERROR: GLenum = 0;
pub const GL_TRIANGLES: GLenum = 0x0004;
pub const GL_UNSIGNED_BYTE: GLenum = 0x1401;
pub const GL_INT: GLenum = 0x1404;
pub const GL_UNSIGNED_INT: GLenum = 0x1405;
pub const GL_FLOAT: GLenum = 0x1406;
pub const GL_RED: GLenum = 0x1903;
pub const GL_RGB: GLenum = 0x1907;
pub const GL_RGBA: GLenum = 0x1908;
pub const GL_RG: GLenum = 0x8227;
pub const GL_TEXTURE_2D: GLenum = 0x0DE1;
pub const GL_TEXTURE0: GLenum = 0x84C0;
pub const GL_NEAREST: GLint = 0x2600;
pub const GL_LINEAR: GLint = 0x2601;
pub const GL_REPEAT: GLint = 0x2901;
pub const GL_CLAMP_TO_EDGE: GLint = 0x812F;
pub const GL_TEXTURE_MAG_FILTER: GLenum = 0x2800;
pub const GL_TEXTURE_MIN_FILTER: GLenum = 0x2801;
pub const GL_TEXTURE_WRAP_S: GLenum = 0x2802;
pub const GL_TEXTURE_WRAP_T: GLenum = 0x2803;

pub const GL_ARRAY_BUFFER: GLenum = 0x8892;
pub const GL_ELEMENT_ARRAY_BUFFER: GLenum = 0x8893;
pub const GL_PIXEL_UNPACK_BUFFER: GLenum = 0x88EC;
pub const GL_UNIFORM_BUFFER: GLenum = 0x8A11;
pub const GL_SHADER_STORAGE_BUFFER: GLenum = 0x90D2;
pub const GL_DRAW_INDIRECT_BUFFER: GLenum = 0x8F3F;
pub const GL_COPY_READ_BUFFER: GLenum = 0x8F36;
pub const GL_COPY_WRITE_BUFFER: GLenum = 0x8F37;

pub const GL_STATIC_DRAW: GLenum = 0x88E4;
pub const GL_DYNAMIC_DRAW: GLenum = 0x88E8;
pub const GL_STREAM_DRAW: GLenum = 0x88E0;

pub const GL_MAP_WRITE_BIT: GLbitfield = 0x0002;
pub const GL_MAP_INVALIDATE_BUFFER_BIT: GLbitfield = 0x0008;
pub const GL_MAP_UNSYNCHRONIZED_BIT: GLbitfield = 0x0020;
pub const GL_MAP_PERSISTENT_BIT: GLbitfield = 0x0040;
pub const GL_MAP_COHERENT_BIT: GLbitfield = 0x0080;
pub const GL_DYNAMIC_STORAGE_BIT: GLbitfield = 0x0100;

pub const GL_VERTEX_SHADER: GLenum = 0x8B31;
pub const GL_FRAGMENT_SHADER: GLenum = 0x8B30;
pub const GL_COMPILE_STATUS: GLenum = 0x8B81;
pub const GL_LINK_STATUS: GLenum = 0x8B82;
pub const GL_INFO_LOG_LENGTH: GLenum = 0x8B84;

pub const GL_BLEND: GLenum = 0x0BE2;
pub const GL_CULL_FACE: GLenum = 0x0B44;
pub const GL_DEPTH_TEST: GLenum = 0x0B71;
pub const GL_SCISSOR_TEST: GLenum = 0x0C11;
pub const GL_POLYGON_OFFSET_FILL: GLenum = 0x8037;
pub const GL_LEQUAL: GLenum = 0x0203;
pub const GL_LESS: GLenum = 0x0201;
pub const GL_ALWAYS: GLenum = 0x0207;
pub const GL_SRC_ALPHA: GLenum = 0x0302;
pub const GL_ONE_MINUS_SRC_ALPHA: GLenum = 0x0303;
pub const GL_ONE: GLenum = 1;
pub const GL_ZERO: GLenum = 0;
pub const GL_BACK: GLenum = 0x0405;
pub const GL_CCW: GLenum = 0x0901;

pub const GL_FRAMEBUFFER: GLenum = 0x8D40;
pub const GL_DRAW_FRAMEBUFFER: GLenum = 0x8CA9;
pub const GL_DRAW_FRAMEBUFFER_BINDING: GLenum = 0x8CA6;
pub const GL_CURRENT_PROGRAM: GLenum = 0x8B8D;
pub const GL_VERTEX_ARRAY_BINDING: GLenum = 0x85B5;
pub const GL_ARRAY_BUFFER_BINDING: GLenum = 0x8894;
pub const GL_ELEMENT_ARRAY_BUFFER_BINDING: GLenum = 0x8895;
pub const GL_PIXEL_UNPACK_BUFFER_BINDING: GLenum = 0x88EF;
pub const GL_DRAW_INDIRECT_BUFFER_BINDING: GLenum = 0x8F43;
pub const GL_SHADER_STORAGE_BUFFER_BINDING: GLenum = 0x90D3;
pub const GL_UNIFORM_BUFFER_BINDING: GLenum = 0x8A28;
pub const GL_UNIFORM_BUFFER_START: GLenum = 0x8A29;
pub const GL_UNIFORM_BUFFER_SIZE: GLenum = 0x8A2A;
pub const GL_ACTIVE_TEXTURE: GLenum = 0x84E0;
pub const GL_TEXTURE_BINDING_2D: GLenum = 0x8069;
pub const GL_SAMPLER_BINDING: GLenum = 0x8919;
pub const GL_DEPTH_WRITEMASK: GLenum = 0x0B72;
pub const GL_DEPTH_FUNC: GLenum = 0x0B74;
pub const GL_BLEND_SRC_RGB: GLenum = 0x80C9;
pub const GL_BLEND_DST_RGB: GLenum = 0x80C8;
pub const GL_BLEND_SRC_ALPHA: GLenum = 0x80CB;
pub const GL_BLEND_DST_ALPHA: GLenum = 0x80CA;
pub const GL_VIEWPORT: GLenum = 0x0BA2;
pub const GL_COLOR_WRITEMASK: GLenum = 0x0C23;
pub const GL_UNPACK_ROW_LENGTH: GLenum = 0x0CF2;
pub const GL_UNPACK_SKIP_ROWS: GLenum = 0x0CF3;
pub const GL_UNPACK_SKIP_PIXELS: GLenum = 0x0CF4;
pub const GL_UNPACK_ALIGNMENT: GLenum = 0x0CF5;

pub const GL_SYNC_GPU_COMMANDS_COMPLETE: GLenum = 0x9117;
pub const GL_SYNC_FLUSH_COMMANDS_BIT: GLbitfield = 0x0000_0001;
pub const GL_ALREADY_SIGNALED: GLenum = 0x911A;
pub const GL_CONDITION_SATISFIED: GLenum = 0x911C;

pub const REQUIRED_FN_COUNT: usize = 66;

#[derive(Clone, Copy)]
pub struct GlApi {
    pub glGetError: unsafe extern "system" fn() -> GLenum,
    pub glGetIntegerv: unsafe extern "system" fn(GLenum, *mut GLint),
    pub glGetIntegeri_v: unsafe extern "system" fn(GLenum, GLuint, *mut GLint),
    pub glGetInteger64i_v: unsafe extern "system" fn(GLenum, GLuint, *mut i64),
    pub glGetBooleanv: unsafe extern "system" fn(GLenum, *mut GLboolean),
    pub glIsEnabled: unsafe extern "system" fn(GLenum) -> GLboolean,
    pub glEnable: unsafe extern "system" fn(GLenum),
    pub glDisable: unsafe extern "system" fn(GLenum),
    pub glDepthMask: unsafe extern "system" fn(GLboolean),
    pub glDepthFunc: unsafe extern "system" fn(GLenum),
    pub glColorMask: unsafe extern "system" fn(GLboolean, GLboolean, GLboolean, GLboolean),
    pub glBlendFuncSeparate: unsafe extern "system" fn(GLenum, GLenum, GLenum, GLenum),
    pub glPolygonOffset: unsafe extern "system" fn(GLfloat, GLfloat),
    pub glViewport: unsafe extern "system" fn(GLint, GLint, GLsizei, GLsizei),
    pub glBindFramebuffer: unsafe extern "system" fn(GLenum, GLuint),

    pub glGenVertexArrays: unsafe extern "system" fn(GLsizei, *mut GLuint),
    pub glDeleteVertexArrays: unsafe extern "system" fn(GLsizei, *const GLuint),
    pub glBindVertexArray: unsafe extern "system" fn(GLuint),

    pub glGenBuffers: unsafe extern "system" fn(GLsizei, *mut GLuint),
    pub glDeleteBuffers: unsafe extern "system" fn(GLsizei, *const GLuint),
    pub glBindBuffer: unsafe extern "system" fn(GLenum, GLuint),
    pub glBindBufferBase: unsafe extern "system" fn(GLenum, GLuint, GLuint),
    pub glBindBufferRange: unsafe extern "system" fn(GLenum, GLuint, GLuint, GLintptr, GLsizeiptr),
    pub glBufferData: unsafe extern "system" fn(GLenum, GLsizeiptr, *const c_void, GLenum),
    pub glBufferSubData: unsafe extern "system" fn(GLenum, GLintptr, GLsizeiptr, *const c_void),
    pub glCopyBufferSubData: unsafe extern "system" fn(GLenum, GLenum, GLintptr, GLintptr, GLsizeiptr),
    pub glMapBufferRange: unsafe extern "system" fn(GLenum, GLintptr, GLsizeiptr, GLbitfield) -> *mut c_void,
    pub glUnmapBuffer: unsafe extern "system" fn(GLenum) -> GLboolean,

    pub glEnableVertexAttribArray: unsafe extern "system" fn(GLuint),
    pub glVertexAttribPointer: unsafe extern "system" fn(GLuint, GLint, GLenum, GLboolean, GLsizei, *const c_void),
    pub glVertexAttribIPointer: unsafe extern "system" fn(GLuint, GLint, GLenum, GLsizei, *const c_void),
    pub glVertexAttribDivisor: unsafe extern "system" fn(GLuint, GLuint),

    pub glCreateShader: unsafe extern "system" fn(GLenum) -> GLuint,
    pub glShaderSource: unsafe extern "system" fn(GLuint, GLsizei, *const *const c_char, *const GLint),
    pub glCompileShader: unsafe extern "system" fn(GLuint),
    pub glGetShaderiv: unsafe extern "system" fn(GLuint, GLenum, *mut GLint),
    pub glGetShaderInfoLog: unsafe extern "system" fn(GLuint, GLsizei, *mut GLsizei, *mut c_char),
    pub glDeleteShader: unsafe extern "system" fn(GLuint),

    pub glCreateProgram: unsafe extern "system" fn() -> GLuint,
    pub glAttachShader: unsafe extern "system" fn(GLuint, GLuint),
    pub glLinkProgram: unsafe extern "system" fn(GLuint),
    pub glGetProgramiv: unsafe extern "system" fn(GLuint, GLenum, *mut GLint),
    pub glGetProgramInfoLog: unsafe extern "system" fn(GLuint, GLsizei, *mut GLsizei, *mut c_char),
    pub glDeleteProgram: unsafe extern "system" fn(GLuint),
    pub glUseProgram: unsafe extern "system" fn(GLuint),
    pub glGetUniformLocation: unsafe extern "system" fn(GLuint, *const c_char) -> GLint,
    pub glGetUniformBlockIndex: unsafe extern "system" fn(GLuint, *const c_char) -> GLuint,
    pub glUniformBlockBinding: unsafe extern "system" fn(GLuint, GLuint, GLuint),
    pub glUniform1i: unsafe extern "system" fn(GLint, GLint),
    pub glUniform1iv: unsafe extern "system" fn(GLint, GLsizei, *const GLint),
    pub glUniformMatrix4fv: unsafe extern "system" fn(GLint, GLsizei, GLboolean, *const GLfloat),

    pub glActiveTexture: unsafe extern "system" fn(GLenum),
    pub glBindTexture: unsafe extern "system" fn(GLenum, GLuint),
    pub glTexSubImage2D: unsafe extern "system" fn(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, *const c_void),
    pub glPixelStorei: unsafe extern "system" fn(GLenum, GLint),

    pub glGenSamplers: unsafe extern "system" fn(GLsizei, *mut GLuint),
    pub glDeleteSamplers: unsafe extern "system" fn(GLsizei, *const GLuint),
    pub glBindSampler: unsafe extern "system" fn(GLuint, GLuint),
    pub glSamplerParameteri: unsafe extern "system" fn(GLuint, GLenum, GLint),

    pub glFenceSync: unsafe extern "system" fn(GLenum, GLbitfield) -> GLsync,
    pub glClientWaitSync: unsafe extern "system" fn(GLsync, GLbitfield, GLuint64) -> GLenum,
    pub glDeleteSync: unsafe extern "system" fn(GLsync),

    pub glDrawElementsInstancedBaseVertex: unsafe extern "system" fn(GLenum, GLsizei, GLenum, *const c_void, GLsizei, GLint),

    pub glDrawElementsInstancedBaseVertexBaseInstance:
        Option<unsafe extern "system" fn(GLenum, GLsizei, GLenum, *const c_void, GLsizei, GLint, GLuint)>,
    pub glMultiDrawElementsIndirect:
        Option<unsafe extern "system" fn(GLenum, GLenum, *const c_void, GLsizei, GLsizei)>,
    pub glBufferStorage:
        Option<unsafe extern "system" fn(GLenum, GLsizeiptr, *const c_void, GLbitfield)>,
}

unsafe impl Send for GlApi {}
unsafe impl Sync for GlApi {}

impl GlApi {
    #[allow(clippy::missing_transmute_annotations)]
    pub unsafe fn from_ptrs(ptrs: &[i64]) -> Option<Self> {
        if ptrs.len() < REQUIRED_FN_COUNT {
            return None;
        }
        macro_rules! req {
            ($idx:expr) => {{
                let p = ptrs[$idx] as usize;
                if p == 0 {
                    return None;
                }
                std::mem::transmute(p)
            }};
        }
        macro_rules! opt {
            ($idx:expr) => {{
                let p = ptrs[$idx] as usize;
                if p == 0 {
                    None
                } else {
                    Some(std::mem::transmute(p))
                }
            }};
        }

        Some(Self {
            glGetError: req!(0),
            glGetIntegerv: req!(1),
            glGetIntegeri_v: req!(2),
            glGetInteger64i_v: req!(3),
            glGetBooleanv: req!(4),
            glIsEnabled: req!(5),
            glEnable: req!(6),
            glDisable: req!(7),
            glDepthMask: req!(8),
            glDepthFunc: req!(9),
            glColorMask: req!(10),
            glBlendFuncSeparate: req!(11),
            glPolygonOffset: req!(12),
            glViewport: req!(13),
            glBindFramebuffer: req!(14),
            glGenVertexArrays: req!(15),
            glDeleteVertexArrays: req!(16),
            glBindVertexArray: req!(17),
            glGenBuffers: req!(18),
            glDeleteBuffers: req!(19),
            glBindBuffer: req!(20),
            glBindBufferBase: req!(21),
            glBindBufferRange: req!(22),
            glBufferData: req!(23),
            glBufferSubData: req!(24),
            glCopyBufferSubData: req!(25),
            glMapBufferRange: req!(26),
            glUnmapBuffer: req!(27),
            glEnableVertexAttribArray: req!(28),
            glVertexAttribPointer: req!(29),
            glVertexAttribIPointer: req!(30),
            glVertexAttribDivisor: req!(31),
            glCreateShader: req!(32),
            glShaderSource: req!(33),
            glCompileShader: req!(34),
            glGetShaderiv: req!(35),
            glGetShaderInfoLog: req!(36),
            glDeleteShader: req!(37),
            glCreateProgram: req!(38),
            glAttachShader: req!(39),
            glLinkProgram: req!(40),
            glGetProgramiv: req!(41),
            glGetProgramInfoLog: req!(42),
            glDeleteProgram: req!(43),
            glUseProgram: req!(44),
            glGetUniformLocation: req!(45),
            glGetUniformBlockIndex: req!(46),
            glUniformBlockBinding: req!(47),
            glUniform1i: req!(48),
            glUniform1iv: req!(49),
            glUniformMatrix4fv: req!(50),
            glActiveTexture: req!(51),
            glBindTexture: req!(52),
            glTexSubImage2D: req!(53),
            glPixelStorei: req!(54),
            glGenSamplers: req!(55),
            glDeleteSamplers: req!(56),
            glBindSampler: req!(57),
            glSamplerParameteri: req!(58),
            glFenceSync: req!(59),
            glClientWaitSync: req!(60),
            glDeleteSync: req!(61),
            glDrawElementsInstancedBaseVertex: req!(62),
            glDrawElementsInstancedBaseVertexBaseInstance: opt!(63),
            glMultiDrawElementsIndirect: opt!(64),
            glBufferStorage: opt!(65),
        })
    }

    #[inline]
    pub fn supports_persistent_ssbo_mdi(&self) -> bool {
        self.glBufferStorage.is_some()
            && self.glMultiDrawElementsIndirect.is_some()
            && self.glDrawElementsInstancedBaseVertexBaseInstance.is_some()
    }
}
