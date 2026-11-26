package dev.nightclient.platform.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void nightclient$setXpos(double x);

    @Accessor("ypos")
    void nightclient$setYpos(double y);
}
