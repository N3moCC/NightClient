package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import org.junit.jupiter.api.Test;

class GridLayoutTest {
  private SkinView view(String tree) throws Exception {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    return new SkinView(NcParser.parse(tree), s, new SkinViewTest.Actions());
  }
  @Test
  void measuredRowsUseEqualColumnsAndCollapseBeforeCardsBecomeTooNarrow() throws Exception {
    SkinView v =
        view("menu { layout=grid; columns=2; minCellWidth=100px; gap=10px; padding=10px; button "
             + "(id=a) {height=30px}; button (id=b) {height=50px}; button (id=c) {height=40px} }");
    v.renderMenu(new SkinViewTest.Sink(), 300, 240, -1, -1, 0);
    float[] a = v.elementBounds("a"), b = v.elementBounds("b"), c = v.elementBounds("c");
    assertArrayEquals(new float[] {10, 10, 135, 30}, a, .01f);
    assertArrayEquals(new float[] {155, 10, 135, 50}, b, .01f);
    assertArrayEquals(new float[] {10, 70, 135, 40}, c, .01f);
    v.renderMenu(new SkinViewTest.Sink(), 190, 240, -1, -1, 1);
    assertArrayEquals(new float[] {10, 10, 170, 30}, v.elementBounds("a"), .01f);
    assertArrayEquals(new float[] {10, 50, 170, 50}, v.elementBounds("b"), .01f);
    assertArrayEquals(new float[] {10, 110, 170, 40}, v.elementBounds("c"), .01f);
  }
  @Test
  void scrollingAndHitTargetsFollowTheSameGridRows() throws Exception {
    SkinView v = view("menu { layout=grid; columns=2; gap=8px; overflow=scroll; button (id=a, "
                      + "action=page:first) {height=50px}; button (id=b) {height=50px}; button "
                      + "(id=c, action=page:third) {height=50px}; button (id=d) {height=50px} }");
    v.renderMenu(new SkinViewTest.Sink(), 240, 60, 20, 20, 0);
    assertNotNull(v.elementBounds("a"));
    assertTrue(v.mouseScrolled(20, 20, -2));
    v.renderMenu(new SkinViewTest.Sink(), 240, 60, 20, 20, 1);
    float[] third = v.elementBounds("c");
    assertNotNull(third);
    assertTrue(third[1] < 58);
    assertTrue(v.mouseClicked(third[0] + 5, third[1] + third[3] / 2, 0));
    assertEquals("third", v.page());
  }
  @Test
  void minAndMaxHeightReserveActualClampedSpaceForTheNextRow() throws Exception {
    SkinView v = view(
        "menu { layout=grid; columns=2; gap=10px; button (id=a) {height=30px; minHeight=60px}; "
        + "button (id=b) {height=100px; maxHeight=40px}; button (id=c) {height=40px} }");
    v.renderMenu(new SkinViewTest.Sink(), 300, 200, -1, -1, 0);
    assertEquals(60, v.elementBounds("a")[3], .01f);
    assertEquals(40, v.elementBounds("b")[3], .01f);
    assertEquals(70, v.elementBounds("c")[1], .01f);
  }
  @Test
  void scrollbarDragsToLastRowWithoutChangingSettingsOrUndoHistory() throws Exception {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView v =
        new SkinView(NcParser.parse("menu {layout=grid; columns=2; gap=8px; overflow=scroll; "
                                     + "button (id=a) {height=50px}; button (id=b) {height=50px}; "
                                     + "button (id=c) {height=50px}; button (id=d) {height=50px}}"),
            s, actions);
    v.renderMenu(new SkinViewTest.Sink(), 240, 60, -1, -1, 0);
    assertTrue(v.mouseClicked(238, 8, 0));
    assertTrue(v.mouseDragged(238, 60, 0, 0, 52));
    v.mouseReleased(238, 60, 0);
    v.renderMenu(new SkinViewTest.Sink(), 240, 60, -1, -1, 1);
    assertEquals(10, v.elementBounds("c")[1], .01f);
    assertEquals(50, v.elementBounds("c")[3], .01f);
    assertEquals(0, actions.changes);
    assertEquals(0, actions.checkpoints);
  }
}
