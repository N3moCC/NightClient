package dev.nightclient.platform.mixin;

import dev.nightclient.platform.TerrainFaceBuckets;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@@COMPILED_MESH_EXTRA_IMPORTS@@

@Mixin(CompiledSectionMesh.class)
public abstract class OptCompiledSectionMeshMixin implements TerrainFaceBuckets.SliceCacheHolder {
  @Unique private Object[] nightclient$cachedSlices;
  @Unique private long nightclient$cachedSliceGen = -1L;

  @Override
  public Object[] nightclient$getCachedSlices() {
    return this.nightclient$cachedSlices;
  }

  @Override
  public long nightclient$getCachedSliceGen() {
    return this.nightclient$cachedSliceGen;
  }

  @Override
  public void nightclient$setCachedSlices(Object[] slices, long gen) {
    this.nightclient$cachedSlices = slices;
    this.nightclient$cachedSliceGen = gen;
  }

  @@COMPILED_MESH_EXTRA_BODY@@
}
