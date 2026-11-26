package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.SkinParser;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditRegressionsCoreTest {
  @TempDir Path folder;

  @Test
  void localSaveAndLoadAllowLargeConfigsUpTo2MiBWhileClipboardDecodeRejectsOver256KiB()
      throws Exception {
    StringBuilder sb = new StringBuilder(310_000);
    sb.append("{\"schemaVersion\":1,\"saturation\":{\"strength\":1.75},\"unused\":\"");
    for (int i = 0; i < 300_000; i++) sb.append('A');
    sb.append("\"}");
    String largeJson = sb.toString();
    java.nio.file.Path settingsFile = folder.resolve("nightclient/settings.json");
    java.nio.file.Files.createDirectories(settingsFile.getParent());
    java.nio.file.Files.write(settingsFile, largeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    try (ConfigStore store = new ConfigStore(folder)) {
      assertThrows(IllegalArgumentException.class, () -> store.decode(largeJson));
      Settings loaded = store.load();
      assertEquals(1.75f, loaded.saturation.strength, 0.0001f);
    }
  }

  @Test
  void settingsValidateSanitizesAllSkinAppearanceThemes() {
    Settings s = new Settings();
    Theme broken = Theme.defaults();
    broken.radius = -42f;
    broken.blurRadius = Float.NaN;
    s.skin.appearance.put("default", broken);
    s.validate();
    assertEquals(0f, broken.radius);
    assertTrue(Float.isFinite(broken.blurRadius));
  }

  @Test
  void skinDocumentColorRejectsInvalidHexCharacters() {
    assertEquals(0x11223344, SkinDocument.color("#GGGGGG", 0x11223344));
    assertEquals(0x11223344, SkinDocument.color("#12345z", 0x11223344));
    assertEquals(0xFF123456, SkinDocument.color("#123456", 0));
  }

  @Test
  void skinParserNormalizesBetterChatCamelCase() throws Exception {
    SkinDocument doc = SkinParser.parse("<nightclient><hud><betterchat/></hud></nightclient>");
    assertNotNull(doc);
    assertNotNull(doc.hud());
    assertEquals(1, doc.hud().children.size());
    assertEquals("betterChat", doc.hud().children.get(0).tag);
  }
}
