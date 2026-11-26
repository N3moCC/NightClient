package dev.nightclient.ui.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.FeatureSupport;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import dev.nightclient.i18n.JsonResources;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UiCatalog {
  public static final class Module {
    public final String id, label, labelKey, icon, bind, summary, summaryKey, category;
    public final boolean hud, hasControls, menuVisible;
    public boolean enabled;
    public String bindingLabel = "";
    public String cardBindingLabel = "";
    private final Field[] fields;
    private final String bindingTarget;

    private Module(ModuleSpec spec, String language) {
      id = spec.id;
      labelKey = spec.label;
      summaryKey = spec.summary;
      label = I18n.text(language, labelKey);
      summary = I18n.text(language, summaryKey);
      icon = spec.icon;
      bind = spec.bind;
      hud = spec.hud;
      category = spec.category;
      menuVisible = spec.menuVisible;
      fields = spec.fields;
      bindingTarget = "module:" + id;
      hasControls = METADATA.groups.containsKey(id) && !METADATA.groups.get(id).isEmpty();
    }
  }

  public static final class Control {
    public final String bind, label, labelKey, kind, options, optionLabels, format, condition;
    public final float min, max, step;

    private Control(ControlSpec spec, String language) {
      bind = spec.bind;
      labelKey = spec.label;
      label = I18n.text(language, labelKey);
      kind = spec.kind;
      min = spec.min;
      max = spec.max;
      step = spec.step;
      format = spec.format;
      condition = spec.condition;
      StringBuilder values = new StringBuilder(), labels = new StringBuilder();
      for (OptionSpec option : spec.options) {
        if (values.length() > 0) {
          values.append('|');
          labels.append('|');
        }
        values.append(option.value);
        labels.append(I18n.text(language, option.label));
      }
      options = values.toString();
      optionLabels = labels.toString();
    }
  }

  private static final Metadata METADATA = Metadata.load();
  private final Settings settings;
  private final dev.nightclient.ui.UiActions actions;
  private String language;
  private long policyRevision = -1L;
  private List<Module> modules = Collections.emptyList();
  private List<Module> featureModules = Collections.emptyList(),
                       hudModules = Collections.emptyList(),
                       allVisibleModules = Collections.emptyList();
  private Map<String, Module> modulesById = Collections.emptyMap();
  private final Map<String, Control> localizedControls = new LinkedHashMap<String, Control>();
  private final Map<String, List<Control>> cache = new LinkedHashMap<String, List<Control>>();

  public UiCatalog(Settings settings) {
    this(settings, null);
  }

  public UiCatalog(Settings settings, dev.nightclient.ui.UiActions actions) {
    this.settings = settings;
    this.actions = actions;
    refresh();
  }

  public void refresh() {
    refreshLanguage();
  }

  private void refreshLanguage() {
    String next = I18n.language(settings.language);
    long rev = FeaturePolicy.revision() * 31L + FeatureSupport.revision();
    if (next.equals(language) && rev == policyRevision) return;
    language = next;
    policyRevision = rev;
    cache.clear();
    localizedControls.clear();
    List<Module> rows = new ArrayList<Module>(), features = new ArrayList<Module>(),
                 hud = new ArrayList<Module>();
    for (ModuleSpec spec : METADATA.modules)
      if (!FeaturePolicy.isBlocked(spec.id) && read(spec.fields) != null) {
        Module module = new Module(spec, language);
        rows.add(module);
        if (module.menuVisible) (module.hud ? hud : features).add(module);
      }
    Map<String, Module> byId = new java.util.HashMap<String, Module>();
    for (Module module : rows) byId.put(module.id, module);
    modulesById = Collections.unmodifiableMap(byId);
    modules = Collections.unmodifiableList(rows);
    featureModules = Collections.unmodifiableList(features);
    hudModules = Collections.unmodifiableList(hud);
    List<Module> combined = new ArrayList<Module>(features.size() + hud.size());
    combined.addAll(features);
    combined.addAll(hud);
    allVisibleModules = Collections.unmodifiableList(combined);
  }

  private void refreshValues() {
    for (Module module : modules) refreshValue(module);
  }

  private void refreshValues(List<Module> rows) {
    for (Module module : rows) refreshValue(module);
  }

  private void refreshValue(Module module) {
    module.enabled = Boolean.TRUE.equals(read(module.fields));
    module.bindingLabel = actions == null ? "" : actions.keyBindingLabel(module.bindingTarget);
    String badge = module.bindingLabel;
    if (isUnboundLabel(badge) && actions != null) {
      String primary = primaryBindingTarget(module.id);
      if (primary != null) badge = actions.keyBindingLabel(primary);
    }
    module.cardBindingLabel = isUnboundLabel(badge) ? "" : badge;
  }

  private static String primaryBindingTarget(String id) {
    if ("freelook".equals(id)) return "freelook";
    if ("zoom".equals(id)) return "zoom";
    if ("worldMap".equals(id)) return "worldMap";
    if ("waypoints".equals(id)) return "waypoint";
    if ("minimap".equals(id)) return "mapEnlarge";
    return null;
  }

  private static boolean isUnboundLabel(String label) {
    if (label == null) return true;
    String trimmed = label.trim();
    if (trimmed.isEmpty() || "-".equals(trimmed) || "—".equals(trimmed)) return true;
    String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
    return "none".equals(lower) || "unbound".equals(lower) || "not bound".equals(lower)
        || "нет".equals(lower) || "не назначено".equals(lower) || "не назначена".equals(lower);
  }

  public List<Module> modules(boolean hud) {
    refreshLanguage();
    List<Module> rows = hud ? hudModules : featureModules;
    refreshValues(rows);
    return rows;
  }

  public List<Module> allVisibleModules() {
    refreshLanguage();
    refreshValues(allVisibleModules);
    return allVisibleModules;
  }

  public Module module(String id) {
    refreshLanguage();
    Module module = modulesById.get(id);
    if (module == null) module = modules.isEmpty() ? null : modules.get(0);
    if (module != null) refreshValue(module);
    return module;
  }

  public List<Control> controls(String group) {
    refreshLanguage();
    return localized("group:" + group, METADATA.groups.get(group));
  }

  public List<Control> editorControls(String group) {
    refreshLanguage();
    return localized("editor:" + group, METADATA.editorGroups.get(group));
  }

  public List<Control> styleControls(boolean detailed) {
    refreshLanguage();
    String group = detailed ? "detailed" : "simple";
    return localized("style:" + group, METADATA.styleGroups.get(group));
  }

  public Control control(String bind) {
    refreshLanguage();
    if (FeaturePolicy.isControlBlocked(bind) || !FeatureSupport.isControlSupported(bind)) return null;
    Control localized = localizedControls.get(bind);
    if (localized == null) {
      ControlSpec spec = METADATA.controls.get(bind);
      if (spec == null) return null;
      localized = new Control(spec, language);
      localizedControls.put(bind, localized);
    }
    return localized;
  }

  public boolean toggleModule(String id) {
    if (FeaturePolicy.isBlocked(id)) return false;
    Module module = module(id);
    if (module == null || !module.id.equals(id)) return false;
    try {
      Object owner = settings;
      for (int i = 0; i < module.fields.length - 1; i++) owner = module.fields[i].get(owner);
      Field field = module.fields[module.fields.length - 1];
      field.setBoolean(owner, !field.getBoolean(owner));
      refreshValues();
      return true;
    } catch (ReflectiveOperationException error) {
      throw invalid("module toggle: " + id);
    }
  }

  public Set<String> groups() {
    return METADATA.groups.keySet();
  }

  private static final String[] GLOBAL_BINDINGS = {
      "menu", "zoom", "freelook", "waypoint", "worldMap", "mapSettings", "mapEnlarge",
      "waypointsMenu", "tapeMouseKey"
  };

  public long bindingSignature() {
    if (actions == null) return 0;
    long h = 0xCBF29CE484222325L;
    for (int i = 0; i < GLOBAL_BINDINGS.length; i++) {
      String label = actions.keyBindingLabel(GLOBAL_BINDINGS[i]);
      h = (h ^ (label == null ? 0 : label.hashCode())) * 0x9E3779B185EBCA87L;
    }
    for (int i = 0; i < modules.size(); i++) {
      String label = actions.keyBindingLabel(modules.get(i).bindingTarget);
      h = (h ^ (label == null ? 0 : label.hashCode())) * 0x9E3779B185EBCA87L;
    }
    return h;
  }

  public boolean visible(Control control, SkinBindings bindings, Map<String, Object> context) {
    if (control == null || FeaturePolicy.isControlBlocked(control.bind)
        || !FeatureSupport.isControlSupported(control.bind)) return false;
    return control.condition.isEmpty() || bindings.condition(control.condition, context);
  }

  private List<Control> localized(String key, List<String> binds) {
    List<Control> result = cache.get(key);
    if (result != null) return result;
    if (binds == null) return Collections.emptyList();
    List<Control> rows = new ArrayList<Control>();
    for (String bind : binds) {
      if (FeaturePolicy.isControlBlocked(bind) || !FeatureSupport.isControlSupported(bind)) continue;
      Control ctrl = control(bind);
      if (ctrl != null) rows.add(ctrl);
    }
    result = Collections.unmodifiableList(rows);
    cache.put(key, result);
    return result;
  }

  private Object read(Field[] fields) {
    try {
      Object value = settings;
      for (Field field : fields) {
        if (value == null) return null;
        value = field.get(value);
      }
      return value;
    } catch (ReflectiveOperationException error) {
      return null;
    }
  }

  private static final class ModuleSpec {
    final String id, label, summary, icon, bind, category;
    final boolean hud, menuVisible;
    final Field[] fields;

    ModuleSpec(JsonObject data) {
      id = identifier(string(data, "id"));
      label = label(data, "label");
      summary = label(data, "summary");
      icon = string(data, "icon");
      if (!icon.matches("[a-zA-Z0-9_-]{1,64}")) throw invalid("icon");
      bind = binding(string(data, "bind"));
      if (fieldType(bind) != boolean.class) throw invalid("module bind");
      fields = fieldPath(bind);
      JsonElement flag = data.get("hud");
      if (flag == null || !flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean())
        throw invalid("hud flag");
      hud = flag.getAsBoolean();
      JsonElement visible = data.get("menuVisible");
      if (visible != null
          && (!visible.isJsonPrimitive() || !visible.getAsJsonPrimitive().isBoolean()))
        throw invalid("menu visibility flag");
      menuVisible = visible == null || visible.getAsBoolean();
      category = data.has("category") ? string(data, "category") : (hud ? "hud" : "render");
      if (!category.matches("render|camera|world|player|hud")) throw invalid("module category");
    }
  }

  private static final class OptionSpec {
    final String value, label;

    OptionSpec(JsonObject data) {
      value = string(data, "value");
      if (!value.matches("[a-zA-Z0-9_.-]{1,64}")) throw invalid("option value");
      label = label(data, "label");
    }
  }

  private static final class ControlSpec {
    final String bind, label, kind, condition, format;
    final float min, max, step;
    final List<OptionSpec> options;

    ControlSpec(String path, JsonObject data) {
      bind = binding(path);
      label = label(data, "label");
      kind = string(data, "kind");
      Class<?> type = fieldType(bind);
      if (!("toggle".equals(kind) && type == boolean.class
              || "color".equals(kind) && type == int.class
              || "choice".equals(kind) && type == String.class
              || "input".equals(kind) && type == String.class
              || "slider".equals(kind) && (type == int.class || type == float.class)))
        throw invalid("control kind/type: " + bind);
      if ("slider".equals(kind)) {
        JsonElement range = data.get("range");
        if (range == null || !range.isJsonArray() || range.getAsJsonArray().size() != 3)
          throw invalid("control range: " + bind);
        min = number(range.getAsJsonArray().get(0));
        max = number(range.getAsJsonArray().get(1));
        step = number(range.getAsJsonArray().get(2));
        if (min >= max || step <= 0 || step > max - min)
          throw invalid("control range bounds: " + bind);
      } else {
        min = 0;
        max = 1;
        step = 1;
      }
      condition = data.has("condition") ? string(data, "condition") : "";
      if (!condition.isEmpty()) {
        for (String branch : condition.split("\\|\\|")) {
          for (String rawTerm : branch.split("&&")) {
            String term = rawTerm.trim();
            if (term.isEmpty()) throw invalid("control condition: " + bind);
            String conditionBind = term.startsWith("!") ? term.substring(1).trim() : term;
            int eq = conditionBind.indexOf('=');
            if (eq > 0) {
              Class<?> ctype = fieldType(binding(conditionBind.substring(0, eq).trim()));
              if (ctype != String.class && ctype != int.class && ctype != boolean.class)
                throw invalid("control condition: " + bind);
            } else if (fieldType(binding(conditionBind)) != boolean.class)
              throw invalid("control condition: " + bind);
          }
        }
      }
      format = data.has("format") ? string(data, "format") : "";
      if (!format.isEmpty() && !"hex".equals(format)) throw invalid("control format");
      List<OptionSpec> choices = new ArrayList<OptionSpec>();
      Set<String> values = new HashSet<String>();
      if (data.has("options")) {
        JsonElement entries = data.get("options");
        if (!entries.isJsonArray() || entries.getAsJsonArray().size() > 64)
          throw invalid("control options");
        for (JsonElement option : entries.getAsJsonArray()) {
          OptionSpec spec = new OptionSpec(object(option));
          if (!values.add(spec.value)) throw invalid("duplicate option value");
          choices.add(spec);
        }
      }
      if ("choice".equals(kind) != !choices.isEmpty()) throw invalid("choice options: " + bind);
      options = Collections.unmodifiableList(choices);
    }
  }

  private static final class Metadata {
    final List<ModuleSpec> modules;
    final Map<String, ControlSpec> controls;
    final Map<String, List<String>> groups, editorGroups, styleGroups;

    private Metadata(JsonObject data) {
      if (!data.has("schemaVersion") || number(data.get("schemaVersion")) != 1)
        throw invalid("catalog version");
      JsonElement entries = data.get("modules");
      if (entries == null || !entries.isJsonArray() || entries.getAsJsonArray().size() > 128)
        throw invalid("module list");
      List<ModuleSpec> rows = new ArrayList<ModuleSpec>();
      Set<String> ids = new HashSet<String>();
      for (JsonElement entry : entries.getAsJsonArray()) {
        ModuleSpec module = new ModuleSpec(object(entry));
        if (!ids.add(module.id)) throw invalid("duplicate module");
        rows.add(module);
      }
      modules = Collections.unmodifiableList(rows);
      Map<String, ControlSpec> fields = new LinkedHashMap<String, ControlSpec>();
      JsonObject descriptors = object(data.get("controls"));
      if (descriptors.entrySet().size() > 1024) throw invalid("too many controls");
      for (Map.Entry<String, JsonElement> entry : descriptors.entrySet())
        fields.put(entry.getKey(), new ControlSpec(entry.getKey(), object(entry.getValue())));
      controls = Collections.unmodifiableMap(fields);
      groups = lists(object(data.get("groups")), fields);
      editorGroups = lists(object(data.get("editorGroups")), fields);
      styleGroups = lists(object(data.get("styleGroups")), fields);
    }

    static Metadata load() {
      return new Metadata(object(JsonResources.read("/assets/nightclient/ui/catalog.json")));
    }
  }

  private static Map<String, List<String>> lists(
      JsonObject data, Map<String, ControlSpec> controls) {
    if (data.entrySet().size() > 128) throw invalid("too many control groups");
    Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
      identifier(entry.getKey());
      JsonElement values = entry.getValue();
      if (!values.isJsonArray() || values.getAsJsonArray().size() > 512)
        throw invalid("control group");
      List<String> binds = new ArrayList<String>();
      Set<String> unique = new HashSet<String>();
      for (JsonElement value : values.getAsJsonArray()) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
          throw invalid("group binding");
        String bind = value.getAsString();
        if (!controls.containsKey(bind) || !unique.add(bind))
          throw invalid("unknown or duplicate group binding: " + bind);
        binds.add(bind);
      }
      groups.put(entry.getKey(), Collections.unmodifiableList(binds));
    }
    return Collections.unmodifiableMap(groups);
  }

  private static String label(JsonObject data, String key) {
    String value = string(data, key);
    if (!I18n.catalog("en").containsKey(value)) throw invalid("unknown translation key: " + value);
    return value;
  }

  private static String identifier(String value) {
    if (!value.matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}")) throw invalid("identifier");
    return value;
  }

  private static String binding(String value) {
    if (!value.matches("[a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*){1,3}"))
      throw invalid("binding");
    fieldType(value);
    return value;
  }

  private static Class<?> fieldType(String path) {
    try {
      Class<?> owner = Settings.class;
      String[] names = path.split("\\.");
      for (int i = 0; i < names.length; i++) {
        Field field = owner.getField(names[i]);
        int flags = field.getModifiers();
        if (Modifier.isStatic(flags) || Modifier.isFinal(flags) || Modifier.isTransient(flags))
          throw invalid("noneditable binding");
        owner = field.getType();
        if (i < names.length - 1 && !owner.getName().startsWith("dev.nightclient.config."))
          throw invalid("binding owner");
      }
      if (owner != boolean.class
          && owner != int.class&& owner != float.class&& owner != String.class)
        throw invalid("nonscalar binding");
      return owner;
    } catch (NoSuchFieldException error) {
      throw invalid("unknown setting: " + path);
    }
  }

  private static Field[] fieldPath(String path) {
    String[] names = path.split("\\.");
    Field[] fields = new Field[names.length];
    Class<?> owner = Settings.class;
    try {
      for (int i = 0; i < names.length; i++) {
        fields[i] = owner.getField(names[i]);
        owner = fields[i].getType();
      }
      return fields;
    } catch (NoSuchFieldException error) {
      throw invalid("unknown setting: " + path);
    }
  }

  private static JsonObject object(JsonElement value) {
    if (value == null || !value.isJsonObject()) throw invalid("object");
    return value.getAsJsonObject();
  }

  private static String string(JsonObject object, String name) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
        || value.getAsString().length() > 160)
      throw invalid("string: " + name);
    return value.getAsString();
  }

  private static float number(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw invalid("number");
    float number = value.getAsFloat();
    if (!Float.isFinite(number) || Math.abs(number) > 1000000) throw invalid("finite number");
    return number;
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("Invalid UI catalog: " + reason);
  }
}
