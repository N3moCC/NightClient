package dev.nightclient.platform.mixin;

import dev.nightclient.platform.LiteApiBridge;
import dev.nightclient.platform.WorldMapBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.world.level.block.state.BlockState;
@@MAP_MIXIN_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class WorldMapChunkMixin {
    @Inject(method = "@@MAP_CHUNK_HOOK@@", at = @At("TAIL"))
    private void nightclient$onChunk(@@MAP_CHUNK_PACKET@@ packet, CallbackInfo callback) {
        if (packet != null) WorldMapBridge.onChunkLoaded(@@MAP_CHUNK_X@@, @@MAP_CHUNK_Z@@);
    }

    @Inject(method = "handleBlockUpdate(Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;)V", at = @At("TAIL"))
    private void nightclient$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo callback) {
        if (packet != null) WorldMapBridge.onBlockUpdated(packet.getPos());
    }

    @Inject(method = "handleChunkBlocksUpdate(Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;)V", at = @At("TAIL"))
    private void nightclient$onSectionBlocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo callback) {
        if (packet == null) return;
        final boolean[] queued = new boolean[1];
        packet.runUpdates(new java.util.function.BiConsumer<BlockPos, BlockState>() {
            @Override
            public void accept(BlockPos pos, BlockState state) {
                if (!queued[0] && pos != null) {
                    queued[0] = true;
                    WorldMapBridge.onBlockUpdated(pos);
                }
            }
        });
    }

    @Inject(method = "handleSetSpawn(Lnet/minecraft/network/protocol/game/ClientboundSetDefaultSpawnPositionPacket;)V", at = @At("TAIL"))
    private void nightclient$onSetSpawn(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo callback) {
        if (packet != null) WorldMapBridge.onSpawnUpdated(@@MAP_SPAWN_POS@@);
    }

    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("TAIL"))
    private void nightclient$onLogin(ClientboundLoginPacket packet, CallbackInfo callback) {
        WorldMapBridge.onWorldTransition();
        LiteApiBridge.onLogin();
    }

    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V", at = @At("TAIL"))
    private void nightclient$onRespawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        WorldMapBridge.onWorldTransition();
    }

    @Inject(method = "handleHurtAnimation(Lnet/minecraft/network/protocol/game/ClientboundHurtAnimationPacket;)V", at = @At("HEAD"))
    private void nightclient$onHurtAnim(net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket packet, CallbackInfo callback) {
        if (packet != null) dev.nightclient.platform.WorldFeatureBridge.onHurtAnimationPacket(packet.id());
    }

    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At("HEAD"))
    private void nightclient$onDamageEvent(net.minecraft.network.protocol.game.ClientboundDamageEventPacket packet, CallbackInfo callback) {
        if (packet != null) dev.nightclient.platform.WorldFeatureBridge.onDamageEventPacket(packet);
    }

    @Inject(method = "setActionBarText(Lnet/minecraft/network/protocol/game/ClientboundSetActionBarTextPacket;)V", at = @At("HEAD"))
    private void nightclient$onActionBar(net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket packet, CallbackInfo callback) {
        if (packet != null) {
            net.minecraft.network.chat.Component c = @@ACTION_BAR_PACKET_TEXT@@;
            if (c != null) dev.nightclient.platform.WorldFeatureBridge.onActionBarPacket(c.getString());
        }
    }

    @@MAP_CUSTOM_PAYLOAD_HOOK@@
}
