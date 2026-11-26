package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(Font.class)
public abstract class ProtectFontMixin {
    @ModifyVariable(method={@@FONT_STRING_METHODS@@},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private String nightclient$nickname(String original){return FeatureBridge.protectText(original);}
    @ModifyVariable(method={@@FONT_SEQUENCE_METHODS@@},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private FormattedCharSequence nightclient$nicknameSequence(FormattedCharSequence original){return FeatureBridge.protectText(original);}
    @ModifyVariable(method="width(Lnet/minecraft/network/chat/FormattedText;)I",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private FormattedText nightclient$nicknameWidth(FormattedText original){return FeatureBridge.protectText(original);}
}
