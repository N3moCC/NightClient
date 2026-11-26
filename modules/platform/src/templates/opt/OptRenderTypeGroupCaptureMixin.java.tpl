package dev.nightclient.platform.mixin;

import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
public abstract class OptRenderTypeGroupCaptureMixin {
  @Inject(
      method = "getOrAddDraw(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lnet/minecraft/client/renderer/StagedVertexBuffer$Draw;",
      at = @At("RETURN"))
  private void nightclient$captureStagedDraw(
      RenderType rt, CallbackInfoReturnable<StagedVertexBuffer.Draw> cir) {
    if (StaticBeVboCache.isCapturing()) {
      StaticBeVboCache.onCapturedStagedDraw(rt, cir.getReturnValue());
    } else if (StaticEntityVboCache.isCapturing()) {
      StaticEntityVboCache.onCapturedStagedDraw(rt, cir.getReturnValue());
    }
  }
}
