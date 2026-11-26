package dev.nightclient.i18n;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JsonResources {
  private static final int MAX_BYTES = 512 * 1024;

  private JsonResources() {}

  public static JsonElement read(String resource) {
    if (resource == null || !resource.matches("/assets/nightclient/[a-zA-Z0-9_./-]+\\.json")
        || resource.contains(".."))
      throw new IllegalArgumentException("Invalid resource path");
    try (InputStream input = JsonResources.class.getResourceAsStream(resource)) {
      if (input == null) throw new IOException("Missing presentation resource: " + resource);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      for (int count; (count = input.read(buffer)) >= 0;) {
        if (bytes.size() + count > MAX_BYTES)
          throw new IOException("Presentation resource is too large");
        bytes.write(buffer, 0, count);
      }
      return parse(utf8(bytes.toByteArray()));
    } catch (IOException error) {
      throw new IllegalStateException("Cannot load presentation resource: " + resource, error);
    }
  }

  public static JsonElement parse(String json) {
    if (json == null || json.length() > MAX_BYTES
        || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IllegalArgumentException("Presentation document is too large");
    checkStructure(json);
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      JsonElement result = new Gson().getAdapter(JsonElement.class).read(reader);
      if (result == null || reader.peek() != JsonToken.END_DOCUMENT)
        throw new IllegalArgumentException("Trailing presentation content");
      return result;
    } catch (IOException | RuntimeException error) {
      throw new IllegalArgumentException("Invalid presentation JSON", error);
    }
  }

  private static String utf8(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }

  private static void checkStructure(String json) {
    List<Set<String>> scopes = new ArrayList<Set<String>>();
    int values = 0;
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      while (reader.peek() != JsonToken.END_DOCUMENT) {
        if (++values > 32768) throw new IllegalArgumentException("Too many presentation values");
        switch (reader.peek()) {
          case BEGIN_OBJECT:
            reader.beginObject();
            scopes.add(new HashSet<String>());
            break;
          case BEGIN_ARRAY:
            reader.beginArray();
            scopes.add(null);
            break;
          case END_OBJECT:
            reader.endObject();
            scopes.remove(scopes.size() - 1);
            break;
          case END_ARRAY:
            reader.endArray();
            scopes.remove(scopes.size() - 1);
            break;
          case NAME:
            String name = reader.nextName();
            if (name.length() > 160 || !scopes.get(scopes.size() - 1).add(name))
              throw new IllegalArgumentException("Invalid or duplicate presentation key: " + name);
            break;
          case BOOLEAN:
            reader.nextBoolean();
            break;
          case NULL:
            reader.nextNull();
            break;
          case STRING:
          case NUMBER:
            if (reader.nextString().length() > 4096)
              throw new IllegalArgumentException("Presentation value is too long");
            break;
          default:
            throw new IllegalArgumentException("Invalid presentation structure");
        }
        if (scopes.size() > 16)
          throw new IllegalArgumentException("Presentation nesting is too deep");
      }
    } catch (IOException | RuntimeException error) {
      throw new IllegalArgumentException("Invalid presentation JSON", error);
    }
  }
}
