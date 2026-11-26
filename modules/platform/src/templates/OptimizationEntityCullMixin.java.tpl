package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class OptimizationEntityCullMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/world/entity/Entity;DDDF@@SECOND_FLOAT@@Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullEntity(
      Entity entity,
      double x,
      double y,
      double z,
      float partialTick,
      @@SECOND_FLOAT_ARG@@PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight,
      CallbackInfo callback) {
    if (!OptimizationBridge.entityCullingActive()) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) return;
    boolean inWorld = entity@@LEVEL_ACCESS@@ == minecraft.level;
    if (!inWorld) return;
    boolean localPlayer = entity == minecraft.player;
    boolean glowing = entity.@@GLOW@@();
    double halfWidth = entity.getBbWidth() * 0.5;
    double halfHeight = entity.getBbHeight() * 0.5;
    double halfDiagonalSq = halfWidth * halfWidth + halfHeight * halfHeight;
    if (OptimizationBridge.cullEntity(
        x * x + y * y + z * z, halfDiagonalSq, inWorld, localPlayer, glowing)) {
      callback.cancel();
    }
  }
}
