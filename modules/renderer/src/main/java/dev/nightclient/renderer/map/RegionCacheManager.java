package dev.nightclient.renderer.map;

import dev.nightclient.config.Settings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class RegionCacheManager {
  public static final int MAX_LOADED_REGIONS = 128;
  public static final int MAX_LOADED_REGIONS_HARD = 256;
  public static final int MAX_OVERVIEW_TILES = 4096;
  public static final int MINIMAP_WINDOW_SIZE = 512;
  public static final int MINIMAP_WINDOW_HALF = MINIMAP_WINDOW_SIZE / 2;
  public static final int MINIMAP_TEX_SCALE = 2;
  public static final int MINIMAP_TEX_SIZE = MINIMAP_WINDOW_SIZE * MINIMAP_TEX_SCALE;
  private static final long AUTOSAVE_INTERVAL_NANOS = 10_000_000_000L;
  private static volatile int[] SHARED_DEMO_PIXELS;

  public static final class OverviewTile {
    public final int[] mip8;
    public final int[] mip16;
    public final long revision;
    public final boolean terrainShading;
    public final boolean blockLight;
    public final int reliefStyle;
    public final float reliefStrength;

    OverviewTile(int[] mip8, int[] mip16, long revision, boolean terrainShading,
        boolean blockLight, int reliefStyle, float reliefStrength) {
      this.mip8 = mip8;
      this.mip16 = mip16;
      this.revision = revision;
      this.terrainShading = terrainShading;
      this.blockLight = blockLight;
      this.reliefStyle = reliefStyle;
      this.reliefStrength = reliefStrength;
    }

    public boolean matchesShading(
        boolean shading, boolean light, int style, float strength) {
      return terrainShading == shading && blockLight == light && reliefStyle == style
          && Math.abs(reliefStrength - strength) <= 0.005f;
    }

    public int[] pixelsForMip(int mipLevel) {
      return mipLevel >= 4 ? mip16 : mip8;
    }

    public int sizeForMip(int mipLevel) {
      return mipLevel >= 4 ? 32 : 64;
    }
  }

  private static final class ProvisionalChunk {
    final int cx, cz;
    final int[] baseRgb;
    final short[] heights;
    final byte[] blockLight;

    ProvisionalChunk(int cx, int cz, int[] baseRgb, short[] heights, byte[] blockLight) {
      this.cx = cx;
      this.cz = cz;
      this.baseRgb = Arrays.copyOf(baseRgb, 256);
      this.heights = Arrays.copyOf(heights, 256);
      this.blockLight = Arrays.copyOf(blockLight, 256);
    }
  }

  public static final class MinimapWindow {
    public final String cacheKey;
    public final long revision;
    public final int[] pixels;
    public final int originBlockX;
    public final int originBlockZ;
    public final int size;
    public final int texSize;

    MinimapWindow(String cacheKey, long revision, int[] pixels, int originBlockX,
        int originBlockZ, int size, int texSize) {
      this.cacheKey = cacheKey;
      this.revision = revision;
      this.pixels = pixels;
      this.originBlockX = originBlockX;
      this.originBlockZ = originBlockZ;
      this.size = size;
      this.texSize = texSize;
    }
  }

  private final DiskRegionStore store;
  private final WorldIdentityEngine identity;
  private final ExecutorService ioExecutor;

  private final Object lock = new Object();
  private final Map<Long, MapRegionTile> regions = new LinkedHashMap<Long, MapRegionTile>(64, 0.75f, true);
  private final Map<Long, OverviewTile> overviewTiles =
      new LinkedHashMap<Long, OverviewTile>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, OverviewTile> eldest) {
          return size() > MAX_OVERVIEW_TILES;
        }
      };
  private final Set<Long> pendingLoads = new HashSet<Long>();
  private final Set<Long> knownDiskRegions = new HashSet<Long>();
  private final Map<Long, ProvisionalChunk> provisionalChunks = new HashMap<Long, ProvisionalChunk>();
  private final AtomicLong provisionalRevision = new AtomicLong(1L);
  private final AtomicLong worldMapFrameCounter = new AtomicLong(1L);

  private volatile boolean diskCacheEnabled = true;
  private volatile boolean shadingSlope = true;
  private volatile boolean shadingLight = true;
  private volatile int reliefStyle = MapColorShading.RELIEF_CLASSIC;
  private volatile float reliefStrength = 1.35f;
  private volatile long lastAutosaveNanos = System.nanoTime();
  private volatile long activeEpoch = 1L;

  private final Object minimapLock = new Object();
  private final int[] minimapPixels = new int[MINIMAP_WINDOW_SIZE * MINIMAP_WINDOW_SIZE];
  private final int[] minimapTexPixels = new int[MINIMAP_TEX_SIZE * MINIMAP_TEX_SIZE];
  private int lastWinOriginX = Integer.MIN_VALUE;
  private int lastWinOriginZ = Integer.MIN_VALUE;
  private long lastWinSourceHash = Long.MIN_VALUE;
  private long minimapWindowRevision = 1L;
  private MinimapWindow cachedMinimapWindow;

  public RegionCacheManager(Path configDir) {
    this.store = new DiskRegionStore(configDir);
    this.identity = new WorldIdentityEngine(store);
    this.ioExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "NightClient-MapIO");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
      }
    });
  }

  public WorldIdentityEngine identity() {
    return identity;
  }

  public DiskRegionStore store() {
    return store;
  }

  public void syncSettings(Settings settings) {
    if (settings == null) return;
    boolean nextDisk = settings.worldMap == null || settings.worldMap.diskCache;
    boolean nextSlope = settings.minimap != null && settings.minimap.enabled
        ? settings.minimap.terrainShading
        : (settings.worldMap == null || settings.worldMap.terrainShading);
    boolean nextLight = settings.minimap != null && settings.minimap.enabled
        ? settings.minimap.blockLight
        : (settings.worldMap == null || settings.worldMap.blockLight);
    int nextStyle = settings.minimap != null
        ? MapColorShading.parseReliefStyle(settings.minimap.reliefStyle)
        : MapColorShading.RELIEF_CLASSIC;
    float nextStrength = settings.minimap != null ? settings.minimap.reliefStrength : 1.35f;
    diskCacheEnabled = nextDisk;
    shadingSlope = nextSlope;
    shadingLight = nextLight;
    reliefStyle = nextStyle;
    reliefStrength = nextStrength;
  }

  public int reliefStyle() {
    return reliefStyle;
  }

  public float reliefStrength() {
    return reliefStrength;
  }

  public long activeEpoch() {
    return activeEpoch;
  }

  public int loadedRegionCount() {
    synchronized (lock) {
      return regions.size();
    }
  }

  public int knownDiskRegionCount() {
    synchronized (lock) {
      return knownDiskRegions.size();
    }
  }

  public int clearCurrentWorldCache() {
    int clearedRam;
    final String srv = identity.serverKey();
    final String sub = identity.subWorldDir();
    final String dim = identity.dimension();
    final boolean locked = identity.isLocked();
    synchronized (lock) {
      clearedRam = regions.size();
      regions.clear();
      overviewTiles.clear();
      pendingLoads.clear();
      knownDiskRegions.clear();
      provisionalChunks.clear();
      provisionalRevision.incrementAndGet();
      activeEpoch++;
    }
    invalidateMinimapWindow();
    if (locked && store.isAvailable()) {
      submitIo(new Runnable() {
        @Override
        public void run() {
          store.deleteDimensionCache(srv, sub, dim);
        }
      });
    }
    return clearedRam;
  }

  public int clearAllCache() {
    int clearedRam;
    synchronized (lock) {
      clearedRam = regions.size();
      regions.clear();
      overviewTiles.clear();
      pendingLoads.clear();
      knownDiskRegions.clear();
      provisionalChunks.clear();
      provisionalRevision.incrementAndGet();
      activeEpoch++;
    }
    invalidateMinimapWindow();
    identity.resetIndex();
    if (store.isAvailable()) {
      submitIo(new Runnable() {
        @Override
        public void run() {
          store.deleteAllCache();
        }
      });
    }
    return clearedRam;
  }

  public static int[] sharedDemoTerrainPixels() {
    int[] cached = SHARED_DEMO_PIXELS;
    if (cached == null) {
      cached = MapColorShading.generateDemoTerrain(192);
      SHARED_DEMO_PIXELS = cached;
    }
    return cached;
  }

  public int[] demoTerrainPixels() {
    return sharedDemoTerrainPixels();
  }

  private void invalidateMinimapWindow() {
    synchronized (minimapLock) {
      lastWinOriginX = Integer.MIN_VALUE;
      lastWinOriginZ = Integer.MIN_VALUE;
      lastWinSourceHash = Long.MIN_VALUE;
      cachedMinimapWindow = null;
      minimapWindowRevision++;
    }
  }

  private Future<?> submitIo(Runnable task) {
    if (ioExecutor.isShutdown()) return null;
    try {
      return ioExecutor.submit(task);
    } catch (RuntimeException rejected) {
      return null;
    }
  }

  public void beginWorldSession(boolean singleplayer, String serverHost, String spFolder,
      String spDisplayName, String dimension, long hashedSeed, String spawnKey) {
    flushDirtyRegionsAsync();
    synchronized (lock) {
      regions.clear();
      overviewTiles.clear();
      pendingLoads.clear();
      knownDiskRegions.clear();
      provisionalChunks.clear();
      activeEpoch++;
    }
    invalidateMinimapWindow();
    identity.beginSession(
        singleplayer, serverHost, spFolder, spDisplayName, dimension, hashedSeed, spawnKey);
    if (identity.isLocked()) {
      onIdentityLocked();
    }
  }

  public void clearSession() {
    boolean hasProvisional;
    synchronized (lock) {
      hasProvisional = !provisionalChunks.isEmpty();
    }
    if (identity.state() == WorldIdentityEngine.State.PROVISIONAL && hasProvisional) {
      List<String> samples = buildProvisionalTerrainSamples();
      if (identity.forceLockIfProvisional(samples)) {
        flushProvisionalIntoRegions();
      }
    }
    flushDirtyRegionsSync();
    synchronized (lock) {
      regions.clear();
      overviewTiles.clear();
      pendingLoads.clear();
      knownDiskRegions.clear();
      provisionalChunks.clear();
      activeEpoch++;
    }
    invalidateMinimapWindow();
    identity.clear();
  }

  public void feedServerLevelId(String packetLevelId, Settings.Waypoints waypoints) {
    boolean wasLocked = identity.isLocked();
    String prevWorldId = identity.worldId();
    String prevSrv = identity.serverKey();
    String prevSub = identity.subWorldDir();
    String prevDim = identity.dimension();
    if (identity.feedServerLevelId(packetLevelId)) {
      if (wasLocked && !prevWorldId.equals(identity.worldId())) {
        flushAndResetForSubWorldSwitch(prevSrv, prevSub, prevDim);
      }
      onIdentityLocked();
      if (waypoints != null) identity.upgradeLegacyWaypoints(waypoints);
    }
  }

  public void feedSpawnKey(String spawnKey) {
    identity.feedSpawnKey(spawnKey);
  }

  public boolean feedRealmLabel(String realmLabel, Settings.Waypoints waypoints) {
    boolean wasLocked = identity.isLocked();
    String prevWorldId = identity.worldId();
    String prevSrv = identity.serverKey();
    String prevSub = identity.subWorldDir();
    String prevDim = identity.dimension();
    boolean changed = identity.feedRealmLabel(realmLabel);
    if (changed) {
      if (wasLocked && !prevWorldId.equals(identity.worldId())) {
        flushAndResetForSubWorldSwitch(prevSrv, prevSub, prevDim);
      }
      onIdentityLocked();
      if (waypoints != null) identity.upgradeLegacyWaypoints(waypoints);
    }
    return changed;
  }

  private void flushAndResetForSubWorldSwitch(
      final String prevSrv, final String prevSub, final String prevDim) {
    if (diskCacheEnabled && store.isAvailable() && prevSrv != null && !prevSrv.isEmpty()) {
      final List<MapRegionTile> dirty = collectDirtyTiles();
      if (!dirty.isEmpty()) {
        submitIo(new Runnable() {
          @Override
          public void run() {
            for (int i = 0; i < dirty.size(); i++) {
              try {
                store.saveRegion(prevSrv, prevSub, prevDim, dirty.get(i));
              } catch (Exception ignored) {
              }
            }
          }
        });
      }
    }
    synchronized (lock) {
      regions.clear();
      overviewTiles.clear();
      pendingLoads.clear();
      knownDiskRegions.clear();
      provisionalChunks.clear();
      activeEpoch++;
    }
    invalidateMinimapWindow();
  }

  public boolean selectManualSubWorld(String worldId, String customLabel) {
    flushDirtyRegionsAsync();
    boolean ok = identity.selectManualSubWorld(worldId, customLabel);
    if (ok) {
      synchronized (lock) {
        regions.clear();
        overviewTiles.clear();
        pendingLoads.clear();
        knownDiskRegions.clear();
        provisionalChunks.clear();
        activeEpoch++;
      }
      invalidateMinimapWindow();
      onIdentityLocked();
    }
    return ok;
  }

  public boolean tick(Settings.Waypoints waypoints) {
    boolean justLocked = false;
    if (identity.state() == WorldIdentityEngine.State.PROVISIONAL) {
      List<String> samples = buildProvisionalTerrainSamples();
      if (identity.tickProvisional(samples)) {
        onIdentityLocked();
        if (waypoints != null) identity.upgradeLegacyWaypoints(waypoints);
        justLocked = true;
      }
    }
    long now = System.nanoTime();
    if (now - lastAutosaveNanos >= AUTOSAVE_INTERVAL_NANOS) {
      lastAutosaveNanos = now;
      flushDirtyRegionsAsync();
    }
    return justLocked;
  }

  private void onIdentityLocked() {
    final String srv = identity.serverKey();
    final String sub = identity.subWorldDir();
    final String dim = identity.dimension();
    final long epoch;
    synchronized (lock) {
      activeEpoch++;
      epoch = activeEpoch;
      knownDiskRegions.clear();
    }
    invalidateMinimapWindow();
    flushProvisionalIntoRegions();
    if (diskCacheEnabled && store.isAvailable()) {
      submitIo(new Runnable() {
        @Override
        public void run() {
          Set<Long> found = store.discoverStoredRegions(srv, sub, dim);
          synchronized (lock) {
            if (epoch != activeEpoch) return;
            knownDiskRegions.addAll(found);
          }
          List<String> samples = buildLoadedTerrainSamples();
          if (!samples.isEmpty()) identity.enrichTerrainSamples(samples);
        }
      });
    }
  }

  private void flushProvisionalIntoRegions() {
    List<ProvisionalChunk> toApply;
    synchronized (lock) {
      if (provisionalChunks.isEmpty()) return;
      toApply = new ArrayList<ProvisionalChunk>(provisionalChunks.values());
      provisionalChunks.clear();
    }
    for (int i = 0; i < toApply.size(); i++) {
      ProvisionalChunk c = toApply.get(i);
      applyChunkToRegion(c.cx, c.cz, c.baseRgb, c.heights, c.blockLight);
    }
  }

  public void ingestChunk(
      int cx, int cz, int[] chunkBaseRgb, short[] chunkHeights, byte[] chunkLight) {
    if (!identity.isActive() || chunkBaseRgb == null || chunkHeights == null || chunkLight == null
        || chunkBaseRgb.length < 256 || chunkHeights.length < 256 || chunkLight.length < 256)
      return;
    if (!identity.isLocked()) {
      long key = chunkKey(cx, cz);
      synchronized (lock) {
        if (provisionalChunks.size() < 512 || provisionalChunks.containsKey(key)) {
          provisionalChunks.put(
              key, new ProvisionalChunk(cx, cz, chunkBaseRgb, chunkHeights, chunkLight));
          provisionalRevision.incrementAndGet();
        }
      }
      return;
    }
    applyChunkToRegion(cx, cz, chunkBaseRgb, chunkHeights, chunkLight);
  }

  private void applyChunkToRegion(
      int cx, int cz, int[] chunkBaseRgb, short[] chunkHeights, byte[] chunkLight) {
    int rx = MapRegionTile.chunkToRegion(cx);
    int rz = MapRegionTile.chunkToRegion(cz);
    int localCx = cx & 31;
    int localCz = cz & 31;

    MapRegionTile tile = getOrCreateRegion(rx, rz);
    short[] northSeam = null;
    short[] westSeam = null;
    if (localCz == 0) {
      MapRegionTile northRegion = getLoadedRegion(rx, rz - 1);
      if (northRegion != null) {
        northSeam = new short[16];
        northRegion.copySouthEdgeHeights(localCx, northSeam);
      }
    }
    if (localCx == 0) {
      MapRegionTile westRegion = getLoadedRegion(rx - 1, rz);
      if (westRegion != null) {
        westSeam = new short[16];
        westRegion.copyEastEdgeHeights(localCz, westSeam);
      }
    }

    tile.putChunk(localCx, localCz, chunkBaseRgb, chunkHeights, chunkLight, northSeam, westSeam,
        shadingSlope, shadingLight, reliefStyle, reliefStrength);

    if (localCz == 31) {
      MapRegionTile southRegion = getLoadedRegion(rx, rz + 1);
      if (southRegion != null) {
        short[] edge = new short[16];
        tile.copySouthEdgeHeights(localCx, edge);
        southRegion.reshadeNorthEdge(
            localCx, edge, shadingSlope, shadingLight, reliefStyle, reliefStrength);
      }
    }
    if (localCx == 31) {
      MapRegionTile eastRegion = getLoadedRegion(rx + 1, rz);
      if (eastRegion != null) {
        short[] edge = new short[16];
        tile.copyEastEdgeHeights(localCz, edge);
        eastRegion.reshadeWestEdge(
            localCz, edge, shadingSlope, shadingLight, reliefStyle, reliefStrength);
      }
    }
  }

  private MapRegionTile getOrCreateRegion(int rx, int rz) {
    long key = MapRegionTile.regionKey(rx, rz);
    MapRegionTile tile;
    synchronized (lock) {
      tile = regions.get(key);
      if (tile != null) {
        tile.touch();
        return tile;
      }
      tile = new MapRegionTile(rx, rz);
      regions.put(key, tile);
      knownDiskRegions.add(key);
      evictIfNeededLocked();
    }
    if (diskCacheEnabled && store.isAvailable() && identity.isLocked()) {
      requestAsyncDiskMerge(rx, rz, key);
    }
    return tile;
  }

  public MapRegionTile getOrLoadRegion(int rx, int rz) {
    long key = MapRegionTile.regionKey(rx, rz);
    synchronized (lock) {
      MapRegionTile tile = regions.get(key);
      if (tile != null) {
        tile.touch();
        return tile;
      }
      if (!diskCacheEnabled || !store.isAvailable() || !identity.isLocked()) return null;
      if (!knownDiskRegions.contains(key) || pendingLoads.contains(key)) return null;
      pendingLoads.add(key);
    }
    requestAsyncDiskMerge(rx, rz, key);
    return null;
  }

  private void requestAsyncDiskMerge(final int rx, final int rz, final long key) {
    final String srv = identity.serverKey();
    final String sub = identity.subWorldDir();
    final String dim = identity.dimension();
    final long epoch = activeEpoch;
    final boolean slope = shadingSlope;
    final boolean light = shadingLight;
    final int rStyle = reliefStyle;
    final float rStrength = reliefStrength;
    submitIo(new Runnable() {
      @Override
      public void run() {
        try {
          MapRegionTile loaded =
              store.loadRegion(srv, sub, dim, rx, rz, slope, light, rStyle, rStrength);
          OverviewTile loadedOverview = loaded != null ? loaded.captureOverview() : null;
          MapRegionTile existingToMerge = null;
          synchronized (lock) {
            pendingLoads.remove(key);
            if (epoch != activeEpoch || loaded == null) return;
            if (loadedOverview != null) overviewTiles.put(key, loadedOverview);
            MapRegionTile existing = regions.get(key);
            if (existing == null) {
              regions.put(key, loaded);
              evictIfNeededLocked();
            } else {
              existingToMerge = existing;
            }
          }
          if (existingToMerge != null) {
            mergeDiskIntoLive(loaded, existingToMerge, slope, light, rStyle, rStrength);
            syncOverviewIfEpoch(existingToMerge, epoch);
          }
        } catch (Throwable ignored) {
          synchronized (lock) {
            pendingLoads.remove(key);
          }
        }
      }
    });
  }

  private static void mergeDiskIntoLive(MapRegionTile disk, MapRegionTile live, boolean slope,
      boolean light, int rStyle, float rStrength) {
    boolean merged = false;
    synchronized (live) {
      for (int cz = 0; cz < MapRegionTile.CHUNKS_PER_SIDE; cz++) {
        for (int cx = 0; cx < MapRegionTile.CHUNKS_PER_SIDE; cx++) {
          if (!disk.isChunkPopulated(cx, cz) || live.isChunkPopulated(cx, cz)) continue;
          int bx0 = cx << 4;
          int bz0 = cz << 4;
          for (int lz = 0; lz < 16; lz++) {
            int row = (bz0 + lz) * MapRegionTile.SIZE + bx0;
            System.arraycopy(disk.baseRgb, row, live.baseRgb, row, 16);
            System.arraycopy(disk.heights, row, live.heights, row, 16);
            System.arraycopy(disk.blockLight, row, live.blockLight, row, 16);
          }
          int bit = (cz << 5) | cx;
          live.populatedMask[bit >>> 6] |= (1L << (bit & 63));
          merged = true;
        }
      }
      if (merged) live.rebuildShading(slope, light, rStyle, rStrength);
    }
  }

  public long beginWorldMapFrame() {
    return worldMapFrameCounter.incrementAndGet();
  }

  public OverviewTile getOverviewTile(int rx, int rz) {
    long key = MapRegionTile.regionKey(rx, rz);
    synchronized (lock) {
      return overviewTiles.get(key);
    }
  }

  public void syncOverview(MapRegionTile tile) {
    syncOverviewIfEpoch(tile, -1L);
  }

  private void syncOverviewIfEpoch(MapRegionTile tile, long expectedEpoch) {
    if (tile == null) return;
    long key = MapRegionTile.regionKey(tile.regionX, tile.regionZ);
    long rev = tile.revision();
    synchronized (lock) {
      if (expectedEpoch >= 0L && expectedEpoch != activeEpoch) return;
      OverviewTile cur = overviewTiles.get(key);
      if (cur != null && cur.revision == rev) return;
    }
    OverviewTile snap = tile.captureOverview();
    if (snap == null) return;
    synchronized (lock) {
      if (expectedEpoch >= 0L && expectedEpoch != activeEpoch) return;
      overviewTiles.put(key, snap);
    }
  }

  private void evictIfNeededLocked() {
    long currentFrame = worldMapFrameCounter.get();
    while (regions.size() > MAX_LOADED_REGIONS) {
      boolean forceEvict = regions.size() > MAX_LOADED_REGIONS_HARD;
      Long victimKey = null;
      MapRegionTile victimTile = null;
      Iterator<Map.Entry<Long, MapRegionTile>> it = regions.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<Long, MapRegionTile> entry = it.next();
        MapRegionTile candidate = entry.getValue();
        if (forceEvict || candidate == null || candidate.lastRenderedFrame() < currentFrame - 1L) {
          victimKey = entry.getKey();
          victimTile = candidate;
          it.remove();
          break;
        }
      }
      if (victimKey == null) break;
      final MapRegionTile tile = victimTile;
      if (tile != null) {
        OverviewTile snap = tile.captureOverview();
        if (snap != null) overviewTiles.put(victimKey, snap);
      }
      if (tile != null && tile.isDiskDirty() && diskCacheEnabled && store.isAvailable()
          && identity.isLocked()) {
        final String srv = identity.serverKey();
        final String sub = identity.subWorldDir();
        final String dim = identity.dimension();
        submitIo(new Runnable() {
          @Override
          public void run() {
            try {
              store.saveRegion(srv, sub, dim, tile);
            } catch (Exception ignored) {
            }
          }
        });
      }
    }
  }

  public MapRegionTile getLoadedRegion(int rx, int rz) {
    synchronized (lock) {
      return regions.get(MapRegionTile.regionKey(rx, rz));
    }
  }

  public short heightAt(int blockX, int blockZ) {
    int rx = MapRegionTile.blockToRegion(blockX);
    int rz = MapRegionTile.blockToRegion(blockZ);
    MapRegionTile tile = getLoadedRegion(rx, rz);
    if (tile != null) {
      return tile.heightAtLocal(blockX & 511, blockZ & 511);
    }
    if (!identity.isLocked()) {
      synchronized (lock) {
        ProvisionalChunk c = provisionalChunks.get(chunkKey(blockX >> 4, blockZ >> 4));
        if (c != null) return c.heights[((blockZ & 15) << 4) | (blockX & 15)];
      }
    }
    return MapColorShading.UNEXPLORED_HEIGHT;
  }

  public MinimapWindow composeMinimapWindow(
      double playerX, double playerZ, boolean terrainShading, boolean blockLight) {
    return composeMinimapWindow(
        playerX, playerZ, terrainShading, blockLight, reliefStyle, reliefStrength);
  }

  public MinimapWindow composeMinimapWindow(double playerX, double playerZ, boolean terrainShading,
      boolean blockLight, int rStyle, float rStrength) {
    int centerChunkX = ((int) Math.floor(playerX)) >> 4;
    int centerChunkZ = ((int) Math.floor(playerZ)) >> 4;
    int originX = (centerChunkX << 4) - MINIMAP_WINDOW_HALF;
    int originZ = (centerChunkZ << 4) - MINIMAP_WINDOW_HALF;

    int minRx = MapRegionTile.blockToRegion(originX);
    int maxRx = MapRegionTile.blockToRegion(originX + MINIMAP_WINDOW_SIZE - 1);
    int minRz = MapRegionTile.blockToRegion(originZ);
    int maxRz = MapRegionTile.blockToRegion(originZ + MINIMAP_WINDOW_SIZE - 1);

    long sourceHash = 1469598103934665603L;
    sourceHash = (sourceHash ^ activeEpoch) * 1099511628211L;
    sourceHash = (sourceHash ^ (terrainShading ? 1L : 2L)) * 1099511628211L;
    sourceHash = (sourceHash ^ (blockLight ? 3L : 4L)) * 1099511628211L;
    sourceHash = (sourceHash ^ (long) rStyle) * 1099511628211L;
    sourceHash = (sourceHash ^ (long) Math.round(rStrength * 100f)) * 1099511628211L;

    boolean locked = identity.isLocked();
    if (!locked) {
      sourceHash = (sourceHash ^ provisionalRevision.get()) * 1099511628211L;
    } else {
      for (int rz = minRz; rz <= maxRz; rz++) {
        for (int rx = minRx; rx <= maxRx; rx++) {
          MapRegionTile tile = getOrLoadRegion(rx, rz);
          long rev = 0L;
          if (tile != null) {
            tile.ensureShadingMatches(terrainShading, blockLight, rStyle, rStrength);
            rev = tile.revision();
          }
          sourceHash = (sourceHash ^ rev) * 1099511628211L;
        }
      }
    }

    synchronized (minimapLock) {
      if (originX == lastWinOriginX && originZ == lastWinOriginZ
          && sourceHash == lastWinSourceHash && cachedMinimapWindow != null) {
        return cachedMinimapWindow;
      }

      java.util.Arrays.fill(minimapPixels, MapColorShading.UNEXPLORED_ARGB);

      if (!locked) {
        synchronized (lock) {
          int minCx = originX >> 4;
          int maxCx = (originX + MINIMAP_WINDOW_SIZE - 1) >> 4;
          int minCz = originZ >> 4;
          int maxCz = (originZ + MINIMAP_WINDOW_SIZE - 1) >> 4;
          for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
              ProvisionalChunk c = provisionalChunks.get(chunkKey(cx, cz));
              if (c == null) continue;
              int dstX0 = (cx << 4) - originX;
              int dstZ0 = (cz << 4) - originZ;
              for (int lz = 0; lz < 16; lz++) {
                int dz = dstZ0 + lz;
                if (dz < 0 || dz >= MINIMAP_WINDOW_SIZE) continue;
                int dstRow = dz * MINIMAP_WINDOW_SIZE + dstX0;
                int srcRow = lz << 4;
                for (int lx = 0; lx < 16; lx++) {
                  int dx = dstX0 + lx;
                  if (dx < 0 || dx >= MINIMAP_WINDOW_SIZE) continue;
                  int sIdx = srcRow + lx;
                  short h = c.heights[sIdx];
                  if (h == MapColorShading.UNEXPLORED_HEIGHT) continue;
                  short nh = lz > 0 ? c.heights[sIdx - 16] : h;
                  short wh = lx > 0 ? c.heights[sIdx - 1] : h;
                  minimapPixels[dstRow + lx] = MapColorShading.shadePixel(c.baseRgb[sIdx], h, nh,
                      wh, c.blockLight[sIdx] & 0xFF, terrainShading, blockLight, rStyle, rStrength);
                }
              }
            }
          }
        }
      } else {
        for (int rz = minRz; rz <= maxRz; rz++) {
          int regWorldZ = rz << MapRegionTile.REGION_SHIFT;
          int zStart = Math.max(originZ, regWorldZ);
          int zEnd = Math.min(originZ + MINIMAP_WINDOW_SIZE, regWorldZ + MapRegionTile.SIZE);
          int copyH = zEnd - zStart;
          if (copyH <= 0) continue;
          for (int rx = minRx; rx <= maxRx; rx++) {
            MapRegionTile tile = getLoadedRegion(rx, rz);
            if (tile == null) continue;
            int regWorldX = rx << MapRegionTile.REGION_SHIFT;
            int xStart = Math.max(originX, regWorldX);
            int xEnd = Math.min(originX + MINIMAP_WINDOW_SIZE, regWorldX + MapRegionTile.SIZE);
            int copyLen = xEnd - xStart;
            if (copyLen <= 0) continue;
            tile.copyArgbRect(xStart - regWorldX, zStart - regWorldZ, copyLen, copyH, minimapPixels,
                xStart - originX, zStart - originZ, MINIMAP_WINDOW_SIZE);
          }
        }
      }

      for (int z = 0; z < MINIMAP_WINDOW_SIZE; z++) {
        int srcRow = z * MINIMAP_WINDOW_SIZE;
        int dstRow0 = (z << 1) * MINIMAP_TEX_SIZE;
        int dstRow1 = dstRow0 + MINIMAP_TEX_SIZE;
        for (int x = 0; x < MINIMAP_WINDOW_SIZE; x++) {
          int px = minimapPixels[srcRow + x];
          int dx = x << 1;
          minimapTexPixels[dstRow0 + dx] = px;
          minimapTexPixels[dstRow0 + dx + 1] = px;
          minimapTexPixels[dstRow1 + dx] = px;
          minimapTexPixels[dstRow1 + dx + 1] = px;
        }
      }

      lastWinOriginX = originX;
      lastWinOriginZ = originZ;
      lastWinSourceHash = sourceHash;
      minimapWindowRevision++;
      cachedMinimapWindow = new MinimapWindow("minimap_window", minimapWindowRevision,
          minimapTexPixels, originX, originZ, MINIMAP_WINDOW_SIZE, MINIMAP_TEX_SIZE);
      return cachedMinimapWindow;
    }
  }

  public List<Long> knownDiskRegionKeys() {
    synchronized (lock) {
      if (knownDiskRegions.isEmpty()) return Collections.emptyList();
      return new ArrayList<Long>(knownDiskRegions);
    }
  }

  public List<Long> allKnownRegionKeys() {
    Set<Long> merged;
    synchronized (lock) {
      int cap = knownDiskRegions.size() + regions.size() + overviewTiles.size();
      if (cap == 0) return Collections.emptyList();
      merged = new HashSet<Long>(cap);
      merged.addAll(knownDiskRegions);
      merged.addAll(regions.keySet());
      merged.addAll(overviewTiles.keySet());
    }
    List<Long> sorted = new ArrayList<Long>(merged);
    Collections.sort(sorted, new java.util.Comparator<Long>() {
      @Override
      public int compare(Long a, Long b) {
        long ka = a.longValue(), kb = b.longValue();
        int rza = (int) ka, rzb = (int) kb;
        if (rza != rzb) return Integer.compare(rza, rzb);
        return Integer.compare((int) (ka >> 32), (int) (kb >> 32));
      }
    });
    return sorted;
  }

  private List<String> buildProvisionalTerrainSamples() {
    List<String> out = new ArrayList<String>(48);
    synchronized (lock) {
      for (ProvisionalChunk c : provisionalChunks.values()) {
        if (out.size() >= 48) break;
        for (int s = 0; s < 4 && out.size() < 48; s++) {
          int lx = (s & 1) == 0 ? 4 : 12;
          int lz = (s & 2) == 0 ? 4 : 12;
          int idx = (lz << 4) | lx;
          short h = c.heights[idx];
          if (h == MapColorShading.UNEXPLORED_HEIGHT) continue;
          out.add(WorldIdentityEngine.encodeTerrainSample(
              (c.cx << 4) + lx, (c.cz << 4) + lz, h, c.baseRgb[idx]));
        }
      }
    }
    return out;
  }

  private List<String> buildLoadedTerrainSamples() {
    List<String> out = new ArrayList<String>(48);
    synchronized (lock) {
      for (MapRegionTile tile : regions.values()) {
        if (out.size() >= 48) break;
        synchronized (tile) {
          for (int cz = 0; cz < 32 && out.size() < 48; cz++) {
            for (int cx = 0; cx < 32 && out.size() < 48; cx++) {
              if (!tile.isChunkPopulated(cx, cz)) continue;
              for (int s = 0; s < 4 && out.size() < 48; s++) {
                int localX = (cx << 4) + ((s & 1) == 0 ? 4 : 12);
                int localZ = (cz << 4) + ((s & 2) == 0 ? 4 : 12);
                int idx = (localZ << MapRegionTile.REGION_SHIFT) | localX;
                short h = tile.heights[idx];
                if (h == MapColorShading.UNEXPLORED_HEIGHT) continue;
                int wx = (tile.regionX << MapRegionTile.REGION_SHIFT) + localX;
                int wz = (tile.regionZ << MapRegionTile.REGION_SHIFT) + localZ;
                out.add(WorldIdentityEngine.encodeTerrainSample(wx, wz, h, tile.baseRgb[idx]));
              }
            }
          }
        }
      }
    }
    return out;
  }

  public void flushDirtyRegionsAsync() {
    if (!diskCacheEnabled || !store.isAvailable() || !identity.isLocked()) return;
    final List<MapRegionTile> dirty = collectDirtyTiles();
    if (dirty.isEmpty()) return;
    final String srv = identity.serverKey();
    final String sub = identity.subWorldDir();
    final String dim = identity.dimension();
    submitIo(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < dirty.size(); i++) {
          try {
            store.saveRegion(srv, sub, dim, dirty.get(i));
          } catch (Exception ignored) {
          }
        }
      }
    });
  }

  public void flushDirtyRegionsSync() {
    if (!diskCacheEnabled || !store.isAvailable() || !identity.isLocked()) return;
    final List<MapRegionTile> dirty = collectDirtyTiles();
    if (dirty.isEmpty()) return;
    final String srv = identity.serverKey();
    final String sub = identity.subWorldDir();
    final String dim = identity.dimension();
    Future<?> future = submitIo(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < dirty.size(); i++) {
          try {
            store.saveRegion(srv, sub, dim, dirty.get(i));
          } catch (Exception ignored) {
          }
        }
      }
    });
    if (future != null) {
      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (Exception ignored) {
      }
    } else {
      for (int i = 0; i < dirty.size(); i++) {
        try {
          store.saveRegion(srv, sub, dim, dirty.get(i));
        } catch (Exception ignored) {
        }
      }
    }
  }

  private List<MapRegionTile> collectDirtyTiles() {
    List<MapRegionTile> dirty = new ArrayList<MapRegionTile>();
    synchronized (lock) {
      for (MapRegionTile tile : regions.values()) {
        if (tile != null && tile.isDiskDirty()) dirty.add(tile);
      }
    }
    return dirty;
  }

  public void shutdown() {
    clearSession();
    ioExecutor.shutdown();
    try {
      if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) ioExecutor.shutdownNow();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      ioExecutor.shutdownNow();
    }
  }

  private static long chunkKey(int cx, int cz) {
    return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
  }
}
