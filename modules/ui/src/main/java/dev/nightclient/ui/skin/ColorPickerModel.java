package dev.nightclient.ui.skin;

final class ColorPickerModel {
  float hue, saturation, value, alpha;
  private int lastArgb;
  private boolean initialized;

  void sync(int argb) {
    if (initialized && argb == lastArgb) return;
    float r = ((argb >>> 16) & 255) / 255f, g = ((argb >>> 8) & 255) / 255f,
          b = (argb & 255) / 255f;
    float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
    if (delta > 0) {
      float h = max == r ? (g - b) / delta : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4;
      hue = h / 6;
      if (hue < 0) hue += 1;
    }
    if (max > 0) saturation = delta / max;
    value = max;
    alpha = (argb >>> 24) / 255f;
    lastArgb = argb;
    initialized = true;
  }

  int argb() {
    lastArgb = rgb(hue, saturation, value, alpha);
    initialized = true;
    return lastArgb;
  }

  static int rgb(float hue, float saturation, float value, float alpha) {
    float h = (hue - (float) Math.floor(hue)) * 6, s = clamp(saturation), v = clamp(value),
          c = v * s, x = c * (1 - Math.abs(h % 2 - 1)), m = v - c;
    float r = 0, g = 0, b = 0;
    switch ((int) h) {
      case 0:
        r = c;
        g = x;
        break;
      case 1:
        r = x;
        g = c;
        break;
      case 2:
        g = c;
        b = x;
        break;
      case 3:
        g = x;
        b = c;
        break;
      case 4:
        r = x;
        b = c;
        break;
      default:
        r = c;
        b = x;
    }
    return Math.round(clamp(alpha) * 255) << 24 | Math.round((r + m) * 255) << 16
        | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
  }

  private static float clamp(float v) {
    return Math.max(0, Math.min(1, v));
  }
}
