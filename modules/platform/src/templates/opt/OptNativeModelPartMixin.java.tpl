package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nightclient.platform.NativeGpuBridge;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class OptNativeModelPartMixin {
  @Shadow @Final private List<ModelPart.Cube> cubes;

  @Unique private int nightclient$nativeMeshId;
  @Unique private long nightclient$nativeMeshGen;

  @Inject(
      method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$emitNativeInstance(
      PoseStack.Pose pose,
      VertexConsumer consumer,
      int packedLight,
      int packedOverlay,
      int color,
      CallbackInfo ci) {
    if (consumer != NativeGpuBridge.ENTITY_COLLECTOR) return;
    if (this.cubes == null || this.cubes.isEmpty()) {
      ci.cancel();
      return;
    }
    long gen = NativeGpuBridge.meshGeneration();
    int meshId = this.nightclient$nativeMeshId;
    if (meshId <= 0 || this.nightclient$nativeMeshGen != gen) {
      meshId = NativeGpuBridge.registerModelPart(this.cubes);
      this.nightclient$nativeMeshId = meshId;
      this.nightclient$nativeMeshGen = gen;
    }
    if (meshId > 0
        && NativeGpuBridge.submitEntityPart(meshId, pose.pose(), packedLight, packedOverlay, color)) {
      ci.cancel();
    }
  }
}
