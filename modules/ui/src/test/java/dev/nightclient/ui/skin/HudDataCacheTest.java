package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudDataCacheTest {
  private static InventorySlot item(int id, String name, int count, int damage, boolean enchanted) {
    return new InventorySlot(id, "minecraft:diamond_sword", name, count, damage, 100, enchanted);
  }
  private static Cooldown timer(String key, String name, float seconds) {
    return new Cooldown(key, name, "minecraft:ender_pearl", seconds, 20);
  }
  private static PotionView potion(String name, int level, float seconds) {
    return new PotionView("minecraft:speed", name, level, 0x7cafc6, seconds, true);
  }
  private static TargetView target(String name, float health, List<InventorySlot> equipment) {
    return new TargetView(7, "uuid", name, health, 20, 2, 3, true, "skin", equipment);
  }

  @Test
  void InventoryReusesEquivalentSnapshotsButSeesInPlaceContentAndLayoutChanges() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<InventorySlot> input =
        new ArrayList<InventorySlot>(Collections.singletonList(item(9, "Sword", 16, 20, true)));
    List<Map<String, Object>> first = cache.inventory(s, input);
    input.set(0, item(9, "Sword", 16, 20, true));
    assertSame(first, cache.inventory(s, input));
    input.set(0, item(9, "Sword", 32, 20, true));
    List<Map<String, Object>> count = cache.inventory(s, input);
    assertNotSame(first, count);
    assertEquals(32, count.get(0).get("count"));
    input.set(0, item(9, "Renamed", 32, 80, false));
    List<Map<String, Object>> damage = cache.inventory(s, input);
    assertEquals("Renamed", damage.get(0).get("name"));
    assertEquals(20, damage.get(0).get("remainingDurability"));
    assertFalse(((InventorySlot) damage.get(0).get("slot")).enchanted);
    s.inventory.enabled = !s.inventory.enabled;
    s.inventory.counts = !s.inventory.counts;
    s.inventory.durability = !s.inventory.durability;
    assertSame(
        damage, cache.inventory(s, input), "Presentation-only flags do not change semantic data");
    s.inventory.rows = 2;
    assertEquals(18, cache.inventory(s, input).size());
    s.inventory.hotbar = true;
    assertEquals(27, cache.inventory(s, input).size());
    assertEquals(0, cache.inventory(s, input).get(18).get("slotId"));
    input.clear();
    assertFalse((Boolean) cache.inventory(s, input).get(0).get("occupied"));
    input.add(item(9, "Returned", 1, 0, false));
    assertEquals("Returned", cache.inventory(s, input).get(0).get("name"));
    assertThrows(UnsupportedOperationException.class,
        () -> cache.inventory(s, input).get(0).put("count", 99));
  }
  @Test
  void InventoryItemIdAndSlotNumberCannotHideBehindEqualCounts() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<InventorySlot> input = new ArrayList<InventorySlot>(
        Collections.singletonList(new InventorySlot(9, "minecraft:stone", "Block", 1, 0, 0)));
    List<Map<String, Object>> first = cache.inventory(s, input);
    input.set(0, new InventorySlot(9, "minecraft:dirt", "Block", 1, 0, 0));
    assertNotSame(first, cache.inventory(s, input));
    assertEquals("minecraft:dirt", cache.inventory(s, input).get(0).get("itemId"));
    input.set(0, new InventorySlot(10, "minecraft:dirt", "Block", 1, 0, 0));
    List<Map<String, Object>> moved = cache.inventory(s, input);
    assertFalse((Boolean) moved.get(0).get("occupied"));
    assertTrue((Boolean) moved.get(1).get("occupied"));
  }
  @Test
  void ArmorSeesDurabilityAndLastDuplicateSlotWhileEquivalentNewListsReuse() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    InventorySlot a = item(100, "First", 1, 10, false), b = item(100, "Last", 1, 60, true);
    List<InventorySlot> input = new ArrayList<InventorySlot>(Arrays.asList(a, b));
    List<Map<String, Object>> rows = cache.armor(s, input);
    assertEquals("Last", rows.get(0).get("name"));
    assertSame(rows,
        cache.armor(s,
            new ArrayList<InventorySlot>(
                Arrays.asList(item(100, "First", 1, 10, false), item(100, "Last", 1, 60, true)))));
    Collections.reverse(input);
    assertEquals("First", cache.armor(s, input).get(0).get("name"));
    input.set(1, item(100, "First", 1, 90, false));
    assertEquals(10, cache.armor(s, input).get(0).get("remainingDurability"));
    input.clear();
    assertFalse((Boolean) cache.armor(s, input).get(0).get("occupied"));
    assertEquals(4, cache.armor(s, input).size());
  }
  @Test
  void EffectsSeeTimerLevelAndSettingsChangesWithoutDependingOnListIdentity() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<PotionView> input =
        new ArrayList<PotionView>(Collections.singletonList(potion("Speed", 1, 82)));
    List<Map<String, Object>> first = cache.potions(s, input);
    assertEquals("Speed 2", first.get(0).get("label"));
    input.set(0, potion("Speed", 1, 82));
    assertSame(first, cache.potions(s, input));
    input.set(0, potion("Speed", 1, 81));
    assertEquals(81f, cache.potions(s, input).get(0).get("durationSeconds"));
    s.potions.level = false;
    assertEquals("Speed", cache.potions(s, input).get(0).get("label"));
    List<Map<String, Object>> hiddenLevel = cache.potions(s, input);
    s.potions.enabled = !s.potions.enabled;
    s.potions.time = false;
    assertSame(hiddenLevel, cache.potions(s, input));
    input.set(0, new PotionView("mod:other", "Other", 0, 0x123456, -1, false));
    Map<String, Object> other = cache.potions(s, input).get(0);
    assertEquals("mod:mob_effect/other", other.get("sprite"));
    assertEquals(0xff123456, other.get("color"));
    assertTrue((Boolean) other.get("infinite"));
    assertFalse((Boolean) other.get("beneficial"));
  }
  @Test
  void TargetCachesOnlySemanticScalarsAndHandlesDisappearance() {
    HudDataCache cache = new HudDataCache();
    List<Map<String, Object>> first = cache.target(
        target("Target", 14, Collections.singletonList(item(100, "Armor", 1, 10, false))));
    assertSame(first, cache.target(target("Target", 14, Collections.<InventorySlot>emptyList())),
        "Equipment is resolved separately from targetRows");
    assertEquals(3f,
        cache.target(target("Target", 3, Collections.<InventorySlot>emptyList()))
            .get(0)
            .get("health"));
    assertEquals("New",
        cache.target(target("New", 3, Collections.<InventorySlot>emptyList())).get(0).get("name"));
    assertTrue(cache.target(null).isEmpty());
    assertEquals(
        1, cache.target(target("Returned", 20, Collections.<InventorySlot>emptyList())).size());
  }
  @Test
  void CooldownsSeeFullInputOrderingSortLimitAndExpiry() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<Cooldown> input =
        new ArrayList<Cooldown>(Arrays.asList(timer("a", "Alpha", 5), timer("b", "Beta", 10)));
    List<Map<String, Object>> first = cache.cooldowns(s, input);
    assertEquals("b", first.get(0).get("key"));
    input.set(0, timer("a", "Alpha", 5));
    input.set(1, timer("b", "Beta", 10));
    assertSame(first, cache.cooldowns(s, input));
    s.hud.sort = "name";
    assertEquals("a", cache.cooldowns(s, input).get(0).get("key"));
    s.hud.maxVisible = 1;
    assertEquals(1, cache.cooldowns(s, input).size());
    input.set(0, timer("a", "Alpha", 0));
    assertEquals("b", cache.cooldowns(s, input).get(0).get("key"));
    input.set(1, new Cooldown("c", "Changed", "minecraft:shield", 7, 15));
    assertEquals("minecraft:shield", cache.cooldowns(s, input).get(0).get("itemId"));
    input.clear();
    assertTrue(cache.cooldowns(s, input).isEmpty());
    assertTrue(cache.cooldowns(s, null).isEmpty());
  }
  @Test
  void TntChangesRangeFuseDistanceAndMembershipWithoutUsingProjectionForVisibility() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<TntView> input = new ArrayList<TntView>(
        Arrays.asList(new TntView(1, 1, 2, 3, 5, false), new TntView(2, 3, 4, 2, 20, true)));
    List<Map<String, Object>> first = cache.tnts(s, input);
    assertEquals(2, first.size());
    input.set(0, new TntView(1, 1, 2, 3, 5, false));
    assertSame(first, cache.tnts(s, input));
    s.tnt.range = 10;
    assertEquals(1, cache.tnts(s, input).size());
    input.set(0, new TntView(1, 1, 2, 1, 7, false));
    assertEquals(1f, cache.tnts(s, input).get(0).get("seconds"));
    assertEquals(7f, cache.tnts(s, input).get(0).get("distance"));
    input.set(0, new TntView(1, 1, 2, 0, 7, false));
    assertTrue(cache.tnts(s, input).isEmpty());
    input.clear();
    input.add(new TntView(3, 1, 2, 2, 3, false));
    assertEquals(3, cache.tnts(s, input).get(0).get("id"));
  }
  @Test
  void CooldownRowsReuseOnlyMapsWhoseTimerSemanticsAreUnchanged() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<Cooldown> input =
        new ArrayList<Cooldown>(Arrays.asList(timer("a", "Alpha", 5), timer("b", "Beta", 10)));
    List<Map<String, Object>> first = cache.cooldowns(s, input);
    assertEquals("b", first.get(0).get("key"));
    input.set(1, timer("b", "Beta", 9));
    List<Map<String, Object>> second = cache.cooldowns(s, input);
    assertNotSame(first, second);
    assertNotSame(first.get(0), second.get(0));
    assertSame(first.get(1), second.get(1));
    assertEquals(9f, second.get(0).get("remainingSeconds"));
    input.set(1, timer("b", "Beta", 0));
    List<Map<String, Object>> expired = cache.cooldowns(s, input);
    assertEquals(1, expired.size());
    assertEquals("a", expired.get(0).get("key"));
    assertEquals(5f, expired.get(0).get("remainingSeconds"));
  }
  @Test
  void TntRowsReuseMapsWhileRangeAndFusesStaySemanticallyEqual() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<TntView> input = new ArrayList<TntView>(
        Arrays.asList(new TntView(1, 0, 0, 4, 5, false), new TntView(2, 0, 0, 2, 2, true)));
    List<Map<String, Object>> first = cache.tnts(s, input);
    assertEquals(2, first.size());
    input.set(1, new TntView(2, 0, 0, 3, 2, true));
    List<Map<String, Object>> second = cache.tnts(s, input);
    assertNotSame(first, second);
    assertNotSame(first.get(0), second.get(0));
    assertSame(first.get(1), second.get(1));
    assertEquals(3f, second.get(0).get("seconds"));
    input.set(0, new TntView(1, 0, 0, 4, 2, false));
    List<Map<String, Object>> reordered = cache.tnts(s, input);
    assertEquals(2, reordered.size());
    assertEquals(1, reordered.get(0).get("id"));
    assertEquals(2, reordered.get(1).get("id"));
  }
  @Test
  void OversizedInputIsNeverHeldOrReusedAndSmallSnapshotsRecover() {
    Settings s = new Settings();
    HudDataCache cache = new HudDataCache();
    List<PotionView> input = new ArrayList<PotionView>();
    for (int i = 0; i < 513; i++) input.add(potion("Speed " + i, 0, 10));
    List<Map<String, Object>> first = cache.potions(s, input);
    assertNotSame(first, cache.potions(s, input));
    input.subList(1, input.size()).clear();
    List<Map<String, Object>> small = cache.potions(s, input);
    assertSame(small, cache.potions(s, input));
    assertEquals(1, small.size());
  }
}
