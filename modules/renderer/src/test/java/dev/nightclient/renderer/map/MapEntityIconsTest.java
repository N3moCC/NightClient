package dev.nightclient.renderer.map;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.MapRadarModels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapEntityIconsTest {

  private static final class RecordingCanvas implements Canvas {
    final List<String> items = new ArrayList<String>();
    final List<String> textureRegions = new ArrayList<String>();
    final List<Float> textureRadii = new ArrayList<Float>();
    final List<Boolean> mapQuadNearest = new ArrayList<Boolean>();
    boolean allowTextureRegion = true;

    @Override public void rect(float x, float y, float w, float h, int c) {}
    @Override public void roundedRect(float x, float y, float w, float h, float r, int c) {}
    @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    @Override public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
    @Override public void circle(float cx, float cy, float r, int c) {}
    @Override public void text(String v, float x, float y, float s, int c, boolean b) {}
    @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0f : v.length() * s * 0.5f; }
    @Override public void pushClip(float x, float y, float w, float h) {}
    @Override public void popClip() {}
    @Override public void item(String id, float x, float y, float s, float a) { items.add(id); }
    @Override public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
    @Override public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}

    @Override
    public boolean textureRegion(String textureId, float x, float y, float w, float h, float radius,
        float u0, float v0, float u1, float v1, float alpha, int tintRgb) {
      if (!allowTextureRegion) return false;
      assertTrue(u0 >= 0f && u1 <= 1.0001f && u1 > u0, "Valid U bounds for " + textureId);
      assertTrue(v0 >= 0f && v1 <= 1.0001f && v1 > v0, "Valid V bounds for " + textureId);
      textureRegions.add(textureId);
      textureRadii.add(radius);
      return true;
    }

    @Override
    public void mapQuad(String key, long rev, int[] px, int tw, int th, float x, float y, float w,
        float h, float r, float uc, float vc, float uh, float vh, float ang, boolean near, int c) {
      mapQuadNearest.add(near);
    }
  }

  @Test
  void allRegisteredMobsResolveToValidNormalizedFaceUvs() {
    List<String> mobs = MapEntityTextures.knownMobTypes();
    assertTrue(mobs.size() >= 88, "Expected full vanilla mob catalog (88+ mobs), got " + mobs.size());

    MapEntityTextures.ResourceProbe allAvailable = new MapEntityTextures.ResourceProbe() {
      @Override public int[] probeTextureSize(String textureId) { return new int[] { 64, 64 }; }
      @Override public boolean hasItem(String itemId) { return true; }
    };

    for (String mob : mobs) {
      MapEntityTextures.ResolvedIcon icon =
          MapEntityTextures.resolve("minecraft:" + mob, MapRadarModels.MOB_HOSTILE, allAvailable);
      assertTrue(icon.hasFaceTexture(), "Mob should resolve to a face texture: " + mob);
      assertFalse(icon.hasItemIcon(), "Living mob should not resolve to an item icon: " + mob);
      assertTrue(icon.u0 >= 0f && icon.u1 <= 1.0001f && icon.u1 > icon.u0, "Invalid U for " + mob);
      assertTrue(icon.v0 >= 0f && icon.v1 <= 1.0001f && icon.v1 > icon.v0, "Invalid V for " + mob);
      if (icon.overlays != null) {
        for (MapRadarModels.OverlayLayer ov : icon.overlays) {
          assertFalse(ov.textureId.isEmpty(), "Overlay texture missing for " + mob);
          assertTrue(ov.u0 >= 0f && ov.u1 <= 1.0001f && ov.u1 > ov.u0, "Invalid overlay U for " + mob);
          assertTrue(ov.v0 >= 0f && ov.v1 <= 1.0001f && ov.v1 > ov.v0, "Invalid overlay V for " + mob);
          assertTrue(ov.dx0 >= 0f && ov.dx1 <= 1f && ov.dx1 > ov.dx0, "Invalid overlay dx for " + mob);
          assertTrue(ov.dy0 >= 0f && ov.dy1 <= 1f && ov.dy1 > ov.dy0, "Invalid overlay dy for " + mob);
        }
      }
    }
  }

  @Test
  void crossVersionTexturePathsAndAspectRatiosAdaptCleanly() {
    final Map<String, int[]> legacy120 = new HashMap<String, int[]>();
    legacy120.put("minecraft:textures/entity/pig/pig.png", new int[] { 64, 32 });
    legacy120.put("minecraft:textures/entity/cow/cow.png", new int[] { 64, 32 });
    legacy120.put("minecraft:textures/entity/slime/magmacube.png", new int[] { 64, 32 });
    legacy120.put("minecraft:textures/entity/rabbit/brown.png", new int[] { 64, 32 });
    legacy120.put("minecraft:textures/entity/bat.png", new int[] { 64, 64 });
    MapEntityTextures.ResourceProbe legacyProbe = new MapEntityTextures.ResourceProbe() {
      @Override public int[] probeTextureSize(String id) { return legacy120.get(id); }
      @Override public boolean hasItem(String itemId) { return !"minecraft:wind_charge".equals(itemId); }
    };

    MapEntityTextures.ResolvedIcon pig120 =
        MapEntityTextures.resolve("minecraft:pig", MapRadarModels.MOB_PASSIVE, legacyProbe);
    assertEquals("minecraft:textures/entity/pig/pig.png", pig120.textureId);
    assertEquals(8f / 32f, pig120.v0, 0.0001f);
    assertEquals(16f / 32f, pig120.v1, 0.0001f);
    assertNotNull(pig120.overlays);
    assertEquals(17f / 32f, pig120.overlays[0].v0, 0.0001f);

    MapEntityTextures.ResolvedIcon magma120 =
        MapEntityTextures.resolve("minecraft:magma_cube", MapRadarModels.MOB_HOSTILE, legacyProbe);
    assertEquals(32f / 64f, magma120.u0, 0.0001f);
    assertEquals(24f / 32f, magma120.v0, 0.0001f);

    MapEntityTextures.ResolvedIcon bat120 =
        MapEntityTextures.resolve("minecraft:bat", MapRadarModels.MOB_PASSIVE, legacyProbe);
    assertEquals(6f / 64f, bat120.u0, 0.0001f);
    assertEquals(12f / 64f, bat120.u1, 0.0001f);

    MapEntityTextures.ResolvedIcon wc120 =
        MapEntityTextures.resolve("minecraft:wind_charge", MapRadarModels.MOB_OTHER, legacyProbe);
    assertEquals("minecraft:snowball", wc120.itemIconId);

    final Map<String, int[]> modern2x = new HashMap<String, int[]>();
    modern2x.put("minecraft:textures/entity/pig/pig_temperate.png", new int[] { 128, 128 });
    modern2x.put("minecraft:textures/entity/slime/magmacube.png", new int[] { 128, 128 });
    modern2x.put("minecraft:textures/entity/rabbit/rabbit_brown.png", new int[] { 128, 128 });
    MapEntityTextures.ResourceProbe modernProbe = new MapEntityTextures.ResourceProbe() {
      @Override public int[] probeTextureSize(String id) { return modern2x.get(id); }
      @Override public boolean hasItem(String itemId) { return true; }
    };

    MapEntityTextures.ResolvedIcon pig2x =
        MapEntityTextures.resolve("minecraft:pig", MapRadarModels.MOB_PASSIVE, modernProbe);
    assertEquals("minecraft:textures/entity/pig/pig_temperate.png", pig2x.textureId);
    assertEquals(8f / 64f, pig2x.v0, 0.0001f);
    assertEquals(16f / 64f, pig2x.v1, 0.0001f);

    MapEntityTextures.ResolvedIcon magma2x =
        MapEntityTextures.resolve("minecraft:magma_cube", MapRadarModels.MOB_HOSTILE, modernProbe);
    assertEquals(8f / 64f, magma2x.u0, 0.0001f);
    assertEquals(32f / 64f, magma2x.v0, 0.0001f);

    MapEntityTextures.ResolvedIcon rabbit26 =
        MapEntityTextures.resolve("minecraft:rabbit", MapRadarModels.MOB_PASSIVE, modernProbe);
    assertEquals("minecraft:textures/entity/rabbit/rabbit_brown.png", rabbit26.textureId);
    assertEquals(5f / 64f, rabbit26.u0, 0.0001f);
    assertEquals(21f / 64f, rabbit26.v0, 0.0001f);
  }

  @Test
  void nonLivingEntityEligibilityAndItemIconsCoverRealEntitiesAndRejectServerClutter() {
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:spruce_boat"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:bamboo_chest_raft"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:tnt_minecart"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:item_frame"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:glow_item_frame"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:painting"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:leash_knot"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:falling_block"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:experience_orb"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:ender_pearl"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:trident"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:arrow"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:spectral_arrow"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:snowball"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:egg"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:firework_rocket"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:wind_charge"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:fishing_bobber"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:shulker_bullet"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:evoker_fangs"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:llama_spit"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:lightning_bolt"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:area_effect_cloud"));
    assertTrue(MapEntityTextures.isEligibleNonLivingEntity("minecraft:ominous_item_spawner"));

    assertFalse(MapEntityTextures.isEligibleNonLivingEntity("minecraft:text_display"));
    assertFalse(MapEntityTextures.isEligibleNonLivingEntity("minecraft:block_display"));
    assertFalse(MapEntityTextures.isEligibleNonLivingEntity("minecraft:item_display"));
    assertFalse(MapEntityTextures.isEligibleNonLivingEntity("minecraft:interaction"));
    assertFalse(MapEntityTextures.isEligibleNonLivingEntity("minecraft:marker"));

    assertEquals("minecraft:tnt", MapEntityTextures.itemIconForEntity("minecraft:tnt", MapRadarModels.MOB_TNT));
    assertEquals("minecraft:end_crystal",
        MapEntityTextures.itemIconForEntity("minecraft:end_crystal", MapRadarModels.MOB_CRYSTAL));
    assertEquals("minecraft:oak_boat",
        MapEntityTextures.itemIconForEntity("minecraft:boat", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:cherry_chest_boat",
        MapEntityTextures.itemIconForEntity("minecraft:cherry_chest_boat", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:minecart",
        MapEntityTextures.itemIconForEntity("minecraft:spawner_minecart", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:lead",
        MapEntityTextures.itemIconForEntity("minecraft:leash_knot", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:snowball",
        MapEntityTextures.itemIconForEntity("minecraft:snowball", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:arrow",
        MapEntityTextures.itemIconForEntity("minecraft:arrow", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:fishing_rod",
        MapEntityTextures.itemIconForEntity("minecraft:fishing_bobber", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:sand",
        MapEntityTextures.itemIconForEntity("minecraft:falling_block", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:lingering_potion",
        MapEntityTextures.itemIconForEntity("minecraft:area_effect_cloud", MapRadarModels.MOB_OTHER));
    assertEquals("minecraft:lightning_rod",
        MapEntityTextures.itemIconForEntity("minecraft:lightning_bolt", MapRadarModels.MOB_OTHER));
    assertEquals(0x90FF30, MapEntityTextures.defaultTintForEntity("minecraft:experience_orb"));
    assertEquals(0xFFFFFF, MapEntityTextures.defaultTintForEntity("minecraft:zombie"));
  }

  @Test
  void painterDispatchesItemIconsFaceTexturesWithZeroBleedAndNearestFallback() {
    RecordingCanvas canvas = new RecordingCanvas();

    MapRadarModels.MobEntry tnt = new MapRadarModels.MobEntry(
        1, MapRadarModels.MOB_TNT, "minecraft:tnt", "TNT", false, 10, 64, 10, 0f,
        "", 0f, 0f, 0f, 0f, "minecraft:tnt", null, 0xFFFFFF);
    MapEntityIconPainter.drawEntity(canvas, tnt, 50f, 50f, 1.0f, 1.0f, true, false, 64.0, 1.0);
    assertEquals(1, canvas.items.size());
    assertEquals("minecraft:tnt", canvas.items.get(0));
    assertTrue(canvas.textureRegions.isEmpty());
    assertTrue(canvas.mapQuadNearest.isEmpty());

    MapRadarModels.OverlayLayer[] eyes = new MapRadarModels.OverlayLayer[] {
        new MapRadarModels.OverlayLayer("minecraft:textures/entity/enderman/enderman_eyes.png",
            8f / 64f, 8f / 32f, 16f / 64f, 16f / 32f)
    };
    MapRadarModels.MobEntry enderman = new MapRadarModels.MobEntry(
        2, MapRadarModels.MOB_HOSTILE, "minecraft:enderman", "Enderman", false, 12, 64, 12, 0f,
        "minecraft:textures/entity/enderman/enderman.png",
        8f / 64f, 8f / 32f, 16f / 64f, 16f / 32f, "", eyes, 0xFFFFFF);
    MapEntityIconPainter.drawEntity(canvas, enderman, 80f, 80f, 1.0f, 1.0f, true, false, 64.0, 1.0);
    assertEquals(2, canvas.textureRegions.size());
    assertEquals("minecraft:textures/entity/enderman/enderman.png", canvas.textureRegions.get(0));
    assertEquals("minecraft:textures/entity/enderman/enderman_eyes.png", canvas.textureRegions.get(1));
    for (Float r : canvas.textureRadii) {
      assertEquals(0f, r.floatValue(), 0.0001f, "Textured face/overlay must use radius=0 to prevent UV bleed");
    }
    assertTrue(canvas.mapQuadNearest.isEmpty());

    canvas.allowTextureRegion = false;
    MapEntityIconPainter.drawEntity(canvas, enderman, 80f, 80f, 1.0f, 1.0f, true, false, 64.0, 1.0);
    assertEquals(1, canvas.mapQuadNearest.size());
    assertTrue(canvas.mapQuadNearest.get(0), "Fallback 16x16 sprite must always use nearest filtering");

    final Set<String> known = new HashSet<String>();
    MapEntityTextures.ResolvedIcon modded = MapEntityTextures.resolve("mymod:custom_dragon",
        MapRadarModels.MOB_BOSS, new MapEntityTextures.ResourceProbe() {
          @Override public int[] probeTextureSize(String id) { return known.contains(id) ? new int[] { 64, 64 } : null; }
          @Override public boolean hasItem(String id) { return false; }
        });
    assertFalse(modded.hasFaceTexture());
    assertFalse(modded.hasItemIcon());

    int[] composed = new int[32 * 32];
    composed[16 * 32 + 16] = 0xFF00FF00;
    MapRadarModels.MobEntry composedMob = new MapRadarModels.MobEntry(
        3, MapRadarModels.MOB_PASSIVE, "minecraft:axolotl", "Axolotl", false, 15, 64, 15, 0f,
        "", 0f, 0f, 0f, 0f, "", null, 0xFFFFFF, "ent32_axolotl", composed, 32, 1L);
    int beforeQuads = canvas.mapQuadNearest.size();
    MapEntityIconPainter.drawEntity(canvas, composedMob, 100f, 100f, 1.0f, 1.0f, true, false, 64.0, 1.0);
    assertEquals(beforeQuads + 1, canvas.mapQuadNearest.size());
    assertFalse(canvas.mapQuadNearest.get(canvas.mapQuadNearest.size() - 1));
    MapEntityIconPainter.drawEntity(canvas, composedMob, 100f, 100f, 3.0f, 1.0f, true, false, 64.0, 1.0);
    assertTrue(canvas.mapQuadNearest.get(canvas.mapQuadNearest.size() - 1));
  }

  @Test
  void composerProduces3DMojangModelsAndNativeItemsWithoutAwt() throws Exception {
    final byte[] png64 = makeSyntheticPng(64, 64, 0xFF64A848);
    final byte[] png32 = makeSyntheticPng(32, 32, 0xFFE89030);
    final byte[] png16 = makeSyntheticPng(16, 16, 0xFFD946EF);

    MapEntityIconComposer.AssetLoader richLoader = new MapEntityIconComposer.AssetLoader() {
      @Override public byte[] loadResourceBytes(String textureId) {
        if (textureId.contains("particle/") || textureId.contains("block/")) return png16;
        if (textureId.contains("fish/") || textureId.contains("vex") || textureId.contains("allay")) {
          return png32;
        }
        return png64;
      }
      @Override public boolean hasItem(String itemId) { return true; }
    };

    for (String[] pair : new String[][] {
        { "minecraft:tnt", "minecraft:tnt" },
        { "minecraft:end_crystal", "minecraft:end_crystal" },
        { "minecraft:oak_boat", "minecraft:oak_boat" },
        { "minecraft:tnt_minecart", "minecraft:tnt_minecart" },
        { "minecraft:arrow", "minecraft:arrow" },
        { "minecraft:snowball", "minecraft:snowball" },
        { "minecraft:ender_pearl", "minecraft:ender_pearl" },
        { "minecraft:trident", "minecraft:trident" }
    }) {
      assertEquals(pair[1],
          MapEntityIconComposer.compose(pair[0], MapRadarModels.MOB_OTHER, richLoader).itemIconId,
          "Expected native item icon for " + pair[0]);
    }

    for (String entityId : new String[] {
        "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
        "minecraft:nautilus", "minecraft:zombie_nautilus", "minecraft:squid", "minecraft:glow_squid",
        "minecraft:mooshroom", "minecraft:llama", "minecraft:panda", "minecraft:wolf",
        "minecraft:guardian", "minecraft:elder_guardian", "minecraft:vex", "minecraft:allay",
        "minecraft:magma_cube", "minecraft:sniffer", "minecraft:snow_golem", "minecraft:creaking",
        "minecraft:silverfish", "minecraft:endermite", "minecraft:shulker_bullet",
        "minecraft:evoker_fangs", "minecraft:llama_spit", "minecraft:area_effect_cloud",
        "minecraft:lightning_bolt", "minecraft:experience_orb"
    }) {
      int kind = "minecraft:elder_guardian".equals(entityId)
          ? MapRadarModels.MOB_BOSS : MapRadarModels.MOB_HOSTILE;
      MapEntityIconComposer.ComposedIcon icon =
          MapEntityIconComposer.compose(entityId, kind, richLoader);
      assertFalse(icon.hasItemIcon(), "Living mob / 3D entity must NOT use dead item icon: " + entityId);
      assertTrue(icon.hasComposedPixels(), "Expected composed 32x32 sprite for " + entityId);
      assertEquals(MapEntityIconComposer.ICON_SIZE, icon.size);
      int nonZero = 0;
      for (int p : icon.pixels) if ((p >>> 24) > 0) nonZero++;
      assertTrue(nonZero > 40, "3D sprite should have visible outlined pixels for " + entityId);
    }

    MapEntityIconComposer.DecodedImage tex = MapEntityIconComposer.decodePng(png64);
    assertNotNull(tex);
    MapEntity3DRenderer.Scene scene = new MapEntity3DRenderer.Scene()
        .box(tex, 64, 64, 0, 0, -4, -4, -4, 8, 8, 8);
    int[] out = new int[32 * 32];
    assertTrue(MapEntity3DRenderer.render(scene, out, 32, 3, -24f, 14f, -15f));
    int visible = 0;
    for (int px : out) if ((px >>> 24) > 200) visible++;
    assertTrue(visible > 150, "3D orthographic cube should fill center of 32x32 canvas");

    assertTrue(MapEntityTextures.isTechnicalExcludedEntity("minecraft:text_display"));
    assertTrue(MapEntityTextures.isTechnicalExcludedEntity("minecraft:block_display"));
    assertTrue(MapEntityTextures.isTechnicalExcludedEntity("minecraft:item_display"));
    assertTrue(MapEntityTextures.isTechnicalExcludedEntity("minecraft:interaction"));
    assertTrue(MapEntityTextures.isTechnicalExcludedEntity("minecraft:marker"));
    assertFalse(MapEntityTextures.isTechnicalExcludedEntity("minecraft:experience_orb"));
    assertFalse(MapEntityTextures.isTechnicalExcludedEntity("minecraft:arrow"));
    assertFalse(MapEntityTextures.isTechnicalExcludedEntity("mymod:custom_vehicle"));
  }

  private static byte[] makeSyntheticPng(int w, int h, int argb) throws Exception {
    java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
    int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
    for (int y = 0; y < h; y++) {
      raw.write(0);
      for (int x = 0; x < w; x++) {
        raw.write(r); raw.write(g); raw.write(b); raw.write(a);
      }
    }
    java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
    java.util.zip.Deflater def = new java.util.zip.Deflater();
    try (java.util.zip.DeflaterOutputStream dos = new java.util.zip.DeflaterOutputStream(compressed, def)) {
      dos.write(raw.toByteArray());
    }
    def.end();

    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    png.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
    java.io.ByteArrayOutputStream ihdr = new java.io.ByteArrayOutputStream();
    writeIntBE(ihdr, w); writeIntBE(ihdr, h);
    ihdr.write(8); ihdr.write(6); ihdr.write(0); ihdr.write(0); ihdr.write(0);
    writeChunk(png, "IHDR", ihdr.toByteArray());
    writeChunk(png, "IDAT", compressed.toByteArray());
    writeChunk(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  private static void writeChunk(java.io.ByteArrayOutputStream out, String type, byte[] data) throws Exception {
    writeIntBE(out, data.length);
    byte[] tb = type.getBytes("US-ASCII");
    out.write(tb);
    out.write(data);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(tb); crc.update(data);
    writeIntBE(out, (int) crc.getValue());
  }

  private static void writeIntBE(java.io.ByteArrayOutputStream out, int v) {
    out.write((v >>> 24) & 0xFF); out.write((v >>> 16) & 0xFF);
    out.write((v >>> 8) & 0xFF); out.write(v & 0xFF);
  }
}
