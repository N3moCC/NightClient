package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import @@CROSSHAIR_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(@@CROSSHAIR_SHORT@@.class)
public abstract class CrosshairMixin {
    @Inject(method="@@CROSSHAIR_SELECTOR@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$crosshair(CallbackInfo callback){if(FeatureBridge.customCrosshair())callback.cancel();}
}
