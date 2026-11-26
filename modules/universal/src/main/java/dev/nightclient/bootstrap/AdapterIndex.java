package dev.nightclient.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class AdapterIndex {
  static final String RESOURCE = "nightclient-universal.properties";
  static final String MIXIN_PACKAGE = "dev.nightclient.universal.mixin";
  static final String ADAPTER_PREFIX = "mc";
  private static final Map<String, Integer> SUPPORTED = supported();
  private static final Set<String> ADAPTER_IDS = adapterIds();
  private final Map<String, Adapter> adapters;

  private AdapterIndex(Map<String, Adapter> adapters) {
    this.adapters = Collections.unmodifiableMap(adapters);
  }

  static AdapterIndex read(InputStream stream) throws IOException {
    if (stream == null) throw invalid("Missing /" + RESOURCE);
    Properties properties = new Properties() {
      @Override
      public synchronized Object put(Object key, Object value) {
        if (containsKey(key)) throw invalid("Duplicate index key: " + key);
        return super.put(key, value);
      }
    };
    properties.load(stream);
    if (!"1".equals(required(properties, "schemaVersion")))
      throw invalid("Unsupported index schema");
    required(properties, "modVersion");
    Set<String> versions = new LinkedHashSet<String>(csv(required(properties, "versions")));
    if (!versions.equals(SUPPORTED.keySet()))
      throw invalid("Index must contain the exact 24 supported Minecraft releases");
    Map<String, Adapter> result = new LinkedHashMap<String, Adapter>();
    Set<String> allowedKeys =
        new LinkedHashSet<String>(Arrays.asList("schemaVersion", "modVersion", "versions"));
    for (Map.Entry<String, Integer> version : SUPPORTED.entrySet()) {
      String prefix = "version." + version.getKey() + ".";
      for (String field :
          Arrays.asList("adapter", "entrypoint", "mixinPackage", "mixins", "compatibilityLevel")) {
        allowedKeys.add(prefix + field);
      }
      String id = required(properties, prefix + "adapter");
      if (!ADAPTER_IDS.contains(id)) throw invalid("Invalid adapter id for " + version.getKey());
      String entrypoint = "dev.nightclient.adapter." + id + ".platform.NightClientMod";
      if (!entrypoint.equals(required(properties, prefix + "entrypoint")))
        throw invalid("Adapter entrypoint escapes its namespace");
      String mixinPackage = MIXIN_PACKAGE + "." + id;
      if (!mixinPackage.equals(required(properties, prefix + "mixinPackage")))
        throw invalid("Mixin package escapes its reserved namespace");
      String compatibility = required(properties, prefix + "compatibilityLevel");
      if (!("JAVA_" + version.getValue()).equals(compatibility))
        throw invalid("Incorrect Java compatibility for " + version.getKey());
      List<String> mixins = new ArrayList<String>();
      for (String name : csv(required(properties, prefix + "mixins"))) {
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*"))
          throw invalid("Mixin must be a simple class name: " + name);
        mixins.add(id + "." + name);
      }
      result.put(version.getKey(),
          new Adapter(version.getKey(), id, entrypoint, mixinPackage, compatibility,
              version.getValue(), mixins));
    }
    if (!allowedKeys.equals(properties.stringPropertyNames()))
      throw invalid("Unknown fields in adapter index");
    return new AdapterIndex(result);
  }

  Adapter select(String minecraft, int javaFeature) {
    Adapter adapter = adapters.get(minecraft);
    if (adapter == null)
      throw invalid("Unsupported Minecraft release '" + minecraft
          + "'; this build supports 1.20 through 26.3 stable releases only");
    if (javaFeature < adapter.javaFeature)
      throw invalid("Minecraft " + minecraft + " requires Java " + adapter.javaFeature
          + " or newer; running Java " + javaFeature);
    return adapter;
  }

  static Map<String, Integer> supportedVersions() {
    return SUPPORTED;
  }

  static Set<String> adapterIds() {
    Set<String> result = new LinkedHashSet<String>();
    for (String version : SUPPORTED.keySet())
      result.add(ADAPTER_PREFIX + version.replace('.', '_'));
    return Collections.unmodifiableSet(result);
  }

  static int javaFeature(String specification) {
    if (specification == null) throw invalid("Missing Java specification version");
    String feature = specification.startsWith("1.") ? specification.substring(2) : specification;
    if (!feature.matches("[0-9]+"))
      throw invalid("Invalid Java specification version: " + specification);
    try {
      int value = Integer.parseInt(feature);
      if (value < 17) throw invalid("Java 17 or newer is required");
      return value;
    } catch (NumberFormatException exception) {
      throw invalid("Invalid Java specification version: " + specification);
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.trim().isEmpty() || !value.equals(value.trim()))
      throw invalid("Missing or malformed index field: " + key);
    return value;
  }

  private static List<String> csv(String value) {
    List<String> result = new ArrayList<String>();
    Set<String> unique = new LinkedHashSet<String>();
    for (String part : value.split(",", -1)) {
      if (part.isEmpty() || !part.equals(part.trim()) || !unique.add(part))
        throw invalid("Empty, duplicate or whitespace-containing index list value");
      result.add(part);
    }
    return result;
  }

  private static IllegalStateException invalid(String message) {
    return new IllegalStateException("NightClient universal: " + message);
  }

  private static Map<String, Integer> supported() {
    Map<String, Integer> result = new LinkedHashMap<String, Integer>();
    for (String version : new String[] {"1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4"})
      result.put(version, 17);
    for (String version : new String[] {"1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3",
             "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11"})
      result.put(version, 21);
    for (String version : new String[] {"26.1", "26.1.1", "26.1.2", "26.2", "26.3"})
      result.put(version, 25);
    return Collections.unmodifiableMap(result);
  }

  static final class Adapter {
    final String minecraft;
    final String id;
    final String entrypoint;
    final String mixinPackage;
    final String compatibilityLevel;
    final int javaFeature;
    final List<String> mixins;

    Adapter(String minecraft, String id, String entrypoint, String mixinPackage,
        String compatibilityLevel, int javaFeature, List<String> mixins) {
      this.minecraft = minecraft;
      this.id = id;
      this.entrypoint = entrypoint;
      this.mixinPackage = mixinPackage;
      this.compatibilityLevel = compatibilityLevel;
      this.javaFeature = javaFeature;
      this.mixins = Collections.unmodifiableList(new ArrayList<String>(mixins));
    }
  }
}
