package dev.nightclient.api;

public final class ViewmodelProjection {
  private final float[] clip, inverse;
  public final int width, height;
  public final float anchorX, anchorY, anchorZ, x, y, z, rotateX, rotateY, rotateZ;

  public ViewmodelProjection(float[] columnMajor, int width, int height, float anchorX,
      float anchorY, float anchorZ, float x, float y, float z, float rotateX, float rotateY,
      float rotateZ) {
    if (columnMajor == null || columnMajor.length != 16 || width < 1 || height < 1)
      throw new IllegalArgumentException("Invalid hand projection");
    this.clip = columnMajor.clone();
    for (float value : clip)
      if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite hand projection");
    this.inverse = invert(clip);
    this.width = width;
    this.height = height;
    this.anchorX = anchorX;
    this.anchorY = anchorY;
    this.anchorZ = anchorZ;
    this.x = x;
    this.y = y;
    this.z = z;
    this.rotateX = rotateX;
    this.rotateY = rotateY;
    this.rotateZ = rotateZ;
  }

  public float[] project(float localX, float localY, float localZ) {
    float[] p =
        transform(clip, anchorX + x + localX, anchorY + y + localY, anchorZ + z + localZ, 1);
    if (p[3] <= .00001f) return null;
    return new float[] {
        (p[0] / p[3] + 1) * width * .5f, (1 - p[1] / p[3]) * height * .5f, p[2] / p[3]};
  }

  public float[] pivot() {
    return project(0, 0, 0);
  }

  public float[] axis(String operation, int axis) {
    float[] v = new float[3];
    v[axis] = 1;
    if ("G".equals(operation)) return v;
    if ("R".equals(operation)) {
      if (axis > 0) rotate(v, 0, rotateX);
      if (axis > 1) {
        v = new float[] {0, 0, 1};
        rotate(v, 1, rotateY);
        rotate(v, 0, rotateX);
      }
      return v;
    }
    rotate(v, 2, rotateZ);
    rotate(v, 1, rotateY);
    rotate(v, 0, rotateX);
    return v;
  }

  public float[] axisVector(int axis, boolean local) {
    float[] v = new float[3];
    v[axis] = 1;
    if (!local) return v;
    rotate(v, 2, rotateZ);
    rotate(v, 1, rotateY);
    rotate(v, 0, rotateX);
    return v;
  }

  public float[] viewDirection() {
    float[] p = pivot();
    if (p == null) return null;
    float[][] ray = ray(p[0], p[1]);
    if (ray == null) return null;
    float[] d = ray[1].clone();
    float size = (float) Math.sqrt(dot(d, d));
    if (size < .0000001f) return null;
    for (int i = 0; i < 3; i++) d[i] /= size;
    return d;
  }

  public float[][] ring(int axis) {
    return basis(axis("R", axis));
  }

  public static float[][] basis(float[] axis) {
    float[] normal = axis.clone();
    normalize(normal);
    float[] u = new float[] {
        Math.abs(normal[0]) < .8f ? 1 : 0, Math.abs(normal[0]) < .8f ? 0 : 1, 0};
    float dot = dot(u, normal);
    for (int i = 0; i < 3; i++) u[i] -= normal[i] * dot;
    normalize(u);
    float[] v = cross(normal, u);
    normalize(v);
    return new float[][] {u, v, normal};
  }

  public float ringAngle(int axis, float screenX, float screenY) {
    return planeAngle(axis("R", axis), screenX, screenY);
  }

  public float planeAngle(float[] normal, float screenX, float screenY) {
    float[][] basis = basis(normal);
    float[] point = intersection(screenX, screenY, basis[2]);
    return point == null ? Float.NaN
                         : (float) Math.atan2(dot(point, basis[1]), dot(point, basis[0]));
  }

  public float axisPosition(int axis, float screenX, float screenY) {
    return axisPosition(axis("G", axis), screenX, screenY);
  }

  public float axisPosition(float[] direction, float screenX, float screenY) {
    float[][] ray = ray(screenX, screenY);
    if (ray == null) return Float.NaN;
    float[] d = ray[1], origin = ray[0];
    float[] r = {anchorX + x - origin[0], anchorY + y - origin[1], anchorZ + z - origin[2]};
    float b = dot(direction, d), c = dot(d, d), denominator = c - b * b;
    return Math.abs(denominator) < .0000001f
        ? Float.NaN
        : (b * dot(d, r) - c * dot(direction, r)) / denominator;
  }

  public float[] planeMovement(float screenX, float screenY) {
    return intersection(screenX, screenY, new float[] {0, 0, 1});
  }

  public float[] planeMovement(float[] normal, float screenX, float screenY) {
    return intersection(screenX, screenY, normal);
  }

  private float[] intersection(float screenX, float screenY, float[] normal) {
    float[][] ray = ray(screenX, screenY);
    if (ray == null) return null;
    float denominator = dot(normal, ray[1]);
    if (Math.abs(denominator) < .000001f) return null;
    float[] p = {anchorX + x - ray[0][0], anchorY + y - ray[0][1], anchorZ + z - ray[0][2]};
    float t = dot(normal, p) / denominator;
    for (int i = 0; i < 3; i++)
      p[i] = ray[0][i] + ray[1][i] * t
          - (i == 0        ? anchorX + x
                  : i == 1 ? anchorY + y
                           : anchorZ + z);
    return p;
  }

  private float[][] ray(float screenX, float screenY) {
    if (inverse == null) return null;
    float nx = screenX * 2 / width - 1, ny = 1 - screenY * 2 / height;
    float[] near = transform(inverse, nx, ny, -1, 1), far = transform(inverse, nx, ny, 1, 1);
    if (Math.abs(near[3]) < .000001f || Math.abs(far[3]) < .000001f) return null;
    float[] origin = new float[3], direction = new float[3];
    for (int i = 0; i < 3; i++) {
      origin[i] = near[i] / near[3];
      direction[i] = far[i] / far[3] - origin[i];
    }
    return new float[][] {origin, direction};
  }

  public float radius(float logicalPixels) {
    float[] p = pivot();
    if (p == null) return .1f;
    float best = 0;
    for (int i = 0; i < 2; i++) {
      float[] q = project(i == 0 ? .01f : 0, i == 1 ? .01f : 0, 0);
      if (q != null) best = Math.max(best, (float) Math.hypot(q[0] - p[0], q[1] - p[1]));
    }
    return best < .00001f ? .1f : Math.max(.0001f, Math.min(4, logicalPixels * .01f / best));
  }

  private static void rotate(float[] v, int axis, float degrees) {
    float sin = (float) Math.sin(Math.toRadians(degrees)),
          cos = (float) Math.cos(Math.toRadians(degrees));
    int a = (axis + 1) % 3, b = (axis + 2) % 3;
    float first = v[a], second = v[b];
    v[a] = first * cos - second * sin;
    v[b] = first * sin + second * cos;
  }

  private static float dot(float[] a, float[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }

  private static float[] cross(float[] a, float[] b) {
    return new float[] {
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
  }

  private static void normalize(float[] a) {
    float size = (float) Math.sqrt(dot(a, a));
    for (int i = 0; i < 3; i++) a[i] /= size;
  }

  private static float[] transform(float[] m, float x, float y, float z, float w) {
    float[] out = new float[4];
    for (int r = 0; r < 4; r++) out[r] = m[r] * x + m[4 + r] * y + m[8 + r] * z + m[12 + r] * w;
    return out;
  }

  private static float[] invert(float[] m) {
    double[][] a = new double[4][8];
    for (int r = 0; r < 4; r++)
      for (int c = 0; c < 4; c++) {
        a[r][c] = m[c * 4 + r];
        a[r][c + 4] = r == c ? 1 : 0;
      }
    for (int col = 0; col < 4; col++) {
      int pivot = col;
      for (int r = col + 1; r < 4; r++)
        if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
      if (Math.abs(a[pivot][col]) < 1e-12) return null;
      double[] swap = a[col];
      a[col] = a[pivot];
      a[pivot] = swap;
      double divisor = a[col][col];
      for (int c = 0; c < 8; c++) a[col][c] /= divisor;
      for (int r = 0; r < 4; r++)
        if (r != col) {
          double factor = a[r][col];
          for (int c = 0; c < 8; c++) a[r][c] -= factor * a[col][c];
        }
    }
    float[] out = new float[16];
    for (int r = 0; r < 4; r++)
      for (int c = 0; c < 4; c++) out[c * 4 + r] = (float) a[r][c + 4];
    return out;
  }
}
