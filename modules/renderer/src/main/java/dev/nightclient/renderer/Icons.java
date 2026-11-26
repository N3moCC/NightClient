package dev.nightclient.renderer;

import dev.nightclient.api.Canvas;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Icons {
  private static final Map<String, Shape[]> LIBRARY = load();

  private Icons() {}

  public static Set<String> ids() {
    return LIBRARY.keySet();
  }

  public static void prewarmAsync(float density) {
    IconRasterCache.prewarmAsync(LIBRARY, density);
  }

  public static void draw(
      Canvas c, String id, float x, float y, float size, int color, float strokeWidth) {
    Shape[] shapes = LIBRARY.get(id);
    if (shapes == null) throw new IllegalArgumentException("Unknown NightClient SVG: " + id);
    if (size <= 0 || (color >>> 24) == 0) return;
    if (c.supportsIconAtlas()) {
      IconRasterCache.RasterIcon icon =
          IconRasterCache.get(id, shapes, size, c.iconPixelDensity(), strokeWidth);
      float padScreen = icon.padViewBox * (size / 24.0f);
      c.iconQuad(icon.cacheKey, icon.revision, icon.pixels, icon.width, icon.height,
          x - padScreen, y - padScreen, size + padScreen * 2.0f, size + padScreen * 2.0f, color);
      return;
    }
    float scale = size / 24, stroke = Math.max(.5f, strokeWidth) * scale;
    for (Shape shape : shapes) {
      float[] p = shape.points;
      if (shape.kind == 0) {
        for (int i = 2; i < p.length; i += 2)
          c.line(x + p[i - 2] * scale, y + p[i - 1] * scale, x + p[i] * scale, y + p[i + 1] * scale,
              stroke, color);
        if (!shape.closed) {
          c.circle(x + p[0] * scale, y + p[1] * scale, stroke / 2, color);
          c.circle(x + p[p.length - 2] * scale, y + p[p.length - 1] * scale, stroke / 2, color);
        }

        for (int i : shape.joins)
          c.circle(x + p[i] * scale, y + p[i + 1] * scale, stroke / 2, color);
      } else if (shape.kind == 1)
        c.strokeRoundedRect(x + (p[0] - p[2]) * scale - stroke / 2,
            y + (p[1] - p[2]) * scale - stroke / 2, p[2] * 2 * scale + stroke,
            p[2] * 2 * scale + stroke, p[2] * scale + stroke / 2, stroke, color);
      else if (shape.kind == 2)
        c.strokeRoundedRect(x + p[0] * scale - stroke / 2, y + p[1] * scale - stroke / 2,
            p[2] * scale + stroke, p[3] * scale + stroke, p[4] * scale + stroke / 2, stroke, color);
      else if (shape.kind == 3)
        for (int i = 0; i < p.length; i += 6)
          c.fillTriangle(x + p[i] * scale, y + p[i + 1] * scale, x + p[i + 2] * scale,
              y + p[i + 3] * scale, x + p[i + 4] * scale, y + p[i + 5] * scale, color);
      else if (shape.kind == 4)
        c.circle(x + p[0] * scale, y + p[1] * scale, p[2] * scale, color);
      else
        c.roundedRect(
            x + p[0] * scale, y + p[1] * scale, p[2] * scale, p[3] * scale, p[4] * scale, color);
    }
  }

  private static Map<String, Shape[]> load() {
    Map<String, Shape[]> icons = new LinkedHashMap<String, Shape[]>();
    try (InputStream resource =
             Icons.class.getResourceAsStream("/assets/nightclient/icons/compiled.bin")) {
      if (resource == null) throw new IOException("Missing compiled SVG library");
      try (DataInputStream input = new DataInputStream(resource)) {
        if (input.readInt() != 0x4E434943) throw new IOException("Invalid SVG library version");
        int version = input.readInt();
        if (version < 1 || version > 2) throw new IOException("Invalid SVG library version");
        int count = input.readInt();
        if (count < 1 || count > 256) throw new IOException("Invalid SVG library size");
        for (int i = 0; i < count; i++) {
          String id = input.readUTF();
          int n = input.readInt();
          if (n < 1 || n > 64) throw new IOException("Invalid SVG path count");
          Shape[] paths = new Shape[n];
          for (int j = 0; j < n; j++) {
            int kind = input.readUnsignedByte(), size = input.readInt();
            if (kind > (version == 1 ? 2 : 5) || size < 3 || size > 4096
                || kind == 0 && (size < 4 || size % 2 != 0) || (kind == 1 || kind == 4) && size != 3
                || (kind == 2 || kind == 5) && size != 5
                || kind == 3 && (size < 6 || size % 6 != 0))
              throw new IOException("Invalid SVG geometry");
            float[] points = new float[size];
            for (int k = 0; k < size; k++) {
              points[k] = input.readFloat();
              if (Float.isNaN(points[k]) || Float.isInfinite(points[k]))
                throw new IOException("Non-finite SVG coordinate");
            }
            paths[j] = new Shape(kind, points);
          }
          if (icons.put(id, paths) != null) throw new IOException("Duplicate SVG identifier");
        }
        if (input.read() != -1) throw new IOException("Trailing SVG data");
      }
    } catch (IOException error) {
      throw new IllegalStateException("Cannot load NightClient SVG library", error);
    }
    return Collections.unmodifiableMap(icons);
  }

  static final class Shape {
    final int kind;
    final float[] points;
    final boolean closed;
    final int[] joins;

    Shape(int kind, float[] points) {
      this.kind = kind;
      this.points = points;
      closed = kind == 0 && points[0] == points[points.length - 2]
          && points[1] == points[points.length - 1];
      if (kind != 0) {
        joins = new int[0];
        return;
      }
      int limit = closed ? points.length - 2 : points.length;
      int[] indices = new int[limit / 2];
      int count = 0;
      for (int i = closed ? 0 : 2; i < limit - (closed ? 0 : 2); i += 2) {
        int before = (i - 2 + limit) % limit, after = (i + 2) % limit;
        float ax = points[i] - points[before], ay = points[i + 1] - points[before + 1],
              bx = points[after] - points[i], by = points[after + 1] - points[i + 1];
        float length = (float) Math.sqrt((ax * ax + ay * ay) * (bx * bx + by * by));
        if (length > 0 && (ax * bx + ay * by) / length < .97f) indices[count++] = i;
      }
      joins = java.util.Arrays.copyOf(indices, count);
    }
  }
}
