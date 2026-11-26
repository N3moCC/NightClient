package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(@@MAIN_ARM_TARGET@@.class)
public abstract class MainLeftFixMixin {
    @Inject(method="getMainArm",at=@At("RETURN"),cancellable=true)
    private void nightclient$mainLeftFix(CallbackInfoReturnable<HumanoidArm> callback){
        HumanoidArm arm=WorldFeatureBridge.mainLeftArm(this);
        if(arm!=null)callback.setReturnValue(arm);
    }
}
