package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SafeCtStopMixin {
    @Inject(method="stop()V",at=@At("HEAD"),cancellable=true)
    private void nightclient$blockCtWindowClose(CallbackInfo callback){
        if(WorldFeatureBridge.shouldBlockWindowClose()){
            @@SAFE_CT_RESET_WINDOW_CLOSE@@
            callback.cancel();
        }
    }
    @Inject(method="pauseGame(Z)V",at=@At("HEAD"),cancellable=true)
    private void nightclient$suppressUnfocusedPause(boolean pauseOnly,CallbackInfo callback){
        if(!((Minecraft)(Object)this).isWindowActive()&&WorldFeatureBridge.shouldSuppressUnfocusedPause()){
            callback.cancel();
        }
    }
}
