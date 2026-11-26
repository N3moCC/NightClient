package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
@@CONTAINER_TOOLTIP_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTooltipMixin {
    @Inject(method="init()V",at=@At("RETURN"))
    private void nightclient$containerInit(CallbackInfo callback){
        WorldFeatureBridge.onContainerScreenInit(this);
    }
    @Inject(method="@@CONTAINER_SLOT_HOOK@@",at=@At("HEAD"))
    private void nightclient$slotHighlight(@@CONTAINER_SLOT_ARGS@@CallbackInfo callback){
        WorldFeatureBridge.renderSlotHighlight(graphics,slot);
    }
    @Inject(method="@@CONTAINER_TOOLTIP_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$tooltipHead(@@CONTEXT@@ graphics,int mouseX,int mouseY,CallbackInfo callback){
        if(WorldFeatureBridge.beginContainerTooltip(graphics,this,mouseX,mouseY)){
            callback.cancel();
        }
    }
    @Inject(method="@@CONTAINER_TOOLTIP_HOOK@@",at=@At("RETURN"))
    private void nightclient$tooltipReturn(@@CONTEXT@@ graphics,int mouseX,int mouseY,CallbackInfo callback){
        WorldFeatureBridge.renderContainerPeekOverlay(graphics,this,mouseX,mouseY);
    }
}
