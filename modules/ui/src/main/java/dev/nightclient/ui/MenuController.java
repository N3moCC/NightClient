package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Keys;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.skin.SkinView;
import dev.nightclient.ui.skin.UiCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MenuController {
  private final Settings settings;
  private final UiActions actions;
  private final ViewmodelEditor viewmodelEditor;
  private final UiCatalog catalog;
  private SkinDocument document;
  private SkinView view;
  private final List<Handle> handles = new ArrayList<Handle>();
  private boolean editor, chatOverlay, closing, closeDispatched, waypointOnly, accountOnly;
  private String capturingBinding = "";
  private double now, openedAt = Double.NaN, closingAt;
  private int width, height, modifiers;
  private float mouseX, mouseY, snapX = -1, snapY = -1, dragX, dragY, startScale;
  private float[] resizeStart;
  private String selected = "";
  private Handle dragged;
  private String hudPositionDrag = "";
  private float hudDragStartX, hudDragStartY, hudDragLastX, hudDragLastY;
  private UiCanvas canvas;
  private float menuEnter = 1, menuFade = 1;
  private Map<String, Object> presentationCache, menuPresentationCache, editorStaticValues;
  private String menuPresentationLanguage = "", editorStaticLanguage = "", namesLanguage = "",
                 translationsLanguage = "";
  private SkinDocument editorStaticDocument, namesDocument;
  private long editorStaticPolicyRevision = -1L;
  private float menuPresentationEnter = Float.NaN, menuPresentationFade = Float.NaN;
  private final Map<String, String> names = new HashMap<String, String>(),
                                    translations = new HashMap<String, String>();
  private String derivedControlId, derivedCountId;
  private UiCatalog.Control derivedScale;
  private int derivedControlCount = -1;
  private List<String> editorTargetLabelKeys = Collections.emptyList();
  private final List<String> hudTargets = new ArrayList<String>();

  private interface Gesture {
    void move(float x, float y);
  }

  private static final class Handle {
    final float x, y, w, h;
    final Runnable click;
    final Gesture gesture;

    Handle(float x, float y, float w, float h, Runnable click, Gesture gesture) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.click = click;
      this.gesture = gesture;
    }

    boolean contains(float x, float y) {
      return x >= this.x && y >= this.y && x < this.x + w && y < this.y + h;
    }
  }

  public MenuController(Settings settings, UiActions actions) {
    this.settings = settings;
    this.actions = actions;
    catalog = new UiCatalog(settings);
    viewmodelEditor = new ViewmodelEditor(settings, actions);
    refreshSkin();
  }

  public String page() {
    return view == null ? "features" : view.page();
  }

  public void page(String page) {
    if (view == null) return;
    view.page(page);
    invalidatePresentation();
  }

  public void openWaypointManager() {
    if (view == null) return;
    waypointOnly = false;
    view.openWaypointManager();
    invalidatePresentation();
  }

  public void openWaypointManagerStandalone() {
    if (view == null) return;
    waypointOnly = true;
    accountOnly = false;
    view.openWaypointManager();
    invalidatePresentation();
  }

  public void openWaypointCreateStandalone() {
    if (view == null) return;
    waypointOnly = true;
    accountOnly = false;
    view.openWaypointCreate();
    invalidatePresentation();
  }

  public void openWaypointCreateStandalone(double x, double y, double z) {
    if (view == null) return;
    waypointOnly = true;
    accountOnly = false;
    view.openWaypointCreate(x, y, z);
    invalidatePresentation();
  }

  public void openAccountManager() {
    if (view == null) return;
    waypointOnly = false;
    accountOnly = false;
    view.openAccountManager(false);
    invalidatePresentation();
  }

  public void openAccountManagerStandalone() {
    if (view == null) return;
    waypointOnly = false;
    accountOnly = true;
    view.openAccountManager(true);
    invalidatePresentation();
  }

  public void refreshSkin() {
    finishHudPositionDrag();
    boolean keepMotion = !Double.isNaN(openedAt) && view != null;
    String page = view == null ? "features" : view.page();
    String savedSearch = view == null ? "" : view.searchQuery();
    String savedCapeSearch = view == null ? "" : view.capeSearchQuery();
    document = resolvedDocument();
    viewmodelEditor.presentation(document);
    hudTargets.clear();
    Set<String> ids = new LinkedHashSet<String>();
    collectHudTargets(document.hud(), ids);
    hudTargets.addAll(ids);
    invalidatePresentation();
    view = new SkinView(document, settings, actions);
    if (keepMotion) view.enableMotion();
    view.page(page);
    if (!savedSearch.isEmpty()) view.searchQuery(savedSearch);
    if (!savedCapeSearch.isEmpty()) view.capeSearchQuery(savedCapeSearch);
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        if (FeaturePolicy.isActionBlocked(action)) return false;
        if (action.startsWith("bind-key:")) {
          String id = action.substring(9);
          if (id.matches(
                  "menu|zoom|freelook|waypoint|worldMap|mapSettings|mapEnlarge|waypointsMenu|"
                  + "tapeMouseKey")) {
            capturingBinding = id;
            invalidatePresentation();
            return true;
          }
          return false;
        }
        if (action.startsWith("bind-module:")) {
          String id = action.substring(12);
          UiCatalog.Module module = catalog.module(id);
          if (module != null && module.id.equals(id)) {
            capturingBinding = "module:" + id;
            invalidatePresentation();
            return true;
          }
          return false;
        }
        if (editor && action.startsWith("page:")) {
          leaveEditor();
          view.page(action.substring(5));
          return true;
        }
        if ("close".equals(action)) {
          requestClose();
          return true;
        }
        if ("theme-reset".equals(action)) {
          actions.resetTheme();
          view.invalidateLayout();
          invalidatePresentation();
          return true;
        }
        if ("editor-open".equals(action)) {
          openEditor();
          return true;
        }
        if ("editor-done".equals(action)) {
          leaveEditor();
          return true;
        }
        if (action.startsWith("editor-target:")) {
          selected = action.substring(14);
          if ("viewmodel".equals(selected))
            viewmodelEditor.ensureHandSelected();
          else
            viewmodelEditor.clearSelection();
          invalidatePresentation();
          return true;
        }
        if (action.startsWith("viewmodel-hand:")) {
          viewmodelEditor.selectHand(action.substring(15));
          invalidatePresentation();
          return true;
        }
        if (action.startsWith("viewmodel-mode:")) {
          viewmodelEditor.ensureHandSelected();
          viewmodelEditor.chooseMode(action.substring(15));
          invalidatePresentation();
          return true;
        }
        if (action.startsWith("viewmodel-axis:")) {
          viewmodelEditor.ensureHandSelected();
          viewmodelEditor.chooseAxis(action.substring(15));
          invalidatePresentation();
          return true;
        }
        if ("viewmodel-reset".equals(action)) {
          viewmodelEditor.ensureHandSelected();
          viewmodelEditor.resetSelectedTransforms();
          invalidatePresentation();
          return true;
        }
        if ("viewmodel-reset-all".equals(action)) {
          viewmodelEditor.resetAllHands();
          invalidatePresentation();
          return true;
        }
        if ("editor-crosshair".equals(action)) {
          leaveEditor();
          view.selectModule("crosshair");
          return true;
        }
        return false;
      }

      public void bindingChanged(String bind) {
        if ("skin.preset".equals(bind)) {
          DefaultSkin.applyDefaults(settings.skin.preset, settings.theme);
          settings.validate();
        } else if ("wavyCapes.preset".equals(bind)) {
          if (settings.wavyCapes != null && !"custom".equals(settings.wavyCapes.preset)) {
            settings.wavyCapes.applyPreset(settings.wavyCapes.preset);
            settings.validate();
          }
        } else if (bind.startsWith("wavyCapes.") && !"wavyCapes.enabled".equals(bind)
            && !"wavyCapes.quality".equals(bind) && !"wavyCapes.players".equals(bind)
            && !"wavyCapes.maxDistance".equals(bind) && !"wavyCapes.weatherWind".equals(bind)
            && !"wavyCapes.waterPhysics".equals(bind) && !"wavyCapes.bodyCollision".equals(bind)) {
          if (settings.wavyCapes != null) settings.wavyCapes.preset = "custom";
        } else if ("aspectRatio.preset".equals(bind)) {
          if (settings.aspectRatio != null && !"custom".equals(settings.aspectRatio.preset)) {
            settings.aspectRatio.preset(settings.aspectRatio.preset);
            settings.validate();
          }
        } else if ("aspectRatio.ratio".equals(bind)) {
          if (settings.aspectRatio != null) settings.aspectRatio.preset = "custom";
        } else if ("optimization.profile".equals(bind)) {
          if (settings.optimization != null && !"custom".equals(settings.optimization.profile)) {
            settings.optimization.applyProfile(settings.optimization.profile);
            settings.validate();
          }
        } else if (bind.startsWith("optimization.") && !"optimization.enabled".equals(bind)
            && !"optimization.autoCompatibility".equals(bind)
            && !"optimization.signTextDistance".equals(bind)
            && !"optimization.itemClusterLimit".equals(bind)
            && !"optimization.nametagDistance".equals(bind)
            && !"optimization.entityDistance".equals(bind)
            && !"optimization.blockEntityDistance".equals(bind)
            && !"optimization.particleDistance".equals(bind)
            && !"optimization.particleLimit".equals(bind)) {
          if (settings.optimization != null) settings.optimization.profile = "custom";
        }
        view.invalidateLayout();
        invalidatePresentation();
        if (bind.startsWith("viewmodel.main.") && !bind.endsWith(".enabled")) {
          settings.viewmodel.enabled = true;
          settings.viewmodel.main.enabled = true;
        }
        if (bind.startsWith("viewmodel.off.") && !bind.endsWith(".enabled")) {
          settings.viewmodel.enabled = true;
          settings.viewmodel.off.enabled = true;
        }
        if ("lowFire.enabled".equals(bind)) {
          if (settings.lowFire.enabled) {
            settings.noRender.enabled = true;
            settings.noRender.fire = true;
          } else {
            settings.noRender.fire = false;
          }
        } else if ("lowFire.offsetY".equals(bind)) {
          settings.lowFire.enabled = true;
          settings.noRender.enabled = true;
          settings.noRender.fire = true;
          settings.noRender.fireOffset = settings.lowFire.offsetY;
        } else if ("lowFire.hidden".equals(bind)) {
          settings.lowFire.enabled = true;
          settings.noRender.enabled = true;
          settings.noRender.fire = true;
          settings.noRender.hideFire = settings.lowFire.hidden;
        } else if ("noRender.enabled".equals(bind) || "noRender.fire".equals(bind)) {
          settings.lowFire.enabled = settings.noRender.enabled && settings.noRender.fire;
        } else if ("noRender.fireOffset".equals(bind)) {
          settings.lowFire.offsetY = settings.noRender.fireOffset;
        } else if ("noRender.hideFire".equals(bind)) {
          settings.lowFire.hidden = settings.noRender.hideFire;
        }
      }

      public Map<String, Object> context() {
        return presentationContext();
      }
    });
    settings.skinDocument = document;
  }

  private SkinDocument resolvedDocument() {
    return DefaultSkin.resolve(
        settings.skin.preset, settings.skin.enabled ? actions.skinDocument() : null);
  }

  private void collectHudTargets(SkinDocument.Node node, Set<String> ids) {
    if (node == null) return;
    if (panel(node.tag) != null || "cooldowns".equals(node.tag) || "crosshair".equals(node.tag))
      ids.add(node.tag);
    for (SkinDocument.Node child : node.children) collectHudTargets(child, ids);
  }

  private Map<String, Object> presentationContext() {
    if (presentationCache != null) return presentationCache;
    if (!editor && menuPresentationCache != null
        && java.util.Objects.equals(menuPresentationLanguage, settings.language)) {
      if (Float.floatToIntBits(menuPresentationEnter) != Float.floatToIntBits(menuEnter)) {
        menuPresentationCache.put("menuEnter", menuEnter);
        menuPresentationEnter = menuEnter;
      }
      if (Float.floatToIntBits(menuPresentationFade) != Float.floatToIntBits(menuFade)) {
        menuPresentationCache.put("menuFade", menuFade);
        menuPresentationFade = menuFade;
      }
      return menuPresentationCache;
    }
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("bindingCapture", capturingBinding);
    values.put("menuBindingLabel", bindingLabel("menu"));
    values.put("zoomBindingLabel", bindingLabel("zoom"));
    values.put("freelookBindingLabel", bindingLabel("freelook"));
    values.put("waypointBindingLabel", bindingLabel("waypoint"));
    values.put("worldMapBindingLabel", bindingLabel("worldMap"));
    values.put("mapSettingsBindingLabel", bindingLabel("mapSettings"));
    values.put("mapEnlargeBindingLabel", bindingLabel("mapEnlarge"));
    values.put("waypointsMenuBindingLabel", bindingLabel("waypointsMenu"));
    values.put("tapeMouseKeyBindingLabel", bindingLabel("tapeMouseKey"));
    values.put("menuEnter", menuEnter);
    values.put("menuFade", menuFade);
    values.put("editorTarget", selected);
    values.put("editorSelectedLabel", name(selected));
    values.put("editorDragging", dragged != null);
    values.put("bindingCaptureModule",
        capturingBinding.startsWith("module:") ? capturingBinding.substring(7) : "");
    values.put("editorActive", editor);

    if (!editor) {
      menuPresentationCache = values;
      menuPresentationLanguage = settings.language;
      menuPresentationEnter = menuEnter;
      menuPresentationFade = menuFade;
      return values;
    }
    boolean compact = width < behavior("compact-width") || height < behavior("compact-height");
    values.put("editorCompact", compact);
    values.put("editorToolbarWidth",
        compact ? Math.min(behavior("toolbar-compact-width"), Math.max(100, width - 24))
                : Math.min(behavior("toolbar-width"), width - 24));
    values.put("editorToolbarHeight",
        compact ? behavior("toolbar-compact-height") : behavior("toolbar-height"));
    values.put("editorToolbarY", compact ? behavior("toolbar-compact-y") : behavior("toolbar-y"));
    values.put("editorToolbarPadding",
        compact ? behavior("toolbar-compact-padding") : behavior("toolbar-padding"));
    values.put(
        "editorToolbarGap", compact ? behavior("toolbar-compact-gap") : behavior("toolbar-gap"));
    Map<String, Object> targets = editorStaticContext();
    values.putAll(targets);
    if (compact) {
      for (int i = 0; i < editorTargetLabelKeys.size(); i++)
        values.put(editorTargetLabelKeys.get(i), "");
      values.put("editorToolbarDoneLabel", "");
    }
    UiCatalog.Control control = scaleControl(selected);
    values.put("editorScaleBind", control == null ? "" : control.bind);
    values.put("editorScaleMin", control == null ? 0 : control.min);
    values.put("editorScaleMax", control == null ? 0 : control.max);
    values.put("editorScaleStep", control == null ? 0 : control.step);
    values.put("editorControlCount", editorControlCount(selected));
    float[] inspector = hudSelected() || "viewmodel".equals(selected) || "lowFire".equals(selected)
        ? inspectorBounds()
        : new float[4];
    values.put("editorInspectorX", inspector[0]);
    values.put("editorInspectorY", inspector[1]);
    values.put("editorInspectorWidth", inspector[2]);
    values.put("editorInspectorHeight", inspector[3]);
    values.put("editorScaleLabel", tr("ui.scale"));
    values.put("editorSnapLabel", tr("ui.snap"));
    values.put("editorDrawLabel", tr("ui.draw_crosshair"));
    values.put("editorStyleLabel", tr("ui.style"));
    Map<String, Object> selection = new LinkedHashMap<String, Object>();
    float[] box = hudSelected() ? bounds(selected) : new float[] {0, 0, 0, 0, 1};
    selection.put("id", selected);
    selection.put("visible", hudSelected() && box[2] > 0 && box[3] > 0);
    selection.put("x", box[0]);
    selection.put("y", box[1]);
    selection.put("width", box[2]);
    selection.put("height", box[3]);
    selection.put("scale", box[4]);
    values.put("selection", Collections.unmodifiableMap(selection));
    Map<String, Object> guides = new LinkedHashMap<String, Object>();
    guides.put("vertical", snapX >= 0);
    guides.put("horizontal", snapY >= 0);
    guides.put("x", snapX);
    guides.put("y", snapY);
    values.put("guides", Collections.unmodifiableMap(guides));
    Map<String, Object> fire = new LinkedHashMap<String, Object>();
    fire.put("visible", actions.fireVisible() && !settings.lowFire.hidden);
    fire.put("offsetY", settings.lowFire.offsetY);
    values.put("fire", Collections.unmodifiableMap(fire));
    values.put("viewmodelGizmo", viewmodelEditor.presentation());
    values.put("viewmodelSelected", viewmodelEditor.isSelected());
    values.put("viewmodelHand", viewmodelEditor.selectedHand());
    values.put("viewmodelActiveHand",
        "off".equals(viewmodelEditor.selectedHand()) ? "off" : "main");
    values.put("viewmodelMode", viewmodelEditor.mode());
    values.put("viewmodelAxis", viewmodelEditor.activeAxis());
    values.put("viewmodelHandBind",
        "viewmodel." + ("off".equals(viewmodelEditor.selectedHand()) ? "off" : "main"));
    values.put("fireVisible", actions.fireVisible());
    presentationCache = values;
    return values;
  }

  private Map<String, Object> editorStaticContext() {
    long policyRev = FeaturePolicy.revision();
    if (editorStaticValues == null || editorStaticDocument != document
        || editorStaticPolicyRevision != policyRev
        || !java.util.Objects.equals(editorStaticLanguage, settings.language)) {
      List<String> ids = new ArrayList<String>();
      for (String id : hudTargets) {
        if (!FeaturePolicy.isWidgetBlocked(id)) ids.add(id);
      }
      if (!FeaturePolicy.isBlocked("viewmodel")) ids.add("viewmodel");
      if (!FeaturePolicy.isBlocked("lowFire")) ids.add("lowFire");
      List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
      List<String> labelKeys = new ArrayList<String>();
      Map<String, Object> values = new HashMap<String, Object>();
      for (String id : ids) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("label", name(id));
        UiCatalog.Module module = catalog.module(moduleId(id));
        row.put("icon", module != null && module.id.equals(moduleId(id)) ? module.icon : "");
        rows.add(Collections.unmodifiableMap(row));
        String key =
            "editorToolbar" + Character.toUpperCase(id.charAt(0)) + id.substring(1) + "Label";
        values.put(key, name(id));
        values.put(key.substring(0, key.length() - 5) + "Tooltip", name(id));
        labelKeys.add(key);
      }
      values.put("editorTargets", Collections.unmodifiableList(rows));
      editorTargetLabelKeys = Collections.unmodifiableList(labelKeys);
      values.put("editorToolbarDoneLabel", tr("ui.done"));
      values.put("editorToolbarDoneTooltip", tr("ui.done"));
      values.put("editorScaleLabel", tr("ui.scale"));
      values.put("editorSnapLabel", tr("ui.snap"));
      values.put("editorDrawLabel", tr("ui.draw_crosshair"));
      values.put("editorStyleLabel", tr("ui.style"));
      editorStaticValues = values;
      editorStaticDocument = document;
      editorStaticLanguage = settings.language;
      editorStaticPolicyRevision = policyRev;
    }
    return editorStaticValues;
  }

  private int editorControlCount(String id) {
    if (!id.equals(derivedCountId) || derivedControlCount < 0) {
      derivedCountId = id;
      derivedControlCount = catalog.editorControls(moduleId(id)).size();
    }
    return derivedControlCount;
  }

  public void resourceReload() {
    invalidatePresentation();
    if (view != null) view.invalidateLayout();
  }

  public void invalidatePresentation() {
    presentationCache = null;
    menuPresentationCache = null;
    editorStaticValues = null;
    editorTargetLabelKeys = Collections.emptyList();
    derivedControlId = derivedCountId = null;
    derivedControlCount = -1;
    derivedScale = null;
  }

  private static String moduleId(String id) {
    return "cooldowns".equals(id) ? "hud" : id;
  }

  private UiCatalog.Control scaleControl(String id) {
    if (!id.equals(derivedControlId)) {
      derivedControlId = id;
      derivedScale = catalog.control(
          "crosshair".equals(id) ? "crosshair.pixelScale" : moduleId(id) + ".scale");
    }
    return derivedScale;
  }

  private float behavior(String name) {
    return styleNumber(document.component("editor.behavior"), "--" + name);
  }

  private float styleNumber(SkinDocument.Node node, String property) {
    if (node == null) return 0;
    String source = document.styles(node, "").get(property);
    if (source == null) return 0;
    source = source.trim();
    if (source.endsWith("px")) source = source.substring(0, source.length() - 2);
    try {
      float value = Float.parseFloat(source);
      return Float.isFinite(value) ? value : 0;
    } catch (NumberFormatException invalid) {
      return 0;
    }
  }

  public void onOpen() {
    waypointOnly = false;
    accountOnly = false;
    openedAt = Double.NaN;
    chatOverlay = false;
    closing = false;
    closeDispatched = false;
    dragged = null;
    invalidatePresentation();
    view.resetInteraction();
    view.resetMotion();
  }

  public void onClose() {
    finishHudPositionDrag();
    waypointOnly = false;
    accountOnly = false;
    openedAt = Double.NaN;
    capturingBinding = "";
    viewmodelEditor.close();
    editor = false;
    chatOverlay = false;
    closing = false;
    dragged = null;
    modifiers = 0;
    invalidatePresentation();
    if (document != resolvedDocument()) {
      refreshSkin();
    } else if (view != null) {
      view.resetInteraction();
      view.onClose();
    }
  }

  public boolean isEditor() {
    return editor;
  }

  public void openEditor() {
    editor = true;
    closing = false;
    selected = "";
    capturingBinding = "";
    dragged = null;
    modifiers = 0;
    snapX = snapY = -1;
    invalidatePresentation();
    view.resetInteraction();
    viewmodelEditor.open();
    view.resetMotion();
  }

  public boolean isTextFocused() {
    return !capturingBinding.isEmpty() || view.isTextFocused();
  }

  public boolean isBindingCapture() {
    return !capturingBinding.isEmpty();
  }

  public boolean captureBinding(int normalizedKey, String nativeKeyName) {
    if (capturingBinding.isEmpty()) return false;
    String id = capturingBinding;
    capturingBinding = "";
    invalidatePresentation();
    String targetKey =
        normalizedKey == Keys.ESCAPE || normalizedKey == Keys.DELETE
            || normalizedKey == Keys.BACKSPACE
            ? "key.keyboard.unknown"
            : nativeKeyName;
    if (id.startsWith("module:")) {
      String moduleId = id.substring(7);
      actions.checkpoint();
      if (targetKey == null || targetKey.isEmpty() || "key.keyboard.unknown".equals(targetKey))
        settings.moduleBindings.remove(moduleId);
      else
        settings.moduleBindings.put(moduleId, targetKey);
      actions.changed();
    } else if ("tapeMouseKey".equals(id)) {
      String clean = targetKey == null || targetKey.isEmpty() ? "key.keyboard.unknown" : targetKey;
      if (settings.tapeMouse == null) settings.tapeMouse = new Settings.TapeMouse();
      actions.checkpoint();
      settings.tapeMouse.customKey = clean;
      actions.changed();
      return true;
    }
    actions.setKeyBinding(id, targetKey);
    return true;
  }

  public float[] elementBounds(String id) {
    float[] bounds = view.elementBounds(id);
    if (bounds != null && canvas != null && !editor) {
      bounds[0] = canvas.x + bounds[0] * canvas.scale;
      bounds[1] = canvas.y + bounds[1] * canvas.scale;
      bounds[2] *= canvas.scale;
      bounds[3] *= canvas.scale;
    }
    return bounds;
  }

  public boolean usesBackdrop() {
    return settings.menu.vanillaBlur || document.usesMenuBackdrop(settings.theme);
  }

  private String storedCustomKey(String id) {
    if ("tapeMouseKey".equals(id))
      return settings.tapeMouse == null ? null : settings.tapeMouse.customKey;
    return null;
  }

  private String bindingLabel(String id) {
    if (id.equals(capturingBinding)) return tr("ui.press_a_key");
    String label = actions.keyBindingLabel(id);
    String stored = storedCustomKey(id);
    if (stored != null) {
      if (stored.isEmpty() || stored.endsWith(".unknown")) return tr("binding.unbound");
      String unboundRu = dev.nightclient.i18n.I18n.text("ru", "binding.unbound");
      String unboundEn = dev.nightclient.i18n.I18n.text("en", "binding.unbound");
      if (label == null || label.isEmpty() || label.equals(unboundRu) || label.equals(unboundEn)) {
        int at = stored.lastIndexOf('.');
        return stored.substring(at + 1).replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
      }
    }
    return label;
  }

  private String tr(String key) {
    if (!java.util.Objects.equals(translationsLanguage, settings.language)) {
      translations.clear();
      translationsLanguage = settings.language;
    }
    String value = translations.get(key);
    if (value == null) {
      value = dev.nightclient.i18n.I18n.text(settings.language, key);
      translations.put(key, value);
    }
    return value;
  }

  private String name(String id) {
    if (id == null || id.isEmpty()) return "";
    if (!java.util.Objects.equals(namesLanguage, settings.language) || namesDocument != document) {
      names.clear();
      namesLanguage = settings.language;
      namesDocument = document;
    }
    String value = names.get(id);
    if (value == null) {
      String key = moduleId(id);
      UiCatalog.Module module = catalog.module(key);
      value = module != null && module.id.equals(key) ? module.label : "";
      names.put(id, value);
    }
    return value;
  }

  private void requestClose() {
    if (chatOverlay) {
      leaveEditor();
      return;
    }
    if (closing) return;
    closing = true;
    closingAt = now;
    if (settings.menu.animationSpeed >= 100000f || settings.theme.motion.exitDuration <= 0)
      dispatchClose();
  }

  private void dispatchClose() {
    if (!closeDispatched) {
      closeDispatched = true;
      actions.close();
    }
  }

  public void leaveEditor() {
    finishHudPositionDrag();
    editor = false;
    chatOverlay = false;
    selected = "";
    viewmodelEditor.close();
    dragged = null;
    modifiers = 0;
    snapX = snapY = -1;
    invalidatePresentation();
    view.resetInteraction();
    view.resetMotion();
  }

  public void renderEditorOverlay(
      Canvas target, int width, int height, float mouseX, float mouseY, double seconds) {
    if (!editor) return;
    chatOverlay = true;
    presentationCache = null;
    now = seconds;
    this.width = width;
    this.height = height;
    this.mouseX = mouseX;
    this.mouseY = mouseY;
    settings.theme.effects.time = seconds;
    if (document != resolvedDocument()) refreshSkin();
    menuEnter = menuFade = 1;
    canvas = new UiCanvas(target, 0, 0, 1, 1, settings.theme);
    renderEditor();
  }

  public void render(
      Canvas target, int width, int height, float mouseX, float mouseY, double seconds) {
    presentationCache = null;
    now = seconds;
    this.width = width;
    this.height = height;
    this.mouseX = mouseX;
    this.mouseY = mouseY;
    settings.theme.effects.time = seconds;
    if (document != resolvedDocument()) refreshSkin();
    if (Double.isNaN(openedAt)) openedAt = seconds;
    float enter = settings.menu.animationSpeed >= 100000f
        ? 1
        : clamp((float) ((seconds - openedAt) * settings.menu.animationSpeed
                    / Math.max(.001, settings.theme.motion.enterDuration)),
              0, 1);
    float rawFade = closing ? 1
            - clamp((float) ((seconds - closingAt) * settings.menu.animationSpeed
                        / Math.max(.001, settings.theme.motion.exitDuration)),
                0, 1)
                            : 1;
    if (closing && rawFade <= 0) {
      dispatchClose();
      return;
    }
    float fade = closing ? 1 - (1 - rawFade) * (1 - rawFade) * (1 - rawFade) : 1;
    menuEnter = enter;
    menuFade = fade;
    view.renderComponent("menu.backdrop", target, 0, 0, width, height, seconds, null);
    if (editor) {
      canvas = new UiCanvas(target, 0, 0, 1, fade, settings.theme);
      renderEditor();
      return;
    }
    float minWidth = styleNumber(document.menu(), "viewport-min-width"),
          minHeight = styleNumber(document.menu(), "viewport-min-height");
    float scale = Math.max(.01f,
        Math.min(settings.menu.scale,
            Math.min(minWidth > 0 ? width / minWidth : settings.menu.scale,
                minHeight > 0 ? height / minHeight : settings.menu.scale)));
    float exitScale = closing && settings.menu.animationSpeed < 100000f ? .97f + .03f * fade : 1f;
    float drawScale = scale * exitScale;
    canvas = new UiCanvas(target, width * (1 - exitScale) * .5f, height * (1 - exitScale) * .5f,
        drawScale, fade, settings.theme);
    if (waypointOnly) {
      if (!view.isWaypointManagerOpen()) {
        if (!closing) requestClose();
        return;
      }
      if (view.renderMenuOverlay("waypoint-manager", canvas, Math.round(width / scale),
              Math.round(height / scale), canvas.localX(mouseX), canvas.localY(mouseY), seconds))
        return;
    }
    if (accountOnly) {
      if (!view.isAccountManagerOpen()) {
        if (!closing) requestClose();
        return;
      }
      if (view.renderMenuOverlay("account-manager", canvas, Math.round(width / scale),
              Math.round(height / scale), canvas.localX(mouseX), canvas.localY(mouseY), seconds))
        return;
    }
    view.renderMenu(canvas, Math.round(width / scale), Math.round(height / scale),
        canvas.localX(mouseX), canvas.localY(mouseY), seconds);
  }

  private Settings.Panel panel(String id) {
    if ("inventory".equals(id)) return settings.inventory;
    if ("tnt".equals(id)) return settings.tnt;
    if ("armor".equals(id)) return settings.armor;
    if ("potions".equals(id)) return settings.potions;
    if ("target".equals(id)) return settings.target;
    if ("scoreboard".equals(id)) return settings.scoreboard;
    if ("keystrokes".equals(id)) return settings.keystrokes;
    if ("coordinates".equals(id)) return settings.coordinates;
    if ("ping".equals(id)) return settings.ping;
    if ("fps".equals(id)) return settings.fps;
    if ("betterChat".equals(id)) return settings.betterChat;
    if ("minimap".equals(id)) return settings.minimap;
    return null;
  }

  private void scale(float value) {
    if ("crosshair".equals(selected))
      settings.crosshair.pixelScale = Math.max(1, Math.min(8, Math.round(value)));
    else {
      if (!"armor".equals(selected) && !"betterChat".equals(selected) && !"minimap".equals(selected)
          && !("target".equals(selected) && "zakohp".equals(settings.target.style)))
        value /= settings.menu.hudScale;
      Settings.Panel p = panel(selected);
      if (p != null)
        p.scale = ("betterChat".equals(selected) || "minimap".equals(selected))
            ? Math.max(.5f, Math.min(2f, value))
            : Math.max(.1f, Math.min(3, value));
      else
        settings.hud.scale = Math.max(.1f, Math.min(2, value));
    }
    settings.validate();
    view.invalidateLayout();
    if (dragged != null)
      actions.changedLive();
    else
      actions.changed();
  }

  private float[] bounds(String id) {
    float[] measured = view.hudBounds(id, width, height, now, true);
    return measured == null ? new float[] {0, 0, 0, 0, 1} : measured;
  }

  private void renderEditor() {
    boolean overControls = view.hasInteractiveHit(mouseX, mouseY);
    handles.clear();
    view.beginFrame(mouseX, mouseY, now);
    if ("viewmodel".equals(selected) && !viewmodelEditor.isSelected())
      viewmodelEditor.ensureHandSelected();
    viewmodelEditor.update(view, now, mouseX, mouseY);
    if (overControls) actions.hoverViewmodel(Float.NaN, Float.NaN);
    viewmodelEditor.renderOutline(view, canvas, width, height);
    view.renderHudEditor(canvas, width, height, now);
    ExtendedHudRenderer.renderFood(canvas, settings, actions.vitals(), width, height);
    viewmodelEditor.render(view, canvas, width, height, now);
    for (int pass = 0; pass < 2; pass++) {
      for (final String id : hudTargets) {
        if (FeaturePolicy.isWidgetBlocked(id)) continue;
        boolean isSel = id.equals(selected);
        if ((pass == 0 && isSel) || (pass == 1 && !isSel)) continue;
        final float[] b = bounds(id);
        if (b[2] <= 0 || b[3] <= 0) continue;
        handles.add(new Handle(b[0], b[1], b[2], b[3],
            new Runnable() {
              public void run() {
                selected = id;
                viewmodelEditor.clearSelection();
                actions.checkpoint();
                dragX = MenuController.this.mouseX - b[0];
                dragY = MenuController.this.mouseY - b[1];
                if (!"crosshair".equals(id)) {
                  hudPositionDrag = id;
                  hudDragStartX = hudDragLastX = b[0];
                  hudDragStartY = hudDragLastY = b[1];
                  view.hudPlacementDrag(id, b[0], b[1]);
                }
              }
            },
            new Gesture() {
              public void move(float x, float y) {
                moveHud(x - dragX, y - dragY);
              }
            }));
      }
    }
    view.renderComponent("editor.selection", canvas, 0, 0, width, height, now, null);
    view.renderComponent("editor.guides", canvas, 0, 0, width, height, now, null);
    if (hudSelected()) {
      final float[] b = bounds(selected), handle = view.layoutBounds("editor-resize-handle");
      if (handle != null && handle[2] > 0 && handle[3] > 0)
        handles.add(new Handle(handle[0], handle[1], handle[2], handle[3],
            new Runnable() {
              public void run() {
                actions.checkpoint();
                resizeStart = b.clone();
                startScale = b[4];
              }
            },
            new Gesture() {
              public void move(float x, float y) {
                float ratio = Math.max((x - resizeStart[0]) / Math.max(1, resizeStart[2]),
                    (y - resizeStart[1]) / Math.max(1, resizeStart[3]));
                scale(startScale * ratio);
                if (!"crosshair".equals(selected)) moveHud(resizeStart[0], resizeStart[1], false);
              }
            }));
    }
    view.renderComponent("editor.chrome", canvas, 0, 0, width, height, now, null);
    if (hudSelected() && dragged == null)
      view.renderComponent("editor.inspector", canvas, 0, 0, width, height, now, null);
    if ("viewmodel".equals(selected))
      view.renderComponent("editor.viewmodel", canvas, 0, 0, width, height, now, null);
    if ("lowFire".equals(selected)) {
      if (actions.fireVisible() && !settings.lowFire.hidden) {
        view.renderComponent("editor.fire-indicator", canvas, 0, 0, width, height, now, null);
        final float initial = settings.lowFire.offsetY;
        float[] region = view.layoutBounds("editor-fire-drag-region");
        if (region != null)
          handles.add(new Handle(region[0], region[1], region[2], region[3],
              new Runnable() {
                public void run() {
                  actions.checkpoint();
                  dragY = MenuController.this.mouseY;
                }
              },
              new Gesture() {
                public void move(float x, float y) {
                  settings.lowFire.enabled = true;
                  settings.lowFire.offsetY =
                      clamp(initial + (y - dragY) * 2 / Math.max(1, height), 0, 1.5f);
                  settings.noRender.fireOffset = settings.lowFire.offsetY;
                  settings.validate();
                  view.invalidateLayout();
                  actions.changedLive();
                }
              }));
      }
      view.renderComponent("editor.lowfire", canvas, 0, 0, width, height, now, null);
    }
    view.finishFrame(canvas, width, height);
  }

  private boolean hudSelected() {
    return hudTargets.contains(selected);
  }

  private float[] inspectorBounds() {
    float inset = behavior("inspector-inset"), minimum = behavior("inspector-min-size"),
          top = Math.min(behavior("inspector-top"), Math.max(inset, height - 36));
    float w = Math.min("viewmodel".equals(selected) ? behavior("viewmodel-inspector-width")
                                                      : behavior("inspector-width"),
        Math.max(minimum, width - 2 * inset));
    int controls = editorControlCount(selected);
    float natural = "viewmodel".equals(selected) ? behavior("viewmodel-inspector-height")
        : "lowFire".equals(selected)
        ? behavior("fire-inspector-height")
        : behavior("inspector-base-height") + controls * behavior("inspector-row-height");
    float h = Math.min(Math.min(behavior("inspector-max-height"), natural),
        Math.max(minimum, height - top - inset));
    float right = Math.max(inset, width - w - inset), bottom = Math.max(top, height - h - inset);
    float middleX = Math.max(inset, (width - w) / 2), middleY = Math.max(top, (top + bottom) / 2);
    float[][] candidates = {{right, middleY}, {inset, middleY}, {right, top}, {inset, top},
        {right, bottom}, {inset, bottom}, {middleX, top}, {middleX, bottom}};
    float bestScore = Float.POSITIVE_INFINITY;
    float[] best = candidates[0];
    List<float[]> occupied = new ArrayList<float[]>(hudTargets.size());
    for (String id : hudTargets) occupied.add(bounds(id));
    for (float[] point : candidates) {
      float score = 0;
      for (int i = 0; i < hudTargets.size(); i++) {
        String id = hudTargets.get(i);
        float[] b = occupied.get(i);
        float overlapW =
            Math.max(0, Math.min(point[0] + w, b[0] + b[2] + 8) - Math.max(point[0], b[0] - 8));
        float overlapH =
            Math.max(0, Math.min(point[1] + h, b[1] + b[3] + 8) - Math.max(point[1], b[1] - 8));
        score += overlapW * overlapH * (id.equals(selected) ? 100 : 1);
      }
      if (score < bestScore) {
        bestScore = score;
        best = point;
      }
    }
    return new float[] {best[0], best[1], w, h};
  }

  private static final String[] ORDERED_HUD = {"cooldowns", "inventory", "tnt", "armor", "potions",
      "target", "scoreboard", "keystrokes", "coordinates", "ping", "fps"};
  private int hudOrder(String id) {
    return "cooldowns".equals(id) ? settings.hud.layoutOrder : panel(id).layoutOrder;
  }
  private void hudOrder(String id, int value) {
    if ("cooldowns".equals(id))
      settings.hud.layoutOrder = value;
    else
      panel(id).layoutOrder = value;
  }
  private void promoteHudOrder(String targetId) {
    if (targetId == null || targetId.isEmpty() || "crosshair".equals(targetId)) return;
    if ("betterChat".equals(targetId)) {
      settings.betterChat.layoutOrder = Math.max(1, settings.betterChat.layoutOrder);
      return;
    }
    if ("minimap".equals(targetId)) {
      settings.minimap.layoutOrder = Math.max(1, settings.minimap.layoutOrder);
      return;
    }
    int maximum = 0;
    for (String id : ORDERED_HUD) maximum = Math.max(maximum, hudOrder(id));
    if (maximum == Integer.MAX_VALUE) {
      java.util.List<String> order =
          new java.util.ArrayList<String>(java.util.Arrays.asList(ORDERED_HUD));
      java.util.Collections.sort(order, (a, b) -> Integer.compare(hudOrder(a), hudOrder(b)));
      for (int i = 0; i < order.size(); i++) hudOrder(order.get(i), i + 1);
      maximum = order.size();
    }
    hudOrder(targetId, maximum + 1);
  }
  private void finishHudPositionDrag() {
    if (!hudPositionDrag.isEmpty()
        && (Math.abs(hudDragLastX - hudDragStartX) > .01f
            || Math.abs(hudDragLastY - hudDragStartY) > .01f)) {
      promoteHudOrder(hudPositionDrag);
      settings.validate();
      actions.changed();
    }
    hudPositionDrag = "";
    if (view != null) view.hudPlacementDragEnd();
  }

  private void moveHud(float x, float y) {
    moveHud(x, y, true);
  }

  private void moveHud(float x, float y, boolean magnetic) {
    float[] b = bounds(selected);
    float inset = behavior("edge-inset");
    x = clamp(x, inset, Math.max(inset, width - b[2] - inset));
    y = clamp(y, inset, Math.max(inset, height - b[3] - inset));
    snapX = snapY = -1;
    if (settings.hud.snap && magnetic) {
      float snapInset = behavior("snap-inset"), distance = behavior("snap-distance");
      float bestDx = distance, bestDy = distance;
      float targetX = x, targetY = y;
      float[] xs = {snapInset, (width - b[2]) / 2, width - b[2] - snapInset},
              ys = {snapInset, (height - b[3]) / 2, height - b[3] - snapInset};
      for (int i = 0; i < 3; i++) {
        float dx = Math.abs(x - xs[i]);
        if (dx < bestDx) {
          bestDx = dx;
          targetX = xs[i];
          snapX = targetX + b[2] / 2;
        }
        float dy = Math.abs(y - ys[i]);
        if (dy < bestDy) {
          bestDy = dy;
          targetY = ys[i];
          snapY = targetY + b[3] / 2;
        }
      }
      if (!"crosshair".equals(selected)) {
        for (int i = 0; i < hudTargets.size(); i++) {
          String otherId = hudTargets.get(i);
          if (otherId.equals(selected) || "crosshair".equals(otherId)) continue;
          float[] o = bounds(otherId);
          if (o[2] <= 0 || o[3] <= 0) continue;
          boolean nearY = y <= o[1] + o[3] + 48 && y + b[3] >= o[1] - 48;
          if (nearY) {
            float[] cxs = {o[0] - b[2], o[0] + o[2], o[0], o[0] + o[2] - b[2]};
            float[] gxs = {o[0], o[0] + o[2], o[0], o[0] + o[2]};
            for (int k = 0; k < cxs.length; k++) {
              float cx = clamp(cxs[k], inset, Math.max(inset, width - b[2] - inset));
              float dx = Math.abs(x - cx);
              if (dx < bestDx) {
                bestDx = dx;
                targetX = cx;
                snapX = gxs[k];
              }
            }
          }
          boolean nearX = x <= o[0] + o[2] + 48 && x + b[2] >= o[0] - 48;
          if (nearX) {
            float[] cys = {o[1] - b[3], o[1] + o[3], o[1], o[1] + o[3] - b[3]};
            float[] gys = {o[1], o[1] + o[3], o[1], o[1] + o[3]};
            for (int k = 0; k < cys.length; k++) {
              float cy = clamp(cys[k], inset, Math.max(inset, height - b[3] - inset));
              float dy = Math.abs(y - cy);
              if (dy < bestDy) {
                bestDy = dy;
                targetY = cy;
                snapY = gys[k];
              }
            }
          }
        }
      }
      x = targetX;
      y = targetY;
    }
    if (!hudPositionDrag.isEmpty()) {
      hudDragLastX = x;
      hudDragLastY = y;
      view.hudPlacementDrag(hudPositionDrag, x, y);
    }
    if ("crosshair".equals(selected)) {
      settings.crosshair.offsetX += Math.round(x - b[0]);
      settings.crosshair.offsetY += Math.round(y - b[1]);
    } else if ("target".equals(selected) && "zakohp".equals(settings.target.style)) {
      settings.target.offsetX = x + b[2] / 2 - width / 2f;
      settings.target.offsetY = y - height / 2f - 4 + 64;
    } else {
      Settings.Panel p = panel(selected);
      if (p != null) {
        p.scale = ("armor".equals(selected) || "betterChat".equals(selected)
            || "minimap".equals(selected))
            ? b[4]
            : b[4] / settings.menu.hudScale;
        p.offsetX = x - p.anchorX * (width - b[2]);
        p.offsetY = y - p.anchorY * (height - b[3]);
      } else {
        settings.hud.scale = b[4] / settings.menu.hudScale;
        settings.hud.offsetX = x - settings.hud.anchorX * (width - b[2]);
        settings.hud.offsetY = y - settings.hud.anchorY * (height - b[3]);
      }
    }
    settings.validate();
    view.invalidateLayout();
    if (dragged != null)
      actions.changedLive();
    else
      actions.changed();
  }

  public boolean mouseClicked(double x, double y, int button) {
    if (closing) return true;
    if (editor && viewmodelEditor.active())
      return viewmodelEditor.mouseDown((float) x, (float) y, button, modifiers);
    mouseX = (float) x;
    mouseY = (float) y;
    float localX = editor ? (float) x
        : canvas == null  ? (float) x
                          : canvas.localX(x),
          localY = editor ? (float) y
        : canvas == null  ? (float) y
                          : canvas.localY(y);
    if (view.mouseClicked(localX, localY, button)) return true;
    if (!editor) return false;
    if (button == 0)
      for (int index = handles.size() - 1; index >= 0; index--) {
        Handle handle = handles.get(index);
        if (handle.contains((float) x, (float) y)) {
          handle.click.run();
          dragged = handle;
          presentationCache = null;
          return true;
        }
      }
    if (viewmodelEditor.mouseDown((float) x, (float) y, button, modifiers)) {
      selected = "viewmodel";
      return true;
    }
    if (button == 0) selected = "";
    return false;
  }

  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (closing) return true;
    if (editor && viewmodelEditor.active())
      return viewmodelEditor.mouseDrag((float) x, (float) y, modifiers);
    mouseX = (float) x;
    mouseY = (float) y;
    float localX = editor ? (float) x
        : canvas == null  ? (float) x
                          : canvas.localX(x),
          localY = editor ? (float) y
        : canvas == null  ? (float) y
                          : canvas.localY(y);
    if (view.mouseDragged(localX, localY, button, dx, dy)) return true;
    if (!editor) return false;
    if (viewmodelEditor.mouseDrag((float) x, (float) y, modifiers)) return true;
    if (dragged != null && button == 0) {
      dragged.gesture.move((float) x, (float) y);
      return true;
    }
    return false;
  }

  public boolean mouseReleased(double x, double y, int button) {
    float localX = editor ? (float) x
        : canvas == null  ? (float) x
                          : canvas.localX(x),
          localY = editor ? (float) y
        : canvas == null  ? (float) y
                          : canvas.localY(y);
    boolean uiUsed = view.mouseReleased(localX, localY, button);
    boolean modelUsed = editor && viewmodelEditor.mouseUp(button);
    boolean dragEnded = button == 0 && dragged != null;
    boolean used = uiUsed || modelUsed || dragEnded;
    if (button == 0) {
      finishHudPositionDrag();
      if (dragEnded) actions.changed();
      dragged = null;
      snapX = snapY = -1;
      presentationCache = null;
    }
    return used;
  }

  public boolean mouseScrolled(double x, double y, double amount) {
    return mouseScrolled(x, y, amount, modifiers);
  }

  public boolean mouseScrolled(double x, double y, double amount, int modifiers) {
    if (closing || editor && viewmodelEditor.active()) return true;
    float localX = editor ? (float) x
        : canvas == null  ? (float) x
                          : canvas.localX(x),
          localY = editor ? (float) y
        : canvas == null  ? (float) y
                          : canvas.localY(y);
    if (view.mouseScrolled(localX, localY, amount)) return true;
    if (editor && "viewmodel".equals(selected) && !isTextFocused()
        && viewmodelEditor.mouseScrolled(amount, modifiers)) {
      invalidatePresentation();
      return true;
    }
    if (editor && hudSelected() && amount != 0) {
      float[] before = bounds(selected);
      float effective = before[4];
      actions.checkpoint();
      scale(effective
          + (float) amount * ("crosshair".equals(selected) ? 1 : behavior("scale-wheel-step")));
      if (!"crosshair".equals(selected)) moveHud(before[0], before[1], false);
      return true;
    }
    return false;
  }

  public boolean keyPressed(int key, int modifiers) {
    this.modifiers = modifiers;
    if (closing) return true;
    if (key == Keys.F5) {
      actions.reloadSkin();
      return true;
    }
    if (editor && viewmodelEditor.active() && viewmodelEditor.key(key, modifiers)) {
      invalidatePresentation();
      return true;
    }
    if (view.keyPressed(key, 0, modifiers)) return true;
    if (editor && !isTextFocused() && !hudSelected() && !"lowFire".equals(selected)) {
      if (!viewmodelEditor.isSelected()
          && (ViewmodelEditor.isHotkey(key) || "viewmodel".equals(selected)))
        viewmodelEditor.ensureHandSelected();
      if (viewmodelEditor.key(key, modifiers)) {
        selected = "viewmodel";
        invalidatePresentation();
        return true;
      }
    }
    if (key == Keys.ESCAPE) {
      if (editor)
        leaveEditor();
      else
        requestClose();
      return true;
    }
    if ((modifiers & Keys.MOD_CONTROL) != 0 && key == 'Z') {
      if (actions.canUndo()) {
        actions.undo();
        invalidatePresentation();
      }
      return true;
    }
    if (editor && hudSelected()
        && (key == Keys.LEFT || key == Keys.RIGHT || key == Keys.UP || key == Keys.DOWN)) {
      actions.checkpoint();
      float[] b = bounds(selected);
      promoteHudOrder(selected);
      float step = (modifiers & Keys.MOD_SHIFT) != 0 ? behavior("keyboard-fast-step") : 1;
      moveHud(b[0]
              + (key == Keys.LEFT         ? -step
                      : key == Keys.RIGHT ? step
                                          : 0),
          b[1]
              + (key == Keys.UP          ? -step
                      : key == Keys.DOWN ? step
                                         : 0),
          false);
      return true;
    }
    return false;
  }

  public boolean keyReleased(int key, int modifiers) {
    this.modifiers = modifiers;
    if (key == 340 || key == 344) this.modifiers &= ~Keys.MOD_SHIFT;
    if (key == 341 || key == 345) this.modifiers &= ~Keys.MOD_CONTROL;
    if (key == 342 || key == 346) this.modifiers &= ~Keys.MOD_ALT;
    if (key == 343 || key == 347) this.modifiers &= ~Keys.MOD_SUPER;
    if (editor) viewmodelEditor.modifiers(this.modifiers);
    return editor && viewmodelEditor.active();
  }

  public boolean charTyped(int codePoint) {
    if (closing) return true;
    if (!capturingBinding.isEmpty()) return true;
    if (editor && viewmodelEditor.active()) return true;
    return view.charTyped(codePoint, 0);
  }

  private static float clamp(float value, float min, float max) {
    return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
  }
}
