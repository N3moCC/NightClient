package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinBindingContextCacheTest {
  private static final class Root extends HashMap<String, Object> {
    int reads;
    @Override
    public Object get(Object key) {
      reads++;
      return super.get(key);
    }
  }
  @Test
  void DeepLookupMemoizesOnlyTheRequestingScopeAndPreservesNullAndMissing() {
    Root root = new Root();
    Object value = new Object();
    root.put("value", value);
    root.put("nullable", null);
    Map<String, Object> middle = SkinBindings.childContext(root), leaf = middle;
    for (int depth = 0; depth < 128; depth++) leaf = SkinBindings.childContext(leaf);
    assertSame(value, leaf.get("value"));
    assertEquals(1, root.reads);
    assertSame(value, leaf.get("value"));
    assertTrue(leaf.containsKey("value"));
    assertEquals(1, root.reads);
    assertSame(value, middle.get("value"));
    assertEquals(2, root.reads, "A leaf read must not populate ancestor caches");
    assertNull(leaf.get("nullable"));
    assertTrue(leaf.containsKey("nullable"));
    assertEquals(3, root.reads);
    assertNull(leaf.get("missing"));
    assertFalse(leaf.containsKey("missing"));
    assertEquals(4, root.reads);
    assertSame(value, leaf.put("value", null));
    assertNull(leaf.get("value"));
    assertTrue(leaf.containsKey("value"));
    assertSame(value, middle.get("value"));
  }
  @Test
  void LateAncestorDimensionsBypassMissingAndNullCachesAndRespectOwnShadowing() {
    Map<String, Object> root = new HashMap<String, Object>();
    Map<String, Object> parent = SkinBindings.childContext(root),
                        middle = SkinBindings.childContext(parent),
                        leaf = SkinBindings.childContext(middle);
    assertNull(leaf.get("boxWidth"));
    assertFalse(leaf.containsKey("boxWidth"));
    parent.put("boxWidth", 120);
    parent.put("boxHeight", 40);
    assertEquals(120, leaf.get("boxWidth"));
    assertEquals(40, leaf.get("boxHeight"));
    parent.put("boxWidth", 80);
    assertEquals(80, leaf.get("boxWidth"));
    middle.put("boxWidth", null);
    assertNull(leaf.get("boxWidth"));
    assertTrue(leaf.containsKey("boxWidth"));
    middle.put("boxWidth", 60);
    assertEquals(60, leaf.get("boxWidth"));
    leaf.put("boxWidth", 25);
    middle.put("boxWidth", 50);
    assertEquals(25, leaf.get("boxWidth"));
    assertEquals(80, parent.get("boxWidth"));
  }
  @Test
  void CompiledDependencyFlagsFollowNestedVariablesAndEachScopesRecipe() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    frame.put("width", 200);
    bindings.frameContext(frame);
    Map<String, Object> parent = SkinBindings.childContext(frame);
    parent.put("boxWidth", 100);
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--half", "boxWidth/2");
    style.put("--size", "vars.half/2");
    style.put("--constant", "width+17");
    Map<String, Object> child =
        bindings.scope(SkinBindings.childContext(SkinBindings.childContext(parent)), style);
    assertEquals("25.0", bindings.text("{{=vars.size}}", child));
    String constant = bindings.text("{{=vars.constant}}", child);
    Map<String, Object> fixed = bindings.scope(
        SkinBindings.childContext(frame), Collections.singletonMap("--size", "width/4"));
    String fixedText = bindings.text("{{=vars.size}}", fixed);
    assertEquals("50.0", fixedText);
    parent.put("boxWidth", 80);
    assertEquals("20.0", bindings.text("{{=vars.size}}", child));
    assertSame(constant, bindings.text("{{=vars.constant}}", child));
    fixed.put("boxWidth", 8);
    bindings.dimensionsChanged(fixed);
    assertSame(fixedText, bindings.text("{{=vars.size}}", fixed));
    child.put("value", 3);
    bindings.scope(child, Collections.singletonMap("--size", "value*2"));
    assertEquals("6.0", bindings.text("{{=vars.size}}", child));
    child.put("value", 4);
    assertEquals("8.0", bindings.text("{{=vars.size}}", child));
  }
  @Test
  void ExplicitVariableInvalidationDropsTheSharedRecipeResult() {
    Settings settings = new Settings();
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, Object> frame = new HashMap<String, Object>();
    bindings.frameContext(frame);
    Map<String, String> style = Collections.singletonMap("--strength", "saturation.strength*2");
    Map<String, Object> node = bindings.scope(SkinBindings.childContext(frame), style);
    assertEquals(2.3, Double.parseDouble(bindings.text("{{=vars.strength}}", node)), .00001);
    settings.saturation.strength = .8f;
    bindings.invalidateVariables(node);
    assertEquals(1.6, Double.parseDouble(bindings.text("{{=vars.strength}}", node)), .00001);
    Map<String, Object> next = bindings.scope(SkinBindings.childContext(frame), style);
    assertEquals(1.6, Double.parseDouble(bindings.text("{{=vars.strength}}", next)), .00001);
  }
}
