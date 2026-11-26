package dev.nightclient.runtime;

import dev.nightclient.api.PlayerSkin;
import dev.nightclient.cosmetics.OfficialCapes;
import dev.nightclient.renderer.player.DefaultSkins;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class CosmeticsImages {
  public static final int MAX_DIMENSION = PlayerSkin.MAX_TEXTURE_SIZE;
  private static final int MAX_DECODE_DIMENSION = 8192;

  public static final class DecodedTexture {
    public final int[] pixels;
    public final int width;
    public final int height;
    public final boolean slim;

    public DecodedTexture(int[] pixels, int width, int height, boolean slim) {
      this.pixels = pixels;
      this.width = width;
      this.height = height;
      this.slim = slim;
    }
  }

  private CosmeticsImages() {}

  public static DecodedTexture decodeSkinFile(Path path) throws IOException {
    if (path == null || !Files.isRegularFile(path))
      throw new IOException("Skin file not found");
    try (InputStream stream = Files.newInputStream(path)) {
      return decodeSkin(readAllBytes(stream, 64 * 1024 * 1024));
    }
  }

  public static DecodedTexture decodeSkin(byte[] bytes) throws IOException {
    BufferedImage raw = readSafeImage(bytes);
    int w = raw.getWidth(), h = raw.getHeight();
    if (w < 64 || h < 32) throw new IOException("Skin too small: " + w + "x" + h);

    if (w == h * 2) {
      if (w > MAX_DIMENSION) {
        raw = scale(raw, MAX_DIMENSION, MAX_DIMENSION / 2, true);
        w = raw.getWidth();
        h = raw.getHeight();
      }
      int[] src = raw.getRGB(0, 0, w, h, null, 0, w);
      int[] out = upgradeLegacySkin(src, w, h);
      return new DecodedTexture(out, w, w, detectSlim(out, w, w));
    }

    if (w != h) {
      int target = clampPowerOrSize(Math.max(w, h));
      raw = scale(raw, target, target, w <= 128 && h <= 128);
      w = raw.getWidth();
      h = raw.getHeight();
    } else if (w > MAX_DIMENSION) {
      raw = scale(raw, MAX_DIMENSION, MAX_DIMENSION, false);
      w = MAX_DIMENSION;
      h = MAX_DIMENSION;
    }

    int[] pixels = raw.getRGB(0, 0, w, h, null, 0, w);
    return new DecodedTexture(pixels, w, h, detectSlim(pixels, w, h));
  }

  public static DecodedTexture decodeCapeFile(Path path) throws IOException {
    if (path == null || !Files.isRegularFile(path))
      throw new IOException("Cape file not found");
    try (InputStream stream = Files.newInputStream(path)) {
      return decodeCape(readAllBytes(stream, 64 * 1024 * 1024));
    }
  }

  public static DecodedTexture decodeCape(byte[] bytes) throws IOException {
    BufferedImage raw = readSafeImage(bytes);
    int w = raw.getWidth(), h = raw.getHeight();
    if (w < 8 || h < 8) throw new IOException("Cape image too small: " + w + "x" + h);

    if (w == h * 2 && w >= 64) {
      if (w > MAX_DIMENSION) {
        raw = scale(raw, MAX_DIMENSION, MAX_DIMENSION / 2, false);
        w = raw.getWidth();
        h = raw.getHeight();
      }
      int[] pixels = raw.getRGB(0, 0, w, h, null, 0, w);
      if (isMojangCapeSheet(pixels, w, h)) return new DecodedTexture(pixels, w, h, false);
    }

    return mapArtworkToCapeSheet(raw);
  }

  public static DecodedTexture decodeOfficialCape(byte[] bytes) throws IOException {
    BufferedImage raw = readSafeImage(bytes);
    int w = raw.getWidth(), h = raw.getHeight();
    if (w < 8 || h < 8) throw new IOException("Cape image too small: " + w + "x" + h);
    if (w == h * 2 && w >= 64) {
      if (w > MAX_DIMENSION) {
        raw = scale(raw, MAX_DIMENSION, MAX_DIMENSION / 2, false);
        w = raw.getWidth();
        h = raw.getHeight();
      }
      int[] pixels = raw.getRGB(0, 0, w, h, null, 0, w);
      return new DecodedTexture(pixels, w, h, false);
    }
    return decodeCape(bytes);
  }

  static boolean isMojangCapeSheet(int[] pixels, int w, int h) {
    int frontX = (w * 2) / 64, frontY = (h * 2) / 32;
    int frontX1 = Math.max(frontX + 1, (w * 10) / 64),
        frontY1 = Math.max(frontY + 1, (h * 16) / 32);
    boolean frontVisible = false;
    for (int y = frontY; y < frontY1 && !frontVisible;
         y += Math.max(1, (frontY1 - frontY) / 4)) {
      for (int x = frontX; x < frontX1; x += Math.max(1, (frontX1 - frontX) / 4)) {
        if ((pixels[y * w + x] >>> 24) > 16) {
          frontVisible = true;
          break;
        }
      }
    }
    if (!frontVisible) return false;
    if (w == 64 && h == 32) return true;

    int xStart = (w * 46) / 64, yStart = (h * 22) / 32;
    int stepX = Math.max(1, (w - xStart) / 8), stepY = Math.max(1, (h - yStart) / 8);
    int distinct = 0;
    int c0 = 0, c1 = 0;
    boolean hasC0 = false, hasC1 = false;
    for (int y = yStart; y < h; y += stepY) {
      for (int x = xStart; x < w; x += stepX) {
        int px = pixels[y * w + x];
        int key = (px >>> 24) <= 16 ? 0 : (px | 0xff000000);
        if (!hasC0) {
          c0 = key;
          hasC0 = true;
          distinct = 1;
        } else if (key != c0) {
          if (!hasC1) {
            c1 = key;
            hasC1 = true;
            distinct = 2;
          } else if (key != c1) {
            distinct++;
          }
        }
      }
    }
    return distinct <= 2;
  }

  private static DecodedTexture mapArtworkToCapeSheet(BufferedImage art) {
    int aw = art.getWidth(), ah = art.getHeight();
    int kx = Math.max(1, (int) Math.ceil(aw / 10.0));
    int ky = Math.max(1, (int) Math.ceil(ah / 16.0));
    int k = Math.min(64, Math.max(2, Math.max(kx, ky)));
    int sw = k * 64, sh = k * 32;
    int panelX = k, panelY = k, panelW = k * 10, panelH = k * 16;

    BufferedImage scaledFront = scale(art, panelW, panelH, aw <= 40 && ah <= 64);
    int[] front = scaledFront.getRGB(0, 0, panelW, panelH, null, 0, panelW);
    int[] out = new int[sw * sh];

    int backX = k * 12;
    for (int y = 0; y < panelH; y++) {
      int dstRow = (panelY + y) * sw;
      int srcRow = y * panelW;
      System.arraycopy(front, srcRow, out, dstRow + panelX, panelW);
      for (int x = 0; x < panelW; x++) {
        out[dstRow + backX + x] = shadeColor(front[srcRow + (panelW - 1 - x)], .72f);
      }
    }
    for (int x = 0; x < panelW; x++) {
      int topC = shadeColor(front[x], .86f);
      int botC = shadeColor(front[(panelH - 1) * panelW + x], .68f);
      fillRect(out, sw, panelX + x, 0, 1, k, topC);
      fillRect(out, sw, panelX + k * 10 + x, 0, 1, k, shadeColor(botC, .85f));
      fillRect(out, sw, backX + x, 0, 1, k, shadeColor(topC, .85f));
    }
    for (int y = 0; y < panelH; y++) {
      int leftC = shadeColor(front[y * panelW], .80f);
      int rightC = shadeColor(front[y * panelW + panelW - 1], .80f);
      fillRect(out, sw, 0, panelY + y, k, 1, leftC);
      fillRect(out, sw, panelX + panelW, panelY + y, k, 1, rightC);
    }
    fillRect(out, sw, 0, panelY + panelH, k * 22, k, 0xff10141c);
    return new DecodedTexture(out, sw, sh, false);
  }

  static int[] upgradeLegacySkin(int[] src, int w, int h) {
    int[] out = new int[w * w];
    int s = w / 64;
    if (s <= 0) return out;
    copyRegion(src, w, out, w, 0, 0, 0, 0, 64 * s, 16 * s);
    copyRegion(src, w, out, w, 0, 16 * s, 0, 16 * s, 16 * s, 16 * s);
    copyRegion(src, w, out, w, 16 * s, 16 * s, 16 * s, 16 * s, 24 * s, 16 * s);
    copyRegion(src, w, out, w, 40 * s, 16 * s, 40 * s, 16 * s, 16 * s, 16 * s);
    mirrorRegion(out, w, 40 * s, 16 * s, 32 * s, 48 * s, 16 * s, 16 * s);
    mirrorRegion(out, w, 0, 16 * s, 16 * s, 48 * s, 16 * s, 16 * s);
    return out;
  }

  public static boolean detectSlim(int[] pixels, int w, int h) {
    if (pixels == null || w < 64 || h < 64) return false;
    int x0 = (54 * w) / 64, x1 = Math.max(x0 + 1, (56 * w) / 64);
    int y0 = (20 * h) / 64, y1 = Math.max(y0 + 1, (32 * h) / 64);
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) {
        if ((pixels[y * w + x] >>> 24) > 16) return false;
      }
    }
    return true;
  }

  public static DecodedTexture synthesizeCape(OfficialCapes.Cape cape) {
    return new DecodedTexture(
        DefaultSkins.synthesizeCape(cape), PlayerSkin.CAPE_WIDTH, PlayerSkin.CAPE_HEIGHT, false);
  }

  private static BufferedImage readSafeImage(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length == 0) throw new IOException("Empty image");
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (input == null) throw new IOException("Unsupported image format");
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("No image decoder");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w <= 0 || h <= 0 || w > MAX_DECODE_DIMENSION || h > MAX_DECODE_DIMENSION)
          throw new IOException("Image dimensions out of bounds: " + w + "x" + h);
        BufferedImage image = reader.read(0);
        if (image == null) throw new IOException("Failed to decode image");
        return image;
      } finally {
        reader.dispose();
      }
    }
  }

  private static BufferedImage scale(BufferedImage src, int dstW, int dstH, boolean pixelArt) {
    if (src.getWidth() == dstW && src.getHeight() == dstH) return src;
    BufferedImage dst = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = dst.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          pixelArt ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                   : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(src, 0, 0, dstW, dstH, null);
    } finally {
      g.dispose();
    }
    return dst;
  }

  private static int clampPowerOrSize(int dim) {
    int clamped = Math.max(64, Math.min(MAX_DIMENSION, dim));
    return (clamped + 63) / 64 * 64 > MAX_DIMENSION ? MAX_DIMENSION : clamped;
  }

  private static byte[] readAllBytes(InputStream in, int maxBytes) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[16384];
    int total = 0, n;
    while ((n = in.read(buf)) >= 0) {
      total += n;
      if (total > maxBytes) throw new IOException("Image exceeds maximum file size");
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }

  private static void copyRegion(
      int[] src, int sw, int[] dst, int dw, int sx, int sy, int dx, int dy, int rw, int rh) {
    for (int y = 0; y < rh; y++) {
      System.arraycopy(src, (sy + y) * sw + sx, dst, (dy + y) * dw + dx, rw);
    }
  }

  private static void mirrorRegion(int[] buf, int stride, int sx, int sy, int dx, int dy, int rw,
      int rh) {
    for (int y = 0; y < rh; y++) {
      for (int x = 0; x < rw; x++) {
        buf[(dy + y) * stride + dx + x] = buf[(sy + y) * stride + sx + (rw - 1 - x)];
      }
    }
  }

  private static void fillRect(int[] p, int stride, int x, int y, int w, int h, int color) {
    int x0 = Math.max(0, x), y0 = Math.max(0, y);
    int x1 = Math.min(stride, x + w), y1 = Math.min(p.length / stride, y + h);
    for (int yy = y0; yy < y1; yy++)
      for (int xx = x0; xx < x1; xx++) p[yy * stride + xx] = color;
  }

  private static int blend(int a, int b, float t) {
    int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
    int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
    return 0xff000000 | (Math.round(ar + (br - ar) * t) << 16)
        | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
  }

  private static int shadeColor(int c, float factor) {
    int a = (c >>> 24) & 255;
    if (a == 0) return c;
    int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 255) * factor)));
    int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 255) * factor)));
    int b = Math.min(255, Math.max(0, Math.round((c & 255) * factor)));
    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
