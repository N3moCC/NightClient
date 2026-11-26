package dev.nightclient.ui.skin;

final class MotionTrack {
  private float start, target;
  private double began;
  private float duration;
  private String easing = "cubic-out";
  private boolean initialized;

  MotionTrack() {}

  MotionTrack(float initial) {
    start = target = initial;
    initialized = true;
  }

  float to(float next, double time, float seconds, String curve, boolean reduced) {
    if (!Float.isFinite(next)) next = 0;
    if (!Double.isFinite(time)) time = 0;
    if (!initialized) {
      start = target = next;
      began = time;
      initialized = true;
    }
    if (reduced || seconds <= 0) {
      start = target = next;
      began = time;
      duration = 0;
      return next;
    }
    if (next != target) {
      start = at(time);
      target = next;
      began = time;
      duration = seconds;
      easing = curve;
    }
    return at(time);
  }

  float peek(float next, double time, float seconds, boolean reduced) {
    if (!Float.isFinite(next)) next = 0;
    if (!Double.isFinite(time)) time = 0;
    if (!initialized || reduced || seconds <= 0) return next;
    return at(time);
  }

  float at(double time) {
    if (duration <= 0 || start == target) return target;
    float progress = (float) ((time - began) / duration);
    if (progress >= 1) return target;
    if (progress <= 0) return start;
    return start + (target - start) * ease(progress, easing);
  }

  boolean inFlight(double time) {
    return initialized && start != target && duration > 0 && time < began + duration;
  }

  static float ease(float p, String curve) {
    p = Math.max(0, Math.min(1, p));
    if (p == 0 || p == 1) return p;
    if (curve != null && curve.startsWith("cubic-bezier("))
      return dev.nightclient.config.Easing.cubicBezier(p, curve);
    if ("linear".equals(curve)) return p;
    if ("smooth".equals(curve)) return p * p * (3 - 2 * p);
    if ("cubic-in".equals(curve)) return p * p * p;
    if ("cubic-in-out".equals(curve))
      return p < .5f ? 4 * p * p * p : 1 - (float) Math.pow(-2 * p + 2, 3) / 2;
    if ("quad-out".equals(curve)) return 1 - (1 - p) * (1 - p);
    if ("quart-out".equals(curve)) return 1 - (float) Math.pow(1 - p, 4);
    if ("quint-out".equals(curve)) return 1 - (float) Math.pow(1 - p, 5);
    if ("sine-out".equals(curve)) return (float) Math.sin(p * Math.PI / 2);
    if ("expo-out".equals(curve)) return p == 1 ? 1 : 1 - (float) Math.pow(2, -10 * p);
    if ("back-out".equals(curve)) {
      float q = p - 1;
      return 1 + 2.70158f * q * q * q + 1.70158f * q * q;
    }
    return 1 - (float) Math.pow(1 - p, 3);
  }
}
