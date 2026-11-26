package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Inject(method="@@TOOLTIP_HOOK@@",at=@At("RETURN"))
    private void nightclient$tooltip(CallbackInfoReturnable<List<Component>> callback){
        WorldFeatureBridge.formatTooltipLines((ItemStack)(Object)this,callback.getReturnValue());
    }
}
