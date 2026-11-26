package dev.nightclient.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class GeometryStreamContractTest {
  private static final String EXPECTED_FALLBACK =
      "ed22c96f6c167d65052d47b42fa0f545432274a17f3cc86b732916e551f15b63";
  private static final String EXPECTED_ANALYTIC =
      "eb74b7bcecc4ffc7861c28defcbe037884a50398b9c8c0c00dccccb1d347edc0";

  @Test
  void representativeHudPrimitiveStreamIsUnchanged() throws Exception {
    Capture canvas = new Capture(false);
    paintRepresentative(canvas);
    canvas.finish();
    assertTrue(canvas.commands > 10);
    assertEquals(EXPECTED_FALLBACK, canvas.digest());
  }
  @Test
  void analyticRoundedSurfaceStreamIsUnchanged() throws Exception {
    Capture canvas = new Capture(true);
    paintRepresentative(canvas);
    canvas.finish();
    assertTrue(canvas.roundedCommands > 0);
    assertEquals(EXPECTED_ANALYTIC, canvas.digest());
  }
  @Test
  void promotedGlyphBufferEmitsTheSameEightFloatVertices() {
    GlyphCapture c = new GlyphCapture();
    c.add(10.25f, 20.5f, 8, 9, .25f, .5f, .5f, .75f, 0x804060a0);
    c.add(40, 50, 7, 6, .125f, .625f, .25f, .875f, 0xffabcdef);
    float[] data = c.data();
    assertArrayEquals(
        new float[] {10.25f, 20.5f, .25f, .5f, 64 / 255f, 96 / 255f, 160 / 255f, 128 / 255f, 18.25f,
            20.5f, .5f, .5f, 64 / 255f, 96 / 255f, 160 / 255f, 128 / 255f, 18.25f, 29.5f, .5f, .75f,
            64 / 255f, 96 / 255f, 160 / 255f, 128 / 255f, 10.25f, 20.5f, .25f, .5f, 64 / 255f,
            96 / 255f, 160 / 255f, 128 / 255f, 18.25f, 29.5f, .5f, .75f, 64 / 255f, 96 / 255f,
            160 / 255f, 128 / 255f, 10.25f, 29.5f, .25f, .75f, 64 / 255f, 96 / 255f, 160 / 255f,
            128 / 255f, 40, 50, .125f, .625f, 171 / 255f, 205 / 255f, 239 / 255f, 255 / 255f, 47,
            50, .25f, .625f, 171 / 255f, 205 / 255f, 239 / 255f, 255 / 255f, 47, 56, .25f, .875f,
            171 / 255f, 205 / 255f, 239 / 255f, 255 / 255f, 40, 50, .125f, .625f, 171 / 255f,
            205 / 255f, 239 / 255f, 255 / 255f, 47, 56, .25f, .875f, 171 / 255f, 205 / 255f,
            239 / 255f, 255 / 255f, 40, 56, .125f, .875f, 171 / 255f, 205 / 255f, 239 / 255f,
            255 / 255f},
        data);
  }
  @Test
  void maximumBatchBoundaryStillSplitsOnlyBetweenWholeTriangles() throws Exception {
    Capture canvas = new Capture(false);
    for (int i = 0; i < 3639; i++) canvas.fillTriangle(0, 0, 1, 0, 0, 1, 0xff123456);
    canvas.rect(10, 20, 30, 40, 0xffabcdef);
    canvas.finish();
    assertEquals(2, canvas.batchLengths.size());
    assertEquals(65520, canvas.batchLengths.get(0));
    assertEquals(18, canvas.batchLengths.get(1));
  }
  private static void paintRepresentative(Capture c) {
    c.rect(10, 10, 80, 40, 0xffabcdef);
    c.gradientRect(10, 60, 80, 40, 0xff112233, 0xffddeeff);
    c.fillTriangle(10, 210, 24, 230, 38, 210, 0xffabcdef);
    c.fillTriangle(10, 250, 38, 250, 24, 230, 0xffabcdef);
    c.line(150, 150, 180, 170, 3, 0xff123456);
    c.circle(40, 140, 15, 0xffabcdef);
    c.roundedRect(100, 10, 80, 40, 12, 0xffabcdef);
    c.strokeRoundedRect(100, 60, 80, 40, 12, 2, 0xffabcdef);
    c.gradientRoundedRect(220, 110, 80, 40, 12, 0xff112233, 0xffddeeff);
    c.roundedRectCorners(12, 170, 96, 38, 0, 3, 11, 19, 0xffabcdef);
    c.strokeRoundedRectCorners(120, 170, 96, 38, 0, 3, 11, 19, 1.2f, 0xffabcdef);
    c.gradientRoundedRectCorners(
        220, 170, 90, 38, 0, 3, 11, 19, 0xff000000, 0xffff0000, 0xff00ff00, 0xff0000ff);
    c.pushClip(10, 12, 240, 220);
    c.text("NightClient HUD 0.11.5", 16, 236, 9, 0xffabcdef, false);
    c.textTracked("tracking", 150, 236, 8, 0x80fedcba, true, .75f);
    c.item("minecraft:ender_pearl", 260, 20, 18, 1);
    c.inventoryItem(new dev.nightclient.api.InventorySlot(
                        9, "minecraft:diamond_sword", "Slot 9", 1, 12, 80, true),
        260, 44, 18, .65f);
    c.backdrop(20, 20, 120, 80, 6, 12, 3, .75f);
    c.backdropCorners(20, 110, 120, 80, 2, 8, 10, 0, 12, 4, .5f);
    c.texture("nightclient:textures/ui/grain.png", 160, 20, 64, 64, 4, 32, 0x80ffffff);
    c.invertRect(30, 30, 20, 20, .5f);
    c.popClip();
  }
  private static final class Capture extends GeometryCanvas {
    final MessageDigest digest;
    final boolean analytic;
    final List<Integer> batchLengths = new ArrayList<Integer>();
    int commands, roundedCommands;
    Capture(boolean analytic) throws Exception {
      super(320, 240);
      this.analytic = analytic;
      digest = MessageDigest.getInstance("SHA-256");
    }
    private void record(float value) {
      int bits = Float.floatToIntBits(value);
      digest.update((byte) (bits >>> 24));
      digest.update((byte) (bits >>> 16));
      digest.update((byte) (bits >>> 8));
      digest.update((byte) bits);
    }
    private void record(int value) {
      digest.update((byte) (value >>> 24));
      digest.update((byte) (value >>> 16));
      digest.update((byte) (value >>> 8));
      digest.update((byte) value);
    }
    private void record(String value) {
      digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
    private void recordClip(float[] clip) {
      record
      (clip == null ? 0 : 1);
      if (clip != null)
        for (float value : clip) record
      (value);
    }
    private void recordHeader(int kind, int length) {
      record
      (kind);
      record
      (length);
      commands++;
    }
    @Override
    protected boolean usesGpuRounded() {
      return analytic;
    }
    @Override
    protected void draw(float[] vertices, int length, float[] clip) {
      recordHeader(1, length);
      for (int i = 0; i < length; i++) record
      (vertices[i]);
      recordClip(clip);
      batchLengths.add(length);
    }
    @Override
    protected void drawRounded(float[] vertices, int length, float[] clip) {
      roundedCommands++;
      recordHeader(2, length);
      for (int i = 0; i < length; i++) record
      (vertices[i]);
      recordClip(clip);
    }
    @Override
    protected void glyph(
        float x, float y, float w, float h, int ax, int ay, int aw, int ah, int argb) {
      recordHeader(3, 0);
      record
      (x);
      record
      (y);
      record
      (w);
      record
      (h);
      record
      (ax);
      record
      (ay);
      record
      (aw);
      record
      (ah);
      record
      (argb);
    }
    @Override
    protected void drawItem(String id, float x, float y, float size, float alpha) {
      recordHeader(4, 0);
      record
      (id);
      record
      (x);
      record
      (y);
      record
      (size);
      record
      (alpha);
    }
    @Override
    protected void drawBackdrop(float x, float y, float w, float h, float radius, float blurRadius,
        int quality, float opacity, float[] clip) {
      recordHeader(5, 0);
      record
      (x);
      record
      (y);
      record
      (w);
      record
      (h);
      record
      (radius);
      record
      (blurRadius);
      record
      (quality);
      record
      (opacity);
      recordClip(clip);
    }
    @Override
    protected void drawTexture(String id, float x, float y, float w, float h, float radius,
        float tileSize, int argb, float[] clip) {
      recordHeader(6, 0);
      record
      (id);
      record
      (x);
      record
      (y);
      record
      (w);
      record
      (h);
      record
      (radius);
      record
      (tileSize);
      record
      (argb);
      recordClip(clip);
    }
    @Override
    protected void drawInvertedRect(
        float x, float y, float w, float h, float opacity, float[] clip) {
      recordHeader(7, 0);
      record
      (x);
      record
      (y);
      record
      (w);
      record
      (h);
      record
      (opacity);
      recordClip(clip);
    }
    String digest() {
      StringBuilder out = new StringBuilder();
      for (byte value : digest.digest())
        out.append(String.format(Locale.ROOT, "%02x", value & 255));
      return out.toString();
    }
  }
  private static final class GlyphCapture extends GeometryCanvas {
    GlyphCapture() {
      super(320, 240);
      beginGlyphs();
    }
    void add(float x, float y, float w, float h, float u, float v, float u1, float v1, int argb) {
      glyphQuad(x, y, w, h, u, v, u1, v1, argb);
    }
    float[] data() {
      return Arrays.copyOf(glyphData(), glyphCount());
    }
    @Override
    protected void draw(float[] vertices, int length, float[] clip) {}
    @Override
    protected void glyph(
        float x, float y, float w, float h, int ax, int ay, int aw, int ah, int color) {}
    @Override
    protected void drawItem(String id, float x, float y, float size, float alpha) {}
    @Override
    protected void drawBackdrop(float x, float y, float w, float h, float radius, float blurRadius,
        int quality, float opacity, float[] clip) {}
    @Override
    protected void drawTexture(String id, float x, float y, float w, float h, float radius,
        float tileSize, int argb, float[] clip) {}
  }
}
