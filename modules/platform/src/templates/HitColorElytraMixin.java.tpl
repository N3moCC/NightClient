package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
public abstract class HitColorElytraMixin {
    @Unique private boolean nightclient$hurt;

    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"))
    private void nightclient$beginHurt(PoseStack poseStack,MultiBufferSource buffer,int packedLight,LivingEntity entity,float limbSwing,float limbSwingAmount,float partialTick,float ageInTicks,float netHeadYaw,float headPitch,CallbackInfo callback){
        nightclient$hurt=WorldFeatureBridge.policy().hitColorArmor&&entity!=null&&(entity.hurtTime>0||entity.deathTime>0);
    }

    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("RETURN"))
    private void nightclient$endHurt(CallbackInfo callback){
        nightclient$hurt=false;
    }

    @Redirect(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType nightclient$elytraRenderType(ResourceLocation texture){
        return nightclient$hurt?RenderType.entityCutoutNoCullZOffset(texture):RenderType.armorCutoutNoCull(texture);
    }

    @ModifyArg(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At(value="INVOKE",target="@@HIT_COLOR_ELYTRA_RENDER_TARGET@@"),index=3)
    private int nightclient$elytraOverlay(int overlay){
        return nightclient$hurt?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }
}
