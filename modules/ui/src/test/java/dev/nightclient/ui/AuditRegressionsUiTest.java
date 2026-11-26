package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.Keys;
import dev.nightclient.api.MapRadarModels;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.map.RegionCacheManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditRegressionsUiTest {
  @TempDir Path tempDir;

  private static class StubCanvas implements Canvas {
    float lastMapRadius;
    final List<Float> rectHeights = new ArrayList<Float>();
    @Override public void rect(float x, float y, float w, float h, int c) { rectHeights.add(h); }
    @Override public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {}
    @Override public void roundedRect(float x, float y, float w, float h, float r, int c) { rectHeights.add(h); }
    @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    @Override public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
    @Override public void circle(float cx, float cy, float r, int c) {}
    @Override public void text(String v, float x, float y, float s, int c, boolean b) {}
    @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0 : v.length() * s * 0.55f; }
    @Override public void pushClip(float x, float y, float w, float h) {}
    @Override public void popClip() {}
    @Override public void item(String id, float x, float y, float s, float a) {}
    @Override public void inventoryItem(InventorySlot slot, float x, float y, float s, float a) {}
    @Override public boolean skinHead(String id, float x, float y, float s, float r, float a, int tint) { return true; }
    @Override public void mapQuad(String key, long rev, int[] px, int tw, int th, float x, float y,
        float w, float h, float r, float uc, float vc, float uh, float vh, float ang, boolean near, int c) {
      lastMapRadius = r;
    }
    @Override public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
    @Override public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}
  }

  @Test
  void uiCanvasMapQuadPreservesNegativePolygonEncodingAcrossScale() {
    StubCanvas sink = new StubCanvas();
    UiCanvas scaled = new UiCanvas(sink, 10f, 20f, 2.0f, 1.0f);
    scaled.mapQuad("mm", 1L, new int[4], 2, 2, 0f, 0f, 64f, 64f, -6f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, true, -1);
    assertEquals(-6f, sink.lastMapRadius, 0.0001f, "Negative octagon shape tag (-6f) must NOT be scaled");
    scaled.mapQuad("mm", 1L, new int[4], 2, 2, 0f, 0f, 64f, 64f, 8f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, true, -1);
    assertEquals(16f, sink.lastMapRadius, 0.0001f, "Positive corner radius must scale by 2x");
  }

  @Test
  void extendedHudBoundsSupportsMinimapWithoutMutatingLiveSettings() {
    Settings s = new Settings();
    s.minimap.enabled = true;
    s.minimap.size = 116f;
    s.armor.enabled = false;
    float[] mmBounds = ExtendedHudRenderer.bounds(s.minimap, 1280, 720, 0f);
    assertNotNull(mmBounds);
    assertTrue(mmBounds[2] >= 100f && mmBounds[3] >= 100f);
    float[] armorBounds = ExtendedHudRenderer.bounds(s.armor, 1280, 720, 0f);
    assertNotNull(armorBounds);
    assertFalse(s.armor.enabled, "Bounds query must never mutate live settings");
  }

  @Test
  void vanillaWidgetsHandlesTinyScrollbarAndSurrogatePairFitText() {
    StubCanvas sink = new StubCanvas();
    VanillaWidgets.drawScrollbar(sink, 0f, 0f, 6f, 10f, 0f, 200f);
    for (Float rh : sink.rectHeights) {
      assertTrue(rh >= 0f && rh <= 10.01f, "Scrollbar height must stay within track");
    }
    String emoji = "AB\uD83D\uDE00CD";
    String fitted = VanillaWidgets.fitText(sink, emoji, 22f);
    for (int i = 0; i < fitted.length(); i++) {
      if (Character.isHighSurrogate(fitted.charAt(i))) {
        assertTrue(i + 1 < fitted.length() && Character.isLowSurrogate(fitted.charAt(i + 1)),
            "fitText must not split surrogate pair");
      }
    }
  }

  @Test
  void worldMapControllerReturnsTrueOnDragReleaseAndSupportsNumpadZoom() {
    Settings s = new Settings();
    RegionCacheManager cache = new RegionCacheManager(tempDir);
    try {
      WorldMapController ctrl = new WorldMapController(s, new WorldMapController.Host() {
        @Override public RegionCacheManager mapCache() { return cache; }
        @Override public MapRadarModels.RadarSnapshot radarSnapshot() { return MapRadarModels.RadarSnapshot.EMPTY; }
        @Override public void openWaypointCreate(double x, double y, double z) {}
        @Override public void clipboard(String value) {}
        @Override public void teleportTo(int x, int y, int z) {}
        @Override public void checkpoint() {}
        @Override public void changed() {}
        @Override public void closeWorldMap() {}
      });
      ctrl.onOpen();
      ctrl.render(new StubCanvas(), 800, 600, 200, 200, 0f);
      ctrl.mouseClicked(200, 200, 0);
      assertTrue(ctrl.mouseReleased(210, 210, 0), "Ending pan drag must return true");
      float zoomBefore = ctrl.viewState().zoom;
      assertTrue(ctrl.keyPressed(334, 0), "Numpad + (334) must zoom in");
      assertTrue(ctrl.viewState().zoom > zoomBefore);
      assertTrue(ctrl.keyPressed(333, 0), "Numpad - (333) must zoom out");
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void waypointsScreenControllerSurrogateTypingAndBackspaceStayValid() {
    Settings s = new Settings();
    WaypointsScreenController ctrl = new WaypointsScreenController(s, new WaypointsScreenController.Host() {
      @Override public double[] playerPosition() { return new double[] {10, 64, 20}; }
      @Override public String currentDimension() { return "minecraft:overworld"; }
      @Override public String currentWorldId() { return "sp:test"; }
      @Override public String currentWorldLabel() { return "Test"; }
      @Override public void teleportTo(int x, int y, int z) {}
      @Override public void clipboard(String value) {}
      @Override public void checkpoint() {}
      @Override public void changed() {}
      @Override public void close() {}
    });
    ctrl.onOpen();
    ctrl.openAddDraft(10, 64, 20);
    assertTrue(ctrl.isEditing());
    assertTrue(ctrl.charTyped(0x1F525));
    assertTrue(ctrl.keyPressed(Keys.BACKSPACE, 0));
    assertTrue(ctrl.keyPressed(Keys.ENTER, 0));
    assertEquals(1, s.waypoints.points.size());
    String savedName = s.waypoints.points.get(0).label;
    for (int i = 0; i < savedName.length(); i++) {
      assertFalse(Character.isSurrogate(savedName.charAt(i)), "Must not leave lone surrogate in name");
    }
  }
}
