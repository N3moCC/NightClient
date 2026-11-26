package dev.nightclient.platform.mixin;

import dev.nightclient.config.OptimizationFlags;
import dev.nightclient.platform.BenchmarkDirector;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.WorldOcclusionSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
@@BE_EARLY_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
@@BE_EARLY_CALLBACK_IMPORT@@

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class OptBlockEntityEarlyCullMixin {
  @@BE_EARLY_BODY@@
}
