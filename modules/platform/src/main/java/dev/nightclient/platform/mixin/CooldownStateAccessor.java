package dev.nightclient.platform.mixin;

import java.util.Map;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCooldowns.class)
public interface CooldownStateAccessor {
  @Accessor("cooldowns") Map<?, ?> nightclient$cooldowns();

  @Accessor("tickCount") int nightclient$tickCount();
}
