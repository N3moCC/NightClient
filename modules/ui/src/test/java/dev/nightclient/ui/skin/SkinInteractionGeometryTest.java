package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkinInteractionGeometryTest {
  private static SkinView view(String nc, Settings settings, SkinViewTest.Actions actions)
      throws Exception {
    return new SkinView(NcParser.parse(nc), settings, actions);
  }
  private static SkinViewTest.Sink render(
      SkinView view, int w, int h, float x, float y, double time) {
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    view.renderMenu(sink, w, h, x, y, time);
    assertEquals(0, sink.clips);
    return sink;
  }
  private static final String SLIDER =
      "menu { slider(id=opacity,bind=hud.opacity,min=0,max=1,step=.01) { width=100px; height=20px "
      + "} } component 'element.slider' { layout=absolute; "
      + "slider(id='{{elementId}}',bind='{{bind}}',min='{{min}}',max='{{max}}',step='{{step}}',"
      + "visual=none) { width=100px; height=20px; padding=0px; rect(id=fill) { position=absolute; "
      + "width='{{=ratio*100}}%'; height=3px; animateLayout=false } rect(id=thumb) { "
      + "position=absolute; x='{{=ratio*100}}%'; width=4px; height=4px; anchorX=.5; "
      + "animateLayout=false } } }";

  @Test
  void sliderUsesOneTimeBasedRatioForFillAndThumbWhileSettingsChangeImmediately() throws Exception {
    Settings settings = new Settings();
    settings.hud.opacity = 0;
    settings.theme.motion.duration = .2f;
    settings.theme.motion.easing = "linear";
    settings.theme.motion.enterScale = 1;
    settings.theme.motion.enterOpacity = 1;
    settings.theme.motion.enterOffsetY = 0;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = view(SLIDER, settings, actions);
    view.resetMotion();
    render(view, 200, 100, -1, -1, 0);
    assertTrue(view.mouseClicked(100 - .01, 10, 0));
    assertEquals(1, settings.hud.opacity, .001f);
    view.mouseReleased(100, 10, 0);
    render(view, 200, 100, -1, -1, .1);
    assertEquals(0, view.layoutBounds("fill")[2], .001f);
    render(view, 200, 100, -1, -1, .2);
    float fill = view.layoutBounds("fill")[2], thumb = view.layoutBounds("thumb")[0] + 2;
    assertEquals(50, fill, .01f);
    assertEquals(fill, thumb, .01f);
    render(view, 200, 100, -1, -1, .31);
    assertEquals(100, view.layoutBounds("fill")[2], .001f);
    settings.menu.animationSpeed = 1000000f;
    settings.hud.opacity = .25f;
    render(view, 200, 100, -1, -1, .32);
    assertEquals(25, view.layoutBounds("fill")[2], .001f);
  }

  @Test
  void expandingAndCollapsingCardClipsItsChildrenAndTheirClickableArea() throws Exception {
    Settings settings = new Settings();
    settings.theme.motion.duration = .2f;
    settings.theme.motion.easing = "linear";
    settings.theme.motion.enterScale = 1;
    settings.theme.motion.enterOpacity = 1;
    settings.theme.motion.enterOffsetY = 0;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    String nc =
        "state { expanded=false } menu { layout=absolute; panel(id=card) { width=120px; "
        + "height=auto; layout=column; button 'expand'(id=expand,action='ui-toggle:expanded') { "
        + "height=20px } div(if=ui.expanded) { height=80px; layout=absolute; button "
        + "'inside'(id=inside,action=close) { position=absolute; y=60px; width=100px; height=20px "
        + "} } } }";
    SkinView view = view(nc, settings, actions);
    view.resetMotion();
    render(view, 200, 160, -1, -1, 0);
    assertTrue(view.mouseClicked(10, 10, 0));
    view.mouseReleased(10, 10, 0);
    render(view, 200, 160, -1, -1, .01);
    assertEquals(20, view.layoutBounds("card")[3], .01f);
    assertNull(view.elementBounds("inside"));
    render(view, 200, 160, -1, -1, .11);
    float[] card = view.layoutBounds("card"), hit = view.elementBounds("inside");
    if (hit != null) assertTrue(hit[1] + hit[3] <= card[1] + card[3] + .01f);
    view.mouseClicked(10, 95, 0);
    assertEquals(0, actions.closed, "Content outside the current animated card is not clickable");
    view.mouseReleased(10, 95, 0);
    render(view, 200, 160, -1, -1, .3);
    assertNotNull(view.elementBounds("inside"));
    assertTrue(view.mouseClicked(10, 90, 0));
    assertEquals(1, actions.closed);
    view.mouseReleased(10, 90, 0);
    assertTrue(view.mouseClicked(10, 10, 0));
    view.mouseReleased(10, 10, 0);
    render(view, 200, 160, -1, -1, .31);
    assertNull(view.elementBounds("inside"), "Retiring content never accepts input");
  }

  @Test
  void popupBlankSpaceBlocksButtonsAndTooltipsBehindIt() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view =
        view("menu { layout=absolute; button 'behind'(action=close,tooltip='behind') { "
             + "width=100px; height=60px } panel { position=overlay; width=100px; height=60px } } "
             + "component 'overlay.tooltip' { panel { text '{{tooltip.text}}' } }",
            settings, actions);
    render(view, 160, 100, 10, 10, 0);
    SkinViewTest.Sink sink = render(view, 160, 100, 10, 10, 1);
    assertFalse(sink.strings().contains("behind") && sink.strings().size() > 1,
        "Covered tooltip must not be painted");
    assertTrue(view.mouseClicked(10, 10, 0));
    assertEquals(0, actions.closed);
  }

  @Test
  void tooltipWaitsWrapsUnicodeFlipsAbovePointerAndStaysInsideSmallViewport() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    String nc =
        "menu { layout=absolute; button 'h'(id=hover,tooltip='Длинная подсказка с переносом и "
        + "Unicode 😀😀 длинноесловобезпробелов') { x=140px; y=70px; width=40px; height=25px } } "
        + "component 'overlay.tooltip' { let max-width=90px; let viewport-margin=4px; let "
        + "padding-x=8px; let padding-y=6px; let offset-x=10px; let offset-y=12px; let "
        + "delay=300ms; fontSize=10px; lineHeight=1.2; panel(id=tip) { position=absolute; "
        + "x='{{tooltip.x}}'; y='{{tooltip.y}}'; width='{{=tooltip.width+16}}'; "
        + "height='{{=tooltip.height+12}}'; padding='6px 8px'; text '{{tooltip.text}}' { "
        + "width=100%; height=100%; fontSize=10px; lineHeight=1.2; padding=0px; textOverflow=wrap "
        + "} } }";
    SkinView view = view(nc, settings, actions);
    render(view, 180, 100, 170, 85, 0);
    assertNull(view.layoutBounds("tip"));
    render(view, 180, 100, 170, 85, .29);
    assertNull(view.layoutBounds("tip"));
    SkinViewTest.Sink sink = render(view, 180, 100, 170, 85, .31);
    float[] tip = view.layoutBounds("tip");
    assertNotNull(tip);
    assertTrue(tip[0] >= 4 && tip[1] >= 4 && tip[0] + tip[2] <= 176.01 && tip[1] + tip[3] <= 96.01);
    assertTrue(tip[1] < 85);
    List<SkinViewTest.Text> lines = new ArrayList<SkinViewTest.Text>();
    for (SkinViewTest.Text text : sink.text)
      if (!"h".equals(text.value)) lines.add(text);
    assertTrue(lines.size() > 1);
    for (SkinViewTest.Text text : lines) {
      assertTrue(text.x >= tip[0] + 8 - .01f && text.y >= tip[1] + 6 - .01f);
      assertTrue(text.y + 10 <= tip[1] + tip[3] - .01f);
      assertFalse(text.value.endsWith("\ud83d"));
    }
    render(view, 180, 100, -1, -1, .4);
    assertNull(view.layoutBounds("tip"));
    render(view, 180, 100, 170, 85, .41);
    assertNull(view.layoutBounds("tip"), "Returning to the same control starts a fresh delay");
  }

  @Test
  void oversizedPortalUsesViewportConstrainedScrollableGeometry() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view =
        view("menu { layout=absolute; panel(id=popup) { position=overlay; width=300px; "
             + "height=200px; layout=column; button 'first'(id=first,action=close) { height=30px } "
             + "spacer { height=150px } button 'last'(id=last,action=close) { height=30px } } }",
            settings, actions);
    render(view, 160, 100, -1, -1, 0);
    assertArrayEquals(new float[] {0, 0, 160, 100}, view.layoutBounds("popup"), .01f);
    assertNull(view.elementBounds("last"));
    assertTrue(view.mouseScrolled(20, 70, -10));
    render(view, 160, 100, -1, -1, .1);
    assertNotNull(view.elementBounds("last"));
  }

  @Test
  void originTooltipWithArrowCentersAboveTriggerAndFlipsBelowNearTopEdge() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    String nc =
        "menu { layout=absolute;"
        + " button 'mid'(id=mid,tooltip='Mid tip') { x=100px; y=80px; width=40px; height=20px }"
        + " button 'top'(id=top,tooltip='Top tip') { x=100px; y=6px; width=40px; height=20px } }"
        + " component 'overlay.tooltip' { let max-width=140px; let viewport-margin=6px;"
        + " let padding-x=7px; let padding-y=4px; let arrow-width=7px; let arrow-height=3.5px;"
        + " let side-offset=3.5px; let delay=100ms; fontSize=10px; lineHeight=1.2;"
        + " panel(id=hover-tooltip) { position=absolute; x='{{tooltip.x}}'; y='{{tooltip.y}}';"
        + " width='{{=tooltip.width+14}}'; height='{{=tooltip.height+8}}'; padding='4px 7px';"
        + " background=#09090B; borderWidth=.65px; borderColor=#27272A;"
        + " text '{{tooltip.text}}' { width=100%; height=100%; fontSize=10px; lineHeight=1.2;"
        + " padding=0px; color=#FAFAFA } } }";
    SkinView view = view(nc, settings, actions);
    final List<float[]> polys = new ArrayList<float[]>();
    SkinViewTest.Sink polySink = new SkinViewTest.Sink() {
      @Override
      public void fillPolygon(float cx, float cy, float[] xy, int color) {
        if (xy != null && xy.length >= 6) polys.add(java.util.Arrays.copyOf(xy, xy.length));
      }
    };

    view.renderMenu(polySink, 260, 160, 120, 90, 0.0);
    polys.clear();
    view.renderMenu(polySink, 260, 160, 120, 90, 0.2);
    float[] midTip = view.layoutBounds("hover-tooltip");
    assertNotNull(midTip, "Origin tooltip should appear after delay");
    assertEquals(120f, midTip[0] + midTip[2] * 0.5f, 0.5f,
        "Tooltip must center horizontally over trigger button");
    assertTrue(midTip[1] + midTip[3] < 80f, "Tooltip must render above mid trigger");
    assertTrue(polys.size() >= 2, "Tooltip must draw border + seamless fill arrow polygons");
    float tipYDown = Math.max(polys.get(0)[1], Math.max(polys.get(0)[3], polys.get(0)[5]));
    assertTrue(tipYDown > midTip[1] + midTip[3] + 2f, "Arrow apex must point down toward trigger");

    view.renderMenu(polySink, 260, 160, 120, 16, 0.3);
    polys.clear();
    view.renderMenu(polySink, 260, 160, 120, 16, 0.5);
    float[] topTip = view.layoutBounds("hover-tooltip");
    assertNotNull(topTip, "Flipped tooltip should appear after delay");
    assertTrue(topTip[1] > 26f, "Tooltip must flip below trigger near top viewport edge");
    assertTrue(polys.size() >= 2, "Flipped tooltip must draw upward arrow polygons");
    float tipYUp = Math.min(polys.get(0)[1], Math.min(polys.get(0)[3], polys.get(0)[5]));
    assertTrue(tipYUp < topTip[1] - 2f, "Flipped arrow apex must point up toward trigger");
  }
}
