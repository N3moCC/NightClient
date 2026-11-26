package dev.nightclient.platform.mixin;

import dev.nightclient.config.OptimizationFlags;
import dev.nightclient.platform.BenchmarkDirector;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.StaticEntityVboCache;
import dev.nightclient.platform.WorldOcclusionSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class OptEntityEarlyCullMixin {
  @Inject(
      method = "@@ENTITY_SHOULD_RENDER_METHOD@@",
      at = @At("HEAD"),
      cancellable = true)
  private <E extends Entity> void nightclient$earlyCullEntity(
      E entity,
      Frustum frustum,
      double camX,
      double camY,
      double camZ,
      @@ENTITY_SHOULD_RENDER_EXTRA_ARG@@CallbackInfoReturnable<Boolean> cir) {
    if (!OptimizationBridge.anyEntityEarlyOpt || entity == null) return;
    if ((OptimizationBridge.opt028StaticEntityBake || OptimizationBridge.opt147StaticVramBatch)
        && StaticEntityVboCache.trySkipStaticEntity(entity)) {
      cir.setReturnValue(false);
      return;
    }
    if (entity instanceof Player
        || entity.isCurrentlyGlowing()
        || entity instanceof PrimedTnt
        || entity instanceof EndCrystal
        || entity instanceof EnderDragon
        || entity instanceof WitherBoss) {
      return;
    }

    double dx = entity.getX() - camX;
    double dy = entity.getY() - camY;
    double dz = entity.getZ() - camZ;
    double distSq = dx * dx + dy * dy + dz * dz;

    if (OptimizationBridge.isOptActive(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING)) {
      int shift = distSq > 25.0 ? 2 : 1;
      int cap = distSq > 64.0 ? Math.max(1, OptimizationBridge.itemClusterLimit - 2) : OptimizationBridge.itemClusterLimit;
      if (entity instanceof ItemEntity) {
        ItemEntity item = (ItemEntity) entity;
        int typeHash = System.identityHashCode(item.getItem().getItem());
        if (OptimizationBridge.ITEM_CLUSTERS.shouldCull(
            entity.getBlockX() >> shift, entity.getBlockY() >> shift, entity.getBlockZ() >> shift,
            typeHash, cap)) {
          if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledEntity();
          cir.setReturnValue(false);
          return;
        }
      } else if (entity instanceof ExperienceOrb) {
        if (OptimizationBridge.ITEM_CLUSTERS.shouldCull(
            entity.getBlockX() >> shift, entity.getBlockY() >> shift, entity.getBlockZ() >> shift,
            0x58504F52, Math.max(1, cap - 1))) {
          if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledEntity();
          cir.setReturnValue(false);
          return;
        }
      }
    }

    if (entity instanceof Projectile) {
      return;
    }

    if (OptimizationBridge.isOptActive(OptimizationFlags.OPT_023_EARLY_ENTITY_CULL)) {
      double hw = entity.getBbWidth() * 0.5;
      double hh = entity.getBbHeight() * 0.5;
      if (OptimizationBridge.cullEntity(distSq, hw * hw + hh * hh, true, false, false)) {
        if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledEntity();
        cir.setReturnValue(false);
        return;
      }
    }

    if (OptimizationBridge.isOptActive(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION)) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && entity.level() == mc.level) {
        AABB bb = entity.getBoundingBox();
        if (OptimizationBridge.OCCLUSION.isOccluded(
            0x100000000L | (entity.getId() & 0xFFFFFFFFL),
            camX, camY, camZ,
            bb.minX, bb.minY, bb.minZ,
            bb.maxX, bb.maxY, bb.maxZ,
            WorldOcclusionSampler.INSTANCE)) {
          if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledEntity();
          cir.setReturnValue(false);
        }
      }
    }
  }
}
