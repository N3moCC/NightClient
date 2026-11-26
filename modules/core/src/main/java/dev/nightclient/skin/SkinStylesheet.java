package dev.nightclient.skin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SkinStylesheet {
  static final Set<String> PROPERTIES = new LinkedHashSet<String>(Arrays.asList("theme-name",
      "background", "background-color", "surface", "raised", "color", "text", "muted", "accent",
      "secondary-accent", "border-color", "border-width", "border-radius", "radius",
      "radius-top-left", "radius-top-right", "radius-bottom-left", "radius-bottom-right",
      "fill-top-left", "fill-top-right", "fill-bottom-left", "fill-bottom-right", "opacity",
      "content-opacity", "background-opacity", "tint-opacity", "surface-opacity", "raised-opacity",
      "shadow", "cast-effects", "blur", "blur-radius", "blur-quality", "blur-mix", "glass-mix",
      "accent-gradient", "font-size", "font-weight", "font-family", "font-tracking",
      "letter-spacing", "text-align", "pixel-origin-x", "text-overflow", "text-motion",
      "line-height", "fade-width", "scroll-speed", "scroll-pause", "line-start-x", "line-start-y",
      "line-end-x", "line-end-y", "hud-scale", "viewport-min-width", "viewport-min-height",
      "scrollbar-width", "scroll-step", "scroll-duration", "layout", "layout-reflow", "columns",
      "min-cell-width", "masonry-stable", "intrinsic-height", "retain-exit", "display", "direction",
      "flex-direction", "padding", "gap", "width", "height", "min-width", "min-height", "max-width",
      "max-height", "x", "y", "left", "top", "right", "bottom", "anchor-x", "anchor-y",
      "anchor-side", "anchor-align", "anchor-gap", "z-index", "align", "align-items", "justify",
      "justify-content", "position", "flex", "grow", "font-scale", "visible", "overflow", "grain",
      "grain-amount", "grain-scale", "line-width", "stroke", "stroke-width", "fill", "size",
      "transition-duration", "easing", "enter-scale", "hover-scale", "press-scale", "enter-opacity",
      "enter-offset-x", "enter-offset-y", "enter-duration", "exit-duration", "animate-layout",
      "animate-value", "animate-enter", "animation-stagger"));
  private static final Pattern PART = Pattern.compile("([.#:]?)([A-Za-z_][A-Za-z0-9_-]*)");
  final List<Rule> rules;
  private final Rule[] universalRules;
  private final Rule[] rootRules;
  private final Map<String, Rule[]> byId;
  private final Map<String, Rule[]> byTag;
  private final Map<String, Rule[]> byClass;

  SkinStylesheet(List<Rule> nativeRules) {
    rules = Collections.unmodifiableList(new ArrayList<Rule>(nativeRules));
    RuleIndex index = new RuleIndex(rules);
    universalRules = index.universal;
    rootRules = index.root;
    byId = index.byId;
    byTag = index.byTag;
    byClass = index.byClass;
  }

  static SkinStylesheet layer(SkinStylesheet base, SkinStylesheet override) {
    return layer(base, override, false, false);
  }

  static SkinStylesheet layer(
      SkinStylesheet base, SkinStylesheet override, boolean replacesMenu, boolean replacesHud) {
    List<Rule> result = new ArrayList<Rule>();
    for (Rule rule : base.rules) {
      if (replacesMenu && ("menu".equals(rule.tag) || "menu".equals(rule.id))
          || replacesHud && ("hud".equals(rule.tag) || "hud".equals(rule.id)))
        continue;
      result.add(new Rule(rule, result.size()));
    }
    for (Rule rule : override.rules) result.add(new Rule(rule, result.size()));
    return new SkinStylesheet(result);
  }

  static Rule nativeRule(String kind, String name, int state, Map<String, String> values, int order)
      throws IOException {
    return new Rule(kind, name, state, values, order);
  }

  SkinStylesheet(String css) throws IOException {
    List<Rule> result = new ArrayList<Rule>();
    css = stripComments(css);
    int at = 0, order = 0;
    while (at < css.length()) {
      while (at < css.length() && Character.isWhitespace(css.charAt(at))) at++;
      if (at == css.length()) break;
      int open = css.indexOf('{', at), close = css.indexOf('}', open + 1);
      if (open < 0 || close < 0) throw new IOException("Unclosed CSS rule");
      String declaration = css.substring(open + 1, close);
      if (declaration.indexOf('{') >= 0) throw new IOException("Nested CSS rules are unsupported");
      Map<String, String> values = declarations(declaration);
      for (String selector : css.substring(at, open).split(",")) {
        if (result.size() >= 512) throw new IOException("Too many CSS selectors");
        result.add(new Rule(selector.trim(), values, order++));
      }
      at = close + 1;
    }
    rules = Collections.unmodifiableList(result);
    RuleIndex index = new RuleIndex(rules);
    universalRules = index.universal;
    rootRules = index.root;
    byId = index.byId;
    byTag = index.byTag;
    byClass = index.byClass;
  }

  boolean hasIdRule(String id) {
    return id != null && byId.containsKey(id);
  }

  boolean idRuleChangesInheritedOrVars(String id, Set<String> inheritedProps) {
    if (id == null) return false;
    Rule[] matched = byId.get(id);
    if (matched == null) return false;
    for (Rule rule : matched) {
      for (String key : rule.values.keySet()) {
        if (key.startsWith("--") || inheritedProps.contains(key)) return true;
      }
    }
    return false;
  }

  private static final class RuleIndex {
    final Rule[] universal, root;
    final Map<String, Rule[]> byId, byTag, byClass;

    RuleIndex(List<Rule> rules) {
      List<Rule> univ = new ArrayList<Rule>(), rt = new ArrayList<Rule>();
      Map<String, List<Rule>> ids = new LinkedHashMap<String, List<Rule>>();
      Map<String, List<Rule>> tags = new LinkedHashMap<String, List<Rule>>();
      Map<String, List<Rule>> classes = new LinkedHashMap<String, List<Rule>>();
      for (Rule rule : rules) {
        if (rule.id != null) {
          ids.computeIfAbsent(rule.id, k -> new ArrayList<Rule>()).add(rule);
        } else if (!rule.classes.isEmpty()) {
          classes.computeIfAbsent(rule.classes.get(0), k -> new ArrayList<Rule>()).add(rule);
        } else if (rule.tag != null) {
          tags.computeIfAbsent(rule.tag, k -> new ArrayList<Rule>()).add(rule);
        } else if (rule.root) {
          rt.add(rule);
        } else {
          univ.add(rule);
        }
      }
      universal = univ.toArray(new Rule[univ.size()]);
      root = rt.toArray(new Rule[rt.size()]);
      byId = freeze(ids);
      byTag = freeze(tags);
      byClass = freeze(classes);
    }

    private static Map<String, Rule[]> freeze(Map<String, List<Rule>> source) {
      if (source.isEmpty()) return Collections.emptyMap();
      Map<String, Rule[]> out = new java.util.HashMap<String, Rule[]>(source.size() * 2);
      for (Map.Entry<String, List<Rule>> e : source.entrySet()) {
        List<Rule> list = e.getValue();
        out.put(e.getKey(), list.toArray(new Rule[list.size()]));
      }
      return out;
    }
  }

  private static String stripComments(String css) throws IOException {
    StringBuilder out = new StringBuilder();
    int at = 0;
    while (at < css.length()) {
      int open = css.indexOf("/*", at);
      if (open < 0) {
        out.append(css.substring(at));
        break;
      }
      out.append(css.substring(at, open));
      int close = css.indexOf("*/", open + 2);
      if (close < 0) throw new IOException("Unclosed CSS comment");
      at = close + 2;
    }
    return out.toString();
  }

  static Map<String, String> declarations(String css) throws IOException {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (String declaration : css.split(";")) {
      declaration = declaration.trim();
      if (declaration.isEmpty()) continue;
      int colon = declaration.indexOf(':');
      if (colon <= 0) throw new IOException("Invalid CSS declaration: " + declaration);
      String key = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT),
             value = declaration.substring(colon + 1).trim();
      if (!key.startsWith("--") && !PROPERTIES.contains(key))
        throw new IOException("Unsupported CSS property: " + key);
      if (value.isEmpty()) throw new IOException("Empty CSS property: " + key);
      String lower = value.toLowerCase(Locale.ROOT);
      if (lower.contains("url(") || lower.contains("javascript:") || lower.contains("expression("))
        throw new IOException("External resources and scripts are unsupported");
      if (value.length() > 4096)
        throw new IOException("CSS property value exceeds 4096 characters");
      if (result.size() >= 128 && !result.containsKey(key))
        throw new IOException("Too many CSS declarations");
      result.put(key, value);
    }
    return Collections.unmodifiableMap(result);
  }

  Map<String, String> own(SkinDocument.Node node, int state) throws IOException {
    Map<String, String> out = rules(node, state);
    String inline = node.attributes.get("style");
    if (inline != null) out.putAll(declarations(inline));
    out.putAll(node.inlineStyles);

    for (int bits = 1; bits <= 3; bits++)
      for (Map.Entry<Integer, Map<String, String>> variant : node.stateStyles.entrySet())
        if (Integer.bitCount(variant.getKey()) == bits
            && (state & variant.getKey()) == variant.getKey())
          out.putAll(variant.getValue());
    return out;
  }

  Map<String, String> rules(SkinDocument.Node node, int state) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    Map<String, Long> priority = new LinkedHashMap<String, Long>();
    applyBucket(universalRules, node, state, out, priority);
    if ("nightclient".equals(node.tag)) applyBucket(rootRules, node, state, out, priority);
    if (node.tag != null) applyBucket(byTag.get(node.tag), node, state, out, priority);
    if (node.id != null) applyBucket(byId.get(node.id), node, state, out, priority);
    if (!node.classes.isEmpty()) {
      for (String cls : node.classes) applyBucket(byClass.get(cls), node, state, out, priority);
    }
    return out;
  }

  private static void applyBucket(Rule[] bucket, SkinDocument.Node node, int state,
      Map<String, String> out, Map<String, Long> priority) {
    if (bucket == null || bucket.length == 0) return;
    for (int i = 0; i < bucket.length; i++) {
      Rule rule = bucket[i];
      if (rule.matches(node, state)) {
        long score = (((long) rule.specificity) << 20) | (rule.order & 0xfffffL);
        for (Map.Entry<String, String> entry : rule.values.entrySet()) {
          Long previous = priority.get(entry.getKey());
          if (previous == null || score >= previous.longValue()) {
            out.put(entry.getKey(), entry.getValue());
            priority.put(entry.getKey(), score);
          }
        }
      }
    }
  }

  static final class Rule {
    final String tag, id;
    final List<String> classes;
    final int state, specificity, order;
    final Map<String, String> values;
    final boolean root;

    Rule(Rule rule, int order) {
      tag = rule.tag;
      id = rule.id;
      classes = rule.classes;
      state = rule.state;
      specificity = rule.specificity;
      values = rule.values;
      root = rule.root;
      this.order = order;
    }

    Rule(String kind, String name, int mask, Map<String, String> declarations, int order)
        throws IOException {
      if (mask < 0 || mask > 7) throw new IOException("Unsupported interaction state");
      this.order = order;
      state = mask;
      values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(declarations));
      root = "theme".equals(kind);
      tag = "element".equals(kind) ? name : null;
      id = "id".equals(kind) ? name : null;
      classes = "class".equals(kind) ? Collections.singletonList(name)
                                       : Collections.<String>emptyList();
      if (!root && tag == null && id == null && classes.isEmpty())
        throw new IOException("Unsupported style target: " + kind);
      specificity = (root ? 10
                            : tag != null ? 1
                            : id != null  ? 100
                                          : 10)
          + Integer.bitCount(mask) * 10;
    }

    Rule(String selector, Map<String, String> values, int order) throws IOException {
      this.order = order;
      this.values = values;
      String type = null, identifier = null;
      List<String> groups = new ArrayList<String>();
      int mask = 0, score = 0;
      boolean isRoot = false;
      if ("*".equals(selector)) {
        tag = null;
        id = null;
        classes = Collections.emptyList();
        state = 0;
        specificity = 0;
        root = false;
        return;
      }

      if (selector.startsWith("#")) {
        String[] parts = selector.substring(1).split(":");
        if (parts[0].matches("[A-Za-z_][A-Za-z0-9_.-]*") && parts[0].indexOf('.') >= 0) {
          identifier = parts[0];
          score = 100;
          for (int i = 1; i < parts.length; i++) {
            if ("hover".equals(parts[i]))
              mask |= 1;
            else if ("focus".equals(parts[i]))
              mask |= 2;
            else if ("active".equals(parts[i]))
              mask |= 4;
            else
              throw new IOException("Unsupported selector state: " + parts[i]);
            score += 10;
          }
          tag = null;
          id = identifier;
          classes = Collections.emptyList();
          state = mask;
          specificity = score;
          root = false;
          return;
        }
      }
      Matcher matcher = PART.matcher(selector);
      int at = 0;
      while (matcher.find()) {
        if (matcher.start() != at) throw new IOException("Unsupported selector: " + selector);
        at = matcher.end();
        String prefix = matcher.group(1), name = matcher.group(2);
        if (prefix.isEmpty()) {
          if (type != null) throw new IOException("Invalid selector: " + selector);
          type = name.toLowerCase(Locale.ROOT);
          score++;
        } else if ("#".equals(prefix)) {
          if (identifier != null) throw new IOException("Invalid selector: " + selector);
          identifier = name;
          score += 100;
        } else if (".".equals(prefix)) {
          groups.add(name);
          score += 10;
        } else {
          if ("hover".equals(name))
            mask |= 1;
          else if ("focus".equals(name))
            mask |= 2;
          else if ("active".equals(name))
            mask |= 4;
          else if ("root".equals(name))
            isRoot = true;
          else
            throw new IOException("Unsupported selector state: " + name);
          score += 10;
        }
      }
      if (at != selector.length() || at == 0)
        throw new IOException("Unsupported selector: " + selector);
      tag = type;
      id = identifier;
      classes = Collections.unmodifiableList(groups);
      state = mask;
      specificity = score;
      root = isRoot;
    }

    boolean matches(SkinDocument.Node node, int current) {
      if ((current & state) != state) return false;
      if (tag != null && !tag.equals(node.tag)) return false;
      if (id != null && !id.equals(node.id)) return false;
      if (root && !"nightclient".equals(node.tag)) return false;
      int needed = classes.size();
      if (needed == 0) return true;
      if (node.classes.size() < needed) return false;
      for (int i = 0; i < needed; i++)
        if (!node.classes.contains(classes.get(i))) return false;
      return true;
    }
  }
}
