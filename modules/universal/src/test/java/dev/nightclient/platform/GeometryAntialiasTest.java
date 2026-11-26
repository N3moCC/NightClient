package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GeometryAntialiasTest {
  @Test
  void diagonalSvgStrokeHasTransparentPerimeterAndKeepsItsRequestedColor() {
    Capture c = new Capture(1);
    c.line(10, 10, 30, 30, 1.8f, 0x804060a0);
    c.finish();
    assertTrue(c.used > 36, "SVG strokes need an interpolated edge, not a hard quad");
    boolean core = false, fringe = false;
    for (int i = 0; i < c.used; i += 6) {
      assertEquals(64 / 255f, c.data[i + 2], .00001f);
      assertEquals(96 / 255f, c.data[i + 3], .00001f);
      assertEquals(160 / 255f, c.data[i + 4], .00001f);
      core |= Math.abs(c.data[i + 5] - 128 / 255f) < .00001f;
      fringe |= c.data[i + 5] == 0;
    }
    assertTrue(core);
    assertTrue(fringe);
    for (int i = 0; i < c.used; i += 18) {
      float area = (c.data[i + 6] - c.data[i]) * (c.data[i + 13] - c.data[i + 1])
          - (c.data[i + 7] - c.data[i + 1]) * (c.data[i + 12] - c.data[i]);
      assertTrue(area >= -.0001f, "Native GUI culling must preserve AA triangles");
    }
  }
  @Test
  void antialiasFringeTracksPhysicalPixelsInsteadOfGrowingWithGuiScale() {
    Capture one = new Capture(1), three = new Capture(3);
    one.line(10, 10, 30, 10, 2, 0xffffffff);
    three.line(10, 10, 30, 10, 2, 0xffffffff);
    one.finish();
    three.finish();
    assertEquals(.65f, one.maxY() - 11, .00001f);
    assertEquals(.65f, (three.maxY() - 11) * 3, .00001f);
  }
  @Test
  void transparentOrEmptySvgStrokeCreatesNoCommands() {
    Capture c = new Capture(1);
    c.line(1, 1, 1, 1, 2, 0xffffffff);
    c.line(1, 1, 2, 2, 0, 0xffffffff);
    c.line(1, 1, 2, 2, 2, 0x00ffffff);
    c.finish();
    assertEquals(0, c.used);
  }
  private static final class Capture extends GeometryCanvas {
    final float density;
    float[] data;
    int used;
    Capture(float density) {
      super(100, 100);
      this.density = density;
    }
    protected float rasterDensity() {
      return density;
    }
    protected void draw(float[] vertices, int length, float[] clip) {
      data = java.util.Arrays.copyOf(vertices, length);
      used = length;
    }
    float maxY() {
      float max = Float.NEGATIVE_INFINITY;
      for (int i = 0; i < used; i += 6) max = Math.max(max, data[i + 1]);
      return max;
    }
    protected void glyph(
        float x, float y, float w, float h, int px, int py, int pw, int ph, int color) {}
    protected void drawItem(String id, float x, float y, float size, float alpha) {}
    protected void drawBackdrop(float x, float y, float w, float h, float radius, float blurRadius,
        int quality, float opacity, float[] clip) {}
    protected void drawTexture(String id, float x, float y, float w, float h, float radius,
        float tileSize, int argb, float[] clip) {}
  }
}
