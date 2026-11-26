package dev.nightclient.ui.skin;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.ui.ExtendedHudRenderer;
import dev.nightclient.ui.FeatureHudRenderer;
import dev.nightclient.ui.HudRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HudDataCache {
  private static final int MAX_INPUTS = 512;
  private static final Object[] EMPTY = new Object[0];

  private interface Same<T> {
    boolean test(T previous, T current);
  }

  private static final Same<InventorySlot> ITEMS = HudDataCache::sameItem;
  private static final Same<Cooldown> TIMERS = (a, b)
      -> a == b
      || a != null && b != null && same(a.key, b.key) && same(a.name, b.name)
          && same(a.itemId, b.itemId) && same(a.remainingSeconds, b.remainingSeconds)
          && same(a.durationSeconds, b.durationSeconds)
          && same(a.remainingFraction, b.remainingFraction);
  private static final Same<PotionView> EFFECTS = (a, b)
      -> a == b
      || a != null && b != null && same(a.id, b.id) && same(a.name, b.name)
          && a.amplifier == b.amplifier && a.color == b.color
          && same(a.durationSeconds, b.durationSeconds) && a.beneficial == b.beneficial;
  private static final Same<TntView> FUSES = (a, b)
      -> a == b
      || a != null && b != null && a.id == b.id && same(a.screenX, b.screenX)
          && same(a.screenY, b.screenY) && same(a.seconds, b.seconds)
          && same(a.distance, b.distance) && a.visible == b.visible;

  private final Snapshot<InventorySlot> inventory = new Snapshot<InventorySlot>(),
                                        armor = new Snapshot<InventorySlot>();
  private final Snapshot<Cooldown> cooldowns = new Snapshot<Cooldown>();
  private final Snapshot<PotionView> potions = new Snapshot<PotionView>();
  private final Snapshot<TntView> tnts = new Snapshot<TntView>();
  private int inventoryRows, cooldownLimit;
  private boolean inventoryHotbar, potionLevel;
  private String cooldownSort, potionFormat;
  private float tntRange;
  private TargetSnapshot target;
  private boolean targetKnown;
  private List<Map<String, Object>> targetRows = Collections.emptyList();

  private boolean pingKnown, pingAvail, pingConn;
  private int pingVal;
  private String pingServer, pingLang;
  private Map<String, Object> pingCached;

  private boolean fpsKnown;
  private int fpsVal;
  private Map<String, Object> fpsCached;

  private boolean keystrokesKnown, keystrokesEditing;
  private Map<String, Object> keystrokesSourceCopy;
  private Map<String, Object> keystrokesCached;

  private boolean coordsKnown, coordsEditing;
  private String coordsX, coordsY, coordsZ;
  private Map<String, Object> coordsCached;

  private boolean scoreboardKnown, scoreboardEditing;
  private Object scoreboardTitle;
  private Object scoreboardActive;
  private List<?> scoreboardRawLinesCopy;
  private Map<String, Object> scoreboardCached;
  private String editingScoreboardLang;
  private Map<String, Object> editingScoreboardCached;

  private static final Map<String, Object> EDITING_KEYSTROKES = buildEditingKeystrokes();
  private static final Map<String, Object> EDITING_COORDS = buildEditingCoords();
  private static final Map<String, Object> EMPTY_SCOREBOARD = buildEmptyScoreboard();

  private static Map<String, Object> buildEditingKeystrokes() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("w", false);
    data.put("a", true);
    data.put("s", false);
    data.put("d", false);
    data.put("lmb", true);
    data.put("rmb", false);
    data.put("space", false);
    data.put("lmbCps", 8);
    data.put("rmbCps", 11);
    data.put("lmbText", "8 CPS");
    data.put("rmbText", "11 CPS");
    return Collections.unmodifiableMap(data);
  }

  private static Map<String, Object> buildEditingCoords() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("x", "124");
    data.put("y", "68");
    data.put("z", "-450");
    data.put("xyz", "124, 68, -450");
    return Collections.unmodifiableMap(data);
  }

  private static Map<String, Object> buildEmptyScoreboard() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("active", Boolean.FALSE);
    data.put("title", "");
    data.put("lines", Collections.emptyList());
    return Collections.unmodifiableMap(data);
  }

  private static Map<String, Object> buildEditingScoreboard(String language) {
    String lang = dev.nightclient.i18n.I18n.language(language);
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("title", dev.nightclient.i18n.I18n.text(lang, "app.name"));
    List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>(5);
    lines.add(editingScoreRow(dev.nightclient.i18n.I18n.text(lang, "hud.preview.scoreboard.line1"), 5, 0));
    lines.add(editingScoreRow(dev.nightclient.i18n.I18n.text(lang, "hud.preview.scoreboard.line2"), 4, 1));
    lines.add(editingScoreRow(dev.nightclient.i18n.I18n.text(lang, "hud.preview.scoreboard.line3"), 3, 2));
    lines.add(editingScoreRow(dev.nightclient.i18n.I18n.text(lang, "hud.preview.scoreboard.line4"), 2, 3));
    lines.add(editingScoreRow(dev.nightclient.i18n.I18n.text(lang, "hud.preview.scoreboard.line5"), 1, 4));
    data.put("lines", Collections.unmodifiableList(lines));
    data.put("active", Boolean.TRUE);
    return Collections.unmodifiableMap(data);
  }

  private static Map<String, Object> editingScoreRow(String text, int score, int index) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("text", text);
    row.put("score", score);
    row.put("hasScore", true);
    row.put("id", text + "#" + index);
    return Collections.unmodifiableMap(row);
  }

  List<Map<String, Object>> inventory(Settings settings, List<InventorySlot> values) {
    if (inventoryRows == settings.inventory.rows && inventoryHotbar == settings.inventory.hotbar
        && inventory.matches(values, ITEMS))
      return inventory.rows;
    List<Map<String, Object>> rows =
        SkinInputPlan.stableRows(FeatureHudRenderer.inventoryCells(settings, values));
    inventoryRows = settings.inventory.rows;
    inventoryHotbar = settings.inventory.hotbar;
    inventory.remember(values, rows);
    return rows;
  }

  List<Map<String, Object>> armor(Settings settings, List<InventorySlot> values) {
    if (armor.matches(values, ITEMS)) return armor.rows;
    List<Map<String, Object>> rows =
        SkinInputPlan.stableRows(ExtendedHudRenderer.armorSlots(settings, values));
    armor.remember(values, rows);
    return rows;
  }

  List<Map<String, Object>> potions(Settings settings, List<PotionView> values) {
    if (potionLevel == settings.potions.level && same(potionFormat, settings.potions.format)
        && potions.matches(values, EFFECTS))
      return potions.rows;
    List<Map<String, Object>> rows =
        SkinInputPlan.stableRows(ExtendedHudRenderer.potionRows(settings, values));
    potionLevel = settings.potions.level;
    potionFormat = settings.potions.format;
    potions.remember(values, rows);
    return rows;
  }

  List<Map<String, Object>> target(TargetView value) {
    if (targetKnown && (value == null ? target == null : target != null && target.matches(value)))
      return targetRows;
    targetRows = SkinInputPlan.stableRows(ExtendedHudRenderer.targetRows(value));
    target = value == null ? null : new TargetSnapshot(value);
    targetKnown = true;
    return targetRows;
  }

  List<Map<String, Object>> cooldowns(Settings settings, List<Cooldown> values) {
    if (cooldownLimit == settings.hud.maxVisible && same(cooldownSort, settings.hud.sort)
        && cooldowns.matches(values, TIMERS))
      return cooldowns.rows;
    List<Map<String, Object>> rows = SkinInputPlan.stableRows(HudRenderer.rows(settings, values));
    cooldownLimit = settings.hud.maxVisible;
    cooldownSort = settings.hud.sort;
    cooldowns.remember(values, rows);
    return rows;
  }

  List<Map<String, Object>> tnts(Settings settings, List<TntView> values) {
    if (same(tntRange, settings.tnt.range) && tnts.matches(values, FUSES)) return tnts.rows;
    List<Map<String, Object>> rows =
        SkinInputPlan.stableRows(FeatureHudRenderer.tntRows(settings, values));
    tntRange = settings.tnt.range;
    tnts.remember(values, rows);
    return rows;
  }

  Map<String, Object> ping(
      Settings settings, int rawPing, Map<String, Object> network, boolean editing) {
    Map<String, Object> source = network == null ? Collections.<String, Object>emptyMap() : network;
    Object measured = source.get("ping");
    int ping = editing ? 42 : measured instanceof Number ? ((Number) measured).intValue() : rawPing;
    boolean available = editing
        || (source.containsKey("available") ? Boolean.TRUE.equals(source.get("available"))
                                            : ping >= 0);
    boolean connected = editing || Boolean.TRUE.equals(source.get("connected"));
    Object address = source.get("serverAddress");
    String server = editing ? "example.org" : address instanceof String ? (String) address : "";
    if (server.isEmpty())
      server = dev.nightclient.i18n.I18n.text(
          settings.language, connected ? "ui.network_unknown_server" : "ui.network_disconnected");
    String lang = dev.nightclient.i18n.I18n.language(settings.language);
    int clampedPing = available ? Math.max(0, ping) : -1;
    if (pingKnown && pingVal == clampedPing && pingAvail == available && pingConn == connected
        && same(pingServer, server) && same(pingLang, lang)) {
      return pingCached;
    }
    Map<String, Object> data = new LinkedHashMap<String, Object>(8);
    data.put("ping", clampedPing);
    data.put("pingText",
        available ? dev.nightclient.i18n.I18n.format(lang, "hud.unit.ms",
                        Collections.<String, Object>singletonMap("value", Integer.valueOf(clampedPing)))
                  : dev.nightclient.i18n.I18n.text(lang, "hud.unit.ms_empty"));
    data.put("pingColor",
        !available              ? "#9299A6"
            : clampedPing < 80  ? "#72C69B"
            : clampedPing < 150 ? "#D7B46A"
                                : "#E18B91");
    data.put("serverIp", server);
    data.put("available", available);
    data.put("connected", connected);
    pingVal = clampedPing;
    pingAvail = available;
    pingConn = connected;
    pingServer = server;
    pingLang = lang;
    pingCached = Collections.unmodifiableMap(data);
    pingKnown = true;
    return pingCached;
  }

  Map<String, Object> fps(Settings settings, int measured, boolean editing) {
    int value = editing ? 144 : Math.max(0, measured);
    if (fpsKnown && fpsVal == value) return fpsCached;
    Map<String, Object> data = new LinkedHashMap<String, Object>(4);
    data.put("value", value);
    data.put("text", value + " FPS");
    data.put("color", value >= 60 ? "#72C69B" : value >= 30 ? "#D7B46A" : "#E18B91");
    fpsVal = value;
    fpsCached = Collections.unmodifiableMap(data);
    fpsKnown = true;
    return fpsCached;
  }

  Map<String, Object> keystrokes(Settings settings, Map<String, Object> raw, boolean editing) {
    if (editing) return EDITING_KEYSTROKES;
    if (raw == null || raw.isEmpty()) return Collections.emptyMap();
    if (keystrokesKnown && !keystrokesEditing && raw.equals(keystrokesSourceCopy)) {
      return keystrokesCached;
    }
    Map<String, Object> copy = new LinkedHashMap<String, Object>(raw);
    keystrokesSourceCopy = copy;
    keystrokesCached = Collections.unmodifiableMap(copy);
    keystrokesEditing = false;
    keystrokesKnown = true;
    return keystrokesCached;
  }

  Map<String, Object> coordinates(Settings settings, Map<String, Object> raw, boolean editing) {
    if (editing) return EDITING_COORDS;
    if (raw == null || raw.isEmpty()) return Collections.emptyMap();
    String bx = integerCoordinate(raw.get("x"));
    String by = integerCoordinate(raw.get("y"));
    String bz = integerCoordinate(raw.get("z"));
    if (bx == null || by == null || bz == null) {
      Object combined = raw.get("xyz");
      if (combined instanceof String) {
        String[] parts = ((String) combined).trim().split("[,\\s]+");
        if (parts.length == 3) {
          if (bx == null) bx = integerCoordinate(parts[0]);
          if (by == null) by = integerCoordinate(parts[1]);
          if (bz == null) bz = integerCoordinate(parts[2]);
        }
      }
    }
    if (bx == null || by == null || bz == null) return Collections.emptyMap();
    if (coordsKnown && !coordsEditing && same(coordsX, bx) && same(coordsY, by)
        && same(coordsZ, bz)) {
      return coordsCached;
    }
    Map<String, Object> data = new LinkedHashMap<String, Object>(6);
    data.put("x", bx);
    data.put("y", by);
    data.put("z", bz);
    data.put("xyz", bx + ", " + by + ", " + bz);
    coordsX = bx;
    coordsY = by;
    coordsZ = bz;
    coordsEditing = false;
    coordsCached = Collections.unmodifiableMap(data);
    coordsKnown = true;
    return coordsCached;
  }

  private static String integerCoordinate(Object value) {
    if (value == null) return null;
    if (value instanceof Integer || value instanceof Long) {
      return Long.toString(((Number) value).longValue());
    }
    double coordinate;
    try {
      coordinate = value instanceof Number ? ((Number) value).doubleValue()
                                           : Double.parseDouble(String.valueOf(value).trim());
    } catch (NumberFormatException ignored) {
      return null;
    }
    if (!Double.isFinite(coordinate)) return null;
    return Long.toString((long) Math.floor(coordinate));
  }

  Map<String, Object> scoreboard(Settings settings, Map<String, Object> raw, boolean editing) {
    if (editing) {
      String lang = dev.nightclient.i18n.I18n.language(settings.language);
      if (editingScoreboardCached == null || !same(editingScoreboardLang, lang)) {
        editingScoreboardCached = buildEditingScoreboard(lang);
        editingScoreboardLang = lang;
      }
      return editingScoreboardCached;
    }
    if (raw == null || raw.isEmpty()) return EMPTY_SCOREBOARD;
    Object rawTitle = raw.get("title");
    Object rawActive = raw.get("active");
    Object rawLinesObj = raw.get("lines");
    List<?> rawLinesList = rawLinesObj instanceof List ? (List<?>) rawLinesObj : null;
    if (scoreboardKnown && !scoreboardEditing
        && java.util.Objects.equals(scoreboardTitle, rawTitle)
        && java.util.Objects.equals(scoreboardActive, rawActive)
        && java.util.Objects.equals(scoreboardRawLinesCopy, rawLinesList)) {
      return scoreboardCached;
    }
    Map<String, Object> data = new LinkedHashMap<String, Object>(raw);
    boolean hasTitle = rawTitle != null && !String.valueOf(rawTitle).isEmpty();
    List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
    List<Object> linesSnapshot = rawLinesList == null ? null : new ArrayList<Object>(rawLinesList.size());
    if (rawLinesList != null) {
      for (int i = 0; i < rawLinesList.size(); i++) {
        Object entry = rawLinesList.get(i);
        if (!(entry instanceof Map)) continue;
        @SuppressWarnings("unchecked")
        Map<String, Object> srcRow = (Map<String, Object>) entry;
        linesSnapshot.add(new LinkedHashMap<String, Object>(srcRow));
        Map<String, Object> row = new LinkedHashMap<String, Object>(srcRow);
        if (row.get("id") == null || String.valueOf(row.get("id")).isEmpty()) {
          Object text = row.get("text");
          row.put("id", text != null ? text + "#" + i : "row#" + i);
        }
        normalized.add(Collections.unmodifiableMap(row));
      }
    }
    data.put("lines", Collections.unmodifiableList(normalized));
    data.put("active",
        Boolean.valueOf(Boolean.TRUE.equals(rawActive) || hasTitle || !normalized.isEmpty()));
    scoreboardTitle = rawTitle;
    scoreboardActive = rawActive;
    scoreboardRawLinesCopy = linesSnapshot;
    scoreboardEditing = false;
    scoreboardCached = Collections.unmodifiableMap(data);
    scoreboardKnown = true;
    return scoreboardCached;
  }

  private static boolean same(String a, String b) {
    return a == b || a != null && a.equals(b);
  }

  private static boolean same(float a, float b) {
    return Float.floatToIntBits(a) == Float.floatToIntBits(b);
  }

  private static boolean sameItem(InventorySlot a, InventorySlot b) {
    return a == b
        || a != null && b != null && a.slot == b.slot && same(a.itemId, b.itemId)
        && same(a.name, b.name) && a.count == b.count && a.damage == b.damage
        && a.maxDamage == b.maxDamage && same(a.damageFraction, b.damageFraction)
        && a.enchanted == b.enchanted;
  }

  private static final class Snapshot<T> {
    private Object[] values = EMPTY;
    private boolean known;
    private List<Map<String, Object>> rows;

    @SuppressWarnings("unchecked")
    boolean matches(List<T> current, Same<T> equal) {
      int count = current == null ? 0 : current.size();
      if (!known || count > MAX_INPUTS || count != values.length) return false;
      for (int i = 0; i < count; i++)
        if (!equal.test((T) values[i], current.get(i))) return false;
      return true;
    }

    void remember(List<T> current, List<Map<String, Object>> prepared) {
      int count = current == null ? 0 : current.size();
      if (count > MAX_INPUTS) {
        values = EMPTY;
        rows = null;
        known = false;
        return;
      }
      if (values.length != count) values = count == 0 ? EMPTY : new Object[count];
      for (int i = 0; i < count; i++) values[i] = current.get(i);
      rows = prepared;
      known = true;
    }
  }

  private static final class TargetSnapshot {
    final int entityId;
    final String uuid, name;
    final float health, maxHealth, absorption, distance;
    final boolean player;

    TargetSnapshot(TargetView value) {
      entityId = value.entityId;
      uuid = value.uuid;
      name = value.name;
      health = value.health;
      maxHealth = value.maxHealth;
      absorption = value.absorption;
      distance = value.distance;
      player = value.player;
    }

    boolean matches(TargetView value) {
      return entityId == value.entityId && same(uuid, value.uuid) && same(name, value.name)
          && same(health, value.health) && same(maxHealth, value.maxHealth)
          && same(absorption, value.absorption) && same(distance, value.distance)
          && player == value.player;
    }
  }
}
