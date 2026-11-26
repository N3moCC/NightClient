package dev.nightclient.runtime;

import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExplosionRingLogic {
  public static final float VANILLA_TNT_POWER = 4.0f;
  public static final float VANILLA_CRYSTAL_POWER = 6.0f;
  public static final float VANILLA_ANCHOR_POWER = 5.0f;

  public static final int COLOR_SAFE = 0xff38d9a9;
  public static final int COLOR_DANGER = 0xfff59f00;
  public static final int COLOR_LETHAL = 0xffff4d6d;

  private static final Pattern EXPLICIT_RADIUS =
      Pattern.compile("(?:радиус|radius|rad|дальн\\w*)\\s*[:=\\-]?\\s*(\\d+(?:[.,]\\d+)?)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern AREA_NXN =
      Pattern.compile("(\\d{1,2})\\s*[xх×]\\s*(\\d{1,2})(?:\\s*[xх×]\\s*(\\d{1,2}))?",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern POWER_MULTIPLIER =
      Pattern.compile("(?:^|[\\s(\\[])([xх×]\\s*(\\d+(?:[.,]\\d+)?))(?=$|[\\s)\\]])",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern COLOR_CODES =
      Pattern.compile("(?:§[0-9a-fk-orxA-FK-ORX]|&[0-9a-fk-orA-FK-OR])");
  private static final Pattern VOLATILE_TIMER =
      Pattern.compile("\\[?\\d+(?:[.,]\\d+)?\\s*(?:s|sec|с|сек)\\]?");
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-zа-яё0-9]+");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private ExplosionRingLogic() {}

  public static final class HazardBadge {
    public final double x, y, z;
    public final String label;
    public final float fuseSeconds;
    public final float radius;
    public final float estimatedDamage;
    public final boolean lethal;
    public final boolean learned;
    public final int color;

    public HazardBadge(double x, double y, double z, String label, float fuseSeconds, float radius,
        float estimatedDamage, boolean lethal, boolean learned, int color) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.label = label == null ? "" : label;
      this.fuseSeconds = fuseSeconds;
      this.radius = radius;
      this.estimatedDamage = estimatedDamage;
      this.lethal = lethal;
      this.learned = learned;
      this.color = color;
    }
  }

  public static final class TntSpec {
    public final float radius;
    public final float power;
    public final float damageMultiplier;
    public final String label;
    public final String signature;
    public final boolean custom;

    public TntSpec(
        float radius, float power, float damageMultiplier, String label, String signature, boolean custom) {
      this.radius = clamp(radius, 3.0f, 24.0f);
      this.power = clamp(power, 1.5f, 12.0f);
      this.damageMultiplier = clamp(damageMultiplier, 0.5f, 4.0f);
      this.label = label == null ? "" : label;
      this.signature = signature == null || signature.isEmpty() ? "vanilla" : signature;
      this.custom = custom;
    }
  }

  public static String stripColors(String text) {
    if (text == null || text.isEmpty()) return "";
    return COLOR_CODES.matcher(text).replaceAll("").trim();
  }

  public static String normalizeSignature(String raw) {
    String clean = stripColors(raw).toLowerCase(Locale.ROOT);
    if (clean.isEmpty()) return "vanilla";
    clean = VOLATILE_TIMER.matcher(clean).replaceAll(" ");
    clean = NON_ALNUM.matcher(clean).replaceAll(" ").trim();
    if (clean.isEmpty() || "tnt".equals(clean) || "динамита".equals(clean) || "динамит".equals(clean)
        || "primed tnt".equals(clean)) {
      return "vanilla";
    }
    return clean.length() > 40 ? clean.substring(0, 40).trim() : clean;
  }

  public static TntSpec inferFromText(String name, String extraText) {
    String cleanName = stripColors(name);
    String cleanExtra = stripColors(extraText);
    String combined = (cleanName + " " + cleanExtra).trim();
    String lower = combined.toLowerCase(Locale.ROOT);
    String sig = normalizeSignature(cleanName.isEmpty() ? cleanExtra : cleanName);

    if (lower.isEmpty()) {
      return new TntSpec(VANILLA_TNT_POWER * 2.0f, VANILLA_TNT_POWER, 1.0f, "TNT", "vanilla", false);
    }

    Matcher radMatch = EXPLICIT_RADIUS.matcher(lower);
    if (radMatch.find()) {
      float r = parseFloat(radMatch.group(1), 8.0f);
      if (r >= 2.0f && r <= 32.0f) {
        float clamped = clamp(r, 3.0f, 24.0f);
        String lbl = cleanName.isEmpty() ? ("TNT R" + Math.round(clamped)) : trimLabel(cleanName);
        return new TntSpec(clamped, clamped * 0.5f, clamped > 9.0f ? 1.25f : 1.0f, lbl, sig, true);
      }
    }

    if (lower.contains("взрывная волна") || lower.contains("ударная волна")
        || lower.contains("blast wave") || lower.contains("shockwave")) {
      return new TntSpec(12.0f, 6.0f, 1.35f, "explosionRing.label.blast_wave", "blast_wave", true);
    }
    if (lower.contains("c4") || lower.contains("с4") || lower.contains("си-4")
        || lower.contains("си4") || lower.contains("пластид")) {
      return new TntSpec(10.0f, 5.0f, 1.25f, "C4", "c4", true);
    }
    if (lower.contains("ядер") || lower.contains("nuke") || lower.contains("атом")
        || lower.contains("mega") || lower.contains("мега")
        || lower.contains("тир 3") || lower.contains("tier 3") || lower.contains("lvl 3")
        || lower.contains("3 уровень") || lower.contains("iii")) {
      return new TntSpec(14.0f, 7.0f, 1.5f, "explosionRing.label.tier3",
          sig.equals("vanilla") ? "tier3" : sig, true);
    }
    if (lower.contains("тир 2") || lower.contains("tier 2") || lower.contains("lvl 2")
        || lower.contains("2 уровень") || lower.contains("усилен") || lower.contains("black tnt")
        || lower.contains("черн")) {
      return new TntSpec(10.5f, 5.25f, 1.2f, "explosionRing.label.tier2",
          sig.equals("vanilla") ? "tier2" : sig, true);
    }
    if (lower.contains("тир 1") || lower.contains("tier 1") || lower.contains("lvl 1")
        || lower.contains("1 уровень")) {
      return new TntSpec(9.0f, 4.5f, 1.05f, "explosionRing.label.tier1",
          sig.equals("vanilla") ? "tier1" : sig, true);
    }
    if (lower.contains("обсидиан") || lower.contains("obsidian") || lower.contains("рейд")
        || lower.contains("raid") || lower.contains("разруш") || lower.contains("детонатор")) {
      return new TntSpec(10.0f, 5.0f, 1.2f,
          cleanName.isEmpty() ? "explosionRing.label.raid_tnt" : trimLabel(cleanName),
          sig.equals("vanilla") ? "raid_tnt" : sig, true);
    }

    Matcher areaMatch = AREA_NXN.matcher(lower);
    if (areaMatch.find()) {
      float a = parseFloat(areaMatch.group(1), 0f);
      float b = parseFloat(areaMatch.group(2), 0f);
      float span = Math.max(a, b);
      if (span >= 5.0f && span <= 40.0f) {
        float r = clamp(span * 0.65f, 4.0f, 24.0f);
        return new TntSpec(r, r * 0.5f, span >= 13.0f ? 1.3f : 1.1f,
            trimLabel(cleanName.isEmpty() ? (Math.round(a) + "x" + Math.round(b)) : cleanName), sig, true);
      }
    }

    Matcher multMatch = POWER_MULTIPLIER.matcher(lower);
    if (multMatch.find()) {
      float m = parseFloat(multMatch.group(2), 1.0f);
      if (m > 1.05f && m <= 6.0f) {
        float power = clamp(VANILLA_TNT_POWER * (float) Math.sqrt(m), 4.0f, 12.0f);
        return new TntSpec(power * 2.0f, power, clamp((float) Math.sqrt(m), 1.0f, 2.5f),
            trimLabel(cleanName.isEmpty() ? ("TNT x" + (m == (int) m ? Integer.toString((int) m) : Float.toString(m))) : cleanName),
            sig, true);
      }
    }

    boolean hasCustomName = !sig.equals("vanilla");
    return new TntSpec(VANILLA_TNT_POWER * 2.0f, VANILLA_TNT_POWER, 1.0f,
        hasCustomName ? trimLabel(cleanName) : "TNT", sig, hasCustomName);
  }

  public static String localizeLabel(String rawLabel, String language) {
    if (rawLabel == null || rawLabel.isEmpty()) return "";
    if (rawLabel.startsWith("explosionRing.label.")) {
      return I18n.text(language, rawLabel);
    }
    if ("Crystal".equals(rawLabel)) return I18n.text(language, "explosionRing.label.crystal");
    if ("Anchor".equals(rawLabel)) return I18n.text(language, "explosionRing.label.anchor");
    return rawLabel;
  }

  private static String trimLabel(String raw) {
    if (raw == null) return "TNT";
    String s = WHITESPACE.matcher(raw).replaceAll(" ").trim();
    if (s.isEmpty()) return "TNT";
    return s.length() > 16 ? s.substring(0, 15).trim() + "…" : s;
  }

  public static float estimateBlastDamage(double distance, float power, float exposure, float armor,
      float toughness, int epf, int resistanceAmplifier, float damageMultiplier) {
    float diameter = Math.max(1.0f, power * 2.0f);
    if (distance >= diameter || exposure <= 0.0f) return 0.0f;
    double distFrac = Math.max(0.0, Math.min(1.0, distance / diameter));
    double impact = (1.0 - distFrac) * Math.max(0.0, Math.min(1.0, exposure));
    float raw = (float) ((impact * impact + impact) * 0.5 * 7.0 * diameter + 1.0);
    raw *= clamp(damageMultiplier, 0.25f, 5.0f);
    if (raw <= 0.0f) return 0.0f;

    float f = 2.0f + Math.max(0.0f, toughness) * 0.25f;
    float defense = clamp(Math.max(armor * 0.2f, armor - raw / f), 0.0f, 20.0f);
    float afterArmor = raw * (1.0f - defense / 25.0f);

    float afterResist = afterArmor;
    if (resistanceAmplifier >= 0) {
      int level = Math.min(5, resistanceAmplifier + 1);
      afterResist *= Math.max(0.0f, 1.0f - 0.2f * level);
    }

    float clampedEpf = clamp(epf, 0.0f, 20.0f);
    return Math.max(0.0f, afterResist * (1.0f - clampedEpf / 25.0f));
  }

  public static float computeLethalRadius(float power, float playerEffectiveHp, float armor,
      float toughness, int epf, int resistanceAmplifier, float damageMultiplier) {
    if (playerEffectiveHp <= 0.0f || power <= 0.1f) return 0.0f;
    float pointBlank = estimateBlastDamage(
        0.0, power, 1.0f, armor, toughness, epf, resistanceAmplifier, damageMultiplier);
    if (pointBlank < playerEffectiveHp) return 0.0f;
    float lo = 0.0f;
    float hi = power * 2.0f;
    for (int i = 0; i < 14; i++) {
      float mid = (lo + hi) * 0.5f;
      float dmg = estimateBlastDamage(
          mid, power, 1.0f, armor, toughness, epf, resistanceAmplifier, damageMultiplier);
      if (dmg >= playerEffectiveHp) lo = mid;
      else hi = mid;
    }
    return lo;
  }

  public static int threatColor(
      double distance, float dangerRadius, float lethalRadius, float damage, float playerEffectiveHp) {
    if (damage >= playerEffectiveHp * 0.92f || (lethalRadius > 0.1f && distance <= lethalRadius)) {
      return COLOR_LETHAL;
    }
    if (distance <= dangerRadius) {
      return COLOR_DANGER;
    }
    return COLOR_SAFE;
  }

  public static int resolveGlintColor(Settings.EnchantGlint cfg, int themeAccent, double timeSeconds) {
    if (cfg == null) return 0xff8c52ff;
    String mode = cfg.colorMode == null ? "custom" : cfg.colorMode;
    if ("theme".equals(mode)) return (themeAccent & 0x00ffffff) | 0xff000000;
    if ("chroma".equals(mode)) {
      float hue = (float) ((timeSeconds * 0.14 * Math.max(0.25f, cfg.speed)) % 1.0);
      if (hue < 0f) hue += 1f;
      return hsvToArgb(hue, 0.72f, 1.0f);
    }
    if ("preset".equals(mode)) {
      String p = cfg.preset == null ? "amethyst" : cfg.preset;
      if ("crimson".equals(p)) return 0xffff3b6b;
      if ("cyan".equals(p)) return 0xff38e2ff;
      if ("gold".equals(p)) return 0xffffc845;
      if ("emerald".equals(p)) return 0xff34e89e;
      if ("sakura".equals(p)) return 0xffff8cc6;
      if ("white".equals(p)) return 0xfff0f5ff;
      return 0xff9d6bff;
    }
    return (cfg.color & 0x00ffffff) | 0xff000000;
  }

  private static int hsvToArgb(float h, float s, float v) {
    float c = v * s;
    float x = c * (1.0f - Math.abs((h * 6.0f) % 2.0f - 1.0f));
    float m = v - c;
    float r = 0, g = 0, b = 0;
    float h6 = h * 6.0f;
    if (h6 < 1f) { r = c; g = x; }
    else if (h6 < 2f) { r = x; g = c; }
    else if (h6 < 3f) { g = c; b = x; }
    else if (h6 < 4f) { g = x; b = c; }
    else if (h6 < 5f) { r = x; b = c; }
    else { r = c; b = x; }
    int ri = Math.round(clamp(r + m, 0f, 1f) * 255f);
    int gi = Math.round(clamp(g + m, 0f, 1f) * 255f);
    int bi = Math.round(clamp(b + m, 0f, 1f) * 255f);
    return 0xff000000 | (ri << 16) | (gi << 8) | bi;
  }

  public static final class ServerExplosionLearner {
    private static final class TrackedTnt {
      double x, y, z;
      String signature;
      long updatedMs;
    }

    private String currentScope = "";
    private final Map<Integer, TrackedTnt> tracked = new LinkedHashMap<Integer, TrackedTnt>();
    private final Map<String, Float> learnedRadius =
        new LinkedHashMap<String, Float>(32, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
            return size() > 128;
          }
        };

    public synchronized void syncScope(String scope) {
      String norm = scope == null ? "" : scope;
      if (!norm.equals(currentScope)) {
        currentScope = norm;
        tracked.clear();
        learnedRadius.clear();
      }
    }

    public synchronized void clear() {
      tracked.clear();
      learnedRadius.clear();
      currentScope = "";
    }

    public synchronized void trackTnt(
        int entityId, double x, double y, double z, String signature, long nowMs) {
      TrackedTnt entry = tracked.get(entityId);
      if (entry == null) {
        if (tracked.size() >= 96) {
          Iterator<Integer> it = tracked.keySet().iterator();
          if (it.hasNext()) {
            it.next();
            it.remove();
          }
        }
        entry = new TrackedTnt();
        tracked.put(entityId, entry);
      }
      entry.x = x;
      entry.y = y;
      entry.z = z;
      entry.signature = signature == null || signature.isEmpty() ? "vanilla" : signature;
      entry.updatedMs = nowMs;
    }

    public synchronized float recordExplosion(
        double ex, double ey, double ez, float packetPower, float maxBlockDist, long nowMs) {
      for (Iterator<Map.Entry<Integer, TrackedTnt>> it = tracked.entrySet().iterator();
           it.hasNext();) {
        if (nowMs - it.next().getValue().updatedMs > 3000L) it.remove();
      }
      float observedRadius = -1.0f;
      if (packetPower > 0.1f) {
        observedRadius = Math.max(packetPower * 2.0f, maxBlockDist);
      } else if (maxBlockDist > 1.5f) {
        observedRadius = Math.max(8.0f, maxBlockDist * 1.4f);
      }
      if (observedRadius <= 0.1f) return -1.0f;
      observedRadius = clamp(observedRadius, 4.0f, 24.0f);

      Integer bestId = null;
      TrackedTnt best = null;
      double bestDistSq = 9.0;
      for (Map.Entry<Integer, TrackedTnt> row : tracked.entrySet()) {
        TrackedTnt t = row.getValue();
        double dx = t.x - ex;
        double dy = t.y - ey;
        double dz = t.z - ez;
        double dSq = dx * dx + dy * dy + dz * dz;
        if (dSq < bestDistSq) {
          bestDistSq = dSq;
          bestId = row.getKey();
          best = t;
        }
      }
      if (best == null) return -1.0f;
      tracked.remove(bestId);

      if (!"vanilla".equals(best.signature) || Math.abs(observedRadius - 8.0f) >= 0.75f) {
        Float prev = learnedRadius.get(best.signature);
        float next = prev == null ? observedRadius : (prev * 0.35f + observedRadius * 0.65f);
        learnedRadius.put(best.signature, clamp(next, 4.0f, 24.0f));
        return next;
      }
      return observedRadius;
    }

    public synchronized float resolveRadius(
        String signature, float inferredRadius, float manualOverride, boolean autoLearn) {
      if (manualOverride >= 3.0f) return clamp(manualOverride, 3.0f, 24.0f);
      if (autoLearn) {
        String key = signature == null || signature.isEmpty() ? "vanilla" : signature;
        Float learned = learnedRadius.get(key);
        if (learned != null) return learned;
      }
      return clamp(inferredRadius, 3.0f, 24.0f);
    }

    public synchronized boolean hasLearned(String signature) {
      String key = signature == null || signature.isEmpty() ? "vanilla" : signature;
      return learnedRadius.containsKey(key);
    }
  }

  private static float parseFloat(String raw, float fallback) {
    if (raw == null || raw.isEmpty()) return fallback;
    try {
      return Float.parseFloat(raw.replace(',', '.'));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static float clamp(float value, float min, float max) {
    if (Float.isNaN(value) || Float.isInfinite(value)) return min;
    return Math.max(min, Math.min(max, value));
  }
}
