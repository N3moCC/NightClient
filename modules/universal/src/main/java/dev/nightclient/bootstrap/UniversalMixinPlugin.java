package dev.nightclient.bootstrap;

import java.util.List;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

public final class UniversalMixinPlugin implements IMixinConfigPlugin {
  private AdapterIndex.Adapter adapter;

  @Override
  public void onLoad(String mixinPackage) {
    if (!AdapterIndex.MIXIN_PACKAGE.equals(mixinPackage)) {
      throw new IllegalStateException(
          "NightClient universal: Incorrect reserved Mixin package " + mixinPackage);
    }
    adapter = UniversalRuntime.selected();
    CompatibilityLevel required = CompatibilityLevel.valueOf(adapter.compatibilityLevel);
    if (MixinEnvironment.getCompatibilityLevel().isLessThan(required)) {
      MixinEnvironment.setCompatibilityLevel(required);
    }
    UniversalRuntime.markMixinsSelected();
    System.out.println("[NightClient] Universal adapter " + adapter.id + " selected for Minecraft "
        + adapter.minecraft);
  }

  @Override
  public List<String> getMixins() {
    if (adapter == null)
      throw new IllegalStateException(
          "NightClient universal: Mixin plugin was queried before selection");
    return adapter.mixins;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (adapter == null)
      throw new IllegalStateException(
          "NightClient universal: Mixin plugin was queried before selection");
    return mixinClassName.startsWith(adapter.mixinPackage + ".")
        && adapter.mixins.contains(
            mixinClassName.substring(AdapterIndex.MIXIN_PACKAGE.length() + 1));
  }
}
