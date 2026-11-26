package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.config.OptimizationFlags;
import dev.nightclient.platform.OptimizationBridge;
@@SIGN_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(@@SIGN_OWNER@@.class)
public abstract class OptSignTextCullMixin {
  @@SIGN_BODY@@
}
