package dev.nightclient.renderer.atmosphere;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class AtmosphereColumns {
  public static final int GRID_SIZE = 64;
  public static final int HALF_GRID = GRID_SIZE / 2;
  public static final int COLUMN_COUNT = GRID_SIZE * GRID_SIZE;
  public static final int TEX_WIDTH = GRID_SIZE * 2;
  public static final int TEX_HEIGHT = GRID_SIZE;
  public static final int INCREMENTAL_BUDGET = 128;
  private static final int VALID_COLUMN = 128;
  private static final int GROUND_COVER = 32;
  private static final int BROAD_GROUND_COVER = 64;
  private static final int[] ROOF_OX = {0, 1, -1, 0, 0};
  private static final int[] ROOF_OZ = {0, 0, 0, 1, -1};

  private Object lastWorldIdentity;
  private boolean initialized;
  private int originX;
  private int originZ;
  private int scanCursor;
  private int validCount;
  private boolean dirty;

  private final short[] topY = new short[COLUMN_COUNT];
  private final short[] groundY = new short[COLUMN_COUNT];
  private final byte[] surfaceType = new byte[COLUMN_COUNT];
  private final int[] canopyLayers = new int[COLUMN_COUNT];
  private final short[] groundCover = new short[COLUMN_COUNT];
  private final boolean[] valid = new boolean[COLUMN_COUNT];

  private final short[] tempTopY = new short[COLUMN_COUNT];
  private final short[] tempGroundY = new short[COLUMN_COUNT];
  private final byte[] tempSurfaceType = new byte[COLUMN_COUNT];
  private final int[] tempCanopyLayers = new int[COLUMN_COUNT];
  private final short[] tempGroundCover = new short[COLUMN_COUNT];
  private final boolean[] tempValid = new boolean[COLUMN_COUNT];

  private final int[] argbTexels = new int[TEX_WIDTH * TEX_HEIGHT];
  private final int[] scratchCol = new int[5];
  private final int[] groundSampleScratch = new int[1089];

  private boolean groundInit;
  private float groundYSmooth;
  private float cachedTargetGround = 64.0f;
  private boolean cachedGroundValid;
  private int lastMetricBlockX = Integer.MIN_VALUE;
  private int lastMetricBlockZ = Integer.MIN_VALUE;
  private int columnRevision;
  private int lastMetricRevision = -1;
  private boolean isCave;
  private float caveFactor;
  private float roofShelter;

  public void reset() {
    lastWorldIdentity = null;
    initialized = false;
    validCount = 0;
    scanCursor = 0;
    dirty = true;
    groundInit = false;
    groundYSmooth = 64.0f;
    cachedTargetGround = 64.0f;
    cachedGroundValid = false;
    lastMetricBlockX = Integer.MIN_VALUE;
    lastMetricBlockZ = Integer.MIN_VALUE;
    columnRevision++;
    lastMetricRevision = -1;
    isCave = false;
    caveFactor = 0.0f;
    roofShelter = 0.0f;
    Arrays.fill(valid, false);
    Arrays.fill(argbTexels, 0);
  }

  public void update(AtmosphereWorldAccess world, double camX, double camY, double camZ) {
    if (world == null || !Double.isFinite(camX) || !Double.isFinite(camY) || !Double.isFinite(camZ)) {
      if (initialized && world == null) {
        reset();
      }
      return;
    }
    Object worldId = world.worldIdentity();
    if (worldId == null) {
      if (initialized) {
        reset();
      }
      return;
    }

    int camBlockX = floorInt(camX);
    int camFootY = floorInt(camY - 1.6);
    int camEyeY = floorInt(camY);
    int camBlockZ = floorInt(camZ);
    int targetOriginX = camBlockX - HALF_GRID;
    int targetOriginZ = camBlockZ - HALF_GRID;

    boolean fullBurst = false;
    if (!initialized || worldId != lastWorldIdentity) {
      reset();
      lastWorldIdentity = worldId;
      initialized = true;
      originX = targetOriginX;
      originZ = targetOriginZ;
      fullBurst = true;
    } else {
      int dx = targetOriginX - originX;
      int dz = targetOriginZ - originZ;
      if (Math.abs(dx) >= 24 || Math.abs(dz) >= 24) {
        originX = targetOriginX;
        originZ = targetOriginZ;
        Arrays.fill(valid, false);
        validCount = 0;
        dirty = true;
        fullBurst = true;
      } else if (dx != 0 || dz != 0) {
        shiftGrid(dx, dz);
        originX = targetOriginX;
        originZ = targetOriginZ;
      }
    }

    if (fullBurst) {
      for (int idx = 0; idx < COLUMN_COUNT; idx++) {
        scanIndex(world, idx, camFootY);
      }
      rebuildAllTexels();
    } else {
      int missingBudget = COLUMN_COUNT;
      for (int idx = 0; idx < COLUMN_COUNT && missingBudget > 0; idx++) {
        if (!valid[idx]) {
          scanIndex(world, idx, camFootY);
          missingBudget--;
        }
      }
      for (int i = 0; i < INCREMENTAL_BUDGET; i++) {
        int idx = scanCursor;
        scanCursor = (scanCursor + 1) & (COLUMN_COUNT - 1);
        scanIndex(world, idx, camFootY);
      }
    }

    updateMetrics(world, camBlockX, camFootY, camEyeY, (float) camY, camBlockZ, fullBurst);
  }

  private void shiftGrid(int dx, int dz) {
    System.arraycopy(topY, 0, tempTopY, 0, COLUMN_COUNT);
    System.arraycopy(groundY, 0, tempGroundY, 0, COLUMN_COUNT);
    System.arraycopy(surfaceType, 0, tempSurfaceType, 0, COLUMN_COUNT);
    System.arraycopy(canopyLayers, 0, tempCanopyLayers, 0, COLUMN_COUNT);
    System.arraycopy(groundCover, 0, tempGroundCover, 0, COLUMN_COUNT);
    System.arraycopy(valid, 0, tempValid, 0, COLUMN_COUNT);

    int count = 0;
    for (int cz = 0; cz < GRID_SIZE; cz++) {
      int srcZ = cz + dz;
      boolean zIn = srcZ >= 0 && srcZ < GRID_SIZE;
      int rowBase = cz * GRID_SIZE;
      for (int cx = 0; cx < GRID_SIZE; cx++) {
        int dstIdx = rowBase + cx;
        int srcX = cx + dx;
        if (zIn && srcX >= 0 && srcX < GRID_SIZE) {
          int srcIdx = srcZ * GRID_SIZE + srcX;
          topY[dstIdx] = tempTopY[srcIdx];
          groundY[dstIdx] = tempGroundY[srcIdx];
          surfaceType[dstIdx] = tempSurfaceType[srcIdx];
          canopyLayers[dstIdx] = tempCanopyLayers[srcIdx];
          groundCover[dstIdx] = tempGroundCover[srcIdx];
          boolean v = tempValid[srcIdx];
          valid[dstIdx] = v;
          if (v) count++;
        } else {
          valid[dstIdx] = false;
          topY[dstIdx] = 0;
          groundY[dstIdx] = 0;
          surfaceType[dstIdx] = 0;
          canopyLayers[dstIdx] = 0;
          groundCover[dstIdx] = AtmosphereWorldAccess.NO_GROUND_COVER;
        }
        packColumnTexel(cx, cz, dstIdx);
      }
    }
    validCount = count;
    dirty = true;
    columnRevision++;
  }

  private void scanIndex(AtmosphereWorldAccess world, int idx, int camFootY) {
    int cx = idx & 63;
    int cz = idx >>> 6;
    int wx = originX + cx;
    int wz = originZ + cz;
    scratchCol[3] = 0;
    scratchCol[4] = AtmosphereWorldAccess.NO_GROUND_COVER;
    boolean ok = world.scanColumn(wx, camFootY, wz, scratchCol);
    boolean prevValid = valid[idx];
    if (!ok) {
      if (prevValid) {
        valid[idx] = false;
        validCount--;
        packColumnTexel(cx, cz, idx);
        dirty = true;
        columnRevision++;
      }
      return;
    }
    short newTop = clampShort(scratchCol[0]);
    short newGround = clampShort(scratchCol[1]);
    byte newType = (byte) Math.max(0, Math.min(6, scratchCol[2]));
    int newLayers = scratchCol[3] & ((1 << AtmosphereWorldAccess.CANOPY_LAYERS) - 1);
    int packedCover = scratchCol[4];
    short newCover = packedCover < 0 ? AtmosphereWorldAccess.NO_GROUND_COVER
        : (short) (Math.min(64, packedCover & AtmosphereWorldAccess.GROUND_COVER_DEPTH_MASK)
            | (packedCover & AtmosphereWorldAccess.BROAD_GROUND_COVER));
    if (!prevValid
        || topY[idx] != newTop
        || groundY[idx] != newGround
        || surfaceType[idx] != newType
        || canopyLayers[idx] != newLayers
        || groundCover[idx] != newCover) {
      if (!prevValid) validCount++;
      valid[idx] = true;
      topY[idx] = newTop;
      groundY[idx] = newGround;
      surfaceType[idx] = newType;
      canopyLayers[idx] = newLayers;
      groundCover[idx] = newCover;
      packColumnTexel(cx, cz, idx);
      dirty = true;
      columnRevision++;
    }
  }

  private void rebuildAllTexels() {
    for (int cz = 0; cz < GRID_SIZE; cz++) {
      int rowBase = cz * GRID_SIZE;
      for (int cx = 0; cx < GRID_SIZE; cx++) {
        packColumnTexel(cx, cz, rowBase + cx);
      }
    }
    dirty = true;
  }

  private void packColumnTexel(int cx, int cz, int idx) {
    int texBase = cz * TEX_WIDTH + (cx << 1);
    if (!valid[idx]) {
      argbTexels[texBase] = 0;
      argbTexels[texBase + 1] = 0;
      return;
    }
    int topFixed = encodeHeightFixed(topY[idx]);
    int cover = groundCover[idx];
    int depth = cover < 0 ? 0 : cover & AtmosphereWorldAccess.GROUND_COVER_DEPTH_MASK;
    int groundFixed = encodeHeightFixed(groundY[idx] - depth / 64.0f);
    int r0 = (topFixed >>> 8) & 0xFF;
    int g0 = topFixed & 0xFF;
    int b0 = (groundFixed >>> 8) & 0xFF;
    int a0 = groundFixed & 0xFF;
    argbTexels[texBase] = (a0 << 24) | (r0 << 16) | (g0 << 8) | b0;

    int flags = VALID_COLUMN | surfaceType[idx];
    if (cover >= 0) {
      flags |= GROUND_COVER;
      if ((cover & AtmosphereWorldAccess.BROAD_GROUND_COVER) != 0) flags |= BROAD_GROUND_COVER;
    }
    int layers = canopyLayers[idx];
    argbTexels[texBase + 1] = (flags << 16) | (layers << 24) | (layers >>> 8);
  }

  public static int encodeHeightFixed(float y) {
    int v = Math.round((y + 128.0f) * 64.0f);
    if (v < 0) return 0;
    return v > 65535 ? 65535 : v;
  }

  public static float decodeHeightFixed(int u16) {
    return (u16 & 0xFFFF) * (1.0f / 64.0f) - 128.0f;
  }

  private void updateMetrics(
      AtmosphereWorldAccess world,
      int camBlockX,
      int camFootY,
      int camEyeBlockY,
      float camEyeY,
      int camBlockZ,
      boolean immediate) {
    boolean needRecompute = immediate || !cachedGroundValid
        || camBlockX != lastMetricBlockX || camBlockZ != lastMetricBlockZ
        || columnRevision != lastMetricRevision;
    if (needRecompute) {
      int samples = 0;
      for (int dz = -16; dz <= 16; dz++) {
        int wz = camBlockZ + dz;
        int cz = wz - originZ;
        if (cz < 0 || cz >= GRID_SIZE) continue;
        for (int dx = -16; dx <= 16; dx++) {
          if (dx * dx + dz * dz > 256) continue;
          int wx = camBlockX + dx;
          int cx = wx - originX;
          if (cx < 0 || cx >= GRID_SIZE) continue;
          int idx = cz * GRID_SIZE + cx;
          if (!valid[idx]) continue;
          int st = surfaceType[idx] & 0xFF;
          if (st == 1 || st == 3 || st == 4) {
            groundSampleScratch[samples++] = groundY[idx];
          }
        }
      }
      if (samples == 0) {
        for (int dz = -16; dz <= 16; dz++) {
          int cz = camBlockZ + dz - originZ;
          if (cz < 0 || cz >= GRID_SIZE) continue;
          for (int dx = -16; dx <= 16; dx++) {
            if (dx * dx + dz * dz > 256) continue;
            int cx = camBlockX + dx - originX;
            if (cx < 0 || cx >= GRID_SIZE) continue;
            int idx = cz * GRID_SIZE + cx;
            if (valid[idx] && surfaceType[idx] != 0) {
              groundSampleScratch[samples++] = groundY[idx];
            }
          }
        }
      }
      lastMetricBlockX = camBlockX;
      lastMetricBlockZ = camBlockZ;
      lastMetricRevision = columnRevision;
      if (samples > 0) {
        Arrays.sort(groundSampleScratch, 0, samples);
        cachedTargetGround = groundSampleScratch[samples >>> 2];
        cachedGroundValid = true;
      } else {
        cachedGroundValid = false;
      }
    }
    if (cachedGroundValid) {
      if (!groundInit || immediate) {
        groundYSmooth = cachedTargetGround;
        groundInit = true;
      } else {
        groundYSmooth += (cachedTargetGround - groundYSmooth) * 0.025f;
      }
    }

    boolean caveNow = false;
    int camCx = camBlockX - originX;
    int camCz = camBlockZ - originZ;
    if (camCx >= 0 && camCx < GRID_SIZE && camCz >= 0 && camCz < GRID_SIZE) {
      int camIdx = camCz * GRID_SIZE + camCx;
      if (valid[camIdx] && topY[camIdx] > camFootY + 6) {
        int sky = world.skyLightAt(camBlockX, camEyeBlockY, camBlockZ);
        if (sky == 0) {
          caveNow = true;
        }
      }
    }
    isCave = caveNow;
    float targetCave = caveNow ? 1.0f : 0.0f;
    if (immediate) {
      caveFactor = targetCave;
    } else {
      caveFactor += (targetCave - caveFactor) * 0.05f;
    }

    int roofHits = 0;
    int roofValid = 0;
    for (int k = 0; k < 5; k++) {
      int cx = camBlockX + ROOF_OX[k] - originX;
      int cz = camBlockZ + ROOF_OZ[k] - originZ;
      if (cx >= 0 && cx < GRID_SIZE && cz >= 0 && cz < GRID_SIZE) {
        int idx = cz * GRID_SIZE + cx;
        if (valid[idx]) {
          roofValid++;
          if (topY[idx] > camEyeY + 0.5f) {
            roofHits++;
          }
        }
      }
    }
    float targetShelter = roofValid > 0 ? (float) roofHits / 5.0f : 0.0f;
    if (immediate) {
      roofShelter = targetShelter;
    } else {
      roofShelter += (targetShelter - roofShelter) * 0.05f;
    }
  }

  public boolean hasValid() {
    return validCount > 0;
  }

  public int originX() {
    return originX;
  }

  public int originZ() {
    return originZ;
  }

  public float groundYSmooth() {
    return groundYSmooth;
  }

  public boolean isCave() {
    return isCave;
  }

  public float caveFactor() {
    return caveFactor;
  }

  public float roofShelter() {
    return roofShelter;
  }

  public boolean isValidWorld(int worldX, int worldZ) {
    int cx = worldX - originX;
    int cz = worldZ - originZ;
    if (cx < 0 || cx >= GRID_SIZE || cz < 0 || cz >= GRID_SIZE) return false;
    return valid[cz * GRID_SIZE + cx];
  }

  public int topYWorld(int worldX, int worldZ) {
    int cx = worldX - originX;
    int cz = worldZ - originZ;
    if (cx < 0 || cx >= GRID_SIZE || cz < 0 || cz >= GRID_SIZE) return 0;
    return topY[cz * GRID_SIZE + cx];
  }

  public int groundYWorld(int worldX, int worldZ) {
    int cx = worldX - originX;
    int cz = worldZ - originZ;
    if (cx < 0 || cx >= GRID_SIZE || cz < 0 || cz >= GRID_SIZE) return 0;
    return groundY[cz * GRID_SIZE + cx];
  }

  public int surfaceTypeWorld(int worldX, int worldZ) {
    int cx = worldX - originX;
    int cz = worldZ - originZ;
    if (cx < 0 || cx >= GRID_SIZE || cz < 0 || cz >= GRID_SIZE) return 0;
    return surfaceType[cz * GRID_SIZE + cx] & 0xFF;
  }

  public boolean isNearBuilding(int worldX, int worldZ, int radius) {
    int r2 = radius * radius;
    for (int dz = -radius; dz <= radius; dz++) {
      int cz = worldZ + dz - originZ;
      if (cz < 0 || cz >= GRID_SIZE) continue;
      for (int dx = -radius; dx <= radius; dx++) {
        if (dx * dx + dz * dz > r2) continue;
        int cx = worldX + dx - originX;
        if (cx < 0 || cx >= GRID_SIZE) continue;
        int idx = cz * GRID_SIZE + cx;
        if (valid[idx] && (surfaceType[idx] & 0xFF) == 5) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean consumeDirty() {
    boolean wasDirty = dirty;
    dirty = false;
    return wasDirty;
  }

  public int[] argbTexels() {
    return argbTexels;
  }

  public void writeRgbaBytes(ByteBuffer dest) {
    dest.clear();
    for (int i = 0; i < argbTexels.length; i++) {
      int argb = argbTexels[i];
      dest.put((byte) ((argb >>> 16) & 0xFF));
      dest.put((byte) ((argb >>> 8) & 0xFF));
      dest.put((byte) (argb & 0xFF));
      dest.put((byte) ((argb >>> 24) & 0xFF));
    }
    dest.flip();
  }

  public static int floorInt(double v) {
    int i = (int) v;
    return v < (double) i ? i - 1 : i;
  }

  private static short clampShort(int v) {
    if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
    return v > Short.MAX_VALUE ? Short.MAX_VALUE : (short) v;
  }
}
