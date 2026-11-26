package dev.nightclient.renderer.map;

public final class MapColorShading {
  private MapColorShading() {}

  public static final short UNEXPLORED_HEIGHT = Short.MIN_VALUE;

  public static final int UNEXPLORED_ARGB = 0x00000000;

  public static final int RELIEF_FLAT = 0;
  public static final int RELIEF_CLASSIC = 1;
  public static final int RELIEF_VIVID = 2;
  public static final int RELIEF_CONTOUR = 3;

  public static int parseReliefStyle(String style) {
    if ("flat".equals(style)) return RELIEF_FLAT;
    if ("vivid".equals(style)) return RELIEF_VIVID;
    if ("contour".equals(style)) return RELIEF_CONTOUR;
    return RELIEF_CLASSIC;
  }

  public static int composeSurfaceRgb(int groundRgb, int waterDepth, int biomeWaterRgb,
      int overlayRgb, float overlayAlpha) {
    int rgb = groundRgb & 0xFFFFFF;
    if (waterDepth > 0) {
      int water = (biomeWaterRgb & 0xFFFFFF);
      if (water == 0) water = 0x3F76E4;
      double absorption = 1.0 - Math.exp(-waterDepth * 0.28);
      float mix = (float) Math.max(0.38, Math.min(0.93, absorption));
      float depthDarken = (float) Math.max(0.66, 1.0 - Math.min(24, waterDepth) * 0.013);
      int shadedWater = scaleRgb(water, depthDarken);
      rgb = blendRgb(rgb, shadedWater, mix);
      if (waterDepth == 1) rgb = scaleRgb(rgb, 1.03f);
    }
    if (overlayAlpha > 0.01f) {
      rgb = blendRgb(rgb, overlayRgb & 0xFFFFFF, Math.min(1f, overlayAlpha));
    }
    return rgb & 0xFFFFFF;
  }

  public static int shadePixel(int baseRgb, short height, short northHeight, short westHeight,
      int blockLight, boolean terrainShading, boolean useBlockLight) {
    return shadePixel(baseRgb, height, northHeight, westHeight, blockLight, terrainShading,
        useBlockLight, RELIEF_CLASSIC, 1.35f);
  }

  public static int shadePixel(int baseRgb, short height, short northHeight, short westHeight,
      int blockLight, boolean terrainShading, boolean useBlockLight, int reliefStyle,
      float reliefStrength) {
    if (height == UNEXPLORED_HEIGHT) return UNEXPLORED_ARGB;
    int r = (baseRgb >>> 16) & 0xFF;
    int g = (baseRgb >>> 8) & 0xFF;
    int b = baseRgb & 0xFF;

    float mul = 1.0f;
    float coolShadow = 0f;
    float warmSun = 0f;
    boolean activeRelief = terrainShading && reliefStyle != RELIEF_FLAT && reliefStrength > 0.001f;
    if (activeRelief) {
      int dhN = northHeight == UNEXPLORED_HEIGHT ? 0 : (height - northHeight);
      int dhW = westHeight == UNEXPLORED_HEIGHT ? 0 : (height - westHeight);
      float strength = Math.max(0f, Math.min(8.0f, reliefStrength));
      if (reliefStyle == RELIEF_VIVID) strength *= 1.25f;

      float slopeN = stepSlope(dhN, 0.068f, 0.030f, 7);
      float slopeW = stepSlope(dhW, 0.038f, 0.017f, 6);
      float rawSlope = (slopeN + slopeW) * strength;
      float maxUp = Math.min(0.42f, 0.16f + 0.055f * strength);
      float maxDown = Math.min(0.48f, 0.20f + 0.060f * strength);
      float slope;
      if (rawSlope > 0f) {
        slope = maxUp * (rawSlope / (rawSlope + maxUp * 0.72f));
      } else if (rawSlope < 0f) {
        float neg = -rawSlope;
        slope = -maxDown * (neg / (neg + maxDown * 0.72f));
      } else {
        slope = 0f;
      }

      float altitude = (Math.max(-64, Math.min(256, height)) - 64) * 0.00075f
          * Math.min(1.6f, strength * 0.65f);
      mul = 1.0f + slope + altitude;

      if (reliefStyle == RELIEF_CONTOUR) {
        int band = height >> 3;
        boolean crossN = northHeight != UNEXPLORED_HEIGHT && (northHeight >> 3) != band;
        boolean crossW = westHeight != UNEXPLORED_HEIGHT && (westHeight >> 3) != band;
        if (crossN || crossW) {
          mul *= (1.0f - Math.min(0.32f, 0.11f + 0.035f * strength));
        }
      }

      float grade = reliefStyle == RELIEF_VIVID ? 1.5f : 0.45f;
      if (slope < -0.02f) coolShadow = Math.min(1f, (-slope) * 1.8f * grade);
      else if (slope > 0.02f) warmSun = Math.min(1f, slope * 1.4f * grade);

      float extra = Math.max(0f, strength - 1.5f);
      float minMul = Math.max(0.42f, (reliefStyle == RELIEF_VIVID ? 0.56f : 0.62f) - extra * 0.045f);
      float maxMul = Math.min(1.48f, (reliefStyle == RELIEF_VIVID ? 1.32f : 1.26f) + extra * 0.055f);
      if (mul < minMul) mul = minMul;
      else if (mul > maxMul) mul = maxMul;
    }

    float rf = r * mul;
    float gf = g * mul;
    float bf = b * mul;
    if (coolShadow > 0f) {
      rf *= (1.0f - coolShadow * 0.055f);
      gf *= (1.0f - coolShadow * 0.015f);
      bf *= (1.0f + coolShadow * 0.055f);
    } else if (warmSun > 0f) {
      rf *= (1.0f + warmSun * 0.032f);
      gf *= (1.0f + warmSun * 0.012f);
      bf *= (1.0f - warmSun * 0.024f);
    }
    r = clampByte(Math.round(rf));
    g = clampByte(Math.round(gf));
    b = clampByte(Math.round(bf));

    if (useBlockLight && blockLight > 8) {
      float glow = (blockLight - 8) / 7.0f;
      float warm = glow * 0.11f;
      r = clampByte(Math.round(r + (255 - r) * warm * 1.10f));
      g = clampByte(Math.round(g + (222 - g) * warm * 0.80f));
      b = clampByte(Math.round(b + (156 - b) * warm * 0.30f));
    }

    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private static float stepSlope(int dh, float firstStep, float extraStep, int maxSteps) {
    if (dh == 0) return 0f;
    int abs = dh < 0 ? -dh : dh;
    if (abs > maxSteps) abs = maxSteps;
    float mag = firstStep + (abs - 1) * extraStep;
    return dh > 0 ? mag : -mag * 1.12f;
  }

  public static int blendRgb(int a, int b, float t) {
    if (t <= 0f) return a & 0xFFFFFF;
    if (t >= 1f) return b & 0xFFFFFF;
    int ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
    int br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
    int r = clampByte(Math.round(ar + (br - ar) * t));
    int g = clampByte(Math.round(ag + (bg - ag) * t));
    int bl = clampByte(Math.round(ab + (bb - ab) * t));
    return (r << 16) | (g << 8) | bl;
  }

  public static int tintRgb(int baseRgb, int tintRgb) {
    int r = (((baseRgb >>> 16) & 0xFF) * ((tintRgb >>> 16) & 0xFF)) / 255;
    int g = (((baseRgb >>> 8) & 0xFF) * ((tintRgb >>> 8) & 0xFF)) / 255;
    int b = ((baseRgb & 0xFF) * (tintRgb & 0xFF)) / 255;
    return (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
  }

  public static int scaleRgb(int rgb, float factor) {
    int r = clampByte(Math.round(((rgb >>> 16) & 0xFF) * factor));
    int g = clampByte(Math.round(((rgb >>> 8) & 0xFF) * factor));
    int b = clampByte(Math.round((rgb & 0xFF) * factor));
    return (r << 16) | (g << 8) | b;
  }

  public static int averageQuad(int p00, int p10, int p01, int p11) {
    int count = 0, r = 0, g = 0, b = 0;
    if ((p00 >>> 24) != 0) {
      r += (p00 >>> 16) & 0xFF;
      g += (p00 >>> 8) & 0xFF;
      b += p00 & 0xFF;
      count++;
    }
    if ((p10 >>> 24) != 0) {
      r += (p10 >>> 16) & 0xFF;
      g += (p10 >>> 8) & 0xFF;
      b += p10 & 0xFF;
      count++;
    }
    if ((p01 >>> 24) != 0) {
      r += (p01 >>> 16) & 0xFF;
      g += (p01 >>> 8) & 0xFF;
      b += p01 & 0xFF;
      count++;
    }
    if ((p11 >>> 24) != 0) {
      r += (p11 >>> 16) & 0xFF;
      g += (p11 >>> 8) & 0xFF;
      b += p11 & 0xFF;
      count++;
    }
    if (count == 0) return UNEXPLORED_ARGB;
    int alpha = count >= 2 ? 0xFF : 0xD0;
    return (alpha << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
  }

  private static int clampByte(int v) {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
  }

  public static int[] generateDemoTerrain(int size) {
    int[] base = new int[size * size];
    short[] heights = new short[size * size];
    byte[] lights = new byte[size * size];
    int[] out = new int[size * size];
    float half = size * 0.5f;
    for (int z = 0; z < size; z++) {
      for (int x = 0; x < size; x++) {
        float dx = (x - half) / half;
        float dz = (z - half) / half;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float ridge = (float) (Math.sin(x * 0.085) * Math.cos(z * 0.075) * 8.0
            + Math.sin((x + z) * 0.042) * 5.0 + Math.cos((x - z) * 0.14) * 2.2);
        float river = Math.abs((float) (z - half - Math.sin(x * 0.055) * (size * 0.18)));
        float elev = 72f + ridge - dist * 16f;
        if (river < 7.5f) elev -= (7.5f - river) * 2.1f;

        int idx = z * size + x;
        int h = Math.round(elev);
        if (h < 62) {
          int depth = Math.max(1, 62 - h);
          base[idx] = composeSurfaceRgb(0xC2B280, depth, 0x3A7AD9, 0, 0f);
          heights[idx] = 62;
        } else {
          heights[idx] = (short) h;
          int rgb;
          if (h <= 64) {
            rgb = 0xD6C88D;
          } else if (h >= 78) {
            rgb = (h >= 82) ? 0xD8E1E8 : 0x8A9099;
          } else {
            float trees = (float) (Math.sin(x * 0.37) * Math.cos(z * 0.41)
                + Math.sin((x - z) * 0.22));
            if (trees > 0.72f && h > 66 && river > 10f) {
              rgb = ((x + z) & 1) == 0 ? 0x3B6E2A : 0x457C31;
              heights[idx] = (short) (h + 4);
            } else {
              rgb = (((x ^ z) & 3) == 0) ? 0x6AA845 : 0x73B34C;
            }
          }
          float path = (float) Math.abs(x - half - Math.sin(z * 0.07) * 8.0);
          if (path < 2.2f && h >= 64 && river > 6.5f) {
            rgb = 0xB89B67;
            heights[idx] = (short) h;
          }
          if (Math.abs(x - (half - 14)) <= 5 && Math.abs(z - (half - 10)) <= 4) {
            rgb = (Math.abs(x - (half - 14)) == 5 || Math.abs(z - (half - 10)) == 4)
                ? 0x6A4E32
                : 0x9F7A4D;
            heights[idx] = (short) (h + 5);
            lights[idx] = 12;
          }
          if (Math.abs(x - (half + 15)) <= 4 && Math.abs(z - (half + 12)) <= 4) {
            rgb = 0x8A6642;
            heights[idx] = (short) (h + 4);
            lights[idx] = 10;
          }
          base[idx] = rgb;
        }
      }
    }
    for (int z = 0; z < size; z++) {
      for (int x = 0; x < size; x++) {
        int idx = z * size + x;
        short nh = z > 0 ? heights[idx - size] : heights[idx];
        short wh = x > 0 ? heights[idx - 1] : heights[idx];
        out[idx] = shadePixel(base[idx], heights[idx], nh, wh, lights[idx] & 0xFF, true, true);
      }
    }
    return out;
  }
}
