package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Keys;
import dev.nightclient.api.ViewmodelProjection;
import dev.nightclient.api.ViewmodelSelection;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.skin.SkinView;
import java.util.Map;

public final class ViewmodelEditor {
  private static final String[] AXES = {"X", "Y", "Z"};
  private static final float[] AXIS_X = {1, 0, 0}, AXIS_Y = {0, 1, 0}, AXIS_Z = {0, 0, 1};
  private static final int MAX_RING_SEGMENTS = 128;
  private final Settings settings;
  private final UiActions actions;
  private ViewmodelSelection selection = ViewmodelSelection.NONE;
  private ViewmodelProjection gestureProjection;
  private Settings.HandTransform before;
  private String operation = "", axis = "", number = "", mode = "G", chosenAxis = "",
                 hoverAxis = "";
  private float startX, startY, pointerX, pointerY, previousRingAngle, ringDelta;
  private int modifiers, lastAppliedModifiers = Integer.MIN_VALUE, stepCount;
  private float lastAppliedX = Float.NaN, lastAppliedY = Float.NaN;
  private String lastAppliedOperation = "", lastAppliedAxis = "", lastAppliedNumber = "";
  private boolean opened, pending, previousEnabled, mouseGesture, hoverCenter, localAxis,
                  lastAppliedLocal;
  private long pendingRevision;
  private SkinDocument document;
  private SkinView legacyView;
  private Map<String, Object> snapshot;
  private double time;

  public ViewmodelEditor(Settings settings, UiActions actions) {
    this.settings = settings;
    this.actions = actions;
    presentation(settings.skinDocument == null
            ? DefaultSkin.resolve(
                  settings.skin.preset, settings.skin.enabled ? actions.skinDocument() : null)
            : settings.skinDocument);
  }

  public void presentation(SkinDocument document) {
    if (document == null)
      throw new IllegalArgumentException("Viewmodel presentation requires a NC document");
    this.document = document;
    legacyView = null;
    snapshot = null;
  }

  private float behavior(String key) {
    SkinDocument.Node node = document.component("editor.viewmodel-behavior");
    String value = node == null ? null : document.styles(node, "").get("--" + key);
    if (value == null) return 0;
    try {
      float parsed = Float.parseFloat(value.replace("px", ""));
      return Float.isFinite(parsed) ? parsed : 0;
    } catch (NumberFormatException invalid) {
      return 0;
    }
  }

  public void open() {
    if (opened) return;
    opened = true;
    selection = ViewmodelSelection.NONE;
    pending = false;
    snapshot = null;
    resetPresentation();
    actions.setViewmodelEditing(true);
  }

  public void close() {
    if (!opened) return;
    if (active()) cancel();
    opened = false;
    pending = false;
    selection = ViewmodelSelection.NONE;
    snapshot = null;
    resetPresentation();
    actions.setViewmodelEditing(false);
  }

  private void resetPresentation() {
    if (legacyView != null) {
      legacyView.resetInteraction();
      legacyView.resetMotion();
    }
  }

  public boolean isSelected() {
    return selection.selected();
  }

  public boolean ensureHandSelected() {
    if (selection.selected()) return true;
    if (!opened || !actions.viewmodelEditorSupported()) return false;
    ViewmodelSelection current = actions.viewmodelSelection(pointerX, pointerY);
    if (current == null) return false;
    if (current.selected()) {
      selection = current;
      pending = false;
      snapshot = null;
      return true;
    }
    if (current.mainProjection != null
        || (current.mainMask != null && !current.mainMask.isEmpty())) {
      dev.nightclient.api.PickMask mask = current.mainMask != null && !current.mainMask.isEmpty()
          ? current.mainMask
          : syntheticMask(current.mainProjection);
      selection = new ViewmodelSelection("main", mask, current.revision, mask, current.offMask,
          current.mainProjection, current.offProjection);
      pending = false;
      snapshot = null;
      return true;
    }
    if (current.offProjection != null || (current.offMask != null && !current.offMask.isEmpty())) {
      dev.nightclient.api.PickMask mask = current.offMask != null && !current.offMask.isEmpty()
          ? current.offMask
          : syntheticMask(current.offProjection);
      selection = new ViewmodelSelection("off", mask, current.revision, current.mainMask, mask,
          current.mainProjection, current.offProjection);
      pending = false;
      snapshot = null;
      return true;
    }
    return false;
  }

  private static dev.nightclient.api.PickMask syntheticMask(ViewmodelProjection projection) {
    java.nio.ByteBuffer rgba = java.nio.ByteBuffer.allocate(4);
    rgba.put(3, (byte) 255);
    int w = projection == null ? 800 : Math.max(1, projection.width),
        h = projection == null ? 600 : Math.max(1, projection.height);
    return dev.nightclient.api.PickMask.fromRgba(rgba, 1, 1, w, h, false);
  }

  public void clearSelection() {
    if (active()) cancel();
    pending = false;
    selection = ViewmodelSelection.NONE;
    hoverAxis = "";
    hoverCenter = false;
    localAxis = false;
    stepCount = 0;
    snapshot = null;
  }

  public boolean active() {
    return !operation.isEmpty();
  }

  public String selectedHand() {
    return selection.hand;
  }

  public String mode() {
    return active() ? operation : mode;
  }

  public String activeAxis() {
    return active() ? axis : chosenAxis;
  }

  public void chooseMode(String value) {
    if (!"G".equals(value) && !"R".equals(value) && !"S".equals(value)) return;
    if (active()) cancel();
    mode = value;
    chosenAxis = "";
    localAxis = false;
    stepCount = 0;
    snapshot = null;
  }

  public void chooseAxis(String value) {
    if (!"".equals(value) && !"X".equals(value) && !"Y".equals(value) && !"Z".equals(value)) return;
    localAxis = false;
    if (active()) {
      axis = axis.equals(value) ? "" : value;
      resetRing();
      apply();
    } else
      chosenAxis = chosenAxis.equals(value) ? "" : value;
    snapshot = null;
  }

  public void selectHand(String value) {
    String target = "off".equals(value) ? "off" : "main";
    if (active()) cancel();
    ViewmodelSelection current =
        opened && actions.viewmodelEditorSupported() ? actions.viewmodelSelection(pointerX, pointerY)
                                                     : null;
    boolean off = "off".equals(target);
    ViewmodelProjection projection = current == null
        ? null
        : (off ? current.offProjection : current.mainProjection);
    dev.nightclient.api.PickMask mask = current == null
        ? null
        : (off ? current.offMask : current.mainMask);
    if (mask == null || mask.isEmpty()) mask = syntheticMask(projection);
    long revision = current == null ? 0 : current.revision;
    selection = new ViewmodelSelection(target, mask, revision,
        off ? (current == null ? null : current.mainMask) : mask,
        off ? mask : (current == null ? null : current.offMask),
        off ? (current == null ? null : current.mainProjection) : projection,
        off ? projection : (current == null ? null : current.offProjection));
    pending = false;
    snapshot = null;
  }

  public void resetSelectedTransforms() {
    if (active()) cancel();
    actions.checkpoint();
    resetHandTransform(hand());
    changed();
    snapshot = null;
    if (selection.selected())
      actions.requestViewmodelPick(selection.pivotX, selection.pivotY);
    else
      actions.requestViewmodelPick(pointerX, pointerY);
  }

  public void resetAllHands() {
    if (active()) cancel();
    actions.checkpoint();
    resetHandTransform(settings.viewmodel.main);
    resetHandTransform(settings.viewmodel.off);
    settings.viewmodel.main.enabled = false;
    settings.viewmodel.off.enabled = false;
    settings.viewmodel.enabled = false;
    changed();
    snapshot = null;
    actions.requestViewmodelPick(pointerX, pointerY);
  }

  private static void resetHandTransform(Settings.HandTransform h) {
    h.x = h.y = h.z = h.rotateX = h.rotateY = h.rotateZ = 0;
    h.scaleX = h.scaleY = h.scaleZ = 1;
  }

  public void update(double now, float mouseX, float mouseY) {
    update(null, now, mouseX, mouseY);
  }

  public void update(SkinView hostView, double now, float mouseX, float mouseY) {
    pointerX = mouseX;
    pointerY = mouseY;
    time = now;
    snapshot = null;
    if (!opened) return;

    if (hostView == null) compatibilityView().beginFrame(mouseX, mouseY, now);
    actions.hoverViewmodel(mouseX, mouseY);
    if (!actions.viewmodelEditorSupported()) {
      clearSelection();
      return;
    }
    if (pending) {
      ViewmodelSelection current = actions.viewmodelSelection(startX, startY);
      if (current != null && current.revision > pendingRevision) {
        selection = current;
        pending = false;
      }
    }
    if (!pending && selection.selected()) {
      ViewmodelSelection current = actions.viewmodelSelection(mouseX, mouseY);

      if (current != null && current.revision >= selection.revision)
        selection = refreshSelectedHand(current);
    }
    if (active()) apply();
    int hovered = !pending && !active() && selection.selected() ? gizmoHit(mouseX, mouseY) : -1;
    hoverAxis = hovered >= 0 && hovered < 3 ? AXES[hovered] : "";
    hoverCenter = hovered == 3;
  }

  public java.util.Map<String, Object> presentation() {
    if (snapshot != null) return snapshot;
    java.util.Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
    data.put("opened", opened);
    data.put("visible", false);
    data.put("mode", mode());
    data.put("active", active());
    data.put("axis", activeAxis());
    data.put("number", number);
    data.put("hoverAxis", hoverAxis);
    data.put("hoverCenter", hoverCenter);
    java.util.List<java.util.Map<String, Object>>
        segments = new java.util.ArrayList<java.util.Map<String, Object>>(),
        axes = new java.util.ArrayList<java.util.Map<String, Object>>(),
        labels = new java.util.ArrayList<java.util.Map<String, Object>>();
    data.put("segments", segments);
    data.put("axes", axes);
    data.put("labels", labels);
    if (!opened || !selection.selected() || selection.projection == null)
      return snapshot = java.util.Collections.unmodifiableMap(data);
    ViewmodelProjection projection = selection.projection;
    float[] pivot = projection.pivot();
    if (pivot == null) return snapshot = java.util.Collections.unmodifiableMap(data);
    data.put("visible", true);
    data.put("projection", projection);
    data.put("radiusPixels", behavior("gizmo-radius"));
    java.util.Map<String, Object> origin = new java.util.LinkedHashMap<String, Object>();
    origin.put("x", pivot[0]);
    origin.put("y", pivot[1]);
    origin.put("depth", pivot[2]);
    data.put("pivot", origin);
    float radius = projection.radius(behavior("gizmo-radius"));
    int steps = Math.max(8, Math.min(128, Math.round(behavior("ring-segments"))));
    for (int i = 0; i < 3; i++) {
      boolean selected = activeAxis().equals(AXES[i]);
      if ("R".equals(mode())) {
        float[][] basis = projection.ring(i);
        float[] prior = null;
        for (int k = 0; k <= steps; k++) {
          float[] point = ringPoint(projection, basis, radius, k * Math.PI * 2 / steps);
          if (prior != null && point != null) {
            java.util.Map<String, Object> row = axisRow(i, selected);
            row.put("behind", point[2] > pivot[2]);
            row.put("x1", prior[0]);
            row.put("y1", prior[1]);
            row.put("x2", point[0]);
            row.put("y2", point[1]);
            segments.add(row);
          }
          prior = point;
        }
        float[] label =
            ringPoint(projection, basis, radius, Math.toRadians(behavior("ring-label-angle")));
        if (label != null) {
          java.util.Map<String, Object> row = axisRow(i, selected);
          row.put("x", label[0]);
          row.put("y", label[1]);
          labels.add(row);
        }
      } else {
        float[] direction = projection.axis(mode(), i),
                end = projection.project(
                    direction[0] * radius, direction[1] * radius, direction[2] * radius);
        if (end == null) continue;
        float length = (float) Math.hypot(end[0] - pivot[0], end[1] - pivot[1]);
        if (length < behavior("axis-min-length")) continue;
        java.util.Map<String, Object> line = axisRow(i, selected);
        line.put("behind", false);
        line.put("x1", pivot[0]);
        line.put("y1", pivot[1]);
        line.put("x2", end[0]);
        line.put("y2", end[1]);
        segments.add(line);
        float unitX = (end[0] - pivot[0]) / length, unitY = (end[1] - pivot[1]) / length;
        java.util.Map<String, Object> row = axisRow(i, selected);
        row.put("x", end[0]);
        row.put("y", end[1]);
        row.put("unitX", unitX);
        row.put("unitY", unitY);
        axes.add(row);
        java.util.Map<String, Object> label = axisRow(i, selected);
        label.put("x", end[0] + unitX * 9f);
        label.put("y", end[1] + unitY * 9f);
        labels.add(label);
      }
    }
    return snapshot = java.util.Collections.unmodifiableMap(data);
  }

  private java.util.Map<String, Object> axisRow(int index, boolean selected) {
    java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
    row.put("axis", AXES[index]);
    row.put("selected", selected);
    row.put("hovered", AXES[index].equals(hoverAxis));
    return row;
  }

  private dev.nightclient.ui.skin.SkinView compatibilityView() {
    if (legacyView == null)
      legacyView = new dev.nightclient.ui.skin.SkinView(document == null
              ? dev.nightclient.skin.DefaultSkin.resolve(
                    settings.skin.preset, settings.skinDocument)
              : document,
          settings, actions);
    return legacyView;
  }

  public void renderOutline(Canvas canvas, int width, int height) {
    renderOutline(null, canvas, width, height);
  }

  public void renderOutline(SkinView hostView, Canvas canvas, int width, int height) {
    SkinView target = hostView != null ? hostView : compatibilityView();
    target.renderComponent("editor.viewmodel-outline", canvas, 0, 0, width, height,
        settings.theme.effects.time,
        java.util.Collections.<String, Object>singletonMap("viewmodelGizmo", presentation()));
  }

  public void render(Canvas canvas, int width, int height, double now) {
    render(null, canvas, width, height, now);
  }

  public void render(SkinView hostView, Canvas canvas, int width, int height, double now) {
    SkinView target = hostView != null ? hostView : compatibilityView();
    target.renderComponent("editor.viewmodel-gizmo", canvas, 0, 0, width, height, now,
        java.util.Collections.<String, Object>singletonMap("viewmodelGizmo", presentation()));
  }

  private ViewmodelSelection refreshSelectedHand(ViewmodelSelection current) {
    boolean off = "off".equals(selection.hand);
    dev.nightclient.api.PickMask mask = off ? current.offMask : current.mainMask;
    ViewmodelProjection projection = off ? current.offProjection : current.mainProjection;
    if (mask == null || mask.isEmpty()) mask = selection.mask;
    if (projection == null || projection.pivot() == null) projection = selection.projection;
    return new ViewmodelSelection(selection.hand, mask, current.revision,
        off ? current.mainMask : mask, off ? mask : current.offMask,
        off ? current.mainProjection : projection, off ? projection : current.offProjection);
  }

  public boolean mouseDown(float x, float y, int button, int mods) {
    if (!opened || !actions.viewmodelEditorSupported()) return false;
    pointerX = x;
    pointerY = y;
    modifiers = mods;
    if (active()) {
      if (button == 1)
        cancel();
      else if (button == 0)
        confirm();
      return true;
    }
    if (button != 0) return false;
    int hit = gizmoHit(x, y);
    if (hit >= 0) {
      String pickedAxis = hit < 3 ? AXES[hit] : "";
      if ((mods & Keys.MOD_ALT) != 0)
        resetTransform(mode, pickedAxis);
      else
        begin(mode, pickedAxis, x, y, true);
      return true;
    }
    ViewmodelSelection prior = actions.viewmodelSelection(x, y);
    if (selection.selected() && prior != null && selection.hand.equals(prior.hand)) {
      begin(mode, chosenAxis, x, y, true);
      return true;
    }
    startX = x;
    startY = y;
    pendingRevision = prior == null ? 0 : prior.revision;
    pending = true;
    actions.requestViewmodelPick(x, y);
    return true;
  }

  public boolean mouseDrag(float x, float y, int mods) {
    if (!active()) return false;
    pointerX = x;
    pointerY = y;
    modifiers = mods;
    apply();
    return true;
  }

  public boolean mouseUp(int button) {
    if (!active()) return false;
    if (button == 0 && mouseGesture) confirm();
    return true;
  }

  private int gizmoHit(float x, float y) {
    if (!selection.selected() || selection.projection == null) return -1;
    ViewmodelProjection projection = selection.projection;
    float[] pivot = projection.pivot();
    if (pivot == null) return -1;

    if (Math.hypot(x - pivot[0], y - pivot[1]) <= behavior("center-hit-radius")) return 3;
    float radius = projection.radius(behavior("gizmo-radius")), best = behavior("hit-distance");
    int hit = -1;
    for (int i = 0; i < 3; i++) {
      float distance = Float.POSITIVE_INFINITY;
      if ("R".equals(mode)) {
        float[][] basis = projection.ring(i);
        float[] prior = null;
        for (int k = 0,
                 steps = Math.max(
                     8, Math.min(MAX_RING_SEGMENTS, Math.round(behavior("ring-segments"))));
            k <= steps; k++) {
          float[] p = ringPoint(projection, basis, radius, k * Math.PI * 2 / steps);
          if (prior != null && p != null)
            distance = Math.min(distance, distanceToSegment(x, y, prior[0], prior[1], p[0], p[1]));
          prior = p;
        }
      } else {
        float[] d = projection.axis(mode, i),
                p = projection.project(d[0] * radius, d[1] * radius, d[2] * radius);
        if (p != null && Math.hypot(p[0] - pivot[0], p[1] - pivot[1]) > behavior("axis-min-length"))
          distance = distanceToSegment(x, y, pivot[0], pivot[1], p[0], p[1]);
      }
      if (distance < best) {
        best = distance;
        hit = i;
      }
    }
    return hit;
  }

  public void modifiers(int value) {
    modifiers = value;
    if (active()) apply();
  }

  public boolean mouseScrolled(double amount, int modifiers) {
    if (!opened || active() || !selection.selected() || !actions.viewmodelEditorSupported()
        || (modifiers & Keys.MOD_CONTROL) == 0 || !Double.isFinite(amount) || amount == 0)
      return false;

    Settings.HandTransform hand = hand();
    double factor = Math.pow(1.0 + stepSize("S", modifiers), amount);
    float x = wheelScale(hand.scaleX, factor);
    float y = wheelScale(hand.scaleY, factor);
    float z = wheelScale(hand.scaleZ, factor);
    if (x == hand.scaleX && y == hand.scaleY && z == hand.scaleZ) return true;

    actions.checkpoint();
    hand.scaleX = x;
    hand.scaleY = y;
    hand.scaleZ = z;
    settings.viewmodel.enabled = true;
    hand.enabled = true;
    changed();
    snapshot = null;
    actions.requestViewmodelPick(selection.pivotX, selection.pivotY);
    return true;
  }

  private static float wheelScale(float scale, double factor) {
    return (float) Math.max(.05, Math.min(8.0, scale * factor));
  }

  private static int normalizeKey(int key) {
    if (key >= 'a' && key <= 'z') return key - 32;
    if (key >= 320 && key <= 329) return '0' + (key - 320);
    if (key == 330 || key == ',') return '.';
    if (key == 333) return '-';
    if (key == 334 || key == '=') return '+';
    if (key == 335) return Keys.ENTER;
    if (key == 'п' || key == 'П') return 'G';
    if (key == 'к' || key == 'К') return 'R';
    if (key == 'ы' || key == 'Ы') return 'S';
    if (key == 'ч' || key == 'Ч') return 'X';
    if (key == 'н' || key == 'Н') return 'Y';
    if (key == 'я' || key == 'Я') return 'Z';
    return key;
  }

  public static boolean isHotkey(int rawKey) {
    int key = normalizeKey(rawKey);
    return key == 'G' || key == 'R' || key == 'S' || key == 'X' || key == 'Y' || key == 'Z'
        || key == '+' || key == '-';
  }

  private float stepSize(String op, int mods) {
    boolean fine = (mods & Keys.MOD_SHIFT) != 0;
    boolean snap = !fine && (settings.viewmodel.snap || (mods & Keys.MOD_CONTROL) != 0);
    if ("G".equals(op)) {
      float base = snap ? Math.max(.01f, settings.viewmodel.translationSnap) : .05f;
      return fine ? base * .2f : base;
    }
    if ("R".equals(op)) {
      float base = snap ? Math.max(1f, settings.viewmodel.rotationSnap) : 5f;
      return fine ? 1f : base;
    }
    float base = snap ? Math.max(.01f, settings.viewmodel.scaleSnap) : .05f;
    return fine ? .01f : base;
  }

  private static String formatStep(float value) {
    float rounded = Math.round(value * 1000f) / 1000f;
    if (Math.abs(rounded - Math.round(rounded)) < .0001f)
      return Integer.toString(Math.round(rounded));
    String text = Float.toString(rounded);
    while (text.contains(".") && (text.endsWith("0") || text.endsWith(".")))
      text = text.substring(0, text.length() - 1);
    return text;
  }

  private static boolean hasRotation(Settings.HandTransform h) {
    return h != null
        && (Math.abs(h.rotateX) > .01f || Math.abs(h.rotateY) > .01f
            || Math.abs(h.rotateZ) > .01f);
  }

  public boolean key(int rawKey, int mods) {
    if (!opened) return false;
    modifiers = mods;
    int key = normalizeKey(rawKey);
    if (pending && !selection.selected()) {
      ViewmodelSelection current = actions.viewmodelSelection(startX, startY);
      if (current != null && current.selected()) {
        selection = current;
        pending = false;
      }
    }
    if (active()) {
      if (key == Keys.ESCAPE) {
        cancel();
        return true;
      }
      if (key == Keys.ENTER || key == Keys.SPACE) {
        confirm();
        return true;
      }
      if (key == 'X' || key == 'Y' || key == 'Z') {
        String picked = String.valueOf((char) key);
        if ((mods & Keys.MOD_SHIFT) != 0 && !"R".equals(operation)) {
          String plane = "X".equals(picked) ? "YZ" : "Y".equals(picked) ? "XZ" : "XY";
          axis = axis.equals(plane) ? "" : plane;
          localAxis = false;
        } else if (!axis.equals(picked)) {
          axis = picked;
          localAxis = false;
        } else if (!localAxis && "G".equals(operation) && hasRotation(before)) {
          localAxis = true;
        } else {
          axis = "";
          localAxis = false;
        }
        resetRing();
        apply();
        return true;
      }
      if (key == Keys.BACKSPACE || key == Keys.DELETE) {
        stepCount = 0;
        if (key == Keys.DELETE)
          number = "";
        else if (!number.isEmpty())
          number = number.substring(0, number.length() - 1);
        apply();
        return true;
      }
      if ((key >= '0' && key <= '9') || key == '.') {
        if (stepCount != 0) {
          number = "";
          stepCount = 0;
        }
        if (key == '.' && number.contains(".")) return true;
        if (number.length() < 16) number += (char) key;
        apply();
        return true;
      }
      if (key == '+' || key == Keys.UP || key == Keys.RIGHT) {
        float step = stepSize(operation, mods);
        float base = "S".equals(operation) ? 1f : 0f;
        try {
          if (!number.isEmpty() && !"-".equals(number)) base = Float.parseFloat(number);
        } catch (NumberFormatException ignored) {
        }
        stepCount++;
        number = formatStep(base + step);
        apply();
        return true;
      }
      if (key == '-' || key == Keys.DOWN || key == Keys.LEFT) {
        float step = stepSize(operation, mods);
        if (key == Keys.DOWN || key == Keys.LEFT || stepCount != 0) {
          float base = "S".equals(operation) ? 1f : 0f;
          try {
            if (!number.isEmpty() && !"-".equals(number))
              base = Float.parseFloat(number);
            else if ("-".equals(number))
              base = ("S".equals(operation) ? 1f : 0f) - step;
          } catch (NumberFormatException ignored) {
          }
          stepCount--;
          number = formatStep(base - step);
        } else if (number.isEmpty()) {
          number = "-";
        } else if ("-".equals(number)) {
          stepCount = -2;
          number = formatStep(("S".equals(operation) ? 1f : 0f) - 2f * step);
        } else {
          number = number.startsWith("-") ? number.substring(1) : "-" + number;
        }
        apply();
        return true;
      }
      if (key == 'G' || key == 'R' || key == 'S') {
        String next = String.valueOf((char) key);
        operation = mode = next;
        axis = "";
        localAxis = false;
        number = "";
        stepCount = 0;
        resetRing();
        apply();
        return true;
      }
      return true;
    }
    if (!selection.selected()) return false;
    if (key == 'G' || key == 'R' || key == 'S') {
      if ((mods & Keys.MOD_ALT) != 0) {
        resetTransform(String.valueOf((char) key), chosenAxis);
        return true;
      }
      mode = String.valueOf((char) key);
      begin(mode, chosenAxis, pointerX, pointerY, false);
      return true;
    }
    if (key == 'X' || key == 'Y' || key == 'Z') {
      String picked = String.valueOf((char) key);
      if ((mods & Keys.MOD_ALT) != 0) {
        resetTransform(mode, picked);
      } else {
        chooseAxis(picked);
      }
      return true;
    }
    if (key == '+' || key == '-' || key == Keys.UP || key == Keys.DOWN || key == Keys.LEFT
        || key == Keys.RIGHT) {
      float sign = (key == '-' || key == Keys.DOWN || key == Keys.LEFT) ? -1f : 1f;
      String targetAxis = !chosenAxis.isEmpty() ? chosenAxis
          : !hoverAxis.isEmpty()                ? hoverAxis
          : (key == Keys.UP || key == Keys.DOWN)    ? "Y"
          : (key == Keys.LEFT || key == Keys.RIGHT) ? "X"
          : "S".equals(mode)                        ? ""
                                                    : "X";
      nudgeTransform(mode, targetAxis, sign * stepSize(mode, mods));
      return true;
    }
    return false;
  }

  private void nudgeTransform(String op, String targetAxis, float delta) {
    actions.checkpoint();
    Settings.HandTransform h = hand();
    boolean x = targetAxis.isEmpty() || targetAxis.contains("X"),
            y = targetAxis.isEmpty() || targetAxis.contains("Y"),
            z = targetAxis.isEmpty() || targetAxis.contains("Z");
    if ("G".equals(op)) {
      if (x) h.x += delta;
      if (y && !targetAxis.isEmpty()) h.y += delta;
      if (z && !targetAxis.isEmpty()) h.z += delta;
    } else if ("R".equals(op)) {
      if (x) h.rotateX += delta;
      if (y && !targetAxis.isEmpty()) h.rotateY += delta;
      if (z && !targetAxis.isEmpty()) h.rotateZ += delta;
    } else {
      if (x) h.scaleX = Math.max(.05f, h.scaleX + delta);
      if (y) h.scaleY = Math.max(.05f, h.scaleY + delta);
      if (z) h.scaleZ = Math.max(.05f, h.scaleZ + delta);
    }
    settings.viewmodel.enabled = true;
    h.enabled = true;
    changed();
    snapshot = null;
    actions.requestViewmodelPick(selection.pivotX, selection.pivotY);
  }

  private void resetTransform(String op, String selectedAxis) {
    actions.checkpoint();
    Settings.HandTransform h = hand();
    boolean x = selectedAxis.isEmpty() || "X".equals(selectedAxis),
            y = selectedAxis.isEmpty() || "Y".equals(selectedAxis),
            z = selectedAxis.isEmpty() || "Z".equals(selectedAxis);
    if ("G".equals(op)) {
      if (x) h.x = 0;
      if (y) h.y = 0;
      if (z) h.z = 0;
    } else if ("R".equals(op)) {
      if (x) h.rotateX = 0;
      if (y) h.rotateY = 0;
      if (z) h.rotateZ = 0;
    } else {
      if (x) h.scaleX = 1;
      if (y) h.scaleY = 1;
      if (z) h.scaleZ = 1;
    }
    changed();
    snapshot = null;
    actions.requestViewmodelPick(selection.pivotX, selection.pivotY);
  }

  private void begin(String op, String selectedAxis, float x, float y, boolean mouse) {
    lastAppliedX = lastAppliedY = Float.NaN;
    lastAppliedModifiers = Integer.MIN_VALUE;
    lastAppliedLocal = false;
    actions.checkpoint();
    before = copy(hand());
    previousEnabled = settings.viewmodel.enabled;
    gestureProjection = selection.projection;
    operation = mode = op;
    axis = selectedAxis;
    localAxis = false;
    number = "";
    stepCount = 0;
    startX = x;
    startY = y;
    pointerX = x;
    pointerY = y;
    mouseGesture = mouse;
    resetRing();
  }

  private Settings.HandTransform hand() {
    return "off".equals(selection.hand) ? settings.viewmodel.off : settings.viewmodel.main;
  }

  private void resetRing() {
    previousRingAngle = gestureProjection == null || axis.length() != 1
        ? Float.NaN
        : gestureProjection.ringAngle(axisIndex(), startX, startY);
    ringDelta = 0;
  }

  private int axisIndex() {
    return "X".equals(axis) ? 0 : "Y".equals(axis) ? 1 : 2;
  }

  private float screenAxisDelta(ViewmodelProjection p, float[] d, float dx, float dy) {
    float radius = p.radius(Math.max(24f, behavior("gizmo-radius")));
    float[] pivot = p.pivot(),
            end = p.project(d[0] * radius, d[1] * radius, d[2] * radius);
    if (pivot != null && end != null) {
      float ux = end[0] - pivot[0], uy = end[1] - pivot[1], len2 = ux * ux + uy * uy;
      if (len2 > 1f) {
        float denom = Math.max(len2, 18f * 18f);
        return ((dx * ux + dy * uy) / denom) * radius;
      }
    }
    return (dx - dy) * .0028f;
  }

  private void apply() {
    if (before == null) return;
    if (pointerX == lastAppliedX && pointerY == lastAppliedY && modifiers == lastAppliedModifiers
        && operation.equals(lastAppliedOperation) && axis.equals(lastAppliedAxis)
        && localAxis == lastAppliedLocal && number.equals(lastAppliedNumber))
      return;
    boolean pointerChanged = pointerX != lastAppliedX || pointerY != lastAppliedY;
    lastAppliedX = pointerX;
    lastAppliedY = pointerY;
    lastAppliedModifiers = modifiers;
    lastAppliedOperation = operation;
    lastAppliedAxis = axis;
    lastAppliedLocal = localAxis;
    lastAppliedNumber = number;
    Settings.HandTransform h = hand();
    copyTo(before, h);
    float dx = pointerX - startX, dy = pointerY - startY;
    boolean fine = (modifiers & Keys.MOD_SHIFT) != 0,
            snap = !fine && (settings.viewmodel.snap || (modifiers & Keys.MOD_CONTROL) != 0);
    float factor = fine ? .1f : 1;
    Float numeric = null;
    try {
      if ("-".equals(number))
        numeric = ("S".equals(operation) ? 1f : 0f) - stepSize(operation, modifiers);
      else if (!number.isEmpty())
        numeric = Float.parseFloat(number);
    } catch (NumberFormatException ignored) {
    }
    if ("G".equals(operation)) {
      float x = dx * .004f * factor, y = -dy * .004f * factor, z = (dx - dy) * .0028f * factor;
      if (gestureProjection != null) {
        if (axis.isEmpty() || "XY".equals(axis)) {
          float[] start = gestureProjection.planeMovement(startX, startY),
                  current = gestureProjection.planeMovement(pointerX, pointerY);
          if (start != null && current != null) {
            x = (current[0] - start[0]) * factor;
            y = (current[1] - start[1]) * factor;
          }
        } else if ("XZ".equals(axis) || "YZ".equals(axis)) {
          x = screenAxisDelta(gestureProjection, AXIS_X, dx, dy) * factor;
          y = screenAxisDelta(gestureProjection, AXIS_Y, dx, dy) * factor;
          z = screenAxisDelta(gestureProjection, AXIS_Z, dx, dy) * factor;
        } else {
          int idx = axisIndex();
          float[] d = gestureProjection.axisVector(idx, localAxis);
          float delta;
          if (!localAxis && Math.abs(d[2]) < .2f) {
            float start = gestureProjection.axisPosition(idx, startX, startY),
                  current = gestureProjection.axisPosition(idx, pointerX, pointerY);
            delta = Float.isFinite(start) && Float.isFinite(current)
                ? (current - start) * factor
                : screenAxisDelta(gestureProjection, d, dx, dy) * factor;
          } else {
            delta = screenAxisDelta(gestureProjection, d, dx, dy) * factor;
          }
          x = y = z = delta;
        }
      }
      if (numeric != null) {
        x = y = z = numeric;
      }
      if (snap) {
        x = snap(x, settings.viewmodel.translationSnap);
        y = snap(y, settings.viewmodel.translationSnap);
        z = snap(z, settings.viewmodel.translationSnap);
      }
      if (localAxis && axis.length() == 1 && gestureProjection != null) {
        float[] d = gestureProjection.axisVector(axisIndex(), true);
        h.x = before.x + d[0] * x;
        h.y = before.y + d[1] * y;
        h.z = before.z + d[2] * z;
      } else {
        if (axis.isEmpty() || axis.contains("X")) h.x = before.x + x;
        if (axis.isEmpty() || axis.contains("Y")) h.y = before.y + y;
        if (axis.contains("Z")) h.z = before.z + z;
      }
    } else if ("R".equals(operation)) {
      float degrees = (dx - dy) * .7f * factor;
      if (gestureProjection != null && axis.length() == 1) {
        float angle = gestureProjection.ringAngle(axisIndex(), pointerX, pointerY);
        if (Float.isFinite(angle) && Float.isFinite(previousRingAngle)) {
          if (pointerChanged) {
            float delta = angle - previousRingAngle;
            while (delta > Math.PI) delta -= (float) (2 * Math.PI);
            while (delta < -Math.PI) delta += (float) (2 * Math.PI);
            ringDelta += delta;
            previousRingAngle = angle;
          }
          degrees = (float) Math.toDegrees(ringDelta) * factor;
        }
      }
      if (numeric != null) degrees = numeric;
      if (snap) degrees = snap(degrees, settings.viewmodel.rotationSnap);
      if (axis.isEmpty() && numeric == null) {
        float rx = dy * .7f * factor, ry = dx * .7f * factor;
        h.rotateX = before.rotateX + (snap ? snap(rx, settings.viewmodel.rotationSnap) : rx);
        h.rotateY = before.rotateY + (snap ? snap(ry, settings.viewmodel.rotationSnap) : ry);
      } else if (axis.contains("X") || axis.isEmpty())
        h.rotateX = before.rotateX + degrees;
      else if (axis.contains("Y"))
        h.rotateY = before.rotateY + degrees;
      else
        h.rotateZ = before.rotateZ + degrees;
    } else {
      float scale = numeric != null ? numeric : 1 + (dx - dy) * .008f * factor;
      if (numeric == null && gestureProjection != null) {
        if (axis.length() == 1) {
          float radius = gestureProjection.radius(behavior("gizmo-radius"));
          float[] direction = gestureProjection.axis("S", axisIndex()),
                  pivot = gestureProjection.pivot(),
                  end = gestureProjection.project(
                      direction[0] * radius, direction[1] * radius, direction[2] * radius);
          if (pivot != null && end != null) {
            float ux = end[0] - pivot[0], uy = end[1] - pivot[1], length2 = ux * ux + uy * uy;
            if (length2 > .001f) {
              float first = ((startX - pivot[0]) * ux + (startY - pivot[1]) * uy) / length2,
                    current = ((pointerX - pivot[0]) * ux + (pointerY - pivot[1]) * uy) / length2;
              if (mouseGesture && first > .08f)
                scale = 1 + (current / first - 1) * factor;
              else
                scale = 1 + (current - first) * factor;
            }
          }
        } else {
          float[] pivot = gestureProjection.pivot();
          if (pivot != null) {
            float first = (float) Math.hypot(startX - pivot[0], startY - pivot[1]);
            if (first > 8)
              scale = 1
                  + ((float) Math.hypot(pointerX - pivot[0], pointerY - pivot[1]) / first - 1)
                      * factor;
          }
        }
      }
      if (snap) scale = snap(scale, settings.viewmodel.scaleSnap);
      scale = Math.max(.05f, scale);
      if (axis.isEmpty() || axis.contains("X")) h.scaleX = before.scaleX * scale;
      if (axis.isEmpty() || axis.contains("Y")) h.scaleY = before.scaleY * scale;
      if (axis.isEmpty() || axis.contains("Z")) h.scaleZ = before.scaleZ * scale;
    }
    settings.viewmodel.enabled = true;
    h.enabled = true;
    settings.validate();
    actions.changedLive();
  }

  private void changed() {
    settings.validate();
    actions.changed();
  }

  private void confirm() {
    operation = "";
    axis = "";
    localAxis = false;
    number = "";
    stepCount = 0;
    before = null;
    gestureProjection = null;
    mouseGesture = false;
    pending = false;
    changed();
    if (selection.mask != null)
      actions.requestViewmodelPick(selection.mask.centerX, selection.mask.centerY);
  }

  private void cancel() {
    if (before != null) copyTo(before, hand());
    settings.viewmodel.enabled = previousEnabled;
    operation = "";
    axis = "";
    localAxis = false;
    number = "";
    stepCount = 0;
    before = null;
    gestureProjection = null;
    mouseGesture = false;
    changed();
  }

  private static float[] ringPoint(
      ViewmodelProjection projection, float[][] basis, float radius, double angle) {
    float u = (float) Math.cos(angle) * radius, v = (float) Math.sin(angle) * radius;
    return projection.project(basis[0][0] * u + basis[1][0] * v, basis[0][1] * u + basis[1][1] * v,
        basis[0][2] * u + basis[1][2] * v);
  }

  private static float snap(float v, float step) {
    return step > 0 ? Math.round(v / step) * step : v;
  }

  private static float distanceToSegment(
      float px, float py, float ax, float ay, float bx, float by) {
    float dx = bx - ax, dy = by - ay, size = dx * dx + dy * dy;
    if (size < .00001f) return (float) Math.hypot(px - ax, py - ay);
    float t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / size));
    return (float) Math.hypot(px - ax - t * dx, py - ay - t * dy);
  }

  private static Settings.HandTransform copy(Settings.HandTransform h) {
    Settings.HandTransform n = new Settings.HandTransform();
    copyTo(h, n);
    return n;
  }

  private static void copyTo(Settings.HandTransform a, Settings.HandTransform b) {
    b.enabled = a.enabled;
    b.x = a.x;
    b.y = a.y;
    b.z = a.z;
    b.rotateX = a.rotateX;
    b.rotateY = a.rotateY;
    b.rotateZ = a.rotateZ;
    b.scaleX = a.scaleX;
    b.scaleY = a.scaleY;
    b.scaleZ = a.scaleZ;
  }
}
