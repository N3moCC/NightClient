package dev.nightclient.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

final class ViewmodelProjectionTest {
  static float[] perspective() {
    return new float[] {1.1f, 0, 0, 0, 0, 1.5f, 0, 0, 0, 0, -1.01f, -1, 0, 0, -.1005f, 0};
  }
  private static ViewmodelProjection projection(
      float x, float y, float z, float rx, float ry, float rz) {
    return new ViewmodelProjection(perspective(), 800, 600, .42f, -.25f, -1, x, y, z, rx, ry, rz);
  }
  @Test
  void projectsTheActualAnchorAndDepthInsteadOfAMaskRectangle() {
    ViewmodelProjection p = projection(0, 0, 0, 0, 0, 0);
    float[] center = p.pivot();
    assertEquals(584.8, center[0], .001);
    assertEquals(412.5, center[1], .001);
    float[] moved = projection(.1f, 0, 0, 0, 0, 0).pivot();
    assertEquals(44, moved[0] - center[0], .001);
    assertNull(projection(0, 0, 2, 0, 0, 0).pivot());
  }
  @Test
  void rayPlaneRecoversRotationAnglesOnAllThreePerspectiveRings() {
    ViewmodelProjection p = projection(0, 0, 0, 27, -31, 18);
    for (int axis = 0; axis < 3; axis++) {
      float[][] b = p.ring(axis);
      double angle = .62;
      float radius = .13f, u = (float) Math.cos(angle) * radius,
            v = (float) Math.sin(angle) * radius;
      float[] point = p.project(
          b[0][0] * u + b[1][0] * v, b[0][1] * u + b[1][1] * v, b[0][2] * u + b[1][2] * v);
      assertNotNull(point);
      assertEquals(angle, p.ringAngle(axis, point[0], point[1]), .0001, "axis " + axis);
    }
  }
  @Test
  void translationAxisRayRecoversWorldUnitsAndRotationDoesNotRotateParentAxes() {
    ViewmodelProjection p = projection(0, 0, 0, 60, 40, 30);
    for (int axis = 0; axis < 3; axis++) {
      float[] coordinates = new float[3];
      coordinates[axis] = .12f;
      float[] point = p.project(coordinates[0], coordinates[1], coordinates[2]);
      assertEquals(.12, p.axisPosition(axis, point[0], point[1]), .0001, "axis " + axis);
    }
    assertArrayEquals(new float[] {1, 0, 0}, p.axis("G", 0), .00001f);
    assertTrue(Math.abs(p.axis("S", 0)[1]) > .1);
    assertEquals(0, p.axis("R", 1)[0], .00001);
  }
  @Test
  void matrixSnapshotIsImmutableAndSelectionRetainsBothHandProjections() {
    float[] source = perspective();
    ViewmodelProjection p =
        new ViewmodelProjection(source, 800, 600, .42f, -.25f, -1, 0, 0, 0, 0, 0, 0);
    float original = p.pivot()[0];
    source[0] = 0;
    assertEquals(original, p.pivot()[0]);
    ByteBuffer rgba = ByteBuffer.allocate(4);
    rgba.put(3, (byte) 255);
    PickMask mask = PickMask.fromRgba(rgba, 1, 1, 800, 600, false);
    ViewmodelProjection off =
        new ViewmodelProjection(perspective(), 800, 600, -.42f, -.25f, -1, 0, 0, 0, 0, 0, 0);
    ViewmodelSelection selection = new ViewmodelSelection("main", mask, 5, mask, mask, p, off);
    assertSame(p, selection.projection);
    assertSame(off, selection.forHand("off").projection);
    assertEquals(off.pivot()[0], selection.forHand("off").pivotX);
    assertThrows(IllegalArgumentException.class,
        () -> new ViewmodelProjection(new float[] {1}, 800, 600, 0, 0, -1, 0, 0, 0, 0, 0, 0));
  }
}
