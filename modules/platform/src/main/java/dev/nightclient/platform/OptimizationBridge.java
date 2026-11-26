package dev.nightclient.platform;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.OptimizationFlags;
import dev.nightclient.config.Settings;
import dev.nightclient.platform.opt.BitGridOcclusionEngine;
import dev.nightclient.platform.opt.ItemClusterGrid;
import dev.nightclient.runtime.ClientRuntime;
import java.util.function.Predicate;

public final class OptimizationBridge {
  private OptimizationBridge() {}

  public static volatile boolean snapshotActive;
  public static long mask0;
  public static long mask1;
  public static long mask2;
  public static long frameIndex;

  public static boolean opt005UndergroundCull;
  public static volatile boolean opt011LeafCull;
  public static boolean opt024Occlusion;
  public static boolean opt028StaticEntityBake;
  public static boolean opt042SignText;
  public static boolean opt047EmptyBeSkip;
  public static boolean opt056ParticleCollision;
  public static boolean opt062ScoreboardCache;
  public static boolean opt116FastModelCube;
  public static boolean opt117FastBulkQuad;
  public static boolean opt119RetainedHud;
  public static boolean opt132HorizonOcclusion;
  public static boolean opt144TextDisplayCache;
  public static boolean opt147StaticVramBatch;
  public static boolean opt165NativeGpuInstancing;
  public static boolean opt166NativePboUploads;
  public static volatile boolean opt167NativePacketCodec;
  public static boolean anyEntityEarlyOpt;
  public static boolean anyBeEarlyOpt;
  public static boolean anyItemClusterOpt;
  private static int distantNametagsThisFrame;

  public static double camX;
  public static double camY;
  public static double camZ;
  public static double lookX;
  public static double lookY;
  public static double lookZ;

  public static double entityDistSq;
  public static double blockEntityDistSq;
  public static double nametagDistSq;
  public static double particleDistSq;
  public static double signTextDistSq;

  public static int particleLimit;
  public static int itemClusterLimit;

  public static final BitGridOcclusionEngine OCCLUSION = new BitGridOcclusionEngine();
  public static final ItemClusterGrid ITEM_CLUSTERS = new ItemClusterGrid();
  public static final ItemClusterGrid PARTICLE_CLUSTERS = new ItemClusterGrid();

  public static int liveParticleCount;
  public static long particleTickIndex;
  private static Object lastWorldRef;

  public static void resetWorldCaches(Object currentWorld) {
    if (currentWorld != lastWorldRef) {
      lastWorldRef = currentWorld;
      OCCLUSION.resetWorld();
      liveParticleCount = 0;
      lastTerrainMeshBits = -1;
      chunkRebuildRequested = false;
    }
  }

  public static boolean consumeChunkRebuildRequest() {
    if (!chunkRebuildRequested) return false;
    chunkRebuildRequested = false;
    return true;
  }

  public static void onClientTick() {
  }

  public static volatile boolean sodiumLoaded;
  public static volatile boolean chunkRebuildRequested;
  private static int lastTerrainMeshBits = -1;
  private static volatile OptimizationFlags.BitMask modConflictMask = OptimizationFlags.BitMask.EMPTY;
  private static volatile String systemModeOverride = readSystemMode();
  private static volatile boolean ignoreModCompat = Boolean.getBoolean("nightclient.opt.ignoreCompat");
  public static final boolean LAUNCHER_TURBO_MODE = Boolean.getBoolean("nightclient.turbo");

  private static OptimizationFlags.BitMask cachedResolvedMask = OptimizationFlags.BitMask.EMPTY;
  private static boolean cacheValid;
  private static Settings.Optimization cachedOptRef;
  private static boolean cachedModEnabled;
  private static String cachedProfile;
  private static boolean cachedAutoCompat;
  private static OptimizationFlags.BitMask cachedConflictMask;
  private static String cachedEnabledCsv;
  private static String cachedDisabledCsv;
  private static String cachedSysMode;
  private static int cachedToggleBits;

  private static int packToggleBits(Settings.Optimization opt) {
    if (opt == null) return -1;
    int b = 0;
    if (opt.undergroundCaveCulling) b |= 1 << 0;
    if (opt.leafCulling) b |= 1 << 1;
    if (opt.fastModelRendering) b |= 1 << 2;
    if (opt.occlusionCulling) b |= 1 << 3;
    if (opt.staticBlockEntityBatching) b |= 1 << 4;
    if (opt.staticEntityBatching) b |= 1 << 5;
    if (opt.emptyBlockEntitySkip) b |= 1 << 6;
    if (opt.signTextCulling) b |= 1 << 7;
    if (opt.itemClustering) b |= 1 << 8;
    if (opt.nametagCulling) b |= 1 << 9;
    if (opt.entityCulling) b |= 1 << 10;
    if (opt.blockEntityCulling) b |= 1 << 11;
    if (opt.particleCulling) b |= 1 << 12;
    if (opt.fastParticlePhysics) b |= 1 << 13;
    if (opt.scoreboardCache) b |= 1 << 14;
    if (opt.retainedTerrainCache) b |= 1 << 15;
    if (opt.hologramTextCache) b |= 1 << 16;
    if (opt.vanillaHudCache) b |= 1 << 17;
    if (opt.nativeGpuInstancing) b |= 1 << 18;
    if (opt.nativePboUploads) b |= 1 << 19;
    if (opt.nativePacketCodec) b |= 1 << 20;
    return b;
  }

  private static OptimizationFlags.BitMask applyUserToggles(
      OptimizationFlags.BitMask mask, Settings.Optimization opt) {
    if (opt == null) return mask;
    if (!opt.undergroundCaveCulling) mask = mask.without(OptimizationFlags.OPT_005_GPU_HIZ_OCCLUSION);
    if (!opt.retainedTerrainCache) mask = mask.without(OptimizationFlags.OPT_132_ALL_ALTITUDE_HORIZON_OCCLUSION);
    if (!opt.leafCulling) mask = mask.without(OptimizationFlags.OPT_011_SMART_LEAVES_FACE_CULL);
    if (!opt.fastModelRendering) {
      mask = mask.without(OptimizationFlags.OPT_116_FAST_MODEL_CUBE_EMIT);
      mask = mask.without(OptimizationFlags.OPT_117_FAST_BAKED_QUAD_EMIT);
    }
    if (!opt.occlusionCulling) mask = mask.without(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION);
    if (!opt.staticBlockEntityBatching) {
      mask = mask.without(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE);
      mask = mask.without(OptimizationFlags.OPT_051_DECORATED_POT_BELL_BAKE);
      mask = mask.without(OptimizationFlags.OPT_147_CROSS_ERA_STATIC_VRAM_BATCH);
    }
    if (!opt.staticEntityBatching) {
      mask = mask.without(OptimizationFlags.OPT_028_ENTITY_TRANSFORM_BATCH);
      mask = mask.without(OptimizationFlags.OPT_147_CROSS_ERA_STATIC_VRAM_BATCH);
    }
    if (!opt.hologramTextCache) mask = mask.without(OptimizationFlags.OPT_144_TEXT_DISPLAY_HOLOGRAM_CACHE);
    if (!opt.emptyBlockEntitySkip) mask = mask.without(OptimizationFlags.OPT_047_DIRECT_BE_RENDERER_FILTER);
    if (!opt.signTextCulling) mask = mask.without(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE);
    if (!opt.itemClustering) mask = mask.without(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING);
    if (!opt.nametagCulling) mask = mask.without(OptimizationFlags.OPT_032_NAMETAG_OCCLUSION_CACHE);
    if (!opt.entityCulling) mask = mask.without(OptimizationFlags.OPT_023_EARLY_ENTITY_CULL);
    if (!opt.blockEntityCulling) mask = mask.without(OptimizationFlags.OPT_041_EARLY_BE_CULL_OCCLUSION);
    if (!opt.particleCulling) mask = mask.without(OptimizationFlags.OPT_053_O1_PARTICLE_PRIORITY_RING);
    if (!opt.fastParticlePhysics) mask = mask.without(OptimizationFlags.OPT_056_FAST_PARTICLE_COLLISION);
    if (!opt.vanillaHudCache) mask = mask.without(OptimizationFlags.OPT_119_RETAINED_HUD_FBO_CACHE);
    if (!opt.scoreboardCache) mask = mask.without(OptimizationFlags.OPT_062_RETAINED_VANILLA_HUD_TAB);
    if (!opt.nativeGpuInstancing) mask = mask.without(OptimizationFlags.OPT_165_NATIVE_GPU_INSTANCING);
    if (!opt.nativePboUploads) mask = mask.without(OptimizationFlags.OPT_166_NATIVE_PBO_UPLOADS);
    if (!opt.nativePacketCodec) mask = mask.without(OptimizationFlags.OPT_167_NATIVE_PACKET_CODEC);
    return mask;
  }

  private static String readSystemMode() {
    try {
      String value = System.getProperty("nightclient.opt.mode");
      return value == null ? "" : value.trim();
    } catch (Throwable ignored) {
      return "";
    }
  }

  public static void initModCompat(Predicate<String> modLoaded) {
    try {
      sodiumLoaded = modLoaded != null && modLoaded.test("sodium");
      modConflictMask = OptimizationFlags.computeConflictMask(modLoaded);
      cacheValid = false;
    } catch (Throwable ignored) {
      sodiumLoaded = false;
      modConflictMask = OptimizationFlags.BitMask.EMPTY;
    }
  }

  public static void setSystemModeOverride(String mode) {
    systemModeOverride = mode == null ? "" : mode.trim();
    cacheValid = false;
    snapshotActive = false;
    OCCLUSION.resetWorld();
    liveParticleCount = 0;
  }

  public static void setIgnoreModCompat(boolean ignore) {
    ignoreModCompat = ignore;
    cacheValid = false;
    snapshotActive = false;
  }

  public static String activeModeLabel() {
    String sys = systemModeOverride;
    if (sys != null && !sys.isEmpty()) return sys;
    Settings.Optimization opt = optimization();
    if (opt == null || !opt.enabled) return "baseline";
    return opt.profile == null || opt.profile.isEmpty() ? "balanced" : opt.profile;
  }

  public static void beginFrame(
      double cameraX, double cameraY, double cameraZ,
      double viewX, double viewY, double viewZ) {
    frameIndex++;
    distantNametagsThisFrame = 0;
    camX = cameraX;
    camY = cameraY;
    camZ = cameraZ;
    lookX = viewX;
    lookY = viewY;
    lookZ = viewZ;

    try {
      if (FeaturePolicy.isBlocked("optimization")) {
        clearSnapshot();
        return;
      }
      Settings.Optimization opt = optimization();
      String sys = systemModeOverride;
      boolean hasSys = sys != null && !sys.isEmpty();
      if (opt == null && !hasSys && !LAUNCHER_TURBO_MODE) {
        clearSnapshot();
        return;
      }
      boolean modEnabled = (opt != null && opt.enabled) || (!hasSys && LAUNCHER_TURBO_MODE);
      String profile = opt != null ? opt.profile : "performance";
      boolean autoCompat = !ignoreModCompat && (opt == null || opt.autoCompatibility);
      String enabledCsv = opt != null ? opt.enabledFlags : null;
      String disabledCsv = opt != null ? opt.disabledFlags : null;
      int toggleBits = hasSys ? -1 : packToggleBits(opt);
      OptimizationFlags.BitMask conflict = modConflictMask;
      OptimizationFlags.BitMask resolved;
      if (cacheValid
          && opt == cachedOptRef
          && modEnabled == cachedModEnabled
          && autoCompat == cachedAutoCompat
          && toggleBits == cachedToggleBits
          && conflict == cachedConflictMask
          && java.util.Objects.equals(profile, cachedProfile)
          && java.util.Objects.equals(enabledCsv, cachedEnabledCsv)
          && java.util.Objects.equals(disabledCsv, cachedDisabledCsv)
          && java.util.Objects.equals(sys, cachedSysMode)) {
        resolved = cachedResolvedMask;
      } else {
        String effectiveProfile = "custom".equalsIgnoreCase(profile) ? "all" : profile;
        resolved = OptimizationFlags.resolve(
            modEnabled,
            effectiveProfile,
            autoCompat,
            conflict,
            enabledCsv != null && !enabledCsv.isEmpty() ? OptimizationFlags.parseCodeSet(enabledCsv) : null,
            disabledCsv != null && !disabledCsv.isEmpty() ? OptimizationFlags.parseCodeSet(disabledCsv) : null,
            sys);
        if (!hasSys && modEnabled) {
          resolved = applyUserToggles(resolved, opt);
        }
        cachedOptRef = opt;
        cachedModEnabled = modEnabled;
        cachedProfile = profile;
        cachedAutoCompat = autoCompat;
        cachedToggleBits = toggleBits;
        cachedConflictMask = conflict;
        cachedEnabledCsv = enabledCsv;
        cachedDisabledCsv = disabledCsv;
        cachedSysMode = sys;
        cachedResolvedMask = resolved;
        cacheValid = true;
      }

      mask0 = resolved.w0;
      mask1 = resolved.w1;
      mask2 = resolved.w2;
      boolean any = (mask0 | mask1 | mask2) != 0L;
      opt005UndergroundCull = any && resolved.has(OptimizationFlags.OPT_005_GPU_HIZ_OCCLUSION);
      opt011LeafCull = any && resolved.has(OptimizationFlags.OPT_011_SMART_LEAVES_FACE_CULL);
      opt024Occlusion = any && resolved.has(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION);
      opt028StaticEntityBake = any && resolved.has(OptimizationFlags.OPT_028_ENTITY_TRANSFORM_BATCH);
      opt042SignText = any && resolved.has(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE);
      opt047EmptyBeSkip = any && resolved.has(OptimizationFlags.OPT_047_DIRECT_BE_RENDERER_FILTER);
      opt056ParticleCollision = any && resolved.has(OptimizationFlags.OPT_056_FAST_PARTICLE_COLLISION);
      opt062ScoreboardCache = any && resolved.has(OptimizationFlags.OPT_062_RETAINED_VANILLA_HUD_TAB);
      opt116FastModelCube = any && resolved.has(OptimizationFlags.OPT_116_FAST_MODEL_CUBE_EMIT);
      opt117FastBulkQuad = any && resolved.has(OptimizationFlags.OPT_117_FAST_BAKED_QUAD_EMIT);
      opt119RetainedHud = any && resolved.has(OptimizationFlags.OPT_119_RETAINED_HUD_FBO_CACHE);
      opt132HorizonOcclusion = any && resolved.has(OptimizationFlags.OPT_132_ALL_ALTITUDE_HORIZON_OCCLUSION);
      int terrainMeshBits = opt011LeafCull ? 1 : 0;
      if (lastTerrainMeshBits >= 0 && terrainMeshBits != lastTerrainMeshBits) {
        chunkRebuildRequested = true;
      }
      lastTerrainMeshBits = terrainMeshBits;
      opt144TextDisplayCache = any && resolved.has(OptimizationFlags.OPT_144_TEXT_DISPLAY_HOLOGRAM_CACHE);
      opt147StaticVramBatch = any && resolved.has(OptimizationFlags.OPT_147_CROSS_ERA_STATIC_VRAM_BATCH);
      boolean nativeReady = dev.nightclient.platform.opt.NightNative.isLoaded();
      opt165NativeGpuInstancing = any && nativeReady && resolved.has(OptimizationFlags.OPT_165_NATIVE_GPU_INSTANCING);
      opt166NativePboUploads = any && nativeReady && resolved.has(OptimizationFlags.OPT_166_NATIVE_PBO_UPLOADS);
      opt167NativePacketCodec = any && nativeReady && resolved.has(OptimizationFlags.OPT_167_NATIVE_PACKET_CODEC);
      anyItemClusterOpt = any && resolved.has(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING);
      anyEntityEarlyOpt = any && (opt028StaticEntityBake || opt147StaticVramBatch || opt024Occlusion || anyItemClusterOpt
          || resolved.has(OptimizationFlags.OPT_023_EARLY_ENTITY_CULL));
      anyBeEarlyOpt = any && (opt024Occlusion || opt047EmptyBeSkip || opt147StaticVramBatch
          || resolved.has(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE)
          || resolved.has(OptimizationFlags.OPT_041_EARLY_BE_CULL_OCCLUSION)
          || resolved.has(OptimizationFlags.OPT_051_DECORATED_POT_BELL_BAKE));
      if (opt024Occlusion) {
        OCCLUSION.beginFrame(frameIndex, cameraX, cameraY, cameraZ);
      }
      if (anyItemClusterOpt) {
        ITEM_CLUSTERS.beginFrame(frameIndex);
      }
      snapshotActive = any;
      if (!any) {
        clearThresholds();
        return;
      }

      int entD = opt != null ? distance(opt.entityDistance, 16, 256) : 96;
      entityDistSq = (opt == null || opt.entityCulling || hasSys) && entD >= 0 ? (double) entD * entD : -1.0;

      int beD = opt != null ? distance(opt.blockEntityDistance, 16, 256) : 96;
      blockEntityDistSq = (opt == null || opt.blockEntityCulling || hasSys) && beD >= 0 ? (double) beD * beD : -1.0;

      int ntD = opt != null ? distance(opt.nametagDistance, 8, 128) : 48;
      nametagDistSq = (opt == null || opt.nametagCulling || hasSys) && ntD >= 0 ? (double) ntD * ntD : -1.0;

      int pD = opt != null ? distance(opt.particleDistance, 8, 128) : 48;
      int pL = opt != null ? distance(opt.particleLimit, 100, 10000) : 4000;
      if ((opt == null || opt.particleCulling || hasSys) && pD >= 0 && pL >= 0) {
        particleDistSq = (double) pD * pD;
        particleLimit = pL;
      } else {
        particleDistSq = -1.0;
        particleLimit = -1;
      }

      int sgD = opt != null ? distance(opt.signTextDistance, 8, 96) : 28;
      signTextDistSq = (opt == null || opt.signTextCulling || hasSys) && sgD >= 0 ? (double) sgD * sgD : -1.0;

      itemClusterLimit = opt != null ? Math.max(1, Math.min(16, opt.itemClusterLimit)) : 3;
    } catch (Throwable ignored) {
      clearSnapshot();
    }
  }

  public static void resetSnapshot() {
    clearSnapshot();
  }

  private static void clearSnapshot() {
    snapshotActive = false;
    opt005UndergroundCull = false;
    opt011LeafCull = false;
    opt024Occlusion = false;
    opt028StaticEntityBake = false;
    opt042SignText = false;
    opt047EmptyBeSkip = false;
    opt056ParticleCollision = false;
    opt062ScoreboardCache = false;
    opt116FastModelCube = false;
    opt117FastBulkQuad = false;
    opt119RetainedHud = false;
    opt132HorizonOcclusion = false;
    opt144TextDisplayCache = false;
    opt147StaticVramBatch = false;
    opt165NativeGpuInstancing = false;
    opt166NativePboUploads = false;
    opt167NativePacketCodec = false;
    anyEntityEarlyOpt = false;
    anyBeEarlyOpt = false;
    anyItemClusterOpt = false;
    mask0 = 0L;
    mask1 = 0L;
    mask2 = 0L;
    clearThresholds();
  }

  private static void clearThresholds() {
    entityDistSq = -1.0;
    blockEntityDistSq = -1.0;
    nametagDistSq = -1.0;
    particleDistSq = -1.0;
    signTextDistSq = -1.0;
    particleLimit = -1;
    itemClusterLimit = 0;
  }

  public static boolean isOptActive(int optId) {
    if (!snapshotActive || FeaturePolicy.isBlocked("optimization")) return false;
    if (optId < 64) return (mask0 & (1L << optId)) != 0L;
    if (optId < 128) return (mask1 & (1L << (optId - 64))) != 0L;
    if (optId < OptimizationFlags.MAX_SLOTS) return (mask2 & (1L << (optId - 128))) != 0L;
    return false;
  }

  public static boolean enabled() {
    if (FeaturePolicy.isBlocked("optimization")) return false;
    if (snapshotActive) return true;
    try {
      String sys = systemModeOverride;
      if (sys != null && !sys.isEmpty()) {
        return !("baseline".equalsIgnoreCase(sys) || "off".equalsIgnoreCase(sys) || "none".equalsIgnoreCase(sys));
      }
      Settings.Optimization optimization = optimization();
      return optimization != null && optimization.enabled;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean entityCullingActive() {
    if (snapshotActive) {
      return entityDistSq > 0.0 && isOptActive(OptimizationFlags.OPT_023_EARLY_ENTITY_CULL);
    }
    try {
      Settings.Optimization optimization = optimization();
      return optimization != null && optimization.enabled && optimization.entityCulling;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean blockEntityCullingActive() {
    if (snapshotActive) {
      return blockEntityDistSq > 0.0 && isOptActive(OptimizationFlags.OPT_041_EARLY_BE_CULL_OCCLUSION);
    }
    try {
      Settings.Optimization optimization = optimization();
      return optimization != null && optimization.enabled && optimization.blockEntityCulling;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean nametagCullingActive() {
    if (snapshotActive) {
      return nametagDistSq > 0.0 && isOptActive(OptimizationFlags.OPT_032_NAMETAG_OCCLUSION_CACHE);
    }
    try {
      Settings.Optimization optimization = optimization();
      return optimization != null && optimization.enabled && optimization.nametagCulling;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean cullParticleDistance(double distanceSq) {
    if (snapshotActive) {
      return particleDistSq > 0.0
          && isOptActive(OptimizationFlags.OPT_053_O1_PARTICLE_PRIORITY_RING)
          && distanceSq > particleDistSq;
    }
    try {
      Settings.Optimization optimization = optimization();
      if (optimization == null || !optimization.enabled || !optimization.particleCulling)
        return false;
      int distance = distance(optimization.particleDistance, 8, 128);
      return distance >= 0 && distanceSq > (double) distance * distance;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean cullEntity(double distanceSq, double halfDiagonalSq, boolean inWorld,
      boolean localPlayer, boolean glowing) {
    if (!inWorld || localPlayer || glowing) return false;
    if (!(distanceSq >= 1.0e-4)) return false;
    if (snapshotActive) {
      if (entityDistSq <= 0.0 || !isOptActive(OptimizationFlags.OPT_023_EARLY_ENTITY_CULL))
        return false;
      double radius = halfDiagonalSq > 0 ? halfDiagonalSq : 0;
      return distanceSq - radius > entityDistSq;
    }
    try {
      Settings.Optimization optimization = optimization();
      if (optimization == null || !optimization.enabled || !optimization.entityCulling)
        return false;
      int distance = distance(optimization.entityDistance, 16, 256);
      if (distance < 0) return false;
      double radius = halfDiagonalSq > 0 ? halfDiagonalSq : 0;
      return distanceSq - radius > (double) distance * distance;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean cullBlockEntity(double distanceSq, boolean inWorld) {
    if (!inWorld) return false;
    if (snapshotActive) {
      return blockEntityDistSq > 0.0
          && isOptActive(OptimizationFlags.OPT_041_EARLY_BE_CULL_OCCLUSION)
          && distanceSq > blockEntityDistSq;
    }
    try {
      Settings.Optimization optimization = optimization();
      if (optimization == null || !optimization.enabled || !optimization.blockEntityCulling)
        return false;
      int distance = distance(optimization.blockEntityDistance, 16, 256);
      if (distance < 0) return false;
      return distanceSq > (double) distance * distance;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean particleCullingActive() {
    if (snapshotActive) {
      return particleDistSq > 0.0
          && particleLimit > 0
          && isOptActive(OptimizationFlags.OPT_053_O1_PARTICLE_PRIORITY_RING);
    }
    try {
      Settings.Optimization optimization = optimization();
      if (optimization == null || !optimization.enabled || !optimization.particleCulling)
        return false;
      return distance(optimization.particleDistance, 8, 128) >= 0
          && distance(optimization.particleLimit, 100, 10000) >= 0;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean cullParticle(double distanceSq, int liveCount) {
    if (snapshotActive) {
      if (particleDistSq <= 0.0 || particleLimit <= 0
          || !isOptActive(OptimizationFlags.OPT_053_O1_PARTICLE_PRIORITY_RING))
        return false;
      return distanceSq > particleDistSq || liveCount >= particleLimit;
    }
    try {
      if (!particleCullingActive()) return false;
      Settings.Optimization optimization = optimization();
      int distance = distance(optimization.particleDistance, 8, 128);
      int limit = distance(optimization.particleLimit, 100, 10000);
      if (distance < 0 || limit < 0) return false;
      if (distanceSq > (double) distance * distance) return true;
      return liveCount >= limit;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean cullNametag(
      double distanceSq, boolean inWorld, boolean localPlayer, boolean glowing) {
    if (!inWorld || localPlayer || glowing) return false;
    if (snapshotActive) {
      if (nametagDistSq <= 0.0 || !isOptActive(OptimizationFlags.OPT_032_NAMETAG_OCCLUSION_CACHE)) {
        return false;
      }
      if (distanceSq > nametagDistSq) return true;
      if (distanceSq > 225.0) {
        if (distantNametagsThisFrame >= 28) return true;
        distantNametagsThisFrame++;
      }
      return false;
    }
    try {
      Settings.Optimization optimization = optimization();
      if (optimization == null || !optimization.enabled || !optimization.nametagCulling)
        return false;
      int distance = distance(optimization.nametagDistance, 8, 128);
      if (distance < 0) return false;
      return distanceSq > (double) distance * distance;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean cullSignText(double distanceSq, double normalDotLook) {
    if (!snapshotActive || !isOptActive(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE)) return false;
    if (signTextDistSq > 0.0 && distanceSq > signTextDistSq) return true;
    return normalDotLook > 0.15;
  }

  private static Settings.Optimization optimization() {
    if (FeaturePolicy.isBlocked("optimization")) return null;
    ClientRuntime runtime = ClientRuntime.get();
    if (runtime == null) return null;
    Settings settings = runtime.settings();
    return settings == null ? null : settings.optimization;
  }

  private static int distance(int value, int minimum, int maximum) {
    return value >= minimum && value <= maximum ? value : -1;
  }
}
