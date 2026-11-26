package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class TotemPopMixin {
  @Inject(
      method = "handleEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;)V",
      at = @At("RETURN"))
  private void nightclient$totemPop(ClientboundEntityEventPacket packet, CallbackInfo callback) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.level == null || packet == null) return;
    byte eventId = packet.getEventId();
    if (eventId == 35 || eventId == 3) {
      WorldFeatureBridge.onEntityEvent(packet.getEntity(mc.level), eventId);
    }
  }
}
