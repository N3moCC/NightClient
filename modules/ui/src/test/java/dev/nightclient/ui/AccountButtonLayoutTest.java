package dev.nightclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountButtonLayoutTest {

  private static List<int[]> vanillaTitleWidgets(int vw, int vh) {
    int cx = vw / 2 - 100;
    int y0 = vh / 4 + 48;
    return Arrays.asList(
        new int[] {cx, y0, 200, 20},
        new int[] {cx, y0 + 24, 200, 20},
        new int[] {cx, y0 + 48, 200, 20},
        new int[] {cx, y0 + 84, 98, 20},
        new int[] {cx + 102, y0 + 84, 98, 20},
        new int[] {cx - 24, y0 + 84, 20, 20},
        new int[] {cx + 204, y0 + 84, 20, 20}
    );
  }

  private static void assertNoOverlap(AccountButtonLayout.Placement p, List<int[]> obstacles) {
    for (int[] o : obstacles) {
      boolean overlaps = p.x < o[0] + o[2] && p.x + p.width > o[0]
          && p.y < o[1] + o[3] && p.y + p.height > o[1];
      assertFalse(overlaps, "Placement [" + p.x + "," + p.y + "," + p.width + "x" + p.height
          + "] overlaps obstacle [" + o[0] + "," + o[1] + "," + o[2] + "x" + o[3] + "]");
    }
  }

  @Test
  void titleAndMultiplayerUseDistinctAdaptiveDefaults() {
    int vw = 640, vh = 360;
    List<int[]> titleObs = vanillaTitleWidgets(vw, vh);

    AccountButtonLayout.Placement modernTitle =
        AccountButtonLayout.place(vw, vh, 130, 24, true, false, "auto", titleObs);
    AccountButtonLayout.Placement modernMulti =
        AccountButtonLayout.place(vw, vh, 120, 20, false, false, "auto", Collections.<int[]>emptyList());
    assertTrue(modernTitle.x > vw / 2, "Modern TitleScreen should default to top-right");
    assertTrue(modernMulti.x < vw / 4, "Modern MultiplayerScreen should default to header-left");
    assertEquals(AccountButtonLayout.HEADER_Y, modernMulti.y);

    AccountButtonLayout.Placement classicTitle =
        AccountButtonLayout.place(vw, vh, 20, 20, true, true, "auto", titleObs);
    AccountButtonLayout.Placement classicMulti =
        AccountButtonLayout.place(vw, vh, 100, 20, false, true, "auto", Collections.<int[]>emptyList());
    int mpY = vh / 4 + 48 + 24;
    assertEquals(vw / 2 + 104, classicTitle.x, "Classic TitleScreen should sit beside Multiplayer");
    assertEquals(mpY, classicTitle.y);
    assertTrue(classicMulti.x > vw / 2 + AccountButtonLayout.TITLE_HALF_CLEARANCE,
        "Classic MultiplayerScreen should default to header-right");
    assertEquals(AccountButtonLayout.HEADER_Y, classicMulti.y);
    assertNoOverlap(classicTitle, titleObs);
  }

  @Test
  void avoidsStandaloneIasButtonOnTitleScreen() {
    int vw = 640, vh = 360;
    int mpY = vh / 4 + 48 + 24;
    List<int[]> withIas = Arrays.asList(
        new int[] {vw / 2 - 100, vh / 4 + 48, 200, 20},
        new int[] {vw / 2 - 100, mpY, 200, 20},
        new int[] {vw / 2 - 100, mpY + 24, 200, 20},
        new int[] {vw / 2 + 104, mpY, 20, 20}
    );
    AccountButtonLayout.Placement p =
        AccountButtonLayout.place(vw, vh, 20, 20, true, true, "auto", withIas);
    assertNoOverlap(p, withIas);
    assertEquals(vw / 2 - 124, p.x, "Should smoothly shift to symmetric left slot beside Multiplayer");
    assertEquals(mpY, p.y);
  }

  @Test
  void avoidsIasAndViaFabricPlusInMultiplayerHeader() {
    int vw = 480, vh = 270;
    List<int[]> headerMods = Arrays.asList(
        new int[] {vw - 103, 5, 98, 20},
        new int[] {vw - 128, 6, 20, 20}
    );
    AccountButtonLayout.Placement classicMulti =
        AccountButtonLayout.place(vw, vh, 96, 20, false, true, "auto", headerMods);
    assertNoOverlap(classicMulti, headerMods);
    assertTrue(classicMulti.x < vw / 2 - AccountButtonLayout.TITLE_HALF_CLEARANCE,
        "Should flip to header-left when header-right is occupied by ViaFabricPlus + IAS");
    assertEquals(AccountButtonLayout.HEADER_Y, classicMulti.y);
  }

  @Test
  void titleStatusTextSuppressedWhenIasInstalled() {
    int vw = 640, vh = 360;
    List<int[]> obs = vanillaTitleWidgets(vw, vh);
    assertTrue(AccountButtonLayout.titleStatusTextY(vw, vh, obs, false) > 0,
        "Status text Y should be active when IAS is not loaded");
    assertEquals(-1, AccountButtonLayout.titleStatusTextY(vw, vh, obs, true),
        "Status text must be suppressed when standalone IAS is installed");
  }

  @Test
  void modernTitlePillSlidesAwayFromTopRightWidget() {
    int vw = 640, vh = 360;
    List<int[]> obs = Collections.singletonList(new int[] {vw - 130, 8, 120, 24});
    AccountButtonLayout.Placement p =
        AccountButtonLayout.place(vw, vh, 130, 24, true, false, "auto", obs);
    assertNoOverlap(p, obs);
    assertTrue(p.x + p.width <= vw - 130, "Should slide left past existing top-right widget");
  }
}
