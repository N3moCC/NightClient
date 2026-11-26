package dev.nightclient.platform.mixin;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(@@HUD_OWNER@@.class)
public interface GuiOverlayAccessor {
    @Accessor("overlayMessageString")
    Component nightclient$overlayMessageString();

    @Accessor("overlayMessageTime")
    int nightclient$overlayMessageTime();
}
