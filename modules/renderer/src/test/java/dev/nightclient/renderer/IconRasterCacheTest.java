package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.api.Canvas;
import org.junit.jupiter.api.Test;

class IconRasterCacheTest {
  private static final class AtlasSpyCanvas implements Canvas {
    int iconQuads;
    int vectorCalls;
    String lastKey;
    long lastRev;
    int[] lastPixels;
    int lastTexW, lastTexH;
    float lastX, lastY, lastW, lastH;
    int lastArgb;
    final float density;

    AtlasSpyCanvas(float density) {
      this.density = density;
    }

    @Override
    public boolean supportsIconAtlas() {
      return true;
    }

    @Override
    public float iconPixelDensity() {
      return density;
    }

    @Override
    public void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float x, float y, float width, float height, int argb) {
      iconQuads++;
      lastKey = cacheKey;
      lastRev = revision;
      lastPixels = pixels;
      lastTexW = texWidth;
      lastTexH = texHeight;
      lastX = x;
      lastY = y;
      lastW = width;
      lastH = height;
      lastArgb = argb;
    }

    @Override
    public void rect(float x, float y, float w, float h, int c) {
      vectorCalls++;
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      vectorCalls++;
    }

    @Override
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      vectorCalls++;
    }

    @Override
    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      vectorCalls++;
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float t, int c) {
      vectorCalls++;
    }

    @Override
    public void circle(float cx, float cy, float r, int c) {
      vectorCalls++;
    }

    @Override
    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {
      vectorCalls++;
    }

    @Override
    public void text(String v, float x, float y, float s, int c, boolean b) {}

    @Override
    public float textWidth(String v, float s, boolean b) {
      return 0;
    }

    @Override
    public void pushClip(float x, float y, float w, float h) {}

    @Override
    public void popClip() {}

    @Override
    public void item(String id, float x, float y, float s, float a) {}

    @Override
    public void backdrop(float x, float y, float w, float h, float r, float b, int q, float a) {}

    @Override
    public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}
  }

  @Test
  void rasterizesAllIconsIntoHaloFreeSupersampledQuadsWithCleanBorders() {
    IconRasterCache.clear();
    AtlasSpyCanvas spy = new AtlasSpyCanvas(2.0f);
    for (String id : Icons.ids()) {
      spy.iconQuads = 0;
      spy.vectorCalls = 0;
      Icons.draw(spy, id, 10f, 20f, 12f, 0xFF60A5FA, 1.5f);
      assertEquals(1, spy.iconQuads, id + " must draw via a single icon quad");
      assertEquals(0, spy.vectorCalls, id + " must not fall back to jagged vector primitives");
      assertEquals(0xFF60A5FA, spy.lastArgb);
      assertTrue(spy.lastTexW >= 28 && spy.lastTexW == spy.lastTexH,
          id + " texture must be square and include padding");
      assertTrue(spy.lastW > 12f && spy.lastX < 10f,
          id + " screen quad must expand symmetrically by padViewBox");
      assertEquals(10f + 6f, spy.lastX + spy.lastW * 0.5f, 1e-4f, id + " center X must stay exact");
      assertEquals(20f + 6f, spy.lastY + spy.lastH * 0.5f, 1e-4f, id + " center Y must stay exact");

      int visible = 0;
      int antialiased = 0;
      int w = spy.lastTexW;
      for (int y = 0; y < w; y++) {
        for (int x = 0; x < w; x++) {
          int px = spy.lastPixels[y * w + x];
          assertEquals(0x00FFFFFF, px & 0x00FFFFFF,
              id + " pixel RGB must stay pure white for halo-free GL_LINEAR");
          int a = (px >>> 24) & 0xFF;
          if (a > 0) visible++;
          if (a > 0 && a < 255) antialiased++;
          if (x == 0 || y == 0 || x == w - 1 || y == w - 1) {
            assertEquals(0, a, id + " outer 1px border must be transparent for CLAMP_TO_EDGE");
          }
        }
      }
      assertTrue(visible > 8, id + " must contain visible rasterized icon pixels");
      assertTrue(antialiased > 0, id + " must have smooth SSAA partial-coverage edge pixels");
    }
  }

  @Test
  void cachesByIdPhysicalSizeAndDynamicStrokeWidth() {
    IconRasterCache.clear();
    AtlasSpyCanvas spy2x = new AtlasSpyCanvas(2.0f);
    AtlasSpyCanvas spy3x = new AtlasSpyCanvas(3.0f);

    Icons.draw(spy2x, "search", 0f, 0f, 12f, 0xFFFFFFFF, 1.5f);
    String keyA = spy2x.lastKey;
    int[] pixelsA = spy2x.lastPixels;

    Icons.draw(spy2x, "search", 40f, 50f, 12f, 0xFFFF0000, 1.5f);
    assertEquals(keyA, spy2x.lastKey);
    assertTrue(pixelsA == spy2x.lastPixels, "Cache hit must reuse exact same pixel array");

    Icons.draw(spy2x, "search", 0f, 0f, 12f, 0xFFFFFFFF, 2.2f);
    assertNotEquals(keyA, spy2x.lastKey);

    Icons.draw(spy3x, "search", 0f, 0f, 12f, 0xFFFFFFFF, 1.5f);
    assertNotEquals(keyA, spy3x.lastKey);
    assertTrue(spy3x.lastTexW > spy2x.lastTexW);
  }
}
