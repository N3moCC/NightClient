package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class LowFireOverlayMixin {
    @Inject(method="@@FIRE_HOOK@@",at=@At("HEAD"),cancellable=true)
    private static void nightclient$hide(CallbackInfo callback){if(WorldFeatureBridge.policy().hideFire)callback.cancel();}
    @ModifyArg(method="@@FIRE_TRANSLATION_HOOK@@",at=@At(value="INVOKE",target="@@FIRE_TRANSLATION_TARGET@@"),index=1)
    private static @@FIRE_TRANSLATION_TYPE@@ nightclient$lower(@@FIRE_TRANSLATION_TYPE@@ y){return y-WorldFeatureBridge.policy().fireOffset;}
}
