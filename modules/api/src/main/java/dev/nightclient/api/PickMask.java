package dev.nightclient.api;

import java.nio.ByteBuffer;

public final class PickMask {
  private final long[] pixels;
  public final int width, height, logicalWidth, logicalHeight, pixelCount;
  public final float x, y, boundsWidth, boundsHeight, centerX, centerY;

  public PickMask(int width, int height, int logicalWidth, int logicalHeight, long[] occupied) {
    if (width < 1 || height < 1 || width > 16384 || height > 16384
        || (long) width * height > 16777216 || logicalWidth < 1 || logicalHeight < 1
        || occupied == null || occupied.length != ((long) width * height + 63) / 64)
      throw new IllegalArgumentException("Invalid physical silhouette dimensions");
    this.width = width;
    this.height = height;
    this.logicalWidth = logicalWidth;
    this.logicalHeight = logicalHeight;
    this.pixels = occupied.clone();
    int left = width, top = height, right = -1, bottom = -1, count = 0;
    long sx = 0, sy = 0;
    for (int py = 0; py < height; py++)
      for (int px = 0; px < width; px++) {
        int index = px + py * width;
        if ((pixels[index >>> 6] & (1L << (index & 63))) != 0) {
          left = Math.min(left, px);
          right = Math.max(right, px);
          top = Math.min(top, py);
          bottom = Math.max(bottom, py);
          count++;
          sx += px;
          sy += py;
        }
      }
    this.pixelCount = count;
    float scaleX = logicalWidth / (float) width, scaleY = logicalHeight / (float) height;
    this.x = count == 0 ? 0 : left * scaleX;
    this.y = count == 0 ? 0 : top * scaleY;
    this.boundsWidth = count == 0 ? 0 : (right - left + 1) * scaleX;
    this.boundsHeight = count == 0 ? 0 : (bottom - top + 1) * scaleY;
    this.centerX = count == 0 ? 0 : (sx / (float) count + .5f) * scaleX;
    this.centerY = count == 0 ? 0 : (sy / (float) count + .5f) * scaleY;
  }

  public static PickMask fromRgba(ByteBuffer rgba, int width, int height, int logicalWidth,
      int logicalHeight, boolean bottomUp) {
    if (rgba == null || width < 1 || height < 1 || (long) width * height * 4 > rgba.capacity())
      throw new IllegalArgumentException("RGBA image is incomplete");
    long[] occupied = new long[(int) (((long) width * height + 63) / 64)];
    for (int py = 0; py < height; py++)
      for (int px = 0; px < width; px++) {
        int source = px + (bottomUp ? height - 1 - py : py) * width;

        if ((rgba.get(source * 4 + 3) & 255) != 0) {
          int index = px + py * width;
          occupied[index >>> 6] |= 1L << (index & 63);
        }
      }
    return new PickMask(width, height, logicalWidth, logicalHeight, occupied);
  }

  public boolean contains(float logicalX, float logicalY) {
    if (!Float.isFinite(logicalX) || !Float.isFinite(logicalY) || logicalX < 0 || logicalY < 0
        || logicalX >= logicalWidth || logicalY >= logicalHeight)
      return false;
    int px = (int) (logicalX * width / logicalWidth),
        py = (int) (logicalY * height / logicalHeight), index = px + py * width;
    return (pixels[index >>> 6] & (1L << (index & 63))) != 0;
  }

  public boolean physicalPixel(int px, int py) {
    if (px < 0 || py < 0 || px >= width || py >= height) return false;
    int index = px + py * width;
    return (pixels[index >>> 6] & (1L << (index & 63))) != 0;
  }

  public boolean isEmpty() {
    return pixelCount == 0;
  }
}
