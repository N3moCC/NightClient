package dev.nightclient.platform.mixin;

import dev.nightclient.platform.StaticBeVboCache;
import dev.nightclient.platform.StaticEntityVboCache;
@@STATIC_EXEC_EXTRA_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedFrame")
public abstract class OptStaticBeExecuteMixin {
  @@STATIC_EXEC_BODY@@
}
