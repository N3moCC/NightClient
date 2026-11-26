package dev.nightclient.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.api.PlayerSkin;
import dev.nightclient.config.AccountStore;
import dev.nightclient.config.SavedAccount;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.player.DefaultSkins;
import dev.nightclient.renderer.player.PlayerFacePainter;
import dev.nightclient.renderer.player.PlayerSkinData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class AccountManager {
  private static final long SKIN_DISK_TTL_MS = 24L * 3600L * 1000L;
  private static final long MISS_RETRY_MS = 15L * 60L * 1000L;

  private static final class CachedSkin {
    final PlayerSkinData skin;
    final int[] face64;
    final long resolvedAt;
    final boolean missing;

    CachedSkin(PlayerSkinData skin, int[] face64, long resolvedAt, boolean missing) {
      this.skin = skin;
      this.face64 = face64;
      this.resolvedAt = resolvedAt;
      this.missing = missing;
    }
  }

  private final AccountStore store;
  private final Path skinCacheDir;
  private final PlatformServices platform;
  private final Settings settings;
  private final Runnable onSessionChanged;
  private final CosmeticsManager.HttpFetcher fetcher;
  private final ExecutorService skinExecutor;
  private final Random random;
  private final List<SavedAccount> accounts = new ArrayList<SavedAccount>();
  private final java.util.Map<String, CachedSkin> skins = Collections.synchronizedMap(
      new java.util.LinkedHashMap<String, CachedSkin>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedSkin> eldest) {
          return size() > 128;
        }
      });
  private final Set<String> skinQueued = ConcurrentHashMap.newKeySet();
  private volatile String errorKey = "";

  public AccountManager(Path configDirectory, PlatformServices platform, Settings settings,
      Runnable onSessionChanged) {
    this(configDirectory, platform, settings, onSessionChanged, AccountManager::httpGet,
        defaultExecutor(), new SecureRandom());
  }

  AccountManager(Path configDirectory, PlatformServices platform, Settings settings,
      Runnable onSessionChanged, CosmeticsManager.HttpFetcher fetcher, ExecutorService executor,
      Random random) {
    this.store = new AccountStore(configDirectory);
    this.skinCacheDir = configDirectory.resolve("nightclient").resolve("cache").resolve("account-skins");
    this.platform = platform;
    this.settings = settings;
    this.onSessionChanged = onSessionChanged != null ? onSessionChanged : () -> {};
    this.fetcher = fetcher != null ? fetcher : AccountManager::httpGet;
    this.skinExecutor = executor != null ? executor : defaultExecutor();
    this.random = random != null ? random : new SecureRandom();
    synchronized (accounts) {
      accounts.addAll(store.load());
      sortLocked();
    }
  }

  public List<SavedAccount> accounts() {
    synchronized (accounts) {
      List<SavedAccount> copy = new ArrayList<SavedAccount>(accounts.size());
      for (SavedAccount entry : accounts) copy.add(entry.copy());
      return copy;
    }
  }

  public SavedAccount findById(String id) {
    if (id == null || id.isEmpty()) return null;
    synchronized (accounts) {
      for (SavedAccount entry : accounts)
        if (id.equals(entry.id)) return entry.copy();
    }
    return null;
  }

  public String launcherNickname() {
    String nick = platform.launcherNickname();
    return nick == null || nick.trim().isEmpty() ? activeNickname() : nick.trim();
  }

  public String activeNickname() {
    String nick = platform.ownNickname();
    return nick == null || nick.trim().isEmpty() ? "Player" : nick.trim();
  }

  public boolean isLauncherActive() {
    return platform.isLauncherSessionActive();
  }

  public String errorKey() {
    return errorKey;
  }

  public void clearError() {
    errorKey = "";
  }

  public String saveDraft(String id, String rawName, String rawNote, String rawSkinName) {
    String cleanName = SavedAccount.normalizeNickname(rawName);
    if (cleanName == null) {
      errorKey = "accounts.err.invalid_name";
      return errorKey;
    }
    String cleanNote = SavedAccount.cleanNote(rawNote);
    String cleanSkin = SavedAccount.normalizeNickname(rawSkinName);
    if (cleanSkin == null || cleanSkin.equalsIgnoreCase(cleanName)) cleanSkin = "";

    synchronized (accounts) {
      SavedAccount existing = null;
      if (id != null && !id.isEmpty()) {
        for (SavedAccount entry : accounts) {
          if (id.equals(entry.id)) {
            existing = entry;
            break;
          }
        }
      }
      for (SavedAccount entry : accounts) {
        if (entry != existing && entry.name.equalsIgnoreCase(cleanName)) {
          errorKey = "accounts.err.duplicate";
          return errorKey;
        }
      }
      if (existing != null) {
        existing.name = cleanName;
        existing.note = cleanNote;
        existing.skinName = cleanSkin;
        existing.normalize();
      } else {
        if (accounts.size() >= SavedAccount.MAX_ACCOUNTS) {
          errorKey = "accounts.err.limit";
          return errorKey;
        }
        SavedAccount created = new SavedAccount(SavedAccount.newId(), cleanName, cleanNote, cleanSkin);
        accounts.add(created);
      }
      sortLocked();
      store.save(accounts);
    }
    errorKey = "";
    ensureSkinQueued(cleanSkin.isEmpty() ? cleanName : cleanSkin);
    return "";
  }

  public String switchSaved(String id) {
    if (id == null || id.isEmpty()) return "";
    SavedAccount target = null;
    synchronized (accounts) {
      for (SavedAccount entry : accounts) {
        if (id.equals(entry.id)) {
          target = entry;
          break;
        }
      }
      if (target == null) return "";
      UUID offlineUuid = target.offlineUuid();
      if (!platform.switchOfflineSession(target.name, offlineUuid)) return "";
      target.lastUsedAt = System.currentTimeMillis();
      target.useCount++;
      sortLocked();
      store.save(accounts);
    }
    errorKey = "";
    onSessionChanged.run();
    return "";
  }

  public String quickSwitch(String rawName) {
    String cleanName = SavedAccount.normalizeNickname(rawName);
    if (cleanName == null) {
      errorKey = "accounts.err.invalid_name";
      return errorKey;
    }
    String existingId = null;
    synchronized (accounts) {
      for (SavedAccount entry : accounts) {
        if (entry.name.equalsIgnoreCase(cleanName)) {
          existingId = entry.id;
          break;
        }
      }
      if (existingId == null) {
        if (!platform.switchOfflineSession(cleanName, SavedAccount.offlineUuid(cleanName)))
          return "";
        if (accounts.size() < SavedAccount.MAX_ACCOUNTS) {
          SavedAccount created = new SavedAccount(SavedAccount.newId(), cleanName, "", "");
          created.lastUsedAt = System.currentTimeMillis();
          created.useCount = 1;
          accounts.add(created);
          sortLocked();
          store.save(accounts);
        }
        errorKey = "";
      }
    }
    if (existingId != null) return switchSaved(existingId);
    onSessionChanged.run();
    return "";
  }

  public boolean restoreLauncher() {
    boolean ok = platform.restoreLauncherSession();
    if (ok) {
      errorKey = "";
      onSessionChanged.run();
    }
    return ok;
  }

  public boolean togglePin(String id) {
    if (id == null || id.isEmpty()) return false;
    synchronized (accounts) {
      for (SavedAccount entry : accounts) {
        if (id.equals(entry.id)) {
          entry.pinned = !entry.pinned;
          sortLocked();
          store.save(accounts);
          return true;
        }
      }
    }
    return false;
  }

  public boolean delete(String id) {
    if (id == null || id.isEmpty()) return false;
    synchronized (accounts) {
      for (int i = 0; i < accounts.size(); i++) {
        if (id.equals(accounts.get(i).id)) {
          accounts.remove(i);
          store.save(accounts);
          return true;
        }
      }
    }
    return false;
  }

  public String generateRandomNickname() {
    synchronized (accounts) {
      for (int attempt = 0; attempt < 24; attempt++) {
        String candidate = SavedAccount.generateRandomNickname(random);
        boolean clash = false;
        for (SavedAccount entry : accounts) {
          if (entry.name.equalsIgnoreCase(candidate)) {
            clash = true;
            break;
          }
        }
        if (!clash) return candidate;
      }
    }
    return SavedAccount.generateRandomNickname(random);
  }

  public String reconnect() {
    if (!platform.canReconnectServer()) {
      errorKey = "accounts.err.no_server";
      return errorKey;
    }
    if (settings != null && settings.safeCtLeave != null && settings.safeCtLeave.enabled
        && platform.isInCombat()) {
      errorKey = "accounts.err.combat";
      return errorKey;
    }
    errorKey = "";
    platform.reconnectCurrentServer();
    return "";
  }

  public int[] faceFor(String nickname) {
    String lookup = lookupName(nickname);
    if (lookup.isEmpty()) return PlayerFacePainter.fallbackFace("Player");
    String key = lookup.toLowerCase(Locale.ROOT);
    CachedSkin cached = skins.get(key);
    if (cached != null) {
      if (cached.missing && System.currentTimeMillis() - cached.resolvedAt > MISS_RETRY_MS) {
        ensureSkinQueued(lookup);
      }
      return cached.face64;
    }
    ensureSkinQueued(lookup);
    return PlayerFacePainter.fallbackFace(lookup);
  }

  public PlayerSkin previewFor(String nickname) {
    String lookup = lookupName(nickname);
    if (lookup.isEmpty()) return DefaultSkins.classic();
    String key = lookup.toLowerCase(Locale.ROOT);
    CachedSkin cached = skins.get(key);
    if (cached != null && cached.skin != null) return cached.skin;
    if (cached == null || (cached.missing && System.currentTimeMillis() - cached.resolvedAt > MISS_RETRY_MS)) {
      ensureSkinQueued(lookup);
    }
    return DefaultSkins.forModel((SavedAccount.offlineUuid(lookup).hashCode() & 1) != 0);
  }

  private String lookupName(String nickname) {
    if (nickname == null) return "";
    String clean = SavedAccount.normalizeNickname(nickname);
    return clean == null ? "" : clean;
  }

  private void ensureSkinQueued(String nick) {
    if (nick == null || nick.isEmpty()) return;
    final String key = nick.toLowerCase(Locale.ROOT);
    if (!skinQueued.add(key)) return;
    if (skinExecutor.isShutdown()) {
      skinQueued.remove(key);
      return;
    }
    try {
      skinExecutor.submit(() -> {
        try {
          CachedSkin loaded = loadOrFetchSkin(nick, key);
          if (loaded != null) skins.put(key, loaded);
        } catch (Throwable ignored) {
          skins.put(key, new CachedSkin(null, PlayerFacePainter.fallbackFace(nick),
              System.currentTimeMillis(), true));
        } finally {
          skinQueued.remove(key);
        }
      });
    } catch (java.util.concurrent.RejectedExecutionException rejected) {
      skinQueued.remove(key);
    }
  }

  private CachedSkin loadOrFetchSkin(String nick, String key) {
    long now = System.currentTimeMillis();
    Path pngFile = skinCacheDir.resolve(key + ".png");
    Path slimFlag = skinCacheDir.resolve(key + ".slim");
    try {
      if (Files.isRegularFile(pngFile)) {
        long age = now - Files.getLastModifiedTime(pngFile).toMillis();
        if (age >= 0 && age < SKIN_DISK_TTL_MS) {
          CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeSkinFile(pngFile);
          boolean slim = Files.isRegularFile(slimFlag);
          return buildCachedSkin(decoded, slim, now);
        }
      }
    } catch (Exception ignored) {
    }

    try {
      String uuid = resolveMojangUuid(nick);
      if (uuid != null) {
        byte[] sessionJson = fetcher.fetch(
            "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (sessionJson != null && sessionJson.length > 0) {
          JsonObject root = new JsonParser()
              .parse(new String(sessionJson, StandardCharsets.UTF_8))
              .getAsJsonObject();
          if (root.has("properties")) {
            for (JsonElement el : root.getAsJsonArray("properties")) {
              JsonObject prop = el.getAsJsonObject();
              if (!"textures".equals(prop.get("name").getAsString())) continue;
              String decodedJson = new String(
                  Base64.getDecoder().decode(prop.get("value").getAsString()),
                  StandardCharsets.UTF_8);
              JsonObject payload = new JsonParser().parse(decodedJson).getAsJsonObject();
              JsonObject textures = payload.getAsJsonObject("textures");
              if (textures == null || !textures.has("SKIN")) continue;
              JsonObject skinObj = textures.getAsJsonObject("SKIN");
              String url = skinObj.get("url").getAsString();
              boolean slim = false;
              if (skinObj.has("metadata")) {
                JsonObject meta = skinObj.getAsJsonObject("metadata");
                if (meta.has("model")) slim = "slim".equals(meta.get("model").getAsString());
              }
              byte[] png = fetcher.fetch(url);
              if (png != null && png.length > 0) {
                CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeSkin(png);
                try {
                  Files.createDirectories(skinCacheDir);
                  Files.write(pngFile, png);
                  if (slim) Files.write(slimFlag, new byte[] {'1'});
                  else Files.deleteIfExists(slimFlag);
                } catch (IOException ignored) {
                }
                return buildCachedSkin(decoded, slim, now);
              }
            }
          }
        }
      }
    } catch (Exception ignored) {
    }
    return new CachedSkin(null, PlayerFacePainter.fallbackFace(nick), now, true);
  }

  private CachedSkin buildCachedSkin(CosmeticsImages.DecodedTexture decoded, boolean slim, long now) {
    PlayerSkinData data = new PlayerSkinData(
        decoded.pixels, decoded.width, decoded.height, null, 0, 0, slim);
    int[] face = PlayerFacePainter.extractFace(data);
    if (face == null) face = PlayerFacePainter.fallbackFace("Player");
    return new CachedSkin(data, face, now, false);
  }

  private String resolveMojangUuid(String nick) {
    String[] endpoints = {
        "https://api.mojang.com/users/profiles/minecraft/" + nick,
        "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + nick
    };
    for (String url : endpoints) {
      try {
        byte[] raw = fetcher.fetch(url);
        if (raw == null || raw.length == 0) continue;
        JsonObject obj =
            new JsonParser().parse(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        if (obj.has("id")) return obj.get("id").getAsString();
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  private void sortLocked() {
    Collections.sort(accounts, new Comparator<SavedAccount>() {
      @Override
      public int compare(SavedAccount a, SavedAccount b) {
        if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
        if (a.lastUsedAt != b.lastUsedAt) return a.lastUsedAt > b.lastUsedAt ? -1 : 1;
        return a.name.compareToIgnoreCase(b.name);
      }
    });
  }

  public void shutdown() {
    store.close();
    skinExecutor.shutdownNow();
    try {
      skinExecutor.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private static ExecutorService defaultExecutor() {
    return Executors.newFixedThreadPool(2, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "NightClient AccountSkins");
        t.setDaemon(true);
        return t;
      }
    });
  }

  private static byte[] httpGet(String url) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    try {
      conn.setConnectTimeout(4000);
      conn.setReadTimeout(5000);
      conn.setInstanceFollowRedirects(true);
      conn.setRequestProperty("User-Agent", "NightClient-AccountPreview/1.0");
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
      try (InputStream in = conn.getInputStream();
           ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) >= 0) {
          total += n;
          if (total > 8L * 1024L * 1024L) throw new IOException("Skin response too large");
          out.write(buf, 0, n);
        }
        return out.toByteArray();
      }
    } finally {
      conn.disconnect();
    }
  }
}
