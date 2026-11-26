package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public abstract class ItemScrollerKeyMixin {
    @Inject(method="@@SCROLLER_KEY_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$bulkDrop(@@SCROLLER_KEY_ARGS@@CallbackInfo callback){
        Minecraft mc=Minecraft.getInstance();
        if(window!=@@SCROLLER_WINDOW@@||!@@SCROLLER_KEY_MATCH@@)return;
        int mods=dev.nightclient.platform.GameBridge.modifiers(@@SCROLLER_KEY_MODS@@);
        if(WorldFeatureBridge.dropMatching(action,(mods&dev.nightclient.api.Keys.MOD_SHIFT)!=0,(mods&dev.nightclient.api.Keys.MOD_CONTROL)!=0))callback.cancel();
    }
}
