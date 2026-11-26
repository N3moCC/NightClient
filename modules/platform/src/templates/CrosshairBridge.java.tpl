package dev.nightclient.platform;
import net.minecraft.client.Minecraft;
@@CROSSHAIR_IMPORTS@@

public final class CrosshairBridge {
    private static final @@IDENTIFIER@@ CROSSHAIR=GameBridge.identifier("@@CROSSHAIR_RESOURCE@@");
    private CrosshairBridge() {}
    public static void draw(@@CONTEXT@@ graphics,float x,float y,float size,float opacity){
        if(size<=0||opacity<=0)return;
        Minecraft mc=Minecraft.getInstance();
        @@CROSSHAIR_STATE_CAPTURE@@
        @@CROSSHAIR_PUSH@@
        try{@@CROSSHAIR_DRAW@@}
        finally{@@CROSSHAIR_POP@@ @@CROSSHAIR_STATE_RESTORE@@}
    }
}
