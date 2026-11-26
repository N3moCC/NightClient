package dev.nightclient.platform.mixin;

import dev.nightclient.platform.RetainedRenderTypeDrawer;
import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
@@RETAINED_RT_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
@@RETAINED_RT_EXTRA_MIXIN_IMPORTS@@

@Mixin(@@RETAINED_RT_OWNER@@.class)
public abstract class OptRenderTypeRetainedMixin implements RetainedRenderTypeDrawer {
  @@RETAINED_RT_BODY@@
}
