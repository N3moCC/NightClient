package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
@@HIT_COLOR_ARMOR_IMPORTS@@

@Mixin(@@HIT_COLOR_ARMOR_TARGET@@.class)
public abstract class HitColorArmorMixin {
@@HIT_COLOR_ARMOR_BODY@@
}
