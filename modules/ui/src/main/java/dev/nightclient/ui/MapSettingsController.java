package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.Keys;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import dev.nightclient.i18n.I18n;
import dev.nightclient.renderer.Icons;
import dev.nightclient.renderer.SurfacePainter;
import dev.nightclient.renderer.map.RegionCacheManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MapSettingsController {
  public interface Host {
    RegionCacheManager mapCache();

    String keyBindingLabel(String id);

    void setKeyBinding(String id, String nativeKeyName);

    void clearCurrentWorldCache();

    void clearAllMapCache();

    void openWorldMap();

    void openWaypointsMenu();

    void openHudEditor();

    void checkpoint();

    void changed();

    void close();
  }

  private static final String FONT_MEDIUM = "studio-sans-medium";
  private static final String FONT_REGULAR = "studio-sans";

  private static final String[] SHAPES = {
      "square", "sharp", "circle", "diamond", "hexagon", "octagon"
  };
  private static final String[] RELIEF_STYLES = {
      "classic", "vivid", "contour", "flat"
  };
  private static final String[] ARROW_STYLES = {
      "classic", "modern", "pointer", "head", "dot"
  };
  private static final int[] ARROW_COLORS = {
      0xFFFFFFFF, 0xFFEF4444, 0xFF38BDF8, 0xFF4ADE80, 0xFFFACC15, 0xFFA855F7, 0xFFF97316
  };
  private static final String[] ENLARGE_MODES = {"hold", "toggle"};
  private static final String[] ITEM_STYLES = {"icons", "dots"};
  private static final String[] MOB_STYLES = {"icons", "dots"};

  private static final int KIND_TOGGLE = 0;
  private static final int KIND_CHOICE = 1;
  private static final int KIND_SLIDER = 2;
  private static final int KIND_COLOR = 3;
  private static final int KIND_KEY = 4;
  private static final int KIND_CACHE_PAIR = 5;

  private static final class Entry {
    final String id;
    final String label;
    final int kind;
    final boolean boolVal;
    final String valueText;
    final int colorVal;
    final float sliderFrac;
    final float min, max, step;

    Entry(String id, String label, int kind, boolean boolVal, String valueText, int colorVal,
        float value, float min, float max, float step) {
      this.id = id;
      this.label = label;
      this.kind = kind;
      this.boolVal = boolVal;
      this.valueText = valueText == null ? "" : valueText;
      this.colorVal = colorVal;
      this.min = min;
      this.max = max;
      this.step = step;
      this.sliderFrac = max > min ? Math.max(0f, Math.min(1f, (value - min) / (max - min))) : 0f;
    }

    float height() {
      return kind == KIND_SLIDER ? 30f : 24f;
    }
  }

  private static final class Hit {
    final String action;
    final float x, y, w, h;
    final Entry entry;

    Hit(String action, float x, float y, float w, float h, Entry entry) {
      this.action = action;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.entry = entry;
    }

    boolean contains(float mx, float my) {
      return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }
  }

  private final Settings settings;
  private final Host host;
  private final List<Hit> hits = new ArrayList<Hit>();
  private boolean open;
  private int tab;
  private float scroll;
  private float maxScroll;
  private float modalX, modalY, modalW, modalH;
  private String draggingSliderId = "";
  private float dragX, dragW;
  private float dragMin, dragMax, dragStep;
  private String capturingBind = "";
  private boolean confirmClearWorld;
  private boolean confirmClearAll;
  private String statusMessage = "";
  private double statusUntil;
  private double lastTime;

  public MapSettingsController(Settings settings, Host host) {
    this.settings = settings;
    this.host = host;
  }

  public boolean isOpen() {
    return open;
  }

  public boolean isBindingCapture() {
    return !capturingBind.isEmpty();
  }

  public int activeTab() {
    return tab;
  }

  public void onOpen() {
    open = true;
    scroll = 0f;
    draggingSliderId = "";
    capturingBind = "";
    confirmClearWorld = false;
    confirmClearAll = false;
    statusMessage = "";
  }

  public void onClose() {
    open = false;
    draggingSliderId = "";
    capturingBind = "";
    confirmClearWorld = false;
    confirmClearAll = false;
  }

  public boolean captureBinding(int normalizedKey, String nativeKeyName) {
    if (capturingBind.isEmpty()) return false;
    String id = capturingBind;
    capturingBind = "";
    String target = (normalizedKey == Keys.ESCAPE || normalizedKey == Keys.BACKSPACE
        || normalizedKey == Keys.DELETE) ? "key.keyboard.unknown" : nativeKeyName;
    if (normalizedKey != Keys.ESCAPE) {
      host.setKeyBinding(id, target);
    }
    return true;
  }

  public void render(
      Canvas canvas, int width, int height, float mouseX, float mouseY, double timeSeconds) {
    if (canvas == null || width <= 0 || height <= 0) return;
    lastTime = timeSeconds;
    hits.clear();

    String lang = I18n.language(settings.language);
    Theme theme = settings.theme != null ? settings.theme : Theme.defaults();
    int bg = theme.background | 0xFF000000;
    int surface = theme.surface | 0xFF000000;
    int raised = theme.raised | 0xFF000000;
    int text = theme.text | 0xFF000000;
    int muted = theme.muted | 0xFF000000;
    int accent = theme.accent | 0xFF000000;
    int onAccent = bg;
    int border = theme.border;
    int subtleBorder = Colors.alpha(text, 0.06f);

    canvas.rect(0f, 0f, width, height, 0x78000000);

    modalW = Math.min(364f, Math.max(220f, width - 20f));
    modalH = Math.min(298f, Math.max(180f, height - 20f));
    modalX = (width - modalW) * 0.5f;
    modalY = (height - modalH) * 0.5f;
    float radius = 16f;
    float pad = 13f;

    if (SurfacePainter.usesBackdrop(theme)) {
      canvas.backdrop(modalX, modalY, modalW, modalH, radius, Math.max(8f, theme.blurRadius),
          theme.blurQuality, 0.95f);
    }
    SurfacePainter.fillRounded(canvas, modalX, modalY, modalW, modalH, radius,
        Colors.alpha(surface, 0.95f));
    canvas.strokeRoundedRect(modalX, modalY, modalW, modalH, radius, 0.7f, border);

    float contentX = modalX + pad;
    float contentW = modalW - pad * 2f;

    float headerY = modalY + pad;
    float headerH = 18f;
    Icons.draw(canvas, "minimap", contentX, headerY + (headerH - 9f) * 0.5f, 9f, accent, 1.35f);
    String title = I18n.text(lang, "mapSettings.title");
    canvas.textFamily(FONT_MEDIUM, title, contentX + 14f, headerY + (headerH - 8.2f) * 0.5f - 0.3f,
        8.2f, text, false, 0f);

    float closeSize = 18f;
    float closeX = contentX + contentW - closeSize;
    boolean closeHov = mouseX >= closeX && mouseX <= closeX + closeSize
        && mouseY >= headerY && mouseY <= headerY + closeSize;
    if (closeHov) {
      SurfacePainter.fillRounded(canvas, closeX, headerY, closeSize, closeSize, 6f, raised);
    }
    Icons.draw(canvas, "close", closeX + (closeSize - 6f) * 0.5f,
        headerY + (closeSize - 6f) * 0.5f, 6f, closeHov ? text : muted, 1.35f);
    hits.add(new Hit("done", closeX, headerY, closeSize, closeSize, null));

    float tabY = headerY + headerH + 7f;
    float tabH = 20f;
    SurfacePainter.fillRounded(canvas, contentX, tabY, contentW, tabH, 6f, bg);
    canvas.strokeRoundedRect(contentX, tabY, contentW, tabH, 6f, 0.5f, border);

    String[] tabLabels = {
        I18n.text(lang, "mapSettings.tab.general"),
        I18n.text(lang, "mapSettings.tab.relief"),
        I18n.text(lang, "mapSettings.tab.radar"),
        I18n.text(lang, "mapSettings.tab.world"),
        I18n.text(lang, "mapSettings.tab.keys")
    };
    float tabGap = 2f;
    float tabW = (contentW - 4f - tabGap * (tabLabels.length - 1)) / tabLabels.length;
    for (int i = 0; i < tabLabels.length; i++) {
      float tx = contentX + 2f + i * (tabW + tabGap);
      float ty = tabY + 2f;
      float th = tabH - 4f;
      boolean active = i == tab;
      boolean hov = mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + th;
      if (active) {
        SurfacePainter.fillRounded(canvas, tx, ty, tabW, th, 4f, accent);
      } else if (hov) {
        SurfacePainter.fillRounded(canvas, tx, ty, tabW, th, 4f, Colors.alpha(text, 0.06f));
      }
      String label = clip(canvas, tabLabels[i], FONT_MEDIUM, 6.2f, tabW - 6f);
      float tw = canvas.textWidthFamily(FONT_MEDIUM, label, 6.2f, false, 0f);
      canvas.textFamily(FONT_MEDIUM, label, tx + (tabW - tw) * 0.5f,
          ty + (th - 6.2f) * 0.5f - 0.3f, 6.2f, active ? onAccent : (hov ? text : muted), false, 0f);
      hits.add(new Hit("tab:" + i, tx, ty, tabW, th, null));
    }

    float footerH = 23f;
    float footerY = modalY + modalH - pad - footerH;
    float listTop = tabY + tabH + 7f;
    float listH = Math.max(40f, footerY - listTop - 7f);

    List<Entry> entries = buildEntries(lang);
    float rowGap = 4f;
    float contentH = 0f;
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) contentH += rowGap;
      contentH += entries.get(i).height();
    }
    maxScroll = Math.max(0f, contentH - listH);
    if (scroll > maxScroll) scroll = maxScroll;
    if (scroll < 0f) scroll = 0f;

    boolean hasScroll = maxScroll > 0.5f;
    float rowW = hasScroll ? (contentW - 6f) : contentW;

    canvas.pushClip(contentX, listTop, contentW, listH);
    try {
      float curY = listTop - scroll;
      boolean inList = mouseX >= contentX && mouseX <= contentX + contentW
          && mouseY >= listTop && mouseY <= listTop + listH;
      for (int i = 0; i < entries.size(); i++) {
        Entry e = entries.get(i);
        float rh = e.height();
        if (curY + rh >= listTop && curY <= listTop + listH) {
          if (e.kind == KIND_CACHE_PAIR) {
            renderCachePair(canvas, lang, contentX, curY, rowW, rh, listTop, listH,
                inList, mouseX, mouseY, raised);
          } else {
            boolean hov = inList && mouseX >= contentX && mouseX <= contentX + rowW
                && mouseY >= curY && mouseY <= curY + rh;
            renderRow(canvas, lang, e, contentX, curY, rowW, rh, listTop, listH,
                hov, bg, raised, text, muted, accent, onAccent, border, subtleBorder);
          }
        }
        curY += rh + rowGap;
      }
    } finally {
      canvas.popClip();
    }

    if (hasScroll) {
      float trackX = contentX + contentW - 3f;
      float thumbRatio = Math.max(0.14f, Math.min(1f, listH / Math.max(1f, contentH)));
      float thumbH = Math.max(16f, listH * thumbRatio);
      float frac = scroll / Math.max(1f, maxScroll);
      float thumbY = listTop + frac * Math.max(0f, listH - thumbH);
      SurfacePainter.fillRounded(canvas, trackX, thumbY, 2.5f, thumbH, 1.25f,
          Colors.alpha(text, 0.22f));
    }

    if (!statusMessage.isEmpty() && timeSeconds <= statusUntil) {
      float tw = canvas.textWidthFamily(FONT_MEDIUM, statusMessage, 6.3f, false, 0f);
      float toastW = Math.min(contentW - 16f, tw + 22f);
      float toastH = 18f;
      float toastX = modalX + (modalW - toastW) * 0.5f;
      float toastY = footerY - toastH - 6f;
      SurfacePainter.fillRounded(canvas, toastX, toastY, toastW, toastH, 6f,
          Colors.alpha(bg, 0.94f));
      canvas.strokeRoundedRect(toastX, toastY, toastW, toastH, 6f, 0.6f, 0x6634D399);
      canvas.circle(toastX + 8f, toastY + toastH * 0.5f, 2.2f, 0xFF34D399);
      String msg = clip(canvas, statusMessage, FONT_MEDIUM, 6.3f, toastW - 18f);
      canvas.textFamily(FONT_MEDIUM, msg, toastX + 14f, toastY + (toastH - 6.3f) * 0.5f - 0.3f,
          6.3f, 0xFF6EE7B7, false, 0f);
    }

    float footGap = 4f;
    float btnW = (contentW - footGap * 3f) / 4f;
    String[][] footButtons = {
        {"open-editor", I18n.text(lang, "mapSettings.open_editor")},
        {"open-waypoints", I18n.text(lang, "mapSettings.open_waypoints")},
        {"open-worldmap", I18n.text(lang, "mapSettings.open_worldmap")},
        {"done", I18n.text(lang, "mapSettings.done")}
    };
    for (int i = 0; i < footButtons.length; i++) {
      float fx = contentX + i * (btnW + footGap);
      boolean primary = i == footButtons.length - 1;
      boolean hov = mouseX >= fx && mouseX <= fx + btnW
          && mouseY >= footerY && mouseY <= footerY + footerH;
      int fill = primary
          ? (hov ? Colors.mix(accent, 0xFFFFFFFF, 0.10f) : accent)
          : (hov ? Colors.mix(raised, text, 0.07f) : raised);
      int rim = primary ? accent : (hov ? Colors.alpha(text, 0.16f) : subtleBorder);
      SurfacePainter.fillRounded(canvas, fx, footerY, btnW, footerH, 8f, fill);
      canvas.strokeRoundedRect(fx, footerY, btnW, footerH, 8f, 0.6f, rim);
      String lbl = clip(canvas, footButtons[i][1], FONT_MEDIUM, 6.7f, btnW - 8f);
      float lw = canvas.textWidthFamily(FONT_MEDIUM, lbl, 6.7f, false, 0f);
      canvas.textFamily(FONT_MEDIUM, lbl, fx + (btnW - lw) * 0.5f,
          footerY + (footerH - 6.7f) * 0.5f - 0.3f, 6.7f, primary ? onAccent : text, false, 0f);
      hits.add(new Hit(footButtons[i][0], fx, footerY, btnW, footerH, null));
    }
  }

  private void renderRow(Canvas canvas, String lang, Entry e, float x, float y, float w, float h,
      float listTop, float listH, boolean hovered, int bg, int raised, int text, int muted,
      int accent, int onAccent, int border, int subtleBorder) {
    boolean dragging = e.kind == KIND_SLIDER && e.id.equals(draggingSliderId);
    boolean capturing = e.kind == KIND_KEY && e.id.substring(5).equals(capturingBind);
    int rowBg = (hovered || dragging || capturing)
        ? Colors.mix(raised, accent, capturing ? 0.14f : 0.05f) : raised;
    int rowBorder = capturing ? accent : ((hovered || dragging) ? Colors.alpha(accent, 0.48f) : subtleBorder);
    SurfacePainter.fillRounded(canvas, x, y, w, h, 9f, rowBg);
    canvas.strokeRoundedRect(x, y, w, h, 9f, 0.6f, rowBorder);

    float cy0 = Math.max(listTop, y);
    float cy1 = Math.min(listTop + listH, y + h);

    if (e.kind == KIND_SLIDER) {
      float padX = 9f;
      String val = e.valueText;
      float valW = Math.max(28f, canvas.textWidthFamily(FONT_MEDIUM, val, 6.2f, false, 0f) + 10f);
      float valH = 13f;
      float valX = x + w - padX - valW;
      float valY = y + 3.5f;
      SurfacePainter.fillRounded(canvas, valX, valY, valW, valH, 6.5f, bg);
      canvas.strokeRoundedRect(valX, valY, valW, valH, 6.5f, 0.5f, border);
      float vtw = canvas.textWidthFamily(FONT_MEDIUM, val, 6.2f, false, 0f);
      canvas.textFamily(FONT_MEDIUM, val, valX + (valW - vtw) * 0.5f,
          valY + (valH - 6.2f) * 0.5f - 0.3f, 6.2f, text, false, 0f);

      String lbl = clip(canvas, e.label, FONT_MEDIUM, 6.8f, Math.max(24f, valX - (x + padX) - 8f));
      canvas.textFamily(FONT_MEDIUM, lbl, x + padX, y + 5.5f, 6.8f, text, false, 0f);

      float trackX = x + padX;
      float trackW = Math.max(20f, w - padX * 2f);
      float trackY = y + 21.5f;
      float trackH = 3.5f;
      SurfacePainter.fillRounded(canvas, trackX, trackY, trackW, trackH, 1.75f,
          Colors.alpha(text, 0.16f));
      float fillW = Math.max(0f, trackW * e.sliderFrac);
      if (fillW > 0.5f) {
        SurfacePainter.fillRounded(canvas, trackX, trackY, fillW, trackH, 1.75f, accent);
      }
      float knobX = trackX + fillW;
      float knobY = trackY + trackH * 0.5f;
      canvas.circle(knobX, knobY, 4.4f, bg);
      canvas.circle(knobX, knobY, 3.6f, text);

      if (cy1 > cy0) hits.add(new Hit(e.id, trackX, cy0, trackW, cy1 - cy0, e));
      return;
    }

    float padX = 9f;
    float textY = y + (h - 7.0f) * 0.5f - 0.3f;

    if (e.kind == KIND_TOGGLE) {
      float swW = 22f, swH = 12f;
      float swX = x + w - padX - swW;
      float swY = y + (h - swH) * 0.5f;
      String lbl = clip(canvas, e.label, FONT_MEDIUM, 7.0f, Math.max(24f, swX - (x + padX) - 8f));
      canvas.textFamily(FONT_MEDIUM, lbl, x + padX, textY, 7.0f, text, false, 0f);

      SurfacePainter.fillRounded(canvas, swX, swY, swW, swH, 6f,
          e.boolVal ? accent : Colors.alpha(text, 0.18f));
      float knobX = e.boolVal ? (swX + swW - 6f) : (swX + 6f);
      canvas.circle(knobX, swY + swH * 0.5f, 4f, e.boolVal ? onAccent : 0xFFFFFFFF);
    } else if (e.kind == KIND_COLOR) {
      float dotR = 5.2f;
      float dotCx = x + w - padX - dotR;
      float dotCy = y + h * 0.5f;
      String lbl = clip(canvas, e.label, FONT_MEDIUM, 7.0f, Math.max(24f, dotCx - dotR - (x + padX) - 8f));
      canvas.textFamily(FONT_MEDIUM, lbl, x + padX, textY, 7.0f, text, false, 0f);
      canvas.circle(dotCx, dotCy, dotR + 1.2f, Colors.alpha(text, 0.28f));
      canvas.circle(dotCx, dotCy, dotR, e.colorVal | 0xFF000000);
    } else if (e.kind == KIND_CHOICE) {
      String val = e.valueText;
      float pillW = Math.min(w * 0.44f, Math.max(38f, canvas.textWidthFamily(FONT_MEDIUM, val, 6.2f, false, 0f) + 12f));
      float pillH = 15f;
      float pillX = x + w - padX - pillW;
      float pillY = y + (h - pillH) * 0.5f;
      String lbl = clip(canvas, e.label, FONT_MEDIUM, 7.0f, Math.max(24f, pillX - (x + padX) - 8f));
      canvas.textFamily(FONT_MEDIUM, lbl, x + padX, textY, 7.0f, text, false, 0f);

      SurfacePainter.fillRounded(canvas, pillX, pillY, pillW, pillH, 5f, bg);
      canvas.strokeRoundedRect(pillX, pillY, pillW, pillH, 5f, 0.5f, border);
      String shown = clip(canvas, val, FONT_MEDIUM, 6.2f, pillW - 8f);
      float vw = canvas.textWidthFamily(FONT_MEDIUM, shown, 6.2f, false, 0f);
      canvas.textFamily(FONT_MEDIUM, shown, pillX + (pillW - vw) * 0.5f,
          pillY + (pillH - 6.2f) * 0.5f - 0.3f, 6.2f, text, false, 0f);
    } else if (e.kind == KIND_KEY) {
      String bindId = e.id.substring(5);
      String keyText = capturing ? I18n.text(lang, "mapSettings.press_key") : host.keyBindingLabel(bindId);
      float keyW = Math.min(w * 0.36f, Math.max(30f, canvas.textWidthFamily(FONT_MEDIUM, keyText, 6.3f, false, 0f) + 14f));
      float keyH = 16f;
      float keyX = x + w - padX - keyW;
      float keyY = y + (h - keyH) * 0.5f;
      String lbl = clip(canvas, e.label, FONT_MEDIUM, 7.0f, Math.max(24f, keyX - (x + padX) - 8f));
      canvas.textFamily(FONT_MEDIUM, lbl, x + padX, textY, 7.0f, text, false, 0f);

      SurfacePainter.fillRounded(canvas, keyX, keyY, keyW, keyH, 5f, capturing ? accent : bg);
      canvas.strokeRoundedRect(keyX, keyY, keyW, keyH, 5f, 0.55f, capturing ? accent : border);
      String shown = clip(canvas, keyText, FONT_MEDIUM, 6.3f, keyW - 8f);
      float kw = canvas.textWidthFamily(FONT_MEDIUM, shown, 6.3f, false, 0f);
      canvas.textFamily(FONT_MEDIUM, shown, keyX + (keyW - kw) * 0.5f,
          keyY + (keyH - 6.3f) * 0.5f - 0.3f, 6.3f, capturing ? onAccent : text, false, 0f);
    }

    if (cy1 > cy0) hits.add(new Hit(e.id, x, cy0, w, cy1 - cy0, e));
  }

  private void renderCachePair(Canvas canvas, String lang, float x, float y, float w, float h,
      float listTop, float listH, boolean inList, float mouseX, float mouseY, int raised) {
    float gap = 5f;
    float bw = (w - gap) * 0.5f;
    float cy0 = Math.max(listTop, y);
    float cy1 = Math.min(listTop + listH, y + h);

    String[][] pair = {
        {"cache.clearWorld", confirmClearWorld
            ? I18n.text(lang, "mapSettings.confirm_clear_world")
            : I18n.text(lang, "worldMap.clear_world_cache"), confirmClearWorld ? "1" : "0"},
        {"cache.clearAll", confirmClearAll
            ? I18n.text(lang, "mapSettings.confirm_clear_all")
            : I18n.text(lang, "worldMap.clear_all_cache"), confirmClearAll ? "1" : "0"}
    };
    for (int i = 0; i < 2; i++) {
      float bx = x + i * (bw + gap);
      boolean confirm = "1".equals(pair[i][2]);
      boolean hov = inList && mouseX >= bx && mouseX <= bx + bw && mouseY >= y && mouseY <= y + h;
      int fill = confirm
          ? 0x4DFF4D4F
          : (hov ? Colors.mix(raised, 0xFFFF4D4F, 0.14f) : Colors.mix(raised, 0xFFFF4D4F, 0.06f));
      int rim = confirm ? 0xCCFF6B6B : (hov ? 0x66FF6B6B : 0x30FF6B6B);
      SurfacePainter.fillRounded(canvas, bx, y, bw, h, 8f, fill);
      canvas.strokeRoundedRect(bx, y, bw, h, 8f, 0.6f, rim);
      String lbl = clip(canvas, pair[i][1], FONT_MEDIUM, 6.6f, bw - 10f);
      float lw = canvas.textWidthFamily(FONT_MEDIUM, lbl, 6.6f, false, 0f);
      canvas.textFamily(FONT_MEDIUM, lbl, bx + (bw - lw) * 0.5f,
          y + (h - 6.6f) * 0.5f - 0.3f, 6.6f, confirm ? 0xFFFFD6D6 : 0xFFFF8A80, false, 0f);
      if (cy1 > cy0) hits.add(new Hit(pair[i][0], bx, cy0, bw, cy1 - cy0, null));
    }
  }

  private List<Entry> buildEntries(String lang) {
    List<Entry> list = new ArrayList<Entry>();
    Settings.Minimap m = settings.minimap;
    Settings.WorldMap w = settings.worldMap;

    if (tab == 0) {
      list.add(toggle(lang, "m.enabled", "module.minimap.label", m.enabled));
      list.add(choice(lang, "m.shape", "field.minimap.shape", "choice.minimap.shape." + m.shape));
      list.add(slider(lang, "m.size", "field.minimap.size", m.size, 84f, 220f, 2f, "%dpx"));
      list.add(slider(lang, "m.zoom", "field.minimap.zoom", m.zoom, 0.5f, 3.0f, 0.05f, "%.2fx"));
      list.add(toggle(lang, "m.lockNorth", "field.minimap.lockNorth", m.lockNorth));
      list.add(toggle(lang, "m.showCompass", "field.minimap.showCompass", m.showCompass));
      list.add(toggle(lang, "m.showCoords", "field.minimap.showCoords", m.showCoords));
      list.add(toggle(lang, "m.chunkGrid", "field.minimap.chunkGrid", m.chunkGrid));
      list.add(slider(lang, "m.frameOpacity", "field.minimap.frameOpacity", m.frameOpacity, 0f, 1f, 0.05f, "%.0f%%"));
      list.add(choice(lang, "m.enlargeMode", "field.minimap.enlargeMode", "choice.minimap.enlargeMode." + m.enlargeMode));
      list.add(slider(lang, "m.enlargeSize", "field.minimap.enlargeSize", m.enlargeSize, 180f, 440f, 4f, "%dpx"));
      list.add(slider(lang, "m.enlargeZoom", "field.minimap.enlargeZoom", m.enlargeZoom, 0.35f, 2.0f, 0.05f, "%.2fx"));
    } else if (tab == 1) {
      list.add(toggle(lang, "m.terrainShading", "field.minimap.terrainShading", m.terrainShading));
      if (m.terrainShading) {
        list.add(choice(lang, "m.reliefStyle", "field.minimap.reliefStyle", "choice.minimap.reliefStyle." + m.reliefStyle));
        list.add(slider(lang, "m.reliefStrength", "field.minimap.reliefStrength", m.reliefStrength, 0.5f, 4.0f, 0.05f, "%.2f"));
      }
      list.add(toggle(lang, "m.blockLight", "field.minimap.blockLight", m.blockLight));
      list.add(toggle(lang, "m.biomeColors", "field.minimap.biomeColors", m.biomeColors));
      list.add(choice(lang, "m.arrowStyle", "field.minimap.arrowStyle", "choice.minimap.arrowStyle." + m.arrowStyle));
      list.add(slider(lang, "m.arrowScale", "field.minimap.arrowScale", m.arrowScale, 0.6f, 2.0f, 0.05f, "%.2fx"));
      list.add(colorRow(lang, "m.arrowColor", "field.minimap.arrowColor", m.arrowColor));
    } else if (tab == 2) {
      list.add(toggle(lang, "m.showPlayers", "field.minimap.showPlayers", m.showPlayers));
      if (m.showPlayers) {
        list.add(toggle(lang, "m.showPlayerHeads", "field.minimap.showPlayerHeads", m.showPlayerHeads));
        list.add(toggle(lang, "m.showPlayerNames", "field.minimap.showPlayerNames", m.showPlayerNames));
        list.add(toggle(lang, "m.showInvisiblePlayers", "field.minimap.showInvisiblePlayers", m.showInvisiblePlayers));
        list.add(slider(lang, "m.playerScale", "field.minimap.playerScale", m.playerScale, 0.6f, 1.8f, 0.05f, "%.2fx"));
      }
      list.add(choice(lang, "m.mobIconStyle", "field.minimap.mobIconStyle", "choice.minimap.mobIconStyle." + m.mobIconStyle));
      list.add(slider(lang, "m.mobScale", "field.minimap.mobScale", m.mobScale, 0.6f, 1.8f, 0.05f, "%.2fx"));
      list.add(toggle(lang, "m.showHostileMobs", "field.minimap.showHostileMobs", m.showHostileMobs));
      list.add(toggle(lang, "m.showNeutralMobs", "field.minimap.showNeutralMobs", m.showNeutralMobs));
      list.add(toggle(lang, "m.showPassiveMobs", "field.minimap.showPassiveMobs", m.showPassiveMobs));
      list.add(toggle(lang, "m.showOtherEntities", "field.minimap.showOtherEntities", m.showOtherEntities));
      list.add(toggle(lang, "m.showMobNames", "field.minimap.showMobNames", m.showMobNames));
      list.add(toggle(lang, "m.showExplosives", "field.minimap.showExplosives", m.showExplosives));
      list.add(toggle(lang, "m.showItems", "field.minimap.showItems", m.showItems));
      if (m.showItems) {
        list.add(choice(lang, "m.itemStyle", "field.minimap.itemStyle", "choice.minimap.itemStyle." + m.itemStyle));
        list.add(toggle(lang, "m.itemBackground", "field.minimap.itemBackground", m.itemBackground));
        list.add(toggle(lang, "m.onlyValuableItems", "field.minimap.onlyValuableItems", m.onlyValuableItems));
        list.add(slider(lang, "m.itemScale", "field.minimap.itemScale", m.itemScale, 0.6f, 1.8f, 0.05f, "%.2fx"));
      }
      list.add(toggle(lang, "m.showWaypoints", "field.minimap.showWaypoints", m.showWaypoints));
      if (m.showWaypoints) {
        list.add(toggle(lang, "m.offscreenWaypoints", "field.minimap.offscreenWaypoints", m.offscreenWaypoints));
      }
    } else if (tab == 3) {
      list.add(toggle(lang, "w.enabled", "module.worldMap.label", w.enabled));
      list.add(toggle(lang, "w.diskCache", "field.worldMap.diskCache", w.diskCache));
      list.add(toggle(lang, "w.terrainShading", "field.worldMap.terrainShading", w.terrainShading));
      list.add(toggle(lang, "w.blockLight", "field.worldMap.blockLight", w.blockLight));
      list.add(toggle(lang, "w.showGrid", "field.worldMap.showGrid", w.showGrid));
      list.add(toggle(lang, "w.showCoordinatesBar", "field.worldMap.showCoordinatesBar", w.showCoordinatesBar));
      list.add(toggle(lang, "w.showWaypoints", "field.worldMap.showWaypoints", w.showWaypoints));
      list.add(toggle(lang, "w.showPlayers", "field.worldMap.showPlayers", w.showPlayers));
      if (w.showPlayers) {
        list.add(toggle(lang, "w.showInvisiblePlayers", "field.worldMap.showInvisiblePlayers", w.showInvisiblePlayers));
      }
      list.add(toggle(lang, "w.showMobs", "field.worldMap.showMobs", w.showMobs));
      list.add(toggle(lang, "w.showOtherEntities", "field.worldMap.showOtherEntities", w.showOtherEntities));
      list.add(toggle(lang, "w.showItems", "field.worldMap.showItems", w.showItems));
      list.add(toggle(lang, "w.teleportCommand", "field.worldMap.teleportCommand", w.teleportCommand));
      list.add(new Entry("cache.pair", "", KIND_CACHE_PAIR, false, "", 0, 0f, 0f, 1f, 0.1f));
    } else {
      list.add(keyRow(lang, "worldMap", "ui.world_map_key"));
      list.add(keyRow(lang, "mapSettings", "ui.map_settings_key"));
      list.add(keyRow(lang, "mapEnlarge", "ui.map_enlarge_key"));
      list.add(keyRow(lang, "waypointsMenu", "ui.waypoints_menu_key"));
      list.add(keyRow(lang, "waypoint", "ui.quick_waypoint_key"));
    }
    return list;
  }

  private Entry toggle(String lang, String id, String labelKey, boolean on) {
    return new Entry(id, I18n.text(lang, labelKey), KIND_TOGGLE, on, "", 0, 0f, 0f, 1f, 0.1f);
  }

  private Entry choice(String lang, String id, String labelKey, String choiceKey) {
    return new Entry(id, I18n.text(lang, labelKey), KIND_CHOICE, false, I18n.text(lang, choiceKey),
        0, 0f, 0f, 1f, 0.1f);
  }

  private Entry colorRow(String lang, String id, String labelKey, int argb) {
    return new Entry(id, I18n.text(lang, labelKey), KIND_COLOR, false, "", argb, 0f, 0f, 1f, 0.1f);
  }

  private Entry slider(
      String lang, String id, String labelKey, float value, float min, float max, float step, String fmt) {
    String valText;
    if ("%dpx".equals(fmt)) {
      valText = Math.round(value) + "px";
    } else if ("%.0f%%".equals(fmt)) {
      valText = Math.round(value * 100f) + "%";
    } else {
      valText = String.format(Locale.ROOT, fmt, value);
    }
    return new Entry(id, I18n.text(lang, labelKey), KIND_SLIDER, false, valText, 0,
        value, min, max, step);
  }

  private Entry keyRow(String lang, String bindId, String labelKey) {
    return new Entry("bind:" + bindId, I18n.text(lang, labelKey), KIND_KEY, false, "", 0,
        0f, 0f, 1f, 0.1f);
  }

  public boolean mouseClicked(double mx, double my, int button) {
    if (button != 0) return false;
    float x = (float) mx, y = (float) my;
    if (!capturingBind.isEmpty()) {
      capturingBind = "";
      return true;
    }
    for (int i = hits.size() - 1; i >= 0; i--) {
      Hit h = hits.get(i);
      if (h.contains(x, y)) {
        if (h.entry != null && h.entry.kind == KIND_SLIDER) {
          host.checkpoint();
          draggingSliderId = h.action;
          dragX = h.x;
          dragW = h.w;
          dragMin = h.entry.min;
          dragMax = h.entry.max;
          dragStep = h.entry.step;
          updateSliderFromX(x);
          return true;
        }
        handleAction(h.action);
        return true;
      }
    }
    confirmClearWorld = false;
    confirmClearAll = false;
    if (modalW > 0f && (x < modalX || x > modalX + modalW || y < modalY || y > modalY + modalH)) {
      host.close();
      return true;
    }
    return false;
  }

  public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    if (button != 0 || draggingSliderId.isEmpty()) return false;
    updateSliderFromX((float) mx);
    return true;
  }

  public boolean mouseReleased(double mx, double my, int button) {
    if (button == 0 && !draggingSliderId.isEmpty()) {
      draggingSliderId = "";
      return true;
    }
    return false;
  }

  public boolean mouseScrolled(double mx, double my, double vertical) {
    if (vertical == 0 || maxScroll <= 0f) return false;
    scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) vertical * 24f));
    return true;
  }

  public boolean keyPressed(int key, int modifiers) {
    if (key == Keys.ESCAPE) {
      if (!capturingBind.isEmpty()) {
        capturingBind = "";
        return true;
      }
      host.close();
      return true;
    }
    return false;
  }

  private void updateSliderFromX(float mx) {
    float frac = Math.max(0f, Math.min(1f, (mx - dragX) / Math.max(1f, dragW)));
    float raw = dragMin + frac * (dragMax - dragMin);
    float stepped = dragStep > 0f ? Math.round(raw / dragStep) * dragStep : raw;
    float value = Math.max(dragMin, Math.min(dragMax, stepped));
    Settings.Minimap m = settings.minimap;
    if ("m.size".equals(draggingSliderId)) m.size = Math.round(value);
    else if ("m.zoom".equals(draggingSliderId)) m.zoom = value;
    else if ("m.frameOpacity".equals(draggingSliderId)) m.frameOpacity = value;
    else if ("m.enlargeSize".equals(draggingSliderId)) m.enlargeSize = Math.round(value);
    else if ("m.enlargeZoom".equals(draggingSliderId)) m.enlargeZoom = value;
    else if ("m.reliefStrength".equals(draggingSliderId)) m.reliefStrength = value;
    else if ("m.arrowScale".equals(draggingSliderId)) m.arrowScale = value;
    else if ("m.playerScale".equals(draggingSliderId)) m.playerScale = value;
    else if ("m.mobScale".equals(draggingSliderId)) m.mobScale = value;
    else if ("m.itemScale".equals(draggingSliderId)) m.itemScale = value;
    settings.validate();
    host.changed();
  }

  private void handleAction(String action) {
    if (!"cache.clearWorld".equals(action)) confirmClearWorld = false;
    if (!"cache.clearAll".equals(action)) confirmClearAll = false;

    if ("done".equals(action)) {
      host.close();
      return;
    }
    if ("open-worldmap".equals(action)) {
      host.close();
      host.openWorldMap();
      return;
    }
    if ("open-waypoints".equals(action)) {
      host.close();
      host.openWaypointsMenu();
      return;
    }
    if ("open-editor".equals(action)) {
      host.close();
      host.openHudEditor();
      return;
    }
    if (action.startsWith("tab:")) {
      tab = Integer.parseInt(action.substring(4));
      scroll = 0f;
      return;
    }
    if (action.startsWith("bind:")) {
      capturingBind = action.substring(5);
      return;
    }
    String lang = I18n.language(settings.language);
    if ("cache.clearWorld".equals(action)) {
      if (!confirmClearWorld) {
        confirmClearWorld = true;
      } else {
        confirmClearWorld = false;
        host.clearCurrentWorldCache();
        statusMessage = I18n.text(lang, "worldMap.cache_cleared_world");
        statusUntil = lastTime + 2.4;
      }
      return;
    }
    if ("cache.clearAll".equals(action)) {
      if (!confirmClearAll) {
        confirmClearAll = true;
      } else {
        confirmClearAll = false;
        host.clearAllMapCache();
        statusMessage = I18n.text(lang, "worldMap.cache_cleared_all");
        statusUntil = lastTime + 2.4;
      }
      return;
    }

    host.checkpoint();
    Settings.Minimap m = settings.minimap;
    Settings.WorldMap w = settings.worldMap;
    if ("m.enabled".equals(action)) m.enabled = !m.enabled;
    else if ("m.shape".equals(action)) m.shape = nextChoice(SHAPES, m.shape);
    else if ("m.lockNorth".equals(action)) m.lockNorth = !m.lockNorth;
    else if ("m.showCompass".equals(action)) m.showCompass = !m.showCompass;
    else if ("m.showCoords".equals(action)) m.showCoords = !m.showCoords;
    else if ("m.chunkGrid".equals(action)) m.chunkGrid = !m.chunkGrid;
    else if ("m.enlargeMode".equals(action)) m.enlargeMode = nextChoice(ENLARGE_MODES, m.enlargeMode);
    else if ("m.terrainShading".equals(action)) m.terrainShading = !m.terrainShading;
    else if ("m.reliefStyle".equals(action)) m.reliefStyle = nextChoice(RELIEF_STYLES, m.reliefStyle);
    else if ("m.blockLight".equals(action)) m.blockLight = !m.blockLight;
    else if ("m.biomeColors".equals(action)) m.biomeColors = !m.biomeColors;
    else if ("m.arrowStyle".equals(action)) m.arrowStyle = nextChoice(ARROW_STYLES, m.arrowStyle);
    else if ("m.arrowColor".equals(action)) m.arrowColor = nextArrowColor(m.arrowColor);
    else if ("m.showPlayers".equals(action)) m.showPlayers = !m.showPlayers;
    else if ("m.showPlayerHeads".equals(action)) m.showPlayerHeads = !m.showPlayerHeads;
    else if ("m.showPlayerNames".equals(action)) m.showPlayerNames = !m.showPlayerNames;
    else if ("m.showInvisiblePlayers".equals(action)) m.showInvisiblePlayers = !m.showInvisiblePlayers;
    else if ("m.mobIconStyle".equals(action)) m.mobIconStyle = nextChoice(MOB_STYLES, m.mobIconStyle);
    else if ("m.showHostileMobs".equals(action)) m.showHostileMobs = !m.showHostileMobs;
    else if ("m.showNeutralMobs".equals(action)) m.showNeutralMobs = !m.showNeutralMobs;
    else if ("m.showPassiveMobs".equals(action)) m.showPassiveMobs = !m.showPassiveMobs;
    else if ("m.showOtherEntities".equals(action)) m.showOtherEntities = !m.showOtherEntities;
    else if ("m.showMobNames".equals(action)) m.showMobNames = !m.showMobNames;
    else if ("m.showExplosives".equals(action)) m.showExplosives = !m.showExplosives;
    else if ("m.showItems".equals(action)) m.showItems = !m.showItems;
    else if ("m.itemStyle".equals(action)) m.itemStyle = nextChoice(ITEM_STYLES, m.itemStyle);
    else if ("m.itemBackground".equals(action)) m.itemBackground = !m.itemBackground;
    else if ("m.onlyValuableItems".equals(action)) m.onlyValuableItems = !m.onlyValuableItems;
    else if ("m.showWaypoints".equals(action)) m.showWaypoints = !m.showWaypoints;
    else if ("m.offscreenWaypoints".equals(action)) m.offscreenWaypoints = !m.offscreenWaypoints;
    else if ("w.enabled".equals(action)) w.enabled = !w.enabled;
    else if ("w.diskCache".equals(action)) w.diskCache = !w.diskCache;
    else if ("w.terrainShading".equals(action)) w.terrainShading = !w.terrainShading;
    else if ("w.blockLight".equals(action)) w.blockLight = !w.blockLight;
    else if ("w.showGrid".equals(action)) w.showGrid = !w.showGrid;
    else if ("w.showCoordinatesBar".equals(action)) w.showCoordinatesBar = !w.showCoordinatesBar;
    else if ("w.showWaypoints".equals(action)) w.showWaypoints = !w.showWaypoints;
    else if ("w.showPlayers".equals(action)) w.showPlayers = !w.showPlayers;
    else if ("w.showInvisiblePlayers".equals(action)) w.showInvisiblePlayers = !w.showInvisiblePlayers;
    else if ("w.showMobs".equals(action)) w.showMobs = !w.showMobs;
    else if ("w.showOtherEntities".equals(action)) w.showOtherEntities = !w.showOtherEntities;
    else if ("w.showItems".equals(action)) w.showItems = !w.showItems;
    else if ("w.teleportCommand".equals(action)) w.teleportCommand = !w.teleportCommand;
    settings.validate();
    host.changed();
  }

  private static String nextChoice(String[] options, String current) {
    for (int i = 0; i < options.length; i++) {
      if (options[i].equals(current)) return options[(i + 1) % options.length];
    }
    return options[0];
  }

  private static int nextArrowColor(int current) {
    int c = current | 0xFF000000;
    for (int i = 0; i < ARROW_COLORS.length; i++) {
      if (ARROW_COLORS[i] == c) return ARROW_COLORS[(i + 1) % ARROW_COLORS.length];
    }
    return ARROW_COLORS[0];
  }

  private static String clip(Canvas canvas, String text, String family, float size, float maxWidth) {
    if (text == null || text.isEmpty()) return "";
    if (canvas.textWidthFamily(family, text, size, false, 0f) <= maxWidth) return text;
    String ellipsis = "...";
    float ellW = canvas.textWidthFamily(family, ellipsis, size, false, 0f);
    if (maxWidth <= ellW) return "";
    int end = text.length();
    while (end > 1 && canvas.textWidthFamily(family, text.substring(0, end), size, false, 0f) + ellW > maxWidth) {
      end--;
    }
    return text.substring(0, end) + ellipsis;
  }
}
