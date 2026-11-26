package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinLazyBindingTest {
  @Test
  void presenceAndScopeConstructionNeverEvaluateOptionalValues() {
    final int[] reads = {0, 0};
    Map<String, Object> root = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    SkinBindings.lazy(root, "optional", () -> {
      reads[0]++;
      return Collections.singletonMap("name", "live");
    });
    SkinBindings.lazy(root, "nullable", () -> {
      reads[1]++;
      return null;
    });
    Map<String, Object> leaf = root;
    for (int i = 0; i < 40; i++) leaf = SkinBindings.childContext(leaf);
    assertTrue(leaf.containsKey("optional"));
    assertTrue(leaf.containsKey("nullable"));
    assertFalse(leaf.containsKey("missing"));
    assertArrayEquals(new int[] {0, 0}, reads);
    SkinBindings bindings = new SkinBindings(new Settings());
    assertEquals("live", bindings.get("optional.name", leaf));
    assertEquals("live", bindings.get("optional.name", leaf));
    assertArrayEquals(new int[] {1, 0}, reads);
    assertNull(leaf.get("nullable"));
    assertTrue(leaf.containsKey("nullable"));
    assertNull(bindings.get("nullable", leaf));
    assertArrayEquals(new int[] {1, 1}, reads);
    assertNull(leaf.put("nullable", "shadow"));
    assertEquals("shadow", leaf.get("nullable"));
    assertNull(root.get("nullable"));
    assertArrayEquals(new int[] {1, 1}, reads);
  }
  @Test
  void explicitMapSnapshotsResolveLazyValuesAndPreserveDimensionSlots() {
    final int[] reads = {0};
    Map<String, Object> root = SkinBindings.lazyContext();
    SkinBindings.lazy(root, "base", () -> {
      reads[0]++;
      return 7;
    });
    root.put("nullable", null);
    Map<String, Object> parent = SkinBindings.childContext(root);
    parent.put("boxWidth", 80);
    parent.put("boxHeight", 20);
    Map<String, Object> leaf = SkinBindings.childContext(parent);
    SkinBindings.lazy(leaf, "child", () -> {
      reads[0]++;
      return 9;
    });
    assertEquals(5, leaf.size());
    assertEquals(0, reads[0], "Asking the size must only enumerate binding names");
    Map<String, Object> copy = new HashMap<String, Object>(leaf);
    assertEquals(7, copy.get("base"));
    assertEquals(9, copy.get("child"));
    assertNull(copy.get("nullable"));
    assertTrue(copy.containsKey("nullable"));
    assertEquals(80, copy.get("boxWidth"));
    assertEquals(20, copy.get("boxHeight"));
    assertEquals(2, reads[0]);
    assertEquals(copy, new HashMap<String, Object>(leaf));
    assertEquals(2, reads[0]);
    parent.put("boxWidth", 40);
    assertEquals(40, leaf.get("boxWidth"));
    assertEquals(80, copy.get("boxWidth"));
  }
  @Test
  void contextNullShadowingAndLazyThemeBindingsOverrideCompiledSettingsPaths() {
    Settings settings = new Settings();
    settings.theme.accent = 0xff123456;
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, Object> context = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    assertEquals(0xff123456, bindings.get(" settings.theme.accent ", context));
    final int[] reads = {0};
    SkinBindings.lazy(context, "theme", () -> {
      reads[0]++;
      return Collections.singletonMap("accent", 42);
    });
    assertEquals(42, bindings.get(" settings.theme.accent ", context));
    assertEquals(42, bindings.get("theme.accent", context));
    assertEquals(1, reads[0]);
    context.put("theme", null);
    assertNull(bindings.get("theme.accent", context));
    assertTrue(context.containsKey("theme"));
    Map<String, Object> other = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    settings.theme.accent = 0xffabcdef;
    assertEquals(0xffabcdef, bindings.get("theme.accent", other));
  }
  @Test
  void rowWidthMeasuresCombinedColumnsOnTheSameRowAndAllowsHiddenColumns() {
    SkinBindings bindings = new SkinBindings(new Settings());
    bindings.measurementCanvas(new SkinViewTest.Sink() {
      @Override
      public float textWidthFamily(
          String family, String text, float size, boolean bold, float tracking) {
        return text.length() * size;
      }
    });
    Map<String, Object> root = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    root.put("rows", Arrays.asList(row("aaaa", 1), row("b", 1234)));
    bindings.frameContext(root);
    assertEquals(10d,
        ((Number) bindings.get("=maxRowTextWidth('rows','label','amount|integer',2,'test')", root))
            .doubleValue());
    assertEquals(8d,
        ((Number) bindings.get("=maxRowTextWidth('rows','label','',2,'test',true,0)", root))
            .doubleValue());
    assertEquals(
        0d, ((Number) bindings.get("=maxRowTextWidth('rows','','',2,'test')", root)).doubleValue());
    assertEquals(
        8d, ((Number) bindings.get("=maxTextWidth('rows','label',2,'test')", root)).doubleValue());
    root.put("rows", Arrays.asList(row("bb", 9)));
    bindings.frameContext(root);
    assertEquals(6d,
        ((Number) bindings.get("=maxRowTextWidth('rows','label','amount|integer',2,'test')", root))
            .doubleValue());
  }
  private static Map<String, Object> row(String name, int amount) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("label", name);
    row.put("amount", amount);
    return row;
  }
}
