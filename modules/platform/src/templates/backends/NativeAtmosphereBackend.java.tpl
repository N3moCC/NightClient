package dev.nightclient.platform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightclient.renderer.atmosphere.AtmosphereColumns;
import dev.nightclient.renderer.atmosphere.AtmosphereEngine;
import dev.nightclient.renderer.atmosphere.AtmospherePresets;
import dev.nightclient.runtime.ClientRuntime;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
@@ATMO_NATIVE_IMPORTS@@

public final class AtmosphereBackend {
  private static final AtmosphereEngine ENGINE = new AtmosphereEngine();

  private static final float[] VIEW_ROT = new float[16];
  private static final float[] PROJ = new float[16];
  private static final float[] BOBBED_PROJ = new float[16];
  private static double camX, camY, camZ;
  private static float camPitch, camYaw;
  private static boolean submerged;
  private static boolean captured;
  private static boolean bobbedCaptured;

  private static volatile boolean invalidated;

  private static RenderPipeline pipeBokehEx;
  private static RenderPipeline pipeBokehDisc;
  private static RenderPipeline pipeBokehStreak;
  private static RenderPipeline pipeDawn;
  private static RenderPipeline pipeCustomSky;
  private static RenderPipeline pipeMotionBlur;
  private static final RenderPipeline[] pipeMood = new RenderPipeline[6];
  @@ATMO_PRECIP_PIPE_DECL@@

  private static GpuTexture sceneCopy;
  private static GpuTextureView sceneCopyView;
  private static GpuTexture qA, qB, qC;
  private static GpuTextureView qAView, qBView, qCView;
  private static GpuTexture columnsTex;
  private static GpuTextureView columnsView;
  private static NativeImage columnsImage;
  private static GpuTexture zeroTex;
  private static GpuTextureView zeroView;
  private static GpuTexture skyAtlasTex;
  private static GpuTextureView skyAtlasView;
  private static int sceneW, sceneH, qW, qH;
  private static boolean columnsUploaded;

  private static GpuBuffer uboBokehEx;
  private static GpuBuffer uboBokehDisc;
  private static GpuBuffer uboBokehStreakWide;
  private static GpuBuffer uboBokehStreakFine;
  private static GpuBuffer uboDawn;
  private static GpuBuffer uboCustomSky;
  private static GpuBuffer uboMotionBlur;
  private static GpuBuffer uboMood;
  private static GpuBuffer uboPrecip;
  @@ATMO_VERTEX_DECL@@

  private AtmosphereBackend() {}

  public static boolean isCameraSubmerged() {
    return submerged;
  }

  public static void captureBobbedProjection(float[] projection) {
    if (projection == null || projection.length < 16) return;
    if (Math.abs(projection[11] + 1.0f) > 0.05f || Math.abs(projection[15]) > 0.05f) return;
    System.arraycopy(projection, 0, BOBBED_PROJ, 0, 16);
    bobbedCaptured = true;
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
    System.arraycopy(bobbedCaptured ? BOBBED_PROJ : projection, 0, PROJ, 0, 16);
    bobbedCaptured = false;
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
    RenderTarget target = @@ATMO_MAIN_TARGET@@;
    if (target == null || target.width <= 1 || target.height <= 1) return;
    GpuTexture mainColor = target.getColorTexture();
    GpuTexture mainDepth = target.getDepthTexture();
    GpuTextureView mainColorView = target.getColorTextureView();
    GpuTextureView mainDepthView = target.getDepthTextureView();
    if (mainColor == null || mainDepth == null || mainColorView == null || mainDepthView == null) {
      return;
    }

    int revDepth = @@ATMO_REVERSED_DEPTH@@;
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
            revDepth);
    if (!plan.active) return;

    if (invalidated) {
      disposeGpu();
      invalidated = false;
    }
    ensurePipelines();
    CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
    ensureResources(encoder, mainColor, target.width, target.height, plan.quarterWidth, plan.quarterHeight);

    if (plan.drawAtmosphere && (plan.uploadColumns || !columnsUploaded)) {
      int[] argb = ENGINE.columns().argbTexels();
      int idx = 0;
      for (int y = 0; y < AtmosphereColumns.TEX_HEIGHT; y++) {
        for (int x = 0; x < AtmosphereColumns.TEX_WIDTH; x++) {
          columnsImage.setPixel(x, y, argb[idx++]);
        }
      }
      encoder.writeToTexture(columnsTex, columnsImage);
      columnsUploaded = true;
    }

    encoder.copyTextureToTexture(mainColor, sceneCopy, 0, 0, 0, 0, 0, sceneW, sceneH);
    @@ATMO_DEPTH_FILTER_PREP@@

    if (plan.drawCustomSky) {
      writeUbo(encoder, uboCustomSky, plan.customSkyUbo);
      try (RenderPass pass = @@ATMO_PASS_MAIN_NODEPTH@@) {
        @@ATMO_SET_PIPE_CUSTOM_SKY@@
        @@ATMO_BIND_CUSTOM_SKY@@
        pass.setUniform("NightCustomSky", uboCustomSky);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }
      if (plan.drawAtmosphere || plan.drawMotionBlur) {
        encoder.copyTextureToTexture(mainColor, sceneCopy, 0, 0, 0, 0, 0, sceneW, sceneH);
      }
    }

    if (plan.drawAtmosphere) {
    if (plan.runBokeh && !plan.isDawn) {
      writeUbo(encoder, uboBokehEx, plan.bokehExtractUbo);
      try (RenderPass pass = @@ATMO_PASS_QA@@) {
        @@ATMO_SET_PIPE_BOKEH_EX@@
        @@ATMO_BIND_BOKEH_EX@@
        pass.setUniform("NightAtmoBokehExtract", uboBokehEx);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }

      writeUbo(encoder, uboBokehDisc, plan.bokehDiscUbo);
      try (RenderPass pass = @@ATMO_PASS_QB@@) {
        @@ATMO_SET_PIPE_BOKEH_DISC@@
        @@ATMO_BIND_BOKEH_DISC@@
        pass.setUniform("NightAtmoBokehDisc", uboBokehDisc);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }

      writeUbo(encoder, uboBokehStreakWide, plan.bokehStreakWideUbo);
      try (RenderPass pass = @@ATMO_PASS_QC@@) {
        @@ATMO_SET_PIPE_BOKEH_STREAK@@
        @@ATMO_BIND_BOKEH_STREAK_WIDE@@
        pass.setUniform("NightAtmoBokehStreak", uboBokehStreakWide);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }

      writeUbo(encoder, uboBokehStreakFine, plan.bokehStreakFineUbo);
      try (RenderPass pass = @@ATMO_PASS_QA@@) {
        @@ATMO_SET_PIPE_BOKEH_STREAK@@
        @@ATMO_BIND_BOKEH_STREAK_FINE@@
        pass.setUniform("NightAtmoBokehStreak", uboBokehStreakFine);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }
    }

    if (plan.isDawn) {
      writeUbo(encoder, uboDawn, plan.dawnUbo);
      try (RenderPass pass = @@ATMO_PASS_MAIN_NODEPTH@@) {
        @@ATMO_SET_PIPE_DAWN@@
        @@ATMO_BIND_DAWN@@
        pass.setUniform("NightAtmoDawn", uboDawn);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }
    } else {
      RenderPipeline moodPipe = pipeMood[moodSlot(plan.moodKind)];
      GpuTextureView bokehView = plan.runBokeh ? qAView : zeroView;
      writeUbo(encoder, uboMood, plan.moodUbo);
      try (RenderPass pass = @@ATMO_PASS_MAIN_NODEPTH@@) {
        @@ATMO_SET_PIPE_MOOD@@
        @@ATMO_BIND_MOOD@@
        pass.setUniform("NightAtmoMood", uboMood);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }
    }

    if (plan.drawPrecip && plan.precipVertexCount > 0) {
      RenderPipeline precipPipe = @@ATMO_PRECIP_PIPE_EXPR@@;
      encoder.copyTextureToTexture(mainColor, sceneCopy, 0, 0, 0, 0, 0, sceneW, sceneH);
      writeUbo(encoder, uboPrecip, plan.precipUbo);
      try (RenderPass pass = @@ATMO_PASS_MAIN_DEPTH@@) {
        @@ATMO_SET_PIPE_PRECIP@@
        @@ATMO_BIND_PRECIP@@
        pass.setUniform("NightAtmoPrecip", uboPrecip);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_PRECIP@@
      }
    }
    }

    if (plan.drawMotionBlur) {
      if (plan.drawAtmosphere) {
        encoder.copyTextureToTexture(mainColor, sceneCopy, 0, 0, 0, 0, 0, sceneW, sceneH);
      }
      writeUbo(encoder, uboMotionBlur, plan.motionBlurUbo);
      try (RenderPass pass = @@ATMO_PASS_MAIN_NODEPTH@@) {
        @@ATMO_SET_PIPE_MOTION_BLUR@@
        @@ATMO_BIND_MOTION_BLUR@@
        pass.setUniform("NightMotionBlur", uboMotionBlur);
        @@ATMO_VERTEX_BIND@@
        @@ATMO_DRAW_FS@@
      }
    }
  }

  private static void writeUbo(CommandEncoder encoder, GpuBuffer ubo, ByteBuffer data) {
    data.rewind();
    encoder.writeToBuffer(ubo.slice(), data);
  }

  private static int moodSlot(int kind) {
    if (kind == AtmospherePresets.KIND_SNOW) return 1;
    if (kind == AtmospherePresets.KIND_SAKURA) return 2;
    if (kind == AtmospherePresets.KIND_AUTUMN) return 3;
    if (kind == AtmospherePresets.KIND_FIREFLIES) return 4;
    if (kind == AtmospherePresets.KIND_MYSTIC) return 5;
    return 0;
  }

  private static void ensurePipelines() {
    if (pipeDawn != null) return;
    @@ATMO_BUILD_PIPELINES@@
  }

  private static void ensureResources(
      CommandEncoder encoder, GpuTexture mainColor, int w, int h, int qw, int qh) {
    @@ATMO_VERTEX_CREATE@@
    if (uboBokehEx == null) {
      uboBokehEx = createUbo("NightClient Atmo BokehEx", AtmosphereEngine.BOKEH_EXTRACT_UBO_BYTES);
      uboBokehDisc = createUbo("NightClient Atmo BokehDisc", AtmosphereEngine.BOKEH_DISC_UBO_BYTES);
      uboBokehStreakWide = createUbo("NightClient Atmo BokehStrW", AtmosphereEngine.BOKEH_STREAK_UBO_BYTES);
      uboBokehStreakFine = createUbo("NightClient Atmo BokehStrF", AtmosphereEngine.BOKEH_STREAK_UBO_BYTES);
      uboDawn = createUbo("NightClient Atmo Dawn", AtmosphereEngine.DAWN_UBO_BYTES);
      uboCustomSky = createUbo("NightClient Custom Sky", AtmosphereEngine.CUSTOM_SKY_UBO_BYTES);
      uboMotionBlur = createUbo("NightClient Motion Blur", AtmosphereEngine.MOTION_BLUR_UBO_BYTES);
      uboMood = createUbo("NightClient Atmo Mood", AtmosphereEngine.MOOD_UBO_BYTES);
      uboPrecip = createUbo("NightClient Atmo Precip", AtmosphereEngine.PRECIP_UBO_BYTES);
    }
    if (columnsTex == null) {
      columnsTex =
          RenderSystem.getDevice()
              .createTexture(
                  "NightClient Atmo Columns",
                  GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                  @@ATMO_RGBA8_FORMAT@@,
                  AtmosphereColumns.TEX_WIDTH,
                  AtmosphereColumns.TEX_HEIGHT,
                  1,
                  1);
      @@ATMO_COLUMNS_TEX_SETUP@@
      columnsView = RenderSystem.getDevice().createTextureView(columnsTex);
      columnsImage = new NativeImage(AtmosphereColumns.TEX_WIDTH, AtmosphereColumns.TEX_HEIGHT, false);
      columnsUploaded = false;

      zeroTex =
          RenderSystem.getDevice()
              .createTexture(
                  "NightClient Atmo Zero",
                  GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                  @@ATMO_RGBA8_FORMAT@@,
                  1,
                  1,
                  1,
                  1);
      @@ATMO_LINEAR_ZERO_SETUP@@
      zeroView = RenderSystem.getDevice().createTextureView(zeroTex);
      try (NativeImage zImg = new NativeImage(1, 1, true)) {
        zImg.setPixel(0, 0, 0);
        encoder.writeToTexture(zeroTex, zImg);
      }

      loadSkyAtlas(encoder);
    }
    if (sceneCopy == null || sceneW != w || sceneH != h || !sceneCopy.getFormat().equals(mainColor.getFormat())) {
      closeSceneCopy();
      sceneW = w;
      sceneH = h;
      sceneCopy =
          RenderSystem.getDevice()
              .createTexture(
                  "NightClient Atmo SceneCopy",
                  GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                  mainColor.getFormat(),
                  w,
                  h,
                  1,
                  1);
      @@ATMO_LINEAR_SCENE_SETUP@@
      sceneCopyView = RenderSystem.getDevice().createTextureView(sceneCopy);
    }
    if (qA == null || qW != qw || qH != qh) {
      closeQuarter();
      qW = qw;
      qH = qh;
      int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
      qA = RenderSystem.getDevice().createTexture("NightClient Atmo qA", usage, @@ATMO_RGBA8_FORMAT@@, qw, qh, 1, 1);
      qB = RenderSystem.getDevice().createTexture("NightClient Atmo qB", usage, @@ATMO_RGBA8_FORMAT@@, qw, qh, 1, 1);
      qC = RenderSystem.getDevice().createTexture("NightClient Atmo qC", usage, @@ATMO_RGBA8_FORMAT@@, qw, qh, 1, 1);
      @@ATMO_LINEAR_QUARTER_SETUP@@
      qAView = RenderSystem.getDevice().createTextureView(qA);
      qBView = RenderSystem.getDevice().createTextureView(qB);
      qCView = RenderSystem.getDevice().createTextureView(qC);
    }
  }

  private static void loadSkyAtlas(CommandEncoder encoder) {
    String path = "/assets/nightclient/textures/sky/celestials.png";
    java.io.InputStream in = AtmosphereEngine.class.getResourceAsStream(path);
    if (in != null) {
      try (java.io.InputStream stream = in; NativeImage img = NativeImage.read(stream)) {
        if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
          skyAtlasTex =
              RenderSystem.getDevice()
                  .createTexture(
                      "NightClient Sky Atlas",
                      GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                      @@ATMO_RGBA8_FORMAT@@,
                      img.getWidth(),
                      img.getHeight(),
                      1,
                      1);
          @@ATMO_LINEAR_SKY_ATLAS_SETUP@@
          skyAtlasView = RenderSystem.getDevice().createTextureView(skyAtlasTex);
          encoder.writeToTexture(skyAtlasTex, img);
          return;
        }
      } catch (Throwable ignored) {
      }
    }
    skyAtlasTex = null;
    skyAtlasView = zeroView;
  }

  private static GpuBuffer createUbo(final String label, int size) {
    return RenderSystem.getDevice()
        .createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
  }

  private static void closeSceneCopy() {
    if (sceneCopyView != null) {
      sceneCopyView.close();
      sceneCopyView = null;
    }
    if (sceneCopy != null) {
      sceneCopy.close();
      sceneCopy = null;
    }
  }

  private static void closeQuarter() {
    if (qAView != null) {
      qAView.close();
      qAView = null;
    }
    if (qBView != null) {
      qBView.close();
      qBView = null;
    }
    if (qCView != null) {
      qCView.close();
      qCView = null;
    }
    if (qA != null) {
      qA.close();
      qA = null;
    }
    if (qB != null) {
      qB.close();
      qB = null;
    }
    if (qC != null) {
      qC.close();
      qC = null;
    }
  }

  private static void closeUbo(GpuBuffer buf) {
    if (buf != null) buf.close();
  }

  private static void disposeGpu() {
    closeSceneCopy();
    closeQuarter();
    if (columnsView != null) {
      columnsView.close();
      columnsView = null;
    }
    if (columnsTex != null) {
      columnsTex.close();
      columnsTex = null;
    }
    if (columnsImage != null) {
      columnsImage.close();
      columnsImage = null;
    }
    if (skyAtlasView != null && skyAtlasView != zeroView) {
      skyAtlasView.close();
    }
    skyAtlasView = null;
    if (skyAtlasTex != null) {
      skyAtlasTex.close();
      skyAtlasTex = null;
    }
    if (zeroView != null) {
      zeroView.close();
      zeroView = null;
    }
    if (zeroTex != null) {
      zeroTex.close();
      zeroTex = null;
    }
    closeUbo(uboBokehEx);
    closeUbo(uboBokehDisc);
    closeUbo(uboBokehStreakWide);
    closeUbo(uboBokehStreakFine);
    closeUbo(uboDawn);
    closeUbo(uboCustomSky);
    closeUbo(uboMotionBlur);
    closeUbo(uboMood);
    closeUbo(uboPrecip);
    uboBokehEx = uboBokehDisc = uboBokehStreakWide = uboBokehStreakFine = null;
    uboDawn = uboCustomSky = uboMotionBlur = uboMood = uboPrecip = null;
    @@ATMO_VERTEX_CLOSE@@
    pipeBokehEx = pipeBokehDisc = pipeBokehStreak = pipeDawn = pipeCustomSky = pipeMotionBlur = null;
    @@ATMO_PRECIP_PIPE_CLOSE@@
    for (int i = 0; i < pipeMood.length; i++) pipeMood[i] = null;
    sceneW = sceneH = qW = qH = 0;
    columnsUploaded = false;
  }

  public static void close() {
    ENGINE.reset();
    disposeGpu();
  }
}
