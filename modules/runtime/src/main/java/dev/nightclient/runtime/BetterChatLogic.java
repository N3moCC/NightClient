package dev.nightclient.runtime;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BetterChatLogic {
  private BetterChatLogic() {}

  public static final class ChatToastStatus {
    public final String message;
    public final float ageSeconds;

    public ChatToastStatus(String message, float ageSeconds) {
      this.message = message == null ? "" : message;
      this.ageSeconds = ageSeconds;
    }
  }

  public static final class StackMatch {
    public final int count;
    public final Object previousEntry;

    public StackMatch(int count, Object previousEntry) {
      this.count = count;
      this.previousEntry = previousEntry;
    }
  }

  private static final Pattern FORMAT_STRIP =
      Pattern.compile("(?:§x(?:§[0-9a-fA-F]){6})|§[0-9a-fk-orA-FK-OR]");
  private static final Pattern LEADING_TIMESTAMP =
      Pattern.compile("^\\[\\d{2}:\\d{2}(?::\\d{2})?\\]\\s+");
  private static final Pattern TRAILING_STACK_BADGE =
      Pattern.compile("\\s+\\[x(\\d{1,4})\\]$");

  public static String stripFormatting(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    return FORMAT_STRIP.matcher(raw).replaceAll("");
  }

  public static String formatTimestamp(long epochMillis, boolean includeSeconds) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(epochMillis);
    int h = cal.get(Calendar.HOUR_OF_DAY);
    int m = cal.get(Calendar.MINUTE);
    int s = cal.get(Calendar.SECOND);
    StringBuilder sb = new StringBuilder(11);
    sb.append('[');
    if (h < 10) sb.append('0');
    sb.append(h).append(':');
    if (m < 10) sb.append('0');
    sb.append(m);
    if (includeSeconds) {
      sb.append(':');
      if (s < 10) sb.append('0');
      sb.append(s);
    }
    sb.append("] ");
    return sb.toString();
  }

  public static String normalizeMessage(String raw) {
    String clean = stripFormatting(raw).trim();
    if (clean.isEmpty()) return "";
    clean = LEADING_TIMESTAMP.matcher(clean).replaceFirst("");
    clean = TRAILING_STACK_BADGE.matcher(clean).replaceFirst("");
    return clean.trim();
  }

  public static String cleanForCopy(String raw) {
    return normalizeMessage(raw);
  }

  public static int extractStackCount(String raw) {
    String clean = stripFormatting(raw).trim();
    Matcher m = TRAILING_STACK_BADGE.matcher(clean);
    if (!m.find()) return 1;
    try {
      return Math.max(1, Integer.parseInt(m.group(1)));
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  public static float advanceSlideOffset(float currentOffset, float dtSeconds, float speed) {
    if (!Float.isFinite(currentOffset) || currentOffset <= 0.12f) return 0f;
    float clampedDt = Float.isFinite(dtSeconds) ? Math.max(0f, Math.min(0.1f, dtSeconds)) : 0f;
    float clampedSpeed = Float.isFinite(speed) ? Math.max(0.5f, Math.min(2.5f, speed)) : 1.0f;
    float decayed = (float) (currentOffset * Math.exp(-clampedDt * 15.5 * clampedSpeed));
    return decayed < 0.12f ? 0f : decayed;
  }

  public static float smoothApproach(float current, float target, float dtSeconds, float rate) {
    if (!Float.isFinite(current) || current <= 0f) return target;
    if (!Float.isFinite(target)) return current;
    float clampedDt = Float.isFinite(dtSeconds) ? Math.max(0f, Math.min(0.1f, dtSeconds)) : 0f;
    float factor = 1f - (float) Math.exp(-clampedDt * Math.max(1f, rate));
    float next = current + (target - current) * factor;
    return Math.abs(next - target) < 0.15f ? target : next;
  }

  public static float[] computeHudAnchor(
      float anchorX, float anchorY, float offsetX, float offsetY, float scale,
      int screenW, int screenH) {
    float sw = Math.max(1f, (float) screenW);
    float sh = Math.max(1f, (float) screenH);
    float rawS = Float.isFinite(scale) ? Math.max(0.5f, Math.min(2.0f, scale)) : 1.0f;
    float s = Math.max(0.01f, Math.min(rawS, Math.min(sw / 240f, sh / 96f)));
    float pw = 240f * s;
    float ph = 96f * s;
    float ax = Float.isFinite(anchorX) ? Math.max(0f, Math.min(1f, anchorX)) : 0f;
    float ay = Float.isFinite(anchorY) ? Math.max(0f, Math.min(1f, anchorY)) : 1f;
    float ox = Float.isFinite(offsetX) ? offsetX : 4f;
    float oy = Float.isFinite(offsetY) ? offsetY : -40f;
    float px = Math.max(0f, Math.min(Math.max(0f, sw - pw), ax * (sw - pw) + ox));
    float py = Math.max(0f, Math.min(Math.max(0f, sh - ph), ay * (sh - ph) + oy));
    return new float[] {px, py + ph, s};
  }

  public static float[] computeCardBounds(
      float leftX, float bottomY, float hudScale, double vanillaScale, int vanillaChatWidth,
      int maxFormattedLineW, int visibleCount, int lineHeight, boolean compact, boolean drawCard,
      int screenW, int screenH) {
    float s = Math.max(0.01f, hudScale);
    double vs = vanillaScale > 0.01 ? vanillaScale : 1.0;
    float lineStep = (float) (Math.max(1, lineHeight) * vs) * s;
    float padX = drawCard ? 6f * s : 0f;
    float padY = drawCard ? 4f * s : 0f;
    float rightSafety = drawCard ? 4f * s : 0f;
    float actualTextW = Math.max(0, maxFormattedLineW) * (float) vs * s;
    float vanillaWrapW = (float) (Math.ceil(Math.max(40, vanillaChatWidth) / vs) * vs) * s;
    float neededW = actualTextW + padX * 2f + rightSafety;
    float fullW = Math.max(vanillaWrapW + padX * 2f + rightSafety, neededW);
    float targetW = compact ? Math.max(64f * s, neededW) : fullW;
    float maxScreenW = Math.max(48f, screenW - 4f);
    float boxW = Math.min(targetW, maxScreenW);
    float bx = Math.max(2f, Math.min(leftX, screenW - boxW - 2f));
    float clampedBottomY = Math.max(18f, Math.min(screenH - 2f, bottomY));
    float targetH = Math.max(0, visibleCount) * lineStep + padY * 2f;
    float maxAvailH = Math.max(16f, clampedBottomY - 2f);
    float boxH = Math.min(targetH, maxAvailH);
    float by = Math.max(2f, clampedBottomY - boxH);
    float textOriginX = bx + padX;
    float textOriginBottomY = clampedBottomY - padY;
    return new float[] {bx, by, boxW, boxH, textOriginX, textOriginBottomY};
  }

  public static double[] screenToVanillaChat(
      double mouseX, double mouseY, float textOriginX, float textOriginBottomY, float hudScale,
      float vanillaTextX, float vanillaBottomY) {
    double s = Math.max(0.01, hudScale);
    double vx = vanillaTextX + (mouseX - textOriginX) / s;
    double vy = vanillaBottomY + (mouseY - textOriginBottomY) / s;
    return new double[] {vx, vy};
  }

  public static final class DuplicateTracker {
    private static final class Entry {
      final String normalized;
      int count;
      Object token;

      Entry(String normalized, int count, Object token) {
        this.normalized = normalized;
        this.count = count;
        this.token = token;
      }
    }

    private final int maxWindow;
    private final LinkedList<Entry> recent = new LinkedList<Entry>();

    public DuplicateTracker(int maxWindow) {
      this.maxWindow = Math.max(1, maxWindow);
    }

    public StackMatch record(String rawText, Object newToken) {
      String norm = normalizeMessage(rawText);
      if (norm.isEmpty()) return new StackMatch(1, null);
      for (Iterator<Entry> it = recent.iterator(); it.hasNext(); ) {
        Entry e = it.next();
        if (e.normalized.equals(norm)) {
          it.remove();
          e.count = Math.min(9999, e.count + 1);
          Object prev = e.token;
          e.token = newToken;
          recent.addFirst(e);
          return new StackMatch(e.count, prev);
        }
      }
      recent.addFirst(new Entry(norm, 1, newToken));
      while (recent.size() > maxWindow) {
        recent.removeLast();
      }
      return new StackMatch(1, null);
    }

    public void updateLatestToken(Object token) {
      if (!recent.isEmpty()) {
        recent.getFirst().token = token;
      }
    }

    public void clear() {
      recent.clear();
    }
  }
}
