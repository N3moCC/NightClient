package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
@@STATIC_BE_LEVEL_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
@@STATIC_BE_LEVEL_EXTRA_IMPORTS@@

@Mixin(@@STATIC_BE_LEVEL_OWNER@@.class)
public abstract class OptStaticBeLevelMixin {
  @@STATIC_BE_LEVEL_BODY@@
}
