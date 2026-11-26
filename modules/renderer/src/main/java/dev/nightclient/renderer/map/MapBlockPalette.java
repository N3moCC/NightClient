package dev.nightclient.renderer.map;

import java.util.Locale;

public final class MapBlockPalette {

  public static final byte KIND_SOLID = 0;
  public static final byte KIND_SKIP = 1;
  public static final byte KIND_TRANSLUCENT = 2;
  public static final byte KIND_FLAT_OVERLAY = 3;

  public static final byte TINT_NONE = 0;
  public static final byte TINT_GRASS = 1;
  public static final byte TINT_FOLIAGE = 2;
  public static final byte TINT_SPRUCE = 3;
  public static final byte TINT_BIRCH = 4;
  public static final byte TINT_LILY = 5;

  private static final int SPRUCE_FOLIAGE_RGB = 0x619961;
  private static final int BIRCH_FOLIAGE_RGB = 0x80A755;
  private static final int LILY_PAD_RGB = 0x208030;

  public static final class BlockEntry {
    public final byte kind;
    public final byte tintMode;
    public final int textureRgb;
    public final float overlayAlpha;

    public BlockEntry(byte kind, byte tintMode, int textureRgb, float overlayAlpha) {
      this.kind = kind;
      this.tintMode = tintMode;
      this.textureRgb = textureRgb;
      this.overlayAlpha = overlayAlpha;
    }
  }

  public interface TextureReader {
    byte[] loadResourceBytes(String resourceId);
  }

  private MapBlockPalette() {}

  public static BlockEntry resolve(String rawBlockId, int mapColorId, TextureReader reader) {
    String full = rawBlockId == null ? "" : rawBlockId.trim().toLowerCase(Locale.ROOT);
    String ns = "minecraft";
    String path = full;
    int colon = full.indexOf(':');
    if (colon >= 0) {
      ns = full.substring(0, colon);
      path = full.substring(colon + 1);
    }
    if (path.isEmpty()) {
      return new BlockEntry(KIND_SOLID, TINT_NONE, -1, 0f);
    }

    if (isSkippedClutter(path)) {
      return new BlockEntry(KIND_SKIP, TINT_NONE, -1, 0f);
    }

    if ("lily_pad".equals(path)) {
      int rgb = sampleBlockTexture(ns, path, reader);
      return new BlockEntry(KIND_FLAT_OVERLAY, TINT_LILY, rgb, 0.84f);
    }
    if ("snow".equals(path)) {
      int rgb = sampleBlockTexture(ns, "snow", reader);
      return new BlockEntry(KIND_FLAT_OVERLAY, TINT_NONE, rgb >= 0 ? rgb : 0xF4F9FC, 0.88f);
    }
    if (isFlowerOverlay(path)) {
      int rgb = sampleBlockTexture(ns, path, reader);
      return new BlockEntry(KIND_FLAT_OVERLAY, TINT_NONE, rgb, 0.34f);
    }

    if (isTranslucentSurface(path)) {
      int rgb = sampleBlockTexture(ns, path, reader);
      float a = path.contains("ice") ? 0.62f : (path.contains("glass") ? 0.44f : 0.55f);
      return new BlockEntry(KIND_TRANSLUCENT, TINT_NONE, rgb, a);
    }

    byte tint = tintModeFor(path, mapColorId);

    int rgb = sampleBlockTexture(ns, path, reader);
    return new BlockEntry(KIND_SOLID, tint, rgb, 0f);
  }

  public static int resolveColor(BlockEntry entry, int fallbackMapColor, boolean biomeColors,
      int biomeGrassRgb, int biomeFoliageRgb) {
    if (entry == null) return fallbackMapColor & 0xFFFFFF;
    int base = entry.textureRgb >= 0 ? entry.textureRgb : (fallbackMapColor & 0xFFFFFF);
    boolean hasTex = entry.textureRgb >= 0;

    switch (entry.tintMode) {
      case TINT_GRASS: {
        int tint = biomeColors && biomeGrassRgb != 0 ? (biomeGrassRgb & 0xFFFFFF) : 0x91BD59;
        return hasTex ? MapColorShading.tintRgb(base, tint)
                      : MapColorShading.blendRgb(base, tint, 0.72f);
      }
      case TINT_FOLIAGE: {
        int tint = biomeColors && biomeFoliageRgb != 0 ? (biomeFoliageRgb & 0xFFFFFF) : 0x77AB2F;
        if (hasTex) {
          return MapColorShading.scaleRgb(MapColorShading.tintRgb(base, tint), 1.06f);
        }
        return MapColorShading.blendRgb(base, tint, 0.72f);
      }
      case TINT_SPRUCE: {
        return hasTex ? MapColorShading.scaleRgb(
            MapColorShading.tintRgb(base, SPRUCE_FOLIAGE_RGB), 1.08f) : 0x4E724E;
      }
      case TINT_BIRCH: {
        return hasTex ? MapColorShading.scaleRgb(
            MapColorShading.tintRgb(base, BIRCH_FOLIAGE_RGB), 1.08f) : 0x6D8F4B;
      }
      case TINT_LILY: {
        return hasTex ? MapColorShading.tintRgb(base, LILY_PAD_RGB) : LILY_PAD_RGB;
      }
      default:
        return base & 0xFFFFFF;
    }
  }

  private static boolean isSkippedClutter(String p) {
    if ("air".equals(p) || "cave_air".equals(p) || "void_air".equals(p)
        || "barrier".equals(p) || "structure_void".equals(p) || "light".equals(p)) {
      return true;
    }
    if ("grass".equals(p) || "short_grass".equals(p) || "tall_grass".equals(p)
        || "short_dry_grass".equals(p) || "tall_dry_grass".equals(p)
        || "fern".equals(p) || "large_fern".equals(p) || "dead_bush".equals(p)
        || "bush".equals(p) || "firefly_bush".equals(p) || "leaf_litter".equals(p)
        || "seagrass".equals(p) || "tall_seagrass".equals(p)
        || "kelp".equals(p) || "kelp_plant".equals(p)
        || "sugar_cane".equals(p) || "bamboo_sapling".equals(p)
        || "cobweb".equals(p) || "vine".equals(p) || "glow_lichen".equals(p)
        || "sculk_vein".equals(p) || "hanging_roots".equals(p)
        || "string".equals(p) || "tripwire".equals(p) || "tripwire_hook".equals(p)
        || "lever".equals(p) || "redstone_wire".equals(p) || "ladder".equals(p)
        || "chain".equals(p) || "iron_bars".equals(p) || "flower_pot".equals(p)
        || "sea_pickle".equals(p) || "frogspawn".equals(p)
        || "crimson_roots".equals(p) || "warped_roots".equals(p) || "nether_sprouts".equals(p)
        || "brown_mushroom".equals(p) || "red_mushroom".equals(p)
        || "crimson_fungus".equals(p) || "warped_fungus".equals(p)
        || "cave_vines".equals(p) || "cave_vines_plant".equals(p)
        || "weeping_vines".equals(p) || "weeping_vines_plant".equals(p)
        || "twisting_vines".equals(p) || "twisting_vines_plant".equals(p)
        || "spore_blossom".equals(p) || "pointed_dripstone".equals(p)
        || "small_dripleaf".equals(p) || "hanging_moss".equals(p) || "pale_hanging_moss".equals(p)) {
      return true;
    }
    return p.contains("torch") || p.endsWith("_button") || p.endsWith("_rail") || "rail".equals(p)
        || p.endsWith("_sapling") || p.endsWith("_sign") || p.endsWith("_banner")
        || p.endsWith("_pressure_plate") || p.endsWith("_candle") || p.endsWith("lantern")
        || p.endsWith("_bud") || p.endsWith("_cluster") || p.startsWith("potted_");
  }

  private static boolean isFlowerOverlay(String p) {
    return "dandelion".equals(p) || "poppy".equals(p) || "blue_orchid".equals(p)
        || "allium".equals(p) || "azure_bluet".equals(p) || "oxeye_daisy".equals(p)
        || "cornflower".equals(p) || "lily_of_the_valley".equals(p) || "wither_rose".equals(p)
        || "torchflower".equals(p) || "sunflower".equals(p) || "lilac".equals(p)
        || "rose_bush".equals(p) || "peony".equals(p) || "pitcher_plant".equals(p)
        || "pink_petals".equals(p) || "wildflowers".equals(p)
        || "open_eyeblossom".equals(p) || "closed_eyeblossom".equals(p)
        || "cactus_flower".equals(p) || p.endsWith("_tulip");
  }

  private static boolean isTranslucentSurface(String p) {
    return p.contains("glass") || "ice".equals(p) || "frosted_ice".equals(p)
        || "honey_block".equals(p) || "slime_block".equals(p);
  }

  private static byte tintModeFor(String p, int mapColorId) {
    if ("grass_block".equals(p)) return TINT_GRASS;
    if ("spruce_leaves".equals(p)) return TINT_SPRUCE;
    if ("birch_leaves".equals(p)) return TINT_BIRCH;
    if ("cherry_leaves".equals(p) || "pale_oak_leaves".equals(p)
        || "azalea_leaves".equals(p) || "flowering_azalea_leaves".equals(p)) {
      return TINT_NONE;
    }
    if (p.endsWith("_leaves")) return TINT_FOLIAGE;
    if (mapColorId == 1 && (p.contains("grass") || p.contains("turf"))) return TINT_GRASS;
    return TINT_NONE;
  }

  private static int sampleBlockTexture(String ns, String path, TextureReader reader) {
    if (reader == null) return -1;
    String[] candidates = textureCandidates(path);
    for (int i = 0; i < candidates.length; i++) {
      String c = candidates[i];
      if (c == null || c.isEmpty()) continue;
      byte[] bytes = reader.loadResourceBytes(ns + ":textures/block/" + c + ".png");
      if (bytes != null && bytes.length >= 24) {
        MapEntityIconComposer.DecodedImage img = MapEntityIconComposer.decodePng(bytes);
        if (img != null) {
          int rgb = averageTopRgb(img);
          if (rgb >= 0) return rgb;
        }
      }
    }
    return -1;
  }

  private static String[] textureCandidates(String p) {
    if ("grass_block".equals(p)) return new String[] { "grass_block_top" };
    if ("dirt_path".equals(p)) return new String[] { "dirt_path_top" };
    if ("podzol".equals(p)) return new String[] { "podzol_top" };
    if ("mycelium".equals(p)) return new String[] { "mycelium_top" };
    if ("farmland".equals(p)) return new String[] { "farmland_moist", "farmland" };
    if ("snow_block".equals(p)) return new String[] { "snow" };
    if ("sandstone".equals(p) || "smooth_sandstone".equals(p) || "chiseled_sandstone".equals(p)
        || "cut_sandstone".equals(p)) {
      return new String[] { "sandstone_top" };
    }
    if ("red_sandstone".equals(p) || "smooth_red_sandstone".equals(p)
        || "chiseled_red_sandstone".equals(p) || "cut_red_sandstone".equals(p)) {
      return new String[] { "red_sandstone_top" };
    }
    if ("quartz_block".equals(p) || "smooth_quartz".equals(p)) {
      return new String[] { "quartz_block_top" };
    }
    if ("smooth_stone".equals(p)) return new String[] { "smooth_stone" };
    if ("stone_slab".equals(p)) return new String[] { "stone" };
    if ("smooth_stone_slab".equals(p)) return new String[] { "smooth_stone" };
    if ("petrified_oak_slab".equals(p)) return new String[] { "oak_planks" };
    if ("crimson_nylium".equals(p)) return new String[] { "crimson_nylium" };
    if ("warped_nylium".equals(p)) return new String[] { "warped_nylium" };
    if ("crimson_hyphae".equals(p)) return new String[] { "crimson_stem" };
    if ("warped_hyphae".equals(p)) return new String[] { "warped_stem" };
    if ("stripped_crimson_hyphae".equals(p)) return new String[] { "stripped_crimson_stem" };
    if ("stripped_warped_hyphae".equals(p)) return new String[] { "stripped_warped_stem" };
    if ("bamboo_block".equals(p)) return new String[] { "bamboo_block_top", "bamboo_block" };
    if ("stripped_bamboo_block".equals(p)) {
      return new String[] { "stripped_bamboo_block_top", "stripped_bamboo_block" };
    }
    if ("wheat".equals(p)) return new String[] { "wheat_stage7", "wheat_stage6" };
    if ("carrots".equals(p)) return new String[] { "carrots_stage3" };
    if ("potatoes".equals(p)) return new String[] { "potatoes_stage3" };
    if ("beetroots".equals(p)) return new String[] { "beetroots_stage3" };
    if ("sunflower".equals(p)) return new String[] { "sunflower_front", "sunflower_top" };
    if ("lilac".equals(p)) return new String[] { "lilac_top" };
    if ("rose_bush".equals(p)) return new String[] { "rose_bush_top" };
    if ("peony".equals(p)) return new String[] { "peony_top" };
    if ("pitcher_plant".equals(p)) return new String[] { "pitcher_crop_top_stage_4" };
    if ("magma_block".equals(p)) return new String[] { "magma" };
    if ("infested_stone".equals(p)) return new String[] { "stone" };
    if ("infested_cobblestone".equals(p)) return new String[] { "cobblestone" };
    if ("infested_stone_bricks".equals(p)) return new String[] { "stone_bricks" };
    if ("infested_deepslate".equals(p)) return new String[] { "deepslate_top", "deepslate" };
    if ("waxed_copper_block".equals(p)) return new String[] { "copper_block" };
    if (p.startsWith("waxed_")) {
      String un = p.substring(6);
      return new String[] { un + "_top", un };
    }
    if (p.endsWith("_wood")) {
      String base = p.substring(0, p.length() - 5);
      return new String[] { base + "_log", p };
    }
    if (p.endsWith("_carpet")) {
      String col = p.substring(0, p.length() - 7);
      if ("moss".equals(col)) return new String[] { "moss_block" };
      if ("pale_moss".equals(col)) return new String[] { "pale_moss_block" };
      return new String[] { col + "_wool", p };
    }
    if (p.endsWith("_bed")) {
      String col = p.substring(0, p.length() - 4);
      return new String[] { col + "_wool" };
    }
    if (p.endsWith("_glazed_terracotta")) return new String[] { p };
    if (p.endsWith("_concrete_powder")) return new String[] { p };
    if (p.endsWith("_stained_glass_pane")) {
      return new String[] { p.substring(0, p.length() - 5) };
    }
    if ("glass_pane".equals(p)) return new String[] { "glass" };

    String base = stripVariantSuffix(p);
    if (base != null) {
      return new String[] {
        base + "_planks",
        base + "_top",
        base + "s",
        base + "_block",
        base
      };
    }

    return new String[] { p + "_top", p, p + "_side" };
  }

  private static String stripVariantSuffix(String p) {
    String[] suffixes = { "_fence_gate", "_stairs", "_slab", "_wall", "_fence", "_trapdoor", "_door" };
    for (int i = 0; i < suffixes.length; i++) {
      String suf = suffixes[i];
      if (p.endsWith(suf) && p.length() > suf.length()) {
        String root = p.substring(0, p.length() - suf.length());
        if (root.startsWith("waxed_")) root = root.substring(6);
        return root;
      }
    }
    return null;
  }

  public static int averageTopRgb(MapEntityIconComposer.DecodedImage img) {
    if (img == null || img.argb == null || img.width <= 0 || img.height <= 0) return -1;
    int w = img.width;
    int h = img.height > w ? w : img.height;
    double sumR = 0.0, sumG = 0.0, sumB = 0.0, weightSum = 0.0;
    for (int y = 0; y < h; y++) {
      int row = y * w;
      for (int x = 0; x < w; x++) {
        int argb = img.argb[row + x];
        int a = (argb >>> 24) & 0xFF;
        if (a < 48) continue;
        double wt = a / 255.0;
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        sumR += Math.pow(r, 1.6) * wt;
        sumG += Math.pow(g, 1.6) * wt;
        sumB += Math.pow(b, 1.6) * wt;
        weightSum += wt;
      }
    }
    if (weightSum < 1e-4) return -1;
    double inv = 1.0 / weightSum;
    int r = clampByte((int) Math.round(Math.pow(sumR * inv, 1.0 / 1.6) * 255.0));
    int g = clampByte((int) Math.round(Math.pow(sumG * inv, 1.0 / 1.6) * 255.0));
    int b = clampByte((int) Math.round(Math.pow(sumB * inv, 1.0 / 1.6) * 255.0));
    return (r << 16) | (g << 8) | b;
  }

  private static int clampByte(int v) {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
  }
}
