package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
@@NAMETAG_EXTRA_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class OptimizationNametagCullMixin {
  @Inject(
      method = "shouldShowName(Lnet/minecraft/world/entity/Entity;@@DISTANCE_DESCRIPTOR@@)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullNametag(
      Entity entity, @@DISTANCE_ARG@@CallbackInfoReturnable<Boolean> callback) {
    if (!OptimizationBridge.nametagCullingActive()) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) return;
    boolean inWorld = entity@@LEVEL_ACCESS@@ == minecraft.level;
    if (!inWorld) return;
    boolean localPlayer = entity == minecraft.player;
    boolean glowing = entity.@@GLOW@@();
    double distanceSq = @@DISTANCE_EXPRESSION@@;
    if (OptimizationBridge.cullNametag(distanceSq, inWorld, localPlayer, glowing)) {
      callback.setReturnValue(false);
    }
  }

  @@NAMETAG_SUBMIT_HOOK@@
}
