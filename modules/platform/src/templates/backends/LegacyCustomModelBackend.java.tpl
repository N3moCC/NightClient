package dev.nightclient.platform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.nightclient.renderer.model.CosmeticModelMesh;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
@@CM_LEGACY_IMPORTS@@

public final class CustomModelBackend {
  private static final int UBO_SLOT = 6;
  private static final int MAX_CACHED_MESHES = 16;

  private static final class GpuMesh {
    int meshId;
    int vao;
    int vbo;
    int ebo;
    int texId;
    long lastUsed;

    void dispose() {
      if (texId != 0) GL11.glDeleteTextures(texId);
      if (ebo != 0) GL15.glDeleteBuffers(ebo);
      if (vbo != 0) GL15.glDeleteBuffers(vbo);
      if (vao != 0) GL30.glDeleteVertexArrays(vao);
      meshId = 0;
      vao = vbo = ebo = texId = 0;
    }
  }

  private static final GpuMesh[] CACHE = new GpuMesh[MAX_CACHED_MESHES];
  private static long useClock;

  private static volatile boolean invalidated;
  private static boolean ready;
  private static int program;
  private static int uboId;

  private static final ByteBuffer UBO_BYTES =
      ByteBuffer.allocateDirect(CustomModelBridge.UBO_STRIDE).order(ByteOrder.nativeOrder());
  private static final IntBuffer STATE_INTS =
      ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();
  private static final ByteBuffer STATE_BOOLS = ByteBuffer.allocateDirect(4);

  static {
    for (int i = 0; i < MAX_CACHED_MESHES; i++) CACHE[i] = new GpuMesh();
  }

  private CustomModelBackend() {}

  public static void resourceReload() {
    invalidated = true;
  }

  public static void flushWorld() {
    int count = CustomModelBridge.worldDrawCount();
    if (count <= 0) {
      CustomModelBridge.endWorldPass();
      return;
    }
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.level == null) return;
      RenderTarget target = mc.getMainRenderTarget();
      if (target == null || target.width <= 1 || target.height <= 1) return;
      int mainFbo = @@CM_MAIN_FBO@@;
      if (mainFbo <= 0) return;
      renderQueue(
          CustomModelBridge.WORLD_DRAWS,
          count,
          CustomModelBridge.WORLD_PROJ,
          mainFbo,
          target.width,
          target.height,
          true);
    } finally {
      CustomModelBridge.endWorldPass();
    }
  }

  public static void flushHands() {
    int count = CustomModelBridge.handDrawCount();
    if (count <= 0) {
      CustomModelBridge.endHandPass();
      return;
    }
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.level == null) return;
      renderQueue(CustomModelBridge.HAND_DRAWS, count, CustomModelBridge.HAND_PROJ, -1, 0, 0, false);
    } finally {
      CustomModelBridge.endHandPass();
    }
  }

  private static void renderQueue(
      CustomModelBridge.DrawCmd[] cmds,
      int count,
      float[] proj,
      int targetFbo,
      int targetW,
      int targetH,
      boolean bindMainFbo) {
    GlSnapshot snap = new GlSnapshot();
    try {
      if (invalidated) {
        disposeGpu();
        invalidated = false;
      }
      if (!ready) {
        initializeGpu();
      }
      if (program == 0 || uboId == 0) return;

      if (bindMainFbo && targetFbo > 0) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);
        GL11.glViewport(0, 0, targetW, targetH);
      }

      if (snap.samplerSupported) {
        GL33.glBindSampler(0, 0);
      }
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glDisable(GL11.GL_STENCIL_TEST);
      GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
      if (snap.alphaTestSupported) {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
      }
      GL11.glColorMask(true, true, true, true);
      GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
      GL11.glEnable(GL11.GL_DEPTH_TEST);
      GL11.glDepthFunc(GL11.GL_LEQUAL);
      GL11.glFrontFace(GL11.GL_CCW);
      GL11.glCullFace(GL11.GL_BACK);

      GL20.glUseProgram(program);
      GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_SLOT, uboId);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);

      for (int pass = 0; pass < 3; pass++) {
        boolean isAdditive = pass == CosmeticModelMesh.BLEND_ADDITIVE;
        if (pass == CosmeticModelMesh.BLEND_OPAQUE) {
          GL11.glDisable(GL11.GL_BLEND);
          GL11.glDepthMask(true);
        } else if (pass == CosmeticModelMesh.BLEND_TRANSLUCENT) {
          GL11.glEnable(GL11.GL_BLEND);
          GL14.glBlendEquation(GL14.GL_FUNC_ADD);
          GL14.glBlendFuncSeparate(
              GL11.GL_SRC_ALPHA,
              GL11.GL_ONE_MINUS_SRC_ALPHA,
              GL11.GL_ONE,
              GL11.GL_ONE_MINUS_SRC_ALPHA);
          GL11.glDepthMask(false);
        } else {
          GL11.glEnable(GL11.GL_BLEND);
          GL14.glBlendEquation(GL14.GL_FUNC_ADD);
          GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
          GL11.glDepthMask(false);
        }

        for (int i = 0; i < count; i++) {
          CustomModelBridge.DrawCmd cmd = cmds[i];
          CosmeticModelMesh mesh = cmd.mesh;
          if (mesh == null) continue;
          if (!hasSubMeshForPass(mesh, cmd.boneId, pass)) continue;

          GpuMesh gpu = ensureMesh(mesh);
          if (gpu == null || gpu.vao == 0) continue;

          UBO_BYTES.clear();
          CustomModelBridge.packDrawUbo(cmd, proj, UBO_BYTES, 0, isAdditive);
          UBO_BYTES.position(0);
          UBO_BYTES.limit(CustomModelBridge.UBO_STRIDE);
          GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboId);
          GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, UBO_BYTES);

          GL30.glBindVertexArray(gpu.vao);
          GL11.glBindTexture(GL11.GL_TEXTURE_2D, gpu.texId);

          CosmeticModelMesh.SubMesh[] subs = mesh.subMeshes;
          for (int s = 0; s < subs.length; s++) {
            CosmeticModelMesh.SubMesh sub = subs[s];
            if (sub.boneId != cmd.boneId || sub.blendMode != pass || sub.indexCount <= 0) continue;
            if (pass == CosmeticModelMesh.BLEND_OPAQUE && !sub.doubleSided) {
              GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
              GL11.glDisable(GL11.GL_CULL_FACE);
            }
            GL11.glDrawElements(
                GL11.GL_TRIANGLES, sub.indexCount, GL11.GL_UNSIGNED_INT, sub.firstIndex * 4L);
          }
        }
      }
    } finally {
      snap.restore();
      @@CM_LEGACY_POST_RESTORE@@
    }
  }

  private static boolean hasSubMeshForPass(CosmeticModelMesh mesh, int boneId, int pass) {
    CosmeticModelMesh.SubMesh[] subs = mesh.subMeshes;
    for (int i = 0; i < subs.length; i++) {
      CosmeticModelMesh.SubMesh sub = subs[i];
      if (sub.boneId == boneId && sub.blendMode == pass && sub.indexCount > 0) return true;
    }
    return false;
  }

  private static GpuMesh ensureMesh(CosmeticModelMesh mesh) {
    useClock++;
    GpuMesh free = null;
    GpuMesh oldest = CACHE[0];
    for (int i = 0; i < MAX_CACHED_MESHES; i++) {
      GpuMesh e = CACHE[i];
      if (e.meshId == mesh.meshId && e.vao != 0) {
        e.lastUsed = useClock;
        return e;
      }
      if (e.vao == 0 && free == null) free = e;
      if (e.lastUsed < oldest.lastUsed) oldest = e;
    }
    GpuMesh slot = free != null ? free : oldest;
    slot.dispose();

    int vao = GL30.glGenVertexArrays();
    int vbo = GL15.glGenBuffers();
    int ebo = GL15.glGenBuffers();
    GL30.glBindVertexArray(vao);

    ByteBuffer vbuf = mesh.copyVerticesToDirectBuffer();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vbuf, GL15.GL_STATIC_DRAW);

    ByteBuffer ibuf = mesh.copyIndicesToDirectBuffer();
    GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
    GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ibuf, GL15.GL_STATIC_DRAW);

    int stride = CosmeticModelMesh.VERTEX_STRIDE;
    GL20.glEnableVertexAttribArray(0);
    GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
    GL20.glEnableVertexAttribArray(1);
    GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, stride, 12L);
    GL20.glEnableVertexAttribArray(2);
    GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 16L);
    GL20.glEnableVertexAttribArray(3);
    GL30.glVertexAttribIPointer(3, 2, GL11.GL_SHORT, stride, 24L);
    GL20.glEnableVertexAttribArray(4);
    GL30.glVertexAttribIPointer(4, 2, GL11.GL_SHORT, stride, 28L);
    GL20.glEnableVertexAttribArray(5);
    GL20.glVertexAttribPointer(5, 3, GL11.GL_BYTE, true, stride, 32L);
    GL30.glBindVertexArray(0);

    int texId = uploadArgbTexture(mesh.texturePixels, mesh.textureWidth, mesh.textureHeight);
    slot.meshId = mesh.meshId;
    slot.vao = vao;
    slot.vbo = vbo;
    slot.ebo = ebo;
    slot.texId = texId;
    slot.lastUsed = useClock;
    return slot;
  }

  private static int uploadArgbTexture(int[] argb, int w, int h) {
    int tex = GL11.glGenTextures();
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    int rw = Math.max(1, w);
    int rh = Math.max(1, h);
    ByteBuffer rgba = ByteBuffer.allocateDirect(rw * rh * 4).order(ByteOrder.nativeOrder());
    int count = rw * rh;
    for (int i = 0; i < count; i++) {
      int p = (argb != null && i < argb.length) ? argb[i] : 0xFFFFFFFF;
      rgba.put((byte) ((p >>> 16) & 0xFF));
      rgba.put((byte) ((p >>> 8) & 0xFF));
      rgba.put((byte) (p & 0xFF));
      rgba.put((byte) ((p >>> 24) & 0xFF));
    }
    rgba.flip();

    int prevUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    int prevRow = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
    int prevSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
    int prevSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
    int prevAlign = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
    GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
    try {
      GL11.glTexImage2D(
          GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, rw, rh, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
    } finally {
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevAlign);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevSkipPixels);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevSkipRows);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRow);
      GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevUnpack);
    }
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    return tex;
  }

  private static void initializeGpu() {
    uboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboId);
    GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, CustomModelBridge.UBO_STRIDE, GL15.GL_STREAM_DRAW);
    program =
        linkProgram(
            readShader("cosmetic_model_legacy.vsh"), readShader("cosmetic_model_legacy.fsh"));
    ready = true;
  }

  private static int linkProgram(String vertSrc, String fragSrc) {
    int vs = compileShader(GL20.GL_VERTEX_SHADER, vertSrc);
    int fs = 0;
    try {
      fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragSrc);
      int prog = GL20.glCreateProgram();
      GL20.glAttachShader(prog, vs);
      GL20.glAttachShader(prog, fs);
      GL20.glBindAttribLocation(prog, 0, "Position");
      GL20.glBindAttribLocation(prog, 1, "Color");
      GL20.glBindAttribLocation(prog, 2, "UV0");
      GL20.glBindAttribLocation(prog, 3, "UV1");
      GL20.glBindAttribLocation(prog, 4, "UV2");
      GL20.glBindAttribLocation(prog, 5, "Normal");
      GL20.glLinkProgram(prog);
      if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
        String log = GL20.glGetProgramInfoLog(prog);
        GL20.glDeleteProgram(prog);
        throw new IllegalStateException("NightClient 3D model link failed: " + log);
      }
      int blk = GL31.glGetUniformBlockIndex(prog, "NightModelDraw");
      if (blk != GL31.GL_INVALID_INDEX) {
        GL31.glUniformBlockBinding(prog, blk, UBO_SLOT);
      }
      GL20.glUseProgram(prog);
      int loc = GL20.glGetUniformLocation(prog, "Sampler0");
      if (loc >= 0) GL20.glUniform1i(loc, 0);
      return prog;
    } finally {
      GL20.glDeleteShader(vs);
      if (fs != 0) GL20.glDeleteShader(fs);
    }
  }

  private static int compileShader(int type, String src) {
    int id = GL20.glCreateShader(type);
    GL20.glShaderSource(id, src);
    GL20.glCompileShader(id);
    if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
      String log = GL20.glGetShaderInfoLog(id);
      GL20.glDeleteShader(id);
      throw new IllegalStateException("NightClient 3D model shader compile failed: " + log);
    }
    return id;
  }

  private static String readShader(String fileName) {
    String path = "/assets/nightclient/shaders/core/" + fileName;
    InputStream in = CosmeticModelMesh.class.getResourceAsStream(path);
    if (in == null) {
      throw new IllegalStateException("Missing 3D model shader resource: " + path);
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed reading 3D model shader: " + path, e);
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static void disposeGpu() {
    for (int i = 0; i < MAX_CACHED_MESHES; i++) CACHE[i].dispose();
    if (program != 0) GL20.glDeleteProgram(program);
    if (uboId != 0) GL15.glDeleteBuffers(uboId);
    program = uboId = 0;
    ready = false;
  }

  public static void close() {
    disposeGpu();
  }

  private static final class GlSnapshot {
    final org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();
    final boolean samplerSupported = caps.OpenGL33;
    final boolean alphaTestSupported =
        !caps.OpenGL32
            || (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)
                    & GL32.GL_CONTEXT_CORE_PROFILE_BIT)
                == 0;
    final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    final int arrayBuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    final int elemBuf = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    final int uboBuf = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
    final int uboSlotBuf = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, UBO_SLOT);
    final long uboSlotStart =
        caps.OpenGL32 && uboSlotBuf != 0
            ? GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START, UBO_SLOT)
            : 0L;
    final long uboSlotSize =
        caps.OpenGL32 && uboSlotBuf != 0
            ? GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE, UBO_SLOT)
            : 0L;
    final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    final int activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    final int activeTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    final int tex0;
    final int samp0;
    final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    final int eqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    final int eqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    final int cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
    final int frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
    final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    final boolean logic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
    final boolean alphaTest = alphaTestSupported && GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    final int[] viewport = new int[4];
    final int polygon;
    final boolean rMask, gMask, bMask, aMask;

    GlSnapshot() {
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      tex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      samp0 = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
      GL13.glActiveTexture(activeTex);
      STATE_INTS.clear();
      GL11.glGetIntegerv(GL11.GL_VIEWPORT, STATE_INTS);
      for (int i = 0; i < 4; i++) viewport[i] = STATE_INTS.get(i);
      STATE_INTS.clear();
      GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, STATE_INTS);
      polygon = STATE_INTS.get(0);
      STATE_BOOLS.clear();
      GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, STATE_BOOLS);
      rMask = STATE_BOOLS.get(0) != 0;
      gMask = STATE_BOOLS.get(1) != 0;
      bMask = STATE_BOOLS.get(2) != 0;
      aMask = STATE_BOOLS.get(3) != 0;
    }

    void restore() {
      GL20.glUseProgram(program);
      GL30.glBindVertexArray(vao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuf);
      GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elemBuf);
      if (uboSlotBuf != 0 && uboSlotSize > 0L) {
        GL30.glBindBufferRange(
            GL31.GL_UNIFORM_BUFFER, UBO_SLOT, uboSlotBuf, uboSlotStart, uboSlotSize);
      } else {
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_SLOT, uboSlotBuf);
      }
      GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboBuf);
      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex0);
      if (samplerSupported) GL33.glBindSampler(0, samp0);
      GL13.glActiveTexture(activeTex);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, activeTex2D);
      GL20.glBlendEquationSeparate(eqRgb, eqAlpha);
      GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
      setCap(GL11.GL_BLEND, blend);
      setCap(GL11.GL_DEPTH_TEST, depth);
      GL11.glDepthMask(depthMask);
      GL11.glDepthFunc(depthFunc);
      setCap(GL11.GL_CULL_FACE, cull);
      GL11.glCullFace(cullMode);
      GL11.glFrontFace(frontFace);
      setCap(GL11.GL_SCISSOR_TEST, scissor);
      setCap(GL11.GL_STENCIL_TEST, stencil);
      setCap(GL11.GL_COLOR_LOGIC_OP, logic);
      if (alphaTestSupported) setCap(GL11.GL_ALPHA_TEST, alphaTest);
      GL11.glColorMask(rMask, gMask, bMask, aMask);
      GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
      GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygon);
    }

    private static void setCap(int cap, boolean on) {
      if (on) GL11.glEnable(cap);
      else GL11.glDisable(cap);
    }
  }
}
