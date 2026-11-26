package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.TerrainFaceBuckets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@@TERRAIN_PREPARE_IMPORTS@@

@Mixin(LevelRenderer.class)
public abstract class OptTerrainPrepareMixin {
  @Unique private static final ChunkSectionLayer[] NC_LAYERS = ChunkSectionLayer.values();

  @Unique
  @SuppressWarnings("unchecked")
  private static final BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader>[] NC_UPLOADERS =
      ncBuildUploaders(4096);

  @Unique private static final TerrainFaceBuckets.SkyEmptyCache NC_SKY_CACHE =
      new TerrainFaceBuckets.SkyEmptyCache();
  @Unique private static final ArrayList<DynamicUniforms.ChunkSectionInfo> NC_SECTION_INFOS =
      new ArrayList<DynamicUniforms.ChunkSectionInfo>(1024);
  @Unique private static final BlockPos.MutableBlockPos NC_CAM_BLOCK_POS =
      new BlockPos.MutableBlockPos();
  @Unique private static final Matrix4f NC_SHARED_MV = new Matrix4f();

  @Unique private static long ncCachedVisGen = -1L;
  @Unique private static long ncCachedBufGen = -1L;
  @Unique private static int ncCachedVisCount = -1;
  @Unique private static int ncCachedModeBits = -1;
  @Unique private static int ncCachedCamCellX = Integer.MIN_VALUE;
  @Unique private static int ncCachedCamCellY = Integer.MIN_VALUE;
  @Unique private static int ncCachedCamCellZ = Integer.MIN_VALUE;
  @Unique private static Object ncCachedWorld;
  @Unique private static GpuTextureView ncCachedAtlasView;
  @Unique private static int ncCachedMaxIndices;
  @Unique private static boolean ncCachedAnyFading;
  @Unique private static DynamicUniforms.ChunkSectionInfo[] ncCachedInfos;

  @Unique
  @SuppressWarnings("unchecked")
  private static BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader>[] ncBuildUploaders(int n) {
    BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader>[] arr = new BiConsumer[n];
    for (int i = 0; i < n; i++) {
      final int idx = i;
      arr[i] = (slices, u) -> u.upload("ChunkSection", slices[idx]);
    }
    return arr;
  }

  @@TERRAIN_PREPARE_BODY@@
}
