package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
@@TOOLTIP_BG_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBackgroundMixin {
    @Inject(method="@@TOOLTIP_BG_HOOK@@",at=@At("HEAD"),cancellable=true)
    private static void nightclient$tooltipBg(@@TOOLTIP_BG_ARGS@@CallbackInfo callback){
        WorldFeatureBridge.recordTooltipRect(x,y,width,height);
        if(WorldFeatureBridge.renderCustomTooltipBackdrop(graphics,x,y,width,height)){
            callback.cancel();
        }
    }
}
