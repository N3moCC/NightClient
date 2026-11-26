package dev.nightclient.skin;

import dev.nightclient.config.Theme;
import dev.nightclient.config.ThemeEffects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkinDocument {
  private static final int THEME_VARIANTS_PER_STATE = 8;
  private static final int MAX_INSTANCES_PER_NODE = 1024;

  public static final class Node {
    public final String tag, id, text;
    public final Set<String> classes;
    public final Map<String, String> attributes;
    public final List<Node> children;

    public final Map<String, String> inlineStyles;
    final Map<Integer, Map<String, String>> stateStyles;
    public final int sourceLine, sourceColumn;

    public Node(String tag, Map<String, String> attributes, String text, List<Node> children) {
      this(tag, attributes, text, children, Collections.<String, String>emptyMap(),
          Collections.<Integer, Map<String, String>>emptyMap(), 0, 0);
    }

    Node(String tag, Map<String, String> attributes, String text, List<Node> children,
        Map<String, String> styles, Map<Integer, Map<String, String>> states, int line,
        int column) {
      this.tag = tag;
      this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(attributes));
      this.id = attributes.containsKey("id")
          ? attributes.get("id")
          : ("component".equals(tag) ? attributes.get("name") : null);
      String cls = attributes.get("class");
      Set<String> set = new LinkedHashSet<String>();
      if (cls != null)
        for (String c : WHITESPACE.split(cls.trim()))
          if (!c.isEmpty()) set.add(c);
      this.classes = Collections.unmodifiableSet(set);
      this.text = text;
      this.children = Collections.unmodifiableList(new ArrayList<Node>(children));
      this.inlineStyles = Collections.unmodifiableMap(new LinkedHashMap<String, String>(styles));
      Map<Integer, Map<String, String>> variants =
          new LinkedHashMap<Integer, Map<String, String>>();
      for (Map.Entry<Integer, Map<String, String>> entry : states.entrySet())
        variants.put(entry.getKey(),
            Collections.unmodifiableMap(new LinkedHashMap<String, String>(entry.getValue())));
      this.stateStyles = Collections.unmodifiableMap(variants);
      this.sourceLine = line;
      this.sourceColumn = column;
    }

    public String attribute(String name, String fallback) {
      String value = attributes.get(name);
      return value == null ? fallback : value;
    }
  }

  private static final java.util.regex.Pattern INSTANCE_ID =
      java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,95}");
  private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
  private static final java.util.regex.Pattern VARIABLE_PATH =
      java.util.regex.Pattern.compile("vars\\.[A-Za-z_][A-Za-z0-9_]*");
  private static final java.util.regex.Pattern EXPRESSION =
      java.util.regex.Pattern.compile("(?s).*\\{\\{\\s*=.*");
  private static final Set<String> INHERITED = new LinkedHashSet<String>(Arrays.asList("color",
      "text", "muted", "accent", "secondary-accent", "font-size", "font-weight", "font-family",
      "font-tracking", "letter-spacing", "text-align", "font-scale", "transition-duration",
      "easing", "hover-scale", "press-scale", "enter-opacity", "enter-offset-x", "enter-offset-y",
      "enter-duration", "exit-duration", "animate-layout", "animate-value", "animation-stagger"));
  private final Node root, menu, hud;
  private final Map<String, Node> components;
  private final SkinStylesheet stylesheet;
  private final SkinStylesheet overlayStylesheet;
  private final Map<String, String> defaults;
  private final ThemePatch defaultsPatch;
  private final IdentityHashMap<Node, Map<String, String>[]> computed =
      new IdentityHashMap<Node, Map<String, String>[]>();
  private final IdentityHashMap<Node, ThemePatch[]> patches =
      new IdentityHashMap<Node, ThemePatch[]>();
  private static final Object EMPTY_RECIPE_KEY = new Object();
  private final Map<Map<String, Object>, Object> canonicalRecipeKeys =
      new java.util.HashMap<Map<String, Object>, Object>(32);
  private final IdentityHashMap<Object, Cache> patchThemeCache =
      new IdentityHashMap<Object, Cache>(64);
  private final IdentityHashMap<Node, Map<String, String>[]> dynamicThemeProperties =
      new IdentityHashMap<Node, Map<String, String>[]>();
  private final IdentityHashMap<Node, ResolvedCache[]> resolvedThemes =
      new IdentityHashMap<Node, ResolvedCache[]>();
  private final IdentityHashMap<Node, ThemeBindingPlan[]> bindingPlans =
      new IdentityHashMap<Node, ThemeBindingPlan[]>();
  private final Map<String, Node> targets = new LinkedHashMap<String, Node>();
  private final Map<Map<String, String>, ThemePatch> resolvedPatches =
      new LinkedHashMap<Map<String, String>, ThemePatch>();
  private final IdentityHashMap<Node, Map<String, Node>> instances =
      new IdentityHashMap<Node, Map<String, Node>>();
  private final Map<Node, Node> parents = new IdentityHashMap<Node, Node>();
  private final int nodeCount;
  private final long[] backdropStamps = new long[3];
  private final boolean[] backdropValues = new boolean[3], backdropCached = new boolean[3];
  private ThemePatch[][] scopeSurfacePatches;

  private static final String[] HUD_WIDGETS = {"cooldowns", "inventory", "tnt", "crosshair",
      "armor", "potions", "food", "target", "waypoints", "scoreboard", "keystrokes", "coordinates",
      "ping", "fps", "betterChat", "minimap"};
  private long hudBackdropStamp;
  private int hudBackdropDependencies = -1, hudBackdropValues;
  private boolean hudBackdropResult;

  SkinDocument(Node root) throws IOException {
    this(root, null);
  }

  SkinDocument(Node root, SkinStylesheet nativeStylesheet) throws IOException {
    this(root, nativeStylesheet, Collections.<String, String>emptyMap());
  }

  SkinDocument(Node root, SkinStylesheet nativeStylesheet, Map<String, String> defaults)
      throws IOException {
    this(root, nativeStylesheet, defaults, null);
  }

  SkinDocument(Node root, SkinStylesheet nativeStylesheet, Map<String, String> defaults,
      SkinStylesheet overlayStylesheet) throws IOException {
    this.defaults = Collections.unmodifiableMap(new LinkedHashMap<String, String>(defaults));
    this.defaultsPatch = new ThemePatch(defaults);
    this.root = root;
    Node m = null, h = null;
    Map<String, Node> parts = new LinkedHashMap<String, Node>();
    StringBuilder css = new StringBuilder();
    for (Node node : root.children) {
      if ("style".equals(node.tag)) {
        if (!node.children.isEmpty()) throw new IOException("Style must contain CSS text");
        css.append(node.text).append('\n');
      } else if ("menu".equals(node.tag)) {
        if (m != null) throw new IOException("Only one menu is allowed");
        m = node;
      } else if ("hud".equals(node.tag)) {
        if (h != null) throw new IOException("Only one HUD is allowed");
        h = node;
      } else if ("component".equals(node.tag)) {
        String name = node.attributes.get("name");
        if (name == null || !INSTANCE_ID.matcher(name).matches())
          throw new IOException("Component requires a valid name");
        if (parts.put(name, node) != null) throw new IOException("Duplicate component: " + name);
      } else
        throw new IOException("Root children must be style/menu/hud/component");
    }
    menu = m;
    hud = h;
    components = Collections.unmodifiableMap(parts);
    stylesheet = nativeStylesheet == null ? new SkinStylesheet(css.toString()) : nativeStylesheet;
    this.overlayStylesheet = overlayStylesheet;
    Set<String> ids = new LinkedHashSet<String>();
    nodeCount = index(root, null, ids);
    compile(root);
  }

  private int index(Node node, Node parent, Set<String> ids) throws IOException {
    if (parent != null) parents.put(node, parent);
    if (node.id != null && !node.id.contains("{{") && !"component".equals(node.tag)) {
      if (!ids.add(node.id)) throw new IOException("Duplicate element ID: " + node.id);
      targets.put(node.id, node);
    }
    int count = 1;
    for (Node child : node.children) count += index(child, node, ids);
    return count;
  }

  private void compile(Node node) throws IOException {
    compileNode(node, true);
  }

  @SuppressWarnings("unchecked")
  private void compileNode(Node node, boolean recurse) throws IOException {
    Map<String, String>[] variants = (Map<String, String>[]) new Map < ?, ? > [8];
    ThemePatch[] typed = new ThemePatch[8];
    Map<String, String>[] dynamic = (Map<String, String>[]) new Map < ?, ? > [8];
    Node parent = parents.get(node);
    Map<String, String>[] parentComputed = parent == null ? null : computed.get(parent);
    ThemePatch[] parentPatches = parent == null ? null : patches.get(parent);
    Map<String, String>[] parentDynamic =
        parent == null ? null : dynamicThemeProperties.get(parent);
    Map<String, String> own0 = null, overlay0 = null;
    for (int state = 0; state < 8; state++) {
      Map<String, String> own = stylesheet.own(node, state);
      Map<String, String> overlay = overlayStylesheet == null
          ? Collections.<String, String>emptyMap()
          : overlayStylesheet.rules(node, state);
      if (state == 0) {
        own0 = own;
        overlay0 = overlay;
      } else if (own.equals(own0) && overlay.equals(overlay0)
          && (parent == null
              || (parentComputed[state] == parentComputed[0]
                  && parentPatches[state] == parentPatches[0]))) {
        variants[state] = variants[0];
        typed[state] = typed[0];
        dynamic[state] = dynamic[0];
        continue;
      }
      Map<String, String> values = new LinkedHashMap<String, String>();
      if (parent != null)
        for (Map.Entry<String, String> e : parentComputed[state].entrySet())
          if (INHERITED.contains(e.getKey()) || e.getKey().startsWith("--"))
            values.put(e.getKey(), e.getValue());
      values.putAll(own);
      if (!overlay.isEmpty()) values.putAll(overlay);
      if (values.size() > 128) throw new IOException("Too many computed CSS properties");
      resolveVariables(values);
      variants[state] = Collections.unmodifiableMap(values);
      if (parent != null && parentPatches[state].source.entrySet().containsAll(values.entrySet())) {
        typed[state] = parentPatches[state];
        dynamic[state] = parentDynamic[state];
      } else {
        Map<String, String> themeValues = new LinkedHashMap<String, String>();
        if (parent != null) themeValues.putAll(parentPatches[state].source);
        themeValues.putAll(values);
        ThemePatch created = new ThemePatch(themeValues);
        assignRecipeKey(created);
        typed[state] = created;
        Map<String, String> bound = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : themeValues.entrySet())
          if (entry.getValue().contains("{{") && ThemePatch.themeProperty(entry.getKey()))
            bound.put(entry.getKey(), entry.getValue());
        dynamic[state] = Collections.unmodifiableMap(bound);
      }
    }
    computed.put(node, variants);
    patches.put(node, typed);
    dynamicThemeProperties.put(node, dynamic);
    scopeSurfacePatches = null;
    if (recurse)
      for (Node child : node.children) compileNode(child, true);
  }

  public static String variablePath(String name) {
    return "vars." + name.substring(2).replace('-', '_');
  }

  public static boolean matchesVariablePath(String cssKey, String path) {
    if (cssKey == null || path == null || cssKey.length() < 2
        || cssKey.charAt(0) != '-' || cssKey.charAt(1) != '-')
      return false;
    int varLen = cssKey.length() - 2;
    if (path.length() != 5 + varLen || !path.startsWith("vars.")) return false;
    for (int i = 0; i < varLen; i++) {
      char c = cssKey.charAt(2 + i);
      if (c == '-') c = '_';
      if (c != path.charAt(5 + i)) return false;
    }
    return true;
  }

  static void resolveVariables(Map<String, String> values) throws IOException {
    Map<String, String> raw = new LinkedHashMap<String, String>(values);
    Map<String, String> names = new LinkedHashMap<String, String>();
    for (String name : raw.keySet())
      if (name.startsWith("--")) {
        String path = variablePath(name);
        if (!VARIABLE_PATH.matcher(path).matches())
          throw new IOException("Invalid variable name: " + name);
        String old = names.put(path, name);
        if (old != null && !old.equals(name))
          throw new IOException("Ambiguous variable names: " + old + " / " + name);
        checkVariable(name, raw, new LinkedHashSet<String>(), 0);
      }
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (entry.getKey().startsWith("--")) continue;
      boolean expression = EXPRESSION.matcher(entry.getValue()).matches();
      entry.setValue(expandVariables(entry.getValue(), raw, expression, 0));
    }
  }

  private static void checkVariable(
      String name, Map<String, String> raw, Set<String> chain, int depth) throws IOException {
    if (depth > 24 || !chain.add(name))
      throw new IOException("Cyclic or excessive NC variable dependencies: " + name);
    String value = raw.get(name);
    if (value == null) throw new IOException("Unknown CSS variable: " + name);
    int at = 0;
    while ((at = value.indexOf("var(", at)) >= 0) {
      int end = value.indexOf(')', at);
      if (end < 0) throw new IOException("Unclosed CSS variable");
      String[] ref = value.substring(at + 4, end).split(",", 2);
      String key = ref[0].trim();
      if (raw.containsKey(key))
        checkVariable(key, raw, new LinkedHashSet<String>(chain), depth + 1);
      else if (ref.length < 2)
        throw new IOException("Unknown CSS variable: " + key);
      at = end + 1;
    }
  }

  public static String expressionVariables(String expression, Map<String, String> variables)
      throws IOException {
    return expandVariables(expression, variables, true, 0);
  }

  private static String expandVariables(
      String value, Map<String, String> raw, boolean expression, int depth) throws IOException {
    if (depth > 24) throw new IOException("Excessive NC variable dependencies");
    int at = 0;
    while ((at = value.indexOf("var(", at)) >= 0) {
      int end = value.indexOf(')', at);
      if (end < 0) throw new IOException("Unclosed CSS variable");
      String[] ref = value.substring(at + 4, end).split(",", 2);
      String key = ref[0].trim(), replacement = raw.get(key);
      if (replacement == null) {
        if (ref.length < 2) throw new IOException("Unknown CSS variable: " + key);
        replacement = ref[1].trim();
      } else
        replacement =
            expression ? variablePath(key) : expandVariables(replacement, raw, false, depth + 1);
      if (value.length() - end - 1 + at + replacement.length() > 4096)
        throw new IOException("CSS variable expansion exceeds 4096 characters");
      value = value.substring(0, at) + replacement + value.substring(end + 1);
      at += replacement.length();
    }
    return value;
  }

  public boolean hasIdStyle(String id) {
    return id != null
        && (stylesheet.hasIdRule(id)
            || overlayStylesheet != null && overlayStylesheet.hasIdRule(id));
  }

  public Node instance(Node node, String id) {
    if (node == null || id == null || id.equals(node.id)) return node;
    Map<String, Node> values = instances.get(node);
    if (values != null) {
      Node known = values.get(id);
      if (known != null) return known;
    }
    if (!INSTANCE_ID.matcher(id).matches()) return node;
    if (values == null) {
      values = new LinkedHashMap<String, Node>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Node> eldest) {
          if (size() > MAX_INSTANCES_PER_NODE) {
            evictSubtree(eldest.getValue());
            return true;
          }
          return false;
        }
      };
      instances.put(node, values);
    }
    Map<String, String> attrs = new LinkedHashMap<String, String>(node.attributes);
    attrs.put("id", id);
    boolean deep = stylesheet.idRuleChangesInheritedOrVars(id, INHERITED)
        || overlayStylesheet != null
            && overlayStylesheet.idRuleChangesInheritedOrVars(id, INHERITED);
    Node instance;
    if (deep) {
      instance = copyInstance(node, parents.get(node), attrs);
    } else {
      instance = new Node(node.tag, attrs, node.text, node.children, node.inlineStyles,
          node.stateStyles, node.sourceLine, node.sourceColumn);
      Node parent = parents.get(node);
      if (parent != null) parents.put(instance, parent);
    }
    try {
      compileNode(instance, deep);
    } catch (IOException error) {
      evictSubtree(instance);
      throw new IllegalStateException(error);
    }
    values.put(id, instance);
    return instance;
  }

  private void evictSubtree(Node node) {
    if (node == null) return;
    parents.remove(node);
    computed.remove(node);
    patches.remove(node);
    dynamicThemeProperties.remove(node);
    resolvedThemes.remove(node);
    bindingPlans.remove(node);
    instances.remove(node);
    for (Node child : node.children)
      if (parents.get(child) == node) evictSubtree(child);
  }

  private Node copyInstance(Node source, Node parent, Map<String, String> attributes) {
    List<Node> children = new ArrayList<Node>();
    for (Node child : source.children) children.add(copyInstance(child, null, child.attributes));
    Node result = new Node(source.tag, attributes, source.text, children, source.inlineStyles,
        source.stateStyles, source.sourceLine, source.sourceColumn);
    if (parent != null) parents.put(result, parent);
    for (Node child : children) parents.put(child, result);
    return result;
  }

  public Node root() {
    return root;
  }

  static SkinDocument layer(SkinDocument base, SkinDocument override) throws IOException {
    boolean layoutOnly = override.layoutOnly();
    Map<String, String> values = new LinkedHashMap<String, String>(base.root.inlineStyles);
    if (!layoutOnly) values.putAll(override.root.inlineStyles);
    List<Node> children = new ArrayList<Node>();
    children.add(override.menu == null ? base.menu : override.menu);
    children.add(override.hud == null ? base.hud : override.hud);
    Map<String, Node> parts = new LinkedHashMap<String, Node>(base.components);
    parts.putAll(override.components);
    children.addAll(parts.values());
    children.removeAll(Collections.singleton(null));
    Map<String, String> state = new LinkedHashMap<String, String>(base.root.attributes);
    if (!layoutOnly) state.putAll(override.root.attributes);
    if (state.size() > 64) throw new IOException("Composed NC state exceeds 64 entries");
    Node root = new Node("nightclient", state, "", children, values,
        layoutOnly ? base.root.stateStyles : override.root.stateStyles, 1, 1);
    Map<String, String> defaults = new LinkedHashMap<String, String>(base.defaults);
    if (!layoutOnly) defaults.putAll(override.defaults);
    if (layoutOnly) return new SkinDocument(root, base.stylesheet, defaults, override.stylesheet);
    return new SkinDocument(root,
        SkinStylesheet.layer(
            base.stylesheet, override.stylesheet, override.menu != null, override.hud != null),
        defaults);
  }

  public Node menu() {
    return menu;
  }

  public boolean layoutOnly() {
    return menu == null && hud == null && defaults.isEmpty() && root.inlineStyles.isEmpty();
  }

  public void applyDefaults(Theme target) {
    defaultsPatch.apply(target);
    patches.get(root)[0].apply(target);
  }

  public Theme resolvedTheme(Node node, Theme base, String state, Map<String, String> values) {
    int stateIndex = state(state);
    long stamp = fingerprint(base);
    return applyResolvedTheme(
        node, resolveTheme(node, base, stateIndex, stamp), stamp, stateIndex, values, null, null);
  }

  private void assignRecipeKey(ThemePatch patch) {
    if (patch.empty) {
      patch.cacheKey = EMPTY_RECIPE_KEY;
      return;
    }
    Object key = canonicalRecipeKeys.get(patch.appliedValues);
    if (key == null) {
      key = new Object();
      canonicalRecipeKeys.put(patch.appliedValues, key);
    }
    patch.cacheKey = key;
  }

  private Theme applyResolvedTheme(Node node, Theme result, long baseStamp, int stateIndex,
      Map<String, String> values, String[] planKeys, String[] candidate) {
    Map<String, String>[] bound = dynamicThemeProperties.get(node);
    if (bound == null || bound[stateIndex].isEmpty()) return result;
    ResolvedCache[] variants = resolvedThemes.get(node);
    if (variants == null) {
      variants = new ResolvedCache[8];
      resolvedThemes.put(node, variants);
    }
    ResolvedCache head = variants[stateIndex], previous = null, beforeTail = null, tail = null;
    int count = 0;
    for (ResolvedCache cached = head; cached != null; cached = cached.next) {
      if (cached.baseStamp == baseStamp && cached.base == result
          && (candidate == null ? cached.matches(values) : cached.matches(candidate))) {
        if (previous != null) {
          previous.next = cached.next;
          cached.next = head;
          variants[stateIndex] = cached;
        }
        if (result.effects != null) cached.theme.effects.time = result.effects.time;
        return cached.theme;
      }
      beforeTail = previous;
      previous = cached;
      tail = cached;
      count++;
    }

    String[] keys = head == null
        ? (planKeys == null
                  ? bound[stateIndex].keySet().toArray(new String[bound[stateIndex].size()])
                  : planKeys)
        : head.keys;
    ResolvedCache entry;
    if (count >= 4 && tail != null) {
      if (beforeTail != null) beforeTail.next = null;
      else head = null;
      entry = tail;
      entry.base = result;
      entry.baseStamp = baseStamp;
      if (entry.values.length != keys.length) entry.values = new String[keys.length];
      entry.theme.copyFrom(result);
    } else {
      entry = new ResolvedCache(result, baseStamp, keys, new String[keys.length], result.copy());
    }
    try {
      for (int i = 0; i < keys.length; i++) {
        String raw = candidate == null ? values.get(keys[i]) : candidate[i];
        entry.values[i] = raw;
        ThemePatch.applyDynamic(entry.theme, keys[i], raw);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid resolved NC style", e);
    }
    entry.next = head;
    variants[stateIndex] = entry;
    return entry.theme;
  }

  public Map<String, String> themeBindings(Node node, String state) {
    Map<String, String>[] variants = dynamicThemeProperties.get(node);
    return variants == null ? Collections.<String, String>emptyMap() : variants[state(state)];
  }

  public static final class ThemeBindingPlan {
    private final SkinDocument owner;
    private final Node node;
    private final int state;
    private final String[] keys, expressions;

    private ThemeBindingPlan(
        SkinDocument owner, Node node, int state, Map<String, String> properties) {
      this.owner = owner;
      this.node = node;
      this.state = state;
      keys = new String[properties.size()];
      expressions = new String[keys.length];
      int index = 0;
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        keys[index] = entry.getKey();
        expressions[index++] = entry.getValue();
      }
    }

    public int size() {
      return keys.length;
    }

    public String key(int index) {
      return keys[index];
    }

    public String expression(int index) {
      return expressions[index];
    }
  }

  public ThemeBindingPlan themeBindingPlan(Node node, String state) {
    int index = state(state);
    Map<String, String>[] properties = dynamicThemeProperties.get(node);
    if (properties == null || properties[index].isEmpty()) return null;
    ThemeBindingPlan[] plans = bindingPlans.get(node);
    if (plans == null) {
      plans = new ThemeBindingPlan[8];
      bindingPlans.put(node, plans);
    }
    ThemeBindingPlan plan = plans[index];
    if (plan == null) {
      plan = new ThemeBindingPlan(this, node, index, properties[index]);
      plans[index] = plan;
    }
    return plan;
  }

  public Map<String, Object> stateDefaults() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, String> e : root.attributes.entrySet())
      if (e.getKey().startsWith("state:")) {
        String v = e.getValue();
        values.put(e.getKey().substring(6),
            v.startsWith("b:")       ? Boolean.valueOf(v.substring(2))
                : v.startsWith("n:") ? Float.valueOf(v.substring(2))
                                     : v.substring(2));
      }
    return Collections.unmodifiableMap(values);
  }

  public Node hud() {
    return hud;
  }

  public Node component(String id) {
    return components.get(id);
  }

  public Map<String, Node> components() {
    return components;
  }

  public int nodeCount() {
    return nodeCount;
  }

  public Map<String, String> styles(Node node, String state) {
    Map<String, String>[] variants = computed.get(node);
    return variants == null ? Collections.<String, String>emptyMap() : variants[state(state)];
  }

  public static int color(String value, int fallback) {
    if (value == null) return fallback;
    try {
      return ThemePatch.color(value);
    } catch (IOException error) {
      return fallback;
    }
  }

  public int color(Node node, String state, String property, int fallback) {
    ThemePatch[] p = patches.get(node);
    return p == null ? fallback : p[state(state)].colorValue(property, fallback);
  }

  public Theme theme(Node node, Theme base, String state) {
    return resolveTheme(node, base, state(state));
  }

  public ThemeResolver themeResolver() {
    return new ThemeResolver();
  }

  public final class ThemeResolver {
    private final IdentityHashMap<Theme, ThemeStamp> stamps =
        new IdentityHashMap<Theme, ThemeStamp>();
    private long generation;

    public void beginFrame() {
      if (stamps.size() > 1024 || generation == Long.MAX_VALUE) {
        stamps.clear();
        generation = 0;
      }
      generation++;
    }

    public Theme theme(Node node, Theme base, String state) {
      return resolveTheme(node, base, state(state), stamp(base));
    }

    public Theme resolvedTheme(Node node, Theme base, String state, Map<String, String> values) {
      int index = state(state);
      long baseStamp = stamp(base);
      return applyResolvedTheme(node, resolveTheme(node, base, index, baseStamp), baseStamp,
          index, values, null, null);
    }

    public Theme resolvedTheme(ThemeBindingPlan plan, Theme base, String[] values) {
      if (plan == null || plan.owner != SkinDocument.this)
        throw new IllegalArgumentException("Theme binding plan belongs to another document");
      if (values == null || values.length < plan.keys.length)
        throw new IllegalArgumentException("Missing resolved theme values");
      long baseStamp = stamp(base);
      return applyResolvedTheme(plan.node, resolveTheme(plan.node, base, plan.state, baseStamp),
          baseStamp, plan.state, null, plan.keys, values);
    }

    private long stamp(Theme base) {
      if (base == null) throw new IllegalArgumentException("Theme base is required");
      if (generation == 0) return fingerprint(base);
      ThemeStamp stored = stamps.get(base);
      if (stored == null) {
        stored = new ThemeStamp();
        stamps.put(base, stored);
      }
      if (stored.generation != generation) {
        stored.fingerprint = fingerprint(base);
        stored.generation = generation;
      }
      return stored.fingerprint;
    }
  }

  private static final class ThemeStamp {
    long generation, fingerprint;
  }

  public Theme theme(String id, Theme base) {
    Node node = targets.get(id);
    if (node == null) node = components.get(id);
    if (node == null && "menu".equals(id)) node = menu;
    if (node == null && "hud".equals(id)) node = hud;
    if (node == null) {
      String scope = id != null && id.startsWith("menu") ? "menu" : "hud";
      Map<String, String> attrs = new LinkedHashMap<String, String>();
      attrs.put("id", id == null ? scope : id);
      node = new Node(scope, attrs, "", Collections.<Node>emptyList());
      Node parent = "menu".equals(scope) && menu != null
          ? menu
          : (!"menu".equals(scope) && hud != null ? hud : root);
      parents.put(node, parent);
      try {
        compile(node);
      } catch (IOException error) {
        throw new IllegalStateException(error);
      }
      targets.put(id, node);
    }
    return resolveTheme(node, base, 0);
  }

  private Theme resolveTheme(Node node, Theme base, int state) {
    if (base == null) throw new IllegalArgumentException("Theme base is required");
    return resolveTheme(node, base, state, fingerprint(base));
  }

  private Theme resolveTheme(Node node, Theme base, int state, long stamp) {
    ThemePatch[] recipes = patches.get(node);
    if (recipes == null) return base;
    ThemePatch recipe = recipes[state];
    Object key = recipe.cacheKey;
    if (key == null) {
      assignRecipeKey(recipe);
      key = recipe.cacheKey;
    }
    Cache head = patchThemeCache.get(key), cached = head, previous = null;
    while (cached != null && cached.fingerprint != stamp) {
      previous = cached;
      cached = cached.next;
    }
    if (cached == null) {
      Theme theme = base.copy();
      if (!recipe.empty) recipe.apply(theme);
      cached = new Cache(stamp, theme);
      cached.next = head;
      patchThemeCache.put(key, cached);
      Cache tail = cached;
      for (int i = 1; tail.next != null; i++, tail = tail.next)
        if (i == THEME_VARIANTS_PER_STATE) {
          tail.next = null;
          break;
        }
    } else if (previous != null) {
      previous.next = cached.next;
      cached.next = head;
      patchThemeCache.put(key, cached);
    }
    if (base.effects != null) cached.theme.effects.time = base.effects.time;
    return cached.theme;
  }

  public boolean usesBackdrop(Theme base) {
    return backdrop(base, 0);
  }

  public boolean usesMenuBackdrop(Theme base) {
    return backdrop(base, 1);
  }

  public boolean usesHudBackdrop(Theme base) {
    return backdrop(base, 2);
  }

  public boolean usesComponentBackdrop(String id, Theme base) {
    Node component = components.get(id);
    return component != null && base != null && activeBackdrop(component, base, widget -> true, 0);
  }

  public boolean usesHudBackdrop(Theme base, java.util.function.Predicate<String> activeWidget) {
    if (hud == null || base == null) return false;
    long stamp = backdropFingerprint(base);
    if (hudBackdropDependencies >= 0 && hudBackdropStamp == stamp) {
      int values = 0;
      for (int bit = 0; bit < HUD_WIDGETS.length; bit++)
        if ((hudBackdropDependencies & (1 << bit)) != 0 && activeWidget.test(HUD_WIDGETS[bit]))
          values |= 1 << bit;
      if (values == hudBackdropValues) return hudBackdropResult;
    }
    final int[] dependencies = {0}, values = {0};
    boolean result = activeBackdrop(hud, base, id -> {
      boolean active = activeWidget.test(id);
      int bit = hudWidgetBit(id);
      if (bit >= 0) {
        dependencies[0] |= 1 << bit;
        if (active) values[0] |= 1 << bit;
      }
      return active;
    }, 0);
    hudBackdropStamp = stamp;
    hudBackdropDependencies = dependencies[0];
    hudBackdropValues = values[0];
    hudBackdropResult = result;
    return result;
  }

  private static int hudWidgetBit(String id) {
    if ("cooldowns".equals(id)) return 0;
    if ("inventory".equals(id)) return 1;
    if ("tnt".equals(id)) return 2;
    if ("crosshair".equals(id)) return 3;
    if ("armor".equals(id)) return 4;
    if ("potions".equals(id)) return 5;
    if ("food".equals(id)) return 6;
    if ("target".equals(id)) return 7;
    if ("waypoints".equals(id)) return 8;
    if ("scoreboard".equals(id)) return 9;
    if ("keystrokes".equals(id)) return 10;
    if ("coordinates".equals(id)) return 11;
    if ("ping".equals(id)) return 12;
    if ("fps".equals(id)) return 13;
    if ("betterChat".equals(id)) return 14;
    if ("minimap".equals(id)) return 15;
    return -1;
  }

  private static final Set<String> GAME_WIDGETS =
      new java.util.HashSet<String>(Arrays.asList("cooldowns", "inventory", "tnt", "crosshair",
          "armor", "potions", "food", "target", "waypoints", "scoreboard", "keystrokes",
          "coordinates", "ping", "fps", "betterChat", "minimap"));
  private static final Set<String> SURFACE_TAGS = new java.util.HashSet<String>(Arrays.asList(
      "panel", "box", "button", "toggle", "switch", "slider", "input", "choice", "color"));

  private boolean activeBackdrop(
      Node node, Theme base, java.util.function.Predicate<String> activeWidget, int depth) {
    if (depth >= 32) return false;
    String tag = node.tag;
    boolean widget = GAME_WIDGETS.contains(tag);
    if (widget && !activeWidget.test(tag)) return false;
    boolean surface = widget && !"crosshair".equals(tag) || SURFACE_TAGS.contains(tag);
    for (int state = 0; state < 8; state++) {
      Map<String, String> style = computed.get(node)[state];
      if ("none".equals(style.get("display")) || "false".equals(style.get("visible"))) continue;
      if (!surface && !style.containsKey("background") && !style.containsKey("background-color"))
        continue;
      Theme t = resolveTheme(node, base, state);
      if (needsBackdrop(t)) return true;
    }
    if (widget) {
      Node part = components.get(tag);
      if (part != null && activeBackdrop(part, base, activeWidget, depth + 1)) return true;
    }
    for (Node child : node.children)
      if (activeBackdrop(child, base, activeWidget, depth + 1)) return true;
    return false;
  }

  private boolean backdrop(Theme base, int scope) {
    if (base == null) return false;
    long stamp = backdropFingerprint(base);
    if (backdropCached[scope] && backdropStamps[scope] == stamp) return backdropValues[scope];
    ThemePatch[] candidates = surfacePatchesForScope(scope);
    boolean result = false;
    if (candidates.length > 0) {
      Theme scratch = base.copy();
      for (int i = 0; i < candidates.length; i++) {
        ThemePatch patch = candidates[i];
        if (patch.isEmpty()) {
          if (needsBackdrop(base)) {
            result = true;
            break;
          }
          continue;
        }
        scratch.copyFrom(base);
        patch.apply(scratch);
        if (needsBackdrop(scratch)) {
          result = true;
          break;
        }
      }
    }
    backdropStamps[scope] = stamp;
    backdropValues[scope] = result;
    backdropCached[scope] = true;
    return result;
  }

  private ThemePatch[] surfacePatchesForScope(int scope) {
    ThemePatch[][] table = scopeSurfacePatches;
    if (table == null) {
      table = new ThemePatch[3][];
      scopeSurfacePatches = table;
    }
    if (table[scope] != null) return table[scope];
    IdentityHashMap<ThemePatch, Boolean> unique = new IdentityHashMap<ThemePatch, Boolean>();
    List<ThemePatch> list = new ArrayList<ThemePatch>();
    for (Map.Entry<Node, ThemePatch[]> entry : patches.entrySet()) {
      Node node = entry.getKey();
      String tag = node.tag;
      if (!inScope(node, scope) || "nightclient".equals(tag) || "style".equals(tag)
          || "text".equals(tag) || "label".equals(tag) || "spacer".equals(tag)
          || "icon".equals(tag) || "item".equals(tag))
        continue;
      boolean surface = "menu".equals(tag) || SURFACE_TAGS.contains(tag)
          || GAME_WIDGETS.contains(tag) && !"crosshair".equals(tag);
      ThemePatch[] nodePatches = entry.getValue();
      Map<String, String>[] nodeStyles = computed.get(node);
      for (int state = 0; state < 8; state++) {
        Map<String, String> style = nodeStyles[state];
        if ("none".equals(style.get("display")) || "false".equals(style.get("visible"))) continue;
        if (!surface && !style.containsKey("background") && !style.containsKey("background-color"))
          continue;
        ThemePatch patch = nodePatches[state];
        if (unique.put(patch, Boolean.TRUE) == null) list.add(patch);
      }
    }
    ThemePatch[] array = list.toArray(new ThemePatch[list.size()]);
    table[scope] = array;
    return array;
  }

  private static long backdropFingerprint(Theme t) {
    long h = 1469598103934665603L;
    h = add(h, t.blurRadius);
    h = add(h, t.blurMix);
    h = add(h, t.effects != null && t.effects.backgroundBlur ? 1 : 0);
    return h;
  }

  private static boolean needsBackdrop(Theme t) {
    return Float.isFinite(t.blurRadius) && t.blurRadius > 0 && Float.isFinite(t.blurMix)
        && t.blurMix > 0 && t.effects != null && t.effects.backgroundBlur;
  }

  private boolean inScope(Node node, int scope) {
    if (scope == 0) return true;
    for (Node cursor = node; cursor != null; cursor = parents.get(cursor)) {
      if (cursor == menu) return scope == 1;
      if (cursor == hud) return scope == 2;
      if ("component".equals(cursor.tag)) {
        String id = cursor.attributes.get("name");
        if (id != null && ("menu".equals(id) || id.startsWith("menu."))) return scope == 1;
        if (id != null && ("hud".equals(id) || id.startsWith("hud."))) return scope == 2;

        return scope == 1 ? menu != null : hud != null;
      }
    }
    return false;
  }

  public void clearCache() {
    patchThemeCache.clear();
    resolvedPatches.clear();
    resolvedThemes.clear();
    scopeSurfacePatches = null;
    hudBackdropDependencies = -1;
    for (int i = 0; i < backdropCached.length; i++) backdropCached[i] = false;
  }

  public void invalidate() {
    clearCache();
  }

  private static int state(String value) {
    if (value == null) return 0;
    return (value.contains("hover") ? 1 : 0) | (value.contains("focus") ? 2 : 0)
        | (value.contains("active") ? 4 : 0);
  }

  private static final class ResolvedCache {
    Theme base;
    long baseStamp;
    final Theme theme;
    final String[] keys;
    String[] values;
    ResolvedCache next;

    ResolvedCache(Theme base, long baseStamp, String[] keys, String[] values, Theme theme) {
      this.base = base;
      this.baseStamp = baseStamp;
      this.keys = keys;
      this.values = values;
      this.theme = theme;
    }

    boolean matches(String[] candidate) {
      for (int i = 0; i < keys.length; i++) {
        String value = candidate[i], stored = values[i];
        if (stored != value && (stored == null || !stored.equals(value))) return false;
      }
      return true;
    }

    boolean matches(Map<String, String> candidate) {
      for (int i = 0; i < keys.length; i++) {
        String value = candidate.get(keys[i]), stored = values[i];
        if (stored != value && (stored == null || !stored.equals(value))) return false;
      }
      return true;
    }
  }

  private static final class Cache {
    final long fingerprint;
    final Theme theme;
    Cache next;

    Cache(long fingerprint, Theme theme) {
      this.fingerprint = fingerprint;
      this.theme = theme;
    }
  }

  private static long add(long hash, int value) {
    return (hash ^ value) * 1099511628211L;
  }

  private static long add(long hash, float value) {
    return add(hash, Float.floatToIntBits(value));
  }

  private static long add(long hash, String value) {
    return add(hash, value == null ? 0 : value.hashCode());
  }

  private static long fingerprint(Theme t) {
    long h = 1469598103934665603L;
    h = add(h, t.name);
    h = add(h, t.mode);
    h = add(h, t.customBackground);
    h = add(h, t.customSurface);
    h = add(h, t.customRaised);
    h = add(h, t.customText);
    h = add(h, t.background);
    h = add(h, t.surface);
    h = add(h, t.raised);
    h = add(h, t.border);
    h = add(h, t.text);
    h = add(h, t.muted);
    h = add(h, t.accent);
    h = add(h, t.secondaryAccent);
    h = add(h, t.radius);
    h = add(h, t.borderWidth);
    h = add(h, t.backgroundOpacity);
    h = add(h, t.tintOpacity);
    h = add(h, t.surfaceOpacity);
    h = add(h, t.raisedOpacity);
    h = add(h, t.blurRadius);
    h = add(h, t.blurQuality);
    h = add(h, t.blurMix);
    h = add(h, t.glassMix);
    h = add(h, t.accentGradient);
    h = add(h, t.fontTracking);
    h = add(h, t.fontWeight);
    h = add(h, t.fontFamily);
    dev.nightclient.config.MotionSettings m = t.motion;
    if (m != null) {
      h = add(h, m.enterScale);
      h = add(h, m.duration);
      h = add(h, m.enterDuration);
      h = add(h, m.exitDuration);
      h = add(h, m.easing);
      h = add(h, m.hoverScale);
      h = add(h, m.pressScale);
      h = add(h, m.enterOpacity);
      h = add(h, m.enterOffsetX);
      h = add(h, m.enterOffsetY);
      h = add(h, m.layout ? 1 : 0);
      h = add(h, m.value ? 1 : 0);
      h = add(h, m.stagger);
    }
    ThemeEffects e = t.effects;
    if (e != null) {
      h = add(h, e.backgroundBlur ? 1 : 0);
      h = add(h, e.grain ? 1 : 0);
      h = add(h, e.grainAmount);
      h = add(h, e.grainScale);
    }
    return h;
  }
}
