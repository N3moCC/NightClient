package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class OptStaticBeSubmitMixin {
  @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderDispatcher;
  @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

  @Inject(
      method = "submitBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
      at = @At("HEAD"))
  private void nightclient$submitStaticBeBake(
      PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
    StaticBeVboCache.bakeAndFilter(this.blockEntityRenderDispatcher, state);
  }

  @Inject(
      method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
      at = @At("HEAD"))
  private void nightclient$submitStaticEntityBake(
      PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
    StaticEntityVboCache.bakeAndFilter(this.entityRenderDispatcher, state, pose, collector);
  }
}
