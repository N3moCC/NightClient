package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.config.DisplayProtection;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import dev.nightclient.i18n.I18n;
import dev.nightclient.renderer.Icons;
import dev.nightclient.renderer.SurfacePainter;
import dev.nightclient.renderer.player.PlayerFacePainter;
import java.util.Collections;
import java.util.List;

public final class AccountOverlayRenderer {
  private static final float TITLE_PILL_HEIGHT = 24f;
  private static final float MULTI_PILL_HEIGHT = 20f;
  private static final float FACE_SIZE = 16f;
  private static final float NAME_FONT = 8.0f;
  private static final float BADGE_FONT = 6.0f;
  private static final String FONT_MEDIUM = "studio-sans-medium";
  private static final String FONT_REGULAR = "studio-sans";

  private float lastX, lastY, lastW, lastH;
  private boolean visible;

  public boolean isVisible() {
    return visible;
  }

  public void hide() {
    visible = false;
  }

  public float[] lastBounds() {
    return visible ? new float[] {lastX, lastY, lastW, lastH} : null;
  }

  public boolean hitTest(float mouseX, float mouseY) {
    return visible && mouseX >= lastX && mouseY >= lastY && mouseX <= lastX + lastW
        && mouseY <= lastY + lastH;
  }

  public void render(Canvas canvas, int viewportWidth, int viewportHeight, float mouseX, float mouseY,
      Settings settings, String rawNickname, boolean launcherSession, int[] face64,
      boolean isTitleScreen) {
    render(canvas, viewportWidth, viewportHeight, mouseX, mouseY, settings, rawNickname,
        launcherSession, face64, isTitleScreen, Collections.<int[]>emptyList(), false);
  }

  public void render(Canvas canvas, int viewportWidth, int viewportHeight, float mouseX, float mouseY,
      Settings settings, String rawNickname, boolean launcherSession, int[] face64,
      boolean isTitleScreen, List<int[]> obstacles, boolean iasLoaded) {
    visible = false;
    if (canvas == null || settings == null || settings.accountSwitcher == null) return;
    Settings.AccountSwitcher cfg = settings.accountSwitcher;
    if (!cfg.enabled) return;
    if (isTitleScreen && !cfg.titleScreen) return;
    if (!isTitleScreen && !cfg.multiplayerScreen) return;
    if (viewportWidth < 180 || viewportHeight < 80) return;

    String lang = settings.language;
    String name = rawNickname == null || rawNickname.trim().isEmpty() ? "Player" : rawNickname.trim();
    if (cfg.hideAltsInProtect && settings.protect != null && settings.protect.enabled
        && settings.protect.hideNickname) {
      name = DisplayProtection.maskNickname(name, name, settings.protect);
    }
    String badge = I18n.text(lang, launcherSession ? "accounts.launcher_badge" : "accounts.offline_badge");

    float nameW = canvas.textWidthFamily(FONT_MEDIUM, name, NAME_FONT, false, 0f);
    float badgeW = canvas.textWidthFamily(FONT_REGULAR, badge, BADGE_FONT, false, 0f);
    float badgePillW = badgeW + 9f;

    float pillH = isTitleScreen ? TITLE_PILL_HEIGHT : MULTI_PILL_HEIGHT;
    boolean compactHeader = !isTitleScreen;
    float maxDesiredW = compactHeader
        ? Math.max(96f, Math.min(148f, viewportWidth * 0.5f - AccountButtonLayout.TITLE_HALF_CLEARANCE - 12f))
        : Math.min(200f, viewportWidth - 20f);
    float desiredW = compactHeader
        ? Math.max(82f, Math.min(maxDesiredW, 6f + FACE_SIZE + 6f + nameW + 18f))
        : Math.max(114f, Math.min(maxDesiredW, 7f + FACE_SIZE + 7f + nameW + 7f + badgePillW + 16f));

    String prefPos = isTitleScreen ? cfg.titlePosition : cfg.multiplayerPosition;
    AccountButtonLayout.Placement placed = AccountButtonLayout.place(viewportWidth, viewportHeight,
        Math.round(desiredW), Math.round(pillH), isTitleScreen, false, prefPos, obstacles);

    float x = placed.x;
    float y = placed.y;
    float pillW = placed.width;
    pillH = placed.height;

    lastX = x;
    lastY = y;
    lastW = pillW;
    lastH = pillH;
    visible = true;

    boolean hovered = mouseX >= x && mouseY >= y && mouseX <= x + pillW && mouseY <= y + pillH;
    Theme theme = settings.theme != null ? settings.theme : Theme.defaults();
    float radius = Math.min(9f, Math.max(5f, theme.radius * 0.65f));
    int surface = Colors.alpha(theme.surface, hovered ? 0.92f : 0.80f);
    int border = hovered ? Colors.alpha(theme.accent, 0.78f) : Colors.alpha(theme.border, 0.85f);

    SurfacePainter.fillRounded(canvas, x, y, pillW, pillH, radius, surface);
    canvas.strokeRoundedRect(x, y, pillW, pillH, radius, hovered ? 0.95f : 0.7f, border);

    int[] face = face64 != null && face64.length >= 64 ? face64 : PlayerFacePainter.fallbackFace(name);
    int statusColor = launcherSession ? 0xff34d399 : 0xfff59e0b;

    if (pillW <= 34f) {
      float faceX = (float) Math.floor(x + (pillW - FACE_SIZE) * 0.5f);
      float faceY = (float) Math.floor(y + (pillH - FACE_SIZE) * 0.5f);
      PlayerFacePainter.paint(canvas, face, faceX, faceY, FACE_SIZE, 1f);
      canvas.strokeRoundedRect(faceX - 1f, faceY - 1f, FACE_SIZE + 2f, FACE_SIZE + 2f, 2.5f, 0.65f,
          Colors.alpha(theme.text, 0.24f));
      canvas.circle(faceX + FACE_SIZE - 1f, faceY + FACE_SIZE - 1f, 2.6f,
          Colors.alpha(theme.surface, 0.95f));
      canvas.circle(faceX + FACE_SIZE - 1f, faceY + FACE_SIZE - 1f, 1.7f, statusColor);
      return;
    }

    float faceInsetX = Math.max(4f, (pillH - FACE_SIZE) * 0.5f);
    float faceX = (float) Math.floor(x + faceInsetX);
    float faceY = (float) Math.floor(y + (pillH - FACE_SIZE) * 0.5f);
    PlayerFacePainter.paint(canvas, face, faceX, faceY, FACE_SIZE, 1f);
    canvas.strokeRoundedRect(faceX - 1f, faceY - 1f, FACE_SIZE + 2f, FACE_SIZE + 2f, 2.5f, 0.65f,
        Colors.alpha(theme.text, 0.24f));

    canvas.circle(faceX + FACE_SIZE - 1f, faceY + FACE_SIZE - 1f, 2.8f,
        Colors.alpha(theme.surface, 0.95f));
    canvas.circle(faceX + FACE_SIZE - 1f, faceY + FACE_SIZE - 1f, 1.9f, statusColor);

    boolean showBadge = !compactHeader && pillW >= 122f;
    float textX = faceX + FACE_SIZE + 7f;
    float rightReserve = showBadge ? (badgePillW + 20f) : 16f;
    float maxNameW = Math.max(22f, pillW - (textX - x) - rightReserve);
    String clipped = clipText(canvas, name, maxNameW);
    float textY = y + (pillH - NAME_FONT) * 0.5f - 0.5f;
    canvas.textFamily(FONT_MEDIUM, clipped, textX, textY, NAME_FONT, theme.text, false, 0f);

    if (showBadge) {
      float badgeX = x + pillW - 16f - badgePillW;
      float badgeH = 12.5f;
      float badgeY = y + (pillH - badgeH) * 0.5f;
      int badgeBg = Colors.alpha(statusColor, launcherSession ? 0.18f : 0.16f);
      int badgeFg = launcherSession ? 0xff6ee7b7 : 0xfffbbf24;
      SurfacePainter.fillRounded(canvas, badgeX, badgeY, badgePillW, badgeH, 4.5f, badgeBg);
      canvas.textFamily(FONT_REGULAR, badge, badgeX + 4.5f,
          badgeY + (badgeH - BADGE_FONT) * 0.5f - 0.3f, BADGE_FONT, badgeFg, false, 0f);
    }

    Icons.draw(canvas, "chevron-down", x + pillW - 12f, y + (pillH - 6.5f) * 0.5f, 6.5f,
        hovered ? theme.accent : theme.muted, 1.25f);
  }

  private static String clipText(Canvas canvas, String text, float maxWidth) {
    if (canvas.textWidthFamily(FONT_MEDIUM, text, NAME_FONT, false, 0f) <= maxWidth) return text;
    String ellipsis = "...";
    float ellW = canvas.textWidthFamily(FONT_MEDIUM, ellipsis, NAME_FONT, false, 0f);
    int end = text.length();
    while (end > 1
        && canvas.textWidthFamily(FONT_MEDIUM, text.substring(0, end), NAME_FONT, false, 0f) + ellW
            > maxWidth) {
      end--;
    }
    return text.substring(0, end) + ellipsis;
  }
}
