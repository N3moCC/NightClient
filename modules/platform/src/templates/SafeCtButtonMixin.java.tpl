package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Button.class)
public abstract class SafeCtButtonMixin {
    @Inject(method="@@SAFE_CT_BUTTON_PRESS_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$blockCtDisconnect(@@SAFE_CT_BUTTON_PRESS_ARGS@@CallbackInfo callback){
        if(WorldFeatureBridge.shouldBlockDisconnect(this, () -> ((Button)(Object)this).onPress(@@SAFE_CT_BUTTON_REPLAY_ARGS@@)))callback.cancel();
    }
}
