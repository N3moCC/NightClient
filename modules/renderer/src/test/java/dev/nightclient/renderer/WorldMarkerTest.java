package dev.nightclient.renderer;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
class WorldMarkerTest {
  private static final int COLOR = 0xff50c8ff;
  private static final WorldMarker.Palette THEME =
      new WorldMarker.Palette(0xff111214, 0xff66ccff, 0xffececf0);
  static final class Sink implements WorldRibbon.Quads {
    final List<double[]> quads = new ArrayList<double[]>();
    final List<Integer> colors = new ArrayList<Integer>();
    public void quad(double ax, double ay, double az, double bx, double by, double bz, double cx,
        double cy, double cz, double dx, double dy, double dz, int color) {
      quads.add(new double[] {ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz});
      colors.add(color);
    }
  }
  private static WorldRibbon.View view(int height) {
    return new WorldRibbon.View(0, 0, 0, 0, 0, 70, 16 / 9f, height * 16 / 9, height);
  }
  private static void draw(Sink sink, WorldRibbon.View view, int style, float badge, boolean digits,
      long meters, boolean emphasis) {
    WorldMarker.draw(
        sink, view, 0, 0, 10, style, .7, 1.6, 1.5f, badge, digits, meters, emphasis, COLOR, THEME);
  }
  private static void drawPlate(
      Sink sink, WorldRibbon.View view, float badge, boolean digits, long meters) {
    WorldMarker.draw(sink, view, 0, 0, 10, WorldMarker.BADGE, .08, 1.6, 1.5f, badge, digits, meters,
        false, COLOR, THEME);
  }
  private static double maxX(Sink sink) {
    double max = 0;
    for (double[] quad : sink.quads)
      for (int corner = 0; corner < 4; corner++) max = Math.max(max, Math.abs(quad[corner * 3]));
    return max;
  }
  private static double maxY(Sink sink) {
    double max = 0;
    for (double[] quad : sink.quads)
      for (int corner = 0; corner < 4; corner++)
        max = Math.max(max, Math.abs(quad[corner * 3 + 1]));
    return max;
  }
  private static boolean hasRgb(Sink sink, int rgb) {
    for (int color : sink.colors)
      if ((color & 0xffffff) == rgb && color >>> 24 > 0) return true;
    return false;
  }
  @Test
  void styleNamesFallBackToTheBadge() {
    assertEquals(WorldMarker.BADGE, WorldMarker.style(null));
    assertEquals(WorldMarker.BADGE, WorldMarker.style("sparkles"));
    assertEquals(WorldMarker.RING, WorldMarker.style("ring"));
    assertEquals(WorldMarker.STEM, WorldMarker.style("stem"));
    assertEquals(WorldMarker.DOT, WorldMarker.style("dot"));
  }
  @Test
  void transparentColoursAndPointsBehindTheCameraEmitNothing() {
    Sink sink = new Sink();
    WorldMarker.draw(sink, view(900), 0, 0, 10, WorldMarker.BADGE, .7, 1.6, 1.5f, 1, false, -1,
        false, 0x0050c8ff, THEME);
    assertTrue(sink.quads.isEmpty());
    WorldMarker.draw(sink, view(900), 0, 0, -10, WorldMarker.BADGE, .7, 1.6, 1.5f, 1, false, -1,
        false, COLOR, THEME);
    assertTrue(sink.quads.isEmpty(), "A badge only exists in front of the camera");
    WorldMarker.draw(sink, view(900), 0, 0, 10, WorldMarker.BADGE, .7, 1.6, 1.5f, 1, false, -1,
        false, COLOR, null);
    assertTrue(sink.quads.isEmpty());
  }
  @Test
  void badgeFloatsAPlateAboveTheGroundAndKeepsTheGroundRingWorldFlat() {
    Sink sink = new Sink();
    draw(sink, view(900), WorldMarker.BADGE, 1, false, -1, false);
    assertTrue(sink.quads.size() > 20, "Plate fill, outline, glyph and halo");
    assertTrue(maxY(sink) > 1.7, "The plate must float above the 1.6 stem");
    boolean ground = false, plate = false;
    for (double[] quad : sink.quads) {
      double y = quad[1];
      if (Math.abs(y - .02) < .01) ground = true;
      if (y > .5) plate = true;
    }
    assertTrue(ground, "The ring and halo stay on the ground plane");
    assertTrue(plate, "The plate itself floats");
    assertTrue(hasRgb(sink, 0x111214), "The panel comes from the theme");
    assertTrue(hasRgb(sink, 0x66ccff), "The stroke and digits come from the theme");
  }
  @Test
  void ringStyleStaysWorldFlatAtTheSavedRadius() {
    Sink sink = new Sink();
    draw(sink, view(900), WorldMarker.RING, 1, false, -1, false);
    assertTrue(sink.quads.size() > 4, "A ring draws its halo and its stroke band");
    for (double[] quad : sink.quads)
      for (int corner = 0; corner < 4; corner++) {
        int i = corner * 3;
        double ground = Math.hypot(quad[i], quad[i + 2] - 10);
        assertEquals(.02, quad[i + 1], .05, "Rings never float");
        if (ground > .35) assertEquals(.7, ground, .06, "Only the stroke leaves the halo zone");
      }
  }
  @Test
  void stemStyleParksItsHeadAtTheStemTop() {
    Sink sink = new Sink();
    draw(sink, view(900), WorldMarker.STEM, 1, false, -1, false);
    double top = maxY(sink);
    assertEquals(1.6, top, .3, "The beacon head sits at the stem top");
  }
  @Test
  void farMarkersCollapseIntoASmallDotAtTheirPoint() {
    Sink near = new Sink();
    draw(near, view(900), WorldMarker.BADGE, 1, false, -1, false);
    Sink far = new Sink();
    WorldMarker.draw(far, view(900), 0, 0, 200, WorldMarker.BADGE, .7, 1.6, 1.5f, 1, false, -1,
        false, COLOR, THEME);
    assertTrue(far.quads.size() < near.quads.size(), "A collapsed marker keeps only its dot");
    for (double[] quad : far.quads)
      for (int corner = 0; corner < 4; corner++) {
        int i = corner * 3;
        assertTrue(Math.abs(quad[i]) <= 1.2 && Math.abs(quad[i + 1]) <= 1.2
                && Math.abs(quad[i + 2] - 200) <= .15,
            "The dot hugs its point instead of a tall badge");
      }
  }
  @Test
  void distanceDigitsWidenThePlate() {
    Sink plain = new Sink();
    drawPlate(plain, view(900), 1, false, -1);
    Sink digits = new Sink();
    drawPlate(digits, view(900), 1, true, 128);
    assertTrue(maxX(digits) > maxX(plain), "Digits widen the plate on the right");
  }
  @Test
  void badgeScaleGrowsThePlateOnScreen() {
    Sink one = new Sink();
    drawPlate(one, view(900), 1, false, -1);
    Sink two = new Sink();
    drawPlate(two, view(900), 2, false, -1);
    assertTrue(maxX(two) > maxX(one) * 1.8);
    double topOne = maxY(one) - 1.6, topTwo = maxY(two) - 1.6;
    assertTrue(topTwo > topOne * 1.8, "The plate height grows with the badge scale");
  }
  @Test
  void plateExtentsScaleWithPixelsPerBlockSoScreenSizeStaysConstant() {
    WorldRibbon.View small = view(900), large = view(1800);
    Sink a = new Sink(), b = new Sink();
    drawPlate(a, small, 1, false, -1);
    drawPlate(b, large, 1, false, -1);
    double xa = maxX(a), xb = maxX(b);
    assertTrue(xa > .1 && xb > 0);
    assertEquals(
        2, xa / xb, .3, "Doubling the framebuffer halves the world extent at equal screen size");
  }
}
