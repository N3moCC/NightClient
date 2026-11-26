package dev.nightclient.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AccountButtonLayout {
  public static final int PAD = 3;
  public static final int MARGIN = 8;
  public static final int MODERN_MARGIN = 10;
  public static final int HEADER_Y = 6;
  public static final int TITLE_HALF_CLEARANCE = 66;

  public static final class Placement {
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Placement(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  private AccountButtonLayout() {}

  public static Placement place(int viewportWidth, int viewportHeight, int buttonWidth,
      int buttonHeight, boolean isTitleScreen, boolean classicMode, String preferredPosition,
      List<int[]> obstacles) {
    int vw = Math.max(120, viewportWidth);
    int vh = Math.max(80, viewportHeight);
    int bw = Math.max(20, Math.min(buttonWidth, vw - 12));
    int bh = Math.max(18, Math.min(buttonHeight, vh - 12));
    List<int[]> obs = obstacles == null ? Collections.<int[]>emptyList() : obstacles;
    String pos = preferredPosition == null || preferredPosition.isEmpty() ? "auto" : preferredPosition;

    if (isTitleScreen) {
      return placeTitleScreen(vw, vh, bw, bh, classicMode, pos, obs);
    }
    return placeMultiplayerScreen(vw, vh, bw, bh, classicMode, pos, obs);
  }

  public static int titleStatusTextY(int viewportWidth, int viewportHeight, List<int[]> obstacles,
      boolean iasLoaded) {
    if (iasLoaded || viewportWidth < 180 || viewportHeight < 120) return -1;
    List<int[]> obs = obstacles == null ? Collections.<int[]>emptyList() : obstacles;
    int centerX = viewportWidth / 2;
    int colBottom = -1;
    int colTop = Integer.MAX_VALUE;
    for (int i = 0; i < obs.size(); i++) {
      int[] r = obs.get(i);
      if (r == null || r.length < 4 || r[2] < 80) continue;
      int rxCenter = r[0] + r[2] / 2;
      if (Math.abs(rxCenter - centerX) <= 120) {
        if (r[1] < colTop) colTop = r[1];
        if (r[1] + r[3] > colBottom) colBottom = r[1] + r[3];
      }
    }
    int bandW = Math.min(200, viewportWidth - 24);
    int bandX = centerX - bandW / 2;
    int belowY = colBottom > 0 ? colBottom + 8 : viewportHeight / 4 + 162;
    if (belowY + 11 <= viewportHeight - 16 && !collides(bandX, belowY, bandW, 11, obs, 2)) {
      return belowY;
    }
    int aboveY = colTop < Integer.MAX_VALUE ? colTop - 15 : viewportHeight / 4 + 34;
    if (aboveY >= 36 && !collides(bandX, aboveY, bandW, 11, obs, 2)) {
      return aboveY;
    }
    return -1;
  }

  private static Placement placeTitleScreen(int vw, int vh, int bw, int bh, boolean classicMode,
      String pos, List<int[]> obs) {
    if ("topRight".equals(pos)) {
      int m = classicMode ? MARGIN : MODERN_MARGIN;
      return placeTopRight(vw, vh, bw, bh, m, m, obs);
    }
    if ("topLeft".equals(pos)) {
      int m = classicMode ? MARGIN : MODERN_MARGIN;
      return placeTopLeft(vw, vh, bw, bh, m, m, obs);
    }
    if ("bottomLeft".equals(pos)) {
      int bottomY = Math.max(MARGIN, vh - bh - 24);
      Placement p = placeCornerScan(vw, vh, bw, bh, MARGIN, bottomY, 1, -1, obs);
      return p != null ? p : new Placement(MARGIN, bottomY, bw, bh);
    }
    boolean wantBeside = "besideButtons".equals(pos) || ("auto".equals(pos) && classicMode);
    if (wantBeside) {
      Placement beside = tryBesideTitleButtons(vw, vh, bw, bh, obs);
      if (beside != null) return beside;
    }
    int m = classicMode ? MARGIN : MODERN_MARGIN;
    return placeTopRight(vw, vh, bw, bh, m, m, obs);
  }

  private static Placement tryBesideTitleButtons(int vw, int vh, int bw, int bh, List<int[]> obs) {
    int centerX = vw / 2;
    int colLeft = centerX - 100;
    int colRight = centerX + 100;
    List<Integer> rowYs = new ArrayList<Integer>();

    for (int i = 0; i < obs.size(); i++) {
      int[] r = obs.get(i);
      if (r == null || r.length < 4) continue;
      int rw = r[2];
      int rh = r[3];
      if (rh < 16 || rh > 28) continue;
      int rxCenter = r[0] + rw / 2;
      if (rw >= 150 && rw <= 240 && Math.abs(rxCenter - centerX) <= 32) {
        colLeft = Math.min(colLeft, r[0]);
        colRight = Math.max(colRight, r[0] + rw);
        addUniqueRow(rowYs, r[1]);
      } else if (rw >= 88 && rw <= 115 && Math.abs(rxCenter - centerX) <= 64) {
        colLeft = Math.min(colLeft, r[0]);
        colRight = Math.max(colRight, r[0] + rw);
        addUniqueRow(rowYs, r[1]);
      }
    }

    if (rowYs.isEmpty()) {
      int base = vh / 4 + 48;
      rowYs.add(base);
      rowYs.add(base + 24);
      rowYs.add(base + 48);
    } else {
      Collections.sort(rowYs);
    }

    List<Integer> orderedRows = new ArrayList<Integer>();
    int[] prefIdx = {1, 0, 2};
    for (int idx : prefIdx) {
      if (idx < rowYs.size()) orderedRows.add(rowYs.get(idx));
    }
    for (int i = 3; i < rowYs.size(); i++) orderedRows.add(rowYs.get(i));

    int gap = 4;
    for (int i = 0; i < orderedRows.size(); i++) {
      int ry = orderedRows.get(i);
      int y = clamp(ry + (20 - bh) / 2, MARGIN, vh - bh - MARGIN);
      int rx = colRight + gap;
      if (inBounds(rx, y, bw, bh, vw, vh) && !collides(rx, y, bw, bh, obs, PAD)) {
        return new Placement(rx, y, bw, bh);
      }
      int lx = colLeft - bw - gap;
      if (inBounds(lx, y, bw, bh, vw, vh) && !collides(lx, y, bw, bh, obs, PAD)) {
        return new Placement(lx, y, bw, bh);
      }
    }

    int step = Math.max(24, bw + gap);
    for (int i = 0; i < orderedRows.size(); i++) {
      int ry = orderedRows.get(i);
      int y = clamp(ry + (20 - bh) / 2, MARGIN, vh - bh - MARGIN);
      int rx2 = colRight + gap + step;
      if (inBounds(rx2, y, bw, bh, vw, vh) && !collides(rx2, y, bw, bh, obs, PAD)) {
        return new Placement(rx2, y, bw, bh);
      }
      int lx2 = colLeft - bw - gap - step;
      if (inBounds(lx2, y, bw, bh, vw, vh) && !collides(lx2, y, bw, bh, obs, PAD)) {
        return new Placement(lx2, y, bw, bh);
      }
    }
    return null;
  }

  private static Placement placeMultiplayerScreen(int vw, int vh, int bw, int bh,
      boolean classicMode, String pos, List<int[]> obs) {
    if ("bottomLeft".equals(pos)) {
      int bottomY = Math.max(HEADER_Y, vh - bh - 6);
      Placement p = tryScanHorizontal(vw, vh, bw, bh, MARGIN, bottomY, 1, vw / 2 - 40, obs);
      if (p != null) return p;
      int upperBottomY = Math.max(HEADER_Y, vh - bh - 30);
      if (inBounds(MARGIN, upperBottomY, bw, bh, vw, vh)
          && !collides(MARGIN, upperBottomY, bw, bh, obs, PAD)) {
        return new Placement(MARGIN, upperBottomY, bw, bh);
      }
      return placeHeaderLeftThenRight(vw, vh, bw, bh, obs);
    }
    if ("headerRight".equals(pos)) {
      return placeHeaderRightThenLeft(vw, vh, bw, bh, obs);
    }
    if ("headerLeft".equals(pos)) {
      return placeHeaderLeftThenRight(vw, vh, bw, bh, obs);
    }
    if (classicMode) {
      return placeHeaderRightThenLeft(vw, vh, bw, bh, obs);
    }
    return placeHeaderLeftThenRight(vw, vh, bw, bh, obs);
  }

  private static Placement placeHeaderRightThenLeft(int vw, int vh, int bw, int bh,
      List<int[]> obs) {
    int minRightX = vw / 2 + TITLE_HALF_CLEARANCE;
    int maxLeftX = vw / 2 - TITLE_HALF_CLEARANCE;
    Placement p = tryHeaderRight(vw, vh, bw, bh, minRightX, obs);
    if (p != null) return p;
    p = tryHeaderLeft(vw, vh, bw, bh, maxLeftX, obs);
    if (p != null) return p;
    if (bw > 20) {
      p = tryHeaderRight(vw, vh, 20, bh, minRightX, obs);
      if (p != null) return p;
      p = tryHeaderLeft(vw, vh, 20, bh, maxLeftX, obs);
      if (p != null) return p;
    }
    p = tryHeaderRight(vw, vh, bw, bh, vw / 2 + 28, obs);
    if (p != null) return p;
    p = tryHeaderLeft(vw, vh, bw, bh, vw / 2 - 28, obs);
    if (p != null) return p;
    return new Placement(clamp(vw - bw - MARGIN, 4, vw - bw - 4), HEADER_Y, bw, bh);
  }

  private static Placement placeHeaderLeftThenRight(int vw, int vh, int bw, int bh,
      List<int[]> obs) {
    int maxLeftX = vw / 2 - TITLE_HALF_CLEARANCE;
    int minRightX = vw / 2 + TITLE_HALF_CLEARANCE;
    Placement p = tryHeaderLeft(vw, vh, bw, bh, maxLeftX, obs);
    if (p != null) return p;
    p = tryHeaderRight(vw, vh, bw, bh, minRightX, obs);
    if (p != null) return p;
    if (bw > 20) {
      p = tryHeaderLeft(vw, vh, 20, bh, maxLeftX, obs);
      if (p != null) return p;
      p = tryHeaderRight(vw, vh, 20, bh, minRightX, obs);
      if (p != null) return p;
    }
    p = tryHeaderLeft(vw, vh, bw, bh, vw / 2 - 28, obs);
    if (p != null) return p;
    p = tryHeaderRight(vw, vh, bw, bh, vw / 2 + 28, obs);
    if (p != null) return p;
    return new Placement(MARGIN, HEADER_Y, bw, bh);
  }

  private static Placement tryHeaderLeft(int vw, int vh, int bw, int bh, int maxRightEdge,
      List<int[]> obs) {
    int y = HEADER_Y;
    int x = MARGIN;
    List<int[]> headerObs = headerObstacles(obs, y, bh);
    for (int attempt = 0; attempt < 8; attempt++) {
      if (x + bw > maxRightEdge || !inBounds(x, y, bw, bh, vw, vh)) return null;
      int[] hit = firstCollision(x, y, bw, bh, headerObs, PAD);
      if (hit == null) return new Placement(x, y, bw, bh);
      x = hit[0] + hit[2] + PAD + 2;
    }
    return null;
  }

  private static Placement tryHeaderRight(int vw, int vh, int bw, int bh, int minLeftEdge,
      List<int[]> obs) {
    int y = HEADER_Y;
    int x = vw - bw - MARGIN;
    List<int[]> headerObs = headerObstacles(obs, y, bh);
    for (int attempt = 0; attempt < 8; attempt++) {
      if (x < minLeftEdge || !inBounds(x, y, bw, bh, vw, vh)) return null;
      int[] hit = firstCollision(x, y, bw, bh, headerObs, PAD);
      if (hit == null) return new Placement(x, y, bw, bh);
      x = hit[0] - bw - PAD - 2;
    }
    return null;
  }

  private static List<int[]> headerObstacles(List<int[]> obs, int y, int bh) {
    List<int[]> list = new ArrayList<int[]>();
    for (int i = 0; i < obs.size(); i++) {
      int[] r = obs.get(i);
      if (r != null && r.length >= 4 && r[1] < y + bh + 8 && r[1] + r[3] > y - 4) {
        list.add(r);
      }
    }
    Collections.sort(list, new Comparator<int[]>() {
      @Override public int compare(int[] a, int[] b) {
        return Integer.compare(a[0], b[0]);
      }
    });
    return list;
  }

  private static Placement placeTopRight(int vw, int vh, int bw, int bh, int mx, int my,
      List<int[]> obs) {
    int startX = clamp(vw - bw - mx, 4, vw - bw - 4);
    Placement p = placeCornerScan(vw, vh, bw, bh, startX, my, -1, 1, obs);
    if (p != null) return p;
    p = placeCornerScan(vw, vh, bw, bh, mx, my, 1, 1, obs);
    if (p != null) return p;
    return new Placement(startX, my, bw, bh);
  }

  private static Placement placeTopLeft(int vw, int vh, int bw, int bh, int mx, int my,
      List<int[]> obs) {
    Placement p = placeCornerScan(vw, vh, bw, bh, mx, my, 1, 1, obs);
    if (p != null) return p;
    int rx = clamp(vw - bw - mx, 4, vw - bw - 4);
    p = placeCornerScan(vw, vh, bw, bh, rx, my, -1, 1, obs);
    if (p != null) return p;
    return new Placement(mx, my, bw, bh);
  }

  private static Placement placeCornerScan(int vw, int vh, int bw, int bh, int startX, int startY,
      int dirX, int dirY, List<int[]> obs) {
    if (inBounds(startX, startY, bw, bh, vw, vh) && !collides(startX, startY, bw, bh, obs, PAD)) {
      return new Placement(startX, startY, bw, bh);
    }
    int x = startX;
    for (int i = 0; i < 6; i++) {
      int[] hit = firstCollision(x, startY, bw, bh, obs, PAD);
      if (hit == null && inBounds(x, startY, bw, bh, vw, vh)) {
        return new Placement(x, startY, bw, bh);
      }
      if (hit == null) break;
      x = dirX < 0 ? hit[0] - bw - PAD - 2 : hit[0] + hit[2] + PAD + 2;
      if (!inBounds(x, startY, bw, bh, vw, vh)) break;
    }
    int y = startY;
    for (int i = 0; i < 6; i++) {
      int[] hit = firstCollision(startX, y, bw, bh, obs, PAD);
      if (hit == null && inBounds(startX, y, bw, bh, vw, vh)) {
        return new Placement(startX, y, bw, bh);
      }
      if (hit == null) break;
      y = dirY > 0 ? hit[1] + hit[3] + PAD + 2 : hit[1] - bh - PAD - 2;
      if (!inBounds(startX, y, bw, bh, vw, vh)) break;
    }
    return null;
  }

  private static Placement tryScanHorizontal(int vw, int vh, int bw, int bh, int startX, int y,
      int dirX, int limitEdge, List<int[]> obs) {
    int x = startX;
    for (int i = 0; i < 6; i++) {
      if (dirX > 0 && x + bw > limitEdge) return null;
      if (dirX < 0 && x < limitEdge) return null;
      if (!inBounds(x, y, bw, bh, vw, vh)) return null;
      int[] hit = firstCollision(x, y, bw, bh, obs, PAD);
      if (hit == null) return new Placement(x, y, bw, bh);
      x = dirX > 0 ? hit[0] + hit[2] + PAD + 2 : hit[0] - bw - PAD - 2;
    }
    return null;
  }

  private static void addUniqueRow(List<Integer> rows, int y) {
    for (int i = 0; i < rows.size(); i++) {
      if (Math.abs(rows.get(i) - y) <= 4) return;
    }
    rows.add(y);
  }

  private static boolean inBounds(int x, int y, int w, int h, int vw, int vh) {
    return x >= 4 && y >= 4 && x + w <= vw - 4 && y + h <= vh - 4;
  }

  private static boolean collides(int x, int y, int w, int h, List<int[]> obs, int pad) {
    return firstCollision(x, y, w, h, obs, pad) != null;
  }

  private static int[] firstCollision(int x, int y, int w, int h, List<int[]> obs, int pad) {
    for (int i = 0; i < obs.size(); i++) {
      int[] r = obs.get(i);
      if (r == null || r.length < 4 || r[2] <= 0 || r[3] <= 0) continue;
      if (x < r[0] + r[2] + pad && x + w + pad > r[0] && y < r[1] + r[3] + pad
          && y + h + pad > r[1]) {
        return r;
      }
    }
    return null;
  }

  private static int clamp(int value, int min, int max) {
    if (max < min) return min;
    return Math.max(min, Math.min(max, value));
  }
}
