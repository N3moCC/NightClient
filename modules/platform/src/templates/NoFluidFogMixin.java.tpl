package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.Camera;
import @@FOG_IMPORT@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Camera.class)
public abstract class NoFluidFogMixin {
    @Inject(method="@@FLUID_HOOK@@",at=@At("RETURN"),cancellable=true)
    private void nightclient$fog(CallbackInfoReturnable<@@FOG_TYPE@@> callback){
        WorldFeatureBridge.Policy policy=WorldFeatureBridge.policy();@@FOG_TYPE@@ original=callback.getReturnValue();
        @@FOG_REPLACE@@
    }
}
