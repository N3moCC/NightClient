package dev.nightclient.config;

import dev.nightclient.skin.DefaultSkin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Settings {
  public int schemaVersion = 1;
  public String language = "ru";
  public Theme theme = Theme.defaults();
  public Skin skin = new Skin();
  public InterfaceState interfaceState = new InterfaceState();

  public java.util.Map<String, String> moduleBindings =
      new java.util.LinkedHashMap<String, String>();
  public transient dev.nightclient.skin.SkinDocument skinDocument;
  public Menu menu = new Menu();
  public Saturation saturation = new Saturation();
  public Hud hud = new Hud();
  public Tnt tnt = new Tnt();
  public Inventory inventory = new Inventory();
  public Crosshair crosshair = new Crosshair();
  public Zoom zoom = new Zoom();
  public Protect protect = new Protect();
  public Armor armor = new Armor();
  public Potions potions = new Potions();
  public Food food = new Food();
  public Target target = new Target();
  public Tweaksy tweaksy = new Tweaksy();
  public Optimization optimization = new Optimization();
  public ItemScroller itemScroller = new ItemScroller();
  public HitColor hitColor = new HitColor();
  public Hitbox hitbox = new Hitbox();
  public Waypoints waypoints = new Waypoints();
  public LowFire lowFire = new LowFire();
  public NoRender noRender = new NoRender();
  public ItemPhysics itemPhysics = new ItemPhysics();
  public Viewmodel viewmodel = new Viewmodel();
  public AspectRatio aspectRatio = new AspectRatio();
  public Swing swing = new Swing();
  public Freelook freelook = new Freelook();
  public DamageSplash damageSplash = new DamageSplash();
  public Scoreboard scoreboard = new Scoreboard();
  public Keystrokes keystrokes = new Keystrokes();
  public Coordinates coordinates = new Coordinates();
  public Ping ping = new Ping();
  public Fps fps = new Fps();
  public HwHelper hwhelper = new HwHelper();
  public PlayerHealth playerHealth = new PlayerHealth();
  public TotemCounter totemCounter = new TotemCounter();
  public PlayerPing playerPing = new PlayerPing();
  public SafeCtLeave safeCtLeave = new SafeCtLeave();
  public BetterChat betterChat = new BetterChat();
  public WorldTime worldTime = new WorldTime();
  public CustomSky customSky = new CustomSky();
  public BlockOverlay blockOverlay = new BlockOverlay();
  public EnchantGlint enchantGlint = new EnchantGlint();
  public ExplosionRing explosionRing = new ExplosionRing();
  public Tooltips tooltips = new Tooltips();
  public Minimap minimap = new Minimap();
  public WorldMap worldMap = new WorldMap();
  public Cosmetics cosmetics = new Cosmetics();
  public Atmosphere atmosphere = new Atmosphere();
  public WavyCapes wavyCapes = new WavyCapes();
  public AccountSwitcher accountSwitcher = new AccountSwitcher();
  public SoundControl soundControl = new SoundControl();
  public EffectAlert effectAlert = new EffectAlert();
  public TapeMouse tapeMouse = new TapeMouse();
  public MotionBlur motionBlur = new MotionBlur();
  public TabAnimation tabAnimation = new TabAnimation();

  public static final class Skin {
    public boolean enabled;
    public String file = "skin.nc";

    public String preset = "default";

    public String appliedPreset = "default";

    public int appearanceRevision = 3;

    public java.util.LinkedHashMap<String, dev.nightclient.config.Theme> appearance =
        new java.util.LinkedHashMap<String, dev.nightclient.config.Theme>();
  }

  public static final class Menu {
    public boolean vanillaBlur;
    public float vanillaBlurRadius = 5;
    public float opacity = 1;
    public float scale = 1f;

    public float hudScale = 1f;
    public float fontScale = 1;
    public float animationSpeed = 1;
    public boolean chatHudEditor = true;
    public float width = 425;
    public float height = 250;
    public float density = 0.9f;
  }

  public static final class InterfaceState {
    public boolean sidebarCollapsed;
    public LinkedHashMap<String, Boolean> expandedPanels = new LinkedHashMap<String, Boolean>();
  }

  public static final class Saturation {
    public boolean enabled = false;
    public float strength = 1.15f;
  }

  public static final class MotionBlur {
    public boolean enabled = false;
    public float strength = 50.0f;
    public String quality = "high";
    public boolean blurSky = true;
  }

  public static final class TabAnimation {
    public boolean enabled = true;
    public String style = "scale";
    public int durationMs = 200;
  }

  public static final class Hud {
    public int layoutOrder;
    public boolean enabled = true;
    public float opacity = 1;
    public float scale = 1;
    public float anchorX = 1;
    public float anchorY = 0;
    public float offsetX = -20;
    public float offsetY = 90;
    public float width = 148;
    public float spacing = 7;
    public int maxVisible = 8;
    public boolean icons = true;
    public boolean names = true;
    public boolean time = true;
    public boolean progress = false;
    public boolean background = true;
    public boolean hideWhenEmpty = true;
    public boolean hideWithGameHud = true;
    public boolean horizontal = false;
    public boolean snap = true;
    public String sort = "remaining";
    public float fontScale = 1;
    public float rowHeight = 38;
    public float iconScale = 1;
    public float progressHeight = 2;
    public boolean showHeader = true;
  }

  public static class Panel {
    public int layoutOrder;
    public boolean enabled = false;
    public float scale = 1;
    public float opacity = 1;
    public float anchorX = 0;
    public float anchorY = 1;
    public float offsetX = 20;
    public float offsetY = -20;
    public float width = 148;
    public float height = 80;
  }

  public static final class Inventory extends Panel {
    public boolean hotbar = false;
    public int rows = 3;
    public boolean counts = true;
    public boolean durability = true;

    public Inventory() {
      width = 198;
      anchorX = 1;
      anchorY = 1;
      offsetX = -20;
      offsetY = -54;
    }
  }

  public static final class Tnt extends Panel {
    public float range = 64;
    public boolean showDistance = false;

    public Tnt() {
      anchorX = 0;
      anchorY = 0;
      offsetX = 20;
      offsetY = 90;
      width = 168;
      height = 96;
    }
  }

  public static final class Crosshair {
    public boolean enabled = false;
    public String mode = "pixels";
    public String colorMode = "vanilla";
    public CrosshairPattern pattern = new CrosshairPattern();
    public float pixelScale = 1;
    public int offsetX = 0;
    public int offsetY = 0;
    public boolean mirrorX = false;
    public boolean mirrorY = false;

    public String preset = "cross";
    public float size = 7;
    public float gap = 3;
    public float thickness = 1.3f;
    public float outline = 1;
    public boolean dot = false;
    public int color = 0xffffffff;
    public boolean inheritColor = true;
    public float opacity = 1;
    public boolean thirdPerson = false;
    public boolean targetHighlight = false;
    public int targetColor = 0xffff3b5c;
    public boolean targetMobs = false;
    public boolean targetHostileOnly = false;
    public float targetRange = 0f;

    public boolean targetShapeEnabled = false;
    public String targetShape = "x";
    public CrosshairPattern targetPattern = CrosshairPattern.defaultTargetPattern();
    public float targetScale = 1.15f;
    public float targetSize = 6f;
    public float targetGap = 2.5f;
    public float targetThickness = 1.5f;
    public float targetOutline = 1f;
    public boolean targetCenterDot = true;
    public float targetRotation = 0f;
    public boolean targetAnimate = true;
    public float targetAnimSpeed = 14f;
  }

  public static final class Zoom {
    public boolean enabled = false;
    public float factor = 3;
    public boolean smooth = true;
    public boolean scroll = true;
  }

  public static final class Protect {
    public boolean enabled = false;
    public boolean hideNickname = true;
    public boolean maskPasswords = true;
    public String replacement = "NightProtect";
    public List<String> aliases = new ArrayList<String>(
        Arrays.asList("l", "login", "reg", "register", "changepassword", "cp"));
  }

  public static final class Armor extends Panel {
    public boolean horizontal = true, durability = true, percent = false, names = false;
    public String style = "visual";
    public String durabilityStyle = "auto";
    public String durabilityPosition = "below";

    public Armor() {
      anchorX = 0;
      anchorY = 1;
      offsetX = 20;
      offsetY = -56;
      width = 128;
      height = 50;
    }
  }

  public static final class Potions extends Panel {
    public boolean time = true, level = true, icons = true;
    public boolean hideWhenEmpty = true;
    public boolean horizontal = true;
    public String format = "list";

    public Potions() {
      anchorX = 0;
      anchorY = 0;
      offsetX = 20;
      offsetY = 196;
      width = 148;
      height = 160;
    }
  }

  public static final class Food {
    public boolean enabled = false;
  }

  public static final class Target extends Panel {
    public String style = "card";
    public float displaySeconds = 5;
    public boolean absorption = true, distance = true;

    public Target() {
      anchorX = .5f;
      anchorY = .5f;
      offsetX = 0;
      offsetY = 64;
      width = 115;
      height = 36;
    }
  }

  public static final class CommandFix {
    public boolean enabled = false;
  }

  public static final class ItemScroller {
    public boolean enabled = false;
    public boolean dragMatching = true;
    public boolean dragOnlyMatching = false;
    public int dragCooldownMs = 0;
    public boolean moveMatching = true;
    public boolean dropMatching = true;
    public boolean shiftPlace = false;
    public boolean wheel = false;
    public String wheelMode = "stack";
    public String wheelDirection = "both";
    public int cooldownMs = 80;
    public boolean matchNbt = true;
    public String gesture = "shift-drag";
  }

  public static final class HitColor {
    public boolean enabled = false;
    public String mode = "entity";
    public boolean armor = false;
    public int color = 0xffff4c72;
    public float opacity = .65f;
  }

  public static final class Hitbox {
    public boolean enabled = false, players = true, mobs = true, items = false, eyeLine = false;
    public int color = 0xffa99aff;
    public float lineWidth = 1.5f, range = 64;
  }

  public static final class Waypoints {
    public static final int POINT_LIMIT = 512;

    public boolean enabled = true;
    public boolean deathPoints = false;
    public boolean chatCoordinates = true;
    public boolean hideNearby = false;
    public int hideNearbyRadius = 10;
    public float badge = 1;

    public List<Waypoint> points = new ArrayList<Waypoint>();

    public Waypoint add(String label, double x, double y, double z) {
      return add(label, x, y, z, Waypoint.DEFAULT_DIMENSION, "", "");
    }

    public Waypoint add(String label, double x, double y, double z, String dimension) {
      return add(label, x, y, z, dimension, "", "");
    }

    public Waypoint add(String label, double x, double y, double z, String dimension, String world,
        String worldLabel) {
      if (points == null) points = new ArrayList<Waypoint>();
      if (points.size() >= POINT_LIMIT) return null;
      Waypoint point = new Waypoint(label, x, y, z);
      point.id = Waypoint.newId();
      point.dimension = dimension;
      point.world = Waypoint.normalizeWorld(world);
      point.worldLabel = Waypoint.normalizeWorldLabel(worldLabel);
      points.add(point);
      return point;
    }

    public Waypoint addDeath(double x, double y, double z, String dimension) {
      return addDeath(x, y, z, dimension, "", "");
    }

    public Waypoint addDeath(
        double x, double y, double z, String dimension, String world, String worldLabel) {
      if (points == null) points = new ArrayList<Waypoint>();
      String dim =
          dimension == null || dimension.isEmpty() ? Waypoint.DEFAULT_DIMENSION : dimension;
      String normalizedWorld = Waypoint.normalizeWorld(world);
      for (int index = points.size() - 1; index >= 0; index--) {
        Waypoint point = points.get(index);
        if (point != null && point.death && dim.equalsIgnoreCase(point.dimension)
            && point.matchesWorld(normalizedWorld))
          points.remove(index);
      }
      if (points.size() >= POINT_LIMIT) return null;
      Waypoint point = new Waypoint("", x, y, z);
      point.id = Waypoint.newId();
      point.dimension = dim;
      point.world = normalizedWorld;
      point.worldLabel = Waypoint.normalizeWorldLabel(worldLabel);
      point.death = true;
      point.color = 0xFFFF5A5A;
      points.add(point);
      return point;
    }

    public int removeWorld(String worldId) {
      if (points == null) return 0;
      String normalized = Waypoint.normalizeWorld(worldId);
      int removed = 0;
      for (int index = points.size() - 1; index >= 0; index--) {
        Waypoint point = points.get(index);
        if (point != null && point.matchesWorld(normalized)) {
          points.remove(index);
          removed++;
        }
      }
      return removed;
    }

    public Waypoint find(String id) {
      if (id == null || points == null) return null;
      for (Waypoint point : points)
        if (point != null && id.equals(point.id)) return point;
      return null;
    }

    public boolean remove(String id) {
      Waypoint point = find(id);
      return point != null && points.remove(point);
    }
  }

  public static final class LowFire {
    public boolean enabled = false, hidden = false;
    public float offsetY = .45f;
  }

  public static final class NoRender {
    public boolean enabled = false;
    public boolean fire = true;
    public boolean hideFire = false;
    public float fireOffset = .45f;
    public boolean hurtCam = true;
    public boolean totem = false;
    public boolean pumpkin = true;
    public boolean powderSnow = true;
    public boolean blockOverlay = true;
    public boolean portal = false;
    public boolean nausea = true;
    public boolean vignette = false;
    public boolean bossBar = false;
    public boolean waterOverlay = true;
    public boolean waterFog = false;
    public boolean lavaFog = false;
    public boolean weather = false;
  }

  public static final class ItemPhysics {
    public boolean enabled = false;
    public float rotationSpeed = 1;
    public boolean grounded = true;
  }

  public static final class FullBright {
    public boolean enabled = false;
    public float strength = 1;
  }

  public static final class MainLeftFix {
    public boolean enabled = false;
  }

  public static final class OwnNametag {
    public boolean enabled = false;
  }

  public static final class InvisibleArmorNametag {
    public boolean enabled = false;
    public boolean hitbox = false;
  }

  public static final class Tweaksy {
    public boolean enabled = true;
    public MainLeftFix mainLeftFix = new MainLeftFix();
    public CommandFix commandFix = new CommandFix();
    public CursorFix cursorFix = new CursorFix();
    public FullBright fullBright = new FullBright();
    public OwnNametag ownNametag = new OwnNametag();
    public InvisibleArmorNametag invisibleArmorNametag = new InvisibleArmorNametag();
  }

  public static final class Optimization {
    public boolean enabled = false;
    public String profile = "performance";
    public boolean autoCompatibility = true;
    public boolean undergroundCaveCulling = true;
    public boolean retainedTerrainCache = true;
    public boolean leafCulling = true;
    public boolean fastModelRendering = true;
    public boolean occlusionCulling = true;
    public boolean staticBlockEntityBatching = true;
    public boolean staticEntityBatching = true;
    public boolean hologramTextCache = true;
    public boolean emptyBlockEntitySkip = true;
    public boolean signTextCulling = true;
    public int signTextDistance = 28;
    public boolean itemClustering = false;
    public int itemClusterLimit = 3;
    public boolean nametagCulling = true;
    public int nametagDistance = 48;
    public boolean entityCulling = true;
    public int entityDistance = 96;
    public boolean blockEntityCulling = true;
    public int blockEntityDistance = 96;
    public boolean particleCulling = true;
    public int particleDistance = 48;
    public int particleLimit = 4000;
    public boolean fastParticlePhysics = true;
    public boolean hudCache = true;
    public boolean vanillaHudCache = true;
    public boolean scoreboardCache = true;
    public boolean nativeGpuInstancing = true;
    public boolean nativePboUploads = true;
    public boolean nativePacketCodec = true;
    public String enabledFlags = "";
    public String disabledFlags = "";

    public void applyProfile(String name) {
      if ("lossless".equals(name)) {
        profile = "lossless";
        undergroundCaveCulling = true;
        retainedTerrainCache = true;
        leafCulling = false;
        fastModelRendering = true;
        occlusionCulling = true;
        staticBlockEntityBatching = true;
        staticEntityBatching = true;
        hologramTextCache = true;
        emptyBlockEntitySkip = true;
        signTextCulling = false;
        itemClustering = false;
        nametagCulling = false;
        entityCulling = false;
        blockEntityCulling = false;
        particleCulling = false;
        fastParticlePhysics = false;
        hudCache = true;
        vanillaHudCache = false;
        scoreboardCache = true;
        nativeGpuInstancing = true;
        nativePboUploads = true;
        nativePacketCodec = true;
      } else if ("performance".equals(name) || "balanced".equals(name)) {
        profile = "performance";
        undergroundCaveCulling = true;
        retainedTerrainCache = true;
        leafCulling = true;
        fastModelRendering = true;
        occlusionCulling = true;
        staticBlockEntityBatching = true;
        staticEntityBatching = true;
        hologramTextCache = true;
        emptyBlockEntitySkip = true;
        signTextCulling = true;
        itemClustering = false;
        nametagCulling = true;
        entityCulling = true;
        blockEntityCulling = true;
        particleCulling = true;
        fastParticlePhysics = true;
        hudCache = true;
        vanillaHudCache = true;
        scoreboardCache = true;
        nativeGpuInstancing = true;
        nativePboUploads = true;
        nativePacketCodec = true;
      } else if ("competitive".equals(name)) {
        profile = "competitive";
        undergroundCaveCulling = true;
        retainedTerrainCache = true;
        leafCulling = true;
        fastModelRendering = true;
        occlusionCulling = true;
        staticBlockEntityBatching = true;
        staticEntityBatching = true;
        hologramTextCache = true;
        emptyBlockEntitySkip = true;
        signTextCulling = true;
        itemClustering = true;
        nametagCulling = true;
        entityCulling = true;
        blockEntityCulling = true;
        particleCulling = true;
        fastParticlePhysics = true;
        hudCache = true;
        vanillaHudCache = true;
        scoreboardCache = true;
        nativeGpuInstancing = true;
        nativePboUploads = true;
        nativePacketCodec = true;
      } else {
        profile = "custom";
      }
    }
  }

  public static final class HandTransform {
    public boolean enabled = true;
    public float x = 0, y = 0, z = 0, rotateX = 0, rotateY = 0, rotateZ = 0, scaleX = 1, scaleY = 1,
                 scaleZ = 1;
  }

  public static final class Viewmodel {
    public boolean enabled = false;
    public HandTransform main = new HandTransform(), off = new HandTransform();
    public boolean snap = false;
    public float translationSnap = .05f, rotationSnap = 15, scaleSnap = .1f;
  }

  public static final class AspectRatio {
    public boolean enabled = false;
    public String preset = "16_9";
    public float ratio = 1.7777778f;

    public void preset(String name) {
      if ("16_9".equals(name) || "16:9".equals(name)) {
        preset = "16_9";
        ratio = 16f / 9f;
      } else if ("4_3".equals(name) || "4:3".equals(name)) {
        preset = "4_3";
        ratio = 4f / 3f;
      } else if ("16_10".equals(name) || "16:10".equals(name)) {
        preset = "16_10";
        ratio = 16f / 10f;
      } else if ("21_9".equals(name) || "21:9".equals(name)) {
        preset = "21_9";
        ratio = 21f / 9f;
      } else if ("5_4".equals(name) || "5:4".equals(name)) {
        preset = "5_4";
        ratio = 5f / 4f;
      } else if ("custom".equals(name)) {
        preset = "custom";
      }
    }
  }

  public static final class DamageSplash {
    public boolean enabled = false;
    public boolean crits = true;
    public boolean icons = true;
    public float scale = 1.0f;
    public float duration = 1.2f;
    public String colorMode = "dynamic";
    public int color = 0xffff4c4c;
    public int critColor = 0xffffd700;
  }

  public static final class Scoreboard extends Panel {
    public boolean hideScores = true;
    public boolean cleanAds = true;
    public boolean background = true;
    public String font = "minecraft";

    public Scoreboard() {
      anchorX = 1;
      anchorY = .5f;
      offsetX = -10;
      offsetY = 0;
      width = 140;
      height = 120;
    }
  }

  public static final class Keystrokes extends Panel {
    public boolean mouseButtons = true;
    public boolean space = true;
    public boolean cps = true;
    public boolean background = true;

    public Keystrokes() {
      anchorX = 0;
      anchorY = 0;
      offsetX = 20;
      offsetY = 20;
      width = 80;
      height = 70;
    }
  }

  public static final class Coordinates extends Panel {
    public boolean background = true;

    public Coordinates() {
      anchorX = 0;
      anchorY = 1;
      offsetX = 10;
      offsetY = -10;
      width = 160;
      height = 24;
    }
  }

  public static final class Fps extends Panel {
    public boolean colored = true;
    public boolean background = true;

    public Fps() {
      enabled = false;
      width = 84;
      height = 20;
      anchorX = 0;
      anchorY = 0;
      offsetX = 12;
      offsetY = 38;
      opacity = 1;
    }
  }

  public static final class Ping extends Panel {
    public boolean colored = true;
    public boolean serverIp = true;
    public boolean background = true;

    public Ping() {
      anchorX = 1;
      anchorY = 0;
      offsetX = -10;
      offsetY = 10;
      width = 60;
      height = 20;
    }
  }

  public static final class AucPrice {
    public boolean enabled = true;
  }

  public static final class AnarchySwap {
    public boolean enabled = true;
    public boolean catalogScanned = false;
    public Map<String, String> catalog = new LinkedHashMap<String, String>();
  }

  public static final class BetterSell {
    public boolean enabled = true;
  }

  public static final class HwTimers {
    public boolean enabled = true;
    public boolean traps = true;
    public boolean explosiveTraps = true;
    public boolean stuns = true;
    public boolean snowball = true;
    public boolean farewell = true;
    public boolean actionbarTimer = true;
    public float trapSeconds = 15.0f;
    public float stunSeconds = 15.0f;
    public float snowballSeconds = 10.0f;
    public float scale = 1.0f;
  }

  public static final class HwEsp {
    public boolean enabled = true;
    public boolean stun = true;
    public boolean explosiveTrap = true;
    public boolean trap = true;
    public boolean jakeLuminaire = true;
    public boolean drillHelper = true;
    public int idleColor = 0xff38bdf8;
    public int enemyColor = 0xff22c55e;
    public int drillColor = 0xff38bdf8;
    public float fillOpacity = 0.16f;
    public float lineWidth = 2.0f;
  }

  public static final class HwEvents {
    public boolean enabled = false;
    public boolean showHud = false;
    public String modeFilter = "all";
    public String rarityFilter = "all";
    public boolean soundAlert = true;
    public int pollIntervalSeconds = 15;
  }

  public static final class HwHelper {
    public boolean enabled = true;
    public AucPrice aucprice = new AucPrice();
    public AnarchySwap answap = new AnarchySwap();
    public BetterSell betterSell = new BetterSell();
    public HwEsp esp = new HwEsp();
    public boolean compassWaypoint = true;
    public HwTimers timers = new HwTimers();
    public HwEvents events = new HwEvents();
  }

  public static final class Swing {
    public boolean enabled = false;
    public String style = "vanilla";
    public float speed = 1, amplitude = 1, rotateX = 0, rotateY = 0, rotateZ = 0;
    public boolean equip = true;
  }

  public static final class PlayerHealth {
    public boolean enabled = false;
    public String style = "pill";
    public float height = 0.45f;
    public float scale = 1.0f;
    public boolean showInvisible = false;
    public boolean showAbsorption = true;
    public boolean showEnchanted = true;
    public int maxDistance = 64;
  }

  public static final class TotemCounter {
    public boolean enabled = false;
    public float height = 0.0f;
    public float scale = 1.0f;
    public boolean showZero = false;
    public boolean showInvisible = false;
    public boolean resetOnDeath = true;
    public int maxDistance = 64;
  }

  public static final class PlayerPing {
    public boolean enabled = false;
    public boolean inWorld = true;
    public boolean inTab = false;
    public boolean tabKeepBars = false;
    public String style = "badge";
    public boolean colored = true;
    public boolean showSelf = false;
    public boolean showInvisible = false;
    public float height = 0.14f;
    public float scale = 1.0f;
    public int maxDistance = 64;
  }

  public static final class SafeCtLeave {
    public boolean enabled = false;
    public boolean blockDisconnect = true;
    public boolean confirmDisconnect = true;
    public boolean blockWindowClose = true;
    public boolean blockCommands = true;
    public boolean shiftBypass = true;
    public boolean detectPvpHits = true;
    public boolean dangerGuard = true;
    public float dangerRadius = 6.5f;
    public int fallbackSeconds = 15;
    public boolean showBanner = true;
  }

  public static final class CursorFix {
    public boolean enabled = false;
  }

  public static final class SoundControl {
    public boolean enabled = false;
    public boolean muteBlocks = true;
    public List<String> mutedBlocks = new ArrayList<String>();
    public float blockVolume = 0.0f;
    public boolean filterStep = true;
    public boolean filterBreak = true;
    public boolean filterPlace = true;
    public boolean filterHit = true;
    public boolean customTnt = false;
    public String tntFusePreset = "note_pling";
    public String tntExplodePreset = "vanilla";
    public String tntFuseMp3 = "";
    public String tntExplodeMp3 = "";
    public String customTntSoundId = "minecraft:block.note_block.pling";
    public float tntVolume = 1.0f;
    public float tntPitch = 1.35f;
    public boolean tntCountdownTicks = true;
    public boolean chatMention = true;
    public String mentionPreset = "note_pling";
    public float mentionVolume = 1.0f;
    public float mentionPitch = 1.25f;
    public String mentionKeywords = "";
    public boolean mentionHighlight = true;
    public float explosionVolume = 1.0f;
    public float totemVolume = 1.0f;
    public float weatherVolume = 1.0f;
    public float portalBeaconVolume = 1.0f;

    public static final List<String> POPULAR_BLOCKS = java.util.Collections.unmodifiableList(
        java.util.Arrays.asList("note_block", "piston", "chest", "ender_chest", "shulker_box",
            "obsidian", "glass", "anvil", "amethyst_block", "bell", "sculk_sensor",
            "nether_portal"));
    private static final Map<String, String> BLOCK_ALIASES = createBlockAliases();

    private static Map<String, String> createBlockAliases() {
      Map<String, String> m = new LinkedHashMap<String, String>();
      m.put("нотный блок", "note_block");
      m.put("нотблок", "note_block");
      m.put("поршень", "piston");
      m.put("липкий поршень", "sticky_piston");
      m.put("сундук", "chest");
      m.put("эндер сундук", "ender_chest");
      m.put("эндер-сундук", "ender_chest");
      m.put("шалкер", "shulker_box");
      m.put("обсидиан", "obsidian");
      m.put("плачущий обсидиан", "crying_obsidian");
      m.put("стекло", "glass");
      m.put("наковальня", "anvil");
      m.put("аметист", "amethyst_block");
      m.put("колокол", "bell");
      m.put("скалк", "sculk_sensor");
      m.put("портал", "nether_portal");
      m.put("маяк", "beacon");
      m.put("раздатчик", "dispenser");
      m.put("выбрасыватель", "dropper");
      m.put("воронка", "hopper");
      m.put("дверь", "oak_door");
      m.put("люк", "oak_trapdoor");
      m.put("калитка", "oak_fence_gate");
      m.put("вода", "water");
      m.put("лава", "lava");
      m.put("огонь", "fire");
      m.put("костер", "campfire");
      m.put("костёр", "campfire");
      m.put("камень", "stone");
      m.put("булыжник", "cobblestone");
      m.put("глубинный сланец", "deepslate");
      m.put("трава", "grass_block");
      m.put("дерн", "grass_block");
      m.put("земля", "dirt");
      m.put("песок", "sand");
      m.put("гравий", "gravel");
      m.put("снег", "snow_block");
      m.put("шерсть", "white_wool");
      m.put("доски", "oak_planks");
      m.put("паутина", "cobweb");
      return m;
    }

    public static String normalizeBlockQuery(String raw) {
      if (raw == null) return "";
      String clean = raw.trim().toLowerCase(java.util.Locale.ROOT);
      if (clean.isEmpty()) return "";
      if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length()).trim();
      String alias = BLOCK_ALIASES.get(clean);
      if (alias != null) return alias;
      String underscored = clean.replace(' ', '_').replace('-', '_');
      alias = BLOCK_ALIASES.get(underscored.replace('_', ' '));
      if (alias != null) return alias;
      StringBuilder sb = new StringBuilder(underscored.length());
      for (int i = 0; i < underscored.length(); i++) {
        char c = underscored.charAt(i);
        if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') sb.append(c);
      }
      return sb.toString();
    }

    public static String displayBlockName(String blockId, boolean ru) {
      return displayBlockName(blockId, ru ? "ru" : "en");
    }

    public static String displayBlockName(String blockId, String language) {
      if (blockId == null || blockId.isEmpty()) return "";
      String id = blockId.startsWith("minecraft:") ? blockId.substring(10) : blockId;
      String key = "soundControl.block." + id;
      String localized = dev.nightclient.i18n.I18n.text(language, key);
      if (!key.equals(localized)) return localized;
      String[] parts = id.split("_");
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parts.length; i++) {
        String p = parts[i];
        if (p.isEmpty()) continue;
        if (sb.length() > 0) sb.append(' ');
        sb.append(Character.toUpperCase(p.charAt(0)));
        if (p.length() > 1) sb.append(p.substring(1));
      }
      return sb.toString();
    }
  }

  public static final class EffectAlert extends Panel {
    public String mode = "both";
    public int warnSeconds = 5;
    public boolean notifyOnExpire = true;
    public String soundPreset = "note_pling";
    public float soundVolume = 1.0f;
    public float soundPitch = 1.3f;
    public boolean strength = true;
    public boolean speed = true;
    public boolean fireResistance = true;
    public boolean invisibility = true;
    public boolean regeneration = true;
    public boolean resistance = true;
    public boolean absorption = false;
    public boolean haste = false;
    public boolean slowFalling = false;
    public boolean waterBreathing = false;
    public boolean nightVision = false;

    public EffectAlert() {
      enabled = false;
      anchorX = 0.5f;
      anchorY = 0.24f;
      offsetX = 0f;
      offsetY = 0f;
      width = 160f;
      height = 30f;
      scale = 1.0f;
    }
  }

  public static final class TapeMouse {
    public boolean enabled = false;
    public String action = "attack";
    public String customKey = "key.mouse.left";
    public int delayTicks = 12;
    public int jitterTicks = 0;
    public boolean workInBackground = true;
    public boolean pauseInScreens = true;
    public boolean stopOnDamage = false;
    public boolean showHudIndicator = true;
  }

  public static final class BetterChat extends Panel {
    public String backgroundStyle = "rounded";
    public float backgroundOpacity = 0.45f;
    public boolean blur = true;
    public boolean animation = true;
    public float animationSpeed = 1.0f;
    public boolean stackDuplicates = true;
    public boolean timestamps = false;
    public boolean timestampSeconds = false;
    public boolean keepHistory = true;
    public int historyLimit = 1000;
    public boolean copyOnClick = true;
    public boolean keepDraft = true;
    public boolean hideSignatureBar = true;

    public BetterChat() {
      anchorX = 0f;
      anchorY = 1f;
      offsetX = 4f;
      offsetY = -40f;
      width = 240f;
      height = 96f;
      scale = 1.0f;
    }
  }

  public static final class WorldTime {
    public boolean enabled = false;
    public String time = "day";
    public boolean noRain = true;
  }

  public static final class CustomSky {
    public boolean enabled = false;
    public String style = "galaxy";
    public String timeMode = "server";
    public int customTime = 18000;
    public boolean useCustomColors = false;
    public int topColor = 0xff090d24;
    public int horizonColor = 0xff2b1e56;
    public int accentColor = 0xff8b5cf6;
    public float brightness = 100f;
    public float rotationSpeed = 40f;
    public float stars = 75f;
    public float clouds = 55f;
    public boolean celestial = true;
    public float celestialSize = 100f;
    public float celestialHeight = 32f;
    public float horizonGlow = 65f;
    public boolean endAndNether = false;
  }

  public static final class BlockOverlay {
    public boolean enabled = false;
    public String mode = "both";
    public boolean useThemeAccent = true;
    public int color = 0xff7c9cff;
    public float outlineOpacity = 0.85f;
    public float fillOpacity = 0.14f;
    public float lineWidth = 1.6f;
    public boolean smooth = true;
    public float smoothSpeed = 14.0f;
    public boolean exactShape = true;
    public boolean pulse = false;
  }

  public static final class EnchantGlint {
    public boolean enabled = false;
    public String colorMode = "custom";
    public String preset = "amethyst";
    public int color = 0xff7c5cff;
    public float intensity = 1.0f;
    public float speed = 1.0f;
  }

  public static final class ExplosionRing {
    public boolean enabled = false;
    public boolean showTnt = true;
    public boolean showCrystals = true;
    public boolean showAnchors = true;
    public boolean showFill = true;
    public float fillOpacity = 0.12f;
    public boolean showFuseRing = true;
    public boolean showLethalRing = true;
    public boolean showDamageBadge = true;
    public boolean autoLearnRadius = true;
    public float customTntRadius = 0.0f;
    public float damageMultiplier = 1.0f;
    public float lineWidth = 2.0f;
    public float maxDistance = 48.0f;
  }

  public static final class Tooltips {
    public boolean enabled = false;
    public String style = "hw";
    public boolean shulkerPeek = true;
    public String peekMode = "compact";
    public String peekTrigger = "always";
    public boolean colorByShulker = true;
    public boolean hideVanillaList = true;
    public boolean hwTooltips = true;
    public boolean hwAuctionPrice = true;
    public boolean hwRarityBorder = true;
    public boolean hwGoldenSpawners = true;
    public boolean hwSpawnerHighlight = true;
    public boolean hwSpecialCompass = true;
    public boolean hwCoinExchange = true;
    public boolean hwKnockbackHighlight = true;
    public boolean showDurability = true;
    public boolean showItemHeader = true;
  }

  public static final class Minimap extends Panel {
    public String shape = "square";
    public float size = 116f;
    public float zoom = 1.0f;
    public boolean lockNorth = false;
    public boolean terrainShading = true;
    public String reliefStyle = "classic";
    public float reliefStrength = 1.35f;
    public boolean biomeColors = true;
    public boolean blockLight = true;
    public boolean chunkGrid = false;
    public boolean showCompass = true;
    public boolean showCoords = true;
    public boolean showWaypoints = true;
    public boolean offscreenWaypoints = true;
    public String arrowStyle = "classic";
    public float arrowScale = 1.0f;
    public int arrowColor = 0xffffffff;
    public String enlargeMode = "hold";
    public float enlargeSize = 260f;
    public float enlargeZoom = 0.65f;
    public boolean showPlayers = true;
    public boolean showPlayerHeads = true;
    public boolean showPlayerNames = true;
    public boolean showInvisiblePlayers = true;
    public float playerScale = 1.0f;
    public boolean showItems = true;
    public String itemStyle = "icons";
    public boolean itemBackground = false;
    public boolean onlyValuableItems = false;
    public float itemScale = 0.95f;
    public String mobIconStyle = "icons";
    public float mobScale = 1.0f;
    public boolean showHostileMobs = true;
    public boolean showPassiveMobs = true;
    public boolean showNeutralMobs = true;
    public boolean showOtherEntities = true;
    public boolean showMobNames = false;
    public boolean showExplosives = true;
    public float frameOpacity = 0.85f;

    public Minimap() {
      enabled = false;
      anchorX = 0f;
      anchorY = 0f;
      offsetX = 10f;
      offsetY = 10f;
      width = 116f;
      height = 132f;
      scale = 1.0f;
    }
  }

  public static final class WorldMap {
    public boolean enabled = true;
    public boolean diskCache = true;
    public boolean terrainShading = true;
    public boolean biomeColors = true;
    public boolean blockLight = true;
    public boolean showGrid = false;
    public boolean showWaypoints = true;
    public boolean showPlayers = true;
    public boolean showInvisiblePlayers = true;
    public boolean showItems = true;
    public boolean showMobs = true;
    public boolean showOtherEntities = true;
    public boolean showCoordinatesBar = true;
    public boolean teleportCommand = false;
  }

  public static final class Freelook {
    public boolean enabled = false, smoothReturn = true;
    public float returnSpeed = 8, sensitivity = 1;
  }

  public static final class Cosmetics {
    public String capeMode = "default";
    public String officialCape = "";
    public String customCapePath = "";
    public String skinMode = "default";
    public String skinNickname = "";
    public String customSkinPath = "";
    public String skinModel = "auto";

    public String weaponMode = "none";
    public String builtinWeapon = "";
    public String weaponMaterial = "diamond";
    public String customWeaponPath = "";
    public String weaponTarget = "swords";
    public int weaponColor = 0xff38bdf8;
    public float weaponScale = 1.0f;
    public boolean weaponFirstPerson = true;
    public boolean weaponThirdPerson = true;

    public String armorMode = "none";
    public String builtinArmor = "";
    public String customArmorPath = "";
    public String armorTrigger = "always";
    public boolean armorHelmet = true;
    public boolean armorChestplate = true;
    public boolean armorLeggings = true;
    public boolean armorBoots = true;
    public boolean armorHideVanilla = true;
    public int armorColor = 0xff38bdf8;
    public float armorScale = 1.0f;
  }

  public static final class Atmosphere {
    public boolean enabled = false;
    public String mode = "dawn";

    public float density = 0.35f;
    public float scatterHeight = 76f;
    public float godRays = 75f;
    public float softness = 60f;
    public int dawnColor = 0xffffad7a;

    public float amount = 50f;
    public float ground = 100f;
    public float speed = 100f;
    public float haze = 50f;
    public float grade = 70f;
    public float vignette = 40f;
    public String quality = "medium";

    public SnowCover snowCover = new SnowCover();
    public Snowfall snowfall = new Snowfall();
    public Sakura sakura = new Sakura();
    public SakuraWater sakuraWater = new SakuraWater();
    public SakuraSky sakuraSky = new SakuraSky();
    public AutumnTrees autumnTrees = new AutumnTrees();
    public AutumnGround autumnGround = new AutumnGround();
    public AutumnAir autumnAir = new AutumnAir();
    public AutumnSky autumnSky = new AutumnSky();
    public MysticSky mysticSky = new MysticSky();
    public MysticFog mysticFog = new MysticFog();
    public MysticWorld mysticWorld = new MysticWorld();
    public MysticLens mysticLens = new MysticLens();
    public MysticLife mysticLife = new MysticLife();
    public MysticWind mysticWind = new MysticWind();

    public static final class SnowCover {
      public float frost = 40f;
      public float shelter = 10f;
      public float coldShadows = 60f;
      public float drifts = 50f;
      public float thickness = 50f;
      public float texture = 50f;
      public float sparkle = 50f;
      public float leaves = 60f;
      public float ice = 85f;
      public float cloudShadows = 30f;
      public boolean daytime = true;
    }

    public static final class Snowfall {
      public float flakeSize = 100f;
      public float clumps = 25f;
      public float wind = 30f;
      public float spindrift = 40f;
      public float farSnow = 50f;
    }

    public static final class Sakura {
      public float blossom = 70f;
      public int blossomColor = 0xfff6c3d5;
      public float wholeFlowers = 5f;
      public float gusts = 40f;
      public float nearTrees = 60f;
      public float hotaru = 50f;
    }

    public static final class SakuraWater {
      public float petals = 50f;
      public float moonPath = 60f;
      public float reflections = 100f;
      public float mist = 35f;
    }

    public static final class SakuraSky {
      public boolean volcano = true;
      public boolean pagoda = true;
      public float clouds = 35f;
      public float stars = 100f;
    }

    public static final class AutumnTrees {
      public float treeColors = 70f;
      public boolean evergreen = true;
      public float bareTrees = 25f;
    }

    public static final class AutumnGround {
      public float piles = 50f;
      public float dryGrass = 40f;
      public float puddles = 20f;
      public float dew = 30f;
    }

    public static final class AutumnAir {
      public boolean leafShapes = true;
      public float leafGlow = 60f;
      public float whirls = 40f;
      public float skitter = 50f;
      public float gossamer = 40f;
    }

    public static final class AutumnSky {
      public float clouds = 40f;
      public float birds = 50f;
      public boolean farForest = true;
      public float mist = 35f;
      public float sunHeight = 17f;
      public float sunRays = 60f;
    }

    public static final class MysticSky {
      public boolean moon = true;
      public int moonColor = 0xfff3e6cc;
      public float moonSize = 100f;
      public float moonHeight = 18f;
      public float halo = 40f;
      public float moonRays = 45f;
      public float stars = 50f;
      public float meteors = 30f;
      public float clouds = 45f;
      public float cloudShadows = 60f;
      public float lightning = 20f;
      public float silhouettes = 60f;
    }

    public static final class MysticFog {
      public float groundFog = 55f;
      public float fogHeight = 2.0f;
      public float fogTendrils = 50f;
      public int fogColor = 0xff59667a;
    }

    public static final class MysticWorld {
      public boolean alwaysNight = true;
      public float moonlight = 100f;
      public float coldShadows = 65f;
      public float blueNight = 60f;
      public float wither = 70f;
      public float wetness = 45f;
      public float puddles = 40f;
      public float reflections = 70f;
    }

    public static final class MysticLens {
      public float bokeh = 30f;
      public float grain = 50f;
    }

    public static final class MysticLife {
      public float bats = 40f;
      public float batColony = 35f;
      public boolean wisps = true;
      public int wispsColor = 0xff8ce6ff;
      public int wispCount = 3;
      public float webs = 35f;
      public float ghosts = 35f;
    }

    public static final class MysticWind {
      public float gusts = 50f;
      public float skitter = 50f;
      public float leafSize = 100f;
    }
  }

  public static final class WavyCapes {
    public boolean enabled = false;
    public String preset = "silk";
    public String style = "smooth";
    public String quality = "medium";
    public String players = "all";

    public float gravity = 1.0f;
    public float stiffness = 0.28f;
    public float damping = 0.34f;
    public float drag = 1.05f;
    public float turnSwing = 1.15f;

    public float wind = 0.65f;
    public float flutter = 0.90f;
    public boolean weatherWind = true;
    public boolean waterPhysics = true;

    public float thickness = 0.90f;
    public float widthScale = 1.0f;
    public float lengthScale = 1.0f;
    public float hemFlare = 0.14f;
    public float foldShading = 0.45f;
    public boolean bodyCollision = true;
    public float maxDistance = 64.0f;

    public void applyPreset(String name) {
      if ("silk".equals(name)) {
        preset = "silk";
        style = "smooth";
        gravity = 1.0f;
        stiffness = 0.28f;
        damping = 0.34f;
        drag = 1.05f;
        turnSwing = 1.15f;
        wind = 0.65f;
        flutter = 0.90f;
        thickness = 0.90f;
        widthScale = 1.0f;
        lengthScale = 1.0f;
        hemFlare = 0.14f;
        foldShading = 0.45f;
      } else if ("classic".equals(name)) {
        preset = "classic";
        style = "crisp";
        gravity = 1.25f;
        stiffness = 0.48f;
        damping = 0.45f;
        drag = 0.90f;
        turnSwing = 0.85f;
        wind = 0.35f;
        flutter = 0.50f;
        thickness = 1.0f;
        widthScale = 1.0f;
        lengthScale = 1.0f;
        hemFlare = 0.0f;
        foldShading = 0.30f;
      } else if ("royal".equals(name)) {
        preset = "royal";
        style = "smooth";
        gravity = 1.55f;
        stiffness = 0.52f;
        damping = 0.52f;
        drag = 0.80f;
        turnSwing = 0.75f;
        wind = 0.45f;
        flutter = 0.55f;
        thickness = 1.15f;
        widthScale = 1.12f;
        lengthScale = 1.18f;
        hemFlare = 0.28f;
        foldShading = 0.55f;
      } else if ("ethereal".equals(name)) {
        preset = "ethereal";
        style = "slim";
        gravity = 0.62f;
        stiffness = 0.14f;
        damping = 0.24f;
        drag = 1.45f;
        turnSwing = 1.55f;
        wind = 1.15f;
        flutter = 1.35f;
        thickness = 0.55f;
        widthScale = 1.04f;
        lengthScale = 1.08f;
        hemFlare = 0.22f;
        foldShading = 0.50f;
      } else if ("voxel".equals(name)) {
        preset = "voxel";
        style = "blocky";
        gravity = 1.10f;
        stiffness = 0.40f;
        damping = 0.40f;
        drag = 1.00f;
        turnSwing = 1.00f;
        wind = 0.50f;
        flutter = 0.70f;
        thickness = 1.0f;
        widthScale = 1.0f;
        lengthScale = 1.0f;
        hemFlare = 0.0f;
        foldShading = 0.25f;
      } else if ("custom".equals(name)) {
        preset = "custom";
      }
    }
  }

  public static final class AccountSwitcher {
    public boolean enabled = true;
    public String layout = "modern";
    public boolean titleScreen = true;
    public boolean multiplayerScreen = true;
    public String titlePosition = "auto";
    public String multiplayerPosition = "auto";
    public String overlayPosition = "topLeft";
    public boolean closeOnSwitch = false;
    public boolean hideAltsInProtect = true;
  }

  private static float finite(float v, float fallback, float min, float max) {
    return Float.isNaN(v) || Float.isInfinite(v) ? fallback : Math.max(min, Math.min(max, v));
  }

  private static boolean knownPreset(String id) {
    if (id == null || id.isEmpty()) return false;
    try {
      DefaultSkin.canonicalPreset(id);
      return true;
    } catch (IllegalArgumentException unknown) {
      return false;
    }
  }

  private static void validateTheme(Theme t, Theme defaults, boolean captureAccent) {
    if (t.motion == null) t.motion = defaults.motion.copy();
    t.motion.validate(defaults.motion);
    if (t.effects == null) t.effects = defaults.effects.copy();
    t.effects.validate(defaults.effects);
    if (t.name == null || t.name.length() > 64) t.name = defaults.name;
    t.radius = finite(t.radius, defaults.radius, 0, 28);
    t.borderWidth = finite(t.borderWidth, defaults.borderWidth, 0, 3);
    t.backgroundOpacity = finite(t.backgroundOpacity, defaults.backgroundOpacity, 0, 1);
    t.tintOpacity = finite(t.tintOpacity, defaults.tintOpacity, -1, 1);
    t.surfaceOpacity = finite(t.surfaceOpacity, defaults.surfaceOpacity, 0, 1);
    t.raisedOpacity = finite(t.raisedOpacity, defaults.raisedOpacity, 0, 1);
    t.blurRadius = finite(t.blurRadius, defaults.blurRadius, 0, 32);
    t.blurQuality = Math.max(1, Math.min(6, t.blurQuality));
    t.blurMix = finite(t.blurMix, defaults.blurMix, 0, 1);
    t.glassMix = finite(t.glassMix, defaults.glassMix, -1, 1);
    t.accentGradient = finite(t.accentGradient, defaults.accentGradient, 0, 1);
    t.fontTracking = finite(t.fontTracking, defaults.fontTracking, -.6f, 2);
    if (!"bold".equals(t.fontWeight)) t.fontWeight = defaults.fontWeight;
    if (t.fontFamily == null || !t.fontFamily.matches("[a-z][a-z0-9-]{0,63}"))
      t.fontFamily = defaults.fontFamily;
    if (captureAccent)
      t.applyMode();
    else
      t.applySavedMode();
  }

  public void validate() {
    validateExtensions();
    schemaVersion = 1;
    language = dev.nightclient.i18n.I18n.language(language);
    Theme themeDefaults = Theme.defaults();
    if (theme == null) theme = themeDefaults.copy();
    if (skin == null) skin = new Skin();
    try {
      skin.preset = DefaultSkin.canonicalPreset(skin.preset);
    } catch (IllegalArgumentException invalidPreset) {
      skin.preset = DefaultSkin.canonicalPreset(null);
    }
    if (skin.appearance == null)
      skin.appearance = new java.util.LinkedHashMap<String, dev.nightclient.config.Theme>();
    if (!knownPreset(skin.appliedPreset)) skin.appliedPreset = skin.preset;
    java.util.Iterator<java.util.Map.Entry<String, dev.nightclient.config.Theme>> looks =
        skin.appearance.entrySet().iterator();
    while (looks.hasNext()) {
      java.util.Map.Entry<String, dev.nightclient.config.Theme> look = looks.next();
      if (look.getValue() == null || !knownPreset(look.getKey()))
        looks.remove();
      else
        validateTheme(look.getValue(), themeDefaults, false);
    }
    if (skin.file != null && skin.file.matches("[a-zA-Z0-9_-]{1,64}\\.html"))
      skin.file = skin.file.substring(0, skin.file.length() - 5) + ".nc";
    if (skin.file == null || !skin.file.matches("[a-zA-Z0-9_-]{1,64}\\.nc"))
      skin.file = "skin.nc";
    if (moduleBindings == null) moduleBindings = new java.util.LinkedHashMap<String, String>();
    java.util.Iterator<java.util.Map.Entry<String, String>> binds =
        moduleBindings.entrySet().iterator();
    while (binds.hasNext()) {
      java.util.Map.Entry<String, String> entry = binds.next();
      if (moduleBindings.size() > 128 || entry.getKey() == null
          || !entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}") || entry.getValue() == null
          || !entry.getValue().matches("key\\.(keyboard|mouse)\\.[a-z0-9._-]{1,64}"))
        binds.remove();
    }
    if (menu == null) menu = new Menu();
    if (saturation == null) saturation = new Saturation();
    if (hud == null) hud = new Hud();
    if (tnt == null) tnt = new Tnt();
    if (inventory == null) inventory = new Inventory();
    if (crosshair == null) crosshair = new Crosshair();
    if (zoom == null) zoom = new Zoom();
    if (protect == null) protect = new Protect();
    validateTheme(theme, themeDefaults, true);
    menu.vanillaBlurRadius = finite(menu.vanillaBlurRadius, 5, 0, 20);
    menu.opacity = finite(menu.opacity, 1, 0, 1);
    menu.scale = finite(menu.scale, 1f, 0.75f, 1.5f);
    menu.hudScale = finite(menu.hudScale, 1f, 0.5f, 2);
    menu.fontScale = finite(menu.fontScale, 1, 0.8f, 1.3f);
    menu.animationSpeed = finite(menu.animationSpeed, 1, 0.4f, 1000000);
    menu.width = finite(menu.width, 425, 320, 1600);
    menu.height = finite(menu.height, 250, 220, 1000);
    menu.density = finite(menu.density, .9f, 0.65f, 1.3f);
    saturation.strength = finite(saturation.strength, 1.15f, 0, 2.5f);
    hud.layoutOrder = Math.max(0, hud.layoutOrder);
    hud.opacity = finite(hud.opacity, 1, 0, 1);
    hud.scale = finite(hud.scale, 1, 0.1f, 2);
    hud.anchorX = finite(hud.anchorX, 1, 0, 1);
    hud.anchorY = finite(hud.anchorY, 0, 0, 1);
    hud.offsetX = finite(hud.offsetX, -20, -4096, 4096);
    hud.offsetY = finite(hud.offsetY, 90, -4096, 4096);
    hud.width = finite(hud.width, 148, 120, 380);
    hud.spacing = finite(hud.spacing, 7, 0, 20);
    hud.maxVisible = Math.max(1, Math.min(20, hud.maxVisible));
    hud.fontScale = finite(hud.fontScale, 1, 0.7f, 1.5f);
    hud.rowHeight = finite(hud.rowHeight, 38, 28, 64);
    hud.iconScale = finite(hud.iconScale, 1, 0.6f, 1.4f);
    hud.progressHeight = finite(hud.progressHeight, 2, 1, 6);
    if (!"name".equals(hud.sort) && !"duration".equals(hud.sort)) hud.sort = "remaining";
    validatePanel(inventory);
    validatePanel(tnt);
    inventory.rows = Math.max(1, Math.min(3, inventory.rows));
    tnt.range = finite(tnt.range, 64, 8, 256);
    if (!"dot".equals(crosshair.preset) && !"circle".equals(crosshair.preset)
        && !"ring".equals(crosshair.preset) && !"diamond".equals(crosshair.preset))
      crosshair.preset = "cross";
    if (!"shape".equals(crosshair.mode)) crosshair.mode = "pixels";
    if (!"custom".equals(crosshair.colorMode) && !"pixels".equals(crosshair.colorMode))
      crosshair.colorMode = "vanilla";
    if (crosshair.pattern == null) crosshair.pattern = new CrosshairPattern();
    crosshair.pattern.validate();
    crosshair.pixelScale = finite(crosshair.pixelScale, 1, 1, 8);
    crosshair.offsetX = Math.max(-64, Math.min(64, crosshair.offsetX));
    crosshair.offsetY = Math.max(-64, Math.min(64, crosshair.offsetY));
    crosshair.size = finite(crosshair.size, 7, 1, 48);
    crosshair.gap = finite(crosshair.gap, 3, 0, 32);
    crosshair.thickness = finite(crosshair.thickness, 1.3f, .5f, 8);
    crosshair.outline = finite(crosshair.outline, 1, 0, 4);
    crosshair.opacity = finite(crosshair.opacity, 1, 0, 1);
    crosshair.targetColor = opaque(crosshair.targetColor, 0xffff3b5c);
    crosshair.targetRange = finite(crosshair.targetRange, 0f, 0f, 64f);
    if (!"cross".equals(crosshair.targetShape) && !"dot".equals(crosshair.targetShape)
        && !"circle".equals(crosshair.targetShape) && !"diamond".equals(crosshair.targetShape)
        && !"brackets".equals(crosshair.targetShape) && !"chevron".equals(crosshair.targetShape)
        && !"expand".equals(crosshair.targetShape) && !"custom".equals(crosshair.targetShape))
      crosshair.targetShape = "x";
    if (crosshair.targetPattern == null)
      crosshair.targetPattern = CrosshairPattern.defaultTargetPattern();
    crosshair.targetPattern.validate();
    crosshair.targetScale = finite(crosshair.targetScale, 1.15f, 0.4f, 3.0f);
    crosshair.targetSize = finite(crosshair.targetSize, 6f, 1f, 32f);
    crosshair.targetGap = finite(crosshair.targetGap, 2.5f, 0f, 24f);
    crosshair.targetThickness = finite(crosshair.targetThickness, 1.5f, 0.5f, 6f);
    crosshair.targetOutline = finite(crosshair.targetOutline, 1f, 0f, 3f);
    crosshair.targetRotation = finite(crosshair.targetRotation, 0f, -180f, 180f);
    crosshair.targetAnimSpeed = finite(crosshair.targetAnimSpeed, 14f, 4f, 30f);
    zoom.factor = finite(zoom.factor, 3, 1.25f, 20);
    if (protect.replacement == null || protect.replacement.trim().isEmpty())
      protect.replacement = "NightProtect";
    protect.replacement = protect.replacement.replaceAll("[\\p{Cntrl}§]", "");
    if (protect.replacement.length() > 32)
      protect.replacement = protect.replacement.substring(0, 32);
    if (!protect.replacement.isEmpty()
        && Character.isHighSurrogate(protect.replacement.charAt(protect.replacement.length() - 1)))
      protect.replacement = protect.replacement.substring(0, protect.replacement.length() - 1);
    if (protect.replacement.trim().isEmpty()) protect.replacement = "NightProtect";
    if (protect.aliases == null) protect.aliases = new Protect().aliases;
    List<String> cleanAliases = new ArrayList<String>();
    for (String alias : protect.aliases) {
      if (alias == null || cleanAliases.size() >= 32) continue;
      String normalized = alias.trim().toLowerCase(Locale.ROOT);
      if (normalized.startsWith("/")) normalized = normalized.substring(1);
      if (normalized.matches("[a-z0-9_:-]{1,48}") && !cleanAliases.contains(normalized))
        cleanAliases.add(normalized);
    }
    protect.aliases = cleanAliases;
  }

  private static void validatePanel(Panel p) {
    p.layoutOrder = Math.max(0, p.layoutOrder);
    boolean tntPanel = p instanceof Tnt, inventoryPanel = p instanceof Inventory;
    p.scale = finite(p.scale, 1, .1f, 3);
    p.opacity = finite(p.opacity, 1, 0, 1);
    p.anchorX = finite(p.anchorX, inventoryPanel ? 1 : 0, 0, 1);
    p.anchorY = finite(p.anchorY, tntPanel ? 0 : 1, 0, 1);
    p.offsetX = finite(p.offsetX, inventoryPanel ? -20 : 20, -4096, 4096);
    p.offsetY = finite(p.offsetY, tntPanel ? 90 : inventoryPanel ? -54 : -20, -4096, 4096);
    p.width = finite(p.width, tntPanel ? 168 : 224, 72, 1024);
    p.height = finite(p.height, tntPanel ? 96 : 80, 24, 1024);
  }

  public Theme hudTheme() {
    return theme;
  }

  public Theme styleTheme(String id) {
    Theme resolved = skinDocument != null ? skinDocument.theme(id, theme) : theme;
    float opacity = 1;
    if ("cooldowns".equals(id))
      opacity = hud.opacity;
    else if ("inventory".equals(id))
      opacity = inventory.opacity;
    else if ("tnt".equals(id))
      opacity = tnt.opacity;
    else if ("armor".equals(id))
      opacity = armor.opacity;
    else if ("potions".equals(id))
      opacity = potions.opacity;
    else if ("target".equals(id))
      opacity = target.opacity;
    else if ("scoreboard".equals(id))
      opacity = scoreboard.opacity;
    else if ("keystrokes".equals(id))
      opacity = keystrokes.opacity;
    else if ("coordinates".equals(id))
      opacity = coordinates.opacity;
    else if ("ping".equals(id))
      opacity = ping.opacity;
    else if ("fps".equals(id))
      opacity = fps.opacity;
    else if ("betterChat".equals(id))
      opacity = betterChat.opacity;
    else if ("minimap".equals(id))
      opacity = minimap.opacity;
    if (opacity >= 1) return resolved;
    Theme local = resolved.copy();
    local.backgroundOpacity *= Math.max(0, opacity);
    return local;
  }

  private void validateExtensions() {
    if (interfaceState == null) interfaceState = new InterfaceState();
    if (interfaceState.expandedPanels == null)
      interfaceState.expandedPanels = new LinkedHashMap<String, Boolean>();
    Iterator<Map.Entry<String, Boolean>> expanded =
        interfaceState.expandedPanels.entrySet().iterator();
    while (expanded.hasNext()) {
      Map.Entry<String, Boolean> entry = expanded.next();
      if (interfaceState.expandedPanels.size() > 256 || entry.getKey() == null
          || entry.getKey().isEmpty() || entry.getKey().length() > 128 || entry.getValue() == null)
        expanded.remove();
    }
    if (armor == null) armor = new Armor();
    if (!"minecraft".equals(armor.style)) armor.style = "visual";
    if (!"vanilla".equals(armor.durabilityStyle) && !"bar".equals(armor.durabilityStyle)
        && !"text".equals(armor.durabilityStyle))
      armor.durabilityStyle = "auto";
    if (!"above".equals(armor.durabilityPosition)) armor.durabilityPosition = "below";
    if (potions == null) potions = new Potions();
    if (food == null) food = new Food();
    if (target == null) target = new Target();
    if (tweaksy == null) tweaksy = new Tweaksy();
    if (optimization == null) optimization = new Optimization();
    if (optimization.profile == null || optimization.profile.trim().isEmpty()
        || "balanced".equals(optimization.profile)) {
      optimization.profile = "performance";
    } else if (!java.util.Arrays.asList("lossless", "performance", "competitive", "custom")
        .contains(optimization.profile)) {
      optimization.profile = "performance";
    }
    optimization.particleDistance = Math.max(8, Math.min(128, optimization.particleDistance));
    optimization.particleLimit = Math.max(100, Math.min(10000, optimization.particleLimit));
    optimization.entityDistance = Math.max(16, Math.min(256, optimization.entityDistance));
    optimization.nametagDistance = Math.max(8, Math.min(128, optimization.nametagDistance));
    optimization.blockEntityDistance =
        Math.max(16, Math.min(256, optimization.blockEntityDistance));
    optimization.signTextDistance = Math.max(8, Math.min(96, optimization.signTextDistance));
    optimization.itemClusterLimit = Math.max(1, Math.min(16, optimization.itemClusterLimit));
    if (optimization.enabledFlags == null) optimization.enabledFlags = "";
    if (optimization.disabledFlags == null) optimization.disabledFlags = "";
    if (tweaksy.mainLeftFix == null) tweaksy.mainLeftFix = new MainLeftFix();
    if (tweaksy.commandFix == null) tweaksy.commandFix = new CommandFix();
    if (tweaksy.cursorFix == null) tweaksy.cursorFix = new CursorFix();
    if (tweaksy.fullBright == null) tweaksy.fullBright = new FullBright();
    if (tweaksy.ownNametag == null) tweaksy.ownNametag = new OwnNametag();
    if (tweaksy.invisibleArmorNametag == null) tweaksy.invisibleArmorNametag = new InvisibleArmorNametag();
    if (itemScroller == null) itemScroller = new ItemScroller();
    if (hitColor == null) hitColor = new HitColor();
    if (hitbox == null) hitbox = new Hitbox();
    if (waypoints == null) waypoints = new Waypoints();
    if (lowFire == null) lowFire = new LowFire();
    if (noRender == null) noRender = new NoRender();
    if (itemPhysics == null) itemPhysics = new ItemPhysics();
    if (viewmodel == null) viewmodel = new Viewmodel();
    if (aspectRatio == null) aspectRatio = new AspectRatio();
    if (swing == null) swing = new Swing();
    if (freelook == null) freelook = new Freelook();
    if (damageSplash == null) damageSplash = new DamageSplash();
    if (scoreboard == null) scoreboard = new Scoreboard();
    if (keystrokes == null) keystrokes = new Keystrokes();
    if (coordinates == null) coordinates = new Coordinates();
    if (ping == null) ping = new Ping();
    if (fps == null) fps = new Fps();
    if (hwhelper == null) hwhelper = new HwHelper();
    if (hwhelper.aucprice == null) hwhelper.aucprice = new AucPrice();
    if (hwhelper.answap == null) hwhelper.answap = new AnarchySwap();
    if (hwhelper.answap.catalog == null)
      hwhelper.answap.catalog = new LinkedHashMap<String, String>();
    else {
      Map<String, String> cleaned = new LinkedHashMap<String, String>();
      for (Map.Entry<String, String> entry : hwhelper.answap.catalog.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) continue;
        try {
          int number = Integer.parseInt(entry.getKey().trim());
          String cat = entry.getValue().trim().toLowerCase(Locale.ROOT);
          if (number >= 1 && number <= 256 && cat.matches("[a-z0-9_-]{1,32}"))
            cleaned.put(Integer.toString(number), cat);
        } catch (NumberFormatException ignored) {
        }
      }
      hwhelper.answap.catalog = cleaned;
    }
    if (hwhelper.betterSell == null) hwhelper.betterSell = new BetterSell();
    validatePanel(armor, 168, 50);
    validatePanel(potions, 220, 160);
    validatePanel(target, 224, 72);
    validatePanel(scoreboard, 140, 120);
    validatePanel(keystrokes, 80, 70);
    validatePanel(coordinates, 160, 24);
    validatePanel(ping, 60, 20);
    validatePanel(fps, 60, 20);
    if (playerHealth == null) playerHealth = new PlayerHealth();
    if (!"hearts".equals(playerHealth.style)) playerHealth.style = "pill";
    playerHealth.height = finite(playerHealth.height, .45f, 0, 1.5f);
    playerHealth.scale = finite(playerHealth.scale, 1, .6f, 1.6f);
    playerHealth.maxDistance = Math.max(8, Math.min(128, playerHealth.maxDistance));
    if (totemCounter == null) totemCounter = new TotemCounter();
    totemCounter.height = finite(totemCounter.height, 0f, -.5f, .8f);
    totemCounter.scale = finite(totemCounter.scale, 1f, .6f, 1.6f);
    totemCounter.maxDistance = Math.max(8, Math.min(128, totemCounter.maxDistance));
    if (!Arrays.asList("list", "compact", "pills", "vanilla").contains(potions.format))
      potions.format = "list";
    lowFire.offsetY = finite(lowFire.offsetY, .45f, 0, 1.5f);
    noRender.fireOffset = finite(noRender.fireOffset, .45f, 0, 1.5f);
    if (!"zakohp".equals(target.style)) target.style = "card";
    target.displaySeconds = finite(target.displaySeconds, 5, .2f, 60);
    if (!Arrays.asList("entity", "hitbox", "both").contains(hitColor.mode))
      hitColor.mode = "entity";
    hitColor.opacity = finite(hitColor.opacity, .65f, 0, 1);
    hitbox.lineWidth = finite(hitbox.lineWidth, 1.5f, .5f, 8);
    hitbox.range = finite(hitbox.range, 64, 1, 128);
    waypoints.badge = finite(waypoints.badge, 1, .5f, 2);
    waypoints.hideNearbyRadius = Math.max(1, Math.min(100, waypoints.hideNearbyRadius));
    if (waypoints.points == null) waypoints.points = new ArrayList<Waypoint>();
    java.util.Iterator<Waypoint> points = waypoints.points.iterator();
    for (int kept = 0; points.hasNext();) {
      Waypoint point = points.next();
      if (point == null || kept >= Waypoints.POINT_LIMIT || !point.normalize())
        points.remove();
      else
        kept++;
    }
    itemScroller.cooldownMs = Math.max(20, Math.min(1000, itemScroller.cooldownMs));
    itemScroller.dragCooldownMs = Math.max(0, Math.min(250, itemScroller.dragCooldownMs));
    if (!"matching".equals(itemScroller.wheelMode)) itemScroller.wheelMode = "stack";
    if (!Arrays.asList("both", "up", "down").contains(itemScroller.wheelDirection))
      itemScroller.wheelDirection = "both";
    itemScroller.gesture = "shift-drag";
    itemPhysics.rotationSpeed = finite(itemPhysics.rotationSpeed, 1, 0, 5);
    tweaksy.fullBright.strength = finite(tweaksy.fullBright.strength, 1, 0, 1);
    if (viewmodel.main == null) viewmodel.main = new HandTransform();
    if (viewmodel.off == null) viewmodel.off = new HandTransform();
    validateHand(viewmodel.main);
    validateHand(viewmodel.off);
    viewmodel.translationSnap = finite(viewmodel.translationSnap, .05f, .001f, 2);
    viewmodel.rotationSnap = finite(viewmodel.rotationSnap, 15, 1, 90);
    viewmodel.scaleSnap = finite(viewmodel.scaleSnap, .1f, .01f, 1);
    if (aspectRatio.preset == null) {
      aspectRatio.preset = "16_9";
    } else if ("16:9".equals(aspectRatio.preset)) {
      aspectRatio.preset = "16_9";
    } else if ("4:3".equals(aspectRatio.preset)) {
      aspectRatio.preset = "4_3";
    } else if ("16:10".equals(aspectRatio.preset)) {
      aspectRatio.preset = "16_10";
    } else if ("21:9".equals(aspectRatio.preset)) {
      aspectRatio.preset = "21_9";
    } else if ("5:4".equals(aspectRatio.preset)) {
      aspectRatio.preset = "5_4";
    } else if (!Arrays.asList("16_9", "4_3", "16_10", "21_9", "5_4", "custom")
                   .contains(aspectRatio.preset)) {
      aspectRatio.preset = "16_9";
    }
    aspectRatio.ratio = finite(aspectRatio.ratio, 1.7777778f, .5f, 4);
    damageSplash.scale = finite(damageSplash.scale, 1, 0.5f, 2.5f);
    damageSplash.duration = finite(damageSplash.duration, 1.2f, 0.5f, 3.0f);
    if (!Arrays.asList("dynamic", "custom", "accent").contains(damageSplash.colorMode))
      damageSplash.colorMode = "dynamic";
    scoreboard.scale = finite(scoreboard.scale, 1, 0.5f, 2.0f);
    scoreboard.opacity = finite(scoreboard.opacity, 0.8f, 0, 1);
    if (!"visual".equals(scoreboard.font)) scoreboard.font = "minecraft";
    keystrokes.scale = finite(keystrokes.scale, 1, 0.5f, 2.0f);
    keystrokes.opacity = finite(keystrokes.opacity, 0.9f, 0, 1);
    coordinates.scale = finite(coordinates.scale, 1, 0.5f, 2.0f);
    coordinates.opacity = finite(coordinates.opacity, 1, 0, 1);
    ping.scale = finite(ping.scale, 1, 0.5f, 2.0f);
    ping.opacity = finite(ping.opacity, 1, 0, 1);
    fps.scale = finite(fps.scale, 1, 0.5f, 2.0f);
    fps.opacity = finite(fps.opacity, 1, 0, 1);
    swing.speed = finite(swing.speed, 1, .1f, 5);
    swing.amplitude = finite(swing.amplitude, 1, 0, 3);
    if (!Arrays.asList("vanilla", "smooth", "swipe", "spin", "custom").contains(swing.style))
      swing.style = "vanilla";
    swing.rotateX = finite(swing.rotateX, 0, -180, 180);
    swing.rotateY = finite(swing.rotateY, 0, -180, 180);
    swing.rotateZ = finite(swing.rotateZ, 0, -180, 180);
    freelook.returnSpeed = finite(freelook.returnSpeed, 8, 1, 30);
    freelook.sensitivity = finite(freelook.sensitivity, 1, .1f, 3);
    if (cosmetics == null) cosmetics = new Cosmetics();
    cosmetics.officialCape = cleanCosmeticValue(cosmetics.officialCape, 64);
    cosmetics.customCapePath = cleanCosmeticValue(cosmetics.customCapePath, 1024);
    cosmetics.skinNickname = cleanCosmeticValue(cosmetics.skinNickname, 32);
    cosmetics.customSkinPath = cleanCosmeticValue(cosmetics.customSkinPath, 1024);
    if (!Arrays.asList("default", "none", "official", "custom").contains(cosmetics.capeMode))
      cosmetics.capeMode = "default";
    if ("official".equals(cosmetics.capeMode)
        && (!dev.nightclient.cosmetics.OfficialCapes.isEnabled()
            || dev.nightclient.cosmetics.OfficialCapes.byId(cosmetics.officialCape) == null)) {
      cosmetics.officialCape = "";
      cosmetics.capeMode = "default";
    }
    if ("custom".equals(cosmetics.capeMode) && cosmetics.customCapePath.isEmpty())
      cosmetics.capeMode = "default";
    if (!Arrays.asList("default", "nick", "custom").contains(cosmetics.skinMode))
      cosmetics.skinMode = "default";
    if ("nick".equals(cosmetics.skinMode) && cosmetics.skinNickname.isEmpty())
      cosmetics.skinMode = "default";
    if ("custom".equals(cosmetics.skinMode) && cosmetics.customSkinPath.isEmpty())
      cosmetics.skinMode = "default";
    if (!Arrays.asList("auto", "classic", "slim").contains(cosmetics.skinModel))
      cosmetics.skinModel = "auto";
    cosmetics.builtinWeapon = cleanCosmeticValue(cosmetics.builtinWeapon, 64);
    cosmetics.weaponMaterial = cleanCosmeticValue(cosmetics.weaponMaterial, 64);
    if (dev.nightclient.cosmetics.CosmeticModels.materialById(cosmetics.weaponMaterial) == null)
      cosmetics.weaponMaterial = dev.nightclient.cosmetics.CosmeticModels.DEFAULT_MATERIAL;
    cosmetics.customWeaponPath = cleanCosmeticValue(cosmetics.customWeaponPath, 1024);
    if (!Arrays.asList("none", "builtin", "custom").contains(cosmetics.weaponMode))
      cosmetics.weaponMode = "none";
    if ("builtin".equals(cosmetics.weaponMode)
        && dev.nightclient.cosmetics.CosmeticModels.weaponById(cosmetics.builtinWeapon) == null) {
      cosmetics.builtinWeapon = "";
      cosmetics.weaponMode = "none";
    }
    if ("custom".equals(cosmetics.weaponMode) && cosmetics.customWeaponPath.isEmpty())
      cosmetics.weaponMode = "none";
    if (!Arrays.asList("swords", "weapons", "tools", "all").contains(cosmetics.weaponTarget))
      cosmetics.weaponTarget = "swords";
    cosmetics.weaponColor = opaque(cosmetics.weaponColor, 0xff38bdf8);
    cosmetics.weaponScale = finite(cosmetics.weaponScale, 1.0f, 0.25f, 2.5f);

    cosmetics.builtinArmor = cleanCosmeticValue(cosmetics.builtinArmor, 64);
    cosmetics.customArmorPath = cleanCosmeticValue(cosmetics.customArmorPath, 1024);
    if (!Arrays.asList("none", "builtin", "custom").contains(cosmetics.armorMode))
      cosmetics.armorMode = "none";
    if ("builtin".equals(cosmetics.armorMode)
        && dev.nightclient.cosmetics.CosmeticModels.armorById(cosmetics.builtinArmor) == null) {
      cosmetics.builtinArmor = "";
      cosmetics.armorMode = "none";
    }
    if ("custom".equals(cosmetics.armorMode) && cosmetics.customArmorPath.isEmpty())
      cosmetics.armorMode = "none";
    if (!Arrays.asList("always", "equipped").contains(cosmetics.armorTrigger))
      cosmetics.armorTrigger = "always";
    cosmetics.armorColor = opaque(cosmetics.armorColor, 0xff38bdf8);
    cosmetics.armorScale = finite(cosmetics.armorScale, 1.0f, 0.5f, 1.8f);
    if (worldTime == null) worldTime = new WorldTime();
    if (!"off".equals(worldTime.time) && !"day".equals(worldTime.time)
        && !"night".equals(worldTime.time))
      worldTime.time = "day";
    if (customSky == null) customSky = new CustomSky();
    validateCustomSky(customSky);
    if (playerPing == null) playerPing = new PlayerPing();
    validatePlayerPing(playerPing);
    if (safeCtLeave == null) safeCtLeave = new SafeCtLeave();
    validateSafeCtLeave(safeCtLeave);
    if (betterChat == null) betterChat = new BetterChat();
    validateBetterChat(betterChat);
    if (blockOverlay == null) blockOverlay = new BlockOverlay();
    validateBlockOverlay(blockOverlay);
    if (enchantGlint == null) enchantGlint = new EnchantGlint();
    validateEnchantGlint(enchantGlint);
    if (explosionRing == null) explosionRing = new ExplosionRing();
    validateExplosionRing(explosionRing);
    if (tooltips == null) tooltips = new Tooltips();
    validateTooltips(tooltips);
    if (minimap == null) minimap = new Minimap();
    validateMinimap(minimap);
    if (worldMap == null) worldMap = new WorldMap();
    validateWorldMap(worldMap);
    if (atmosphere == null) atmosphere = new Atmosphere();
    validateAtmosphere(atmosphere);
    if (wavyCapes == null) wavyCapes = new WavyCapes();
    validateWavyCapes(wavyCapes);
    if (accountSwitcher == null) accountSwitcher = new AccountSwitcher();
    validateAccountSwitcher(accountSwitcher);
    if (hwhelper == null) hwhelper = new HwHelper();
    validateHwHelper(hwhelper);
    if (soundControl == null) soundControl = new SoundControl();
    validateSoundControl(soundControl);
    if (effectAlert == null) effectAlert = new EffectAlert();
    validateEffectAlert(effectAlert);
    if (tapeMouse == null) tapeMouse = new TapeMouse();
    validateTapeMouse(tapeMouse);
    if (motionBlur == null) motionBlur = new MotionBlur();
    validateMotionBlur(motionBlur);
    if (tabAnimation == null) tabAnimation = new TabAnimation();
    validateTabAnimation(tabAnimation);
  }

  private static void validateMotionBlur(MotionBlur mb) {
    mb.strength = finite(mb.strength, 50.0f, 1.0f, 100.0f);
    if (!Arrays.asList("low", "medium", "high").contains(mb.quality)) {
      mb.quality = "high";
    }
  }

  private static void validateTabAnimation(TabAnimation ta) {
    if (!"slide".equals(ta.style)) ta.style = "scale";
    ta.durationMs = Math.max(60, Math.min(600, ta.durationMs));
  }

  private static void validateHwHelper(HwHelper hw) {
    if (hw.aucprice == null) hw.aucprice = new AucPrice();
    if (hw.answap == null) hw.answap = new AnarchySwap();
    if (hw.answap.catalog == null) hw.answap.catalog = new LinkedHashMap<String, String>();
    if (hw.betterSell == null) hw.betterSell = new BetterSell();
    if (hw.esp == null) hw.esp = new HwEsp();
    hw.esp.idleColor = opaque(hw.esp.idleColor, 0xff38bdf8);
    hw.esp.enemyColor = opaque(hw.esp.enemyColor, 0xff22c55e);
    hw.esp.drillColor = opaque(hw.esp.drillColor, 0xff38bdf8);
    hw.esp.fillOpacity = finite(hw.esp.fillOpacity, 0.16f, 0.0f, 0.45f);
    hw.esp.lineWidth = finite(hw.esp.lineWidth, 2.0f, 0.8f, 5.0f);
    if (hw.timers == null) hw.timers = new HwTimers();
    hw.timers.trapSeconds = finite(hw.timers.trapSeconds, 15.0f, 3.0f, 45.0f);
    hw.timers.stunSeconds = finite(hw.timers.stunSeconds, 15.0f, 3.0f, 45.0f);
    hw.timers.snowballSeconds = finite(hw.timers.snowballSeconds, 10.0f, 2.0f, 30.0f);
    hw.timers.scale = finite(hw.timers.scale, 1.0f, 0.6f, 1.8f);
    if (hw.events == null) hw.events = new HwEvents();
    if (!Arrays.asList("all", "solo", "duo", "trio", "clan", "lite121")
             .contains(hw.events.modeFilter)) {
      hw.events.modeFilter = "all";
    }
    if (!Arrays.asList("all", "legendary", "mythic", "rare")
             .contains(hw.events.rarityFilter)) {
      hw.events.rarityFilter = "all";
    }
    hw.events.pollIntervalSeconds = Math.max(10, Math.min(60, hw.events.pollIntervalSeconds));
  }

  private static void validateSoundControl(SoundControl sc) {
    if (sc.mutedBlocks == null) sc.mutedBlocks = new ArrayList<String>();
    List<String> cleaned = new ArrayList<String>(sc.mutedBlocks.size());
    for (int i = 0; i < sc.mutedBlocks.size(); i++) {
      String b = cleanCosmeticValue(sc.mutedBlocks.get(i), 96).toLowerCase(Locale.ROOT);
      if (b.startsWith("minecraft:")) b = b.substring("minecraft:".length());
      if (!b.isEmpty() && !cleaned.contains(b)) cleaned.add(b);
    }
    sc.mutedBlocks = cleaned;
    sc.blockVolume = finite(sc.blockVolume, 0.0f, 0.0f, 1.0f);
    if (!Arrays.asList("vanilla", "note_pling", "amethyst", "anvil", "bell", "warden_sonic", "mp3", "custom")
             .contains(sc.tntFusePreset)) {
      sc.tntFusePreset = "note_pling";
    }
    if (!Arrays.asList("vanilla", "warden_sonic", "thunder", "trident", "amethyst", "mute", "mp3", "custom")
             .contains(sc.tntExplodePreset)) {
      sc.tntExplodePreset = "vanilla";
    }
    sc.tntFuseMp3 = cleanCosmeticValue(sc.tntFuseMp3, 512);
    sc.tntExplodeMp3 = cleanCosmeticValue(sc.tntExplodeMp3, 512);
    sc.customTntSoundId = cleanCosmeticValue(sc.customTntSoundId, 128);
    if (sc.customTntSoundId.isEmpty()) sc.customTntSoundId = "minecraft:block.note_block.pling";
    sc.tntVolume = finite(sc.tntVolume, 1.0f, 0.0f, 2.0f);
    sc.tntPitch = finite(sc.tntPitch, 1.35f, 0.5f, 2.0f);
    if (!Arrays.asList("note_pling", "experience", "amethyst", "bell", "chime", "levelup")
             .contains(sc.mentionPreset)) {
      sc.mentionPreset = "note_pling";
    }
    sc.mentionVolume = finite(sc.mentionVolume, 1.0f, 0.1f, 2.0f);
    sc.mentionPitch = finite(sc.mentionPitch, 1.25f, 0.5f, 2.0f);
    sc.mentionKeywords = cleanCosmeticValue(sc.mentionKeywords, 256);
    sc.explosionVolume = finite(sc.explosionVolume, 1.0f, 0.0f, 1.0f);
    sc.totemVolume = finite(sc.totemVolume, 1.0f, 0.0f, 1.0f);
    sc.weatherVolume = finite(sc.weatherVolume, 1.0f, 0.0f, 1.0f);
    sc.portalBeaconVolume = finite(sc.portalBeaconVolume, 1.0f, 0.0f, 1.0f);
  }

  private static void validateEffectAlert(EffectAlert ea) {
    validatePanel(ea, 160f, 30f);
    if (!Arrays.asList("both", "sound", "hud").contains(ea.mode)) ea.mode = "both";
    ea.warnSeconds = Math.max(1, Math.min(30, ea.warnSeconds));
    if (!Arrays.asList("note_pling", "amethyst", "bell", "experience", "anvil", "bass")
             .contains(ea.soundPreset)) {
      ea.soundPreset = "note_pling";
    }
    ea.soundVolume = finite(ea.soundVolume, 1.0f, 0.1f, 2.0f);
    ea.soundPitch = finite(ea.soundPitch, 1.3f, 0.5f, 2.0f);
  }

  private static void validateTapeMouse(TapeMouse tm) {
    if (!Arrays.asList("attack", "use", "forward", "jump", "sneak", "sprint", "drop", "custom")
             .contains(tm.action)) {
      tm.action = "attack";
    }
    tm.customKey = cleanCosmeticValue(tm.customKey, 64);
    if (tm.customKey.isEmpty()) tm.customKey = "key.mouse.left";
    tm.delayTicks = Math.max(0, Math.min(1200, tm.delayTicks));
    tm.jitterTicks = Math.max(0, Math.min(10, tm.jitterTicks));
  }

  private static void validateAccountSwitcher(AccountSwitcher as) {
    if (!"classic".equals(as.layout)) as.layout = "modern";
    if ("top-left".equals(as.overlayPosition)) as.overlayPosition = "topLeft";
    else if ("top-right".equals(as.overlayPosition)) as.overlayPosition = "topRight";
    else if ("bottom-left".equals(as.overlayPosition)) as.overlayPosition = "bottomLeft";
    if (!Arrays.asList("topLeft", "topRight", "bottomLeft").contains(as.overlayPosition))
      as.overlayPosition = "topLeft";
    if (!Arrays.asList("auto", "besideButtons", "topRight", "topLeft", "bottomLeft")
             .contains(as.titlePosition))
      as.titlePosition = "auto";
    if (!Arrays.asList("auto", "headerRight", "headerLeft", "bottomLeft")
             .contains(as.multiplayerPosition))
      as.multiplayerPosition = "auto";
  }

  private static void validateWavyCapes(WavyCapes wc) {
    if (!Arrays.asList("silk", "classic", "royal", "ethereal", "voxel", "custom")
             .contains(wc.preset))
      wc.preset = "silk";
    if (!Arrays.asList("smooth", "crisp", "blocky", "slim").contains(wc.style))
      wc.style = "smooth";
    if (!Arrays.asList("low", "medium", "high").contains(wc.quality)) wc.quality = "medium";
    if (!Arrays.asList("all", "self").contains(wc.players)) wc.players = "all";
    wc.gravity = finite(wc.gravity, 1.0f, 0.2f, 2.5f);
    wc.stiffness = finite(wc.stiffness, 0.28f, 0.0f, 1.0f);
    wc.damping = finite(wc.damping, 0.34f, 0.0f, 1.0f);
    wc.drag = finite(wc.drag, 1.05f, 0.2f, 2.5f);
    wc.turnSwing = finite(wc.turnSwing, 1.15f, 0.0f, 2.5f);
    wc.wind = finite(wc.wind, 0.65f, 0.0f, 2.5f);
    wc.flutter = finite(wc.flutter, 0.90f, 0.0f, 2.0f);
    wc.thickness = finite(wc.thickness, 0.90f, 0.2f, 1.8f);
    wc.widthScale = finite(wc.widthScale, 1.0f, 0.8f, 1.35f);
    wc.lengthScale = finite(wc.lengthScale, 1.0f, 0.7f, 1.35f);
    wc.hemFlare = finite(wc.hemFlare, 0.14f, 0.0f, 1.0f);
    wc.foldShading = finite(wc.foldShading, 0.45f, 0.0f, 1.0f);
    wc.maxDistance = finite(wc.maxDistance, 64.0f, 16.0f, 128.0f);
  }

  private static void validateBlockOverlay(BlockOverlay bo) {
    if (!Arrays.asList("outline", "fill", "both", "corners").contains(bo.mode)) bo.mode = "both";
    bo.color = opaque(bo.color, 0xff7c9cff);
    bo.outlineOpacity = finite(bo.outlineOpacity, 0.85f, 0.05f, 1.0f);
    bo.fillOpacity = finite(bo.fillOpacity, 0.14f, 0.0f, 0.6f);
    bo.lineWidth = finite(bo.lineWidth, 1.6f, 0.5f, 5.0f);
    bo.smoothSpeed = finite(bo.smoothSpeed, 14.0f, 2.0f, 35.0f);
  }

  private static void validateEnchantGlint(EnchantGlint eg) {
    if (!Arrays.asList("theme", "custom", "preset", "chroma").contains(eg.colorMode))
      eg.colorMode = "custom";
    if (!Arrays.asList("amethyst", "crimson", "cyan", "gold", "emerald", "sakura", "white")
             .contains(eg.preset))
      eg.preset = "amethyst";
    eg.color = opaque(eg.color, 0xff7c5cff);
    eg.intensity = finite(eg.intensity, 1.0f, 0.0f, 1.5f);
    eg.speed = finite(eg.speed, 1.0f, 0.1f, 3.0f);
  }

  private static void validateExplosionRing(ExplosionRing er) {
    er.fillOpacity = finite(er.fillOpacity, 0.12f, 0.0f, 0.45f);
    er.customTntRadius = finite(er.customTntRadius, 0.0f, 0.0f, 24.0f);
    if (er.customTntRadius > 0.0f && er.customTntRadius < 3.0f) er.customTntRadius = 3.0f;
    er.damageMultiplier = finite(er.damageMultiplier, 1.0f, 0.5f, 4.0f);
    er.lineWidth = finite(er.lineWidth, 2.0f, 0.8f, 5.0f);
    er.maxDistance = finite(er.maxDistance, 48.0f, 12.0f, 96.0f);
  }

  private static void validateTooltips(Tooltips tt) {
    if (!Arrays.asList("hw", "glass", "vanilla").contains(tt.style)) tt.style = "hw";
    if (!Arrays.asList("compact", "full").contains(tt.peekMode)) tt.peekMode = "compact";
    if (!Arrays.asList("always", "shift").contains(tt.peekTrigger)) tt.peekTrigger = "always";
  }

  private static void validateMinimap(Minimap mm) {
    validatePanel(mm, 116f, 132f);
    mm.scale = finite(mm.scale, 1.0f, 0.5f, 2.0f);
    if (!Arrays.asList("square", "circle", "sharp", "diamond", "hexagon", "octagon")
             .contains(mm.shape)) {
      mm.shape = "square";
    }
    mm.size = finite(mm.size, 116f, 72f, 240f);
    mm.zoom = finite(mm.zoom, 1.0f, 0.5f, 4.0f);
    if (!Arrays.asList("classic", "vivid", "contour", "flat").contains(mm.reliefStyle)) {
      mm.reliefStyle = "classic";
    }
    mm.reliefStrength = finite(mm.reliefStrength, 1.35f, 0.5f, 4.0f);
    if (!Arrays.asList("classic", "modern", "pointer", "head", "dot").contains(mm.arrowStyle)) {
      mm.arrowStyle = "classic";
    }
    mm.arrowScale = finite(mm.arrowScale, 1.0f, 0.6f, 1.8f);
    mm.arrowColor = opaque(mm.arrowColor, 0xffffffff);
    if (!"toggle".equals(mm.enlargeMode)) mm.enlargeMode = "hold";
    mm.enlargeSize = finite(mm.enlargeSize, 260f, 160f, 420f);
    mm.enlargeZoom = finite(mm.enlargeZoom, 0.65f, 0.35f, 1.5f);
    mm.playerScale = finite(mm.playerScale, 1.0f, 0.6f, 1.8f);
    if (!"dots".equals(mm.itemStyle)) mm.itemStyle = "icons";
    mm.itemScale = finite(mm.itemScale, 0.95f, 0.6f, 1.6f);
    if (!"dots".equals(mm.mobIconStyle)) mm.mobIconStyle = "icons";
    mm.mobScale = finite(mm.mobScale, 1.0f, 0.6f, 1.8f);
    mm.frameOpacity = finite(mm.frameOpacity, 0.85f, 0.0f, 1.0f);
    mm.width = mm.size;
    mm.height = mm.size + (mm.showCoords ? 16f : 0f);
  }

  private static void validateWorldMap(WorldMap wm) {
  }

  private static void validatePlayerPing(PlayerPing pingCfg) {
    if (!"text".equals(pingCfg.style)) pingCfg.style = "badge";
    pingCfg.height = finite(pingCfg.height, 0.14f, -0.5f, 1.5f);
    pingCfg.scale = finite(pingCfg.scale, 1.0f, 0.6f, 1.6f);
    pingCfg.maxDistance = Math.max(8, Math.min(128, pingCfg.maxDistance));
  }

  private static void validateSafeCtLeave(SafeCtLeave ct) {
    ct.fallbackSeconds = Math.max(5, Math.min(30, ct.fallbackSeconds));
    ct.dangerRadius = finite(ct.dangerRadius, 6.5f, 3.0f, 16.0f);
  }

  private static void validateBetterChat(BetterChat chat) {
    validatePanel(chat, 240f, 96f);
    chat.scale = finite(chat.scale, 1.0f, 0.5f, 2.0f);
    if (chat.anchorY >= 0.99f && chat.offsetY >= 0f && chat.offsetY <= 48f
        && chat.layoutOrder == 0 && Math.abs(chat.offsetX - 4f) < 0.01f) {
      chat.offsetY = -40f - chat.offsetY;
    }
    if (!Arrays.asList("rounded", "transparent", "compact", "vanilla").contains(chat.backgroundStyle))
      chat.backgroundStyle = "rounded";
    chat.backgroundOpacity = finite(chat.backgroundOpacity, 0.45f, 0.0f, 1.0f);
    chat.animationSpeed = finite(chat.animationSpeed, 1.0f, 0.5f, 2.5f);
    chat.historyLimit = Math.max(100, Math.min(2000, chat.historyLimit));
  }

  private static void validateCustomSky(CustomSky sky) {
    if (!Arrays.asList("vanilla", "galaxy", "aurora", "sunset", "anime", "blood_moon", "pastel")
             .contains(sky.style))
      sky.style = "galaxy";
    if (!Arrays.asList("server", "day", "sunset", "night", "midnight", "custom")
             .contains(sky.timeMode))
      sky.timeMode = "server";
    sky.customTime = Math.max(0, Math.min(24000, sky.customTime));
    sky.topColor = opaque(sky.topColor, 0xff090d24);
    sky.horizonColor = opaque(sky.horizonColor, 0xff2b1e56);
    sky.accentColor = opaque(sky.accentColor, 0xff8b5cf6);
    sky.brightness = finite(sky.brightness, 100f, 30f, 160f);
    sky.rotationSpeed = finite(sky.rotationSpeed, 40f, 0f, 200f);
    sky.stars = finite(sky.stars, 75f, 0f, 100f);
    sky.clouds = finite(sky.clouds, 55f, 0f, 100f);
    sky.celestialSize = finite(sky.celestialSize, 100f, 40f, 220f);
    sky.celestialHeight = finite(sky.celestialHeight, 32f, 5f, 75f);
    sky.horizonGlow = finite(sky.horizonGlow, 65f, 0f, 100f);
  }

  private static void validateAtmosphere(Atmosphere a) {
    if ("japan".equals(a.mode)) a.mode = "sakura";
    else if ("halloween".equals(a.mode)) a.mode = "mystic";
    if (!Arrays.asList("dawn", "dust", "snow", "sakura", "autumn", "fireflies", "mystic")
             .contains(a.mode))
      a.mode = "dawn";
    a.density = finite(a.density, 0.35f, 0.05f, 0.80f);
    a.scatterHeight = finite(a.scatterHeight, 76f, 60f, 120f);
    a.godRays = finite(a.godRays, 75f, 0f, 100f);
    a.softness = finite(a.softness, 60f, 0f, 100f);
    a.dawnColor = opaque(a.dawnColor, 0xffffad7a);

    a.amount = finite(a.amount, 50f, 0f, 100f);
    a.ground = finite(a.ground, 100f, 0f, 200f);
    a.speed = finite(a.speed, 100f, 10f, 300f);
    a.haze = finite(a.haze, 50f, 0f, 100f);
    a.grade = finite(a.grade, 70f, 0f, 100f);
    a.vignette = finite(a.vignette, 40f, 0f, 100f);
    if (!Arrays.asList("low", "medium", "high").contains(a.quality)) a.quality = "medium";

    if (a.snowCover == null) a.snowCover = new Atmosphere.SnowCover();
    a.snowCover.frost = finite(a.snowCover.frost, 40f, 0f, 100f);
    a.snowCover.shelter = finite(a.snowCover.shelter, 10f, 0f, 100f);
    a.snowCover.coldShadows = finite(a.snowCover.coldShadows, 60f, 0f, 100f);
    a.snowCover.drifts = finite(a.snowCover.drifts, 50f, 0f, 100f);
    a.snowCover.thickness = finite(a.snowCover.thickness, 50f, 0f, 100f);
    a.snowCover.texture = finite(a.snowCover.texture, 50f, 0f, 100f);
    a.snowCover.sparkle = finite(a.snowCover.sparkle, 50f, 0f, 100f);
    a.snowCover.leaves = finite(a.snowCover.leaves, 60f, 0f, 100f);
    a.snowCover.ice = finite(a.snowCover.ice, 85f, 0f, 100f);
    a.snowCover.cloudShadows = finite(a.snowCover.cloudShadows, 30f, 0f, 100f);

    if (a.snowfall == null) a.snowfall = new Atmosphere.Snowfall();
    a.snowfall.flakeSize = finite(a.snowfall.flakeSize, 100f, 50f, 200f);
    a.snowfall.clumps = finite(a.snowfall.clumps, 25f, 0f, 100f);
    a.snowfall.wind = finite(a.snowfall.wind, 30f, 0f, 100f);
    a.snowfall.spindrift = finite(a.snowfall.spindrift, 40f, 0f, 100f);
    a.snowfall.farSnow = finite(a.snowfall.farSnow, 50f, 0f, 100f);

    if (a.sakura == null) a.sakura = new Atmosphere.Sakura();
    a.sakura.blossom = finite(a.sakura.blossom, 70f, 0f, 100f);
    a.sakura.blossomColor = opaque(a.sakura.blossomColor, 0xfff6c3d5);
    a.sakura.wholeFlowers = finite(a.sakura.wholeFlowers, 5f, 0f, 30f);
    a.sakura.gusts = finite(a.sakura.gusts, 40f, 0f, 100f);
    a.sakura.nearTrees = finite(a.sakura.nearTrees, 60f, 0f, 100f);
    a.sakura.hotaru = finite(a.sakura.hotaru, 50f, 0f, 100f);

    if (a.sakuraWater == null) a.sakuraWater = new Atmosphere.SakuraWater();
    a.sakuraWater.petals = finite(a.sakuraWater.petals, 50f, 0f, 100f);
    a.sakuraWater.moonPath = finite(a.sakuraWater.moonPath, 60f, 0f, 100f);
    a.sakuraWater.reflections = finite(a.sakuraWater.reflections, 100f, 0f, 100f);
    a.sakuraWater.mist = finite(a.sakuraWater.mist, 35f, 0f, 100f);

    if (a.sakuraSky == null) a.sakuraSky = new Atmosphere.SakuraSky();
    a.sakuraSky.clouds = finite(a.sakuraSky.clouds, 35f, 0f, 100f);
    a.sakuraSky.stars = finite(a.sakuraSky.stars, 100f, 0f, 100f);

    if (a.autumnTrees == null) a.autumnTrees = new Atmosphere.AutumnTrees();
    a.autumnTrees.treeColors = finite(a.autumnTrees.treeColors, 70f, 0f, 100f);
    a.autumnTrees.bareTrees = finite(a.autumnTrees.bareTrees, 25f, 0f, 100f);

    if (a.autumnGround == null) a.autumnGround = new Atmosphere.AutumnGround();
    a.autumnGround.piles = finite(a.autumnGround.piles, 50f, 0f, 100f);
    a.autumnGround.dryGrass = finite(a.autumnGround.dryGrass, 40f, 0f, 100f);
    a.autumnGround.puddles = finite(a.autumnGround.puddles, 20f, 0f, 100f);
    a.autumnGround.dew = finite(a.autumnGround.dew, 30f, 0f, 100f);

    if (a.autumnAir == null) a.autumnAir = new Atmosphere.AutumnAir();
    a.autumnAir.leafGlow = finite(a.autumnAir.leafGlow, 60f, 0f, 100f);
    a.autumnAir.whirls = finite(a.autumnAir.whirls, 40f, 0f, 100f);
    a.autumnAir.skitter = finite(a.autumnAir.skitter, 50f, 0f, 100f);
    a.autumnAir.gossamer = finite(a.autumnAir.gossamer, 40f, 0f, 100f);

    if (a.autumnSky == null) a.autumnSky = new Atmosphere.AutumnSky();
    a.autumnSky.clouds = finite(a.autumnSky.clouds, 40f, 0f, 100f);
    a.autumnSky.birds = finite(a.autumnSky.birds, 50f, 0f, 100f);
    a.autumnSky.mist = finite(a.autumnSky.mist, 35f, 0f, 100f);
    a.autumnSky.sunHeight = finite(a.autumnSky.sunHeight, 17f, 5f, 35f);
    a.autumnSky.sunRays = finite(a.autumnSky.sunRays, 60f, 0f, 100f);

    if (a.mysticSky == null) a.mysticSky = new Atmosphere.MysticSky();
    a.mysticSky.moonColor = opaque(a.mysticSky.moonColor, 0xfff3e6cc);
    a.mysticSky.moonSize = finite(a.mysticSky.moonSize, 100f, 40f, 250f);
    a.mysticSky.moonHeight = finite(a.mysticSky.moonHeight, 18f, 6f, 60f);
    a.mysticSky.halo = finite(a.mysticSky.halo, 40f, 0f, 100f);
    a.mysticSky.moonRays = finite(a.mysticSky.moonRays, 45f, 0f, 100f);
    a.mysticSky.stars = finite(a.mysticSky.stars, 50f, 0f, 100f);
    a.mysticSky.meteors = finite(a.mysticSky.meteors, 30f, 0f, 100f);
    a.mysticSky.clouds = finite(a.mysticSky.clouds, 45f, 0f, 100f);
    a.mysticSky.cloudShadows = finite(a.mysticSky.cloudShadows, 60f, 0f, 100f);
    a.mysticSky.lightning = finite(a.mysticSky.lightning, 20f, 0f, 100f);
    a.mysticSky.silhouettes = finite(a.mysticSky.silhouettes, 60f, 0f, 100f);

    if (a.mysticFog == null) a.mysticFog = new Atmosphere.MysticFog();
    a.mysticFog.groundFog = finite(a.mysticFog.groundFog, 55f, 0f, 100f);
    a.mysticFog.fogHeight = finite(a.mysticFog.fogHeight, 2.0f, 0.5f, 6.0f);
    a.mysticFog.fogTendrils = finite(a.mysticFog.fogTendrils, 50f, 0f, 100f);
    a.mysticFog.fogColor = opaque(a.mysticFog.fogColor, 0xff59667a);

    if (a.mysticWorld == null) a.mysticWorld = new Atmosphere.MysticWorld();
    a.mysticWorld.moonlight = finite(a.mysticWorld.moonlight, 100f, 0f, 200f);
    a.mysticWorld.coldShadows = finite(a.mysticWorld.coldShadows, 65f, 0f, 100f);
    a.mysticWorld.blueNight = finite(a.mysticWorld.blueNight, 60f, 0f, 100f);
    a.mysticWorld.wither = finite(a.mysticWorld.wither, 70f, 0f, 100f);
    a.mysticWorld.wetness = finite(a.mysticWorld.wetness, 45f, 0f, 100f);
    a.mysticWorld.puddles = finite(a.mysticWorld.puddles, 40f, 0f, 100f);
    a.mysticWorld.reflections = finite(a.mysticWorld.reflections, 70f, 0f, 100f);

    if (a.mysticLens == null) a.mysticLens = new Atmosphere.MysticLens();
    a.mysticLens.bokeh = finite(a.mysticLens.bokeh, 30f, 0f, 100f);
    a.mysticLens.grain = finite(a.mysticLens.grain, 50f, 0f, 100f);

    if (a.mysticLife == null) a.mysticLife = new Atmosphere.MysticLife();
    a.mysticLife.bats = finite(a.mysticLife.bats, 40f, 0f, 100f);
    a.mysticLife.batColony = finite(a.mysticLife.batColony, 35f, 0f, 100f);
    a.mysticLife.wispsColor = opaque(a.mysticLife.wispsColor, 0xff8ce6ff);
    a.mysticLife.wispCount = Math.max(1, Math.min(4, a.mysticLife.wispCount));
    a.mysticLife.webs = finite(a.mysticLife.webs, 35f, 0f, 100f);
    a.mysticLife.ghosts = finite(a.mysticLife.ghosts, 35f, 0f, 100f);

    if (a.mysticWind == null) a.mysticWind = new Atmosphere.MysticWind();
    a.mysticWind.gusts = finite(a.mysticWind.gusts, 50f, 0f, 100f);
    a.mysticWind.skitter = finite(a.mysticWind.skitter, 50f, 0f, 100f);
    a.mysticWind.leafSize = finite(a.mysticWind.leafSize, 100f, 50f, 200f);
  }

  private static String cleanCosmeticValue(String value, int maxLen) {
    if (value == null) return "";
    String cleaned = value.replace("\0", "").replaceAll("[\\p{Cntrl}]", "").trim();
    return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
  }

  private static int opaque(int color, int fallback) {
    return (color & 0xffffff) | 0xff000000;
  }

  private static void validatePanel(Panel p, float width, float height) {
    p.layoutOrder = Math.max(0, p.layoutOrder);
    p.scale = finite(p.scale, 1, .1f, 4);
    p.opacity = finite(p.opacity, 1, 0, 1);
    p.anchorX = finite(p.anchorX, 0, 0, 1);
    p.anchorY = finite(p.anchorY, 0, 0, 1);
    p.offsetX = finite(p.offsetX, 20, -8192, 8192);
    p.offsetY = finite(p.offsetY, -20, -8192, 8192);
    p.width = finite(p.width, width, 48, 800);
    p.height = finite(p.height, height, Math.min(20f, height), 800);
  }

  private static void validateHand(HandTransform h) {
    h.x = finite(h.x, 0, -4, 4);
    h.y = finite(h.y, 0, -4, 4);
    h.z = finite(h.z, 0, -4, 4);
    h.rotateX = finite(h.rotateX, 0, -360, 360);
    h.rotateY = finite(h.rotateY, 0, -360, 360);
    h.rotateZ = finite(h.rotateZ, 0, -360, 360);
    h.scaleX = finite(h.scaleX, 1, .05f, 8);
    h.scaleY = finite(h.scaleY, 1, .05f, 8);
    h.scaleZ = finite(h.scaleZ, 1, .05f, 8);
  }
}
