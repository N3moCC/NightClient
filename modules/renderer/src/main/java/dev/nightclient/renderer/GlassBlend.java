package dev.nightclient.renderer;

public final class GlassBlend {
  private GlassBlend() {}

  public static float alpha(float tintAlpha, float fraction) {
    float a = clamp(tintAlpha), m = clamp(fraction);
    return a * (1 - m + m * a);
  }

  public static float tintOpacity(float tintAlpha, float fraction) {
    return alpha(tintAlpha, fraction) * clamp(fraction);
  }

  public static float sampleOpacity(float tintAlpha, float fraction) {
    float m = clamp(fraction), a = alpha(tintAlpha, m), t = a * m;
    return t >= 1 ? 0 : clamp(a * (1 - m) / (1 - t));
  }

  private static float clamp(float value) {
    return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
  }
}
