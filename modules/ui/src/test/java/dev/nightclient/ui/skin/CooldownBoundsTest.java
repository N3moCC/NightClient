package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.skin.DefaultSkin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class CooldownBoundsTest {
  static final class Ink extends SkinViewTest.Sink {
    final List<float[]> rows = new ArrayList<float[]>();
    final List<String> labels = new ArrayList<String>();
    final List<float[]> glyphs = new ArrayList<float[]>();
    final Deque<float[]> scissors = new ArrayDeque<float[]>();
    @Override
    public void pushClip(float x, float y, float w, float h) {
      float[] parent = scissors.peek();
      if (parent != null) {
        float right = Math.min(x + w, parent[0] + parent[2]),
              bottom = Math.min(y + h, parent[1] + parent[3]);
        x = Math.max(x, parent[0]);
        y = Math.max(y, parent[1]);
        w = Math.max(0, right - x);
        h = Math.max(0, bottom - y);
      }
      scissors.push(new float[] {x, y, w, h});
      super.pushClip(x, y, w, h);
    }
    @Override
    public void popClip() {
      scissors.pop();
      super.popClip();
    }
    @Override
    public float textWidthTracked(String text, float size, boolean bold, float tracking) {
      return FontAtlas.get().width(text, size, bold, tracking);
    }
    @Override
    public float textWidthFamily(
        String family, String text, float size, boolean bold, float tracking) {
      return FontAtlas.get(family).width(text, size, bold, tracking);
    }
    @Override
    public void textFamily(String family, String text, float x, float y, float size, int color,
        boolean bold, float tracking) {
      record
      (FontAtlas.get(family), text, x, y, size, bold, tracking);
      super.textTracked(text, x, y, size, color, bold, tracking);
    }
    @Override
    public void textTracked(
        String text, float x, float y, float size, int color, boolean bold, float tracking) {
      record
      (FontAtlas.get(), text, x, y, size, bold, tracking);
      super.textTracked(text, x, y, size, color, bold, tracking);
    }
    private void record(
        FontAtlas font, String text, float x, float y, float size, boolean bold, float tracking) {
      rows.add(new float[] {x, y, x + font.width(text, size, bold, tracking), size});
      labels.add(text);
      final float[] clip = scissors.peek();
      assertNotNull(clip, "Every submitted HUD glyph has a real content clip");
      font.visitGlyphs(text, x, y, size, bold, tracking, (gx, gy, w, h, px, py, pw, ph) -> {
        float left = Math.max(gx, clip[0]), top = Math.max(gy, clip[1]),
              right = Math.min(gx + w, clip[0] + clip[2]),
              bottom = Math.min(gy + h, clip[1] + clip[3]);
        if (right > left && bottom > top) glyphs.add(new float[] {left, top, right, bottom});
      });
    }
  }
  @Test
  void actualGlyphsAndLongNamesFitAtSmallAndLargeHudScales() throws Exception {
    List<Cooldown> timers = Arrays.asList(
        new Cooldown("a", "QA custom shield with a deliberately very long localized display name",
            "minecraft:shield", 599.9f, 600),
        new Cooldown("b", "Ender Pearl", "minecraft:ender_pearl", 12000.9f, 12001));
    for (float scale : new float[] {.65f, 1.4f}) {
      Settings s = new Settings();
      s.menu.animationSpeed = 1000000f;
      s.hud.enabled = true;
      s.hud.scale = scale;
      s.hud.names = s.hud.time = s.hud.icons = true;
      s.hud.width = 260;
      s.inventory.enabled = s.tnt.enabled = s.crosshair.enabled = s.armor.enabled =
          s.potions.enabled = s.target.enabled = false;
      SkinView v = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
        @Override
        public List<Cooldown> cooldowns() {
          return timers;
        }
      });
      SkinViewTest.BoundarySink boundary = new SkinViewTest.BoundarySink(640, 400, true);
      assertTrue(v.renderHud(
          boundary, 640, 400, 1, timers, Collections.emptyList(), Collections.emptyList()));
      Ink ink = new Ink();
      v.renderHud(ink, 640, 400, 2, timers, Collections.emptyList(), Collections.emptyList());
      assertEquals(5, ink.rows.size(),
          "one header and two name/value pairs, without a count badge; scale=" + scale);
      float[] bounds = v.hudBounds("cooldowns", 640, 400, 2, false);
      assertArrayEquals(v.layoutBounds("cooldowns-frame"), Arrays.copyOf(bounds, 4), .001f,
          "The host and painter use the same actual cooldown snapshot");
      for (int row = 0; row < 2; row++) {
        float[] name = ink.rows.get(1 + row * 2), timer = ink.rows.get(2 + row * 2);
        assertEquals(name[1], timer[1], .01f, "scale=" + scale);
        assertTrue(timer[0] >= bounds[0] && timer[2] <= bounds[0] + bounds[2] + .01f);
        assertEquals(row == 0
                ? dev.nightclient.ui.HudRenderer.cooldownDuration(12000.9f, s.language)
                : "10:00",
            ink.labels.get(2 + row * 2), "scale=" + scale + " time was truncated");
        assertTrue(name[0] >= bounds[0] && name[0] < timer[0]);
      }
      assertEquals(timers.get(0).name, ink.labels.get(3),
          "The marquee retains full text for horizontal scrolling");
      assertFalse(ink.glyphs.isEmpty());
      for (float[] glyph : ink.glyphs)
        assertTrue(glyph[0] >= bounds[0] - .01f && glyph[1] >= bounds[1] - .01f
                && glyph[2] <= bounds[0] + bounds[2] + .01f
                && glyph[3] <= bounds[1] + bounds[3] + .01f,
            "Actual font ink " + Arrays.toString(glyph) + " stays inside the picked HUD frame "
                + Arrays.toString(bounds) + " at scale=" + scale);
      assertTrue(ink.scissors.isEmpty());
      assertEquals(0, ink.clips);
    }
  }
}
