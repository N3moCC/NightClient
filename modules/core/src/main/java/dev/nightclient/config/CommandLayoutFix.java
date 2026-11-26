package dev.nightclient.config;

public final class CommandLayoutFix {
  private static final String CYRILLIC = "йцукенгшщзхъфывапролджэячсмитьбюё";
  private static final String LATIN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
  private static final String UPPER = "QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>~";

  private CommandLayoutFix() {}

  public static String fix(String input) {
    if (input == null || input.isEmpty()) return input;
    int first = input.charAt(0) == '/' ? 1 : 0, end = first;
    while (end < input.length() && !Character.isWhitespace(input.charAt(end))) end++;
    StringBuilder output = null;
    for (int i = first; i < end; i++) {
      char original = input.charAt(i), lower = Character.toLowerCase(original);
      int index = CYRILLIC.indexOf(lower);
      if (index < 0) continue;
      if (output == null) output = new StringBuilder(input);
      char mapped = (Character.isUpperCase(original) ? UPPER : LATIN).charAt(index);
      output.setCharAt(i, mapped);
    }
    return output == null ? input : output.toString();
  }
}
