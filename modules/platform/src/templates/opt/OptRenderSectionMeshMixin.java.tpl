package dev.nightclient.platform.mixin;

import dev.nightclient.platform.TerrainFaceBuckets;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
@@RENDER_SECTION_MESH_CB_IMPORT@@

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class OptRenderSectionMeshMixin {
  @Inject(method = "setSectionMesh", at = @At("RETURN"))
  private void nightclient$onSetSectionMesh(SectionMesh mesh, @@RENDER_SECTION_MESH_CB_PARAM@@) {
    TerrainFaceBuckets.bufferGen++;
  }
}
