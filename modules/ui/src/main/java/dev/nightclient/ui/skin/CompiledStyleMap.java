package dev.nightclient.ui.skin;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

final class CompiledStyleMap extends AbstractMap<String, String> {
  static final class Plan {
    private final String[] keys;
    private final String[] defaults;
    private final Map<String, Integer> index;
    private final int[] slots, hashes;
    private final int mask;

    Plan(Map<String, String> source) {
      Objects.requireNonNull(source, "source");
      keys = source.keySet().toArray(new String[source.size()]);
      defaults = new String[keys.length];
      hashes = new int[keys.length];
      index = new HashMap<String, Integer>(Math.max(1, keys.length * 4 / 3 + 1));
      int capacity = 2;
      while (capacity < keys.length * 4) capacity <<= 1;
      slots = new int[capacity];
      mask = capacity - 1;
      for (int i = 0; i < keys.length; i++) {
        index.put(keys[i], i);
        defaults[i] = source.get(keys[i]);
        if (keys[i] != null) {
          int hash = keys[i].hashCode(), at = (hash ^ (hash >>> 16)) & mask;
          hashes[i] = hash;
          while (slots[at] != 0) at = (at + 1) & mask;
          slots[at] = i + 1;
        }
      }
    }

    private int find(Object key) {
      if (!(key instanceof String)) {
        Integer value = index.get(key);
        return value == null ? -1 : value;
      }
      int hash = key.hashCode(), at = (hash ^ (hash >>> 16)) & mask;
      for (int value; (value = slots[at]) != 0; at = (at + 1) & mask) {
        int slot = value - 1;
        if (hashes[slot] == hash && (key == keys[slot] || key.equals(keys[slot]))) return slot;
      }
      return -1;
    }

    CompiledStyleMap create() {
      return new CompiledStyleMap(this);
    }
  }

  private final Plan plan;
  private final String[] values;
  private boolean[] removed;
  private Map<String, String> extra;
  private Set<Map.Entry<String, String>> entries;
  private int size;
  private int modifications;

  private CompiledStyleMap(Plan plan) {
    this.plan = plan;
    values = plan.defaults.clone();
    size = values.length;
  }

  void reset() {
    System.arraycopy(plan.defaults, 0, values, 0, values.length);
    removed = null;
    if (extra != null) extra.clear();
    size = values.length;
    modifications++;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean containsKey(Object key) {
    int index = plan.find(key);
    return index >= 0 ? present(index) : extra != null && extra.containsKey(key);
  }

  @Override
  public String get(Object key) {
    int index = plan.find(key);
    return index >= 0   ? (present(index) ? values[index] : null)
        : extra == null ? null
                        : extra.get(key);
  }

  @Override
  public String getOrDefault(Object key, String defaultValue) {
    int index = plan.find(key);
    if (index >= 0) return present(index) ? values[index] : defaultValue;
    return extra == null ? defaultValue : extra.getOrDefault(key, defaultValue);
  }

  @Override
  public boolean containsValue(Object value) {
    for (int i = 0; i < values.length; i++)
      if (present(i) && Objects.equals(value, values[i])) return true;
    return extra != null && extra.containsValue(value);
  }

  @Override
  public String put(String key, String value) {
    int index = plan.find(key);
    if (index >= 0) {
      String previous = present(index) ? values[index] : null;
      if (!present(index)) {
        removed[index] = false;
        size++;
        modifications++;
      }
      values[index] = value;
      return previous;
    }
    if (extra == null) extra = new LinkedHashMap<String, String>();
    if (!extra.containsKey(key)) {
      size++;
      modifications++;
    }
    return extra.put(key, value);
  }

  @Override
  public void putAll(Map<? extends String, ? extends String> source) {
    if (source == this) return;
    source.forEach(this::put);
  }

  @Override
  public String remove(Object key) {
    int index = plan.find(key);
    if (index >= 0) {
      if (!present(index)) return null;
      String previous = values[index];
      removeIndex(index);
      return previous;
    }
    if (extra == null || !extra.containsKey(key)) return null;
    String previous = extra.remove(key);
    size--;
    modifications++;
    return previous;
  }

  @Override
  public void clear() {
    if (size == 0) return;
    if (values.length > 0) {
      if (removed == null) removed = new boolean[values.length];
      Arrays.fill(removed, true);
      Arrays.fill(values, null);
    }
    if (extra != null) extra.clear();
    size = 0;
    modifications++;
  }

  @Override
  public void forEach(BiConsumer<? super String, ? super String> action) {
    Objects.requireNonNull(action, "action");
    int expected = modifications;
    for (int i = 0; i < values.length; i++)
      if (present(i)) action.accept(plan.keys[i], values[i]);
    if (extra != null) extra.forEach(action);
    if (expected != modifications) throw new ConcurrentModificationException();
  }

  @Override
  public Set<Map.Entry<String, String>> entrySet() {
    if (entries == null) entries = new Entries();
    return entries;
  }

  private boolean present(int index) {
    return removed == null || !removed[index];
  }

  private void removeIndex(int index) {
    if (removed == null) removed = new boolean[values.length];
    removed[index] = true;
    values[index] = null;
    size--;
    modifications++;
  }

  private final class Entries extends AbstractSet<Map.Entry<String, String>> {
    @Override
    public int size() {
      return CompiledStyleMap.this.size;
    }

    @Override
    public void clear() {
      CompiledStyleMap.this.clear();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return new EntriesIterator();
    }

    @Override
    public boolean contains(Object candidate) {
      if (!(candidate instanceof Map.Entry)) return false;
      Map.Entry<?, ?> entry = (Map.Entry<?, ?>) candidate;
      return containsKey(entry.getKey()) && Objects.equals(get(entry.getKey()), entry.getValue());
    }

    @Override
    public boolean remove(Object candidate) {
      if (!contains(candidate)) return false;
      CompiledStyleMap.this.remove(((Map.Entry<?, ?>) candidate).getKey());
      return true;
    }
  }

  private final class EntriesIterator implements Iterator<Map.Entry<String, String>> {
    private int expected = modifications;
    private int cursor;
    private int lastIndex = -1;
    private boolean canRemove, lastExtra;
    private Iterator<Map.Entry<String, String>> extraIterator;

    @Override
    public boolean hasNext() {
      checkModification();
      while (cursor < values.length && !present(cursor)) cursor++;
      if (cursor < values.length) return true;
      if (extraIterator == null && extra != null) extraIterator = extra.entrySet().iterator();
      return extraIterator != null && extraIterator.hasNext();
    }

    @Override
    public Map.Entry<String, String> next() {
      if (!hasNext()) throw new NoSuchElementException();
      String key;
      if (cursor < values.length) {
        lastIndex = cursor++;
        lastExtra = false;
        key = plan.keys[lastIndex];
      } else {
        lastExtra = true;
        key = extraIterator.next().getKey();
      }
      canRemove = true;
      return new Entry(key);
    }

    @Override
    public void remove() {
      checkModification();
      if (!canRemove) throw new IllegalStateException();
      if (lastExtra) {
        extraIterator.remove();
        size--;
        modifications++;
      } else
        removeIndex(lastIndex);
      expected = modifications;
      canRemove = false;
    }

    private void checkModification() {
      if (expected != modifications) throw new ConcurrentModificationException();
    }
  }

  private final class Entry implements Map.Entry<String, String> {
    private final String key;

    Entry(String key) {
      this.key = key;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public String getValue() {
      return get(key);
    }

    @Override
    public String setValue(String value) {
      return put(key, value);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Map.Entry)) return false;
      Map.Entry<?, ?> entry = (Map.Entry<?, ?>) other;
      return Objects.equals(key, entry.getKey()) && Objects.equals(getValue(), entry.getValue());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(key) ^ Objects.hashCode(getValue());
    }

    @Override
    public String toString() {
      return key + "=" + getValue();
    }
  }
}
