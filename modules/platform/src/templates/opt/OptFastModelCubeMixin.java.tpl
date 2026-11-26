package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nightclient.platform.FastEntityVertexWriter;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public abstract class OptFastModelCubeMixin {
  @Shadow @Final public ModelPart.Polygon[] polygons;

  @Unique private float[] nightclient$flatQuads;
  @Unique private boolean nightclient$flatReady;

  @Unique
  private float[] nightclient$ensureFlat() {
    if (this.nightclient$flatReady) return this.nightclient$flatQuads;
    this.nightclient$flatReady = true;
    ModelPart.Polygon[] polys = this.polygons;
    if (polys == null || polys.length == 0) return null;
    for (int i = 0; i < polys.length; i++) {
      ModelPart.Polygon p = polys[i];
      if (p == null || p.vertices() == null || p.vertices().length != 4 || p.normal() == null) {
        return null;
      }
    }
    float[] flat = new float[polys.length * 23];
    int out = 0;
    for (int i = 0; i < polys.length; i++) {
      ModelPart.Polygon p = polys[i];
      Vector3fc n = p.normal();
      flat[out++] = n.x();
      flat[out++] = n.y();
      flat[out++] = n.z();
      ModelPart.Vertex[] verts = p.vertices();
      for (int v = 0; v < 4; v++) {
        ModelPart.Vertex mv = verts[v];
        flat[out++] = @@MODEL_VERTEX_X@@;
        flat[out++] = @@MODEL_VERTEX_Y@@;
        flat[out++] = @@MODEL_VERTEX_Z@@;
        flat[out++] = mv.u();
        flat[out++] = mv.v();
      }
    }
    this.nightclient$flatQuads = flat;
    return flat;
  }

  @Inject(
      method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$fastCompileCube(
      PoseStack.Pose pose,
      VertexConsumer consumer,
      int packedLight,
      int packedOverlay,
      int color,
      CallbackInfo ci) {
    if (!OptimizationBridge.opt116FastModelCube) return;
    @@FAST_MODEL_SODIUM_GUARD@@
    if (!(consumer instanceof FastEntityVertexWriter)) return;
    float[] flat = this.nightclient$ensureFlat();
    if (flat == null) return;
    Object poseObj = pose;
    boolean trusted = poseObj instanceof OptPoseNormalAccessor
        && ((OptPoseNormalAccessor) poseObj).nightclient$hasTrustedNormals();
    if (((FastEntityVertexWriter) consumer).nightclient$emitFlatCube(
        flat, pose.pose(), pose.normal(), trusted, packedLight, packedOverlay, color)) {
      ci.cancel();
    }
  }
}
