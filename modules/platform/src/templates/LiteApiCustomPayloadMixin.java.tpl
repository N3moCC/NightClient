package dev.nightclient.platform.mixin;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.platform.LiteApiPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class LiteApiCustomPayloadMixin {
    @Inject(method = "readPayload(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;", at = @At("HEAD"), cancellable = true)
    private static void nightclient$readLiteApi(ResourceLocation id, FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> callback) {
        if (id != null && FeaturePolicy.CHANNEL.equals(id.toString())) {
            callback.setReturnValue(LiteApiPayload.read(buf));
        }
    }
}
