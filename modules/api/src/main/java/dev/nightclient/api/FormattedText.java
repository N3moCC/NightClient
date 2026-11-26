package dev.nightclient.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FormattedText {
  private static final String COLORS = "0123456789abcdef";
  private static final int[] RGB = {0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa,
      0xffaa00, 0xaaaaaa, 0x555555, 0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55,
      0xffffff};
  private FormattedText() {}

  public static final class Run {
    public final String text;
    public final int color;
    public final boolean bold, italic, underline, strikethrough, obfuscated;
    private final int style;

    private Run(String text, int color, int style) {
      this.text = text;
      this.color = color;
      this.style = style;
      bold = (style & 1) != 0;
      italic = (style & 2) != 0;
      underline = (style & 4) != 0;
      strikethrough = (style & 8) != 0;
      obfuscated = (style & 16) != 0;
    }

    private Run withText(String nextText) {
      return new Run(nextText, color, style);
    }

    public String nativeText() {
      if (!bold && !italic && !underline && !strikethrough && !obfuscated) return text;
      StringBuilder out = new StringBuilder();
      if (obfuscated) out.append('§').append('k');
      if (bold) out.append('§').append('l');
      if (strikethrough) out.append('§').append('m');
      if (underline) out.append('§').append('n');
      if (italic) out.append('§').append('o');
      return out.append(text).toString();
    }
  }

  public static List<Run> runs(String value, int baseColor) {
    if (value == null || value.isEmpty()) return Collections.emptyList();
    List<Run> result = new ArrayList<Run>();
    StringBuilder text = new StringBuilder();
    int color = baseColor, style = 0;
    for (int i = 0; i < value.length();) {
      char c = value.charAt(i);
      if (c == '§' && i + 1 < value.length()) {
        char code = Character.toLowerCase(value.charAt(i + 1));
        int nextColor = color, nextStyle = style, consumed = 0, legacy = COLORS.indexOf(code);
        if (legacy >= 0) {
          nextColor = (baseColor & 0xff000000) | RGB[legacy];
          nextStyle = 0;
          consumed = 2;
        } else if (code == 'x' && i + 14 <= value.length()) {
          int rgb = 0;
          boolean valid = true;
          for (int n = 0; n < 6; n++) {
            int at = i + 2 + n * 2, digit = Character.digit(value.charAt(at + 1), 16);
            if (value.charAt(at) != '§' || digit < 0) {
              valid = false;
              break;
            }
            rgb = (rgb << 4) | digit;
          }
          if (valid) {
            nextColor = (baseColor & 0xff000000) | rgb;
            nextStyle = 0;
            consumed = 14;
          }
        } else if (code == 'r') {
          nextColor = baseColor;
          nextStyle = 0;
          consumed = 2;
        } else {
          int bit = code == 'l' ? 1
              : code == 'o'     ? 2
              : code == 'n'     ? 4
              : code == 'm'     ? 8
              : code == 'k'     ? 16
                                : 0;
          if (bit != 0) {
            nextStyle |= bit;
            consumed = 2;
          }
        }
        if (consumed > 0) {
          if (text.length() > 0) {
            result.add(new Run(text.toString(), color, style));
            text.setLength(0);
          }
          color = nextColor;
          style = nextStyle;
          i += consumed;
          continue;
        }
      }
      text.append(c);
      i++;
    }
    if (text.length() > 0) result.add(new Run(text.toString(), color, style));
    return Collections.unmodifiableList(result);
  }

  public static String plainText(String value) {
    List<Run> parsed = runs(value, 0xffffffff);
    if (parsed.isEmpty()) return "";
    StringBuilder out = new StringBuilder();
    for (Run run : parsed) out.append(run.text);
    return out.toString();
  }

  public static float runsWidth(
      Canvas canvas, String family, List<Run> runs, float size, boolean bold, float tracking) {
    float width = 0;
    for (Run run : runs)
      if (!run.text.isEmpty())
        width += canvas.textWidthFamily(family, run.text, size, bold || run.bold, tracking);
    return width;
  }

  public static float textWidth(
      Canvas canvas, String family, String value, float size, boolean bold, float tracking) {
    return runsWidth(canvas, family, runs(value, 0xffffffff), size, bold, tracking);
  }

  public static List<Run> fit(Canvas canvas, String family, List<Run> runs, float width, float size,
      boolean bold, float tracking) {
    if (runs == null || runs.isEmpty() || width <= 0) return Collections.emptyList();
    if (runsWidth(canvas, family, runs, size, bold, tracking) <= width) return runs;
    String suffix = "…";
    float suffixWidth = canvas.textWidthFamily(family, suffix, size, bold, tracking);
    if (suffixWidth > width) return Collections.emptyList();
    float budget = Math.max(0, width - suffixWidth);
    List<Run> out = new ArrayList<Run>();
    float used = 0;
    for (Run run : runs) {
      if (run.text.isEmpty()) continue;
      boolean runBold = bold || run.bold;
      float runWidth = canvas.textWidthFamily(family, run.text, size, runBold, tracking);
      if (used + runWidth <= budget) {
        out.add(run);
        used += runWidth;
        continue;
      }
      String part = run.text;
      while (!part.isEmpty()
          && used + canvas.textWidthFamily(family, part, size, runBold, tracking) > budget)
        part = part.substring(0, part.offsetByCodePoints(part.length(), -1));
      if (!part.isEmpty()) {
        out.add(run.withText(part + suffix));
      } else if (!out.isEmpty()) {
        Run last = out.remove(out.size() - 1);
        out.add(last.withText(last.text + suffix));
      } else {
        out.add(run.withText(suffix));
      }
      break;
    }
    return Collections.unmodifiableList(out);
  }

  public static void drawRuns(Canvas canvas, String family, List<Run> runs, float x, float y,
      float size, boolean bold, float tracking) {
    float pen = x, lineThickness = Math.max(.6f, size * .08f);
    for (Run run : runs) {
      if (run.text.isEmpty()) continue;
      boolean runBold = bold || run.bold;
      canvas.textFamily(family, run.text, pen, y, size, run.color, runBold, tracking);
      float runWidth = canvas.textWidthFamily(family, run.text, size, runBold, tracking);
      if (run.strikethrough)
        canvas.rect(pen, y + size * .52f, runWidth, lineThickness, run.color);
      if (run.underline)
        canvas.rect(pen, y + size * .95f, runWidth, lineThickness, run.color);
      pen += runWidth;
    }
  }

  public static float gameWidth(Canvas canvas, String value, float scale) {
    float width = 0;
    for (Run run : runs(value, 0xffffffff)) width += canvas.gameTextWidth(run.nativeText(), scale);
    return width;
  }

  public static void drawGame(
      Canvas canvas, String value, float x, float y, float scale, int color, boolean shadow) {
    float pen = x;
    for (Run run : runs(value, color)) {
      String text = run.nativeText();
      canvas.gameText(text, pen, y, scale, run.color, shadow);
      pen += canvas.gameTextWidth(text, scale);
    }
  }
}
