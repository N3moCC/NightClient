package dev.nightclient.platform.mixin;

import dev.nightclient.platform.WorldFeatureBridge;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundControlMixin {
    @ModifyVariable(method = "@@SOUND_ENGINE_PLAY_HOOK@@", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private SoundInstance nightclient$replaceSound(SoundInstance sound) {
        return WorldFeatureBridge.replacePlayedSound(sound);
    }

    @Inject(
        method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At("RETURN"),
        cancellable = true)
    private void nightclient$scaleVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float orig = cir.getReturnValueF();
        if (orig <= 0.0f) return;
        float adjusted = WorldFeatureBridge.scalePlayedSoundVolume(sound, orig);
        if (adjusted != orig) {
            cir.setReturnValue(Float.valueOf(adjusted));
        }
    }
}
