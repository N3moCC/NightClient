package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExtendedHudRenderer {
  private ExtendedHudRenderer() {}

  private static final ThreadLocal<Settings> BOUNDS_HOST = new ThreadLocal<Settings>() {
    protected Settings initialValue() {
      return new Settings();
    }
  };

  public static float[] bounds(Settings.Panel panel, int width, int height, float naturalHeight) {
    Settings host = BOUNDS_HOST.get();
    if (panel instanceof Settings.Armor) {
      Settings.Armor prev = host.armor;
      host.armor = (Settings.Armor) panel;
      try {
        return HudRenderer.widgetBounds(host, "armor", width, height, true);
      } finally {
        host.armor = prev;
      }
    } else if (panel instanceof Settings.Potions) {
      Settings.Potions prev = host.potions;
      host.potions = (Settings.Potions) panel;
      try {
        return HudRenderer.widgetBounds(host, "potions", width, height, true);
      } finally {
        host.potions = prev;
      }
    } else if (panel instanceof Settings.Target) {
      Settings.Target prev = host.target;
      host.target = (Settings.Target) panel;
      try {
        return HudRenderer.widgetBounds(host, "target", width, height, true);
      } finally {
        host.target = prev;
      }
    } else if (panel instanceof Settings.Inventory) {
      Settings.Inventory prev = host.inventory;
      host.inventory = (Settings.Inventory) panel;
      try {
        return HudRenderer.widgetBounds(host, "inventory", width, height, true);
      } finally {
        host.inventory = prev;
      }
    } else if (panel instanceof Settings.Tnt) {
      Settings.Tnt prev = host.tnt;
      host.tnt = (Settings.Tnt) panel;
      try {
        return HudRenderer.widgetBounds(host, "tnt", width, height, true);
      } finally {
        host.tnt = prev;
      }
    } else if (panel instanceof Settings.Scoreboard) {
      Settings.Scoreboard prev = host.scoreboard;
      host.scoreboard = (Settings.Scoreboard) panel;
      try {
        return HudRenderer.widgetBounds(host, "scoreboard", width, height, true);
      } finally {
        host.scoreboard = prev;
      }
    } else if (panel instanceof Settings.Keystrokes) {
      Settings.Keystrokes prev = host.keystrokes;
      host.keystrokes = (Settings.Keystrokes) panel;
      try {
        return HudRenderer.widgetBounds(host, "keystrokes", width, height, true);
      } finally {
        host.keystrokes = prev;
      }
    } else if (panel instanceof Settings.Coordinates) {
      Settings.Coordinates prev = host.coordinates;
      host.coordinates = (Settings.Coordinates) panel;
      try {
        return HudRenderer.widgetBounds(host, "coordinates", width, height, true);
      } finally {
        host.coordinates = prev;
      }
    } else if (panel instanceof Settings.Ping) {
      Settings.Ping prev = host.ping;
      host.ping = (Settings.Ping) panel;
      try {
        return HudRenderer.widgetBounds(host, "ping", width, height, true);
      } finally {
        host.ping = prev;
      }
    } else if (panel instanceof Settings.Fps) {
      Settings.Fps prev = host.fps;
      host.fps = (Settings.Fps) panel;
      try {
        return HudRenderer.widgetBounds(host, "fps", width, height, true);
      } finally {
        host.fps = prev;
      }
    } else if (panel instanceof Settings.BetterChat) {
      Settings.BetterChat prev = host.betterChat;
      host.betterChat = (Settings.BetterChat) panel;
      try {
        return HudRenderer.widgetBounds(host, "betterChat", width, height, true);
      } finally {
        host.betterChat = prev;
      }
    } else if (panel instanceof Settings.Minimap) {
      Settings.Minimap prev = host.minimap;
      host.minimap = (Settings.Minimap) panel;
      try {
        return HudRenderer.widgetBounds(host, "minimap", width, height, true);
      } finally {
        host.minimap = prev;
      }
    } else
      return new float[] {0, 0, 0, 0, 1};
  }

  public static float armorHeight(Settings.Armor armor) {
    float[] b = bounds(armor, 8192, 8192, 0);
    return b[3] / Math.max(.01f, b[4]);
  }

  public static List<Map<String, Object>> armorSlots(
      Settings settings, List<InventorySlot> values) {
    Map<Integer, InventorySlot> indexed = new LinkedHashMap<Integer, InventorySlot>();
    if (values != null)
      for (InventorySlot slot : values)
        if (slot != null && slot.slot >= 100 && slot.slot <= 103) indexed.put(slot.slot, slot);
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (int id = 100; id <= 103; id++) {
      InventorySlot slot = indexed.get(id);
      if (slot == null) slot = new InventorySlot(id, "minecraft:air", "", 0, 0, 0);
      Map<String, Object> row = FeatureHudRenderer.itemData(slot);
      row.put("index", id - 100);
      result.add(Collections.unmodifiableMap(row));
    }
    return Collections.unmodifiableList(result);
  }

  public static List<Map<String, Object>> potionRows(Settings settings, List<PotionView> values) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    boolean showLevel = settings != null && settings.potions != null && settings.potions.level;
    String format =
        settings != null && settings.potions != null && settings.potions.format != null
            ? settings.potions.format
            : "list";
    if (values != null)
      for (PotionView value : values)
        if (value != null) {
          String safeName = value.name == null ? "" : value.name;
          Map<String, Object> row = new LinkedHashMap<String, Object>();
          row.put("id", value.id);
          row.put("name", safeName);
          row.put("label",
              safeName + (showLevel && value.amplifier > 0 ? " " + (value.amplifier + 1) : ""));
          row.put("levelText", value.amplifier > 0 ? romanLevel(value.amplifier + 1) : "");
          row.put("amplifier", value.amplifier);
          String effect = value.id == null ? "minecraft:speed" : value.id;
          int colon = effect.indexOf(':');
          String namespace = colon < 0 ? "minecraft" : effect.substring(0, colon),
                 path = colon < 0 ? effect : effect.substring(colon + 1);
          row.put("texture", namespace + ":textures/mob_effect/" + path + ".png");
          row.put("sprite", namespace + ":mob_effect/" + path);
          row.put("color", 0xff000000 | (value.color & 0xffffff));
          row.put("durationSeconds", value.durationSeconds);
          row.put("beneficial", value.beneficial);
          row.put("infinite", value.durationSeconds < 0);
          row.put("format", format);
          result.add(Collections.unmodifiableMap(row));
        }
    return Collections.unmodifiableList(result);
  }

  private static String romanLevel(int level) {
    switch (level) {
      case 1: return "I";
      case 2: return "II";
      case 3: return "III";
      case 4: return "IV";
      case 5: return "V";
      case 6: return "VI";
      case 7: return "VII";
      case 8: return "VIII";
      case 9: return "IX";
      case 10: return "X";
      default: return String.valueOf(level);
    }
  }

  public static List<Map<String, Object>> targetRows(TargetView value) {
    if (value == null) return Collections.emptyList();
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("entityId", value.entityId);
    row.put("uuid", value.uuid);
    row.put("name", value.name);
    row.put("health", Math.max(0, value.health));
    row.put("maxHealth", Math.max(1, value.maxHealth));
    row.put("absorption", Math.max(0, value.absorption));
    row.put("fraction", Math.max(0, Math.min(1, value.health / Math.max(1, value.maxHealth))));
    row.put("absorptionFraction",
        Math.max(0, Math.min(1, value.absorption / Math.max(1, value.maxHealth))));
    row.put("distance", value.distance);
    row.put("player", value.player);
    return Collections.singletonList(Collections.unmodifiableMap(row));
  }

  public static float[] targetBounds(Settings settings, int width, int height) {
    return HudRenderer.widgetBounds(settings, "target", width, height, true);
  }

  public static void render(Canvas c, Settings s, List<InventorySlot> armor,
      List<PotionView> effects, VitalsView food, TargetView target, int width, int height,
      double time, boolean editor, dev.nightclient.ui.skin.SkinView skin) {
    HudRenderer.extended(s, armor, effects, target);
    HudRenderer.widget(c, s, "armor", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "potions", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "target", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "scoreboard", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "keystrokes", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "coordinates", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "ping", width, height, time, editor, skin);
    HudRenderer.widget(c, s, "fps", width, height, time, editor, skin);
  }

  public static void renderArmor(Canvas c, Settings s, List<InventorySlot> data, int w, int h) {
    HudRenderer.armor(s, data);
    HudRenderer.widgetInstant(c, s, "armor", w, h, false);
  }

  public static void renderPotions(Canvas c, Settings s, List<PotionView> data, int w, int h) {
    HudRenderer.potions(s, data);
    HudRenderer.widgetInstant(c, s, "potions", w, h, false);
  }

  public static void renderFood(Canvas c, Settings s, VitalsView data, int w, int h) {
    food(c, s, data, w, h);
  }

  public static void renderTarget(Canvas c, Settings s, TargetView data, int w, int h) {
    HudRenderer.target(s, data);
    HudRenderer.widgetInstant(c, s, "target", w, h, false);
  }

  public static float[] foodBounds(Settings s, int width, int height) {
    return new float[] {width / 2 + 10, height - 49, 81, 10, 1};
  }

  private static int foodFill(float points, int index) {
    float remaining = points - index * 2;
    return remaining >= 2 ? 2 : remaining > 0 ? 1 : 0;
  }

  private static void food(Canvas canvas, Settings s, VitalsView data, int width, int height) {
    if (!s.food.enabled || data == null || !data.available || !data.hungerVisible) return;
    int right = width / 2 + 91, y = height - 49;
    float saturation =
        Float.isNaN(data.saturation) ? 0 : Math.max(0, Math.min(20, data.saturation));
    for (int i = 0; i < 10; i++)
      canvas.hungerIcon(right - 9 - i * 8, y, 9, foodFill(saturation, i));
  }
}
