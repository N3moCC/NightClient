package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MultiPlayerGameMode.class)
public abstract class WorldAttackMixin {
    @Inject(method="attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"))
    private void nightclient$target(Player source,Entity target,CallbackInfo callback){WorldFeatureBridge.attack(source,target);}
    @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;)Z",at=@At("HEAD"))
    private void nightclient$beforeDestroy(net.minecraft.core.BlockPos pos,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir){
        WorldFeatureBridge.onBeforeDestroyBlock(pos);
    }
    @Inject(method="useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"))
    private void nightclient$onUseItem(Player player,net.minecraft.world.InteractionHand hand,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir){
        WorldFeatureBridge.onUseHandItem(player,hand);
    }
    @Inject(method="useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"))
    private void nightclient$onUseItemOn(net.minecraft.client.player.LocalPlayer player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir){
        WorldFeatureBridge.onUseHandItemOn(player,hand,hit);
    }
}
