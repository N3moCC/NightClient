package dev.nightclient.runtime;

import dev.nightclient.api.PotionView;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EffectAlertLogic {
  private static final long EXPIRE_BANNER_MS = 2600L;

  public static final class SoundCue {
    public final String soundId;
    public final float volume;
    public final float pitch;

    public SoundCue(String soundId, float volume, float pitch) {
      this.soundId = soundId;
      this.volume = volume;
      this.pitch = pitch;
    }
  }

  public static final class HudAlert {
    public final String effectId;
    public final String name;
    public final int amplifier;
    public final int color;
    public final float remainingSeconds;
    public final boolean expired;
    public final float fadeAlpha;

    public HudAlert(String effectId, String name, int amplifier, int color, float remainingSeconds,
        boolean expired, float fadeAlpha) {
      this.effectId = effectId;
      this.name = name;
      this.amplifier = amplifier;
      this.color = color;
      this.remainingSeconds = remainingSeconds;
      this.expired = expired;
      this.fadeAlpha = fadeAlpha;
    }
  }

  private static final class TrackedEffect {
    String name;
    int amplifier;
    int color;
    float lastRemaining;
    boolean warned;
    long expiredAtMs;
  }

  private final Map<String, TrackedEffect> tracked = new HashMap<String, TrackedEffect>();
  private final List<HudAlert> currentHudAlerts = new ArrayList<HudAlert>(4);
  private final List<SoundCue> pendingSounds = new ArrayList<SoundCue>(2);

  public void clear() {
    tracked.clear();
    currentHudAlerts.clear();
    pendingSounds.clear();
  }

  public List<HudAlert> hudAlerts() {
    return currentHudAlerts;
  }

  public List<SoundCue> drainSounds() {
    if (pendingSounds.isEmpty()) return Collections.emptyList();
    List<SoundCue> copy = new ArrayList<SoundCue>(pendingSounds);
    pendingSounds.clear();
    return copy;
  }

  public void update(Settings.EffectAlert cfg, List<PotionView> activeEffects, long nowMs) {
    currentHudAlerts.clear();
    if (cfg == null || !cfg.enabled) {
      tracked.clear();
      pendingSounds.clear();
      return;
    }

    boolean wantSound = "both".equals(cfg.mode) || "sound".equals(cfg.mode);
    boolean wantHud = "both".equals(cfg.mode) || "hud".equals(cfg.mode);
    float warnThreshold = Math.max(1f, cfg.warnSeconds);

    Set<String> seen = new HashSet<String>();
    if (activeEffects != null) {
      for (int i = 0; i < activeEffects.size(); i++) {
        PotionView pv = activeEffects.get(i);
        if (pv == null || pv.id == null) continue;
        String key = normalizeEffectId(pv.id);
        if (!isEffectSelected(cfg, key)) continue;
        seen.add(key);

        TrackedEffect st = tracked.get(key);
        if (st == null) {
          st = new TrackedEffect();
          tracked.put(key, st);
        }
        st.name = (pv.name == null || pv.name.isEmpty()) ? fallbackName(key) : pv.name;
        st.amplifier = pv.amplifier;
        st.color = (pv.color & 0xFFFFFF) | 0xFF000000;
        st.expiredAtMs = 0L;

        if (pv.durationSeconds < 0f) {
          st.lastRemaining = 9999f;
          st.warned = false;
          continue;
        }

        if (pv.durationSeconds > warnThreshold + 1.2f) {
          st.warned = false;
        }

        if (pv.durationSeconds <= warnThreshold && pv.durationSeconds > 0.05f) {
          if (!st.warned) {
            st.warned = true;
            if (wantSound) {
              String sid = SoundControlLogic.resolvePresetSoundId(cfg.soundPreset, "");
              pendingSounds.add(new SoundCue(sid, cfg.soundVolume, cfg.soundPitch));
            }
          }
          if (wantHud) {
            currentHudAlerts.add(new HudAlert(
                pv.id, st.name, st.amplifier, st.color, pv.durationSeconds, false, 1.0f));
          }
        }
        st.lastRemaining = pv.durationSeconds;
      }
    }

    Iterator<Map.Entry<String, TrackedEffect>> it = tracked.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, TrackedEffect> entry = it.next();
      String key = entry.getKey();
      if (seen.contains(key)) continue;
      if (!isEffectSelected(cfg, key)) {
        it.remove();
        continue;
      }

      TrackedEffect st = entry.getValue();
      if (st.expiredAtMs == 0L) {
        if (cfg.notifyOnExpire && st.lastRemaining <= Math.max(warnThreshold + 3.0f, 8.0f)) {
          st.expiredAtMs = nowMs;
          if (wantSound) {
            String sid = SoundControlLogic.resolvePresetSoundId(cfg.soundPreset, "");
            pendingSounds.add(new SoundCue(
                sid, cfg.soundVolume, Math.max(0.5f, cfg.soundPitch * 0.72f)));
          }
        } else {
          it.remove();
          continue;
        }
      }

      long age = nowMs - st.expiredAtMs;
      if (age >= EXPIRE_BANNER_MS || !wantHud) {
        if (age >= EXPIRE_BANNER_MS) it.remove();
      } else {
        float fade = age > EXPIRE_BANNER_MS - 500L
            ? Math.max(0f, (EXPIRE_BANNER_MS - age) / 500f)
            : 1.0f;
        currentHudAlerts.add(new HudAlert(
            "minecraft:" + key, st.name, st.amplifier, st.color, 0f, true, fade));
      }
    }
  }

  public static boolean isEffectSelected(Settings.EffectAlert cfg, String rawId) {
    if (cfg == null || rawId == null) return false;
    String id = normalizeEffectId(rawId);
    if ("strength".equals(id) || "increase_damage".equals(id)) return cfg.strength;
    if ("speed".equals(id) || "movement_speed".equals(id)) return cfg.speed;
    if ("fire_resistance".equals(id)) return cfg.fireResistance;
    if ("invisibility".equals(id)) return cfg.invisibility;
    if ("regeneration".equals(id)) return cfg.regeneration;
    if ("resistance".equals(id) || "damage_resistance".equals(id)) return cfg.resistance;
    if ("absorption".equals(id)) return cfg.absorption;
    if ("haste".equals(id) || "dig_speed".equals(id)) return cfg.haste;
    if ("slow_falling".equals(id)) return cfg.slowFalling;
    if ("water_breathing".equals(id) || "conduit_power".equals(id)) return cfg.waterBreathing;
    if ("night_vision".equals(id)) return cfg.nightVision;
    return false;
  }

  public static String normalizeEffectId(String rawId) {
    String s = rawId.trim().toLowerCase(Locale.ROOT);
    int colon = s.indexOf(':');
    return colon >= 0 ? s.substring(colon + 1) : s;
  }

  public static String romanAmplifier(int amplifier) {
    int lvl = amplifier + 1;
    if (lvl <= 1) return "";
    if (lvl == 2) return " II";
    if (lvl == 3) return " III";
    if (lvl == 4) return " IV";
    if (lvl == 5) return " V";
    return " " + lvl;
  }

  private static String fallbackName(String key) {
    String[] parts = key.split("_");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].isEmpty()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(Character.toUpperCase(parts[i].charAt(0)));
      if (parts[i].length() > 1) sb.append(parts[i].substring(1));
    }
    return sb.toString();
  }
}
