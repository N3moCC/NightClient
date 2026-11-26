package dev.nightclient.ui.skin;

import dev.nightclient.api.Colors;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SkinBindings {
  private final Settings settings;
  private final Map<String, Object> ui;
  private final Map<String, Binding> paths = new HashMap<String, Binding>();
  private final Map<String, ReadPath> readPaths = new HashMap<String, ReadPath>();
  private static final java.util.regex.Pattern VARIABLE_WORD =
      java.util.regex.Pattern.compile("[A-Za-z-]+");
  private static final java.util.regex.Pattern FIELD_NAME =
      java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
  private static final java.util.regex.Pattern SETTING_PATH =
      java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*){0,3}");
  private final Map<String, String[]> contextPaths = new HashMap<String, String[]>();
  private final Map<Class<?>, Map<String, Field>> contextFields =
      new HashMap<Class<?>, Map<String, Field>>();
  private final java.util
      .IdentityHashMap<Map<String, String>, VariableDefinitions> variableDefinitions =
      new java.util.IdentityHashMap<Map<String, String>, VariableDefinitions>();
  private final Map<Map<String, String>, VariableDefinitions> sharedDefinitions =
      new HashMap<Map<String, String>, VariableDefinitions>();
  private final java.util.IdentityHashMap<VariableDefinitions, FrameVariables> frameVariables =
      new java.util.IdentityHashMap<VariableDefinitions, FrameVariables>();
  private java.util.function.Consumer<String> changed;
  private dev.nightclient.api.Canvas measuringCanvas;
  private final Map<PresentationKey, Object> presentationCalls =
      new HashMap<PresentationKey, Object>();
  private Map<String, Object> frameContext = java.util.Collections.emptyMap();
  private long frameEpoch;
  private final Map<String, TextTemplate> templates =
      new java.util.LinkedHashMap<String, TextTemplate>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TextTemplate> entry) {
          return size() > 2048;
        }
      };

  void frameContext(Map<String, Object> values) {
    frameContext = values;
    frameEpoch++;
    presentationCalls.clear();
  }

  private java.util.function.BiFunction<String, List<?>, Integer> retainedCounts;

  void retainedCounts(java.util.function.BiFunction<String, List<?>, Integer> provider) {
    retainedCounts = provider;
  }

  void measurementCanvas(dev.nightclient.api.Canvas canvas) {
    measuringCanvas = canvas;
    frameEpoch++;
    presentationCalls.clear();
  }

  void onChanged(java.util.function.Consumer<String> listener) {
    changed = listener;
  }

  SkinBindings(Settings settings) {
    this(settings, new HashMap<String, Object>());
  }

  SkinBindings(Settings settings, Map<String, Object> ui) {
    this.settings = settings;
    this.ui = ui;
  }

  Object get(String path, Map<String, Object> context) {
    if (path == null) return null;
    return read(readPath(path), context);
  }

  private ReadPath readPath(String path) {
    ReadPath read = readPaths.get(path);
    if (read == null) {
      read = new ReadPath(path);
      if (readPaths.size() >= 2048) readPaths.clear();
      readPaths.put(path, read);
    }
    return read;
  }

  private Object read(ReadPath read, Map<String, Object> context) {
    if (read.expression != null)
      return read.scalar != null ? read.scalar.evaluate(context)
          : read.trace != null   ? read.trace.evaluate(context)
                                 : read.expression.evaluate(resolver(context));
    Object value = context instanceof Context ? ((Context) context).lookup(read.head)
        : context.containsKey(read.head)      ? encode(context.get(read.head))
                                              : Context.MISSING;
    if (value != Context.MISSING) {
      value = decode(value);
      if (read.tail == null) return value;
      if (value instanceof Map) {
        for (String key : read.parts) {
          if (!(value instanceof Map)) return null;
          value = ((Map<?, ?>) value).get(key);
          if (value == null) return null;
        }
        return value;
      }
      if (!(value instanceof Cooldown || value instanceof InventorySlot || value instanceof TntView
              || value instanceof PotionView || value instanceof VitalsView
              || value instanceof TargetView || value instanceof UiCatalog.Module
              || value instanceof UiCatalog.Control))
        return null;
      try {
        Field field = contextField(value.getClass(), read.tail);
        return field == null ? null : field.get(value);
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }
    return read.binding == null ? null : read.binding.read(settings);
  }

  private final class ReadPath {
    final String head, tail;
    final String[] parts;
    final Binding binding;
    final dev.nightclient.skin.NcExpression.Expression expression;
    final BoundScalar scalar;
    final BoundTrace trace;

    ReadPath(String source) {
      String name = source.trim();
      if (name.startsWith("=")) {
        expression = dev.nightclient.skin.NcExpression.compile(name.substring(1));
        scalar = expression.scalarMemoizable() ? new BoundScalar(expression) : null;
        trace = scalar == null && expression.traceMemoizable() ? new BoundTrace(expression) : null;
        head = tail = null;
        parts = null;
        binding = null;
        return;
      }
      expression = null;
      scalar = null;
      trace = null;
      if (name.startsWith("settings.")) name = name.substring(9);
      int dot = name.indexOf('.');
      head = dot < 0 ? name : name.substring(0, dot);
      tail = dot < 0 ? null : name.substring(dot + 1);
      parts = tail == null ? null : contextPath(tail);
      binding = path(name);
    }
  }

  private final class BoundScalar implements java.util.function.IntFunction<Object> {
    private final ReadPath[] paths;
    private final dev.nightclient.skin.NcExpression.ScalarMemo memo;
    private Map<String, Object> context;

    BoundScalar(dev.nightclient.skin.NcExpression.Expression expression) {
      memo = expression.newScalarMemo();
      paths = new ReadPath[expression.scalarInputCount()];
      for (int index = 0; index < paths.length; index++)
        paths[index] = readPath(expression.scalarInputPath(index));
    }

    public Object apply(int index) {
      return read(paths[index], context);
    }

    Object evaluate(Map<String, Object> values) {
      Map<String, Object> previous = context;
      context = values;
      try {
        return memo.evaluateIndexed(this);
      } finally {
        context = previous;
      }
    }

    String text(Map<String, Object> values) {
      Map<String, Object> previous = context;
      context = values;
      try {
        return memo.textIndexed(this);
      } finally {
        context = previous;
      }
    }
  }

  private final class BoundTrace implements java.util.function.IntFunction<Object> {
    private final ReadPath[] paths;
    private final dev.nightclient.skin.NcExpression.TraceMemo memo;
    private Map<String, Object> context;

    BoundTrace(dev.nightclient.skin.NcExpression.Expression expression) {
      memo = expression.newTraceMemo();
      paths = new ReadPath[expression.traceInputCount()];
      for (int index = 0; index < paths.length; index++)
        paths[index] = readPath(expression.traceInputPath(index));
    }

    public Object apply(int index) {
      return read(paths[index], context);
    }

    Object evaluate(Map<String, Object> values) {
      Map<String, Object> previous = context;
      context = values;
      try {
        return memo.evaluateIndexed(this);
      } finally {
        context = previous;
      }
    }

    String text(Map<String, Object> values) {
      Map<String, Object> previous = context;
      context = values;
      try {
        return memo.textIndexed(this);
      } finally {
        context = previous;
      }
    }
  }

  private String[] contextPath(String path) {
    String[] result = contextPaths.get(path);
    if (result == null) {
      result = path.split("\\.");
      if (contextPaths.size() < 2048) contextPaths.put(path, result);
    }
    return result;
  }

  private Field contextField(Class<?> owner, String name) {
    Map<String, Field> fields = contextFields.get(owner);
    if (fields == null) {
      fields = new HashMap<String, Field>();
      contextFields.put(owner, fields);
    }
    if (fields.containsKey(name)) return fields.get(name);
    if (!FIELD_NAME.matcher(name).matches()) return null;
    Field result = null;
    try {
      Field found = owner.getField(name);
      if (scalar(found.getType())) result = found;
    } catch (NoSuchFieldException ignored) {
    }
    fields.put(name, result);
    return result;
  }

  static Map<String, Object> lazyContext() {
    return new LazyContext();
  }

  static void lazy(
      Map<String, Object> context, String name, java.util.function.Supplier<?> supplier) {
    set(context, name, new LazyValue(supplier));
  }

  static FrameLazy frameLazy(Map<String, Object> context, String name, long[] epoch,
      java.util.function.Supplier<?> supplier) {
    FrameLazy slot = new FrameLazy(epoch, supplier);
    set(context, name, slot);
    return slot;
  }

  static void set(Map<String, Object> context, String key, Object value) {
    if (context instanceof Context) ((Context) context).set(key, value);
    else context.put(key, value);
  }

  private static Object encode(Object value) {
    return value == null ? Context.NULL : value;
  }

  private static Object decode(Object value) {
    if (value instanceof LazyValue) value = ((LazyValue) value).get();
    else if (value instanceof FrameLazy) value = ((FrameLazy) value).get();
    return value == Context.NULL || value == Context.MISSING ? null : value;
  }

  static final class FrameLazy {
    private final long[] epoch;
    private final java.util.function.Supplier<?> supplier;
    private long evaluatedAt = Long.MIN_VALUE;
    private Object value;
    private boolean evaluating;

    FrameLazy(long[] epoch, java.util.function.Supplier<?> supplier) {
      if (supplier == null) throw new IllegalArgumentException("A lazy binding needs a supplier");
      this.epoch = epoch;
      this.supplier = supplier;
    }

    Object get() {
      long now = epoch[0];
      if (evaluatedAt != now) {
        if (evaluating) throw new IllegalArgumentException("Recursive lazy NC binding");
        evaluating = true;
        try {
          value = supplier.get();
          evaluatedAt = now;
        } finally {
          evaluating = false;
        }
      }
      return value;
    }
  }

  private static final class LazyValue {
    private java.util.function.Supplier<?> supplier;
    private Object value;
    private boolean evaluating;

    LazyValue(java.util.function.Supplier<?> supplier) {
      if (supplier == null) throw new IllegalArgumentException("A lazy binding needs a supplier");
      this.supplier = supplier;
    }

    Object get() {
      if (supplier != null) {
        if (evaluating) throw new IllegalArgumentException("Recursive lazy NC binding");
        evaluating = true;
        try {
          value = supplier.get();
          supplier = null;
        } finally {
          evaluating = false;
        }
      }
      return value;
    }
  }

  private static final class LazyContext extends java.util.AbstractMap<String, Object> {
    private final Map<String, Object> values;

    LazyContext() {
      this(new HashMap<String, Object>());
    }

    LazyContext(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    public Object get(Object key) {
      return decode(values.get(key));
    }

    @Override
    public boolean containsKey(Object key) {
      return values.containsKey(key);
    }

    @Override
    public Object put(String key, Object value) {
      return decode(values.put(key, encode(value)));
    }

    @Override
    public java.util.Set<Map.Entry<String, Object>> entrySet() {
      return resolvedEntries(values);
    }
  }

  private static java.util.Set<Map.Entry<String, Object>> resolvedEntries(
      final Map<String, Object> values) {
    return new java.util.AbstractSet<Map.Entry<String, Object>>() {
      public int size() {
        return values.size();
      }

      public java.util.Iterator<Map.Entry<String, Object>> iterator() {
        final java.util.Iterator<Map.Entry<String, Object>> iterator = values.entrySet().iterator();
        return new java.util.Iterator<Map.Entry<String, Object>>() {
          public boolean hasNext() {
            return iterator.hasNext();
          }

          public void remove() {
            iterator.remove();
          }

          public Map.Entry<String, Object> next() {
            final Map.Entry<String, Object> entry = iterator.next();
            return new Map.Entry<String, Object>() {
              public String getKey() {
                return entry.getKey();
              }

              public Object getValue() {
                return decode(entry.getValue());
              }

              public Object setValue(Object value) {
                return decode(entry.setValue(encode(value)));
              }

              @Override
              public int hashCode() {
                return java.util.Objects.hashCode(getKey())
                    ^ java.util.Objects.hashCode(getValue());
              }

              @Override
              public boolean equals(Object other) {
                if (!(other instanceof Map.Entry)) return false;
                Map.Entry<?, ?> that = (Map.Entry<?, ?>) other;
                return java.util.Objects.equals(getKey(), that.getKey())
                    && java.util.Objects.equals(getValue(), that.getValue());
              }
            };
          }
        };
      }
    };
  }

  static Map<String, Object> childContext(Map<String, Object> parent) {
    return new Context(parent, 0);
  }

  static Map<String, Object> childContext(Map<String, Object> parent, int initialOwnCapacity) {
    return new Context(parent, initialOwnCapacity);
  }

  static Map<String, Object> resetContext(
      Map<String, Object> existing, Map<String, Object> parent) {
    if (!(existing instanceof Context)) return childContext(parent);
    Context context = (Context) existing;
    if (context == parent)
      throw new IllegalArgumentException("A NC context cannot inherit itself");
    context.parent = parent;
    if (context.own != null) context.own.clear();
    if (context.inherited != null) context.inherited.clear();
    context.inhK0 = context.inhV0 = context.inhK1 = context.inhV1 = null;
    context.clearText();
    if (context.variables instanceof SkinBindings.Variables)
      context.reusableVariables = (SkinBindings.Variables) context.variables;
    context.sharedVariables = null;
    context.identityOverrides = 0;
    context.identity = null;
    context.identityParent = null;
    context.boxWidth = context.boxHeight = context.variables = Context.MISSING;
    return context;
  }

  private static final class Context extends java.util.AbstractMap<String, Object> {
    private static final Object MISSING = new Object(), NULL = new Object(), PRESENT = new Object();
    private Map<String, Object> parent;
    private Map<String, Object> own, inherited;
    private Object inhK0, inhV0, inhK1, inhV1;
    private Map<String, CachedText> textValues;
    private int textGeneration;
    private dev.nightclient.skin.NcExpression.Resolver resolver;
    private SkinBindings resolverOwner;
    private SkinBindings.Variables reusableVariables;
    private SkinBindings.Variables sharedVariables;
    private int identityOverrides;
    private FrameIdentity identity, identityParent;
    private Object boxWidth = MISSING, boxHeight = MISSING, variables = MISSING;

    Context(Map<String, Object> parent, int initialOwnCapacity) {
      this.parent = parent;
      if (initialOwnCapacity > 0) this.own = new HashMap<String, Object>(initialOwnCapacity);
    }

    private Object own(Object key) {
      if (key instanceof String) switch ((String) key) {
          case "boxWidth":
            return boxWidth;
          case "boxHeight":
            return boxHeight;
          case "vars":
            return variables;
          default:
            break;
        }
      Object value = own == null ? null : own.get(key);
      return value == null ? MISSING : value;
    }

    private Object lookupParent(Object key) {
      Map<String, Object> ancestor = parent;
      boolean liveDimensions = "boxWidth".equals(key) || "boxHeight".equals(key);
      while (ancestor instanceof Context) {
        Context scope = (Context) ancestor;
        Object value = scope.own(key);
        if (value != MISSING) return value;
        if (!liveDimensions) {
          if (scope.inhK0 != null && (scope.inhK0 == key || scope.inhK0.equals(key))
              && scope.inhV0 != PRESENT)
            return scope.inhV0;
          if (scope.inhK1 != null && (scope.inhK1 == key || scope.inhK1.equals(key))
              && scope.inhV1 != PRESENT)
            return scope.inhV1;
          if (scope.inherited != null) {
            value = scope.inherited.get(key);
            if (value != null && value != PRESENT) return value;
          }
        }
        ancestor = scope.parent;
      }
      Object value = ancestor.get(key);
      return value != null ? value : ancestor.containsKey(key) ? NULL : MISSING;
    }

    private Object inherited(Object key) {
      if ("boxWidth".equals(key) || "boxHeight".equals(key)) return lookupParent(key);
      if (inhK0 != null && (inhK0 == key || inhK0.equals(key))) {
        if (inhV0 != PRESENT) return inhV0;
        Object value = lookupParent(key);
        inhV0 = value;
        return value;
      }
      if (inhK1 != null && (inhK1 == key || inhK1.equals(key))) {
        if (inhV1 != PRESENT) return inhV1;
        Object value = lookupParent(key);
        inhV1 = value;
        return value;
      }
      Object value = inherited == null ? null : inherited.get(key);
      if (value != null && value != PRESENT) return value;
      value = lookupParent(key);
      String k = String.valueOf(key);
      if (inhK0 == null) {
        inhK0 = k;
        inhV0 = value;
      } else if (inhK1 == null) {
        inhK1 = k;
        inhV1 = value;
      } else {
        if (inherited == null) inherited = new HashMap<String, Object>(8);
        inherited.put(k, value);
      }
      return value;
    }

    private Object lookup(Object key) {
      Object value = own(key);
      return value == MISSING ? inherited(key) : value;
    }

    private FrameIdentity frameIdentity() {
      FrameIdentity parentIdentity = parent instanceof Context
          ? ((Context) parent).frameIdentity()
          : readFrameIdentity(parent, identityParent);
      if (identity != null && identityParent == parentIdentity) return identity;
      identityParent = parentIdentity;
      if (identityOverrides == 0) return identity = parentIdentity;
      int present = parentIdentity.present | identityOverrides;
      String repeat = (identityOverrides & 1) != 0 ? String.valueOf(decode(own.get("__repeatKey")))
                                                   : parentIdentity.repeat;
      String scope = (identityOverrides & 2) != 0 ? String.valueOf(decode(own.get("__scope")))
                                                  : parentIdentity.scope;
      String mount = (identityOverrides & 4) != 0 ? String.valueOf(decode(own.get("__mountKey")))
                                                  : parentIdentity.mount;
      String element = (identityOverrides & 8) != 0 ? String.valueOf(decode(own.get("elementId")))
                                                    : parentIdentity.element;
      return identity = FrameIdentity.create(identity, present, repeat, scope, mount, element);
    }

    private boolean unshadowedFrom(Context target, SkinBindings.Variables variables) {
      Context scope = this;
      for (int depth = 0; depth < 512; depth++) {
        if (scope == target) return true;
        if (scope.own != null && !scope.own.isEmpty()
            || scope.variables != MISSING && scope.variables != variables
            || !(scope.parent instanceof Context))
          return false;
        scope = (Context) scope.parent;
      }
      return false;
    }

    @Override
    public Object get(Object key) {
      return decode(lookup(key));
    }

    @Override
    public boolean containsKey(Object key) {
      if (own(key) != MISSING) return true;
      boolean live = "boxWidth".equals(key) || "boxHeight".equals(key);
      if (!live) {
        if (inhK0 != null && (inhK0 == key || inhK0.equals(key))) return inhV0 != MISSING;
        if (inhK1 != null && (inhK1 == key || inhK1.equals(key))) return inhV1 != MISSING;
        if (inherited != null && inherited.containsKey(key)) return inherited.get(key) != MISSING;
      }
      Map<String, Object> ancestor = parent;
      boolean present = false;
      while (ancestor instanceof Context) {
        Context scope = (Context) ancestor;
        if (scope.own(key) != MISSING) {
          present = true;
          break;
        }
        ancestor = scope.parent;
      }
      if (!present && !(ancestor instanceof Context)) present = ancestor.containsKey(key);
      if (!live) {
        Object mark = present ? PRESENT : MISSING;
        String k = String.valueOf(key);
        if (inhK0 == null) {
          inhK0 = k;
          inhV0 = mark;
        } else if (inhK1 == null) {
          inhK1 = k;
          inhV1 = mark;
        } else {
          if (inherited == null) inherited = new HashMap<String, Object>(8);
          inherited.put(k, mark);
        }
      }
      return present;
    }

    void set(String key, Object value) {
      Object encoded = encode(value);
      if (sharedVariables != null && !"boxWidth".equals(key) && !"boxHeight".equals(key)) {
        if (!"vars".equals(key)) variables = sharedVariables.fork(this);
        sharedVariables = null;
      }
      if ("boxWidth".equals(key))
        boxWidth = encoded;
      else if ("boxHeight".equals(key))
        boxHeight = encoded;
      else if ("vars".equals(key))
        variables = encoded;
      else {
        if (own == null) own = new HashMap<String, Object>(8);
        own.put(key, encoded);
      }
      int identityBit = identityBit(key);
      if (identityBit != 0) {
        identityOverrides |= identityBit;
        identity = null;
      }
      if (!"boxWidth".equals(key) && !"boxHeight".equals(key)) {
        clearText();
        if (variables instanceof SkinBindings.Variables)
          ((SkinBindings.Variables) variables).invalidate();
      }
    }

    @Override
    public Object put(String key, Object value) {
      Object old = own(key);
      if (old == MISSING) old = lookupParent(key);
      set(key, value);
      return decode(old);
    }

    CachedText cachedText(String source) {
      CachedText value = textValues == null ? null : textValues.get(source);
      return value != null && value.generation == textGeneration ? value : null;
    }

    void cacheText(
        String source, String value, long epoch, Object width, Object height, boolean dimensions) {
      if (textValues == null) textValues = new HashMap<String, CachedText>();
      CachedText cached = textValues.get(source);
      if (cached == null) {
        if (textValues.size() >= 256) textValues.clear();
        cached = new CachedText();
        textValues.put(source, cached);
      }
      cached.value = value;
      cached.epoch = epoch;
      cached.boxWidth = width;
      cached.boxHeight = height;
      cached.dimensions = dimensions;
      cached.generation = textGeneration;
    }

    void clearText() {
      textGeneration++;
      if (textGeneration == 0 && textValues != null) textValues.clear();
    }

    @Override
    public java.util.Set<Map.Entry<String, Object>> entrySet() {
      java.util.List<Context> chain = new ArrayList<Context>();
      Map<String, Object> ancestor = this;
      while (ancestor instanceof Context) {
        Context context = (Context) ancestor;
        chain.add(context);
        ancestor = context.parent;
      }
      Map<String, Object> combined = new HashMap<String, Object>(
          ancestor instanceof LazyContext ? ((LazyContext) ancestor).values : ancestor);
      for (int i = chain.size() - 1; i >= 0; i--) {
        Context context = chain.get(i);
        if (context.own != null) combined.putAll(context.own);
        if (context.boxWidth != MISSING) combined.put("boxWidth", context.boxWidth);
        if (context.boxHeight != MISSING) combined.put("boxHeight", context.boxHeight);
        if (context.variables != MISSING) combined.put("vars", context.variables);
      }
      return java.util.Collections.unmodifiableMap(new LazyContext(combined)).entrySet();
    }
  }

  String frameIdentity(Map<String, Object> context) {
    return (context instanceof Context ? ((Context) context).frameIdentity()
                                       : readFrameIdentity(context, null))
        .text;
  }

  private static int identityBit(String key) {
    if ("__repeatKey".equals(key)) return 1;
    if ("__scope".equals(key)) return 2;
    if ("__mountKey".equals(key)) return 4;
    return "elementId".equals(key) ? 8 : 0;
  }

  private static FrameIdentity readFrameIdentity(
      Map<String, Object> context, FrameIdentity previous) {
    int present = 0;
    String repeat = "", scope = null, mount = null, element = null;
    if (context.containsKey("__repeatKey")) {
      present |= 1;
      repeat = String.valueOf(context.get("__repeatKey"));
    }
    if (context.containsKey("__scope")) {
      present |= 2;
      scope = String.valueOf(context.get("__scope"));
    }
    if (context.containsKey("__mountKey")) {
      present |= 4;
      mount = String.valueOf(context.get("__mountKey"));
    }
    if (context.containsKey("elementId")) {
      present |= 8;
      element = String.valueOf(context.get("elementId"));
    }
    return FrameIdentity.create(previous, present, repeat, scope, mount, element);
  }

  private static final class FrameIdentity {
    static final FrameIdentity EMPTY = new FrameIdentity(0, "", null, null, null);
    final int present;
    final String repeat, scope, mount, element, text;

    FrameIdentity(int present, String repeat, String scope, String mount, String element) {
      this.present = present;
      this.repeat = repeat;
      this.scope = scope;
      this.mount = mount;
      this.element = element;
      if ((present & 14) == 0)
        text = repeat;
      else {
        StringBuilder value = new StringBuilder(repeat);
        if ((present & 2) != 0) value.append('/').append(scope);
        if ((present & 4) != 0) value.append('/').append(mount);
        if ((present & 8) != 0) value.append('/').append(element);
        text = value.toString();
      }
    }

    static FrameIdentity create(FrameIdentity previous, int present, String repeat, String scope,
        String mount, String element) {
      if (present == 0) return EMPTY;
      if (previous != null && previous.present == present
          && java.util.Objects.equals(previous.repeat, repeat)
          && java.util.Objects.equals(previous.scope, scope)
          && java.util.Objects.equals(previous.mount, mount)
          && java.util.Objects.equals(previous.element, element))
        return previous;
      return new FrameIdentity(present, repeat, scope, mount, element);
    }
  }

  private dev.nightclient.skin.NcExpression.Resolver resolver(final Map<String, Object> context) {
    if (context instanceof Context) {
      Context scope = (Context) context;
      if (scope.resolver == null || scope.resolverOwner != this) {
        scope.resolver = new ContextResolver(context);
        scope.resolverOwner = this;
      }
      return scope.resolver;
    }
    return new ContextResolver(context);
  }

  private final class ContextResolver implements dev.nightclient.skin.NcExpression.Resolver {
    private final Map<String, Object> context;

    ContextResolver(Map<String, Object> context) {
      this.context = context;
    }

    public Object apply(String name) {
      return get(name, context);
    }

    public Object call(String name, List<Object> arguments) {
      return presentation(name, arguments, context);
    }
  }

  int retainedCount(String source, Map<String, Object> context) {
    Object values = get(source, context);
    List<?> rows = values instanceof List ? (List<?>) values : java.util.Collections.emptyList();
    return retainedCounts == null ? rows.size() : retainedCounts.apply(source, rows);
  }

  private Object presentation(String name, List<Object> args, Map<String, Object> context) {
    if ("format".equals(name)) return formatted(args.get(0), String.valueOf(args.get(1)));
    boolean rowWidth = "maxRowTextWidth".equals(name), maxWidth = "maxTextWidth".equals(name),
            gameText = "gameTextWidth".equals(name),
            gameRowWidth = "maxRowGameTextWidth".equals(name);
    String source = "";
    List<?> rows = null;
    if ("retainedCount".equals(name) || maxWidth || rowWidth || gameRowWidth) {
      source = String.valueOf(args.get(0));
      Object values = get(source, context);
      rows = values instanceof List ? (List<?>) values : java.util.Collections.emptyList();
    }
    PresentationKey cache = new PresentationKey(name, args, rows);
    if (presentationCalls.containsKey(cache)) return presentationCalls.get(cache);
    if ("retainedCount".equals(name)) {
      int count = retainedCounts == null ? rows.size() : retainedCounts.apply(source, rows);
      if (presentationCalls.size() < 1024) presentationCalls.put(cache, count);
      return count;
    }
    if (gameText || gameRowWidth) {
      float scale = number(args.get(gameRowWidth ? 3 : 1), 1, .1f, 512), width = 0;
      if (gameText)
        width = measureGame(String.valueOf(args.get(0)), scale);
      else {
        MeasuredField first = new MeasuredField(String.valueOf(args.get(1)), true),
                      second = new MeasuredField(String.valueOf(args.get(2)), true);
        int count = 0;
        Map<String, Object> scoped = childContext(context);
        for (Object row : rows) {
          if (++count > 512) break;
          scoped.put("measuredRow", row);
          float current = first.gameWidth(scoped, scale) + second.gameWidth(scoped, scale);
          width = Math.max(width, current);
        }
      }
      if (presentationCalls.size() < 1024) presentationCalls.put(cache, width);
      return width;
    }
    int at = rowWidth ? 3 : maxWidth ? 2 : 1;
    float size = number(args.get(at), 12, .1f, 512),
          tracking = args.size() > at + 3 ? number(args.get(at + 3), 0, -16, 32) : 0;
    String family =
        args.size() > at + 1 ? String.valueOf(args.get(at + 1)) : settings.theme.fontFamily;
    boolean bold =
        args.size() > at + 2 && dev.nightclient.skin.NcExpression.truth(args.get(at + 2));
    float width = 0;
    if ("textWidth".equals(name))
      width = measure(family, String.valueOf(args.get(0)), size, bold, tracking);
    else {
      MeasuredField first = new MeasuredField(String.valueOf(args.get(1)), rowWidth),
                    second = rowWidth ? new MeasuredField(String.valueOf(args.get(2)), true) : null;
      int count = 0;
      Map<String, Object> scoped = childContext(context);
      for (Object row : rows) {
        if (++count > 512) break;
        scoped.put("measuredRow", row);
        float current = first.width(scoped, family, size, bold, tracking);
        if (second != null) current += second.width(scoped, family, size, bold, tracking);
        width = Math.max(width, current);
      }
    }
    if (presentationCalls.size() < 1024) presentationCalls.put(cache, width);
    return width;
  }

  private final class MeasuredField {
    final String path, format;
    final boolean empty;

    MeasuredField(String field, boolean allowEmpty) {
      int pipe = field.indexOf('|');
      String name = pipe < 0 ? field : field.substring(0, pipe);
      format = pipe < 0 ? "" : field.substring(pipe + 1);
      empty = allowEmpty && name.isEmpty();
      path = name.isEmpty() ? "measuredRow" : "measuredRow." + name;
    }

    float width(
        Map<String, Object> context, String family, float size, boolean bold, float tracking) {
      return empty ? 0
                   : measure(family, formatted(get(path, context), format), size, bold, tracking);
    }

    float gameWidth(Map<String, Object> context, float scale) {
      return empty ? 0 : measureGame(formatted(get(path, context), format), scale);
    }
  }

  private static final class PresentationKey {
    final String name;
    final List<Object> arguments;
    final Object rows;
    final int hash;

    PresentationKey(String name, List<Object> arguments, Object rows) {
      this.name = name;
      this.arguments = arguments;
      this.rows = rows;
      hash = (name.hashCode() * 31 + arguments.hashCode()) * 31 + System.identityHashCode(rows);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object value) {
      if (!(value instanceof PresentationKey)) return false;
      PresentationKey other = (PresentationKey) value;
      return rows == other.rows && name.equals(other.name) && arguments.equals(other.arguments);
    }
  }

  private float measure(String family, String text, float size, boolean bold, float tracking) {
    if (text.length() > 1024) text = text.substring(0, 1024);
    return measuringCanvas == null
        ? dev.nightclient.renderer.FontAtlas.get(family).width(text, size, bold, tracking)
        : measuringCanvas.textWidthFamily(family, text, size, bold, tracking);
  }

  private float measureGame(String text, float scale) {
    if (text.length() > 1024) text = text.substring(0, 1024);
    return measuringCanvas == null
        ? dev.nightclient.renderer.FontAtlas.get(settings.theme.fontFamily)
              .width(dev.nightclient.api.FormattedText.plainText(text), 8.2f * scale, false, 0)
        : dev.nightclient.api.FormattedText.gameWidth(measuringCanvas, text, scale);
  }

  private static String decimal(Number value, int digits) {
    String result = String.format(Locale.ROOT, "%." + digits + "f", value.doubleValue());
    if (result.indexOf('.') >= 0) {
      int end = result.length();
      while (end > 0 && result.charAt(end - 1) == '0') end--;
      if (end > 0 && result.charAt(end - 1) == '.') end--;
      result = result.substring(0, end);
    }
    return "-0".equals(result) ? "0" : result;
  }

  private static float number(Object value, float fallback, float min, float max) {
    if (!(value instanceof Number)) return fallback;
    float result = ((Number) value).floatValue();
    return Float.isFinite(result) ? Math.max(min, Math.min(max, result)) : fallback;
  }

  private String formatted(Object value, String format) {
    String formatted = value == null ? "" : String.valueOf(value);
    if ("plain".equals(format)) return dev.nightclient.api.FormattedText.plainText(formatted);
    if (value instanceof Number) {
      if (format.length() == 7 && format.startsWith("fixed:") && format.charAt(6) >= '0'
          && format.charAt(6) <= '3')
        return String.format(
            Locale.ROOT, "%." + format.charAt(6) + "f", ((Number) value).doubleValue());
      if (format.length() == 9 && format.startsWith("decimal:") && format.charAt(8) >= '0'
          && format.charAt(8) <= '3')
        return decimal((Number) value, format.charAt(8) - '0');
      if ("integer".equals(format))
        return Long.toString(Math.round(((Number) value).doubleValue()));
      if ("cooldown-duration".equals(format))
        return dev.nightclient.ui.HudRenderer.cooldownDuration(
            ((Number) value).floatValue(), settings.language);
      if ("effect-duration".equals(format))
        return dev.nightclient.ui.HudRenderer.effectDuration(
            ((Number) value).floatValue(), settings.language);
      if ("duration".equals(format) || "clock".equals(format))
        return dev.nightclient.ui.HudRenderer.duration(
            ((Number) value).floatValue(), settings.language, "clock".equals(format));
    }
    return formatted.length() > 2048 ? formatted.substring(0, 2048) : formatted;
  }

  boolean set(String name, String value, boolean hex) {
    return set(name, value, hex, null);
  }

  boolean set(String name, String value, boolean hex, Map<String, Object> context) {
    if (name == null || value == null) return false;
    if (name.startsWith("local.") && context != null
        && context.get("local") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> local = (Map<String, Object>) context.get("local");
      return setState(local, name.substring(6), value, hex);
    }
    if (name.startsWith("ui.") && ui.containsKey(name.substring(3))) {
      try {
        String key = name.substring(3);
        Object old = ui.get(key);
        if (value.length() > 128) return false;
        Object parsed = old instanceof Integer
            ? (hex ? Colors.parseHex(value) : Integer.parseInt(value))
            : old instanceof Float   ? Float.parseFloat(value)
            : old instanceof Boolean ? Boolean.parseBoolean(value)
                                     : value;
        if (parsed instanceof Float && !Float.isFinite((Float) parsed)) return false;
        if (parsed.equals(old)) return false;
        ui.put(key, parsed);
        return true;
      } catch (IllegalArgumentException ignored) {
        return false;
      }
    }
    Binding binding = path(name);
    if (binding == null) return false;
    try {
      Class<?> type = binding.type;
      Object parsed;
      if (type == boolean.class)
        parsed = Boolean.parseBoolean(value);
      else if (type == int.class)
        parsed = hex ? Colors.parseHex(value) : Integer.parseInt(value);
      else if (type == float.class) {
        float number = Float.parseFloat(value);
        if (!Float.isFinite(number)) return false;
        parsed = number;
      } else if (type == String.class)
        parsed = value.length() > 128 ? value.substring(0, 128) : value;
      else
        return false;
      if (parsed.equals(binding.read(settings))) return false;
      if ("theme.accent".equals(name) && parsed instanceof Integer) {
        if (!"custom".equals(settings.theme.mode)) {
          settings.theme.customBackground = settings.theme.background;
          settings.theme.customSurface = settings.theme.surface;
          settings.theme.customRaised = settings.theme.raised;
          settings.theme.customText = settings.theme.text;
          settings.theme.mode = "custom";
        }
        settings.theme.customAccent = (Integer) parsed;
        settings.theme.customAccentEnabled = true;
      } else if ("theme.customAccent".equals(name) && parsed instanceof Integer) {
        settings.theme.customAccentEnabled = true;
      }
      binding.write(settings, parsed);
      settings.validate();
      if (changed != null) changed.accept(name);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static boolean setState(
      Map<String, Object> values, String key, String value, boolean hex) {
    if (!values.containsKey(key) || value == null || value.length() > 128) return false;
    try {
      Object old = values.get(key),
             parsed = old instanceof Integer
          ? (hex ? Colors.parseHex(value) : Integer.parseInt(value))
          : old instanceof Number  ? Float.parseFloat(value)
          : old instanceof Boolean ? Boolean.parseBoolean(value)
                                   : value;
      if (parsed instanceof Float && !Float.isFinite((Float) parsed) || parsed.equals(old))
        return false;
      values.put(key, parsed);
      return true;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  boolean writable(String name) {
    return name != null && name.startsWith("ui.") && ui.containsKey(name.substring(3))
        || path(name) != null;
  }

  boolean writable(String name, Map<String, Object> context) {
    return name != null
            && name.startsWith("local.")
            && context != null
            && context.get("local") instanceof Map
            && ((Map<?, ?>) context.get("local")).containsKey(name.substring(6))
        || writable(name);
  }

  Class<?> type(String name) {
    Binding binding = path(name);
    return binding == null ? null : binding.type;
  }

  Map<String, Object> scope(Map<String, Object> values, Map<String, String> style) {
    return scope(values, definitions(style));
  }

  private VariableDefinitions definitions(Map<String, String> style) {
    VariableDefinitions definitions = variableDefinitions.get(style);
    if (definitions == null) {
      definitions = new VariableDefinitions(style);
      VariableDefinitions shared = sharedDefinitions.get(definitions.values);
      if (shared == null)
        sharedDefinitions.put(definitions.values, definitions);
      else
        definitions = shared;
      variableDefinitions.put(style, definitions);
    }
    return definitions;
  }

  Map<String, Object> nodeContext(
      Map<String, Object> parent, Map<String, String> style, boolean allowAlias) {
    VariableDefinitions definitions = definitions(style);
    if (allowAlias && canAliasNodeContext(parent, definitions)) return parent;
    return scope(childContext(parent), definitions);
  }

  boolean canAliasNodeContext(Map<String, Object> parent, Map<String, String> style) {
    return canAliasNodeContext(parent, definitions(style));
  }

  private boolean canAliasNodeContext(Map<String, Object> parent, VariableDefinitions definitions) {
    if (parent instanceof Context && definitions.dimensionIndexes.length == 0) {
      Context context = (Context) parent;
      Object inherited = context.get("vars");
      if (definitions.values.isEmpty() && inherited == null) return true;
      if (inherited instanceof Variables) {
        Variables candidate = (Variables) inherited;
        if (candidate.recipe == definitions && candidate.owner() == this
            && candidate.context instanceof Context
            && context.unshadowedFrom((Context) candidate.context, candidate))
          return true;
      }
    }
    return false;
  }

  private Map<String, Object> scope(Map<String, Object> values, VariableDefinitions definitions) {
    if (definitions.values.isEmpty()) return values;
    Variables variables = null;
    if (values instanceof Context) {
      Context context = (Context) values;
      if (definitions.dimensionIndexes.length == 0 && context.variables == Context.MISSING) {
        Object inherited = context.get("vars");
        if (inherited instanceof Variables) {
          Variables candidate = (Variables) inherited;
          if (candidate.recipe == definitions && candidate.owner() == this
              && candidate.context instanceof Context
              && context.unshadowedFrom((Context) candidate.context, candidate)) {
            context.sharedVariables = candidate;
            context.reusableVariables = null;
            return values;
          }
        }
      }
      Variables reusable = context.reusableVariables;
      if (reusable != null && reusable.recipe == definitions && reusable.owner() == this) {
        variables = reusable;
        variables.reset();
      }
      context.reusableVariables = null;
    }
    set(values, "vars", variables == null ? new Variables(values, definitions) : variables);
    return values;
  }

  void invalidateVariables(Map<String, Object> context) {
    Object variables = context.get("vars");
    if (variables instanceof Variables) {
      Variables values = (Variables) variables;
      if (values.cachedCount > 0) {
        frameVariables.remove(values.recipe);
        frameEpoch++;
      }
      values.invalidate();
    }
    if (context instanceof Context) ((Context) context).clearText();
  }

  void dimensionsChanged(Map<String, Object> context) {
    Object variables = context.get("vars");
    if (variables instanceof Variables
        && ((Variables) variables).recipe.dimensionIndexes.length > 0)
      ((Variables) variables).dimensionsChanged();
  }

  private final class Variables extends java.util.AbstractMap<String, Object> {
    private final Map<String, Object> context;
    private Object[] cache, sharedCache;
    private int cachedCount, localCachedCount, evaluatingCount;
    private long epoch = Long.MIN_VALUE;
    private Object boxWidthSeen, boxHeightSeen;
    private final VariableDefinitions recipe;
    private final Map<String, String> definitions;
    private long evaluatingBits, sharedVerifiedBits;
    private long[] extraEvaluatingBits, extraSharedVerifiedBits;

    Variables(Map<String, Object> context, VariableDefinitions definitions) {
      this.context = context;
      this.recipe = definitions;
      this.definitions = definitions.values;
    }

    SkinBindings owner() {
      return SkinBindings.this;
    }

    Variables fork(Map<String, Object> values) {
      return new Variables(values, recipe);
    }

    void reset() {
      invalidate();
      epoch = Long.MIN_VALUE;
      boxWidthSeen = boxHeightSeen = null;
      evaluatingBits = 0;
      if (extraEvaluatingBits != null) java.util.Arrays.fill(extraEvaluatingBits, 0);
      evaluatingCount = 0;
    }

    void invalidate() {
      if (cache != null && localCachedCount > 0) java.util.Arrays.fill(cache, null);
      cachedCount = localCachedCount = 0;
      sharedCache = null;
      sharedVerifiedBits = 0;
      if (extraSharedVerifiedBits != null) java.util.Arrays.fill(extraSharedVerifiedBits, 0);
    }

    void dimensionsChanged() {
      if (cachedCount > 0) {
        for (int index : recipe.dimensionIndexes) {
          if (cache != null && cache[index] != null) {
            cache[index] = null;
            cachedCount--;
            localCachedCount--;
          }
          if (sharedVerified(index)) {
            unverifyShared(index);
            cachedCount--;
          }
        }
      }
      boxWidthSeen = context.get("boxWidth");
      boxHeightSeen = context.get("boxHeight");
    }

    boolean dimensionDependent(String key) {
      Integer index = recipe.indexes.get(key);
      return index == null || recipe.plans[index].dimensionDependent;
    }

    private boolean enter(int index) {
      if (evaluatingCount >= 24) return false;
      long bit = 1L << (index & 63);
      if (index < 64) {
        if ((evaluatingBits & bit) != 0) return false;
        evaluatingBits |= bit;
      } else {
        if (extraEvaluatingBits == null)
          extraEvaluatingBits = new long[(recipe.plans.length + 63) / 64 - 1];
        int word = (index >>> 6) - 1;
        if ((extraEvaluatingBits[word] & bit) != 0) return false;
        extraEvaluatingBits[word] |= bit;
      }
      evaluatingCount++;
      return true;
    }

    private void leave(int index) {
      long bit = 1L << (index & 63);
      if (index < 64)
        evaluatingBits &= ~bit;
      else
        extraEvaluatingBits[(index >>> 6) - 1] &= ~bit;
      evaluatingCount--;
    }

    private Object cached(Object value) {
      return value == Context.NULL ? null : value;
    }

    private void remember(int index, Object value) {
      if (cache == null) cache = new Object[recipe.plans.length];
      if (cache[index] == null) {
        cachedCount++;
        localCachedCount++;
      }
      cache[index] = encode(value);
    }

    private boolean sharedVerified(int index) {
      long bit = 1L << (index & 63);
      return index < 64 ? (sharedVerifiedBits & bit) != 0
                        : extraSharedVerifiedBits != null
              && (extraSharedVerifiedBits[(index >>> 6) - 1] & bit) != 0;
    }

    private void unverifyShared(int index) {
      long bit = 1L << (index & 63);
      if (index < 64)
        sharedVerifiedBits &= ~bit;
      else if (extraSharedVerifiedBits != null)
        extraSharedVerifiedBits[(index >>> 6) - 1] &= ~bit;
    }

    private void rememberShared(int index, Object[] shared) {
      if (sharedCache != shared) {
        cachedCount = localCachedCount;
        sharedVerifiedBits = 0;
        if (extraSharedVerifiedBits != null) java.util.Arrays.fill(extraSharedVerifiedBits, 0);
        sharedCache = shared;
      }
      long bit = 1L << (index & 63);
      if (!sharedVerified(index)) cachedCount++;
      if (index < 64)
        sharedVerifiedBits |= bit;
      else {
        if (extraSharedVerifiedBits == null)
          extraSharedVerifiedBits = new long[(recipe.plans.length + 63) / 64 - 1];
        extraSharedVerifiedBits[(index >>> 6) - 1] |= bit;
      }
    }

    @Override
    public Object get(Object key) {
      Integer slot = recipe.indexes.get(key);
      if (slot == null) return null;
      int index = slot;
      VariablePlan plan = recipe.plans[index];
      if (epoch != frameEpoch) {
        invalidate();
        epoch = frameEpoch;
      }
      if (plan.dimensionDependent
          && (!java.util.Objects.equals(boxWidthSeen, context.get("boxWidth"))
              || !java.util.Objects.equals(boxHeightSeen, context.get("boxHeight"))))
        dimensionsChanged();
      if (cache != null && cache[index] != null) return cached(cache[index]);
      if (sharedVerified(index)) return cached(sharedCache[index]);
      if (!enter(index)) return null;
      Object[] shared = null;
      Dependencies dependencies = plan.dependencies;
      boolean reusable = dependencies != null && dependencies.matches(context);
      if (reusable) {
        FrameVariables frame = frameVariables.get(recipe);
        if (frame == null) {
          frame = new FrameVariables(recipe.plans.length);
          frameVariables.put(recipe, frame);
        }
        shared = frame.values;
        if (frame.epoch != frameEpoch) {
          java.util.Arrays.fill(shared, null);
          frame.epoch = frameEpoch;
        }
        if (shared[index] != null) {
          Object value = cached(shared[index]);
          rememberShared(index, shared);
          leave(index);
          return value;
        }
      }
      Object value;
      try {
        if (plan.raw.startsWith("#"))
          value = plan.raw;
        else if (plan.word) {
          Object bound = SkinBindings.this.get(plan.raw, context);
          value = bound == null ? plan.raw : bound;
        } else if (plan.raw.startsWith("{{"))
          value = text(plan.raw, context);
        else
          value = plan.expression == null ? null
              : plan.scalar != null       ? plan.scalar.evaluate(context)
              : plan.trace != null        ? plan.trace.evaluate(context)
                                          : plan.expression.evaluate(resolver(context));
      } catch (IllegalArgumentException failure) {
        value = null;
      } finally {
        leave(index);
      }
      if (shared != null) {
        shared[index] = encode(value);
        rememberShared(index, shared);
      } else
        remember(index, value);
      return value;
    }

    @Override
    public java.util.Set<Map.Entry<String, Object>> entrySet() {
      return java.util.Collections.emptySet();
    }
  }

  private static final class FrameVariables {
    final Object[] values;
    long epoch = Long.MIN_VALUE;

    FrameVariables(int size) {
      values = new Object[size];
    }
  }

  private final class Dependencies {
    final java.util.Set<String> settingHeads = new java.util.HashSet<String>(),
                                frameHeads = new java.util.HashSet<String>();
    String[] settingPaths, framePaths;

    void compile() {
      settingPaths = settingHeads.toArray(new String[settingHeads.size()]);
      framePaths = frameHeads.toArray(new String[frameHeads.size()]);
    }

    void addAll(Dependencies other) {
      settingHeads.addAll(other.settingHeads);
      frameHeads.addAll(other.frameHeads);
    }

    boolean matches(Map<String, Object> context) {
      for (String head : settingPaths)
        if (context.containsKey(head)) return false;
      for (String head : framePaths) {
        if (!frameContext.containsKey(head)) return false;
        Object value = context.get(head), root = frameContext.get(head);
        if (value != root
            && (!(value instanceof String || value instanceof Number || value instanceof Boolean)
                || !value.equals(root)))
          return false;
      }
      return true;
    }
  }

  private final class VariableDefinitions {
    final Map<String, String> values = new HashMap<String, String>();
    final java.util.Set<String> words = new java.util.HashSet<String>();
    final Map<String, dev.nightclient.skin.NcExpression.Expression> expressions =
        new HashMap<String, dev.nightclient.skin.NcExpression.Expression>();
    final Map<String, Dependencies> sharedDependencies = new HashMap<String, Dependencies>();
    final Map<String, Boolean> dimensionDependencies = new HashMap<String, Boolean>();
    final Map<String, Integer> indexes = new HashMap<String, Integer>();
    final VariablePlan[] plans;
    final int[] dimensionIndexes;

    VariableDefinitions(Map<String, String> styles) {
      for (Map.Entry<String, String> entry : styles.entrySet())
        if (entry.getKey().startsWith("--")) {
          try {
            String name =
                       dev.nightclient.skin.SkinDocument.variablePath(entry.getKey()).substring(5),
                   raw = dev.nightclient.skin.SkinDocument.expressionVariables(
                       entry.getValue(), styles);
            values.put(name, raw);
            if (VARIABLE_WORD.matcher(raw).matches() && !"true".equals(raw) && !"false".equals(raw))
              words.add(name);
            if (!raw.startsWith("#") && !raw.contains("{{")) try {
                expressions.put(name, dev.nightclient.skin.NcExpression.compile(raw));
              } catch (IllegalArgumentException ignored) {
              }
          } catch (java.io.IOException invalid) {
            throw new IllegalArgumentException("Invalid NC variable", invalid);
          }
        }
      for (String name : values.keySet()) {
        Dependencies dependencies = dependencies(name, new java.util.HashSet<String>());
        if (dependencies != null) {
          dependencies.compile();
          sharedDependencies.put(name, dependencies);
        }
        dimensionDependencies.put(name,
            dependencies == null || dependencies.frameHeads.contains("boxWidth")
                || dependencies.frameHeads.contains("boxHeight"));
      }
      plans = new VariablePlan[values.size()];
      int index = 0, dimensions = 0;
      for (String name : values.keySet()) {
        indexes.put(name, index);
        VariablePlan plan = new VariablePlan(values.get(name), expressions.get(name),
            words.contains(name), sharedDependencies.get(name),
            !Boolean.FALSE.equals(dimensionDependencies.get(name)));
        plans[index++] = plan;
        if (plan.dimensionDependent) dimensions++;
      }
      dimensionIndexes = new int[dimensions];
      index = 0;
      for (int at = 0; at < plans.length; at++)
        if (plans[at].dimensionDependent) dimensionIndexes[index++] = at;
    }

    private Dependencies dependencies(String name, java.util.Set<String> visiting) {
      if (!visiting.add(name) || visiting.size() > 24) return null;
      String raw = values.get(name);
      if (raw == null) return null;
      Dependencies result = new Dependencies();
      if (raw.startsWith("#")) return result;
      dev.nightclient.skin.NcExpression.Expression expression = expressions.get(name);
      if (expression == null || !expression.fixedPresentationSources()) return null;
      for (String id : expression.contextPaths()) {
        if (id.startsWith("vars.")) {
          Dependencies nested =
              dependencies(id.substring(5), new java.util.HashSet<String>(visiting));
          if (nested == null) return null;
          result.addAll(nested);
        } else {
          String path = id.startsWith("settings.") ? id.substring(9) : id;
          int dot = path.indexOf('.');
          String head = dot < 0 ? path : path.substring(0, dot);
          if (SkinBindings.this.path(path) != null)
            result.settingHeads.add(head);
          else
            result.frameHeads.add(head);
        }
      }
      return result;
    }
  }

  private final class VariablePlan {
    final String raw;
    final dev.nightclient.skin.NcExpression.Expression expression;
    final BoundScalar scalar;
    final BoundTrace trace;
    final boolean word, dimensionDependent;
    final Dependencies dependencies;

    VariablePlan(String raw, dev.nightclient.skin.NcExpression.Expression expression, boolean word,
        Dependencies dependencies, boolean dimensionDependent) {
      this.raw = raw;
      this.expression = expression;
      this.scalar =
          expression != null && expression.scalarMemoizable() ? new BoundScalar(expression) : null;
      this.trace = this.scalar == null && expression != null && expression.traceMemoizable()
          ? new BoundTrace(expression)
          : null;
      this.word = word;
      this.dependencies = dependencies;
      this.dimensionDependent = dimensionDependent;
    }
  }

  static int formatSeparator(String expression) {
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\' && quote != 0) {
        escaped = true;
        continue;
      }
      if (quote != 0) {
        if (c == quote) quote = 0;
        continue;
      }
      if (c == '\'' || c == '"') {
        quote = c;
        continue;
      }
      if (c == '|' && (i == 0 || expression.charAt(i - 1) != '|')
          && (i + 1 == expression.length() || expression.charAt(i + 1) != '|'))
        return i;
    }
    return -1;
  }

  String text(String source, Map<String, Object> context) {
    if (source == null) return "";
    if (source.indexOf("{{") < 0)
      return source.length() <= 2048 ? source : source.substring(0, 2048);
    TextTemplate template = templates.get(source);
    if (template == null) {
      template = new TextTemplate(source);
      templates.put(source, template);
    }
    return text(source, template, context);
  }

  final class BoundText {
    final String source, literal;
    final TextTemplate template;

    BoundText(String source) {
      this.source = source;
      literal = source == null ? ""
          : source.indexOf("{{") < 0
          ? (source.length() <= 2048 ? source : source.substring(0, 2048))
          : null;
      if (literal != null)
        template = null;
      else {
        TextTemplate compiled = templates.get(source);
        if (compiled == null) {
          compiled = new TextTemplate(source);
          templates.put(source, compiled);
        }
        template = compiled;
      }
    }
  }

  BoundText compileText(String source) {
    return new BoundText(source);
  }

  String text(BoundText text, Map<String, Object> context) {
    return text.template == null ? text.literal : text(text.source, text.template, context);
  }

  boolean dimensionDependent(BoundText text, Map<String, Object> context) {
    return text.template != null && text.template.dimensionDependent(context);
  }

  private String text(String source, TextTemplate template, Map<String, Object> context) {
    Context scope = context instanceof Context ? (Context) context : null;
    if (scope != null) {
      CachedText cached = scope.cachedText(source);
      if (cached != null && cached.epoch == frameEpoch
          && (!cached.dimensions
              || java.util.Objects.equals(cached.boxWidth, context.get("boxWidth"))
                  && java.util.Objects.equals(cached.boxHeight, context.get("boxHeight"))))
        return cached.value;
    }
    String value = template.render(context);
    if (scope != null) {
      boolean dimensions = template.dimensionDependent(context);
      scope.cacheText(source, value, frameEpoch, dimensions ? context.get("boxWidth") : null,
          dimensions ? context.get("boxHeight") : null, dimensions);
    }
    return value;
  }

  boolean dimensionDependent(String source, Map<String, Object> context) {
    if (source == null || source.indexOf("{{") < 0) return false;
    TextTemplate template = templates.get(source);
    if (template == null) {
      template = new TextTemplate(source);
      templates.put(source, template);
    }
    return template.dimensionDependent(context);
  }

  Object variableRecipe(Map<String, Object> context) {
    Object variables = context.get("vars");
    return variables instanceof Variables ? ((Variables) variables).recipe : variables;
  }

  private static final class CachedText {
    String value;
    long epoch;
    Object boxWidth, boxHeight;
    boolean dimensions;
    int generation;
  }

  private final class TextPart {
    final String literal, path, format;
    final ReadPath directPath;
    final dev.nightclient.skin.NcExpression.Expression expression;
    final BoundScalar scalar;
    final BoundTrace trace;
    final boolean directDimensions;
    final String[] dimensionVariables;
    Object lastValue;
    String lastText;

    TextPart(String literal) {
      this.literal = literal;
      path = format = null;
      directPath = null;
      expression = null;
      scalar = null;
      trace = null;
      directDimensions = false;
      dimensionVariables = new String[0];
    }

    TextPart(String binding, String format) {
      literal = null;
      path = binding.trim();
      this.format = format;
      expression = path.startsWith("=")
          ? dev.nightclient.skin.NcExpression.compile(path.substring(1))
          : null;
      directPath = expression == null ? readPath(path) : null;
      scalar =
          expression != null && expression.scalarMemoizable() ? new BoundScalar(expression) : null;
      trace = scalar == null && expression != null && expression.traceMemoizable()
          ? new BoundTrace(expression)
          : null;
      boolean dimensions = expression != null && !expression.fixedPresentationSources();
      List<String> variables = new ArrayList<String>();
      Iterable<String> paths =
          expression == null ? java.util.Collections.singleton(path) : expression.contextPaths();
      for (String name : paths) {
        if ("boxWidth".equals(name) || "boxHeight".equals(name))
          dimensions = true;
        else if (name.startsWith("vars."))
          variables.add(name.substring(5));
      }
      directDimensions = dimensions;
      dimensionVariables = variables.toArray(new String[variables.size()]);
    }

    String render(Map<String, Object> context) {
      if (scalar != null && format == null) return scalar.text(context);
      if (trace != null && format == null) return trace.text(context);
      Object value = expression == null ? read(directPath, context)
          : scalar != null              ? scalar.evaluate(context)
          : trace != null               ? trace.evaluate(context)
                                        : expression.evaluate(resolver(context));
      if (format == null) {
        if (value == null) return "";
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
          if (value.equals(lastValue)) return lastText;
          String text = String.valueOf(value);
          lastValue = value;
          lastText = text;
          return text;
        }
        return String.valueOf(value);
      }
      return formatted(value, format);
    }
  }

  private final class TextTemplate {
    final java.util.List<TextPart> parts = new ArrayList<TextPart>();
    final boolean directDimensions;
    final String[] dimensionVariables;

    TextTemplate(String source) {
      int at = 0, substitutions = 0;
      while (at < source.length()) {
        int open = source.indexOf("{{", at);
        if (open < 0) {
          parts.add(new TextPart(source.substring(at)));
          break;
        }
        if (open > at) parts.add(new TextPart(source.substring(at, open)));
        int close = source.indexOf("}}", open + 2);
        if (close < 0 || ++substitutions > 32) break;
        String body = source.substring(open + 2, close).trim();
        int separator = formatSeparator(body);
        parts.add(new TextPart(separator < 0 ? body : body.substring(0, separator),
            separator < 0 ? null : body.substring(separator + 1).trim()));
        at = close + 2;
      }
      boolean dimensions = false;
      java.util.Set<String> variables = new java.util.LinkedHashSet<String>();
      for (TextPart part : parts) {
        dimensions |= part.directDimensions;
        java.util.Collections.addAll(variables, part.dimensionVariables);
      }
      directDimensions = dimensions;
      dimensionVariables = variables.toArray(new String[variables.size()]);
    }

    String render(Map<String, Object> context) {
      if (parts.size() == 1) {
        String value =
            parts.get(0).literal == null ? parts.get(0).render(context) : parts.get(0).literal;
        return value.length() <= 2048 ? value : value.substring(0, 2048);
      }
      StringBuilder value = new StringBuilder();
      for (TextPart part : parts) {
        String text = part.literal == null ? part.render(context) : part.literal;
        value.append(text, 0, Math.min(text.length(), 2048 - value.length()));
        if (value.length() >= 2048) break;
      }
      return value.toString();
    }

    boolean dimensionDependent(Map<String, Object> context) {
      if (directDimensions) return true;
      if (dimensionVariables.length == 0) return false;
      Object variables = context.get("vars");
      if (!(variables instanceof Variables)) return true;
      for (String key : dimensionVariables)
        if (((Variables) variables).dimensionDependent(key)) return true;
      return false;
    }
  }

  boolean condition(String expression, Map<String, Object> context) {
    if (expression == null || expression.trim().isEmpty()) return true;
    expression = expression.trim();
    if (expression.startsWith("{{")) {
      String expanded = text(expression, context);
      return "true".equals(expanded)
          || !"false".equals(expanded) && !"0".equals(expanded) && !expanded.isEmpty();
    }
    if (expression.startsWith("="))
      return dev.nightclient.skin.NcExpression.truth(get(expression, context));
    if (expression.contains("||")) {
      for (String branch : expression.split("\\|\\|"))
        if (condition(branch, context)) return true;
      return false;
    }
    if (expression.contains("&&")) {
      for (String term : expression.split("&&"))
        if (!condition(term, context)) return false;
      return true;
    }
    boolean negate = expression.startsWith("!");
    if (negate) expression = expression.substring(1).trim();
    int equal = expression.indexOf("==");
    if (equal < 0) equal = expression.indexOf('=');
    boolean result;
    if (equal >= 0) {
      int skip = expression.startsWith("==", equal) ? 2 : 1;
      Object value = get(expression.substring(0, equal).trim(), context);
      String expected = expression.substring(equal + skip).trim();
      if (expected.length() > 1 && (expected.charAt(0) == '\'' || expected.charAt(0) == '"'))
        expected = expected.substring(1, expected.length() - 1);
      result = value != null && String.valueOf(value).equals(expected);
    } else {
      Object value = get(expression, context);
      result = value instanceof Boolean ? (Boolean) value
          : value instanceof Number     ? ((Number) value).doubleValue() != 0
                                        : value != null && !String.valueOf(value).isEmpty();
    }
    return negate ? !result : result;
  }

  private Binding path(String name) {
    if (name == null) return null;
    name = name.trim();
    if (name.startsWith("settings.")) name = name.substring(9);
    if (paths.containsKey(name)) return paths.get(name);
    if (!SETTING_PATH.matcher(name).matches() || "schemaVersion".equals(name)) return null;
    List<Field> fields = new ArrayList<Field>();
    Class<?> owner = Settings.class;
    try {
      String[] parts = name.split("\\.");
      for (int index = 0; index < parts.length; index++) {
        Field field = owner.getField(parts[index]);
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
          throw new IllegalArgumentException();
        owner = field.getType();
        fields.add(field);
        if (index < parts.length - 1 && !owner.getName().startsWith("dev.nightclient.config."))
          throw new IllegalArgumentException();
      }
      Binding binding = scalar(owner) ? new Binding(fields, owner) : null;
      paths.put(name, binding);
      return binding;
    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
      paths.put(name, null);
      return null;
    }
  }

  private static boolean scalar(Class<?> type) {
    return type == boolean.class || type == int.class || type == float.class || type == String.class
        ;
  }

  private static final class Binding {
    final Field[] fields;
    final Class<?> type;

    Binding(List<Field> fields, Class<?> type) {
      this.fields = fields.toArray(new Field[fields.size()]);
      this.type = type;
    }

    Object read(Settings settings) {
      try {
        Object owner = settings;
        for (Field field : fields) {
          if (owner == null) return null;
          owner = field.get(owner);
        }
        return owner;
      } catch (IllegalAccessException ignored) {
        return null;
      }
    }

    void write(Settings settings, Object value) {
      try {
        Object owner = settings;
        for (int i = 0; i < fields.length - 1; i++) owner = fields[i].get(owner);
        if (owner != null) fields[fields.length - 1].set(owner, value);
      } catch (IllegalAccessException ignored) {
        throw new IllegalArgumentException();
      }
    }
  }
}
