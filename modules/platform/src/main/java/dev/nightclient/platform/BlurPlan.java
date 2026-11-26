package dev.nightclient.platform;

public final class BlurPlan {
  private BlurPlan() {}

  public static int quality(int quality) {
    return Math.max(1, Math.min(6, quality));
  }

  public static boolean fullResolution(int quality) {
    return quality(quality) >= 4;
  }

  public static int samples(int quality) {
    int q = quality(quality);
    return q == 6 ? 9 : q == 5 ? 4 : 1;
  }

  public static int levels(float radius, int quality) {
    if (!Float.isFinite(radius) || !(radius > 0)) return 0;
    int maximum = Math.min(3, quality(quality)) + 1, levels = 1;
    while (levels < maximum && (1 << levels) <= radius / 2f) levels++;
    return levels + (fullResolution(quality) ? 1 : 0);
  }

  public static int firstDownLevel(int quality) {
    return fullResolution(quality) ? 1 : 0;
  }

  public static int passes(float radius, int quality) {
    int count = levels(radius, quality);
    return count == 0 ? 0 : count * 2 - 1 - firstDownLevel(quality);
  }

  public static float offset(float radius, int levels) {
    return Math.max(.25f, radius / (3f * (1 << levels) - 5f));
  }

  public static float offset(float radius, int levels, int quality) {
    if (!fullResolution(quality)) return offset(radius, levels);

    return Math.max(.25f, radius / (3f * (1 << (levels - 1)) - 3f));
  }

  public static int size(int full, int level) {
    int divisor = 1 << (level + 1);
    return Math.max(1, (full + divisor - 1) / divisor);
  }

  public static int size(int full, int level, int quality) {
    return fullResolution(quality) ? (level == 0 ? Math.max(1, full) : size(full, level - 1))
                                   : size(full, level);
  }

  public static int fastLevels(float radius, int quality) {
    if (!Float.isFinite(radius) || !(radius > 0)) return 0;
    int maximum = Math.min(2, quality(quality)) + 1, levels = 1;
    while (levels < maximum && (1 << (levels + 2)) <= radius) levels++;
    return levels;
  }

  public static int fastPasses(float radius, int quality) {
    int count = fastLevels(radius, quality);
    return count == 0 ? 0 : count * 2 - 1;
  }

  public static float fastOffset(float radius, int levels) {
    if (levels <= 1) return Math.max(1f, radius * 0.25f);
    return Math.max(.25f, radius / (3f * (1 << (levels + 1)) - 11f));
  }

  public static int fastSize(int full, int level) {
    return size(full, level + 1);
  }
}
