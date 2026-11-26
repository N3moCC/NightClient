package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.Keys;
import dev.nightclient.api.MapRadarModels;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.renderer.map.MinimapRenderer;
import dev.nightclient.renderer.map.RegionCacheManager;
import dev.nightclient.ui.skin.UiCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorldMapAndMinimapUiTest {

  private static final class RecordingCanvas implements Canvas {
    int mapQuads, skinHeads, inventoryItems, texts, triangles, circles, rects;
    float lastTerrainAngle = Float.NaN;
    @Override public void rect(float x, float y, float w, float h, int c) { rects++; }
    @Override public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) { triangles++; }
    @Override public void roundedRect(float x, float y, float w, float h, float r, int c) { rects++; }
    @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    @Override public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
    @Override public void circle(float cx, float cy, float r, int c) { circles++; }
    @Override public void text(String v, float x, float y, float s, int c, boolean b) { texts++; }
    @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0 : v.length() * s * 0.55f; }
    @Override public void pushClip(float x, float y, float w, float h) {}
    @Override public void popClip() {}
    @Override public void item(String id, float x, float y, float s, float a) {}
    @Override public void inventoryItem(InventorySlot slot, float x, float y, float s, float a) { inventoryItems++; }
    @Override public boolean skinHead(String id, float x, float y, float s, float r, float a, int tint) { skinHeads++; return true; }
    @Override public void mapQuad(String key, long rev, int[] px, int tw, int th, float x, float y,
        float w, float h, float r, float uc, float vc, float uh, float vh, float ang, boolean near, int c) {
      mapQuads++;
      if (tw > 16) lastTerrainAngle = ang;
    }
    @Override public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
    @Override public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}
  }

  @Test
  void defaultsMatchApprovedPreferencesAndCatalogRegistersBothModules() {
    Settings s = new Settings();
    s.validate();
    assertEquals("square", s.minimap.shape);
    assertEquals(1.35f, s.minimap.reliefStrength);
    s.minimap.reliefStrength = 0.1f;
    s.validate();
    assertEquals(0.5f, s.minimap.reliefStrength);
    s.minimap.reliefStrength = 3.2f;
    s.validate();
    assertEquals(3.2f, s.minimap.reliefStrength);
    s.minimap.reliefStrength = 1.35f;
    assertEquals(0f, s.minimap.anchorX);
    assertEquals(0f, s.minimap.anchorY);
    assertTrue(s.minimap.showInvisiblePlayers);
    assertTrue(s.worldMap.enabled);

    UiCatalog catalog = new UiCatalog(s);
    UiCatalog.Module mm = catalog.module("minimap");
    assertEquals("minimap", mm.id);
    assertEquals("hud", mm.category);
    assertTrue(mm.hud);
    assertNotNull(catalog.control("minimap.scale"));

    UiCatalog.Module wm = catalog.module("worldMap");
    assertEquals("worldMap", wm.id);
    assertEquals("world", wm.category);
    assertFalse(wm.hud);
  }

  @Test
  void minimapRendersPlayersInvisiblesItemsWaypointsInBothShapes() throws Exception {
    Path temp = Files.createTempDirectory("tv-mm-render");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "testsave", "Test Save", "minecraft:overworld", 42L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x6AA845);
      Arrays.fill(heights, (short) 64);
      cache.ingestChunk(0, 0, rgb, heights, light);

      Settings s = new Settings();
      s.minimap.enabled = true;
      s.waypoints.add("Base", 150, 64, -120, Waypoint.DEFAULT_DIMENSION,
          cache.identity().worldId(), cache.identity().worldLabel());

      MapRadarModels.RadarSnapshot radar = new MapRadarModels.RadarSnapshot(
          true, 8, 65, 8, 180f, Waypoint.DEFAULT_DIMENSION, "plains",
          cache.identity().worldId(), cache.identity().worldLabel(),
          Arrays.asList(
              new MapRadarModels.PlayerEntry("u1", "Self", "skin:self", 8, 65, 8, 180f, false, false, true, 20, 20),
              new MapRadarModels.PlayerEntry("u2", "Steve", "skin:steve", 20, 65, 12, 90f, false, false, false, 20, 20),
              new MapRadarModels.PlayerEntry("u3", "Ghost", "skin:ghost", -5, 76, 4, 0f, true, true, false, 18, 20)),
          Arrays.asList(
              new MapRadarModels.ItemEntry(1, 400, "minecraft:totem_of_undying", "Totem", 1, 1, 12, 65, 10, true),
              new MapRadarModels.ItemEntry(2, 401, "minecraft:cobblestone", "Cobble", 32, 2, 14, 65, 10, false)),
          Arrays.asList(
              new MapRadarModels.MobEntry(3, MapRadarModels.MOB_TNT, 16, 65, 16, 0f)));

      RecordingCanvas square = new RecordingCanvas();
      MinimapRenderer.renderLive(square, s, cache, radar, 960, 540, 1.5);
      assertEquals(2, square.mapQuads);
      assertEquals(2, square.skinHeads);
      assertEquals(2, square.inventoryItems);
      assertTrue(square.triangles >= 4);

      s.minimap.showInvisiblePlayers = false;
      s.minimap.shape = "circle";
      s.minimap.onlyValuableItems = true;
      RecordingCanvas circle = new RecordingCanvas();
      MinimapRenderer.renderLive(circle, s, cache, radar, 960, 540, 2.0);
      assertEquals(2, circle.mapQuads);
      assertEquals(1, circle.skinHeads);
      assertEquals(1, circle.inventoryItems);
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void worldMapControllerPansZoomsAndCreatesTempWaypointsOnRightClick() throws Exception {
    Path temp = Files.createTempDirectory("tv-wm-ctrl");
    final RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "wmtest", "WM Test", "minecraft:overworld", 99L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x6AA845);
      Arrays.fill(heights, (short) 70);
      cache.ingestChunk(0, 0, rgb, heights, light);

      final Settings settings = new Settings();
      final boolean[] closed = new boolean[1];
      final String[] clip = new String[1];
      final MapRadarModels.RadarSnapshot radar = new MapRadarModels.RadarSnapshot(
          true, 10, 71, 20, 180f, Waypoint.DEFAULT_DIMENSION, "",
          cache.identity().worldId(), cache.identity().worldLabel(), null, null, null);

      WorldMapController ctrl = new WorldMapController(settings, new WorldMapController.Host() {
        @Override public RegionCacheManager mapCache() { return cache; }
        @Override public MapRadarModels.RadarSnapshot radarSnapshot() { return radar; }
        @Override public void openWaypointCreate(double x, double y, double z) {}
        @Override public void clipboard(String value) { clip[0] = value; }
        @Override public void teleportTo(int x, int y, int z) {}
        @Override public void checkpoint() {}
        @Override public void changed() {}
        @Override public void closeWorldMap() { closed[0] = true; }
      });

      ctrl.onOpen();
      assertEquals(10.0, ctrl.viewState().centerX, 0.01);
      assertEquals(20.0, ctrl.viewState().centerZ, 0.01);

      RecordingCanvas canvas = new RecordingCanvas();
      ctrl.render(canvas, 800, 600, 400, 300, 1.0);
      assertTrue(canvas.mapQuads >= 1);

      assertTrue(ctrl.mouseClicked(400, 300, 0));
      assertTrue(ctrl.mouseDragged(450, 320, 0, 50, 20));
      ctrl.mouseReleased(450, 320, 0);
      assertEquals(-40.0, ctrl.viewState().centerX, 0.1);
      assertEquals(0.0, ctrl.viewState().centerZ, 0.1);

      float beforeZoom = ctrl.viewState().zoom;
      assertTrue(ctrl.mouseScrolled(400, 300, 1.0, 0));
      assertTrue(ctrl.viewState().zoom > beforeZoom);

      assertTrue(ctrl.mouseClicked(400, 300, 1));
      assertTrue(ctrl.viewState().contextMenuOpen);
      ctrl.render(canvas, 800, 600, 410, 330, 1.2);

      boolean clickedTemp = false;
      for (dev.nightclient.renderer.map.WorldMapRenderer.HitBox hit : ctrl.viewState().hits) {
        if ("ctx-temp-wp".equals(hit.action)) {
          assertTrue(ctrl.mouseClicked(hit.x + 5, hit.y + 5, 0));
          clickedTemp = true;
          break;
        }
      }
      assertTrue(clickedTemp);
      assertEquals(1, settings.waypoints.points.size());
      assertTrue(settings.waypoints.points.get(0).temp);

      assertTrue(ctrl.keyPressed(Keys.ESCAPE, 0));
      assertTrue(closed[0]);
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void minimapShapesArrowStylesItemBackgroundMobIconsAndEnlargeWork() throws Exception {
    Path temp = Files.createTempDirectory("tv-mm-shapes");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "shapetest", "Shape Test", "minecraft:overworld", 55L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x5E9D44);
      Arrays.fill(heights, (short) 64);
      cache.ingestChunk(0, 0, rgb, heights, light);

      Settings s = new Settings();
      s.minimap.enabled = true;
      assertFalse(s.minimap.itemBackground, "Item circular background should default to OFF");

      MapRadarModels.RadarSnapshot radar = new MapRadarModels.RadarSnapshot(
          true, 8, 65, 8, 90f, Waypoint.DEFAULT_DIMENSION, "",
          cache.identity().worldId(), cache.identity().worldLabel(),
          Arrays.asList(new MapRadarModels.PlayerEntry(
              "u1", "Self", "skin:self", 8, 65, 8, 90f, false, false, true, 20, 20)),
          Arrays.asList(new MapRadarModels.ItemEntry(
              1, 400, "minecraft:cobblestone", "Cobble", 16, 1, 14, 65, 10, false)),
          Arrays.asList(
              new MapRadarModels.MobEntry(10, MapRadarModels.MOB_HOSTILE, "minecraft:creeper", "Creeper", false, 18, 65, 12, 0f),
              new MapRadarModels.MobEntry(11, MapRadarModels.MOB_PASSIVE, "minecraft:pig", "Pig", true, 4, 65, 12, 0f),
              new MapRadarModels.MobEntry(12, MapRadarModels.MOB_NEUTRAL, "minecraft:villager", "Villager", false, 12, 65, 4, 0f),
              new MapRadarModels.MobEntry(13, MapRadarModels.MOB_BOSS, "minecraft:warden", "Warden", false, 16, 65, 16, 0f),
              new MapRadarModels.MobEntry(14, MapRadarModels.MOB_OTHER, "minecraft:oak_boat", "Boat", false, 6, 65, 6, 0f)));

      s.minimap.showHostileMobs = false;
      s.minimap.showPassiveMobs = false;
      s.minimap.showNeutralMobs = false;
      s.minimap.showOtherEntities = false;
      RecordingCanvas noBg = new RecordingCanvas();
      MinimapRenderer.renderLive(noBg, s, cache, radar, 960, 540, 1.0);
      s.minimap.itemBackground = true;
      RecordingCanvas withBg = new RecordingCanvas();
      MinimapRenderer.renderLive(withBg, s, cache, radar, 960, 540, 1.0);
      assertTrue(withBg.circles > noBg.circles, "Enabling itemBackground draws a circle behind dropped items");

      s.minimap.showHostileMobs = true;
      s.minimap.showPassiveMobs = true;
      s.minimap.showNeutralMobs = true;
      s.minimap.showOtherEntities = true;
      String[] shapes = {"square", "sharp", "circle", "diamond", "hexagon", "octagon"};
      String[] arrows = {"classic", "modern", "pointer", "head", "dot"};
      for (int i = 0; i < shapes.length; i++) {
        s.minimap.shape = shapes[i];
        s.minimap.arrowStyle = arrows[i % arrows.length];
        RecordingCanvas c = new RecordingCanvas();
        MinimapRenderer.renderLive(c, s, cache, radar, 960, 540, 0.5f, 1.0 + i);
        assertEquals(6, c.mapQuads, "Shape " + shapes[i] + " draws terrain quad + 5 crisp 16x16 mob/entity quads");
        assertTrue(c.rects >= 5, "Map mob icons render shadow/badge rects for shape " + shapes[i]);
      }

      s.minimap.anchorX = 0f;
      s.minimap.anchorY = 0f;
      s.minimap.offsetX = 4f;
      s.minimap.offsetY = 4f;
      float[] normalB = MinimapRenderer.computeBounds(s, 960, 540, 0f);
      float[] firstFrameB = MinimapRenderer.computeBounds(s, 960, 540, 0.02f);
      float[] bigB = MinimapRenderer.computeBounds(s, 960, 540, 1f);
      assertEquals(4f, normalB[0], 0.01f);
      assertEquals(4f, firstFrameB[0], 0.01f, "Top-left corner X must NOT jump on frame 1 of Z-enlarge");
      assertEquals(4f, bigB[0], 0.01f, "Top-left corner X must stay pinned at full Z-enlarge");
      assertEquals(4f, firstFrameB[1], 0.01f, "Top-left corner Y must NOT jump on frame 1 of Z-enlarge");
      assertTrue(bigB[2] > normalB[2] * 1.5f, "Enlarged minimap should significantly expand width");

      s.minimap.anchorX = 1f;
      s.minimap.offsetX = -4f;
      float[] trRest = MinimapRenderer.computeBounds(s, 960, 540, 0f);
      float[] trMid = MinimapRenderer.computeBounds(s, 960, 540, 0.5f);
      float[] trBig = MinimapRenderer.computeBounds(s, 960, 540, 1f);
      assertEquals(trRest[0] + trRest[2], trMid[0] + trMid[2], 0.05f, "Top-right right edge stays locked mid-animation");
      assertEquals(trRest[0] + trRest[2], trBig[0] + trBig[2], 0.05f, "Top-right right edge stays locked at full enlarge");
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void vanillaMapSettingsAndWaypointsMenusOperateEndToEnd() throws Exception {
    final Settings s = new Settings();
    final int[] clearedWorld = {0};
    final int[] clearedAll = {0};
    final String[] bound = {""};
    final boolean[] closed = {false};

    MapSettingsController mapSettings = new MapSettingsController(s, new MapSettingsController.Host() {
      @Override public RegionCacheManager mapCache() { return null; }
      @Override public String keyBindingLabel(String id) { return "Y"; }
      @Override public void setKeyBinding(String id, String nativeKeyName) { bound[0] = id + "=" + nativeKeyName; }
      @Override public void clearCurrentWorldCache() { clearedWorld[0]++; }
      @Override public void clearAllMapCache() { clearedAll[0]++; }
      @Override public void openWorldMap() {}
      @Override public void openWaypointsMenu() {}
      @Override public void openHudEditor() {}
      @Override public void checkpoint() {}
      @Override public void changed() {}
      @Override public void close() { closed[0] = true; }
    });
    mapSettings.onOpen();
    RecordingCanvas c = new RecordingCanvas();
    mapSettings.render(c, 640, 360, 320, 180, 1.0);
    assertTrue(c.rects > 10);
    assertTrue(mapSettings.mouseClicked(10, 10, 0));
    assertTrue(closed[0]);
    closed[0] = false;
    mapSettings.onOpen();
    mapSettings.render(c, 640, 360, 320, 180, 1.1);
    assertTrue(mapSettings.keyPressed(Keys.ESCAPE, 0));
    assertTrue(closed[0]);

    final String[] lastTp = {""};
    WaypointsScreenController wpMenu = new WaypointsScreenController(s, new WaypointsScreenController.Host() {
      @Override public double[] playerPosition() { return new double[] {12, 65, -34}; }
      @Override public String currentDimension() { return Waypoint.DEFAULT_DIMENSION; }
      @Override public String currentWorldId() { return "sp:demo"; }
      @Override public String currentWorldLabel() { return "Demo"; }
      @Override public void teleportTo(int x, int y, int z) { lastTp[0] = x + "," + y + "," + z; }
      @Override public void clipboard(String value) {}
      @Override public void checkpoint() {}
      @Override public void changed() {}
      @Override public void close() {}
    });
    wpMenu.onOpen();
    wpMenu.openAddDraft(12, 65, -34);
    assertTrue(wpMenu.isEditing());
    assertTrue(wpMenu.charTyped('B'));
    assertTrue(wpMenu.charTyped('a'));
    assertTrue(wpMenu.charTyped('s'));
    assertTrue(wpMenu.charTyped('e'));
    assertTrue(wpMenu.keyPressed(Keys.ENTER, 0));
    assertFalse(wpMenu.isEditing());
    assertEquals(1, s.waypoints.points.size());
    assertEquals("Base", s.waypoints.points.get(0).displayName());
    wpMenu.render(new RecordingCanvas(), 640, 360, 320, 180, 2.0);
    assertFalse(wpMenu.selectedWaypointId().isEmpty());

    Path mapSettingsTpl = Path.of("../platform/src/templates/NightMapSettingsScreen.java.tpl");
    Path waypointsTpl = Path.of("../platform/src/templates/NightWaypointsScreen.java.tpl");
    if (Files.isRegularFile(mapSettingsTpl) && Files.isRegularFile(waypointsTpl)) {
      String msSrc = Files.readString(mapSettingsTpl);
      String wpSrc = Files.readString(waypointsTpl);
      assertTrue(msSrc.contains("NightVanillaAccountScreen.createVanillaButton"));
      assertTrue(msSrc.contains("@@VANILLA_RENDER_BG@@"));
      assertTrue(wpSrc.contains("NightVanillaAccountScreen.createVanillaButton"));
      assertTrue(wpSrc.contains("new EditBox("));
      assertTrue(wpSrc.contains("forCreate("));
    }
  }

  @Test
  void hudEditorEditsMinimapWithoutScaleCorruptionUnderNonUnitHudScale() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1.45f;
    s.hud.snap = false;
    s.minimap.enabled = true;
    s.minimap.scale = 1.0f;
    s.minimap.offsetX = 10f;
    s.minimap.offsetY = 10f;

    UiActions stubActions = new UiActions() {
      @Override public void changed() {}
      @Override public String clipboard() { return ""; }
      @Override public void clipboard(String value) {}
      @Override public java.util.List<String> profiles() { return java.util.Collections.emptyList(); }
      @Override public void saveProfile(String name) {}
      @Override public void loadProfile(String name) {}
      @Override public void deleteProfile(String name) {}
      @Override public String exportProfile() { return ""; }
      @Override public void importProfile(String json) {}
      @Override public void reset() {}
      @Override public void undo() {}
      @Override public boolean canUndo() { return false; }
      @Override public void checkpoint() {}
      @Override public void close() {}
      @Override public java.util.List<dev.nightclient.api.Cooldown> cooldowns() { return java.util.Collections.emptyList(); }
      @Override public String gameVersion() { return "test"; }
      @Override public String saveStatus() { return ""; }
    };
    MenuController menu = new MenuController(s, stubActions);
    menu.onOpen();
    menu.openEditor();
    RecordingCanvas sink = new RecordingCanvas();
    menu.render(sink, 1000, 620, 0, 0, 1.0);

    assertTrue(menu.mouseClicked(30, 30, 0));
    assertTrue(menu.mouseDragged(170, 90, 0, 140, 60));
    menu.mouseReleased(170, 90, 0);
    menu.render(sink, 1000, 620, 170, 90, 1.1);

    assertEquals(1.0f, s.minimap.scale, 0.02f, "Dragging minimap must not corrupt its scale");
    assertTrue(s.minimap.offsetX > 120f, "Minimap offsetX moved cleanly: " + s.minimap.offsetX);
    assertTrue(s.minimap.offsetY > 55f, "Minimap offsetY moved cleanly: " + s.minimap.offsetY);
  }

  @Test
  void keybindPillsRespectMinWidthInsideRowWithoutOverflowingRightEdge() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    UiActions stubActions = new UiActions() {
      @Override public void changed() {}
      @Override public String clipboard() { return ""; }
      @Override public void clipboard(String value) {}
      @Override public java.util.List<String> profiles() { return java.util.Collections.emptyList(); }
      @Override public void saveProfile(String name) {}
      @Override public void loadProfile(String name) {}
      @Override public void deleteProfile(String name) {}
      @Override public String exportProfile() { return ""; }
      @Override public void importProfile(String json) {}
      @Override public void reset() {}
      @Override public void undo() {}
      @Override public boolean canUndo() { return false; }
      @Override public void checkpoint() {}
      @Override public void close() {}
      @Override public java.util.List<dev.nightclient.api.Cooldown> cooldowns() { return java.util.Collections.emptyList(); }
      @Override public String gameVersion() { return "test"; }
      @Override public String saveStatus() { return ""; }
      @Override public String keyBindingLabel(String id) { return "B"; }
    };
    MenuController menu = new MenuController(s, stubActions);
    menu.onOpen();
    RecordingCanvas sink = new RecordingCanvas();
    menu.render(sink, 1200, 760, 0, 0, 0.1);
    float[] navWorld = menu.elementBounds("nav-world");
    assertNotNull(navWorld);
    menu.mouseClicked(navWorld[0] + 5, navWorld[1] + 5, 0);
    menu.mouseReleased(navWorld[0] + 5, navWorld[1] + 5, 0);
    menu.render(sink, 1200, 760, 0, 0, 0.2);
    float[] expWp = menu.elementBounds("expand.waypoints");
    assertNotNull(expWp);
    menu.mouseClicked(expWp[0] + 5, expWp[1] + 5, 1);
    menu.mouseReleased(expWp[0] + 5, expWp[1] + 5, 1);
    menu.render(sink, 1200, 760, 0, 0, 0.3);

    float[] bindRow = menu.elementBounds("binding-waypoint");
    assertNotNull(bindRow);
    assertTrue(bindRow[2] > 180f && bindRow[3] >= 24f);
  }

  @Test
  void minimapLockNorthDisablesTerrainRotationRegardlessOfPlayerYaw() throws Exception {
    Settings s = new Settings();
    s.minimap.enabled = true;
    Path temp = Files.createTempDirectory("tv-mm-lock-north");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "locknorth", "Lock North", "minecraft:overworld", 42L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x6AA845);
      Arrays.fill(heights, (short) 64);
      cache.ingestChunk(0, 0, rgb, heights, light);
      RecordingCanvas angleSink = new RecordingCanvas();

      s.minimap.lockNorth = false;
      MapRadarModels.RadarSnapshot rotating = new MapRadarModels.RadarSnapshot(
          true, 8, 65, 8, 73f, Waypoint.DEFAULT_DIMENSION, "plains",
          cache.identity().worldId(), cache.identity().worldLabel(),
          java.util.Collections.emptyList(), java.util.Collections.emptyList(),
          java.util.Collections.emptyList());
      MinimapRenderer.renderLive(angleSink, s, cache, rotating, 960, 540, 1.0);
      assertNotEquals(0f, angleSink.lastTerrainAngle, 0.01f, "Rotating minimap must rotate with playerYaw");

      s.minimap.lockNorth = true;
      for (float yaw : new float[] {0f, 73f, -145f, 270f}) {
        MapRadarModels.RadarSnapshot snap = new MapRadarModels.RadarSnapshot(
            true, 8, 65, 8, yaw, Waypoint.DEFAULT_DIMENSION, "plains",
            cache.identity().worldId(), cache.identity().worldLabel(),
            java.util.Collections.emptyList(), java.util.Collections.emptyList(),
            java.util.Collections.emptyList());
        angleSink.lastTerrainAngle = 999f;
        MinimapRenderer.renderLive(angleSink, s, cache, snap, 960, 540, 1.0);
        assertEquals(0f, angleSink.lastTerrainAngle, 1e-6f,
            "Lock North must keep minimap rotation disabled at yaw=" + yaw);
      }
    } finally {
      cache.shutdown();
    }
  }
}
