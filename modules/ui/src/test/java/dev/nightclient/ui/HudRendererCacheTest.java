package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.ui.skin.SkinView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudRendererCacheTest {
  private static Cooldown timer(String key, String name, float remaining, float duration) {
    return new Cooldown(key, name, "minecraft:" + key, remaining, duration);
  }
  private static Settings settings() {
    Settings value = new Settings();
    value.menu.animationSpeed = 1000000f;
    return value;
  }
  private static List<String> keys(List<Cooldown> values) {
    List<String> result = new ArrayList<String>();
    for (Cooldown value : values) result.add(value.key);
    return result;
  }
  private static List<Object> rowKeys(List<Map<String, Object>> rows) {
    List<Object> result = new ArrayList<Object>();
    for (Map<String, Object> row : rows) result.add(row.get("key"));
    return result;
  }
  private static long layoutGeneration(SkinView view) throws Exception {
    Field field = SkinView.class.getDeclaredField("themeGeneration");
    field.setAccessible(true);
    return field.getLong(view);
  }

  @Test
  void visibleCooldownsOrdersFiltersCapsAndReadsSettingsEveryCall() {
    Settings s = settings();
    List<Cooldown> input = new ArrayList<Cooldown>(Arrays.asList(timer("alpha", "Alpha", 5, 20),
        timer("beta", "Beta", 10, 10), timer("gamma", "Gamma", 10, 30),
        timer("zero", "Zero", 0, 20), timer("infinite", "Infinite", Float.NaN, 20)));
    assertEquals(Arrays.asList("beta", "gamma", "alpha"),
        keys(HudRenderer.visibleCooldowns(s, input, 1, 1)));
    s.hud.sort = "name";
    assertEquals(Arrays.asList("alpha", "beta", "gamma"),
        keys(HudRenderer.visibleCooldowns(s, input, 1, 1)));
    s.hud.sort = "duration";
    assertEquals(Arrays.asList("beta", "alpha", "gamma"),
        keys(HudRenderer.visibleCooldowns(s, input, 1, 1)));
    s.hud.sort = "remaining";
    s.hud.maxVisible = 2;
    assertEquals(
        Arrays.asList("beta", "gamma"), keys(HudRenderer.visibleCooldowns(s, input, 1, 1)));
    assertTrue(HudRenderer.visibleCooldowns(s, null, 1, 1).isEmpty());
    assertTrue(HudRenderer.visibleCooldowns(s, Collections.<Cooldown>emptyList(), 1, 1).isEmpty());
  }

  @Test
  void visibleCooldownsReusesSemanticallyEqualSnapshotsAndSeesInPlaceEdits() {
    Settings s = settings();
    List<Cooldown> input = new ArrayList<Cooldown>(
        Arrays.asList(timer("alpha", "Alpha", 5, 20), timer("beta", "Beta", 10, 10)));
    List<Cooldown> first = HudRenderer.visibleCooldowns(s, input, 1, 1);
    assertSame(first, HudRenderer.visibleCooldowns(s, input, 900, 600));
    assertSame(first, HudRenderer.visibleCooldowns(s, new ArrayList<Cooldown>(input), 42, 7));
    s.hud.maxVisible = 1;
    List<Cooldown> capped = HudRenderer.visibleCooldowns(s, input, 1, 1);
    assertNotSame(first, capped);
    assertEquals(Collections.singletonList("beta"), keys(capped));
    s.hud.maxVisible = 8;
    input.set(0, timer("alpha", "Alpha", 12, 20));
    List<Cooldown> changed = HudRenderer.visibleCooldowns(s, input, 1, 1);
    assertNotSame(first, changed);
    assertEquals(Arrays.asList("alpha", "beta"), keys(changed));
    Collections.reverse(input);
    assertEquals(
        Arrays.asList("alpha", "beta"), keys(HudRenderer.visibleCooldowns(s, input, 1, 1)));
    assertThrows(
        UnsupportedOperationException.class, () -> changed.add(timer("gamma", "Gamma", 1, 1)));
  }

  @Test
  void rowsReuseUnchangedMapsAndKeepReadOnlySemantics() {
    Settings s = settings();
    List<Cooldown> input = new ArrayList<Cooldown>(Arrays.asList(timer("alpha", "Alpha", 10, 20),
        timer("beta", "Beta", 5, 10), timer("gamma", "Gamma", 1, 10)));
    List<Map<String, Object>> first = HudRenderer.rows(s, input);
    assertEquals(Arrays.asList("alpha", "beta", "gamma"), rowKeys(first));
    assertEquals(0.5f, first.get(0).get("fraction"));
    assertEquals(20f, first.get(0).get("durationSeconds"));
    assertEquals("minecraft:alpha", first.get(0).get("itemId"));
    assertThrows(UnsupportedOperationException.class,
        () -> first.add(Collections.<String, Object>emptyMap()));
    assertThrows(UnsupportedOperationException.class, () -> first.get(0).put("key", "changed"));
    assertSame(first, HudRenderer.rows(s, new ArrayList<Cooldown>(input)));

    input.set(1, timer("beta", "Beta", 4, 10));
    List<Map<String, Object>> second = HudRenderer.rows(s, input);
    assertNotSame(first, second);
    assertSame(first.get(0), second.get(0));
    assertSame(first.get(2), second.get(2));
    assertNotSame(first.get(1), second.get(1));
    assertEquals(4f, second.get(1).get("remainingSeconds"));

    s.hud.maxVisible = 2;
    List<Map<String, Object>> capped = HudRenderer.rows(s, input);
    assertEquals(2, capped.size());
    assertSame(first.get(0), capped.get(0));
    assertEquals("beta", capped.get(1).get("key"));
  }

  @Test
  void tntRowsReuseUnchangedMapsAndRecomputeFilteringAndOrder() {
    Settings s = settings();
    List<TntView> input =
        new ArrayList<TntView>(Arrays.asList(new TntView(2, 0, 0, 7, 7), new TntView(1, 0, 0, 4, 4),
            new TntView(3, 0, 0, 3, 4), new TntView(4, 0, 0, 0, 3), new TntView(5, 0, 0, 2, 100)));
    List<Map<String, Object>> first = FeatureHudRenderer.tntRows(s, input);
    assertEquals(Arrays.asList(1, 3, 2), ids(first));
    assertSame(first, FeatureHudRenderer.tntRows(s, new ArrayList<TntView>(input)));
    assertEquals(3f, FeatureHudRenderer.tntBounds(s, input, 1000, 700, false)[5]);

    input.set(0, new TntView(2, 0, 0, 6, 7));
    List<Map<String, Object>> second = FeatureHudRenderer.tntRows(s, input);
    assertNotSame(first, second);
    assertSame(first.get(0), second.get(0));
    assertSame(first.get(1), second.get(1));
    assertNotSame(first.get(2), second.get(2));
    assertEquals(6f, second.get(2).get("seconds"));

    input.set(4, new TntView(5, 0, 0, 2, 10));
    s.tnt.range = 10;
    assertEquals(Arrays.asList(1, 3, 2, 5), ids(FeatureHudRenderer.tntRows(s, input)));
    assertTrue(FeatureHudRenderer.tntRows(s, Collections.<TntView>emptyList()).isEmpty());
  }
  private static List<Integer> ids(List<Map<String, Object>> rows) {
    List<Integer> result = new ArrayList<Integer>();
    for (Map<String, Object> row : rows) result.add((Integer) row.get("id"));
    return result;
  }

  @Test
  void settersInvalidateLayoutOnlyForSemanticChanges() throws Exception {
    Settings s = settings();
    SkinView view = HudRenderer.view(s);
    long initial = layoutGeneration(view);
    List<Cooldown> cooldowns = new ArrayList<Cooldown>(
        Arrays.asList(timer("alpha", "Alpha", 5, 10), timer("beta", "Beta", 10, 20)));
    HudRenderer.cooldowns(s, cooldowns);
    long afterData = layoutGeneration(view);
    assertEquals(initial + 1, afterData);
    HudRenderer.cooldowns(s, new ArrayList<Cooldown>(cooldowns));
    assertEquals(
        afterData, layoutGeneration(view), "Equal timers must not invalidate prepared layout");
    cooldowns.set(0, timer("alpha", "Alpha", 6, 10));
    HudRenderer.cooldowns(s, cooldowns);
    long afterChange = layoutGeneration(view);
    assertEquals(afterData + 1, afterChange);
    HudRenderer.cooldowns(s, null);
    HudRenderer.cooldowns(s, Collections.<Cooldown>emptyList());
    assertEquals(
        afterChange + 1, layoutGeneration(view), "Null and empty mean the same empty snapshot");

    List<InventorySlot> armor = new ArrayList<InventorySlot>(Collections.singletonList(
        new InventorySlot(100, "minecraft:diamond_helmet", "Helmet", 1, 10, 100)));
    TargetView target = new TargetView(7, "uuid", "Target", 14, 20, 2, 3, true, "", armor);
    HudRenderer.target(s, target);
    long afterTarget = layoutGeneration(view);
    assertTrue(afterTarget > afterChange);
    HudRenderer.target(s,
        new TargetView(
            7, "uuid", "Target", 14, 20, 2, 3, true, "", new ArrayList<InventorySlot>(armor)));
    assertEquals(afterTarget, layoutGeneration(view),
        "Equivalent target equipment must not invalidate layout");
    armor.set(0, new InventorySlot(100, "minecraft:netherite_helmet", "Helmet", 1, 90, 100));
    HudRenderer.target(s, new TargetView(7, "uuid", "Target", 14, 20, 2, 3, true, "", armor));
    assertEquals(afterTarget + 1, layoutGeneration(view),
        "Target equipment changes affect prepared geometry");
  }

  @Test
  void hideWhenEmptyOnlySuppressesTheEmptyCooldownPanel() {
    Settings hidden = settings();
    hidden.hud.hideWhenEmpty = true;
    String cooldownsTitle =
        dev.nightclient.i18n.I18n.text(hidden.language, "hud.cooldowns.title");
    HudPresentationTest.Sink emptyHidden = new HudPresentationTest.Sink();
    HudRenderer.render(emptyHidden, hidden, Collections.<Cooldown>emptyList(), 900, 600, 0, false);
    assertFalse(texts(emptyHidden).contains(cooldownsTitle));
    Settings shown = settings();
    shown.hud.hideWhenEmpty = false;
    HudPresentationTest.Sink emptyShown = new HudPresentationTest.Sink();
    HudRenderer.render(emptyShown, shown, Collections.<Cooldown>emptyList(), 900, 600, 0, false);
    assertTrue(texts(emptyShown).contains(cooldownsTitle));
    Settings values = settings();
    HudPresentationTest.Sink rows = new HudPresentationTest.Sink();
    HudRenderer.render(rows, values, Collections.singletonList(timer("alpha", "Alpha", 5, 10)), 900,
        600, 0, false);
    assertTrue(texts(rows).contains(cooldownsTitle));
  }
  private static List<String> texts(HudPresentationTest.Sink sink) {
    List<String> result = new ArrayList<String>();
    for (HudPresentationTest.Text text : sink.texts) result.add(text.value);
    return result;
  }

  @Test
  void durationFormattingKeepsItsExactBoundaries() {
    assertEquals("9.9s", HudRenderer.cooldownDuration(9.94f, "en"));
    assertEquals("9.9s", HudRenderer.cooldownDuration(9.95f, "en"));
    assertEquals("60s", HudRenderer.cooldownDuration(59.4f, "en"));
    assertEquals("10s", HudRenderer.cooldownDuration(10, "en"));
    assertEquals("1:00", HudRenderer.cooldownDuration(60, "en"));
    assertEquals("200:01", HudRenderer.cooldownDuration(12000.9f, "en"));
    assertEquals("∞", HudRenderer.cooldownDuration(-1, "en"));
    assertEquals("7s", HudRenderer.effectDuration(7.9f, "en"));
    assertEquals("1:00", HudRenderer.effectDuration(60.9f, "en"));
    assertEquals("1:00:00", HudRenderer.effectDuration(3600, "en"));
    assertEquals("**:**", HudRenderer.effectDuration(Float.NaN, "en"));
    assertEquals("0:05", HudRenderer.duration(5, "en", true));
    assertEquals("1:01", HudRenderer.duration(60.1f, "en", false));
    assertEquals("2:03", HudRenderer.duration(123, "en", false));
    assertEquals("1.0h", HudRenderer.duration(3600, "en", false));
    assertEquals("1.0d", HudRenderer.duration(86400, "en", false));
    assertEquals("∞", HudRenderer.duration(-1, "en", false));
  }
}
