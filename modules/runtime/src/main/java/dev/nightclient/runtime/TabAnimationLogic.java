package dev.nightclient.runtime;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;

public final class TabAnimationLogic {
  public static final class Frame {
    public final boolean visible;
    public final float progress;
    public final float scale;
    public final float slideFraction;

    public Frame(boolean visible, float progress, float scale, float slideFraction) {
      this.visible = visible;
      this.progress = progress;
      this.scale = scale;
      this.slideFraction = slideFraction;
    }
  }

  private static final float MIN_VISIBLE_EPSILON = 0.01f;
  private float linearProgress = 0.0f;
  private long lastNanos = 0L;

  public void reset() {
    linearProgress = 0.0f;
    lastNanos = 0L;
  }

  public float progress() {
    return linearProgress;
  }

  public Frame update(boolean targetOpen, Settings.TabAnimation cfg, long nowNanos) {
    if (FeaturePolicy.isBlocked("tabAnimation") || cfg == null || !cfg.enabled) {
      linearProgress = targetOpen ? 1.0f : 0.0f;
      lastNanos = nowNanos;
      return new Frame(targetOpen, linearProgress, targetOpen ? 1.0f : 0.0f, 0.0f);
    }

    float dtSec;
    if (lastNanos == 0L || nowNanos < lastNanos) {
      dtSec = 0.0f;
    } else {
      dtSec = (float) Math.min(0.1, (nowNanos - lastNanos) * 1.0e-9);
    }
    lastNanos = nowNanos;

    float durationSec = Math.max(0.06f, Math.min(0.60f, cfg.durationMs / 1000.0f));
    float step = dtSec / durationSec;
    if (targetOpen) {
      linearProgress = Math.min(1.0f, linearProgress + step);
    } else {
      linearProgress = Math.max(0.0f, linearProgress - step);
    }

    float eased = easeOutCubic(linearProgress);
    boolean visible = targetOpen || eased > MIN_VISIBLE_EPSILON;
    if (!visible) {
      eased = 0.0f;
    }
    float slide = "slide".equals(cfg.style) ? (1.0f - eased) : 0.0f;
    float scale = "slide".equals(cfg.style) ? 1.0f : eased;
    return new Frame(visible, linearProgress, scale, slide);
  }

  public static float easeOutCubic(float t) {
    float c = Math.max(0.0f, Math.min(1.0f, t));
    float inv = 1.0f - c;
    return 1.0f - inv * inv * inv;
  }
}
