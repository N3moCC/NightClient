package dev.nightclient.platform.mixin;

import dev.nightclient.platform.NativeGpuBridge;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.TextQuadCache;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NameTagFeatureRenderer.class)
public abstract class OptNativeNameTagMixin {
  @Inject(
      method = "render(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/gui/Font;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$nativeRenderNameTags(
      SubmitNodeCollection collection,
      MultiBufferSource.BufferSource bufferSource,
      Font font,
      CallbackInfo ci) {
    if (!OptimizationBridge.opt144TextDisplayCache && !OptimizationBridge.opt165NativeGpuInstancing) {
      return;
    }
    if (font == null) return;
    NameTagFeatureRenderer.Storage storage = collection.getNameTagSubmits();
    if (storage == null) return;
    OptNameTagStorageAccessor acc = (OptNameTagStorageAccessor) (Object) storage;
    List<SubmitNodeStorage.NameTagSubmit> seeThrough = acc.nightclient$seeThrough();
    List<SubmitNodeStorage.NameTagSubmit> normal = acc.nightclient$normal();
    if ((seeThrough == null || seeThrough.isEmpty()) && (normal == null || normal.isEmpty())) {
      return;
    }

    TextQuadCache.ConsumerProvider provider = bufferSource::getBuffer;

    if (seeThrough != null && !seeThrough.isEmpty()) {
      seeThrough.sort(Comparator.comparing(SubmitNodeStorage.NameTagSubmit::distanceToCameraSq).reversed());
      for (int i = 0; i < seeThrough.size(); i++) {
        SubmitNodeStorage.NameTagSubmit s = seeThrough.get(i);
        TextQuadCache.Entry entry = TextQuadCache.getOrBuildComponent(
            font, s.text(), s.x(), s.y(), s.color(), false, Font.DisplayMode.SEE_THROUGH, s.backgroundColor());
        if (entry != null) {
          TextQuadCache.emitEntry(entry, s.pose(), s.lightCoords(), provider);
        } else {
          font.drawInBatch(
              s.text(), s.x(), s.y(), s.color(), false, s.pose(), bufferSource,
              Font.DisplayMode.SEE_THROUGH, s.backgroundColor(), s.lightCoords());
        }
      }
    }

    if (normal != null && !normal.isEmpty()) {
      for (int i = 0; i < normal.size(); i++) {
        SubmitNodeStorage.NameTagSubmit s = normal.get(i);
        TextQuadCache.Entry entry = TextQuadCache.getOrBuildComponent(
            font, s.text(), s.x(), s.y(), s.color(), false, Font.DisplayMode.NORMAL, s.backgroundColor());
        if (entry != null) {
          TextQuadCache.emitEntry(entry, s.pose(), s.lightCoords(), provider);
        } else {
          font.drawInBatch(
              s.text(), s.x(), s.y(), s.color(), false, s.pose(), bufferSource,
              Font.DisplayMode.NORMAL, s.backgroundColor(), s.lightCoords());
        }
      }
    }
    ci.cancel();
  }
}
