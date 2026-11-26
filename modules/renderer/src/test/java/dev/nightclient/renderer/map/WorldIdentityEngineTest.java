package dev.nightclient.renderer.map;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldIdentityEngineTest {

  @Test
  void separatesTwoAnarchiesWithIdenticalHostSeedAndSpawnUsingScoreboard() throws Exception {
    Path temp = Files.createTempDirectory("tv-wid-anarchy");
    DiskRegionStore store = new DiskRegionStore(temp);
    WorldIdentityEngine engine = new WorldIdentityEngine(store);

    long sharedSeed = 0x1a2b3c4d5e6f7081L;
    engine.beginSession(
        false, "mc.holyworld.ru:25565", "", "", "minecraft:overworld", sharedSeed, "0_64_0");
    assertEquals(WorldIdentityEngine.State.PROVISIONAL, engine.state());

    String realm101 = WorldIdentityEngine.extractRealmLabel(
        Arrays.asList("§6§lHolyWorld", "§7Режим: §eАнархия #101", "§7Онлайн: §a412", "mc.holyworld.ru"));
    assertEquals("Анархия #101", realm101);
    assertTrue(engine.feedRealmLabel(realm101));
    assertEquals(WorldIdentityEngine.State.LOCKED, engine.state());
    String id101 = engine.worldId();
    assertTrue(id101.startsWith("mp:mc.holyworld.ru/r:"));
    assertTrue(engine.worldLabel().contains("Анархия #101"));

    engine.beginSession(
        false, "mc.holyworld.ru", "", "", "minecraft:overworld", sharedSeed, "0_64_0");
    String realm102 = WorldIdentityEngine.extractRealmLabel(
        Arrays.asList("§6§lHolyWorld", "§7Анархия #102", "§7Монет: 5000"));
    assertEquals("Анархия #102", realm102);
    assertTrue(engine.feedRealmLabel(realm102));
    String id102 = engine.worldId();

    assertNotEquals(id101, id102);
    Waypoint wp101 = new Waypoint("Base101", 10, 64, 10);
    wp101.world = id101;
    assertTrue(wp101.matchesWorld(id101));
    assertFalse(wp101.matchesWorld(id102));
  }

  @Test
  void tier1ServerLevelIdPacketLocksImmediatelyAndTakesPriority() throws Exception {
    Path temp = Files.createTempDirectory("tv-wid-pkt");
    WorldIdentityEngine engine = new WorldIdentityEngine(new DiskRegionStore(temp));
    engine.beginSession(false, "play.example.net", "", "", "minecraft:overworld", 0L, "0_64_0");
    assertTrue(engine.feedServerLevelId("4291"));
    assertEquals(WorldIdentityEngine.State.LOCKED, engine.state());
    assertEquals("mp:play.example.net/pkt:4291", engine.worldId());
    assertFalse(engine.feedRealmLabel("Lobby #1"));
    assertEquals("mp:play.example.net/pkt:4291", engine.worldId());
  }

  @Test
  void tier3TerrainSignatureRecognizesExistingWorldAndIsolatesDifferentArena() throws Exception {
    Path temp = Files.createTempDirectory("tv-wid-terrain");
    DiskRegionStore store = new DiskRegionStore(temp);
    WorldIdentityEngine engine = new WorldIdentityEngine(store);

    List<String> arenaA = Arrays.asList(
        WorldIdentityEngine.encodeTerrainSample(0, 0, (short) 64, 0x70B040),
        WorldIdentityEngine.encodeTerrainSample(8, 0, (short) 68, 0x70B040),
        WorldIdentityEngine.encodeTerrainSample(0, 8, (short) 72, 0x808080),
        WorldIdentityEngine.encodeTerrainSample(8, 8, (short) 64, 0x3070D0),
        WorldIdentityEngine.encodeTerrainSample(16, 0, (short) 70, 0x70B040),
        WorldIdentityEngine.encodeTerrainSample(16, 8, (short) 66, 0xD0C080));

    engine.beginSession(false, "proxy.test", "", "", "minecraft:overworld", 0L, "0_64_0");
    for (int t = 0; t < WorldIdentityEngine.PROVISIONAL_SETTLE_TICKS; t++) {
      engine.tickProvisional(arenaA);
    }
    assertEquals(WorldIdentityEngine.State.LOCKED, engine.state());
    String idA = engine.worldId();
    assertTrue(idA.startsWith("mp:proxy.test/mw:"));

    List<String> arenaASlightlyEdited = Arrays.asList(
        WorldIdentityEngine.encodeTerrainSample(0, 0, (short) 64, 0x70B040),
        WorldIdentityEngine.encodeTerrainSample(8, 0, (short) 68, 0x70B040),
        WorldIdentityEngine.encodeTerrainSample(0, 8, (short) 72, 0x808080),
        WorldIdentityEngine.encodeTerrainSample(8, 8, (short) 64, 0x3070D0),
        WorldIdentityEngine.encodeTerrainSample(16, 0, (short) 70, 0x70B040),
        WorldIdentityEngine.encodeTerrainSample(16, 8, (short) 90, 0xFF0000));
    engine.beginSession(false, "proxy.test", "", "", "minecraft:overworld", 0L, "0_64_0");
    for (int t = 0; t < WorldIdentityEngine.PROVISIONAL_SETTLE_TICKS; t++) {
      engine.tickProvisional(arenaASlightlyEdited);
    }
    assertEquals(idA, engine.worldId());

    List<String> arenaB = Arrays.asList(
        WorldIdentityEngine.encodeTerrainSample(0, 0, (short) 110, 0xE0E0E0),
        WorldIdentityEngine.encodeTerrainSample(8, 0, (short) 112, 0x202020),
        WorldIdentityEngine.encodeTerrainSample(0, 8, (short) 40, 0xE05010),
        WorldIdentityEngine.encodeTerrainSample(8, 8, (short) 42, 0xE05010),
        WorldIdentityEngine.encodeTerrainSample(16, 0, (short) 105, 0x902020),
        WorldIdentityEngine.encodeTerrainSample(16, 8, (short) 108, 0x902020));
    engine.beginSession(false, "proxy.test", "", "", "minecraft:overworld", 0L, "0_64_0");
    for (int t = 0; t < WorldIdentityEngine.PROVISIONAL_SETTLE_TICKS; t++) {
      engine.tickProvisional(arenaB);
    }
    assertEquals(WorldIdentityEngine.State.LOCKED, engine.state());
    assertNotEquals(idA, engine.worldId());
  }

  @Test
  void provisionalBufferHoldsChunksUntilLockAndMigratesLegacySeedWaypoints() throws Exception {
    Path temp = Files.createTempDirectory("tv-wid-provisional");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      long seed = 0xb721aa4c0b4c5c9cL;
      Settings.Waypoints wps = new Settings.Waypoints();
      Waypoint legacy = wps.add("OldBase", 50, 64, 50, Waypoint.DEFAULT_DIMENSION,
          "mp:holyworld.ru/s:b721aa4c0b4c5c9c", "");
      assertNotNull(legacy);

      cache.beginWorldSession(
          false, "holyworld.ru", "", "", Waypoint.DEFAULT_DIMENSION, seed, "0_64_0");
      assertEquals(WorldIdentityEngine.State.PROVISIONAL, cache.identity().state());

      int[] rgb = new int[256];
      short[] h = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x6AA845);
      Arrays.fill(h, (short) 65);
      cache.ingestChunk(0, 0, rgb, h, light);
      assertEquals((short) 65, cache.heightAt(5, 5));

      assertTrue(cache.feedRealmLabel("СолоЛайт #6", wps));
      assertEquals(WorldIdentityEngine.State.LOCKED, cache.identity().state());
      assertEquals((short) 65, cache.heightAt(5, 5));
      assertTrue(legacy.world.contains("/r:"));
      assertTrue(legacy.matchesWorld(cache.identity().worldId()));
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void provisionalChunksDefensivelyCopyReusedScratchBuffers() throws Exception {
    Path temp = Files.createTempDirectory("tv-wid-scratch-copy");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(
          false, "play.example.net", "", "", Waypoint.DEFAULT_DIMENSION, 12345L, "0_64_0");
      assertEquals(WorldIdentityEngine.State.PROVISIONAL, cache.identity().state());

      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];

      Arrays.fill(rgb, 0xFF22AA44);
      Arrays.fill(heights, (short) 70);
      Arrays.fill(light, (byte) 4);
      cache.ingestChunk(0, 0, rgb, heights, light);

      Arrays.fill(rgb, 0xFF3355EE);
      Arrays.fill(heights, (short) 95);
      Arrays.fill(light, (byte) 11);
      cache.ingestChunk(1, 0, rgb, heights, light);

      Arrays.fill(rgb, 0xFFCC8822);
      Arrays.fill(heights, (short) 120);
      Arrays.fill(light, (byte) 15);
      cache.ingestChunk(2, 0, rgb, heights, light);

      assertEquals((short) 70, cache.heightAt(5, 5));
      assertEquals((short) 95, cache.heightAt(21, 5));
      assertEquals((short) 120, cache.heightAt(37, 5));

      RegionCacheManager.MinimapWindow win = cache.composeMinimapWindow(8.0, 8.0, false, false);
      int scale = Math.max(1, win.texSize / win.size);
      int c0 = win.pixels[((5 - win.originBlockZ) * scale) * win.texSize + (5 - win.originBlockX) * scale] & 0x00FFFFFF;
      int c1 = win.pixels[((5 - win.originBlockZ) * scale) * win.texSize + (21 - win.originBlockX) * scale] & 0x00FFFFFF;
      int c2 = win.pixels[((5 - win.originBlockZ) * scale) * win.texSize + (37 - win.originBlockX) * scale] & 0x00FFFFFF;
      assertTrue(c0 != c1 && c1 != c2 && c0 != c2,
          "Provisional chunks must not alias reused scratch arrays");

      assertTrue(cache.feedRealmLabel("Анархия #1", new Settings.Waypoints()));
      assertEquals((short) 70, cache.heightAt(5, 5));
      assertEquals((short) 95, cache.heightAt(21, 5));
      assertEquals((short) 120, cache.heightAt(37, 5));
    } finally {
      cache.shutdown();
    }
  }
}
