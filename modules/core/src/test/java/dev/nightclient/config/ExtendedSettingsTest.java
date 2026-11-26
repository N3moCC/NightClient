package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtendedSettingsTest {
  @TempDir Path directory;
  @Test
  void newModulesSurviveProfilesWithoutLosingIndependentHandTransforms() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings s = new Settings();
      s.armor.enabled = s.potions.enabled = s.food.enabled = s.target.enabled = true;
      s.tweaksy.commandFix.enabled = s.itemScroller.enabled = s.hitColor.enabled =
          s.hitbox.enabled = true;
      s.noRender.enabled = s.noRender.waterOverlay = s.noRender.waterFog = s.noRender.lavaFog =
          s.itemPhysics.enabled = s.tweaksy.fullBright.enabled = s.viewmodel.enabled = true;
      s.aspectRatio.enabled = s.swing.enabled = s.freelook.enabled = true;
      s.viewmodel.main.rotateY = 45;
      s.viewmodel.off.scaleZ = 2;
      s.target.style = "zakohp";
      s.theme.motion.easing = "quint-out";
      s.theme.motion.stagger = .08f;
      Settings decoded = store.decode(store.encode(s));
      assertTrue(decoded.armor.enabled && decoded.potions.enabled && decoded.food.enabled
          && decoded.target.enabled);
      assertTrue(decoded.tweaksy.commandFix.enabled && decoded.itemScroller.enabled
          && decoded.hitColor.enabled && decoded.hitbox.enabled);
      assertTrue(decoded.noRender.enabled && decoded.noRender.waterOverlay
          && decoded.noRender.waterFog && decoded.noRender.lavaFog
          && decoded.itemPhysics.enabled && decoded.tweaksy.fullBright.enabled
          && decoded.viewmodel.enabled);
      assertTrue(decoded.aspectRatio.enabled && decoded.swing.enabled && decoded.freelook.enabled);
      assertEquals(45, decoded.viewmodel.main.rotateY);
      assertEquals(2, decoded.viewmodel.off.scaleZ);
      assertEquals("zakohp", decoded.target.style);
      assertEquals("quint-out", decoded.theme.motion.easing);
    }
  }
  @Test
  void corruptedAndOldConfigurationKeepsFiniteDefaults() {
    Settings s = new Settings();
    s.armor = null;
    s.target = null;
    s.potions = null;
    s.food = null;
    s.viewmodel.main.scaleX = Float.NaN;
    s.viewmodel.off.rotateZ = Float.POSITIVE_INFINITY;
    s.hitbox.lineWidth = 100;
    s.tweaksy.fullBright.strength = -10;
    s.theme.motion = null;
    s.validate();
    assertNotNull(s.armor);
    assertNotNull(s.target);
    assertNotNull(s.potions);
    assertNotNull(s.food);
    assertEquals(1, s.viewmodel.main.scaleX);
    assertEquals(0, s.viewmodel.off.rotateZ);
    assertEquals(8, s.hitbox.lineWidth);
    assertEquals(0, s.tweaksy.fullBright.strength);
    assertNotNull(s.theme.motion);
  }
  @Test
  void optimizationModuleRoundTripsAndClampsWorkloadLimits() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings s = new Settings();
      assertFalse(s.optimization.enabled);
      assertTrue(s.optimization.hudCache);
      s.optimization.enabled = true;
      s.optimization.particleDistance = 64;
      s.optimization.particleLimit = 2500;
      s.optimization.entityDistance = 160;
      s.optimization.nametagDistance = 72;
      s.optimization.blockEntityDistance = 180;
      Settings decoded = store.decode(store.encode(s));
      assertTrue(decoded.optimization.enabled);
      assertEquals(64, decoded.optimization.particleDistance);
      assertEquals(2500, decoded.optimization.particleLimit);
      assertEquals(160, decoded.optimization.entityDistance);
      assertEquals(72, decoded.optimization.nametagDistance);
      assertEquals(180, decoded.optimization.blockEntityDistance);
      decoded.optimization = null;
      decoded.validate();
      assertNotNull(decoded.optimization);
      assertEquals(48, decoded.optimization.particleDistance);
      assertEquals(4000, decoded.optimization.particleLimit);
      assertEquals(96, decoded.optimization.entityDistance);
      decoded.optimization.particleDistance = 1;
      decoded.optimization.particleLimit = 1;
      decoded.optimization.entityDistance = 1;
      decoded.optimization.nametagDistance = 1;
      decoded.optimization.blockEntityDistance = 1;
      decoded.validate();
      assertEquals(8, decoded.optimization.particleDistance);
      assertEquals(100, decoded.optimization.particleLimit);
      assertEquals(16, decoded.optimization.entityDistance);
      assertEquals(8, decoded.optimization.nametagDistance);
      assertEquals(16, decoded.optimization.blockEntityDistance);
    }
  }
  @Test
  void tweaksyDefaultsDisableAllAndNoRenderDefaultsLeaveModuleDisabled() {
    Settings s = new Settings();
    assertTrue(s.tweaksy.enabled);
    assertFalse(s.tweaksy.mainLeftFix.enabled);
    assertFalse(s.tweaksy.commandFix.enabled);
    assertFalse(s.tweaksy.cursorFix.enabled);
    assertFalse(s.tweaksy.fullBright.enabled);
    assertFalse(s.noRender.enabled);
    assertTrue(s.noRender.hurtCam);
  }
  @Test
  void legacyStandaloneTweaksAndAtmosphereMigrateIntoCanonicalFields() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings legacy = store.decode(
          "{\"commandfix\":{\"enabled\":true},\"noFluid\":{\"enabled\":true,"
          + "\"waterOverlay\":false,\"waterFog\":false,\"lavaFog\":true},"
          + "\"fullBright\":{\"enabled\":true,\"strength\":0.4},"
          + "\"atmosphere\":{\"mode\":\"japan\",\"japanSky\":{\"clouds\":62},"
          + "\"halloweenWorld\":{\"alwaysNight\":false},\"halloweenLife\":{\"wispCount\":3}}}");
      assertTrue(legacy.tweaksy.commandFix.enabled);
      assertTrue(legacy.noRender.enabled);
      assertFalse(legacy.noRender.waterOverlay);
      assertFalse(legacy.noRender.waterFog);
      assertTrue(legacy.noRender.lavaFog);
      assertTrue(legacy.tweaksy.fullBright.enabled);
      assertEquals(0.4f, legacy.tweaksy.fullBright.strength);
      assertEquals("sakura", legacy.atmosphere.mode);
      assertEquals(62f, legacy.atmosphere.sakuraSky.clouds);
      assertFalse(legacy.atmosphere.mysticWorld.alwaysNight);
      assertEquals(3, legacy.atmosphere.mysticLife.wispCount);

      Settings intermediate = store.decode(
          "{\"tweaksy\":{\"enabled\":true,\"noHurtCam\":{\"enabled\":true},"
          + "\"noFluid\":{\"enabled\":true,\"waterOverlay\":true,\"waterFog\":false,\"lavaFog\":true}},"
          + "\"cursorFix\":{\"enabled\":true}}");
      assertTrue(intermediate.tweaksy.cursorFix.enabled);
      assertTrue(intermediate.noRender.enabled);
      assertTrue(intermediate.noRender.hurtCam);
      assertTrue(intermediate.noRender.waterOverlay);
      assertFalse(intermediate.noRender.waterFog);
      assertTrue(intermediate.noRender.lavaFog);
    }
  }
  @Test
  void legacyWallPenetratingHitboxOptionIsIgnoredAndNotPersisted() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings decoded = store.decode("{\"hitbox\":{\"enabled\":true,\"throughWalls\":true}}");
      assertTrue(decoded.hitbox.enabled);
      assertFalse(java.util.Arrays.stream(Settings.Hitbox.class.getFields())
              .anyMatch(field -> field.getName().equals("throughWalls")));
      com.google.gson.JsonObject hitbox = new com.google.gson.JsonParser()
                                              .parse(store.encode(decoded))
                                              .getAsJsonObject()
                                              .getAsJsonObject("hitbox");
      assertFalse(hitbox.has("throughWalls"));
    }
  }
  @Test
  void lowFireSurvivesProfilesAndBoundsInvalidOffsets() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings s = new Settings();
      s.lowFire.enabled = true;
      s.lowFire.hidden = true;
      s.lowFire.offsetY = .72f;
      Settings decoded = store.decode(store.encode(s));
      assertTrue(decoded.lowFire.enabled && decoded.lowFire.hidden);
      assertEquals(.72f, decoded.lowFire.offsetY);
      decoded.lowFire.offsetY = Float.NaN;
      decoded.validate();
      assertEquals(.45f, decoded.lowFire.offsetY);
      decoded.lowFire.offsetY = 100;
      decoded.validate();
      assertEquals(1.5f, decoded.lowFire.offsetY);
    }
  }
  @Test
  void oldSaturationCardsKeepOnlyTheirEnabledState() {
    try (ConfigStore store = new ConfigStore(directory)) {
      String old =
          "{\"food\":{\"enabled\":true,\"hunger\":true,\"numeric\":true,\"anchorX\":0.5,"
          + "\"anchorY\":1,\"offsetX\":0,\"offsetY\":-72,\"width\":160,\"height\":44,\"scale\":1}}";
      for (String json : new String[] {
               old, old.replace("-72", "-70"), old.replace("\"scale\":1", "\"scale\":3")}) {
        Settings migrated = store.decode(json);
        assertTrue(migrated.food.enabled);
        com.google.gson.JsonObject food = new com.google.gson.JsonParser()
                                              .parse(store.encode(migrated))
                                              .getAsJsonObject()
                                              .getAsJsonObject("food");
        assertEquals(1, food.size());
        assertTrue(food.get("enabled").getAsBoolean());
      }
    }
  }
  @Test
  void oldFoodRowGeometryAndExtraRowsAreNotPersisted() {
    try (ConfigStore store = new ConfigStore(directory)) {
      String old = "{\"food\":{\"enabled\":false,\"anchorX\":0.5,\"anchorY\":1,\"offsetX\":51.5,"
                   + "\"offsetY\":-40,\"width\":82,\"height\":10,\"scale\":1,\"hunger\":true}}";
      Settings migrated = store.decode(old);
      assertFalse(migrated.food.enabled);
      com.google.gson.JsonObject food = new com.google.gson.JsonParser()
                                            .parse(store.encode(migrated))
                                            .getAsJsonObject()
                                            .getAsJsonObject("food");
      assertEquals(1, food.size());
      assertFalse(food.get("enabled").getAsBoolean());
      assertEquals(1, Settings.Food.class.getFields().length);
      assertEquals("enabled", Settings.Food.class.getFields()[0].getName());
    }
  }
  @Test
  void oldItemScrollerBecomesShiftDragWhileKeepingItsEnabledState() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings s = store.decode(
          "{\"itemScroller\":{\"enabled\":true,\"wheel\":true,\"dragMatching\":false}}");
      assertTrue(s.itemScroller.enabled && s.itemScroller.dragMatching);
      assertFalse(s.itemScroller.wheel);
      assertEquals("shift-drag", s.itemScroller.gesture);
      Settings roundTrip = store.decode(store.encode(s));
      assertTrue(roundTrip.itemScroller.enabled);
      assertFalse(roundTrip.itemScroller.wheel);
    }
  }
  @Test
  void itemScrollerSettingsRoundTripPreserveNewDefaultsAndClampBounds() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings existing = store.decode(
          "{\"itemScroller\":{\"enabled\":true,\"gesture\":\"shift-drag\",\"dragMatching\":false,\"wheel\":true}}");
      assertTrue(existing.itemScroller.enabled);
      assertFalse(existing.itemScroller.dragMatching);
      assertTrue(existing.itemScroller.wheel);
      assertFalse(existing.itemScroller.dragOnlyMatching);
      assertEquals(0, existing.itemScroller.dragCooldownMs);
      assertTrue(existing.itemScroller.moveMatching);
      assertTrue(existing.itemScroller.dropMatching);
      assertFalse(existing.itemScroller.shiftPlace);
      assertEquals("stack", existing.itemScroller.wheelMode);
      assertEquals("both", existing.itemScroller.wheelDirection);
      assertTrue(existing.itemScroller.matchNbt);

      Settings custom = new Settings();
      custom.itemScroller.enabled = true;
      custom.itemScroller.dragMatching = false;
      custom.itemScroller.dragOnlyMatching = true;
      custom.itemScroller.dragCooldownMs = 45;
      custom.itemScroller.moveMatching = false;
      custom.itemScroller.dropMatching = false;
      custom.itemScroller.shiftPlace = true;
      custom.itemScroller.wheel = true;
      custom.itemScroller.wheelMode = "matching";
      custom.itemScroller.wheelDirection = "up";
      custom.itemScroller.cooldownMs = 150;
      custom.itemScroller.matchNbt = false;

      Settings decoded = store.decode(store.encode(custom));
      assertTrue(decoded.itemScroller.enabled);
      assertFalse(decoded.itemScroller.dragMatching);
      assertTrue(decoded.itemScroller.dragOnlyMatching);
      assertEquals(45, decoded.itemScroller.dragCooldownMs);
      assertFalse(decoded.itemScroller.moveMatching);
      assertFalse(decoded.itemScroller.dropMatching);
      assertTrue(decoded.itemScroller.shiftPlace);
      assertTrue(decoded.itemScroller.wheel);
      assertEquals("matching", decoded.itemScroller.wheelMode);
      assertEquals("up", decoded.itemScroller.wheelDirection);
      assertEquals(150, decoded.itemScroller.cooldownMs);
      assertFalse(decoded.itemScroller.matchNbt);

      decoded.itemScroller.dragCooldownMs = -50;
      decoded.itemScroller.cooldownMs = 5000;
      decoded.itemScroller.wheelMode = "invalid";
      decoded.itemScroller.wheelDirection = "sideways";
      decoded.validate();
      assertEquals(0, decoded.itemScroller.dragCooldownMs);
      assertEquals(1000, decoded.itemScroller.cooldownMs);
      assertEquals("stack", decoded.itemScroller.wheelMode);
      assertEquals("both", decoded.itemScroller.wheelDirection);

      decoded.itemScroller.dragCooldownMs = 999;
      decoded.itemScroller.cooldownMs = 5;
      decoded.validate();
      assertEquals(250, decoded.itemScroller.dragCooldownMs);
      assertEquals(20, decoded.itemScroller.cooldownMs);
    }
  }
  @Test
  void hitColorArmorDefaultsFalseAndRoundTripsThroughProfiles() {
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings defaults = new Settings();
      assertFalse(defaults.hitColor.armor);

      Settings legacy = store.decode("{\"hitColor\":{\"enabled\":true,\"mode\":\"entity\"}}");
      assertTrue(legacy.hitColor.enabled);
      assertFalse(legacy.hitColor.armor);

      defaults.hitColor.enabled = true;
      defaults.hitColor.armor = true;
      defaults.hitColor.mode = "both";
      Settings decoded = store.decode(store.encode(defaults));
      assertTrue(decoded.hitColor.enabled);
      assertTrue(decoded.hitColor.armor);
      assertEquals("both", decoded.hitColor.mode);
    }
  }
}
