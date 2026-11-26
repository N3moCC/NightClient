package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrosshairPatternTest {
  @Test
  void defaultMaskMatchesTheGameResourceRatherThanAnApproximateVector() {
    CrosshairPattern p = new CrosshairPattern();
    assertEquals(15, p.size);
    int painted = 0;
    for (int y = 0; y < p.size; y++)
      for (int x = 0; x < p.size; x++) {
        boolean expected = x == 7 && y >= 3 && y <= 11 || y == 7 && x >= 3 && x <= 11;
        assertEquals(expected ? 0xffffffff : 0, p.pixel(x, y));
        if (expected) painted++;
      }
    assertEquals(17, painted);
  }

  @Test
  void FastPointerStrokeFillsThePathAndMirrorsBothPaintingAndErasing() {
    CrosshairPattern p = new CrosshairPattern(15);
    assertTrue(p.stroke(2, 3, 6, 3, 0x804488cc, true, true));
    for (int x = 2; x <= 6; x++) {
      assertEquals(0x804488cc, p.pixel(x, 3));
      assertEquals(0x804488cc, p.pixel(14 - x, 3));
      assertEquals(0x804488cc, p.pixel(x, 11));
      assertEquals(0x804488cc, p.pixel(14 - x, 11));
    }
    assertFalse(p.paint(2, 3, 0x804488cc, true, true));
    assertTrue(p.paint(2, 3, 0, true, true));
    assertEquals(0, p.pixel(2, 3));
    assertEquals(0, p.pixel(12, 11));
    assertFalse(p.paint(-1, 20, -1, true, true));
  }

  @Test
  void ResizingAndCopiesPreserveTheCenterAndDoNotShareMutablePixels() {
    CrosshairPattern p = new CrosshairPattern(15);
    p.paint(7, 7, 0xff123456, false, false);
    p.paint(6, 4, 0x80abcdef, false, false);
    p.resize(31);
    assertEquals(0xff123456, p.pixel(15, 15));
    assertEquals(0x80abcdef, p.pixel(14, 12));
    CrosshairPattern copy = p.copy();
    copy.clear();
    assertEquals(0xff123456, p.pixel(15, 15));
    assertEquals(0, copy.pixel(15, 15));
    p.resize(7);
    assertEquals(0xff123456, p.pixel(3, 3));
    assertEquals(0x80abcdef, p.pixel(2, 0));
  }

  @Test
  void malformedConfigIsBoundedAndTransparentPixelsStayEmpty() {
    Settings s = new Settings();
    s.crosshair.pattern.size = Integer.MAX_VALUE;
    s.crosshair.pattern.pixels = new int[] {0x00112233, 0x804488cc};
    s.crosshair.pixelScale = Float.NaN;
    s.crosshair.offsetX = Integer.MAX_VALUE;
    s.crosshair.offsetY = Integer.MIN_VALUE;
    s.crosshair.colorMode = "missing";
    s.validate();
    assertEquals(31, s.crosshair.pattern.size);
    assertEquals(961, s.crosshair.pattern.pixels.length);
    assertEquals(0, s.crosshair.pattern.pixel(0, 0));
    assertEquals(0x804488cc, s.crosshair.pattern.pixel(1, 0));
    assertEquals(1, s.crosshair.pixelScale);
    assertEquals(64, s.crosshair.offsetX);
    assertEquals(-64, s.crosshair.offsetY);
    assertEquals("vanilla", s.crosshair.colorMode);
  }

  @Test
  void drawingRgbaSurvivesPersistenceAndLegacyProfilesKeepTheirExistingShape() throws Exception {
    Path folder = Files.createTempDirectory("night-crosshair");
    try (ConfigStore store = new ConfigStore(folder)) {
      Settings s = new Settings();
      s.crosshair.pattern.clear();
      s.crosshair.colorMode = "pixels";
      s.crosshair.pattern.paint(7, 5, 0x804488cc, false, false);
      Settings loaded = store.decode(store.encode(s));
      assertEquals("pixels", loaded.crosshair.mode);
      assertEquals("pixels", loaded.crosshair.colorMode);
      assertEquals(0x804488cc, loaded.crosshair.pattern.pixel(7, 5));
      assertEquals(0, loaded.crosshair.pattern.pixel(7, 7));
      Settings old = store.decode(
          "{\"crosshair\":{\"preset\":\"diamond\",\"color\":-16711936,\"inheritColor\":false}}");
      assertEquals("shape", old.crosshair.mode);
      assertEquals("custom", old.crosshair.colorMode);
      assertEquals("diamond", old.crosshair.preset);
      assertEquals(0xff00ff00, old.crosshair.color);
      assertFalse(old.crosshair.inheritColor);
    }
  }
}
