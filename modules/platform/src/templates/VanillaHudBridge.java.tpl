package dev.nightclient.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
@@VANILLA_HUD_IMPORTS@@

public final class VanillaHudBridge {
    private VanillaHudBridge() {}
    @@VANILLA_HUD_TEXTURES@@
    public static void hungerIcon(@@CONTEXT@@ graphics,float x,float y,float size,int fill) {
        if(size<=0)return;
        Minecraft mc=Minecraft.getInstance();
        boolean hunger=mc.player!=null&&mc.player.hasEffect(MobEffects.HUNGER);
        fill=Math.max(0,Math.min(2,fill));
        @@VANILLA_HUD_PUSH@@
        try {
            @@VANILLA_HUD_DRAW@@
        } finally { @@VANILLA_HUD_POP@@ }
    }
}
