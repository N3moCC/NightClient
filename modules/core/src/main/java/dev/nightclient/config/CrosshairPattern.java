package dev.nightclient.config;

import java.util.Arrays;

public final class CrosshairPattern {
  public static final int MAX_SIZE = 31;
  public static final String[] PRESETS = {
      "vanilla", "dot", "cross", "x", "ring", "diamond", "brackets", "arrows"};
  public int size = 15;
  public int[] pixels = vanillaPixels();

  public CrosshairPattern() {}

  public static CrosshairPattern defaultTargetPattern() {
    CrosshairPattern p = new CrosshairPattern(15);
    p.preset("x");
    return p;
  }

  public CrosshairPattern(int size) {
    this.size = boundedSize(size);
    this.pixels = new int[this.size * this.size];
  }

  public void validate() {
    int bounded = boundedSize(size);
    if (pixels == null) {
      size = 15;
      pixels = vanillaPixels();
      return;
    }
    if (bounded != size || pixels.length != bounded * bounded) {
      int[] valid = new int[bounded * bounded];

      System.arraycopy(pixels, 0, valid, 0, Math.min(pixels.length, valid.length));
      size = bounded;
      pixels = valid;
    }
    for (int i = 0; i < pixels.length; i++)
      if ((pixels[i] >>> 24) == 0) pixels[i] = 0;
  }

  public int pixel(int x, int y) {
    return x < 0 || y < 0 || x >= size || y >= size || pixels == null
            || y * size + x >= pixels.length
        ? 0
        : pixels[y * size + x];
  }

  public boolean paint(int x, int y, int argb, boolean mirrorX, boolean mirrorY) {
    if (size < 3 || size > MAX_SIZE || (size & 1) == 0 || pixels == null
        || pixels.length != size * size)
      validate();
    if (x < 0 || y < 0 || x >= size || y >= size) return false;
    if ((argb >>> 24) == 0) argb = 0;
    boolean changed = put(x, y, argb);
    if (mirrorX) changed |= put(size - 1 - x, y, argb);
    if (mirrorY) changed |= put(x, size - 1 - y, argb);
    if (mirrorX && mirrorY) changed |= put(size - 1 - x, size - 1 - y, argb);
    return changed;
  }

  public boolean stroke(
      int x0, int y0, int x1, int y1, int argb, boolean mirrorX, boolean mirrorY) {
    if (size < 3 || size > MAX_SIZE || (size & 1) == 0 || pixels == null
        || pixels.length != size * size)
      validate();
    x0 = clamp(x0, 0, size - 1);
    y0 = clamp(y0, 0, size - 1);
    x1 = clamp(x1, 0, size - 1);
    y1 = clamp(y1, 0, size - 1);
    int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
    int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, error = dx + dy;
    boolean changed = false;
    while (true) {
      changed |= paint(x0, y0, argb, mirrorX, mirrorY);
      if (x0 == x1 && y0 == y1) return changed;
      int twice = 2 * error;
      if (twice >= dy) {
        error += dy;
        x0 += sx;
      }
      if (twice <= dx) {
        error += dx;
        y0 += sy;
      }
    }
  }

  public void clear() {
    validate();
    Arrays.fill(pixels, 0);
  }

  public void resize(int newSize) {
    validate();
    newSize = boundedSize(newSize);
    if (newSize == size) return;
    int[] resized = new int[newSize * newSize];
    int offset = (newSize - size) / 2;
    for (int y = 0; y < size; y++)
      for (int x = 0; x < size; x++) {
        int nx = x + offset, ny = y + offset;
        if (nx >= 0 && ny >= 0 && nx < newSize && ny < newSize)
          resized[ny * newSize + nx] = pixels[y * size + x];
      }
    size = newSize;
    pixels = resized;
  }

  public void preset(String name) {
    clear();
    int center = size / 2, reach = Math.min(4, center);
    if ("dot".equals(name)) {
      put(center, center, 0xffffffff);
      return;
    }
    if ("x".equals(name)) {
      int diag = Math.min(Math.max(4, size / 3), center);
      int gap = size >= 21 ? 2 : 1;
      for (int n = gap + 1; n <= diag; n++) {
        put(center - n, center - n, 0xffffffff);
        put(center + n, center - n, 0xffffffff);
        put(center - n, center + n, 0xffffffff);
        put(center + n, center + n, 0xffffffff);
      }
      put(center, center, 0xffffffff);
      return;
    }
    if ("brackets".equals(name)) {
      int r = Math.min(Math.max(4, size / 3), center);
      int arm = Math.max(2, r / 2);
      for (int i = 0; i <= arm; i++) {
        put(center - r + i, center - r, 0xffffffff);
        put(center - r, center - r + i, 0xffffffff);
        put(center + r - i, center - r, 0xffffffff);
        put(center + r, center - r + i, 0xffffffff);
        put(center - r + i, center + r, 0xffffffff);
        put(center - r, center + r - i, 0xffffffff);
        put(center + r - i, center + r, 0xffffffff);
        put(center + r, center + r - i, 0xffffffff);
      }
      put(center, center, 0xffffffff);
      return;
    }
    if ("arrows".equals(name)) {
      int r = Math.min(Math.max(4, size / 3), center);
      for (int i = 0; i <= 2; i++) {
        put(center - r + i, center - i, 0xffffffff);
        put(center - r + i, center + i, 0xffffffff);
        put(center + r - i, center - i, 0xffffffff);
        put(center + r - i, center + i, 0xffffffff);
        put(center - i, center - r + i, 0xffffffff);
        put(center + i, center - r + i, 0xffffffff);
        put(center - i, center + r - i, 0xffffffff);
        put(center + i, center + r - i, 0xffffffff);
      }
      put(center, center, 0xffffffff);
      return;
    }
    if ("ring".equals(name) || "diamond".equals(name)) {
      for (int y = -reach; y <= reach; y++)
        for (int x = -reach; x <= reach; x++) {
          int distance = x * x + y * y;
          boolean filled = "diamond".equals(name) ? Math.abs(x) + Math.abs(y) == reach
                                                    : distance >= (reach - .65f) * (reach - .65f)
                  && distance <= (reach + .65f) * (reach + .65f);
          if (filled) put(center + x, center + y, 0xffffffff);
        }
      return;
    }
    for (int n = -reach; n <= reach; n++) {
      if ("cross".equals(name) && Math.abs(n) <= 1) continue;
      put(center + n, center, 0xffffffff);
      put(center, center + n, 0xffffffff);
    }
  }

  public boolean isVanilla() {
    if (size != 15 || pixels == null || pixels.length != 225) return false;
    for (int y = 0; y < 15; y++)
      for (int x = 0; x < 15; x++) {
        int expected = (y == 7 && x >= 3 && x <= 11 || x == 7 && y >= 3 && y <= 11) ? -1 : 0;
        if (pixels[y * 15 + x] != expected) return false;
      }
    return true;
  }

  public CrosshairPattern copy() {
    CrosshairPattern copy = new CrosshairPattern(size);
    if (pixels != null)
      System.arraycopy(pixels, 0, copy.pixels, 0, Math.min(pixels.length, copy.pixels.length));
    return copy;
  }

  private boolean put(int x, int y, int argb) {
    if (x < 0 || y < 0 || x >= size || y >= size || pixels == null) return false;
    int index = y * size + x;
    if (index < 0 || index >= pixels.length || pixels[index] == argb) return false;
    pixels[index] = argb;
    return true;
  }

  private static int boundedSize(int value) {
    return clamp(value, 3, MAX_SIZE) | 1;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static int[] vanillaPixels() {
    int[] result = new int[15 * 15];
    for (int n = 3; n <= 11; n++) {
      result[7 * 15 + n] = 0xffffffff;
      result[n * 15 + 7] = 0xffffffff;
    }
    return result;
  }
}
