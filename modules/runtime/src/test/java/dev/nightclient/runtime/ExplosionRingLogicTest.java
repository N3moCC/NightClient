package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

public final class ExplosionRingLogicTest {
  @Test
  void infersHolyWorldAndAnarchyCustomTntSpecs() {
    ExplosionRingLogic.TntSpec c4 = ExplosionRingLogic.inferFromText("§c§lC4", "§7Рейд взрывчатка");
    assertTrue(c4.custom);
    assertEquals(10.0f, c4.radius, 0.01f);
    assertEquals("C4", c4.label);

    ExplosionRingLogic.TntSpec wave =
        ExplosionRingLogic.inferFromText("§6Взрывная волна", "§7Мощный взрыв");
    assertTrue(wave.custom);
    assertEquals(12.0f, wave.radius, 0.01f);

    ExplosionRingLogic.TntSpec explicit =
        ExplosionRingLogic.inferFromText("§eДинамит", "§7Радиус: 16 блоков");
    assertTrue(explicit.custom);
    assertEquals(16.0f, explicit.radius, 0.01f);

    ExplosionRingLogic.TntSpec area = ExplosionRingLogic.inferFromText("§bСупер ТНТ", "15x15");
    assertTrue(area.custom);
    assertTrue(area.radius >= 9.0f && area.radius <= 12.0f);

    ExplosionRingLogic.TntSpec vanilla = ExplosionRingLogic.inferFromText("", "");
    assertFalse(vanilla.custom);
    assertEquals(8.0f, vanilla.radius, 0.01f);
  }

  @Test
  void adaptivePacketLearnerRemembersServerCustomRadiusAndResetsOnWorldSwitch() {
    ExplosionRingLogic.ServerExplosionLearner learner =
        new ExplosionRingLogic.ServerExplosionLearner();
    learner.syncScope("mp:mc.holyworld.ru/s:abc123");
    learner.trackTnt(42, 100.5, 64.0, -20.5, "c4", 1000L);

    float learned = learner.recordExplosion(100.8, 64.2, -20.3, 7.5f, 11.0f, 2200L);
    assertEquals(15.0f, learned, 0.01f);
    assertTrue(learner.hasLearned("c4"));
    assertEquals(15.0f, learner.resolveRadius("c4", 10.0f, 0.0f, true), 0.01f);

    assertEquals(18.0f, learner.resolveRadius("c4", 10.0f, 18.0f, true), 0.01f);

    learner.syncScope("mp:mc.holyworld.ru/s:def456");
    assertFalse(learner.hasLearned("c4"));
    assertEquals(10.0f, learner.resolveRadius("c4", 10.0f, 0.0f, true), 0.01f);
  }

  @Test
  void computesBlastDamageAndLethalRadiusWithArmorAndProtection() {
    float unarmored = ExplosionRingLogic.estimateBlastDamage(1.0, 6.0f, 1.0f, 0f, 0f, 0, -1, 1.0f);
    float armored =
        ExplosionRingLogic.estimateBlastDamage(1.0, 6.0f, 1.0f, 20f, 12f, 16, 0, 1.0f);
    assertTrue(unarmored > 40f);
    assertTrue(armored < unarmored * 0.35f);
    assertEquals(0.0f, ExplosionRingLogic.estimateBlastDamage(12.5, 6.0f, 1.0f, 0f, 0f, 0, -1, 1.0f),
        0.001f);

    float lethalUnarmored =
        ExplosionRingLogic.computeLethalRadius(6.0f, 20.0f, 0f, 0f, 0, -1, 1.0f);
    float lethalArmored =
        ExplosionRingLogic.computeLethalRadius(6.0f, 20.0f, 20f, 12f, 20, 1, 1.0f);
    assertTrue(lethalUnarmored > 4.0f);
    assertTrue(lethalArmored < lethalUnarmored);
  }

  @Test
  void resolvesGlintColorModesAndPresets() {
    Settings.EnchantGlint cfg = new Settings.EnchantGlint();
    cfg.colorMode = "theme";
    assertEquals(0xff123456, ExplosionRingLogic.resolveGlintColor(cfg, 0x80123456, 1.0));

    cfg.colorMode = "preset";
    cfg.preset = "cyan";
    assertEquals(0xff38e2ff, ExplosionRingLogic.resolveGlintColor(cfg, 0xffffffff, 1.0));

    cfg.colorMode = "chroma";
    int c1 = ExplosionRingLogic.resolveGlintColor(cfg, 0xffffffff, 0.0);
    int c2 = ExplosionRingLogic.resolveGlintColor(cfg, 0xffffffff, 3.0);
    assertNotEquals(c1, c2);
  }
}
