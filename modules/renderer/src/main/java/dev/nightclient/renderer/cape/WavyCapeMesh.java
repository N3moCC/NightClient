package dev.nightclient.renderer.cape;

import java.util.Arrays;

public final class WavyCapeMesh {
  public float[] xyz;
  public float[] nrm;
  public float[] uv;
  public float[] shade;
  public int quadCount;

  public WavyCapeMesh(int maxQuads) {
    allocate(Math.max(16, maxQuads));
  }

  private void allocate(int maxQuads) {
    xyz = new float[maxQuads * 12];
    nrm = new float[maxQuads * 12];
    uv = new float[maxQuads * 8];
    shade = new float[maxQuads * 4];
  }

  public void reset(int requiredQuads) {
    if (xyz.length < requiredQuads * 12) {
      allocate(requiredQuads);
    }
    quadCount = 0;
  }

  public void addQuad(
      float x0, float y0, float z0, float nx0, float ny0, float nz0, float u0, float v0, float s0,
      float x1, float y1, float z1, float nx1, float ny1, float nz1, float u1, float v1, float s1,
      float x2, float y2, float z2, float nx2, float ny2, float nz2, float u2, float v2, float s2,
      float x3, float y3, float z3, float nx3, float ny3, float nz3, float u3, float v3, float s3) {
    int q = quadCount++;
    int p = q * 12;
    xyz[p] = x0; xyz[p + 1] = y0; xyz[p + 2] = z0;
    xyz[p + 3] = x1; xyz[p + 4] = y1; xyz[p + 5] = z1;
    xyz[p + 6] = x2; xyz[p + 7] = y2; xyz[p + 8] = z2;
    xyz[p + 9] = x3; xyz[p + 10] = y3; xyz[p + 11] = z3;

    nrm[p] = nx0; nrm[p + 1] = ny0; nrm[p + 2] = nz0;
    nrm[p + 3] = nx1; nrm[p + 4] = ny1; nrm[p + 5] = nz1;
    nrm[p + 6] = nx2; nrm[p + 7] = ny2; nrm[p + 8] = nz2;
    nrm[p + 9] = nx3; nrm[p + 10] = ny3; nrm[p + 11] = nz3;

    int t = q * 8;
    uv[t] = u0; uv[t + 1] = v0;
    uv[t + 2] = u1; uv[t + 3] = v1;
    uv[t + 4] = u2; uv[t + 5] = v2;
    uv[t + 6] = u3; uv[t + 7] = v3;

    int c = q * 4;
    shade[c] = s0; shade[c + 1] = s1; shade[c + 2] = s2; shade[c + 3] = s3;
  }

  public void addFlatQuad(
      float x0, float y0, float z0, float u0, float v0, float s0,
      float x1, float y1, float z1, float u1, float v1, float s1,
      float x2, float y2, float z2, float u2, float v2, float s2,
      float x3, float y3, float z3, float u3, float v3, float s3,
      float fallbackNx, float fallbackNy, float fallbackNz) {
    float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
    float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;
    float nx = e1y * e2z - e1z * e2y;
    float ny = e1z * e2x - e1x * e2z;
    float nz = e1x * e2y - e1y * e2x;
    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (len < 1e-6f) {
      nx = fallbackNx;
      ny = fallbackNy;
      nz = fallbackNz;
    } else {
      float inv = 1.0f / len;
      nx *= inv;
      ny *= inv;
      nz *= inv;
    }
    addQuad(
        x0, y0, z0, nx, ny, nz, u0, v0, s0,
        x1, y1, z1, nx, ny, nz, u1, v1, s1,
        x2, y2, z2, nx, ny, nz, u2, v2, s2,
        x3, y3, z3, nx, ny, nz, u3, v3, s3);
  }

  public WavyCapeMesh snapshot() {
    WavyCapeMesh copy = new WavyCapeMesh(Math.max(1, quadCount));
    copy.quadCount = quadCount;
    if (quadCount > 0) {
      System.arraycopy(xyz, 0, copy.xyz, 0, quadCount * 12);
      System.arraycopy(nrm, 0, copy.nrm, 0, quadCount * 12);
      System.arraycopy(uv, 0, copy.uv, 0, quadCount * 8);
      System.arraycopy(shade, 0, copy.shade, 0, quadCount * 4);
    }
    return copy;
  }

  public boolean hasNaN() {
    for (int i = 0; i < quadCount * 12; i++) {
      if (!Float.isFinite(xyz[i]) || !Float.isFinite(nrm[i])) return true;
    }
    for (int i = 0; i < quadCount * 8; i++) {
      if (!Float.isFinite(uv[i])) return true;
    }
    for (int i = 0; i < quadCount * 4; i++) {
      if (!Float.isFinite(shade[i])) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "WavyCapeMesh{quads=" + quadCount + ", verts=" + (quadCount * 4) + "}";
  }
}
