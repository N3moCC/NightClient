package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TntView;
import dev.nightclient.config.CrosshairPattern;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeatureHudRenderer {
  private FeatureHudRenderer() {}

  private static final int MAX_TNT_INPUTS = 512, MAX_TNT_CACHES = 16;
  private static final Object[] EMPTY = new Object[0];
  private static final Comparator<TntView> BY_DISTANCE = new Comparator<TntView>() {
    public int compare(TntView a, TntView b) {
      int result = Float.compare(a.distance, b.distance);
      return result == 0 ? Integer.compare(a.id, b.id) : result;
    }
  };

  public static float[] inventoryBounds(Settings settings, int width, int height) {
    float[] b = HudRenderer.widgetBounds(settings, "inventory", width, height, true);
    return new float[] {
        b[0], b[1], b[2], b[3], b[4], Math.max(1, (settings.inventory.width - 12) / 9f)};
  }

  public static float[] crosshairBounds(Settings settings, int width, int height) {
    Settings.Crosshair h = settings.crosshair;
    if (!"shape".equals(h.mode)) {
      float scale = h.pixelScale, padding = 4 * scale;
      int size = h.pattern == null ? 15 : h.pattern.size;
      return new float[] {(float) Math.floor((width - size * scale) / 2f) + h.offsetX - padding,
          (float) Math.floor((height - size * scale) / 2f) + h.offsetY - padding,
          size * scale + padding * 2, size * scale + padding * 2, scale};
    }
    float scale = h.pixelScale, radius = (h.size + h.gap + h.thickness + h.outline + 4) * scale;
    return new float[] {width / 2f + h.offsetX - radius, height / 2f + h.offsetY - radius,
        radius * 2, radius * 2, scale};
  }

  public static float[] tntBounds(
      Settings settings, List<TntView> data, int width, int height, boolean editor) {
    HudRenderer.tnts(settings, data);
    float[] b = HudRenderer.widgetBounds(settings, "tnt", width, height, editor);
    float header = hudMetric(settings, "tnt-header-height", 28.2f),
          row = hudMetric(settings, "tnt-row-height", 14.2f);
    int capacity = Math.max(1, (int) ((settings.tnt.height - header) / row));
    return new float[] {
        b[0], b[1], b[2], b[3], b[4], Math.min(nearbyTnt(settings, data).size(), capacity)};
  }

  private static float hudMetric(Settings settings, String name, float fallback) {
    dev.nightclient.skin.SkinDocument document =
        dev.nightclient.skin.DefaultSkin.resolve(settings.skin.preset, settings.skinDocument);
    String source = document.styles(document.root(), "").get("--" + name);
    if (source == null) return fallback;
    source = source.trim();
    if (source.endsWith("px")) source = source.substring(0, source.length() - 2);
    try {
      float value = Float.parseFloat(source);
      return Float.isFinite(value) && value > 0 ? value : fallback;
    } catch (NumberFormatException invalid) {
      return fallback;
    }
  }

  private static List<TntView> nearbyTnt(Settings settings, List<TntView> data) {
    TntCache cache = tntCache(settings);
    if (cache.matches(settings, data)) return cache.nearby;
    List<TntView> list = new ArrayList<TntView>();
    if (data != null)
      for (TntView value : data)
        if (value != null && Float.isFinite(value.seconds) && value.seconds > 0
            && Float.isFinite(value.distance) && value.distance <= settings.tnt.range)
          list.add(value);

    Collections.sort(list, BY_DISTANCE);
    List<TntView> nearby = Collections.unmodifiableList(list);
    cache.remember(settings, data, nearby);
    return nearby;
  }

  private static TntCache tntCache(Settings settings) {
    TntCache value = TNT_CACHES.get(settings);
    if (value == null) {
      if (TNT_CACHES.size() >= MAX_TNT_CACHES) TNT_CACHES.clear();
      value = new TntCache();
      TNT_CACHES.put(settings, value);
    }
    return value;
  }

  private static final Map<Settings, TntCache> TNT_CACHES =
      new IdentityHashMap<Settings, TntCache>();

  private static final class TntCache {
    private Object[] inputs = EMPTY;
    private boolean known;
    private float range;
    private List<TntView> nearby = Collections.emptyList();
    private List<TntView> rowSource;
    private List<Map<String, Object>> rows = Collections.emptyList();
    private TntView[] rowValues = new TntView[0];
    private Object[] rowMaps = EMPTY;

    boolean matches(Settings settings, List<TntView> data) {
      int count = data == null ? 0 : data.size();
      if (!known || count != inputs.length || count > MAX_TNT_INPUTS
          || Float.floatToIntBits(range) != Float.floatToIntBits(settings.tnt.range))
        return false;
      for (int index = 0; index < count; index++)
        if (!sameFuse((TntView) inputs[index], data.get(index))) return false;
      return true;
    }

    void remember(Settings settings, List<TntView> data, List<TntView> values) {
      int count = data == null ? 0 : data.size();
      if (count > MAX_TNT_INPUTS) {
        inputs = EMPTY;
        known = false;
        nearby = Collections.emptyList();
        clearRows();
        return;
      }
      if (inputs.length != count) inputs = count == 0 ? EMPTY : new Object[count];
      for (int index = 0; index < count; index++) inputs[index] = data.get(index);
      range = settings.tnt.range;
      nearby = values;
      known = true;
    }

    void clearRows() {
      rowSource = null;
      rows = Collections.emptyList();
      rowValues = new TntView[0];
      rowMaps = EMPTY;
    }
  }

  private static boolean same(float a, float b) {
    return Float.floatToIntBits(a) == Float.floatToIntBits(b);
  }

  private static boolean sameFuse(TntView a, TntView b) {
    return a == b
        || a != null && b != null && a.id == b.id && same(a.seconds, b.seconds)
        && same(a.distance, b.distance);
  }

  public static List<Map<String, Object>> inventoryCells(
      Settings settings, List<InventorySlot> slots) {
    Map<Integer, InventorySlot> indexed = new LinkedHashMap<Integer, InventorySlot>();
    if (slots != null)
      for (InventorySlot slot : slots)
        if (slot != null) indexed.put(slot.slot, slot);
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (int row = 0; row < settings.inventory.rows + (settings.inventory.hotbar ? 1 : 0); row++)
      for (int column = 0; column < 9; column++) {
        int id = row == settings.inventory.rows ? column : 9 + row * 9 + column;
        InventorySlot slot = indexed.get(id);
        if (slot == null) slot = new InventorySlot(id, "minecraft:air", "", 0, 0, 0);
        Map<String, Object> cell = itemData(slot);
        cell.put("row", row);
        cell.put("column", column);
        cell.put("hotbar", row == settings.inventory.rows);
        result.add(Collections.unmodifiableMap(cell));
      }
    return Collections.unmodifiableList(result);
  }

  static Map<String, Object> itemData(InventorySlot slot) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("slot", slot);
    row.put("slotId", slot.slot);
    row.put("occupied", !slot.empty());
    row.put("itemId", slot.itemId);
    row.put("name", slot.name);
    row.put("count", slot.count);
    row.put("stacked", slot.count > 1);
    row.put("damageable", slot.maxDamage > 0);
    row.put("fraction", Math.max(0, Math.min(1, 1 - slot.damageFraction)));
    row.put("remainingDurability", Math.max(0, slot.maxDamage - slot.damage));
    row.put(
        "remainingPercent", Math.round(Math.max(0, Math.min(1, 1 - slot.damageFraction)) * 100));
    return row;
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> tntRows(Settings settings, List<TntView> data) {
    TntCache cache = tntCache(settings);
    List<TntView> values = nearbyTnt(settings, data);
    if (values == cache.rowSource) return cache.rows;
    int count = values.size();
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(count);
    boolean allReused = true;
    for (int index = 0; index < count; index++) {
      TntView value = values.get(index);
      boolean reused = index < cache.rowValues.length && sameFuse(cache.rowValues[index], value);
      if (!reused) allReused = false;
      result.add(reused ? (Map<String, Object>) cache.rowMaps[index] : tntRow(value));
    }
    if (allReused && count == cache.rowValues.length) {
      cache.rowSource = values;
      return cache.rows;
    }
    List<Map<String, Object>> rows = Collections.unmodifiableList(result);
    if (count <= MAX_TNT_INPUTS) {
      cache.rowValues = values.toArray(new TntView[count]);
      cache.rowMaps = result.toArray();
      cache.rowSource = values;
      cache.rows = rows;
    } else
      cache.clearRows();
    return rows;
  }

  private static Map<String, Object> tntRow(TntView value) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("id", value.id);
    row.put("seconds", value.seconds);
    row.put("distance", value.distance);
    return Collections.unmodifiableMap(row);
  }

  public static void render(Canvas canvas, Settings settings, List<InventorySlot> slots,
      List<TntView> tnts, int width, int height, double time, boolean hidden) {
    render(canvas, settings, slots, tnts, width, height, time, hidden, true);
  }

  public static void render(Canvas canvas, Settings settings, List<InventorySlot> slots,
      List<TntView> tnts, int width, int height, double time, boolean hidden,
      boolean crosshairVisible) {
    render(canvas, settings, slots, tnts, width, height, time, hidden, crosshairVisible, null);
  }

  public static void render(Canvas canvas, Settings settings, List<InventorySlot> slots,
      List<TntView> tnts, int width, int height, double time, boolean hidden,
      boolean crosshairVisible, dev.nightclient.ui.skin.SkinView skin) {
    if (hidden) return;
    HudRenderer.inventory(settings, slots);
    HudRenderer.tnts(settings, tnts);
    if (settings.inventory.enabled)
      HudRenderer.widget(canvas, settings, "inventory", width, height, time, false, skin);
    if (settings.tnt.enabled)
      HudRenderer.widget(canvas, settings, "tnt", width, height, time, false, skin);
    if (settings.crosshair.enabled && crosshairVisible)
      HudRenderer.widget(canvas, settings, "crosshair", width, height, time, false, skin);
  }

  public static void renderEditor(Canvas canvas, Settings settings, List<InventorySlot> slots,
      List<TntView> tnts, int width, int height, double time) {
    HudRenderer.inventory(settings, slots);
    HudRenderer.tnts(settings, tnts);
    HudRenderer.widget(canvas, settings, "inventory", width, height, time, true, null);
    HudRenderer.widget(canvas, settings, "tnt", width, height, time, true, null);
    HudRenderer.widget(canvas, settings, "crosshair", width, height, time, true, null);
  }

  public static void renderInventory(
      Canvas canvas, Settings settings, List<InventorySlot> slots, int width, int height) {
    HudRenderer.inventory(settings, slots);
    HudRenderer.widgetInstant(canvas, settings, "inventory", width, height, false);
  }

  public static void renderTnt(
      Canvas canvas, Settings settings, List<TntView> tnts, int width, int height) {
    HudRenderer.tnts(settings, tnts);
    HudRenderer.widgetInstant(canvas, settings, "tnt", width, height, false);
  }

  public static void renderCrosshair(Canvas canvas, Settings settings, int width, int height) {
    renderCrosshair(canvas, settings, width, height, false, 0f);
  }

  public static void renderCrosshair(
      Canvas canvas, Settings settings, int width, int height, boolean targetHit) {
    renderCrosshair(canvas, settings, width, height, targetHit, targetHit ? 1f : 0f);
  }

  public static void renderCrosshair(Canvas canvas, Settings settings, int width, int height,
      boolean targetHit, float targetProgress) {
    Settings.Crosshair h = settings.crosshair;
    if (h == null || h.opacity <= 0) return;
    crosshairAt(canvas, settings, width / 2f + h.offsetX, height / 2f + h.offsetY, 1f, targetHit,
        targetProgress);
  }

  public static void renderCrosshairTargetPreview(
      Canvas canvas, Settings settings, float cx, float cy, float maxBox) {
    Settings.Crosshair h = settings.crosshair;
    if (h == null) return;
    float estRadius;
    if (!h.targetShapeEnabled || "expand".equals(h.targetShape) || "custom".equals(h.targetShape)) {
      CrosshairPattern pat =
          (h.targetShapeEnabled && "custom".equals(h.targetShape) && h.targetPattern != null)
              ? h.targetPattern
              : h.pattern;
      int sz = pat == null ? 15 : pat.size;
      float sMul = h.targetShapeEnabled ? h.targetScale : 1f;
      estRadius = Math.max(8f, sz * h.pixelScale * sMul * 0.5f);
    } else {
      estRadius = Math.max(
          8f, (h.targetSize + h.targetGap + h.targetThickness + h.targetOutline) * h.pixelScale
              * h.targetScale);
    }
    float fit = estRadius * 2f > maxBox * 0.82f ? (maxBox * 0.82f) / (estRadius * 2f) : 1f;
    crosshairAt(canvas, settings, cx, cy, Math.max(0.25f, Math.min(2f, fit)), true, 1f);
  }

  private static void crosshairAt(Canvas canvas, Settings settings, float cx, float cy,
      float fitScale, boolean targetHit, float targetProgress) {
    Settings.Crosshair h = settings.crosshair;
    if (h == null || h.opacity <= 0) return;
    Theme t = settings.styleTheme("crosshair");
    float p = h.targetAnimate ? clamp(targetProgress, 0f, 1f) : (targetHit ? 1f : 0f);
    float ease = p <= 0f ? 0f : (p >= 1f ? 1f : p * p * (3f - 2f * p));
    float colorBlend = h.targetHighlight ? ease : 0f;
    boolean shapeActive = h.targetShapeEnabled && ease > 0.001f;

    if (!shapeActive) {
      renderBaseCrosshair(canvas, h, t, cx, cy, fitScale, 1f, colorBlend);
      return;
    }

    if ("expand".equals(h.targetShape)) {
      float scaleMul = fitScale * (1f + (h.targetScale - 1f) * ease);
      renderBaseCrosshair(canvas, h, t, cx, cy, scaleMul, 1f, colorBlend);
      return;
    }

    if (ease < 0.999f) {
      renderBaseCrosshair(canvas, h, t, cx, cy, fitScale, 1f - ease, colorBlend);
    }

    if ("custom".equals(h.targetShape)) {
      CrosshairPattern targetPat = h.targetPattern != null ? h.targetPattern : h.pattern;
      float scaleMul = fitScale * (0.88f + 0.12f * ease) * (1f + (h.targetScale - 1f) * ease);
      pixelCrosshair(canvas, h, targetPat, cx, cy, scaleMul, ease, colorBlend);
      return;
    }

    renderTargetVectorShape(canvas, h, t, cx, cy, fitScale, ease, colorBlend);
  }

  private static void renderBaseCrosshair(Canvas canvas, Settings.Crosshair h, Theme t, float x,
      float y, float scaleMul, float alphaMul, float colorBlend) {
    if (alphaMul <= 0.001f) return;
    if (!"shape".equals(h.mode)) {
      pixelCrosshair(canvas, h, h.pattern, x, y, scaleMul, alphaMul, colorBlend);
      return;
    }
    float scale = h.pixelScale * scaleMul;
    int normalColor = h.inheritColor ? t.accent : h.color;
    int baseColor = colorBlend <= 0f ? normalColor
        : (colorBlend >= 1f ? h.targetColor : Colors.mix(normalColor, h.targetColor, colorBlend));
    float effOpacity = h.opacity * alphaMul;
    int color = Colors.alpha(baseColor, effOpacity),
        edge = Colors.alpha(0xff101319, effOpacity);
    float length = h.size * scale, gap = h.gap * scale, thickness = h.thickness * scale,
          outline = h.outline * scale;
    if ("dot".equals(h.preset)) {
      if (outline > 0) canvas.circle(x, y, thickness + outline, edge);
      canvas.circle(x, y, thickness, color);
      return;
    }
    if ("circle".equals(h.preset) || "ring".equals(h.preset)) {
      if (outline > 0)
        canvas.strokeRoundedRect(x - length - outline, y - length - outline, (length + outline) * 2,
            (length + outline) * 2, length + outline, thickness + outline * 2, edge);
      canvas.strokeRoundedRect(
          x - length, y - length, length * 2, length * 2, length, thickness, color);
    } else if ("diamond".equals(h.preset)) {
      float[] xx = {x, x + length, x, x - length, x},
              yy = {y - length, y, y + length, y, y - length};
      if (outline > 0) {
        for (int i = 0; i < 4; i++)
          canvas.line(xx[i], yy[i], xx[i + 1], yy[i + 1], thickness + outline * 2, edge);
      }
      for (int i = 0; i < 4; i++)
        canvas.line(xx[i], yy[i], xx[i + 1], yy[i + 1], thickness, color);
    } else {
      bar(canvas, x - gap - length, y - thickness / 2, length, thickness, outline, color, edge);
      bar(canvas, x + gap, y - thickness / 2, length, thickness, outline, color, edge);
      bar(canvas, x - thickness / 2, y - gap - length, thickness, length, outline, color, edge);
      bar(canvas, x - thickness / 2, y + gap, thickness, length, outline, color, edge);
    }
    if (h.dot) {
      if (outline > 0) canvas.circle(x, y, thickness + outline, edge);
      canvas.circle(x, y, thickness, color);
    }
  }

  private static void renderTargetVectorShape(Canvas canvas, Settings.Crosshair h, Theme t,
      float cx, float cy, float fitScale, float ease, float colorBlend) {
    float effOpacity = h.opacity * ease;
    if (effOpacity <= 0.001f) return;
    int normalRgb = "custom".equals(h.colorMode)
        ? h.color
        : ("shape".equals(h.mode) && h.inheritColor ? t.accent : 0xffffffff);
    int rgb = colorBlend <= 0f ? normalRgb
        : (colorBlend >= 1f ? h.targetColor : Colors.mix(normalRgb, h.targetColor, colorBlend));
    int color = Colors.alpha(rgb, effOpacity);
    int edge = Colors.alpha(0xff101319, effOpacity);

    float scale =
        h.pixelScale * fitScale * (0.88f + 0.12f * ease) * (1f + (h.targetScale - 1f) * ease);
    float size = h.targetSize * scale;
    float gap = h.targetGap * scale * (1.25f - 0.25f * ease);
    float thickness = Math.max(0.5f, h.targetThickness * scale);
    float outline = Math.max(0f, h.targetOutline * scale);
    String shape = h.targetShape == null ? "x" : h.targetShape;

    float baseDeg = "x".equals(shape) ? 45f : 0f;
    float rad = (float) Math.toRadians(baseDeg + h.targetRotation * ease);
    float cos = (float) Math.cos(rad);
    float sin = (float) Math.sin(rad);

    if ("dot".equals(shape)) {
      float r = Math.max(thickness, size * 0.35f);
      if (outline > 0) canvas.circle(cx, cy, r + outline, edge);
      canvas.circle(cx, cy, r, color);
      return;
    }

    if ("circle".equals(shape)) {
      float r = Math.max(1f, size + gap * 0.5f);
      if (outline > 0)
        canvas.strokeRoundedRect(cx - r - outline, cy - r - outline, (r + outline) * 2,
            (r + outline) * 2, r + outline, thickness + outline * 2, edge);
      canvas.strokeRoundedRect(cx - r, cy - r, r * 2, r * 2, r, thickness, color);
    } else if ("diamond".equals(shape)) {
      float r = Math.max(1f, size + gap * 0.5f);
      float[] lx = {0, r, 0, -r, 0};
      float[] ly = {-r, 0, r, 0, -r};
      if (outline > 0) {
        for (int i = 0; i < 4; i++)
          rotatedLine(canvas, cx, cy, lx[i], ly[i], lx[i + 1], ly[i + 1], cos, sin,
              thickness + outline * 2, outline, edge);
      }
      for (int i = 0; i < 4; i++)
        rotatedLine(canvas, cx, cy, lx[i], ly[i], lx[i + 1], ly[i + 1], cos, sin,
            thickness, 0f, color);
    } else if ("brackets".equals(shape)) {
      float r = gap + size;
      float arm = Math.max(2f * scale, size * 0.6f);
      float[][] segs = {
          {-r, -r, -r + arm, -r}, {-r, -r, -r, -r + arm},
          {r, -r, r - arm, -r}, {r, -r, r, -r + arm},
          {-r, r, -r + arm, r}, {-r, r, -r, r - arm},
          {r, r, r - arm, r}, {r, r, r, r - arm}
      };
      if (outline > 0) {
        for (float[] s : segs)
          rotatedLine(canvas, cx, cy, s[0], s[1], s[2], s[3], cos, sin,
              thickness + outline * 2, outline, edge);
      }
      for (float[] s : segs)
        rotatedLine(canvas, cx, cy, s[0], s[1], s[2], s[3], cos, sin, thickness, 0f, color);
    } else if ("chevron".equals(shape)) {
      float topY = -gap * 0.4f;
      float wingX = size * 0.9f;
      float wingY = topY + size * 0.85f;
      if (outline > 0) {
        rotatedLine(canvas, cx, cy, -wingX, wingY, 0, topY, cos, sin,
            thickness + outline * 2, outline, edge);
        rotatedLine(canvas, cx, cy, 0, topY, wingX, wingY, cos, sin,
            thickness + outline * 2, outline, edge);
      }
      rotatedLine(canvas, cx, cy, -wingX, wingY, 0, topY, cos, sin, thickness, 0f, color);
      rotatedLine(canvas, cx, cy, 0, topY, wingX, wingY, cos, sin, thickness, 0f, color);
    } else {
      float[][] segs = {
          {-gap - size, 0, -gap, 0},
          {gap, 0, gap + size, 0},
          {0, -gap - size, 0, -gap},
          {0, gap, 0, gap + size}
      };
      if (outline > 0) {
        for (float[] s : segs)
          rotatedLine(canvas, cx, cy, s[0], s[1], s[2], s[3], cos, sin,
              thickness + outline * 2, outline, edge);
      }
      for (float[] s : segs)
        rotatedLine(canvas, cx, cy, s[0], s[1], s[2], s[3], cos, sin, thickness, 0f, color);
    }

    if (h.targetCenterDot) {
      float dotR = Math.max(0.8f * scale, thickness * 0.65f);
      if (outline > 0) canvas.circle(cx, cy, dotR + outline, edge);
      canvas.circle(cx, cy, dotR, color);
    }
  }

  private static void rotatedLine(Canvas canvas, float cx, float cy, float x1, float y1, float x2,
      float y2, float cos, float sin, float thickness, float capExtend, int color) {
    float rx1 = cx + x1 * cos - y1 * sin;
    float ry1 = cy + x1 * sin + y1 * cos;
    float rx2 = cx + x2 * cos - y2 * sin;
    float ry2 = cy + x2 * sin + y2 * cos;
    if (capExtend > 0f) {
      float dx = rx2 - rx1, dy = ry2 - ry1;
      float d = (float) Math.hypot(dx, dy);
      if (d > 1e-4f) {
        float ex = dx / d * capExtend;
        float ey = dy / d * capExtend;
        rx1 -= ex;
        ry1 -= ey;
        rx2 += ex;
        ry2 += ey;
      }
    }
    canvas.line(rx1, ry1, rx2, ry2, thickness, color);
  }

  private static void pixelCrosshair(Canvas canvas, Settings.Crosshair h, CrosshairPattern pattern,
      float cx, float cy, float scaleMul, float alphaMul, float colorBlend) {
    if (pattern == null || h.opacity <= 0 || alphaMul <= 0.001f) return;
    float scale = h.pixelScale * scaleMul;
    float effOpacity = h.opacity * alphaMul;

    float originX = (float) Math.floor(cx - pattern.size * scale / 2f);
    float originY = (float) Math.floor(cy - pattern.size * scale / 2f);
    boolean inverse = "vanilla".equals(h.colorMode) && colorBlend <= 0.001f && alphaMul >= 0.999f;

    if (inverse && scaleMul == 1f && pattern.isVanilla()
        && canvas.vanillaCrosshair(originX, originY, 15 * scale, effOpacity))
      return;
    for (int row = 0; row < pattern.size; row++) {
      int column = 0;
      while (column < pattern.size) {
        int pixel = pattern.pixel(column, row),
            color = pixelColor(pixel, h, inverse, colorBlend, effOpacity);
        if ((color >>> 24) == 0) {
          column++;
          continue;
        }
        int first = column++;
        while (column < pattern.size
            && pixelColor(pattern.pixel(column, row), h, inverse, colorBlend, effOpacity) == color)
          column++;
        float x = originX + first * scale, y = originY + row * scale, w = (column - first) * scale;
        if (inverse)
          canvas.invertRect(x, y, w, scale, (color >>> 24) / 255f);
        else
          canvas.rect(x, y, w, scale, color);
      }
    }
  }

  private static int pixelColor(int pixel, Settings.Crosshair settings, boolean inverse,
      float colorBlend, float effOpacity) {
    if ((pixel >>> 24) == 0) return 0;
    float pxCoverage = (pixel >>> 24) / 255f;
    float pxAlpha = pxCoverage * effOpacity;
    if (colorBlend >= 0.999f) return Colors.alpha(settings.targetColor, pxAlpha);
    if (inverse) {
      int base = colorBlend > 0.001f ? Colors.mix(0xffffffff, settings.targetColor, colorBlend) : 0xffffffff;
      return Colors.alpha(base, pxAlpha);
    }
    if ("pixels".equals(settings.colorMode)) {
      int base = colorBlend > 0.001f
          ? Colors.mix(pixel, Colors.alpha(settings.targetColor, pxCoverage), colorBlend)
          : pixel;
      return Colors.alpha(base, effOpacity);
    }
    int base = colorBlend > 0.001f
        ? Colors.mix(settings.color, settings.targetColor, colorBlend)
        : settings.color;
    return Colors.alpha(base, pxAlpha);
  }

  private static void bar(
      Canvas c, float x, float y, float w, float h, float outline, int color, int edge) {
    if (outline > 0)
      c.roundedRect(x - outline, y - outline, w + outline * 2, h + outline * 2, outline, edge);
    c.roundedRect(x, y, w, h, Math.min(w, h) * .2f, color);
  }

  private static float clamp(float v, float min, float max) {
    return Math.max(min, Math.min(max, v));
  }
}
