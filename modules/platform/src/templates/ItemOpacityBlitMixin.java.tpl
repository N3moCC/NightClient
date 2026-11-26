package dev.nightclient.platform.mixin;

import dev.nightclient.platform.ItemOpacity;
import @@PIPELINE_PACKAGE@@.RenderPipeline;
import @@GUI_STATE_PACKAGE@@.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.@@BLIT_POSE_TYPE@@;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlitRenderState.class)
public abstract class ItemOpacityBlitMixin {
    @Shadow @Final private RenderPipeline pipeline;
    @Shadow @Final private @@BLIT_POSE_TYPE@@ pose;
    @Shadow @Final @Mutable private int color;
    @Inject(method="@@BLIT_CTOR@@",at=@At("RETURN"))
    private void nightclient$applyOpacity(CallbackInfo callback){
        if(pipeline==RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA)color=ItemOpacity.color(pose,color);
    }
}
