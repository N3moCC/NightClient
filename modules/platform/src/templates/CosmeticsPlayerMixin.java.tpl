package dev.nightclient.platform.mixin;

import dev.nightclient.platform.CosmeticsBridge;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@@COSMETICS_PLAYER_MIXIN_IMPORTS@@

@Mixin(AbstractClientPlayer.class)
public abstract class CosmeticsPlayerMixin {
@@COSMETICS_PLAYER_MIXIN_BODY@@
}
