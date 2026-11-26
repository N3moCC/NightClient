package dev.nightclient.renderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class IconRasterCache {
  private static final int MAX_ENTRIES = 2048;
  private static final int MIN_INNER_PX = 12;
  private static final int MAX_INNER_PX = 128;
  private static final float SQRT2_HALF = 0.70710678f;

  public static final class RasterIcon {
    public final String cacheKey;
    public final long revision;
    public final int[] pixels;
    public final int width;
    public final int height;
    public final float padViewBox;

    RasterIcon(String cacheKey, long revision, int[] pixels, int width, int height,
        float padViewBox) {
      this.cacheKey = cacheKey;
      this.revision = revision;
      this.pixels = pixels;
      this.width = width;
      this.height = height;
      this.padViewBox = padViewBox;
    }
  }

  private static final ConcurrentHashMap<String, RasterIcon> CACHE =
      new ConcurrentHashMap<String, RasterIcon>(512);
  private static final AtomicInteger PREWARMED_DENSITY_Q = new AtomicInteger(-1);

  private IconRasterCache() {}

  public static void clear() {
    CACHE.clear();
    PREWARMED_DENSITY_Q.set(-1);
  }

  public static int cachedCount() {
    return CACHE.size();
  }

  static void prewarmAsync(final Map<String, Icons.Shape[]> library, final float density) {
    if (library == null || library.isEmpty()) return;
    final float safeDensity = Float.isFinite(density) ? Math.max(1.0f, density) : 1.0f;
    final int densityQ = Math.round(safeDensity * 10.0f);
    if (PREWARMED_DENSITY_Q.getAndSet(densityQ) == densityQ) return;
    Thread worker = new Thread(new Runnable() {
      @Override
      public void run() {
        final float[][] presets = {
            {12.0f, 1.5f},
            {12.0f, 1.55f},
            {10.0f, 1.55f},
            {9.0f, 1.55f},
        };
        for (float[] preset : presets) {
          float size = preset[0];
          float stroke = preset[1];
          for (Map.Entry<String, Icons.Shape[]> entry : library.entrySet()) {
            if (PREWARMED_DENSITY_Q.get() != densityQ) return;
            try {
              get(entry.getKey(), entry.getValue(), size, safeDensity, stroke);
            } catch (Throwable ignored) {
            }
          }
        }
      }
    }, "NightClient-IconPrewarm");
    worker.setDaemon(true);
    worker.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
    worker.start();
  }

  static int quantizeInnerPx(float size, float density) {
    float raw = size * Math.max(1.0f, density);
    if (!Float.isFinite(raw) || raw <= MIN_INNER_PX) return MIN_INNER_PX;
    if (raw <= 40.0f) return Math.round(raw);
    if (raw <= 80.0f) return Math.round(raw * 0.5f) * 2;
    return Math.min(MAX_INNER_PX, Math.round(raw * 0.25f) * 4);
  }

  static int quantizeStroke(float strokeWidth) {
    float clamped = Math.max(0.5f, Math.min(6.0f, Float.isFinite(strokeWidth) ? strokeWidth : 1.5f));
    return Math.round(clamped * 20.0f);
  }

  static RasterIcon get(String id, Icons.Shape[] shapes, float size, float density,
      float strokeWidth) {
    int innerPx = quantizeInnerPx(size, density);
    int strokeQ = quantizeStroke(strokeWidth);
    String key = "icon:" + id + ":" + innerPx + ":" + strokeQ;
    RasterIcon cached = CACHE.get(key);
    if (cached != null) return cached;
    RasterIcon built = rasterize(key, shapes, innerPx, strokeQ / 20.0f);
    if (CACHE.size() >= MAX_ENTRIES) CACHE.clear();
    RasterIcon existing = CACHE.putIfAbsent(key, built);
    return existing != null ? existing : built;
  }

  private static RasterIcon rasterize(
      String key, Icons.Shape[] shapes, int innerPx, float strokeWidth) {
    float halfStroke = Math.max(0.5f, strokeWidth) * 0.5f;
    float minPadVb = Math.max(1.5f, halfStroke + 0.75f);
    int padPx = Math.max(2, (int) Math.ceil(minPadVb * innerPx / 24.0f));
    int texSize = innerPx + padPx * 2;
    float padViewBox = (padPx * 24.0f) / (float) innerPx;
    float vbStep = 24.0f / (float) innerPx;
    float halfDiag = SQRT2_HALF * vbStep + 1e-4f;

    int shapeCount = shapes.length;
    float[] minX = new float[shapeCount];
    float[] minY = new float[shapeCount];
    float[] maxX = new float[shapeCount];
    float[] maxY = new float[shapeCount];
    float iconMinX = Float.POSITIVE_INFINITY;
    float iconMinY = Float.POSITIVE_INFINITY;
    float iconMaxX = Float.NEGATIVE_INFINITY;
    float iconMaxY = Float.NEGATIVE_INFINITY;
    for (int s = 0; s < shapeCount; s++) {
      computeBounds(shapes[s], halfStroke, s, minX, minY, maxX, maxY);
      if (minX[s] < iconMinX) iconMinX = minX[s];
      if (minY[s] < iconMinY) iconMinY = minY[s];
      if (maxX[s] > iconMaxX) iconMaxX = maxX[s];
      if (maxY[s] > iconMaxY) iconMaxY = maxY[s];
    }

    int samplesAxis = texSize <= 48 ? 8 : 4;
    int totalSamples = samplesAxis * samplesAxis;
    float subStep = vbStep / (float) samplesAxis;
    float invTotal = 255.0f / (float) totalSamples;

    int[] pixels = new int[texSize * texSize];
    java.util.Arrays.fill(pixels, 0x00FFFFFF);

    int yStart = Math.max(1, (int) Math.floor((iconMinY + padViewBox) / vbStep) - 1);
    int yEnd = Math.min(texSize - 2, (int) Math.ceil((iconMaxY + padViewBox) / vbStep) + 1);
    int xStart = Math.max(1, (int) Math.floor((iconMinX + padViewBox) / vbStep) - 1);
    int xEnd = Math.min(texSize - 2, (int) Math.ceil((iconMaxX + padViewBox) / vbStep) + 1);

    for (int iy = yStart; iy <= yEnd; iy++) {
      float vbY0 = iy * vbStep - padViewBox;
      float cy = vbY0 + vbStep * 0.5f;
      int row = iy * texSize;
      for (int ix = xStart; ix <= xEnd; ix++) {
        float vbX0 = ix * vbStep - padViewBox;
        float cx = vbX0 + vbStep * 0.5f;

        float centerSd = signedDistance(shapes, minX, minY, maxX, maxY, cx, cy, halfStroke);
        if (centerSd > halfDiag) {
          continue;
        }
        if (centerSd < -halfDiag) {
          pixels[row + ix] = 0xFFFFFFFF;
          continue;
        }

        int hits = 0;
        for (int sy = 0; sy < samplesAxis; sy++) {
          float py = vbY0 + (sy + 0.5f) * subStep;
          for (int sx = 0; sx < samplesAxis; sx++) {
            float px = vbX0 + (sx + 0.5f) * subStep;
            if (isInside(shapes, minX, minY, maxX, maxY, px, py, halfStroke)) {
              hits++;
            }
          }
        }
        if (hits > 0) {
          int alpha = Math.min(255, Math.round(hits * invTotal));
          pixels[row + ix] = (alpha << 24) | 0x00FFFFFF;
        }
      }
    }
    return new RasterIcon(key, 1L, pixels, texSize, texSize, padViewBox);
  }

  private static void computeBounds(Icons.Shape shape, float halfStroke, int idx,
      float[] minX, float[] minY, float[] maxX, float[] maxY) {
    float[] p = shape.points;
    float x0 = Float.POSITIVE_INFINITY;
    float y0 = Float.POSITIVE_INFINITY;
    float x1 = Float.NEGATIVE_INFINITY;
    float y1 = Float.NEGATIVE_INFINITY;
    switch (shape.kind) {
      case 0:
        for (int i = 0; i + 1 < p.length; i += 2) {
          if (p[i] < x0) x0 = p[i];
          if (p[i] > x1) x1 = p[i];
          if (p[i + 1] < y0) y0 = p[i + 1];
          if (p[i + 1] > y1) y1 = p[i + 1];
        }
        x0 -= halfStroke;
        y0 -= halfStroke;
        x1 += halfStroke;
        y1 += halfStroke;
        break;
      case 1:
        x0 = p[0] - p[2] - halfStroke;
        y0 = p[1] - p[2] - halfStroke;
        x1 = p[0] + p[2] + halfStroke;
        y1 = p[1] + p[2] + halfStroke;
        break;
      case 2:
        x0 = p[0] - halfStroke;
        y0 = p[1] - halfStroke;
        x1 = p[0] + p[2] + halfStroke;
        y1 = p[1] + p[3] + halfStroke;
        break;
      case 3:
        for (int i = 0; i + 1 < p.length; i += 2) {
          if (p[i] < x0) x0 = p[i];
          if (p[i] > x1) x1 = p[i];
          if (p[i + 1] < y0) y0 = p[i + 1];
          if (p[i + 1] > y1) y1 = p[i + 1];
        }
        break;
      case 4:
        x0 = p[0] - p[2];
        y0 = p[1] - p[2];
        x1 = p[0] + p[2];
        y1 = p[1] + p[2];
        break;
      default:
        x0 = p[0];
        y0 = p[1];
        x1 = p[0] + p[2];
        y1 = p[1] + p[3];
        break;
    }
    minX[idx] = x0;
    minY[idx] = y0;
    maxX[idx] = x1;
    maxY[idx] = y1;
  }

  private static boolean isInside(Icons.Shape[] shapes, float[] minX, float[] minY,
      float[] maxX, float[] maxY, float px, float py, float halfStroke) {
    float halfStrokeSq = halfStroke * halfStroke;
    for (int s = 0; s < shapes.length; s++) {
      if (px < minX[s] || px > maxX[s] || py < minY[s] || py > maxY[s]) continue;
      Icons.Shape shape = shapes[s];
      float[] p = shape.points;
      switch (shape.kind) {
        case 0:
          for (int i = 2; i < p.length; i += 2) {
            if (distToSegmentSq(px, py, p[i - 2], p[i - 1], p[i], p[i + 1]) <= halfStrokeSq) {
              return true;
            }
          }
          break;
        case 1: {
          float dx = px - p[0];
          float dy = py - p[1];
          float r = (float) Math.sqrt(dx * dx + dy * dy);
          if (Math.abs(r - p[2]) <= halfStroke) return true;
          break;
        }
        case 2:
          if (Math.abs(roundedRectSd(px, py, p[0], p[1], p[2], p[3], p[4])) <= halfStroke) {
            return true;
          }
          break;
        case 3:
          for (int i = 0; i + 5 < p.length; i += 6) {
            if (pointInTriangle(px, py, p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5])) {
              return true;
            }
          }
          break;
        case 4: {
          float dx = px - p[0];
          float dy = py - p[1];
          if (dx * dx + dy * dy <= p[2] * p[2]) return true;
          break;
        }
        default:
          if (roundedRectSd(px, py, p[0], p[1], p[2], p[3], p[4]) <= 0.0f) return true;
          break;
      }
    }
    return false;
  }

  private static float signedDistance(Icons.Shape[] shapes, float[] minX, float[] minY,
      float[] maxX, float[] maxY, float px, float py, float halfStroke) {
    float best = Float.POSITIVE_INFINITY;
    for (int s = 0; s < shapes.length; s++) {
      float bx = px < minX[s] ? minX[s] - px : (px > maxX[s] ? px - maxX[s] : 0.0f);
      float by = py < minY[s] ? minY[s] - py : (py > maxY[s] ? py - maxY[s] : 0.0f);
      if (bx > 0.0f || by > 0.0f) {
        float boxDist = (float) Math.sqrt(bx * bx + by * by);
        if (boxDist >= best) continue;
      }
      Icons.Shape shape = shapes[s];
      float[] p = shape.points;
      float sd;
      switch (shape.kind) {
        case 0: {
          float minSegSq = Float.POSITIVE_INFINITY;
          for (int i = 2; i < p.length; i += 2) {
            float dSq = distToSegmentSq(px, py, p[i - 2], p[i - 1], p[i], p[i + 1]);
            if (dSq < minSegSq) minSegSq = dSq;
          }
          sd = (float) Math.sqrt(minSegSq) - halfStroke;
          break;
        }
        case 1: {
          float dx = px - p[0];
          float dy = py - p[1];
          sd = Math.abs((float) Math.sqrt(dx * dx + dy * dy) - p[2]) - halfStroke;
          break;
        }
        case 2:
          sd = Math.abs(roundedRectSd(px, py, p[0], p[1], p[2], p[3], p[4])) - halfStroke;
          break;
        case 3: {
          float triBest = Float.POSITIVE_INFINITY;
          for (int i = 0; i + 5 < p.length; i += 6) {
            float d = triangleSd(px, py, p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5]);
            if (d < triBest) triBest = d;
          }
          sd = triBest;
          break;
        }
        case 4: {
          float dx = px - p[0];
          float dy = py - p[1];
          sd = (float) Math.sqrt(dx * dx + dy * dy) - p[2];
          break;
        }
        default:
          sd = roundedRectSd(px, py, p[0], p[1], p[2], p[3], p[4]);
          break;
      }
      if (sd < best) best = sd;
    }
    return best;
  }

  private static float distToSegmentSq(
      float px, float py, float x0, float y0, float x1, float y1) {
    float vx = x1 - x0;
    float vy = y1 - y0;
    float wx = px - x0;
    float wy = py - y0;
    float lenSq = vx * vx + vy * vy;
    if (lenSq <= 1e-8f) {
      return wx * wx + wy * wy;
    }
    float t = (wx * vx + wy * vy) / lenSq;
    if (t < 0.0f) t = 0.0f;
    else if (t > 1.0f) t = 1.0f;
    float dx = wx - t * vx;
    float dy = wy - t * vy;
    return dx * dx + dy * dy;
  }

  private static float roundedRectSd(
      float px, float py, float rx, float ry, float rw, float rh, float rad) {
    float hx = rw * 0.5f;
    float hy = rh * 0.5f;
    float r = Math.max(0.0f, Math.min(rad, Math.min(hx, hy)));
    float qx = Math.abs(px - (rx + hx)) - (hx - r);
    float qy = Math.abs(py - (ry + hy)) - (hy - r);
    float ox = Math.max(qx, 0.0f);
    float oy = Math.max(qy, 0.0f);
    float out = (float) Math.sqrt(ox * ox + oy * oy);
    float in = Math.min(Math.max(qx, qy), 0.0f);
    return out + in - r;
  }

  private static boolean pointInTriangle(
      float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
    float area2 = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    if (Math.abs(area2) < 1e-7f) return false;
    float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
    float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
    float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
    boolean hasNeg = (d1 < 0.0f) || (d2 < 0.0f) || (d3 < 0.0f);
    boolean hasPos = (d1 > 0.0f) || (d2 > 0.0f) || (d3 > 0.0f);
    return !(hasNeg && hasPos);
  }

  private static float triangleSd(
      float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
    float area2 = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    if (Math.abs(area2) < 1e-7f) return Float.POSITIVE_INFINITY;
    float d0 = distToSegmentSq(px, py, ax, ay, bx, by);
    float d1 = distToSegmentSq(px, py, bx, by, cx, cy);
    float d2 = distToSegmentSq(px, py, cx, cy, ax, ay);
    float dist = (float) Math.sqrt(Math.min(d0, Math.min(d1, d2)));
    return pointInTriangle(px, py, ax, ay, bx, by, cx, cy) ? -dist : dist;
  }
}
