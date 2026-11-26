package dev.nightclient.ui.skin;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

final class PaintStyleMap extends AbstractMap<String, String> {
  static final class Plan {
    final Map<String, Integer> index = new HashMap<String, Integer>();
    final String[] keys, raw;
    final boolean[] bound;

    Plan(Map<String, String> source) {
      keys = new String[source.size()];
      raw = new String[keys.length];
      bound = new boolean[keys.length];
      int i = 0;
      for (Map.Entry<String, String> entry : source.entrySet()) {
        index.put(entry.getKey(), i);
        keys[i] = entry.getKey();
        raw[i] = entry.getValue();
        bound[i] = raw[i] != null && raw[i].contains("{{");
        i++;
      }
    }
  }

  private static final class Values {
    Plan plan;
    String[] resolved = new String[0];
    int[] epochs = new int[0];
    int epoch;

    void reset(Plan next) {
      plan = next;
      if (++epoch == 0) {
        java.util.Arrays.fill(epochs, 0);
        epoch = 1;
      }
      if (next != null && resolved.length < next.keys.length) {
        resolved = new String[next.keys.length];
        epochs = new int[next.keys.length];
      }
    }
  }

  private Map<String, String> base = Collections.emptyMap();
  private final Values node = new Values(), presentation = new Values();
  private SkinBindings bindings;
  private Map<String, Object> context;

  void reset(Map<String, String> base, Plan node, Plan presentation, SkinBindings bindings,
      Map<String, Object> context) {
    this.base = base;
    this.bindings = bindings;
    this.context = context;
    this.node.reset(node);
    this.presentation.reset(presentation);
  }

  @Override
  public boolean containsKey(Object key) {
    return presentation.plan != null && presentation.plan.index.containsKey(key)
        || node.plan != null && node.plan.index.containsKey(key) || base.containsKey(key);
  }

  @Override
  public String get(Object key) {
    if (presentation.plan != null) {
      Integer index = presentation.plan.index.get(key);
      if (index != null) return value(presentation, index);
    }
    if (node.plan != null) {
      Integer index = node.plan.index.get(key);
      if (index != null) return value(node, index);
    }
    return base.get(key);
  }

  private String value(Values values, int index) {
    if (!values.plan.bound[index]) return values.plan.raw[index];
    if (values.epochs[index] != values.epoch) {
      values.resolved[index] = bindings.text(values.plan.raw[index], context);
      values.epochs[index] = values.epoch;
    }
    return values.resolved[index];
  }

  @Override
  public Set<Map.Entry<String, String>> entrySet() {
    return new AbstractSet<Map.Entry<String, String>>() {
      private Set<String> keys() {
        Set<String> keys = new LinkedHashSet<String>(base.keySet());
        if (node.plan != null) Collections.addAll(keys, node.plan.keys);
        if (presentation.plan != null) Collections.addAll(keys, presentation.plan.keys);
        return keys;
      }

      @Override
      public int size() {
        return keys().size();
      }

      @Override
      public Iterator<Map.Entry<String, String>> iterator() {
        final Iterator<String> keys = keys().iterator();
        return new Iterator<Map.Entry<String, String>>() {
          public boolean hasNext() {
            return keys.hasNext();
          }

          public Map.Entry<String, String> next() {
            if (!keys.hasNext()) throw new NoSuchElementException();
            String key = keys.next();
            return new SimpleImmutableEntry<String, String>(key, get(key));
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }
}
