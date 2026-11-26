package dev.nightclient.platform.mixin;

import dev.nightclient.platform.LiteApiBridge;
import dev.nightclient.platform.LiteApiPayload;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
@@LITEAPI_DISCONNECT_IMPORT@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class LiteApiClientCommonMixin {
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true)
    private void nightclient$onLiteApiPayload(ClientboundCustomPayloadPacket packet, CallbackInfo callback) {
        if (packet != null && packet.payload() instanceof LiteApiPayload) {
            LiteApiBridge.onPayload(((LiteApiPayload) packet.payload()).data());
            callback.cancel();
        }
    }

    @Inject(method = "@@LITEAPI_DISCONNECT_METHOD@@", at = @At("HEAD"))
    private void nightclient$onDisconnect(@@LITEAPI_DISCONNECT_ARG@@, CallbackInfo callback) {
        LiteApiBridge.onDisconnect();
    }
}
