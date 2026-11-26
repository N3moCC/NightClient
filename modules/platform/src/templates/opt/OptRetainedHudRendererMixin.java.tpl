package dev.nightclient.platform.mixin;

import dev.nightclient.platform.RetainedHudState;
import java.util.List;
import net.minecraft.client.gui.render.GuiRenderer;
@@RHUD_RENDERER_IMPORTS@@
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class OptRetainedHudRendererMixin {
@@RHUD_RENDERER_BODY@@
}
