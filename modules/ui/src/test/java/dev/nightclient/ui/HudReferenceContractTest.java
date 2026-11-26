package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudReferenceContractTest {
  @Test
  void timersUseWholeSecondsWithDifferentRoundingForCooldownsAndEffects() {
    assertEquals("9.9s", HudRenderer.cooldownDuration(9.9f, "en"));
    assertEquals("11s", HudRenderer.cooldownDuration(10.1f, "en"));
    assertEquals("1:01", HudRenderer.cooldownDuration(60.1f, "en"));
    assertEquals("200:01", HudRenderer.cooldownDuration(12000.9f, "en"));
    assertEquals("7s", HudRenderer.effectDuration(7.9f, "en"));
    assertEquals("1:00", HudRenderer.effectDuration(60.9f, "en"));
    assertEquals("3:20:00", HudRenderer.effectDuration(12000.9f, "en"));
    assertEquals("**:**", HudRenderer.effectDuration(-1, "en"));
  }
  @Test
  void remainingTimeSortShowsLongestCooldownFirst() {
    Settings s = new Settings();
    s.hud.sort = "remaining";
    List<Cooldown> input = Arrays.asList(new Cooldown("a", "A", "minecraft:shield", 5, 10),
        new Cooldown("b", "B", "minecraft:shield", 2, 10),
        new Cooldown("c", "C", "minecraft:shield", 8, 10));
    List<Cooldown> sorted = HudRenderer.visibleCooldowns(s, input, 640, 400);
    assertEquals(Arrays.asList("c", "a", "b"),
        Arrays.asList(sorted.get(0).key, sorted.get(1).key, sorted.get(2).key));
  }
  @Test
  void effectsExposeTheirResourcePackTextureRatherThanARecoloredSharedIcon() {
    Settings s = new Settings();
    Map<String, Object> row = ExtendedHudRenderer
                                  .potionRows(s,
                                      Collections.singletonList(new PotionView(
                                          "custom:swiftness", "Speed", 1, 0x123456, 30, true)))
                                  .get(0);
    assertEquals("custom:textures/mob_effect/swiftness.png", row.get("texture"));
    assertEquals("Speed 2", row.get("label"));
  }
  @Test
  void armorUsesFourRoundedSlotsAndKeepsDurabilityBelowTheRealItems() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    s.armor.enabled = true;
    s.armor.percent = false;
    final List<float[]> sprites = new ArrayList<float[]>(), items = new ArrayList<float[]>();
    final List<Integer> slots = new ArrayList<Integer>();
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
      @Override
      public void gameSprite(String id, float x, float y, float w, float h, float u, float v,
          float rw, float rh, float sw, float sh, int color) {
        sprites.add(new float[] {x, y, w, h, u, v, rw, rh});
      }
      @Override
      public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
        slots.add(slot.slot);
        items.add(new float[] {x, y, size});
      }
    };
    List<InventorySlot> armor = new ArrayList<InventorySlot>();
    for (int i = 0; i < 4; i++)
      armor.add(new InventorySlot(100 + i, "minecraft:diamond_helmet", "Armor", 1, i * 25, 100));
    ExtendedHudRenderer.renderArmor(sink, s, armor, 640, 400);
    assertTrue(sprites.isEmpty(), "The standalone HUD owns its rounded armor cells");
    assertEquals(4, items.size());
    assertEquals(Arrays.asList(100, 101, 102, 103), slots);
    assertArrayEquals(new float[] {419, 361, 96, 31, 1},
        HudRenderer.widgetBounds(s, "armor", 640, 400, true), .001f);
    for (int i = 0; i < 4; i++)
      assertArrayEquals(new float[] {426 + i * 22, 364, 16}, items.get(i), .001f);
    assertEquals(Arrays.asList("100", "75", "50", "25"),
        Arrays.asList(sink.texts.get(0).value, sink.texts.get(1).value, sink.texts.get(2).value,
            sink.texts.get(3).value));
    for (HudPresentationTest.Text text : sink.texts) {
      assertEquals(381.35f, text.y, .01f);
      assertTrue(text.y > items.get(0)[1] + items.get(0)[2]);
    }
    assertTrue(sink.maxRadius > 0);
    assertEquals(0, sink.clips);
  }
  @Test
  void emptyEditorListsAreHeaderOnlyAndNeverInventAnExtraBodyRow() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    s.hud.enabled = s.potions.enabled = s.tnt.enabled = true;
    for (String id : new String[] {"cooldowns", "potions", "tnt"}) {
      float[] b = HudRenderer.widgetBounds(s, id, 960, 540, true);
      assertEquals(28, b[3], .001f, id + " keeps only its header while empty");
      HudPresentationTest.Sink sink = new HudPresentationTest.Sink();
      HudRenderer.widgetInstant(sink, s, id, 960, 540, true);
      assertEquals(1, sink.texts.size(), id + " has only a header without a redundant count badge");
      HudPresentationTest.Text title = sink.texts.get(0);
      assertEquals(b[0] + 22, title.x, .001f);
      assertEquals(b[1] + 7.35f, title.y, .01f);
      assertEquals(7.3f, title.size, .01f);
      assertEquals(0, sink.clips);
    }
  }
  @Test
  void cooldownRowsKeepTheExactContainerInsetsInsteadOfCenteringInAnOversizedCard() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    s.hud.anchorX = 0;
    s.hud.offsetX = 10;
    s.hud.offsetY = 20;
    final List<float[]> items = new ArrayList<float[]>();
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
      @Override
      public void item(String id, float x, float y, float size, float alpha) {
        items.add(new float[] {x, y, size});
        super.item(id, x, y, size, alpha);
      }
    };
    List<Cooldown> rows =
        Collections.singletonList(new Cooldown("a", "Pearl", "minecraft:ender_pearl", 1.2f, 10));
    HudRenderer.render(sink, s, rows, 960, 540, 0, false);
    float[] b = HudRenderer.bounds(s, rows, 960, 540, true);
    assertArrayEquals(new float[] {10, 20, 126, 48}, Arrays.copyOf(b, 4), .001f);
    assertArrayEquals(new float[] {18.5f, 50.75f, 9.5f}, items.get(0), .01f);
    HudPresentationTest.Text name =
        sink.texts.stream().filter(t -> t.value.equals("Pearl")).findFirst().get();
    assertEquals(31.5f, name.x, .01f);
    assertEquals(52.1f, name.y, .05f);
    assertEquals(6.8f, name.size, .01f);
  }
  @Test
  void nativeArmorSizeDoesNotFollowTheContainerHudSizeButKeepsItsUniformEditorScale() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 2;
    s.armor.enabled = true;
    float[] nativeSize = HudRenderer.widgetBounds(s, "armor", 960, 540, true);
    assertEquals(96, nativeSize[2], .001f);
    assertEquals(31, nativeSize[3], .001f);
    assertEquals(1, nativeSize[4], .001f);
    s.armor.scale = 1.25f;
    float[] edited = HudRenderer.widgetBounds(s, "armor", 960, 540, true);
    assertEquals(120, edited[2], .001f);
    assertEquals(38.75f, edited[3], .001f);
    assertEquals(1.25f, edited[4], .001f);
  }
  @Test
  void targetAvatarNameHealthAndEquipmentUseTheSameDesignCoordinateSystem() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    s.target.enabled = true;
    s.target.anchorX = s.target.anchorY = 0;
    s.target.offsetX = 10;
    s.target.offsetY = 20;
    final List<float[]> heads = new ArrayList<float[]>(), items = new ArrayList<float[]>(),
                        fills = new ArrayList<float[]>();
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
      @Override
      public boolean entityHead(TargetView target, float x, float y, float size, float alpha) {
        heads.add(new float[] {x, y, size});
        return true;
      }
      @Override
      public void inventoryItem(InventorySlot item, float x, float y, float size, float alpha) {
        items.add(new float[] {x, y, size});
      }
      @Override
      public void roundedRect(float x, float y, float w, float h, float r, int color) {
        fills.add(new float[] {x, y, w, h});
        super.roundedRect(x, y, w, h, r, color);
      }
    };
    List<InventorySlot> gear = new ArrayList<InventorySlot>();
    for (int i = 0; i < 6; i++)
      gear.add(new InventorySlot(200 + i, "minecraft:shield", "Shield", 1, 0, 0));
    ExtendedHudRenderer.renderTarget(sink, s,
        new TargetView(1, "uuid", "Player", 12.7f, 20, 0, 3, true, "minecraft:skin", gear), 960,
        540);
    assertArrayEquals(new float[] {17, 26, 22}, heads.get(0), .001f);
    HudPresentationTest.Text name =
        sink.texts.stream().filter(t -> t.value.equals("Player")).findFirst().get();
    assertEquals(44, name.x, .01f);
    assertEquals(27.45f, name.y, .5f);
    assertEquals(7.6f, name.size, .01f);
    assertTrue(fills.stream().anyMatch(b
        -> Math.abs(b[0] - 17) < .05f && Math.abs(b[1] - 55.9f) < .05f
            && Math.abs(b[2] - 120) < .05f && Math.abs(b[3] - 3.6f) < .05f));
    assertEquals(6, items.size());
    for (int i = 0; i < 6; i++)
      assertArrayEquals(new float[] {128.5f - i * 9.5f, 38.5f, 8.5f}, items.get(i), .05f);
    assertEquals(0, sink.clips);
  }
}
