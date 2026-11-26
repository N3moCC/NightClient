package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.FeatureSupport;
import dev.nightclient.config.Settings;
import dev.nightclient.ui.skin.UiCatalog;
import org.junit.jupiter.api.Test;

public class FeatureSupportUiTest {
  @Test
  public void optimizationControlsAreGatedByMinecraftVersion() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    try {
      FeatureSupport.configureForGameVersion("1.20.1");
      assertNull(catalog.control("optimization.nativeGpuInstancing"));
      assertNull(catalog.control("optimization.undergroundCaveCulling"));
      assertNull(catalog.control("optimization.retainedTerrainCache"));
      assertNull(catalog.control("optimization.fastModelRendering"));
      assertNull(catalog.control("optimization.staticBlockEntityBatching"));
      assertNull(catalog.control("optimization.staticEntityBatching"));
      assertNull(catalog.control("optimization.hologramTextCache"));
      assertNull(catalog.control("optimization.vanillaHudCache"));
      assertNull(catalog.control("optimization.scoreboardCache"));
      assertNotNull(catalog.control("optimization.nativePboUploads"));
      assertNotNull(catalog.control("optimization.leafCulling"));
      assertNotNull(catalog.control("optimization.nativePacketCodec"));

      FeatureSupport.configureForGameVersion("1.21.4");
      assertNotNull(catalog.control("optimization.fastModelRendering"));
      assertNotNull(catalog.control("optimization.scoreboardCache"));
      assertNotNull(catalog.control("optimization.nativePboUploads"));
      assertNull(catalog.control("optimization.vanillaHudCache"));
      assertNull(catalog.control("optimization.nativeGpuInstancing"));

      FeatureSupport.configureForGameVersion("1.21.6");
      assertNotNull(catalog.control("optimization.vanillaHudCache"));
      assertNull(catalog.control("optimization.nativePboUploads"));
      assertNull(catalog.control("optimization.hologramTextCache"));

      FeatureSupport.configureForGameVersion("1.21.11");
      assertNotNull(catalog.control("optimization.nativeGpuInstancing"));
      assertNotNull(catalog.control("optimization.nativePboUploads"));
      assertNotNull(catalog.control("optimization.undergroundCaveCulling"));
      assertNotNull(catalog.control("optimization.retainedTerrainCache"));
      assertNotNull(catalog.control("optimization.staticBlockEntityBatching"));
      assertNotNull(catalog.control("optimization.staticEntityBatching"));
      assertNotNull(catalog.control("optimization.hologramTextCache"));
      assertNotNull(catalog.control("optimization.vanillaHudCache"));
      assertNotNull(catalog.control("optimization.scoreboardCache"));

      FeatureSupport.configureForGameVersion("26.2");
      assertNull(catalog.control("optimization.nativeGpuInstancing"));
      assertNull(catalog.control("optimization.nativePboUploads"));
      assertNotNull(catalog.control("optimization.undergroundCaveCulling"));
      assertNotNull(catalog.control("optimization.retainedTerrainCache"));
      assertNotNull(catalog.control("optimization.staticBlockEntityBatching"));
      assertNotNull(catalog.control("optimization.staticEntityBatching"));

      FeatureSupport.configureForGameVersion("26.3");
      assertNull(catalog.control("optimization.nativeGpuInstancing"));
      assertNull(catalog.control("optimization.nativePboUploads"));
      assertNull(catalog.control("optimization.undergroundCaveCulling"));
      assertNull(catalog.control("optimization.retainedTerrainCache"));
      assertNotNull(catalog.control("optimization.staticBlockEntityBatching"));
      assertNotNull(catalog.control("optimization.staticEntityBatching"));
      assertNotNull(catalog.control("optimization.hologramTextCache"));
      assertNotNull(catalog.control("optimization.vanillaHudCache"));
      assertNotNull(catalog.control("optimization.scoreboardCache"));
    } finally {
      FeatureSupport.reset();
    }
  }
}
