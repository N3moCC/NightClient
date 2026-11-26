package dev.nightclient.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormattedTextTest {
  private static final class Draw {
    final String text;
    final float x;
    final int color;
    final boolean bold;
    Draw(String text, float x, int color, boolean bold) {
      this.text = text;
      this.x = x;
      this.color = color;
      this.bold = bold;
    }
  }
  private static final class Sink implements Canvas {
    final List<Draw> draws = new ArrayList<Draw>();
    public void rect(float x, float y, float w, float h, int c) {}
    public void roundedRect(float x, float y, float w, float h, float r, int c) {}
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    public void line(float x, float y, float xx, float yy, float t, int c) {}
    public void circle(float x, float y, float r, int c) {}
    public void text(String text, float x, float y, float size, int color, boolean bold) {
      draws.add(new Draw(text, x, color, bold));
    }
    public float textWidth(String text, float size, boolean bold) {
      return text.codePointCount(0, text.length()) * (bold ? 6 : 5) * size / 9;
    }
    public void pushClip(float x, float y, float w, float h) {}
    public void popClip() {}
    public void item(String id, float x, float y, float size, float alpha) {}
    public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
    public void texture(
        String id, float x, float y, float w, float h, float r, float tile, int color) {}
  }
  @Test
  void legacyAndExactRgbRunsPreserveAlphaAndStylesResetWithColors() {
    List<FormattedText.Run> runs =
        FormattedText.runs("§aGreen§lBold§x§1§2§3§4§5§6RGB§rBase", 0x80443322);
    assertEquals(4, runs.size());
    assertEquals("Green", runs.get(0).text);
    assertEquals(0x8055ff55, runs.get(0).color);
    assertTrue(runs.get(1).bold);
    assertEquals(0x8055ff55, runs.get(1).color);
    assertEquals(0x80123456, runs.get(2).color);
    assertFalse(runs.get(2).bold);
    assertEquals(0x80443322, runs.get(3).color);
    assertEquals("Base", runs.get(3).text);
  }
  @Test
  void nativeStylesAndUnicodeSurviveColorSplits() {
    List<FormattedText.Run> runs = FormattedText.runs("§l§o§n§m§kПривет 🎮", 0xffffffff);
    assertEquals(1, runs.size());
    FormattedText.Run run = runs.get(0);
    assertEquals("Привет 🎮", run.text);
    assertTrue(run.bold && run.italic && run.underline && run.strikethrough && run.obfuscated);
    assertEquals("§k§l§m§n§oПривет 🎮", run.nativeText());
    assertEquals("plain §z", FormattedText.runs("plain §z", 0xffffffff).get(0).text);
  }
  @Test
  void gameMeasurementAndDefaultCanvasDrawNeverCountFormattingAsLetters() {
    Sink sink = new Sink();
    String value = "§cA§lB§x§3§4§5§6§7§8🎮";
    assertEquals(16, FormattedText.gameWidth(sink, value, 1), .001f);
    FormattedText.drawGame(sink, value, 10, 20, 1, 0xffffffff, false);
    assertEquals(3, sink.draws.size());
    assertEquals("A", sink.draws.get(0).text);
    assertEquals(0xffff5555, sink.draws.get(0).color);
    assertEquals(15, sink.draws.get(1).x, .001f);
    assertTrue(sink.draws.get(1).bold);
    assertEquals(21, sink.draws.get(2).x, .001f);
    assertEquals(0xff345678, sink.draws.get(2).color);
    assertEquals(8, FormattedText.gameWidth(sink, value, .5f), .001f);
    assertEquals(0, FormattedText.gameWidth(sink, "§a§l", 1), .001f);
  }

  @Test
  void plainTextFitAndDrawRunsPreserveColorsWithoutFormatMarkers() {
    Sink sink = new Sink();
    String value = "§fCoins: §61,250,000 §x§1§2§3§4§5§6coins";
    assertEquals("Coins: 1,250,000 coins", FormattedText.plainText(value));
    List<FormattedText.Run> runs = FormattedText.runs(value, 0xffffffff);
    float fullWidth = FormattedText.runsWidth(sink, "studio-sans", runs, 9, false, 0);
    assertEquals(fullWidth, FormattedText.textWidth(sink, "studio-sans", value, 9, false, 0), .001f);

    List<FormattedText.Run> fitted =
        FormattedText.fit(sink, "studio-sans", runs, fullWidth * .65f, 9, false, 0);
    assertTrue(FormattedText.runsWidth(sink, "studio-sans", fitted, 9, false, 0) <= fullWidth * .65f + .001f);
    assertTrue(fitted.get(fitted.size() - 1).text.endsWith("…"));
    assertEquals(0xffffffff, fitted.get(0).color);
    assertEquals(0xffffaa00, fitted.get(1).color);

    FormattedText.drawRuns(sink, "studio-sans", fitted, 12, 24, 9, false, 0);
    StringBuilder rendered = new StringBuilder();
    for (int i = 0; i < sink.draws.size(); i++) {
      Draw draw = sink.draws.get(i);
      assertFalse(draw.text.contains("§"), draw.text);
      assertEquals(12 + i * 5, draw.x, .001f);
      assertEquals(i < fitted.get(0).text.length() ? 0xffffffff : 0xffffaa00, draw.color);
      rendered.append(draw.text);
    }
    assertEquals(fitted.get(0).text + fitted.get(1).text, rendered.toString());
  }
}
