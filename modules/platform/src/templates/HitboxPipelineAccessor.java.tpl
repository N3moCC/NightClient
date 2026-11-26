package dev.nightclient.platform.mixin;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(RenderPipelines.class)
public interface HitboxPipelineAccessor {
    @Accessor("DEBUG_FILLED_SNIPPET") static RenderPipeline.Snippet nightclient$quads(){throw new AssertionError();}
}
