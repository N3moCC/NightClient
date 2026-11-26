package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import @@TOTEM_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(@@TOTEM_SHORT@@.class)
public abstract class NoRenderTotemMixin {
    @Inject(method="@@TOTEM_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$totem(CallbackInfo callback){if(WorldFeatureBridge.policy().noTotem)callback.cancel();}
}
