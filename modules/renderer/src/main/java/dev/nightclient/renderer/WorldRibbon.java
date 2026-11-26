package dev.nightclient.renderer;

public final class WorldRibbon {
  private static final double NEAR = .06;
  private static final int FRINGE_STEPS = 4;
  private static final int MAX_CACHED_SEGMENTS = 256;
  private static final double[][] CACHED_COS = new double[MAX_CACHED_SEGMENTS + 1][];
  private static final double[][] CACHED_SIN = new double[MAX_CACHED_SEGMENTS + 1][];

  private WorldRibbon() {}

  private static double[][] unitCircle(int segs) {
    if (segs <= MAX_CACHED_SEGMENTS) {
      double[] c = CACHED_COS[segs];
      double[] s = CACHED_SIN[segs];
      if (c != null && s != null) return new double[][] {c, s};
      c = new double[segs + 1];
      s = new double[segs + 1];
      double step = (Math.PI * 2.0) / segs;
      for (int i = 0; i < segs; i++) {
        double a = i * step;
        c[i] = Math.cos(a);
        s[i] = Math.sin(a);
      }
      c[segs] = 1.0;
      s[segs] = 0.0;
      CACHED_COS[segs] = c;
      CACHED_SIN[segs] = s;
      return new double[][] {c, s};
    }
    double[] c = new double[segs + 1];
    double[] s = new double[segs + 1];
    double step = (Math.PI * 2.0) / segs;
    for (int i = 0; i < segs; i++) {
      double a = i * step;
      c[i] = Math.cos(a);
      s[i] = Math.sin(a);
    }
    c[segs] = 1.0;
    s[segs] = 0.0;
    return new double[][] {c, s};
  }

  public interface Quads {
    void quad(double ax, double ay, double az, double bx, double by, double bz, double cx,
        double cy, double cz, double dx, double dy, double dz, int color);
  }

  public static final class View {
    final double x, y, z, fx, fy, fz, ux, uy, uz, rx, ry, rz, focalX, focalY, centerX, centerY;

    public View(double x, double y, double z, float pitch, float yaw, float fov, float aspect,
        int width, int height) {
      this.x = x;
      this.y = y;
      this.z = z;
      double p = Math.toRadians(pitch), a = Math.toRadians(yaw), cp = Math.cos(p), sp = Math.sin(p),
             ca = Math.cos(a), sa = Math.sin(a);
      fx = -sa * cp;
      fy = -sp;
      fz = ca * cp;
      ux = -sa * sp;
      uy = cp;
      uz = ca * sp;
      rx = fy * uz - fz * uy;
      ry = fz * ux - fx * uz;
      rz = fx * uy - fy * ux;
      double tangent = Math.tan(Math.toRadians(Math.max(1, Math.min(179, fov))) * .5);
      focalY = Math.max(1, height) / (2 * tangent);
      focalX = Math.max(1, width) / (2 * tangent * Math.max(.01, aspect));
      centerX = Math.max(1, width) * .5;
      centerY = Math.max(1, height) * .5;
    }

    public void project(double px, double py, double pz, double[] out) {
      double dx = px - x, dy = py - y, dz = pz - z;
      double forward = dx * fx + dy * fy + dz * fz;
      double right = dx * rx + dy * ry + dz * rz;
      double up = dx * ux + dy * uy + dz * uz;
      out[0] = Double.NaN;
      out[1] = Double.NaN;
      out[2] = forward;
      out[3] = right;
      out[4] = up;
      if (forward <= 1e-9) return;
      out[0] = centerX + right / forward * focalX;
      out[1] = centerY - up / forward * focalY;
    }

    public double pixelsPerBlock(double px, double py, double pz) {
      return pixelScale(px, py, pz, rx, ry, rz);
    }

    public double eyeY() {
      return y;
    }

    public double[] right() {
      return new double[] {rx, ry, rz};
    }

    public double[] up() {
      return new double[] {ux, uy, uz};
    }

    public double forward(double px, double py, double pz) {
      return (px - x) * fx + (py - y) * fy + (pz - z) * fz;
    }

    public double distance(double px, double py, double pz) {
      double dx = px - x, dy = py - y, dz = pz - z;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double depth(double x, double y, double z) {
      return (x - this.x) * fx + (y - this.y) * fy + (z - this.z) * fz;
    }

    private double pixelScale(double x, double y, double z, double sx, double sy, double sz) {
      x -= this.x;
      y -= this.y;
      z -= this.z;
      double depth = x * fx + y * fy + z * fz, along = sx * fx + sy * fy + sz * fz;
      double dx = ((sx * rx + sy * ry + sz * rz) * depth - (x * rx + y * ry + z * rz) * along)
          / (depth * depth);
      double dy = ((sx * ux + sy * uy + sz * uz) * depth - (x * ux + y * uy + z * uz) * along)
          / (depth * depth);
      return Math.sqrt(dx * dx * focalX * focalX + dy * dy * focalY * focalY);
    }
  }

  public static void line(Quads out, View view, double ax, double ay, double az, double bx,
      double by, double bz, float width, int color) {
    if (out == null || view == null || (color >>> 24) == 0 || !Float.isFinite(width) || width <= 0)
      return;
    double da = view.depth(ax, ay, az), db = view.depth(bx, by, bz);
    if (!Double.isFinite(da) || !Double.isFinite(db) || da < NEAR && db < NEAR) return;
    if (da < NEAR) {
      double t = (NEAR - da) / (db - da);
      ax += (bx - ax) * t;
      ay += (by - ay) * t;
      az += (bz - az) * t;
    }
    if (db < NEAR) {
      double t = (NEAR - db) / (da - db);
      bx += (ax - bx) * t;
      by += (ay - by) * t;
      bz += (az - bz) * t;
    }
    double dx = bx - ax, dy = by - ay, dz = bz - az;
    double vx = (ax + bx) * .5 - view.x, vy = (ay + by) * .5 - view.y, vz = (az + bz) * .5 - view.z;
    double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
    double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
    if (length < 1e-12 || !Double.isFinite(length)) return;
    sx /= length;
    sy /= length;
    sz /= length;
    double pa = view.pixelScale(ax, ay, az, sx, sy, sz),
           pb = view.pixelScale(bx, by, bz, sx, sy, sz);
    if (pa < 1e-9 || pb < 1e-9 || !Double.isFinite(pa) || !Double.isFinite(pb)) return;
    double inner = Math.max(0.0, width * .5 - .5), outer = width * .5 + .5;
    int coreAlpha = (int) Math.round((color >>> 24) * Math.min(1, width));
    if (inner > 0)
      strip(out, ax, ay, az, bx, by, bz, sx, sy, sz, -inner, inner, pa, pb,
          coreAlpha << 24 | color & 0xffffff);
    for (int band = 0; band < FRINGE_STEPS; band++) {
      double low = inner + (outer - inner) * band / FRINGE_STEPS,
             high = inner + (outer - inner) * (band + 1) / FRINGE_STEPS;
      int alpha =
          (int) Math.round((color >>> 24) * Math.min(1, width) * (1 - (band + .5) / FRINGE_STEPS));
      int coverage = alpha << 24 | color & 0xffffff;
      strip(out, ax, ay, az, bx, by, bz, sx, sy, sz, low, high, pa, pb, coverage);
      strip(out, ax, ay, az, bx, by, bz, sx, sy, sz, -high, -low, pa, pb, coverage);
    }
  }

  private static void strip(Quads out, double ax, double ay, double az, double bx, double by,
      double bz, double sx, double sy, double sz, double low, double high, double pa, double pb,
      int color) {
    double al = low / pa, ah = high / pa, bl = low / pb, bh = high / pb;
    out.quad(ax + sx * al, ay + sy * al, az + sz * al, bx + sx * bl, by + sy * bl, bz + sz * bl,
        bx + sx * bh, by + sy * bh, bz + sz * bh, ax + sx * ah, ay + sy * ah, az + sz * ah, color);
  }

  public static int groundRingSegments(
      View view, double cx, double cy, double cz, double radius) {
    if (!Double.isFinite(radius) || radius <= 0) return 96;
    int byRadius = (int) Math.ceil(Math.max(96.0, Math.sqrt(Math.max(1.0, radius)) * 48.0));
    int byScreen = 96;
    if (view != null) {
      double pxPerBlock = view.pixelsPerBlock(cx, cy, cz);
      if (Double.isFinite(pxPerBlock) && pxPerBlock > 0) {
        double pxCirc = pxPerBlock * 2.0 * Math.PI * radius;
        if (Double.isFinite(pxCirc) && pxCirc > 0) {
          byScreen = (int) Math.ceil(pxCirc / 5.0);
        }
      }
    }
    int raw = Math.max(byRadius, byScreen);
    raw = ((raw + 7) / 8) * 8;
    return Math.max(96, Math.min(256, raw));
  }

  public static void groundRing(Quads out, View view, double cx, double cy, double cz,
      double radius, float width, int color, int segments) {
    int baseAlpha = color >>> 24;
    if (out == null || baseAlpha == 0 || !Double.isFinite(radius) || radius <= 0.04
        || !Float.isFinite(width) || width <= 0f) {
      return;
    }
    int segs = Math.max(32, segments);
    double pxPerBlock = 60.0;
    double grazingBoost = 1.0;
    if (view != null) {
      double dx = view.x - cx, dz = view.z - cz;
      double horiz = Math.hypot(dx, dz);
      double sampleX = horiz > 1e-4 ? cx + (dx / horiz) * Math.min(horiz, radius) : cx + radius;
      double sampleZ = horiz > 1e-4 ? cz + (dz / horiz) * Math.min(horiz, radius) : cz;
      double d = Math.max(3.5, view.distance(sampleX, cy, sampleZ));
      double focal = Math.max(view.focalX, view.focalY);
      if (Double.isFinite(focal) && focal > 0) pxPerBlock = focal / d;
      double sinElev = Math.abs(view.y - cy) / d;
      if (sinElev < 0.45) {
        grazingBoost = 1.0 + (0.45 - sinElev) * 0.78;
      }
    }
    double clampedPx = Math.max(8.0, pxPerBlock);
    double halfCore =
        Math.max(0.026 * width, (width * 0.65) / clampedPx) * grazingBoost;
    halfCore = Math.min(halfCore, radius * 0.22);
    double feather = Math.max(halfCore * 0.45, 1.15 / clampedPx);
    feather = Math.min(feather, Math.min(radius * 0.14, halfCore * 1.4));

    double rIn = Math.max(0.0, radius - halfCore);
    double rOut = radius + halfCore;
    double rInMid = Math.max(0.0, rIn - feather * 0.45);
    double rInFar = Math.max(0.0, rIn - feather);
    double rOutMid = rOut + feather * 0.45;
    double rOutFar = rOut + feather;

    int rgb = color & 0x00ffffff;
    int midAlpha = Math.max(0, Math.min(255, (int) Math.round(baseAlpha * 0.54)));
    int farAlpha = Math.max(0, Math.min(255, (int) Math.round(baseAlpha * 0.18)));
    int midCol = (midAlpha << 24) | rgb;
    int farCol = (farAlpha << 24) | rgb;

    double[][] trig = unitCircle(segs);
    double[] cos = trig[0];
    double[] sin = trig[1];

    if (farAlpha > 0 && rInMid > rInFar + 1e-6) {
      annulusBand(out, cx, cy, cz, cos, sin, segs, rInFar, rInMid, farCol);
    }
    if (midAlpha > 0 && rIn > rInMid + 1e-6) {
      annulusBand(out, cx, cy, cz, cos, sin, segs, rInMid, rIn, midCol);
    }
    annulusBand(out, cx, cy, cz, cos, sin, segs, rIn, rOut, color);
    if (midAlpha > 0 && rOutMid > rOut + 1e-6) {
      annulusBand(out, cx, cy, cz, cos, sin, segs, rOut, rOutMid, midCol);
    }
    if (farAlpha > 0 && rOutFar > rOutMid + 1e-6) {
      annulusBand(out, cx, cy, cz, cos, sin, segs, rOutMid, rOutFar, farCol);
    }
  }

  private static void annulusBand(Quads out, double cx, double cy, double cz, double[] cos,
      double[] sin, int segs, double r0, double r1, int color) {
    if ((color >>> 24) == 0 || r1 <= r0 + 1e-7) return;
    double in0x = cx + cos[0] * r0, in0z = cz + sin[0] * r0;
    double out0x = cx + cos[0] * r1, out0z = cz + sin[0] * r1;
    for (int i = 1; i <= segs; i++) {
      double c = cos[i], s = sin[i];
      double in1x = cx + c * r0, in1z = cz + s * r0;
      double out1x = cx + c * r1, out1z = cz + s * r1;
      out.quad(in0x, cy, in0z, in1x, cy, in1z, out1x, cy, out1z, out0x, cy, out0z, color);
      in0x = in1x;
      in0z = in1z;
      out0x = out1x;
      out0z = out1z;
    }
  }

  public static void groundDisc(Quads out, double cx, double cy, double cz, double radius,
      int fillColor, int segments) {
    if (out == null || (fillColor >>> 24) == 0 || !Double.isFinite(radius) || radius <= 0.04) {
      return;
    }
    int segs = Math.max(32, (segments + 1) & ~1);
    double[][] trig = unitCircle(segs);
    double[] cos = trig[0];
    double[] sin = trig[1];
    for (int i = 0; i < segs; i += 2) {
      out.quad(cx, cy, cz, cx + cos[i + 2] * radius, cy, cz + sin[i + 2] * radius,
          cx + cos[i + 1] * radius, cy, cz + sin[i + 1] * radius, cx + cos[i] * radius, cy,
          cz + sin[i] * radius, fillColor);
    }
  }
}
