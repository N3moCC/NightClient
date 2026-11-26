package dev.nightclient.platform.mixin;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.platform.LiteApiPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.@@COSMETICS_IDENTIFIER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class LiteApiPayloadCodecMixin {
    @Inject(method = "findCodec(Lnet/minecraft/resources/@@COSMETICS_IDENTIFIER@@;)Lnet/minecraft/network/codec/StreamCodec;", at = @At("HEAD"), cancellable = true)
    private void nightclient$findLiteApiCodec(@@COSMETICS_IDENTIFIER@@ id, CallbackInfoReturnable<StreamCodec<?, ? extends CustomPacketPayload>> callback) {
        if (id != null && FeaturePolicy.CHANNEL.equals(id.toString())) {
            callback.setReturnValue(LiteApiPayload.STREAM_CODEC);
        }
    }
}
