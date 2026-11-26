package dev.nightclient.platform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.nightclient.renderer.atmosphere.AtmosphereColumns;
import dev.nightclient.renderer.atmosphere.AtmosphereEngine;
import dev.nightclient.renderer.atmosphere.AtmospherePresets;
import dev.nightclient.runtime.ClientRuntime;
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
import org.lwjgl.opengl.GL33;
@@ATMO_LEGACY_IMPORTS@@

public final class AtmosphereBackend {
  private static final int UBO_SLOT = 6;
  private static final AtmosphereEngine ENGINE = new AtmosphereEngine();

  private static final float[] VIEW_ROT = new float[16];
  private static final float[] PROJ = new float[16];
  private static double camX, camY, camZ;
  private static float camPitch, camYaw;
  private static boolean submerged;
  private static boolean captured;

  private static volatile boolean invalidated;
  private static boolean ready;

  private static int vao;
  private static int workFbo;
  private static int uboId;
  private static int columnsTex;
  private static int zeroTex;
  private static int skyAtlasTex;
  private static int sceneCopyTex;
  private static int qATex, qBTex, qCTex;
  private static int sceneW, sceneH, qW, qH;
  private static boolean columnsUploaded;

  private static int progBokehEx;
  private static int progBokehDisc;
  private static int progBokehStreak;
  private static int progDawn;
  private static int progCustomSky;
  private static int progMotionBlur;
  private static final int[] progMood = new int[6];
  private static int progPrecip;

  private static final ByteBuffer COLUMNS_BYTES =
      ByteBuffer.allocateDirect(AtmosphereColumns.TEX_WIDTH * AtmosphereColumns.TEX_HEIGHT * 4)
          .order(ByteOrder.nativeOrder());
  private static final IntBuffer STATE_INTS =
      ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();
  private static final ByteBuffer STATE_BOOLS = ByteBuffer.allocateDirect(4);

  private AtmosphereBackend() {}

  public static boolean isCameraSubmerged() {
    return submerged;
  }

  public static void captureFrame(
      double x,
      double y,
      double z,
      float pitch,
      float yaw,
      boolean cameraSubmerged,
      float[] viewRotation,
      float[] projection) {
    camX = x;
    camY = y;
    camZ = z;
    camPitch = pitch;
    camYaw = yaw;
    submerged = cameraSubmerged;
    System.arraycopy(viewRotation, 0, VIEW_ROT, 0, 16);
    System.arraycopy(projection, 0, PROJ, 0, 16);
    captured = true;
  }

  public static void resourceReload() {
    invalidated = true;
  }

  public static void renderLevel() {
    if (!captured) return;
    captured = false;
    ClientRuntime runtime = ClientRuntime.get();
    if (runtime == null) return;
    boolean atmoOn = runtime.settings().atmosphere.enabled;
    boolean skyOn = AtmosphereEngine.isCustomSkyActive(
        runtime.settings(), AtmosphereWorldBridge.INSTANCE.isOverworld());
    boolean mbOn = AtmosphereEngine.isMotionBlurActive(runtime.settings());
    if (!mbOn) {
      ENGINE.resetMotionBlur();
    }
    if (!atmoOn && !skyOn && !mbOn) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;
    RenderTarget target = mc.getMainRenderTarget();
    if (target == null || target.width <= 1 || target.height <= 1) return;

    int mainFbo = @@ATMO_MAIN_FBO@@;
    int mainColor = @@ATMO_MAIN_COLOR@@;
    int mainDepth = @@ATMO_MAIN_DEPTH@@;
    if (mainFbo <= 0 || mainColor <= 0 || mainDepth <= 0) return;

    AtmosphereEngine.FramePlan plan =
        ENGINE.prepareFrame(
            runtime.settings(),
            AtmosphereWorldBridge.INSTANCE,
            target.width,
            target.height,
            camX,
            camY,
            camZ,
            camPitch,
            camYaw,
            VIEW_ROT,
            PROJ,
            0);
    if (!plan.active) return;

    GlSnapshot snap = new GlSnapshot();
    int depthMin = 0, depthMag = 0, depthWrapS = 0, depthWrapT = 0, depthCompare = 0;
    boolean depthParamsSaved = false;
    try {
      if (invalidated) {
        disposeGpu();
        invalidated = false;
      }
      if (!ready) {
        initializeGpu();
      }
      ensureTargets(target.width, target.height, plan.quarterWidth, plan.quarterHeight);

      if (plan.drawAtmosphere && (plan.uploadColumns || !columnsUploaded)) {
        ENGINE.columns().writeRgbaBytes(COLUMNS_BYTES);
        uploadRgba2D(
            columnsTex, AtmosphereColumns.TEX_WIDTH, AtmosphereColumns.TEX_HEIGHT, COLUMNS_BYTES);
        columnsUploaded = true;
      }

      if (snap.samplerSupported) {
        for (int i = 0; i < 4; i++) {
          GL33.glBindSampler(i, 0);
        }
      }
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glDisable(GL11.GL_STENCIL_TEST);
      GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
      if (snap.alphaTestSupported) {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
      }
      GL11.glColorMask(true, true, true, true);
      GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
      GL30.glBindVertexArray(vao);
      GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_SLOT, uboId);

      copySceneColor(mainFbo);

      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainDepth);
      depthMin = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
      depthMag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
      depthWrapS = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
      depthWrapT = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
      depthCompare = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE);
      depthParamsSaved = true;
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);

      GL11.glDisable(GL11.GL_DEPTH_TEST);
      GL11.glDepthMask(false);
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glDisable(GL11.GL_CULL_FACE);

      if (plan.drawCustomSky) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
        GL11.glViewport(0, 0, sceneW, sceneH);
        bindTex(0, sceneCopyTex);
        bindTex(1, mainDepth);
        bindTex(2, skyAtlasTex != 0 ? skyAtlasTex : zeroTex);
        uploadUbo(plan.customSkyUbo);
        GL20.glUseProgram(progCustomSky);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        if (plan.drawAtmosphere || plan.drawMotionBlur) {
          copySceneColor(mainFbo);
        }
      }

      if (plan.drawAtmosphere) {
      if (plan.runBokeh && !plan.isDawn) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, workFbo);
        GL11.glViewport(0, 0, qW, qH);

        GL30.glFramebufferTexture2D(
            GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, qATex, 0);
        bindTex(0, sceneCopyTex);
        bindTex(1, mainDepth);
        uploadUbo(plan.bokehExtractUbo);
        GL20.glUseProgram(progBokehEx);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glFramebufferTexture2D(
            GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, qBTex, 0);
        bindTex(0, qATex);
        uploadUbo(plan.bokehDiscUbo);
        GL20.glUseProgram(progBokehDisc);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glFramebufferTexture2D(
            GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, qCTex, 0);
        bindTex(0, qATex);
        bindTex(1, zeroTex);
        uploadUbo(plan.bokehStreakWideUbo);
        GL20.glUseProgram(progBokehStreak);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glFramebufferTexture2D(
            GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, qATex, 0);
        bindTex(0, qCTex);
        bindTex(1, qBTex);
        uploadUbo(plan.bokehStreakFineUbo);
        GL20.glUseProgram(progBokehStreak);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
      }

      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
      GL11.glViewport(0, 0, sceneW, sceneH);
      if (plan.isDawn) {
        bindTex(0, sceneCopyTex);
        bindTex(1, mainDepth);
        uploadUbo(plan.dawnUbo);
        GL20.glUseProgram(progDawn);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
      } else {
        bindTex(0, sceneCopyTex);
        bindTex(1, mainDepth);
        bindTex(2, plan.runBokeh ? qATex : zeroTex);
        bindTex(3, columnsTex);
        uploadUbo(plan.moodUbo);
        GL20.glUseProgram(progMood[moodSlot(plan.moodKind)]);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
      }

      if (plan.drawPrecip && plan.precipVertexCount > 0) {
        copySceneColor(mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
        GL11.glViewport(0, 0, sceneW, sceneH);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);

        bindTex(0, columnsTex);
        bindTex(1, sceneCopyTex);
        uploadUbo(plan.precipUbo);
        GL20.glUseProgram(progPrecip);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, plan.precipVertexCount);
      }
      }

      if (plan.drawMotionBlur) {
        if (plan.drawAtmosphere) {
          copySceneColor(mainFbo);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
        GL11.glViewport(0, 0, sceneW, sceneH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        bindTex(0, sceneCopyTex);
        bindTex(1, mainDepth);
        uploadUbo(plan.motionBlurUbo);
        GL20.glUseProgram(progMotionBlur);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
      }
    } finally {
      try {
        if (depthParamsSaved) {
          GL13.glActiveTexture(GL13.GL_TEXTURE1);
          GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainDepth);
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, depthMin);
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, depthMag);
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, depthWrapS);
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, depthWrapT);
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, depthCompare);
        }
      } finally {
        snap.restore();
        @@ATMO_POST_RESTORE@@
      }
    }
  }

  private static void copySceneColor(int mainFbo) {
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneCopyTex);
    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, sceneW, sceneH);
  }

  private static void bindTex(int unit, int tex) {
    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
  }

  private static void uploadUbo(ByteBuffer data) {
    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboId);
    data.rewind();
    GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, data);
  }

  private static void uploadRgba2D(int tex, int w, int h, ByteBuffer data) {
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
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
      GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    } finally {
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevAlign);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevSkipPixels);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevSkipRows);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRow);
      GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevUnpack);
    }
  }

  private static int moodSlot(int kind) {
    if (kind == AtmospherePresets.KIND_SNOW) return 1;
    if (kind == AtmospherePresets.KIND_SAKURA) return 2;
    if (kind == AtmospherePresets.KIND_AUTUMN) return 3;
    if (kind == AtmospherePresets.KIND_FIREFLIES) return 4;
    if (kind == AtmospherePresets.KIND_MYSTIC) return 5;
    return 0;
  }

  private static void initializeGpu() {
    vao = GL30.glGenVertexArrays();
    workFbo = GL30.glGenFramebuffers();
    uboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboId);
    GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, 2048L, GL15.GL_STREAM_DRAW);

    columnsTex = createTex2D(AtmosphereColumns.TEX_WIDTH, AtmosphereColumns.TEX_HEIGHT, GL11.GL_RGBA8, GL11.GL_NEAREST);
    zeroTex = createTex2D(1, 1, GL11.GL_RGBA8, GL11.GL_LINEAR);
    ByteBuffer zero = ByteBuffer.allocateDirect(4);
    zero.putInt(0).flip();
    uploadRgba2D(zeroTex, 1, 1, zero);
    skyAtlasTex = loadSkyAtlasTex();
    columnsUploaded = false;

    String fsVert = readShader("fullscreen_ubo.vsh");
    progBokehEx =
        linkProgram(
            fsVert,
            readShader("atmosphere_bokeh_extract_ubo.fsh"),
            "NightAtmoBokehExtract",
            new String[] {"Sampler0", "Sampler1"});
    progBokehDisc =
        linkProgram(
            fsVert,
            readShader("atmosphere_bokeh_disc_ubo.fsh"),
            "NightAtmoBokehDisc",
            new String[] {"Sampler0"});
    progBokehStreak =
        linkProgram(
            fsVert,
            readShader("atmosphere_bokeh_streak_ubo.fsh"),
            "NightAtmoBokehStreak",
            new String[] {"Sampler0", "Sampler1"});
    progDawn =
        linkProgram(
            fsVert,
            readShader("atmosphere_dawn_ubo.fsh"),
            "NightAtmoDawn",
            new String[] {"Sampler0", "Sampler1"});
    progCustomSky =
        linkProgram(
            fsVert,
            readShader("atmosphere_custom_sky_ubo.fsh"),
            "NightCustomSky",
            new String[] {"Sampler0", "Sampler1", "Sampler2"});
    progMotionBlur =
        linkProgram(
            fsVert,
            readShader("atmosphere_motion_blur_ubo.fsh"),
            "NightMotionBlur",
            new String[] {"Sampler0", "Sampler1"});

    String[] moodNames = {"dust", "snow", "sakura", "autumn", "fireflies", "mystic"};
    String[] moodSamplers = {"Sampler0", "Sampler1", "Sampler2", "Sampler3"};
    for (int i = 0; i < moodNames.length; i++) {
      progMood[i] =
          linkProgram(
              fsVert,
              readShader("atmosphere_mood_" + moodNames[i] + "_ubo.fsh"),
              "NightAtmoMood",
              moodSamplers);
    }

    progPrecip =
        linkProgram(
            readShader("atmosphere_precip_ubo.vsh"),
            readShader("atmosphere_precip_ubo.fsh"),
            "NightAtmoPrecip",
            new String[] {"Columns", "Scene"});

    ready = true;
  }

  private static void ensureTargets(int w, int h, int qw, int qh) {
    if (sceneCopyTex == 0 || sceneW != w || sceneH != h) {
      if (sceneCopyTex != 0) GL11.glDeleteTextures(sceneCopyTex);
      sceneW = w;
      sceneH = h;
      sceneCopyTex = createTex2D(w, h, GL11.GL_RGBA8, GL11.GL_LINEAR);
    }
    if (qATex == 0 || qW != qw || qH != qh) {
      if (qATex != 0) GL11.glDeleteTextures(qATex);
      if (qBTex != 0) GL11.glDeleteTextures(qBTex);
      if (qCTex != 0) GL11.glDeleteTextures(qCTex);
      qW = qw;
      qH = qh;
      qATex = createTex2D(qw, qh, GL30.GL_RGBA16F, GL11.GL_LINEAR);
      qBTex = createTex2D(qw, qh, GL30.GL_RGBA16F, GL11.GL_LINEAR);
      qCTex = createTex2D(qw, qh, GL30.GL_RGBA16F, GL11.GL_LINEAR);
    }
  }

  private static int createTex2D(int w, int h, int internalFmt, int filter) {
    int id = GL11.glGenTextures();
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    int prevUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
    try {
      GL11.glTexImage2D(
          GL11.GL_TEXTURE_2D, 0, internalFmt, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    } finally {
      GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevUnpack);
    }
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
    return id;
  }

  private static int linkProgram(
      String vertSrc, String fragSrc, String uboBlockName, String[] samplerNames) {
    int vs = compileShader(GL20.GL_VERTEX_SHADER, vertSrc);
    int fs = 0;
    try {
      fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragSrc);
      int prog = GL20.glCreateProgram();
      GL20.glAttachShader(prog, vs);
      GL20.glAttachShader(prog, fs);
      GL20.glLinkProgram(prog);
      if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
        String log = GL20.glGetProgramInfoLog(prog);
        GL20.glDeleteProgram(prog);
        throw new IllegalStateException("NightClient Atmosphere link failed: " + log);
      }
      int blk = GL31.glGetUniformBlockIndex(prog, uboBlockName);
      if (blk != GL31.GL_INVALID_INDEX) {
        GL31.glUniformBlockBinding(prog, blk, UBO_SLOT);
      }
      GL20.glUseProgram(prog);
      for (int i = 0; i < samplerNames.length; i++) {
        int loc = GL20.glGetUniformLocation(prog, samplerNames[i]);
        if (loc >= 0) {
          GL20.glUniform1i(loc, i);
        }
      }
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
      throw new IllegalStateException("NightClient Atmosphere shader compile failed: " + log);
    }
    return id;
  }

  private static String readShader(String fileName) {
    String path = "/assets/nightclient/shaders/core/" + fileName;
    InputStream in = AtmosphereEngine.class.getResourceAsStream(path);
    if (in == null) {
      throw new IllegalStateException("Missing Atmosphere shader resource: " + path);
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed reading Atmosphere shader: " + path, e);
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static int loadSkyAtlasTex() {
    String path = "/assets/nightclient/textures/sky/celestials.png";
    InputStream in = AtmosphereEngine.class.getResourceAsStream(path);
    if (in == null) return 0;
    try {
      java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
      if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return 0;
      int w = img.getWidth(), h = img.getHeight();
      int[] argb = new int[w * h];
      img.getRGB(0, 0, w, h, argb, 0, w);
      ByteBuffer rgba = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
      for (int i = 0; i < argb.length; i++) {
        int px = argb[i];
        rgba.put((byte) ((px >>> 16) & 255));
        rgba.put((byte) ((px >>> 8) & 255));
        rgba.put((byte) (px & 255));
        rgba.put((byte) ((px >>> 24) & 255));
      }
      rgba.flip();
      int tex = createTex2D(w, h, GL11.GL_RGBA8, GL11.GL_LINEAR);
      uploadRgba2D(tex, w, h, rgba);
      return tex;
    } catch (Throwable ignored) {
      return 0;
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static void disposeGpu() {
    if (progBokehEx != 0) GL20.glDeleteProgram(progBokehEx);
    if (progBokehDisc != 0) GL20.glDeleteProgram(progBokehDisc);
    if (progBokehStreak != 0) GL20.glDeleteProgram(progBokehStreak);
    if (progDawn != 0) GL20.glDeleteProgram(progDawn);
    if (progCustomSky != 0) GL20.glDeleteProgram(progCustomSky);
    if (progMotionBlur != 0) GL20.glDeleteProgram(progMotionBlur);
    for (int i = 0; i < progMood.length; i++) {
      if (progMood[i] != 0) GL20.glDeleteProgram(progMood[i]);
      progMood[i] = 0;
    }
    if (progPrecip != 0) GL20.glDeleteProgram(progPrecip);
    progBokehEx = progBokehDisc = progBokehStreak = progDawn = progCustomSky = progMotionBlur = progPrecip = 0;

    if (sceneCopyTex != 0) GL11.glDeleteTextures(sceneCopyTex);
    if (qATex != 0) GL11.glDeleteTextures(qATex);
    if (qBTex != 0) GL11.glDeleteTextures(qBTex);
    if (qCTex != 0) GL11.glDeleteTextures(qCTex);
    if (columnsTex != 0) GL11.glDeleteTextures(columnsTex);
    if (zeroTex != 0) GL11.glDeleteTextures(zeroTex);
    if (skyAtlasTex != 0) GL11.glDeleteTextures(skyAtlasTex);
    sceneCopyTex = qATex = qBTex = qCTex = columnsTex = zeroTex = skyAtlasTex = 0;

    if (uboId != 0) GL15.glDeleteBuffers(uboId);
    if (workFbo != 0) GL30.glDeleteFramebuffers(workFbo);
    if (vao != 0) GL30.glDeleteVertexArrays(vao);
    uboId = workFbo = vao = 0;
    sceneW = sceneH = qW = qH = 0;
    columnsUploaded = false;
    ready = false;
  }

  public static void close() {
    ENGINE.reset();
    disposeGpu();
  }

  private static final class GlSnapshot {
    final org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();
    final boolean samplerSupported = caps.OpenGL33;
    final boolean alphaTestSupported =
        !caps.OpenGL32
            || (GL11.glGetInteger(org.lwjgl.opengl.GL32.GL_CONTEXT_PROFILE_MASK)
                    & org.lwjgl.opengl.GL32.GL_CONTEXT_CORE_PROFILE_BIT)
                == 0;
    final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    final int arrayBuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    final int uboBuf = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
    final int uboSlotBuf = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, UBO_SLOT);
    final long uboSlotStart =
        caps.OpenGL32 && uboSlotBuf != 0
            ? org.lwjgl.opengl.GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START, UBO_SLOT)
            : 0L;
    final long uboSlotSize =
        caps.OpenGL32 && uboSlotBuf != 0
            ? org.lwjgl.opengl.GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE, UBO_SLOT)
            : 0L;
    final int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    final int activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    final int activeTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    final int[] tex = new int[4];
    final int[] samp = new int[4];
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
    final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    final boolean logic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
    final boolean alphaTest = alphaTestSupported && GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    final int[] viewport = new int[4];
    final int polygon;
    final boolean rMask, gMask, bMask, aMask;

    GlSnapshot() {
      for (int i = 0; i < 4; i++) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
        tex[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        samp[i] = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
      }
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
      if (uboSlotBuf != 0 && uboSlotSize > 0L) {
        GL30.glBindBufferRange(
            GL31.GL_UNIFORM_BUFFER, UBO_SLOT, uboSlotBuf, uboSlotStart, uboSlotSize);
      } else {
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_SLOT, uboSlotBuf);
      }
      GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboBuf);
      GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
      for (int i = 0; i < 4; i++) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
        if (samplerSupported) GL33.glBindSampler(i, samp[i]);
      }
      GL13.glActiveTexture(activeTex);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, activeTex2D);
      GL20.glBlendEquationSeparate(eqRgb, eqAlpha);
      GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
      setCap(GL11.GL_BLEND, blend);
      setCap(GL11.GL_DEPTH_TEST, depth);
      GL11.glDepthMask(depthMask);
      GL11.glDepthFunc(depthFunc);
      setCap(GL11.GL_CULL_FACE, cull);
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
