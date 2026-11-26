package dev.nightclient.renderer.map;

import dev.nightclient.api.MapRadarModels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MapEntityTextures {
  private static final String PREFIX = "minecraft:textures/entity/";

  private MapEntityTextures() {}

  public interface ResourceProbe {
    int[] probeTextureSize(String textureId);

    boolean hasItem(String itemId);
  }

  public static final class ResolvedIcon {
    public static final ResolvedIcon EMPTY = new ResolvedIcon("", 0f, 0f, 0f, 0f, "", null);

    public final String textureId;
    public final float u0;
    public final float v0;
    public final float u1;
    public final float v1;
    public final String itemIconId;
    public final MapRadarModels.OverlayLayer[] overlays;

    public ResolvedIcon(String textureId, float u0, float v0, float u1, float v1,
        String itemIconId, MapRadarModels.OverlayLayer[] overlays) {
      this.textureId = textureId == null ? "" : textureId;
      this.u0 = u0;
      this.v0 = v0;
      this.u1 = u1;
      this.v1 = v1;
      this.itemIconId = itemIconId == null ? "" : itemIconId;
      this.overlays = overlays;
    }

    public boolean hasItemIcon() {
      return !itemIconId.isEmpty();
    }

    public boolean hasFaceTexture() {
      return !textureId.isEmpty() && u1 > u0 && v1 > v0;
    }
  }

  static final class FaceCandidate {
    final String relPath;
    final int x, y, w, h, tw, th;
    final int altX, altY, altW, altH, altTw, altTh;

    FaceCandidate(String relPath, int x, int y, int w, int h, int tw, int th) {
      this(relPath, x, y, w, h, tw, th, -1, -1, -1, -1, -1, -1);
    }

    FaceCandidate(String relPath, int x, int y, int w, int h, int tw, int th,
        int altX, int altY, int altW, int altH, int altTw, int altTh) {
      this.relPath = relPath;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.tw = tw;
      this.th = th;
      this.altX = altX;
      this.altY = altY;
      this.altW = altW;
      this.altH = altH;
      this.altTw = altTw;
      this.altTh = altTh;
    }
  }

  static final class OverlaySpec {
    final String[] relPaths;
    final int x, y, w, h, tw, th;
    final float dx0, dy0, dx1, dy1;

    OverlaySpec(String[] relPaths, int x, int y, int w, int h, int tw, int th,
        float dx0, float dy0, float dx1, float dy1) {
      this.relPaths = relPaths;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.tw = tw;
      this.th = th;
      this.dx0 = dx0;
      this.dy0 = dy0;
      this.dx1 = dx1;
      this.dy1 = dy1;
    }
  }

  static final class MobFaceDef {
    final FaceCandidate[] candidates;
    final OverlaySpec[] overlays;

    MobFaceDef(FaceCandidate[] candidates, OverlaySpec[] overlays) {
      this.candidates = candidates;
      this.overlays = overlays;
    }
  }

  private static final Map<String, MobFaceDef> FACES = buildFaceCatalog();

  public static List<String> knownMobTypes() {
    List<String> keys = new ArrayList<String>(FACES.keySet());
    Collections.sort(keys);
    return keys;
  }

  public static String shortId(String entityType) {
    if (entityType == null || entityType.isEmpty()) return "";
    String s = entityType.trim().toLowerCase(Locale.ROOT);
    int colon = s.indexOf(':');
    return colon >= 0 ? s.substring(colon + 1) : s;
  }

  public static boolean isTechnicalExcludedEntity(String entityType) {
    String id = shortId(entityType);
    return id.isEmpty()
        || "text_display".equals(id)
        || "block_display".equals(id)
        || "item_display".equals(id)
        || "interaction".equals(id)
        || "marker".equals(id);
  }

  public static boolean isEligibleNonLivingEntity(String entityType) {
    if (isTechnicalExcludedEntity(entityType)) return false;
    String id = shortId(entityType);
    if (id.contains("boat") || id.contains("raft") || id.contains("minecart")) return true;
    return "item_frame".equals(id)
        || "glow_item_frame".equals(id)
        || "painting".equals(id)
        || "leash_knot".equals(id)
        || "falling_block".equals(id)
        || "experience_orb".equals(id)
        || "ender_pearl".equals(id)
        || "trident".equals(id)
        || "arrow".equals(id)
        || "spectral_arrow".equals(id)
        || "snowball".equals(id)
        || "egg".equals(id)
        || "firework_rocket".equals(id)
        || "wind_charge".equals(id)
        || "breeze_wind_charge".equals(id)
        || "fireball".equals(id)
        || "small_fireball".equals(id)
        || "dragon_fireball".equals(id)
        || "wither_skull".equals(id)
        || "eye_of_ender".equals(id)
        || "potion".equals(id)
        || "splash_potion".equals(id)
        || "lingering_potion".equals(id)
        || "experience_bottle".equals(id)
        || "fishing_bobber".equals(id)
        || "shulker_bullet".equals(id)
        || "evoker_fangs".equals(id)
        || "llama_spit".equals(id)
        || "lightning_bolt".equals(id)
        || "area_effect_cloud".equals(id)
        || "ominous_item_spawner".equals(id)
        || "cushion".equals(id);
  }

  public static int defaultTintForEntity(String entityType) {
    String id = shortId(entityType);
    if ("experience_orb".equals(id)) return 0x90FF30;
    return 0xFFFFFF;
  }

  public static String itemIconForEntity(String entityType, int kind) {
    if (kind == MapRadarModels.MOB_TNT) return "minecraft:tnt";
    if (kind == MapRadarModels.MOB_CRYSTAL) return "minecraft:end_crystal";
    String id = shortId(entityType);
    if (id.isEmpty()) return "";
    if ("tnt".equals(id)) return "minecraft:tnt";
    if ("end_crystal".equals(id)) return "minecraft:end_crystal";
    if ("armor_stand".equals(id)) return "minecraft:armor_stand";
    if ("item_frame".equals(id)) return "minecraft:item_frame";
    if ("glow_item_frame".equals(id)) return "minecraft:glow_item_frame";
    if ("painting".equals(id)) return "minecraft:painting";
    if ("leash_knot".equals(id)) return "minecraft:lead";
    if ("falling_block".equals(id)) return "minecraft:sand";
    if ("cushion".equals(id)) return "minecraft:white_wool";

    if ("boat".equals(id)) return "minecraft:oak_boat";
    if ("chest_boat".equals(id)) return "minecraft:oak_chest_boat";
    if (id.endsWith("_boat") || id.endsWith("_raft")) return "minecraft:" + id;

    if ("spawner_minecart".equals(id)) return "minecraft:minecart";
    if ("minecart".equals(id) || id.endsWith("_minecart")) return "minecraft:" + id;

    if ("ender_pearl".equals(id)) return "minecraft:ender_pearl";
    if ("trident".equals(id)) return "minecraft:trident";
    if ("arrow".equals(id)) return "minecraft:arrow";
    if ("spectral_arrow".equals(id)) return "minecraft:spectral_arrow";
    if ("snowball".equals(id) || "llama_spit".equals(id)) return "minecraft:snowball";
    if ("egg".equals(id)) return "minecraft:egg";
    if ("firework_rocket".equals(id)) return "minecraft:firework_rocket";
    if ("wind_charge".equals(id) || "breeze_wind_charge".equals(id)) return "minecraft:wind_charge";
    if ("fireball".equals(id) || "small_fireball".equals(id)) return "minecraft:fire_charge";
    if ("dragon_fireball".equals(id)) return "minecraft:dragon_breath";
    if ("wither_skull".equals(id)) return "minecraft:wither_skeleton_skull";
    if ("eye_of_ender".equals(id)) return "minecraft:ender_eye";
    if ("potion".equals(id) || "splash_potion".equals(id)) return "minecraft:splash_potion";
    if ("lingering_potion".equals(id) || "area_effect_cloud".equals(id)) {
      return "minecraft:lingering_potion";
    }
    if ("experience_bottle".equals(id)) return "minecraft:experience_bottle";
    if ("fishing_bobber".equals(id)) return "minecraft:fishing_rod";
    if ("shulker_bullet".equals(id)) return "minecraft:shulker_shell";
    if ("evoker_fangs".equals(id)) return "minecraft:totem_of_undying";
    if ("lightning_bolt".equals(id)) return "minecraft:lightning_rod";
    if ("ominous_item_spawner".equals(id)) return "minecraft:ominous_trial_key";
    return "";
  }

  public static ResolvedIcon resolve(String entityType, int kind, ResourceProbe probe) {
    if (probe == null) return ResolvedIcon.EMPTY;

    String itemId = itemIconForEntity(entityType, kind);
    if (!itemId.isEmpty()) {
      if (probe.hasItem(itemId)) {
        return new ResolvedIcon("", 0f, 0f, 0f, 0f, itemId, null);
      }
      String fallbackItem = fallbackItemFor(itemId);
      if (!fallbackItem.isEmpty() && probe.hasItem(fallbackItem)) {
        return new ResolvedIcon("", 0f, 0f, 0f, 0f, fallbackItem, null);
      }
      return ResolvedIcon.EMPTY;
    }

    String id = shortId(entityType);
    MobFaceDef def = FACES.get(id);
    if (def == null) return ResolvedIcon.EMPTY;

    FaceCandidate matched = null;
    String matchedTexId = null;
    int[] matchedSize = null;
    for (int i = 0; i < def.candidates.length; i++) {
      FaceCandidate cand = def.candidates[i];
      String fullTex = PREFIX + cand.relPath;
      int[] size = probe.probeTextureSize(fullTex);
      if (size != null && size.length >= 2 && size[0] > 0 && size[1] > 0) {
        matched = cand;
        matchedTexId = fullTex;
        matchedSize = size;
        break;
      }
    }
    if (matched == null) return ResolvedIcon.EMPTY;

    float[] baseUv = computeUv(matched, matchedSize[0], matchedSize[1]);
    MapRadarModels.OverlayLayer[] resolvedOverlays = null;
    if (def.overlays != null && def.overlays.length > 0) {
      List<MapRadarModels.OverlayLayer> list =
          new ArrayList<MapRadarModels.OverlayLayer>(def.overlays.length);
      for (int i = 0; i < def.overlays.length; i++) {
        OverlaySpec ov = def.overlays[i];
        String ovTex = null;
        int[] ovSize = null;
        if (ov.relPaths == null || ov.relPaths.length == 0) {
          ovTex = matchedTexId;
          ovSize = matchedSize;
        } else {
          for (int p = 0; p < ov.relPaths.length; p++) {
            String candidateTex = PREFIX + ov.relPaths[p];
            int[] sz = probe.probeTextureSize(candidateTex);
            if (sz != null && sz.length >= 2 && sz[0] > 0 && sz[1] > 0) {
              ovTex = candidateTex;
              ovSize = sz;
              break;
            }
          }
        }
        if (ovTex == null || ovSize == null) continue;
        float[] ovUv = computeOverlayUv(ov, baseUv, ovSize[0], ovSize[1]);
        list.add(new MapRadarModels.OverlayLayer(
            ovTex, ovUv[0], ovUv[1], ovUv[2], ovUv[3], ov.dx0, ov.dy0, ov.dx1, ov.dy1, 0xFFFFFF));
      }
      if (!list.isEmpty()) {
        resolvedOverlays = list.toArray(new MapRadarModels.OverlayLayer[list.size()]);
      }
    }

    return new ResolvedIcon(
        matchedTexId, baseUv[0], baseUv[1], baseUv[2], baseUv[3], "", resolvedOverlays);
  }

  private static String fallbackItemFor(String itemId) {
    if (itemId.endsWith("_chest_boat") || itemId.endsWith("_chest_raft")) {
      return "minecraft:oak_chest_boat";
    }
    if (itemId.endsWith("_boat") || itemId.endsWith("_raft")) {
      return "minecraft:oak_boat";
    }
    if (itemId.endsWith("_minecart")) {
      return "minecraft:minecart";
    }
    if ("minecraft:wind_charge".equals(itemId)) {
      return "minecraft:snowball";
    }
    if ("minecraft:ominous_trial_key".equals(itemId)) {
      return "minecraft:nether_star";
    }
    if ("minecraft:lightning_rod".equals(itemId)) {
      return "minecraft:blaze_rod";
    }
    return "";
  }

  private static float[] computeUv(FaceCandidate c, int actualW, int actualH) {
    int bx = c.x, by = c.y, bw = c.w, bh = c.h, tw = c.tw, th = c.th;
    if (c.altTw > 0 && c.altTh > 0) {
      boolean matchesPrimary = (long) actualW * th == (long) actualH * tw;
      boolean matchesAlt = (long) actualW * c.altTh == (long) actualH * c.altTw;
      if (!matchesPrimary && matchesAlt) {
        bx = c.altX;
        by = c.altY;
        bw = c.altW;
        bh = c.altH;
        tw = c.altTw;
        th = c.altTh;
      }
    } else if (tw == 64 && th == 32 && actualW == actualH) {
      th = 64;
    } else if (tw == 64 && th == 64 && actualW == actualH * 2) {
      th = 32;
    }
    if (c.tw == 32 && c.th == 32 && c.x == 2 && c.y == 9 && actualW == 64 && actualH == 64) {
      bx = 6;
      by = 6;
      bw = 6;
      bh = 6;
      tw = 64;
      th = 64;
    }
    return new float[] {
        bx / (float) tw,
        by / (float) th,
        (bx + bw) / (float) tw,
        (by + bh) / (float) th
    };
  }

  private static float[] computeOverlayUv(OverlaySpec ov, float[] baseUv, int actualW, int actualH) {
    if (ov.w <= 0 || ov.h <= 0 || ov.tw <= 0 || ov.th <= 0) {
      return baseUv;
    }
    int th = ov.th;
    if (ov.tw == 64 && ov.th == 32 && actualW == actualH) {
      th = 64;
    } else if (ov.tw == 64 && ov.th == 64 && actualW == actualH * 2) {
      th = 32;
    }
    return new float[] {
        ov.x / (float) ov.tw,
        ov.y / (float) th,
        (ov.x + ov.w) / (float) ov.tw,
        (ov.y + ov.h) / (float) th
    };
  }

  private static FaceCandidate fc(String path, int x, int y, int w, int h, int tw, int th) {
    return new FaceCandidate(path, x, y, w, h, tw, th);
  }

  private static OverlaySpec fullOverlay(String... paths) {
    return new OverlaySpec(paths, 0, 0, 0, 0, 0, 0, 0f, 0f, 1f, 1f);
  }

  private static OverlaySpec subOverlay(int x, int y, int w, int h, int tw, int th,
      float dx0, float dy0, float dx1, float dy1) {
    return new OverlaySpec(null, x, y, w, h, tw, th, dx0, dy0, dx1, dy1);
  }

  private static void put(Map<String, MobFaceDef> m, String id, FaceCandidate... cands) {
    m.put(id, new MobFaceDef(cands, null));
  }

  private static void putOv(Map<String, MobFaceDef> m, String id, FaceCandidate[] cands,
      OverlaySpec... ovs) {
    m.put(id, new MobFaceDef(cands, ovs));
  }

  private static Map<String, MobFaceDef> buildFaceCatalog() {
    Map<String, MobFaceDef> m = new HashMap<String, MobFaceDef>(128);

    put(m, "zombie", fc("zombie/zombie.png", 8, 8, 8, 8, 64, 64));
    put(m, "giant", fc("zombie/zombie.png", 8, 8, 8, 8, 64, 64));
    put(m, "husk", fc("zombie/husk.png", 8, 8, 8, 8, 64, 64));
    putOv(m, "drowned",
        new FaceCandidate[] { fc("zombie/drowned.png", 8, 8, 8, 8, 64, 64) },
        fullOverlay("zombie/drowned_outer_layer.png"));
    put(m, "zombie_villager", fc("zombie_villager/zombie_villager.png", 8, 8, 8, 10, 64, 64));
    put(m, "skeleton", fc("skeleton/skeleton.png", 8, 8, 8, 8, 64, 32));
    put(m, "wither_skeleton", fc("skeleton/wither_skeleton.png", 8, 8, 8, 8, 64, 32));
    putOv(m, "stray",
        new FaceCandidate[] { fc("skeleton/stray.png", 8, 8, 8, 8, 64, 32) },
        fullOverlay("skeleton/stray_overlay.png"));
    putOv(m, "bogged",
        new FaceCandidate[] { fc("skeleton/bogged.png", 8, 8, 8, 8, 64, 32) },
        fullOverlay("skeleton/bogged_overlay.png"));
    put(m, "parched", fc("skeleton/parched.png", 8, 8, 8, 8, 64, 64));
    put(m, "creeper", fc("creeper/creeper.png", 8, 8, 8, 8, 64, 32));
    putOv(m, "spider",
        new FaceCandidate[] { fc("spider/spider.png", 40, 12, 8, 8, 64, 32) },
        fullOverlay("spider/spider_eyes.png", "spider_eyes.png"));
    putOv(m, "cave_spider",
        new FaceCandidate[] { fc("spider/cave_spider.png", 40, 12, 8, 8, 64, 32) },
        fullOverlay("spider/spider_eyes.png", "spider_eyes.png"));
    putOv(m, "enderman",
        new FaceCandidate[] { fc("enderman/enderman.png", 8, 8, 8, 8, 64, 32) },
        fullOverlay("enderman/enderman_eyes.png"));
    put(m, "blaze",
        fc("blaze/blaze.png", 8, 8, 8, 8, 64, 32),
        fc("blaze.png", 8, 8, 8, 8, 64, 32));
    put(m, "ghast", fc("ghast/ghast.png", 16, 16, 16, 16, 64, 32));
    put(m, "happy_ghast", fc("ghast/happy_ghast.png", 16, 16, 16, 16, 64, 64));
    putOv(m, "slime",
        new FaceCandidate[] { fc("slime/slime.png", 8, 8, 8, 8, 64, 32) },
        subOverlay(34, 2, 2, 2, 64, 32, 0.16f, 0.25f, 0.40f, 0.49f),
        subOverlay(34, 6, 2, 2, 64, 32, 0.60f, 0.25f, 0.84f, 0.49f),
        subOverlay(33, 9, 1, 1, 64, 32, 0.44f, 0.62f, 0.62f, 0.78f));
    put(m, "magma_cube",
        new FaceCandidate("slime/magmacube.png", 8, 32, 8, 8, 64, 64, 32, 24, 8, 8, 64, 32));
    put(m, "sulfur_cube", fc("sulfur_cube/sulfur_cube_outer.png", 18, 18, 18, 18, 128, 128));

    put(m, "piglin", fc("piglin/piglin.png", 8, 8, 10, 8, 64, 64));
    put(m, "piglin_brute", fc("piglin/piglin_brute.png", 8, 8, 10, 8, 64, 64));
    put(m, "zombified_piglin", fc("piglin/zombified_piglin.png", 8, 8, 10, 8, 64, 64));
    put(m, "hoglin", fc("hoglin/hoglin.png", 80, 1, 14, 14, 128, 64));
    put(m, "zoglin", fc("hoglin/zoglin.png", 80, 1, 14, 14, 128, 64));
    put(m, "strider", fc("strider/strider.png", 16, 16, 16, 14, 64, 128));

    put(m, "villager", fc("villager/villager.png", 8, 8, 8, 10, 64, 64));
    put(m, "wandering_trader",
        fc("wandering_trader/wandering_trader.png", 8, 8, 8, 10, 64, 64),
        fc("wandering_trader.png", 8, 8, 8, 10, 64, 64));
    put(m, "pillager", fc("illager/pillager.png", 8, 8, 8, 10, 64, 64));
    put(m, "vindicator", fc("illager/vindicator.png", 8, 8, 8, 10, 64, 64));
    put(m, "evoker", fc("illager/evoker.png", 8, 8, 8, 10, 64, 64));
    put(m, "illusioner", fc("illager/illusioner.png", 8, 8, 8, 10, 64, 64));
    put(m, "witch",
        fc("witch/witch.png", 8, 8, 8, 10, 64, 128),
        fc("witch.png", 8, 8, 8, 10, 64, 128));
    put(m, "ravager", fc("illager/ravager.png", 16, 16, 16, 20, 128, 128));
    put(m, "vex", fc("illager/vex.png", 5, 5, 5, 5, 32, 32));
    put(m, "allay", fc("allay/allay.png", 5, 5, 5, 5, 32, 32));

    putOv(m, "guardian",
        new FaceCandidate[] {
            fc("guardian/guardian.png", 16, 16, 12, 12, 64, 64),
            fc("guardian.png", 16, 16, 12, 12, 64, 64)
        },
        subOverlay(9, 1, 2, 2, 64, 64, 0.34f, 0.34f, 0.66f, 0.66f));
    putOv(m, "elder_guardian",
        new FaceCandidate[] {
            fc("guardian/guardian_elder.png", 16, 16, 12, 12, 64, 64),
            fc("guardian_elder.png", 16, 16, 12, 12, 64, 64)
        },
        subOverlay(9, 1, 2, 2, 64, 64, 0.34f, 0.34f, 0.66f, 0.66f));
    putOv(m, "phantom",
        new FaceCandidate[] {
            fc("phantom/phantom.png", 5, 5, 7, 3, 64, 64),
            fc("phantom.png", 5, 5, 7, 3, 64, 64)
        },
        fullOverlay("phantom/phantom_eyes.png", "phantom_eyes.png"));
    put(m, "shulker", fc("shulker/shulker.png", 6, 58, 6, 6, 64, 64));
    put(m, "silverfish",
        fc("silverfish/silverfish.png", 2, 2, 6, 4, 64, 32),
        fc("silverfish.png", 2, 2, 6, 4, 64, 32));
    put(m, "endermite",
        fc("endermite/endermite.png", 2, 2, 4, 3, 64, 32),
        fc("endermite.png", 2, 2, 4, 3, 64, 32));
    putOv(m, "warden",
        new FaceCandidate[] { fc("warden/warden.png", 10, 42, 16, 16, 128, 128) },
        fullOverlay("warden/warden_bioluminescent_layer.png"));
    put(m, "wither", fc("wither/wither.png", 8, 8, 8, 8, 64, 64));
    putOv(m, "ender_dragon",
        new FaceCandidate[] { fc("enderdragon/dragon.png", 128, 46, 16, 16, 256, 256) },
        fullOverlay("enderdragon/dragon_eyes.png"));
    putOv(m, "breeze",
        new FaceCandidate[] { fc("breeze/breeze.png", 8, 8, 8, 8, 32, 32) },
        fullOverlay("breeze/breeze_eyes.png"));
    putOv(m, "creaking",
        new FaceCandidate[] { fc("creaking/creaking.png", 6, 6, 6, 10, 64, 64) },
        fullOverlay("creaking/creaking_eyes.png"));
    putOv(m, "creaking_transient",
        new FaceCandidate[] { fc("creaking/creaking.png", 6, 6, 6, 10, 64, 64) },
        fullOverlay("creaking/creaking_eyes.png"));

    putOv(m, "copper_golem",
        new FaceCandidate[] { fc("copper_golem/copper_golem.png", 10, 10, 8, 5, 64, 64) },
        fullOverlay("copper_golem/copper_golem_eyes.png"));
    put(m, "iron_golem", fc("iron_golem/iron_golem.png", 8, 8, 8, 10, 128, 128));
    put(m, "snow_golem",
        fc("snow_golem/snow_golem.png", 8, 8, 8, 8, 64, 64),
        fc("snow_golem.png", 8, 8, 8, 8, 64, 64));

    putOv(m, "pig",
        new FaceCandidate[] {
            fc("pig/pig_temperate.png", 8, 8, 8, 8, 64, 64),
            fc("pig/temperate_pig.png", 8, 8, 8, 8, 64, 64),
            fc("pig/pig.png", 8, 8, 8, 8, 64, 32)
        },
        subOverlay(17, 17, 4, 3, 64, 64, 0.25f, 0.50f, 0.75f, 0.88f));
    put(m, "cow",
        fc("cow/cow_temperate.png", 6, 6, 8, 8, 64, 64),
        fc("cow/temperate_cow.png", 6, 6, 8, 8, 64, 64),
        fc("cow/cow.png", 6, 6, 8, 8, 64, 32));
    put(m, "mooshroom",
        fc("cow/mooshroom_red.png", 6, 6, 8, 8, 64, 64),
        fc("cow/red_mooshroom.png", 6, 6, 8, 8, 64, 32));
    put(m, "sheep", fc("sheep/sheep.png", 8, 8, 6, 6, 64, 32));
    putOv(m, "chicken",
        new FaceCandidate[] {
            fc("chicken/chicken_temperate.png", 3, 3, 4, 6, 64, 32),
            fc("chicken/temperate_chicken.png", 3, 3, 4, 6, 64, 32),
            fc("chicken.png", 3, 3, 4, 6, 64, 32)
        },
        subOverlay(16, 2, 4, 2, 64, 32, 0.14f, 0.44f, 0.86f, 0.72f));
    put(m, "wolf", fc("wolf/wolf.png", 4, 4, 6, 6, 64, 32));
    put(m, "cat",
        fc("cat/cat_tabby.png", 5, 5, 5, 4, 64, 32),
        fc("cat/tabby.png", 5, 5, 5, 4, 64, 32));
    put(m, "ocelot", fc("cat/ocelot.png", 5, 5, 5, 4, 64, 32));
    put(m, "fox", fc("fox/fox.png", 7, 11, 8, 6, 48, 32));
    put(m, "horse", fc("horse/horse_chestnut.png", 0, 20, 7, 5, 64, 64));
    put(m, "donkey", fc("horse/donkey.png", 0, 20, 7, 5, 64, 64));
    put(m, "mule", fc("horse/mule.png", 0, 20, 7, 5, 64, 64));
    put(m, "skeleton_horse", fc("horse/horse_skeleton.png", 0, 20, 7, 5, 64, 64));
    put(m, "zombie_horse", fc("horse/horse_zombie.png", 0, 20, 7, 5, 64, 64));
    put(m, "llama",
        fc("llama/llama_creamy.png", 6, 20, 8, 8, 128, 64),
        fc("llama/creamy.png", 6, 20, 8, 8, 128, 64));
    put(m, "trader_llama",
        fc("llama/llama_brown.png", 6, 20, 8, 8, 128, 64),
        fc("llama/brown.png", 6, 20, 8, 8, 128, 64));
    put(m, "camel", fc("camel/camel.png", 28, 7, 7, 7, 128, 128));
    put(m, "camel_husk", fc("camel/camel_husk.png", 28, 7, 7, 7, 128, 128));
    put(m, "panda", fc("panda/panda.png", 9, 15, 13, 10, 64, 64));
    put(m, "polar_bear", fc("bear/polarbear.png", 7, 7, 7, 7, 128, 64));
    put(m, "goat", fc("goat/goat.png", 44, 56, 5, 7, 64, 64));
    put(m, "rabbit",
        fc("rabbit/rabbit_brown.png", 5, 21, 5, 5, 64, 64),
        fc("rabbit/brown.png", 37, 5, 5, 4, 64, 32));
    put(m, "killer_bunny",
        fc("rabbit/rabbit_caerbannog.png", 5, 21, 5, 5, 64, 64),
        fc("rabbit/caerbannog.png", 37, 5, 5, 4, 64, 32));
    put(m, "bee", fc("bee/bee.png", 10, 10, 7, 7, 64, 64));
    put(m, "bat",
        fc("bat/bat.png", 2, 9, 4, 3, 32, 32),
        fc("bat.png", 2, 9, 4, 3, 32, 32));
    put(m, "parrot", fc("parrot/parrot_red_blue.png", 2, 4, 4, 4, 32, 32));
    put(m, "axolotl", fc("axolotl/axolotl_lucy.png", 5, 6, 8, 5, 64, 64));
    putOv(m, "frog",
        new FaceCandidate[] {
            fc("frog/frog_temperate.png", 9, 22, 7, 3, 48, 48),
            fc("frog/temperate_frog.png", 9, 22, 7, 3, 48, 48)
        },
        subOverlay(3, 3, 3, 2, 48, 48, 0.08f, 0.10f, 0.42f, 0.48f),
        subOverlay(3, 8, 3, 2, 48, 48, 0.58f, 0.10f, 0.92f, 0.48f));
    put(m, "tadpole", fc("tadpole/tadpole.png", 2, 1, 6, 4, 16, 16));
    put(m, "turtle",
        fc("turtle/turtle.png", 9, 6, 6, 5, 128, 64),
        fc("turtle/big_sea_turtle.png", 9, 6, 6, 5, 128, 64));
    put(m, "dolphin",
        fc("dolphin/dolphin.png", 0, 6, 6, 7, 64, 64),
        fc("dolphin.png", 0, 6, 6, 7, 64, 64));
    put(m, "squid", fc("squid/squid.png", 12, 16, 12, 12, 64, 32));
    put(m, "glow_squid", fc("squid/glow_squid.png", 12, 16, 12, 12, 64, 32));
    put(m, "cod", fc("fish/cod.png", 11, 3, 6, 4, 32, 32));
    put(m, "salmon", fc("fish/salmon.png", 22, 3, 6, 4, 32, 32));
    put(m, "pufferfish", fc("fish/pufferfish.png", 8, 8, 8, 8, 32, 32));
    put(m, "tropical_fish", fc("fish/tropical_a.png", 2, 6, 6, 3, 32, 32));
    putOv(m, "sniffer",
        new FaceCandidate[] { fc("sniffer/sniffer.png", 34, 27, 13, 14, 192, 192) },
        subOverlay(20, 47, 12, 8, 192, 192, 0.14f, 0.52f, 0.86f, 0.96f));
    put(m, "armadillo",
        fc("armadillo/armadillo.png", 45, 17, 5, 5, 64, 64),
        fc("armadillo.png", 45, 17, 5, 5, 64, 64));
    put(m, "nautilus", fc("nautilus/nautilus.png", 16, 14, 14, 12, 128, 128));
    put(m, "zombie_nautilus", fc("nautilus/zombie_nautilus.png", 16, 14, 14, 12, 128, 128));

    put(m, "experience_orb",
        fc("experience/experience_orb.png", 32, 16, 16, 16, 64, 64),
        fc("experience_orb.png", 32, 16, 16, 16, 64, 64));

    return m;
  }
}
