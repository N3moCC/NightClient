package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.PotionView;
import dev.nightclient.config.Settings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectAlertLogicTest {
  private static PotionView potion(String id, String name, int amp, float seconds) {
    return new PotionView(id, name, amp, 0xFFE11D48, seconds, true);
  }

  @Test
  void warnsOnceAtThresholdResetsOnRefreshAndNotifiesOnNaturalExpiry() {
    Settings.EffectAlert cfg = new Settings.EffectAlert();
    cfg.enabled = true;
    cfg.mode = "both";
    cfg.warnSeconds = 5;
    cfg.notifyOnExpire = true;
    cfg.strength = true;
    cfg.nightVision = false;

    EffectAlertLogic logic = new EffectAlertLogic();

    logic.update(cfg,
        Arrays.asList(
            potion("minecraft:strength", "Сила", 1, 12.0f),
            potion("minecraft:night_vision", "Ночное зрение", 0, 3.0f)),
        1000L);
    assertTrue(logic.drainSounds().isEmpty());
    assertTrue(logic.hudAlerts().isEmpty(), "Unselected night_vision must not trigger HUD alert");

    logic.update(cfg, Collections.singletonList(potion("minecraft:strength", "Сила", 1, 4.8f)),
        2000L);
    List<EffectAlertLogic.SoundCue> warnSounds = logic.drainSounds();
    assertEquals(1, warnSounds.size());
    assertEquals(1, logic.hudAlerts().size());
    assertFalse(logic.hudAlerts().get(0).expired);

    logic.update(cfg, Collections.singletonList(potion("minecraft:strength", "Сила", 1, 4.2f)),
        2600L);
    assertTrue(logic.drainSounds().isEmpty());
    assertEquals(1, logic.hudAlerts().size());

    logic.update(cfg, Collections.singletonList(potion("minecraft:strength", "Сила", 1, 90.0f)),
        3000L);
    assertTrue(logic.drainSounds().isEmpty());
    assertTrue(logic.hudAlerts().isEmpty());

    logic.update(cfg, Collections.singletonList(potion("minecraft:strength", "Сила", 1, 1.0f)),
        88_000L);
    assertEquals(1, logic.drainSounds().size());

    logic.update(cfg, Collections.<PotionView>emptyList(), 89_000L);
    List<EffectAlertLogic.SoundCue> expSounds = logic.drainSounds();
    assertEquals(1, expSounds.size());
    assertTrue(expSounds.get(0).pitch < cfg.soundPitch);
    assertEquals(1, logic.hudAlerts().size());
    assertTrue(logic.hudAlerts().get(0).expired);

    logic.update(cfg, Collections.<PotionView>emptyList(), 92_000L);
    assertTrue(logic.hudAlerts().isEmpty());
  }

  @Test
  void respectsSoundOnlyAndHudOnlyModes() {
    Settings.EffectAlert cfg = new Settings.EffectAlert();
    cfg.enabled = true;
    cfg.warnSeconds = 5;
    cfg.speed = true;

    EffectAlertLogic soundOnly = new EffectAlertLogic();
    cfg.mode = "sound";
    soundOnly.update(
        cfg, Collections.singletonList(potion("minecraft:speed", "Скорость", 1, 3.5f)), 1000L);
    assertEquals(1, soundOnly.drainSounds().size());
    assertTrue(soundOnly.hudAlerts().isEmpty());

    EffectAlertLogic hudOnly = new EffectAlertLogic();
    cfg.mode = "hud";
    hudOnly.update(
        cfg, Collections.singletonList(potion("minecraft:speed", "Скорость", 1, 3.5f)), 1000L);
    assertTrue(hudOnly.drainSounds().isEmpty());
    assertEquals(1, hudOnly.hudAlerts().size());
  }
}
