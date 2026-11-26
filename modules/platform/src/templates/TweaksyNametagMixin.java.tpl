package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class TweaksyNametagMixin {
  @Inject(
      method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;@@NAMETAG_DISTANCE_DESC@@)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$forceNametag(
      LivingEntity entity,
      @@NAMETAG_DISTANCE_ARG@@CallbackInfoReturnable<Boolean> callback) {
    if (!WorldFeatureBridge.shouldForceShowNametag(entity)) return;
    Minecraft minecraft = Minecraft.getInstance();
    boolean local = minecraft.player != null && entity == minecraft.player;
    if (!OptimizationBridge.cullNametag(
        @@NAMETAG_DISTANCE_SQ@@, minecraft.level != null, local, entity.@@NAMETAG_GLOW@@())) {
      callback.setReturnValue(true);
    }
  }
}
