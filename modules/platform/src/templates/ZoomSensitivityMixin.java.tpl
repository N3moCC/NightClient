package dev.nightclient.platform.mixin;
import dev.nightclient.platform.FeatureBridge;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(MouseHandler.class)
public abstract class ZoomSensitivityMixin {
    @@SENSITIVITY_HANDLER@@
}
