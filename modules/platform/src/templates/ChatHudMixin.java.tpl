package dev.nightclient.platform.mixin;

import dev.nightclient.platform.GameBridge;
import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
@@CHAT_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatHudMixin extends Screen {
    @Unique private boolean nightclient$editingInput;
    protected ChatHudMixin(Component title){super(title);}

    @Unique private void nightclient$syncTextFocus(){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null)return;
        boolean editing=runtime.isChatHudEditing();
        if(editing!=nightclient$editingInput){nightclient$editingInput=editing;GameBridge.textFocus((Screen)(Object)this,!editing);}
    }

    @Inject(method="@@CHAT_RENDER_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$editor(@@CONTEXT@@ graphics,int mouseX,int mouseY,float delta,CallbackInfo callback){
        GameBridge.renderChatEditor(this,graphics,width,height,mouseX,mouseY);
        nightclient$syncTextFocus();
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime!=null&&runtime.isChatHudEditing())callback.cancel();
    }
    @Inject(method="@@CHAT_RENDER_HOOK@@",at=@At("TAIL"))
    private void nightclient$chrome(@@CONTEXT@@ graphics,int mouseX,int mouseY,float delta,CallbackInfo callback){
        GameBridge.renderChatChrome(this,graphics,width,height,mouseX,mouseY);nightclient$syncTextFocus();
    }
    @Inject(method="init()V",at=@At("TAIL"))
    private void nightclient$initDraft(CallbackInfo callback){
        dev.nightclient.platform.WorldFeatureBridge.onChatScreenInit(this);
    }
    @Inject(method="@@CHAT_CLICK_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$click(@@CHAT_CLICK_ARGS@@CallbackInfoReturnable<Boolean> callback){
        ClientRuntime runtime=ClientRuntime.get();if(runtime!=null&&runtime.chatHudMouseClicked(this,@@CHAT_MOUSE_ARGS@@)){callback.setReturnValue(true);return;}
        nightclient$syncTextFocus();
        if(dev.nightclient.platform.WorldFeatureBridge.handleChatCopyClick(this,@@CHAT_MOUSE_ARGS@@)){callback.setReturnValue(true);return;}
        if(dev.nightclient.platform.WorldFeatureBridge.handleChatCoordinateClick(this,@@CHAT_MOUSE_ARGS@@))callback.setReturnValue(true);
    }
    @Inject(method="@@CHAT_SCROLL_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$scroll(@@CHAT_SCROLL_ARGS@@CallbackInfoReturnable<Boolean> callback){
        ClientRuntime runtime=ClientRuntime.get();if(runtime!=null&&runtime.chatHudMouseScrolled(this,mouseX,mouseY,vertical,GameBridge.scrollModifiers()))callback.setReturnValue(true);
    }
    @Inject(method="@@CHAT_KEY_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$key(@@CHAT_KEY_ARGS@@CallbackInfoReturnable<Boolean> callback){
        ClientRuntime runtime=ClientRuntime.get();if(runtime!=null&&runtime.chatHudKeyPressed(this,@@CHAT_KEY_VALUES@@))callback.setReturnValue(true);
        nightclient$syncTextFocus();
    }
    @Inject(method="removed()V",at=@At("HEAD"))
    private void nightclient$removed(CallbackInfo callback){
        dev.nightclient.platform.WorldFeatureBridge.onChatScreenRemoved(this);
        ClientRuntime runtime=ClientRuntime.get();if(runtime!=null)runtime.chatHudClosed(this);nightclient$editingInput=false;
    }
    @Override public boolean mouseDragged(@@CHAT_DRAG_SIGNATURE@@){
        ClientRuntime runtime=ClientRuntime.get();
        return runtime!=null&&runtime.chatHudMouseDragged(this,@@CHAT_MOUSE_ARGS@@,deltaX,deltaY)||@@CHAT_SUPER_DRAG@@;
    }
    @Override public boolean mouseReleased(@@CHAT_RELEASE_SIGNATURE@@){
        ClientRuntime runtime=ClientRuntime.get();
        return runtime!=null&&runtime.chatHudMouseReleased(this,@@CHAT_MOUSE_ARGS@@)||@@CHAT_SUPER_RELEASE@@;
    }
    @Override public boolean keyReleased(@@CHAT_KEY_SIGNATURE@@){
        ClientRuntime runtime=ClientRuntime.get();
        return runtime!=null&&runtime.chatHudKeyReleased(this,@@CHAT_KEY_VALUES@@)||@@CHAT_SUPER_KEY_RELEASE@@;
    }
    @@CHAT_CHARACTER_OVERRIDE@@
}
