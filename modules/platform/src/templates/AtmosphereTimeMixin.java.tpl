package dev.nightclient.platform.mixin;

import dev.nightclient.platform.AtmosphereWorldBridge;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class AtmosphereTimeMixin {
@@ATMO_TIME_INJECTS@@

  @Inject(method = "getRainLevel(F)F", at = @At("HEAD"), cancellable = true)
  private void nightclient$clearRain(float delta, CallbackInfoReturnable<Float> cir) {
    if (AtmosphereWorldBridge.shouldClearWeather(this)) {
      cir.setReturnValue(Float.valueOf(0.0f));
    }
  }

  @Inject(method = "getThunderLevel(F)F", at = @At("HEAD"), cancellable = true)
  private void nightclient$clearThunder(float delta, CallbackInfoReturnable<Float> cir) {
    if (AtmosphereWorldBridge.shouldClearWeather(this)) {
      cir.setReturnValue(Float.valueOf(0.0f));
    }
  }
}
