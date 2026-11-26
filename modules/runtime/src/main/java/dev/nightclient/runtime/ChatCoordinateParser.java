package dev.nightclient.runtime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatCoordinateParser {
  private ChatCoordinateParser() {}

  private static final Pattern FORMAT_CODE =
      Pattern.compile("(?:§x(?:§[0-9a-fA-F]){6}|§#[0-9a-fA-F]{6}|§[0-9a-fk-orA-FK-OR#])");

  private static final Pattern COORD_3 =
      Pattern.compile("(?<![a-zA-Z0-9_])(?:x\\s*[:=]\\s*)?([+-]?\\d+(?:\\.\\d+)?)[,\\s/"
                      + "|]+(?:y\\s*[:=]\\s*)?([+-]?\\d+(?:\\.\\d+)?)[,\\s/"
                      + "|]+(?:z\\s*[:=]\\s*)?([+-]?\\d+(?:\\.\\d+)?)(?![a-zA-Z0-9_])",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern COORD_2 =
      Pattern.compile("(?<![a-zA-Z0-9_])(?:x\\s*[:=]\\s*)?([+-]?\\d+(?:\\.\\d+)?)[,\\s/"
                      + "|]+(?:z\\s*[:=]\\s*)?([+-]?\\d+(?:\\.\\d+)?)(?![a-zA-Z0-9_])",
          Pattern.CASE_INSENSITIVE);

  public static int clickedLine(double mouseY, int height, double scale, int lineHeight,
      int linesPerPage, int scrollPosition, int lineCount) {
    if (!Double.isFinite(mouseY) || !Double.isFinite(scale) || scale <= 0 || lineHeight <= 0
        || linesPerPage <= 0 || scrollPosition < 0 || lineCount <= 0)
      return -1;
    double offset = (height - mouseY - 40.0) / (scale * lineHeight);
    if (offset < 0 || offset >= linesPerPage) return -1;
    int index = (int) offset + scrollPosition;
    return index >= 0 && index < lineCount ? index : -1;
  }

  public static double[] parse(String rawText, double defaultY) {
    if (rawText == null || rawText.isEmpty()) return null;
    String clean = FORMAT_CODE.matcher(rawText).replaceAll("");

    Matcher m3 = COORD_3.matcher(clean);
    while (m3.find()) {
      try {
        double x = Double.parseDouble(m3.group(1));
        double y = Double.parseDouble(m3.group(2));
        double z = Double.parseDouble(m3.group(3));
        if (validCoord(x, y, z)) {
          return new double[] {x, y, z};
        }
      } catch (NumberFormatException ignored) {
      }
    }

    Matcher m2 = COORD_2.matcher(clean);
    while (m2.find()) {
      try {
        double x = Double.parseDouble(m2.group(1));
        double z = Double.parseDouble(m2.group(2));
        if (validCoord(x, defaultY, z) && (Math.abs(x) >= 20 || Math.abs(z) >= 20)) {
          return new double[] {x, defaultY, z};
        }
      } catch (NumberFormatException ignored) {
      }
    }

    return null;
  }

  public static double[] parseAt(String rawText, double defaultY, int character) {
    if (rawText == null || character < 0) return null;
    String clean = FORMAT_CODE.matcher(rawText).replaceAll("");
    if (character >= clean.length()) return null;
    Matcher triple = COORD_3.matcher(clean);
    while (triple.find()) {
      if (character < triple.start() || character >= triple.end()) continue;
      try {
        double x = Double.parseDouble(triple.group(1)), y = Double.parseDouble(triple.group(2)),
               z = Double.parseDouble(triple.group(3));
        return validCoord(x, y, z) ? new double[] {x, y, z} : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    Matcher pair = COORD_2.matcher(clean);
    while (pair.find()) {
      if (character < pair.start() || character >= pair.end()) continue;
      try {
        double x = Double.parseDouble(pair.group(1)), z = Double.parseDouble(pair.group(2));
        return validCoord(x, defaultY, z) && (Math.abs(x) >= 20 || Math.abs(z) >= 20)
            ? new double[] {x, defaultY, z}
            : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static boolean validCoord(double x, double y, double z) {
    return Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000 && y >= -128 && y <= 512;
  }
}
