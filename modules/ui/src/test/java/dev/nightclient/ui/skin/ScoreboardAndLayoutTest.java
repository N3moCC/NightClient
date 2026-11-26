package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.FormattedText;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.ui.MenuController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoreboardAndLayoutTest {
  private static final class GameCall {
    final String text;
    final float x, y, scale;
    final int color;
    final boolean shadow;

    GameCall(String text, float x, float y, float scale, int color, boolean shadow) {
      this.text = text;
      this.x = x;
      this.y = y;
      this.scale = scale;
      this.color = color;
      this.shadow = shadow;
    }
  }

  private static final class TextCall {
    final String family, text;
    final float x, y, size;
    final int color;
    final boolean bold;

    TextCall(String family, String text, float x, float y, float size, int color, boolean bold) {
      this.family = family;
      this.text = text;
      this.x = x;
      this.y = y;
      this.size = size;
      this.color = color;
      this.bold = bold;
    }
  }

  private static final class ShapeBox {
    final float minX, minY, maxX, maxY;

    ShapeBox(float minX, float minY, float maxX, float maxY) {
      this.minX = minX;
      this.minY = minY;
      this.maxX = maxX;
      this.maxY = maxY;
    }
  }

  private static final class RecordingCanvas implements Canvas {
    final List<GameCall> gameTexts = new ArrayList<GameCall>();
    final List<TextCall> texts = new ArrayList<TextCall>();
    final List<ShapeBox> shapes = new ArrayList<ShapeBox>();
    final List<float[]> panels = new ArrayList<float[]>();

    @Override
    public void rect(float x, float y, float w, float h, int c) {
      if (w > 0 && h > 0) shapes.add(new ShapeBox(x, y, x + w, y + h));
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      panels.add(new float[] {x, y, w, h});
    }

    @Override
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}

    @Override
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}

    @Override
    public void line(float x1, float y1, float x2, float y2, float t, int c) {
      shapes.add(new ShapeBox(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)));
    }

    @Override
    public void circle(float cx, float cy, float r, int c) {
      shapes.add(new ShapeBox(cx - r, cy - r, cx + r, cy + r));
    }

    @Override
    public void fillTriangle(
        float ax, float ay, float bx, float by, float cx, float cy, int c) {
      shapes.add(new ShapeBox(Math.min(ax, Math.min(bx, cx)), Math.min(ay, Math.min(by, cy)),
          Math.max(ax, Math.max(bx, cx)), Math.max(ay, Math.max(by, cy))));
    }

    @Override
    public void text(String value, float x, float y, float size, int argb, boolean bold) {
      textFamily("studio-sans", value, x, y, size, argb, bold, 0);
    }

    @Override
    public void textTracked(
        String value, float x, float y, float size, int argb, boolean bold, float tracking) {
      textFamily("studio-sans", value, x, y, size, argb, bold, tracking);
    }

    @Override
    public void textFamily(String family, String value, float x, float y, float size, int argb,
        boolean bold, float tracking) {
      texts.add(new TextCall(family, value, x, y, size, argb, bold));
    }

    @Override
    public float textWidth(String value, float size, boolean bold) {
      return FontAtlas.get().width(value, size, bold);
    }

    @Override
    public float textWidthFamily(
        String family, String value, float size, boolean bold, float tracking) {
      return FontAtlas.get(family).width(value, size, bold, tracking);
    }

    @Override
    public void gameText(String value, float x, float y, float scale, int argb, boolean shadow) {
      gameTexts.add(new GameCall(value, x, y, scale, argb, shadow));
    }

    @Override
    public float gameTextWidth(String value, float scale) {
      return value == null
          ? 0
          : FontAtlas.get().width(FormattedText.plainText(value), 8 * scale, false);
    }

    @Override
    public void pushClip(float x, float y, float width, float height) {}

    @Override
    public void popClip() {}

    @Override
    public void item(String id, float x, float y, float size, float alpha) {}

    @Override
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float alpha) {}

    @Override
    public void texture(
        String id, float x, float y, float w, float h, float r, float tile, int c) {}
  }

  private static SkinViewTest.Actions sampleScoreboardActions() {
    final Map<String, Object> board = new LinkedHashMap<String, Object>();
    board.put("title", "§x§F§F§A§A§0§0Holy§fWorld");
    List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
    lines.add(row("§7Anarchy Lite #10", 5));
    lines.add(row("§fCoins: §61,250,000 coins", 4));
    lines.add(row("§fOnline: §a124 players", 3));
    board.put("lines", Collections.unmodifiableList(lines));
    return new SkinViewTest.Actions() {
      @Override
      public Map<String, Object> scoreboard() {
        return board;
      }
    };
  }

  private static Map<String, Object> row(String text, int score) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("text", text);
    map.put("score", score);
    map.put("hasScore", true);
    return map;
  }

  private static void click(
      MenuController menu, RecordingCanvas canvas, String id, double time) {
    float[] b = menu.elementBounds(id);
    assertNotNull(b, id);
    assertTrue(menu.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, 0));
    menu.mouseReleased(b[0] + b[2] / 2, b[1] + b[3] / 2, 0);
    menu.render(canvas, 1000, 700, 0, 0, time);
  }

  @Test
  void viewmodelTransformIconsStayCenteredInsideTheirButtons() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.validate();
    MenuController menu = new MenuController(settings, new SkinViewTest.Actions());
    RecordingCanvas canvas = new RecordingCanvas();
    menu.onOpen();
    menu.render(canvas, 1000, 700, 0, 0, 0);
    click(menu, canvas, "nav2", 1);
    click(menu, canvas, "edit-hud", 2);
    click(menu, canvas, "editor-target-viewmodel", 3);

    canvas.shapes.clear();
    menu.render(canvas, 1000, 700, 0, 0, 4);
    for (String mode : new String[] {"G", "R", "S"}) {
      float[] btn = menu.elementBounds("viewmodel-mode-" + mode);
      assertNotNull(btn, mode);
      float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
            maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
      int hits = 0;
      for (ShapeBox box : canvas.shapes) {
        if (box.minX >= btn[0] - .5f && box.maxX <= btn[0] + btn[2] + .5f
            && box.minY >= btn[1] - .5f && box.maxY <= btn[1] + btn[3] + .5f) {
          minX = Math.min(minX, box.minX);
          minY = Math.min(minY, box.minY);
          maxX = Math.max(maxX, box.maxX);
          maxY = Math.max(maxY, box.maxY);
          hits++;
        }
      }
      assertTrue(hits > 0, "Expected vector primitives inside viewmodel-mode-" + mode);
      float iconCenterX = (minX + maxX) / 2f;
      float iconCenterY = (minY + maxY) / 2f;
      assertEquals(btn[0] + btn[2] / 2f, iconCenterX, 1.0f, "X center of " + mode);
      assertEquals(btn[1] + btn[3] / 2f, iconCenterY, 1.0f, "Y center of " + mode);
    }
  }

  @Test
  void scoreboardVanillaModeIsReadableCenteredEvenlySpacedAndDynamicallySized() {
    Settings settings = new Settings();
    settings.scoreboard.enabled = true;
    settings.scoreboard.hideScores = false;
    settings.scoreboard.scale = 1;
    settings.menu.hudScale = 1;
    settings.validate();
    assertEquals("minecraft", settings.scoreboard.font);

    SkinView view = new SkinView(DefaultSkin.document(), settings, sampleScoreboardActions());
    RecordingCanvas canvas = new RecordingCanvas();
    assertTrue(view.renderHudWidgetInstant("scoreboard", canvas, 1200, 720, false));
    assertFalse(canvas.panels.isEmpty());
    float[] frame = canvas.panels.get(0);

    assertTrue(canvas.gameTexts.size() >= 8);
    GameCall t0 = canvas.gameTexts.get(0), t1 = canvas.gameTexts.get(1);
    assertEquals("Holy", t0.text);
    assertEquals("World", t1.text);
    assertEquals(0xffffaa00, t0.color);
    assertEquals(1f, t0.scale, .001f);
    assertTrue(t0.shadow && t1.shadow);
    float titleLeft = t0.x;
    float titleRight = t1.x + canvas.gameTextWidth(t1.text, t1.scale);
    assertEquals(frame[0] + frame[2] / 2f, (titleLeft + titleRight) / 2f, .6f,
        "Scoreboard title should be horizontally centered");
    assertEquals(frame[1] + 6.5f, t0.y, .01f);

    List<GameCall> leftRows = new ArrayList<GameCall>();
    List<GameCall> scores = new ArrayList<GameCall>();
    for (GameCall call : canvas.gameTexts) {
      if (call.y > t0.y + 2) {
        if ("5".equals(call.text) || "4".equals(call.text) || "3".equals(call.text))
          scores.add(call);
        else if (Math.abs(call.x - (frame[0] + 8f)) < .1f)
          leftRows.add(call);
      }
    }
    assertEquals(3, leftRows.size());
    assertEquals(3, scores.size());
    assertEquals(frame[1] + 27f, leftRows.get(0).y, .01f);
    assertEquals(12f, leftRows.get(1).y - leftRows.get(0).y, .01f);
    assertEquals(12f, leftRows.get(2).y - leftRows.get(1).y, .01f);
    for (int i = 0; i < 3; i++) {
      assertEquals(leftRows.get(i).y, scores.get(i).y, .01f);
      assertEquals(1f, leftRows.get(i).scale, .001f);
      assertTrue(leftRows.get(i).shadow && scores.get(i).shadow);
      assertFalse(leftRows.get(i).text.contains("§"));
    }
    float bottomSpace = (frame[1] + frame[3]) - (leftRows.get(2).y + 9f);
    assertEquals(9f, bottomSpace, .01f, "Bottom padding stays generous and balanced");
  }

  @Test
  void scoreboardVisualFontModeRendersFormattedRunsCleanlyWithoutMarkers() {
    Settings settings = new Settings();
    settings.scoreboard.enabled = true;
    settings.scoreboard.hideScores = false;
    settings.scoreboard.font = "visual";
    settings.scoreboard.scale = 1;
    settings.menu.hudScale = 1;
    settings.validate();

    SkinView view = new SkinView(DefaultSkin.document(), settings, sampleScoreboardActions());
    RecordingCanvas canvas = new RecordingCanvas();
    assertTrue(view.renderHudWidgetInstant("scoreboard", canvas, 1200, 720, false));
    assertTrue(canvas.gameTexts.isEmpty(), "Visual font mode should use NightClient text rendering");
    assertFalse(canvas.panels.isEmpty());
    float[] frame = canvas.panels.get(0);

    TextCall title0 = canvas.texts.get(0), title1 = canvas.texts.get(1);
    assertEquals("Holy", title0.text);
    assertEquals("World", title1.text);
    assertEquals(0xffffaa00, title0.color);
    assertTrue(title0.bold);
    float titleLeft = title0.x;
    float titleRight = title1.x + canvas.textWidthFamily(title1.family, title1.text, title1.size, title1.bold, 0);
    assertEquals(frame[0] + frame[2] / 2f, (titleLeft + titleRight) / 2f, .6f,
        "Visual font scoreboard title should be centered");
    for (TextCall call : canvas.texts) assertFalse(call.text.contains("§"), call.text);

    List<TextCall> rowStarts = new ArrayList<TextCall>();
    for (TextCall call : canvas.texts)
      if (call.y > title0.y + 4 && Math.abs(call.x - (frame[0] + 8f)) < .1f) rowStarts.add(call);
    assertEquals(3, rowStarts.size());
    assertEquals(12f, rowStarts.get(1).y - rowStarts.get(0).y, .01f);
    assertEquals(12f, rowStarts.get(2).y - rowStarts.get(1).y, .01f);
  }

  @Test
  void scoreboardFontCatalogOptionsAndSettingsValidationStayConsistent() {
    Settings settings = new Settings();
    settings.scoreboard.font = "bogus";
    settings.validate();
    assertEquals("minecraft", settings.scoreboard.font);
    settings.scoreboard.font = "visual";
    settings.validate();
    assertEquals("visual", settings.scoreboard.font);

    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Control control = catalog.control("scoreboard.font");
    assertNotNull(control);
    assertEquals("minecraft|visual", control.options);
    assertTrue(catalog.controls("scoreboard").stream().anyMatch(c -> c.bind.equals("scoreboard.font")));
    assertTrue(catalog.editorControls("scoreboard").stream().anyMatch(c -> c.bind.equals("scoreboard.font")));
  }
}
