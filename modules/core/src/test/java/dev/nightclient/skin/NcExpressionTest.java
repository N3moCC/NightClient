package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NcExpressionTest {
  @Test
  void presentationFunctionsAreBoundedOptionalHostCalls() {
    assertEquals(0d, NcExpression.compile("textWidth('abc',12)").evaluate(name -> null));
    final java.util.List<String> calls = new java.util.ArrayList<String>();
    NcExpression.Resolver resolver = new NcExpression.Resolver() {
      public Object apply(String name) {
        return null;
      }
      public Object call(String name, java.util.List<Object> args) {
        calls.add(name);
        return "format".equals(name) ? "1.23" : 42d;
      }
    };
    assertEquals(
        42d, NcExpression.compile("textWidth('abc',12,'family',false,1)").evaluate(resolver));
    assertEquals("1.23", NcExpression.compile("format(1.234,'fixed:2')").evaluate(resolver));
    assertEquals(42d, NcExpression.compile("maxTextWidth('rows','name',12)").evaluate(resolver));
    assertEquals(3, calls.size());
    for (String expression : new String[] {"textWidth('abc')", "textWidth('abc',1,'x',false,0,0)",
             "maxTextWidth('rows',12)", "format(12)"})
      assertThrows(IllegalArgumentException.class, () -> NcExpression.compile(expression));
  }
  private static Object value(String source) {
    return NcExpression.compile(source).evaluate(path -> null);
  }

  @Test
  void computesResponsiveLayoutWithNormalOperatorPrecedence() {
    Map<String, Object> bindings = new HashMap<String, Object>();
    bindings.put("viewport.width", 1280);
    bindings.put("ui.compact", false);
    assertEquals(640d,
        NcExpression.compile("clamp(viewport.width - 160, 320, 640)").evaluate(bindings::get));
    assertEquals(536d,
        NcExpression.compile("viewport.width / 2 - (ui.compact ? 72 : 104)")
            .evaluate(bindings::get));
    assertEquals(13d, value("1 + 2 * 6"));
    assertEquals(18d, value("(1 + 2) * 6"));
    assertEquals(1d, value("19 % 6"));
    assertEquals(-2.5d, value("-10 / +4"));
    assertEquals(.125d, value("1.25e-1"));
    assertEquals(2.5d, value(".5 + 2."));
  }

  @Test
  void handlesTextAndNestedChoicesWithoutExecutableFormattingCode() {
    assertEquals("Enabled", value("true ? 'Enabled' : 'Disabled'"));
    assertEquals("off", value("false ? 'first' : false ? 'second' : 'off'"));
    assertEquals("first", value("true ? false ? 'bad' : 'first' : 'second'"));
    assertEquals("12 px", value("round(11.6) + ' px'"));
    assertEquals("Привет \"мир\"\nЖ", value("'Привет \\\"мир\\\"\\n\\u0416'"));
    assertEquals(3d, value("len('А😀Б')"));
  }

  @Test
  void shortCircuitsBranchesAndEnumeratesAllReadOnlyDependencies() {
    AtomicInteger reads = new AtomicInteger();
    NcExpression.Expression expression = NcExpression.compile(
        "ui.enabled && (player.health > 0 ? item.name == 'Sword' : hidden.value)");
    assertEquals(Boolean.FALSE, expression.evaluate(path -> {
      reads.incrementAndGet();
      return false;
    }));
    assertEquals(1, reads.get());
    assertEquals(new HashSet<String>(
                     Arrays.asList("ui.enabled", "player.health", "item.name", "hidden.value")),
        expression.identifiers());
    assertThrows(UnsupportedOperationException.class, () -> expression.identifiers().add("extra"));
    assertEquals(Boolean.TRUE, NcExpression.compile("true || forbidden.path").evaluate(path -> {
      fail("Unused branch was evaluated");
      return null;
    }));
    assertEquals("good", NcExpression.compile("false ? forbidden.path : 'good'").evaluate(path -> {
      fail("Unused branch was evaluated");
      return null;
    }));
  }

  @Test
  void supportsOnlyTheDocumentedMathAndComparisonOperations() {
    assertEquals(2d, value("min(8, 3, 2, 7)"));
    assertEquals(8d, value("max(8, 3, 2, 7)"));
    assertEquals(4d, value("clamp(8, 1, 4)"));
    assertEquals(4d, value("clamp(8, 4, 1)"));
    assertEquals(-2d, value("floor(-1.1)"));
    assertEquals(-1d, value("ceil(-1.1)"));
    assertEquals(2d, value("abs(-2)"));
    assertEquals(Boolean.TRUE, value("2 < 3 && 3 <= 3 && 5 > 4 && 4 >= 4 && 3 == 3 && 3 != 4"));
    assertEquals(Boolean.TRUE, value("'armor' < 'zoom' && 'same' == 'same'"));
    assertEquals(Boolean.FALSE, value("'1' == 1"));
    assertEquals(Boolean.TRUE, value("!false && !(1 > 2)"));
  }

  @Test
  void mixesColorsWithRoundedOrTruncatedChannelMath() {
    assertEquals((double) 0xff808080,
        NcExpression.compile("mixColor('#000000','#FFFFFF',.5)").evaluate(path -> null));
    assertEquals((double) 0xff7f7f7f,
        NcExpression.compile("lerpColor('#000000','#FFFFFF',.5)").evaluate(path -> null));
    assertEquals((double) 0x8022aa44,
        NcExpression.compile("alphaColor('#22AA44FF',.5)").evaluate(path -> null));
    assertEquals((double) 0xff818181,
        NcExpression.compile("lerpColor('#505050','#FFFFFF',.28)").evaluate(path -> null));
  }

  @Test
  void replacingAlphaPreservesRgbAndDoesNotMultiplyTheExistingAlpha() {
    assertEquals((double) 0x3322aa44, value("setAlphaColor('#22AA4419',.2)"));
    assertEquals((double) 0xff22aa44, value("setAlphaColor('#22AA4419',2)"));
    assertEquals((double) 0x0022aa44, value("setAlphaColor('#22AA4419',-1)"));
    assertThrows(
        IllegalArgumentException.class, () -> NcExpression.compile("setAlphaColor('#FFFFFF')"));
  }

  @Test
  void untrustedExpressionsCannotAssignCallMethodsOrAccessObjects() {
    for (String source : new String[] {"ui.enabled = true", "ui.enabled++", "item['name']",
             "new Something()", "System.exit(0)", "Runtime.getRuntime()", "eval('1')", "random()",
             "foo()", "min()", "max()", "clamp(1, 2)", "abs(1, 2)", "floor()", "1; 2", "{}",
             "a -> a", "'text'.length()", "1e9999", "foo.", "foo..bar", "true ? 1", "1 +", "",
             "1e+", "'unterminated", "'\\x41'"}) {
      assertThrows(IllegalArgumentException.class, () -> NcExpression.compile(source), source);
    }
    AtomicInteger access = new AtomicInteger();
    Object opaque = new Object() {
      @Override
      public String toString() {
        access.incrementAndGet();
        return "unsafe";
      }
      @Override
      public boolean equals(Object other) {
        access.incrementAndGet();
        return true;
      }
    };
    assertEquals("", NcExpression.compile("opaque + ''").evaluate(path -> opaque));
    assertEquals(Boolean.FALSE, NcExpression.compile("opaque == 'x'").evaluate(path -> opaque));
    assertFalse(NcExpression.truth(opaque));
    assertEquals(0, access.get());
    assertNull(
        NcExpression.compile("opaque").evaluate(path -> Collections.singletonMap("key", "value")));
  }

  @Test
  void allNumericResultsRemainFiniteAndDivisionByZeroIsDefined() {
    for (String source : new String[] {"1 / 0", "1 % 0", "1e200 * 1e200", "1e308 + 1e308"}) {
      assertEquals(0d, value(source), source);
    }
    for (Object source :
        new Object[] {Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      assertEquals(0d, NcExpression.compile("bound").evaluate(path -> source));
      assertFalse(NcExpression.truth(source));
    }
    assertEquals(0d, value("'NaN' * 1"));
    assertEquals(Boolean.TRUE, value("-0 >= 0"));
    assertEquals(Boolean.FALSE, value("-0 < 0"));
    assertFalse(NcExpression.truth(null));
    assertFalse(NcExpression.truth(""));
    assertTrue(NcExpression.truth("false"));
  }

  @Test
  void limitsApplyToSourceNestingNodeCountArgumentsAndOutput() {
    StringBuilder large = new StringBuilder();
    for (int i = 0; i <= NcExpression.MAX_SOURCE_LENGTH; i++) large.append(' ');
    assertThrows(IllegalArgumentException.class, () -> NcExpression.compile(large.toString()));
    String deep = "1";
    for (int i = 0; i < 25; i++) deep = "(" + deep + ")";
    final String nested = deep;
    assertThrows(IllegalArgumentException.class, () -> NcExpression.compile(nested));
    StringBuilder unary = new StringBuilder();
    for (int i = 0; i < 25; i++) unary.append('!');
    unary.append("true");
    assertThrows(IllegalArgumentException.class, () -> NcExpression.compile(unary.toString()));
    StringBuilder manyNodes = new StringBuilder("max(");
    for (int i = 0; i < 16; i++) {
      if (i != 0) manyNodes.append(',');
      manyNodes.append("max(1,2,3,4,5,6,7,8)");
    }
    manyNodes.append(')');
    assertThrows(IllegalArgumentException.class, () -> NcExpression.compile(manyNodes.toString()));
    assertThrows(IllegalArgumentException.class,
        () -> NcExpression.compile("min(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17)"));
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 5000; i++) text.append('a');
    Object result = NcExpression.compile("text + text").evaluate(path -> text.toString());
    assertEquals(4096, ((String) result).length());
  }

  @Test
  void cachedCompilationKeepsResolverStateOutsideTheImmutableAst() {
    NcExpression.Expression expression = NcExpression.compile("value + 2");
    assertSame(expression, NcExpression.compile("value + 2"));
    assertEquals(5d, expression.evaluate(path -> 3));
    assertEquals(10d, expression.evaluate(path -> 8));
    for (int i = 0; i <= NcExpression.MAX_CACHE_ENTRIES; i++)
      NcExpression.compile("value + " + i);
    assertEquals(5d, expression.evaluate(path -> 3));
    assertThrows(IllegalArgumentException.class, () -> expression.evaluate(null));
  }
}
