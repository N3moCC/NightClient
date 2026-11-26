package dev.nightclient.platform.mixin;

import dev.nightclient.platform.GameBridge;
import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.Minecraft;
import @@HUD_OWNER@@;
@@HUD_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(@@HUD_SHORT@@.class)
public abstract class HudMixin {
    private boolean nightclient$hudDrawn;
    @@HUD_LEGACY_DELTA_FIELD@@
    private void nightclient$renderHudOnce(@@CONTEXT@@ graphics,@@HUD_DELTA_TYPE@@ delta){
        if(this.nightclient$hudDrawn)return;
        this.nightclient$hudDrawn=true;
        Minecraft mc=Minecraft.getInstance();
        GameBridge.renderHud(graphics,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),@@HUD_DELTA@@);
    }
    @Inject(method="@@HUD_METHOD@@",at=@At("HEAD"))
    private void nightclient$hudHead(@@CONTEXT@@ graphics,@@HUD_DELTA_TYPE@@ delta,CallbackInfo callback){
        this.nightclient$hudDrawn=false;
        @@HUD_LEGACY_DELTA_SAVE@@
    }
    @Inject(method="displayScoreboardSidebar",at=@At("HEAD"),cancellable=true)
    private void nightclient$hideVanillaScoreboard(CallbackInfo callback){
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime!=null && runtime.settings().scoreboard.enabled)callback.cancel();
    }
    @Inject(method="@@HUD_EFFECTS_METHOD@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$hideVanillaPotions(CallbackInfo callback){
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime!=null && runtime.settings().potions.enabled)callback.cancel();
    }
    @@HUD_TAB_HOOK@@
    @Inject(method="@@HUD_METHOD@@",at=@At("TAIL"))
    private void nightclient$hud(@@CONTEXT@@ graphics,@@HUD_DELTA_TYPE@@ delta,CallbackInfo callback){
        this.nightclient$renderHudOnce(graphics,delta);
        @@HUD_LEGACY_TAB_CALL@@
    }
}
