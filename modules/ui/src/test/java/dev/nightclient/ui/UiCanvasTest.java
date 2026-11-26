package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.config.Theme;
import org.junit.jupiter.api.Test;

class UiCanvasTest {
  private static final class Sink implements Canvas {
    float[] backdrop, texture, gradient, text, hunger, sprite, triangle;
    int textureColor, textColor, spriteColor, triangleColor;
    float itemAlpha;
    InventorySlot slot;
    boolean bold;
    public void rect(float x, float y, float w, float h, int c) {}
    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {
      triangle = new float[] {ax, ay, bx, by, cx, cy};
      triangleColor = c;
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {}
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    public void gradientRoundedRect(float x, float y, float w, float h, float r, int a, int b) {
      gradient = new float[] {x, y, w, h, r};
    }
    public void circle(float x, float y, float r, int c) {}
    public void line(float x, float y, float xx, float yy, float t, int c) {}
    public void text(String v, float x, float y, float size, int c, boolean b) {
      fail("The themed canvas must use tracked text");
    }
    public float textWidth(String v, float s, boolean b) {
      return v.codePointCount(0, v.length()) * s * .5f;
    }
    public void textTracked(String v, float x, float y, float s, int c, boolean b, float tracking) {
      text = new float[] {x, y, s, tracking};
      textColor = c;
      bold = b;
    }
    public float textWidthTracked(String v, float s, boolean b, float tracking) {
      return textWidth(v, s, b) + (v.codePointCount(0, v.length()) - 1) * tracking;
    }
    public void pushClip(float x, float y, float w, float h) {}
    public void popClip() {}
    public void item(String id, float x, float y, float size, float alpha) {
      itemAlpha = alpha;
    }
    public void inventoryItem(InventorySlot value, float x, float y, float size, float alpha) {
      slot = value;
      itemAlpha = alpha;
    }
    public void hungerIcon(float x, float y, float size, int fill) {
      hunger = new float[] {x, y, size, fill};
    }
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float alpha) {
      backdrop = new float[] {x, y, w, h, r, blur, quality, alpha};
    }
    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
      texture = new float[] {x, y, w, h, r, tile};
      textureColor = c;
    }
    public void gameSprite(String id, float x, float y, float w, float h, float u, float v,
        float rw, float rh, float sw, float sh, int c) {
      sprite = new float[] {x, y, w, h, u, v, rw, rh, sw, sh};
      spriteColor = c;
    }
  }
  @Test
  void nestedMaterialBoundsAndOpacityFollowTheSameTransformAsGeometry() {
    Sink sink = new Sink();
    UiCanvas outer = new UiCanvas(sink, 10, 20, 2, .5f),
             inner = new UiCanvas(outer, 3, 4, .5f, .8f);
    inner.backdrop(2, 6, 12, 10, 4, 8, 3, .7f);
    assertArrayEquals(new float[] {18, 34, 12, 10, 4, 8, 3, .28f}, sink.backdrop, .0001f);
    inner.texture("nightclient:textures/ui/grain.png", 2, 6, 12, 10, 4, 128, 0xffffffff);
    assertArrayEquals(new float[] {18, 34, 12, 10, 4, 128}, sink.texture, .0001f);
    assertEquals(0x66ffffff, sink.textureColor);
    inner.gradientRoundedRect(2, 6, 12, 10, 4, 0xffffffff, 0xff000000);
    assertArrayEquals(new float[] {18, 34, 12, 10, 4}, sink.gradient, .0001f);
  }
  @Test
  void themedFontWeightAndTrackingUseIdenticalDrawAndMeasureSemantics() {
    Sink sink = new Sink();
    Theme theme = new Theme();
    theme.fontWeight = "bold";
    theme.fontTracking = .4f;
    UiCanvas canvas = new UiCanvas(sink, 10, 20, 2, .5f, theme);
    canvas.text("A🌙", 3, 4, 12, 0xffffffff, false);
    assertTrue(sink.bold);
    assertArrayEquals(new float[] {16, 28, 24, .8f}, sink.text, .0001f);
    assertEquals(12.4f, canvas.textWidth("A🌙", 12, false), .0001f);
  }
  @Test
  void backgroundRecipeDoesNotChangeTextOrNativeItemAlpha() {
    Sink sink = new Sink();
    Theme t = new Theme();
    t.backgroundOpacity = 0;
    UiCanvas canvas = new UiCanvas(sink, 10, 20, 2, 1, t);
    canvas.text("Visible", 0, 0, 12, 0xffabcdef, false);
    InventorySlot slot = new InventorySlot(10, "minecraft:shield", "Actual shield", 1, 0, 0, true);
    canvas.inventoryItem(slot, 2, 3, 16, 1);
    assertEquals(0xffabcdef, sink.textColor);
    assertSame(slot, sink.slot);
    assertEquals(1, sink.itemAlpha, .0001f);
  }
  @Test
  void explicitAnimationAlphaCanStillFadeAllContent() {
    Sink sink = new Sink();
    Theme t = new Theme();
    t.backgroundOpacity = 0;
    UiCanvas canvas = new UiCanvas(sink, 0, 0, 1, .4f, t);
    canvas.text("Animated", 0, 0, 12, 0xffabcdef, false);
    canvas.item("minecraft:shield", 0, 0, 16, 1);
    assertEquals(0x66abcdef, sink.textColor);
    assertEquals(.4f, sink.itemAlpha, .0001f);
  }
  @Test
  void nativeHungerTexturesRetainTheSameNestedPositionAndScaleAsHudGeometry() {
    Sink sink = new Sink();
    UiCanvas outer = new UiCanvas(sink, 10, 20, 2, 1), inner = new UiCanvas(outer, 3, 4, .5f, 1);
    inner.hungerIcon(2, 6, 9, 1);
    assertArrayEquals(new float[] {18, 34, 9, 1}, sink.hunger);
  }
  @Test
  void spriteUvAndVectorWindingSurviveNestedUiTransforms() {
    Sink sink = new Sink();
    UiCanvas outer = new UiCanvas(sink, 10, 20, 2, .5f),
             inner = new UiCanvas(outer, 3, 4, .5f, .8f);
    inner.gameSprite("minecraft:hud/hotbar", 2, 6, 22, 22, 160, 0, 22, 22, 182, 22, 0xffffffff);
    assertArrayEquals(new float[] {18, 34, 22, 22, 160, 0, 22, 22, 182, 22}, sink.sprite, .0001f);
    assertEquals(0x66ffffff, sink.spriteColor);
    inner.fillTriangle(2, 6, 4, 8, 7, 6, 0xffffffff);
    assertArrayEquals(new float[] {18, 34, 20, 36, 23, 34}, sink.triangle, .0001f);
    assertEquals(0x66ffffff, sink.triangleColor);
  }
}
