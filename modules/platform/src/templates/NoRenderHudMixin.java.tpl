package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import @@NORENDER_HUD_OWNER@@;
@@NORENDER_HUD_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(@@NORENDER_HUD_SHORT@@.class)
public abstract class NoRenderHudMixin {
@@NORENDER_TEXTURE_OVERLAY_INJECT@@
    @Inject(method="@@PORTAL_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$portal(CallbackInfo callback){if(WorldFeatureBridge.policy().noPortal)callback.cancel();}
    @Inject(method="@@VIGNETTE_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$vignette(CallbackInfo callback){if(WorldFeatureBridge.policy().noVignette)callback.cancel();}
}
