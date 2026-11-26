package dev.nightclient.runtime;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;

public final class TabPingLogic {
  private static final int MAX_RESERVED_TEXT_WIDTH = 48;
  private static final int VANILLA_ICON_WIDTH = 10;
  private static final int RIGHT_MARGIN = 1;
  private static final int TEXT_BARS_GAP = 2;
  private static final int LEFT_GAP_REPLACE = 4;
  private static final int LEFT_GAP_WITH_BARS = 3;

  private TabPingLogic() {}

  public static boolean isWorldActive(Settings.PlayerPing cfg) {
    return !FeaturePolicy.isBlocked("playerPing") && cfg != null && cfg.enabled && cfg.inWorld;
  }

  public static boolean isTabActive(Settings.PlayerPing cfg) {
    return !FeaturePolicy.isBlocked("playerPing") && cfg != null && cfg.enabled && cfg.inTab;
  }

  public static String formatLabel(int pingMs) {
    return Math.max(0, pingMs) + "ms";
  }

  public static int colorFor(int pingMs, boolean colored) {
    if (!colored) return 0xffe2e8f0;
    if (pingMs < 60) return 0xff4ade80;
    if (pingMs < 120) return 0xfffacc15;
    if (pingMs < 200) return 0xfffb923c;
    return 0xfff87171;
  }

  public static int reserveColumnWidth(
      Settings.PlayerPing cfg, int vanillaWidth, int maxTextWidth) {
    if (!isTabActive(cfg)) return vanillaWidth;
    int clamped = Math.max(0, Math.min(MAX_RESERVED_TEXT_WIDTH, maxTextWidth));
    int needed = cfg.tabKeepBars
        ? clamped + LEFT_GAP_WITH_BARS + TEXT_BARS_GAP + VANILLA_ICON_WIDTH + RIGHT_MARGIN
        : clamped + LEFT_GAP_REPLACE + RIGHT_MARGIN;
    return Math.max(vanillaWidth, needed);
  }

  public static int computeTextX(int rowX, int slotWidth, int textWidth, boolean keepBars) {
    int rightAnchor = keepBars
        ? rowX + slotWidth - VANILLA_ICON_WIDTH - RIGHT_MARGIN - TEXT_BARS_GAP
        : rowX + slotWidth - RIGHT_MARGIN;
    return rightAnchor - Math.max(0, textWidth);
  }
}
