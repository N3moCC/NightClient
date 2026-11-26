package dev.nightclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.nightclient.skin.DefaultSkin;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ConfigStore implements AutoCloseable {
  private static final int MAX_IMPORT_BYTES = 256 * 1024;
  private static final int MAX_LOCAL_BYTES = 2 * 1024 * 1024;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private final Path directory;
  private final Path configFile;
  private final ScheduledExecutorService writer;
  private final Object ioLock = new Object();
  private ScheduledFuture<?> pending;
  private volatile String status = "saved";
  private volatile String latest;
  private boolean closed;

  public ConfigStore(Path configDirectory) {
    directory = configDirectory.resolve("nightclient");
    configFile = directory.resolve("settings.json");
    writer = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "NightClient config");
      t.setDaemon(true);
      return t;
    });
  }

  public Settings load() {
    if (!Files.isRegularFile(configFile)) return new Settings();
    try {
      return decode(read(configFile), MAX_LOCAL_BYTES);
    } catch (Exception error) {
      status = "recovered";
      try {
        Files.move(configFile,
            directory.resolve("settings.broken-" + System.currentTimeMillis() + ".json"));
      } catch (IOException ignored) {
        status = "read-error";
      }
      return new Settings();
    }
  }

  public Settings decode(String json) {
    return decode(json, MAX_IMPORT_BYTES);
  }

  private Settings decode(String json, int maxBytes) {
    if (json == null || json.length() > maxBytes
        || json.getBytes(StandardCharsets.UTF_8).length > maxBytes)
      throw new IllegalArgumentException("Profile is too large");
    JsonElement tree;
    checkStructure(json);
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      tree = gson.getAdapter(JsonElement.class).read(reader);
      if (reader.peek() != JsonToken.END_DOCUMENT)
        throw new IllegalArgumentException("Trailing profile content");
    } catch (IOException | RuntimeException ex) {
      throw new IllegalArgumentException("Invalid JSON", ex);
    }
    if (!tree.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object");
    JsonObject object = tree.getAsJsonObject();
    if (object.has("schemaVersion")) {
      JsonElement version = object.get("schemaVersion");
      if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
          || version.getAsBigDecimal().compareTo(BigDecimal.ONE) != 0)
        throw new IllegalArgumentException("Unsupported profile version");
    }
    Settings s;
    try {
      s = gson.fromJson(object, Settings.class);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid settings values", ex);
    }
    if (s == null) throw new IllegalArgumentException("Empty profile");
    migrateTweaksy(object, s);
    migrateNoRender(object, s);
    migrateAtmosphere(object, s, gson);

    if (s.itemScroller != null && object.has("itemScroller")
        && object.get("itemScroller").isJsonObject()
        && !object.getAsJsonObject("itemScroller").has("gesture")) {
      s.itemScroller.wheel = false;
      s.itemScroller.dragMatching = true;
    }

    if (object.has("crosshair") && object.get("crosshair").isJsonObject()
        && !object.getAsJsonObject("crosshair").has("mode") && s.crosshair != null) {
      s.crosshair.mode = "shape";
      s.crosshair.colorMode = "custom";
      if (s.crosshair.inheritColor && s.theme != null) s.crosshair.color = s.theme.accent;
      s.crosshair.inheritColor = false;
    }
    boolean retiredPreset = false;
    if (s.skin != null) try {
        DefaultSkin.canonicalPreset(s.skin.preset);
      } catch (IllegalArgumentException ex) {
        retiredPreset = true;
      }
    migrateThemeModes(s, object);
    migrateLayout(object, s);
    s.validate();

    if (retiredPreset || !object.has("theme")) {
      if (retiredPreset) {
        s.theme.mode = "dark";
        s.theme.customAccentEnabled = false;
        s.theme.applySavedMode();
      }
      DefaultSkin.applyDefaultsSafe(s.skin.preset, s.theme);
      s.validate();
    }
    if (retiredPreset) {
      Settings.Menu defaults = new Settings.Menu();
      s.menu.width = defaults.width;
      s.menu.height = defaults.height;
      s.menu.scale = defaults.scale;
      s.menu.hudScale = defaults.hudScale;
    }
    restoreAppearance(s, object);
    s.theme.applySavedMode();
    s.skin.appearance.put(s.skin.preset, s.theme.copy());
    s.skin.appearanceRevision = 3;
    return s;
  }

  private static void migrateTweaksy(JsonObject object, Settings s) {
    if (s == null) return;
    if (s.tweaksy == null) s.tweaksy = new Settings.Tweaksy();
    JsonObject tweaksyObj = object(object, "tweaksy");
    if (tweaksyObj == null || object(tweaksyObj, "cursorFix") == null) {
      JsonObject oldCursor = object(object, "cursorFix");
      Boolean cursorEnabled = bool(oldCursor, "enabled");
      if (cursorEnabled != null) {
        if (s.tweaksy.cursorFix == null) s.tweaksy.cursorFix = new Settings.CursorFix();
        s.tweaksy.cursorFix.enabled = cursorEnabled;
      }
    }
    if (tweaksyObj != null) return;
    JsonObject old = object(object, "commandfix");
    Boolean enabled = bool(old, "enabled");
    if (enabled != null) s.tweaksy.commandFix.enabled = enabled;
    old = object(object, "fullBright");
    enabled = bool(old, "enabled");
    if (enabled != null) s.tweaksy.fullBright.enabled = enabled;
    if (old != null) {
      JsonElement strength = old.get("strength");
      if (strength != null && strength.isJsonPrimitive()
          && strength.getAsJsonPrimitive().isNumber())
        s.tweaksy.fullBright.strength = strength.getAsFloat();
    }
  }

  private static void migrateNoRender(JsonObject object, Settings s) {
    if (s == null || object(object, "noRender") != null) return;
    if (s.noRender == null) s.noRender = new Settings.NoRender();

    JsonObject oldLowFire = object(object, "lowFire");
    Boolean lowFireEnabled = bool(oldLowFire, "enabled");
    JsonObject oldFluid = object(object, "noFluid");
    JsonObject tweaksyObj = object(object, "tweaksy");
    Boolean tweaksyEnabled = tweaksyObj != null ? bool(tweaksyObj, "enabled") : null;
    boolean tweaksyActive = !Boolean.FALSE.equals(tweaksyEnabled);
    JsonObject tHurt = tweaksyObj != null ? object(tweaksyObj, "noHurtCam") : null;
    JsonObject tFluid = tweaksyObj != null ? object(tweaksyObj, "noFluid") : null;

    boolean hasFluidMigration = Boolean.TRUE.equals(bool(oldFluid, "enabled"))
        || (tweaksyActive && tFluid != null && Boolean.TRUE.equals(bool(tFluid, "enabled")));
    boolean hasHurtMigration =
        tweaksyActive && tHurt != null && Boolean.TRUE.equals(bool(tHurt, "enabled"));
    boolean hasLowFireMigration = Boolean.TRUE.equals(lowFireEnabled);

    if (!hasFluidMigration && !hasHurtMigration && !hasLowFireMigration) return;

    s.noRender.enabled = true;
    if (!hasLowFireMigration) {
      s.noRender.fire = false;
      s.noRender.blockOverlay = false;
      s.noRender.pumpkin = false;
      s.noRender.powderSnow = false;
      s.noRender.nausea = false;
      s.noRender.hurtCam = false;
      s.noRender.waterOverlay = false;
      s.noRender.waterFog = false;
      s.noRender.lavaFog = false;
    }

    if (hasLowFireMigration) {
      s.noRender.fire = true;
      Boolean hidden = bool(oldLowFire, "hidden");
      if (hidden != null) s.noRender.hideFire = hidden;
      if (oldLowFire != null) {
        JsonElement offset = oldLowFire.get("offsetY");
        if (offset != null && offset.isJsonPrimitive() && offset.getAsJsonPrimitive().isNumber())
          s.noRender.fireOffset = offset.getAsFloat();
      }
    }

    if (hasHurtMigration) s.noRender.hurtCam = true;

    JsonObject fluidSrc = (tweaksyActive && tFluid != null && Boolean.TRUE.equals(bool(tFluid, "enabled")))
        ? tFluid
        : (Boolean.TRUE.equals(bool(oldFluid, "enabled")) ? oldFluid : null);
    if (fluidSrc != null) {
      Boolean waterOverlay = bool(fluidSrc, "waterOverlay");
      if (waterOverlay != null) s.noRender.waterOverlay = waterOverlay;
      Boolean waterFog = bool(fluidSrc, "waterFog");
      if (waterFog != null) s.noRender.waterFog = waterFog;
      Boolean lavaFog = bool(fluidSrc, "lavaFog");
      if (lavaFog != null) s.noRender.lavaFog = lavaFog;
    }
  }

  private static void migrateAtmosphere(JsonObject object, Settings s, Gson gson) {
    if (s == null) return;
    if (s.atmosphere == null) s.atmosphere = new Settings.Atmosphere();
    if ("japan".equals(s.atmosphere.mode)) s.atmosphere.mode = "sakura";
    else if ("halloween".equals(s.atmosphere.mode)) s.atmosphere.mode = "mystic";

    JsonObject atmo = object(object, "atmosphere");
    if (atmo == null) return;
    if (!atmo.has("sakura") && object(atmo, "japanSakura") != null)
      s.atmosphere.sakura = gson.fromJson(atmo.get("japanSakura"), Settings.Atmosphere.Sakura.class);
    if (!atmo.has("sakuraWater") && object(atmo, "japanWater") != null)
      s.atmosphere.sakuraWater =
          gson.fromJson(atmo.get("japanWater"), Settings.Atmosphere.SakuraWater.class);
    if (!atmo.has("sakuraSky") && object(atmo, "japanSky") != null)
      s.atmosphere.sakuraSky =
          gson.fromJson(atmo.get("japanSky"), Settings.Atmosphere.SakuraSky.class);

    if (!atmo.has("mysticSky") && object(atmo, "halloweenSky") != null)
      s.atmosphere.mysticSky =
          gson.fromJson(atmo.get("halloweenSky"), Settings.Atmosphere.MysticSky.class);
    if (!atmo.has("mysticFog") && object(atmo, "halloweenFog") != null)
      s.atmosphere.mysticFog =
          gson.fromJson(atmo.get("halloweenFog"), Settings.Atmosphere.MysticFog.class);
    if (!atmo.has("mysticWorld") && object(atmo, "halloweenWorld") != null)
      s.atmosphere.mysticWorld =
          gson.fromJson(atmo.get("halloweenWorld"), Settings.Atmosphere.MysticWorld.class);
    if (!atmo.has("mysticLens") && object(atmo, "halloweenLens") != null)
      s.atmosphere.mysticLens =
          gson.fromJson(atmo.get("halloweenLens"), Settings.Atmosphere.MysticLens.class);
    if (!atmo.has("mysticLife") && object(atmo, "halloweenLife") != null)
      s.atmosphere.mysticLife =
          gson.fromJson(atmo.get("halloweenLife"), Settings.Atmosphere.MysticLife.class);
    if (!atmo.has("mysticWind") && object(atmo, "halloweenWind") != null)
      s.atmosphere.mysticWind =
          gson.fromJson(atmo.get("halloweenWind"), Settings.Atmosphere.MysticWind.class);
  }

  private static JsonObject object(JsonObject parent, String key) {
    if (parent == null || !parent.has(key)) return null;
    JsonElement value = parent.get(key);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
  }

  private static Boolean bool(JsonObject parent, String key) {
    if (parent == null || !parent.has(key)) return null;
    JsonElement value = parent.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
      return null;
    return value.getAsBoolean();
  }

  private static void restoreAppearance(Settings s, JsonObject object) {
    if (s.skin == null || s.theme == null) return;
    java.util.LinkedHashMap<String, Theme> appearance = s.skin.appearance;
    if (appearance == null) {
      appearance = new java.util.LinkedHashMap<String, Theme>();
      s.skin.appearance = appearance;
    }
    String preset = s.skin.preset;
    if (appearance.isEmpty()) {
      appearance.put(preset, s.theme.copy());
    } else if (!appearance.containsKey(preset)) {
      Theme theme = Theme.defaults();
      DefaultSkin.applyDefaultsSafe(preset, theme);
      s.theme = theme;
      appearance.put(preset, theme.copy());
    } else {
      Theme stored = appearance.get(preset);
      if (stored != null) {
        s.theme = stored.copy();
      } else {
        Theme theme = Theme.defaults();
        DefaultSkin.applyDefaultsSafe(preset, theme);
        s.theme = theme;
        appearance.put(preset, theme.copy());
      }
    }
    s.skin.appliedPreset = preset;
  }

  private static void migrateThemeModes(Settings settings, JsonObject object) {
    migrateThemeMode(settings.theme, object(object, "theme"));
    if (settings.skin == null || settings.skin.appearance == null) return;
    JsonObject skin = object(object, "skin"), appearances = object(skin, "appearance");
    for (java.util.Map.Entry<String, Theme> entry : settings.skin.appearance.entrySet())
      migrateThemeMode(entry.getValue(), object(appearances, entry.getKey()));
  }

  private static void migrateThemeMode(Theme theme, JsonObject saved) {
    if (theme == null || saved == null) return;
    if (saved.has("mode")) {
      theme.applySavedMode();
      return;
    }
    boolean oldDark = theme.background == 0xff151618 && theme.surface == 0xff1c1e21
        && theme.raised == 0xff25282c && theme.text == 0xfff2f3f4;
    boolean oldLight = theme.background == 0xfff4f4f5 && theme.surface == 0xffffffff
        && theme.raised == 0xffeeeef0 && theme.text == 0xff18181b;
    if (oldDark || oldLight)
      theme.mode = "dark";
    else {
      theme.mode = "custom";
      theme.customBackground = theme.background;
      theme.customSurface = theme.surface;
      theme.customRaised = theme.raised;
      theme.customText = theme.text;
      if (theme.accent != theme.text) {
        theme.customAccent = theme.accent;
        theme.customAccentEnabled = true;
      }
    }
    theme.applySavedMode();
  }

  private static void migrateLayout(JsonObject object, Settings s) {
    if (!object.has("styles") || !object.get("styles").isJsonObject()) return;
    JsonObject old = object.getAsJsonObject("styles");
    if (!old.has("overrides") || !old.get("overrides").isJsonObject()) return;
    JsonObject overrides = old.getAsJsonObject("overrides");
    for (String id : new String[] {"menu", "cooldowns", "inventory", "tnt"}) {
      if (!overrides.has(id) || !overrides.get(id).isJsonObject()) continue;
      JsonObject entry = overrides.getAsJsonObject(id);
      if ("menu".equals(id) && s.menu != null) {
        s.menu.width = oldNumber(entry, "width", s.menu.width);
        s.menu.height = oldNumber(entry, "height", s.menu.height);
        s.menu.scale = oldNumber(entry, "scale", s.menu.scale);
      } else if ("cooldowns".equals(id) && s.hud != null) {
        s.hud.width = oldNumber(entry, "width", s.hud.width);
        s.hud.scale = oldNumber(entry, "scale", s.hud.scale);
      } else {
        Settings.Panel panel = "inventory".equals(id) ? s.inventory
            : "tnt".equals(id)                        ? s.tnt
                                                        : null;
        if (panel != null) {
          panel.width = oldNumber(entry, "width", panel.width);
          panel.height = oldNumber(entry, "height", panel.height);
          panel.scale = oldNumber(entry, "scale", panel.scale);
        }
      }
    }
  }

  private static float oldNumber(JsonObject entry, String name, float fallback) {
    try {
      if (entry.has(name) && entry.get(name).isJsonPrimitive()
          && entry.get(name).getAsJsonPrimitive().isNumber()) {
        float value = entry.get(name).getAsFloat();
        if (Float.isFinite(value) && value > 0) return value;
      }
    } catch (RuntimeException ignored) {
    }
    return fallback;
  }

  private static void checkStructure(String json) {
    List<Set<String>> scopes = new ArrayList<Set<String>>();
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      while (reader.peek() != JsonToken.END_DOCUMENT) {
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
            if (!scopes.get(scopes.size() - 1).add(name))
              throw new IllegalArgumentException("Duplicate profile key: " + name);
            break;
          case BOOLEAN:
            reader.nextBoolean();
            break;
          case NULL:
            reader.nextNull();
            break;
          case STRING:
          case NUMBER:
            reader.nextString();
            break;
          default:
            throw new IllegalArgumentException("Invalid profile structure");
        }
        if (scopes.size() > 32) throw new IllegalArgumentException("Profile nesting is too deep");
      }
    } catch (IOException | RuntimeException ex) {
      throw new IllegalArgumentException("Invalid JSON", ex);
    }
  }

  public String encode(Settings settings) {
    settings.validate();
    syncAppearance(settings);
    return gson.toJson(settings);
  }

  private static void syncAppearance(Settings settings) {
    if (settings == null || settings.skin == null || settings.theme == null) return;
    if (settings.skin.appearance == null)
      settings.skin.appearance = new java.util.LinkedHashMap<String, Theme>();
    if (settings.skin.preset == null || settings.skin.preset.isEmpty()) return;
    settings.skin.appearance.put(settings.skin.preset, settings.theme.copy());
  }

  public Settings copy(Settings settings) {
    return decode(encode(settings), MAX_LOCAL_BYTES);
  }

  public synchronized void changed(Settings settings) {
    if (closed) return;
    latest = encode(settings);
    status = "pending";
    if (pending != null) pending.cancel(false);
    final String snapshot = latest;
    pending = writer.schedule(() -> saveSnapshot(snapshot), 350, TimeUnit.MILLISECONDS);
  }

  private void saveSnapshot(String snapshot) {
    synchronized (ioLock) {
      if (!snapshot.equals(latest)) return;
      try {
        writeAtomic(configFile, snapshot);
        if (snapshot.equals(latest)) status = "saved";
      } catch (IOException error) {
        status = "write-error";
      }
    }
  }

  public String status() {
    return status;
  }

  public List<String> profiles() {
    Path folder = directory.resolve("profiles");
    if (!Files.isDirectory(folder)) return Collections.emptyList();
    List<String> names = new ArrayList<String>();
    try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.json")) {
      for (Path file : files) {
        String n = file.getFileName().toString();
        if (Files.isRegularFile(file)) names.add(n.substring(0, n.length() - 5));
      }
    } catch (IOException error) {
      throw new IllegalStateException("Cannot list profiles", error);
    }
    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    return Collections.unmodifiableList(names);
  }

  private Path profile(String name) {
    if (name == null || !name.equals(name.trim()) || name.length() > 48 || name.isEmpty()
        || name.startsWith(".") || !name.matches("[\\p{L}\\p{N} _.-]+") || name.contains(".."))
      throw new IllegalArgumentException("Profile name: 1–48 letters, digits, spaces, _ or -");
    return directory.resolve("profiles").resolve(name + ".json");
  }

  public void saveProfile(String name, Settings settings) {
    try {
      writeAtomic(profile(name), encode(settings));
    } catch (IOException error) {
      throw new IllegalStateException("Cannot save profile", error);
    }
  }

  public Settings loadProfile(String name) {
    try {
      return decode(read(profile(name)), MAX_LOCAL_BYTES);
    } catch (IOException error) {
      throw new IllegalStateException("Cannot load profile", error);
    }
  }

  public void deleteProfile(String name) {
    try {
      Files.deleteIfExists(profile(name));
    } catch (IOException error) {
      throw new IllegalStateException("Cannot delete profile", error);
    }
  }

  private static String read(Path file) throws IOException {
    if (Files.size(file) > MAX_LOCAL_BYTES) throw new IOException("Profile is too large");
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  private static void writeAtomic(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Path temp = Files.createTempFile(file.getParent(), ".nightclient-", ".tmp");
    try {
      Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
      try {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (pending != null) pending.cancel(false);
    final String snapshot = latest;
    if (snapshot != null) writer.execute(() -> saveSnapshot(snapshot));
    writer.shutdown();
    try {
      if (!writer.awaitTermination(3, TimeUnit.SECONDS)) writer.shutdownNow();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      writer.shutdownNow();
    }
  }
}
