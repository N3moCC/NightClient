package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

class TapeMouseLogicTest {
  @Test
  void parsesAllTapeMouseChatCommandsAndCustomKeys() {
    Settings.TapeMouse cfg = new Settings.TapeMouse();
    assertFalse(cfg.enabled);

    TapeMouseLogic.CommandOutcome r1 = TapeMouseLogic.parseCommand("/tapemouse", cfg, true);
    assertNotNull(r1);
    assertTrue(r1.intercepted);
    assertTrue(cfg.enabled);

    TapeMouseLogic.CommandOutcome r2 = TapeMouseLogic.parseCommand("/tm right 0", cfg, true);
    assertNotNull(r2);
    assertEquals("use", cfg.action);
    assertEquals(0, cfg.delayTicks);
    assertTrue(cfg.enabled);

    TapeMouseLogic.parseCommand("/tapemouse left 12", cfg, false);
    assertEquals("attack", cfg.action);
    assertEquals(12, cfg.delayTicks);

    TapeMouseLogic.parseCommand("/tm g 20", cfg, true);
    assertEquals("custom", cfg.action);
    assertEquals("key.keyboard.g", cfg.customKey);
    assertEquals(20, cfg.delayTicks);

    TapeMouseLogic.parseCommand("/tm off", cfg, true);
    assertFalse(cfg.enabled);

    assertNull(TapeMouseLogic.parseCommand("/spawn", cfg, true));
  }

  @Test
  void schedulesHoldPeriodicClicksPauseInScreensAndStopOnDamage() {
    Settings.TapeMouse cfg = new Settings.TapeMouse();
    cfg.enabled = true;
    cfg.action = "use";
    cfg.delayTicks = 0;
    cfg.pauseInScreens = true;
    cfg.stopOnDamage = true;

    TapeMouseLogic logic = new TapeMouseLogic();

    TapeMouseLogic.TickStep s1 = logic.step(cfg, true, false, false);
    assertTrue(s1.holdDown);
    assertFalse(s1.triggerClick);

    TapeMouseLogic.TickStep pauseStep = logic.step(cfg, true, true, false);
    assertFalse(pauseStep.holdDown);
    assertTrue(pauseStep.releaseHold);

    cfg.action = "attack";
    cfg.delayTicks = 3;
    cfg.jitterTicks = 0;
    TapeMouseLogic.TickStep c0 = logic.step(cfg, true, false, false);
    assertTrue(c0.triggerClick);
    assertFalse(logic.step(cfg, true, false, false).triggerClick);
    assertFalse(logic.step(cfg, true, false, false).triggerClick);
    assertTrue(logic.step(cfg, true, false, false).triggerClick);

    TapeMouseLogic.TickStep dmg = logic.step(cfg, true, false, true);
    assertFalse(cfg.enabled);
    assertFalse(dmg.triggerClick);
  }
}
