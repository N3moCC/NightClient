package dev.nightclient.runtime;

import dev.nightclient.api.FormattedText;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FormattedNicknameProtection {
  private FormattedNicknameProtection() {}

  static String mask(String source, String nickname, Settings.Protect config, int baseColor) {
    if (source == null || source.isEmpty() || nickname == null || nickname.isEmpty()
        || config == null || !config.enabled || !config.hideNickname)
      return source;
    List<FormattedText.Run> runs = FormattedText.runs(source, baseColor);
    StringBuilder visible = new StringBuilder();
    List<Integer> owners = new ArrayList<Integer>();
    for (int r = 0; r < runs.size(); r++) {
      String text = runs.get(r).text;
      visible.append(text);
      for (int i = 0; i < text.length(); i++) owners.add(r);
    }
    String plain = visible.toString();
    String replacement =
        config.replacement == null || config.replacement.isEmpty() ? "NightProtect" : config.replacement;
    List<int[]> matches = new ArrayList<int[]>();
    for (int i = 0; i <= plain.length() - nickname.length(); i++) {
      int end = i + nickname.length();
      if (sameName(plain, i, nickname) && (i == 0 || !nameCharacter(plain.charAt(i - 1)))
          && (end == plain.length() || !nameCharacter(plain.charAt(end)))) {
        matches.add(new int[] {i, end});
        i = end - 1;
      }
    }
    if (matches.isEmpty()) return source;
    StringBuilder masked = new StringBuilder(source.length());
    int cursor = 0;
    for (int[] match : matches) {
      appendOriginal(masked, plain, runs, owners, cursor, match[0]);
      appendRun(masked, replacement, runs.get(owners.get(match[0])));
      cursor = match[1];
    }
    appendOriginal(masked, plain, runs, owners, cursor, plain.length());
    return masked.toString();
  }

  private static void appendOriginal(StringBuilder out, String plain, List<FormattedText.Run> runs,
      List<Integer> owners, int start, int end) {
    while (start < end) {
      int owner = owners.get(start), stop = start + 1;
      while (stop < end && owners.get(stop) == owner) stop++;
      appendRun(out, plain.substring(start, stop), runs.get(owner));
      start = stop;
    }
  }
  private static void appendRun(StringBuilder out, String text, FormattedText.Run style) {
    if (text.isEmpty()) return;
    out.append('§').append('r').append('§').append('x');
    String hex = String.format(java.util.Locale.ROOT, "%06x", style.color & 0xffffff);
    for (int i = 0; i < hex.length(); i++) out.append('§').append(hex.charAt(i));
    if (style.obfuscated) out.append('§').append('k');
    if (style.bold) out.append('§').append('l');
    if (style.strikethrough) out.append('§').append('m');
    if (style.underline) out.append('§').append('n');
    if (style.italic) out.append('§').append('o');
    out.append(text);
  }
  private static boolean sameName(String text, int at, String nickname) {
    for (int i = 0; i < nickname.length(); i++)
      if (Character.toLowerCase(text.charAt(at + i)) != Character.toLowerCase(nickname.charAt(i)))
        return false;
    return true;
  }
  private static boolean nameCharacter(char value) {
    return value == '_' || Character.isLetterOrDigit(value);
  }

  static Map<String, Object> scoreboard(
      Map<String, Object> source, String nickname, Settings.Protect config, int baseColor) {
    if (source == null || source.isEmpty() || config == null || !config.enabled
        || !config.hideNickname || nickname == null || nickname.isEmpty())
      return source;
    Map<String, Object> result = null;
    Object title = source.get("title");
    if (title instanceof String) {
      String masked = mask((String) title, nickname, config, baseColor);
      if (masked != title) {
        result = new LinkedHashMap<String, Object>(source);
        result.put("title", masked);
      }
    }
    Object rows = source.get("lines");
    if (rows instanceof List<?>) {
      List<Object> changed = null;
      List<?> lines = (List<?>) rows;
      for (int i = 0; i < lines.size(); i++) {
        Object value = lines.get(i);
        if(!(value instanceof Map<?,?>))continue;
        Map<?,?> row=(Map<?,?>)value;
        Object text = row.get("text");
        if (!(text instanceof String)) continue;
        String masked = mask((String) text, nickname, config, baseColor);
        if (masked == text) continue;
        if (changed == null) changed = new ArrayList<Object>(lines);
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : row.entrySet())
          if (entry.getKey() instanceof String) copy.put((String) entry.getKey(), entry.getValue());
        copy.put("text", masked);
        changed.set(i, Collections.unmodifiableMap(copy));
      }
      if (changed != null) {
        if (result == null) result = new LinkedHashMap<String, Object>(source);
        result.put("lines", Collections.unmodifiableList(changed));
      }
    }
    return result == null ? source : Collections.unmodifiableMap(result);
  }
}
