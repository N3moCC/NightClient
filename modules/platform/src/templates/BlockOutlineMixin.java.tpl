package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
    @Inject(method="@@BLOCK_OUTLINE_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$cancelVanillaBlockOutline(@@BLOCK_OUTLINE_ARGS@@CallbackInfo callback){
        if(WorldFeatureBridge.policy().blockOverlay)callback.cancel();
    }
}
