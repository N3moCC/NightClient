package dev.nightclient.renderer.player;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerFacePainterTest {
  @Test
  void extractsBaseFaceAndCompositesHatOverlayFromStandardAndHdSkins() {
    int[] body = new int[64 * 64];
    for (int y = 8; y < 16; y++)
      for (int x = 8; x < 16; x++) body[y * 64 + x] = 0xff224466;
    body[8 * 64 + 40] = 0x80ff0000;
    body[15 * 64 + 47] = 0xff00ff00;

    PlayerSkinData skin = new PlayerSkinData(body, 64, 64, null, 0, 0, false);
    int[] face = PlayerFacePainter.extractFace(skin);
    assertNotNull(face);
    assertEquals(64, face.length);
    assertEquals(0xff224466, face[1]);
    assertEquals(0xff00ff00, face[63]);
    assertNotEquals(0xff224466, face[0]);
    assertEquals(0xff, (face[0] >>> 24) & 0xff);

    int[] hdBody = new int[128 * 128];
    for (int y = 16; y < 32; y++)
      for (int x = 16; x < 32; x++) hdBody[y * 128 + x] = 0xff336699;
    PlayerSkinData hd = new PlayerSkinData(hdBody, 128, 128, null, 0, 0, true);
    int[] hdFace = PlayerFacePainter.extractFace(hd);
    assertNotNull(hdFace);
    assertEquals(0xff336699, hdFace[0]);
  }

  @Test
  void fallbackFaceIsDeterministicAndRunLengthMergingBatchesRects() {
    int[] a1 = PlayerFacePainter.fallbackFace("FrostBlade99");
    int[] a2 = PlayerFacePainter.fallbackFace("frostblade99");
    int[] b = PlayerFacePainter.fallbackFace("SolarWisp");
    assertArrayEquals(a1, a2);
    assertEquals(64, a1.length);
    assertEquals(64, b.length);

    int[] solid = new int[64];
    java.util.Arrays.fill(solid, 0xff112233);
    final List<float[]> rects = new ArrayList<float[]>();
    Canvas stub = new Canvas() {
      @Override public void rect(float x, float y, float w, float h, int argb) {
        rects.add(new float[] {x, y, w, h, argb});
      }
      @Override public void roundedRect(float x, float y, float w, float h, float r, int argb) {}
      @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int argb) {}
      @Override public void gradientRect(float x, float y, float w, float h, int top, int bot) {}
      @Override public void line(float x1, float y1, float x2, float y2, float t, int argb) {}
      @Override public void circle(float cx, float cy, float r, int argb) {}
      @Override public void text(String v, float x, float y, float s, int argb, boolean bold) {}
      @Override public float textWidth(String v, float s, boolean bold) { return 0; }
      @Override public void pushClip(float x, float y, float w, float h) {}
      @Override public void popClip() {}
      @Override public void item(String id, float x, float y, float s, float a) {}
      @Override public void backdrop(float x, float y, float w, float h, float r, float br, int q, float o) {}
      @Override public void texture(String id, float x, float y, float w, float h, float r, float ts, int argb) {}
    };

    PlayerFacePainter.paint(stub, solid, 10f, 20f, 16f, 1f);
    assertEquals(8, rects.size(), "Solid 8x8 face merges each row into a single quad");
    assertEquals(10f, rects.get(0)[0], 0.001f);
    assertEquals(20f, rects.get(0)[1], 0.001f);
    assertEquals(16f, rects.get(0)[2], 0.001f);
    assertEquals(2f, rects.get(0)[3], 0.001f);
  }
}
