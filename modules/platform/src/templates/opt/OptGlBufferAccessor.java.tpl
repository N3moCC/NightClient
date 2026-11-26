package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.opengl.GlBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlBuffer.class)
public interface OptGlBufferAccessor {
  @Accessor("handle")
  int nightclient$handle();
}
