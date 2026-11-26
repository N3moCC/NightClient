package dev.nightclient.platform.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
@@SESSION_ACCESSOR_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface SessionAccessor {
    @Accessor("user") User nightclient$user();
    @Mutable @Accessor("user") void nightclient$setUser(User value);
    @@SESSION_ACCESSOR_METHODS@@
}
