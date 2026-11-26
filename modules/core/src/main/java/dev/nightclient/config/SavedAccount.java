package dev.nightclient.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SavedAccount {
  public static final int MAX_ACCOUNTS = 256;
  public static final int MAX_NOTE_LENGTH = 32;
  private static final Pattern VALID_NICK = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
  private static final Pattern VALID_ID = Pattern.compile("^[0-9a-f]{1,32}$");

  private static final String[] NICK_PREFIXES = {
      "Aero", "Apex", "Ash", "Blaze", "Bolt", "Cinder", "Cloud", "Cryo", "Dusk", "Echo", "Ember",
      "Fable", "Flux", "Frost", "Gale", "Glint", "Halo", "Haze", "Hex", "Ion", "Jade", "Kite",
      "Lumen", "Lynx", "Mist", "Mono", "Nebula", "Neon", "Nyx", "Onyx", "Orbit", "Prism", "Pyro",
      "Quill", "Rift", "Rune", "Shade", "Shift", "Sky", "Snow", "Solar", "Spark", "Storm", "Swift",
      "Tide", "Trace", "Umbra", "Vex", "Void", "Volt", "Wisp", "Zephyr"
  };

  private static final String[] NICK_SUFFIXES = {
      "Ace", "Blade", "Byte", "Cast", "Core", "Craft", "Dash", "Edge", "Fall", "Fang", "Fire",
      "Fox", "Glow", "Hawk", "Ice", "Jet", "Leaf", "Lite", "Mark", "Moon", "Node", "Nova", "Peak",
      "Ray", "Rush", "Soul", "Star", "Step", "Vibe", "Wave", "Wind", "Wing", "Wolf", "Zero"
  };

  public String id = "";
  public String name = "";
  public String skinName = "";
  public String note = "";
  public boolean pinned;
  public long lastUsedAt;
  public int useCount;

  public SavedAccount() {}

  public SavedAccount(String id, String name, String note, String skinName) {
    this.id = id == null ? "" : id;
    this.name = name == null ? "" : name;
    this.note = note == null ? "" : note;
    this.skinName = skinName == null ? "" : skinName;
    normalize();
  }

  public static String newId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  public static boolean isValidNickname(String value) {
    return normalizeNickname(value) != null;
  }

  public static String normalizeNickname(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return VALID_NICK.matcher(trimmed).matches() ? trimmed : null;
  }

  public static UUID offlineUuid(String name) {
    String normalized = name == null ? "" : name.trim();
    return UUID.nameUUIDFromBytes(
        ("OfflinePlayer:" + normalized).getBytes(StandardCharsets.UTF_8));
  }

  public static String undashedUuid(UUID uuid) {
    return uuid == null ? "" : uuid.toString().replace("-", "");
  }

  public static String shortUuid(UUID uuid) {
    if (uuid == null) return "";
    String text = uuid.toString();
    return text.length() >= 8 ? text.substring(0, 8) : text;
  }

  public static String generateRandomNickname(Random random) {
    Random rng = random == null ? new Random() : random;
    String prefix = NICK_PREFIXES[rng.nextInt(NICK_PREFIXES.length)];
    String suffix = NICK_SUFFIXES[rng.nextInt(NICK_SUFFIXES.length)];
    boolean underscore = rng.nextInt(4) == 0;
    boolean digits = rng.nextInt(3) != 0;
    StringBuilder out = new StringBuilder(16);
    out.append(prefix);
    if (underscore && out.length() + 1 + suffix.length() <= 14) out.append('_');
    out.append(suffix);
    if (digits && out.length() <= 13) {
      int maxDigits = Math.min(3, 16 - out.length());
      int bound = maxDigits >= 3 ? 900 : maxDigits == 2 ? 90 : 9;
      int base = maxDigits >= 3 ? 100 : maxDigits == 2 ? 10 : 1;
      out.append(base + rng.nextInt(bound));
    }
    String result = out.length() > 16 ? out.substring(0, 16) : out.toString();
    return isValidNickname(result) ? result : "NightPlayer" + (100 + rng.nextInt(900));
  }

  public static String cleanNote(String value) {
    if (value == null) return "";
    StringBuilder out = new StringBuilder(Math.min(MAX_NOTE_LENGTH, value.length()));
    for (int i = 0; i < value.length() && out.length() < MAX_NOTE_LENGTH; i++) {
      char c = value.charAt(i);
      if (c >= 32 && c != 127) out.append(c);
    }
    return out.toString().trim();
  }

  public boolean normalize() {
    String cleanName = normalizeNickname(name);
    if (cleanName == null) return false;
    name = cleanName;
    if (id == null || !VALID_ID.matcher(id.trim().toLowerCase(Locale.ROOT)).matches())
      id = newId();
    else
      id = id.trim().toLowerCase(Locale.ROOT);
    String cleanSkin = normalizeNickname(skinName);
    skinName = cleanSkin != null && !cleanSkin.equalsIgnoreCase(name) ? cleanSkin : "";
    note = cleanNote(note);
    if (lastUsedAt < 0L) lastUsedAt = 0L;
    if (useCount < 0) useCount = 0;
    return true;
  }

  public String effectiveSkinName() {
    return skinName != null && !skinName.isEmpty() ? skinName : name;
  }

  public UUID offlineUuid() {
    return offlineUuid(name);
  }

  public SavedAccount copy() {
    SavedAccount copy = new SavedAccount();
    copy.id = id;
    copy.name = name;
    copy.skinName = skinName;
    copy.note = note;
    copy.pinned = pinned;
    copy.lastUsedAt = lastUsedAt;
    copy.useCount = useCount;
    return copy;
  }
}
