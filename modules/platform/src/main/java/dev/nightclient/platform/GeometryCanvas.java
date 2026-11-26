package dev.nightclient.platform;

import dev.nightclient.api.Canvas;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.renderer.RoundedContour;
import dev.nightclient.renderer.RoundedQuadMesh;
import java.util.Arrays;

public abstract class GeometryCanvas implements Canvas {
  private static final ThreadLocal<CornerCache> CORNER_CACHE =
      ThreadLocal.withInitial(CornerCache::new);
  private static final int CORNER_CACHE_SIZE = 32;
  private static final class CornerCache {
    final long[] hashes = new long[CORNER_CACHE_SIZE];
    final float[][] params = new float[CORNER_CACHE_SIZE][];
    final float[][] meshes = new float[CORNER_CACHE_SIZE][];
  }
  private static long cornerHash(
      float x, float y, float w, float h, float tl, float tr, float bl, float br) {
    long result = -3750763034362895579L;
    result = (result ^ (Float.floatToIntBits(x) & 0xffffffffL)) * 1099511628211L;
    result = (result ^ (Float.floatToIntBits(y) & 0xffffffffL)) * 1099511628211L;
    result = (result ^ (Float.floatToIntBits(w) & 0xffffffffL)) * 1099511628211L;
    result = (result ^ (Float.floatToIntBits(h) & 0xffffffffL)) * 1099511628211L;
    result = (result ^ (Float.floatToIntBits(tl) & 0xffffffffL)) * 1099511628211L;
    result = (result ^ (Float.floatToIntBits(tr) & 0xffffffffL)) * 1099511628211L;
    result = (result ^ (Float.floatToIntBits(bl) & 0xffffffffL)) * 1099511628211L;
    return (result ^ (Float.floatToIntBits(br) & 0xffffffffL)) * 1099511628211L;
  }
  private static final Object TEXT_METRICS_KEY = new Object();

  @Override
  public Object textMetricsKey() {
    return TEXT_METRICS_KEY;
  }

  private static final int MAX_BATCH_FLOATS = 65520;
  private static final ThreadLocal<float[]> SCRATCH = new ThreadLocal<float[]>();
  private float[] vertices = borrowBuffer();

  private static float[] borrowBuffer() {
    float[] buffer = SCRATCH.get();
    SCRATCH.set(null);
    return buffer == null ? new float[4096] : buffer;
  }

  private int used;
  private float[] roundedVertices;
  private int roundedUsed;
  private static final ThreadLocal<float[]> ROUNDED_SCRATCH = new ThreadLocal<float[]>();
  private static final ThreadLocal<float[]> GLYPH_SCRATCH = new ThreadLocal<float[]>();
  private static final ThreadLocal<float[][]> CLIP_SCRATCH = new ThreadLocal<float[][]>();
  private static final ThreadLocal<float[][]> CONTOUR_SCRATCH = new ThreadLocal<float[][]>();

  private static float[][] contourScratch() {
    float[][] scratch = CONTOUR_SCRATCH.get();
    if (scratch == null) {
      scratch = new float[][] {new float[RoundedContour.MAX_FLOATS],
          new float[RoundedContour.MAX_FLOATS], new float[RoundedContour.MAX_FLOATS]};
      CONTOUR_SCRATCH.set(scratch);
    }
    return scratch;
  }
  private static final float[][] EMPTY_CLIPS = new float[0][];
  private float[][] clips = borrowClips();
  private int clipDepth;
  private float[] glyphVertices;
  private int glyphUsed;
  protected final int width;
  protected final int height;

  protected GeometryCanvas(int width, int height) {
    this.width = width;
    this.height = height;
  }

  private static float[][] borrowClips() {
    float[][] buffer = CLIP_SCRATCH.get();
    CLIP_SCRATCH.set(null);
    return buffer == null ? new float[4][] : buffer;
  }

  protected abstract void draw(float[] vertices, int length, float[] clip);

  protected boolean usesGpuRounded() {
    return false;
  }

  protected void drawRounded(float[] vertices, int length, float[] clip) {
    throw new UnsupportedOperationException("Analytic surface executor is missing");
  }

  protected abstract void glyph(float x, float y, float width, float height, int atlasX, int atlasY,
      int atlasWidth, int atlasHeight, int argb);

  protected abstract void drawItem(String id, float x, float y, float size, float alpha);

  protected abstract void drawBackdrop(float x, float y, float w, float h, float radius,
      float blurRadius, int quality, float opacity, float[] clip);

  protected void drawBackdropCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, float blurRadius, int quality, float opacity, float[] clip) {
    drawBackdrop(x, y, w, h, Math.min(Math.min(tl, tr), Math.min(bl, br)), blurRadius, quality,
        opacity, clip);
  }

  protected abstract void drawTexture(String id, float x, float y, float w, float h, float radius,
      float tileSize, int argb, float[] clip);

  protected void drawTextureCorners(String id, float x, float y, float w, float h, float tl,
      float tr, float bl, float br, float tileSize, int argb, float[] clip) {
    drawTexture(id, x, y, w, h, Math.max(Math.max(tl, tr), Math.max(bl, br)), tileSize, argb, clip);
  }

  protected void drawInvertedRect(float x, float y, float w, float h, float opacity, float[] clip) {
    throw new UnsupportedOperationException("This canvas does not implement destination inversion");
  }

  protected float rasterDensity() {
    return 1;
  }

  protected void beginText() {}

  protected void beginText(FontAtlas atlas) {
    beginText();
  }

  protected void endText() {}

  protected void flushText() {}

  protected final float[] clip() {
    return clipDepth == 0 ? null : clips[clipDepth - 1];
  }

  protected final boolean clipVisible() {
    float[] c = clip();
    return c == null || c[2] > c[0] && c[3] > c[1];
  }

  protected final void beginGlyphs() {
    glyphUsed = 0;
  }

  protected final int glyphCount() {
    return glyphUsed;
  }

  protected final float[] glyphData() {
    return glyphVertices;
  }

  protected final void glyphQuad(
      float x, float y, float width, float height, float u, float v, float u1, float v1, int argb) {
    if (glyphVertices == null) glyphVertices = borrowGlyphBuffer();
    if (glyphUsed + 48 > glyphVertices.length)
      glyphVertices = Arrays.copyOf(glyphVertices, glyphVertices.length * 2);
    float red = ((argb >>> 16) & 255) / 255f, green = ((argb >>> 8) & 255) / 255f,
          blue = (argb & 255) / 255f, alpha = (argb >>> 24) / 255f;
    float[] out = glyphVertices;
    int at = glyphUsed;
    at = glyphVertex(out, at, x, y, u, v, red, green, blue, alpha);
    at = glyphVertex(out, at, x + width, y, u1, v, red, green, blue, alpha);
    at = glyphVertex(out, at, x + width, y + height, u1, v1, red, green, blue, alpha);
    at = glyphVertex(out, at, x, y, u, v, red, green, blue, alpha);
    at = glyphVertex(out, at, x + width, y + height, u1, v1, red, green, blue, alpha);
    at = glyphVertex(out, at, x, y + height, u, v1, red, green, blue, alpha);
    glyphUsed = at;
  }

  private static float[] borrowGlyphBuffer() {
    float[] buffer = GLYPH_SCRATCH.get();
    GLYPH_SCRATCH.set(null);
    return buffer == null ? new float[2048] : buffer;
  }

  private static int glyphVertex(float[] out, int at, float x, float y, float u, float v, float red,
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

  public final void finish() {
    if (vertices == null) return;
    try {
      flush();
    } finally {
      try {
        finished();
      } finally {
        float[] available = SCRATCH.get();
        if (available == null || available.length < vertices.length) SCRATCH.set(vertices);
        if (roundedVertices != null) {
          float[] roundedAvailable = ROUNDED_SCRATCH.get();
          if (roundedAvailable == null || roundedAvailable.length < roundedVertices.length)
            ROUNDED_SCRATCH.set(roundedVertices);
        }
        if (glyphVertices != null) {
          float[] glyphAvailable = GLYPH_SCRATCH.get();
          if (glyphAvailable == null || glyphAvailable.length < glyphVertices.length)
            GLYPH_SCRATCH.set(glyphVertices);
        }
        float[][] availableClips = CLIP_SCRATCH.get();
        if (availableClips == null || availableClips.length < clips.length) CLIP_SCRATCH.set(clips);
        vertices = null;
        roundedVertices = null;
        glyphVertices = null;
        used = roundedUsed = glyphUsed = clipDepth = 0;
        clips = EMPTY_CLIPS;
      }
    }
  }

  protected void finished() {}

  protected final void flush() {
    flushText();
    flushGeometry();
  }

  private void flushGeometry() {
    if (used > 0) {
      if (clipVisible()) draw(vertices, used, clip());
      used = 0;
    }
    flushRounded();
  }

  private void flushRounded() {
    if (roundedUsed > 0) {
      if (clipVisible()) drawRounded(roundedVertices, roundedUsed, clip());
      roundedUsed = 0;
    }
  }

  private boolean gpuRounded(float x, float y, float w, float h, float radius, float stroke, int tl,
      int tr, int bl, int br) {
    if (!usesGpuRounded() || radius < .25f || stroke >= radius) return false;
    if (!clipVisible()) return true;
    flushText();
    if (used > 0) {
      draw(vertices, used, clip());
      used = 0;
    }
    if (roundedVertices == null) {
      roundedVertices = ROUNDED_SCRATCH.get();
      ROUNDED_SCRATCH.set(null);
      if (roundedVertices == null) roundedVertices = new float[4096];
    }
    if (roundedUsed + RoundedQuadMesh.MAX_FLOATS > MAX_BATCH_FLOATS) flushRounded();
    if (roundedUsed + RoundedQuadMesh.MAX_FLOATS > roundedVertices.length)
      roundedVertices =
          Arrays.copyOf(roundedVertices, Math.min(MAX_BATCH_FLOATS, roundedVertices.length * 2));
    roundedUsed = RoundedQuadMesh.append(
        roundedVertices, roundedUsed, x, y, w, h, radius, stroke, rasterDensity(), tl, tr, bl, br);
    return true;
  }

  private void vertex(float x, float y, int argb) {
    vertices[used++] = x;
    vertices[used++] = y;
    vertices[used++] = ((argb >>> 16) & 255) / 255f;
    vertices[used++] = ((argb >>> 8) & 255) / 255f;
    vertices[used++] = (argb & 255) / 255f;
    vertices[used++] = (argb >>> 24) / 255f;
  }

  private void triangle(
      float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy, int cc) {
    if (vertices == null) throw new IllegalStateException("Canvas is finished");
    flushText();
    flushRounded();
    if (used + 18 > MAX_BATCH_FLOATS) flush();
    if (used + 18 > vertices.length)
      vertices = Arrays.copyOf(vertices, Math.min(MAX_BATCH_FLOATS, vertices.length * 2));

    float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    if (area < 0) {
      vertex(ax, ay, ac);
      vertex(cx, cy, cc);
      vertex(bx, by, bc);
    } else {
      vertex(ax, ay, ac);
      vertex(bx, by, bc);
      vertex(cx, cy, cc);
    }
  }

  @Override
  public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int argb) {
    if ((argb >>> 24) != 0 && clipVisible()) triangle(ax, ay, argb, bx, by, argb, cx, cy, argb);
  }

  @Override
  public void fillPolygon(float centerX, float centerY, float[] xy, int argb) {
    if ((argb >>> 24) == 0 || xy == null || xy.length < 6 || !clipVisible()) return;
    int n = xy.length / 2;
    float signedArea = 0f;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      signedArea += xy[i * 2] * xy[j * 2 + 1] - xy[j * 2] * xy[i * 2 + 1];
    }
    if (Math.abs(signedArea) < 1e-5f) return;
    float sign = signedArea >= 0f ? 1f : -1f;
    float fringe = 0.65f / Math.max(1f, rasterDensity());
    int edge = argb & 0x00ffffff;
    float[] ox = new float[n];
    float[] oy = new float[n];
    boolean[] valid = new boolean[n];
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      float ax = xy[i * 2], ay = xy[i * 2 + 1], bx = xy[j * 2], by = xy[j * 2 + 1];
      triangle(centerX, centerY, argb, ax, ay, argb, bx, by, argb);
      float dx = bx - ax, dy = by - ay;
      float len = (float) Math.hypot(dx, dy);
      if (len >= 1e-4f) {
        ox[i] = sign * dy / len * fringe;
        oy[i] = -sign * dx / len * fringe;
        valid[i] = true;
        quad(ax, ay, argb, bx, by, argb, bx + ox[i], by + oy[i], edge, ax + ox[i], ay + oy[i],
            edge);
      }
    }
    for (int i = 0; i < n; i++) {
      int prev = (i - 1 + n) % n;
      if (!valid[prev] || !valid[i]) continue;
      int next = (i + 1) % n;
      float vx = xy[i * 2], vy = xy[i * 2 + 1];
      float e1x = vx - xy[prev * 2], e1y = vy - xy[prev * 2 + 1];
      float e2x = xy[next * 2] - vx, e2y = xy[next * 2 + 1] - vy;
      if ((e1x * e2y - e1y * e2x) * sign > 1e-5f) {
        triangle(vx, vy, argb, vx + ox[prev], vy + oy[prev], edge, vx + ox[i], vy + oy[i], edge);
      }
    }
  }

  private void quad(float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy,
      int cc, float dx, float dy, int dc) {
    if (vertices == null) throw new IllegalStateException("Canvas is finished");
    flushText();
    flushRounded();

    if (used + 36 > MAX_BATCH_FLOATS) {
      triangle(ax, ay, ac, bx, by, bc, cx, cy, cc);
      triangle(ax, ay, ac, cx, cy, cc, dx, dy, dc);
      return;
    }
    if (used + 36 > vertices.length)
      vertices = Arrays.copyOf(vertices, Math.min(MAX_BATCH_FLOATS, vertices.length * 2));
    float ar = ((ac >>> 16) & 255) / 255f, ag = ((ac >>> 8) & 255) / 255f, ab = (ac & 255) / 255f,
          aa = (ac >>> 24) / 255f;
    float br = ((bc >>> 16) & 255) / 255f, bg = ((bc >>> 8) & 255) / 255f, bb = (bc & 255) / 255f,
          ba = (bc >>> 24) / 255f;
    float cr = ((cc >>> 16) & 255) / 255f, cg = ((cc >>> 8) & 255) / 255f, cb = (cc & 255) / 255f,
          ca = (cc >>> 24) / 255f;
    float dr = ((dc >>> 16) & 255) / 255f, dg = ((dc >>> 8) & 255) / 255f, db = (dc & 255) / 255f,
          da = (dc >>> 24) / 255f;
    float[] out = vertices;
    int at = used;
    float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    if (area < 0) {
      at = quadVertex(out, at, ax, ay, ar, ag, ab, aa);
      at = quadVertex(out, at, cx, cy, cr, cg, cb, ca);
      at = quadVertex(out, at, bx, by, br, bg, bb, ba);
    } else {
      at = quadVertex(out, at, ax, ay, ar, ag, ab, aa);
      at = quadVertex(out, at, bx, by, br, bg, bb, ba);
      at = quadVertex(out, at, cx, cy, cr, cg, cb, ca);
    }
    area = (cx - ax) * (dy - ay) - (cy - ay) * (dx - ax);
    if (area < 0) {
      at = quadVertex(out, at, ax, ay, ar, ag, ab, aa);
      at = quadVertex(out, at, dx, dy, dr, dg, db, da);
      at = quadVertex(out, at, cx, cy, cr, cg, cb, ca);
    } else {
      at = quadVertex(out, at, ax, ay, ar, ag, ab, aa);
      at = quadVertex(out, at, cx, cy, cr, cg, cb, ca);
      at = quadVertex(out, at, dx, dy, dr, dg, db, da);
    }
    used = at;
  }

  private static int quadVertex(
      float[] out, int at, float x, float y, float red, float green, float blue, float alpha) {
    out[at++] = x;
    out[at++] = y;
    out[at++] = red;
    out[at++] = green;
    out[at++] = blue;
    out[at++] = alpha;
    return at;
  }

  private void solidRect(float x, float y, float w, float h, int color) {
    if (vertices == null) throw new IllegalStateException("Canvas is finished");
    flushText();
    flushRounded();
    if (used + 36 > MAX_BATCH_FLOATS) {
      quad(x, y, color, x + w, y, color, x + w, y + h, color, x, y + h, color);
      return;
    }
    if (used + 36 > vertices.length)
      vertices = Arrays.copyOf(vertices, Math.min(MAX_BATCH_FLOATS, vertices.length * 2));
    float red = ((color >>> 16) & 255) / 255f, green = ((color >>> 8) & 255) / 255f,
          blue = (color & 255) / 255f, alpha = (color >>> 24) / 255f;
    float right = x + w, bottom = y + h;
    float[] out = vertices;
    int at = used;
    at = quadVertex(out, at, x, y, red, green, blue, alpha);
    at = quadVertex(out, at, right, y, red, green, blue, alpha);
    at = quadVertex(out, at, right, bottom, red, green, blue, alpha);
    at = quadVertex(out, at, x, y, red, green, blue, alpha);
    at = quadVertex(out, at, right, bottom, red, green, blue, alpha);
    at = quadVertex(out, at, x, bottom, red, green, blue, alpha);
    used = at;
  }

  private void gradientRectQuad(float x, float y, float w, float h, int top, int bottom) {
    if (vertices == null) throw new IllegalStateException("Canvas is finished");
    flushText();
    flushRounded();
    if (used + 36 > MAX_BATCH_FLOATS) {
      quad(x, y, top, x + w, y, top, x + w, y + h, bottom, x, y + h, bottom);
      return;
    }
    if (used + 36 > vertices.length)
      vertices = Arrays.copyOf(vertices, Math.min(MAX_BATCH_FLOATS, vertices.length * 2));
    float tr = ((top >>> 16) & 255) / 255f, tg = ((top >>> 8) & 255) / 255f,
          tb = (top & 255) / 255f, ta = (top >>> 24) / 255f;
    float br = ((bottom >>> 16) & 255) / 255f, bg = ((bottom >>> 8) & 255) / 255f,
          bb = (bottom & 255) / 255f, ba = (bottom >>> 24) / 255f;
    float right = x + w, lower = y + h;
    float[] out = vertices;
    int at = used;
    at = quadVertex(out, at, x, y, tr, tg, tb, ta);
    at = quadVertex(out, at, right, y, tr, tg, tb, ta);
    at = quadVertex(out, at, right, lower, br, bg, bb, ba);
    at = quadVertex(out, at, x, y, tr, tg, tb, ta);
    at = quadVertex(out, at, right, lower, br, bg, bb, ba);
    at = quadVertex(out, at, x, lower, br, bg, bb, ba);
    used = at;
  }

  @Override
  public void rect(float x, float y, float w, float h, int color) {
    if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
    solidRect(x, y, w, h, color);
  }

  @Override
  public void gradientRect(float x, float y, float w, float h, int top, int bottom) {
    if (w <= 0 || h <= 0) return;
    gradientRectQuad(x, y, w, h, top, bottom);
  }

  private static int fillContour(
      float[] out, float x, float y, float w, float h, float radius, int steps) {
    return RoundedContour.fill(out, x, y, w, h, radius, radius, radius, radius, steps);
  }

  private void ring(float[] outside, int len, int outerColor, float[] inside, int innerColor) {
    int n = len / 2;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      quad(outside[i * 2], outside[i * 2 + 1], outerColor, outside[j * 2], outside[j * 2 + 1],
          outerColor, inside[j * 2], inside[j * 2 + 1], innerColor, inside[i * 2],
          inside[i * 2 + 1], innerColor);
    }
  }

  @Override
  public void roundedRect(float x, float y, float w, float h, float radius, int color) {
    if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
    float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
    if (r < 0.25f) {
      rect(x, y, w, h, color);
      return;
    }
    if (gpuRounded(x, y, w, h, r, 0, color, color, color, color)) return;
    int steps = Math.max(4, Math.min(12, (int) Math.ceil(r * 0.7f)));
    float[][] scratch = contourScratch();
    float[] boundary = scratch[0], fringe = scratch[1];
    int len = fillContour(boundary, x, y, w, h, r, steps);
    int n = len / 2;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      triangle(x + w / 2, y + h / 2, color, boundary[i * 2], boundary[i * 2 + 1], color,
          boundary[j * 2], boundary[j * 2 + 1], color);
    }
    fillContour(fringe, x - 0.6f, y - 0.6f, w + 1.2f, h + 1.2f, r + 0.6f, steps);
    ring(fringe, len, color & 0xFFFFFF, boundary, color);
  }

  @Override
  public void strokeRoundedRect(
      float x, float y, float w, float h, float radius, float thickness, int color) {
    if (w <= 0 || h <= 0 || thickness <= 0 || (color >>> 24) == 0) return;
    if (thickness * 2 >= Math.min(w, h)) {
      roundedRect(x, y, w, h, radius, color);
      return;
    }
    float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
    if (gpuRounded(x, y, w, h, r, thickness, color, color, color, color)) return;
    int steps = Math.max(4, Math.min(12, (int) Math.ceil(radius * 0.7f)));
    float[][] scratch = contourScratch();
    float[] outer = scratch[0], inner = scratch[1], fringe = scratch[2];
    int len = fillContour(outer, x, y, w, h, radius, steps);
    fillContour(inner, x + thickness, y + thickness, w - 2 * thickness, h - 2 * thickness,
        Math.max(0, radius - thickness), steps);
    ring(outer, len, color, inner, color);
    fillContour(fringe, x - 0.5f, y - 0.5f, w + 1, h + 1, radius + 0.5f, steps);
    ring(fringe, len, color & 0xFFFFFF, outer, color);
    float innerFringe = Math.min(0.5f, Math.min(w - 2 * thickness, h - 2 * thickness) / 2);
    fillContour(fringe, x + thickness + innerFringe, y + thickness + innerFringe,
        w - 2 * thickness - 2 * innerFringe, h - 2 * thickness - 2 * innerFringe,
        Math.max(0, radius - thickness - innerFringe), steps);
    ring(inner, len, color, fringe, color & 0xFFFFFF);
  }

  @Override
  public void circle(float x, float y, float radius, int color) {
    roundedRect(x - radius, y - radius, radius * 2, radius * 2, radius, color);
  }

  @Override
  public void roundedRectCorners(
      float x, float y, float w, float h, float tl, float tr, float bl, float br, int color) {
    gradientRoundedRectCorners(x, y, w, h, tl, tr, bl, br, color, color, color, color);
  }

  @Override
  public void gradientRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, int ctl, int ctr, int cbl, int cbr) {
    if (w <= 0 || h <= 0 || ((ctl | ctr | cbl | cbr) >>> 24) == 0) return;
    if (tl == tr && tl == bl && tl == br
        && gpuRounded(
            x, y, w, h, Math.max(0, Math.min(tl, Math.min(w, h) / 2)), 0, ctl, ctr, cbl, cbr))
      return;
    long hash = cornerHash(x, y, w, h, tl, tr, bl, br);
    int slot = (int) (hash ^ (hash >>> 32)) & (CORNER_CACHE_SIZE - 1);
    CornerCache cache = CORNER_CACHE.get();
    float[] mesh = null;
    int free = -1;
    for (int probe = 0; probe < CORNER_CACHE_SIZE; probe++) {
      int index = (slot + probe) & (CORNER_CACHE_SIZE - 1);
      float[] params = cache.params[index];
      if (params == null) {
        if (free < 0) free = index;
        break;
      }
      if (cache.hashes[index] == hash && params[0] == x && params[1] == y && params[2] == w
          && params[3] == h && params[4] == tl && params[5] == tr && params[6] == bl
          && params[7] == br) {
        mesh = cache.meshes[index];
        break;
      }
    }
    if (mesh == null) {
      mesh = dev.nightclient.renderer.TexturedMesh.roundedRectCorners(
          x, y, w, h, tl, tr, bl, br, 0xffffffff, 0, 0, 1, 1);
      if (free < 0) free = slot;
      cache.hashes[free] = hash;
      cache.params[free] = new float[] {x, y, w, h, tl, tr, bl, br};
      cache.meshes[free] = mesh;
    }
    for (int i = 0; i < mesh.length; i += 24) {
      int a = cornerColor(ctl, ctr, cbl, cbr, mesh[i + 2], mesh[i + 3], mesh[i + 7]);
      int b = cornerColor(ctl, ctr, cbl, cbr, mesh[i + 10], mesh[i + 11], mesh[i + 15]);
      int c = cornerColor(ctl, ctr, cbl, cbr, mesh[i + 18], mesh[i + 19], mesh[i + 23]);
      triangle(mesh[i], mesh[i + 1], a, mesh[i + 8], mesh[i + 9], b, mesh[i + 16], mesh[i + 17], c);
    }
  }

  private static int cornerColor(int tl, int tr, int bl, int br, float u, float v, float alpha) {
    return gradientColor(gradientColor(tl, tr, u, 1), gradientColor(bl, br, u, 1), v, alpha);
  }

  @Override
  public void strokeRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, float thickness, int color) {
    if (w <= 0 || h <= 0 || thickness <= 0 || (color >>> 24) == 0) return;
    if (thickness * 2 >= Math.min(w, h)) {
      roundedRectCorners(x, y, w, h, tl, tr, bl, br, color);
      return;
    }
    if (tl == tr && tl == bl && tl == br
        && gpuRounded(x, y, w, h, Math.max(0, Math.min(tl, Math.min(w, h) / 2)), thickness, color,
            color, color, color))
      return;
    int steps = Math.max(
        4, Math.min(12, (int) Math.ceil(Math.max(Math.max(tl, tr), Math.max(bl, br)) * .7f)));
    float[][] scratch = contourScratch();
    float[] outer = scratch[0], inner = scratch[1], fringe = scratch[2];
    int len = RoundedContour.fill(outer, x, y, w, h, tl, tr, bl, br, steps);
    RoundedContour.fill(inner, x + thickness, y + thickness, w - 2 * thickness, h - 2 * thickness,
        tl - thickness, tr - thickness, bl - thickness, br - thickness, steps);
    ring(outer, len, color, inner, color);
    float aa = .5f / Math.max(1, rasterDensity());
    RoundedContour.fill(
        fringe, x - aa, y - aa, w + 2 * aa, h + 2 * aa, tl + aa, tr + aa, bl + aa, br + aa, steps);
    ring(fringe, len, color & 0xffffff, outer, color);
    RoundedContour.fill(fringe, x + thickness + aa, y + thickness + aa, w - 2 * thickness - 2 * aa,
        h - 2 * thickness - 2 * aa, tl - thickness - aa, tr - thickness - aa,
        bl - thickness - aa, br - thickness - aa, steps);
    ring(inner, len, color, fringe, color & 0xffffff);
  }

  @Override
  public void gradientRoundedRect(
      float x, float y, float w, float h, float radius, int left, int right) {
    if (w <= 0 || h <= 0 || ((left | right) >>> 24) == 0) return;
    if (gpuRounded(x, y, w, h, Math.max(0, Math.min(radius, Math.min(w, h) / 2)), 0, left, right,
            left, right))
      return;
    float[] mesh = dev.nightclient.renderer.TexturedMesh.roundedRect(
        x, y, w, h, radius, 0xffffffff, 0, 0, 1, 1);
    for (int i = 0; i < mesh.length; i += 24) {
      int a = gradientColor(left, right, mesh[i + 2], mesh[i + 7]);
      int b = gradientColor(left, right, mesh[i + 10], mesh[i + 15]);
      int c = gradientColor(left, right, mesh[i + 18], mesh[i + 23]);
      triangle(mesh[i], mesh[i + 1], a, mesh[i + 8], mesh[i + 9], b, mesh[i + 16], mesh[i + 17], c);
    }
  }

  private static int gradientColor(int left, int right, float factor, float alpha) {
    factor = Math.max(0, Math.min(1, factor));
    int color = 0;
    for (int shift = 0; shift < 24; shift += 8)
      color |=
          Math.round(((left >>> shift) & 255) * (1 - factor) + ((right >>> shift) & 255) * factor)
          << shift;
    return color
        | Math.round(
              (((left >>> 24) & 255) * (1 - factor) + ((right >>> 24) & 255) * factor) * alpha)
        << 24;
  }

  @Override
  public void line(float ax, float ay, float bx, float by, float thickness, int color) {
    float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
    if (len == 0 || thickness <= 0) return;
    if ((color >>> 24) == 0) return;
    float nx = -dy / len, ny = dx / len, half = thickness / 2,
          fringe = .65f / Math.max(1, rasterDensity());
    float ox = nx * half, oy = ny * half, fx = nx * (half + fringe), fy = ny * (half + fringe);
    int transparent = color & 0xffffff;
    quad(ax + ox, ay + oy, color, bx + ox, by + oy, color, bx - ox, by - oy, color, ax - ox,
        ay - oy, color);
    quad(ax + fx, ay + fy, transparent, bx + fx, by + fy, transparent, bx + ox, by + oy, color,
        ax + ox, ay + oy, color);
    quad(ax - ox, ay - oy, color, bx - ox, by - oy, color, bx - fx, by - fy, transparent, ax - fx,
        ay - fy, transparent);
    float tx = dx / len * fringe, ty = dy / len * fringe;
    quad(ax + fx - tx, ay + fy - ty, transparent, ax + ox, ay + oy, color, ax - ox, ay - oy, color,
        ax - fx - tx, ay - fy - ty, transparent);
    quad(bx + ox, by + oy, color, bx + fx + tx, by + fy + ty, transparent, bx - fx + tx,
        by - fy + ty, transparent, bx - ox, by - oy, color);
  }

  protected boolean hasNativeTextFallback() {
    return false;
  }

  private static float fallbackGameScale(float size) {
    return size * 0.12f;
  }

  private static float fallbackBaselineOffset(FontAtlas atlas, float size) {
    return (atlas.baselineRatio() - 0.84f) * size;
  }

  private static int nextClusterEnd(String value, int start) {
    int len = value.length();
    int cp = value.codePointAt(start);
    int end = start + Character.charCount(cp);
    while (end < len) {
      int next = value.codePointAt(end);
      if (FontAtlas.isTrailingClusterCodePoint(next)) {
        end += Character.charCount(next);
      } else if (next == 0x200D) {
        end += 1;
        if (end < len) {
          int joined = value.codePointAt(end);
          end += Character.charCount(joined);
        }
      } else {
        break;
      }
    }
    return end;
  }

  private boolean clusterSupportedByAtlas(FontAtlas atlas, String value, int start, int end, boolean bold) {
    int cp = value.codePointAt(start);
    if (start + Character.charCount(cp) != end) {
      return false;
    }
    return atlas.hasGlyph(cp, bold);
  }

  @Override
  public void text(String value, float x, float y, float size, final int color, boolean bold) {
    textTracked(value, x, y, size, color, bold, 0);
  }

  @Override
  public void textTracked(
      String value, float x, float y, float size, final int color, boolean bold, float tracking) {
    textFamily("onest", value, x, y, size, color, bold, tracking);
  }

  @Override
  public void textFamily(String family, String value, float x, float y, float size, final int color,
      boolean bold, float tracking) {
    if (value == null || value.isEmpty() || size <= 0 || (color >>> 24) == 0 || !clipVisible())
      return;
    FontAtlas atlas = FontAtlas.get(family);
    if (!hasNativeTextFallback() || atlas.supportsAll(value, bold)) {
      flushGeometry();
      beginText(atlas);
      try {
        atlas.visitGlyphs(value, x, y, size, bold, tracking, rasterDensity(),
            (gx, gy, gw, gh, px, py, pw, ph) -> glyph(gx, gy, gw, gh, px, py, pw, ph, color));
      } finally {
        endText();
      }
      return;
    }
    drawMixedText(atlas, value, x, y, size, color, bold, tracking);
  }

  private void drawMixedText(FontAtlas atlas, String value, float x, float y, float size,
      final int color, boolean bold, float tracking) {
    float gameScale = fallbackGameScale(size);
    float yOff = fallbackBaselineOffset(atlas, size);
    float lineStep = size * 1.25f;
    float penX = x, penY = y;
    boolean hasPrev = false;
    int len = value.length();
    int i = 0;
    while (i < len) {
      char ch = value.charAt(i);
      if (ch == '\n') {
        penX = x;
        penY += lineStep;
        hasPrev = false;
        i++;
        continue;
      }
      int firstEnd = nextClusterEnd(value, i);
      int firstCp = value.codePointAt(i);
      if (firstEnd == i + Character.charCount(firstCp) && FontAtlas.isIgnorableFormatChar(firstCp)) {
        i = firstEnd;
        continue;
      }
      boolean useAtlas = clusterSupportedByAtlas(atlas, value, i, firstEnd, bold);
      int runStart = i;
      int runEnd = firstEnd;
      while (runEnd < len) {
        char nextCh = value.charAt(runEnd);
        if (nextCh == '\n') break;
        int clusterEnd = nextClusterEnd(value, runEnd);
        int cp = value.codePointAt(runEnd);
        if (clusterEnd == runEnd + Character.charCount(cp) && FontAtlas.isIgnorableFormatChar(cp)) {
          break;
        }
        if (clusterSupportedByAtlas(atlas, value, runEnd, clusterEnd, bold) != useAtlas) break;
        runEnd = clusterEnd;
      }
      String run = value.substring(runStart, runEnd);
      if (hasPrev) penX += tracking;
      if (useAtlas) {
        flushGeometry();
        beginText(atlas);
        try {
          atlas.visitGlyphs(run, penX, penY, size, bold, tracking, rasterDensity(),
              (gx, gy, gw, gh, px, py, pw, ph) -> glyph(gx, gy, gw, gh, px, py, pw, ph, color));
        } finally {
          endText();
        }
        penX += atlas.width(run, size, bold, tracking);
      } else {
        if ((color >>> 24) >= 4) {
          gameText(run, penX, penY + yOff, gameScale, color, false);
        }
        penX += gameTextWidth(run, gameScale);
      }
      hasPrev = true;
      i = runEnd;
    }
  }

  private float measureMixedText(
      FontAtlas atlas, String value, float size, boolean bold, float tracking) {
    float gameScale = fallbackGameScale(size);
    float current = 0, max = 0;
    boolean hasPrev = false;
    int len = value.length();
    int i = 0;
    while (i < len) {
      char ch = value.charAt(i);
      if (ch == '\n') {
        max = Math.max(max, current);
        current = 0;
        hasPrev = false;
        i++;
        continue;
      }
      int firstEnd = nextClusterEnd(value, i);
      int firstCp = value.codePointAt(i);
      if (firstEnd == i + Character.charCount(firstCp) && FontAtlas.isIgnorableFormatChar(firstCp)) {
        i = firstEnd;
        continue;
      }
      boolean useAtlas = clusterSupportedByAtlas(atlas, value, i, firstEnd, bold);
      int runStart = i;
      int runEnd = firstEnd;
      while (runEnd < len) {
        char nextCh = value.charAt(runEnd);
        if (nextCh == '\n') break;
        int clusterEnd = nextClusterEnd(value, runEnd);
        int cp = value.codePointAt(runEnd);
        if (clusterEnd == runEnd + Character.charCount(cp) && FontAtlas.isIgnorableFormatChar(cp)) {
          break;
        }
        if (clusterSupportedByAtlas(atlas, value, runEnd, clusterEnd, bold) != useAtlas) break;
        runEnd = clusterEnd;
      }
      String run = value.substring(runStart, runEnd);
      if (hasPrev) current += tracking;
      current += useAtlas
          ? atlas.width(run, size, bold, tracking)
          : gameTextWidth(run, gameScale);
      hasPrev = true;
      i = runEnd;
    }
    return Math.max(max, current);
  }

  @Override
  public float textWidth(String value, float size, boolean bold) {
    return textWidthFamily("onest", value, size, bold, 0);
  }

  @Override
  public float textWidthTracked(String value, float size, boolean bold, float tracking) {
    return textWidthFamily("onest", value, size, bold, tracking);
  }

  @Override
  public float textWidthFamily(
      String family, String value, float size, boolean bold, float tracking) {
    if (value == null || value.isEmpty() || size <= 0) return 0;
    FontAtlas atlas = FontAtlas.get(family);
    if (!hasNativeTextFallback() || atlas.supportsAll(value, bold)) {
      return atlas.width(value, size, bold, tracking);
    }
    return measureMixedText(atlas, value, size, bold, tracking);
  }

  @Override
  public void pushClip(float x, float y, float w, float h) {
    flush();
    float[] parent = clip();
    float left = Math.min(width, Math.max(0, x)), top = Math.min(height, Math.max(0, y)),
          right = Math.max(0, Math.min(width, x + Math.max(0, w))),
          bottom = Math.max(0, Math.min(height, y + Math.max(0, h)));
    if (parent != null) {
      left = Math.max(left, parent[0]);
      top = Math.max(top, parent[1]);
      right = Math.min(right, parent[2]);
      bottom = Math.min(bottom, parent[3]);
    }
    if (clipDepth == clips.length) clips = Arrays.copyOf(clips, Math.max(4, clips.length * 2));
    float[] slot = clips[clipDepth];
    if (slot == null) clips[clipDepth] = slot = new float[4];
    slot[0] = left;
    slot[1] = top;
    slot[2] = Math.max(left, right);
    slot[3] = Math.max(top, bottom);
    clipDepth++;
  }

  @Override
  public void popClip() {
    flush();
    if (clipDepth == 0) throw new IllegalStateException("Unbalanced canvas clip");
    clipDepth--;
  }

  @Override
  public void item(String id, float x, float y, float size, float alpha) {
    if (id != null && size > 0 && alpha > 0 && clipVisible()) {
      flush();
      drawItem(id, x, y, size, alpha);
    }
  }

  @Override
  public final void backdrop(float x, float y, float w, float h, float radius, float blurRadius,
      int quality, float opacity) {
    if (w > 0 && h > 0 && blurRadius > 0 && opacity > 0 && clipVisible()) {
      flush();
      drawBackdrop(x, y, w, h, radius, blurRadius, quality, Math.min(1, opacity), clip());
    }
  }

  @Override
  public final void backdropCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, float blurRadius, int quality, float opacity) {
    if (w > 0 && h > 0 && blurRadius > 0 && opacity > 0 && clipVisible()) {
      flush();
      drawBackdropCorners(
          x, y, w, h, tl, tr, bl, br, blurRadius, quality, Math.min(1, opacity), clip());
    }
  }

  @Override
  public final void invertRect(float x, float y, float w, float h, float opacity) {
    if (w > 0 && h > 0 && Float.isFinite(opacity) && opacity > 0 && clipVisible()) {
      float value = Math.round(Math.min(1, opacity) * 255) / 255f;
      if (value > 0) {
        flush();
        drawInvertedRect(x, y, w, h, value, clip());
      }
    }
  }

  @Override
  public final void texture(
      String id, float x, float y, float w, float h, float radius, float tileSize, int argb) {
    if (id != null && w > 0 && h > 0 && Float.isFinite(tileSize) && tileSize >= 0
        && (argb >>> 24) != 0 && clipVisible()) {
      flush();
      drawTexture(id, x, y, w, h, radius, tileSize, argb, clip());
    }
  }

  @Override
  public final void textureCorners(String id, float x, float y, float w, float h, float tl,
      float tr, float bl, float br, float tileSize, int argb) {
    if (id != null && w > 0 && h > 0 && Float.isFinite(tileSize) && tileSize >= 0
        && (argb >>> 24) != 0 && clipVisible()) {
      flush();
      drawTextureCorners(id, x, y, w, h, tl, tr, bl, br, tileSize, argb, clip());
    }
  }
}
