package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import @@GLINT_OWNER@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(@@GLINT_SHORT@@.class)
public abstract class EnchantGlintMixin {
    @Redirect(method="@@GLINT_METHOD@@",at=@At(value="INVOKE",target="@@GLINT_TIME_TARGET@@"))
    private static long nightclient$glintTime(){
        return WorldFeatureBridge.glintMillis();
    }
}
