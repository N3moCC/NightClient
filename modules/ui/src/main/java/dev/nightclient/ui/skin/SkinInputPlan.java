package dev.nightclient.ui.skin;

import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcExpression;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class SkinInputPlan {
  private static final String[] STATES = {"", "hover", "focus", "hover focus", "active",
      "hover active", "focus active", "hover focus active"};
  private static final java.util.regex
      .Pattern FORMAT = java.util.regex.Pattern.compile("format\\s*\\("),
               MEASUREMENT = java.util.regex.Pattern.compile(
                   "(?:textWidth|maxTextWidth|maxRowTextWidth)\\s*\\("),
               RETAINED_COUNT = java.util.regex.Pattern.compile(
                   "retainedCount\\s*\\(\\s*(['\"])([^'\"]*)\\1\\s*\\)");
  private static final Set<String> SEMANTIC_ROWS =
      Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
          "inventoryCells", "armorSlots", "cooldownRows", "potionRows", "tntRows", "targetRows")));
  private final String[] paths, retainedSources;
  private final boolean safe;

  private SkinInputPlan(String[] paths, String[] retainedSources, boolean safe) {
    this.paths = paths;
    this.retainedSources = retainedSources;
    this.safe = safe;
  }

  static SkinInputPlan compile(SkinDocument.Node node, SkinDocument document,
      Function<SkinDocument.Node, SkinDocument.Node> components) {
    Compiler compiler = new Compiler(document, components);
    compiler.walk(node, Collections.<String>emptySet(), 0);
    if (compiler.retentionSensitive) {
      compiler.paths.add("menu.animationSpeed");
    }
    return new SkinInputPlan(compiler.paths.toArray(new String[compiler.paths.size()]),
        compiler.retainedSources.toArray(new String[compiler.retainedSources.size()]),
        compiler.safe);
  }

  boolean safe() {
    return safe;
  }

  String[] paths() {
    return paths.clone();
  }

  Snapshot capture(SkinBindings bindings, Map<String, Object> context) {
    if (!safe) return null;
    Object[] values = new Object[paths.length];
    for (int index = 0; index < paths.length; index++) {
      Object value = bindings.get(paths[index], context);
      if (!acceptable(paths[index], value)) return null;
      values[index] = value;
    }
    int[] retained = new int[retainedSources.length];
    for (int index = 0; index < retained.length; index++)
      retained[index] = bindings.retainedCount(retainedSources[index], context);
    return new Snapshot(this, values, retained);
  }

  static final class Snapshot {
    private final SkinInputPlan plan;
    private final Object[] values;
    private final int[] retained;

    Snapshot(SkinInputPlan plan, Object[] values, int[] retained) {
      this.plan = plan;
      this.values = values;
      this.retained = retained;
    }

    boolean matches(SkinBindings bindings, Map<String, Object> context) {
      for (int index = 0; index < values.length; index++) {
        Object value = bindings.get(plan.paths[index], context), previous = values[index];
        if (!acceptable(plan.paths[index], value)) return false;
        if (value == previous) continue;
        if (value == null || previous == null || value.getClass() != previous.getClass()
            || value instanceof StableRows || !value.equals(previous))
          return false;
      }

      for (int index = 0; index < retained.length; index++)
        if (bindings.retainedCount(plan.retainedSources[index], context) != retained[index])
          return false;
      return true;
    }
  }

  private static boolean acceptable(String path, Object value) {
    return value == null || value instanceof String || value instanceof Boolean
        || value instanceof Character || value instanceof Byte || value instanceof Short
        || value instanceof Integer || value instanceof Long || value instanceof Float
        || value instanceof Double || SEMANTIC_ROWS.contains(path) && value instanceof StableRows;
  }

  static <T> List<T> stableRows(List<T> rows) {
    return rows instanceof StableRows ? rows : new StableRows<T>(rows);
  }

  private static final class StableRows<T>
      extends AbstractList<T> implements java.util.RandomAccess {
    private final List<T> rows;

    StableRows(List<T> rows) {
      this.rows = rows;
    }

    @Override
    public T get(int index) {
      return rows.get(index);
    }

    @Override
    public int size() {
      return rows.size();
    }
  }

  private static final class Compiler {
    final SkinDocument document;
    final Function<SkinDocument.Node, SkinDocument.Node> components;
    final LinkedHashSet<String> paths = new LinkedHashSet<String>(),
                                retainedSources = new LinkedHashSet<String>();
    final Set<SkinDocument.Node> visiting =
        Collections.newSetFromMap(new IdentityHashMap<SkinDocument.Node, Boolean>());
    final Set<SkinDocument.Node> documentNodes =
        Collections.newSetFromMap(new IdentityHashMap<SkinDocument.Node, Boolean>());
    boolean safe = true, retentionSensitive;
    int nodes;

    Compiler(SkinDocument document, Function<SkinDocument.Node, SkinDocument.Node> components) {
      this.document = document;
      this.components = components;
      index(document.root());
    }

    void index(SkinDocument.Node node) {
      documentNodes.add(node);
      for (SkinDocument.Node child : node.children) index(child);
    }

    void walk(SkinDocument.Node node, Set<String> aliases, int depth) {
      if (!safe) return;
      if (depth > 64 || ++nodes > 8192 || !documentNodes.contains(node) || !visiting.add(node)) {
        safe = false;
        return;
      }
      try {
        if (node.attributes.containsKey("scope") || node.attributes.containsKey("bind")
            || node.attributes.containsKey("action") || interactive(node.tag)) {
          safe = false;
          return;
        }
        String id = node.attributes.get("id"), visual = node.attributes.get("visual");
        if (id != null && id.contains("{{") || visual != null && visual.contains("{{")) {
          safe = false;
          return;
        }
        for (String state : STATES) {
          Map<String, String> styles = document.styles(node, state);
          if ("overlay".equals(styles.get("position"))) {
            safe = false;
            return;
          }
          if ("true".equals(styles.get("retain-exit"))) retentionSensitive = true;
          for (Map.Entry<String, String> entry : styles.entrySet())
            if (!entry.getKey().startsWith("--"))
              template(entry.getValue(), styles, aliases, new HashSet<String>());
          for (String value : document.themeBindings(node, state).values())
            template(value, styles, aliases, new HashSet<String>());
        }
        Map<String, String> styles = document.styles(node, "");
        template(node.text, styles, aliases, new HashSet<String>());
        Set<String> childAliases = aliases;
        if ("repeat".equals(node.tag)) {
          childAliases = new HashSet<String>(aliases);
          childAliases.add(node.attribute("as", "item"));
          childAliases.add("index");
        }
        for (Map.Entry<String, String> attribute : node.attributes.entrySet()) {
          String name = attribute.getKey(), value = attribute.getValue();
          if ("if".equals(name))
            condition(value, styles, aliases);
          else if ("source".equals(name)) {
            if (value.contains("{{")
                || "repeat".equals(node.tag) && !SEMANTIC_ROWS.contains(value)) {
              safe = false;
              return;
            }
            if (!"game".equals(value) && !"native".equals(value))
              path(value, styles, aliases, new HashSet<String>());
          } else
            template(value, styles,
                "key".equals(name) && "repeat".equals(node.tag) ? childAliases : aliases,
                new HashSet<String>());
        }
        SkinDocument.Node component = components == null ? null : components.apply(node);
        if (component != null && !"component".equals(node.tag)
            && !"none".equals(node.attribute("visual", ""))) {
          if (visiting.contains(component)) {
            safe = false;
            return;
          }
          walk(component, aliases, depth + 1);
        }
        for (SkinDocument.Node child : node.children) walk(child, childAliases, depth + 1);
      } finally {
        visiting.remove(node);
      }
    }

    private static boolean interactive(String tag) {
      return "crosshair".equals(tag) || "slider".equals(tag) || "toggle".equals(tag)
          || "switch".equals(tag) || "choice".equals(tag) || "input".equals(tag)
          || "button".equals(tag) || "color".equals(tag) || "color-plane".equals(tag)
          || "hue-strip".equals(tag) || "alpha-strip".equals(tag) || "viewmodel".equals(tag);
    }

    void condition(String source, Map<String, String> styles, Set<String> aliases) {
      if (source == null || source.trim().isEmpty()) return;
      source = source.trim();
      if (source.contains("{{")) {
        template(source, styles, aliases, new HashSet<String>());
        return;
      }
      if (source.startsWith("=")) {
        expression(source.substring(1), styles, aliases, new HashSet<String>());
        return;
      }
      if (source.startsWith("!")) source = source.substring(1).trim();
      int equal = source.indexOf('=');
      path(equal < 0 ? source : source.substring(0, equal).trim(), styles, aliases,
          new HashSet<String>());
    }

    void template(
        String source, Map<String, String> styles, Set<String> aliases, Set<String> variables) {
      if (!safe || source == null) return;
      int at = 0, count = 0;
      while ((at = source.indexOf("{{", at)) >= 0) {
        int end = source.indexOf("}}", at + 2);
        if (end < 0 || ++count > 32) return;
        String body = source.substring(at + 2, end).trim();
        int separator = SkinBindings.formatSeparator(body);
        if (separator >= 0) {
          body = body.substring(0, separator).trim();
          paths.add("language");
        }
        if (body.startsWith("="))
          expression(body.substring(1), styles, aliases, variables);
        else
          path(body, styles, aliases, variables);
        at = end + 2;
      }
    }

    void expression(
        String source, Map<String, String> styles, Set<String> aliases, Set<String> variables) {
      if (!safe) return;
      try {
        NcExpression.Expression expression = NcExpression.compile(source);

        if (!expression.fixedPresentationSources()) {
          safe = false;
          return;
        }

        if (FORMAT.matcher(source).find() || MEASUREMENT.matcher(source).find())
          paths.add("language");
        int calls = count(source, "retainedCount");
        if (calls > 0) {
          java.util.regex.Matcher retained = RETAINED_COUNT.matcher(source);
          int literal = 0;
          while (retained.find()) {
            literal++;
            retainedSources.add(retained.group(2));
          }
          if (literal != calls) {
            safe = false;
            return;
          }
          retentionSensitive = true;
        }
        for (String dependency : expression.contextPaths()) {
          if (!expression.identifiers().contains(dependency)
              && !SEMANTIC_ROWS.contains(dependency)) {
            safe = false;
            return;
          }
          path(dependency, styles, aliases, variables);
        }
      } catch (IllegalArgumentException invalid) {
        safe = false;
      }
    }

    private static int count(String source, String token) {
      int count = 0, at = 0;
      while ((at = source.indexOf(token, at)) >= 0) {
        count++;
        at += token.length();
      }
      return count;
    }

    void path(
        String source, Map<String, String> styles, Set<String> aliases, Set<String> variables) {
      if (!safe) return;
      String path = source.trim();
      if (path.startsWith("settings.")) path = path.substring(9);
      int dot = path.indexOf('.');
      String head = dot < 0 ? path : path.substring(0, dot);
      if (aliases.contains(head)) return;
      if ("vars".equals(head)) {
        if (dot < 0 || variables.size() >= 24 || !variables.add(path)) {
          safe = false;
          return;
        }
        try {
          String raw = null;
          for (Map.Entry<String, String> entry : styles.entrySet())
            if (SkinDocument.matchesVariablePath(entry.getKey(), path)) {
              raw = SkinDocument.expressionVariables(entry.getValue(), styles);
              break;
            }
          if (raw == null) {
            safe = false;
            return;
          }
          if (raw.startsWith("#")) return;
          if (raw.startsWith("{{"))
            template(raw, styles, aliases, variables);
          else
            expression(raw, styles, aliases, variables);
        } catch (java.io.IOException invalid) {
          safe = false;
        } finally {
          variables.remove(path);
        }
        return;
      }
      if ("boxWidth".equals(head) || "boxHeight".equals(head) || "time".equals(head)
          || "ui".equals(head) || "local".equals(head) || head.startsWith("__")
          || containsIgnoreCase(head, "pointer") || containsIgnoreCase(head, "mouse")) {
        safe = false;
        return;
      }
      if (!isValidPath(path)) {
        safe = false;
        return;
      }
      if ("tntRows".equals(path)) paths.add("tnt.range");
      if (paths.size() >= 256) {
        safe = false;
        return;
      }
      paths.add(path);
    }

    private static boolean containsIgnoreCase(String s, String needle) {
      int n = needle.length(), lim = s.length() - n;
      for (int i = 0; i <= lim; i++)
        if (s.regionMatches(true, i, needle, 0, n)) return true;
      return false;
    }

    private static boolean isValidPath(String s) {
      int len = s.length();
      if (len == 0) return false;
      int segments = 0;
      int i = 0;
      while (true) {
        if (i >= len) return false;
        char c = s.charAt(i++);
        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_')) return false;
        while (i < len) {
          c = s.charAt(i);
          if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
              || c == '_') {
            i++;
          } else {
            break;
          }
        }
        if (i == len) return true;
        if (s.charAt(i) != '.' || ++segments > 5) return false;
        i++;
      }
    }
  }
}
