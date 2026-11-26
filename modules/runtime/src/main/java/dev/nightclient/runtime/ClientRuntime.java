package dev.nightclient.runtime;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PlayerHealthView;
import dev.nightclient.api.PlayerPingView;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.TotemCounterView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.CommandLayoutFix;
import dev.nightclient.config.ConfigStore;
import dev.nightclient.config.DisplayProtection;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.i18n.I18n;
import dev.nightclient.renderer.SurfacePainter;
import dev.nightclient.renderer.WorldRibbon;
import dev.nightclient.renderer.map.MinimapRenderer;
import dev.nightclient.renderer.map.RegionCacheManager;
import dev.nightclient.ui.FeatureHudRenderer;
import dev.nightclient.ui.HudRenderer;
import dev.nightclient.ui.MapSettingsController;
import dev.nightclient.ui.MenuController;
import dev.nightclient.ui.UiActions;
import dev.nightclient.ui.WaypointsScreenController;
import dev.nightclient.ui.WorldMapController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ClientRuntime implements UiActions {
  private static volatile ClientRuntime instance;
  private final PlatformServices platform;
  private final ConfigStore store;
  private final Settings settings;
  private final MenuController menu;
  private final ChatHudController chatHud;
  private final dev.nightclient.skin.SkinManager skins;
  private final HwHelperManager hwhelper;
  private final LiteApiManager liteApi;
  private final CosmeticsManager cosmetics;
  private final AccountManager accounts;
  private final CustomAudioPlayer customAudio = new CustomAudioPlayer();
  private final java.util.concurrent.atomic.AtomicBoolean audioPickerBusy =
      new java.util.concurrent.atomic.AtomicBoolean();
  private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> pendingMainThreadTasks =
      new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
  private final dev.nightclient.ui.AccountOverlayRenderer accountOverlay =
      new dev.nightclient.ui.AccountOverlayRenderer();
  private final RegionCacheManager mapCache;
  private final WorldMapController worldMap;
  private final MapSettingsController mapSettings;
  private final WaypointsScreenController waypointsMenu;
  private volatile boolean mapEnlargeHeld;
  private volatile boolean mapEnlargeToggled;
  private float mapEnlargeProgress;
  private double mapEnlargeLastTime;
  private dev.nightclient.ui.skin.SkinView hudSkin;
  private dev.nightclient.skin.SkinDocument hudDocument;
  private volatile String cachedSkinPreset;
  private volatile dev.nightclient.skin.SkinDocument cachedSkinSource;
  private volatile dev.nightclient.skin.SkinDocument cachedSkinDocument;
  private long hudFrameSerial;
  private long hudFrameReadySerial;
  private HudFrame hudFrame;
  private boolean hudFrameRendering;
  private volatile float preparedSaturation = 1;
  private volatile boolean worldActive;
  private List<Cooldown> cooldowns = Collections.emptyList();
  private List<InventorySlot> inventory = Collections.emptyList();
  private List<TntView> tnts = Collections.emptyList();
  private List<InventorySlot> armor = Collections.emptyList();
  private List<PotionView> potions = Collections.emptyList();
  private VitalsView vitals = VitalsView.EMPTY;
  private TargetView target;
  private java.util.Map<String, Object> hudNetworkInfo, hudKeystrokes, hudCoordinates,
      hudScoreboard;
  private TargetView hudLocalPlayer;
  private String hudGameVersion;
  private int hudPing, hudFps;
  private boolean hudNetworkInfoRead, hudKeystrokesRead, hudCoordinatesRead, hudScoreboardRead,
      hudLocalPlayerRead, hudGameVersionRead, hudPingRead, hudFpsRead, hudCameraViewRead;
  private WorldRibbon.View hudCameraView;
  private final double[] projScratch = new double[5];
  private dev.nightclient.ui.skin.UiCatalog moduleCatalog;
  private boolean viewmodelEditing;
  private String ownNickname = "";
  private final DamageIndicators damageIndicators = new DamageIndicators();
  private final ExplosionRingLogic.ServerExplosionLearner explosionLearner =
      new ExplosionRingLogic.ServerExplosionLearner();
  private final EffectAlertLogic effectAlertLogic = new EffectAlertLogic();
  private final TapeMouseLogic tapeMouseLogic = new TapeMouseLogic();
  private volatile SoundControlLogic.MentionToast latestMentionToast = null;
  private long lastMentionSoundMs = 0L;
  private boolean zoomHeld;
  private float zoomTarget = 1, zoomCurrent = 1, effectiveFov = 70;
  private double zoomUpdatedAt;
  private String undo;
  private final java.util.concurrent.atomic.AtomicLong settingsRevision =
      new java.util.concurrent.atomic.AtomicLong(1L);
  private long pendingConfigSaveAt;

  @Override
  public long settingsRevision() {
    return settingsRevision.get();
  }

  private ClientRuntime(PlatformServices platform) {
    this.platform = platform;
    dev.nightclient.config.FeatureSupport.configureForGameVersion(platform.gameVersion());
    this.store = new ConfigStore(platform.configDirectory());
    this.settings = store.load();
    settings.validate();
    this.ownNickname = platform.ownNickname();
    this.skins =
        new dev.nightclient.skin.SkinManager(platform.configDirectory().resolve("nightclient"),
            document
            -> new dev.nightclient.ui.skin.SkinValidator().validate(
                dev.nightclient.skin.DefaultSkin.resolve(settings.skin.preset, document)));
    validateBundledThemes();
    if (settings.skin.enabled) skins.reload(settings.skin.file);
    settings.skinDocument = skinDocument();
    this.menu = new MenuController(settings, this);
    this.chatHud = new ChatHudController(settings, this, menu);
    this.hwhelper = new HwHelperManager(platform, settings, this::changed);
    this.liteApi = new LiteApiManager(platform::sendPluginMessage, this::onFeaturePolicyChanged);
    this.cosmetics =
        new CosmeticsManager(platform.configDirectory(), platform, settings, this::changed);
    this.accounts = new AccountManager(platform.configDirectory(), platform, settings, () -> {
      this.ownNickname = platform.ownNickname();
      invalidateHudFrame();
      if (this.menu != null) this.menu.invalidatePresentation();
    });
    this.mapCache = new RegionCacheManager(platform.configDirectory());
    this.mapCache.syncSettings(settings);
    this.worldMap = new WorldMapController(settings, new WorldMapController.Host() {
      @Override
      public RegionCacheManager mapCache() {
        return mapCache;
      }

      @Override
      public dev.nightclient.api.MapRadarModels.RadarSnapshot radarSnapshot() {
        return platform.mapRadarSnapshot(0f);
      }

      @Override
      public void openWaypointCreate(double x, double y, double z) {
        platform.openWaypointCreate(x, y, z);
      }

      @Override
      public void clipboard(String value) {
        ClientRuntime.this.clipboard(value);
      }

      @Override
      public void teleportTo(int x, int y, int z) {
        platform.sendCommandToServer("tp " + x + " " + y + " " + z);
      }

      @Override
      public void checkpoint() {
        ClientRuntime.this.checkpoint();
      }

      @Override
      public void changed() {
        ClientRuntime.this.changed();
      }

      @Override
      public void closeWorldMap() {
        platform.closeWorldMap();
      }

      @Override
      public void openMapSettings() {
        platform.openMapSettings();
      }

      @Override
      public void openWaypointsMenu() {
        platform.openWaypointsMenu();
      }

      @Override
      public void clearCurrentWorldCache() {
        clearCurrentWorldMapCache();
      }
    });
    this.mapSettings = new MapSettingsController(settings, new MapSettingsController.Host() {
      @Override
      public RegionCacheManager mapCache() {
        return mapCache;
      }

      @Override
      public String keyBindingLabel(String id) {
        return ClientRuntime.this.keyBindingLabel(id);
      }

      @Override
      public void setKeyBinding(String id, String nativeKeyName) {
        ClientRuntime.this.setKeyBinding(id, nativeKeyName);
      }

      @Override
      public void clearCurrentWorldCache() {
        clearCurrentWorldMapCache();
      }

      @Override
      public void clearAllMapCache() {
        ClientRuntime.this.clearAllMapCache();
      }

      @Override
      public void openWorldMap() {
        platform.openWorldMap();
      }

      @Override
      public void openWaypointsMenu() {
        platform.openWaypointsMenu();
      }

      @Override
      public void openHudEditor() {
        platform.openHudEditor();
      }

      @Override
      public void checkpoint() {
        ClientRuntime.this.checkpoint();
      }

      @Override
      public void changed() {
        ClientRuntime.this.changed();
      }

      @Override
      public void close() {
        platform.closeMapSettings();
      }
    });
    this.waypointsMenu = new WaypointsScreenController(settings, new WaypointsScreenController.Host() {
      @Override
      public double[] playerPosition() {
        return platform.playerPosition();
      }

      @Override
      public String currentDimension() {
        return ClientRuntime.this.currentDimension();
      }

      @Override
      public String currentWorldId() {
        return ClientRuntime.this.currentWorldId();
      }

      @Override
      public String currentWorldLabel() {
        return ClientRuntime.this.currentWorldLabel();
      }

      @Override
      public void teleportTo(int x, int y, int z) {
        platform.sendCommandToServer("tp " + x + " " + y + " " + z);
      }

      @Override
      public void clipboard(String value) {
        ClientRuntime.this.clipboard(value);
      }

      @Override
      public void checkpoint() {
        ClientRuntime.this.checkpoint();
      }

      @Override
      public void changed() {
        ClientRuntime.this.changed();
      }

      @Override
      public void close() {
        platform.closeWaypointsMenu();
      }
    });
    prepareSettings();
  }

  private static void validateBundledThemes() {
    dev.nightclient.ui.skin.SkinValidator validator = new dev.nightclient.ui.skin.SkinValidator();
    for (String id : dev.nightclient.skin.DefaultSkin.presetIds()) {
      try {
        validator.validate(dev.nightclient.skin.DefaultSkin.document(id));
      } catch (Exception error) {
        System.err.println("[NightClient] Bundled theme '" + id + "' failed validation: "
            + (error.getMessage() == null ? error.toString() : error.getMessage()));
      }
    }
  }

  public static synchronized ClientRuntime initialize(PlatformServices platform) {
    if (instance == null) instance = new ClientRuntime(platform);
    return instance;
  }

  public static ClientRuntime get() {
    return instance;
  }

  public static String language() {
    ClientRuntime runtime = instance;
    return I18n.language(runtime == null ? null : runtime.settings.language);
  }

  public Settings settings() {
    return settings;
  }

  public boolean hasCustomHud() {
    return skinDocument().hud() != null;
  }

  public boolean skinHudUsesBackdrop() {
    HudFrame frame = hudFrame != null && hudFrameReadySerial == hudFrame.serial ? hudFrame : null;
    final HudFrame active = frame != null ? frame : new HudFrame(0, 0, hudWidth, hudHeight);
    return skinDocument().usesHudBackdrop(
        settings.theme, id -> hudBackdropWidgetActive(active, id));
  }

  private final class HudFrame {
    final long serial;
    final float partialTick;
    final int width, height;
    private List<Cooldown> cooldowns;
    private List<InventorySlot> inventory;
    private List<TntView> tnts;
    private List<InventorySlot> armor;
    private List<PotionView> potions;
    private VitalsView vitals;
    private TargetView target;
    private boolean cooldownsRead, inventoryRead, tntsRead, armorRead, potionsRead, vitalsRead,
        targetRead, crosshairRead, crosshairVisible, crosshairTargetRead, crosshairTargetHit;

    HudFrame(long serial, float partialTick, int width, int height) {
      this.serial = serial;
      this.partialTick = partialTick;
      this.width = width;
      this.height = height;
    }

    List<Cooldown> cooldowns() {
      if (!cooldownsRead) {
        cooldownsRead = true;
        cooldowns = hwhelper.explosiveThingCooldown()
            .mergeInto(platform.cooldowns(partialTick), System.currentTimeMillis());
      }
      return cooldowns;
    }

    List<InventorySlot> inventory() {
      if (!inventoryRead) {
        inventoryRead = true;
        inventory = platform.inventory(partialTick);
      }
      return inventory;
    }

    List<TntView> tnts() {
      if (!tntsRead) {
        tntsRead = true;
        tnts = platform.tnts(partialTick, width, height);
      }
      return tnts;
    }

    List<InventorySlot> armor() {
      if (!armorRead) {
        armorRead = true;
        armor = platform.armor(partialTick);
      }
      return armor;
    }

    List<PotionView> potions() {
      if (!potionsRead) {
        potionsRead = true;
        potions = platform.potions(partialTick);
      }
      return potions;
    }

    VitalsView vitals() {
      if (!vitalsRead) {
        vitalsRead = true;
        vitals = platform.vitals(partialTick);
      }
      return vitals;
    }

    TargetView target() {
      if (!targetRead) {
        targetRead = true;
        target = platform.target(partialTick);
      }
      return target;
    }

    boolean crosshairVisible() {
      if (!crosshairRead) {
        crosshairRead = true;
        crosshairVisible = platform.crosshairVisible();
      }
      return crosshairVisible;
    }

    boolean crosshairTargetHit() {
      if (!crosshairTargetRead) {
        crosshairTargetRead = true;
        crosshairTargetHit = platform.crosshairTargetHit();
      }
      return crosshairTargetHit;
    }
  }

  private int hudWidth, hudHeight;

  public void prepareHudFrame(float partialTick) {
    prepareHudFrame(partialTick, hudWidth, hudHeight);
  }

  public void prepareHudFrame(float partialTick, int width, int height) {
    hudWidth = width;
    hudHeight = height;
    long serial = ++hudFrameSerial;
    if (menu.isEditor()) {
      hudFrame = null;
      hudFrameReadySerial = 0;
      return;
    }
    hudFrame = new HudFrame(serial, partialTick, width, height);
    hudFrameReadySerial = serial;
  }

  private void invalidateHudFrame() {
    hudFrame = null;
    hudFrameReadySerial = 0;
    hudFrameRendering = false;
    hudNetworkInfo = hudKeystrokes = hudCoordinates = hudScoreboard = null;
    hudLocalPlayer = null;
    hudGameVersion = null;
    hudPing = hudFps = 0;
    hudCameraView = null;
    hudNetworkInfoRead = hudKeystrokesRead = hudCoordinatesRead = hudScoreboardRead =
        hudLocalPlayerRead = hudGameVersionRead = hudPingRead = hudFpsRead =
            hudCameraViewRead = false;
  }

  private WorldRibbon.View hudCameraView(int width, int height) {
    if (!hudFrameRendering) return platform.cameraView(width, height);
    if (!hudCameraViewRead) {
      hudCameraViewRead = true;
      hudCameraView = platform.cameraView(width, height);
    }
    return hudCameraView;
  }

  private HudFrame preparedHudFrame(float partialTick, int width, int height) {
    if (hudFrame == null || hudFrameReadySerial != hudFrame.serial)
      prepareHudFrame(partialTick, width, height);
    return hudFrame;
  }

  private HudFrame visibilityFrame() {
    if (hudFrame != null && hudFrameReadySerial == hudFrame.serial) return hudFrame;

    return new HudFrame(0, 0, hudWidth, hudHeight);
  }

  private final java.util.Map<String, Double> hudLastVisible =
      new java.util.HashMap<String, Double>();

  private boolean hudWidgetPresented(HudFrame frame, String id) {
    double time = nowSeconds();
    if (hudWidgetVisible(frame, id)) {
      hudLastVisible.put(id, time);
      return true;
    }
    Double last = hudLastVisible.get(id);
    return last != null && hudSkin != null
        && time - last < hudSkin.hudExitDuration() / Math.max(.1f, settings.menu.animationSpeed);
  }

  public boolean shouldRenderHud() {
    if (menu.isEditor()) return false;
    if (settings.damageSplash.enabled
        && damageIndicators.visible(nowSeconds(), settings.damageSplash.duration))
      return true;
    if (settings.skin.enabled) return true;
    if (settings.waypoints != null && settings.waypoints.enabled
        && settings.waypoints.points != null && !settings.waypoints.points.isEmpty()) {
      return true;
    }
    if (settings.playerHealth != null && settings.playerHealth.enabled) return true;
    if (settings.totemCounter != null && settings.totemCounter.enabled) return true;
    if (TabPingLogic.isWorldActive(settings.playerPing)) return true;
    if (settings.explosionRing != null && settings.explosionRing.enabled
        && settings.explosionRing.showDamageBadge && !platform.explosionBadges(0f).isEmpty())
      return true;
    if (settings.safeCtLeave != null && settings.safeCtLeave.enabled) {
      CombatTagDetector.CombatStatus ct = platform.combatStatus();
      if (ct != null && ((settings.safeCtLeave.showBanner && ct.active)
          || ct.blockedAgoSeconds < 3.0f))
        return true;
    }
    if (settings.betterChat != null && settings.betterChat.enabled) {
      BetterChatLogic.ChatToastStatus toast = platform.chatToast();
      if (toast != null && toast.ageSeconds < 2.2f) return true;
    }
    if (settings.minimap != null && settings.minimap.enabled) return true;
    if (settings.effectAlert != null && settings.effectAlert.enabled
        && !effectAlertLogic.hudAlerts().isEmpty()) {
      return true;
    }
    if (settings.tapeMouse != null && settings.tapeMouse.enabled
        && settings.tapeMouse.showHudIndicator) {
      return true;
    }
    if (settings.hwhelper != null && settings.hwhelper.enabled) {
      if (settings.hwhelper.events != null && settings.hwhelper.events.enabled
          && settings.hwhelper.events.showHud && !hwhelper.filteredEvents().isEmpty()) {
        return true;
      }
      if (settings.hwhelper.timers != null && settings.hwhelper.timers.enabled
          && !hwhelper.timerTracker().snapshot(
                  settings.hwhelper.timers, System.currentTimeMillis()).isEmpty()) {
        return true;
      }
    }
    SoundControlLogic.MentionToast mt = latestMentionToast;
    if (mt != null && (System.nanoTime() - mt.createdNanos) < 3_200_000_000L) return true;
    HudFrame frame = visibilityFrame();
    return hudWidgetPresented(frame, "cooldowns") || hudWidgetPresented(frame, "inventory")
        || hudWidgetPresented(frame, "tnt") || hudWidgetPresented(frame, "crosshair")
        || hudWidgetPresented(frame, "armor") || hudWidgetPresented(frame, "potions")
        || hudWidgetPresented(frame, "food") || hudWidgetPresented(frame, "target")
        || hudWidgetPresented(frame, "scoreboard") || hudWidgetPresented(frame, "keystrokes")
        || hudWidgetPresented(frame, "coordinates") || hudWidgetPresented(frame, "ping")
        || hudWidgetPresented(frame, "fps");
  }

  private boolean hudWidgetVisible(HudFrame frame, String id) {
    if ("cooldowns".equals(id))
      return settings.hud.enabled && (!settings.hud.hideWhenEmpty || !frame.cooldowns().isEmpty());
    if ("inventory".equals(id)) return settings.inventory.enabled;
    if ("tnt".equals(id)) return settings.tnt.enabled && !frame.tnts().isEmpty();
    if ("crosshair".equals(id)) return settings.crosshair.enabled && frame.crosshairVisible();
    if ("armor".equals(id)) return settings.armor.enabled && !frame.armor().isEmpty();
    if ("potions".equals(id))
      return settings.potions.enabled
          && (!settings.potions.hideWhenEmpty || !frame.potions().isEmpty());
    if ("food".equals(id)) {
      VitalsView food = frame.vitals();
      return settings.food.enabled && food.available && food.hungerVisible;
    }
    if ("scoreboard".equals(id)) return settings.scoreboard.enabled && !scoreboard().isEmpty();
    if ("keystrokes".equals(id)) return settings.keystrokes.enabled;
    if ("coordinates".equals(id)) return settings.coordinates.enabled;
    if ("ping".equals(id)) return settings.ping.enabled;
    if ("fps".equals(id)) return settings.fps.enabled;
    return "target".equals(id) && settings.target.enabled && frame.target() != null;
  }

  private boolean hudBackdropWidgetActive(HudFrame frame, String id) {
    if (!hudWidgetPresented(frame, id)) return false;
    if ("cooldowns".equals(id)) return settings.hud.background;
    if ("food".equals(id)) return false;
    if ("scoreboard".equals(id)) return settings.scoreboard.background;
    if ("keystrokes".equals(id)) return settings.keystrokes.background;
    if ("coordinates".equals(id)) return settings.coordinates.background;
    if ("ping".equals(id)) return settings.ping.background;
    if ("fps".equals(id)) return settings.fps.background;
    return !"target".equals(id) || !"zakohp".equals(settings.target.style);
  }

  public MenuController menu() {
    return menu;
  }

  public RegionCacheManager mapCache() {
    return mapCache;
  }

  public WorldMapController worldMap() {
    return worldMap;
  }

  public MapSettingsController mapSettings() {
    return mapSettings;
  }

  public WaypointsScreenController waypointsMenu() {
    return waypointsMenu;
  }

  public void renderWorldMap(Canvas canvas, int width, int height, float mouseX, float mouseY) {
    worldMap.render(canvas, width, height, mouseX, mouseY, nowSeconds());
  }

  public void renderMapSettings(Canvas canvas, int width, int height, float mouseX, float mouseY) {
    mapSettings.render(canvas, width, height, mouseX, mouseY, nowSeconds());
  }

  public void renderWaypointsMenu(Canvas canvas, int width, int height, float mouseX, float mouseY) {
    waypointsMenu.render(canvas, width, height, mouseX, mouseY, nowSeconds());
  }

  public void updateMapEnlargeKey(boolean held, boolean toggledClick) {
    if (settings.minimap != null && "toggle".equals(settings.minimap.enlargeMode)) {
      if (toggledClick) mapEnlargeToggled = !mapEnlargeToggled;
      mapEnlargeHeld = false;
    } else {
      mapEnlargeHeld = held;
      mapEnlargeToggled = false;
    }
  }

  public boolean isMapEnlarged() {
    if (settings.minimap == null || !settings.minimap.enabled) return false;
    return "toggle".equals(settings.minimap.enlargeMode) ? mapEnlargeToggled : mapEnlargeHeld;
  }

  public float mapEnlargeProgress() {
    return mapEnlargeProgress;
  }

  public static double nowSeconds() {
    return System.nanoTime() / 1_000_000_000.0;
  }

  public void onServerJoin(boolean remoteServer) {
    liteApi.onServerJoin(remoteServer, System.currentTimeMillis());
  }

  public void onServerDisconnect() {
    liteApi.onDisconnect();
  }

  public void onLiteApiPayload(byte[] data) {
    liteApi.onIncomingPayload(data, System.currentTimeMillis());
  }

  private void onFeaturePolicyChanged() {
    if (FeaturePolicy.isBlocked("tapeMouse")) tapeMouseLogic.reset();
    if (FeaturePolicy.isBlocked("zoom")) {
      zoomHeld = false;
      zoomCurrent = zoomTarget = 1;
    }
    if (FeaturePolicy.isBlocked("worldMap") && platform.isWorldMapOpen()) {
      platform.closeWorldMap();
    }
    if (FeaturePolicy.isBlocked("minimap") && FeaturePolicy.isBlocked("worldMap")) {
      platform.closeMapSettings();
    }
    if (FeaturePolicy.isBlocked("waypoints")) {
      platform.closeWaypointsMenu();
    }
    if (FeaturePolicy.isBlocked("damageSplash")) damageIndicators.clear();
    if (FeaturePolicy.isBlocked("effectAlert")) effectAlertLogic.clear();
    if (FeaturePolicy.isBlocked("hwhelper.timers")) hwhelper.timerTracker().clear();
    if (FeaturePolicy.isBlocked("soundControl")) latestMentionToast = null;
    prepareSettings();
    invalidateHudFrame();
    if (menu != null) menu.invalidatePresentation();
  }

  public void tick() {
    for (Runnable task = pendingMainThreadTasks.poll(); task != null;
        task = pendingMainThreadTasks.poll()) {
      task.run();
    }
    flushPendingConfigSave(false);
    prepareSettings();
    ownNickname = platform.ownNickname();
    hwhelper.tick();
    cosmetics.tick();
    mapCache.syncSettings(settings);
    long nowMs = System.currentTimeMillis();
    liteApi.tick(nowMs);
    if (worldActive) {
      if (!FeaturePolicy.isBlocked("effectAlert")
          && settings.effectAlert != null && settings.effectAlert.enabled) {
        effectAlertLogic.update(settings.effectAlert, platform.potions(0f), nowMs);
        List<EffectAlertLogic.SoundCue> cues = effectAlertLogic.drainSounds();
        for (int i = 0; i < cues.size(); i++) {
          EffectAlertLogic.SoundCue cue = cues.get(i);
          platform.playClientSound(cue.soundId, cue.volume, cue.pitch);
        }
      } else {
        effectAlertLogic.clear();
      }
      if (!FeaturePolicy.isBlocked("hwhelper.timers")
          && settings.hwhelper != null && settings.hwhelper.enabled && settings.hwhelper.timers != null
          && settings.hwhelper.timers.enabled) {
        double[] pos = platform.playerPosition();
        double px = pos != null && pos.length >= 3 ? pos[0] : 0.0;
        double py = pos != null && pos.length >= 3 ? pos[1] : 0.0;
        double pz = pos != null && pos.length >= 3 ? pos[2] : 0.0;
        hwhelper.timerTracker().syncPersonalEffects(
            platform.potions(0f), platform.cooldowns(0f), px, py, pz, settings.hwhelper.timers,
            nowMs);
      }
    }
  }

  private void prepareSettings() {
    preparedSaturation = !FeaturePolicy.isBlocked("saturation") && settings.saturation.enabled
        ? settings.saturation.strength
        : 1;
    settings.skinDocument = skinDocument();
    if (cosmetics != null) cosmetics.syncFromSettings();
    invalidateHudFrame();
  }

  public void setWorldActive(boolean value) {
    worldActive = value;
    if (!value) {
      chatHud.close();
      hudLastVisible.clear();
      hudDocument = null;
      hudSkin = null;
      zoomHeld = false;
      zoomCurrent = zoomTarget = 1;
      inventory = Collections.emptyList();
      tnts = Collections.emptyList();
      armor = Collections.emptyList();
      potions = Collections.emptyList();
      vitals = VitalsView.EMPTY;
      target = null;
      damageIndicators.clear();
      effectAlertLogic.clear();
      tapeMouseLogic.reset();
      hwhelper.timerTracker().clear();
      hwhelper.explosiveThingCooldown().clear();
      latestMentionToast = null;
      worldMap.onClose();
      mapSettings.onClose();
      waypointsMenu.onClose();
      mapEnlargeHeld = false;
      mapEnlargeToggled = false;
      mapEnlargeProgress = 0f;
      mapCache.clearSession();
      if (settings.waypoints != null && settings.waypoints.points != null) {
        boolean removedTemp = false;
        for (int i = settings.waypoints.points.size() - 1; i >= 0; i--) {
          Waypoint wp = settings.waypoints.points.get(i);
          if (wp != null && wp.temp) {
            settings.waypoints.points.remove(i);
            removedTemp = true;
          }
        }
        if (removedTemp) changed();
      }
      invalidateHudFrame();
    }
  }

  public float saturationAmount() {
    return worldActive ? preparedSaturation : 1;
  }

  public void resourceReload() {
    invalidateHudFrame();
    if (hudSkin != null) hudSkin.invalidateLayout();
    if (menu != null) menu.resourceReload();
  }

  public void renderHud(Canvas canvas, int width, int height, float partialTick) {
    if (menu.isEditor()) return;
    HudFrame frame = preparedHudFrame(partialTick, width, height);
    hudWidth = width;
    hudHeight = height;
    cooldowns = FeaturePolicy.isBlocked("hud") ? Collections.emptyList() : frame.cooldowns();
    inventory = !FeaturePolicy.isBlocked("inventory") && settings.inventory.enabled ? frame.inventory() : Collections.emptyList();
    tnts = !FeaturePolicy.isBlocked("tnt") && settings.tnt.enabled ? frame.tnts() : Collections.emptyList();
    armor = !FeaturePolicy.isBlocked("armor") && settings.armor.enabled ? frame.armor() : Collections.emptyList();
    potions = !FeaturePolicy.isBlocked("potions") && settings.potions.enabled ? frame.potions() : Collections.emptyList();
    vitals = !FeaturePolicy.isBlocked("food") && settings.food.enabled ? frame.vitals() : VitalsView.EMPTY;
    target = !FeaturePolicy.isBlocked("target") && settings.target.enabled ? frame.target() : null;
    hudFrameRendering = true;
    try {
      double time = nowSeconds();
      settings.theme.effects.time = time;
      dev.nightclient.skin.SkinDocument document = skinDocument();
      if (document != hudDocument) {
        hudDocument = document;
        hudSkin = document == null ? null
                                   : new dev.nightclient.ui.skin.SkinView(document, settings, this);
      }
      if (!FeaturePolicy.isBlocked("food"))
        dev.nightclient.ui.ExtendedHudRenderer.renderFood(canvas, settings, vitals, width, height);
      if (!FeaturePolicy.isBlocked("waypoints"))
        renderWaypoints(canvas, width, height);
      renderPlayerHealth(canvas, width, height, partialTick, time);
      java.util.Set<Integer> totemEntityIds =
          renderTotemCounters(canvas, width, height, partialTick, time);
      renderPlayerPings(canvas, width, height, partialTick, time, totemEntityIds);
      renderExplosionBadges(canvas, width, height, partialTick);
      if (!FeaturePolicy.isBlocked("damageSplash") && settings.damageSplash.enabled)
        damageIndicators.render(
            canvas, settings, hudCameraView(width, height), width, height, time);
      else
        damageIndicators.clear();
      boolean crosshairVisible = frame.crosshairVisible();
      boolean renderedSkin = hudSkin != null
          && hudSkin.renderHud(
              canvas, width, height, time, cooldowns, inventory, tnts, crosshairVisible);
      if (!renderedSkin) {
        if (!FeaturePolicy.isBlocked("hud") && settings.hud.enabled)
          HudRenderer.render(canvas, settings, cooldowns, width, height, time, false, hudSkin);
        FeatureHudRenderer.render(
            canvas, settings, inventory, tnts, width, height, time, false, crosshairVisible, hudSkin);
        dev.nightclient.ui.ExtendedHudRenderer.render(
            canvas, settings, armor, potions, vitals, target, width, height, time, false, hudSkin);
      }
      if (!FeaturePolicy.isBlocked("minimap") && settings.minimap != null && settings.minimap.enabled && !worldMap.isOpen()) {
        double dt = mapEnlargeLastTime > 0.0 ? Math.max(0.0, Math.min(0.1, time - mapEnlargeLastTime)) : 0.016;
        mapEnlargeLastTime = time;
        float targetEnlarge = isMapEnlarged() ? 1f : 0f;
        float k = 1f - (float) Math.exp(-dt * 14.0);
        mapEnlargeProgress += (targetEnlarge - mapEnlargeProgress) * k;
        if (Math.abs(targetEnlarge - mapEnlargeProgress) < 0.002f) {
          mapEnlargeProgress = targetEnlarge;
        }
        MinimapRenderer.renderLive(canvas, settings, mapCache, platform.mapRadarSnapshot(partialTick),
            width, height, mapEnlargeProgress, time);
      } else {
        mapEnlargeProgress = 0f;
        mapEnlargeLastTime = time;
      }
      renderSafeCtBanner(canvas, width, height, time);
      renderChatCopyToast(canvas, width, height);
      renderEffectAlertsHud(canvas, width, height, time);
      renderHwTimersHud(canvas, width, height, time);
      renderHwEventsHud(canvas, width, height);
      renderTapeMouseHud(canvas, width, height, time);
      renderMentionToastHud(canvas, width, height);
    } finally {
      invalidateHudFrame();
    }
  }

  private void renderPlayerHealth(
      Canvas canvas, int width, int height, float partialTick, double time) {
    Settings.PlayerHealth cfg = settings.playerHealth;
    if (FeaturePolicy.isBlocked("playerHealth") || cfg == null || !cfg.enabled || canvas == null || width <= 0 || height <= 0) return;
    List<PlayerHealthView> views = platform.playerHealths(partialTick);
    if (views == null || views.isEmpty()) return;
    WorldRibbon.View view = hudCameraView(width, height);
    if (view == null) return;
    final class ProjectedHealth {
      final PlayerHealthView entry;
      final float sx, sy, s, alpha;
      final double depth;
      ProjectedHealth(
          PlayerHealthView entry, float sx, float sy, float s, float alpha, double depth) {
        this.entry = entry;
        this.sx = sx;
        this.sy = sy;
        this.s = s;
        this.alpha = alpha;
        this.depth = depth;
      }
    }
    List<ProjectedHealth> visible = new ArrayList<ProjectedHealth>(views.size());
    double[] point = projScratch;
    float fadeStart = Math.max(4f, cfg.maxDistance * .75f);
    double fovRad = Math.toRadians(Math.max(20f, Math.min(130f, effectiveFov)));
    float fovScale = (float) (Math.tan(Math.toRadians(35.0)) / Math.max(.12, Math.tan(fovRad * .5)));
    float uiScale = (height / 540f) * cfg.scale;
    for (int i = 0; i < views.size(); i++) {
      PlayerHealthView ph = views.get(i);
      double wy = ph.y + ph.bbHeight + .32 + cfg.height;
      view.project(ph.x, wy, ph.z, point);
      if (point[2] <= .15 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) continue;
      double dist = view.distance(ph.x, wy, ph.z);
      float alpha = dist <= fadeStart ? 1f
                                      : Math.max(0f,
                                          Math.min(1f,
                                              (float) ((cfg.maxDistance - dist)
                                                  / Math.max(1f, cfg.maxDistance - fadeStart))));
      if (alpha <= .04f) continue;
      float perspective = (float) Math.max(.30, Math.min(1.30, (7.5 / Math.max(3.2, dist)) * fovScale));
      float s = Math.max(.28f, uiScale * perspective);
      visible.add(new ProjectedHealth(ph, (float) point[0], (float) point[1], s, alpha, point[2]));
    }
    if (visible.isEmpty()) return;
    Collections.sort(visible, new Comparator<ProjectedHealth>() {
      @Override
      public int compare(ProjectedHealth a, ProjectedHealth b) {
        return Double.compare(b.depth, a.depth);
      }
    });
    for (int i = 0; i < visible.size(); i++) {
      ProjectedHealth item = visible.get(i);
      PlayerHealthView ph = item.entry;
      float s = item.s, a = item.alpha;
      boolean hasAbs = cfg.showAbsorption && ph.absorption > .05f;
      boolean hasEnch = cfg.showEnchanted && ph.enchanted;
      if ("hearts".equals(cfg.style)) {
        renderPlayerHealthHearts(canvas, width, height, item.sx, item.sy, s, a, ph, hasAbs, hasEnch, time);
        continue;
      }
      String hpText = formatHpValue(ph.health);
      String absText = hasAbs ? " +" + formatHpValue(ph.absorption) : "";
      float font = 7.2f * s;
      float absFont = 6.4f * s;
      float hpW = canvas.textWidthFamily("studio-sans-medium", hpText, font, true, 0);
      float absW = hasAbs ? canvas.textWidthFamily("studio-sans-medium", absText, absFont, true, 0) : 0f;
      float iconSize = 5.6f * s;
      float iconGap = 2.4f * s;
      float readoutW = iconSize + iconGap + hpW + absW;
      float barW = Math.max(46f * s, readoutW + 6f * s);
      float barH = Math.max(2.2f, 4.4f * s);
      float rowGap = 2.0f * s;
      float totalH = font + rowGap + barH;
      float barX = item.sx - barW * .5f;
      float topY = item.sy - totalH;
      if (barX + barW < 0 || barX > width || topY + totalH < 0 || topY > height) continue;

      float hpRatio = Math.max(0f, Math.min(1f, ph.health / Math.max(1f, ph.maxHealth)));
      int hpLeft = hpRatio < .32f ? 0xffef4444 : hpRatio < .65f ? 0xfff59e0b : 0xff10b981;
      int hpRight = hpRatio < .32f ? 0xfffb7185 : hpRatio < .65f ? 0xfffde047 : 0xff4ade80;
      int hpTextColor = hpRatio < .32f ? 0xfffecdd3 : 0xfff8fafc;

      float readoutX = item.sx - readoutW * .5f;
      float heartY = topY + (font - iconSize) * .5f + .3f * s;
      if (hasEnch) {
        drawVectorHeart(canvas, readoutX, heartY, iconSize, 2,
            0xffc084fc, 0xff9333ea, 0xfff5d0fe, 0xff231d26, a);
      } else {
        int heartBright = ph.invisible ? 0xff38bdf8 : hpRight;
        int heartShade = ph.invisible ? 0xff0284c7 : hpLeft;
        drawVectorHeart(canvas, readoutX, heartY, iconSize, 2,
            heartBright, heartShade, 0xffffffff, 0xff231d26, a);
      }

      float textX = readoutX + iconSize + iconGap;
      float shadowOff = Math.max(.5f, .65f * s);
      canvas.textFamily("studio-sans-medium", hpText, textX + shadowOff, topY + shadowOff, font,
          Colors.alpha(0xb805070b, a), true, 0);
      canvas.textFamily("studio-sans-medium", hpText, textX, topY, font,
          Colors.alpha(hpTextColor, a), true, 0);
      if (hasAbs) {
        float absX = textX + hpW;
        float absY = topY + (font - absFont) * .65f;
        canvas.textFamily("studio-sans-medium", absText, absX + shadowOff, absY + shadowOff, absFont,
            Colors.alpha(0xb805070b, a), true, 0);
        canvas.textFamily("studio-sans-medium", absText, absX, absY, absFont,
            Colors.alpha(0xfffde047, a), true, 0);
      }

      float barY = topY + font + rowGap;
      float pad = Math.max(.6f, .85f * s);
      float outerR = (barH + pad * 2) * .5f;
      int rim = ph.invisible ? 0x9038bdf8 : 0x38ffffff;
      canvas.roundedRect(barX - pad, barY - pad, barW + pad * 2, barH + pad * 2, outerR,
          Colors.alpha(0xc4070a10, a));
      canvas.strokeRoundedRect(barX - pad, barY - pad, barW + pad * 2, barH + pad * 2, outerR,
          Math.max(.55f, .65f * s), Colors.alpha(rim, a));

      float barR = barH * .5f;
      canvas.roundedRect(barX, barY, barW, barH, barR, Colors.alpha(0x22ffffff, a));
      float absRatio = hasAbs ? Math.max(0f, Math.min(1f, ph.absorption / Math.max(1f, ph.maxHealth))) : 0f;
      float totalUnits = Math.max(1f, hpRatio + absRatio);
      float hpFrac = hpRatio / totalUnits;
      float fillW = barW * hpFrac;
      if (ph.health > .01f && fillW > .5f) {
        float drawHpW = Math.max(barH, fillW);
        canvas.gradientRoundedRect(barX, barY, drawHpW, barH, barR,
            Colors.alpha(hpLeft, a), Colors.alpha(hpRight, a));
        if (barH >= 3f) {
          canvas.roundedRect(barX + barR * .5f, barY + .45f * s, Math.max(0f, drawHpW - barR),
              Math.max(.6f, barH * .26f), barR * .3f, Colors.alpha(0x36ffffff, a));
        }
      }
      if (hasAbs) {
        float absFrac = absRatio / totalUnits;
        float absWBar = Math.min(barW, Math.max(barH, barW * absFrac));
        float absXBar = Math.min(barX + barW - absWBar, barX + fillW);
        canvas.gradientRoundedRect(absXBar, barY, absWBar, barH, barR,
            Colors.alpha(0xfff59e0b, a * .95f), Colors.alpha(0xfffde047, a * .95f));
      }
    }
  }

  private java.util.Set<Integer> renderTotemCounters(
      Canvas canvas, int width, int height, float partialTick, double time) {
    Settings.TotemCounter cfg = settings.totemCounter;
    if (FeaturePolicy.isBlocked("totemCounter") || cfg == null || !cfg.enabled || canvas == null || width <= 0 || height <= 0)
      return Collections.emptySet();
    List<TotemCounterView> views = platform.totemCounters(partialTick);
    if (views == null || views.isEmpty()) return Collections.emptySet();
    WorldRibbon.View view = hudCameraView(width, height);
    if (view == null) return Collections.emptySet();
    final class ProjectedTotem {
      final TotemCounterView entry;
      final float sx, sy, s, alpha;
      final double depth;
      ProjectedTotem(
          TotemCounterView entry, float sx, float sy, float s, float alpha, double depth) {
        this.entry = entry;
        this.sx = sx;
        this.sy = sy;
        this.s = s;
        this.alpha = alpha;
        this.depth = depth;
      }
    }
    List<ProjectedTotem> visible = new ArrayList<ProjectedTotem>(views.size());
    double[] point = projScratch;
    float fadeStart = Math.max(4f, cfg.maxDistance * .75f);
    double fovRad = Math.toRadians(Math.max(20f, Math.min(130f, effectiveFov)));
    float fovScale = (float) (Math.tan(Math.toRadians(35.0)) / Math.max(.12, Math.tan(fovRad * .5)));
    float uiScale = (height / 540f) * cfg.scale;
    for (int i = 0; i < views.size(); i++) {
      TotemCounterView tc = views.get(i);
      if (tc == null || (tc.pops <= 0 && !cfg.showZero)) continue;
      double wy = tc.y + tc.bbHeight + .25 + cfg.height;
      view.project(tc.x, wy, tc.z, point);
      if (point[2] <= .15 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) continue;
      double dist = view.distance(tc.x, wy, tc.z);
      float alpha = dist <= fadeStart ? 1f
                                      : Math.max(0f,
                                          Math.min(1f,
                                              (float) ((cfg.maxDistance - dist)
                                                  / Math.max(1f, cfg.maxDistance - fadeStart))));
      if (alpha <= .04f) continue;
      float perspective = (float) Math.max(.32, Math.min(1.25, (7.5 / Math.max(3.2, dist)) * fovScale));
      float s = Math.max(.30f, uiScale * perspective);
      visible.add(new ProjectedTotem(tc, (float) point[0], (float) point[1], s, alpha, point[2]));
    }
    if (visible.isEmpty()) return Collections.emptySet();
    java.util.Set<Integer> renderedIds = new java.util.HashSet<Integer>(visible.size());
    Collections.sort(visible, new Comparator<ProjectedTotem>() {
      @Override
      public int compare(ProjectedTotem a, ProjectedTotem b) {
        return Double.compare(b.depth, a.depth);
      }
    });
    for (int i = 0; i < visible.size(); i++) {
      ProjectedTotem item = visible.get(i);
      TotemCounterView tc = item.entry;
      float s = item.s, a = item.alpha;
      float recent = Math.max(0f, 1f - tc.sincePopSeconds / 1.4f);
      float popBoost = 1f + .12f * recent * (float) Math.sin(recent * Math.PI);
      float bs = s * popBoost;

      String label = "\u00d7" + tc.pops;
      float font = 6.8f * bs;
      float iconSize = 8.4f * bs;
      float textW = canvas.textWidthFamily("studio-sans-medium", label, font, true, 0);
      float padX = 3.6f * bs;
      float padY = 1.9f * bs;
      float gap = 2.0f * bs;
      float pillW = padX * 2 + iconSize + gap + textW;
      float pillH = Math.max(iconSize, font) + padY * 2;
      float px = item.sx - pillW * .5f;
      float py = item.sy - pillH * .5f;
      if (px + pillW < 0 || px > width || py + pillH < 0 || py > height) continue;

      int accentColor = tc.pops <= 0 ? 0xff94a3b8
          : tc.pops <= 2             ? 0xfffde047
          : tc.pops <= 5             ? 0xfffb923c
                                     : 0xfff87171;
      float radius = pillH * .5f;
      if (recent > .05f) {
        canvas.roundedRect(px - 1.5f * bs, py - 1.5f * bs, pillW + 3f * bs, pillH + 3f * bs,
            radius + 1.5f * bs, Colors.alpha(accentColor, a * recent * .32f));
      }
      int bgColor = tc.invisible ? 0xcc071220 : 0xcc090d14;
      int rimColor = recent > .05f
          ? Colors.mix(0x88facc15, 0xfffef08a, (float) (.5 + .5 * Math.sin(time * 9.0)))
          : tc.pops > 0 ? Colors.alpha(accentColor, .58f) : 0x38ffffff;
      canvas.roundedRect(px, py, pillW, pillH, radius, Colors.alpha(bgColor, a));
      canvas.strokeRoundedRect(
          px, py, pillW, pillH, radius, Math.max(.55f, .65f * bs), Colors.alpha(rimColor, a));

      float iconX = px + padX;
      float iconY = py + (pillH - iconSize) * .5f;
      canvas.item("minecraft:totem_of_undying", iconX, iconY, iconSize, a);

      float tx = iconX + iconSize + gap;
      float ty = py + (pillH - font) * .5f;
      float shadowOff = Math.max(.45f, .55f * bs);
      canvas.textFamily("studio-sans-medium", label, tx + shadowOff, ty + shadowOff, font,
          Colors.alpha(0xb805070b, a), true, 0);
      canvas.textFamily(
          "studio-sans-medium", label, tx, ty, font, Colors.alpha(accentColor, a), true, 0);
      renderedIds.add(tc.entityId);
    }
    return renderedIds;
  }

  private void renderPlayerPings(Canvas canvas, int width, int height, float partialTick,
      double time, java.util.Set<Integer> totemEntityIds) {
    Settings.PlayerPing cfg = settings.playerPing;
    if (FeaturePolicy.isBlocked("playerPing") || !TabPingLogic.isWorldActive(cfg) || canvas == null || width <= 0 || height <= 0) return;
    List<PlayerPingView> views = platform.playerPings(partialTick);
    if (views == null || views.isEmpty()) return;
    WorldRibbon.View view = hudCameraView(width, height);
    if (view == null) return;
    final class ProjectedPing {
      final PlayerPingView entry;
      final float sx, sy, s, alpha;
      final double depth;
      ProjectedPing(
          PlayerPingView entry, float sx, float sy, float s, float alpha, double depth) {
        this.entry = entry;
        this.sx = sx;
        this.sy = sy;
        this.s = s;
        this.alpha = alpha;
        this.depth = depth;
      }
    }
    List<ProjectedPing> visible = new ArrayList<ProjectedPing>(views.size());
    double[] point = projScratch;
    float fadeStart = Math.max(4f, cfg.maxDistance * .75f);
    double fovRad = Math.toRadians(Math.max(20f, Math.min(130f, effectiveFov)));
    float fovScale = (float) (Math.tan(Math.toRadians(35.0)) / Math.max(.12, Math.tan(fovRad * .5)));
    float uiScale = (height / 540f) * cfg.scale;
    for (int i = 0; i < views.size(); i++) {
      PlayerPingView pv = views.get(i);
      if (pv == null) continue;
      double wy = pv.y + pv.bbHeight + .25 + cfg.height;
      view.project(pv.x, wy, pv.z, point);
      if (point[2] <= .15 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) continue;
      double dist = view.distance(pv.x, wy, pv.z);
      float alpha = dist <= fadeStart ? 1f
                                      : Math.max(0f,
                                          Math.min(1f,
                                              (float) ((cfg.maxDistance - dist)
                                                  / Math.max(1f, cfg.maxDistance - fadeStart))));
      if (alpha <= .04f) continue;
      float perspective = (float) Math.max(.30, Math.min(1.25, (7.5 / Math.max(3.2, dist)) * fovScale));
      float s = Math.max(.28f, uiScale * perspective);
      float sy = (float) point[1];
      if (totemEntityIds != null && totemEntityIds.contains(pv.entityId)
          && Math.abs(cfg.height - (settings.totemCounter == null ? 0f : settings.totemCounter.height)) < 0.22f) {
        sy += 13.5f * s;
      }
      visible.add(new ProjectedPing(pv, (float) point[0], sy, s, alpha, point[2]));
    }
    if (visible.isEmpty()) return;
    Collections.sort(visible, new Comparator<ProjectedPing>() {
      @Override
      public int compare(ProjectedPing a, ProjectedPing b) {
        return Double.compare(b.depth, a.depth);
      }
    });
    for (int i = 0; i < visible.size(); i++) {
      ProjectedPing item = visible.get(i);
      PlayerPingView pv = item.entry;
      float s = item.s, a = item.alpha;
      int ping = pv.pingMs;
      int pingColor = TabPingLogic.colorFor(ping, cfg.colored);
      String label = TabPingLogic.formatLabel(ping);
      float font = 6.4f * s;
      float textW = canvas.textWidthFamily("studio-sans-medium", label, font, true, 0);
      float shadowOff = Math.max(.45f, .55f * s);
      if ("text".equals(cfg.style)) {
        float tx = item.sx - textW * .5f;
        float ty = item.sy - font * .5f;
        if (tx + textW < 0 || tx > width || ty + font < 0 || ty > height) continue;
        canvas.textFamily("studio-sans-medium", label, tx + shadowOff, ty + shadowOff, font,
            Colors.alpha(0xc805070b, a), true, 0);
        canvas.textFamily(
            "studio-sans-medium", label, tx, ty, font, Colors.alpha(pingColor, a), true, 0);
        continue;
      }
      int barsLit = ping < 60 ? 3 : ping < 140 ? 2 : 1;
      float barsW = 5.4f * s;
      float barsH = 5.4f * s;
      float gap = 2.4f * s;
      float padX = 3.6f * s;
      float padY = 1.8f * s;
      float pillW = padX * 2 + barsW + gap + textW;
      float pillH = Math.max(barsH, font) + padY * 2;
      float px = item.sx - pillW * .5f;
      float py = item.sy - pillH * .5f;
      if (px + pillW < 0 || px > width || py + pillH < 0 || py > height) continue;

      float radius = pillH * .5f;
      int bgColor = pv.invisible ? 0xcc071220 : 0xc8090d14;
      int rimColor = cfg.colored ? Colors.alpha(pingColor, .48f) : 0x38ffffff;
      canvas.roundedRect(px, py, pillW, pillH, radius, Colors.alpha(bgColor, a));
      canvas.strokeRoundedRect(
          px, py, pillW, pillH, radius, Math.max(.5f, .6f * s), Colors.alpha(rimColor, a));

      float bx = px + padX;
      float baseY = py + (pillH + barsH) * .5f;
      float bw = 1.25f * s;
      float bStep = 2.05f * s;
      for (int b = 0; b < 3; b++) {
        float bh = barsH * (0.42f + 0.29f * b);
        int col = b < barsLit ? Colors.alpha(pingColor, a) : Colors.alpha(0x4094a3b8, a);
        canvas.roundedRect(bx + b * bStep, baseY - bh, bw, bh, bw * .4f, col);
      }

      float tx = bx + barsW + gap;
      float ty = py + (pillH - font) * .5f;
      canvas.textFamily("studio-sans-medium", label, tx + shadowOff, ty + shadowOff, font,
          Colors.alpha(0xb805070b, a), true, 0);
      canvas.textFamily(
          "studio-sans-medium", label, tx, ty, font, Colors.alpha(pingColor, a), true, 0);
    }
  }

  private void renderSafeCtBanner(Canvas canvas, int width, int height, double time) {
    Settings.SafeCtLeave cfg = settings.safeCtLeave;
    if (FeaturePolicy.isBlocked("safeCtLeave") || cfg == null || !cfg.enabled || canvas == null || width <= 0 || height <= 0) return;
    CombatTagDetector.CombatStatus ct = platform.combatStatus();
    if (ct == null) return;
    boolean justBlocked = ct.blockedAgoSeconds < 2.8f;
    if (!justBlocked && (!cfg.showBanner || !ct.active)) return;
    String lang = I18n.language(settings.language);
    boolean hitAbort = justBlocked && "hit".equals(ct.blockedReason);
    String timer = ct.remainingSeconds > 0
        ? I18n.format(lang, "safeCtLeave.hud.timer",
              Collections.<String, Object>singletonMap("sec", Integer.valueOf(ct.remainingSeconds)))
        : "";
    String title;
    if (hitAbort) {
      title = I18n.text(lang, "safeCtLeave.hud.hit_abort") + timer;
    } else if (justBlocked) {
      title = I18n.text(lang, "safeCtLeave.hud.blocked") + timer;
    } else {
      title = ct.remainingSeconds > 0
          ? (I18n.text(lang, "safeCtLeave.hud.active") + timer)
          : I18n.text(lang, "safeCtLeave.hud.guard");
    }
    float fade = justBlocked
        ? (ct.blockedAgoSeconds > 2.25f ? Math.max(0f, (2.8f - ct.blockedAgoSeconds) / 0.55f) : 1f)
        : 0.92f;
    if (fade <= 0.02f) return;

    float hudScale = settings.menu != null ? settings.menu.hudScale : 1.0f;
    float fit = Math.max(0.55f,
        Math.min(1.0f, Math.min((width - 24f) / 640f, (height - 20f) / 360f)));
    float s = Math.max(0.55f, Math.min(1.15f, fit * hudScale));

    float font = 6.2f * s;
    float hintFont = 5.4f * s;
    float iconSize = 7.8f * s;
    float padX = 6.5f * s;
    float gap = 4.2f * s;
    float titleW = canvas.textWidthFamily("studio-sans-medium", title, font, true, 0);
    String shiftTag = (justBlocked && cfg.shiftBypass) ? " · Shift" : "";
    float shiftW = shiftTag.isEmpty()
        ? 0f
        : canvas.textWidthFamily("studio-sans-medium", shiftTag, hintFont, false, 0);
    float maxPillW = Math.max(80f, width * 0.42f);
    if (!shiftTag.isEmpty() && (padX * 2f + iconSize + gap + titleW + shiftW) > maxPillW) {
      shiftTag = "";
      shiftW = 0f;
    }
    float contentW = iconSize + gap + titleW + shiftW;
    float pillW = padX * 2f + contentW;
    if (pillW > maxPillW && contentW > 1f) {
      float shrink = Math.max(0.68f, (maxPillW - padX * 2f) / contentW);
      font *= shrink;
      hintFont *= shrink;
      iconSize *= shrink;
      gap *= shrink;
      titleW = canvas.textWidthFamily("studio-sans-medium", title, font, true, 0);
      shiftW = shiftTag.isEmpty()
          ? 0f
          : canvas.textWidthFamily("studio-sans-medium", shiftTag, hintFont, false, 0);
      pillW = padX * 2f + iconSize + gap + titleW + shiftW;
    }
    float pillH = (justBlocked ? 15.5f : 14.5f) * s;
    float px = (width - pillW) * .5f;
    float py = Math.max(4f, (justBlocked ? 8f : 6f) * s);
    float radius = pillH * 0.5f;

    float pulse = justBlocked ? (float) (.5 + .5 * Math.sin(time * 9.0)) : 0.2f;
    int accent = justBlocked ? 0xffef4444 : 0xfff59e0b;
    int bg = Colors.mix(0xd00b0f17, 0xd8280b10, justBlocked ? 0.48f + 0.22f * pulse : 0.20f);
    int rim = Colors.mix(Colors.alpha(accent, 0.50f), Colors.alpha(0xfffca5a5, 0.90f), pulse);
    canvas.roundedRect(px, py, pillW, pillH, radius, Colors.alpha(bg, fade));
    canvas.strokeRoundedRect(px, py, pillW, pillH, radius, 0.9f, Colors.alpha(rim, fade));
    dev.nightclient.renderer.Icons.draw(canvas, "safe-ct-leave", px + padX,
        py + (pillH - iconSize) * .5f, iconSize, Colors.alpha(accent, fade), 1.2f);
    float tx = px + padX + iconSize + gap;
    float ty = py + (pillH - font) * .5f;
    canvas.textFamily("studio-sans-medium", title, tx, ty, font,
        Colors.alpha(0xfff8fafc, fade), true, 0);
    if (!shiftTag.isEmpty()) {
      canvas.textFamily("studio-sans-medium", shiftTag, tx + titleW,
          py + (pillH - hintFont) * .5f, hintFont,
          Colors.alpha(0xffcbd5e1, fade * 0.85f), false, 0);
    }
  }

  private void renderChatCopyToast(Canvas canvas, int width, int height) {
    Settings.BetterChat cfg = settings.betterChat;
    if (cfg == null || !cfg.enabled || canvas == null || width <= 0 || height <= 0) return;
    BetterChatLogic.ChatToastStatus toast = platform.chatToast();
    if (toast == null || toast.message.isEmpty() || toast.ageSeconds >= 2.1f) return;
    float alpha = toast.ageSeconds < 0.16f
        ? toast.ageSeconds / 0.16f
        : toast.ageSeconds > 1.6f ? Math.max(0f, (2.1f - toast.ageSeconds) / 0.5f) : 1f;
    if (alpha <= 0.02f) return;
    float font = 7.0f;
    float textW = canvas.textWidthFamily("studio-sans-medium", toast.message, font, true, 0);
    float iconSize = 8.5f;
    float padX = 7.5f;
    float padY = 4.2f;
    float gap = 5.0f;
    float pillW = padX * 2 + iconSize + gap + textW;
    float pillH = Math.max(iconSize, font) + padY * 2;
    float[] anchor = BetterChatLogic.computeHudAnchor(
        cfg.anchorX, cfg.anchorY, cfg.offsetX, cfg.offsetY, cfg.scale, width, height);
    float px = Math.max(8f, Math.min(width - pillW - 8f, anchor[0] + 4f));
    float py = Math.max(12f, Math.min(height - pillH - 6f, anchor[1] + 6f));
    float r = pillH * .5f;
    int accent = settings.theme != null ? settings.theme.accent : 0xff38bdf8;
    canvas.roundedRect(px, py, pillW, pillH, r, Colors.alpha(0xd80b1019, alpha));
    canvas.strokeRoundedRect(px, py, pillW, pillH, r, 0.8f, Colors.alpha(accent, alpha * 0.65f));
    dev.nightclient.renderer.Icons.draw(canvas, "check", px + padX,
        py + (pillH - iconSize) * .5f, iconSize, Colors.alpha(accent, alpha), 1.3f);
    canvas.textFamily("studio-sans-medium", toast.message, px + padX + iconSize + gap,
        py + (pillH - font) * .5f, font, Colors.alpha(0xfff1f5f9, alpha), true, 0);
  }

  public void renderBetterChatBackdrop(
      Canvas canvas, float x, float y, float w, float h, float visibilityAlpha) {
    Settings.BetterChat cfg = settings.betterChat;
    if (cfg == null || !cfg.enabled
        || (!"rounded".equals(cfg.backgroundStyle) && !"compact".equals(cfg.backgroundStyle)))
      return;
    if (canvas == null || w <= 4f || h <= 4f || visibilityAlpha <= 0.02f) return;
    float alpha = Math.max(0f, Math.min(1f, visibilityAlpha));
    float bgOpacity = Math.max(0f, Math.min(1f, cfg.backgroundOpacity));
    float radius = 6.5f;
    if (cfg.blur && bgOpacity > 0.02f) {
      float blurRadius = settings.theme != null
          ? Math.max(4f, Math.min(14f, settings.theme.blurRadius))
          : 8f;
      canvas.backdrop(x, y, w, h, radius, blurRadius, 3, alpha * Math.min(1f, bgOpacity * 1.4f));
    }
    int baseBg = settings.theme != null ? (settings.theme.background & 0x00ffffff) : 0x0b0f17;
    int fillColor = Colors.alpha(0xff000000 | baseBg, bgOpacity * alpha);
    canvas.roundedRect(x, y, w, h, radius, fillColor);
    int borderRgb = settings.theme != null ? (settings.theme.border & 0x00ffffff) : 0xffffff;
    float borderAlpha = Math.min(0.28f, bgOpacity * 0.42f + 0.06f) * alpha;
    canvas.strokeRoundedRect(x, y, w, h, radius, 0.75f, Colors.alpha(0xff000000 | borderRgb, borderAlpha));
  }

  public ExplosionRingLogic.ServerExplosionLearner explosionLearner() {
    return explosionLearner;
  }

  public EffectAlertLogic effectAlertLogic() {
    return effectAlertLogic;
  }

  public TapeMouseLogic tapeMouseLogic() {
    return tapeMouseLogic;
  }

  public void onIncomingChatLine(String text) {
    if (text == null || text.isEmpty()) return;
    hwhelper.observeChatLine(text);
    Settings.SoundControl sc = settings.soundControl;
    if (sc != null && sc.enabled && sc.chatMention) {
      if (SoundControlLogic.matchesChatMention(text, ownNickname, sc.mentionKeywords)) {
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastMentionSoundMs >= 450L) {
          lastMentionSoundMs = nowMs;
          String sid = SoundControlLogic.resolvePresetSoundId(sc.mentionPreset, "");
          platform.playClientSound(sid, sc.mentionVolume, sc.mentionPitch);
          if (sc.mentionHighlight) {
            String clean = SoundControlLogic.stripFormatting(text).trim();
            if (clean.length() > 54) clean = clean.substring(0, 51) + "…";
            latestMentionToast =
                new SoundControlLogic.MentionToast(ownNickname, clean, System.nanoTime());
          }
        }
      }
    }
  }

  public void renderTooltipBackdrop(Canvas canvas, float x, float y, float w, float h,
      int accentArgb, boolean hwStyle, String badgeText) {
    if (canvas == null || settings.tooltips == null || !settings.tooltips.enabled) return;
    dev.nightclient.renderer.TooltipOverlayRenderer.renderTooltipBackdrop(
        canvas, settings.theme, x, y, w, h, accentArgb, hwStyle, badgeText);
  }

  public void renderContainerPeek(Canvas canvas,
      dev.nightclient.renderer.TooltipOverlayRenderer.ContainerPeek peek, float anchorX,
      float anchorY, float anchorW, float anchorH, int screenW, int screenH) {
    renderContainerPeek(
        canvas, peek, anchorX, anchorY, anchorW, anchorH, screenW, screenH, -1000f, -1000f, null);
  }

  public int renderContainerPeek(Canvas canvas,
      dev.nightclient.renderer.TooltipOverlayRenderer.ContainerPeek peek, float anchorX,
      float anchorY, float anchorW, float anchorH, int screenW, int screenH, float mouseX,
      float mouseY, float[] boundsOut) {
    if (canvas == null || peek == null || settings.tooltips == null || !settings.tooltips.enabled)
      return -1;
    return dev.nightclient.renderer.TooltipOverlayRenderer.renderContainerPeek(canvas,
        settings.theme, peek, anchorX, anchorY, anchorW, anchorH, screenW, screenH,
        settings.language, mouseX, mouseY, boundsOut);
  }

  private void renderEffectAlertsHud(Canvas canvas, int width, int height, double time) {
    Settings.EffectAlert cfg = settings.effectAlert;
    if (FeaturePolicy.isBlocked("effectAlert") || cfg == null || !cfg.enabled || canvas == null || width <= 0 || height <= 0) return;
    List<EffectAlertLogic.HudAlert> alerts = effectAlertLogic.hudAlerts();
    if (alerts.isEmpty()) return;
    String lang = I18n.language(settings.language);
    float s = Math.max(0.65f, Math.min(2.0f, cfg.scale));
    float pulse = (float) (0.70 + 0.30 * Math.sin(time * 9.0));
    float cx = width * cfg.anchorX + cfg.offsetX;
    float baseY = height * cfg.anchorY + cfg.offsetY;
    float curY = baseY;

    for (int i = 0; i < alerts.size() && i < 4; i++) {
      EffectAlertLogic.HudAlert a = alerts.get(i);
      float alpha = a.fadeAlpha;
      if (alpha <= 0.02f) continue;
      String amp = EffectAlertLogic.romanAmplifier(a.amplifier);
      String status = a.expired
          ? I18n.text(lang, "effectAlert.hud.expired")
          : I18n.format(lang, "units.duration.seconds",
                Collections.<String, Object>singletonMap(
                    "value", String.format(java.util.Locale.ROOT, "%.1f", a.remainingSeconds)));
      String text = a.name + amp + " · " + status;

      float fs = 7.4f * s;
      float tw = canvas.textWidthFamily("studio-sans-medium", text, fs, true, 0f);
      float padX = 9.0f * s;
      float padY = 4.5f * s;
      float pillW = tw + padX * 2f;
      float pillH = fs + padY * 2f;
      float px = Math.max(6f, Math.min(width - pillW - 6f, cx - pillW * 0.5f));
      float py = Math.max(6f, Math.min(height - pillH - 6f, curY));

      int accent = a.expired ? 0xFFEF4444 : a.color;
      int bg = Colors.mix(0xDE090D16, accent, a.expired ? 0.30f : 0.16f + 0.10f * pulse);
      canvas.roundedRect(px, py, pillW, pillH, pillH * 0.5f, Colors.alpha(bg, alpha));
      canvas.strokeRoundedRect(px, py, pillW, pillH, pillH * 0.5f, Math.max(0.8f, 1.0f * s),
          Colors.alpha(accent, alpha * 0.90f));
      canvas.textFamily("studio-sans-medium", text, px + padX, py + padY - 0.2f * s, fs,
          Colors.alpha(a.expired ? 0xFFFEE2E2 : 0xFFF8FAFC, alpha), true, 0f);
      curY += pillH + 3.5f * s;
    }
  }

  private void renderHwTimersHud(Canvas canvas, int width, int height, double time) {
    if (FeaturePolicy.isBlocked("hwhelper.timers") || settings.hwhelper == null || !settings.hwhelper.enabled
        || settings.hwhelper.timers == null || !settings.hwhelper.timers.enabled || canvas == null)
      return;
    Settings.HwTimers cfg = settings.hwhelper.timers;
    List<HwPvpAndEventsLogic.HwActiveTimer> timers =
        hwhelper.timerTracker().snapshot(cfg, System.currentTimeMillis());
    if (timers.isEmpty()) return;
    String lang = I18n.language(settings.language);
    WorldRibbon.View view = hudCameraView(width, height);
    float s = (height / 540f) * cfg.scale;
    float personalY = height * 0.32f;

    for (int i = 0; i < timers.size(); i++) {
      HwPvpAndEventsLogic.HwActiveTimer t = timers.get(i);
      float sx;
      float sy;
      if (t.personal || view == null) {
        sx = width * 0.5f;
        sy = personalY;
        personalY += 28f * s;
      } else {
        view.project(t.x, t.y, t.z, projScratch);
        if (projScratch[2] <= 0.2 || Double.isNaN(projScratch[0]) || Double.isNaN(projScratch[1])
            || projScratch[2] > 42.0) {
          continue;
        }
        sx = (float) projScratch[0];
        sy = (float) projScratch[1];
      }

      boolean readySoon = t.remainingSeconds <= 2.0f;
      float pulse = readySoon ? (float) (0.65 + 0.35 * Math.sin(time * 11.0)) : 0f;
      String title = t.kind.label(lang);
      String val = I18n.format(lang, "units.duration.seconds",
          Collections.<String, Object>singletonMap(
              "value", String.format(java.util.Locale.ROOT, "%.1f", t.remainingSeconds)));
      String line = title + " · " + val;

      float fs = 7.4f * Math.max(0.75f, Math.min(1.45f, s));
      float tw = canvas.textWidthFamily("studio-sans-medium", line, fs, true, 0f);
      float padX = 7.5f * s;
      float padY = 4.2f * s;
      float barH = 2.2f * s;
      float cardW = Math.max(68f * s, tw + padX * 2f);
      float cardH = padY * 2f + fs + 3.0f * s + barH;
      float px = sx - cardW * 0.5f;
      float py = sy - cardH * 0.5f;
      if (px + cardW < -20 || px > width + 20 || py + cardH < -20 || py > height + 20) continue;

      int bg = Colors.mix(0xDE080C14, t.kind.color, readySoon ? 0.28f + 0.10f * pulse : 0.16f);
      canvas.roundedRect(px, py, cardW, cardH, 6f * s, bg);
      canvas.strokeRoundedRect(
          px, py, cardW, cardH, 6f * s, Math.max(0.8f, 0.95f * s), Colors.alpha(t.kind.color, 0.88f));
      canvas.textFamily("studio-sans-medium", line, px + (cardW - tw) * 0.5f, py + padY, fs,
          0xFFF8FAFC, true, 0f);

      float frac = Math.max(0f, Math.min(1f, t.remainingSeconds / t.totalSeconds));
      float barW = cardW - padX * 2f;
      float barY = py + padY + fs + 2.6f * s;
      canvas.roundedRect(barX(px, padX), barY, barW, barH, barH * 0.5f, 0x4494A3B8);
      if (frac > 0.01f) {
        canvas.roundedRect(barX(px, padX), barY, Math.max(barH, barW * frac), barH, barH * 0.5f,
            t.kind.color);
      }
    }
  }

  private static float barX(float px, float padX) {
    return px + padX;
  }

  private void renderHwEventsHud(Canvas canvas, int width, int height) {
    if (FeaturePolicy.isBlocked("hwhelper.events") || settings.hwhelper == null || !settings.hwhelper.enabled || settings.hwhelper.events == null
        || !settings.hwhelper.events.enabled || !settings.hwhelper.events.showHud || canvas == null)
      return;
    List<HwPvpAndEventsLogic.EventItem> events = hwhelper.filteredEvents();
    if (events.isEmpty()) return;
    String lang = I18n.language(settings.language);
    int showCount = Math.min(5, events.size());
    float x = 10f;
    float y = height * 0.36f;
    float headerFs = 6.8f;
    float rowFs = 6.3f;
    String hdr = I18n.format(lang, "hwEvents.hud_header",
        Collections.<String, Object>singletonMap("count", Integer.valueOf(events.size())));
    float maxW = canvas.textWidthFamily("studio-sans-medium", hdr, headerFs, true, 0f);
    long nowMs = System.currentTimeMillis();
    String[] lines = new String[showCount];
    int[] colors = new int[showCount];
    for (int i = 0; i < showCount; i++) {
      HwPvpAndEventsLogic.EventItem ev = events.get(i);
      lines[i] = ev.localizedTitle(lang) + " · " + ev.localizedServerName(lang)
          + " (" + ev.elapsedText(nowMs, lang) + ")";
      colors[i] = ev.rarityColor;
      maxW = Math.max(maxW, canvas.textWidthFamily("studio-sans-medium", lines[i], rowFs, false, 0f) + 8f);
    }
    float cardW = maxW + 16f;
    float cardH = 18f + showCount * 12.5f + 4f;
    canvas.roundedRect(x, y, cardW, cardH, 7f, 0xD6090D16);
    canvas.strokeRoundedRect(x, y, cardW, cardH, 7f, 0.75f, Colors.alpha(settings.theme.accent, 0.65f));
    canvas.textFamily("studio-sans-medium", hdr, x + 8f, y + 5.5f, headerFs, 0xFFF8FAFC, true, 0f);
    float ry = y + 18f;
    for (int i = 0; i < showCount; i++) {
      canvas.circle(x + 9.5f, ry + 3.4f, 2.0f, colors[i]);
      canvas.textFamily("studio-sans-medium", lines[i], x + 15f, ry, rowFs, 0xFFE2E8F0, false, 0f);
      ry += 12.5f;
    }
  }

  private void renderTapeMouseHud(Canvas canvas, int width, int height, double time) {
    Settings.TapeMouse cfg = settings.tapeMouse;
    if (FeaturePolicy.isBlocked("tapeMouse") || cfg == null || !cfg.enabled || !cfg.showHudIndicator || canvas == null) return;
    String label = "TapeMouse · " + TapeMouseLogic.formatBadgeText(cfg, settings.language);
    float fs = 6.6f;
    float tw = canvas.textWidthFamily("studio-sans-medium", label, fs, true, 0f);
    float pillW = tw + 18f;
    float pillH = 15f;
    float px = (width - pillW) * 0.5f;
    float py = 8f;
    float dotPulse = (float) (0.55 + 0.45 * Math.sin(time * 6.0));
    canvas.roundedRect(px, py, pillW, pillH, 7.5f, 0xD8090D16);
    canvas.strokeRoundedRect(
        px, py, pillW, pillH, 7.5f, 0.75f, Colors.alpha(settings.theme.accent, 0.75f));
    canvas.circle(px + 7.5f, py + pillH * 0.5f, 2.2f, Colors.alpha(0xFF4ADE80, dotPulse));
    canvas.textFamily("studio-sans-medium", label, px + 13f, py + 4.0f, fs, 0xFFF8FAFC, true, 0f);
  }

  private void renderMentionToastHud(Canvas canvas, int width, int height) {
    SoundControlLogic.MentionToast mt = latestMentionToast;
    if (FeaturePolicy.isBlocked("soundControl") || mt == null || canvas == null) return;
    float ageSec = (float) ((System.nanoTime() - mt.createdNanos) * 1e-9);
    if (ageSec >= 3.2f) {
      latestMentionToast = null;
      return;
    }
    float alpha = ageSec > 2.6f ? Math.max(0f, (3.2f - ageSec) / 0.6f) : 1.0f;
    String title = I18n.format(settings.language, "soundControl.mention_toast",
        Collections.<String, Object>singletonMap("text", mt.snippet));
    float fs = 6.6f;
    float tw = canvas.textWidthFamily("studio-sans-medium", title, fs, true, 0f);
    float pillW = tw + 16f;
    float pillH = 15.5f;
    float px = (width - pillW) * 0.5f;
    float py = height - 66f;
    canvas.roundedRect(px, py, pillW, pillH, 7.5f, Colors.alpha(0xDF0B101C, alpha));
    canvas.strokeRoundedRect(
        px, py, pillW, pillH, 7.5f, 0.8f, Colors.alpha(0xFFFBBF24, alpha * 0.88f));
    canvas.textFamily(
        "studio-sans-medium", title, px + 8f, py + 4.2f, fs, Colors.alpha(0xFFFEF3C7, alpha), true,
        0f);
  }

  private void renderExplosionBadges(Canvas canvas, int width, int height, float partialTick) {
    Settings.ExplosionRing cfg = settings.explosionRing;
    if (FeaturePolicy.isBlocked("explosionRing") || cfg == null || !cfg.enabled || !cfg.showDamageBadge || canvas == null || width <= 0
        || height <= 0)
      return;
    List<ExplosionRingLogic.HazardBadge> badges = platform.explosionBadges(partialTick);
    if (badges == null || badges.isEmpty()) return;
    WorldRibbon.View view = hudCameraView(width, height);
    if (view == null) return;
    String lang = I18n.language(settings.language);
    double[] point = projScratch;
    for (int i = 0; i < badges.size(); i++) {
      ExplosionRingLogic.HazardBadge b = badges.get(i);
      if (b == null) continue;
      view.project(b.x, b.y, b.z, point);
      if (Double.isNaN(point[0]) || Double.isNaN(point[1])) continue;
      double depth = point[2];
      if (depth < 0.5 || depth > cfg.maxDistance) continue;
      float sx = (float) point[0];
      float sy = (float) point[1];
      if (sx < -60 || sx > width + 60 || sy < -30 || sy > height + 30) continue;

      StringBuilder text = new StringBuilder();
      String localizedBadgeLabel = ExplosionRingLogic.localizeLabel(b.label, lang);
      if (!localizedBadgeLabel.isEmpty()) text.append(localizedBadgeLabel);
      if (b.fuseSeconds >= 0f) {
        if (text.length() > 0) text.append(" · ");
        text.append(I18n.format(lang, "units.duration.seconds",
            Collections.<String, Object>singletonMap(
                "value", String.format(java.util.Locale.ROOT, "%.1f", b.fuseSeconds))));
      }
      if (b.lethal) {
        if (text.length() > 0) text.append(" · ");
        text.append(I18n.text(lang, "explosionRing.hud.lethal"));
      } else if (b.estimatedDamage >= 0.5f) {
        if (text.length() > 0) text.append(" · ");
        text.append(String.format(java.util.Locale.ROOT, "-%.1f♥", b.estimatedDamage * 0.5f));
      } else if (b.learned) {
        if (text.length() > 0) text.append(" · ");
        text.append("R").append(Math.round(b.radius));
      }
      String label = text.toString();
      if (label.isEmpty()) continue;

      float distFade = depth > cfg.maxDistance * 0.78f
          ? Math.max(0f, 1f - (float) ((depth - cfg.maxDistance * 0.78f) / (cfg.maxDistance * 0.22f)))
          : 1f;
      if (distFade <= 0.04f) continue;

      float fontSize = 6.8f;
      float tw = canvas.textWidthFamily("studio-sans-medium", label, fontSize, true, 0);
      float padX = 5.5f;
      float padY = 2.8f;
      float pillW = tw + padX * 2f;
      float pillH = fontSize + padY * 2f;
      float px = sx - pillW * 0.5f;
      float py = sy - pillH - 4f;
      float r = pillH * 0.5f;
      int bg = Colors.mix(0xd40a0d14, b.color, b.lethal ? 0.26f : 0.14f);
      canvas.roundedRect(px, py, pillW, pillH, r, Colors.alpha(bg, distFade));
      canvas.strokeRoundedRect(px, py, pillW, pillH, r, 0.8f, Colors.alpha(b.color, distFade * 0.85f));
      canvas.textFamily("studio-sans-medium", label, px + padX, py + padY - 0.2f, fontSize,
          Colors.alpha(b.lethal ? 0xffffe4ea : 0xfff3f6fc, distFade), true, 0);
    }
  }

  private static void renderPlayerHealthHearts(Canvas canvas, int width, int height, float sx,
      float sy, float s, float a, PlayerHealthView ph, boolean hasAbs, boolean hasEnch, double time) {
    int baseContainers = Math.max(1, Math.min(20, (int) Math.ceil(ph.maxHealth / 2f)));
    int hpHalves = Math.max(0, Math.min(baseContainers * 2, (int) Math.ceil(ph.health)));
    int absContainers = hasAbs ? Math.max(1, Math.min(20, (int) Math.ceil(ph.absorption / 2f))) : 0;
    int absHalves = hasAbs ? Math.max(0, Math.min(absContainers * 2, (int) Math.ceil(ph.absorption))) : 0;

    int cols = 10;
    int baseRows = (baseContainers + cols - 1) / cols;
    int absRows = absContainers > 0 ? (absContainers + cols - 1) / cols : 0;
    int maxRowCount = Math.min(cols, Math.max(baseContainers, absContainers));

    float hs = 7.0f * s;
    float hStep = hs + 1.15f * s;
    float rowGap = 1.5f * s;
    String overflowText = ph.health > 40f || (hasAbs && ph.absorption > 40f)
        ? " " + formatHpValue(ph.health + (hasAbs ? ph.absorption : 0f))
        : "";
    float overflowW = overflowText.isEmpty()
        ? 0f
        : canvas.textWidthFamily("studio-sans-medium", overflowText, 6.6f * s, true, 0);

    float heartsW = maxRowCount * hStep - 1.15f * s;
    float contentW = heartsW + overflowW;
    int totalRows = baseRows + absRows;
    float totalH = totalRows * hs + Math.max(0, totalRows - 1) * rowGap;
    float x = sx - contentW * .5f, y = sy - totalH;
    if (x + contentW < 0 || x > width || y + totalH < 0 || y > height) return;

    float curY = y;
    for (int row = absRows - 1; row >= 0; row--) {
      int startIdx = row * cols;
      int rowCount = Math.min(cols, absContainers - startIdx);
      float rowX = x + (heartsW - (rowCount * hStep - 1.15f * s)) * .5f;
      for (int c = 0; c < rowCount; c++) {
        int remaining = absHalves - (startIdx + c) * 2;
        int fill = remaining >= 2 ? 2 : remaining == 1 ? 1 : 0;
        drawVectorHeart(canvas, rowX + c * hStep, curY, hs, fill,
            0xfffacc15, 0xfff59e0b, 0xfffef08a, 0xff1e1912, a);
      }
      curY += hs + rowGap;
    }

    for (int row = baseRows - 1; row >= 0; row--) {
      int startIdx = row * cols;
      int rowCount = Math.min(cols, baseContainers - startIdx);
      float rowX = x + (heartsW - (rowCount * hStep - 1.15f * s)) * .5f;
      for (int c = 0; c < rowCount; c++) {
        int remaining = hpHalves - (startIdx + c) * 2;
        int fill = remaining >= 2 ? 2 : remaining == 1 ? 1 : 0;
        drawVectorHeart(canvas, rowX + c * hStep, curY, hs, fill,
            0xffff4258, 0xffdc2626, 0xffffb3bd, 0xff1f1924, a);
      }
      curY += hs + rowGap;
    }

    if (!overflowText.isEmpty()) {
      float tx = x + heartsW, ty = y + totalH * .5f - 3.3f * s;
      float shadowOff = Math.max(.5f, .6f * s);
      canvas.textFamily("studio-sans-medium", overflowText, tx + shadowOff, ty + shadowOff,
          6.6f * s, Colors.alpha(0xb805070b, a), true, 0);
      canvas.textFamily("studio-sans-medium", overflowText, tx, ty,
          6.6f * s, Colors.alpha(hasAbs ? 0xfffde047 : 0xfff8fafc, a), true, 0);
    }
  }

  private static void drawVectorHeart(Canvas canvas, float x, float y, float size, int fill,
      int brightColor, int shadeColor, int glintColor, int emptyColor, float alpha) {
    float pad = Math.max(.55f, size * .075f);
    int outline = Colors.alpha(0xf006080c, alpha);
    paintHeartSilhouette(canvas, x - pad, y - pad * .7f, size + pad * 2, outline);
    if (fill == 0) {
      paintHeartSilhouette(canvas, x, y, size, Colors.alpha(emptyColor, alpha));
      return;
    }
    if (fill == 1) {
      paintHeartSilhouette(canvas, x, y, size, Colors.alpha(emptyColor, alpha));
      float r = size * .245f;
      int col = Colors.alpha(brightColor, alpha);
      canvas.circle(x + size * .26f, y + size * .29f, r, col);
      canvas.fillTriangle(
          x + size * .03f, y + size * .34f,
          x + size * .50f, y + size * .27f,
          x + size * .50f, y + size * .93f, col);
      canvas.circle(x + size * .23f, y + size * .23f, size * .085f, Colors.alpha(glintColor, alpha * .85f));
      return;
    }
    paintHeartSilhouette(canvas, x, y, size, Colors.alpha(shadeColor, alpha));
    float inset = size * .07f;
    paintHeartSilhouette(canvas, x + inset * .3f, y, size - inset, Colors.alpha(brightColor, alpha));
    canvas.circle(x + size * .23f, y + size * .23f, size * .09f, Colors.alpha(glintColor, alpha * .88f));
  }

  private static void paintHeartSilhouette(Canvas canvas, float x, float y, float size, int color) {
    float r = size * .26f;
    canvas.circle(x + size * .27f, y + size * .29f, r, color);
    canvas.circle(x + size * .73f, y + size * .29f, r, color);
    canvas.fillTriangle(
        x + size * .27f, y + size * .27f,
        x + size * .73f, y + size * .27f,
        x + size * .50f, y + size * .66f, color);
    canvas.fillTriangle(
        x + size * .025f, y + size * .34f,
        x + size * .975f, y + size * .34f,
        x + size * .50f, y + size * .93f, color);
  }

  private static String formatHpValue(float value) {
    float clamped = Math.max(0f, value);
    int rounded = Math.round(clamped);
    if (Math.abs(clamped - rounded) < .05f) return Integer.toString(rounded);
    return String.format(java.util.Locale.ROOT, "%.1f", clamped);
  }

  public void renderMenu(Canvas canvas, int width, int height, float mouseX, float mouseY) {
    if (menu.isEditor()) {
      tnts = platform.tnts(0, width, height);
    }
    menu.render(canvas, width, height, mouseX, mouseY, nowSeconds());
  }

  public boolean isChatHudEditing() {
    return chatHud.editing();
  }

  public boolean chatHudUsesBackdrop() {
    return chatHud.usesBackdrop();
  }

  public void renderChatHudEditor(
      Object owner, Canvas canvas, int width, int height, float mouseX, float mouseY) {
    if (chatHud.editing()) {
      tnts = platform.tnts(0, width, height);
    }
    chatHud.renderEditor(owner, canvas, width, height, mouseX, mouseY, nowSeconds());
  }

  public void renderChatHudChrome(
      Object owner, Canvas canvas, int width, int height, float mouseX, float mouseY) {
    chatHud.renderChrome(owner, canvas, width, height, mouseX, mouseY, nowSeconds());
    renderChatCopyToast(canvas, width, height);
  }

  public boolean chatHudMouseClicked(Object owner, double x, double y, int button) {
    return chatHud.mouseClicked(owner, x, y, button);
  }

  public boolean chatHudMouseDragged(
      Object owner, double x, double y, int button, double dx, double dy) {
    return chatHud.mouseDragged(owner, x, y, button, dx, dy);
  }

  public boolean chatHudMouseReleased(Object owner, double x, double y, int button) {
    return chatHud.mouseReleased(owner, x, y, button);
  }

  public boolean chatHudMouseScrolled(Object owner, double x, double y, double amount,
      int modifiers) {
    return chatHud.mouseScrolled(owner, x, y, amount, modifiers);
  }

  public boolean chatHudKeyPressed(Object owner, int key, int modifiers) {
    return chatHud.keyPressed(owner, key, modifiers);
  }

  public boolean chatHudKeyReleased(Object owner, int key, int modifiers) {
    return chatHud.keyReleased(owner, key, modifiers);
  }

  public boolean chatHudCharTyped(Object owner, int codePoint) {
    return chatHud.charTyped(owner, codePoint);
  }

  public void chatHudClosed(Object owner) {
    chatHud.close(owner);
  }

  public float[] chatHudElementBounds(String id) {
    return chatHud.elementBounds(id);
  }

  @Override
  public void changed() {
    pendingConfigSaveAt = 0L;
    settings.validate();
    settingsRevision.incrementAndGet();
    prepareSettings();
    store.changed(settings);
  }

  @Override
  public void changedLive() {
    settings.validate();
    settingsRevision.incrementAndGet();
    prepareSettings();
    pendingConfigSaveAt = System.nanoTime() + 250_000_000L;
  }

  private void flushPendingConfigSave(boolean force) {
    long due = pendingConfigSaveAt;
    if (due != 0L && (force || System.nanoTime() - due >= 0L)) {
      pendingConfigSaveAt = 0L;
      store.changed(settings);
    }
  }

  @Override
  public String clipboard() {
    return platform.clipboard();
  }

  @Override
  public void clipboard(String value) {
    platform.clipboard(value);
  }

  @Override
  public List<String> profiles() {
    return store.profiles();
  }

  @Override
  public void saveProfile(String name) {
    store.saveProfile(name, settings);
  }

  @Override
  public void loadProfile(String name) {
    Settings next = store.loadProfile(name);
    if (next.waypoints != null && next.waypoints.points != null) next.waypoints.points.clear();
    checkpoint();
    replace(next);
  }

  @Override
  public void deleteProfile(String name) {
    store.deleteProfile(name);
  }

  @Override
  public String exportProfile() {
    return store.encode(settings);
  }

  @Override
  public void importProfile(String json) {
    Settings next = store.decode(json);
    if (next.waypoints != null && next.waypoints.points != null) next.waypoints.points.clear();
    checkpoint();
    replace(next);
  }

  @Override
  public void reset() {
    checkpoint();
    replace(new Settings());
  }

  @Override
  public void resetTheme() {
    checkpoint();
    dev.nightclient.config.Theme reset = dev.nightclient.config.Theme.defaults();
    dev.nightclient.skin.DefaultSkin.applyDefaultsSafe(settings.skin.preset, reset);
    settings.theme = reset;
    if (settings.skin.appearance == null)
      settings.skin.appearance =
          new java.util.LinkedHashMap<String, dev.nightclient.config.Theme>();
    settings.skin.appearance.put(settings.skin.preset, reset.copy());
    settings.skin.appliedPreset = settings.skin.preset;
    settings.skin.enabled = false;
    settings.menu = new Settings.Menu();
    reloadSkin();
    changed();
  }

  @Override
  public void undo() {
    if (undo == null) return;
    String previous = undo;
    undo = null;
    replace(store.decode(previous));
  }

  @Override
  public boolean canUndo() {
    return undo != null;
  }

  @Override
  public void checkpoint() {
    undo = store.encode(settings);
  }

  private void replace(Settings next) {
    next.validate();
    settings.language = next.language;
    settings.theme = next.theme;
    settings.menu = next.menu;
    settings.saturation = next.saturation;
    settings.hud = next.hud;
    settings.skin = next.skin;
    settings.interfaceState = next.interfaceState;
    settings.moduleBindings = new java.util.LinkedHashMap<String, String>(next.moduleBindings);
    settings.inventory = next.inventory;
    settings.tnt = next.tnt;
    settings.crosshair = next.crosshair;
    settings.zoom = next.zoom;
    settings.protect = next.protect;
    settings.armor = next.armor;
    settings.potions = next.potions;
    settings.food = next.food;
    settings.target = next.target;
    settings.tweaksy = next.tweaksy;
    settings.optimization = next.optimization;
    settings.itemScroller = next.itemScroller;
    settings.hitColor = next.hitColor;
    settings.hitbox = next.hitbox;
    settings.waypoints = next.waypoints;
    settings.lowFire = next.lowFire;
    settings.itemPhysics = next.itemPhysics;
    settings.viewmodel = next.viewmodel;
    settings.aspectRatio = next.aspectRatio;
    settings.swing = next.swing;
    settings.freelook = next.freelook;
    settings.damageSplash = next.damageSplash;
    settings.scoreboard = next.scoreboard;
    settings.keystrokes = next.keystrokes;
    settings.coordinates = next.coordinates;
    settings.ping = next.ping;
    settings.fps = next.fps;
    settings.hwhelper = next.hwhelper;
    settings.playerHealth = next.playerHealth;
    settings.totemCounter = next.totemCounter;
    settings.playerPing = next.playerPing;
    settings.safeCtLeave = next.safeCtLeave;
    settings.betterChat = next.betterChat;
    settings.worldTime = next.worldTime;
    settings.customSky = next.customSky;
    settings.blockOverlay = next.blockOverlay;
    settings.enchantGlint = next.enchantGlint;
    settings.explosionRing = next.explosionRing;
    settings.tooltips = next.tooltips;
    settings.minimap = next.minimap;
    settings.worldMap = next.worldMap;
    settings.atmosphere = next.atmosphere;
    settings.wavyCapes = next.wavyCapes;
    settings.accountSwitcher = next.accountSwitcher;
    settings.soundControl = next.soundControl;
    settings.effectAlert = next.effectAlert;
    settings.tapeMouse = next.tapeMouse;
    settings.noRender = next.noRender;
    settings.cosmetics = next.cosmetics;
    settings.motionBlur = next.motionBlur;
    settings.tabAnimation = next.tabAnimation;
    damageIndicators.clear();
    reloadSkin();
    changed();
  }

  public void shutdown() {
    flushPendingConfigSave(true);
    hwhelper.shutdown();
    cosmetics.shutdown();
    accounts.shutdown();
    customAudio.shutdown();
    chatHud.close();
    worldMap.onClose();
    mapSettings.onClose();
    waypointsMenu.onClose();
    mapCache.shutdown();
    menu.onClose();
    store.close();
  }

  @Override
  public dev.nightclient.skin.SkinDocument skinDocument() {
    String preset = settings.skin.preset;
    dev.nightclient.skin.SkinDocument custom =
        settings.skin.enabled && skins != null ? skins.document() : null;
    if (cachedSkinDocument == null || cachedSkinSource != custom
        || !java.util.Objects.equals(preset, cachedSkinPreset)) {
      cachedSkinDocument = dev.nightclient.skin.DefaultSkin.resolve(preset, custom);
      cachedSkinPreset = preset;
      cachedSkinSource = custom;
    }
    return cachedSkinDocument;
  }

  @Override
  public void reloadSkin() {
    if (settings.skin.enabled) skins.reload(settings.skin.file);
    settings.skinDocument = skinDocument();
    settingsRevision.incrementAndGet();
    invalidateHudFrame();
    if (menu != null) menu.refreshSkin();
  }

  @Override
  public void createSkinExamples() {
    try {
      skins.writeExamples(settings.skin.preset);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  @Override
  public String skinStatus() {
    if (skins.error() != null) return skins.error();
    return settings.skin.enabled && skins.document() != null
        ? settings.skin.file
        : "config/nightclient/" + settings.skin.file;
  }

  private HudFrame renderingHudFrame() {
    return hudFrameRendering && hudFrame != null && hudFrameReadySerial == hudFrame.serial
        ? hudFrame
        : null;
  }

  @Override
  public List<Cooldown> cooldowns() {
    HudFrame frame = renderingHudFrame();
    cooldowns = frame == null
        ? hwhelper.explosiveThingCooldown().mergeInto(
            platform.cooldowns(0), System.currentTimeMillis())
        : frame.cooldowns();
    return cooldowns;
  }

  @Override
  public List<InventorySlot> inventory() {
    HudFrame frame = renderingHudFrame();
    inventory = frame == null ? platform.inventory(0) : frame.inventory();
    return inventory;
  }

  @Override
  public List<TntView> tnts() {
    return tnts;
  }

  @Override
  public boolean waypointAddHere() {
    String before = store.encode(settings);
    if (!platform.waypointAddHere()) return false;
    if (settings.waypoints != null) settings.waypoints.enabled = true;
    undo = before;
    changed();
    return true;
  }

  @Override
  public double[] playerPosition() {
    return platform.playerPosition();
  }

  @Override
  public String currentDimension() {
    return platform.currentDimension();
  }

  @Override
  public String currentWorldId() {
    if (mapCache != null && mapCache.identity().isActive()) {
      String id = mapCache.identity().worldId();
      if (id != null && !id.isEmpty()) return id;
    }
    return platform.currentWorldId();
  }

  @Override
  public String currentWorldLabel() {
    if (mapCache != null && mapCache.identity().isActive()) {
      String label = mapCache.identity().worldLabel();
      if (label != null && !label.isEmpty()) return label;
    }
    return platform.currentWorldLabel();
  }

  @Override
  public void renderMinimap(Canvas canvas, float x, float y, float w, float h, float scale,
      boolean editorPreview) {
    MinimapRenderer.renderAt(canvas, settings, mapCache,
        editorPreview ? null : platform.mapRadarSnapshot(0f), x, y, w, h, scale, editorPreview,
        nowSeconds());
  }

  @Override
  public void openWorldMap() {
    close();
    platform.openWorldMap();
  }

  @Override
  public void openMapSettings() {
    close();
    platform.openMapSettings();
  }

  @Override
  public void openWaypointsMenu() {
    close();
    platform.openWaypointsMenu();
  }

  @Override
  public void clearCurrentWorldMapCache() {
    mapCache.clearCurrentWorldCache();
    platform.rescanLoadedMapChunks();
    waypointFeedback(I18n.text(settings.language, "worldMap.cache_cleared_world"));
  }

  @Override
  public void clearAllMapCache() {
    mapCache.clearAllCache();
    platform.rescanLoadedMapChunks();
    waypointFeedback(I18n.text(settings.language, "worldMap.cache_cleared_all"));
  }

  private boolean addWaypoint(boolean added) {
    if (!added) return false;
    if (settings.waypoints != null) settings.waypoints.enabled = true;
    checkpoint();
    changed();
    return true;
  }

  @Override
  public List<InventorySlot> armor() {
    HudFrame frame = renderingHudFrame();
    armor = frame == null ? platform.armor(0) : frame.armor();
    return armor;
  }

  @Override
  public List<PotionView> potions() {
    HudFrame frame = renderingHudFrame();
    potions = frame == null ? platform.potions(0) : frame.potions();
    return potions;
  }

  @Override
  public VitalsView vitals() {
    HudFrame frame = renderingHudFrame();
    vitals = frame == null ? platform.vitals(0) : frame.vitals();
    return vitals;
  }

  @Override
  public TargetView target() {
    HudFrame frame = renderingHudFrame();
    target = frame == null ? platform.target(0) : frame.target();
    return target;
  }

  @Override
  public dev.nightclient.api.ViewmodelSelection viewmodelSelection(float x, float y) {
    return platform.viewmodelSelection(x, y);
  }

  @Override
  public void requestViewmodelPick(float x, float y) {
    platform.requestViewmodelPick(x, y);
  }

  @Override
  public void hoverViewmodel(float x, float y) {
    platform.hoverViewmodel(x, y);
  }

  @Override
  public boolean viewmodelEditorSupported() {
    return platform.viewmodelEditorSupported();
  }

  @Override
  public boolean fireVisible() {
    return platform.fireVisible();
  }

  @Override
  public boolean crosshairTargetHit() {
    if (hudFrameRendering && hudFrame != null) return hudFrame.crosshairTargetHit();
    return platform.crosshairTargetHit();
  }

  @Override
  public boolean leftMainArm() {
    return platform.leftMainArm();
  }

  @Override
  public void setViewmodelEditing(boolean value) {
    viewmodelEditing = value;
    platform.setViewmodelEditing(value);
  }

  public boolean isViewmodelEditing() {
    return viewmodelEditing;
  }

  private String settingsRawKey(String id) {
    if ("tapeMouseKey".equals(id))
      return settings.tapeMouse == null ? null : settings.tapeMouse.customKey;
    return null;
  }

  private boolean setSettingsRawKey(String id, String key) {
    String clean = key == null || key.isEmpty() ? "key.keyboard.unknown" : key;
    if ("tapeMouseKey".equals(id)) {
      if (settings.tapeMouse == null) settings.tapeMouse = new Settings.TapeMouse();
      checkpoint();
      settings.tapeMouse.customKey = clean;
      changed();
      return true;
    }
    return false;
  }

  @Override
  public String keyBindingLabel(String id) {
    String raw = settingsRawKey(id);
    if (raw != null) {
      if (raw.isEmpty() || raw.endsWith(".unknown"))
        return I18n.text(settings.language, "binding.unbound");
      String nativeLabel = platform.keyBindingLabel("rawkey:" + raw);
      if (nativeLabel != null && !nativeLabel.isEmpty()) return nativeLabel;
      int at = raw.lastIndexOf('.');
      return raw.substring(at + 1).replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
    }
    String label = platform.keyBindingLabel(id);
    if ((label == null || label.isEmpty()) && id.startsWith("module:")) {
      String stored = settings.moduleBindings.get(id.substring(7));
      if (stored != null && !stored.endsWith(".unknown")) {
        int at = stored.lastIndexOf('.');
        label = stored.substring(at + 1).replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
      }
    }
    return label == null || label.isEmpty() ? I18n.text(settings.language, "binding.unbound")
                                            : label;
  }

  @Override
  public void setKeyBinding(String id, String nativeKeyName) {
    if (setSettingsRawKey(id, nativeKeyName)) return;
    if (id.startsWith("module:")) {
      String module = id.substring(7);
      if (module.matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}")) {
        checkpoint();
        if ("key.keyboard.unknown".equals(nativeKeyName))
          settings.moduleBindings.remove(module);
        else
          settings.moduleBindings.put(module, nativeKeyName);
        changed();
      }
    } else
      platform.setKeyBinding(id, nativeKeyName);
  }

  public void toggleModule(String id) {
    if (FeaturePolicy.isBlocked(id)) return;
    if (moduleCatalog == null) moduleCatalog = new dev.nightclient.ui.skin.UiCatalog(settings);
    dev.nightclient.ui.skin.UiCatalog.Module module = moduleCatalog.module(id);
    if (module == null || !module.id.equals(id)) return;
    checkpoint();
    if (moduleCatalog.toggleModule(id)) changed();
  }

  private boolean commandFixEnabled() {
    return !FeaturePolicy.isBlocked("tweaksy.commandFix")
        && settings.tweaksy != null && settings.tweaksy.enabled
        && settings.tweaksy.commandFix != null && settings.tweaksy.commandFix.enabled;
  }

  public String fixCommand(String value) {
    if (!FeaturePolicy.isBlocked("waypoints") && value != null && value.startsWith("/nc-wp ")) {
      String[] parts = value.trim().split("\\s+");
      if (parts.length >= 4) {
        try {
          double x = Double.parseDouble(parts[1]);
          double y = Double.parseDouble(parts[2]);
          double z = Double.parseDouble(parts[3]);
          platform.openWaypointCreate(x, y, z);
          return "";
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (handleWaypointCommand(value)) return "";
    if (handleTapeMouseCommand(value)) return "";
    String normalized = value;
    if (commandFixEnabled() && value != null && (value.startsWith("/") || value.startsWith("."))) {
      String candidate = value.startsWith(".")
          ? ("." + CommandLayoutFix.fix("/" + value.substring(1)).substring(1))
          : CommandLayoutFix.fix(value);
      if (!candidate.equals(value) && handleWaypointCommand(candidate)) return "";
      if (!candidate.equals(value) && handleTapeMouseCommand(candidate)) return "";
      if (value.startsWith("/")) normalized = candidate;
    }
    String processed = hwhelper.handleCommand(normalized);
    if (processed != null && processed.isEmpty()) return "";
    return commandFixEnabled() && processed != null && processed.startsWith("/")
        ? CommandLayoutFix.fix(processed)
        : processed;
  }

  private boolean handleTapeMouseCommand(String raw) {
    if (FeaturePolicy.isBlocked("tapeMouse") || raw == null || settings.tapeMouse == null) return false;
    String trimmed = raw.trim();
    if (!(trimmed.startsWith("/") || trimmed.startsWith("."))) return false;
    String slashForm = trimmed.startsWith(".") ? ("/" + trimmed.substring(1)) : trimmed;
    TapeMouseLogic.CommandOutcome outcome =
        TapeMouseLogic.parseCommand(slashForm, settings.tapeMouse, settings.language);
    if (outcome == null || !outcome.intercepted) return false;
    if (outcome.settingsChanged) changed();
    if (outcome.clientMessage != null && !outcome.clientMessage.isEmpty()) {
      platform.sendFeedback(outcome.clientMessage);
    }
    return true;
  }

  private boolean handleWaypointCommand(String raw) {
    if (FeaturePolicy.isBlocked("waypoints") || raw == null) return false;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return false;
    int space = trimmed.indexOf(' ');
    String head = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
    if (!"/wp".equals(head) && !"/way".equals(head) && !"/waypoint".equals(head) && !"/waypoints".equals(head)
        && !".wp".equals(head) && !".way".equals(head) && !".waypoint".equals(head) && !".waypoints".equals(head)) {
      return false;
    }
    String rest = space < 0 ? "" : trimmed.substring(space + 1).trim();
    String[] tokens = rest.isEmpty() ? new String[0] : rest.split("\\s+");
    String sub = tokens.length == 0 ? "help" : tokens[0].toLowerCase(java.util.Locale.ROOT);
    if (settings.waypoints.points == null) settings.waypoints.points = new ArrayList<Waypoint>();
    List<Waypoint> points = settings.waypoints.points;
    String currentWorld = Waypoint.normalizeWorld(currentWorldId());

    if ("help".equals(sub) || "?".equals(sub)) {
      waypointFeedback(I18n.text(settings.language, "waypoint.cmd_help"));
      return true;
    }
    if ("menu".equals(sub) || "gui".equals(sub) || "open".equals(sub)) {
      platform.openWaypointsMenu();
      return true;
    }
    if ("list".equals(sub) || "ls".equals(sub)) {
      boolean showAll = tokens.length > 1 && "all".equalsIgnoreCase(tokens[1]);
      List<Waypoint> scoped = showAll ? points : worldWaypoints(points, currentWorld);
      if (scoped.isEmpty()) {
        waypointFeedback(I18n.text(settings.language, "waypoint.empty"));
        return true;
      }
      waypointFeedback(I18n.text(settings.language, "waypoint.cmd_list_header")
                           .replace("{count}", Integer.toString(scoped.size())));
      for (int i = 0; i < scoped.size(); i++) {
        Waypoint p = scoped.get(i);
        if (p == null) continue;
        String dim = p.dimension == null ? "" : p.dimension.replace("minecraft:", "");
        String worldTag = showAll ? p.displayWorld() : "";
        String vis = p.visible ? "" : (" [" + I18n.text(settings.language, "mapSettings.off") + "]");
        platform.sendFeedback("§7" + (i + 1) + ". §f" + p.displayName() + " §8(" + p.coordinates()
            + (worldTag.isEmpty() ? "" : ", " + worldTag)
            + (dim.isEmpty() ? "" : ", " + dim) + ")§7" + vis);
      }
      return true;
    }
    if ("clear".equals(sub)) {
      boolean clearTemp = tokens.length > 1 && "temp".equalsIgnoreCase(tokens[1]);
      boolean clearAll = tokens.length > 1 && "all".equalsIgnoreCase(tokens[1]);
      checkpoint();
      if (clearTemp) {
        for (int i = points.size() - 1; i >= 0; i--) {
          Waypoint wp = points.get(i);
          if (wp != null && wp.temp) points.remove(i);
        }
      } else if (clearAll || currentWorld.isEmpty()) {
        points.clear();
      } else {
        settings.waypoints.removeWorld(currentWorld);
      }
      changed();
      waypointFeedback(I18n.text(settings.language,
          clearTemp ? "waypointsMenu.status.cleared_temp" : "waypoint.deleted_all"));
      return true;
    }
    if ("tp".equals(sub) || "teleport".equals(sub)) {
      String query = joinTokens(tokens, 1, tokens.length);
      if (query.isEmpty()) {
        waypointFeedback(I18n.text(settings.language, "waypoint.cmd_help"));
        return true;
      }
      Waypoint match = findWaypointByQuery(worldWaypoints(points, currentWorld), query);
      if (match == null) {
        waypointFeedback(I18n.text(settings.language, "waypoint.cmd_not_found")
                             .replace("{name}", query));
        return true;
      }
      int tx = (int) Math.round(match.x);
      int ty = (int) Math.round(match.y);
      int tz = (int) Math.round(match.z);
      platform.sendCommandToServer("tp " + tx + " " + ty + " " + tz);
      waypointFeedback(I18n.text(settings.language, "waypoint.cmd_tp")
                           .replace("{name}", match.displayName())
                           .replace("{coords}", match.coordinates()));
      return true;
    }
    if ("here".equals(sub)) {
      String label = joinTokens(tokens, 1, tokens.length);
      addWaypointViaCommand(label, null, null, null, null);
      return true;
    }
    if ("add".equals(sub) || "new".equals(sub) || "set".equals(sub)) {
      int end = tokens.length;
      Integer explicitColor = null;
      if (end > 1) {
        Integer parsed = parseHexColorToken(tokens[end - 1]);
        if (parsed != null) {
          explicitColor = parsed;
          end--;
        }
      }
      if (end <= 1) {
        addWaypointViaCommand("", null, null, null, explicitColor);
        return true;
      }
      double[] playerPos = platform.playerPosition();
      boolean hasPos = playerPos != null && playerPos.length >= 3;
      if (end >= 4) {
        Double x = parseCoordToken(tokens[1], hasPos ? playerPos[0] : null);
        Double y = parseCoordToken(tokens[2], hasPos ? playerPos[1] : null);
        Double z = parseCoordToken(tokens[3], hasPos ? playerPos[2] : null);
        if (x != null && y != null && z != null) {
          String label = joinTokens(tokens, 4, end);
          addWaypointViaCommand(label, x, y, z, explicitColor);
          return true;
        }
        Double tx = parseCoordToken(tokens[end - 3], hasPos ? playerPos[0] : null);
        Double ty = parseCoordToken(tokens[end - 2], hasPos ? playerPos[1] : null);
        Double tz = parseCoordToken(tokens[end - 1], hasPos ? playerPos[2] : null);
        if (tx != null && ty != null && tz != null) {
          String label = joinTokens(tokens, 1, end - 3);
          addWaypointViaCommand(label, tx, ty, tz, explicitColor);
          return true;
        }
      }
      if (end >= 3) {
        Double fx = parseCoordToken(tokens[1], hasPos ? playerPos[0] : null);
        Double fz = parseCoordToken(tokens[2], hasPos ? playerPos[2] : null);
        if (fx != null && fz != null) {
          double fy = defaultSurfaceY(fx, fz, playerPos);
          String label = joinTokens(tokens, 3, end);
          addWaypointViaCommand(label, fx, fy, fz, explicitColor);
          return true;
        }
        Double tx = parseCoordToken(tokens[end - 2], hasPos ? playerPos[0] : null);
        Double tz = parseCoordToken(tokens[end - 1], hasPos ? playerPos[2] : null);
        if (tx != null && tz != null) {
          double ty = defaultSurfaceY(tx, tz, playerPos);
          String label = joinTokens(tokens, 1, end - 2);
          addWaypointViaCommand(label, tx, ty, tz, explicitColor);
          return true;
        }
      }
      String label = joinTokens(tokens, 1, end);
      addWaypointViaCommand(label, null, null, null, explicitColor);
      return true;
    }
    if ("del".equals(sub) || "delete".equals(sub) || "remove".equals(sub) || "rm".equals(sub)) {
      String query = joinTokens(tokens, 1, tokens.length);
      if (query.isEmpty()) {
        waypointFeedback(I18n.text(settings.language, "waypoint.cmd_help"));
        return true;
      }
      Waypoint match = findWaypointByQuery(worldWaypoints(points, currentWorld), query);
      if (match == null) {
        waypointFeedback(I18n.text(settings.language, "waypoint.cmd_not_found")
                             .replace("{name}", query));
        return true;
      }
      String removedName = match.displayName();
      checkpoint();
      points.remove(match);
      changed();
      waypointFeedback(I18n.text(settings.language, "waypoint.cmd_removed")
                           .replace("{name}", removedName));
      return true;
    }
    if ("toggle".equals(sub)) {
      String query = joinTokens(tokens, 1, tokens.length);
      if (query.isEmpty()) {
        checkpoint();
        settings.waypoints.enabled = !settings.waypoints.enabled;
        changed();
        String state = I18n.text(
            settings.language, settings.waypoints.enabled ? "mapSettings.on" : "mapSettings.off");
        waypointFeedback(I18n.text(settings.language, "waypoint.cmd_toggled")
                             .replace("{name}", I18n.text(settings.language, "module.waypoints.label"))
                             .replace("{state}", state));
        return true;
      }
      Waypoint match = findWaypointByQuery(worldWaypoints(points, currentWorld), query);
      if (match == null) {
        waypointFeedback(I18n.text(settings.language, "waypoint.cmd_not_found")
                             .replace("{name}", query));
        return true;
      }
      checkpoint();
      match.visible = !match.visible;
      changed();
      waypointFeedback(I18n.text(settings.language, "waypoint.cmd_toggled")
                           .replace("{name}", match.displayName())
                           .replace("{state}",
                               I18n.text(settings.language,
                                   match.visible ? "mapSettings.on" : "mapSettings.off")));
      return true;
    }
    waypointFeedback(I18n.text(settings.language, "waypoint.cmd_help"));
    return true;
  }

  private static List<Waypoint> worldWaypoints(List<Waypoint> points, String currentWorld) {
    if (points == null || points.isEmpty()) return Collections.emptyList();
    if (currentWorld == null || currentWorld.isEmpty()) return points;
    List<Waypoint> scoped = new ArrayList<Waypoint>();
    for (int i = 0; i < points.size(); i++) {
      Waypoint p = points.get(i);
      if (p != null && p.matchesWorld(currentWorld)) scoped.add(p);
    }
    return scoped;
  }

  private void addWaypointViaCommand(
      String label, Double x, Double y, Double z, Integer explicitColor) {
    double wx, wy, wz;
    if (x != null && y != null && z != null) {
      wx = x;
      wy = y;
      wz = z;
    } else {
      double[] pos = platform.playerPosition();
      if (pos == null || pos.length < 3) {
        waypointFeedback(I18n.text(settings.language, "waypoint.invalid"));
        return;
      }
      wx = pos[0];
      wy = pos[1];
      wz = pos[2];
    }
    String dim = platform.currentDimension();
    if (dim == null || dim.isEmpty()) dim = Waypoint.DEFAULT_DIMENSION;
    String worldId = Waypoint.normalizeWorld(currentWorldId());
    String worldLabel = Waypoint.normalizeWorldLabel(currentWorldLabel());
    checkpoint();
    Waypoint point =
        settings.waypoints.add(label == null ? "" : label, wx, wy, wz, dim, worldId, worldLabel);
    if (point == null) {
      waypointFeedback(I18n.text(settings.language, "waypoint.full"));
      return;
    }
    if (explicitColor != null) {
      point.color = explicitColor.intValue() | 0xFF000000;
    } else {
      float hue = (float) ((System.nanoTime() * 1e-9 * 0.61803398875) % 1.0);
      point.color = Colors.hsv(hue < 0 ? hue + 1f : hue, .58f, .95f, 1f);
    }
    if (!point.normalize()) {
      settings.waypoints.points.remove(point);
      waypointFeedback(I18n.text(settings.language, "waypoint.invalid"));
      return;
    }
    settings.waypoints.enabled = true;
    changed();
    waypointFeedback(I18n.text(settings.language, "waypoint.cmd_added")
                         .replace("{name}", point.displayName())
                         .replace("{coords}", point.coordinates()));
  }

  private double defaultSurfaceY(double bx, double bz, double[] playerPos) {
    if (mapCache != null && mapCache.identity().isActive()) {
      short h = mapCache.heightAt((int) Math.floor(bx), (int) Math.floor(bz));
      if (h != dev.nightclient.renderer.map.MapColorShading.UNEXPLORED_HEIGHT) return h + 1.0;
    }
    if (playerPos != null && playerPos.length >= 3) return playerPos[1];
    return 64.0;
  }

  private static Integer parseHexColorToken(String token) {
    if (token == null || token.length() != 7 || token.charAt(0) != '#') return null;
    try {
      return Integer.valueOf(Integer.parseInt(token.substring(1), 16) | 0xFF000000);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private void waypointFeedback(String text) {
    platform.sendFeedback("§b[NightClient] §f" + text);
  }

  private static String joinTokens(String[] tokens, int start, int end) {
    if (start >= end || start >= tokens.length) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < Math.min(end, tokens.length); i++) {
      if (sb.length() > 0) sb.append(' ');
      sb.append(tokens[i]);
    }
    return sb.toString().trim();
  }

  private static Double parseCoordToken(String token, Double relativeBase) {
    if (token == null || token.isEmpty()) return null;
    String normalized = token.replace(',', '.');
    if (normalized.startsWith("~")) {
      if (relativeBase == null) return null;
      if (normalized.length() == 1) return relativeBase;
      try {
        double offset = Double.parseDouble(normalized.substring(1));
        return Double.isFinite(offset) ? relativeBase + offset : null;
      } catch (NumberFormatException e) {
        return null;
      }
    }
    try {
      double value = Double.parseDouble(normalized);
      return Double.isFinite(value) ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Waypoint findWaypointByQuery(List<Waypoint> points, String query) {
    String q = query.trim();
    if (q.startsWith("#")) q = q.substring(1);
    try {
      int idx = Integer.parseInt(q);
      if (idx >= 1 && idx <= points.size() && points.get(idx - 1) != null)
        return points.get(idx - 1);
    } catch (NumberFormatException ignored) {
    }
    String lower = query.trim().toLowerCase(java.util.Locale.ROOT);
    for (Waypoint p : points) {
      if (p == null) continue;
      if (lower.equals(p.id != null ? p.id.toLowerCase(java.util.Locale.ROOT) : "")) return p;
      if (lower.equals(p.displayName().toLowerCase(java.util.Locale.ROOT))) return p;
    }
    for (Waypoint p : points) {
      if (p != null && p.displayName().toLowerCase(java.util.Locale.ROOT).contains(lower)) return p;
    }
    return null;
  }

  public void setZoomHeld(boolean held) {
    held = held && !FeaturePolicy.isBlocked("zoom") && settings.zoom.enabled && worldActive;
    if (held && !zoomHeld) zoomTarget = settings.zoom.factor;
    if (!held) zoomTarget = 1;
    zoomHeld = held;
  }

  public boolean adjustZoom(double wheel) {
    if (FeaturePolicy.isBlocked("zoom") || !zoomHeld || !settings.zoom.enabled || !settings.zoom.scroll || !Double.isFinite(wheel))
      return false;
    zoomTarget = (float) Math.max(1.25, Math.min(20, zoomTarget * Math.pow(1.18, wheel)));
    return true;
  }

  public float zoomFactor() {
    double now = nowSeconds(),
           delta = zoomUpdatedAt == 0 ? 0 : Math.max(0, Math.min(.1, now - zoomUpdatedAt));
    zoomUpdatedAt = now;
    float target = !FeaturePolicy.isBlocked("zoom") && zoomHeld && settings.zoom.enabled && worldActive ? zoomTarget : 1;
    if (!settings.zoom.smooth)
      zoomCurrent = target;
    else
      zoomCurrent += (target - zoomCurrent) * (1 - (float) Math.exp(-delta * 18));
    if (Math.abs(zoomCurrent - target) < .001f) zoomCurrent = target;
    return Math.max(1, zoomCurrent);
  }

  public float zoomSensitivity() {
    return 1 / zoomFactor();
  }

  public void effectiveFov(float value) {
    if (Float.isFinite(value) && value > 0 && value < 180) effectiveFov = value;
  }

  public float effectiveFov() {
    return effectiveFov;
  }

  public String maskNickname(String text) {
    if (FeaturePolicy.isBlocked("protect")) return text;
    return DisplayProtection.maskNickname(text, ownNickname, settings.protect);
  }

  public String maskPasswordInput(String text) {
    if (FeaturePolicy.isBlocked("protect") || text == null) return text;
    int start = 0;
    while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
    String normalized = text;
    if (commandFixEnabled() && start < text.length() && text.charAt(start) == '/') {
      String tail = text.substring(start);
      String fixed = CommandLayoutFix.fix(tail);
      if (fixed != null && !fixed.equals(tail)) {
        normalized = text.substring(0, start) + fixed;
      }
    }
    String masked = DisplayProtection.maskPasswordInput(normalized, settings.protect);
    if (normalized == text) return masked;
    if (masked == null || masked.equals(normalized)) return text;
    int end = start;
    while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
    int maskedEnd = start;
    while (maskedEnd < masked.length() && !Character.isWhitespace(masked.charAt(maskedEnd))) {
      maskedEnd++;
    }
    return text.substring(0, end) + masked.substring(maskedEnd);
  }

  @Override
  public String gameVersion() {
    if (!hudFrameRendering) return platform.gameVersion();
    if (!hudGameVersionRead) {
      hudGameVersionRead = true;
      hudGameVersion = platform.gameVersion();
    }
    return hudGameVersion;
  }

  @Override
  public String menuKeyLabel() {
    String label = platform.menuKeyLabel();
    return label == null || label.isEmpty() ? I18n.text(settings.language, "binding.unbound")
                                            : label;
  }

  public String saveStatusKey() {
    return store.status();
  }

  @Override
  public String saveStatus() {
    return I18n.text(settings.language, "status." + saveStatusKey());
  }

  @Override
  public void close() {
    flushPendingConfigSave(true);
    if (chatHud.editing())
      menu.leaveEditor();
    else
      platform.closeMenu();
  }

  public DamageIndicators damageIndicators() {
    return damageIndicators;
  }

  @Override
  public TargetView localPlayer() {
    if (hudFrameRendering && hudLocalPlayerRead) return hudLocalPlayer;
    TargetView player = platform.localPlayer(0);
    if (player != null) {
      String name = DisplayProtection.maskNickname(player.name, player.name, settings.protect);
      if (!java.util.Objects.equals(name, player.name))
        player = new TargetView(player.entityId, player.uuid, name, player.health, player.maxHealth,
            player.absorption, player.distance, player.player, player.headTexture,
            player.equipment);
    }
    if (hudFrameRendering) {
      hudLocalPlayerRead = true;
      hudLocalPlayer = player;
    }
    return player;
  }

  @Override
  public dev.nightclient.api.PlayerSkin playerPreview() {
    return cosmetics.composePreview(platform.playerPreview());
  }

  @Override
  public dev.nightclient.api.PlayerSkin capePreview(String capeId) {
    return cosmetics.capeThumbnail(capeId);
  }

  @Override
  public String capePreviewKey(String capeId) {
    return cosmetics.capeThumbnailKey(capeId);
  }

  @Override
  public void selectOfficialCape(String id) {
    cosmetics.selectOfficialCape(id);
  }

  @Override
  public void resetCapeToAccount() {
    cosmetics.resetCapeToAccount();
  }

  @Override
  public void disableCape() {
    cosmetics.disableCape();
  }

  @Override
  public void applyCustomCapePath(String path) {
    cosmetics.applyCustomCapePath(path);
  }

  @Override
  public void browseCustomCape() {
    cosmetics.browseCustomCape(I18n.text(settings.language, "cosmetics.custom_cape_title"));
  }

  @Override
  public void applySkinNickname(String nickname) {
    cosmetics.applySkinNickname(nickname);
  }

  @Override
  public void applyCustomSkinPath(String path) {
    cosmetics.applyCustomSkinPath(path);
  }

  @Override
  public void browseCustomSkin() {
    cosmetics.browseCustomSkin(I18n.text(settings.language, "cosmetics.custom_skin_title"));
  }

  @Override
  public void resetSkinToAccount() {
    cosmetics.resetSkinToAccount();
  }

  @Override
  public void setCosmeticsSkinModel(String model) {
    cosmetics.setSkinModel(model);
  }

  @Override
  public void selectBuiltinWeapon(String id) {
    cosmetics.selectBuiltinWeapon(id);
  }

  @Override
  public void selectWeaponMaterial(String id) {
    cosmetics.selectWeaponMaterial(id);
  }

  @Override
  public void resetWeaponCosmetic() {
    cosmetics.resetWeapon();
  }

  @Override
  public void applyCustomWeaponPath(String path) {
    cosmetics.applyCustomWeaponPath(path);
  }

  @Override
  public void browseCustomWeapon() {
    cosmetics.browseCustomWeapon(I18n.text(settings.language, "cosmetics.custom_weapon_title"));
  }

  @Override
  public void setCosmeticsWeaponTarget(String target) {
    cosmetics.setWeaponTarget(target);
  }

  @Override
  public void setCosmeticsWeaponColor(int argb) {
    cosmetics.setWeaponColor(argb);
  }

  @Override
  public void selectBuiltinArmor(String id) {
    cosmetics.selectBuiltinArmor(id);
  }

  @Override
  public void resetArmorCosmetic() {
    cosmetics.resetArmor();
  }

  @Override
  public void applyCustomArmorPath(String path) {
    cosmetics.applyCustomArmorPath(path);
  }

  @Override
  public void browseCustomArmor() {
    cosmetics.browseCustomArmor(I18n.text(settings.language, "cosmetics.custom_armor_title"));
  }

  @Override
  public void setCosmeticsArmorTrigger(String trigger) {
    cosmetics.setArmorTrigger(trigger);
  }

  @Override
  public void setCosmeticsArmorColor(int argb) {
    cosmetics.setArmorColor(argb);
  }

  @Override
  public boolean cosmeticsLoading() {
    return cosmetics.isLoading();
  }

  @Override
  public String cosmeticsErrorKey() {
    return cosmetics.errorKey();
  }

  @Override
  public String cosmeticsSkinSummary() {
    return cosmetics.skinStatusSummary(settings.language);
  }

  @Override
  public String cosmeticsCapeSummary() {
    return cosmetics.capeStatusSummary(settings.language);
  }

  @Override
  public String cosmeticsWeaponSummary() {
    return cosmetics.weaponStatusSummary(settings.language);
  }

  @Override
  public String cosmeticsArmorSummary() {
    return cosmetics.armorStatusSummary(settings.language);
  }

  public AccountManager accountManager() {
    return accounts;
  }

  public void renderScreenAccountOverlay(
      Canvas canvas, int width, int height, float mouseX, float mouseY, boolean isTitleScreen) {
    renderScreenAccountOverlay(
        canvas, width, height, mouseX, mouseY, isTitleScreen, Collections.<int[]>emptyList(), false);
  }

  public void renderScreenAccountOverlay(
      Canvas canvas, int width, int height, float mouseX, float mouseY, boolean isTitleScreen,
      List<int[]> obstacles, boolean iasLoaded) {
    String nick = accounts.activeNickname();
    boolean launcher = accounts.isLauncherActive();
    accountOverlay.render(
        canvas, width, height, mouseX, mouseY, settings, nick, launcher, accounts.faceFor(nick),
        isTitleScreen, obstacles, iasLoaded);
  }

  public void hideScreenAccountOverlay() {
    accountOverlay.hide();
  }

  public boolean hitScreenAccountOverlay(float mouseX, float mouseY) {
    return accountOverlay.hitTest(mouseX, mouseY);
  }

  @Override
  public boolean openVanillaAccountScreen() {
    return platform.openVanillaAccountScreen();
  }

  @Override
  public List<dev.nightclient.config.SavedAccount> savedAccounts() {
    return accounts.accounts();
  }

  @Override
  public String activeAccountName() {
    return accounts.activeNickname();
  }

  @Override
  public String launcherAccountName() {
    return accounts.launcherNickname();
  }

  @Override
  public boolean isLauncherAccountActive() {
    return accounts.isLauncherActive();
  }

  @Override
  public boolean canReconnectServer() {
    return platform.canReconnectServer();
  }

  @Override
  public String currentServerLabel() {
    String label = platform.currentServerLabel();
    return label == null ? "" : label;
  }

  @Override
  public String accountErrorKey() {
    return accounts.errorKey();
  }

  @Override
  public void clearAccountError() {
    accounts.clearError();
  }

  @Override
  public String switchSavedAccount(String id) {
    return accounts.switchSaved(id);
  }

  @Override
  public String quickSwitchAccount(String rawName) {
    return accounts.quickSwitch(rawName);
  }

  @Override
  public boolean restoreLauncherAccount() {
    return accounts.restoreLauncher();
  }

  @Override
  public String saveAccountDraft(String id, String name, String note, String skinName) {
    return accounts.saveDraft(id, name, note, skinName);
  }

  @Override
  public boolean toggleAccountPin(String id) {
    return accounts.togglePin(id);
  }

  @Override
  public boolean deleteSavedAccount(String id) {
    return accounts.delete(id);
  }

  @Override
  public String generateRandomAccountName() {
    return accounts.generateRandomNickname();
  }

  @Override
  public String reconnectCurrentServer() {
    return accounts.reconnect();
  }

  @Override
  public int[] accountFace(String nickname) {
    return accounts.faceFor(nickname);
  }

  @Override
  public dev.nightclient.api.PlayerSkin accountPreviewSkin(String nickname) {
    return accounts.previewFor(nickname);
  }

  @Override
  public java.util.Map<String, Object> networkInfo() {
    if (!hudFrameRendering) return platform.networkInfo();
    if (!hudNetworkInfoRead) {
      hudNetworkInfoRead = true;
      hudNetworkInfo = platform.networkInfo();
    }
    return hudNetworkInfo;
  }

  @Override
  public int ping() {
    if (!hudFrameRendering) return platform.ping();
    if (!hudPingRead) {
      hudPingRead = true;
      hudPing = platform.ping();
    }
    return hudPing;
  }

  @Override
  public int fps() {
    if (!hudFrameRendering) return platform.fps();
    if (!hudFpsRead) {
      hudFpsRead = true;
      hudFps = platform.fps();
    }
    return hudFps;
  }

  @Override
  public java.util.Map<String, Object> keystrokes() {
    if (!hudFrameRendering) return platform.keystrokes();
    if (!hudKeystrokesRead) {
      hudKeystrokesRead = true;
      hudKeystrokes = platform.keystrokes();
    }
    return hudKeystrokes;
  }

  @Override
  public java.util.Map<String, Object> coordinates() {
    if (!hudFrameRendering) return platform.coordinates();
    if (!hudCoordinatesRead) {
      hudCoordinatesRead = true;
      hudCoordinates = platform.coordinates();
    }
    return hudCoordinates;
  }

  @Override
  public java.util.Map<String, Object> scoreboard() {
    if (!hudFrameRendering)
      return FormattedNicknameProtection.scoreboard(
          platform.scoreboard(), ownNickname, settings.protect, settings.theme.text);
    if (!hudScoreboardRead) {
      hudScoreboardRead = true;
      hudScoreboard = FormattedNicknameProtection.scoreboard(
          platform.scoreboard(), ownNickname, settings.protect, settings.theme.text);
    }
    return hudScoreboard;
  }

  public HwHelperManager hwhelper() {
    return hwhelper;
  }

  @Override
  public java.util.List<java.util.Map<String, Object>> hwEvents() {
    List<HwPvpAndEventsLogic.EventItem> list = hwhelper.filteredEvents();
    if (list == null || list.isEmpty()) return Collections.emptyList();
    String lang = I18n.language(settings.language);
    long nowMs = System.currentTimeMillis();
    List<java.util.Map<String, Object>> out =
        new ArrayList<java.util.Map<String, Object>>(list.size());
    for (int i = 0; i < list.size(); i++) {
      HwPvpAndEventsLogic.EventItem ev = list.get(i);
      java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
      String srv = ev.localizedServerName(lang);
      String elapsed = ev.elapsedText(nowMs, lang);
      row.put("id", ev.instanceId);
      row.put("title", ev.localizedTitle(lang));
      row.put("serverName", srv);
      row.put("rarityBadge", ev.rarityBadge(lang));
      row.put("rarityColor", Colors.hex(ev.rarityColor));
      row.put("elapsed", elapsed);
      String coords = ev.coordsHint == null ? "" : ev.coordsHint;
      row.put("coords", coords);
      row.put("hasCoords", !coords.isEmpty());
      String meta = srv + " · " + elapsed + (coords.isEmpty() ? "" : (" · " + coords));
      row.put("meta", meta);
      row.put("canJoin", Boolean.valueOf(ev.canAutoJoin()));
      out.add(row);
    }
    return out;
  }

  @Override
  public boolean hwEventsLoading() {
    return hwhelper.isEventsLoading();
  }

  @Override
  public String hwEventsSummary() {
    String lang = I18n.language(settings.language);
    int shown = hwhelper.filteredEvents().size();
    int total = hwhelper.allEvents().size();
    if (hwhelper.isEventsLoading() && total == 0) {
      return I18n.text(lang, "hwEvents.summary.loading");
    }
    if (hwhelper.lastEventsFetchMs() == 0L) {
      return I18n.text(lang, "hwEvents.summary.waiting");
    }
    java.util.Map<String, Object> args = new java.util.LinkedHashMap<String, Object>(4);
    args.put("shown", Integer.valueOf(shown));
    args.put("total", Integer.valueOf(total));
    return I18n.format(lang, "hwEvents.summary.active", args);
  }

  @Override
  public void refreshHwEvents() {
    hwhelper.requestEventsRefresh();
  }

  @Override
  public boolean joinHwEvent(String instanceOrServer) {
    return hwhelper.joinEventByInstanceOrServer(instanceOrServer);
  }

  @Override
  public String crosshairBlockId() {
    return platform.crosshairBlockId();
  }

  public java.nio.file.Path soundsDirectory() {
    return platform.configDirectory().resolve("nightclient").resolve("sounds");
  }

  public boolean playCustomAudio(String configuredPath, float volume, float pitch) {
    return customAudio.playAsync(soundsDirectory(), configuredPath, volume, pitch);
  }

  @Override
  public void browseTntMp3(final boolean explode) {
    if (!audioPickerBusy.compareAndSet(false, true)) return;
    final String lang = settings.language;
    final java.nio.file.Path soundsDir = soundsDirectory();
    final String title = I18n.text(lang,
        explode ? "soundControl.mp3_explode_title" : "soundControl.mp3_fuse_title");
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          String picked = platform.chooseAudioFile(title);
          if (picked == null || picked.trim().isEmpty()) return;
          if ("__NO_PICKER__".equals(picked)) {
            pendingMainThreadTasks.add(new Runnable() {
              @Override
              public void run() {
                waypointFeedback(I18n.text(settings.language, "soundControl.mp3_no_picker"));
              }
            });
            return;
          }
          final String saved = customAudio.importAudioFile(
              soundsDir, picked, explode ? "tnt_explode" : "tnt_fuse");
          pendingMainThreadTasks.add(new Runnable() {
            @Override
            public void run() {
              if (settings.soundControl == null) {
                settings.soundControl = new Settings.SoundControl();
              }
              checkpoint();
              settings.soundControl.enabled = true;
              settings.soundControl.customTnt = true;
              if (explode) {
                settings.soundControl.tntExplodeMp3 = saved;
                settings.soundControl.tntExplodePreset = "mp3";
              } else {
                settings.soundControl.tntFuseMp3 = saved;
                settings.soundControl.tntFusePreset = "mp3";
              }
              settings.validate();
              changed();
              waypointFeedback(I18n.text(settings.language, "soundControl.mp3_loaded") + ": " + saved);
              playCustomAudio(saved, settings.soundControl.tntVolume, settings.soundControl.tntPitch);
            }
          });
        } catch (Throwable err) {
          pendingMainThreadTasks.add(new Runnable() {
            @Override
            public void run() {
              waypointFeedback(I18n.text(settings.language, "soundControl.mp3_error"));
            }
          });
        } finally {
          audioPickerBusy.set(false);
        }
      }
    }, "nightclient-audio-picker");
    t.setDaemon(true);
    t.start();
  }

  @Override
  public void clearTntMp3(boolean explode) {
    if (settings.soundControl == null) return;
    checkpoint();
    if (explode) {
      settings.soundControl.tntExplodeMp3 = "";
      if ("mp3".equals(settings.soundControl.tntExplodePreset)) {
        settings.soundControl.tntExplodePreset = "vanilla";
      }
    } else {
      settings.soundControl.tntFuseMp3 = "";
      if ("mp3".equals(settings.soundControl.tntFusePreset)) {
        settings.soundControl.tntFusePreset = "vanilla";
      }
    }
    settings.validate();
    changed();
  }

  @Override
  public void previewClientSound(String kind) {
    if ("tnt".equals(kind) && settings.soundControl != null) {
      Settings.SoundControl sc = settings.soundControl;
      if ("mp3".equals(sc.tntExplodePreset) && sc.tntExplodeMp3 != null
          && !sc.tntExplodeMp3.trim().isEmpty()) {
        if (playCustomAudio(sc.tntExplodeMp3, sc.tntVolume, sc.tntPitch)) return;
      }
      if ("mp3".equals(sc.tntFusePreset) && sc.tntFuseMp3 != null
          && !sc.tntFuseMp3.trim().isEmpty()) {
        if (playCustomAudio(sc.tntFuseMp3, sc.tntVolume, sc.tntPitch)) return;
      }
      String preset = !"vanilla".equals(sc.tntExplodePreset) && !"mute".equals(sc.tntExplodePreset)
          && !"mp3".equals(sc.tntExplodePreset)
          ? sc.tntExplodePreset
          : ("vanilla".equals(sc.tntFusePreset) || "mp3".equals(sc.tntFusePreset)
              ? "note_pling"
              : sc.tntFusePreset);
      String id = SoundControlLogic.resolvePresetSoundId(preset, sc.customTntSoundId);
      platform.playClientSound(id, sc.tntVolume, sc.tntPitch);
    } else if ("mention".equals(kind) && settings.soundControl != null) {
      String id = SoundControlLogic.resolvePresetSoundId(
          settings.soundControl.mentionPreset, "");
      platform.playClientSound(
          id, settings.soundControl.mentionVolume, settings.soundControl.mentionPitch);
    } else if ("effect".equals(kind) && settings.effectAlert != null) {
      String id = SoundControlLogic.resolvePresetSoundId(
          settings.effectAlert.soundPreset, "");
      platform.playClientSound(
          id, settings.effectAlert.soundVolume, settings.effectAlert.soundPitch);
    }
  }

  private static final class WaypointCandidate {
    final Waypoint point;
    final double dist;
    final float screenX, screenY;
    final float alpha;

    WaypointCandidate(Waypoint point, double dist, float screenX, float screenY, float alpha) {
      this.point = point;
      this.dist = dist;
      this.screenX = screenX;
      this.screenY = screenY;
      this.alpha = alpha;
    }
  }

  public void renderWaypoints(Canvas canvas, int width, int height) {
    if (canvas == null || settings == null || settings.waypoints == null
        || !settings.waypoints.enabled)
      return;
    List<Waypoint> points = settings.waypoints.points;
    if (points == null || points.isEmpty()) return;

    WorldRibbon.View view = hudCameraView(width, height);
    if (view == null) return;

    String currentDim = platform.currentDimension();
    String currentWorld = Waypoint.normalizeWorld(currentWorldId());
    double[] out = projScratch;
    float cx = width * 0.5f;
    float cy = height * 0.5f;
    final double distanceScale = 512.0;

    List<WaypointCandidate> candidates = new ArrayList<WaypointCandidate>();
    for (int i = 0; i < points.size(); i++) {
      Waypoint wp = points.get(i);
      if (wp == null || !wp.visible) continue;
      if (wp.death && !settings.waypoints.deathPoints) continue;
      if (!wp.matchesWorld(currentWorld)) continue;
      if (wp.dimension != null && !wp.dimension.isEmpty() && currentDim != null
          && !currentDim.isEmpty()) {
        String a =
            wp.dimension.startsWith("minecraft:") ? wp.dimension.substring(10) : wp.dimension;
        String b = currentDim.startsWith("minecraft:") ? currentDim.substring(10) : currentDim;
        if (!a.equalsIgnoreCase(b)) continue;
      }

      double dist = view.distance(wp.x, wp.y, wp.z);
      if (!Double.isFinite(dist)) continue;
      if (settings.waypoints.hideNearby && dist <= settings.waypoints.hideNearbyRadius) continue;

      view.project(wp.x, wp.y, wp.z, out);
      if (out[2] <= 0.1 || Double.isNaN(out[0]) || Double.isNaN(out[1])) continue;

      float screenX = (float) out[0];
      float screenY = (float) out[1];
      if (Float.isNaN(screenX) || Float.isNaN(screenY) || Float.isInfinite(screenX)
          || Float.isInfinite(screenY))
        continue;
      if (screenX < -200 || screenX > width + 200 || screenY < -200 || screenY > height + 200)
        continue;

      float dx = screenX - cx;
      float dy = screenY - cy;
      float centerDist = (float) Math.sqrt(dx * dx + dy * dy);
      float crosshairAlpha = 1.0f;
      if (centerDist < 75.0f) {
        float t = centerDist / 75.0f;
        crosshairAlpha = 0.55f + 0.45f * (t * t * (3.0f - 2.0f * t));
      }

      float alpha = crosshairAlpha;
      if (alpha <= 0.05f) continue;

      candidates.add(new WaypointCandidate(wp, dist, screenX, screenY, alpha));
    }

    if (candidates.isEmpty()) return;

    Collections.sort(candidates, (a, b) -> Double.compare(b.dist, a.dist));

    float badgeSize = settings.waypoints.badge;
    dev.nightclient.config.Theme theme = settings.theme;
    int defaultAccent = (theme != null && (theme.accent >>> 24) != 0) ? theme.accent : 0xff5ad1ff;
    int textBaseColor =
        (theme != null && (theme.text >>> 24) != 0) ? (theme.text & 0xffffff) : 0xf0f0f5;

    for (int i = 0; i < candidates.size(); i++) {
      WaypointCandidate c = candidates.get(i);
      float scale = badgeSize;
      if (c.dist > 30.0) {
        scale *= Math.max(0.85f, 1.0f - (float) ((c.dist - 30.0) / (distanceScale * 1.8)));
      }

      int accent = (c.point.color >>> 24) != 0 ? c.point.color : defaultAccent;
      if ((accent >>> 24) == 0) accent |= 0xff000000;
      int rgbAccent = accent & 0xffffff;

      drawDynamicSpatialCapsule(canvas, c.point, c.dist, c.screenX, c.screenY, c.alpha, scale,
          accent, textBaseColor, theme);
    }
  }

  private void drawDynamicSpatialCapsule(Canvas canvas, Waypoint wp, double dist, float screenX,
      float screenY, float alpha, float scale, int accent, int textBaseColor,
      dev.nightclient.config.Theme theme) {
    String distStr;
    if (dist >= 1000.0) {
      distStr = String.format(java.util.Locale.ROOT, "%.1fk", dist / 1000.0);
    } else {
      distStr = Math.round(dist) + "m";
    }

    String label = wp.displayName();
    float labelSize = 7.5f * scale;
    float distSize = 6.5f * scale;
    String family = theme != null && theme.fontFamily != null ? theme.fontFamily : "onest";
    boolean strong =
        theme == null || theme.fontWeight == null || !"regular".equals(theme.fontWeight);
    float tracking = theme != null ? theme.fontTracking : 0;
    float labelW = canvas.textWidthFamily(family, label, labelSize, strong, tracking);
    float distW = canvas.textWidthFamily(family, distStr, distSize, false, tracking);

    float capsuleH = 18.0f * scale;
    float capsuleR = capsuleH * 0.5f;
    float subPillH = 12.0f * scale;
    float subPillR = subPillH * 0.5f;
    float subPillW = distW + 8.0f * scale;

    float chipDiam = 11.0f * scale;
    float chipR = chipDiam * 0.5f;

    float padLeft = 4.0f * scale;
    float gap1 = 4.5f * scale;
    float gap2 = 4.5f * scale;
    float padRight = 4.0f * scale;

    float totalW = padLeft + chipDiam + gap1 + labelW + gap2 + subPillW + padRight;

    float caretH = 3.5f * scale;
    float caretW = 4.0f * scale;
    float gapToPin = 3.0f * scale;

    float capsuleBottom = screenY - gapToPin - caretH;
    float capsuleTop = capsuleBottom - capsuleH;
    float capsuleLeft = screenX - totalW * 0.5f;

    int fill =
        theme != null ? Colors.alpha(theme.surface, alpha) : Colors.alpha(0xff0a0c14, alpha * .82f);
    int borderColor = theme != null ? Colors.alpha(theme.border, alpha * .9f)
                                    : Colors.alpha(0xffffffff, alpha * .22f);
    int accentFill = Colors.alpha(accent, alpha * .20f);
    int accentStroke = Colors.alpha(accent, alpha * .48f);
    int textColor = theme != null ? Colors.alpha(theme.text, alpha * .96f)
                                  : Colors.alpha(textBaseColor, alpha * .96f);
    int mutedColor = theme != null ? Colors.alpha(theme.muted, alpha * .9f)
                                   : Colors.alpha(textBaseColor, alpha * .72f);

    if (theme != null) {
      SurfacePainter.panel(
          canvas, theme, capsuleLeft, capsuleTop, totalW, capsuleH, fill, capsuleR, true);
    } else {
      canvas.backdrop(capsuleLeft, capsuleTop, totalW, capsuleH, capsuleR, 10.0f, 3, 0.75f * alpha);
      canvas.roundedRect(capsuleLeft, capsuleTop, totalW, capsuleH, capsuleR, fill);
      canvas.strokeRoundedRect(
          capsuleLeft, capsuleTop, totalW, capsuleH, capsuleR, 1.0f, borderColor);
    }

    canvas.fillTriangle(screenX - caretW, capsuleBottom, screenX + caretW, capsuleBottom, screenX,
        capsuleBottom + caretH, fill);
    canvas.line(
        screenX - caretW, capsuleBottom, screenX, capsuleBottom + caretH, 1.0f, borderColor);
    canvas.line(
        screenX + caretW, capsuleBottom, screenX, capsuleBottom + caretH, 1.0f, borderColor);

    int pinColor = Colors.alpha(accent, alpha * .86f);
    int pinRing = Colors.alpha(accent, alpha * .32f);
    canvas.circle(screenX, screenY, 2.0f * scale, pinColor);
    canvas.strokeRoundedRect(screenX - 4.0f * scale, screenY - 4.0f * scale, 8.0f * scale,
        8.0f * scale, 4.0f * scale, 1.0f, pinRing);

    float chipCx = capsuleLeft + padLeft + chipR;
    float chipCy = capsuleTop + capsuleH * 0.5f;
    canvas.circle(chipCx, chipCy, chipR, accentFill);
    canvas.strokeRoundedRect(
        chipCx - chipR, chipCy - chipR, chipDiam, chipDiam, chipR, 1.0f, accentStroke);
    canvas.circle(chipCx, chipCy, 2.0f * scale, Colors.alpha(accent, alpha * .92f));

    float labelX = capsuleLeft + padLeft + chipDiam + gap1;
    float labelY = capsuleTop + (capsuleH - labelSize) * 0.5f;
    canvas.textFamily(family, label, labelX, labelY, labelSize, textColor, strong, tracking);

    float subPillX = labelX + labelW + gap2;
    float subPillY = capsuleTop + (capsuleH - subPillH) * 0.5f;
    canvas.roundedRect(
        subPillX, subPillY, subPillW, subPillH, subPillR, Colors.alpha(accent, alpha * .13f));
    canvas.strokeRoundedRect(
        subPillX, subPillY, subPillW, subPillH, subPillR, 1.0f, Colors.alpha(accent, alpha * .34f));

    float distTextX = subPillX + (subPillW - distW) * 0.5f;
    float distTextY = subPillY + (subPillH - distSize) * 0.5f;
    canvas.textFamily(family, distStr, distTextX, distTextY, distSize, mutedColor, false, tracking);
  }
}
