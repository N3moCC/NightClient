package dev.nightclient.runtime;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.Icons;
import dev.nightclient.renderer.WorldRibbon;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DamageIndicators {
  private static final double HEALTH_UPDATE_WINDOW = 1.5;
  private static final int MAX_PENDING = 64, MAX_SPLASHES = 48;
  private final Map<Integer, Pending> pending = new LinkedHashMap<Integer, Pending>();
  private final List<Splash> splashes = new ArrayList<Splash>();
  private final double[] projPoint = new double[5];

  private static final class Pending {
    float health;
    final double started;
    final boolean critical;
    Pending(float health, boolean critical, double time) {
      this.health = health;
      this.critical = critical;
      started = time;
    }
  }
  private static final class Splash {
    final double x, y, z, started;
    final float damage;
    final boolean critical;
    final String label;
    Splash(double x, double y, double z, float damage, boolean critical, double started) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.damage = damage;
      this.critical = critical;
      this.started = started;
      this.label = Math.abs(damage - Math.round(damage)) < .05f
          ? Integer.toString(Math.round(damage))
          : String.format(java.util.Locale.ROOT, "%.1f", damage);
    }
  }

  public void attack(int entityId, float health, boolean critical, double time) {
    if (!Float.isFinite(health) || !Double.isFinite(time) || health <= 0) return;
    pending.remove(entityId);
    if (pending.size() >= MAX_PENDING) pending.remove(pending.keySet().iterator().next());
    pending.put(entityId, new Pending(health, critical, time));
  }

  public boolean sample(int entityId, float health, double x, double y, double z, double time) {
    Pending watch = pending.get(entityId);
    if (watch == null) return false;
    if (!Float.isFinite(health) || !Double.isFinite(time)
        || time - watch.started > HEALTH_UPDATE_WINDOW || time < watch.started) {
      pending.remove(entityId);
      return false;
    }
    float current = Math.max(0, health), damage = watch.health - current;
    if (damage > .01f) {
      pending.remove(entityId);
      if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
        if (splashes.size() >= MAX_SPLASHES) splashes.remove(0);
        splashes.add(new Splash(x, y, z, damage, watch.critical, time));
      }
      return false;
    }
    watch.health = Math.max(watch.health, current);
    return true;
  }

  public void forget(int entityId) {
    pending.remove(entityId);
  }
  public void clear() {
    pending.clear();
    splashes.clear();
  }
  public int pendingCount() {
    return pending.size();
  }
  public int splashCount() {
    return splashes.size();
  }

  public boolean visible(double time, float duration) {
    expire(time, duration);
    return !splashes.isEmpty();
  }
  private void expire(double time, float duration) {
    for (Iterator<Splash> it = splashes.iterator(); it.hasNext();) {
      double age = time - it.next().started;
      if (age < 0 || age >= duration) it.remove();
    }
    for (Iterator<Pending> it = pending.values().iterator(); it.hasNext();)
      if (time - it.next().started > HEALTH_UPDATE_WINDOW) it.remove();
  }

  public void render(
      Canvas canvas, Settings settings, WorldRibbon.View view, int width, int height, double time) {
    Settings.DamageSplash style = settings.damageSplash;
    if (style == null || !style.enabled) {
      clear();
      return;
    }
    expire(time, style.duration);
    if (splashes.isEmpty() || canvas == null || view == null || width <= 0 || height <= 0) return;
    double[] point = projPoint;
    for (int i = 0; i < splashes.size(); i++) {
      Splash splash = splashes.get(i);
      float progress = (float) ((time - splash.started) / Math.max(.001f, style.duration));
      view.project(splash.x, splash.y + progress * .65, splash.z, point);
      if (point[2] <= .1 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) continue;
      float scale = style.scale * settings.menu.hudScale, font = 9 * scale;
      String label = splash.label;
      boolean critical = style.crits && splash.critical;
      int color = "custom".equals(style.colorMode) ? (critical ? style.critColor : style.color)
          : "accent".equals(style.colorMode)       ? settings.theme.accent
          : critical                                 ? 0xffffd75f
                                                     : 0xffff6b6b;
      float alpha = 1 - Math.max(0, Math.min(1, (progress - .65f) / .35f));
      float textWidth = canvas.textWidthFamily("studio-sans-medium", label, font, true, 0);
      float icon = style.icons ? 7 * scale : 0, gap = style.icons ? 3 * scale : 0;
      float x = (float) point[0] - (textWidth + icon + gap) / 2, y = (float) point[1] - font / 2;
      if (x + textWidth + icon + gap < 0 || x > width || y + font < 0 || y > height) continue;
      canvas.textFamily("studio-sans-medium", label, x + icon + gap + scale, y + scale, font,
          Colors.alpha(0xff000000, alpha * .7f), true, 0);
      if (style.icons)
        Icons.draw(canvas, "crosshair", x, y + (font - icon) / 2, icon, Colors.alpha(color, alpha),
            critical ? 2 : 1.4f);
      canvas.textFamily("studio-sans-medium", label, x + icon + gap, y, font,
          Colors.alpha(color, alpha), true, 0);
    }
  }
}
