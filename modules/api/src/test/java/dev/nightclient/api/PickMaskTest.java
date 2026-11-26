package dev.nightclient.api;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
final class PickMaskTest {
  @Test
  void transparentHolesRemainUnselectableAtScaledCoordinates() {
    ByteBuffer rgba = ByteBuffer.allocate(4 * 3 * 3);
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 3; x++)
        if (x != 1 || y != 1) rgba.put((x + y * 3) * 4 + 3, (byte) 255);
    PickMask m = PickMask.fromRgba(rgba, 3, 3, 30, 30, false);
    assertTrue(m.contains(5, 5));
    assertFalse(m.contains(15, 15));
    assertEquals(8, m.pixelCount);
    assertEquals(30, m.boundsWidth);
    assertEquals(15, m.centerX);
    assertFalse(m.contains(30, 5));
    assertFalse(m.contains(Float.NaN, 5));
  }
  @Test
  void gpuBottomUpImageAndPartialAlphaArePreserved() {
    ByteBuffer rgba = ByteBuffer.allocate(4 * 2 * 2);
    rgba.put(3, (byte) 1);
    PickMask m = PickMask.fromRgba(rgba, 2, 2, 20, 20, true);
    assertTrue(m.contains(5, 15));
    assertFalse(m.contains(5, 5));
    assertEquals(1, m.pixelCount);
  }
  @Test
  void constructorCopiesInputAndMissCarriesRevision() {
    long[] bits = {1};
    PickMask m = new PickMask(1, 1, 1, 1, bits);
    bits[0] = 0;
    assertTrue(m.contains(0, 0));
    assertFalse(ViewmodelSelection.missed(5).selected());
    assertEquals(5, ViewmodelSelection.missed(5).revision);
  }
}
