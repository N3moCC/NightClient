package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nightclient.platform.NativeGpuBridge;
import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class OptNativeModelBatchMixin {
  @Shadow @Final private PoseStack poseStack;

  @Shadow
  private <S> void renderModel(
      SubmitNodeStorage.ModelSubmit<S> submit,
      RenderType renderType,
      VertexConsumer consumer,
      OutlineBufferSource outlineBufferSource,
      MultiBufferSource.BufferSource crumblingBufferSource) {}

  @Inject(
      method = "renderBatch(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Ljava/util/Map;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void nightclient$nativeRenderBatch(
      MultiBufferSource.BufferSource bufferSource,
      OutlineBufferSource outlineBufferSource,
      Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> submits,
      MultiBufferSource.BufferSource crumblingBufferSource,
      CallbackInfo ci) {
    if (SharedConstants.DEBUG_SHUFFLE_MODELS
        || StaticBeVboCache.isCapturing()
        || StaticEntityVboCache.isCapturing()
        || !NativeGpuBridge.isEntityInstancingActive()) {
      return;
    }

    for (Map.Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> entry : submits.entrySet()) {
      RenderType rt = entry.getKey();
      List<SubmitNodeStorage.ModelSubmit<?>> list = entry.getValue();
      if (list == null || list.isEmpty()) continue;

      NativeGpuBridge.EntityRtMeta meta = NativeGpuBridge.resolveEntityRt(rt);
      if (meta == null) {
        VertexConsumer vc = bufferSource.getBuffer(rt);
        for (int i = 0; i < list.size(); i++) {
          this.renderModel(list.get(i), rt, vc, outlineBufferSource, crumblingBufferSource);
        }
        continue;
      }

      VertexConsumer fallbackVc = null;
      NativeGpuBridge.beginEntityBatch(meta, bufferSource, rt);
      try {
        for (int i = 0; i < list.size(); i++) {
          SubmitNodeStorage.ModelSubmit s = list.get(i);
          if (s.outlineColor() != 0 || s.crumblingOverlay() != null) {
            if (fallbackVc == null) fallbackVc = bufferSource.getBuffer(rt);
            this.renderModel(s, rt, fallbackVc, outlineBufferSource, crumblingBufferSource);
            continue;
          }
          this.poseStack.pushPose();
          this.poseStack.last().set(s.pose());
          Model model = s.model();
          model.setupAnim(s.state());
          NativeGpuBridge.setEntitySprite(s.sprite());
          try {
            model.renderToBuffer(
                this.poseStack,
                NativeGpuBridge.ENTITY_COLLECTOR,
                s.lightCoords(),
                s.overlayCoords(),
                s.tintedColor());
          } finally {
            this.poseStack.popPose();
          }
        }
      } finally {
        NativeGpuBridge.endEntitySubmit();
      }
    }
    ci.cancel();
  }

  @Inject(
      method = "renderTranslucents(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Ljava/util/List;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void nightclient$nativeRenderTranslucents(
      MultiBufferSource.BufferSource bufferSource,
      OutlineBufferSource outlineBufferSource,
      List<SubmitNodeStorage.TranslucentModelSubmit<?>> submits,
      MultiBufferSource.BufferSource crumblingBufferSource,
      CallbackInfo ci) {
    if (submits == null
        || submits.isEmpty()
        || StaticBeVboCache.isCapturing()
        || StaticEntityVboCache.isCapturing()
        || !NativeGpuBridge.isEntityInstancingActive()) {
      return;
    }

    for (int i = 0; i < submits.size(); i++) {
      SubmitNodeStorage.TranslucentModelSubmit<?> ts = submits.get(i);
      RenderType rt = ts.renderType();
      SubmitNodeStorage.ModelSubmit s = ts.modelSubmit();
      NativeGpuBridge.EntityRtMeta meta = NativeGpuBridge.resolveEntityRt(rt);
      if (meta == null || s.outlineColor() != 0 || s.crumblingOverlay() != null) {
        this.renderModel(s, rt, bufferSource.getBuffer(rt), outlineBufferSource, crumblingBufferSource);
        continue;
      }
      this.poseStack.pushPose();
      this.poseStack.last().set(s.pose());
      Model model = s.model();
      model.setupAnim(s.state());
      NativeGpuBridge.beginEntitySubmit(meta, s.sprite(), bufferSource, rt);
      try {
        model.renderToBuffer(
            this.poseStack,
            NativeGpuBridge.ENTITY_COLLECTOR,
            s.lightCoords(),
            s.overlayCoords(),
            s.tintedColor());
      } finally {
        NativeGpuBridge.endEntitySubmit();
        this.poseStack.popPose();
      }
    }
    ci.cancel();
  }
}
