package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import dev.nightclient.platform.TerrainFaceBuckets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UberGpuBuffer.class)
public abstract class OptUberGpuBufferGenMixin {
  @Inject(method = "uploadStagedAllocations", at = @At("RETURN"))
  private void nightclient$onUberUpload(CallbackInfoReturnable<Boolean> cir) {
    if (Boolean.TRUE.equals(cir.getReturnValue())) {
      TerrainFaceBuckets.bufferGen++;
    }
  }

  @Inject(method = "removeAllocation", at = @At("HEAD"))
  private void nightclient$onUberRemove(Object key, CallbackInfo ci) {
    TerrainFaceBuckets.bufferGen++;
  }

  @Inject(method = "freeAllocation", at = @At("HEAD"))
  private void nightclient$onUberFree(Object key, CallbackInfo ci) {
    TerrainFaceBuckets.bufferGen++;
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void nightclient$onUberClose(CallbackInfo ci) {
    TerrainFaceBuckets.bufferGen++;
  }
}
