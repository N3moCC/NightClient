package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public abstract class ItemScrollerMixin {
    @Inject(method="onScroll(JDD)V",at=@At("HEAD"),cancellable=true)
    private void nightclient$wheel(long window,double horizontal,double vertical,CallbackInfo callback){if(WorldFeatureBridge.scroll(vertical))callback.cancel();}
}
