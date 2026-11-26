package dev.nightclient.renderer.player;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.PlayerSkin;
import java.util.Locale;

public final class PlayerFacePainter {
  public static final int FACE_SIZE = 8;

  private static final int[] HAIR_PALETTE = {
      0xff1b2433, 0xff3b271c, 0xff5a3825, 0xff1f2d3d, 0xff2b1f3a, 0xff4c2429, 0xff1e3832, 0xff6e4a27
  };
  private static final int[] SKIN_PALETTE = {
      0xffe3b48d, 0xffefc6a3, 0xffd59c72, 0xffb87c54, 0xfff3d0b2, 0xffc68b63
  };
  private static final int[] EYE_PALETTE = {
      0xff2a6f97, 0xff3a7d44, 0xff6a4c93, 0xff2b3a4a, 0xff8b4513, 0xff0ea5e9
  };

  private PlayerFacePainter() {}

  public static int[] extractFace(PlayerSkin skin) {
    if (skin == null) return null;
    int[] body = skin.bodyPixels();
    int w = skin.bodyWidth();
    int h = skin.bodyHeight();
    if (body == null || w < 64 || h < 32 || (long) w * h > body.length) return null;
    float baseHeight = (w == h * 2) ? 32f : 64f;
    int[] face = new int[FACE_SIZE * FACE_SIZE];
    for (int py = 0; py < FACE_SIZE; py++) {
      int sy = clampIndex((int) ((8 + py + 0.5f) * h / baseHeight), h);
      int rowOffset = sy * w;
      for (int px = 0; px < FACE_SIZE; px++) {
        int bx = clampIndex((int) ((8 + px + 0.5f) * w / 64f), w);
        int base = body[rowOffset + bx] | 0xff000000;
        int hx = clampIndex((int) ((40 + px + 0.5f) * w / 64f), w);
        int hat = body[rowOffset + hx];
        int ha = (hat >>> 24) & 0xff;
        face[py * FACE_SIZE + px] = ha == 0 ? base : blendOpaque(base, hat, ha);
      }
    }
    return face;
  }

  public static int[] fallbackFace(String nickname) {
    String key = nickname == null ? "" : nickname.trim().toLowerCase(Locale.ROOT);
    int hash = 0x811c9dc5;
    for (int i = 0; i < key.length(); i++) {
      hash ^= key.charAt(i);
      hash *= 0x01000193;
    }
    int hair = HAIR_PALETTE[Math.floorMod(hash, HAIR_PALETTE.length)];
    int hairHi = shade(hair, 1.18f);
    int skin = SKIN_PALETTE[Math.floorMod(hash >>> 8, SKIN_PALETTE.length)];
    int skinShade = shade(skin, 0.88f);
    int eye = EYE_PALETTE[Math.floorMod(hash >>> 16, EYE_PALETTE.length)];
    int mouth = shade(skin, 0.68f);

    int[] face = new int[FACE_SIZE * FACE_SIZE];
    for (int i = 0; i < face.length; i++) face[i] = skin;
    for (int x = 0; x < 8; x++) {
      face[x] = (x >= 2 && x <= 5) ? hairHi : hair;
      face[8 + x] = hair;
    }
    face[16] = hair;
    face[23] = hair;
    if ((hash & 1) != 0) face[17] = hair;
    if ((hash & 2) != 0) face[22] = hair;
    face[32 + 1] = 0xfff5f9ff;
    face[32 + 2] = eye;
    face[32 + 5] = eye;
    face[32 + 6] = 0xfff5f9ff;
    face[40 + 3] = skinShade;
    face[40 + 4] = skinShade;
    for (int x = 0; x < 8; x++) face[56 + x] = skinShade;
    face[48 + 3] = mouth;
    face[48 + 4] = mouth;
    return face;
  }

  public static void paint(Canvas canvas, int[] face64, float x, float y, float size, float alpha) {
    if (canvas == null || face64 == null || face64.length < 64 || size <= 0f || alpha <= 0f) return;
    float clampedAlpha = Math.max(0f, Math.min(1f, alpha));
    float cell = size / FACE_SIZE;
    for (int row = 0; row < FACE_SIZE; row++) {
      float ry = y + row * cell;
      float rh = (y + (row + 1) * cell) - ry;
      int base = row * FACE_SIZE;
      int col = 0;
      while (col < FACE_SIZE) {
        int argb = face64[base + col];
        int end = col + 1;
        while (end < FACE_SIZE && face64[base + end] == argb) end++;
        float rx = x + col * cell;
        float rw = (x + end * cell) - rx;
        int color = clampedAlpha >= 0.999f ? (argb | 0xff000000) : Colors.alpha(argb | 0xff000000, clampedAlpha);
        canvas.rect(rx, ry, rw, rh, color);
        col = end;
      }
    }
  }

  private static int clampIndex(int value, int maxExclusive) {
    return value < 0 ? 0 : value >= maxExclusive ? maxExclusive - 1 : value;
  }

  private static int blendOpaque(int base, int over, int alpha) {
    if (alpha >= 255) return over | 0xff000000;
    int inv = 255 - alpha;
    int r = (((over >>> 16) & 0xff) * alpha + ((base >>> 16) & 0xff) * inv + 127) / 255;
    int g = (((over >>> 8) & 0xff) * alpha + ((base >>> 8) & 0xff) * inv + 127) / 255;
    int b = ((over & 0xff) * alpha + (base & 0xff) * inv + 127) / 255;
    return 0xff000000 | (r << 16) | (g << 8) | b;
  }

  private static int shade(int argb, float factor) {
    int r = Math.max(0, Math.min(255, Math.round(((argb >>> 16) & 0xff) * factor)));
    int g = Math.max(0, Math.min(255, Math.round(((argb >>> 8) & 0xff) * factor)));
    int b = Math.max(0, Math.min(255, Math.round((argb & 0xff) * factor)));
    return 0xff000000 | (r << 16) | (g << 8) | b;
  }
}
