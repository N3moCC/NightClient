package dev.nightclient.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class Easing {
  private static final Pattern NUMBER =
      Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
  private static final Map<String, float[]> CURVES =
      new LinkedHashMap<String, float[]>(32, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> entry) {
          return size() > 128;
        }
      };

  private Easing() {}

  private static synchronized float[] curve(String name) {
    if (name == null || !name.startsWith("cubic-bezier(") || !name.endsWith(")")) return null;
    if (CURVES.containsKey(name)) return CURVES.get(name);
    float[] points = null;
    String[] parts = name.substring(13, name.length() - 1).split(",", -1);
    if (parts.length == 4) {
      points = new float[4];
      for (int i = 0; i < 4; i++) {
        String value = parts[i].trim();
        if (!NUMBER.matcher(value).matches()) {
          points = null;
          break;
        }
        try {
          points[i] = Float.parseFloat(value);
        } catch (NumberFormatException invalid) {
          points = null;
          break;
        }
        if (!Float.isFinite(points[i]) || Math.abs(points[i]) > 10) {
          points = null;
          break;
        }
      }
      if (points != null && (points[0] < 0 || points[0] > 1 || points[2] < 0 || points[2] > 1))
        points = null;
    }
    CURVES.put(name, points);
    return points;
  }

  public static boolean supported(String name) {
    return name != null
        && (name.matches("linear|cubic-in|cubic-out|cubic-in-out|quad-out|quart-out|quint-out|sine-"
                         + "out|expo-out|back-out|smooth")
            || curve(name) != null);
  }

  public static float cubicBezier(float progress, String name) {
    if (!Float.isFinite(progress)) return 0;
    progress = Math.max(0, Math.min(1, progress));
    float[] p = curve(name);
    if (p == null || progress == 0 || progress == 1) return progress;
    float low = 0, high = 1, t = progress;
    for (int i = 0; i < 18; i++) {
      float x = coordinate(t, p[0], p[2]);
      if (x < progress)
        low = t;
      else
        high = t;
      t = (low + high) * .5f;
    }
    return coordinate(t, p[1], p[3]);
  }

  private static float coordinate(float t, float a, float b) {
    float u = 1 - t;
    return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
  }
}
