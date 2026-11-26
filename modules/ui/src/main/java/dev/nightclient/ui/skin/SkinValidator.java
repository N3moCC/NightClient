package dev.nightclient.ui.skin;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import dev.nightclient.renderer.Icons;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.SkinManager;
import dev.nightclient.skin.NcExpression;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SkinValidator implements SkinManager.Validator {
  private static final Set<String> ACTIONS = new HashSet<String>(Arrays.asList("close", "reset",
      "theme-reset", "undo", "reload-skin", "activate", "component", "language", "color-close",
      "create-examples", "profile-save", "profile-export", "profile-import",
      "profile-delete-confirm", "profile-delete-cancel", "editor-open", "editor-done",
      "editor-crosshair", "pixel-clear", "pixel-copy-to-target", "viewmodel-reset", "viewmodel-reset-all", "waypoint-here",
      "waypoint-manager-open", "waypoint-manager-close", "waypoint-manager-noop",
      "waypoint-create-open", "waypoint-shuffle-color", "waypoint-delete-all",
      "waypoint-delete-cancel", "waypoint-apply", "waypoint-cancel", "module-back",
      "cosmetics-open", "cosmetics-close", "cosmetics-reset", "cosmetics-auto",
      "cosmetics-cape-reset", "cosmetics-cape-none", "cosmetics-cape-browse", "cosmetics-cape-path",
      "cosmetics-skin-nick", "cosmetics-skin-browse", "cosmetics-skin-path", "cosmetics-skin-reset",
      "cosmetics-weapon-reset", "cosmetics-weapon-browse", "cosmetics-weapon-path",
      "cosmetics-armor-reset", "cosmetics-armor-browse", "cosmetics-armor-path",
      "chat-hud-edit", "world-map-open", "map-settings-open", "waypoints-menu-open",
      "map-clear-world-cache", "map-clear-all-cache",
      "account-manager-open", "account-manager-close", "account-manager-noop",
      "account-layout-toggle", "account-layout-modern", "account-layout-classic",
      "account-launcher-select", "account-launcher-restore", "account-reconnect",
      "account-create-open", "account-random-nick", "account-quick-switch",
      "account-switch-selected", "account-edit-selected", "account-delete-selected",
      "account-apply", "account-cancel",
      "hw-events-refresh", "sound-block-add-draft", "sound-block-add-crosshair",
      "sound-block-clear", "sound-test-tnt", "sound-test-mention", "sound-test-effect",
      "sound-tnt-fuse-mp3-browse", "sound-tnt-fuse-mp3-clear",
      "sound-tnt-explode-mp3-browse", "sound-tnt-explode-mp3-clear"));

  private static boolean isRaster(String icon) {
    return icon.startsWith("nightclient:") || icon.endsWith(".png");
  }

  private static final Set<String> SOURCES = new HashSet<String>(
      Arrays.asList("cooldowns", "visibleCooldowns", "inventory", "tnt", "armor", "potions",
          "vitals", "target", "modules", "categoryModules", "filteredModules", "renderModules",
          "playerModules", "hudModules", "filteredHudModules", "controls", "styleControls",
          "allStyleControls", "choiceOptions", "targetEquipment", "menuControls", "editorControls",
          "profiles", "savedWaypoints", "savedAccounts", "officialCapes", "cosmeticWeapons",
          "cosmeticWeaponMaterials", "cosmeticArmors",
          "hwEvents", "mutedBlocks", "popularSoundBlocks",
          "cooldownRows", "inventoryCells", "tntRows",
          "armorSlots", "potionRows", "targetRows", "scoreboardData.lines",
          "editorTargets", "viewmodelGizmo.segments", "viewmodelGizmo.axes",
          "viewmodelGizmo.labels"));
  private static final java.util.regex.Pattern BOOLEAN_TEXT =
      java.util.regex.Pattern.compile("true|false");
  private static final java.util.regex.Pattern COMMIT_MODE =
      java.util.regex.Pattern.compile("live|release");
  private static final java.util.regex.Pattern DISPLAY_FORMAT =
      java.util.regex.Pattern.compile("fixed:[0-3]");
  private static final java.util.regex.Pattern ALIAS =
      java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,31}");
  private static final java.util.regex.Pattern STATE_NAME =
      java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");
  private static final java.util.regex.Pattern SET_SCALAR =
      java.util.regex.Pattern.compile("(?:#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?|-?[0-9]+(?:\\.[0-9]+)?"
                                      + "|true|false|[A-Za-z0-9_.-]{1,128})");
  private static final java.util.regex.Pattern DYNAMIC_ACTION = java.util.regex.Pattern.compile(
      "(?:page|module|open-module|toggle-module|bind-module|editor-target|profile-load|profile-"
      + "delete|bind-key|viewmodel-hand|viewmodel-mode|viewmodel-axis|waypoint-edit|waypoint-"
      + "remove|waypoint-visibility|waypoint-bind-world|cosmetics-cape-select|"
      + "cosmetics-weapon-select|cosmetics-weapon-material|cosmetics-armor-select|account-select|account-switch|"
      + "account-pin|account-edit|account-remove|account-copy|hw-event-join|sound-block-toggle|"
      + "sound-block-remove):.*");
  private static final java.util.regex.Pattern STATIC_ACTION = java.util.regex.Pattern.compile(
      "(?:page|module|open-module|toggle-module|bind-module|editor-target):[A-Za-z_][A-Za-z0-9_-]{"
      + "0,47}|bind-key:(?:menu|zoom|freelook|waypoint|worldMap|mapSettings|mapEnlarge|waypointsMenu|tapeMouseKey)|viewmodel-hand:(?:main|off)|viewmodel-mode:"
      + "[GRS]|viewmodel-axis:[XYZ]|"
      + "pixel-preset:(?:vanilla|dot|cross|x|ring|diamond|brackets|arrows)|pixel-size:(?:15|21|31)|"
      + "pixel-layer:(?:normal|target)|profile-(?:load|"
      + "delete):.{1,64}|waypoint-(?:edit|remove|visibility|bind-world):[0-9a-f]{1,32}|"
      + "account-(?:select|switch|pin|edit|remove|copy):[0-9a-f]{1,32}|"
      + "hw-event-join:[A-Za-z0-9_.:-]{1,96}|sound-block-(?:toggle|remove):[a-z0-9_]{1,64}|"
      + "cosmetics-cape-select:"
      + "[A-Za-z0-9_-]{1,48}|cosmetics-skin-model:(?:auto|classic|slim)|cosmetics-weapon-select:"
      + "[A-Za-z0-9_-]{1,48}|cosmetics-weapon-material:[A-Za-z0-9_-]{1,48}|cosmetics-weapon-target:(?:swords|weapons|tools|all)|"
      + "cosmetics-weapon-color:#?[0-9A-Fa-f]{6,8}|cosmetics-armor-select:[A-Za-z0-9_-]{1,48}|"
      + "cosmetics-armor-trigger:(?:always|equipped)|cosmetics-armor-color:#?[0-9A-Fa-f]{6,8}");
  private static final java.util.regex.Pattern HEX_COLOR =
      java.util.regex.Pattern.compile("#[0-9A-Fa-f]{3,8}");
  private static final java.util.regex.Pattern VARIABLE_NAME =
      java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*){0,7}");
  private static final java.util.regex.Pattern UI_PROPERTY = java.util.regex.Pattern.compile(
      "ui\\.(?:profileName|deleteProfile|brushColor|crosshairLayer|moreStyle|waypointEdit|waypointName|"
      + "waypointIcon|waypointManager|waypointDraftId|waypointDraftName|waypointDraftX|"
      + "waypointDraftY|waypointDraftZ|waypointDraftColor|waypointDraftDimension|"
      + "waypointScope|waypointDeleteConfirm|cosmeticsTab|cosmeticsCapeFilter|cosmeticsCapeQuery|"
      + "cosmeticsNickDraft|cosmeticsCapePathDraft|cosmeticsSkinPathDraft|"
      + "cosmeticsWeaponPathDraft|cosmeticsArmorPathDraft|accountManager|accountSearch|"
      + "accountSelectedId|accountDraftId|accountDraftName|accountDraftNote|accountDraftSkin|"
      + "soundBlockDraft)");
  private static final java.util.regex.Pattern FIELD_NAME =
      java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
  private static final java.util.regex.Pattern TYPE_PATH =
      java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*){0,3}");
  private static final java.util.regex.Pattern BINDING_FORMAT = java.util.regex.Pattern.compile(
      "fixed:[0-3]|decimal:[0-3]|integer|duration|clock|cooldown-duration|effect-duration");
  private static final java.util.regex.Pattern ITEM_ID =
      java.util.regex.Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
  private static final Set<String> HUD_SPECIAL =
      new HashSet<String>(Arrays.asList("cooldownRowHeight", "cooldownGap", "cooldownPadding",
          "cooldownLineHeight", "cooldownIconSize", "cooldownFontSize", "cooldownTimeWidth",
          "cooldownProgressHeight", "cooldownVisible", "cooldownDirection", "cooldownCellWidth",
          "cooldownX", "cooldownY", "cooldownWidth", "cooldownHeight", "cooldownCount",
          "cooldownsEmpty", "noModules", "searchLabel", "backLabel", "noResultsLabel"));

  private static final Set<String> CONTEXT = new HashSet<String>(Arrays.asList("page", "width",
      "height", "time", "gameVersion", "saveStatus", "index", "label", "chosenLabel", "tooltipText",
      "options", "optionLabels", "editorDragging", "active", "moduleEnabled", "value", "valueScale",
      "valueOffset", "min", "max", "step", "ratio", "targetRatio", "leftMainArm", "previewAuto",
      "previewHasCape", "previewZoomLabel", "previewBackPage", "unit", "crosshairVisible",
      "selectedModule", "selectedHud", "selectedLabel", "selectedBind", "selectedSummary",
      "selectedIcon", "themeFileLabel", "vanillaLabel", "brushLabel", "navigationWidth",
      "brandWidth", "skinStatus", "canUndo", "profilePending", "waypointPending", "waypointStandalone", "markersEmpty",
      "currentWorldId", "currentWorldLabel", "waypointInWorld",
      "colorBind", "colorLabel", "featuresLabel", "styleLabel", "profilesLabel", "editorLabel",
      "doneLabel", "languageLabel", "saveLabel", "loadLabel", "deleteLabel", "cancelLabel",
      "reloadLabel", "examplesLabel", "customLabel", "undoLabel", "resetLabel", "copyLabel",
      "pasteLabel", "emptyProfilesLabel", "profilesEmpty", "editorCompact", "editorToolbarWidth",
      "editorToolbarHeight", "editorToolbarPadding", "editorToolbarGap",
      "editorToolbarCooldownsLabel", "editorToolbarCooldownsTooltip", "editorToolbarInventoryLabel",
      "editorToolbarInventoryTooltip", "editorToolbarCrosshairLabel",
      "editorToolbarCrosshairTooltip", "editorToolbarTntLabel", "editorToolbarTntTooltip",
      "editorToolbarViewmodelLabel", "editorToolbarViewmodelTooltip", "editorToolbarDoneLabel",
      "editorToolbarDoneTooltip", "editorTarget", "editorSelectedLabel", "editorScaleBind",
      "editorScaleMin", "editorScaleMax", "editorScaleStep", "editorInspectorX", "editorInspectorY",
      "editorInspectorWidth", "editorInspectorHeight", "editorScaleLabel", "editorSnapLabel",
      "editorDrawLabel", "editorStyleLabel", "pixelGridHeight", "elementId", "bind", "action",
      "menuPadding", "menuGap", "moduleListWidth", "colorPopupX", "colorPopupY", "colorPopupWidth",
      "colorPopupHeight", "controlsLabel", "bindingsLabel", "menuBindingTitle", "menuSettingsLabel",
      "bindingHint", "bindingCapture", "bindingCaptureModule", "menuBindingLabel",
      "zoomBindingLabel",
      "freelookBindingLabel", "waypointBindingLabel", "worldMapBindingLabel",
      "mapSettingsBindingLabel", "mapEnlargeBindingLabel", "waypointsMenuBindingLabel",
      "tapeMouseKeyBindingLabel",
      "hwEventsEmpty", "hwEventsLoading", "hwEventsSummary", "soundMutedEmpty",
      "tntFuseMp3Loaded", "tntExplodeMp3Loaded", "tntFuseMp3Label", "tntExplodeMp3Label",
      "editorToolbarMinimapLabel", "editorToolbarMinimapTooltip",
      "colorPopupPadding", "colorPopupGap",
      "colorTitleHeight", "colorStripHeight", "colorFieldHeight", "colorDoneHeight",
      "moreStyleLabel", "alphaLabel", "vanillaBlurLabel", "viewmodelRotationLabel",
      "viewmodelModeLabel", "viewmodelResetLabel", "viewmodelPickLabel", "viewmodelSelected",
      "viewmodelHand", "viewmodelActiveHand", "viewmodelMode", "viewmodelAxis",
      "viewmodelHandBind", "editorToolbarArmorLabel", "editorToolbarArmorTooltip",
      "editorToolbarPotionsLabel", "editorToolbarPotionsTooltip", "editorToolbarFoodLabel",
      "editorToolbarFoodTooltip", "editorToolbarTargetLabel", "editorToolbarTargetTooltip",
      "editorToolbarLowFireLabel", "editorToolbarLowFireTooltip", "editorToolbarY", "fireVisible",
      "lowFireDragLabel", "lowFireAbsentLabel", "lowFireHiddenLabel", "lowFireHeightLabel",
      "cosmeticsSkinSummary", "cosmeticsCapeSummary", "cosmeticsWeaponSummary",
      "cosmeticsArmorSummary", "cosmeticsError", "cosmeticsLoading", "cosmeticsCapesEmpty",
      "officialCapesEnabled",
      "accountStandalone", "accountActiveName", "accountActiveSkin", "accountIsLauncher",
      "accountLauncherName", "accountLauncherSkin", "accountSelectedIsLauncher",
      "accountSelectedActive", "accountSelectedName", "accountSelectedSkin",
      "accountSelectedMeta", "accountCanReconnect", "accountServerLabel", "accountError",
      "accountsEmpty", "accountsSearchEmpty", "accountsCount"));
  private static final Set<String> HUD_CONTEXT = new HashSet<String>(Arrays.asList("hudEditing",
      "cooldownCount", "tntCount", "potionCount", "armorEmpty", "targetVisible", "colorAnchorX",
      "colorAnchorY", "colorAnchorWidth", "colorAnchorHeight", "mouseX", "mouseY", "colorOpen",
      "bindingOpen", "elementWidth", "elementHeight", "boxWidth", "boxHeight", "viewportWidth",
      "viewportHeight", "editorControlCount", "menuEnter", "menuFade", "chatEditorActive"));
  private static final Set<String> ITEM_FIELDS = new HashSet<String>(Arrays.asList("slot", "slotId",
      "occupied", "itemId", "name", "count", "stacked", "damageable", "fraction",
      "remainingPercent", "remainingDurability", "row", "column", "hotbar", "index"));
  private static final Map<Class<?>, Set<String>> MAP_FIELDS = new HashMap<Class<?>, Set<String>>();

  private static final class ChoiceOption {}

  private static final class CooldownRow {}

  private static final class InventoryCell {}

  private static final class TntRow {}

  private static final class MarkerRow {}

  private static final class ArmorSlot {}

  private static final class PotionRow {}

  private static final class TargetRow {}

  private static final class EditorTarget {}

  private static final class GizmoSegment {}

  private static final class GizmoAxis {}

  private static final class GizmoLabel {}

  private static final class ScoreboardLine {}

  private static final class OfficialCapeRow {}

  private static final class CosmeticModelRow {}

  private static final class CosmeticMaterialRow {}

  private static final class AccountRow {}

  private static final class HwEventRow {}

  private static final class SoundBlockRow {}

  static {
    MAP_FIELDS.put(ChoiceOption.class,
        new HashSet<String>(Arrays.asList("value", "label", "selected", "index")));
    MAP_FIELDS.put(EditorTarget.class, new HashSet<String>(Arrays.asList("id", "label", "icon")));
    MAP_FIELDS.put(GizmoSegment.class,
        new HashSet<String>(
            Arrays.asList("axis", "selected", "hovered", "behind", "x1", "y1", "x2", "y2")));
    MAP_FIELDS.put(GizmoAxis.class,
        new HashSet<String>(
            Arrays.asList("axis", "selected", "hovered", "x", "y", "unitX", "unitY")));
    MAP_FIELDS.put(GizmoLabel.class,
        new HashSet<String>(Arrays.asList("axis", "selected", "hovered", "x", "y")));
    MAP_FIELDS.put(Map.class, new HashSet<String>(Arrays.asList("name")));
    MAP_FIELDS.put(CooldownRow.class,
        new HashSet<String>(Arrays.asList(
            "key", "itemId", "name", "remainingSeconds", "durationSeconds", "fraction")));
    MAP_FIELDS.put(InventoryCell.class, ITEM_FIELDS);
    MAP_FIELDS.put(ArmorSlot.class, ITEM_FIELDS);
    MAP_FIELDS.put(TntRow.class, new HashSet<String>(Arrays.asList("id", "seconds", "distance")));
    MAP_FIELDS.put(MarkerRow.class,
        new HashSet<String>(Arrays.asList("id", "name", "label", "coordinates", "dimension",
            "world", "worldLabel", "otherWorld", "meta", "color", "icon", "visible", "death")));
    MAP_FIELDS.put(PotionRow.class,
        new HashSet<String>(Arrays.asList("id", "name", "label", "levelText", "amplifier", "color",
            "texture", "sprite", "durationSeconds", "beneficial", "infinite", "format")));
    MAP_FIELDS.put(TargetRow.class,
        new HashSet<String>(Arrays.asList("entityId", "uuid", "name", "health", "maxHealth",
            "absorption", "fraction", "absorptionFraction", "distance", "player")));
    MAP_FIELDS.put(
        ScoreboardLine.class, new HashSet<String>(Arrays.asList("id", "text", "score", "hasScore")));
    MAP_FIELDS.put(OfficialCapeRow.class,
        new HashSet<String>(Arrays.asList(
            "id", "name", "category", "year", "primary", "secondary", "accent", "emblem",
            "selected")));
    MAP_FIELDS.put(CosmeticModelRow.class,
        new HashSet<String>(Arrays.asList(
            "id", "previewSource", "name", "subtitle", "polys", "accent", "selected")));
    MAP_FIELDS.put(CosmeticMaterialRow.class,
        new HashSet<String>(Arrays.asList(
            "id", "name", "primary", "secondary", "selected")));
    MAP_FIELDS.put(AccountRow.class,
        new HashSet<String>(Arrays.asList("id", "name", "displayName", "note", "hasNote", "meta",
            "skinSource", "uuidShort", "pinned", "active", "selected")));
    MAP_FIELDS.put(HwEventRow.class,
        new HashSet<String>(Arrays.asList("id", "title", "serverName", "rarityBadge",
            "rarityColor", "elapsed", "coords", "hasCoords", "meta", "canJoin")));
    MAP_FIELDS.put(SoundBlockRow.class,
        new HashSet<String>(Arrays.asList("id", "label", "muted")));
  }

  private static final Map<String, Set<String>> CONTEXT_MAP_FIELDS =
      new HashMap<String, Set<String>>();

  static {
    CONTEXT_MAP_FIELDS.put(
        "anchor", new HashSet<String>(Arrays.asList("x", "y", "width", "height")));
    CONTEXT_MAP_FIELDS.put("pointer", new HashSet<String>(Arrays.asList("x", "y")));
    CONTEXT_MAP_FIELDS.put("tooltip",
        new HashSet<String>(Arrays.asList(
            "text", "width", "height", "x", "y", "paddingX", "paddingY", "arrowX", "arrowSide")));
    CONTEXT_MAP_FIELDS.put(
        "notification", new HashSet<String>(Arrays.asList("text", "width", "height")));
    CONTEXT_MAP_FIELDS.put("selection",
        new HashSet<String>(Arrays.asList("id", "x", "y", "width", "height", "scale", "visible")));
    CONTEXT_MAP_FIELDS.put(
        "guides", new HashSet<String>(Arrays.asList("vertical", "horizontal", "x", "y")));
    CONTEXT_MAP_FIELDS.put("viewmodelGizmo",
        new HashSet<String>(Arrays.asList("visible", "opened", "mode", "active", "axis", "number",
            "hoverAxis", "hoverCenter", "pivot.x", "pivot.y", "pivot.depth")));
    CONTEXT_MAP_FIELDS.put("fire", new HashSet<String>(Arrays.asList("visible", "offsetY")));
    CONTEXT_MAP_FIELDS.put(
        "scoreboardData", new HashSet<String>(Arrays.asList("active", "title", "lines")));
    CONTEXT_MAP_FIELDS.put("keystrokesData",
        new HashSet<String>(Arrays.asList(
            "w", "a", "s", "d", "lmb", "rmb", "space", "lmbText", "rmbText", "cpsText")));
    CONTEXT_MAP_FIELDS.put("coordinatesData", new HashSet<String>(Arrays.asList("xyz")));
    CONTEXT_MAP_FIELDS.put("pingData",
        new HashSet<String>(
            Arrays.asList("pingColor", "pingText", "serverIp", "available", "connected")));
    CONTEXT_MAP_FIELDS.put(
        "fpsData", new HashSet<String>(Arrays.asList("value", "text", "color")));
  }

  private SkinDocument document;
  private Map<String, String> styleScope = java.util.Collections.emptyMap();
  private Map<String, Object> state = java.util.Collections.emptyMap();
  private final Map<String, Class<?>> paths = new HashMap<String, Class<?>>();

  @Override
  public void validate(SkinDocument document) throws IOException {
    if (document == null) throw new IOException("Skin document is required");
    this.document = document;
    state = document.stateDefaults();
    for (String reserved : new String[] {
             "profileName",
             "deleteProfile",
             "brushColor",
             "moreStyle",
             "waypointEdit",
             "waypointName",
             "waypointIcon",
         })
      if (state.containsKey(reserved)) throw new IOException("Reserved UI state name: " + reserved);
    check(document.root(), new HashMap<String, Class<?>>());
  }

  private void check(SkinDocument.Node node, Map<String, Class<?>> aliases) throws IOException {
    if (node.attributes.containsKey("scope")) {
      aliases = new HashMap<String, Class<?>>(aliases);
      aliases.put("__localScope", Map.class);
    }
    styleScope = document.styles(node, "");
    String tag = node.tag, bind = node.attributes.get("bind");
    if (node.attributes.containsKey("auto-layout")
        && (!"hud".equals(tag)
            || !BOOLEAN_TEXT.matcher(node.attribute("auto-layout", "")).matches()))
      throw error(node, "auto-layout is a literal true/false HUD policy");
    Class<?> type = null;
    String selectionState = node.attributes.get("selection-state");
    if (selectionState != null
        && (!node.attributes.containsKey("scope")
            || !(state.get(selectionState) instanceof Boolean)))
      throw error(node, "Selection state requires a scoped declared boolean");
    String commit = node.attributes.get("commit");
    if (commit != null && (!"slider".equals(tag) || !COMMIT_MODE.matcher(commit).matches()))
      throw error(node, "Commit mode is supported only on sliders and must be live or release");
    String displayFormat = node.attributes.get("display-format");
    if (displayFormat != null) {
      if (!"input".equals(tag)) throw error(node, "Display format is supported only on input");
      if (!DISPLAY_FORMAT.matcher(displayFormat).matches())
        throw error(node, "Input display format must be fixed:0..3");
      if (!"number".equals(node.attributes.get("format")))
        throw error(node, "Input display format requires format = number");
    }
    if (bind != null) {
      if (bind.startsWith("local.") && !aliases.containsKey("__localScope"))
        throw error(node, "Local binding requires a scope");
      if (bind.contains("{{")) {
        templates(node, bind, aliases);
        if ("toggle".equals(tag) || "switch".equals(tag))
          type = boolean.class;
        else if ("slider".equals(tag) || "color".equals(tag) || "progress".equals(tag)
            || colorTag(tag))
          type = float.class;
        else
          type = String.class;
      } else
        type = type(bind);
      if (type == null) throw error(node, "Unknown or read-only setting: " + bind);
    }
    if ("toggle".equals(tag) || "switch".equals(tag)) {
      if (type != boolean.class) throw error(node, "Toggle requires a boolean bind");
    }
    if ("slider".equals(tag)) {
      if (type != float.class&& type != int.class)
        throw error(node, "Slider requires a numeric bind");
      float min = number(node, "min", 0), max = number(node, "max", 1),
            step = number(node, "step", 0);
      if (!node.attribute("max", "").contains("{{") && (max <= min || step < 0))
        throw error(node, "Slider requires max > min and step >= 0");
      if (!node.attribute("value-scale", "").contains("{{") && number(node, "value-scale", 1) == 0)
        throw error(node, "Slider value scale must be nonzero");
    }
    if ((node.attributes.containsKey("value-scale") || node.attributes.containsKey("value-offset"))
        && !"slider".equals(tag))
      throw error(node, "Value mapping is supported only on sliders");
    if ("input".equals(tag)) {
      if (bind == null) throw error(node, "Input requires a bind");
      boolean hex = "hex".equals(node.attributes.get("format"))
          || "{{control.format}}".equals(node.attributes.get("format"));
      String format = node.attributes.get("format");
      boolean numeric = "number".equals(format);
      if (format != null && !hex && !numeric)
        throw error(node, "Input format must be hex or number");
      if (!bind.contains("{{")
          && (hex ? type != int.class
                  : numeric ? type != int.class && type != float.class
                            : type != String.class))
        throw error(node,
            hex ? "Hex input requires an integer color bind"
                : numeric ? "Numeric input requires a numeric bind"
                          : "Input requires a string bind");
      float max = number(node, "maxlength", 128);
      if (max < 1 || max > 512) throw error(node, "Input maxlength must be 1..512");
    }
    if ("choice".equals(tag) && type != String.class && type != int.class)
      throw error(node, "Choice requires a string or integer bind");
    if (node.attributes.containsKey("option-labels")) {
      if (!"choice".equals(tag)) throw error(node, "Option labels are supported only on choice");
      String options = node.attribute("options", ""), labels = node.attribute("option-labels", "");
      if (!options.contains("{{") && !labels.contains("{{")
          && options.split("\\|", -1).length != labels.split("\\|", -1).length)
        throw error(node, "Option labels must match the choice options");
    }
    if (("color".equals(tag) || colorTag(tag))
        && (bind == null || type != int.class && !bind.contains("{{")))
      throw error(node, "Color control requires an integer bind");
    if ("progress".equals(tag) && bind != null && type != float.class&& type != int.class)
      throw error(node, "Progress bind must be numeric");
    if ("icon".equals(tag) || "image".equals(tag) || "texture".equals(tag)) {
      String icon = node.attributes.containsKey("name") ? node.attributes.get("name")
                                                        : node.attributes.get("src");
      if (icon == null || icon.isEmpty()) throw error(node, "Icon requires a name or src");
      if (!icon.contains("{{") && !Icons.ids().contains(icon) && !isRaster(icon))
        throw error(node, "Unknown SVG icon: " + icon);
    }
    if ("item".equals(tag) && node.attributes.containsKey("src")) {
      String id = node.attributes.get("src");
      if (!id.contains("{{") && !itemId(id)) throw error(node, "Invalid Minecraft item ID: " + id);
    }
    for (String actionKey :
        new String[] {"action", "secondary-action", "middle-action", "dismiss-action"}) {
      String action = node.attributes.get(actionKey);
      if (action == null || action.isEmpty()) {
        if ("action".equals(actionKey) && "button".equals(tag))
          throw error(node, "Button requires an action");
        continue;
      }
      if ("dismiss-action".equals(actionKey) && action.length() > 512)
        throw error(node, "Dismiss action exceeds 512 characters");
      validateAction(node, action, aliases);
    }
    Map<String, Class<?>> nested = aliases;
    if ("repeat".equals(tag)) {
      if (!SOURCES.contains(node.attributes.get("source")))
        throw error(node, "Unknown repeat source");
      String alias = node.attribute("as", "item");
      if (!ALIAS.matcher(alias).matches()) throw error(node, "Invalid repeat alias");
      nested = new HashMap<String, Class<?>>(aliases);
      nested.put(alias, sourceType(node.attributes.get("source")));
    }
    String condition = node.attributes.get("if");
    if (condition != null && !condition.trim().isEmpty()) {
      String key = condition.trim();
      if (key.contains("{{"))
        templates(node, key, nested);
      else if (key.startsWith("="))
        expression(node, key.substring(1), nested);
      else {
        if (key.startsWith("!")) key = key.substring(1).trim();
        int equals = key.indexOf('=');
        if (equals >= 0) key = key.substring(0, equals).trim();
        if (!known(key, nested)) throw error(node, "Unknown condition binding: " + key);
      }
    }
    if (("item".equals(tag) || "entity-head".equals(tag) || "viewmodel-gizmo".equals(tag))
        && node.attributes.containsKey("source")
        && !("viewmodel-gizmo".equals(tag)
            && "viewmodelGizmo".equals(node.attribute("source", "")))
        && !node.attribute("source", "").contains("{{")
        && !known(node.attribute("source", ""), nested))
      throw error(node, "Unknown item source: " + node.attribute("source", ""));
    templates(node, node.text, nested);
    for (Map.Entry<String, String> attribute : node.attributes.entrySet())
      templates(node, attribute.getValue(), nested);

    for (String variant : new String[] {"", "hover", "focus", "active", "hover focus",
             "hover active", "focus active", "hover focus active"}) {
      styleScope = document.styles(node, variant);
      for (Map.Entry<String, String> style : styleScope.entrySet())
        templates(node, style.getValue(), nested);
    }
    styleScope = document.styles(node, "");
    for (SkinDocument.Node child : node.children) check(child, nested);
  }

  private void validateAction(SkinDocument.Node node, String action, Map<String, Class<?>> aliases)
      throws IOException {
    if (action.startsWith("sequence:")) {
      String[] chain = action.substring(9).split(";", -1);
      if (chain.length > 16) throw error(node, "Action sequence exceeds 16 actions");
      for (String part : chain) {
        part = part.trim();
        if (part.isEmpty() || part.startsWith("sequence:"))
          throw error(node, "Action sequence requires nonempty actions without nesting");
        validateAction(node, part, aliases);
      }
      return;
    }
    if (action.startsWith("ui-set:") || action.startsWith("ui-toggle:")
        || action.startsWith("local-set:") || action.startsWith("local-toggle:")) {
      boolean local = action.startsWith("local-"),
              toggle = action.startsWith(local ? "local-toggle:" : "ui-toggle:");
      String[] parts = action.split(":", 3);
      String name = parts.length > 1 ? parts[1] : "";
      if (local && !aliases.containsKey("__localScope"))
        throw error(node, "Local action requires a scope");
      if (!state.containsKey(name) || !STATE_NAME.matcher(name).matches())
        throw error(node, "Unknown presentation state: " + name);
      Object old = state.get(name);
      if (toggle && (!(old instanceof Boolean) || parts.length != 2))
        throw error(node, "Toggle state requires a boolean");
      if (!toggle) {
        if (parts.length != 3 || parts[2].length() > 128)
          throw error(node, "State assignment requires a bounded scalar");
        if (parts[2].contains("{{")) {
          templates(node, parts[2], aliases);
          return;
        }
        if (old instanceof Boolean && !BOOLEAN_TEXT.matcher(parts[2]).matches())
          throw error(node, "State assignment requires a boolean");
        if (old instanceof Number) {
          try {
            if (!Float.isFinite(Float.parseFloat(parts[2]))) throw new NumberFormatException();
          } catch (NumberFormatException invalid) {
            throw error(node, "State assignment requires a finite number");
          }
        }
      }
      return;
    }
    if (action.startsWith("toggle:")) {
      String bind = action.substring(7);
      if (bind.contains("{{"))
        templates(node, bind, aliases);
      else if (type(bind) != boolean.class)
        throw error(node, "Toggle action requires a writable boolean");
      return;
    }
    if (action.startsWith("clipboard-copy:") || action.startsWith("clipboard-paste:")) {
      String bind = action.substring(action.indexOf(':') + 1);
      if (bind.contains("{{"))
        templates(node, bind, aliases);
      else {
        Class<?> type = type(bind);
        if (type != int.class&& type != String.class)
          throw error(node, "Clipboard action requires a writable color or string binding");
      }
      return;
    }
    if (action.startsWith("set:")) {
      String[] parts = action.split(":", 3);
      if (parts.length != 3) throw error(node, "Set action requires a writable setting and value");
      if (parts[1].contains("{{"))
        templates(node, parts[1], aliases);
      else if (type(parts[1]) == null)
        throw error(node, "Set action requires a writable setting");
      if (parts[2].contains("{{"))
        templates(node, parts[2], aliases);
      else if (!SET_SCALAR.matcher(parts[2]).matches())
        throw error(node, "Set action requires a bounded scalar");
      return;
    }
    if (action.contains("{{")) {
      templates(node, action, aliases);
      if (!DYNAMIC_ACTION.matcher(action).matches())
        throw error(node, "Unsupported dynamic action: " + action);
      return;
    }
    if (!ACTIONS.contains(action) && !STATIC_ACTION.matcher(action).matches())
      throw error(node, "Unsupported action: " + action);
  }

  private static Class<?> sourceType(String source) {
    if ("editorTargets".equals(source)) return EditorTarget.class;
    if ("choiceOptions".equals(source)) return ChoiceOption.class;
    if ("targetEquipment".equals(source)) return InventorySlot.class;
    if ("viewmodelGizmo.segments".equals(source)) return GizmoSegment.class;
    if ("viewmodelGizmo.axes".equals(source)) return GizmoAxis.class;
    if ("viewmodelGizmo.labels".equals(source)) return GizmoLabel.class;
    if ("cooldownRows".equals(source)) return CooldownRow.class;
    if ("inventoryCells".equals(source)) return InventoryCell.class;
    if ("tntRows".equals(source)) return TntRow.class;
    if ("savedWaypoints".equals(source)) return MarkerRow.class;
    if ("savedAccounts".equals(source)) return AccountRow.class;
    if ("hwEvents".equals(source)) return HwEventRow.class;
    if ("mutedBlocks".equals(source) || "popularSoundBlocks".equals(source))
      return SoundBlockRow.class;
    if ("officialCapes".equals(source)) return OfficialCapeRow.class;
    if ("cosmeticWeapons".equals(source) || "cosmeticArmors".equals(source))
      return CosmeticModelRow.class;
    if ("cosmeticWeaponMaterials".equals(source)) return CosmeticMaterialRow.class;
    if ("armorSlots".equals(source)) return ArmorSlot.class;
    if ("potionRows".equals(source)) return PotionRow.class;
    if ("targetRows".equals(source)) return TargetRow.class;
    if ("scoreboardData.lines".equals(source)) return ScoreboardLine.class;
    if ("cooldowns".equals(source) || "visibleCooldowns".equals(source)) return Cooldown.class;
    if ("inventory".equals(source) || "armor".equals(source)) return InventorySlot.class;
    if ("tnt".equals(source)) return TntView.class;
    if ("potions".equals(source)) return PotionView.class;
    if ("vitals".equals(source)) return VitalsView.class;
    if ("target".equals(source)) return TargetView.class;
    if (source.endsWith("Controls") || "controls".equals(source)) return UiCatalog.Control.class;
    if ("modules".equals(source) || "categoryModules".equals(source)
        || "filteredModules".equals(source) || "renderModules".equals(source)
        || "playerModules".equals(source) || "hudModules".equals(source)
        || "filteredHudModules".equals(source))
      return UiCatalog.Module.class;
    return Map.class;
  }

  private void templates(SkinDocument.Node node, String value, Map<String, Class<?>> aliases)
      throws IOException {
    if (value == null) return;
    int at = 0, substitutions = 0;
    while ((at = value.indexOf("{{", at)) >= 0) {
      int end = value.indexOf("}}", at + 2);
      if (end < 0) throw error(node, "Unclosed data binding");
      if (++substitutions > 32) throw error(node, "Too many substitutions in one text value");
      String template = value.substring(at + 2, end).trim();
      int separator = filterSeparator(template);
      String binding = (separator < 0 ? template : template.substring(0, separator)).trim();
      if (binding.startsWith("="))
        expression(node, binding.substring(1), aliases);
      else {
        if (!known(binding, aliases)) throw error(node, "Unknown text binding: " + binding);
        if (binding.startsWith("vars."))
          variable(node, binding.substring(5), aliases, new HashSet<String>());
      }
      if (separator >= 0
          && !BINDING_FORMAT.matcher(template.substring(separator + 1).trim()).matches())
        throw error(node, "Unsupported binding format: " + template.substring(separator + 1));
      at = end + 2;
    }
  }

  private static int filterSeparator(String expression) {
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < expression.length(); i++) {
      char ch = expression.charAt(i);
      if (quote != 0) {
        if (escaped)
          escaped = false;
        else if (ch == '\\')
          escaped = true;
        else if (ch == quote)
          quote = 0;
        continue;
      }
      if (ch == '\'' || ch == '"') {
        quote = ch;
        continue;
      }
      if (ch == '|' && (i == 0 || expression.charAt(i - 1) != '|')
          && (i + 1 == expression.length() || expression.charAt(i + 1) != '|'))
        return i;
    }
    return -1;
  }

  private void expression(SkinDocument.Node node, String source, Map<String, Class<?>> aliases)
      throws IOException {
    final NcExpression.Expression compiled;
    try {
      compiled = NcExpression.compile(source);
    } catch (IllegalArgumentException invalid) {
      throw error(node, invalid.getMessage());
    }
    for (String name : compiled.identifiers()) {
      if (!known(name, aliases)) throw error(node, "Unknown expression binding: " + name);
      if (name.startsWith("vars."))
        variable(node, name.substring(5), aliases, new HashSet<String>());
    }
  }

  private void variable(SkinDocument.Node node, String name, Map<String, Class<?>> aliases,
      Set<String> visiting) throws IOException {
    String definition = variableDefinition("vars." + name);
    if (definition == null) throw error(node, "Unknown scoped variable: " + name);
    if (!visiting.add(name)) throw error(node, "Cyclic scoped variable: " + name);
    try {
      String source = SkinDocument.expressionVariables(definition.trim(), styleScope);
      if (source.startsWith("{{=") && source.endsWith("}}"))
        source = source.substring(3, source.length() - 2).trim();
      else if (source.startsWith("="))
        source = source.substring(1).trim();
      if (HEX_COLOR.matcher(source).matches()) return;
      final NcExpression.Expression compiled;
      try {
        compiled = NcExpression.compile(source);
      } catch (IllegalArgumentException invalid) {
        throw error(node, "Variable " + name + ": " + invalid.getMessage());
      }
      for (String dependency : compiled.identifiers()) {
        if (!known(dependency, aliases))
          throw error(node, "Unknown variable binding: " + dependency);
        if (dependency.startsWith("vars."))
          variable(node, dependency.substring(5), aliases, visiting);
      }
    } finally {
      visiting.remove(name);
    }
  }

  private String variableDefinition(String path) {
    for (Map.Entry<String, String> entry : styleScope.entrySet())
      if (entry.getKey().startsWith("--") && SkinDocument.variablePath(entry.getKey()).equals(path))
        return entry.getValue();
    return null;
  }

  private boolean known(String name, Map<String, Class<?>> aliases) {
    if ("themePresetOptions".equals(name) || "themePresetLabels".equals(name)) return true;
    if (name != null && (name.startsWith("i18n.") || name.startsWith("tr."))) {
      String key = name.substring(name.indexOf('.') + 1);
      for (String language : I18n.languages())
        if (I18n.catalog(language).containsKey(key)) return true;
      return false;
    }
    if (name == null || !VARIABLE_NAME.matcher(name).matches()) return false;
    if (HUD_SPECIAL.contains(name) || CONTEXT.contains(name) || HUD_CONTEXT.contains(name)
        || UI_PROPERTY.matcher(name).matches())
      return true;
    int dot = name.indexOf('.');
    String head = dot < 0 ? name : name.substring(0, dot);
    if ("local".equals(head))
      return aliases.containsKey("__localScope") && dot >= 0
          && state.containsKey(name.substring(dot + 1));
    if ("vars".equals(head))
      return dot >= 0 && name.indexOf('.', dot + 1) < 0 && variableDefinition(name) != null;
    Set<String> contextFields = CONTEXT_MAP_FIELDS.get(head);
    if (contextFields != null) return dot >= 0 && contextFields.contains(name.substring(dot + 1));
    Class<?> owner = aliases.get(head);
    if ("foodData".equals(head))
      owner = VitalsView.class;
    else if ("targetData".equals(head) || "localPlayerData".equals(head))
      owner = TargetView.class;
    if (owner != null) {
      if (dot < 0) return true;
      String field = name.substring(dot + 1);
      Set<String> fields = MAP_FIELDS.get(owner);
      if (fields != null) {
        int nestedDot = field.indexOf('.');
        if (nestedDot < 0) return fields.contains(field);
        return "slot".equals(field.substring(0, nestedDot)) && fields.contains("slot")
            && scalarField(InventorySlot.class, field.substring(nestedDot + 1));
      }
      return scalarField(owner, field);
    }
    return type(name) != null;
  }

  private static boolean scalarField(Class<?> owner, String fieldName) {
    if (!FIELD_NAME.matcher(fieldName).matches()) return false;
    try {
      Field field = owner.getField(fieldName);
      Class<?> type = field.getType();
      return !Modifier.isStatic(field.getModifiers())
          && (type == boolean.class || type == int.class || type == float.class
              || type == String.class);
    } catch (ReflectiveOperationException invalid) {
      return false;
    }
  }

  private Class<?> type(String name) {
    if (name != null && name.startsWith("local.") && state.containsKey(name.substring(6))) {
      Object value = state.get(name.substring(6));
      return value instanceof Boolean ? boolean.class
          : value instanceof Number   ? float.class
                                      : String.class;
    }
    if (name != null && name.startsWith("ui.") && state.containsKey(name.substring(3))) {
      Object value = state.get(name.substring(3));
      return value instanceof Boolean ? boolean.class
          : value instanceof Number   ? float.class
                                      : String.class;
    }
    if ("ui.profileName".equals(name) || "ui.deleteProfile".equals(name)) return String.class;
    if ("ui.waypointEdit".equals(name) || "ui.waypointName".equals(name)
        || "ui.waypointIcon".equals(name) || "ui.waypointManager".equals(name)
        || "ui.waypointDraftId".equals(name) || "ui.waypointDraftName".equals(name)
        || "ui.waypointDraftX".equals(name) || "ui.waypointDraftY".equals(name)
        || "ui.waypointDraftZ".equals(name) || "ui.waypointDraftColor".equals(name)
        || "ui.waypointDraftDimension".equals(name) || "ui.waypointScope".equals(name))
      return String.class;
    if ("ui.waypointDeleteConfirm".equals(name)) return boolean.class;
    if ("ui.cosmeticsTab".equals(name) || "ui.cosmeticsCapeFilter".equals(name)
        || "ui.cosmeticsCapeQuery".equals(name) || "ui.cosmeticsNickDraft".equals(name)
        || "ui.cosmeticsCapePathDraft".equals(name) || "ui.cosmeticsSkinPathDraft".equals(name)
        || "ui.cosmeticsWeaponPathDraft".equals(name) || "ui.cosmeticsArmorPathDraft".equals(name))
      return String.class;
    if ("ui.accountManager".equals(name) || "ui.accountSearch".equals(name)
        || "ui.accountSelectedId".equals(name) || "ui.accountDraftId".equals(name)
        || "ui.accountDraftName".equals(name) || "ui.accountDraftNote".equals(name)
        || "ui.accountDraftSkin".equals(name) || "ui.soundBlockDraft".equals(name)
        || "ui.crosshairLayer".equals(name))
      return String.class;
    if ("ui.brushColor".equals(name)) return int.class;
    if ("ui.moreStyle".equals(name)) return boolean.class;
    if (name == null) return null;
    name = name.trim();
    if (name.startsWith("settings.")) name = name.substring(9);
    if (!TYPE_PATH.matcher(name).matches() || "schemaVersion".equals(name)) return null;
    if (paths.containsKey(name)) return paths.get(name);
    Class<?> owner = Settings.class;
    try {
      String[] names = name.split("\\.");
      for (int i = 0; i < names.length; i++) {
        Field field = owner.getField(names[i]);
        int flags = field.getModifiers();
        if (Modifier.isStatic(flags) || Modifier.isFinal(flags) || Modifier.isTransient(flags))
          throw new IllegalArgumentException();
        owner = field.getType();
        if (i < names.length - 1 && !owner.getName().startsWith("dev.nightclient.config."))
          throw new IllegalArgumentException();
      }
      if (owner != boolean.class
          && owner != int.class&& owner != float.class&& owner != String.class)
        owner = null;
    } catch (ReflectiveOperationException | IllegalArgumentException error) {
      owner = null;
    }
    paths.put(name, owner);
    return owner;
  }

  private static float number(SkinDocument.Node node, String key, float fallback)
      throws IOException {
    String raw = node.attributes.get(key);
    if (raw == null) return fallback;
    if (raw.contains("{{")) return fallback;
    try {
      float n = Float.parseFloat(raw);
      if (!Float.isFinite(n) || Math.abs(n) > 100000) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException error) {
      throw error(node, "Invalid numeric attribute: " + key);
    }
  }

  private static boolean colorTag(String tag) {
    return "color-plane".equals(tag) || "hue-strip".equals(tag) || "alpha-strip".equals(tag)
        || "color-swatch".equals(tag);
  }

  public static boolean itemId(String id) {
    return id != null && ITEM_ID.matcher(id).matches();
  }

  private static IOException error(SkinDocument.Node node, String message) {
    return new IOException((node.sourceLine > 0 ? "NC line " + node.sourceLine + ", column "
                                       + node.sourceColumn + ": "
                                                : "")
        + node.tag + (node.id == null ? "" : " #" + node.id) + ": " + message);
  }
}
