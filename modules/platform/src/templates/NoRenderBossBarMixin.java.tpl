package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BossHealthOverlay.class)
public abstract class NoRenderBossBarMixin {
    @Inject(method="@@BOSS_BAR_HOOK@@",at=@At("HEAD"),cancellable=true)
    private void nightclient$bossBar(CallbackInfo callback){if(WorldFeatureBridge.policy().noBossBar)callback.cancel();}
    @Inject(method="shouldDarkenScreen()Z",at=@At("HEAD"),cancellable=true)
    private void nightclient$darken(CallbackInfoReturnable<Boolean> callback){if(WorldFeatureBridge.policy().noBossBar)callback.setReturnValue(Boolean.FALSE);}
    @Inject(method="shouldCreateWorldFog()Z",at=@At("HEAD"),cancellable=true)
    private void nightclient$fog(CallbackInfoReturnable<Boolean> callback){if(WorldFeatureBridge.policy().noBossBar)callback.setReturnValue(Boolean.FALSE);}
    @Inject(method="update(Lnet/minecraft/network/protocol/game/ClientboundBossEventPacket;)V",at=@At("RETURN"))
    private void nightclient$onBossPacket(net.minecraft.network.protocol.game.ClientboundBossEventPacket packet,CallbackInfo callback){
        WorldFeatureBridge.onBossBarPacketUpdated();
    }
}
