package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.ViewmodelSelection;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.ui.MenuController;
import dev.nightclient.ui.UiActions;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuAndHudUpgradeTest {
  private static final class OffscreenActions implements UiActions {
    String lastBound = "";

    @Override
    public ViewmodelSelection viewmodelSelection(float x, float y) {
      return ViewmodelSelection.NONE;
    }

    @Override
    public boolean viewmodelEditorSupported() {
      return true;
    }

    @Override
    public void setKeyBinding(String id, String nativeKeyName) {
      lastBound = id + "=" + nativeKeyName;
    }

    @Override
    public String keyBindingLabel(String id) {
      return "KEY_" + id;
    }

    @Override
    public void checkpoint() {}

    @Override
    public void changed() {}

    @Override
    public String clipboard() {
      return "";
    }

    @Override
    public void clipboard(String value) {}

    @Override
    public List<String> profiles() {
      return Collections.emptyList();
    }

    @Override
    public void saveProfile(String name) {}

    @Override
    public void loadProfile(String name) {}

    @Override
    public void deleteProfile(String name) {}

    @Override
    public String exportProfile() {
      return "{}";
    }

    @Override
    public void importProfile(String json) {}

    @Override
    public void reset() {}

    @Override
    public void undo() {}

    @Override
    public boolean canUndo() {
      return false;
    }

    @Override
    public void close() {}

    @Override
    public List<Cooldown> cooldowns() {
      return Collections.emptyList();
    }

    @Override
    public String gameVersion() {
      return "test";
    }

    @Override
    public String saveStatus() {
      return "";
    }
  }

  private static void click(MenuController menu, String id, int button) {
    float[] b = menu.elementBounds(id);
    assertNotNull(b, "Missing element: " + id);
    assertTrue(menu.mouseClicked(b[0] + b[2] * .5f, b[1] + b[3] * .5f, button), id);
    menu.mouseReleased(b[0] + b[2] * .5f, b[1] + b[3] * .5f, button);
  }

  @Test
  void userPositionedHudBoxesPlaceFlushWithScreenEdgesAndNeighbors() {
    List<HudAutoLayout.Box> placed = Arrays.asList(
        new HudAutoLayout.Box("armor", 0, 509, 128, 31, 1L, false),
        new HudAutoLayout.Box("fps", 128, 509, 64, 24, 2L, false));
    HudAutoLayout.place(placed, 960, 540, 6f);
    assertEquals(0f, placed.get(0).x, 0.001f);
    assertEquals(509f, placed.get(0).y, 0.001f);
    assertEquals(128f, placed.get(1).x, 0.001f, "User-placed neighbor stays flush at 0px gap");
    assertEquals(509f, placed.get(1).y, 0.001f);

    List<HudAutoLayout.Box> automatic = Arrays.asList(
        new HudAutoLayout.Box("armor", 0, 535, 128, 31, 0L, false),
        new HudAutoLayout.Box("fps", 128, 535, 64, 24, 0L, false));
    HudAutoLayout.place(automatic, 960, 540, 6f);
    assertEquals(8f, automatic.get(0).x, 0.001f, "Default unplaced HUD keeps 8px safe edge margin");
    assertEquals(540f - 8f - 31f, automatic.get(0).y, 0.001f);
    assertEquals(8f + 128f + 6f, automatic.get(1).x, 0.001f, "Automatic boxes keep 6px gap");
  }

  @Test
  void offscreenViewmodelHandsCanBeSelectedAndResetFromHudEditorInspector() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.viewmodel.enabled = true;
    s.viewmodel.main.x = 2.0f;
    s.viewmodel.main.y = -2.0f;
    s.viewmodel.off.x = -2.0f;
    s.viewmodel.off.rotateZ = 75f;

    OffscreenActions actions = new OffscreenActions();
    MenuController menu = new MenuController(s, actions);
    menu.onOpen();
    menu.openEditor();
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    menu.render(sink, 1000, 700, 0, 0, 0.1f);

    click(menu, "editor-target-viewmodel", 0);
    menu.render(sink, 1000, 700, 0, 0, 0.2f);

    assertNotNull(menu.elementBounds("viewmodel-hand-main"));
    assertNotNull(menu.elementBounds("viewmodel-hand-off"));
    assertNotNull(menu.elementBounds("viewmodel-reset"));
    assertNotNull(menu.elementBounds("viewmodel-reset-all"));

    click(menu, "viewmodel-hand-off", 0);
    menu.render(sink, 1000, 700, 0, 0, 0.3f);
    click(menu, "viewmodel-reset", 0);
    assertEquals(0f, s.viewmodel.off.x, 0.001f);
    assertEquals(0f, s.viewmodel.off.rotateZ, 0.001f);
    assertEquals(2.0f, s.viewmodel.main.x, 0.001f, "Main hand untouched when resetting off-hand");

    click(menu, "viewmodel-reset-all", 0);
    assertEquals(0f, s.viewmodel.main.x, 0.001f);
    assertEquals(0f, s.viewmodel.main.y, 0.001f);
    assertFalse(s.viewmodel.enabled);
  }

  @Test
  void moduleKeybindsLiveInsideModuleCardsAndNoRenderSyncsLowFire() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    OffscreenActions actions = new OffscreenActions();
    MenuController menu = new MenuController(s, actions);
    menu.onOpen();
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    menu.render(sink, 1100, 760, 0, 0, 0.1f);

    click(menu, "nav0", 0);
    menu.render(sink, 1100, 760, 0, 0, 0.2f);
    assertNull(menu.elementBounds("card.lowFire"), "Low Fire is consolidated into No Render");
    assertNotNull(menu.elementBounds("card.noRender"));
    click(menu, "module-toggle.noRender", 0);
    assertTrue(s.noRender.enabled);
    assertTrue(s.lowFire.enabled, "Enabling No Render with fire=true syncs lowFire");

    click(menu, "nav-world", 0);
    menu.render(sink, 1100, 760, 0, 0, 0.3f);
    click(menu, "expand.waypoints", 1);
    menu.render(sink, 1100, 760, 0, 0, 0.4f);
    assertNotNull(menu.elementBounds("binding-waypoint"));
    assertNotNull(menu.elementBounds("bind-module.waypoints"));
    click(menu, "binding-waypoint", 0);
    assertTrue(menu.captureBinding(78, "key.keyboard.n"));
    assertEquals("waypoint=key.keyboard.n", actions.lastBound);

    click(menu, "bind-module.waypoints", 0);
    assertTrue(menu.captureBinding(77, "key.keyboard.m"));
    assertEquals("module:waypoints=key.keyboard.m", actions.lastBound);
    assertEquals("key.keyboard.m", s.moduleBindings.get("waypoints"));
  }

  @Test
  void twoColumnModuleGridInterpolatesLowerRowYDuringCardExpansion() throws Exception {
    Settings s = new Settings();
    s.menu.animationSpeed = 1f;
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions());
    view.enableMotion();
    view.page("features");
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    view.renderMenu(sink, 1100, 760, 0, 0, 1.0);
    view.renderMenu(sink, 1100, 760, 0, 0, 2.0);

    float[] lowerBefore = view.elementBounds("card.noRender");
    assertNotNull(lowerBefore);

    float[] expand = view.elementBounds("expand.saturation");
    assertNotNull(expand);
    assertTrue(view.mouseClicked(expand[0] + 4, expand[1] + 4, 1));
    view.mouseReleased(expand[0] + 4, expand[1] + 4, 1);

    view.renderMenu(sink, 1100, 760, 0, 0, 2.016);
    float[] lowerStart = view.elementBounds("card.noRender");
    view.renderMenu(sink, 1100, 760, 0, 0, 2.066);
    float[] lowerMid = view.elementBounds("card.noRender");
    view.renderMenu(sink, 1100, 760, 0, 0, 2.6);
    view.renderMenu(sink, 1100, 760, 0, 0, 3.0);
    float[] lowerEnd = view.elementBounds("card.noRender");

    assertTrue(lowerEnd[1] > lowerBefore[1] + 40f, "Expanded row pushes lower row downward");
    assertEquals(lowerBefore[1], lowerStart[1], 1f, "Does not snap on initial click frame");
    assertTrue(lowerMid[1] > lowerBefore[1] + 5f && lowerMid[1] < lowerEnd[1] - 5f,
        "Lower row card smoothly glides instead of snapping: before=" + lowerBefore[1] + " mid="
            + lowerMid[1] + " end=" + lowerEnd[1]);

    SkinView gridView = new SkinView(
        dev.nightclient.skin.NcParser.parse(
            "state { open = false } "
            + "menu { width=400px; height=400px; layout=grid; columns=2; gap=8px; "
            + "animateLayout=true; transitionDuration=240ms; "
            + "panel(id=c0) { layout=column; animateLayout=true; transitionDuration=240ms; "
            + "button(id=toggleOpen,action='ui-toggle:open') { height=30px } "
            + "panel(if='ui.open') { height=80px } } "
            + "panel(id=c1) { height=30px } "
            + "panel(id=c2) { height=30px; animateLayout=true; transitionDuration=240ms } }"),
        s, new SkinViewTest.Actions());
    gridView.enableMotion();
    gridView.renderMenu(sink, 500, 500, 0, 0, 1.0);
    gridView.renderMenu(sink, 500, 500, 0, 0, 2.0);
    float[] gBefore = gridView.elementBounds("c2");
    float[] gBtn = gridView.elementBounds("toggleOpen");
    assertTrue(gridView.mouseClicked(gBtn[0] + 4, gBtn[1] + 4, 0));
    gridView.mouseReleased(gBtn[0] + 4, gBtn[1] + 4, 0);
    gridView.renderMenu(sink, 500, 500, 0, 0, 2.016);
    gridView.renderMenu(sink, 500, 500, 0, 0, 2.066);
    float[] gMid = gridView.elementBounds("c2");
    gridView.renderMenu(sink, 500, 500, 0, 0, 3.0);
    float[] gEnd = gridView.elementBounds("c2");
    assertTrue(gEnd[1] > gBefore[1] + 70f);
    assertTrue(gMid[1] > gBefore[1] + 5f && gMid[1] < gEnd[1] - 5f,
        "2-column grid row-1 card smoothly glides: before=" + gBefore[1] + " mid=" + gMid[1]
            + " end=" + gEnd[1]);
  }
}
