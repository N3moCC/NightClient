package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

class ItemScrollerLogicTest {
  @Test
  void gestureGatesRequireBothModuleEnabledAndSpecificToggle() {
    Settings.ItemScroller cfg = new Settings.ItemScroller();
    assertFalse(ItemScrollerLogic.canDragMove(null));
    assertFalse(ItemScrollerLogic.canDragMove(cfg));
    assertFalse(ItemScrollerLogic.canMoveMatching(cfg));
    assertFalse(ItemScrollerLogic.canDropMatching(cfg));
    assertFalse(ItemScrollerLogic.canShiftPlace(cfg));

    cfg.enabled = true;
    assertTrue(ItemScrollerLogic.canDragMove(cfg));
    assertTrue(ItemScrollerLogic.canMoveMatching(cfg));
    assertTrue(ItemScrollerLogic.canDropMatching(cfg));
    assertFalse(ItemScrollerLogic.canShiftPlace(cfg));

    cfg.dragMatching = false;
    cfg.moveMatching = false;
    cfg.dropMatching = false;
    cfg.shiftPlace = true;
    assertFalse(ItemScrollerLogic.canDragMove(cfg));
    assertFalse(ItemScrollerLogic.canMoveMatching(cfg));
    assertFalse(ItemScrollerLogic.canDropMatching(cfg));
    assertTrue(ItemScrollerLogic.canShiftPlace(cfg));
  }

  @Test
  void scrollRespectsEnabledWheelFlagDirectionAndMode() {
    Settings.ItemScroller cfg = new Settings.ItemScroller();
    assertFalse(ItemScrollerLogic.canScroll(cfg, 1.0));
    assertFalse(ItemScrollerLogic.wheelMovesMatching(cfg));

    cfg.enabled = true;
    assertFalse(ItemScrollerLogic.canScroll(cfg, 1.0));

    cfg.wheel = true;
    assertFalse(ItemScrollerLogic.canScroll(cfg, 0.0));
    assertTrue(ItemScrollerLogic.canScroll(cfg, 1.0));
    assertTrue(ItemScrollerLogic.canScroll(cfg, -1.0));

    cfg.wheelDirection = "up";
    assertTrue(ItemScrollerLogic.canScroll(cfg, 1.0));
    assertFalse(ItemScrollerLogic.canScroll(cfg, -1.0));

    cfg.wheelDirection = "down";
    assertFalse(ItemScrollerLogic.canScroll(cfg, 1.0));
    assertTrue(ItemScrollerLogic.canScroll(cfg, -1.0));

    cfg.wheelMode = "matching";
    assertTrue(ItemScrollerLogic.wheelMovesMatching(cfg));
    cfg.wheelMode = "stack";
    assertFalse(ItemScrollerLogic.wheelMovesMatching(cfg));
  }

  @Test
  void cooldownAllowsImmediateFirstActionZeroDelayAndExactThreshold() {
    assertTrue(ItemScrollerLogic.cooldownElapsed(0L, 1_000_000L, 80));
    assertTrue(ItemScrollerLogic.cooldownElapsed(10_000_000L, 10_000_000L, 0));
    assertFalse(ItemScrollerLogic.cooldownElapsed(100_000_000L, 179_999_999L, 80));
    assertTrue(ItemScrollerLogic.cooldownElapsed(100_000_000L, 180_000_000L, 80));
    assertTrue(ItemScrollerLogic.cooldownElapsed(100_000_000L, 250_000_000L, 80));
  }
}
