package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class AtmosphereSettingsTest {
  @Test
  void defaultsAreDisabledAndValid() {
    Settings s = new Settings();
    assertFalse(s.atmosphere.enabled);
    assertEquals("dawn", s.atmosphere.mode);
    assertEquals("medium", s.atmosphere.quality);
    assertTrue(s.atmosphere.mysticWorld.alwaysNight);

    s.validate();
    assertFalse(s.atmosphere.enabled);
    assertEquals("dawn", s.atmosphere.mode);
  }

  @Test
  void validationClampsInvalidAndNaNValues() {
    Settings s = new Settings();
    s.atmosphere.mode = "invalid_mode";
    s.atmosphere.quality = "ultra";
    s.atmosphere.density = Float.NaN;
    s.atmosphere.scatterHeight = -999f;
    s.atmosphere.amount = 999f;
    s.atmosphere.ground = -50f;
    s.atmosphere.mysticLife.wispCount = 99;
    s.atmosphere.snowCover = null;
    s.atmosphere.mysticSky = null;

    s.validate();
    assertEquals("dawn", s.atmosphere.mode);
    assertEquals("medium", s.atmosphere.quality);
    assertFalse(Float.isNaN(s.atmosphere.density));
    assertTrue(s.atmosphere.density >= 0.05f && s.atmosphere.density <= 0.80f);
    assertNotNull(s.atmosphere.snowCover);
    assertNotNull(s.atmosphere.mysticSky);
    assertTrue(s.atmosphere.mysticLife.wispCount <= 4);

    s.atmosphere.mode = "japan";
    s.validate();
    assertEquals("sakura", s.atmosphere.mode);

    s.atmosphere.mode = "halloween";
    s.validate();
    assertEquals("mystic", s.atmosphere.mode);
  }

  @Test
  void worldTimeDefaultsAndValidation() {
    Settings s = new Settings();
    assertNotNull(s.worldTime);
    assertFalse(s.worldTime.enabled);
    assertEquals("day", s.worldTime.time);
    assertTrue(s.worldTime.noRain);

    s.worldTime.time = "invalid";
    s.validate();
    assertEquals("day", s.worldTime.time);

    s.worldTime.time = "night";
    s.validate();
    assertEquals("night", s.worldTime.time);

    s.worldTime.time = "off";
    s.validate();
    assertEquals("off", s.worldTime.time);

    s.worldTime = null;
    s.validate();
    assertNotNull(s.worldTime);
    assertEquals("day", s.worldTime.time);
    assertTrue(s.worldTime.noRain);
  }

  @Test
  void customSkyAndNoRenderWeatherValidateCleanly() {
    Settings s = new Settings();
    assertNotNull(s.customSky);
    assertFalse(s.customSky.enabled);
    assertEquals("galaxy", s.customSky.style);
    assertEquals("server", s.customSky.timeMode);
    assertFalse(s.noRender.weather);

    s.customSky.style = "bogus";
    s.customSky.timeMode = "bogus";
    s.customSky.customTime = 99999;
    s.customSky.brightness = 500f;
    s.customSky.celestialHeight = -20f;
    s.customSky.stars = Float.NaN;
    s.validate();

    assertEquals("galaxy", s.customSky.style);
    assertEquals("server", s.customSky.timeMode);
    assertEquals(24000, s.customSky.customTime);
    assertEquals(160f, s.customSky.brightness, 0.01f);
    assertEquals(5f, s.customSky.celestialHeight, 0.01f);
    assertEquals(75f, s.customSky.stars, 0.01f);
  }
}
