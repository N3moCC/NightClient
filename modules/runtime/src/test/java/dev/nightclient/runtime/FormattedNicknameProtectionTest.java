package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.FormattedText;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormattedNicknameProtectionTest {
  private static Settings.Protect config() {
    Settings.Protect p = new Settings.Protect();
    p.enabled = true;
    p.hideNickname = true;
    p.replacement = "Alias";
    return p;
  }
  private static String visible(String value) {
    StringBuilder out = new StringBuilder();
    for (FormattedText.Run r : FormattedText.runs(value, 0x60445566)) out.append(r.text);
    return out.toString();
  }
  private static List<String> glyphs(String value) {
    List<String> out = new ArrayList<String>();
    for (FormattedText.Run r : FormattedText.runs(value, 0x60445566))
      for (int i = 0; i < r.text.length(); i++)
        out.add(r.text.charAt(i) + ":" + r.color + ":" + r.bold + ":" + r.italic + ":" + r.underline
            + ":" + r.strikethrough);
    return out;
  }
  @Test
  void rainbowRgbNameIsMaskedAcrossRunsWithoutChangingSurroundingStyleOrAlpha() {
    String prefix = "§8🏠 [§6Rank§8] ";
    String nick = "§x§1§2§3§4§5§6P§bl§ca§dy§ee§ar";
    String suffix = "§7! §oAfter 🏠";
    String original = prefix + nick + suffix;
    String protectedText =
        FormattedNicknameProtection.mask(original, "Player", config(), 0x60445566);
    assertEquals("🏠 [Rank] Alias! After 🏠", visible(protectedText));
    assertFalse(visible(protectedText).contains("Player"));
    List<String> expected = glyphs(original), actual = glyphs(protectedText);
    int prefixLength = visible(prefix).length(), suffixLength = visible(suffix).length();
    assertEquals(expected.subList(0, prefixLength), actual.subList(0, prefixLength));
    assertEquals(expected.subList(expected.size() - suffixLength, expected.size()),
        actual.subList(actual.size() - suffixLength, actual.size()));
    boolean aliasFound = false;
    for (FormattedText.Run r : FormattedText.runs(protectedText, 0x60445566))
      if (r.text.equals("Alias")) {
        aliasFound = true;
        assertEquals(0x60123456, r.color);
      }
    assertTrue(
        aliasFound, "The alias inherits the first matched color while preserving the base alpha");
  }
  @Test
  void matchingIsCaseInsensitiveButRespectsWholeMinecraftNamesAndNewlines() {
    String raw =
        "Player_2 xPlayer Player2 _Player ИгрокPlayer Player-PLAYER / player\nPlayer /\nPlay\ner";
    assertEquals(
        "Player_2 xPlayer Player2 _Player ИгрокPlayer Alias-Alias / Alias\nAlias /\nPlay\ner",
        visible(FormattedNicknameProtection.mask(raw, "Player", config(), 0xffffffff)));
    String noMatch = "§aPlayerTwo §bOther_Player §cPlay\ner 🏠";
    assertSame(noMatch, FormattedNicknameProtection.mask(noMatch, "Player", config(), 0xffffffff));
  }
  @Test
  void disabledProtectionAndHideNicknameFalsePreserveOriginalObjectAndFormatting() {
    String source = "§x§0§1§2§3§4§5P§cl§da§ey§be§ar";
    Settings.Protect config = config();
    config.hideNickname = false;
    assertSame(source, FormattedNicknameProtection.mask(source, "Player", config, 0x40ffffff));
    config.hideNickname = true;
    config.enabled = false;
    assertSame(source, FormattedNicknameProtection.mask(source, "Player", config, 0xffffffff));
    config.enabled = true;
    assertSame(source, FormattedNicknameProtection.mask(source, "", config, 0xffffffff));
    config.replacement = "";
    assertEquals("NightProtect",
        visible(FormattedNicknameProtection.mask(source, "Player", config, 0xffffffff)),
        "An empty alias uses the same safe fallback as regular Protect");
  }
}
