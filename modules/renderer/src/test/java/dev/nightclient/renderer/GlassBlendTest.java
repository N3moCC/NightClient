package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GlassBlendTest {
  @Test
  void sourceOverFactorsMatchOneSampleAndTintMixAcrossTheirEntireDomain() {
    for (int ai = 0; ai <= 20; ai++)
      for (int mi = 0; mi <= 20; mi++) {
        float a = ai / 20f, m = mi / 20f, total = a * (1 - m + m * a);
        float tint = GlassBlend.tintOpacity(a, m), sample = GlassBlend.sampleOpacity(a, m);
        assertEquals(total * m, tint, 1e-6);
        assertEquals(total * (1 - m), (1 - tint) * sample, 1e-6);
        assertEquals(1 - total, (1 - tint) * (1 - sample), 1e-6);
        assertTrue(tint >= 0 && tint <= 1 && sample >= 0 && sample <= 1);
      }
  }
  @Test
  void opaqueTintNeedsNoSceneSampleAndPureSampleNeedsNoTint() {
    assertEquals(0, GlassBlend.sampleOpacity(1, 1));
    assertEquals(1, GlassBlend.tintOpacity(1, 1));
    assertEquals(.4f, GlassBlend.sampleOpacity(.4f, 0), 1e-6);
    assertEquals(0, GlassBlend.tintOpacity(.4f, 0));
    assertEquals(0, GlassBlend.alpha(Float.NaN, .5f));
  }
}
