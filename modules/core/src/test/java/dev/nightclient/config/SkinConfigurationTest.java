package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class SkinConfigurationTest {
  @Test
  void nativeSkinDefaultsAndFilenameValidationUseNcOnly() {
    Settings settings = new Settings();
    assertEquals("skin.nc", settings.skin.file);
    assertFalse(settings.skin.enabled);
    settings.skin.file = "my-theme_02.nc";
    settings.validate();
    assertEquals("my-theme_02.nc", settings.skin.file);
    for (String invalid : new String[] {
             "../outside.nc", "/tmp/skin.nc", "skin.js", "theme.css", "bad name.nc", null}) {
      settings.skin.file = invalid;
      settings.validate();
      assertEquals("skin.nc", settings.skin.file);
    }
  }
  @Test
  void oldFilenamePreferencesMigrateWithoutEnablingSkinsOrLoadingHtml() throws Exception {
    try (ConfigStore store = new ConfigStore(Files.createTempDirectory("night-nc-config"))) {
      Settings settings = store.decode("{\"skin\":{\"file\":\"skin.html\",\"enabled\":false}}");
      assertEquals("skin.nc", settings.skin.file);
      assertFalse(settings.skin.enabled);
      settings = store.decode("{\"skin\":{\"file\":\"skin-neon.html\",\"enabled\":true}}");
      assertEquals("skin-neon.nc", settings.skin.file);
      assertTrue(settings.skin.enabled);
      settings = store.decode("{\"skin\":{\"file\":\"personal.html\"}}");
      assertEquals("personal.nc", settings.skin.file);
      assertEquals("personal.nc", store.decode(store.encode(settings)).skin.file);
    }
  }
  @Test
  void soleDefaultPresetIsPersistedAndRemovedThemesMigrateWithoutLosingFeatures() throws Exception {
    Theme expected = Theme.defaults();
    try (ConfigStore store = new ConfigStore(Files.createTempDirectory("night-retired-preset"))) {
      Settings selected = store.decode("{\"skin\":{\"preset\":\"default\"}}");
      assertEquals("default", selected.skin.preset);
      assertEquals(expected.name, selected.theme.name);
      assertEquals(expected.mode, selected.theme.mode);
      com.google.gson.JsonObject saved =
          new com.google.gson.JsonParser().parse(store.encode(selected)).getAsJsonObject();
      assertEquals("default", saved.getAsJsonObject("skin").get("preset").getAsString());
      for (String retired :
          new String[] {"studio", "rivet", "kinetic", "parallax", "paper", "terminal", "other"}) {
        Settings settings = store.decode("{\"skin\":{\"preset\":\"" + retired
            + ("\",\"file\":\"personal.nc\",\"enabled\":true},\"theme\":{\"accent\":-123},"
               + "\"zoom\":{\"enabled\":true,\"factor\":3.75}}"));
        assertEquals("default", settings.skin.preset);
        assertEquals("default", settings.skin.appliedPreset);
        assertEquals("personal.nc", settings.skin.file);
        assertTrue(settings.skin.enabled);
        assertEquals(expected.accent, settings.theme.accent);
        assertEquals(expected.name, settings.theme.name);
        assertEquals(new Settings.Menu().width, settings.menu.width);
        assertEquals(new Settings.Menu().height, settings.menu.height);
        assertTrue(settings.zoom.enabled);
        assertEquals(3.75f, settings.zoom.factor);
        saved = new com.google.gson.JsonParser().parse(store.encode(settings)).getAsJsonObject();
        assertEquals("default", saved.getAsJsonObject("skin").get("preset").getAsString());
        assertEquals("personal.nc", saved.getAsJsonObject("skin").get("file").getAsString());
      }
    }
  }
  @Test
  void removedAppearanceEntriesAreDroppedAndDefaultCustomAppearanceRoundTrips() throws Exception {
    try (
        ConfigStore store = new ConfigStore(Files.createTempDirectory("night-default-appearance"))) {
      Settings settings = store.decode(
          "{\"skin\":{\"preset\":\"default\",\"appearance\":{\"studio\":{\"accent\":-33},\"rivet\":"
          + "{\"accent\":-44},\"default\":{\"mode\":\"custom\",\"customText\":-123}}}}");
      assertEquals(java.util.Collections.singleton("default"), settings.skin.appearance.keySet());
      assertEquals(-123, settings.theme.text);
      Settings restored = store.decode(store.encode(settings));
      assertEquals("default", restored.skin.preset);
      assertEquals(-123, restored.theme.text);
      assertEquals(java.util.Collections.singleton("default"), restored.skin.appearance.keySet());
    }
  }
}
