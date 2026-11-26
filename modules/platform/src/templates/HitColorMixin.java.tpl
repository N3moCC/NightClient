package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.@@OVERLAY_CALLBACK_IMPORT@@;
@Mixin(OverlayTexture.class)
public abstract class HitColorMixin {
    @Shadow @Final private DynamicTexture texture;
    @Unique private int nightclient$lastColor=@@HIT_COLOR_INIT@@;
    @Inject(method="@@OVERLAY_HOOK@@",at=@At("HEAD"))
    private void nightclient$color(@@OVERLAY_CALLBACK@@ callback){
        WorldFeatureBridge.Policy policy=WorldFeatureBridge.policy();
        int argb=policy.hitColor?policy.hurtColor:0xb2ff0000;

        int pixel=((255-((argb>>>24)&255))<<24)|(argb&0xffffff);
        if(!policy.hitColor)pixel=0xb2ff0000;
        @@ABGR_COLOR@@
        if(pixel==nightclient$lastColor)return;nightclient$lastColor=pixel;
        NativeImage image=texture.getPixels();if(image==null)return;
        for(int y=0;y<8;y++)for(int x=0;x<16;x++)image.@@SET_PIXEL@@(x,y,pixel);
        @@OVERLAY_UPLOAD@@
    }
}
