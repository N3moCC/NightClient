package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.skin.NcParser;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeSettingsTest {
  @TempDir Path directory;

  @Test
  void darkIsDefaultAndNamedPalettesHaveContrastingText() {
    Theme t = Theme.defaults();
    assertEquals("dark", t.mode);
    assertEquals(0xff101216, t.background);
    assertEquals(0xfff4f6fb, t.text);
    int darkBackground = t.background, darkText = t.text;
    t.selectMode("light");
    assertEquals(0xfff4f4f5, t.background);
    assertEquals(0xff18181b, t.text);
    t.selectMode("dark");
    assertEquals(darkBackground, t.background);
    assertEquals(darkText, t.text);
    t.selectMode("invalid");
    assertEquals("dark", t.mode);
  }

  @Test
  void customPaletteSurvivesRepeatedPresetSwitchesAndCopiesIndependently() {
    Theme t = Theme.defaults();
    t.customBackground = 0xff102030;
    t.customSurface = 0xff203040;
    t.customRaised = 0xff304050;
    t.customText = 0xfff0a060;
    t.selectMode("custom");
    assertEquals(t.customBackground, t.background);
    assertEquals(t.customSurface, t.surface);
    assertEquals(t.customRaised, t.raised);
    assertEquals(t.customText, t.text);
    Theme copy = t.copy();
    copy.customText = 0xffabcdef;
    copy.applyMode();
    assertNotEquals(t.text, copy.text);
    assertNotSame(t.effects, copy.effects);
    assertNotSame(t.motion, copy.motion);
    for (String mode : new String[] {"dark", "light", "dark", "custom"}) t.selectMode(mode);
    assertEquals(0xff102030, t.background);
    assertEquals(0xff203040, t.surface);
    assertEquals(0xff304050, t.raised);
    assertEquals(0xfff0a060, t.text);
  }

  @Test
  void glassAndCustomPaletteRoundTripWithoutRetiredMaterialsOrEffects() {
    Settings s = new Settings();
    s.theme.mode = "custom";
    s.theme.customBackground = 0xff223344;
    s.theme.customSurface = 0xff334455;
    s.theme.customRaised = 0xff445566;
    s.theme.customText = 0xffeeddcc;
    s.theme.applyMode();
    s.theme.blurRadius = 25;
    s.theme.blurQuality = 3;
    s.theme.fontWeight = "bold";
    s.theme.backgroundOpacity = .65f;
    s.theme.effects.grain = true;
    s.theme.effects.grainAmount = .12f;
    s.menu.density = .82f;
    s.hud.rowHeight = 52;
    s.hud.showHeader = false;
    try (ConfigStore store = new ConfigStore(directory)) {
      String json = store.encode(s);
      Settings decoded = store.decode(json);
      assertEquals("custom", decoded.theme.mode);
      assertEquals(0xff223344, decoded.theme.background);
      assertEquals(0xff334455, decoded.theme.surface);
      assertEquals(0xff445566, decoded.theme.raised);
      assertEquals(0xffeeddcc, decoded.theme.text);
      assertEquals(25, decoded.theme.blurRadius);
      assertEquals(.65f, decoded.theme.backgroundOpacity);
      assertTrue(decoded.theme.effects.grain);
      assertEquals(.12f, decoded.theme.effects.grainAmount);
      assertEquals(52, decoded.hud.rowHeight);
      assertFalse(decoded.hud.showHeader);
      assertSame(decoded.theme, decoded.hudTheme());
      for (String retired : new String[] {
               "material", "variant", "leather", "glow", "shadows", "accentLight", "shadowRadius"})
        assertFalse(
            json.contains("\"" + retired + "\""), retired + " is absent from the persisted model");
    }
  }

  @Test
  void removedLegacyFieldsCannotRestoreSurfaceImplementations() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings s = store.decode("{\"theme\":{\"material\":\"leather\",\"glow\":1,\"shadow\":1},"
                                + "\"saturation\":{\"enabled\":true,\"strength\":1.8}}");
      assertTrue(s.saturation.enabled);
      assertEquals(1.8f, s.saturation.strength);
      assertFalse(store.encode(s).contains("\"material\""));
      assertFalse(store.encode(s).contains("\"leather\""));
    }
  }

  @Test
  void glassRecipePreservesBlurOpacityAndGrainWithoutOtherMaterialBranches() throws Exception {
    Theme saved = Theme.defaults();
    saved.backgroundOpacity = .5f;
    saved.blurMix = .37f;
    saved.effects.backgroundBlur = true;
    saved.blurRadius = 21;
    saved.blurQuality = 5;
    saved.effects.grain = true;
    saved.effects.grainAmount = .1f;
    dev.nightclient.skin.SkinDocument document =
        NcParser.parse("menu { panel(id=glass) { width=100px; height=50px } }");
    Theme rendered = document.theme("glass", saved);
    assertEquals(saved.backgroundOpacity, rendered.backgroundOpacity);
    assertEquals(21, rendered.blurRadius);
    assertEquals(5, rendered.blurQuality);
    assertTrue(rendered.effects.backgroundBlur);
    assertTrue(rendered.effects.grain);
    assertEquals(.1f, rendered.effects.grainAmount);
    assertEquals(.37f, rendered.blurMix);
  }

  @Test
  void unsafeGlassValuesAreBoundedBeforeTheyReachGpu() {
    Settings s = new Settings();
    s.theme.blurRadius = Float.NaN;
    s.theme.blurQuality = 999;
    s.theme.fontTracking = Float.NEGATIVE_INFINITY;
    s.theme.effects.grainAmount = Float.NaN;
    s.hud.rowHeight = -9;
    s.hud.iconScale = Float.NaN;
    s.validate();
    assertEquals(Theme.defaults().blurRadius, s.theme.blurRadius);
    assertEquals(6, s.theme.blurQuality);
    assertEquals(Theme.defaults().fontTracking, s.theme.fontTracking);
    assertEquals(Theme.defaults().effects.grainAmount, s.theme.effects.grainAmount);
    assertEquals(28, s.hud.rowHeight);
    assertEquals(1, s.hud.iconScale);
  }
}
