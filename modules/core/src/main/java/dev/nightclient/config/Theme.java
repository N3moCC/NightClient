package dev.nightclient.config;

import dev.nightclient.api.Colors;
import dev.nightclient.skin.DefaultSkin;

public final class Theme {
  public ThemeEffects effects = new ThemeEffects();
  public MotionSettings motion = new MotionSettings();
  public String name = "";
  public String mode = "dark";
  public int customBackground = 0xff101216;
  public int customSurface = 0xff171a20;
  public int customRaised = 0xff22262f;
  public int customText = 0xfff4f6fb;
  public int customAccent = 0xfff4f6fb;
  public boolean customAccentEnabled;
  private transient int lastAppliedAccent;
  private transient boolean paletteInitialized;
  public int background, surface, raised, border, text, muted, accent, secondaryAccent;
  public float radius, borderWidth;
  public float backgroundOpacity, surfaceOpacity, raisedOpacity;
  public float tintOpacity = -1;
  public float blurRadius;
  public int blurQuality;
  public float blurMix = 1, glassMix = -1;
  public float accentGradient;
  public float fontTracking;
  public String fontWeight = "", fontFamily = "onest";

  public Theme() {
    this(true);
  }

  private Theme(boolean defaults) {
    if (defaults) {
      DefaultSkin.documentSafe(null).applyDefaults(this);
      applyMode();
    }
  }

  public static Theme defaults() {
    return new Theme();
  }

  public void selectMode(String selected) {
    mode = selected;
    applyMode();
  }

  public void applyMode() {
    applyMode(true);
  }

  void applySavedMode() {
    applyMode(false);
  }

  private void applyMode(boolean captureAccent) {
    if (captureAccent && paletteInitialized && accent != lastAppliedAccent) {
      if (!"custom".equals(mode)) {
        customBackground = background;
        customSurface = surface;
        customRaised = raised;
        customText = text;
        mode = "custom";
      }
      customAccent = accent;
      customAccentEnabled = true;
    }
    if (!"light".equals(mode) && !"custom".equals(mode)) mode = "dark";
    if ("light".equals(mode)) {
      background = 0xfff4f4f5;
      surface = 0xffffffff;
      raised = 0xffeeeef0;
      text = 0xff18181b;
      muted = 0xff71717a;
      border = 0xffe4e4e7;
    } else if ("custom".equals(mode)) {
      background = customBackground;
      surface = customSurface;
      raised = customRaised;
      text = customText;
      muted = Colors.mix(text, surface, .42f);
      border = Colors.alpha(text, .12f);
    } else {
      background = 0xff101216;
      surface = 0xff171a20;
      raised = 0xff22262f;
      text = 0xfff4f6fb;
      muted = 0xffa0a7b3;
      border = 0x20ffffff;
    }
    if (!customAccentEnabled) customAccent = text;
    accent = secondaryAccent = "custom".equals(mode) && customAccentEnabled ? customAccent : text;
    lastAppliedAccent = accent;
    paletteInitialized = true;
  }

  public Theme copy() {
    Theme t = new Theme(false);
    t.copyFrom(this);
    return t;
  }

  public void copyFrom(Theme source) {
    if (source == null || source == this) return;
    if (source.effects != null) {
      if (effects == null) effects = source.effects.copy();
      else effects.copyFrom(source.effects);
    }
    if (source.motion != null) {
      if (motion == null) motion = source.motion.copy();
      else motion.copyFrom(source.motion);
    }
    name = source.name;
    mode = source.mode;
    customBackground = source.customBackground;
    customSurface = source.customSurface;
    customRaised = source.customRaised;
    customText = source.customText;
    customAccent = source.customAccent;
    customAccentEnabled = source.customAccentEnabled;
    lastAppliedAccent = source.lastAppliedAccent;
    paletteInitialized = source.paletteInitialized;
    background = source.background;
    surface = source.surface;
    raised = source.raised;
    border = source.border;
    text = source.text;
    muted = source.muted;
    accent = source.accent;
    secondaryAccent = source.secondaryAccent;
    radius = source.radius;
    borderWidth = source.borderWidth;
    backgroundOpacity = source.backgroundOpacity;
    tintOpacity = source.tintOpacity;
    surfaceOpacity = source.surfaceOpacity;
    raisedOpacity = source.raisedOpacity;
    blurRadius = source.blurRadius;
    blurQuality = source.blurQuality;
    blurMix = source.blurMix;
    glassMix = source.glassMix;
    accentGradient = source.accentGradient;
    fontTracking = source.fontTracking;
    fontWeight = source.fontWeight;
    fontFamily = source.fontFamily;
  }
}
