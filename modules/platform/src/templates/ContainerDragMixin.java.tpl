package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerDragMixin {
    @Inject(method="@@CLICK_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$begin(@@CLICK_ARGS@@CallbackInfoReturnable<Boolean> callback){if(WorldFeatureBridge.click(this,@@CLICK_BUTTON@@,@@CLICK_X@@,@@CLICK_Y@@,@@INPUT_SHIFT@@,@@INPUT_CONTROL@@))callback.setReturnValue(true);}
    @Inject(method="@@DRAG_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$drag(@@DRAG_ARGS@@CallbackInfoReturnable<Boolean> callback){if(WorldFeatureBridge.drag(this,@@DRAG_BUTTON@@,@@CLICK_X@@,@@CLICK_Y@@,@@INPUT_SHIFT@@))callback.setReturnValue(true);}
    @Inject(method="@@RELEASE_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$release(CallbackInfoReturnable<Boolean> callback){if(WorldFeatureBridge.releaseDrag())callback.setReturnValue(true);}
}
