package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.renderer.FontAtlas;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryTextFallbackTest {
  private static final class RecordingCanvas extends GeometryCanvas {
    final List<String> fallbackTexts = new ArrayList<String>();
    final List<Float> fallbackX = new ArrayList<Float>();
    final List<Float> fallbackY = new ArrayList<Float>();
    final List<Float> fallbackScale = new ArrayList<Float>();
    int atlasGlyphCount;
    private final boolean nativeFallback;

    RecordingCanvas(boolean nativeFallback) {
      super(400, 200);
      this.nativeFallback = nativeFallback;
    }

    @Override
    protected boolean hasNativeTextFallback() {
      return nativeFallback;
    }

    @Override
    public float gameTextWidth(String value, float scale) {
      return value == null ? 0 : value.codePointCount(0, value.length()) * 7f * scale;
    }

    @Override
    public void gameText(String value, float x, float y, float scale, int argb, boolean shadow) {
      fallbackTexts.add(value);
      fallbackX.add(x);
      fallbackY.add(y);
      fallbackScale.add(scale);
    }

    @Override
    protected void draw(float[] vertices, int length, float[] clip) {}

    @Override
    protected void glyph(float x, float y, float w, float h, int ax, int ay, int aw, int ah, int argb) {
      atlasGlyphCount++;
    }

    @Override
    protected void drawItem(String id, float x, float y, float size, float alpha) {}

    @Override
    protected void drawBackdrop(float x, float y, float w, float h, float r, float b, int q, float o, float[] c) {}

    @Override
    protected void drawTexture(String id, float x, float y, float w, float h, float r, float t, int a, float[] c) {}
  }

  @Test
  void rendersSpecialSymbolsAndPuaIconsViaNativeFallbackAlignedWithAtlasText() {
    RecordingCanvas canvas = new RecordingCanvas(true);
    try {
      canvas.textFamily("studio-sans", "Трапка 123", 10, 20, 7f, 0xffffffff, false, 0);
      assertTrue(canvas.fallbackTexts.isEmpty(), "Standard Cyrillic/ASCII should use FontAtlas");
      assertTrue(canvas.atlasGlyphCount >= 8);

      canvas.atlasGlyphCount = 0;
      String mixed = "\u2605 \u0422\u0440\u0430\u043f\u043a\u0430 \u2694\ufe0f \ue001";
      float measured = canvas.textWidthFamily("studio-sans", mixed, 7f, false, 0);
      canvas.textFamily("studio-sans", mixed, 10, 20, 7f, 0xffffffff, false, 0);

      assertEquals(3, canvas.fallbackTexts.size());
      assertEquals("\u2605", canvas.fallbackTexts.get(0));
      assertEquals("\u2694\ufe0f", canvas.fallbackTexts.get(1));
      assertEquals("\ue001", canvas.fallbackTexts.get(2));
      assertTrue(canvas.atlasGlyphCount >= 6, "Cyrillic text between symbols still uses FontAtlas");
      assertEquals(10f, canvas.fallbackX.get(0), 0.001f);
      assertEquals(7f * 0.12f, canvas.fallbackScale.get(0), 0.0001f);

      float expectedLastX = 10f + measured - canvas.gameTextWidth("\ue001", 7f * 0.12f);
      assertEquals(expectedLastX, canvas.fallbackX.get(2), 0.01f,
          "Measured text width must match rendered pen advance across mixed runs");
    } finally {
      canvas.finish();
    }
  }

  @Test
  void variationSelectorsDoNotEmitQuestionMark() {
    FontAtlas atlas = FontAtlas.get("studio-sans");
    assertFalse(atlas.supportsAll("\u2605 \u0422\u0440\u0430\u043f\u043a\u0430", false));
    assertTrue(atlas.supportsAll("\u0422\u0440\u0430\u043f\u043a\u0430", false));
    assertEquals(atlas.width("A", 10f, false), atlas.width("A\ufe0f", 10f, false), 0.001f);
  }
}
