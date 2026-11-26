package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.config.Theme;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurfacePainterTest {
  private static Theme glass() {
    Theme t = Theme.defaults();
    t.effects.backgroundBlur = true;
    t.blurRadius = 10;
    t.backgroundOpacity = t.surfaceOpacity = t.raisedOpacity = 1;
    t.tintOpacity = -1;
    t.glassMix = -1;
    t.borderWidth = 1;
    t.effects.grain = false;
    return t;
  }
  static final class Sink implements Canvas {
    final List<Integer> fills = new ArrayList<Integer>(), borders = new ArrayList<Integer>();
    final List<Float> radii = new ArrayList<Float>();
    final List<String> textureIds = new ArrayList<String>();
    final List<Integer> textureColors = new ArrayList<Integer>();
    float blurOpacity = -1, bx, by, bw, bh, br;
    public void rect(float x, float y, float w, float h, int c) {
      fills.add(c);
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      fills.add(c);
      radii.add(r);
      assertTrue(w >= 0 && h >= 0);
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      borders.add(c);
      assertTrue(w >= 0 && h >= 0);
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    public void line(float x, float y, float xx, float yy, float t, int c) {}
    public void circle(float x, float y, float r, int c) {}
    public void text(String v, float x, float y, float s, int c, boolean b) {}
    public float textWidth(String v, float s, boolean b) {
      return 0;
    }
    public void pushClip(float x, float y, float w, float h) {}
    public void popClip() {}
    public void item(String id, float x, float y, float s, float alpha) {}
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int q, float opacity) {
      blurOpacity = opacity;
      bx = x;
      by = y;
      bw = w;
      bh = h;
      br = r;
    }
    public void texture(String id, float x, float y, float w, float h, float r, float tile, int c) {
      textureIds.add(id);
      textureColors.add(c);
    }
  }
  @Test
  void tintOpacityIsAppliedOnceWhileBackdropStaysFullStrength() {
    Theme t = glass();
    t.backgroundOpacity = .25f;
    t.effects.grain = true;
    t.effects.grainAmount = 1;
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, 0xffffffff, 14, false);
    assertEquals(64, s.fills.get(0) >>> 24);
    assertEquals(1f, s.blurOpacity, .0001f);
    assertEquals(Math.round((t.border >>> 24) * .25f), s.borders.get(0) >>> 24);
    assertTrue(s.textureIds.isEmpty());
  }
  @Test
  void glassBackdropStopsExactlyAtThePaintedBorder() {
    Theme t = glass();
    t.glassMix = .5f;
    t.blurRadius = 8;
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 10, 20, 100, 60, 0xffffffff, 14, false);
    assertEquals(10, s.bx);
    assertEquals(20, s.by);
    assertEquals(100, s.bw);
    assertEquals(60, s.bh);
    assertEquals(14, s.br);
    assertEquals(GlassBlend.sampleOpacity(1, .5f), s.blurOpacity, .0001f);
  }
  @Test
  void composedGlassSealsItsAntialiasedRimWithTheSurfaceFill() {
    Theme t = glass();
    t.glassMix = .5f;
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, 0xff112233, 14, false);
    assertEquals(2, s.borders.size());
    assertEquals(0xff112233, (int) s.borders.get(0));
  }
  @Test
  void transparentTintSkipsBackdropAndBordersWhileTranslucentSamplesBlur() {
    Theme t = glass();
    t.backgroundOpacity = 0;
    t.effects.grain = true;
    t.effects.grainAmount = .5f;
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, t.surface, 14, true);
    assertTrue(s.fills.isEmpty());
    assertTrue(s.borders.isEmpty());
    assertEquals(-1, s.blurOpacity);
    assertTrue(s.textureIds.isEmpty());
    t.backgroundOpacity = .5f;
    s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, 0xffffffff, 14, true);
    assertEquals(1f, s.blurOpacity, .0001f);
    t.effects.backgroundBlur = false;
    s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, t.surface, 14, true);
    assertEquals(-1, s.blurOpacity);
  }
  @Test
  void selectedAndFocusedSurfacesHaveOneStateBorderWithoutDecorativeHalos() {
    Theme t = glass();
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, t.surface, 14, true, true, true);
    assertEquals(1, s.fills.size());
    assertEquals(1, s.borders.size());
    assertEquals(t.accent, (int) s.borders.get(0));
  }
  @Test
  void theEffectFlagCannotResurrectRemovedElementShadowsOrLights() {
    Theme t = glass();
    Sink a = new Sink(), b = new Sink();
    SurfacePainter.panel(a, t, 0, 0, 100, 60, t.surface, 14, false);
    SurfacePainter.panel(b, t, 0, 0, 100, 60, t.surface, 14, true);
    assertEquals(a.fills, b.fills);
    assertEquals(a.borders, b.borders);
    assertEquals(a.blurOpacity, b.blurOpacity);
  }
  @Test
  void geometryComesFromTheComponentAndAlwaysClampsToItsActualBounds() {
    Theme t = glass();
    assertEquals(14, SurfacePainter.radius(t, 100, 60, 14));
    assertEquals(4, SurfacePainter.radius(t, 20, 8, 99));
    assertEquals(0, SurfacePainter.radius(t, 20, 8, -9));
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 0, 60, t.surface, 14, true);
    assertTrue(s.fills.isEmpty());
    assertEquals(-1, s.blurOpacity);
  }
  @Test
  void raisedControlsHaveGlassTintAndOneBorderWithoutDuplicatingTheSceneSample() {
    Theme t = glass();
    Sink s = new Sink();
    SurfacePainter.control(s, t, 0, 0, 100, 28, true, true, .5f);
    assertEquals(1, s.fills.size());
    assertEquals(1, s.borders.size());
    assertEquals(-1, s.blurOpacity);
  }
  @Test
  void anOpaqueTintDoesNotRequestAnInvisibleBackdrop() {
    Theme t = glass();
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, 0xffffffff, 14, true);
    assertEquals(255, s.fills.get(0) >>> 24);
    assertEquals(-1, s.blurOpacity);
    t.backgroundOpacity = .99f;
    s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, 0xffffffff, 14, true);
    assertTrue(s.blurOpacity > 0);
  }
  @Test
  void composedGlassStillSamplesTheSceneUnderAnOpaqueSurface() {
    Theme t = glass();
    t.glassMix = .5f;
    Sink s = new Sink();
    SurfacePainter.panel(s, t, 0, 0, 100, 60, 0xffffffff, 14, true);
    assertEquals(GlassBlend.sampleOpacity(1, .5f), s.blurOpacity, .0001f);
  }
  @Test
  void backdropDemandRejectsUnsafeGpuInputsButDoesNotDependOnTintOpacity() {
    Theme t = glass();
    for (float opacity : new float[] {0, .25f, 1}) {
      t.backgroundOpacity = opacity;
      assertTrue(SurfacePainter.usesBackdrop(t));
    }
    for (float blur : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
      t.blurRadius = blur;
      assertFalse(SurfacePainter.usesBackdrop(t));
    }
    t.blurRadius = 10;
    for (float mix : new float[] {0, Float.NaN, Float.POSITIVE_INFINITY}) {
      t.blurMix = mix;
      assertFalse(SurfacePainter.usesBackdrop(t));
    }
    t.blurMix = 1;
    t.effects.backgroundBlur = false;
    assertFalse(SurfacePainter.usesBackdrop(t));
  }
}
