package dev.nightclient.platform;

import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
@@SCREEN_IMPORTS@@

public final class NightScreen extends Screen {
    private final Screen parent;
    private boolean opened,textFocused;
    private char pendingHighSurrogate;
    public NightScreen(){this(null);}
    public NightScreen(Screen parent){super(@@SCREEN_TITLE@@);this.parent=parent;}
    public Screen parentScreen(){return parent;}
    @Override protected void init(){if(!opened){ClientRuntime.get().menu().onOpen();opened=true;}}
    @Override public boolean isPauseScreen(){return false;}
    @@SCREEN_BACKGROUND_OVERRIDE@@
    @Override public void @@SCREEN_RENDER_METHOD@@(@@CONTEXT@@ graphics,int mouseX,int mouseY,float delta){
        @@FLUSH_GUI@@
        @@PARENT_NO_WORLD_BACKGROUND@@
        @@NO_WORLD_MENU_CLEAR@@
        @@VANILLA_GUI_BLUR@@
        NativeCanvas canvas=new NativeCanvas(graphics,width,height);
        try{@@VANILLA_GUI_BLUR_FALLBACK@@ ClientRuntime.get().renderMenu(canvas,width,height,mouseX,mouseY);}finally{canvas.finish();}
        syncTextFocus();
    }
    private void syncTextFocus(){
        boolean focused=ClientRuntime.get().menu().isTextFocused();
        if(focused!=textFocused){textFocused=focused;GameBridge.textFocus(this,focused);}
    }
    @Override public void onClose(){ClientRuntime.get().close();}
    @Override public void removed(){
        if(textFocused){GameBridge.textFocus(this,false);textFocused=false;}
        if(opened){ClientRuntime.get().menu().onClose();opened=false;}
    }
    @@INPUT_METHODS@@
}
