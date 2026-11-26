package dev.nightclient.ui.skin;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TntView;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import dev.nightclient.config.Waypoint;
import dev.nightclient.renderer.Icons;
import dev.nightclient.renderer.SurfacePainter;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.FeatureHudRenderer;
import dev.nightclient.ui.HudRenderer;
import dev.nightclient.ui.UiActions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SkinView {
  private dev.nightclient.renderer.ViewmodelGizmoRenderer viewmodelGizmoRenderer;
  private static final int MAX_FRAMES = 8192;
  private static final java.util.regex.Pattern ELEMENT_BIND = java.util.regex.Pattern.compile(
                                                   "[A-Za-z_][A-Za-z0-9_.-]*"),
                                               RESOURCE_ID = java.util.regex.Pattern.compile(
                                                   "[a-z0-9_.-]+:[a-z0-9_./-]+"),
                                               SPACE = java.util.regex.Pattern.compile("\\s+");
  private final java.util.IdentityHashMap<Map<String, String>, Map<String, String>>
      visualStyles = new java.util.IdentityHashMap<Map<String, String>, Map<String, String>>(),
      boundStyles = new java.util.IdentityHashMap<Map<String, String>, Map<String, String>>();
  private static final Map<String, float[]> PADDING_CACHE =
      java.util.Collections.synchronizedMap(new LinkedHashMap<String, float[]>(32, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> entry) {
          return size() > 256;
        }
      });
  private static final String[] INHERITED_STYLES = {
      "color", "font-size", "font-weight", "font-family"};
  private static final String[] ATTRIBUTE_STYLES = {"x", "y", "top", "bottom", "left", "right",
      "width", "height", "padding", "gap", "direction", "layout", "overflow", "font-size", "color",
      "background", "radius"};
  private static final java.util.concurrent.ConcurrentHashMap<String, ParsedDimension> DIMENSIONS =
      new java.util.concurrent.ConcurrentHashMap<String, ParsedDimension>();
  private final java.util.IdentityHashMap<SkinDocument.Node, NodePlan> nodePlans =
      new java.util.IdentityHashMap<SkinDocument.Node, NodePlan>();
  private String[] themeValues = new String[32];
  private final SkinDocument document;
  private final SkinDocument.ThemeResolver themeResolver;
  private final Settings settings;
  private final UiActions actions;
  private final SkinBindings bindings;
  private final UiCatalog catalog;
  private final HudDataCache hudData = new HudDataCache();
  private final Map<String, Object> ui;
  private final Map<String, ColorPickerModel> colorModels;
  private final Map<String, Motion> motion = new HashMap<String, Motion>();
  private final Map<String, Map<String, Object>> localStates =
      new LinkedHashMap<String, Map<String, Object>>();
  private final Map<String, Masonry> masonry = new HashMap<String, Masonry>();
  private final Map<String, RepeatMemory> retainedRepeats =
      new LinkedHashMap<String, RepeatMemory>();
  private final ModuleSearchIndex searchIndex = new ModuleSearchIndex();
  private ModuleSearchIndex.SearchPlan activeSearchPlan = ModuleSearchIndex.SearchPlan.EMPTY;
  private String searchAutoQuery = "";
  private final Set<String> searchManualCollapsed = new HashSet<String>();
  private boolean pendingSearchScroll;

  private long retentionGeneration;
  private final Map<String, MotionTrack> scrollMotion = new HashMap<String, MotionTrack>();
  private final Map<String, ValueMotion> sliderMotion = new HashMap<String, ValueMotion>();
  private final Map<String, Bounds> paintedBoxes = new HashMap<String, Bounds>();
  private final java.util.IdentityHashMap<SkinDocument.Node, Integer> nodeIds =
      new java.util.IdentityHashMap<SkinDocument.Node, Integer>();
  private String selectedModule = "saturation", selectedHud = "hud", pendingSelection = "",
                 tooltip = "", tooltipKey = "", hoveredTooltipKey = "", notification = "",
                 colorBind = "", colorLabel = "";
  private double notificationUntil, tooltipSince;
  private Bounds colorAnchor, tooltipAnchor;
  private boolean pixelDrawing, pixelErase;
  private int pixelLastX, pixelLastY, brushColor = 0xffffffff;
  private float crosshairTargetProgress = 0f;
  private double crosshairTargetTime = Double.NaN;
  private Hit pixelHit;
  private Host host;
  private int componentMountDepth;
  private final java.util.Set<String> mounting = new java.util.HashSet<String>();
  private List<String> profileCache;
  private boolean entrance, menuScope, paintingGhost, suppressMotion, standaloneWaypoint,
      waypointReturnList, standaloneAccount;
  private String lastSyncedCustomCape = "", lastSyncedCustomSkin = "",
      lastSyncedCustomWeapon = "", lastSyncedCustomArmor = "";
  private String renderScope = "";
  private String pressedKey = "";
  private long lastPolicyRevision = -1L;

  public interface Host {
    boolean action(String action);

    default boolean widget(
        String name, Canvas canvas, float x, float y, float width, float height, Theme theme) {
      return false;
    }

    default Map<String, Object> context() {
      return Collections.emptyMap();
    }

    default void bindingChanged(String bind) {}
  }

  private String hudDragId = "";
  private final HudAutoLayout.Cache hudPlacementCache = new HudAutoLayout.Cache();
  private float hudDragX, hudDragY;
  public void hudPlacementDrag(String id, float x, float y) {
    hudDragId = id == null ? "" : id;
    hudDragX = x;
    hudDragY = y;
    cachedHud = null;
  }
  public void hudPlacementDragEnd() {
    hudDragId = "";
    cachedHud = null;
  }

  public void host(Host host) {
    this.host = host;
  }

  public String page() {
    return page;
  }

  public boolean hasInteractiveHit(float x, float y) {
    for (int i = hits.size() - 1; i >= 0; i--)
      if (hits.get(i).clip.contains(x, y)) return true;
    return false;
  }

  public float[] elementBounds(String id) {
    for (Hit hit : hits)
      if (id.equals(hit.id) && hit.clip.w > 0 && hit.clip.h > 0)
        return new float[] {hit.clip.x, hit.clip.y, hit.clip.w, hit.clip.h};
    return null;
  }

  private boolean hwEventsTabVisible() {
    return !FeaturePolicy.isBlocked("hwhelper.events")
        && settings.hwhelper != null && settings.hwhelper.enabled
        && settings.hwhelper.events != null && settings.hwhelper.events.enabled;
  }

  private void syncPolicyRevision() {
    long rev = FeaturePolicy.revision();
    if (rev == lastPolicyRevision) return;
    lastPolicyRevision = rev;
    menuFrameCache.clear();
    hudFrameCache.clear();
    cachedHud = null;
    if ("waypoints".equals(page) && FeaturePolicy.isBlocked("waypoints")) {
      page = "all";
      ui.put("waypointManager", "");
    }
    if ("events".equals(page) && !hwEventsTabVisible()) {
      page = "player";
    }
    if (FeaturePolicy.isBlocked(selectedModule)) {
      List<UiCatalog.Module> mods = catalog.modules(false);
      selectedModule = mods.isEmpty() ? "" : mods.get(0).id;
      ui.put("moduleOpen", false);
    }
    if (FeaturePolicy.isBlocked(selectedHud)) {
      List<UiCatalog.Module> mods = catalog.modules(true);
      selectedHud = mods.isEmpty() ? "" : mods.get(0).id;
    }
  }

  public void page(String page) {
    if ("waypoints".equals(page) && FeaturePolicy.isBlocked("waypoints")) page = "all";
    if ("events".equals(page) && !hwEventsTabVisible()) page = "player";
    boolean changed = !java.util.Objects.equals(this.page, page);
    this.page = page;
    if ("events".equals(page) && changed) actions.refreshHwEvents();
    previewDragging = false;
    previewYawVelocity = 0;
    previewLastTime = -1;
    ui.put("moduleOpen", false);
    if (changed && menuScope) {
      Integer listId = document.menu() == null ? null : nodeIdByDomId(document.menu(), "modules-list");
      if (listId != null) {
        String prefix = listId + ":";
        scroll.keySet().removeIf(k -> k.startsWith(prefix));
        scrollMotion.keySet().removeIf(k -> k.startsWith(prefix));
      }
    }
    if (!standaloneWaypoint) {
      waypointReturnList = "waypoints".equals(page);
      ui.put("waypointManager", "");
      ui.put("waypointDraftId", "");
      ui.put("waypointDeleteConfirm", false);
    }
    if (!standaloneAccount) {
      ui.put("accountManager", "");
      ui.put("accountDraftId", "");
    }
    focused = null;
    dragged = null;
    colorBind = "";
    pendingBind = pendingValue = "";
    menuFrameCache.clear();
  }

  private Integer nodeIdByDomId(SkinDocument.Node node, String id) {
    if (id.equals(node.id)) return nodeIds.get(node);
    for (int i = 0, size = node.children.size(); i < size; i++) {
      Integer found = nodeIdByDomId(node.children.get(i), id);
      if (found != null) return found;
    }
    return null;
  }

  public void openWaypointManager() {
    standaloneWaypoint = false;
    ui.put("waypointManager", "list");
    ui.put("waypointDeleteConfirm", false);
    ui.put("waypointDraftId", "");
  }

  public void openWaypointCreate() {
    standaloneWaypoint = true;
    startWaypointDraft(null);
  }

  public void openWaypointCreate(double x, double y, double z) {
    standaloneWaypoint = true;
    startWaypointDraft(null);
    ui.put("waypointDraftX", formatCoordinate(x));
    ui.put("waypointDraftY", formatCoordinate(y));
    ui.put("waypointDraftZ", formatCoordinate(z));
  }

  public boolean isWaypointManagerOpen() {
    return !String.valueOf(ui.get("waypointManager")).isEmpty();
  }

  public void openAccountManager(boolean standalone) {
    standaloneAccount = standalone;
    actions.clearAccountError();
    ui.put("accountManager", "list");
    ui.put("accountDraftId", "");
    ui.put("accountDraftName", "");
    ui.put("accountDraftNote", "");
    ui.put("accountDraftSkin", "");
    syncSelectedAccountToActive();
    menuFrameCache.clear();
  }

  public boolean isAccountManagerOpen() {
    return !String.valueOf(ui.get("accountManager")).isEmpty();
  }

  private void syncSelectedAccountToActive() {
    if (actions.isLauncherAccountActive()) {
      ui.put("accountSelectedId", "");
      return;
    }
    String active = actions.activeAccountName();
    if (active != null && !active.isEmpty()) {
      for (dev.nightclient.config.SavedAccount acc : actions.savedAccounts()) {
        if (acc.name.equalsIgnoreCase(active)) {
          ui.put("accountSelectedId", acc.id);
          return;
        }
      }
    }
    ui.put("accountSelectedId", "");
  }

  public boolean renderMenuOverlay(
      String id, Canvas canvas, int width, int height, float mouseX, float mouseY, double time) {
    viewportWidth = width;
    viewportHeight = height;
    menuScope = true;
    beginFrame(mouseX, mouseY, time);
    SkinDocument.Node root = document.menu();
    if (root == null) return false;
    SkinDocument.Node target = nodeById(root, id);
    if (target == null) return false;
    if (menuNeedsHudData()) {
      cooldowns = safe(actions.cooldowns());
      inventory = safe(actions.inventory());
      tnts = safe(actions.tnts());
    } else {
      cooldowns = Collections.emptyList();
      inventory = Collections.emptyList();
      tnts = Collections.emptyList();
    }
    Map<String, Object> values = context(width, height);
    SkinDocument.Node scrim =
        nodeById(root, "account-manager".equals(id) ? "account-scrim" : "waypoint-scrim");
    if (scrim != null) renderRoot(scrim, canvas, 0, 0, width, height, values);
    renderRoot(target, canvas, 0, 0, width, height, values);
    paintOverlay(canvas, width, height);
    endFrame();
    return true;
  }

  private SkinDocument.Node nodeById(SkinDocument.Node node, String id) {
    if (id.equals(node.id)) return node;
    for (SkinDocument.Node child : node.children) {
      SkinDocument.Node found = nodeById(child, id);
      if (found != null) return found;
    }
    return null;
  }

  public void selectModule(String module) {
    if (FeaturePolicy.isBlocked(module)) return;
    selectedModule = module;
    pendingSelection = module;
    UiCatalog.Module selected = catalog.module(module);
    page = selected == null                    ? "all"
        : selected.hud                         ? "hud"
        : "render".equals(selected.category) ? "features"
                                               : selected.category;
    ui.put("search", "");
    ui.put("moduleOpen", true);
    menuFrameCache.clear();
  }

  public void enableMotion() {
    entrance = true;
  }

  public void resetMotion() {
    motion.clear();
    sliderMotion.clear();
    menuFrameCache.clear();
    entrance = true;
  }

  public String searchQuery() {
    Object value = ui.get("search");
    return value instanceof String ? (String) value : "";
  }

  public void searchQuery(String value) {
    ui.put("search", value == null ? "" : value);
    menuFrameCache.clear();
  }

  public String capeSearchQuery() {
    Object value = ui.get("cosmeticsCapeQuery");
    return value instanceof String ? (String) value : "";
  }

  public void capeSearchQuery(String value) {
    ui.put("cosmeticsCapeQuery", value == null ? "" : value);
    menuFrameCache.clear();
  }

  public void onClose() {
    if (liveBindingDirty) {
      liveBindingDirty = false;
      actions.changed();
    }
    frameActive = frameHudDataLoaded = false;
    Object savedSearch = ui.get("search");
    Object savedCapeQuery = ui.get("cosmeticsCapeQuery");
    profileCache = null;
    cachedHud = null;
    hudFrameCache.clear();
    menuFrameCache.clear();
    motion.clear();
    sliderMotion.clear();
    scrollMotion.clear();
    scroll.clear();
    masonry.clear();
    retainedRepeats.clear();
    localStates.clear();
    searchManualCollapsed.clear();
    searchAutoQuery = "";
    activeSearchPlan = ModuleSearchIndex.SearchPlan.EMPTY;
    pendingSearchScroll = false;
    entrance = false;
    previewDragging = false;
    previewYawVelocity = 0;
    previewLastTime = -1;
    standaloneWaypoint = false;
    waypointReturnList = false;
    standaloneAccount = false;
    notification = "";
    notificationUntil = 0;
    ui.put("profileName", "");
    ui.put("brushColor", 0xffffffff);
    ui.put("crosshairLayer", "normal");
    ui.put("deleteProfile", "");
    ui.put("moreStyle", false);
    ui.put("waypointEdit", "");
    ui.put("waypointName", "");
    ui.put("waypointIcon", "");
    ui.put("waypointManager", "");
    ui.put("waypointDraftId", "");
    ui.put("waypointDraftName", "");
    ui.put("waypointDraftX", "0.0");
    ui.put("waypointDraftY", "0.0");
    ui.put("waypointDraftZ", "0.0");
    ui.put("waypointDraftColor", "#FFFFFFFF");
    ui.put("waypointDraftDimension", "minecraft:overworld");
    ui.put("waypointScope", "world");
    ui.put("waypointDeleteConfirm", false);
    ui.put("accountManager", "");
    ui.put("accountSearch", "");
    ui.put("accountSelectedId", "");
    ui.put("accountDraftId", "");
    ui.put("accountDraftName", "");
    ui.put("accountDraftNote", "");
    ui.put("accountDraftSkin", "");
    ui.put("soundBlockDraft", "");
    ui.put("cosmeticsTab", "capes");
    ui.put("cosmeticsCapeFilter", "all");
    ui.put("cosmeticsCapeQuery", "");
    ui.put("cosmeticsNickDraft",
        settings.cosmetics == null || settings.cosmetics.skinNickname == null
            ? ""
            : settings.cosmetics.skinNickname);
    ui.put("cosmeticsCapePathDraft",
        settings.cosmetics == null || settings.cosmetics.customCapePath == null
            ? ""
            : settings.cosmetics.customCapePath);
    ui.put("cosmeticsSkinPathDraft",
        settings.cosmetics == null || settings.cosmetics.customSkinPath == null
            ? ""
            : settings.cosmetics.customSkinPath);
    ui.put("cosmeticsWeaponPathDraft",
        settings.cosmetics == null || settings.cosmetics.customWeaponPath == null
            ? ""
            : settings.cosmetics.customWeaponPath);
    ui.put("cosmeticsArmorPathDraft",
        settings.cosmetics == null || settings.cosmetics.customArmorPath == null
            ? ""
            : settings.cosmetics.customArmorPath);
    ui.putAll(document.stateDefaults());
    if (savedSearch instanceof String && !((String) savedSearch).isEmpty())
      ui.put("search", savedSearch);
    if (savedCapeQuery instanceof String && !((String) savedCapeQuery).isEmpty())
      ui.put("cosmeticsCapeQuery", savedCapeQuery);
    if (settings.interfaceState != null)
      ui.put("sidebarCollapsed", settings.interfaceState.sidebarCollapsed);
  }

  public void resetInteraction() {
    focused = dragged = pixelHit = null;
    pressedKey = "";
    draggingScroll = pixelDrawing = false;
    pendingBind = pendingValue = "";
    colorBind = colorLabel = "";
    colorAnchor = null;
    tooltipAnchor = null;
    tooltip = tooltipKey = hoveredTooltipKey = "";
    hits.clear();
    popups.clear();
    layoutBoxes.clear();
    paintedBoxes.clear();
    menuFrameCache.clear();
  }

  private final List<Hit> hits = new ArrayList<Hit>();
  private final List<Popup> popups = new ArrayList<Popup>();
  private final Map<String, float[]> layoutBoxes = new HashMap<String, float[]>();
  private final Map<String, float[]> layoutBoxPool = new HashMap<String, float[]>();

  private void recordLayoutBox(String id, float x, float y, float w, float h) {
    float[] box = layoutBoxPool.get(id);
    if (box == null) {
      box = new float[4];
      layoutBoxPool.put(id, box);
    }
    box[0] = x;
    box[1] = y;
    box[2] = w;
    box[3] = h;
    layoutBoxes.put(id, box);
  }

  public float[] layoutBounds(String id) {
    float[] box = layoutBoxes.get(id);
    return box == null ? null : box.clone();
  }

  private final Map<String, Float> scroll = new HashMap<String, Float>();
  private List<Cooldown> cooldowns = Collections.emptyList();
  private List<InventorySlot> inventory = Collections.emptyList();
  private List<TntView> tnts = Collections.emptyList();
  private static final List<String> WHEEL_PAGES = Collections.unmodifiableList(
      Arrays.asList("all", "features", "camera", "world", "player", "waypoints", "hud", "settings"));
  private String page = "features";
  private String previewBackPage = "features";
  private boolean previewAuto = false, previewDragging;
  private float previewYaw = -22f, previewPitch = 9f, previewZoom = 1.12f;
  private float previewYawVelocity, previewPitchTarget = 9f;
  private float previewLastX, previewLastY;
  private double previewLastTime = -1;
  private Hit focused, dragged;
  private String pendingBind = "", pendingValue = "";
  private boolean draggingScroll;
  private float scrollGrab;
  private String inputBuffer = "";
  private int caret, selection;
  private float inputScroll;
  private float mouseX = -1, mouseY = -1;
  private double time;
  private int frames;
  private int drawCommands;
  private float hudExitDuration;
  private final HudFrameCache hudFrameCache = new HudFrameCache();
  private final HudFrameCache menuFrameCache = new HudFrameCache();
  private int hudFrameCacheHits;
  private boolean hudFrameDynamic;
  private SkinDocument hudScanDocument, menuHudDataDocument;
  private long hudScanThemeGeneration = -1;
  private boolean hudScanStatic, menuHudDataNeeded;
  private int renderRootDepth;
  private final FastStringSet scratchActiveKeys = new FastStringSet(128);
  private final List<Frame> scratchRetiringFrames = new ArrayList<Frame>();
  private final List<Frame> scratchOverlays = new ArrayList<Frame>();

  private static final class FastStringSet {
    private String[] table;
    private int size;

    FastStringSet(int initialCapacity) {
      int cap = 16;
      while (cap < initialCapacity) cap <<= 1;
      table = new String[cap];
    }

    void clear() {
      if (size > 0) {
        java.util.Arrays.fill(table, null);
        size = 0;
      }
    }

    void add(String key) {
      if (key == null) return;
      if ((size + 1) * 2 >= table.length) rehash();
      int mask = table.length - 1;
      int i = spread(key.hashCode()) & mask;
      while (true) {
        String existing = table[i];
        if (existing == null) {
          table[i] = key;
          size++;
          return;
        }
        if (existing == key || existing.equals(key)) return;
        i = (i + 1) & mask;
      }
    }

    boolean contains(String key) {
      if (key == null || size == 0) return false;
      int mask = table.length - 1;
      int i = spread(key.hashCode()) & mask;
      while (true) {
        String existing = table[i];
        if (existing == null) return false;
        if (existing == key || existing.equals(key)) return true;
        i = (i + 1) & mask;
      }
    }

    private void rehash() {
      String[] old = table;
      table = new String[old.length << 1];
      int mask = table.length - 1;
      for (int k = 0; k < old.length; k++) {
        String key = old[k];
        if (key == null) continue;
        int i = spread(key.hashCode()) & mask;
        while (table[i] != null) i = (i + 1) & mask;
        table[i] = key;
      }
    }

    private static int spread(int h) {
      return h ^ (h >>> 16);
    }
  }

  public float hudExitDuration() {
    return instantMotion() ? 0 : hudExitDuration;
  }

  private Canvas measuringCanvas;
  private Map<String, Object> frameValues;
  private boolean cameraCrosshairVisible = true, hudEditing;
  private int viewportWidth = 1, viewportHeight = 1;
  private final Set<Frame> retainedFrames = new HashSet<Frame>();
  private long renderGeneration;
  private final ThemeSnapshot themeSnapshot = new ThemeSnapshot();
  private long themeGeneration;
  private boolean cacheHudPass;
  private Object metricsKey;
  private Frame cachedHud;
  private double cachedHudTime = Double.NaN;
  private int cachedHudWidth, cachedHudHeight;
  private boolean cachedHudEditor;
  private boolean frameActive, frameHudDataLoaded, liveBindingDirty;

  public void invalidateLayout() {
    cachedHud = null;
    hudFrameCache.clear();
    menuFrameCache.clear();
    themeGeneration++;
  }

  int hudFrameCacheHits() {
    return hudFrameCacheHits;
  }

  public SkinView(SkinDocument document, Settings settings, UiActions actions) {
    this(document, settings, actions, new HashMap<String, Object>(),
        new HashMap<String, ColorPickerModel>());
  }

  private SkinView(SkinDocument document, Settings settings, UiActions actions,
      Map<String, Object> ui, Map<String, ColorPickerModel> colorModels) {
    if (document == null || settings == null || actions == null)
      throw new IllegalArgumentException("Skin view needs a document, settings, and actions");
    this.document = document;
    this.themeResolver = document.themeResolver();
    this.settings = settings;
    this.actions = actions;
    this.ui = ui;
    this.colorModels = colorModels;
    if (!ui.containsKey("profileName")) {
      ui.put("profileName", "");
      ui.put("brushColor", 0xffffffff);
      ui.put("crosshairLayer", "normal");
      ui.put("deleteProfile", "");
      ui.put("moreStyle", false);
      ui.put("waypointEdit", "");
      ui.put("waypointName", "");
      ui.put("waypointIcon", "");
      ui.put("waypointManager", "");
      ui.put("waypointDraftId", "");
      ui.put("waypointDraftName", "");
      ui.put("waypointDraftX", "0.0");
      ui.put("waypointDraftY", "0.0");
      ui.put("waypointDraftZ", "0.0");
      ui.put("waypointDraftColor", "#FFFFFFFF");
      ui.put("waypointDraftDimension", "minecraft:overworld");
      ui.put("waypointScope", "world");
      ui.put("waypointDeleteConfirm", false);
      ui.put("accountManager", "");
      ui.put("accountSearch", "");
      ui.put("accountSelectedId", "");
      ui.put("accountDraftId", "");
      ui.put("accountDraftName", "");
      ui.put("accountDraftNote", "");
      ui.put("accountDraftSkin", "");
      ui.put("soundBlockDraft", "");
      ui.put("cosmeticsTab", "capes");
      ui.put("cosmeticsCapeFilter", "all");
      ui.put("cosmeticsCapeQuery", "");
      ui.put("cosmeticsNickDraft",
          settings.cosmetics == null || settings.cosmetics.skinNickname == null
              ? ""
              : settings.cosmetics.skinNickname);
      ui.put("cosmeticsCapePathDraft",
          settings.cosmetics == null || settings.cosmetics.customCapePath == null
              ? ""
              : settings.cosmetics.customCapePath);
      ui.put("cosmeticsSkinPathDraft",
          settings.cosmetics == null || settings.cosmetics.customSkinPath == null
              ? ""
              : settings.cosmetics.customSkinPath);
      ui.put("cosmeticsWeaponPathDraft",
          settings.cosmetics == null || settings.cosmetics.customWeaponPath == null
              ? ""
              : settings.cosmetics.customWeaponPath);
      ui.put("cosmeticsArmorPathDraft",
          settings.cosmetics == null || settings.cosmetics.customArmorPath == null
              ? ""
              : settings.cosmetics.customArmorPath);
    }
    if (settings.cosmetics != null) {
      lastSyncedCustomCape =
          settings.cosmetics.customCapePath == null ? "" : settings.cosmetics.customCapePath;
      lastSyncedCustomSkin =
          settings.cosmetics.customSkinPath == null ? "" : settings.cosmetics.customSkinPath;
      lastSyncedCustomWeapon =
          settings.cosmetics.customWeaponPath == null ? "" : settings.cosmetics.customWeaponPath;
      lastSyncedCustomArmor =
          settings.cosmetics.customArmorPath == null ? "" : settings.cosmetics.customArmorPath;
    }
    for (Map.Entry<String, Object> e : document.stateDefaults().entrySet())
      if (!ui.containsKey(e.getKey())) ui.put(e.getKey(), e.getValue());
    if (settings.interfaceState != null)
      ui.put("sidebarCollapsed", settings.interfaceState.sidebarCollapsed);
    bindings = new SkinBindings(settings, ui);
    bindings.retainedCounts((source, rows) -> retainedCount(source, rows));
    bindings.onChanged(new java.util.function.Consumer<String>() {
      public void accept(String bind) {
        if (host != null) host.bindingChanged(bind);
      }
    });
    catalog = new UiCatalog(settings, actions);
  }

  public SkinDocument document() {
    return document;
  }

  public boolean hasComponent(String id) {
    return document.component(id) != null;
  }

  private void updatePreviewMotion(double now) {
    double dt = previewLastTime < 0 ? 0 : now - previewLastTime;
    previewLastTime = now;
    if (!"cosmetics".equals(page)) return;
    if (dt <= 0 || dt > .5) return;
    if (!previewDragging) {
      previewYaw += previewYawVelocity * (float) dt;
      previewYawVelocity *= (float) Math.pow(.055, dt);
      if (previewAuto) previewYaw += 15f * (float) dt;
      previewPitch += (previewPitchTarget - previewPitch) * (float) (1 - Math.exp(-dt * 12));
    }
    if (previewYaw > 360f)
      previewYaw -= 360f;
    else if (previewYaw < -360f)
      previewYaw += 360f;
    if (Math.abs(previewYawVelocity) < .02f) previewYawVelocity = 0;
  }

  public void beginFrame(float mouseX, float mouseY, double time) {
    frameActive = true;
    frameHudDataLoaded = false;
    this.mouseX = mouseX;
    this.mouseY = mouseY;
    this.time = time;
    updatePreviewMotion(time);
    frames = 0;
    drawCommands = 0;
    hits.clear();
    popups.clear();
    layoutBoxes.clear();
    paintedBoxes.clear();
    tooltip = tooltipKey = "";
    tooltipAnchor = null;
    catalog.refresh();
  }

  public boolean renderMenu(
      Canvas canvas, int width, int height, float mouseX, float mouseY, double time) {
    SkinDocument.Node root = document.menu();
    if (root == null) return false;
    viewportWidth = width;
    viewportHeight = height;
    menuScope = true;
    this.time = time;
    renderScope = root.id == null ? root.tag : root.id;
    catalog.refresh();
    syncPolicyRevision();
    syncSearchPlan();
    Object menuMetrics = canvas.textMetricsKey();
    boolean cacheAllowed = canCacheMenu(time, menuMetrics);
    long signature = 0;
    if (cacheAllowed) {
      if (!themeSnapshot.update(settings.theme)) themeGeneration++;
      signature = computeMenuSignature(width, height, mouseX, mouseY);
      if (signature != 0
          && menuFrameCache.matches(signature, width, height, false, menuMetrics, themeGeneration)) {
        HudFrameCache.DisplayList list = menuFrameCache.cached();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        metricsKey = menuMetrics;
        touchMenuMotionAlive(time);
        beginRenderPass();
        frames = list.frames;
        drawCommands = list.commands;
        HudFrameCache.replay(canvas, list);
        return true;
      }
    } else if (menuFrameCache.cached() != null) {
      menuFrameCache.clear();
    }
    beginFrame(mouseX, mouseY, time);
    if (menuNeedsHudData()) {
      cooldowns = safe(actions.cooldowns());
      inventory = safe(actions.inventory());
      tnts = safe(actions.tnts());
    } else {
      cooldowns = Collections.emptyList();
      inventory = Collections.emptyList();
      tnts = Collections.emptyList();
    }
    if (cacheAllowed && signature != 0) {
      hudFrameDynamic = false;
      HudFrameCache.Recorder recorder = menuFrameCache.begin(canvas, false);
      renderRoot(root, recorder, 0, 0, width, height, context(width, height));
      paintOverlay(recorder, width, height);
      endFrame();
      HudFrameCache.DisplayList recorded = recorder.finish(
          width, height, frames, drawCommands, false, menuMetrics, signature, themeGeneration, 0);
      if (recorded == null || hudFrameDynamic || hudAnimationActive() || !tooltip.isEmpty()
          || !hoveredTooltipKey.isEmpty()) {
        menuFrameCache.clear();
      } else {
        menuFrameCache.store(recorded);
      }
      return true;
    }
    renderRoot(root, canvas, 0, 0, width, height, context(width, height));
    paintOverlay(canvas, width, height);
    endFrame();
    return true;
  }

  private boolean canCacheMenu(double now, Object menuMetrics) {
    if (!settings.optimization.enabled || !settings.optimization.hudCache || menuMetrics == null)
      return false;
    if (menuNeedsHudData() || focused != null || dragged != null || pixelDrawing || draggingScroll)
      return false;
    if (!colorBind.isEmpty() || !pendingBind.isEmpty() || !hoveredTooltipKey.isEmpty())
      return false;
    if (now < notificationUntil && !notification.isEmpty()) return false;
    if ("cosmetics".equals(page) || "waypoints".equals(page) || "events".equals(page)
        || isWaypointManagerOpen() || isAccountManagerOpen())
      return false;
    if ("settings".equals(page) && "profiles".equals(ui.get("settingsTab")) && profileCache == null)
      return false;
    if (document != dev.nightclient.skin.DefaultSkin.resolve(settings.skin.preset, null))
      return false;
    if (host != null) {
      Map<String, Object> ctx = host.context();
      Object enter = ctx.get("menuEnter"), fade = ctx.get("menuFade"),
             cap = ctx.get("bindingCapture");
      if (enter instanceof Number && ((Number) enter).floatValue() < 1f) return false;
      if (fade instanceof Number && ((Number) fade).floatValue() < 1f) return false;
      if (cap != null && !String.valueOf(cap).isEmpty()) return false;
    }
    return !hudAnimationActive();
  }

  private long computeMenuSignature(int width, int height, float mx, float my) {
    catalog.refresh();
    syncSearchPlan();
    long sig = HudFrameCache.menuSignature(settings, actions, ui, localStates, scroll,
        host == null ? Collections.<String, Object>emptyMap() : host.context(), profileCache, width,
        height, mx, my, page, selectedModule, selectedHud, pressedKey, entrance);
    if (sig == 0) return 0;
    sig = HudFrameCache.mix(sig, catalog.bindingSignature());
    sig = HudFrameCache.mix(sig, FeaturePolicy.revision());
    sig = HudFrameCache.mix(sig, activeSearchPlan.signature);
    return HudFrameCache.mix(sig, searchManualCollapsed.hashCode());
  }

  private void syncSearchPlan() {
    String lang = dev.nightclient.i18n.I18n.language(settings.language);
    searchIndex.ensureBuilt(document, catalog, bindings, lang);
    String query = ModuleSearchIndex.normalize(searchQuery());
    if (!query.equals(searchAutoQuery)) {
      searchManualCollapsed.clear();
      searchAutoQuery = query;
      pendingSearchScroll = !query.isEmpty();
      menuFrameCache.clear();
    }
    if (query.isEmpty()) {
      activeSearchPlan = ModuleSearchIndex.SearchPlan.EMPTY;
      return;
    }
    List<UiCatalog.Module> pageCandidates;
    if ("hud".equals(page)) {
      pageCandidates = catalog.modules(true);
    } else if ("all".equals(page)) {
      pageCandidates = catalog.allVisibleModules();
    } else if ("settings".equals(page) || "events".equals(page) || "cosmetics".equals(page)
        || "waypoints".equals(page)) {
      pageCandidates = Collections.emptyList();
    } else {
      String cat = "features".equals(page) ? "render" : page;
      List<UiCatalog.Module> all = catalog.modules(false);
      pageCandidates = new ArrayList<UiCatalog.Module>(all.size());
      for (int i = 0; i < all.size(); i++) {
        UiCatalog.Module m = all.get(i);
        if (cat.equals(m.category)) pageCandidates.add(m);
      }
    }
    activeSearchPlan = searchIndex.plan(query, page, pageCandidates);
  }

  private boolean conditionMet(
      SkinDocument.Node sourceNode, String expr, Map<String, Object> context) {
    if (bindings.condition(expr, context)) return true;
    if (!menuScope || activeSearchPlan.isEmpty() || expr == null || expr.isEmpty()) return false;
    Object mod = context.get("module");
    if (!(mod instanceof UiCatalog.Module)) return false;
    return activeSearchPlan.isNodeRevealed(((UiCatalog.Module) mod).id, sourceNode);
  }

  private boolean isControlRevealedBySearch(UiCatalog.Control control) {
    return menuScope && !activeSearchPlan.isEmpty() && activeSearchPlan.isControlRevealed(control.bind);
  }

  private void touchMenuMotionAlive(double now) {
    for (Motion state : motion.values()) state.lastSeen = now;
    for (ValueMotion state : sliderMotion.values()) state.lastSeen = now;
    for (Frame frame : retainedFrames) frame.lastBuilt = now;
  }

  private boolean menuNeedsHudData() {
    if (menuHudDataDocument != document) {
      menuHudDataDocument = document;
      menuHudDataNeeded = referencesHudData(document.menu(),
          new java.util.IdentityHashMap<SkinDocument.Node, Boolean>());
    }
    return menuHudDataNeeded;
  }

  private static boolean mentionsHudData(String value) {
    return value != null
        && (value.contains("cooldown") || value.contains("inventory") || value.contains("tnt"));
  }

  private boolean referencesHudData(
      SkinDocument.Node node, java.util.IdentityHashMap<SkinDocument.Node, Boolean> seen) {
    if (node == null || seen.put(node, Boolean.TRUE) != null) return false;
    String tag = node.tag;
    if ("cooldowns".equals(tag) || "inventory".equals(tag) || "tnt".equals(tag)) return true;
    if (mentionsHudData(node.text)) return true;
    for (String attrVal : node.attributes.values())
      if (mentionsHudData(attrVal)) return true;
    for (String styleVal : document.styles(node, "").values())
      if (mentionsHudData(styleVal)) return true;
    SkinDocument.Node comp = componentFor(node);
    if (comp != null && comp != node && referencesHudData(comp, seen)) return true;
    for (int i = 0, size = node.children.size(); i < size; i++)
      if (referencesHudData(node.children.get(i), seen)) return true;
    return false;
  }

  public boolean renderHud(Canvas canvas, int width, int height, double time,
      List<Cooldown> cooldowns, List<InventorySlot> inventory, List<TntView> tnts) {
    return renderHud(canvas, width, height, time, cooldowns, inventory, tnts, true);
  }

  public boolean renderHud(Canvas canvas, int width, int height, double time,
      List<Cooldown> cooldowns, List<InventorySlot> inventory, List<TntView> tnts,
      boolean crosshairVisible) {
    syncPolicyRevision();
    viewportWidth = width;
    viewportHeight = height;
    menuScope = false;
    hudEditing = false;
    this.time = time;
    frames = 0;
    drawCommands = 0;
    hudExitDuration = 0;
    this.cooldowns = safe(cooldowns);
    this.inventory = safe(inventory);
    this.tnts = safe(tnts);
    cameraCrosshairVisible = crosshairVisible;
    updateCrosshairTargetProgress(!hudEditing && actions.crosshairTargetHit());
    SkinDocument.Node root = document.hud();
    if (root == null) return false;
    boolean cacheEnabled = settings.optimization.enabled && settings.optimization.hudCache;
    if (!cacheEnabled) {
      if (hudFrameCache.cached() != null) hudFrameCache.clear();
      renderRoot(root, canvas, 0, 0, width, height, context(width, height));
      return true;
    }
    Object hudMetrics = canvas.textMetricsKey();
    if (hudMetrics == null || !cacheableHudDocument()) {
      hudFrameCache.clear();
      renderRoot(root, canvas, 0, 0, width, height, context(width, height));
      return true;
    }
    if (!themeSnapshot.update(settings.theme)) themeGeneration++;
    long signature = hudSignature(width, height, crosshairVisible);
    if (signature == 0) {
      hudFrameCache.clear();
      renderRoot(root, canvas, 0, 0, width, height, context(width, height));
      return true;
    }
    if (hudFrameCache.matches(
            signature, width, height, crosshairVisible, hudMetrics, themeGeneration)) {
      HudFrameCache.DisplayList list = hudFrameCache.cached();
      metricsKey = hudMetrics;
      beginRenderPass();
      hudFrameCacheHits++;
      frames = list.frames;
      drawCommands = list.commands;
      hudExitDuration = list.hudExitDuration;
      HudFrameCache.replay(canvas, list);
      return true;
    }
    hudFrameDynamic = false;
    HudFrameCache.Recorder recorder = hudFrameCache.begin(canvas, true);
    renderRoot(root, recorder, 0, 0, width, height, context(width, height));
    HudFrameCache.DisplayList recorded = recorder.finish(width, height, frames, drawCommands,
        crosshairVisible, hudMetrics, signature, themeGeneration, hudExitDuration);
    if (recorded == null || hudFrameDynamic || hudAnimationActive()) {
      hudFrameCache.clear();
    } else {
      hudFrameCache.store(recorded);
    }
    return true;
  }

  private long hudSignature(int width, int height, boolean crosshairVisible) {
    long interaction = 0xCBF29CE484222325L;
    interaction = HudFrameCache.mix(interaction, FeaturePolicy.revision());
    interaction = HudFrameCache.mix(interaction, Float.floatToIntBits(inputScroll));
    interaction = HudFrameCache.mix(interaction, Float.floatToIntBits(scrollGrab));
    interaction = HudFrameCache.mix(interaction, caret);
    interaction = HudFrameCache.mix(interaction, selection);
    interaction = HudFrameCache.mix(interaction, draggingScroll ? 1 : 2);
    interaction = HudFrameCache.mix(interaction, inputBuffer == null ? 0 : inputBuffer.hashCode());
    interaction = HudFrameCache.mix(interaction, pendingBind == null ? 0 : pendingBind.hashCode());
    interaction =
        HudFrameCache.mix(interaction, pendingValue == null ? 0 : pendingValue.hashCode());
    interaction = HudFrameCache.mix(interaction, tooltip == null ? 0 : tooltip.hashCode());
    interaction = HudFrameCache.mix(interaction, colorBind == null ? 0 : colorBind.hashCode());
    interaction = HudFrameCache.mix(interaction, colorLabel == null ? 0 : colorLabel.hashCode());
    interaction = HudFrameCache.mix(interaction, Math.round(crosshairTargetProgress * 1000f));
    return HudFrameCache.signature(settings, actions, cooldowns, inventory, tnts, width, height,
        crosshairVisible, ui, mouseX, mouseY, page, selectedModule, selectedHud, pressedKey,
        focused == null ? null : focused.key, dragged == null ? null : dragged.key, colorBind,
        colorLabel, entrance, interaction);
  }

  private boolean cacheableHudDocument() {
    if (hudScanDocument != document || hudScanThemeGeneration != themeGeneration) {
      hudScanDocument = document;
      hudScanThemeGeneration = themeGeneration;
      hudScanStatic = HudFrameCache.cacheableDocument(document, this::componentFor);
    }
    return hudScanStatic;
  }

  private void updateCrosshairTargetProgress(boolean targetHit) {
    Settings.Crosshair h = settings.crosshair;
    float goal = targetHit ? 1f : 0f;
    if (h == null || !h.enabled || (!h.targetHighlight && !h.targetShapeEnabled) || !h.targetAnimate) {
      crosshairTargetProgress = goal;
      crosshairTargetTime = time;
      return;
    }
    if (Double.isNaN(crosshairTargetTime) || time < crosshairTargetTime) {
      crosshairTargetProgress = goal;
      crosshairTargetTime = time;
      return;
    }
    double dt = Math.min(0.1, Math.max(0.0, time - crosshairTargetTime));
    crosshairTargetTime = time;
    if (Math.abs(goal - crosshairTargetProgress) <= 0.002f) {
      crosshairTargetProgress = goal;
      return;
    }
    float alpha = (float) (1.0 - Math.exp(-Math.max(4f, h.targetAnimSpeed) * dt));
    crosshairTargetProgress += (goal - crosshairTargetProgress) * alpha;
    if (Math.abs(goal - crosshairTargetProgress) <= 0.002f) crosshairTargetProgress = goal;
  }

  private boolean hudAnimationActive() {
    if (crosshairTargetProgress > 0.0005f && crosshairTargetProgress < 0.9995f) return true;
    for (MotionTrack track : scrollMotion.values())
      if (track.inFlight(time)) return true;
    for (ValueMotion state : sliderMotion.values())
      if (state.track.inFlight(time)) return true;
    for (RepeatMemory memory : retainedRepeats.values())
      for (RepeatRow row : memory.rows.values())
        if (!Double.isNaN(row.retiredAt) && time < row.retiredAt + Math.max(0, memory.exitDuration))
          return true;
    for (Motion state : motion.values()) {
      if (!renderScope.equals(state.scope) || state.lastFrame == null) continue;
      if (state.opacity != null && state.opacity.inFlight(time)) return true;
      if (state.layoutWidth != null && state.layoutWidth.inFlight(time)) return true;
      if (state.slotOpening) return true;
      if (state.hover.inFlight(time) || state.press.inFlight(time) || state.value.inFlight(time)
          || state.x.inFlight(time) || state.y.inFlight(time) || state.width.inFlight(time)
          || state.height.inFlight(time) || state.layoutHeight.inFlight(time))
        return true;
      if (state.colorInFlight(time)) return true;
      if (!Double.isNaN(state.retiredAt)
          && time < state.retiredAt + Math.max(0, settings.theme.motion.exitDuration))
        return true;
    }
    return false;
  }

  public boolean renderComponent(String id, Canvas canvas, float x, float y, float width,
      float height, double time, Map<String, Object> context) {
    viewportWidth = Math.max(1, Math.round(width));
    viewportHeight = Math.max(1, Math.round(height));
    if (!frameActive || !frameHudDataLoaded) {
      cooldowns = safe(actions.cooldowns());
      inventory = safe(actions.inventory());
      tnts = safe(actions.tnts());
      if (frameActive) frameHudDataLoaded = true;
    }
    SkinDocument.Node root = document.component(id);
    if (root == null) return false;
    this.time = time;
    Map<String, Object> values = context(Math.round(width), Math.round(height));
    if (context != null) values.putAll(context);
    renderRoot(root, canvas, x, y, width, height, values);
    return true;
  }

  public boolean renderHudComponent(
      String id, Canvas canvas, float x, float y, float width, float height, double time) {
    SkinDocument.Node root = document.component(id);
    if (root == null) root = document.component("hud." + id);
    if (root == null) return false;
    this.time = time;
    renderRoot(
        root, canvas, x, y, width, height, context(Math.round(width), Math.round(height)), false);
    return true;
  }

  public float[] hudBounds(String id, int width, int height, double time, boolean editor) {
    if ("crosshair".equals(id)) return FeatureHudRenderer.crosshairBounds(settings, width, height);
    boolean oldScope = menuScope, oldEditing = hudEditing;
    int oldWidth = viewportWidth, oldHeight = viewportHeight, oldFrames = frames,
        oldCommands = drawCommands;
    double oldTime = this.time;
    Canvas oldCanvas = measuringCanvas;
    Map<String, Object> oldValues = frameValues;
    String oldRenderScope = renderScope;
    try {
      Frame root = cachedHud != null && cachedHudTime == time && cachedHudWidth == width
              && cachedHudHeight == height && cachedHudEditor == editor
          ? cachedHud
          : prepareHud(MeasurementCanvas.INSTANCE, width, height, time, editor);
      Frame widget = widgetFrame(root, id);
      return widget == null ? new float[] {0, 0, 0, 0, 1}
                            : new float[] {widget.x, widget.y, widget.w, widget.h,
                                  number(widget.style, "hud-scale", 1)};
    } finally {
      menuScope = oldScope;
      hudEditing = oldEditing;
      viewportWidth = oldWidth;
      viewportHeight = oldHeight;
      this.time = oldTime;
      frames = oldFrames;
      drawCommands = oldCommands;
      measuringCanvas = oldCanvas;
      frameValues = oldValues;
      renderScope = oldRenderScope;
    }
  }

  public float[] hudBoundsInstant(String id, int width, int height, boolean editor) {
    boolean previous = suppressMotion;
    suppressMotion = true;
    cachedHud = null;
    try {
      return hudBounds(id, width, height, 0, editor);
    } finally {
      suppressMotion = previous;
      cachedHud = null;
    }
  }

  public boolean renderHudWidgetInstant(
      String id, Canvas canvas, int width, int height, boolean editor) {
    boolean previous = suppressMotion;
    suppressMotion = true;
    try {
      return renderHudWidget(id, canvas, width, height, 0, editor);
    } finally {
      suppressMotion = previous;
      cachedHud = null;
    }
  }

  public boolean renderHudWidget(
      String id, Canvas canvas, int width, int height, double time, boolean editor) {
    syncPolicyRevision();
    if (FeaturePolicy.isWidgetBlocked(id)) return false;
    Frame root = prepareHud(canvas, width, height, time, editor);
    if (root == null) return renderHudComponent(id, canvas, 0, 0, width, height, time);
    Frame widget = widgetFrame(root, id);
    if (widget == null) return false;
    Bounds viewport = new Bounds(0, 0, width, height);
    Canvas budget = new BudgetCanvas(canvas);
    paint(widget, budget, viewport, false, Transform.IDENTITY, viewport);
    return true;
  }

  public boolean renderHudEditor(Canvas canvas, int width, int height, double time) {
    Frame root = prepareHud(canvas, width, height, time, true);
    if (root == null) return false;
    Bounds viewport = new Bounds(0, 0, width, height);
    Canvas budget = new BudgetCanvas(canvas);
    budget.pushClip(0, 0, width, height);
    try {
      paint(root, budget, viewport, false, Transform.IDENTITY, viewport);
    } finally {
      budget.popClip();
    }
    return true;
  }

  private Frame prepareHud(Canvas canvas, int width, int height, double time, boolean editor) {
    SkinDocument.Node node = document.hud();
    if (node == null) return null;
    viewportWidth = width;
    viewportHeight = height;
    menuScope = false;
    hudEditing = editor;
    this.time = time;
    frames = drawCommands = 0;
    cooldowns = safe(actions.cooldowns());
    inventory = safe(actions.inventory());
    tnts = safe(actions.tnts());
    cacheHudPass = false;
    beginRenderPass();
    measuringCanvas = canvas;
    bindings.measurementCanvas(canvas);
    frameValues = context(width, height, false);
    bindings.frameContext(frameValues);
    renderScope = "hud";
    Frame root = build(node, frameValues, null);
    if (root != null) {
      markHudPlacement(root);
      layout(root, 0, 0, width, height, true);
      placeHud(root, width, height);
    }
    cachedHud = root;
    cachedHudTime = time;
    cachedHudWidth = width;
    cachedHudHeight = height;
    cachedHudEditor = editor;
    return root;
  }

  private static final String[] HUD_PLACEMENTS = {"cooldowns", "inventory", "tnt", "armor",
      "potions", "target", "scoreboard", "keystrokes", "coordinates", "ping", "fps"};
  private static final String[] HUD_FRAME_IDS = {"cooldowns-frame", "inventory-frame", "tnt-frame",
      "armor-frame", "potions-frame", "target-frame", "scoreboard-frame", "keystrokes-frame",
      "coordinates-frame", "ping-frame", "fps-frame"};
  private static final String[] HUD_FRAME_ALIASES = {null, null, null, null, null, "target-native",
      null, null, null, null, null};
  private Settings.Panel hudPanel(String id) {
    if ("inventory".equals(id)) return settings.inventory;
    if ("tnt".equals(id)) return settings.tnt;
    if ("armor".equals(id)) return settings.armor;
    if ("potions".equals(id)) return settings.potions;
    if ("target".equals(id)) return settings.target;
    if ("scoreboard".equals(id)) return settings.scoreboard;
    if ("keystrokes".equals(id)) return settings.keystrokes;
    if ("coordinates".equals(id)) return settings.coordinates;
    if ("ping".equals(id)) return settings.ping;
    if ("betterChat".equals(id)) return settings.betterChat;
    if ("minimap".equals(id)) return settings.minimap;
    return settings.fps;
  }
  private static Frame placementFrame(Frame frame, String target, String alias) {
    if (target.equals(frame.id) || alias != null && alias.equals(frame.id)) return frame;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame found = placementFrame(frame.children.get(i), target, alias);
      if (found != null) return found;
    }
    return null;
  }
  private void markHudPlacement(Frame root) {
    if (!"true".equals(attr(root, "auto-layout", "false"))) return;
    for (int i = 0; i < HUD_FRAME_IDS.length; i++) {
      Frame frame = placementFrame(root, HUD_FRAME_IDS[i], HUD_FRAME_ALIASES[i]);
      for (Frame current = frame; current != null; current = current.parent)
        current.hudPlaced = true;
    }
  }
  private void placeHud(Frame root, float width, float height) {
    if (!"true".equals(attr(root, "auto-layout", "false"))) return;
    List<HudAutoLayout.Box> boxes = new ArrayList<HudAutoLayout.Box>();
    Map<String, Frame> frames = new HashMap<String, Frame>();
    for (int i = 0; i < HUD_FRAME_IDS.length; i++) {
      String id = HUD_PLACEMENTS[i];
      Frame frame = placementFrame(root, HUD_FRAME_IDS[i], HUD_FRAME_ALIASES[i]);
      if (frame == null || frame.w <= 0 || frame.h <= 0) continue;
      boolean pinned = id.equals(hudDragId);
      boxes.add(new HudAutoLayout.Box(id, pinned ? hudDragX : frame.x, pinned ? hudDragY : frame.y,
          frame.w, frame.h,
          ("cooldowns".equals(id) ? settings.hud.layoutOrder : hudPanel(id).layoutOrder),
          pinned));
      frames.put(id, frame);
    }
    hudPlacementCache.place(boxes, width, height, 6);
    for (HudAutoLayout.Box box : boxes) {
      Frame frame = frames.get(box.id);
      moveFrame(frame, box.x - frame.x, box.y - frame.y);
    }
  }

  private static Frame widgetFrame(Frame frame, String id) {
    String target = id + "-frame", component = "hud." + id;
    String alias = "crosshair".equals(id)              ? "crosshair-native"
        : "target".equals(id)                 ? "target-native"
                                                : null;
    return widgetFrame(frame, id, target, component, alias);
  }
  private static Frame widgetFrame(
      Frame frame, String id, String target, String component, String alias) {
    if (frame == null) return null;
    if (target.equals(frame.id) || alias != null && alias.equals(frame.id)) return frame;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame found = widgetFrame(frame.children.get(i), id, target, component, alias);
      if (found != null) return found;
    }
    if (frame.presentation != null
        && (id.equals(frame.presentationId) || id.equals(frame.presentation.id)
            || id.equals(frame.presentation.tag)))
      return frame;
    if ("component".equals(frame.node.tag) && component.equals(frame.id)) return frame;
    if (id.equals(frame.id)) return frame;
    return null;
  }

  private static final class MeasurementCanvas implements Canvas {
    static final MeasurementCanvas INSTANCE = new MeasurementCanvas();

    public void rect(float x, float y, float w, float h, int c) {}

    public void roundedRect(float x, float y, float w, float h, float r, int c) {}

    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}

    public void gradientRect(float x, float y, float w, float h, int a, int b) {}

    public void line(float x, float y, float xx, float yy, float t, int c) {}

    public void circle(float x, float y, float r, int c) {}

    public void text(String text, float x, float y, float size, int c, boolean b) {}

    public float textWidth(String text, float size, boolean bold) {
      return dev.nightclient.renderer.FontAtlas.get().width(text, size, bold);
    }

    public float textWidthFamily(
        String family, String text, float size, boolean bold, float tracking) {
      return dev.nightclient.renderer.FontAtlas.get(family).width(text, size, bold, tracking);
    }

    @Override
    public float gameTextWidth(String text, float scale) {
      return text == null
          ? 0
          : textWidth(dev.nightclient.api.FormattedText.plainText(text), 8.2f * scale, false);
    }

    public void pushClip(float x, float y, float w, float h) {}

    public void popClip() {}

    public void item(String id, float x, float y, float size, float alpha) {}

    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int q, float alpha) {}

    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
    }
  }

  public void finishFrame(Canvas canvas, int width, int height) {
    paintOverlay(canvas, width, height);
    endFrame();
  }

  public void endFrame() {
    frameActive = frameHudDataLoaded = false;
    if (focused != null && !hasHit(focused.key)) focused = null;
    if (dragged != null && !hasHit(dragged.key)) {
      dragged = null;
      pendingBind = pendingValue = "";
      if (liveBindingDirty) {
        liveBindingDirty = false;
        actions.changed();
      }
    }
    java.util.Iterator<Map.Entry<String, Motion>> iterator = motion.entrySet().iterator();
    while (iterator.hasNext())
      if (time - iterator.next().getValue().lastSeen > 2) iterator.remove();
    java.util.Iterator<ValueMotion> values = sliderMotion.values().iterator();
    while (values.hasNext())
      if (time - values.next().lastSeen > 2) values.remove();
    if (!scrollMotion.isEmpty()) {
      java.util.Iterator<Map.Entry<String, MotionTrack>> scrolls =
          scrollMotion.entrySet().iterator();
      while (scrolls.hasNext()) {
        Map.Entry<String, MotionTrack> entry = scrolls.next();
        MotionTrack track = entry.getValue();
        if (!track.inFlight(time) && track.at(time) == 0f
            && (!scroll.containsKey(entry.getKey()) || scroll.get(entry.getKey()) == 0f)) {
          scrolls.remove();
          scroll.remove(entry.getKey());
        }
      }
    }
  }

  private boolean hasHit(String key) {
    for (Hit hit : hits)
      if (hit.key.equals(key)) return true;
    return false;
  }

  private void beginRenderPass() {
    themeResolver.beginFrame();
    renderGeneration++;
    if (!themeSnapshot.update(settings.theme)) themeGeneration++;
    if (retainedFrames.size() >= MAX_FRAMES) {
      java.util.Iterator<Frame> frames = retainedFrames.iterator();
      while (frames.hasNext()) {
        Frame old = frames.next();
        if (time - old.lastBuilt > 2) {
          frames.remove();
          if (old.pool.get(old.poolIdentity) == old) old.pool.remove(old.poolIdentity);
        }
      }
    }
  }

  private String frameKey(SkinDocument.Node sourceNode, String identity) {
    Integer nodeId = nodeIds.get(sourceNode);
    if (nodeId == null) {
      nodeId = nodeIds.size();
      nodeIds.put(sourceNode, nodeId);
    }
    return nodeId + ":" + identity;
  }

  private void renderRoot(SkinDocument.Node node, Canvas canvas, float x, float y, float width,
      float height, Map<String, Object> context) {
    renderRoot(node, canvas, x, y, width, height, context, document.hud() != node);
  }

  private void renderRoot(SkinDocument.Node node, Canvas canvas, float x, float y, float width,
      float height, Map<String, Object> context, boolean interactive) {
    beginRenderPass();
    metricsKey = canvas.textMetricsKey();
    cacheHudPass = !menuScope && !hudEditing && !suppressMotion && !interactive
        && node == document.hud() && host == null && metricsKey != null;
    measuringCanvas = canvas;
    bindings.measurementCanvas(canvas);
    frameValues = context;
    bindings.frameContext(context);
    renderScope = node.id == null ? node.tag : node.id;
    Frame root = build(node, context, null);
    if (root == null) return;
    if (node == document.hud()) markHudPlacement(root);
    layout(root, x, y, Math.max(0, width), Math.max(0, height), true);
    if (menuScope && pendingSearchScroll && !activeSearchPlan.isEmpty()) {
      applySearchAutoScroll(root);
    }
    if (node == document.hud()) placeHud(root, width, height);
    Bounds viewport = new Bounds(x, y, width, height);
    Canvas budget = new BudgetCanvas(canvas);
    boolean topPass = renderRootDepth++ == 0;
    FastStringSet active = topPass ? scratchActiveKeys : new FastStringSet(64);
    List<Frame> retiring = topPass ? scratchRetiringFrames : new ArrayList<Frame>();
    List<Frame> overlays = topPass ? scratchOverlays : new ArrayList<Frame>();
    if (topPass) {
      active.clear();
      retiring.clear();
      overlays.clear();
    }
    try {
      if (entrance && !instantMotion() && !motion.isEmpty()) {
        collectKeys(root, active);
        for (Motion state : motion.values())
          if (renderScope.equals(state.scope) && state.lastFrame != null
              && !active.contains(state.lastFrame.key)
              && (state.lastFrame.parent == null || active.contains(state.lastFrame.parent.key))
              && (!menuScope || outOfFlow(state.lastFrame)
                  || state.lastFrame.parent == null
                  || "absolute".equals(state.lastFrame.parent.style.get("layout"))))
            retiring.add(state.lastFrame);
      }
      if (!retiring.isEmpty()) hudFrameDynamic = true;
      budget.pushClip(x, y, width, height);
      try {
        paint(root, budget, viewport, interactive, Transform.IDENTITY, viewport);
      } finally {
        budget.popClip();
      }
      paintingGhost = true;
      try {
        for (Frame old : retiring) {
          Motion state = motion.get(old.key);
          if (Double.isNaN(state.retiredAt)) state.retiredAt = time;
          dev.nightclient.config.MotionSettings recipe =
              themeResolver.theme(old.node, settings.theme, "").motion;
          float duration = recipe.exitDuration / Math.max(.1f, settings.menu.animationSpeed),
                progress =
                    duration <= 0 ? 1 : clamp((float) ((time - state.retiredAt) / duration), 0, 1);
          if (progress < 1)
            paintRetiring(old, state, budget, viewport, active,
                1 - MotionTrack.ease(progress, recipe.easing));
        }
      } finally {
        paintingGhost = false;
      }
      collectOverlays(root, overlays);
      if (overlays.size() > 1) Collections.sort(overlays, OVERLAY_ORDER);
      for (Frame overlay : overlays) {
        placeOverlay(overlay, viewport);
        budget.pushClip(x, y, width, height);
        try {
          paint(overlay, budget, viewport, interactive, Transform.IDENTITY, viewport);
        } finally {
          budget.popClip();
        }
      }
    } finally {
      renderRootDepth--;
    }
  }

  private static void collectOverlays(Frame frame, List<Frame> overlays) {
    for (Frame child : frame.children) {
      if ("overlay".equals(child.style.get("position"))) overlays.add(child);
      collectOverlays(child, overlays);
    }
  }

  private void paintRetiring(Frame frame, Motion state, Canvas canvas, Bounds viewport,
      FastStringSet active, float opacity) {
    Bounds clip = state.lastClip == null ? viewport : state.lastClip.intersect(viewport);
    for (Frame parent = frame.parent; parent != null; parent = parent.parent)
      if (active.contains(parent.key)) {
        Motion current = motion.get(parent.key);
        if (current != null && current.lastChildClip != null)
          clip = clip.intersect(current.lastChildClip);
      }
    if (clip.w <= 0 || clip.h <= 0) return;
    canvas.pushClip(clip.x, clip.y, clip.w, clip.h);
    try {
      paint(frame, new PoseCanvas(canvas, state.lastParentTransform, opacity), viewport, false,
          state.lastParentTransform, clip);
    } finally {
      canvas.popClip();
    }
  }

  private void placeOverlay(Frame frame, Bounds viewport) {
    if (frame.w > viewport.w || frame.h > viewport.h) {
      float w = Math.min(frame.w, viewport.w), h = Math.min(frame.h, viewport.h);
      frame.style.put("min-width", "0");
      frame.style.put("min-height", "0");
      frame.style.put("max-width", Float.toString(viewport.w));
      frame.style.put("max-height", Float.toString(viewport.h));
      if (!frame.style.containsKey("overflow")) frame.style.put("overflow", "auto");
      layout(frame, 0, 0, viewport.w, viewport.h, w, h, false);
    }
    Bounds anchor = paintedBoxes.get(attr(frame, "anchor", ""));
    if (anchor == null) {
      float[] box = layoutBoxes.get(attr(frame, "anchor", ""));
      if (box != null) anchor = new Bounds(box[0], box[1], box[2], box[3]);
    }
    float x = frame.x, y = frame.y;
    if (anchor != null) {
      String side = frame.style.get("anchor-side"), align = frame.style.get("anchor-align");
      float gap = number(frame.style, "anchor-gap", 0),
            offset = "center".equals(align) ? .5f
          : "end".equals(align)             ? 1
                                              : 0;
      if ("left".equals(side) || "right".equals(side)) {
        boolean left = "left".equals(side);
        x = left ? anchor.x - frame.w - gap : anchor.x + anchor.w + gap;
        y = anchor.y + (anchor.h - frame.h) * offset;
        float opposite = left ? anchor.x + anchor.w + gap : anchor.x - frame.w - gap;
        if ((x < viewport.x || x + frame.w > viewport.x + viewport.w) && opposite >= viewport.x
            && opposite + frame.w <= viewport.x + viewport.w)
          x = opposite;
      } else {
        boolean above = "above".equals(side);
        x = anchor.x + (anchor.w - frame.w) * offset;
        y = above ? anchor.y - frame.h - gap : anchor.y + anchor.h + gap;
        float opposite = above ? anchor.y + anchor.h + gap : anchor.y - frame.h - gap;
        if ((y < viewport.y || y + frame.h > viewport.y + viewport.h) && opposite >= viewport.y
            && opposite + frame.h <= viewport.y + viewport.h)
          y = opposite;
      }
    }
    x = clamp(x, viewport.x, viewport.x + Math.max(0, viewport.w - frame.w));
    y = clamp(y, viewport.y, viewport.y + Math.max(0, viewport.h - frame.h));
    moveFrame(frame, x - frame.x, y - frame.y);
  }

  private void applySearchAutoScroll(Frame root) {
    if (activeSearchPlan.matchedTargetIds.isEmpty()) {
      pendingSearchScroll = false;
      return;
    }
    Frame list = findFrameById(root, "modules-list");
    if (list == null) {
      pendingSearchScroll = false;
      return;
    }
    Frame target = null;
    for (int i = 0, size = activeSearchPlan.matchedTargetIds.size(); i < size; i++) {
      target = findFrameById(list, activeSearchPlan.matchedTargetIds.get(i));
      if (target != null) break;
    }
    if (target == null) {
      if (!list.layoutTransition) pendingSearchScroll = false;
      return;
    }
    float[] pad = padding(list.style);
    float viewTop = list.y + pad[0];
    float viewBottom = list.y + list.h - pad[2];
    float viewH = Math.max(1f, viewBottom - viewTop);
    float currentScroll = clamp(scrollOffset(list), 0f, list.scrollMax);
    if (list.scrollMax > 0f) {
      float targetTop = (target.y - viewTop) + currentScroll;
      float targetBottom = targetTop + Math.max(target.h, 20f);
      boolean visible = targetTop >= currentScroll + 8f && targetBottom <= currentScroll + viewH - 8f;
      if (!visible) {
        float desired =
            clamp(targetTop - Math.max(20f, (viewH - target.h) * 0.35f), 0f, list.scrollMax);
        scroll.put(list.key, desired);
        float nextScroll = clamp(scrollOffset(list), 0f, list.scrollMax);
        float delta = nextScroll - currentScroll;
        if (Math.abs(delta) > 0.01f) {
          for (int i = 0, size = list.children.size(); i < size; i++) {
            Frame ch = list.children.get(i);
            if (!outOfFlow(ch)) moveFrame(ch, 0f, -delta);
          }
        }
      }
    }
    if (!list.layoutTransition) pendingSearchScroll = false;
  }

  private static Frame findFrameById(Frame frame, String id) {
    if (frame == null || id == null) return null;
    if (id.equals(frame.id) || id.equals(frame.presentationId)) return frame;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame found = findFrameById(frame.children.get(i), id);
      if (found != null) return found;
    }
    return null;
  }

  private void moveFrame(Frame frame, float dx, float dy) {
    frame.x += dx;
    frame.y += dy;
    if (frame.id != null) recordLayoutBox(frame.id, frame.x, frame.y, frame.w, frame.h);
    for (int i = 0, size = frame.children.size(); i < size; i++)
      moveFrame(frame.children.get(i), dx, dy);
  }

  private static void collectKeys(Frame frame, FastStringSet keys) {
    keys.add(frame.key);
    for (int i = 0, size = frame.children.size(); i < size; i++)
      collectKeys(frame.children.get(i), keys);
  }

  private String contextLanguage;
  private Map<String, Object> localizedValues;

  private Map<String, Object> localizedContext() {
    if (localizedValues != null && java.util.Objects.equals(contextLanguage, settings.language))
      return localizedValues;
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("tr", dev.nightclient.i18n.I18n.context(settings.language));
    values.put("i18n", dev.nightclient.i18n.I18n.context(settings.language));
    values.put("themePresetOptions", dev.nightclient.skin.DefaultSkin.presetOptions());
    values.put("themePresetLabels", dev.nightclient.skin.DefaultSkin.presetLabels());
    values.put("searchLabel", tr("ui.find_a_feature"));
    values.put("backLabel", tr("ui.back"));
    values.put("noResultsLabel", tr("ui.no_results"));
    values.put("controlsLabel", tr("ui.controls"));
    values.put("bindingsLabel", tr("ui.key_bindings"));
    values.put("menuBindingTitle", tr("ui.menu"));
    values.put("menuSettingsLabel", tr("ui.interface"));
    values.put("bindingHint", tr("ui.escape_cancels_delete_unbinds"));
    values.put("vanillaBlurLabel", tr("ui.minecraft_gui_blur"));
    values.put("viewmodelRotationLabel", tr("ui.rotation"));
    values.put("moreStyleLabel", tr("ui.detailed_settings"));
    values.put("alphaLabel", tr("ui.transparency"));
    values.put("lowFireDragLabel", tr("ui.drag_the_fire_down"));
    values.put("lowFireAbsentLabel", tr("ui.fire_appears_while_the_player_is_burning"));
    values.put("lowFireHiddenLabel", tr("ui.hide_fire"));
    values.put("lowFireHeightLabel", tr("ui.fire_position"));
    values.put("viewmodelModeLabel", tr("ui.transform"));
    values.put("viewmodelResetLabel", tr("ui.reset_hand"));
    values.put("viewmodelPickLabel", tr("ui.click_the_item_or_hand"));
    values.put("themeFileLabel", tr("ui.nc_interface"));
    values.put("vanillaLabel", tr("ui.minecraft"));
    values.put("brushLabel", tr("ui.brush_colour"));
    values.put("featuresLabel", tr("ui.features"));
    values.put("styleLabel", tr("ui.style"));
    values.put("profilesLabel", tr("ui.profiles"));
    values.put("editorLabel", tr("ui.hud_editor"));
    values.put("doneLabel", tr("ui.done"));
    values.put("languageLabel", tr("ui.english"));
    values.put("saveLabel", tr("ui.save"));
    values.put("loadLabel", tr("ui.load"));
    values.put("deleteLabel", tr("ui.delete"));
    values.put("cancelLabel", tr("ui.cancel"));
    values.put("reloadLabel", tr("ui.reload_nc"));
    values.put("examplesLabel", tr("ui.export_nc"));
    values.put("customLabel", tr("ui.custom_nc_file"));
    values.put("undoLabel", tr("ui.undo"));
    values.put("resetLabel", tr("ui.reset"));
    values.put("copyLabel", tr("ui.copy"));
    values.put("pasteLabel", tr("ui.paste"));
    values.put("emptyProfilesLabel", tr("ui.no_profiles"));
    contextLanguage = settings.language;
    localizedValues = Collections.unmodifiableMap(values);
    return localizedValues;
  }

  private final long[] contextEpoch = new long[1];
  private Map<String, Object> baseContextParent;
  private Map<String, Object> baseContextMap;

  private Map<String, Object> baseContext() {
    Map<String, Object> localized = localizedContext();
    if (baseContextMap != null && baseContextParent == localized) return baseContextMap;
    Map<String, Object> base = SkinBindings.childContext(localized, 96);
    SkinBindings.frameLazy(
        base, "previewZoomLabel", contextEpoch, () -> Math.round(previewZoom * 100) + "%");
    SkinBindings.frameLazy(
        base, "cosmeticsSkinSummary", contextEpoch, () -> actions.cosmeticsSkinSummary());
    SkinBindings.frameLazy(
        base, "cosmeticsCapeSummary", contextEpoch, () -> actions.cosmeticsCapeSummary());
    SkinBindings.frameLazy(
        base, "cosmeticsWeaponSummary", contextEpoch, () -> actions.cosmeticsWeaponSummary());
    SkinBindings.frameLazy(
        base, "cosmeticsArmorSummary", contextEpoch, () -> actions.cosmeticsArmorSummary());
    SkinBindings.frameLazy(
        base, "cosmeticsLoading", contextEpoch, () -> actions.cosmeticsLoading());
    SkinBindings.frameLazy(base, "cosmeticsError", contextEpoch, () -> {
      String key = actions.cosmeticsErrorKey();
      return key == null || key.isEmpty() ? "" : tr(key);
    });
    SkinBindings.frameLazy(
        base, "cosmeticsCapesEmpty", contextEpoch, () -> officialCapeRows().isEmpty());
    base.put("officialCapesEnabled", dev.nightclient.cosmetics.OfficialCapes.isEnabled());
    SkinBindings.frameLazy(base, "previewHasCape", contextEpoch, () -> {
      try {
        dev.nightclient.api.PlayerSkin skin = actions.playerPreview();
        return skin != null && skin.capePixels() != null;
      } catch (RuntimeException unavailableSkin) {
        return false;
      }
    });
    final SkinBindings.FrameLazy lazyCooldownRows = SkinBindings.frameLazy(
        base, "cooldownRows", contextEpoch, () -> hudData.cooldowns(settings, cooldowns));
    SkinBindings.frameLazy(
        base, "inventoryCells", contextEpoch, () -> hudData.inventory(settings, inventory));
    final SkinBindings.FrameLazy lazyTntRows =
        SkinBindings.frameLazy(base, "tntRows", contextEpoch, () -> hudData.tnts(settings, tnts));
    SkinBindings.frameLazy(
        base, "armorSlots", contextEpoch, () -> hudData.armor(settings, safe(actions.armor())));
    final SkinBindings.FrameLazy lazyPotionRows = SkinBindings.frameLazy(
        base, "potionRows", contextEpoch, () -> hudData.potions(settings, safe(actions.potions())));
    final SkinBindings.FrameLazy lazyTargetData =
        SkinBindings.frameLazy(base, "targetData", contextEpoch, () -> actions.target());
    SkinBindings.frameLazy(base, "localPlayerData", contextEpoch, () -> actions.localPlayer());
    SkinBindings.frameLazy(base, "targetRows", contextEpoch,
        () -> hudData.target((dev.nightclient.api.TargetView) lazyTargetData.get()));
    SkinBindings.frameLazy(base, "pingData", contextEpoch,
        () -> hudData.ping(settings, actions.ping(), actions.networkInfo(), hudEditing));
    SkinBindings.frameLazy(
        base, "fpsData", contextEpoch, () -> hudData.fps(settings, actions.fps(), hudEditing));
    SkinBindings.frameLazy(base, "keystrokesData", contextEpoch,
        () -> hudData.keystrokes(settings, actions.keystrokes(), hudEditing));
    SkinBindings.frameLazy(base, "coordinatesData", contextEpoch,
        () -> hudData.coordinates(settings, actions.coordinates(), hudEditing));
    SkinBindings.frameLazy(base, "scoreboardData", contextEpoch,
        () -> hudData.scoreboard(settings, actions.scoreboard(), hudEditing));
    SkinBindings.frameLazy(
        base, "cooldownCount", contextEpoch, () -> ((List<?>) lazyCooldownRows.get()).size());
    SkinBindings.frameLazy(
        base, "tntCount", contextEpoch, () -> ((List<?>) lazyTntRows.get()).size());
    SkinBindings.frameLazy(
        base, "potionCount", contextEpoch, () -> ((List<?>) lazyPotionRows.get()).size());
    SkinBindings.frameLazy(base, "armorEmpty", contextEpoch, () -> {
      List<InventorySlot> slots = safe(actions.armor());
      for (int i = 0, n = slots.size(); i < n; i++) {
        InventorySlot slot = slots.get(i);
        if (slot != null && !slot.empty()) return false;
      }
      return true;
    });
    SkinBindings.frameLazy(base, "categoryModules", contextEpoch, () -> categoryModules());
    SkinBindings.frameLazy(base, "filteredModules", contextEpoch, () -> filteredModules());
    SkinBindings.frameLazy(base, "filteredHudModules", contextEpoch, () -> filteredModules(true));
    SkinBindings.frameLazy(base, "renderModules", contextEpoch, () -> filteredCategory("render"));
    SkinBindings.frameLazy(base, "playerModules", contextEpoch, () -> filteredCategory("player"));
    SkinBindings.frameLazy(base, "noModules", contextEpoch, () -> filteredModules().isEmpty());
    SkinBindings.frameLazy(base, "anchor", contextEpoch, () -> {
      Bounds anchor = colorAnchor == null ? new Bounds(mouseX, mouseY, 0, 0) : colorAnchor;
      Map<String, Object> anchorData = new HashMap<String, Object>(8);
      anchorData.put("x", anchor.x);
      anchorData.put("y", anchor.y);
      anchorData.put("width", anchor.w);
      anchorData.put("height", anchor.h);
      return anchorData;
    });
    SkinBindings.frameLazy(base, "pointer", contextEpoch, () -> {
      Map<String, Object> pointer = new HashMap<String, Object>(4);
      pointer.put("x", mouseX);
      pointer.put("y", mouseY);
      return pointer;
    });
    final SkinBindings.FrameLazy selectedMeta = new SkinBindings.FrameLazy(
        contextEpoch, () -> catalog.module("hud".equals(page) ? selectedHud : selectedModule));
    SkinBindings.frameLazy(base, "selectedLabel", contextEpoch, () -> {
      UiCatalog.Module selected = (UiCatalog.Module) selectedMeta.get();
      return selected == null ? "" : selected.label;
    });
    SkinBindings.frameLazy(base, "selectedBind", contextEpoch, () -> {
      UiCatalog.Module selected = (UiCatalog.Module) selectedMeta.get();
      return selected == null ? "" : selected.bind;
    });
    SkinBindings.frameLazy(base, "selectedSummary", contextEpoch, () -> {
      UiCatalog.Module selected = (UiCatalog.Module) selectedMeta.get();
      return selected == null ? "" : selected.summary;
    });
    SkinBindings.frameLazy(base, "selectedIcon", contextEpoch, () -> {
      UiCatalog.Module selected = (UiCatalog.Module) selectedMeta.get();
      return selected == null ? "" : selected.icon;
    });
    SkinBindings.frameLazy(base, "skinStatus", contextEpoch, () -> actions.skinStatus());
    SkinBindings.frameLazy(base, "canUndo", contextEpoch, () -> actions.canUndo());
    SkinBindings.frameLazy(
        base, "accountActiveName", contextEpoch, () -> maskAccountDisplay(actions.activeAccountName()));
    SkinBindings.frameLazy(
        base, "accountActiveSkin", contextEpoch, () -> safeNick(actions.activeAccountName()));
    SkinBindings.frameLazy(
        base, "accountIsLauncher", contextEpoch, () -> actions.isLauncherAccountActive());
    SkinBindings.frameLazy(base, "accountLauncherName", contextEpoch,
        () -> maskAccountDisplay(actions.launcherAccountName()));
    SkinBindings.frameLazy(
        base, "accountLauncherSkin", contextEpoch, () -> safeNick(actions.launcherAccountName()));
    SkinBindings.frameLazy(
        base, "accountSelectedIsLauncher", contextEpoch, () -> selectedAccount() == null);
    SkinBindings.frameLazy(base, "accountSelectedActive", contextEpoch, () -> {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      if (sel == null) return actions.isLauncherAccountActive();
      return !actions.isLauncherAccountActive()
          && sel.name.equalsIgnoreCase(safeNick(actions.activeAccountName()));
    });
    SkinBindings.frameLazy(base, "accountSelectedName", contextEpoch, () -> {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      return maskAccountDisplay(sel == null ? actions.launcherAccountName() : sel.name);
    });
    SkinBindings.frameLazy(base, "accountSelectedSkin", contextEpoch, () -> {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      return sel == null ? safeNick(actions.launcherAccountName()) : sel.effectiveSkinName();
    });
    SkinBindings.frameLazy(base, "accountSelectedMeta", contextEpoch, () -> {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      if (sel == null) return tr("accounts.launcher_hint");
      String uuidShort = dev.nightclient.config.SavedAccount.shortUuid(sel.offlineUuid());
      return sel.note != null && !sel.note.isEmpty() ? sel.note + " · " + uuidShort
                                                     : tr("accounts.offline_badge") + " · " + uuidShort;
    });
    SkinBindings.frameLazy(
        base, "accountCanReconnect", contextEpoch, () -> actions.canReconnectServer());
    SkinBindings.frameLazy(base, "accountServerLabel", contextEpoch, () -> {
      String label = actions.currentServerLabel();
      return label == null ? "" : label;
    });
    SkinBindings.frameLazy(base, "accountError", contextEpoch, () -> {
      String key = actions.accountErrorKey();
      return key == null || key.isEmpty() ? "" : tr(key);
    });
    SkinBindings.frameLazy(
        base, "accountsCount", contextEpoch, () -> actions.savedAccounts().size());
    SkinBindings.frameLazy(
        base, "accountsEmpty", contextEpoch, () -> actions.savedAccounts().isEmpty());
    SkinBindings.frameLazy(base, "accountsSearchEmpty", contextEpoch,
        () -> !actions.savedAccounts().isEmpty() && accountRows().isEmpty());
    SkinBindings.frameLazy(base, "currentWorldId", contextEpoch, () -> {
      String id = actions.currentWorldId();
      return id == null ? "" : id;
    });
    SkinBindings.frameLazy(base, "currentWorldLabel", contextEpoch, () -> {
      String label = actions.currentWorldLabel();
      if (label != null && !label.trim().isEmpty()) return label.trim();
      String id = actions.currentWorldId();
      return id == null || id.isEmpty() ? tr("waypoint.world_none") : id;
    });
    SkinBindings.frameLazy(base, "waypointInWorld", contextEpoch, () -> {
      String id = actions.currentWorldId();
      return id != null && !id.isEmpty();
    });
    SkinBindings.frameLazy(base, "markersEmpty", contextEpoch, () -> markerRows().isEmpty());
    SkinBindings.frameLazy(base, "hwEventsEmpty", contextEpoch, () -> actions.hwEvents().isEmpty());
    SkinBindings.frameLazy(base, "hwEventsLoading", contextEpoch, () -> actions.hwEventsLoading());
    SkinBindings.frameLazy(base, "hwEventsSummary", contextEpoch, () -> {
      String summary = actions.hwEventsSummary();
      return summary == null || summary.isEmpty() ? tr("hwEvents.summary_default") : summary;
    });
    SkinBindings.frameLazy(base, "soundMutedEmpty", contextEpoch,
        () -> settings.soundControl == null || settings.soundControl.mutedBlocks == null
            || settings.soundControl.mutedBlocks.isEmpty());
    SkinBindings.frameLazy(base, "tntFuseMp3Loaded", contextEpoch,
        () -> settings.soundControl != null && settings.soundControl.tntFuseMp3 != null
            && !settings.soundControl.tntFuseMp3.trim().isEmpty());
    SkinBindings.frameLazy(base, "tntExplodeMp3Loaded", contextEpoch,
        () -> settings.soundControl != null && settings.soundControl.tntExplodeMp3 != null
            && !settings.soundControl.tntExplodeMp3.trim().isEmpty());
    SkinBindings.frameLazy(base, "tntFuseMp3Label", contextEpoch, () -> {
      String raw = settings.soundControl == null ? "" : settings.soundControl.tntFuseMp3;
      String name = shortAudioName(raw);
      return name.isEmpty() ? tr("soundControl.mp3_none") : name;
    });
    SkinBindings.frameLazy(base, "tntExplodeMp3Label", contextEpoch, () -> {
      String raw = settings.soundControl == null ? "" : settings.soundControl.tntExplodeMp3;
      String name = shortAudioName(raw);
      return name.isEmpty() ? tr("soundControl.mp3_none") : name;
    });
    SkinBindings.frameLazy(base, "profilesEmpty", contextEpoch, () -> profiles().isEmpty());
    SkinBindings.frameLazy(base, "foodData", contextEpoch, () -> actions.vitals());
    SkinBindings.frameLazy(base, "targetEquipment", contextEpoch, () -> {
      dev.nightclient.api.TargetView targetData =
          (dev.nightclient.api.TargetView) lazyTargetData.get();
      return targetData == null ? Collections.emptyList() : targetData.equipment;
    });
    SkinBindings.frameLazy(
        base, "targetVisible", contextEpoch, () -> lazyTargetData.get() != null);
    SkinBindings.frameLazy(base, "gameVersion", contextEpoch, () -> actions.gameVersion());
    SkinBindings.frameLazy(base, "saveStatus", contextEpoch, () -> actions.saveStatus());
    baseContextParent = localized;
    baseContextMap = base;
    return base;
  }

  private Map<String, Object> context(int width, int height) {
    return context(width, height, true);
  }

  private Map<String, Object> context(int width, int height, boolean includeHost) {
    if ("events".equals(page) && !hwEventsTabVisible()) page = "player";
    contextEpoch[0]++;
    Map<String, Object> values = SkinBindings.childContext(baseContext(), 32);
    SkinBindings.set(values, "page", page);
    SkinBindings.set(values, "previewAuto", previewAuto);
    SkinBindings.set(values, "previewBackPage", previewBackPage);
    if (settings.cosmetics != null) {
      String capePath =
          settings.cosmetics.customCapePath == null ? "" : settings.cosmetics.customCapePath;
      if (!capePath.equals(lastSyncedCustomCape)) {
        lastSyncedCustomCape = capePath;
        ui.put("cosmeticsCapePathDraft", capePath);
      }
      String skinPath =
          settings.cosmetics.customSkinPath == null ? "" : settings.cosmetics.customSkinPath;
      if (!skinPath.equals(lastSyncedCustomSkin)) {
        lastSyncedCustomSkin = skinPath;
        ui.put("cosmeticsSkinPathDraft", skinPath);
      }
      String weaponPath =
          settings.cosmetics.customWeaponPath == null ? "" : settings.cosmetics.customWeaponPath;
      if (!weaponPath.equals(lastSyncedCustomWeapon)) {
        lastSyncedCustomWeapon = weaponPath;
        ui.put("cosmeticsWeaponPathDraft", weaponPath);
      }
      if ("armor".equals(String.valueOf(ui.get("cosmeticsTab")))) {
        ui.put("cosmeticsTab", "capes");
      }
      String armorPath =
          settings.cosmetics.customArmorPath == null ? "" : settings.cosmetics.customArmorPath;
      if (!armorPath.equals(lastSyncedCustomArmor)) {
        lastSyncedCustomArmor = armorPath;
        ui.put("cosmeticsArmorPathDraft", armorPath);
      }
    }
    SkinBindings.set(values, "boxWidth", width);
    SkinBindings.set(values, "boxHeight", height);
    SkinBindings.set(values, "hudEditing", hudEditing);
    SkinBindings.set(values, "leftMainArm", actions.leftMainArm());
    SkinBindings.set(values, "width", width);
    SkinBindings.set(values, "height", height);
    SkinBindings.set(values, "viewportWidth", viewportWidth);
    SkinBindings.set(values, "viewportHeight", viewportHeight);
    SkinBindings.set(values, "time", time);
    SkinBindings.set(values, "ui", ui);
    SkinBindings.set(values, "selectedModule", selectedModule);
    SkinBindings.set(values, "selectedHud", selectedHud);
    Object delProf = ui.get("deleteProfile");
    SkinBindings.set(values, "profilePending", delProf != null && !String.valueOf(delProf).isEmpty());
    Object wpEdit = ui.get("waypointEdit");
    SkinBindings.set(values, "waypointPending", wpEdit != null && !String.valueOf(wpEdit).isEmpty());
    SkinBindings.set(values, "waypointStandalone", standaloneWaypoint);
    SkinBindings.set(values, "accountStandalone", standaloneAccount);
    if (includeHost && host != null) {
      Map<String, Object> hostCtx = host.context();
      if (hostCtx != null && !hostCtx.isEmpty())
        for (Map.Entry<String, Object> entry : hostCtx.entrySet())
          SkinBindings.set(values, entry.getKey(), entry.getValue());
    }
    SkinBindings.set(values, "crosshairVisible", cameraCrosshairVisible);
    return values;
  }

  private float numericTemplate(String value, Map<String, Object> context, float fallback) {
    try {
      return Float.parseFloat(bindings.text(value, context));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private Object displayValue(SkinDocument.Node node, Map<String, Object> context, Object value) {
    if (!"slider".equals(node.tag) || !(value instanceof Number)) return value;
    float scale = numericTemplate(node.attribute("value-scale", "1"), context, 1),
          offset = numericTemplate(node.attribute("value-offset", "0"), context, 0);
    return scale == 1 && offset == 0 ? value : ((Number) value).floatValue() * scale + offset;
  }

  private static String encodedSliderValue(Frame frame, Object stored, float displayed) {
    float scale = numericAttr(frame, "value-scale", 1),
          offset = numericAttr(frame, "value-offset", 0);
    float value = Math.abs(scale) < .000001f ? displayed : (displayed - offset) / scale;
    return stored instanceof Integer ? Integer.toString(Math.round(value)) : Float.toString(value);
  }

  private static String choiceLabel(String value, String options, String labels) {
    String[] ids = options.split("\\|", -1), names = labels.split("\\|", -1);
    for (int i = 0; i < ids.length; i++)
      if (ids[i].equals(value)) return i < names.length && !names[i].isEmpty() ? names[i] : value;
    return value;
  }

  private String tr(String key) {
    return dev.nightclient.i18n.I18n.text(settings.language, key);
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? Collections.<T>emptyList() : values;
  }

  private Map<String, String> visualStyles(SkinDocument.Node node, String state) {
    Map<String, String> source = document.styles(node, state), cached = visualStyles.get(source);
    if (cached != null) return cached;
    Map<String, String> values = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> entry : source.entrySet())
      if (!entry.getKey().startsWith("--")) values.put(entry.getKey(), entry.getValue());
    cached = Collections.unmodifiableMap(values);
    visualStyles.put(source, cached);
    return cached;
  }

  private Map<String, String> boundStyles(SkinDocument.Node node) {
    Map<String, String> source = document.styles(node, ""), cached = boundStyles.get(source);
    if (cached != null) return cached;
    Map<String, String> values = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> entry : visualStyles(node, "").entrySet())
      if (entry.getValue().contains("{{")) values.put(entry.getKey(), entry.getValue());
    cached = Collections.unmodifiableMap(values);
    boundStyles.put(source, cached);
    return cached;
  }

  private Frame build(
      SkinDocument.Node node, Map<String, Object> context, Map<String, String> inherited) {
    if (frames++ >= MAX_FRAMES) return null;
    SkinDocument.Node sourceNode = node;
    NodePlan sourcePlan = nodePlan(node);
    int buildStart = frames;
    long retention = retentionGeneration;
    boolean cacheComponent = cacheHudPass && "component".equals(node.tag);
    if (cacheComponent && !sourcePlan.inputCompiled) {
      sourcePlan.inputs = SkinInputPlan.compile(node, document, this::componentFor);
      sourcePlan.inputCompiled = true;
    }
    if (cacheComponent) {
      Map<String, Frame> pool = sourcePlan.framePools.get(renderScope);
      Frame retained = pool == null ? null : pool.get(bindings.frameIdentity(context));
      if (retained != null && Double.isNaN(retained.retiredAt)
          && retained.lastGeneration != renderGeneration && retained.memory != null
          && retained.memory.matches(context, inherited)
          && frames + retained.memory.buildCost - 1 <= MAX_FRAMES) {
        frames += retained.memory.buildCost - 1;
        revive(retained);
        return retained;
      }
    }
    if (sourcePlan.earlyCondition
        && !conditionMet(sourceNode, node.attributes.get("if"), context))
      return null;
    if (node.attributes.containsKey("scope")) {
      String scope = bindings.text(node.attributes.get("scope"), context);
      if (scope.length() > 128) scope = scope.substring(0, 128);
      Map<String, Object> state = localStates.get(scope);
      if (state == null) {
        if (localStates.size() >= MAX_FRAMES)
          localStates.remove(localStates.keySet().iterator().next());
        state = new HashMap<String, Object>(document.stateDefaults());
        if (settings.interfaceState != null && settings.interfaceState.expandedPanels != null) {
          Boolean expanded = settings.interfaceState.expandedPanels.get(scope);
          if (expanded != null) state.put("expanded", expanded);
        }
        localStates.put(scope, state);
      }
      String selectionState = node.attributes.get("selection-state");
      if (!pendingSelection.isEmpty() && selectionState != null
          && state.get(selectionState) instanceof Boolean)
        for (Object value : context.values())
          if (value instanceof UiCatalog.Module
              && pendingSelection.equals(((UiCatalog.Module) value).id)) {
            state.put(selectionState, true);
            if ("expanded".equals(selectionState)) persistExpanded(scope, true);
            pendingSelection = "";
            break;
          }
      Map<String, Object> effectiveState = state;
      if (menuScope && !activeSearchPlan.isEmpty() && activeSearchPlan.isScopeExpanded(scope)
          && !searchManualCollapsed.contains(scope) && !Boolean.TRUE.equals(state.get("expanded"))) {
        effectiveState = new HashMap<String, Object>(state);
        effectiveState.put("expanded", Boolean.TRUE);
      }
      context = SkinBindings.childContext(context);
      context.put("local", effectiveState);
      context.put("__scope", scope);
    }
    Map<String, Object> parentContext = context;
    context = bindings.nodeContext(context, document.styles(node, ""), sourcePlan.aliasContext);
    boolean ownsBox = context != parentContext;
    if (!sourcePlan.earlyCondition
        && !conditionMet(sourceNode, node.attributes.get("if"), context))
      return null;
    if (node.attributes.containsKey("bind")) {
      String binding = bindings.text(node.attributes.get("bind"), context);
      if (FeaturePolicy.isControlBlocked(binding)
          || !dev.nightclient.config.FeatureSupport.isControlSupported(binding)) return null;
      Object value = displayValue(node, context, preview(binding, bindings.get(binding, context)));
      context.put("value", value);
      context.put("active", Boolean.TRUE.equals(value));
    }
    if (FeaturePolicy.hasBlocked() && node.attributes.containsKey("action")) {
      String resolvedAction = bindings.text(node.attributes.get("action"), context);
      if (FeaturePolicy.isActionBlocked(resolvedAction)) return null;
    }
    String declaredId = node.attributes.get("id");
    String resolvedId = node.id;
    if (declaredId != null && declaredId.contains("{{")) {
      resolvedId = bindings.text(declaredId, context);
      if (document.hasIdStyle(resolvedId)) node = document.instance(node, resolvedId);
    }
    if (FeaturePolicy.hasBlocked() && resolvedId != null && !resolvedId.isEmpty()) {
      if (!menuScope && FeaturePolicy.isWidgetBlocked(resolvedId)) return null;
      if ("nav-waypoints".equals(resolvedId) && FeaturePolicy.isBlocked("waypoints")) return null;
      if ("nav-events".equals(resolvedId) && FeaturePolicy.isBlocked("hwhelper.events")) return null;
    }
    if (componentMountDepth < 16 && !"none".equals(node.attribute("visual", ""))
        && !"component".equals(node.tag) && !"menu".equals(node.tag) && !"hud".equals(node.tag)) {
      final String resolved = resolvedId;
      SkinDocument.Node component = nodePlan(node).component;
      if (component == null && resolvedId != null && !resolvedId.equals(node.id))
        component = document.component(resolvedId);
      if (component != null && !mounting.contains(component.id)) {
        final SkinDocument.Node original = node;
        final Map<String, Object> originalContext = context;
        Map<String, Object> componentContext = SkinBindings.childContext(context);
        final String bind = bindings.text(node.attribute("bind", ""), context),
                     action = bindings.text(node.attribute("action", ""), context);
        final Object value =
            displayValue(node, context, preview(bind, bindings.get(bind, context)));
        if ("release".equals(node.attribute("commit", "")))
          componentContext.put("__releaseBind", bind);
        Integer mountId = nodeIds.get(sourceNode);
        if (mountId == null) {
          mountId = nodeIds.size();
          nodeIds.put(sourceNode, mountId);
        }
        componentContext.put(
            "__mountKey", context.getOrDefault("__mountKey", "") + "/" + mountId + ":" + bind);
        SkinBindings.lazy(componentContext, "moduleEnabled",
            ()
                -> !(originalContext.get("module") instanceof UiCatalog.Module)
                || ((UiCatalog.Module) originalContext.get("module")).enabled);
        SkinBindings.lazy(componentContext, "valueScale",
            () -> numericTemplate(original.attribute("value-scale", "1"), originalContext, 1));
        SkinBindings.lazy(componentContext, "valueOffset",
            () -> numericTemplate(original.attribute("value-offset", "0"), originalContext, 0));
        SkinBindings.lazy(componentContext, "elementId",
            ()
                -> resolved == null
                ? original.tag + (ELEMENT_BIND.matcher(bind).matches() ? "." + bind : "")
                : resolved);
        SkinBindings.lazy(componentContext, "min",
            () -> numericTemplate(original.attribute("min", "0"), originalContext, 0));
        SkinBindings.lazy(componentContext, "max",
            () -> numericTemplate(original.attribute("max", "1"), originalContext, 1));
        SkinBindings.lazy(componentContext, "step",
            () -> numericTemplate(original.attribute("step", "0"), originalContext, 0));
        SkinBindings.lazy(componentContext, "options",
            () -> bindings.text(original.attribute("options", ""), originalContext));
        SkinBindings.lazy(componentContext, "optionLabels",
            () -> bindings.text(original.attribute("option-labels", ""), originalContext));
        SkinBindings.lazy(componentContext, "tooltipText",
            () -> bindings.text(original.attribute("tooltip", ""), originalContext));
        SkinBindings.lazy(componentContext, "targetRatio",
            ()
                -> value instanceof Number
                ? clamp((((Number) value).floatValue()
                            - ((Number) componentContext.get("min")).floatValue())
                          / Math.max(.00001f,
                              ((Number) componentContext.get("max")).floatValue()
                                  - ((Number) componentContext.get("min")).floatValue()),
                      0, 1)
                : 0f);
        SkinBindings.lazy(componentContext, "ratio", () -> {
          float ratio = ((Number) componentContext.get("targetRatio")).floatValue();
          return "slider".equals(original.tag) ? sliderRatio(original, componentContext, ratio)
                                                 : ratio;
        });
        SkinBindings.lazy(componentContext, "chosenLabel",
            ()
                -> choiceLabel(String.valueOf(value),
                    String.valueOf(componentContext.get("options")),
                    String.valueOf(componentContext.get("optionLabels"))));
        SkinBindings.lazy(componentContext, "label",
            () -> bindings.text(original.attribute("text", original.text), originalContext));
        componentContext.put("value", value);
        componentContext.put("active", Boolean.TRUE.equals(value));
        componentContext.put("bind", bind);
        componentContext.put("action", action);
        SkinBindings.lazy(componentContext, "activate", () -> new Runnable() {
          public void run() {
            if ("toggle".equals(original.tag) || "switch".equals(original.tag)) {
              checkpointBinding(bind);
              if (bindings.set(bind,
                      Boolean.toString(!Boolean.TRUE.equals(bindings.get(bind, originalContext))),
                      false, originalContext))
                changedBinding(bind);
            } else
              act(action, originalContext);
          }
        });
        componentMountDepth++;
        mounting.add(component.id);
        try {
          Frame mounted = build(component, componentContext, inherited);
          if (mounted != null) {
            mounted.presentation = node;
            mounted.presentationId = resolvedId;
            Map<String, String> originalStyle = visualStyles(node, "");
            for (Map.Entry<String, String> entry : originalStyle.entrySet())
              if (!entry.getKey().startsWith("--")
                  && !("height".equals(entry.getKey())
                      && "true".equals(mounted.style.get("intrinsic-height"))))
                mounted.style.put(entry.getKey(), bindings.text(entry.getValue(), context));
          }
          return mounted;
        } finally {
          mounting.remove(component.id);
          componentMountDepth--;
        }
      }
    }
    String identity = bindings.frameIdentity(context);
    NodePlan plan = nodePlan(node);
    Map<String, Frame> pool = plan.framePools.get(renderScope);
    if (pool == null) {
      pool = new HashMap<String, Frame>();
      plan.framePools.put(renderScope, pool);
    }
    Frame frame = pool.get(identity);
    if (frame == null || frame.lastGeneration == renderGeneration) {
      frame = new Frame(node, context, plan.stylePlan.create(), node.attributes);
      frame.key = frameKey(sourceNode, identity);
      if (retainedFrames.size() < MAX_FRAMES) {
        Frame previous = pool.put(identity, frame);
        if (previous != null) retainedFrames.remove(previous);
        retainedFrames.add(frame);
        frame.pool = pool;
        frame.poolIdentity = identity;
      }
    } else {
      ((CompiledStyleMap) frame.style).reset();
      frame.context = context;
      frame.attributes = node.attributes;
      frame.presentation = null;
      frame.presentationId = null;
      frame.parent = null;
      frame.children.clear();
      frame.heightAnimated = frame.widthAnimated = frame.horizontalReflow = frame.layoutTransition =
          frame.flowSlotClip = false;
      frame.hudPlaced = false;
      frame.retiredAt = Double.NaN;
      frame.exitDuration = 0;
      frame.naturalHeight = frame.retireStartHeight = 0;
      frame.flowPresence = 1f;
    }
    frame.id = resolvedId;
    frame.measuredWidth = Float.NaN;
    frame.lastGeneration = renderGeneration;
    frame.lastBuilt = time;
    frame.ownsBox = ownsBox;
    Map<String, String> style = frame.style;
    if (inherited != null)
      for (String name : INHERITED_STYLES)
        if (!plan.styles.containsKey(name) && inherited.containsKey(name))
          style.put(name, inherited.get(name));
    for (BoundProperty entry : plan.properties)
      style.put(entry.name, bindings.text(entry.text, context));
    if (frame.dimensionStyles == null || frame.variableRecipe != bindings.variableRecipe(context)) {
      frame.dimensionStyles = new ArrayList<BoundProperty>();
      for (BoundProperty entry : plan.layoutProperties)
        if (bindings.dimensionDependent(entry.text, context)) frame.dimensionStyles.add(entry);
      frame.variableRecipe = bindings.variableRecipe(context);
    }
    if ("none".equals(style.get("display")) || "false".equals(style.get("visible"))) return null;
    Map<String, String> attributes = node.attributes;
    if (!plan.boundAttributes.isEmpty()) {
      attributes = new HashMap<String, String>(attributes);
      for (BoundProperty entry : plan.attributes)
        attributes.put(entry.name, bindings.text(entry.text, context));
    }
    frame.attributes = attributes;
    if ("repeat".equals(node.tag)) {
      String source = node.attributes.get("source"),
             alias = node.attributes.containsKey("as") ? node.attributes.get("as") : "item";
      List<?> values = source(source, context);
      List<RepeatRow> rows = new ArrayList<RepeatRow>();
      Set<String> live = new HashSet<String>();
      dev.nightclient.config.MotionSettings recipe =
          themeResolver.theme(node, settings.theme, "").motion;
      float exit = recipe.exitDuration / Math.max(.1f, settings.menu.animationSpeed);
      boolean retain = "true".equals(style.get("retain-exit")) && !instantMotion()
          && !suppressMotion && exit > 0;
      RepeatMemory memory = retainedRepeats.get(frame.key);
      if (retain && memory == null) {
        if (retainedRepeats.size() >= 1024)
          retainedRepeats.remove(retainedRepeats.keySet().iterator().next());
        memory = new RepeatMemory();
        retainedRepeats.put(frame.key, memory);
        retentionGeneration++;
      }
      if (retain) {
        if (!java.util.Objects.equals(memory.source, source)
            || !java.util.Objects.equals(memory.alias, alias)
            || !java.util.Objects.equals(memory.keyTemplate, node.attributes.get("key"))
            || memory.exitDuration != exit)
          retentionGeneration++;
        memory.source = source;
        memory.alias = alias;
        memory.keyTemplate = node.attributes.get("key");
        memory.context = context;
        memory.exitDuration = exit;
      }
      for (int index = 0; index < Math.min(values.size(), 512) && frames < MAX_FRAMES; index++) {
        Object value = values.get(index);
        if (value == null) continue;
        if (value instanceof UiCatalog.Control
            && !catalog.visible((UiCatalog.Control) value, bindings, context)
            && !isControlRevealedBySearch((UiCatalog.Control) value))
          continue;
        if (value instanceof Cooldown && ((Cooldown) value).remainingSeconds <= 0) continue;
        if (value instanceof TntView
            && (((TntView) value).seconds <= 0 || ((TntView) value).distance > settings.tnt.range))
          continue;
        Map<String, Object> row = SkinBindings.childContext(context);
        row.put(alias, value);
        row.put("index", index);
        String stable = repeatKey(node.attributes.get("key"), row, value, index);
        row.put("__repeatKey", identity + "/" + stable);
        RepeatRow snapshot = new RepeatRow(stable, row, index);
        rows.add(snapshot);
        live.add(stable);
        if (retain) memory.rows.put(stable, snapshot);
      }
      if (retain) {
        java.util.Iterator<RepeatRow> old = memory.rows.values().iterator();
        List<RepeatRow> retiring = new ArrayList<RepeatRow>();
        while (old.hasNext()) {
          RepeatRow row = old.next();
          if (live.contains(row.key)) continue;
          if (Double.isNaN(row.retiredAt)) {
            row.retiredAt = time;
            retentionGeneration++;
          }
          if (time - row.retiredAt >= exit) {
            old.remove();
            retentionGeneration++;
            continue;
          }
          retiring.add(row);
        }
        Collections.sort(retiring, RETIRING_ORDER);
        for (RepeatRow row : retiring) rows.add(Math.min(row.index, rows.size()), row);
      } else if (retainedRepeats.remove(frame.key) != null)
        retentionGeneration++;
      if (memory != null)
        while (memory.rows.size() > 1024) {
          memory.rows.remove(memory.rows.keySet().iterator().next());
          retentionGeneration++;
        }
      for (RepeatRow row : rows) {
        if (frames >= MAX_FRAMES) break;
        for (SkinDocument.Node child : node.children) {
          Frame built = build(child, row.context, style);
          if (built != null) {
            built.parent = frame;
            if (!Double.isNaN(row.retiredAt)) {
              built.retiredAt = row.retiredAt;
              built.exitDuration = exit;
              built.exitEasing = recipe.easing;
            }
            frame.children.add(built);
          }
        }
      }
    } else
      for (SkinDocument.Node child : node.children) {
        Frame built = build(child, context, style);
        if (built != null) {
          built.parent = frame;
          frame.children.add(built);
        }
      }
    if (menuScope && entrance && !instantMotion() && !suppressMotion && isVerticalFlow(frame)
        && !("repeat".equals(node.tag) && "true".equals(style.get("retain-exit"))))
      reconcileVerticalFlow(frame);
    if (cacheComponent && !frame.children.isEmpty() && !hasRetiring(frame)
        && retention == retentionGeneration) {
      SkinInputPlan.Snapshot inputs = sourcePlan.inputs.capture(bindings, context);
      frame.memory = inputs == null || retention != retentionGeneration
          ? null
          : new ComponentMemory(inputs, inherited, frames - buildStart + 1);
    } else
      frame.memory = null;
    return frame;
  }

  private static boolean hasRetiring(Frame frame) {
    if (!Double.isNaN(frame.retiredAt)) return true;
    for (int i = 0, size = frame.children.size(); i < size; i++)
      if (hasRetiring(frame.children.get(i))) return true;
    return false;
  }

  private void revive(Frame frame) {
    frame.lastGeneration = renderGeneration;
    frame.lastBuilt = time;
    frame.heightAnimated = frame.widthAnimated = frame.horizontalReflow = frame.layoutTransition =
        frame.flowSlotClip = false;
    frame.naturalHeight = frame.retireStartHeight = 0;
    frame.flowPresence = 1f;
    frame.hudPlaced = false;
    for (int i = 0, size = frame.children.size(); i < size; i++) revive(frame.children.get(i));
  }

  private static boolean isVerticalFlow(Frame frame) {
    if (frame == null) return false;
    String layout = frame.style.get("layout");
    if ("absolute".equals(layout) || "masonry".equals(layout) || isRow(frame)) return false;
    if ("grid".equals(layout)) return Math.round(number(frame.style, "columns", 2)) <= 1;
    return true;
  }

  private static boolean isAutoHeight(Frame frame) {
    return !frame.style.containsKey("height") || "auto".equals(frame.style.get("height"));
  }

  private boolean hasFlowSlotDescendant(Frame frame) {
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (outOfFlow(child)) continue;
      if (!Double.isNaN(child.retiredAt)) return true;
      Motion m = motion.get(child.key);
      if (m != null && m.slotOpening) return true;
      if (isAutoHeight(child) && isVerticalFlow(child) && hasFlowSlotDescendant(child)) return true;
    }
    return false;
  }

  private void touchSubtreeBuilt(Frame frame) {
    frame.lastBuilt = time;
    for (int i = 0, size = frame.children.size(); i < size; i++)
      touchSubtreeBuilt(frame.children.get(i));
  }

  private void reconcileVerticalFlow(Frame frame) {
    Motion parentMotion = motion.get(frame.key);
    if (parentMotion == null || parentMotion.lastFlowChildren == null
        || parentMotion.lastFlowChildren.isEmpty())
      return;
    List<Frame> prevList = parentMotion.lastFlowChildren;
    Set<String> liveKeys = new HashSet<String>(frame.children.size() * 2 + 1);
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame c = frame.children.get(i);
      if (!outOfFlow(c) && Double.isNaN(c.retiredAt)) liveKeys.add(c.key);
    }
    Set<String> prevLiveKeys = new HashSet<String>(prevList.size() * 2 + 1);
    Map<String, Frame> prevRetiringByKey = null;
    boolean hasSharedLive = false;
    for (int i = 0, size = prevList.size(); i < size; i++) {
      Frame prev = prevList.get(i);
      if (Double.isNaN(prev.retiredAt)) {
        prevLiveKeys.add(prev.key);
        if (liveKeys.contains(prev.key)) hasSharedLive = true;
      } else {
        if (prevRetiringByKey == null) prevRetiringByKey = new HashMap<String, Frame>();
        prevRetiringByKey.put(prev.key, prev);
      }
    }
    if (!hasSharedLive) return;

    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (outOfFlow(child) || !Double.isNaN(child.retiredAt)) continue;
      if (!prevLiveKeys.contains(child.key)) {
        float startH = 0f;
        Frame revived = prevRetiringByKey == null ? null : prevRetiringByKey.get(child.key);
        if (revived != null) startH = Math.max(0f, revived.h);
        Motion cm = motion.get(child.key);
        if (cm == null) {
          cm = new Motion(time, renderScope);
          motion.put(child.key, cm);
        }
        cm.layoutHeight.to(startH, time, 0f, "linear", true);
        cm.previousLayoutHeight = startH;
        cm.slotOpening = true;
        cm.retiredAt = Double.NaN;
      }
    }

    List<Frame> leadingRetiring = null;
    Map<String, List<Frame>> trailingByAnchor = null;
    String lastLiveAnchor = null;
    for (int i = 0, size = prevList.size(); i < size; i++) {
      Frame prev = prevList.get(i);
      if (liveKeys.contains(prev.key)) {
        lastLiveAnchor = prev.key;
        continue;
      }
      if (prev.lastGeneration == renderGeneration) continue;
      if (Double.isNaN(prev.retiredAt)) {
        dev.nightclient.config.MotionSettings recipe =
            themeResolver.theme(prev.node, settings.theme, "").motion;
        float speed = Math.max(.1f, settings.menu.animationSpeed);
        float exitDur = (recipe.exitDuration > 0 ? recipe.exitDuration : recipe.duration) / speed;
        if (exitDur <= 0f) continue;
        prev.retiredAt = time;
        prev.exitDuration = exitDur;
        prev.exitEasing = recipe.easing;
        prev.retireStartHeight = prev.h > 0f ? prev.h : prev.naturalHeight;
      }
      if (time - prev.retiredAt >= prev.exitDuration) continue;
      prev.parent = frame;
      touchSubtreeBuilt(prev);
      if (lastLiveAnchor == null) {
        if (leadingRetiring == null) leadingRetiring = new ArrayList<Frame>();
        leadingRetiring.add(prev);
      } else {
        if (trailingByAnchor == null) trailingByAnchor = new HashMap<String, List<Frame>>();
        List<Frame> bucket = trailingByAnchor.get(lastLiveAnchor);
        if (bucket == null) {
          bucket = new ArrayList<Frame>();
          trailingByAnchor.put(lastLiveAnchor, bucket);
        }
        bucket.add(prev);
      }
    }
    if (leadingRetiring != null || trailingByAnchor != null) {
      List<Frame> merged = new ArrayList<Frame>(frame.children.size() + 4);
      if (leadingRetiring != null) merged.addAll(leadingRetiring);
      for (int i = 0, size = frame.children.size(); i < size; i++) {
        Frame c = frame.children.get(i);
        merged.add(c);
        if (trailingByAnchor != null) {
          List<Frame> after = trailingByAnchor.get(c.key);
          if (after != null) merged.addAll(after);
        }
      }
      frame.children.clear();
      frame.children.addAll(merged);
    }
  }

  private final class ComponentMemory {
    final SkinInputPlan.Snapshot inputs;
    final String[] inherited = new String[INHERITED_STYLES.length];
    final long theme = themeGeneration;
    final Object metrics = metricsKey;
    final int buildCost;

    ComponentMemory(SkinInputPlan.Snapshot inputs, Map<String, String> source, int cost) {
      this.inputs = inputs;
      buildCost = cost;
      for (int i = 0; i < inherited.length; i++)
        inherited[i] = source == null ? null : source.get(INHERITED_STYLES[i]);
    }

    boolean matches(Map<String, Object> context, Map<String, String> source) {
      if (theme != themeGeneration || metrics != metricsKey) return false;
      for (int i = 0; i < inherited.length; i++)
        if (!java.util.Objects.equals(
                inherited[i], source == null ? null : source.get(INHERITED_STYLES[i])))
          return false;
      return inputs.matches(bindings, context);
    }
  }

  private float sliderRatio(SkinDocument.Node node, Map<String, Object> context, float target) {
    String key = renderScope + ":" + context.get("__mountKey") + ":"
        + context.getOrDefault("__repeatKey", "") + ":" + context.getOrDefault("__scope", "");
    ValueMotion state = sliderMotion.get(key);
    if (state == null) {
      state = new ValueMotion();
      sliderMotion.put(key, state);
    }
    state.lastSeen = time;
    dev.nightclient.config.MotionSettings recipe =
        themeResolver.theme(node, settings.theme, "").motion;
    boolean draggingThis = dragged != null && "slider".equals(dragged.node.tag)
        && java.util.Objects.equals(dragged.bind, context.get("bind"));
    float duration = recipe.duration / Math.max(.1f, settings.menu.animationSpeed);
    if (draggingThis) duration = Math.min(duration, .035f);
    return clamp(
        state.track.to(target, time, duration, recipe.easing,
            instantMotion() || suppressMotion || !recipe.value),
        0, 1);
  }

  private String repeatKey(String template, Map<String, Object> context, Object value, int index) {
    return template != null
        ? bindings.text(template, context)
        : value instanceof UiCatalog.Module
            ? ((UiCatalog.Module) value).id
            : value instanceof UiCatalog.Control
                ? ((UiCatalog.Control) value).bind
                : value instanceof Map && ((Map<?, ?>) value).containsKey("id")
                    ? String.valueOf(((Map<?, ?>) value).get("id"))
                    : String.valueOf(index);
  }

  private int retainedCount(String source, List<?> values) {
    int count = Math.min(512, values.size());
    if (instantMotion() || suppressMotion) return count;
    for (RepeatMemory memory : retainedRepeats.values())
      if (source.equals(memory.source)) {
        Set<String> live = new HashSet<String>();
        for (int i = 0; i < Math.min(512, values.size()); i++) {
          Map<String, Object> context = SkinBindings.childContext(memory.context);
          context.put(memory.alias, values.get(i));
          context.put("index", i);
          live.add(repeatKey(memory.keyTemplate, context, values.get(i), i));
        }
        int rows = live.size();
        for (RepeatRow row : memory.rows.values())
          if (!live.contains(row.key)) {
            if (Double.isNaN(row.retiredAt)) {
              row.retiredAt = time;
              retentionGeneration++;
            }
            if (time - row.retiredAt < memory.exitDuration) rows++;
          }
        count = Math.max(count, rows);
      }
    return Math.min(1024, count);
  }

  private void layout(
      Frame frame, float x, float y, float availableWidth, float availableHeight, boolean root) {
    layout(frame, x, y, availableWidth, availableHeight, -1, -1, root);
  }

  private void layout(Frame frame, float x, float y, float availableWidth, float availableHeight,
      float allocatedWidth, float allocatedHeight, boolean root) {
    if (frame.parent != null && frame.ownsBox) {
      SkinBindings.set(frame.context, "boxWidth", frame.parent.w);
      SkinBindings.set(frame.context, "boxHeight", frame.parent.h);
      bindings.dimensionsChanged(frame.context);
      if (!frame.dimensionStyles.isEmpty()) {
        frame.measuredWidth = Float.NaN;
        for (BoundProperty entry : frame.dimensionStyles)
          frame.style.put(entry.name, bindings.text(entry.text, frame.context));
      }
    }
    Map<String, String> style = frame.style;
    frame.flowPositionReflow = false;
    frame.w = allocatedWidth >= 0 ? allocatedWidth
                                  : dimension(style.get("width"), availableWidth, availableWidth);
    frame.w = clamp(frame.w, dimension(style.get("min-width"), availableWidth, 0),
        dimension(style.get("max-width"), availableWidth, 8192));
    frame.horizontalReflow = frame.parent != null && frame.parent.horizontalReflow;
    if ("true".equals(style.get("layout-reflow"))) frame.w = animatedLayoutWidth(frame, frame.w);
    frame.h = allocatedHeight >= 0 ? allocatedHeight
                                   : preferredHeight(frame, availableHeight, frame.w, root, false);
    frame.h = clamp(frame.h, dimension(style.get("min-height"), availableHeight, 0),
        dimension(style.get("max-height"), availableHeight, 8192));

    if ("overlay".equals(style.get("position"))
        && (frame.w > viewportWidth || frame.h > viewportHeight)) {
      frame.w = Math.min(frame.w, viewportWidth);
      frame.h = Math.min(frame.h, viewportHeight);
      if (!style.containsKey("overflow") || "visible".equals(style.get("overflow")))
        style.put("overflow", "auto");
    }
    if (frame.parent != null
        && (outOfFlow(frame) || "absolute".equals(frame.parent.style.get("layout"))))
      frame.h = animatedLayoutHeight(frame, frame.h);
    frame.x = x + dimension(first(style, "x", "left"), availableWidth, 0);
    frame.y = y + dimension(first(style, "y", "top"), availableHeight, 0);
    if (style.containsKey("right"))
      frame.x = x + availableWidth - frame.w - dimension(style.get("right"), availableWidth, 0);
    if (style.containsKey("bottom"))
      frame.y = y + availableHeight - frame.h - dimension(style.get("bottom"), availableHeight, 0);
    frame.x -= frame.w * number(style, "anchor-x", 0);
    frame.y -= frame.h * number(style, "anchor-y", 0);
    float contentH = frame.flowSlotClip && frame.naturalHeight > frame.h + 0.05f ? frame.naturalHeight
                                                                               : frame.h;
    if (frame.ownsBox) {
      SkinBindings.set(frame.context, "boxWidth", frame.w);
      SkinBindings.set(frame.context, "boxHeight", contentH);
      bindings.dimensionsChanged(frame.context);
    }
    if (frame.id != null) recordLayoutBox(frame.id, frame.x, frame.y, frame.w, frame.h);
    float[] padding = padding(style);
    float gap = number(style, "gap", 0), innerW = Math.max(0, frame.w - padding[1] - padding[3]),
          innerH = Math.max(0, contentH - padding[0] - padding[2]);
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (child.ownsBox) {
        SkinBindings.set(child.context, "boxWidth", innerW);
        SkinBindings.set(child.context, "boxHeight", innerH);
        bindings.dimensionsChanged(child.context);
        if (!child.dimensionStyles.isEmpty()) {
          child.measuredWidth = Float.NaN;
          for (BoundProperty entry : child.dimensionStyles)
            child.style.put(entry.name, bindings.text(entry.text, child.context));
        }
      }
    }
    if ("grid".equals(style.get("layout"))) {
      layoutGrid(frame, padding, gap, innerW, innerH);
      return;
    }
    if ("masonry".equals(style.get("layout"))) {
      layoutMasonry(frame, padding, gap, innerW, innerH);
      return;
    }
    boolean row = isRow(frame);
    boolean absolute = "absolute".equals(style.get("layout"));
    float fixed = 0, flex = 0;
    int flowCount = 0;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (!absolute && !outOfFlow(child)) {
        flowCount++;
        float grow = number(child.style, "flex", number(child.style, "grow", 0));
        flex += grow;
        fixed += grow > 0 ? 0
            : row ? preferredWidth(child, innerW)
                  : preferredHeight(child, innerH, innerW, false, true) * retirementPresence(child);
      }
    }
    fixed += Math.max(0, flowCount - 1) * gap;
    float remaining = Math.max(0, (row ? innerW : innerH) - fixed), cursor = 0;
    frame.scrollMax =
        row ? 0 : Math.max(0, fixed + Math.max(0, remaining) * (flex > 0 ? 1 : 0) - innerH);
    String justify = first(style, "justify-content", "justify");
    if (flex == 0 && "center".equals(justify))
      cursor = remaining / 2;
    else if (flex == 0 && "end".equals(justify))
      cursor = remaining;
    float scrolling = scrollOffset(frame), content = 0;
    boolean precedingHeightTransition = false;
    float sumPresence = 0f;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (absolute || outOfFlow(child)) {
        layout(child, frame.x + padding[3], frame.y + padding[0], innerW, innerH, false);
        continue;
      }
      float grow = number(child.style, "flex", number(child.style, "grow", 0));
      float cw = row
          ? (grow > 0 && flex > 0 ? remaining * grow / flex : preferredWidth(child, innerW))
          : dimension(child.style.get("width"), innerW, innerW);
      float fullCh = row ? preferredHeight(child, innerH, cw, false, false)
                         : (grow > 0 && flex > 0 ? remaining * grow / flex
                                                 : preferredHeight(child, innerH, cw, false, true));
      float ch = fullCh;
      if (!row && grow <= 0) ch = verticalSlotHeight(child, fullCh);
      if (!row) {
        cursor += gap * Math.min(Math.min(1f, sumPresence), child.flowPresence);
      } else if (sumPresence > 0f) {
        cursor += gap;
      }
      float cross = 0;
      String align = first(style, "align-items", "align");
      float space = row ? innerH - ch : innerW - cw;
      if ("center".equals(align))
        cross = space / 2;
      else if ("end".equals(align))
        cross = space;
      layout(child, frame.x + padding[3] + (row ? cursor : cross),
          frame.y + padding[0] + (row ? cross : cursor) - scrolling, innerW, innerH, cw, ch, false);
      if (!row) {
        child.flowPositionReflow = precedingHeightTransition;
        precedingHeightTransition |= child.layoutTransition;
      }
      cursor += (row ? child.w : child.h);
      sumPresence += row ? 1f : child.flowPresence;
      content = Math.max(content, cursor);
    }
    if (!row) frame.layoutTransition |= precedingHeightTransition;
    frame.scrollMax = row ? 0 : Math.max(0, content - innerH);
    if (scrolling > frame.scrollMax) scroll.put(frame.key, frame.scrollMax);
  }

  private void layoutGrid(Frame frame, float[] padding, float gap, float innerW, float innerH) {
    List<Frame> flow = new ArrayList<Frame>(frame.children.size());
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (!outOfFlow(child)) flow.add(child);
    }
    int columns = gridColumns(frame, innerW, gap), rowCount = (flow.size() + columns - 1) / columns;
    float cellWidth = Math.max(0, (innerW - gap * (columns - 1)) / columns);
    float[] heights = new float[rowCount];
    float[] childHeights = new float[flow.size()];
    boolean[] rowTransition = new boolean[rowCount];
    for (int i = 0; i < flow.size(); i++) {
      Frame item = flow.get(i);
      float h = gridHeight(item, cellWidth, innerH);
      childHeights[i] = h;
      int row = i / columns;
      heights[row] = Math.max(heights[row], h);
      rowTransition[row] |= item.layoutTransition;
    }
    float content = 0;
    if (columns == 1) {
      float sumP = 0f;
      for (int i = 0; i < flow.size(); i++) {
        float p = flow.get(i).flowPresence;
        content += gap * Math.min(Math.min(1f, sumP), p) + childHeights[i];
        sumP += p;
      }
    } else {
      content = gap * Math.max(0, rowCount - 1);
      for (float height : heights) content += height;
    }
    frame.scrollMax = Math.max(0, content - innerH);
    float scrolling = clamp(scrollOffset(frame), 0, frame.scrollMax);

    float cursor = 0;
    int index = 0;
    float sumPresence = 0f;
    boolean precedingRowTransition = false;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (outOfFlow(child)) {
        layout(child, frame.x + padding[3], frame.y + padding[0], innerW, innerH, false);
        continue;
      }
      int column = index % columns, row = index / columns;
      float childHeight = childHeights[index];
      if (columns == 1) {
        cursor += gap * Math.min(Math.min(1f, sumPresence), child.flowPresence);
      }
      layout(child, frame.x + padding[3] + column * (cellWidth + gap),
          frame.y + padding[0] + cursor - scrolling, cellWidth, innerH, cellWidth, childHeight,
          false);
      child.flowPositionReflow = precedingRowTransition;
      rowTransition[row] |= child.layoutTransition;
      if (columns == 1) {
        cursor += childHeight;
        sumPresence += child.flowPresence;
        precedingRowTransition |= rowTransition[row];
      } else if (column == columns - 1 || index == flow.size() - 1) {
        if (column == columns - 1) cursor += heights[row] + gap;
        precedingRowTransition |= rowTransition[row];
      }
      index++;
    }
    frame.layoutTransition |= precedingRowTransition;
  }

  private static boolean matchesFlowKeys(Frame frame, Set<String> keys) {
    if (keys == null) return false;
    int unique = 0;
    Set<String> seen = null;
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (outOfFlow(child)) continue;
      if (!keys.contains(child.key)) return false;
      if (seen == null) seen = new HashSet<String>(keys.size() * 2 + 1);
      if (seen.add(child.key)) unique++;
    }
    return unique == keys.size();
  }

  private Masonry resolveStableMasonry(Frame frame, int columns) {
    Masonry stable = masonry.get(frame.key);
    if (stable != null && stable.columns == columns && matchesFlowKeys(frame, stable.keys))
      return stable;
    Set<String> currentKeys = new HashSet<String>(frame.children.size() * 2 + 1);
    for (int i = 0, size = frame.children.size(); i < size; i++) {
      Frame child = frame.children.get(i);
      if (!outOfFlow(child)) currentKeys.add(child.key);
    }
    stable = new Masonry(columns);
    stable.keys = currentKeys;
    masonry.put(frame.key, stable);
    return stable;
  }

  private static int chooseMasonryColumn(boolean stableMode, Masonry stable, Frame child,
      int columns, int[] columnCounts, float[] heights, boolean record) {
    if (stableMode) {
      Integer saved = stable == null ? null : stable.assigned.get(child.key);
      int column;
      if (saved != null && saved >= 0 && saved < columns) {
        column = saved;
      } else {
        column = 0;
        for (int c = 1; c < columns; c++)
          if (columnCounts[c] < columnCounts[column]) column = c;
        if (record && stable != null) stable.assigned.put(child.key, column);
      }
      columnCounts[column]++;
      return column;
    }
    int column = 0;
    for (int c = 1; c < columns; c++)
      if (heights[c] < heights[column]) column = c;
    return column;
  }

  private void layoutMasonry(Frame frame, float[] padding, float gap, float innerW, float innerH) {
    int columns = gridColumns(frame, innerW, gap);
    float cell = Math.max(0, (innerW - gap * (columns - 1)) / columns);
    float[] heights = new float[columns];
    int[] columnCounts = new int[columns];
    Map<Frame, Integer> assigned = new java.util.IdentityHashMap<Frame, Integer>();
    Map<Frame, Float> measuredHeights = new java.util.IdentityHashMap<Frame, Float>();
    boolean stableMode = "true".equals(frame.style.get("masonry-stable"));
    Masonry stable = stableMode ? resolveStableMasonry(frame, columns) : null;
    for (Frame child : frame.children)
      if (!outOfFlow(child)) {
        int column =
            chooseMasonryColumn(stableMode, stable, child, columns, columnCounts, heights, true);
        assigned.put(child, column);
        float h = gridHeight(child, cell, innerH);
        measuredHeights.put(child, h);
        heights[column] += h + gap;
      }
    if (stable != null && stable.assigned.size() > MAX_FRAMES) {
      stable.assigned.clear();
      for (Map.Entry<Frame, Integer> entry : assigned.entrySet())
        stable.assigned.put(entry.getKey().key, entry.getValue());
    }
    float content = 0;
    for (float height : heights) content = Math.max(content, Math.max(0, height - gap));
    frame.scrollMax = Math.max(0, content - innerH);
    float scrolling = clamp(scrollOffset(frame), 0, frame.scrollMax);
    Arrays.fill(heights, 0);
    boolean[] colTransition = new boolean[columns];
    for (Frame child : frame.children) {
      if (!assigned.containsKey(child)) {
        layout(child, frame.x + padding[3], frame.y + padding[0], innerW, innerH, false);
        continue;
      }
      int column = assigned.get(child);
      float h = measuredHeights.get(child);
      layout(child, frame.x + padding[3] + column * (cell + gap),
          frame.y + padding[0] + heights[column] - scrolling, cell, innerH, cell, h, false);
      child.flowPositionReflow = colTransition[column];
      colTransition[column] |= child.layoutTransition;
      heights[column] += h + gap;
    }
    for (boolean tr : colTransition) frame.layoutTransition |= tr;
  }

  private float gridHeight(Frame child, float width, float availableHeight) {
    float target = clamp(preferredHeight(child, availableHeight, width, false, true),
        dimension(child.style.get("min-height"), availableHeight, 0),
        dimension(child.style.get("max-height"), availableHeight, 8192));
    return verticalSlotHeight(child, target);
  }

  private float peekGridHeight(Frame child, float width, float availableHeight) {
    float target = clamp(preferredHeight(child, availableHeight, width, false, true),
        dimension(child.style.get("min-height"), availableHeight, 0),
        dimension(child.style.get("max-height"), availableHeight, 8192));
    if (!Double.isNaN(child.retiredAt)) {
      float presence = retirementPresence(child);
      float baseH = child.retireStartHeight > 0f ? child.retireStartHeight : target;
      return Math.max(0f, baseH * presence);
    }
    if (child.hudPlaced || !motionEnabled() || instantMotion()) return target;
    if (menuScope && isAutoHeight(child) && isVerticalFlow(child) && hasFlowSlotDescendant(child))
      return Math.max(0f, target);
    Motion cm = motion.get(child.key);
    if (cm == null) return Math.max(0f, target);
    dev.nightclient.config.MotionSettings recipe =
        themeResolver.theme(child.node, settings.theme, "").motion;
    float duration = recipe.duration / Math.max(.1f, settings.menu.animationSpeed);
    boolean reduced = !cm.slotOpening && !recipe.layout;
    return Math.max(0f, cm.layoutHeight.peek(target, time, duration, reduced));
  }

  private float verticalSlotHeight(Frame child, float fullTarget) {
    if (!Double.isNaN(child.retiredAt)) {
      float presence = retirementPresence(child);
      float baseH = child.retireStartHeight > 0f ? child.retireStartHeight : fullTarget;
      float h = Math.max(0f, baseH * presence);
      child.naturalHeight = fullTarget;
      child.flowSlotClip = true;
      child.flowPresence = presence;
      child.heightAnimated = true;
      child.layoutTransition = true;
      Motion cm = motion.get(child.key);
      if (cm != null) {
        cm.layoutHeight.to(h, time, 0f, "linear", true);
        cm.previousLayoutHeight = h;
      }
      return h;
    }
    return animatedLayoutHeight(child, fullTarget);
  }

  private float peekSlotHeight(Frame child, float rawH, float[] presenceOut) {
    if (!Double.isNaN(child.retiredAt)) {
      float presence = retirementPresence(child);
      float baseH = child.retireStartHeight > 0f ? child.retireStartHeight : rawH;
      if (presenceOut != null) presenceOut[0] = presence;
      return Math.max(0f, baseH * presence);
    }
    if (menuScope && motionEnabled() && !instantMotion()) {
      Motion cm = motion.get(child.key);
      if (cm != null && cm.slotOpening) {
        dev.nightclient.config.MotionSettings recipe =
            themeResolver.theme(child.node, settings.theme, "").motion;
        float h = Math.max(0f,
            cm.layoutHeight.to(rawH, time,
                recipe.duration / Math.max(.1f, settings.menu.animationSpeed), recipe.easing,
                false));
        if (presenceOut != null)
          presenceOut[0] = rawH > 0.01f ? clamp(h / rawH, 0f, 1f) : 1f;
        return h;
      }
    }
    if (presenceOut != null) presenceOut[0] = 1f;
    return rawH;
  }

  private float retirementPresence(Frame frame) {
    return Double.isNaN(frame.retiredAt) ? 1
                                         : 1
            - MotionTrack.ease(
                clamp(
                    (float) ((time - frame.retiredAt) / Math.max(.001, frame.exitDuration)), 0, 1),
                frame.exitEasing);
  }

  private float animatedLayoutWidth(Frame frame, float target) {
    if (!motionEnabled()) return target;
    dev.nightclient.config.MotionSettings recipe =
        themeResolver.theme(frame.node, settings.theme, "").motion;
    Motion animation = motion.get(frame.key);
    if (animation == null) {
      animation = new Motion(time, renderScope);
      motion.put(frame.key, animation);
    }
    if (animation.layoutWidth == null) animation.layoutWidth = new MotionTrack();
    float width = animation.layoutWidth.to(target, time,
        recipe.duration / Math.max(.1f, settings.menu.animationSpeed), recipe.easing,
        instantMotion() || !recipe.layout || frame.horizontalReflow);
    boolean changed =
        Float.isFinite(animation.previousLayoutWidth) && width != animation.previousLayoutWidth;
    animation.previousLayoutWidth = width;
    frame.horizontalReflow |= recipe.layout && (changed || animation.layoutWidth.inFlight(time));
    frame.widthAnimated = recipe.layout;
    return Math.max(0, width);
  }

  private float animatedLayoutHeight(Frame frame, float target) {
    if (frame.hudPlaced || !motionEnabled() || instantMotion()) return target;
    dev.nightclient.config.MotionSettings recipe =
        themeResolver.theme(frame.node, settings.theme, "").motion;
    Motion animation = motion.get(frame.key);
    if (animation == null) {
      animation = new Motion(time, renderScope);
      motion.put(frame.key, animation);
    }
    if (menuScope && isAutoHeight(frame) && isVerticalFlow(frame) && hasFlowSlotDescendant(frame)) {
      animation.layoutHeight.to(target, time, 0f, recipe.easing, true);
      animation.previousLayoutHeight = target;
      frame.heightAnimated = true;
      frame.layoutTransition = true;
      return Math.max(0f, target);
    }
    if (animation.slotOpening) {
      float height = animation.layoutHeight.to(target, time,
          recipe.duration / Math.max(.1f, settings.menu.animationSpeed), recipe.easing, false);
      boolean changed =
          Float.isFinite(animation.previousLayoutHeight) && height != animation.previousLayoutHeight;
      animation.previousLayoutHeight = height;
      boolean inFlight = animation.layoutHeight.inFlight(time);
      if (!inFlight) animation.slotOpening = false;
      frame.heightAnimated = true;
      frame.layoutTransition = inFlight || changed || Math.abs(height - target) > 0.05f;
      if (inFlight || height < target - 0.05f) {
        frame.naturalHeight = target;
        frame.flowSlotClip = true;
        frame.flowPresence = target > 0.01f ? clamp(height / target, 0f, 1f) : 1f;
      } else {
        frame.flowPresence = 1f;
      }
      return Math.max(0f, height);
    }
    float height = animation.layoutHeight.to(target, time,
        recipe.duration / Math.max(.1f, settings.menu.animationSpeed), recipe.easing,
        !recipe.layout);
    boolean changed =
        Float.isFinite(animation.previousLayoutHeight) && height != animation.previousLayoutHeight;
    animation.previousLayoutHeight = height;
    frame.heightAnimated = recipe.layout;
    frame.layoutTransition = recipe.layout && (changed || animation.layoutHeight.inFlight(time));
    return Math.max(0, height);
  }

  private static boolean outOfFlow(Frame frame) {
    String position = frame.style.get("position");
    return "absolute".equals(position) || "overlay".equals(position);
  }

  private boolean instantMotion() {
    return settings.menu.animationSpeed >= 100000f;
  }

  private boolean motionEnabled() {
    return !suppressMotion && (entrance || !menuScope);
  }

  private static boolean hasAction(Frame frame) {
    return frame.attributes.containsKey("action")
        || frame.attributes.containsKey("secondary-action")
        || frame.attributes.containsKey("middle-action");
  }

  private static boolean isControl(Frame frame) {
    String tag = frame.node.tag;
    return hasAction(frame) || "button".equals(tag) || "toggle".equals(tag) || "switch".equals(tag)
        || "slider".equals(tag) || "input".equals(tag) || "choice".equals(tag)
        || "color".equals(tag) || "pixel-grid".equals(tag) || "player-preview".equals(tag)
        || pickerControl(tag);
  }

  private static boolean insideControl(Frame frame) {
    for (Frame parent = frame.parent; parent != null; parent = parent.parent)
      if (isControl(parent)) return true;
    return false;
  }

  private static final java.util.Comparator<Frame> OVERLAY_ORDER =
      new java.util.Comparator<Frame>() {
        public int compare(Frame a, Frame b) {
          return Float.compare(number(a.style, "z-index", 0), number(b.style, "z-index", 0));
        }
      };
  private static final java.util.Comparator<RepeatRow> RETIRING_ORDER =
      new java.util.Comparator<RepeatRow>() {
        public int compare(RepeatRow a, RepeatRow b) {
          return Integer.compare(a.index, b.index);
        }
      };

  private float scrollOffset(Frame frame) {
    Float stored = scroll.get(frame.key);
    MotionTrack track = scrollMotion.get(frame.key);
    if (stored == null && track == null && frame.scrollMax <= 0f) return 0f;
    float target = clamp(stored != null ? stored.floatValue() : 0f, 0f, Math.max(0f, frame.scrollMax));
    if (stored != null && target != stored.floatValue()) scroll.put(frame.key, target);
    if (track == null) {
      if (target == 0f) return 0f;
      track = new MotionTrack(0);
      scrollMotion.put(frame.key, track);
    }
    float duration = duration(frame.style.get("scroll-duration"), 0);
    return track.to(target, time, duration,
        frame.style.containsKey("easing") ? frame.style.get("easing") : "cubic-out",
        instantMotion());
  }

  private static float duration(String value, float fallback) {
    if (value == null) return fallback;
    try {
      String number = value.trim();
      return number.endsWith("ms")
          ? Float.parseFloat(number.substring(0, number.length() - 2)) * .001f
          : number.endsWith("s") ? Float.parseFloat(number.substring(0, number.length() - 1))
                                 : Float.parseFloat(number);
    } catch (NumberFormatException invalid) {
      return fallback;
    }
  }

  private static int gridColumns(Frame frame, float innerW, float gap) {
    int columns = Math.max(1, Math.min(16, Math.round(number(frame.style, "columns", 2))));
    float minimum = number(frame.style, "min-cell-width", 0);
    return minimum <= 0 ? columns
                        : Math.max(1, Math.min(columns, (int) ((innerW + gap) / (minimum + gap))));
  }

  private float preferredWidth(Frame frame, float available) {
    float value = dimension(frame.style.get("width"), available, Float.NaN);
    if (Float.isNaN(value)) value = defaultWidth(frame, available);
    return clamp(value, dimension(frame.style.get("min-width"), available, 0),
        dimension(frame.style.get("max-width"), available, 8192));
  }

  private float preferredHeight(
      Frame frame, float available, float width, boolean root, boolean raw) {
    float value = dimension(frame.style.get("height"), available, Float.NaN);
    return Float.isNaN(value) ? root ? available
            : raw                    ? measureRaw(frame, width)
                                     : measure(frame, width)
                              : value;
  }

  private float measure(Frame frame, float width) {
    return measureRaw(frame, width) * (!outOfFlow(frame) ? retirementPresence(frame) : 1);
  }

  private float measureRaw(Frame frame, float width) {
    if (Float.compare(frame.measuredWidth, width) == 0) return frame.measuredHeight;
    float measured = computeMeasureRaw(frame, width);
    frame.measuredWidth = width;
    frame.measuredHeight = measured;
    return measured;
  }

  private float computeMeasureRaw(Frame frame, float width) {
    if (frame.style.containsKey("height") && !"auto".equals(frame.style.get("height")))
      return dimension(frame.style.get("height"), 8192, 32);
    if (frame.children.isEmpty()) {
      String tag = frame.node.tag;
      if ("repeat".equals(tag)) return 0;
      if ("text".equals(tag) || "label".equals(tag))
        return number(frame.style, "font-size", 12) * number(frame.style, "font-scale", 1) + 4;
      if ("spacer".equals(tag)) return 8;
      if ("icon".equals(tag) || "item".equals(tag)) return 20;
      if ("progress".equals(tag) || "line".equals(tag)) return 3;
      if ("inventory".equals(tag)) return 112;
      if ("cooldowns".equals(tag) || "tnt".equals(tag)) return 96;
      if ("crosshair".equals(tag)) return 64;
      if ("pixel-grid".equals(tag)) return Math.min(280, width);
      return 34;
    }
    float result = 0, gap = number(frame.style, "gap", 0);
    float[] padding = padding(frame.style);
    boolean row = isRow(frame);
    if ("masonry".equals(frame.style.get("layout"))) {
      float innerW = Math.max(0, width - padding[1] - padding[3]);
      int columns = gridColumns(frame, innerW, gap);
      float cell = Math.max(0, (innerW - gap * (columns - 1)) / columns);
      float[] heights = new float[columns];
      int[] columnCounts = new int[columns];
      boolean stableMode = "true".equals(frame.style.get("masonry-stable"));
      Masonry existing = stableMode ? masonry.get(frame.key) : null;
      if (existing != null
          && (existing.columns != columns || !matchesFlowKeys(frame, existing.keys)))
        existing = null;
      for (Frame child : frame.children)
        if (!outOfFlow(child)) {
          int c = chooseMasonryColumn(
              stableMode, existing, child, columns, columnCounts, heights, false);
          heights[c] += peekGridHeight(child, cell, 8192) + gap;
        }
      float h = 0;
      for (float value : heights) h = Math.max(h, Math.max(0, value - gap));
      return padding[0] + padding[2] + h;
    }
    if ("grid".equals(frame.style.get("layout"))) {
      float innerW = Math.max(0, width - padding[1] - padding[3]);
      int columns = gridColumns(frame, innerW, gap), index = 0;
      float cellWidth = Math.max(0, (innerW - gap * (columns - 1)) / columns), rowHeight = 0;
      if (columns == 1) {
        float sumP = 0f;
        float[] pOut = new float[1];
        for (Frame child : frame.children)
          if (!outOfFlow(child)) {
            float rawChild = clamp(preferredHeight(child, 8192, cellWidth, false, true),
                dimension(child.style.get("min-height"), 8192, 0),
                dimension(child.style.get("max-height"), 8192, 8192));
            float sh = peekSlotHeight(child, rawChild, pOut);
            result += gap * Math.min(Math.min(1f, sumP), pOut[0]) + sh;
            sumP += pOut[0];
          }
        return padding[0] + padding[2] + result;
      }
      for (Frame child : frame.children)
        if (!outOfFlow(child)) {
          rowHeight = Math.max(rowHeight, gridHeight(child, cellWidth, 8192));
          if (++index % columns == 0) {
            result += rowHeight + gap;
            rowHeight = 0;
          }
        }
      if (index % columns != 0) result += rowHeight + gap;
      return padding[0] + padding[2] + Math.max(0, result - (index == 0 ? 0 : gap));
    }
    if (row) {
      for (Frame child : frame.children)
        if (!outOfFlow(child)) result = Math.max(result, measure(child, width));
      return padding[0] + padding[2] + result;
    }
    float innerW = Math.max(0, width - padding[1] - padding[3]);
    float sumP = 0f;
    float[] pOut = new float[1];
    for (Frame child : frame.children)
      if (!outOfFlow(child)) {
        float rawChild = preferredHeight(child, 8192, innerW, false, true);
        float sh = peekSlotHeight(child, rawChild, pOut);
        result += gap * Math.min(Math.min(1f, sumP), pOut[0]) + sh;
        sumP += pOut[0];
      }
    return padding[0] + padding[2] + result;
  }

  private float defaultWidth(Frame child, float available) {
    String tag = child.node.tag;
    if ("spacer".equals(tag)) return 8;
    if ("icon".equals(tag) || "item".equals(tag)) return Math.min(available, 20);
    if ("text".equals(tag) || "label".equals(tag)) {
      String label = bindings.text(child.node.attributes.containsKey("text")
              ? child.node.attributes.get("text")
              : child.node.text,
          child.context);
      float font = number(child.style, "font-size", 12) * number(child.style, "font-scale", 1)
          * ("menu".equals(renderScope) ? settings.menu.fontScale : 1f);
      String weight = child.style.get("font-weight");
      boolean bold = "bold".equals(weight) || "semibold".equals(weight) || "600".equals(weight);
      float tracking = number(child.style, "letter-spacing",
          number(child.style, "font-tracking", settings.theme.fontTracking));
      String family = child.style.containsKey("font-family") ? child.style.get("font-family")
                                                             : settings.theme.fontFamily;
      float horizPad = 16f;
      if (child.style.containsKey("padding")) {
        float[] p = padding(child.style);
        horizPad = p[1] + p[3];
      }
      return Math.min(
          available, measuringCanvas.textWidthFamily(family, label, font, bold, tracking) + horizPad);
    }
    return Math.min(available, 120);
  }

  private static boolean isRow(Frame frame) {
    return "row".equals(frame.node.tag)
        || "row".equals(first(frame.style, "flex-direction", "direction"))
        || "row".equals(frame.style.get("layout"));
  }

  private final java.util.IdentityHashMap<SkinDocument.Node, PaintNodePlan> paintPlans =
      new java.util.IdentityHashMap<SkinDocument.Node, PaintNodePlan>();
  private final java.util.IdentityHashMap<Map<String, String>, PaintStyleMap.Plan> paintStylePlans =
      new java.util.IdentityHashMap<Map<String, String>, PaintStyleMap.Plan>();

  private PaintStyleMap.Plan paintStylePlan(Map<String, String> source) {
    if (source.isEmpty()) return null;
    PaintStyleMap.Plan plan = paintStylePlans.get(source);
    if (plan == null) {
      plan = new PaintStyleMap.Plan(source);
      paintStylePlans.put(source, plan);
    }
    return plan;
  }

  private Map<String, String> paintStyles(Frame frame, String state) {
    if (state.isEmpty() && frame.presentation == null && frame.dimensionStyles.isEmpty())
      return frame.style;
    Map<String, String> node = state.isEmpty() && frame.presentation == null
        ? boundStyles(frame.node)
        : visualStyles(frame.node, state);
    Map<String, String> presentation = frame.presentation == null
        ? Collections.<String, String>emptyMap()
        : visualStyles(frame.presentation, state);
    if (node.isEmpty() && presentation.isEmpty()) return frame.style;
    if (frame.paintStyles == null) frame.paintStyles = new PaintStyleMap();
    frame.paintStyles.reset(
        frame.style, paintStylePlan(node), paintStylePlan(presentation), bindings, frame.context);
    return frame.paintStyles;
  }

  private PaintNodePlan paintPlan(SkinDocument.Node node) {
    PaintNodePlan plan = paintPlans.get(node);
    if (plan != null) return plan;
    boolean structural = "hud".equals(node.tag) || "component".equals(node.tag)
        || "div".equals(node.tag) || "row".equals(node.tag) || "column".equals(node.tag)
        || "repeat".equals(node.tag) || "spacer".equals(node.tag);
    boolean motionOnly = "item".equals(node.tag) || "entity-head".equals(node.tag);
    boolean undecorated = !node.attributes.containsKey("action")
        && !node.attributes.containsKey("secondary-action")
        && !node.attributes.containsKey("middle-action")
        && !node.attributes.containsKey("background")
        && !node.attributes.containsKey("background-color");
    structural &= undecorated;
    motionOnly &= undecorated;
    boolean dynamicMotion = false, dynamicMargins = false;
    SkinDocument.ThemeBindingPlan[] plans = new SkinDocument.ThemeBindingPlan[8];
    for (int i = 0; i < 8; i++) {
      String state = PAINT_STATES[i];
      Map<String, String> styles = document.styles(node, state);
      if (styles.containsKey("background") || styles.containsKey("background-color")) {
        structural = false;
        motionOnly = false;
      }
      SkinDocument.ThemeBindingPlan bindings = document.themeBindingPlan(node, state);
      plans[i] = bindings;
      if (bindings != null)
        for (int key = 0; key < bindings.size(); key++) {
          String property = bindings.key(key);
          if (motionProperty(property)) dynamicMotion = true;
          if ("shadow-radius".equals(property) || "glow-radius".equals(property))
            dynamicMargins = true;
        }
    }
    plan = new PaintNodePlan(
        structural, dynamicMotion, motionOnly && !dynamicMotion && !dynamicMargins, plans);
    paintPlans.put(node, plan);
    return plan;
  }

  private static boolean motionProperty(String key) {
    return "content-opacity".equals(key) || "text-motion".equals(key) || "easing".equals(key)
        || "transition-duration".equals(key) || "enter-duration".equals(key)
        || "exit-duration".equals(key) || "animation-stagger".equals(key)
        || "animate-layout".equals(key) || "animate-value".equals(key)
        || "enter-scale".equals(key) || "hover-scale".equals(key) || "press-scale".equals(key)
        || "enter-opacity".equals(key) || "enter-offset-x".equals(key)
        || "enter-offset-y".equals(key);
  }

  private static final String[] PAINT_STATES = {"", "hover", "focus", "hover focus", "active",
      "hover active", "focus active", "hover focus active"};

  private static final class PaintNodePlan {
    final boolean structural, dynamicMotion, motionOnly;
    final SkinDocument.ThemeBindingPlan[] bindingPlans;

    PaintNodePlan(boolean structural, boolean dynamicMotion, boolean motionOnly,
        SkinDocument.ThemeBindingPlan[] bindingPlans) {
      this.structural = structural;
      this.dynamicMotion = dynamicMotion;
      this.motionOnly = motionOnly;
      this.bindingPlans = bindingPlans;
    }
  }

  private Theme resolvedTheme(SkinDocument.Node node, Theme base, String state,
      Map<String, String> visual, Map<String, Object> context) {
    SkinDocument.ThemeBindingPlan plan = paintPlan(node).bindingPlans[paintStateIndex(state)];
    return resolvedThemeWithPlan(node, base, state, plan, visual, context);
  }

  private Theme resolvedTheme(SkinDocument.Node node, PaintNodePlan nodePlan, Theme base,
      int stateIndex, String state, Map<String, String> visual, Map<String, Object> context) {
    return resolvedThemeWithPlan(
        node, base, state, nodePlan.bindingPlans[stateIndex], visual, context);
  }

  private Theme resolvedThemeWithPlan(SkinDocument.Node node, Theme base, String state,
      SkinDocument.ThemeBindingPlan plan, Map<String, String> visual, Map<String, Object> context) {
    if (plan == null) return themeResolver.theme(node, base, state);
    if (themeValues.length < plan.size()) themeValues = new String[plan.size()];
    for (int i = 0; i < plan.size(); i++) {
      String value = visual.get(plan.key(i));
      themeValues[i] = value == null ? bindings.text(plan.expression(i), context) : value;
    }
    return themeResolver.resolvedTheme(plan, base, themeValues);
  }

  private static int paintStateIndex(String value) {
    if (value == null || value.isEmpty()) return 0;
    return (value.contains("hover") ? 1 : 0) | (value.contains("focus") ? 2 : 0)
        | (value.contains("active") ? 4 : 0);
  }

  private void paint(Frame frame, Canvas canvas, Bounds parentClip, boolean interactive,
      Transform parentTransform, Bounds physicalParentClip) {
    if (drawCommands >= 8192) return;
    if (physicalParentClip.w <= 0 || physicalParentClip.h <= 0) return;
    if (!Double.isNaN(frame.retiredAt)) {
      interactive = false;
      float progress =
          clamp((float) ((time - frame.retiredAt) / Math.max(.001, frame.exitDuration)), 0, 1);
      canvas = new PoseCanvas(
          canvas, Transform.IDENTITY, 1 - MotionTrack.ease(progress, frame.exitEasing));
    }
    boolean isFocused = focused != null && focused.key.equals(frame.key),
            isHovered = physicalParentClip.contains(mouseX, mouseY)
        && hoverContains(frame, frame.x, frame.y, frame.w, frame.h, parentTransform, mouseX, mouseY);
    int stateIndex = (isHovered ? 1 : 0) | (isFocused ? 2 : 0);
    boolean physicallyPressed =
        pressedKey.equals(frame.key) || dragged != null && dragged.key.equals(frame.key);
    boolean selected = selected(frame);
    if (physicallyPressed || selected) stateIndex |= 4;
    String state = PAINT_STATES[stateIndex];
    PaintNodePlan paintPlan = paintPlan(frame.node);
    boolean structural =
        !interactive && frame.presentation == null && frame.scrollMax == 0 && paintPlan.structural;
    Map<String, String> visual = paintStyles(frame, state);
    boolean simpleTheme = frame.presentation == null
        && (structural && !paintPlan.dynamicMotion || !interactive && paintPlan.motionOnly);
    Theme theme = simpleTheme
        ? themeResolver.theme(frame.node, settings.theme, state)
        : resolvedTheme(frame.node, paintPlan, settings.theme, stateIndex, state, visual,
              frame.context);
    if (frame.presentation != null)
      theme = resolvedTheme(frame.presentation, theme, state, visual, frame.context);
    String tag = frame.node.tag;
    Motion animation = suppressMotion ? new Motion(time, renderScope) : motion.get(frame.key);
    if (animation == null) {
      animation = new Motion(time, renderScope);
      motion.put(frame.key, animation);
    }
    if (!paintingGhost && !suppressMotion) {
      animation.lastSeen = time;
      animation.lastFrame = frame;
      animation.lastClip = physicalParentClip;
      animation.lastParentTransform = parentTransform;
      animation.retiredAt = Double.NaN;
      if (menuScope && isVerticalFlow(frame)) {
        List<Frame> snapshot = new ArrayList<Frame>(frame.children.size());
        for (int i = 0, size = frame.children.size(); i < size; i++) {
          Frame c = frame.children.get(i);
          if (!outOfFlow(c)) snapshot.add(c);
        }
        animation.lastFlowChildren = snapshot;
      }
    }
    dev.nightclient.config.MotionSettings recipe = theme.motion;
    if (!menuScope && !suppressMotion && frame.parent != null)
      hudExitDuration = Math.max(hudExitDuration, recipe.exitDuration);
    boolean reduced = instantMotion() || suppressMotion;
    float speed = Math.max(.1f, settings.menu.animationSpeed), duration = recipe.duration / speed;
    float hover = animation.hover.to(isHovered ? 1 : 0, time, duration, recipe.easing, reduced),
          pressed =
              animation.press.to(physicallyPressed ? 1 : 0, time, duration, recipe.easing, reduced);
    boolean enterActive = motionEnabled() && !paintingGhost && !reduced && recipe.enterDuration > 0;
    float enter = 1;
    if (enterActive) {
      float stagger = 0;
      if (recipe.stagger != 0) {
        Object idx = frame.context.get("index");
        if (idx instanceof Number) stagger = ((Number) idx).floatValue() * recipe.stagger;
      }
      enter = MotionTrack.ease(clamp((float) ((time - animation.created - stagger / speed)
                                   / Math.max(.001, recipe.enterDuration / speed)),
                               0, 1),
          recipe.easing);
    }
    if (enter < 1 || frame.layoutTransition || frame.horizontalReflow || frame.flowPositionReflow)
      hudFrameDynamic = true;
    boolean leafControl = "button".equals(tag) || "toggle".equals(tag) || "switch".equals(tag)
        || "slider".equals(tag) || "choice".equals(tag) || "input".equals(tag)
        || "color".equals(tag);
    float scale = leafControl && !reduced
        ? 1 + hover * (recipe.hoverScale - 1) + pressed * (recipe.pressScale - 1)
        : 1;
    if ("menu".equals(tag)) scale *= recipe.enterScale + (1 - recipe.enterScale) * enter;
    boolean enterGroup = "true".equals(visual.get("animate-enter"));
    float alpha = leafControl || "menu".equals(tag) || "panel".equals(tag) || enterGroup
        ? recipe.enterOpacity + (1 - recipe.enterOpacity) * enter
        : 1;
    if (visual.containsKey("content-opacity") || animation.opacity != null) {
      if (animation.opacity == null) animation.opacity = new MotionTrack();
      alpha *= animation.opacity.to(clamp(number(visual, "content-opacity", 1), 0, 1), time,
          duration, recipe.easing, reduced || !motionEnabled() || !recipe.value);
    }
    if (frame.flowSlotClip && frame.h <= 0.05f) return;
    if (Double.isNaN(frame.retiredAt) && frame.flowSlotClip) {
      alpha *= frame.flowPresence;
    }
    float dx = !frame.flowSlotClip && (leafControl || "menu".equals(tag) || enterGroup)
            ? recipe.enterOffsetX * (1 - enter)
            : 0,
          dy = !frame.flowSlotClip && (leafControl || "menu".equals(tag) || enterGroup)
            ? recipe.enterOffsetY * (1 - enter)
            : 0;
    float relativeX = frame.x - (frame.parent == null ? 0 : frame.parent.x),
          relativeY = frame.y - (frame.parent == null ? 0 : frame.parent.y);
    dx += animation.x.to(relativeX, time, duration, recipe.easing,
              reduced || frame.hudPlaced || !motionEnabled() || !recipe.layout
                  || frame.parent != null && frame.parent.horizontalReflow
                  || frame.horizontalReflow
                      && (visual.containsKey("right") || number(visual, "anchor-x", 0) != 0))
        - relativeX;
    dy += animation.y.to(relativeY, time, duration, recipe.easing,
              reduced || frame.hudPlaced || !motionEnabled() || !recipe.layout
                  || frame.flowPositionReflow)
        - relativeY;
    float sx = frame.w > 0 && !frame.widthAnimated ? scale
            * animation.width.to(frame.w, time, duration, recipe.easing,
                reduced || frame.hudPlaced || !motionEnabled() || !recipe.layout
                    || frame.horizontalReflow)
            / frame.w
                                                   : scale,
          sy = frame.h > 0 && !frame.heightAnimated ? scale
            * animation.height.to(frame.h, time, duration, recipe.easing,
                reduced || frame.hudPlaced || !motionEnabled() || !recipe.layout)
            / frame.h
                                                    : scale;

    Transform local = scale == 1 && dx == 0 && dy == 0 && sx == 1 && sy == 1
        ? Transform.IDENTITY
        : new Transform(frame.x + frame.w / 2 * (1 - scale) + dx,
              frame.y + frame.h / 2 * (1 - scale) + dy, sx, sy, frame.x, frame.y);
    Transform transform =
        local == Transform.IDENTITY ? parentTransform : parentTransform.compose(local);
    if (alpha != 1 || local != Transform.IDENTITY) canvas = new PoseCanvas(canvas, local, alpha);
    float tbx, tby, tbw, tbh;
    if (transform == Transform.IDENTITY) {
      tbx = frame.x;
      tby = frame.y;
      tbw = Math.max(0, frame.w);
      tbh = Math.max(0, frame.h);
    } else {
      tbx = transform.px(frame.x);
      tby = transform.py(frame.y);
      tbw = Math.max(0, Math.max(0, frame.w) * transform.sx);
      tbh = Math.max(0, Math.max(0, frame.h) * transform.sy);
    }
    Bounds transformedBounds = frame.id != null ? new Bounds(tbx, tby, tbw, tbh) : null;
    if (frame.id != null) paintedBoxes.put(frame.id, transformedBounds);
    if (menuScope && frame.parent != null && !frame.children.isEmpty() && !frame.layoutTransition
        && !"menu".equals(tag) && attr(frame, "dismiss-action", "").isEmpty()
        && !containsOverlayOrActiveHit(frame)) {
      float cullMargin = 24f;
      if (tbw <= 0 || tbh <= 0 || tbx + tbw < physicalParentClip.x - cullMargin
          || tby + tbh < physicalParentClip.y - cullMargin
          || tbx > physicalParentClip.x + physicalParentClip.w + cullMargin
          || tby > physicalParentClip.y + physicalParentClip.h + cullMargin) {
        if (!paintingGhost && !suppressMotion) keepSubtreeMotionAlive(frame);
        return;
      }
    }
    String dismiss = attr(frame, "dismiss-action", "");
    if (interactive && !dismiss.isEmpty()) {
      if (transformedBounds == null) transformedBounds = new Bounds(tbx, tby, tbw, tbh);
      Bounds popupBox = transformedBounds.intersect(physicalParentClip);
      if (popupBox.w > 0 && popupBox.h > 0) popups.add(new Popup(frame, popupBox, dismiss));
    }
    boolean control = isControl(frame);
    boolean surface = "panel".equals(tag) || "menu".equals(tag) || "box".equals(tag);
    boolean blockingSurface = surface && !"menu".equals(tag) && !insideControl(frame);
    boolean builtin = isHudTag(tag);
    if (!builtin && frame.children.isEmpty()) {
      if (tbw <= 0 || tbh <= 0 || tbx + tbw <= physicalParentClip.x
          || tby + tbh <= physicalParentClip.y
          || tbx >= physicalParentClip.x + physicalParentClip.w
          || tby >= physicalParentClip.y + physicalParentClip.h)
        return;
    }
    if (!isFocused && (hover > 0 || pressed > 0)) {
      Theme base = resolvedTheme(frame.node, settings.theme, "", frame.style, frame.context);
      if (hover > 0)
        theme = interpolate(
            base, resolvedTheme(frame.node, settings.theme, "hover", visual, frame.context), hover);
      if (pressed > 0)
        theme = interpolate(theme,
            resolvedTheme(frame.node, settings.theme, "active", visual, frame.context), pressed);
    }
    if (!structural) {
      int border = animation.color(
              "border", theme.border, time, duration, recipe.easing, reduced || !recipe.value),
          accent = animation.color(
              "accent", theme.accent, time, duration, recipe.easing, reduced || !recipe.value);
      if (border != theme.border || accent != theme.accent) {
        theme = theme.copy();
        theme.border = border;
        theme.accent = accent;
      }
      if (menuScope && !"menu".equals(tag) && visual.containsKey("background-opacity")) {
        theme = theme.copy();
        theme.backgroundOpacity = clamp(theme.backgroundOpacity * settings.menu.opacity
                * (settings.theme.backgroundOpacity / .94f),
            0, 1);
      }
      if (!"none".equals(attr(frame, "visual", "")) && !builtin
          && ((control && !pickerControl(tag) && !"player-preview".equals(tag))
              || "menu".equals(tag) || "box".equals(tag) || "panel".equals(tag)
              || visual.containsKey("background") || visual.containsKey("background-color"))) {
        int background = color(first(visual, "background", "background-color"),
            control ? Colors.mix(theme.raised, theme.accent, selected ? .16f : hover * .065f)
                    : theme.surface);
        background = animation.color(
            "background", background, time, duration, recipe.easing, reduced || !recipe.value);
        boolean casts = visual.containsKey("cast-effects")
            ? ("true".equals(visual.get("cast-effects"))
                  || "on".equals(visual.get("cast-effects")))
            : false;
        float defaultCorner = visual.containsKey("radius")
            ? number(visual, "radius", theme.radius)
            : number(visual, "border-radius", theme.radius);
        float tl = number(visual, "radius-top-left", defaultCorner);
        float tr = number(visual, "radius-top-right", defaultCorner);
        float bl = number(visual, "radius-bottom-left", defaultCorner);
        float br = number(visual, "radius-bottom-right", defaultCorner);
        float rad = (visual.containsKey("radius-top-left") || visual.containsKey("radius-top-right")
                        || visual.containsKey("radius-bottom-left")
                        || visual.containsKey("radius-bottom-right"))
            ? Math.max(Math.max(tl, tr), Math.max(bl, br))
            : defaultCorner;
        Canvas surfaceCanvas = corners(canvas, visual, rad);
        SurfacePainter.panel(surfaceCanvas, theme, frame.x, frame.y, frame.w, frame.h, background,
            rad, casts, false, false);
        paintTooltipArrow(canvas, frame, theme, background, rad);
        if (menuScope && "menu".equals(tag) && theme.effects != null && theme.effects.grain
            && theme.effects.grainAmount > 0) {
          surfaceCanvas.texture("nightclient:textures/ui/grain.png", frame.x, frame.y, frame.w,
              frame.h, rad, 128 * theme.effects.grainScale,
              Colors.alpha(theme.text, theme.effects.grainAmount * theme.backgroundOpacity));
        }
      }

      boolean selfClip = "input".equals(tag) || frame.flowSlotClip || !Double.isNaN(frame.retiredAt);
      if (selfClip) canvas.pushClip(frame.x, frame.y, frame.w, frame.h);
      try {
        boolean textual = "game-text".equals(tag) || "text".equals(tag) || "label".equals(tag)
            || "button".equals(tag) || "input".equals(tag);
        boolean tinted = textual || "sprite".equals(tag) || "viewmodel-outline".equals(tag)
            || "rect".equals(tag) || "circle".equals(tag) || "line".equals(tag);
        boolean needsValue = "input".equals(tag) || pickerControl(tag)
            || "color-swatch".equals(tag) || "progress".equals(tag);
        float font = textual ? number(visual, "font-size", 12) * number(visual, "font-scale", 1)
                * (menuScope ? settings.menu.fontScale : 1)
                             : 0,
              padding = textual ? (visual.containsKey("padding") ? padding(visual)[3] : 8) : 0,
              tracking = theme.fontTracking;
        int foreground = tinted
            ? animation.color("foreground", color(visual.get("color"), theme.text), time, duration,
                  recipe.easing, reduced || !recipe.value)
            : 0;
        boolean bold = textual
            && ("bold".equals(theme.fontWeight) || "bold".equals(visual.get("font-weight"))
                || "semibold".equals(visual.get("font-weight"))
                || "600".equals(visual.get("font-weight")));
        String family = theme.fontFamily;
        String label = textual ? bindings.text(frame.node.attributes.containsKey("text")
                                         ? frame.node.attributes.get("text")
                                         : frame.node.text,
                                     frame.context)
                               : "";
        String bind = needsValue ? frame.attributes.get("bind") : null;
        Object value = needsValue ? preview(bind, bindings.get(bind, frame.context)) : null;
        if ("game-text".equals(tag)) {
          float gameScale = number(visual, "size", 1);
          float tx = frame.x + padding;
          String align = visual.get("text-align");
          if ("center".equals(align))
            tx = frame.x
                + (frame.w - dev.nightclient.api.FormattedText.gameWidth(canvas, label, gameScale))
                    / 2;
          else if ("pixel-center".equals(align) && gameScale > 0) {
            float origin = number(visual, "pixel-origin-x", 0);
            tx =
                (float) Math.floor((frame.x + frame.w / 2 - origin) / gameScale
                    - Math.floor(dev.nightclient.api.FormattedText.gameWidth(canvas, label, 1)) / 2)
                    * gameScale
                + origin;
          } else if ("right".equals(align))
            tx = frame.x + frame.w
                - dev.nightclient.api.FormattedText.gameWidth(canvas, label, gameScale) - padding;
          boolean textClip = "hidden".equals(visual.get("overflow"))
              || "clip".equals(visual.get("text-overflow"));
          if (textClip) canvas.pushClip(frame.x, frame.y, frame.w, frame.h);
          try {
            dev.nightclient.api.FormattedText.drawGame(
                canvas, label, tx, frame.y, gameScale, foreground, number(visual, "shadow", 0) > 0);
          } finally {
            if (textClip) canvas.popClip();
          }
        } else if ("text".equals(tag) || "label".equals(tag) || "button".equals(tag)) {
          boolean formatted =
              "text".equals(tag) && "minecraft".equals(attr(frame, "format", ""));
          List<dev.nightclient.api.FormattedText.Run> runs = formatted
              ? dev.nightclient.api.FormattedText.runs(label, foreground)
              : null;
          float textWidth = formatted
              ? dev.nightclient.api.FormattedText.runsWidth(
                    canvas, family, runs, font, bold, tracking)
              : canvas.textWidthFamily(family, label, font, bold, tracking);
          boolean inButton = "button".equals(tag)
              || (frame.parent != null && "button".equals(frame.parent.node.tag));
          boolean shouldFit = inButton || "ellipsis".equals(visual.get("text-overflow"))
              || "hidden".equals(visual.get("overflow"))
              || (frame.parent != null && "hidden".equals(frame.parent.style.get("overflow")));
          float available = Math.max(0, frame.w - padding * 2);
          if (shouldFit && available > 0 && textWidth > available) {
            if (formatted) {
              runs = dev.nightclient.api.FormattedText.fit(
                  canvas, family, runs, available, font, bold, tracking);
              textWidth = dev.nightclient.api.FormattedText.runsWidth(
                  canvas, family, runs, font, bold, tracking);
            } else {
              label = fit(canvas, family, label, available, font, bold, tracking);
              textWidth = canvas.textWidthFamily(family, label, font, bold, tracking);
            }
          } else if (shouldFit && available <= 0 && frame.w > 0) {
            label = "";
            if (formatted) runs = java.util.Collections.emptyList();
            textWidth = 0;
          }
          String align = visual.get("text-align");
          float tx = frame.x
              + ("right".equals(align) ? Math.max(padding, frame.w - textWidth - padding)
                      : "center".equals(align) || "button".equals(tag) && align == null
                      ? Math.max(padding, (frame.w - textWidth) / 2)
                      : padding);
          if (formatted)
            dev.nightclient.api.FormattedText.drawRuns(canvas, family, runs, tx,
                frame.y + Math.max(0, (frame.h - font) / 2), font, bold, tracking);
          else if ("marquee".equals(visual.get("text-overflow")))
            paintMarquee(frame, animation, canvas, visual, family, label, frame.x + padding,
                frame.y + Math.max(0, (frame.h - font) / 2), Math.max(0, frame.w - padding * 2),
                font, foreground, bold, tracking);
          else if ("wrap".equals(visual.get("text-overflow"))) {
            float lineHeight = font * Math.max(1, number(visual, "line-height", 1.35f));
            List<String> lines = wrapText(
                canvas, family, label, Math.max(1, frame.w - padding * 2), font, bold, tracking);
            int visible =
                Math.min(lines.size(), Math.max(1, (int) Math.floor(frame.h / lineHeight + .01f)));
            float blockHeight = (visible - 1) * lineHeight + font;
            float startY = frame.y + Math.max(0f, (frame.h - blockHeight) * 0.5f);
            canvas.pushClip(frame.x, frame.y, frame.w, frame.h);
            try {
              for (int line = 0; line < visible; line++) {
                String part = lines.get(line);
                if (line == visible - 1 && visible < lines.size())
                  part = fit(canvas, family, part + "…", Math.max(1, frame.w - padding * 2), font,
                      bold, tracking);
                canvas.textFamily(family, part, frame.x + padding, startY + line * lineHeight,
                    font, foreground, bold, tracking);
              }
            } finally {
              canvas.popClip();
            }
          } else if ("numeric".equals(visual.get("text-motion"))) {
            String numericMode = settings.theme != null && settings.theme.motion != null
                ? settings.theme.motion.numericMotion
                : "off";
            paintNumericText(frame, animation, canvas, family, label, tx,
                frame.y + Math.max(0, (frame.h - font) / 2), font, foreground, bold, tracking,
                duration, recipe.easing, numericMode,
                reduced || !motionEnabled() || !recipe.value || "off".equals(numericMode));
          } else
            canvas.textFamily(family, label, tx, frame.y + Math.max(0, (frame.h - font) / 2), font,
                foreground, bold, tracking);
        } else if ("icon".equals(tag) || "image".equals(tag) || "texture".equals(tag)) {
          String name =
              bindings.text(attr(frame, "name", attr(frame, "src", "style")), frame.context);
          float size = Math.min(
              Math.min(frame.w, frame.h), number(visual, "size", Math.min(frame.w, frame.h)));
          int iconColor = animation.color("icon", color(visual.get("color"), theme.accent), time,
              duration, recipe.easing, reduced || !recipe.value);
          if (name.startsWith("nightclient:") || name.endsWith(".png")) {
            canvas.texture(name, frame.x, frame.y, frame.w, frame.h, number(visual, "radius", 0), 0,
                iconColor);
          } else if (Icons.ids().contains(name)) {
            Icons.draw(canvas, name, frame.x, frame.y, size, iconColor,
                number(visual, "stroke-width", 1.4f));
          }
        } else if ("sprite".equals(tag)) {
          float sourceWidth = numericAttr(frame, "source-width", frame.w),
                sourceHeight = numericAttr(frame, "source-height", frame.h);
          canvas.gameSprite(attr(frame, "src", ""), frame.x, frame.y, frame.w, frame.h,
              numericAttr(frame, "u", 0), numericAttr(frame, "v", 0),
              numericAttr(frame, "region-width", sourceWidth),
              numericAttr(frame, "region-height", sourceHeight), sourceWidth, sourceHeight,
              foreground);
        } else if ("entity-head".equals(tag)) {
          Object target = bindings.get(attr(frame, "source", "targetData"), frame.context);
          if (target instanceof dev.nightclient.api.TargetView) {
            float size = Math.max(1, Math.round(Math.min(frame.w, frame.h)));
            canvas.entityHead((dev.nightclient.api.TargetView) target, frame.x, frame.y, size, 1);
          }
        } else if ("player-preview".equals(tag)) {
          String source = bindings.text(attr(frame, "source", ""), frame.context);
          dev.nightclient.api.PlayerSkin skin = null;
          try {
            skin = source.isEmpty() ? actions.playerPreview() : actions.accountPreviewSkin(source);
          } catch (RuntimeException ignored) {
          }
          canvas.playerPreview(skin, frame.x, frame.y, frame.w, frame.h, previewYaw, previewPitch,
              previewZoom, (float) time, .55f + .15f * (float) Math.sin(time * .7),
              previewYawVelocity, alpha);
        } else if ("account-face".equals(tag)) {
          String source = bindings.text(attr(frame, "source", ""), frame.context);
          float size = Math.max(1f, Math.min(frame.w, frame.h));
          int[] face = actions.accountFace(source);
          dev.nightclient.renderer.player.PlayerFacePainter.paint(canvas, face,
              frame.x + (frame.w - size) * 0.5f, frame.y + (frame.h - size) * 0.5f, size, alpha);
        } else if ("cape-preview".equals(tag)) {
          String source = bindings.text(attr(frame, "source", ""), frame.context);
          if (!source.isEmpty()) {
            dev.nightclient.api.PlayerSkin capeSkin = null;
            String key = source;
            try {
              capeSkin = actions.capePreview(source);
              key = actions.capePreviewKey(source);
            } catch (RuntimeException ignored) {
            }
            canvas.capePreview(key, capeSkin, frame.x, frame.y, frame.w, frame.h, alpha);
          }
        } else if ("item".equals(tag)) {
          String source = attr(frame, "source", "item");
          Object slot = bindings.get(source, frame.context);
          float size = Math.min(
              Math.min(frame.w, frame.h), number(visual, "size", Math.min(frame.w, frame.h)));
          if (slot instanceof InventorySlot)
            canvas.inventoryItem((InventorySlot) slot, frame.x, frame.y, size, 1);
          else {
            String resource = bindings.text(attr(frame, "src", "{{item.itemId}}"), frame.context);
            if (!resource.equals(frame.lastItemResource)) {
              frame.lastItemResource = resource;
              frame.validItemResource = resource.length() <= 256
                  && RESOURCE_ID.matcher(resource).matches() && !"minecraft:air".equals(resource);
            }
            if (frame.validItemResource) canvas.item(resource, frame.x, frame.y, size, 1);
          }
        } else if ("input".equals(tag)) {
          paintInput(
              frame, canvas, visual, theme, value, label, font, foreground, tracking, family);
        } else if (pickerControl(tag) || "color-swatch".equals(tag))
          paintColorControl(frame, canvas, theme, value);
        else if ("pixel-grid".equals(tag))
          paintPixelGrid(frame, canvas, theme);
        else if ("viewmodel-gizmo".equals(tag)) {
          Object source = bindings.get(attr(frame, "source", "viewmodelGizmo"), frame.context);
          if (source instanceof Map) {
            Map<?, ?> gizmo = (Map<?, ?>) source;
            Object projection = gizmo.get("projection"), radius = gizmo.get("radiusPixels");
            if (Boolean.TRUE.equals(gizmo.get("visible"))
                && projection instanceof dev.nightclient.api.ViewmodelProjection) {
              if (viewmodelGizmoRenderer == null)
                viewmodelGizmoRenderer = new dev.nightclient.renderer.ViewmodelGizmoRenderer();
              dev.nightclient.api.ViewmodelProjection p =
                  (dev.nightclient.api.ViewmodelProjection) projection;
              viewmodelGizmoRenderer.render(canvas, p, String.valueOf(gizmo.get("mode")),
                  String.valueOf(gizmo.get("axis")), String.valueOf(gizmo.get("hoverAxis")),
                  radius instanceof Number ? ((Number) radius).floatValue() : 54,
                  numericAttr(frame, "thickness", 3.6f),
                  color(attr(frame, "axis-x-color", "#FF3352"), 0xffff3352),
                  color(attr(frame, "axis-y-color", "#8BDC00"), 0xff8bdc00),
                  color(attr(frame, "axis-z-color", "#2890FF"), 0xff2890ff), frame.x, frame.y,
                  frame.w / p.width, frame.h / p.height, 1);
            }
          }
        } else if ("viewmodel-outline".equals(tag))
          canvas.viewmodelOutline(attr(frame, "name", "hover"), foreground);
        else if ("rect".equals(tag)) {
          float defaultCorner = visual.containsKey("radius") ? number(visual, "radius", 0)
                                                             : number(visual, "border-radius", 0);
          float tl = number(visual, "radius-top-left", defaultCorner);
          float tr = number(visual, "radius-top-right", defaultCorner);
          float bl = number(visual, "radius-bottom-left", defaultCorner);
          float br = number(visual, "radius-bottom-right", defaultCorner);
          float rad =
              (visual.containsKey("radius-top-left") || visual.containsKey("radius-top-right")
                  || visual.containsKey("radius-bottom-left")
                  || visual.containsKey("radius-bottom-right"))
              ? Math.max(Math.max(tl, tr), Math.max(bl, br))
              : defaultCorner;
          Canvas shape = corners(canvas, visual, rad);
          int fill = animation.color("fill", color(visual.get("fill"), foreground), time, duration,
              recipe.easing, reduced || !recipe.value);
          if (visual.containsKey("fill-top-left") || visual.containsKey("fill-top-right")
              || visual.containsKey("fill-bottom-left")
              || visual.containsKey("fill-bottom-right")) {
            canvas.gradientRoundedRectCorners(frame.x, frame.y, frame.w, frame.h,
                number(visual, "radius-top-left", rad), number(visual, "radius-top-right", rad),
                number(visual, "radius-bottom-left", rad),
                number(visual, "radius-bottom-right", rad),
                color(visual.get("fill-top-left"), fill), color(visual.get("fill-top-right"), fill),
                color(visual.get("fill-bottom-left"), fill),
                color(visual.get("fill-bottom-right"), fill));
          } else
            shape.roundedRect(frame.x, frame.y, frame.w, frame.h, rad, fill);
          if (visual.containsKey("stroke"))
            shape.strokeRoundedRect(frame.x, frame.y, frame.w, frame.h, rad,
                number(visual, "stroke-width", 1), color(visual.get("stroke"), foreground));
        } else if ("circle".equals(tag))
          canvas.circle(frame.x + frame.w / 2, frame.y + frame.h / 2,
              Math.min(frame.w, frame.h) / 2,
              animation.color("fill", color(visual.get("fill"), foreground), time, duration,
                  recipe.easing, reduced || !recipe.value));
        else if ("line".equals(tag))
          canvas.line(frame.x + number(visual, "line-start-x", 0),
              frame.y + number(visual, "line-start-y", 0),
              frame.x + number(visual, "line-end-x", frame.w),
              frame.y + number(visual, "line-end-y", frame.h),
              number(visual, "stroke-width", number(visual, "line-width", 1)),
              color(visual.get("stroke"), foreground));
        else if ("progress".equals(tag)) {
          float fraction = value instanceof Number ? ((Number) value).floatValue()
                                                   : numericAttr(frame, "value", 0);
          fraction = animation.value.to(
              clamp(fraction, 0, 1), time, duration, recipe.easing, reduced || !recipe.value);
          canvas.roundedRect(frame.x, frame.y, frame.w, frame.h, frame.h / 2, theme.border);
          canvas.roundedRect(frame.x, frame.y, frame.w * clamp(fraction, 0, 1), frame.h,
              frame.h / 2, theme.accent);
        } else if (builtin)
          paintBuiltin(frame, canvas, theme);
        if (interactive
            && (control || blockingSurface
                || frame.scrollMax > 0
                    && ("scroll".equals(visual.get("overflow"))
                        || "auto".equals(visual.get("overflow"))))) {
          if (transformedBounds == null) transformedBounds = new Bounds(tbx, tby, tbw, tbh);
          Hit hit = new Hit(frame, transformedBounds.intersect(physicalParentClip), transform,
              false, blockingSurface && !control);
          if (hit.clip.w > 0 && hit.clip.h > 0) hits.add(hit);
          if (focused != null && focused.key.equals(frame.key)) focused = hit;
          if (dragged != null && dragged.key.equals(frame.key)) dragged = hit;
        }
        if (interactive && physicalParentClip.contains(mouseX, mouseY)
            && mouseX >= tbx && mouseY >= tby && mouseX < tbx + tbw && mouseY < tby + tbh) {
          if (control || surface) {
            tooltip = tooltipKey = "";
            tooltipAnchor = null;
          }
          if (!attr(frame, "tooltip", "").isEmpty()) {
            tooltip = attr(frame, "tooltip", "");
            tooltipKey = frame.key + ":" + tooltip;
            tooltipAnchor = new Bounds(tbx, tby, tbw, tbh);
          }
        }
      } finally {
        if (selfClip) canvas.popClip();
      }
    }
    String overflow = visual.get("overflow");
    boolean isScrollable = frame.scrollMax > 0 || scrollOffset(frame) > 0
        || "scroll".equals(overflow) || "auto".equals(overflow);
    boolean clipsChildren = leafControl || isScrollable || frame.layoutTransition
        || frame.flowSlotClip || !Double.isNaN(frame.retiredAt)
        || "hidden".equals(overflow) || "true".equals(visual.get("clip"));
    float[] p = isScrollable ? padding(visual) : null;
    float clipX = isScrollable ? frame.x + p[3] : frame.x;
    float clipY = isScrollable ? frame.y + p[0] : frame.y;
    float clipW = isScrollable ? Math.max(0, frame.w - p[1] - p[3]) : frame.w;
    float clipH = isScrollable ? Math.max(0, frame.h - p[0] - p[2]) : frame.h;

    Bounds childPhysicalClip = physicalParentClip;
    if (clipsChildren) {
      Bounds contentBounds;
      if (isScrollable) {
        contentBounds = transform == Transform.IDENTITY
            ? new Bounds(clipX, clipY, clipW, clipH)
            : transform.bounds(new Bounds(clipX, clipY, clipW, clipH));
      } else {
        if (transformedBounds == null) transformedBounds = new Bounds(tbx, tby, tbw, tbh);
        contentBounds = transformedBounds;
      }
      childPhysicalClip = contentBounds.intersect(physicalParentClip);
    }
    if (!paintingGhost && !suppressMotion) animation.lastChildClip = childPhysicalClip;
    if (clipsChildren) canvas.pushClip(clipX, clipY, clipW, clipH);
    try {
      for (int i = 0, size = frame.children.size(); i < size; i++) {
        Frame child = frame.children.get(i);
        if (!"overlay".equals(child.style.get("position")))
          paint(child, canvas, parentClip, interactive, transform, childPhysicalClip);
      }
    } finally {
      if (clipsChildren) canvas.popClip();
    }
    float barWidth = clamp(number(visual, "scrollbar-width", 3), 0, 12);
    if (frame.scrollMax > 0 && barWidth > 0
        && ("scroll".equals(overflow) || "auto".equals(overflow))) {
      float[] bar = scrollbar(frame);
      float current = scrollOffset(frame);
      float bx = frame.x + frame.w - barWidth - 1,
            by = bar[0] + (bar[1] - bar[2]) * current / frame.scrollMax;
      canvas.roundedRect(
          bx, bar[0], barWidth, bar[1], barWidth / 2, Colors.alpha(theme.text, .045f));
      canvas.roundedRect(bx, by, barWidth, bar[2], barWidth / 2,
          Colors.alpha(theme.muted,
              isHovered || draggingScroll && dragged != null && dragged.key.equals(frame.key)
                  ? .7f
                  : .38f));
      if (interactive) {
        Hit barHit = new Hit(frame,
            transform.bounds(new Bounds(bx - 4, bar[0], barWidth + 5, bar[1]))
                .intersect(physicalParentClip),
            transform, true);
        hits.add(barHit);
        if (draggingScroll && dragged != null && dragged.key.equals(frame.key)) dragged = barHit;
      }
    }
  }

  private boolean containsOverlayOrActiveHit(Frame frame) {
    if ("overlay".equals(frame.style.get("position")) || !attr(frame, "dismiss-action", "").isEmpty())
      return true;
    if (focused != null && focused.key.equals(frame.key)) return true;
    if (dragged != null && dragged.key.equals(frame.key)) return true;
    for (int i = 0, size = frame.children.size(); i < size; i++)
      if (containsOverlayOrActiveHit(frame.children.get(i))) return true;
    return false;
  }

  private void keepSubtreeMotionAlive(Frame frame) {
    Motion state = motion.get(frame.key);
    if (state != null) {
      state.lastSeen = time;
      state.lastFrame = frame;
      state.retiredAt = Double.NaN;
    }
    for (int i = 0, size = frame.children.size(); i < size; i++)
      keepSubtreeMotionAlive(frame.children.get(i));
  }

  private static float[] scrollbar(Frame frame) {
    float[] inset = padding(frame.style);
    float height = Math.max(0, frame.h - inset[0] - inset[2]);
    return new float[] {frame.y + inset[0], height,
        Math.min(height, Math.max(18, height * height / Math.max(1, height + frame.scrollMax)))};
  }

  private void paintMarquee(Frame frame, Motion animation, Canvas canvas,
      Map<String, String> visual, String family, String label, float x, float y, float width,
      float font, int color, boolean bold, float tracking) {
    float total = canvas.textWidthFamily(family, label, font, bold, tracking);
    float clipPadTop = font * .2f, clipHeight = Math.max(font, frame.h) + font * .45f;
    if (total <= width || instantMotion()) {
      canvas.pushClip(x, y - clipPadTop, width, clipHeight);
      try {
        canvas.textFamily(family, label, x, y, font, color, bold, tracking);
      } finally {
        canvas.popClip();
      }
      return;
    }
    hudFrameDynamic = true;
    String signature = family + '\n' + label + '\n' + font + '\n' + bold + '\n' + tracking;
    if (!signature.equals(animation.marqueeSignature)) {
      animation.marqueeSignature = signature;
      animation.marqueeSince = time;
      animation.marqueeGlyphs = new ArrayList<MarqueeGlyph>();
      float at = 0;
      for (int i = 0; i < label.length() && animation.marqueeGlyphs.size() < 512;) {
        int cp = label.codePointAt(i), end = i + Character.charCount(cp);
        while (end < label.length()) {
          int nextCp = label.codePointAt(end);
          if (dev.nightclient.renderer.FontAtlas.isTrailingClusterCodePoint(nextCp)) {
            end += Character.charCount(nextCp);
          } else if (nextCp == 0x200D) {
            end += 1;
            if (end < label.length()) end += Character.charCount(label.codePointAt(end));
          } else {
            break;
          }
        }
        String glyph = label.substring(i, end);
        float next = canvas.textWidthFamily(family, label.substring(0, end), font, bold, tracking);
        animation.marqueeGlyphs.add(new MarqueeGlyph(glyph, at, Math.max(0, next - at)));
        at = next + tracking;
        i = end;
      }
    }
    float speed = Math.max(0, number(visual, "scroll-speed", 12)),
          pause = Math.max(0, duration(visual.get("scroll-pause"), 3)),
          gap = Math.max(font, number(visual, "gap", font)), distance = total + gap;
    double cycle =
        speed <= 0 ? 0 : (Math.max(0, time - animation.marqueeSince) % (pause + distance / speed));
    float offset = speed <= 0 ? 0 : Math.max(0, (float) cycle - pause) * speed,
          fade = Math.max(0, Math.min(width / 2, number(visual, "fade-width", 5)));
    canvas.pushClip(x, y - clipPadTop, width, clipHeight);
    try {
      for (int copy = 0; copy < 2; copy++)
        for (MarqueeGlyph glyph : animation.marqueeGlyphs) {
          float gx = x + glyph.x - offset + copy * distance;
          if (gx + glyph.width < x || gx > x + width) continue;
          float center = gx + glyph.width / 2,
                alpha = fade <= 0
              ? 1
              : clamp(Math.min((center - x) / fade, (x + width - center) / fade), 0, 1);
          canvas.textFamily(
              family, glyph.text, gx, y, font, Colors.alpha(color, alpha), bold, tracking);
        }
    } finally {
      canvas.popClip();
    }
  }

  private static Canvas corners(Canvas canvas, Map<String, String> visual, float radius) {
    if (!visual.containsKey("radius-top-left") && !visual.containsKey("radius-top-right")
        && !visual.containsKey("radius-bottom-left") && !visual.containsKey("radius-bottom-right"))
      return canvas;
    float defaultCorner = visual.containsKey("radius") ? number(visual, "radius", 0)
                                                       : number(visual, "border-radius", 0);
    float tl = number(visual, "radius-top-left", defaultCorner);
    float tr = number(visual, "radius-top-right", defaultCorner);
    float bl = number(visual, "radius-bottom-left", defaultCorner);
    float br = number(visual, "radius-bottom-right", defaultCorner);
    float maxCorner = Math.max(Math.max(tl, tr), Math.max(bl, br));
    float base = (radius > 0) ? radius : maxCorner;
    return new CornerCanvas(canvas, base, tl, tr, bl, br);
  }

  private void paintInput(Frame frame, Canvas canvas, Map<String, String> visual, Theme theme,
      Object value, String label, float font, int foreground, float tracking, String family) {
    float[] inset = visual.containsKey("padding") ? padding(visual) : new float[] {0, 8, 0, 8};
    float border = Math.max(1, theme.borderWidth), left = Math.max(border, inset[3]),
          right = Math.max(border, inset[1]), top = Math.max(border, inset[0]),
          bottom = Math.max(border, inset[2]);
    Bounds inner = new Bounds(frame.x + Math.min(left, frame.w), frame.y + Math.min(top, frame.h),
        Math.max(0, frame.w - left - right), Math.max(0, frame.h - top - bottom));
    if (inner.w <= 0 || inner.h <= 0) return;
    boolean active = focused != null && focused.key.equals(frame.key);
    String text = active ? inputBuffer : inputDisplayText(frame, value);
    boolean placeholder = text.isEmpty();
    float inkInset = Math.max(1, font / 12), available = Math.max(0, inner.w - inkInset * 2),
          caretWidth = Math.min(1, available), ty = inner.y + (inner.h - font) / 2;
    float tx = inner.x + inkInset;
    if (active) {
      caret = clamp(caret, 0, text.length());
      selection = clamp(selection, 0, text.length());
      float caretPosition =
                canvas.textWidthFamily(family, text.substring(0, caret), font, false, tracking),
            textWidth = canvas.textWidthFamily(family, text, font, false, tracking);
      inputScroll = clamp(inputScroll, 0, Math.max(0, textWidth - available + caretWidth));
      if (caretPosition < inputScroll)
        inputScroll = caretPosition;
      else if (caretPosition > inputScroll + available - caretWidth)
        inputScroll = Math.max(0, caretPosition - available + caretWidth);
      tx -= inputScroll;
    }
    canvas.pushClip(inner.x, inner.y, inner.w, inner.h);
    try {
      if (active && caret != selection) {
        float start = tx
            + canvas.textWidthFamily(
                family, text.substring(0, Math.min(caret, selection)), font, false, tracking),
              end = tx
            + canvas.textWidthFamily(
                family, text.substring(0, Math.max(caret, selection)), font, false, tracking);
        Bounds selected = new Bounds(start, ty, Math.max(0, end - start), font).intersect(inner);
        if (selected.w > 0 && selected.h > 0)
          canvas.rect(
              selected.x, selected.y, selected.w, selected.h, Colors.alpha(theme.accent, .3f));
      }
      String shown = placeholder
          ? fit(canvas, family, attr(frame, "placeholder", label), available, font, false, tracking)
          : active ? text
                   : fit(canvas, family, text, available, font, false, tracking);
      canvas.textFamily(
          family, shown, tx, ty, font, placeholder ? theme.muted : foreground, false, tracking);
      if (active && caretWidth > 0 && (Math.floor(time * 2) % 2) == 0) {
        float cx = clamp(
            tx + canvas.textWidthFamily(family, text.substring(0, caret), font, false, tracking),
            inner.x, inner.x + inner.w - caretWidth);
        Bounds cursor = new Bounds(cx, ty, caretWidth, font).intersect(inner);
        if (cursor.w > 0 && cursor.h > 0)
          canvas.rect(cursor.x, cursor.y, cursor.w, cursor.h, theme.accent);
      }
    } finally {
      canvas.popClip();
    }
  }

  private boolean selected(Frame frame) {
    String action = attr(frame, "action", "");
    if (action.startsWith("page:")) return page.equals(action.substring(5));
    if (action.startsWith("module:"))
      return ("hud".equals(page) ? selectedHud : selectedModule).equals(action.substring(7));
    if (action.startsWith("editor-target:"))
      return action.substring(14).equals(String.valueOf(frame.context.get("editorTarget")));
    if (action.startsWith("viewmodel-hand:"))
      return action.substring(15).equals(String.valueOf(frame.context.get("viewmodelActiveHand")));
    if (action.startsWith("viewmodel-mode:"))
      return action.substring(15).equals(String.valueOf(frame.context.get("viewmodelMode")));
    if (action.startsWith("viewmodel-axis:"))
      return action.substring(15).equals(String.valueOf(frame.context.get("viewmodelAxis")));
    return false;
  }

  private static boolean pickerControl(String tag) {
    return "color-plane".equals(tag) || "hue-strip".equals(tag) || "alpha-strip".equals(tag);
  }

  private ColorPickerModel colorModel(Frame frame) {
    String bind = attr(frame, "bind", "");
    ColorPickerModel model = colorModels.get(bind);
    if (model == null) {
      model = new ColorPickerModel();
      colorModels.put(bind, model);
    }
    Object value = bindings.get(bind, frame.context);
    model.sync(value instanceof Integer ? (Integer) value : settings.theme.accent);
    return model;
  }

  private void commitColor(Frame frame, ColorPickerModel model) {
    String bind = attr(frame, "bind", "");
    if (bindings.set(bind, Integer.toString(model.argb()), false, frame.context))
      changedBinding(bind, true);
  }

  private void updateColor(Hit hit, float screenX, float screenY) {
    Frame frame = hit.frame;
    ColorPickerModel model = colorModel(frame);
    float x = clamp((hit.transform.inverseX(screenX) - frame.x) / Math.max(1, frame.w), 0, 1),
          y = clamp((hit.transform.inverseY(screenY) - frame.y) / Math.max(1, frame.h), 0, 1);
    if ("color-plane".equals(frame.node.tag)) {
      model.saturation = x;
      model.value = 1 - y;
    } else if ("hue-strip".equals(frame.node.tag))
      model.hue = x;
    else
      model.alpha = x;
    commitColor(frame, model);
  }

  private void paintColorControl(Frame frame, Canvas canvas, Theme theme, Object value) {
    ColorPickerModel model = colorModel(frame);
    String tag = frame.node.tag;
    float radius =
        clamp(number(frame.style, "radius", theme.radius), 0, Math.min(frame.w, frame.h) / 2);
    if ("color-plane".equals(tag)) {
      canvas.gradientRoundedRect(frame.x, frame.y, frame.w, frame.h, radius, 0xffffffff,
          ColorPickerModel.rgb(model.hue, 1, 1, 1));
      canvas.gradientRoundedRectCorners(frame.x, frame.y, frame.w, frame.h, radius, radius, radius,
          radius, 0x00000000, 0x00000000, 0xff000000, 0xff000000);
      float x = clamp(frame.x + model.saturation * frame.w, frame.x + 5,
                frame.x + Math.max(5, frame.w - 5)),
            y = clamp(frame.y + (1 - model.value) * frame.h, frame.y + 5,
                frame.y + Math.max(5, frame.h - 5));
      canvas.circle(x, y, 5, 0xcc000000);
      canvas.circle(x, y, 4, 0xffffffff);
      canvas.circle(x, y, 2.5f, ColorPickerModel.rgb(model.hue, model.saturation, model.value, 1));
    } else if ("hue-strip".equals(tag)) {
      int columns = Math.max(12, Math.min(192, (int) Math.ceil(frame.w)));
      float step = frame.w / columns;
      for (int column = 0; column < columns; column++)
        canvas.rect(frame.x + column * step, frame.y, step, frame.h,
            ColorPickerModel.rgb((column + .5f) / columns, 1, 1, 1));
      stripHandle(canvas, clamp(frame.x + model.hue * frame.w, frame.x + 4, frame.x + frame.w - 4),
          frame.y, frame.h, ColorPickerModel.rgb(model.hue, 1, 1, 1));
    } else if ("alpha-strip".equals(tag)) {
      checkerboard(canvas, frame.x, frame.y, frame.w, frame.h, 6);
      int columns = Math.max(1, Math.min(128, (int) Math.ceil(frame.w)));
      float step = frame.w / columns;
      for (int column = 0; column < columns; column++)
        canvas.rect(frame.x + column * step, frame.y, step, frame.h,
            ColorPickerModel.rgb(
                model.hue, model.saturation, model.value, (column + .5f) / columns));
      stripHandle(canvas,
          clamp(frame.x + model.alpha * frame.w, frame.x + 4, frame.x + frame.w - 4), frame.y,
          frame.h, ColorPickerModel.rgb(model.hue, model.saturation, model.value, 1));
    } else {
      int tint = value instanceof Integer ? (Integer) value : theme.accent;
      if ((tint >>> 24) < 255)
        roundedCheckerboard(canvas, frame.x, frame.y, frame.w, frame.h, radius);
      canvas.roundedRect(frame.x, frame.y, frame.w, frame.h, radius, tint);
    }
  }

  private static void roundedCheckerboard(
      Canvas canvas, float x, float y, float w, float h, float radius) {
    float cell = Math.max(1, Math.min(3, Math.min(w, h) / 2));
    int cols = Math.max(1, (int) Math.ceil(w / cell)),
        rows = Math.max(1, (int) Math.ceil(h / cell));
    canvas.roundedRect(x, y, w, h, radius, 0xff485160);
    for (int row = 0; row < rows; row++)
      for (int col = 0; col < cols; col++)
        if ((row + col) % 2 != 0) {
          float cw = Math.min(cell, w - col * cell), ch = Math.min(cell, h - row * cell);
          canvas.roundedRectCorners(x + col * cell, y + row * cell, cw, ch,
              row == 0 && col == 0 ? radius : 0, row == 0 && col == cols - 1 ? radius : 0,
              row == rows - 1 && col == 0 ? radius : 0,
              row == rows - 1 && col == cols - 1 ? radius : 0, 0xff28313e);
        }
  }

  private static void stripHandle(Canvas canvas, float x, float y, float height, int fillColor) {
    float cy = y + height * .5f;
    float r = Math.max(4f, height * .5f + 1f);
    canvas.circle(x, cy, r + 1f, 0xcc000000);
    canvas.circle(x, cy, r, 0xffffffff);
    canvas.circle(x, cy, Math.max(2f, r - 1.5f), fillColor);
  }

  private static void checkerboard(
      Canvas canvas, float x, float y, float width, float height, float desiredCell) {
    float cell = Math.max(desiredCell, Math.max(width, height) / 32);
    int cols = Math.max(1, (int) Math.ceil(width / cell)),
        rows = Math.max(1, (int) Math.ceil(height / cell));
    for (int row = 0; row < rows; row++)
      for (int col = 0; col < cols; col++)
        canvas.rect(x + col * cell, y + row * cell, Math.min(cell, width - col * cell),
            Math.min(cell, height - row * cell), (row + col) % 2 == 0 ? 0xff485160 : 0xff28313e);
  }

  private static boolean isHudTag(String tag) {
    return "cooldowns".equals(tag) || "inventory".equals(tag) || "tnt".equals(tag)
        || "crosshair".equals(tag) || "armor".equals(tag) || "potions".equals(tag)
        || "target".equals(tag) || "scoreboard".equals(tag) || "keystrokes".equals(tag)
        || "coordinates".equals(tag) || "ping".equals(tag) || "fps".equals(tag)
        || "betterChat".equals(tag) || "minimap".equals(tag);
  }

  private void paintBuiltin(Frame frame, Canvas canvas, Theme theme) {
    if ("crosshair".equals(frame.node.tag)) {
      if ("target-preview".equals(attr(frame, "source", ""))) {
        paintCrosshairTargetPreview(frame, canvas, theme);
      } else if (cameraCrosshairVisible && (hudEditing || settings.crosshair.enabled)) {
        boolean hit = !hudEditing && actions.crosshairTargetHit();
        FeatureHudRenderer.renderCrosshair(
            canvas, settings, viewportWidth, viewportHeight, hit, crosshairTargetProgress);
      }
    } else if ("minimap".equals(frame.node.tag) && (hudEditing || settings.minimap.enabled)) {
      float scale = Math.max(0.01f, frame.w / Math.max(72f, settings.minimap.size));
      actions.renderMinimap(canvas, frame.x, frame.y, frame.w, frame.h, scale, hudEditing);
    }
  }

  private void paintCrosshairTargetPreview(Frame frame, Canvas canvas, Theme theme) {
    float r = 6f;
    canvas.roundedRect(frame.x, frame.y, frame.w, frame.h, r,
        Colors.mix(theme.background, 0xff0b0d12, 0.55f));
    canvas.pushClip(frame.x + 1f, frame.y + 1f, Math.max(0f, frame.w - 2f),
        Math.max(0f, frame.h - 2f));
    float cx = frame.x + frame.w / 2f;
    float cy = frame.y + frame.h / 2f;
    int guide = Colors.alpha(theme.text, 0.09f);
    canvas.line(frame.x + 6f, cy, frame.x + frame.w - 6f, cy, 0.7f, guide);
    canvas.line(cx, frame.y + 6f, cx, frame.y + frame.h - 6f, 0.7f, guide);
    FeatureHudRenderer.renderCrosshairTargetPreview(
        canvas, settings, cx, cy, Math.min(frame.w, frame.h));
    canvas.popClip();
    canvas.strokeRoundedRect(frame.x, frame.y, frame.w, frame.h, r, 0.7f,
        Colors.alpha(theme.text, 0.14f));
  }

  public boolean mouseClicked(double x, double y, int button) {
    if (button < 0 || button > 2) return false;
    menuFrameCache.clear();
    mouseX = (float) x;
    mouseY = (float) y;
    draggingScroll = false;
    if (!popups.isEmpty() && !popups.get(popups.size() - 1).bounds.contains((float) x, (float) y)) {
      dismissPopup();
      return true;
    }
    for (int index = hits.size() - 1; index >= 0; index--) {
      Hit hit = hits.get(index);
      if (!hit.clip.contains((float) x, (float) y)) continue;
      if (hit.scrollbar && button == 0) {
        float[] bar = scrollbar(hit.frame);
        float current = scroll.containsKey(hit.key) ? scroll.get(hit.key) : 0,
              top = bar[0] + (bar[1] - bar[2]) * current / hit.frame.scrollMax,
              local = hit.transform.inverseY((float) y);
        scrollGrab = local >= top && local <= top + bar[2] ? local - top : bar[2] / 2;
        draggingScroll = true;
        dragged = hit;
        focused = null;
        updateScroll(hit, (float) y);
        return true;
      }
      String tag = hit.node.tag;
      if ("pixel-grid".equals(tag)) {
        actions.checkpoint();
        pixelDrawing = true;
        pixelErase = button == 1;
        pixelHit = hit;
        pixelLastX = pixelLastY = -1;
        paintPixelStroke(hit, (float) x, (float) y);
        return true;
      }
      if (button > 0) {
        String secondary = attr(hit.frame, button == 1 ? "secondary-action" : "middle-action", "");
        if (!secondary.isEmpty()) {
          act(secondary, hit.frame.context);
          return true;
        }
        if (hit.blocker) return true;
        continue;
      }
      pressedKey = hit.key;
      if (hit.blocker) {
        focused = null;
        return true;
      }
      if ("input".equals(tag)) {
        if (!bindings.writable(hit.bind, hit.frame.context)) return true;
        focusInput(hit);
        checkpointBinding(hit.bind);
        return true;
      }
      if ("player-preview".equals(tag)) {
        previewDragging = true;
        previewYawVelocity = 0;
        previewLastX = (float) x;
        previewLastY = (float) y;
        pressedKey = hit.key;
        return true;
      }
      focused = null;
      if (pickerControl(tag)) {
        if (!bindings.writable(hit.bind, hit.frame.context)) return true;
        checkpointBinding(hit.bind);
        dragged = hit;
        focused = hit;
        updateColor(hit, (float) x, (float) y);
        return true;
      }
      if ("slider".equals(tag)) {
        if (!bindings.writable(hit.bind, hit.frame.context)) return true;
        checkpointBinding(hit.bind);
        dragged = hit;
        focused = hit;
        updateSlider(hit, (float) x);
        return true;
      }
      if ("toggle".equals(tag) || "switch".equals(tag)) {
        checkpointBinding(hit.bind);
        boolean value = Boolean.TRUE.equals(bindings.get(hit.bind, hit.frame.context));
        if (bindings.set(hit.bind, Boolean.toString(!value), false, hit.frame.context))
          changedBinding(hit.bind);
        return true;
      }
      if ("choice".equals(tag)) {
        String[] options = attr(hit.frame, "options", "").split("\\|");
        Object current = bindings.get(hit.bind, hit.frame.context);
        int selected = 0;
        for (int i = 0; i < options.length; i++)
          if (options[i].equals(String.valueOf(current))) selected = i;
        checkpointBinding(hit.bind);
        if (options.length > 0
            && bindings.set(
                hit.bind, options[(selected + 1) % options.length], false, hit.frame.context))
          changedBinding(hit.bind);
        return true;
      }
      if ("color".equals(tag)) {
        colorBind = hit.bind;
        colorLabel = attr(hit.frame, "text", bindings.text(hit.node.text, hit.frame.context));
        colorAnchor = new Bounds(mouseX, mouseY, 0, 0);
        focused = null;
        return true;
      }
      if (hasAction(hit.frame)) {
        act(bindings.text(attr(hit.frame, "action", ""), hit.frame.context), hit.frame.context);
        return true;
      }
    }
    if (!colorBind.isEmpty()) {
      colorBind = "";
      colorAnchor = null;
      focused = null;
      dragged = null;
      return true;
    }
    focused = null;
    return false;
  }

  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    menuFrameCache.clear();
    if (previewDragging && button == 0) {
      previewYaw += (float) dx * .55f;
      previewPitchTarget = clamp(previewPitchTarget + (float) dy * .32f, -28f, 34f);
      previewPitch = previewPitchTarget;
      previewYawVelocity = (float) dx * 18f;
      previewLastX = (float) x;
      previewLastY = (float) y;
      return true;
    }
    if (pixelDrawing && pixelHit != null) {
      paintPixelStroke(pixelHit, (float) x, (float) y);
      return true;
    }
    if (button != 0 || dragged == null) return false;
    if (draggingScroll)
      updateScroll(dragged, (float) y);
    else if (pickerControl(dragged.node.tag))
      updateColor(dragged, (float) x, (float) y);
    else
      updateSlider(dragged, (float) x);
    return true;
  }

  public boolean mouseReleased(double x, double y, int button) {
    menuFrameCache.clear();
    boolean handled = dragged != null || pixelDrawing || previewDragging || !pressedKey.isEmpty();
    previewDragging = false;
    if (button == 0 && !pendingBind.isEmpty() && dragged != null) {
      String bind = pendingBind, value = pendingValue;
      pendingBind = pendingValue = "";
      if (bindings.set(bind, value, false, dragged.frame.context)) changedBinding(bind);
    }
    if (button == 0 && liveBindingDirty) {
      liveBindingDirty = false;
      actions.changed();
    }
    pressedKey = "";
    dragged = null;
    draggingScroll = false;
    pixelDrawing = false;
    pixelHit = null;
    return handled;
  }

  private void updateScroll(Hit hit, float y) {
    float[] bar = scrollbar(hit.frame);
    scroll.put(hit.key,
        clamp((hit.transform.inverseY(y) - bar[0] - scrollGrab) / Math.max(1, bar[1] - bar[2])
                * hit.frame.scrollMax,
            0, hit.frame.scrollMax));
  }

  public boolean mouseScrolled(double x, double y, double amount) {
    if (amount == 0) return false;
    menuFrameCache.clear();
    for (int index = hits.size() - 1; index >= 0; index--) {
      Hit hit = hits.get(index);
      if (!hit.clip.contains((float) x, (float) y)) continue;
      if ("player-preview".equals(hit.node.tag)) {
        previewZoom = clamp(previewZoom + (float) amount * .09f, .72f, 1.65f);
        return true;
      }
      String wheel = wheelAction(hit.frame, amount < 0 ? "wheel-next" : "wheel-previous");
      if (!wheel.isEmpty()) {
        act(liveWheel(wheel, amount), hit.frame.context);
        return true;
      }
      if (hit.frame.scrollMax > 0) {
        pendingSearchScroll = false;
        float current = scroll.containsKey(hit.key) ? scroll.get(hit.key) : 0;
        scroll.put(hit.key,
            clamp(current - (float) amount * number(hit.frame.style, "scroll-step", 24), 0,
                hit.frame.scrollMax));
        return true;
      }
    }
    return false;
  }

  private String liveWheel(String wheel, double amount) {
    if (!wheel.startsWith("page:")) return wheel;
    List<String> pages = hwEventsTabVisible()
        ? Arrays.asList(
              "all", "features", "camera", "world", "player", "waypoints", "events", "hud",
              "settings")
        : WHEEL_PAGES;
    int index = pages.indexOf(page);
    if (index < 0) return wheel;
    int next = Math.floorMod(index + (amount < 0 ? 1 : -1), pages.size());
    return "page:" + pages.get(next);
  }

  private String wheelAction(Frame frame, String key) {
    for (Frame current = frame; current != null; current = current.parent) {
      String value = current.attributes.get(key);
      if (value != null && !value.isEmpty()) return bindings.text(value, current.context);
    }
    return "";
  }

  private void updateSlider(Hit hit, float x) {
    float min = numericAttr(hit.frame, "min", 0), max = numericAttr(hit.frame, "max", 1),
          padding = number(hit.frame.style, "padding", 8);
    float fraction = clamp((hit.transform.inverseX(x) - hit.frame.x - padding)
                  / Math.max(1, hit.frame.w - 2 * padding),
              0, 1),
          value = min + (max - min) * fraction;
    float step = numericAttr(hit.frame, "step", 0);
    if (step > 0) value = min + Math.round((value - min) / step) * step;
    Object old = bindings.get(hit.bind, hit.frame.context);
    String encoded = encodedSliderValue(hit.frame, old, clamp(value, min, max));
    if ("release".equals(attr(hit.frame, "commit", ""))
        || hit.bind != null && hit.bind.equals(hit.frame.context.get("__releaseBind"))) {
      pendingBind = hit.bind;
      pendingValue = encoded;
      return;
    }
    if (bindings.set(hit.bind, encoded, false, hit.frame.context)) changedBinding(hit.bind, true);
  }

  private Object preview(String bind, Object original) {
    if (bind == null || !bind.equals(pendingBind) || pendingValue.isEmpty()) return original;
    try {
      if (original instanceof Integer) return Integer.valueOf(pendingValue);
      return Float.valueOf(pendingValue);
    } catch (NumberFormatException invalid) {
      return original;
    }
  }

  private void checkpointBinding(String bind) {
    if (bind == null || !bind.startsWith("ui.") && !bind.startsWith("local.")) actions.checkpoint();
  }

  private void changedBinding(String bind) {
    changedBinding(bind, false);
  }

  private void changedBinding(String bind, boolean live) {
    invalidateLayout();
    if ("ui.search".equals(bind)) {
      ui.put("moduleOpen", false);
    }
    if (bind == null || !bind.startsWith("ui.") && !bind.startsWith("local.")) {
      if (live) {
        liveBindingDirty = true;
        actions.changedLive();
      } else {
        liveBindingDirty = false;
        actions.changed();
      }
    }
  }

  private void applyManualExpanded(String scope, boolean next) {
    if (scope == null) return;
    Map<String, Object> realState = localStates.get(scope);
    boolean wasSearchAuto = !activeSearchPlan.isEmpty() && activeSearchPlan.isScopeExpanded(scope);
    boolean wasPersistedTrue = settings.interfaceState != null
        && settings.interfaceState.expandedPanels != null
        && Boolean.TRUE.equals(settings.interfaceState.expandedPanels.get(scope));
    if (realState != null) realState.put("expanded", next);
    if (wasSearchAuto) {
      if (next) {
        searchManualCollapsed.remove(scope);
        persistExpanded(scope, true);
      } else {
        searchManualCollapsed.add(scope);
        if (wasPersistedTrue) persistExpanded(scope, false);
      }
    } else {
      persistExpanded(scope, next);
    }
  }

  private void persistExpanded(String scope, boolean expanded) {
    if (settings.interfaceState == null || scope == null || !scope.startsWith("module.")
        || scope.length() > 128)
      return;
    if (settings.interfaceState.expandedPanels == null)
      settings.interfaceState.expandedPanels = new java.util.LinkedHashMap<String, Boolean>();
    java.util.LinkedHashMap<String, Boolean> stored = settings.interfaceState.expandedPanels;
    if (Boolean.valueOf(expanded).equals(stored.get(scope))) return;
    stored.put(scope, expanded);
    while (stored.size() > 256) stored.remove(stored.keySet().iterator().next());
    actions.changed();
  }

  private void act(String action, Map<String, Object> context) {
    if (action.startsWith("sequence:")) {
      String[] chain = action.substring(9).split(";", -1);
      if (chain.length <= 16)
        for (String part : chain)
          if (!part.startsWith("sequence:")) act(part.trim(), context);
      return;
    }
    if (host != null && host.action(action)) return;
    if ("close".equals(action))
      actions.close();
    else if ("language".equals(action)) {
      actions.checkpoint();
      List<String> languages = dev.nightclient.i18n.I18n.languages();
      settings.language = languages.get(
          (languages.indexOf(dev.nightclient.i18n.I18n.language(settings.language)) + 1)
          % languages.size());
      catalog.refresh();
      actions.changed();
    } else if ("color-close".equals(action)) {
      colorBind = "";
      focused = null;
      dragged = null;
    } else if (action.startsWith("toggle-module:")) {
      String id = action.substring(14);
      UiCatalog.Module module = catalog.module(id);
      if (module != null && module.id.equals(id)) {
        boolean same = (module.hud ? selectedHud : selectedModule).equals(id);
        ui.put("moduleOpen", !same || !Boolean.TRUE.equals(ui.get("moduleOpen")));
        if (module.hud)
          selectedHud = id;
        else
          selectedModule = id;
      }
    } else if (action.startsWith("open-module:")) {
      String id = action.substring(12);
      UiCatalog.Module module = catalog.module(id);
      if (module != null && module.id.equals(id)) selectModule(id);
    } else if ("module-back".equals(action))
      ui.put("moduleOpen", false);
    else if (action.startsWith("clipboard-copy:")) {
      String bind = action.substring(15);
      Object value = bindings.get(bind, context);
      if (value instanceof Integer)
        actions.clipboard(Colors.hex((Integer) value));
      else if (value instanceof String)
        actions.clipboard((String) value);
    } else if (action.startsWith("clipboard-paste:")) {
      String bind = action.substring(16);
      Object old = bindings.get(bind, context);
      String value = clean(actions.clipboard()).trim();
      if ((old instanceof Integer || old instanceof String) && bindings.writable(bind, context)) {
        checkpointBinding(bind);
        if (bindings.set(bind, value, old instanceof Integer, context)) changedBinding(bind);
      }
    } else if (action.startsWith("local-toggle:")) {
      String localName = action.substring(13);
      String bind = "local." + localName;
      Object old = bindings.get(bind, context);
      if (old instanceof Boolean) {
        boolean next = !((Boolean) old);
        if (bindings.set(bind, Boolean.toString(next), false, context)
            && "expanded".equals(localName)) {
          Object scopeObj = context.get("__scope");
          String scope = scopeObj == null ? null : String.valueOf(scopeObj);
          applyManualExpanded(scope, next);
          invalidateLayout();
        }
      }
    } else if (action.startsWith("local-set:")) {
      int split = action.indexOf(':', 10);
      if (split > 10) {
        String localName = action.substring(10, split), value = action.substring(split + 1);
        if (bindings.set("local." + localName, value, false, context)
            && "expanded".equals(localName) && ("true".equals(value) || "false".equals(value))) {
          Object scopeObj = context.get("__scope");
          String scope = scopeObj == null ? null : String.valueOf(scopeObj);
          applyManualExpanded(scope, Boolean.parseBoolean(value));
          invalidateLayout();
        }
      }
    } else if (action.startsWith("toggle:")) {
      String bind = action.substring(7);
      Object old = bindings.get(bind, context);
      if (old instanceof Boolean && bindings.writable(bind, context)) {
        checkpointBinding(bind);
        if (bindings.set(bind, Boolean.toString(!((Boolean) old)), false, context))
          changedBinding(bind);
      }
    } else if (action.startsWith("ui-set:")) {
      int split = action.indexOf(':', 7);
      if (split > 7)
        bindings.set("ui." + action.substring(7, split), action.substring(split + 1), false);
    } else if (action.startsWith("ui-toggle:")) {
      String key = action.substring(10);
      Object old = ui.get(key);
      if (old instanceof Boolean) {
        boolean next = !((Boolean) old);
        ui.put(key, next);
        if ("sidebarCollapsed".equals(key) && settings.interfaceState != null) {
          if (settings.interfaceState.sidebarCollapsed != next) {
            settings.interfaceState.sidebarCollapsed = next;
            actions.changed();
          }
          invalidateLayout();
        }
      }
    } else if (action.startsWith("set:")) {
      int split = action.indexOf(':', 4);
      if (split > 4) {
        String bind = action.substring(4, split), value = action.substring(split + 1);
        if (bindings.writable(bind, context)) {
          checkpointBinding(bind);
          if (bindings.set(bind, value, value.startsWith("#"), context)) changedBinding(bind);
        }
      }
    } else if (action.startsWith("module:")) {
      String id = action.substring(7);
      UiCatalog.Module module = catalog.module(id);
      if (module != null) {
        if (module.hud)
          selectedHud = id;
        else
          selectedModule = id;
      }
    } else if (action.startsWith("pixel-layer:")) {
      String layer = action.substring(12);
      ui.put("crosshairLayer", "target".equals(layer) ? "target" : "normal");
    } else if ("pixel-copy-to-target".equals(action)) {
      actions.checkpoint();
      settings.crosshair.targetPattern = settings.crosshair.pattern != null
          ? settings.crosshair.pattern.copy()
          : dev.nightclient.config.CrosshairPattern.defaultTargetPattern();
      ui.put("crosshairLayer", "target");
      actions.changed();
    } else if (action.startsWith("pixel-preset:")) {
      actions.checkpoint();
      boolean editingTarget = isEditingTargetCrosshair();
      activeCrosshairPattern().preset(action.substring(13));
      if (!editingTarget) settings.crosshair.mode = "pixels";
      actions.changed();
    } else if (action.startsWith("pixel-size:")) {
      actions.checkpoint();
      try {
        boolean editingTarget = isEditingTargetCrosshair();
        activeCrosshairPattern().resize(Integer.parseInt(action.substring(11)));
        if (!editingTarget) settings.crosshair.mode = "pixels";
        actions.changed();
      } catch (NumberFormatException ignored) {
      }
    } else if ("pixel-clear".equals(action)) {
      actions.checkpoint();
      activeCrosshairPattern().clear();
      actions.changed();
    } else if ("create-examples".equals(action))
      safe(new Runnable() {
        public void run() {
          actions.createSkinExamples();
          showNotification(actions.skinStatus());
        }
      });
    else if ("profile-save".equals(action))
      safe(new Runnable() {
        public void run() {
          String name = String.valueOf(ui.get("profileName")).trim();
          if (name.isEmpty()) throw new IllegalArgumentException(tr("ui.enter_a_profile_name"));
          actions.saveProfile(name);
          profileCache = null;
          showNotification(tr("ui.saved"));
        }
      });
    else if (action.startsWith("profile-load:")) {
      final String name = action.substring(13);
      safe(new Runnable() {
        public void run() {
          actions.checkpoint();
          actions.loadProfile(name);
          ui.put("profileName", name);
          showNotification(tr("ui.profile_applied"));
        }
      });
    } else if (action.startsWith("profile-delete:"))
      ui.put("deleteProfile", action.substring(15));
    else if ("profile-delete-confirm".equals(action))
      safe(new Runnable() {
        public void run() {
          actions.deleteProfile(String.valueOf(ui.get("deleteProfile")));
          profileCache = null;
          ui.put("deleteProfile", "");
        }
      });
    else if ("profile-delete-cancel".equals(action))
      ui.put("deleteProfile", "");
    else if ("profile-export".equals(action))
      safe(new Runnable() {
        public void run() {
          actions.clipboard(actions.exportProfile());
          showNotification(tr("ui.copied"));
        }
      });
    else if ("profile-import".equals(action))
      safe(new Runnable() {
        public void run() {
          actions.checkpoint();
          actions.importProfile(actions.clipboard());
          showNotification(tr("ui.applied"));
        }
      });
    else if ("waypoint-here".equals(action))
      safe(() -> {
        int before = settings.waypoints.points != null ? settings.waypoints.points.size() : 0;
        if (!actions.waypointAddHere()) throw new IllegalArgumentException(tr("waypoint.full"));
        showNotification(tr("waypoint.added"));
        if (settings.waypoints.points != null && settings.waypoints.points.size() > before) {
          Waypoint added = settings.waypoints.points.get(settings.waypoints.points.size() - 1);
          if (waypointReturnList || "list".equals(ui.get("waypointManager"))) {
            ui.put("waypointManager", "list");
            ui.put("waypointDraftId", added.id);
          } else {
            ui.put("waypointDraftId", "");
          }
        }
      });
    else if ("waypoint-manager-open".equals(action)) {
      waypointReturnList = true;
      ui.put("waypointManager", "list");
      ui.put("waypointDeleteConfirm", false);
      ui.put("waypointDraftId", "");
    } else if ("world-map-open".equals(action)) {
      actions.openWorldMap();
    } else if ("map-settings-open".equals(action)) {
      actions.openMapSettings();
    } else if ("waypoints-menu-open".equals(action)) {
      actions.openWaypointsMenu();
    } else if ("map-clear-world-cache".equals(action)) {
      actions.clearCurrentWorldMapCache();
    } else if ("map-clear-all-cache".equals(action)) {
      actions.clearAllMapCache();
    } else if ("account-manager-open".equals(action)) {
      if (settings.accountSwitcher != null && "classic".equals(settings.accountSwitcher.layout)
          && actions.openVanillaAccountScreen()) {
        ui.put("accountManager", "");
        standaloneAccount = false;
        menuFrameCache.clear();
      } else {
        openAccountManager(false);
      }
    } else if ("account-manager-close".equals(action)) {
      closeAccountManager();
    } else if ("account-manager-noop".equals(action)) {
    } else if ("account-layout-toggle".equals(action)) {
      if (settings.accountSwitcher == null) settings.accountSwitcher = new Settings.AccountSwitcher();
      settings.accountSwitcher.layout =
          "classic".equals(settings.accountSwitcher.layout) ? "modern" : "classic";
      actions.changed();
      if ("classic".equals(settings.accountSwitcher.layout) && actions.openVanillaAccountScreen()) {
        ui.put("accountManager", "");
        standaloneAccount = false;
        menuFrameCache.clear();
      }
    } else if ("account-layout-modern".equals(action)) {
      if (settings.accountSwitcher == null) settings.accountSwitcher = new Settings.AccountSwitcher();
      settings.accountSwitcher.layout = "modern";
      actions.changed();
    } else if ("account-layout-classic".equals(action)) {
      if (settings.accountSwitcher == null) settings.accountSwitcher = new Settings.AccountSwitcher();
      settings.accountSwitcher.layout = "classic";
      actions.changed();
      if (actions.openVanillaAccountScreen()) {
        ui.put("accountManager", "");
        standaloneAccount = false;
        menuFrameCache.clear();
      }
    } else if ("account-launcher-select".equals(action)) {
      actions.clearAccountError();
      ui.put("accountSelectedId", "");
    } else if ("account-launcher-restore".equals(action)) {
      if (actions.restoreLauncherAccount()) {
        ui.put("accountSelectedId", "");
        showNotification(tr("accounts.status.restored"));
        if (standaloneAccount && settings.accountSwitcher != null
            && settings.accountSwitcher.closeOnSwitch)
          closeAccountManager();
      }
    } else if ("account-reconnect".equals(action)) {
      String err = actions.reconnectCurrentServer();
      if (err != null && !err.isEmpty()) showNotification(tr(err));
    } else if ("account-create-open".equals(action)) {
      startAccountDraft(null);
    } else if ("account-random-nick".equals(action)) {
      actions.clearAccountError();
      String generated = actions.generateRandomAccountName();
      String mode = String.valueOf(ui.get("accountManager"));
      if ("create".equals(mode) || "edit".equals(mode)) {
        ui.put("accountDraftName", generated);
      } else {
        ui.put("accountSearch", generated);
      }
    } else if ("account-quick-switch".equals(action)) {
      performQuickSwitchAccount();
    } else if ("account-switch-selected".equals(action)) {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      if (sel == null) {
        if (actions.restoreLauncherAccount()) {
          showNotification(tr("accounts.status.restored"));
          if (standaloneAccount && settings.accountSwitcher != null
              && settings.accountSwitcher.closeOnSwitch)
            closeAccountManager();
        }
      } else {
        switchSavedAccountUi(sel.id);
      }
    } else if ("account-edit-selected".equals(action)) {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      if (sel != null) startAccountDraft(sel);
    } else if ("account-delete-selected".equals(action)) {
      dev.nightclient.config.SavedAccount sel = selectedAccount();
      if (sel != null && actions.deleteSavedAccount(sel.id)) {
        ui.put("accountSelectedId", "");
      }
    } else if ("account-apply".equals(action)) {
      applyAccountDraft();
    } else if ("account-cancel".equals(action)) {
      actions.clearAccountError();
      String mode = String.valueOf(ui.get("accountManager"));
      if ("create".equals(mode) || "edit".equals(mode)) {
        ui.put("accountManager", "list");
        ui.put("accountDraftId", "");
      } else {
        closeAccountManager();
      }
    } else if (action.startsWith("account-select:")) {
      actions.clearAccountError();
      ui.put("accountSelectedId", action.substring("account-select:".length()));
    } else if (action.startsWith("account-switch:")) {
      switchSavedAccountUi(action.substring("account-switch:".length()));
    } else if (action.startsWith("account-pin:")) {
      actions.toggleAccountPin(action.substring("account-pin:".length()));
    } else if (action.startsWith("account-edit:")) {
      dev.nightclient.config.SavedAccount target =
          savedAccountById(action.substring("account-edit:".length()));
      if (target != null) startAccountDraft(target);
    } else if (action.startsWith("account-remove:")) {
      String id = action.substring("account-remove:".length());
      if (actions.deleteSavedAccount(id)) {
        if (id.equals(String.valueOf(ui.get("accountSelectedId"))))
          ui.put("accountSelectedId", "");
      }
    } else if (action.startsWith("account-copy:")) {
      dev.nightclient.config.SavedAccount target =
          savedAccountById(action.substring("account-copy:".length()));
      if (target != null) {
        actions.clipboard(target.name);
        showNotification(tr("accounts.status.copied"));
      }
    } else if ("waypoint-manager-close".equals(action))
      closeWaypointManager();
    else if ("waypoint-manager-noop".equals(action)) {
    } else if ("waypoint-create-open".equals(action))
      startWaypointDraft(null);
    else if ("waypoint-shuffle-color".equals(action))
      ui.put("waypointDraftColor", randomWaypointColor());
    else if (action.startsWith("waypoint-edit:")) {
      Waypoint point = marker(action.substring(14));
      if (point != null) startWaypointDraft(point);
    } else if (action.startsWith("waypoint-remove:")) {
      String id = action.substring(16);
      if (settings.waypoints.find(id) != null) {
        actions.checkpoint();
        settings.waypoints.remove(id);
        actions.changed();
        showNotification(tr("waypoint.removed"));
      }
    } else if (action.startsWith("waypoint-visibility:")) {
      Waypoint point = marker(action.substring(20));
      if (point != null) {
        actions.checkpoint();
        point.visible = !point.visible;
        actions.changed();
      }
    } else if (action.startsWith("waypoint-bind-world:")) {
      String currentWorld = Waypoint.normalizeWorld(actions.currentWorldId());
      Waypoint point = marker(action.substring(20));
      if (point != null && !currentWorld.isEmpty()) {
        actions.checkpoint();
        point.world = currentWorld;
        point.worldLabel = Waypoint.normalizeWorldLabel(actions.currentWorldLabel());
        actions.changed();
        showNotification(tr("waypoint.bound_world"));
      }
    } else if ("waypoint-delete-all".equals(action)) {
      if (!Boolean.TRUE.equals(ui.get("waypointDeleteConfirm"))) {
        ui.put("waypointDeleteConfirm", true);
      } else {
        actions.checkpoint();
        String currentWorld = Waypoint.normalizeWorld(actions.currentWorldId());
        boolean showAll = "all".equals(String.valueOf(ui.get("waypointScope")));
        if (settings.waypoints.points != null) {
          if (showAll || currentWorld.isEmpty())
            settings.waypoints.points.clear();
          else
            settings.waypoints.removeWorld(currentWorld);
        }
        ui.put("waypointDeleteConfirm", false);
        actions.changed();
        showNotification(tr("waypoint.deleted_all"));
      }
    } else if ("waypoint-delete-cancel".equals(action))
      ui.put("waypointDeleteConfirm", false);
    else if ("waypoint-apply".equals(action))
      applyWaypointDraft();
    else if ("waypoint-cancel".equals(action)) {
      if (standaloneWaypoint || !waypointReturnList)
        closeWaypointManager();
      else if ("waypoints".equals(page)) {
        ui.put("waypointManager", "");
        ui.put("waypointDraftId", "");
        ui.put("waypointDeleteConfirm", false);
      } else if ("edit".equals(ui.get("waypointManager"))
          || "create".equals(ui.get("waypointManager")))
        ui.put("waypointManager", "list");
      else
        closeWaypointManager();
    } else if ("cosmetics-open".equals(action)) {
      if (!"cosmetics".equals(page)) previewBackPage = page;
      previewLastTime = -1;
      page("cosmetics");
    } else if ("cosmetics-close".equals(action)) {
      page("cosmetics".equals(previewBackPage) ? "features" : previewBackPage);
    } else if ("cosmetics-reset".equals(action)) {
      previewYaw = -22f;
      previewPitch = previewPitchTarget = 9f;
      previewZoom = 1.12f;
      previewYawVelocity = 0;
    } else if ("cosmetics-auto".equals(action)) {
      previewAuto = !previewAuto;
    } else if (action.startsWith("cosmetics-cape-select:")) {
      actions.selectOfficialCape(action.substring("cosmetics-cape-select:".length()));
    } else if ("cosmetics-cape-reset".equals(action)) {
      actions.resetCapeToAccount();
    } else if ("cosmetics-cape-none".equals(action)) {
      actions.disableCape();
    } else if ("cosmetics-cape-browse".equals(action)) {
      actions.browseCustomCape();
    } else if ("cosmetics-cape-path".equals(action)) {
      actions.applyCustomCapePath(String.valueOf(ui.get("cosmeticsCapePathDraft")));
    } else if ("cosmetics-skin-nick".equals(action)) {
      actions.applySkinNickname(String.valueOf(ui.get("cosmeticsNickDraft")));
    } else if ("cosmetics-skin-browse".equals(action)) {
      actions.browseCustomSkin();
    } else if ("cosmetics-skin-path".equals(action)) {
      actions.applyCustomSkinPath(String.valueOf(ui.get("cosmeticsSkinPathDraft")));
    } else if ("cosmetics-skin-reset".equals(action)) {
      actions.resetSkinToAccount();
      ui.put("cosmeticsNickDraft", "");
    } else if (action.startsWith("cosmetics-skin-model:")) {
      actions.setCosmeticsSkinModel(action.substring("cosmetics-skin-model:".length()));
    } else if (action.startsWith("cosmetics-weapon-select:")) {
      actions.selectBuiltinWeapon(action.substring("cosmetics-weapon-select:".length()));
    } else if (action.startsWith("cosmetics-weapon-material:")) {
      actions.selectWeaponMaterial(action.substring("cosmetics-weapon-material:".length()));
    } else if ("cosmetics-weapon-reset".equals(action)) {
      actions.resetWeaponCosmetic();
    } else if ("cosmetics-weapon-browse".equals(action)) {
      actions.browseCustomWeapon();
    } else if ("cosmetics-weapon-path".equals(action)) {
      actions.applyCustomWeaponPath(String.valueOf(ui.get("cosmeticsWeaponPathDraft")));
    } else if (action.startsWith("cosmetics-weapon-target:")) {
      actions.setCosmeticsWeaponTarget(action.substring("cosmetics-weapon-target:".length()));
    } else if (action.startsWith("cosmetics-weapon-color:")) {
      actions.setCosmeticsWeaponColor(parseHexColor(action.substring("cosmetics-weapon-color:".length()), 0xff38bdf8));
    } else if (action.startsWith("cosmetics-armor-select:")) {
      actions.selectBuiltinArmor(action.substring("cosmetics-armor-select:".length()));
    } else if ("cosmetics-armor-reset".equals(action)) {
      actions.resetArmorCosmetic();
    } else if ("cosmetics-armor-browse".equals(action)) {
      actions.browseCustomArmor();
    } else if ("cosmetics-armor-path".equals(action)) {
      actions.applyCustomArmorPath(String.valueOf(ui.get("cosmeticsArmorPathDraft")));
    } else if (action.startsWith("cosmetics-armor-trigger:")) {
      actions.setCosmeticsArmorTrigger(action.substring("cosmetics-armor-trigger:".length()));
    } else if (action.startsWith("cosmetics-armor-color:")) {
      actions.setCosmeticsArmorColor(parseHexColor(action.substring("cosmetics-armor-color:".length()), 0xff38bdf8));
    } else if ("hw-events-refresh".equals(action)) {
      actions.refreshHwEvents();
      showNotification(tr("hwEvents.refreshed"));
    } else if (action.startsWith("hw-event-join:")) {
      String token = action.substring("hw-event-join:".length()).trim();
      if (!token.isEmpty() && actions.joinHwEvent(token)) {
        if (host != null) host.action("close");
        else actions.close();
      }
    } else if ("sound-block-add-draft".equals(action)) {
      addSoundBlockFromDraft();
    } else if ("sound-block-add-crosshair".equals(action)) {
      String hit = Settings.SoundControl.normalizeBlockQuery(actions.crosshairBlockId());
      if (hit.isEmpty() || "air".equals(hit) || "cave_air".equals(hit) || "void_air".equals(hit)) {
        showNotification(tr("soundControl.no_crosshair_block"));
      } else {
        addSoundBlockId(hit);
      }
    } else if ("sound-block-clear".equals(action)) {
      if (settings.soundControl != null && settings.soundControl.mutedBlocks != null
          && !settings.soundControl.mutedBlocks.isEmpty()) {
        actions.checkpoint();
        settings.soundControl.mutedBlocks.clear();
        actions.changed();
      }
    } else if (action.startsWith("sound-block-toggle:")) {
      toggleSoundBlockId(action.substring("sound-block-toggle:".length()));
    } else if (action.startsWith("sound-block-remove:")) {
      removeSoundBlockId(action.substring("sound-block-remove:".length()));
    } else if ("sound-test-tnt".equals(action)) {
      actions.previewClientSound("tnt");
    } else if ("sound-tnt-fuse-mp3-browse".equals(action)) {
      actions.browseTntMp3(false);
    } else if ("sound-tnt-fuse-mp3-clear".equals(action)) {
      actions.clearTntMp3(false);
    } else if ("sound-tnt-explode-mp3-browse".equals(action)) {
      actions.browseTntMp3(true);
    } else if ("sound-tnt-explode-mp3-clear".equals(action)) {
      actions.clearTntMp3(true);
    } else if ("sound-test-mention".equals(action)) {
      actions.previewClientSound("mention");
    } else if ("sound-test-effect".equals(action)) {
      actions.previewClientSound("effect");
    } else if ("reset".equals(action))
      actions.reset();
    else if ("undo".equals(action))
      actions.undo();
    else if ("reload-skin".equals(action))
      actions.reloadSkin();
    else if (action.startsWith("page:"))
      page(action.substring(5, Math.min(action.length(), 53)));
    else if ("activate".equals(action) || "component".equals(action)) {
      Object callback =
          context.containsKey("activate") ? context.get("activate") : context.get("onClick");
      if (callback instanceof Runnable) ((Runnable) callback).run();
    }
  }

  private static String shortAudioName(String raw) {
    if (raw == null || raw.trim().isEmpty()) return "";
    String s = raw.trim().replace('\\', '/');
    int slash = s.lastIndexOf('/');
    return slash >= 0 ? s.substring(slash + 1) : s;
  }

  private void closeWaypointManager() {
    standaloneWaypoint = false;
    waypointReturnList = false;
    ui.put("waypointManager", "");
    ui.put("waypointDraftId", "");
    ui.put("waypointDeleteConfirm", false);
  }

  private void startWaypointDraft(Waypoint point) {
    waypointReturnList = "list".equals(ui.get("waypointManager"))
        || ("waypoints".equals(page) && !standaloneWaypoint);
    ui.put("waypointManager", point == null ? "create" : "edit");
    ui.put("waypointDeleteConfirm", false);
    if (point != null) {
      ui.put("waypointDraftId", point.id);
      ui.put("waypointDraftName", point.label == null ? "" : point.label);
      ui.put("waypointDraftX", formatCoordinate(point.x));
      ui.put("waypointDraftY", formatCoordinate(point.y));
      ui.put("waypointDraftZ", formatCoordinate(point.z));
      ui.put("waypointDraftColor", Colors.hex(point.color));
      ui.put("waypointDraftDimension", point.dimension);
    } else {
      double[] position = actions.playerPosition();
      String dimension = actions.currentDimension();
      ui.put("waypointDraftId", "");
      ui.put("waypointDraftName", "");
      ui.put("waypointDraftX", position == null ? "0.0" : formatCoordinate(position[0]));
      ui.put("waypointDraftY", position == null ? "0.0" : formatCoordinate(position[1]));
      ui.put("waypointDraftZ", position == null ? "0.0" : formatCoordinate(position[2]));
      ui.put("waypointDraftColor", randomWaypointColor());
      ui.put("waypointDraftDimension",
          dimension == null || dimension.isEmpty() ? Waypoint.DEFAULT_DIMENSION : dimension);
    }
  }

  private void applyWaypointDraft() {
    safe(() -> {
      String manager = String.valueOf(ui.get("waypointManager"));
      if (!"create".equals(manager) && !"edit".equals(manager)) return;
      String id = String.valueOf(ui.get("waypointDraftId"));
      if (!"create".equals(manager) && (id == null || id.isEmpty()))
        throw new IllegalArgumentException(tr("waypoint.gone"));
      String name = String.valueOf(ui.get("waypointDraftName"));
      double x = parseCoordinate(String.valueOf(ui.get("waypointDraftX")));
      double y = parseCoordinate(String.valueOf(ui.get("waypointDraftY")));
      double z = parseCoordinate(String.valueOf(ui.get("waypointDraftZ")));
      int color = Colors.parseHex(String.valueOf(ui.get("waypointDraftColor")));
      String dimension = String.valueOf(ui.get("waypointDraftDimension"));
      if (dimension == null || dimension.isEmpty()) dimension = Waypoint.DEFAULT_DIMENSION;
      actions.checkpoint();
      String currentWorld = Waypoint.normalizeWorld(actions.currentWorldId());
      String currentWorldLabel = Waypoint.normalizeWorldLabel(actions.currentWorldLabel());
      if (id != null && !id.isEmpty()) {
        Waypoint point = marker(id);
        if (point == null) throw new IllegalArgumentException(tr("waypoint.gone"));
        point.label = name;
        point.x = x;
        point.y = y;
        point.z = z;
        point.color = color;
        if (!point.death) point.dimension = dimension;
        if (!currentWorld.isEmpty() && point.matchesWorld(currentWorld)
            && !currentWorldLabel.isEmpty()) {
          point.worldLabel = currentWorldLabel;
        }
        if (!point.normalize()) throw new IllegalArgumentException(tr("waypoint.invalid"));
      } else {
        Waypoint point = settings.waypoints.add(
            name, x, y, z, dimension, currentWorld, currentWorldLabel);
        if (point == null) throw new IllegalArgumentException(tr("waypoint.full"));
        point.color = color;
        if (!point.normalize()) throw new IllegalArgumentException(tr("waypoint.invalid"));
      }
      actions.changed();
      if (standaloneWaypoint || !waypointReturnList)
        closeWaypointManager();
      else {
        ui.put("waypointManager", "waypoints".equals(page) ? "" : "list");
        ui.put("waypointDraftId", "");
      }
      showNotification(tr("ui.saved"));
    });
  }

  private static double parseCoordinate(String value) {
    try {
      String text = value == null ? "" : value.trim().replace(',', '.');
      if (text.isEmpty()) throw new NumberFormatException();
      return Double.parseDouble(text);
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(dev.nightclient.i18n.I18n.text(null, "waypoint.invalid"));
    }
  }

  private static String formatCoordinate(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private String randomWaypointColor() {
    hudFrameDynamic = true;
    float hue = (float) ((Math.random() + time * 0.61803398875) % 1.0);
    return Colors.hex(Colors.hsv(hue, .58f, .95f, 1f));
  }

  private void dismissPopup() {
    Popup popup = popups.remove(popups.size() - 1);
    focused = dragged = null;
    pressedKey = "";
    pendingBind = pendingValue = "";
    tooltip = tooltipKey = hoveredTooltipKey = "";
    tooltipAnchor = null;
    act(popup.action, popup.frame.context);
  }

  private static final class Popup {
    final Frame frame;
    final Bounds bounds;
    final String action;

    Popup(Frame frame, Bounds bounds, String action) {
      this.frame = frame;
      this.bounds = bounds;
      this.action = action;
    }
  }

  public boolean isTextFocused() {
    return focused != null && "input".equals(focused.node.tag) && hasHit(focused.key);
  }

  public boolean keyPressed(int key, int scanCode, int modifiers) {
    menuFrameCache.clear();
    if (key == 256 && !pendingBind.isEmpty()) {
      pendingBind = pendingValue = "";
      dragged = null;
      pressedKey = "";
      return true;
    }
    if (key == 256 && !colorBind.isEmpty()) {
      colorBind = "";
      focused = null;
      dragged = null;
      return true;
    }
    if (key == 256 && focused != null && "input".equals(focused.node.tag)) {
      focused = null;
      return true;
    }
    if (key == 256 && !popups.isEmpty()) {
      dismissPopup();
      return true;
    }
    if (key == 256 && isAccountManagerOpen()) {
      String mode = String.valueOf(ui.get("accountManager"));
      if ("create".equals(mode) || "edit".equals(mode)) {
        actions.clearAccountError();
        ui.put("accountManager", "list");
        ui.put("accountDraftId", "");
        return true;
      }
      if (!standaloneAccount) {
        closeAccountManager();
        return true;
      }
    }
    if (focused == null) return false;
    if (pickerControl(focused.node.tag)) {
      if (key < 262 || key > 265) return false;
      ColorPickerModel model = colorModel(focused.frame);
      float step = (modifiers & 1) != 0 ? .1f : .01f;
      checkpointBinding(focused.bind);
      if ("color-plane".equals(focused.node.tag)) {
        if (key == 262 || key == 263)
          model.saturation = clamp(model.saturation + (key == 262 ? step : -step), 0, 1);
        else
          model.value = clamp(model.value + (key == 265 ? step : -step), 0, 1);
      } else if ("hue-strip".equals(focused.node.tag))
        model.hue = clamp(model.hue + (key == 262 || key == 265 ? step : -step), 0, 1);
      else
        model.alpha = clamp(model.alpha + (key == 262 || key == 265 ? step : -step), 0, 1);
      commitColor(focused.frame, model);
      return true;
    }
    if ("slider".equals(focused.node.tag)) {
      if (key != 263 && key != 262 && key != 268 && key != 269) return false;
      float min = numericAttr(focused.frame, "min", 0), max = numericAttr(focused.frame, "max", 1),
            step = numericAttr(focused.frame, "step", .01f);
      Object current = bindings.get(focused.bind, focused.frame.context);
      float value = current instanceof Number
          ? ((Number) current).floatValue() * numericAttr(focused.frame, "value-scale", 1)
              + numericAttr(focused.frame, "value-offset", 0)
          : min;
      value = key == 268 ? min
          : key == 269   ? max
                         : value + (key == 262 ? 1 : -1) * step * ((modifiers & 1) != 0 ? 10 : 1);
      checkpointBinding(focused.bind);
      if (bindings.set(focused.bind,
              encodedSliderValue(focused.frame, current, clamp(value, min, max)), false,
              focused.frame.context))
        changedBinding(focused.bind);
      return true;
    }
    String text = inputBuffer;
    caret = clamp(caret, 0, text.length());
    selection = clamp(selection, 0, text.length());
    boolean control = (modifiers & 2) != 0, shift = (modifiers & 1) != 0;
    if (key == 256 || key == 257 || key == 335) {
      String bind = focused.bind;
      focused = null;
      if (key == 257 || key == 335) {
        if ("ui.cosmeticsNickDraft".equals(bind))
          actions.applySkinNickname(String.valueOf(ui.get("cosmeticsNickDraft")));
        else if ("ui.cosmeticsCapePathDraft".equals(bind))
          actions.applyCustomCapePath(String.valueOf(ui.get("cosmeticsCapePathDraft")));
        else if ("ui.cosmeticsSkinPathDraft".equals(bind))
          actions.applyCustomSkinPath(String.valueOf(ui.get("cosmeticsSkinPathDraft")));
        else if ("ui.accountSearch".equals(bind))
          performQuickSwitchAccount();
        else if ("ui.accountDraftName".equals(bind) || "ui.accountDraftNote".equals(bind)
            || "ui.accountDraftSkin".equals(bind))
          applyAccountDraft();
        else if ("ui.soundBlockDraft".equals(bind))
          addSoundBlockFromDraft();
      }
      return true;
    }
    if (control && key == 65) {
      selection = 0;
      caret = text.length();
      return true;
    }
    if (control && (key == 67 || key == 88)) {
      actions.clipboard(text.substring(Math.min(caret, selection), Math.max(caret, selection)));
      if (key == 88) replaceSelection(text, "");
      return true;
    }
    if (control && key == 86) {
      replaceSelection(text, clean(actions.clipboard()));
      return true;
    }
    if (control && (key == 259 || key == 261)) {
      if (caret != selection)
        replaceSelection(text, "");
      else if (key == 259) {
        int start = wordStart(text, caret);
        if (start < caret) {
          selection = start;
          replaceSelection(text, "");
        }
      } else {
        int end = wordEnd(text, caret);
        if (end > caret) {
          selection = end;
          replaceSelection(text, "");
        }
      }
      return true;
    }
    if (key == 259 || key == 261) {
      if (caret != selection)
        replaceSelection(text, "");
      else if (key == 259 && caret > 0) {
        selection = text.offsetByCodePoints(caret, -1);
        replaceSelection(text, "");
      } else if (key == 261 && caret < text.length()) {
        selection = text.offsetByCodePoints(caret, 1);
        replaceSelection(text, "");
      }
      return true;
    }
    if (key == 263 || key == 262 || key == 268 || key == 269) {
      if (control && key == 268)
        caret = 0;
      else if (control && key == 269)
        caret = text.length();
      else if (key == 268)
        caret = 0;
      else if (key == 269)
        caret = text.length();
      else if (key == 263)
        caret =
            control ? wordStart(text, caret) : (caret > 0 ? text.offsetByCodePoints(caret, -1) : 0);
      else
        caret = control
            ? wordEnd(text, caret)
            : (caret < text.length() ? text.offsetByCodePoints(caret, 1) : text.length());
      if (!shift) selection = caret;
      return true;
    }
    if (key == 258) {
      for (int index = 0; index < hits.size(); index++)
        if (hits.get(index).key.equals(focused.key)) {
          for (int step = 1; step <= hits.size(); step++) {
            Hit next = hits.get((index + step) % hits.size());
            if ("input".equals(next.node.tag) && bindings.writable(next.bind, next.frame.context)) {
              focusInput(next);
              return true;
            }
          }
        }
      return true;
    }
    return true;
  }

  public boolean charTyped(char character, int modifiers) {
    return charTyped((int) character, modifiers);
  }

  public boolean charTyped(int codePoint, int modifiers) {
    menuFrameCache.clear();
    if (codePoint >= 0xd800 && codePoint <= 0xdfff) return false;
    if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) return false;
    if (focused == null) {
      if (!pendingBind.isEmpty() || !colorBind.isEmpty() || !popups.isEmpty()) return false;
      Hit search = searchHit();
      if (search == null) return false;
      focusInput(search);
      inputBuffer = "";
      caret = selection = 0;
      replaceSelection("", new String(Character.toChars(codePoint)));
      return true;
    }
    if (!"input".equals(focused.node.tag)) return false;
    replaceSelection(inputBuffer, new String(Character.toChars(codePoint)));
    return true;
  }

  private Hit searchHit() {
    for (int index = hits.size() - 1; index >= 0; index--) {
      Hit hit = hits.get(index);
      if ("input".equals(hit.node.tag) && "true".equals(attr(hit.frame, "search-input", ""))
          && hasHit(hit.key)) {
        String pages = attr(hit.frame, "search-pages", "");
        String target = attr(hit.frame, "search-page", "");
        if (!pages.isEmpty() && !target.isEmpty() && !searchPageAllowed(pages)) page(target);
        return hit;
      }
    }
    return null;
  }

  private boolean searchPageAllowed(String pages) {
    for (String value : pages.split("[,\\s]+"))
      if (value.equals(page)) return true;
    return false;
  }

  private static boolean wordChar(int codePoint) {
    return Character.isLetterOrDigit(codePoint) || codePoint == '_';
  }

  private static int wordStart(String text, int at) {
    int index = at;
    while (index > 0) {
      int codePoint = text.codePointBefore(index);
      if (!Character.isWhitespace(codePoint)) break;
      index -= Character.charCount(codePoint);
    }
    if (index <= 0) return 0;
    int codePoint = text.codePointBefore(index);
    if (wordChar(codePoint)) {
      while (index > 0 && wordChar(text.codePointBefore(index)))
        index -= Character.charCount(text.codePointBefore(index));
    } else {
      while (index > 0) {
        int previous = text.codePointBefore(index);
        if (Character.isWhitespace(previous) || wordChar(previous)) break;
        index -= Character.charCount(previous);
      }
    }
    return index;
  }

  private static int wordEnd(String text, int at) {
    int index = at, length = text.length();
    while (index < length) {
      int codePoint = text.codePointAt(index);
      if (!Character.isWhitespace(codePoint)) break;
      index += Character.charCount(codePoint);
    }
    if (index >= length) return length;
    int codePoint = text.codePointAt(index);
    if (wordChar(codePoint)) {
      while (index < length && wordChar(text.codePointAt(index)))
        index += Character.charCount(text.codePointAt(index));
    } else {
      while (index < length) {
        int current = text.codePointAt(index);
        if (Character.isWhitespace(current) || wordChar(current)) break;
        index += Character.charCount(current);
      }
    }
    return index;
  }

  private void replaceSelection(String old, String inserted) {
    int first = Math.min(caret, selection), last = Math.max(caret, selection);
    String value = old.substring(0, first) + inserted + old.substring(last);
    int max = Math.round(numericAttr(focused.frame, "maxlength", 128));
    max = clamp(max, 1, 128);
    if (value.length() > max) value = value.substring(0, max);
    inputBuffer = value;
    caret = selection = Math.min(value.length(), first + inserted.length());
    boolean hex = "hex".equals(attr(focused.frame, "format", ""));
    if (bindings.set(focused.bind, value, hex, focused.frame.context)) changedBinding(focused.bind);
  }

  private static String clean(String value) {
    if (value == null) return "";
    StringBuilder clean = new StringBuilder();
    for (int at = 0; at < value.length() && clean.length() < 128;) {
      int cp = value.codePointAt(at);
      at += Character.charCount(cp);
      if (!Character.isISOControl(cp)) clean.appendCodePoint(cp);
    }
    return clean.toString();
  }

  private void focusInput(Hit hit) {
    if (focused == null || !focused.key.equals(hit.key)) {
      inputBuffer = inputText(hit.frame, bindings.get(hit.bind, hit.frame.context));
      caret = selection = inputBuffer.length();
      inputScroll = 0;
    }
    focused = hit;
  }

  private static String inputText(Frame frame, Object value) {
    return value instanceof Integer && "hex".equals(attr(frame, "format", ""))
        ? Colors.hex((Integer) value)
        : value == null ? ""
                        : String.valueOf(value);
  }

  private static String inputDisplayText(Frame frame, Object value) {
    String format = attr(frame, "display-format", "");
    return value instanceof Number && "number".equals(attr(frame, "format", ""))
            && format.length() == 7 && format.startsWith("fixed:") && format.charAt(6) >= '0'
            && format.charAt(6) <= '3'
        ? String.format(Locale.ROOT, "%." + format.charAt(6) + "f", ((Number) value).doubleValue())
        : inputText(frame, value);
  }

  private List<String> profiles() {
    if (profileCache == null) {
      profileCache = new ArrayList<String>();
      try {
        for (String name : safe(actions.profiles()))
          if (name != null && name.length() <= 64 && profileCache.size() < 64)
            profileCache.add(name);
      } catch (RuntimeException failure) {
        showNotification(
            failure.getMessage() == null ? tr("ui.could_not_read_profiles") : failure.getMessage());
      }
    }
    return profileCache;
  }

  private List<UiCatalog.Module> filteredModules() {
    return filteredModules(false);
  }

  private List<UiCatalog.Module> filteredModules(boolean hud) {
    return filterModuleList(catalog.modules(hud));
  }

  private List<UiCatalog.Module> filteredAllModules() {
    return filterModuleList(catalog.allVisibleModules());
  }

  private List<UiCatalog.Module> filterModuleList(List<UiCatalog.Module> all) {
    String search = ModuleSearchIndex.normalize(searchQuery());
    if (search.isEmpty()) return all;
    searchIndex.ensureBuilt(
        document, catalog, bindings, dev.nightclient.i18n.I18n.language(settings.language));
    List<UiCatalog.Module> titleMatches = new ArrayList<UiCatalog.Module>();
    List<UiCatalog.Module> otherMatches = new ArrayList<UiCatalog.Module>();
    for (UiCatalog.Module m : all) {
      if (searchIndex.matchesModuleTitle(m.id, search)) titleMatches.add(m);
      else if (searchIndex.matchesModule(m.id, search)) otherMatches.add(m);
    }
    if (otherMatches.isEmpty()) return titleMatches;
    titleMatches.addAll(otherMatches);
    return titleMatches;
  }

  private List<UiCatalog.Module> categoryModules() {
    if ("settings".equals(page) || "events".equals(page)) return Collections.emptyList();
    if ("hud".equals(page)) return filteredModules(true);
    if ("all".equals(page)) {
      return filteredAllModules();
    }
    return filteredCategory("features".equals(page) ? "render" : page);
  }

  private List<UiCatalog.Module> filteredCategory(String category) {
    List<UiCatalog.Module> rows = new ArrayList<UiCatalog.Module>();
    for (UiCatalog.Module module : filteredModules())
      if (category.equals(module.category)) rows.add(module);
    return rows;
  }

  private List<?> source(String source, Map<String, Object> context) {
    if (frameValues != null) {
      Object data = bindings.get(source, frameValues);
      if (data instanceof List) return (List<?>) data;
    }
    if (("cooldownRows".equals(source) || "inventoryCells".equals(source)
            || "tntRows".equals(source) || "armorSlots".equals(source)
            || "potionRows".equals(source) || "targetRows".equals(source))) {
      Object rows =
          (frameValues == null ? context(viewportWidth, viewportHeight, false) : frameValues)
              .get(source);
      return rows instanceof List ? (List<?>) rows : Collections.emptyList();
    }
    if ("armor".equals(source)) return safe(actions.armor());
    if ("potions".equals(source)) return safe(actions.potions());
    if ("vitals".equals(source)) {
      dev.nightclient.api.VitalsView v = actions.vitals();
      return v == null || !v.available ? Collections.emptyList() : Collections.singletonList(v);
    }
    if ("target".equals(source)) {
      dev.nightclient.api.TargetView t = actions.target();
      return t == null ? Collections.emptyList() : Collections.singletonList(t);
    }
    if ("cooldowns".equals(source)) return cooldowns;
    if ("inventory".equals(source)) return inventory;
    if ("tnt".equals(source)) return tnts;
    if ("visibleCooldowns".equals(source))
      return HudRenderer.visibleCooldowns(settings, cooldowns, viewportWidth, viewportHeight);
    if ("categoryModules".equals(source)) return categoryModules();
    if ("filteredModules".equals(source)) return filteredModules();
    if ("allStyleControls".equals(source)) return catalog.styleControls(true);
    if ("choiceOptions".equals(source)) {
      String[] options = String.valueOf(context.get("options")).split("\\|", -1),
               labels = String.valueOf(context.get("optionLabels")).split("\\|", -1);
      List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
      for (int i = 0; i < Math.min(128, options.length); i++) {
        Map<String, Object> option = new HashMap<String, Object>();
        option.put("value", options[i]);
        option.put("label", i < labels.length && !labels[i].isEmpty() ? labels[i] : options[i]);
        option.put("selected", options[i].equals(String.valueOf(context.get("value"))));
        option.put("index", i);
        rows.add(option);
      }
      return rows;
    }
    if ("renderModules".equals(source) || "playerModules".equals(source)) {
      List<UiCatalog.Module> rows = new ArrayList<UiCatalog.Module>();
      String category = "renderModules".equals(source) ? "render" : "player";
      for (UiCatalog.Module module : filteredModules())
        if (category.equals(module.category)) rows.add(module);
      return rows;
    }
    if ("modules".equals(source)) return catalog.modules(false);
    if ("hudModules".equals(source)) return catalog.modules(true);
    if ("filteredHudModules".equals(source)) return filteredModules(true);
    if ("controls".equals(source)) {
      for (Object value : context.values())
        if (value instanceof UiCatalog.Module)
          return catalog.controls(((UiCatalog.Module) value).id);
      return catalog.controls("hud".equals(page) ? selectedHud : selectedModule);
    }
    if ("editorControls".equals(source)) {
      Object target = host == null ? null : host.context().get("editorTarget");
      String id = target == null ? selectedHud : String.valueOf(target);
      return catalog.editorControls("cooldowns".equals(id) ? "hud" : id);
    }
    if ("styleControls".equals(source))
      return catalog.styleControls(Boolean.TRUE.equals(ui.get("moreStyle")));
    if ("menuControls".equals(source)) return catalog.controls("menu");
    if ("profiles".equals(source)) {
      List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
      for (String name : profiles()) {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("name", name);
        values.add(row);
      }
      return values;
    }
    if ("savedWaypoints".equals(source)) return markerRows();
    if ("savedAccounts".equals(source)) return accountRows();
    if ("hwEvents".equals(source)) return actions.hwEvents();
    if ("mutedBlocks".equals(source)) return soundMutedRows();
    if ("popularSoundBlocks".equals(source)) return popularSoundRows();
    if ("officialCapes".equals(source)) return officialCapeRows();
    if ("cosmeticWeapons".equals(source)) return cosmeticModelRows(false);
    if ("cosmeticWeaponMaterials".equals(source)) return cosmeticMaterialRows();
    if ("cosmeticArmors".equals(source)) return cosmeticModelRows(true);
    return Collections.emptyList();
  }

  private boolean hoverContains(
      Frame frame, float bx, float by, float bw, float bh, Transform transform, float pointX,
      float pointY) {
    float sx, sy, sw, sh;
    float clampedW = Math.max(0, bw), clampedH = Math.max(0, bh);
    if (transform == Transform.IDENTITY) {
      sx = bx;
      sy = by;
      sw = clampedW;
      sh = clampedH;
    } else {
      sx = transform.px(bx);
      sy = transform.py(by);
      sw = Math.max(0, clampedW * transform.sx);
      sh = Math.max(0, clampedH * transform.sy);
    }
    if (pointX < sx || pointY < sy || pointX >= sx + sw || pointY >= sy + sh) return false;
    String tag = frame.node.tag;
    boolean shape = isControl(frame) || "panel".equals(tag) || "menu".equals(tag)
        || "box".equals(tag) || frame.style.containsKey("background")
        || frame.style.containsKey("background-color");
    if (!shape) return true;
    float localX = transform == Transform.IDENTITY ? pointX : transform.inverseX(pointX);
    float localY = transform == Transform.IDENTITY ? pointY : transform.inverseY(pointY);
    float defaultRadius =
        number(frame.style, "radius", number(frame.style, "border-radius", settings.theme.radius));
    float limit = Math.max(0, Math.min(bw, bh) / 2);
    float tl = clamp(number(frame.style, "radius-top-left", defaultRadius), 0, limit);
    float tr = clamp(number(frame.style, "radius-top-right", defaultRadius), 0, limit);
    float bl = clamp(number(frame.style, "radius-bottom-left", defaultRadius), 0, limit);
    float br = clamp(number(frame.style, "radius-bottom-right", defaultRadius), 0, limit);
    return inRoundedCorner(localX, localY, bx + tl, by + tl, tl, true, true)
        && inRoundedCorner(localX, localY, bx + bw - tr, by + tr, tr, false, true)
        && inRoundedCorner(localX, localY, bx + bl, by + bh - bl, bl, true, false)
        && inRoundedCorner(localX, localY, bx + bw - br, by + bh - br, br, false, false);
  }

  private static boolean inRoundedCorner(
      float x, float y, float centerX, float centerY, float radius, boolean left, boolean top) {
    if (radius <= 0) return true;
    boolean inCorner = (left ? x < centerX : x > centerX) && (top ? y < centerY : y > centerY);
    if (!inCorner) return true;
    float dx = x - centerX, dy = y - centerY;
    return dx * dx + dy * dy <= radius * radius;
  }

  private void ensureSoundControl() {
    if (settings.soundControl == null) settings.soundControl = new Settings.SoundControl();
    if (settings.soundControl.mutedBlocks == null)
      settings.soundControl.mutedBlocks = new ArrayList<String>();
  }

  private void addSoundBlockFromDraft() {
    String raw = String.valueOf(ui.get("soundBlockDraft"));
    if (raw == null || raw.trim().isEmpty()) return;
    String[] tokens = raw.split("[,;]+");
    boolean added = false;
    ensureSoundControl();
    actions.checkpoint();
    for (int i = 0; i < tokens.length; i++) {
      String id = Settings.SoundControl.normalizeBlockQuery(tokens[i]);
      if (id.isEmpty() || "air".equals(id)) continue;
      if (!settings.soundControl.mutedBlocks.contains(id)
          && settings.soundControl.mutedBlocks.size() < 256) {
        settings.soundControl.mutedBlocks.add(id);
        added = true;
      }
    }
    ui.put("soundBlockDraft", "");
    if (added) {
      settings.soundControl.muteBlocks = true;
      actions.changed();
      showNotification(tr("soundControl.block_added"));
    }
    menuFrameCache.clear();
  }

  private void addSoundBlockId(String rawId) {
    String id = Settings.SoundControl.normalizeBlockQuery(rawId);
    if (id.isEmpty() || "air".equals(id)) return;
    ensureSoundControl();
    if (!settings.soundControl.mutedBlocks.contains(id)
        && settings.soundControl.mutedBlocks.size() < 256) {
      actions.checkpoint();
      settings.soundControl.mutedBlocks.add(id);
      settings.soundControl.muteBlocks = true;
      actions.changed();
      showNotification(tr("soundControl.block_added") + ": " + id);
    }
    menuFrameCache.clear();
  }

  private void toggleSoundBlockId(String rawId) {
    String id = Settings.SoundControl.normalizeBlockQuery(rawId);
    if (id.isEmpty()) return;
    ensureSoundControl();
    actions.checkpoint();
    if (settings.soundControl.mutedBlocks.contains(id)) {
      settings.soundControl.mutedBlocks.remove(id);
    } else if (settings.soundControl.mutedBlocks.size() < 256) {
      settings.soundControl.mutedBlocks.add(id);
      settings.soundControl.muteBlocks = true;
    }
    actions.changed();
    menuFrameCache.clear();
  }

  private void removeSoundBlockId(String rawId) {
    String id = Settings.SoundControl.normalizeBlockQuery(rawId);
    if (id.isEmpty() || settings.soundControl == null
        || settings.soundControl.mutedBlocks == null)
      return;
    if (settings.soundControl.mutedBlocks.contains(id)) {
      actions.checkpoint();
      settings.soundControl.mutedBlocks.remove(id);
      actions.changed();
      menuFrameCache.clear();
    }
  }

  private List<Map<String, Object>> soundMutedRows() {
    if (settings.soundControl == null || settings.soundControl.mutedBlocks == null
        || settings.soundControl.mutedBlocks.isEmpty())
      return Collections.emptyList();
    String lang = dev.nightclient.i18n.I18n.language(settings.language);
    List<Map<String, Object>> rows =
        new ArrayList<Map<String, Object>>(settings.soundControl.mutedBlocks.size());
    for (int i = 0; i < settings.soundControl.mutedBlocks.size(); i++) {
      String id = settings.soundControl.mutedBlocks.get(i);
      if (id == null || id.isEmpty()) continue;
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("id", id);
      String pretty = Settings.SoundControl.displayBlockName(id, lang);
      row.put("label", pretty.equalsIgnoreCase(id) ? id : (pretty + " (" + id + ")"));
      row.put("muted", Boolean.TRUE);
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> popularSoundRows() {
    String lang = dev.nightclient.i18n.I18n.language(settings.language);
    List<String> muted = settings.soundControl == null || settings.soundControl.mutedBlocks == null
        ? Collections.<String>emptyList()
        : settings.soundControl.mutedBlocks;
    List<Map<String, Object>> rows =
        new ArrayList<Map<String, Object>>(Settings.SoundControl.POPULAR_BLOCKS.size());
    for (int i = 0; i < Settings.SoundControl.POPULAR_BLOCKS.size(); i++) {
      String id = Settings.SoundControl.POPULAR_BLOCKS.get(i);
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("id", id);
      row.put("label", Settings.SoundControl.displayBlockName(id, lang));
      row.put("muted", Boolean.valueOf(muted.contains(id)));
      rows.add(row);
    }
    return rows;
  }

  private void closeAccountManager() {
    standaloneAccount = false;
    actions.clearAccountError();
    ui.put("accountManager", "");
    ui.put("accountDraftId", "");
    ui.put("accountDraftName", "");
    ui.put("accountDraftNote", "");
    ui.put("accountDraftSkin", "");
    focused = null;
    menuFrameCache.clear();
  }

  private void startAccountDraft(dev.nightclient.config.SavedAccount account) {
    actions.clearAccountError();
    ui.put("accountManager", account == null ? "create" : "edit");
    ui.put("accountDraftId", account == null ? "" : account.id);
    String seed = account != null ? account.name : "";
    if (seed.isEmpty()) {
      String search = String.valueOf(ui.get("accountSearch")).trim();
      if (dev.nightclient.config.SavedAccount.isValidNickname(search)) seed = search;
    }
    ui.put("accountDraftName", seed);
    ui.put("accountDraftNote", account == null ? "" : account.note);
    ui.put("accountDraftSkin", account == null ? "" : account.skinName);
    focused = null;
    menuFrameCache.clear();
  }

  private void applyAccountDraft() {
    String id = String.valueOf(ui.get("accountDraftId"));
    String name = String.valueOf(ui.get("accountDraftName"));
    String note = String.valueOf(ui.get("accountDraftNote"));
    String skin = String.valueOf(ui.get("accountDraftSkin"));
    String err = actions.saveAccountDraft(id, name, note, skin);
    if (err != null && !err.isEmpty()) {
      showNotification(tr(err));
      return;
    }
    ui.put("accountManager", "list");
    ui.put("accountDraftId", "");
    String clean = dev.nightclient.config.SavedAccount.normalizeNickname(name);
    if (clean != null) {
      for (dev.nightclient.config.SavedAccount acc : actions.savedAccounts()) {
        if (acc.name.equalsIgnoreCase(clean)) {
          ui.put("accountSelectedId", acc.id);
          break;
        }
      }
    }
    focused = null;
    menuFrameCache.clear();
  }

  private void switchSavedAccountUi(String id) {
    if (id == null || id.isEmpty()) return;
    String err = actions.switchSavedAccount(id);
    if (err != null && !err.isEmpty()) {
      showNotification(tr(err));
      return;
    }
    ui.put("accountSelectedId", id);
    showNotification(tr("accounts.status.switched"));
    if (standaloneAccount && settings.accountSwitcher != null
        && settings.accountSwitcher.closeOnSwitch)
      closeAccountManager();
  }

  private void performQuickSwitchAccount() {
    String raw = String.valueOf(ui.get("accountSearch")).trim();
    if (raw.isEmpty()) return;
    String err = actions.quickSwitchAccount(raw);
    if (err != null && !err.isEmpty()) {
      showNotification(tr(err));
      return;
    }
    ui.put("accountSearch", "");
    syncSelectedAccountToActive();
    showNotification(tr("accounts.status.switched"));
    if (standaloneAccount && settings.accountSwitcher != null
        && settings.accountSwitcher.closeOnSwitch)
      closeAccountManager();
  }

  private dev.nightclient.config.SavedAccount savedAccountById(String id) {
    if (id == null || id.isEmpty()) return null;
    for (dev.nightclient.config.SavedAccount acc : actions.savedAccounts())
      if (id.equals(acc.id)) return acc;
    return null;
  }

  private dev.nightclient.config.SavedAccount selectedAccount() {
    String id = String.valueOf(ui.get("accountSelectedId"));
    return id == null || id.isEmpty() ? null : savedAccountById(id);
  }

  private static String safeNick(String raw) {
    return raw == null || raw.trim().isEmpty() ? "Player" : raw.trim();
  }

  private String maskAccountDisplay(String raw) {
    String name = safeNick(raw);
    if (settings.accountSwitcher != null && settings.accountSwitcher.hideAltsInProtect
        && settings.protect != null && settings.protect.enabled && settings.protect.hideNickname) {
      return dev.nightclient.config.DisplayProtection.maskNickname(name, name, settings.protect);
    }
    return name;
  }

  private List<Map<String, Object>> accountRows() {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    List<dev.nightclient.config.SavedAccount> saved = actions.savedAccounts();
    if (saved == null || saved.isEmpty()) return rows;
    String query = String.valueOf(ui.get("accountSearch")).trim().toLowerCase(Locale.ROOT);
    String activeName = safeNick(actions.activeAccountName());
    boolean launcherActive = actions.isLauncherAccountActive();
    String selectedId = String.valueOf(ui.get("accountSelectedId"));
    for (dev.nightclient.config.SavedAccount acc : saved) {
      if (acc == null) continue;
      if (!query.isEmpty()
          && !acc.name.toLowerCase(Locale.ROOT).contains(query)
          && !(acc.note != null && acc.note.toLowerCase(Locale.ROOT).contains(query)))
        continue;
      boolean active = !launcherActive && acc.name.equalsIgnoreCase(activeName);
      boolean hasNote = acc.note != null && !acc.note.isEmpty();
      String uuidShort = dev.nightclient.config.SavedAccount.shortUuid(acc.offlineUuid());
      Map<String, Object> row = new HashMap<String, Object>();
      row.put("id", acc.id);
      row.put("name", acc.name);
      row.put("displayName", maskAccountDisplay(acc.name));
      row.put("note", hasNote ? acc.note : "");
      row.put("hasNote", hasNote);
      row.put("meta", hasNote ? acc.note + " · " + uuidShort : uuidShort);
      row.put("skinSource", acc.effectiveSkinName());
      row.put("uuidShort", uuidShort);
      row.put("pinned", acc.pinned);
      row.put("active", active);
      row.put("selected", acc.id.equals(selectedId));
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> markerRows() {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    if (settings.waypoints.points == null) return rows;
    String currentWorld = Waypoint.normalizeWorld(actions.currentWorldId());
    boolean showAll = "all".equals(String.valueOf(ui.get("waypointScope")));
    for (Waypoint point : settings.waypoints.points) {
      if (point == null) continue;
      boolean sameWorld = point.matchesWorld(currentWorld);
      if (!showAll && !currentWorld.isEmpty() && !sameWorld) continue;
      String worldLabel = point.displayWorld();
      boolean otherWorld = !currentWorld.isEmpty() && !sameWorld;
      String dimShort = point.dimension == null ? "" : point.dimension.replace("minecraft:", "");
      String meta = showAll && !worldLabel.isEmpty()
          ? point.coordinates() + " · " + worldLabel + (dimShort.isEmpty() ? "" : " · " + dimShort)
          : point.coordinates() + " · " + point.dimension;
      Map<String, Object> row = new HashMap<String, Object>();
      row.put("id", point.id);
      row.put("name",
          point.death && (point.label == null || point.label.trim().isEmpty())
              ? tr("waypoint.death")
              : point.displayName());
      row.put("label", point.label);
      row.put("coordinates", point.coordinates());
      row.put("dimension", point.dimension);
      row.put("world", point.world == null ? "" : point.world);
      row.put("worldLabel", worldLabel);
      row.put("otherWorld", otherWorld);
      row.put("meta", meta);
      row.put("color", point.color);
      row.put("icon", point.icon);
      row.put("visible", point.visible);
      row.put("death", point.death);
      rows.add(row);
    }
    return rows;
  }

  private Waypoint marker(String id) {
    return id == null || id.isEmpty() ? null : settings.waypoints.find(id);
  }

  private List<Map<String, Object>> officialCapeRows() {
    String filter = ui.get("cosmeticsCapeFilter") == null
        ? "all"
        : String.valueOf(ui.get("cosmeticsCapeFilter"));
    String query = ui.get("cosmeticsCapeQuery") == null
        ? ""
        : String.valueOf(ui.get("cosmeticsCapeQuery"));
    List<dev.nightclient.cosmetics.OfficialCapes.Cape> matched =
        dev.nightclient.cosmetics.OfficialCapes.filter(filter, query, settings.language);
    String activeId = settings.cosmetics != null && "official".equals(settings.cosmetics.capeMode)
        ? settings.cosmetics.officialCape
        : "";
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(matched.size());
    for (dev.nightclient.cosmetics.OfficialCapes.Cape cape : matched) {
      Map<String, Object> row = new HashMap<String, Object>();
      row.put("id", cape.id);
      row.put("name", cape.name(settings.language));
      row.put("category", cape.category);
      row.put("year", Integer.toString(cape.year));
      row.put("primary", cape.primaryHex);
      row.put("secondary", cape.secondaryHex);
      row.put("accent", cape.accentHex);
      row.put("emblem", cape.emblem);
      row.put("selected", cape.id.equals(activeId));
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> cosmeticMaterialRows() {
    List<dev.nightclient.cosmetics.CosmeticModels.Material> list =
        dev.nightclient.cosmetics.CosmeticModels.materials();
    String activeId = settings.cosmetics != null
        ? dev.nightclient.cosmetics.CosmeticModels.resolveMaterial(
            settings.cosmetics.weaponMaterial).id
        : dev.nightclient.cosmetics.CosmeticModels.DEFAULT_MATERIAL;
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(list.size());
    for (dev.nightclient.cosmetics.CosmeticModels.Material mat : list) {
      Map<String, Object> row = new HashMap<String, Object>();
      row.put("id", mat.id);
      row.put("name", mat.name(settings.language));
      row.put("primary", mat.primaryHex);
      row.put("secondary", mat.secondaryHex);
      row.put("selected", mat.id.equals(activeId));
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> cosmeticModelRows(boolean armor) {
    List<dev.nightclient.cosmetics.CosmeticModels.Entry> list = armor
        ? dev.nightclient.cosmetics.CosmeticModels.armors()
        : dev.nightclient.cosmetics.CosmeticModels.weapons();
    String activeId = "";
    if (settings.cosmetics != null) {
      if (armor && "builtin".equals(settings.cosmetics.armorMode)) {
        activeId = settings.cosmetics.builtinArmor == null ? "" : settings.cosmetics.builtinArmor;
      } else if (!armor && "builtin".equals(settings.cosmetics.weaponMode)) {
        activeId = settings.cosmetics.builtinWeapon == null ? "" : settings.cosmetics.builtinWeapon;
      }
    }
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(list.size());
    for (dev.nightclient.cosmetics.CosmeticModels.Entry entry : list) {
      Map<String, Object> row = new HashMap<String, Object>();
      row.put("id", entry.id);
      row.put("previewSource", "model:" + entry.id);
      row.put("name", entry.name(settings.language));
      row.put("subtitle", entry.subtitle(settings.language));
      row.put("polys", entry.polyBadge());
      row.put("accent", Colors.hex(entry.defaultAccentColor));
      row.put("selected", entry.id.equals(activeId));
      rows.add(row);
    }
    return rows;
  }

  private static int parseHexColor(String hex, int fallback) {
    if (hex == null) return fallback;
    String s = hex.trim();
    if (s.startsWith("#")) s = s.substring(1);
    try {
      if (s.length() == 6) return 0xff000000 | Integer.parseInt(s, 16);
      if (s.length() == 8) return (int) Long.parseLong(s, 16);
    } catch (NumberFormatException ignored) {
    }
    return fallback;
  }

  private void showNotification(String message) {
    notification = message;
    notificationUntil = time + 4;
  }

  private void safe(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException failure) {
      showNotification(
          failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
    }
  }

  private void paintOverlay(Canvas canvas, int width, int height) {
    if (!colorBind.isEmpty()) {
      hits.clear();
      popups.clear();
      Map<String, Object> context = context(width, height);
      context.put("colorBind", colorBind);
      context.put("colorLabel", colorLabel);
      SkinDocument.Node popup = document.component("color-picker");
      if (popup == null)
        popup = dev.nightclient.skin.DefaultSkin.document().component("color-picker");
      if (popup != null) {
        SkinDocument owner = popup == document.component("color-picker")
            ? document
            : dev.nightclient.skin.DefaultSkin.document();
        if (owner == document)
          renderRoot(popup, canvas, 0, 0, width, height, context);
        else {
          SkinView modal = new SkinView(owner, settings, actions, ui, colorModels);
          modal.colorBind = colorBind;
          modal.colorLabel = colorLabel;
          modal.colorAnchor = colorAnchor;
          modal.renderRoot(popup, canvas, 0, 0, width, height, context);
          hits.addAll(modal.hits);
        }
      }
    }
    if (!tooltip.isEmpty() && colorBind.isEmpty() && dragged == null && pressedKey.isEmpty()) {
      if (!tooltipKey.equals(hoveredTooltipKey)) {
        hoveredTooltipKey = tooltipKey;
        tooltipSince = time;
      }
      SkinDocument.Node recipe = document.component("overlay.tooltip");
      Map<String, String> style =
          recipe == null ? Collections.<String, String>emptyMap() : document.styles(recipe, "");
      if (time - tooltipSince >= duration(style.get("--delay"), .3f))
        paintMessage("overlay.tooltip", "tooltip", tooltip, canvas, width, height);
    } else
      hoveredTooltipKey = "";
    if (time < notificationUntil && !notification.isEmpty())
      paintMessage("overlay.notification", "notification", notification, canvas, width, height);
  }

  private void paintMessage(
      String component, String key, String text, Canvas canvas, int width, int height) {
    SkinDocument.Node node = document.component(component);
    if (node == null) return;
    Map<String, String> style = document.styles(node, "");
    float font = number(style, "font-size", 12);
    Theme theme = themeResolver.theme(node, settings.theme, "");
    if (menuScope) font *= settings.menu.fontScale;
    boolean bold = "bold".equals(theme.fontWeight);
    Map<String, Object> data = new HashMap<String, Object>();
    if ("tooltip".equals(key)) {
      float margin = number(style, "--viewport-margin", 6), px = number(style, "--padding-x", 8),
            py = number(style, "--padding-y", 6),
            lineHeight = font * Math.max(1, number(style, "line-height", 1.35f));
      float maxWidth =
          Math.max(1, Math.min(number(style, "--max-width", 220), width - margin * 2 - px * 2));
      if (text.length() > 2048)
        text =
            text.substring(0,
                text.offsetByCodePoints(0, Math.min(2047, text.codePointCount(0, text.length()))))
            + "…";
      List<String> lines =
          wrapText(canvas, theme.fontFamily, text, maxWidth, font, bold, theme.fontTracking);
      float verticalSlack = Math.max(2, font * .24f);
      float horizontalSlack = Math.max(2, font * .2f);
      int maxLines = Math.max(1,
          (int) Math.floor(
              (height - margin * 2 - py * 2 - verticalSlack) / Math.max(1, lineHeight)));
      if (lines.size() > maxLines) {
        lines = new ArrayList<String>(lines.subList(0, maxLines));
        lines.set(maxLines - 1,
            fit(canvas, theme.fontFamily, lines.get(maxLines - 1) + "…", maxWidth, font, bold,
                theme.fontTracking));
      }
      float contentWidth = 0;
      StringBuilder wrapped = new StringBuilder();
      for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
        String line = lines.get(lineIndex);
        if (lineIndex > 0) wrapped.append('\n');
        wrapped.append(line);
        contentWidth = Math.max(contentWidth,
            canvas.textWidthFamily(theme.fontFamily, line, font, bold, theme.fontTracking));
      }
      float contentHeight = lines.size() * lineHeight + verticalSlack,
            outerWidth = contentWidth + horizontalSlack + px * 2,
            outerHeight = contentHeight + py * 2;
      float arrowW = number(style, "--arrow-width", 0f),
            arrowH = number(style, "--arrow-height", 0f);
      boolean hasArrow = arrowW > 0.5f && arrowH > 0.5f;
      float x, y;
      float arrowX;
      String arrowSide = "bottom";
      if (hasArrow) {
        float sideGap = number(style, "--side-offset", 4f);
        float anchorCx = (tooltipAnchor != null && tooltipAnchor.w > 0f)
            ? tooltipAnchor.x + tooltipAnchor.w * 0.5f
            : mouseX;
        float anchorTop = (tooltipAnchor != null && tooltipAnchor.h > 0f) ? tooltipAnchor.y : mouseY;
        float anchorBottom = (tooltipAnchor != null && tooltipAnchor.h > 0f)
            ? tooltipAnchor.y + tooltipAnchor.h
            : mouseY;
        float topY = anchorTop - sideGap - arrowH - outerHeight;
        float bottomY = anchorBottom + sideGap + arrowH;
        boolean placeAbove = topY >= margin
            || (bottomY + outerHeight > height - margin && anchorTop >= height - anchorBottom);
        arrowSide = placeAbove ? "bottom" : "top";
        x = clamp(anchorCx - outerWidth * 0.5f, margin, Math.max(margin, width - margin - outerWidth));
        y = clamp(placeAbove ? topY : bottomY, margin,
            Math.max(margin, height - margin - outerHeight));
        arrowX = anchorCx;
      } else {
        x = mouseX + number(style, "--offset-x", 10);
        y = mouseY + number(style, "--offset-y", 12);
        if (y + outerHeight > height - margin)
          y = mouseY - number(style, "--offset-y", 12) - outerHeight;
        x = clamp(x, margin, Math.max(margin, width - margin - outerWidth));
        y = clamp(y, margin, Math.max(margin, height - margin - outerHeight));
        arrowX = x + outerWidth * 0.5f;
      }
      data.put("x", x);
      data.put("y", y);
      data.put("arrowX", arrowX);
      data.put("arrowSide", arrowSide);
      data.put("text", wrapped.toString());
      data.put("width", contentWidth + horizontalSlack);
      data.put("height", contentHeight);
      data.put("paddingX", px);
      data.put("paddingY", py);
    } else {
      data.put("text", text);
      data.put(
          "width", canvas.textWidthFamily(theme.fontFamily, text, font, bold, theme.fontTracking));
      data.put("height", font);
    }
    Map<String, Object> values = context(width, height);
    values.put(key, data);
    renderRoot(node, canvas, 0, 0, width, height, values, false);
  }

  private void paintTooltipArrow(
      Canvas canvas, Frame frame, Theme theme, int background, float rad) {
    if (frame == null || frame.node == null || frame.context == null) return;
    Object tipObj = frame.context.get("tooltip");
    if (!(tipObj instanceof Map)) return;
    Map<String, String> rawStyle = document.styles(frame.node, "");
    float arrowW = number(rawStyle, "--arrow-width", 0f);
    float arrowH = number(rawStyle, "--arrow-height", 0f);
    if (arrowW <= 0.5f || arrowH <= 0.5f) return;
    Map<?, ?> tip = (Map<?, ?>) tipObj;
    String side = String.valueOf(tip.get("arrowSide"));
    boolean arrowBottom = !"top".equals(side);
    Object rawAx = tip.get("arrowX");
    float targetAx = rawAx instanceof Number
        ? ((Number) rawAx).floatValue()
        : frame.x + frame.w * 0.5f;
    float halfW = arrowW * 0.5f;
    float cornerR = Math.min(Math.max(0f, rad), Math.min(frame.w, frame.h) * 0.5f);
    float safeInset = Math.min(frame.w * 0.45f, Math.max(cornerR + 1f, halfW + 1.5f));
    float ax = clamp(targetAx, frame.x + safeInset, frame.x + frame.w - safeInset);
    float bw = Math.max(0f, theme.borderWidth);
    float overlap = Math.min(2.2f, Math.max(0.75f, bw + 0.65f));
    float opacity = clamp(theme.tintOpacity >= 0 ? theme.tintOpacity : theme.backgroundOpacity, 0f, 1f);
    int fillCol = Colors.alpha(background, opacity * clamp(theme.surfaceOpacity, 0f, 1f));
    int borderCol = Colors.alpha(theme.border, opacity);
    if (arrowBottom) {
      float edgeY = frame.y + frame.h;
      if (bw > 0f && (borderCol >>> 24) != 0) {
        float bHalf = halfW + bw * 1.15f;
        float bTipY = edgeY + arrowH + bw * 0.95f;
        canvas.fillPolygon(ax, edgeY + arrowH * 0.35f,
            new float[] {ax - bHalf, edgeY - 0.15f, ax + bHalf, edgeY - 0.15f, ax, bTipY},
            borderCol);
      }
      if ((fillCol >>> 24) != 0) {
        canvas.fillPolygon(ax, edgeY + arrowH * 0.25f,
            new float[] {ax - halfW, edgeY - overlap, ax + halfW, edgeY - overlap, ax, edgeY + arrowH},
            fillCol);
      }
    } else {
      float edgeY = frame.y;
      if (bw > 0f && (borderCol >>> 24) != 0) {
        float bHalf = halfW + bw * 1.15f;
        float bTipY = edgeY - arrowH - bw * 0.95f;
        canvas.fillPolygon(ax, edgeY - arrowH * 0.35f,
            new float[] {ax - bHalf, edgeY + 0.15f, ax + bHalf, edgeY + 0.15f, ax, bTipY},
            borderCol);
      }
      if ((fillCol >>> 24) != 0) {
        canvas.fillPolygon(ax, edgeY - arrowH * 0.25f,
            new float[] {ax - halfW, edgeY + overlap, ax + halfW, edgeY + overlap, ax, edgeY - arrowH},
            fillCol);
      }
    }
  }

  private static List<String> wrapText(Canvas canvas, String family, String text, float width,
      float size, boolean bold, float tracking) {
    List<String> lines = new ArrayList<String>();
    for (String paragraph : text.split("\n", -1)) {
      String line = "";
      for (String word : SPACE.split(paragraph.trim())) {
        if (word.isEmpty()) continue;
        String joined = line.isEmpty() ? word : line + " " + word;
        if (canvas.textWidthFamily(family, joined, size, bold, tracking) <= width) {
          line = joined;
          continue;
        }
        if (!line.isEmpty()) {
          lines.add(line);
          line = "";
        }
        for (int at = 0; at < word.length();) {
          int next = word.offsetByCodePoints(at, 1);
          String point = word.substring(at, next);
          if (!line.isEmpty()
              && canvas.textWidthFamily(family, line + point, size, bold, tracking) > width) {
            lines.add(line);
            line = "";
          }
          line += point;
          at = next;
        }
      }
      lines.add(line);
    }
    return lines;
  }

  private boolean isEditingTargetCrosshair() {
    return "target".equals(ui.get("crosshairLayer"))
        && settings.crosshair.targetShapeEnabled
        && "custom".equals(settings.crosshair.targetShape);
  }

  private dev.nightclient.config.CrosshairPattern activeCrosshairPattern() {
    if (isEditingTargetCrosshair()) {
      if (settings.crosshair.targetPattern == null)
        settings.crosshair.targetPattern =
            dev.nightclient.config.CrosshairPattern.defaultTargetPattern();
      return settings.crosshair.targetPattern;
    }
    if (settings.crosshair.pattern == null)
      settings.crosshair.pattern = new dev.nightclient.config.CrosshairPattern();
    return settings.crosshair.pattern;
  }

  private void paintPixelGrid(Frame frame, Canvas canvas, Theme theme) {
    boolean editingTarget = isEditingTargetCrosshair();
    dev.nightclient.config.CrosshairPattern pat = activeCrosshairPattern();
    int size = pat.size;
    float cell = Math.max(1, Math.min(frame.w, frame.h) / size),
          left = frame.x + (frame.w - cell * size) / 2, top = frame.y + (frame.h - cell * size) / 2;
    for (int y = 0; y < size; y++)
      for (int x = 0; x < size; x++) {
        canvas.rect(left + x * cell, top + y * cell, cell, cell,
            (x + y) % 2 == 0 ? Colors.mix(theme.background, theme.text, .06f)
                             : Colors.mix(theme.background, theme.text, .1f));
        int color = pat.pixel(x, y);
        if ((color >>> 24) != 0) {
          int drawColor;
          if (editingTarget && settings.crosshair.targetHighlight) {
            drawColor = Colors.alpha(settings.crosshair.targetColor, (color >>> 24) / 255f);
          } else if ("pixels".equals(settings.crosshair.colorMode)) {
            drawColor = color;
          } else {
            drawColor = Colors.alpha(
                "custom".equals(settings.crosshair.colorMode) ? settings.crosshair.color : theme.text,
                (color >>> 24) / 255f);
          }
          canvas.rect(left + x * cell, top + y * cell, cell, cell, drawColor);
        }
      }
  }

  private void paintPixelStroke(Hit hit, float screenX, float screenY) {
    Frame frame = hit.frame;
    boolean editingTarget = isEditingTargetCrosshair();
    dev.nightclient.config.CrosshairPattern pat = activeCrosshairPattern();
    int size = pat.size;
    float cell = Math.max(1, Math.min(frame.w, frame.h) / size),
          left = frame.x + (frame.w - cell * size) / 2, top = frame.y + (frame.h - cell * size) / 2;
    int x = (int) Math.floor((hit.transform.inverseX(screenX) - left) / cell),
        y = (int) Math.floor((hit.transform.inverseY(screenY) - top) / cell);
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    if (pixelLastX < 0) {
      pixelLastX = x;
      pixelLastY = y;
    }
    int ax = pixelLastX, ay = pixelLastY, dx = Math.abs(x - ax), dy = -Math.abs(y - ay),
        sx = ax < x ? 1 : -1, sy = ay < y ? 1 : -1, error = dx + dy,
        color = pixelErase
        ? 0
        : ("pixels".equals(settings.crosshair.colorMode) ? ((Integer) ui.get("brushColor"))
                                                           : 0xffffffff);
    while (true) {
      pat.paint(ax, ay, color, settings.crosshair.mirrorX, settings.crosshair.mirrorY);
      if (ax == x && ay == y) break;
      int twice = error * 2;
      if (twice >= dy) {
        error += dy;
        ax += sx;
      }
      if (twice <= dx) {
        error += dx;
        ay += sy;
      }
    }
    pixelLastX = x;
    pixelLastY = y;
    if (!editingTarget) settings.crosshair.mode = "pixels";
    actions.changed();
  }

  private static String fit(Canvas canvas, String family, String text, float width, float size,
      boolean bold, float tracking) {
    if (text == null || width <= 0) return "";
    if (canvas.textWidthFamily(family, text, size, bold, tracking) <= width) return text;
    String suffix = "…";
    while (!text.isEmpty()
        && canvas.textWidthFamily(family, text + suffix, size, bold, tracking) > width)
      text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
    return text.isEmpty() && canvas.textWidthFamily(family, suffix, size, bold, tracking) > width
        ? ""
        : text + suffix;
  }

  private static Theme interpolate(Theme from, Theme to, float ratio) {
    if (ratio <= 0 || from == to) return from;
    if (ratio >= 1) return to;
    Theme t = to.copy();
    t.background = Colors.mix(from.background, to.background, ratio);
    t.surface = Colors.mix(from.surface, to.surface, ratio);
    t.raised = Colors.mix(from.raised, to.raised, ratio);
    t.border = Colors.mix(from.border, to.border, ratio);
    t.accent = Colors.mix(from.accent, to.accent, ratio);
    t.text = Colors.mix(from.text, to.text, ratio);
    t.radius = from.radius + (to.radius - from.radius) * ratio;
    return t;
  }

  private void paintNumericText(Frame frame, Motion animation, Canvas canvas, String family,
      String label, float x, float y, float font, int color, boolean bold, float tracking,
      float duration, String easing, String mode, boolean snap) {
    if (animation.numericTo == null || snap || duration <= 0) {
      animation.numericFrom = animation.numericTo = label;
      animation.numericBegan = time;
    } else if (!label.equals(animation.numericTo)) {
      String previous = animation.numericTo;
      if (time - animation.numericBegan < duration * .5 && animation.numericFrom != null)
        previous = animation.numericFrom;
      animation.numericFrom = compatibleDigits(previous, label) ? previous : label;
      animation.numericTo = label;
      animation.numericBegan = time;
    }
    float progress = snap
        ? 1
        : MotionTrack.ease(
              clamp((float) ((time - animation.numericBegan) / Math.max(.001f, duration)), 0, 1),
              easing);
    String previous = animation.numericFrom;
    if (progress >= 1 || previous == null || previous.equals(label)) {
      animation.numericFrom = label;
      canvas.textFamily(family, label, x, y, font, color, bold, tracking);
      return;
    }
    hudFrameDynamic = true;
    float cursor = x, direction = label.compareTo(previous) >= 0 ? 1 : -1;
    float travel = "fade".equals(mode) ? 0f : "slide".equals(mode) ? font * .38f : font;
    canvas.pushClip(frame.x, frame.y, frame.w, frame.h);
    try {
      for (int i = 0; i < label.length(); i++) {
        String next = label.substring(i, i + 1), before = previous.substring(i, i + 1);
        if (next.equals(before))
          canvas.textFamily(family, next, cursor, y, font, color, bold, tracking);
        else {
          canvas.textFamily(family, before, cursor, y - direction * travel * progress, font,
              Colors.alpha(color, 1 - progress), bold, tracking);
          canvas.textFamily(family, next, cursor, y + direction * travel * (1 - progress), font,
              Colors.alpha(color, progress), bold, tracking);
        }
        cursor += canvas.textWidthFamily(family, next, font, bold, 0) + tracking;
      }
    } finally {
      canvas.popClip();
    }
  }

  private static boolean compatibleDigits(String previous, String next) {
    if (previous == null || previous.length() != next.length()) return false;
    for (int i = 0; i < next.length(); i++) {
      char a = previous.charAt(i), b = next.charAt(i);
      if (Character.isSurrogate(a) || Character.isSurrogate(b)) return false;
      if (a != b && !(a >= '0' && a <= '9' && b >= '0' && b <= '9')) return false;
    }
    return true;
  }

  private static final class Motion {
    String numericFrom, numericTo;
    double numericBegan;
    float previousLayoutWidth = Float.NaN, previousLayoutHeight = Float.NaN;
    boolean slotOpening;
    List<Frame> lastFlowChildren;
    MotionTrack opacity, layoutWidth;
    final String scope;
    final double created;
    double lastSeen, retiredAt = Double.NaN, marqueeSince;
    String marqueeSignature = "";
    List<MarqueeGlyph> marqueeGlyphs = Collections.emptyList();
    Frame lastFrame;
    Bounds lastClip, lastChildClip;
    Transform lastParentTransform = Transform.IDENTITY;
    private ColorMotion borderColor, accentColor, backgroundColor, foregroundColor, iconColor;
    private Map<String, ColorMotion> extraColors;
    final MotionTrack hover = new MotionTrack(0), press = new MotionTrack(0),
                      value = new MotionTrack(), x = new MotionTrack(), y = new MotionTrack(),
                      width = new MotionTrack(), height = new MotionTrack(),
                      layoutHeight = new MotionTrack();

    Motion(double created, String scope) {
      this.scope = scope;
      this.created = created;
      lastSeen = created;
    }

    int color(String key, int next, double time, float duration, String easing, boolean reduced) {
      ColorMotion color;
      if ("border".equals(key)) {
        color = borderColor;
        if (color == null) borderColor = color = new ColorMotion(next);
      } else if ("accent".equals(key)) {
        color = accentColor;
        if (color == null) accentColor = color = new ColorMotion(next);
      } else if ("background".equals(key)) {
        color = backgroundColor;
        if (color == null) backgroundColor = color = new ColorMotion(next);
      } else if ("foreground".equals(key)) {
        color = foregroundColor;
        if (color == null) foregroundColor = color = new ColorMotion(next);
      } else if ("icon".equals(key)) {
        color = iconColor;
        if (color == null) iconColor = color = new ColorMotion(next);
      } else {
        if (extraColors == null) extraColors = new HashMap<String, ColorMotion>(4);
        color = extraColors.get(key);
        if (color == null) {
          color = new ColorMotion(next);
          extraColors.put(key, color);
        }
      }
      return color.to(next, time, duration, easing, reduced);
    }

    private static boolean colorActive(ColorMotion color, double time) {
      return color != null && color.duration > 0 && color.start != color.target
          && time < color.began + color.duration;
    }

    boolean colorInFlight(double time) {
      if (colorActive(borderColor, time) || colorActive(accentColor, time)
          || colorActive(backgroundColor, time) || colorActive(foregroundColor, time)
          || colorActive(iconColor, time))
        return true;
      if (extraColors != null)
        for (ColorMotion color : extraColors.values())
          if (colorActive(color, time)) return true;
      return false;
    }
  }

  private static final class ColorMotion {
    int start, target;
    double began;
    float duration;
    String easing = "linear";

    ColorMotion(int color) {
      start = target = color;
    }

    int at(double time) {
      if (duration <= 0 || start == target || time >= began + duration) return target;
      if (time <= began) return start;
      int from = (start >>> 24) == 0 && (target >>> 24) != 0 ? (target & 0x00ffffff) : start;
      int to = (target >>> 24) == 0 && (start >>> 24) != 0 ? (start & 0x00ffffff) : target;
      return Colors.mix(from, to,
          MotionTrack.ease(clamp((float) ((time - began) / duration), 0, 1), easing));
    }

    int to(int next, double time, float duration, String easing, boolean reduced) {
      if (reduced || duration <= 0) {
        start = target = next;
        this.duration = 0;
        return next;
      }
      if (target != next) {
        start = at(time);
        target = next;
        began = time;
        this.duration = duration;
        this.easing = easing;
      }
      return at(time);
    }
  }

  private static final class ValueMotion {
    final MotionTrack track = new MotionTrack();
    double lastSeen;
  }

  private static final class MarqueeGlyph {
    final String text;
    final float x, width;

    MarqueeGlyph(String text, float x, float width) {
      this.text = text;
      this.x = x;
      this.width = width;
    }
  }

  private static final class Masonry {
    final int columns;
    final Map<String, Integer> assigned = new HashMap<String, Integer>();
    Set<String> keys = Collections.emptySet();

    Masonry(int columns) {
      this.columns = columns;
    }
  }

  private static final class RepeatMemory {
    final Map<String, RepeatRow> rows = new LinkedHashMap<String, RepeatRow>();
    String source = "", alias = "item", keyTemplate;
    Map<String, Object> context = Collections.emptyMap();
    float exitDuration;
  }

  private static final class RepeatRow {
    final String key;
    final Map<String, Object> context;
    final int index;
    double retiredAt = Double.NaN;

    RepeatRow(String key, Map<String, Object> context, int index) {
      this.key = key;
      this.context = SkinBindings.childContext(context);
      this.index = index;
    }
  }

  private static final class Transform {
    static final Transform IDENTITY = new Transform(0, 0, 1);
    final float x, y, scale, sx, sy;

    Transform(float x, float y, float scale) {
      this(x, y, scale, scale);
    }

    Transform(float x, float y, float sx, float sy) {
      this.x = x;
      this.y = y;
      this.sx = sx;
      this.sy = sy;
      this.scale = Math.min(sx, sy);
    }

    Transform(float x, float y, float sx, float sy, float originX, float originY) {
      this(x - originX * sx, y - originY * sy, sx, sy);
    }

    float px(float v) {
      return x + v * sx;
    }

    float py(float v) {
      return y + v * sy;
    }

    float inverseX(float v) {
      return (v - x) / sx;
    }

    float inverseY(float v) {
      return (v - y) / sy;
    }

    Transform compose(Transform child) {
      return new Transform(px(child.x), py(child.y), sx * child.sx, sy * child.sy);
    }

    Bounds bounds(Bounds bounds) {
      return new Bounds(px(bounds.x), py(bounds.y), bounds.w * sx, bounds.h * sy);
    }
  }

  private static final class PoseCanvas extends OffsetCanvas {
    final Transform pose;
    final float alpha;

    PoseCanvas(Canvas canvas, Transform pose, float alpha) {
      super(canvas, 0, 0);
      this.pose = pose;
      this.alpha = alpha;
    }

    private int tint(int color) {
      return Colors.alpha(color, alpha);
    }

    public void rect(float x, float y, float w, float h, int c) {
      delegate.rect(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, tint(c));
    }

    public void invertRect(float x, float y, float w, float h, float a) {
      delegate.invertRect(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, a * alpha);
    }

    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      delegate.roundedRect(
          pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, r * pose.scale, tint(c));
    }

    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      delegate.strokeRoundedRect(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, r * pose.scale,
          t * pose.scale, tint(c));
    }

    public void roundedRectCorners(
        float x, float y, float w, float h, float tl, float tr, float bl, float br, int c) {
      delegate.roundedRectCorners(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, tl * pose.scale,
          tr * pose.scale, bl * pose.scale, br * pose.scale, tint(c));
    }

    public void strokeRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
        float bl, float br, float t, int c) {
      delegate.strokeRoundedRectCorners(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy,
          tl * pose.scale, tr * pose.scale, bl * pose.scale, br * pose.scale, t * pose.scale,
          tint(c));
    }

    public void gradientRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
        float bl, float br, int ctl, int ctr, int cbl, int cbr) {
      delegate.gradientRoundedRectCorners(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy,
          tl * pose.scale, tr * pose.scale, bl * pose.scale, br * pose.scale, tint(ctl), tint(ctr),
          tint(cbl), tint(cbr));
    }

    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      delegate.gradientRect(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, tint(a), tint(b));
    }

    public void gradientRoundedRect(float x, float y, float w, float h, float r, int a, int b) {
      delegate.gradientRoundedRect(
          pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, r * pose.scale, tint(a), tint(b));
    }

    public void circle(float x, float y, float r, int c) {
      delegate.circle(pose.px(x), pose.py(y), r * pose.scale, tint(c));
    }

    public void line(float x, float y, float xx, float yy, float t, int c) {
      delegate.line(pose.px(x), pose.py(y), pose.px(xx), pose.py(yy), t * pose.scale, tint(c));
    }

    public void text(String value, float x, float y, float font, int c, boolean bold) {
      delegate.text(value, pose.px(x), pose.py(y), font * pose.scale, tint(c), bold);
    }

    public float textWidth(String value, float font, boolean bold) {
      return delegate.textWidth(value, font * pose.scale, bold) / pose.scale;
    }

    public void textTracked(
        String value, float x, float y, float font, int c, boolean bold, float tracking) {
      delegate.textTracked(
          value, pose.px(x), pose.py(y), font * pose.scale, tint(c), bold, tracking * pose.scale);
    }

    public float textWidthTracked(String value, float font, boolean bold, float tracking) {
      return delegate.textWidthTracked(value, font * pose.scale, bold, tracking * pose.scale)
          / pose.scale;
    }

    public void textFamily(String family, String value, float x, float y, float font, int c,
        boolean bold, float tracking) {
      delegate.textFamily(family, value, pose.px(x), pose.py(y), font * pose.scale, tint(c), bold,
          tracking * pose.scale);
    }

    public float textWidthFamily(
        String family, String value, float font, boolean bold, float tracking) {
      return delegate.textWidthFamily(family, value, font * pose.scale, bold, tracking * pose.scale)
          / pose.scale;
    }

    public void hungerIcon(float x, float y, float size, int fill) {
      delegate.hungerIcon(pose.px(x), pose.py(y), size * pose.scale, fill);
    }

    public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
      return delegate.vanillaCrosshair(pose.px(x), pose.py(y), size * pose.scale, opacity * alpha);
    }

    public void viewmodelOutline(String hand, int color) {
      delegate.viewmodelOutline(hand, tint(color));
    }

    public void gameSprite(String id, float x, float y, float w, float h, float u, float v,
        float rw, float rh, float sw, float sh, int c) {
      delegate.gameSprite(
          id, pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, u, v, rw, rh, sw, sh, tint(c));
    }

    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {
      delegate.fillTriangle(
          pose.px(ax), pose.py(ay), pose.px(bx), pose.py(by), pose.px(cx), pose.py(cy), tint(c));
    }

    public void fillPolygon(float cx, float cy, float[] xy, int c) {
      if (xy == null || xy.length < 6) return;
      float[] out = new float[xy.length];
      for (int i = 0; i + 1 < xy.length; i += 2) {
        out[i] = pose.px(xy[i]);
        out[i + 1] = pose.py(xy[i + 1]);
      }
      delegate.fillPolygon(pose.px(cx), pose.py(cy), out, tint(c));
    }

    public void gameText(String value, float x, float y, float scale, int c, boolean shadow) {
      delegate.gameText(value, pose.px(x), pose.py(y), scale * pose.scale, tint(c), shadow);
    }

    public float gameTextWidth(String value, float scale) {
      return delegate.gameTextWidth(value, scale * pose.scale) / pose.scale;
    }

    public void pushClip(float x, float y, float w, float h) {
      delegate.pushClip(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy);
    }

    public void popClip() {
      delegate.popClip();
    }

    public void item(String id, float x, float y, float size, float a) {
      delegate.item(id, pose.px(x), pose.py(y), size * pose.scale, a * alpha);
    }

    public void inventoryItem(InventorySlot slot, float x, float y, float size, float a) {
      delegate.inventoryItem(slot, pose.px(x), pose.py(y), size * pose.scale, a * alpha);
    }

    public boolean entityHead(
        dev.nightclient.api.TargetView target, float x, float y, float size, float a) {
      return delegate.entityHead(target, pose.px(x), pose.py(y), size * pose.scale, a * alpha);
    }

    public boolean skinHead(String skinTextureId, float x, float y, float size, float radius,
        float a, int tintRgb) {
      return delegate.skinHead(skinTextureId, pose.px(x), pose.py(y), size * pose.scale,
          radius * pose.scale, a * alpha, tintRgb);
    }

    public boolean textureRegion(String textureId, float x, float y, float w, float h, float radius,
        float u0, float v0, float u1, float v1, float a, int tintRgb) {
      return delegate.textureRegion(textureId, pose.px(x), pose.py(y), w * pose.sx, h * pose.sy,
          radius * pose.scale, u0, v0, u1, v1, a * alpha, tintRgb);
    }

    public void mapQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float x, float y, float w, float h, float radius, float uCenter, float vCenter,
        float uHalfSpan, float vHalfSpan, float angleRad, boolean nearest, int argb) {
      delegate.mapQuad(cacheKey, revision, pixels, texWidth, texHeight, pose.px(x), pose.py(y),
          w * pose.sx, h * pose.sy, radius < 0 ? radius : radius * pose.scale, uCenter, vCenter,
          uHalfSpan, vHalfSpan, angleRad, nearest, tint(argb));
    }

    public void playerPreview(dev.nightclient.api.PlayerSkin skin, float x, float y, float w,
        float h, float yaw, float pitch, float zoom, float time, float wind, float yawVelocity,
        float a) {
      delegate.playerPreview(skin, pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, yaw, pitch,
          zoom, time, wind, yawVelocity, a * alpha);
    }

    public void capePreview(String cacheKey, dev.nightclient.api.PlayerSkin skin, float x, float y,
        float w, float h, float a) {
      delegate.capePreview(
          cacheKey, skin, pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, a * alpha);
    }

    public boolean supportsIconAtlas() {
      return delegate.supportsIconAtlas();
    }

    public float iconPixelDensity() {
      return delegate.iconPixelDensity();
    }

    public void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float x, float y, float w, float h, int c) {
      delegate.iconQuad(cacheKey, revision, pixels, texWidth, texHeight, pose.px(x), pose.py(y),
          w * pose.sx, h * pose.sy, tint(c));
    }

    public void backdrop(float x, float y, float w, float h, float r, float blur, int q, float a) {
      delegate.backdrop(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, r * pose.scale,
          blur, q, a * alpha);
    }

    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
      delegate.texture(id, pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, r * pose.scale,
          tile * pose.scale, tint(c));
    }

    public void textureCorners(String id, float x, float y, float w, float h, float tl, float tr,
        float bl, float br, float tile, int c) {
      delegate.textureCorners(id, pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, tl * pose.scale,
          tr * pose.scale, bl * pose.scale, br * pose.scale, tile * pose.scale, tint(c));
    }

    public void backdropCorners(float x, float y, float w, float h, float tl, float tr, float bl,
        float br, float blur, int q, float a) {
      delegate.backdropCorners(pose.px(x), pose.py(y), w * pose.sx, h * pose.sy, tl * pose.scale,
          tr * pose.scale, bl * pose.scale, br * pose.scale, blur, q, a * alpha);
    }
  }

  private static String attr(Frame frame, String key, String fallback) {
    String value = frame.attributes.get(key);
    return value == null ? fallback : value;
  }

  private static float numericAttr(Frame frame, String key, float fallback) {
    return dimension(frame.attributes.get(key), 1, fallback);
  }

  private static String first(Map<String, String> values, String first, String second) {
    return values.containsKey(first) ? values.get(first) : values.get(second);
  }

  private static float number(Map<String, String> values, String key, float fallback) {
    return dimension(values.get(key), 1, fallback);
  }

  private static float[] padding(Map<String, String> style) {
    String raw = style.get("padding");
    if (raw == null) raw = "0";
    float[] cached = PADDING_CACHE.get(raw);
    if (cached != null) return cached;
    String[] parts = SPACE.split(raw.trim());
    float[] values = new float[4];
    for (int i = 0; i < Math.min(4, parts.length); i++)
      values[i] = Math.max(0, dimension(parts[i], 1, 0));
    if (parts.length == 1)
      values[1] = values[2] = values[3] = values[0];
    else if (parts.length == 2) {
      values[2] = values[0];
      values[3] = values[1];
    } else if (parts.length == 3)
      values[3] = values[1];
    PADDING_CACHE.put(raw, values);
    return values;
  }

  private static float dimension(String value, float reference, float fallback) {
    if (value == null || "auto".equals(value)) return fallback;
    ParsedDimension parsed = DIMENSIONS.get(value);
    if (parsed == null) {
      parsed = new ParsedDimension(value);
      if (DIMENSIONS.size() >= 4096) DIMENSIONS.clear();
      DIMENSIONS.put(value, parsed);
    }
    if (!parsed.valid) return fallback;
    float result = parsed.value * (parsed.percent ? reference / 100 : 1);
    return Float.isFinite(result) ? clamp(result, -8192, 8192) : fallback;
  }

  private static final class ParsedDimension {
    final float value;
    final boolean percent, valid;

    ParsedDimension(String source) {
      String text = source.trim();
      percent = text.endsWith("%");
      float number = 0;
      boolean ok = false;
      try {
        if (percent)
          text = text.substring(0, text.length() - 1);
        else if (text.endsWith("px"))
          text = text.substring(0, text.length() - 2);
        number = Float.parseFloat(text);
        ok = Float.isFinite(number);
      } catch (NumberFormatException ignored) {
      }
      value = number;
      valid = ok;
    }
  }

  private NodePlan nodePlan(SkinDocument.Node node) {
    NodePlan plan = nodePlans.get(node);
    if (plan == null) {
      plan = new NodePlan(node, visualStyles(node, ""), componentFor(node), canAliasContext(node));
      nodePlans.put(node, plan);
    }
    return plan;
  }

  private boolean canAliasContext(SkinDocument.Node node) {
    if (node.attributes.containsKey("scope") || node.attributes.containsKey("bind")
        || boxBinding(node.text))
      return false;
    for (String value : node.attributes.values())
      if (boxBinding(value)) return false;
    for (String state : PAINT_STATES) {
      for (String value : document.styles(node, state).values())
        if (boxBinding(value)) return false;
      for (String value : document.themeBindings(node, state).values())
        if (boxBinding(value)) return false;
    }
    return true;
  }

  private static boolean boxBinding(String value) {
    return value != null && (value.contains("boxWidth") || value.contains("boxHeight"));
  }

  private SkinDocument.Node componentFor(SkinDocument.Node node) {
    String variant = node.attribute("visual", "");
    SkinDocument.Node component =
        variant.isEmpty() ? null : document.component("element." + node.tag + "." + variant);
    if (component == null && node.id != null) component = document.component(node.id);
    if (component == null && isHudTag(node.tag)) {
      component = document.component("hud." + node.tag);
      if (component == null)
        component = dev.nightclient.skin.DefaultSkin.document().component("hud." + node.tag);
    }
    return component == null ? document.component("element." + node.tag) : component;
  }

  private final class NodePlan {
    final Map<String, String> styles, boundStyles = new HashMap<String, String>(),
                                      boundAttributes = new HashMap<String, String>();
    SkinInputPlan inputs;
    boolean inputCompiled;
    final BoundProperty[] properties, layoutProperties, attributes;
    final CompiledStyleMap.Plan stylePlan;
    final SkinDocument.Node component;
    final boolean earlyCondition, aliasContext;
    final Map<String, Map<String, Frame>> framePools = new HashMap<String, Map<String, Frame>>();

    NodePlan(SkinDocument.Node node, Map<String, String> computed, SkinDocument.Node component,
        boolean aliasContext) {
      this.component = component;
      this.aliasContext = aliasContext;
      String condition = node.attributes.get("if");
      earlyCondition = condition != null && condition.indexOf("vars") < 0
          && !node.attributes.containsKey("scope");
      styles = new HashMap<String, String>(computed);
      for (String name : ATTRIBUTE_STYLES)
        if (node.attributes.containsKey(name)) styles.put(name, node.attributes.get(name));
      for (Map.Entry<String, String> entry : styles.entrySet())
        if (entry.getValue().contains("{{")) boundStyles.put(entry.getKey(), entry.getValue());
      for (Map.Entry<String, String> entry : node.attributes.entrySet())
        if (entry.getValue().contains("{{")) boundAttributes.put(entry.getKey(), entry.getValue());
      stylePlan = new CompiledStyleMap.Plan(styles);
      properties = boundProperties(boundStyles);
      layoutProperties = boundProperties(SkinView.this.boundStyles(node));
      attributes = boundProperties(boundAttributes);
    }
  }

  private BoundProperty[] boundProperties(Map<String, String> values) {
    BoundProperty[] result = new BoundProperty[values.size()];
    int at = 0;
    for (Map.Entry<String, String> entry : values.entrySet())
      result[at++] = new BoundProperty(entry.getKey(), bindings.compileText(entry.getValue()));
    return result;
  }

  private static final class BoundProperty {
    final String name;
    final SkinBindings.BoundText text;

    BoundProperty(String name, SkinBindings.BoundText text) {
      this.name = name;
      this.text = text;
    }
  }

  private static int color(String value, int fallback) {
    return SkinDocument.color(value, fallback);
  }

  private static float clamp(float value, float min, float max) {
    return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static final class Frame {
    PaintStyleMap paintStyles;
    String lastItemResource;
    boolean validItemResource;
    final SkinDocument.Node node;
    String id, presentationId;
    SkinDocument.Node presentation;
    Map<String, Object> context;
    final Map<String, String> style;
    Map<String, String> attributes;
    String key;
    ComponentMemory memory;
    Map<String, Frame> pool;
    String poolIdentity;
    long lastGeneration;
    double lastBuilt;
    Object variableRecipe;
    List<BoundProperty> dimensionStyles;
    Frame parent;
    final List<Frame> children = new ArrayList<Frame>();
    float x, y, w, h, scrollMax, exitDuration, naturalHeight, retireStartHeight, flowPresence = 1f;
    float measuredWidth = Float.NaN, measuredHeight;
    boolean heightAnimated, widthAnimated, horizontalReflow, flowPositionReflow, layoutTransition,
        flowSlotClip, ownsBox;
    boolean hudPlaced;
    double retiredAt = Double.NaN;
    String exitEasing = "cubic-out";

    Frame(SkinDocument.Node node, Map<String, Object> context, Map<String, String> style,
        Map<String, String> attributes) {
      this.node = node;
      this.id = node.id;
      this.context = context;
      this.style = style;
      this.attributes = attributes;
    }
  }

  private static final class Hit {
    final SkinDocument.Node node;
    final Frame frame;
    final Bounds clip;
    final String id, bind, key;
    final Transform transform;
    final boolean scrollbar, blocker;

    Hit(Frame frame, Bounds clip, Transform transform) {
      this(frame, clip, transform, false);
    }

    Hit(Frame frame, Bounds clip, Transform transform, boolean scrollbar) {
      this(frame, clip, transform, scrollbar, false);
    }

    Hit(Frame frame, Bounds clip, Transform transform, boolean scrollbar, boolean blocker) {
      this.frame = frame;
      node = frame.node;
      id = frame.id;
      this.clip = clip;
      bind = frame.attributes.get("bind");
      key = frame.key;
      this.transform = transform;
      this.scrollbar = scrollbar;
      this.blocker = blocker;
    }
  }

  private static final class Bounds {
    final float x, y, w, h;

    Bounds(float x, float y, float w, float h) {
      this.x = x;
      this.y = y;
      this.w = Math.max(0, w);
      this.h = Math.max(0, h);
    }

    Bounds intersect(Bounds other) {
      float xx = Math.max(x, other.x), yy = Math.max(y, other.y);
      return new Bounds(
          xx, yy, Math.min(x + w, other.x + other.w) - xx, Math.min(y + h, other.y + other.h) - yy);
    }

    boolean contains(float px, float py) {
      return px >= x && py >= y && px < x + w && py < y + h;
    }
  }

  private static class OffsetCanvas implements Canvas {
    final Canvas delegate;
    final float x, y;

    OffsetCanvas(Canvas delegate, float x, float y) {
      this.delegate = delegate;
      this.x = x;
      this.y = y;
    }

    public Object textMetricsKey() {
      return delegate.textMetricsKey();
    }

    public void rect(float a, float b, float w, float h, int c) {
      delegate.rect(x + a, y + b, w, h, c);
    }

    public void invertRect(float a, float b, float w, float h, float alpha) {
      delegate.invertRect(x + a, y + b, w, h, alpha);
    }

    public void roundedRect(float a, float b, float w, float h, float r, int c) {
      delegate.roundedRect(x + a, y + b, w, h, r, c);
    }

    public void strokeRoundedRect(float a, float b, float w, float h, float r, float t, int c) {
      delegate.strokeRoundedRect(x + a, y + b, w, h, r, t, c);
    }

    public void roundedRectCorners(
        float a, float b, float w, float h, float tl, float tr, float bl, float br, int c) {
      delegate.roundedRectCorners(x + a, y + b, w, h, tl, tr, bl, br, c);
    }

    public void strokeRoundedRectCorners(float a, float b, float w, float h, float tl, float tr,
        float bl, float br, float t, int c) {
      delegate.strokeRoundedRectCorners(x + a, y + b, w, h, tl, tr, bl, br, t, c);
    }

    public void gradientRoundedRectCorners(float a, float b, float w, float h, float tl, float tr,
        float bl, float br, int ctl, int ctr, int cbl, int cbr) {
      delegate.gradientRoundedRectCorners(x + a, y + b, w, h, tl, tr, bl, br, ctl, ctr, cbl, cbr);
    }

    public void gradientRect(float a, float b, float w, float h, int c, int d) {
      delegate.gradientRect(x + a, y + b, w, h, c, d);
    }

    public void gradientRoundedRect(float a, float b, float w, float h, float r, int c, int d) {
      delegate.gradientRoundedRect(x + a, y + b, w, h, r, c, d);
    }

    public void line(float a, float b, float c, float d, float t, int color) {
      delegate.line(x + a, y + b, x + c, y + d, t, color);
    }

    public void circle(float a, float b, float r, int c) {
      delegate.circle(x + a, y + b, r, c);
    }

    public void text(String value, float a, float b, float size, int c, boolean bold) {
      delegate.text(value, x + a, y + b, size, c, bold);
    }

    public float textWidth(String value, float size, boolean bold) {
      return delegate.textWidth(value, size, bold);
    }

    public void textTracked(
        String value, float a, float b, float size, int c, boolean bold, float tracking) {
      delegate.textTracked(value, x + a, y + b, size, c, bold, tracking);
    }

    public float textWidthTracked(String value, float size, boolean bold, float tracking) {
      return delegate.textWidthTracked(value, size, bold, tracking);
    }

    public void textFamily(String family, String value, float a, float b, float size, int c,
        boolean bold, float tracking) {
      delegate.textFamily(family, value, x + a, y + b, size, c, bold, tracking);
    }

    public float textWidthFamily(
        String family, String value, float size, boolean bold, float tracking) {
      return delegate.textWidthFamily(family, value, size, bold, tracking);
    }

    public void hungerIcon(float a, float b, float size, int fill) {
      delegate.hungerIcon(x + a, y + b, size, fill);
    }

    public boolean vanillaCrosshair(float a, float b, float size, float opacity) {
      return delegate.vanillaCrosshair(x + a, y + b, size, opacity);
    }

    public void viewmodelOutline(String hand, int color) {
      delegate.viewmodelOutline(hand, color);
    }

    public void gameSprite(String id, float a, float b, float w, float h, float u, float v,
        float rw, float rh, float sw, float sh, int c) {
      delegate.gameSprite(id, x + a, y + b, w, h, u, v, rw, rh, sw, sh, c);
    }

    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {
      delegate.fillTriangle(x + ax, y + ay, x + bx, y + by, x + cx, y + cy, c);
    }

    public void fillPolygon(float cx, float cy, float[] xy, int c) {
      if (xy == null || xy.length < 6) return;
      float[] out = new float[xy.length];
      for (int i = 0; i + 1 < xy.length; i += 2) {
        out[i] = x + xy[i];
        out[i + 1] = y + xy[i + 1];
      }
      delegate.fillPolygon(x + cx, y + cy, out, c);
    }

    public void gameText(String value, float a, float b, float scale, int c, boolean shadow) {
      delegate.gameText(value, x + a, y + b, scale, c, shadow);
    }

    public float gameTextWidth(String value, float scale) {
      return delegate.gameTextWidth(value, scale);
    }

    public void pushClip(float a, float b, float w, float h) {
      delegate.pushClip(x + a, y + b, w, h);
    }

    public void popClip() {
      delegate.popClip();
    }

    public void item(String id, float a, float b, float size, float alpha) {
      delegate.item(id, x + a, y + b, size, alpha);
    }

    public void inventoryItem(InventorySlot slot, float a, float b, float size, float alpha) {
      delegate.inventoryItem(slot, x + a, y + b, size, alpha);
    }

    public boolean entityHead(
        dev.nightclient.api.TargetView target, float a, float b, float size, float alpha) {
      return delegate.entityHead(target, x + a, y + b, size, alpha);
    }

    public boolean skinHead(String skinTextureId, float a, float b, float size, float radius,
        float alpha, int tintRgb) {
      return delegate.skinHead(skinTextureId, x + a, y + b, size, radius, alpha, tintRgb);
    }

    public boolean textureRegion(String textureId, float a, float b, float w, float h, float radius,
        float u0, float v0, float u1, float v1, float alpha, int tintRgb) {
      return delegate.textureRegion(
          textureId, x + a, y + b, w, h, radius, u0, v0, u1, v1, alpha, tintRgb);
    }

    public void mapQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float a, float b, float w, float h, float radius, float uCenter, float vCenter,
        float uHalfSpan, float vHalfSpan, float angleRad, boolean nearest, int argb) {
      delegate.mapQuad(cacheKey, revision, pixels, texWidth, texHeight, x + a, y + b, w, h, radius,
          uCenter, vCenter, uHalfSpan, vHalfSpan, angleRad, nearest, argb);
    }

    public boolean supportsIconAtlas() {
      return delegate.supportsIconAtlas();
    }

    public float iconPixelDensity() {
      return delegate.iconPixelDensity();
    }

    public void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float a, float b, float w, float h, int argb) {
      delegate.iconQuad(cacheKey, revision, pixels, texWidth, texHeight, x + a, y + b, w, h, argb);
    }

    public void playerPreview(dev.nightclient.api.PlayerSkin skin, float a, float b, float w,
        float h, float yaw, float pitch, float zoom, float time, float wind, float yawVelocity,
        float alpha) {
      delegate.playerPreview(
          skin, x + a, y + b, w, h, yaw, pitch, zoom, time, wind, yawVelocity, alpha);
    }

    public void capePreview(String cacheKey, dev.nightclient.api.PlayerSkin skin, float a, float b,
        float w, float h, float alpha) {
      delegate.capePreview(cacheKey, skin, x + a, y + b, w, h, alpha);
    }

    public void backdrop(
        float a, float b, float w, float h, float r, float blur, int q, float alpha) {
      delegate.backdrop(x + a, y + b, w, h, r, blur, q, alpha);
    }

    public void texture(String id, float a, float b, float w, float h, float r, float tile, int c) {
      delegate.texture(id, x + a, y + b, w, h, r, tile, c);
    }

    public void textureCorners(String id, float a, float b, float w, float h, float tl, float tr,
        float bl, float br, float tile, int c) {
      delegate.textureCorners(id, x + a, y + b, w, h, tl, tr, bl, br, tile, c);
    }

    public void backdropCorners(float a, float b, float w, float h, float tl, float tr, float bl,
        float br, float blur, int q, float alpha) {
      delegate.backdropCorners(x + a, y + b, w, h, tl, tr, bl, br, blur, q, alpha);
    }
  }

  private static final class CornerCanvas extends OffsetCanvas {
    final float base, tl, tr, bl, br;

    CornerCanvas(Canvas delegate, float base, float tl, float tr, float bl, float br) {
      super(delegate, 0, 0);
      this.base = base;
      this.tl = tl;
      this.tr = tr;
      this.bl = bl;
      this.br = br;
    }

    private float r(float corner, float radius) {
      if (corner <= 0) return 0;
      if (base <= 0 || radius >= base) return corner;
      float inset = base - radius;
      return Math.max(0, corner - inset);
    }

    public void roundedRect(float x, float y, float w, float h, float radius, int color) {
      delegate.roundedRectCorners(
          x, y, w, h, r(tl, radius), r(tr, radius), r(bl, radius), r(br, radius), color);
    }

    public void strokeRoundedRect(
        float x, float y, float w, float h, float radius, float thickness, int color) {
      delegate.strokeRoundedRectCorners(
          x, y, w, h, r(tl, radius), r(tr, radius), r(bl, radius), r(br, radius), thickness, color);
    }

    public void texture(String id, float a, float b, float w, float h, float r, float tile, int c) {
      delegate.textureCorners(
          id, x + a, y + b, w, h, r(tl, r), r(tr, r), r(bl, r), r(br, r), tile, c);
    }

    public void textureCorners(String id, float a, float b, float w, float h, float ctl, float ctr,
        float cbl, float cbr, float tile, int c) {
      delegate.textureCorners(
          id, x + a, y + b, w, h, r(tl, ctl), r(tr, ctr), r(bl, cbl), r(br, cbr), tile, c);
    }

    public void backdrop(
        float x, float y, float w, float h, float radius, float blur, int quality, float alpha) {
      delegate.backdropCorners(x, y, w, h, r(tl, radius), r(tr, radius), r(bl, radius),
          r(br, radius), blur, quality, alpha);
    }
  }

  private final class BudgetCanvas extends OffsetCanvas {
    BudgetCanvas(Canvas canvas) {
      super(canvas, 0, 0);
    }

    private boolean allow(int cost) {
      if (drawCommands + cost > 8192) {
        drawCommands = 8192;
        return false;
      }
      drawCommands += cost;
      return true;
    }

    public void rect(float a, float b, float w, float h, int c) {
      if (allow(1)) super.rect(a, b, w, h, c);
    }

    public void invertRect(float a, float b, float w, float h, float alpha) {
      if (allow(1)) super.invertRect(a, b, w, h, alpha);
    }

    public void roundedRect(float a, float b, float w, float h, float r, int c) {
      if (allow(1)) super.roundedRect(a, b, w, h, r, c);
    }

    public void strokeRoundedRect(float a, float b, float w, float h, float r, float t, int c) {
      if (allow(1)) super.strokeRoundedRect(a, b, w, h, r, t, c);
    }

    public void roundedRectCorners(
        float a, float b, float w, float h, float tl, float tr, float bl, float br, int c) {
      if (allow(1)) super.roundedRectCorners(a, b, w, h, tl, tr, bl, br, c);
    }

    public void strokeRoundedRectCorners(float a, float b, float w, float h, float tl, float tr,
        float bl, float br, float t, int c) {
      if (allow(1)) super.strokeRoundedRectCorners(a, b, w, h, tl, tr, bl, br, t, c);
    }

    public void gradientRoundedRectCorners(float a, float b, float w, float h, float tl, float tr,
        float bl, float br, int ctl, int ctr, int cbl, int cbr) {
      if (allow(1))
        super.gradientRoundedRectCorners(a, b, w, h, tl, tr, bl, br, ctl, ctr, cbl, cbr);
    }

    public void gradientRect(float a, float b, float w, float h, int c, int d) {
      if (allow(1)) super.gradientRect(a, b, w, h, c, d);
    }

    public void gradientRoundedRect(float a, float b, float w, float h, float r, int c, int d) {
      if (allow(1)) super.gradientRoundedRect(a, b, w, h, r, c, d);
    }

    public void line(float a, float b, float c, float d, float t, int color) {
      if (allow(1)) super.line(a, b, c, d, t, color);
    }

    public void circle(float a, float b, float r, int c) {
      if (allow(1)) super.circle(a, b, r, c);
    }

    public void text(String value, float a, float b, float size, int c, boolean bold) {
      if (allow(Math.max(1, value.codePointCount(0, value.length()))))
        super.text(value, a, b, size, c, bold);
    }

    public void textTracked(
        String value, float a, float b, float size, int c, boolean bold, float tracking) {
      if (allow(Math.max(1, value.codePointCount(0, value.length()))))
        super.textTracked(value, a, b, size, c, bold, tracking);
    }

    public void textFamily(String family, String value, float a, float b, float size, int c,
        boolean bold, float tracking) {
      if (allow(Math.max(1, value.codePointCount(0, value.length()))))
        super.textFamily(family, value, a, b, size, c, bold, tracking);
    }

    public void gameText(String value, float a, float b, float scale, int c, boolean shadow) {
      int cost = value == null ? 1 : Math.max(1, value.codePointCount(0, value.length()));
      if (allow(cost)) super.gameText(value, a, b, scale, c, shadow);
    }

    public void item(String id, float a, float b, float size, float alpha) {
      if (allow(16)) super.item(id, a, b, size, alpha);
    }

    public void inventoryItem(InventorySlot slot, float a, float b, float size, float alpha) {
      if (allow(16)) super.inventoryItem(slot, a, b, size, alpha);
    }

    public boolean entityHead(
        dev.nightclient.api.TargetView target, float a, float b, float size, float alpha) {
      return allow(4) && super.entityHead(target, a, b, size, alpha);
    }

    public boolean skinHead(String skinTextureId, float a, float b, float size, float radius,
        float alpha, int tintRgb) {
      return allow(4) && super.skinHead(skinTextureId, a, b, size, radius, alpha, tintRgb);
    }

    public boolean textureRegion(String textureId, float a, float b, float w, float h, float radius,
        float u0, float v0, float u1, float v1, float alpha, int tintRgb) {
      return allow(4)
          && super.textureRegion(textureId, a, b, w, h, radius, u0, v0, u1, v1, alpha, tintRgb);
    }

    public void mapQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float a, float b, float w, float h, float radius, float uCenter, float vCenter,
        float uHalfSpan, float vHalfSpan, float angleRad, boolean nearest, int argb) {
      if (allow(4))
        super.mapQuad(cacheKey, revision, pixels, texWidth, texHeight, a, b, w, h, radius, uCenter,
            vCenter, uHalfSpan, vHalfSpan, angleRad, nearest, argb);
    }

    public void playerPreview(dev.nightclient.api.PlayerSkin skin, float a, float b, float w,
        float h, float yaw, float pitch, float zoom, float time, float wind, float yawVelocity,
        float alpha) {
      if (allow(8))
        super.playerPreview(skin, a, b, w, h, yaw, pitch, zoom, time, wind, yawVelocity, alpha);
    }

    public void capePreview(String cacheKey, dev.nightclient.api.PlayerSkin skin, float a, float b,
        float w, float h, float alpha) {
      if (allow(4)) super.capePreview(cacheKey, skin, a, b, w, h, alpha);
    }

    public void hungerIcon(float x, float y, float size, int fill) {
      if (allow(4)) super.hungerIcon(x, y, size, fill);
    }

    public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
      return allow(4) && super.vanillaCrosshair(x, y, size, opacity);
    }

    public void viewmodelOutline(String hand, int color) {
      if (allow(4)) super.viewmodelOutline(hand, color);
    }

    public void gameSprite(String id, float a, float b, float w, float h, float u, float v,
        float rw, float rh, float sw, float sh, int c) {
      if (allow(4)) super.gameSprite(id, a, b, w, h, u, v, rw, rh, sw, sh, c);
    }

    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {
      if (allow(1)) super.fillTriangle(ax, ay, bx, by, cx, cy, c);
    }

    public void fillPolygon(float cx, float cy, float[] xy, int c) {
      if (xy != null && xy.length >= 6 && allow(xy.length / 2)) super.fillPolygon(cx, cy, xy, c);
    }

    public void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float a, float b, float w, float h, int c) {
      if (allow(2)) super.iconQuad(cacheKey, revision, pixels, texWidth, texHeight, a, b, w, h, c);
    }

    public void backdrop(
        float a, float b, float w, float h, float r, float blur, int q, float alpha) {
      if (allow(16)) super.backdrop(a, b, w, h, r, blur, q, alpha);
    }

    public void texture(String id, float a, float b, float w, float h, float r, float tile, int c) {
      if (allow(4)) super.texture(id, a, b, w, h, r, tile, c);
    }

    public void textureCorners(String id, float a, float b, float w, float h, float tl, float tr,
        float bl, float br, float tile, int c) {
      if (allow(4)) super.textureCorners(id, a, b, w, h, tl, tr, bl, br, tile, c);
    }

    public void backdropCorners(float a, float b, float w, float h, float tl, float tr, float bl,
        float br, float blur, int q, float alpha) {
      if (allow(16)) super.backdropCorners(a, b, w, h, tl, tr, bl, br, blur, q, alpha);
    }
  }
}
