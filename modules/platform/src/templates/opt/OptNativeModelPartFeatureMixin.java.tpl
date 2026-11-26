package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nightclient.platform.NativeGpuBridge;
import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPartFeatureRenderer.class)
public abstract class OptNativeModelPartFeatureMixin {
  @Shadow @Final private PoseStack poseStack;

  @Inject(
      method = "render(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$nativeRenderModelParts(
      SubmitNodeCollection collection,
      MultiBufferSource.BufferSource bufferSource,
      OutlineBufferSource outlineBufferSource,
      MultiBufferSource.BufferSource crumblingBufferSource,
      CallbackInfo ci) {
    if (StaticBeVboCache.isCapturing()
        || StaticEntityVboCache.isCapturing()
        || !NativeGpuBridge.isEntityInstancingActive()) {
      return;
    }
    ModelPartFeatureRenderer.Storage storage = collection.getModelPartSubmits();
    if (storage == null) return;
    Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> map =
        ((OptModelPartStorageAccessor) (Object) storage).nightclient$modelPartSubmits();
    if (map == null || map.isEmpty()) return;

    boolean allHandled = true;
    for (Map.Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> entry : map.entrySet()) {
      RenderType rt = entry.getKey();
      List<SubmitNodeStorage.ModelPartSubmit> list = entry.getValue();
      if (list == null || list.isEmpty()) continue;

      NativeGpuBridge.EntityRtMeta meta = NativeGpuBridge.resolveEntityRt(rt);
      if (meta == null) {
        allHandled = false;
        continue;
      }

      boolean listAll = true;
      for (int i = 0; i < list.size(); i++) {
        SubmitNodeStorage.ModelPartSubmit s = list.get(i);
        if (s.hasFoil() || s.outlineColor() != 0 || s.crumblingOverlay() != null) {
          listAll = false;
          break;
        }
      }
      if (!listAll) {
        allHandled = false;
        continue;
      }

      NativeGpuBridge.beginEntityBatch(meta, bufferSource, rt);
      try {
        for (int i = 0; i < list.size(); i++) {
          SubmitNodeStorage.ModelPartSubmit s = list.get(i);
          this.poseStack.pushPose();
          this.poseStack.last().set(s.pose());
          NativeGpuBridge.setEntitySprite(s.sprite());
          try {
            s.modelPart().render(
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
      list.clear();
    }
    if (allHandled) {
      ci.cancel();
    }
  }
}
