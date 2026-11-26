package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.*;
import org.junit.jupiter.api.Test;

class SkinTemplateMemoizationTest {
  @Test
  void boxChangesInvalidateOnlyBoxDependentTemplatesAndVariables() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> root = new HashMap<String, Object>();
    root.put("width", 200);
    bindings.frameContext(root);
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--quarter", "boxWidth/4");
    style.put("--constant", "width+17");
    Map<String, Object> node = bindings.scope(SkinBindings.childContext(root), style);
    node.put("boxWidth", 100);
    node.put("boxHeight", 50);
    assertEquals("25.0", bindings.text("{{=vars.quarter}}", node));
    String constant = bindings.text("{{=vars.constant}}", node);
    node.put("boxWidth", 80);
    bindings.dimensionsChanged(node);
    assertEquals("20.0", bindings.text("{{=vars.quarter}}", node));
    assertSame(constant, bindings.text("{{=vars.constant}}", node));
    node.put("boxHeight", 40);
    bindings.dimensionsChanged(node);
    assertSame(constant, bindings.text("{{=vars.constant}}", node));
  }
  @Test
  void inheritedBoxChangesStayLiveEvenWhenAChildHasNotBeenLaidOut() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> parent = SkinBindings.childContext(new HashMap<String, Object>());
    parent.put("boxWidth", 100);
    parent.put("boxHeight", 40);
    Map<String, Object> child = bindings.scope(
        SkinBindings.childContext(parent), Collections.singletonMap("--quarter", "boxWidth/4"));
    assertEquals("25.0", bindings.text("{{=vars.quarter}}", child));
    parent.put("boxWidth", 80);
    assertEquals("20.0", bindings.text("{{=vars.quarter}}", child));
  }
  @Test
  void changingBindingsOrStartingAnotherFrameInvalidatesCachedValues() {
    Settings settings = new Settings();
    SkinBindings bindings = new SkinBindings(settings);
    Map<String, Object> frame = new HashMap<String, Object>();
    bindings.frameContext(frame);
    Map<String, Object> node = bindings.scope(
        SkinBindings.childContext(frame), Collections.singletonMap("--double", "value*2"));
    node.put("value", 3);
    assertEquals("6.0", bindings.text("{{=vars.double}}", node));
    node.put("value", 4);
    assertEquals("8.0", bindings.text("{{=vars.double}}", node));
    assertEquals("1.15", bindings.text("{{saturation.strength}}", node));
    settings.saturation.strength = .8f;
    bindings.frameContext(frame);
    assertEquals("0.8", bindings.text("{{saturation.strength}}", node));
  }
  @Test
  void compiledTemplatesKeepFormattingQuotesBooleanPipesAndBounds() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> node = SkinBindings.childContext(new HashMap<String, Object>());
    node.put("amount", 1.25);
    assertEquals("Value 1.3 / true / 3.0",
        bindings.text("Value {{amount|fixed:1}} / {{=false || true}} / {{=len('a|b')}}", node));
    StringBuilder source = new StringBuilder();
    for (int i = 0; i < 33; i++) source.append("{{amount|integer}}");
    assertEquals(32, bindings.text(source.toString(), node).length());
    char[] text = new char[3000];
    Arrays.fill(text, 'x');
    node.put("label", new String(text));
    assertEquals(2048, bindings.text("{{label}}", node).length());
    assertEquals("before ", bindings.text("before {{unfinished", node));
  }
}
