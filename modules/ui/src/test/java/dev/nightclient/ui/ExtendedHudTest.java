package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.*;
import dev.nightclient.config.Settings;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ExtendedHudTest {
  private static Settings settings() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    s.armor.enabled = s.potions.enabled = s.target.enabled = s.tnt.enabled = true;
    return s;
  }
  private static float[] bounds(
      Settings s, Settings.Panel panel, int width, int height, float naturalHeight) {
    String id = panel instanceof Settings.Armor ? "armor"
        : panel instanceof Settings.Potions     ? "potions"
                                                : "target";
    return HudRenderer.widgetBounds(s, id, width, height, true);
  }
  private static float defaultFit(int width, int height) {
    return Math.max(.25f, Math.min(1, Math.min((width - 24) / 936f, (height - 20) / 520f)));
  }
  @Test
  void noWorldDataDoesNotInventItemsTargetsOrFood() {
    Settings s = settings();
    HudPresentationTest.Sink c = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderArmor(c, s, Collections.emptyList(), 640, 400);
    ExtendedHudRenderer.renderFood(c, s, VitalsView.EMPTY, 640, 400);
    ExtendedHudRenderer.renderTarget(c, s, null, 640, 400);
    assertTrue(c.itemAlpha.isEmpty());
    assertTrue(c.texts.stream().noneMatch(
        t -> t.value.contains("Opponent") || t.value.contains("Player")));
    assertEquals(0, c.clips);
  }
  @Test
  void backgroundOpacityNeverFadesRealFoodTextAndArmorItems() {
    Settings s = settings();
    s.food.enabled = true;
    s.theme.backgroundOpacity = .05f;
    HudPresentationTest.Sink c = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderArmor(c, s,
        Arrays.asList(new InventorySlot(103, "minecraft:diamond_helmet", "Helmet", 1, 50, 100)),
        640, 400);
    ExtendedHudRenderer.renderFood(c, s, new VitalsView(18, 8.5f, true), 640, 400);
    assertEquals(Collections.singletonList(1f), c.itemAlpha);
    assertFalse(c.texts.isEmpty());
    for (HudPresentationTest.Text text : c.texts) assertEquals(255, text.color >>> 24);
    assertEquals(0, c.clips);
  }
  @Test
  void smallPotionPanelKeepsAllRowsWithoutClipping() {
    Settings s = settings();
    s.potions.height = 46;
    HudPresentationTest.Sink c = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderPotions(c, s,
        Arrays.asList(new PotionView("a", "Speed", 1, 0xff44aa, 90, true),
            new PotionView("b", "Poison", 0, 0x11ff44, 2, false)),
        640, 400);
    assertTrue(c.texts.stream().anyMatch(t -> t.value.equals("1:30")));
    assertTrue(c.texts.stream().anyMatch(t -> t.value.equals("Poison")));
    assertEquals(0, c.clips);
  }
  @Test
  void defaultLeftColumnKeepsItsStackedFallbackPlacementWhenTheViewportHasRoom() {
    Settings s = settings();
    int[][] viewports = {{1600, 1000}, {900, 600}, {640, 400}};
    java.util.List<TntView> fullTnt = Arrays.asList(
        new TntView(1, 0, 0, 3, 1), new TntView(2, 0, 0, 3, 2), new TntView(3, 0, 0, 3, 3));
    for (int[] viewport : viewports)
      for (java.util.List<TntView> tnt : Arrays.asList(Collections.<TntView>emptyList(), fullTnt)) {
        HudSceneActions scene = new HudSceneActions(Collections.<Cooldown>emptyList(), tnt, false);
        dev.nightclient.ui.skin.SkinView view = new dev.nightclient.ui.skin.SkinView(
            dev.nightclient.skin.DefaultSkin.document(), s, scene);
        view.renderHudEditor(new HudPresentationTest.Sink(), viewport[0], viewport[1], 1);
        float[] upper = view.hudBounds("tnt", viewport[0], viewport[1], 1, true);
        float[] lower = view.hudBounds("potions", viewport[0], viewport[1], 1, true);
        float[] armor = view.hudBounds("armor", viewport[0], viewport[1], 1, true);
        assertTrue(upper[1] + upper[3] < lower[1], "TNT must end before Potions starts");
        assertTrue(lower[1] + lower[3] < armor[1], "Potions must leave room for default Armor");
        for (float[] panel : new float[][] {upper, lower}) {
          assertTrue(panel[0] >= 6 && panel[1] >= 6);
          assertTrue(panel[0] + panel[2] <= viewport[0] - 6 + .001f);
          assertTrue(panel[1] + panel[3] <= viewport[1] - 6 + .001f);
        }
        assertEquals(upper[4], lower[4], .001f);
      }
    assertEquals(90, s.tnt.offsetY);
    assertEquals(196, s.potions.offsetY);
    assertEquals(1, s.tnt.scale);
    assertEquals(1, s.potions.scale);
  }
  @Test
  void fullSizeDefaultColumnKeepsItsTopWhileTheStackFollowsActualLiveRows() {
    Settings s = settings();
    for (java.util.List<TntView> rows : Arrays.asList(Collections.<TntView>emptyList(),
             Arrays.asList(new TntView(1, 0, 0, 3, 1), new TntView(2, 0, 0, 3, 2),
                 new TntView(3, 0, 0, 3, 3)))) {
      HudSceneActions scene = new HudSceneActions(Collections.<Cooldown>emptyList(), rows, false);
      dev.nightclient.ui.skin.SkinView view = new dev.nightclient.ui.skin.SkinView(
          dev.nightclient.skin.DefaultSkin.document(), s, scene);
      view.renderHudEditor(new HudPresentationTest.Sink(), 1000, 700, 1);
      float[] tnt = view.hudBounds("tnt", 1000, 700, 1, true),
              potions = view.hudBounds("potions", 1000, 700, 1, true);
      assertArrayEquals(
          new float[] {10, 42, 168, rows.isEmpty() ? 28 : 28 + 18 * rows.size() + 4, 1}, tnt,
          .001f);
      assertEquals(10, potions[0]);
      assertEquals(tnt[1] + tnt[3] + 8, potions[1], .001f);
      assertEquals(tnt[4], potions[4], .001f);
    }
  }
  @Test
  void untouchedDefaultWidgetsFitSmallViewportsWithoutMutatingConfiguredScales() {
    Settings s = settings();
    java.util.List<Cooldown> cooldowns = new java.util.ArrayList<Cooldown>();
    for (int i = 0; i < 8; i++)
      cooldowns.add(new Cooldown("c" + i, "Shield", "minecraft:shield", 5 + i, 20));
    java.util.List<TntView> tnts = Arrays.asList(
        new TntView(1, 0, 0, 3, 1), new TntView(2, 0, 0, 3, 2), new TntView(3, 0, 0, 3, 3));
    String[] ids = {"cooldowns", "tnt", "potions", "armor", "inventory", "target"};
    for (int[] viewport : new int[][] {{1000, 700}, {640, 400}, {320, 200}})
      for (boolean live : new boolean[] {false, true}) {
        int width = viewport[0], height = viewport[1];
        HudSceneActions scene =
            new HudSceneActions(live ? cooldowns : Collections.<Cooldown>emptyList(),
                live ? tnts : Collections.<TntView>emptyList(), live);
        dev.nightclient.ui.skin.SkinView view = new dev.nightclient.ui.skin.SkinView(
            dev.nightclient.skin.DefaultSkin.document(), s, scene);
        view.renderHudEditor(new HudPresentationTest.Sink(), width, height, 1);
        for (String id : ids) {
          float[] b = view.hudBounds(id, width, height, 1, true);
          assertTrue(b[0] >= 6 && b[1] >= 6, id);
          assertTrue(b[0] + b[2] <= width - 6 + .001f
                  && b[1] + b[3] <= height - ("armor".equals(id) ? 0 : 6) + .001f,
              id);
          assertEquals("armor".equals(id) ? 1 : defaultFit(width, height), b[4], .001f,
              id + " uses the shared automatic fit, except native-scale armor");
        }
        float[] target = view.hudBounds("target", width, height, 1, true);
        assertEquals(134 * defaultFit(width, height), target[2], .001f);
        assertEquals(46 * defaultFit(width, height), target[3], .001f);
        for (int a = 0; a < ids.length; a++)
          for (int b = a + 1; b < ids.length; b++) {
            float[] first = view.hudBounds(ids[a], width, height, 1, true),
                    second = view.hudBounds(ids[b], width, height, 1, true);
            float ix = Math.min(first[0] + first[2], second[0] + second[2])
                - Math.max(first[0], second[0]);
            float iy = Math.min(first[1] + first[3], second[1] + second[3])
                - Math.max(first[1], second[1]);
            assertFalse(ix > .5f && iy > .5f,
                ids[a] + " overlaps " + ids[b] + " at " + width + "x" + height);
          }
      }
    assertEquals(1, s.hud.scale);
    assertEquals(1, s.inventory.scale);
    assertEquals(1, s.target.scale);
  }
  @Test
  void onlyAnOversizedWidgetUsesItsOwnViewportLimit() {
    Settings s = settings();
    s.inventory.width = 600;
    s.inventory.offsetX = -21;
    HudSceneActions scene = new HudSceneActions(
        Collections.<Cooldown>emptyList(), Collections.<TntView>emptyList(), false);
    dev.nightclient.ui.skin.SkinView view =
        new dev.nightclient.ui.skin.SkinView(dev.nightclient.skin.DefaultSkin.document(), s, scene);
    view.renderHudEditor(new HudPresentationTest.Sink(), 320, 200, 1);
    float[] inventory = view.hudBounds("inventory", 320, 200, 1, true),
            target = view.hudBounds("target", 320, 200, 1, true);
    assertEquals(304f / 600, inventory[4], .001f);
    assertEquals(304, inventory[2], .001f);
    assertEquals(defaultFit(320, 200), target[4], .001f);
    assertEquals(134 * defaultFit(320, 200), target[2], .001f);
    assertEquals(1, s.inventory.scale);
    assertEquals(600, s.inventory.width);
  }
  static final class HudSceneActions implements UiActions {
    final java.util.List<Cooldown> timers;
    final java.util.List<TntView> explosives;
    final java.util.List<PotionView> effects;
    final boolean live;
    boolean leftArm;
    HudSceneActions(
        java.util.List<Cooldown> timers, java.util.List<TntView> explosives, boolean live) {
      this.timers = timers;
      this.explosives = explosives;
      this.live = live;
      effects = new java.util.ArrayList<PotionView>();
      if (live)
        for (int i = 0; i < 9; i++)
          effects.add(
              new PotionView("minecraft:speed" + i, "Speed " + i, 0, 0xffffff, 120 + i, true));
    }
    public java.util.List<Cooldown> cooldowns() {
      return timers;
    }
    public java.util.List<TntView> tnts() {
      return explosives;
    }
    public java.util.List<PotionView> potions() {
      return effects;
    }
    public boolean leftMainArm() {
      return leftArm;
    }
    public TargetView target() {
      return live ? new TargetView(1, "uuid", "Player", 20, 20, 0, 2, true) : null;
    }
    public void changed() {}
    public String clipboard() {
      return "";
    }
    public void clipboard(String value) {}
    public java.util.List<String> profiles() {
      return Collections.emptyList();
    }
    public void saveProfile(String name) {}
    public void loadProfile(String name) {}
    public void deleteProfile(String name) {}
    public String exportProfile() {
      return "{}";
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
      return "test";
    }
    public String saveStatus() {
      return "";
    }
  }
  @Test
  void defaultArmorFollowsTheHandednessOffsetAndPreservesCustomPlacement() {
    Settings s = settings();
    HudSceneActions scene = new HudSceneActions(
        Collections.<Cooldown>emptyList(), Collections.<TntView>emptyList(), false);
    dev.nightclient.ui.skin.SkinView view =
        new dev.nightclient.ui.skin.SkinView(dev.nightclient.skin.DefaultSkin.document(), s, scene);
    view.renderHudEditor(new HudPresentationTest.Sink(), 640, 400, 1);
    float[] right = view.hudBounds("armor", 640, 400, 1, true);
    assertArrayEquals(new float[] {419, 361, 96, 31, 1}, right, .001f);
    scene.leftArm = true;
    view.invalidateLayout();
    final java.util.List<float[]> strips = new java.util.ArrayList<float[]>();
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
      @Override
      public void gameSprite(String id, float x, float y, float w, float h, float u, float v,
          float rw, float rh, float sw, float sh, int color) {
        if ("minecraft:hud/hotbar".equals(id)) strips.add(new float[] {x, y, w, h, u});
      }
    };
    view.renderHudEditor(sink, 640, 400, 2);
    float[] left = view.hudBounds("armor", 640, 400, 2, true);
    assertEquals(right[0] + 28, left[0], .001f);
    assertTrue(strips.isEmpty(), "Rounded theme cells own the armor presentation");
    s.armor.offsetX = 31;
    view.invalidateLayout();
    view.renderHudEditor(new HudPresentationTest.Sink(), 640, 400, 3);
    assertEquals(31, view.hudBounds("armor", 640, 400, 3, true)[0], .001f,
        "Handedness must not move a manually placed panel");
  }
  @Test
  void customFooterAndTargetLayoutsRetainTheirConfiguredGeometry() {
    Settings s = settings();
    s.armor.offsetX = 27;
    s.inventory.offsetX = -31;
    s.target.offsetY = 35;
    float[] armor = bounds(s, s.armor, 640, 400, 50),
            food = ExtendedHudRenderer.foodBounds(s, 640, 400);
    float[] inventory = FeatureHudRenderer.inventoryBounds(s, 640, 400),
            target = ExtendedHudRenderer.targetBounds(s, 640, 400);
    assertEquals(27, armor[0]);
    assertEquals(96, armor[2], .001f);
    assertEquals(330, food[0]);
    assertEquals(351, food[1]);
    assertEquals(81, food[2]);
    assertEquals(640 - inventory[2] - 31, inventory[0], .001f);
    assertEquals(s.inventory.width, inventory[2], .001f);
    assertEquals((640 - target[2]) * .5f, target[0], .001f);
    assertEquals((400 - target[3]) * .5f + 35, target[1], .001f);
    s.hud.offsetY = 17;
    s.hud.width = 159;
    float[] cooldown = HudRenderer.bounds(s, Collections.<Cooldown>emptyList(), 640, 400, true);
    assertEquals(640 - s.hud.width - 20, cooldown[0], .001f);
    assertEquals(17, cooldown[1]);
    assertEquals(s.hud.width, cooldown[2], .001f);
    assertEquals(27, s.armor.offsetX);
    assertEquals(-31, s.inventory.offsetX);
    assertEquals(35, s.target.offsetY);
  }
  @Test
  void customPanelPositionsAndSizesBypassDefaultColumnFitting() {
    Settings s = settings();
    s.tnt.offsetX = 44;
    s.tnt.offsetY = 38;
    s.tnt.width = 132;
    s.tnt.height = 96;
    s.tnt.scale = 1.25f;
    s.potions.anchorX = 1;
    s.potions.offsetX = -17;
    s.potions.offsetY = 43;
    s.potions.width = 126;
    s.potions.height = 100;
    s.potions.scale = .75f;
    s.potions.layoutOrder = 1;
    float[] tnt = FeatureHudRenderer.tntBounds(s, Collections.<TntView>emptyList(), 640, 400, true);
    float[] potions = bounds(s, s.potions, 640, 400, 76);
    assertEquals(44, tnt[0]);
    assertEquals(38, tnt[1]);
    assertEquals(165, tnt[2]);
    assertEquals(1.25f, tnt[4]);
    assertEquals(640 - 102 - 17, potions[0]);
    assertEquals(43, potions[1]);
    assertEquals(102, potions[2]);
    assertEquals(.75f, potions[4]);
    assertEquals(38, s.tnt.offsetY);
    assertEquals(43, s.potions.offsetY);
    assertEquals(1.25f, s.tnt.scale);
    assertEquals(.75f, s.potions.scale);
  }
  @Test
  void zakoModeKeepsNativePositionAndUsesGreenHighHealthColor() {
    Settings s = settings();
    s.target.style = "zakohp";
    float[] health = {5, 10, 15, 20, 21};
    int[] colors = {0xfffca5a5, 0xfffde68a, 0xff55ff55, 0xff55ff55, 0xff55ff55};
    for (int i = 0; i < health.length; i++) {
      NativeFontSink c = new NativeFontSink();
      ExtendedHudRenderer.renderTarget(
          c, s, new TargetView(1, "uuid", "Player", health[i], 20, 0, 3, true), 640, 400);
      assertEquals(1, c.texts.size());
      HudPresentationTest.Text t = c.texts.get(0);
      assertEquals(colors[i], t.color);
      assertEquals(health[i] < 10 ? 208 : 204, t.y);
      assertEquals(health[i] < 10 ? 18 : 9, t.size);
      assertEquals(314, t.x, .001f);
      assertTrue(c.shadow);
    }
  }
  private static final class NativeFontSink extends HudPresentationTest.Sink {
    boolean shadow;
    @Override
    public float gameTextWidth(String value, float scale) {
      return value.length() * 6 * scale;
    }
    @Override
    public void gameText(String value, float x, float y, float scale, int color, boolean shadow) {
      this.shadow = shadow;
      text(value, x, y, 9 * scale, color, false);
    }
  }
  @Test
  void saturationIsAnAdditionalFoodRowAboveVanillaHungerWithoutACard() {
    Settings s = settings();
    s.food.enabled = true;
    HudPresentationTest.Sink full = new HudPresentationTest.Sink(),
                             empty = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderFood(full, s, new VitalsView(13, 20, true), 640, 400);
    ExtendedHudRenderer.renderFood(empty, s, new VitalsView(13, 0, true), 640, 400);
    float[] b = ExtendedHudRenderer.foodBounds(s, 640, 400);
    assertArrayEquals(new float[] {330, 351, 81, 10, 1}, b);
    assertTrue(full.texts.isEmpty());
    assertTrue(full.fillColors.isEmpty());
    assertEquals(0, full.maxRadius);
    assertEquals(10, full.hungerIcons.size());
    assertEquals(10, empty.hungerIcons.size());
    for (int i = 0; i < 10; i++) {
      HudPresentationTest.HungerIcon icon = full.hungerIcons.get(i);
      assertEquals(402 - i * 8, icon.x);
      assertEquals(351, icon.y);
      assertEquals(9, icon.size);
      assertEquals(2, icon.fill);
      assertEquals(0, empty.hungerIcons.get(i).fill);
    }
    s.theme.backgroundOpacity = 0;
    HudPresentationTest.Sink transparent = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderFood(transparent, s, new VitalsView(13, 20, true), 640, 400);
    assertEquals(10, transparent.hungerIcons.size());
    for (HudPresentationTest.HungerIcon icon : transparent.hungerIcons) assertEquals(2, icon.fill);
    assertEquals(0, transparent.clips);
    HudPresentationTest.Sink creative = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderFood(creative, s, new VitalsView(20, 5, true, false), 640, 400);
    assertTrue(creative.hungerIcons.isEmpty());
  }
  @Test
  void saturationUsesVanillaHalfFullAndEmptySpritesOnTheExactOddViewportPixelGrid() {
    Settings s = settings();
    s.food.enabled = true;
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink();
    ExtendedHudRenderer.renderFood(sink, s, new VitalsView(13, 4.5f, true), 641, 403);
    assertArrayEquals(
        new float[] {330, 354, 81, 10, 1}, ExtendedHudRenderer.foodBounds(s, 641, 403));
    int[] fills = {2, 2, 1, 0, 0, 0, 0, 0, 0, 0};
    for (int i = 0; i < 10; i++) {
      HudPresentationTest.HungerIcon icon = sink.hungerIcons.get(i);
      assertEquals(fills[i], icon.fill);
      assertEquals(402 - i * 8, icon.x);
      assertEquals(354, icon.y);
      assertEquals(9, icon.size);
    }
  }
  @Test
  void saturationHasNoMovablePanelAndNeverAppearsInTheHudEditor() {
    Settings s = settings();
    s.food.enabled = true;
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink();
    ExtendedHudRenderer.render(sink, s, Collections.<InventorySlot>emptyList(),
        Collections.<PotionView>emptyList(), new VitalsView(13, 4.5f, true), null, 640, 400, 0,
        true, null);
    assertTrue(sink.hungerIcons.isEmpty());
    s.food.enabled = false;
    ExtendedHudRenderer.renderFood(sink, s, new VitalsView(13, 4.5f, true), 640, 400);
    assertTrue(sink.hungerIcons.isEmpty());
    s.food.enabled = true;
    s.menu.scale = 2;
    s.hud.scale = 3;
    s.theme.backgroundOpacity = 0;
    ExtendedHudRenderer.renderFood(sink, s, new VitalsView(13, 4.5f, true), 640, 400);
    assertEquals(10, sink.hungerIcons.size());
    for (HudPresentationTest.HungerIcon icon : sink.hungerIcons) {
      assertEquals(351, icon.y);
      assertEquals(9, icon.size);
    }
    assertEquals(1, Settings.Food.class.getFields().length);
    assertEquals("enabled", Settings.Food.class.getFields()[0].getName());
  }
  @Test
  void hiddenArmorRowsCollapseTheirPanelAndEditorBounds() {
    Settings s = settings();
    s.armor.offsetX = 27;
    assertEquals(31, bounds(s, s.armor, 640, 400, 50)[3], .001f);
    s.armor.durability = false;
    float[] hiddenDurability = bounds(s, s.armor, 640, 400, 50);
    assertEquals(22, hiddenDurability[3], .001f);
    s.armor.percent = false;
    float[] icons = bounds(s, s.armor, 640, 400, 50);
    assertArrayEquals(hiddenDurability, icons);
    s.armor.durability = true;
    assertEquals(31, bounds(s, s.armor, 640, 400, 50)[3], .001f);
    s.armor.horizontal = false;
    s.armor.durability = false;
    assertEquals(24f, bounds(s, s.armor, 640, 400, 180)[2], .001f);
    s.armor.percent = true;
    assertEquals(24f, bounds(s, s.armor, 640, 400, 180)[2], .001f);
    s.armor.durability = true;
    assertEquals(58f, bounds(s, s.armor, 640, 400, 180)[2], .001f);
    s.armor.names = true;
    assertEquals(128, bounds(s, s.armor, 640, 400, 180)[2], .001f);
    s.armor.width = 400;
    s.armor.height = 300;
    assertEquals(400, bounds(s, s.armor, 640, 400, 180)[2], .001f,
        "The standalone theme honors the configured vertical width");
  }
  @Test
  void disablingDurabilityHidesItsPersistedPercentInBothOrientations() {
    java.util.List<InventorySlot> slots = Collections.singletonList(
        new InventorySlot(100, "minecraft:iron_helmet", "Helmet", 1, 50, 100));
    for (boolean horizontal : new boolean[] {true, false}) {
      Settings s = settings();
      s.armor.horizontal = horizontal;
      s.armor.durability = false;
      s.armor.percent = true;
      float[] hidden = bounds(s, s.armor, 640, 400, 180);
      HudPresentationTest.Sink canvas = new HudPresentationTest.Sink();
      ExtendedHudRenderer.renderArmor(canvas, s, slots, 640, 400);
      assertTrue(canvas.texts.stream().noneMatch(t -> t.value.contains("%")));
      assertEquals(1, canvas.itemAlpha.size());
      assertEquals(
          horizontal ? 22 : 24f, (horizontal ? hidden[3] : hidden[2]) / hidden[4], .001f);
      s.armor.percent = false;
      assertArrayEquals(hidden, bounds(s, s.armor, 640, 400, 180));
      s.armor.percent = true;
      s.armor.durability = true;
      HudPresentationTest.Sink enabled = new HudPresentationTest.Sink();
      ExtendedHudRenderer.renderArmor(enabled, s, slots, 640, 400);
      assertTrue(enabled.texts.stream().anyMatch(text -> text.value.equals("50%")));
      assertTrue(s.armor.percent);
    }
  }
  @Test
  void durabilityPositionMovesTheValueAndBarInBothArmorStyles() {
    java.util.List<InventorySlot> slots = Collections.singletonList(
        new InventorySlot(100, "minecraft:iron_helmet", "Cap", 1, 50, 100));
    for (String style : new String[] {"visual", "minecraft"}) {
      for (boolean horizontal : new boolean[] {true, false}) {
        for (String position : new String[] {"below", "above"}) {
          Settings s = settings();
          s.armor.style = style;
          s.armor.horizontal = horizontal;
          s.armor.durabilityStyle = "bar";
          s.armor.names = true;
          s.armor.percent = true;
          s.armor.durabilityPosition = position;
          float[] item = new float[2];
          java.util.List<Float> bars = new java.util.ArrayList<Float>();
          HudPresentationTest.Sink canvas = new HudPresentationTest.Sink() {
            @Override
            public void inventoryItem(
                InventorySlot slot, float x, float y, float size, float alpha) {
              item[0] = y;
              item[1] = y + size;
            }

            @Override
            public void roundedRect(float x, float y, float w, float h, float r, int c) {
              if (Math.abs(h - 1) < .001f) bars.add(y);
              super.roundedRect(x, y, w, h, r, c);
            }
          };
          ExtendedHudRenderer.renderArmor(canvas, s, slots, 640, 400);
          HudPresentationTest.Text value = canvas.texts.stream()
              .filter(t -> "50%".equals(t.value))
              .findFirst()
              .orElseThrow(AssertionError::new);
          HudPresentationTest.Text name = canvas.texts.stream()
              .filter(t -> "Cap".equals(t.value))
              .findFirst()
              .orElseThrow(AssertionError::new);
          assertEquals(2, bars.size());
          if (horizontal) {
            if ("above".equals(position)) {
              assertTrue(value.y + value.size <= item[0]);
              assertTrue(bars.stream().allMatch(y -> y + 1 <= item[0]));
            } else {
              assertTrue(bars.stream().allMatch(y -> y >= item[1]));
              assertTrue(value.y >= item[1]);
            }
          } else {
            if ("above".equals(position)) {
              assertTrue(bars.stream().allMatch(y -> y < value.y));
              assertTrue(value.y < name.y);
            } else {
              assertTrue(name.y < value.y);
              assertTrue(bars.stream().allMatch(y -> y > value.y));
            }
          }
        }
      }
    }
  }
  @Test
  void vanillaDurabilityStyleRendersCompactInsideSlotBar() {
    java.util.List<InventorySlot> slots = Collections.singletonList(
        new InventorySlot(100, "minecraft:diamond_chestplate", "Chest", 1, 100, 400));
    for (boolean horizontal : new boolean[] {true, false}) {
      Settings s = settings();
      s.armor.style = "minecraft";
      s.armor.horizontal = horizontal;
      s.armor.durability = true;
      s.armor.durabilityStyle = "auto";
      s.armor.names = false;
      float[] item = new float[3];
      java.util.List<float[]> rects = new java.util.ArrayList<float[]>();
      HudPresentationTest.Sink canvas = new HudPresentationTest.Sink() {
        @Override
        public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
          item[0] = x;
          item[1] = y;
          item[2] = size;
        }
        @Override
        public void rect(float x, float y, float w, float h, int color) {
          rects.add(new float[] {x, y, w, h});
          super.rect(x, y, w, h, color);
        }
        @Override
        public void roundedRect(float x, float y, float w, float h, float r, int color) {
          rects.add(new float[] {x, y, w, h});
          super.roundedRect(x, y, w, h, r, color);
        }
      };
      ExtendedHudRenderer.renderArmor(canvas, s, slots, 640, 400);
      float[] b = HudRenderer.widgetBounds(s, "armor", 640, 400, false);
      assertTrue(canvas.texts.isEmpty(), "Vanilla durability mode draws no floating text");
      if (horizontal) {
        assertEquals(82f, b[2], .001f);
        assertEquals(22f, b[3], .001f);
      } else {
        assertEquals(24f, b[2], .001f);
        assertEquals(94f, b[3], .001f);
      }
      assertTrue(rects.stream().anyMatch(
          r -> Math.abs(r[0] - (item[0] + 2f)) < .05f && Math.abs(r[1] - (item[1] + 13f)) < .05f
              && Math.abs(r[2] - 13f) < .05f && Math.abs(r[3] - 2f) < .05f));
      assertTrue(rects.stream().anyMatch(
          r -> Math.abs(r[0] - (item[0] + 2f)) < .05f && Math.abs(r[1] - (item[1] + 13f)) < .05f
              && r[2] >= 9f && r[2] <= 11f && Math.abs(r[3] - 1f) < .05f));
    }
  }
  @Test
  void compactArmorPaintingKeepsEveryRealIconAndEnabledRowInsideTheSameBounds() {
    java.util.List<InventorySlot> slots = new java.util.ArrayList<InventorySlot>();
    for (int i = 0; i < 4; i++)
      slots.add(new InventorySlot(100 + i, "minecraft:iron_helmet", "Helmet", 1, 50, 100));
    for (boolean horizontal : new boolean[] {true, false})
      for (boolean durability : new boolean[] {true, false})
        for (boolean percent : new boolean[] {true, false})
          for (boolean names : new boolean[] {true, false}) {
            Settings s = settings();
            s.armor.horizontal = horizontal;
            s.armor.durability = durability;
            s.armor.percent = percent;
            s.armor.names = names;
            java.util.List<float[]> actual = new java.util.ArrayList<float[]>();
            HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
              @Override
              public void inventoryItem(
                  InventorySlot slot, float x, float y, float size, float alpha) {
                actual.add(new float[] {x, y, size});
                super.inventoryItem(slot, x, y, size, alpha);
              }
            };
            ExtendedHudRenderer.renderArmor(sink, s, slots, 640, 400);
            assertEquals(4, actual.size());
            final float[] b = HudRenderer.widgetBounds(s, "armor", 640, 400, false);
            for (float[] icon : actual) {
              assertEquals(16 * b[4], icon[2], .001f);
              assertTrue(icon[0] >= b[0] && icon[1] >= b[1]);
              assertTrue(icon[0] + icon[2] <= b[0] + b[2] + .001f
                  && icon[1] + icon[2] <= b[1] + b[3] + .001f);
            }
            for (HudPresentationTest.Text text : sink.texts) {
              assertTrue(text.y >= b[1]);
              assertTrue(text.y + text.size <= b[1] + b[3] + .001f);
            }
            assertEquals(0, sink.clips);
          }
  }
  @Test
  void verticalArmorDisplaysAllFourRealSlotsInsteadOfClippingToHorizontalHeight() {
    Settings s = settings();
    s.armor.horizontal = false;
    java.util.List<Float> ys = new java.util.ArrayList<Float>();
    HudPresentationTest.Sink canvas = new HudPresentationTest.Sink() {
      @Override
      public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
        ys.add(y);
        super.inventoryItem(slot, x, y, size, alpha);
      }
    };
    java.util.List<InventorySlot> slots = new java.util.ArrayList<InventorySlot>();
    for (int i = 0; i < 4; i++)
      slots.add(new InventorySlot(100 + i, "minecraft:iron_helmet", "Armor", 1, 0, 100));
    ExtendedHudRenderer.renderArmor(canvas, s, slots, 640, 400);
    float[] b = bounds(s, s.armor, 640, 400, 180);
    assertEquals(102 * b[4], b[3], .001f);
    assertEquals(4, ys.size());
    for (int i = 0; i < 4; i++) {
      assertTrue(ys.get(i) >= b[1]);
      assertTrue(ys.get(i) + 16 * b[4] <= b[1] + b[3]);
      if (i > 0) assertEquals(24f * b[4], ys.get(i) - ys.get(i - 1), .001f);
    }
    assertEquals(0, canvas.clips);
  }
  @Test
  void zakoZeroAndOddViewportUseVanillaPixelGrid() {
    Settings s = settings();
    s.target.style = "zakohp";
    NativeFontSink c = new NativeFontSink();
    ExtendedHudRenderer.renderTarget(
        c, s, new TargetView(1, "uuid", "Player", 0, 20, 0, 3, true), 641, 403);
    HudPresentationTest.Text text = c.texts.get(0);
    assertEquals("0", text.value);
    assertEquals(314, text.x);
    assertEquals(208, text.y);
    assertTrue(c.shadow);
  }
}
