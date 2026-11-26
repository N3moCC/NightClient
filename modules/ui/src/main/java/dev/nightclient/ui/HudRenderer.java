package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.skin.SkinView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HudRenderer {
  private HudRenderer() {}

  private static final Comparator<Cooldown> BY_NAME = new Comparator<Cooldown>() {
    public int compare(Cooldown a, Cooldown b) {
      String left = a == null || a.name == null ? "" : a.name;
      String right = b == null || b.name == null ? "" : b.name;
      return left.compareToIgnoreCase(right);
    }
  };
  private static final Comparator<Cooldown> BY_DURATION = new Comparator<Cooldown>() {
    public int compare(Cooldown a, Cooldown b) {
      return Float.compare(a.durationSeconds, b.durationSeconds);
    }
  };
  private static final Comparator<Cooldown> BY_REMAINING = new Comparator<Cooldown>() {
    public int compare(Cooldown a, Cooldown b) {
      return Float.compare(b.remainingSeconds, a.remainingSeconds);
    }
  };

  private static Comparator<Cooldown> comparator(String sort) {
    if ("name".equals(sort)) return BY_NAME;
    if ("duration".equals(sort)) return BY_DURATION;
    return BY_REMAINING;
  }

  private static List<Cooldown> ordered(
      Settings settings, List<Cooldown> data, List<Cooldown> result) {
    result.clear();
    if (data != null)
      for (Cooldown value : data)
        if (value != null && Float.isFinite(value.remainingSeconds) && value.remainingSeconds > 0)
          result.add(value);
    Collections.sort(result, comparator(settings.hud.sort));
    return result;
  }

  public static List<Cooldown> visibleCooldowns(
      Settings settings, List<Cooldown> data, int width, int height) {
    CooldownCache cache = cooldownCache(settings);
    if (cache.matches(settings, data)) return cache.visible;
    List<Cooldown> values = ordered(settings, data, cache.ordered);
    List<Cooldown> visible = Collections.unmodifiableList(new ArrayList<Cooldown>(
        values.subList(0, Math.min(values.size(), settings.hud.maxVisible))));
    cache.remember(settings, data, visible);
    return visible;
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> rows(Settings settings, List<Cooldown> data) {
    CooldownCache cache = cooldownCache(settings);
    List<Cooldown> values = visibleCooldowns(settings, data, 0, 0);
    if (values == cache.rowSource) return cache.rows;
    int count = values.size();
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(count);
    boolean allReused = true;
    for (int index = 0; index < count; index++) {
      Cooldown value = values.get(index);
      boolean reused =
          index < cache.rowValues.length && sameCooldown(cache.rowValues[index], value);
      if (!reused) allReused = false;
      result.add(reused ? (Map<String, Object>) cache.rowMaps[index] : row(value));
    }
    if (allReused && count == cache.rowValues.length) {
      cache.rowSource = values;
      return cache.rows;
    }
    List<Map<String, Object>> rows = Collections.unmodifiableList(result);
    if (count <= MAX_INPUTS) {
      cache.rowValues = values.toArray(new Cooldown[count]);
      cache.rowMaps = result.toArray();
      cache.rowSource = values;
      cache.rows = rows;
    } else
      cache.clearRows();
    return rows;
  }

  private static Map<String, Object> row(Cooldown value) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("key", value.key);
    row.put("itemId", value.itemId);
    row.put("name", cleanCooldownName(value.name));
    row.put("remainingSeconds", value.remainingSeconds);
    row.put("durationSeconds", value.durationSeconds);
    row.put("fraction", value.remainingFraction);
    return Collections.unmodifiableMap(row);
  }

  private static String cleanCooldownName(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    String cleaned = raw.indexOf('§') >= 0
        ? dev.nightclient.api.FormattedText.plainText(raw)
        : raw;
    return cleaned.trim();
  }

  public static float rowHeight(Settings.Hud h) {
    return Math.max(10, 14.2f * h.rowHeight / 38f);
  }

  public static float spacing(Settings.Hud h) {
    return Math.max(0, h.spacing - 7);
  }

  public static float[] bounds(
      Settings settings, List<Cooldown> data, int width, int height, boolean forceFrame) {
    cooldowns(settings, data);
    float[] b = widgetBounds(settings, "cooldowns", width, height, forceFrame);
    return new float[] {
        b[0], b[1], b[2], b[3], visibleCooldowns(settings, data, width, height).size()};
  }

  public static float effectiveScale(Settings settings, int width, int height) {
    return widgetBounds(settings, "cooldowns", width, height, true)[4];
  }

  static float[] widgetBounds(Settings settings, String id, int width, int height, boolean editor) {
    SkinView view = view(settings);
    view.invalidateLayout();
    return view.hudBoundsInstant(id, width, height, editor);
  }

  static void widgetInstant(
      Canvas canvas, Settings settings, String id, int width, int height, boolean editor) {
    view(settings).renderHudWidgetInstant(id, canvas, width, height, editor);
  }

  static void widget(Canvas canvas, Settings settings, String id, int width, int height,
      double time, boolean editor, SkinView skin) {
    SkinView active = skin == null ? view(settings) : skin;
    if (!active.renderHudWidget(id, canvas, width, height, time, editor)
        && active != view(settings))
      view(settings).renderHudWidget(id, canvas, width, height, time, editor);
  }

  public static void render(Canvas canvas, Settings settings, List<Cooldown> data, int width,
      int height, double time, boolean editor) {
    render(canvas, settings, data, width, height, time, editor, null);
  }

  public static void render(Canvas canvas, Settings settings, List<Cooldown> data, int width,
      int height, double time, boolean editor, SkinView skin) {
    cooldowns(settings, data);
    widget(canvas, settings, "cooldowns", width, height, time, editor, skin);
  }

  public static void renderEditor(
      Canvas canvas, Settings settings, List<Cooldown> data, int width, int height, double time) {
    render(canvas, settings, data, width, height, time, true);
  }

  public static String cooldownTime(float seconds) {
    return cooldownDuration(seconds, "en");
  }

  public static String cooldownDuration(float seconds, String language) {
    if (!Float.isFinite(seconds) || seconds < 0)
      return dev.nightclient.i18n.I18n.text(language, "units.duration.infinite");
    if (seconds >= 60) {
      long total = (long) Math.ceil(seconds);
      return Long.toString(total / 60) + ":" + (total % 60 < 10 ? "0" : "")
          + Long.toString(total % 60);
    }
    Map<String, Object> values = Collections.<String, Object>singletonMap("value",
        seconds < 10 ? String.format(Locale.ROOT, "%.1f", seconds)
                     : Long.toString((long) Math.ceil(seconds)));
    return dev.nightclient.i18n.I18n.format(language, "units.duration.seconds", values);
  }

  public static String duration(float seconds, String language, boolean clock) {
    Map<String, Object> values;
    String key;
    if (!Float.isFinite(seconds) || seconds < 0)
      return dev.nightclient.i18n.I18n.text(language, "units.duration.infinite");
    if (clock || seconds >= 60 && seconds < 3600) {
      int total = (int) Math.ceil(seconds);
      key = "units.duration.minutes";
      values = new LinkedHashMap<String, Object>();
      values.put("minutes", total / 60);
      values.put("seconds", (total % 60 < 10 ? "0" : "") + Integer.toString(total % 60));
    } else if (seconds < 60) {
      key = "units.duration.seconds";
      values = Collections.<String, Object>singletonMap(
          "value", String.format(Locale.ROOT, seconds < 10 ? "%.1f" : "%.0f", seconds));
    } else if (seconds < 86400) {
      key = "units.duration.hours";
      values = Collections.<String, Object>singletonMap(
          "value", String.format(Locale.ROOT, "%.1f", seconds / 3600));
    } else {
      key = "units.duration.days";
      values = Collections.<String, Object>singletonMap(
          "value", String.format(Locale.ROOT, "%.1f", seconds / 86400));
    }
    return dev.nightclient.i18n.I18n.format(language, key, values);
  }

  public static String effectDuration(float seconds, String language) {
    if (!Float.isFinite(seconds) || seconds < 0)
      return dev.nightclient.i18n.I18n.text(language, "units.duration.effectInfinite");
    int total = (int) Math.floor(seconds);
    if (total >= 3600)
      return Integer.toString(total / 3600) + ":" + (total / 60 % 60 < 10 ? "0" : "")
          + Integer.toString(total / 60 % 60) + ":" + (total % 60 < 10 ? "0" : "")
          + Integer.toString(total % 60);
    if (total >= 60)
      return Integer.toString(total / 60) + ":" + (total % 60 < 10 ? "0" : "")
          + Integer.toString(total % 60);
    return dev.nightclient.i18n.I18n.format(language, "units.duration.seconds",
        Collections.<String, Object>singletonMap("value", Integer.toString(total)));
  }

  static String ellipsize(Canvas canvas, String text, float width, float size) {
    return ellipsize(canvas, text, width, size, false);
  }

  static String ellipsize(Canvas canvas, String text, float width, float size, boolean bold) {
    if (text == null) return "";
    if (canvas.textWidth(text, size, bold) <= width) return text;
    if (width < canvas.textWidth("…", size, bold)) return "";
    int low = 0, high = text.codePointCount(0, text.length());
    while (low < high) {
      int middle = (low + high + 1) / 2, end = text.offsetByCodePoints(0, middle);
      if (canvas.textWidth(text.substring(0, end) + "…", size, bold) <= width)
        low = middle;
      else
        high = middle - 1;
    }
    return text.substring(0, text.offsetByCodePoints(0, low)) + "…";
  }

  static SkinView view(Settings settings) {
    return state(settings).view;
  }

  static void cooldowns(Settings settings, List<Cooldown> values) {
    State state = state(settings);
    List<Cooldown> safe = safe(values);
    boolean changed = !state.cooldowns.matches(safe, COOLDOWNS);
    state.actions.cooldowns = safe;
    if (changed) {
      state.cooldowns.remember(safe);
      state.view.invalidateLayout();
    }
  }

  static void inventory(Settings settings, List<InventorySlot> values) {
    State state = state(settings);
    List<InventorySlot> safe = safe(values);
    boolean changed = !state.inventory.matches(safe, ITEMS);
    state.actions.inventory = safe;
    if (changed) {
      state.inventory.remember(safe);
      state.view.invalidateLayout();
    }
  }

  static void tnts(Settings settings, List<TntView> values) {
    State state = state(settings);
    List<TntView> safe = safe(values);
    boolean changed = !state.tnts.matches(safe, FUSES);
    state.actions.tnts = safe;
    if (changed) {
      state.tnts.remember(safe);
      state.view.invalidateLayout();
    }
  }

  static void extended(
      Settings settings, List<InventorySlot> armor, List<PotionView> potions, TargetView target) {
    State state = state(settings);
    List<InventorySlot> safeArmor = safe(armor);
    List<PotionView> safePotions = safe(potions);
    boolean changed = !state.armor.matches(safeArmor, ITEMS);
    changed |= !state.potions.matches(safePotions, EFFECTS);
    changed |= !state.target.matches(target, TARGETS);
    state.actions.armor = safeArmor;
    state.actions.potions = safePotions;
    state.actions.target = target;
    if (changed) {
      state.armor.remember(safeArmor);
      state.potions.remember(safePotions);
      state.target.remember(target);
      state.view.invalidateLayout();
    }
  }

  static void armor(Settings settings, List<InventorySlot> values) {
    State state = state(settings);
    List<InventorySlot> safe = safe(values);
    boolean changed = !state.armor.matches(safe, ITEMS);
    state.actions.armor = safe;
    if (changed) {
      state.armor.remember(safe);
      state.view.invalidateLayout();
    }
  }

  static void potions(Settings settings, List<PotionView> values) {
    State state = state(settings);
    List<PotionView> safe = safe(values);
    boolean changed = !state.potions.matches(safe, EFFECTS);
    state.actions.potions = safe;
    if (changed) {
      state.potions.remember(safe);
      state.view.invalidateLayout();
    }
  }

  static void target(Settings settings, TargetView value) {
    State state = state(settings);
    boolean changed = !state.target.matches(value, TARGETS);
    state.actions.target = value;
    if (changed) {
      state.target.remember(value);
      state.view.invalidateLayout();
    }
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? Collections.<T>emptyList() : values;
  }

  private static final Map<Settings, State> STATES = new IdentityHashMap<Settings, State>();

  private static State state(Settings settings) {
    SkinDocument document = DefaultSkin.resolve(settings.skin.preset, settings.skinDocument);
    State value = STATES.get(settings);
    if (value == null || value.document != document) {
      if (STATES.size() >= 16) STATES.clear();
      value = new State(document, settings);
      STATES.put(settings, value);
    }
    return value;
  }

  private static final int MAX_INPUTS = 512, MAX_CACHES = 16;
  private static final Object[] EMPTY = new Object[0];
  private static final Map<Settings, CooldownCache> COOLDOWN_CACHES =
      new IdentityHashMap<Settings, CooldownCache>();

  private static CooldownCache cooldownCache(Settings settings) {
    CooldownCache value = COOLDOWN_CACHES.get(settings);
    if (value == null) {
      if (COOLDOWN_CACHES.size() >= MAX_CACHES) COOLDOWN_CACHES.clear();
      value = new CooldownCache();
      COOLDOWN_CACHES.put(settings, value);
    }
    return value;
  }

  private static final class CooldownCache {
    private Object[] inputs = EMPTY;
    private boolean known;
    private String sort;
    private int limit;
    private List<Cooldown> visible = Collections.emptyList();
    private final ArrayList<Cooldown> ordered = new ArrayList<Cooldown>();
    private List<Cooldown> rowSource;
    private List<Map<String, Object>> rows = Collections.emptyList();
    private Cooldown[] rowValues = new Cooldown[0];
    private Object[] rowMaps = EMPTY;

    boolean matches(Settings settings, List<Cooldown> data) {
      int count = data == null ? 0 : data.size();
      if (!known || count != inputs.length || count > MAX_INPUTS || limit != settings.hud.maxVisible
          || !same(sort, settings.hud.sort))
        return false;
      for (int index = 0; index < count; index++)
        if (!sameCooldown((Cooldown) inputs[index], data.get(index))) return false;
      return true;
    }

    void remember(Settings settings, List<Cooldown> data, List<Cooldown> values) {
      int count = data == null ? 0 : data.size();
      if (count > MAX_INPUTS) {
        inputs = EMPTY;
        known = false;
        visible = Collections.emptyList();
        clearRows();
        return;
      }
      if (inputs.length != count) inputs = count == 0 ? EMPTY : new Object[count];
      for (int index = 0; index < count; index++) inputs[index] = data.get(index);
      sort = settings.hud.sort;
      limit = settings.hud.maxVisible;
      visible = values;
      known = true;
    }

    void clearRows() {
      rowSource = null;
      rows = Collections.emptyList();
      rowValues = new Cooldown[0];
      rowMaps = EMPTY;
    }
  }

  private interface Same<T> {
    boolean test(T previous, T current);
  }

  private static final Same<Cooldown> COOLDOWNS = HudRenderer::sameCooldown;
  private static final Same<InventorySlot> ITEMS = HudRenderer::sameItem;
  private static final Same<PotionView> EFFECTS = HudRenderer::samePotion;
  private static final Same<TntView> FUSES = HudRenderer::sameTnt;
  private static final Same<TargetView> TARGETS = HudRenderer::sameTarget;

  private static boolean same(String a, String b) {
    return a == b || a != null && a.equals(b);
  }

  private static boolean same(float a, float b) {
    return Float.floatToIntBits(a) == Float.floatToIntBits(b);
  }

  private static boolean sameCooldown(Cooldown a, Cooldown b) {
    return a == b
        || a != null && b != null && same(a.key, b.key) && same(a.name, b.name)
        && same(a.itemId, b.itemId) && same(a.remainingSeconds, b.remainingSeconds)
        && same(a.durationSeconds, b.durationSeconds)
        && same(a.remainingFraction, b.remainingFraction);
  }

  private static boolean sameItem(InventorySlot a, InventorySlot b) {
    return a == b
        || a != null && b != null && a.slot == b.slot && same(a.itemId, b.itemId)
        && same(a.name, b.name) && a.count == b.count && a.damage == b.damage
        && a.maxDamage == b.maxDamage && same(a.damageFraction, b.damageFraction)
        && a.enchanted == b.enchanted;
  }

  private static boolean samePotion(PotionView a, PotionView b) {
    return a == b
        || a != null && b != null && same(a.id, b.id) && same(a.name, b.name)
        && a.amplifier == b.amplifier && a.color == b.color
        && same(a.durationSeconds, b.durationSeconds) && a.beneficial == b.beneficial;
  }

  private static boolean sameTnt(TntView a, TntView b) {
    return a == b
        || a != null && b != null && a.id == b.id && same(a.screenX, b.screenX)
        && same(a.screenY, b.screenY) && same(a.seconds, b.seconds) && same(a.distance, b.distance)
        && a.visible == b.visible;
  }

  private static boolean sameTarget(TargetView a, TargetView b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a.entityId != b.entityId || !same(a.uuid, b.uuid) || !same(a.name, b.name)
        || !same(a.health, b.health) || !same(a.maxHealth, b.maxHealth)
        || !same(a.absorption, b.absorption) || !same(a.distance, b.distance)
        || a.player != b.player || !same(a.headTexture, b.headTexture))
      return false;
    List<InventorySlot> left = a.equipment, right = b.equipment;
    if (left.size() != right.size()) return false;
    for (int index = 0; index < left.size(); index++)
      if (!sameItem(left.get(index), right.get(index))) return false;
    return true;
  }

  private static final class Snapshot<T> {
    private Object[] values = EMPTY;
    private boolean known;

    @SuppressWarnings("unchecked")
    boolean matches(List<T> current, Same<T> equal) {
      int count = current == null ? 0 : current.size();
      if (!known || count > MAX_INPUTS || count != values.length) return false;
      for (int index = 0; index < count; index++)
        if (!equal.test((T) values[index], current.get(index))) return false;
      return true;
    }

    void remember(List<T> current) {
      int count = current == null ? 0 : current.size();
      if (count > MAX_INPUTS) {
        values = EMPTY;
        known = false;
        return;
      }
      if (values.length != count) values = count == 0 ? EMPTY : new Object[count];
      for (int index = 0; index < count; index++) values[index] = current.get(index);
      known = true;
    }
  }

  private static final class ScalarSnapshot<T> {
    private T value;
    private boolean known;

    boolean matches(T current, Same<T> equal) {
      return known && equal.test(value, current);
    }

    void remember(T current) {
      value = current;
      known = true;
    }
  }

  private static final class State {
    final SkinDocument document;
    final DataActions actions = new DataActions();
    final SkinView view;
    final Snapshot<Cooldown> cooldowns = new Snapshot<Cooldown>();
    final Snapshot<InventorySlot> inventory = new Snapshot<InventorySlot>(),
                                  armor = new Snapshot<InventorySlot>();
    final Snapshot<TntView> tnts = new Snapshot<TntView>();
    final Snapshot<PotionView> potions = new Snapshot<PotionView>();
    final ScalarSnapshot<TargetView> target = new ScalarSnapshot<TargetView>();

    State(SkinDocument document, Settings settings) {
      this.document = document;
      view = new SkinView(document, settings, actions);
    }
  }

  private static final class DataActions implements UiActions {
    List<Cooldown> cooldowns = Collections.emptyList();
    List<InventorySlot> inventory = Collections.emptyList(), armor = Collections.emptyList();
    List<TntView> tnts = Collections.emptyList();
    List<PotionView> potions = Collections.emptyList();
    TargetView target;

    public List<Cooldown> cooldowns() {
      return cooldowns;
    }

    public List<InventorySlot> inventory() {
      return inventory;
    }

    public List<TntView> tnts() {
      return tnts;
    }

    public List<InventorySlot> armor() {
      return armor;
    }

    public List<PotionView> potions() {
      return potions;
    }

    public TargetView target() {
      return target;
    }

    public void changed() {}

    public String clipboard() {
      return "";
    }

    public void clipboard(String value) {}

    public List<String> profiles() {
      return Collections.emptyList();
    }

    public void saveProfile(String name) {}

    public void loadProfile(String name) {}

    public void deleteProfile(String name) {}

    public String exportProfile() {
      return "";
    }

    public void importProfile(String json) {}

    public void reset() {}

    public void undo() {}

    public boolean canUndo() {
      return false;
    }

    public void checkpoint() {}

    public void close() {}

    public String gameVersion() {
      return "";
    }

    public String saveStatus() {
      return "";
    }
  }
}
