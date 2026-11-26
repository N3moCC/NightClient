package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
@@BETTER_CHAT_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class BetterChatMixin {
    @Unique private boolean nightclient$chatPushed;

    @Inject(method="clearMessages(Z)V",at=@At("HEAD"),cancellable=true)
    private void nightclient$keepHistory(boolean clearSentMsgHistory,CallbackInfo callback){
        if(WorldFeatureBridge.onBetterChatClear(clearSentMsgHistory))callback.cancel();
    }

    @ModifyConstant(method=@@BETTER_CHAT_HISTORY_METHODS@@,constant=@Constant(intValue=100))
    private int nightclient$chatHistoryLimit(int original){
        return WorldFeatureBridge.betterChatHistoryLimit(original);
    }

    @ModifyVariable(method="@@BETTER_CHAT_ADD_HOOK@@",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Component nightclient$transformChatMessage(Component message){
        return WorldFeatureBridge.prepareBetterChatMessage(this,message);
    }

    @@BETTER_CHAT_TAG_HOOK@@

    @@BETTER_CHAT_RENDER_HOOKS@@
}
