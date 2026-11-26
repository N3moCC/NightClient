package dev.nightclient.ui.skin;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PlayerSkin;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.UiActions;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

final class HudFrameCache {
  static final int MAX_COMMANDS = 4096;
  static long reflectionFallbackCount;

  private static final Pattern DYNAMIC = Pattern.compile(
      "(?<![A-Za-z0-9_.$])(?:time|ui|local|pointer|mouse|boxWidth|boxHeight|caret|random|"
      + "selectedModule|selectedHud|page|filteredModules|renderModules|playerModules|"
      + "noModules|categoryModules|skinStatus|canUndo|profiles|markersEmpty|"
      + "profilePending|waypointPending|waypointStandalone|currentWorldId|currentWorldLabel|"
      + "waypointInWorld|previewAuto|previewZoomLabel|previewBackPage|"
      + "previewHasCape|selectedLabel|selectedMetadata)(?![A-Za-z0-9_])");
  private static final Pattern RANDOM = Pattern.compile("Math\\s*\\.\\s*random|\\brandom\\s*\\(");
  private static final Pattern SELF_METHODS =
      Pattern.compile("Math\\s*\\.\\s*random|\\brandom\\s*\\(");
  private static final String[] STATES = {"", "hover", "focus", "hover focus", "active",
      "hover active", "focus active", "hover focus active"};
  private static final Map<Class<?>, Field[]> FIELDS =
      Collections.synchronizedMap(new HashMap<Class<?>, Field[]>());

  private DisplayList cached;
  private final Recorder recorder = new Recorder(this);

  static final class DisplayList {
    final int width, height, frames, commands;
    final float hudExitDuration;
    final long themeGeneration;
    final boolean crosshair;
    final Object metricsKey;
    final long signature;
    final byte[] ops;
    final int[] ints;
    final float[] floats;
    final Object[] objects;

    DisplayList(int width, int height, int frames, int commands, float hudExitDuration,
        long themeGeneration, boolean crosshair, Object metricsKey, long signature, byte[] ops,
        int[] ints, float[] floats, Object[] objects) {
      this.width = width;
      this.height = height;
      this.frames = frames;
      this.commands = commands;
      this.hudExitDuration = hudExitDuration;
      this.themeGeneration = themeGeneration;
      this.crosshair = crosshair;
      this.metricsKey = metricsKey;
      this.signature = signature;
      this.ops = ops;
      this.ints = ints;
      this.floats = floats;
      this.objects = objects;
    }
  }

  boolean matches(long signature, int width, int height, boolean crosshair, Object metricsKey,
      long themeGeneration) {
    DisplayList list = cached;
    return list != null && list.signature == signature && list.width == width
        && list.height == height && list.crosshair == crosshair && list.metricsKey == metricsKey
        && list.themeGeneration == themeGeneration;
  }

  DisplayList cached() {
    return cached;
  }

  void clear() {
    cached = null;
    recorder.reset();
  }

  Recorder begin(Canvas target) {
    return begin(target, true);
  }

  Recorder begin(Canvas target, boolean allowEntityHead) {
    recorder.reset();
    recorder.target = target;
    recorder.allowEntityHead = allowEntityHead;
    return recorder;
  }

  void store(DisplayList list) {
    cached = list;
  }

  void replay(Canvas canvas) {
    DisplayList list = cached;
    if (list == null) return;
    replay(canvas, list);
  }

  static void replay(Canvas canvas, DisplayList list) {
    int fi = 0, ii = 0, oi = 0;
    for (int index = 0; index < list.ops.length; index++) {
      switch (list.ops[index]) {
        case Recorder.RECT:
          canvas.rect(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.ints[ii]);
          fi += 4;
          ii++;
          break;
        case Recorder.ROUNDED:
          canvas.roundedRect(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.ints[ii]);
          fi += 5;
          ii++;
          break;
        case Recorder.ROUNDED_CORNERS:
          canvas.roundedRectCorners(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5], list.floats[fi + 6],
              list.floats[fi + 7], list.ints[ii]);
          fi += 8;
          ii++;
          break;
        case Recorder.STROKE:
          canvas.strokeRoundedRect(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5], list.ints[ii]);
          fi += 6;
          ii++;
          break;
        case Recorder.STROKE_CORNERS:
          canvas.strokeRoundedRectCorners(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5], list.floats[fi + 6],
              list.floats[fi + 7], list.floats[fi + 8], list.ints[ii]);
          fi += 9;
          ii++;
          break;
        case Recorder.GRADIENT:
          canvas.gradientRect(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.ints[ii], list.ints[ii + 1]);
          fi += 4;
          ii += 2;
          break;
        case Recorder.GRADIENT_ROUNDED:
          canvas.gradientRoundedRect(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.ints[ii], list.ints[ii + 1]);
          fi += 5;
          ii += 2;
          break;
        case Recorder.GRADIENT_CORNERS:
          canvas.gradientRoundedRectCorners(list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5],
              list.floats[fi + 6], list.floats[fi + 7], list.ints[ii], list.ints[ii + 1],
              list.ints[ii + 2], list.ints[ii + 3]);
          fi += 8;
          ii += 4;
          break;
        case Recorder.LINE:
          canvas.line(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.ints[ii]);
          fi += 5;
          ii++;
          break;
        case Recorder.CIRCLE:
          canvas.circle(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2], list.ints[ii]);
          fi += 3;
          ii++;
          break;
        case Recorder.TEXT:
          canvas.text((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.ints[ii], list.ints[ii + 1] != 0);
          fi += 3;
          ii += 2;
          oi++;
          break;
        case Recorder.TEXT_FAMILY:
          canvas.textFamily((String) list.objects[oi], (String) list.objects[oi + 1],
              list.floats[fi], list.floats[fi + 1], list.floats[fi + 2], list.ints[ii],
              list.ints[ii + 1] != 0, list.floats[fi + 3]);
          fi += 4;
          ii += 2;
          oi += 2;
          break;
        case Recorder.TEXT_TRACKED:
          canvas.textTracked((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.ints[ii], list.ints[ii + 1] != 0, list.floats[fi + 3]);
          fi += 4;
          ii += 2;
          oi++;
          break;
        case Recorder.GAME_TEXT:
          canvas.gameText((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.ints[ii], list.ints[ii + 1] != 0);
          fi += 3;
          ii += 2;
          oi++;
          break;
        case Recorder.PUSH_CLIP:
          canvas.pushClip(
              list.floats[fi], list.floats[fi + 1], list.floats[fi + 2], list.floats[fi + 3]);
          fi += 4;
          break;
        case Recorder.POP_CLIP:
          canvas.popClip();
          break;
        case Recorder.ITEM:
          canvas.item((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3]);
          fi += 4;
          oi++;
          break;
        case Recorder.INVENTORY_ITEM:
          canvas.inventoryItem((InventorySlot) list.objects[oi], list.floats[fi],
              list.floats[fi + 1], list.floats[fi + 2], list.floats[fi + 3]);
          fi += 4;
          oi++;
          break;
        case Recorder.ENTITY_HEAD:
          canvas.entityHead((TargetView) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3]);
          fi += 4;
          oi++;
          break;
        case Recorder.PLAYER_PREVIEW:
          canvas.playerPreview((PlayerSkin) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5],
              list.floats[fi + 6], list.floats[fi + 7], list.floats[fi + 8], list.floats[fi + 9],
              list.floats[fi + 10]);
          fi += 11;
          oi++;
          break;
        case Recorder.CAPE_PREVIEW:
          canvas.capePreview((String) list.objects[oi], (PlayerSkin) list.objects[oi + 1],
              list.floats[fi], list.floats[fi + 1], list.floats[fi + 2], list.floats[fi + 3],
              list.floats[fi + 4]);
          fi += 5;
          oi += 2;
          break;
        case Recorder.HUNGER:
          canvas.hungerIcon(
              list.floats[fi], list.floats[fi + 1], list.floats[fi + 2], list.ints[ii]);
          fi += 3;
          ii++;
          break;
        case Recorder.BACKDROP:
          canvas.backdrop(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5], list.ints[ii],
              list.floats[fi + 6]);
          fi += 7;
          ii++;
          break;
        case Recorder.BACKDROP_CORNERS:
          canvas.backdropCorners(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5], list.floats[fi + 6],
              list.floats[fi + 7], list.floats[fi + 8], list.ints[ii], list.floats[fi + 9]);
          fi += 10;
          ii++;
          break;
        case Recorder.TEXTURE:
          canvas.texture((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5],
              list.ints[ii]);
          fi += 6;
          ii++;
          oi++;
          break;
        case Recorder.TEXTURE_CORNERS:
          canvas.textureCorners((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5],
              list.floats[fi + 6], list.floats[fi + 7], list.floats[fi + 8], list.ints[ii]);
          fi += 9;
          ii++;
          oi++;
          break;
        case Recorder.GAME_SPRITE:
          canvas.gameSprite((String) list.objects[oi], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5],
              list.floats[fi + 6], list.floats[fi + 7], list.floats[fi + 8], list.floats[fi + 9],
              list.ints[ii]);
          fi += 10;
          ii++;
          oi++;
          break;
        case Recorder.VIEWMODEL:
          canvas.viewmodelOutline((String) list.objects[oi], list.ints[ii]);
          ii++;
          oi++;
          break;
        case Recorder.VANILLA_CROSSHAIR:
          canvas.vanillaCrosshair(
              list.floats[fi], list.floats[fi + 1], list.floats[fi + 2], list.floats[fi + 3]);
          fi += 4;
          break;
        case Recorder.TRIANGLE:
          canvas.fillTriangle(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4], list.floats[fi + 5], list.ints[ii]);
          fi += 6;
          ii++;
          break;
        case Recorder.INVERT:
          canvas.invertRect(list.floats[fi], list.floats[fi + 1], list.floats[fi + 2],
              list.floats[fi + 3], list.floats[fi + 4]);
          fi += 5;
          break;
        case Recorder.ICON_QUAD: {
          long rev = (((long) list.ints[ii]) << 32) | (list.ints[ii + 1] & 0xFFFFFFFFL);
          canvas.iconQuad((String) list.objects[oi], rev, (int[]) list.objects[oi + 1],
              list.ints[ii + 2], list.ints[ii + 3], list.floats[fi], list.floats[fi + 1],
              list.floats[fi + 2], list.floats[fi + 3], list.ints[ii + 4]);
          fi += 4;
          ii += 5;
          oi += 2;
          break;
        }
        case Recorder.POLYGON:
          canvas.fillPolygon(
              list.floats[fi], list.floats[fi + 1], (float[]) list.objects[oi], list.ints[ii]);
          fi += 2;
          ii++;
          oi++;
          break;
        default:
          throw new IllegalStateException("Unknown HUD cache command " + list.ops[index]);
      }
    }
  }

  static boolean cacheableDocument(
      SkinDocument document, Function<SkinDocument.Node, SkinDocument.Node> components) {
    if (document == null || document.hud() == null) return false;
    return new Scanner(document, components)
        .scan(document.hud(), 0, new IdentityHashMap<SkinDocument.Node, Boolean>());
  }

  private static final class Scanner {
    private final SkinDocument document;
    private final Function<SkinDocument.Node, SkinDocument.Node> components;

    Scanner(SkinDocument document, Function<SkinDocument.Node, SkinDocument.Node> components) {
      this.document = document;
      this.components = components;
    }

    boolean scan(
        SkinDocument.Node node, int depth, IdentityHashMap<SkinDocument.Node, Boolean> visiting) {
      if (node == null || depth > 24 || visiting.put(node, Boolean.TRUE) != null) return true;
      try {
        String tag = node.tag == null ? "" : node.tag;
        if ("player-preview".equals(tag) || "cape-preview".equals(tag)
            || "account-face".equals(tag)
            || "viewmodel-gizmo".equals(tag) || "viewmodel".equals(tag)
            || "pixel-grid".equals(tag) || "input".equals(tag) || "text-input".equals(tag))
          return false;
        if (!safe(node.text) || !safe(node.attributes)) return false;
        if (!safe(node.inlineStyles)) return false;
        for (int state = 0; state < STATES.length; state++) {
          if (!safe(document.styles(node, STATES[state]))) return false;
          if (!safe(document.themeBindings(node, STATES[state]))) return false;
        }
        SkinDocument.Node component = components == null ? null : components.apply(node);
        if (component != null && component != node && !"component".equals(tag)
            && !"none".equals(node.attribute("visual", ""))) {
          if (!scan(component, depth + 1, visiting)) return false;
        }
        for (int index = 0; index < node.children.size(); index++)
          if (!scan(node.children.get(index), depth + 1, visiting)) return false;
        return true;
      } finally {
        visiting.remove(node);
      }
    }

    private static boolean safe(Map<String, String> values) {
      if (values == null || values.isEmpty()) return true;
      for (Map.Entry<String, String> entry : values.entrySet()) {
        if (!safe(entry.getKey()) || !safe(entry.getValue())) return false;
      }
      return true;
    }

    private static boolean safe(String value) {
      if (value == null || value.isEmpty()) return true;
      return !DYNAMIC.matcher(value).find() && !RANDOM.matcher(value).find();
    }
  }

  static final class Recorder implements Canvas {
    static final byte RECT = 1, ROUNDED = 2, ROUNDED_CORNERS = 3, STROKE = 4, STROKE_CORNERS = 5,
                      GRADIENT = 6, GRADIENT_ROUNDED = 7, GRADIENT_CORNERS = 8, LINE = 9,
                      CIRCLE = 10, TEXT = 11, TEXT_FAMILY = 12, TEXT_TRACKED = 13, GAME_TEXT = 14,
                      PUSH_CLIP = 15, POP_CLIP = 16, ITEM = 17, INVENTORY_ITEM = 18,
                      ENTITY_HEAD = 19, PLAYER_PREVIEW = 20, HUNGER = 21, BACKDROP = 22,
                      BACKDROP_CORNERS = 23, TEXTURE = 24, TEXTURE_CORNERS = 25, GAME_SPRITE = 26,
                      VIEWMODEL = 27, VANILLA_CROSSHAIR = 28, TRIANGLE = 29, INVERT = 30,
                      CAPE_PREVIEW = 31, ICON_QUAD = 32, POLYGON = 33;

    private final HudFrameCache owner;
    private Canvas target;
    private byte[] ops = new byte[128];
    private int[] ints = new int[128];
    private float[] floats = new float[512];
    private Object[] objects = new Object[128];
    private int opCount, intCount, floatCount, objectCount;
    private boolean dynamic, overflow, allowEntityHead = true;

    Recorder(HudFrameCache owner) {
      this.owner = owner;
    }

    void reset() {
      target = null;
      if (objectCount > 0) java.util.Arrays.fill(objects, 0, objectCount, null);
      opCount = intCount = floatCount = objectCount = 0;
      dynamic = overflow = false;
      allowEntityHead = true;
    }

    boolean dynamic() {
      return dynamic;
    }

    boolean overflow() {
      return overflow;
    }

    int opCount() {
      return opCount;
    }

    DisplayList finish(int width, int height, int frames, int commands, boolean crosshair,
        Object metricsKey, long signature, long themeGeneration, float hudExitDuration) {
      if (dynamic || overflow || commandLimit(commands)) {
        reset();
        return null;
      }
      byte[] copiedOps = new byte[opCount];
      int[] copiedInts = new int[intCount];
      float[] copiedFloats = new float[floatCount];
      Object[] copiedObjects = new Object[objectCount];
      System.arraycopy(ops, 0, copiedOps, 0, opCount);
      System.arraycopy(ints, 0, copiedInts, 0, intCount);
      System.arraycopy(floats, 0, copiedFloats, 0, floatCount);
      System.arraycopy(objects, 0, copiedObjects, 0, objectCount);
      DisplayList list =
          new DisplayList(width, height, frames, commands, hudExitDuration, themeGeneration,
              crosshair, metricsKey, signature, copiedOps, copiedInts, copiedFloats, copiedObjects);
      reset();
      return list;
    }

    private static boolean commandLimit(int commands) {
      return commands > MAX_COMMANDS;
    }

    private void target(Canvas canvas) {
      target = canvas;
    }

    private void op(byte op) {
      if (target == null || overflow || dynamic) return;
      if (opCount >= MAX_COMMANDS) {
        overflow = true;
        return;
      }
      if (opCount == ops.length) ops = grow(ops, opCount + 1);
      ops[opCount++] = op;
    }

    private void f(float value) {
      if (target == null || overflow || dynamic) return;
      if (floatCount == floats.length) floats = grow(floats, floatCount + 1);
      floats[floatCount++] = value;
    }

    private void i(int value) {
      if (target == null || overflow || dynamic) return;
      if (intCount == ints.length) ints = grow(ints, intCount + 1);
      ints[intCount++] = value;
    }

    private void o(Object value) {
      if (target == null || overflow || dynamic) return;
      if (objectCount == objects.length) objects = grow(objects, objectCount + 1);
      objects[objectCount++] = value;
    }

    private static byte[] grow(byte[] values, int needed) {
      byte[] next = new byte[Math.max(needed, values.length * 2)];
      System.arraycopy(values, 0, next, 0, values.length);
      return next;
    }

    private static int[] grow(int[] values, int needed) {
      int[] next = new int[Math.max(needed, values.length * 2)];
      System.arraycopy(values, 0, next, 0, values.length);
      return next;
    }

    private static float[] grow(float[] values, int needed) {
      float[] next = new float[Math.max(needed, values.length * 2)];
      System.arraycopy(values, 0, next, 0, values.length);
      return next;
    }

    private static Object[] grow(Object[] values, int needed) {
      Object[] next = new Object[Math.max(needed, values.length * 2)];
      System.arraycopy(values, 0, next, 0, values.length);
      return next;
    }

    public Object textMetricsKey() {
      return target == null ? null : target.textMetricsKey();
    }

    public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
      boolean drawn = target != null && target.vanillaCrosshair(x, y, size, opacity);
      op(VANILLA_CROSSHAIR);
      f(x);
      f(y);
      f(size);
      f(opacity);
      return drawn;
    }

    public void viewmodelOutline(String hand, int argb) {
      dynamic = true;
      if (target != null) target.viewmodelOutline(hand, argb);
      op(VIEWMODEL);
      o(hand);
      i(argb);
    }

    public void rect(float x, float y, float width, float height, int argb) {
      op(RECT);
      f(x);
      f(y);
      f(width);
      f(height);
      i(argb);
      if (target != null) target.rect(x, y, width, height, argb);
    }

    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int argb) {
      op(TRIANGLE);
      f(ax);
      f(ay);
      f(bx);
      f(by);
      f(cx);
      f(cy);
      i(argb);
      if (target != null) target.fillTriangle(ax, ay, bx, by, cx, cy, argb);
    }

    public void fillPolygon(float centerX, float centerY, float[] xy, int argb) {
      if (xy == null || xy.length < 6) return;
      op(POLYGON);
      f(centerX);
      f(centerY);
      o(xy.clone());
      i(argb);
      if (target != null) target.fillPolygon(centerX, centerY, xy, argb);
    }

    public void invertRect(float x, float y, float width, float height, float alpha) {
      op(INVERT);
      f(x);
      f(y);
      f(width);
      f(height);
      f(alpha);
      if (target != null) target.invertRect(x, y, width, height, alpha);
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int argb) {
      op(ROUNDED);
      f(x);
      f(y);
      f(width);
      f(height);
      f(radius);
      i(argb);
      if (target != null) target.roundedRect(x, y, width, height, radius, argb);
    }

    public void roundedRectCorners(
        float x, float y, float w, float h, float tl, float tr, float bl, float br, int color) {
      op(ROUNDED_CORNERS);
      f(x);
      f(y);
      f(w);
      f(h);
      f(tl);
      f(tr);
      f(bl);
      f(br);
      i(color);
      if (target != null) target.roundedRectCorners(x, y, w, h, tl, tr, bl, br, color);
    }

    public void strokeRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
        float bl, float br, float thickness, int color) {
      op(STROKE_CORNERS);
      f(x);
      f(y);
      f(w);
      f(h);
      f(tl);
      f(tr);
      f(bl);
      f(br);
      f(thickness);
      i(color);
      if (target != null)
        target.strokeRoundedRectCorners(x, y, w, h, tl, tr, bl, br, thickness, color);
    }

    public void gradientRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
        float bl, float br, int ctl, int ctr, int cbl, int cbr) {
      op(GRADIENT_CORNERS);
      f(x);
      f(y);
      f(w);
      f(h);
      f(tl);
      f(tr);
      f(bl);
      f(br);
      i(ctl);
      i(ctr);
      i(cbl);
      i(cbr);
      if (target != null)
        target.gradientRoundedRectCorners(x, y, w, h, tl, tr, bl, br, ctl, ctr, cbl, cbr);
    }

    public void strokeRoundedRect(
        float x, float y, float width, float height, float radius, float thickness, int argb) {
      op(STROKE);
      f(x);
      f(y);
      f(width);
      f(height);
      f(radius);
      f(thickness);
      i(argb);
      if (target != null) target.strokeRoundedRect(x, y, width, height, radius, thickness, argb);
    }

    public void gradientRect(
        float x, float y, float width, float height, int topArgb, int bottomArgb) {
      op(GRADIENT);
      f(x);
      f(y);
      f(width);
      f(height);
      i(topArgb);
      i(bottomArgb);
      if (target != null) target.gradientRect(x, y, width, height, topArgb, bottomArgb);
    }

    public void gradientRoundedRect(
        float x, float y, float width, float height, float radius, int leftArgb, int rightArgb) {
      op(GRADIENT_ROUNDED);
      f(x);
      f(y);
      f(width);
      f(height);
      f(radius);
      i(leftArgb);
      i(rightArgb);
      if (target != null)
        target.gradientRoundedRect(x, y, width, height, radius, leftArgb, rightArgb);
    }

    public void line(float x1, float y1, float x2, float y2, float thickness, int argb) {
      op(LINE);
      f(x1);
      f(y1);
      f(x2);
      f(y2);
      f(thickness);
      i(argb);
      if (target != null) target.line(x1, y1, x2, y2, thickness, argb);
    }

    public void circle(float centerX, float centerY, float radius, int argb) {
      op(CIRCLE);
      f(centerX);
      f(centerY);
      f(radius);
      i(argb);
      if (target != null) target.circle(centerX, centerY, radius, argb);
    }

    public void text(String value, float x, float y, float size, int argb, boolean bold) {
      op(TEXT);
      o(value);
      f(x);
      f(y);
      f(size);
      i(argb);
      i(bold ? 1 : 0);
      if (target != null) target.text(value, x, y, size, argb, bold);
    }

    public float textWidth(String value, float size, boolean bold) {
      return target == null ? 0 : target.textWidth(value, size, bold);
    }

    public void textFamily(String family, String value, float x, float y, float size, int argb,
        boolean bold, float tracking) {
      op(TEXT_FAMILY);
      o(family);
      o(value);
      f(x);
      f(y);
      f(size);
      f(tracking);
      i(argb);
      i(bold ? 1 : 0);
      if (target != null) target.textFamily(family, value, x, y, size, argb, bold, tracking);
    }

    public float textWidthFamily(
        String family, String value, float size, boolean bold, float tracking) {
      return target == null ? 0 : target.textWidthFamily(family, value, size, bold, tracking);
    }

    public void gameText(String value, float x, float y, float scale, int argb, boolean shadow) {
      op(GAME_TEXT);
      o(value);
      f(x);
      f(y);
      f(scale);
      i(argb);
      i(shadow ? 1 : 0);
      if (target != null) target.gameText(value, x, y, scale, argb, shadow);
    }

    public float gameTextWidth(String value, float scale) {
      return target == null ? 0 : target.gameTextWidth(value, scale);
    }

    public void hungerIcon(float x, float y, float size, int fill) {
      op(HUNGER);
      f(x);
      f(y);
      f(size);
      i(fill);
      if (target != null) target.hungerIcon(x, y, size, fill);
    }

    public void pushClip(float x, float y, float width, float height) {
      op(PUSH_CLIP);
      f(x);
      f(y);
      f(width);
      f(height);
      if (target != null) target.pushClip(x, y, width, height);
    }

    public void popClip() {
      op(POP_CLIP);
      if (target != null) target.popClip();
    }

    public void item(String registryId, float x, float y, float size, float alpha) {
      op(ITEM);
      o(registryId);
      f(x);
      f(y);
      f(size);
      f(alpha);
      if (target != null) target.item(registryId, x, y, size, alpha);
    }

    public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
      op(INVENTORY_ITEM);
      o(slot);
      f(x);
      f(y);
      f(size);
      f(alpha);
      if (target != null) target.inventoryItem(slot, x, y, size, alpha);
    }

    public boolean entityHead(TargetView targetView, float x, float y, float size, float alpha) {
      boolean drawn = target != null && target.entityHead(targetView, x, y, size, alpha);
      if (drawn && !allowEntityHead) dynamic = true;
      op(ENTITY_HEAD);
      o(targetView);
      f(x);
      f(y);
      f(size);
      f(alpha);
      return drawn;
    }

    public boolean skinHead(String skinTextureId, float x, float y, float size, float radius,
        float alpha, int tintRgb) {
      dynamic = true;
      return target != null
          && target.skinHead(skinTextureId, x, y, size, radius, alpha, tintRgb);
    }

    public boolean textureRegion(String textureId, float x, float y, float width, float height,
        float radius, float u0, float v0, float u1, float v1, float alpha, int tintRgb) {
      dynamic = true;
      return target != null
          && target.textureRegion(
              textureId, x, y, width, height, radius, u0, v0, u1, v1, alpha, tintRgb);
    }

    public boolean supportsIconAtlas() {
      return target != null && target.supportsIconAtlas();
    }

    public float iconPixelDensity() {
      return target == null ? 1.0f : target.iconPixelDensity();
    }

    public void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float x, float y, float width, float height, int argb) {
      op(ICON_QUAD);
      o(cacheKey);
      o(pixels);
      i((int) (revision >>> 32));
      i((int) revision);
      i(texWidth);
      i(texHeight);
      i(argb);
      f(x);
      f(y);
      f(width);
      f(height);
      if (target != null) {
        target.iconQuad(cacheKey, revision, pixels, texWidth, texHeight, x, y, width, height, argb);
      }
    }

    public void mapQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
        float x, float y, float width, float height, float radius, float uCenter, float vCenter,
        float uHalfSpan, float vHalfSpan, float angleRad, boolean nearest, int argb) {
      dynamic = true;
      if (target != null) {
        target.mapQuad(cacheKey, revision, pixels, texWidth, texHeight, x, y, width, height,
            radius, uCenter, vCenter, uHalfSpan, vHalfSpan, angleRad, nearest, argb);
      }
    }

    public void playerPreview(PlayerSkin skin, float x, float y, float width, float height,
        float yaw, float pitch, float zoom, float time, float wind, float yawVelocity,
        float opacity) {
      dynamic = true;
      op(PLAYER_PREVIEW);
      o(skin);
      f(x);
      f(y);
      f(width);
      f(height);
      f(yaw);
      f(pitch);
      f(zoom);
      f(time);
      f(wind);
      f(yawVelocity);
      f(opacity);
      if (target != null)
        target.playerPreview(
            skin, x, y, width, height, yaw, pitch, zoom, time, wind, yawVelocity, opacity);
    }

    public void capePreview(String cacheKey, PlayerSkin skin, float x, float y, float width,
        float height, float opacity) {
      dynamic = true;
      op(CAPE_PREVIEW);
      o(cacheKey);
      o(skin);
      f(x);
      f(y);
      f(width);
      f(height);
      f(opacity);
      if (target != null) target.capePreview(cacheKey, skin, x, y, width, height, opacity);
    }

    public void backdrop(float x, float y, float width, float height, float radius,
        float blurRadius, int quality, float opacity) {
      op(BACKDROP);
      f(x);
      f(y);
      f(width);
      f(height);
      f(radius);
      f(blurRadius);
      f(opacity);
      i(quality);
      if (target != null)
        target.backdrop(x, y, width, height, radius, blurRadius, quality, opacity);
    }

    public void backdropCorners(float x, float y, float w, float h, float tl, float tr, float bl,
        float br, float blurRadius, int quality, float opacity) {
      op(BACKDROP_CORNERS);
      f(x);
      f(y);
      f(w);
      f(h);
      f(tl);
      f(tr);
      f(bl);
      f(br);
      f(blurRadius);
      f(opacity);
      i(quality);
      if (target != null)
        target.backdropCorners(x, y, w, h, tl, tr, bl, br, blurRadius, quality, opacity);
    }

    public void texture(String resourceId, float x, float y, float width, float height,
        float radius, float tileSize, int argb) {
      op(TEXTURE);
      o(resourceId);
      f(x);
      f(y);
      f(width);
      f(height);
      f(radius);
      f(tileSize);
      i(argb);
      if (target != null) target.texture(resourceId, x, y, width, height, radius, tileSize, argb);
    }

    public void textureCorners(String resourceId, float x, float y, float width, float height,
        float tl, float tr, float bl, float br, float tileSize, int argb) {
      op(TEXTURE_CORNERS);
      o(resourceId);
      f(x);
      f(y);
      f(width);
      f(height);
      f(tl);
      f(tr);
      f(bl);
      f(br);
      f(tileSize);
      i(argb);
      if (target != null)
        target.textureCorners(resourceId, x, y, width, height, tl, tr, bl, br, tileSize, argb);
    }

    public void gameSprite(String spriteId, float x, float y, float width, float height, float u,
        float v, float regionWidth, float regionHeight, float sourceWidth, float sourceHeight,
        int argb) {
      op(GAME_SPRITE);
      o(spriteId);
      f(x);
      f(y);
      f(width);
      f(height);
      f(u);
      f(v);
      f(regionWidth);
      f(regionHeight);
      f(sourceWidth);
      f(sourceHeight);
      i(argb);
      if (target != null)
        target.gameSprite(spriteId, x, y, width, height, u, v, regionWidth, regionHeight,
            sourceWidth, sourceHeight, argb);
    }

    public void textTracked(
        String value, float x, float y, float size, int argb, boolean bold, float tracking) {
      op(TEXT_TRACKED);
      o(value);
      f(x);
      f(y);
      f(size);
      f(tracking);
      i(argb);
      i(bold ? 1 : 0);
      if (target != null) target.textTracked(value, x, y, size, argb, bold, tracking);
    }

    public float textWidthTracked(String value, float size, boolean bold, float tracking) {
      return target == null ? 0 : target.textWidthTracked(value, size, bold, tracking);
    }
  }

  private static final ThreadLocal<Hasher> REUSABLE_HASHER =
      new ThreadLocal<Hasher>() {
        @Override
        protected Hasher initialValue() {
          return new Hasher();
        }
      };

  static long signature(Settings settings, UiActions actions, List<Cooldown> cooldowns,
      List<InventorySlot> inventory, List<TntView> tnts, int width, int height, boolean crosshair,
      Map<String, Object> ui, float mouseX, float mouseY, String page, String selectedModule,
      String selectedHud, String pressedKey, String focusedKey, String draggedKey, String colorBind,
      String colorLabel, boolean entrance, long interaction) {
    Hasher hasher = REUSABLE_HASHER.get();
    hasher.reset();
    long rev = actions.settingsRevision();
    if (rev >= 0L)
      hasher.mix(rev);
    else
      hasher.value(settings, 0);
    hasher.cooldownList(cooldowns);
    hasher.inventorySlotList(inventory);
    hasher.tntViewList(tnts);
    hasher.inventorySlotList(actions.armor());
    hasher.potionViewList(actions.potions());
    hasher.vitalsView(actions.vitals());
    hasher.targetView(actions.target());
    hasher.targetView(actions.localPlayer());
    hasher.map(actions.networkInfo(), 0);
    hasher.mix(actions.ping());
    hasher.mix(actions.fps());
    hasher.string(actions.gameVersion());
    hasher.map(actions.keystrokes(), 0);
    hasher.map(actions.coordinates(), 0);
    hasher.map(actions.scoreboard(), 0);
    hasher.mix(actions.leftMainArm() ? 1 : 2);
    hasher.string(actions.saveStatus());
    hasher.mix(actions.fireVisible() ? 1 : 2);
    hasher.mix(actions.crosshairTargetHit() ? 1 : 2);
    hasher.map(ui, 0);
    hasher.mix(width);
    hasher.mix(height);
    hasher.mix(crosshair ? 1 : 2);
    hasher.mix(Float.floatToIntBits(mouseX));
    hasher.mix(Float.floatToIntBits(mouseY));
    hasher.string(page);
    hasher.string(selectedModule);
    hasher.string(selectedHud);
    hasher.string(pressedKey);
    hasher.string(focusedKey);
    hasher.string(draggedKey);
    hasher.string(colorBind);
    hasher.string(colorLabel);
    hasher.mix(entrance ? 1 : 2);
    hasher.mix(interaction);
    return hasher.unsupported ? 0L : (hasher.hash == 0L ? 1L : hasher.hash);
  }

  static long menuSignature(Settings settings, UiActions actions, Map<String, Object> ui,
      Map<String, Map<String, Object>> localStates, Map<String, Float> scroll,
      Map<String, Object> hostContext, List<String> profileCache, int width, int height,
      float mouseX, float mouseY, String page, String selectedModule, String selectedHud,
      String pressedKey, boolean entrance) {
    Hasher hasher = REUSABLE_HASHER.get();
    hasher.reset();
    long rev = actions.settingsRevision();
    if (rev >= 0L)
      hasher.mix(rev);
    else
      hasher.value(settings, 0);
    hasher.map(ui, 0);
    hasher.map(localStates, 0);
    hasher.map(scroll, 0);
    hasher.map(hostContext, 0);
    hasher.stringList(profileCache);
    hasher.string(actions.gameVersion());
    hasher.string(actions.saveStatus());
    hasher.string(actions.skinStatus());
    hasher.mix(actions.canUndo() ? 1 : 2);
    hasher.mix(width);
    hasher.mix(height);
    hasher.mix(Float.floatToIntBits(mouseX));
    hasher.mix(Float.floatToIntBits(mouseY));
    hasher.string(page);
    hasher.string(selectedModule);
    hasher.string(selectedHud);
    hasher.string(pressedKey);
    hasher.mix(entrance ? 1 : 2);
    return hasher.unsupported ? 0L : (hasher.hash == 0L ? 1L : hasher.hash);
  }

  static long mix(long hash, long value) {
    return (hash ^ value) * 0x9E3779B185EBCA87L;
  }

  private static final class Hasher {
    private static final long NULL_SENTINEL = 0x9E3779B97F4A7C15L;
    private static final long COOLDOWN_TAG = 0x517CC1B727220A95L;
    private static final long INVENTORY_SLOT_TAG = 0x94D049BB133111EBL;
    private static final long TNT_VIEW_TAG = 0xBF58476D1CE4E5B9L;
    private static final long POTION_VIEW_TAG = 0x2545F4914F6CDD1DL;
    private static final long VITALS_VIEW_TAG = 0xD6E8FEB86659FD93L;
    private static final long TARGET_VIEW_TAG = 0xA0761D6478BD642FL;
    private static final long PLAYER_SKIN_TAG = 0xE7037ED1A0B428DBL;
    private static final long LIST_TAG = 0x8EBC6AF09C88C6E3L;
    private static final long MAP_TAG = 0x589965CC75374CC3L;

    long hash = 0xCBF29CE484222325L;
    boolean unsupported;
    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<Object, Boolean>(64);
    private int mapDepth;
    private final BiConsumer<Object, Object> mapEntryHasher =
        new BiConsumer<Object, Object>() {
          @Override
          public void accept(Object k, Object v) {
            if (unsupported) return;
            value(k, mapDepth);
            value(v, mapDepth);
          }
        };

    void reset() {
      hash = 0xCBF29CE484222325L;
      unsupported = false;
      if (!seen.isEmpty()) seen.clear();
    }

    void string(String s) {
      if (s == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(s.length());
      mix(s.hashCode());
    }

    void intArray(int[] arr) {
      if (arr == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(arr.length);
      for (int i = 0; i < arr.length; i++) mix(arr[i]);
    }

    void cooldown(Cooldown c) {
      if (c == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(COOLDOWN_TAG);
      string(c.key);
      string(c.name);
      string(c.itemId);
      mix(Float.floatToIntBits(c.remainingSeconds));
      mix(Float.floatToIntBits(c.durationSeconds));
      mix(Float.floatToIntBits(c.remainingFraction));
    }

    void inventorySlot(InventorySlot s) {
      if (s == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(INVENTORY_SLOT_TAG);
      mix(s.slot);
      string(s.itemId);
      string(s.name);
      mix(s.count);
      mix(s.damage);
      mix(s.maxDamage);
      mix(Float.floatToIntBits(s.damageFraction));
      mix(s.enchanted ? 1 : 2);
    }

    void tntView(TntView t) {
      if (t == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(TNT_VIEW_TAG);
      mix(t.id);
      mix(Float.floatToIntBits(t.screenX));
      mix(Float.floatToIntBits(t.screenY));
      mix(Float.floatToIntBits(t.seconds));
      mix(Float.floatToIntBits(t.distance));
      mix(t.visible ? 1 : 2);
    }

    void potionView(PotionView p) {
      if (p == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(POTION_VIEW_TAG);
      string(p.id);
      string(p.name);
      mix(p.amplifier);
      mix(p.color);
      mix(Float.floatToIntBits(p.durationSeconds));
      mix(p.beneficial ? 1 : 2);
    }

    void vitalsView(VitalsView v) {
      if (v == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(VITALS_VIEW_TAG);
      mix(v.hunger);
      mix(Float.floatToIntBits(v.saturation));
      mix(v.available ? 1 : 2);
      mix(v.hungerVisible ? 1 : 2);
    }

    void targetView(TargetView t) {
      if (t == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(TARGET_VIEW_TAG);
      mix(t.entityId);
      string(t.uuid);
      string(t.name);
      mix(Float.floatToIntBits(t.health));
      mix(Float.floatToIntBits(t.maxHealth));
      mix(Float.floatToIntBits(t.absorption));
      mix(Float.floatToIntBits(t.distance));
      mix(t.player ? 1 : 2);
      string(t.headTexture);
      inventorySlotList(t.equipment);
    }

    void playerSkin(PlayerSkin s) {
      if (s == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(PLAYER_SKIN_TAG);
      mix(s.bodyWidth());
      mix(s.bodyHeight());
      mix(s.capeWidth());
      mix(s.capeHeight());
      mix(s.slimModel() ? 1 : 2);
      intArray(s.bodyPixels());
      intArray(s.capePixels());
    }

    void cooldownList(List<Cooldown> list) {
      if (list == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(LIST_TAG);
      int n = list.size();
      mix(n);
      if (list instanceof RandomAccess) {
        for (int i = 0; i < n; i++) cooldown(list.get(i));
      } else {
        for (Cooldown item : list) cooldown(item);
      }
    }

    void inventorySlotList(List<InventorySlot> list) {
      if (list == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(LIST_TAG);
      int n = list.size();
      mix(n);
      if (list instanceof RandomAccess) {
        for (int i = 0; i < n; i++) inventorySlot(list.get(i));
      } else {
        for (InventorySlot item : list) inventorySlot(item);
      }
    }

    void tntViewList(List<TntView> list) {
      if (list == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(LIST_TAG);
      int n = list.size();
      mix(n);
      if (list instanceof RandomAccess) {
        for (int i = 0; i < n; i++) tntView(list.get(i));
      } else {
        for (TntView item : list) tntView(item);
      }
    }

    void potionViewList(List<PotionView> list) {
      if (list == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(LIST_TAG);
      int n = list.size();
      mix(n);
      if (list instanceof RandomAccess) {
        for (int i = 0; i < n; i++) potionView(list.get(i));
      } else {
        for (PotionView item : list) potionView(item);
      }
    }

    void stringList(List<String> list) {
      if (list == null) {
        mix(NULL_SENTINEL);
        return;
      }
      mix(LIST_TAG);
      int n = list.size();
      mix(n);
      if (list instanceof RandomAccess) {
        for (int i = 0; i < n; i++) string(list.get(i));
      } else {
        for (String item : list) string(item);
      }
    }

    void map(Map<?, ?> value, int depth) {
      if (unsupported) return;
      if (value == null) {
        mix(NULL_SENTINEL);
        return;
      }
      if (depth > 8) {
        unsupported = true;
        return;
      }
      mix(MAP_TAG);
      int size = value.size();
      mix(size);
      if (size == 0) return;
      int prevDepth = mapDepth;
      mapDepth = depth + 1;
      try {
        value.forEach(mapEntryHasher);
      } finally {
        mapDepth = prevDepth;
      }
    }

    void value(Object value, int depth) {
      if (unsupported) return;
      if (value == null) {
        mix(NULL_SENTINEL);
        return;
      }
      if (depth > 8) {
        unsupported = true;
        return;
      }
      if (value instanceof String) {
        string((String) value);
        return;
      }
      if (value instanceof Boolean) {
        mix((Boolean) value ? 1 : 2);
        return;
      }
      if (value instanceof Integer) {
        mix((Integer) value);
        return;
      }
      if (value instanceof Float) {
        mix(Float.floatToIntBits((Float) value));
        return;
      }
      if (value instanceof Double) {
        mix(Double.doubleToLongBits((Double) value));
        return;
      }
      if (value instanceof Long) {
        mix((Long) value);
        return;
      }
      if (value instanceof Short || value instanceof Byte) {
        mix(((Number) value).longValue());
        return;
      }
      if (value instanceof Character) {
        mix((Character) value);
        return;
      }
      if (value instanceof Map) {
        map((Map<?, ?>) value, depth);
        return;
      }
      if (value instanceof List) {
        List<?> list = (List<?>) value;
        mix(LIST_TAG);
        int n = list.size();
        mix(n);
        if (list instanceof RandomAccess) {
          for (int i = 0; i < n; i++) {
            value(list.get(i), depth + 1);
            if (unsupported) return;
          }
        } else {
          for (Object item : list) {
            value(item, depth + 1);
            if (unsupported) return;
          }
        }
        return;
      }
      if (value instanceof Cooldown) {
        cooldown((Cooldown) value);
        return;
      }
      if (value instanceof InventorySlot) {
        inventorySlot((InventorySlot) value);
        return;
      }
      if (value instanceof TntView) {
        tntView((TntView) value);
        return;
      }
      if (value instanceof PotionView) {
        potionView((PotionView) value);
        return;
      }
      if (value instanceof VitalsView) {
        vitalsView((VitalsView) value);
        return;
      }
      if (value instanceof TargetView) {
        targetView((TargetView) value);
        return;
      }
      if (value instanceof PlayerSkin) {
        playerSkin((PlayerSkin) value);
        return;
      }
      Class<?> type = value.getClass();
      if (value instanceof Enum) {
        mix(type.getName().hashCode());
        mix(((Enum<?>) value).name().hashCode());
        return;
      }
      if (value instanceof Class) {
        mix(((Class<?>) value).getName().hashCode());
        return;
      }
      if (value instanceof int[]) {
        intArray((int[]) value);
        return;
      }
      if (value instanceof long[]) {
        long[] array = (long[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(array[i]);
        return;
      }
      if (value instanceof float[]) {
        float[] array = (float[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(Float.floatToIntBits(array[i]));
        return;
      }
      if (value instanceof double[]) {
        double[] array = (double[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(Double.doubleToLongBits(array[i]));
        return;
      }
      if (value instanceof boolean[]) {
        boolean[] array = (boolean[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(array[i] ? 1 : 2);
        return;
      }
      if (value instanceof byte[]) {
        byte[] array = (byte[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(array[i]);
        return;
      }
      if (value instanceof short[]) {
        short[] array = (short[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(array[i]);
        return;
      }
      if (value instanceof char[]) {
        char[] array = (char[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) mix(array[i]);
        return;
      }
      if (value instanceof Object[]) {
        Object[] array = (Object[]) value;
        mix(array.length);
        for (int i = 0; i < array.length; i++) {
          value(array[i], depth + 1);
          if (unsupported) return;
        }
        return;
      }
      if (value instanceof Iterable) {
        mix(type.getName().hashCode());
        for (Object item : (Iterable<?>) value) {
          value(item, depth + 1);
          if (unsupported) return;
        }
        return;
      }
      if (seen.put(value, Boolean.TRUE) != null) {
        mix(System.identityHashCode(value));
        return;
      }
      try {
        reflectionFallbackCount++;
        Field[] fields = fields(type);
        if (fields.length == 0) {
          unsupported = true;
          return;
        }
        mix(type.getName().hashCode());
        for (Field field : fields) {
          Class<?> fieldType = field.getType();
          if (fieldType == int.class)
            mix(field.getInt(value));
          else if (fieldType == long.class)
            mix(field.getLong(value));
          else if (fieldType == float.class)
            mix(Float.floatToIntBits(field.getFloat(value)));
          else if (fieldType == double.class)
            mix(Double.doubleToLongBits(field.getDouble(value)));
          else if (fieldType == boolean.class)
            mix(field.getBoolean(value) ? 1 : 2);
          else if (fieldType == short.class)
            mix(field.getShort(value));
          else if (fieldType == byte.class)
            mix(field.getByte(value));
          else if (fieldType == char.class)
            mix(field.getChar(value));
          else
            value(field.get(value), depth + 1);
        }
      } catch (IllegalAccessException error) {
        unsupported = true;
      } finally {
        seen.remove(value);
      }
    }

    private static Field[] fields(Class<?> type) {
      Field[] cachedFields = FIELDS.get(type);
      if (cachedFields != null) return cachedFields;
      java.util.List<Field> values = new java.util.ArrayList<Field>();
      for (Field field : type.getFields()) {
        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
            && !field.isSynthetic())
          values.add(field);
      }
      cachedFields = values.toArray(new Field[values.size()]);
      FIELDS.put(type, cachedFields);
      return cachedFields;
    }

    void mix(long value) {
      hash ^= value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
    }
  }
}
