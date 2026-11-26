package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class ExplosionPacketMixin {
    @Inject(method="handleExplosion(Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;)V",at=@At("TAIL"))
    private void nightclient$onExplosion(ClientboundExplodePacket packet,CallbackInfo callback){
        WorldFeatureBridge.onExplosionPacket(packet);
    }
}
