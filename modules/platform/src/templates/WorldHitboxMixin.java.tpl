package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.debug.DebugRenderer;
@@BOX_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DebugRenderer.class)
public abstract class WorldHitboxMixin {
    @Inject(method="@@DEBUG_HOOK@@",at=@At("HEAD"))
    private void nightclient$boxes(@@DEBUG_ARGS@@CallbackInfo callback){@@BOX_CALL@@}
}
