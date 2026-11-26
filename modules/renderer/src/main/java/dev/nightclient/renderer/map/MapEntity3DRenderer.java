package dev.nightclient.renderer.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MapEntity3DRenderer {

  public static final class Part {
    final MapEntityIconComposer.DecodedImage tex;
    final int texW, texH;
    final float x0, y0, z0, dx, dy, dz;
    final float u, v;
    final float pivX, pivY, pivZ;
    final float rotX, rotY, rotZ;
    final boolean fullQuad;
    final boolean emissive;
    final boolean mirror;
    final int tintRgb;

    Part(MapEntityIconComposer.DecodedImage tex, int texW, int texH,
        float x0, float y0, float z0, float dx, float dy, float dz,
        float u, float v, float pivX, float pivY, float pivZ,
        float rotX, float rotY, float rotZ, boolean fullQuad, boolean emissive, boolean mirror,
        int tintRgb) {
      this.tex = tex;
      this.texW = texW;
      this.texH = texH;
      this.x0 = x0; this.y0 = y0; this.z0 = z0;
      this.dx = dx; this.dy = dy; this.dz = dz;
      this.u = u; this.v = v;
      this.pivX = pivX; this.pivY = pivY; this.pivZ = pivZ;
      this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
      this.fullQuad = fullQuad;
      this.emissive = emissive;
      this.mirror = mirror;
      this.tintRgb = tintRgb & 0xFFFFFF;
    }
  }

  public static final class Scene {
    final List<Part> parts = new ArrayList<Part>(16);

    public Scene box(MapEntityIconComposer.DecodedImage tex, int texW, int texH,
        float u, float v, float x, float y, float z, float dx, float dy, float dz) {
      if (tex != null) {
        parts.add(new Part(tex, texW, texH, x, y, z, dx, dy, dz, u, v,
            0, 0, 0, 0, 0, 0, false, false, false, 0xFFFFFF));
      }
      return this;
    }

    public Scene boxRot(MapEntityIconComposer.DecodedImage tex, int texW, int texH,
        float u, float v, float x, float y, float z, float dx, float dy, float dz,
        float pivX, float pivY, float pivZ, float rotX, float rotY, float rotZ) {
      if (tex != null) {
        parts.add(new Part(tex, texW, texH, x, y, z, dx, dy, dz, u, v,
            pivX, pivY, pivZ, rotX, rotY, rotZ, false, false, false, 0xFFFFFF));
      }
      return this;
    }

    public Scene boxEmissive(MapEntityIconComposer.DecodedImage tex, int texW, int texH,
        float u, float v, float x, float y, float z, float dx, float dy, float dz, float inflate) {
      if (tex != null) {
        parts.add(new Part(tex, texW, texH, x - inflate, y - inflate, z - inflate,
            dx + inflate * 2f, dy + inflate * 2f, dz + inflate * 2f, u, v,
            0, 0, 0, 0, 0, 0, false, true, false, 0xFFFFFF));
      }
      return this;
    }

    public Scene boxTinted(MapEntityIconComposer.DecodedImage tex, int texW, int texH,
        float u, float v, float x, float y, float z, float dx, float dy, float dz, int tintRgb) {
      if (tex != null) {
        parts.add(new Part(tex, texW, texH, x, y, z, dx, dy, dz, u, v,
            0, 0, 0, 0, 0, 0, false, false, false, tintRgb));
      }
      return this;
    }

    public Scene boxRotEmissive(MapEntityIconComposer.DecodedImage tex, int texW, int texH,
        float u, float v, float x, float y, float z, float dx, float dy, float dz,
        float pivX, float pivY, float pivZ, float rotX, float rotY, float rotZ) {
      if (tex != null) {
        parts.add(new Part(tex, texW, texH, x, y, z, dx, dy, dz, u, v,
            pivX, pivY, pivZ, rotX, rotY, rotZ, false, true, false, 0xFFFFFF));
      }
      return this;
    }

    public Scene spriteQuad(MapEntityIconComposer.DecodedImage tex,
        float x, float y, float z, float w, float h,
        float pivX, float pivY, float pivZ, float rotY) {
      if (tex != null) {
        parts.add(new Part(tex, tex.width, tex.height, x, y, z, w, h, 0f, 0f, 0f,
            pivX, pivY, pivZ, 0f, rotY, 0f, true, false, false, 0xFFFFFF));
      }
      return this;
    }

    public Scene spriteQuadTop(MapEntityIconComposer.DecodedImage tex,
        float x, float y, float z, float w, float d) {
      if (tex != null) {
        parts.add(new Part(tex, tex.width, tex.height, x, y, z, w, 0f, d, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, true, false, false, 0xFFFFFF));
      }
      return this;
    }
  }

  private static final class Quad {
    final float[] px = new float[4], py = new float[4], pz = new float[4];
    final float[] pu = new float[4], pv = new float[4];
    final MapEntityIconComposer.DecodedImage tex;
    final float shade;
    final int tintRgb;
    final boolean doubleSided;

    Quad(MapEntityIconComposer.DecodedImage tex, float shade, int tintRgb, boolean doubleSided) {
      this.tex = tex;
      this.shade = shade;
      this.tintRgb = tintRgb;
      this.doubleSided = doubleSided;
    }
  }

  private MapEntity3DRenderer() {}

  public static boolean render(Scene scene, int[] out, int size, int margin,
      float camYawDeg, float camPitchDeg) {
    return render(scene, out, size, margin, camYawDeg, camPitchDeg, 0f);
  }

  public static boolean render(Scene scene, int[] out, int size, int margin,
      float camYawDeg, float camPitchDeg, float camRollDeg) {
    if (scene == null || scene.parts.isEmpty() || out == null || out.length < size * size) {
      return false;
    }
    List<Quad> quads = new ArrayList<Quad>(scene.parts.size() * 6);
    float yaw = (float) Math.toRadians(camYawDeg);
    float pitch = (float) Math.toRadians(camPitchDeg);
    float roll = (float) Math.toRadians(camRollDeg);
    float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
    float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
    float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);

    for (int i = 0; i < scene.parts.size(); i++) {
      Part p = scene.parts.get(i);
      emitPartQuads(p, quads, cy, sy, cp, sp, cr, sr);
    }
    if (quads.isEmpty()) return false;

    float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
    float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
    float[] uvBounds = new float[4];

    for (int i = 0; i < quads.size(); i++) {
      Quad q = quads.get(i);
      float cross = (q.px[1] - q.px[0]) * (q.py[2] - q.py[0]) - (q.py[1] - q.py[0]) * (q.px[2] - q.px[0]);
      if (!q.doubleSided && cross <= 0f) continue;
      if (!opaqueUvFraction(q, uvBounds)) continue;
      float s0 = uvBounds[0], s1 = uvBounds[1], t0 = uvBounds[2], t1 = uvBounds[3];
      for (int ci = 0; ci < 4; ci++) {
        float su = (ci == 0 || ci == 3) ? s0 : s1;
        float tv = (ci == 0 || ci == 1) ? t0 : t1;
        float bx = bilerp(q.px[0], q.px[1], q.px[2], q.px[3], su, tv);
        float by = bilerp(q.py[0], q.py[1], q.py[2], q.py[3], su, tv);
        if (bx < minX) minX = bx;
        if (bx > maxX) maxX = bx;
        if (by < minY) minY = by;
        if (by > maxY) maxY = by;
      }
    }
    float spanX = maxX - minX;
    float spanY = maxY - minY;
    if (!(spanX > 1e-4f) || !(spanY > 1e-4f)) return false;

    float avail = Math.max(4f, size - margin * 2f);
    float scale = Math.min(avail / spanX, avail / spanY);
    float midX = (minX + maxX) * 0.5f;
    float midY = (minY + maxY) * 0.5f;
    float center = size * 0.5f;

    float[] zbuf = new float[size * size];
    Arrays.fill(zbuf, Float.POSITIVE_INFINITY);

    for (int pass = 0; pass < 2; pass++) {
      boolean translucentPass = pass == 1;
      for (int i = 0; i < quads.size(); i++) {
        Quad q = quads.get(i);
        float sx0 = center + (q.px[0] - midX) * scale;
        float sy0 = center + (q.py[0] - midY) * scale;
        float sx1 = center + (q.px[1] - midX) * scale;
        float sy1 = center + (q.py[1] - midY) * scale;
        float sx2 = center + (q.px[2] - midX) * scale;
        float sy2 = center + (q.py[2] - midY) * scale;
        float sx3 = center + (q.px[3] - midX) * scale;
        float sy3 = center + (q.py[3] - midY) * scale;

        float cross = (sx1 - sx0) * (sy2 - sy0) - (sy1 - sy0) * (sx2 - sx0);
        if (!q.doubleSided && cross <= 0f) continue;

        rasterTriangle(out, zbuf, size, q.tex, q.shade, q.tintRgb, translucentPass,
            sx0, sy0, q.pz[0], q.pu[0], q.pv[0],
            sx1, sy1, q.pz[1], q.pu[1], q.pv[1],
            sx2, sy2, q.pz[2], q.pu[2], q.pv[2]);
        rasterTriangle(out, zbuf, size, q.tex, q.shade, q.tintRgb, translucentPass,
            sx0, sy0, q.pz[0], q.pu[0], q.pv[0],
            sx2, sy2, q.pz[2], q.pu[2], q.pv[2],
            sx3, sy3, q.pz[3], q.pu[3], q.pv[3]);
      }
    }
    return true;
  }

  private static float bilerp(float v0, float v1, float v2, float v3, float s, float t) {
    float top = v0 + (v1 - v0) * s;
    float bot = v3 + (v2 - v3) * s;
    return top + (bot - top) * t;
  }

  private static boolean opaqueUvFraction(Quad q, float[] dst) {
    int tw = q.tex.width, th = q.tex.height;
    float uA = q.pu[0], uB = q.pu[1];
    float vA = q.pv[0], vB = q.pv[3];
    float uLo = Math.min(uA, uB), uHi = Math.max(uA, uB);
    float vLo = Math.min(vA, vB), vHi = Math.max(vA, vB);
    int x0 = Math.max(0, (int) Math.floor(uLo * tw));
    int x1 = Math.min(tw - 1, (int) Math.ceil(uHi * tw) - 1);
    int y0 = Math.max(0, (int) Math.floor(vLo * th));
    int y1 = Math.min(th - 1, (int) Math.ceil(vHi * th) - 1);
    if (x1 < x0 || y1 < y0) return false;

    int minTx = Integer.MAX_VALUE, maxTx = Integer.MIN_VALUE;
    int minTy = Integer.MAX_VALUE, maxTy = Integer.MIN_VALUE;
    for (int ty = y0; ty <= y1; ty++) {
      int row = ty * tw;
      for (int tx = x0; tx <= x1; tx++) {
        if ((q.tex.argb[row + tx] >>> 24) >= 24) {
          if (tx < minTx) minTx = tx;
          if (tx > maxTx) maxTx = tx;
          if (ty < minTy) minTy = ty;
          if (ty > maxTy) maxTy = ty;
        }
      }
    }
    if (minTx > maxTx) return false;
    float du = uB - uA;
    float dv = vB - vA;
    float fu0 = minTx / (float) tw, fu1 = (maxTx + 1) / (float) tw;
    float fv0 = minTy / (float) th, fv1 = (maxTy + 1) / (float) th;
    float s0 = Math.abs(du) > 1e-5f ? (fu0 - uA) / du : 0f;
    float s1 = Math.abs(du) > 1e-5f ? (fu1 - uA) / du : 1f;
    float t0 = Math.abs(dv) > 1e-5f ? (fv0 - vA) / dv : 0f;
    float t1 = Math.abs(dv) > 1e-5f ? (fv1 - vA) / dv : 1f;
    dst[0] = Math.max(0f, Math.min(1f, Math.min(s0, s1)));
    dst[1] = Math.max(0f, Math.min(1f, Math.max(s0, s1)));
    dst[2] = Math.max(0f, Math.min(1f, Math.min(t0, t1)));
    dst[3] = Math.max(0f, Math.min(1f, Math.max(t0, t1)));
    return true;
  }

  private static boolean hasOpaqueTexels(MapEntityIconComposer.DecodedImage tex,
      float u0, float v0, float u1, float v1) {
    int tw = tex.width, th = tex.height;
    int x0 = Math.max(0, (int) Math.floor(Math.min(u0, u1) * tw));
    int x1 = Math.min(tw - 1, (int) Math.ceil(Math.max(u0, u1) * tw) - 1);
    int y0 = Math.max(0, (int) Math.floor(Math.min(v0, v1) * th));
    int y1 = Math.min(th - 1, (int) Math.ceil(Math.max(v0, v1) * th) - 1);
    if (x1 < x0 || y1 < y0) return false;
    for (int y = y0; y <= y1; y++) {
      int row = y * tw;
      for (int x = x0; x <= x1; x++) {
        if ((tex.argb[row + x] >>> 24) >= 24) return true;
      }
    }
    return false;
  }

  private static void emitPartQuads(Part p, List<Quad> out,
      float cy, float sy, float cp, float sp, float cr, float sr) {
    float x0 = p.x0, y0 = p.y0, z0 = p.z0;
    float x1 = x0 + p.dx, y1 = y0 + p.dy, z1 = z0 + p.dz;
    float u = p.u, v = p.v;
    float dx = p.dx, dy = p.dy, dz = p.dz;
    float tw = p.texW, th = p.texH;

    if (p.fullQuad) {
      if (dy <= 0.001f) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, 1.0f, true,
            x0, y0, z1, 0f, 0f,
            x1, y0, z1, 1f, 0f,
            x1, y0, z0, 1f, 1f,
            x0, y0, z0, 0f, 1f);
      } else {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, 1.0f, true,
            x0, y0, z0, 0f, 0f,
            x1, y0, z0, 1f, 0f,
            x1, y1, z0, 1f, 1f,
            x0, y1, z0, 0f, 1f);
      }
      return;
    }

    if (dz <= 0.001f) {
      float shade = p.emissive ? 1.0f : 0.96f;
      if (hasOpaqueTexels(p.tex, u / tw, v / th, (u + dx) / tw, (v + dy) / th)) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, shade, true,
            x0, y0, z0, u / tw, v / th,
            x1, y0, z0, (u + dx) / tw, v / th,
            x1, y1, z0, (u + dx) / tw, (v + dy) / th,
            x0, y1, z0, u / tw, (v + dy) / th);
      } else if (hasOpaqueTexels(p.tex, (u + dx) / tw, v / th, (u + dx + dx) / tw, (v + dy) / th)) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, shade, true,
            x1, y0, z0, (u + dx) / tw, v / th,
            x0, y0, z0, (u + dx + dx) / tw, v / th,
            x0, y1, z0, (u + dx + dx) / tw, (v + dy) / th,
            x1, y1, z0, (u + dx) / tw, (v + dy) / th);
      }
      return;
    }
    if (dx <= 0.001f) {
      float shade = p.emissive ? 1.0f : 0.92f;
      boolean hasRight = hasOpaqueTexels(p.tex, u / tw, (v + dz) / th, (u + dz) / tw, (v + dz + dy) / th);
      boolean hasLeft = hasOpaqueTexels(p.tex, (u + dz) / tw, (v + dz) / th, (u + dz + dz) / tw, (v + dz + dy) / th);
      if (hasRight) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, shade, true,
            x0, y0, z1, u / tw, (v + dz) / th,
            x0, y0, z0, (u + dz) / tw, (v + dz) / th,
            x0, y1, z0, (u + dz) / tw, (v + dz + dy) / th,
            x0, y1, z1, u / tw, (v + dz + dy) / th);
      }
      if (hasLeft && !hasRight) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, shade, true,
            x0, y0, z0, (u + dz) / tw, (v + dz) / th,
            x0, y0, z1, (u + dz + dz) / tw, (v + dz) / th,
            x0, y1, z1, (u + dz + dz) / tw, (v + dz + dy) / th,
            x0, y1, z0, (u + dz) / tw, (v + dz + dy) / th);
      }
      return;
    }
    if (dy <= 0.001f) {
      float shade = p.emissive ? 1.0f : 0.98f;
      boolean hasTop = hasOpaqueTexels(p.tex, (u + dz) / tw, v / th, (u + dz + dx) / tw, (v + dz) / th);
      boolean hasBot = hasOpaqueTexels(p.tex, (u + dz + dx) / tw, v / th, (u + dz + dx + dx) / tw, (v + dz) / th);
      if (hasTop) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, shade, true,
            x0, y0, z1, (u + dz) / tw, v / th,
            x1, y0, z1, (u + dz + dx) / tw, v / th,
            x1, y0, z0, (u + dz + dx) / tw, (v + dz) / th,
            x0, y0, z0, (u + dz) / tw, (v + dz) / th);
      }
      if (hasBot && !hasTop) {
        addQuad(out, p, cy, sy, cp, sp, cr, sr, shade, true,
            x0, y0, z0, (u + dz + dx) / tw, v / th,
            x1, y0, z0, (u + dz + dx + dx) / tw, v / th,
            x1, y0, z1, (u + dz + dx + dx) / tw, (v + dz) / th,
            x0, y0, z1, (u + dz + dx) / tw, (v + dz) / th);
      }
      return;
    }

    float sFront = p.emissive ? 1.0f : 0.98f;
    float sTop = p.emissive ? 1.0f : 1.00f;
    float sSide = p.emissive ? 1.0f : 0.84f;
    float sBack = p.emissive ? 1.0f : 0.80f;
    float sBot = p.emissive ? 1.0f : 0.68f;

    addQuad(out, p, cy, sy, cp, sp, cr, sr, sFront, false,
        x0, y0, z0, (u + dz) / tw, (v + dz) / th,
        x1, y0, z0, (u + dz + dx) / tw, (v + dz) / th,
        x1, y1, z0, (u + dz + dx) / tw, (v + dz + dy) / th,
        x0, y1, z0, (u + dz) / tw, (v + dz + dy) / th);

    addQuad(out, p, cy, sy, cp, sp, cr, sr, sBack, false,
        x1, y0, z1, (u + dz + dx + dz) / tw, (v + dz) / th,
        x0, y0, z1, (u + dz + dx + dz + dx) / tw, (v + dz) / th,
        x0, y1, z1, (u + dz + dx + dz + dx) / tw, (v + dz + dy) / th,
        x1, y1, z1, (u + dz + dx + dz) / tw, (v + dz + dy) / th);

    addQuad(out, p, cy, sy, cp, sp, cr, sr, sSide, false,
        x0, y0, z1, u / tw, (v + dz) / th,
        x0, y0, z0, (u + dz) / tw, (v + dz) / th,
        x0, y1, z0, (u + dz) / tw, (v + dz + dy) / th,
        x0, y1, z1, u / tw, (v + dz + dy) / th);

    addQuad(out, p, cy, sy, cp, sp, cr, sr, sSide, false,
        x1, y0, z0, (u + dz + dx) / tw, (v + dz) / th,
        x1, y0, z1, (u + dz + dx + dz) / tw, (v + dz) / th,
        x1, y1, z1, (u + dz + dx + dz) / tw, (v + dz + dy) / th,
        x1, y1, z0, (u + dz + dx) / tw, (v + dz + dy) / th);

    addQuad(out, p, cy, sy, cp, sp, cr, sr, sTop, false,
        x0, y0, z1, (u + dz) / tw, v / th,
        x1, y0, z1, (u + dz + dx) / tw, v / th,
        x1, y0, z0, (u + dz + dx) / tw, (v + dz) / th,
        x0, y0, z0, (u + dz) / tw, (v + dz) / th);

    addQuad(out, p, cy, sy, cp, sp, cr, sr, sBot, false,
        x0, y1, z0, (u + dz + dx) / tw, v / th,
        x1, y1, z0, (u + dz + dx + dx) / tw, v / th,
        x1, y1, z1, (u + dz + dx + dx) / tw, (v + dz) / th,
        x0, y1, z1, (u + dz + dx) / tw, (v + dz) / th);
  }

  private static void addQuad(List<Quad> out, Part p,
      float cy, float sy, float cp, float sp, float cr, float sr,
      float shade, boolean doubleSided,
      float x0, float y0, float z0, float u0, float v0,
      float x1, float y1, float z1, float u1, float v1,
      float x2, float y2, float z2, float u2, float v2,
      float x3, float y3, float z3, float u3, float v3) {
    Quad q = new Quad(p.tex, shade, p.tintRgb, doubleSided);
    float[] xs = { x0, x1, x2, x3 }, ys = { y0, y1, y2, y3 }, zs = { z0, z1, z2, z3 };
    q.pu[0] = u0; q.pv[0] = v0;
    q.pu[1] = u1; q.pv[1] = v1;
    q.pu[2] = u2; q.pv[2] = v2;
    q.pu[3] = u3; q.pv[3] = v3;

    boolean hasPartRot = p.rotX != 0f || p.rotY != 0f || p.rotZ != 0f;
    float crx = hasPartRot ? (float) Math.cos(p.rotX) : 1f;
    float srx = hasPartRot ? (float) Math.sin(p.rotX) : 0f;
    float cry = hasPartRot ? (float) Math.cos(p.rotY) : 1f;
    float sry = hasPartRot ? (float) Math.sin(p.rotY) : 0f;
    float crz = hasPartRot ? (float) Math.cos(p.rotZ) : 1f;
    float srz = hasPartRot ? (float) Math.sin(p.rotZ) : 0f;

    for (int k = 0; k < 4; k++) {
      float lx = xs[k], ly = ys[k], lz = zs[k];
      if (hasPartRot) {
        float tx = lx * crz - ly * srz;
        float ty = lx * srz + ly * crz;
        lx = tx; ly = ty;

        tx = lx * cry + lz * sry;
        float tz = -lx * sry + lz * cry;
        lx = tx; lz = tz;

        ty = ly * crx - lz * srx;
        tz = ly * srx + lz * crx;
        ly = ty; lz = tz;
      }
      float wx = lx + p.pivX;
      float wy = ly + p.pivY;
      float wz = lz + p.pivZ;

      float rx = wx * cy + wz * sy;
      float rz = -wx * sy + wz * cy;
      float ry = wy * cp - rz * sp;
      float rz2 = wy * sp + rz * cp;

      float fx = rx * cr - ry * sr;
      float fy = rx * sr + ry * cr;

      q.px[k] = fx;
      q.py[k] = fy;
      q.pz[k] = rz2;
    }
    out.add(q);
  }

  private static void rasterTriangle(int[] out, float[] zbuf, int size,
      MapEntityIconComposer.DecodedImage tex, float shade, int tintRgb, boolean translucentPass,
      float x0, float y0, float z0, float u0, float v0,
      float x1, float y1, float z1, float u1, float v1,
      float x2, float y2, float z2, float u2, float v2) {
    float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    if (Math.abs(area) < 1e-5f) return;
    float invArea = 1f / area;

    int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
    int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
    int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
    int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));

    int tw = tex.width, th = tex.height;
    int tr = (tintRgb >> 16) & 0xFF, tg = (tintRgb >> 8) & 0xFF, tb = tintRgb & 0xFF;
    boolean hasTint = tintRgb != 0xFFFFFF;

    for (int py = minY; py <= maxY; py++) {
      float cy = py + 0.5f;
      for (int px = minX; px <= maxX; px++) {
        float cx = px + 0.5f;
        float w0 = ((x1 - cx) * (y2 - cy) - (y1 - cy) * (x2 - cx)) * invArea;
        float w1 = ((x2 - cx) * (y0 - cy) - (y2 - cy) * (x0 - cx)) * invArea;
        float w2 = 1f - w0 - w1;
        if (w0 < -0.001f || w1 < -0.001f || w2 < -0.001f) continue;

        float z = w0 * z0 + w1 * z1 + w2 * z2;
        int idx = py * size + px;
        if (z > zbuf[idx] + 0.002f) continue;

        float fu = w0 * u0 + w1 * u1 + w2 * u2;
        float fv = w0 * v0 + w1 * v1 + w2 * v2;
        int tx = Math.max(0, Math.min(tw - 1, (int) Math.floor(fu * tw)));
        int ty = Math.max(0, Math.min(th - 1, (int) Math.floor(fv * th)));
        int argb = tex.argb[ty * tw + tx];
        int a = argb >>> 24;
        if (a < 24) continue;
        boolean opaque = a >= 245;
        if (opaque == translucentPass) continue;

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (hasTint) {
          r = (r * tr) / 255;
          g = (g * tg) / 255;
          b = (b * tb) / 255;
        }
        r = Math.min(255, Math.round(r * shade));
        g = Math.min(255, Math.round(g * shade));
        b = Math.min(255, Math.round(b * shade));

        if (opaque || (out[idx] >>> 24) == 0) {
          out[idx] = (a << 24) | (r << 16) | (g << 8) | b;
          zbuf[idx] = z;
        } else {
          int bg = out[idx];
          int inv = 255 - a;
          int rr = (r * a + ((bg >> 16) & 0xFF) * inv) / 255;
          int rg = (g * a + ((bg >> 8) & 0xFF) * inv) / 255;
          int rb = (b * a + (bg & 0xFF) * inv) / 255;
          int oa = Math.min(255, a + ((bg >>> 24) * inv) / 255);
          out[idx] = (oa << 24) | (rr << 16) | (rg << 8) | rb;
        }
      }
    }
  }
}
