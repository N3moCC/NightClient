package dev.nightclient.renderer;

public final class WorldMarker {
  public static final int BADGE = 0, RING = 1, STEM = 2, DOT = 3;

  public static final class Palette {
    public final int panel, stroke, title;

    public Palette(int panel, int stroke, int title) {
      this.panel = panel;
      this.stroke = stroke;
      this.title = title;
    }
  }

  private static final int MIN_SEGMENTS = 8, MAX_SEGMENTS = 32, PX_PER_SEGMENT = 12;
  private static final int ARC_STEPS = 3, DISC_STEPS = 12;

  private static final double COLLAPSE_PIXELS = 7;

  private static final double PLATE_H = 16, PLATE_R = 5, PLATE_W = 26, PLATE_W_DIGITS = 44;
  private static final double GLYPH_R = 4.2, GLYPH_RING = 6.2, GLYPH_PAD = 6, DIGIT_CELL = 4;
  private static final double PLATE_ALPHA = .78, STROKE_ALPHA = .55, DIGIT_ALPHA = .9;
  private static final double GROUND_ALPHA = .3, STEM_ALPHA = .8;

  private static final int[] DIGITS = {126, 48, 109, 121, 51, 91, 95, 112, 127, 123};

  private WorldMarker() {}

  public static int style(String value) {
    if ("ring".equals(value)) return RING;
    if ("stem".equals(value)) return STEM;
    if ("dot".equals(value)) return DOT;
    return BADGE;
  }

  public static void draw(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, int style, double radius, double stem, float width, float badge, boolean digits,
      long meters, boolean emphasis, int color, Palette theme) {
    if (out == null || view == null || theme == null || (color >>> 24) == 0) return;
    if (!(view.forward(x, y, z) > .08)) return;
    float alpha = (color >>> 24) / 255f;
    double size = Math.max(.02, radius);
    double bp = blocksPerPixel(view, x, y, z);
    if (style == DOT || collapses(view, x, y, z, size)) {
      dot(out, view, x, y, z, 2.4 * bp, width, color);
      return;
    }
    groundDisc(
        out, view, x, y + .02, z, Math.min(.5, size * .4), fill(color, alpha * GROUND_ALPHA));
    ring(out, view, x, y + .02, z, size, width, color);
    if (style == RING) return;
    double top = y + Math.max(0, stem);
    WorldRibbon.line(out, view, x, y + .02, z, x, top, z, Math.max(.8f, width * .8f),
        fill(color, alpha * STEM_ALPHA));
    if (style == STEM) {
      fillDisc(out, view, x, top, z, 3.4 * bp, color);
      ringDisc(out, view, x, top, z, 5.2 * bp, width * .8f, fill(theme.title, alpha * .42));
      return;
    }
    plate(out, view, x, top, z, bp, Math.max(.5, badge), width, digits, meters, emphasis, color,
        theme, alpha);
  }

  private static void plate(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double bp, double badge, float width, boolean digits, long meters, boolean emphasis,
      int color, Palette theme, float alpha) {
    double w = (digits && meters >= 0 ? PLATE_W_DIGITS : PLATE_W) * badge, h = PLATE_H * badge,
           r = Math.min(PLATE_R * badge, h * .5);
    double lift = h * .5;
    double[] right = view.right(), up = view.up();
    int rows = (int) Math.ceil(h / 2);
    for (int row = 0; row < rows; row++) {
      double low = -h * .5 + h * row / rows, high = -h * .5 + h * (row + 1) / rows;
      if (row + 1 == rows) high += .01;
      int panel = fill(theme.panel, alpha * PLATE_ALPHA);
      double[] a = corner(x, y, z, right, up, bp, lift + low, -edge(low, w, h, r)),
               b = corner(x, y, z, right, up, bp, lift + high, -edge(high, w, h, r)),
               c = corner(x, y, z, right, up, bp, lift + high, edge(high, w, h, r)),
               d = corner(x, y, z, right, up, bp, lift + low, edge(low, w, h, r));
      out.quad(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], panel);
    }
    outline(out, view, x, y, z, right, up, bp, lift, w, h, r,
        Math.max(.8f, width) * (emphasis ? 1.3f : 1f),
        fill(theme.stroke, Math.min(1, alpha * STROKE_ALPHA + (emphasis ? .3 : 0))));
    double glyphU = -w * .5 + (GLYPH_PAD + GLYPH_R) * badge;
    double[] glyph = corner(x, y, z, right, up, bp, lift, glyphU);
    fillDisc(out, view, glyph[0], glyph[1], glyph[2], GLYPH_R * badge * bp, color);
    ringDisc(out, view, glyph[0], glyph[1], glyph[2], GLYPH_RING * badge * bp, width * .8f,
        fill(theme.title, alpha * .42));
    if (digits && meters >= 0)
      engrave(out, view, x, y, z, right, up, bp, lift, w, h, badge, meters,
          fill(theme.title, alpha * DIGIT_ALPHA));
  }

  private static double edge(double v, double w, double h, double r) {
    double inset = h * .5 - r, over = Math.abs(v) - inset;
    if (over <= 0) return w * .5;
    return w * .5 - (r - Math.sqrt(Math.max(0, r * r - over * over)));
  }

  private static double[] corner(
      double x, double y, double z, double[] right, double[] up, double bp, double v, double u) {
    double du = u * bp, dv = v * bp;
    return new double[] {x + right[0] * du + up[0] * dv, y + right[1] * du + up[1] * dv,
        z + right[2] * du + up[2] * dv};
  }

  private static void outline(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double[] right, double[] up, double bp, double lift, double w, double h, double r,
      float width, int color) {
    double iu = w * .5 - r, iv = h * .5 - r;
    java.util.List<double[]> trace = new java.util.ArrayList<double[]>();
    trace.add(corner(x, y, z, right, up, bp, lift - iv - r, -iu));
    trace.add(corner(x, y, z, right, up, bp, lift - iv - r, iu));
    addArc(trace, x, y, z, right, up, bp, lift, iu, -iv, r, -90, 0);
    trace.add(corner(x, y, z, right, up, bp, lift + iv, iu + r));
    addArc(trace, x, y, z, right, up, bp, lift, iu, iv, r, 0, 90);
    trace.add(corner(x, y, z, right, up, bp, lift + iv + r, -iu));
    addArc(trace, x, y, z, right, up, bp, lift, -iu, iv, r, 90, 180);
    trace.add(corner(x, y, z, right, up, bp, lift - iv, -iu - r));
    addArc(trace, x, y, z, right, up, bp, lift, -iu, -iv, r, 180, 270);
    double[] prior = trace.get(trace.size() - 1);
    for (double[] at : trace) prior = stroke(out, view, prior, at, width, color);
  }

  private static void addArc(java.util.List<double[]> trace, double x, double y, double z,
      double[] right, double[] up, double bp, double lift, double centreU, double centreV, double r,
      double from, double to) {
    for (int step = 1; step <= ARC_STEPS; step++) {
      double angle = Math.toRadians(from + (to - from) * step / ARC_STEPS);
      trace.add(corner(x, y, z, right, up, bp, lift + centreV + r * Math.sin(angle),
          centreU + r * Math.cos(angle)));
    }
  }

  private static double[] stroke(WorldRibbon.Quads out, WorldRibbon.View view, double[] from,
      double[] to, float width, int color) {
    WorldRibbon.line(out, view, from[0], from[1], from[2], to[0], to[1], to[2], width, color);
    return to;
  }

  private static void engrave(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double[] right, double[] up, double bp, double lift, double w, double h,
      double badge, long meters, int color) {
    String text = String.valueOf(Math.max(0, Math.min(9999, meters)));
    double cell = DIGIT_CELL * badge;
    double span = text.length() * cell - (cell - 3 * badge);
    double startU = w * .5 - 5 * badge - span;
    double baseV = -h * .5 + 5.5 * badge;
    float thickness = Math.max(.7f, (float) (.9 * badge));
    for (int index = 0; index < text.length(); index++) {
      int shape = DIGITS[text.charAt(index) - '0'];
      double u = startU + index * cell, far = u + 3 * badge;
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, u, baseV + 5 * badge, far,
          baseV + 5 * badge, (shape & 64) != 0);
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, far, baseV + 5 * badge,
          far, baseV + 2.5 * badge, (shape & 32) != 0);
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, far, baseV + 2.5 * badge,
          far, baseV, (shape & 16) != 0);
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, u, baseV, far, baseV,
          (shape & 8) != 0);
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, u, baseV + 2.5 * badge, u,
          baseV, (shape & 4) != 0);
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, u, baseV + 5 * badge, u,
          baseV + 2.5 * badge, (shape & 2) != 0);
      engrave(out, view, x, y, z, right, up, bp, lift, thickness, color, u, baseV + 2.5 * badge,
          far, baseV + 2.5 * badge, (shape & 1) != 0);
    }
  }

  private static void engrave(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double[] right, double[] up, double bp, double lift, float thickness, int color,
      double u0, double v0, double u1, double v1, boolean on) {
    if (!on) return;
    double[] a = corner(x, y, z, right, up, bp, lift + v0, u0);
    double[] b = corner(x, y, z, right, up, bp, lift + v1, u1);
    WorldRibbon.line(out, view, a[0], a[1], a[2], b[0], b[1], b[2], thickness, color);
  }

  private static void fillDisc(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double radius, int color) {
    if ((color >>> 24) == 0 || !(radius > 0)) return;
    double[] right = view.right(), up = view.up();
    double[] prior = fan(x, y, z, right, up, radius, 0);
    for (int step = 1; step <= DISC_STEPS; step++) {
      double angle = step * 2 * Math.PI / DISC_STEPS;
      double[] at = fan(x, y, z, right, up, radius * Math.cos(angle), radius * Math.sin(angle));
      out.quad(x, y, z, prior[0], prior[1], prior[2], at[0], at[1], at[2], x, y, z, color);
      prior = at;
    }
  }

  private static void ringDisc(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double radius, float width, int color) {
    if ((color >>> 24) == 0 || !(radius > 0)) return;
    double[] right = view.right(), up = view.up();
    double[] prior = fan(x, y, z, right, up, radius, 0);
    for (int step = 1; step <= DISC_STEPS; step++) {
      double angle = step * 2 * Math.PI / DISC_STEPS;
      double[] at = fan(x, y, z, right, up, radius * Math.cos(angle), radius * Math.sin(angle));
      WorldRibbon.line(out, view, prior[0], prior[1], prior[2], at[0], at[1], at[2], width, color);
      prior = at;
    }
  }

  private static double[] fan(
      double x, double y, double z, double[] right, double[] up, double u, double v) {
    return new double[] {
        x + right[0] * u + up[0] * v, y + right[1] * u + up[1] * v, z + right[2] * u + up[2] * v};
  }

  private static void groundDisc(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double radius, int color) {
    if ((color >>> 24) == 0 || !(radius > 0)) return;
    double priorX = x + radius, priorZ = z;
    for (int step = 1; step <= DISC_STEPS; step++) {
      double angle = step * 2 * Math.PI / DISC_STEPS;
      double atX = x + radius * Math.cos(angle), atZ = z + radius * Math.sin(angle);
      out.quad(x, y, z, priorX, y, priorZ, atX, y, atZ, x, y, z, color);
      priorX = atX;
      priorZ = atZ;
    }
  }

  public static void ring(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y,
      double z, double radius, float width, int color) {
    if (out == null || view == null || (color >>> 24) == 0) return;
    int steps = segments(view, x, y, z, radius);
    double fromX = x + radius, fromZ = z;
    for (int step = 1; step <= steps; step++) {
      double angle = step * 2 * Math.PI / steps;
      double toX = x + radius * Math.cos(angle), toZ = z + radius * Math.sin(angle);
      WorldRibbon.line(out, view, fromX, y, fromZ, toX, y, toZ, width, color);
      fromX = toX;
      fromZ = toZ;
    }
  }

  public static void dot(WorldRibbon.Quads out, WorldRibbon.View view, double x, double y, double z,
      double arm, float width, int color) {
    if (out == null || view == null || (color >>> 24) == 0) return;
    double[] right = view.right(), up = view.up();
    double[] a = fan(x, y, z, right, up, -arm, 0), b = fan(x, y, z, right, up, arm, 0);
    WorldRibbon.line(out, view, a[0], a[1], a[2], b[0], b[1], b[2], width, color);
    a = fan(x, y, z, right, up, 0, -arm);
    b = fan(x, y, z, right, up, 0, arm);
    WorldRibbon.line(out, view, a[0], a[1], a[2], b[0], b[1], b[2], width, color);
    fillDisc(out, view, x, y, z, arm * .45, color);
  }

  private static double blocksPerPixel(WorldRibbon.View view, double x, double y, double z) {
    double pixels = view.pixelsPerBlock(x, y, z);
    return !(pixels > 1e-6) || !Double.isFinite(pixels) ? 0 : 1 / pixels;
  }

  private static boolean collapses(
      WorldRibbon.View view, double x, double y, double z, double radius) {
    double pixels = view.pixelsPerBlock(x, y, z) * radius * 2;
    return !Double.isFinite(pixels) || pixels < COLLAPSE_PIXELS;
  }

  private static int segments(WorldRibbon.View view, double x, double y, double z, double radius) {
    double pixels = view.pixelsPerBlock(x, y, z) * radius * 2 * Math.PI;
    if (!Double.isFinite(pixels) || pixels <= 0) return MIN_SEGMENTS;
    return (int) Math.max(
        MIN_SEGMENTS, Math.min(MAX_SEGMENTS, Math.round(pixels / PX_PER_SEGMENT)));
  }

  private static int fill(int base, double alpha) {
    int a = (int) Math.max(0, Math.min(255, Math.round((base >>> 24) * alpha)));
    return a << 24 | base & 0xffffff;
  }
}
