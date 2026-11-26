package dev.nightclient.skin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkinManager {
  public interface Validator {
    void validate(SkinDocument document) throws IOException;
  }

  private final Path directory;
  private final Validator validator;
  private SkinDocument document;
  private String error, requested = "skin.nc", loaded;

  public SkinManager(Path configurationDirectory) {
    this(configurationDirectory, null);
  }

  public SkinManager(Path configurationDirectory, Validator validator) {
    if (configurationDirectory == null)
      throw new IllegalArgumentException("Configuration directory is required");
    directory = configurationDirectory.toAbsolutePath().normalize();
    this.validator = validator;
  }

  public Path directory() {
    return directory;
  }

  public SkinDocument document() {
    return document;
  }

  public String error() {
    return error;
  }

  public String loadedFile() {
    return loaded;
  }

  public String status() {
    return error != null ? "error" : (document == null ? "empty" : "loaded");
  }

  public boolean reload() {
    return reload(requested);
  }

  public boolean reload(String filename) {
    requested = filename == null ? "skin.nc" : filename;
    try {
      Path path = resolve(requested);
      byte[] bytes;
      try (InputStream stream = Files.newInputStream(path)) {
        bytes = readBounded(stream);
      }
      SkinDocument next = NcParser.parse(bytes);
      if (validator != null) validator.validate(next);
      document = next;
      loaded = requested;
      error = null;
      return true;
    } catch (Exception failure) {
      error =
          failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
      return false;
    }
  }

  private Path resolve(String filename) throws IOException {
    if (!filename.matches("[a-zA-Z0-9_-]{1,64}\\.nc"))
      throw new IOException("Use a local .nc skin file");
    Path path = directory.resolve(filename).normalize();
    if (!path.startsWith(directory) || path.equals(directory))
      throw new IOException("Skin must be inside NightClient configuration");

    if (!path.toRealPath().startsWith(directory.toRealPath()))
      throw new IOException("Skin symlink leaves NightClient configuration");
    if (!Files.isRegularFile(path)) throw new IOException("Skin is not a regular file");
    if (Files.size(path) > NcParser.MAX_BYTES) throw new IOException("Skin exceeds 512 KiB");
    return path;
  }

  private static byte[] readBounded(InputStream stream) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int count;
    while ((count = stream.read(buffer)) >= 0) {
      if (count == 0) continue;
      if (out.size() + count > NcParser.MAX_BYTES) throw new IOException("Skin exceeds 512 KiB");
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }

  public List<Path> writeExamples() throws IOException {
    return writeExamples(null);
  }

  public List<Path> writeExamples(String preset) throws IOException {
    Files.createDirectories(directory);
    List<Path> created = new ArrayList<Path>();
    for (String name : new String[] {"skin.nc"}) {
      Path target = directory.resolve(name);
      if (Files.exists(target)) continue;
      try (InputStream stream = new java.io.ByteArrayInputStream(DefaultSkin.source(preset))) {
        try {
          Files.copy(stream, target);
          created.add(target);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
        }
      }
    }
    return Collections.unmodifiableList(created);
  }
}
