package dev.nightclient.renderer;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.config.Theme;
import dev.nightclient.i18n.I18n;
import java.util.List;

public final class TooltipOverlayRenderer {
  public static final class ContainerPeek {
    public final String title;
    public final String containerItemId;
    public final int containerCacheSlot;
    public final int accentColor;
    public final boolean bundle;
    public final boolean compact;
    public final boolean locked;
    public final int usedSlots;
    public final int maxSlots;
    public final float bundleFullness;
    public final List<SlotEntry> entries;

    public ContainerPeek(String title, String containerItemId, int containerCacheSlot,
        int accentColor, boolean bundle, boolean compact, boolean locked, int usedSlots,
        int maxSlots, float bundleFullness, List<SlotEntry> entries) {
      this.title = title == null ? "" : title;
      this.containerItemId = containerItemId == null ? "" : containerItemId;
      this.containerCacheSlot = containerCacheSlot;
      this.accentColor = accentColor;
      this.bundle = bundle;
      this.compact = compact;
      this.locked = locked;
      this.usedSlots = usedSlots;
      this.maxSlots = maxSlots;
      this.bundleFullness = bundleFullness;
      this.entries = entries;
    }
  }

  public static final class SlotEntry {
    public final int gridSlot;
    public final InventorySlot slot;
    public final String countText;

    public SlotEntry(int gridSlot, InventorySlot slot, String countText) {
      this.gridSlot = gridSlot;
      this.slot = slot;
      this.countText = countText == null ? "" : countText;
    }
  }

  private TooltipOverlayRenderer() {}

  public static void renderTooltipBackdrop(Canvas canvas, Theme theme, float x, float y, float w,
      float h, int accentArgb, boolean hwStyle, String badgeText) {
    if (canvas == null || theme == null || w <= 0 || h <= 0) return;
    float padX = 5f;
    float padY = 5f;
    float bx = x - padX;
    float by = y - padY;
    float bw = w + padX * 2f;
    float bh = h + padY * 2f;
    float radius = Math.max(4f, Math.min(8f, theme.radius * 0.65f));

    int accent = (accentArgb >>> 24) != 0 ? accentArgb : theme.accent;
    int bgTop = hwStyle ? Colors.mix(0xee0c0e16, Colors.alpha(accent, 0.14f), 0.25f)
                        : Colors.alpha(theme.background, 0.90f);
    int bgBot = hwStyle ? 0xf208090f : Colors.alpha(theme.surface, 0.92f);

    canvas.roundedRect(bx - 1.5f, by + 1.5f, bw + 3f, bh + 2f, radius + 1.5f, 0x45000000);
    canvas.gradientRoundedRectCorners(
        bx, by, bw, bh, radius, radius, radius, radius, bgTop, bgTop, bgBot, bgBot);
    int topGlow = Colors.alpha(hwStyle ? accent : theme.text, hwStyle ? 0.34f : 0.12f);
    canvas.roundedRect(bx + radius * 0.7f, by + 0.5f, Math.max(0f, bw - radius * 1.4f), 1.0f, 0.5f,
        topGlow);
    int border = hwStyle ? Colors.alpha(accent, 0.72f) : Colors.alpha(theme.border, 0.85f);
    canvas.strokeRoundedRect(bx, by, bw, bh, radius, hwStyle ? 1.0f : 0.8f, border);

    if (hwStyle && badgeText != null && !badgeText.isEmpty() && bw >= 70f) {
      float fontSize = 6.2f;
      float tw = canvas.textWidth(badgeText, fontSize, true);
      float pillW = tw + 8f;
      float pillH = 10.5f;
      float px = bx + bw - pillW - 5f;
      float py = Math.max(2f, by - pillH * 0.52f);
      int pillBg = Colors.mix(0xf010121c, accent, 0.28f);
      canvas.roundedRect(px, py, pillW, pillH, 4f, pillBg);
      canvas.strokeRoundedRect(px, py, pillW, pillH, 4f, 0.75f, Colors.alpha(accent, 0.85f));
      canvas.text(badgeText, px + 4f, py + 2.1f, fontSize, Colors.mix(0xffffffff, accent, 0.22f),
          true);
    }
  }

  public static void renderContainerPeek(Canvas canvas, Theme theme, ContainerPeek peek,
      float anchorX, float anchorY, float anchorW, float anchorH, int screenW, int screenH,
      boolean ru) {
    renderContainerPeek(canvas, theme, peek, anchorX, anchorY, anchorW, anchorH, screenW, screenH,
        ru ? "ru" : "en", -1000f, -1000f, null);
  }

  public static int renderContainerPeek(Canvas canvas, Theme theme, ContainerPeek peek,
      float anchorX, float anchorY, float anchorW, float anchorH, int screenW, int screenH,
      boolean ru, float mouseX, float mouseY, float[] boundsOut) {
    return renderContainerPeek(canvas, theme, peek, anchorX, anchorY, anchorW, anchorH, screenW,
        screenH, ru ? "ru" : "en", mouseX, mouseY, boundsOut);
  }

  public static int renderContainerPeek(Canvas canvas, Theme theme, ContainerPeek peek,
      float anchorX, float anchorY, float anchorW, float anchorH, int screenW, int screenH,
      String language, float mouseX, float mouseY, float[] boundsOut) {
    if (canvas == null || theme == null || peek == null) return -1;
    String lang = I18n.language(language);
    int entryCount = peek.entries == null ? 0 : peek.entries.size();
    boolean empty = entryCount == 0;

    int cols;
    int rows;
    if (empty) {
      cols = 6;
      rows = 1;
    } else if (!peek.compact && !peek.bundle) {
      cols = 9;
      rows = Math.max(1, (peek.maxSlots + 8) / 9);
    } else {
      cols = Math.max(4, Math.min(9, entryCount));
      rows = Math.max(1, (entryCount + cols - 1) / cols);
    }

    float slotSize = 18f;
    float gap = 2f;
    float pad = 7f;
    float headerH = 18f;
    float footerH = 11f;
    float gridW = cols * slotSize + Math.max(0, cols - 1) * gap;
    float gridH = empty ? 20f : (rows * slotSize + Math.max(0, rows - 1) * gap);

    String modeLabel = peek.bundle
        ? (Math.round(peek.bundleFullness * 100f) + "%")
        : I18n.text(lang, peek.compact ? "tooltips.peek.compact" : "tooltips.peek.full");
    String countLabel = peek.bundle ? "" : (peek.usedSlots + "/" + peek.maxSlots);
    float titleMinW = 118f;
    float cardW = Math.max(titleMinW, gridW + pad * 2f);
    float cardH = pad + headerH + 4f + gridH + 4f + footerH + 3f;

    float gapFromTooltip = 7f;
    float cx = anchorX - 5f;
    float cy = anchorY - 5f - cardH - gapFromTooltip;
    if (cy < 4f) {
      float belowY = anchorY + anchorH + 5f + gapFromTooltip;
      if (belowY + cardH <= screenH - 4f) {
        cy = belowY;
      } else {
        float rightX = anchorX + anchorW + 5f + gapFromTooltip;
        if (rightX + cardW <= screenW - 4f) {
          cx = rightX;
          cy = Math.max(4f, Math.min(screenH - cardH - 4f, anchorY - 5f));
        } else {
          cx = Math.max(4f, anchorX - 5f - cardW - gapFromTooltip);
          cy = Math.max(4f, Math.min(screenH - cardH - 4f, anchorY - 5f));
        }
      }
    }
    cx = Math.max(4f, Math.min(screenW - cardW - 4f, cx));
    cy = Math.max(4f, Math.min(screenH - cardH - 4f, cy));
    if (boundsOut != null && boundsOut.length >= 4) {
      boundsOut[0] = cx;
      boundsOut[1] = cy;
      boundsOut[2] = cardW;
      boundsOut[3] = cardH;
    }

    int accent = (peek.accentColor >>> 24) != 0 ? peek.accentColor : theme.accent;
    float radius = 7.5f;
    int bgTop = Colors.mix(0xf00d1018, Colors.alpha(accent, 0.20f), 0.24f);
    int bgBot = 0xf4080a10;

    canvas.roundedRect(cx - 1.5f, cy + 2f, cardW + 3f, cardH + 2f, radius + 1.5f, 0x4a000000);
    canvas.gradientRoundedRectCorners(
        cx, cy, cardW, cardH, radius, radius, radius, radius, bgTop, bgTop, bgBot, bgBot);
    canvas.roundedRect(
        cx + radius, cy + 0.5f, Math.max(0f, cardW - radius * 2f), 1.0f, 0.5f,
        Colors.alpha(accent, 0.45f));
    canvas.strokeRoundedRect(cx, cy, cardW, cardH, radius, 1.0f, Colors.alpha(accent, 0.72f));

    float hx = cx + pad;
    float hy = cy + pad;
    if (peek.containerCacheSlot >= 300 && peek.containerCacheSlot <= 355) {
      InventorySlot iconSlot =
          new InventorySlot(peek.containerCacheSlot, peek.containerItemId, peek.title, 1, 0, 0, false);
      canvas.inventoryItem(iconSlot, hx, hy + 1f, 13f, 1f);
    } else if (!peek.containerItemId.isEmpty()) {
      canvas.item(peek.containerItemId, hx, hy + 1f, 13f, 1f);
    }

    float badgeRight = cx + cardW - pad;
    float badgeFont = 6.2f;
    if (!modeLabel.isEmpty()) {
      String pillText = peek.locked ? ("★ " + modeLabel) : modeLabel;
      float pw = canvas.textWidth(pillText, badgeFont, true) + 7f;
      float px = badgeRight - pw;
      canvas.roundedRect(px, hy + 2f, pw, 11f, 3.5f, Colors.alpha(accent, 0.20f));
      canvas.strokeRoundedRect(px, hy + 2f, pw, 11f, 3.5f, 0.65f, Colors.alpha(accent, 0.55f));
      canvas.text(pillText, px + 3.5f, hy + 4.3f, badgeFont, Colors.mix(0xffffffff, accent, 0.25f),
          true);
      badgeRight = px - 4f;
    }
    if (!countLabel.isEmpty()) {
      float cw = canvas.textWidth(countLabel, badgeFont, false);
      canvas.text(countLabel, badgeRight - cw, hy + 4.3f, badgeFont, 0xffaab3c5, false);
      badgeRight -= cw + 5f;
    }

    float titleX = hx + 16f;
    float titleMaxW = Math.max(16f, badgeRight - titleX);
    String title = truncate(canvas, peek.title, 7.6f, true, titleMaxW);
    canvas.text(title, titleX, hy + 3.4f, 7.6f, 0xfff2f5fc, true);

    float gridStartY = hy + headerH + 4f;
    if (peek.bundle) {
      float barY = hy + headerH - 1f;
      float barW = cardW - pad * 2f;
      canvas.roundedRect(hx, barY, barW, 2.2f, 1.1f, 0x35ffffff);
      float fillW = barW * Math.max(0f, Math.min(1f, peek.bundleFullness));
      if (fillW > 0.5f) {
        int barColor = peek.bundleFullness >= 0.99f ? 0xffff6b6b : accent;
        canvas.roundedRect(hx, barY, fillW, 2.2f, 1.1f, barColor);
      }
      gridStartY = barY + 5f;
    } else {
      canvas.rect(hx, hy + headerH, cardW - pad * 2f, 0.55f, Colors.alpha(accent, 0.24f));
    }

    int hoveredCacheSlot = -1;
    float gridStartX = cx + (cardW - gridW) * 0.5f;
    if (empty) {
      String emptyText = I18n.text(lang, "tooltips.peek.empty");
      float ew = canvas.textWidth(emptyText, 7.2f, false);
      canvas.text(emptyText, cx + (cardW - ew) * 0.5f, gridStartY + 5.5f, 7.2f, 0xff8b94a8, false);
    } else {
      int totalCells = (!peek.compact && !peek.bundle) ? peek.maxSlots : entryCount;
      for (int i = 0; i < totalCells; i++) {
        int col = i % cols;
        int row = i / cols;
        float sx = gridStartX + col * (slotSize + gap);
        float sy = gridStartY + row * (slotSize + gap);
        canvas.roundedRect(sx, sy, slotSize, slotSize, 3.2f, 0x28ffffff);
        canvas.strokeRoundedRect(sx, sy, slotSize, slotSize, 3.2f, 0.5f, 0x1effffff);
      }
      for (int i = 0; i < entryCount; i++) {
        SlotEntry entry = peek.entries.get(i);
        if (entry == null || entry.slot == null || entry.slot.empty()) continue;
        int cell = (!peek.compact && !peek.bundle) ? entry.gridSlot : i;
        if (cell < 0 || cell >= cols * rows) continue;
        int col = cell % cols;
        int row = cell / cols;
        float sx = gridStartX + col * (slotSize + gap);
        float sy = gridStartY + row * (slotSize + gap);

        boolean slotHovered = peek.locked && mouseX >= sx && mouseX <= sx + slotSize
            && mouseY >= sy && mouseY <= sy + slotSize;
        if (slotHovered) {
          hoveredCacheSlot = entry.slot.slot;
          canvas.roundedRect(sx, sy, slotSize, slotSize, 3.2f, Colors.alpha(accent, 0.34f));
          canvas.strokeRoundedRect(
              sx, sy, slotSize, slotSize, 3.2f, 0.9f, Colors.alpha(0xffffffff, 0.88f));
        } else if (entry.slot.enchanted) {
          canvas.roundedRect(sx + 0.5f, sy + 0.5f, slotSize - 1f, slotSize - 1f, 2.8f,
              Colors.alpha(accent, 0.14f));
        }
        canvas.inventoryItem(entry.slot, sx + 1.5f, sy + 1.5f, 15f, 1f);

        if (entry.slot.maxDamage > 0 && entry.slot.damage > 0) {
          float frac = Math.max(0f, Math.min(1f, 1f - entry.slot.damage / (float) entry.slot.maxDamage));
          int dColor = frac >= 0.6f ? 0xff4ade80 : frac >= 0.25f ? 0xfffbbf24 : 0xfff87171;
          canvas.rect(sx + 2.5f, sy + slotSize - 2.6f, slotSize - 5f, 1.5f, 0xc8000000);
          canvas.rect(sx + 2.5f, sy + slotSize - 2.6f, (slotSize - 5f) * frac, 1.5f, dColor);
        }

        if (!entry.countText.isEmpty()) {
          float cf = 6.2f;
          float tw = canvas.textWidth(entry.countText, cf, true);
          float tx = sx + slotSize - tw - 1.2f;
          float ty = sy + slotSize - 7.4f;
          canvas.text(entry.countText, tx + 0.6f, ty + 0.6f, cf, 0xd0000000, true);
          canvas.text(entry.countText, tx, ty, cf, 0xffffffff, true);
        }
      }
    }

    float fy = gridStartY + gridH + 4f;
    String hint;
    if (peek.locked) {
      hint = I18n.text(lang, "tooltips.peek.hint_locked");
    } else if (peek.bundle) {
      hint = I18n.text(lang, "tooltips.peek.hint_bundle");
    } else {
      hint = I18n.text(lang, "tooltips.peek.hint_shulker");
    }
    canvas.text(hint, hx, fy + 1f, 5.8f, 0xff7f889c, false);
    return hoveredCacheSlot;
  }

  private static String truncate(
      Canvas canvas, String text, float size, boolean bold, float maxWidth) {
    if (text == null || text.isEmpty()) return "";
    if (canvas.textWidth(text, size, bold) <= maxWidth) return text;
    String ellipsis = "…";
    float ellW = canvas.textWidth(ellipsis, size, bold);
    int end = text.length();
    while (end > 1 && canvas.textWidth(text.substring(0, end), size, bold) + ellW > maxWidth) {
      end--;
    }
    return text.substring(0, Math.max(1, end)) + ellipsis;
  }
}
