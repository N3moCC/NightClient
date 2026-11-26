package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.NcParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudThemeOwnershipTest {
  @Test
  void aWidgetReplacementOwnsBoundsAndEveryRenderedLabel() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.skinDocument = NcParser.parse(
        "component \"hud.cooldowns\" { panel (id = \"cooldowns-frame\") { position = absolute; x = "
        + "17px; y = 19px; width = 108px; height = 44px; hudScale = 1; text \"Widget "
        + "{{cooldownCount}}\" { height = 12px; fontSize = 9px } } }");
    List<Cooldown> data =
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 4, 10));
    HudPresentationTest.Sink canvas = new HudPresentationTest.Sink();
    HudRenderer.render(canvas, settings, data, 640, 400, 1, false);
    assertEquals(1, canvas.texts.size());
    assertEquals("Widget 1", canvas.texts.get(0).value);
    assertTrue(canvas.itemAlpha.isEmpty(), "Java must not draw a duplicate item or row");
    assertArrayEquals(new float[] {17, 19, 108, 44, 1},
        HudRenderer.widgetBounds(settings, "cooldowns", 640, 400, true));
    assertEquals(0, canvas.clips);
  }
  @Test
  void sourceRowsRetainIndependentIdentityEvenWhenTheirLabelsMatch() {
    Settings settings = new Settings();
    List<Map<String, Object>> rows = HudRenderer.rows(settings,
        Arrays.asList(new Cooldown("group/one", "Same label", "minecraft:shield", 2, 10),
            new Cooldown("group/two", "Same label", "minecraft:ender_pearl", 3, 10)));
    assertEquals(2, rows.size());
    assertNotEquals(rows.get(0).get("key"), rows.get(1).get("key"));
    assertThrows(UnsupportedOperationException.class, () -> rows.get(0).put("name", "changed"));
  }
  @Test
  void inventoryTntAndExtendedHostsAllHonorWidgetReplacements() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.inventory.enabled = settings.tnt.enabled = settings.armor.enabled =
        settings.potions.enabled = settings.target.enabled = true;
    settings.skinDocument =
        NcParser.parse("component \"hud.inventory\" { text \"Inventory {{inventory.enabled}}\" { "
                        + "width = 200; height = 10; fontSize = 7 } } "
            + "component \"hud.tnt\" { text \"TNT {{tntCount}}\" { width = 200; height = 10; "
              + "fontSize = 7 } } "
            + "component \"hud.armor\" { text \"Armor {{armorEmpty}}\" { width = 200; height = 10; "
              + "fontSize = 7 } } "
            + "component \"hud.potions\" { text \"Potions {{potionCount}}\" { width = 200; height "
              + "= 10; fontSize = 7 } } "
            + "component \"hud.target\" { text \"Target {{targetData.name}}\" { width = 200; "
              + "height = 10; fontSize = 7 } }");
    HudPresentationTest.Sink canvas = new HudPresentationTest.Sink();
    FeatureHudRenderer.renderInventory(canvas, settings,
        Collections.singletonList(new InventorySlot(9, "minecraft:diamond", "Diamond", 1, 0, 0)),
        640, 400);
    FeatureHudRenderer.renderTnt(
        canvas, settings, Collections.singletonList(new TntView(1, 0, 0, 2, 1)), 640, 400);
    ExtendedHudRenderer.render(canvas, settings,
        Collections.singletonList(
            new InventorySlot(100, "minecraft:iron_boots", "Boots", 1, 0, 100)),
        Collections.singletonList(new PotionView("speed", "Speed", 1, 0x3388ee, 90, true)), null,
        new TargetView(1, "id", "Opponent", 10, 20, 0, 3, true), 640, 400, 1, false, null);
    assertEquals(
        Arrays.asList("Inventory true", "TNT 1", "Armor false", "Potions 1", "Target Opponent"),
        Arrays.asList(canvas.texts.get(0).value, canvas.texts.get(1).value,
            canvas.texts.get(2).value, canvas.texts.get(3).value, canvas.texts.get(4).value));
    assertTrue(canvas.itemAlpha.isEmpty());
    assertEquals(0, canvas.clips);
  }
  @Test
  void bundledHudUsesTheSharedSurfaceAndKeepsItIndependentFromTheAccent() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.menu.hudScale = 1;
    settings.theme.surface = 0xff334455;
    settings.theme.accent = 0xffffffff;
    HudPresentationTest.Sink dark = new HudPresentationTest.Sink();
    HudRenderer.render(dark, settings,
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 4, 10)), 960, 540,
        0, false);
    assertTrue(dark.fillColors.stream().anyMatch(
                   color -> (color & 0xffffff) == 0x334455 && (color >>> 24) > 0),
        "HUD cards use the shared configurable surface");
    settings.theme.accent = 0xff4c8fff;
    HudPresentationTest.Sink tinted = new HudPresentationTest.Sink();
    HudRenderer.render(tinted, settings,
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 4, 10)), 960, 540,
        1, false);
    assertTrue(tinted.fillColors.stream().anyMatch(color -> (color & 0xffffff) == 0x334455),
        "Changing the accent must not recolor the card surface");
    assertTrue(tinted.maxRadius > 0, "Default cards use compact rounded surfaces");
  }
  @Test
  void aSingleActiveWidgetKeepsItsConfiguredGlobalScaleAndDisabledStacksReserveNoSpace() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.menu.hudScale = 1.2f;
    settings.hud.enabled = false;
    settings.inventory.enabled = true;
    float[] alone = HudRenderer.widgetBounds(settings, "inventory", 960, 540, false);
    assertEquals(1.2f, alone[4], .001f);
    float[] target = HudRenderer.widgetBounds(settings, "target", 960, 540, true);
    assertTrue(alone[0] > target[0] + target[2],
        "Default Inventory must clear the 120% Target card at the standard viewport");
    settings.hud.enabled = settings.tnt.enabled = settings.potions.enabled =
        settings.armor.enabled = settings.target.enabled = true;
    float[] together = HudRenderer.widgetBounds(settings, "inventory", 960, 540, false);
    assertEquals(alone[4], together[4], .001f,
        "Enabled lists with no visible rows reserve no viewport space");
    settings.inventory.offsetX = -27;
    float[] custom = HudRenderer.widgetBounds(settings, "inventory", 960, 540, false);
    assertEquals(1.2f, custom[4], .001f, "A moved widget retains its own scale and anchor");
    assertEquals(960 - custom[2] - 27, custom[0], .001f);
  }
  @Test
  void defaultListWidthMeasuresRealTextWhileExplicitWidgetWidthRemainsAuthoritative() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.menu.hudScale = 1;
    List<Cooldown> shortName =
        Collections.singletonList(new Cooldown("a", "Pearl", "minecraft:ender_pearl", 2, 10));
    List<Cooldown> longName = Collections.singletonList(
        new Cooldown("a", "A considerably longer item name", "minecraft:ender_pearl", 2, 10));
    float shortWidth = HudRenderer.bounds(settings, shortName, 2000, 1000, true)[2];
    float longWidth = HudRenderer.bounds(settings, longName, 2000, 1000, true)[2];
    assertEquals(126, shortWidth, .001f);
    assertTrue(longWidth > shortWidth);
    settings.hud.width = 180;
    assertEquals(180, HudRenderer.bounds(settings, shortName, 2000, 1000, true)[2], .001f);
    assertEquals(180, HudRenderer.bounds(settings, longName, 2000, 1000, true)[2], .001f);
  }
  @Test
  void listWidthMeasuresNameAndValueTogetherForEachActualRow() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    String longName = "A considerably longer item name with a compact timer";
    List<Cooldown> rows = Arrays.asList(new Cooldown("a", longName, "minecraft:shield", 1, 10),
        new Cooldown("b", "A", "minecraft:shield", 12000.9f, 12001));
    dev.nightclient.renderer.FontAtlas font =
        dev.nightclient.renderer.FontAtlas.get("studio-sans-medium");
    float first = font.width(longName, 6.8f, false)
        + font.width(HudRenderer.cooldownDuration(1, s.language), 6.8f, false);
    float second = font.width("A", 6.8f, false)
        + font.width(HudRenderer.cooldownDuration(12000.9f, s.language), 6.8f, false);
    float width = HudRenderer.bounds(s, rows, 2000, 1000, true)[2];
    assertEquals(Math.max(126, Math.max(first, second) + 43.5f), width, .01f);
    assertTrue(width < font.width(longName, 6.8f, false)
                + font.width(HudRenderer.cooldownDuration(12000.9f, s.language), 6.8f, false) + 43.5f,
        "Longest name and longest value on different rows must not inflate the container");
  }
  @Test
  void unrelatedWidgetsNeverResizeTheTargetOrItsFont() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1.2f;
    s.target.enabled = true;
    float[] alone = HudRenderer.widgetBounds(s, "target", 960, 540, true);
    TargetView target = new TargetView(1, "id", "Opponent", 12, 20, 0, 3, true);
    HudPresentationTest.Sink before = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderTarget(before, s, target, 960, 540);
    s.hud.enabled = s.inventory.enabled = s.tnt.enabled = s.potions.enabled = true;
    s.inventory.width = 400;
    float[] together = HudRenderer.widgetBounds(s, "target", 960, 540, true);
    assertArrayEquals(Arrays.copyOfRange(alone, 2, 5), Arrays.copyOfRange(together, 2, 5), .001f);
    assertEquals(160.8f, together[2], .001f);
    assertEquals(55.2f, together[3], .001f);
    assertEquals(1.2f, together[4], .001f);
    HudPresentationTest.Sink after = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderTarget(after, s, target, 960, 540);
    assertFalse(before.texts.isEmpty());
    assertEquals(before.texts.size(), after.texts.size());
    for (int i = 0; i < before.texts.size(); i++) {
      assertEquals(before.texts.get(i).value, after.texts.get(i).value);
      assertEquals(before.texts.get(i).size, after.texts.get(i).size, .001f);
    }
  }
  @Test
  void targetUsesCompactFaceAndSixRealEquipmentSlotsWithTwoPixelSpacing() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.menu.hudScale = 1;
    settings.target.enabled = true;
    settings.target.offsetY = 0;
    final java.util.List<float[]> items = new java.util.ArrayList<float[]>();
    final int[] heads = {0};
    HudPresentationTest.Sink canvas = new HudPresentationTest.Sink() {
      @Override
      public boolean entityHead(TargetView target, float x, float y, float size, float alpha) {
        heads[0]++;
        assertEquals(22, size, .001f);
        return true;
      }
      @Override
      public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
        items.add(new float[] {x, y, size});
        super.inventoryItem(slot, x, y, size, alpha);
      }
    };
    java.util.List<InventorySlot> equipment = new java.util.ArrayList<InventorySlot>();
    for (int index = 0; index < 6; index++)
      equipment.add(new InventorySlot(200 + index, "minecraft:shield", "Shield", 1, 0, 0));
    ExtendedHudRenderer.renderTarget(canvas, settings,
        new TargetView(1, "id", "Player", 12, 20, 0, 2, true, "minecraft:skin", equipment), 960,
        540);
    float[] bounds = ExtendedHudRenderer.targetBounds(settings, 960, 540);
    assertEquals(134, bounds[2], .001f);
    assertEquals(46, bounds[3], .001f);
    assertEquals(1, heads[0]);
    assertEquals(6, items.size());
    for (int index = 0; index < 6; index++) {
      assertEquals(8.5f, items.get(index)[2], .001f);
      if (index > 0) assertEquals(-9.5f, items.get(index)[0] - items.get(index - 1)[0], .001f);
    }
  }
  @Test
  void theLastLiveRowFadesBeforeItsContainerDisappears() {
    Settings settings = new Settings();
    settings.menu.hudScale = 1;
    settings.hud.width = 160;
    List<Cooldown> data =
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 4, 10));
    HudRenderer.render(new HudPresentationTest.Sink(), settings, data, 960, 540, 0, false);
    HudPresentationTest.Sink live = new HudPresentationTest.Sink();
    HudRenderer.render(live, settings, data, 960, 540, .3, false);
    assertTrue(live.texts.stream().anyMatch(text -> text.value.equals("Shield")));
    HudPresentationTest.Sink exiting = new HudPresentationTest.Sink();
    HudRenderer.render(exiting, settings, Collections.emptyList(), 960, 540, .32, false);
    assertTrue(exiting.texts.stream().anyMatch(text -> text.value.equals("Shield")),
        "Removing data must retain the keyed row during its exit");
    HudPresentationTest.Sink gone = new HudPresentationTest.Sink();
    HudRenderer.render(gone, settings, Collections.emptyList(), 960, 540, .6, false);
    assertTrue(gone.texts.isEmpty());
    assertTrue(gone.itemAlpha.isEmpty());
    assertEquals(0, gone.clips);
  }
  @Test
  void completeBundledSourceCarriesEveryMovableWidgetAndNoFoodPanel() {
    for (String id : new String[] {"cooldowns", "inventory", "tnt", "armor", "potions", "target",
             "crosshair", "scoreboard", "keystrokes", "coordinates", "ping", "fps"})
      assertNotNull(DefaultSkin.document().component("hud." + id), id);
    assertNull(DefaultSkin.document().component("hud.food"));
  }
}
