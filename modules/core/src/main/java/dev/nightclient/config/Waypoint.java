package dev.nightclient.config;

import java.util.Locale;

public final class Waypoint {
  public static final int LABEL_LIMIT = 32;
  public static final int WORLD_LIMIT = 160;
  public static final int WORLD_LABEL_LIMIT = 64;
  public static final double COORDINATE_LIMIT = 30_000_000;
  public static final String DEFAULT_DIMENSION = "minecraft:overworld";

  public String id = "";
  public String label = "";
  public double x, y, z;
  public int color = 0xffffffff;

  public String dimension = DEFAULT_DIMENSION;

  public String world = "";

  public String worldLabel = "";

  public String icon = "";

  public boolean visible = true;

  public boolean death = false;

  public boolean temp = false;

  public Waypoint() {}

  public Waypoint(String label, double x, double y, double z) {
    this.label = label;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public Waypoint(Waypoint source) {
    if (source != null) {
      id = source.id;
      label = source.label;
      x = source.x;
      y = source.y;
      z = source.z;
      color = source.color;
      dimension = source.dimension;
      world = source.world;
      worldLabel = source.worldLabel;
      icon = source.icon;
      visible = source.visible;
      death = source.death;
      temp = source.temp;
    }
  }

  public static String newId() {
    return java.util.UUID.randomUUID().toString().substring(0, 8);
  }

  public static String normalizeWorld(String value) {
    String normalized = tidy(value).toLowerCase(Locale.ROOT);
    if (normalized.length() > WORLD_LIMIT) normalized = normalized.substring(0, WORLD_LIMIT);
    return normalized.matches("[a-z0-9_.:/@-]{0,160}") ? normalized : "";
  }

  public static String normalizeWorldLabel(String value) {
    return clean(value, WORLD_LABEL_LIMIT);
  }

  public boolean matchesWorld(String currentWorld) {
    String mine = normalizeWorld(world);
    String target = normalizeWorld(currentWorld);
    if (mine.equals(target)) return true;
    if (mine.isEmpty() || target.isEmpty()) return false;
    return legacySeedMatchesRealm(mine, target) || legacySeedMatchesRealm(target, mine);
  }

  private static boolean legacySeedMatchesRealm(String legacy, String realm) {
    if (!legacy.startsWith("mp:") || !realm.startsWith("mp:")) return false;
    int sAt = legacy.indexOf("/s:");
    int rAt = realm.indexOf("/r:");
    if (sAt <= 3 || rAt <= 3) return false;
    if (!legacy.substring(0, sAt).equals(realm.substring(0, rAt))) return false;
    String seedHex = legacy.substring(sAt + 3);
    int uAt = realm.lastIndexOf('_');
    if (uAt <= rAt + 3) return false;
    String seedSuffix = realm.substring(uAt + 1);
    if (seedHex.length() < 6 || seedSuffix.length() < 6) return false;
    int cmp = Math.min(seedHex.length(), seedSuffix.length());
    return cmp >= 6 && seedHex.regionMatches(0, seedSuffix, 0, cmp);
  }

  public String displayWorld() {
    String pretty = worldLabel == null ? "" : worldLabel.trim();
    if (!pretty.isEmpty()) return pretty;
    String idKey = normalizeWorld(world);
    if (idKey.isEmpty()) return "";
    if (idKey.startsWith("sp:")) return idKey.substring(3);
    if (idKey.startsWith("mp:")) {
      String rest = idKey.substring(3);
      int realmAt = rest.indexOf("/r:");
      if (realmAt > 0) {
        String host = rest.substring(0, realmAt);
        String realm = rest.substring(realmAt + 3);
        int u = realm.lastIndexOf('_');
        if (u > 0 && realm.length() - u - 1 == 8) realm = realm.substring(0, u);
        return host + " · " + realm;
      }
      int pktAt = rest.indexOf("/pkt:");
      if (pktAt > 0) {
        return rest.substring(0, pktAt) + " · #" + rest.substring(pktAt + 5);
      }
      int mwAt = rest.indexOf("/mw:");
      if (mwAt > 0) {
        String sub = rest.substring(mwAt + 4);
        if (sub.length() > 8) sub = sub.substring(0, 8);
        return rest.substring(0, mwAt) + " · #" + sub;
      }
      int seedAt = rest.indexOf("/s:");
      if (seedAt > 0) {
        String host = rest.substring(0, seedAt);
        String hex = rest.substring(seedAt + 3);
        if (hex.length() > 6) hex = hex.substring(0, 6);
        return host + " · #" + hex;
      }
      int spawnAt = rest.indexOf("/p:");
      if (spawnAt > 0) return rest.substring(0, spawnAt);
      return rest;
    }
    return idKey;
  }

  public String displayName() {
    String name = label == null ? "" : label.trim();
    return name.isEmpty() ? coordinates() : name;
  }

  public String coordinates() {
    return Math.round(x) + " " + Math.round(y) + " " + Math.round(z);
  }

  public double distanceTo(double ox, double oy, double oz) {
    double dx = x - ox, dy = y - oy, dz = z - oz;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public boolean normalize() {
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
    x = clamp(x, -COORDINATE_LIMIT, COORDINATE_LIMIT);
    y = clamp(y, -COORDINATE_LIMIT, COORDINATE_LIMIT);
    z = clamp(z, -COORDINATE_LIMIT, COORDINATE_LIMIT);
    label = clean(label, LABEL_LIMIT);
    if (id == null || !id.matches("[0-9a-f]{1,32}")) id = newId();
    if ((color >>> 24) == 0) color = color | 0xff000000;
    icon = tidy(icon).toLowerCase(Locale.ROOT);
    if (!icon.matches("[a-z0-9_.:/-]{0,64}")) icon = "";
    dimension = tidy(dimension).toLowerCase(Locale.ROOT);
    if (!dimension.matches("[a-z0-9_:/.-]{1,64}")) dimension = DEFAULT_DIMENSION;
    world = normalizeWorld(world);
    worldLabel = normalizeWorldLabel(worldLabel);
    return true;
  }

  private static String clean(String value, int limit) {
    String normalized = tidy(value);
    if (normalized.length() > limit) normalized = normalized.substring(0, limit);
    if (!normalized.isEmpty()
        && Character.isHighSurrogate(normalized.charAt(normalized.length() - 1)))
      normalized = normalized.substring(0, normalized.length() - 1);
    return normalized;
  }

  private static String tidy(String value) {
    return value == null ? "" : value.replaceAll("[\\p{Cntrl}§]", "").trim();
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
