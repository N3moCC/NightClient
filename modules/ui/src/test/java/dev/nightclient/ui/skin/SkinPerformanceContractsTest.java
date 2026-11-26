package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinPerformanceContractsTest {
  @Test
  void deepContextFlatteningEnumeratesTheRootOncePerSnapshotPass() {
    final int[] reads = {0};
    Map<String, Object> root = new HashMap<String, Object>() {
      @Override
      public java.util.Set<Map.Entry<String, Object>> entrySet() {
        reads[0]++;
        return super.entrySet();
      }
    };
    root.put("base", 1);
    root.put("shadow", -1);
    Map<String, Object> nested = root;
    for (int i = 0; i < 32; i++) {
      nested = SkinBindings.childContext(nested);
      nested.put("layer" + i, i);
      nested.put("shadow", i);
    }
    Map<String, Object> snapshot = new HashMap<String, Object>(nested);
    assertEquals(1, snapshot.get("base"));
    assertEquals(31, snapshot.get("shadow"));
    assertEquals(34, snapshot.size());
    assertTrue(reads[0] <= 2,
        "A snapshot may request size and entries, but must not recursively re-enumerate its root: "
            + reads[0]);
    assertEquals(-1, root.get("shadow"));
    assertFalse(root.containsKey("layer31"));
  }
  @Test
  void settingsOnlyVariableCacheExpiresEveryFrameAndRespectsLocalOverrides() {
    Settings settings = new Settings();
    settings.theme.accent = 0xff123456;
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--tone", "theme.accent");
    style.put("--derived", "vars.tone + 1");
    Map<String, Object> first =
        bindings.scope(SkinBindings.childContext(new HashMap<String, Object>()), style);
    Map<String, Object> second =
        bindings.scope(SkinBindings.childContext(new HashMap<String, Object>()), style);
    bindings.measurementCanvas(null);
    assertEquals((double) settings.theme.accent + 1,
        ((Number) bindings.get("vars.derived", first)).doubleValue());
    assertEquals(bindings.get("vars.derived", first), bindings.get("vars.derived", second));
    settings.theme.accent = 0xff654321;
    bindings.measurementCanvas(null);
    bindings.invalidateVariables(first);
    assertEquals((double) settings.theme.accent + 1,
        ((Number) bindings.get("vars.derived", first)).doubleValue());
    Map<String, Object> palette = new HashMap<String, Object>();
    palette.put("accent", 42);
    Map<String, Object> override = SkinBindings.childContext(new HashMap<String, Object>());
    override.put("theme", palette);
    bindings.scope(override, style);
    assertEquals(43d, ((Number) bindings.get("vars.derived", override)).doubleValue(),
        "A context theme must not read another scope's settings-only result");
  }
  @Test
  void localLayoutAndRepeatedRowVariablesNeverReuseAnotherNodesValues() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--size", "boxWidth + row.amount");
    Map<String, Object> parent = new HashMap<String, Object>();
    parent.put("boxWidth", 10);
    parent.put("row", java.util.Collections.singletonMap("amount", 2));
    Map<String, Object> first = bindings.scope(SkinBindings.childContext(parent), style),
                        second = bindings.scope(SkinBindings.childContext(parent), style);
    first.put("boxWidth", 20);
    second.put("boxWidth", 30);
    assertEquals(22d, bindings.get("vars.size", first));
    assertEquals(32d, bindings.get("vars.size", second));
    first.put("boxWidth", 40);
    bindings.invalidateVariables(first);
    assertEquals(42d, bindings.get("vars.size", first));
    assertEquals(10, parent.get("boxWidth"));
    assertEquals(32d, bindings.get("vars.size", second));
  }
  @Test
  void compiledFieldBindingsReadReplacementSettingsObjects() {
    Settings settings = new Settings();
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, Object> context = new HashMap<String, Object>();
    settings.saturation.strength = .4f;
    assertEquals(.4f, bindings.get("saturation.strength", context));
    settings.saturation = new Settings.Saturation();
    settings.saturation.strength = 1.7f;
    assertEquals(1.7f, bindings.get("saturation.strength", context));
    context.put("row", new dev.nightclient.api.Cooldown("one", "first", "minecraft:stone", 1, 2));
    assertEquals("first", bindings.get("row.name", context));
    context.put("row", new dev.nightclient.api.TntView(2, 0, 0, 1, 3, false));
    assertNull(bindings.get("row.name", context));
    assertEquals(3f, bindings.get("row.distance", context));
    assertNull(bindings.get("row.getClass", context));
    assertNull(bindings.get("class.classLoader", context));
  }
  @Test
  void boundedLiteralTextDoesNotAllocateAnotherString() {
    SkinBindings bindings = new SkinBindings(new Settings());
    String literal = new String("A literal NC property");
    assertSame(literal, bindings.text(literal, new HashMap<String, Object>()));
    char[] longText = new char[2200];
    java.util.Arrays.fill(longText, 'x');
    assertEquals(2048, bindings.text(new String(longText), new HashMap<String, Object>()).length());
  }
  @Test
  void mappedSliderPointerKeyboardAndReleasePreviewUseDisplayDomain() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.theme.backgroundOpacity = .75f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = new SkinView(
        NcParser.parse("menu { slider "
                        + "(id=transparency,bind=theme.backgroundOpacity,min=0,max=50,step=5,value-"
                        + "scale=-100,value-offset=100,commit=release) { width=100px; height=20px; "
                        + "padding=0px }; text '{{theme.backgroundOpacity}}' }"),
        settings, actions);
    render(view);
    float[] box = view.elementBounds("transparency");
    assertNotNull(box);
    assertTrue(view.mouseClicked(box[0] + box[2] * .8f, box[1] + box[3] / 2, 0));
    assertEquals(.75f, settings.theme.backgroundOpacity, .0001f,
        "Release commits retain the original stored value while dragging");
    view.mouseReleased(0, 0, 0);
    assertEquals(.6f, settings.theme.backgroundOpacity, .0001f);
    render(view);
    assertTrue(view.keyPressed(263, 0, 0));
    assertEquals(.65f, settings.theme.backgroundOpacity, .0001f);
    assertTrue(view.keyPressed(268, 0, 0));
    assertEquals(1, settings.theme.backgroundOpacity, .0001f);
    assertTrue(view.keyPressed(269, 0, 0));
    assertEquals(.5f, settings.theme.backgroundOpacity, .0001f);
  }
  @Test
  void mountedSliderExposesMappedValueAndInvertibleAttributes() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.theme.backgroundOpacity = .75f;
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { slider "
            + "(bind=theme.backgroundOpacity,min=0,max=50,step=5,value-scale=-100,value-offset=100)"
            + " } component 'element.slider' { column { text '{{value|integer}}'; slider "
            + "(id=track,visual=none,bind='{{bind}}',min='{{min}}',max='{{max}}',step='{{step}}',"
            + "value-scale='{{valueScale}}',value-offset='{{valueOffset}}') { width=100px; "
            + "height=20px; padding=0px } } }"),
        settings, new SkinViewTest.Actions());
    assertTrue(render(view).strings().contains("25"));
    float[] b = view.elementBounds("track");
    assertNotNull(b);
    assertTrue(view.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, 0));
    assertTrue(view.mouseDragged(b[0] + b[2], b[1] + b[3] / 2, 0, 1, 0));
    view.mouseReleased(0, 0, 0);
    assertEquals(.5f, settings.theme.backgroundOpacity, .0001f);
    assertTrue(render(view).strings().contains("50"));
  }
  @Test
  void ordinaryPaintResolvesDynamicStyleAgainstItsFinalOwnDimensions() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView view = new SkinView(
        NcParser.parse("menu { width=100px; height=20px; rect { width=50px; height=10px; "
                        + "fill=\"{{=boxWidth == 50 ? '#abcdef' : '#123456'}}\" } }"),
        settings, new SkinViewTest.Actions());
    final java.util.List<Integer> colors = new java.util.ArrayList<Integer>();
    SkinViewTest.Sink sink = new SkinViewTest.Sink() {
      @Override
      public void roundedRect(float x, float y, float w, float h, float r, int color) {
        super.roundedRect(x, y, w, h, r, color);
        if (w == 50 && h == 10) colors.add(color);
      }
    };
    view.renderMenu(sink, 500, 300, 400, 200, 1);
    assertTrue(colors.contains(0xffabcdef), colors.toString());
  }
  private static SkinViewTest.Sink render(SkinView view) {
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    assertTrue(view.renderMenu(sink, 500, 300, 0, 0, 1));
    return sink;
  }
}
