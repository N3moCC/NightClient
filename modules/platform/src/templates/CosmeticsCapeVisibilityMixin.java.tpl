package dev.nightclient.platform.mixin;

import dev.nightclient.platform.CosmeticsBridge;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(@@MAIN_ARM_TARGET@@.class)
public abstract class CosmeticsCapeVisibilityMixin {
    @Inject(method="isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z",at=@At("HEAD"),cancellable=true)
    private void nightclient$capeShown(PlayerModelPart part,CallbackInfoReturnable<Boolean> callback){
        if(part!=PlayerModelPart.CAPE || !CosmeticsBridge.appliesTo(this))return;
        if(CosmeticsBridge.capeSuppressed()){callback.setReturnValue(Boolean.FALSE);return;}
        if(CosmeticsBridge.capeOverridden() && CosmeticsBridge.capeTexture()!=null)callback.setReturnValue(Boolean.TRUE);
    }
}
