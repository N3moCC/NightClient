package dev.nightclient.renderer.map;

import dev.nightclient.api.MapRadarModels;
import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

public final class MapEntityIconComposer {
  public static final int ICON_SIZE = 32;
  private static final int OUTLINE_DARK = 0xF00B0E14;
  private static final String ENT = "minecraft:textures/entity/";
  private static final String BLK = "minecraft:textures/block/";
  private static final String PART = "minecraft:textures/particle/";

  private MapEntityIconComposer() {}

  public interface AssetLoader {
    byte[] loadResourceBytes(String textureId);

    boolean hasItem(String itemId);
  }

  public static final class ComposedIcon {
    public static final ComposedIcon EMPTY = new ComposedIcon("", null, 0, "");

    public final String cacheKey;
    public final int[] pixels;
    public final int size;
    public final String itemIconId;

    public ComposedIcon(String cacheKey, int[] pixels, int size, String itemIconId) {
      this.cacheKey = cacheKey == null ? "" : cacheKey;
      this.pixels = pixels;
      this.size = size;
      this.itemIconId = itemIconId == null ? "" : itemIconId;
    }

    public boolean hasItemIcon() {
      return !itemIconId.isEmpty();
    }

    public boolean hasComposedPixels() {
      return pixels != null && size > 0 && pixels.length == size * size;
    }
  }

  public static final class DecodedImage {
    public final int width;
    public final int height;
    public final int[] argb;

    public DecodedImage(int width, int height, int[] argb) {
      this.width = width;
      this.height = height;
      this.argb = argb;
    }
  }

  public static ComposedIcon compose(String entityType, int kind, AssetLoader loader) {
    if (loader == null) return ComposedIcon.EMPTY;
    String id = MapEntityTextures.shortId(entityType);

    String nativeItem = preferredItemIcon(id, kind, loader);
    if (!nativeItem.isEmpty()) {
      return new ComposedIcon("", null, 0, nativeItem);
    }

    int[] g = new int[ICON_SIZE * ICON_SIZE];
    boolean ok = buildIcon32(g, id, loader);
    if (!ok) return ComposedIcon.EMPTY;

    if (kind == MapRadarModels.MOB_BOSS) {
      addOutline(g, ICON_SIZE, ICON_SIZE, OUTLINE_DARK);
      addOutline(g, ICON_SIZE, ICON_SIZE, 0xBCE879F9);
    } else {
      addOutline(g, ICON_SIZE, ICON_SIZE, OUTLINE_DARK);
    }
    return new ComposedIcon("ent32_" + (id.isEmpty() ? ("k" + kind) : id), g, ICON_SIZE, "");
  }

  private static String preferredItemIcon(String id, int kind, AssetLoader loader) {
    if ("experience_orb".equals(id) || "dragon_fireball".equals(id) || "fishing_bobber".equals(id)
        || "shulker_bullet".equals(id) || "wither_skull".equals(id) || "evoker_fangs".equals(id)
        || "lightning_bolt".equals(id) || "area_effect_cloud".equals(id)
        || "llama_spit".equals(id)) {
      return "";
    }
    String item = MapEntityTextures.itemIconForEntity(id, kind);
    if (item.isEmpty()) return "";
    if (loader.hasItem(item)) return item;
    if (item.endsWith("_chest_boat") || item.endsWith("_chest_raft")) {
      return loader.hasItem("minecraft:oak_chest_boat") ? "minecraft:oak_chest_boat" : "";
    }
    if (item.endsWith("_boat") || item.endsWith("_raft")) {
      return loader.hasItem("minecraft:oak_boat") ? "minecraft:oak_boat" : "";
    }
    if (item.endsWith("_minecart")) {
      return loader.hasItem("minecraft:minecart") ? "minecraft:minecart" : "";
    }
    if ("minecraft:wind_charge".equals(item)) {
      return loader.hasItem("minecraft:snowball") ? "minecraft:snowball" : "";
    }
    if ("minecraft:ominous_trial_key".equals(item)) {
      return loader.hasItem("minecraft:nether_star") ? "minecraft:nether_star" : "";
    }
    return "";
  }

  private static boolean buildIcon32(int[] g, String id, AssetLoader loader) {
    MapEntity3DRenderer.Scene s = new MapEntity3DRenderer.Scene();

    if ("experience_orb".equals(id)) {
      DecodedImage img = loadAny(loader, "experience/experience_orb.png", "experience_orb.png");
      if (img == null) return false;
      blitScaled(g, img, 32, 16, 16, 16, 64, 64, 6, 6, 20, 20, 0x94FF32);
      return true;
    }
    if ("dragon_fireball".equals(id)) {
      DecodedImage img = loadAny(loader, "enderdragon/dragon_fireball.png");
      if (img == null) return false;
      blitScaled(g, img, 1, 1, 14, 14, 16, 16, 4, 4, 24, 24, 0xFFFFFF);
      return true;
    }
    if ("fishing_bobber".equals(id)) {
      DecodedImage img = loadAny(loader, "fishing/fishing_hook.png", "fishing_hook.png");
      if (img == null) return false;
      blitScaled(g, img, 1, 0, 6, 8, 8, 8, 7, 3, 18, 26, 0xFFFFFF);
      return true;
    }
    if ("shulker_bullet".equals(id)) {
      DecodedImage img = loadAny(loader, "shulker/spark.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -4, -4, -1, 8, 8, 2);
      s.box(img, 64, 32, 0, 10, -1, -4, -4, 2, 8, 8);
      s.box(img, 64, 32, 20, 0, -4, -1, -4, 8, 2, 8);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -36f, 28f);
    }
    if ("wither_skull".equals(id)) {
      DecodedImage img = loadAny(loader, "wither/wither.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 0, -4, -4, -4, 8, 8, 8);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 5, -22f, 12f);
    }
    if ("evoker_fangs".equals(id)) {
      DecodedImage img = loadAny(loader, "illager/evoker_fangs.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -5, 4, -5, 10, 8, 10);
      s.boxRot(img, 64, 32, 40, 0, -4, -13, -4, 4, 14, 8, -1.5f, 4f, 0f, 0f, 0f, -0.38f);
      s.boxRot(img, 64, 32, 40, 0, 0, -13, -4, 4, 14, 8, 1.5f, 4f, 0f, 0f, 0f, 0.38f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 14f);
    }
    if ("llama_spit".equals(id)) {
      DecodedImage img = loadAny(loader, "llama/llama_spit.png", "llama/spit.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -4, 0, 0, 2, 2, 2);
      s.box(img, 64, 32, 0, 0, 0, -4, 0, 2, 2, 2);
      s.box(img, 64, 32, 0, 0, 0, 0, -4, 2, 2, 2);
      s.box(img, 64, 32, 0, 0, 0, 0, 0, 2, 2, 2);
      s.box(img, 64, 32, 0, 0, 2, 0, 0, 2, 2, 2);
      s.box(img, 64, 32, 0, 0, 0, 2, 0, 2, 2, 2);
      s.box(img, 64, 32, 0, 0, 0, 0, 2, 2, 2, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 5, -28f, 18f);
    }
    if ("area_effect_cloud".equals(id)) {
      DecodedImage p1 = loadParticle(loader, "effect_5.png");
      DecodedImage p2 = loadParticle(loader, "effect_4.png");
      DecodedImage p3 = loadParticle(loader, "effect_6.png");
      if (p1 == null) return false;
      blitScaled(g, p3 != null ? p3 : p1, 0, 0, p1.width, p1.height, p1.width, p1.height,
          4, 11, 13, 13, 0xC026D3);
      blitScaled(g, p2 != null ? p2 : p1, 0, 0, p1.width, p1.height, p1.width, p1.height,
          15, 11, 13, 13, 0xE879F9);
      blitScaled(g, p1, 0, 0, p1.width, p1.height, p1.width, p1.height,
          9, 5, 15, 15, 0xF5D0FE);
      return true;
    }
    if ("lightning_bolt".equals(id)) {
      paintLightningBolt32(g);
      return true;
    }

    if ("ender_dragon".equals(id)) {
      DecodedImage img = loadAny(loader, "enderdragon/dragon.png");
      if (img == null) return false;
      DecodedImage eyes = loadAny(loader, "enderdragon/dragon_eyes.png");
      s.box(img, 256, 256, 112, 30, -8, -8, -4, 16, 16, 16);
      s.box(img, 256, 256, 176, 44, -6, -1, -18, 12, 5, 14);
      s.box(img, 256, 256, 176, 65, -6, 4, -18, 12, 4, 14);
      s.box(img, 256, 256, 0, 0, -5, -12, 2, 2, 4, 6);
      s.box(img, 256, 256, 0, 0, 3, -12, 2, 2, 4, 6);
      if (eyes != null) {
        s.boxEmissive(eyes, 256, 256, 112, 30, -8, -8, -4, 16, 16, 16, 0.15f);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 12f);
    }
    if ("wither".equals(id)) {
      DecodedImage img = loadAny(loader, "wither/wither.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 16, -10, 4, -1.5f, 20, 3, 3);
      s.box(img, 64, 64, 0, 0, -4, -4, -4, 8, 8, 8);
      s.box(img, 64, 64, 32, 0, -12, -1, -3, 6, 6, 6);
      s.box(img, 64, 64, 32, 0, 6, -1, -3, 6, 6, 6);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -12f, 10f);
    }
    if ("warden".equals(id)) {
      DecodedImage img = loadAny(loader, "warden/warden.png");
      if (img == null) return false;
      DecodedImage glow = loadAny(loader, "warden/warden_bioluminescent_layer.png");
      s.box(img, 128, 128, 0, 32, -8, -8, -5, 16, 16, 10);
      s.box(img, 128, 128, 52, 32, -20, -15, 0, 14, 14, 0);
      s.box(img, 128, 128, 58, 0, 6, -15, 0, 14, 14, 0);
      if (glow != null) {
        s.boxEmissive(glow, 128, 128, 0, 32, -8, -8, -5, 16, 16, 10, 0.15f);
        s.boxEmissive(glow, 128, 128, 52, 32, -20, -15, -0.1f, 14, 14, 0, 0f);
        s.boxEmissive(glow, 128, 128, 58, 0, 6, -15, -0.1f, 14, 14, 0, 0f);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -12f, 8f);
    }
    if ("guardian".equals(id) || "elder_guardian".equals(id)) {
      boolean elder = "elder_guardian".equals(id);
      DecodedImage img = elder
          ? loadAny(loader, "guardian/guardian_elder.png", "guardian_elder.png")
          : loadAny(loader, "guardian/guardian.png", "guardian.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 0, -6, -6, -8, 12, 12, 16);
      s.box(img, 64, 64, 0, 28, -8, -6, -6, 2, 12, 12);
      s.box(img, 64, 64, 0, 28, 6, -6, -6, 2, 12, 12);
      s.box(img, 64, 64, 16, 40, -6, -8, -6, 12, 2, 12);
      s.box(img, 64, 64, 16, 40, -6, 6, -6, 12, 2, 12);
      s.box(img, 64, 64, 8, 0, -1.5f, -1.5f, -8.3f, 3, 3, 1);
      s.boxRot(img, 64, 64, 0, 0, -1, -7, -1, 2, 8, 2, -6, -6, -2, 0f, 0f, -0.78f);
      s.boxRot(img, 64, 64, 0, 0, -1, -7, -1, 2, 8, 2, 6, -6, -2, 0f, 0f, 0.78f);
      s.boxRot(img, 64, 64, 0, 0, -1, -1, -1, 2, 8, 2, -6, 6, -2, 0f, 0f, 0.78f);
      s.boxRot(img, 64, 64, 0, 0, -1, -1, -1, 2, 8, 2, 6, 6, -2, 0f, 0f, -0.78f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -22f, 12f);
    }

    if ("zombie".equals(id) || "giant".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "zombie/zombie.png"), 64, 64, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("husk".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "zombie/husk.png"), 64, 64, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("drowned".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "zombie/drowned.png"), 64, 64,
          loadAny(loader, "zombie/drowned_outer_layer.png"), 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("skeleton".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "skeleton/skeleton.png"), 64, 32, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("wither_skeleton".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "skeleton/wither_skeleton.png"), 64, 32, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("stray".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "skeleton/stray.png"), 64, 32,
          loadAny(loader, "skeleton/stray_overlay.png"), 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("bogged".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "skeleton/bogged.png"), 64, 32,
          loadAny(loader, "skeleton/bogged_overlay.png"), 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("parched".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "skeleton/parched.png"), 64, 64, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("creeper".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "creeper/creeper.png"), 64, 32, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("spider".equals(id) || "cave_spider".equals(id)) {
      DecodedImage img = "cave_spider".equals(id)
          ? loadAny(loader, "spider/cave_spider.png")
          : loadAny(loader, "spider/spider.png");
      return cubeHead3D(g, img, 64, 32,
          loadAny(loader, "spider/spider_eyes.png", "spider_eyes.png"), 32, 4, 8, 8, 8, -14f, 8f);
    }
    if ("enderman".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "enderman/enderman.png"), 64, 32,
          loadAny(loader, "enderman/enderman_eyes.png"), 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("blaze".equals(id)) {
      DecodedImage img = loadAny(loader, "blaze/blaze.png", "blaze.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -4, -5, -4, 8, 8, 8);
      s.box(img, 64, 32, 0, 16, -7.5f, -1, -2, 2, 8, 2);
      s.box(img, 64, 32, 0, 16, 5.5f, -2, -1, 2, 8, 2);
      s.box(img, 64, 32, 0, 16, -2, 1, 5, 2, 8, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }
    if ("ghast".equals(id) || "happy_ghast".equals(id)) {
      boolean happy = "happy_ghast".equals(id);
      DecodedImage img = happy
          ? loadAny(loader, "ghast/happy_ghast.png")
          : loadAny(loader, "ghast/ghast.png");
      if (img == null) return false;
      int bh = happy ? 64 : 32;
      s.box(img, 64, bh, 0, 0, -8, -8, -8, 16, 16, 16);
      s.box(img, 64, bh, 0, 0, -6.5f, 8, -5, 2, 7, 2);
      s.box(img, 64, bh, 0, 0, -1.5f, 8, -5, 2, 5, 2);
      s.box(img, 64, bh, 0, 0, 3.5f, 8, -5, 2, 7, 2);
      s.box(img, 64, bh, 0, 0, -4, 8, 1, 2, 6, 2);
      s.box(img, 64, bh, 0, 0, 2, 8, 1, 2, 6, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }
    if ("slime".equals(id)) {
      DecodedImage img = loadAny(loader, "slime/slime.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 16, -3, -3, -3, 6, 6, 6);
      s.box(img, 64, 32, 32, 0, -3.25f, -2, -3.5f, 2, 2, 2);
      s.box(img, 64, 32, 32, 4, 1.25f, -2, -3.5f, 2, 2, 2);
      s.box(img, 64, 32, 32, 8, 0, 1, -3.5f, 1, 1, 1);
      s.box(img, 64, 32, 0, 0, -4, -4, -4, 8, 8, 8);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -18f, 12f);
    }
    if ("magma_cube".equals(id)) {
      DecodedImage img = loadAny(loader, "slime/magmacube.png");
      if (img == null) return false;
      int th = img.width == img.height ? 64 : 32;
      s.boxEmissive(img, 64, th, th == 64 ? 24 : 0, th == 64 ? 40 : 16, -2.5f, -2.5f, -2.5f, 5, 5, 5, 0f);
      for (int slice = 0; slice < 8; slice++) {
        int u, v;
        if (th == 64) {
          if (slice == 0) { u = 0; v = 0; }
          else if (slice < 4) { u = 0; v = 9 * slice; }
          else { u = 32; v = 9 * slice - 36; }
        } else {
          u = (slice == 2 || slice == 3) ? 24 : 0;
          v = (slice == 2) ? 10 : (slice == 3 ? 19 : slice);
        }
        float y = -4.5f + slice * 1.14f;
        if (slice == 2 || slice == 3) {
          s.boxEmissive(img, 64, th, u, v, -4, y, -4, 8, 1, 8, 0f);
        } else {
          s.box(img, 64, th, u, v, -4, y, -4, 8, 1, 8);
        }
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 12f);
    }
    if ("sulfur_cube".equals(id)) {
      DecodedImage outer = loadAny(loader, "sulfur_cube/sulfur_cube_outer.png");
      if (outer == null) return false;
      DecodedImage inner = loadAny(loader, "sulfur_cube/sulfur_cube_inner.png");
      if (inner != null) s.box(inner, 128, 128, 0, 36, -8, -8, -8, 16, 16, 16);
      s.box(outer, 128, 128, 0, 0, -9, -9, -9, 18, 18, 18);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -18f, 12f);
    }
    if ("breeze".equals(id)) {
      DecodedImage img = loadAny(loader, "breeze/breeze.png");
      if (img == null) return false;
      DecodedImage eyes = loadAny(loader, "breeze/breeze_eyes.png");
      s.box(img, 32, 32, 0, 0, -4, -4, -4, 8, 8, 8);
      if (eyes != null) s.boxEmissive(eyes, 32, 32, 0, 0, -4, -4, -4, 8, 8, 8, 0.15f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -18f, 10f);
    }
    if ("creaking".equals(id) || "creaking_transient".equals(id)) {
      DecodedImage img = loadAny(loader, "creaking/creaking.png");
      if (img == null) return false;
      DecodedImage eyes = loadAny(loader, "creaking/creaking_eyes.png");
      s.box(img, 64, 64, 0, 0, -3, -5, -3, 6, 10, 6);
      s.box(img, 64, 64, 28, 31, -3, -8, -3, 6, 3, 6);
      s.box(img, 64, 64, 12, 40, 3, -8, 0, 9, 14, 0);
      s.box(img, 64, 64, 34, 12, -12, -9, 0, 9, 14, 0);
      if (eyes != null) s.boxEmissive(eyes, 64, 64, 0, 0, -3, -5, -3, 6, 10, 6, 0.15f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }

    if ("villager".equals(id)) {
      return villager3D(g, loadAny(loader, "villager/villager.png"), 64, 64);
    }
    if ("zombie_villager".equals(id)) {
      return villager3D(g, loadAny(loader, "zombie_villager/zombie_villager.png"), 64, 64);
    }
    if ("wandering_trader".equals(id)) {
      return villager3D(g,
          loadAny(loader, "wandering_trader/wandering_trader.png", "wandering_trader.png"), 64, 64);
    }
    if ("pillager".equals(id)) {
      return villager3D(g, loadAny(loader, "illager/pillager.png"), 64, 64);
    }
    if ("vindicator".equals(id)) {
      return villager3D(g, loadAny(loader, "illager/vindicator.png"), 64, 64);
    }
    if ("evoker".equals(id)) {
      return villager3D(g, loadAny(loader, "illager/evoker.png"), 64, 64);
    }
    if ("illusioner".equals(id)) {
      return villager3D(g, loadAny(loader, "illager/illusioner.png"), 64, 64);
    }
    if ("iron_golem".equals(id)) {
      return villager3D(g, loadAny(loader, "iron_golem/iron_golem.png"), 128, 128);
    }
    if ("witch".equals(id)) {
      DecodedImage img = loadAny(loader, "witch/witch.png", "witch.png");
      if (img == null) return false;
      s.box(img, 64, 128, 0, 0, -4, -10, -4, 8, 10, 8);
      s.box(img, 64, 128, 24, 0, -1, -3, -6, 2, 4, 2);
      s.box(img, 64, 128, 0, 64, -5, -10.05f, -5, 10, 2, 10);
      s.boxRot(img, 64, 128, 0, 76, -3.5f, -4, -3.5f, 7, 4, 7, 0, -10, 0, -0.05f, 0, 0.03f);
      s.boxRot(img, 64, 128, 0, 87, -2, -8, -2, 4, 4, 4, 0.2f, -10, 0.2f, -0.10f, 0, 0.05f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }
    if ("ravager".equals(id)) {
      DecodedImage img = loadAny(loader, "illager/ravager.png");
      if (img == null) return false;
      s.box(img, 128, 128, 0, 0, -8, -10, -8, 16, 20, 16);
      s.box(img, 128, 128, 0, 0, -2, 2, -12, 4, 8, 4);
      s.boxRot(img, 128, 128, 74, 55, 0, -14, -2, 2, 14, 4, -10, -4, -2, 1.05f, 0, 0);
      s.boxRot(img, 128, 128, 74, 55, 0, -14, -2, 2, 14, 4, 8, -4, -2, 1.05f, 0, 0);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -22f, 10f);
    }

    if ("piglin".equals(id) || "piglin_brute".equals(id) || "zombified_piglin".equals(id)) {
      DecodedImage img = "piglin_brute".equals(id)
          ? loadAny(loader, "piglin/piglin_brute.png")
          : ("zombified_piglin".equals(id)
              ? loadAny(loader, "piglin/zombified_piglin.png")
              : loadAny(loader, "piglin/piglin.png"));
      if (img == null) return false;
      s.box(img, 64, 64, 0, 0, -5, -4, -4, 10, 8, 8);
      s.box(img, 64, 64, 31, 1, -2, 0, -5, 4, 4, 1);
      s.box(img, 64, 64, 2, 4, 2, 2, -5, 1, 2, 1);
      s.box(img, 64, 64, 2, 0, -3, 2, -5, 1, 2, 1);
      s.boxRot(img, 64, 64, 51, 6, 0, 0, -2, 1, 5, 4, 4.5f, -2, 0, 0, 0, -0.52f);
      s.boxRot(img, 64, 64, 39, 6, -1, 0, -2, 1, 5, 4, -4.5f, -2, 0, 0, 0, 0.52f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }
    if ("hoglin".equals(id) || "zoglin".equals(id)) {
      DecodedImage img = "zoglin".equals(id)
          ? loadAny(loader, "hoglin/zoglin.png")
          : loadAny(loader, "hoglin/hoglin.png");
      if (img == null) return false;
      s.boxRot(img, 128, 64, 61, 1, -7, -3, -16, 14, 6, 19, 0, 0, 0, 0.72f, 0, 0);
      s.boxRot(img, 128, 64, 10, 13, -1, -11, -1, 2, 11, 2, -7.2f, 3, -9, 0.35f, 0, -0.15f);
      s.boxRot(img, 128, 64, 1, 13, -1, -11, -1, 2, 11, 2, 7.2f, 3, -9, 0.35f, 0, 0.15f);
      s.boxRot(img, 128, 64, 1, 1, -6, -1, -2, 6, 1, 4, -6, -3, 0, 0.5f, 0, -0.65f);
      s.boxRot(img, 128, 64, 1, 6, 0, -1, -2, 6, 1, 4, 6, -3, 0, 0.5f, 0, 0.65f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -22f, 10f);
    }
    if ("strider".equals(id)) {
      DecodedImage img = loadAny(loader, "strider/strider.png");
      if (img == null) return false;
      s.box(img, 64, 128, 0, 0, -8, -7, -8, 16, 14, 16);
      s.boxRot(img, 64, 128, 16, 33, -12, 0, -8, 12, 0, 16, -8, -5, 0, 0, 0, -0.95f);
      s.boxRot(img, 64, 128, 16, 33, 0, 0, -8, 12, 0, 16, 8, -5, 0, 0, 0, 0.95f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }

    if ("vex".equals(id) || "allay".equals(id)) {
      DecodedImage img = "allay".equals(id)
          ? loadAny(loader, "allay/allay.png")
          : loadAny(loader, "illager/vex.png");
      if (img == null) return false;
      s.box(img, 32, 32, 0, 0, -2.5f, -5, -2.5f, 5, 5, 5);
      s.box(img, 32, 32, 0, 10, -1.5f, 0, -1, 3, 4, 2);
      s.boxRot(img, 32, 32, 16, 14, 0, -2, 0, 0, 5, 8, -1.5f, 0, 1, 0.2f, -0.78f, 0f);
      s.boxRot(img, 32, 32, 16, 14, 0, -2, 0, 0, 5, 8, 1.5f, 0, 1, 0.2f, 0.78f, 0f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 14f);
    }
    if ("phantom".equals(id)) {
      DecodedImage img = loadAny(loader, "phantom/phantom.png", "phantom.png");
      if (img == null) return false;
      DecodedImage eyes = loadAny(loader, "phantom/phantom_eyes.png", "phantom_eyes.png");
      s.box(img, 64, 64, 0, 0, -3.5f, -1, -11, 7, 3, 5);
      if (eyes != null) s.boxEmissive(eyes, 64, 64, 0, 0, -3.5f, -1, -11, 7, 3, 5, 0.12f);
      s.box(img, 64, 64, 0, 8, -2.5f, -1.5f, -6, 5, 3, 9);
      s.box(img, 64, 64, 23, 12, 2.5f, -1.5f, -6, 6, 2, 9);
      s.box(img, 64, 64, 16, 24, 8.5f, -1.0f, -6, 9, 1, 9);
      s.box(img, 64, 64, 23, 12, -8.5f, -1.5f, -6, 6, 2, 9);
      s.box(img, 64, 64, 16, 24, -17.5f, -1.0f, -6, 9, 1, 9);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 2, -16f, 38f);
    }
    if ("shulker".equals(id)) {
      DecodedImage img = loadAny(loader, "shulker/shulker.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 0, -8, -11, -8, 16, 12, 16);
      s.box(img, 64, 64, 0, 28, -8, 3, -8, 16, 8, 16);
      s.box(img, 64, 64, 0, 52, -3, -2, -3, 6, 6, 6);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -22f, 14f);
    }
    if ("silverfish".equals(id)) {
      DecodedImage img = loadAny(loader, "silverfish/silverfish.png", "silverfish.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -1.5f, -1, -7, 3, 2, 2);
      s.box(img, 64, 32, 0, 4, -2, -1.5f, -5, 4, 3, 2);
      s.box(img, 64, 32, 0, 9, -3, -2, -3, 6, 4, 3);
      s.box(img, 64, 32, 0, 16, -1.5f, -1.5f, 0, 3, 3, 3);
      s.box(img, 64, 32, 0, 22, -1, -1, 3, 2, 2, 3);
      s.box(img, 64, 32, 20, 0, -5, -4, -3, 10, 8, 3);
      s.box(img, 64, 32, 20, 11, -3, -2, 3, 6, 4, 3);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -52f, 20f);
    }
    if ("endermite".equals(id)) {
      DecodedImage img = loadAny(loader, "endermite/endermite.png", "endermite.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -2, -1.5f, -5, 4, 3, 2);
      s.box(img, 64, 32, 0, 5, -3, -2.5f, -3, 6, 4, 5);
      s.box(img, 64, 32, 0, 14, -1.5f, -1.5f, 2, 3, 3, 1);
      s.box(img, 64, 32, 0, 18, -0.5f, -0.5f, 3, 1, 2, 1);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -26f, 16f);
    }

    if ("copper_golem".equals(id)) {
      DecodedImage img = loadAny(loader, "copper_golem/copper_golem.png");
      if (img == null) return false;
      DecodedImage eyes = loadAny(loader, "copper_golem/copper_golem_eyes.png");
      s.box(img, 64, 64, 0, 0, -4, -2, -5, 8, 5, 10);
      s.box(img, 64, 64, 56, 0, -1, 1, -6, 2, 3, 2);
      s.box(img, 64, 64, 37, 8, -1, -6, -1, 2, 4, 2);
      s.box(img, 64, 64, 37, 0, -2, -10, -2, 4, 4, 4);
      if (eyes != null) s.boxEmissive(eyes, 64, 64, 0, 0, -4, -2, -5, 8, 5, 10, 0.12f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -22f, 12f);
    }
    if ("snow_golem".equals(id)) {
      DecodedImage pumpkin = loadBlockTex(loader, "carved_pumpkin.png");
      DecodedImage snow = loadAny(loader, "snow_golem/snow_golem.png", "snow_golem.png");
      if (pumpkin != null && snow != null) {
        s.box(snow, 64, 64, 0, 16, -5, 0, -5, 10, 10, 10);
        DecodedImage pSide = loadBlockTex(loader, "pumpkin_side.png");
        DecodedImage pTop = loadBlockTex(loader, "pumpkin_top.png");
        s.spriteQuad(pumpkin, -4, -8, -4.01f, 8, 8, 0, 0, 0, 0f);
        s.spriteQuad(pSide != null ? pSide : pumpkin, -4, -8, 0, 8, 8, -4, 0, 0, (float) (Math.PI * 0.5));
        s.spriteQuad(pSide != null ? pSide : pumpkin, -4, -8, 0, 8, 8, 4, 0, 0, (float) (-Math.PI * 0.5));
        s.spriteQuadTop(pTop != null ? pTop : (pSide != null ? pSide : pumpkin), -4, -8.01f, -4, 8, 8);
        return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 12f);
      }
      return cubeHead3D(g, snow, 64, 64, null, 0, 0, 8, 8, 8, -14f, 8f);
    }
    if ("mannequin".equals(id)) {
      return cubeHead3D(g, loadAny(loader, "player/wide/steve.png", "steve.png"),
          64, 64, null, 0, 0, 8, 8, 8, -14f, 8f);
    }

    if ("pig".equals(id)) {
      DecodedImage img = loadAny(loader, "pig/pig_temperate.png", "pig/temperate_pig.png", "pig/pig.png");
      if (img == null) return false;
      int th = img.width == img.height ? 64 : 32;
      s.box(img, 64, th, 0, 0, -4, -4, -4, 8, 8, 8);
      s.box(img, 64, th, 16, 16, -2, 0, -5, 4, 3, 1);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -20f, 10f);
    }
    if ("cow".equals(id) || "mooshroom".equals(id)) {
      boolean shroom = "mooshroom".equals(id);
      DecodedImage img = shroom
          ? loadAny(loader, "cow/mooshroom_red.png", "cow/red_mooshroom.png")
          : loadAny(loader, "cow/cow_temperate.png", "cow/temperate_cow.png", "cow/cow.png");
      if (img == null) return false;
      int th = img.width == img.height ? 64 : 32;
      s.box(img, 64, th, 0, 0, -4, -4, -3, 8, 8, 6);
      s.box(img, 64, th, 22, 0, -5, -5, -2, 1, 3, 1);
      s.box(img, 64, th, 22, 0, 4, -5, -2, 1, 3, 1);
      if (th == 64) {
        s.box(img, 64, 64, 1, 33, -3, 1, -4, 6, 3, 1);
      }
      if (shroom) {
        DecodedImage mush = loadBlockTex(loader, "red_mushroom.png");
        if (mush != null) {
          s.spriteQuad(mush, -3.5f, -10.5f, 0, 7, 7, 0, 0, 0, 0.78f);
          s.spriteQuad(mush, -3.5f, -10.5f, 0, 7, 7, 0, 0, 0, -0.78f);
        }
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -20f, 12f);
    }
    if ("sheep".equals(id)) {
      DecodedImage img = loadAny(loader, "sheep/sheep.png");
      if (img == null) return false;
      DecodedImage wool = loadAny(loader, "sheep/sheep_wool.png", "sheep/sheep_fur.png");
      s.box(img, 64, 32, 0, 0, -3, -3, -4, 6, 6, 8);
      if (wool != null) {
        s.box(wool, 64, 32, 0, 0, -3.45f, -3.45f, -3.0f, 6.9f, 6.9f, 6.9f);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -20f, 10f);
    }
    if ("chicken".equals(id)) {
      DecodedImage img = loadAny(loader, "chicken/chicken_temperate.png",
          "chicken/temperate_chicken.png", "chicken.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -2, -6, -1, 4, 6, 3);
      s.box(img, 64, 32, 14, 0, -2, -4, -3, 4, 2, 2);
      s.box(img, 64, 32, 14, 4, -1, -2, -2, 2, 2, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -26f, 10f);
    }
    if ("wolf".equals(id)) {
      DecodedImage img = loadAny(loader, "wolf/wolf.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -3, -3, -2, 6, 6, 4);
      s.box(img, 64, 32, 16, 14, -3, -5, 0, 2, 2, 1);
      s.box(img, 64, 32, 16, 14, 1, -5, 0, 2, 2, 1);
      s.box(img, 64, 32, 0, 10, -1.5f, 0, -5, 3, 3, 4);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -22f, 10f);
    }
    if ("cat".equals(id) || "ocelot".equals(id)) {
      DecodedImage img = "ocelot".equals(id)
          ? loadAny(loader, "cat/ocelot.png")
          : loadAny(loader, "cat/cat_tabby.png", "cat/tabby.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -2.5f, -2, -2.5f, 5, 4, 5);
      s.box(img, 64, 32, 0, 24, -1.5f, 0, -3.5f, 3, 2, 2);
      s.box(img, 64, 32, 0, 10, -2, -3, 0, 1, 1, 2);
      s.box(img, 64, 32, 6, 10, 1, -3, 0, 1, 1, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -22f, 10f);
    }
    if ("fox".equals(id)) {
      DecodedImage img = loadAny(loader, "fox/fox.png");
      if (img == null) return false;
      s.box(img, 48, 32, 1, 5, -4, -3, -3, 8, 6, 6);
      s.box(img, 48, 32, 8, 1, -4, -5, -2, 2, 2, 1);
      s.box(img, 48, 32, 15, 1, 2, -5, -2, 2, 2, 1);
      s.box(img, 48, 32, 6, 18, -2, 1, -6, 4, 2, 3);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 10f);
    }
    if ("horse".equals(id) || "donkey".equals(id) || "mule".equals(id)
        || "skeleton_horse".equals(id) || "zombie_horse".equals(id)) {
      boolean longEars = "donkey".equals(id) || "mule".equals(id);
      DecodedImage img = "donkey".equals(id) ? loadAny(loader, "horse/donkey.png")
          : ("mule".equals(id) ? loadAny(loader, "horse/mule.png")
              : ("skeleton_horse".equals(id) ? loadAny(loader, "horse/horse_skeleton.png")
                  : ("zombie_horse".equals(id) ? loadAny(loader, "horse/horse_zombie.png")
                      : loadAny(loader, "horse/horse_chestnut.png"))));
      if (img == null) return false;
      s.box(img, 64, 64, 0, 35, -2, -5, -1, 4, 10, 7);
      s.box(img, 64, 64, 0, 13, -3, -10, -2, 6, 5, 7);
      s.box(img, 64, 64, 0, 25, -2, -10, -7, 4, 5, 5);
      s.box(img, 64, 64, 56, 36, -1, -10, 5.01f, 2, 14, 2);
      if (longEars) {
        s.boxRot(img, 64, 64, 0, 12, -1, -7, 0, 2, 7, 1, -2, -10, 3, 0, 0, -0.25f);
        s.boxRot(img, 64, 64, 0, 12, -1, -7, 0, 2, 7, 1, 2, -10, 3, 0, 0, 0.25f);
      } else {
        s.box(img, 64, 64, 19, 16, -2.5f, -13, 3, 2, 3, 1);
        s.box(img, 64, 64, 19, 16, 0.5f, -13, 3, 2, 3, 1);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -52f, 10f);
    }
    if ("llama".equals(id) || "trader_llama".equals(id)) {
      DecodedImage img = "trader_llama".equals(id)
          ? loadAny(loader, "llama/llama_brown.png", "llama/brown.png")
          : loadAny(loader, "llama/llama_creamy.png", "llama/creamy.png");
      if (img == null) return false;
      s.box(img, 128, 64, 0, 14, -4, -6, -3, 8, 12, 6);
      s.box(img, 128, 64, 0, 0, -2, -4, -7, 4, 4, 9);
      s.box(img, 128, 64, 17, 0, -4, -9, -1, 3, 3, 2);
      s.box(img, 128, 64, 17, 0, 1, -9, -1, 3, 3, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 10f);
    }
    if ("camel".equals(id) || "camel_husk".equals(id)) {
      DecodedImage img = "camel_husk".equals(id)
          ? loadAny(loader, "camel/camel_husk.png")
          : loadAny(loader, "camel/camel.png");
      if (img == null) return false;
      s.box(img, 128, 128, 21, 0, -3.5f, -7, -3.5f, 7, 14, 7);
      s.box(img, 128, 128, 50, 0, -2.5f, -7, -9.5f, 5, 5, 6);
      s.box(img, 128, 128, 45, 0, 3.5f, -6, 1, 3, 1, 2);
      s.box(img, 128, 128, 67, 0, -6.5f, -6, 1, 3, 1, 2);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -38f, 10f);
    }
    if ("panda".equals(id)) {
      DecodedImage img = loadAny(loader, "panda/panda.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 6, -6.5f, -5, -4, 13, 10, 9);
      s.box(img, 64, 64, 45, 16, -3.5f, 0, -6, 7, 5, 2);
      s.box(img, 64, 64, 52, 25, -8.5f, -8, -1, 5, 4, 1);
      s.box(img, 64, 64, 52, 25, 3.5f, -8, -1, 5, 4, 1);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }
    if ("polar_bear".equals(id)) {
      DecodedImage img = loadAny(loader, "bear/polarbear.png");
      if (img == null) return false;
      s.box(img, 128, 64, 0, 0, -3.5f, -3.5f, -3.5f, 7, 7, 7);
      s.box(img, 128, 64, 0, 44, -2.5f, 0.5f, -6.5f, 5, 3, 3);
      s.box(img, 128, 64, 26, 0, -4.5f, -4.5f, -1.5f, 2, 2, 1);
      s.box(img, 128, 64, 26, 0, 2.5f, -4.5f, -1.5f, 2, 2, 1);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, -22f, 10f);
    }
    if ("goat".equals(id)) {
      DecodedImage img = loadAny(loader, "goat/goat.png");
      if (img == null) return false;
      s.boxRot(img, 64, 64, 34, 46, -2.5f, -3, -6, 5, 7, 10, 0, 0, 0, 0.72f, 0, 0);
      s.box(img, 64, 64, 12, 55, -2.5f, -10, -2, 2, 7, 2);
      s.box(img, 64, 64, 12, 55, 0.5f, -10, -2, 2, 7, 2);
      s.box(img, 64, 64, 2, 61, -5.5f, -5, -1, 3, 2, 1);
      s.box(img, 64, 64, 2, 61, 2.5f, -5, -1, 3, 2, 1);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -28f, 10f);
    }
    if ("rabbit".equals(id) || "killer_bunny".equals(id)) {
      boolean killer = "killer_bunny".equals(id);
      DecodedImage img = killer
          ? loadAny(loader, "rabbit/rabbit_caerbannog.png", "rabbit/caerbannog.png")
          : loadAny(loader, "rabbit/rabbit_brown.png", "rabbit/brown.png");
      if (img == null) return false;
      boolean modern64 = img.width == img.height;
      if (modern64) {
        s.box(img, 64, 64, 0, 16, -2.5f, -2.5f, -2.5f, 5, 5, 5);
        s.box(img, 64, 64, 26, 0, -2.5f, -7.5f, 0, 2, 5, 1);
        s.box(img, 64, 64, 32, 0, 0.5f, -7.5f, 0, 2, 5, 1);
      } else {
        s.box(img, 64, 32, 32, 0, -2.5f, -2, -2.5f, 5, 4, 5);
        s.box(img, 64, 32, 52, 0, -2.5f, -7, 0, 2, 5, 1);
        s.box(img, 64, 32, 58, 0, 0.5f, -7, 0, 2, 5, 1);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -22f, 10f);
    }
    if ("bee".equals(id)) {
      DecodedImage img = loadAny(loader, "bee/bee.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 0, -3.5f, -3.5f, -5, 7, 7, 10);
      s.box(img, 64, 64, 2, 0, 1.5f, -4.5f, -8, 1, 2, 3);
      s.box(img, 64, 64, 2, 3, -2.5f, -4.5f, -8, 1, 2, 3);
      s.boxRot(img, 64, 64, 0, 18, -9, 0, 0, 9, 0, 6, -1.5f, -3.5f, -2, 0, -0.2f, 0.35f);
      s.boxRot(img, 64, 64, 0, 18, 0, 0, 0, 9, 0, 6, 1.5f, -3.5f, -2, 0, 0.2f, -0.35f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -28f, 18f);
    }
    if ("bat".equals(id)) {
      DecodedImage img = loadAny(loader, "bat/bat.png", "bat.png");
      if (img == null) return false;
      if (img.width == 64 && img.height == 64) {
        s.box(img, 64, 64, 0, 0, -3, -3, -3, 6, 6, 6);
        s.box(img, 64, 64, 24, 0, -4, -6, -1, 3, 4, 1);
        s.box(img, 64, 64, 24, 0, 1, -6, -1, 3, 4, 1);
      } else {
        s.box(img, 32, 32, 0, 7, -2, -1.5f, -1, 4, 3, 2);
        s.box(img, 32, 32, 0, 0, -1.5f, 1.5f, -1, 3, 4, 2);
        s.box(img, 32, 32, 1, 15, -3.5f, -5.5f, 0, 3, 5, 0);
        s.box(img, 32, 32, 8, 15, 0.5f, -5.5f, 0, 3, 5, 0);
        s.box(img, 32, 32, 12, 0, -3.5f, -1, 0, 2, 7, 0);
        s.box(img, 32, 32, 16, 0, -9.5f, -1, 0, 6, 8, 0);
        s.box(img, 32, 32, 12, 7, 1.5f, -1, 0, 2, 7, 0);
        s.box(img, 32, 32, 16, 8, 3.5f, -1, 0, 6, 8, 0);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 2, -14f, 10f);
    }
    if ("parrot".equals(id)) {
      DecodedImage img = loadAny(loader, "parrot/parrot_red_blue.png");
      if (img == null) return false;
      s.box(img, 32, 32, 2, 2, -1, -3, -1, 2, 3, 2);
      s.box(img, 32, 32, 10, 0, -1, -4, -2, 2, 1, 4);
      s.box(img, 32, 32, 11, 7, -0.5f, -2.5f, -2, 1, 2, 1);
      s.box(img, 32, 32, 16, 7, -0.5f, -3.5f, -2.9f, 1, 2, 1);
      s.boxRot(img, 32, 32, 2, 18, 0, -4.5f, -2, 0, 5, 4, 0, -3.2f, 0.1f, -0.22f, 0, 0);
      s.boxRot(img, 32, 32, 2, 8, -1.5f, 0, -1.5f, 3, 5, 3, 0, -0.2f, 0, 0.45f, 0, 0);
      s.boxRot(img, 32, 32, 19, 8, -0.5f, 0, -1.5f, 1, 4.5f, 3, -1.6f, 0.2f, 0.2f, 0.45f, 0, 0);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -46f, 10f);
    }
    if ("axolotl".equals(id)) {
      DecodedImage img = loadAny(loader, "axolotl/axolotl_lucy.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 1, -4, -2.5f, -2.5f, 8, 5, 5);
      s.box(img, 64, 64, 3, 37, -4, -5.5f, 0, 8, 3, 0);
      s.box(img, 64, 64, 0, 40, -7, -4.5f, 0, 3, 7, 0);
      s.box(img, 64, 64, 11, 40, 4, -4.5f, 0, 3, 7, 0);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
    }
    if ("frog".equals(id)) {
      DecodedImage img = loadAny(loader, "frog/frog_temperate.png", "frog/temperate_frog.png");
      if (img == null) return false;
      s.box(img, 48, 48, 3, 1, -3.5f, 0, -4, 7, 3, 9);
      s.box(img, 48, 48, 0, 13, -3.5f, -2, -4, 7, 3, 9);
      s.box(img, 48, 48, 0, 0, -3.5f, -4, -3.5f, 3, 2, 3);
      s.box(img, 48, 48, 0, 5, 0.5f, -4, -3.5f, 3, 2, 3);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 16f);
    }
    if ("tadpole".equals(id)) {
      DecodedImage img = loadAny(loader, "tadpole/tadpole.png");
      if (img == null) return false;
      s.box(img, 16, 16, 0, 0, -1.5f, -1, -3, 3, 2, 3);
      s.box(img, 16, 16, 0, 0, 0, -1, 0, 0, 2, 7);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -55f, 22f);
    }
    if ("turtle".equals(id)) {
      DecodedImage img = loadAny(loader, "turtle/turtle.png", "turtle/big_sea_turtle.png");
      if (img == null) return false;
      s.boxRot(img, 128, 64, 7, 37, -9.5f, -10, -3, 19, 20, 6, 0, 0, 0, (float) (Math.PI * 0.5), 0, 0);
      s.box(img, 128, 64, 3, 0, -3, -2, -14, 6, 5, 6);
      s.box(img, 128, 64, 27, 30, -17, 1, -7, 10, 1, 5);
      s.box(img, 128, 64, 27, 24, 7, 1, -7, 10, 1, 5);
      s.box(img, 128, 64, 1, 23, -6, 1, 9, 4, 1, 7);
      s.box(img, 128, 64, 1, 12, 2, 1, 9, 4, 1, 7);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -28f, 44f);
    }
    if ("dolphin".equals(id)) {
      DecodedImage img = loadAny(loader, "dolphin/dolphin.png", "dolphin.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 0, -4, -3, -9, 8, 7, 6);
      s.box(img, 64, 64, 0, 13, -1, 1, -13, 2, 2, 4);
      s.box(img, 64, 64, 22, 0, -4, -3, -3, 8, 7, 13);
      s.boxRot(img, 64, 64, 51, 0, -0.5f, -5, 1, 1, 4, 5, 0, -2, 2, -0.35f, 0, 0);
      s.box(img, 64, 64, 0, 19, -2, -1.5f, 10, 4, 5, 8);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -58f, 14f);
    }
    if ("squid".equals(id) || "glow_squid".equals(id)) {
      DecodedImage img = "glow_squid".equals(id)
          ? loadAny(loader, "squid/glow_squid.png")
          : loadAny(loader, "squid/squid.png");
      if (img == null) return false;
      s.box(img, 64, 32, 0, 0, -6, -12, -6, 12, 16, 12);
      for (int i = 0; i < 6; i++) {
        double ang = i * Math.PI * 2.0 / 6.0 + 0.35;
        float tx = (float) (Math.cos(ang) * 4.2);
        float tz = (float) (Math.sin(ang) * 4.2);
        s.boxRot(img, 64, 32, 48, 0, -1, 0, -1, 2, 11, 2, tx, 4, tz,
            (float) (Math.sin(ang) * 0.24), 0f, (float) (-Math.cos(ang) * 0.24));
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -15f, 10f);
    }

    if ("cod".equals(id)) {
      DecodedImage img = loadAny(loader, "fish/cod.png");
      if (img == null) return false;
      s.box(img, 32, 32, 0, 0, -1, -2, 0, 2, 4, 7);
      s.box(img, 32, 32, 11, 0, -1, -2, -3, 2, 4, 3);
      s.box(img, 32, 32, 0, 0, -1, -2, -4, 2, 3, 1);
      s.box(img, 32, 32, 22, 3, 0, -2, 7, 0, 4, 4);
      s.box(img, 32, 32, 20, -6, 0, -3, -1, 0, 1, 6);
      s.boxRot(img, 32, 32, 22, 1, -2, 0, -1, 2, 0, 2, -1, 1, 0, 0, 0, -0.78f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 2, -64f, 12f, -22f);
    }
    if ("salmon".equals(id)) {
      DecodedImage img = loadAny(loader, "fish/salmon.png");
      if (img == null) return false;
      s.box(img, 32, 32, 22, 0, -1, -2, -10.2f, 2, 4, 3);
      s.box(img, 32, 32, 0, 0, -1.5f, -2.5f, -7.2f, 3, 5, 8);
      s.box(img, 32, 32, 0, 13, -1.5f, -2.5f, 0.8f, 3, 5, 8);
      s.box(img, 32, 32, 20, 10, 0, -2.5f, 8.8f, 0, 5, 6);
      s.box(img, 32, 32, 2, 1, 0, -4.5f, -2.2f, 0, 2, 3);
      s.box(img, 32, 32, 0, 2, 0, -4.5f, -0.2f, 0, 2, 4);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 2, -64f, 12f, -24f);
    }
    if ("pufferfish".equals(id)) {
      DecodedImage img = loadAny(loader, "fish/pufferfish.png");
      if (img == null) return false;
      s.box(img, 32, 32, 0, 0, -4, -4, -4, 8, 8, 8);
      s.box(img, 32, 32, 24, 0, -6, -1, -1, 2, 1, 2);
      s.box(img, 32, 32, 24, 3, 4, -1, -1, 2, 1, 2);
      s.box(img, 32, 32, 14, 16, -4, -5, -0.5f, 8, 1, 1);
      s.boxRot(img, 32, 32, 5, 17, -1, -4, 0, 1, 8, 0, -4, 0, -4, 0, -0.78f, 0);
      s.boxRot(img, 32, 32, 1, 17, 0, -4, 0, 1, 8, 0, 4, 0, -4, 0, 0.78f, 0);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -24f, 14f);
    }
    if ("tropical_fish".equals(id)) {
      DecodedImage base = loadAny(loader, "fish/tropical_a.png");
      if (base == null) return false;
      DecodedImage pat = loadAny(loader, "fish/tropical_a_pattern_1.png");
      s.boxTinted(base, 32, 32, 0, 0, -1, -1.5f, -3, 2, 3, 6, 0xFF8C24);
      s.boxTinted(base, 32, 32, 22, -6, 0, -1.5f, 3, 0, 3, 6, 0xFF8C24);
      s.boxTinted(base, 32, 32, 10, -5, 0, -4.5f, -3, 0, 3, 6, 0xFF8C24);
      if (pat != null) {
        s.boxTinted(pat, 32, 32, 0, 0, -1.08f, -1.58f, -3.08f, 2.16f, 3.16f, 6.16f, 0xFFFFFF);
        s.boxTinted(pat, 32, 32, 22, -6, -0.06f, -1.5f, 3, 0, 3, 6, 0xFFFFFF);
      }
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 2, -64f, 12f, -20f);
    }

    if ("nautilus".equals(id) || "zombie_nautilus".equals(id)) {
      DecodedImage img = "zombie_nautilus".equals(id)
          ? loadAny(loader, "nautilus/zombie_nautilus.png")
          : loadAny(loader, "nautilus/nautilus.png");
      if (img == null) return false;
      s.box(img, 128, 128, 0, 0, -7, -10, -7, 14, 10, 16);
      s.box(img, 128, 128, 0, 26, -7, 0, -7, 14, 8, 20);
      s.box(img, 128, 128, 0, 54, -5, 0, 4.3f, 10, 8, 14);
      s.box(img, 128, 128, 54, 54, -5, 0, 14.3f, 10, 4, 4);
      s.box(img, 128, 128, 54, 62, -5, 4, 14.3f, 10, 4, 4);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, 134f, 12f);
    }

    if ("sniffer".equals(id)) {
      DecodedImage img = loadAny(loader, "sniffer/sniffer.png");
      if (img == null) return false;
      s.box(img, 192, 192, 8, 15, -6.5f, -7.5f, -11.5f, 13, 18, 11);
      s.box(img, 192, 192, 10, 45, -6.5f, -6.5f, -20.5f, 13, 2, 9);
      s.box(img, 192, 192, 10, 57, -6.5f, -4.5f, -20.5f, 13, 12, 9);
      s.box(img, 192, 192, 2, 0, 6.51f, -7.5f, -7.51f, 1, 19, 7);
      s.box(img, 192, 192, 48, 0, -7.51f, -7.5f, -7.51f, 1, 19, 7);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -30f, 12f);
    }
    if ("armadillo".equals(id)) {
      DecodedImage img = loadAny(loader, "armadillo/armadillo.png", "armadillo.png");
      if (img == null) return false;
      s.box(img, 64, 64, 0, 20, -4, -4, -3, 8, 8, 12);
      s.boxRot(img, 64, 64, 43, 15, -1.5f, -1, -1, 3, 5, 2, 0, 1, -4.5f, -0.39f, 0, 0);
      s.boxRot(img, 64, 64, 43, 10, -2, -3, 0, 2, 5, 0, -1.5f, 0, -4.5f, 0.19f, -0.39f, -0.07f);
      s.boxRot(img, 64, 64, 47, 10, 0, -3, 0, 2, 5, 0, 1.5f, 0, -4.5f, 0.19f, 0.39f, 0.07f);
      return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -28f, 14f);
    }

    return false;
  }

  private static boolean cubeHead3D(int[] g, DecodedImage img, int tw, int th,
      DecodedImage overlay, float u, float v, float dx, float dy, float dz,
      float yawDeg, float pitchDeg) {
    if (img == null) return false;
    int effH = adjustHeight(tw, th, img.width, img.height);
    MapEntity3DRenderer.Scene s = new MapEntity3DRenderer.Scene();
    s.box(img, tw, effH, u, v, -dx * 0.5f, -dy * 0.5f, -dz * 0.5f, dx, dy, dz);
    if (overlay != null) {
      int ovH = adjustHeight(tw, th, overlay.width, overlay.height);
      s.boxEmissive(overlay, tw, ovH, u, v, -dx * 0.5f, -dy * 0.5f, -dz * 0.5f, dx, dy, dz, 0.25f);
    }
    return MapEntity3DRenderer.render(s, g, ICON_SIZE, 4, yawDeg, pitchDeg);
  }

  private static boolean villager3D(int[] g, DecodedImage img, int tw, int th) {
    if (img == null) return false;
    MapEntity3DRenderer.Scene s = new MapEntity3DRenderer.Scene();
    s.box(img, tw, th, 0, 0, -4, -5, -4, 8, 10, 8);
    s.box(img, tw, th, 24, 0, -1, 2, -6, 2, 4, 2);
    return MapEntity3DRenderer.render(s, g, ICON_SIZE, 3, -18f, 10f);
  }

  private static int adjustHeight(int tw, int th, int aw, int ah) {
    if (tw == 64 && th == 32 && aw == ah) return 64;
    if (tw == 64 && th == 64 && aw == ah * 2) return 32;
    return th;
  }

  private static DecodedImage loadAny(AssetLoader loader, String... relPaths) {
    for (int i = 0; i < relPaths.length; i++) {
      byte[] bytes = loader.loadResourceBytes(ENT + relPaths[i]);
      if (bytes != null && bytes.length >= 24) {
        DecodedImage img = decodePng(bytes);
        if (img != null) return img;
      }
    }
    return null;
  }

  private static DecodedImage loadBlockTex(AssetLoader loader, String relPath) {
    byte[] bytes = loader.loadResourceBytes(BLK + relPath);
    return (bytes != null && bytes.length >= 24) ? decodePng(bytes) : null;
  }

  private static DecodedImage loadParticle(AssetLoader loader, String relPath) {
    byte[] bytes = loader.loadResourceBytes(PART + relPath);
    return (bytes != null && bytes.length >= 24) ? decodePng(bytes) : null;
  }

  private static void paintLightningBolt32(int[] g) {
    int bright = 0xFFFEF08A, gold = 0xFFFACC15, glow = 0xFFBAE6FD;
    fillRect(g, 16, 3, 7, 4, glow);
    fillRect(g, 13, 7, 7, 4, bright);
    fillRect(g, 10, 11, 8, 4, bright);
    fillRect(g, 8, 14, 16, 4, gold);
    fillRect(g, 13, 18, 8, 4, bright);
    fillRect(g, 11, 22, 6, 4, gold);
    fillRect(g, 9, 26, 5, 3, glow);
  }

  private static void blitScaled(int[] dst, DecodedImage src, int sx, int sy, int sw, int sh,
      int tw, int th, int dx, int dy, int dw, int dh, int tintRgb) {
    if (src == null || dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0 || tw <= 0 || th <= 0) return;
    float scaleX = src.width / (float) tw;
    float scaleY = src.height / (float) th;
    int tr = (tintRgb >> 16) & 0xFF, tg = (tintRgb >> 8) & 0xFF, tb = tintRgb & 0xFF;
    boolean tint = (tintRgb & 0xFFFFFF) != 0xFFFFFF;
    for (int py = 0; py < dh; py++) {
      int ty = dy + py;
      if (ty < 0 || ty >= ICON_SIZE) continue;
      float fy = (sy + ((py + 0.5f) / dh) * sh) * scaleY;
      int iy = Math.max(0, Math.min(src.height - 1, (int) fy));
      for (int px = 0; px < dw; px++) {
        int tx = dx + px;
        if (tx < 0 || tx >= ICON_SIZE) continue;
        float fx = (sx + ((px + 0.5f) / dw) * sw) * scaleX;
        int ix = Math.max(0, Math.min(src.width - 1, (int) fx));
        int c = src.argb[iy * src.width + ix];
        int sa = c >>> 24;
        if (sa < 20) continue;
        int sr = (c >> 16) & 0xFF, sg = (c >> 8) & 0xFF, sb = c & 0xFF;
        if (tint) {
          sr = (sr * tr) / 255;
          sg = (sg * tg) / 255;
          sb = (sb * tb) / 255;
        }
        int idx = ty * ICON_SIZE + tx;
        int bg = dst[idx];
        int ba = bg >>> 24;
        if (sa >= 250 || ba == 0) {
          dst[idx] = (sa << 24) | (sr << 16) | (sg << 8) | sb;
        } else {
          int inv = 255 - sa;
          int rr = (sr * sa + ((bg >> 16) & 0xFF) * inv) / 255;
          int rg = (sg * sa + ((bg >> 8) & 0xFF) * inv) / 255;
          int rb = (sb * sa + (bg & 0xFF) * inv) / 255;
          dst[idx] = (Math.min(255, sa + (ba * inv) / 255) << 24) | (rr << 16) | (rg << 8) | rb;
        }
      }
    }
  }

  private static void fillRect(int[] dst, int x, int y, int w, int h, int argb) {
    int sa = argb >>> 24;
    if (sa == 0) return;
    for (int py = Math.max(0, y); py < Math.min(ICON_SIZE, y + h); py++) {
      for (int px = Math.max(0, x); px < Math.min(ICON_SIZE, x + w); px++) {
        dst[py * ICON_SIZE + px] = argb;
      }
    }
  }

  private static void addOutline(int[] g, int w, int h, int outlineArgb) {
    int[] copy = new int[g.length];
    System.arraycopy(g, 0, copy, 0, g.length);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if ((copy[y * w + x] >>> 24) > 32) continue;
        boolean adj = (x > 0 && (copy[y * w + (x - 1)] >>> 24) > 32)
            || (x + 1 < w && (copy[y * w + (x + 1)] >>> 24) > 32)
            || (y > 0 && (copy[(y - 1) * w + x] >>> 24) > 32)
            || (y + 1 < h && (copy[(y + 1) * w + x] >>> 24) > 32);
        if (adj) g[y * w + x] = outlineArgb;
      }
    }
  }

  public static DecodedImage decodePng(byte[] data) {
    if (data == null || data.length < 33) return null;
    if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G'
        || data[4] != 0x0D || data[5] != 0x0A || data[6] != 0x1A || data[7] != 0x0A) {
      return null;
    }
    int pos = 8;
    int width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
    byte[] palette = null;
    byte[] trns = null;
    ByteArrayOutputStream idat = new ByteArrayOutputStream(Math.min(65536, data.length));

    while (pos + 12 <= data.length) {
      int len = readInt(data, pos);
      int type = readInt(data, pos + 4);
      int body = pos + 8;
      if (len < 0 || body + len + 4 > data.length) break;
      if (type == 0x49484452 && len >= 13) {
        width = readInt(data, body);
        height = readInt(data, body + 4);
        bitDepth = data[body + 8] & 0xFF;
        colorType = data[body + 9] & 0xFF;
        interlace = data[body + 12] & 0xFF;
      } else if (type == 0x504C5445) {
        palette = new byte[len];
        System.arraycopy(data, body, palette, 0, len);
      } else if (type == 0x74524E53) {
        trns = new byte[len];
        System.arraycopy(data, body, trns, 0, len);
      } else if (type == 0x49444154) {
        idat.write(data, body, len);
      } else if (type == 0x49454E44) {
        break;
      }
      pos = body + len + 4;
    }

    if (width <= 0 || height <= 0 || width > 2048 || height > 2048 || interlace != 0) return null;
    int channels;
    switch (colorType) {
      case 0: channels = 1; break;
      case 2: channels = 3; break;
      case 3: channels = 1; break;
      case 4: channels = 2; break;
      case 6: channels = 4; break;
      default: return null;
    }
    if (colorType == 2 || colorType == 4 || colorType == 6) {
      if (bitDepth != 8) return null;
    } else if (bitDepth != 1 && bitDepth != 2 && bitDepth != 4 && bitDepth != 8) {
      return null;
    }

    int rowBytes = (width * channels * bitDepth + 7) / 8;
    int bpp = Math.max(1, (channels * bitDepth + 7) / 8);
    byte[] raw = inflate(idat.toByteArray(), height * (rowBytes + 1));
    if (raw == null || raw.length < height * (rowBytes + 1)) return null;

    byte[] recon = new byte[height * rowBytes];
    int src = 0;
    for (int y = 0; y < height; y++) {
      int filter = raw[src++] & 0xFF;
      int rowStart = y * rowBytes;
      for (int x = 0; x < rowBytes; x++) {
        int v = raw[src++] & 0xFF;
        int a = x >= bpp ? (recon[rowStart + x - bpp] & 0xFF) : 0;
        int b = y > 0 ? (recon[rowStart - rowBytes + x] & 0xFF) : 0;
        int c = (x >= bpp && y > 0) ? (recon[rowStart - rowBytes + x - bpp] & 0xFF) : 0;
        int p;
        switch (filter) {
          case 0: p = 0; break;
          case 1: p = a; break;
          case 2: p = b; break;
          case 3: p = (a + b) >>> 1; break;
          case 4: p = paeth(a, b, c); break;
          default: return null;
        }
        recon[rowStart + x] = (byte) ((v + p) & 0xFF);
      }
    }

    int[] out = new int[width * height];
    for (int y = 0; y < height; y++) {
      int rowStart = y * rowBytes;
      for (int x = 0; x < width; x++) {
        int argb;
        if (colorType == 6) {
          int si = rowStart + x * 4;
          argb = ((recon[si + 3] & 0xFF) << 24) | ((recon[si] & 0xFF) << 16)
              | ((recon[si + 1] & 0xFF) << 8) | (recon[si + 2] & 0xFF);
        } else if (colorType == 2) {
          int si = rowStart + x * 3;
          int r = recon[si] & 0xFF, gv = recon[si + 1] & 0xFF, b = recon[si + 2] & 0xFF;
          int a = 255;
          if (trns != null && trns.length >= 6) {
            int tr = ((trns[0] & 0xFF) << 8) | (trns[1] & 0xFF);
            int tg = ((trns[2] & 0xFF) << 8) | (trns[3] & 0xFF);
            int tb = ((trns[4] & 0xFF) << 8) | (trns[5] & 0xFF);
            if (r == tr && gv == tg && b == tb) a = 0;
          }
          argb = (a << 24) | (r << 16) | (gv << 8) | b;
        } else if (colorType == 4) {
          int si = rowStart + x * 2;
          int lum = recon[si] & 0xFF;
          argb = ((recon[si + 1] & 0xFF) << 24) | (lum << 16) | (lum << 8) | lum;
        } else if (colorType == 3) {
          int idx = extractSubByte(recon, rowStart, x, bitDepth);
          if (palette == null || idx * 3 + 2 >= palette.length) {
            argb = 0;
          } else {
            int a = (trns != null && idx < trns.length) ? (trns[idx] & 0xFF) : 255;
            argb = (a << 24) | ((palette[idx * 3] & 0xFF) << 16)
                | ((palette[idx * 3 + 1] & 0xFF) << 8) | (palette[idx * 3 + 2] & 0xFF);
          }
        } else {
          int val = extractSubByte(recon, rowStart, x, bitDepth);
          int lum = bitDepth == 8 ? val : (val * 255) / ((1 << bitDepth) - 1);
          int a = 255;
          if (trns != null && trns.length >= 2) {
            int tg = ((trns[0] & 0xFF) << 8) | (trns[1] & 0xFF);
            if (val == tg) a = 0;
          }
          argb = (a << 24) | (lum << 16) | (lum << 8) | lum;
        }
        out[y * width + x] = argb;
      }
    }
    return new DecodedImage(width, height, out);
  }

  private static int extractSubByte(byte[] data, int rowStart, int x, int bitDepth) {
    if (bitDepth == 8) return data[rowStart + x] & 0xFF;
    int perByte = 8 / bitDepth;
    int byteVal = data[rowStart + (x / perByte)] & 0xFF;
    int shift = (perByte - 1 - (x % perByte)) * bitDepth;
    return (byteVal >>> shift) & ((1 << bitDepth) - 1);
  }

  private static int paeth(int a, int b, int c) {
    int p = a + b - c;
    int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
    return (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
  }

  private static int readInt(byte[] d, int off) {
    return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
        | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
  }

  private static byte[] inflate(byte[] compressed, int expectedSize) {
    int maxBytes = Math.min(64 * 1024 * 1024, Math.max(expectedSize, 1) + 4096);
    Inflater inflater = new Inflater();
    try {
      inflater.setInput(compressed);
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, Math.max(256, expectedSize)));
      byte[] buf = new byte[4096];
      while (!inflater.finished()) {
        int n = inflater.inflate(buf);
        if (n > 0) {
          if (out.size() + n > maxBytes) return null;
          out.write(buf, 0, n);
        } else if (inflater.needsInput() || inflater.needsDictionary()) break;
      }
      return out.toByteArray();
    } catch (Throwable ignored) {
      return null;
    } finally {
      inflater.end();
    }
  }
}
