package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import org.junit.jupiter.api.Test;

class RendererContractTest {
  @org.junit.jupiter.api.Test
  void everyBundledFontFamilyReferencesAnExistingTexture() throws Exception {
    for (String family : new String[] {"onest", "studio-sans", "studio-sans-medium"}) {
      FontAtlas atlas = FontAtlas.get(family);
      String texture = atlas.textureResourceId().replace(":", "/");
      try (java.io.InputStream in =
               RendererContractTest.class.getResourceAsStream("/assets/" + texture)) {
        org.junit.jupiter.api.Assertions.assertNotNull(in, family + " texture");
        org.junit.jupiter.api.Assertions.assertTrue(in.read() >= 0);
      }
    }
  }
  @Test
  void squareTextureUsesTwoTrianglesWithoutCoverageFringeOrUvBleed() {
    float[] v = TexturedMesh.roundedRect(10, 20, 40, 30, 0, 0x8040a0ff, .1f, .2f, .9f, .8f);
    assertEquals(6 * 8, v.length,
        "Sprites and full-screen composites need one quad, not a rounded contour fan");
    for (int i = 0; i < v.length; i += 8) {
      assertTrue(v[i] >= 10 && v[i] <= 50);
      assertTrue(v[i + 1] >= 20 && v[i + 1] <= 50);
      assertEquals(128 / 255f, v[i + 7], .00001);
      assertTrue(v[i + 2] >= .1f - .00001f && v[i + 2] <= .9f + .00001f);
      assertTrue(v[i + 3] >= .2f - .00001f && v[i + 3] <= .8f + .00001f);
    }
  }
  @Test
  void roundedTexturedMeshKeepsWindingUvAndTransparentFringe() {
    float[] vertices = TexturedMesh.roundedRect(10, 20, 40, 30, 9, 0x8040a0ff, .1f, .2f, .9f, .8f);
    boolean fringe = false, interior = false;
    for (int i = 0; i < vertices.length; i += 8) {
      assertTrue(Float.isFinite(vertices[i]));
      assertTrue(Float.isFinite(vertices[i + 1]));
      assertEquals(.1f + (vertices[i] - 10) / 40 * .8f, vertices[i + 2], .00001f);
      assertEquals(.2f + (vertices[i + 1] - 20) / 30 * .6f, vertices[i + 3], .00001f);
      assertEquals(64 / 255f, vertices[i + 4], .00001f);
      assertEquals(160 / 255f, vertices[i + 5], .00001f);
      fringe |= vertices[i + 7] == 0;
      interior |= Math.abs(vertices[i + 7] - 128 / 255f) < .00001f;
    }
    for (int i = 0; i < vertices.length; i += 24) {
      float area = (vertices[i + 8] - vertices[i]) * (vertices[i + 17] - vertices[i + 1])
          - (vertices[i + 9] - vertices[i + 1]) * (vertices[i + 16] - vertices[i]);
      assertTrue(area >= -.0001f, "GUI culling must not discard reversed triangles");
    }
    assertTrue(fringe);
    assertTrue(interior);
  }
  @Test
  void asymmetricCornersRetainEachRadiusAndPositiveWinding() {
    float[] mesh =
        TexturedMesh.roundedRectCorners(10, 20, 80, 40, 0, 3, 11, 19, 0xffffffff, 0, 0, 1, 1);
    boolean topLeftSquare = false, topRightArc = false, bottomLeftArc = false,
            bottomRightArc = false;
    for (int i = 0; i < mesh.length; i += 8) {
      assertTrue(Float.isFinite(mesh[i]) && Float.isFinite(mesh[i + 1]));
      if (mesh[i + 7] < 1) continue;
      float x = mesh[i], y = mesh[i + 1];
      topLeftSquare |= Math.abs(x - 10) < .001f && Math.abs(y - 20) < .001f;
      topRightArc |= Math.abs(x - 87) < .001f && Math.abs(y - 20) < .001f;
      bottomLeftArc |= Math.abs(x - 21) < .001f && Math.abs(y - 60) < .001f;
      bottomRightArc |= Math.abs(x - 71) < .001f && Math.abs(y - 60) < .001f;
    }
    for (int i = 0; i < mesh.length; i += 24)
      assertTrue((mesh[i + 8] - mesh[i]) * (mesh[i + 17] - mesh[i + 1])
              - (mesh[i + 9] - mesh[i + 1]) * (mesh[i + 16] - mesh[i])
          >= -.0001f);
    assertTrue(topLeftSquare && topRightArc && bottomLeftArc && bottomRightArc);
  }
  @Test
  void widthCacheKeepsMetricsStableAcrossRepeatedAndInterleavedLabels() {
    FontAtlas font = FontAtlas.get();
    float cooldown = font.width("Cooldown 12.4s", 12, false, .5f);
    float bold = font.width("Cooldown 12.4s", 12, true, .5f);
    float tracked = font.width("Cooldown 12.4s", 12, false, 1.5f);
    for (int i = 0; i < 4; i++) {
      assertEquals(cooldown, font.width("Cooldown 12.4s", 12, false, .5f), 0f);
      assertEquals(bold, font.width("Cooldown 12.4s", 12, true, .5f), 0f);
      assertEquals(tracked, font.width("Cooldown 12.4s", 12, false, 1.5f), 0f);
    }
    assertNotEquals(cooldown, bold);
    assertNotEquals(cooldown, tracked);
  }
  @Test
  void trackingUsesCodePointsAndRestartsAtNewlines() {
    FontAtlas font = FontAtlas.get();
    assertEquals(font.width("AБ🧩", 12, false) + 3, font.width("AБ🧩", 12, false, 1.5f), .0001f);
    float expected = Math.max(font.width("AB", 12, false) + 1.5f, font.width("XYZ", 12, false) + 3);
    assertEquals(expected, font.width("AB\nXYZ", 12, false, 1.5f), .0001f);
    final int[] visited = {0};
    font.visitGlyphs("AБ🧩", 5, 10, 12, false, 1.5f, (x, y, w, h, px, py, pw, ph) -> visited[0]++);
    assertEquals(3, visited[0]);
  }
  @Test
  void rasterDensityKeepsCyrillicLayoutAndUsesAnAppropriateAtlasRegion() {
    FontAtlas font = FontAtlas.get();
    final float[] small = new float[8], large = new float[8];
    font.visitGlyphs("Ж", 10, 20, 14, false, 0, 1, (x, y, w, h, px, py, pw, ph) -> {
      small[0] = x;
      small[1] = y;
      small[2] = w;
      small[3] = h;
      small[4] = px;
      small[5] = py;
      small[6] = pw;
      small[7] = ph;
    });
    font.visitGlyphs("Ж", 10, 20, 14, false, 0, 3, (x, y, w, h, px, py, pw, ph) -> {
      large[0] = x;
      large[1] = y;
      large[2] = w;
      large[3] = h;
      large[4] = px;
      large[5] = py;
      large[6] = pw;
      large[7] = ph;
    });
    assertNotEquals(
        small[5], large[5], "Small text must not minify the high resolution coverage mask");
    assertTrue(large[6] > small[6]);
    assertEquals(small[2], large[2], 1.2f);
    assertEquals(small[3], large[3], 1.2f);
    final float[] starts = new float[4];
    for (int pass = 0; pass < 2; pass++) {
      final int index = pass;
      font.visitGlyphs(
          "Ж Ж", 10, 20, 14, false, 1.5f, pass == 0 ? 1 : 3, (x, y, w, h, px, py, pw, ph) -> {
            if (starts[index * 2] == 0)
              starts[index * 2] = x;
            else
              starts[index * 2 + 1] = x;
          });
    }
    assertEquals(starts[1] - starts[0], starts[3] - starts[2], .0001f,
        "Raster density cannot move following glyphs");
    assertTrue(font.width("Прозрачность", 14, false) > 50);
  }
  @Test
  void bothRasterWeightsContainRussianAlphabetAndFilteredTransparentPadding() throws Exception {
    java.util.Properties metrics = new java.util.Properties();
    try (java.io.InputStream stream =
             getClass().getResourceAsStream("/assets/nightclient/font/atlas.properties")) {
      metrics.load(stream);
    }
    java.awt.image.BufferedImage atlas;
    try (java.io.InputStream stream =
             getClass().getResourceAsStream("/assets/nightclient/textures/gui/font.png")) {
      atlas = javax.imageio.ImageIO.read(stream);
    }
    for (String prefix : new String[] {"r.", "b.", "sr.", "sb."})
      for (int code = 0x410; code <= 0x44f; code++) {
        String[] v = metrics.getProperty(prefix + code).split(",");
        int x = Integer.parseInt(v[0]), y = Integer.parseInt(v[1]), w = Integer.parseInt(v[2]),
            h = Integer.parseInt(v[3]);
        assertTrue(w > 0 && h > 0, prefix + code);
        boolean covered = false, antialiased = false;
        for (int yy = y - 1; yy <= y + h; yy++)
          for (int xx = x - 1; xx <= x + w; xx++) {
            int texel = atlas.getRGB(xx, yy), alpha = texel >>> 24;
            assertEquals(
                0xffffff, texel & 0xffffff, "Filtering cannot introduce a dark RGB fringe");
            if (xx == x - 1 || xx == x + w || yy == y - 1 || yy == y + h)
              assertEquals(0, alpha, "Glyph must have transparent padding");
            covered |= alpha > 0;
            antialiased |= alpha > 0 && alpha < 255;
          }
        assertTrue(covered);
        assertTrue(antialiased, prefix + code);
      }
  }
  @Test
  void everyBundledSvgProducesFiniteVectorGeometryWithoutRasterFallback() {
    CountingCanvas c = new CountingCanvas();
    assertTrue(Icons.ids().contains("nightclient-mark"));
    assertFalse(Icons.ids().contains("mark"));
    assertFalse(Icons.ids().contains("studio-mark"));
    assertTrue(Icons.ids().contains("blur"));
    assertTrue(Icons.ids().contains("noise"));
    for (String id : Icons.ids()) {
      int before = c.commands;
      Icons.draw(c, id, 0, 0, 24, 0xffffffff, 1.7f);
      assertTrue(c.commands > before, id);
    }
    assertThrows(
        IllegalArgumentException.class, () -> Icons.draw(c, "absent", 0, 0, 24, 0xffffffff, 1.7f));
  }
  @Test
  void filledSvgSilhouettesRetainTheirInteriorWithoutBecomingOutlines() {
    CountingCanvas c = new CountingCanvas();
    Icons.draw(c, "studio-next", 10, 20, 24, 0xffffffff, 1.7f);
    assertEquals(1, c.triangles);
    assertEquals(64, c.triangleArea, .001f);
    assertEquals(0, c.lines);
    assertEquals(0, c.outlines);
    int triangles = c.triangles;
    Icons.draw(c, "studio-player", 0, 0, 8, 0xffffffff, 1.7f);
    assertTrue(c.triangles > triangles);
    assertTrue(c.circles > 0);
    int before = c.commands;
    Icons.draw(c, "studio-style", 0, 0, 8, 0xffffffff, 1.7f);
    assertEquals(3, c.commands - before, "The own palette mark is three filled disks");
  }
  static final class CountingCanvas implements Canvas {
    int commands, triangles, lines, outlines, circles;
    float triangleArea;
    private void point(float... values) {
      commands++;
      for (float value : values) assertTrue(Float.isFinite(value));
    }
    public void fillTriangle(
        float ax, float ay, float bx, float by, float cx, float cy, int color) {
      point(ax, ay, bx, by, cx, cy);
      float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
      assertTrue(cross >= 0, "Offline SVG triangulation keeps the native GUI winding");
      triangles++;
      triangleArea += cross / 2;
    }
    public void rect(float x, float y, float w, float h, int c) {
      point(x, y, w, h);
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      point(x, y, w, h, r);
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      point(x, y, w, h, r, t);
      outlines++;
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      point(x, y, w, h);
    }
    public void line(float a, float b, float c, float d, float t, int color) {
      point(a, b, c, d, t);
      lines++;
    }
    public void circle(float x, float y, float r, int c) {
      point(x, y, r);
      circles++;
    }
    public void text(String v, float x, float y, float s, int c, boolean bold) {
      fail("SVG cannot use font placeholders");
    }
    public float textWidth(String v, float s, boolean bold) {
      return 0;
    }
    public void pushClip(float x, float y, float w, float h) {}
    public void popClip() {}
    public void item(String id, float x, float y, float s, float a) {
      fail("SVG cannot use game icons");
    }
    public void backdrop(float x, float y, float w, float h, float r, float b, int q, float a) {
      fail();
    }
    public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {
      fail("SVG cannot use raster fallbacks");
    }
  }
}
