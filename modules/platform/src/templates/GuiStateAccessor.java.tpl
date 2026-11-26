package dev.nightclient.platform.mixin;

import @@CONTEXT_IMPORT@@;
import @@GUI_STATE_PACKAGE@@.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(@@CONTEXT@@.class)
public interface GuiStateAccessor {
    @Accessor("guiRenderState") GuiRenderState nightclient$state();
}
