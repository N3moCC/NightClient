package dev.nightclient.renderer.map;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.renderer.TexturedMesh;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MapRegionCacheTest {

  @Test
  void slopeShadingWaterDepthAndBlockLightProduceExpectedContrast() {
    int flat = MapColorShading.shadePixel(0x808080, (short) 64, (short) 64, (short) 64, 0, true, true);
    int hill = MapColorShading.shadePixel(0x808080, (short) 68, (short) 64, (short) 64, 0, true, true);
    int valley = MapColorShading.shadePixel(0x808080, (short) 60, (short) 64, (short) 64, 0, true, true);
    int lit = MapColorShading.shadePixel(0x808080, (short) 64, (short) 64, (short) 64, 15, true, true);

    assertTrue(((hill >>> 16) & 0xFF) > ((flat >>> 16) & 0xFF));
    assertTrue(((valley >>> 16) & 0xFF) < ((flat >>> 16) & 0xFF));
    assertTrue(((lit >>> 16) & 0xFF) > ((flat >>> 16) & 0xFF));
    assertEquals(MapColorShading.UNEXPLORED_ARGB,
        MapColorShading.shadePixel(0x808080, MapColorShading.UNEXPLORED_HEIGHT, (short) 64,
            (short) 64, 0, true, true));

    int shallow = MapColorShading.composeSurfaceRgb(0xD8C890, 1, 0x3F76E4, 0, 0f);
    int deep = MapColorShading.composeSurfaceRgb(0xD8C890, 18, 0x3F76E4, 0, 0f);
    assertTrue(((shallow >>> 16) & 0xFF) > ((deep >>> 16) & 0xFF));
  }

  @Test
  void regionTileBuildsMipmapsAndSurvivesCompressedTvmapRoundTrip() throws Exception {
    Path temp = Files.createTempDirectory("tv-region-rt");
    DiskRegionStore store = new DiskRegionStore(temp);
    MapRegionTile tile = new MapRegionTile(2, -3);

    int[] rgb = new int[256];
    short[] heights = new short[256];
    byte[] light = new byte[256];
    for (int i = 0; i < 256; i++) {
      rgb[i] = 0x5A9E3C;
      heights[i] = (short) (64 + (i & 7));
      light[i] = (byte) (i % 15);
    }
    tile.putChunk(4, 7, rgb, heights, light, null, null, true, true);
    assertTrue(tile.isChunkPopulated(4, 7));
    assertFalse(tile.isChunkPopulated(0, 0));
    assertTrue(tile.isDiskDirty());

    int localX = 4 * 16 + 4;
    int localZ = 7 * 16 + 4;
    assertNotEquals(0, tile.argb[localZ * 512 + localX] >>> 24);
    assertNotEquals(0, tile.mip2[(localZ >> 1) * 256 + (localX >> 1)] >>> 24);
    assertNotEquals(0, tile.mip4[(localZ >> 2) * 128 + (localX >> 2)] >>> 24);
    assertNotEquals(0, tile.mip8[(localZ >> 3) * 64 + (localX >> 3)] >>> 24);
    assertNotEquals(0, tile.mip16[(localZ >> 4) * 32 + (localX >> 4)] >>> 24);

    store.saveRegion("sp_demo", "world", "minecraft:overworld", tile);
    assertFalse(tile.isDiskDirty());
    assertTrue(store.discoverStoredRegions("sp_demo", "world", "minecraft:overworld")
                   .contains(MapRegionTile.regionKey(2, -3)));

    MapRegionTile reloaded =
        store.loadRegion("sp_demo", "world", "minecraft:overworld", 2, -3, true, true);
    assertNotNull(reloaded);
    assertTrue(reloaded.isChunkPopulated(4, 7));
    assertEquals(tile.heightAtLocal(localX, localZ), reloaded.heightAtLocal(localX, localZ));
    assertEquals(tile.argbAtLocal(localX, localZ), reloaded.argbAtLocal(localX, localZ));
  }

  @Test
  void minimapSlidingWindowSkipsRebuildWithinSameChunkAndRotatesUvCleanly() throws Exception {
    Path temp = Files.createTempDirectory("tv-win-cache");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "myworld", "My World", "minecraft:overworld", 123L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x74B24A);
      Arrays.fill(heights, (short) 66);
      cache.ingestChunk(0, 0, rgb, heights, light);

      RegionCacheManager.MinimapWindow w1 = cache.composeMinimapWindow(4.2, 6.8, true, true);
      RegionCacheManager.MinimapWindow w2 = cache.composeMinimapWindow(11.9, 13.1, true, true);
      assertEquals(w1.revision, w2.revision);

      RegionCacheManager.MinimapWindow w3 = cache.composeMinimapWindow(20.0, 6.8, true, true);
      assertNotEquals(w2.revision, w3.revision);
    } finally {
      cache.shutdown();
    }

    float[] circleVerts = TexturedMesh.roundedRectRotatedUv(
        10, 10, 100, 100, 50, 0xFFFFFFFF, 0.5f, 0.5f, 0.2f, 0.2f, (float) (Math.PI * 0.25));
    assertTrue(circleVerts.length > 0 && circleVerts.length % 24 == 0);
    float[] rectVerts = TexturedMesh.roundedRectRotatedUv(
        10, 10, 100, 100, 0, 0xFFFFFFFF, 0.5f, 0.5f, 0.2f, 0.2f, 0f);
    assertEquals(6 * 8, rectVerts.length);

    for (float polyR : new float[] {-4f, -6f, -8f}) {
      float[] verts = TexturedMesh.roundedRectRotatedUv(
          10, 10, 100, 100, polyR, 0xFFFFFFFF, 0.5f, 0.5f, 0.2f, 0.2f, 0.5f);
      assertTrue(verts.length > 0 && verts.length % 24 == 0, "valid polygon mesh for r=" + polyR);
    }
  }

  @Test
  void reliefStylesAndCacheClearingWorkCleanly() throws Exception {
    int base = 0x709A50;
    int flat = MapColorShading.shadePixel(base, (short) 72, (short) 64, (short) 64, 0, true, false,
        MapColorShading.RELIEF_FLAT, 1.35f);
    int classic = MapColorShading.shadePixel(base, (short) 72, (short) 64, (short) 64, 0, true, false,
        MapColorShading.RELIEF_CLASSIC, 1.35f);
    int classicStrong = MapColorShading.shadePixel(base, (short) 72, (short) 64, (short) 64, 0, true, false,
        MapColorShading.RELIEF_CLASSIC, 3.5f);
    int valleyMin = MapColorShading.shadePixel(base, (short) 62, (short) 64, (short) 64, 0, true, false,
        MapColorShading.RELIEF_CLASSIC, 1.35f);
    int valleyStrong = MapColorShading.shadePixel(base, (short) 62, (short) 64, (short) 64, 0, true, false,
        MapColorShading.RELIEF_CLASSIC, 3.5f);
    int vivid = MapColorShading.shadePixel(base, (short) 72, (short) 64, (short) 64, 0, true, false,
        MapColorShading.RELIEF_VIVID, 2.5f);
    int classicStep = MapColorShading.shadePixel(base, (short) 64, (short) 63, (short) 63, 0, true, false,
        MapColorShading.RELIEF_CLASSIC, 1.35f);
    int contourStep = MapColorShading.shadePixel(base, (short) 64, (short) 63, (short) 63, 0, true, false,
        MapColorShading.RELIEF_CONTOUR, 1.35f);
    assertEquals(0xFF000000 | base, flat);
    assertTrue(((classic >>> 16) & 0xFF) > ((flat >>> 16) & 0xFF));
    assertTrue(((classicStrong >>> 16) & 0xFF) > ((classic >>> 16) & 0xFF),
        "higher reliefStrength increases sunlit hill brightness");
    assertTrue(((valleyStrong >>> 16) & 0xFF) < ((valleyMin >>> 16) & 0xFF),
        "higher reliefStrength deepens shadowed valleys");
    assertTrue(((vivid >>> 16) & 0xFF) >= ((classic >>> 16) & 0xFF));
    assertTrue(((contourStep >>> 16) & 0xFF) < ((classicStep >>> 16) & 0xFF),
        "contour darkens 8-block elevation band crossings");

    MapBlockPalette.BlockEntry shortGrass = MapBlockPalette.resolve("minecraft:short_grass", 1, null);
    assertEquals(MapBlockPalette.KIND_SKIP, shortGrass.kind);
    MapBlockPalette.BlockEntry poppy = MapBlockPalette.resolve("minecraft:poppy", 7, null);
    assertEquals(MapBlockPalette.KIND_FLAT_OVERLAY, poppy.kind);
    MapBlockPalette.BlockEntry snow = MapBlockPalette.resolve("minecraft:snow", 8, null);
    assertEquals(MapBlockPalette.KIND_FLAT_OVERLAY, snow.kind);
    MapBlockPalette.BlockEntry cherry = MapBlockPalette.resolve("minecraft:cherry_leaves", 7, null);
    assertEquals(MapBlockPalette.KIND_SOLID, cherry.kind);
    assertEquals(MapBlockPalette.TINT_NONE, cherry.tintMode);
    MapBlockPalette.BlockEntry grassBlock = MapBlockPalette.resolve("minecraft:grass_block", 1, null);
    assertEquals(MapBlockPalette.TINT_GRASS, grassBlock.tintMode);
    MapBlockPalette.BlockEntry spruce = MapBlockPalette.resolve("minecraft:spruce_leaves", 7, null);
    assertEquals(MapBlockPalette.TINT_SPRUCE, spruce.tintMode);

    Path temp = Files.createTempDirectory("tv-clear-cache");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "clearworld", "Clear World", "minecraft:overworld", 77L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x6AA845);
      Arrays.fill(heights, (short) 68);
      cache.ingestChunk(0, 0, rgb, heights, light);
      assertEquals(1, cache.loadedRegionCount());

      cache.flushDirtyRegionsSync();
      assertTrue(cache.knownDiskRegionCount() >= 1);
      cache.clearCurrentWorldCache();
      assertEquals(0, cache.loadedRegionCount());
      assertEquals(0, cache.knownDiskRegionCount());

      cache.ingestChunk(0, 0, rgb, heights, light);
      cache.flushDirtyRegionsSync();
      assertEquals(1, cache.loadedRegionCount());
      cache.clearAllCache();
      assertEquals(0, cache.loadedRegionCount());
      assertEquals(0, cache.knownDiskRegionCount());
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void wideZoomOutWithManyRegionsAndExtremeZoomInRenderWithoutFlickerOrOversizedQuads()
      throws Exception {
    Path temp = Files.createTempDirectory("tv-wide-zoom");
    RegionCacheManager cache = new RegionCacheManager(temp);
    try {
      cache.beginWorldSession(true, "", "hugeworld", "Huge World", "minecraft:overworld", 99L, "0_64_0");
      int[] rgb = new int[256];
      short[] heights = new short[256];
      byte[] light = new byte[256];
      Arrays.fill(rgb, 0x5E9E3E);
      Arrays.fill(heights, (short) 66);

      for (int rz = -6; rz < 6; rz++) {
        for (int rx = -6; rx < 6; rx++) {
          cache.ingestChunk(rx * 32 + 16, rz * 32 + 16, rgb, heights, light);
        }
      }
      assertEquals(144, cache.allKnownRegionKeys().size());

      dev.nightclient.config.Settings settings = new dev.nightclient.config.Settings();
      WorldMapRenderer.ViewState state = new WorldMapRenderer.ViewState();
      state.centerX = 0.0;
      state.centerZ = 0.0;
      state.zoom = 0.08f;

      final int[] quadCounts = new int[3];
      for (int frame = 0; frame < 3; frame++) {
        final int f = frame;
        dev.nightclient.api.Canvas spy = new dev.nightclient.api.Canvas() {
          @Override public void rect(float x, float y, float w, float h, int c) {}
          @Override public void roundedRect(float x, float y, float w, float h, float r, int c) {}
          @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
          @Override public void gradientRect(float x, float y, float w, float h, int a, int b) {}
          @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
          @Override public void circle(float cx, float cy, float r, int c) {}
          @Override public void text(String v, float x, float y, float s, int c, boolean b) {}
          @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0f : v.length() * s * 0.5f; }
          @Override public void pushClip(float x, float y, float w, float h) {}
          @Override public void popClip() {}
          @Override public void item(String id, float x, float y, float s, float a) {}
          @Override public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
          @Override public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}
          @Override public void mapQuad(String key, long rev, int[] px, int tw, int th, float x, float y,
              float w, float h, float r, float uc, float vc, float uh, float vh, float ang, boolean near, int c) {
            quadCounts[f]++;
            assertEquals(64, tw, "zoom=0.08 should use crisp mip8 (64x64) overview without upscale blur");
            assertEquals(64, th);
            assertTrue(x >= -0.01f && y >= -0.01f && x + w <= 1000.01f && y + h <= 800.01f);
          }
        };
        WorldMapRenderer.render(spy, settings, cache, null, state, 1000, 800, 500, 400, 1.0 + f * 0.016);
      }
      assertEquals(144, quadCounts[0]);
      assertEquals(144, quadCounts[1]);
      assertEquals(144, quadCounts[2]);

      state.zoom = 8.0f;
      state.centerX = 256.0;
      state.centerZ = 256.0;
      final int[] zoomedInQuads = new int[1];
      dev.nightclient.api.Canvas clipSpy = new dev.nightclient.api.Canvas() {
        @Override public void rect(float x, float y, float w, float h, int c) {}
        @Override public void roundedRect(float x, float y, float w, float h, float r, int c) {}
        @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
        @Override public void gradientRect(float x, float y, float w, float h, int a, int b) {}
        @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
        @Override public void circle(float cx, float cy, float r, int c) {}
        @Override public void text(String v, float x, float y, float s, int c, boolean b) {}
        @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0f : v.length() * s * 0.5f; }
        @Override public void pushClip(float x, float y, float w, float h) {}
        @Override public void popClip() {}
        @Override public void item(String id, float x, float y, float s, float a) {}
        @Override public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
        @Override public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}
        @Override public void mapQuad(String key, long rev, int[] px, int tw, int th, float x, float y,
            float w, float h, float r, float uc, float vc, float uh, float vh, float ang, boolean near, int c) {
          zoomedInQuads[0]++;
          assertTrue(x >= 0f && y >= 0f && x + w <= 800.01f && y + h <= 600.01f,
              "Zoomed-in tile quad must be clipped to viewport: " + x + "," + y + " " + w + "x" + h);
          assertTrue(uc - uh >= -0.001f && uc + uh <= 1.001f);
          assertTrue(vc - vh >= -0.001f && vc + vh <= 1.001f);
        }
      };
      WorldMapRenderer.render(clipSpy, settings, cache, null, state, 800, 600, 400, 300, 2.0);
      assertTrue(zoomedInQuads[0] >= 1);
    } finally {
      cache.shutdown();
    }
  }
}
