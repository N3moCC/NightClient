package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinCompiledBindingSlotsTest {
  @Test
  void reusableScopeDropsOldParentOverridesDimensionsAndTemplateResults() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> first = new HashMap<String, Object>();
    first.put("value", 3);
    first.put("boxWidth", 100);
    Map<String, String> style = Collections.singletonMap("--half", "boxWidth/2+value");
    Map<String, Object> context = bindings.scope(SkinBindings.childContext(first), style);
    context.put("localOnly", "old");
    context.put("boxWidth", 80);
    assertEquals("43.0 / old", bindings.text("{{=vars.half}} / {{localOnly}}", context));
    Map<String, Object> second = new HashMap<String, Object>();
    second.put("value", 7);
    second.put("boxWidth", 20);
    assertSame(context, SkinBindings.resetContext(context, second));
    bindings.scope(context, style);
    assertFalse(context.containsKey("localOnly"));
    assertEquals(20, context.get("boxWidth"));
    assertEquals("17.0 / ", bindings.text("{{=vars.half}} / {{localOnly}}", context));
    context.put("boxWidth", 10);
    bindings.dimensionsChanged(context);
    assertEquals("12.0 / ", bindings.text("{{=vars.half}} / {{localOnly}}", context));
    assertThrows(IllegalArgumentException.class, () -> SkinBindings.resetContext(context, context));
  }
  @Test
  void reusableScopeKeepsSettingsAndRecipeReplacementLive() {
    Settings settings = new Settings();
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, Object> frame = new HashMap<String, Object>();
    bindings.frameContext(frame);
    Map<String, Object> context = bindings.scope(SkinBindings.childContext(frame),
        Collections.singletonMap("--amount", "saturation.strength*2"));
    assertEquals(2.3, ((Number) bindings.get("vars.amount", context)).doubleValue(), .00001);
    settings.saturation.strength = .75f;
    bindings.frameContext(frame);
    SkinBindings.resetContext(context, frame);
    bindings.scope(context, Collections.singletonMap("--amount", "saturation.strength*2"));
    assertEquals(1.5, bindings.get("vars.amount", context));
    SkinBindings.resetContext(context, frame);
    bindings.scope(context, Collections.singletonMap("--amount", "saturation.strength*4"));
    assertEquals(3d, bindings.get("vars.amount", context));
  }
  @Test
  void indexedVariablesCacheNullAndGuardRecursionBeyondTheFirstBitWord() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    bindings.frameContext(frame);
    Map<String, String> style = new LinkedHashMap<String, String>();
    for (int index = 0; index < 180; index++)
      style.put("--number" + index, Integer.toString(index));
    style.put("--left", "vars.right");
    style.put("--right", "vars.left");
    style.put("--missing", "missing.value");
    Map<String, Object> context = bindings.scope(SkinBindings.childContext(frame), style);
    for (int index = 0; index < 180; index++)
      assertEquals((double) index, bindings.get("vars.number" + index, context));
    assertNull(bindings.get("vars.left", context));
    assertNull(bindings.get("vars.right", context));
    assertNull(bindings.get("vars.missing", context));
    assertNull(bindings.get("vars.missing", context));
    assertEquals(179d, bindings.get("vars.number179", context),
        "A rejected cycle must release its evaluation slots");
  }
  @Test
  void resolverCacheIsOwnedByItsBindingsInstance() {
    Settings one = new Settings(), two = new Settings();
    one.saturation.strength = .25f;
    two.saturation.strength = 1.5f;
    SkinBindings first = new SkinBindings(one), second = new SkinBindings(two);
    Map<String, Object> context = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    assertEquals(.5d, first.get("=saturation.strength*2", context));
    assertEquals(3d, second.get("=saturation.strength*2", context));
    assertEquals(.5d, first.get("=saturation.strength*2", context));
  }
}
