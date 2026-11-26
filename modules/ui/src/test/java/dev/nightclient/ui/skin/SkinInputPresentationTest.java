package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.SkinParser;
import dev.nightclient.skin.NcParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SkinInputPresentationTest {
  private static final float EPS = .01f;
  static final class Box {
    final float x, y, right, bottom;
    Box(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      right = x + width;
      bottom = y + height;
    }
    Box intersect(Box other) {
      float left = Math.max(x, other.x), top = Math.max(y, other.y);
      return new Box(left, top, Math.max(0, Math.min(right, other.right) - left),
          Math.max(0, Math.min(bottom, other.bottom) - top));
    }
    void contains(Box ink, String message) {
      assertTrue(ink.x >= x - EPS && ink.y >= y - EPS && ink.right <= right + EPS
              && ink.bottom <= bottom + EPS,
          message + " ink=" + ink.x + "," + ink.right + " clip=" + x + "," + right);
    }
  }
  static final class Draw {
    final String text;
    final float x, size;
    final Box clip;
    final List<Box> glyphs = new ArrayList<Box>();
    Draw(String text, float x, float size, Box clip) {
      this.text = text;
      this.x = x;
      this.size = size;
      this.clip = clip;
    }
    void fullyVisible() {
      for (Box glyph : glyphs) clip.contains(glyph, text);
    }
  }
  static final class InputCanvas extends SkinViewTest.Sink {
    final List<Box> stack = new ArrayList<Box>();
    final List<Draw> draws = new ArrayList<Draw>();
    final List<Box> inputRects = new ArrayList<Box>();
    InputCanvas() {
      stack.add(new Box(0, 0, 600, 300));
    }
    @Override
    public float textWidth(String text, float size, boolean bold) {
      return FontAtlas.get().width(text, size, bold);
    }
    @Override
    public float textWidthTracked(String text, float size, boolean bold, float tracking) {
      return FontAtlas.get().width(text, size, bold, tracking);
    }
    @Override
    public void textTracked(
        final String text, float x, float y, float size, int color, boolean bold, float tracking) {
      final Draw draw = new Draw(text, x, size, stack.get(stack.size() - 1));
      draws.add(draw);
      FontAtlas.get().visitGlyphs(text, x, y, size, bold, tracking, new FontAtlas.GlyphConsumer() {
        public void glyph(float gx, float gy, float w, float h, int px, int py, int pw, int ph) {
          draw.glyphs.add(new Box(gx, gy, w, h));
        }
      });
      super.text(text, x, y, size, color, bold);
    }
    @Override
    public void pushClip(float x, float y, float width, float height) {
      super.pushClip(x, y, width, height);
      stack.add(stack.get(stack.size() - 1).intersect(new Box(x, y, width, height)));
    }
    @Override
    public void popClip() {
      super.popClip();
      stack.remove(stack.size() - 1);
    }
    @Override
    public void rect(float x, float y, float width, float height, int color) {
      if (stack.size() >= 4) {
        Box box = new Box(x, y, width, height);
        stack.get(stack.size() - 1).contains(box, "input selection/caret");
        inputRects.add(box);
      }
      super.rect(x, y, width, height, color);
    }
    Draw only() {
      assertEquals(1, draws.size());
      assertEquals(0, clips);
      assertEquals(1, stack.size());
      return draws.get(0);
    }
  }
  private static SkinView view(String controls, Settings settings, SkinViewTest.Actions actions)
      throws Exception {
    return new SkinView(SkinParser.parse("<nightclient><menu layout=\"absolute\">" + controls
                            + "</menu></nightclient>"),
        settings, actions);
  }
  private static String numeric(String format) {
    return "<input id=\"number\" bind=\"viewmodel.main.rotateX\" format=\"number\" "
           + "display-format=\""
        + format + "\" width=\"60\" height=\"30\"/>";
  }
  private static InputCanvas render(SkinView view, double time) {
    InputCanvas canvas = new InputCanvas();
    assertTrue(view.renderMenu(canvas, 600, 300, -1, -1, time));
    return canvas;
  }
  private static void focus(SkinView view) {
    assertTrue(view.mouseClicked(20, 15, 0));
    view.mouseReleased(20, 15, 0);
  }
  private static void sameBits(float expected, float actual) {
    assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual));
  }

  @Test
  void originalRotationValuesAndExtremesFitWithActualGlyphBoundsAtSixtyPixels() throws Exception {
    Locale previous = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
    try {
      for (float value : new float[] {21.772474f, 21.772526f, 21.772413f, -21.772474f, -359.987654f,
               -0.0f, 360f, 1.2345678E-20f, Float.MAX_VALUE}) {
        Settings settings = new Settings();
        settings.viewmodel.main.rotateX = value;
        SkinViewTest.Actions actions = new SkinViewTest.Actions();
        SkinView view = view(numeric("fixed:2"), settings, actions);
        Draw draw = render(view, 0).only();
        draw.fullyVisible();
        assertEquals(12, draw.size, EPS);
        assertEquals(8, draw.clip.x, EPS);
        assertEquals(52, draw.clip.right, EPS);
        if (Math.abs(value) < 100)
          assertEquals(String.format(Locale.ROOT, "%.2f", (double) value), draw.text);
        if (value == 21.772474f) assertEquals("21.77", draw.text);
        if (value == Float.MAX_VALUE) assertTrue(draw.text.endsWith("…"));
        sameBits(value, settings.viewmodel.main.rotateX);
        assertEquals(0, actions.changes);
      }
      for (int decimals = 0; decimals <= 3; decimals++) {
        Settings settings = new Settings();
        settings.viewmodel.main.rotateX = 1.234567f;
        Draw draw =
            render(view(numeric("fixed:" + decimals), settings, new SkinViewTest.Actions()), 0)
                .only();
        assertEquals(String.format(Locale.ROOT, "%." + decimals + "f",
                         (double) settings.viewmodel.main.rotateX),
            draw.text);
        draw.fullyVisible();
      }
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void focusClipboardTabBlurEscapeAndNoOpEnterPreserveStoredFloatBits() throws Exception {
    Settings settings = new Settings();
    float value = 21.772474f;
    settings.viewmodel.main.rotateX = value;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = view(numeric("fixed:2")
            + ("<input bind=\"viewmodel.main.rotateY\" format=\"number\" "
               + "display-format=\"fixed:2\" y=\"40\" width=\"60\" height=\"30\"/>"),
        settings, actions);
    render(view, 0);
    focus(view);
    InputCanvas focused = render(view, .1);
    assertEquals(Float.toString(value), focused.draws.get(0).text);
    view.keyPressed(65, 0, 2);
    view.keyPressed(67, 0, 2);
    assertEquals(Float.toString(value), actions.clipboard);
    assertTrue(view.keyPressed(258, 0, 0));
    assertTrue(view.keyPressed(256, 0, 0));
    sameBits(value, settings.viewmodel.main.rotateX);
    focus(view);
    assertTrue(view.keyPressed(257, 0, 0));
    sameBits(value, settings.viewmodel.main.rotateX);
    focus(view);
    view.mouseClicked(200, 150, 0);
    sameBits(value, settings.viewmodel.main.rotateX);
    render(view, .2);
    assertEquals(0, actions.changes);
    assertFalse(view.isTextFocused());
  }

  @Test
  void longRawEditsScrollAndKeepPrecisionWithSelectionCaretAndRepeatedClick() throws Exception {
    Settings settings = new Settings();
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = view(numeric("fixed:2"), settings, actions);
    render(view, 0);
    focus(view);
    view.keyPressed(65, 0, 2);
    String raw = "-123.123456789000";
    for (char c : raw.toCharArray()) view.charTyped(c, 0);
    float expected = Float.parseFloat(raw);
    sameBits(expected, settings.viewmodel.main.rotateX);
    InputCanvas atEnd = render(view, .1);
    Draw end = atEnd.only();
    assertEquals(raw, end.text);
    assertTrue(end.x < end.clip.x);
    assertFalse(atEnd.inputRects.isEmpty());
    assertEquals(8, end.clip.x, EPS);
    assertEquals(52, end.clip.right, EPS);
    assertEquals(1, end.clip.y, EPS);
    assertEquals(29, end.clip.bottom, EPS);
    assertTrue(
        end.glyphs.get(0).x < end.clip.x, "Full raw text is scrolled, rather than shortened");
    end.clip.contains(
        end.glyphs.get(end.glyphs.size() - 1), "last editable glyph and caret remain visible");
    view.keyPressed(263, 0, 0);
    Draw afterLeft = render(view, .2).only();
    assertEquals(
        end.x, afterLeft.x, EPS, "Moving within the visible range must retain the scroll offset");
    focus(view);
    view.keyPressed(65, 0, 2);
    view.keyPressed(67, 0, 2);
    assertEquals(raw, actions.clipboard,
        "Reclick must not replace the raw buffer with the canonical stored float");
    InputCanvas selected = render(view, .3);
    assertFalse(selected.inputRects.isEmpty());
    view.keyPressed(268, 0, 0);
    Draw home = render(view, .4).only();
    assertTrue(home.x > afterLeft.x);
    assertEquals(home.clip.x + 1, home.x, EPS);
    view.keyPressed(269, 0, 0);
    assertEquals(raw, render(view, .45).only().text);
    view.keyPressed(257, 0, 0);
    Draw unfocused = render(view, .5).only();
    unfocused.fullyVisible();
    sameBits(expected, settings.viewmodel.main.rotateX);
    assertEquals(12, unfocused.size, EPS);
  }

  @Test
  void stringAndPlaceholderEllipsisAndScaledAnimatedAsymmetricPaddingStayInsideContent()
      throws Exception {
    Settings settings = new Settings();
    settings.protect.replacement = "Длинное имя игрока с Юникодом";
    settings.menu.fontScale = 1.2f;
    settings.theme.fontTracking = .6f;
    settings.theme.motion.hoverScale = 1.04f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = new SkinView(
        SkinParser.parse("<nightclient><style>.scaled { font-scale:1.25; }</style><menu "
                         + "layout=\"absolute\"><input bind=\"protect.replacement\" x=\"10.25\" "
                         + "y=\"12.5\" width=\"90\" height=\"40\" padding=\"5 9 7 11\" "
                         + "font-size=\"12\" class=\"scaled\"/></menu></nightclient>"),
        settings, actions);
    Draw idle = render(view, 0).only();
    idle.fullyVisible();
    assertTrue(idle.text.endsWith("…"));
    assertEquals(18, idle.size, EPS);
    assertEquals(21.25f, idle.clip.x, EPS);
    assertEquals(91.25f, idle.clip.right, EPS);
    assertEquals(17.5f, idle.clip.y, EPS);
    assertEquals(45.5f, idle.clip.bottom, EPS);
    InputCanvas hovered = new InputCanvas();
    view.renderMenu(hovered, 600, 300, 40, 30, .05);
    InputCanvas animated = new InputCanvas();
    view.renderMenu(animated, 600, 300, 40, 30, .11);
    Draw moving = animated.only();
    moving.fullyVisible();
    assertTrue(moving.size > 18);
    assertNotEquals(Math.round(moving.clip.x), moving.clip.x, EPS);
    assertTrue(view.mouseClicked(40, 30, 0));
    view.mouseReleased(40, 30, 0);
    InputCanvas editing = render(view, .2);
    assertEquals(settings.protect.replacement, editing.only().text);
    assertFalse(editing.inputRects.isEmpty());
    view.keyPressed(65, 0, 2);
    view.keyPressed(67, 0, 2);
    assertEquals(settings.protect.replacement, actions.clipboard);
    Settings empty = new Settings();
    empty.protect.replacement = "";
    SkinView placeholder = view("<input bind=\"protect.replacement\" placeholder=\"Длинная "
                                + "подсказка игроку\" width=\"60\" height=\"30\"/>",
        empty, new SkinViewTest.Actions());
    Draw hint = render(placeholder, 0).only();
    hint.fullyVisible();
    assertTrue(hint.text.endsWith("…"));
  }

  @Test
  void bundledTransformToolsUseIconsAndCompactNumericInputsRemainReadable() throws Exception {
    String source;
    try (InputStream stream = SkinInputPresentationTest.class.getResourceAsStream(
             "/assets/nightclient/skins/default.nc")) {
      assertNotNull(stream);
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] block = new byte[4096];
      int read;
      while ((read = stream.read(block)) != -1) bytes.write(block, 0, read);
      source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
    NcParser.parse(source);
    SkinDocument document = dev.nightclient.skin.DefaultSkin.document();
    new SkinValidator().validate(document);
    for (String[] tool : new String[][] {
             {"G", "transform-move"}, {"R", "transform-rotate"}, {"S", "transform-scale"}}) {
      SkinDocument.Node button = find(document.root(), "viewmodel-mode-" + tool[0]);
      assertNotNull(button);
      assertEquals("viewmodel-mode:" + tool[0], button.attributes.get("action"));
      assertTrue(button.text.isEmpty(),
          "Transform tools display their vector icon, not a keyboard letter");
      assertTrue(
          button.children.stream().anyMatch(
              child -> "icon".equals(child.tag) && tool[1].equals(child.attributes.get("src"))),
          "Missing transform tool icon " + tool[1]);
      assertNotNull(
          button.attributes.get("tooltip"), "Every icon tool has a localized description");
    }
    for (String axis : new String[] {"x", "y", "z"})
      assertNull(find(document.root(), "viewmodel-rotate-" + axis),
          "The numeric rotation grid is replaced by the real projected gizmo");
    assertNotNull(find(document.root(), "viewmodel-reset"));
    Settings settings = new Settings();
    settings.viewmodel.main.rotateX = 21.772474f;
    Draw draw = render(view("<input bind=\"viewmodel.main.rotateX\" format=\"number\" width=\"64\" "
                            + "height=\"20\" font-size=\"10\"/>",
                           settings, new SkinViewTest.Actions()),
        0)
                    .only();
    draw.fullyVisible();
    assertEquals(10, draw.size, EPS);
    assertEquals(1, draw.clip.y, EPS);
    assertEquals(19, draw.clip.bottom, EPS);
    assertEquals(Float.toString(settings.viewmodel.main.rotateX), draw.text);
  }
  private static SkinDocument.Node find(SkinDocument.Node node, String id) {
    if (id.equals(node.id)) return node;
    for (SkinDocument.Node child : node.children) {
      SkinDocument.Node match = find(child, id);
      if (match != null) return match;
    }
    return null;
  }
}
