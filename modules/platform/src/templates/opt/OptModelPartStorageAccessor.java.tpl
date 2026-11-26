package dev.nightclient.platform.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPartFeatureRenderer.Storage.class)
public interface OptModelPartStorageAccessor {
  @Accessor("modelPartSubmits")
  Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> nightclient$modelPartSubmits();
}
