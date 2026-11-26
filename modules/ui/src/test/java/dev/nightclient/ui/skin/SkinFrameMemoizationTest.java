package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.*;
import org.junit.jupiter.api.Test;

class SkinFrameMemoizationTest {
  private static Map<String, Object> frame() {
    Map<String, Object> frame = new HashMap<String, Object>();
    frame.put("width", 100);
    frame.put("height", 50);
    frame.put("cooldownRows", Collections.singletonList(Collections.singletonMap("name", "ab")));
    frame.put("sourceName", "cooldownRows");
    return frame;
  }
  private static Map<String, String> style() {
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--fit",
        "width + retainedCount('cooldownRows')*10 + maxTextWidth('cooldownRows','name',10)");
    style.put("--size", "vars.fit + height");
    return style;
  }
  @Test
  void frameDagsShareAcrossNodesButLocalAndSourceAliasesKeepTheirValues() {
    SkinBindings bindings = new SkinBindings(new Settings());
    final int[] retainedCalls = {0};
    bindings.retainedCounts((source, rows) -> {
      retainedCalls[0]++;
      return rows.size();
    });
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    bindings.measurementCanvas(sink);
    Map<String, Object> frame = frame();
    bindings.frameContext(frame);
    Map<String, String> style = style();
    Object first = null;
    for (int i = 0; i < 100; i++) {
      Map<String, Object> node = bindings.scope(SkinBindings.childContext(frame), style);
      node.put("boxWidth", i + 1);
      bindings.invalidateVariables(node);
      Object value = bindings.get("vars.size", node);
      assertEquals(170d, value);
      if (first == null)
        first = value;
      else
        assertSame(first, value, "Identical frame-only DAGs are shared across nodes");
    }
    assertEquals(1, retainedCalls[0]);
    Map<String, Object> override = SkinBindings.childContext(frame);
    override.put("width", 200);
    bindings.scope(override, style);
    assertEquals(270d, bindings.get("vars.size", override));
    Map<String, Object> rows = SkinBindings.childContext(frame);
    rows.put(
        "cooldownRows", Collections.singletonList(Collections.singletonMap("name", "abcdefgh")));
    bindings.scope(rows, style);
    assertEquals(200d, bindings.get("vars.size", rows));
    assertEquals(
        2, retainedCalls[0], "Distinct source-list aliases need distinct host-function results");
    Map<String, Object> next = frame();
    next.put("width", 200);
    bindings.measurementCanvas(sink);
    bindings.frameContext(next);
    Map<String, Object> node = bindings.scope(SkinBindings.childContext(next), style);
    assertEquals(270d, bindings.get("vars.size", node));
    assertEquals(3, retainedCalls[0]);
  }
  @Test
  void aSharedHitAlsoFillsTheLocalScopeCache() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = frame();
    bindings.frameContext(frame);
    Map<String, String> style = style();
    bindings.scope(SkinBindings.childContext(frame), style);
    Map<String, Object> warm = bindings.scope(SkinBindings.childContext(frame), style);
    bindings.get("vars.size", warm);
    final int[] widthReads = {0};
    Map<String, Object> scope = new HashMap<String, Object>(frame) {
      @Override
      public Object get(Object key) {
        if ("width".equals(key)) widthReads[0]++;
        return super.get(key);
      }
    };
    bindings.scope(scope, style);
    Object first = bindings.get("vars.size", scope);
    int before = widthReads[0];
    for (int i = 0; i < 100; i++) assertSame(first, bindings.get("vars.size", scope));
    assertEquals(before, widthReads[0], "A local hit must not rerun frame-dependency checks");
  }
  @Test
  void aDynamicSourceNameRemainsLocalWhenTheResolvedListIsShadowed() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = frame();
    bindings.frameContext(frame);
    Map<String, String> style = Collections.singletonMap("--count", "retainedCount(sourceName)");
    Map<String, Object> first = bindings.scope(SkinBindings.childContext(frame), style);
    assertEquals(1d, bindings.get("vars.count", first));
    Map<String, Object> override = SkinBindings.childContext(frame);
    override.put("cooldownRows", Arrays.asList("one", "two", "three"));
    bindings.scope(override, style);
    assertEquals(3d, bindings.get("vars.count", override));
  }
  @Test
  void memoizedInheritedFrameHeadsKeepLayoutDimensionsLive() {
    Map<String, Object> frame = frame(), parent = SkinBindings.childContext(frame);
    parent.put("boxWidth", 100);
    parent.put("boxHeight", 50);
    Map<String, Object> child = SkinBindings.childContext(parent);
    assertEquals(100, child.get("boxWidth"));
    assertEquals(50, child.get("boxHeight"));
    parent.put("boxWidth", 80);
    parent.put("boxHeight", 40);
    assertEquals(80, child.get("boxWidth"));
    assertEquals(40, child.get("boxHeight"));
    child.put("boxWidth", 30);
    parent.put("boxWidth", 60);
    assertEquals(30, child.get("boxWidth"));
    assertEquals(100, frame.get("width"));
  }
}
