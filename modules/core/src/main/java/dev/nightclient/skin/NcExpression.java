package dev.nightclient.skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class NcExpression {
  public static final int MAX_SOURCE_LENGTH = 1024;
  public static final int MAX_NODES = 128;
  public static final int MAX_DEPTH = 24;
  public static final int MAX_CACHE_ENTRIES = 4096;
  private static final int MAX_STRING_LENGTH = 4096;
  private static final Map<String, Expression> CACHE =
      new LinkedHashMap<String, Expression>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Expression> entry) {
          return size() > MAX_CACHE_ENTRIES;
        }
      };

  private NcExpression() {}

  public interface Resolver extends Function<String, Object> {
    Object call(String name, List<Object> arguments);
  }

  public static Expression compile(String source) {
    if (source == null) throw new IllegalArgumentException("NC expression is null");
    if (source.length() > MAX_SOURCE_LENGTH)
      throw new IllegalArgumentException("NC expression exceeds 1024 characters");
    synchronized (CACHE) {
      Expression found = CACHE.get(source);
      if (found != null) return found;
    }
    Parser parser = new Parser(source);
    Node root = parser.conditional();
    parser.space();
    if (parser.offset != source.length()) throw parser.error("Unexpected token");
    Expression result = new Expression(root, parser.identifiers, parser.presentationSources,
        parser.fixedPresentationSources, parser.scalarMemoizable, parser.hasPresentationCall);
    synchronized (CACHE) {
      Expression found = CACHE.get(source);
      if (found != null) return found;
      CACHE.put(source, result);
    }
    return result;
  }

  public static final class Expression {
    private final Node root;
    private final Set<String> identifiers, contextPaths;
    private final boolean fixedPresentationSources, scalarMemoizable, hasPresentationCall;
    private final String[] scalarPaths, allPaths;
    private final Map<String, Integer> scalarIndexes, allIndexes;

    private Expression(Node root, Set<String> identifiers, Set<String> presentationSources,
        boolean fixedPresentationSources, boolean scalarMemoizable, boolean hasPresentationCall) {
      this.root = root;
      this.identifiers = Collections.unmodifiableSet(new LinkedHashSet<String>(identifiers));
      Set<String> paths = new LinkedHashSet<String>(identifiers);
      paths.addAll(presentationSources);
      this.contextPaths = Collections.unmodifiableSet(paths);
      this.fixedPresentationSources = fixedPresentationSources;
      this.scalarMemoizable = scalarMemoizable;
      this.hasPresentationCall = hasPresentationCall;
      scalarPaths = scalarMemoizable ? identifiers.toArray(new String[identifiers.size()]) : null;
      if (scalarMemoizable) {
        Map<String, Integer> indexes = new java.util.HashMap<String, Integer>();
        for (int index = 0; index < scalarPaths.length; index++)
          indexes.put(scalarPaths[index], index);
        scalarIndexes = Collections.unmodifiableMap(indexes);
        allPaths = scalarPaths;
        allIndexes = scalarIndexes;
      } else {
        scalarIndexes = null;
        if (!hasPresentationCall) {
          allPaths = identifiers.toArray(new String[identifiers.size()]);
          Map<String, Integer> indexes = new java.util.HashMap<String, Integer>();
          for (int index = 0; index < allPaths.length; index++) indexes.put(allPaths[index], index);
          allIndexes = Collections.unmodifiableMap(indexes);
        } else {
          allPaths = null;
          allIndexes = null;
        }
      }
    }

    public boolean traceMemoizable() {
      return !scalarMemoizable && !hasPresentationCall;
    }

    public int traceInputCount() {
      return allPaths == null ? 0 : allPaths.length;
    }

    public String traceInputPath(int index) {
      if (allPaths == null)
        throw new IllegalArgumentException("Expression is not trace-memoizable");
      return allPaths[index];
    }

    public TraceMemo newTraceMemo() {
      if (!traceMemoizable())
        throw new IllegalArgumentException("Only pure branching expressions can use trace memo");
      return new TraceMemo(this);
    }

    public Object evaluate(Function<String, Object> resolver) {
      if (resolver == null)
        throw new IllegalArgumentException("NC expression requires a resolver");
      return scalar(root.evaluate(resolver));
    }

    public Set<String> identifiers() {
      return identifiers;
    }

    public Set<String> contextPaths() {
      return contextPaths;
    }

    public boolean fixedPresentationSources() {
      return fixedPresentationSources;
    }

    public boolean scalarMemoizable() {
      return scalarMemoizable;
    }

    public int scalarInputCount() {
      return scalarPaths == null ? 0 : scalarPaths.length;
    }

    public String scalarInputPath(int index) {
      if (scalarPaths == null)
        throw new IllegalArgumentException("Expression is not scalar-memoizable");
      return scalarPaths[index];
    }

    public ScalarMemo newScalarMemo() {
      if (!scalarMemoizable)
        throw new IllegalArgumentException("Only eager pure scalar expressions can be memoized");
      return new ScalarMemo(this);
    }
  }

  public static final class ScalarMemo {
    private static final int CAPACITY = 32, BUCKETS = 64;
    private final Expression expression;
    private final Object[] inputs;
    private final Function<String, Object> capturedInputs;
    private java.util.function.IntFunction<Object> activeIndexedInputs;
    private final Function<String, Object> indexedFallback;
    private Entry last, resultEntry;
    private Entry[] buckets, ring;
    private int size, nextEviction;

    private static final class Entry {
      final Object[] inputs;
      Object value;
      String text;
      int hash;
      Entry next;

      Entry(Object[] inputs, Object value, int hash) {
        this.inputs = inputs.clone();
        this.value = value;
        this.hash = hash;
      }
    }

    private ScalarMemo(final Expression expression) {
      this.expression = expression;
      inputs = new Object[expression.scalarPaths.length];
      capturedInputs = new Function<String, Object>() {
        public Object apply(String path) {
          Integer index = expression.scalarIndexes.get(path);
          return index == null ? null : inputs[index];
        }
      };
      indexedFallback = new Function<String, Object>() {
        public Object apply(String path) {
          Integer index = expression.scalarIndexes.get(path);
          return index == null ? null : activeIndexedInputs.apply(index);
        }
      };
    }

    public Object evaluate(Function<String, Object> resolver) {
      if (resolver == null)
        throw new IllegalArgumentException("NC expression requires a resolver");
      return evaluate(resolver, null);
    }

    public Object evaluateIndexed(java.util.function.IntFunction<Object> resolver) {
      if (resolver == null)
        throw new IllegalArgumentException("NC expression requires an indexed resolver");
      java.util.function.IntFunction<Object> previous = activeIndexedInputs;
      activeIndexedInputs = resolver;
      try {
        return evaluate(indexedFallback, resolver);
      } finally {
        activeIndexedInputs = previous;
      }
    }

    private Object evaluate(
        Function<String, Object> resolver, java.util.function.IntFunction<Object> indexed) {
      boolean same = last != null;
      for (int index = 0; index < inputs.length; index++) {
        Object next =
            indexed == null ? resolver.apply(expression.scalarPaths[index]) : indexed.apply(index);
        if (!memoScalar(next)) {
          resultEntry = null;
          return expression.evaluate(resolver);
        }
        if (same && !sameScalar(last.inputs[index], next)) same = false;
        inputs[index] = next;
      }
      if (same) {
        resultEntry = last;
        return last.value;
      }
      int hash = 1;
      for (Object input : inputs)
        hash =
            31 * hash + (input == null ? 0 : 31 * input.getClass().hashCode() + input.hashCode());
      if (buckets != null) {
        for (Entry entry = buckets[hash & (BUCKETS - 1)]; entry != null; entry = entry.next)
          if (entry.hash == hash && sameInputs(entry.inputs)) {
            last = resultEntry = entry;
            return entry.value;
          }
      }
      Object next = expression.evaluate(capturedInputs);
      Entry entry;
      if (size == 0) {
        entry = new Entry(inputs, next, hash);
        size = 1;
      } else {
        if (buckets == null) {
          buckets = new Entry[BUCKETS];
          ring = new Entry[CAPACITY];
          ring[0] = last;
          buckets[last.hash & (BUCKETS - 1)] = last;
        }
        if (size < CAPACITY) {
          entry = new Entry(inputs, next, hash);
          ring[size++] = entry;
        } else {
          entry = ring[nextEviction];
          int bucket = entry.hash & (BUCKETS - 1);
          Entry previous = null, current = buckets[bucket];
          while (current != entry) {
            previous = current;
            current = current.next;
          }
          if (previous == null)
            buckets[bucket] = entry.next;
          else
            previous.next = entry.next;
          System.arraycopy(inputs, 0, entry.inputs, 0, inputs.length);
          entry.value = next;
          entry.text = null;
          entry.hash = hash;
          nextEviction = (nextEviction + 1) & (CAPACITY - 1);
        }
        int bucket = hash & (BUCKETS - 1);
        entry.next = buckets[bucket];
        buckets[bucket] = entry;
      }
      last = resultEntry = entry;
      return entry.value;
    }

    private boolean sameInputs(Object[] other) {
      for (int index = 0; index < inputs.length; index++)
        if (!sameScalar(inputs[index], other[index])) return false;
      return true;
    }

    public String text(Function<String, Object> resolver) {
      return resultText(evaluate(resolver));
    }

    public String textIndexed(java.util.function.IntFunction<Object> resolver) {
      return resultText(evaluateIndexed(resolver));
    }

    private String resultText(Object result) {
      Entry entry = resultEntry;
      if (entry == null) return result == null ? "" : String.valueOf(result);
      if (entry.text == null) entry.text = result == null ? "" : String.valueOf(result);
      return entry.text;
    }

    private static boolean memoScalar(Object value) {
      return value == null || value instanceof String || value instanceof Boolean
          || value instanceof Character || value instanceof Double || value instanceof Float
          || value instanceof Long || value instanceof Integer || value instanceof Short
          || value instanceof Byte;
    }

    private static boolean sameScalar(Object first, Object second) {
      return first == second
          || first != null && second != null && first.getClass() == second.getClass()
          && first.equals(second);
    }
  }

  public static final class TraceMemo {
    private static final int CAPACITY = 16;
    private final Expression expression;
    private final Object[] readValues;
    private final int[] readGen;
    private int gen, reusableGen;
    private final int[] traceIdx;
    private final Object[] traceVals;
    private int traceLen;
    private boolean traceCacheable;
    private java.util.function.IntFunction<Object> activeResolver;
    private final Function<String, Object> recordingResolver;
    private final Entry[] entries = new Entry[CAPACITY];
    private int size, nextEvict;
    private Entry last, resultEntry;

    private static final class Entry {
      int[] indices;
      Object[] values;
      int length;
      Object value;
      String text;
    }

    private TraceMemo(final Expression expression) {
      this.expression = expression;
      int n = expression.allPaths.length;
      readValues = new Object[n];
      readGen = new int[n];
      traceIdx = new int[n];
      traceVals = new Object[n];
      recordingResolver = new Function<String, Object>() {
        public Object apply(String path) {
          Integer slot = expression.allIndexes.get(path);
          if (slot == null) return null;
          int idx = slot.intValue();
          if (readGen[idx] == gen) return readValues[idx];
          Object val = (reusableGen != 0 && readGen[idx] == reusableGen)
              ? readValues[idx]
              : activeResolver.apply(idx);
          readGen[idx] = gen;
          readValues[idx] = val;
          if (traceCacheable) {
            if (!ScalarMemo.memoScalar(val) || traceLen >= traceIdx.length) {
              traceCacheable = false;
            } else {
              traceIdx[traceLen] = idx;
              traceVals[traceLen] = val;
              traceLen++;
            }
          }
          return val;
        }
      };
    }

    public Object evaluateIndexed(java.util.function.IntFunction<Object> resolver) {
      if (resolver == null)
        throw new IllegalArgumentException("NC expression requires an indexed resolver");
      java.util.function.IntFunction<Object> previous = activeResolver;
      activeResolver = resolver;
      try {
        if (++gen == 0) {
          java.util.Arrays.fill(readGen, 0);
          gen = 1;
        }
        Entry hit = last;
        if (hit != null && matches(hit, resolver)) {
          resultEntry = hit;
          return hit.value;
        }
        for (int i = 0; i < size; i++) {
          Entry candidate = entries[i];
          if (candidate != hit && matches(candidate, resolver)) {
            last = resultEntry = candidate;
            return candidate.value;
          }
        }
        traceLen = 0;
        traceCacheable = true;
        int prevGen = gen;
        if (++gen == 0) {
          java.util.Arrays.fill(readGen, 0);
          gen = 1;
          prevGen = 0;
        }
        reusableGen = prevGen;
        Object value;
        try {
          value = expression.evaluate(recordingResolver);
        } finally {
          reusableGen = 0;
        }
        if (!traceCacheable) {
          resultEntry = null;
          return value;
        }
        Entry slot;
        if (size < CAPACITY) {
          slot = new Entry();
          entries[size++] = slot;
        } else {
          slot = entries[nextEvict];
          nextEvict = (nextEvict + 1) & (CAPACITY - 1);
        }
        if (slot.indices == null || slot.indices.length < traceLen) {
          slot.indices = new int[traceLen];
          slot.values = new Object[traceLen];
        }
        System.arraycopy(traceIdx, 0, slot.indices, 0, traceLen);
        System.arraycopy(traceVals, 0, slot.values, 0, traceLen);
        slot.length = traceLen;
        slot.value = value;
        slot.text = null;
        last = resultEntry = slot;
        return value;
      } finally {
        activeResolver = previous;
      }
    }

    private boolean matches(Entry entry, java.util.function.IntFunction<Object> resolver) {
      int len = entry.length;
      for (int k = 0; k < len; k++) {
        int idx = entry.indices[k];
        Object actual;
        if (readGen[idx] == gen) {
          actual = readValues[idx];
        } else {
          actual = resolver.apply(idx);
          readGen[idx] = gen;
          readValues[idx] = actual;
        }
        if (!ScalarMemo.sameScalar(entry.values[k], actual)) return false;
      }
      return true;
    }

    public String textIndexed(java.util.function.IntFunction<Object> resolver) {
      Object result = evaluateIndexed(resolver);
      Entry entry = resultEntry;
      if (entry == null) return result == null ? "" : String.valueOf(result);
      if (entry.text == null) entry.text = result == null ? "" : String.valueOf(result);
      return entry.text;
    }
  }

  public static boolean truth(Object value) {
    value = scalar(value);
    if (value instanceof Boolean) return (Boolean) value;
    if (value instanceof Number) return ((Number) value).doubleValue() != 0;
    return value instanceof String && !((String) value).isEmpty();
  }

  private static Object scalar(Object value) {
    if (value == null || value instanceof Boolean) return value;
    if (value instanceof String) {
      String string = (String) value;
      return string.length() <= MAX_STRING_LENGTH ? string : string.substring(0, MAX_STRING_LENGTH);
    }
    if (value instanceof Character) return String.valueOf(((Character) value).charValue());

    if (value instanceof Double) {
      double number = (Double) value;
      return Double.isFinite(number) ? value : 0d;
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer
        || value instanceof Long || value instanceof Float) {
      return finite(((Number) value).doubleValue());
    }
    return null;
  }

  private static double finite(double value) {
    return Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
  }

  private static double number(Object value) {
    value = scalar(value);
    if (value instanceof Number) return ((Number) value).doubleValue();
    if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
    if (value instanceof String) {
      try {
        return finite(Double.parseDouble((String) value));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }

  private static String string(Object value) {
    value = scalar(value);
    if (value == null) return "";
    if (value instanceof String) return (String) value;
    if (value instanceof Boolean) return (Boolean) value ? "true" : "false";
    double numeric = number(value);
    if (numeric >= Long.MIN_VALUE && numeric < 0x1.0p63 && numeric == Math.rint(numeric))
      return Long.toString((long) numeric);
    return Double.toString(numeric);
  }

  private abstract static class Node {
    final int depth;

    Node(int depth) {
      this.depth = depth;
    }

    abstract Object evaluate(Function<String, Object> resolver);
  }

  private static final class Literal extends Node {
    final Object value;

    Literal(Object value) {
      super(1);
      this.value = value;
    }

    @Override
    Object evaluate(Function<String, Object> resolver) {
      return value;
    }
  }

  private static final class Identifier extends Node {
    final String path;

    Identifier(String path) {
      super(1);
      this.path = path;
    }

    @Override
    Object evaluate(Function<String, Object> resolver) {
      return scalar(resolver.apply(path));
    }
  }

  private static final class Unary extends Node {
    final char operator;
    final Node operand;

    Unary(char operator, Node operand) {
      super(operand.depth + 1);
      this.operator = operator;
      this.operand = operand;
    }

    @Override
    Object evaluate(Function<String, Object> resolver) {
      Object value = operand.evaluate(resolver);
      if (operator == '!') return !truth(value);
      return operator == '-' ? finite(-number(value)) : number(value);
    }
  }

  private static final class Binary extends Node {
    final String operator;
    final Node left, right;

    Binary(String operator, Node left, Node right) {
      super(1 + Math.max(left.depth, right.depth));
      this.operator = operator;
      this.left = left;
      this.right = right;
    }

    @Override
    Object evaluate(Function<String, Object> resolver) {
      Object a = left.evaluate(resolver);
      if ("&&".equals(operator)) return truth(a) && truth(right.evaluate(resolver));
      if ("||".equals(operator)) return truth(a) || truth(right.evaluate(resolver));
      Object b = right.evaluate(resolver);
      if ("==".equals(operator) || "!=".equals(operator)) {
        boolean same = a instanceof Number && b instanceof Number ? number(a) == number(b)
            : a == null                                           ? b == null
                                                                  : a.equals(b);
        return "==".equals(operator) ? same : !same;
      }
      if ("+".equals(operator) && (a instanceof String || b instanceof String)) {
        String result = string(a) + string(b);
        return result.length() <= MAX_STRING_LENGTH ? result
                                                    : result.substring(0, MAX_STRING_LENGTH);
      }
      double x = number(a), y = number(b);
      if ("+".equals(operator)) return finite(x + y);
      if ("-".equals(operator)) return finite(x - y);
      if ("*".equals(operator)) return finite(x * y);
      if ("/".equals(operator)) return y == 0 ? 0d : finite(x / y);
      if ("%".equals(operator)) return y == 0 ? 0d : finite(x % y);
      int comparison = a instanceof String && b instanceof String
          ? ((String) a).compareTo((String) b)
          : Double.compare(x == 0 ? 0 : x, y == 0 ? 0 : y);
      if ("<".equals(operator)) return comparison < 0;
      if ("<=".equals(operator)) return comparison <= 0;
      if (">".equals(operator)) return comparison > 0;
      return comparison >= 0;
    }
  }

  private static final class Conditional extends Node {
    final Node condition, yes, no;

    Conditional(Node condition, Node yes, Node no) {
      super(1 + Math.max(condition.depth, Math.max(yes.depth, no.depth)));
      this.condition = condition;
      this.yes = yes;
      this.no = no;
    }

    @Override
    Object evaluate(Function<String, Object> resolver) {
      return (truth(condition.evaluate(resolver)) ? yes : no).evaluate(resolver);
    }
  }

  private static final class Call extends Node {
    final String name;
    final List<Node> arguments;

    Call(String name, List<Node> arguments, int depth) {
      super(depth);
      this.name = name;
      this.arguments = Collections.unmodifiableList(new ArrayList<Node>(arguments));
    }

    @Override
    Object evaluate(Function<String, Object> resolver) {
      if ("textWidth".equals(name) || "maxTextWidth".equals(name)
          || "maxRowTextWidth".equals(name) || "gameTextWidth".equals(name)
          || "maxRowGameTextWidth".equals(name) || "format".equals(name)
          || "retainedCount".equals(name)) {
        if (!(resolver instanceof Resolver)) return "format".equals(name) ? "" : 0d;
        List<Object> values = new ArrayList<Object>();
        for (Node argument : arguments) values.add(scalar(argument.evaluate(resolver)));
        return scalar(((Resolver) resolver).call(name, Collections.unmodifiableList(values)));
      }
      if ("mixColor".equals(name) || "lerpColor".equals(name) || "alphaColor".equals(name)
          || "setAlphaColor".equals(name)) {
        int first = color(arguments.get(0).evaluate(resolver));
        if ("setAlphaColor".equals(name))
          return (first & 0x00ffffff)
              | ((int) (255 * Math.max(0, Math.min(1, number(arguments.get(1).evaluate(resolver)))))
                  << 24);
        if ("alphaColor".equals(name))
          return dev.nightclient.api.Colors.alpha(
              first, (float) Math.max(0, Math.min(1, number(arguments.get(1).evaluate(resolver)))));
        int second = color(arguments.get(1).evaluate(resolver));
        float amount =
            (float) Math.max(0, Math.min(1, number(arguments.get(2).evaluate(resolver))));
        if ("mixColor".equals(name)) return dev.nightclient.api.Colors.mix(first, second, amount);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
          int a = (first >>> shift) & 255, b = (second >>> shift) & 255;
          result |= ((int) (a + (b - a) * amount)) << shift;
        }
        return result;
      }
      if ("len".equals(name)) {
        String value = string(arguments.get(0).evaluate(resolver));
        return (double) value.codePointCount(0, value.length());
      }
      double first = number(arguments.get(0).evaluate(resolver));
      if ("floor".equals(name)) return Math.floor(first);
      if ("ceil".equals(name)) return Math.ceil(first);
      if ("round".equals(name)) return finite(Math.floor(first + .5));
      if ("abs".equals(name)) return Math.abs(first);
      if ("clamp".equals(name)) {
        double a = number(arguments.get(1).evaluate(resolver)),
               b = number(arguments.get(2).evaluate(resolver));
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), first));
      }
      for (int i = 1; i < arguments.size(); i++) {
        double next = number(arguments.get(i).evaluate(resolver));
        first = "min".equals(name) ? Math.min(first, next) : Math.max(first, next);
      }
      return first;
    }

    private static int color(Object value) {
      if (value instanceof Number) return ((Number) value).intValue();
      try {
        return ThemePatch.color(string(value));
      } catch (java.io.IOException invalid) {
        throw new IllegalArgumentException("Invalid expression color", invalid);
      }
    }
  }

  private static final class Parser {
    final String source;
    final Set<String> identifiers = new LinkedHashSet<String>();
    final Set<String> presentationSources = new LinkedHashSet<String>();
    boolean fixedPresentationSources = true, scalarMemoizable = true, hasPresentationCall = false;
    int offset, nodes, recursion;

    Parser(String source) {
      this.source = source;
    }

    IllegalArgumentException error(String message) {
      return new IllegalArgumentException("NC expression at " + (offset + 1) + ": " + message);
    }

    void space() {
      while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
    }

    boolean take(String token) {
      space();
      if (!source.startsWith(token, offset)) return false;
      offset += token.length();
      return true;
    }

    void need(String token) {
      if (!take(token)) throw error("Expected '" + token + "'");
    }

    <T extends Node> T node(T node) {
      if (++nodes > MAX_NODES) throw error("Expression exceeds 128 nodes");
      if (node.depth > MAX_DEPTH) throw error("Expression exceeds 24 levels");
      return node;
    }

    void enter() {
      if (++recursion > MAX_DEPTH) throw error("Expression exceeds 24 levels");
    }

    Node conditional() {
      enter();
      try {
        Node value = or();
        if (take("?")) {
          scalarMemoizable = false;
          Node yes = conditional();
          need(":");
          value = node(new Conditional(value, yes, conditional()));
        }
        return value;
      } finally {
        recursion--;
      }
    }

    Node or() {
      Node value = and();
      while (take("||")) {
        scalarMemoizable = false;
        value = node(new Binary("||", value, and()));
      }
      return value;
    }

    Node and() {
      Node value = equality();
      while (take("&&")) {
        scalarMemoizable = false;
        value = node(new Binary("&&", value, equality()));
      }
      return value;
    }

    Node equality() {
      Node value = comparison();
      while (true) {
        String op = take("==") ? "==" : take("!=") ? "!=" : null;
        if (op == null) return value;
        value = node(new Binary(op, value, comparison()));
      }
    }

    Node comparison() {
      Node value = sum();
      while (true) {
        String op = take("<=") ? "<="
            : take(">=")       ? ">="
            : take("<")        ? "<"
            : take(">")        ? ">"
                               : null;
        if (op == null) return value;
        value = node(new Binary(op, value, sum()));
      }
    }

    Node sum() {
      Node value = product();
      while (true) {
        String op = take("+") ? "+" : take("-") ? "-" : null;
        if (op == null) return value;
        value = node(new Binary(op, value, product()));
      }
    }

    Node product() {
      Node value = unary();
      while (true) {
        String op = take("*") ? "*" : take("/") ? "/" : take("%") ? "%" : null;
        if (op == null) return value;
        value = node(new Binary(op, value, unary()));
      }
    }

    Node unary() {
      space();
      if (offset < source.length() && "!+-".indexOf(source.charAt(offset)) >= 0) {
        char op = source.charAt(offset++);
        enter();
        try {
          return node(new Unary(op, unary()));
        } finally {
          recursion--;
        }
      }
      return primary();
    }

    Node primary() {
      space();
      if (offset == source.length()) throw error("Expected a value");
      char ch = source.charAt(offset);
      if (ch == '(') {
        offset++;
        Node result = conditional();
        need(")");
        return result;
      }
      if (ch == '\'' || ch == '"') return node(new Literal(quoted()));
      if (digit(ch)
          || ch == '.' && offset + 1 < source.length() && digit(source.charAt(offset + 1)))
        return numeric();
      if (!start(ch)) throw error("Expected a number, string or binding path");
      String path = path();
      if ("true".equals(path)) return node(new Literal(Boolean.TRUE));
      if ("false".equals(path)) return node(new Literal(Boolean.FALSE));
      if ("null".equals(path)) return node(new Literal(null));
      if (take("(")) return call(path);
      identifiers.add(path);
      return node(new Identifier(path));
    }

    Node call(String name) {
      boolean aggregate = "min".equals(name) || "max".equals(name);
      boolean text = "textWidth".equals(name), maxText = "maxTextWidth".equals(name),
              maxRowText = "maxRowTextWidth".equals(name),
              gameText = "gameTextWidth".equals(name),
              maxRowGameText = "maxRowGameTextWidth".equals(name);
      if (text || maxText || maxRowText || gameText || maxRowGameText || "format".equals(name)
          || "retainedCount".equals(name)) {
        scalarMemoizable = false;
        hasPresentationCall = true;
      }
      int arity = text || maxText || maxRowText                                           ? -1
          : maxRowGameText                                                                ? 4
          : "clamp".equals(name) || "mixColor".equals(name) || "lerpColor".equals(name) ? 3
          : gameText || "alphaColor".equals(name) || "setAlphaColor".equals(name)
                  || "format".equals(name)
              ? 2
          : "floor".equals(name) || "ceil".equals(name) || "round".equals(name)
                  || "abs".equals(name) || "len".equals(name) || "retainedCount".equals(name)
              ? 1
              : aggregate ? -1
                          : 0;
      if (arity == 0) throw error("Unknown function '" + name + "'");
      List<Node> arguments = new ArrayList<Node>();
      int depth = 1;
      if (!take(")")) {
        do {
          if (arguments.size() == 16) throw error("Function accepts at most 16 arguments");
          Node argument = conditional();
          arguments.add(argument);
          depth = Math.max(depth, argument.depth + 1);
        } while (take(","));
        need(")");
      }
      if (text             ? arguments.size() < 2 || arguments.size() > 5
              : maxText    ? arguments.size() < 3 || arguments.size() > 6
              : maxRowText ? arguments.size() < 4 || arguments.size() > 7
              : aggregate  ? arguments.isEmpty()
                           : arguments.size() != arity)
        throw error("Incorrect argument count for '" + name + "'");
      if ("maxTextWidth".equals(name) || "maxRowTextWidth".equals(name)
          || "maxRowGameTextWidth".equals(name) || "retainedCount".equals(name)) {
        Node source = arguments.get(0);
        if (source instanceof Literal && ((Literal) source).value instanceof String)
          presentationSources.add((String) ((Literal) source).value);
        else
          fixedPresentationSources = false;
      }
      return node(new Call(name, arguments, depth));
    }

    String path() {
      int begin = offset;
      do {
        if (!start(source.charAt(offset))) throw error("Expected a binding name after '.'");
        offset++;
        while (offset < source.length() && part(source.charAt(offset))) offset++;
        if (offset >= source.length() || source.charAt(offset) != '.') break;
        offset++;
        if (offset >= source.length()) throw error("Incomplete binding path");
      } while (true);
      return source.substring(begin, offset);
    }

    Node numeric() {
      int begin = offset;
      while (offset < source.length() && digit(source.charAt(offset))) offset++;
      if (offset < source.length() && source.charAt(offset) == '.') {
        offset++;
        while (offset < source.length() && digit(source.charAt(offset))) offset++;
      }
      if (offset < source.length()
          && (source.charAt(offset) == 'e' || source.charAt(offset) == 'E')) {
        offset++;
        if (offset < source.length()
            && (source.charAt(offset) == '+' || source.charAt(offset) == '-'))
          offset++;
        int digits = offset;
        while (offset < source.length() && digit(source.charAt(offset))) offset++;
        if (digits == offset) throw error("Incomplete numeric exponent");
      }
      try {
        double value = Double.parseDouble(source.substring(begin, offset));
        if (Double.isInfinite(value) || Double.isNaN(value)) throw error("Number must be finite");
        return node(new Literal(value));
      } catch (NumberFormatException invalid) {
        throw error("Invalid number");
      }
    }

    String quoted() {
      char quote = source.charAt(offset++);
      StringBuilder result = new StringBuilder();
      while (offset < source.length()) {
        char ch = source.charAt(offset++);
        if (ch == quote) return result.toString();
        if (ch < 32) throw error("Control character in string");
        if (ch == '\\') {
          if (offset == source.length()) throw error("Incomplete string escape");
          ch = source.charAt(offset++);
          if (ch == 'n')
            ch = '\n';
          else if (ch == 'r')
            ch = '\r';
          else if (ch == 't')
            ch = '\t';
          else if (ch == 'u') {
            if (offset + 4 > source.length()) throw error("Incomplete Unicode escape");
            int code = 0;
            for (int i = 0; i < 4; i++) {
              int hex = Character.digit(source.charAt(offset++), 16);
              if (hex < 0) throw error("Invalid Unicode escape");
              code = code * 16 + hex;
            }
            ch = (char) code;
          } else if (ch != '\\' && ch != '\'' && ch != '"')
            throw error("Unsupported string escape");
        }
        result.append(ch);
      }
      throw error("Unterminated string");
    }

    static boolean digit(char ch) {
      return ch >= '0' && ch <= '9';
    }

    static boolean start(char ch) {
      return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_';
    }

    static boolean part(char ch) {
      return start(ch) || digit(ch);
    }
  }
}
