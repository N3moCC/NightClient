package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandLayoutFixTest {
  @Test
  void fixesOnlyCommandAndPreservesEveryArgument() {
    assertEquals(
        "/login Ник пароль ЙЦ\tсекрет", CommandLayoutFix.fix("/дщпшт Ник пароль ЙЦ\tсекрет"));
    assertEquals(
        "/register  Пароль!  Пароль! ", CommandLayoutFix.fix("/купшыеук  Пароль!  Пароль! "));
    assertEquals("LOGIN Ник", CommandLayoutFix.fix("ДЩПШТ Ник"));
  }
  @Test
  void latinArgumentsAndWhitespaceRemainUnchanged() {
    String original = "/login  abc<秘密>\tпароль";
    assertSame(original, CommandLayoutFix.fix(original));
    assertEquals("/msg Ник Привет", CommandLayoutFix.fix("/ьып Ник Привет"));
    assertEquals("/foo\nаргументы", CommandLayoutFix.fix("/ащщ\nаргументы"));
    assertNull(CommandLayoutFix.fix(null));
    assertEquals("", CommandLayoutFix.fix(""));
  }
  @Test
  void shiftedPunctuationUsesTheRealKeyboardKeys() {
    assertEquals("/[];'<>,.~`{}:\"", CommandLayoutFix.fix("/хъжэБЮбюЁёХЪЖЭ"));
  }
}
