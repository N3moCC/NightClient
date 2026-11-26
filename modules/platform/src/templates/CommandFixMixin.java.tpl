package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import @@COMMAND_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.@@COMMAND_CALLBACK_TYPE@@;
@Mixin(@@COMMAND_SHORT@@.class)
public abstract class CommandFixMixin {
    @Unique private String nightclient$source;
    @Unique private String nightclient$rewritten;
    @Unique private boolean nightclient$active;
    @Unique private boolean nightclient$historySaved;

    @ModifyVariable(method="@@COMMAND_HOOK@@",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private String nightclient$command(String original){
        if(nightclient$active)return nightclient$rewritten;
        String next=WorldFeatureBridge.fixCommand(original);
        nightclient$source=original;
        nightclient$rewritten=next;
        nightclient$active=true;
        nightclient$historySaved=false;
        return next;
    }

    @ModifyVariable(method="@@COMMAND_HOOK@@",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean nightclient$addToChat(boolean addToChat){
        if(!nightclient$active||!addToChat)return addToChat;
        String orig=nightclient$source;
        String next=nightclient$rewritten;
        if(orig!=null&&next!=null&&(next.isEmpty()||!next.equals(orig))){
            WorldFeatureBridge.addRecentChat(orig);
            nightclient$historySaved=true;
            return false;
        }
        return addToChat;
    }

    @Inject(method="@@COMMAND_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$intercept(String command,boolean addToChat,@@COMMAND_CALLBACK_PARAM@@ callback){
        String orig;
        String next;
        boolean saved;
        if(nightclient$active){
            orig=nightclient$source;
            next=nightclient$rewritten;
            saved=nightclient$historySaved;
            nightclient$active=false;
            nightclient$source=null;
            nightclient$rewritten=null;
            nightclient$historySaved=false;
        }else{
            orig=command;
            next=WorldFeatureBridge.fixCommand(command);
            saved=false;
            nightclient$source=orig;
            nightclient$rewritten=next;
            nightclient$active=true;
            nightclient$historySaved=false;
        }
        if(next!=null&&next.isEmpty()){
            if(addToChat&&!saved&&orig!=null)WorldFeatureBridge.addRecentChat(orig);
            nightclient$active=false;
            nightclient$source=null;
            nightclient$rewritten=null;
            nightclient$historySaved=false;
            @@COMMAND_CANCEL@@
        }
    }

    @Inject(method="@@COMMAND_HOOK@@",at=@At("RETURN"))
    private void nightclient$cleanup(String command,boolean addToChat,@@COMMAND_CALLBACK_PARAM@@ callback){
        nightclient$active=false;
        nightclient$source=null;
        nightclient$rewritten=null;
        nightclient$historySaved=false;
    }
}
