package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinVariableScopeSharingTest {
  @Test
  void plainDescendantsShareLexicalVariablesAndLateBoundControlsForkBeforeShadowing() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    frame.put("value", 3);
    frame.put("active", false);
    bindings.frameContext(frame);
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--double", "value*2");
    style.put("--enabled", "active ? 1 : 0");
    Map<String, Object> parent = bindings.scope(SkinBindings.childContext(frame), style);
    Map<String, Object> plain =
        bindings.scope(SkinBindings.childContext(SkinBindings.childContext(parent)), style);
    assertSame(parent.get("vars"), plain.get("vars"));
    assertEquals(6d, bindings.get("vars.double", plain));
    plain.put("boxWidth", 20);
    plain.put("boxHeight", 10);
    bindings.dimensionsChanged(plain);
    assertSame(parent.get("vars"), plain.get("vars"));
    plain.put("value", 7);
    plain.put("active", true);
    assertNotSame(parent.get("vars"), plain.get("vars"));
    assertEquals(14d, bindings.get("vars.double", plain));
    assertEquals(1d, bindings.get("vars.enabled", plain));
    assertEquals(6d, bindings.get("vars.double", parent));
    assertEquals(0d, bindings.get("vars.enabled", parent));
  }
  @Test
  void rowAndLocalComponentScopesKeepTheirOwnVariableInputs() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>(),
                        firstRow = new HashMap<String, Object>();
    firstRow.put("column", 1);
    frame.put("row", firstRow);
    bindings.frameContext(frame);
    Map<String, String> style = Collections.singletonMap("--x", "row.column*2");
    Map<String, Object> parent = bindings.scope(SkinBindings.childContext(frame), style);
    Map<String, Object> secondRow = new HashMap<String, Object>();
    secondRow.put("column", 5);
    Map<String, Object> rowContext = SkinBindings.childContext(parent);
    rowContext.put("row", secondRow);
    rowContext.put("index", 2);
    Map<String, Object> rowNode = bindings.scope(SkinBindings.childContext(rowContext), style);
    assertNotSame(parent.get("vars"), rowNode.get("vars"));
    assertEquals(2d, bindings.get("vars.x", parent));
    assertEquals(10d, bindings.get("vars.x", rowNode));
    secondRow.put("column", 9);
    bindings.frameContext(frame);
    assertEquals(18d, bindings.get("vars.x", rowNode));
    Map<String, Object> localContext = SkinBindings.childContext(parent);
    localContext.put("local", Collections.singletonMap("selected", true));
    Map<String, Object> localNode = bindings.scope(SkinBindings.childContext(localContext), style);
    assertNotSame(parent.get("vars"), localNode.get("vars"));
  }
  @Test
  void dimensionsAndDifferentLexicalRecipesNeverReuseAnotherScopesVariables() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    frame.put("value", 3);
    bindings.frameContext(frame);
    Map<String, String> dimensions = Collections.singletonMap("--size", "boxWidth/2");
    Map<String, Object> parent = bindings.scope(SkinBindings.childContext(frame), dimensions);
    parent.put("boxWidth", 100);
    Map<String, Object> child = bindings.scope(SkinBindings.childContext(parent), dimensions);
    child.put("boxWidth", 40);
    assertNotSame(parent.get("vars"), child.get("vars"));
    assertEquals(50d, bindings.get("vars.size", parent));
    assertEquals(20d, bindings.get("vars.size", child));
    Map<String, Object> original = bindings.scope(
        SkinBindings.childContext(frame), Collections.singletonMap("--size", "value*2"));
    Map<String, Object> unrelated = bindings.scope(
        SkinBindings.childContext(original), Collections.singletonMap("--size", "value+1"));
    Map<String, Object> restored = bindings.scope(
        SkinBindings.childContext(unrelated), Collections.singletonMap("--size", "value*2"));
    assertNotSame(original.get("vars"), restored.get("vars"));
    assertNotSame(unrelated.get("vars"), restored.get("vars"));
    assertEquals(6d, bindings.get("vars.size", restored));
  }
  @Test
  void reparentedSharedScopesUseTheNewRowsAndInheritedValues() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    frame.put("value", 3);
    bindings.frameContext(frame);
    Map<String, String> style = Collections.singletonMap("--double", "value*2");
    Map<String, Object> first = bindings.scope(SkinBindings.childContext(frame), style),
                        child = bindings.scope(SkinBindings.childContext(first), style);
    assertEquals(6d, bindings.get("vars.double", child));
    Map<String, Object> changed = SkinBindings.childContext(frame);
    changed.put("value", 8);
    Map<String, Object> second = bindings.scope(SkinBindings.childContext(changed), style);
    SkinBindings.resetContext(child, second);
    bindings.scope(child, style);
    assertSame(second.get("vars"), child.get("vars"));
    assertEquals(16d, bindings.get("vars.double", child));
    assertEquals(6d, bindings.get("vars.double", first));
  }
  @Test
  void boxIndependentNodesCanAliasOnlyTheExactUnshadowedLexicalScope() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    frame.put("value", 3);
    bindings.frameContext(frame);
    Map<String, String> style = Collections.singletonMap("--double", "value*2");
    Map<String, Object> parent = bindings.scope(SkinBindings.childContext(frame), style);
    parent.put("boxWidth", 120);
    parent.put("boxHeight", 40);
    assertSame(parent, bindings.nodeContext(parent, style, true));
    assertEquals(120, parent.get("boxWidth"));
    assertEquals(40, parent.get("boxHeight"));
    assertEquals(6d, bindings.get("vars.double", parent));
    assertNotSame(parent, bindings.nodeContext(parent, style, false));
    assertNotSame(parent,
        bindings.nodeContext(parent, Collections.singletonMap("--double", "value*3"), true));
    Map<String, Object> override = SkinBindings.childContext(parent);
    override.put("value", 8);
    Map<String, Object> child = bindings.nodeContext(override, style, true);
    assertNotSame(override, child);
    assertEquals(16d, bindings.get("vars.double", child));
    assertEquals(6d, bindings.get("vars.double", parent));
    Map<String, Object> empty = SkinBindings.childContext(frame);
    assertSame(empty, bindings.nodeContext(empty, Collections.<String, String>emptyMap(), true));
  }
  @Test
  void dimensionRecipesAndUnknownVariableMapsAlwaysKeepTheirOwnNodeContext() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>();
    bindings.frameContext(frame);
    Map<String, String> style = Collections.singletonMap("--half", "boxWidth/2");
    Map<String, Object> parent = bindings.scope(SkinBindings.childContext(frame), style);
    parent.put("boxWidth", 120);
    Map<String, Object> child = bindings.nodeContext(parent, style, true);
    assertNotSame(parent, child);
    child.put("boxWidth", 40);
    assertEquals(60d, bindings.get("vars.half", parent));
    assertEquals(20d, bindings.get("vars.half", child));
    Map<String, Object> unknown = SkinBindings.childContext(frame);
    unknown.put("vars", Collections.singletonMap("half", 7));
    assertNotSame(
        unknown, bindings.nodeContext(unknown, Collections.<String, String>emptyMap(), true));
    assertNotSame(frame, bindings.nodeContext(frame, Collections.<String, String>emptyMap(), true),
        "Unknown ordinary root maps stay isolated");
  }
}
