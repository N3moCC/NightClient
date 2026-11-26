package dev.nightclient.platform.mixin;

import dev.nightclient.platform.GameBridge;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin {
  @Shadow @Final @Mutable public KeyMapping[] keyMappings;

  @Inject(method = "load()V", at = @At("HEAD"))
  private void nightclient$registerKey(CallbackInfo callback) {
    for (KeyMapping key : new KeyMapping[] {GameBridge.menuKey(), GameBridge.zoomKey(),
             GameBridge.freelookKey(), GameBridge.waypointKey(), GameBridge.worldMapKey(),
             GameBridge.mapSettingsKey(), GameBridge.mapEnlargeKey(),
             GameBridge.waypointsMenuKey()}) {
      boolean present = false;
      for (KeyMapping existing : keyMappings)
        if (existing == key) {
          present = true;
          break;
        }
      if (present) continue;
      keyMappings = Arrays.copyOf(keyMappings, keyMappings.length + 1);
      keyMappings[keyMappings.length - 1] = key;
    }
    KeyMapping.resetMapping();
  }
}
