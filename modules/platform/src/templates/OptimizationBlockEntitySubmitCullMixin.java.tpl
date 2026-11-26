package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import @@CAMERA_IMPORT@@;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class OptimizationBlockEntitySubmitCullMixin {
  @Inject(
      method =
          "submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;L@@CAMERA_DESCRIPTOR@@;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullBlockEntity(
      BlockEntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      CallbackInfo callback) {
    if (!OptimizationBridge.blockEntityCullingActive()) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null || camera == null) return;
    BlockPos pos = state.blockPos;
    if (pos == null) return;
    double dx = pos.getX() + 0.5 - camera.pos.x;
    double dy = pos.getY() + 0.5 - camera.pos.y;
    double dz = pos.getZ() + 0.5 - camera.pos.z;
    if (OptimizationBridge.cullBlockEntity(dx * dx + dy * dy + dz * dz, true)) callback.cancel();
  }
}
