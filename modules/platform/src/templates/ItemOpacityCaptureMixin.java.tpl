package dev.nightclient.platform.mixin;

import dev.nightclient.platform.ItemOpacity;
import @@GUI_STATE_PACKAGE@@.GuiItemRenderState;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemRenderState.class)
public abstract class ItemOpacityCaptureMixin {
    @Shadow @Final private Matrix3x2f pose;
    @Inject(method="<init>",at=@At("RETURN"))
    private void nightclient$captureOpacity(CallbackInfo callback){ItemOpacity.capture(pose);}
}
