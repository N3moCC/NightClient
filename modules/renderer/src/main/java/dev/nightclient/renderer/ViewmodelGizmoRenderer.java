package dev.nightclient.renderer;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.ViewmodelProjection;

public final class ViewmodelGizmoRenderer {
  private static final int SIDES = 20, RING_STEPS = 64, TUBE_STEPS = 8;
  private static final int MAX_VERTICES = 2048, MAX_TRIANGLES = 4096;
  private static final float[] SIDE_COS = new float[SIDES];
  private static final float[] SIDE_SIN = new float[SIDES];
  private static final float[] SIDE_MID_COS = new float[SIDES];
  private static final float[] SIDE_MID_SIN = new float[SIDES];
  private static final float[] RING_COS = new float[RING_STEPS];
  private static final float[] RING_SIN = new float[RING_STEPS];
  private static final float[] RING_MID_COS = new float[RING_STEPS];
  private static final float[] RING_MID_SIN = new float[RING_STEPS];
  private static final float[] TUBE_COS = new float[TUBE_STEPS];
  private static final float[] TUBE_SIN = new float[TUBE_STEPS];
  private static final float[] TUBE_MID_COS = new float[TUBE_STEPS];
  private static final float[] TUBE_MID_SIN = new float[TUBE_STEPS];

  static {
    for (int i = 0; i < SIDES; i++) {
      double a = i * Math.PI * 2 / SIDES, m = (i + .5) * Math.PI * 2 / SIDES;
      SIDE_COS[i] = (float) Math.cos(a);
      SIDE_SIN[i] = (float) Math.sin(a);
      SIDE_MID_COS[i] = (float) Math.cos(m);
      SIDE_MID_SIN[i] = (float) Math.sin(m);
    }
    for (int i = 0; i < RING_STEPS; i++) {
      double a = i * Math.PI * 2 / RING_STEPS, m = (i + .5) * Math.PI * 2 / RING_STEPS;
      RING_COS[i] = (float) Math.cos(a);
      RING_SIN[i] = (float) Math.sin(a);
      RING_MID_COS[i] = (float) Math.cos(m);
      RING_MID_SIN[i] = (float) Math.sin(m);
    }
    for (int i = 0; i < TUBE_STEPS; i++) {
      double a = i * Math.PI * 2 / TUBE_STEPS, m = (i + .5) * Math.PI * 2 / TUBE_STEPS;
      TUBE_COS[i] = (float) Math.cos(a);
      TUBE_SIN[i] = (float) Math.sin(a);
      TUBE_MID_COS[i] = (float) Math.cos(m);
      TUBE_MID_SIN[i] = (float) Math.sin(m);
    }
  }
  private final float[] vx = new float[MAX_VERTICES], vy = new float[MAX_VERTICES],
                        vz = new float[MAX_VERTICES];
  private final float[] sx = new float[MAX_VERTICES], sy = new float[MAX_VERTICES],
                        sz = new float[MAX_VERTICES];
  private final boolean[] valid = new boolean[MAX_VERTICES];
  private final int[] ta = new int[MAX_TRIANGLES], tb = new int[MAX_TRIANGLES],
                      tc = new int[MAX_TRIANGLES];
  private final int[] colors = new int[MAX_TRIANGLES], order = new int[MAX_TRIANGLES];
  private final float[] depths = new float[MAX_TRIANGLES];
  private int vertices, triangles;
  private int color;

  public void render(Canvas canvas, ViewmodelProjection projection, String mode,
      String selectedAxis, String hoveredAxis, float radiusPixels, float thicknessPixels,
      int colorX, int colorY, int colorZ, float x, float y, float scaleX, float scaleY,
      float opacity) {
    if (canvas == null || projection == null
        || !finite(radiusPixels, thicknessPixels, x, y, scaleX, scaleY, opacity)
        || radiusPixels <= 0 || thicknessPixels <= 0 || scaleX == 0 || scaleY == 0 || opacity <= 0)
      return;
    if (!"G".equals(mode) && !"R".equals(mode) && !"S".equals(mode)) return;
    opacity = Math.min(1f, opacity);
    float[] pivot = projection.pivot();
    if (pivot == null || !finite(pivot[0], pivot[1], pivot[2]) || Math.abs(pivot[2]) > 1.001f)
      return;
    radiusPixels = Math.min(1024, radiusPixels);
    float radius = projection.radius(radiusPixels), unit = radius / radiusPixels;
    float thickness = Math.max(.6f, Math.min(12, thicknessPixels)) * unit;
    vertices = triangles = 0;
    int[] palette = {colorX, colorY, colorZ};
    String[] names = {"X", "Y", "Z"};
    float scaleMean = (Math.abs(scaleX) + Math.abs(scaleY)) * .5f;
    float shaftStroke = Math.max(1.35f, Math.min(3.2f, thicknessPixels * .52f)) * scaleMean;
    for (int axis = 0; axis < 3; axis++) {
      int baseColor = palette[axis];
      boolean isSelected = selectedAxis != null && selectedAxis.contains(names[axis]);
      boolean isHovered = hoveredAxis != null && hoveredAxis.contains(names[axis]);
      color = isSelected ? lighten(baseColor, .38f)
          : isHovered    ? lighten(baseColor, .22f)
                         : baseColor;
      if ((color >>> 24) == 0) continue;
      if (isSelected) {
        float[] dir = "G".equals(mode) ? projection.axisVector(axis, selectedAxis.startsWith("L:"))
                                       : projection.axis(mode, axis);
        float guideSpan = radius * 3.8f;
        float[] g0 = projection.project(-dir[0] * guideSpan, -dir[1] * guideSpan, -dir[2] * guideSpan),
                g1 = projection.project(dir[0] * guideSpan, dir[1] * guideSpan, dir[2] * guideSpan);
        if (g0 != null && g1 != null && finite(g0[0], g0[1], g1[0], g1[1])) {
          int guideColor = Math.round((color >>> 24) * opacity * .55f) << 24 | color & 0xffffff;
          canvas.line(x + g0[0] * scaleX, y + g0[1] * scaleY, x + g1[0] * scaleX,
              y + g1[1] * scaleY, Math.max(1f, shaftStroke * .72f), guideColor);
        }
      }
      if ("R".equals(mode)) {
        float tubeRadius = Math.max(unit * .85f, thickness * .34f);
        torus(projection.ring(axis), radius, tubeRadius);
      } else {
        float[] d = projection.axis(mode, axis), u = perpendicular(d), v = cross(d, u);
        float innerOffset = Math.min(radius * .14f, Math.max(unit * 5.5f, thickness * 1.4f));
        float shaftRadius = Math.max(unit * .78f, thickness * .26f);
        float shaftEnd;
        if ("G".equals(mode)) {
          float headLength = Math.min(radius * .25f, Math.max(radius * .19f, thickness * 3.3f));
          float headRadius = Math.min(radius * .092f, Math.max(unit * 3.1f, thickness * 1.15f));
          shaftEnd = Math.max(innerOffset, radius - headLength);
          cylinder(d, u, v, innerOffset, shaftEnd, shaftRadius);
          cone(d, u, v, shaftEnd, radius, headRadius);
        } else {
          float half = Math.min(radius * .078f, Math.max(unit * 2.5f, thickness * .95f));
          shaftEnd = Math.max(innerOffset, radius - half * 2);
          cylinder(d, u, v, innerOffset, shaftEnd, shaftRadius);
          box(d, u, v, radius - half, half);
        }
        float[] s0 = projection.project(
                    d[0] * innerOffset, d[1] * innerOffset, d[2] * innerOffset),
                s1 = projection.project(d[0] * shaftEnd, d[1] * shaftEnd, d[2] * shaftEnd);
        if (s0 != null && s1 != null && finite(s0[0], s0[1], s1[0], s1[1])) {
          int lineColor = Math.round((color >>> 24) * opacity) << 24 | color & 0xffffff;
          canvas.line(x + s0[0] * scaleX, y + s0[1] * scaleY, x + s1[0] * scaleX,
              y + s1[1] * scaleY, shaftStroke, lineColor);
        }
      }
    }
    for (int i = 0; i < vertices; i++) {
      float[] p = projection.project(vx[i], vy[i], vz[i]);
      valid[i] = p != null && finite(p[0], p[1], p[2]) && Math.abs(p[2]) <= 1.001f
          && Math.abs(p[0]) <= projection.width * 16f + 1024
          && Math.abs(p[1]) <= projection.height * 16f + 1024;
      if (valid[i]) {
        sx[i] = p[0];
        sy[i] = p[1];
        sz[i] = p[2];
      }
    }
    int count = 0;
    for (int i = 0; i < triangles; i++) {
      int a = ta[i], b = tb[i], c = tc[i];
      if (!valid[a] || !valid[b] || !valid[c]) continue;
      if ((sx[a] < 0 && sx[b] < 0 && sx[c] < 0)
          || (sx[a] > projection.width && sx[b] > projection.width && sx[c] > projection.width)
          || (sy[a] < 0 && sy[b] < 0 && sy[c] < 0)
          || (sy[a] > projection.height && sy[b] > projection.height && sy[c] > projection.height))
        continue;
      depths[i] = (sz[a] + sz[b] + sz[c]) / 3;
      order[count++] = i;
    }
    if (count > 1) sort(0, count - 1);
    opacity = Math.min(1, opacity);
    for (int j = 0; j < count; j++) {
      int i = order[j], a = ta[i], b = tb[i], c = tc[i];
      float ax = x + sx[a] * scaleX, ay = y + sy[a] * scaleY;
      float bx = x + sx[b] * scaleX, by = y + sy[b] * scaleY;
      float cx = x + sx[c] * scaleX, cy = y + sy[c] * scaleY;
      float winding = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
      if (!Float.isFinite(winding) || Math.abs(winding) < .0001f) continue;
      int tint = colors[i];
      if ("R".equals(mode) && depths[i] > pivot[2]) tint = multiply(tint, .41f);
      tint = Math.round((tint >>> 24) * opacity) << 24 | tint & 0xffffff;
      if ((tint >>> 24) == 0) continue;
      if (winding > 0)
        canvas.fillTriangle(ax, ay, bx, by, cx, cy, tint);
      else
        canvas.fillTriangle(ax, ay, cx, cy, bx, by, tint);
    }
  }

  private void cylinder(float[] d, float[] u, float[] v, float start, float end, float radius) {
    int first = vertices;
    for (int i = 0; i < SIDES; i++) {
      float a = SIDE_COS[i] * radius, b = SIDE_SIN[i] * radius;
      vertex(d[0] * start + u[0] * a + v[0] * b, d[1] * start + u[1] * a + v[1] * b,
          d[2] * start + u[2] * a + v[2] * b);
      vertex(d[0] * end + u[0] * a + v[0] * b, d[1] * end + u[1] * a + v[1] * b,
          d[2] * end + u[2] * a + v[2] * b);
    }
    int bottom = vertex(d[0] * start, d[1] * start, d[2] * start),
        top = vertex(d[0] * end, d[1] * end, d[2] * end);
    for (int i = 0; i < SIDES; i++) {
      int a = first + i * 2, b = first + ((i + 1) % SIDES) * 2;
      float ca = SIDE_MID_COS[i], sa = SIDE_MID_SIN[i];
      quad(a, b, b + 1, a + 1, u[0] * ca + v[0] * sa, u[1] * ca + v[1] * sa, u[2] * ca + v[2] * sa);
      triangle(bottom, b, a, -d[0], -d[1], -d[2]);
      triangle(top, a + 1, b + 1, d[0], d[1], d[2]);
    }
  }

  private void cone(float[] d, float[] u, float[] v, float start, float end, float radius) {
    int first = vertices;
    for (int i = 0; i < SIDES; i++) {
      float a = SIDE_COS[i] * radius, b = SIDE_SIN[i] * radius;
      vertex(d[0] * start + u[0] * a + v[0] * b, d[1] * start + u[1] * a + v[1] * b,
          d[2] * start + u[2] * a + v[2] * b);
    }
    int tip = vertex(d[0] * end, d[1] * end, d[2] * end),
        center = vertex(d[0] * start, d[1] * start, d[2] * start);
    float span = end - start;
    for (int i = 0; i < SIDES; i++) {
      int a = first + i, b = first + (i + 1) % SIDES;
      float ca = SIDE_MID_COS[i] * span, sa = SIDE_MID_SIN[i] * span;
      triangle(a, b, tip, u[0] * ca + v[0] * sa + d[0] * radius,
          u[1] * ca + v[1] * sa + d[1] * radius, u[2] * ca + v[2] * sa + d[2] * radius);
      triangle(center, b, a, -d[0], -d[1], -d[2]);
    }
  }

  private void box(float[] d, float[] u, float[] v, float center, float half) {
    int first = vertices;
    for (int i = 0; i < 8; i++) {
      float a = (i & 1) == 0 ? -half : half, b = (i & 2) == 0 ? -half : half,
            c = (i & 4) == 0 ? -half : half;
      vertex(d[0] * (center + a) + u[0] * b + v[0] * c, d[1] * (center + a) + u[1] * b + v[1] * c,
          d[2] * (center + a) + u[2] * b + v[2] * c);
    }
    quad(first, first + 2, first + 6, first + 4, -d[0], -d[1], -d[2]);
    quad(first + 1, first + 5, first + 7, first + 3, d[0], d[1], d[2]);
    quad(first, first + 4, first + 5, first + 1, -u[0], -u[1], -u[2]);
    quad(first + 2, first + 3, first + 7, first + 6, u[0], u[1], u[2]);
    quad(first, first + 1, first + 3, first + 2, -v[0], -v[1], -v[2]);
    quad(first + 4, first + 6, first + 7, first + 5, v[0], v[1], v[2]);
  }

  private void torus(float[][] basis, float radius, float tube) {
    float[] u = basis[0], v = basis[1], n = basis[2];
    int first = vertices;
    for (int i = 0; i < RING_STEPS; i++) {
      float ca = RING_COS[i], sa = RING_SIN[i];
      for (int j = 0; j < TUBE_STEPS; j++) {
        float r = radius + tube * TUBE_COS[j], h = tube * TUBE_SIN[j];
        vertex((u[0] * ca + v[0] * sa) * r + n[0] * h, (u[1] * ca + v[1] * sa) * r + n[1] * h,
            (u[2] * ca + v[2] * sa) * r + n[2] * h);
      }
    }
    for (int i = 0; i < RING_STEPS; i++) {
      float ca = RING_MID_COS[i], sa = RING_MID_SIN[i];
      for (int j = 0; j < TUBE_STEPS; j++) {
        float cb = TUBE_MID_COS[j], sb = TUBE_MID_SIN[j];
        int p = first + i * TUBE_STEPS + j, q = first + ((i + 1) % RING_STEPS) * TUBE_STEPS + j;
        int r = first + ((i + 1) % RING_STEPS) * TUBE_STEPS + (j + 1) % TUBE_STEPS,
            s = first + i * TUBE_STEPS + (j + 1) % TUBE_STEPS;
        quad(p, q, r, s, (u[0] * ca + v[0] * sa) * cb + n[0] * sb,
            (u[1] * ca + v[1] * sa) * cb + n[1] * sb, (u[2] * ca + v[2] * sa) * cb + n[2] * sb);
      }
    }
  }

  private int vertex(float x, float y, float z) {
    int i = vertices++;
    vx[i] = x;
    vy[i] = y;
    vz[i] = z;
    return i;
  }
  private void quad(int a, int b, int c, int d, float nx, float ny, float nz) {
    triangle(a, b, c, nx, ny, nz);
    triangle(a, c, d, nx, ny, nz);
  }
  private void triangle(int a, int b, int c, float nx, float ny, float nz) {
    int i = triangles++;
    ta[i] = a;
    tb[i] = b;
    tc[i] = c;
    float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
    float diffuse =
        length < .000001f ? 0 : Math.max(0, (-.38f * nx + .70f * ny + .60f * nz) / length);
    colors[i] = multiply(color, .78f + .22f * diffuse);
  }
  private void sort(int low, int high) {
    int i = low, j = high;
    float pivot = depths[order[(low + high) >>> 1]];
    while (i <= j) {
      while (depths[order[i]] > pivot) i++;
      while (depths[order[j]] < pivot) j--;
      if (i <= j) {
        int swap = order[i];
        order[i++] = order[j];
        order[j--] = swap;
      }
    }
    if (low < j) sort(low, j);
    if (i < high) sort(i, high);
  }
  private static int multiply(int color, float factor) {
    return color & 0xff000000 | Math.min(255, Math.round((color >>> 16 & 255) * factor)) << 16
        | Math.min(255, Math.round((color >>> 8 & 255) * factor)) << 8
        | Math.min(255, Math.round((color & 255) * factor));
  }
  private static int lighten(int color, float amount) {
    return color & 0xff000000 | Math.round((color >>> 16 & 255) * (1 - amount) + 255 * amount) << 16
        | Math.round((color >>> 8 & 255) * (1 - amount) + 255 * amount) << 8
        | Math.round((color & 255) * (1 - amount) + 255 * amount);
  }
  private static float[] perpendicular(float[] d) {
    float[] ref = Math.abs(d[0]) < .8f ? new float[] {1, 0, 0} : new float[] {0, 1, 0};
    float dot = ref[0] * d[0] + ref[1] * d[1] + ref[2] * d[2], length = 0;
    for (int i = 0; i < 3; i++) {
      ref[i] -= dot * d[i];
      length += ref[i] * ref[i];
    }
    length = (float) Math.sqrt(length);
    for (int i = 0; i < 3; i++) ref[i] /= length;
    return ref;
  }
  private static float[] cross(float[] a, float[] b) {
    return new float[] {
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
  }
  private static boolean finite(float... values) {
    for (float value : values)
      if (!Float.isFinite(value)) return false;
    return true;
  }
}
