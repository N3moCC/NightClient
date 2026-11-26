package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HudPresentationTest {
  static final class Text {
    final String value;
    final float x, y, size;
    final int color;
    Text(String v, float x, float y, float s, int c) {
      value = v;
      this.x = x;
      this.y = y;
      size = s;
      color = c;
    }
  }
  static final class HungerIcon {
    final float x, y, size;
    final int fill;
    HungerIcon(float x, float y, float size, int fill) {
      this.x = x;
      this.y = y;
      this.size = size;
      this.fill = fill;
    }
  }
  static class Sink implements Canvas {
    final List<Text> texts = new ArrayList<Text>();
    final List<Float> itemAlpha = new ArrayList<Float>();
    final List<HungerIcon> hungerIcons = new ArrayList<HungerIcon>();
    final List<Integer> fillColors = new ArrayList<Integer>(),
                        crosshairColors = new ArrayList<Integer>();
    float[] clip;
    int clips, backdropCalls;
    float maxRadius;
    public void rect(float x, float y, float w, float h, int c) {
      fillColors.add(c);
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      assertTrue(w >= 0 && h >= 0);
      fillColors.add(c);
      maxRadius = Math.max(maxRadius, r);
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      assertTrue(w >= 0 && h >= 0);
      crosshairColors.add(c);
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    public void line(float x, float y, float xx, float yy, float t, int c) {
      crosshairColors.add(c);
    }
    public void circle(float x, float y, float r, int c) {
      crosshairColors.add(c);
    }
    public void text(String v, float x, float y, float s, int c, boolean b) {
      texts.add(new Text(v, x, y, s, c));
    }
    public void textTracked(String v, float x, float y, float s, int c, boolean b, float tracking) {
      text(v, x, y, s, c, b);
    }
    public float textWidth(String v, float s, boolean b) {
      return v.codePointCount(0, v.length()) * s * .5f;
    }
    public void pushClip(float x, float y, float w, float h) {
      clip = new float[] {x, y, w, h};
      clips++;
    }
    public void popClip() {
      assertTrue(clips > 0);
      clips--;
    }
    public void item(String id, float x, float y, float s, float alpha) {
      itemAlpha.add(alpha);
    }
    public void hungerIcon(float x, float y, float size, int fill) {
      hungerIcons.add(new HungerIcon(x, y, size, fill));
    }
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float opacity) {
      backdropCalls++;
    }
    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
    }
  }
  private static Settings quiet() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.menu.hudScale = 1;
    s.theme.surfaceOpacity = s.theme.raisedOpacity = 1;
    return s;
  }
  @Test
  void transparentHudBackgroundKeepsCooldownTextAndItemOpaque() {
    Settings s = quiet();
    s.theme.backgroundOpacity = .2f;
    Sink sink = new Sink();
    HudRenderer.render(sink, s,
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 5, 10)), 900, 600,
        0, false);
    assertEquals(Collections.singletonList(1f), sink.itemAlpha);
    assertFalse(sink.texts.isEmpty());
    for (Text text : sink.texts) assertEquals(255, text.color >>> 24);
    assertTrue(sink.fillColors.stream().anyMatch(c -> (c >>> 24) > 0 && (c >>> 24) <= 51));
    assertEquals(0, sink.clips);
  }
  @Test
  void unifiedInventoryOpacityAffectsOnlySurfacesAndUsesExactSlot() {
    Settings s = quiet();
    s.inventory.enabled = true;
    s.theme.backgroundOpacity = .15f;
    Sink sink = new Sink();
    FeatureHudRenderer.render(sink, s,
        Collections.singletonList(new InventorySlot(9, "minecraft:ender_pearl", "Pearl", 16, 0, 0)),
        null, 900, 600, 0, false);
    assertEquals(Collections.singletonList(1f), sink.itemAlpha);
    for (Text text : sink.texts) assertEquals(255, text.color >>> 24);
    assertTrue(sink.fillColors.stream().anyMatch(c -> (c >>> 24) > 0 && (c >>> 24) <= 38));
    assertEquals(0, sink.clips);
  }
  @Test
  void transparentTntPanelPreservesFuseForegroundAndClampsAllText() {
    Settings s = quiet();
    s.tnt.enabled = true;
    s.theme.effects.backgroundBlur = true;
    s.theme.backgroundOpacity = 0f;
    s.tnt.width = 8f;
    Sink sink = new Sink();
    FeatureHudRenderer.render(sink, s, null,
        Collections.singletonList(new TntView(1, 0, 0, Float.MAX_VALUE, 2)), 900, 600, 0, false);
    assertFalse(sink.texts.isEmpty());
    for (Text text : sink.texts) assertEquals(255, text.color >>> 24);
    assertEquals(
        0, sink.backdropCalls, "Fully transparent panels omit redundant backdrop requests");
    assertEquals(0, sink.clips);
    s.theme.backgroundOpacity = .25f;
    sink = new Sink();
    FeatureHudRenderer.render(sink, s, null,
        Collections.singletonList(new TntView(1, 0, 0, Float.MAX_VALUE, 2)), 900, 600, 0, false);
    assertTrue(sink.backdropCalls > 0, "Translucent panels still request backdrop blur");
    for (Text text : sink.texts) assertEquals(255, text.color >>> 24);
  }
  @Test
  void hugeCooldownTimeStaysInsideItsActualRowAndClip() {
    Settings s = quiet();
    s.hud.width = 120;
    s.hud.fontScale = 1.5f;
    Sink sink = new Sink();
    HudRenderer.render(sink, s,
        Collections.singletonList(new Cooldown(
            "a", "Very long actual name", "minecraft:shield", Float.MAX_VALUE, Float.MAX_VALUE)),
        900, 600, 0, false);
    assertNotNull(sink.clip);
    float[] b = HudRenderer.bounds(s,
        Collections.singletonList(new Cooldown(
            "a", "Very long actual name", "minecraft:shield", Float.MAX_VALUE, Float.MAX_VALUE)),
        900, 600, true);
    for (Text text : sink.texts) {
      assertTrue(text.x >= b[0] - .01f);
      assertTrue(text.y >= b[1] - .01f);
      assertTrue(text.y + text.size <= b[1] + b[3] + .01f);
    }
    assertEquals(0, sink.clips);
  }
  @Test
  void crosshairUsesUnifiedHudThemeAndIgnoresBackgroundOpacity() {
    Settings s = quiet();
    s.crosshair.enabled = true;
    s.crosshair.mode = "shape";
    s.theme = Theme.defaults();
    s.theme.accent = 0xff7ce8bb;
    s.theme.backgroundOpacity = 0f;
    Sink sink = new Sink();
    FeatureHudRenderer.render(sink, s, null, null, 900, 600, 0, false);
    assertTrue(sink.fillColors.contains(s.theme.accent));
    s.theme.accent = 0xff123456;
    sink = new Sink();
    FeatureHudRenderer.render(sink, s, null, null, 900, 600, 0, false);
    assertTrue(sink.fillColors.contains(0xff123456));
  }
  @Test
  void explicitReticleOpacityFadesEveryShapeIndependentlyOfHudBackground() {
    Settings s = quiet();
    s.crosshair.enabled = true;
    s.crosshair.mode = "shape";
    s.crosshair.opacity = .4f;
    s.theme.backgroundOpacity = 0f;
    for (String shape : new String[] {"cross", "circle", "diamond", "dot"}) {
      s.crosshair.preset = shape;
      Sink sink = new Sink();
      FeatureHudRenderer.render(sink, s, null, null, 900, 600, 0, false);
      assertFalse(sink.fillColors.isEmpty() && sink.crosshairColors.isEmpty());
      for (int color : sink.fillColors) assertEquals(102, color >>> 24);
      for (int color : sink.crosshairColors) assertEquals(102, color >>> 24);
    }
  }
  @Test
  void explicitForegroundHexAlphaIsPreservedIndependently() {
    Settings s = quiet();
    s.theme.text = 0x804488cc;
    s.theme.backgroundOpacity = .1f;
    try {
      s.skinDocument =
          dev.nightclient.skin.NcParser.parse("style class dfh-label { color = #4488cc80 }");
    } catch (java.io.IOException failure) {
      throw new AssertionError(failure);
    }
    Sink sink = new Sink();
    HudRenderer.render(sink, s,
        Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 5, 10)), 900, 600,
        0, false);
    boolean found = false;
    for (Text text : sink.texts)
      if ("Shield".equals(text.value)) {
        found = true;
        assertEquals(0x804488cc, text.color);
      }
    assertTrue(found);
  }
}
