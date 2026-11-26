package dev.nightclient.platform;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public interface FastEntityVertexWriter {
  boolean nightclient$emitFlatCube(
      float[] flatQuads,
      Matrix4f pose,
      Matrix3f normal,
      boolean trustedNormals,
      int packedLight,
      int packedOverlay,
      int color);

  default boolean nightclient$emitFlatCubeUv(
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
    return false;
  }

  default boolean nightclient$emitFlatTextQuads(
      float[] xyzuv,
      int[] abgrs,
      int quadCount,
      Matrix4fc pose,
      int packedLight) {
    return false;
  }
}
