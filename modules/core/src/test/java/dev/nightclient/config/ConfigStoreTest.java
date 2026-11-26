package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
  @TempDir Path folder;
  @Test
  void latestSnapshotSurvivesDebounceAndShutdown() throws Exception {
    Settings settings = new Settings();
    try (ConfigStore store = new ConfigStore(folder)) {
      for (int i = 0; i < 30; i++) {
        settings.saturation.strength = i / 20f;
        store.changed(settings);
      }
      settings.theme.accent = 0x7f49aabb;
      settings.language = "en";
      store.changed(settings);
    }
    try (ConfigStore store = new ConfigStore(folder)) {
      Settings loaded = store.load();
      assertEquals(1.45f, loaded.saturation.strength);
      assertEquals(0x7f49aabb, loaded.theme.accent);
      assertEquals("en", loaded.language);
    }
  }
  @Test
  void malformedFileIsPreservedBeforeRecovery() throws Exception {
    Path file = folder.resolve("nightclient/settings.json");
    Files.createDirectories(file.getParent());
    Files.write(file, "{malformed".getBytes(StandardCharsets.UTF_8));
    try (ConfigStore store = new ConfigStore(folder)) {
      assertEquals(1.15f, store.load().saturation.strength);
    }
    assertFalse(Files.exists(file));
    try (Stream<Path> files = Files.list(file.getParent())) {
      assertEquals(
          1, files.filter(p -> p.getFileName().toString().startsWith("settings.broken-")).count());
    }
  }
  @Test
  void importRejectsUnrecognizedSchemaAndOversizedDocument() {
    try (ConfigStore store = new ConfigStore(folder)) {
      assertThrows(IllegalArgumentException.class, () -> store.decode("{\"schemaVersion\":2}"));
      assertThrows(IllegalArgumentException.class, () -> store.decode("[]"));
      assertThrows(
          IllegalArgumentException.class, () -> store.decode(new String(new char[300000])));
      Settings imported = store.decode("{\"saturation\":{\"strength\":100},\"hud\":null}");
      assertEquals(2.5f, imported.saturation.strength);
      assertNotNull(imported.hud);
    }
  }
  @Test
  void localProfilesRoundTripAndCannotEscapeTheirDirectory() {
    try (ConfigStore store = new ConfigStore(folder)) {
      Settings original = new Settings();
      original.theme = Theme.defaults();
      original.theme.name = "Personal";
      original.theme.accent = 0xff43caa4;
      store.saveProfile("Моя тема", original);
      assertEquals("Personal", store.loadProfile("Моя тема").theme.name);
      assertEquals(0xff43caa4, store.loadProfile("Моя тема").theme.accent);
      assertEquals("Моя тема", store.profiles().get(0));
      assertThrows(IllegalArgumentException.class, () -> store.saveProfile("../outside", original));
      store.deleteProfile("Моя тема");
      assertTrue(store.profiles().isEmpty());
    }
  }
  @Test
  void nonfiniteSettingsAreSanitizedBeforeRendering() {
    Settings s = new Settings();
    assertEquals(1f, s.menu.scale);
    assertEquals(1f, s.menu.hudScale);
    assertTrue(s.menu.chatHudEditor);
    s.menu.scale = Float.NaN;
    s.menu.hudScale = Float.NaN;
    s.hud.width = Float.POSITIVE_INFINITY;
    s.saturation.strength = Float.NEGATIVE_INFINITY;
    s.theme.radius = -100;
    s.validate();
    assertEquals(1f, s.menu.scale);
    assertEquals(1f, s.menu.hudScale);
    assertEquals(148, s.hud.width);
    assertEquals(1.15f, s.saturation.strength);
    assertEquals(0, s.theme.radius);
  }
  @Test
  void importRequiresStrictJsonAndAnIntegralNumericSchema() {
    try (ConfigStore store = new ConfigStore(folder)) {
      for (String invalid :
          new String[] {"{schemaVersion:1}", "{'schemaVersion':1}", "{\"schemaVersion\":1,}",
              "{\"schemaVersion\":1} trailing", "{\"schemaVersion\":1.5}",
              "{\"schemaVersion\":\"1\"}", "{\"schemaVersion\":null}"}) {
        assertThrows(IllegalArgumentException.class, () -> store.decode(invalid), invalid);
      }
      assertEquals(1, store.decode("{\"schemaVersion\":1.0}").schemaVersion);
      StringBuilder multibyte = new StringBuilder("{\"unused\":\"");
      for (int i = 0; i < 100000; i++) multibyte.append('界');
      multibyte.append("\"}");
      assertThrows(IllegalArgumentException.class, () -> store.decode(multibyte.toString()));
    }
  }
  @Test
  void oldElementGeometryMigratesWithoutBringingBackItsVisualCascade() {
    try (ConfigStore store = new ConfigStore(folder)) {
      Settings s =
          store.decode("{\"styles\":{\"overrides\":{\"menu\":{\"width\":960,\"height\":620,"
                       + "\"scale\":1.25,\"accent\":42},\"cooldowns\":{\"width\":250,\"scale\":1.4}"
                       + ",\"inventory\":{\"width\":280,\"height\":110,\"scale\":0.8}}}}");
      assertEquals(960, s.menu.width);
      assertEquals(620, s.menu.height);
      assertEquals(1.25f, s.menu.scale);
      assertEquals(250, s.hud.width);
      assertEquals(1.4f, s.hud.scale);
      assertEquals(280, s.inventory.width);
      assertEquals(110, s.inventory.height);
      assertEquals(.8f, s.inventory.scale);
      assertFalse(store.encode(s).contains("overrides"));
      assertSame(s.theme, s.hudTheme());
    }
  }
  @Test
  void moduleBindingsRoundTripAndRejectInvalidInputNames() {
    try (ConfigStore store = new ConfigStore(folder)) {
      Settings s = new Settings();
      s.moduleBindings.put("saturation", "key.keyboard.g");
      s.moduleBindings.put("protect", "key.mouse.4");
      s.moduleBindings.put("../invalid", "key.keyboard.g");
      s.moduleBindings.put("hud", "file:/tmp/key");
      Settings copy = store.copy(s);
      assertEquals(2, copy.moduleBindings.size());
      assertEquals("key.keyboard.g", copy.moduleBindings.get("saturation"));
      assertEquals("key.mouse.4", copy.moduleBindings.get("protect"));
    }
  }
  @Test
  void ambiguousAndDeepProfilesFailBeforeRecursiveParsing() {
    try (ConfigStore store = new ConfigStore(folder)) {
      assertThrows(IllegalArgumentException.class,
          () -> store.decode("{\"schemaVersion\":1,\"schemaVersion\":2}"));
      assertThrows(IllegalArgumentException.class,
          () -> store.decode("{\"theme\":{\"name\":\"one\",\"name\":\"two\"}}"));
      StringBuilder deep = new StringBuilder("{\"unused\":");
      for (int i = 0; i < 2000; i++) deep.append('[');
      deep.append('0');
      for (int i = 0; i < 2000; i++) deep.append(']');
      deep.append('}');
      assertThrows(IllegalArgumentException.class, () -> store.decode(deep.toString()));
      assertNotNull(store.decode("{\"unused\":[{},[]],\"schemaVersion\":1}"));
    }
  }
}
