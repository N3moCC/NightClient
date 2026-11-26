package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class OptimizationBlockEntityCullMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullBlockEntity(
      BlockEntity blockEntity,
      float partialTick,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      CallbackInfo callback) {
    if (!OptimizationBridge.blockEntityCullingActive()) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) return;
    if (blockEntity.getLevel() != minecraft.level) return;
    BlockPos pos = blockEntity.getBlockPos();
    double dx = pos.getX() + 0.5 - minecraft.player.getX();
    double dy = pos.getY() + 0.5 - minecraft.player.getEyeY();
    double dz = pos.getZ() + 0.5 - minecraft.player.getZ();
    if (OptimizationBridge.cullBlockEntity(dx * dx + dy * dy + dz * dz, true)) callback.cancel();
  }
}
