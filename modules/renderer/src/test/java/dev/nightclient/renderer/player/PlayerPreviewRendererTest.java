package dev.nightclient.renderer.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PlayerPreviewRendererTest {
  private static final int W = 240, H = 320;

  @Test
  void fullResolutionAndAntialiasedEdgesRemainAvailableOnLargePreviews() {
    int[] size = PlayerPreviewRenderer.previewSize(1200, 900, 1);
    assertArrayEquals(new int[] {1200, 900}, size);
    int[] capped = PlayerPreviewRenderer.previewSize(2400, 1600, 2);
    assertTrue((long) capped[0] * capped[1] <= PlayerPreviewRenderer.MAX_PREVIEW_PIXELS);
    assertTrue((long) capped[0] * capped[1] > 1_190_000);
    assertEquals(0, PlayerPreviewRenderer.previewSize(Float.NaN, 100, 1)[0]);

    int width = 400, height = 320;
    int[] opaqueSkin = new int[64 * 64];
    java.util.Arrays.fill(opaqueSkin, 0xffbe9477);
    int[] out = new int[width * height];
    new PlayerPreviewRenderer().render(
        new PlayerSkinData(opaqueSkin, null, false), -22, 6, 1, 0, 0, 0, width, height, out);
    int antialiased = 0;
    for (int pixel : out)
      if ((pixel >>> 24) > 0 && (pixel >>> 24) < 255) antialiased++;
    assertTrue(antialiased > 100, "Large previews must keep antialiased model edges");
  }

  @Test
  void legsMeetWithoutTransparentGap() {
    int[] opaqueSkin = new int[64 * 64];
    java.util.Arrays.fill(opaqueSkin, 0xffbe9477);
    int width = 400, height = 320;
    int[] out = new int[width * height];
    new PlayerPreviewRenderer().render(
        new PlayerSkinData(opaqueSkin, null, false), 0, 0, 1, 0, 0, 0, width, height, out);
    for (int y = 210; y < 278; y++)
      assertTrue((out[y * width + width / 2] >>> 24) > 245, "Gap between legs at row " + y);
  }

  @Test
  void rendersOpaqueModelOnTransparentBackground() {
    int[] out = new int[W * H];
    new PlayerPreviewRenderer().render(
        DefaultSkins.classic(), -22f, 6f, 1f, 1.2f, .7f, 0f, W, H, out);
    int opaque = 0;
    for (int p : out)
      if ((p >>> 24) > 200) opaque++;
    assertTrue(opaque > 18000, "expected a substantial model, got " + opaque + " opaque pixels");
    assertTrue((out[0] >>> 24) == 0, "top-left corner must be transparent");
    assertTrue((out[W - 1] >>> 24) == 0, "top-right corner must be transparent");
  }

  @Test
  void frontFaceUsesFrontHeadPixels() {
    int[] body = DefaultSkins.classic().body.clone();
    for (int y = 0; y < 16; y++)
      for (int x = 32; x < 64; x++) body[y * 64 + x] = 0;
    for (int y = 8; y < 16; y++)
      for (int x = 8; x < 16; x++) body[y * 64 + x] = 0xffd83b3b;
    PlayerSkinData skin = new PlayerSkinData(body, null, false);
    int[] out = new int[W * H];
    new PlayerPreviewRenderer().render(skin, 0f, 0f, 1f, 0f, 0f, 0f, W, H, out);
    int redPixels = 0;
    for (int y = 25; y < 115; y++) {
      for (int x = 80; x < 160; x++) {
        int pixel = out[y * W + x];
        int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
        if ((pixel >>> 24) > 200 && r > 90 && r > g + 35 && r > b + 35) redPixels++;
      }
    }
    assertTrue(redPixels > 200, "front face should be red, redPixels=" + redPixels);
  }

  @Test
  void sameInputsProduceIdenticalFrame() {
    int[] first = new int[W * H], second = new int[W * H];
    PlayerPreviewRenderer renderer = new PlayerPreviewRenderer();
    renderer.render(DefaultSkins.classic(), -18f, 5f, 1f, 2f, .5f, 0f, W, H, first);
    renderer.render(DefaultSkins.classic(), -18f, 5f, 1f, 2f, .5f, 0f, W, H, second);
    assertArrayEquals(first, second);
  }

  @Test
  void capeMovesWithWindAndTime() {
    int[] first = new int[W * H], second = new int[W * H];
    PlayerPreviewRenderer a = new PlayerPreviewRenderer();
    a.render(DefaultSkins.classic(), 150f, 2f, 1f, 0f, 0f, 0f, W, H, first);
    PlayerPreviewRenderer b = new PlayerPreviewRenderer();
    b.render(DefaultSkins.classic(), 150f, 2f, 1f, 0f, 0f, 0f, W, H, second);
    for (int i = 1; i <= 50; i++)
      b.render(DefaultSkins.classic(), 150f, 2f, 1f, i * .05f, 1.8f, 40f, W, H, second);
    int changed = 0;
    for (int i = 0; i < first.length; i++)
      if (first[i] != second[i]) changed++;
    assertTrue(changed > 500, "cape animation should visibly change pixels, changed=" + changed);
  }

  @Test
  void slimArmsDifferFromClassic() {
    int[] classic = new int[W * H], slim = new int[W * H];
    new PlayerPreviewRenderer().render(
        DefaultSkins.classic(), 30f, 4f, 1f, 0f, 0f, 0f, W, H, classic);
    new PlayerPreviewRenderer().render(DefaultSkins.slim(), 30f, 4f, 1f, 0f, 0f, 0f, W, H, slim);
    assertNotEquals(hash(classic), hash(slim));
  }

  @Test
  void writesPreviewPng() throws Exception {
    int[] out = new int[W * H];
    PlayerPreviewRenderer renderer = new PlayerPreviewRenderer();
    renderer.render(DefaultSkins.classic(), -23f, 7f, 1f, 0f, 0f, 0f, W, H, out);
    write(out, new File("/tmp/player-preview.png"));
    renderer.render(DefaultSkins.classic(), 158f, 5f, 1f, .03f, .7f, 0f, W, H, out);
    write(out, new File("/tmp/player-preview-back.png"));
    assertTrue(new File("/tmp/player-preview.png").length() > 1000);
    assertTrue(new File("/tmp/player-preview-back.png").length() > 1000);
  }

  @Test
  void benchmark240x320() {
    int[] out = new int[W * H];
    PlayerPreviewRenderer renderer = new PlayerPreviewRenderer();
    for (int i = 0; i < 8; i++)
      renderer.render(DefaultSkins.classic(), -22f, 6f, 1f, i * .03f, .8f, 20f, W, H, out);
    long start = System.nanoTime();
    int frames = 40;
    for (int i = 0; i < frames; i++)
      renderer.render(
          DefaultSkins.classic(), -22f + i * .2f, 6f, 1f, .3f + i * .03f, .8f, 20f, W, H, out);
    double ms = (System.nanoTime() - start) / 1e6 / frames;
    System.out.println("player-preview 240x320: " + String.format(java.util.Locale.ROOT, "%.2f", ms)
        + " ms/frame");
    assertTrue(ms < 50.0, "render is unexpectedly slow: " + ms + " ms");
  }

  @Test
  void highResolution4kSkinAndMojangCapeUvRenderCleanly() {
    int[] hdBody = new int[512 * 512];
    java.util.Arrays.fill(hdBody, 0xff4080d0);
    int[] hdCape = new int[512 * 256];
    int s = 512 / 64;
    for (int y = s; y < 17 * s; y++)
      for (int x = s; x < 11 * s; x++) hdCape[y * 512 + x] = 0xffd62828;
    PlayerSkinData skin = new PlayerSkinData(hdBody, 512, 512, hdCape, 512, 256, true);
    int[] out = new int[W * H];
    new PlayerPreviewRenderer().render(skin, 180f, 3f, 1f, 0f, 0f, 0f, W, H, out);
    int red = 0;
    for (int y = 90; y < 250; y++) {
      for (int x = 80; x < 160; x++) {
        int c = out[y * W + x];
        if ((c >>> 24) > 200 && ((c >> 16) & 255) > ((c & 255) + 40)) red++;
      }
    }
    assertTrue(red > 500, "Mojang cape front UV should render red cape panel, red=" + red);

    int[] fourKBody = new int[4096 * 4096];
    fourKBody[0] = 0xff00ff00;
    PlayerSkinData fourK = new PlayerSkinData(fourKBody, 4096, 4096, null, 0, 0, false);
    assertEquals(4096, fourK.bodyWidth());
    assertEquals(4096, fourK.bodyHeight());
  }

  @Test
  void capeHasVolumetricThicknessFromSideView() {
    int[] body = new int[64 * 64];
    java.util.Arrays.fill(body, 0xff2244aa);
    int[] cape = new int[64 * 32];
    java.util.Arrays.fill(cape, 0xffdd2233);
    PlayerSkinData skin = new PlayerSkinData(body, 64, 64, cape, 64, 32, false);
    int[] out = new int[W * H];
    new PlayerPreviewRenderer().render(skin, 90f, 0f, 1f, 0f, 0f, 0f, W, H, out);
    int maxCapeRowSpan = 0;
    for (int y = 90; y < 220; y++) {
      int redSpan = 0;
      for (int x = 0; x < W; x++) {
        int c = out[y * W + x];
        if ((c >>> 24) > 200 && ((c >> 16) & 255) > ((c & 255) + 40)) redSpan++;
      }
      if (redSpan > maxCapeRowSpan) maxCapeRowSpan = redSpan;
    }
    assertTrue(maxCapeRowSpan >= 7,
        "Cape should show genuine 3D slab thickness from side angle, redSpan=" + maxCapeRowSpan);
  }

  @Test
  void capeCardRenders3dThumbnailWithoutBody() {
    int[] cape = DefaultSkins.synthesizeCape(
        dev.nightclient.cosmetics.OfficialCapes.byId("valentine"));
    int[] card = new int[56 * 72];
    PlayerPreviewRenderer.renderCapeCard(cape, 64, 32, 56, 72, card);
    int solid = 0;
    for (int px : card)
      if ((px >>> 24) > 200) solid++;
    assertTrue(solid > 1200, "Cape card 3D render should fill thumbnail cleanly, solid=" + solid);
  }

  private static int hash(int[] values) {
    int h = 1;
    for (int v : values) h = 31 * h + v;
    return h;
  }

  private static void write(int[] pixels, File file) throws Exception {
    BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, W, H, pixels, 0, W);
    ImageIO.write(image, "png", file);
  }
}
