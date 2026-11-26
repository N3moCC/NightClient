package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.TextQuadCache;
@@TEXT_MIXIN_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFeatureRenderer.class)
public abstract class OptTextFeatureCacheMixin {
@@TEXT_MIXIN_BODY@@
}
