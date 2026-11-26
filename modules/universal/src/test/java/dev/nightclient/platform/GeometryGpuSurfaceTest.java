package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.renderer.RoundedQuadMesh;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryGpuSurfaceTest {
  @Test
  void analyticAndFallbackBatchesKeepPainterOrderAndClips() {
    Capture c = new Capture();
    c.roundedRect(10, 10, 80, 30, 8, 0x8060a0ff);
    c.strokeRoundedRect(10, 10, 80, 30, 8, 1, 0xffffffff);
    c.rect(2, 2, 4, 4, 0xffffffff);
    c.pushClip(20, 20, 30, 30);
    c.gradientRoundedRect(20, 20, 30, 20, 4, 0xff000000, 0xffffffff);
    c.popClip();
    c.finish();
    assertEquals(Arrays.asList("gpu:108:none", "flat:6:none", "gpu:54:clip"), c.commands);
    assertEquals(20, c.clipped[0]);
    assertEquals(50, c.clipped[2]);
  }
  @Test
  void asymmetricAndVeryThickBordersKeepAnExplicitPortableFallback() {
    Capture c = new Capture();
    c.roundedRectCorners(0, 0, 80, 30, 0, 4, 8, 12, 0xffffffff);
    c.strokeRoundedRect(0, 0, 80, 30, 2, 4, 0xffffffff);
    c.finish();
    assertEquals(1, c.commands.size());
    assertTrue(c.commands.get(0).startsWith("flat:"));
  }
  @Test
  void nineSliceMeshPartitionsBoundsWithoutOverlapAndKeepsSubpixelGradient() {
    float[] mesh = new float[RoundedQuadMesh.MAX_FLOATS];
    int length = RoundedQuadMesh.append(
        mesh, 0, 10.125f, 20.25f, 70, 30, 7, 0, 2, 0x80400000, 0x80004000, 0x80000040, 0x80404040);
    assertEquals(9 * 6 * 8, length);
    double area = 0;
    for (int i = 0; i < length; i += 24) {
      double cross = (mesh[i + 8] - mesh[i]) * (mesh[i + 17] - mesh[i + 1])
          - (mesh[i + 9] - mesh[i + 1]) * (mesh[i + 16] - mesh[i]);
      assertTrue(cross > 0);
      area += cross / 2;
    }
    assertEquals((70 + .75) * (30 + .75), area, .002,
        "Nine cells cover the expanded rectangle exactly once");
    for (int i = 0; i < length; i += 8) {
      assertEquals(128 / 255f, mesh[i + 7], .00001);
      for (int j = 0; j < 8; j++) assertTrue(Float.isFinite(mesh[i + j]));
    }
  }
  @Test
  void pillDegeneratesToFourCellsAndInvisibleClipEmitsNoGpuCommand() {
    float[] mesh = new float[RoundedQuadMesh.MAX_FLOATS];
    assertEquals(4 * 6 * 8,
        RoundedQuadMesh.append(
            mesh, 0, 10, 20, 20, 20, 10, 1, 1, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff));
    Capture c = new Capture();
    c.pushClip(10, 10, 0, 10);
    c.roundedRect(0, 0, 30, 30, 6, 0xffffffff);
    c.popClip();
    c.finish();
    assertTrue(c.commands.isEmpty());
  }
  private static final class Capture extends GeometryCanvas {
    final List<String> commands = new ArrayList<String>();
    float[] clipped;
    Capture() {
      super(100, 100);
    }
    protected boolean usesGpuRounded() {
      return true;
    }
    protected void drawRounded(float[] v, int length, float[] clip) {
      commands.add("gpu:" + length / 8 + ":" + (clip == null ? "none" : "clip"));
      if (clip != null) clipped = clip.clone();
    }
    protected void draw(float[] v, int length, float[] clip) {
      commands.add("flat:" + length / 6 + ":" + (clip == null ? "none" : "clip"));
    }
    protected void glyph(
        float x, float y, float w, float h, int px, int py, int pw, int ph, int c) {}
    protected void drawItem(String id, float x, float y, float size, float alpha) {}
    protected void drawBackdrop(float x, float y, float w, float h, float r, float blur,
        int quality, float opacity, float[] clip) {}
    protected void drawTexture(String id, float x, float y, float w, float h, float r, float tile,
        int color, float[] clip) {}
  }
}
