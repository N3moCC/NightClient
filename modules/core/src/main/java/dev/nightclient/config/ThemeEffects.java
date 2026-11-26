package dev.nightclient.config;

public final class ThemeEffects {
  public boolean backgroundBlur;
  public boolean grain;
  public float grainAmount, grainScale;
  public transient double time;

  public ThemeEffects copy() {
    ThemeEffects e = new ThemeEffects();
    e.copyFrom(this);
    return e;
  }

  public void copyFrom(ThemeEffects source) {
    if (source == null) return;
    backgroundBlur = source.backgroundBlur;
    grain = source.grain;
    grainAmount = source.grainAmount;
    grainScale = source.grainScale;
    time = source.time;
  }

  public void validate() {
    validate(Theme.defaults().effects);
  }

  void validate(ThemeEffects defaults) {
    grainAmount = bounded(grainAmount, defaults.grainAmount, 0, .15f);
    grainScale = bounded(grainScale, defaults.grainScale, .5f, 4);
  }

  private static float bounded(float x, float fallback, float low, float high) {
    return Float.isFinite(x) ? Math.max(low, Math.min(high, x)) : fallback;
  }
}
