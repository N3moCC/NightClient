package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiRevisionTest {
  private static SkinView view(String source, Settings settings, SkinViewTest.Actions actions)
      throws Exception {
    SkinDocument document = NcParser.parse(source);
    new SkinValidator().validate(document);
    return new SkinView(document, settings, actions);
  }
  private static float[] center(SkinView view, String id) {
    float[] b = view.elementBounds(id);
    assertNotNull(b, id);
    return new float[] {b[0] + b[2] / 2, b[1] + b[3] / 2};
  }
  private static void click(SkinView view, String id) {
    float[] p = center(view, id);
    int button = id.startsWith("expand.") ? 1 : 0;
    assertTrue(view.mouseClicked(p[0], p[1], button));
    view.mouseReleased(p[0], p[1], button);
  }

  @Test
  void hsvKeepsHueWhileEditingBlackAndGreyAndRoundTripsAlpha() {
    ColorPickerModel model = new ColorPickerModel();
    model.sync(0x8033ccff);
    assertEquals(0x8033ccff, model.argb());
    float hue = model.hue;
    model.value = 0;
    assertEquals(0x80000000, model.argb());
    model.sync(0x80000000);
    assertEquals(hue, model.hue);
    model.value = 1;
    model.saturation = 0;
    assertEquals(0x80ffffff, model.argb());
    model.sync(0x80ffffff);
    assertEquals(hue, model.hue);
    model.saturation = 1;
    assertNotEquals(0x80ffffff, model.argb());
    assertEquals(0x80, model.argb() >>> 24);
    assertEquals(0xffff0000, ColorPickerModel.rgb(1, 1, 1, 1));
    assertEquals(0xff00ffff, ColorPickerModel.rgb(.5f, 1, 1, 1));
  }

  @Test
  void graphicalPickerChangesActualRgbaAndHexWithOneCheckpointPerGesture() throws Exception {
    Settings s = new Settings();
    s.theme.accent = 0xff00ff00;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView v = view("menu { color \"Accent\" (id=accent, bind=theme.accent) }", s, actions);
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, 0);
    click(v, "accent");
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, .2);
    float[] plane = v.elementBounds("color-plane");
    assertNotNull(plane);
    assertNotNull(v.elementBounds("color-hue"));
    assertNotNull(v.elementBounds("color-alpha"));
    assertTrue(v.mouseClicked(plane[0] + plane[2] / 2, plane[1] + plane[3] / 2, 0));
    assertTrue(v.mouseDragged(plane[0] + plane[2], plane[1], 0, 0, 0));
    v.mouseReleased(0, 0, 0);
    assertEquals(0xff00ff00, s.theme.accent);
    assertEquals(1, actions.checkpoints);
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, .3);
    float[] hue = v.elementBounds("color-hue");
    v.mouseClicked(hue[0] + hue[2] / 2, hue[1] + hue[3] / 2, 0);
    v.mouseReleased(0, 0, 0);
    assertEquals(0xff00ffff, s.theme.accent);
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, .4);
    float[] alpha = v.elementBounds("color-alpha");
    v.mouseClicked(alpha[0] + alpha[2] / 4, alpha[1] + alpha[3] / 2, 0);
    v.mouseReleased(0, 0, 0);
    assertEquals(64, s.theme.accent >>> 24);
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, .5);
    click(v, "color-hex");
    assertTrue(v.isTextFocused());
    v.keyPressed(65, 0, 2);
    for (char c : "#11223380".toCharArray()) v.charTyped(c, 0);
    assertEquals(0x80112233, s.theme.accent);
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, .6);
    click(v, "color-done");
    v.renderMenu(new SkinViewTest.Sink(), 500, 460, 0, 0, .7);
    assertNull(v.elementBounds("color-plane"));
  }

  @Test
  void hueAndAlphaSupportKeyboardAndFallbackPickerSharesActualBrushBinding() throws Exception {
    Settings s = new Settings();
    SkinView v = view(
        "menu { color \"Brush\" (id=brush, bind=ui.brushColor) }", s, new SkinViewTest.Actions());
    v.renderMenu(new SkinViewTest.Sink(), 320, 200, 0, 0, 0);
    click(v, "brush");
    v.renderMenu(new SkinViewTest.Sink(), 320, 200, 0, 0, .1);
    float[] hue = v.elementBounds("color-hue");
    assertNotNull(hue);
    assertTrue(hue[1] + hue[3] <= 200);
    click(v, "color-hue");
    assertTrue(v.keyPressed(262, 0, 0));
    v.renderMenu(new SkinViewTest.Sink(), 320, 200, 0, 0, .2);
    click(v, "color-alpha");
    assertTrue(v.keyPressed(263, 0, 1));
    assertTrue(v.keyPressed(256, 0, 0));
    v.renderMenu(new SkinViewTest.Sink(), 320, 200, 0, 0, .3);
    assertNull(v.elementBounds("color-alpha"));
  }

  static class ClipSink extends SkinViewTest.Sink {
    final List<float[]> stack = new ArrayList<float[]>();
    final List<Float> textClipWidths = new ArrayList<Float>();
    @Override
    public void pushClip(float x, float y, float w, float h) {
      super.pushClip(x, y, w, h);
      float left = x, top = y, right = x + w, bottom = y + h;
      if (!stack.isEmpty()) {
        float[] p = stack.get(stack.size() - 1);
        left = Math.max(left, p[0]);
        top = Math.max(top, p[1]);
        right = Math.min(right, p[2]);
        bottom = Math.min(bottom, p[3]);
      }
      stack.add(new float[] {left, top, right, bottom});
    }
    @Override
    public void popClip() {
      super.popClip();
      stack.remove(stack.size() - 1);
    }
    @Override
    public void text(String text, float x, float y, float size, int color, boolean bold) {
      textClipWidths.add(stack.isEmpty()
              ? Float.POSITIVE_INFINITY
              : stack.get(stack.size() - 1)[2] - stack.get(stack.size() - 1)[0]);
      super.text(text, x, y, size, color, bold);
    }
  }
  @Test
  void ordinaryContainersDoNotClipTheirAnimatedDescendantsAndInputUsesTheSamePose()
      throws Exception {
    Settings s = new Settings();
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView v =
        view("style element button { hoverScale=1.3; enterOffsetX=20px; enterOpacity=100%; "
             + "enterDuration=1s } menu { column { width=60px; height=30px; button \"Wide\" "
             + "(id=wide, action=close) { width=100px; height=30px } } }",
            s, actions);
    v.resetMotion();
    ClipSink sink = new ClipSink();
    v.renderMenu(sink, 240, 180, 0, 0, .2);
    assertEquals(1, sink.textClipWidths.size());
    assertEquals(240, sink.textClipWidths.get(0));
    assertEquals(0, sink.clips);
    float[] b = v.elementBounds("wide");
    assertNotNull(b);
    assertTrue(b[0] >= 19);
    assertTrue(v.mouseClicked(b[0] + b[2] - 2, b[1] + b[3] / 2, 0));
    assertEquals(1, actions.closed);
  }
  @Test
  void explicitOverflowClipsAndBlocksInputsOutsideTheAnimatedAncestor() throws Exception {
    Settings s = new Settings();
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView v = view("menu { column { width=60px; height=30px; overflow=hidden; button \"Wide\" "
                      + "(id=wide, action=close) { width=100px; height=30px } } }",
        s, actions);
    ClipSink sink = new ClipSink();
    v.renderMenu(sink, 240, 180, 0, 0, 0);
    assertEquals(60, sink.textClipWidths.get(0));
    assertFalse(v.mouseClicked(80, 15, 0));
    assertEquals(0, actions.closed);
    assertEquals(0, sink.clips);
  }

  @Test
  void hudModulesToggleAndExpandIndependentlyWhileEditorKeepsItsGeometryPolicy() throws Exception {
    Settings s = new Settings();
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView v = new SkinView(DefaultSkin.document(), s, actions);
    s.menu.animationSpeed = 1000000f;
    v.page("hud");
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    click(v, "module-search");
    for (char c : "armor".toCharArray()) v.charTyped(c, 0);
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .1);
    assertNotNull(v.elementBounds("hud-armor"));
    assertNotNull(v.elementBounds("edit-hud"));
    assertNull(v.elementBounds("armor.horizontal"));
    click(v, "hud-armor");
    assertTrue(s.armor.enabled);
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    click(v, "expand.armor");
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .3);
    assertNotNull(v.elementBounds("armor.style"));
    assertTrue(s.armor.enabled);
    v.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public Map<String, Object> context() {
        Map<String, Object> c = new HashMap<String, Object>();
        c.put("editorTarget", "armor");
        return c;
      }
    });
    v.beginFrame(0, 0, 0);
    Map<String, Object> context = new HashMap<String, Object>();
    context.put("editorInspectorX", 20);
    context.put("editorInspectorY", 20);
    context.put("editorInspectorWidth", 280);
    context.put("editorInspectorHeight", 320);
    context.put("editorSelectedLabel", "Armor");
    context.put("editorScaleBind", "armor.scale");
    context.put("editorScaleMin", .1);
    context.put("editorScaleMax", 3);
    context.put("editorScaleStep", .01);
    context.put("editorScaleLabel", "Scale");
    context.put("editorSnapLabel", "Snap");
    context.put("editorStyleLabel", "Style");
    v.renderComponent("editor.inspector", new SkinViewTest.Sink(), 0, 0, 1000, 700, 0, context);
    assertNotNull(v.elementBounds("armor.horizontal"));
    assertNull(v.elementBounds("armor.offsetX"));
    assertNull(v.elementBounds("armor.width"));
    assertNull(v.elementBounds("armor.height"));
  }

  @Test
  void themePageOffersPalettesAndGlassWithoutRetiredMaterialsOrElementEffects() {
    UiCatalog catalog = new UiCatalog(new Settings());
    for (boolean detailed : new boolean[] {false, true}) {
      List<UiCatalog.Control> controls = catalog.styleControls(detailed);
      for (String bind : new String[] {"theme.mode", "theme.customBackground",
               "theme.customSurface", "theme.customRaised", "theme.customText",
               "theme.backgroundOpacity", "theme.effects.backgroundBlur", "theme.blurQuality"})
        assertTrue(controls.stream().anyMatch(c -> c.bind.equals(bind)), bind);
      assertFalse(controls.stream().anyMatch(c -> c.bind.equals("skin.preset")));
    }
    for (String retired : new String[] {"theme.material", "theme.effects.accentLight",
             "theme.effects.glow", "theme.effects.shadows"})
      assertNull(catalog.control(retired));
    assertEquals("dark|light|custom", catalog.control("theme.mode").options);
    for (UiCatalog.Control c : catalog.editorControls("armor"))
      assertFalse(c.bind.matches("armor.(offsetX|offsetY|width|height|scale)"));
  }
  @Test
  void pickerAnchorsNextToItsControlAndDismissesOnOutsideClick() throws Exception {
    Settings s = new Settings();
    SkinView v = view("menu { color \"Accent\" (id=accent,bind=theme.accent) { "
                      + "position=absolute;x=40px;y=30px;width=100px;height=34px } }",
        s, new SkinViewTest.Actions());
    v.renderMenu(new SkinViewTest.Sink(), 900, 600, 0, 0, 0);
    click(v, "accent");
    v.renderMenu(new SkinViewTest.Sink(), 900, 600, 0, 0, .1);
    float[] plane = v.elementBounds("color-plane");
    assertNotNull(plane);
    assertTrue(plane[0] >= 90 && plane[0] < 210, "Picker opens beside the clicked control");
    assertTrue(plane[1] >= 40 && plane[1] < 140,
        "Picker stays beside the pointer instead of leaving the viewport");
    assertTrue(v.mouseClicked(850, 550, 0));
    v.renderMenu(new SkinViewTest.Sink(), 900, 600, 0, 0, .2);
    assertNull(v.elementBounds("color-plane"));
  }
}
