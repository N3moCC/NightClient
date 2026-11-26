package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.FontAtlas;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedHudGeometryTest {
  static final class Ink extends HudPresentationTest.Sink {
    final Deque<float[]> scissors = new ArrayDeque<float[]>();
    final List<float[]> ink = new ArrayList<float[]>(), contentFills = new ArrayList<float[]>(),
                        outlineBoxes = new ArrayList<float[]>();
    int outlines;
    @Override
    public void pushClip(float x, float y, float w, float h) {
      scissors.push(new float[] {x, y, w, h});
      super.pushClip(x, y, w, h);
    }
    @Override
    public void popClip() {
      scissors.pop();
      super.popClip();
    }
    @Override
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      outlines++;
      outlineBoxes.add(new float[] {x, y, w, h});
      super.strokeRoundedRect(x, y, w, h, r, t, c);
    }
    @Override
    public float textWidth(String value, float size, boolean bold) {
      return FontAtlas.get().width(value, size, bold);
    }
    @Override
    public float textWidthTracked(String value, float size, boolean bold, float tracking) {
      return FontAtlas.get().width(value, size, bold, tracking);
    }
    @Override
    public void roundedRect(float x, float y, float w, float h, float r, int color) {
      if (!scissors.isEmpty()) contentFills.add(new float[] {x, y, w, h});
      super.roundedRect(x, y, w, h, r, color);
    }
    @Override
    public void textTracked(
        final String value, float x, float y, float size, int color, boolean bold, float tracking) {
      final float[] b = scissors.peek();
      assertNotNull(b, "HUD text must be clipped to the same panel that the editor picks");
      FontAtlas.get().visitGlyphs(value, x, y, size, bold, tracking, new FontAtlas.GlyphConsumer() {
        public void glyph(float gx, float gy, float w, float h, int px, int py, int pw, int ph) {
          float left = Math.max(gx, b[0]), top = Math.max(gy, b[1]);
          float right = Math.min(gx + w, b[0] + b[2]), bottom = Math.min(gy + h, b[1] + b[3]);
          if (right > left && bottom > top)
            ink.add(new float[] {left, top, right - left, bottom - top});
        }
      });
      super.textTracked(value, x, y, size, color, bold, tracking);
    }
  }

  @Test
  void compactActualWidgetsKeepLocalizedInkInsideTheirEditorGeometry() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.armor.enabled = s.potions.enabled = s.target.enabled = true;
    s.language = "ru";
    s.theme.fontTracking = .6f;
    s.hud.width = 120;
    s.hud.fontScale = 1.5f;
    s.hud.offsetY = 17;
    s.tnt.width = 120;
    s.tnt.showDistance = true;
    s.potions.width = 120;
    s.potions.height = 46;
    s.target.width = 120;
    s.target.height = 24;
    Ink c = new Ink();
    HudRenderer.render(c, s,
        Collections.singletonList(new Cooldown(
            "a", "Алмазный щит с длинным названием", "minecraft:shield", 12000.9f, 12001)),
        640, 400, 0, false);
    FeatureHudRenderer.renderTnt(
        c, s, Collections.singletonList(new TntView(1, 0, 0, Float.MAX_VALUE, 63.9f)), 640, 400);
    ExtendedHudRenderer.renderPotions(c, s,
        Collections.singletonList(
            new PotionView("a", "Очень долгое название эффекта", 1, 0x44bb88, 12000, true)),
        640, 400);
    ExtendedHudRenderer.renderTarget(
        c, s, new TargetView(1, "uuid", "Длинный ник игрока", 20, 20, 0, 3, true), 640, 400);
    assertFalse(c.texts.isEmpty());
    assertEquals(0, c.clips);
    assertTrue(c.scissors.isEmpty());
  }

  @Test
  void itemGridHasOneOuterOutlineAndExactlyOneOutlinePerRealGridCell() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.inventory.enabled = true;
    s.theme.borderWidth = 1;
    s.inventory.hotbar = true;
    Ink c = new Ink();
    FeatureHudRenderer.renderInventory(c, s,
        Arrays.asList(new InventorySlot(9, "minecraft:ender_pearl", "Pearl", 16, 0, 0),
            new InventorySlot(0, "minecraft:shield", "Shield", 1, 20, 100)),
        640, 400);
    float[] frame = FeatureHudRenderer.inventoryBounds(s, 640, 400);
    assertEquals(
        37, c.outlines, "One outer card and nine cells in each of the four configured rows");
    float cell = (198 - 24) / 9f, scale = frame[4];
    assertArrayEquals(new float[] {frame[0] + .325f * scale, frame[1] + .325f * scale,
                          frame[2] - .65f * scale, frame[3] - .65f * scale},
        c.outlineBoxes.get(0), .005f);
    for (int row = 0; row < 4; row++)
      for (int column = 0; column < 9; column++) {
        float[] expected = {frame[0] + (4 + column * (cell + 2)) * scale,
            frame[1] + (4 + row * (cell + 2) + (row == 3 ? 4 : 0)) * scale, cell * scale,
            cell * scale};
        assertArrayEquals(expected, c.outlineBoxes.get(1 + row * 9 + column), .005f,
            "one distinct outline per inventory cell");
        assertTrue(expected[0] >= frame[0] && expected[1] >= frame[1]
            && expected[0] + expected[2] <= frame[0] + frame[2]
            && expected[1] + expected[3] <= frame[1] + frame[3]);
      }
    assertEquals(2, c.itemAlpha.size());
    assertEquals(0, c.clips);
  }

  @Test
  void resizedTargetCardDoesNotPaintHealthBarsThroughLocalizedText() {
    for (float height : new float[] {24, 36, 51, 52, 60, 63, 64, 72})
      for (float scale : new float[] {.5f, 1, 2}) {
        Settings s = new Settings();
        s.menu.animationSpeed = 1000000f;
        s.armor.enabled = s.potions.enabled = s.target.enabled = true;
        s.language = "ru";
        s.theme.fontTracking = .6f;
        s.target.width = 120;
        s.target.height = height;
        s.target.scale = scale;
        Ink c = new Ink();
        ExtendedHudRenderer.renderTarget(c, s,
            new TargetView(1, "uuid", "Игрок с длинным ником", 17.5f, 20, 4, 1200, true), 640, 400);
        assertFalse(c.ink.isEmpty());
        assertFalse(c.contentFills.isEmpty());
        for (float[] glyph : c.ink)
          for (float[] track : c.contentFills)
            if (track[3] <= 4 * scale && track[2] > 12 * scale)
              assertTrue(glyph[0] + glyph[2] <= track[0] || track[0] + track[2] <= glyph[0]
                      || glyph[1] + glyph[3] <= track[1] || track[1] + track[3] <= glyph[1],
                  "Target health track overlaps real font ink at height=" + height
                      + " scale=" + scale);
        assertEquals(0, c.clips);
        assertTrue(c.scissors.isEmpty());
      }
  }
}
