package dev.nightclient.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FeatureSupport {
  private static volatile String activeGameVersion = "";
  private static volatile Set<String> unsupportedControls = Collections.emptySet();
  private static volatile long revision = 0L;

  private FeatureSupport() {}

  public static long revision() {
    return revision;
  }

  public static String gameVersion() {
    return activeGameVersion;
  }

  public static boolean isControlSupported(String bindPath) {
    if (bindPath == null || bindPath.isEmpty()) return true;
    Set<String> set = unsupportedControls;
    return set.isEmpty() || !set.contains(bindPath);
  }

  public static synchronized void reset() {
    if (activeGameVersion.isEmpty() && unsupportedControls.isEmpty()) return;
    activeGameVersion = "";
    unsupportedControls = Collections.emptySet();
    revision++;
  }

  public static synchronized void configureForGameVersion(String mcVersion) {
    String normalized = mcVersion == null ? "" : mcVersion.trim();
    int[] parsed = parseVersion(normalized);
    Set<String> next;
    if (parsed == null) {
      next = Collections.emptySet();
    } else {
      Set<String> missing = new HashSet<String>();
      int major = parsed[0];
      int minor = parsed[1];
      int patch = parsed[2];

      boolean is12111 = major == 1 && minor == 21 && patch == 11;
      boolean is262 = major == 26 && minor == 2 && patch == 0;
      boolean ge1212 = compare(major, minor, patch, 1, 21, 2) >= 0;
      boolean ge1216 = compare(major, minor, patch, 1, 21, 6) >= 0;
      boolean ge12111 = compare(major, minor, patch, 1, 21, 11) >= 0;
      boolean lt1215 = compare(major, minor, patch, 1, 21, 5) < 0;
      boolean lt262 = compare(major, minor, patch, 26, 2, 0) < 0;
      boolean ge262 = !lt262;

      boolean hasTerrainEngine = is12111 || is262;
      boolean hasFastModel = ge1212;
      boolean hasNativeGpuInstancing = is12111;
      boolean hasNativePbo = lt1215 || (ge12111 && lt262);
      boolean hasRetainedBatching = is12111 || ge262;
      boolean hasHologramTextCache = ge12111;
      boolean hasVanillaHudCache = ge1216;
      boolean hasScoreboardCache = ge1212;

      if (!hasTerrainEngine) {
        missing.add("optimization.undergroundCaveCulling");
        missing.add("optimization.retainedTerrainCache");
      }
      if (!hasFastModel) {
        missing.add("optimization.fastModelRendering");
      }
      if (!hasNativeGpuInstancing) {
        missing.add("optimization.nativeGpuInstancing");
      }
      if (!hasNativePbo) {
        missing.add("optimization.nativePboUploads");
      }
      if (!hasRetainedBatching) {
        missing.add("optimization.staticBlockEntityBatching");
        missing.add("optimization.staticEntityBatching");
      }
      if (!hasHologramTextCache) {
        missing.add("optimization.hologramTextCache");
      }
      if (!hasVanillaHudCache) {
        missing.add("optimization.vanillaHudCache");
      }
      if (!hasScoreboardCache) {
        missing.add("optimization.scoreboardCache");
      }
      next = missing.isEmpty()
          ? Collections.<String>emptySet()
          : Collections.unmodifiableSet(missing);
    }
    if (normalized.equals(activeGameVersion) && next.equals(unsupportedControls)) return;
    activeGameVersion = normalized;
    unsupportedControls = next;
    revision++;
  }

  private static int[] parseVersion(String raw) {
    if (raw == null || raw.isEmpty()) return null;
    String[] parts = raw.split("\\.");
    if (parts.length < 2 || parts.length > 3) return null;
    try {
      int major = Integer.parseInt(parts[0]);
      int minor = Integer.parseInt(parts[1]);
      int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
      return new int[] {major, minor, patch};
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static int compare(int a0, int a1, int a2, int b0, int b1, int b2) {
    if (a0 != b0) return Integer.compare(a0, b0);
    if (a1 != b1) return Integer.compare(a1, b1);
    return Integer.compare(a2, b2);
  }
}
