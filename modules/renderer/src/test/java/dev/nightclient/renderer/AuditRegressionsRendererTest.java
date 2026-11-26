package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.MapRadarModels;
import dev.nightclient.api.PlayerSkin;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.cape.WavyCapeConfig;
import dev.nightclient.renderer.cape.WavyCapeMesh;
import dev.nightclient.renderer.cape.WavyCapePose;
import dev.nightclient.renderer.cape.WavyCapeSimulation;
import dev.nightclient.renderer.map.MapColorShading;
import dev.nightclient.renderer.map.MapEntityIconPainter;
import dev.nightclient.renderer.map.MapRegionTile;
import dev.nightclient.renderer.map.MinimapRenderer;
import dev.nightclient.renderer.map.RegionCacheManager;
import dev.nightclient.renderer.map.WorldMapRenderer;
import dev.nightclient.renderer.model.CosmeticModelLoader;
import dev.nightclient.renderer.model.CosmeticModelMesh;
import dev.nightclient.renderer.player.DefaultSkins;
import dev.nightclient.renderer.player.PlayerPreviewRenderer;
import dev.nightclient.renderer.player.PlayerSkinData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditRegressionsRendererTest {
  @TempDir Path tempDir;

  @Test
  void proxyRealmSwitchFlushesDirtyTilesAndBumpsActiveEpoch() {
    RegionCacheManager cache = new RegionCacheManager(tempDir);
    try {
      Settings.Waypoints wps = new Settings.Waypoints();
      cache.beginWorldSession(false, "play.holyworld.ru", "", "", "minecraft:overworld", 0L, "0_64_0");
      assertTrue(cache.feedRealmLabel("Анархия #1", wps));
      long epoch1 = cache.activeEpoch();
      int[] rgb = new int[256];
      short[] h = new short[256];
      byte[] light = new byte[256];
      for (int i = 0; i < 256; i++) {
        rgb[i] = 0x55AA44;
        h[i] = 70;
        light[i] = 15;
      }
      cache.ingestChunk(0, 0, rgb, h, light);
      assertEquals(1, cache.loadedRegionCount());

      boolean switched = cache.feedRealmLabel("Анархия #12", wps);
      assertTrue(switched);
      assertTrue(cache.activeEpoch() > epoch1);
      assertEquals(0, cache.loadedRegionCount());

      RegionCacheManager.MinimapWindow win =
          cache.composeMinimapWindow(8.0, 8.0, true, false, 1, 1.0f);
      assertNotNull(win);
      assertEquals(RegionCacheManager.MINIMAP_WINDOW_SIZE, win.size);
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void mapRegionTileRevisionsAreGloballyMonotonicAndSafeAgainstConcurrentEdit() {
    MapRegionTile t1 = new MapRegionTile(0, 0);
    MapRegionTile t2 = new MapRegionTile(1, 0);
    int[] rgb = new int[256];
    short[] h = new short[256];
    byte[] l = new byte[256];
    for (int i = 0; i < 256; i++) {
      rgb[i] = 0x336699;
      h[i] = 64;
    }
    t1.putChunk(0, 0, rgb, h, l, null, null, true, false);
    long rev1 = t1.revision();
    t2.putChunk(0, 0, rgb, h, l, null, null, true, false);
    long rev2 = t2.revision();
    assertTrue(rev2 > rev1, "Global revisions must be strictly monotonic across tiles");
    t1.putChunk(1, 0, rgb, h, l, null, null, true, false);
    t1.clearDiskDirtyIfRevision(rev1);
    assertTrue(t1.isDiskDirty(), "Stale revision flush must not clear newer dirty flag");
    t1.clearDiskDirtyIfRevision(t1.revision());
    assertFalse(t1.isDiskDirty());
  }

  @Test
  void cosmeticModelLoaderPreservesUnitUvAndBlocksMtlPathTraversal() throws Exception {
    Path subDir = tempDir.resolve("models");
    Files.createDirectories(subDir);
    Path secret = tempDir.resolve("secret.mtl");
    Files.write(secret, "newmtl secret\nKd 1 0 0\n".getBytes(StandardCharsets.UTF_8));
    String obj =
        "mtllib ../secret.mtl\n"
            + "usemtl secret\n"
            + "v 0 0 0\nv 1 0 0\nv 0 1 0\n"
            + "vt 0.0 0.0\nvt 1.0 0.0\nvt 1.0 1.0\n"
            + "f 1/1 2/2 3/3\n";
    Path objFile = subDir.resolve("sword.obj");
    Files.write(objFile, obj.getBytes(StandardCharsets.UTF_8));
    CosmeticModelMesh mesh = CosmeticModelLoader.loadFile(objFile, false);
    assertNotNull(mesh);
    assertTrue(mesh.vertexCount >= 3);
    java.nio.ByteBuffer vb = mesh.copyVerticesToDirectBuffer();
    boolean foundMaxU = false;
    for (int i = 0; i < mesh.vertexCount; i++) {
      int base = i * CosmeticModelMesh.VERTEX_STRIDE;
      int g = vb.get(base + 13) & 255;
      assertTrue(g > 150, "Blocked ../secret.mtl (Kd 1 0 0) must not tint vertex pure red");
      float u = vb.getFloat(base + 16);
      if (u > 0.5f) foundMaxU = true;
    }
    assertTrue(foundMaxU, "wrap01(1.0f) must preserve 1.0f instead of wrapping to 0.0f");
  }

  @Test
  void playerPreviewRendersTranslucentHatOverlayWithoutPunchingThroughHead() {
    PlayerSkin base = DefaultSkins.forModel(false);
    int[] body = base.bodyPixels().clone();
    for (int y = 8; y < 16; y++) {
      for (int x = 40; x < 48; x++) {
        body[y * 64 + x] = 0x802266FF;
      }
    }
    PlayerSkinData skin = new PlayerSkinData(body, null, false);
    int w = 96, h = 144;
    int[] out = new int[w * h];
    new PlayerPreviewRenderer().render(skin, 0f, 0f, 1f, 0f, 0f, 0f, w, h, out);
    int opaquePixels = 0;
    for (int px : out) if ((px >>> 24) > 200) opaquePixels++;
    assertTrue(opaquePixels > 200, "Player preview with translucent visor must render solidly");
  }

  @Test
  void wavyCapeSimulationStaysSmoothAndFiniteAtHighFpsAndBadInputs() {
    WavyCapeConfig cfg = new WavyCapeConfig(true, WavyCapeConfig.STYLE_SMOOTH,
        WavyCapeConfig.QUALITY_HIGH, WavyCapeConfig.PLAYERS_ALL, 1.0f, 0.28f, 0.34f, 1.05f, 1.15f,
        0.65f, 0.90f, true, true, 0.95f, 1.0f, 1.0f, 0.14f, 0.45f, true, 64.0f);
    WavyCapeSimulation sim = new WavyCapeSimulation(7, cfg);
    WavyCapePose pose = new WavyCapePose();
    pose.entityId = 7;
    pose.worldX = 10.0;
    pose.worldY = 64.0;
    pose.worldZ = 10.0;
    long t = 1_000_000_000L;
    for (int f = 0; f < 20; f++) {
      pose.worldX += 0.05;
      pose.timeNanos = t + f * 3_000_000L;
      WavyCapeMesh mesh = sim.updateAndBuild(pose, cfg);
      assertTrue(mesh.quadCount > 0);
      assertFalse(mesh.hasNaN(), "Cape mesh must remain finite at >240 FPS");
    }
    pose.bodyRot = Float.NaN;
    pose.timeNanos = t + 100_000_000L;
    WavyCapeMesh recovered = sim.updateAndBuild(pose, cfg);
    assertFalse(recovered.hasNaN(), "NaN pose input must not corrupt cape");
  }

  @Test
  void mapEntityIconPainterHandlesNativeCropSymmetricFallbackExplosivesAndVehicles() {
    final int[] counts = new int[5];
    final int[][] lastFallbackPixels = new int[1][];
    final boolean[] allowNative = {false};
    Canvas probe = new Canvas() {
      @Override public void rect(float x, float y, float w, float h, int c) {}
      @Override public void roundedRect(float x, float y, float w, float h, float r, int c) {}
      @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
      @Override public void gradientRect(float x, float y, float w, float h, int t, int b) {}
      @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
      @Override public void circle(float cx, float cy, float r, int c) {}
      @Override public void fillPolygon(float cx, float cy, float[] xy, int c) { counts[4]++; }
      @Override public void text(String v, float x, float y, float s, int c, boolean b) { counts[3]++; }
      @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0f : v.length() * 4f; }
      @Override public void pushClip(float x, float y, float w, float h) {}
      @Override public void popClip() {}
      @Override public void item(String id, float x, float y, float s, float a) { counts[2]++; }
      @Override public void backdrop(float x, float y, float w, float h, float r, float br, int q, float o) {}
      @Override public void texture(String id, float x, float y, float w, float h, float r, float ts, int c) {}
      @Override
      public boolean textureRegion(String id, float x, float y, float w, float h, float r,
          float u0, float v0, float u1, float v1, float a, int tint) {
        if (!allowNative[0]) return false;
        counts[0]++;
        return true;
      }
      @Override
      public void mapQuad(String key, long rev, int[] px, int tw, int th, float x, float y,
          float w, float h, float r, float uc, float vc, float uh, float vh, float ang,
          boolean nearest, int argb) {
        counts[1]++;
        lastFallbackPixels[0] = px;
      }
    };

    MapRadarModels.MobEntry creeper = new MapRadarModels.MobEntry(
        1, MapRadarModels.MOB_HOSTILE, "minecraft:creeper", "Creeper", false,
        10.0, 80.0, 10.0, 0f);
    MapEntityIconPainter.drawEntity(probe, creeper, 50f, 50f, 1f, 1f, true, true, 64.0, 1.0);
    assertEquals(1, counts[1]);
    assertEquals(0, counts[4], "Mobs must not render elevation arrow triangles on map");
    assertNotNull(lastFallbackPixels[0]);
    assertEquals(256, lastFallbackPixels[0].length);
    for (int col = 0; col < 8; col++) {
      assertEquals(lastFallbackPixels[0][5 * 16 + col], lastFallbackPixels[0][5 * 16 + (15 - col)]);
    }

    MapRadarModels.MobEntry tnt = new MapRadarModels.MobEntry(
        2, MapRadarModels.MOB_TNT, "minecraft:tnt", "TNT", false, 0, 64, 0, 0f);
    MapRadarModels.MobEntry boat = new MapRadarModels.MobEntry(
        3, MapRadarModels.MOB_OTHER, "minecraft:oak_chest_boat", "Boat", false, 0, 64, 0, 0f);
    MapEntityIconPainter.drawEntity(probe, tnt, 20f, 20f, 1f, 1f, true, false, 64.0, 1.0);
    MapEntityIconPainter.drawEntity(probe, boat, 30f, 30f, 1f, 1f, true, false, 64.0, 1.0);
    assertEquals(3, counts[1]);
    assertEquals(0, counts[2]);

    int[] horsePx = MapEntityIconPainter.spriteForType("minecraft:horse", MapRadarModels.MOB_PASSIVE);
    int[] chickenPx = MapEntityIconPainter.spriteForType("minecraft:chicken", MapRadarModels.MOB_PASSIVE);
    int[] dragonPx = MapEntityIconPainter.spriteForType("minecraft:ender_dragon", MapRadarModels.MOB_BOSS);
    assertEquals(256, horsePx.length);
    assertEquals(256, chickenPx.length);
    assertEquals(256, dragonPx.length);
    assertFalse(java.util.Arrays.equals(horsePx, chickenPx));
    assertFalse(java.util.Arrays.equals(horsePx, dragonPx));
  }

  @Test
  void minimapAndWorldMapDoNotRenderArrowsOnOtherPlayersOrMobs() {
    RegionCacheManager cache = new RegionCacheManager(tempDir);
    try {
      cache.beginWorldSession(false, "local", "", "", "minecraft:overworld", 0L, "0_64_0");
      Settings settings = new Settings();
      settings.minimap.enabled = true;
      settings.minimap.shape = "square";
      settings.minimap.arrowStyle = "classic";
      settings.minimap.showWaypoints = false;
      settings.minimap.showItems = false;

      MapRadarModels.RadarSnapshot radar = new MapRadarModels.RadarSnapshot(
          true, 10.0, 64.0, 10.0, 90f, "local", "minecraft:overworld", "", "",
          java.util.Arrays.asList(
              new MapRadarModels.PlayerEntry(
                  "self", "Self", "skin:self", 10.0, 64.0, 10.0, 90f, false, false, true, 20f, 20f),
              new MapRadarModels.PlayerEntry(
                  "p1", "HighPlayer", "skin:high", 18.0, 92.0, 12.0, 45f, false, false, false, 20f, 20f),
              new MapRadarModels.PlayerEntry(
                  "p2", "LowPlayer", "", 4.0, 38.0, 14.0, 180f, false, false, false, 20f, 20f)),
          Collections.<MapRadarModels.ItemEntry>emptyList(),
          java.util.Arrays.asList(
              new MapRadarModels.MobEntry(
                  1, MapRadarModels.MOB_HOSTILE, "minecraft:creeper", "Creeper", false,
                  14.0, 90.0, 14.0, 0f),
              new MapRadarModels.MobEntry(
                  2, MapRadarModels.MOB_PASSIVE, "minecraft:cow", "Cow", false,
                  6.0, 40.0, 6.0, 0f)));

      final int[] polygons = {0};
      Canvas spy = new Canvas() {
        @Override public void rect(float x, float y, float w, float h, int c) {}
        @Override public void gradientRect(float x, float y, float w, float h, int t, int b) {}
        @Override public void roundedRect(float x, float y, float w, float h, float r, int c) {}
        @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
        @Override public void circle(float cx, float cy, float r, int c) {}
        @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
        @Override public void fillPolygon(float cx, float cy, float[] xy, int c) { polygons[0]++; }
        @Override public void text(String v, float x, float y, float s, int c, boolean b) {}
        @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0f : v.length() * 4f; }
        @Override public void pushClip(float x, float y, float w, float h) {}
        @Override public void popClip() {}
        @Override public void item(String id, float x, float y, float s, float a) {}
        @Override public void backdrop(float x, float y, float w, float h, float r, float br, int q, float o) {}
        @Override public void texture(String id, float x, float y, float w, float h, float r, float ts, int c) {}
      };

      settings.minimap.showPlayers = false;
      settings.minimap.showHostileMobs = false;
      settings.minimap.showPassiveMobs = false;
      polygons[0] = 0;
      MinimapRenderer.renderLive(spy, settings, cache, radar, 960, 540, 1.0);
      int selfOnlyMinimapPolys = polygons[0];
      assertTrue(selfOnlyMinimapPolys > 0, "Local player marker must still render directional arrow");

      settings.minimap.showPlayers = true;
      settings.minimap.showPlayerHeads = true;
      settings.minimap.showHostileMobs = true;
      settings.minimap.showPassiveMobs = true;
      polygons[0] = 0;
      MinimapRenderer.renderLive(spy, settings, cache, radar, 960, 540, 1.0);
      assertEquals(selfOnlyMinimapPolys, polygons[0],
          "Minimap must not render arrow/elevation triangles on other players or mobs");

      settings.minimap.showPlayerHeads = false;
      settings.minimap.mobIconStyle = "dots";
      polygons[0] = 0;
      MinimapRenderer.renderLive(spy, settings, cache, radar, 960, 540, 1.0);
      assertEquals(selfOnlyMinimapPolys, polygons[0],
          "Dot-mode other players/mobs must not render arrow/elevation triangles on minimap");

      WorldMapRenderer.ViewState state = new WorldMapRenderer.ViewState();
      state.centerX = 10.0;
      state.centerZ = 10.0;
      state.zoom = 1.0f;
      settings.worldMap.showWaypoints = false;
      settings.worldMap.showItems = false;
      settings.worldMap.showPlayers = false;
      settings.worldMap.showMobs = false;
      polygons[0] = 0;
      WorldMapRenderer.render(spy, settings, cache, radar, state, 800, 600, 400, 300, 1.0);
      int selfOnlyWorldPolys = polygons[0];
      assertTrue(selfOnlyWorldPolys > 0, "World map must still render local player arrow");

      settings.worldMap.showPlayers = true;
      settings.worldMap.showMobs = true;
      settings.minimap.mobIconStyle = "icons";
      polygons[0] = 0;
      WorldMapRenderer.render(spy, settings, cache, radar, state, 800, 600, 400, 300, 1.0);
      assertEquals(selfOnlyWorldPolys, polygons[0],
          "World map must not render arrow/elevation triangles on other players or mobs");
    } finally {
      cache.shutdown();
    }
  }
}
