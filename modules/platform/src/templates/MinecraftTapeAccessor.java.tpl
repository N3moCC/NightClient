package dev.nightclient.platform.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftTapeAccessor {
    @Invoker("startAttack")
    boolean nightclient$startAttack();

    @Invoker("continueAttack")
    void nightclient$continueAttack(boolean leftClick);

    @Invoker("startUseItem")
    void nightclient$startUseItem();

    @Accessor("missTime")
    void nightclient$setMissTime(int value);

    @Accessor("rightClickDelay")
    void nightclient$setRightClickDelay(int value);
}
