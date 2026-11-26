package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class HitColorLivingMixin {
    @Unique private boolean nightclient$prevArmorHurt;

    @Inject(method="render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
    private void nightclient$beginArmorHurt(LivingEntityRenderState state,PoseStack poseStack,MultiBufferSource buffer,int packedLight,CallbackInfo callback){
        nightclient$prevArmorHurt=WorldFeatureBridge.beginArmorHitColor(state!=null&&state.hasRedOverlay);
    }

    @Inject(method="render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void nightclient$endArmorHurt(CallbackInfo callback){
        WorldFeatureBridge.endArmorHitColor(nightclient$prevArmorHurt);
    }
}
