package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.Keys;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import dev.nightclient.renderer.FontAtlas;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuControllerTest {
  private static final class Text {
    final String value;
    final float x, y, size;
    final boolean bold;
    Text(String value, float x, float y, float size, boolean bold) {
      this.value = value;
      this.x = x;
      this.y = y;
      this.size = size;
      this.bold = bold;
    }
  }
  private static final class RecordingCanvas implements Canvas {
    final List<Text> text = new ArrayList<Text>();
    final List<String> items = new ArrayList<String>();
    int clips, commands, glyphs, measurements, backdrops;
    boolean realMetrics;
    final List<InventorySlot> slots = new ArrayList<InventorySlot>();
    final List<float[]> rounded = new ArrayList<float[]>();
    final List<float[]> roundedCorners = new ArrayList<float[]>();
    final List<float[]> backdropBounds = new ArrayList<float[]>();
    private final StringBuilder trace = new StringBuilder();
    private void f(float value) {
      trace.append(Float.floatToIntBits(value)).append(',');
    }
    private void i(int value) {
      trace.append(value).append(',');
    }
    private void s(String value) {
      trace.append(value.length()).append(':').append(value);
    }
    public void rect(float x, float y, float w, float h, int c) {
      commands++;
      trace.append('R');
      f(x);
      f(y);
      f(w);
      f(h);
      i(c);
      assertTrue(w >= 0 && h >= 0);
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      commands++;
      rounded.add(new float[] {x, y, w, h, r, c >>> 24});
      trace.append('D');
      f(x);
      f(y);
      f(w);
      f(h);
      f(r);
      i(c);
      assertTrue(w >= 0 && h >= 0);
    }
    @Override
    public void roundedRectCorners(
        float x, float y, float w, float h, float tl, float tr, float bl, float br, int c) {
      roundedCorners.add(new float[] {x, y, w, h, tl, tr, bl, br, c >>> 24});
      roundedRect(x, y, w, h, Math.max(Math.max(tl, tr), Math.max(bl, br)), c);
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      commands++;
      trace.append('S');
      f(x);
      f(y);
      f(w);
      f(h);
      f(r);
      f(t);
      i(c);
      assertTrue(w >= 0 && h >= 0);
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      commands++;
      trace.append('G');
      f(x);
      f(y);
      f(w);
      f(h);
      i(a);
      i(b);
      assertTrue(w >= 0 && h >= 0);
    }
    public void line(float a, float b, float c, float d, float t, int color) {
      commands++;
      trace.append('L');
      f(a);
      f(b);
      f(c);
      f(d);
      f(t);
      i(color);
    }
    public void circle(float a, float b, float r, int c) {
      commands++;
      trace.append('C');
      f(a);
      f(b);
      f(r);
      i(c);
    }
    public void text(String v, float x, float y, float size, int c, boolean bold) {
      commands++;
      glyphs += v.codePointCount(0, v.length());
      text.add(new Text(v, x, y, size, bold));
      trace.append('T');
      s(v);
      f(x);
      f(y);
      f(size);
      i(c);
      trace.append(bold ? 1 : 0);
    }
    public float textWidth(String v, float size, boolean bold) {
      measurements++;
      return realMetrics ? FontAtlas.get().width(v, size, bold)
                         : v.codePointCount(0, v.length()) * size * .5f;
    }
    public void pushClip(float x, float y, float w, float h) {
      commands++;
      trace.append('[');
      f(x);
      f(y);
      f(w);
      f(h);
      assertTrue(w >= 0 && h >= 0);
      clips++;
    }
    public void popClip() {
      commands++;
      trace.append(']');
      assertTrue(clips > 0);
      clips--;
    }
    public void item(String id, float x, float y, float size, float alpha) {
      commands++;
      items.add(id);
      trace.append('I');
      s(id);
      f(x);
      f(y);
      f(size);
      f(alpha);
    }
    public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
      slots.add(slot);
      item(slot.itemId, x, y, size, alpha);
    }
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float opacity) {
      commands++;
      backdrops++;
      backdropBounds.add(new float[] {x, y, w, h, r, blur, opacity});
      trace.append('B');
      f(x);
      f(y);
      f(w);
      f(h);
      f(r);
      f(blur);
      i(quality);
      f(opacity);
      assertTrue(w >= 0 && h >= 0);
    }
    public void invertRect(float x, float y, float w, float h, float opacity) {
      commands++;
      trace.append('V');
      f(x);
      f(y);
      f(w);
      f(h);
      f(opacity);
      assertTrue(w > 0 && h > 0 && opacity > 0 && opacity <= 1);
    }
    public void texture(
        String id, float x, float y, float w, float h, float r, float tile, int argb) {
      commands++;
      trace.append('X');
      s(id);
      f(x);
      f(y);
      f(w);
      f(h);
      f(r);
      f(tile);
      i(argb);
      assertTrue(w >= 0 && h >= 0);
    }
    public void textTracked(
        String v, float x, float y, float size, int argb, boolean bold, float tracking) {
      trace.append('K');
      f(tracking);
      text(v, x, y, size, argb, bold);
    }
    public float textWidthTracked(String v, float size, boolean bold, float tracking) {
      return textWidth(v, size, bold) + Math.max(0, v.codePointCount(0, v.length()) - 1) * tracking;
    }
    Text find(String value) {
      for (int i = text.size() - 1; i >= 0; i--)
        if (value.equals(text.get(i).value)) return text.get(i);
      fail("Missing text: " + value + " in " + textValues());
      return null;
    }
    List<String> textValues() {
      List<String> values = new ArrayList<String>();
      for (Text t : text) values.add(t.value);
      return values;
    }
    String digest() {
      try {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(
            trace.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte value : hash)
          out.append(Character.forDigit((value >> 4) & 15, 16))
              .append(Character.forDigit(value & 15, 16));
        return out.toString();
      } catch (java.security.NoSuchAlgorithmException unavailable) {
        throw new AssertionError(unavailable);
      }
    }
    void clear() {
      text.clear();
      items.clear();
      slots.clear();
      rounded.clear();
      backdropBounds.clear();
      trace.setLength(0);
      commands = glyphs = backdrops = 0;
      assertEquals(0, clips);
    }
  }
  private static final class Actions implements UiActions {
    String clipboard = "", saved = "", error = "", deleted = "", binding = "";
    boolean closed, failProfiles, failSave, failDelete, viewmodelSupported;
    int changes, checkpoints, profileReads, sceneReads;
    final List<String> profileNames = new ArrayList<String>();
    final java.util.Map<String, String> labels = new java.util.HashMap<String, String>();
    List<Cooldown> timers = Collections.emptyList();
    dev.nightclient.skin.SkinDocument customDocument;
    public dev.nightclient.skin.SkinDocument skinDocument() {
      return customDocument;
    }
    public void setKeyBinding(String id, String key) {
      binding = id + ":" + key;
    }
    public String keyBindingLabel(String id) {
      String label = labels.get(id);
      return label == null ? UiActions.super.keyBindingLabel(id) : label;
    }
    public void changed() {
      changes++;
    }
    public String clipboard() {
      return clipboard;
    }
    public void clipboard(String value) {
      clipboard = value;
    }
    public List<String> profiles() {
      profileReads++;
      if (failProfiles) throw new IllegalStateException("Cannot read profiles");
      return profileNames;
    }
    public void saveProfile(String name) {
      if (failSave) throw new IllegalStateException("Cannot save profile");
      saved = name;
      if (!profileNames.contains(name)) profileNames.add(name);
    }
    public void loadProfile(String name) {}
    public void deleteProfile(String name) {
      if (failDelete) throw new IllegalStateException("Cannot delete profile");
      deleted = name;
      profileNames.remove(name);
    }
    public String exportProfile() {
      return "{}";
    }
    public void importProfile(String json) {
      throw new IllegalArgumentException("Invalid profile");
    }
    public void reset() {}
    public void undo() {}
    public boolean canUndo() {
      return false;
    }
    public void checkpoint() {
      checkpoints++;
    }
    public void close() {
      closed = true;
    }
    public List<Cooldown> cooldowns() {
      return timers;
    }
    public boolean fireVisible() {
      sceneReads++;
      return false;
    }
    public boolean viewmodelEditorSupported() {
      return viewmodelSupported;
    }
    public String gameVersion() {
      return "1.20";
    }
    public String saveStatus() {
      return "saved";
    }
  }
  private static final class Harness {
    final Settings settings = new Settings();
    final Actions actions = new Actions();
    final RecordingCanvas canvas = new RecordingCanvas();
    final MenuController menu;
    double time;
    Harness() {
      settings.menu.animationSpeed = 1000000f;
      menu = new MenuController(settings, actions);
      menu.onOpen();
      render();
    }
    Harness(String preset) {
      settings.menu.animationSpeed = 1000000f;
      settings.skin.enabled = true;
      settings.skin.preset = preset;
      menu = new MenuController(settings, actions);
      menu.onOpen();
      render();
    }
    Harness(String source, boolean enabled) throws Exception {
      settings.menu.animationSpeed = 1000000f;
      settings.skin.enabled = enabled;
      actions.customDocument = dev.nightclient.skin.NcParser.parse(source);
      menu = new MenuController(settings, actions);
      menu.onOpen();
      render();
    }
    void render() {
      canvas.clear();
      menu.render(canvas, 1000, 700, 0, 0, time++);
      assertEquals(0, canvas.clips);
    }
    void assertBackdrop(String id, float radius, boolean expected) {
      float[] bounds = menu.elementBounds(id);
      assertNotNull(bounds, "Missing surface: " + id);
      boolean drawn = canvas.backdropBounds.stream().anyMatch(call ->
          matchesSurface(call, bounds, radius) && call[5] > 0 && call[6] > 0);
      assertEquals(expected, drawn, id + " backdrop at opacity " + settings.theme.backgroundOpacity);
      assertTrue(canvas.rounded.stream().anyMatch(call -> matchesSurface(call, bounds, radius)
              && call[5] == Math.round(settings.theme.backgroundOpacity * 255)),
          id + " tint should match opacity " + settings.theme.backgroundOpacity);
    }
    private static boolean matchesSurface(float[] call, float[] bounds, float radius) {
      return Math.abs(call[0] - bounds[0]) < .05f && Math.abs(call[1] - bounds[1]) < .05f
          && Math.abs(call[2] - bounds[2]) < .05f && Math.abs(call[3] - bounds[3]) < .05f
          && Math.abs(call[4] - radius) < .05f;
    }
    String hashAt(double seconds) {
      canvas.clear();
      menu.render(canvas, 1000, 700, 0, 0, seconds);
      assertEquals(0, canvas.clips);
      return canvas.digest();
    }
    void click(String id) {
      click(id, id.startsWith("expand.") ? 1 : 0);
    }
    void click(String id, int button) {
      float[] b = menu.elementBounds(id);
      assertNotNull(b, "Missing semantic control: " + id);
      assertTrue(menu.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, button));
      menu.mouseReleased(b[0] + b[2] / 2, b[1] + b[3] / 2, button);
      render();
    }
    void openModule(String id) {
      dev.nightclient.ui.skin.UiCatalog.Module module =
          new dev.nightclient.ui.skin.UiCatalog(settings).module(id);
      String nav = module.hud                  ? "nav2"
          : "player".equals(module.category) ? "nav-player"
          : "camera".equals(module.category) ? "nav-camera"
          : "world".equals(module.category)  ? "nav-world"
                                               : "nav0";
      click(nav);
      click("module-search");
      replace(id);
      menu.keyPressed(Keys.ENTER, 0);
      render();
      if (module.hasControls) click("expand." + id);
    }
    void page(int page) {
      if (page == 0 || page == 2) {
        click(page == 0 ? "nav0" : "nav2");
        return;
      }
      click("nav-settings");
      click(page == 1     ? "settings-appearance"
              : page == 4 ? "settings-interface"
                          : "settings-profiles");
    }
    void seek(String id) {
      seek(id, Math.min(16f, 13.5f * settings.menu.scale));
    }
    void seek(String id, float minimumHeight) {
      for (int i = 0; i < 60; i++) {
        float[] b = menu.elementBounds(id);
        if (b != null && b[2] >= 8 && b[3] >= minimumHeight) return;
        menu.mouseScrolled(600, 400, -1);
        render();
      }
      fail("Missing fully reachable control: " + id);
    }
    void type(String text) {
      for (int cp : text.codePoints().toArray()) assertTrue(menu.charTyped(cp));
    }
    void replace(String text) {
      menu.keyPressed('A', Keys.MOD_CONTROL);
      type(text);
    }
    void clickText(String text) {
      Text value = canvas.find(text);
      assertTrue(menu.mouseClicked(value.x + 3, value.y + 4, 0));
      menu.mouseReleased(0, 0, 0);
      render();
    }
    void editor() {
      page(2);
      click("edit-hud");
    }
    float[] bounds(String id, int width, int height) {
      return new dev.nightclient.ui.skin.SkinView(settings.skinDocument, settings, actions)
          .hudBounds(id, width, height, time, true);
    }

    float[] pixel(int x, int y) {
      float[] b = menu.elementBounds("pixel-grid");
      assertNotNull(b);
      float cell = Math.min(b[2], b[3]) / settings.crosshair.pattern.size;
      return new float[] {
          b[0] + (b[2] - cell * settings.crosshair.pattern.size) / 2 + (x + .5f) * cell,
          b[1] + (b[3] - cell * settings.crosshair.pattern.size) / 2 + (y + .5f) * cell};
    }
  }
  @Test
  void ordinaryMenuFramesDoNotQuerySceneEditorPresentation() {
    Harness h = new Harness();
    h.actions.sceneReads = 0;
    h.render();
    h.render();
    assertEquals(0, h.actions.sceneReads);
    h.editor();
    assertTrue(h.actions.sceneReads > 0,
        "Scene queries are still available when the editor actually opens");
  }
  @Test
  void emptyHudDoesNotInventGameplayTimersAndLargeSpacingKeepsRows() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    RecordingCanvas canvas = new RecordingCanvas();
    HudRenderer.render(canvas, settings, Collections.<Cooldown>emptyList(), 900, 600, 0, false);
    assertTrue(canvas.items.isEmpty());
    assertTrue(canvas.text.isEmpty());
    settings.hud.hideWhenEmpty = false;
    HudRenderer.render(canvas, settings, Collections.<Cooldown>emptyList(), 900, 600, 0, false);
    assertTrue(canvas.textValues().contains(
        dev.nightclient.i18n.I18n.text(settings.language, "hud.cooldowns.title")));
    assertTrue(canvas.items.isEmpty());
    canvas.clear();
    settings.hud.spacing = 20;
    List<Cooldown> data = Arrays.asList(new Cooldown("a", "A", "minecraft:shield", 5, 10),
        new Cooldown("b", "B", "minecraft:ender_pearl", 2, 10),
        new Cooldown("c", "C", "minecraft:golden_apple", 8, 10));
    HudRenderer.render(canvas, settings, data, 900, 600, 0, false);
    assertEquals(
        Arrays.asList("minecraft:golden_apple", "minecraft:shield", "minecraft:ender_pearl"),
        canvas.items);
  }

  @Test
  void oversizedHudFitsSmallViewportWithoutChangingConfiguredScale() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.hud.width = 380;
    settings.hud.scale = 2;
    float[] bounds = HudRenderer.bounds(settings, null, 320, 200, true);
    assertTrue(bounds[0] >= 6 && bounds[1] >= 6);
    assertTrue(bounds[0] + bounds[2] <= 314.001f);
    assertTrue(bounds[1] + bounds[3] <= 194.001f);
    assertEquals(2, settings.hud.scale);
  }

  @Test
  void hudUsesAllRowsThatFitAndNeverExceedsViewport() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.hud.spacing = 20;
    RecordingCanvas canvas = new RecordingCanvas();
    List<Cooldown> data = Arrays.asList(new Cooldown("a", "A", "minecraft:shield", 5, 10),
        new Cooldown("b", "B", "minecraft:ender_pearl", 2, 10),
        new Cooldown("c", "C", "minecraft:golden_apple", 8, 10));
    HudRenderer.render(canvas, settings, data, 900, 156, 0, false);
    assertEquals(3, canvas.items.size());
    float[] bounds = HudRenderer.bounds(settings, data, 900, 156, false);
    assertTrue(bounds[1] + bounds[3] <= 150.001f);
    settings.hud.horizontal = true;
    settings.hud.width = 120;
    settings.hud.spacing = 0;
    canvas.clear();
    HudRenderer.render(canvas, settings, data, 372, 150, 0, false);
    assertEquals(3, canvas.items.size());
  }

  @Test
  void longServerNamesDoNotCauseThousandsOfWidthMeasurements() {
    RecordingCanvas canvas = new RecordingCanvas();
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < 10000; i++) name.append("Ж");
    String fit = HudRenderer.ellipsize(canvas, name.toString(), 80, 10);
    assertEquals(16, fit.codePointCount(0, fit.length()));
    assertTrue(fit.endsWith("…"));
    assertTrue(canvas.measurements <= 25,
        "Long names used " + canvas.measurements + " width measurements");
  }

  @Test
  void fractionalHudScaleKeepsTheCountChosenByLayout() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    RecordingCanvas canvas = new RecordingCanvas();
    List<Cooldown> data = Arrays.asList(new Cooldown("a", "A", "minecraft:shield", 5, 10),
        new Cooldown("b", "B", "minecraft:ender_pearl", 2, 10),
        new Cooldown("c", "C", "minecraft:golden_apple", 8, 10));
    for (float scale : new float[] {.61f, .83f, 1.03f, 1.17f, 1.37f, 1.69f}) {
      canvas.clear();
      settings.hud.scale = scale;
      HudRenderer.render(canvas, settings, data, 1000, 700, 0, false);
      assertEquals(3, canvas.items.size(), "A fitting row was lost at scale " + scale);
    }
  }

  @Test
  void narrowEmptyHudKeepsTrackedBoldTextInsideItsConfiguredFrame() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    RecordingCanvas canvas = new RecordingCanvas();
    canvas.realMetrics = true;
    settings.hud.width = 120;
    settings.hud.fontScale = 1.5f;
    settings.theme.fontTracking = 2;
    settings.theme.fontWeight = "bold";
    HudRenderer.renderEditor(canvas, settings, Collections.<Cooldown>emptyList(), 900, 600, 0);
    float[] b = HudRenderer.bounds(settings, null, 900, 600, true);
    assertTrue(canvas.items.isEmpty());
    for (Text t : canvas.text) {
      float width = FontAtlas.get("studio-sans-medium").width(t.value, t.size, t.bold, 0);
      assertTrue(t.x >= b[0] + 7 * b[4] - .01f && t.x + width <= b[0] + b[2] - 7 * b[4] + .01f,
          "Text escaped the HUD: " + t.value);
      assertTrue(t.y >= b[1] && t.y + t.size <= b[1] + b[3] + .01f);
    }
  }

  @Test
  void inventoryUsesExactClientSnapshotsAndHotbarIsOptIn() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.inventory.enabled = true;
    RecordingCanvas c = new RecordingCanvas();
    InventorySlot sword =
        new InventorySlot(9, "minecraft:diamond_sword", "Actual server sword", 1, 20, 100, true);
    InventorySlot hotbar = new InventorySlot(0, "minecraft:ender_pearl", "Pearl", 16, 0, 0);
    InventorySlot air = new InventorySlot(10, "minecraft:air", "", 0, 0, 0);
    FeatureHudRenderer.render(c, s, Arrays.asList(sword, hotbar, air),
        Collections.<TntView>emptyList(), 1000, 700, 0, false);
    assertEquals(Collections.singletonList(sword), c.slots);
    assertSame(sword, c.slots.get(0));
    c.clear();
    s.inventory.hotbar = true;
    FeatureHudRenderer.render(c, s, Arrays.asList(sword, hotbar), null, 1000, 700, 0, false);
    assertEquals(Arrays.asList(sword, hotbar), c.slots);
    assertTrue(c.textValues().contains("16"));
  }

  @Test
  void tntTimerIsAnAnchoredNearestListIncludingEntitiesBehindTheCamera() {
    Settings s = new Settings();
    s.language = "en";
    s.menu.animationSpeed = 1000000f;
    s.tnt.enabled = true;
    RecordingCanvas c = new RecordingCanvas();
    List<TntView> values = Arrays.asList(new TntView(1, 400, 300, 2.4f, 12),
        new TntView(2, -500, -300, 1.2f, 8, false), new TntView(3, 500, 300, 0, 8),
        new TntView(4, 500, 300, 3, 100));
    FeatureHudRenderer.render(c, s, null, values, 1000, 700, 0, false);
    assertTrue(c.textValues().contains("TNT"));
    assertEquals(2, c.items.size());
    assertTrue(
        c.textValues().indexOf("1.2s") < c.textValues().indexOf("2.4s"), c.textValues().toString());
    assertFalse(c.textValues().contains("3.0s"));
    float[] b = FeatureHudRenderer.tntBounds(s, values, 1000, 700, false);
    Text first = c.find("1.2s");
    assertTrue(first.x >= b[0] && first.x < b[0] + b[2]);
    assertTrue(first.y >= b[1] && first.y < b[1] + b[3]);
    assertEquals(Arrays.asList("minecraft:tnt", "minecraft:tnt"), c.items);
    c.clear();
    s.tnt.showDistance = true;
    FeatureHudRenderer.render(c, s, null, values, 1000, 700, 0, false);
    assertTrue(c.textValues().contains("8m"), c.textValues().toString());
    assertTrue(c.textValues().contains("12m"), c.textValues().toString());
  }

  @Test
  void nearestTntListIsBoundedByConfiguredPanelHeightAndFitsTheViewport() {
    Settings s = new Settings();
    s.language = "en";
    s.menu.animationSpeed = 1000000f;
    s.tnt.enabled = true;
    List<TntView> list = new ArrayList<TntView>();
    for (int i = 0; i < 50; i++) list.add(new TntView(i, 9999, -9999, 4 - i * .01f, i + 1, false));
    s.tnt.scale = 3;
    s.tnt.width = 4096;
    s.tnt.height = 96;
    float[] b = FeatureHudRenderer.tntBounds(s, list, 320, 200, false);
    assertEquals(3, (int) b[5]);
    assertTrue(b[0] >= 8 && b[1] >= 8 && b[0] + b[2] <= 312.001f && b[1] + b[3] <= 192.001f);
    RecordingCanvas c = new RecordingCanvas();
    FeatureHudRenderer.render(c, s, null, list, 320, 200, 0, false);
    int times = 0;
    for (Text value : c.text)
      if (value.value.matches("[0-9]+\\.[0-9]s")) times++;
    assertEquals(3, times);
    assertEquals(0, c.clips);
  }

  @Test
  void allAdditionalHudElementsRespectF1AndDisabledFeatures() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    RecordingCanvas c = new RecordingCanvas();
    List<InventorySlot> slots =
        Collections.singletonList(new InventorySlot(9, "minecraft:shield", "Shield", 1, 0, 0));
    List<TntView> tnt = Collections.singletonList(new TntView(1, 500, 350, 3, 2));
    FeatureHudRenderer.render(c, s, slots, tnt, 1000, 700, 0, false);
    assertEquals(0, c.commands);
    s.inventory.enabled = s.crosshair.enabled = s.tnt.enabled = true;
    FeatureHudRenderer.render(c, s, slots, tnt, 1000, 700, 0, true);
    assertEquals(0, c.commands);
  }

  @Test
  void inventoryAndCrosshairStylesChangeGeometryIndependently() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.inventory.offsetX = 40;
    float[] original = FeatureHudRenderer.inventoryBounds(s, 1000, 700),
            cross = FeatureHudRenderer.crosshairBounds(s, 1000, 700);
    s.inventory.scale = 1.7f;
    float[] changed = FeatureHudRenderer.inventoryBounds(s, 1000, 700);
    assertEquals(original[2] * 1.7f, changed[2], .001f);
    assertArrayEquals(cross, FeatureHudRenderer.crosshairBounds(s, 1000, 700), .001f);
    assertTrue(changed[0] >= 6 && changed[0] + changed[2] <= 994.001f);
  }

  @Test
  void customCrosshairFollowsTheActualCameraReticleVisibility() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.crosshair.enabled = true;
    RecordingCanvas c = new RecordingCanvas();
    FeatureHudRenderer.render(c, s, null, null, 1000, 700, 0, false, false);
    assertEquals(0, c.commands);
    FeatureHudRenderer.render(c, s, null, null, 1000, 700, 0, false, true);
    assertTrue(c.commands > 0);
  }

  @Test
  void tinyImportedPanelWidthsKeepAllGeometryNonnegative() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.inventory.enabled = true;
    s.inventory.width = 8;
    s.hud.width = 8;
    RecordingCanvas c = new RecordingCanvas();
    FeatureHudRenderer.render(c, s,
        Collections.singletonList(new InventorySlot(9, "minecraft:shield", "Shield", 100000, 0, 0)),
        null, 320, 200, 0, false);
    assertTrue(c.items.contains("minecraft:shield"));
    HudRenderer.render(c, s,
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 3, 5)), 320, 200,
        0, false);
    assertEquals(0, c.clips);
  }
  @Test
  void defaultMenuIsTheBundledNcAndFeaturePagesUseRealSettings() {
    Harness h = new Harness();
    assertNotNull(h.settings.skinDocument.menu());
    assertNotNull(h.settings.skinDocument.hud());
    assertNotNull(h.menu.elementBounds("local-profile"));
    assertFalse(h.canvas.textValues().contains("default"));
    h.openModule("protect");
    assertNotNull(h.menu.elementBounds("protect.replacement"));
    h.click("protect.replacement");
    h.replace("Лунный 🎮");
    h.menu.keyPressed(Keys.ENTER, 0);
    assertEquals("Лунный 🎮", h.settings.protect.replacement);
    assertFalse(h.settings.protect.enabled);
  }
  @Test
  void featuresHudAndVisualOnlyViewmodelEditorHaveSeparateEntries() {
    Harness h = new Harness();
    assertFalse(h.canvas.textValues().contains("TNT Timer"));
    assertNull(h.menu.elementBounds("viewmodel"));
    assertNull(h.menu.elementBounds("viewmodel.main.enabled"));
    h.page(2);
    assertTrue(h.canvas.textValues().contains("TNT Timer"));
    assertNotNull(h.menu.elementBounds("edit-hud"));
    h.click("edit-hud");
    h.click("editor-target-viewmodel");
    assertNotNull(h.menu.elementBounds("viewmodel-inspector"));
    for (String mode : new String[] {"G", "R", "S"})
      assertNotNull(h.menu.elementBounds("viewmodel-mode-" + mode));
    assertNull(h.menu.elementBounds("viewmodel.main.enabled"),
        "Hand transforms belong to the visual editor, not a second settings list");
  }
  @Test
  void slidersAcceptDragAndKeyboardWithoutRetainingTextFocus() {
    Harness h = new Harness();
    h.openModule("saturation");
    float[] b = h.menu.elementBounds("saturation.strength");
    assertNotNull(b);
    h.menu.mouseClicked(b[0] + b[2] * .2, b[1] + b[3] / 2, 0);
    h.menu.mouseDragged(b[0] + b[2] + 6, b[1] + b[3] / 2, 0, 100, 0);
    h.menu.mouseReleased(0, 0, 0);
    assertEquals(2.5f, h.settings.saturation.strength, .001f);
    assertFalse(h.menu.isTextFocused());
    h.menu.keyPressed(Keys.LEFT, 0);
    assertEquals(2.49f, h.settings.saturation.strength, .001f);
  }
  @Test
  void unifiedSettingsExposePalettesGlassLanguageProfilesAndBindings() {
    Harness h = new Harness();
    h.page(1);
    for (String mode : new String[] {"dark", "light", "custom"})
      assertNotNull(h.menu.elementBounds("option.theme-mode." + mode),
          "Actual segmented palette button: " + mode);
    assertNull(h.menu.elementBounds("global-settings"));
    assertNull(h.menu.elementBounds("profile-card"));
    h.click("option.theme-mode.custom");
    assertEquals("custom", h.settings.theme.mode);
    for (String id : new String[] {"custom-background", "custom-surface", "custom-raised",
             "custom-text", "custom-accent"})
      h.seek(id);
    h.seek("style-transparency", 12);
    float opacity = h.settings.theme.backgroundOpacity;
    h.click("style-transparency");
    assertNotEquals(opacity, h.settings.theme.backgroundOpacity);
    h.seek("glass-blur");
    boolean blur = h.settings.theme.effects.backgroundBlur;
    h.click("glass-blur");
    assertNotEquals(blur, h.settings.theme.effects.backgroundBlur);
    h.page(4);
    assertNotNull(h.menu.elementBounds("option.language.en"));
    assertNotNull(h.menu.elementBounds("option.language.ru"));
    assertNotNull(h.menu.elementBounds("binding-menu"));
    assertNull(h.menu.elementBounds("binding-zoom"));
    assertNull(h.menu.elementBounds("binding-freelook"));
    h.page(3);
    assertNotNull(h.menu.elementBounds("create-profile"));
    assertNotNull(h.menu.elementBounds("copy-profile"));
  }
  @Test
  void menuCapturesActualBindingWithoutClosingAndUnbindsOnEscapeOrDelete() {
    Harness h = new Harness();
    h.openModule("zoom");
    h.click("binding-zoom");
    assertTrue(h.menu.isBindingCapture());
    assertTrue(h.menu.captureBinding(71, "key.keyboard.g"));
    assertEquals("zoom:key.keyboard.g", h.actions.binding);
    assertFalse(h.actions.closed);
    h.page(4);
    h.click("binding-menu");
    assertTrue(h.menu.captureBinding(Keys.ESCAPE, "key.keyboard.escape"));
    assertEquals("menu:key.keyboard.unknown", h.actions.binding);
    assertFalse(h.actions.closed);
    assertFalse(h.menu.isBindingCapture());
    h.openModule("freelook");
    h.click("binding-freelook");
    h.menu.captureBinding(Keys.DELETE, "key.keyboard.delete");
    assertEquals("freelook:key.keyboard.unknown", h.actions.binding);
    assertFalse(h.menu.isBindingCapture());
  }
  @Test
  void colorPopoverPreservesRgbaAndEscapeDismissesItBeforeTheMenu() throws Exception {
    Harness h = new Harness(
        "menu { color 'Accent' (id=fixture-accent,bind=theme.accent) { height=24px } }", true);
    h.click("fixture-accent");
    h.click("color-hex");
    h.replace("#11223380");
    h.menu.keyPressed(Keys.ENTER, 0);
    assertEquals(0x80112233, h.settings.theme.accent);
    h.menu.keyPressed(Keys.ESCAPE, 0);
    assertFalse(h.actions.closed);
    h.menu.keyPressed(Keys.ESCAPE, 0);
    assertTrue(h.actions.closed);
  }
  @Test
  void unicodeProfileInputEditsCompleteCodePointsAndCrudRefreshesTheList() {
    Harness h = new Harness();
    h.page(3);
    h.click("create-profile");
    h.click("profile-name");
    h.type("PvP 🎮");
    h.menu.keyPressed(Keys.BACKSPACE, 0);
    h.type("🌙");
    h.menu.keyPressed(Keys.ENTER, 0);
    h.click("save-profile");
    assertEquals("PvP 🌙", h.actions.saved);
    assertTrue(h.canvas.textValues().contains("PvP 🌙"));
  }
  @Test
  void cachedProfileListAvoidsDirectoryReadsOnEveryFrame() {
    Harness h = new Harness();
    h.page(3);
    int before = h.actions.profileReads;
    for (int i = 0; i < 8; i++) h.render();
    assertEquals(before, h.actions.profileReads);
    h.menu.onClose();
    h.menu.onOpen();
    h.render();
    h.page(3);
    assertTrue(h.actions.profileReads > before,
        "Reentering Profiles after reopen refreshes the directory snapshot");
    int refreshed = h.actions.profileReads;
    for (int i = 0; i < 8; i++) h.render();
    assertEquals(refreshed, h.actions.profileReads);
  }
  @Test
  void externalCloseClearsFocusedInputAndRejectsIsolatedSurrogates() {
    Harness h = new Harness();
    h.openModule("protect");
    h.click("protect.replacement");
    h.replace("Игрок 🌙");
    assertFalse(h.menu.charTyped(0xd800));
    h.menu.onClose();
    assertFalse(h.menu.isTextFocused());
    assertFalse(h.menu.charTyped('X'));
    assertEquals("Игрок 🌙", h.settings.protect.replacement);
  }
  @Test
  void searchQueryPersistsAcrossMenuReopen() {
    Harness h = new Harness();
    h.click("nav-all");
    h.click("module-search");
    h.type("sky");
    h.render();
    assertTrue(h.canvas.textValues().contains("sky"));
    assertNotNull(h.menu.elementBounds("all-customSky"));
    h.menu.onClose();
    h.menu.onOpen();
    h.render();
    assertTrue(h.canvas.textValues().contains("sky"),
        "Search query should remain in module-search after reopening the menu");
    assertNotNull(h.menu.elementBounds("all-customSky"),
        "Filtered search results should persist after reopening the menu");
  }
  @Test
  void hudEditorUsesNcToolbarAndGestureBoundsTogether() {
    Harness h = new Harness();
    h.editor();
    assertNotNull(h.menu.elementBounds("editor-target-crosshair"));
    assertNotNull(h.menu.elementBounds("editor-target-betterChat"));
    float[] b = h.bounds("cooldowns", 1000, 700);
    h.menu.mouseClicked(b[0] + 20, b[1] + 20, 0);
    h.menu.mouseDragged(-100, -100, 0, -500, -500);
    h.menu.mouseReleased(0, 0, 0);
    float[] moved = h.bounds("cooldowns", 1000, 700);
    assertEquals(0f, moved[0], .001f);
    assertEquals(0f, moved[1], .001f);
    h.menu.keyPressed(Keys.RIGHT, Keys.MOD_SHIFT);
    assertTrue(h.bounds("cooldowns", 1000, 700)[0] >= moved[0]);
  }
  @Test
  void betterChatIsDraggableAndScalableInHudEditorAndHiddenInLiveSkinHud() {
    Harness h = new Harness();
    h.settings.hud.snap = false;
    h.editor();
    assertNotNull(h.menu.elementBounds("editor-target-betterChat"));
    float[] before = h.bounds("betterChat", 1000, 700);
    assertTrue(before[2] > 100f && before[3] > 40f, "Better Chat preview must render in HUD editor");
    h.menu.mouseClicked(before[0] + 30, before[1] + 20, 0);
    h.menu.mouseDragged(before[0] + 150, before[1] - 80, 0, 120, -100);
    h.menu.mouseReleased(before[0] + 150, before[1] - 80, 0);
    float[] after = h.bounds("betterChat", 1000, 700);
    assertTrue(after[0] > before[0] + 50f, "Better Chat should drag horizontally in HUD editor");
    assertTrue(after[1] < before[1] - 50f, "Better Chat should drag vertically in HUD editor");
    assertTrue(h.settings.betterChat.layoutOrder >= 1);
    h.render();
    float rx = after[0] + after[2], ry = after[1] + after[3];
    assertTrue(h.menu.mouseClicked(rx, ry, 0), "Bottom-right resize handle must capture click");
    h.menu.mouseDragged(rx + 60, ry + 24, 0, 60, 24);
    h.menu.mouseReleased(rx + 60, ry + 24, 0);
    assertTrue(h.settings.betterChat.scale > 1.0f, "Resize handle should scale Better Chat");
  }
  @Test
  void cosmeticsProfileHoverRespectsBottomLeftMenuCornerRadius() {
    Harness h = new Harness();
    h.render();
    float[] profile = h.menu.elementBounds("local-profile");
    assertNotNull(profile);
    h.canvas.rounded.clear();
    h.menu.render(h.canvas, 1000, 700, profile[0] + 10, profile[1] + 10, 2);
    float expectedRadius =
        Math.max(0f, Math.min(12f, h.settings.theme.radius * 0.75f)) * h.settings.menu.scale;
    boolean foundProfileHover = false;
    for (float[] r : h.canvas.rounded) {
      if (Math.abs(r[0] - profile[0]) < 1.5f && Math.abs(r[1] - profile[1]) < 1.5f
          && Math.abs(r[2] - profile[2]) < 1.5f && Math.abs(r[3] - profile[3]) < 1.5f
          && Math.abs(r[4] - expectedRadius) < 0.2f && r[5] > 0) {
        foundProfileHover = true;
      }
    }
    assertTrue(foundProfileHover,
        "local-profile cosmetics hover must render its concentric inset pill cleanly");
  }
  @Test
  void selectingViewmodelDoesNotPreventPickingAndDraggingAnotherHudWidget() {
    Harness h = new Harness();
    h.editor();
    h.click("editor-target-viewmodel");
    float[] before = h.bounds("tnt", 1000, 700);
    float x = before[0] + before[2] / 2, y = before[1] + before[3] / 2;
    assertTrue(h.menu.mouseClicked(x, y, 0));
    assertTrue(h.menu.mouseDragged(x + 24, y + 14, 0, 24, 14));
    h.menu.mouseReleased(x + 24, y + 14, 0);
    h.render();
    float[] moved = h.bounds("tnt", 1000, 700);
    assertTrue(moved[0] > before[0] + 10);
    assertTrue(h.menu.mouseClicked(moved[0] + moved[2] / 2, moved[1] + moved[3] / 2, 0));
    h.menu.mouseReleased(0, 0, 0);
    assertNotNull(h.menu.elementBounds("editor-target-tnt"));
    h.menu.leaveEditor();
    h.menu.openEditor();
    h.render();
    assertTrue(h.menu.isEditor());
  }
  @Test
  void controlWheelScalesOnlyTheSelectedHand() {
    Harness h = new Harness();
    h.actions.viewmodelSupported = true;
    h.settings.viewmodel.off.scaleY = 2f;
    h.editor();
    h.click("editor-target-viewmodel");
    h.click("viewmodel-hand-off");
    int before = h.actions.checkpoints;

    assertFalse(h.menu.mouseScrolled(500, 400, 1, 0));
    assertEquals(1f, h.settings.viewmodel.off.scaleX);
    assertTrue(h.menu.mouseScrolled(500, 400, 1, Keys.MOD_CONTROL));
    assertTrue(h.settings.viewmodel.enabled);
    assertEquals(1.1f, h.settings.viewmodel.off.scaleX, .0001f);
    assertEquals(2.2f, h.settings.viewmodel.off.scaleY, .0001f);
    assertEquals(1.1f, h.settings.viewmodel.off.scaleZ, .0001f);
    assertEquals(1f, h.settings.viewmodel.main.scaleX);
    assertEquals(before + 1, h.actions.checkpoints);

    assertTrue(h.menu.mouseScrolled(500, 400, -1, Keys.MOD_CONTROL | Keys.MOD_SHIFT));
    assertEquals(1.1 / 1.01, h.settings.viewmodel.off.scaleX, .0001);
    assertEquals(before + 2, h.actions.checkpoints);
    h.click("editor-target-crosshair");
    assertTrue(h.menu.mouseScrolled(500, 400, 1, Keys.MOD_CONTROL));
    assertEquals(2, h.settings.crosshair.pixelScale);
    assertEquals(1.1 / 1.01, h.settings.viewmodel.off.scaleX, .0001);
  }

  @Test
  void editorStartsWithoutAnInspectorAndDocksItAwayFromSelection() {
    Harness h = new Harness();
    h.editor();
    assertNull(h.menu.elementBounds("editor-inspector"));
    float scale = h.settings.hud.scale;
    h.menu.mouseScrolled(500, 350, 2);
    assertEquals(scale, h.settings.hud.scale);
    h.click("editor-target-cooldowns");
    float[] inspector = h.menu.elementBounds("editor-inspector");
    assertNotNull(inspector);
    float[] widget = h.bounds("cooldowns", 1000, 700);
    assertTrue(inspector[0] + inspector[2] <= widget[0] || widget[0] + widget[2] <= inspector[0]
        || inspector[1] + inspector[3] <= widget[1] || widget[1] + widget[3] <= inspector[1]);
    assertTrue(inspector[0] >= 0 && inspector[1] >= 0 && inspector[0] + inspector[2] <= 1000
        && inspector[1] + inspector[3] <= 700);
  }
  @Test
  void firstMoveOfFittedDefaultPanelPreservesItsRenderedSize() {
    Harness h = new Harness();
    h.settings.hud.snap = false;
    h.editor();
    h.canvas.clear();
    h.menu.render(h.canvas, 640, 400, 0, 0, h.time++);
    float[] before = h.bounds("tnt", 640, 400);
    h.menu.mouseClicked(before[0] + before[2] / 2, before[1] + before[3] / 2, 0);
    h.menu.mouseDragged(before[0] + before[2] / 2 + 10, before[1] + before[3] / 2, 0, 10, 0);
    h.menu.mouseReleased(0, 0, 0);
    float[] after = h.bounds("tnt", 640, 400);
    assertEquals(before[2], after[2], .001f);
    assertEquals(before[3], after[3], .001f);
    assertEquals(before[0] + 10, after[0], .001f);
  }
  @Test
  void keyboardMovePreservesTheTinyViewportFittedScale() {
    Harness h = new Harness();
    h.editor();
    h.click("editor-target-tnt");
    h.canvas.clear();
    h.menu.render(h.canvas, 320, 200, 0, 0, h.time++);
    float[] before = h.bounds("tnt", 320, 200);
    h.menu.keyPressed(Keys.RIGHT, Keys.MOD_SHIFT);
    float[] after = h.bounds("tnt", 320, 200);
    assertEquals(before[2], after[2], .001f);
    assertEquals(before[3], after[3], .001f);
    assertEquals(before[0] + 10, after[0], .001f);
  }
  @Test
  void compactEditorToolbarNeverCoversTheTopDefaultWidgets() {
    Harness h = new Harness();
    h.editor();
    for (int[] viewport : new int[][] {{320, 200}, {640, 400}, {1000, 700}}) {
      h.canvas.clear();
      h.menu.render(h.canvas, viewport[0], viewport[1], 0, 0, h.time++);
      float[] first = h.menu.elementBounds("editor-target-cooldowns"),
              last = h.menu.elementBounds("editor-done");
      assertNotNull(first);
      assertNotNull(last);
      float padding = viewport[0] < 1100 || viewport[1] < 500 ? 2 : 8;
      float[] toolbar = {first[0] - padding, first[1] - padding,
          last[0] + last[2] - first[0] + 2 * padding, first[3] + 2 * padding};
      for (float[] b : new float[][] {h.bounds("cooldowns", viewport[0], viewport[1]),
               h.bounds("tnt", viewport[0], viewport[1])})
        assertTrue(toolbar[0] + toolbar[2] <= b[0] || b[0] + b[2] <= toolbar[0]
            || toolbar[1] + toolbar[3] <= b[1] || b[1] + b[3] <= toolbar[1]);
    }
  }
  @Test
  void cooldownResizeUsesItsEffectiveScaleWithoutAssumingAPanelBoundsShape() {
    Harness h = new Harness();
    h.editor();
    h.click("editor-target-cooldowns");
    float[] b = h.bounds("cooldowns", 1000, 700);
    assertTrue(h.menu.mouseClicked(b[0] + b[2], b[1] + b[3], 0));
    assertTrue(h.menu.mouseDragged(b[0] + b[2] * 1.1, b[1] + b[3] * 1.1, 0, 20, 10));
    h.menu.mouseReleased(0, 0, 0);
    assertTrue(h.bounds("cooldowns", 1000, 700)[2] > b[2]);
  }
  @Test
  void firstWheelStepUsesVisibleScaleAndPreservesFittedPanelPosition() {
    for (String id : new String[] {"tnt", "inventory", "cooldowns"}) {
      Harness h = new Harness();
      h.editor();
      h.click("editor-target-" + id);
      h.canvas.clear();
      h.menu.render(h.canvas, 640, 400, 0, 0, h.time++);
      float[] before = h.bounds(id, 640, 400);
      float effective = before[4];
      h.menu.mouseScrolled(before[0] + before[2] / 2, before[1] + before[3] / 2, 1);
      float[] after = h.bounds(id, 640, 400);
      float next = after[4];
      assertEquals(effective + .05f, next, .0001f);
      assertEquals(Math.min(before[0], 640 - after[2] - 8), after[0], .0001f,
          "Growing at the right edge must keep the widget on screen");
      assertEquals(before[1], after[1], .0001f);
    }
  }
  @Test
  void pixelCrosshairKeyboardNudgeIsExactlyOnePixelAndZeroDragKeepsItsPosition() {
    Harness h = new Harness();
    h.settings.crosshair.mode = "pixels";
    h.editor();
    h.click("editor-target-crosshair");
    h.menu.keyPressed(Keys.RIGHT, 0);
    assertEquals(1, h.settings.crosshair.offsetX);
    assertEquals(0, h.settings.crosshair.offsetY);
    h.menu.keyPressed(Keys.DOWN, 0);
    assertEquals(1, h.settings.crosshair.offsetY);
    h.settings.hud.snap = false;
    h.render();
    float[] b = FeatureHudRenderer.crosshairBounds(h.settings, 1000, 700);
    float x = b[0] + b[2] / 2, y = b[1] + b[3] / 2;
    h.menu.mouseClicked(x, y, 0);
    h.menu.mouseDragged(x, y, 0, 0, 0);
    h.menu.mouseReleased(x, y, 0);
    assertEquals(1, h.settings.crosshair.offsetX);
    assertEquals(1, h.settings.crosshair.offsetY);
  }
  @Test
  void hudEditorResizesOnlyTheSelectedWidget() {
    Harness h = new Harness();
    h.editor();
    h.click("editor-target-crosshair");
    h.menu.mouseScrolled(900, 650, 3);
    assertEquals(4, h.settings.crosshair.pixelScale);
    assertEquals(1, h.settings.inventory.scale);
    assertEquals(1, h.settings.hud.scale);
  }
  @Test
  void nativeArmorScaleDoesNotJumpWhenMovingOrResizingAtContainerSize120() {
    Harness h = new Harness();
    h.settings.menu.hudScale = 1.2f;
    h.settings.hud.snap = false;
    h.editor();
    h.click("editor-target-armor");
    float[] before = h.bounds("armor", 1000, 700);
    assertEquals(1, before[4], .0001f);
    h.menu.mouseScrolled(900, 650, 1);
    h.render();
    assertEquals(1.05f, h.settings.armor.scale, .0001f);
    float[] scaled = h.bounds("armor", 1000, 700);
    float x = scaled[0] + scaled[2] / 2, y = scaled[1] + scaled[3] / 2;
    h.menu.mouseClicked(x, y, 0);
    h.menu.mouseDragged(x - 16, y - 8, 0, -16, -8);
    h.menu.mouseReleased(x - 16, y - 8, 0);
    h.render();
    assertEquals(1.05f, h.settings.armor.scale, .0001f);
    assertEquals(1.05f, h.bounds("armor", 1000, 700)[4], .0001f);
  }
  @Test
  void editorCrosshairButtonOpensTheRealNcPixelGrid() {
    Harness h = new Harness();
    h.editor();
    h.click("editor-target-crosshair");
    h.click("editor-draw-crosshair");
    assertNotNull(h.menu.elementBounds("pixel-grid"));
    h.seek("crosshair.colorMode");
    assertNotNull(h.menu.elementBounds("crosshair.colorMode"));
  }
  @Test
  void pixelDrawingFillsCrossedCellsAndRightButtonErasesWithOneCheckpoint() {
    Harness h = new Harness();
    h.settings.crosshair.pattern.clear();
    h.openModule("crosshair");
    float[] a = h.pixel(1, 3), b = h.pixel(10, 3);
    int before = h.actions.checkpoints;
    h.menu.mouseClicked(a[0], a[1], 0);
    h.menu.mouseDragged(b[0], b[1], 0, b[0] - a[0], 0);
    h.menu.mouseReleased(0, 0, 0);
    for (int x = 1; x <= 10; x++)
      assertEquals(0xffffffff, h.settings.crosshair.pattern.pixel(x, 3));
    assertEquals(before + 1, h.actions.checkpoints);
    float[] erase = h.pixel(6, 3);
    h.menu.mouseClicked(erase[0], erase[1], 1);
    h.menu.mouseReleased(0, 0, 1);
    assertEquals(0, h.settings.crosshair.pattern.pixel(6, 3));
  }
  @Test
  void pixelMirrorsUseTheSameBrushGesture() {
    Harness h = new Harness();
    h.settings.crosshair.pattern.clear();
    h.settings.crosshair.mirrorX = h.settings.crosshair.mirrorY = true;
    h.openModule("crosshair");
    float[] p = h.pixel(2, 2);
    h.menu.mouseClicked(p[0], p[1], 0);
    h.menu.mouseReleased(0, 0, 0);
    for (int[] cell : new int[][] {{2, 2}, {12, 2}, {2, 12}, {12, 12}})
      assertEquals(0xffffffff, h.settings.crosshair.pattern.pixel(cell[0], cell[1]));
  }
  @Test
  void menuResizingAndScaleNeverPutNavigationOutsideTheViewport() {
    Harness h = new Harness();
    for (int[] viewport : new int[][] {{640, 400}, {320, 240}, {1280, 800}})
      for (float scale : new float[] {.65f, 1, 1.65f}) {
        h.settings.menu.scale = scale;
        h.canvas.clear();
        h.menu.render(h.canvas, viewport[0], viewport[1], 0, 0, h.time++);
        float[] nav = h.menu.elementBounds("nav-settings");
        for (int attempt = 0; (nav == null || nav[3] < 16) && attempt < 12; attempt++) {
          float[] scroll = h.menu.elementBounds("sidebar-scroll");
          assertNotNull(scroll);
          assertTrue(
              h.menu.mouseScrolled(scroll[0] + scroll[2] / 2, scroll[1] + scroll[3] / 2, -2));
          h.canvas.clear();
          h.menu.render(h.canvas, viewport[0], viewport[1], 0, 0, h.time++);
          nav = h.menu.elementBounds("nav-settings");
        }
        assertNotNull(nav, "Unified settings remain reachable through sidebar scrolling");
        assertTrue(nav[0] >= 0 && nav[1] >= 0 && nav[0] + nav[2] <= viewport[0] + .01f
            && nav[1] + nav[3] <= viewport[1] + .01f);
        assertEquals(0, h.canvas.clips);
      }
  }
  @Test
  void zeroBackgroundLeavesTextOpaqueAndTranslucentRetainsBackdrop() {
    Harness h = new Harness();
    h.settings.theme.effects.backgroundBlur = true;
    h.settings.theme.glassMix = -1;
    h.render();
    List<String> labels = new ArrayList<String>(h.canvas.textValues());
    h.settings.theme.backgroundOpacity = 0;
    h.render();
    assertEquals(labels, h.canvas.textValues());
    assertTrue(h.menu.usesBackdrop());
    assertEquals(0, h.canvas.backdrops);
    h.settings.theme.backgroundOpacity = .25f;
    h.render();
    assertTrue(h.canvas.backdrops > 0);
  }
  @Test
  void hudEditorToolbarAndInspectorRespectTheBackdropSetting() {
    Harness h = new Harness();
    h.editor();
    h.click("editor-target-armor");
    for (float opacity : new float[] {.42f, .94f}) {
      h.settings.theme.backgroundOpacity = opacity;
      for (boolean blur : new boolean[] {true, false}) {
        h.settings.theme.effects.backgroundBlur = blur;
        h.render();
        h.assertBackdrop("editor-toolbar", 10, blur);
        h.assertBackdrop("editor-inspector", 10, blur);
      }
    }
  }
  @Test
  void standaloneWaypointFormRespectsTheBackdropSetting() {
    Harness h = new Harness();
    h.menu.openWaypointCreateStandalone();
    for (float opacity : new float[] {.42f, .94f}) {
      h.settings.theme.backgroundOpacity = opacity;
      for (boolean blur : new boolean[] {true, false}) {
        h.settings.theme.effects.backgroundBlur = blur;
        h.render();
        h.assertBackdrop("waypoint-manager", 10 * h.settings.menu.scale, blur);
      }
    }
  }
  @Test
  void waypointShortcutOpensTheNewMarkerFormWithoutCreatingAMarker() {
    Harness h = new Harness();
    int before = h.settings.waypoints.points.size();
    h.menu.openWaypointCreateStandalone();
    h.render();
    assertNotNull(h.menu.elementBounds("waypoint-manager"));
    assertNotNull(
        h.menu.elementBounds("waypoint-draft-name"), "The shortcut must open the create form");
    assertNotNull(h.menu.elementBounds("waypoint-apply"), "The create form must be submittable");
    assertNull(
        h.menu.elementBounds("waypoint-create"), "The shortcut must not open the marker list");
    assertNull(
        h.menu.elementBounds("module-search"), "The shortcut must not expose ClickGUI underneath");
    assertEquals(
        before, h.settings.waypoints.points.size(), "Opening the form must not create a marker");
    h.click("waypoint-cancel");
    assertTrue(h.actions.closed, "Cancelling the standalone create form returns to the world");
  }
  @Test
  void waypointCreateSurfaceClicksKeepTheModalOpenAndOmitDimension() {
    Harness h = new Harness("default");
    h.menu.openWaypointCreateStandalone();
    h.render();
    assertFalse(h.canvas.textValues().contains("minecraft:overworld"),
        "The create form must not show a dimension field");
    float[] shuffle = h.menu.elementBounds("waypoint-shuffle");
    assertNotNull(shuffle);
    float x = shuffle[0] - 20 * h.settings.menu.scale, y = shuffle[1] + shuffle[3] / 2;
    assertTrue(h.menu.mouseClicked(x, y, 0));
    h.menu.mouseReleased(x, y, 0);
    h.render();
    assertNotNull(h.menu.elementBounds("waypoint-manager"),
        "Clicking the marker colour must not dismiss the create form");
    assertNotNull(h.menu.elementBounds("waypoint-apply"));
    assertFalse(h.actions.closed);
    h.click("waypoint-cancel");
    assertTrue(h.actions.closed);
  }
  @Test
  void waypointsTabManagesInlineWithoutOpeningModalPopup() {
    Harness h = new Harness("default");
    h.click("nav-waypoints");
    h.render();
    assertNotNull(h.menu.elementBounds("waypoints-tab-here"));
    assertNotNull(h.menu.elementBounds("waypoints-tab-create"));
    assertNull(h.menu.elementBounds("waypoint-manager"));

    int before = h.settings.waypoints.points.size();
    h.click("waypoints-tab-create");
    h.render();
    assertNotNull(h.menu.elementBounds("waypoints-tab-form"),
        "Create button inside Waypoints tab must open the inline form");
    assertNotNull(h.menu.elementBounds("waypoints-tab-draft-name"));
    assertNotNull(h.menu.elementBounds("waypoints-tab-apply"));
    assertNull(h.menu.elementBounds("waypoint-manager"),
        "Waypoints tab must not pop up a floating modal when creating inline");

    h.click("waypoints-tab-draft-name");
    for (char c : "Vault".toCharArray()) h.menu.charTyped(c);
    h.click("waypoints-tab-apply");
    h.render();
    assertEquals(before + 1, h.settings.waypoints.points.size());
    assertEquals("Vault", h.settings.waypoints.points.get(before).label);
    assertNull(h.menu.elementBounds("waypoints-tab-form"));
    assertNotNull(h.menu.elementBounds("waypoints-tab-create"));

    h.menu.openWaypointCreateStandalone();
    h.render();
    assertNotNull(h.menu.elementBounds("waypoint-manager"),
        "Standalone hotkey modal must still open even when the menu was last on Waypoints tab");
  }
  @Test
  void defaultColorSwatchOpensFloatingPopoverColorPicker() {
    Harness h = new Harness("default");
    h.page(1);
    h.click("option.theme-mode.custom");
    float[] row = h.menu.elementBounds("custom-background");
    assertNotNull(row, "The custom surface colour row must be present");
    float x = row[0] + row[2] - 24 * h.settings.menu.scale, y = row[1] + row[3] / 2;
    assertTrue(h.menu.mouseClicked(x, y, 0));
    h.menu.mouseReleased(x, y, 0);
    h.render();
    float[] dialog = h.menu.elementBounds("color-dialog");
    float[] plane = h.menu.elementBounds("color-plane");
    assertNotNull(dialog, "Clicking a colour swatch opens the floating colour picker mini-menu");
    assertNotNull(plane, "The floating colour picker exposes its saturation/value plane");
    h.click("color-done");
    h.render();
    assertNull(h.menu.elementBounds("color-dialog"), "Closing dismisses the floating colour picker");
  }
  @Test
  void moduleLeftClickTogglesAndRightClickExpandsWithoutChangingItsState() {
    Harness h = new Harness();
    assertFalse(h.settings.saturation.enabled);
    h.click("saturation");
    assertTrue(h.settings.saturation.enabled);
    assertNull(h.menu.elementBounds("saturation.strength"));
    h.click("expand.saturation");
    assertTrue(h.settings.saturation.enabled);
    assertNotNull(h.menu.elementBounds("saturation.strength"));
    h.click("module-toggle.saturation");
    assertFalse(h.settings.saturation.enabled);
    assertNotNull(h.menu.elementBounds("saturation.strength"));
    h.click("saturation");
    assertTrue(h.settings.saturation.enabled);
    h.click("expand.saturation");
    assertNull(h.menu.elementBounds("saturation.strength"));
    assertTrue(h.settings.saturation.enabled);
    h.click("saturation", 2);
    assertTrue(h.menu.isBindingCapture());
    h.menu.captureBinding(71, "key.keyboard.g");
    assertEquals("module:saturation:key.keyboard.g", h.actions.binding);
    assertFalse(h.actions.closed);
  }
  @Test
  void everyPartOfTheModuleHeaderUsesTheSameMouseButtons() {
    Harness h = new Harness();
    for (float fraction : new float[] {.03f, .5f, .97f}) {
      float[] row = h.menu.elementBounds("saturation");
      assertNotNull(row);
      float x = row[0] + row[2] * fraction, y = row[1] + row[3] / 2;
      boolean before = h.settings.saturation.enabled;
      assertTrue(h.menu.mouseClicked(x, y, 0));
      h.menu.mouseReleased(x, y, 0);
      h.render();
      assertNotEquals(before, h.settings.saturation.enabled, "left click at " + fraction);
      assertTrue(h.menu.mouseClicked(x, y, 1));
      h.menu.mouseReleased(x, y, 1);
      h.render();
      assertNotNull(h.menu.elementBounds("saturation.strength"), "right click at " + fraction);
      assertTrue(h.menu.mouseClicked(x, y, 1));
      h.menu.mouseReleased(x, y, 1);
      h.render();
      assertNull(h.menu.elementBounds("saturation.strength"));
    }
  }
  @Test
  void openingAndClosingAnimationsKeepControlsAndPhysicalInputAligned() {
    Settings s = new Settings();
    Actions a = new Actions();
    MenuController menu = new MenuController(s, a);
    RecordingCanvas c = new RecordingCanvas();
    menu.onOpen();
    menu.render(c, 1000, 700, 0, 0, 0);
    c.clear();
    menu.render(c, 1000, 700, 0, 0, .12);
    float[] b = menu.elementBounds("expand.saturation");
    assertNotNull(b);
    assertTrue(menu.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, 1));
    menu.mouseReleased(0, 0, 1);
    c.clear();
    menu.render(c, 1000, 700, 0, 0, .4);
    c.clear();
    menu.render(c, 1000, 700, 0, 0, .7);
    assertNotNull(menu.elementBounds("saturation.strength"),
        "Expanded controls become reachable after the card grows");
    assertFalse(s.saturation.enabled);
    menu.keyPressed(Keys.ESCAPE, 0);
    assertFalse(a.closed);
    menu.render(c, 1000, 700, 0, 0, 1.1);
    assertTrue(a.closed);
  }

  @Test
  void cachedMenuContextProducesTheSameCommandsAsARebuiltOneAcrossLanguageChanges() {
    Harness h = new Harness();
    String russian = h.hashAt(8);
    assertEquals(russian, h.hashAt(8), "Repeated cached frames must not change the command stream");
    h.settings.language = "en";
    String english = h.hashAt(8);
    assertNotEquals(russian, english, "The menu cache must follow the active language");
    assertEquals(english, h.hashAt(8));
    h.settings.language = "ru";
    assertEquals(russian, h.hashAt(8), "Restoring the language must restore every cached label");
  }

  @Test
  void bindingCaptureInvalidatesTheCachedMenuContextAndRestoresTheSameCommands() {
    Harness h = new Harness();
    h.click("nav-camera");
    h.click("expand.zoom");
    String idle = h.hashAt(8);
    h.click("binding-zoom");
    String capturing = h.hashAt(8);
    assertNotEquals(
        idle, capturing, "The capture prompt must not be masked by a cached binding label");
    h.menu.captureBinding(Keys.ESCAPE, "key.keyboard.escape");
    assertEquals(idle, h.hashAt(8));
  }

  @Test
  void skinReloadPicksUpExternalBindingLabelChangesWithoutStaleMenuContext() {
    Harness h = new Harness();
    h.click("nav-camera");
    h.click("expand.zoom");
    h.menu.refreshSkin();
    h.render();
    String unbound = h.hashAt(8);
    assertTrue(h.canvas.textValues().contains(h.actions.keyBindingLabel("zoom")));
    h.actions.labels.put("zoom", "G");
    h.menu.refreshSkin();
    h.render();
    String bound = h.hashAt(8);
    assertTrue(h.canvas.textValues().contains("G"));
    assertNotEquals(
        unbound, bound, "Profile and config reloads must rebuild cached binding labels");
    h.actions.labels.remove("zoom");
    h.menu.refreshSkin();
    h.render();
    assertEquals(unbound, h.hashAt(8));
  }

  @Test
  void menuModulesRefreshTheirLiveValuesWithoutRebuildingPresentationContext() {
    Harness h = new Harness();
    String disabled = h.hashAt(8);
    h.settings.saturation.enabled = true;
    String enabled = h.hashAt(8);
    assertNotEquals(
        disabled, enabled, "A module toggle must be visible without invalidating the whole menu");
    h.settings.saturation.enabled = false;
    assertEquals(disabled, h.hashAt(8));
  }

  @Test
  void skinRefreshDropsCachedMenuAndEditorContextsWithoutChangingTheFrame() {
    Harness h = new Harness();
    h.page(2);
    h.menu.refreshSkin();
    String menu = h.hashAt(8);
    h.menu.refreshSkin();
    assertEquals(menu, h.hashAt(8),
        "A reloaded skin must rebuild its cached context but keep the same visuals");
    h.editor();
    h.settings.language = "en";
    String editorEnglish = h.hashAt(8);
    h.menu.refreshSkin();
    assertEquals(editorEnglish, h.hashAt(8),
        "Editor toolbar text must survive a skin reload in the active language");
    h.menu.leaveEditor();
    h.settings.language = "ru";
    assertEquals(menu, h.hashAt(8));
  }

  @Test
  void cachedPresentationValuesTrackAnimationTimeWithoutReplayingStaleEntries() {
    Settings settings = new Settings();
    Actions actions = new Actions();
    MenuController menu = new MenuController(settings, actions);
    RecordingCanvas canvas = new RecordingCanvas();
    menu.onOpen();
    canvas.clear();
    menu.render(canvas, 1000, 700, 0, 0, 0);
    String start = canvas.digest();
    canvas.clear();
    menu.render(canvas, 1000, 700, 0, 0, 0);
    assertEquals(
        start, canvas.digest(), "An unchanged frame time must reuse the cached enter/fade values");
    canvas.clear();
    menu.render(canvas, 1000, 700, 0, 0, .05);
    assertNotEquals(
        start, canvas.digest(), "Advancing the animation must update the cached enter/fade values");
    canvas.clear();
    menu.render(canvas, 1000, 700, 0, 0, 0);
    assertEquals(start, canvas.digest());
  }

  @Test
  void cornerRadiusSliderUnifiesMenuCornersAndRemovesClickGuiAccountButtons() {
    Harness h = new Harness();
    h.render();
    assertNull(h.menu.elementBounds("sidebar-account-switch"),
        "Account switcher button must not appear in ClickGUI sidebar");
    h.page(1);
    h.seek("style-radius");
    assertNotNull(h.menu.elementBounds("style-radius"),
        "Appearance settings must expose unified corner radius slider");

    h.settings.theme.radius = 0f;
    h.page(0);
    h.render();
    float[] profile = h.menu.elementBounds("local-profile");
    assertNotNull(profile);
    h.canvas.rounded.clear();
    h.menu.render(h.canvas, 1000, 700, profile[0] + 10, profile[1] + 10, 2);
    boolean foundSharpProfile = false;
    for (float[] r : h.canvas.rounded) {
      if (Math.abs(r[0] - profile[0]) < 1.5f && Math.abs(r[1] - profile[1]) < 1.5f
          && Math.abs(r[2] - profile[2]) < 1.5f && Math.abs(r[3] - profile[3]) < 1.5f
          && Math.abs(r[4]) < 0.05f && r[5] > 0) {
        foundSharpProfile = true;
      }
    }
    assertTrue(foundSharpProfile, "Setting theme.radius=0 must make ClickGUI controls square");
  }

  @Test
  void aspectRatioPresetsUpdateRatioAndManualSliderSwitchesToCustom() {
    Harness h = new Harness();
    h.click("nav-camera");
    h.seek("expand.aspectRatio");
    h.click("expand.aspectRatio");
    h.seek("aspectRatio.preset");

    h.click("aspectRatio.preset");
    h.seek("option.aspectRatio.preset.4_3");
    h.click("option.aspectRatio.preset.4_3");
    assertEquals("4_3", h.settings.aspectRatio.preset);
    assertEquals(4f / 3f, h.settings.aspectRatio.ratio, 0.001f);

    h.click("aspectRatio.preset");
    h.seek("option.aspectRatio.preset.21_9");
    h.click("option.aspectRatio.preset.21_9");
    assertEquals("21_9", h.settings.aspectRatio.preset);
    assertEquals(21f / 9f, h.settings.aspectRatio.ratio, 0.001f);

    h.click("aspectRatio.preset");
    h.seek("option.aspectRatio.preset.5_4");
    h.click("option.aspectRatio.preset.5_4");
    assertEquals("5_4", h.settings.aspectRatio.preset);
    assertEquals(5f / 4f, h.settings.aspectRatio.ratio, 0.001f);

    h.click("aspectRatio.preset");
    h.seek("option.aspectRatio.preset.16_10");
    h.click("option.aspectRatio.preset.16_10");
    assertEquals("16_10", h.settings.aspectRatio.preset);
    assertEquals(16f / 10f, h.settings.aspectRatio.ratio, 0.001f);

    h.seek("aspectRatio.ratio");
    float[] slider = h.menu.elementBounds("aspectRatio.ratio");
    assertNotNull(slider);
    assertTrue(h.menu.mouseClicked(slider[0] + slider[2] * 0.8f, slider[1] + slider[3] * 0.5f, 0));
    h.menu.mouseReleased(slider[0] + slider[2] * 0.8f, slider[1] + slider[3] * 0.5f, 0);
    h.render();
    assertEquals("custom", h.settings.aspectRatio.preset);
  }
}
