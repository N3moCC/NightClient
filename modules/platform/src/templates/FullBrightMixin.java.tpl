package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import @@LIGHT_OWNER@@;
@@LIGHT_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(@@LIGHT_SHORT@@.class)
public abstract class FullBrightMixin {
    @@LIGHT_FIELDS@@
    @Unique private boolean nightclient$previous;
    @Unique private float nightclient$lastIllumination=Float.NaN;
    @Unique private int nightclient$lastReloadGen=Integer.MIN_VALUE;
    @Inject(method="@@LIGHT_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$prepare(@@LIGHT_ARGS@@CallbackInfo callback){
        WorldFeatureBridge.Policy policy=WorldFeatureBridge.policy();
        if(policy.fullBright){
            int reloadGen=WorldFeatureBridge.reloadGeneration();
            if(!nightclient$previous||Float.compare(nightclient$lastIllumination,policy.illumination)!=0||nightclient$lastReloadGen!=reloadGen){
                @@LIGHT_FILL@@
                nightclient$lastIllumination=policy.illumination;
                nightclient$lastReloadGen=reloadGen;
            }
            @@LIGHT_MARK_CLEAN@@
            nightclient$previous=true;
            callback.cancel();
        }else if(nightclient$previous){
            @@LIGHT_FORCE_REBUILD@@
            nightclient$previous=false;
            nightclient$lastIllumination=Float.NaN;
        }
    }
}
