package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import @@ZOOM_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(@@ZOOM_SHORT@@.class)
public abstract class ZoomFovMixin {
    @Inject(method="@@ZOOM_SELECTOR@@",at=@At("RETURN"),cancellable=true)
    private void nightclient$zoom(@@ZOOM_ARGUMENTS@@CallbackInfoReturnable<@@FOV_BOXED@@> callback){
        @@ZOOM_GUARD@@
        @@FOV_TYPE@@ value=callback.getReturnValue();
        callback.setReturnValue((@@FOV_TYPE@@)FeatureBridge.zoomFov(value));
    }
}
