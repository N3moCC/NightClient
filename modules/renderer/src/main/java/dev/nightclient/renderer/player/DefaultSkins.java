package dev.nightclient.renderer.player;

import dev.nightclient.cosmetics.OfficialCapes;

public final class DefaultSkins {
  private static final int SKIN = 0xffe3b48d;
  private static final int SKIN_SHADE = 0xffcf9972;
  private static final int HAIR = 0xff1b2433;
  private static final int HAIR_HI = 0xff2d3a4d;
  private static final int JACKET = 0xff1b3548;
  private static final int JACKET_HI = 0xff28546b;
  private static final int PANTS = 0xff243247;
  private static final int BOOT = 0xff111a25;
  private static final int CYAN = 0xff58e6ff;
  private static final int CYAN_DIM = 0xff2496b5;
  private static final int WHITE = 0xfff2fbff;
  private static final int EYE = 0xff182532;

  private static final PlayerSkinData CLASSIC = build(false);
  private static final PlayerSkinData SLIM = build(true);

  private DefaultSkins() {}

  public static PlayerSkinData classic() {
    return CLASSIC;
  }

  public static PlayerSkinData slim() {
    return SLIM;
  }

  public static PlayerSkinData forModel(boolean slim) {
    return slim ? SLIM : CLASSIC;
  }

  private static PlayerSkinData build(boolean slim) {
    int[] body = new int[64 * 64];
    cube(body, 0, 0, 8, 8, 8, SKIN);
    cube(body, 16, 16, 8, 12, 4, JACKET);
    cube(body, 0, 16, 4, 12, 4, PANTS);
    cube(body, 16, 48, 4, 12, 4, PANTS);
    int armWidth = slim ? 3 : 4;
    cube(body, 40, 16, armWidth, 12, 4, JACKET);
    cube(body, 32, 48, armWidth, 12, 4, JACKET);

    hair(body, 0, 0);
    face(body);
    jacket(body, 16, 16);
    legs(body, 0, 16);
    legs(body, 16, 48);
    arm(body, 40, 16, armWidth);
    arm(body, 32, 48, armWidth);

    hatOverlay(body);
    jacketOverlay(body, 16, 32);
    sleeveOverlay(body, 40, 32, armWidth);
    sleeveOverlay(body, 48, 48, armWidth);
    pantOverlay(body, 0, 32);
    pantOverlay(body, 0, 48);

    return new PlayerSkinData(body, capePixels(), slim);
  }

  private static void hair(int[] p, int u, int v) {
    fill(p, u + 8, v, 8, 8, HAIR);
    fill(p, u + 24, v + 8, 8, 8, HAIR);
    fill(p, u, v + 8, 8, 3, HAIR);
    fill(p, u + 16, v + 8, 8, 3, HAIR);
    fill(p, u + 9, v + 1, 6, 1, HAIR_HI);
    fill(p, u + 9, v + 2, 4, 1, HAIR_HI);
    fill(p, u + 24, v + 14, 8, 2, SKIN_SHADE);
  }

  private static void face(int[] p) {
    int u = 8, v = 8;
    fill(p, u, v, 8, 2, HAIR);
    fill(p, u + 1, v + 2, 6, 5, SKIN);
    fill(p, u + 1, v + 6, 6, 1, SKIN_SHADE);
    fill(p, u + 1, v + 3, 2, 1, EYE);
    fill(p, u + 5, v + 3, 2, 1, EYE);
    fill(p, u + 1, v + 3, 1, 1, WHITE);
    fill(p, u + 6, v + 3, 1, 1, WHITE);
    fill(p, u + 3, v + 4, 1, 1, CYAN_DIM);
    fill(p, u + 4, v + 5, 1, 1, SKIN_SHADE);
    fill(p, u + 3, v + 6, 2, 1, 0xff7a4d43);
  }

  private static void jacket(int[] p, int u, int v) {
    int frontU = u + 4, frontV = v + 4;
    fill(p, frontU + 4, frontV, 1, 10, JACKET_HI);
    fill(p, frontU + 1, frontV + 3, 6, 1, CYAN_DIM);
    fill(p, frontU, frontV + 8, 8, 1, 0xff132735);
    fill(p, u + 1, v + 4, 1, 8, JACKET_HI);
    fill(p, u + 13, v + 4, 1, 8, JACKET_HI);
    fill(p, u + 17, v + 4, 1, 8, 0xff132735);
    fill(p, u + 3, v + 15, 2, 1, CYAN_DIM);
  }

  private static void arm(int[] p, int u, int v, int width) {
    int sleeveEnd = v + 9;
    int local = u + 4;
    fill(p, local, sleeveEnd, width, 3, SKIN);
    fill(p, local, sleeveEnd, width, 1, SKIN_SHADE);
    fill(p, u, sleeveEnd, 4, 3, SKIN);
    fill(p, u + 4 + width, sleeveEnd, 4, 3, SKIN);
    fill(p, u + 8 + width, sleeveEnd, width, 3, SKIN);
    fill(p, u, v + 6, 4, 1, JACKET_HI);
    fill(p, u + 4 + width, v + 6, 4, 1, JACKET_HI);
    fill(p, u + 8 + width, v + 6, width, 1, JACKET_HI);
  }

  private static void legs(int[] p, int u, int v) {
    int frontU = u + 4, frontV = v + 4;
    fill(p, frontU, frontV + 2, 4, 1, 0xff2d3d52);
    fill(p, frontU, frontV + 6, 4, 1, 0xff1a2637);
    fill(p, frontU, frontV + 7, 4, 1, 0xff30445b);
    fill(p, frontU, frontV + 8, 4, 3, BOOT);
    fill(p, frontU, frontV + 11, 4, 1, 0xff1b2837);
    fill(p, frontU + 1, frontV + 9, 2, 1, 0xff223141);
    fill(p, u, v + 11, 4, 5, BOOT);
    fill(p, u + 8, v + 11, 4, 5, BOOT);
    fill(p, u + 12, v + 11, 4, 5, BOOT);
  }

  private static void hatOverlay(int[] p) {
    fill(p, 32 + 8 + 1, 8 + 3, 6, 1, 0x703fe6ff);
    fill(p, 32 + 8 + 1, 8 + 4, 6, 1, 0x302496b5);
    fill(p, 32 + 8, 8 + 2, 8, 1, 0x302496b5);
    fill(p, 32 + 8, 0, 8, 1, 0xc018252f);
  }

  private static void jacketOverlay(int[] p, int u, int v) {
    fill(p, u + 4 + 2, v + 4, 4, 1, 0xff101b25);
    fill(p, u + 4 + 3, v + 5, 2, 1, CYAN);
    fill(p, u + 4 + 3, v + 6, 2, 1, CYAN_DIM);
    fill(p, u + 4, v + 4 + 11, 8, 1, 0xff0e1a24);
    fill(p, u, v + 4 + 4, 1, 4, 0x402496b5);
    fill(p, u + 12, v + 4 + 4, 1, 4, 0x402496b5);
  }

  private static void sleeveOverlay(int[] p, int u, int v, int width) {
    fill(p, u + 4, v + 4 + 8, width, 1, CYAN_DIM);
    fill(p, u, v + 4 + 8, 4, 1, CYAN_DIM);
    fill(p, u + 4 + width, v + 4 + 8, 4, 1, CYAN_DIM);
    fill(p, u + 8 + width, v + 4 + 8, width, 1, CYAN_DIM);
  }

  private static void pantOverlay(int[] p, int u, int v) {
    fill(p, u + 4, v + 4 + 3, 4, 1, 0xff2a3d53);
    fill(p, u, v + 4 + 6, 4, 1, 0x402496b5);
    fill(p, u + 8, v + 4 + 6, 4, 1, 0x402496b5);
  }

  public static int[] capePixels() {
    int[] p = new int[64 * 32];
    int w = 10, h = 16;
    fill(p, 0, 0, 22, 17, 0xff0b1721);
    for (int y = 0; y < h; y++) {
      int c = blend(0xff1b3b50, 0xff2d6a86, y / (float) (h - 1));
      fill(p, 1, 1 + y, w, 1, c);
      fill(p, 12, 1 + y, w, 1, blend(c, 0xff0b1721, .45f));
    }
    for (int y = 2; y < h; y++) {
      fill(p, 3, y, 1, 1, blend(p[y * 64 + 3], 0xff0e2230, .22f));
      fill(p, 4, y, 1, 1, blend(p[y * 64 + 4], 0xff4c8aa6, .16f));
      fill(p, 7, y, 1, 1, blend(p[y * 64 + 7], 0xff0e2230, .22f));
      fill(p, 8, y, 1, 1, blend(p[y * 64 + 8], 0xff4c8aa6, .16f));
    }
    fill(p, 1, 1, 1, h, 0xff0b1721);
    fill(p, 10, 1, 1, h, 0xff0b1721);
    fill(p, 1, 16, w, 1, 0xff0b1721);
    fill(p, 1, 1, w, 1, 0xff0b1721);
    for (int y = 4; y <= 11; y++) {
      int dx = Math.abs(y - 7);
      int left = 2 + dx;
      int right = 9 - dx;
      if (left <= right) {
        p[y * 64 + left] = CYAN;
        p[y * 64 + left + 1] = CYAN_DIM;
        p[y * 64 + right] = CYAN;
        p[y * 64 + right - 1] = CYAN_DIM;
      }
    }
    fill(p, 5, 13, 2, 1, 0xffbff7ff);
    fill(p, 5, 13, 1, 2, 0xffbff7ff);
    return p;
  }

  public static int[] synthesizeCape(OfficialCapes.Cape cape) {
    int w = 64, h = 32;
    int[] p = new int[w * h];
    if (cape == null) return capePixels();
    int top = cape.primaryColor(), bot = cape.secondaryColor(), acc = cape.accentColor();
    int border = shadeColor(bot, .62f);

    fill(p, 0, 0, 22, 17, border);
    for (int y = 0; y < 16; y++) {
      float t = y / 15f;
      int row = blend(top, bot, t);
      fill(p, 1, 1 + y, 10, 1, row);
      fill(p, 12, 1 + y, 10, 1, shadeColor(row, .72f));
    }
    fill(p, 1, 0, 10, 1, shadeColor(top, .92f));
    fill(p, 11, 0, 10, 1, border);
    fill(p, 0, 1, 1, 16, shadeColor(top, .76f));
    fill(p, 11, 1, 1, 16, shadeColor(top, .76f));
    fill(p, 1, 1, 10, 1, shadeColor(top, 1.12f));
    fill(p, 1, 16, 10, 1, border);
    fill(p, 1, 1, 1, 16, shadeColor(border, 1.15f));
    fill(p, 10, 1, 1, 16, shadeColor(border, 1.15f));

    drawEmblem(p, cape.emblem, acc, shadeColor(acc, .70f));
    return p;
  }

  private static void drawEmblem(int[] p, String emblem, int bright, int dim) {
    String key = emblem == null ? "star" : emblem;
    if ("creeper".equals(key)) {
      plot(p, 3, 5, 2, 2, bright);
      plot(p, 7, 5, 2, 2, bright);
      plot(p, 5, 7, 2, 3, bright);
      plot(p, 4, 9, 1, 3, bright);
      plot(p, 7, 9, 1, 3, bright);
    } else if ("pickaxe".equals(key) || "builder".equals(key) || "crafter".equals(key)) {
      plot(p, 3, 4, 5, 1, bright);
      plot(p, 7, 5, 2, 2, bright);
      for (int i = 0; i < 6; i++) plot(p, 8 - i, 5 + i, 1, 1, dim);
    } else if ("snow_golem".equals(key)) {
      plot(p, 3, 4, 1, 1, bright);
      plot(p, 8, 4, 1, 1, bright);
      plot(p, 3, 6, 1, 1, bright);
      plot(p, 4, 7, 4, 1, bright);
      plot(p, 8, 6, 1, 1, bright);
      plot(p, 5, 10, 2, 1, dim);
      plot(p, 5, 13, 2, 1, dim);
    } else if ("beacon".equals(key)) {
      plot(p, 5, 2, 2, 7, bright);
      plot(p, 4, 9, 4, 2, bright);
      plot(p, 3, 11, 6, 2, dim);
      plot(p, 2, 13, 8, 2, dim);
    } else if ("heart".equals(key) || "valentine".equals(key)) {
      plot(p, 3, 5, 2, 2, bright);
      plot(p, 7, 5, 2, 2, bright);
      plot(p, 3, 7, 6, 2, bright);
      plot(p, 4, 9, 4, 1, dim);
      plot(p, 5, 10, 2, 1, dim);
    } else if ("crown".equals(key)) {
      plot(p, 3, 6, 1, 4, bright);
      plot(p, 5, 5, 2, 5, bright);
      plot(p, 8, 6, 1, 4, bright);
      plot(p, 3, 9, 6, 2, dim);
    } else if ("piston".equals(key) || "office".equals(key) || "mojang".equals(key)
        || "classic".equals(key)) {
      plot(p, 3, 5, 6, 2, bright);
      plot(p, 5, 7, 2, 4, dim);
      plot(p, 3, 11, 6, 2, bright);
    } else if ("golem".equals(key) || "copper".equals(key) || "common".equals(key)) {
      plot(p, 3, 5, 6, 6, dim);
      plot(p, 4, 6, 1, 1, bright);
      plot(p, 7, 6, 1, 1, bright);
      plot(p, 5, 8, 2, 3, bright);
    } else if ("cherry".equals(key)) {
      for (int i = 0; i < 5; i++) {
        plot(p, 3 + i, 5 + i, 1, 1, bright);
        plot(p, 8 - i, 4 + i, 2, 1, dim);
      }
      plot(p, 3, 10, 2, 2, bright);
    } else if ("aurora".equals(key)) {
      for (int x = 2; x < 10; x++) {
        int y = 5 + (int) Math.round(Math.sin((x - 2) * .75) * 1.5);
        plot(p, x, y, 1, 4, x % 2 == 0 ? bright : dim);
      }
    } else if ("gift".equals(key) || "birthday".equals(key)) {
      plot(p, 3, 5, 6, 7, dim);
      plot(p, 5, 5, 2, 7, bright);
      plot(p, 3, 8, 6, 2, bright);
    } else if ("sword".equals(key)) {
      plot(p, 5, 3, 2, 7, bright);
      plot(p, 3, 9, 6, 2, bright);
      plot(p, 5, 11, 2, 3, dim);
    } else if ("torch".equals(key) || "portal".equals(key)) {
      plot(p, 4, 4, 4, 8, dim);
      plot(p, 5, 5, 2, 6, bright);
    } else if ("enderman".equals(key)) {
      plot(p, 2, 7, 2, 1, bright);
      plot(p, 8, 7, 2, 1, bright);
      plot(p, 3, 7, 1, 1, dim);
      plot(p, 8, 7, 1, 1, dim);
    } else if ("twitch".equals(key)) {
      plot(p, 3, 5, 6, 5, bright);
      plot(p, 4, 6, 1, 2, dim);
      plot(p, 7, 6, 1, 2, dim);
      plot(p, 5, 10, 2, 2, bright);
    } else if ("pancape".equals(key)) {
      plot(p, 3, 5, 6, 5, bright);
      plot(p, 3, 10, 6, 2, dim);
    } else if ("snowman".equals(key)) {
      plot(p, 4, 4, 4, 3, bright);
      plot(p, 3, 7, 6, 4, bright);
      plot(p, 3, 11, 6, 3, dim);
    } else if ("turtle".equals(key) || "horse".equals(key)) {
      plot(p, 4, 4, 4, 3, bright);
      plot(p, 3, 7, 6, 5, dim);
      plot(p, 4, 8, 4, 3, bright);
    } else if ("globe".equals(key) || "globe_cn".equals(key) || "cobalt".equals(key)) {
      plot(p, 4, 5, 4, 1, bright);
      plot(p, 3, 6, 6, 4, bright);
      plot(p, 4, 10, 4, 1, bright);
      plot(p, 4, 7, 4, 2, dim);
    } else if ("bacon".equals(key)) {
      plot(p, 2, 4, 8, 2, bright);
      plot(p, 2, 7, 8, 2, dim);
      plot(p, 2, 10, 8, 2, bright);
    } else if ("prismarine".equals(key) || "scrolls".equals(key) || "map".equals(key)
        || "document".equals(key)) {
      plot(p, 3, 4, 6, 9, dim);
      plot(p, 4, 5, 4, 7, bright);
      plot(p, 5, 6, 2, 2, dim);
    } else if ("translator".equals(key) || "bug".equals(key)) {
      plot(p, 3, 5, 6, 6, dim);
      plot(p, 4, 6, 4, 4, bright);
    } else {
      for (int r = -3; r <= 3; r++) {
        int half = 3 - Math.abs(r);
        plot(p, 5 - half, 8 + r, half * 2 + 2, 1, Math.abs(r) <= 1 ? bright : dim);
      }
    }
  }

  private static void plot(int[] p, int x, int y, int bw, int bh, int color) {
    fill(p, Math.max(1, x), Math.max(1, y), bw, bh, color);
  }

  private static int shadeColor(int c, float factor) {
    int a = (c >>> 24) & 255;
    if (a == 0) return c;
    int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 255) * factor)));
    int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 255) * factor)));
    int b = Math.min(255, Math.max(0, Math.round((c & 255) * factor)));
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static void cube(int[] p, int u, int v, int width, int height, int depth, int color) {
    int side = depth;
    fill(p, u, v + depth, side, height, color);
    fill(p, u + side, v + depth, width, height, color);
    fill(p, u + side + width, v + depth, side, height, color);
    fill(p, u + side * 2 + width, v + depth, width, height, color);
    fill(p, u + side, v, width, depth, color);
    fill(p, u + side + width, v, width, depth, color);
  }

  private static void fill(int[] p, int x, int y, int w, int h, int color) {
    int x0 = Math.max(0, x), y0 = Math.max(0, y);
    int x1 = Math.min(64, x + w), y1 = Math.min(64, y + h);
    for (int yy = y0; yy < y1; yy++)
      for (int xx = x0; xx < x1; xx++) p[yy * 64 + xx] = color;
  }

  private static int blend(int a, int b, float t) {
    int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
    int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
    return 0xff000000 | (Math.round(ar + (br - ar) * t) << 16)
        | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
  }
}
