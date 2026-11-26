package dev.nightclient.platform;

import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
@@SCREEN_IMPORTS@@

public final class NightMapScreen extends Screen {
    private boolean opened;
    public NightMapScreen(){super(@@SCREEN_TITLE@@);}
    @Override protected void init(){if(!opened){ClientRuntime.get().worldMap().onOpen();opened=true;}}
    @Override public boolean isPauseScreen(){return false;}
    @@MAP_SCREEN_BACKGROUND_OVERRIDE@@
    @Override public void @@SCREEN_RENDER_METHOD@@(@@CONTEXT@@ graphics,int mouseX,int mouseY,float delta){
        @@FLUSH_GUI@@
        NativeCanvas canvas=new NativeCanvas(graphics,width,height);
        try{ClientRuntime.get().renderWorldMap(canvas,width,height,mouseX,mouseY);}finally{canvas.finish();}
    }
    @Override public void onClose(){GameBridge.closeWorldMapScreen();}
    @Override public void removed(){
        if(opened){ClientRuntime.get().worldMap().onClose();opened=false;}
    }
    @@MAP_INPUT_METHODS@@
}
