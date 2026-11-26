package dev.nightclient.ui;

import dev.nightclient.api.Canvas;

final class VanillaWidgets {
  private VanillaWidgets() {}

  static void drawBackdrop(Canvas canvas, int width, int height) {
    canvas.gradientRect(0, 0, width, height, 0xAA101014, 0xCC0D0E12);
  }

  static void drawListWell(Canvas canvas, float x, float y, float w, float h) {
    canvas.rect(x, y, w, h, 0xB8080A0F);
    canvas.rect(x, y, w, 1f, 0xFF000000);
    canvas.rect(x, y + h - 1f, w, 1f, 0xFF000000);
    canvas.rect(x, y, 1f, h, 0xFF000000);
    canvas.rect(x + w - 1f, y, 1f, h, 0xFF000000);
    canvas.gradientRect(x + 1f, y + 1f, w - 2f, 5f, 0x66000000, 0x00000000);
    canvas.gradientRect(x + 1f, y + h - 6f, w - 2f, 5f, 0x00000000, 0x66000000);
  }

  static void drawButton(Canvas canvas, float x, float y, float w, float h, String label,
      boolean hovered, boolean enabled, boolean selected, boolean danger) {
    int border = hovered && enabled ? 0xFFFFFFFF : 0xFF000000;
    int fill;
    if (!enabled) {
      fill = 0xFF2D2E32;
    } else if (danger) {
      fill = hovered ? 0xFFAA2E2E : 0xFF832222;
    } else if (selected) {
      fill = hovered ? 0xFF4F6694 : 0xFF3E527A;
    } else {
      fill = hovered ? 0xFF6B7BA6 : 0xFF5A5C61;
    }
    canvas.rect(x, y, w, h, border);
    if (w > 2f && h > 2f) {
      canvas.rect(x + 1f, y + 1f, w - 2f, h - 2f, fill);
      if (enabled) {
        canvas.rect(x + 1f, y + 1f, w - 2f, 1f, 0x42FFFFFF);
        canvas.rect(x + 1f, y + 1f, 1f, h - 2f, 0x30FFFFFF);
        canvas.rect(x + 1f, y + h - 2f, w - 2f, 1f, 0x58000000);
        canvas.rect(x + w - 2f, y + 1f, 1f, h - 2f, 0x48000000);
      }
    }
    int textColor = !enabled ? 0xFFA0A0A0
        : (danger ? 0xFFFFE4E4 : (hovered ? 0xFFFFFFA0 : 0xFFFFFFFF));
    drawCenteredText(canvas, label, x + 4f, y, w - 8f, h, textColor);
  }

  static void drawSlider(Canvas canvas, float x, float y, float w, float h, String label,
      float fraction, boolean hovered, boolean dragging) {
    float f = Math.max(0f, Math.min(1f, fraction));
    int outer = (hovered || dragging) ? 0xFFFFFFFF : 0xFF000000;
    canvas.rect(x, y, w, h, outer);
    if (w > 2f && h > 2f) {
      canvas.rect(x + 1f, y + 1f, w - 2f, h - 2f, 0xFF2B2D32);
      canvas.rect(x + 1f, y + 1f, w - 2f, 1f, 0x50000000);
      float knobW = 8f;
      float knobX = x + 1f + f * Math.max(0f, w - 2f - knobW);
      int knobFill = (hovered || dragging) ? 0xFF7D90BE : 0xFF686B72;
      canvas.rect(knobX, y + 1f, knobW, h - 2f, 0xFF000000);
      canvas.rect(knobX + 1f, y + 2f, knobW - 2f, h - 4f, knobFill);
      canvas.rect(knobX + 1f, y + 2f, knobW - 2f, 1f, 0x4CFFFFFF);
      canvas.rect(knobX + 1f, y + h - 3f, knobW - 2f, 1f, 0x55000000);
    }
    int textColor = (hovered || dragging) ? 0xFFFFFFA0 : 0xFFFFFFFF;
    drawCenteredText(canvas, label, x + 5f, y, w - 10f, h, textColor);
  }

  static void drawTextField(Canvas canvas, float x, float y, float w, float h, String value,
      String placeholder, boolean focused, double timeSeconds) {
    canvas.rect(x, y, w, h, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
    canvas.rect(x + 1f, y + 1f, Math.max(0f, w - 2f), Math.max(0f, h - 2f), 0xFF08090C);
    String safe = value == null ? "" : value;
    boolean empty = safe.isEmpty();
    String shown = empty ? (placeholder == null ? "" : placeholder) : safe;
    int col = empty ? 0xFF707580 : 0xFFE8ECF2;
    canvas.pushClip(x + 3f, y + 1f, Math.max(0f, w - 6f), Math.max(0f, h - 2f));
    try {
      float textY = y + (h - 9f) * 0.5f + 0.5f;
      float tw = canvas.gameTextWidth(shown, 1f);
      float avail = Math.max(1f, w - 10f);
      float drawX = (!empty && tw > avail) ? (x + 4f - (tw - avail)) : (x + 4f);
      canvas.gameText(shown, drawX, textY, 1f, col, true);
      if (focused && (((int) (timeSeconds * 2.0)) & 1) == 0) {
        float cursorX = empty ? (x + 4f) : (drawX + tw + 1f);
        canvas.rect(cursorX, y + 4f, 1f, Math.max(4f, h - 8f), 0xFFFFFFFF);
      }
    } finally {
      canvas.popClip();
    }
  }

  static void drawScrollbar(
      Canvas canvas, float x, float y, float h, float contentHeight, float viewHeight, float scroll) {
    if (h <= 2f || contentHeight <= viewHeight + 1f) return;
    canvas.rect(x, y, 5f, h, 0xFF000000);
    float ratio = Math.max(0.12f, Math.min(1f, viewHeight / contentHeight));
    float thumbH = Math.min(h, Math.max(Math.min(18f, h), h * ratio));
    float maxScroll = Math.max(1f, contentHeight - viewHeight);
    float frac = Math.max(0f, Math.min(1f, scroll / maxScroll));
    float thumbY = y + frac * Math.max(0f, h - thumbH);
    canvas.rect(x + 1f, thumbY, 3f, thumbH, 0xFF808080);
    canvas.rect(x + 1f, thumbY, 2f, Math.max(1f, thumbH - 1f), 0xFFC0C0C0);
  }

  static void drawCenteredText(
      Canvas canvas, String text, float x, float y, float w, float h, int argb) {
    String fitted = fitText(canvas, text == null ? "" : text, Math.max(8f, w));
    float tw = canvas.gameTextWidth(fitted, 1f);
    float tx = x + (w - tw) * 0.5f;
    float ty = y + (h - 9f) * 0.5f + 0.5f;
    canvas.gameText(fitted, tx, ty, 1f, argb, true);
  }

  static String fitText(Canvas canvas, String text, float maxWidth) {
    if (text == null || text.isEmpty()) return "";
    if (canvas.gameTextWidth(text, 1f) <= maxWidth) return text;
    String ellipsis = "...";
    float ew = canvas.gameTextWidth(ellipsis, 1f);
    if (maxWidth <= ew) return "";
    int end = text.length();
    while (end > 0 && canvas.gameTextWidth(text.substring(0, end), 1f) + ew > maxWidth) {
      end = (end >= 2 && Character.isLowSurrogate(text.charAt(end - 1))
          && Character.isHighSurrogate(text.charAt(end - 2))) ? end - 2 : end - 1;
    }
    return end == 0 ? ellipsis : (text.substring(0, end) + ellipsis);
  }
}
