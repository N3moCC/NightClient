package dev.nightclient.platform.mixin;

import java.util.List;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NameTagFeatureRenderer.Storage.class)
public interface OptNameTagStorageAccessor {
  @Accessor("nameTagSubmitsSeethrough")
  List<SubmitNodeStorage.NameTagSubmit> nightclient$seeThrough();

  @Accessor("nameTagSubmitsNormal")
  List<SubmitNodeStorage.NameTagSubmit> nightclient$normal();
}
