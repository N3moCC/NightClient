package dev.nightclient.platform.mixin;

import dev.nightclient.platform.NightClientMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  @Inject(method = "tick()V", at = @At("TAIL"))
  private void nightclient$tick(CallbackInfo callback) {
    NightClientMod.tick();
  }

  @Inject(method = "close()V", at = @At("HEAD"))
  private void nightclient$close(CallbackInfo callback) {
    NightClientMod.close();
  }
}
