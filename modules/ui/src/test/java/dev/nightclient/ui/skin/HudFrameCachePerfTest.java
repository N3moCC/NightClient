package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PlayerSkin;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudFrameCachePerfTest {

  private static final class FullHudActions extends SkinViewTest.Actions {
    long revision = 1L;
    final List<Cooldown> cooldowns = new ArrayList<Cooldown>();
    final List<InventorySlot> inventory = new ArrayList<InventorySlot>();
    final List<InventorySlot> armor = new ArrayList<InventorySlot>();
    final List<TntView> tnts = new ArrayList<TntView>();
    final List<PotionView> potions = new ArrayList<PotionView>();
    VitalsView vitals = new VitalsView(18, 4.5f, true, true);
    TargetView target;
    TargetView localPlayer;
    Map<String, Object> networkInfo = new LinkedHashMap<String, Object>();
    Map<String, Object> keystrokes = new LinkedHashMap<String, Object>();
    Map<String, Object> coordinates = new LinkedHashMap<String, Object>();
    Map<String, Object> scoreboard = new LinkedHashMap<String, Object>();
    int ping = 35;
    int fps = 240;
    String gameVersion = "26.2";
    String saveStatus = "saved";
    boolean leftMainArm;
    boolean fireVisible;
    boolean crosshairTargetHit;

    FullHudActions() {
      cooldowns.add(new Cooldown("pearl", "Ender Pearl", "minecraft:ender_pearl", 5.5f, 15f));
      inventory.add(new InventorySlot(9, "minecraft:golden_apple", "Golden Apple", 16, 0, 0, true));
      armor.add(new InventorySlot(100, "minecraft:netherite_boots", "Boots", 1, 20, 481, true));
      tnts.add(new TntView(42, 320f, 180f, 2.5f, 12.5f, true));
      potions.add(new PotionView("minecraft:speed", "Speed", 1, 0x7CAFC6, 120f, true));
      target = new TargetView(7, "uuid-target", "Rival", 14.5f, 20f, 2f, 4.2f, true,
          "minecraft:skins/rival", armor);
      localPlayer = new TargetView(1, "uuid-self", "Player", 20f, 20f, 0f, 0f, true,
          "minecraft:skins/self", Collections.<InventorySlot>emptyList());

      networkInfo.put("connected", Boolean.TRUE);
      networkInfo.put("available", Boolean.TRUE);
      networkInfo.put("ping", Integer.valueOf(35));
      networkInfo.put("serverAddress", "play.example.net");

      keystrokes.put("w", Boolean.TRUE);
      keystrokes.put("a", Boolean.FALSE);
      keystrokes.put("s", Boolean.FALSE);
      keystrokes.put("d", Boolean.FALSE);
      keystrokes.put("space", Boolean.FALSE);
      keystrokes.put("lmb", Boolean.FALSE);
      keystrokes.put("rmb", Boolean.FALSE);
      keystrokes.put("cps_lmb", Integer.valueOf(8));
      keystrokes.put("cps_rmb", Integer.valueOf(4));

      coordinates.put("x", "124.0");
      coordinates.put("y", "68.0");
      coordinates.put("z", "-450.5");
      coordinates.put("dimension", "minecraft:overworld");
      coordinates.put("facing", "north");
      coordinates.put("speed", "4.32");

      scoreboard.put("active", Boolean.TRUE);
      scoreboard.put("title", "Hub");
      List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("text", "Coins: 100");
      row.put("score", Integer.valueOf(1));
      row.put("hasScore", Boolean.TRUE);
      lines.add(row);
      scoreboard.put("lines", lines);
    }

    @Override
    public long settingsRevision() {
      return revision;
    }

    @Override
    public List<Cooldown> cooldowns() {
      return cooldowns;
    }

    @Override
    public List<InventorySlot> inventory() {
      return inventory;
    }

    @Override
    public List<InventorySlot> armor() {
      return armor;
    }

    @Override
    public List<TntView> tnts() {
      return tnts;
    }

    @Override
    public List<PotionView> potions() {
      return potions;
    }

    @Override
    public VitalsView vitals() {
      return vitals;
    }

    @Override
    public TargetView target() {
      return target;
    }

    @Override
    public TargetView localPlayer() {
      return localPlayer;
    }

    @Override
    public Map<String, Object> networkInfo() {
      return networkInfo;
    }

    @Override
    public Map<String, Object> keystrokes() {
      return keystrokes;
    }

    @Override
    public Map<String, Object> coordinates() {
      return coordinates;
    }

    @Override
    public Map<String, Object> scoreboard() {
      return scoreboard;
    }

    @Override
    public int ping() {
      return ping;
    }

    @Override
    public int fps() {
      return fps;
    }

    @Override
    public String gameVersion() {
      return gameVersion;
    }

    @Override
    public String saveStatus() {
      return saveStatus;
    }

    @Override
    public boolean leftMainArm() {
      return leftMainArm;
    }

    @Override
    public boolean fireVisible() {
      return fireVisible;
    }

    @Override
    public boolean crosshairTargetHit() {
      return crosshairTargetHit;
    }
  }

  private static long compute(Settings settings, FullHudActions actions, Map<String, Object> ui) {
    return HudFrameCache.signature(settings, actions, actions.cooldowns, actions.inventory,
        actions.tnts, 1920, 1080, true, ui, 0f, 0f, "features", "", "", "", null, null, "", "",
        false, 123L);
  }

  @Test
  void hudSignatureUsesZeroReflectionAndDetectsEveryApiChange() {
    Settings settings = new Settings();
    FullHudActions actions = new FullHudActions();
    Map<String, Object> ui = new HashMap<String, Object>();
    ui.put("search", "");
    ui.put("sidebarCollapsed", Boolean.FALSE);
    ui.put("brushColor", Integer.valueOf(0xFFFFFFFF));

    long beforeReflection = HudFrameCache.reflectionFallbackCount;
    long base = compute(settings, actions, ui);
    assertNotEquals(0L, base);
    assertEquals(base, compute(settings, actions, ui), "identical inputs must produce same hash");

    actions.revision++;
    long revChanged = compute(settings, actions, ui);
    assertNotEquals(base, revChanged, "settingsRevision change must change signature");
    base = revChanged;

    actions.cooldowns.set(0,
        new Cooldown("pearl", "Ender Pearl", "minecraft:ender_pearl", 4.5f, 15f));
    long next = compute(settings, actions, ui);
    assertNotEquals(base, next, "Cooldown change must change signature");
    base = next;

    actions.inventory.set(0,
        new InventorySlot(9, "minecraft:golden_apple", "Golden Apple", 15, 0, 0, true));
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "InventorySlot change must change signature");
    base = next;

    actions.armor.set(0,
        new InventorySlot(100, "minecraft:netherite_boots", "Boots", 1, 25, 481, true));
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "Armor change must change signature");
    base = next;

    actions.tnts.set(0, new TntView(42, 320f, 180f, 2.0f, 12.5f, true));
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "TntView change must change signature");
    base = next;

    actions.potions.set(0, new PotionView("minecraft:speed", "Speed", 1, 0x7CAFC6, 119f, true));
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "PotionView change must change signature");
    base = next;

    actions.vitals = new VitalsView(17, 4.5f, true, true);
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "VitalsView change must change signature");
    base = next;

    actions.target = new TargetView(7, "uuid-target", "Rival", 12.5f, 20f, 2f, 4.2f, true,
        "minecraft:skins/rival", actions.armor);
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "TargetView health change must change signature");
    base = next;

    actions.target = new TargetView(7, "uuid-target", "Rival", 12.5f, 20f, 2f, 4.2f, true,
        "minecraft:skins/rival2", actions.armor);
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "TargetView headTexture change must change signature");
    base = next;

    actions.localPlayer = new TargetView(1, "uuid-self", "PlayerRenamed", 20f, 20f, 0f, 0f, true,
        "minecraft:skins/self", Collections.<InventorySlot>emptyList());
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "localPlayer change must change signature");
    base = next;

    actions.networkInfo.put("serverAddress", "hub.example.net");
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "networkInfo change must change signature");
    base = next;

    actions.ping = 88;
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "ping change must change signature");
    base = next;

    actions.fps = 360;
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "fps change must change signature");
    base = next;

    actions.keystrokes.put("space", Boolean.TRUE);
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "keystrokes change must change signature");
    base = next;

    actions.coordinates.put("x", "125.0");
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "coordinates change must change signature");
    base = next;

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) actions.scoreboard.get("lines");
    lines.get(0).put("text", "Coins: 150");
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "scoreboard line change must change signature");
    base = next;

    actions.leftMainArm = true;
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "leftMainArm change must change signature");
    base = next;

    actions.fireVisible = true;
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "fireVisible change must change signature");
    base = next;

    actions.crosshairTargetHit = true;
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "crosshairTargetHit change must change signature");
    base = next;

    actions.gameVersion = "26.3";
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "gameVersion change must change signature");
    base = next;

    actions.saveStatus = "saving";
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "saveStatus change must change signature");
    base = next;

    final int[] pixels = new int[4];
    pixels[0] = 0xFF112233;
    ui.put("previewSkin", new PlayerSkin() {
      @Override
      public int[] bodyPixels() {
        return pixels;
      }

      @Override
      public int[] capePixels() {
        return null;
      }

      @Override
      public boolean slimModel() {
        return false;
      }
    });
    next = compute(settings, actions, ui);
    assertNotEquals(base, next, "PlayerSkin value must be hashed");
    pixels[0] = 0xFF445566;
    assertNotEquals(next, compute(settings, actions, ui), "PlayerSkin pixels change must invalidate");

    assertEquals(beforeReflection, HudFrameCache.reflectionFallbackCount,
        "HUD signature with settingsRevision >= 0 must trigger ZERO reflection fallbacks");
  }

  @Test
  void nativeVanillaCrosshairAndEntityHeadStillHitHudFrameCache() {
    Settings settings = new Settings();
    settings.optimization.enabled = true;
    settings.menu.animationSpeed = 1000000f;
    settings.crosshair.enabled = true;
    settings.crosshair.mode = "pixels";
    settings.crosshair.colorMode = "vanilla";
    settings.target.enabled = true;

    FullHudActions actions = new FullHudActions();
    SkinView view = new SkinView(DefaultSkin.document(), settings, actions);
    final Object metrics = new Object();
    final int[] crosshairDraws = {0};
    final int[] headDraws = {0};
    SkinViewTest.Sink nativeCanvas = new SkinViewTest.Sink() {
      @Override
      public Object textMetricsKey() {
        return metrics;
      }

      @Override
      public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
        crosshairDraws[0]++;
        return true;
      }

      @Override
      public boolean entityHead(TargetView target, float x, float y, float size, float alpha) {
        headDraws[0]++;
        return true;
      }
    };

    assertTrue(view.renderHud(nativeCanvas, 1280, 720, 1.0, actions.cooldowns, actions.inventory,
        actions.tnts, true));
    assertEquals(0, view.hudFrameCacheHits(), "first frame records display list");
    assertEquals(1, crosshairDraws[0], "first frame draws native vanilla crosshair");
    assertEquals(1, headDraws[0], "first frame draws native entity head");

    assertTrue(view.renderHud(nativeCanvas, 1280, 720, 2.0, actions.cooldowns, actions.inventory,
        actions.tnts, true));
    assertEquals(1, view.hudFrameCacheHits(),
        "second identical frame must hit display-list cache even with native crosshair & head");
    assertEquals(2, crosshairDraws[0], "cached replay must still draw native vanilla crosshair");
    assertEquals(2, headDraws[0], "cached replay must still draw native entity head");
  }
}
