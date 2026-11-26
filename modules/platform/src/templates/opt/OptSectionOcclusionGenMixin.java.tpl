package dev.nightclient.platform.mixin;

import dev.nightclient.platform.TerrainFaceBuckets;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
public abstract class OptSectionOcclusionGenMixin {
  @Inject(method = "addSectionsInFrustum", at = @At("RETURN"))
  private void nightclient$onFrustumSectionsUpdated(CallbackInfo ci) {
    TerrainFaceBuckets.visibleGen++;
  }

  @Inject(method = "invalidate", at = @At("HEAD"))
  private void nightclient$onGraphInvalidated(CallbackInfo ci) {
    TerrainFaceBuckets.visibleGen++;
  }

  @Inject(method = "waitAndReset", at = @At("HEAD"))
  private void nightclient$onGraphReset(CallbackInfo ci) {
    TerrainFaceBuckets.visibleGen++;
  }
}
