package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SaturationMathTest {
  @Test
  void neutralIsAnExactPackedColorIdentity() {
    for (int i = 0; i < 256; i++) {
      int color = (i << 24) | (i << 16) | ((255 - i) << 8) | ((i * 17) & 255);
      assertEquals(color, SaturationMath.apply(color, 1));
    }
  }
  @Test
  void grayscaleUsesSharedLumaAndPreservesAlpha() {
    int out = SaturationMath.apply(0x7fff0000, 0);
    assertEquals(0x7f, out >>> 24);
    assertEquals(54, out & 255);
    assertEquals(out & 255, (out >>> 8) & 255);
    assertEquals(out & 255, (out >>> 16) & 255);
  }
  @Test
  void grayIsUnchangedAndVividResultsRemainBounded() {
    for (int i = 0; i < 256; i++) {
      int gray = 0xff000000 | i << 16 | i << 8 | i;
      assertEquals(gray, SaturationMath.apply(gray, 2.5f));
    }
    float[] color = SaturationMath.apply(0.95f, 0.2f, 0.3f, 2.5f);
    for (float channel : color) assertTrue(channel >= 0 && channel <= 1);
  }
  @Test
  void allBackendsUseTheSameBundledMetrics() {
    FontAtlas font = FontAtlas.get();
    assertTrue(font.width("Настройки HUD", 14, false) > 50);
    assertEquals(
        font.width("Saturation", 14, true) * 2, font.width("Saturation", 28, true), 0.0001f);
    assertEquals(2048, font.atlasWidth());
  }
}
