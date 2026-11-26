package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nightclient.platform.FastEntityVertexWriter;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
@@SPRITE_EXPANDER_EXTRA_IMPORT@@
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpriteCoordinateExpander.class)
public abstract class OptSpriteExpanderFastMixin implements FastEntityVertexWriter {
  @Shadow @Final private VertexConsumer delegate;
  @@SPRITE_EXPANDER_FIELD@@

  @Override
  public boolean nightclient$emitFlatCube(
      float[] flatQuads,
      Matrix4f pose,
      Matrix3f normal,
      boolean trustedNormals,
      int packedLight,
      int packedOverlay,
      int color) {
    if (!(this.delegate instanceof FastEntityVertexWriter)) {
      return false;
    }
    @@SPRITE_EXPANDER_RESOLVE@@
    float u0 = s.getU0();
    float v0 = s.getV0();
    return ((FastEntityVertexWriter) this.delegate).nightclient$emitFlatCubeUv(
        flatQuads,
        pose,
        normal,
        trustedNormals,
        packedLight,
        packedOverlay,
        color,
        u0,
        s.getU1() - u0,
        v0,
        s.getV1() - v0);
  }
}
