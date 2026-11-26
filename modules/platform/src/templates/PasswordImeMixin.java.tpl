package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IMEPreeditOverlay.class)
public abstract class PasswordImeMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",at=@At("HEAD"),cancellable=true)
    private void nightclient$preedit(CallbackInfo callback){if(FeatureBridge.maskingChatPassword())callback.cancel();}
}
