package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import org.junit.jupiter.api.Test;

class SidebarResponsiveTest {
  private static final String[][] NAV = {{"nav-all", "all"}, {"nav0", "features"},
      {"nav-camera", "camera"}, {"nav-world", "world"}, {"nav-player", "player"},
      {"nav-waypoints", "waypoints"}, {"nav2", "hud"}, {"nav-settings", "settings"}};

  private static final class Session {
    final Settings settings = new Settings();
    final SkinViewTest.Actions actions = new SkinViewTest.Actions();
    final SkinView view;
    final int width, height;
    double time;

    Session(int width, int height) {
      this.width = width;
      this.height = height;
      settings.menu.animationSpeed = 1000000f;
      view = new SkinView(DefaultSkin.document(), settings, actions);
      render();
    }

    void render() {
      SkinViewTest.Sink sink = new SkinViewTest.Sink();
      assertTrue(view.renderMenu(sink, width, height, -100, -100, time += .3));
      assertEquals(0, sink.clips, "Every sidebar render balances native scissors");
    }

    float[] bounds(String id) {
      float[] bounds = view.elementBounds(id);
      assertNotNull(bounds, "Missing interactive element: " + id);
      return bounds;
    }

    void click(String id) {
      float[] bounds = bounds(id);
      int button = id.startsWith("expand.") ? 1 : 0;
      assertTrue(
          view.mouseClicked(bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2, button), id);
      view.mouseReleased(0, 0, button);
      render();
    }

    void revealNav(String id) {
      reveal(id, "sidebar-scroll");
    }

    void reveal(String id, String viewportId) {
      float[] viewport = view.layoutBounds(viewportId);
      assertNotNull(viewport);
      for (int attempt = 0; attempt < 16; attempt++) {
        float[] hit = view.elementBounds(id), row = view.layoutBounds(id);
        assertNotNull(row, "The declarative layout contains " + id);
        if (hit != null && hit[3] >= row[3] - .01f) return;
        double direction = row[1] < viewport[1] ? 2 : -2;
        assertTrue(view.mouseScrolled(
                       viewport[0] + viewport[2] / 2, viewport[1] + viewport[3] / 2, direction),
            "Navigation scroll reaches " + id);
        render();
      }
      fail("Sidebar row never became fully reachable: " + id);
    }
  }

  private static void inside(float[] child, float[] parent, String message) {
    assertNotNull(child, message);
    assertTrue(child[0] >= parent[0] - .02f && child[1] >= parent[1] - .02f
            && child[0] + child[2] <= parent[0] + parent[2] + .02f
            && child[1] + child[3] <= parent[1] + parent[3] + .02f,
        message);
  }

  @Test
  void desktopToggleReflowsMainContentImmediatelyWithReducedMotion() {
    Session session = new Session(1000, 700);
    float[] expandedNav = session.view.layoutBounds("navigation");
    float[] expandedMain = session.view.layoutBounds("modules-list");
    assertEquals(148f, expandedNav[2], .01f);
    for (String[] nav : NAV)
      assertEquals(session.view.layoutBounds(nav[0])[3], session.bounds(nav[0])[3], .01f,
          "All desktop navigation rows are fully visible without scrolling");
    session.click("sidebar-toggle");
    float[] compactNav = session.view.layoutBounds("navigation");
    float[] compactMain = session.view.layoutBounds("modules-list");
    assertEquals(48, compactNav[2], .01f);
    assertEquals(expandedNav[2] - compactNav[2], expandedMain[0] - compactMain[0], .01f);
    assertEquals(expandedNav[2] - compactNav[2], compactMain[2] - expandedMain[2], .01f);
    assertArrayEquals(new float[] {expandedMain[1], expandedMain[3]},
        new float[] {compactMain[1], compactMain[3]}, .01f);
    inside(session.bounds("sidebar-toggle"), session.view.layoutBounds("topbar"),
        "Compact toggle remains in the continuous top bar");
    session.click("sidebar-toggle");
    assertArrayEquals(expandedNav, session.view.layoutBounds("navigation"), .01f);
    assertFalse(session.settings.interfaceState.sidebarCollapsed);
    assertEquals(
        2, session.actions.changes, "Sidebar collapsed toggles are persisted in interfaceState");
  }

  @Test
  void eachCategoryCanBeSelectedOnDesktopAndShortCompactViewports() {
    for (int[] size : new int[][] {{1000, 700}, {520, 350}, {426, 240}, {320, 240}}) {
      Session session = new Session(size[0], size[1]);
      for (String[] nav : NAV) {
        session.revealNav(nav[0]);
        session.click(nav[0]);
        assertEquals(nav[1], session.view.page());
        float[] row = session.view.layoutBounds(nav[0]);
        float[] selected = session.view.layoutBounds("sidebar-selection");
        assertArrayEquals(row, selected, .02f, "Shared selection settles on " + nav[0]);
        inside(session.bounds(nav[0]), session.view.layoutBounds("sidebar-scroll"),
            "Selected navigation hit is clipped to its scroll viewport");
      }
      inside(session.view.layoutBounds("local-profile"), session.view.layoutBounds("navigation"),
          "Player identity remains independent of sidebar scrolling");
      inside(session.view.layoutBounds("local-player-head"),
          session.view.layoutBounds("local-profile"),
          "Player skin has a stable place in the footer");
      assertNull(session.view.layoutBounds("profile-card"));
      assertNull(session.view.layoutBounds("global-settings"));
      assertNotNull(session.view.elementBounds("option.theme-mode.dark"));
      session.click("settings-interface");
      assertNotNull(session.view.elementBounds("option.language.en"));
      session.click("settings-profiles");
      assertNotNull(session.view.elementBounds("create-profile"));
      assertEquals(0, session.actions.changes);
    }
  }

  @Test
  void smallViewportsAutoCollapseAndKeepSearchCardsAndExpandedControlsSeparate() {
    for (int[] size : new int[][] {{520, 350}, {426, 240}, {320, 240}}) {
      Session session = new Session(size[0], size[1]);
      float[] menu = session.view.layoutBounds("menu");
      inside(menu, new float[] {0, 0, size[0], size[1]}, "Menu fits the logical viewport");
      float[] nav = session.view.layoutBounds("navigation");
      assertEquals(48, nav[2], .01f, "Small viewport starts with icon navigation");
      float[] main = session.view.layoutBounds("modules-list");
      inside(main, menu, "Main scrolling area fits the menu");
      assertTrue(main[0] >= nav[0] + nav[2], "Main content starts after the sidebar");
      inside(session.bounds("module-search"), menu, "Search field fits");
      session.click("module-search");
      for (char c : "saturation".toCharArray()) session.view.charTyped(c, 0);
      session.render();
      inside(session.bounds("saturation"), main, "Filtered module header fits the main area");
      session.click("saturation");
      assertTrue(
          session.settings.saturation.enabled, "The main module row toggles its real setting");
      assertNull(
          session.view.elementBounds("saturation.strength"), "Toggling must not expand the module");
      session.click("expand.saturation");
      session.reveal("saturation.strength", "modules-list");
      inside(session.bounds("saturation.strength"), main, "Expanded slider remains reachable");
      float[] header = session.view.layoutBounds("saturation"),
              slider = session.view.layoutBounds("saturation.strength");
      assertTrue(slider[1] >= header[1] + header[3], "Expanded control follows its card header");
      assertEquals(
          2, session.actions.changes, "Module toggle and expansion persist in interfaceState");
      assertEquals(
          Boolean.TRUE, session.settings.interfaceState.expandedPanels.get("module.saturation"));
      session.click("sidebar-toggle");
      assertTrue(session.view.layoutBounds("navigation")[2] > 48,
          "The user can expand navigation even on a narrow viewport");
      float[] narrowMain = session.view.layoutBounds("modules-list");
      inside(narrowMain, menu, "Main content adapts to manually expanded narrow navigation");
      session.reveal("saturation.strength", "modules-list");
      inside(session.bounds("saturation.strength"), narrowMain,
          "Open controls remain usable after the content width changes");
      inside(session.bounds("module-search"), menu, "Search resizes with expanded navigation");
      assertTrue(session.settings.interfaceState.sidebarCollapsed);
      assertEquals(3, session.actions.changes, "Sidebar toggle persists across sessions");
    }
  }
}
