package dev.nightclient.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownEntryAccessor {
  @Accessor("startTime") int nightclient$startTime();

  @Accessor("endTime") int nightclient$endTime();
}
