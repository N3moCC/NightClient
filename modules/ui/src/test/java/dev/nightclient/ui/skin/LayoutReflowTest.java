package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import dev.nightclient.ui.MenuController;
import org.junit.jupiter.api.Test;

class LayoutReflowTest {
  private static final float EPSILON = .002f;

  private static SkinView view(Settings settings) throws Exception {
    SkinDocument document = NcParser.parse("state { compact=false; expanded=false } "
        + "style element menu { width=360px; height=180px; layout=absolute; padding=0px; "
        + "animateLayout=false; enterScale=1; enterOpacity=100%; enterOffsetY=0px; } "
        + "style element panel { layout=absolute; padding=0px; animateLayout=true; "
        + "transitionDuration=200ms; easing=linear; enterOpacity=100%; enterOffsetY=0px; } "
        + "style element button { padding=0px; layout=absolute; animateLayout=true; "
        + "transitionDuration=200ms; easing=linear; hoverScale=1; pressScale=1; "
        + "enterOpacity=100%; enterOffsetY=0px; } "
        + "menu { panel(id=sidebar) { position=absolute; x=0px; y=0px; "
        + "width='{{= ui.compact ? 50 : 140}}'; height=120px; layoutReflow=true; overflow=hidden; "
        + "panel { width=100%; height=100%; "
        + "button(id=sidebar-icon,action=close) { x=10px; y=12px; width=12px; height=12px; "
        + "icon(src=grid) { width=12px; height=12px; } } } } "
        + "panel(id=main) { position=absolute; x='{{= (ui.compact ? 50 : 140)+8}}'; y=0px; "
        + "width='{{= 344-(ui.compact ? 50 : 140)}}'; height=120px; "
        + "layoutReflow=true; overflow=hidden; "
        + "column { width=100%; height=100%; padding=0px; gap=0px; "
        + "panel(id=main-row) { width=100%; height='{{= ui.expanded ? 60 : 30}}'; "
        + "button(id=main-icon,action=close) { position=absolute; x=10px; y=10px; width=12px; "
          + "height=12px; "
        + "icon(src=grid) { width=12px; height=12px; } } "
        + "button(id=switch,action=close) { position=absolute; right=8px; y=10px; width=20px; "
          + "height=12px; } "
        + "} } } "
        + "button(id=collapse,action='ui-toggle:compact') { position=absolute; x=0px; y=140px; "
          + "width=30px; height=20px; } "
        + "button(id=expand,action='ui-toggle:expanded') { position=absolute; x=40px; y=140px; "
          + "width=30px; height=20px; } }");
    new SkinValidator().validate(document);
    SkinView view = new SkinView(document, settings, new SkinViewTest.Actions());
    view.resetMotion();
    return view;
  }

  private static void render(SkinView view, double time) {
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    assertTrue(view.renderMenu(sink, 360, 180, -100, -100, time));
    assertEquals(0, sink.clips);
  }

  private static void click(SkinView view, String id) {
    float[] bounds = view.elementBounds(id);
    assertNotNull(bounds, id);
    assertTrue(view.mouseClicked(bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2, 0));
    view.mouseReleased(0, 0, 0);
  }

  private static float[] bounds(SkinView view, String id) {
    float[] bounds = view.elementBounds(id);
    assertNotNull(bounds, id + " must stay fully reachable during reflow");
    return bounds;
  }

  private static void assertReflowGeometry(SkinView view) {
    float[] sidebar = bounds(view, "sidebar"), main = bounds(view, "main");
    assertEquals(view.layoutBounds("sidebar")[2], sidebar[2], EPSILON,
        "Reflowed width must not receive a second paint-time scale");
    assertEquals(sidebar[2] + 8, main[0], EPSILON,
        "Container position and width must use one synchronized transition");
    assertEquals(352, main[0] + main[2], EPSILON);
    float[] sidebarIcon = bounds(view, "sidebar-icon");
    assertEquals(10, sidebarIcon[0], EPSILON);
    assertEquals(12, sidebarIcon[2], EPSILON);
    assertEquals(12, sidebarIcon[3], EPSILON);
    float[] mainIcon = bounds(view, "main-icon");
    assertEquals(main[0] + 10, mainIcon[0], EPSILON);
    assertEquals(12, mainIcon[2], EPSILON);
    assertEquals(12, mainIcon[3], EPSILON);
    float[] toggle = bounds(view, "switch");
    assertEquals(324, toggle[0], EPSILON);
    assertEquals(20, toggle[2], EPSILON,
        "The right-aligned control must not be clipped while its parent narrows");
    assertEquals(12, toggle[3], EPSILON);
  }

  @Test
  void expansionCollapseAndReversalReflowNestedChildrenWithoutStretchingOrClipping()
      throws Exception {
    SkinView view = view(new Settings());
    render(view, 1);
    assertReflowGeometry(view);
    click(view, "collapse");
    render(view, 1);
    for (int i = 1; i <= 10; i++) {
      render(view, 1 + i / 100d);
      assertReflowGeometry(view);
    }
    assertEquals(95, bounds(view, "sidebar")[2], EPSILON);
    click(view, "collapse");
    render(view, 1.1);
    assertEquals(95, bounds(view, "sidebar")[2], EPSILON,
        "A reversed width starts at the current visible box");
    for (int i = 1; i <= 22; i++) {
      render(view, 1.1 + i / 100d);
      assertReflowGeometry(view);
    }
    assertEquals(140, bounds(view, "sidebar")[2], EPSILON);
    click(view, "collapse");
    render(view, 1.4);
    for (int i = 1; i <= 25; i++) {
      render(view, 1.4 + i / 100d);
      assertReflowGeometry(view);
    }
    assertEquals(50, bounds(view, "sidebar")[2], EPSILON);
  }

  @Test
  void reflowKeepsIndependentHeightTransitionsAndReducedMotionCannotResumeStaleWidth()
      throws Exception {
    Settings settings = new Settings();
    SkinView view = view(settings);
    render(view, 1);
    click(view, "collapse");
    click(view, "expand");
    render(view, 1);
    render(view, 1.1);
    assertReflowGeometry(view);
    assertEquals(45, bounds(view, "main-row")[3], EPSILON,
        "Horizontal reflow must preserve module height animation");
    settings.menu.animationSpeed = 1000000f;
    render(view, 1.11);
    assertEquals(50, bounds(view, "sidebar")[2], EPSILON);
    assertReflowGeometry(view);
    settings.menu.animationSpeed = 1f;
    render(view, 1.12);
    assertEquals(50, bounds(view, "sidebar")[2], EPSILON,
        "Turning motion back on must not jump to the old in-flight width");
    assertReflowGeometry(view);
  }

  @Test
  void centeredReflowKeepsItsAnchorFixed() throws Exception {
    Settings settings = new Settings();
    SkinView view = new SkinView(
        NcParser.parse("state { compact=false } menu { width=200px; height=100px; "
                        + "layout=absolute; padding=0px; "
            + "enterScale=1; enterOpacity=100%; enterOffsetY=0px; animateLayout=false; "
            + "panel(id=box) { position=absolute; x=50%; anchorX=.5; y=0px; "
            + "width='{{= ui.compact ? 50 : 140}}'; height=50px; padding=0px; "
            + "layoutReflow=true; animateLayout=true; transitionDuration=200ms; easing=linear; "
            + "enterOpacity=100%; enterOffsetY=0px; } "
            + "button(id=collapse,action='ui-toggle:compact') { position=absolute; x=0px; y=70px; "
            + "width=30px; height=20px; enterOpacity=100%; enterOffsetY=0px; hoverScale=1; "
              + "pressScale=1; } }"),
        settings, new SkinViewTest.Actions());
    view.resetMotion();
    view.renderMenu(new SkinViewTest.Sink(), 200, 100, -100, -100, 1);
    click(view, "collapse");
    view.renderMenu(new SkinViewTest.Sink(), 200, 100, -100, -100, 1);
    for (int i = 1; i <= 22; i++) {
      view.renderMenu(new SkinViewTest.Sink(), 200, 100, -100, -100, 1 + i / 100d);
      float[] box = bounds(view, "box");
      assertEquals(100, box[0] + box[2] / 2, EPSILON);
    }
  }
  private static final class CachedSink extends SkinViewTest.Sink {
    private static final Object METRICS = new Object();
    float[] marker;
    @Override
    public Object textMetricsKey() {
      return METRICS;
    }
    @Override
    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
      if ((color & 0xffffff) == 0x123456) marker = new float[] {x, y, width, height};
      super.roundedRect(x, y, width, height, radius, color);
    }
  }

  @Test
  void cachedHudComponentsReflowLikeFreshBuildsThroughReversalAndReducedMotion() throws Exception {
    Settings settings = new Settings();
    settings.theme.motion.enterOpacity = 1;
    settings.theme.motion.enterOffsetX = settings.theme.motion.enterOffsetY = 0;
    settings.hud.width = 140;
    SkinDocument document =
        NcParser.parse("hud { layout=absolute; cooldowns(id=sample) } component 'sample' { "
            + "panel(id=frame) { width='{{hud.width}}'; height=40px; layout=absolute; padding=0px; "
            + "layoutReflow=true; animateLayout=true; transitionDuration=200ms; easing=linear; "
            + "enterOpacity=100%; enterOffsetY=0px; "
            + "rect(id=marker) { position=absolute; right=8px; y=4px; width=12px; height=12px; "
            + "fill=#123456; animateLayout=true; transitionDuration=200ms; easing=linear; } } }");
    SkinView cached = new SkinView(document, settings, new SkinViewTest.Actions());
    SkinView fresh = new SkinView(document, settings, new SkinViewTest.Actions());
    for (int frame = 0; frame <= 45; frame++) {
      if (frame == 1 || frame == 18) settings.hud.width = 50;
      if (frame == 8) settings.menu.animationSpeed = 1000000f;
      if (frame == 9) settings.menu.animationSpeed = 1f;
      if (frame == 14) settings.hud.width = 140;
      double time = 1 + frame / 100d;
      CachedSink a = new CachedSink(), b = new CachedSink();
      fresh.invalidateLayout();
      assertTrue(cached.renderHud(a, 300, 200, time, null, null, null));
      assertTrue(fresh.renderHud(b, 300, 200, time, null, null, null));
      assertNotNull(a.marker);
      assertArrayEquals(b.marker, a.marker, EPSILON, "Cache divergence at frame " + frame);
      float[] parent = cached.layoutBounds("frame");
      assertEquals(parent[0] + parent[2] - 20, a.marker[0], EPSILON);
      assertEquals(12, a.marker[2], EPSILON);
      assertEquals(12, a.marker[3], EPSILON);
      if (frame == 8 || frame == 9) assertEquals(50, parent[2], EPSILON);
    }
  }

  private static float[] actualBounds(MenuController menu, String id) {
    float[] box = menu.elementBounds(id);
    assertNotNull(box, id + " must remain reachable in the real default theme");
    return box;
  }

  private static void actualClick(MenuController menu, String id) {
    float[] box = actualBounds(menu, id);
    int button = id.startsWith("expand.") ? 1 : 0;
    assertTrue(menu.mouseClicked(box[0] + box[2] / 2, box[1] + box[3] / 2, button));
    menu.mouseReleased(0, 0, button);
  }

  private static void assertActualDefaultGeometry(
      MenuController menu, float right, float toggleWidth, String phase) {
    float[] sidebar = actualBounds(menu, "navigation");
    float[] header = actualBounds(menu, "menu-header");
    float[] search = actualBounds(menu, "module-search");
    float[] content = actualBounds(menu, "modules-list");
    float[] card = actualBounds(menu, "card.saturation");
    float[] toggle = actualBounds(menu, "module-toggle.saturation");
    assertEquals(sidebar[0] + sidebar[2], header[0], .02f, phase + ": sidebar/header seam");
    assertEquals(right, header[0] + header[2], .02f, phase + ": header right");
    assertEquals(right - 16.8f, search[0] + search[2], .02f, phase + ": search right");
    assertEquals(header[0], content[0], .02f, phase + ": header/content left");
    assertEquals(right, content[0] + content[2], .02f, phase + ": content right");
    assertTrue(card[0] + card[2] <= content[0] + content[2] + .02f, phase);
    assertEquals(toggleWidth, toggle[2], .02f, phase + ": switch must never clip or stretch");
    assertEquals(card[0] + card[2] - 9.6f, toggle[0] + toggle[2], .02f,
        phase + ": switch stays at the right inset");
    float[] expansion = actualBounds(menu, "expand.saturation");
    assertEquals(91.2f, expansion[2], .02f, phase + ": disclosure must not clip or stretch");
    assertEquals(card[0] + card[2] - 7.2f, expansion[0] + expansion[2], .02f,
        phase + ": disclosure stays at the right inset");
  }

  @Test
  void realDefaultSidebarKeepsSearchCardsAndSwitchesInsideWindowAtEveryTenMillisecondFrame() {
    Settings settings = new Settings();
    settings.menu.scale = 1.2f;
    settings.menu.animationSpeed = 1f;
    settings.menu.animationSpeed = 1;
    MenuController menu = new MenuController(settings, new SkinViewTest.Actions());
    assertSame(DefaultSkin.document(), settings.skinDocument);
    menu.onOpen();
    for (int i = 0; i <= 60; i++)
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, i / 100d);
    float[] header = actualBounds(menu, "menu-header");
    float right = header[0] + header[2],
          toggleWidth = actualBounds(menu, "module-toggle.saturation")[2];
    assertEquals(36f, toggleWidth, .02f);
    for (int phase = 0; phase < 4; phase++) {
      actualClick(menu, "sidebar-toggle");
      double start = 1 + phase;
      for (int frame = 0; frame <= (phase == 2 ? 8 : 60); frame++) {
        menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, start + frame / 100d);
        assertActualDefaultGeometry(menu, right, toggleWidth, phase + "/" + frame);
      }
      if (phase == 2) {
        actualClick(menu, "sidebar-toggle");
        for (int frame = 0; frame <= 60; frame++) {
          menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, start + .08 + frame / 100d);
          assertActualDefaultGeometry(menu, right, toggleWidth, "reversal/" + frame);
        }
      }
    }
  }

  private static void assertActualModuleSpacing(MenuController menu, String phase) {
    float[] first = actualBounds(menu, "card.saturation");
    float[] next = actualBounds(menu, "card.noRender");
    assertEquals(9.6f, next[1] - first[1] - first[3], .02f,
        phase
            + (": neighboring cards must follow the current animated height without a second Y "
               + "tween"));
    float[] toggle = actualBounds(menu, "module-toggle.saturation");
    float[] disclosure = actualBounds(menu, "expand.saturation");
    assertEquals(36f, toggle[2], .02f, phase);
    assertEquals(91.2f, disclosure[2], .02f, phase);
    assertEquals(first[0] + first[2] - 9.6f, toggle[0] + toggle[2], .02f, phase);
    assertEquals(first[0] + first[2] - 7.2f, disclosure[0] + disclosure[2], .02f, phase);
  }

  @Test
  void clickingAcrossTheEntireTopbarTogglesTheSidebar() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    MenuController menu = new MenuController(settings, new SkinViewTest.Actions());
    menu.onOpen();
    menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, 1);
    float initial = actualBounds(menu, "navigation")[2];
    boolean initiallyExpanded = initial > 80f;
    boolean expanded = initiallyExpanded;
    for (float fraction : new float[] {.12f, .5f, .9f, .5f, .12f, .9f}) {
      float[] topbar = actualBounds(menu, "sidebar-toggle");
      float x = topbar[0] + topbar[2] * fraction, y = topbar[1] + topbar[3] * .5f;
      assertTrue(menu.mouseClicked(x, y, 0), "topbar click at " + fraction);
      menu.mouseReleased(x, y, 0);
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, 2 + fraction);
      expanded = !expanded;
      float width = actualBounds(menu, "navigation")[2];
      if (expanded == initiallyExpanded)
        assertEquals(initial, width, .02f, "topbar must restore sidebar at " + fraction);
      else if (expanded)
        assertTrue(width > initial + 25, "topbar must expand sidebar at " + fraction);
      else
        assertTrue(width < initial - 25, "topbar must collapse sidebar at " + fraction);
    }
  }

  @Test
  void realDefaultDisclosureKeepsItsFlowGapThroughExpansionCollapseAndReversalAtEveryFrame() {
    Settings settings = new Settings();
    settings.menu.scale = 1.2f;
    settings.menu.animationSpeed = 1f;
    settings.menu.animationSpeed = 1;
    MenuController menu = new MenuController(settings, new SkinViewTest.Actions());
    menu.onOpen();
    for (int frame = 0; frame <= 60; frame++)
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, frame / 100d);
    float collapsed = actualBounds(menu, "card.saturation")[3];
    double time = 1;
    actualClick(menu, "expand.saturation");
    float intermediate = collapsed;
    for (int frame = 0; frame <= 60; frame++) {
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, time + frame / 100d);
      assertActualModuleSpacing(menu, "expand/" + frame);
      if (frame == 8) intermediate = actualBounds(menu, "card.saturation")[3];
    }
    float expanded = actualBounds(menu, "card.saturation")[3];
    assertTrue(intermediate > collapsed && intermediate < expanded,
        "Spacing must be checked during a real height transition");
    time += 1;
    actualClick(menu, "expand.saturation");
    for (int frame = 0; frame <= 60; frame++) {
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, time + frame / 100d);
      assertActualModuleSpacing(menu, "collapse/" + frame);
    }
    assertEquals(collapsed, actualBounds(menu, "card.saturation")[3], .02f);
    time += 1;
    actualClick(menu, "expand.saturation");
    for (int frame = 0; frame <= 8; frame++) {
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, time + frame / 100d);
      assertActualModuleSpacing(menu, "partial-expand/" + frame);
    }
    intermediate = actualBounds(menu, "card.saturation")[3];
    assertTrue(intermediate > collapsed && intermediate < expanded);
    actualClick(menu, "expand.saturation");
    for (int frame = 0; frame <= 60; frame++) {
      menu.render(new SkinViewTest.Sink(), 640, 360, -100, -100, time + .08 + frame / 100d);
      assertActualModuleSpacing(menu, "reverse/" + frame);
    }
    assertEquals(collapsed, actualBounds(menu, "card.saturation")[3], .02f);
  }

  @Test
  void stableHeightFlowPreservesIndependentPositionAnimation() throws Exception {
    SkinView view = new SkinView(
        NcParser.parse("state { shifted=false } menu { width=200px; height=180px; "
                        + "layout=absolute; padding=0px; "
            + "enterScale=1; enterOpacity=100%; enterOffsetY=0px; animateLayout=false; "
            + "column { width=160px; height=120px; padding=0px; gap=6px; "
            + "panel { height=30px; animateLayout=true; transitionDuration=200ms; easing=linear; } "
            + "button(id=moving,action=close) { width=100px; height=20px; y='{{= ui.shifted ? 20 : "
              + "0}}'; "
            + "animateLayout=true; transitionDuration=200ms; easing=linear; "
            + "enterOpacity=100%; enterOffsetY=0px; hoverScale=1; pressScale=1; } } "
            + "button(id=shift,action='ui-toggle:shifted') { position=absolute; y=140px; "
              + "width=30px; height=20px; "
            + "enterOpacity=100%; enterOffsetY=0px; hoverScale=1; pressScale=1; } }"),
        new Settings(), new SkinViewTest.Actions());
    view.resetMotion();
    view.renderMenu(new SkinViewTest.Sink(), 200, 180, -100, -100, 1);
    assertEquals(36, bounds(view, "moving")[1], EPSILON);
    click(view, "shift");
    view.renderMenu(new SkinViewTest.Sink(), 200, 180, -100, -100, 1);
    view.renderMenu(new SkinViewTest.Sink(), 200, 180, -100, -100, 1.1);
    assertEquals(46, bounds(view, "moving")[1], EPSILON,
        "Only Y movement caused by an animated preceding height may bypass its position track");
    view.renderMenu(new SkinViewTest.Sink(), 200, 180, -100, -100, 1.21);
    assertEquals(56, bounds(view, "moving")[1], EPSILON);
  }

  @Test
  void conditionalGrainToggleAnimatesInFlowWithoutOverlappingSiblings() {
    Settings settings = new Settings();
    settings.language = "ru";
    settings.menu.scale = 1.15f;
    settings.menu.animationSpeed = 1f;
    settings.theme.effects.grain = true;
    settings.validate();
    MenuController menu = new MenuController(settings, new SkinViewTest.Actions());
    menu.onOpen();
    for (int f = 0; f <= 30; f++)
      menu.render(new SkinViewTest.Sink(), 720, 450, -100, -100, f / 100d);
    actualClick(menu, "nav-settings");
    for (int f = 0; f <= 40; f++)
      menu.render(new SkinViewTest.Sink(), 720, 450, -100, -100, 0.5 + f / 100d);

    float blurOpenY = actualBounds(menu, "glass-blur")[1];
    actualClick(menu, "style-grain");
    float prevBlurY = blurOpenY;
    for (int f = 0; f <= 35; f++) {
      SkinViewTest.Sink sink = new SkinViewTest.Sink();
      menu.render(sink, 720, 450, -100, -100, 1.0 + f / 100d);
      float blurY = actualBounds(menu, "glass-blur")[1];
      assertTrue(blurY <= prevBlurY + 0.05f,
          "Collapse frame " + f + ": glass-blur Y must move monotonically up");
      prevBlurY = blurY;
      float grainAmountY = Float.NaN, grainScaleY = Float.NaN, blurLabelY = Float.NaN;
      for (SkinViewTest.Text t : sink.text) {
        if ("Сила зерна".equals(t.value)) grainAmountY = t.y;
        else if ("Размер зерна".equals(t.value)) grainScaleY = t.y;
        else if ("Блюр подложек".equals(t.value)) blurLabelY = t.y;
      }
      if (Float.isFinite(blurLabelY) && Float.isFinite(grainAmountY))
        assertTrue(blurLabelY > grainAmountY + 2f,
            "Collapse frame " + f + ": Blur label must stay strictly below Grain Amount");
      if (Float.isFinite(blurLabelY) && Float.isFinite(grainScaleY))
        assertTrue(blurLabelY > grainScaleY + 2f,
            "Collapse frame " + f + ": Blur label must stay strictly below Grain Scale");
    }
    float blurClosedY = actualBounds(menu, "glass-blur")[1];
    assertTrue(blurClosedY < blurOpenY - 40f, "Blur row must settle higher after Grain collapses");

    actualClick(menu, "style-grain");
    prevBlurY = blurClosedY;
    boolean sawIntermediate = false;
    for (int f = 0; f <= 35; f++) {
      SkinViewTest.Sink sink = new SkinViewTest.Sink();
      menu.render(sink, 720, 450, -100, -100, 2.0 + f / 100d);
      float blurY = actualBounds(menu, "glass-blur")[1];
      assertTrue(blurY >= prevBlurY - 0.05f,
          "Expand frame " + f + ": glass-blur Y must move monotonically down");
      if (blurY > blurClosedY + 5f && blurY < blurOpenY - 5f) sawIntermediate = true;
      prevBlurY = blurY;
      float grainAmountY = Float.NaN, grainScaleY = Float.NaN, blurLabelY = Float.NaN;
      for (SkinViewTest.Text t : sink.text) {
        if ("Сила зерна".equals(t.value)) grainAmountY = t.y;
        else if ("Размер зерна".equals(t.value)) grainScaleY = t.y;
        else if ("Блюр подложек".equals(t.value)) blurLabelY = t.y;
      }
      if (Float.isFinite(blurLabelY) && Float.isFinite(grainAmountY))
        assertTrue(blurLabelY > grainAmountY + 2f,
            "Expand frame " + f + ": Blur label must stay strictly below Grain Amount");
      if (Float.isFinite(blurLabelY) && Float.isFinite(grainScaleY))
        assertTrue(blurLabelY > grainScaleY + 2f,
            "Expand frame " + f + ": Blur label must stay strictly below Grain Scale");
    }
    assertTrue(sawIntermediate, "Blur row must smoothly glide down instead of snapping");
    assertEquals(blurOpenY, actualBounds(menu, "glass-blur")[1], 0.5f);
  }
}
