package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinInputPlanTest {
  @Test
  void compileExpandsOnlyReferencedVariablesAndKeepsSettingsAndScalarContextLive()
      throws Exception {
    SkinDocument document =
        NcParser.parse("hud { panel { let used='inventory.width*2'; let unused='time+vars.cycle'; "
                        + "let cycle='vars.unused'; width='{{=vars.used}}'; text '{{label}}' } }");
    SkinInputPlan plan = SkinInputPlan.compile(document.hud(), document, node -> null);
    assertTrue(plan.safe());
    assertTrue(Arrays.asList(plan.paths()).contains("inventory.width"));
    assertFalse(Arrays.asList(plan.paths()).contains("time"));
    Settings settings = new Settings();
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("label", "a");
    SkinInputPlan.Snapshot snapshot = plan.capture(bindings, values);
    assertNotNull(snapshot);
    assertTrue(snapshot.matches(bindings, values));
    values.put("label", "b");
    assertFalse(snapshot.matches(bindings, values));
    values.put("label", "a");
    assertTrue(snapshot.matches(bindings, values));
    settings.inventory.width += 1;
    assertFalse(snapshot.matches(bindings, values));
  }
  @Test
  void semanticRepeatRowsAreKeyedByImmutablePreparedOutputsRatherThanMutableInputIdentity()
      throws Exception {
    SkinDocument document =
        NcParser.parse("hud { repeat(source='inventoryCells',as='row',key='{{row.slotId}}') { "
                        + "text '{{row.name}}' { x='{{=index*18}}' } } }");
    SkinInputPlan plan = SkinInputPlan.compile(document.hud(), document, node -> null);
    assertTrue(plan.safe());
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> values = new HashMap<String, Object>();
    java.util.List<Map<String, Object>> mutable = new java.util.ArrayList<Map<String, Object>>();
    mutable.add(Collections.<String, Object>singletonMap("name", "one"));
    values.put("inventoryCells", mutable);
    assertNull(plan.capture(bindings, values),
        "A caller-owned mutable list must never become a cache identity");
    values.put("inventoryCells",
        SkinInputPlan.stableRows(
            Collections.unmodifiableList(new java.util.ArrayList<Map<String, Object>>(mutable))));
    SkinInputPlan.Snapshot snapshot = plan.capture(bindings, values);
    assertNotNull(snapshot);
    assertTrue(snapshot.matches(bindings, values));
    values.put("inventoryCells",
        SkinInputPlan.stableRows(
            Collections.singletonList(Collections.<String, Object>singletonMap("name", "two"))));
    assertFalse(snapshot.matches(bindings, values));
  }
  @Test
  void timeDimensionsInteractiveStateAndDynamicSourcesStayOnTheFullBuildPath() throws Exception {
    for (String source : new String[] {"hud { text '{{time}}' }",
             "hud { panel { width='{{boxWidth}}' } }", "hud { text '{{ui.search}}' }",
             "hud { panel(scope='a') {} }", "hud { text '{{=retainedCount(sourceName)}}' }",
             "hud { panel { position=overlay; width=100px; height=30px } }",
             "hud { crosshair(source='native') }", "hud { button 'button' }"}) {
      SkinDocument document = NcParser.parse(source);
      assertFalse(SkinInputPlan.compile(document.hud(), document, node -> null).safe(), source);
    }
  }
  @Test
  void canvasMeasurementsAndLiteralRetainedCountsBecomeSnapshotInputs() throws Exception {
    SkinDocument measured = NcParser.parse("hud { text '{{=textWidth(label,12)}}' }");
    SkinInputPlan measurement = SkinInputPlan.compile(measured.hud(), measured, node -> null);
    assertTrue(measurement.safe());
    assertTrue(Arrays.asList(measurement.paths()).contains("language"));
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = new HashMap<String, Object>();
    context.put("label", "a");
    SkinInputPlan.Snapshot snapshot = measurement.capture(bindings, context);
    assertNotNull(snapshot);
    assertTrue(snapshot.matches(bindings, context));
    context.put("label", "b");
    assertFalse(snapshot.matches(bindings, context));
    SkinDocument retained = NcParser.parse("hud { text '{{=retainedCount(\"tntRows\")}}' }");
    SkinInputPlan rows = SkinInputPlan.compile(retained.hud(), retained, node -> null);
    assertTrue(rows.safe());
    assertTrue(Arrays.asList(rows.paths()).contains("tntRows"));
    assertTrue(Arrays.asList(rows.paths()).contains("menu.animationSpeed"));
    final int[] count = {2};
    bindings.retainedCounts((source, values) -> count[0]);
    context.put("tntRows", SkinInputPlan.stableRows(Collections.<Map<String, Object>>emptyList()));
    snapshot = rows.capture(bindings, context);
    assertNotNull(snapshot);
    assertTrue(snapshot.matches(bindings, context));
    count[0] = 3;
    assertFalse(snapshot.matches(bindings, context));
    count[0] = 2;
    assertTrue(snapshot.matches(bindings, context));
    context.put("tntRows", SkinInputPlan.stableRows(Collections.<Map<String, Object>>emptyList()));
    assertFalse(snapshot.matches(bindings, context));
  }
  @Test
  void activeStateDependenciesAndMountedComponentsAreCollected() throws Exception {
    SkinDocument document =
        NcParser.parse("style element panel { active { color='{{activeColor}}' } } hud { "
                        + "panel(id='mounted') } component 'mounted' { text '{{title}}' }");
    SkinInputPlan plan = SkinInputPlan.compile(document.hud(), document,
        node
        -> "mounted".equals(node.id) && !"component".equals(node.tag)
            ? document.component("mounted")
            : null);
    assertTrue(plan.safe());
    assertTrue(Arrays.asList(plan.paths()).contains("activeColor"));
    assertTrue(Arrays.asList(plan.paths()).contains("title"));
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = new HashMap<String, Object>();
    context.put("activeColor", 1);
    context.put("title", "hello");
    SkinInputPlan.Snapshot snapshot = plan.capture(bindings, context);
    assertNotNull(snapshot);
    context.put("activeColor", 2);
    assertFalse(snapshot.matches(bindings, context));
    context.put("activeColor", 1);
    context.put("title", "changed");
    assertFalse(snapshot.matches(bindings, context));
  }
  @Test
  void retainedComponentsObserveRetirementSettings() {
    SkinDocument document = DefaultSkin.document();
    for (String id : new String[] {"hud.cooldowns", "hud.tnt", "hud.potions"}) {
      SkinInputPlan plan = SkinInputPlan.compile(document.component(id), document, node -> null);
      assertTrue(plan.safe(), id);
      assertTrue(Arrays.asList(plan.paths()).contains("menu.animationSpeed"), id);
      if ("hud.tnt".equals(id)) assertTrue(Arrays.asList(plan.paths()).contains("tnt.range"), id);
    }
  }
  @Test
  void unsupportedObjectsRemainUncachedAndDefaultInventoryDoesNotReadUnusedTntSources() {
    SkinDocument document = DefaultSkin.document();
    SkinInputPlan inventory =
        SkinInputPlan.compile(document.component("hud.inventory"), document, node -> null);
    assertTrue(inventory.safe());
    assertFalse(Arrays.asList(inventory.paths()).contains("tntRows"));
    assertFalse(Arrays.asList(inventory.paths()).contains("tnt.enabled"));
    assertTrue(Arrays.asList(inventory.paths()).contains("inventoryCells"));
    SkinInputPlan target =
        SkinInputPlan.compile(document.component("hud.target"), document, node -> null);
    assertFalse(
        target.safe(), "Live target equipment is not part of the immutable target row snapshot");
  }
}
