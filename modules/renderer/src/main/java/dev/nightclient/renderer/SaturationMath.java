package dev.nightclient.renderer;

public final class SaturationMath {
  private SaturationMath() {}

  public static float[] apply(float r, float g, float b, float amount) {
    float luma = r * 0.2126f + g * 0.7152f + b * 0.0722f;
    return new float[] {clamp(luma + (r - luma) * amount), clamp(luma + (g - luma) * amount),
        clamp(luma + (b - luma) * amount)};
  }

  public static int apply(int argb, float amount) {
    float[] rgb = apply(
        ((argb >>> 16) & 255) / 255f, ((argb >>> 8) & 255) / 255f, (argb & 255) / 255f, amount);
    return (argb & 0xff000000) | (Math.round(rgb[0] * 255) << 16) | (Math.round(rgb[1] * 255) << 8)
        | Math.round(rgb[2] * 255);
  }

  private static float clamp(float v) {
    return Math.max(0, Math.min(1, v));
  }
}
