package dev.nightclient.renderer;

public final class RoundedQuadMesh {
  public static final int MAX_FLOATS = 9 * 6 * 8;

  public static final float HEADER_STRIDE = 16;
  public static final int STROKE_STEPS = 2048;

  private RoundedQuadMesh() {}

  public static int append(float[] target, int offset, float x, float y, float w, float h,
      float radius, float stroke, float density, int tl, int tr, int bl, int br) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || !Float.isFinite(h)
        || w <= 0f || h <= 0f) {
      return offset;
    }
    float safeRadius = Float.isFinite(radius) ? radius : 0f;
    float safeStroke = Float.isFinite(stroke) && stroke > 0f ? stroke : 0f;
    float safeDensity = Float.isFinite(density) ? Math.max(1f, density) : 1f;
    float halfMin = Math.min(w, h) * 0.5f;
    float r = Math.min(halfMin, Math.max(Math.min(.25f, halfMin), safeRadius));
    float aa = .75f / safeDensity;
    float edge = 1f + aa / r;
    float header = 0f;
    if (safeStroke > 0f) {
      long steps = Math.min(262144L, Math.max(0L, Math.round((safeStroke / r) * STROKE_STEPS)));
      header = (1L + steps) * HEADER_STRIDE;
    }
    if (tl == tr && tl == bl && tl == br)
      return appendSolid(target, offset, x, y, w, h, r, aa, edge, header, tl);
    for (int row = 0; row < 3; row++) {
      float ay = position(row, y, h, r, aa), by = position(row + 1, y, h, r, aa);
      if (by <= ay) continue;
      float av = coordinate(row, edge), bv = coordinate(row + 1, edge);
      for (int column = 0; column < 3; column++) {
        float ax = position(column, x, w, r, aa), bx = position(column + 1, x, w, r, aa);
        if (bx <= ax) continue;
        float au = header + coordinate(column, edge), bu = header + coordinate(column + 1, edge);
        offset = vertex(target, offset, ax, ay, au, av, x, y, w, h, tl, tr, bl, br);
        offset = vertex(target, offset, bx, ay, bu, av, x, y, w, h, tl, tr, bl, br);
        offset = vertex(target, offset, bx, by, bu, bv, x, y, w, h, tl, tr, bl, br);
        offset = vertex(target, offset, ax, ay, au, av, x, y, w, h, tl, tr, bl, br);
        offset = vertex(target, offset, bx, by, bu, bv, x, y, w, h, tl, tr, bl, br);
        offset = vertex(target, offset, ax, by, au, bv, x, y, w, h, tl, tr, bl, br);
      }
    }
    return offset;
  }

  private static int appendSolid(float[] out, int at, float x, float y, float w, float h, float r,
      float aa, float edge, float header, int color) {
    float red = ((color >>> 16) & 255) / 255f, green = ((color >>> 8) & 255) / 255f,
          blue = (color & 255) / 255f, alpha = (color >>> 24) / 255f;
    for (int row = 0; row < 3; row++) {
      float ay = position(row, y, h, r, aa), by = position(row + 1, y, h, r, aa);
      if (by <= ay) continue;
      float av = coordinate(row, edge), bv = coordinate(row + 1, edge);
      for (int column = 0; column < 3; column++) {
        float ax = position(column, x, w, r, aa), bx = position(column + 1, x, w, r, aa);
        if (bx <= ax) continue;
        float au = header + coordinate(column, edge), bu = header + coordinate(column + 1, edge);
        at = solidVertex(out, at, ax, ay, au, av, red, green, blue, alpha);
        at = solidVertex(out, at, bx, ay, bu, av, red, green, blue, alpha);
        at = solidVertex(out, at, bx, by, bu, bv, red, green, blue, alpha);
        at = solidVertex(out, at, ax, ay, au, av, red, green, blue, alpha);
        at = solidVertex(out, at, bx, by, bu, bv, red, green, blue, alpha);
        at = solidVertex(out, at, ax, by, au, bv, red, green, blue, alpha);
      }
    }
    return at;
  }

  private static int solidVertex(float[] out, int at, float x, float y, float u, float v, float red,
      float green, float blue, float alpha) {
    out[at++] = x;
    out[at++] = y;
    out[at++] = u;
    out[at++] = v;
    out[at++] = red;
    out[at++] = green;
    out[at++] = blue;
    out[at++] = alpha;
    return at;
  }

  private static float position(int index, float start, float size, float radius, float aa) {
    return index == 0 ? start - aa
        : index == 1  ? start + radius
        : index == 2  ? start + size - radius
                      : start + size + aa;
  }

  private static float coordinate(int index, float edge) {
    return index == 0 ? -edge : index == 3 ? edge : 0;
  }

  private static int vertex(float[] out, int at, float px, float py, float u, float v, float x,
      float y, float w, float h, int tl, int tr, int bl, int br) {
    out[at++] = px;
    out[at++] = py;
    out[at++] = u;
    out[at++] = v;
    if (tl == tr && tl == bl && tl == br) {
      out[at++] = ((tl >>> 16) & 255) / 255f;
      out[at++] = ((tl >>> 8) & 255) / 255f;
      out[at++] = (tl & 255) / 255f;
      out[at++] = (tl >>> 24) / 255f;
    } else {
      float xx = Math.max(0, Math.min(1, (px - x) / w)),
            yy = Math.max(0, Math.min(1, (py - y) / h));
      for (int shift = 16; shift >= 0; shift -= 8) {
        float top = ((tl >>> shift) & 255) * (1 - xx) + ((tr >>> shift) & 255) * xx,
              bottom = ((bl >>> shift) & 255) * (1 - xx) + ((br >>> shift) & 255) * xx;
        out[at++] = (top * (1 - yy) + bottom * yy) / 255f;
      }
      float top = (tl >>> 24) * (1 - xx) + (tr >>> 24) * xx,
            bottom = (bl >>> 24) * (1 - xx) + (br >>> 24) * xx;
      out[at++] = (top * (1 - yy) + bottom * yy) / 255f;
    }
    return at;
  }
}
