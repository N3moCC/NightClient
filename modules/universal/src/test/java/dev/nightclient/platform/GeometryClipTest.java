package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GeometryClipTest {
  @Test
  void everyCommandSkipsZeroDisjointAndOffscreenClipsThenRestoresParent() {
    Capture c = new Capture();
    c.pushClip(12, 16, 80, 70);
    c.pushClip(40, 20, 0, 36);
    paint(c);
    c.popClip();
    c.pushClip(100, 20, 10, 36);
    paint(c);
    c.popClip();
    c.pushClip(400, 20, 10, 36);
    paint(c);
    c.popClip();
    c.pushClip(-100, -100, 10, 36);
    paint(c);
    c.popClip();
    assertEquals(0, c.shapes + c.glyphs + c.items + c.backdrops + c.textures + c.inversions);
    paint(c);
    c.popClip();
    c.finish();
    assertTrue(c.shapes > 0);
    assertTrue(c.glyphs > 0);
    assertEquals(1, c.items);
    assertEquals(1, c.backdrops);
    assertEquals(1, c.textures);
    assertEquals(1, c.inversions);
    assertArrayEquals(new float[] {12, 16, 92, 86}, c.lastClip);
  }
  @Test
  void fractionalPositiveClipRemainsVisibleToSharedBackend() {
    Capture c = new Capture();
    c.pushClip(10.1f, 20.2f, .1f, 36);
    paint(c);
    c.popClip();
    assertTrue(c.shapes > 0);
    assertTrue(c.glyphs > 0);
    assertEquals(1, c.items);
    assertTrue(c.lastClip[2] > c.lastClip[0]);
  }
  private static void paint(Capture c) {
    c.rect(0, 0, 200, 100, 0xffffffff);
    c.text("Я", 0, 0, 12, 0xffffffff, false);
    c.item("minecraft:stone", 0, 0, 16, 1);
    c.backdrop(0, 0, 200, 100, 4, 8, 1, 1);
    c.texture("nightclient:material", 0, 0, 200, 100, 4, 8, 0xffffffff);
    c.invertRect(0, 0, 10, 10, 1);
  }
  private static final class Capture extends GeometryCanvas {
    int shapes, glyphs, items, backdrops, textures, inversions;
    float[] lastClip;
    Capture() {
      super(320, 240);
    }
    protected void draw(float[] v, int n, float[] clip) {
      shapes++;
      lastClip = clip.clone();
    }
    protected void glyph(
        float x, float y, float w, float h, int ax, int ay, int aw, int ah, int color) {
      glyphs++;
    }
    protected void drawItem(String id, float x, float y, float size, float alpha) {
      items++;
    }
    protected void drawBackdrop(
        float x, float y, float w, float h, float r, float b, int q, float a, float[] clip) {
      backdrops++;
    }
    protected void drawTexture(String id, float x, float y, float w, float h, float r, float tile,
        int color, float[] clip) {
      textures++;
    }
    protected void drawInvertedRect(float x, float y, float w, float h, float a, float[] clip) {
      inversions++;
      lastClip = clip.clone();
    }
  }
}
