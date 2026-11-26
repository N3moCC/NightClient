package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
@@TAB_PING_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabPingMixin {
    private Font nightclient$pendingFont;
    private Component nightclient$pendingName;
    private int nightclient$pendingX;
    private int nightclient$pendingY;
    private int nightclient$pendingColor;

    private void nightclient$flushPendingName(@@CONTEXT@@ graphics){
        Component name=this.nightclient$pendingName;
        if(name==null)return;
        Font font=this.nightclient$pendingFont;
        int x=this.nightclient$pendingX;
        int y=this.nightclient$pendingY;
        int color=this.nightclient$pendingColor;
        this.nightclient$pendingName=null;
        this.nightclient$pendingFont=null;
        if(graphics!=null && font!=null){
            @@TAB_PING_FLUSH_NAME@@
        }
    }

    @ModifyConstant(method="@@TAB_RENDER_HOOK@@",constant=@Constant(intValue=13))
    private int nightclient$tabPingWidth(int original){
        return WorldFeatureBridge.tabPingReserveWidth(original);
    }

    @Redirect(method="@@TAB_RENDER_HOOK@@",at=@At(value="INVOKE",target="@@TAB_NAME_DRAW_TARGET@@"))
    private @@TAB_NAME_REDIRECT_RET@@ nightclient$deferTabPlayerName(@@CONTEXT@@ graphics,Font font,Component name,int x,int y,int color){
        if(WorldFeatureBridge.isTabPingActive()){
            this.nightclient$flushPendingName(graphics);
            this.nightclient$pendingFont=font;
            this.nightclient$pendingName=name;
            this.nightclient$pendingX=x;
            this.nightclient$pendingY=y;
            this.nightclient$pendingColor=color;
            @@TAB_NAME_REDIRECT_DEFER_RET@@
        }
        @@TAB_NAME_REDIRECT_FALLBACK@@
    }

    @Inject(method="@@TAB_PING_ICON_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$renderTabPing(@@CONTEXT@@ graphics,int slotWidth,int x,int y,PlayerInfo info,CallbackInfo callback){
        if(WorldFeatureBridge.renderTabPing(graphics,slotWidth,x,y,info)){
            this.nightclient$flushPendingName(graphics);
            callback.cancel();
        }
    }

    @Inject(method="@@TAB_PING_ICON_HOOK@@",at=@At("TAIL"))
    private void nightclient$renderTabPingTail(@@CONTEXT@@ graphics,int slotWidth,int x,int y,PlayerInfo info,CallbackInfo callback){
        this.nightclient$flushPendingName(graphics);
    }

    @Inject(method="setHeader(Lnet/minecraft/network/chat/Component;)V",at=@At("HEAD"))
    private void nightclient$onTabHeader(Component header,CallbackInfo callback){
        WorldFeatureBridge.onTabHeader(header);
    }

    @Inject(method="@@TAB_RENDER_HOOK@@",at=@At("RETURN"))
    private void nightclient$renderTabReturn(@@CONTEXT@@ graphics,int width,net.minecraft.world.scores.Scoreboard scoreboard,net.minecraft.world.scores.Objective objective,CallbackInfo callback){
        this.nightclient$flushPendingName(graphics);
    }
}
