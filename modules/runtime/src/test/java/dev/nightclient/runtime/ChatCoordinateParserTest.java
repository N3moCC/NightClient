package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ChatCoordinateParserTest {
  @Test
  public void clicksSelectOnlyTheCoordinateGroupUnderThePointer() {
    String line = "First 100 64 -250; second -1500/70/3200";
    assertArrayEquals(
        new double[] {100, 64, -250}, ChatCoordinateParser.parseAt(line, 42, line.indexOf("100")));
    assertArrayEquals(new double[] {-1500, 70, 3200},
        ChatCoordinateParser.parseAt(line, 42, line.indexOf("3200")));
    assertNull(ChatCoordinateParser.parseAt(line, 42, line.indexOf("second")));
    assertArrayEquals(
        new double[] {2000, 42, -1500}, ChatCoordinateParser.parseAt("meet 2000 -1500", 42, 7));
  }
  @Test
  public void clickedChatLineTracksScaleScrollAndVisibleBounds() {
    assertEquals(0, ChatCoordinateParser.clickedLine(359, 400, 1, 9, 10, 0, 20));
    assertEquals(4, ChatCoordinateParser.clickedLine(350, 400, 1, 9, 10, 3, 20));
    assertEquals(5, ChatCoordinateParser.clickedLine(350, 400, .5, 10, 10, 3, 20));
    assertEquals(-1, ChatCoordinateParser.clickedLine(361, 400, 1, 9, 10, 0, 20));
    assertEquals(-1, ChatCoordinateParser.clickedLine(260, 400, 1, 9, 10, 0, 20));
    assertEquals(-1, ChatCoordinateParser.clickedLine(350, 400, 0, 9, 10, 0, 20));
  }
  @Test
  public void testParse3Coords() {
    double[] c1 = ChatCoordinateParser.parse("Base is at 100 64 -250, come quick!", 64);
    assertNotNull(c1);
    assertEquals(100.0, c1[0], 0.001);
    assertEquals(64.0, c1[1], 0.001);
    assertEquals(-250.0, c1[2], 0.001);

    double[] c2 = ChatCoordinateParser.parse("Coords: -1500/70/3200", 64);
    assertNotNull(c2);
    assertEquals(-1500.0, c2[0], 0.001);
    assertEquals(70.0, c2[1], 0.001);
    assertEquals(3200.0, c2[2], 0.001);

    double[] c3 = ChatCoordinateParser.parse("x: +500, y: -20, z: +1000", 64);
    assertNotNull(c3);
    assertEquals(500.0, c3[0], 0.001);
    assertEquals(-20.0, c3[1], 0.001);
    assertEquals(1000.0, c3[2], 0.001);

    double[] c4 = ChatCoordinateParser.parse("[120.5, 65.0, -450.5]", 64);
    assertNotNull(c4);
    assertEquals(120.5, c4[0], 0.001);
    assertEquals(65.0, c4[1], 0.001);
    assertEquals(-450.5, c4[2], 0.001);
  }

  @Test
  public void testParse2Coords() {
    double[] c1 = ChatCoordinateParser.parse("meet me at 2000 -1500", 64);
    assertNotNull(c1);
    assertEquals(2000.0, c1[0], 0.001);
    assertEquals(64.0, c1[1], 0.001);
    assertEquals(-1500.0, c1[2], 0.001);
  }

  @Test
  public void testMinecraftFormatting() {
    double[] c1 = ChatCoordinateParser.parse("§aPlayer: §fCoords are §e100 §664 §c-500", 64);
    assertNotNull(c1);
    assertEquals(100.0, c1[0], 0.001);
    assertEquals(64.0, c1[1], 0.001);
    assertEquals(-500.0, c1[2], 0.001);
  }
}
