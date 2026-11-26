package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumericTextMotionTest {
  private static final float EPSILON = .001f;

  private static final class Draw {
    final String text;
    final float x, y;
    final float[] clip;

    Draw(String text, float x, float y, float[] clip) {
      this.text = text;
      this.x = x;
      this.y = y;
      this.clip = clip;
    }
  }

  private static final class RecordingSink extends SkinViewTest.Sink {
    final List<Draw> draws = new ArrayList<Draw>();
    final List<float[]> scissors = new ArrayList<float[]>();

    @Override
    public void text(String text, float x, float y, float size, int color, boolean bold) {
      float[] clip = scissors.isEmpty() ? null : scissors.get(scissors.size() - 1).clone();
      draws.add(new Draw(text, x, y, clip));
      super.text(text, x, y, size, color, bold);
    }

    @Override
    public void pushClip(float x, float y, float width, float height) {
      super.pushClip(x, y, width, height);
      float[] next = new float[] {x, y, x + width, y + height};
      if (!scissors.isEmpty()) {
        float[] parent = scissors.get(scissors.size() - 1);
        next[0] = Math.max(next[0], parent[0]);
        next[1] = Math.max(next[1], parent[1]);
        next[2] = Math.min(next[2], parent[2]);
        next[3] = Math.min(next[3], parent[3]);
      }
      scissors.add(next);
    }

    @Override
    public void popClip() {
      super.popClip();
      scissors.remove(scissors.size() - 1);
    }

    Draw containing(String text) {
      Draw found = null;
      for (Draw draw : draws) {
        if (!draw.text.contains(text)) continue;
        assertNull(found, "Text drawn more than once: " + text);
        found = draw;
      }
      assertNotNull(found, "Missing text: " + text);
      return found;
    }
  }

  private static Settings settings() {
    Settings settings = new Settings();
    settings.menu.scale = 1;
    settings.theme.motion.enterOpacity = 1;
    settings.theme.motion.layout = false;
    settings.theme.motion.value = true;
    settings.theme.motion.numericMotion = "roll";
    return settings;
  }

  private static SkinView view(Settings settings, String label, String motion) throws Exception {
    SkinView view = new SkinView(
        NcParser.parse("menu { layout=absolute; width=180px; height=80px; padding=0px; "
            + "enterOpacity=100%; enterScale=1; enterOffsetY=0px; animateLayout=false; "
            + "text '" + label + "'(id=value) { position=absolute; x=20px; y=16px; width=100px; "
            + "height=20px; padding=0px; fontSize=10px; textAlign=left; "
            + "enterOpacity=100%; enterOffsetY=0px; animateLayout=false; "
            + "transitionDuration=200ms; easing=linear; " + motion + " } }"),
        settings, new SkinViewTest.Actions());
    view.resetMotion();
    return view;
  }

  private static RecordingSink render(SkinView view, double time) {
    RecordingSink sink = new RecordingSink();
    assertTrue(view.renderMenu(sink, 180, 80, 0, 0, time));
    assertEquals(0, sink.clips, "Every text scissor must be restored");
    assertTrue(sink.scissors.isEmpty());
    return sink;
  }

  private static void assertSingleValue(RecordingSink sink, String expected, float baseline) {
    assertEquals(1, sink.draws.size(), "A settled or disabled label needs one text run");
    assertEquals(expected, sink.draws.get(0).text);
    assertEquals(baseline, sink.draws.get(0).y, EPSILON);
  }

  private static void assertClippedToLabel(Draw draw, float[] bounds) {
    assertNotNull(draw.clip, "Moving digits need their own scissor");
    assertTrue(draw.clip[0] >= bounds[0] - EPSILON);
    assertTrue(draw.clip[1] >= bounds[1] - EPSILON);
    assertTrue(draw.clip[2] <= bounds[0] + bounds[2] + EPSILON);
    assertTrue(draw.clip[3] <= bounds[1] + bounds[3] + EPSILON);
    assertTrue(draw.clip[2] > draw.clip[0]);
    assertTrue(draw.clip[3] > draw.clip[1]);
  }

  @Test
  void changedDigitsSlideInsideTheirLabelAndSettleAtTheOriginalBaseline() throws Exception {
    Settings settings = settings();
    SkinView view = view(
        settings, "{{= format(menu.scale,\"fixed:0\")}}", "textMotion=numeric; animateValue=true;");
    RecordingSink initial = render(view, 1);
    float baseline = initial.containing("1").y;

    settings.menu.scale = 2;
    render(view, 1);
    RecordingSink halfway = render(view, 1.1);
    Draw outgoing = halfway.containing("1");
    Draw incoming = halfway.containing("2");
    assertTrue(Math.abs(outgoing.y - baseline) > EPSILON, "Outgoing digit must move");
    assertTrue(Math.abs(incoming.y - baseline) > EPSILON, "Incoming digit must move");
    assertTrue((outgoing.y - baseline) * (incoming.y - baseline) < 0,
        "The old and new digits must approach opposite sides of the resting line");
    assertEquals(outgoing.x, incoming.x, EPSILON);
    float[] bounds = view.layoutBounds("value");
    assertClippedToLabel(outgoing, bounds);
    assertClippedToLabel(incoming, bounds);

    assertSingleValue(render(view, 1.21), "2", baseline);
    assertSingleValue(render(view, 1.4), "2", baseline);
  }

  @Test
  void reducedMotionSnapsToTheNewValueWithoutPairedTextRuns() throws Exception {
    Settings settings = settings();
    SkinView view = view(
        settings, "{{= format(menu.scale,\"fixed:0\")}}", "textMotion=numeric; animateValue=true;");
    float baseline = render(view, 1).containing("1").y;
    settings.menu.animationSpeed = 1000000f;
    settings.menu.scale = 2;
    assertSingleValue(render(view, 1), "2", baseline);
    assertSingleValue(render(view, 1.1), "2", baseline);
  }

  @Test
  void animateValueFalseDisablesTheOptedInNumericTransition() throws Exception {
    Settings settings = settings();
    SkinView view = view(settings, "{{= format(menu.scale,\"fixed:0\")}}",
        "textMotion=numeric; animateValue=false;");
    float baseline = render(view, 1).containing("1").y;
    settings.menu.scale = 2;
    assertSingleValue(render(view, 1), "2", baseline);
    assertSingleValue(render(view, 1.1), "2", baseline);
  }

  @Test
  void ordinaryLabelsKeepTheirExistingImmediateUpdateBehavior() throws Exception {
    Settings settings = settings();
    SkinView view = view(settings, "{{= format(menu.scale,\"fixed:0\")}}", "animateValue=true;");
    float baseline = render(view, 1).containing("1").y;
    settings.menu.scale = 2;
    assertSingleValue(render(view, 1), "2", baseline);
    assertSingleValue(render(view, 1.1), "2", baseline);
  }

  @Test
  void stableLettersStayOnTheirBaselineWhileOnlyTheChangedDigitMoves() throws Exception {
    Settings settings = settings();
    SkinView view = view(settings, "FPS {{= format(menu.scale,\"fixed:0\")}}",
        "textMotion=numeric; animateValue=true;");
    float baseline = render(view, 1).containing("FPS").y;
    settings.menu.scale = 2;
    render(view, 1);
    RecordingSink halfway = render(view, 1.1);
    for (char letter : new char[] {'F', 'P', 'S'}) {
      Draw draw = halfway.containing(String.valueOf(letter));
      assertEquals(baseline, draw.y, EPSILON, "Unchanged letters must remain stationary");
    }
    assertNotEquals(halfway.containing("1").y, halfway.containing("2").y, EPSILON);
    assertSingleValue(render(view, 1.21), "FPS 2", baseline);
  }

  @Test
  void nonNumericTextChangesImmediatelyEvenWithNumericMotionEnabled() throws Exception {
    Settings settings = settings();
    settings.protect.replacement = "alpha";
    SkinView view =
        view(settings, "{{protect.replacement}}", "textMotion=numeric; animateValue=true;");
    float baseline = render(view, 1).containing("alpha").y;
    settings.protect.replacement = "beta";
    assertSingleValue(render(view, 1), "beta", baseline);
    assertSingleValue(render(view, 1.1), "beta", baseline);
  }

  @Test
  void numericMotionSettingSupportsOffFadeAndSlideModes() throws Exception {
    Settings offSettings = settings();
    offSettings.theme.motion.numericMotion = "off";
    SkinView offView = view(
        offSettings, "{{= format(menu.scale,\"fixed:0\")}}", "textMotion=numeric; animateValue=true;");
    float baseline = render(offView, 1).containing("1").y;
    offSettings.menu.scale = 2;
    assertSingleValue(render(offView, 1), "2", baseline);
    assertSingleValue(render(offView, 1.1), "2", baseline);

    Settings fadeSettings = settings();
    fadeSettings.theme.motion.numericMotion = "fade";
    SkinView fadeView = view(
        fadeSettings, "{{= format(menu.scale,\"fixed:0\")}}", "textMotion=numeric; animateValue=true;");
    float fadeBaseline = render(fadeView, 1).containing("1").y;
    fadeSettings.menu.scale = 2;
    render(fadeView, 1);
    RecordingSink fadeMid = render(fadeView, 1.1);
    assertEquals(fadeBaseline, fadeMid.containing("1").y, EPSILON,
        "Fade mode cross-fades in place without vertical shift");
    assertEquals(fadeBaseline, fadeMid.containing("2").y, EPSILON);

    Settings slideSettings = settings();
    slideSettings.theme.motion.numericMotion = "slide";
    SkinView slideView = view(
        slideSettings, "{{= format(menu.scale,\"fixed:0\")}}", "textMotion=numeric; animateValue=true;");
    float slideBaseline = render(slideView, 1).containing("1").y;
    slideSettings.menu.scale = 2;
    render(slideView, 1);
    RecordingSink slideMid = render(slideView, 1.1);
    float slideDelta = Math.abs(slideMid.containing("2").y - slideBaseline);
    assertTrue(slideDelta > EPSILON && slideDelta < 6f,
        "Soft slide mode uses a shorter vertical travel than full roll");
  }

  @Test
  void numericMotionDefaultsToOffAndUpdatesImmediately() throws Exception {
    Settings defaults = new Settings();
    assertEquals("off", defaults.theme.motion.numericMotion,
        "Default numericMotion must be plain immediate updates ('off')");
    defaults.theme.motion.numericMotion = "invalid";
    defaults.theme.motion.validate();
    assertEquals("off", defaults.theme.motion.numericMotion,
        "Invalid numericMotion must fall back to 'off'");

    defaults.menu.scale = 1;
    defaults.theme.motion.enterOpacity = 1;
    defaults.theme.motion.layout = false;
    defaults.theme.motion.value = true;
    SkinView view = view(
        defaults, "{{= format(menu.scale,\"fixed:0\")}}", "textMotion=numeric; animateValue=true;");
    float baseline = render(view, 1).containing("1").y;
    defaults.menu.scale = 2;
    assertSingleValue(render(view, 1), "2", baseline);
    assertSingleValue(render(view, 1.1), "2", baseline);
  }
}
