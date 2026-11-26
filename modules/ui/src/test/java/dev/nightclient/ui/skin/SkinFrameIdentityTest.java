package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinFrameIdentityTest {
  @Test
  void descendantsShareIdentityTextAndObserveLateAncestorOverrides() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> root = new HashMap<String, Object>();
    root.put("__scope", "menu");
    Map<String, Object> row = SkinBindings.childContext(root);
    row.put("__repeatKey", "rows/7");
    Map<String, Object> leaf = row;
    for (int depth = 0; depth < 40; depth++) leaf = SkinBindings.childContext(leaf);
    String identity = bindings.frameIdentity(row);
    assertEquals("rows/7/menu", identity);
    assertSame(identity, bindings.frameIdentity(leaf));
    root.put("__scope", "hud");
    assertEquals("rows/7/hud", bindings.frameIdentity(leaf));
    row.put("__mountKey", "control");
    assertEquals("rows/7/hud/control", bindings.frameIdentity(leaf));
    leaf.put("__scope", null);
    assertEquals("rows/7/null/control", bindings.frameIdentity(leaf));
    row.put("__repeatKey", "rows/8");
    assertEquals("rows/8/null/control", bindings.frameIdentity(leaf));
  }
  @Test
  void absentNullAndEmptyPartsKeepTheOriginalIdentityEncoding() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    assertEquals("", bindings.frameIdentity(context));
    context.put("__repeatKey", null);
    assertEquals("null", bindings.frameIdentity(context));
    context.put("__scope", null);
    context.put("__mountKey", null);
    context.put("elementId", null);
    assertEquals("null/null/null/null", bindings.frameIdentity(context));
    Map<String, Object> plain = new HashMap<String, Object>();
    plain.put("__scope", "");
    assertEquals("/", bindings.frameIdentity(plain));
    plain.put("__repeatKey", null);
    assertEquals("null/", bindings.frameIdentity(plain));
  }
  @Test
  void lazyElementIdentityIsResolvedOnceAndUnrelatedDataStaysLazy() {
    SkinBindings bindings = new SkinBindings(new Settings());
    final int[] reads = {0, 0};
    Map<String, Object> parent = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    SkinBindings.lazy(parent, "elementId", () -> {
      reads[0]++;
      return "slider";
    });
    SkinBindings.lazy(parent, "unused", () -> {
      reads[1]++;
      return "unneeded";
    });
    Map<String, Object> first = SkinBindings.childContext(parent),
                        second = SkinBindings.childContext(parent);
    assertEquals("/slider", bindings.frameIdentity(first));
    assertSame(bindings.frameIdentity(first), bindings.frameIdentity(second));
    assertArrayEquals(new int[] {1, 0}, reads);
    parent.put("elementId", "toggle");
    assertEquals("/toggle", bindings.frameIdentity(first));
    assertEquals("/toggle", bindings.frameIdentity(second));
    assertArrayEquals(new int[] {1, 0}, reads);
  }
  @Test
  void reparentingAndHashCollisionsCannotRetainAnotherRowsIdentity() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> first = SkinBindings.childContext(Collections.<String, Object>emptyMap()),
                        second = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    first.put("__repeatKey", "Aa");
    second.put("__repeatKey", "BB");
    assertEquals("Aa".hashCode(), "BB".hashCode());
    Map<String, Object> child = SkinBindings.childContext(first);
    child.put("__scope", "old");
    assertEquals("Aa/old", bindings.frameIdentity(child));
    SkinBindings.resetContext(child, second);
    assertEquals("BB", bindings.frameIdentity(child));
    assertEquals("Aa", bindings.frameIdentity(first));
    second.put("__scope", "new");
    assertEquals("BB/new", bindings.frameIdentity(child));
  }
}
