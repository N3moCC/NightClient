package dev.nightclient.renderer.map;

import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class WorldIdentityEngine {
  public enum State {
    NONE,
    PROVISIONAL,
    LOCKED
  }

  public static final int PROVISIONAL_SETTLE_TICKS = 28;
  public static final int MIN_FINGERPRINT_CHUNKS = 3;
  public static final float TERRAIN_MATCH_THRESHOLD = 0.80f;

  private static final Pattern LEADING_DECOR =
      Pattern.compile("^[\\s▍▎▏▌▋▊▉█•·»›|:–—-]+");
  private static final Pattern HASH_NUMBER = Pattern.compile(".*#\\s*\\d+.*");
  private static final Pattern DIMENSION_PATTERN = Pattern.compile("[a-z0-9_:/.-]{1,64}");
  private static final String[] REALM_PREFIXES = {
      "режим:", "анархия:", "гриф:", "сервер:", "лобби:", "хаб:", "мир:",
      "mode:", "realm:", "server:", "lobby:", "hub:", "world:", "anarchy:"
  };

  private final DiskRegionStore store;

  private State state = State.NONE;
  private boolean singleplayer = false;
  private String serverKey = "";
  private String serverHost = "";
  private String dimension = Waypoint.DEFAULT_DIMENSION;
  private long hashedSeed = 0L;
  private String seedHex = "";
  private String spawnKey = "";

  private String packetLevelId = "";
  private String realmRawLabel = "";
  private String realmSlug = "";

  private String worldId = "";
  private String subWorldDir = "";
  private String worldLabel = "";

  private int provisionalTicks = 0;
  private long sessionId = 0L;
  private DiskRegionStore.ServerWorldIndex serverIndex = new DiskRegionStore.ServerWorldIndex();

  public WorldIdentityEngine(DiskRegionStore store) {
    this.store = store;
  }

  public synchronized State state() {
    return state;
  }

  public synchronized boolean isLocked() {
    return state == State.LOCKED;
  }

  public synchronized boolean isActive() {
    return state != State.NONE;
  }

  public synchronized long sessionId() {
    return sessionId;
  }

  public synchronized String serverKey() {
    return serverKey;
  }

  public synchronized String subWorldDir() {
    return subWorldDir;
  }

  public synchronized String dimension() {
    return dimension;
  }

  public synchronized String worldId() {
    return worldId;
  }

  public synchronized String worldLabel() {
    return worldLabel;
  }

  public synchronized List<DiskRegionStore.SubWorldRecord> knownSubWorlds() {
    if (serverIndex == null || serverIndex.worlds == null || serverIndex.worlds.isEmpty())
      return Collections.emptyList();
    List<DiskRegionStore.SubWorldRecord> list =
        new ArrayList<DiskRegionStore.SubWorldRecord>(serverIndex.worlds.values());
    Collections.sort(list, (a, b) -> Long.compare(b.lastSeenMs, a.lastSeenMs));
    return list;
  }

  public synchronized void clear() {
    state = State.NONE;
    singleplayer = false;
    serverKey = "";
    serverHost = "";
    dimension = Waypoint.DEFAULT_DIMENSION;
    hashedSeed = 0L;
    seedHex = "";
    spawnKey = "";
    packetLevelId = "";
    realmRawLabel = "";
    realmSlug = "";
    worldId = "";
    subWorldDir = "";
    worldLabel = "";
    provisionalTicks = 0;
    serverIndex = new DiskRegionStore.ServerWorldIndex();
  }

  public synchronized void resetIndex() {
    serverIndex = new DiskRegionStore.ServerWorldIndex();
  }

  public synchronized boolean beginSession(boolean isSingleplayer, String rawServerHost,
      String spFolder, String spDisplayName, String dim, long seed, String rawSpawnKey) {
    String normDim = normalizeDimension(dim);
    String normHost = normalizeHost(rawServerHost);
    String normSeedHex = seed == 0L ? "" : Long.toHexString(seed);
    String normSpawn = rawSpawnKey == null ? "" : rawSpawnKey.trim();

    if (isSingleplayer) {
      String folderSlug = sanitizeSlug(spFolder);
      if (folderSlug.isEmpty()) folderSlug = "default";
      String nextWorldId = Waypoint.normalizeWorld("sp:" + folderSlug);
      String nextLabel = (spDisplayName != null && !spDisplayName.trim().isEmpty())
          ? spDisplayName.trim()
          : folderSlug;
      if (state == State.LOCKED && singleplayer && nextWorldId.equals(worldId)
          && normDim.equals(dimension)) {
        return false;
      }
      sessionId++;
      singleplayer = true;
      serverKey = "sp_" + folderSlug;
      serverHost = folderSlug;
      dimension = normDim;
      hashedSeed = seed;
      seedHex = normSeedHex;
      spawnKey = normSpawn;
      packetLevelId = "";
      realmRawLabel = "";
      realmSlug = "";
      worldId = nextWorldId;
      subWorldDir = "world";
      worldLabel = nextLabel;
      state = State.LOCKED;
      provisionalTicks = 0;
      serverIndex = store != null ? store.loadServerIndex(serverKey)
                                  : new DiskRegionStore.ServerWorldIndex();
      recordActiveSubWorld(null);
      return true;
    }

    sessionId++;
    singleplayer = false;
    serverHost = normHost.isEmpty() ? "server" : normHost;
    serverKey = "mp_" + serverHost;
    dimension = normDim;
    hashedSeed = seed;
    seedHex = normSeedHex;
    spawnKey = normSpawn;
    packetLevelId = "";
    realmRawLabel = "";
    realmSlug = "";
    provisionalTicks = 0;
    state = State.PROVISIONAL;
    serverIndex = store != null ? store.loadServerIndex(serverKey)
                                : new DiskRegionStore.ServerWorldIndex();

    if (!seedHex.isEmpty()) {
      worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/s:" + seedHex);
      subWorldDir = "s_" + seedHex;
      worldLabel = serverHost + " · #" + (seedHex.length() > 6 ? seedHex.substring(0, 6) : seedHex);
    } else if (!spawnKey.isEmpty()) {
      worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/p:" + spawnKey);
      subWorldDir = "p_" + spawnKey;
      worldLabel = serverHost;
    } else {
      worldId = Waypoint.normalizeWorld("mp:" + serverHost);
      subWorldDir = "default";
      worldLabel = serverHost;
    }
    return true;
  }

  public synchronized boolean feedServerLevelId(String rawLevelId) {
    if (state == State.NONE || singleplayer || rawLevelId == null || rawLevelId.trim().isEmpty())
      return false;
    String slug = sanitizeSlug(rawLevelId);
    if (slug.isEmpty()) return false;
    if (slug.equals(packetLevelId) && state == State.LOCKED) return false;
    packetLevelId = slug;
    worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/pkt:" + slug);
    subWorldDir = "pkt_" + slug;
    worldLabel = serverHost + " · " + rawLevelId.trim();
    state = State.LOCKED;
    recordActiveSubWorld(null);
    return true;
  }

  public synchronized void feedSpawnKey(String rawSpawnKey) {
    if (state == State.NONE || rawSpawnKey == null || rawSpawnKey.trim().isEmpty()) return;
    if (spawnKey.isEmpty() || "0_0_0".equals(spawnKey)) {
      spawnKey = rawSpawnKey.trim();
    }
  }

  public synchronized boolean feedRealmLabel(String rawRealm) {
    if (state == State.NONE || singleplayer || !packetLevelId.isEmpty()) return false;
    if (rawRealm == null) return false;
    String cleaned = rawRealm.trim();
    if (cleaned.length() < 2) return false;
    String slug = sanitizeSlug(cleaned);
    if (slug.isEmpty()) return false;
    if (slug.equals(realmSlug) && state == State.LOCKED) return false;

    realmRawLabel = cleaned;
    realmSlug = slug;
    String seedTail = "";
    if (!seedHex.isEmpty()) {
      seedTail = "_" + (seedHex.length() > 8 ? seedHex.substring(0, 8) : seedHex);
    }
    worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/r:" + slug + seedTail);
    subWorldDir = "r_" + slug + seedTail;
    worldLabel = serverHost + " · " + cleaned;
    state = State.LOCKED;
    recordActiveSubWorld(null);
    return true;
  }

  public synchronized boolean tickProvisional(List<String> terrainSamples) {
    if (state != State.PROVISIONAL) return false;
    provisionalTicks++;
    boolean hasSamples = terrainSamples != null && terrainSamples.size() >= 12;
    if (provisionalTicks < PROVISIONAL_SETTLE_TICKS
        && !(hasSamples && provisionalTicks >= 16)) {
      return false;
    }
    resolveTier3(terrainSamples);
    return state == State.LOCKED;
  }

  public synchronized boolean forceLockIfProvisional(List<String> terrainSamples) {
    if (state != State.PROVISIONAL) return false;
    resolveTier3(terrainSamples);
    return state == State.LOCKED;
  }

  private void resolveTier3(List<String> terrainSamples) {
    List<String> samples = terrainSamples == null ? Collections.<String>emptyList() : terrainSamples;
    DiskRegionStore.SubWorldRecord bestMatch = null;
    float bestScore = 0f;
    boolean seedHasConflictingWorld = false;

    if (serverIndex != null && serverIndex.worlds != null) {
      for (DiskRegionStore.SubWorldRecord rec : serverIndex.worlds.values()) {
        if (rec == null || rec.worldId == null || rec.worldId.isEmpty()) continue;
        if (!dimension.equalsIgnoreCase(rec.dimension)) continue;
        boolean sameSeed = seedHex.equalsIgnoreCase(rec.seedHex == null ? "" : rec.seedHex);
        if (!samples.isEmpty() && rec.terrainSamples != null && !rec.terrainSamples.isEmpty()) {
          float similarity = terrainSimilarity(samples, rec.terrainSamples);
          if (similarity >= TERRAIN_MATCH_THRESHOLD && similarity > bestScore) {
            bestScore = similarity;
            bestMatch = rec;
          } else if (sameSeed && similarity >= 0f && similarity < 0.35f) {
            seedHasConflictingWorld = true;
          }
        }
      }
    }

    if (bestMatch != null) {
      worldId = Waypoint.normalizeWorld(bestMatch.worldId);
      subWorldDir = bestMatch.dirName == null || bestMatch.dirName.isEmpty()
          ? DiskRegionStore.sanitizeDirSegment(worldId)
          : bestMatch.dirName;
      worldLabel = (bestMatch.label == null || bestMatch.label.isEmpty())
          ? serverHost
          : bestMatch.label;
      state = State.LOCKED;
      recordActiveSubWorld(samples);
      return;
    }

    if (!seedHex.isEmpty() && !seedHasConflictingWorld) {
      worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/s:" + seedHex);
      subWorldDir = "s_" + seedHex;
      worldLabel = serverHost + " · #" + (seedHex.length() > 6 ? seedHex.substring(0, 6) : seedHex);
    } else if (!samples.isEmpty()) {
      String sigHash = hashSamples(samples);
      worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/mw:" + sigHash);
      subWorldDir = "mw_" + sigHash;
      worldLabel = serverHost + " · #" + sigHash.substring(0, Math.min(6, sigHash.length()));
    } else if (!spawnKey.isEmpty()) {
      worldId = Waypoint.normalizeWorld("mp:" + serverHost + "/p:" + spawnKey);
      subWorldDir = "p_" + spawnKey;
      worldLabel = serverHost;
    } else {
      worldId = Waypoint.normalizeWorld("mp:" + serverHost);
      subWorldDir = "default";
      worldLabel = serverHost;
    }
    state = State.LOCKED;
    recordActiveSubWorld(samples);
  }

  public synchronized boolean selectManualSubWorld(String targetWorldId, String customLabel) {
    if (state == State.NONE) return false;
    String norm = Waypoint.normalizeWorld(targetWorldId);
    if (norm.isEmpty()) return false;
    DiskRegionStore.SubWorldRecord existing =
        serverIndex != null && serverIndex.worlds != null ? serverIndex.worlds.get(norm) : null;
    worldId = norm;
    if (existing != null && existing.dirName != null && !existing.dirName.isEmpty()) {
      subWorldDir = existing.dirName;
      worldLabel = (customLabel != null && !customLabel.trim().isEmpty())
          ? customLabel.trim()
          : existing.label;
    } else {
      subWorldDir = DiskRegionStore.sanitizeDirSegment(norm);
      worldLabel = (customLabel != null && !customLabel.trim().isEmpty())
          ? customLabel.trim()
          : serverHost;
    }
    state = State.LOCKED;
    recordActiveSubWorld(null);
    return true;
  }

  public synchronized void enrichTerrainSamples(List<String> samples) {
    if (state != State.LOCKED || samples == null || samples.isEmpty()) return;
    DiskRegionStore.SubWorldRecord rec = serverIndex.worlds.get(worldId);
    if (rec != null && rec.terrainSamples != null && rec.terrainSamples.size() >= 32) return;
    recordActiveSubWorld(samples);
  }

  private void recordActiveSubWorld(List<String> samples) {
    if (worldId.isEmpty() || serverKey.isEmpty()) return;
    if (serverIndex == null) serverIndex = new DiskRegionStore.ServerWorldIndex();
    if (serverIndex.worlds == null)
      serverIndex.worlds = new LinkedHashMap<String, DiskRegionStore.SubWorldRecord>();
    DiskRegionStore.SubWorldRecord rec = serverIndex.worlds.get(worldId);
    if (rec == null) {
      rec = new DiskRegionStore.SubWorldRecord();
      rec.worldId = worldId;
      serverIndex.worlds.put(worldId, rec);
    }
    rec.dirName = subWorldDir;
    rec.label = worldLabel;
    rec.seedHex = seedHex;
    rec.spawnKey = spawnKey;
    rec.dimension = dimension;
    rec.lastSeenMs = System.currentTimeMillis();
    if (samples != null && !samples.isEmpty()) {
      if (rec.terrainSamples == null) rec.terrainSamples = new ArrayList<String>();
      if (rec.terrainSamples.size() < 48) {
        for (String s : samples) {
          if (rec.terrainSamples.size() >= 48) break;
          if (!rec.terrainSamples.contains(s)) rec.terrainSamples.add(s);
        }
      }
    }
    if (store != null && store.isAvailable()) {
      store.saveServerIndex(serverKey, serverIndex);
    }
  }

  public synchronized int upgradeLegacyWaypoints(Settings.Waypoints waypoints) {
    if (state != State.LOCKED || waypoints == null || waypoints.points == null
        || worldId.isEmpty() || !worldId.contains("/r:")) {
      return 0;
    }
    int upgraded = 0;
    for (int i = 0; i < waypoints.points.size(); i++) {
      Waypoint wp = waypoints.points.get(i);
      if (wp == null || wp.world == null || !wp.world.contains("/s:")) continue;
      if (wp.matchesWorld(worldId)) {
        wp.world = worldId;
        if (wp.worldLabel == null || wp.worldLabel.trim().isEmpty()) {
          wp.worldLabel = Waypoint.normalizeWorldLabel(worldLabel);
        }
        upgraded++;
      }
    }
    return upgraded;
  }

  public static String extractRealmLabel(List<String> rawLines) {
    if (rawLines == null || rawLines.isEmpty()) return "";
    String hashCandidate = "";
    String modeCandidate = "";
    for (int i = 0; i < rawLines.size(); i++) {
      String raw = rawLines.get(i);
      if (raw == null || raw.isEmpty()) continue;
      String clean = LEADING_DECOR.matcher(stripMcFormatting(raw)).replaceFirst("").trim();
      if (clean.length() < 2 || clean.length() > 44) continue;
      String lower = clean.toLowerCase(Locale.ROOT);
      if (lower.contains(".ru") || lower.contains(".net") || lower.contains(".com")
          || lower.contains(".gg") || lower.contains("http") || lower.contains("vk.")
          || lower.contains("t.me")) {
        continue;
      }
      if (lower.contains("онлайн") || lower.contains("online") || lower.contains("пинг")
          || lower.contains("ping") || lower.contains("монет") || lower.contains("баланс")
          || lower.contains("убийств") || lower.contains("смерт") || lower.contains("клан")
          || lower.contains("$") || lower.contains("%") || lower.contains("tps")) {
        continue;
      }
      for (int p = 0; p < REALM_PREFIXES.length; p++) {
        String prefix = REALM_PREFIXES[p];
        int idx = lower.indexOf(prefix);
        if (idx >= 0) {
          String val = clean.substring(idx + prefix.length()).trim();
          if (val.length() >= 1 && val.length() <= 30) return val;
        }
      }
      if (hashCandidate.isEmpty() && HASH_NUMBER.matcher(clean).matches()) {
        hashCandidate = clean.length() > 28 ? clean.substring(0, 28).trim() : clean;
      }
      if (modeCandidate.isEmpty()
          && (lower.contains("анархи") || lower.contains("гриф") || lower.contains("лайт")
              || lower.contains("классик") || lower.contains("спидран") || lower.contains("лобби")
              || lower.contains("хаб") || lower.contains("anarchy") || lower.contains("grief")
              || lower.contains("duels") || lower.contains("skyblock")
              || lower.contains("bedwars"))) {
        modeCandidate = clean.length() > 28 ? clean.substring(0, 28).trim() : clean;
      }
    }
    if (!hashCandidate.isEmpty()) return hashCandidate;
    return modeCandidate;
  }

  public static float terrainSimilarity(List<String> a, List<String> b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) return -1f;
    Map<String, String> mapA = new HashMap<String, String>(a.size() * 2);
    for (int i = 0; i < a.size(); i++) {
      String s = a.get(i);
      int c1 = s.indexOf(',');
      int c2 = c1 >= 0 ? s.indexOf(',', c1 + 1) : -1;
      if (c2 > 0) mapA.put(s.substring(0, c2), s.substring(c2 + 1));
    }
    int overlap = 0;
    int matches = 0;
    for (int i = 0; i < b.size(); i++) {
      String s = b.get(i);
      int c1 = s.indexOf(',');
      int c2 = c1 >= 0 ? s.indexOf(',', c1 + 1) : -1;
      if (c2 <= 0) continue;
      String valA = mapA.get(s.substring(0, c2));
      if (valA != null) {
        overlap++;
        if (valA.equals(s.substring(c2 + 1))) matches++;
      }
    }
    if (overlap < 4) return -1f;
    return (float) matches / (float) overlap;
  }

  public static String encodeTerrainSample(int blockX, int blockZ, short height, int rgb) {
    int hBucket = height >> 2;
    int r = ((rgb >>> 16) & 0xFF) >>> 5;
    int g = ((rgb >>> 8) & 0xFF) >>> 5;
    int b = (rgb & 0xFF) >>> 5;
    int colorBucket = (r << 6) | (g << 3) | b;
    return blockX + "," + blockZ + "," + hBucket + ":" + colorBucket;
  }

  private static String hashSamples(List<String> samples) {
    List<String> sorted = new ArrayList<String>(samples);
    Collections.sort(sorted);
    int h = 0x811c9dc5;
    for (int i = 0; i < sorted.size(); i++) {
      String s = sorted.get(i);
      for (int j = 0; j < s.length(); j++) {
        h ^= s.charAt(j);
        h *= 0x01000193;
      }
      h ^= 0xFF;
      h *= 0x01000193;
    }
    return String.format(Locale.ROOT, "%08x", h);
  }

  public static String stripMcFormatting(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '§' && i + 1 < raw.length()) {
        i++;
        continue;
      }
      out.append(c);
    }
    return out.toString();
  }

  public static String normalizeHost(String ip) {
    if (ip == null) return "";
    String host = ip.trim().toLowerCase(Locale.ROOT);
    if (host.endsWith(":25565")) host = host.substring(0, host.length() - 6);
    while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
    StringBuilder clean = new StringBuilder();
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_'
          || c == ':') {
        clean.append(c);
      }
    }
    return clean.toString();
  }

  public static String normalizeDimension(String dim) {
    if (dim == null || dim.trim().isEmpty()) return Waypoint.DEFAULT_DIMENSION;
    String clean = dim.trim().toLowerCase(Locale.ROOT);
    return DIMENSION_PATTERN.matcher(clean).matches() ? clean : Waypoint.DEFAULT_DIMENSION;
  }

  public static String sanitizeSlug(String raw) {
    if (raw == null) return "";
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return "";
    StringBuilder slug = new StringBuilder();
    boolean lossless = true;
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-') {
        slug.append(c);
      } else if (c >= 'A' && c <= 'Z') {
        slug.append((char) (c + 32));
      } else if (Character.isWhitespace(c)) {
        if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '_') slug.append('_');
        lossless = false;
      } else {
        lossless = false;
      }
    }
    String base = slug.toString();
    if (base.length() > 36) base = base.substring(0, 36);
    if (lossless && !base.isEmpty()) return base;
    int hash = 0x811c9dc5;
    for (int i = 0; i < trimmed.length(); i++) {
      hash ^= trimmed.charAt(i);
      hash *= 0x01000193;
    }
    String hex = String.format(Locale.ROOT, "%08x", hash);
    return (base.isEmpty() ? "r" : base) + "_" + hex;
  }
}
