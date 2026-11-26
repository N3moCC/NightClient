package dev.nightclient.ui;
import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.*;
import dev.nightclient.config.Settings;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
final class ViewmodelEditorTest {
  private static final class Actions implements UiActions {
    long revision;
    boolean editing, available = true, supported = true;
    String hand = "main";
    int checkpoints, picks, hovers;
    float hoverX, hoverY;
    final PickMask mask;
    Actions() {
      ByteBuffer rgba = ByteBuffer.allocate(36);
      for (int i = 0; i < 9; i++)
        if (i != 4) rgba.put(i * 4 + 3, (byte) 255);
      mask = PickMask.fromRgba(rgba, 3, 3, 30, 30, false);
    }
    public ViewmodelSelection viewmodelSelection(float x, float y) {
      return new ViewmodelSelection(available && mask.contains(x, y) ? hand : "",
          available && mask.contains(x, y) ? mask : null, revision,
          available && "main".equals(hand) ? mask : null,
          available && "off".equals(hand) ? mask : null);
    }
    public void requestViewmodelPick(float x, float y) {
      picks++;
      revision++;
    }
    public void hoverViewmodel(float x, float y) {
      hovers++;
      hoverX = x;
      hoverY = y;
    }
    public void setViewmodelEditing(boolean value) {
      editing = value;
    }
    public boolean viewmodelEditorSupported() {
      return supported;
    }
    public void checkpoint() {
      checkpoints++;
    }
    public void changed() {}
    public String clipboard() {
      return "";
    }
    public void clipboard(String v) {}
    public List<String> profiles() {
      return Collections.emptyList();
    }
    public void saveProfile(String v) {}
    public void loadProfile(String v) {}
    public void deleteProfile(String v) {}
    public String exportProfile() {
      return "{}";
    }
    public void importProfile(String v) {}
    public void reset() {}
    public void undo() {}
    public boolean canUndo() {
      return false;
    }
    public void close() {}
    public List<Cooldown> cooldowns() {
      return Collections.emptyList();
    }
    public String gameVersion() {
      return "test";
    }
    public String saveStatus() {
      return "";
    }
  }
  @Test
  void clickInsideTransparentHoleDoesNotSelectBounds() {
    Settings s = new Settings();
    Actions a = new Actions();
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    assertTrue(a.editing);
    e.mouseDown(15, 15, 0, 0);
    e.update(0, 15, 15);
    assertFalse(e.isSelected());
    assertFalse(e.key('G', 0));
    e.mouseDown(5, 5, 0, 0);
    e.update(1, 5, 5);
    assertTrue(e.isSelected());
    e.close();
    assertFalse(a.editing);
  }
  @Test
  void hoverUsesGpuCommandBeforeSelectionWithoutRequestingCpuMasks() {
    Settings s = new Settings();
    Actions a = new Actions();
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    final int[] contours = {0};
    HudPresentationTest.Sink c = new HudPresentationTest.Sink() {
      public void viewmodelOutline(String hand, int argb) {
        assertEquals("hover", hand);
        assertEquals(s.theme.accent, argb);
        contours[0]++;
      }
    };
    for (int frame = 0; frame < 120; frame++) {
      e.update(frame / 60.0, 5 + frame * .1f, 5);
      e.renderOutline(c, 800, 600);
      e.render(c, 800, 600, frame / 60.0);
    }
    assertEquals(120, a.hovers);
    assertEquals(120, contours[0]);
    assertEquals(0, a.picks);
    assertFalse(e.isSelected());
    assertEquals(16.9f, a.hoverX, .001);
    assertEquals(5, a.hoverY);
    e.close();
    e.update(3, 2, 2);
    e.renderOutline(c, 800, 600);
    e.render(c, 800, 600, 3);
    assertEquals(120, a.hovers);
    assertEquals(120, contours[0]);
  }
  @Test
  void hoverCompositeUsesTheActualViewportClipRatherThanOnePixel() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    Actions a = new Actions();
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.update(0, 715, 510);
    final Deque<float[]> clipStack = new ArrayDeque<float[]>();
    final int[] composites = {0};
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
      public void pushClip(float x, float y, float w, float h) {
        float[] box = {x, y, x + w, y + h};
        if (!clipStack.isEmpty()) {
          float[] parent = clipStack.peek();
          box[0] = Math.max(box[0], parent[0]);
          box[1] = Math.max(box[1], parent[1]);
          box[2] = Math.min(box[2], parent[2]);
          box[3] = Math.min(box[3], parent[3]);
        }
        clipStack.push(box);
      }
      public void popClip() {
        clipStack.pop();
      }
      public void viewmodelOutline(String hand, int color) {
        assertEquals("hover", hand);
        assertFalse(clipStack.isEmpty());
        float[] clip = clipStack.peek();
        assertTrue(clip[0] <= 715 && clip[1] <= 510 && clip[2] > 715 && clip[3] > 510,
            "Final native outline composite must include the hand pixel near the lower right");
        composites[0]++;
      }
    };
    e.renderOutline(sink, 960, 540);
    assertEquals(1, composites[0]);
    assertTrue(clipStack.isEmpty());
  }
  @Test
  void rotationGizmoAndHoverRemainVisiblePastTheViewsCumulativeSafetyBudget() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    e.chooseMode("R");
    final int[] triangles = {0}, contours = {0};
    HudPresentationTest.Sink sink = new HudPresentationTest.Sink() {
      public void fillTriangle(
          float ax, float ay, float bx, float by, float cx, float cy, int color) {
        triangles[0]++;
      }
      public void viewmodelOutline(String hand, int color) {
        contours[0]++;
      }
    };
    for (int frame = 0; frame < 120; frame++) {
      e.update(frame / 60.0, 20, 20);
      triangles[0] = 0;
      e.renderOutline(sink, 800, 600);
      e.render(sink, 800, 600, frame / 60.0);
      assertTrue(
          triangles[0] >= 1000, "All three volumetric rings must still paint at frame " + frame);
      assertEquals(frame + 1, contours[0]);
    }
  }
  @Test
  void modalAxisNumericTransformCancellationAndUniformReset() {
    Settings s = new Settings();
    Actions a = new Actions();
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(5, 5, 0, 0);
    e.update(0, 5, 5);
    assertTrue(e.key('R', 0));
    e.key('X', 0);
    e.key('9', 0);
    e.key('0', 0);
    assertEquals(90, s.viewmodel.main.rotateX, .001);
    assertEquals(0, s.viewmodel.main.rotateZ, .001);
    e.key(Keys.ESCAPE, 0);
    assertEquals(0, s.viewmodel.main.rotateX, .001);
    assertFalse(s.viewmodel.enabled);
    e.key('S', 0);
    e.key('X', 0);
    e.key('2', 0);
    assertEquals(2, s.viewmodel.main.scaleX, .001);
    assertEquals(1, s.viewmodel.main.scaleY, .001);
    e.key(Keys.ENTER, 0);
    assertTrue(s.viewmodel.enabled);
    assertEquals(2, a.checkpoints);
    e.key('S', Keys.MOD_ALT);
    assertEquals(1, s.viewmodel.main.scaleX, .001);
    assertEquals(3, a.checkpoints);
  }
  @Test
  void pointerTranslationFineMovementAndCloseRestoreCancelledGesture() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    Actions a = new Actions();
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(5, 5, 0, 0);
    e.update(0, 5, 5);
    e.key('G', 0);
    e.key('X', 0);
    e.mouseDrag(55, 5, Keys.MOD_SHIFT);
    assertEquals(.02, s.viewmodel.main.x, .0001);
    assertEquals(0, s.viewmodel.main.y, .0001);
    e.close();
    assertEquals(0, s.viewmodel.main.x, .0001);
    assertFalse(a.editing);
  }
  @Test
  void losingAnOffHandSelectionCancelsThatHandBeforeClearingItsIdentity() {
    Settings s = new Settings();
    Actions a = new Actions();
    a.hand = "off";
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(5, 5, 0, 0);
    e.update(0, 5, 5);
    e.key('R', 0);
    e.key('X', 0);
    e.key('9', 0);
    e.key('0', 0);
    assertEquals(90, s.viewmodel.off.rotateX, .001);
    a.available = false;
    a.supported = false;
    e.update(1, 5, 5);
    assertFalse(e.active());
    assertFalse(e.isSelected());
    assertEquals(0, s.viewmodel.off.rotateX, .001);
    assertEquals(0, s.viewmodel.main.rotateX, .001);
    assertFalse(s.viewmodel.enabled);
  }
  private static final class ProjectedActions implements UiActions {
    final Settings settings;
    long revision;
    final PickMask mask;
    int picks, checkpoints;
    boolean alphaAvailable = true, projectionAvailable = true, supported = true;
    ProjectedActions(Settings settings) {
      this.settings = settings;
      ByteBuffer rgba = ByteBuffer.allocate(4);
      rgba.put(3, (byte) 255);
      mask = PickMask.fromRgba(rgba, 1, 1, 800, 600, false);
    }
    ViewmodelProjection projection() {
      Settings.HandTransform h = settings.viewmodel.main;
      return new ViewmodelProjection(
          new float[] {1.1f, 0, 0, 0, 0, 1.5f, 0, 0, 0, 0, -1.01f, -1, 0, 0, -.1005f, 0}, 800, 600,
          .42f, -.25f, -1, h.x, h.y, h.z, h.rotateX, h.rotateY, h.rotateZ);
    }
    public ViewmodelSelection viewmodelSelection(float x, float y) {
      return new ViewmodelSelection(alphaAvailable ? "main" : "", alphaAvailable ? mask : null,
          revision, alphaAvailable ? mask : null, null, projectionAvailable ? projection() : null,
          null);
    }
    public void requestViewmodelPick(float x, float y) {
      picks++;
      revision++;
    }
    public void setViewmodelEditing(boolean value) {}
    public boolean viewmodelEditorSupported() {
      return supported;
    }
    public void checkpoint() {
      checkpoints++;
    }
    public void changed() {}
    public String clipboard() {
      return "";
    }
    public void clipboard(String v) {}
    public List<String> profiles() {
      return Collections.emptyList();
    }
    public void saveProfile(String v) {}
    public void loadProfile(String v) {}
    public void deleteProfile(String v) {}
    public String exportProfile() {
      return "{}";
    }
    public void importProfile(String v) {}
    public void reset() {}
    public void undo() {}
    public boolean canUndo() {
      return false;
    }
    public void close() {}
    public List<Cooldown> cooldowns() {
      return Collections.emptyList();
    }
    public String gameVersion() {
      return "test";
    }
    public String saveStatus() {
      return "";
    }
  }
  private static float[] ringPoint(ViewmodelProjection projection, int axis, double angle) {
    float radius = projection.radius(54);
    float[][] basis = projection.ring(axis);
    float u = (float) Math.cos(angle) * radius, v = (float) Math.sin(angle) * radius;
    return projection.project(basis[0][0] * u + basis[1][0] * v, basis[0][1] * u + basis[1][1] * v,
        basis[0][2] * u + basis[1][2] * v);
  }
  @Test
  void clickingAndDraggingProjectedRotationRingsRotatesTheActualXYZFields() {
    for (int axis = 0; axis < 3; axis++) {
      Settings s = new Settings();
      s.viewmodel.snap = false;
      s.viewmodel.main.rotateX = 27;
      s.viewmodel.main.rotateY = -31;
      s.viewmodel.main.rotateZ = 18;
      ProjectedActions a = new ProjectedActions(s);
      ViewmodelEditor e = new ViewmodelEditor(s, a);
      e.open();
      e.mouseDown(20, 20, 0, 0);
      e.update(0, 20, 20);
      assertTrue(e.isSelected());
      e.chooseMode("R");
      ViewmodelProjection projection = a.projection();
      float[] first = ringPoint(projection, axis, .63), second = ringPoint(projection, axis, 1.23);
      e.mouseDown(first[0], first[1], 0, 0);
      assertTrue(e.active());
      assertEquals(new String[] {"X", "Y", "Z"}[axis], e.activeAxis());
      e.mouseDrag(second[0], second[1], 0);
      float expected = (float) Math.toDegrees(.6);
      assertEquals(axis == 0 ? 27 + expected : 27, s.viewmodel.main.rotateX, .02);
      assertEquals(axis == 1 ? -31 + expected : -31, s.viewmodel.main.rotateY, .02);
      assertEquals(axis == 2 ? 18 + expected : 18, s.viewmodel.main.rotateZ, .02);
      e.mouseUp(0);
      assertFalse(e.active());
      assertEquals("R", e.mode());
    }
  }
  @Test
  void projectedAxisDragUsesRealWorldUnitsAndMouseReleaseConfirms() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    ViewmodelProjection p = a.projection();
    float radius = p.radius(54);
    float[] first = p.project(radius, 0, 0), second = p.project(radius + .1f, 0, 0);
    e.mouseDown(first[0], first[1], 0, 0);
    assertEquals("X", e.activeAxis());
    e.mouseDrag(second[0], second[1], 0);
    assertEquals(.1f, s.viewmodel.main.x, .0001);
    e.mouseUp(0);
    assertFalse(e.active());
    assertTrue(s.viewmodel.enabled);
    e.close();
    assertEquals(.1f, s.viewmodel.main.x, .0001);
  }
  @Test
  void rotateModeBodyDragIsTrackballAndResetRestoresAllSelectedTransforms() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    e.chooseMode("R");
    e.mouseDown(30, 30, 0, 0);
    e.mouseDrag(70, 50, 0);
    assertEquals(14, s.viewmodel.main.rotateX, .001);
    assertEquals(28, s.viewmodel.main.rotateY, .001);
    assertEquals(0, s.viewmodel.main.rotateZ, .001);
    e.mouseUp(0);
    e.resetSelectedTransforms();
    assertEquals(0, s.viewmodel.main.rotateX, .001);
    assertEquals(0, s.viewmodel.main.rotateY, .001);
    assertEquals(1, s.viewmodel.main.scaleX, .001);
    assertEquals(2, a.checkpoints);
  }
  @Test
  void scaleAxisUsesSignedProjectionAndIgnoresPerpendicularMouseMotion() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    e.chooseMode("S");
    ViewmodelProjection p = a.projection();
    float[] pivot = p.pivot(), end = p.project(p.radius(54), 0, 0);
    e.mouseDown(end[0], end[1], 0, 0);
    assertEquals("X", e.activeAxis());
    e.mouseDrag(end[0], end[1] + 150, 0);
    assertEquals(1, s.viewmodel.main.scaleX, .0001);
    assertEquals(1, s.viewmodel.main.scaleY, .0001);
    assertEquals(1, s.viewmodel.main.scaleZ, .0001);
    e.mouseDrag(pivot[0] - (end[0] - pivot[0]) * .5f, pivot[1], 0);
    assertEquals(.05f, s.viewmodel.main.scaleX, .0001,
        "Crossing the pivot clamps rather than growing again");
    e.mouseUp(0);
    assertFalse(e.active());
  }
  @Test
  void centralScaleHandleIsUniformAndHasPriorityOverOverlappingAxes() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    e.chooseMode("S");
    float[] pivot = a.projection().pivot();
    e.update(1, pivot[0], pivot[1]);
    assertEquals(true, e.presentation().get("hoverCenter"));
    e.mouseDown(pivot[0], pivot[1], 0, 0);
    assertEquals("", e.activeAxis());
    e.mouseDrag(pivot[0] + 25, pivot[1], 0);
    e.mouseUp(0);
    assertEquals(1.2f, s.viewmodel.main.scaleX, .0001);
    assertEquals(1.2f, s.viewmodel.main.scaleY, .0001);
    assertEquals(1.2f, s.viewmodel.main.scaleZ, .0001);
  }
  @Test
  void ctrlSnapsProjectedTranslationWhileShiftGivesFineMovement() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    ViewmodelProjection p = a.projection();
    float radius = p.radius(54);
    float[] first = p.project(radius, 0, 0), last = p.project(radius + .127f, 0, 0);
    e.mouseDown(first[0], first[1], 0, 0);
    e.mouseDrag(last[0], last[1], Keys.MOD_CONTROL);
    assertEquals(.15f, s.viewmodel.main.x, .0001);
    e.modifiers(Keys.MOD_SHIFT);
    assertEquals(.0127f, s.viewmodel.main.x, .0001);
    e.mouseUp(0);
  }
  @Test
  void altClickOnAnAxisResetsOnlyThatTransformComponent() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    s.viewmodel.main.x = .4f;
    s.viewmodel.main.y = .2f;
    s.viewmodel.main.rotateX = 25;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    ViewmodelProjection p = a.projection();
    float[] end = p.project(p.radius(54), 0, 0);
    assertTrue(e.mouseDown(end[0], end[1], 0, Keys.MOD_ALT));
    assertFalse(e.active());
    assertEquals(0, s.viewmodel.main.x, .0001);
    assertEquals(.2f, s.viewmodel.main.y, .0001);
    assertEquals(25, s.viewmodel.main.rotateX, .0001);
    assertEquals(1, a.checkpoints);
  }
  @Test
  void clearingHandSelectionCancelsItsGestureAndDoesNotInterceptHudKeyboard() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    Actions a = new Actions();
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(5, 5, 0, 0);
    e.update(0, 5, 5);
    e.key('G', 0);
    e.mouseDrag(55, 5, 0);
    assertNotEquals(0, s.viewmodel.main.x);
    e.clearSelection();
    assertFalse(e.isSelected());
    assertFalse(e.active());
    assertEquals(0, s.viewmodel.main.x, .0001);
    assertFalse(e.key('S', 0));
    assertEquals(false, e.presentation().get("visible"));
    e.mouseDown(5, 5, 0, 0);
    e.mouseUp(0);
    e.update(1, 5, 5);
    assertTrue(e.isSelected());
    assertFalse(e.active(), "An asynchronous pick after release selects without starting a drag");
  }
  @Test
  void hoveredAxisComesFromTheActualProjectedHandle() {
    Settings s = new Settings();
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    ViewmodelProjection p = a.projection();
    float[] end = p.project(p.radius(54), 0, 0);
    e.update(1, end[0], end[1]);
    assertEquals("X", e.presentation().get("hoverAxis"));
    List<?> rows = (List<?>) e.presentation().get("axes");
    boolean hovered = false;
    for (Object value : rows) {Map<?,?> row=(Map<?,?>)value;
      if ("X".equals(row.get("axis"))) hovered = Boolean.TRUE.equals(row.get("hovered"));
    }
    assertTrue(hovered);
  }
  @Test
  void transientAsynchronousAlphaAndProjectionGapsDoNotDiscardTheSelectedHandOrGesture() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.mouseDown(20, 20, 0, 0);
    e.update(0, 20, 20);
    assertEquals(true, e.presentation().get("visible"));
    e.key('G', 0);
    e.key('X', 0);
    e.mouseDrag(60, 20, 0);
    float moved = s.viewmodel.main.x;
    a.alphaAvailable = false;
    a.projectionAvailable = false;
    a.revision++;
    e.update(1, 60, 20);
    assertTrue(e.isSelected());
    assertTrue(e.active());
    assertEquals("main", e.selectedHand());
    assertEquals(true, e.presentation().get("visible"));
    assertEquals(moved, s.viewmodel.main.x, .0001);
    a.alphaAvailable = true;
    a.projectionAvailable = true;
    a.revision++;
    e.update(2, 60, 20);
    assertTrue(e.active());
    assertEquals(true, e.presentation().get("visible"));
    e.mouseUp(0);
    a.supported = false;
    e.update(3, 60, 20);
    assertFalse(e.isSelected());
    assertEquals(false, e.presentation().get("visible"));
  }
  @Test
  void blenderSignTogglePlusMinusSteppingNumpadAndCyrillicHotkeysWork() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    a.alphaAvailable = false;
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    assertTrue(e.ensureHandSelected(), "Projection alone is enough to select the main hand");
    assertTrue(e.isSelected());
    assertTrue(e.key('к', 0));
    assertTrue(e.key('ч', 0));
    e.key('9', 0);
    e.key('0', 0);
    assertEquals(90f, s.viewmodel.main.rotateX, .001f);
    e.key('-', 0);
    assertEquals(-90f, s.viewmodel.main.rotateX, .001f);
    e.key('-', 0);
    assertEquals(90f, s.viewmodel.main.rotateX, .001f);
    e.key(Keys.ENTER, 0);
    assertFalse(e.active());
    assertTrue(e.key('п', 0));
    assertTrue(e.key('X', 0));
    e.key('=', 0);
    assertEquals(.05f, s.viewmodel.main.x, .0001f);
    e.key(334, 0);
    assertEquals(.10f, s.viewmodel.main.x, .0001f);
    e.key(333, 0);
    assertEquals(.05f, s.viewmodel.main.x, .0001f);
    e.key(Keys.ENTER, 0);
    e.chooseAxis("Y");
    assertTrue(e.key('+', 0));
    assertEquals(.05f, s.viewmodel.main.y, .0001f);
    assertTrue(e.key('-', 0));
    assertEquals(0f, s.viewmodel.main.y, .0001f);
  }
  @Test
  void zAxisTranslationNearScreenCenterIsSmoothAndPlaneAndLocalConstraintsWork() {
    Settings s = new Settings();
    s.viewmodel.snap = false;
    ProjectedActions a = new ProjectedActions(s);
    ViewmodelEditor e = new ViewmodelEditor(s, a);
    e.open();
    e.ensureHandSelected();
    e.update(0, 400, 300);
    e.key('G', 0);
    e.key('Z', 0);
    e.mouseDrag(410, 290, 0);
    assertTrue(Math.abs(s.viewmodel.main.z) > .001f && Math.abs(s.viewmodel.main.z) < .5f,
        "Z-axis drag near screen center must not explode at the vanishing point: "
            + s.viewmodel.main.z);
    e.key(Keys.ESCAPE, 0);
    e.key('G', 0);
    e.key('Z', Keys.MOD_SHIFT);
    assertEquals("XY", e.activeAxis());
    e.mouseDrag(440, 270, 0);
    assertNotEquals(0f, s.viewmodel.main.x);
    assertNotEquals(0f, s.viewmodel.main.y);
    assertEquals(0f, s.viewmodel.main.z, .0001f);
    e.key(Keys.ESCAPE, 0);
    s.viewmodel.main.rotateY = 90f;
    e.update(1, 400, 300);
    e.key('G', 0);
    e.key('X', 0);
    e.key('X', 0);
    e.key('1', 0);
    e.key(Keys.ENTER, 0);
    assertEquals(0f, s.viewmodel.main.x, .001f);
    assertEquals(-1f, s.viewmodel.main.z, .001f);
  }
}
