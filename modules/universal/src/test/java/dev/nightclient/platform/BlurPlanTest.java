package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BlurPlanTest {
  @Test
  void highQualityReconstructsNativePixelsAndIntegratesSymmetricSubpixels() {
    for (int quality = 1; quality <= 6; quality++) {
      assertEquals(quality >= 4, BlurPlan.fullResolution(quality));
      assertEquals(quality >= 4 ? 101 : 51, BlurPlan.size(101, 0, quality));
      assertEquals(quality >= 4 ? 77 : 39, BlurPlan.size(77, 0, quality));
    }
    assertEquals(1, BlurPlan.samples(4));
    assertEquals(4, BlurPlan.samples(5));
    assertEquals(9, BlurPlan.samples(6));
    assertEquals(8, BlurPlan.passes(128, 6));
    assertEquals(7, BlurPlan.passes(128, 3));
  }
  @Test
  void schedulesRemainBoundedForTinyOddAndExtremeInputs() {
    for (int quality = -9; quality <= 99; quality++)
      for (float radius : new float[] {.01f, .25f, 1, 4, 8, 24, 160, Float.MAX_VALUE}) {
        int levels = BlurPlan.levels(radius, quality);
        assertTrue(levels >= 1 && levels <= 5);
        assertTrue(BlurPlan.passes(radius, quality) >= 1 && BlurPlan.passes(radius, quality) <= 8);
        assertTrue(Float.isFinite(BlurPlan.offset(radius, levels, quality)));
        for (int size : new int[] {1, 3, 77, 101, 1600})
          for (int level = 0; level < levels; level++) {
            int target = BlurPlan.size(size, level, quality);
            assertTrue(target >= 1 && target <= size);
          }
      }
    for (float invalid :
        new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      assertEquals(0, BlurPlan.levels(invalid, 6));
      assertEquals(0, BlurPlan.passes(invalid, 6));
    }
    assertEquals(1, BlurPlan.quality(-100));
    assertEquals(6, BlurPlan.quality(100));
  }
}
