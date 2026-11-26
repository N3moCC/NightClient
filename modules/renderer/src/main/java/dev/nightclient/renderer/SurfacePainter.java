package dev.nightclient.renderer;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.config.Theme;

public final class SurfacePainter {
  private SurfacePainter() {}

  public static boolean usesBackdrop(Theme t) {
    return t != null && t.effects != null && t.effects.backgroundBlur
        && Float.isFinite(t.blurRadius) && t.blurRadius > 0 && Float.isFinite(t.blurMix)
        && t.blurMix > 0;
  }

  public static void panel(Canvas c, Theme t, float x, float y, float w, float h, int fill,
      float radius, boolean effects) {
    panel(c, t, x, y, w, h, fill, radius, effects, false, false);
  }

  public static void panel(Canvas c, Theme t, float x, float y, float w, float h, int fill,
      float radius, boolean effects, boolean selected) {
    panel(c, t, x, y, w, h, fill, radius, effects, selected, false);
  }

  public static void panel(Canvas c, Theme t, float x, float y, float w, float h, int fill,
      float radius, boolean effects, boolean selected, boolean focused) {
    paint(c, t, x, y, w, h, fill, radius, t.surfaceOpacity, true, selected || focused,
        focused ? 1.25f : 1);
  }

  public static void raised(Canvas c, Theme t, float x, float y, float w, float h, int fill,
      float radius, boolean effects) {
    paint(c, t, x, y, w, h, fill, radius, t.raisedOpacity, false, false, 1);
  }

  public static void control(Canvas c, Theme t, float x, float y, float w, float h, boolean active,
      boolean focused, float hover) {
    int fill = active ? Colors.mix(t.raised, t.accent, .16f)
                      : Colors.mix(t.raised, t.text, clamp(hover, 0, 1) * .025f);
    paint(c, t, x, y, w, h, fill, Math.min(t.radius, 8), t.raisedOpacity, false, active || focused,
        focused ? 1.25f : 1);
  }

  public static float radius(Theme t, float width, float height, float requested) {
    return Math.min(Math.max(0, requested), Math.max(0, Math.min(width, height) / 2));
  }

  private static void paint(Canvas c, Theme t, float x, float y, float w, float h, int fill,
      float requestedRadius, float materialOpacity, boolean backdrop, boolean emphasized,
      float stateThickness) {
    if (w <= 0 || h <= 0) return;
    float opacity = clamp(t.tintOpacity >= 0 ? t.tintOpacity : t.backgroundOpacity, 0, 1);
    float radius = radius(t, w, h, requestedRadius);
    float surfaceAlpha = ((fill >>> 24) / 255f) * clamp(materialOpacity, 0, 1);
    float alpha = surfaceAlpha * opacity;
    boolean sampled = backdrop && usesBackdrop(t);
    boolean composed = t.glassMix >= 0;
    float sample = composed ? GlassBlend.sampleOpacity(surfaceAlpha, t.glassMix) : surfaceAlpha;
    float tint = composed ? (sampled ? GlassBlend.tintOpacity(alpha, t.glassMix)
                                     : GlassBlend.alpha(alpha, t.glassMix))
                          : alpha;
    int tintAlpha = Math.round(tint * 255);
    if (sampled && opacity > 0 && sample > 0 && tintAlpha < 255)
      c.backdrop(x, y, w, h, radius, t.blurRadius, t.blurQuality, sample * clamp(t.blurMix, 0, 1));
    if (tintAlpha > 0) c.roundedRect(x, y, w, h, radius, (tintAlpha << 24) | (fill & 0xffffff));
    float thickness = emphasized ? Math.max(stateThickness, t.borderWidth) : t.borderWidth;
    if (composed && thickness > 0 && opacity > 0) {
      float inset = Math.min(.5f, Math.min(w, h) / 2);
      c.strokeRoundedRect(x + inset, y + inset, Math.max(0, w - inset * 2),
          Math.max(0, h - inset * 2), Math.max(0, radius - inset), 1,
          Colors.alpha(fill, opacity * materialOpacity));
    }
    if (thickness > 0 && opacity > 0) {
      float inset = Math.min(thickness / 2, Math.min(w, h) / 2);
      c.strokeRoundedRect(x + inset, y + inset, Math.max(0, w - inset * 2),
          Math.max(0, h - inset * 2), Math.max(0, radius - inset), thickness,
          Colors.alpha(emphasized ? t.accent : t.border, opacity));
    }
  }

  public static void accent(
      Canvas c, Theme t, float x, float y, float w, float h, float radius, float opacity) {
    if (w > 0 && h > 0 && opacity > 0)
      c.gradientRoundedRect(x, y, w, h, radius, Colors.alpha(t.accent, opacity),
          Colors.alpha(Colors.mix(t.accent, t.secondaryAccent, t.accentGradient), opacity));
  }

  public static void fillRounded(
      Canvas c, float x, float y, float w, float h, float radius, int argb) {
    if (c != null && w > 0 && h > 0 && (argb >>> 24) != 0)
      c.roundedRect(x, y, w, h, radius, argb);
  }

  private static float clamp(float value, float min, float max) {
    return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
  }
}
