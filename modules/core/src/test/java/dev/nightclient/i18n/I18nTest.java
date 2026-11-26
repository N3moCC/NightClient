package dev.nightclient.i18n;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class I18nTest {
  @Test
  void packagedCatalogsCoverTheSameKeysAndResolveRegionalLanguageNames() {
    assertEquals(I18n.catalog("en").keySet(), I18n.catalog("ru").keySet());
    assertTrue(I18n.catalog("en").size() > 300);
    assertEquals("ru", I18n.language("ru_RU"));
    assertEquals("en", I18n.language("unknown"));
    assertEquals(I18n.text("en", "nav.features"), I18n.text(null, "nav.features"));
    assertEquals("missing.translation.key", I18n.text("ru", "missing.translation.key"));
    assertNotEquals(I18n.text("ru", "nav.features"), I18n.text("en", "nav.features"));
    assertEquals("Перезарядки", I18n.text("ru", "hud.cooldowns.title"));
    assertEquals("Cooldowns", I18n.text("en", "hud.cooldowns.title"));
    assertEquals("Нотный блок", dev.nightclient.config.Settings.SoundControl.displayBlockName("note_block", "ru"));
    assertEquals("Note Block", dev.nightclient.config.Settings.SoundControl.displayBlockName("note_block", "en"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void NcGetsNestedReadOnlyNamespacesWithoutCopyingCatalogsEachFrame() {
    Map<String, Object> context = I18n.context("ru");
    Map<String, Object> nav = (Map<String, Object>) context.get("nav");
    assertEquals(I18n.text("ru", "nav.features"), nav.get("features"));
    assertSame(context, I18n.context("ru_RU"));
    assertThrows(UnsupportedOperationException.class, () -> context.put("extra", "text"));
    assertThrows(UnsupportedOperationException.class, () -> nav.put("features", "text"));
    assertThrows(
        UnsupportedOperationException.class, () -> I18n.catalog("en").put("extra", "text"));
  }

  @Test
  void NamedArgumentsAreInsertedAsTextWithoutExecutingOrRecursivelyExpandingThem() {
    String value = "{{settings.protect.replacement}} {name}";
    assertEquals("Delete " + value + "?",
        I18n.format("en", "profile.deleteConfirmation", Collections.singletonMap("name", value)));
    assertEquals(
        "Delete {name}?", I18n.format("en", "profile.deleteConfirmation", Collections.emptyMap()));
    assertTrue(I18n.format("en", "profile.deleteConfirmation",
                       Collections.singletonMap("name", repeat('x', 10000)))
                   .length()
        <= 8192);
  }

  @Test
  void CatalogParserRejectsAmbiguousKeysAndNonStringOrInvalidUnicodeValues() {
    for (String json :
        new String[] {"{\"a\":\"first\",\"a\":\"second\"}", "{\"a\":true}", "{\"a\":null}",
            "{\"a\":\"value\",\"a.child\":\"value\"}", "{\"../outside\":\"value\"}",
            "{\"a\":\"\\u0000\"}", "{\"a\":\"\\ud800\"}", "{\"a\":\"\\udc00\"}",
            "{\"a\":\"value\"} trailing", "{\"a\":\"" + repeat('x', 2049) + "\"}"})
      assertThrows(IllegalArgumentException.class,
          () -> I18n.parseCatalog(json), json.substring(0, Math.min(80, json.length())));
    assertEquals("Русский 😀\nLine", I18n.parseCatalog("{\"a\":\"Русский 😀\\nLine\"}").get("a"));
  }

  private static String repeat(char value, int count) {
    char[] chars = new char[count];
    java.util.Arrays.fill(chars, value);
    return new String(chars);
  }
}
