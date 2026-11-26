package dev.nightclient.renderer.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

public final class CosmeticModelMesh {
  public static final int VERTEX_STRIDE = 36;

  public static final int BONE_WEAPON = 0;
  public static final int BONE_HEAD = 1;
  public static final int BONE_BODY = 2;
  public static final int BONE_RIGHT_ARM = 3;
  public static final int BONE_LEFT_ARM = 4;
  public static final int BONE_RIGHT_LEG = 5;
  public static final int BONE_LEFT_LEG = 6;
  public static final int BONE_RIGHT_BOOT = 7;
  public static final int BONE_LEFT_BOOT = 8;
  public static final int BONE_COUNT = 9;

  public static final int BLEND_OPAQUE = 0;
  public static final int BLEND_TRANSLUCENT = 1;
  public static final int BLEND_ADDITIVE = 2;

  public static final int FLAG_PLASMA_CORE = 1;
  public static final int FLAG_FRESNEL_AURA = 2;
  public static final int FLAG_PULSE_GLOW = 4;

  private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

  public static final class SubMesh {
    public final int boneId;
    public final int firstIndex;
    public final int indexCount;
    public final int blendMode;
    public final boolean doubleSided;

    public SubMesh(int boneId, int firstIndex, int indexCount, int blendMode, boolean doubleSided) {
      this.boneId = boneId;
      this.firstIndex = firstIndex;
      this.indexCount = indexCount;
      this.blendMode = blendMode;
      this.doubleSided = doubleSided;
    }
  }

  public static final class PreviewMesh {
    public final int triangleCount;
    public final float[] positions;
    public final float[] normals;
    public final int[] colors;
    public final byte[] bones;
    public final int[] materialBits;
    public final float[] uvs;
    public final int[] texturePixels;
    public final int textureWidth;
    public final int textureHeight;

    public PreviewMesh(int triangleCount, float[] positions, float[] normals, int[] colors,
        byte[] bones, int[] materialBits) {
      this(triangleCount, positions, normals, colors, bones, materialBits, null, null, 0, 0);
    }

    public PreviewMesh(int triangleCount, float[] positions, float[] normals, int[] colors,
        byte[] bones, int[] materialBits, float[] uvs, int[] texturePixels, int textureWidth,
        int textureHeight) {
      this.triangleCount = triangleCount;
      this.positions = positions;
      this.normals = normals;
      this.colors = colors;
      this.bones = bones;
      this.materialBits = materialBits;
      this.uvs = uvs;
      this.texturePixels = texturePixels;
      this.textureWidth = textureWidth;
      this.textureHeight = textureHeight;
    }
  }

  public final int meshId;
  public final byte[] vertexBytes;
  public final int[] indices;
  public final int vertexCount;
  public final int triangleCount;
  public final SubMesh[] subMeshes;
  public final int[] texturePixels;
  public final int textureWidth;
  public final int textureHeight;
  public final int boneMask;
  public final float minX, minY, minZ;
  public final float maxX, maxY, maxZ;
  public final PreviewMesh preview;

  public CosmeticModelMesh(byte[] vertexBytes, int[] indices, int vertexCount, SubMesh[] subMeshes,
      int[] texturePixels, int textureWidth, int textureHeight, float minX, float minY, float minZ,
      float maxX, float maxY, float maxZ, PreviewMesh preview) {
    this.meshId = NEXT_ID.getAndIncrement();
    this.vertexBytes = vertexBytes;
    this.indices = indices;
    this.vertexCount = vertexCount;
    this.triangleCount = indices.length / 3;
    this.subMeshes = subMeshes;
    this.texturePixels = texturePixels;
    this.textureWidth = textureWidth;
    this.textureHeight = textureHeight;
    int mask = 0;
    for (int i = 0; i < subMeshes.length; i++) {
      int b = subMeshes[i].boneId;
      if (b >= 0 && b < BONE_COUNT) mask |= (1 << b);
    }
    this.boneMask = mask;
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.maxX = maxX;
    this.maxY = maxY;
    this.maxZ = maxZ;
    this.preview = preview;
  }

  public boolean hasBone(int boneId) {
    return boneId >= 0 && boneId < BONE_COUNT && (boneMask & (1 << boneId)) != 0;
  }

  public ByteBuffer copyVerticesToDirectBuffer() {
    ByteBuffer buf = ByteBuffer.allocateDirect(vertexBytes.length).order(ByteOrder.nativeOrder());
    buf.put(vertexBytes);
    buf.flip();
    return buf;
  }

  public ByteBuffer copyIndicesToDirectBuffer() {
    ByteBuffer buf = ByteBuffer.allocateDirect(indices.length * 4).order(ByteOrder.nativeOrder());
    buf.asIntBuffer().put(indices);
    buf.position(0);
    buf.limit(indices.length * 4);
    return buf;
  }

  public static short packByteAnd7Bit(float a01, float b01) {
    int a = clampByte(Math.round(a01 * 255f));
    int b = Math.max(0, Math.min(127, Math.round(b01 * 127f)));
    return (short) (a | (b << 8));
  }

  public static short packByteAndFlags7(float a01, int flags) {
    int a = clampByte(Math.round(a01 * 255f));
    int f = flags & 0x7F;
    return (short) (a | (f << 8));
  }

  public static byte packSignedNormal(float n) {
    int v = Math.round(Math.max(-1f, Math.min(1f, n)) * 127f);
    return (byte) Math.max(-127, Math.min(127, v));
  }

  public static int clampByte(int v) {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
  }
}
