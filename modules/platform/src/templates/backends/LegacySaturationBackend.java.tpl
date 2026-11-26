package dev.nightclient.platform;

import dev.nightclient.runtime.ClientRuntime;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL;

public final class SaturationBackend {
    private static int program, vao, copyTexture, width, height;
    private static int strengthLocation;
    private static volatile boolean invalidated;
    private static final IntBuffer integers = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();
    private static final ByteBuffer booleans = ByteBuffer.allocateDirect(4);
    private SaturationBackend() {}
    public static void resourceReload() { invalidated = true; }
    public static void render() {
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime == null) return;
        float amount = runtime.saturationAmount();
        if (Math.abs(amount - 1) < 0.000001f) return;
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (target.width <= 0 || target.height <= 0) return;
        State state = new State();
        try {
            if (invalidated) { close(); invalidated = false; }
            if (program == 0) initialize();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if (state.samplerSupported) GL33.glBindSampler(0, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTexture);
            if (width != target.width || height != target.height) {
                width = target.width; height = target.height;
                int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                try {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                } finally { GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack); }
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            if (state.alphaTestSupported) GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColorMask(true, true, true, true); GL11.glDepthMask(false);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL20.glUseProgram(program); GL20.glUniform1f(strengthLocation, amount);
            GL30.glBindVertexArray(vao); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally { state.restore(); }
    }
    private static void initialize() {
        String vertex = "#version 150\nout vec2 texCoord;\nvoid main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=uv;gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}";
        String fragment = "#version 150\nuniform sampler2D InSampler;uniform float Strength;in vec2 texCoord;out vec4 fragColor;\n"
                + @SHARED_SATURATION@
                + "void main(){vec4 c=texture(InSampler,texCoord);fragColor=vec4(ncSaturation(c.rgb,Strength),c.a);}";
        int v = compile(GL20.GL_VERTEX_SHADER, vertex), f = 0;
        try {
            f = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            int next = GL20.glCreateProgram();
            GL20.glAttachShader(next, v); GL20.glAttachShader(next, f); GL20.glLinkProgram(next);
            if (GL20.glGetProgrami(next, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String error = GL20.glGetProgramInfoLog(next); GL20.glDeleteProgram(next);
                throw new IllegalStateException("NightClient Saturation link failed: " + error);
            }
            program = next; strengthLocation = GL20.glGetUniformLocation(program, "Strength");
            GL20.glUseProgram(program); GL20.glUniform1i(GL20.glGetUniformLocation(program, "InSampler"), 0);
            vao = GL30.glGenVertexArrays(); copyTexture = GL11.glGenTextures();
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        } finally { GL20.glDeleteShader(v); if (f != 0) GL20.glDeleteShader(f); }
    }
    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
            throw new IllegalStateException("NightClient Saturation shader failed: " + error);
        }
        return shader;
    }
    public static void close() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (copyTexture != 0) GL11.glDeleteTextures(copyTexture);
        program = vao = copyTexture = width = height = 0;
    }
    private static final class State {
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int texture0;
        final int sampler;
        final org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();
        final boolean samplerSupported = caps.OpenGL33;
        final boolean alphaTestSupported = !caps.OpenGL32 || (GL11.glGetInteger(org.lwjgl.opengl.GL32.GL_CONTEXT_PROFILE_MASK) & org.lwjgl.opengl.GL32.GL_CONTEXT_CORE_PROFILE_BIT) == 0;
        final boolean alphaTest = alphaTestSupported && GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST), logic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
        final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int x, y, w, h, polygon;
        final boolean red, green, blue, alpha;
        State() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0); texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            sampler = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
            integers.clear(); GL11.glGetIntegerv(GL11.GL_VIEWPORT, integers);
            x = integers.get(0); y = integers.get(1); w = integers.get(2); h = integers.get(3);
            integers.clear(); GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, integers); polygon = integers.get(0);
            booleans.clear(); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, booleans);
            red = booleans.get(0) != 0; green = booleans.get(1) != 0; blue = booleans.get(2) != 0; alpha = booleans.get(3) != 0;
        }
        void restore() {
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            if (samplerSupported) GL33.glBindSampler(0, sampler); GL13.glActiveTexture(activeTexture);
            GL11.glViewport(x, y, w, h); GL11.glColorMask(red, green, blue, alpha); GL11.glDepthMask(depthMask);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygon);
            enabled(GL11.GL_BLEND, blend); enabled(GL11.GL_DEPTH_TEST, depth); enabled(GL11.GL_CULL_FACE, cull);
            enabled(GL11.GL_SCISSOR_TEST, scissor); enabled(GL11.GL_STENCIL_TEST, stencil); enabled(GL11.GL_COLOR_LOGIC_OP, logic);
            if (alphaTestSupported) enabled(GL11.GL_ALPHA_TEST, alphaTest);
        }
        private static void enabled(int capability, boolean value) { if (value) GL11.glEnable(capability); else GL11.glDisable(capability); }
    }
}
