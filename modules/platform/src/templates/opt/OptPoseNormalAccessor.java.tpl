package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PoseStack.Pose.class)
public interface OptPoseNormalAccessor {
  @Accessor("trustedNormals")
  boolean nightclient$hasTrustedNormals();
}
