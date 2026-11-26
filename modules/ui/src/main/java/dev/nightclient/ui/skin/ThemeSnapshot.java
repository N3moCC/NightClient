package dev.nightclient.ui.skin;

import dev.nightclient.config.MotionSettings;
import dev.nightclient.config.Theme;
import dev.nightclient.config.ThemeEffects;

final class ThemeSnapshot {
  private Theme saved;

  boolean update(Theme current) {
    boolean unchanged = saved != null && current != null
        && sameTheme(saved, current)
        && sameEffects(saved.effects, current.effects)
        && sameMotion(saved.motion, current.motion);
    if (!unchanged) saved = current == null ? null : current.copy();
    return unchanged;
  }

  private static boolean eq(float a, float b) {
    return Float.floatToIntBits(a) == Float.floatToIntBits(b);
  }

  private static boolean eq(String a, String b) {
    return a == b || (a != null && a.equals(b));
  }

  private static boolean sameTheme(Theme a, Theme b) {
    return a.customBackground == b.customBackground
        && a.customSurface == b.customSurface
        && a.customRaised == b.customRaised
        && a.customText == b.customText
        && a.customAccent == b.customAccent
        && a.customAccentEnabled == b.customAccentEnabled
        && a.background == b.background
        && a.surface == b.surface
        && a.raised == b.raised
        && a.border == b.border
        && a.text == b.text
        && a.muted == b.muted
        && a.accent == b.accent
        && a.secondaryAccent == b.secondaryAccent
        && a.blurQuality == b.blurQuality
        && eq(a.radius, b.radius)
        && eq(a.borderWidth, b.borderWidth)
        && eq(a.backgroundOpacity, b.backgroundOpacity)
        && eq(a.surfaceOpacity, b.surfaceOpacity)
        && eq(a.raisedOpacity, b.raisedOpacity)
        && eq(a.tintOpacity, b.tintOpacity)
        && eq(a.blurRadius, b.blurRadius)
        && eq(a.blurMix, b.blurMix)
        && eq(a.glassMix, b.glassMix)
        && eq(a.accentGradient, b.accentGradient)
        && eq(a.fontTracking, b.fontTracking)
        && eq(a.name, b.name)
        && eq(a.mode, b.mode)
        && eq(a.fontWeight, b.fontWeight)
        && eq(a.fontFamily, b.fontFamily);
  }

  private static boolean sameEffects(ThemeEffects a, ThemeEffects b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return a.backgroundBlur == b.backgroundBlur
        && a.grain == b.grain
        && eq(a.grainAmount, b.grainAmount)
        && eq(a.grainScale, b.grainScale);
  }

  private static boolean sameMotion(MotionSettings a, MotionSettings b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return a.layout == b.layout
        && a.value == b.value
        && eq(a.duration, b.duration)
        && eq(a.enterDuration, b.enterDuration)
        && eq(a.exitDuration, b.exitDuration)
        && eq(a.enterScale, b.enterScale)
        && eq(a.hoverScale, b.hoverScale)
        && eq(a.pressScale, b.pressScale)
        && eq(a.enterOpacity, b.enterOpacity)
        && eq(a.enterOffsetX, b.enterOffsetX)
        && eq(a.enterOffsetY, b.enterOffsetY)
        && eq(a.stagger, b.stagger)
        && eq(a.easing, b.easing)
        && eq(a.numericMotion, b.numericMotion);
  }
}
