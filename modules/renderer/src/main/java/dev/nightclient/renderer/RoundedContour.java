package dev.nightclient.renderer;

public final class RoundedContour {
  private static final float[][] BASES = new float[13][];

  static {
    for (int steps = 4; steps <= 12; steps++) {
      float[] base = new float[(steps + 1) * 8];
      int at = 0;
      for (int q = 0; q < 4; q++)
        for (int i = 0; i <= steps; i++) {
          double angle = (q - 1) * Math.PI / 2 + i * Math.PI / (2 * steps);
          base[at++] = (float) Math.cos(angle);
          base[at++] = (float) Math.sin(angle);
        }
      BASES[steps] = base;
    }
  }

  private RoundedContour() {}

  public static final int MAX_FLOATS = (12 + 1) * 8;

  public static float[] create(
      float x, float y, float w, float h, float tl, float tr, float bl, float br, int steps) {
    steps = Math.max(4, Math.min(12, steps));
    float[] points = new float[BASES[steps].length];
    fill(points, x, y, w, h, tl, tr, bl, br, steps);
    return points;
  }

  public static int fill(float[] points, float x, float y, float w, float h, float tl, float tr,
      float bl, float br, int steps) {
    steps = Math.max(4, Math.min(12, steps));
    float[] base = BASES[steps];
    int at = 0;
    float halfMin = Math.max(0, Math.min(w, h) / 2);
    for (int q = 0; q < 4; q++) {
      float r = Math.max(0,
          Math.min(q == 0  ? tr
                  : q == 1 ? br
                  : q == 2 ? bl
                           : tl,
              halfMin));
      float cx = q < 2 ? x + w - r : x + r, cy = q == 0 || q == 3 ? y + r : y + h - r;
      for (int i = 0; i <= steps; i++) {
        points[at] = cx + base[at] * r;
        at++;
        points[at] = cy + base[at] * r;
        at++;
      }
    }
    return base.length;
  }
}
