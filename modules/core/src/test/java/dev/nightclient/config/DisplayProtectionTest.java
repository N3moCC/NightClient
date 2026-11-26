package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DisplayProtectionTest {
  private Settings.Protect enabled() {
    Settings.Protect p = new Settings.Protect();
    p.enabled = true;
    return p;
  }

  @Test
  void authenticationCommandsMaskEveryArgumentAndRetainCursorOffsets() {
    Settings.Protect p = enabled();
    for (String command :
        new String[] {"/l secret", "/LOGIN secret", "/reg old new", "/register old old",
            "/changepassword old new", "/cp old new", "/auth:login secret", "  /login\t密碼😀"}) {
      String masked = DisplayProtection.maskPasswordInput(command, p);
      assertNotEquals(command, masked, command);
      assertEquals(command.length(), masked.length(), command);
      assertFalse(masked.contains("secret"));
      assertFalse(masked.contains("old"));
      assertFalse(masked.contains("new"));
      for (int i = 0; i < command.length(); i++) {
        if (Character.isWhitespace(command.charAt(i)))
          assertEquals(command.charAt(i), masked.charAt(i));
      }
    }
    String original = "/login secret";
    assertEquals("/login ••••••", DisplayProtection.maskPasswordInput(original, p));
    assertEquals("/login secret", original);
  }

  @Test
  void normalChatAndUnrelatedCommandsKeepTheirExactOriginalObjects() {
    Settings.Protect p = enabled();
    for (String text : new String[] {"hello secret", "tell /login secret", "/login", "/login    ",
             "/loginextra secret", "/registering secret", "/msg friend password",
             "/minecraft:tp user"})
      assertSame(text, DisplayProtection.maskPasswordInput(text, p), text);
    p.aliases = Arrays.asList("auth", "/custom");
    assertEquals("/auth ••••••", DisplayProtection.maskPasswordInput("/auth secret", p));
    assertEquals("/custom ••••••", DisplayProtection.maskPasswordInput("/custom secret", p));
    assertEquals("/login secret", DisplayProtection.maskPasswordInput("/login secret", p));
  }

  @Test
  void nicknameMaskingUsesWholeNamesAndHandlesLegacyColourCodes() {
    Settings.Protect p = enabled();
    p.replacement = "Hidden";
    assertEquals("[Hidden] killed ALICE_BOSS; Hidden's item",
        DisplayProtection.maskNickname("[Alice] killed ALICE_BOSS; alice's item", "Alice", p));
    assertEquals(
        "§aHidden§r joined", DisplayProtection.maskNickname("§aAlice§r joined", "Alice", p));
    assertEquals(
        "§aHidden§r joined", DisplayProtection.maskNickname("§aAl§bice§r joined", "Alice", p));
    assertEquals("Not§aAlice", DisplayProtection.maskNickname("Not§aAlice", "Alice", p));
    assertEquals("Alice§aBoss", DisplayProtection.maskNickname("Alice§aBoss", "Alice", p));
    String unrelated = "Malice and Alice123";
    assertSame(unrelated, DisplayProtection.maskNickname(unrelated, "Alice", p));
    assertEquals("Hidden Hidden", DisplayProtection.maskNickname("Alice ALICE", "Alice", p));
  }

  @Test
  void eachProtectionCanBeDisabledIndependently() {
    Settings.Protect p = enabled();
    String command = "/login secret", name = "Alice";
    p.hideNickname = false;
    assertSame(name, DisplayProtection.maskNickname(name, name, p));
    assertNotSame(command, DisplayProtection.maskPasswordInput(command, p));
    p.hideNickname = true;
    p.maskPasswords = false;
    assertEquals("NightProtect", DisplayProtection.maskNickname(name, name, p));
    assertSame(command, DisplayProtection.maskPasswordInput(command, p));
    p.enabled = false;
    assertSame(name, DisplayProtection.maskNickname(name, name, p));
    assertSame(command, DisplayProtection.maskPasswordInput(command, p));
  }
}
