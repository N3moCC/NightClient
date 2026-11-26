package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompiledStyleMapTest {
  @Test
  void recipeIsASnapshotAndFramesOwnTheirValues() {
    Map<String, String> source = new LinkedHashMap<String, String>();
    source.put("width", "40px");
    source.put("color", "white");
    CompiledStyleMap.Plan recipe = new CompiledStyleMap.Plan(source);
    source.put("width", "900px");
    source.put("height", "900px");
    Map<String, String> first = recipe.create(), second = recipe.create();
    assertEquals("40px", first.put("width", "60px"));
    first.put("height", "20px");
    first.remove("color");
    assertEquals("40px", second.get("width"));
    assertEquals("white", second.get("color"));
    assertFalse(second.containsKey("height"));
    assertEquals(2, second.size());
    assertEquals(source.get("width"), "900px");
  }

  @Test
  void resetRestoresTheRecipeAndDiscardsPortalAndMountOverrides() {
    Map<String, String> recipe = new LinkedHashMap<String, String>();
    recipe.put("width", "40px");
    recipe.put("color", null);
    CompiledStyleMap first = (CompiledStyleMap) new CompiledStyleMap.Plan(recipe).create();
    first.put("width", "80px");
    first.remove("color");
    first.put("max-height", "100px");
    Iterator<Map.Entry<String, String>> old = first.entrySet().iterator();
    first.reset();
    assertEquals(recipe, first);
    assertThrows(ConcurrentModificationException.class, old::next);
    first.clear();
    first.put("padding", "4px");
    first.reset();
    assertEquals(recipe, first);
    first.put("color", "white");
    first.reset();
    assertTrue(first.containsKey("color"));
    assertNull(first.get("color"));
  }

  @Test
  void compactIndexHandlesEqualHashesAndNonInternedPropertyNames() {
    Map<String, String> recipe = new LinkedHashMap<String, String>();
    recipe.put("Aa", "one");
    recipe.put("BB", "two");
    recipe.put(null, "nil");
    CompiledStyleMap values = new CompiledStyleMap.Plan(recipe).create();
    assertEquals("one", values.get(new String("Aa")));
    assertEquals("two", values.get(new String("BB")));
    assertNull(values.get("AaAa"));
    values.remove(new String("Aa"));
    assertEquals("two", values.get("BB"));
    values.put("Aa", "again");
    assertEquals("again", values.get("Aa"));
    assertEquals("nil", values.get(null));
    values.reset();
    assertEquals(recipe, values);
  }

  @Test
  void nullableKnownAndUnknownPropertiesFollowHashMapSemantics() {
    Map<String, String> source = new LinkedHashMap<String, String>();
    source.put(null, "null-key");
    source.put("nil", null);
    source.put("color", "red");
    Map<String, String> actual =
        new CompiledStyleMap.Plan(Collections.unmodifiableMap(source)).create();
    Map<String, String> expected = new HashMap<String, String>(source);
    assertEquals(expected, actual);
    assertEquals(expected.hashCode(), actual.hashCode());
    assertNull(actual.getOrDefault("nil", "fallback"));
    assertEquals("fallback", actual.getOrDefault("absent", "fallback"));
    assertEquals(expected.put(null, null), actual.put(null, null));
    assertEquals(expected.put("unknown", null), actual.put("unknown", null));
    assertEquals(expected.remove("nil"), actual.remove("nil"));
    assertEquals(expected.put("nil", "returned"), actual.put("nil", "returned"));
    assertEquals(expected.remove("unknown"), actual.remove("unknown"));
    assertEquals(expected.remove(null), actual.remove(null));
    assertEquals(expected, actual);
    assertEquals(actual, expected);
    assertEquals(expected.containsValue(null), actual.containsValue(null));
    assertEquals(expected.put(null, "again"), actual.put(null, "again"));
    assertEquals(expected, actual);
    actual.clear();
    expected.clear();
    assertEquals(expected, actual);
    assertFalse(actual.containsValue(null));
    assertEquals(expected.put("color", null), actual.put("color", null));
    assertEquals(expected.put("new", "blue"), actual.put("new", "blue"));
    assertEquals(expected, actual);
    assertEquals(2, actual.size());
  }

  @Test
  void iterationOrderEntriesAndAllBackedViewsRemainMutable() {
    Map<String, String> source = new LinkedHashMap<String, String>();
    source.put("one", "1");
    source.put("two", "2");
    Map<String, String> map = new CompiledStyleMap.Plan(source).create();
    map.put("three", "3");
    map.put("four", null);
    assertEquals(Arrays.asList("one", "two", "three", "four"), new ArrayList<String>(map.keySet()));
    Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
    Map.Entry<String, String> first = iterator.next();
    assertEquals("1", first.setValue("11"));
    assertEquals("11", map.get("one"));
    assertEquals(new AbstractMap.SimpleEntry<String, String>("one", "11"), first);
    assertEquals("two", iterator.next().getKey());
    iterator.remove();
    assertEquals("three", iterator.next().getKey());
    iterator.remove();
    assertEquals("four", iterator.next().getKey());
    iterator.remove();
    assertFalse(iterator.hasNext());
    assertThrows(IllegalStateException.class, iterator::remove);
    assertEquals(Collections.singletonMap("one", "11"), map);
    assertFalse(
        map.entrySet().contains(new AbstractMap.SimpleEntry<String, String>("missing", null)));
    assertFalse(map.entrySet().remove(new AbstractMap.SimpleEntry<String, String>("one", "wrong")));
    assertTrue(map.entrySet().remove(new AbstractMap.SimpleEntry<String, String>("one", "11")));
    map.put("two", null);
    assertTrue(map.values().remove(null));
    assertTrue(map.isEmpty());
    map.put("new", "value");
    assertTrue(map.keySet().remove("new"));
    assertTrue(map.isEmpty());
  }

  @Test
  void bulkAndDefaultMapOperationsPreserveNullAndIteratorContracts() {
    Map<String, String> map =
        new CompiledStyleMap.Plan(Collections.singletonMap("width", "10px")).create();
    Map<String, String> overrides = new LinkedHashMap<String, String>();
    overrides.put("width", null);
    overrides.put("height", "20px");
    map.putAll(overrides);
    map.putAll(map);
    assertEquals(overrides, map);
    Map<String, String> copy = new LinkedHashMap<String, String>();
    map.forEach(copy::put);
    assertEquals(map, copy);
    assertEquals("12px", map.computeIfAbsent("width", key -> "12px"));
    map.replaceAll((key, value) -> value + "!");
    assertEquals("12px!", map.get("width"));
    assertEquals("20px!+", map.merge("height", "+", (left, right) -> left + right));
    Iterator<Map.Entry<String, String>> stable = map.entrySet().iterator();
    map.put("width", "changed");
    assertDoesNotThrow(stable::next);
    Iterator<Map.Entry<String, String>> invalid = map.entrySet().iterator();
    map.put("another", "new");
    assertThrows(ConcurrentModificationException.class, invalid::next);
    assertThrows(NullPointerException.class, () -> map.forEach(null));
    map.entrySet().clear();
    assertTrue(map.isEmpty());
    assertTrue(
        new CompiledStyleMap.Plan(Collections.<String, String>emptyMap()).create().isEmpty());
  }
}
