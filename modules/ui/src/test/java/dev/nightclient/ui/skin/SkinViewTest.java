package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.skin.SkinParser;
import dev.nightclient.skin.NcParser;
import dev.nightclient.ui.FeatureHudRenderer;
import dev.nightclient.ui.HudRenderer;
import dev.nightclient.ui.UiActions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkinViewTest {
  static final class Text {
    final String value;
    final float x, y;
    Text(String value, float x, float y) {
      this.value = value;
      this.x = x;
      this.y = y;
    }
  }
  static class Sink implements Canvas {
    final List<Text> text = new ArrayList<Text>();
    final List<InventorySlot> stacks = new ArrayList<InventorySlot>();
    int clips, commands;
    public void rect(float x, float y, float w, float h, int c) {
      assertTrue(w >= 0 && h >= 0);
      commands++;
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      assertTrue(w >= 0 && h >= 0);
      commands++;
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      assertTrue(w >= 0 && h >= 0);
      commands++;
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      commands++;
    }
    public void line(float x, float y, float xx, float yy, float t, int c) {
      commands++;
    }
    public void circle(float x, float y, float r, int c) {
      commands++;
    }
    public void text(String value, float x, float y, float size, int c, boolean bold) {
      text.add(new Text(value, x, y));
      commands++;
    }
    public void textTracked(
        String value, float x, float y, float size, int c, boolean bold, float tracking) {
      text(value, x, y, size, c, bold);
    }
    public float textWidth(String value, float size, boolean bold) {
      return value.codePointCount(0, value.length()) * size * .5f;
    }
    public void pushClip(float x, float y, float w, float h) {
      assertTrue(w >= 0 && h >= 0);
      clips++;
    }
    public void popClip() {
      assertTrue(clips > 0);
      clips--;
    }
    public void item(String id, float x, float y, float size, float alpha) {
      commands++;
    }
    public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
      stacks.add(slot);
      commands++;
    }
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int q, float alpha) {
      commands++;
    }
    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
      commands++;
    }
    List<String> strings() {
      List<String> values = new ArrayList<String>();
      for (Text t : text) values.add(t.value);
      return values;
    }
  }
  static final class BoundarySink extends Sink {
    final int viewportWidth, viewportHeight;
    final boolean scissoredText;
    final List<float[]> bounds = new ArrayList<float[]>();
    int itemDraws;
    BoundarySink(int width, int height) {
      this(width, height, false);
    }
    BoundarySink(int width, int height, boolean scissoredText) {
      viewportWidth = width;
      viewportHeight = height;
      this.scissoredText = scissoredText;
      bounds.add(new float[] {0, 0, width, height});
    }
    private void visible(float x, float y, float width, float height, String label) {
      float[] clip = bounds.get(bounds.size() - 1);
      float epsilon = .01f;
      assertTrue(x >= -epsilon && y >= -epsilon && x + width <= viewportWidth + epsilon
              && y + height <= viewportHeight + epsilon,
          "Outside viewport: " + label);
      assertTrue(x >= clip[0] - epsilon && y >= clip[1] - epsilon && x + width <= clip[2] + epsilon
              && y + height <= clip[3] + epsilon,
          "Clipped content: " + label);
    }
    @Override
    public float textWidth(String value, float size, boolean bold) {
      return FontAtlas.get().width(value, size, bold);
    }
    @Override
    public float textWidthTracked(String value, float size, boolean bold, float tracking) {
      return FontAtlas.get().width(value, size, bold, tracking);
    }
    @Override
    public void text(String value, float x, float y, float size, int color, boolean bold) {
      textTracked(value, x, y, size, color, bold, 0);
    }
    @Override
    public void textTracked(
        final String value, float x, float y, float size, int color, boolean bold, float tracking) {
      textVisible(x, y, FontAtlas.get().width(value, size, bold, tracking), size, value);
      FontAtlas.get().visitGlyphs(value, x, y, size, bold, tracking, new FontAtlas.GlyphConsumer() {
        public void glyph(float gx, float gy, float width, float height, int pixelX, int pixelY,
            int pixelWidth, int pixelHeight) {
          textVisible(gx, gy, width, height, value + " glyph");
        }
      });
      super.text(value, x, y, size, color, bold);
    }
    private void textVisible(float x, float y, float width, float height, String label) {
      if (!scissoredText || bounds.size() <= 1) {
        visible(x, y, width, height, label);
        return;
      }
      float[] clip = bounds.get(bounds.size() - 1);
      float left = Math.max(x, clip[0]), top = Math.max(y, clip[1]),
            right = Math.min(x + width, clip[2]), bottom = Math.min(y + height, clip[3]);
      if (right > left && bottom > top) visible(left, top, right - left, bottom - top, label);
    }
    @Override
    public void pushClip(float x, float y, float width, float height) {
      super.pushClip(x, y, width, height);
      float[] parent = bounds.get(bounds.size() - 1);
      bounds.add(new float[] {Math.max(x, parent[0]), Math.max(y, parent[1]),
          Math.min(x + width, parent[2]), Math.min(y + height, parent[3])});
    }
    @Override
    public void popClip() {
      super.popClip();
      bounds.remove(bounds.size() - 1);
    }
    @Override
    public void item(String id, float x, float y, float size, float alpha) {
      visible(x, y, size, size, id);
      itemDraws++;
      super.item(id, x, y, size, alpha);
    }
    @Override
    public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
      visible(x, y, size, size, slot.itemId);
      itemDraws++;
      super.inventoryItem(slot, x, y, size, alpha);
    }
  }
  static class Actions implements UiActions {
    int changes, checkpoints, closed, reloaded;
    String clipboard = "";
    public void changed() {
      changes++;
    }
    public void checkpoint() {
      checkpoints++;
    }
    public void close() {
      closed++;
    }
    public String clipboard() {
      return clipboard;
    }
    public void clipboard(String text) {
      clipboard = text;
    }
    public List<String> profiles() {
      return Collections.emptyList();
    }
    public void saveProfile(String name) {}
    public void loadProfile(String name) {}
    public void deleteProfile(String name) {}
    public String exportProfile() {
      return "";
    }
    public void importProfile(String json) {}
    public void reset() {}
    public void undo() {}
    public boolean canUndo() {
      return false;
    }
    public List<Cooldown> cooldowns() {
      return Collections.emptyList();
    }
    public String gameVersion() {
      return "26.3";
    }
    public String saveStatus() {
      return "saved";
    }
    public void reloadSkin() {
      reloaded++;
    }
  }
  private static SkinView view(String markup, Settings settings, Actions actions) throws Exception {
    return new SkinView(
        SkinParser.parse("<nightclient>" + markup + "</nightclient>"), settings, actions);
  }

  @Test
  void keyedMasonryKeepsColumnsAndScopedExpansionWhenRowsReorder() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    final List<String> names = new ArrayList<String>(Arrays.asList("first", "second", "third"));
    Actions actions = new Actions() {
      public List<String> profiles() {
        return names;
      }
    };
    SkinView view = new SkinView(
        NcParser.parse(
            "state { expanded=false } menu { repeat(source=profiles,as=entry,key='{{entry.name}}') "
            + "{ layout=masonry; masonryStable=true; columns=2; gap=0px; width=200px; "
            + "height=150px; div(id='card.{{entry.name}}',scope='profile.{{entry.name}}') { "
            + "height=auto; button "
            + "'{{entry.name}}'(id='header.{{entry.name}}',action=close,secondary-action='local-"
            + "toggle:expanded',middle-action='local-set:expanded:true') { height=20px } text "
            + "'expanded-{{entry.name}}'(if=local.expanded) { height=40px } } } }"),
        settings, actions);
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public java.util.Map<String, Object> context() {
        List<java.util.Map<String, Object>> rows = new ArrayList<java.util.Map<String, Object>>();
        for (String name : names) rows.add(Collections.<String, Object>singletonMap("name", name));
        return Collections.<String, Object>singletonMap("profiles", rows);
      }
    });
    view.renderMenu(new Sink(), 200, 150, 0, 0, 0);
    assertEquals(0, view.layoutBounds("card.third")[0]);
    assertEquals(20, view.layoutBounds("card.third")[1]);
    assertTrue(view.mouseClicked(10, 10, 1));
    Sink opened = new Sink();
    view.renderMenu(opened, 200, 150, 0, 0, .1);
    assertTrue(opened.strings().contains("expanded-first"));
    assertFalse(opened.strings().contains("expanded-second"));
    assertEquals(0, view.layoutBounds("card.third")[0]);
    assertEquals(60, view.layoutBounds("card.third")[1]);
    assertTrue(view.mouseClicked(110, 10, 2));
    Sink middle = new Sink();
    view.renderMenu(middle, 200, 150, 0, 0, .2);
    assertTrue(middle.strings().contains("expanded-second"));
    assertEquals(0, actions.checkpoints);
    assertEquals(0, actions.changes);
    Collections.rotate(names, 1);
    view.resetMotion();
    Sink rebuilt = new Sink();
    view.renderMenu(rebuilt, 200, 150, 0, 0, .3);
    assertTrue(rebuilt.strings().contains("expanded-first"));
    assertTrue(rebuilt.strings().contains("expanded-second"));
    assertEquals(100, view.layoutBounds("card.second")[0]);
    assertEquals(0, view.layoutBounds("card.first")[0]);
  }
  @Test
  void anchoredOverlaysEscapeAncestorClippingAndOwnTheTopmostInputLayer() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    Actions actions = new Actions();
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { layout=absolute; div { width=40px; height=30px; overflow=hidden; "
            + "layout=absolute; button 'anchor'(id=trigger,action=reload-skin) { x=20px; y=10px; "
            + "width=20px; height=10px } panel(id=popup,anchor=trigger) { position=overlay; "
            + "anchorSide=below; anchorAlign=start; anchorGap=4px; width=50px; height=20px; button "
            + "'overlay'(action=close) { height=20px; padding=0px } } } button "
            + "'cover'(action=reload-skin) { x=0px; y=0px; width=150px; height=100px } }"),
        settings, actions);
    Sink sink = new Sink();
    view.renderMenu(sink, 160, 100, 0, 0, 0);
    assertEquals(0, sink.clips);
    assertArrayEquals(new float[] {20, 24, 50, 20}, view.layoutBounds("popup"), .001f);
    assertEquals("overlay", sink.strings().get(sink.strings().size() - 1));
    assertTrue(view.mouseClicked(55, 35, 0));
    assertEquals(1, actions.closed);
    assertEquals(0, actions.reloaded);
  }
  @Test
  void customChoiceUsesIntrinsicHeightAndClosesItsLocalDropdownInOneSequence() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    Actions actions = new Actions();
    SkinView view = new SkinView(
        NcParser.parse(
            "state { expanded=false } style element choice { height=15px } menu { "
            + "choice(id=picker,bind=target.style,options='card|zakohp',option-labels='Card|"
            + "Compact'); text 'after'(id=after) { height=10px } } component "
            + "'element.choice'(scope='choice.{{bind}}') { intrinsicHeight=true; height=auto; "
            + "button '{{chosenLabel}}'(action='local-toggle:expanded') { height=15px } "
            + "repeat(source=choiceOptions,as=option,key='{{option.value}}',if=local.expanded) { "
            + "button "
            + "'{{option.label}}'(action='sequence:set:{{bind}}:{{option.value}};local-set:"
            + "expanded:false') { height=18px } } }"),
        settings, actions);
    view.renderMenu(new Sink(), 200, 150, 0, 0, 0);
    assertEquals(15, view.layoutBounds("after")[1]);
    assertTrue(view.mouseClicked(10, 7, 0));
    view.renderMenu(new Sink(), 200, 150, 0, 0, .1);
    assertEquals(51, view.layoutBounds("after")[1]);
    assertTrue(view.mouseClicked(10, 42, 0));
    assertEquals("zakohp", settings.target.style);
    view.renderMenu(new Sink(), 200, 150, 0, 0, .2);
    assertEquals(15, view.layoutBounds("after")[1]);
    assertEquals(1, actions.checkpoints);
    assertEquals(1, actions.changes);
  }
  @Test
  void smoothScrollIsTimeBasedAndHitGeometryFollowsItsDisplayedOffset() throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions();
    SkinView view = new SkinView(
        NcParser.parse("menu { column { height=20px; overflow=scroll; scrollDuration=100ms; "
                        + "scrollStep=20px; easing=linear; button 'one'(id=one,action=reload-skin) "
                        + "{ height=20px } button 'two'(id=two,action=close) { height=20px } } }"),
        settings, actions);
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertTrue(view.mouseScrolled(10, 10, -1));
    view.renderMenu(new Sink(), 200, 100, 0, 0, .01);
    assertEquals(20, view.layoutBounds("two")[1], .001f);
    view.renderMenu(new Sink(), 200, 100, 0, 0, .06);
    assertEquals(10, view.layoutBounds("two")[1], .001f);
    assertTrue(view.mouseClicked(10, 15, 0));
    assertEquals(1, actions.closed);
    view.renderMenu(new Sink(), 200, 100, 0, 0, .12);
    assertEquals(0, view.layoutBounds("two")[1], .001f);
  }
  @Test
  void releaseCommittedSlidersPreviewTheirValueWithoutChangingTheLayoutScale() throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions();
    float initial = settings.menu.scale;
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { slider(id=scale,bind=menu.scale,commit=release,min=.75,max=1.5,step=.05) { "
            + "width=100px; height=20px; padding=0px } text '{{menu.scale}}' }"),
        settings, actions);
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertTrue(view.mouseClicked(99, 10, 0));
    assertTrue(view.mouseDragged(100, 10, 0, 1, 0));
    assertEquals(initial, settings.menu.scale, .001f);
    assertEquals(0, actions.changes);
    view.renderMenu(new Sink(), 200, 100, 0, 0, .01);
    assertEquals(initial, settings.menu.scale, .001f);
    assertTrue(view.mouseReleased(100, 10, 0));
    assertEquals(1.5f, settings.menu.scale, .001f);
    assertEquals(1, actions.checkpoints);
    assertEquals(1, actions.changes);
    view.renderMenu(new Sink(), 200, 100, 0, 0, .02);
    view.mouseClicked(1, 10, 0);
    view.keyPressed(256, 0, 0);
    view.mouseReleased(1, 10, 0);
    assertEquals(1.5f, settings.menu.scale, .001f);
  }
  @Test
  void clipboardColorActionsRoundTripRgbaWithoutLeakingIntoPresentationState() throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions();
    settings.theme.accent = 0x80112233;
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { button 'copy'(action='clipboard-copy:theme.accent') { height=20px } button "
            + "'paste'(action='clipboard-paste:theme.accent') { height=20px } }"),
        settings, actions);
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertTrue(view.mouseClicked(10, 10, 0));
    assertEquals("#11223380", actions.clipboard);
    assertEquals(0, actions.checkpoints);
    actions.clipboard = "#FFEEDD66";
    assertTrue(view.mouseClicked(10, 30, 0));
    assertEquals(0x66ffeedd, settings.theme.accent);
    assertEquals(1, actions.checkpoints);
    assertEquals(1, actions.changes);
  }
  @Test
  void fullyClippedControlsHaveNoReachableBoundsUntilScrolledIntoView() throws Exception {
    SkinView view = new SkinView(
        NcParser.parse("menu { column { height=20px; overflow=scroll; scrollStep=20px; button "
                        + "'one'(id=one,action=close) { height=20px } button "
                        + "'two'(id=two,action=close) { height=20px } } }"),
        new Settings(), new Actions());
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertNotNull(view.elementBounds("one"));
    assertNull(view.elementBounds("two"));
    assertTrue(view.mouseScrolled(10, 10, -1));
    view.renderMenu(new Sink(), 200, 100, 0, 0, .1);
    assertNotNull(view.elementBounds("two"));
    assertNull(view.elementBounds("one"));
  }
  @Test
  void hudLayoutTransitionsRunWithoutOpeningTheMenuAndRetainChildTextScale() throws Exception {
    Settings settings = new Settings();
    settings.theme.motion.duration = .2f;
    settings.theme.motion.easing = "linear";
    settings.theme.motion.layout = true;
    settings.theme.motion.enterOpacity = 1;
    SkinView view = new SkinView(
        NcParser.parse("hud { layout=absolute; panel(id=widget) { let panel-height='hud.width'; "
                        + "position=absolute; width=100px; height='{{= vars.panel_height}}'; "
                        + "animateLayout=true; transitionDuration=200ms; easing=linear; text "
                        + "'fixed'(id=label) { height=10px; fontSize=10px } } }"),
        settings, new Actions());
    settings.hud.width = 100;
    view.renderHud(new Sink(), 300, 300, 0, null, null, null);
    assertEquals(100, view.layoutBounds("widget")[3], .001f);
    settings.hud.width = 200;
    view.renderHud(new Sink(), 300, 300, .1, null, null, null);
    assertEquals(100, view.layoutBounds("widget")[3], .001f);
    view.renderHud(new Sink(), 300, 300, .2, null, null, null);
    assertEquals(150, view.layoutBounds("widget")[3], .001f);
    assertEquals(10, view.layoutBounds("label")[3], .001f);
    view.renderHud(new Sink(), 300, 300, .31, null, null, null);
    assertEquals(200, view.layoutBounds("widget")[3], .001f);
  }
  @Test
  void instantHudRendersExposeTargetBoundsWithoutResettingTheLiveAnimation() throws Exception {
    Settings settings = new Settings();
    settings.theme.motion.enterOpacity = 0;
    settings.theme.motion.enterDuration = .2f;
    SkinView view = new SkinView(
        NcParser.parse("hud { layout=absolute; panel(id=widget) { position=absolute; width=100px; "
                        + "height='{{hud.width}}'; animateLayout=true; transitionDuration=200ms; "
                        + "easing=linear; text 'content' { height=10px } } }"),
        settings, new Actions());
    settings.hud.width = 100;
    view.renderHud(new Sink(), 300, 300, 0, null, null, null);
    settings.hud.width = 200;
    view.renderHud(new Sink(), 300, 300, .1, null, null, null);
    assertEquals(100, view.layoutBounds("widget")[3], .001f);
    assertEquals(200, view.hudBoundsInstant("widget", 300, 300, false)[3], .001f);
    final List<Integer> colors = new ArrayList<Integer>();
    Sink instant = new Sink() {
      @Override
      public void text(String value, float x, float y, float size, int color, boolean bold) {
        colors.add(color);
        super.text(value, x, y, size, color, bold);
      }
    };
    assertTrue(view.renderHudWidgetInstant("widget", instant, 300, 300, false));
    assertFalse(colors.isEmpty());
    assertTrue(colors.get(0) >>> 24 > 0);
    view.renderHud(new Sink(), 300, 300, .2, null, null, null);
    assertEquals(150, view.layoutBounds("widget")[3], .001f);
  }
  @Test
  void focusedControlBorderIsOwnedByTheStylesheetWithoutAnAddedNativeOutline() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    final List<Float> widths = new ArrayList<Float>();
    final List<Integer> colors = new ArrayList<Integer>();
    Sink sink = new Sink() {
      @Override
      public void strokeRoundedRect(
          float x, float y, float width, float height, float radius, float thickness, int color) {
        widths.add(thickness);
        colors.add(color);
        super.strokeRoundedRect(x, y, width, height, radius, thickness, color);
      }
    };
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { borderWidth=0px; input(bind=protect.replacement) { width=100px; height=20px; "
            + "backgroundOpacity=100%; borderWidth=.25px; borderColor=#111111; focus { "
            + "borderWidth=.4px; borderColor=#123456 } } }"),
        settings, new Actions());
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertTrue(view.mouseClicked(10, 10, 0));
    view.renderMenu(sink, 200, 100, 0, 0, .1);
    assertEquals(1, widths.size());
    assertEquals(.4f, widths.get(0), .001f);
    assertEquals(0xff123456, colors.get(0).intValue());
  }
  @Test
  void keyedRepeatRetainsSnapshotsDuringExitWithoutAcceptingInputAndCollapsesNaturalHeight()
      throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions();
    final List<java.util.Map<String, Object>> rows = new ArrayList<java.util.Map<String, Object>>();
    rows.add(Collections.<String, Object>singletonMap("name", "first"));
    SkinView view =
        new SkinView(NcParser.parse("menu { column(id=container) { height=auto; "
                                     + "repeat(source=profiles,as=entry,key='{{entry.name}}') { "
                                     + "retainExit=true; exitDuration=80ms; easing=linear; button "
                                     + "'{{entry.name}}'(id='row.{{entry.name}}',action=close) { "
                                     + "height=20px; padding=0px } } } }"),
            settings, actions);
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public java.util.Map<String, Object> context() {
        return Collections.<String, Object>singletonMap("profiles", rows);
      }
    });
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertNotNull(view.elementBounds("row.first"));
    assertEquals(20, view.layoutBounds("container")[3], .001f);
    rows.clear();
    view.renderMenu(new Sink(), 200, 100, 0, 0, .01);
    assertNull(view.elementBounds("row.first"));
    assertFalse(view.mouseClicked(10, 10, 0));
    final List<Integer> colors = new ArrayList<Integer>();
    Sink halfway = new Sink() {
      @Override
      public void text(String value, float x, float y, float size, int color, boolean bold) {
        colors.add(color);
        super.text(value, x, y, size, color, bold);
      }
    };
    view.renderMenu(halfway, 200, 100, 0, 0, .05);
    assertTrue(halfway.strings().contains("first"));
    assertEquals(10, view.layoutBounds("container")[3], .001f);
    assertTrue(colors.get(0) >>> 24 > 100 && colors.get(0) >>> 24 < 155);
    Sink gone = new Sink();
    view.renderMenu(gone, 200, 100, 0, 0, .1);
    assertFalse(gone.strings().contains("first"));
    assertEquals(0, view.layoutBounds("container")[3], .001f);
    assertEquals(0, actions.closed);
  }
  @Test
  void textMeasurementFunctionsUseTheDrawingCanvasAndRealFormattedRows() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    Actions actions = new Actions() {
      @Override
      public List<Cooldown> cooldowns() {
        return Arrays.asList(new Cooldown("a", "Short", "minecraft:stone", 12, 20),
            new Cooldown("b", "A much longer name", "minecraft:stone", 125, 180));
      }
    };
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { panel(id=measured) { width=\"{{= "
            + "maxTextWidth('cooldownRows','name',10,'studio-sans-medium')+maxTextWidth('"
            + "cooldownRows','remainingSeconds|duration',10,'studio-sans-medium')}}\"; "
            + "height=20px; text \"{{= format(1.234,'fixed:2')}}\" } panel(id=single) { "
            + "width=\"{{= textWidth('ABCDE',10,'studio-sans-medium',false,1)}}\"; height=20px } "
            + "}"),
        settings, actions);
    Sink canvas = new Sink();
    view.renderMenu(canvas, 400, 100, 0, 0, 0);
    assertEquals(18 * 5 + 4 * 5, view.layoutBounds("measured")[2], .001f);
    assertEquals(29, view.layoutBounds("single")[2], .001f);
    assertTrue(canvas.strings().contains("1.23"));
  }
  @Test
  void retainedCountKeepsTheLastConditionalPanelAliveOnlyForTheDeclaredExit() throws Exception {
    Settings settings = new Settings();
    final List<java.util.Map<String, Object>> rows = new ArrayList<java.util.Map<String, Object>>();
    rows.add(Collections.<String, Object>singletonMap("name", "last"));
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { panel(id=conditional,if=\"{{= retainedCount('profiles')>0}}\") { height=20px; "
            + "repeat(source=profiles,as=entry,key='{{entry.name}}') { retainExit=true; "
            + "exitDuration=80ms; easing=linear; text '{{entry.name}}' { height=20px } } } }"),
        settings, new Actions());
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public java.util.Map<String, Object> context() {
        return Collections.<String, Object>singletonMap("profiles", rows);
      }
    });
    view.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    rows.clear();
    Sink firstExit = new Sink();
    view.renderMenu(firstExit, 200, 100, 0, 0, .01);
    assertNotNull(view.layoutBounds("conditional"));
    assertTrue(firstExit.strings().contains("last"));
    view.renderMenu(new Sink(), 200, 100, 0, 0, .05);
    assertNotNull(view.layoutBounds("conditional"));
    view.renderMenu(new Sink(), 200, 100, 0, 0, .1);
    assertNull(view.layoutBounds("conditional"));
  }

  @Test
  void actualSettingsControlsWorkWithDragUnicodeInputClipboardAndActions() throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions();
    Sink sink = new Sink();
    SkinView view =
        view("<menu padding=\"10\" gap=\"6\"><toggle bind=\"saturation.enabled\" "
             + "height=\"30\">Saturation</toggle><slider bind=\"saturation.strength\" min=\"0\" "
             + "max=\"2\" step=\"0.1\" height=\"30\"/><input bind=\"protect.replacement\" "
             + "height=\"30\"/><button action=\"close\" height=\"30\">Close</button></menu>",
            settings, actions);
    assertTrue(view.renderMenu(sink, 200, 200, 0, 0, 0));
    assertEquals(0, sink.clips);
    assertTrue(view.mouseClicked(20, 20, 0));
    assertTrue(settings.saturation.enabled);
    assertTrue(view.mouseClicked(20, 55, 0));
    assertTrue(view.mouseDragged(190, 55, 0, 170, 0));
    view.mouseReleased(190, 55, 0);
    assertEquals(2, settings.saturation.strength, .001f);
    assertTrue(view.mouseClicked(20, 95, 0));
    assertTrue(view.isTextFocused());
    view.keyPressed(65, 0, 2);
    view.charTyped('М', 0);
    view.charTyped('о', 0);
    view.charTyped('й', 0);
    assertEquals("Мой", settings.protect.replacement);
    actions.clipboard = "Игрок";
    view.keyPressed(65, 0, 2);
    view.keyPressed(86, 0, 2);
    assertEquals("Игрок", settings.protect.replacement);
    assertTrue(view.mouseClicked(20, 130, 0));
    assertEquals(1, actions.closed);
    assertFalse(view.isTextFocused());
    assertTrue(actions.changes >= 5);
    assertEquals(3, actions.checkpoints);
  }

  @Test
  void hexInputAllowsIncompleteTypingAndCommitsTheCompleteRgbaColor() throws Exception {
    Settings s = new Settings();
    Actions a = new Actions();
    SkinView v = view("<menu><input bind=\"theme.accent\" format=\"hex\"/></menu>", s, a);
    v.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    v.mouseClicked(20, 15, 0);
    v.keyPressed(65, 0, 2);
    for (char c : "#11223380".toCharArray()) v.charTyped(c, 0);
    assertEquals(0x80112233, s.theme.accent);
  }

  @Test
  void percentagesAreAppliedOnceAndPageActionsChangeTheVisibleTree() throws Exception {
    Settings s = new Settings();
    Actions a = new Actions();
    SkinView v =
        view("<menu><row width=\"200\" height=\"30\"><text width=\"50%\">Left</text><text "
             + "width=\"50%\">Right</text></row><button action=\"page:edit\">Edit</button><text "
             + "if=\"page=edit\">Editor</text></menu>",
            s, a);
    Sink sink = new Sink();
    v.renderMenu(sink, 200, 150, 0, 0, 0);
    assertEquals(8, sink.text.get(0).x);
    assertEquals(108, sink.text.get(1).x);
    assertFalse(sink.strings().contains("Editor"));
    v.mouseClicked(10, 45, 0);
    sink = new Sink();
    v.renderMenu(sink, 200, 150, 0, 0, 0);
    assertTrue(sink.strings().contains("Editor"));
    assertEquals(0, sink.clips);
  }

  @Test
  void repeatedHudUsesActualTimerValuesAndExactNativeInventorySlots() throws Exception {
    Settings s = new Settings();
    Actions a = new Actions();
    SkinView v = view("<hud><repeat source=\"cooldowns\" as=\"item\"><text>{{item.name}} "
                      + "{{item.remainingSeconds|fixed:1}}s</text></repeat><repeat "
                      + "source=\"inventory\" as=\"slot\"><item source=\"slot\"/></repeat></hud>",
        s, a);
    InventorySlot slot =
        new InventorySlot(9, "minecraft:diamond_sword", "Server sword", 1, 20, 100, true);
    Sink sink = new Sink();
    assertTrue(v.renderHud(sink, 400, 200, 0,
        Collections.singletonList(new Cooldown("a", "Pearl", "minecraft:ender_pearl", 3.25f, 10)),
        Collections.singletonList(slot), Collections.<TntView>emptyList()));
    assertTrue(sink.strings().contains("Pearl 3.3s"));
    assertEquals(Collections.singletonList(slot), sink.stacks);
    assertSame(slot, sink.stacks.get(0));
    assertEquals(0, sink.clips);
    sink = new Sink();
    v.renderHud(sink, 400, 200, 0, Collections.<Cooldown>emptyList(),
        Collections.<InventorySlot>emptyList(), Collections.<TntView>emptyList());
    assertTrue(sink.text.isEmpty());
    assertTrue(sink.stacks.isEmpty());
  }

  @Test
  void clippedScrollPreventsClickingHiddenControlsAndDoesNotAccumulateOldHits() throws Exception {
    Settings s = new Settings();
    Actions a = new Actions();
    SkinView v = view("<menu><column height=\"40\" overflow=\"auto\"><button height=\"30\" "
                      + "action=\"reload-skin\">Reload</button><button height=\"30\" "
                      + "action=\"close\">Close</button></column></menu>",
        s, a);
    v.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertFalse(v.mouseClicked(10, 50, 0));
    assertEquals(0, a.closed);
    assertTrue(v.mouseScrolled(10, 20, -1));
    v.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    assertTrue(v.mouseClicked(10, 20, 0));
    assertEquals(1, a.closed);
    v.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    v.renderMenu(new Sink(), 200, 100, 0, 0, 0);
    v.mouseClicked(10, 20, 0);
    assertEquals(2, a.closed);
  }

  @Test
  void optionalComponentReplacementAndNoMenuFallbackAreExplicit() throws Exception {
    Settings s = new Settings();
    Actions a = new Actions();
    SkinView v =
        view("<component name=\"menu.card.protect\"><text>{{label}}</text></component>", s, a);
    Sink sink = new Sink();
    assertFalse(v.renderMenu(sink, 400, 200, 0, 0, 0));
    assertFalse(v.renderComponent(
        "missing", sink, 20, 30, 100, 40, 0, Collections.<String, Object>emptyMap()));
    assertTrue(v.renderComponent("menu.card.protect", sink, 20, 30, 100, 40, 0,
        Collections.<String, Object>singletonMap("label", "Protect")));
    assertEquals(Collections.singletonList("Protect"), sink.strings());
    assertEquals(28, sink.text.get(0).x);
    assertEquals(0, sink.clips);
  }

  @Test
  void shippedDocumentRendersWithActualDataAndKeepNativeFeatureTogglesWorking() throws Exception {
    Settings settings = new Settings();
    settings.inventory.enabled = settings.tnt.enabled = settings.crosshair.enabled = true;
    InventorySlot slot =
        new InventorySlot(9, "minecraft:diamond_sword", "Actual sword", 1, 20, 100, true);
    for (String file : Collections.singletonList("default.nc")) {
      byte[] sourceBytes = dev.nightclient.skin.DefaultSkin.source();
      Actions actions = new Actions();
      SkinView view = new SkinView(NcParser.parse(sourceBytes), settings, actions);
      Sink menu = new Sink();
      assertTrue(view.renderMenu(menu, 1280, 800, 0, 0, 0));
      assertTrue(menu.commands > 0);
      assertEquals(0, menu.clips);
      Sink hud = new Sink();
      assertTrue(view.renderHud(hud, 1280, 800, 0,
          Collections.singletonList(new Cooldown("p", "Pearl", "minecraft:ender_pearl", 3, 10)),
          Collections.singletonList(slot), Collections.singletonList(new TntView(1, 0, 0, 2, 4))));
      assertTrue(hud.stacks.contains(slot));
      assertEquals(0, hud.clips);
    }
    settings.crosshair.enabled = false;
    SkinView only =
        view("<hud><crosshair width=\"100%\" height=\"100%\"/></hud>", settings, new Actions());
    Sink hidden = new Sink();
    only.renderHud(hidden, 400, 200, 0, null, null, null);
    assertEquals(0, hidden.commands);
    assertEquals(0, hidden.clips);
  }

  @Test
  void packagedHudTimersAndInventoryRemainFullyVisibleAtRealScaledWindowSizes() throws Exception {
    List<Cooldown> timers =
        Arrays.asList(new Cooldown("shield", "Shield", "minecraft:shield", 599, 600),
            new Cooldown("pearl", "Ender Pearl", "minecraft:ender_pearl", 589, 600),
            new Cooldown("fruit", "Chorus Fruit", "minecraft:chorus_fruit", 579, 600));
    List<InventorySlot> slots = new ArrayList<InventorySlot>();
    for (int i = 9; i < 36; i++)
      slots.add(new InventorySlot(i, "minecraft:stone", "Stone", 1, 0, 0));
    for (String file : Collections.singletonList("default.nc")) {
      byte[] sourceBytes = dev.nightclient.skin.DefaultSkin.source();
      for (int[] viewport : new int[][] {{512, 320}, {640, 400}}) {
        Settings settings = new Settings();
        settings.inventory.enabled = true;
        settings.inventory.rows = 3;
        SkinView view = new SkinView(NcParser.parse(sourceBytes), settings, new Actions());
        BoundarySink canvas = new BoundarySink(viewport[0], viewport[1], true);
        assertTrue(view.renderHud(canvas, viewport[0], viewport[1], 0, timers, slots,
                       Collections.<TntView>emptyList()),
            file);
        for (String name : Arrays.asList("Shield", "Ender Pearl", "Chorus Fruit"))
          assertTrue(canvas.strings().contains(name), file + ": " + name);
        for (String time : Arrays.asList("9:59", "9:49", "9:39"))
          assertTrue(canvas.strings().contains(time), file + ": timer " + time);
        assertEquals(30, canvas.itemDraws,
            file + ": three actual cooldown items plus 27 native inventory slots");
        assertEquals(27, canvas.stacks.size());
        assertEquals(0, canvas.clips);
        assertEquals(1, canvas.bounds.size());
      }
    }
  }

  @Test
  void expandedRepeatHasAFiniteDrawingBudgetAndAlwaysBalancesItsClips() throws Exception {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 2000; i++) text.append('A');
    Settings settings = new Settings();
    SkinView view = view(
        "<hud><repeat source=\"inventory\"><text height=\"20\">" + text + "</text></repeat></hud>",
        settings, new Actions());
    List<InventorySlot> slots = new ArrayList<InventorySlot>();
    for (int slot = 0; slot < 36; slot++)
      slots.add(new InventorySlot(slot, "minecraft:stone", "Stone", 1, 0, 0));
    Sink canvas = new Sink();
    view.renderHud(canvas, 400, 1000, 0, null, slots, null);
    assertEquals(4, canvas.text.size());
    assertEquals(0, canvas.clips);
  }

  @Test
  void separateHudComponentsReplaceRealWidgetsAndRespectCameraVisibility() throws Exception {
    Settings settings = new Settings();
    settings.inventory.enabled = settings.tnt.enabled = settings.crosshair.enabled = true;
    String markup =
        "<component name=\"cooldowns\"><repeat source=\"cooldowns\"><text>CD "
        + "{{item.name}}</text></repeat></component><component name=\"hud.inventory\"><repeat "
        + "source=\"inventory\"><item/></repeat></component><component name=\"tnt\"><repeat "
        + "source=\"tnt\"><text>TNT {{item.seconds|fixed:1}}</text></repeat></component><component "
        + "name=\"crosshair\"><text>Reticle</text></component>";
    SkinView view = view(markup, settings, new Actions());
    List<Cooldown> cooldowns =
        Collections.singletonList(new Cooldown("a", "Pearl", "minecraft:ender_pearl", 3, 10));
    InventorySlot slot = new InventorySlot(9, "minecraft:stone", "Stone", 1, 0, 0);
    List<InventorySlot> inventory = Collections.singletonList(slot);
    List<TntView> tnt = Collections.singletonList(new TntView(1, 0, 0, 2, 4));
    Sink canvas = new Sink();
    assertFalse(view.renderHud(canvas, 1000, 700, 0, cooldowns, inventory, tnt, false));
    HudRenderer.render(canvas, settings, cooldowns, 1000, 700, 0, false, view);
    FeatureHudRenderer.render(canvas, settings, inventory, tnt, 1000, 700, 0, false, false, view);
    assertTrue(canvas.strings().contains("CD Pearl"));
    assertTrue(canvas.strings().contains("TNT 2.0"));
    assertFalse(canvas.strings().contains("Reticle"));
    assertEquals(Collections.singletonList(slot), canvas.stacks);
    canvas = new Sink();
    view.renderHud(canvas, 1000, 700, 0, cooldowns, inventory, tnt, true);
    FeatureHudRenderer.render(canvas, settings, inventory, tnt, 1000, 700, 0, false, true, view);
    assertTrue(canvas.strings().contains("Reticle"));
    assertEquals(0, canvas.clips);
    canvas = new Sink();
    FeatureHudRenderer.render(canvas, settings, inventory, tnt, 1000, 700, 0, true, true, view);
    assertEquals(0, canvas.commands);
  }

  @Test
  void scriptedCrosshairUsesActualCameraVisibilityAndInvalidDynamicIconsAreOmitted()
      throws Exception {
    Settings settings = new Settings();
    settings.crosshair.enabled = true;
    SkinView view = view("<hud><crosshair width=\"100%\" height=\"100%\"/><icon "
                         + "src=\"{{gameVersion}}\"/><item src=\"{{gameVersion}}\"/></hud>",
        settings, new Actions());
    Sink canvas = new Sink();
    view.renderHud(canvas, 400, 200, 0, null, null, null, false);
    assertEquals(0, canvas.commands);
    assertEquals(0, canvas.clips);
    canvas = new Sink();
    view.renderHud(canvas, 400, 200, 0, null, null, null, true);
    assertTrue(canvas.commands > 0);
    assertEquals(0, canvas.clips);
  }

  @Test
  void rootMenuPaintsItsInheritedThemeWhileEmptyHudPreservesTheGameScene() throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions();
    SkinView view = view(
        "<style>:root { background:#123; opacity:90%; }</style><menu/><hud/>", settings, actions);
    Sink menu = new Sink(), hud = new Sink();
    assertTrue(view.renderMenu(menu, 400, 200, 0, 0, 0));
    assertTrue(menu.commands > 0);
    assertEquals(0, menu.clips);
    assertTrue(view.renderHud(hud, 400, 200, 0, null, null, null));
    assertEquals(0, hud.commands);
    assertEquals(0, hud.clips);
  }

  @Test
  void markerManagerCreatesRenamesHidesAndDeletesSavedWaypoints() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    Waypoint home = settings.waypoints.add("Home", 10.4, 64, -20.2);
    home.id = "abcd1234";
    home.icon = "anchor";
    final boolean[] room = {true};
    Actions actions = new Actions() {
      public boolean waypointAddHere() {
        return room[0] && settings.waypoints.add("", 1, 2, 3) != null;
      }
    };
    SkinView view = new SkinView(
        NcParser.parse(
            "state { waypointDraftName=\"\"; waypointDraftId=\"\"; waypointDraftX=\"\"; "
            + "waypointDraftY=\"\"; waypointDraftZ=\"\"; waypointDraftColor=\"\"; "
            + "waypointDraftDimension=\"\"; waypointDeleteConfirm=false; waypointManager=\"\" } "
            + "menu { layout=column; "
            + "button 'open'(id=open,action='waypoint-manager-open') { height=16px } "
            + "text 'manager-{{= ui.waypointManager}}'(id=manager) { height=10px; fontSize=8px } "
            + "text '{{= markersEmpty ? \"none\" : \"listed\"}}'(id=empty) { height=10px; "
              + "fontSize=8px } "
            + "repeat(source=savedWaypoints,as=marker,key='{{marker.id}}') { button "
              + "'{{marker.name}}'(id='row-{{marker.id}}',action='waypoint-visibility:{{marker.id}}"
              + "') { height=14px } } "
            + "button 'here'(id=here,action='waypoint-here') { height=16px } "
            + "button 'create'(id=create,action='waypoint-create-open') { height=16px } "
            + "button 'edit'(id=edit,action='waypoint-edit:abcd1234') { height=16px } "
            + "input(id=draft-name,bind=ui.waypointDraftName, maxlength=32) { height=16px } "
            + "button 'draft-name'(id=draft-name-button,action='ui-set:waypointDraftName:Depot') { "
              + "height=16px } "
            + "button 'new-name'(id=new-name,action='ui-set:waypointDraftName:Base') { height=16px "
              + "} "
            + "button 'apply'(id=apply,action='waypoint-apply') { height=16px } "
            + "button 'remove'(id=remove,action='waypoint-remove:abcd1234') { height=16px } "
            + "button 'delete-all'(id=delete-all,action='waypoint-delete-all') { height=16px } "
            + "text '{{= ui.waypointDeleteConfirm ? \"confirm\" : \"idle\"}}'(id=confirm) { "
              + "height=10px; fontSize=8px } "
            + "button 'stale'(id=stale,action='waypoint-apply') { height=16px } } "
            + "component 'overlay.notification' { panel(id=toast) { position=absolute; x=6px; "
              + "y=6px; width=200px; height=20px; text '{{notification.text}}' { height=20px; "
              + "fontSize=8px } } }"),
        settings, actions);
    String added = dev.nightclient.i18n.I18n.text(settings.language, "waypoint.added");
    Sink roster = new Sink();
    view.renderMenu(roster, 220, 300, 110, 150, 0);
    assertTrue(roster.strings().contains("listed"), "The saved marker list reaches the skin");
    assertTrue(click(view, "here", 0).strings().contains(added));
    assertEquals(2, settings.waypoints.points.size());
    room[0] = false;
    assertTrue(click(view, "here", .2)
            .strings()
            .contains(dev.nightclient.i18n.I18n.text(settings.language, "waypoint.full")));
    assertEquals(2, settings.waypoints.points.size());
    Sink unnamed = new Sink();
    view.renderMenu(unnamed, 220, 300, 110, 150, .4);
    assertTrue(
        unnamed.strings().contains("1 2 3"), "An unnamed marker falls back to its coordinates");
    click(view, "open", .45);
    click(view, "create", .5);
    Sink create = new Sink();
    view.renderMenu(create, 220, 300, 110, 150, .55);
    assertTrue(create.strings().contains("manager-create"), "Create opens the manager form");
    click(view, "new-name", .6);
    click(view, "apply", .65);
    assertEquals(3, settings.waypoints.points.size());
    assertEquals("Base", settings.waypoints.points.get(2).label);
    click(view, "edit", .7);
    Sink pending = new Sink();
    view.renderMenu(pending, 220, 300, 110, 150, .75);
    assertTrue(pending.strings().contains("manager-edit"), "Editing a marker opens its form");
    click(view, "draft-name-button", .8);
    Sink applied = click(view, "apply", .9);
    assertEquals("Depot", home.label);
    assertTrue(
        applied.strings().contains(dev.nightclient.i18n.I18n.text(settings.language, "ui.saved")));
    Sink saved = new Sink();
    view.renderMenu(saved, 220, 300, 110, 150, 1.0);
    assertTrue(saved.strings().contains("manager-list"), "Applying returns to the list");
    assertTrue(saved.strings().contains("Depot"));
    click(view, "row-abcd1234", 1.1);
    assertFalse(home.visible);
    click(view, "remove", 1.2);
    assertFalse(settings.waypoints.points.contains(home));
    assertEquals(2, settings.waypoints.points.size());
    view.renderMenu(new Sink(), 220, 300, 110, 150, 1.3);
    assertNull(view.elementBounds("row-abcd1234"));
    click(view, "stale", 1.4);
    assertEquals(
        2, settings.waypoints.points.size(), "A stale edit cannot resurrect a deleted marker");
    click(view, "open", 1.5);
    click(view, "delete-all", 1.6);
    Sink confirm = new Sink();
    view.renderMenu(confirm, 220, 300, 110, 150, 1.65);
    assertTrue(confirm.strings().contains("confirm"));
    assertEquals(2, settings.waypoints.points.size());
    click(view, "delete-all", 1.7);
    assertEquals(0, settings.waypoints.points.size());
  }

  @Test
  void waypointUiFiltersByCurrentWorldScopesDeleteAllAndBindsOtherWorldMarkers() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    String worldA = "mp:holyworld.ru/s:aaaa1111";
    String worldB = "mp:holyworld.ru/s:bbbb2222";
    Waypoint a = settings.waypoints.add("AlphaBase", 10, 64, 10, Waypoint.DEFAULT_DIMENSION,
        worldA, "holyworld.ru · Соло #1");
    a.id = "aaaa1111";
    Waypoint b = settings.waypoints.add("BetaBase", 20, 64, 20, Waypoint.DEFAULT_DIMENSION,
        worldB, "holyworld.ru · Дуо #2");
    b.id = "bbbb2222";
    Actions actions = new Actions() {
      @Override
      public String currentWorldId() {
        return worldA;
      }

      @Override
      public String currentWorldLabel() {
        return "holyworld.ru · Соло #1";
      }
    };
    SkinView view = new SkinView(
        NcParser.parse(
            "state { waypointScope=\"world\"; waypointDeleteConfirm=false; waypointManager=\"\" } "
            + "menu { layout=column; "
            + "text '{{currentWorldLabel}}'(id=world-label) { height=10px; fontSize=8px } "
            + "button 'scope-world'(id=scope-world,action='ui-set:waypointScope:world') { height=16px } "
            + "button 'scope-all'(id=scope-all,action='ui-set:waypointScope:all') { height=16px } "
            + "button 'delete-all'(id=delete-all,action='waypoint-delete-all') { height=16px } "
            + "repeat(source=savedWaypoints,as=marker,key='{{marker.id}}') { "
            + "row { height=16px; text '{{marker.name}}' { grow=1; fontSize=8px } "
            + "button 'bind'(if='marker.otherWorld',id='bind-{{marker.id}}',action='waypoint-bind-world:{{marker.id}}') { width=40px; height=14px } } } }"),
        settings, actions);

    Sink initial = new Sink();
    view.renderMenu(initial, 220, 300, 110, 150, 0);
    assertTrue(initial.strings().contains("AlphaBase"));
    assertFalse(initial.strings().contains("BetaBase"));

    click(view, "delete-all", .1);
    click(view, "delete-all", .2);
    assertEquals(1, settings.waypoints.points.size());
    assertEquals("BetaBase", settings.waypoints.points.get(0).label);

    Sink all = click(view, "scope-all", .3);
    assertTrue(all.strings().contains("BetaBase"));
    assertNotNull(view.elementBounds("bind-bbbb2222"));
    click(view, "bind-bbbb2222", .4);
    assertTrue(b.matchesWorld(worldA));
    assertEquals("holyworld.ru · Соло #1", b.worldLabel);

    Sink backToWorld = click(view, "scope-world", .5);
    assertTrue(backToWorld.strings().contains("BetaBase"));
  }
  private static Sink click(SkinView view, String id, double time) {
    float[] box = view.elementBounds(id);
    assertNotNull(box, "Missing element: " + id);
    view.renderMenu(new Sink(), 220, 300, 110, 150, time);
    box = view.elementBounds(id);
    assertTrue(view.mouseClicked(box[0] + box[2] / 2, box[1] + box[3] / 2, 0),
        "Unclickable element: " + id);
    Sink after = new Sink();
    view.renderMenu(after, 220, 300, 110, 150, time + .005);
    return after;
  }
}
