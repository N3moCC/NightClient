package dev.nightclient.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nightclient.config.Theme;
import dev.nightclient.i18n.JsonResources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DefaultSkin {
  private static final String ROOT = "/assets/nightclient/skins/";
  private static final String[] FILES = {
      "theme-defaults.nc", "default.nc", "hud.nc", "controls.nc"};
  private static final String EMERGENCY_SOURCE = "menu { text \"NightClient\" }\n";
  private static final IdentityHashMap<SkinDocument, IdentityHashMap<SkinDocument, SkinDocument>>
      COMPOSED = new IdentityHashMap<SkinDocument, IdentityHashMap<SkinDocument, SkinDocument>>();

  private DefaultSkin() {}

  private static final class Registry {
    static final Registry INSTANCE = new Registry();
    final Map<String, Preset> presets;
    final String defaultId, options, labels;

    Registry() {
      JsonElement data = JsonResources.read(ROOT + "presets/index.json");
      if (!data.isJsonObject())
        throw new IllegalStateException("Bundled theme registry must be an object");
      JsonObject root = data.getAsJsonObject();
      defaultId = string(root, "default");
      JsonElement entries = root.get("themes");
      if (entries == null || !entries.isJsonArray() || entries.getAsJsonArray().size() == 0
          || entries.getAsJsonArray().size() > 32)
        throw new IllegalStateException("Bundled theme registry requires 1..32 themes");
      Map<String, Preset> next = new LinkedHashMap<String, Preset>();
      for (JsonElement entry : entries.getAsJsonArray()) {
        if (!entry.isJsonObject())
          throw new IllegalStateException("Invalid bundled theme descriptor");
        JsonObject object = entry.getAsJsonObject();
        String id = string(object, "id"), label = string(object, "label"),
               directory = string(object, "directory");
        if (!id.matches("[a-z][a-z0-9-]{0,63}") || label.isEmpty() || label.length() > 64
            || label.indexOf('|') >= 0 || !directory.matches("(?:[a-z0-9_-]+/)*"))
          throw new IllegalStateException("Invalid bundled theme descriptor: " + id);
        if (next.put(id, new Preset(label, ROOT + directory)) != null)
          throw new IllegalStateException("Duplicate bundled theme: " + id);
      }
      if (!next.containsKey(defaultId))
        throw new IllegalStateException("Default bundled theme is missing");
      Set<String> broken = new LinkedHashSet<String>();
      for (Map.Entry<String, Preset> entry : next.entrySet()) {
        Preset preset = entry.getValue();
        try {
          preset.source();
          preset.document();
        } catch (RuntimeException error) {
          broken.add(entry.getKey());
          if (entry.getKey().equals(defaultId))
            System.err.println("[NightClient] Bundled theme '" + defaultId
                + "' is broken and was kept as the default theme: " + reason(error));
          else
            System.err.println("[NightClient] Bundled theme '" + entry.getKey()
                + "' is broken and was skipped: " + reason(error));
        }
      }
      for (String id : broken)
        if (!id.equals(defaultId)) next.remove(id);
      presets = Collections.unmodifiableMap(next);
      StringBuilder ids = new StringBuilder(), names = new StringBuilder();
      for (Map.Entry<String, Preset> entry : next.entrySet()) {
        if (broken.contains(entry.getKey())) continue;
        if (ids.length() > 0) {
          ids.append('|');
          names.append('|');
        }
        ids.append(entry.getKey());
        names.append(entry.getValue().label);
      }
      options = ids.toString();
      labels = names.toString();
    }

    private static String string(JsonObject object, String key) {
      JsonElement value = object.get(key);
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        throw new IllegalStateException("Bundled theme descriptor requires a string: " + key);
      return value.getAsString();
    }

    SkinDocument workingDocument(String id) {
      Preset preset = presets.get(id);
      if (preset == null) return null;
      try {
        return preset.document();
      } catch (RuntimeException error) {
        return null;
      }
    }

    byte[] workingSource(String id) {
      Preset preset = presets.get(id);
      if (preset == null) return null;
      try {
        return preset.source();
      } catch (RuntimeException error) {
        return null;
      }
    }

    SkinDocument firstWorkingDocument() {
      for (String id : presets.keySet()) {
        SkinDocument document = workingDocument(id);
        if (document != null) return document;
      }
      return null;
    }

    byte[] firstWorkingSource() {
      for (String id : presets.keySet()) {
        byte[] source = workingSource(id);
        if (source != null) return source;
      }
      return null;
    }

    String workingId(String preset) {
      if (preset != null && !preset.isEmpty() && presets.containsKey(preset)
          && workingDocument(preset) != null)
        return preset;
      if (workingDocument(defaultId) != null) return defaultId;
      for (String id : presets.keySet())
        if (workingDocument(id) != null) return id;
      return null;
    }
  }

  private static final class Preset {
    final String label, prefix;
    private byte[] source;
    private SkinDocument document;
    private RuntimeException failure;

    Preset(String label, String prefix) {
      this.label = label;
      this.prefix = prefix;
    }

    synchronized byte[] source() {
      if (failure != null) throw failure;
      if (source == null) {
        try {
          source = loadSource(prefix, FILES);
        } catch (RuntimeException error) {
          failure = error;
          throw error;
        }
      }
      return source;
    }

    synchronized SkinDocument document() {
      if (failure != null) throw failure;
      if (document == null) {
        try {
          document = compile(source(), label);
        } catch (RuntimeException error) {
          failure = error;
          throw error;
        }
      }
      return document;
    }
  }

  private static byte[] loadSource(String prefix, String[] files) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      for (String name : files)
        try (InputStream input = DefaultSkin.class.getResourceAsStream(prefix + name)) {
          if (input == null)
            throw new IOException("Missing bundled NC document: " + prefix + name);
          byte[] block = new byte[4096];
          for (int read; (read = input.read(block)) >= 0;) bytes.write(block, 0, read);
          bytes.write('\n');
        }
      if (bytes.size() > NcParser.MAX_BYTES)
        throw new IOException("Bundled NC document exceeds 512 KiB: " + prefix);
      return bytes.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("Bundled NC interface is unavailable", error);
    }
  }

  private static SkinDocument compile(byte[] source, String label) {
    try {
      return NcParser.parse(source);
    } catch (IOException error) {
      throw new IllegalStateException("Bundled " + label + " NC interface is invalid", error);
    }
  }

  private static String reason(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
    String message = cause.getMessage();
    if (message == null || message.isEmpty()) message = cause.getClass().getSimpleName();
    return message;
  }

  public static SkinDocument document() {
    return document(null);
  }

  public static String presetOptions() {
    return Registry.INSTANCE.options;
  }

  public static String presetLabels() {
    return Registry.INSTANCE.labels;
  }

  public static String[] presetIds() {
    Registry registry = Registry.INSTANCE;
    return registry.presets.keySet().toArray(new String[registry.presets.size()]);
  }

  public static String canonicalPreset(String preset) {
    Registry registry = Registry.INSTANCE;
    if (preset == null || preset.isEmpty()) return registry.defaultId;
    if (registry.presets.containsKey(preset)) return preset;
    throw new IllegalArgumentException("Unknown bundled NC theme: " + preset);
  }

  public static SkinDocument document(String preset) {
    return Registry.INSTANCE.presets.get(canonicalPreset(preset)).document();
  }

  public static void applyDefaults(String preset, Theme target) {
    if (target == null) throw new IllegalArgumentException("Theme settings are required");
    document(preset).applyDefaults(target);
  }

  public static byte[] source() {
    return source(null);
  }

  public static byte[] source(String preset) {
    return Registry.INSTANCE.presets.get(canonicalPreset(preset)).source().clone();
  }

  public static SkinDocument documentSafe(String preset) {
    Registry registry = Registry.INSTANCE;
    String id = registry.workingId(preset);
    SkinDocument document = id == null ? null : registry.workingDocument(id);
    if (document == null) document = registry.firstWorkingDocument();
    if (document == null) document = emergencyDocument();
    return document;
  }

  public static byte[] sourceSafe(String preset) {
    Registry registry = Registry.INSTANCE;
    String id = registry.workingId(preset);
    byte[] source = id == null ? null : registry.workingSource(id);
    if (source == null) source = registry.firstWorkingSource();
    if (source == null) source = emergencySource();
    return source.clone();
  }

  public static void applyDefaultsSafe(String preset, Theme target) {
    if (target == null) {
      System.err.println("[NightClient] Cannot apply bundled theme defaults: no theme settings");
      return;
    }
    try {
      documentSafe(preset).applyDefaults(target);
    } catch (RuntimeException error) {
      System.err.println("[NightClient] Cannot apply bundled theme defaults for '" + preset
          + "': " + reason(error));
    }
  }

  private static volatile SkinDocument emergency;

  private static SkinDocument emergencyDocument() {
    SkinDocument document = emergency;
    if (document != null) return document;
    try {
      document =
          NcParser.parse(EMERGENCY_SOURCE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new IllegalStateException("No usable bundled NC theme is available", error);
    }
    emergency = document;
    return document;
  }

  private static byte[] emergencySource() {
    return EMERGENCY_SOURCE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  public static synchronized SkinDocument resolve(SkinDocument custom) {
    return resolve(null, custom);
  }

  public static synchronized SkinDocument resolve(String preset, SkinDocument custom) {
    SkinDocument base = documentSafe(preset);
    if (custom == null || custom == base) return base;
    IdentityHashMap<SkinDocument, SkinDocument> layers = COMPOSED.get(base);
    if (layers == null) {
      layers = new IdentityHashMap<SkinDocument, SkinDocument>();
      COMPOSED.put(base, layers);
    }
    SkinDocument result = layers.get(custom);
    if (result != null) return result;
    try {
      result = SkinDocument.layer(base, custom);
    } catch (IOException error) {
      throw new IllegalArgumentException("Cannot apply NC interface", error);
    }
    if (layers.size() >= 16) layers.clear();
    layers.put(custom, result);
    layers.put(result, result);
    return result;
  }
}
