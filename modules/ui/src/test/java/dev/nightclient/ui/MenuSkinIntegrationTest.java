package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.Keys;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.SkinParser;
import dev.nightclient.skin.NcParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuSkinIntegrationTest {
  static final class Text {
    final String value;
    final float x, y;
    Text(String value, float x, float y) {
      this.value = value;
      this.x = x;
      this.y = y;
    }
  }
  static final class Sink implements Canvas {
    final List<Text> texts = new ArrayList<Text>();
    final List<Integer> fills = new ArrayList<Integer>();
    int clips, backdrops;
    public void rect(float x, float y, float w, float h, int c) {
      assertTrue(w >= 0 && h >= 0);
      fills.add(c);
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      assertTrue(w >= 0 && h >= 0);
      fills.add(c);
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      assertTrue(w >= 0 && h >= 0);
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    public void line(float x, float y, float xx, float yy, float t, int c) {}
    public void circle(float x, float y, float r, int c) {}
    public void text(String value, float x, float y, float size, int c, boolean bold) {
      texts.add(new Text(value, x, y));
    }
    public void textTracked(
        String value, float x, float y, float size, int c, boolean bold, float tracking) {
      text(value, x, y, size, c, bold);
    }
    public float textWidth(String value, float size, boolean bold) {
      return value.codePointCount(0, value.length()) * size * .5f;
    }
    public float textWidthTracked(String value, float size, boolean bold, float tracking) {
      return textWidth(value, size, bold)
          + Math.max(0, value.codePointCount(0, value.length()) - 1) * tracking;
    }
    public void pushClip(float x, float y, float w, float h) {
      assertTrue(w >= 0 && h >= 0);
      clips++;
    }
    public void popClip() {
      assertTrue(clips > 0);
      clips--;
    }
    public void item(String id, float x, float y, float size, float alpha) {}
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float alpha) {
      backdrops++;
    }
    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
    }
    void clear() {
      assertEquals(0, clips);
      texts.clear();
      fills.clear();
      backdrops = 0;
    }
    List<String> strings() {
      List<String> values = new ArrayList<String>();
      for (Text text : texts) values.add(text.value);
      return values;
    }
    Text find(String text) {
      for (int index = texts.size() - 1; index >= 0; index--) {
        Text item = texts.get(index);
        if (item.value.equals(text)) return item;
      }
      fail("Missing " + text + " in " + strings());
      return null;
    }
  }
  static final class Actions implements UiActions {
    final Settings settings;
    final SkinDocument document;
    int changes, checkpoints, closed, reloads;
    String clipboard = "";
    Actions(Settings settings, SkinDocument document) {
      this.settings = settings;
      this.document = document;
    }
    public void changed() {
      settings.validate();
      changes++;
    }
    public void checkpoint() {
      checkpoints++;
    }
    public void close() {
      closed++;
    }
    public String clipboard() {
      return clipboard;
    }
    public void clipboard(String value) {
      clipboard = value;
    }
    public List<String> profiles() {
      return Collections.emptyList();
    }
    public void saveProfile(String value) {}
    public void loadProfile(String value) {}
    public void deleteProfile(String value) {}
    public String exportProfile() {
      return "{}";
    }
    public void importProfile(String value) {}
    public void reset() {}
    public void undo() {}
    public boolean canUndo() {
      return false;
    }
    public List<Cooldown> cooldowns() {
      return Collections.singletonList(
          new Cooldown("a", "Actual pearl", "minecraft:ender_pearl", 3, 10));
    }
    public List<InventorySlot> inventory() {
      return Collections.singletonList(
          new InventorySlot(9, "minecraft:diamond_sword", "Actual sword", 1, 20, 100, true));
    }
    public String gameVersion() {
      return "26.3";
    }
    public String saveStatus() {
      return "saved";
    }
    public SkinDocument skinDocument() {
      return document;
    }
    public void reloadSkin() {
      reloads++;
    }
  }
  static final class Harness {
    final Settings settings = new Settings();
    final Sink canvas = new Sink();
    final Actions actions;
    final MenuController menu;
    double time;
    Harness(String source, boolean enabled) throws Exception {
      settings.menu.animationSpeed = 1000000f;
      settings.skin.enabled = enabled;
      actions = new Actions(settings, NcParser.parse(source));
      menu = new MenuController(settings, actions);
      menu.onOpen();
      render();
    }
    void render() {
      canvas.clear();
      menu.render(canvas, 1000, 700, 0, 0, time++);
      assertEquals(0, canvas.clips);
    }
    void click(String id) {
      click(id, id.startsWith("expand.") ? 1 : 0);
    }
    void click(String id, int button) {
      float[] b = menu.elementBounds(id);
      assertNotNull(b, id);
      assertTrue(menu.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, button));
      menu.mouseReleased(b[0] + b[2] / 2, b[1] + b[3] / 2, button);
      render();
    }
    void style() {
      click("nav-settings");
      click("settings-appearance");
    }
    void player() {
      click("nav-player");
    }
    void openProtect() {
      player();
      search("protect");
      click("expand.protect");
    }
    void search(String value) {
      click("module-search");
      menu.keyPressed('A', Keys.MOD_CONTROL);
      for (int cp : value.codePoints().toArray()) menu.charTyped(cp);
      menu.keyPressed(Keys.ENTER, 0);
      render();
    }
    void seek(String id) {
      for (int i = 0; i < 40; i++) {
        float[] control = menu.elementBounds(id);
        if (control != null && control[2] >= 8 && control[3] >= 18) return;
        float[] scroll = null;
        for (String viewport :
            new String[] {"settings-list", "interface-list", "profiles-list", "modules-list"}) {
          scroll = menu.elementBounds(viewport);
          if (scroll != null) break;
        }
        assertNotNull(scroll, "A scroll viewport must reach " + id);
        assertTrue(
            menu.mouseScrolled(scroll[0] + scroll[2] / 2, scroll[1] + scroll[3] * .65f, -1), id);
        render();
      }
      fail("No fully reachable control: " + id);
    }

    void text(String text) {
      Text value = canvas.find(text);
      assertTrue(menu.mouseClicked(value.x + 3, value.y + 4, 0));
      menu.mouseReleased(0, 0, 0);
      render();
    }
  }
  @Test
  void unifiedSettingsSwitchesPalettesAndPreservesModuleState() throws Exception {
    Harness h = new Harness("theme { }", false);
    assertEquals("dark", h.settings.theme.mode);
    assertNotNull(h.menu.elementBounds("local-profile"));
    h.click("module-toggle.saturation");
    assertTrue(h.settings.saturation.enabled);
    h.click("nav-settings");
    h.click("settings-appearance");
    assertNotNull(h.menu.elementBounds("option.theme-mode.dark"));
    h.click("option.theme-mode.light");
    assertEquals("light", h.settings.theme.mode);
    h.click("option.theme-mode.custom");
    assertEquals("custom", h.settings.theme.mode);
    assertNotNull(h.menu.elementBounds("custom-background"));
    assertTrue(h.settings.saturation.enabled);
    h.click("settings-profiles");
    assertNotNull(h.menu.elementBounds("copy-profile"));
    h.click("settings-interface");
    assertNotNull(h.menu.elementBounds("option.language.en"));
    assertEquals(0, h.actions.closed);
  }
  @Test
  void moduleRowsToggleOnMainClickAndExpandIndependentlyAtFullWidth() throws Exception {
    Harness h = new Harness("theme { }", false);
    h.search("saturation");
    assertFalse(h.settings.saturation.enabled);
    h.click("saturation");
    assertTrue(h.settings.saturation.enabled);
    assertNull(h.menu.elementBounds("saturation.strength"));
    h.click("expand.saturation");
    assertNotNull(h.menu.elementBounds("saturation.strength"));
    h.click("expand.saturation");
    assertNull(h.menu.elementBounds("saturation.strength"));
    assertTrue(h.settings.saturation.enabled);
  }
  @Test
  void themeOnlyFilesStyleTheSameMenuAndKeepRealProtectedControls() throws Exception {
    Harness h = new Harness(
        "theme { accent = #ff4477 }\nstyle element button { background = #123456 }", true);
    assertFalse(h.canvas.strings().contains("default"));
    assertEquals(0xffff4477, h.settings.styleTheme("menu").accent);
    h.openProtect();
    h.click("protect.replacement");
    h.menu.keyPressed('A', Keys.MOD_CONTROL);
    for (int cp : "Игрок 🌙".codePoints().toArray()) h.menu.charTyped(cp);
    assertEquals("Игрок 🌙", h.settings.protect.replacement);
    assertFalse(h.settings.protect.enabled);
  }
  @Test
  void typeClassAndSpecificBindingSelectorsShareOneNativeCascade() throws Exception {
    SkinDocument custom = NcParser.parse(
        "theme { accent = #ff4477 }\nstyle element slider { borderRadius = 2px }\nstyle id "
        + "\"saturation.strength\" { borderRadius = 19px; accent = #44ff77 }");
    SkinDocument doc = DefaultSkin.resolve(custom);
    Settings s = new Settings();
    Actions actions = new Actions(s, doc);
    s.skin.enabled = true;
    MenuController menu = new MenuController(s, actions);
    Sink sink = new Sink();
    s.menu.animationSpeed = 1000000f;
    menu.render(sink, 1000, 700, 0, 0, 0);
    assertEquals(0xff44ff77, doc.theme("saturation.strength", s.theme).accent);
    assertEquals(19, doc.theme("saturation.strength", s.theme).radius);
    assertEquals(0xffff4477, doc.theme("menu", s.theme).accent);
  }
  @Test
  void fullCustomMenuKeepsUnicodeClipboardTypedBindingsAndActions() throws Exception {
    Harness h =
        new Harness("menu { x = 0; y = 0; anchorX = 0; anchorY = 0; width = 300px; height = 220px; "
                    + "padding = 10px; gap = 4px; input (id = \"name\", bind = "
                    + "\"protect.replacement\"); toggle \"Protect\" (id = \"guard\", bind = "
                    + "\"protect.enabled\"); button \"Done\" (id = \"done\", action = \"close\") }",
            true);
    h.click("name");
    h.menu.keyPressed('A', Keys.MOD_CONTROL);
    for (int cp : "Игрок 🌙".codePoints().toArray()) assertTrue(h.menu.charTyped(cp));
    h.menu.keyPressed(Keys.BACKSPACE, 0);
    assertEquals("Игрок ", h.settings.protect.replacement);
    h.click("guard");
    assertTrue(h.settings.protect.enabled);
    h.click("done");
    assertEquals(1, h.actions.closed);
    assertNotNull(h.settings.skinDocument.hud());
  }
  @Test
  void disabledCustomFilesUseTheBundledNcWithoutDiscardingItsDocument() throws Exception {
    Harness h = new Harness("theme { accent = #ff0000 }\nmenu { text \"Replacement\" }", false);
    assertSame(DefaultSkin.document(), h.settings.skinDocument);
    assertFalse(h.canvas.strings().contains("default"));
    assertFalse(h.canvas.strings().contains("Replacement"));
    assertEquals(h.settings.theme.accent, h.settings.styleTheme("menu").accent);
  }
  @Test
  void customPaletteEditsPreserveProtectedName() throws Exception {
    Harness h = new Harness("theme { }", false);
    h.style();
    h.click("option.theme-mode.custom");
    h.click("custom-text");
    h.click("color-hex");
    h.menu.keyPressed('A', Keys.MOD_CONTROL);
    for (char c : "#112233FF".toCharArray()) h.menu.charTyped(c);
    h.render();
    assertEquals(0xff112233, h.settings.theme.customText);
    assertEquals("NightProtect", h.settings.protect.replacement);
  }
  @Test
  void externalCloseClearsFocusAndComposedDocumentsAreNotLayeredAgain() throws Exception {
    Harness h = new Harness("theme { }", true);
    h.openProtect();
    h.click("protect.replacement");
    assertTrue(h.menu.isTextFocused());
    h.menu.onClose();
    assertFalse(h.menu.isTextFocused());
    assertFalse(h.menu.charTyped('X'));
    SkinDocument resolved = DefaultSkin.resolve(h.actions.document);
    assertSame(resolved, DefaultSkin.resolve(resolved));
  }
  @Test
  void inlineChoiceUsesTheDataOptionsAndClosesOnlyItsOwnScope() throws Exception {
    Harness h = new Harness(
        "menu { x=0px; y=0px; anchorX=0; anchorY=0; width=300px; height=180px; layout=column; "
        + "padding=10px; choice \"Mode\" "
        + "(id=\"mode\",bind=\"hitColor.mode\",options=\"entity|hitbox\",option-labels=\"Entity|"
        + "Hitbox\"); toggle \"Enabled\" (id=\"toggle\",bind=\"hitColor.enabled\") }",
        true);
    float initial = h.menu.elementBounds("toggle")[1];
    h.click("mode");
    assertTrue(h.canvas.strings().contains("Hitbox"));
    assertTrue(h.menu.elementBounds("toggle")[1] > initial);
    h.text("Hitbox");
    assertEquals("hitbox", h.settings.hitColor.mode);
    assertEquals(initial, h.menu.elementBounds("toggle")[1], .001f);
    assertFalse(h.settings.hitColor.enabled);
  }
  @Test
  void localPresentationScopesDoNotLeakBetweenElements() throws Exception {
    Harness h =
        new Harness("state { expanded=false } menu { x=0px; y=0px; anchorX=0; anchorY=0; "
                    + "width=300px; height=180px; layout=column; panel(scope=\"one\") { button "
                    + "\"First\"(id=\"first\",action=\"local-toggle:expanded\"); text \"First "
                    + "detail\"(if=\"local.expanded\") } panel(scope=\"two\") { button "
                    + "\"Second\"(id=\"second\",action=\"local-toggle:expanded\"); text \"Second "
                    + "detail\"(if=\"local.expanded\") } }",
            true);
    h.click("first");
    assertTrue(h.canvas.strings().contains("First detail"));
    assertFalse(h.canvas.strings().contains("Second detail"));
    h.click("second");
    assertTrue(h.canvas.strings().contains("First detail"));
    assertTrue(h.canvas.strings().contains("Second detail"));
  }

  @Test
  void inlineColorParticipatesInLayoutAndChangesItsOwnRgbaBinding() throws Exception {
    Harness h = new Harness("menu { x=0px; y=0px; anchorX=0; anchorY=0; width=300px; height=330px; "
                            + "layout=column; padding=10px; color \"Hit colour\" "
                            + "(id=\"hit-colour\",bind=\"hitColor.color\",visual=\"inline\"); "
                            + "toggle \"Enabled\" (id=\"toggle\",bind=\"hitColor.enabled\") }",
        true);
    int accent = h.settings.theme.accent;
    float initial = h.menu.elementBounds("toggle")[1];
    h.click("hit-colour");
    assertEquals(initial + 137 * h.settings.menu.scale, h.menu.elementBounds("toggle")[1], .01f);
    assertNull(h.menu.elementBounds("color-plane"));
    float[] plane = h.menu.elementBounds("hit-colour.plane");
    assertNotNull(plane);
    assertTrue(h.menu.mouseClicked(plane[0] + plane[2] * .5f, plane[1] + plane[3] * .25f, 0));
    h.menu.mouseReleased(0, 0, 0);
    h.render();
    float[] hue = h.menu.elementBounds("hit-colour.hue");
    assertTrue(h.menu.mouseClicked(hue[0] + hue[2] * .5f, hue[1] + hue[3] * .5f, 0));
    h.menu.mouseReleased(0, 0, 0);
    h.render();
    float[] alpha = h.menu.elementBounds("hit-colour.alpha");
    assertTrue(h.menu.mouseClicked(alpha[0] + alpha[2] * .25f, alpha[1] + alpha[3] * .5f, 0));
    h.menu.mouseReleased(0, 0, 0);
    h.render();
    assertEquals(0x4060bfbf, h.settings.hitColor.color);
    assertEquals(accent, h.settings.theme.accent);
    h.click("hit-colour.copy");
    assertEquals("#60BFBF40", h.actions.clipboard);
    h.click("hit-colour");
    assertEquals(initial, h.menu.elementBounds("toggle")[1], .01f);
  }

  @Test
  void masonryResetsColumnAssignmentsWhenSwitchingCategoryPages() throws Exception {
    Harness h = new Harness("theme { }", false);
    h.render();
    h.click("nav-camera");
    float[] crosshair = h.menu.elementBounds("card.crosshair");
    float[] zoom = h.menu.elementBounds("card.zoom");
    float[] freelook = h.menu.elementBounds("card.freelook");
    float[] aspect = h.menu.elementBounds("card.aspectRatio");
    float[] swing = h.menu.elementBounds("card.swing");
    assertNotNull(crosshair);
    assertNotNull(zoom);
    assertNotNull(freelook);
    assertNotNull(aspect);
    assertNotNull(swing);
    assertEquals(crosshair[0], freelook[0], 0.5f, "Freelook should be in column 0");
    assertEquals(crosshair[0], swing[0], 0.5f, "Swing Animation should be in column 0, not skipping a cell");
    assertEquals(zoom[0], aspect[0], 0.5f, "Aspect Ratio should be in column 1");
    assertTrue(zoom[0] > crosshair[0] + crosshair[2] * 0.5f, "Column 1 must be to the right of column 0");
    assertEquals(crosshair[1], zoom[1], 0.5f, "Row 0 cards should align");
    assertEquals(freelook[1], aspect[1], 0.5f, "Row 1 cards should align");
    assertTrue(swing[1] > freelook[1], "Row 2 card should sit directly below Row 1 left card");
  }

  @Test
  void masonryStaysBalancedAcrossTabSwitchAndReopenAfterExpandingTallCard() throws Exception {
    Harness h = new Harness("theme { }", false);
    h.render();
    h.click("nav-player");

    h.click("expand.hwhelper");
    assertEquals(Boolean.TRUE, h.settings.interfaceState.expandedPanels.get("module.hwhelper"));

    h.click("nav-camera");
    h.click("nav-player");
    h.menu.onClose();
    h.menu.onOpen();
    h.render();

    h.seek("card.tweaksy");
    float[] expandedTweaksy = h.menu.elementBounds("card.tweaksy");
    assertNotNull(expandedTweaksy, "Bottom card in expanded column 1 must be scrollable");
    h.menu.onClose();
    h.menu.onOpen();
    h.render();

    h.click("expand.hwhelper");
    assertEquals(Boolean.FALSE, h.settings.interfaceState.expandedPanels.get("module.hwhelper"));

    float[] protect = h.menu.elementBounds("card.protect");
    float[] hwhelper = h.menu.elementBounds("card.hwhelper");
    float[] safeCtLeave = h.menu.elementBounds("card.safeCtLeave");
    float[] itemScroller = h.menu.elementBounds("card.itemScroller");
    float[] tapeMouse = h.menu.elementBounds("card.tapeMouse");
    float[] soundControl = h.menu.elementBounds("card.soundControl");
    assertNotNull(protect);
    assertNotNull(hwhelper);
    assertNotNull(safeCtLeave);
    assertNotNull(itemScroller);
    assertNotNull(tapeMouse);
    assertNotNull(soundControl);

    assertTrue(hwhelper[0] > protect[0] + protect[2] * 0.5f, "HolyWorld Helper stays in column 1");
    assertEquals(protect[0], safeCtLeave[0], 0.5f, "Safe CT Leave must sit in column 0");
    assertEquals(protect[0], tapeMouse[0], 0.5f, "TapeMouse must sit in column 0");
    assertEquals(hwhelper[0], itemScroller[0], 0.5f, "Item Scroller must sit in column 1");
    assertEquals(hwhelper[0], soundControl[0], 0.5f, "Sound Control must sit in column 1");
    assertEquals(protect[1], hwhelper[1], 0.5f, "Row 0 cards must align");
    assertEquals(safeCtLeave[1], itemScroller[1], 0.5f, "Row 1 cards must align");
    assertEquals(tapeMouse[1], soundControl[1], 0.5f, "Row 2 cards must align");
  }
}
