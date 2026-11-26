package dev.nightclient.renderer;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
class WorldRibbonTest {
  static final class Sink implements WorldRibbon.Quads {
    final List<double[]> quads = new ArrayList<double[]>();
    final List<Integer> colors = new ArrayList<Integer>();
    public void quad(double ax, double ay, double az, double bx, double by, double bz, double cx,
        double cy, double cz, double dx, double dy, double dz, int color) {
      quads.add(new double[] {ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz});
      colors.add(color);
    }
  }
  private static Sink line(double depth, int height) {
    Sink sink = new Sink();
    WorldRibbon.line(sink,
        new WorldRibbon.View(0, 0, 0, 0, 0, 70, 16 / 9f, height * 16 / 9, height), -1, 0, depth, 1,
        0, depth, 2, 0xffa99aff);
    return sink;
  }
  @Test
  void samePhysicalWidthAtDifferentDistanceAndFramebufferScale() {
    Sink near = line(2, 900), far = line(20, 900), dense = line(2, 1800);
    assertEquals(9, near.quads.size());
    double nearWidth = Math.abs(near.quads.get(0)[10] - near.quads.get(0)[1]);
    double farWidth = Math.abs(far.quads.get(0)[10] - far.quads.get(0)[1]);
    double denseWidth = Math.abs(dense.quads.get(0)[10] - dense.quads.get(0)[1]);
    assertEquals(nearWidth * 10, farWidth, 1e-10);
    assertEquals(nearWidth * .5, denseWidth, 1e-10);
  }
  @Test
  void realFringeCoverageAndRgbArePreserved() {
    Sink sink = line(4, 900);
    assertEquals(255, sink.colors.get(0) >>> 24);
    assertTrue((sink.colors.get(1) >>> 24) > 0);
    assertTrue((sink.colors.get(1) >>> 24) < 255);
    assertTrue((sink.colors.get(7) >>> 24) < (sink.colors.get(1) >>> 24));
    for (int color : sink.colors) assertEquals(0xa99aff, color & 0xffffff);
    for (double[] quad : sink.quads)
      for (double coordinate : quad) assertTrue(Double.isFinite(coordinate));
  }
  @Test
  void behindCameraAndDegenerateLinesEmitNothingAndNearCrossingIsFinite() {
    Sink sink = new Sink();
    WorldRibbon.View view = new WorldRibbon.View(0, 0, 0, 0, 0, 70, 16 / 9f, 1600, 900);
    WorldRibbon.line(sink, view, 0, 0, -2, 1, 0, -2, 2, -1);
    assertTrue(sink.quads.isEmpty());
    WorldRibbon.line(sink, view, 1, 1, 2, 1, 1, 2, 2, -1);
    assertTrue(sink.quads.isEmpty());
    WorldRibbon.line(sink, view, -1, 0, -1, 1, 0, 2, 2, -1);
    assertEquals(9, sink.quads.size());
    for (double[] quad : sink.quads)
      for (double coordinate : quad) assertTrue(Double.isFinite(coordinate));
  }
}
