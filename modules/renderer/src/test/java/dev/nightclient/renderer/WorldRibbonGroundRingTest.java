package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldRibbonGroundRingTest {

  private static final class Quad {
    final double ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz;
    final int color;

    Quad(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy,
        double cz, double dx, double dy, double dz, int color) {
      this.ax = ax;
      this.ay = ay;
      this.az = az;
      this.bx = bx;
      this.by = by;
      this.bz = bz;
      this.cx = cx;
      this.cy = cy;
      this.cz = cz;
      this.dx = dx;
      this.dy = dy;
      this.dz = dz;
      this.color = color;
    }

    double normalY() {
      double abx = bx - ax, abz = bz - az;
      double adx = dx - ax, adz = dz - az;
      return abz * adx - abx * adz;
    }
  }

  @Test
  void segmentCountIsSymmetricAndMaintainsSubThreeMillimeterSagittaAcrossBlastRadii() {
    WorldRibbon.View view = new WorldRibbon.View(0, 66, -12, 12f, 0f, 70f, 16f / 9f, 1920, 1080);
    for (double radius : new double[] {1.5, 4.0, 8.0, 10.0, 12.0, 16.0, 24.0}) {
      int segs = WorldRibbon.groundRingSegments(view, 0, 64, 0, radius);
      assertTrue(segs >= 96 && segs <= 256, "segments out of bounds: " + segs);
      assertEquals(0, segs % 8, "segments must be 8-way symmetric: " + segs);
      double sagitta = radius * (1.0 - Math.cos(Math.PI / segs));
      assertTrue(sagitta < 0.003,
          "sagitta too large (" + sagitta + " blocks) for radius " + radius + " segs=" + segs);
    }
  }

  @Test
  void groundRingIsFlatMiteredClosedUpwardFacingAndSmoothlyAntiAliased() {
    WorldRibbon.View view =
        new WorldRibbon.View(50000.0, 66.2, -80012.0, 10f, 0f, 70f, 16f / 9f, 1920, 1080);
    double cx = 50000.0, cy = 64.06, cz = -80000.0, radius = 12.0;
    int segs = WorldRibbon.groundRingSegments(view, cx, cy, cz, radius);
    final List<Quad> quads = new ArrayList<Quad>();
    WorldRibbon.groundRing((ax, ay, az, bx, by, bz, cqx, cqy, cqz, dx, dy, dz, color) -> quads.add(
        new Quad(ax, ay, az, bx, by, bz, cqx, cqy, cqz, dx, dy, dz, color)),
        view, cx, cy, cz, radius, 2.0f, 0xD8FF4444, segs);

    assertEquals(5 * segs, quads.size());
    for (int band = 0; band < 5; band++) {
      int base = band * segs;
      Quad first = quads.get(base);
      double rIn = Math.hypot(first.ax - cx, first.az - cz);
      double rOut = Math.hypot(first.dx - cx, first.dz - cz);
      assertTrue(rOut > rIn, "band " + band + " must expand radially outward");
      for (int i = 0; i < segs; i++) {
        Quad q = quads.get(base + i);
        assertEquals(cy, q.ay, 0.0);
        assertEquals(cy, q.by, 0.0);
        assertEquals(cy, q.cy, 0.0);
        assertEquals(cy, q.dy, 0.0);
        assertEquals(rIn, Math.hypot(q.ax - cx, q.az - cz), 1e-7);
        assertEquals(rIn, Math.hypot(q.bx - cx, q.bz - cz), 1e-7);
        assertEquals(rOut, Math.hypot(q.cx - cx, q.cz - cz), 1e-7);
        assertEquals(rOut, Math.hypot(q.dx - cx, q.dz - cz), 1e-7);
        assertTrue(q.normalY() > 0.0, "quad must face +Y upward");
        Quad next = quads.get(base + ((i + 1) % segs));
        assertEquals(q.bx, next.ax, 1e-12);
        assertEquals(q.bz, next.az, 1e-12);
        assertEquals(q.cx, next.dx, 1e-12);
        assertEquals(q.cz, next.dz, 1e-12);
      }
    }
    int coreAlpha = quads.get(2 * segs).color >>> 24;
    int midAlpha = quads.get(3 * segs).color >>> 24;
    int farAlpha = quads.get(4 * segs).color >>> 24;
    assertEquals(0xD8, coreAlpha);
    assertTrue(midAlpha < coreAlpha && midAlpha > farAlpha && farAlpha > 0);
  }

  @Test
  void groundDiscMatchesRingAnglesWithNonDegenerateUpwardQuads() {
    double cx = 123.5, cy = 70.05, cz = -456.25, radius = 8.0;
    int segs = 128;
    final List<Quad> quads = new ArrayList<Quad>();
    WorldRibbon.groundDisc((ax, ay, az, bx, by, bz, cqx, cqy, cqz, dx, dy, dz, color) -> quads.add(
        new Quad(ax, ay, az, bx, by, bz, cqx, cqy, cqz, dx, dy, dz, color)),
        cx, cy, cz, radius, 0x38FF5533, segs);

    assertEquals(segs / 2, quads.size());
    for (int i = 0; i < quads.size(); i++) {
      Quad q = quads.get(i);
      assertEquals(cx, q.ax, 0.0);
      assertEquals(cz, q.az, 0.0);
      assertEquals(cy, q.ay, 0.0);
      assertEquals(radius, Math.hypot(q.bx - cx, q.bz - cz), 1e-9);
      assertEquals(radius, Math.hypot(q.cx - cx, q.cz - cz), 1e-9);
      assertEquals(radius, Math.hypot(q.dx - cx, q.dz - cz), 1e-9);
      double n1 = (q.bz - q.az) * (q.cx - q.ax) - (q.bx - q.ax) * (q.cz - q.az);
      double n2 = (q.cz - q.az) * (q.dx - q.ax) - (q.cx - q.ax) * (q.dz - q.az);
      assertTrue(n1 > 0.0 && n2 > 0.0, "disc wedge triangles must be non-degenerate and +Y facing");
    }
  }
}
