package dev.nightclient.platform.mixin;

import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
@@STATIC_BE_FEATURE_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
@@STATIC_BE_FEATURE_EXTRA_IMPORTS@@

@Mixin(@@STATIC_BE_FEATURE_OWNER@@.class)
public abstract class OptStaticBeFeatureMixin {
  @@STATIC_BE_FEATURE_BODY@@
}
