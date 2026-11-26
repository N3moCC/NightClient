package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NcScalarMemoTest {
  @Test
  void onlyEagerPureTreesMayReadAllScalarDependencies() {
    for (String source : new String[] {"value*2+offset", "clamp(value,2,20)", "len(label)",
             "setAlphaColor(accent,.5)", "round(time*4)"})
      assertTrue(NcExpression.compile(source).scalarMemoizable(), source);
    for (String source : new String[] {"enabled ? first : second", "enabled && hidden",
             "enabled || hidden", "textWidth(label,12)", "maxTextWidth('rows','name',12)",
             "maxRowTextWidth('rows','name','time',12)", "retainedCount('rows')",
             "format(value,'duration')"}) {
      NcExpression.Expression expression = NcExpression.compile(source);
      assertFalse(expression.scalarMemoizable(), source);
      assertThrows(IllegalArgumentException.class, expression::newScalarMemo);
    }
  }
  @Test
  void memoIsBoundToActualScalarValuesAndKeepsMutableHostStateOutsideTheAst() {
    NcExpression.Expression expression = NcExpression.compile("value*2+offset");
    NcExpression.ScalarMemo first = expression.newScalarMemo(),
                             second = expression.newScalarMemo();
    Map<String, Object> inputs = new HashMap<String, Object>();
    inputs.put("value", 3);
    inputs.put("offset", 1);
    String text = first.text(inputs::get);
    assertEquals("7.0", text);
    assertSame(text, first.text(inputs::get));
    inputs.put("value", 4);
    assertEquals("9.0", first.text(inputs::get));
    assertEquals(9d, second.evaluate(inputs::get));
    inputs.put("value", .25f);
    assertEquals(1.5d, first.evaluate(inputs::get));
    assertEquals(30d, expression.evaluate(path -> 10));
    inputs.put("value", 3);
    assertEquals("7.0", first.text(inputs::get));
  }
  @Test
  void arbitraryHostObjectsAreNeverComparedOrStringifiedByMemoKeys() {
    AtomicInteger calls = new AtomicInteger();
    Object opaque = new Object() {
      @Override
      public boolean equals(Object other) {
        calls.incrementAndGet();
        return true;
      }
      @Override
      public String toString() {
        calls.incrementAndGet();
        return "bad";
      }
    };
    NcExpression.ScalarMemo memo = NcExpression.compile("value+1").newScalarMemo();
    assertEquals(1d, memo.evaluate(path -> opaque));
    assertEquals("1.0", memo.text(path -> opaque));
    assertEquals(0, calls.get());
    assertEquals(3d, memo.evaluate(path -> 2));
    assertEquals(1d, memo.evaluate(path -> opaque));
    assertEquals(3d, memo.evaluate(path -> 2));
  }
  @Test
  void scalarInputCaptureReadsEachPathOnceAndPreservesFiniteNumberRules() {
    AtomicInteger reads = new AtomicInteger();
    NcExpression.ScalarMemo memo = NcExpression.compile("value+value").newScalarMemo();
    assertEquals(4d, memo.evaluate(path -> {
      reads.incrementAndGet();
      return 2;
    }));
    assertEquals(1, reads.get());
    assertEquals(4d, memo.evaluate(path -> {
      reads.incrementAndGet();
      return 2;
    }));
    assertEquals(2, reads.get());
    assertEquals(0d, memo.evaluate(path -> Double.NaN));
    assertEquals(0d, memo.evaluate(path -> Double.POSITIVE_INFINITY));
    assertEquals(6d, memo.evaluate(path -> 3));
  }
  @Test
  void rotatingInventoryRowsReuseTheirScalarResultsAndObserveMutatedValues() {
    NcExpression.ScalarMemo memo = NcExpression.compile("row*18+offset").newScalarMemo();
    Map<String, Object> inputs = new HashMap<String, Object>();
    inputs.put("offset", 6d);
    String[] texts = new String[27];
    for (int row = 0; row < 27; row++) {
      inputs.put("row", row);
      texts[row] = memo.text(inputs::get);
      assertEquals(Double.toString(row * 18d + 6), texts[row]);
    }
    for (int turn = 0; turn < 4; turn++)
      for (int row = 0; row < 27; row++) {
        inputs.put("row", row);
        assertSame(texts[row], memo.text(inputs::get));
      }
    inputs.put("row", 5);
    inputs.put("offset", 7d);
    assertEquals("97.0", memo.text(inputs::get));
    inputs.put("offset", 6d);
    assertSame(texts[5], memo.text(inputs::get));
  }
  @Test
  void scalarMemoEvictsOldInputsAfterItsBoundedWorkingSetAndSurvivesHashCollisions()
      throws ReflectiveOperationException {
    NcExpression.ScalarMemo memo = NcExpression.compile("value*2").newScalarMemo();
    assertEquals("0.0", memo.text(path -> 0));
    for (int input = 1; input <= 32; input++) {
      final int current = input;
      assertEquals(Double.toString(input * 2d), memo.text(path -> current));
    }
    java.lang.reflect.Field ringField = NcExpression.ScalarMemo.class.getDeclaredField("ring");
    ringField.setAccessible(true);
    Object[] entries = (Object[]) ringField.get(memo);
    assertEquals(32, entries.length);
    java.lang.reflect.Field inputsField = entries[0].getClass().getDeclaredField("inputs");
    inputsField.setAccessible(true);
    for (Object entry : entries)
      assertNotEquals(0, ((Object[]) inputsField.get(entry))[0],
          "The oldest scalar input must leave the bounded working set");
    assertEquals("0.0", memo.text(path -> 0));
    NcExpression.ScalarMemo strings = NcExpression.compile("label+'!'").newScalarMemo();
    String aa = strings.text(path -> "Aa"), bb = strings.text(path -> "BB");
    assertEquals("Aa".hashCode(), "BB".hashCode());
    assertEquals("Aa!", aa);
    assertEquals("BB!", bb);
    assertSame(aa, strings.text(path -> "Aa"));
    assertSame(bb, strings.text(path -> "BB"));
  }
  @Test
  void indexedInputsMatchNamedInputsAndKeepUnsupportedObjectSanitization() {
    NcExpression.Expression expression = NcExpression.compile("row*18+offset");
    assertEquals(2, expression.scalarInputCount());
    assertEquals("row", expression.scalarInputPath(0));
    assertEquals("offset", expression.scalarInputPath(1));
    NcExpression.ScalarMemo named = expression.newScalarMemo(),
                             indexed = expression.newScalarMemo();
    Object[] values = {3, 6d};
    assertEquals(named.text(path -> "row".equals(path) ? values[0] : values[1]),
        indexed.textIndexed(index -> values[index]));
    String text = indexed.textIndexed(index -> values[index]);
    assertSame(text, indexed.textIndexed(index -> values[index]));
    values[0] = 7;
    values[1] = 2d;
    assertEquals("128.0", indexed.textIndexed(index -> values[index]));
    Object opaque = new Object() {
      @Override
      public boolean equals(Object other) {
        fail("An opaque input was compared");
        return false;
      }
      @Override
      public String toString() {
        fail("An opaque input was converted");
        return "bad";
      }
    };
    values[0] = opaque;
    assertEquals(2d, indexed.evaluateIndexed(index -> values[index]));
    values[0] = 3;
    assertEquals(56d, indexed.evaluateIndexed(index -> values[index]));
    assertThrows(IllegalArgumentException.class, () -> indexed.evaluateIndexed(null));
  }
}
