package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import @@CAMERA_IMPORT@@;
import net.minecraft.world.entity.@@PLAYER_TYPE_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class OptimizationEntitySubmitCullMixin {
  @Inject(
      method =
          "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;L@@CAMERA_DESCRIPTOR@@;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullEntity(
      EntityRenderState state,
      CameraRenderState camera,
      double x,
      double y,
      double z,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CallbackInfo callback) {
    if (!OptimizationBridge.entityCullingActive()) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) return;
    if (state.entityType == @@PLAYER_TYPE_OWNER@@.PLAYER) return;
    if (state.outlineColor != 0) return;
    double halfWidth = state.boundingBoxWidth * 0.5;
    double halfHeight = state.boundingBoxHeight * 0.5;
    double halfDiagonalSq = halfWidth * halfWidth + halfHeight * halfHeight;
    if (OptimizationBridge.cullEntity(
        state.distanceToCameraSq, halfDiagonalSq, true, false, false)) {
      callback.cancel();
    }
  }
}
