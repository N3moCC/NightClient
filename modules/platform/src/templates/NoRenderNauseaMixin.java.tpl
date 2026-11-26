package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import @@NAUSEA_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(@@NAUSEA_SHORT@@.class)
public abstract class NoRenderNauseaMixin {
    @Inject(method="@@NAUSEA_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$nausea(CallbackInfo callback){if(WorldFeatureBridge.policy().noNausea)callback.cancel();}
}
