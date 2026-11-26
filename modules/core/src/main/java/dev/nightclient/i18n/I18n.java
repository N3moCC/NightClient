package dev.nightclient.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class I18n {
  private static final String ROOT = "/assets/nightclient/i18n/";
  private static final String FALLBACK = "en";
  private static final Map<String, Map<String, String>> CATALOGS = loadCatalogs();
  private static final Map<String, Map<String, Object>> CONTEXTS = loadContexts();

  private I18n() {}

  public static List<String> languages() {
    return Collections.unmodifiableList(new ArrayList<String>(CATALOGS.keySet()));
  }

  public static String language(String requested) {
    if (requested == null) return FALLBACK;
    String language = requested.toLowerCase(Locale.ROOT).replace('_', '-');
    if (CATALOGS.containsKey(language)) return language;
    int separator = language.indexOf('-');
    if (separator > 0 && CATALOGS.containsKey(language.substring(0, separator)))
      return language.substring(0, separator);
    return FALLBACK;
  }

  public static String text(String language, String key) {
    if (key == null) return "";
    String value = catalog(language).get(key);
    return value == null ? key : value;
  }

  public static Map<String, String> catalog(String language) {
    return CATALOGS.get(language(language));
  }

  public static Map<String, Object> context(String language) {
    return CONTEXTS.get(language(language));
  }

  public static String format(String language, String key, Map<String, ?> arguments) {
    String template = text(language, key);
    if (arguments == null || arguments.isEmpty()) return template;
    StringBuilder output = new StringBuilder();
    int at = 0;
    while (at < template.length() && output.length() < 8192) {
      int open = template.indexOf('{', at);
      if (open < 0) {
        append(output, template.substring(at));
        break;
      }
      append(output, template.substring(at, open));
      int close = template.indexOf('}', open + 1);
      if (close < 0) {
        append(output, template.substring(open));
        break;
      }
      String name = template.substring(open + 1, close);
      Object value = arguments.get(name);
      append(output, value == null ? template.substring(open, close + 1) : String.valueOf(value));
      at = close + 1;
    }
    return output.toString();
  }

  private static void append(StringBuilder output, String value) {
    output.append(value, 0, Math.min(value.length(), 8192 - output.length()));
  }

  public static Map<String, String> parseCatalog(String json) {
    return decodeCatalog(JsonResources.parse(json));
  }

  private static Map<String, String> decodeCatalog(JsonElement tree) {
    if (!tree.isJsonObject())
      throw new IllegalArgumentException("Language catalog must be an object");
    Map<String, String> strings = new LinkedHashMap<String, String>();
    JsonObject object = tree.getAsJsonObject();
    if (object.entrySet().size() > 4096)
      throw new IllegalArgumentException("Too many translation keys");
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String key = entry.getKey();
      JsonElement value = entry.getValue();
      if (key.length() > 128 || !key.matches("[a-zA-Z][a-zA-Z0-9_-]*(?:\\.[a-zA-Z][a-zA-Z0-9_-]*)*")
          || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        throw new IllegalArgumentException("Invalid translation entry: " + key);
      String text = value.getAsString();
      if (text.length() > 2048)
        throw new IllegalArgumentException("Translation is too long: " + key);
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (Character.isISOControl(c) && c != '\n' && c != '\t')
          throw new IllegalArgumentException("Control character in translation: " + key);
        if (Character.isHighSurrogate(c)) {
          if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
            throw new IllegalArgumentException("Invalid translation Unicode: " + key);
        } else if (Character.isLowSurrogate(c))
          throw new IllegalArgumentException("Invalid translation Unicode: " + key);
      }
      strings.put(key, text);
    }

    for (String key : strings.keySet())
      for (int dot = key.indexOf('.'); dot >= 0; dot = key.indexOf('.', dot + 1))
        if (strings.containsKey(key.substring(0, dot)))
          throw new IllegalArgumentException("Translation namespace collision: " + key);
    return Collections.unmodifiableMap(strings);
  }

  private static Map<String, Map<String, String>> loadCatalogs() {
    JsonElement index = JsonResources.read(ROOT + "languages.json");
    if (!index.isJsonArray() || index.getAsJsonArray().size() > 32)
      throw new IllegalStateException("Invalid language index");
    Map<String, Map<String, String>> catalogs = new LinkedHashMap<String, Map<String, String>>();
    for (JsonElement value : index.getAsJsonArray()) {
      if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
          || !value.getAsString().matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})?"))
        throw new IllegalStateException("Invalid language identifier");
      String id = value.getAsString();
      if (catalogs.containsKey(id))
        throw new IllegalStateException("Duplicate language identifier: " + id);
      catalogs.put(id, decodeCatalog(JsonResources.read(ROOT + id + ".json")));
    }
    Map<String, String> fallback = catalogs.get(FALLBACK);
    if (fallback == null) throw new IllegalStateException("Missing fallback language");
    Map<String, Map<String, String>> merged = new LinkedHashMap<String, Map<String, String>>();
    for (Map.Entry<String, Map<String, String>> entry : catalogs.entrySet()) {
      Map<String, String> strings = new LinkedHashMap<String, String>(fallback);
      strings.putAll(entry.getValue());

      for (String key : strings.keySet())
        for (int dot = key.indexOf('.'); dot >= 0; dot = key.indexOf('.', dot + 1))
          if (strings.containsKey(key.substring(0, dot)))
            throw new IllegalStateException("Merged translation namespace collision: " + key);
      merged.put(entry.getKey(), Collections.unmodifiableMap(strings));
    }
    return Collections.unmodifiableMap(merged);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> loadContexts() {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    for (Map.Entry<String, Map<String, String>> language : CATALOGS.entrySet()) {
      Map<String, Object> root = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, String> entry : language.getValue().entrySet()) {
        String[] parts = entry.getKey().split("\\.");
        Map<String, Object> owner = root;
        for (int i = 0; i < parts.length - 1; i++) {
          if (!owner.containsKey(parts[i]))
            owner.put(parts[i], new LinkedHashMap<String, Object>());
          owner = (Map<String, Object>) owner.get(parts[i]);
        }
        owner.put(parts[parts.length - 1], entry.getValue());
      }
      result.put(language.getKey(), freeze(root));
    }
    return Collections.unmodifiableMap(result);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> freeze(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : source.entrySet())
      result.put(entry.getKey(),
          entry.getValue() instanceof Map ? freeze((Map<String, Object>) entry.getValue())
                                          : entry.getValue());
    return Collections.unmodifiableMap(result);
  }
}
