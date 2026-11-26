package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.platform.WavyCapeBridge;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@@WAVY_CAPE_MIXIN_IMPORTS@@

@Mixin(CapeLayer.class)
public abstract class WavyCapeMixin extends RenderLayer<@@WAVY_CAPE_RENDER_LAYER_GENERICS@@> {
    protected WavyCapeMixin() {
        super(null);
    }

@@WAVY_CAPE_MIXIN_BODY@@
}
