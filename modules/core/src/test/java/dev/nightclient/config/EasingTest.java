package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.skin.NcParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class EasingTest {
  private static final String SIDEBAR = "cubic-bezier(0.23,1,0.32,1)";

  @Test
  void cssTimingSolvesTimeAxisAndKeepsExactEndpoints() {
    assertEquals(.875f, Easing.cubicBezier(.33125f, SIDEBAR), .00001f);
    assertEquals(.5f, Easing.cubicBezier(.5f, "cubic-bezier(.42,0,.58,1)"), .00001f);
    for (float progress : new float[] {0, .1f, .5f, .9f, 1})
      assertEquals(progress, Easing.cubicBezier(progress, "cubic-bezier(0,0,1,1)"), .00001f);
    assertEquals(0, Easing.cubicBezier(-1, SIDEBAR));
    assertEquals(1, Easing.cubicBezier(2, SIDEBAR));
    assertEquals(0, Easing.cubicBezier(Float.NaN, SIDEBAR));
  }

  @Test
  void validatesCssNumbersAndMonotonicTimeControlPoints() {
    for (String curve : new String[] {SIDEBAR, "cubic-bezier(0, -1, 1, 2)",
             "cubic-bezier(+.42, 0e0, .58, 1.)", "linear", "back-out"})
      assertTrue(Easing.supported(curve), curve);
    for (String curve : new String[] {"cubic-bezier(0,0,1,1,)", "cubic-bezier(0,0,1)",
             "cubic-bezier(0f,0,1,1)", "cubic-bezier(0x0p0,0,1,1)", "cubic-bezier(-.01,0,1,1)",
             "cubic-bezier(0,0,1.01,1)", "cubic-bezier(0,NaN,1,1)", "cubic-bezier(0,Infinity,1,1)",
             "cubic-bezier(0,1e20,1,1)", "missing"})
      assertFalse(Easing.supported(curve), curve);
    assertFalse(Easing.supported(null));
  }

  @Test
  void stylesheetAndSavedSettingsRetainValidCurveAndRejectInvalidCurve() throws Exception {
    assertDoesNotThrow(() -> NcParser.parse("menu { easing='" + SIDEBAR + "' }"));
    assertThrows(
        IOException.class, () -> NcParser.parse("menu { easing='cubic-bezier(-1,0,1,1)' }"));
    MotionSettings motion = Theme.defaults().motion.copy();
    motion.easing = SIDEBAR;
    motion.validate();
    assertEquals(SIDEBAR, motion.easing);
    assertEquals(SIDEBAR, motion.copy().easing);
    motion.easing = "cubic-bezier(0,0,2,1)";
    motion.validate();
    assertEquals(Theme.defaults().motion.easing, motion.easing);
  }
}
