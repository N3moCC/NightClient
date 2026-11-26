package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Keys;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.i18n.I18n;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class WaypointsScreenController {
  public interface Host {
    double[] playerPosition();

    String currentDimension();

    String currentWorldId();

    String currentWorldLabel();

    void teleportTo(int x, int y, int z);

    void clipboard(String value);

    void checkpoint();

    void changed();

    void close();
  }

  private static final int[] PALETTE = {
      0xFF38BDF8, 0xFF4ADE80, 0xFFFACC15, 0xFFF97316, 0xFFEF4444, 0xFFE879F9,
      0xFFA855F7, 0xFF6366F1, 0xFF14B8A6, 0xFFF472B6, 0xFFFFFFFF, 0xFF94A3B8
  };

  private static final class Hit {
    final String action;
    final float x, y, w, h;

    Hit(String action, float x, float y, float w, float h) {
      this.action = action;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    boolean contains(float mx, float my) {
      return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }
  }

  private final Settings settings;
  private final Host host;
  private final List<Hit> hits = new ArrayList<Hit>();
  private boolean open;
  private boolean allWorlds;
  private String search = "";
  private boolean searchFocused;
  private String selectedId = "";
  private float scroll;
  private float maxScroll;
  private float lastListH = 120f;
  private String statusMessage = "";
  private double statusUntil;
  private double lastTime;
  private double lastClickTime;
  private String lastClickedRow = "";

  private boolean editing;
  private String editingId = "";
  private String draftName = "";
  private String draftX = "0";
  private String draftY = "64";
  private String draftZ = "0";
  private int draftColor = PALETTE[0];
  private boolean draftTemp;
  private int focusField;

  public WaypointsScreenController(Settings settings, Host host) {
    this.settings = settings;
    this.host = host;
  }

  public boolean isOpen() {
    return open;
  }

  public boolean isTextFocused() {
    return editing || searchFocused;
  }

  public boolean isEditing() {
    return editing;
  }

  public String selectedWaypointId() {
    return selectedId;
  }

  public void onOpen() {
    open = true;
    editing = false;
    search = "";
    searchFocused = false;
    scroll = 0f;
    statusMessage = "";
    ensureValidSelection(filteredWaypoints());
  }

  public void onClose() {
    open = false;
    editing = false;
    searchFocused = false;
  }

  public void openAddDraft(double x, double y, double z) {
    editing = true;
    editingId = "";
    draftName = "";
    draftX = Integer.toString((int) Math.round(x));
    draftY = Integer.toString((int) Math.round(y));
    draftZ = Integer.toString((int) Math.round(z));
    draftColor = PALETTE[settings.waypoints.points.size() % PALETTE.length];
    draftTemp = false;
    focusField = 0;
  }

  public void render(
      Canvas canvas, int width, int height, float mouseX, float mouseY, double timeSeconds) {
    if (canvas == null || width <= 0 || height <= 0) return;
    lastTime = timeSeconds;
    hits.clear();
    String lang = I18n.language(settings.language);
    VanillaWidgets.drawBackdrop(canvas, width, height);

    if (editing) {
      renderEditModal(canvas, width, height, mouseX, mouseY, timeSeconds, lang);
      return;
    }

    float panelW = Math.min(410f, width - 20f);
    float panelX = (width - panelW) * 0.5f;

    String worldLabel = host.currentWorldLabel();
    String dimShort = shortDim(host.currentDimension(), lang);
    String title = I18n.text(lang, "waypointsMenu.title");
    if (worldLabel != null && !worldLabel.isEmpty()) {
      title += " — " + worldLabel + (dimShort.isEmpty() ? "" : (" (" + dimShort + ")"));
    }
    VanillaWidgets.drawCenteredText(canvas, title, 8f, 8f, width - 16f, 16f, 0xFFFFFFFF);

    float topY = 28f;
    float topH = 20f;
    float addW = 66f;
    float filterW = 84f;
    float gap = 5f;
    float searchW = Math.max(80f, panelW - addW - filterW - gap * 2f);
    VanillaWidgets.drawTextField(canvas, panelX, topY, searchW, topH, search,
        I18n.text(lang, "waypointsMenu.search_placeholder"), searchFocused, timeSeconds);
    hits.add(new Hit("focus-search", panelX, topY, searchW, topH));

    float filterX = panelX + searchW + gap;
    boolean filterHov = mouseX >= filterX && mouseX <= filterX + filterW && mouseY >= topY
        && mouseY <= topY + topH;
    VanillaWidgets.drawButton(canvas, filterX, topY, filterW, topH,
        I18n.text(lang, allWorlds ? "waypointsMenu.filter.all" : "waypointsMenu.filter.current"),
        filterHov, true, allWorlds, false);
    hits.add(new Hit("toggle-filter", filterX, topY, filterW, topH));

    float addX = filterX + filterW + gap;
    boolean addHov = mouseX >= addX && mouseX <= addX + addW && mouseY >= topY && mouseY <= topY + topH;
    VanillaWidgets.drawButton(
        canvas, addX, topY, addW, topH, "+ " + I18n.text(lang, "waypointsMenu.add"), addHov, true, false, false);
    hits.add(new Hit("add", addX, topY, addW, topH));

    float listTop = 54f;
    float footerH = 56f;
    float listH = Math.max(64f, height - listTop - footerH);
    lastListH = listH;
    VanillaWidgets.drawListWell(canvas, panelX, listTop, panelW, listH);

    List<Waypoint> rows = filteredWaypoints();
    ensureValidSelection(rows);
    double[] pos = host.playerPosition();
    float rowH = 24f;
    float contentH = Math.max(listH, rows.size() * rowH + 8f);
    maxScroll = Math.max(0f, contentH - listH);
    if (scroll > maxScroll) scroll = maxScroll;
    if (scroll < 0f) scroll = 0f;

    canvas.pushClip(panelX + 1f, listTop + 1f, panelW - 2f, listH - 2f);
    try {
      if (rows.isEmpty()) {
        VanillaWidgets.drawCenteredText(
            canvas, I18n.text(lang, "waypointsMenu.empty"), panelX + 12f, listTop + listH * 0.42f,
            panelW - 24f, 16f, 0xFF9CA3AF);
      } else {
        float innerX = panelX + 4f;
        float innerW = panelW - 14f;
        for (int i = 0; i < rows.size(); i++) {
          Waypoint wp = rows.get(i);
          float ry = listTop + 4f + i * rowH - scroll;
          if (ry + rowH < listTop || ry > listTop + listH) continue;
          boolean sel = wp.id.equals(selectedId);
          boolean inView = mouseY >= listTop && mouseY <= listTop + listH;
          boolean hov = inView && mouseX >= innerX && mouseX <= innerX + innerW
              && mouseY >= ry && mouseY <= ry + rowH - 2f;

          if (sel) {
            canvas.rect(innerX, ry, innerW, rowH - 2f, 0xFFFFFFFF);
            canvas.rect(innerX + 1f, ry + 1f, innerW - 2f, rowH - 4f, 0xFF1F293B);
          } else if (hov) {
            canvas.rect(innerX, ry, innerW, rowH - 2f, 0x38FFFFFF);
          } else if ((i & 1) == 1) {
            canvas.rect(innerX, ry, innerW, rowH - 2f, 0x16FFFFFF);
          }

          int col = wp.death ? 0xFFEF4444 : (wp.color | 0xFF000000);
          float boxX = innerX + 5f;
          float boxY = ry + 3f;
          float boxS = 16f;
          canvas.rect(boxX, boxY, boxS, boxS, 0xFF000000);
          canvas.rect(boxX + 1f, boxY + 1f, boxS - 2f, boxS - 2f, wp.visible ? col : 0xFF3F434D);
          String glyph = wp.death ? "X" : (wp.temp ? "*" : initialOf(wp.displayName()));
          VanillaWidgets.drawCenteredText(
              canvas, glyph, boxX, boxY, boxS, boxS, wp.visible ? 0xFFFFFFFF : 0xFF94A3B8);

          String name = wp.displayName();
          if (wp.temp) name += " [T]";
          if (wp.death) name += " [D]";
          String coords = Math.round(wp.x) + ", " + Math.round(wp.y) + ", " + Math.round(wp.z);
          if (pos != null && pos.length >= 3) {
            int dist = (int) Math.round(Math.hypot(wp.x - pos[0], wp.z - pos[2]));
            coords += "  (" + dist + I18n.text(lang, "hud.unit.distanceSymbol") + ")";
          }
          String stateText = I18n.text(lang, wp.visible ? "mapSettings.on" : "mapSettings.off");
          float stateW = canvas.gameTextWidth(stateText, 1f) + 10f;
          float coordW = canvas.gameTextWidth(coords, 1f);
          float nameMaxW = Math.max(40f, innerW - boxS - stateW - coordW - 26f);
          String fittedName = VanillaWidgets.fitText(canvas, name, nameMaxW);

          float textY = ry + (rowH - 2f - 9f) * 0.5f + 0.5f;
          canvas.gameText(fittedName, boxX + boxS + 6f, textY, 1f,
              wp.visible ? 0xFFFFFFFF : 0xFF8A909C, true);
          canvas.gameText(coords, innerX + innerW - stateW - coordW - 8f, textY, 1f,
              0xFF94A3B8, true);
          canvas.gameText(stateText, innerX + innerW - stateW + 2f, textY, 1f,
              wp.visible ? 0xFF4ADE80 : 0xFFF87171, true);

          float hitY0 = Math.max(listTop, ry);
          float hitY1 = Math.min(listTop + listH, ry + rowH - 2f);
          if (hitY1 > hitY0) {
            hits.add(new Hit("row:" + wp.id, innerX, hitY0, innerW, hitY1 - hitY0));
            hits.add(new Hit("row-toggle:" + wp.id, boxX, hitY0, boxS, hitY1 - hitY0));
          }
        }
      }
    } finally {
      canvas.popClip();
    }

    VanillaWidgets.drawScrollbar(
        canvas, panelX + panelW - 7f, listTop + 2f, listH - 4f, contentH, listH, scroll);

    if (!statusMessage.isEmpty() && timeSeconds <= statusUntil) {
      float toastW = Math.min(panelW - 24f, canvas.gameTextWidth(statusMessage, 1f) + 20f);
      float toastH = 16f;
      float toastX = panelX + (panelW - toastW) * 0.5f;
      float toastY = listTop + listH - toastH - 4f;
      canvas.rect(toastX, toastY, toastW, toastH, 0xFF4ADE80);
      canvas.rect(toastX + 1f, toastY + 1f, toastW - 2f, toastH - 2f, 0xEA0E1520);
      VanillaWidgets.drawCenteredText(
          canvas, statusMessage, toastX + 4f, toastY, toastW - 8f, toastH, 0xFF86EFAC);
    }

    Waypoint selWp = findSelected(rows);
    boolean hasSel = selWp != null;
    float row1Y = height - 47f;
    float row2Y = height - 24f;
    float btnH = 19f;
    float r1W = (panelW - gap * 3f) / 4f;
    String[][] row1 = {
        {"tp", I18n.text(lang, "waypointsMenu.teleport")},
        {"edit", I18n.text(lang, "waypointsMenu.edit")},
        {"toggle", I18n.text(lang, "waypointsMenu.toggle")},
        {"share", I18n.text(lang, "waypointsMenu.share")}
    };
    for (int i = 0; i < row1.length; i++) {
      float bx = panelX + i * (r1W + gap);
      boolean hov = mouseX >= bx && mouseX <= bx + r1W && mouseY >= row1Y && mouseY <= row1Y + btnH;
      VanillaWidgets.drawButton(canvas, bx, row1Y, r1W, btnH, row1[i][1], hov, hasSel, false, false);
      if (hasSel) hits.add(new Hit(row1[i][0], bx, row1Y, r1W, btnH));
    }

    float r2W = (panelW - gap * 2f) / 3f;
    String[][] row2 = {
        {"delete", I18n.text(lang, "waypointsMenu.delete")},
        {"clear-temp", I18n.text(lang, "waypointsMenu.clear_temp")},
        {"done", I18n.text(lang, "waypointsMenu.done")}
    };
    for (int i = 0; i < row2.length; i++) {
      float bx = panelX + i * (r2W + gap);
      boolean enabled = i != 0 || hasSel;
      boolean danger = i == 0 && hasSel;
      boolean hov = mouseX >= bx && mouseX <= bx + r2W && mouseY >= row2Y && mouseY <= row2Y + btnH;
      VanillaWidgets.drawButton(canvas, bx, row2Y, r2W, btnH, row2[i][1], hov, enabled, false, danger);
      if (enabled) hits.add(new Hit(row2[i][0], bx, row2Y, r2W, btnH));
    }
  }

  private void renderEditModal(Canvas canvas, int width, int height, float mouseX, float mouseY,
      double timeSeconds, String lang) {
    float modalW = Math.min(310f, width - 24f);
    float modalH = 198f;
    float mx = (width - modalW) * 0.5f;
    float my = Math.max(18f, (height - modalH) * 0.5f);

    VanillaWidgets.drawListWell(canvas, mx, my, modalW, modalH);
    String header = editingId.isEmpty()
        ? I18n.text(lang, "waypointsMenu.add")
        : I18n.text(lang, "waypointsMenu.edit");
    VanillaWidgets.drawCenteredText(canvas, header, mx, my + 6f, modalW, 14f, 0xFFFFFFFF);

    float fx = mx + 12f;
    float fw = modalW - 24f;
    float y = my + 24f;

    canvas.gameText(I18n.text(lang, "waypointsMenu.field.name"), fx, y, 1f, 0xFFCBD5E1, true);
    y += 12f;
    VanillaWidgets.drawTextField(canvas, fx, y, fw, 19f, draftName,
        I18n.text(lang, "waypointsMenu.placeholder.name"), focusField == 0, timeSeconds);
    hits.add(new Hit("field:0", fx, y, fw, 19f));
    y += 25f;

    canvas.gameText(I18n.text(lang, "waypointsMenu.field.coords"), fx, y, 1f, 0xFFCBD5E1, true);
    y += 12f;
    float cw = (fw - 10f) / 3f;
    VanillaWidgets.drawTextField(canvas, fx, y, cw, 19f, draftX, "X", focusField == 1, timeSeconds);
    hits.add(new Hit("field:1", fx, y, cw, 19f));
    VanillaWidgets.drawTextField(canvas, fx + cw + 5f, y, cw, 19f, draftY, "Y", focusField == 2, timeSeconds);
    hits.add(new Hit("field:2", fx + cw + 5f, y, cw, 19f));
    VanillaWidgets.drawTextField(canvas, fx + (cw + 5f) * 2f, y, cw, 19f, draftZ, "Z", focusField == 3, timeSeconds);
    hits.add(new Hit("field:3", fx + (cw + 5f) * 2f, y, cw, 19f));
    y += 26f;

    canvas.gameText(I18n.text(lang, "waypointsMenu.field.color"), fx, y, 1f, 0xFFCBD5E1, true);
    y += 12f;
    float swGap = 4f;
    float swSize = Math.min(18f, (fw - swGap * (PALETTE.length - 1)) / PALETTE.length);
    for (int i = 0; i < PALETTE.length; i++) {
      float sx = fx + i * (swSize + swGap);
      boolean active = (draftColor | 0xFF000000) == PALETTE[i];
      canvas.rect(sx, y, swSize, swSize, active ? 0xFFFFFFFF : 0xFF000000);
      canvas.rect(sx + 1.5f, y + 1.5f, swSize - 3f, swSize - 3f, PALETTE[i]);
      hits.add(new Hit("color:" + i, sx, y, swSize, swSize));
    }
    y += swSize + 10f;

    float tempW = fw * 0.42f;
    boolean tempHov = mouseX >= fx && mouseX <= fx + tempW && mouseY >= y && mouseY <= y + 20f;
    String tempLabel = I18n.text(lang, "waypointsMenu.field.temp") + ": "
        + I18n.text(lang, draftTemp ? "mapSettings.on" : "mapSettings.off");
    VanillaWidgets.drawButton(canvas, fx, y, tempW, 20f, tempLabel, tempHov, true, draftTemp, false);
    hits.add(new Hit("draft-temp", fx, y, tempW, 20f));

    float restW = (fw - tempW - 10f) * 0.5f;
    float saveX = fx + tempW + 5f;
    boolean saveHov = mouseX >= saveX && mouseX <= saveX + restW && mouseY >= y && mouseY <= y + 20f;
    VanillaWidgets.drawButton(
        canvas, saveX, y, restW, 20f, I18n.text(lang, "waypointsMenu.save"), saveHov, true, true, false);
    hits.add(new Hit("draft-save", saveX, y, restW, 20f));

    float cancelX = saveX + restW + 5f;
    boolean cancelHov = mouseX >= cancelX && mouseX <= cancelX + restW && mouseY >= y && mouseY <= y + 20f;
    VanillaWidgets.drawButton(
        canvas, cancelX, y, restW, 20f, I18n.text(lang, "waypointsMenu.cancel"), cancelHov, true, false, false);
    hits.add(new Hit("draft-cancel", cancelX, y, restW, 20f));
  }

  public boolean mouseClicked(double mx, double my, int button) {
    if (button != 0) return false;
    float x = (float) mx, y = (float) my;
    for (int i = hits.size() - 1; i >= 0; i--) {
      Hit h = hits.get(i);
      if (h.contains(x, y)) {
        handleAction(h.action);
        return true;
      }
    }
    searchFocused = false;
    return false;
  }

  public boolean mouseScrolled(double mx, double my, double vertical) {
    if (editing || vertical == 0 || maxScroll <= 0f) return false;
    scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) vertical * 24f));
    return true;
  }

  public boolean keyPressed(int key, int modifiers) {
    if (editing) {
      if (key == Keys.ESCAPE) {
        editing = false;
        return true;
      }
      if (key == Keys.TAB) {
        focusField = (focusField + 1) % 4;
        return true;
      }
      if (key == Keys.ENTER) {
        saveDraft();
        return true;
      }
      if (key == Keys.BACKSPACE) {
        backspaceActiveField();
        return true;
      }
      return false;
    }

    if (key == Keys.ESCAPE) {
      if (searchFocused && !search.isEmpty()) {
        search = "";
        return true;
      }
      host.close();
      return true;
    }
    if (searchFocused && key == Keys.BACKSPACE) {
      if (!search.isEmpty()) search = removeLastCodePoint(search);
      return true;
    }
    if (!searchFocused && (key == Keys.UP || key == Keys.DOWN)) {
      List<Waypoint> rows = filteredWaypoints();
      if (!rows.isEmpty()) {
        int idx = 0;
        for (int i = 0; i < rows.size(); i++) {
          if (rows.get(i).id.equals(selectedId)) {
            idx = i;
            break;
          }
        }
        idx = key == Keys.UP ? Math.max(0, idx - 1) : Math.min(rows.size() - 1, idx + 1);
        selectedId = rows.get(idx).id;
        float rowH = 24f;
        float rowTop = idx * rowH;
        float rowBottom = rowTop + rowH;
        float visibleH = Math.max(rowH, lastListH - 8f);
        if (rowTop < scroll) scroll = rowTop;
        else if (rowBottom > scroll + visibleH) scroll = Math.min(maxScroll, rowBottom - visibleH);
        return true;
      }
    }
    return false;
  }

  public boolean charTyped(int codePoint) {
    if (codePoint < 32 || codePoint == 127 || !Character.isValidCodePoint(codePoint)) return false;
    String inserted = new String(Character.toChars(codePoint));
    if (editing) {
      if (focusField == 0) {
        if (draftName.codePointCount(0, draftName.length()) < 32) draftName += inserted;
        return true;
      }
      if (codePoint <= 0xFFFF && (Character.isDigit((char) codePoint) || codePoint == '-' || codePoint == '+')) {
        if (focusField == 1 && draftX.length() < 10) draftX += inserted;
        else if (focusField == 2 && draftY.length() < 8) draftY += inserted;
        else if (focusField == 3 && draftZ.length() < 10) draftZ += inserted;
        return true;
      }
      return false;
    }
    if (searchFocused) {
      if (search.codePointCount(0, search.length()) < 32) search += inserted;
      scroll = 0f;
      return true;
    }
    return false;
  }

  private void backspaceActiveField() {
    if (focusField == 0 && !draftName.isEmpty()) draftName = removeLastCodePoint(draftName);
    else if (focusField == 1 && !draftX.isEmpty()) draftX = removeLastCodePoint(draftX);
    else if (focusField == 2 && !draftY.isEmpty()) draftY = removeLastCodePoint(draftY);
    else if (focusField == 3 && !draftZ.isEmpty()) draftZ = removeLastCodePoint(draftZ);
  }

  private static String removeLastCodePoint(String text) {
    if (text == null || text.isEmpty()) return "";
    int len = text.length();
    if (len >= 2 && Character.isLowSurrogate(text.charAt(len - 1))
        && Character.isHighSurrogate(text.charAt(len - 2))) {
      return text.substring(0, len - 2);
    }
    return text.substring(0, len - 1);
  }

  private void handleAction(String action) {
    String lang = I18n.language(settings.language);
    if (editing) {
      if (action.startsWith("field:")) {
        focusField = Integer.parseInt(action.substring(6));
      } else if (action.startsWith("color:")) {
        int idx = Integer.parseInt(action.substring(6));
        if (idx >= 0 && idx < PALETTE.length) draftColor = PALETTE[idx];
      } else if ("draft-temp".equals(action)) {
        draftTemp = !draftTemp;
      } else if ("draft-save".equals(action)) {
        saveDraft();
      } else if ("draft-cancel".equals(action)) {
        editing = false;
      }
      return;
    }

    if ("focus-search".equals(action)) {
      searchFocused = true;
      return;
    }
    searchFocused = false;

    if ("done".equals(action)) {
      host.close();
      return;
    }
    if ("toggle-filter".equals(action)) {
      allWorlds = !allWorlds;
      scroll = 0f;
      return;
    }
    if ("add".equals(action)) {
      double[] pos = host.playerPosition();
      double px = pos != null && pos.length >= 3 ? pos[0] : 0.0;
      double py = pos != null && pos.length >= 3 ? pos[1] : 64.0;
      double pz = pos != null && pos.length >= 3 ? pos[2] : 0.0;
      openAddDraft(px, py, pz);
      return;
    }
    if (action.startsWith("row-toggle:")) {
      String id = action.substring(11);
      Waypoint wp = settings.waypoints.find(id);
      if (wp != null) {
        host.checkpoint();
        wp.visible = !wp.visible;
        host.changed();
      }
      return;
    }
    if (action.startsWith("row:")) {
      String id = action.substring(4);
      boolean dbl = id.equals(lastClickedRow) && (lastTime - lastClickTime) < 0.35;
      lastClickedRow = id;
      lastClickTime = lastTime;
      selectedId = id;
      if (dbl) {
        openEditSelected();
      }
      return;
    }
    if ("edit".equals(action)) {
      openEditSelected();
      return;
    }
    if ("toggle".equals(action)) {
      Waypoint wp = settings.waypoints.find(selectedId);
      if (wp != null) {
        host.checkpoint();
        wp.visible = !wp.visible;
        host.changed();
      }
      return;
    }
    if ("tp".equals(action)) {
      Waypoint wp = settings.waypoints.find(selectedId);
      if (wp != null) {
        host.teleportTo((int) Math.round(wp.x), (int) Math.round(wp.y), (int) Math.round(wp.z));
        host.close();
      }
      return;
    }
    if ("share".equals(action)) {
      Waypoint wp = settings.waypoints.find(selectedId);
      if (wp != null) {
        String text = wp.displayName() + " (" + Math.round(wp.x) + " " + Math.round(wp.y) + " "
            + Math.round(wp.z) + ")";
        host.clipboard(text);
        statusMessage = I18n.text(lang, "waypointsMenu.status.shared");
        statusUntil = lastTime + 2.5;
      }
      return;
    }
    if ("delete".equals(action)) {
      if (selectedId != null && !selectedId.isEmpty()) {
        host.checkpoint();
        if (settings.waypoints.remove(selectedId)) {
          host.changed();
          statusMessage = I18n.text(lang, "waypointsMenu.status.deleted");
          statusUntil = lastTime + 2.5;
          selectedId = "";
        }
      }
      return;
    }
    if ("clear-temp".equals(action)) {
      host.checkpoint();
      boolean removed = false;
      Iterator<Waypoint> it = settings.waypoints.points.iterator();
      while (it.hasNext()) {
        Waypoint wp = it.next();
        if (wp != null && wp.temp) {
          it.remove();
          removed = true;
        }
      }
      if (removed) {
        host.changed();
        statusMessage = I18n.text(lang, "waypointsMenu.status.cleared_temp");
        statusUntil = lastTime + 2.5;
      }
    }
  }

  private void openEditSelected() {
    Waypoint wp = settings.waypoints.find(selectedId);
    if (wp == null) return;
    editing = true;
    editingId = wp.id;
    draftName = wp.label == null ? "" : wp.label;
    draftX = Integer.toString((int) Math.round(wp.x));
    draftY = Integer.toString((int) Math.round(wp.y));
    draftZ = Integer.toString((int) Math.round(wp.z));
    draftColor = wp.color | 0xFF000000;
    draftTemp = wp.temp;
    focusField = 0;
  }

  private void saveDraft() {
    double x = parseDouble(draftX, 0.0);
    double y = parseDouble(draftY, 64.0);
    double z = parseDouble(draftZ, 0.0);
    String name = draftName.trim();
    if (name.isEmpty()) name = Math.round(x) + ", " + Math.round(z);

    host.checkpoint();
    settings.waypoints.enabled = true;
    if (editingId.isEmpty()) {
      Waypoint wp = settings.waypoints.add(name, x, y, z, host.currentDimension(),
          host.currentWorldId(), host.currentWorldLabel());
      if (wp != null) {
        wp.color = draftColor | 0xFF000000;
        wp.temp = draftTemp;
        selectedId = wp.id;
      }
    } else {
      Waypoint wp = settings.waypoints.find(editingId);
      if (wp != null) {
        wp.label = name;
        wp.x = x;
        wp.y = y;
        wp.z = z;
        wp.color = draftColor | 0xFF000000;
        wp.temp = draftTemp;
      }
    }
    settings.validate();
    host.changed();
    editing = false;
    statusMessage = I18n.text(I18n.language(settings.language), "waypointsMenu.status.saved");
    statusUntil = lastTime + 2.5;
  }

  private List<Waypoint> filteredWaypoints() {
    List<Waypoint> out = new ArrayList<Waypoint>();
    if (settings.waypoints == null || settings.waypoints.points == null) return out;
    String curWorld = host.currentWorldId();
    String curDim = host.currentDimension();
    String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < settings.waypoints.points.size(); i++) {
      Waypoint wp = settings.waypoints.points.get(i);
      if (wp == null) continue;
      if (!allWorlds) {
        if (curDim != null && !curDim.isEmpty() && wp.dimension != null
            && !curDim.equalsIgnoreCase(wp.dimension)) {
          continue;
        }
        if (curWorld != null && !curWorld.isEmpty() && !wp.matchesWorld(curWorld)) continue;
      }
      if (!needle.isEmpty()) {
        String hay = (wp.displayName() + " " + Math.round(wp.x) + " " + Math.round(wp.z))
            .toLowerCase(Locale.ROOT);
        if (!hay.contains(needle)) continue;
      }
      out.add(wp);
    }
    return out;
  }

  private void ensureValidSelection(List<Waypoint> rows) {
    if (rows.isEmpty()) {
      selectedId = "";
      return;
    }
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).id.equals(selectedId)) return;
    }
    selectedId = rows.get(0).id;
  }

  private Waypoint findSelected(List<Waypoint> rows) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).id.equals(selectedId)) return rows.get(i);
    }
    return null;
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.trim().isEmpty()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String shortDim(String dim, String lang) {
    if (dim == null || dim.isEmpty()) return "";
    if (dim.endsWith("overworld")) return I18n.text(lang, "dimension.overworld");
    if (dim.endsWith("the_nether")) return I18n.text(lang, "dimension.nether");
    if (dim.endsWith("the_end")) return I18n.text(lang, "dimension.end");
    int c = dim.indexOf(':');
    return c >= 0 ? dim.substring(c + 1) : dim;
  }

  private static String initialOf(String text) {
    if (text == null || text.trim().isEmpty()) return "W";
    String t = text.trim();
    return new String(Character.toChars(Character.toUpperCase(t.codePointAt(0))));
  }
}
