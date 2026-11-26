package dev.nightclient.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.api.PlayerSkin;
import dev.nightclient.config.Settings;
import dev.nightclient.cosmetics.CosmeticModels;
import dev.nightclient.cosmetics.OfficialCapes;
import dev.nightclient.renderer.model.CosmeticModelLoader;
import dev.nightclient.renderer.model.CosmeticModelMesh;
import dev.nightclient.renderer.model.PixelSwordMeshBuilder;
import dev.nightclient.renderer.player.DefaultSkins;
import dev.nightclient.renderer.player.PlayerSkinData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class CosmeticsManager {
  @FunctionalInterface
  public interface HttpFetcher {
    byte[] fetch(String url) throws IOException;
  }

  private static final Pattern VALID_NICK = Pattern.compile("^[A-Za-z0-9_]{2,16}$");
  private static final long NICK_CACHE_TTL_MS = 3600_000L;
  private static final int[] EMPTY_SKIN_PIXELS = {0};

  private static final class ResolvedTexture {
    final String key;
    final int[] pixels;
    final int width;
    final int height;
    final boolean slim;
    final int revision;

    ResolvedTexture(String key, int[] pixels, int width, int height, boolean slim, int revision) {
      this.key = key;
      this.pixels = pixels;
      this.width = width;
      this.height = height;
      this.slim = slim;
      this.revision = revision;
    }
  }

  private static final class CardEntry {
    final String key;
    final PlayerSkin skin;
    final boolean decoded;

    CardEntry(String id, int rev, PlayerSkin skin, boolean decoded) {
      this.key = "cape:" + id + ":" + rev;
      this.skin = skin;
      this.decoded = decoded;
    }
  }

  private final Path cacheRoot;
  private final PlatformServices platform;
  private final Settings settings;
  private final Runnable onChanged;
  private final HttpFetcher fetcher;
  private final ExecutorService executor;
  private final ExecutorService cardExecutor;
  private final AtomicInteger revCounter = new AtomicInteger(1);
  private final AtomicBoolean dirtyPlatform = new AtomicBoolean(true);
  private final AtomicBoolean pickerBusy = new AtomicBoolean(false);
  private final ConcurrentHashMap<String, CardEntry> cardCache =
      new ConcurrentHashMap<String, CardEntry>();
  private final Set<String> cardQueued = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, Long> cardRetryAfter =
      new ConcurrentHashMap<String, Long>();
  private static final long CARD_RETRY_COOLDOWN_MS = 15_000L;

  private final Map<String, CosmeticsImages.DecodedTexture> capeCache =
      Collections.synchronizedMap(new LinkedHashMap<String, CosmeticsImages.DecodedTexture>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CosmeticsImages.DecodedTexture> e) {
          return size() > 64;
        }
      });

  private final Map<String, CosmeticsImages.DecodedTexture> skinCache =
      Collections.synchronizedMap(new LinkedHashMap<String, CosmeticsImages.DecodedTexture>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CosmeticsImages.DecodedTexture> e) {
          return size() > 16;
        }
      });

  private final Map<String, CosmeticModelMesh> modelCache =
      Collections.synchronizedMap(new LinkedHashMap<String, CosmeticModelMesh>(128, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CosmeticModelMesh> e) {
          return size() > 128;
        }
      });
  private final Map<String, CardEntry> modelCardCache =
      Collections.synchronizedMap(new LinkedHashMap<String, CardEntry>(160, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CardEntry> e) {
          return size() > 160;
        }
      });
  private final Set<String> modelCardQueued = ConcurrentHashMap.newKeySet();
  private String lastSyncedCapeMode, lastSyncedOfficialCape, lastSyncedCustomCapePath;
  private String lastSyncedSkinMode, lastSyncedSkinNickname, lastSyncedCustomSkinPath,
      lastSyncedSkinModel;
  private String lastSyncedWeaponMode, lastSyncedBuiltinWeapon, lastSyncedWeaponMaterial,
      lastSyncedCustomWeaponPath, lastSyncedWeaponTarget, lastSyncedArmorMode;
  private int lastSyncedWeaponColor;
  private float lastSyncedWeaponScale = Float.NaN;
  private boolean lastSyncedWeaponFp, lastSyncedWeaponTp, cosmeticsSyncedOnce;

  private volatile String requestedCapeKey = "";
  private volatile String requestedSkinKey = "";
  private volatile String requestedWeaponKey = "";
  private volatile String requestedArmorKey = "";
  private volatile ResolvedTexture activeCape;
  private volatile ResolvedTexture activeSkin;
  private volatile CosmeticModelMesh activeWeapon;
  private volatile CosmeticModelMesh activeArmor;
  private volatile boolean capeLoading;
  private volatile boolean skinLoading;
  private volatile boolean weaponLoading;
  private volatile boolean armorLoading;
  private volatile String errorKey = "";
  private volatile String pendingPickedCape;
  private volatile String pendingPickedSkin;
  private volatile String pendingPickedWeapon;
  private volatile String pendingPickedArmor;

  private PlayerSkin lastPreviewBase;
  private int lastPreviewCapeRev = -1;
  private int lastPreviewSkinRev = -1;
  private int lastPreviewWeaponId = -1;
  private int lastPreviewWeaponColor = 0;
  private float lastPreviewWeaponScale = Float.NaN;
  private int lastPreviewArmorId = -1;
  private int lastPreviewArmorColor = 0;
  private float lastPreviewArmorScale = Float.NaN;
  private int lastPreviewArmorMask = -1;
  private String lastPreviewCapeMode = "";
  private String lastPreviewModelMode = "";
  private PlayerSkinData cachedPreview;
  private String lastPushedSignature = "";
  private String lastPushedModelSignature = "";

  public CosmeticsManager(
      Path configDirectory, PlatformServices platform, Settings settings, Runnable onChanged) {
    this(configDirectory, platform, settings, onChanged, CosmeticsManager::httpGet,
        defaultExecutor(), defaultCardExecutor());
  }

  CosmeticsManager(Path configDirectory, PlatformServices platform, Settings settings,
      Runnable onChanged, HttpFetcher fetcher, ExecutorService executor) {
    this(configDirectory, platform, settings, onChanged, fetcher, executor, executor);
  }

  private CosmeticsManager(Path configDirectory, PlatformServices platform, Settings settings,
      Runnable onChanged, HttpFetcher fetcher, ExecutorService executor,
      ExecutorService cardExecutor) {
    this.cacheRoot = configDirectory == null
        ? null
        : configDirectory.resolve("nightclient").resolve("cosmetics");
    this.platform = platform;
    this.settings = settings;
    this.onChanged = onChanged;
    this.fetcher = fetcher;
    this.executor = executor;
    this.cardExecutor = cardExecutor;
    syncFromSettings();
  }

  private static ExecutorService defaultCardExecutor() {
    return Executors.newFixedThreadPool(6, new ThreadFactory() {
      private int idx = 1;

      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "NightClient-CapeCards-" + idx++);
        t.setDaemon(true);
        return t;
      }
    });
  }

  private static ExecutorService defaultExecutor() {
    return Executors.newFixedThreadPool(2, new ThreadFactory() {
      private int idx = 1;

      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "NightClient-Cosmetics-" + idx++);
        t.setDaemon(true);
        return t;
      }
    });
  }

  public void tick() {
    String pickedCape = pendingPickedCape;
    if (pickedCape != null) {
      pendingPickedCape = null;
      applyCustomCapePath(pickedCape);
    }
    String pickedSkin = pendingPickedSkin;
    if (pickedSkin != null) {
      pendingPickedSkin = null;
      applyCustomSkinPath(pickedSkin);
    }
    String pickedWeapon = pendingPickedWeapon;
    if (pickedWeapon != null) {
      pendingPickedWeapon = null;
      applyCustomWeaponPath(pickedWeapon);
    }
    String pickedArmor = pendingPickedArmor;
    if (pickedArmor != null) {
      pendingPickedArmor = null;
      applyCustomArmorPath(pickedArmor);
    }
    if (dirtyPlatform.compareAndSet(true, false)) {
      pushPlatformState();
      pushModelPlatformState();
    }
  }

  public void shutdown() {
    executor.shutdownNow();
    if (cardExecutor != executor) cardExecutor.shutdownNow();
  }

  private void submitAsync(ExecutorService pool, Runnable task, Runnable onReject) {
    if (pool.isShutdown()) {
      if (onReject != null) onReject.run();
      return;
    }
    try {
      pool.execute(task);
    } catch (java.util.concurrent.RejectedExecutionException rejected) {
      if (onReject != null) onReject.run();
    }
  }

  public synchronized void syncFromSettings() {
    Settings.Cosmetics c = settings.cosmetics;
    if (c == null) return;
    if (cosmeticsSyncedOnce
        && !requestedCapeKey.isEmpty() && !requestedSkinKey.isEmpty()
        && !requestedWeaponKey.isEmpty()
        && Objects.equals(lastSyncedCapeMode, c.capeMode)
        && Objects.equals(lastSyncedOfficialCape, c.officialCape)
        && Objects.equals(lastSyncedCustomCapePath, c.customCapePath)
        && Objects.equals(lastSyncedSkinMode, c.skinMode)
        && Objects.equals(lastSyncedSkinNickname, c.skinNickname)
        && Objects.equals(lastSyncedCustomSkinPath, c.customSkinPath)
        && Objects.equals(lastSyncedSkinModel, c.skinModel)
        && Objects.equals(lastSyncedWeaponMode, c.weaponMode)
        && Objects.equals(lastSyncedBuiltinWeapon, c.builtinWeapon)
        && Objects.equals(lastSyncedWeaponMaterial, c.weaponMaterial)
        && Objects.equals(lastSyncedCustomWeaponPath, c.customWeaponPath)
        && Objects.equals(lastSyncedWeaponTarget, c.weaponTarget)
        && Objects.equals(lastSyncedArmorMode, c.armorMode)
        && lastSyncedWeaponColor == c.weaponColor
        && Float.floatToIntBits(lastSyncedWeaponScale) == Float.floatToIntBits(c.weaponScale)
        && lastSyncedWeaponFp == c.weaponFirstPerson
        && lastSyncedWeaponTp == c.weaponThirdPerson) {
      return;
    }
    lastSyncedCapeMode = c.capeMode;
    lastSyncedOfficialCape = c.officialCape;
    lastSyncedCustomCapePath = c.customCapePath;
    lastSyncedSkinMode = c.skinMode;
    lastSyncedSkinNickname = c.skinNickname;
    lastSyncedCustomSkinPath = c.customSkinPath;
    lastSyncedSkinModel = c.skinModel;
    lastSyncedWeaponMode = c.weaponMode;
    lastSyncedBuiltinWeapon = c.builtinWeapon;
    lastSyncedWeaponMaterial = c.weaponMaterial;
    lastSyncedCustomWeaponPath = c.customWeaponPath;
    lastSyncedWeaponTarget = c.weaponTarget;
    lastSyncedWeaponColor = c.weaponColor;
    lastSyncedWeaponScale = c.weaponScale;
    lastSyncedWeaponFp = c.weaponFirstPerson;
    lastSyncedWeaponTp = c.weaponThirdPerson;
    syncCape(c);
    syncSkin(c);
    syncWeapon(c);
    syncArmor(c);
    lastSyncedArmorMode = c.armorMode;
    cosmeticsSyncedOnce = true;
    dirtyPlatform.set(true);
  }

  private void syncWeapon(Settings.Cosmetics c) {
    String mode = c.weaponMode == null ? "none" : c.weaponMode;
    if ("none".equals(mode)) {
      requestedWeaponKey = "none";
      activeWeapon = null;
      weaponLoading = false;
      return;
    }
    if ("builtin".equals(mode)) {
      CosmeticModels.Entry entry = CosmeticModels.weaponById(c.builtinWeapon);
      if (entry == null) {
        requestedWeaponKey = "none";
        activeWeapon = null;
        weaponLoading = false;
        return;
      }
      final String matId = CosmeticModels.resolveMaterial(c.weaponMaterial).id;
      final String key = "builtin:weapon:" + entry.id + ":" + matId;
      if (key.equals(requestedWeaponKey) && (activeWeapon != null || weaponLoading)) return;
      requestedWeaponKey = key;
      CosmeticModelMesh cached = modelCache.get(key);
      if (cached != null) {
        activeWeapon = cached;
        weaponLoading = false;
        dirtyPlatform.set(true);
        return;
      }
      weaponLoading = true;
      errorKey = "";
      submitAsync(executor, () -> loadBuiltinModelAsync(key, entry, matId, false),
          () -> weaponLoading = false);
      return;
    }
    if ("custom".equals(mode)) {
      final String path = c.customWeaponPath == null ? "" : c.customWeaponPath.trim();
      if (path.isEmpty()) {
        requestedWeaponKey = "none";
        activeWeapon = null;
        weaponLoading = false;
        return;
      }
      final String key = "custom:weapon:" + path;
      if (key.equals(requestedWeaponKey) && (activeWeapon != null || weaponLoading)) return;
      requestedWeaponKey = key;
      CosmeticModelMesh cached = modelCache.get(key);
      if (cached != null) {
        activeWeapon = cached;
        weaponLoading = false;
        dirtyPlatform.set(true);
        return;
      }
      weaponLoading = true;
      errorKey = "";
      submitAsync(executor, () -> loadCustomModelAsync(key, path, false),
          () -> weaponLoading = false);
    }
  }

  private void syncArmor(Settings.Cosmetics c) {
    c.armorMode = "none";
    requestedArmorKey = "none";
    activeArmor = null;
    armorLoading = false;
  }

  private void loadBuiltinModelAsync(
      String key, CosmeticModels.Entry entry, String materialId, boolean armor) {
    try {
      CosmeticModelMesh mesh = modelCache.get(key);
      if (mesh == null) {
        if (!armor && entry.resourcePath != null
            && entry.resourcePath.startsWith("procedural:sword:")) {
          mesh = PixelSwordMeshBuilder.build(entry.id, materialId);
        } else {
          mesh = CosmeticModelLoader.loadResource(entry.resourcePath, armor);
        }
        modelCache.put(key, mesh);
      }
      if (armor) {
        if (key.equals(requestedArmorKey)) {
          activeArmor = mesh;
          errorKey = "";
          dirtyPlatform.set(true);
        }
      } else {
        if (key.equals(requestedWeaponKey)) {
          activeWeapon = mesh;
          errorKey = "";
          dirtyPlatform.set(true);
        }
      }
    } catch (Exception err) {
      if ((armor && key.equals(requestedArmorKey)) || (!armor && key.equals(requestedWeaponKey))) {
        errorKey = "cosmetics.error.invalid_model";
      }
    } finally {
      if (armor && key.equals(requestedArmorKey)) armorLoading = false;
      if (!armor && key.equals(requestedWeaponKey)) weaponLoading = false;
    }
  }

  private void loadCustomModelAsync(String key, String pathStr, boolean armor) {
    try {
      CosmeticModelMesh mesh = CosmeticModelLoader.loadFile(Paths.get(pathStr), armor);
      modelCache.put(key, mesh);
      if (armor) {
        if (key.equals(requestedArmorKey)) {
          activeArmor = mesh;
          errorKey = "";
          dirtyPlatform.set(true);
        }
      } else {
        if (key.equals(requestedWeaponKey)) {
          activeWeapon = mesh;
          errorKey = "";
          dirtyPlatform.set(true);
        }
      }
    } catch (Exception err) {
      if ((armor && key.equals(requestedArmorKey)) || (!armor && key.equals(requestedWeaponKey))) {
        errorKey = "cosmetics.error.invalid_model";
      }
    } finally {
      if (armor && key.equals(requestedArmorKey)) armorLoading = false;
      if (!armor && key.equals(requestedWeaponKey)) weaponLoading = false;
    }
  }

  private void syncCape(Settings.Cosmetics c) {
    String mode = c.capeMode == null ? "default" : c.capeMode;
    if ("default".equals(mode) || "none".equals(mode)) {
      requestedCapeKey = mode;
      activeCape = null;
      capeLoading = false;
      return;
    }
    if ("official".equals(mode)) {
      OfficialCapes.Cape cape = OfficialCapes.isEnabled() ? OfficialCapes.byId(c.officialCape) : null;
      if (cape == null) {
        c.capeMode = "default";
        requestedCapeKey = "default";
        activeCape = null;
        capeLoading = false;
        return;
      }
      final String key = "official:" + cape.id;
      if (key.equals(requestedCapeKey) && activeCape != null) return;
      requestedCapeKey = key;

      CosmeticsImages.DecodedTexture cached = capeCache.get(key);
      if (cached != null) {
        activeCape = new ResolvedTexture(
            key, cached.pixels, cached.width, cached.height, false, revCounter.incrementAndGet());
        capeLoading = false;
        dirtyPlatform.set(true);
        return;
      }

      CosmeticsImages.DecodedTexture fallback = CosmeticsImages.synthesizeCape(cape);
      activeCape = new ResolvedTexture(
          key, fallback.pixels, fallback.width, fallback.height, false, revCounter.incrementAndGet());
      errorKey = "";
      if (cape.textureHash == null) {
        capeCache.put(key, fallback);
        capeLoading = false;
        return;
      }
      capeLoading = true;
      submitAsync(executor, () -> loadOfficialCapeAsync(key, cape), () -> capeLoading = false);
      return;
    }

    if ("custom".equals(mode)) {
      final String path = c.customCapePath == null ? "" : c.customCapePath.trim();
      if (path.isEmpty()) {
        requestedCapeKey = "default";
        activeCape = null;
        capeLoading = false;
        return;
      }
      final String key = "custom:" + path;
      if (key.equals(requestedCapeKey) && (activeCape != null || capeLoading)) return;
      requestedCapeKey = key;
      capeLoading = true;
      errorKey = "";
      submitAsync(executor, () -> loadCustomCapeAsync(key, path), () -> capeLoading = false);
    }
  }

  private void syncSkin(Settings.Cosmetics c) {
    String mode = c.skinMode == null ? "default" : c.skinMode;
    if ("default".equals(mode)) {
      requestedSkinKey = "default";
      activeSkin = null;
      skinLoading = false;
      return;
    }
    if ("nick".equals(mode)) {
      final String nick = c.skinNickname == null ? "" : c.skinNickname.trim();
      if (!VALID_NICK.matcher(nick).matches()) {
        requestedSkinKey = "nick:invalid:" + nick;
        activeSkin = null;
        skinLoading = false;
        if (!nick.isEmpty()) errorKey = "cosmetics.error.invalid_nick";
        return;
      }
      final String key = "nick:" + nick.toLowerCase(Locale.ROOT);
      if (key.equals(requestedSkinKey) && (activeSkin != null || skinLoading)) return;
      requestedSkinKey = key;
      CosmeticsImages.DecodedTexture cached = skinCache.get(key);
      if (cached != null) {
        activeSkin = new ResolvedTexture(key, cached.pixels, cached.width, cached.height,
            cached.slim, revCounter.incrementAndGet());
        skinLoading = false;
        errorKey = "";
        dirtyPlatform.set(true);
        return;
      }
      skinLoading = true;
      errorKey = "";
      submitAsync(executor, () -> loadNickSkinAsync(key, nick), () -> skinLoading = false);
      return;
    }
    if ("custom".equals(mode)) {
      final String path = c.customSkinPath == null ? "" : c.customSkinPath.trim();
      if (path.isEmpty()) {
        requestedSkinKey = "default";
        activeSkin = null;
        skinLoading = false;
        return;
      }
      final String key = "custom:" + path;
      if (key.equals(requestedSkinKey) && (activeSkin != null || skinLoading)) return;
      requestedSkinKey = key;
      skinLoading = true;
      errorKey = "";
      submitAsync(executor, () -> loadCustomSkinAsync(key, path), () -> skinLoading = false);
    }
  }

  private CosmeticsImages.DecodedTexture resolveAndCacheOfficialCape(
      String key, OfficialCapes.Cape cape) throws IOException {
    CosmeticsImages.DecodedTexture existing = capeCache.get(key);
    if (existing != null) return existing;
    Path cachedFile =
        cacheRoot == null ? null : cacheRoot.resolve("capes").resolve(cape.id + ".png");
    byte[] bytes = null;
    if (cachedFile != null && Files.isRegularFile(cachedFile)) {
      try {
        byte[] diskBytes = Files.readAllBytes(cachedFile);
        if (diskBytes != null && diskBytes.length > 0) {
          CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeOfficialCape(diskBytes);
          capeCache.put(key, decoded);
          return decoded;
        }
      } catch (IOException corrupt) {
        try {
          Files.deleteIfExists(cachedFile);
        } catch (IOException ignored) {
        }
      }
    }
    if (cape.textureUrl() != null) {
      bytes = fetcher.fetch(cape.textureUrl());
      if (bytes != null && bytes.length > 0) {
        CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeOfficialCape(bytes);
        if (cachedFile != null) {
          try {
            Files.createDirectories(cachedFile.getParent());
            Files.write(cachedFile, bytes);
          } catch (IOException ignored) {
          }
        }
        capeCache.put(key, decoded);
        return decoded;
      }
    }
    return null;
  }

  private void loadOfficialCapeAsync(String key, OfficialCapes.Cape cape) {
    try {
      CosmeticsImages.DecodedTexture decoded = resolveAndCacheOfficialCape(key, cape);
      if (decoded != null) {
        int rev = revCounter.incrementAndGet();
        cardCache.put(cape.id,
            new CardEntry(cape.id, rev,
                new PlayerSkinData(EMPTY_SKIN_PIXELS, 1, 1, decoded.pixels, decoded.width,
                    decoded.height, false),
                true));
        if (key.equals(requestedCapeKey)) {
          activeCape = new ResolvedTexture(
              key, decoded.pixels, decoded.width, decoded.height, false, rev);
          dirtyPlatform.set(true);
        }
      }
    } catch (Exception ignored) {
    } finally {
      if (key.equals(requestedCapeKey)) capeLoading = false;
    }
  }

  public PlayerSkin capeThumbnail(String id) {
    if (id != null && id.startsWith("model:")) {
      return modelCardEntryFor(id.substring(6)).skin;
    }
    return cardEntryFor(id).skin;
  }

  public String capeThumbnailKey(String id) {
    if (id != null && id.startsWith("model:")) {
      return modelCardEntryFor(id.substring(6)).key;
    }
    return cardEntryFor(id).key;
  }

  private int modelCardAccent(CosmeticModels.Entry entry) {
    if (entry != null && settings != null && settings.cosmetics != null && !entry.isArmor()
        && "builtin".equals(settings.cosmetics.weaponMode)
        && entry.id.equals(settings.cosmetics.builtinWeapon)) {
      return settings.cosmetics.weaponColor;
    }
    return entry != null ? entry.defaultAccentColor : 0xff38bdf8;
  }

  private String activeWeaponMaterialId() {
    String raw = settings != null && settings.cosmetics != null
        ? settings.cosmetics.weaponMaterial
        : CosmeticModels.DEFAULT_MATERIAL;
    return CosmeticModels.resolveMaterial(raw).id;
  }

  private CardEntry modelCardEntryFor(String id) {
    if (id == null || id.isEmpty()) return new CardEntry("", 0, null, true);
    String formId = id;
    String matId = activeWeaponMaterialId();
    int sep = id.indexOf(':');
    if (sep > 0 && sep + 1 < id.length()) {
      formId = id.substring(0, sep);
      matId = CosmeticModels.resolveMaterial(id.substring(sep + 1)).id;
    }
    CosmeticModels.Entry entry = CosmeticModels.byId(formId);
    if (entry == null) return new CardEntry("", 0, null, true);
    int accent = modelCardAccent(entry);
    final String resolvedMat = entry.isArmor() ? "" : matId;
    String variantKey = entry.id + ":" + resolvedMat + ":" + Integer.toHexString(accent & 0xffffff);
    CardEntry existing = modelCardCache.get(variantKey);
    if (existing != null && existing.decoded) return existing;
    String cacheKey = entry.isArmor()
        ? ("builtin:" + entry.kind + ":" + entry.id)
        : ("builtin:" + entry.kind + ":" + entry.id + ":" + resolvedMat);
    CosmeticModelMesh mesh = modelCache.get(cacheKey);
    if (mesh == null && !entry.isArmor() && entry.resourcePath != null
        && entry.resourcePath.startsWith("procedural:sword:")) {
      mesh = PixelSwordMeshBuilder.build(entry.id, resolvedMat);
      modelCache.put(cacheKey, mesh);
    }
    if (mesh != null) {
      CardEntry ready = new CardEntry("model:" + variantKey, revCounter.incrementAndGet(),
          PlayerSkinData.forModelCard(mesh, accent, entry.isArmor()), true);
      modelCardCache.put(variantKey, ready);
      return ready;
    }
    final String queueKey = entry.id + ":" + resolvedMat;
    if (modelCardQueued.add(queueKey)) {
      submitAsync(cardExecutor, () -> prefetchModelCardAsync(entry, resolvedMat),
          () -> modelCardQueued.remove(queueKey));
    }
    return existing != null ? existing : new CardEntry("model:" + variantKey, 0, null, false);
  }

  private void prefetchModelCardAsync(CosmeticModels.Entry entry, String materialId) {
    final String queueKey = entry.id + ":" + materialId;
    try {
      String cacheKey = entry.isArmor()
          ? ("builtin:" + entry.kind + ":" + entry.id)
          : ("builtin:" + entry.kind + ":" + entry.id + ":" + materialId);
      CosmeticModelMesh mesh = modelCache.get(cacheKey);
      if (mesh == null) {
        if (!entry.isArmor() && entry.resourcePath != null
            && entry.resourcePath.startsWith("procedural:sword:")) {
          mesh = PixelSwordMeshBuilder.build(entry.id, materialId);
        } else {
          mesh = CosmeticModelLoader.loadResource(entry.resourcePath, entry.isArmor());
        }
        modelCache.put(cacheKey, mesh);
      }
      int accent = modelCardAccent(entry);
      String variantKey = entry.id + ":" + materialId + ":" + Integer.toHexString(accent & 0xffffff);
      int rev = revCounter.incrementAndGet();
      modelCardCache.put(variantKey, new CardEntry("model:" + variantKey, rev,
          PlayerSkinData.forModelCard(mesh, accent, entry.isArmor()), true));
    } catch (Exception ignored) {
    } finally {
      modelCardQueued.remove(queueKey);
    }
  }

  private CardEntry cardEntryFor(String id) {
    if (!OfficialCapes.isEnabled()) return new CardEntry("", 0, null, true);
    OfficialCapes.Cape cape = OfficialCapes.byId(id);
    if (cape == null) return new CardEntry("", 0, null, true);
    CardEntry entry = cardCache.get(cape.id);
    if (entry != null && entry.decoded) return entry;

    String key = "official:" + cape.id;
    CosmeticsImages.DecodedTexture cached = capeCache.get(key);
    if (cached != null) {
      CardEntry ready = new CardEntry(cape.id, revCounter.incrementAndGet(),
          new PlayerSkinData(
              EMPTY_SKIN_PIXELS, 1, 1, cached.pixels, cached.width, cached.height, false),
          true);
      cardCache.put(cape.id, ready);
      return ready;
    }
    if (entry == null) {
      int[] synth = DefaultSkins.synthesizeCape(cape);
      boolean staticCape = cape.textureHash == null || cape.textureHash.isEmpty();
      CardEntry fallback = new CardEntry(cape.id, 0,
          new PlayerSkinData(EMPTY_SKIN_PIXELS, 1, 1, synth, 64, 32, false), staticCape);
      CardEntry existing = cardCache.putIfAbsent(cape.id, fallback);
      entry = existing != null ? existing : fallback;
    }
    if (cape.textureUrl() != null) {
      Long retryAt = cardRetryAfter.get(cape.id);
      if ((retryAt == null || System.currentTimeMillis() >= retryAt) && cardQueued.add(cape.id)) {
        submitAsync(cardExecutor, () -> prefetchCardAsync(cape), () -> cardQueued.remove(cape.id));
      }
    }
    return entry;
  }

  private void prefetchCardAsync(OfficialCapes.Cape cape) {
    try {
      String key = "official:" + cape.id;
      CosmeticsImages.DecodedTexture decoded = resolveAndCacheOfficialCape(key, cape);
      if (decoded != null) {
        int rev = revCounter.incrementAndGet();
        cardCache.put(cape.id,
            new CardEntry(cape.id, rev,
                new PlayerSkinData(EMPTY_SKIN_PIXELS, 1, 1, decoded.pixels, decoded.width,
                    decoded.height, false),
                true));
        cardRetryAfter.remove(cape.id);
      } else {
        cardRetryAfter.put(cape.id, System.currentTimeMillis() + CARD_RETRY_COOLDOWN_MS);
      }
    } catch (Exception ignored) {
      cardRetryAfter.put(cape.id, System.currentTimeMillis() + CARD_RETRY_COOLDOWN_MS);
    } finally {
      cardQueued.remove(cape.id);
    }
  }

  private void loadCustomCapeAsync(String key, String pathStr) {
    try {
      Path file = Paths.get(pathStr);
      CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeCapeFile(file);
      capeCache.put(key, decoded);
      if (key.equals(requestedCapeKey)) {
        activeCape = new ResolvedTexture(
            key, decoded.pixels, decoded.width, decoded.height, false, revCounter.incrementAndGet());
        errorKey = "";
        dirtyPlatform.set(true);
      }
    } catch (Exception err) {
      if (key.equals(requestedCapeKey)) errorKey = "cosmetics.error.invalid_file";
    } finally {
      if (key.equals(requestedCapeKey)) capeLoading = false;
    }
  }

  private void loadCustomSkinAsync(String key, String pathStr) {
    try {
      Path file = Paths.get(pathStr);
      CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeSkinFile(file);
      skinCache.put(key, decoded);
      if (key.equals(requestedSkinKey)) {
        activeSkin = new ResolvedTexture(key, decoded.pixels, decoded.width, decoded.height,
            decoded.slim, revCounter.incrementAndGet());
        errorKey = "";
        dirtyPlatform.set(true);
      }
    } catch (Exception err) {
      if (key.equals(requestedSkinKey)) errorKey = "cosmetics.error.invalid_file";
    } finally {
      if (key.equals(requestedSkinKey)) skinLoading = false;
    }
  }

  private void loadNickSkinAsync(String key, String nick) {
    try {
      String lower = nick.toLowerCase(Locale.ROOT);
      Path cachedPng = cacheRoot == null ? null : cacheRoot.resolve("skins").resolve(lower + ".png");
      Path cachedMeta =
          cacheRoot == null ? null : cacheRoot.resolve("skins").resolve(lower + ".slim");
      if (cachedPng != null && Files.isRegularFile(cachedPng)) {
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(cachedPng).toMillis();
        if (age >= 0 && age < NICK_CACHE_TTL_MS) {
          CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeSkinFile(cachedPng);
          boolean slim = Files.isRegularFile(cachedMeta)
              ? "true".equalsIgnoreCase(
                    new String(Files.readAllBytes(cachedMeta), StandardCharsets.UTF_8).trim())
              : decoded.slim;
          CosmeticsImages.DecodedTexture withMeta =
              new CosmeticsImages.DecodedTexture(decoded.pixels, decoded.width, decoded.height, slim);
          skinCache.put(key, withMeta);
          if (key.equals(requestedSkinKey)) {
            activeSkin = new ResolvedTexture(key, withMeta.pixels, withMeta.width, withMeta.height,
                withMeta.slim, revCounter.incrementAndGet());
            errorKey = "";
            dirtyPlatform.set(true);
            skinLoading = false;
            return;
          }
        }
      }

      ResolvedNick fetched = fetchPlayerSkinByNickname(nick);
      if (fetched == null) throw new IOException("No skin found for " + nick);
      CosmeticsImages.DecodedTexture decoded = CosmeticsImages.decodeSkin(fetched.png);
      boolean slim = fetched.slim != null ? fetched.slim : decoded.slim;
      CosmeticsImages.DecodedTexture result =
          new CosmeticsImages.DecodedTexture(decoded.pixels, decoded.width, decoded.height, slim);
      skinCache.put(key, result);
      if (cachedPng != null) {
        try {
          Files.createDirectories(cachedPng.getParent());
          Files.write(cachedPng, fetched.png);
          if (cachedMeta != null)
            Files.write(cachedMeta, Boolean.toString(slim).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
      }
      if (key.equals(requestedSkinKey)) {
        activeSkin = new ResolvedTexture(key, result.pixels, result.width, result.height,
            result.slim, revCounter.incrementAndGet());
        errorKey = "";
        dirtyPlatform.set(true);
      }
    } catch (Exception err) {
      if (key.equals(requestedSkinKey)) errorKey = "cosmetics.error.nick_not_found";
    } finally {
      if (key.equals(requestedSkinKey)) skinLoading = false;
    }
  }

  private static final class ResolvedNick {
    final byte[] png;
    final Boolean slim;

    ResolvedNick(byte[] png, Boolean slim) {
      this.png = png;
      this.slim = slim;
    }
  }

  private ResolvedNick fetchPlayerSkinByNickname(String nick) throws IOException {
    String uuid = resolveMojangUuid(nick);
    if (uuid != null) {
      try {
        byte[] sessionJson = fetcher.fetch(
            "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        ResolvedNick sessionSkin = parseSessionProfile(sessionJson);
        if (sessionSkin != null) return sessionSkin;
      } catch (IOException ignored) {
      }
    }
    try {
      byte[] ashconBytes = fetcher.fetch("https://api.ashcon.app/mojang/v2/user/" + nick);
      JsonObject root = new JsonParser()
                            .parse(new String(ashconBytes, StandardCharsets.UTF_8))
                            .getAsJsonObject();
      JsonObject textures = root.getAsJsonObject("textures");
      if (textures != null) {
        Boolean slim = textures.has("slim") ? Boolean.valueOf(textures.get("slim").getAsBoolean())
                                            : null;
        JsonObject skin = textures.getAsJsonObject("skin");
        if (skin != null && skin.has("url")) {
          byte[] png = fetcher.fetch(skin.get("url").getAsString());
          if (png != null && png.length > 0) return new ResolvedNick(png, slim);
        }
      }
    } catch (Exception ignored) {
    }
    byte[] minotar = fetcher.fetch("https://minotar.net/skin/" + nick);
    return minotar != null && minotar.length > 0 ? new ResolvedNick(minotar, null) : null;
  }

  private String resolveMojangUuid(String nick) {
    String[] endpoints = {
        "https://api.mojang.com/users/profiles/minecraft/" + nick,
        "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + nick,
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

  private ResolvedNick parseSessionProfile(byte[] sessionJson) throws IOException {
    if (sessionJson == null || sessionJson.length == 0) return null;
    JsonObject root =
        new JsonParser().parse(new String(sessionJson, StandardCharsets.UTF_8)).getAsJsonObject();
    if (!root.has("properties")) return null;
    for (JsonElement el : root.getAsJsonArray("properties")) {
      JsonObject prop = el.getAsJsonObject();
      if (!"textures".equals(prop.get("name").getAsString())) continue;
      String decodedJson = new String(
          Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
      JsonObject payload = new JsonParser().parse(decodedJson).getAsJsonObject();
      JsonObject textures = payload.getAsJsonObject("textures");
      if (textures == null || !textures.has("SKIN")) continue;
      JsonObject skinObj = textures.getAsJsonObject("SKIN");
      String url = skinObj.get("url").getAsString();
      Boolean slim = null;
      if (skinObj.has("metadata")) {
        JsonObject meta = skinObj.getAsJsonObject("metadata");
        if (meta.has("model")) slim = Boolean.valueOf("slim".equals(meta.get("model").getAsString()));
      }
      byte[] png = fetcher.fetch(url);
      if (png != null && png.length > 0) return new ResolvedNick(png, slim);
    }
    return null;
  }

  private void pushPlatformState() {
    Settings.Cosmetics c = settings.cosmetics;
    ResolvedTexture skin = activeSkin;
    ResolvedTexture cape = activeCape;
    String capeMode = c == null ? "default" : c.capeMode;
    boolean capeOverride = "none".equals(capeMode) || (!"default".equals(capeMode) && cape != null);
    boolean capeSuppressed = "none".equals(capeMode);
    Boolean slimOverride = resolveSlimOverride(c, skin);
    int skinRev = skin == null ? 0 : skin.revision;
    int capeRev = cape == null ? 0 : cape.revision;
    String signature = skinRev + ":" + capeOverride + ":" + capeSuppressed + ":" + capeRev + ":"
        + slimOverride;
    if (signature.equals(lastPushedSignature)) return;
    lastPushedSignature = signature;
    platform.updateCosmetics(skin == null ? null : skin.pixels, skin == null ? 0 : skin.width,
        skin == null ? 0 : skin.height, skinRev, capeOverride, capeSuppressed,
        cape == null ? null : cape.pixels, cape == null ? 0 : cape.width,
        cape == null ? 0 : cape.height, capeRev, slimOverride);
  }

  private void pushModelPlatformState() {
    Settings.Cosmetics c = settings.cosmetics;
    if (c == null) return;
    CosmeticModelMesh weapon = "none".equals(c.weaponMode) ? null : activeWeapon;
    CosmeticModelMesh armor = null;
    int armorMask = 0;
    String wTarget = c.weaponTarget == null ? "swords" : c.weaponTarget;
    String aTrigger = "always";
    String sig = (weapon == null ? 0 : weapon.meshId) + ":" + wTarget + ":" + c.weaponColor + ":"
        + Float.floatToIntBits(c.weaponScale) + ":" + c.weaponFirstPerson + ":"
        + c.weaponThirdPerson + "|0";
    if (sig.equals(lastPushedModelSignature)) return;
    lastPushedModelSignature = sig;
    platform.updateCosmeticModels(weapon, wTarget, c.weaponColor, c.weaponScale,
        c.weaponFirstPerson, c.weaponThirdPerson, armor, aTrigger, armorMask, false,
        c.armorColor, c.armorScale);
  }

  private static int armorSlotMask(Settings.Cosmetics c) {
    if (c == null) return 0;
    int mask = 0;
    if (c.armorHelmet) mask |= PlayerSkinData.SLOT_HEAD;
    if (c.armorChestplate) mask |= PlayerSkinData.SLOT_CHEST;
    if (c.armorLeggings) mask |= PlayerSkinData.SLOT_LEGS;
    if (c.armorBoots) mask |= PlayerSkinData.SLOT_BOOTS;
    return mask;
  }

  private static Boolean resolveSlimOverride(Settings.Cosmetics c, ResolvedTexture skin) {
    String model = c == null || c.skinModel == null ? "auto" : c.skinModel;
    if ("slim".equals(model)) return Boolean.TRUE;
    if ("classic".equals(model)) return Boolean.FALSE;
    return skin == null ? null : Boolean.valueOf(skin.slim);
  }

  public PlayerSkin composePreview(PlayerSkin base) {
    Settings.Cosmetics c = settings.cosmetics;
    ResolvedTexture skin = activeSkin;
    ResolvedTexture cape = activeCape;
    CosmeticModelMesh weapon = c != null && !"none".equals(c.weaponMode) ? activeWeapon : null;
    CosmeticModelMesh armor = null;
    int weaponColor = c == null ? 0xff38bdf8 : c.weaponColor;
    float weaponScale = c == null ? 1f : c.weaponScale;
    int armorColor = c == null ? 0xff38bdf8 : c.armorColor;
    float armorScale = c == null ? 1f : c.armorScale;
    int armorMask = 0;

    String capeMode = c == null || c.capeMode == null ? "default" : c.capeMode;
    String modelMode = c == null || c.skinModel == null ? "auto" : c.skinModel;
    int sRev = skin == null ? 0 : skin.revision;
    int cRev = cape == null ? 0 : cape.revision;
    int wId = weapon == null ? 0 : weapon.meshId;
    int aId = armor == null ? 0 : armor.meshId;

    if (base == lastPreviewBase && sRev == lastPreviewSkinRev && cRev == lastPreviewCapeRev
        && wId == lastPreviewWeaponId && weaponColor == lastPreviewWeaponColor
        && Float.compare(weaponScale, lastPreviewWeaponScale) == 0
        && aId == lastPreviewArmorId && armorColor == lastPreviewArmorColor
        && Float.compare(armorScale, lastPreviewArmorScale) == 0
        && armorMask == lastPreviewArmorMask
        && capeMode.equals(lastPreviewCapeMode) && modelMode.equals(lastPreviewModelMode)
        && cachedPreview != null) {
      return cachedPreview;
    }

    PlayerSkin fallback = base != null ? base : DefaultSkins.forModel(false);
    int[] bodyPixels = skin != null ? skin.pixels : fallback.bodyPixels();
    int bodyW = skin != null ? skin.width : fallback.bodyWidth();
    int bodyH = skin != null ? skin.height : fallback.bodyHeight();

    int[] capePixels;
    int capeW;
    int capeH;
    if ("none".equals(capeMode)) {
      capePixels = null;
      capeW = 0;
      capeH = 0;
    } else if (!"default".equals(capeMode) && cape != null) {
      capePixels = cape.pixels;
      capeW = cape.width;
      capeH = cape.height;
    } else {
      capePixels = fallback.capePixels();
      capeW = capePixels == null ? 0 : fallback.capeWidth();
      capeH = capePixels == null ? 0 : fallback.capeHeight();
    }

    Boolean slimOverride = resolveSlimOverride(c, skin);
    boolean slim = slimOverride != null ? slimOverride.booleanValue() : fallback.slimModel();

    cachedPreview = new PlayerSkinData(bodyPixels, bodyW, bodyH, capePixels, capeW, capeH, slim)
        .withCosmeticModels(weapon, weaponColor, weaponScale, armor, armorColor, armorScale,
            armorMask);
    lastPreviewBase = base;
    lastPreviewSkinRev = sRev;
    lastPreviewCapeRev = cRev;
    lastPreviewWeaponId = wId;
    lastPreviewWeaponColor = weaponColor;
    lastPreviewWeaponScale = weaponScale;
    lastPreviewArmorId = aId;
    lastPreviewArmorColor = armorColor;
    lastPreviewArmorScale = armorScale;
    lastPreviewArmorMask = armorMask;
    lastPreviewCapeMode = capeMode;
    lastPreviewModelMode = modelMode;
    return cachedPreview;
  }

  public void selectOfficialCape(String id) {
    if (!OfficialCapes.isEnabled()) return;
    OfficialCapes.Cape cape = OfficialCapes.byId(id);
    if (cape == null) return;
    settings.cosmetics.capeMode = "official";
    settings.cosmetics.officialCape = cape.id;
    errorKey = "";
    requestedCapeKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void resetCapeToAccount() {
    settings.cosmetics.capeMode = "default";
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void disableCape() {
    settings.cosmetics.capeMode = "none";
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void applyCustomCapePath(String rawPath) {
    String path = sanitizePathInput(rawPath);
    if (path.isEmpty()) return;
    settings.cosmetics.customCapePath = path;
    settings.cosmetics.capeMode = "custom";
    requestedCapeKey = "";
    capeCache.remove("custom:" + path);
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void applySkinNickname(String rawNick) {
    String nick = rawNick == null ? "" : rawNick.trim();
    if (!VALID_NICK.matcher(nick).matches()) {
      errorKey = "cosmetics.error.invalid_nick";
      return;
    }
    settings.cosmetics.skinNickname = nick;
    settings.cosmetics.skinMode = "nick";
    requestedSkinKey = "";
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void applyCustomSkinPath(String rawPath) {
    String path = sanitizePathInput(rawPath);
    if (path.isEmpty()) return;
    settings.cosmetics.customSkinPath = path;
    settings.cosmetics.skinMode = "custom";
    requestedSkinKey = "";
    skinCache.remove("custom:" + path);
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void resetSkinToAccount() {
    settings.cosmetics.skinMode = "default";
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void setSkinModel(String model) {
    if ("classic".equals(model) || "slim".equals(model) || "auto".equals(model)) {
      settings.cosmetics.skinModel = model;
      dirtyPlatform.set(true);
      onChanged.run();
    }
  }

  public void selectBuiltinWeapon(String id) {
    CosmeticModels.Entry entry = CosmeticModels.weaponById(id);
    if (entry == null) return;
    settings.cosmetics.weaponMode = "builtin";
    settings.cosmetics.builtinWeapon = entry.id;
    CosmeticModels.Material mat = CosmeticModels.resolveMaterial(settings.cosmetics.weaponMaterial);
    settings.cosmetics.weaponMaterial = mat.id;
    settings.cosmetics.weaponColor = mat.primaryColor;
    errorKey = "";
    requestedWeaponKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void selectWeaponMaterial(String id) {
    CosmeticModels.Material mat = CosmeticModels.materialById(id);
    if (mat == null) return;
    settings.cosmetics.weaponMaterial = mat.id;
    settings.cosmetics.weaponColor = mat.primaryColor;
    if (!"builtin".equals(settings.cosmetics.weaponMode)
        || CosmeticModels.weaponById(settings.cosmetics.builtinWeapon) == null) {
      settings.cosmetics.weaponMode = "builtin";
      if (CosmeticModels.weaponById(settings.cosmetics.builtinWeapon) == null) {
        settings.cosmetics.builtinWeapon = CosmeticModels.DEFAULT_WEAPON;
      }
    }
    errorKey = "";
    requestedWeaponKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void resetWeapon() {
    settings.cosmetics.weaponMode = "none";
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void applyCustomWeaponPath(String rawPath) {
    String path = sanitizePathInput(rawPath);
    if (path.isEmpty()) return;
    settings.cosmetics.customWeaponPath = path;
    settings.cosmetics.weaponMode = "custom";
    requestedWeaponKey = "";
    modelCache.remove("custom:weapon:" + path);
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void setWeaponTarget(String target) {
    if ("swords".equals(target) || "weapons".equals(target) || "tools".equals(target)
        || "all".equals(target)) {
      settings.cosmetics.weaponTarget = target;
      dirtyPlatform.set(true);
      onChanged.run();
    }
  }

  public void setWeaponColor(int argb) {
    settings.cosmetics.weaponColor = 0xff000000 | (argb & 0xffffff);
    dirtyPlatform.set(true);
    onChanged.run();
  }

  public void selectBuiltinArmor(String id) {
    resetArmor();
  }

  public void resetArmor() {
    settings.cosmetics.armorMode = "none";
    errorKey = "";
    syncFromSettings();
    onChanged.run();
  }

  public void applyCustomArmorPath(String rawPath) {
    resetArmor();
  }

  public void setArmorTrigger(String trigger) {
    if ("always".equals(trigger) || "equipped".equals(trigger)) {
      settings.cosmetics.armorTrigger = trigger;
      dirtyPlatform.set(true);
      onChanged.run();
    }
  }

  public void setArmorColor(int argb) {
    settings.cosmetics.armorColor = 0xff000000 | (argb & 0xffffff);
    dirtyPlatform.set(true);
    onChanged.run();
  }

  public void browseCustomCape(String title) {
    launchPicker(title, 0);
  }

  public void browseCustomSkin(String title) {
    launchPicker(title, 1);
  }

  public void browseCustomWeapon(String title) {
    launchPicker(title, 2);
  }

  public void browseCustomArmor(String title) {
  }

  private void launchPicker(String title, int target) {
    if (!pickerBusy.compareAndSet(false, true)) return;
    errorKey = "";
    Thread thread = new Thread(() -> {
      try {
        String chosen = target >= 2
            ? platform.chooseModelFile(title)
            : platform.chooseImageFile(title);
        if (chosen == null || chosen.trim().isEmpty()) return;
        if ("__NO_PICKER__".equals(chosen)) {
          errorKey = "cosmetics.error.no_picker";
          return;
        }
        if (target == 0) pendingPickedCape = chosen;
        else if (target == 1) pendingPickedSkin = chosen;
        else if (target == 2) pendingPickedWeapon = chosen;
        else pendingPickedArmor = chosen;
      } catch (Throwable ignored) {
        errorKey = "cosmetics.error.no_picker";
      } finally {
        pickerBusy.set(false);
      }
    }, "NightClient-FilePicker");
    thread.setDaemon(true);
    thread.start();
  }

  public boolean isLoading() {
    return capeLoading || skinLoading || weaponLoading || armorLoading || pickerBusy.get();
  }

  public CosmeticModelMesh activeWeaponMesh() {
    return activeWeapon;
  }

  public CosmeticModelMesh activeArmorMesh() {
    return activeArmor;
  }

  public String weaponStatusSummary(String language) {
    Settings.Cosmetics c = settings.cosmetics;
    if (c == null) return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.weapon_none");
    if ("builtin".equals(c.weaponMode)) {
      CosmeticModels.Entry entry = CosmeticModels.weaponById(c.builtinWeapon);
      if (entry != null) {
        CosmeticModels.Material mat = CosmeticModels.resolveMaterial(c.weaponMaterial);
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("shape", entry.name(language));
        vars.put("material", mat.name(language));
        return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.weapon_combo", vars);
      }
    }
    if ("custom".equals(c.weaponMode) && c.customWeaponPath != null && !c.customWeaponPath.isEmpty()) {
      return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.weapon_active",
          Collections.singletonMap("value", fileName(c.customWeaponPath)));
    }
    return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.weapon_none");
  }

  public String armorStatusSummary(String language) {
    Settings.Cosmetics c = settings.cosmetics;
    if (c == null) return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.armor_none");
    if ("builtin".equals(c.armorMode)) {
      CosmeticModels.Entry entry = CosmeticModels.armorById(c.builtinArmor);
      if (entry != null) {
        return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.armor_active",
            Collections.singletonMap("value", entry.name(language)));
      }
    }
    if ("custom".equals(c.armorMode) && c.customArmorPath != null && !c.customArmorPath.isEmpty()) {
      return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.armor_active",
          Collections.singletonMap("value", fileName(c.customArmorPath)));
    }
    return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.armor_none");
  }

  public String errorKey() {
    return errorKey;
  }

  public String skinStatusSummary(String language) {
    Settings.Cosmetics c = settings.cosmetics;
    if (c == null) return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.default_skin");
    if ("nick".equals(c.skinMode) && c.skinNickname != null && !c.skinNickname.isEmpty())
      return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.nick_skin",
          Collections.singletonMap("value", c.skinNickname));
    if ("custom".equals(c.skinMode) && c.customSkinPath != null && !c.customSkinPath.isEmpty())
      return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.custom_skin",
          Collections.singletonMap("value", fileName(c.customSkinPath)));
    return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.default_skin");
  }

  public String capeStatusSummary(String language) {
    Settings.Cosmetics c = settings.cosmetics;
    if (c == null) return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.cape_default");
    if ("none".equals(c.capeMode))
      return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.cape_none");
    if ("official".equals(c.capeMode) && OfficialCapes.isEnabled()) {
      OfficialCapes.Cape cape = OfficialCapes.byId(c.officialCape);
      if (cape != null)
        return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.cape_official",
            Collections.singletonMap("value", cape.name(language)));
    }
    if ("custom".equals(c.capeMode) && c.customCapePath != null && !c.customCapePath.isEmpty())
      return dev.nightclient.i18n.I18n.format(language, "cosmetics.status.cape_custom",
          Collections.singletonMap("value", fileName(c.customCapePath)));
    return dev.nightclient.i18n.I18n.text(language, "cosmetics.status.cape_default");
  }

  private static String sanitizePathInput(String raw) {
    if (raw == null) return "";
    String s = raw.trim();
    if (s.length() >= 2
        && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))))
      s = s.substring(1, s.length() - 1).trim();
    return s;
  }

  private static String fileName(String path) {
    if (path == null) return "";
    int a = path.lastIndexOf('/');
    int b = path.lastIndexOf('\\');
    int idx = Math.max(a, b);
    return idx >= 0 && idx + 1 < path.length() ? path.substring(idx + 1) : path;
  }

  private static byte[] httpGet(String rawUrl) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
    try {
      conn.setConnectTimeout(6000);
      conn.setReadTimeout(8000);
      conn.setInstanceFollowRedirects(true);
      conn.setRequestProperty("User-Agent", "NightClient/1.0");
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + rawUrl);
      try (InputStream in = conn.getInputStream()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0, n;
        while ((n = in.read(buf)) >= 0) {
          total += n;
          if (total > 32 * 1024 * 1024) throw new IOException("Remote asset too large");
          out.write(buf, 0, n);
        }
        return out.toByteArray();
      }
    } finally {
      conn.disconnect();
    }
  }
}
