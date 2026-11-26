package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.i18n.I18n;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoRenderAndI18nUpgradeTest {
  @TempDir Path folder;

  @Test
  void noRenderDefaultsValidateAndClampFireOffset() {
    Settings s = new Settings();
    assertNotNull(s.noRender);
    assertFalse(s.noRender.enabled);
    assertTrue(s.noRender.fire);
    assertEquals(0.45f, s.noRender.fireOffset, 0.001f);
    assertFalse(s.crosshair.thirdPerson);

    s.noRender = null;
    s.validate();
    assertNotNull(s.noRender);

    s.noRender.fireOffset = -5f;
    s.validate();
    assertEquals(0f, s.noRender.fireOffset, 0.001f);

    s.noRender.fireOffset = 5f;
    s.validate();
    assertEquals(1.5f, s.noRender.fireOffset, 0.001f);

    s.noRender.fireOffset = Float.NaN;
    s.validate();
    assertEquals(0.45f, s.noRender.fireOffset, 0.001f);
  }

  @Test
  void legacyLowFireMigratesIntoNoRenderAndThirdPersonCrosshairPersists() {
    try (ConfigStore store = new ConfigStore(folder)) {
      Settings migrated = store.decode(
          "{\"lowFire\":{\"enabled\":true,\"hidden\":true,\"offsetY\":0.55},"
          + "\"crosshair\":{\"enabled\":true,\"thirdPerson\":true}}");
      assertTrue(migrated.noRender.enabled);
      assertTrue(migrated.noRender.fire);
      assertTrue(migrated.noRender.hideFire);
      assertEquals(0.55f, migrated.noRender.fireOffset, 0.001f);
      assertTrue(migrated.crosshair.thirdPerson);

      String encoded = store.encode(migrated);
      Settings reloaded = store.decode(encoded);
      assertTrue(reloaded.noRender.enabled);
      assertTrue(reloaded.noRender.hideFire);
      assertEquals(0.55f, reloaded.noRender.fireOffset, 0.001f);
      assertTrue(reloaded.crosshair.thirdPerson);
    }
  }

  @Test
  void localizationFilesContainNoBoxDrawingGlyphsAndProvideNoRenderAndWaypointStrings() {
    for (String lang : new String[] {"en", "ru"}) {
      Map<String, String> entries = I18n.catalog(lang);
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        String text = entry.getValue();
        assertFalse(text.indexOf('\u2514') >= 0,
            lang + " key " + entry.getKey() + " contains unsupported U+2514 glyph");
      }
      assertFalse(I18n.text(lang, "field.optimization.entityMaxDistance").contains("?"));
      assertFalse(I18n.text(lang, "module.noRender.label").startsWith("module."));
      assertFalse(I18n.text(lang, "field.crosshair.thirdPerson").startsWith("field."));
      assertFalse(I18n.text(lang, "waypoint.cmd_help").startsWith("waypoint."));
    }
  }
}
