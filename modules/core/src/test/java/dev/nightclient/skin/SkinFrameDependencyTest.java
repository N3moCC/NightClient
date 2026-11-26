package dev.nightclient.skin;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkinFrameDependencyTest {
  @Test
  void literalPresentationSourcesAreTrackedWithoutChangingExpressionIdentifiers() {
    NcExpression.Expression expression = NcExpression.compile(
        "width + maxTextWidth('rows.visible','name',7) + retainedCount('cooldownRows')");
    assertEquals(java.util.Collections.singleton("width"), expression.identifiers());
    assertTrue(expression.contextPaths().containsAll(
        java.util.Arrays.asList("width", "rows.visible", "cooldownRows")));
    assertTrue(expression.fixedPresentationSources());
  }
  @Test
  void dynamicSourcePathsConservativelyDisableFrameSharing() {
    assertFalse(NcExpression.compile("retainedCount(sourceName)").fixedPresentationSources());
    assertFalse(NcExpression.compile("false ? maxTextWidth(sourceName,'name',7) : 0")
            .fixedPresentationSources());
    assertTrue(NcExpression.compile("textWidth(row.name,7)").fixedPresentationSources());
  }
}
