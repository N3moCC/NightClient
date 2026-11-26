package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(EditBox.class)
public abstract class PasswordEditBoxMixin {
    @Shadow private String value;
    @Shadow private String suggestion;
    @Redirect(method="@@EDITBOX_RENDER@@",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/components/EditBox;value:Ljava/lang/String;",opcode=180))
    private String nightclient$password(EditBox owner){return FeatureBridge.protectInput(owner,value);}
    @Redirect(method="@@EDITBOX_RENDER@@",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/components/EditBox;suggestion:Ljava/lang/String;",opcode=180))
    private String nightclient$suggestion(EditBox owner){return FeatureBridge.protectSuggestion(owner,suggestion);}
}
