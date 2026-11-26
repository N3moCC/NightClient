package dev.nightclient.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.config.Settings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OfficialCapesTest {
  @Test
  void catalogMatchesActiveBuildProfile() {
    List<OfficialCapes.Cape> all = OfficialCapes.all();
    if (!OfficialCapes.isEnabled()) {
      assertTrue(all.isEmpty(), "Store/clean profile must not embed official Mojang capes");
      assertEquals("", OfficialCapes.TEXTURE_PREFIX);
      assertNull(OfficialCapes.byId("copper"));
      assertNull(OfficialCapes.byId("minecon_2011"));
      return;
    }
    assertTrue(all.size() >= 45, "Expected full official cape catalog, got " + all.size());
    Set<String> ids = new HashSet<String>();
    for (OfficialCapes.Cape cape : all) {
      assertTrue(ids.add(cape.id), "Duplicate cape id: " + cape.id);
      assertFalse(cape.nameEn.isEmpty());
      assertFalse(cape.nameRu.isEmpty());
      if (cape.textureHash != null) {
        assertEquals(64, cape.textureHash.length(), "Invalid SHA-256 texture hash for " + cape.id);
        assertTrue(cape.textureUrl().startsWith(OfficialCapes.TEXTURE_PREFIX));
      }
    }
    assertNotNull(OfficialCapes.byId("snow_golem"));
    assertNotNull(OfficialCapes.byId("crafter"));
    assertNotNull(OfficialCapes.byId("moonlight_trail"));
    assertEquals(2026, OfficialCapes.byId("builder").year);
    assertEquals(2026, OfficialCapes.byId("aurora").year);
    assertNotNull(OfficialCapes.byId("copper"));
    assertNotNull(OfficialCapes.byId("zombie_horse"));
    assertNotNull(OfficialCapes.byId("15th_anniversary"));
    assertNotNull(OfficialCapes.byId("minecon_2011"));
    assertNotNull(OfficialCapes.byId("christmas_2010"));
  }

  @Test
  void filteringByCategoryAndQueryMatchesRussianAndEnglish() {
    if (!OfficialCapes.isEnabled()) {
      assertTrue(OfficialCapes.filter("events", "copper", "en").isEmpty());
      assertTrue(OfficialCapes.filter("all", "сакура", "ru").isEmpty());
      assertTrue(OfficialCapes.filter("milestones", "2021", "ru").isEmpty());
      return;
    }
    assertFalse(OfficialCapes.filter("events", "copper", "en").isEmpty());
    assertFalse(OfficialCapes.filter("all", "сакура", "ru").isEmpty());
    assertFalse(OfficialCapes.filter("milestones", "2021", "ru").isEmpty());
  }

  @Test
  void settingsValidateCosmeticsDefaultsAndBounds() {
    Settings s = new Settings();
    s.cosmetics.capeMode = "official";
    s.cosmetics.officialCape = "does_not_exist";
    s.cosmetics.skinMode = "bogus";
    s.cosmetics.skinModel = "weird";
    s.cosmetics.skinNickname = "\u0000Alex\n";
    s.validate();
    assertEquals("default", s.cosmetics.capeMode);
    assertEquals("", s.cosmetics.officialCape);
    assertEquals("default", s.cosmetics.skinMode);
    assertEquals("auto", s.cosmetics.skinModel);
    assertEquals("Alex", s.cosmetics.skinNickname);

    if (!OfficialCapes.isEnabled()) {
      s.cosmetics.capeMode = "official";
      s.cosmetics.officialCape = "copper";
      s.validate();
      assertEquals("default", s.cosmetics.capeMode);
      assertEquals("", s.cosmetics.officialCape);
    }
  }
}
