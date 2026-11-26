package dev.nightclient.renderer.map;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public final class MapRegionTile {
  public static final int REGION_SHIFT = 9;
  public static final int SIZE = 512;
  public static final int CHUNKS_PER_SIDE = 32;
  public static final int CHUNK_SIZE = 16;

  private static final AtomicLong NEXT_REVISION = new AtomicLong(1L);

  public final int regionX;
  public final int regionZ;

  final int[] baseRgb = new int[SIZE * SIZE];
  final short[] heights = new short[SIZE * SIZE];
  final byte[] blockLight = new byte[SIZE * SIZE];

  private final short[] cachedNorthSeam = new short[SIZE];
  private final short[] cachedWestSeam = new short[SIZE];

  final int[] argb = new int[SIZE * SIZE];
  final int[] mip2 = new int[256 * 256];
  final int[] mip4 = new int[128 * 128];
  final int[] mip8 = new int[64 * 64];
  final int[] mip16 = new int[32 * 32];

  final long[] populatedMask = new long[16];

  private volatile long revision = NEXT_REVISION.incrementAndGet();
  private volatile boolean diskDirty = false;
  private volatile long lastAccessNanos = System.nanoTime();
  private volatile long lastRenderedFrame = 0L;
  private boolean shadedWithSlope = true;
  private boolean shadedWithLight = true;
  private int shadedReliefStyle = MapColorShading.RELIEF_CLASSIC;
  private float shadedReliefStrength = 3.0f;

  public MapRegionTile(int regionX, int regionZ) {
    this.regionX = regionX;
    this.regionZ = regionZ;
    Arrays.fill(heights, MapColorShading.UNEXPLORED_HEIGHT);
    Arrays.fill(cachedNorthSeam, MapColorShading.UNEXPLORED_HEIGHT);
    Arrays.fill(cachedWestSeam, MapColorShading.UNEXPLORED_HEIGHT);
  }

  public static long regionKey(int rx, int rz) {
    return (((long) rx) << 32) | (rz & 0xFFFFFFFFL);
  }

  public static int blockToRegion(int blockCoord) {
    return blockCoord >> REGION_SHIFT;
  }

  public static int chunkToRegion(int chunkCoord) {
    return chunkCoord >> 5;
  }

  public long revision() {
    return revision;
  }

  public boolean isDiskDirty() {
    return diskDirty;
  }

  public synchronized void clearDiskDirty() {
    diskDirty = false;
  }

  public synchronized void clearDiskDirtyIfRevision(long expectedRevision) {
    if (revision == expectedRevision) diskDirty = false;
  }

  public synchronized void copyArgbRect(
      int srcX, int srcZ, int width, int height, int[] dst, int dstX, int dstZ, int dstStride) {
    for (int row = 0; row < height; row++) {
      int srcRow = ((srcZ + row) << REGION_SHIFT) + srcX;
      int dstRow = (dstZ + row) * dstStride + dstX;
      System.arraycopy(argb, srcRow, dst, dstRow, width);
    }
  }

  public void touch() {
    lastAccessNanos = System.nanoTime();
  }

  public long lastAccessNanos() {
    return lastAccessNanos;
  }

  public void markRendered(long frameSerial) {
    lastRenderedFrame = frameSerial;
    lastAccessNanos = System.nanoTime();
  }

  public long lastRenderedFrame() {
    return lastRenderedFrame;
  }

  public synchronized boolean shadedWithSlope() {
    return shadedWithSlope;
  }

  public synchronized boolean shadedWithLight() {
    return shadedWithLight;
  }

  public synchronized int shadedReliefStyle() {
    return shadedReliefStyle;
  }

  public synchronized float shadedReliefStrength() {
    return shadedReliefStrength;
  }

  synchronized RegionCacheManager.OverviewTile captureOverview() {
    if (!hasAnyPopulatedChunk()) return null;
    return new RegionCacheManager.OverviewTile(
        Arrays.copyOf(mip8, mip8.length),
        Arrays.copyOf(mip16, mip16.length),
        revision,
        shadedWithSlope,
        shadedWithLight,
        shadedReliefStyle,
        shadedReliefStrength);
  }

  public synchronized boolean isChunkPopulated(int localCx, int localCz) {
    if (localCx < 0 || localCx >= CHUNKS_PER_SIDE || localCz < 0 || localCz >= CHUNKS_PER_SIDE)
      return false;
    int bit = (localCz << 5) | localCx;
    return (populatedMask[bit >>> 6] & (1L << (bit & 63))) != 0L;
  }

  public synchronized boolean hasAnyPopulatedChunk() {
    for (int i = 0; i < populatedMask.length; i++) {
      if (populatedMask[i] != 0L) return true;
    }
    return false;
  }

  public int[] pixelsForMip(int mipLevel) {
    switch (mipLevel) {
      case 1:
        return mip2;
      case 2:
        return mip4;
      case 3:
        return mip8;
      case 4:
        return mip16;
      default:
        return argb;
    }
  }

  public static int sizeForMip(int mipLevel) {
    switch (mipLevel) {
      case 1:
        return 256;
      case 2:
        return 128;
      case 3:
        return 64;
      case 4:
        return 32;
      default:
        return 512;
    }
  }

  public synchronized void putChunk(int localCx, int localCz, int[] chunkBaseRgb,
      short[] chunkHeights, byte[] chunkLight, short[] northSeamHeights, short[] westSeamHeights,
      boolean terrainShading, boolean useBlockLight) {
    putChunk(localCx, localCz, chunkBaseRgb, chunkHeights, chunkLight, northSeamHeights,
        westSeamHeights, terrainShading, useBlockLight, MapColorShading.RELIEF_CLASSIC, 3.0f);
  }

  public synchronized void putChunk(int localCx, int localCz, int[] chunkBaseRgb,
      short[] chunkHeights, byte[] chunkLight, short[] northSeamHeights, short[] westSeamHeights,
      boolean terrainShading, boolean useBlockLight, int reliefStyle, float reliefStrength) {
    if (localCx < 0 || localCx >= CHUNKS_PER_SIDE || localCz < 0 || localCz >= CHUNKS_PER_SIDE)
      return;
    int bx0 = localCx << 4;
    int bz0 = localCz << 4;
    for (int lz = 0; lz < CHUNK_SIZE; lz++) {
      int dstRow = (bz0 + lz) * SIZE + bx0;
      int srcRow = lz << 4;
      for (int lx = 0; lx < CHUNK_SIZE; lx++) {
        baseRgb[dstRow + lx] = chunkBaseRgb[srcRow + lx] & 0xFFFFFF;
        heights[dstRow + lx] = chunkHeights[srcRow + lx];
        blockLight[dstRow + lx] = chunkLight[srcRow + lx];
      }
    }
    int bit = (localCz << 5) | localCx;
    populatedMask[bit >>> 6] |= (1L << (bit & 63));
    shadedWithSlope = terrainShading;
    shadedWithLight = useBlockLight;
    shadedReliefStyle = reliefStyle;
    shadedReliefStrength = reliefStrength;

    if (localCz == 0 && northSeamHeights != null) {
      System.arraycopy(northSeamHeights, 0, cachedNorthSeam, bx0, CHUNK_SIZE);
    }
    if (localCx == 0 && westSeamHeights != null) {
      System.arraycopy(westSeamHeights, 0, cachedWestSeam, bz0, CHUNK_SIZE);
    }

    int xEnd = Math.min(SIZE, bx0 + CHUNK_SIZE + 1);
    int zEnd = Math.min(SIZE, bz0 + CHUNK_SIZE + 1);
    for (int z = bz0; z < zEnd; z++) {
      int row = z * SIZE;
      for (int x = bx0; x < xEnd; x++) {
        int idx = row + x;
        short h = heights[idx];
        if (h == MapColorShading.UNEXPLORED_HEIGHT) {
          argb[idx] = MapColorShading.UNEXPLORED_ARGB;
          continue;
        }
        short nh = z > 0 ? heights[idx - SIZE] : cachedNorthSeam[x];
        short wh = x > 0 ? heights[idx - 1] : cachedWestSeam[z];
        argb[idx] = MapColorShading.shadePixel(baseRgb[idx], h, nh, wh, blockLight[idx] & 0xFF,
            terrainShading, useBlockLight, reliefStyle, reliefStrength);
      }
    }

    updateMipsForRect(bx0, bz0, xEnd - 1, zEnd - 1);
    diskDirty = true;
    revision = NEXT_REVISION.incrementAndGet();
    lastAccessNanos = System.nanoTime();
  }

  public synchronized void reshadeNorthEdge(int localCx, short[] northHeights,
      boolean terrainShading, boolean useBlockLight) {
    reshadeNorthEdge(localCx, northHeights, terrainShading, useBlockLight, shadedReliefStyle,
        shadedReliefStrength);
  }

  public synchronized void reshadeNorthEdge(int localCx, short[] northHeights,
      boolean terrainShading, boolean useBlockLight, int reliefStyle, float reliefStrength) {
    if (northHeights == null || localCx < 0 || localCx >= CHUNKS_PER_SIDE) return;
    int bx0 = localCx << 4;
    System.arraycopy(northHeights, 0, cachedNorthSeam, bx0, CHUNK_SIZE);
    if (!isChunkPopulated(localCx, 0)) return;
    for (int lx = 0; lx < CHUNK_SIZE; lx++) {
      int x = bx0 + lx;
      short h = heights[x];
      if (h == MapColorShading.UNEXPLORED_HEIGHT) continue;
      short wh = x > 0 ? heights[x - 1] : cachedWestSeam[0];
      argb[x] = MapColorShading.shadePixel(baseRgb[x], h, northHeights[lx], wh,
          blockLight[x] & 0xFF, terrainShading, useBlockLight, reliefStyle, reliefStrength);
    }
    updateMipsForRect(bx0, 0, bx0 + CHUNK_SIZE - 1, 1);
    revision = NEXT_REVISION.incrementAndGet();
  }

  public synchronized void reshadeWestEdge(int localCz, short[] westHeights,
      boolean terrainShading, boolean useBlockLight) {
    reshadeWestEdge(localCz, westHeights, terrainShading, useBlockLight, shadedReliefStyle,
        shadedReliefStrength);
  }

  public synchronized void reshadeWestEdge(int localCz, short[] westHeights,
      boolean terrainShading, boolean useBlockLight, int reliefStyle, float reliefStrength) {
    if (westHeights == null || localCz < 0 || localCz >= CHUNKS_PER_SIDE) return;
    int bz0 = localCz << 4;
    System.arraycopy(westHeights, 0, cachedWestSeam, bz0, CHUNK_SIZE);
    if (!isChunkPopulated(0, localCz)) return;
    for (int lz = 0; lz < CHUNK_SIZE; lz++) {
      int z = bz0 + lz;
      int idx = z * SIZE;
      short h = heights[idx];
      if (h == MapColorShading.UNEXPLORED_HEIGHT) continue;
      short nh = z > 0 ? heights[idx - SIZE] : cachedNorthSeam[0];
      argb[idx] = MapColorShading.shadePixel(baseRgb[idx], h, nh, westHeights[lz],
          blockLight[idx] & 0xFF, terrainShading, useBlockLight, reliefStyle, reliefStrength);
    }
    updateMipsForRect(0, bz0, 1, bz0 + CHUNK_SIZE - 1);
    revision = NEXT_REVISION.incrementAndGet();
  }

  public synchronized void rebuildShading(boolean terrainShading, boolean useBlockLight) {
    rebuildShading(terrainShading, useBlockLight, shadedReliefStyle, shadedReliefStrength);
  }

  public synchronized void rebuildShading(boolean terrainShading, boolean useBlockLight,
      int reliefStyle, float reliefStrength) {
    shadedWithSlope = terrainShading;
    shadedWithLight = useBlockLight;
    shadedReliefStyle = reliefStyle;
    shadedReliefStrength = reliefStrength;
    for (int z = 0; z < SIZE; z++) {
      int row = z * SIZE;
      for (int x = 0; x < SIZE; x++) {
        int idx = row + x;
        short h = heights[idx];
        if (h == MapColorShading.UNEXPLORED_HEIGHT) {
          argb[idx] = MapColorShading.UNEXPLORED_ARGB;
          continue;
        }
        short nh = z > 0 ? heights[idx - SIZE] : cachedNorthSeam[x];
        short wh = x > 0 ? heights[idx - 1] : cachedWestSeam[z];
        argb[idx] = MapColorShading.shadePixel(baseRgb[idx], h, nh, wh, blockLight[idx] & 0xFF,
            terrainShading, useBlockLight, reliefStyle, reliefStrength);
      }
    }
    updateMipsForRect(0, 0, SIZE - 1, SIZE - 1);
    revision = NEXT_REVISION.incrementAndGet();
    lastAccessNanos = System.nanoTime();
  }

  public synchronized void ensureShadingMatches(boolean terrainShading, boolean useBlockLight) {
    ensureShadingMatches(terrainShading, useBlockLight, shadedReliefStyle, shadedReliefStrength);
  }

  public synchronized void ensureShadingMatches(boolean terrainShading, boolean useBlockLight,
      int reliefStyle, float reliefStrength) {
    if (shadedWithSlope != terrainShading || shadedWithLight != useBlockLight
        || shadedReliefStyle != reliefStyle
        || Math.abs(shadedReliefStrength - reliefStrength) > 0.005f) {
      rebuildShading(terrainShading, useBlockLight, reliefStyle, reliefStrength);
    }
  }

  public short heightAtLocal(int localX, int localZ) {
    if (localX < 0 || localX >= SIZE || localZ < 0 || localZ >= SIZE)
      return MapColorShading.UNEXPLORED_HEIGHT;
    return heights[(localZ << REGION_SHIFT) | localX];
  }

  public int argbAtLocal(int localX, int localZ) {
    if (localX < 0 || localX >= SIZE || localZ < 0 || localZ >= SIZE)
      return MapColorShading.UNEXPLORED_ARGB;
    return argb[(localZ << REGION_SHIFT) | localX];
  }

  public synchronized void copySouthEdgeHeights(int localCx, short[] out) {
    int base = (SIZE - 1) * SIZE + (localCx << 4);
    System.arraycopy(heights, base, out, 0, CHUNK_SIZE);
  }

  public synchronized void copyEastEdgeHeights(int localCz, short[] out) {
    int z0 = localCz << 4;
    for (int lz = 0; lz < CHUNK_SIZE; lz++) {
      out[lz] = heights[(z0 + lz) * SIZE + (SIZE - 1)];
    }
  }

  private void updateMipsForRect(int minX, int minZ, int maxX, int maxZ) {
    int x0 = minX >> 1, z0 = minZ >> 1, x1 = maxX >> 1, z1 = maxZ >> 1;
    for (int z = z0; z <= z1; z++) {
      int sz = z << 1;
      int src0 = sz * 512;
      int src1 = (sz + 1) * 512;
      int dst = z * 256;
      for (int x = x0; x <= x1; x++) {
        int sx = x << 1;
        mip2[dst + x] = MapColorShading.averageQuad(
            argb[src0 + sx], argb[src0 + sx + 1], argb[src1 + sx], argb[src1 + sx + 1]);
      }
    }
    x0 >>= 1;
    z0 >>= 1;
    x1 >>= 1;
    z1 >>= 1;
    for (int z = z0; z <= z1; z++) {
      int sz = z << 1;
      int src0 = sz * 256;
      int src1 = (sz + 1) * 256;
      int dst = z * 128;
      for (int x = x0; x <= x1; x++) {
        int sx = x << 1;
        mip4[dst + x] = MapColorShading.averageQuad(
            mip2[src0 + sx], mip2[src0 + sx + 1], mip2[src1 + sx], mip2[src1 + sx + 1]);
      }
    }
    x0 >>= 1;
    z0 >>= 1;
    x1 >>= 1;
    z1 >>= 1;
    for (int z = z0; z <= z1; z++) {
      int sz = z << 1;
      int src0 = sz * 128;
      int src1 = (sz + 1) * 128;
      int dst = z * 64;
      for (int x = x0; x <= x1; x++) {
        int sx = x << 1;
        mip8[dst + x] = MapColorShading.averageQuad(
            mip4[src0 + sx], mip4[src0 + sx + 1], mip4[src1 + sx], mip4[src1 + sx + 1]);
      }
    }
    x0 >>= 1;
    z0 >>= 1;
    x1 >>= 1;
    z1 >>= 1;
    for (int z = z0; z <= z1; z++) {
      int sz = z << 1;
      int src0 = sz * 64;
      int src1 = (sz + 1) * 64;
      int dst = z * 32;
      for (int x = x0; x <= x1; x++) {
        int sx = x << 1;
        mip16[dst + x] = MapColorShading.averageQuad(
            mip8[src0 + sx], mip8[src0 + sx + 1], mip8[src1 + sx], mip8[src1 + sx + 1]);
      }
    }
  }
}
