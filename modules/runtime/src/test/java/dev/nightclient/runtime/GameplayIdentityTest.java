package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.TargetView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.WorldRibbon;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayIdentityTest {
  @TempDir Path directory;
  private ClientRuntime runtime(PlatformServices platform) throws Exception {
    Constructor<ClientRuntime> c =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    c.setAccessible(true);
    return c.newInstance(platform);
  }
  @Test
  void localProfileKeepsActualSkinAndProtectsNameWithoutTargetHud() throws Exception {
    TargetView actual =
        new TargetView(7, "uuid", "PlayerName", 20, 20, 0, 0, true, "minecraft:skins/actual", null);
    ClientRuntime r = runtime(new PlatformServices() {
      public Path configDirectory() {
        return directory;
      }
      public TargetView localPlayer(float tick) {
        return actual;
      }
    });
    try {
      assertFalse(r.settings().target.enabled);
      assertSame(actual, r.localPlayer());
      r.settings().protect.enabled = true;
      r.settings().protect.hideNickname = true;
      r.settings().protect.replacement = "Private";
      assertEquals("Private", r.localPlayer().name);
      assertEquals(actual.headTexture, r.localPlayer().headTexture);
      assertEquals("PlayerName", actual.name);
      r.settings().protect.enabled = false;
      assertSame(actual, r.localPlayer());
    } finally {
      r.shutdown();
    }
  }
  @Test
  void waypointsRemainVisibleBeyondFiveHundredMetersButRespectDimensionAndCamera()
      throws Exception {
    ClientRuntime r = runtime(new PlatformServices() {
      public Path configDirectory() {
        return directory;
      }
      public WorldRibbon.View cameraView(int w, int h) {
        return new WorldRibbon.View(0, 64, 0, 0, 0, 70, w / (float) h, w, h);
      }
      public String currentDimension() {
        return "minecraft:overworld";
      }
    });
    try {
      Settings s = r.settings();
      s.waypoints.enabled = true;
      s.waypoints.add("Kilometer", 0, 64, 1500, "minecraft:overworld");
      s.waypoints.add("Long distance", 100, 64, 100000, "minecraft:overworld");
      s.waypoints.add("Behind", 0, 64, -2000, "minecraft:overworld");
      s.waypoints.add("Other dimension", 0, 64, 2500, "minecraft:the_nether");
      List<String> labels = new ArrayList<String>();
      Canvas canvas = (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if (m.getName().equals("textFamily")) labels.add((String) a[1]);
            if (m.getReturnType() == float.class) return 20f;
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });
      r.renderWaypoints(canvas, 1280, 720);
      assertTrue(labels.contains("Kilometer"));
      assertTrue(labels.contains("Long distance"));
      assertFalse(labels.contains("Behind"));
      assertFalse(labels.contains("Other dimension"));
    } finally {
      r.shutdown();
    }
  }
  @Test
  void eachAuxiliaryHudCanRequestRenderingWithoutAnOriginalHudEnabled() throws Exception {
    ClientRuntime r = runtime(new PlatformServices() {
      public Path configDirectory() {
        return directory;
      }
      public java.util.Map<String, Object> scoreboard() {
        return java.util.Collections.<String, Object>singletonMap("title", "Board");
      }
    });
    try {
      Settings s = r.settings();
      s.hud.enabled = s.inventory.enabled = s.tnt.enabled = s.crosshair.enabled = false;
      s.armor.enabled = s.potions.enabled = s.food.enabled = s.target.enabled = false;
      s.menu.animationSpeed = 1000000f;
      for (String name :
          new String[] {"scoreboard", "keystrokes", "coordinates", "ping", "fps"}) {
        Object panel = Settings.class.getField(name).get(s);
        java.lang.reflect.Field enabled = panel.getClass().getField("enabled");
        enabled.setBoolean(panel, true);
        r.changed();
        assertTrue(r.shouldRenderHud(), name + " requests its own HUD pass");
        enabled.setBoolean(panel, false);
        r.changed();
        assertFalse(r.shouldRenderHud(), name + " does not leak visibility after disabling");
      }
    } finally {
      r.shutdown();
    }
  }
  @Test
  void coloredScoreboardProtectsWholeLogicalNameWithoutMutatingServerData() throws Exception {
    java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
    row.put("text", "§aP§bl§ca§dy§ee§fr §7Coins: §6123");
    row.put("score", 123);
    row.put("hasScore", true);
    java.util.Map<String, Object> raw = new java.util.LinkedHashMap<String, Object>();
    raw.put("title", "§eArena §bPLAYER");
    raw.put(
        "lines", java.util.Collections.singletonList(java.util.Collections.unmodifiableMap(row)));
    raw.put("serverAddress", "arena.example");
    java.util.Map<String, Object> network =
        java.util.Collections.<String, Object>singletonMap("serverAddress", "arena.example");
    ClientRuntime r = runtime(new PlatformServices() {
      public Path configDirectory() {
        return directory;
      }
      public String ownNickname() {
        return "Player";
      }
      public java.util.Map<String, Object> scoreboard() {
        return java.util.Collections.unmodifiableMap(raw);
      }
      public java.util.Map<String, Object> networkInfo() {
        return network;
      }
    });
    try {
      r.settings().protect.enabled = true;
      r.settings().protect.hideNickname = true;
      r.settings().protect.replacement = "Private";
      java.util.Map<String, Object> masked = r.scoreboard();
      assertEquals("Arena Private", plain((String) masked.get("title")));
      java.util.Map<?,?> line=(java.util.Map<?,?>)((java.util.List<?>)masked.get("lines")).get(0);
      assertEquals("Private Coins: 123", plain((String) line.get("text")));
      assertEquals(123, line.get("score"));
      assertEquals(true, line.get("hasScore"));
      assertEquals("arena.example", masked.get("serverAddress"));
      assertSame(network, r.networkInfo());
      assertEquals("Player Coins: 123", plain((String) row.get("text")),
          "Server-supplied rows remain immutable inputs");
      r.settings().protect.hideNickname = false;assertEquals("Player Coins: 123",plain((String)((java.util.Map<?,?>)((java.util.List<?>)r.scoreboard().get("lines")).get(0)).get("text")));
      r.settings().protect.hideNickname = true;
      r.settings().protect.enabled = false;
      assertEquals("Arena PLAYER", plain((String) r.scoreboard().get("title")));
    } finally {
      r.shutdown();
    }
  }
  private static String plain(String value) {
    StringBuilder out = new StringBuilder();
    for (dev.nightclient.api.FormattedText.Run run :
        dev.nightclient.api.FormattedText.runs(value, 0xffffffff))
      out.append(run.text);
    return out.toString();
  }
  @Test
  void resetUndoAndImportRestoreEveryFormerlyOmittedModule() throws Exception {
    ClientRuntime r = runtime(new PlatformServices() {
      public Path configDirectory() {
        return directory;
      }
    });
    try {
      Settings s = r.settings();
      s.damageSplash.enabled = s.scoreboard.enabled = s.keystrokes.enabled = true;
      s.coordinates.enabled = s.ping.enabled = s.hwhelper.enabled = true;
      s.damageSplash.duration = 2.5f;
      s.scoreboard.offsetX = 49;
      s.keystrokes.space = false;
      s.coordinates.offsetY = 71;
      s.ping.offsetX = 39;
      s.moduleBindings.put("damageSplash", "key.keyboard.g");
      s.theme.customBackground = 0xff112233;
      s.theme.customSurface = 0xff223344;
      s.theme.customRaised = 0xff334455;
      s.theme.customText = 0xffd0d0d0;
      s.theme.selectMode("custom");
      String saved = r.exportProfile();
      r.saveProfile("Full settings");
      r.reset();
      assertFalse(s.damageSplash.enabled);
      assertFalse(s.scoreboard.enabled);
      r.undo();
      assertEquals(saved, r.exportProfile(), "Undo restores the complete serialized settings");
      assertTrue(s.damageSplash.enabled && s.scoreboard.enabled && s.keystrokes.enabled
          && s.coordinates.enabled && s.ping.enabled && s.hwhelper.enabled);
      s.damageSplash.enabled = s.scoreboard.enabled = s.keystrokes.enabled = s.coordinates.enabled =
          false;
      s.ping.enabled = s.hwhelper.enabled = false;
      r.importProfile(saved);
      assertEquals(saved, r.exportProfile());
      assertEquals("custom", s.theme.mode);
      assertEquals(0xff112233, s.theme.customBackground);
      assertEquals(0xff223344, s.theme.customSurface);
      assertEquals(0xff334455, s.theme.customRaised);
      assertEquals(0xffd0d0d0, s.theme.customText);
      assertEquals(2.5f, s.damageSplash.duration);
      assertEquals(49, s.scoreboard.offsetX);
      assertFalse(s.keystrokes.space);
      assertEquals(71, s.coordinates.offsetY);
      assertEquals(39, s.ping.offsetX);
      assertEquals("key.keyboard.g", s.moduleBindings.get("damageSplash"));
      s.ping.enabled = false;
      s.scoreboard.offsetX = 0;
      r.loadProfile("Full settings");
      assertEquals(saved, r.exportProfile(), "Named profile loading restores these modules too");
    } finally {
      r.shutdown();
    }
  }
}
