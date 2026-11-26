package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.ConfigStore;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import dev.nightclient.ui.MenuController;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudAutoLayoutTest {
  private static final String[] IDS = {"inventory", "tnt", "potions"};
  private static SkinDocument document() throws Exception {
    StringBuilder text = new StringBuilder(
        "style element panel { layout=absolute; padding=0px; borderWidth=0px; borderRadius=4px; "
        + "backgroundOpacity=100%; tintOpacity=-1; enterOpacity=100%; enterOffsetY=0px; "
        + "animateLayout=true; } menu { width=200px; height=100px; } hud(auto-layout=true) { "
        + "layout=absolute; width=100%; height=100%; enterOpacity=100%; enterOffsetY=0px; ");
    for (String id : IDS) text.append(id).append("(id='").append(id).append("'); ");
    text.append("} ");
    String[] colors = {"#112233", "#223344", "#334455"};
    for (int i = 0; i < IDS.length; i++) {
      String id = IDS[i];
      text.append("component 'hud.")
          .append(id)
          .append("' { panel(id='")
          .append(id)
          .append("-frame') { position=absolute; x='{{=")
          .append(id)
          .append(".offsetX}}'; y='{{=")
          .append(id)
          .append(".offsetY}}'; width='{{=")
          .append(id)
          .append(".width}}'; height='{{=")
          .append(id)
          .append(".height}}'; background=")
          .append(colors[i])
          .append("; } } ");
    }
    SkinDocument doc = NcParser.parse(text.toString());
    new SkinValidator().validate(doc);
    return doc;
  }
  private static Settings settings() {
    Settings s = new Settings();
    s.menu.hudScale = 1;
    s.menu.animationSpeed = 1000000f;
    for (Settings.Panel p : new Settings.Panel[] {s.inventory, s.tnt, s.potions}) {
      p.enabled = true;
      p.offsetX = 20;
      p.offsetY = 20;
      p.anchorX = p.anchorY = 0;
      p.width = 80;
      p.height = 60;
    }
    return s;
  }
  private static final class Sink extends SkinViewTest.Sink {
    final Map<Integer, float[]> boxes = new LinkedHashMap<Integer, float[]>();
    @Override
    public void roundedRect(float x, float y, float w, float h, float r, int color) {
      if (color == 0xff112233 || color == 0xff223344 || color == 0xff334455)
        boxes.put(color, new float[] {x, y, w, h});
      super.roundedRect(x, y, w, h, r, color);
    }
  }
  private static void render(SkinView view, Sink out, int width, int height, double time) {
    assertTrue(view.renderHud(out, width, height, time, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList()));
    assertEquals(0, out.clips);
  }
  private static void nonOverlapping(float[] a, float[] b) {
    assertTrue(a[0] + a[2] <= b[0] + .01 || b[0] + b[2] <= a[0] + .01 || a[1] + a[3] <= b[1] + .01
            || b[1] + b[3] <= a[1] + .01,
        "Unexpected overlap: " + Arrays.toString(a) + " / " + Arrays.toString(b));
  }
  private static void inside(float[] b, int w, int h) {
    assertTrue(b[0] >= 8 - .01 && b[1] >= 8 - .01 && b[0] + b[2] <= w - 8 + .01
            && b[1] + b[3] <= h - 8 + .01,
        Arrays.toString(b));
  }
  @Test
  void resizedHudUsesOnePlacementForPaintingLayoutBoundsAndEditorWithoutChangingSavedOffsets()
      throws Exception {
    Settings s = settings();
    s.menu.animationSpeed = 1f;
    SkinDocument doc = document();
    SkinView view = new SkinView(doc, s, new SkinViewTest.Actions());
    for (int step = 0; step < 3; step++) {
      if (step == 1) s.inventory.height = 160;
      int w = step == 2 ? 300 : 420, h = step == 2 ? 240 : 320;
      double time = 1 + step * .1;
      Sink ink = new Sink();
      render(view, ink, w, h, time);
      float[][] boxes = new float[3][];
      for (int i = 0; i < IDS.length; i++) {
        String id = IDS[i];
        boxes[i] = view.hudBounds(id, w, h, time, false);
        assertArrayEquals(Arrays.copyOf(boxes[i], 4), view.layoutBounds(id + "-frame"), .01f);
        assertArrayEquals(Arrays.copyOf(boxes[i], 4),
            ink.boxes.get(new int[] {0xff112233, 0xff223344, 0xff334455}[i]), .01f,
            "Native paint matches measured geometry");
        inside(boxes[i], w, h);
        assertArrayEquals(boxes[i], view.hudBounds(id, w, h, time, true), .01f,
            "Editor and live placement agree");
      }
      for (int a = 0; a < 3; a++)
        for (int b = a + 1; b < 3; b++) nonOverlapping(boxes[a], boxes[b]);
      assertEquals(20, s.inventory.offsetX);
      assertEquals(20, s.inventory.offsetY);
      assertEquals(20, s.tnt.offsetX);
      assertEquals(20, s.tnt.offsetY);
      assertEquals(20, s.potions.offsetX);
      assertEquals(20, s.potions.offsetY);
      Sink single = new Sink();
      assertTrue(view.renderHudWidget("tnt", single, w, h, time, true));
      assertArrayEquals(Arrays.copyOf(boxes[1], 4), single.boxes.get(0xff223344), .01f,
          "Isolated widget pass uses the same placement");
    }
  }
  @Test
  void pinnedDragAndPersistedPriorityKeepTheDropPositionAcrossFreshViewsAndProfiles()
      throws Exception {
    Settings s = settings();
    SkinDocument doc = document();
    SkinView view = new SkinView(doc, s, new SkinViewTest.Actions());
    render(view, new Sink(), 420, 320, 1);
    view.hudPlacementDrag("potions", 30, 30);
    render(view, new Sink(), 420, 320, 2);
    assertEquals(30, view.hudBounds("potions", 420, 320, 2, true)[0]);
    assertEquals(30, view.hudBounds("potions", 420, 320, 2, true)[1]);
    s.potions.offsetX = s.potions.offsetY = 30;
    s.potions.layoutOrder = 1;
    view.hudPlacementDragEnd();
    render(view, new Sink(), 420, 320, 3);
    float[] placed = view.hudBounds("potions", 420, 320, 3, false);
    assertEquals(30, placed[0]);
    assertEquals(30, placed[1]);
    try (ConfigStore store = new ConfigStore(Files.createTempDirectory("tv-hud-placement-"))) {
      Settings loaded = store.decode(store.encode(s));
      assertEquals(1, loaded.potions.layoutOrder);
      SkinView fresh = new SkinView(doc, loaded, new SkinViewTest.Actions());
      render(fresh, new Sink(), 420, 320, 3);
      assertArrayEquals(placed, fresh.hudBounds("potions", 420, 320, 3, false), .01f);
    }
  }
  @Test
  void impossiblePackingIsBoundedDeterministicAndDoesNotShrinkTextGeometry() {
    List<HudAutoLayout.Box> first =
        Arrays.asList(new HudAutoLayout.Box("a", 10, 10, 150, 110, 0, false),
            new HudAutoLayout.Box("b", 10, 10, 150, 110, 0, false));
    List<HudAutoLayout.Box> second =
        Arrays.asList(new HudAutoLayout.Box("a", 10, 10, 150, 110, 0, false),
            new HudAutoLayout.Box("b", 10, 10, 150, 110, 0, false));
    HudAutoLayout.place(first, 180, 150, 6);
    HudAutoLayout.place(second, 180, 150, 6);
    for (int i = 0; i < 2; i++) {
      HudAutoLayout.Box a = first.get(i), b = second.get(i);
      assertEquals(a.x, b.x);
      assertEquals(a.y, b.y);
      inside(new float[] {a.x, a.y, a.width, a.height}, 180, 150);
      assertEquals(150, a.width);
      assertEquals(110, a.height);
    }
  }
  @Test
  void boundedRepackRevisitsGreedyChoicesAndCacheIncludesEveryPlacementInput() {
    List<HudAutoLayout.Box> boxes =
        Arrays.asList(new HudAutoLayout.Box("a", 195, 8, 110, 100, 0, false),
            new HudAutoLayout.Box("b", 195, 8, 110, 100, 0, false),
            new HudAutoLayout.Box("c", 195, 8, 110, 100, 0, false));
    HudAutoLayout.Cache cache = new HudAutoLayout.Cache();
    cache.place(boxes, 400, 150, 6);
    for (int a = 0; a < 3; a++)
      for (int b = a + 1; b < 3; b++)
        nonOverlapping(new float[] {boxes.get(a).x, boxes.get(a).y, 110, 100},
            new float[] {boxes.get(b).x, boxes.get(b).y, 110, 100});
    float[] expected = {boxes.get(0).x, boxes.get(0).y};
    boxes.get(0).x = -999;
    cache.place(boxes, 400, 150, 6);
    assertEquals(expected[0], boxes.get(0).x);
    assertEquals(expected[1], boxes.get(0).y);
    boxes.set(0, new HudAutoLayout.Box("a", 195, 8, 110, 100, 1, true));
    cache.place(boxes, 400, 150, 6);
    assertEquals(195, boxes.get(0).x);
    assertEquals(8, boxes.get(0).y);
  }
  @Test
  void editorClickDoesNotChangePriorityButActualDragReleaseAndEscapeDoWithoutOverflow()
      throws Exception {
    Settings s = settings();
    s.hud.snap = false;
    s.skin.enabled = true;
    SkinDocument doc = document();
    SkinViewTest.Actions actions = new SkinViewTest.Actions() {
      public SkinDocument skinDocument() {
        return doc;
      }
    };
    MenuController menu = new MenuController(s, actions);
    menu.onOpen();
    menu.openEditor();
    Sink sink = new Sink();
    menu.render(sink, 420, 320, -100, -100, 1);
    java.lang.reflect.Field f = MenuController.class.getDeclaredField("view");
    f.setAccessible(true);
    SkinView view = (SkinView) f.get(menu);
    float[] before = view.hudBounds("tnt", 420, 320, 1, true);
    assertTrue(menu.mouseClicked(before[0] + 10, before[1] + 10, 0));
    menu.mouseReleased(before[0] + 10, before[1] + 10, 0);
    assertEquals(0, s.tnt.layoutOrder, "A selection click does not edit placement order");
    s.inventory.layoutOrder = s.tnt.layoutOrder = s.potions.layoutOrder = Integer.MAX_VALUE;
    menu.render(new Sink(), 420, 320, -100, -100, 2);
    before = view.hudBounds("tnt", 420, 320, 2, true);
    assertTrue(menu.mouseClicked(before[0] + 10, before[1] + 10, 0));
    assertTrue(menu.mouseDragged(30, 30, 0, 30 - before[0] - 10, 30 - before[1] - 10));
    menu.render(new Sink(), 420, 320, 30, 30, 3);
    float[] moving = view.hudBounds("tnt", 420, 320, 3, true);
    assertEquals(20, moving[0], .01);
    assertEquals(20, moving[1], .01);
    menu.mouseReleased(30, 30, 0);
    assertTrue(
        s.tnt.layoutOrder > s.inventory.layoutOrder && s.tnt.layoutOrder > s.potions.layoutOrder);
    assertTrue(s.tnt.layoutOrder < 100);
    menu.render(new Sink(), 420, 320, -100, -100, 4);
    assertArrayEquals(moving, view.hudBounds("tnt", 420, 320, 4, true), .01f);
    menu.leaveEditor();
    SkinView fresh = new SkinView(doc, s, actions);
    render(fresh, new Sink(), 420, 320, 5);
    assertArrayEquals(moving, fresh.hudBounds("tnt", 420, 320, 5, false), .01f,
        "Leaving editor does not move the dropped widget");
    menu.openEditor();
    menu.render(new Sink(), 420, 320, -100, -100, 6);
    view = (SkinView) f.get(menu);
    before = view.hudBounds("tnt", 420, 320, 6, true);
    assertTrue(menu.mouseClicked(before[0] + 40, before[1] + 50, 0),
        "Reopened widget click " + Arrays.toString(before));
    assertTrue(menu.mouseDragged(160, 90, 0, 100, 20), "Reopened widget drag");
    menu.keyPressed(dev.nightclient.api.Keys.ESCAPE, 0);
    assertFalse(menu.isEditor());
    assertEquals(120, s.tnt.offsetX, .01);
    assertEquals(40, s.tnt.offsetY, .01);
  }
  @Test
  void policyIsOptInAndValidatesItsLocationAndLiteralValue() throws Exception {
    assertThrows(java.io.IOException.class,
        () -> new SkinValidator().validate(NcParser.parse("menu(auto-layout=true) {}")));
    assertThrows(java.io.IOException.class,
        () -> new SkinValidator().validate(NcParser.parse("hud(auto-layout=maybe) {}")));
    SkinDocument doc = NcParser.parse(
        "hud { panel(id=inventory-frame){position=absolute; x=20px;y=20px;width=80px;height=60px;} "
        + "panel(id=tnt-frame){position=absolute;x=20px;y=20px;width=80px;height=60px;} }");
    SkinView view = new SkinView(doc, settings(), new SkinViewTest.Actions());
    render(view, new Sink(), 420, 320, 1);
    assertEquals(view.hudBounds("inventory", 420, 320, 1, false)[1],
        view.hudBounds("tnt", 420, 320, 1, false)[1],
        "Unopted custom layouts retain their authored overlap");
  }
  @Test
  void bundledWidgetDroppedAtLegacyDefaultOffsetsKeepsItsExactPositionAfterReleaseExitAndReload()
      throws Exception {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.hud.snap = false;
    s.tnt.enabled = true;
    SkinViewTest.Actions actions = new SkinViewTest.Actions() {
      @Override
      public List<dev.nightclient.api.TntView> tnts() {
        return Collections.singletonList(new dev.nightclient.api.TntView(1, 400, 300, 2, 10));
      }
    };
    MenuController menu = new MenuController(s, actions);
    menu.onOpen();
    menu.openEditor();
    menu.render(new Sink(), 960, 540, -100, -100, 1);
    java.lang.reflect.Field field = MenuController.class.getDeclaredField("view");
    field.setAccessible(true);
    SkinView view = (SkinView) field.get(menu);
    float[] before = view.hudBounds("tnt", 960, 540, 1, true);
    float pressX = before[0] + 30, pressY = before[1] + 20;
    assertTrue(menu.mouseClicked(pressX, pressY, 0));
    assertTrue(menu.mouseDragged(50, 110, 0, 50 - pressX, 110 - pressY));
    menu.render(new Sink(), 960, 540, 50, 110, 2);
    float[] moving = view.hudBounds("tnt", 960, 540, 2, true);
    assertEquals(20, moving[0], .01);
    assertEquals(90, moving[1], .01);
    menu.mouseReleased(50, 110, 0);
    menu.render(new Sink(), 960, 540, -100, -100, 3);
    assertArrayEquals(moving, view.hudBounds("tnt", 960, 540, 3, true), .01f);
    assertTrue(s.tnt.layoutOrder > 0);
    menu.leaveEditor();
    try (ConfigStore store = new ConfigStore(Files.createTempDirectory("tv-bundled-drop-"))) {
      Settings loaded = store.decode(store.encode(s));
      SkinView fresh = new SkinView(dev.nightclient.skin.DefaultSkin.document(), loaded, actions);
      assertTrue(fresh.renderHud(new Sink(), 960, 540, 4, Collections.emptyList(),
          Collections.emptyList(), actions.tnts()));
      assertArrayEquals(moving, fresh.hudBounds("tnt", 960, 540, 4, false), .01f);
      Settings reset = store.decode(store.encode(new Settings()));
      assertEquals(0, reset.tnt.layoutOrder, "Reset returns legacy automatic placement");
    }
  }
}
