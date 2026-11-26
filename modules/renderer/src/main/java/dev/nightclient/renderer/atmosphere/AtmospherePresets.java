package dev.nightclient.renderer.atmosphere;

public final class AtmospherePresets {
  private AtmospherePresets() {}

  public static final int MODE_DAWN = -1;
  public static final int KIND_DUST = 0;
  public static final int KIND_SNOW = 1;
  public static final int KIND_SAKURA = 3;
  public static final int KIND_AUTUMN = 4;
  public static final int KIND_FIREFLIES = 5;
  public static final int KIND_MYSTIC = 6;

  public static final class MoodPreset {
    public final int kind;
    public final float[] skyTop;
    public final float[] skyHorizon;
    public final float[] skySun;
    public final float[] hazeColor;
    public final float[] particleColor;
    public final float[] shadowTint;
    public final float[] lightTint;
    public final float[] gradeParams;
    public final float rays;
    public final float shimmer;
    public final float skyMix;
    public final float chroma;
    public final float lights;
    public final float[] streakTint;
    public final float sunElevationRad;
    public final float hazeScale;
    public final boolean useSunAsLightDir;
    public final float[] lightGain;
    public final float relight;
    public final float occlusion;
    public final float snowCover;
    public final float bokehThreshold;

    MoodPreset(
        int kind,
        float[] skyTop,
        float[] skyHorizon,
        float[] skySun,
        float[] hazeColor,
        float[] particleColor,
        float[] shadowTint,
        float[] lightTint,
        float[] gradeParams,
        float rays,
        float shimmer,
        float skyMix,
        float chroma,
        float lights,
        float[] streakTint,
        float sunElevationRad,
        float hazeScale,
        boolean useSunAsLightDir,
        float[] lightGain,
        float relight,
        float occlusion,
        float snowCover,
        float bokehThreshold) {
      this.kind = kind;
      this.skyTop = skyTop;
      this.skyHorizon = skyHorizon;
      this.skySun = skySun;
      this.hazeColor = hazeColor;
      this.particleColor = particleColor;
      this.shadowTint = shadowTint;
      this.lightTint = lightTint;
      this.gradeParams = gradeParams;
      this.rays = rays;
      this.shimmer = shimmer;
      this.skyMix = skyMix;
      this.chroma = chroma;
      this.lights = lights;
      this.streakTint = streakTint;
      this.sunElevationRad = sunElevationRad;
      this.hazeScale = hazeScale;
      this.useSunAsLightDir = useSunAsLightDir;
      this.lightGain = lightGain;
      this.relight = relight;
      this.occlusion = occlusion;
      this.snowCover = snowCover;
      this.bokehThreshold = bokehThreshold;
    }
  }

  public static final MoodPreset DUST =
      new MoodPreset(
          KIND_DUST,
          new float[] {0.50f, 0.64f, 0.88f},
          new float[] {0.84f, 0.80f, 0.70f},
          new float[] {1.00f, 0.93f, 0.75f},
          new float[] {0.90f, 0.84f, 0.72f},
          new float[] {1.00f, 0.95f, 0.86f},
          new float[] {0.30f, 0.26f, 0.24f},
          new float[] {1.00f, 0.96f, 0.88f},
          new float[] {1.02f, 1.05f, 0.95f, 0.00f},
          0.90f,
          0.30f,
          0.80f,
          0.15f,
          0.00f,
          new float[] {1.00f, 1.00f, 1.00f},
          0.42f,
          0.35f,
          true,
          new float[] {0.30f, 0.23f, 0.13f},
          0.85f,
          0.70f,
          0.00f,
          0.62f);

  public static final MoodPreset SNOW =
      new MoodPreset(
          KIND_SNOW,
          new float[] {0.60f, 0.67f, 0.78f},
          new float[] {0.86f, 0.88f, 0.93f},
          new float[] {0.00f, 0.00f, 0.00f},
          new float[] {0.84f, 0.88f, 0.94f},
          new float[] {0.92f, 0.95f, 1.00f},
          new float[] {0.22f, 0.28f, 0.40f},
          new float[] {0.95f, 0.97f, 1.00f},
          new float[] {1.00f, 0.95f, 0.75f, 0.00f},
          0.00f,
          0.00f,
          0.90f,
          0.00f,
          0.00f,
          new float[] {1.00f, 1.00f, 1.00f},
          0.00f,
          0.55f,
          false,
          new float[] {0.16f, 0.18f, 0.24f},
          1.00f,
          0.65f,
          1.00f,
          0.62f);

  public static final MoodPreset SAKURA =
      new MoodPreset(
          KIND_SAKURA,
          new float[] {0.13f, 0.11f, 0.28f},
          new float[] {0.80f, 0.56f, 0.68f},
          new float[] {0.00f, 0.00f, 0.00f},
          new float[] {0.70f, 0.60f, 0.72f},
          new float[] {1.00f, 0.72f, 0.84f},
          new float[] {0.30f, 0.27f, 0.34f},
          new float[] {1.00f, 0.93f, 0.94f},
          new float[] {0.95f, 1.03f, 1.04f, 0.015f},
          0.00f,
          0.00f,
          0.90f,
          0.00f,
          0.45f,
          new float[] {1.00f, 0.62f, 0.80f},
          0.00f,
          0.28f,
          false,
          new float[] {0.14f, 0.15f, 0.20f},
          0.55f,
          0.55f,
          0.70f,
          0.72f);

  public static final MoodPreset AUTUMN =
      new MoodPreset(
          KIND_AUTUMN,
          new float[] {0.45f, 0.58f, 0.80f},
          new float[] {0.95f, 0.78f, 0.55f},
          new float[] {1.00f, 0.80f, 0.50f},
          new float[] {0.92f, 0.78f, 0.58f},
          new float[] {1.00f, 0.62f, 0.20f},
          new float[] {0.32f, 0.20f, 0.16f},
          new float[] {1.00f, 0.88f, 0.70f},
          new float[] {1.02f, 1.06f, 1.10f, 0.00f},
          0.60f,
          0.00f,
          0.82f,
          0.00f,
          0.00f,
          new float[] {1.00f, 1.00f, 1.00f},
          0.30f,
          0.40f,
          true,
          new float[] {0.32f, 0.22f, 0.10f},
          0.80f,
          0.65f,
          0.70f,
          0.62f);

  public static final MoodPreset FIREFLIES =
      new MoodPreset(
          KIND_FIREFLIES,
          new float[] {0.03f, 0.05f, 0.14f},
          new float[] {0.10f, 0.18f, 0.30f},
          new float[] {0.00f, 0.00f, 0.00f},
          new float[] {0.20f, 0.28f, 0.40f},
          new float[] {0.80f, 1.00f, 0.40f},
          new float[] {0.08f, 0.14f, 0.30f},
          new float[] {0.80f, 0.90f, 1.00f},
          new float[] {0.82f, 1.08f, 0.95f, 0.03f},
          0.00f,
          0.00f,
          0.90f,
          0.00f,
          0.35f,
          new float[] {1.00f, 0.80f, 0.50f},
          0.00f,
          0.45f,
          false,
          new float[] {0.10f, 0.14f, 0.22f},
          0.60f,
          0.60f,
          0.00f,
          0.72f);

  public static final MoodPreset MYSTIC =
      new MoodPreset(
          KIND_MYSTIC,
          new float[] {0.019f, 0.026f, 0.060f},
          new float[] {0.095f, 0.105f, 0.150f},
          new float[] {0.00f, 0.00f, 0.00f},
          new float[] {0.16f, 0.19f, 0.25f},
          new float[] {0.55f, 0.90f, 1.00f},
          new float[] {0.07f, 0.12f, 0.22f},
          new float[] {1.00f, 0.90f, 0.76f},
          new float[] {1.00f, 0.97f, 0.85f, 0.03f},
          0.00f,
          0.00f,
          1.00f,
          0.00f,
          0.50f,
          new float[] {1.00f, 0.58f, 0.24f},
          0.314f,
          0.40f,
          false,
          new float[] {0.30f, 0.38f, 0.56f},
          0.00f,
          0.00f,
          0.60f,
          0.50f);

  public static MoodPreset presetForMode(String mode) {
    if ("dust".equals(mode)) return DUST;
    if ("snow".equals(mode)) return SNOW;
    if ("sakura".equals(mode) || "japan".equals(mode)) return SAKURA;
    if ("autumn".equals(mode)) return AUTUMN;
    if ("fireflies".equals(mode)) return FIREFLIES;
    if ("mystic".equals(mode) || "halloween".equals(mode)) return MYSTIC;
    return null;
  }

  public static int kindForMode(String mode) {
    MoodPreset p = presetForMode(mode);
    return p != null ? p.kind : MODE_DAWN;
  }

  public static float dawnTimeRadians(long dayTime) {
    long ticks = ((dayTime % 24000L) + 24000L) % 24000L;
    double d = (double) ticks / 24000.0 - 0.25;
    d = d - Math.floor(d);
    double curved = (d * 2.0 + (0.5 - 0.5 * Math.cos(d * Math.PI))) / 3.0;
    return (float) (curved * (Math.PI * 2.0));
  }

  public static void unpackRgb(int argb, float[] out3) {
    out3[0] = ((argb >>> 16) & 255) / 255.0f;
    out3[1] = ((argb >>> 8) & 255) / 255.0f;
    out3[2] = (argb & 255) / 255.0f;
  }

  public static float luminance(float r, float g, float b) {
    return 0.2126f * r + 0.7152f * g + 0.0722f * b;
  }

  public static float clamp01(float v) {
    if (Float.isNaN(v) || v <= 0.0f) return 0.0f;
    return v >= 1.0f ? 1.0f : v;
  }

  public static float clamp(float v, float lo, float hi) {
    if (Float.isNaN(v)) return lo;
    if (v < lo) return lo;
    return v > hi ? hi : v;
  }

  private static float srgbToLinear(float c) {
    return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055) / 1.055, 2.4);
  }

  private static float linearToSrgb(float c) {
    c = clamp01(c);
    return c <= 0.0031308f ? c * 12.92f : (float) (1.055 * Math.pow(c, 1.0 / 2.4) - 0.055);
  }

  private static float cbrtSigned(float x) {
    return (float) Math.cbrt(x);
  }

  public static void oklabMix(
      float ar, float ag, float ab, float br, float bg, float bb, float t, float[] out3) {
    float k = clamp01(t);
    float alr = srgbToLinear(ar), alg = srgbToLinear(ag), alb = srgbToLinear(ab);
    float blr = srgbToLinear(br), blg = srgbToLinear(bg), blb = srgbToLinear(bb);

    float al = cbrtSigned(0.41222147f * alr + 0.53633254f * alg + 0.05144599f * alb);
    float am = cbrtSigned(0.21190350f * alr + 0.68069955f * alg + 0.10739696f * alb);
    float as = cbrtSigned(0.08830246f * alr + 0.28171884f * alg + 0.62997870f * alb);

    float bl = cbrtSigned(0.41222147f * blr + 0.53633254f * blg + 0.05144599f * blb);
    float bm = cbrtSigned(0.21190350f * blr + 0.68069955f * blg + 0.10739696f * blb);
    float bs = cbrtSigned(0.08830246f * blr + 0.28171884f * blg + 0.62997870f * blb);

    float l_ = al + (bl - al) * k;
    float m_ = am + (bm - am) * k;
    float s_ = as + (bs - as) * k;

    float l = l_ * l_ * l_;
    float m = m_ * m_ * m_;
    float s = s_ * s_ * s_;

    out3[0] = linearToSrgb(4.0767417f * l - 3.3077116f * m + 0.23096993f * s);
    out3[1] = linearToSrgb(-1.2684380f * l + 2.6097574f * m - 0.34131940f * s);
    out3[2] = linearToSrgb(-0.0041960863f * l - 0.70341861f * m + 1.7076147f * s);
  }
}
