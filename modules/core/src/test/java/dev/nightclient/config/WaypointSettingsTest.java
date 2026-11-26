package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaypointSettingsTest {
  private static final String SECTION = String.valueOf((char) 0x00a7);
  private static final String CONTROL = String.valueOf((char) 1);

  @Test
  void defaultsRenderABadgeAndEveryKnobStaysInsideItsRange() {
    Settings settings = new Settings();
    assertTrue(settings.waypoints.enabled);
    assertEquals(1.0f, settings.waypoints.badge);
    settings.waypoints.badge = 0;
    settings.validate();
    assertEquals(.5f, settings.waypoints.badge);
    settings.waypoints.badge = 99;
    settings.validate();
    assertEquals(2.0f, settings.waypoints.badge);
  }

  @Test
  void thereIsNoHudPanelLeftOnWaypoints() {
    Settings settings = new Settings();
    settings.validate();
    assertFalse(Settings.Panel.class.isInstance(settings.waypoints));
  }

  @Test
  void markersKeepNamesAndColoursInsideSafeBounds() {
    Settings settings = new Settings();
    List<Waypoint> points = new ArrayList<Waypoint>(Arrays.asList(
        new Waypoint("  Base " + SECTION + "l" + CONTROL + "  ", 10, 64, -20),
        new Waypoint(longLabel(), 0, 0, 0), new Waypoint("NaN", Double.NaN, 5, 5),
        new Waypoint("Infinity", 1, Double.POSITIVE_INFINITY, 1), new Waypoint("Far", 4.2E7, 0, 0),
        marker("icon", "minecraft:nether_portal" + CONTROL), null));
    settings.waypoints.points = points;
    settings.validate();
    assertEquals(4, settings.waypoints.points.size());
    Waypoint base = settings.waypoints.points.get(0);
    assertEquals("Base l", base.label);
    assertEquals("Base l", base.displayName());
    assertEquals(10, base.x);
    assertEquals(-20, base.z);
    assertEquals(32, settings.waypoints.points.get(1).label.length());
    assertEquals(longLabel().substring(0, 32), settings.waypoints.points.get(1).label);
    Waypoint far = settings.waypoints.points.get(2);
    assertEquals(Waypoint.COORDINATE_LIMIT, far.x);
    assertEquals("30000000 0 0", far.coordinates());
    Waypoint portal = settings.waypoints.points.get(3);
    assertEquals("minecraft:nether_portal", portal.icon);
    assertEquals(8, base.id.length());
    assertNotEquals(base.id, settings.waypoints.points.get(1).id);
    assertTrue(base.visible);
  }

  @Test
  void savedMarkersSurviveProfilesAndCanBeEditedAgain() throws Exception {
    java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("night-waypoints");
    try (ConfigStore store = new ConfigStore(directory)) {
      Settings settings = new Settings();
      settings.waypoints.enabled = true;
      settings.waypoints.badge = 1.5f;
      Waypoint added = settings.waypoints.add("Cache", 120.5, 70, -45.25);
      assertNotNull(added);
      added.icon = "minecraft:chest";
      added.color = 0x80ff8000;
      Settings decoded = store.decode(store.encode(settings));
      assertTrue(decoded.waypoints.enabled);
      assertEquals(1.5f, decoded.waypoints.badge);
      assertEquals(1, decoded.waypoints.points.size());
      Waypoint stored = decoded.waypoints.points.get(0);
      assertEquals(added.id, stored.id);
      assertEquals("Cache", stored.label);
      assertEquals(120.5, stored.x);
      assertEquals(-45.25, stored.z);
      assertEquals("minecraft:chest", stored.icon);
      assertEquals(0x80ff8000, stored.color);
      assertSame(stored, decoded.waypoints.find(stored.id));
      assertTrue(decoded.waypoints.remove(stored.id));
      assertFalse(decoded.waypoints.remove(stored.id));
      assertNull(decoded.waypoints.find(null));
      assertTrue(decoded.waypoints.points.isEmpty());
    }
  }

  @Test
  void theSavedListRefusesGrowthPastItsLimit() {
    Settings settings = new Settings();
    for (int i = 0; i < Settings.Waypoints.POINT_LIMIT; i++)
      assertNotNull(settings.waypoints.add("P" + i, i, i, i));
    assertNull(settings.waypoints.add("overflow", 0, 0, 0));
    settings.waypoints.points.add(new Waypoint("manual", 1, 2, 3));
    settings.validate();
    assertEquals(Settings.Waypoints.POINT_LIMIT, settings.waypoints.points.size());
  }

  @Test
  void malformedListsFallBackWithoutThrowing() {
    Settings settings = new Settings();
    settings.waypoints.points = null;
    settings.validate();
    assertNotNull(settings.waypoints.points);
    assertTrue(settings.waypoints.points.isEmpty());
    settings.waypoints.points.add(null);
    settings.waypoints.points.add(new Waypoint("ok", 1, 1, 1));
    settings.validate();
    assertEquals(1, settings.waypoints.points.size());
    assertEquals("ok", settings.waypoints.points.get(0).label);
    Waypoint blank = new Waypoint("   ", 1, 2, 3);
    blank.normalize();
    assertEquals("1 2 3", blank.displayName());
  }

  @Test
  void waypointsAreIsolatedPerWorldAndPerModeWithScopedDeathAndRemoval() {
    Settings settings = new Settings();
    String soloMode = "mp:holyworld.ru/s:b721aa4c0b4c5c9c";
    String duoMode = "mp:holyworld.ru/s:86dd62be254b6302";

    Waypoint a = settings.waypoints.add(
        "SoloBase", 10, 64, 20, Waypoint.DEFAULT_DIMENSION, soloMode, "holyworld.ru · СолоЛайт #6");
    Waypoint b = settings.waypoints.add(
        "DuoBase", 50, 70, -10, Waypoint.DEFAULT_DIMENSION, duoMode, "");
    assertNotNull(a);
    assertNotNull(b);
    assertTrue(a.matchesWorld(soloMode));
    assertFalse(a.matchesWorld(duoMode));
    assertFalse(a.matchesWorld(""));
    assertEquals("holyworld.ru · СолоЛайт #6", a.displayWorld());
    assertEquals("holyworld.ru · #86dd62", b.displayWorld());

    assertNotNull(settings.waypoints.addDeath(1, 64, 1, Waypoint.DEFAULT_DIMENSION, soloMode, ""));
    assertNotNull(settings.waypoints.addDeath(2, 64, 2, Waypoint.DEFAULT_DIMENSION, duoMode, ""));
    assertNotNull(settings.waypoints.addDeath(3, 64, 3, Waypoint.DEFAULT_DIMENSION, soloMode, ""));
    assertEquals(4, settings.waypoints.points.size());

    int removed = settings.waypoints.removeWorld(soloMode);
    assertEquals(2, removed);
    assertEquals(2, settings.waypoints.points.size());
    for (Waypoint remaining : settings.waypoints.points) {
      assertTrue(remaining.matchesWorld(duoMode));
    }
  }

  private static Waypoint marker(String label, String icon) {
    Waypoint point = new Waypoint(label, 1, 2, 3);
    point.icon = icon;
    return point;
  }

  private static String longLabel() {
    StringBuilder value = new StringBuilder();
    for (int i = 0; i < 48; i++) value.append('x');
    return value.toString();
  }
}
