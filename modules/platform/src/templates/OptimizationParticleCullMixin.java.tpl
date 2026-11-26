package dev.nightclient.platform.mixin;

import dev.nightclient.platform.BenchmarkDirector;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.AttackSweepParticle;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.world.phys.AABB;
@@PARTICLE_GROUP_IMPORT@@
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ParticleEngine.class)
public abstract class OptimizationParticleCullMixin {
  @Shadow @Final private Map<?, ?> particles;
  @Unique private long nightclient$countTick = -1L;
  @Unique private int nightclient$cachedCount;

  @Inject(method = "tick()V", at = @At("HEAD"))
  private void nightclient$refreshParticleCount(CallbackInfo ci) {
    if (!OptimizationBridge.particleCullingActive()) {
      nightclient$cachedCount = 0;
      return;
    }
    int count = 0;
    for (Object group : particles.values()) {
      if (group instanceof @@PARTICLE_GROUP@@) count += ((@@PARTICLE_GROUP@@) group).size();
    }
    nightclient$cachedCount = count;
    OptimizationBridge.liveParticleCount = count;
    OptimizationBridge.PARTICLE_CLUSTERS.beginFrame(++OptimizationBridge.particleTickIndex);
    nightclient$countTick = OptimizationBridge.frameIndex;
  }

  @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
  private void nightclient$cullParticle(Particle particle, CallbackInfo callback) {
    if (!OptimizationBridge.particleCullingActive() || particle == null) return;
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null) return;
    AABB box = particle.getBoundingBox();
    double cx = (box.minX + box.maxX) * 0.5;
    double cy = (box.minY + box.maxY) * 0.5;
    double cz = (box.minZ + box.maxZ) * 0.5;
    double dx = cx - minecraft.player.getX();
    double dy = cy - minecraft.player.getEyeY();
    double dz = cz - minecraft.player.getZ();
    double distSq = dx * dx + dy * dy + dz * dz;
    if (OptimizationBridge.cullParticleDistance(distSq)) {
      if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledParticle();
      callback.cancel();
      return;
    }
    boolean importantPvp = distSq < 100.0
        && (particle instanceof CritParticle
            || particle instanceof TotemParticle
            || particle instanceof AttackSweepParticle);
    if (!importantPvp && distSq > 25.0) {
      int typeHash = System.identityHashCode(particle.getClass());
      if (OptimizationBridge.PARTICLE_CLUSTERS.shouldCull(
          (int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz), typeHash, 2)) {
        if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledParticle();
        callback.cancel();
        return;
      }
    }
    if (nightclient$countTick < 0L) {
      int count = 0;
      for (Object group : particles.values()) {
        if (group instanceof @@PARTICLE_GROUP@@) count += ((@@PARTICLE_GROUP@@) group).size();
      }
      nightclient$cachedCount = count;
      nightclient$countTick = OptimizationBridge.frameIndex;
    }
    if (OptimizationBridge.cullParticle(distSq, nightclient$cachedCount)) {
      if (importantPvp) {
        nightclient$cachedCount++;
        return;
      }
      if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledParticle();
      callback.cancel();
      return;
    }
    nightclient$cachedCount++;
  }
}
