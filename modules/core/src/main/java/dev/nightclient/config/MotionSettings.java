package dev.nightclient.config;

public final class MotionSettings {
  public float duration, enterDuration, exitDuration;
  public String easing = "";
  public float enterScale = 1;
  public float hoverScale, pressScale, enterOpacity, enterOffsetX, enterOffsetY;
  public boolean layout, value;
  public float stagger;
  public String numericMotion = "off";

  public static boolean supportedNumericMotion(String mode) {
    return "off".equals(mode) || "roll".equals(mode) || "fade".equals(mode)
        || "slide".equals(mode);
  }

  private static float bounded(float value, float fallback, float min, float max) {
    return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
  }

  public void validate() {
    validate(Theme.defaults().motion);
  }

  void validate(MotionSettings defaults) {
    enterScale = bounded(enterScale, 1, .01f, 2);
    duration = bounded(duration, defaults.duration, 0, 5);
    enterDuration = bounded(enterDuration, defaults.enterDuration, 0, 5);
    exitDuration = bounded(exitDuration, defaults.exitDuration, 0, 5);
    hoverScale = bounded(hoverScale, defaults.hoverScale, .5f, 1.5f);
    pressScale = bounded(pressScale, defaults.pressScale, .5f, 1.5f);
    enterOpacity = bounded(enterOpacity, defaults.enterOpacity, 0, 1);
    enterOffsetX = bounded(enterOffsetX, defaults.enterOffsetX, -100, 100);
    enterOffsetY = bounded(enterOffsetY, defaults.enterOffsetY, -100, 100);
    stagger = bounded(stagger, defaults.stagger, 0, .2f);
    if (!Easing.supported(easing)) easing = defaults.easing;
    if (!supportedNumericMotion(numericMotion))
      numericMotion = supportedNumericMotion(defaults.numericMotion) ? defaults.numericMotion : "off";
  }

  public MotionSettings copy() {
    MotionSettings m = new MotionSettings();
    m.copyFrom(this);
    return m;
  }

  public void copyFrom(MotionSettings source) {
    if (source == null) return;
    enterScale = source.enterScale;
    duration = source.duration;
    enterDuration = source.enterDuration;
    exitDuration = source.exitDuration;
    easing = source.easing;
    hoverScale = source.hoverScale;
    pressScale = source.pressScale;
    enterOpacity = source.enterOpacity;
    enterOffsetX = source.enterOffsetX;
    enterOffsetY = source.enterOffsetY;
    layout = source.layout;
    value = source.value;
    stagger = source.stagger;
    numericMotion = source.numericMotion;
  }
}
