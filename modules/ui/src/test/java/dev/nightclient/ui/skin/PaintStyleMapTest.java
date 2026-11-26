package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaintStyleMapTest {
  @Test
  void projectionPreservesPresentationPrecedenceAndLayoutOnlyProperties() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = new HashMap<String, Object>();
    context.put("value", "#123456");
    bindings.frameContext(context);
    Map<String, String> base = map("height", "24px", "width", "90px", "color", "#ffffff");
    PaintStyleMap.Plan node = new PaintStyleMap.Plan(map("color", "{{value}}", "radius", "4px"));
    PaintStyleMap.Plan presentation = new PaintStyleMap.Plan(map("radius", "8px", "shadow", "0"));
    PaintStyleMap style = new PaintStyleMap();
    style.reset(base, node, presentation, bindings, context);
    assertEquals("24px", style.get("height"));
    assertEquals("#123456", style.get("color"));
    assertEquals("8px", style.get("radius"));
    assertTrue(style.containsKey("shadow"));
    assertFalse(style.containsKey("missing"));
    assertEquals(
        map("height", "24px", "width", "90px", "color", "#123456", "radius", "8px", "shadow", "0"),
        new HashMap<String, String>(style));
    assertThrows(UnsupportedOperationException.class, () -> style.put("color", "#000000"));
  }
  @Test
  void unreadBindingsStayLazyAndTheNextPaintUsesItsCurrentScope() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> first = SkinBindings.lazyContext();
    final int[] reads = {0};
    SkinBindings.lazy(first, "optional", () -> {
      reads[0]++;
      return "first";
    });
    bindings.frameContext(first);
    PaintStyleMap.Plan plan = new PaintStyleMap.Plan(map("color", "{{optional}}", "radius", "4px"));
    PaintStyleMap style = new PaintStyleMap();
    style.reset(Collections.<String, String>emptyMap(), plan, null, bindings, first);
    assertTrue(style.containsKey("color"));
    assertEquals("4px", style.get("radius"));
    assertEquals(0, reads[0]);
    assertEquals("first", style.get("color"));
    assertEquals("first", style.get("color"));
    assertEquals(1, reads[0]);
    Map<String, Object> next = new HashMap<String, Object>();
    next.put("optional", "second");
    bindings.frameContext(next);
    style.reset(Collections.<String, String>emptyMap(), plan, null, bindings, next);
    assertEquals("second", style.get("color"));
    style.reset(map("color", "base"), null, null, bindings, next);
    assertEquals("base", style.get("color"));
    assertFalse(style.containsKey("radius"));
  }
  @Test
  void nullOverridesAndDifferentRecipesDoNotLeakPreviouslyResolvedValues() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = new HashMap<String, Object>();
    context.put("value", "live");
    bindings.frameContext(context);
    PaintStyleMap.Plan first = new PaintStyleMap.Plan(map("a", "{{value}}", "b", null));
    PaintStyleMap.Plan second = new PaintStyleMap.Plan(map("b", "{{value}}", "a", "fixed"));
    PaintStyleMap style = new PaintStyleMap();
    style.reset(map("b", "base"), first, null, bindings, context);
    assertEquals("live", style.get("a"));
    assertNull(style.get("b"));
    assertTrue(style.containsKey("b"));
    style.reset(Collections.<String, String>emptyMap(), second, null, bindings, context);
    assertEquals("live", style.get("b"));
    assertEquals("fixed", style.get("a"));
  }
  private static Map<String, String> map(String... values) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
    return result;
  }
}
