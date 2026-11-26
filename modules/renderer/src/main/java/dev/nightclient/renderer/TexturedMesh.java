package dev.nightclient.renderer;

public final class TexturedMesh {
  private TexturedMesh() {}

  public static float[] roundedRect(float x, float y, float w, float h, float radius, int color,
      float u0, float v0, float u1, float v1) {
    return roundedRectCorners(x, y, w, h, radius, radius, radius, radius, color, u0, v0, u1, v1);
  }

  public static float[] roundedRectCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, int color, float u0, float v0, float u1, float v1) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || !Float.isFinite(h)
        || w <= 0 || h <= 0 || (color >>> 24) == 0)
      return new float[0];
    if (tl <= 0 && tr <= 0 && bl <= 0 && br <= 0) {
      Builder rectangle = new Builder(6 * 8, x, y, w, h, u0, v0, u1, v1);
      rectangle.triangle(x, y, color, x + w, y, color, x + w, y + h, color);
      rectangle.triangle(x, y, color, x + w, y + h, color, x, y + h, color);
      return rectangle.data;
    }
    float r =
        Math.max(0, Math.min(Math.max(Math.max(tl, tr), Math.max(bl, br)), Math.min(w, h) / 2));
    int steps = Math.max(4, Math.min(12, (int) Math.ceil(r * .7f)));
    float[] inner = contour(x, y, w, h, tl, tr, bl, br, steps),
            outer = contour(x - .6f, y - .6f, w + 1.2f, h + 1.2f, tl + .6f, tr + .6f, bl + .6f,
                br + .6f, steps);
    Builder b = new Builder(inner.length / 2 * 9 * 8, x, y, w, h, u0, v0, u1, v1);
    int n = inner.length / 2;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      b.triangle(x + w / 2, y + h / 2, color, inner[i * 2], inner[i * 2 + 1], color, inner[j * 2],
          inner[j * 2 + 1], color);
      b.triangle(outer[i * 2], outer[i * 2 + 1], color & 0xffffff, outer[j * 2], outer[j * 2 + 1],
          color & 0xffffff, inner[j * 2], inner[j * 2 + 1], color);
      b.triangle(outer[i * 2], outer[i * 2 + 1], color & 0xffffff, inner[j * 2], inner[j * 2 + 1],
          color, inner[i * 2], inner[i * 2 + 1], color);
    }
    return b.data;
  }

  public static float[] roundedRectRotatedUv(float x, float y, float w, float h, float radius,
      int color, float uCenter, float vCenter, float uHalfSpan, float vHalfSpan, float angleRad) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || !Float.isFinite(h)
        || w <= 0 || h <= 0 || (color >>> 24) == 0)
      return new float[0];
    float cos = (float) Math.cos(angleRad);
    float sin = (float) Math.sin(angleRad);
    if (radius <= -3.5f) {
      int sides = Math.round(-radius);
      float cx = x + w * 0.5f, cy = y + h * 0.5f;
      float[] inner = polygonContour(cx, cy, w * 0.5f, h * 0.5f, sides);
      float[] outer = polygonContour(cx, cy, w * 0.5f + 0.65f, h * 0.5f + 0.65f, sides);
      int n = inner.length / 2;
      RotatedBuilder b = new RotatedBuilder(
          n * 9 * 8, x, y, w, h, uCenter, vCenter, uHalfSpan, vHalfSpan, cos, sin);
      for (int i = 0; i < n; i++) {
        int j = (i + 1) % n;
        b.triangle(cx, cy, color, inner[i * 2], inner[i * 2 + 1], color, inner[j * 2],
            inner[j * 2 + 1], color);
        b.triangle(outer[i * 2], outer[i * 2 + 1], color & 0xffffff, outer[j * 2], outer[j * 2 + 1],
            color & 0xffffff, inner[j * 2], inner[j * 2 + 1], color);
        b.triangle(outer[i * 2], outer[i * 2 + 1], color & 0xffffff, inner[j * 2], inner[j * 2 + 1],
            color, inner[i * 2], inner[i * 2 + 1], color);
      }
      return b.data;
    }
    float r = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
    if (r <= 0.01f) {
      RotatedBuilder rect = new RotatedBuilder(
          6 * 8, x, y, w, h, uCenter, vCenter, uHalfSpan, vHalfSpan, cos, sin);
      rect.triangle(x, y, color, x + w, y, color, x + w, y + h, color);
      rect.triangle(x, y, color, x + w, y + h, color, x, y + h, color);
      return rect.data;
    }
    int steps = Math.max(6, Math.min(20, (int) Math.ceil(r * 0.45f)));
    float[] inner = contour(x, y, w, h, r, r, r, r, steps);
    float[] outer =
        contour(x - .6f, y - .6f, w + 1.2f, h + 1.2f, r + .6f, r + .6f, r + .6f, r + .6f, steps);
    int n = inner.length / 2;
    RotatedBuilder b = new RotatedBuilder(
        n * 9 * 8, x, y, w, h, uCenter, vCenter, uHalfSpan, vHalfSpan, cos, sin);
    float cx = x + w * 0.5f, cy = y + h * 0.5f;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      b.triangle(cx, cy, color, inner[i * 2], inner[i * 2 + 1], color, inner[j * 2],
          inner[j * 2 + 1], color);
      b.triangle(outer[i * 2], outer[i * 2 + 1], color & 0xffffff, outer[j * 2], outer[j * 2 + 1],
          color & 0xffffff, inner[j * 2], inner[j * 2 + 1], color);
      b.triangle(outer[i * 2], outer[i * 2 + 1], color & 0xffffff, inner[j * 2], inner[j * 2 + 1],
          color, inner[i * 2], inner[i * 2 + 1], color);
    }
    return b.data;
  }

  public static float[] polygonContour(float cx, float cy, float hw, float hh, int sides) {
    if (sides == 4) {
      return new float[] {
          cx, cy - hh,
          cx + hw, cy,
          cx, cy + hh,
          cx - hw, cy
      };
    }
    if (sides == 6) {
      float dx = hw * 0.52f;
      float dy = hh * 0.90f;
      return new float[] {
          cx - dx, cy - dy,
          cx + dx, cy - dy,
          cx + hw, cy,
          cx + dx, cy + dy,
          cx - dx, cy + dy,
          cx - hw, cy
      };
    }
    float bx = hw * 0.56f;
    float by = hh * 0.56f;
    return new float[] {
        cx - bx, cy - hh,
        cx + bx, cy - hh,
        cx + hw, cy - by,
        cx + hw, cy + by,
        cx + bx, cy + hh,
        cx - bx, cy + hh,
        cx - hw, cy + by,
        cx - hw, cy - by
    };
  }

  private static float[] contour(
      float x, float y, float w, float h, float tl, float tr, float bl, float br, int steps) {
    return RoundedContour.create(x, y, w, h, tl, tr, bl, br, steps);
  }

  private static final class Builder {
    final float[] data;
    final float x, y, w, h, u0, v0, du, dv;
    int at;

    Builder(
        int capacity, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
      data = new float[capacity];
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.u0 = u0;
      this.v0 = v0;
      du = u1 - u0;
      dv = v1 - v0;
    }

    void vertex(float px, float py, int c) {
      data[at++] = px;
      data[at++] = py;
      data[at++] = u0 + (px - x) / w * du;
      data[at++] = v0 + (py - y) / h * dv;
      data[at++] = ((c >>> 16) & 255) / 255f;
      data[at++] = ((c >>> 8) & 255) / 255f;
      data[at++] = (c & 255) / 255f;
      data[at++] = (c >>> 24) / 255f;
    }

    void triangle(
        float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy, int cc) {
      vertex(ax, ay, ac);
      if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) < 0) {
        vertex(cx, cy, cc);
        vertex(bx, by, bc);
      } else {
        vertex(bx, by, bc);
        vertex(cx, cy, cc);
      }
    }
  }

  private static final class RotatedBuilder {
    final float[] data;
    final float cx, cy, invHalfW, invHalfH, uCenter, vCenter, uHalfSpan, vHalfSpan, cos, sin;
    int at;

    RotatedBuilder(int capacity, float x, float y, float w, float h, float uCenter, float vCenter,
        float uHalfSpan, float vHalfSpan, float cos, float sin) {
      data = new float[capacity];
      this.cx = x + w * 0.5f;
      this.cy = y + h * 0.5f;
      this.invHalfW = 2f / w;
      this.invHalfH = 2f / h;
      this.uCenter = uCenter;
      this.vCenter = vCenter;
      this.uHalfSpan = uHalfSpan;
      this.vHalfSpan = vHalfSpan;
      this.cos = cos;
      this.sin = sin;
    }

    void vertex(float px, float py, int c) {
      float nx = (px - cx) * invHalfW;
      float ny = (py - cy) * invHalfH;
      float rx = nx * cos - ny * sin;
      float ry = nx * sin + ny * cos;
      data[at++] = px;
      data[at++] = py;
      data[at++] = uCenter + rx * uHalfSpan;
      data[at++] = vCenter + ry * vHalfSpan;
      data[at++] = ((c >>> 16) & 255) / 255f;
      data[at++] = ((c >>> 8) & 255) / 255f;
      data[at++] = (c & 255) / 255f;
      data[at++] = (c >>> 24) / 255f;
    }

    void triangle(
        float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy, int cc) {
      vertex(ax, ay, ac);
      if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) < 0) {
        vertex(cx, cy, cc);
        vertex(bx, by, bc);
      } else {
        vertex(bx, by, bc);
        vertex(cx, cy, cc);
      }
    }
  }
}
