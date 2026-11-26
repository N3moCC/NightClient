package dev.nightclient.runtime;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;

public final class ItemScrollerLogic {
  private ItemScrollerLogic() {}

  public static boolean canDragMove(Settings.ItemScroller cfg) {
    return !FeaturePolicy.isBlocked("itemScroller") && cfg != null && cfg.enabled && cfg.dragMatching;
  }

  public static boolean canMoveMatching(Settings.ItemScroller cfg) {
    return !FeaturePolicy.isBlocked("itemScroller") && cfg != null && cfg.enabled && cfg.moveMatching;
  }

  public static boolean canDropMatching(Settings.ItemScroller cfg) {
    return !FeaturePolicy.isBlocked("itemScroller") && cfg != null && cfg.enabled && cfg.dropMatching;
  }

  public static boolean canShiftPlace(Settings.ItemScroller cfg) {
    return !FeaturePolicy.isBlocked("itemScroller") && cfg != null && cfg.enabled && cfg.shiftPlace;
  }

  public static boolean canScroll(Settings.ItemScroller cfg, double amount) {
    if (FeaturePolicy.isBlocked("itemScroller") || cfg == null || !cfg.enabled || !cfg.wheel || amount == 0.0) return false;
    if ("up".equals(cfg.wheelDirection)) return amount > 0.0;
    if ("down".equals(cfg.wheelDirection)) return amount < 0.0;
    return true;
  }

  public static boolean wheelMovesMatching(Settings.ItemScroller cfg) {
    return cfg != null && "matching".equals(cfg.wheelMode);
  }

  public static boolean cooldownElapsed(long lastNanos, long nowNanos, int cooldownMs) {
    if (cooldownMs <= 0 || lastNanos == 0L) return true;
    return (nowNanos - lastNanos) >= (long) cooldownMs * 1_000_000L;
  }
}
