package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryBatchTest {
  @Test
  void largeScenePreservesWholeTrianglesOrderingAndClipAcrossBatches() {
    Capture c = new Capture();
    c.pushClip(10, 20, 90, 80);
    for (int i = 0; i < 10000; i++) c.rect(i, 30, 1, 2, 0xff123456);
    c.item("minecraft:stone", 20, 30, 16, 1);
    c.popClip();
    c.rect(8, 8, 2, 2, 0xffffffff);
    c.finish();
    assertTrue(c.batches.size() > 2);
    assertEquals(10000 * 36 + 36, c.total);
    int rectangle = 0;
    for (int batch = 0; batch < c.batches.size() - 1; batch++) {
      float[] data = c.batches.get(batch);
      assertEquals(0, data.length % 18);
      assertTrue(data.length <= 65520);
      assertArrayEquals(new float[] {10, 20, 100, 100}, c.clips.get(batch));
      for (int i = 0; i < data.length; i += 36) {
        assertEquals(rectangle++, data[i]);
        assertEquals(30, data[i + 1]);
      }
    }
    assertEquals(10000, rectangle);
    assertNull(c.clips.get(c.clips.size() - 1));
    assertEquals("item", c.events.get(c.events.size() - 2));
    assertEquals("geometry", c.events.get(c.events.size() - 1));
  }
  @Test
  void nestedAndSuccessiveCanvasesNeverOverwriteDeferredGeometry() {
    Capture first = new Capture();
    first.rect(1, 2, 3, 4, 0xff123456);
    Capture nested = new Capture();
    nested.rect(10, 20, 30, 40, 0xffabcdef);
    nested.finish();
    float[] snapshot = nested.batches.get(0).clone();
    first.finish();
    Capture next = new Capture();
    next.rect(100, 200, 300, 400, 0xff010203);
    next.finish();
    assertArrayEquals(snapshot, nested.batches.get(0));
    assertEquals(1, first.batches.get(0)[0]);
    assertEquals(100, next.batches.get(0)[0]);
    int calls = next.events.size();
    next.finish();
    assertEquals(calls, next.events.size());
  }
  private static final class Capture extends GeometryCanvas {
    final List<float[]> batches = new ArrayList<float[]>(), clips = new ArrayList<float[]>();
    final List<String> events = new ArrayList<String>();
    int total;
    Capture() {
      super(1000, 700);
    }
    protected void draw(float[] v, int length, float[] clip) {
      batches.add(Arrays.copyOf(v, length));
      clips.add(clip == null ? null : clip.clone());
      total += length;
      events.add("geometry");
    }
    protected void glyph(
        float x, float y, float w, float h, int ax, int ay, int aw, int ah, int color) {}
    protected void drawItem(String id, float x, float y, float size, float alpha) {
      events.add("item");
    }
    protected void drawBackdrop(
        float x, float y, float w, float h, float r, float b, int q, float a, float[] clip) {}
    protected void drawTexture(String id, float x, float y, float w, float h, float r, float tile,
        int color, float[] clip) {}
  }
}
