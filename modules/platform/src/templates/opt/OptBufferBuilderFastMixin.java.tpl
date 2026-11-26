package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import @@VERTEX_FORMAT_IMPORT@@;
import dev.nightclient.platform.FastEntityVertexWriter;
import java.nio.ByteOrder;
import net.minecraft.util.ARGB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
@@FAST_BB_EXTRA_IMPORTS@@

@Mixin(BufferBuilder.class)
public abstract class OptBufferBuilderFastMixin implements @@FAST_BB_INTERFACES@@FastEntityVertexWriter {
  @Unique private static final boolean NC_LITTLE_ENDIAN =
      ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

  @Shadow @Final private ByteBufferBuilder buffer;
  @Shadow private long vertexPointer;
  @Shadow private int vertices;
  @Shadow @Final private VertexFormat format;
  @@FAST_BB_FORMAT_SHADOWS@@
  @Shadow @Final private int vertexSize;
  @Shadow private int elementsToFill;
  @Shadow private boolean building;

  @Shadow private void ensureBuilding() {}
  @Shadow private void endLastVertex() {}

  @Unique
  private static int ncPackNormal(float nx, float ny, float nz) {
    float cx = nx < -1.0f ? -1.0f : (nx > 1.0f ? 1.0f : nx);
    float cy = ny < -1.0f ? -1.0f : (ny > 1.0f ? 1.0f : ny);
    float cz = nz < -1.0f ? -1.0f : (nz > 1.0f ? 1.0f : nz);
    int bx = ((int) (cx * 127.0f)) & 0xFF;
    int by = ((int) (cy * 127.0f)) & 0xFF;
    int bz = ((int) (cz * 127.0f)) & 0xFF;
    return bx | (by << 8) | (bz << 16);
  }

  @@FAST_BB_BULK_QUAD_BODY@@

  @Override
  public boolean nightclient$emitFlatCube(
      float[] flatQuads,
      Matrix4f pose,
      Matrix3f normal,
      boolean trustedNormals,
      int packedLight,
      int packedOverlay,
      int color) {
    @@FAST_BB_CUBE_GUARD@@
    final int len = flatQuads.length;
    if (len == 0) return true;
    final int quadCount = len / 23;
    final int vertCount = quadCount << 2;
    if (this.vertices + vertCount >= 16777215) return false;

    this.ensureBuilding();
    this.endLastVertex();
    long ptr = this.buffer.reserve(vertCount * stride);
    this.vertices += vertCount;
    this.vertexPointer = ptr + (long) (vertCount - 1) * stride;
    this.elementsToFill = 0;

    final int abgr = ARGB.toABGR(color);

    final float m00 = pose.m00(), m10 = pose.m10(), m20 = pose.m20(), m30 = pose.m30();
    final float m01 = pose.m01(), m11 = pose.m11(), m21 = pose.m21(), m31 = pose.m31();
    final float m02 = pose.m02(), m12 = pose.m12(), m22 = pose.m22(), m32 = pose.m32();

    final float n00 = normal.m00(), n10 = normal.m10(), n20 = normal.m20();
    final float n01 = normal.m01(), n11 = normal.m11(), n21 = normal.m21();
    final float n02 = normal.m02(), n12 = normal.m12(), n22 = normal.m22();

    if (full) {
      for (int i = 0; i < len; i += 23) {
        float fnx = flatQuads[i];
        float fny = flatQuads[i + 1];
        float fnz = flatQuads[i + 2];
        float nx = n00 * fnx + n10 * fny + n20 * fnz;
        float ny = n01 * fnx + n11 * fny + n21 * fnz;
        float nz = n02 * fnx + n12 * fny + n22 * fnz;
        if (!trustedNormals) {
          float inv = org.joml.Math.invsqrt(nx * nx + ny * ny + nz * nz);
          nx *= inv;
          ny *= inv;
          nz *= inv;
        }
        int packedNormal = ncPackNormal(nx, ny, nz);

        float x = flatQuads[i + 3], y = flatQuads[i + 4], z = flatQuads[i + 5];
        MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 12L, abgr);
        MemoryUtil.memPutFloat(ptr + 16L, flatQuads[i + 6]);
        MemoryUtil.memPutFloat(ptr + 20L, flatQuads[i + 7]);
        MemoryUtil.memPutInt(ptr + 24L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 28L, packedLight);
        MemoryUtil.memPutInt(ptr + 32L, packedNormal);

        x = flatQuads[i + 8]; y = flatQuads[i + 9]; z = flatQuads[i + 10];
        MemoryUtil.memPutFloat(ptr + 36L, m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 40L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 44L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 48L, abgr);
        MemoryUtil.memPutFloat(ptr + 52L, flatQuads[i + 11]);
        MemoryUtil.memPutFloat(ptr + 56L, flatQuads[i + 12]);
        MemoryUtil.memPutInt(ptr + 60L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 64L, packedLight);
        MemoryUtil.memPutInt(ptr + 68L, packedNormal);

        x = flatQuads[i + 13]; y = flatQuads[i + 14]; z = flatQuads[i + 15];
        MemoryUtil.memPutFloat(ptr + 72L, m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 76L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 80L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 84L, abgr);
        MemoryUtil.memPutFloat(ptr + 88L, flatQuads[i + 16]);
        MemoryUtil.memPutFloat(ptr + 92L, flatQuads[i + 17]);
        MemoryUtil.memPutInt(ptr + 96L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 100L, packedLight);
        MemoryUtil.memPutInt(ptr + 104L, packedNormal);

        x = flatQuads[i + 18]; y = flatQuads[i + 19]; z = flatQuads[i + 20];
        MemoryUtil.memPutFloat(ptr + 108L, m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 112L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 116L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 120L, abgr);
        MemoryUtil.memPutFloat(ptr + 124L, flatQuads[i + 21]);
        MemoryUtil.memPutFloat(ptr + 128L, flatQuads[i + 22]);
        MemoryUtil.memPutInt(ptr + 132L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 136L, packedLight);
        MemoryUtil.memPutInt(ptr + 140L, packedNormal);

        ptr += 144L;
      }
    } else {
      for (int i = 0; i < len; i += 23) {
        float fnx = flatQuads[i];
        float fny = flatQuads[i + 1];
        float fnz = flatQuads[i + 2];
        float nx = n00 * fnx + n10 * fny + n20 * fnz;
        float ny = n01 * fnx + n11 * fny + n21 * fnz;
        float nz = n02 * fnx + n12 * fny + n22 * fnz;
        if (!trustedNormals) {
          float inv = org.joml.Math.invsqrt(nx * nx + ny * ny + nz * nz);
          nx *= inv;
          ny *= inv;
          nz *= inv;
        }
        int packedNormal = ncPackNormal(nx, ny, nz);

        for (int v = 0; v < 4; v++) {
          int vo = i + 3 + v * 5;
          float x = flatQuads[vo], y = flatQuads[vo + 1], z = flatQuads[vo + 2];
          MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
          MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
          MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
          MemoryUtil.memPutInt(ptr + 12L, abgr);
          MemoryUtil.memPutFloat(ptr + 16L, flatQuads[vo + 3]);
          MemoryUtil.memPutFloat(ptr + 20L, flatQuads[vo + 4]);
          MemoryUtil.memPutInt(ptr + 24L, packedLight);
          MemoryUtil.memPutInt(ptr + 28L, packedNormal);
          ptr += 32L;
        }
      }
    }
    return true;
  }

  @Override
  public boolean nightclient$emitFlatCubeUv(
      float[] flatQuads,
      Matrix4f pose,
      Matrix3f normal,
      boolean trustedNormals,
      int packedLight,
      int packedOverlay,
      int color,
      float u0,
      float uScale,
      float v0,
      float vScale) {
    @@FAST_BB_CUBE_GUARD@@
    final int len = flatQuads.length;
    if (len == 0) return true;
    final int quadCount = len / 23;
    final int vertCount = quadCount << 2;
    if (this.vertices + vertCount >= 16777215) return false;

    this.ensureBuilding();
    this.endLastVertex();
    long ptr = this.buffer.reserve(vertCount * stride);
    this.vertices += vertCount;
    this.vertexPointer = ptr + (long) (vertCount - 1) * stride;
    this.elementsToFill = 0;

    final int abgr = ARGB.toABGR(color);

    final float m00 = pose.m00(), m10 = pose.m10(), m20 = pose.m20(), m30 = pose.m30();
    final float m01 = pose.m01(), m11 = pose.m11(), m21 = pose.m21(), m31 = pose.m31();
    final float m02 = pose.m02(), m12 = pose.m12(), m22 = pose.m22(), m32 = pose.m32();

    final float n00 = normal.m00(), n10 = normal.m10(), n20 = normal.m20();
    final float n01 = normal.m01(), n11 = normal.m11(), n21 = normal.m21();
    final float n02 = normal.m02(), n12 = normal.m12(), n22 = normal.m22();

    if (full) {
      for (int i = 0; i < len; i += 23) {
        float fnx = flatQuads[i];
        float fny = flatQuads[i + 1];
        float fnz = flatQuads[i + 2];
        float nx = n00 * fnx + n10 * fny + n20 * fnz;
        float ny = n01 * fnx + n11 * fny + n21 * fnz;
        float nz = n02 * fnx + n12 * fny + n22 * fnz;
        if (!trustedNormals) {
          float inv = org.joml.Math.invsqrt(nx * nx + ny * ny + nz * nz);
          nx *= inv;
          ny *= inv;
          nz *= inv;
        }
        int packedNormal = ncPackNormal(nx, ny, nz);

        float x = flatQuads[i + 3], y = flatQuads[i + 4], z = flatQuads[i + 5];
        MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 12L, abgr);
        MemoryUtil.memPutFloat(ptr + 16L, u0 + flatQuads[i + 6] * uScale);
        MemoryUtil.memPutFloat(ptr + 20L, v0 + flatQuads[i + 7] * vScale);
        MemoryUtil.memPutInt(ptr + 24L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 28L, packedLight);
        MemoryUtil.memPutInt(ptr + 32L, packedNormal);

        x = flatQuads[i + 8]; y = flatQuads[i + 9]; z = flatQuads[i + 10];
        MemoryUtil.memPutFloat(ptr + 36L, m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 40L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 44L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 48L, abgr);
        MemoryUtil.memPutFloat(ptr + 52L, u0 + flatQuads[i + 11] * uScale);
        MemoryUtil.memPutFloat(ptr + 56L, v0 + flatQuads[i + 12] * vScale);
        MemoryUtil.memPutInt(ptr + 60L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 64L, packedLight);
        MemoryUtil.memPutInt(ptr + 68L, packedNormal);

        x = flatQuads[i + 13]; y = flatQuads[i + 14]; z = flatQuads[i + 15];
        MemoryUtil.memPutFloat(ptr + 72L, m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 76L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 80L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 84L, abgr);
        MemoryUtil.memPutFloat(ptr + 88L, u0 + flatQuads[i + 16] * uScale);
        MemoryUtil.memPutFloat(ptr + 92L, v0 + flatQuads[i + 17] * vScale);
        MemoryUtil.memPutInt(ptr + 96L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 100L, packedLight);
        MemoryUtil.memPutInt(ptr + 104L, packedNormal);

        x = flatQuads[i + 18]; y = flatQuads[i + 19]; z = flatQuads[i + 20];
        MemoryUtil.memPutFloat(ptr + 108L, m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 112L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 116L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 120L, abgr);
        MemoryUtil.memPutFloat(ptr + 124L, u0 + flatQuads[i + 21] * uScale);
        MemoryUtil.memPutFloat(ptr + 128L, v0 + flatQuads[i + 22] * vScale);
        MemoryUtil.memPutInt(ptr + 132L, packedOverlay);
        MemoryUtil.memPutInt(ptr + 136L, packedLight);
        MemoryUtil.memPutInt(ptr + 140L, packedNormal);

        ptr += 144L;
      }
    } else {
      for (int i = 0; i < len; i += 23) {
        float fnx = flatQuads[i];
        float fny = flatQuads[i + 1];
        float fnz = flatQuads[i + 2];
        float nx = n00 * fnx + n10 * fny + n20 * fnz;
        float ny = n01 * fnx + n11 * fny + n21 * fnz;
        float nz = n02 * fnx + n12 * fny + n22 * fnz;
        if (!trustedNormals) {
          float inv = org.joml.Math.invsqrt(nx * nx + ny * ny + nz * nz);
          nx *= inv;
          ny *= inv;
          nz *= inv;
        }
        int packedNormal = ncPackNormal(nx, ny, nz);

        for (int v = 0; v < 4; v++) {
          int vo = i + 3 + v * 5;
          float x = flatQuads[vo], y = flatQuads[vo + 1], z = flatQuads[vo + 2];
          MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
          MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
          MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
          MemoryUtil.memPutInt(ptr + 12L, abgr);
          MemoryUtil.memPutFloat(ptr + 16L, u0 + flatQuads[vo + 3] * uScale);
          MemoryUtil.memPutFloat(ptr + 20L, v0 + flatQuads[vo + 4] * vScale);
          MemoryUtil.memPutInt(ptr + 24L, packedLight);
          MemoryUtil.memPutInt(ptr + 28L, packedNormal);
          ptr += 32L;
        }
      }
    }
    return true;
  }

  @Override
  public boolean nightclient$emitFlatTextQuads(
      float[] xyzuv,
      int[] abgrs,
      int quadCount,
      Matrix4fc pose,
      int packedLight) {
    if (!NC_LITTLE_ENDIAN || !this.building || quadCount <= 0) return false;
    final int vertCount = quadCount << 2;
    if (this.vertices + vertCount >= 16777215) return false;

    final boolean textured = this.format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP && this.vertexSize == 28;
    final boolean texturedLateColor = !textured && this.format == DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR && this.vertexSize == 28;
    final boolean colorOnly = !textured && !texturedLateColor && this.format == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP && this.vertexSize == 20;
    if (!textured && !texturedLateColor && !colorOnly) return false;

    final int stride = this.vertexSize;
    this.ensureBuilding();
    this.endLastVertex();
    long ptr = this.buffer.reserve(vertCount * stride);
    this.vertices += vertCount;
    this.vertexPointer = ptr + (long) (vertCount - 1) * stride;
    this.elementsToFill = 0;

    final float m00 = pose.m00(), m10 = pose.m10(), m20 = pose.m20(), m30 = pose.m30();
    final float m01 = pose.m01(), m11 = pose.m11(), m21 = pose.m21(), m31 = pose.m31();
    final float m02 = pose.m02(), m12 = pose.m12(), m22 = pose.m22(), m32 = pose.m32();

    if (textured) {
      int fi = 0;
      for (int vi = 0; vi < vertCount; vi++) {
        float x = xyzuv[fi];
        float y = xyzuv[fi + 1];
        float z = xyzuv[fi + 2];
        MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 12L, abgrs[vi]);
        MemoryUtil.memPutFloat(ptr + 16L, xyzuv[fi + 3]);
        MemoryUtil.memPutFloat(ptr + 20L, xyzuv[fi + 4]);
        MemoryUtil.memPutInt(ptr + 24L, packedLight);
        fi += 5;
        ptr += 28L;
      }
    } else if (texturedLateColor) {
      int fi = 0;
      for (int vi = 0; vi < vertCount; vi++) {
        float x = xyzuv[fi];
        float y = xyzuv[fi + 1];
        float z = xyzuv[fi + 2];
        MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutFloat(ptr + 12L, xyzuv[fi + 3]);
        MemoryUtil.memPutFloat(ptr + 16L, xyzuv[fi + 4]);
        MemoryUtil.memPutInt(ptr + 20L, packedLight);
        MemoryUtil.memPutInt(ptr + 24L, abgrs[vi]);
        fi += 5;
        ptr += 28L;
      }
    } else {
      int fi = 0;
      for (int vi = 0; vi < vertCount; vi++) {
        float x = xyzuv[fi];
        float y = xyzuv[fi + 1];
        float z = xyzuv[fi + 2];
        MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
        MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
        MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
        MemoryUtil.memPutInt(ptr + 12L, abgrs[vi]);
        MemoryUtil.memPutInt(ptr + 16L, packedLight);
        fi += 5;
        ptr += 20L;
      }
    }
    return true;
  }
}
