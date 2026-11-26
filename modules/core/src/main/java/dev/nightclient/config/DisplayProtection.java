package dev.nightclient.config;

public final class DisplayProtection {
  private DisplayProtection() {}

  public static String maskNickname(String text, String ownNickname, Settings.Protect config) {
    if (text == null || config == null || !config.enabled || !config.hideNickname
        || ownNickname == null || ownNickname.isEmpty() || text.length() < ownNickname.length())
      return text;
    String replacement =
        config.replacement == null || config.replacement.isEmpty() ? "NightProtect" : config.replacement;
    StringBuilder result = null;
    int copied = 0, length = ownNickname.length();
    for (int i = 0; i <= text.length() - length; i++) {
      int end = matchName(text, i, ownNickname);
      if (end < 0 || wordBefore(text, i) || wordAfter(text, end)) continue;
      if (result == null) result = new StringBuilder(text.length());
      result.append(text, copied, i).append(replacement);
      copied = end;
      i = copied - 1;
    }
    return result == null ? text : result.append(text, copied, text.length()).toString();
  }

  private static boolean nameCharacter(char c) {
    return c == '_' || Character.isLetterOrDigit(c);
  }

  private static boolean formatCode(char c) {
    char lower = Character.toLowerCase(c);
    return lower >= '0' && lower <= '9' || lower >= 'a' && lower <= 'f'
        || lower >= 'k' && lower <= 'o' || lower == 'r' || lower == 'x';
  }

  public static int findNicknameEnd(String text, int start, String name) {
    if (text == null || name == null || name.isEmpty() || start < 0 || start >= text.length()) {
      return -1;
    }
    int end = matchName(text, start, name);
    if (end < 0 || wordBefore(text, start) || wordAfter(text, end)) return -1;
    return end;
  }

  private static int matchName(String text, int start, String name) {
    int cursor = start;
    for (int i = 0; i < name.length(); i++) {
      if (i > 0)
        while (cursor + 1 < text.length() && text.charAt(cursor) == '§'
            && formatCode(text.charAt(cursor + 1)))
          cursor += 2;
      if (cursor >= text.length()
          || Character.toLowerCase(text.charAt(cursor)) != Character.toLowerCase(name.charAt(i)))
        return -1;
      cursor++;
    }
    return cursor;
  }

  private static boolean wordBefore(String text, int cursor) {
    while (cursor >= 2 && text.charAt(cursor - 2) == '§' && formatCode(text.charAt(cursor - 1)))
      cursor -= 2;
    return cursor > 0 && nameCharacter(text.charAt(cursor - 1));
  }

  private static boolean wordAfter(String text, int cursor) {
    while (cursor + 1 < text.length() && text.charAt(cursor) == '§'
        && formatCode(text.charAt(cursor + 1)))
      cursor += 2;
    return cursor < text.length() && nameCharacter(text.charAt(cursor));
  }

  public static String maskPasswordInput(String value, Settings.Protect config) {
    if (value == null || config == null || !config.enabled || !config.maskPasswords
        || config.aliases == null)
      return value;
    int start = 0;
    while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
    if (start >= value.length() || value.charAt(start) != '/') return value;
    int commandStart = start + 1, end = commandStart;
    while (end < value.length() && !Character.isWhitespace(value.charAt(end))) end++;
    if (end == commandStart || end == value.length()) return value;
    boolean authentication = false;
    for (String alias : config.aliases) {
      if (alias == null || alias.isEmpty()) continue;
      int aliasStart = alias.charAt(0) == '/' ? 1 : 0;
      int aliasLength = alias.length() - aliasStart;
      if (aliasLength == end - commandStart
          && value.regionMatches(true, commandStart, alias, aliasStart, aliasLength)) {
        authentication = true;
        break;
      }

      int colon = value.lastIndexOf(':', end - 1);
      if (colon >= commandStart && aliasLength == end - colon - 1
          && value.regionMatches(true, colon + 1, alias, aliasStart, aliasLength)) {
        authentication = true;
        break;
      }
    }
    if (!authentication) return value;
    StringBuilder masked = null;
    for (int i = end; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) continue;
      if (masked == null) masked = new StringBuilder(value);
      masked.setCharAt(i, '\u2022');
    }
    return masked == null ? value : masked.toString();
  }

  public static String maskCommand(String value, Settings.Protect config) {
    return maskPasswordInput(value, config);
  }
}
