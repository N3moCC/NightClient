package dev.nightclient.platform.mixin;

import dev.nightclient.platform.AtmosphereBackend;
import dev.nightclient.platform.CustomModelBackend;
import dev.nightclient.platform.CustomModelBridge;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@@ATMO_RENDER_IMPORTS@@

@Mixin(LevelRenderer.class)
public abstract class AtmosphereRenderMixin {
  @Inject(method = "@@ATMO_RENDER_DESC@@", at = @At("HEAD"))
  private void nightclient$captureWorldMatrices(@@ATMO_RENDER_ARGS@@CallbackInfo ci) {
    @@ATMO_CAPTURE_CALL@@
  }

  @Inject(method = "@@ATMO_RENDER_DESC@@", at = @At("RETURN"))
  private void nightclient$renderAtmosphere(CallbackInfo ci) {
    CustomModelBackend.flushWorld();
    AtmosphereBackend.renderLevel();
  }
}
