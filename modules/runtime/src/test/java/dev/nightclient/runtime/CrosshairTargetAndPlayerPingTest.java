package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.PlayerPingView;
import dev.nightclient.api.TotemCounterView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.WorldRibbon;
import dev.nightclient.ui.FeatureHudRenderer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrosshairTargetAndPlayerPingTest {
  private static final class RecordingCanvas implements Canvas {
    final List<Integer> rectColors = new ArrayList<Integer>();
    final List<Integer> roundedColors = new ArrayList<Integer>();
    final List<Float> roundedWidths = new ArrayList<Float>();
    final List<Float> roundedHeights = new ArrayList<Float>();
    final List<String> texts = new ArrayList<String>();
    final List<Integer> textColors = new ArrayList<Integer>();
    final List<Float> textYs = new ArrayList<Float>();
    boolean drewVanillaCrosshair = false;

    @Override
    public void rect(float x, float y, float w, float h, int color) {
      rectColors.add(color);
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, float r, int color) {
      roundedColors.add(color);
      roundedWidths.add(w);
      roundedHeights.add(h);
    }

    @Override
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      rectColors.add(c);
    }

    @Override
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}

    @Override
    public void line(float x, float y, float xx, float yy, float t, int color) {
      rectColors.add(color);
    }

    @Override
    public void circle(float x, float y, float r, int color) {
      rectColors.add(color);
    }

    @Override
    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {}

    @Override
    public void text(String value, float x, float y, float size, int color, boolean bold) {
      texts.add(value);
      textColors.add(color);
      textYs.add(y);
    }

    @Override
    public void textFamily(String family, String value, float x, float y, float size, int color,
        boolean bold, float tracking) {
      text(value, x, y, size, color, bold);
    }

    @Override
    public float textWidth(String value, float size, boolean bold) {
      return value == null ? 0f : value.length() * size * 0.55f;
    }

    @Override
    public void pushClip(float x, float y, float w, float h) {}

    @Override
    public void popClip() {}

    @Override
    public void item(String id, float x, float y, float size, float alpha) {}

    @Override
    public void backdrop(float x, float y, float w, float h, float r, float b, int q, float a) {}

    @Override
    public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}

    @Override
    public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
      drewVanillaCrosshair = true;
      return true;
    }
  }

  @Test
  void crosshairSwitchesToAnyCustomTargetColorOnPlayerTargetAcrossModes() {
    Settings settings = new Settings();
    settings.crosshair.enabled = true;
    settings.crosshair.colorMode = "vanilla";
    settings.crosshair.targetHighlight = true;
    settings.crosshair.targetColor = 0xff00e5ff;

    RecordingCanvas idle = new RecordingCanvas();
    FeatureHudRenderer.renderCrosshair(idle, settings, 800, 450, false);
    assertTrue(idle.drewVanillaCrosshair);

    RecordingCanvas hit = new RecordingCanvas();
    FeatureHudRenderer.renderCrosshair(hit, settings, 800, 450, true);
    assertFalse(hit.drewVanillaCrosshair, "Target color overrides vanilla inversion");
    assertFalse(hit.rectColors.isEmpty());
    for (int color : hit.rectColors) {
      assertEquals(0xff00e5ff, color);
    }

    settings.crosshair.mode = "shape";
    settings.crosshair.preset = "dot";
    settings.crosshair.outline = 0;
    settings.crosshair.targetColor = 0xffa855f7;
    RecordingCanvas shapeHit = new RecordingCanvas();
    FeatureHudRenderer.renderCrosshair(shapeHit, settings, 800, 450, true);
    assertTrue(shapeHit.rectColors.contains(0xffa855f7));
  }

  @Test
  void crosshairTargetShapesRenderAcrossAllModesPresetsAndTransitions() {
    Settings settings = new Settings();
    settings.crosshair.enabled = true;
    settings.crosshair.targetShapeEnabled = true;
    settings.crosshair.targetHighlight = false;
    settings.crosshair.targetOutline = 0f;
    settings.crosshair.targetColor = 0xff22d3ee;

    String[] shapes = {"x", "cross", "dot", "circle", "diamond", "brackets", "chevron", "expand", "custom"};
    for (String shape : shapes) {
      settings.crosshair.targetShape = shape;
      settings.crosshair.targetRotation = 25f;
      RecordingCanvas hit = new RecordingCanvas();
      FeatureHudRenderer.renderCrosshair(hit, settings, 800, 450, true, 1f);
      assertFalse(hit.drewVanillaCrosshair, "Target shape '" + shape + "' must override vanilla sprite");
      assertFalse(hit.rectColors.isEmpty(), "Target shape '" + shape + "' must emit geometry");

      RecordingCanvas morph = new RecordingCanvas();
      FeatureHudRenderer.renderCrosshair(morph, settings, 800, 450, true, 0.5f);
      assertFalse(morph.rectColors.isEmpty(), "Morph transition for '" + shape + "' must emit geometry");
    }

    settings.crosshair.targetHighlight = true;
    settings.crosshair.targetShape = "brackets";
    settings.crosshair.targetOutline = 0f;
    RecordingCanvas colored = new RecordingCanvas();
    FeatureHudRenderer.renderCrosshair(colored, settings, 800, 450, true, 1f);
    assertFalse(colored.rectColors.isEmpty());
    for (int c : colored.rectColors) {
      assertEquals(0xff22d3ee, c);
    }

    for (String preset : new String[] {"x", "brackets", "arrows"}) {
      settings.crosshair.targetPattern.preset(preset);
      int nonZero = 0;
      for (int y = 0; y < settings.crosshair.targetPattern.size; y++)
        for (int x = 0; x < settings.crosshair.targetPattern.size; x++)
          if (settings.crosshair.targetPattern.pixel(x, y) != 0) nonZero++;
      assertTrue(nonZero >= 5, "Pixel preset '" + preset + "' should draw visible pixels");
    }
    RecordingCanvas preview = new RecordingCanvas();
    FeatureHudRenderer.renderCrosshairTargetPreview(preview, settings, 56f, 32f, 64f);
    assertFalse(preview.rectColors.isEmpty(), "Target preview must draw geometry");
  }

  @Test
  void playerPingAndSafeCtBannerRenderCleanlyAndAvoidTotemOverlap(@TempDir Path tempDir)
      throws Exception {
    final WorldRibbon.View cam = new WorldRibbon.View(0, 64, 0, 0f, 0f, 70f, 16f / 9f, 960, 540);
    final List<PlayerPingView> pings = Collections.singletonList(
        new PlayerPingView(7, 0, 64, 8, 1.8f, 42, false, false));
    final List<TotemCounterView> totems = Collections.singletonList(
        new TotemCounterView(7, 0, 64, 8, 1.8f, 3, 2.5f, false));
    PlatformServices platform = new PlatformServices() {
      @Override
      public Path configDirectory() {
        return tempDir;
      }

      @Override
      public WorldRibbon.View cameraView(int width, int height) {
        return cam;
      }

      @Override
      public List<PlayerPingView> playerPings(float partialTick) {
        return pings;
      }

      @Override
      public List<TotemCounterView> totemCounters(float partialTick) {
        return totems;
      }

      @Override
      public CombatTagDetector.CombatStatus combatStatus() {
        return new CombatTagDetector.CombatStatus(true, 11, 0.5f, "disconnect");
      }
    };

    java.lang.reflect.Constructor<ClientRuntime> ctor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    ctor.setAccessible(true);
    ClientRuntime runtime = ctor.newInstance(platform);
    runtime.settings().playerPing.enabled = true;
    runtime.settings().playerPing.height = 0.0f;
    runtime.settings().totemCounter.enabled = true;
    runtime.settings().totemCounter.height = 0.0f;
    runtime.settings().safeCtLeave.enabled = true;

    assertTrue(runtime.shouldRenderHud());
    RecordingCanvas canvas = new RecordingCanvas();
    runtime.renderHud(canvas, 960, 540, 0f);
    assertTrue(canvas.texts.contains("42ms"), "Ping text should be rendered");
    assertTrue(canvas.texts.contains("\u00d73"), "Totem text should be rendered");
    assertTrue(canvas.texts.stream().anyMatch(t -> t.contains("11")),
        "Safe CT Leave banner should show remaining seconds");
    float maxPillW960 = canvas.roundedWidths.stream().max(Float::compare).orElse(0f);
    float maxPillH960 = canvas.roundedHeights.stream().max(Float::compare).orElse(0f);
    assertTrue(maxPillW960 <= 960f * 0.25f && maxPillH960 <= 18f,
        "Safe CT Leave banner must stay compact on 960x540 (got " + maxPillW960 + "x" + maxPillH960 + ")");

    RecordingCanvas smallCanvas = new RecordingCanvas();
    runtime.renderHud(smallCanvas, 480, 270, 0f);
    float maxPillW480 = smallCanvas.roundedWidths.stream().max(Float::compare).orElse(0f);
    float maxPillH480 = smallCanvas.roundedHeights.stream().max(Float::compare).orElse(0f);
    assertTrue(maxPillW480 <= 480f * 0.43f && maxPillH480 <= 14f,
        "Safe CT Leave banner must scale cleanly on 480x270 (got " + maxPillW480 + "x" + maxPillH480 + ")");

    int pingIdx = canvas.texts.lastIndexOf("42ms");
    int totemIdx = canvas.texts.lastIndexOf("\u00d73");
    assertTrue(canvas.textYs.get(pingIdx) > canvas.textYs.get(totemIdx),
        "Ping badge should offset below totem counter to avoid overlap");

    runtime.settings().playerPing.inWorld = false;
    runtime.settings().playerPing.inTab = true;
    runtime.settings().totemCounter.enabled = false;
    runtime.settings().safeCtLeave.enabled = false;
    assertFalse(runtime.shouldRenderHud(), "Tab-only Player Ping should not wake world HUD");
    RecordingCanvas tabOnlyCanvas = new RecordingCanvas();
    runtime.renderHud(tabOnlyCanvas, 960, 540, 0f);
    assertFalse(tabOnlyCanvas.texts.contains("42ms"),
        "World ping badge must not render when inWorld is disabled");
    runtime.shutdown();
  }

  @Test
  void tabPingLogicFormatsColorsReservesWidthAndPreservesLegacyDefaults(@TempDir Path tempDir)
      throws Exception {
    Settings defaults = new Settings();
    assertTrue(defaults.playerPing.inWorld);
    assertFalse(defaults.playerPing.inTab);
    assertFalse(defaults.playerPing.tabKeepBars);
    assertFalse(TabPingLogic.isWorldActive(defaults.playerPing));
    assertFalse(TabPingLogic.isTabActive(defaults.playerPing));

    try (dev.nightclient.config.ConfigStore store =
             new dev.nightclient.config.ConfigStore(tempDir)) {
      Settings legacy = store.decode("{\"playerPing\":{\"enabled\":true,\"style\":\"text\"}}");
      assertTrue(legacy.playerPing.enabled);
      assertTrue(legacy.playerPing.inWorld);
      assertFalse(legacy.playerPing.inTab);
      assertFalse(legacy.playerPing.tabKeepBars);
      assertTrue(TabPingLogic.isWorldActive(legacy.playerPing));
      assertFalse(TabPingLogic.isTabActive(legacy.playerPing));
    }

    defaults.playerPing.enabled = true;
    defaults.playerPing.inTab = true;
    assertTrue(TabPingLogic.isTabActive(defaults.playerPing));
    assertEquals("0ms", TabPingLogic.formatLabel(-12));
    assertEquals("42ms", TabPingLogic.formatLabel(42));
    assertEquals(0xffe2e8f0, TabPingLogic.colorFor(42, false));
    assertEquals(0xff4ade80, TabPingLogic.colorFor(42, true));
    assertEquals(0xfffacc15, TabPingLogic.colorFor(95, true));
    assertEquals(0xfffb923c, TabPingLogic.colorFor(155, true));
    assertEquals(0xfff87171, TabPingLogic.colorFor(260, true));

    Settings.PlayerPing cfg = defaults.playerPing;
    cfg.tabKeepBars = false;
    assertEquals(29, TabPingLogic.reserveColumnWidth(cfg, 13, 24));
    assertEquals(13, TabPingLogic.reserveColumnWidth(cfg, 13, 6));
    assertEquals(53, TabPingLogic.reserveColumnWidth(cfg, 13, 200), "Huge ping text width is clamped");
    assertEquals(100 + 120 - 1 - 24, TabPingLogic.computeTextX(100, 120, 24, false));

    cfg.tabKeepBars = true;
    assertEquals(40, TabPingLogic.reserveColumnWidth(cfg, 13, 24));
    assertEquals(100 + 120 - 13 - 24, TabPingLogic.computeTextX(100, 120, 24, true));

    cfg.inTab = false;
    assertEquals(13, TabPingLogic.reserveColumnWidth(cfg, 13, 24));
  }
}
