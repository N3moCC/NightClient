package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

final class TabAnimationLogicTest {
  @Test
  void easesOpenAndHoldsVisibilityWhileClosing() {
    Settings.TabAnimation cfg = new Settings.TabAnimation();
    cfg.enabled = true;
    cfg.style = "scale";
    cfg.durationMs = 200;

    TabAnimationLogic anim = new TabAnimationLogic();
    long t0 = 1_000_000_000L;

    TabAnimationLogic.Frame idle = anim.update(false, cfg, t0);
    assertFalse(idle.visible);
    assertEquals(0.0f, idle.scale, 0.001f);

    TabAnimationLogic.Frame half = anim.update(true, cfg, t0 + 100_000_000L);
    assertTrue(half.visible);
    assertTrue(half.scale > 0.8f && half.scale < 0.95f);

    TabAnimationLogic.Frame full = anim.update(true, cfg, t0 + 300_000_000L);
    assertTrue(full.visible);
    assertEquals(1.0f, full.scale, 0.001f);

    TabAnimationLogic.Frame closing = anim.update(false, cfg, t0 + 400_000_000L);
    assertTrue(closing.visible);
    assertTrue(closing.scale > 0.01f && closing.scale < 1.0f);

    TabAnimationLogic.Frame done = anim.update(false, cfg, t0 + 700_000_000L);
    assertFalse(done.visible);
    assertEquals(0.0f, done.scale, 0.001f);
  }

  @Test
  void slideStyleTranslatesWhileKeepingUnitScaleAndDisabledBypasses() {
    Settings.TabAnimation cfg = new Settings.TabAnimation();
    cfg.enabled = true;
    cfg.style = "slide";
    cfg.durationMs = 200;

    TabAnimationLogic anim = new TabAnimationLogic();
    long t0 = 5_000_000_000L;
    anim.update(false, cfg, t0);
    TabAnimationLogic.Frame mid = anim.update(true, cfg, t0 + 50_000_000L);
    assertTrue(mid.visible);
    assertEquals(1.0f, mid.scale, 0.001f);
    assertTrue(mid.slideFraction > 0.0f && mid.slideFraction < 1.0f);

    cfg.enabled = false;
    TabAnimationLogic.Frame instant = anim.update(true, cfg, t0 + 60_000_000L);
    assertTrue(instant.visible);
    assertEquals(1.0f, instant.scale, 0.001f);
    assertEquals(0.0f, instant.slideFraction, 0.001f);
  }
}
