package dev.nightclient.platform.mixin;

import dev.nightclient.platform.AtmosphereBackend;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectionMatrixBuffer.class)
public abstract class AtmosphereProjectionMixin {
  @Unique private final float[] nightclient$projScratch = new float[16];

  @Inject(method = "getBuffer(Lorg/joml/Matrix4f;)@@ATMO_SLICE_DESC@@", at = @At("HEAD"))
  private void nightclient$captureBobbedProjection(
      Matrix4f matrix, CallbackInfoReturnable<@@ATMO_SLICE_CLASS@@> cir) {
    if (matrix != null && Math.abs(matrix.m23() + 1.0f) <= 0.05f && Math.abs(matrix.m33()) <= 0.05f) {
      matrix.get(nightclient$projScratch);
      AtmosphereBackend.captureBobbedProjection(nightclient$projScratch);
    }
  }
}
