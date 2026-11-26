package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.RetainedHudState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
@@RHUD_EXTRACT_IMPORTS@@
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class OptRetainedHudExtractMixin {
  @Shadow @Final private Minecraft minecraft;
@@RHUD_EXTRACT_BODY@@
}
