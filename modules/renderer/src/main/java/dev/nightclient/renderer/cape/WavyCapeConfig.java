package dev.nightclient.renderer.cape;

import dev.nightclient.config.Settings;

public final class WavyCapeConfig {
  public static final String STYLE_SMOOTH = "smooth";
  public static final String STYLE_CRISP = "crisp";
  public static final String STYLE_BLOCKY = "blocky";
  public static final String STYLE_SLIM = "slim";

  public static final String QUALITY_LOW = "low";
  public static final String QUALITY_MEDIUM = "medium";
  public static final String QUALITY_HIGH = "high";

  public static final String PLAYERS_ALL = "all";
  public static final String PLAYERS_SELF = "self";

  public final boolean enabled;
  public final String style;
  public final String quality;
  public final String players;
  public final float gravity;
  public final float stiffness;
  public final float damping;
  public final float drag;
  public final float turnSwing;
  public final float wind;
  public final float flutter;
  public final boolean weatherWind;
  public final boolean waterPhysics;
  public final float thickness;
  public final float widthScale;
  public final float lengthScale;
  public final float hemFlare;
  public final float foldShading;
  public final boolean bodyCollision;
  public final float maxDistance;

  public WavyCapeConfig(boolean enabled, String style, String quality, String players,
      float gravity, float stiffness, float damping, float drag, float turnSwing, float wind,
      float flutter, boolean weatherWind, boolean waterPhysics, float thickness, float widthScale,
      float lengthScale, float hemFlare, float foldShading, boolean bodyCollision,
      float maxDistance) {
    this.enabled = enabled;
    this.style = style == null ? STYLE_SMOOTH : style;
    this.quality = quality == null ? QUALITY_MEDIUM : quality;
    this.players = players == null ? PLAYERS_ALL : players;
    this.gravity = clamp(gravity, 0.2f, 2.5f);
    this.stiffness = clamp(stiffness, 0.0f, 1.0f);
    this.damping = clamp(damping, 0.0f, 1.0f);
    this.drag = clamp(drag, 0.2f, 2.5f);
    this.turnSwing = clamp(turnSwing, 0.0f, 2.5f);
    this.wind = clamp(wind, 0.0f, 2.5f);
    this.flutter = clamp(flutter, 0.0f, 2.0f);
    this.weatherWind = weatherWind;
    this.waterPhysics = waterPhysics;
    this.thickness = clamp(thickness, 0.2f, 1.8f);
    this.widthScale = clamp(widthScale, 0.8f, 1.35f);
    this.lengthScale = clamp(lengthScale, 0.7f, 1.35f);
    this.hemFlare = clamp(hemFlare, 0.0f, 1.0f);
    this.foldShading = clamp(foldShading, 0.0f, 1.0f);
    this.bodyCollision = bodyCollision;
    this.maxDistance = clamp(maxDistance, 16.0f, 128.0f);
  }

  public static WavyCapeConfig defaults() {
    return new WavyCapeConfig(true, STYLE_SMOOTH, QUALITY_MEDIUM, PLAYERS_ALL, 1.0f, 0.28f, 0.34f,
        1.05f, 1.15f, 0.65f, 0.90f, true, true, 0.90f, 1.0f, 1.0f, 0.14f, 0.45f, true, 64.0f);
  }

  public static WavyCapeConfig fromSettings(Settings.WavyCapes s) {
    if (s == null) {
      return new WavyCapeConfig(false, STYLE_SMOOTH, QUALITY_MEDIUM, PLAYERS_ALL, 1.0f, 0.28f,
          0.34f, 1.05f, 1.15f, 0.65f, 0.90f, true, true, 0.90f, 1.0f, 1.0f, 0.14f, 0.45f, true,
          64.0f);
    }
    return new WavyCapeConfig(s.enabled, s.style, s.quality, s.players, s.gravity, s.stiffness,
        s.damping, s.drag, s.turnSwing, s.wind, s.flutter, s.weatherWind, s.waterPhysics,
        s.thickness, s.widthScale, s.lengthScale, s.hemFlare, s.foldShading, s.bodyCollision,
        s.maxDistance);
  }

  public int gridCols() {
    if (QUALITY_HIGH.equals(quality)) return 9;
    if (QUALITY_LOW.equals(quality)) return 5;
    return 7;
  }

  public int gridRows() {
    if (QUALITY_HIGH.equals(quality)) return 21;
    if (QUALITY_LOW.equals(quality)) return 11;
    return 15;
  }

  public float effectiveThickness() {
    float base = (1.0f / 16.0f) * thickness;
    return STYLE_SLIM.equals(style) ? base * 0.48f : base;
  }

  private static float clamp(float value, float min, float max) {
    if (Float.isNaN(value) || Float.isInfinite(value)) return min;
    return value < min ? min : (value > max ? max : value);
  }
}
