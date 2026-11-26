package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public abstract class ZoomScrollMixin {
    @Inject(method="onScroll(JDD)V",at=@At("HEAD"),cancellable=true)
    private void nightclient$scroll(long window,double horizontal,double vertical,CallbackInfo callback){
        if(FeatureBridge.zoomScroll(vertical))callback.cancel();
    }
}
