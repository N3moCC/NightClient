package dev.nightclient.renderer.atmosphere;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class AtmosphereEngine {
  public static final int BOKEH_EXTRACT_UBO_BYTES = 112;
  public static final int BOKEH_DISC_UBO_BYTES = 16;
  public static final int BOKEH_STREAK_UBO_BYTES = 32;
  public static final int DAWN_UBO_BYTES = 240;
  public static final int MOOD_UBO_BYTES = 1008;
  public static final int PRECIP_UBO_BYTES = 272;
  public static final int CUSTOM_SKY_UBO_BYTES = 192;
  public static final int MOTION_BLUR_UBO_BYTES = MotionBlurEngine.UBO_BYTES;

  private static final double ANCHOR_QUANTUM = 512.0;
  private static final double ANCHOR_HYSTERESIS = 512.0;
  private static final float BOKEH_FOCUS = 36.0f;
  private static final int AUTUMN_GROUND_SIDE = 26;
  private static final int AUTUMN_GROUND_SITES_PER_COLUMN = 9;
  private static final int AUTUMN_GROUND_COORD_MASK = (1 << 20) - 1;

  private final AtmosphereColumns columns = new AtmosphereColumns();
  private final AtmosphereSimulator simulator = new AtmosphereSimulator();
  private final MotionBlurEngine motionBlur = new MotionBlurEngine();
  private final FramePlan plan = new FramePlan();

  private long originNanos;
  private boolean anchorInit;
  private double anchorX;
  private double anchorZ;
  private Object lastWorldId;

  private final float[] viewProj = new float[16];
  private final float[] invViewProj = new float[16];
  private final float[] rgbScratchA = new float[3];
  private final float[] rgbScratchB = new float[3];
  private final float[] sunScreenScratch = new float[3];
  private final float[] palZenithScratch = new float[3];
  private final float[] palFogWarmScratch = new float[3];
  private final float[] skyTopScratch = new float[3];
  private final float[] skyHorizonScratch = new float[3];
  private final float[] skySunScratch = new float[3];
  private final float[] hazeColorScratch = new float[3];
  private final float[] particleColorScratch = new float[3];
  private final float[] shadowTintScratch = new float[3];
  private final float[] lightTintScratch = new float[3];
  private final float[] lightGainScratch = new float[3];
  private final float[] skyAccentScratch = new float[3];
  private final float[] skyCelTintScratch = new float[3];
  private final float[] moodDetail = new float[32];
  private final float[] fallDetail = new float[32];

  public static final class FramePlan {
    public boolean active;
    public boolean drawCustomSky;
    public boolean drawAtmosphere;
    public boolean drawMotionBlur;
    public boolean isDawn;
    public int moodKind;
    public boolean uploadColumns;
    public boolean runBokeh;
    public int quarterWidth;
    public int quarterHeight;
    public boolean drawPrecip;
    public int precipVertexCount;

    public final ByteBuffer bokehExtractUbo = directBuffer(BOKEH_EXTRACT_UBO_BYTES);
    public final ByteBuffer bokehDiscUbo = directBuffer(BOKEH_DISC_UBO_BYTES);
    public final ByteBuffer bokehStreakWideUbo = directBuffer(BOKEH_STREAK_UBO_BYTES);
    public final ByteBuffer bokehStreakFineUbo = directBuffer(BOKEH_STREAK_UBO_BYTES);
    public final ByteBuffer bokehCompositeUbo = directBuffer(16);
    public final ByteBuffer dawnUbo = directBuffer(DAWN_UBO_BYTES);
    public final ByteBuffer moodUbo = directBuffer(MOOD_UBO_BYTES);
    public final ByteBuffer precipUbo = directBuffer(PRECIP_UBO_BYTES);
    public final ByteBuffer customSkyUbo = directBuffer(CUSTOM_SKY_UBO_BYTES);
    public final ByteBuffer motionBlurUbo = directBuffer(MOTION_BLUR_UBO_BYTES);

    private static ByteBuffer directBuffer(int bytes) {
      return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
  }

  public static final long FORCED_DAY_TICKS = 6000L;
  public static final long FORCED_SUNSET_TICKS = 12650L;
  public static final long FORCED_NIGHT_TICKS = 18000L;
  public static final long FORCED_MIDNIGHT_TICKS = 21000L;

  public static boolean shouldForceNight(Settings settings, boolean isOverworld) {
    if (FeaturePolicy.isBlocked("atmosphere") || settings == null || !isOverworld) return false;
    Settings.Atmosphere a = settings.atmosphere;
    return a != null
        && a.enabled
        && ("mystic".equals(a.mode) || "halloween".equals(a.mode))
        && a.mysticWorld != null
        && a.mysticWorld.alwaysNight;
  }

  public static boolean isCustomSkyActive(Settings settings, boolean isOverworld) {
    if (FeaturePolicy.isBlocked("customSky") || settings == null || settings.customSky == null) return false;
    Settings.CustomSky cs = settings.customSky;
    return cs.enabled && !"vanilla".equals(cs.style) && (isOverworld || cs.endAndNether);
  }

  public static boolean isMotionBlurActive(Settings settings) {
    return !FeaturePolicy.isBlocked("motionBlur")
        && settings != null
        && settings.motionBlur != null
        && settings.motionBlur.enabled
        && settings.motionBlur.strength > 0.0f;
  }

  public static long forcedOverworldTime(Settings settings, boolean isOverworld) {
    if (settings == null || !isOverworld) return -1L;
    Settings.CustomSky cs = settings.customSky;
    if (!FeaturePolicy.isBlocked("customSky") && cs != null && cs.enabled) {
      if ("day".equals(cs.timeMode)) return FORCED_DAY_TICKS;
      if ("sunset".equals(cs.timeMode)) return FORCED_SUNSET_TICKS;
      if ("night".equals(cs.timeMode)) return FORCED_NIGHT_TICKS;
      if ("midnight".equals(cs.timeMode)) return FORCED_MIDNIGHT_TICKS;
      if ("custom".equals(cs.timeMode)) return Math.max(0L, Math.min(24000L, cs.customTime));
    }
    Settings.WorldTime wt = settings.worldTime;
    if (!FeaturePolicy.isBlocked("customSky") && wt != null && wt.enabled) {
      if ("day".equals(wt.time)) return FORCED_DAY_TICKS;
      if ("night".equals(wt.time)) return FORCED_NIGHT_TICKS;
    }
    if (shouldForceNight(settings, true)) return FORCED_NIGHT_TICKS;
    return -1L;
  }

  public static boolean shouldClearWeather(Settings settings, boolean isOverworld) {
    if (settings == null) return false;
    Settings.NoRender nr = settings.noRender;
    if (!FeaturePolicy.isBlocked("noRender") && nr != null && nr.enabled && nr.weather) return true;
    Settings.WorldTime wt = settings.worldTime;
    if (!FeaturePolicy.isBlocked("noRender") && wt != null && wt.enabled && wt.noRain) return true;
    return shouldForceNight(settings, isOverworld);
  }

  public static boolean usesCustomClouds(Settings settings, boolean isOverworld) {
    if (settings == null) return false;
    if (isCustomSkyActive(settings, isOverworld) && settings.customSky.clouds > 0.5f) {
      return true;
    }
    if (!isOverworld || FeaturePolicy.isBlocked("atmosphere")) return false;
    Settings.Atmosphere atmosphere = settings.atmosphere;
    if (atmosphere == null || !atmosphere.enabled) return false;
    AtmospherePresets.MoodPreset preset = AtmospherePresets.presetForMode(atmosphere.mode);
    return preset != null && (preset.kind == AtmospherePresets.KIND_SAKURA
        || preset.kind == AtmospherePresets.KIND_AUTUMN
        || preset.kind == AtmospherePresets.KIND_MYSTIC);
  }

  public AtmosphereColumns columns() {
    return columns;
  }

  public AtmosphereSimulator simulator() {
    return simulator;
  }

  public void resetMotionBlur() {
    motionBlur.reset();
  }

  public void reset() {
    columns.reset();
    simulator.reset();
    motionBlur.reset();
    originNanos = 0L;
    anchorInit = false;
    lastWorldId = null;
  }

  public FramePlan prepareFrame(
      Settings settings,
      AtmosphereWorldAccess world,
      int width,
      int height,
      double camX,
      double camY,
      double camZ,
      float pitchDeg,
      float yawDeg,
      float[] viewRotationColMajor,
      float[] projectionColMajor,
      int reversedDepth) {
    plan.active = false;
    plan.drawCustomSky = false;
    plan.drawAtmosphere = false;
    plan.drawMotionBlur = false;
    plan.uploadColumns = false;
    plan.runBokeh = false;
    plan.drawPrecip = false;
    plan.precipVertexCount = 0;

    if (settings == null || world == null || width <= 1 || height <= 1
        || viewRotationColMajor == null || projectionColMajor == null
        || viewRotationColMajor.length < 16 || projectionColMajor.length < 16
        || !Double.isFinite(camX) || !Double.isFinite(camY) || !Double.isFinite(camZ)) {
      motionBlur.reset();
      return plan;
    }
    Settings.Atmosphere cfg = settings.atmosphere;
    boolean atmoOn = !FeaturePolicy.isBlocked("atmosphere") && cfg != null && cfg.enabled;
    if (atmoOn) ensureSubObjects(cfg);
    boolean skyOn = isCustomSkyActive(settings, world.isOverworld());
    boolean mbOn = isMotionBlurActive(settings);
    if (!mbOn) {
      motionBlur.reset();
    }
    if (!atmoOn && !skyOn && !mbOn) {
      return plan;
    }
    Object worldId = world.worldIdentity();
    if (worldId == null) {
      motionBlur.reset();
      return plan;
    }
    if (worldId != lastWorldId) {
      anchorInit = false;
      lastWorldId = worldId;
    }

    multiplyMat4(projectionColMajor, viewRotationColMajor, viewProj);
    if (!invertMat4(viewProj, invViewProj)) {
      motionBlur.reset();
      return plan;
    }

    long now = System.nanoTime();
    if (originNanos == 0L) {
      originNanos = now;
    }
    double timeSec = (double) (now - originNanos) * 1.0e-9;
    float timeFloat = (float) (timeSec % 86400.0);

    if (mbOn) {
      plan.drawMotionBlur =
          motionBlur.prepareFrame(
              settings.motionBlur,
              worldId,
              camX,
              camY,
              camZ,
              viewRotationColMajor,
              projectionColMajor,
              reversedDepth,
              now,
              plan.motionBlurUbo);
    }

    if (!anchorInit
        || Math.abs(camX - anchorX) > ANCHOR_HYSTERESIS
        || Math.abs(camZ - anchorZ) > ANCHOR_HYSTERESIS) {
      anchorX = Math.rint(camX / ANCHOR_QUANTUM) * ANCHOR_QUANTUM;
      anchorZ = Math.rint(camZ / ANCHOR_QUANTUM) * ANCHOR_QUANTUM;
      anchorInit = true;
    }
    float anchoredCamX = (float) (camX - anchorX);
    float anchoredCamY = (float) camY;
    float anchoredCamZ = (float) (camZ - anchorZ);

    plan.quarterWidth = Math.max(1, width / 4);
    plan.quarterHeight = Math.max(1, height / 4);

    if (skyOn) {
      prepareCustomSkyFrame(
          settings.customSky,
          world,
          anchoredCamX,
          anchoredCamY,
          anchoredCamZ,
          timeFloat,
          reversedDepth);
      plan.drawCustomSky = true;
    }

    if (!atmoOn) {
      plan.active = plan.drawCustomSky || plan.drawMotionBlur;
      return plan;
    }
    plan.drawAtmosphere = true;

    float projYScale = Math.max(0.05f, Math.abs(projectionColMajor[5]));
    float pixelWorld = 2.0f / (projYScale * Math.max(1.0f, (float) height));

    float pitchRad = pitchDeg * ((float) Math.PI / 180.0f);
    float yawRad = yawDeg * ((float) Math.PI / 180.0f);
    float cosPitch = (float) Math.cos(pitchRad);
    float lookX = -(float) Math.sin(yawRad) * cosPitch;
    float lookY = -(float) Math.sin(pitchRad);
    float lookZ = (float) Math.cos(yawRad) * cosPitch;

    AtmospherePresets.MoodPreset preset = AtmospherePresets.presetForMode(cfg.mode);
    boolean isDawn = (preset == null);
    plan.isDawn = isDawn;

    if (isDawn) {
      prepareDawnFrame(
          cfg,
          world,
          width,
          height,
          anchoredCamX,
          anchoredCamY,
          anchoredCamZ,
          timeFloat,
          reversedDepth);
      plan.active = true;
      return plan;
    }

    int kind = preset.kind;
    plan.moodKind = kind;

    columns.update(world, camX, camY, camZ);
    plan.uploadColumns = columns.consumeDirty();

    long dayTime = world.dayTime();
    float celAngle = AtmospherePresets.dawnTimeRadians(dayTime);
    float nightFactor =
        kind == AtmospherePresets.KIND_MYSTIC
            ? 1.0f
            : AtmospherePresets.clamp01(0.5f - (float) Math.cos(celAngle) * 0.65f);

    simulator.update(
        cfg,
        kind,
        world,
        columns,
        camX,
        camY,
        camZ,
        lookX,
        lookY,
        lookZ,
        timeSec);

    prepareSeasonalFrame(
        cfg,
        preset,
        world,
        width,
        height,
        camX,
        camY,
        camZ,
        anchoredCamX,
        anchoredCamY,
        anchoredCamZ,
        timeSec,
        timeFloat,
        pixelWorld,
        celAngle,
        nightFactor,
        reversedDepth);

    plan.active = true;
    return plan;
  }

  private void prepareDawnFrame(
      Settings.Atmosphere cfg,
      AtmosphereWorldAccess world,
      int width,
      int height,
      float camPosX,
      float camPosY,
      float camPosZ,
      float timeFloat,
      int reversedDepth) {
    long dayTime = world.dayTime();
    float celAngle = AtmospherePresets.dawnTimeRadians(dayTime);
    float sign = -Math.sin(celAngle) < 0.0 ? -1.0f : 1.0f;
    float sunX = (float) Math.cos(0.11f) * sign;
    float sunY = (float) Math.sin(0.11f);
    float sunZ = 0.0f;

    float[] sunScreen = sunScreenScratch;
    projectDirectionToScreen(sunX, sunY, sunZ, viewProj, sunScreen);

    AtmospherePresets.unpackRgb(cfg.dawnColor, rgbScratchA);
    float sr = rgbScratchA[0];
    float sg = rgbScratchA[1];
    float sb = rgbScratchA[2];

    float[] palZenith = palZenithScratch;
    float[] palFogWarm = palFogWarmScratch;
    AtmospherePresets.oklabMix(sr, sg, sb, 0.16f, 0.19f, 0.38f, 0.14f, palZenith);
    AtmospherePresets.oklabMix(sr, sg, sb, 0.95f, 0.55f, 0.63f, 0.42f, palFogWarm);

    float fogDensity = AtmospherePresets.clamp(cfg.density, 0.05f, 0.80f);
    float fogMaxHeight = cfg.scatterHeight;
    float fogMinHeight = fogMaxHeight - 18.0f;
    float viewDistance = Math.max(2.0f, (float) world.renderDistanceChunks()) * 16.0f;
    float godRays = AtmospherePresets.clamp01(cfg.godRays / 100.0f);
    float softness = AtmospherePresets.clamp01(cfg.softness / 100.0f);

    if (world.cameraSubmerged()) {
      fogDensity *= 0.25f;
      godRays = 0.0f;
    }

    ByteBuffer b = plan.dawnUbo;
    b.clear();
    putMat4(b, invViewProj);
    putVec4(b, camPosX, camPosY, camPosZ, timeFloat);
    putVec4(b, (float) width, (float) height, fogDensity, fogMinHeight);
    putVec4(b, fogMaxHeight, viewDistance, godRays, softness);
    putVec4(b, sunX, sunY, sunZ, (float) reversedDepth);
    putVec4(b, sunScreen[0], sunScreen[1], sunScreen[2], 0.0f);
    putVec4(b, palZenith[0], palZenith[1], palZenith[2], 0.0f);
    putVec4(
        b,
        AtmospherePresets.clamp01(sr * 1.12f),
        AtmospherePresets.clamp01(sg * 0.88f),
        AtmospherePresets.clamp01(sb * 0.62f),
        0.0f);
    putVec4(b, 0.56f, 0.62f, 0.80f, 0.0f);
    putVec4(b, palFogWarm[0], palFogWarm[1], palFogWarm[2], 0.0f);
    putVec4(b, 0.60f, 0.67f, 0.82f, 0.0f);
    putVec4(
        b,
        AtmospherePresets.clamp01(sr * 1.08f),
        AtmospherePresets.clamp01(sg * 0.94f),
        AtmospherePresets.clamp01(sb * 0.72f),
        0.0f);
    b.flip();
  }

  private void prepareCustomSkyFrame(
      Settings.CustomSky cs,
      AtmosphereWorldAccess world,
      float camPosX,
      float camPosY,
      float camPosZ,
      float timeFloat,
      int reversedDepth) {
    int styleKind;
    int atlasTile;
    float[] top = skyTopScratch;
    float[] horizon = skyHorizonScratch;
    float[] accent = skyAccentScratch;
    float[] celTint = skyCelTintScratch;
    celTint[0] = 1.0f;
    celTint[1] = 1.0f;
    celTint[2] = 1.0f;
    float celGlow;

    if ("aurora".equals(cs.style)) {
      styleKind = 1;
      atlasTile = 1;
      top[0] = 0.012f; top[1] = 0.035f; top[2] = 0.086f;
      horizon[0] = 0.043f; horizon[1] = 0.157f; horizon[2] = 0.231f;
      accent[0] = 0.176f; accent[1] = 0.831f; accent[2] = 0.749f;
      celTint[0] = 0.88f; celTint[1] = 0.96f; celTint[2] = 0.98f;
      celGlow = 0.55f;
    } else if ("sunset".equals(cs.style)) {
      styleKind = 2;
      atlasTile = 2;
      top[0] = 0.090f; top[1] = 0.047f; top[2] = 0.220f;
      horizon[0] = 0.957f; horizon[1] = 0.247f; horizon[2] = 0.369f;
      accent[0] = 0.984f; accent[1] = 0.573f; accent[2] = 0.235f;
      celTint[0] = 1.00f; celTint[1] = 0.94f; celTint[2] = 0.88f;
      celGlow = 0.82f;
    } else if ("anime".equals(cs.style)) {
      styleKind = 3;
      atlasTile = 3;
      top[0] = 0.071f; top[1] = 0.388f; top[2] = 0.831f;
      horizon[0] = 0.608f; horizon[1] = 0.886f; horizon[2] = 1.000f;
      accent[0] = 0.996f; accent[1] = 0.941f; accent[2] = 0.541f;
      celTint[0] = 1.00f; celTint[1] = 0.99f; celTint[2] = 0.95f;
      celGlow = 0.78f;
    } else if ("blood_moon".equals(cs.style)) {
      styleKind = 4;
      atlasTile = 1;
      top[0] = 0.043f; top[1] = 0.012f; top[2] = 0.031f;
      horizon[0] = 0.329f; horizon[1] = 0.051f; horizon[2] = 0.102f;
      accent[0] = 0.937f; accent[1] = 0.267f; accent[2] = 0.267f;
      celTint[0] = 0.98f; celTint[1] = 0.34f; celTint[2] = 0.28f;
      celGlow = 0.85f;
    } else if ("pastel".equals(cs.style)) {
      styleKind = 5;
      atlasTile = 1;
      top[0] = 0.290f; top[1] = 0.357f; top[2] = 0.722f;
      horizon[0] = 0.949f; horizon[1] = 0.722f; horizon[2] = 0.831f;
      accent[0] = 0.769f; accent[1] = 0.710f; accent[2] = 0.992f;
      celTint[0] = 1.00f; celTint[1] = 0.96f; celTint[2] = 0.99f;
      celGlow = 0.45f;
    } else {
      styleKind = 0;
      atlasTile = 0;
      top[0] = 0.020f; top[1] = 0.027f; top[2] = 0.086f;
      horizon[0] = 0.114f; horizon[1] = 0.082f; horizon[2] = 0.271f;
      accent[0] = 0.545f; accent[1] = 0.361f; accent[2] = 0.965f;
      celTint[0] = 0.94f; celTint[1] = 0.92f; celTint[2] = 1.00f;
      celGlow = 0.65f;
    }

    if (cs.useCustomColors) {
      AtmospherePresets.unpackRgb(cs.topColor, top);
      AtmospherePresets.unpackRgb(cs.horizonColor, horizon);
      AtmospherePresets.unpackRgb(cs.accentColor, accent);
      if (atlasTile == 1) {
        celTint[0] = 0.55f + 0.45f * accent[0];
        celTint[1] = 0.55f + 0.45f * accent[1];
        celTint[2] = 0.55f + 0.45f * accent[2];
      }
    }

    float brightness = AtmospherePresets.clamp(cs.brightness / 100.0f, 0.30f, 1.60f);
    float rotSpeed = AtmospherePresets.clamp(cs.rotationSpeed / 100.0f, 0.0f, 2.0f);
    float stars = AtmospherePresets.clamp01(cs.stars / 100.0f);
    float clouds = AtmospherePresets.clamp01(cs.clouds / 100.0f);
    float horizonGlow = AtmospherePresets.clamp01(cs.horizonGlow / 100.0f);
    float celSize = AtmospherePresets.clamp(cs.celestialSize / 100.0f, 0.40f, 2.20f);
    float elevRad = AtmospherePresets.clamp(cs.celestialHeight, 5.0f, 75.0f) * ((float) Math.PI / 180.0f);
    float azRad = 0.52f + timeFloat * rotSpeed * 0.008f;
    float cosEl = (float) Math.cos(elevRad);
    float celX = (float) Math.cos(azRad) * cosEl;
    float celY = (float) Math.sin(elevRad);
    float celZ = (float) Math.sin(azRad) * cosEl;
    float submerged = world.cameraSubmerged() ? 1.0f : 0.0f;

    ByteBuffer b = plan.customSkyUbo;
    b.clear();
    putMat4(b, invViewProj);
    putVec4(b, camPosX, camPosY, camPosZ, timeFloat);
    putVec4(b, brightness, rotSpeed, stars, clouds);
    putVec4(b, celX, celY, celZ, (float) reversedDepth);
    putVec4(b, top[0], top[1], top[2], (float) styleKind);
    putVec4(b, horizon[0], horizon[1], horizon[2], (float) atlasTile);
    putVec4(b, accent[0], accent[1], accent[2], horizonGlow);
    putVec4(b, celTint[0], celTint[1], celTint[2], celGlow);
    putVec4(b, celSize, cs.celestial ? 1.0f : 0.0f, submerged, 0.0f);
    b.flip();
  }

  private void prepareSeasonalFrame(
      Settings.Atmosphere cfg,
      AtmospherePresets.MoodPreset preset,
      AtmosphereWorldAccess world,
      int width,
      int height,
      double camX,
      double camY,
      double camZ,
      float camPosX,
      float camPosY,
      float camPosZ,
      double timeSec,
      float timeFloat,
      float pixelWorld,
      float celAngle,
      float nightFactor,
      int reversedDepth) {
    int kind = preset.kind;
    boolean submerged = world.cameraSubmerged();
    boolean hasMap = columns.hasValid();
    float groundY = columns.groundYSmooth();
    float viewDistance = Math.max(2.0f, (float) world.renderDistanceChunks()) * 16.0f;

    float densityFrac = cfg.amount / 100.0f;
    float speed = cfg.speed / 100.0f;
    float haze = (cfg.haze / 100.0f) * preset.hazeScale * 2.0f;
    float grade = cfg.grade / 100.0f;
    float vignette = cfg.vignette / 100.0f;
    float ground =
        (kind == AtmospherePresets.KIND_SNOW
                || kind == AtmospherePresets.KIND_SAKURA
                || kind == AtmospherePresets.KIND_AUTUMN
                || kind == AtmospherePresets.KIND_MYSTIC)
            ? (cfg.ground / 100.0f)
            : 1.0f;
    float amount =
        (kind == AtmospherePresets.KIND_DUST || kind == AtmospherePresets.KIND_FIREFLIES)
            ? densityFrac
            : (kind == AtmospherePresets.KIND_SAKURA
                ? (cfg.sakura.hotaru / 100.0f) * 0.6f
                : 0.0f);

    float lights = preset.lights;
    float rays = preset.rays;
    float shimmer = preset.shimmer;
    float skyMix = preset.skyMix;
    float chroma = preset.chroma;
    float relight = preset.relight;
    float occlusion = preset.occlusion;
    float snowCover = preset.snowCover;

    float[] skyTop = copy3(preset.skyTop, skyTopScratch);
    float[] skyHorizon = copy3(preset.skyHorizon, skyHorizonScratch);
    float[] skySun = copy3(preset.skySun, skySunScratch);
    float[] hazeColor = copy3(preset.hazeColor, hazeColorScratch);
    float[] particleColor = copy3(preset.particleColor, particleColorScratch);
    float[] shadowTint = copy3(preset.shadowTint, shadowTintScratch);
    float[] lightTint = copy3(preset.lightTint, lightTintScratch);
    float[] lightGain = copy3(preset.lightGain, lightGainScratch);

    float elev =
        kind == AtmospherePresets.KIND_AUTUMN
            ? (cfg.autumnSky.sunHeight * ((float) Math.PI / 180.0f))
            : preset.sunElevationRad;
    float sign = -Math.sin(celAngle) < 0.0 ? -1.0f : 1.0f;
    float sunX = (float) Math.cos(elev) * sign;
    float sunY = (float) Math.sin(elev);
    float sunZ = 0.0f;

    float lightDirX = preset.useSunAsLightDir ? sunX : 0.0f;
    float lightDirY = preset.useSunAsLightDir ? sunY : 1.0f;
    float lightDirZ = preset.useSunAsLightDir ? sunZ : 0.0f;

    Arrays.fill(moodDetail, 0.0f);
    float blizzardWind = 0.0f;

    if (kind == AtmospherePresets.KIND_SNOW) {
      float bKnob = cfg.snowfall.wind / 100.0f;
      blizzardWind =
          (bKnob <= 0.3f ? (bKnob / 0.3f) : (1.0f + ((bKnob - 0.3f) / 0.7f) * 7.0f)) - 1.0f;
      moodDetail[0] = cfg.snowCover.frost / 100.0f;
      moodDetail[1] = cfg.snowCover.shelter / 100.0f;
      moodDetail[2] = cfg.snowCover.coldShadows / 100.0f;
      moodDetail[3] = cfg.snowCover.drifts / 100.0f;

      moodDetail[4] = (cfg.snowCover.thickness / 100.0f) * 2.0f - 1.0f;
      moodDetail[5] = cfg.snowCover.texture / 100.0f;
      moodDetail[6] = (cfg.snowCover.sparkle / 100.0f) * 2.0f - 1.0f;
      moodDetail[7] = cfg.snowCover.leaves / 100.0f;

      moodDetail[8] = cfg.snowCover.ice / 100.0f;
      moodDetail[9] = (cfg.snowfall.farSnow / 100.0f) * densityFrac * 2.0f;
      moodDetail[10] = cfg.snowCover.cloudShadows / 100.0f;
      moodDetail[11] = blizzardWind;

      if (!cfg.snowCover.daytime) {
        float ov = 1.0f - columns.caveFactor();
        lerp3(skyTop, 0.045f, 0.055f, 0.095f, ov);
        lerp3(skyHorizon, 0.10f, 0.11f, 0.16f, ov);
        lerp3(hazeColor, 0.11f, 0.12f, 0.17f, ov);
      }
    } else if (kind == AtmospherePresets.KIND_SAKURA) {
      lightDirX = 0.0181f;
      lightDirY = 0.3873f;
      lightDirZ = 0.9218f;
      int petalArgb = cfg.sakura.blossomColor;
      float pr = (float) ((petalArgb >>> 16) & 255);
      float pg = (float) ((petalArgb >>> 8) & 255);
      float pb = (float) (petalArgb & 255);

      moodDetail[0] = cfg.sakura.blossom / 100.0f;
      moodDetail[1] = cfg.sakuraSky.clouds / 100.0f;
      moodDetail[2] = cfg.sakuraSky.stars / 100.0f;
      moodDetail[3] = cfg.sakuraSky.volcano ? 1.0f : 0.0f;

      moodDetail[4] = pr / 246.0f - 1.0f;
      moodDetail[5] = pg / 195.0f - 1.0f;
      moodDetail[6] = pb / 213.0f - 1.0f;
      moodDetail[7] = cfg.sakuraSky.pagoda ? 1.0f : 0.0f;

      moodDetail[8] = cfg.sakuraWater.petals / 50.0f;
      moodDetail[9] = cfg.sakuraWater.moonPath / 100.0f;
      moodDetail[10] = cfg.sakuraWater.reflections / 100.0f;
      moodDetail[11] = cfg.sakuraWater.mist / 100.0f;
    } else if (kind == AtmospherePresets.KIND_AUTUMN) {
      rays = cfg.autumnSky.sunRays / 100.0f;
      moodDetail[0] = cfg.autumnTrees.treeColors / 100.0f;
      moodDetail[1] = cfg.autumnTrees.evergreen ? 1.0f : 0.0f;
      moodDetail[2] = cfg.autumnTrees.bareTrees / 100.0f;
      moodDetail[3] = 0.0f;

      moodDetail[4] = cfg.autumnGround.piles / 100.0f;
      moodDetail[5] = cfg.autumnGround.dryGrass / 100.0f;
      moodDetail[6] = cfg.autumnGround.puddles / 100.0f;
      moodDetail[7] = cfg.autumnGround.dew / 100.0f;

      moodDetail[8] = cfg.autumnSky.birds / 100.0f;
      moodDetail[9] = cfg.autumnSky.clouds / 100.0f;
      moodDetail[10] = cfg.autumnSky.farForest ? 1.0f : 0.0f;
      moodDetail[11] = cfg.autumnSky.mist / 100.0f;

      System.arraycopy(viewProj, 0, moodDetail, 16, 16);
    } else if (kind == AtmospherePresets.KIND_MYSTIC) {
      boolean isOverworld = world.isOverworld();
      boolean moonOn = cfg.mysticSky.moon && isOverworld;
      float mElev = cfg.mysticSky.moonHeight * ((float) Math.PI / 180.0f);
      float cosE = (float) Math.cos(mElev);
      sunX = cosE * 0.924f;
      sunY = (float) Math.sin(mElev);
      sunZ = cosE * 0.383f;

      lights = preset.lights * (cfg.mysticLens.bokeh / 30.0f);
      AtmospherePresets.unpackRgb(cfg.mysticFog.fogColor, rgbScratchB);
      lerp3(hazeColor, rgbScratchB[0] * 0.52f, rgbScratchB[1] * 0.52f, rgbScratchB[2] * 0.52f, 0.65f);

      float blueNight = AtmospherePresets.clamp01(cfg.mysticWorld.blueNight / 100.0f);
      grade *= 0.5f + 0.8f * blueNight;

      float keyElev = Math.max(mElev, 0.6108653f);
      AtmospherePresets.unpackRgb(cfg.mysticSky.moonColor, rgbScratchA);
      float lum = Math.max(0.05f, AtmospherePresets.luminance(rgbScratchA[0], rgbScratchA[1], rgbScratchA[2]));
      if (moonOn) {
        float cosK = (float) Math.cos(keyElev);
        lightDirX = cosK * 0.924f;
        lightDirY = (float) Math.sin(keyElev);
        lightDirZ = cosK * 0.383f;
        skySun[0] = rgbScratchA[0] * 0.22f;
        skySun[1] = rgbScratchA[1] * 0.22f;
        skySun[2] = rgbScratchA[2] * 0.22f;
        rays = (cfg.mysticSky.moonRays / 100.0f) * 0.9f;
        lightGain[0] *= rgbScratchA[0] / lum;
        lightGain[1] *= rgbScratchA[1] / lum;
        lightGain[2] *= rgbScratchA[2] / lum;
      } else {
        lightDirX = 0.0f;
        lightDirY = 1.0f;
        lightDirZ = 0.0f;
        skySun[0] = skySun[1] = skySun[2] = 0.0f;
        rays = 0.0f;
      }
      if (!isOverworld) {
        skyMix = 0.0f;
      }
      snowCover = (!columns.isCave() && isOverworld) ? 1.0f : 0.0f;
      float qualityFloat = (float) AtmosphereSimulator.qualityTier(cfg.quality);
      float shelter = columns.roofShelter();

      moodDetail[0] = moonOn ? (cfg.mysticWorld.moonlight / 100.0f) : 0.0f;
      moodDetail[1] = cfg.mysticSky.silhouettes / 100.0f;
      moodDetail[2] = cfg.mysticWorld.coldShadows / 100.0f;
      moodDetail[3] = cfg.mysticWorld.wither / 100.0f;

      moodDetail[4] = moonOn ? (cfg.mysticSky.moonSize / 100.0f) * 0.042f : 0.0f;
      moodDetail[5] = cfg.mysticSky.halo / 100.0f;
      moodDetail[6] = cfg.mysticSky.stars / 100.0f;
      moodDetail[7] = cfg.mysticSky.clouds / 100.0f;

      moodDetail[8] = simulator.lightningPacked;
      moodDetail[9] = cfg.mysticSky.meteors / 100.0f;
      moodDetail[10] = cfg.mysticLens.grain / 100.0f;
      moodDetail[11] = cfg.mysticSky.meteors / 100.0f;

      moodDetail[12] =
          (columns.isCave() ? 0.0f : (cfg.mysticFog.groundFog / 100.0f))
              * (1.0f - shelter * 0.5f);
      moodDetail[13] = cfg.mysticFog.fogHeight;
      moodDetail[14] = groundY - 1.0f;
      moodDetail[15] = cfg.mysticFog.fogTendrils / 100.0f;

      moodDetail[16] = cfg.mysticWorld.wetness / 100.0f;
      moodDetail[17] = cfg.mysticWorld.reflections / 100.0f;
      moodDetail[18] = cfg.mysticWorld.puddles / 100.0f;
      moodDetail[19] = shelter;

      moodDetail[20] = cfg.mysticSky.cloudShadows / 100.0f;
      moodDetail[21] = 0.0f;
      moodDetail[22] = 0.0f;
      moodDetail[23] = 0.3f * (1.0f - 0.5f * blueNight) * nightFactor;

      moodDetail[24] = (float) simulator.activeWispCount;
      moodDetail[25] = simulator.ghostSeedOut;
      moodDetail[26] = 2.0f;
      moodDetail[27] = qualityFloat;

      moodDetail[28] = simulator.ghostRelX;
      moodDetail[29] = simulator.ghostRelY;
      moodDetail[30] = simulator.ghostRelZ;
      moodDetail[31] = simulator.ghostAlpha;
    }

    if (submerged) {
      haze *= 0.25f;
      snowCover = 0.0f;
      lights = 0.0f;
    }

    float[] sunScreen = sunScreenScratch;
    projectDirectionToScreen(sunX, sunY, sunZ, viewProj, sunScreen);

    float mapOriginX = hasMap ? (float) ((double) columns.originX() - camX) : 0.0f;
    float mapOriginZ = hasMap ? (float) ((double) columns.originZ() - camZ) : 0.0f;

    if (lights > 0.001f) {
      packBokehUbos(
          width,
          height,
          camPosX,
          camPosY,
          camPosZ,
          reversedDepth,
          preset.bokehThreshold,
          BOKEH_FOCUS,
          4.5f,
          1.15f,
          0.85f,
          1.0f,
          preset.streakTint[0],
          preset.streakTint[1],
          preset.streakTint[2]);
      plan.runBokeh = true;
    }

    ByteBuffer mb = plan.moodUbo;
    mb.clear();
    putMat4(mb, invViewProj);
    putVec4(mb, camPosX, camPosY, camPosZ, timeFloat);
    putVec4(mb, (float) width, (float) height, pixelWorld, viewDistance);
    putVec4(mb, sunX, sunY, sunZ, groundY);
    putVec4(mb, sunScreen[0], sunScreen[1], sunScreen[2], amount);
    putVec4(mb, speed, haze, grade, lights);
    putVec4(mb, vignette, rays, shimmer, skyMix);
    putVec4(mb, chroma, relight, occlusion, snowCover);
    putVec4(mb, ground, (float) simulator.lampCount, simulator.lampReach, hasMap ? 1.0f : 0.0f);
    putVec4(mb, mapOriginX, 0.0f, mapOriginZ, (float) reversedDepth);
    putVec4(mb, skyTop[0], skyTop[1], skyTop[2], BOKEH_FOCUS);
    putVec4(mb, skyHorizon[0], skyHorizon[1], skyHorizon[2], 0.0f);
    putVec4(mb, skySun[0], skySun[1], skySun[2], 0.0f);
    putVec4(mb, hazeColor[0], hazeColor[1], hazeColor[2], 0.0f);
    putVec4(mb, particleColor[0], particleColor[1], particleColor[2], 0.0f);
    putVec4(mb, shadowTint[0], shadowTint[1], shadowTint[2], 0.0f);
    putVec4(mb, lightTint[0], lightTint[1], lightTint[2], 0.0f);
    putVec4(mb, lightDirX, lightDirY, lightDirZ, 0.0f);
    putVec4(mb, lightGain[0], lightGain[1], lightGain[2], 0.0f);
    putVec4(
        mb,
        preset.gradeParams[0],
        preset.gradeParams[1],
        preset.gradeParams[2],
        preset.gradeParams[3]);
    for (int i = 0; i < 32; i++) {
      mb.putFloat(moodDetail[i]);
    }
    for (int i = 0; i < AtmosphereSimulator.MAX_LAMPS * 8; i++) {
      mb.putFloat(simulator.lampsUbo[i]);
    }
    mb.flip();

    if (hasMap
        && !submerged
        && (kind == AtmospherePresets.KIND_SNOW
            || kind == AtmospherePresets.KIND_SAKURA
            || kind == AtmospherePresets.KIND_AUTUMN
            || kind == AtmospherePresets.KIND_MYSTIC)) {
      packPrecipStage(
          cfg,
          preset,
          world,
          width,
          height,
          camX,
          camY,
          camZ,
          timeSec,
          timeFloat,
          pixelWorld,
          speed,
          haze,
          vignette,
          ground,
          snowCover,
          groundY,
          sunX,
          sunY,
          sunZ,
          skyHorizon,
          hazeColor,
          blizzardWind,
          nightFactor,
          reversedDepth);
    }
  }

  private void packPrecipStage(
      Settings.Atmosphere cfg,
      AtmospherePresets.MoodPreset preset,
      AtmosphereWorldAccess world,
      int width,
      int height,
      double camX,
      double camY,
      double camZ,
      double timeSec,
      float timeFloat,
      float pixelWorld,
      float speed,
      float haze,
      float vignette,
      float ground,
      float snowCover,
      float groundY,
      float sunX,
      float sunY,
      float sunZ,
      float[] skyHorizon,
      float[] hazeColor,
      float blizzardWind,
      float nightFactor,
      int reversedDepth) {
    int kind = preset.kind;
    float densityFrac = cfg.amount / 100.0f;
    Arrays.fill(fallDetail, 0.0f);

    int particleCount = 0;
    float boxSize = 28.0f;
    float tintR = preset.particleColor[0];
    float tintG = preset.particleColor[1];
    float tintB = preset.particleColor[2];

    if (kind == AtmospherePresets.KIND_SNOW) {
      int base = Math.round(densityFrac * 14000.0f);
      int drifts = Math.round((cfg.snowfall.spindrift / 100.0f) * 2000.0f);
      particleCount = base + drifts;
      boxSize = 48.0f;

      fallDetail[0] = cfg.snowfall.flakeSize / 100.0f - 1.0f;
      fallDetail[1] = cfg.snowfall.clumps / 100.0f;
      fallDetail[2] = blizzardWind;
      fallDetail[3] = cfg.snowfall.spindrift / 100.0f;

      fallDetail[4] = (float) base;
      fallDetail[5] = cfg.snowCover.frost / 100.0f;
      fallDetail[6] = haze * 0.012f;
      fallDetail[7] =
          AtmospherePresets.luminance(skyHorizon[0], skyHorizon[1], skyHorizon[2]);
    } else if (kind == AtmospherePresets.KIND_SAKURA) {
      int base = Math.round(densityFrac * 3200.0f);
      float tree = cfg.sakura.nearTrees / 100.0f;
      float gust = cfg.sakura.gusts / 100.0f;
      particleCount = Math.round((float) base * (1.0f + 1.5f * tree) * (1.0f + gust));
      boxSize = 44.0f;

      int petalArgb = cfg.sakura.blossomColor;
      tintR *= ((petalArgb >>> 16) & 255) / 246.0f;
      tintG *= ((petalArgb >>> 8) & 255) / 195.0f;
      tintB *= (petalArgb & 255) / 213.0f;

      fallDetail[0] = 0.0f;
      fallDetail[1] = cfg.sakura.wholeFlowers / 100.0f;
      fallDetail[2] = tree;
      fallDetail[3] = gust;

      fallDetail[4] = 1.5f * tree;
    } else if (kind == AtmospherePresets.KIND_AUTUMN) {
      float gc = AtmospherePresets.clamp(ground, 0.0f, 2.0f);
      int falling = Math.round(densityFrac * 2600.0f);
      int rolling = Math.round((cfg.autumnAir.skitter / 100.0f) * 300.0f * gc);
      int threads = Math.round((cfg.autumnAir.gossamer / 100.0f) * 160.0f);
      int spores = cfg.autumnAir.whirls > 0.01f ? Math.round(90.0f * Math.min(1.0f, gc)) : 0;
      int movingLeavesAndAir = falling + rolling + threads + spores;
      int groundSide = gc > 0.0f && snowCover > 0.0f ? AUTUMN_GROUND_SIDE : 0;
      particleCount = movingLeavesAndAir + groundSide * groundSide * AUTUMN_GROUND_SITES_PER_COLUMN;
      boxSize = 44.0f;

      fallDetail[4] = sunX;
      fallDetail[5] = sunY;
      fallDetail[6] = sunZ;
      fallDetail[7] = 1.0f - nightFactor;

      fallDetail[8] = preset.skySun[0];
      fallDetail[9] = preset.skySun[1];
      fallDetail[10] = preset.skySun[2];
      fallDetail[11] = cfg.autumnAir.leafGlow / 100.0f;

      fallDetail[12] = (float) falling;
      fallDetail[13] = (float) rolling;
      fallDetail[14] = (float) threads;
      fallDetail[15] = cfg.autumnAir.leafShapes ? 1.0f : 0.0f;

      fallDetail[16] = cfg.autumnAir.whirls / 100.0f;
      fallDetail[17] = columns.originX() & 1;
      fallDetail[18] = columns.originZ() & 1;
      fallDetail[19] = movingLeavesAndAir;

      fallDetail[20] = columns.originX() & AUTUMN_GROUND_COORD_MASK;
      fallDetail[21] = columns.originZ() & AUTUMN_GROUND_COORD_MASK;
      fallDetail[22] = cfg.autumnGround.piles / 100.0f;
      fallDetail[23] = groundSide;

      fallDetail[24] = hazeColor[0];
      fallDetail[25] = hazeColor[1];
      fallDetail[26] = hazeColor[2];
      fallDetail[27] = haze * 0.00864f + (cfg.autumnSky.mist / 100.0f) * 0.012f;
    } else if (kind == AtmospherePresets.KIND_MYSTIC) {
      int quality = AtmosphereSimulator.qualityTier(cfg.quality);
      int leaves = Math.round(densityFrac * 1800.0f);
      int swirling =
          Math.round((cfg.mysticWind.skitter / 100.0f) * 260.0f * ground);
      int bats = Math.round((cfg.mysticLife.bats / 100.0f) * 12.0f) * 2;
      int event = cfg.mysticLife.batColony > 0.01f ? 56 : 0;
      int cobCell = quality == 0 ? 16 : (quality == 2 ? 32 : 24);
      int cobwebs = cfg.mysticLife.webs > 0.01f ? cobCell * cobCell : 0;
      particleCount = leaves + swirling + bats + event + cobwebs;
      boxSize = 52.0f;

      boolean moonOn = cfg.mysticSky.moon && world.isOverworld();
      fallDetail[0] = cfg.mysticWind.leafSize / 100.0f - 1.0f;
      fallDetail[1] = speed * (0.5f + cfg.mysticWind.gusts / 100.0f);
      fallDetail[2] =
          cobwebs > 0
              ? ((float) cobCell + (cfg.mysticLife.webs / 100.0f) * 0.999f)
              : 0.0f;
      fallDetail[3] = simulator.batSwarmPacked;

      fallDetail[4] = (float) leaves;
      fallDetail[5] = (float) (leaves + swirling);
      fallDetail[6] = (float) (leaves + swirling + bats);
      fallDetail[7] = (float) (leaves + swirling + bats + event);

      fallDetail[8] = sunX;
      fallDetail[9] = sunY;
      fallDetail[10] = sunZ;
      fallDetail[11] = moonOn ? (cfg.mysticWorld.moonlight / 100.0f) : 0.0f;

      fallDetail[12] = (float) floorModInt(columns.originX(), 4096);
      fallDetail[13] = (float) floorModInt(columns.originZ(), 4096);
      fallDetail[14] = haze * 0.012f + 0.01f;
      fallDetail[15] = (float) (timeSec % 3600.0);

      fallDetail[16] = groundY - 1.0f - (float) camY;
      fallDetail[17] = cfg.mysticFog.fogHeight;
      fallDetail[18] = columns.isCave() ? 0.0f : (cfg.mysticFog.groundFog / 100.0f);
      fallDetail[19] = columns.roofShelter();

      System.arraycopy(simulator.closestTwoLitLamps, 0, fallDetail, 20, 8);

      AtmospherePresets.unpackRgb(cfg.mysticLife.wispsColor, rgbScratchB);
      float mx =
          Math.max(
              0.05f, Math.max(rgbScratchB[0], Math.max(rgbScratchB[1], rgbScratchB[2])));
      fallDetail[28] = rgbScratchB[0] / mx;
      fallDetail[29] = rgbScratchB[1] / mx;
      fallDetail[30] = rgbScratchB[2] / mx;
      fallDetail[31] = nightFactor;
    }

    if (particleCount <= 0) {
      return;
    }

    float gridOriginX = (float) ((double) columns.originX() - camX);
    float gridOriginY = (float) (-camY);
    float gridOriginZ = (float) ((double) columns.originZ() - camZ);

    float boxOriginX = (float) floorModDouble(camX, (double) boxSize);
    float boxOriginY = (float) floorModDouble(camY, (double) boxSize);
    float boxOriginZ = (float) floorModDouble(camZ, (double) boxSize);

    ByteBuffer pb = plan.precipUbo;
    pb.clear();
    putMat4(pb, viewProj);
    putVec4(pb, gridOriginX, gridOriginY, gridOriginZ, boxSize);
    putVec4(pb, boxOriginX, boxOriginY, boxOriginZ, timeFloat);
    putVec4(pb, speed, pixelWorld, ground, (float) kind);
    putVec4(pb, (float) width, (float) height, vignette, (float) reversedDepth);
    putVec4(pb, tintR, tintG, tintB, 0.0f);
    for (int i = 0; i < 32; i++) {
      pb.putFloat(fallDetail[i]);
    }
    pb.flip();

    plan.drawPrecip = true;
    plan.precipVertexCount = particleCount * 6;
  }

  private void packBokehUbos(
      int width,
      int height,
      float camPosX,
      float camPosY,
      float camPosZ,
      int reversedDepth,
      float threshold,
      float focus,
      float maxRadius,
      float discGain,
      float streakGain,
      float mixDiscs,
      float tintR,
      float tintG,
      float tintB) {
    float qw = (float) plan.quarterWidth;
    float qh = (float) plan.quarterHeight;
    float srcTexelX = 1.0f / Math.max(1.0f, (float) width);
    float srcTexelY = 1.0f / Math.max(1.0f, (float) height);

    ByteBuffer ex = plan.bokehExtractUbo;
    ex.clear();
    putMat4(ex, invViewProj);
    putVec4(ex, camPosX, camPosY, camPosZ, (float) reversedDepth);
    putVec4(ex, qw, qh, srcTexelX, srcTexelY);
    putVec4(ex, threshold, focus, 0.0f, 0.0f);
    ex.flip();

    ByteBuffer dc = plan.bokehDiscUbo;
    dc.clear();
    putVec4(dc, qw, qh, maxRadius, discGain);
    dc.flip();

    ByteBuffer sw = plan.bokehStreakWideUbo;
    sw.clear();
    putVec4(sw, qw, qh, 2.5f, streakGain);
    putVec4(sw, tintR, tintG, tintB, 0.0f);
    sw.flip();

    ByteBuffer sf = plan.bokehStreakFineUbo;
    sf.clear();
    putVec4(sf, qw, qh, 1.0f, streakGain > 0.001f ? 0.85f : 0.0f);
    putVec4(sf, 1.0f, 1.0f, 1.0f, mixDiscs);
    sf.flip();
  }

  private static void projectDirectionToScreen(
      float dx, float dy, float dz, float[] vp, float[] out3) {
    float cx = vp[0] * dx + vp[4] * dy + vp[8] * dz;
    float cy = vp[1] * dx + vp[5] * dy + vp[9] * dz;
    float cw = vp[3] * dx + vp[7] * dy + vp[11] * dz;
    float denom = Math.max(Math.abs(cw), 0.001f);
    float rx = (cx / denom) * 0.5f;
    float ry = (cy / denom) * 0.5f;
    if (cw < 0.0f) {
      rx = -rx;
      ry = -ry;
    }
    float radial = (float) Math.hypot(rx, ry);
    if (radial > 3.5f) {
      float scale = 3.5f / radial;
      rx *= scale;
      ry *= scale;
    }
    out3[0] = 0.5f + rx;
    out3[1] = 0.5f + ry;
    out3[2] = AtmospherePresets.clamp01((cw + 0.12f) / 0.42f);
  }

  private static float[] copy3(float[] src, float[] dst) {
    dst[0] = src[0];
    dst[1] = src[1];
    dst[2] = src[2];
    return dst;
  }

  private static void ensureSubObjects(Settings.Atmosphere a) {
    if (a.snowCover == null) a.snowCover = new Settings.Atmosphere.SnowCover();
    if (a.snowfall == null) a.snowfall = new Settings.Atmosphere.Snowfall();
    if (a.sakura == null) a.sakura = new Settings.Atmosphere.Sakura();
    if (a.sakuraWater == null) a.sakuraWater = new Settings.Atmosphere.SakuraWater();
    if (a.sakuraSky == null) a.sakuraSky = new Settings.Atmosphere.SakuraSky();
    if (a.autumnTrees == null) a.autumnTrees = new Settings.Atmosphere.AutumnTrees();
    if (a.autumnGround == null) a.autumnGround = new Settings.Atmosphere.AutumnGround();
    if (a.autumnAir == null) a.autumnAir = new Settings.Atmosphere.AutumnAir();
    if (a.autumnSky == null) a.autumnSky = new Settings.Atmosphere.AutumnSky();
    if (a.mysticSky == null) a.mysticSky = new Settings.Atmosphere.MysticSky();
    if (a.mysticFog == null) a.mysticFog = new Settings.Atmosphere.MysticFog();
    if (a.mysticWorld == null) a.mysticWorld = new Settings.Atmosphere.MysticWorld();
    if (a.mysticLens == null) a.mysticLens = new Settings.Atmosphere.MysticLens();
    if (a.mysticLife == null) a.mysticLife = new Settings.Atmosphere.MysticLife();
    if (a.mysticWind == null) a.mysticWind = new Settings.Atmosphere.MysticWind();
  }

  private static void lerp3(float[] dst, float tr, float tg, float tb, float k) {
    dst[0] += (tr - dst[0]) * k;
    dst[1] += (tg - dst[1]) * k;
    dst[2] += (tb - dst[2]) * k;
  }

  private static void putMat4(ByteBuffer buf, float[] m) {
    for (int i = 0; i < 16; i++) {
      buf.putFloat(m[i]);
    }
  }

  private static void putVec4(ByteBuffer buf, float x, float y, float z, float w) {
    buf.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
  }

  private static double floorModDouble(double x, double m) {
    double r = x - Math.floor(x / m) * m;
    return r < 0.0 ? r + m : r;
  }

  private static int floorModInt(int x, int m) {
    int r = x % m;
    return r < 0 ? r + m : r;
  }

  public static void multiplyMat4(float[] a, float[] b, float[] out) {
    for (int col = 0; col < 4; col++) {
      int bCol = col * 4;
      float b0 = b[bCol], b1 = b[bCol + 1], b2 = b[bCol + 2], b3 = b[bCol + 3];
      out[bCol] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3;
      out[bCol + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3;
      out[bCol + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3;
      out[bCol + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
    }
  }

  public static boolean invertMat4(float[] m, float[] invOut) {
    float s0 = m[0] * m[5] - m[4] * m[1];
    float s1 = m[0] * m[6] - m[4] * m[2];
    float s2 = m[0] * m[7] - m[4] * m[3];
    float s3 = m[1] * m[6] - m[5] * m[2];
    float s4 = m[1] * m[7] - m[5] * m[3];
    float s5 = m[2] * m[7] - m[6] * m[3];

    float c5 = m[10] * m[15] - m[14] * m[11];
    float c4 = m[9] * m[15] - m[13] * m[11];
    float c3 = m[9] * m[14] - m[13] * m[10];
    float c2 = m[8] * m[15] - m[12] * m[11];
    float c1 = m[8] * m[14] - m[12] * m[10];
    float c0 = m[8] * m[13] - m[12] * m[9];

    float det = s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0;
    if (Math.abs(det) < 1.0e-12f || Float.isNaN(det)) {
      return false;
    }
    float invDet = 1.0f / det;

    invOut[0] = (m[5] * c5 - m[6] * c4 + m[7] * c3) * invDet;
    invOut[1] = (-m[1] * c5 + m[2] * c4 - m[3] * c3) * invDet;
    invOut[2] = (m[13] * s5 - m[14] * s4 + m[15] * s3) * invDet;
    invOut[3] = (-m[9] * s5 + m[10] * s4 - m[11] * s3) * invDet;

    invOut[4] = (-m[4] * c5 + m[6] * c2 - m[7] * c1) * invDet;
    invOut[5] = (m[0] * c5 - m[2] * c2 + m[3] * c1) * invDet;
    invOut[6] = (-m[12] * s5 + m[14] * s2 - m[15] * s1) * invDet;
    invOut[7] = (m[8] * s5 - m[10] * s2 + m[11] * s1) * invDet;

    invOut[8] = (m[4] * c4 - m[5] * c2 + m[7] * c0) * invDet;
    invOut[9] = (-m[0] * c4 + m[1] * c2 - m[3] * c0) * invDet;
    invOut[10] = (m[12] * s4 - m[13] * s2 + m[15] * s0) * invDet;
    invOut[11] = (-m[8] * s4 + m[9] * s2 - m[11] * s0) * invDet;

    invOut[12] = (-m[4] * c3 + m[5] * c1 - m[6] * c0) * invDet;
    invOut[13] = (m[0] * c3 - m[1] * c1 + m[2] * c0) * invDet;
    invOut[14] = (-m[12] * s3 + m[13] * s1 - m[14] * s0) * invDet;
    invOut[15] = (m[8] * s3 - m[9] * s1 + m[10] * s0) * invDet;
    return true;
  }
}
