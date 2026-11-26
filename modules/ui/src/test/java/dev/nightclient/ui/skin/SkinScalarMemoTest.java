package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinScalarMemoTest {
  @Test
  void mutableRowFieldsAndTimeStayLiveAcrossFrameAndScopeReuse() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> frame = new HashMap<String, Object>(), row = new HashMap<String, Object>();
    frame.put("row", row);
    frame.put("time", 2d);
    row.put("column", 3);
    bindings.frameContext(frame);
    Map<String, String> styles = new LinkedHashMap<String, String>();
    styles.put("--x", "row.column*4");
    styles.put("--pulse", "time*2");
    Map<String, Object> context = bindings.scope(SkinBindings.childContext(frame), styles);
    assertEquals("12.0 / 4.0", bindings.text("{{=vars.x}} / {{=vars.pulse}}", context));
    row.put("column", 8);
    frame.put("time", 3d);
    bindings.frameContext(frame);
    SkinBindings.resetContext(context, frame);
    bindings.scope(context, styles);
    assertEquals("32.0 / 6.0", bindings.text("{{=vars.x}} / {{=vars.pulse}}", context));
    row.put("column", 1);
    bindings.frameContext(frame);
    SkinBindings.resetContext(context, frame);
    bindings.scope(context, styles);
    assertEquals("4.0 / 6.0", bindings.text("{{=vars.x}} / {{=vars.pulse}}", context));
  }
  @Test
  void outerScalarMemoObservesCurrentHostFontMetricsAndMutatedRows() {
    final float[] advance = {1};
    SkinViewTest.Sink canvas = new SkinViewTest.Sink() {
      @Override
      public float textWidthFamily(
          String family, String text, float size, boolean bold, float tracking) {
        return text.length() * advance[0];
      }
    };
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> row = new HashMap<String, Object>(), frame = new HashMap<String, Object>();
    row.put("name", "abc");
    frame.put("rows", Collections.singletonList(row));
    Map<String, String> styles = new LinkedHashMap<String, String>();
    styles.put("--width", "maxTextWidth('rows','name',12,'test')");
    styles.put("--double", "vars.width*2");
    bindings.measurementCanvas(canvas);
    bindings.frameContext(frame);
    Map<String, Object> context = bindings.scope(SkinBindings.childContext(frame), styles);
    assertEquals("6.0", bindings.text("{{=vars.double}}", context));
    advance[0] = 2;
    bindings.measurementCanvas(canvas);
    bindings.frameContext(frame);
    SkinBindings.resetContext(context, frame);
    bindings.scope(context, styles);
    assertEquals("12.0", bindings.text("{{=vars.double}}", context));
    row.put("name", "a");
    bindings.measurementCanvas(canvas);
    bindings.frameContext(frame);
    SkinBindings.resetContext(context, frame);
    bindings.scope(context, styles);
    assertEquals("4.0", bindings.text("{{=vars.double}}", context));
  }
  @Test
  void branchingExpressionsNeverPrefetchUntouchedLazyBindings() {
    final int[] reads = {0};
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    SkinBindings.lazy(context, "optional", () -> {
      reads[0]++;
      return Collections.singletonMap("value", 99);
    });
    for (int frame = 0; frame < 4; frame++) {
      bindings.frameContext(context);
      assertEquals("7.0", bindings.text("{{=false ? optional.value : 7}}", context));
      assertEquals("false", bindings.text("{{=false && optional.value}}", context));
      assertEquals("true", bindings.text("{{=true || optional.value}}", context));
    }
    assertEquals(0, reads[0]);
    assertEquals("99.0", bindings.text("{{=true ? optional.value : 7}}", context));
    assertEquals(1, reads[0]);
  }
  @Test
  void compiledTextHandlesPreserveDimensionInvalidationAndFormatting() {
    SkinBindings bindings = new SkinBindings(new Settings());
    Map<String, Object> context = SkinBindings.childContext(Collections.<String, Object>emptyMap());
    Map<String, String> style = new LinkedHashMap<String, String>();
    style.put("--half", "boxWidth/2");
    style.put("--constant", "5*2");
    bindings.scope(context, style);
    context.put("boxWidth", 80);
    context.put("amount", 1.25);
    SkinBindings.BoundText dynamic = bindings.compileText("{{=vars.half}} / {{amount|fixed:1}}"),
                           fixed = bindings.compileText("{{=vars.constant}}");
    assertTrue(bindings.dimensionDependent(dynamic, context));
    assertFalse(bindings.dimensionDependent(fixed, context));
    assertEquals("40.0 / 1.3", bindings.text(dynamic, context));
    assertEquals(bindings.text("{{=vars.half}} / {{amount|fixed:1}}", context),
        bindings.text(dynamic, context));
    String unchanged = bindings.text(fixed, context);
    context.put("boxWidth", 40);
    bindings.dimensionsChanged(context);
    assertEquals("20.0 / 1.3", bindings.text(dynamic, context));
    assertSame(unchanged, bindings.text(fixed, context));
    assertEquals("", bindings.text(bindings.compileText(null), context));
    assertEquals("literal", bindings.text(bindings.compileText("literal"), context));
  }
}
