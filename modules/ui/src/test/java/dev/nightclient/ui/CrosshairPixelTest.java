package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrosshairPixelTest {
  static final class Pixel {
    final float x, y, w, h, alpha;
    final int color;
    final boolean inverse;
    Pixel(float x, float y, float w, float h, int c, float a, boolean inverse) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      color = c;
      alpha = a;
      this.inverse = inverse;
    }
  }
  static class Sink implements Canvas {
    final List<Pixel> pixels = new ArrayList<Pixel>();
    public void rect(float x, float y, float w, float h, int c) {
      pixels.add(new Pixel(x, y, w, h, c, (c >>> 24) / 255f, false));
    }
    public void invertRect(float x, float y, float w, float h, float a) {
      pixels.add(new Pixel(x, y, w, h, 0, a, true));
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      fail("A pixel drawing must keep sharp square pixels");
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      fail("Unexpected vector");
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      fail("Unexpected gradient");
    }
    public void line(float x, float y, float xx, float yy, float t, int c) {
      fail("Unexpected vector");
    }
    public void circle(float x, float y, float r, int c) {
      fail("Unexpected vector");
    }
    public void text(String v, float x, float y, float s, int c, boolean b) {
      fail("Unexpected label");
    }
    public float textWidth(String v, float s, boolean b) {
      return 0;
    }
    public void pushClip(float x, float y, float w, float h) {}
    public void popClip() {}
    public void item(String id, float x, float y, float s, float a) {
      fail("Unexpected item");
    }
    public void backdrop(float x, float y, float w, float h, float r, float blur, int q, float a) {
      fail("Reticle must not capture UI blur");
    }
    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
      fail("Pixel reticle must not sample a filtered texture");
    }
  }
  private static Sink render(Settings s, int width, int height) {
    s.crosshair.enabled = true;
    Sink sink = new Sink();
    FeatureHudRenderer.render(sink, s, null, null, width, height, 0, false);
    return sink;
  }

  @Test
  void vanillaMaskUsesDestinationInversionAtTheActualGamePixelCenter() {
    Settings s = new Settings();
    Sink sink = render(s, 901, 601);
    assertEquals(9, sink.pixels.size());
    int area = 0;
    for (Pixel p : sink.pixels) {
      assertTrue(p.inverse);
      assertEquals(1, p.alpha);
      assertEquals(1, p.h);
      assertEquals(Math.round(p.x), p.x);
      area += Math.round(p.w);
    }
    assertEquals(17, area);
    Pixel horizontal = sink.pixels.get(4);
    assertEquals(446, horizontal.x);
    assertEquals(300, horizontal.y);
    assertEquals(9, horizontal.w);
  }

  @Test
  void evenViewportUsesTheNativeOddSpriteIntegerDivision() {
    Settings s = new Settings();
    Sink sink = render(s, 900, 600);
    Pixel horizontal = sink.pixels.get(4);
    assertEquals((900 - 15) / 2 + 3, horizontal.x);
    assertEquals((600 - 15) / 2 + 7, horizontal.y);
    assertEquals(9, horizontal.w);
    assertEquals(1, horizontal.alpha);
  }

  @Test
  void uneditedPresetCallsNativeSpriteAndEditedCellsRemainTheDrawing() {
    Settings s = new Settings();
    s.crosshair.enabled = true;
    final List<float[]> calls = new ArrayList<float[]>();
    Sink sink = new Sink() {
      @Override
      public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
        calls.add(new float[] {x, y, size, opacity});
        return true;
      }
    };
    FeatureHudRenderer.render(sink, s, null, null, 900, 600, 0, false);
    assertEquals(1, calls.size());
    assertEquals(442, calls.get(0)[0]);
    assertEquals(292, calls.get(0)[1]);
    assertEquals(15, calls.get(0)[2]);
    assertTrue(sink.pixels.isEmpty());
    s.crosshair.pattern.paint(0, 0, -1, false, false);
    FeatureHudRenderer.render(sink, s, null, null, 900, 600, 0, false);
    assertEquals(1, calls.size());
    assertFalse(sink.pixels.isEmpty());
  }

  @Test
  void CustomColorMultipliesCellCoverageAndUserAlphaOnce() {
    Settings s = new Settings();
    s.crosshair.pattern.clear();
    s.crosshair.colorMode = "custom";
    s.crosshair.color = 0x804488cc;
    s.crosshair.opacity = .5f;
    s.crosshair.pattern.paint(7, 7, 0x80ff00ff, false, false);
    s.theme.backgroundOpacity = 0;
    s.theme.accent = 0xffff0000;
    Sink sink = render(s, 900, 600);
    assertEquals(1, sink.pixels.size());
    Pixel p = sink.pixels.get(0);
    assertFalse(p.inverse);
    assertEquals(0x204488cc, p.color);
  }

  @Test
  void individualRgbaCellsStayDistinctAndEmptyDrawingReallyHidesTheReticle() {
    Settings s = new Settings();
    s.crosshair.pattern.clear();
    s.crosshair.colorMode = "pixels";
    s.crosshair.pattern.paint(7, 7, 0x80ff0000, false, false);
    s.crosshair.pattern.paint(8, 7, 0xff00ff00, false, false);
    Sink sink = render(s, 900, 600);
    assertEquals(2, sink.pixels.size());
    assertEquals(0x80ff0000, sink.pixels.get(0).color);
    assertEquals(0xff00ff00, sink.pixels.get(1).color);
    s.crosshair.pattern.clear();
    assertTrue(render(s, 900, 600).pixels.isEmpty());
    s.crosshair.pattern.preset("vanilla");
    s.crosshair.opacity = 0;
    assertTrue(render(s, 900, 600).pixels.isEmpty());
  }

  @Test
  void CellScaleAndOffsetsAffectTheDrawingAndItsEditableBoundsTogether() {
    Settings s = new Settings();
    s.crosshair.pattern.clear();
    s.crosshair.pattern.paint(7, 7, -1, false, false);
    s.crosshair.pixelScale = 3;
    s.crosshair.offsetX = 6;
    s.crosshair.offsetY = -4;
    Pixel pixel = render(s, 900, 600).pixels.get(0);
    assertEquals(454, pixel.x);
    assertEquals(294, pixel.y);
    assertEquals(3, pixel.w);
    assertEquals(3, pixel.h);
    float[] bounds = FeatureHudRenderer.crosshairBounds(s, 900, 600);
    assertTrue(pixel.x >= bounds[0] && pixel.x + pixel.w <= bounds[0] + bounds[2]);
    assertTrue(pixel.y >= bounds[1] && pixel.y + pixel.h <= bounds[1] + bounds[3]);
    assertEquals(3, bounds[4]);
  }
}
