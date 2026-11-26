package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.PlayerHealthView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.WorldRibbon;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointCommandsTest {
  @TempDir Path directory;

  private ClientRuntime createRuntime(
      final List<String> feedback, final List<PlayerHealthView> healths) throws Exception {
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    return constructor.newInstance(new PlatformServices() {
      @Override
      public Path configDirectory() {
        return directory;
      }

      @Override
      public String gameVersion() {
        return "fixture";
      }

      @Override
      public String clipboard() {
        return "";
      }

      @Override
      public void clipboard(String value) {}

      @Override
      public void closeMenu() {}

      @Override
      public List<Cooldown> cooldowns(float partial) {
        return Collections.emptyList();
      }

      @Override
      public String menuKeyLabel() {
        return "R SHIFT";
      }

      @Override
      public double[] playerPosition() {
        return new double[] {100.0, 64.0, -200.0};
      }

      @Override
      public String currentDimension() {
        return "minecraft:overworld";
      }

      @Override
      public void sendFeedback(String message) {
        feedback.add(message);
      }

      @Override
      public List<PlayerHealthView> playerHealths(float partialTick) {
        return healths;
      }

      @Override
      public WorldRibbon.View cameraView(int width, int height) {
        return new WorldRibbon.View(0, 64, 0, 0, 0, 70, 16f / 9, width, height);
      }
    });
  }

  @Test
  void slashAndDotWaypointCommandsManageWaypointsWithoutManglingChat() throws Exception {
    List<String> feedback = new ArrayList<String>();
    ClientRuntime runtime = createRuntime(feedback, Collections.<PlayerHealthView>emptyList());
    try {
      Settings s = runtime.settings();
      s.tweaksy.enabled = true;
      s.tweaksy.commandFix.enabled = true;
      s.waypoints.points.clear();

      assertEquals("", runtime.fixCommand("/wp here Spawn Base"));
      assertEquals(1, s.waypoints.points.size());
      assertEquals("Spawn Base", s.waypoints.points.get(0).displayName());
      assertEquals(100.0, s.waypoints.points.get(0).x, 0.01);

      assertEquals("", runtime.fixCommand(".wp add ~50 70 -100 Fortress"));
      assertEquals(2, s.waypoints.points.size());
      assertEquals("Fortress", s.waypoints.points.get(1).displayName());
      assertEquals(150.0, s.waypoints.points.get(1).x, 0.01);
      assertEquals(70.0, s.waypoints.points.get(1).y, 0.01);

      assertEquals("", runtime.fixCommand("/waypoint add End Portal 1200 40 -800"));
      assertEquals(3, s.waypoints.points.size());
      assertEquals("End Portal", s.waypoints.points.get(2).displayName());
      assertEquals(1200.0, s.waypoints.points.get(2).x, 0.01);

      assertEquals("", runtime.fixCommand(".waypoints toggle Fortress"));
      assertFalse(s.waypoints.points.get(1).visible);
      feedback.clear();
      assertEquals("", runtime.fixCommand("/цз list"));
      assertTrue(feedback.size() >= 4, "Header + 3 waypoint entries printed");

      assertEquals("", runtime.fixCommand("/wp del #2"));
      assertEquals(2, s.waypoints.points.size());
      assertEquals("", runtime.fixCommand(".wp remove Spawn Base"));
      assertEquals(1, s.waypoints.points.size());
      assertEquals("", runtime.fixCommand("/waypoints clear"));
      assertTrue(s.waypoints.points.isEmpty());

      assertEquals("...привет", runtime.fixCommand("...привет"));
      assertEquals("/home", runtime.fixCommand("/рщьу"));
    } finally {
      runtime.close();
    }
  }

  @Test
  void playerHealthScalesNaturallyWithPerspectiveDistance() throws Exception {
    List<PlayerHealthView> views = Arrays.asList(
        new PlayerHealthView(1, -2.0, 64, 5.0, 1.8f, 20f, 20f, 0f, false, false),
        new PlayerHealthView(2, 2.0, 64, 24.0, 1.8f, 20f, 20f, 0f, false, false));
    ClientRuntime runtime = createRuntime(new ArrayList<String>(), views);
    try {
      Settings s = runtime.settings();
      s.hud.enabled = false;
      s.skin.enabled = false;
      s.playerHealth.enabled = true;
      runtime.changed();

      final List<Float> barWidths = new ArrayList<Float>();
      Canvas canvas = (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if ("gradientRoundedRect".equals(m.getName())) barWidths.add((Float) a[2]);
            if (m.getReturnType() == float.class) {
              return m.getName().startsWith("textWidth") ? 12f : 0f;
            }
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });

      runtime.prepareHudFrame(0f, 1280, 720);
      runtime.renderHud(canvas, 1280, 720, 0f);

      assertEquals(2, barWidths.size());
      assertTrue(barWidths.get(1) > barWidths.get(0) * 1.8f,
          "Near player health bar scales larger in 3D perspective than distant bar: " + barWidths);
    } finally {
      runtime.close();
    }
  }

  @Test
  void waypointsRenderAndCommandsAreScopedPerServerMode() throws Exception {
    final String[] activeWorld = {"mp:holyworld.ru/s:b721aa4c0b4c5c9c"};
    final String[] activeLabel = {"holyworld.ru · СолоЛайт #6"};
    final List<String> feedback = new ArrayList<String>();
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    ClientRuntime runtime = constructor.newInstance(new PlatformServices() {
      @Override
      public Path configDirectory() {
        return directory;
      }

      @Override
      public double[] playerPosition() {
        return new double[] {0.0, 64.0, 12.0};
      }

      @Override
      public String currentDimension() {
        return "minecraft:overworld";
      }

      @Override
      public String currentWorldId() {
        return activeWorld[0];
      }

      @Override
      public String currentWorldLabel() {
        return activeLabel[0];
      }

      @Override
      public void sendFeedback(String message) {
        feedback.add(message);
      }

      @Override
      public WorldRibbon.View cameraView(int width, int height) {
        return new WorldRibbon.View(0, 64, 0, 0, 0, 70, 16f / 9, width, height);
      }
    });
    try {
      Settings s = runtime.settings();
      s.waypoints.points.clear();
      s.waypoints.enabled = true;

      assertEquals("", runtime.fixCommand("/wp add 0 64 14 SoloFarm"));
      activeWorld[0] = "mp:holyworld.ru/s:86dd62be254b6302";
      activeLabel[0] = "holyworld.ru · ДуоЛайт #10";
      assertEquals("", runtime.fixCommand("/wp add 0 64 14 DuoFarm"));

      feedback.clear();
      assertEquals("", runtime.fixCommand("/wp list"));
      String duoList = String.join("\n", feedback);
      assertTrue(duoList.contains("DuoFarm"));
      assertFalse(duoList.contains("SoloFarm"));

      feedback.clear();
      assertEquals("", runtime.fixCommand("/wp list all"));
      String allList = String.join("\n", feedback);
      assertTrue(allList.contains("DuoFarm"));
      assertTrue(allList.contains("SoloFarm"));

      final List<String> drawnTexts = new ArrayList<String>();
      Canvas canvas = (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if ("textFamily".equals(m.getName())) drawnTexts.add(String.valueOf(a[1]));
            if (m.getReturnType() == float.class) {
              return m.getName().startsWith("textWidth") ? 20f : 0f;
            }
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });
      runtime.renderWaypoints(canvas, 1280, 720);
      assertTrue(drawnTexts.contains("DuoFarm"));
      assertFalse(drawnTexts.contains("SoloFarm"));

      assertEquals("", runtime.fixCommand("/wp clear"));
      assertEquals(1, s.waypoints.points.size());
      assertEquals("SoloFarm", s.waypoints.points.get(0).displayName());
    } finally {
      runtime.close();
    }
  }

  @Test
  void dotWayCommandsAndEnlargeAndCacheClearWork() throws Exception {
    final List<String> feedback = new ArrayList<String>();
    final List<String> serverCmds = new ArrayList<String>();
    final int[] rescans = {0};
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    ClientRuntime runtime = constructor.newInstance(new PlatformServices() {
      @Override public Path configDirectory() { return directory; }
      @Override public double[] playerPosition() { return new double[] {10.0, 68.0, -20.0}; }
      @Override public String currentDimension() { return "minecraft:overworld"; }
      @Override public void sendFeedback(String message) { feedback.add(message); }
      @Override public void sendCommandToServer(String command) { serverCmds.add(command); }
      @Override public void rescanLoadedMapChunks() { rescans[0]++; }
    });
    try {
      Settings s = runtime.settings();
      s.tweaksy.enabled = true;
      s.tweaksy.commandFix.enabled = true;
      s.waypoints.points.clear();

      assertEquals("", runtime.fixCommand(".way add RaidBase"));
      assertEquals(1, s.waypoints.points.size());
      assertEquals("RaidBase", s.waypoints.points.get(0).displayName());

      assertEquals("", runtime.fixCommand(".way add Stash 250 -400 #22C55E"));
      assertEquals(2, s.waypoints.points.size());
      assertEquals("Stash", s.waypoints.points.get(1).displayName());
      assertEquals(250.0, s.waypoints.points.get(1).x, 0.01);
      assertEquals(-400.0, s.waypoints.points.get(1).z, 0.01);
      assertEquals(0xFF22C55E, s.waypoints.points.get(1).color);

      assertEquals("", runtime.fixCommand(".цфн add Ферма 10 64 20"));
      assertEquals(3, s.waypoints.points.size());
      assertEquals("Ферма", s.waypoints.points.get(2).displayName());

      assertEquals("", runtime.fixCommand(".way tp Stash"));
      assertEquals(1, serverCmds.size());
      assertEquals("tp 250 68 -400", serverCmds.get(0));

      s.minimap.enabled = true;
      s.minimap.enlargeMode = "hold";
      runtime.updateMapEnlargeKey(true, true);
      assertTrue(runtime.isMapEnlarged());
      runtime.updateMapEnlargeKey(false, false);
      assertFalse(runtime.isMapEnlarged());

      s.minimap.enlargeMode = "toggle";
      runtime.updateMapEnlargeKey(false, true);
      assertTrue(runtime.isMapEnlarged());
      runtime.updateMapEnlargeKey(false, true);
      assertFalse(runtime.isMapEnlarged());

      runtime.clearCurrentWorldMapCache();
      runtime.clearAllMapCache();
      assertEquals(2, rescans[0]);
    } finally {
      runtime.close();
    }
  }
}
