package dev.nightclient.platform.mixin;

import dev.nightclient.config.OptimizationFlags;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public abstract class OptParticlePhysicsMixin {
  @Shadow protected double x;
  @Shadow protected double y;
  @Shadow protected double z;
  @Shadow protected boolean hasPhysics;
  @Shadow public abstract AABB getBoundingBox();
  @Shadow public abstract void setBoundingBox(AABB box);
  @Shadow protected abstract void setLocationFromBoundingbox();

  @Inject(method = "move(DDD)V", at = @At("HEAD"), cancellable = true)
  private void nightclient$fastParticleMove(double dx, double dy, double dz, CallbackInfo ci) {
    if (!OptimizationBridge.opt056ParticleCollision) {
      return;
    }
    if (!this.hasPhysics) return;
    if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
      ci.cancel();
      return;
    }
    double cdx = this.x - OptimizationBridge.camX;
    double cdy = this.y - OptimizationBridge.camY;
    double cdz = this.z - OptimizationBridge.camZ;
    if (cdx * cdx + cdy * cdy + cdz * cdz > 36.0) {
      this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
      this.setLocationFromBoundingbox();
      ci.cancel();
    }
  }
}
