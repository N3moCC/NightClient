package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ScreenEffectRenderer.class)
public abstract class NoFluidOverlayMixin {
    @Inject(method="@@WATER_HOOK@@",at=@At("HEAD"),cancellable=true)
    private static void nightclient$water(CallbackInfo callback){if(WorldFeatureBridge.policy().waterOverlay)callback.cancel();}
    @Inject(method="@@BLOCK_OVERLAY_HOOK@@",at=@At("HEAD"),cancellable=true)
    private static void nightclient$blockOverlay(CallbackInfo callback){if(WorldFeatureBridge.policy().noBlockOverlay)callback.cancel();}
}
