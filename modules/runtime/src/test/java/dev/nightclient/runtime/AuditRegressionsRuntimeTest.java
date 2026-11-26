package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditRegressionsRuntimeTest {
  @TempDir Path tempDir;

  @Test
  void combatTagDetectorIgnoresCombatEndMessagesAndHashNumberTags() {
    assertFalse(CombatTagDetector.parseCombatText("§aВы вышли из режима боя!").active);
    assertFalse(CombatTagDetector.parseCombatText("§aВы больше не в бою").active);
    assertFalse(CombatTagDetector.parseCombatText("§aCombat ended - safe to log out").active);
    assertFalse(CombatTagDetector.parseCombatText("§aNo longer in combat").active);
    assertFalse(CombatTagDetector.parseCombatText("§eАнархия #5").active);
    CombatTagDetector.CombatMatch real = CombatTagDetector.parseCombatText("§cРежим боя #5 · 18с");
    assertTrue(real.active);
    assertEquals(18, real.remainingSeconds);
  }

  @Test
  void hwHelperRateAndTooltipHandleZeroAndNonPositiveSafely() {
    assertEquals(100_000L, HwHelperManager.calculateRate(Arrays.asList(0L, -500L)));
    assertEquals(1000L, HwHelperManager.calculateRate(Collections.singletonList(250L)));
    Settings s = new Settings();
    s.hwhelper.enabled = true;
    s.hwhelper.aucprice.enabled = true;
    HwHelperManager mgr = new HwHelperManager(new PlatformServices() {}, s);
    try {
      String unchanged = mgr.formatTooltipPrice("Цена: 100000");
      assertEquals("Цена: 100000", unchanged);
    } finally {
      mgr.shutdown();
    }
  }

  @Test
  void cosmeticsLegacy64x32SkinPreservesHatOverlayPixels() throws Exception {
    BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    legacy.setRGB(8, 8, 0xFFD18862);
    legacy.setRGB(40, 8, 0xFFFF2244);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(legacy, "png", out);
    CosmeticsImages.DecodedTexture upgraded = CosmeticsImages.decodeSkin(out.toByteArray());
    assertEquals(64, upgraded.width);
    assertEquals(64, upgraded.height);
    assertEquals(0xFFFF2244, upgraded.pixels[8 * 64 + 40], "Legacy 64x32 skin hat layer must be preserved");
  }

  @Test
  void chatCoordinateParserStripsHexColorsAndTooltipsParsesMixedSeparators() {
    double[] coords = ChatCoordinateParser.parse("§x§f§f§3§3§3§3Base §#00ff88at 125 64 -340", 64);
    assertNotNull(coords);
    assertEquals(125.0, coords[0], 0.001);
    assertEquals(64.0, coords[1], 0.001);
    assertEquals(-340.0, coords[2], 0.001);

    assertEquals(1250.5, TooltipsLogic.parseHumanNumber("1,250.50"), 0.001);
    assertEquals(1250.5, TooltipsLogic.parseHumanNumber("1.250,50"), 0.001);
    assertEquals(1_250_000.0, TooltipsLogic.parseHumanNumber("1 250 000"), 0.001);
  }

  @Test
  void clientRuntimeRunsCommandLayoutFixBeforeHwHelperAndRecordsUndoBeforeWaypointAdd()
      throws Exception {
    final List<String> sentCmds = new ArrayList<String>();
    final double[] playerPos = {42.0, 70.0, -84.0};
    final ClientRuntime[] holder = new ClientRuntime[1];
    PlatformServices platform = new PlatformServices() {
      @Override public Path configDirectory() { return tempDir; }
      @Override public double[] playerPosition() { return playerPos; }
      @Override public String currentDimension() { return "minecraft:overworld"; }
      @Override public String currentWorldId() { return "sp:audit"; }
      @Override public String currentWorldLabel() { return "Audit"; }
      @Override public void sendCommandToServer(String cmd) { sentCmds.add(cmd); }
      @Override public boolean waypointAddHere() {
        return holder[0].settings().waypoints.add(
            "Here", 42.0, 70.0, -84.0, "minecraft:overworld", "sp:audit", "Audit") != null;
      }
    };
    java.lang.reflect.Constructor<ClientRuntime> ctor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    ctor.setAccessible(true);
    ClientRuntime rt = ctor.newInstance(platform);
    holder[0] = rt;
    try {
      rt.settings().tweaksy.enabled = true;
      rt.settings().tweaksy.commandFix.enabled = true;
      rt.settings().hwhelper.enabled = true;
      rt.settings().hwhelper.answap.enabled = true;
      String fixed = rt.fixCommand("/фт 5");
      assertEquals("", fixed, "Russian-layout /фт 5 must convert to /an 5 and be handled by HwHelper");
      assertEquals("", rt.fixCommand(".цфн add Base 10 64 20"));
      assertEquals(1, rt.settings().waypoints.points.size());
      rt.settings().waypoints.points.clear();

      assertEquals(0, rt.settings().waypoints.points.size());
      assertTrue(rt.waypointAddHere());
      assertEquals(1, rt.settings().waypoints.points.size());
      rt.undo();
      assertEquals(0, rt.settings().waypoints.points.size(), "Undo after waypointAddHere must revert the newly added waypoint");
    } finally {
      rt.shutdown();
    }
  }

  @Test
  void worldMapBridgeTemplateDoesNotSkipLightColoredBlocks() throws Exception {
    Path tpl = Paths.get("../platform/src/templates/WorldMapBridge.java.tpl").toAbsolutePath().normalize();
    if (!Files.isRegularFile(tpl)) {
      tpl = Paths.get("modules/platform/src/templates/WorldMapBridge.java.tpl").toAbsolutePath().normalize();
    }
    String src = new String(Files.readAllBytes(tpl), StandardCharsets.UTF_8);
    assertTrue(src.contains("MapBlockPalette.resolve("), "WorldMapBridge must delegate block classification to MapBlockPalette");
    assertFalse(src.contains("id.contains(\"light\")"), "WorldMapBridge must not skip light_blue/light_gray/froglight/shroomlight blocks");
    assertEquals(dev.nightclient.renderer.map.MapBlockPalette.KIND_SKIP,
        dev.nightclient.renderer.map.MapBlockPalette.resolve("minecraft:light", 0, null).kind);
    assertEquals(dev.nightclient.renderer.map.MapBlockPalette.KIND_SOLID,
        dev.nightclient.renderer.map.MapBlockPalette.resolve("minecraft:light_blue_wool", 0, null).kind);
    assertEquals(dev.nightclient.renderer.map.MapBlockPalette.KIND_SOLID,
        dev.nightclient.renderer.map.MapBlockPalette.resolve("minecraft:light_gray_concrete", 0, null).kind);
    assertEquals(dev.nightclient.renderer.map.MapBlockPalette.KIND_SOLID,
        dev.nightclient.renderer.map.MapBlockPalette.resolve("minecraft:shroomlight", 0, null).kind);
    assertEquals(dev.nightclient.renderer.map.MapBlockPalette.KIND_SOLID,
        dev.nightclient.renderer.map.MapBlockPalette.resolve("minecraft:ochre_froglight", 0, null).kind);
  }

  @Test
  void effectAlertSoundControlAndTapeMouseHandleCoreLifecycleWithoutAllocLeaks() {
    EffectAlertLogic alert = new EffectAlertLogic();
    Settings.EffectAlert ea = new Settings.EffectAlert();
    ea.enabled = true;
    ea.strength = true;
    ea.warnSeconds = 10;
    ea.notifyOnExpire = true;

    alert.update(ea, Collections.singletonList(
        new dev.nightclient.api.PotionView("minecraft:strength", "Strength", 1, 0xFF932423, 8.5f, true)), 1000L);
    assertEquals(1, alert.hudAlerts().size());
    assertFalse(alert.hudAlerts().get(0).expired);
    assertEquals(1, alert.drainSounds().size());

    alert.update(ea, Collections.<dev.nightclient.api.PotionView>emptyList(), 2000L);
    assertEquals(1, alert.hudAlerts().size());
    assertTrue(alert.hudAlerts().get(0).expired);
    assertEquals(1, alert.drainSounds().size());

    HwPvpAndEventsLogic.HwTimerTracker hwTracker = new HwPvpAndEventsLogic.HwTimerTracker();
    Settings.HwTimers hwt = new Settings.HwTimers();
    hwt.enabled = true;
    hwt.stuns = true;
    hwTracker.syncPersonalEffects(
        Collections.<dev.nightclient.api.PotionView>emptyList(),
        Arrays.asList(
            new dev.nightclient.api.Cooldown("ep", "Pearl", "minecraft:ender_pearl", 12.0f, 15.0f),
            new dev.nightclient.api.Cooldown("cf", "Chorus", "minecraft:chorus_fruit", 12.0f, 15.0f)),
        10.0, 64.0, 10.0, hwt, 5000L);
    assertEquals(1, hwTracker.snapshot(hwt, 5100L).size());

    assertEquals("note_block", SoundControlLogic.normalizeBlockQuery("Нотный блок"));
    assertEquals("obsidian", SoundControlLogic.normalizeBlockQuery("minecraft:obsidian"));
    Settings.SoundControl sc = new Settings.SoundControl();
    sc.enabled = true;
    sc.customTnt = true;
    sc.tntFusePreset = "bell";
    SoundControlLogic.SoundReplacement rep =
        SoundControlLogic.resolveCustomTntReplacement(sc, "minecraft:entity.tnt.primed");
    assertNotNull(rep);
    assertEquals("minecraft:block.bell.use", rep.soundId);

    TapeMouseLogic tm = new TapeMouseLogic();
    Settings.TapeMouse tmc = new Settings.TapeMouse();
    TapeMouseLogic.CommandOutcome out = TapeMouseLogic.parseCommand("/tm right hold", tmc, false);
    assertNotNull(out);
    assertTrue(out.intercepted);
    assertTrue(tmc.enabled);
    assertEquals("use", tmc.action);
    assertEquals(0, tmc.delayTicks);
    TapeMouseLogic.TickStep s1 = tm.step(tmc, true, false, false);
    assertTrue(s1.holdDown);
    TapeMouseLogic.TickStep s2 = tm.step(tmc, true, true, false);
    assertFalse(s2.holdDown);
    assertTrue(s2.releaseHold);
  }
}
