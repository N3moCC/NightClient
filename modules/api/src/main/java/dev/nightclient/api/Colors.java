package dev.nightclient.api;

import java.util.Locale;

public final class Colors {
  private Colors() {}

  public static int alpha(int color, float multiplier) {
    int a = Math.round(((color >>> 24) & 255) * Math.max(0, Math.min(1, multiplier)));
    return (color & 0x00ffffff) | (a << 24);
  }

  public static int mix(int a, int b, float t) {
    t = Math.max(0, Math.min(1, t));
    int result = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
      int x = (a >>> shift) & 255, y = (b >>> shift) & 255;
      result |= Math.round(x + (y - x) * t) << shift;
    }
    return result;
  }

  public static float luminance(int color) {
    float r = ((color >>> 16) & 255) / 255f;
    float g = ((color >>> 8) & 255) / 255f;
    float b = (color & 255) / 255f;
    return 0.2126f * r + 0.7152f * g + 0.0722f * b;
  }

  public static int hsv(float hue, float saturation, float value, float alpha) {
    hue = hue - (float) Math.floor(hue);
    saturation = Math.max(0, Math.min(1, saturation));
    value = Math.max(0, Math.min(1, value));
    float h = hue * 6, c = value * saturation, x = c * (1 - Math.abs(h % 2 - 1)), m = value - c;
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
    return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24)
        | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8)
        | Math.round((b + m) * 255);
  }

  public static float[] toHsv(int color) {
    float r = ((color >>> 16) & 255) / 255f, g = ((color >>> 8) & 255) / 255f,
          b = (color & 255) / 255f;
    float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min,
          hue = 0;
    if (delta > 0) {
      if (max == r)
        hue = ((g - b) / delta) % 6;
      else if (max == g)
        hue = (b - r) / delta + 2;
      else
        hue = (r - g) / delta + 4;
      hue /= 6;
      if (hue < 0) hue += 1;
    }
    return new float[] {hue, max == 0 ? 0 : delta / max, max, ((color >>> 24) & 255) / 255f};
  }

  public static String hex(int color) {
    return String.format(Locale.ROOT, "#%06X%02X", color & 0xffffff, color >>> 24);
  }

  public static int parseHex(String value) {
    String s = value.trim();
    if (s.startsWith("#")) s = s.substring(1);
    if (!s.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
      throw new IllegalArgumentException("Use #RRGGBB or #RRGGBBAA");
    long n = Long.parseLong(s, 16);
    return s.length() == 6 ? (int) n | 0xff000000 : (int) ((n >>> 8) | ((n & 255) << 24));
  }
}
