package dev.nightclient.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

final class UniversalRuntime {
  private static AdapterIndex.Adapter selected;
  private static boolean mixinsSelected;

  private UniversalRuntime() {}

  static synchronized AdapterIndex.Adapter selected() {
    if (selected == null) {
      ModContainer minecraft =
          FabricLoader.getInstance()
              .getModContainer("minecraft")
              .orElseThrow(
                  ()
                      -> new IllegalStateException("NightClient universal: Minecraft metadata is"
                          + " missing"));
      String version = minecraft.getMetadata().getVersion().getFriendlyString();
      try (InputStream stream =
               UniversalRuntime.class.getClassLoader().getResourceAsStream(AdapterIndex.RESOURCE)) {
        selected = AdapterIndex.read(stream).select(
            version, AdapterIndex.javaFeature(System.getProperty("java.specification.version")));
      } catch (IOException exception) {
        throw new IllegalStateException(
            "NightClient universal: Cannot read adapter index", exception);
      }
    }
    return selected;
  }

  static synchronized void markMixinsSelected() {
    mixinsSelected = true;
  }

  static synchronized AdapterIndex.Adapter clientAdapter() {
    if (!mixinsSelected)
      throw new IllegalStateException(
          "NightClient universal: The version-specific Mixin configuration was not"
          + " initialized");
    return selected();
  }
}
