package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.InventorySlot;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudOverhaulTest {
  private static final class Text {
    final String text;
    final float x, y, size;
    final int color;
    final boolean bold;
    Text(String text, float x, float y, float size, int color, boolean bold) {
      this.text = text;
      this.x = x;
      this.y = y;
      this.size = size;
      this.color = color;
      this.bold = bold;
    }
  }
  private static final class Sink extends SkinViewTest.Sink {
    final List<Text> draws = new ArrayList<Text>();
    final List<float[]> sprites = new ArrayList<float[]>(), items = new ArrayList<float[]>();
    final List<Integer> spriteColors = new ArrayList<Integer>();
    @Override
    public void text(String text, float x, float y, float size, int color, boolean bold) {
      super.text(text, x, y, size, color, bold);
      draws.add(new Text(text, x, y, size, color, bold));
    }
    @Override
    public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
      super.inventoryItem(slot, x, y, size, alpha);
      items.add(new float[] {x, y, size});
    }
    @Override
    public void gameSprite(String id, float x, float y, float w, float h, float u, float v,
        float rw, float rh, float sw, float sh, int color) {
      assertEquals("minecraft:hud/hotbar", id);
      sprites.add(new float[] {x, y, w, h, u, v, rw, rh, sw, sh});
      spriteColors.add(color);
    }
    Text one(String text) {
      return draws.stream()
          .filter(d -> text.equals(d.text))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Missing text " + text + ": " + strings()));
    }
  }
  private static Settings settings() {
    Settings s = new Settings();
    s.language = "en";
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    return s;
  }
  private static Sink render(SkinView view, String id, boolean editor) {
    Sink sink = new Sink();
    assertTrue(view.renderHudWidgetInstant(id, sink, 1000, 700, editor));
    assertEquals(0, sink.clips);
    return sink;
  }

  @Test
  void headerCountsAreRemovedWhileHeadersAndLiveValuesRemain() {
    Settings s = settings();
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions());
    for (String id : new String[] {"cooldowns", "tnt", "potions"}) {
      Sink sink = render(view, id, true);
      assertFalse(sink.draws.isEmpty(), id + " keeps its header");
      assertTrue(sink.draws.stream().noneMatch(t -> t.text.matches("[0-9]+")),
          id + " has no numeric count badge");
    }
  }

  @Test
  void coordinatesExposeOnlyCoordinatesAndPingUsesLiveConnectionData() {
    Settings s = settings();
    s.ping.enabled = true;
    s.coordinates.enabled = true;
    Map<String, Object> coordinates = new LinkedHashMap<String, Object>();
    coordinates.put("x", "124");
    coordinates.put("y", "68");
    coordinates.put("z", "-450");
    coordinates.put("nether", "15, -56");
    coordinates.put("fps", 999);
    coordinates.put("direction", "North");
    coordinates.put("speed", "9 m/s");
    Map<String, Object> network = new LinkedHashMap<String, Object>();
    network.put("available", true);
    network.put("connected", true);
    network.put("ping", 91);
    network.put("serverAddress", "play.example.org:25565");
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public Map<String, Object> coordinates() {
        return coordinates;
      }
      @Override
      public Map<String, Object> networkInfo() {
        return network;
      }
      @Override
      public int ping() {
        return 999;
      }
    });
    Sink coords = render(view, "coordinates", false);
    assertEquals(Collections.singletonList("124, 68, -450"), coords.strings());
    assertEquals(20, view.hudBoundsInstant("coordinates", 1000, 700, true)[3], .001f);
    Sink ping = render(view, "ping", false);
    assertEquals(
        Collections.singletonList(dev.nightclient.i18n.I18n.format(s.language, "hud.unit.ms",
            Collections.<String, Object>singletonMap("value", Integer.valueOf(91)))),
        ping.strings());
    assertTrue(ping.strings().stream().noneMatch(t -> t.contains("FPS")));
    network.put("available", false);
    network.put("connected", false);
    network.put("serverAddress", "");
    Sink disconnected = render(view, "ping", false);
    assertTrue(disconnected.strings().contains(
        dev.nightclient.i18n.I18n.text(s.language, "hud.unit.ms_empty")));
    assertFalse(disconnected.strings().contains(
        dev.nightclient.i18n.I18n.format(s.language, "hud.unit.ms",
            Collections.<String, Object>singletonMap("value", Integer.valueOf(999)))));
  }

  @Test
  void scoreboardNativeTextPreservesLegacyAndRgbColorsWithoutDrawingFormatMarkers() {
    Settings s = settings();
    s.scoreboard.enabled = true;
    Map<String, Object> line = new LinkedHashMap<String, Object>();
    line.put("text", "§fBalance: §x§1§2§3§4§5§6100");
    line.put("score", 2);
    line.put("hasScore", true);
    Map<String, Object> board = new LinkedHashMap<String, Object>();
    board.put("title", "§a§lServer");
    board.put("lines", Collections.singletonList(line));
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public Map<String, Object> scoreboard() {
        return board;
      }
    });
    Sink sink = render(view, "scoreboard", false);
    assertEquals(0xff55ff55, sink.one("Server").color);
    assertTrue(sink.one("Server").bold);
    assertEquals(0xffffffff, sink.one("Balance: ").color);
    assertEquals(0xff123456, sink.one("100").color);
    assertTrue(sink.strings().stream().noneMatch(t -> t.indexOf('§') >= 0));
    Text prefix = sink.one("Balance: "), value = sink.one("100");
    assertEquals(prefix.y, value.y, .001f);
    assertEquals(prefix.x + sink.textWidth(prefix.text, prefix.size, false), value.x, .001f);
  }

  @Test
  void keyGlyphsAreCenteredInTheGridAndMouseButtonsWithAndWithoutCps() {
    Settings s = settings();
    s.keystrokes.mouseButtons = true;
    s.keystrokes.space = true;
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions());
    for (boolean cps : new boolean[] {true, false}) {
      s.keystrokes.cps = cps;
      Sink sink = render(view, "keystrokes", true);
      float[] frame = view.hudBoundsInstant("keystrokes", 1000, 700, true);
      assertCenter(sink.one("W"), sink, frame[0] + 41, frame[1] + 16.5f);
      assertCenter(sink.one("A"), sink, frame[0] + 18, frame[1] + 39.5f);
      assertCenter(sink.one("S"), sink, frame[0] + 41, frame[1] + 39.5f);
      assertCenter(sink.one("D"), sink, frame[0] + 64, frame[1] + 39.5f);
      String lmb = dev.nightclient.i18n.I18n.text(s.language, "hud.keystrokes.lmb");
      String rmb = dev.nightclient.i18n.I18n.text(s.language, "hud.keystrokes.rmb");
      assertEquals(frame[0] + 23, centerX(sink.one(lmb), sink), .001f);
      assertEquals(frame[0] + 59, centerX(sink.one(rmb), sink), .001f);
      if (!cps) {
        assertEquals(frame[1] + 65, sink.one(lmb).y + sink.one(lmb).size / 2, .001f);
        assertFalse(sink.strings().contains("8 CPS"));
      }
    }
  }
  private static float centerX(Text text, Sink sink) {
    return text.x + sink.textWidth(text.text, text.size, text.bold) / 2;
  }
  private static void assertCenter(Text text, Sink sink, float x, float y) {
    assertEquals(x, centerX(text, sink), .001f, text.text + " horizontal");
    assertEquals(y, text.y + text.size / 2, .001f, text.text + " vertical");
  }

  @Test
  void minecraftArmorUsesOpaqueNativeSlotEdgesAndKeepsItemsCentered() {
    Settings s = settings();
    s.armor.enabled = true;
    List<InventorySlot> armor = new ArrayList<InventorySlot>();
    for (int i = 0; i < 4; i++)
      armor.add(new InventorySlot(100 + i, "minecraft:diamond_helmet", "Armor", 1, i * 10, 100));
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public List<InventorySlot> armor() {
        return armor;
      }
    });
    assertTrue(render(view, "armor", false).sprites.isEmpty());
    s.armor.style = "minecraft";
    for (boolean horizontal : new boolean[] {true, false}) {
      s.armor.horizontal = horizontal;
      for (float[] opacity : new float[][] {{1, 1}, {.5f, .5f}, {0, 1}, {1, 0}}) {
        s.armor.opacity = opacity[0];
        s.theme.backgroundOpacity = opacity[1];
        Sink sink = render(view, "armor", false);
        assertEquals(20, sink.sprites.size());
        assertEquals(4, sink.items.size());
        int interiorAlpha = Math.round(255 * opacity[0] * opacity[1]);
        for (int i = 0; i < 4; i++) {
          float[] leftRegion = horizontal && i > 0 ? new float[] {21, 0, 2, 22} : new float[] {0, 0, 3, 22};
          float[] rightRegion = horizontal && i < 3 ? new float[] {19, 0, 2, 22} : new float[] {179, 0, 3, 22};
          float[][] regions = {leftRegion, {3, 0, 16, 3}, {3, 3, 16, 16}, {3, 19, 16, 3}, rightRegion};
          int first = i * regions.length;
          for (int part = 0; part < regions.length; part++) {
            float[] sprite = sink.sprites.get(first + part);
            assertArrayEquals(regions[part], Arrays.copyOfRange(sprite, 4, 8));
            assertEquals(182, sprite[8]);
            assertEquals(22, sprite[9]);
            assertEquals(part == 2 ? interiorAlpha : 255, sink.spriteColors.get(first + part) >>> 24);
          }
          float[] interior = sink.sprites.get(first + 2), item = sink.items.get(i);
          assertEquals(interior[0] + 8, item[0] + item[2] / 2, .001f);
          assertEquals(interior[1] + 8, item[1] + item[2] / 2, .001f);
          if (horizontal && i > 0) {
            float[] prevRight = sink.sprites.get((i - 1) * 5 + 4);
            float[] curLeft = sink.sprites.get(first);
            assertEquals(prevRight[0] + prevRight[2], curLeft[0], .001f);
            assertEquals(20f, item[0] - sink.items.get(i - 1)[0], .001f);
          }
        }
      }
    }
  }

  @Test
  void cooldownRowsStripLegacyColorCodesWhilePreservingSpecialSymbols() {
    Settings s = settings();
    List<java.util.Map<String, Object>> rows = dev.nightclient.ui.HudRenderer.rows(
        s,
        Collections.singletonList(
            new dev.nightclient.api.Cooldown("trap", "\u00a76\u00a7l\u2605 \u00a7f\u0422\u0440\u0430\u043f\u043a\u0430",
                "minecraft:netherite_scrap", 12f, 20f)));
    assertEquals(1, rows.size());
    assertEquals("\u2605 \u0422\u0440\u0430\u043f\u043a\u0430", rows.get(0).get("name"));
  }
}
