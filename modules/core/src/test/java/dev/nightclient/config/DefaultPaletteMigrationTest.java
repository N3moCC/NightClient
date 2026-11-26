package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultPaletteMigrationTest {
  @TempDir Path directory;
  private JsonObject legacy(int bg, int surface, int raised, int text, boolean storedAppearance) {
    Settings settings = new Settings();
    settings.menu.scale = 1.35f;
    settings.saturation.enabled = true;
    settings.moduleBindings.put("saturation", "key.keyboard.g");
    JsonObject json = new Gson().toJsonTree(settings).getAsJsonObject();
    JsonObject theme = json.getAsJsonObject("theme");
    theme.remove("mode");
    theme.addProperty("background", bg);
    theme.addProperty("surface", surface);
    theme.addProperty("raised", raised);
    theme.addProperty("text", text);
    theme.addProperty("backgroundOpacity", .63f);
    json.getAsJsonObject("skin").remove("appearanceRevision");
    if (storedAppearance) {
      JsonObject map = new JsonObject();
      map.add("default", new Gson().fromJson(theme, JsonObject.class));
      json.getAsJsonObject("skin").add("appearance", map);
    }
    return json;
  }
  @Test
  void bothPreviousStockLooksBecomeDarkWithoutLosingModulePreferences() {
    try (ConfigStore store = new ConfigStore(directory)) {
      for (boolean stored : new boolean[] {false, true})
        for (int palette = 0; palette < 2; palette++) {
          JsonObject json = palette == 0
              ? legacy(0xff151618, 0xff1c1e21, 0xff25282c, 0xfff2f3f4, stored)
              : legacy(0xfff4f4f5, 0xffffffff, 0xffeeeef0, 0xff18181b, stored);
          Settings actual = store.decode(json.toString());
          assertEquals("dark", actual.theme.mode);
          assertEquals(Theme.defaults().background, actual.theme.background);
          assertEquals(.63f, actual.theme.backgroundOpacity);
          assertEquals(1.35f, actual.menu.scale);
          assertTrue(actual.saturation.enabled);
          assertEquals("key.keyboard.g", actual.moduleBindings.get("saturation"));
          assertEquals(actual.theme.background, actual.skin.appearance.get("default").background);
        }
    }
  }
  @Test
  void personalLegacyPaletteBecomesEditableCustomAndSurvivesRoundTrip() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings migrated =
          store.decode(legacy(0xff123456, 0xff234567, 0xff345678, 0xffabcdef, true).toString());
      assertEquals("custom", migrated.theme.mode);
      assertEquals(0xff123456, migrated.theme.customBackground);
      assertEquals(0xff234567, migrated.theme.customSurface);
      assertEquals(0xff345678, migrated.theme.customRaised);
      assertEquals(0xffabcdef, migrated.theme.customText);
      Settings restored = store.decode(store.encode(migrated));
      assertEquals("custom", restored.theme.mode);
      assertEquals(0xff123456, restored.theme.background);
      restored.theme.selectMode("light");
      restored.theme.selectMode("custom");
      assertEquals(0xffabcdef, restored.theme.text);
      assertEquals(0xff234567, restored.theme.surface);
    }
  }
  @Test
  void explicitlySelectedModeAndCustomColorsRemainIndependent() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings s = new Settings();
      s.theme.customBackground = 0xff112233;
      s.theme.customText = 0xffaabbcc;
      s.theme.selectMode("light");
      Settings loaded = store.decode(store.encode(s));
      assertEquals("light", loaded.theme.mode);
      assertEquals(0xfff4f4f5, loaded.theme.background);
      loaded.theme.selectMode("custom");
      assertEquals(0xff112233, loaded.theme.background);
      assertEquals(0xffaabbcc, loaded.theme.text);
    }
  }
}
