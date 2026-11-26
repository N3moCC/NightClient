package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {
    @Inject(method="bobHurt@@NO_HURT_CAM_DESC@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$noHurtCam(@@NO_HURT_CAM_ARGS@@,CallbackInfo callback){
        if(WorldFeatureBridge.noHurtCam())callback.cancel();
    }
}
