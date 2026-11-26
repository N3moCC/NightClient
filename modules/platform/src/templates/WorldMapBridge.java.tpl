package dev.nightclient.platform;

import dev.nightclient.api.MapRadarModels;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.map.MapBlockPalette;
import dev.nightclient.renderer.map.MapColorShading;
import dev.nightclient.renderer.map.MapEntityIconComposer;
import dev.nightclient.renderer.map.MapEntityTextures;
import dev.nightclient.renderer.map.RegionCacheManager;
import dev.nightclient.renderer.map.WorldIdentityEngine;
import dev.nightclient.runtime.ClientRuntime;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
@@MAP_IMPORTS@@

public final class WorldMapBridge {
    private static final int MAX_CHUNKS_PER_TICK = 6;
    private static final long TICK_SCAN_BUDGET_NANOS = 1_100_000L;
    private static final int RADAR_ITEM_SLOTS = 64;
    private static final int RADAR_SLOT_BASE = 400;

    private static final Map<Block, MapBlockPalette.BlockEntry> BLOCK_ENTRY_CACHE =
        new IdentityHashMap<Block, MapBlockPalette.BlockEntry>(256);
    private static Object cachedBlockResourceManager;
    private static int cachedBlockResourceEpoch = -1;
    private static final Map<EntityType<?>, String> ENTITY_TYPE_ID_CACHE = new IdentityHashMap<EntityType<?>, String>(128);
    private static final Map<EntityType<?>, Integer> ENTITY_KIND_CACHE = new IdentityHashMap<EntityType<?>, Integer>(128);
    private static final Map<net.minecraft.world.item.Item, String> ITEM_ID_CACHE = new IdentityHashMap<net.minecraft.world.item.Item, String>(256);
    private static final Map<net.minecraft.world.item.Item, Boolean> ITEM_VALUABLE_ID_CACHE = new IdentityHashMap<net.minecraft.world.item.Item, Boolean>(256);
    private static final Map<UUID, String> PLAYER_UUID_CACHE = new HashMap<UUID, String>(32);
    private static final Map<String, MapEntityIconComposer.ComposedIcon[]> ENTITY_ICON_BY_TYPE =
        new HashMap<String, MapEntityIconComposer.ComposedIcon[]>(160);
    private static Object cachedResourceManager;
    private static int cachedIconResourceEpoch = -1;
    private static long entityIconRevision = 1L;
    private static final int[] SCAN_BASE_RGB = new int[256];
    private static final short[] SCAN_HEIGHTS = new short[256];
    private static final byte[] SCAN_LIGHTS = new byte[256];
    private static final BlockPos.MutableBlockPos SCAN_POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SCAN_ABOVE_POS = new BlockPos.MutableBlockPos();
    private static final Set<Long> DIRTY_CHUNK_SET = new HashSet<Long>(256);
    private static final List<Long> DIRTY_CHUNK_LIST = new ArrayList<Long>(256);

    private static final ItemStack[] radarItemCache = new ItemStack[RADAR_ITEM_SLOTS];
    private static boolean hasRadarItemSlots = false;
    private static Object trackedLevel;
    private static String trackedDimension = "";
    private static int lastSpiralCx = Integer.MIN_VALUE, lastSpiralCz = Integer.MIN_VALUE;
    private static int realmPollCounter = 0;
    private static boolean lastBiomeColors = true;

    private static Object cachedRadarLevel;
    private static Object cachedRadarPlayer;
    private static int cachedRadarTick = Integer.MIN_VALUE;
    private static int cachedRadarConfigHash = 0;
    private static int cachedRadarResourceEpoch = -1;
    private static String cachedDim = "minecraft:overworld";
    private static String cachedWorldId = "";
    private static String cachedWorldLabel = "";
    private static final List<CachedPlayer> CACHED_PLAYERS = new ArrayList<CachedPlayer>(16);
    private static final List<CachedItem> CACHED_ITEMS = new ArrayList<CachedItem>(64);
    private static final List<CachedMob> CACHED_MOBS = new ArrayList<CachedMob>(64);
    private static boolean anyOtherPlayerMoving = false;
    private static boolean anyCachedItemMoving = false;
    private static boolean anyCachedMobMoving = false;
    private static List<MapRadarModels.ItemEntry> staticItems = Collections.emptyList();
    private static List<MapRadarModels.MobEntry> staticMobs = Collections.emptyList();
    private static MapRadarModels.RadarSnapshot lastSnapshot = null;
    private static double lastSelfX, lastSelfY, lastSelfZ;
    private static float lastSelfYaw, lastPartialTick = Float.NaN;
    private static final List<ItemCluster> SCRATCH_CLUSTERS = new ArrayList<ItemCluster>(32);
    private static final List<EntityCluster> SCRATCH_ORB_CLUSTERS = new ArrayList<EntityCluster>(8);
    private static final List<EntityCluster> SCRATCH_ARROW_CLUSTERS = new ArrayList<EntityCluster>(8);
    private static final List<CachedMob> SCRATCH_MOBS = new ArrayList<CachedMob>(64);
    private static double sortRefX, sortRefZ;

    private static final Comparator<CachedMob> MOB_DIST_CMP = new Comparator<CachedMob>() {
        @Override
        public int compare(CachedMob a, CachedMob b) {
            int pa = entityPriority(a.kind);
            int pb = entityPriority(b.kind);
            if (pa != pb) return Integer.compare(pa, pb);
            double da = (a.x1 - sortRefX) * (a.x1 - sortRefX) + (a.z1 - sortRefZ) * (a.z1 - sortRefZ);
            double db = (b.x1 - sortRefX) * (b.x1 - sortRefX) + (b.z1 - sortRefZ) * (b.z1 - sortRefZ);
            return Double.compare(da, db);
        }
    };
    private static final Comparator<CachedMob> MOB_PRIORITY_DESC_CMP = new Comparator<CachedMob>() {
        @Override
        public int compare(CachedMob a, CachedMob b) {
            return Integer.compare(entityPriority(b.kind), entityPriority(a.kind));
        }
    };
    private static final Comparator<ItemCluster> ITEM_CLUSTER_CMP = new Comparator<ItemCluster>() {
        @Override
        public int compare(ItemCluster a, ItemCluster b) {
            if (a.valuable != b.valuable) return a.valuable ? -1 : 1;
            double da = (a.x1 - sortRefX) * (a.x1 - sortRefX) + (a.z1 - sortRefZ) * (a.z1 - sortRefZ);
            double db = (b.x1 - sortRefX) * (b.x1 - sortRefX) + (b.z1 - sortRefZ) * (b.z1 - sortRefZ);
            return Double.compare(da, db);
        }
    };

    private WorldMapBridge() {}

    private static void resetRadarCache() {
        cachedRadarLevel = null;
        cachedRadarPlayer = null;
        cachedRadarTick = Integer.MIN_VALUE;
        cachedRadarConfigHash = 0;
        cachedRadarResourceEpoch = -1;
        CACHED_PLAYERS.clear();
        CACHED_ITEMS.clear();
        CACHED_MOBS.clear();
        SCRATCH_CLUSTERS.clear();
        SCRATCH_ORB_CLUSTERS.clear();
        SCRATCH_ARROW_CLUSTERS.clear();
        SCRATCH_MOBS.clear();
        anyOtherPlayerMoving = false;
        anyCachedItemMoving = false;
        anyCachedMobMoving = false;
        staticItems = Collections.emptyList();
        staticMobs = Collections.emptyList();
        lastSnapshot = null;
        lastPartialTick = Float.NaN;
    }

    public static ItemStack cachedRadarItemStack(int slot) {
        int idx = slot - RADAR_SLOT_BASE;
        return idx >= 0 && idx < radarItemCache.length && radarItemCache[idx] != null
            ? radarItemCache[idx]
            : ItemStack.EMPTY;
    }

    public static void clear() {
        synchronized (DIRTY_CHUNK_SET) {
            DIRTY_CHUNK_SET.clear();
            DIRTY_CHUNK_LIST.clear();
        }
        Arrays.fill(radarItemCache, null);
        hasRadarItemSlots = false;
        resetRadarCache();
        PLAYER_UUID_CACHE.clear();
        trackedLevel = null;
        trackedDimension = "";
        lastSpiralCx = Integer.MIN_VALUE;
        lastSpiralCz = Integer.MIN_VALUE;
        realmPollCounter = 0;
        MapTextureBackend.clearMaps();
    }

    public static void onWorldTransition() {
        synchronized (DIRTY_CHUNK_SET) {
            DIRTY_CHUNK_SET.clear();
            DIRTY_CHUNK_LIST.clear();
        }
        if (hasRadarItemSlots) {
            Arrays.fill(radarItemCache, null);
            hasRadarItemSlots = false;
        }
        resetRadarCache();
        trackedLevel = null;
        trackedDimension = "";
        lastSpiralCx = Integer.MIN_VALUE;
        lastSpiralCz = Integer.MIN_VALUE;
    }

    public static void rescanLoadedChunks() {
        Minecraft mc = Minecraft.getInstance();
        synchronized (DIRTY_CHUNK_SET) {
            DIRTY_CHUNK_SET.clear();
            DIRTY_CHUNK_LIST.clear();
        }
        lastSpiralCx = Integer.MIN_VALUE;
        lastSpiralCz = Integer.MIN_VALUE;
        if (mc.level != null && mc.player != null) {
            enqueueSurroundingChunks(mc, 12);
        }
    }

    public static void onChunkLoaded(int cx, int cz) {
        enqueueDirtyChunk(cx, cz);
    }

    public static void onBlockUpdated(BlockPos pos) {
        if (pos == null) return;
        enqueueDirtyChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static void onSpawnUpdated(BlockPos pos) {
        if (pos == null) return;
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime != null) {
            runtime.mapCache().feedSpawnKey(pos.getX() + "_" + pos.getY() + "_" + pos.getZ());
        }
    }

    public static void onCustomPayload(String channel, byte[] data) {
        if (channel == null || data == null || data.length < 5) return;
        if (!"xaeroworldmap:main".equals(channel) && !"xaerominimap:main".equals(channel)) return;
        if (data[0] != 0) return;
        int levelId = ((data[1] & 0xFF) << 24) | ((data[2] & 0xFF) << 16)
            | ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime != null) {
            String prevId = runtime.mapCache().identity().worldId();
            runtime.mapCache().feedServerLevelId(
                Integer.toString(levelId), runtime.settings().waypoints);
            String nextId = runtime.mapCache().identity().worldId();
            if (prevId != null && nextId != null && !prevId.equals(nextId)) {
                resetRadarCache();
                lastSpiralCx = Integer.MIN_VALUE;
                lastSpiralCz = Integer.MIN_VALUE;
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.level != null && mc.player != null) {
                    enqueueSurroundingChunks(mc, 10);
                }
            }
        }
    }

    private static void enqueueDirtyChunk(int cx, int cz) {
        long key = chunkKey(cx, cz);
        synchronized (DIRTY_CHUNK_SET) {
            if (DIRTY_CHUNK_SET.size() >= 1024) return;
            if (DIRTY_CHUNK_SET.add(key)) DIRTY_CHUNK_LIST.add(key);
        }
    }

    public static void tick() {
        if (BenchmarkDirector.active()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime == null || mc.level == null || mc.player == null) {
            if (trackedLevel != null) clear();
            return;
        }
        RegionCacheManager cache = runtime.mapCache();
        Settings settings = runtime.settings();
        boolean mapActive = (settings.minimap != null && settings.minimap.enabled)
            || (settings.worldMap != null && settings.worldMap.enabled);

        String dim = WorldFeatureBridge.currentDimension();
        if (mc.level != trackedLevel || !dim.equals(trackedDimension)) {
            beginSessionForLevel(mc, runtime, dim);
        }

        realmPollCounter++;
        if (!cache.identity().isLocked() || (realmPollCounter % 20 == 0)) {
            String realm = detectRealmLabel();
            if (!realm.isEmpty()) {
                boolean switched = cache.feedRealmLabel(realm, settings.waypoints);
                if (switched) {
                    enqueueSurroundingChunks(mc, 10);
                }
            }
        }

        cache.tick(settings.waypoints);

        if (!mapActive) return;

        boolean biomeColors = settings.minimap != null && settings.minimap.enabled
            ? settings.minimap.biomeColors
            : (settings.worldMap == null || settings.worldMap.biomeColors);
        if (biomeColors != lastBiomeColors) {
            lastBiomeColors = biomeColors;
            enqueueSurroundingChunks(mc, 10);
        }

        int pcx = ((int) Math.floor(mc.player.getX())) >> 4;
        int pcz = ((int) Math.floor(mc.player.getZ())) >> 4;
        if (pcx != lastSpiralCx || pcz != lastSpiralCz) {
            lastSpiralCx = pcx;
            lastSpiralCz = pcz;
            enqueueMissingNearbyChunks(mc, cache, pcx, pcz, 7);
        }

        processDirtyChunks(mc, cache, pcx, pcz, biomeColors, dim);
    }

    private static void beginSessionForLevel(Minecraft mc, ClientRuntime runtime, String dim) {
        trackedLevel = mc.level;
        trackedDimension = dim;
        synchronized (DIRTY_CHUNK_SET) {
            DIRTY_CHUNK_SET.clear();
            DIRTY_CHUNK_LIST.clear();
        }
        lastSpiralCx = Integer.MIN_VALUE;
        lastSpiralCz = Integer.MIN_VALUE;
        realmPollCounter = 0;

        IntegratedServer sp = mc.getSingleplayerServer();
        boolean isSp = sp != null;
        String spFolder = "";
        String spName = "";
        String host = "";
        if (isSp) {
            try {
                java.nio.file.Path root =
                    sp.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                if (root != null) {
                    java.nio.file.Path norm = root.normalize();
                    if (norm.getFileName() != null) spFolder = norm.getFileName().toString();
                }
            } catch (Throwable ignored) {}
            try {
                spName = sp.getWorldData().getLevelName();
            } catch (Throwable ignored) {}
            if (spFolder.isEmpty()) spFolder = spName;
        } else {
            ServerData srv = mc.getCurrentServer();
            host = srv == null ? "" : srv.ip;
        }

        long seed = WorldFeatureBridge.biomeZoomSeedForMap(mc);
        String spawnKey = WorldFeatureBridge.spawnKeyForMap(mc);
        runtime.mapCache().beginWorldSession(isSp, host, spFolder, spName, dim, seed, spawnKey);

        String realm = detectRealmLabel();
        if (!realm.isEmpty()) {
            runtime.mapCache().feedRealmLabel(realm, runtime.settings().waypoints);
        }
        enqueueSurroundingChunks(mc, 10);
    }

    private static String detectRealmLabel() {
        List<String> lines = new ArrayList<String>(16);
        Map<String, Object> sb = WorldFeatureBridge.scoreboard();
        if (sb != null && !sb.isEmpty()) {
            Object titleObj = sb.get("title");
            if (titleObj != null) lines.add(titleObj.toString());
            Object linesObj = sb.get("lines");
            if (linesObj instanceof List) {
                List<?> raw = (List<?>) linesObj;
                for (int i = 0; i < raw.size(); i++) {
                    Object entry = raw.get(i);
                    if (entry instanceof Map) {
                        Object t = ((Map<?, ?>) entry).get("text");
                        if (t != null) lines.add(t.toString());
                    } else if (entry != null) {
                        lines.add(entry.toString());
                    }
                }
            }
        }
        return WorldIdentityEngine.extractRealmLabel(lines);
    }

    private static void enqueueSurroundingChunks(Minecraft mc, int radius) {
        if (mc.player == null) return;
        int pcx = ((int) Math.floor(mc.player.getX())) >> 4;
        int pcz = ((int) Math.floor(mc.player.getZ())) >> 4;
        for (int r = 0; r <= radius; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    enqueueDirtyChunk(pcx + dx, pcz + dz);
                }
            }
        }
    }

    private static void enqueueMissingNearbyChunks(
        Minecraft mc, RegionCacheManager cache, int pcx, int pcz, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                int rx = cx >> 5;
                int rz = cz >> 5;
                dev.nightclient.renderer.map.MapRegionTile tile = cache.getLoadedRegion(rx, rz);
                if (tile == null || !tile.isChunkPopulated(cx & 31, cz & 31)) {
                    enqueueDirtyChunk(cx, cz);
                }
            }
        }
    }

    private static void processDirtyChunks(Minecraft mc, RegionCacheManager cache, int pcx,
        int pcz, boolean biomeColors, String dim) {
        long start = System.nanoTime();
        ClientLevel level = mc.level;
        boolean nether = dim != null && dim.endsWith("the_nether");
        int scanned = 0;

        while (scanned < MAX_CHUNKS_PER_TICK) {
            if (scanned > 0 && (System.nanoTime() - start) >= TICK_SCAN_BUDGET_NANOS) break;
            Long nextKey = pollClosestDirtyChunk(pcx, pcz);
            if (nextKey == null) break;
            int cx = (int) (nextKey.longValue() >> 32);
            int cz = (int) nextKey.longValue();
            LevelChunk chunk = getLoadedChunk(level, cx, cz);
            if (chunk == null) continue;
            scanAndIngestChunk(mc, level, chunk, cx, cz, cache, biomeColors, nether);
            scanned++;
        }
    }

    private static Long pollClosestDirtyChunk(int pcx, int pcz) {
        synchronized (DIRTY_CHUNK_SET) {
            int size = DIRTY_CHUNK_LIST.size();
            if (size == 0) return null;
            int bestIdx = 0;
            int bestDist = Integer.MAX_VALUE;
            int scanLimit = Math.min(size, 64);
            for (int i = 0; i < scanLimit; i++) {
                long k = DIRTY_CHUNK_LIST.get(i).longValue();
                int dx = ((int) (k >> 32)) - pcx;
                int dz = ((int) k) - pcz;
                int d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    bestIdx = i;
                    if (d <= 1) break;
                }
            }
            int lastIdx = size - 1;
            Long chosen = DIRTY_CHUNK_LIST.get(bestIdx);
            if (bestIdx != lastIdx) {
                DIRTY_CHUNK_LIST.set(bestIdx, DIRTY_CHUNK_LIST.get(lastIdx));
            }
            DIRTY_CHUNK_LIST.remove(lastIdx);
            DIRTY_CHUNK_SET.remove(chosen);
            return chosen;
        }
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int cx, int cz) {
        try {
            return level.getChunkSource().getChunk(cx, cz, @@MAP_CHUNK_STATUS@@.FULL, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void scanAndIngestChunk(Minecraft mc, ClientLevel level, LevelChunk chunk, int cx, int cz,
        RegionCacheManager cache, boolean biomeColors, boolean nether) {
        final ResourceManager rm = mc != null ? mc.getResourceManager() : null;
        int resEpoch = BackdropBackend.resourceEpoch;
        if (rm != cachedBlockResourceManager || cachedBlockResourceEpoch != resEpoch) {
            BLOCK_ENTRY_CACHE.clear();
            cachedBlockResourceManager = rm;
            cachedBlockResourceEpoch = resEpoch;
        }
        LevelChunkSection[] sections = chunk.getSections();
        if (sections == null || sections.length == 0) return;

        int minY = @@MAP_MIN_Y@@;
        int maxY = @@MAP_MAX_Y@@;
        int topSection = sections.length - 1;
        while (topSection >= 0 && isSectionEmpty(sections[topSection])) topSection--;
        if (topSection < 0) return;

        int[] baseRgb = SCAN_BASE_RGB;
        short[] heights = SCAN_HEIGHTS;
        byte[] lights = SCAN_LIGHTS;
        Arrays.fill(baseRgb, 0);
        Arrays.fill(heights, MapColorShading.UNEXPLORED_HEIGHT);
        Arrays.fill(lights, (byte) 0);

        int bx0 = cx << 4;
        int bz0 = cz << 4;
        BlockPos.MutableBlockPos pos = SCAN_POS;
        BlockPos.MutableBlockPos abovePos = SCAN_ABOVE_POS;
        int scanTopY = nether ? Math.min(122, minY + (topSection + 1) * 16 - 1)
                              : Math.min(maxY - 1, minY + (topSection + 1) * 16 - 1);

        for (int lz = 0; lz < 16; lz++) {
            int wz = bz0 + lz;
            int row = lz << 4;
            for (int lx = 0; lx < 16; lx++) {
                int wx = bx0 + lx;
                int startY = scanTopY;
                if (nether) {
                    boolean sawAir = false;
                    for (int y = startY; y >= minY; y--) {
                        int sIdx = (y - minY) >> 4;
                        if (sIdx < 0 || sIdx >= sections.length) continue;
                        LevelChunkSection sec = sections[sIdx];
                        if (isSectionEmpty(sec)) {
                            startY = y;
                            sawAir = true;
                            break;
                        }
                        BlockState st = sec.getBlockState(lx, y & 15, lz);
                        if (st.isAir()) {
                            startY = y;
                            sawAir = true;
                            break;
                        }
                    }
                    if (!sawAir) startY = Math.min(96, scanTopY);
                }

                int waterDepth = 0;
                int waterTopY = Integer.MIN_VALUE;
                int overlayRgb = 0;
                float overlayAlpha = 0f;
                int groundRgb = 0;
                int surfaceY = Integer.MIN_VALUE;

                for (int y = startY; y >= minY; ) {
                    int sIdx = (y - minY) >> 4;
                    if (sIdx < 0 || sIdx >= sections.length) {
                        y -= 16;
                        continue;
                    }
                    LevelChunkSection sec = sections[sIdx];
                    if (isSectionEmpty(sec)) {
                        y = minY + (sIdx << 4) - 1;
                        continue;
                    }
                    int secBottom = minY + (sIdx << 4);
                    for (; y >= secBottom; y--) {
                        BlockState state = sec.getBlockState(lx, y & 15, lz);
                        if (state.isAir()) continue;
                        FluidState fluid = state.getFluidState();
                        Fluid ft = fluid.getType();
                        if (ft == Fluids.WATER || ft == Fluids.FLOWING_WATER) {
                            if (waterDepth == 0) waterTopY = y;
                            waterDepth++;
                            continue;
                        }
                        if (ft == Fluids.LAVA || ft == Fluids.FLOWING_LAVA) {
                            groundRgb = 0xE85A19;
                            surfaceY = y;
                            break;
                        }
                        pos.set(wx, y, wz);
                        int mapCol = blockMapColorRgb(level, state, pos);
                        int mapId = blockMapColorId(level, state, pos);
                        if (mapCol == 0 && mapId == 0) continue;

                        MapBlockPalette.BlockEntry entry = resolveBlockEntry(rm, state.getBlock(), mapId);
                        if (entry.kind == MapBlockPalette.KIND_SKIP) continue;

                        int grassTint = 0;
                        int foliageTint = 0;
                        if (biomeColors) {
                            if (entry.tintMode == MapBlockPalette.TINT_GRASS) {
                                grassTint = BiomeColors.getAverageGrassColor(level, pos) & 0xFFFFFF;
                            } else if (entry.tintMode == MapBlockPalette.TINT_FOLIAGE) {
                                foliageTint = BiomeColors.getAverageFoliageColor(level, pos) & 0xFFFFFF;
                            }
                        }
                        int col = MapBlockPalette.resolveColor(entry, mapCol, biomeColors, grassTint, foliageTint);

                        if (entry.kind == MapBlockPalette.KIND_FLAT_OVERLAY) {
                            if (overlayAlpha < 0.05f) {
                                overlayRgb = col;
                                overlayAlpha = entry.overlayAlpha;
                            }
                            continue;
                        }
                        if (entry.kind == MapBlockPalette.KIND_TRANSLUCENT && waterDepth == 0 && overlayAlpha < 0.05f) {
                            overlayRgb = col;
                            overlayAlpha = entry.overlayAlpha;
                            if (waterTopY == Integer.MIN_VALUE) waterTopY = y;
                            continue;
                        }

                        groundRgb = col;
                        surfaceY = y;
                        break;
                    }
                    if (surfaceY != Integer.MIN_VALUE) break;
                }

                if (surfaceY == Integer.MIN_VALUE && waterDepth == 0) continue;
                int finalY = waterDepth > 0 ? waterTopY : surfaceY;
                if (finalY == Integer.MIN_VALUE) continue;
                if (surfaceY == Integer.MIN_VALUE) groundRgb = 0x1D3A75;

                int waterRgb = 0x3F76E4;
                if (waterDepth > 0 && biomeColors) {
                    pos.set(wx, finalY, wz);
                    waterRgb = BiomeColors.getAverageWaterColor(level, pos) & 0xFFFFFF;
                }

                baseRgb[row + lx] = MapColorShading.composeSurfaceRgb(
                    groundRgb, waterDepth, waterRgb, overlayRgb, overlayAlpha);
                heights[row + lx] = (short) finalY;

                int lightY = Math.min(maxY - 1, finalY + 1);
                abovePos.set(wx, lightY, wz);
                int bl = 0;
                try {
                    bl = level.getBrightness(LightLayer.BLOCK, abovePos);
                    if (!nether && bl > 0) {
                        int sl = level.getBrightness(LightLayer.SKY, abovePos);
                        if (sl >= 13) bl = Math.max(0, bl - 5);
                    }
                } catch (Throwable ignored) {}
                lights[row + lx] = (byte) Math.max(0, Math.min(15, bl));
            }
        }

        cache.ingestChunk(cx, cz, baseRgb, heights, lights);
    }

    private static boolean isSectionEmpty(LevelChunkSection section) {
        return @@MAP_SECTION_EMPTY@@;
    }

    private static int blockMapColorRgb(ClientLevel level, BlockState state, BlockPos pos) {
        return state.getMapColor(level, pos).col & 0xFFFFFF;
    }

    private static int blockMapColorId(ClientLevel level, BlockState state, BlockPos pos) {
        return state.getMapColor(level, pos).id;
    }

    private static MapBlockPalette.BlockEntry resolveBlockEntry(
        final ResourceManager rm, Block block, int mapId) {
        MapBlockPalette.BlockEntry cached = BLOCK_ENTRY_CACHE.get(block);
        if (cached != null) return cached;
        String id = "";
        try {
            Object key = @@MAP_BLOCK_REGISTRY@@.getKey(block);
            if (key != null) id = key.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}
        MapBlockPalette.BlockEntry entry = MapBlockPalette.resolve(id, mapId,
            new MapBlockPalette.TextureReader() {
                @Override
                public byte[] loadResourceBytes(String resourceId) {
                    return readResourceBytes(rm, resourceId);
                }
            });
        if (BLOCK_ENTRY_CACHE.size() < 4096) BLOCK_ENTRY_CACHE.put(block, entry);
        return entry;
    }

    public static MapRadarModels.RadarSnapshot radarSnapshot(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientRuntime runtime = ClientRuntime.get();
        if (mc.level == null || mc.player == null || runtime == null) {
            if (cachedRadarLevel != null) resetRadarCache();
            return MapRadarModels.RadarSnapshot.EMPTY;
        }
        float pt = mc.isPaused() || Float.isNaN(partialTick) ? 0f
                                                             : Math.max(0f, Math.min(1f, partialTick));
        Vec3 selfPos = mc.player.getPosition(pt);
        float selfYaw = entityYaw(mc.player, pt);

        Settings settings = runtime.settings();
        Settings.Minimap mm = settings != null ? settings.minimap : null;
        boolean worldMapOpen = !FeaturePolicy.isBlocked("worldMap") && runtime.worldMap() != null && runtime.worldMap().isOpen();
        boolean itemsAllowed = worldMapOpen ? !FeaturePolicy.isBlocked("worldMap.items") : !FeaturePolicy.isBlocked("minimap.items");
        boolean mobsAllowed = worldMapOpen ? !FeaturePolicy.isBlocked("worldMap.mobs") : !FeaturePolicy.isBlocked("minimap.mobs");
        boolean explosivesAllowed = worldMapOpen ? !FeaturePolicy.isBlocked("worldMap.mobs") : !FeaturePolicy.isBlocked("minimap.explosives");
        boolean playersAllowed = worldMapOpen ? !FeaturePolicy.isBlocked("worldMap.players") : !FeaturePolicy.isBlocked("minimap.players");
        boolean invisAllowed = playersAllowed && (worldMapOpen ? !FeaturePolicy.isBlocked("worldMap.invisibles") : !FeaturePolicy.isBlocked("minimap.invisibles"));
        boolean wantItems = itemsAllowed && (worldMapOpen || mm == null || mm.showItems);
        boolean onlyValuable = !worldMapOpen && mm != null && mm.onlyValuableItems;
        boolean wantExplosives = explosivesAllowed && (worldMapOpen || mm == null || mm.showExplosives);
        boolean wantHostile = mobsAllowed && (worldMapOpen || mm == null || mm.showHostileMobs);
        boolean wantNeutral = mobsAllowed && (worldMapOpen || mm == null || mm.showNeutralMobs);
        boolean wantPassive = mobsAllowed && (worldMapOpen || mm == null || mm.showPassiveMobs);
        boolean wantOther = mobsAllowed && (worldMapOpen || mm == null || mm.showOtherEntities);
        boolean wantMobNames = mobsAllowed && (worldMapOpen || mm == null || mm.showMobNames);
        boolean wantPlayerHeads = playersAllowed && (worldMapOpen || mm == null || mm.showPlayerHeads);
        boolean wantPlayers = playersAllowed && (worldMapOpen || mm == null || mm.showPlayers);
        float minZoom = mm != null
            ? Math.max(0.35f, Math.min(mm.zoom, mm.zoom * Math.max(0.35f, mm.enlargeZoom)))
            : 0.35f;

        int cfgHash = (worldMapOpen ? 1 : 0)
            | (wantItems ? 2 : 0)
            | (onlyValuable ? 4 : 0)
            | (wantExplosives ? 8 : 0)
            | (wantHostile ? 16 : 0)
            | (wantNeutral ? 32 : 0)
            | (wantPassive ? 64 : 0)
            | (wantOther ? 128 : 0)
            | (wantMobNames ? 256 : 0)
            | (wantPlayerHeads ? 512 : 0)
            | (wantPlayers ? 1024 : 0)
            | (invisAllowed ? 2048 : 0);
        cfgHash = cfgHash * 31 + Float.floatToRawIntBits(minZoom);
        cfgHash = cfgHash * 31 + (int) FeaturePolicy.revision();

        int curTick = mc.player.tickCount;
        int resEpoch = BackdropBackend.resourceEpoch;
        if (cachedRadarLevel != mc.level
            || cachedRadarPlayer != mc.player
            || cachedRadarTick != curTick
            || cachedRadarConfigHash != cfgHash
            || cachedRadarResourceEpoch != resEpoch) {
            refreshRadarTickCache(mc, runtime, worldMapOpen, wantPlayers, invisAllowed, wantPlayerHeads,
                wantItems, onlyValuable, wantExplosives, wantHostile, wantNeutral, wantPassive,
                wantOther, wantMobNames, minZoom, curTick, cfgHash, resEpoch);
        }

        if (lastSnapshot != null
            && lastSelfX == selfPos.x && lastSelfY == selfPos.y && lastSelfZ == selfPos.z
            && lastSelfYaw == selfYaw
            && (Float.compare(lastPartialTick, pt) == 0
                || (!anyOtherPlayerMoving && !anyCachedItemMoving && !anyCachedMobMoving))) {
            return lastSnapshot;
        }

        List<MapRadarModels.PlayerEntry> players;
        int playerCount = CACHED_PLAYERS.size();
        if (playerCount == 0) {
            players = Collections.emptyList();
        } else if (playerCount == 1 && CACHED_PLAYERS.get(0).self) {
            players = Collections.singletonList(
                CACHED_PLAYERS.get(0).entryAt(selfPos.x, selfPos.y, selfPos.z, selfYaw));
        } else {
            players = new ArrayList<MapRadarModels.PlayerEntry>(playerCount);
            for (int i = 0; i < playerCount; i++) {
                CachedPlayer cp = CACHED_PLAYERS.get(i);
                if (cp.self) {
                    players.add(cp.entryAt(selfPos.x, selfPos.y, selfPos.z, selfYaw));
                } else if (!cp.moving) {
                    players.add(cp.staticEntry);
                } else {
                    players.add(cp.entryAt(
                        cp.x0 + cp.dx * pt, cp.y0 + cp.dy * pt, cp.z0 + cp.dz * pt,
                        cp.yaw0 + cp.dYaw * pt));
                }
            }
        }

        List<MapRadarModels.ItemEntry> items;
        if (!anyCachedItemMoving) {
            items = staticItems;
        } else {
            int itemCount = CACHED_ITEMS.size();
            items = new ArrayList<MapRadarModels.ItemEntry>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                CachedItem ci = CACHED_ITEMS.get(i);
                if (!ci.moving) {
                    items.add(ci.staticEntry);
                } else {
                    items.add(ci.entryAt(pt));
                }
            }
        }

        List<MapRadarModels.MobEntry> mobs;
        if (!anyCachedMobMoving) {
            mobs = staticMobs;
        } else {
            int mobCount = CACHED_MOBS.size();
            mobs = new ArrayList<MapRadarModels.MobEntry>(mobCount);
            for (int i = 0; i < mobCount; i++) {
                CachedMob cm = CACHED_MOBS.get(i);
                if (!cm.moving) {
                    mobs.add(cm.staticEntry);
                } else {
                    mobs.add(cm.entryAt(pt));
                }
            }
        }

        MapRadarModels.RadarSnapshot snap = new MapRadarModels.RadarSnapshot(true,
            selfPos.x, selfPos.y, selfPos.z, selfYaw,
            cachedDim, "", cachedWorldId, cachedWorldLabel, players, items, mobs);
        lastSnapshot = snap;
        lastSelfX = selfPos.x;
        lastSelfY = selfPos.y;
        lastSelfZ = selfPos.z;
        lastSelfYaw = selfYaw;
        lastPartialTick = pt;
        return snap;
    }

    private static void refreshRadarTickCache(Minecraft mc, ClientRuntime runtime,
        boolean worldMapOpen, boolean wantPlayers, boolean invisAllowed, boolean wantPlayerHeads, boolean wantItems,
        boolean onlyValuable, boolean wantExplosives, boolean wantHostile, boolean wantNeutral,
        boolean wantPassive, boolean wantOther, boolean wantMobNames, float minZoom,
        int curTick, int cfgHash, int resEpoch) {
        cachedDim = WorldFeatureBridge.currentDimension();
        cachedWorldId = runtime.currentWorldId();
        cachedWorldLabel = runtime.currentWorldLabel();

        CACHED_PLAYERS.clear();
        anyOtherPlayerMoving = false;
        List<? extends Player> mcPlayers = mc.level.players();
        for (int i = 0; i < mcPlayers.size(); i++) {
            Player p = mcPlayers.get(i);
            if (p == null || !p.isAlive() || p.isSpectator()) continue;
            boolean isSelf = (p == mc.player);
            if (!isSelf && !wantPlayers) continue;
            if (!isSelf && p.isInvisible() && !invisAllowed) continue;
            String skinId = "";
            if ((wantPlayerHeads || isSelf) && p instanceof AbstractClientPlayer) {
                try {
                    skinId = @@MAP_PLAYER_SKIN@@;
                } catch (Throwable ignored) {}
            }
            String uuidStr = cachedUuidString(p.getUUID());
            String name = p.getName() == null ? "" : p.getName().getString();
            boolean invisible = p.isInvisible();
            boolean crouching = p.isCrouching();
            float health = p.getHealth();
            float maxHealth = Math.max(1f, p.getMaxHealth());
            if (isSelf) {
                CACHED_PLAYERS.add(new CachedPlayer(uuidStr, name, skinId,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0f, 0f,
                    invisible, crouching, true, health, maxHealth, false, null));
            } else {
                Vec3 pos0 = p.getPosition(0f);
                Vec3 pos1 = p.getPosition(1f);
                float yaw0 = entityYaw(p, 0f);
                float yaw1 = entityYaw(p, 1f);
                double dx = pos1.x - pos0.x;
                double dy = pos1.y - pos0.y;
                double dz = pos1.z - pos0.z;
                float dYaw = yaw1 - yaw0;
                boolean moving = dx != 0.0 || dy != 0.0 || dz != 0.0 || dYaw != 0f;
                if (moving) anyOtherPlayerMoving = true;
                MapRadarModels.PlayerEntry staticEntry = moving ? null
                    : new MapRadarModels.PlayerEntry(uuidStr, name, skinId,
                        pos0.x, pos0.y, pos0.z, yaw0, invisible, crouching, false, health, maxHealth);
                CACHED_PLAYERS.add(new CachedPlayer(uuidStr, name, skinId,
                    pos0.x, pos0.y, pos0.z, dx, dy, dz, yaw0, dYaw,
                    invisible, crouching, false, health, maxHealth, moving, staticEntry));
            }
        }

        CACHED_ITEMS.clear();
        CACHED_MOBS.clear();
        anyCachedItemMoving = false;
        anyCachedMobMoving = false;
        boolean wantAnyMob = wantExplosives || wantHostile || wantNeutral || wantPassive || wantOther;

        if (!wantItems && !wantAnyMob) {
            if (hasRadarItemSlots) {
                Arrays.fill(radarItemCache, null);
                hasRadarItemSlots = false;
            }
            staticItems = Collections.emptyList();
            staticMobs = Collections.emptyList();
        } else {
            SCRATCH_CLUSTERS.clear();
            SCRATCH_MOBS.clear();
            SCRATCH_ORB_CLUSTERS.clear();
            SCRATCH_ARROW_CLUSTERS.clear();

            Vec3 selfPos1 = mc.player.getPosition(1f);
            double maxDist = (112.0 / minZoom) * 0.78 + 16.0;
            double maxDistSq = maxDist * maxDist;

            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == null || !e.isAlive() || e instanceof Player) continue;
                Vec3 pos1 = e.getPosition(1f);
                if (!worldMapOpen) {
                    double rdx = pos1.x - selfPos1.x;
                    double rdz = pos1.z - selfPos1.z;
                    if (rdx * rdx + rdz * rdz > maxDistSq) continue;
                }

                if (e instanceof ItemEntity) {
                    if (!wantItems) continue;
                    ItemEntity ie = (ItemEntity) e;
                    ItemStack stack = ie.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    String itemId = itemRegistryId(stack);
                    boolean valuable = isValuableItemById(stack.getItem(), itemId) || stack.isEnchanted();
                    String name = "";
                    if (worldMapOpen || !valuable) {
                        name = stack.getHoverName().getString();
                        if (!valuable) valuable = isValuableDisplayName(name);
                    }
                    if (onlyValuable && !valuable) continue;
                    Vec3 pos0 = ie.getPosition(0f);
                    boolean merged = false;
                    for (int c = 0; c < SCRATCH_CLUSTERS.size(); c++) {
                        ItemCluster cl = SCRATCH_CLUSTERS.get(c);
                        if (cl.itemId.equals(itemId) && cl.valuable == valuable) {
                            double cdx = cl.x1 - pos1.x, cdz = cl.z1 - pos1.z;
                            if (cdx * cdx + cdz * cdz <= 9.0) {
                                cl.totalCount += stack.getCount();
                                cl.mergedStacks++;
                                double inv = 1.0 / cl.mergedStacks;
                                cl.x0 += (pos0.x - cl.x0) * inv;
                                cl.y0 += (pos0.y - cl.y0) * inv;
                                cl.z0 += (pos0.z - cl.z0) * inv;
                                cl.x1 += (pos1.x - cl.x1) * inv;
                                cl.y1 += (pos1.y - cl.y1) * inv;
                                cl.z1 += (pos1.z - cl.z1) * inv;
                                merged = true;
                                break;
                            }
                        }
                    }
                    if (!merged && SCRATCH_CLUSTERS.size() < 128) {
                        SCRATCH_CLUSTERS.add(new ItemCluster(ie.getId(), stack, itemId, name,
                            stack.getCount(), pos0.x, pos0.y, pos0.z, pos1.x, pos1.y, pos1.z,
                            valuable));
                    }
                } else if (e instanceof PrimedTnt) {
                    if (!wantExplosives) continue;
                    Vec3 pos0 = e.getPosition(0f);
                    SCRATCH_MOBS.add(buildCachedMob(mc, e.getId(), MapRadarModels.MOB_TNT,
                        "minecraft:tnt", "TNT", false, pos0.x, pos0.y, pos0.z,
                        pos1.x, pos1.y, pos1.z, 0f, 0f, ""));
                } else if (e instanceof EndCrystal) {
                    if (!wantExplosives) continue;
                    Vec3 pos0 = e.getPosition(0f);
                    SCRATCH_MOBS.add(buildCachedMob(mc, e.getId(), MapRadarModels.MOB_CRYSTAL,
                        "minecraft:end_crystal", "Crystal", false, pos0.x, pos0.y, pos0.z,
                        pos1.x, pos1.y, pos1.z, 0f, 0f, ""));
                } else if (e instanceof ExperienceOrb) {
                    if (!wantOther) continue;
                    Vec3 pos0 = e.getPosition(0f);
                    mergeEntityCluster(SCRATCH_ORB_CLUSTERS, e.getId(),
                        "minecraft:experience_orb", pos0, pos1, 2.25, 64);
                } else if (wantAnyMob) {
                    String typeId = entityTypeId(e);
                    if (!isRadarEligibleEntity(e, typeId)) continue;
                    if ("minecraft:arrow".equals(typeId) || "minecraft:spectral_arrow".equals(typeId)) {
                        if (!wantOther) continue;
                        Vec3 pos0 = e.getPosition(0f);
                        mergeEntityCluster(SCRATCH_ARROW_CLUSTERS, e.getId(), typeId,
                            pos0, pos1, 1.0, 48);
                        continue;
                    }
                    int kind = cachedEntityKind(e, typeId);
                    if (!worldMapOpen && !isKindWanted(kind, wantExplosives, wantHostile,
                        wantNeutral, wantPassive, wantOther)) {
                        continue;
                    }
                    String eName = (wantMobNames || kind == MapRadarModels.MOB_BOSS)
                        ? (e.getName() == null ? "" : e.getName().getString())
                        : "";
                    boolean baby = (e instanceof LivingEntity) && ((LivingEntity) e).isBaby();
                    String itemOverride = dynamicEntityItemIcon(e);
                    Vec3 pos0 = e.getPosition(0f);
                    float yaw0 = entityYaw(e, 0f);
                    float yaw1 = entityYaw(e, 1f);
                    SCRATCH_MOBS.add(buildCachedMob(mc, e.getId(), kind, typeId, eName, baby,
                        pos0.x, pos0.y, pos0.z, pos1.x, pos1.y, pos1.z, yaw0, yaw1, itemOverride));
                }
            }

            for (int i = 0; i < SCRATCH_ORB_CLUSTERS.size(); i++) {
                EntityCluster cl = SCRATCH_ORB_CLUSTERS.get(i);
                SCRATCH_MOBS.add(buildCachedMob(mc, cl.entityId, MapRadarModels.MOB_OTHER,
                    cl.typeId, "XP", false, cl.x0, cl.y0, cl.z0, cl.x1, cl.y1, cl.z1, 0f, 0f, ""));
            }
            for (int i = 0; i < SCRATCH_ARROW_CLUSTERS.size(); i++) {
                EntityCluster cl = SCRATCH_ARROW_CLUSTERS.get(i);
                SCRATCH_MOBS.add(buildCachedMob(mc, cl.entityId, MapRadarModels.MOB_OTHER,
                    cl.typeId, "Arrow", false, cl.x0, cl.y0, cl.z0, cl.x1, cl.y1, cl.z1, 0f, 0f, ""));
            }

            sortRefX = selfPos1.x;
            sortRefZ = selfPos1.z;
            if (SCRATCH_MOBS.size() > 256) {
                Collections.sort(SCRATCH_MOBS, MOB_DIST_CMP);
                SCRATCH_MOBS.subList(256, SCRATCH_MOBS.size()).clear();
            }
            if (SCRATCH_MOBS.size() > 1) {
                Collections.sort(SCRATCH_MOBS, MOB_PRIORITY_DESC_CMP);
            }
            int mobCount = SCRATCH_MOBS.size();
            for (int i = 0; i < mobCount; i++) {
                CachedMob cm = SCRATCH_MOBS.get(i);
                if (cm.moving) anyCachedMobMoving = true;
                CACHED_MOBS.add(cm);
            }
            if (mobCount == 0) {
                staticMobs = Collections.emptyList();
            } else if (!anyCachedMobMoving) {
                List<MapRadarModels.MobEntry> list = new ArrayList<MapRadarModels.MobEntry>(mobCount);
                for (int i = 0; i < mobCount; i++) list.add(CACHED_MOBS.get(i).staticEntry);
                staticMobs = list;
            } else {
                staticMobs = Collections.emptyList();
            }

            if (SCRATCH_CLUSTERS.size() > 1) {
                Collections.sort(SCRATCH_CLUSTERS, ITEM_CLUSTER_CMP);
            }
            int totalItems = Math.min(128, SCRATCH_CLUSTERS.size());
            if (totalItems == 0) {
                if (hasRadarItemSlots) {
                    Arrays.fill(radarItemCache, null);
                    hasRadarItemSlots = false;
                }
                staticItems = Collections.emptyList();
            } else {
                hasRadarItemSlots = true;
                for (int i = 0; i < RADAR_ITEM_SLOTS; i++) {
                    if (i < totalItems) {
                        ItemCluster cl = SCRATCH_CLUSTERS.get(i);
                        radarItemCache[i] = cl.stack;
                        CachedItem ci = buildCachedItem(cl, RADAR_SLOT_BASE + i);
                        if (ci.moving) anyCachedItemMoving = true;
                        CACHED_ITEMS.add(ci);
                    } else {
                        radarItemCache[i] = null;
                    }
                }
                for (int i = RADAR_ITEM_SLOTS; i < totalItems; i++) {
                    ItemCluster cl = SCRATCH_CLUSTERS.get(i);
                    CachedItem ci = buildCachedItem(cl, -1);
                    if (ci.moving) anyCachedItemMoving = true;
                    CACHED_ITEMS.add(ci);
                }
                if (!anyCachedItemMoving) {
                    List<MapRadarModels.ItemEntry> list =
                        new ArrayList<MapRadarModels.ItemEntry>(totalItems);
                    for (int i = 0; i < totalItems; i++) list.add(CACHED_ITEMS.get(i).staticEntry);
                    staticItems = list;
                } else {
                    staticItems = Collections.emptyList();
                }
            }

            SCRATCH_CLUSTERS.clear();
            SCRATCH_MOBS.clear();
            SCRATCH_ORB_CLUSTERS.clear();
            SCRATCH_ARROW_CLUSTERS.clear();
        }

        cachedRadarLevel = mc.level;
        cachedRadarPlayer = mc.player;
        cachedRadarTick = curTick;
        cachedRadarConfigHash = cfgHash;
        cachedRadarResourceEpoch = resEpoch;
        lastSnapshot = null;
        lastPartialTick = Float.NaN;
    }

    private static String cachedUuidString(UUID uuid) {
        if (uuid == null) return "";
        String s = PLAYER_UUID_CACHE.get(uuid);
        if (s != null) return s;
        s = uuid.toString();
        if (PLAYER_UUID_CACHE.size() >= 256) PLAYER_UUID_CACHE.clear();
        PLAYER_UUID_CACHE.put(uuid, s);
        return s;
    }

    private static boolean isKindWanted(int kind, boolean wantExplosives, boolean wantHostile,
        boolean wantNeutral, boolean wantPassive, boolean wantOther) {
        switch (kind) {
            case MapRadarModels.MOB_TNT:
            case MapRadarModels.MOB_CRYSTAL:
                return wantExplosives;
            case MapRadarModels.MOB_BOSS:
            case MapRadarModels.MOB_HOSTILE:
                return wantHostile;
            case MapRadarModels.MOB_NEUTRAL:
                return wantNeutral;
            case MapRadarModels.MOB_PASSIVE:
                return wantPassive;
            default:
                return wantOther;
        }
    }

    private static CachedItem buildCachedItem(ItemCluster cl, int cacheSlot) {
        double dx = cl.x1 - cl.x0;
        double dy = cl.y1 - cl.y0;
        double dz = cl.z1 - cl.z0;
        boolean moving = dx != 0.0 || dy != 0.0 || dz != 0.0;
        MapRadarModels.ItemEntry staticEntry = moving ? null
            : new MapRadarModels.ItemEntry(cl.entityId, cacheSlot, cl.itemId, cl.displayName,
                cl.totalCount, cl.mergedStacks, cl.x0, cl.y0, cl.z0, cl.valuable);
        return new CachedItem(cl.entityId, cacheSlot, cl.itemId, cl.displayName, cl.totalCount,
            cl.mergedStacks, cl.x0, cl.y0, cl.z0, dx, dy, dz, cl.valuable, moving, staticEntry);
    }

    private static CachedMob buildCachedMob(Minecraft mc, int entityId, int kind, String typeId,
        String name, boolean baby, double x0, double y0, double z0, double x1, double y1,
        double z1, float yaw0, float yaw1, String itemOverride) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        float dYaw = yaw1 - yaw0;
        boolean moving = dx != 0.0 || dy != 0.0 || dz != 0.0 || dYaw != 0f;
        String itemIconId;
        String composedKey;
        int[] composedPixels;
        int composedSize;
        long composedRev;
        if (itemOverride != null && !itemOverride.isEmpty()) {
            itemIconId = itemOverride;
            composedKey = "";
            composedPixels = null;
            composedSize = 0;
            composedRev = 0L;
        } else {
            MapEntityIconComposer.ComposedIcon icon = resolveEntityIcon(mc, typeId, kind);
            itemIconId = icon.itemIconId;
            composedKey = icon.cacheKey;
            composedPixels = icon.pixels;
            composedSize = icon.size;
            composedRev = entityIconRevision;
        }
        MapRadarModels.MobEntry staticEntry = moving ? null
            : new MapRadarModels.MobEntry(entityId, kind, typeId, name, baby, x0, y0, z0, yaw0,
                "", 0f, 0f, 0f, 0f, itemIconId, null, 0xFFFFFF,
                composedKey, composedPixels, composedSize, composedRev);
        return new CachedMob(entityId, kind, typeId, name, baby, x0, y0, z0, x1, z1, dx, dy, dz,
            yaw0, dYaw, itemIconId, composedKey, composedPixels, composedSize, composedRev,
            moving, staticEntry);
    }

    private static final class CachedPlayer {
        final String uuid, name, skinId;
        final double x0, y0, z0, dx, dy, dz;
        final float yaw0, dYaw;
        final boolean invisible, sneaking, self, moving;
        final float health, maxHealth;
        final MapRadarModels.PlayerEntry staticEntry;

        CachedPlayer(String uuid, String name, String skinId, double x0, double y0, double z0,
            double dx, double dy, double dz, float yaw0, float dYaw, boolean invisible,
            boolean sneaking, boolean self, float health, float maxHealth, boolean moving,
            MapRadarModels.PlayerEntry staticEntry) {
            this.uuid = uuid;
            this.name = name;
            this.skinId = skinId;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.yaw0 = yaw0;
            this.dYaw = dYaw;
            this.invisible = invisible;
            this.sneaking = sneaking;
            this.self = self;
            this.health = health;
            this.maxHealth = maxHealth;
            this.moving = moving;
            this.staticEntry = staticEntry;
        }

        MapRadarModels.PlayerEntry entryAt(double x, double y, double z, float yaw) {
            return new MapRadarModels.PlayerEntry(uuid, name, skinId, x, y, z, yaw,
                invisible, sneaking, self, health, maxHealth);
        }
    }

    private static final class CachedItem {
        final int entityId, cacheSlot, totalCount, mergedStacks;
        final String itemId, displayName;
        final double x0, y0, z0, dx, dy, dz;
        final boolean valuable, moving;
        final MapRadarModels.ItemEntry staticEntry;

        CachedItem(int entityId, int cacheSlot, String itemId, String displayName, int totalCount,
            int mergedStacks, double x0, double y0, double z0, double dx, double dy, double dz,
            boolean valuable, boolean moving, MapRadarModels.ItemEntry staticEntry) {
            this.entityId = entityId;
            this.cacheSlot = cacheSlot;
            this.itemId = itemId;
            this.displayName = displayName;
            this.totalCount = totalCount;
            this.mergedStacks = mergedStacks;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.valuable = valuable;
            this.moving = moving;
            this.staticEntry = staticEntry;
        }

        MapRadarModels.ItemEntry entryAt(float pt) {
            return new MapRadarModels.ItemEntry(entityId, cacheSlot, itemId, displayName,
                totalCount, mergedStacks, x0 + dx * pt, y0 + dy * pt, z0 + dz * pt, valuable);
        }
    }

    private static final class CachedMob {
        final int entityId, kind, composedSize;
        final String typeId, name, itemIconId, composedKey;
        final int[] composedPixels;
        final long composedRev;
        final boolean baby, moving;
        final double x0, y0, z0, x1, z1, dx, dy, dz;
        final float yaw0, dYaw;
        final MapRadarModels.MobEntry staticEntry;

        CachedMob(int entityId, int kind, String typeId, String name, boolean baby,
            double x0, double y0, double z0, double x1, double z1, double dx, double dy, double dz,
            float yaw0, float dYaw, String itemIconId, String composedKey, int[] composedPixels,
            int composedSize, long composedRev, boolean moving,
            MapRadarModels.MobEntry staticEntry) {
            this.entityId = entityId;
            this.kind = kind;
            this.typeId = typeId;
            this.name = name;
            this.baby = baby;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.yaw0 = yaw0;
            this.dYaw = dYaw;
            this.itemIconId = itemIconId;
            this.composedKey = composedKey;
            this.composedPixels = composedPixels;
            this.composedSize = composedSize;
            this.composedRev = composedRev;
            this.moving = moving;
            this.staticEntry = staticEntry;
        }

        MapRadarModels.MobEntry entryAt(float pt) {
            return new MapRadarModels.MobEntry(entityId, kind, typeId, name, baby,
                x0 + dx * pt, y0 + dy * pt, z0 + dz * pt, yaw0 + dYaw * pt,
                "", 0f, 0f, 0f, 0f, itemIconId, null, 0xFFFFFF,
                composedKey, composedPixels, composedSize, composedRev);
        }
    }

    private static final class ItemCluster {
        final int entityId;
        final ItemStack stack;
        final String itemId;
        final String displayName;
        int totalCount;
        int mergedStacks = 1;
        double x0, y0, z0, x1, y1, z1;
        final boolean valuable;

        ItemCluster(int entityId, ItemStack stack, String itemId, String displayName,
            int totalCount, double x0, double y0, double z0, double x1, double y1, double z1,
            boolean valuable) {
            this.entityId = entityId;
            this.stack = stack;
            this.itemId = itemId;
            this.displayName = displayName;
            this.totalCount = totalCount;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.valuable = valuable;
        }
    }

    private static boolean isValuableItemById(net.minecraft.world.item.Item item, String id) {
        if (item == null || id == null) return false;
        Boolean cached = ITEM_VALUABLE_ID_CACHE.get(item);
        if (cached != null) return cached.booleanValue();
        boolean val = id.contains("totem_of_undying") || id.contains("golden_apple")
            || id.contains("netherite_") || id.contains("shulker_box") || id.contains("elytra")
            || id.contains("end_crystal") || id.contains("respawn_anchor")
            || id.contains("trident") || id.contains("mace") || id.contains("beacon")
            || id.contains("nether_star") || id.contains("dragon_egg")
            || id.contains("player_head") || id.contains("diamond_sword")
            || id.contains("diamond_chestplate") || id.contains("diamond_helmet")
            || id.contains("diamond_leggings") || id.contains("diamond_boots")
            || id.contains("ender_pearl") || id.contains("experience_bottle")
            || id.contains("chorus_fruit") || id.contains("tipped_arrow")
            || id.contains("potion");
        if (ITEM_VALUABLE_ID_CACHE.size() < 2048) {
            ITEM_VALUABLE_ID_CACHE.put(item, val ? Boolean.TRUE : Boolean.FALSE);
        }
        return val;
    }

    private static boolean isValuableDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) return false;
        String lower = displayName.toLowerCase(Locale.ROOT);
        return lower.contains("талисман") || lower.contains("сфера") || lower.contains("трапк")
            || lower.contains("стан") || lower.contains("ком снега")
            || lower.contains("прощальный гул") || lower.contains("talisman")
            || lower.contains("sphere");
    }

    private static float entityYaw(Entity e, float pt) {
        return @@MAP_ENTITY_YAW@@;
    }

    private static String entityTypeId(Entity e) {
        EntityType<?> type = e.getType();
        String cached = ENTITY_TYPE_ID_CACHE.get(type);
        if (cached != null) return cached;
        try {
            Object key = EntityType.getKey(type);
            String id = key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
            if (ENTITY_TYPE_ID_CACHE.size() < 512) ENTITY_TYPE_ID_CACHE.put(type, id);
            return id;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static final class EntityCluster {
        final int entityId;
        final String typeId;
        int count = 1;
        double x0, y0, z0, x1, y1, z1;

        EntityCluster(int entityId, String typeId, double x0, double y0, double z0,
            double x1, double y1, double z1) {
            this.entityId = entityId;
            this.typeId = typeId;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }
    }

    private static void mergeEntityCluster(List<EntityCluster> list, int entityId, String typeId,
        Vec3 pos0, Vec3 pos1, double maxDistSq, int cap) {
        for (int i = 0; i < list.size(); i++) {
            EntityCluster cl = list.get(i);
            if (!cl.typeId.equals(typeId)) continue;
            double dx = cl.x1 - pos1.x, dz = cl.z1 - pos1.z;
            if (dx * dx + dz * dz <= maxDistSq) {
                cl.count++;
                double inv = 1.0 / cl.count;
                cl.x0 += (pos0.x - cl.x0) * inv;
                cl.y0 += (pos0.y - cl.y0) * inv;
                cl.z0 += (pos0.z - cl.z0) * inv;
                cl.x1 += (pos1.x - cl.x1) * inv;
                cl.y1 += (pos1.y - cl.y1) * inv;
                cl.z1 += (pos1.z - cl.z1) * inv;
                return;
            }
        }
        if (list.size() < cap) {
            list.add(new EntityCluster(entityId, typeId, pos0.x, pos0.y, pos0.z,
                pos1.x, pos1.y, pos1.z));
        }
    }

    private static String dynamicEntityItemIcon(Entity e) {
        try {
            if (e instanceof FallingBlockEntity) {
                BlockState bs = ((FallingBlockEntity) e).getBlockState();
                if (bs != null && bs.getBlock() != null) {
                    ItemStack st = new ItemStack(bs.getBlock().asItem());
                    if (!st.isEmpty()) {
                        String id = itemRegistryId(st);
                        if (!"minecraft:air".equals(id)) return id;
                    }
                }
            } else if (e instanceof ItemFrame) {
                ItemStack framed = ((ItemFrame) e).getItem();
                if (framed != null && !framed.isEmpty()) {
                    String id = itemRegistryId(framed);
                    if (!"minecraft:air".equals(id)) return id;
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static MapEntityIconComposer.ComposedIcon resolveEntityIcon(
        final Minecraft mc, String typeId, int kind) {
        final ResourceManager rm = mc.getResourceManager();
        int resEpoch = BackdropBackend.resourceEpoch;
        if (rm != cachedResourceManager || cachedIconResourceEpoch != resEpoch
            || ENTITY_ICON_BY_TYPE.size() > 512) {
            ENTITY_ICON_BY_TYPE.clear();
            cachedResourceManager = rm;
            cachedIconResourceEpoch = resEpoch;
            entityIconRevision++;
        }
        String key = typeId == null ? "" : typeId;
        int slot = (kind >= 0 && kind < 7) ? kind : MapRadarModels.MOB_OTHER;
        MapEntityIconComposer.ComposedIcon[] byKind = ENTITY_ICON_BY_TYPE.get(key);
        if (byKind != null && byKind[slot] != null) return byKind[slot];
        MapEntityIconComposer.ComposedIcon composed = MapEntityIconComposer.compose(key, kind,
            new MapEntityIconComposer.AssetLoader() {
                @Override
                public byte[] loadResourceBytes(String textureId) {
                    return readResourceBytes(rm, textureId);
                }
                @Override
                public boolean hasItem(String itemId) {
                    try {
                        ItemStack stack = GameBridge.stack(itemId);
                        return stack != null && !stack.isEmpty();
                    } catch (Throwable ignored) {
                        return false;
                    }
                }
            });
        if (byKind == null) {
            byKind = new MapEntityIconComposer.ComposedIcon[7];
            ENTITY_ICON_BY_TYPE.put(key, byKind);
        }
        byKind[slot] = composed;
        return composed;
    }

    private static byte[] readResourceBytes(ResourceManager rm, String textureId) {
        if (rm == null || textureId == null || textureId.isEmpty()) return null;
        try {
            Optional<Resource> opt = rm.getResource(GameBridge.identifier(textureId));
            if (opt == null || !opt.isPresent()) return null;
            InputStream in = opt.get().open();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                byte[] buf = new byte[4096];
                int total = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > 524288) return null;
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } finally {
                try { in.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isRadarEligibleEntity(Entity e, String typeId) {
        if (e == null || typeId == null || typeId.isEmpty()) return false;
        if (e instanceof ArmorStand) {
            return !e.isInvisible() && !((ArmorStand) e).isMarker();
        }
        if (e instanceof LivingEntity) {
            return !e.isInvisible();
        }
        if (e instanceof AreaEffectCloud) {
            return ((AreaEffectCloud) e).getRadius() >= 0.45f;
        }
        return !MapEntityTextures.isTechnicalExcludedEntity(typeId);
    }

    private static int cachedEntityKind(Entity e, String typeId) {
        EntityType<?> type = e.getType();
        Integer cached = ENTITY_KIND_CACHE.get(type);
        if (cached != null) return cached.intValue();
        int kind = classifyEntityKind(e, typeId);
        if (ENTITY_KIND_CACHE.size() < 512) ENTITY_KIND_CACHE.put(type, Integer.valueOf(kind));
        return kind;
    }

    private static int classifyEntityKind(Entity e, String typeId) {
        if (typeId.contains("ender_dragon") || typeId.endsWith(":wither")
            || typeId.contains("warden") || typeId.contains("elder_guardian")) {
            return MapRadarModels.MOB_BOSS;
        }
        if (e instanceof ArmorStand || !(e instanceof LivingEntity)) {
            return MapRadarModels.MOB_OTHER;
        }
        if (e instanceof Enemy) {
            return MapRadarModels.MOB_HOSTILE;
        }
        if (typeId.contains("villager") || typeId.contains("wandering_trader")
            || typeId.contains("iron_golem") || typeId.contains("snow_golem")
            || typeId.contains("copper_golem")
            || typeId.contains("wolf") || typeId.contains("bee")
            || typeId.contains("polar_bear") || typeId.contains("panda")
            || typeId.contains("llama") || typeId.contains("dolphin")
            || typeId.contains("goat") || typeId.contains("allay")) {
            return MapRadarModels.MOB_NEUTRAL;
        }
        return MapRadarModels.MOB_PASSIVE;
    }

    private static int entityPriority(int kind) {
        if (kind == MapRadarModels.MOB_BOSS || kind == MapRadarModels.MOB_TNT
            || kind == MapRadarModels.MOB_CRYSTAL) return 0;
        if (kind == MapRadarModels.MOB_HOSTILE) return 1;
        if (kind == MapRadarModels.MOB_NEUTRAL) return 2;
        if (kind == MapRadarModels.MOB_PASSIVE) return 3;
        return 4;
    }

    private static String itemRegistryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        net.minecraft.world.item.Item item = stack.getItem();
        String cached = ITEM_ID_CACHE.get(item);
        if (cached != null) return cached;
        try {
            Object key = @@MAP_ITEM_REGISTRY@@.getKey(item);
            String id = key == null ? "minecraft:air" : key.toString();
            if (ITEM_ID_CACHE.size() < 2048) ITEM_ID_CACHE.put(item, id);
            return id;
        } catch (Throwable ignored) {
            return "minecraft:air";
        }
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }
}
