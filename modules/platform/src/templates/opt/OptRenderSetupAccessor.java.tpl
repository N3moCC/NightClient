package dev.nightclient.platform.mixin;

import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface OptRenderSetupAccessor {
  @Accessor("textureTransform")
  TextureTransform nightclient$textureTransform();

  @Accessor("outputTarget")
  OutputTarget nightclient$outputTarget();

  @Accessor("useLightmap")
  boolean nightclient$useLightmap();

  @Accessor("useOverlay")
  boolean nightclient$useOverlay();
}
