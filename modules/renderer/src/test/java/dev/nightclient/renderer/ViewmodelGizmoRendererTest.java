package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.ViewmodelProjection;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ViewmodelGizmoRendererTest {
  private static final int RED = 0xffff3352, GREEN = 0xff8bdc00, BLUE = 0xff2890ff;

  @Test
  void solidMeshesStayFiniteAndConsistentlyWoundInEveryMode() {
    for (String mode : new String[] {"G", "R", "S"}) {
      Sink sink = render(mode, projection(0, 0, 0), "", "", 0, 0, 1, 1, 1);
      assertTrue(sink.triangles.size() > 100, mode + " must contain a solid mesh");
      assertTrue(sink.triangles.size() <= 4096);
      for (float[] t : sink.triangles) {
        for (float f : t) assertTrue(Float.isFinite(f));
        assertTrue(area(t) > 0, "All native GUI triangles must have positive winding");
        for (int i = 0; i < 6; i += 2) {
          assertTrue(t[i] >= 0 && t[i] <= 640);
          assertTrue(t[i + 1] >= 0 && t[i + 1] <= 480);
        }
      }
      assertTrue(
          sink.colors.stream().distinct().count() > 12, "Lighting must produce a faceted solid");
      float[] pivot = projection(0, 0, 0).pivot();
      for (float[] t : sink.triangles)
        for (int i = 0; i < 6; i += 2)
          assertTrue(Math.hypot(t[i] - pivot[0], t[i + 1] - pivot[1]) < 160,
              "Gizmo stays near its interaction radius");
    }
  }

  @Test
  void paletteHoverSelectionAndOpacityRemainAuthoredInputs() {
    Sink normal = render("G", projection(0, 0, 0), "", "", 0, 0, 1, 1, 1);
    Sink selected = render("G", projection(0, 0, 0), "X", "Y", 0, 0, 1, 1, .5f);
    assertEquals(normal.triangles.size(), selected.triangles.size());
    assertNotEquals(normal.colors, selected.colors);
    for (int c : selected.colors) assertEquals(128, c >>> 24);
    assertTrue(normal.colors.stream().anyMatch(c -> (c >>> 16 & 255) > (c >>> 8 & 255) + 20));
    assertTrue(normal.colors.stream().anyMatch(c -> (c >>> 8 & 255) > (c >>> 16 & 255) + 20));
    assertTrue(normal.colors.stream().anyMatch(c -> (c & 255) > (c >>> 16 & 255) + 20));
  }

  @Test
  void screenMappingPreservesMeshAndNonUniformScaling() {
    Sink original = render("R", projection(0, 0, 0), "", "", 0, 0, 1, 1, 1);
    Sink transformed = render("R", projection(0, 0, 0), "", "", 17, 29, 1.5f, .75f, 1);
    assertEquals(original.colors, transformed.colors);
    assertEquals(original.triangles.size(), transformed.triangles.size());
    for (int i = 0; i < original.triangles.size(); i++)
      for (int j = 0; j < 6; j++)
        assertEquals(
            (j % 2 == 0 ? 17 : 29) + original.triangles.get(i)[j] * (j % 2 == 0 ? 1.5 : .75),
            transformed.triangles.get(i)[j], .0002);
  }

  @Test
  void rotatingSelectedTransformChangesRotationAndScaleMeshes() {
    for (String mode : new String[] {"R", "S"}) {
      Sink a = render(mode, projection(0, 0, 0), "", "", 0, 0, 1, 1, 1);
      Sink b = render(mode, projection(27, -31, 18), "", "", 0, 0, 1, 1, 1);
      assertNotEquals(hash(a), hash(b));
    }
  }

  @Test
  void torusBackHalfIsDarkenedAndDrawnBeforeItsFrontHalf() {
    Sink sink = new Sink();
    new ViewmodelGizmoRenderer().render(sink, projection(0, 0, 0), "R", "", "", 92, 3.6f,
        0xffffffff, 0xffffffff, 0xffffffff, 0, 0, 1, 1, 1);
    boolean front = false, back = false;
    for (int color : sink.colors) {
      int brightness = color & 255;
      if (brightness < 112) {
        assertFalse(front, "Rear geometry must be submitted before the foreground");
        back = true;
      } else {
        assertTrue(brightness >= 134);
        front = true;
      }
    }
    assertTrue(back && front, "Both ring halves should be visible with distinct lighting");
  }

  @Test
  void invalidNearOrOffscreenInputsEmitNoUnsafeGeometry() {
    ViewmodelGizmoRenderer renderer = new ViewmodelGizmoRenderer();
    Sink sink = new Sink();
    renderer.render(
        sink, projection(0, 0, 0), "G", "", "", Float.NaN, 2, RED, GREEN, BLUE, 0, 0, 1, 1, 1);
    renderer.render(sink, projection(0, 0, 0), "G", "", "", 54, 2, RED, GREEN, BLUE, 0, 0, 1, 1, 0);
    renderer.render(
        sink, projection(0, 0, 0), "invalid", "", "", 54, 2, RED, GREEN, BLUE, 0, 0, 1, 1, 1);
    float[] behind = matrix();
    behind[15] = -2;
    ViewmodelProjection bad = new ViewmodelProjection(behind, 640, 480, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    renderer.render(sink, bad, "R", "", "", 54, 2, RED, GREEN, BLUE, 0, 0, 1, 1, 1);
    assertTrue(sink.triangles.isEmpty());
  }

  @Test
  void emitsVisualReviewForEveryOperation() throws Exception {
    for (String mode : new String[] {"G", "R", "S"}) {
      Sink sink = render(mode, projection(0, 0, 0), "", "", 0, 0, 1, 1, 1);
      BufferedImage full = new BufferedImage(1280, 960, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = full.createGraphics();
      g.scale(2, 2);
      g.setColor(new Color(0xff17191e, true));
      g.fillRect(0, 0, 640, 480);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      for (int i = 0; i < sink.triangles.size(); i++) {
        float[] t = sink.triangles.get(i);
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(t[0], t[1]);
        shape.lineTo(t[2], t[3]);
        shape.lineTo(t[4], t[5]);
        shape.closePath();
        g.setColor(new Color(sink.colors.get(i), true));
        g.fill(shape);
      }
      g.dispose();
      BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
      g = image.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(full, 0, 0, 640, 480, null);
      g.dispose();
      ImageIO.write(image, "png", new File("/tmp/viewmodel-gizmo-" + mode + ".png"));
    }
  }

  private static long hash(Sink sink) {
    long h = 17;
    for (float[] t : sink.triangles)
      for (float p : t) h = 31 * h + Float.floatToIntBits(p);
    return h;
  }
  private static float area(float[] t) {
    return (t[2] - t[0]) * (t[5] - t[1]) - (t[3] - t[1]) * (t[4] - t[0]);
  }
  private static Sink render(String mode, ViewmodelProjection p, String selected, String hovered,
      float x, float y, float sx, float sy, float alpha) {
    Sink sink = new Sink();
    new ViewmodelGizmoRenderer().render(
        sink, p, mode, selected, hovered, 92, 3.6f, RED, GREEN, BLUE, x, y, sx, sy, alpha);
    return sink;
  }
  private static ViewmodelProjection projection(float rx, float ry, float rz) {
    return new ViewmodelProjection(matrix(), 640, 480, 0, 0, 0, 0, 0, 0, rx, ry, rz);
  }
  private static float[] matrix() {
    float ay = (float) Math.toRadians(30), ax = (float) Math.toRadians(-22);
    float cy = (float) Math.cos(ay), sy = (float) Math.sin(ay), cx = (float) Math.cos(ax),
          sx = (float) Math.sin(ax);
    float[] r = {cy, 0, -sy, 0, sy * sx, cx, cy * sx, 0, sy * cx, -sx, cy * cx, 0, 0, 0, 0, 1};
    float[] p = {1.3f, 0, 0, 0, 0, 1.73f, 0, 0, 0, 0, -1.002f, -1, 0, 0, 1.904f, 2};
    float[] out = new float[16];
    for (int c = 0; c < 4; c++)
      for (int row = 0; row < 4; row++)
        for (int k = 0; k < 4; k++) out[c * 4 + row] += p[k * 4 + row] * r[c * 4 + k];
    return out;
  }
  private static final class Sink implements Canvas {
    final List<float[]> triangles = new ArrayList<>();
    final List<Integer> colors = new ArrayList<>();
    public void fillTriangle(
        float ax, float ay, float bx, float by, float cx, float cy, int color) {
      triangles.add(new float[] {ax, ay, bx, by, cx, cy});
      colors.add(color);
    }
    public void rect(float x, float y, float w, float h, int c) {}
    public void roundedRect(float x, float y, float w, float h, float r, int c) {}
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    public void line(float x, float y, float x2, float y2, float t, int c) {}
    public void circle(float x, float y, float r, int c) {}
    public void text(String s, float x, float y, float size, int color, boolean bold) {}
    public float textWidth(String s, float size, boolean bold) {
      return 0;
    }
    public void pushClip(float x, float y, float w, float h) {}
    public void popClip() {}
    public void item(String s, float x, float y, float size, float alpha) {}
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int q, float alpha) {}
    public void texture(String s, float x, float y, float w, float h, float r, float tile, int c) {}
  }
}
