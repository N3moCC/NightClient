package dev.nightclient.platform.opt;

public final class BitGridOcclusionEngine {
  public interface SectionSampler {
    int sampleSection(int secX, int secY, int secZ, long[] bits);
  }

  private static final int SEC_CAP = 2048;
  private static final int SEC_MASK = SEC_CAP - 1;
  private static final int VIS_CAP = 8192;
  private static final int VIS_MASK = VIS_CAP - 1;

  private final long[] secKeys = new long[SEC_CAP];
  private final long[] secStamp = new long[SEC_CAP];
  private final byte[] secState = new byte[SEC_CAP];
  private final long[] secBits = new long[SEC_CAP * 64];

  private final long[] visKeys = new long[VIS_CAP];
  private final long[] visFrame = new long[VIS_CAP];
  private final byte[] visResult = new byte[VIS_CAP];

  private long worldGeneration = 1L;
  private long currentFrame = 0L;
  private double lastCamX = Double.NaN, lastCamY = Double.NaN, lastCamZ = Double.NaN;
  private double skipCamX = Double.NaN, skipCamY = Double.NaN, skipCamZ = Double.NaN;
  private int testedThisFrame;
  private int occludedThisFrame;
  private int skipFramesRemaining;
  private boolean skipQueries;

  public void resetWorld() {
    worldGeneration++;
    lastCamX = Double.NaN;
    skipCamX = Double.NaN;
    testedThisFrame = 0;
    occludedThisFrame = 0;
    skipFramesRemaining = 0;
    skipQueries = false;
  }

  public void invalidateSection(int secX, int secY, int secZ) {
    long key = packSection(secX, secY, secZ);
    int slot = mixSlot(key, SEC_MASK);
    if (secKeys[slot] == key) {
      secStamp[slot] = 0L;
    }
  }

  public void beginFrame(long frameIndex, double camX, double camY, double camZ) {
    if (!skipQueries && testedThisFrame >= 32 && occludedThisFrame * 20 < testedThisFrame) {
      skipQueries = true;
      skipFramesRemaining = 30;
      skipCamX = camX;
      skipCamY = camY;
      skipCamZ = camZ;
    } else if (skipQueries) {
      double sdx = camX - skipCamX;
      double sdy = camY - skipCamY;
      double sdz = camZ - skipCamZ;
      if (--skipFramesRemaining <= 0 || sdx * sdx + sdy * sdy + sdz * sdz > 36.0) {
        skipQueries = false;
      }
    }
    testedThisFrame = 0;
    occludedThisFrame = 0;
    currentFrame = frameIndex;

    if (Double.isNaN(lastCamX)) {
      lastCamX = camX;
      lastCamY = camY;
      lastCamZ = camZ;
    } else {
      double dx = camX - lastCamX;
      double dy = camY - lastCamY;
      double dz = camZ - lastCamZ;
      if (dx * dx + dy * dy + dz * dz > 0.25) {
        lastCamX = camX;
        lastCamY = camY;
        lastCamZ = camZ;
        visInvalidationFrame = frameIndex;
      }
    }
  }

  private long visInvalidationFrame;

  public boolean isOccluded(
      long objectKey,
      double camX, double camY, double camZ,
      double minX, double minY, double minZ,
      double maxX, double maxY, double maxZ,
      SectionSampler sampler) {
    if (skipQueries || sampler == null) return false;
    if (camX >= minX - 2.0 && camX <= maxX + 2.0
        && camY >= minY - 2.0 && camY <= maxY + 2.0
        && camZ >= minZ - 2.0 && camZ <= maxZ + 2.0) {
      return false;
    }

    int vslot = mixSlot(objectKey, VIS_MASK);
    long cachedFrame = visFrame[vslot];
    if (visKeys[vslot] == objectKey && cachedFrame >= visInvalidationFrame
        && (currentFrame - cachedFrame) <= 6) {
      byte r = visResult[vslot];
      if (r == 2) return true;
      if (r == 1) return false;
    }

    if (testedThisFrame >= 200 && !(visKeys[vslot] == objectKey && visResult[vslot] == 2)) {
      return false;
    }

    double cx = 0.5 * (minX + maxX);
    double cy = 0.5 * (minY + maxY);
    double cz = 0.5 * (minZ + maxZ);
    testedThisFrame++;
    boolean hidden = rayHitsOpaque(camX, camY, camZ, cx, cy, cz, sampler);
    if (hidden) {
      hidden =
          rayHitsOpaque(camX, camY, camZ, minX, minY, minZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, maxX, minY, minZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, minX, maxY, minZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, maxX, maxY, minZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, minX, minY, maxZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, maxX, minY, maxZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, minX, maxY, maxZ, sampler)
          && rayHitsOpaque(camX, camY, camZ, maxX, maxY, maxZ, sampler);
    }

    visKeys[vslot] = objectKey;
    visFrame[vslot] = currentFrame;
    visResult[vslot] = hidden ? (byte) 2 : (byte) 1;
    if (hidden) {
      occludedThisFrame++;
    } else if (testedThisFrame == 64 && occludedThisFrame * 20 < 64) {
      skipQueries = true;
      skipFramesRemaining = 30;
      skipCamX = camX;
      skipCamY = camY;
      skipCamZ = camZ;
    }
    return hidden;
  }

  public boolean rayHitsOpaque(
      double x0, double y0, double z0,
      double x1, double y1, double z1,
      SectionSampler sampler) {
    double dx = x1 - x0;
    double dy = y1 - y0;
    double dz = z1 - z0;
    double distSq = dx * dx + dy * dy + dz * dz;
    if (distSq < 2.25) return false;

    int bx = fastFloor(x0);
    int by = fastFloor(y0);
    int bz = fastFloor(z0);
    int endX = fastFloor(x1);
    int endY = fastFloor(y1);
    int endZ = fastFloor(z1);
    if (bx == endX && by == endY && bz == endZ) return false;

    int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
    int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
    int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

    double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
    double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
    double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

    double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
        : ((stepX > 0 ? (bx + 1.0 - x0) : (x0 - bx)) * tDeltaX);
    double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
        : ((stepY > 0 ? (by + 1.0 - y0) : (y0 - by)) * tDeltaY);
    double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY
        : ((stepZ > 0 ? (bz + 1.0 - z0) : (z0 - bz)) * tDeltaZ);

    int lastSecX = Integer.MIN_VALUE, lastSecY = Integer.MIN_VALUE, lastSecZ = Integer.MIN_VALUE;
    int cachedSlot = -1;
    byte cachedState = 0;

    for (int step = 0; step < 128; step++) {
      if (tMaxX < tMaxY) {
        if (tMaxX < tMaxZ) {
          if (tMaxX >= 0.995) return false;
          bx += stepX;
          tMaxX += tDeltaX;
        } else {
          if (tMaxZ >= 0.995) return false;
          bz += stepZ;
          tMaxZ += tDeltaZ;
        }
      } else {
        if (tMaxY < tMaxZ) {
          if (tMaxY >= 0.995) return false;
          by += stepY;
          tMaxY += tDeltaY;
        } else {
          if (tMaxZ >= 0.995) return false;
          bz += stepZ;
          tMaxZ += tDeltaZ;
        }
      }

      if (bx == endX && by == endY && bz == endZ) {
        return false;
      }

      int sx = bx >> 4;
      int sy = by >> 4;
      int sz = bz >> 4;
      if (sx != lastSecX || sy != lastSecY || sz != lastSecZ) {
        lastSecX = sx;
        lastSecY = sy;
        lastSecZ = sz;
        cachedSlot = getOrSampleSection(sx, sy, sz, sampler);
        cachedState = secState[cachedSlot];
      }
      if (cachedState == 0) continue;
      if (cachedState == 2) return true;
      int idx = ((by & 15) << 8) | ((bz & 15) << 4) | (bx & 15);
      long word = secBits[(cachedSlot << 6) | (idx >>> 6)];
      if ((word & (1L << (idx & 63))) != 0L) {
        return true;
      }
    }
    return false;
  }

  private int getOrSampleSection(int sx, int sy, int sz, SectionSampler sampler) {
    long key = packSection(sx, sy, sz);
    int slot = mixSlot(key, SEC_MASK);
    if (secKeys[slot] == key && secStamp[slot] == worldGeneration) {
      return slot;
    }
    long[] tmp = TLS_BITS.get();
    for (int i = 0; i < 64; i++) tmp[i] = 0L;
    int state = sampler.sampleSection(sx, sy, sz, tmp);
    secKeys[slot] = key;
    secStamp[slot] = worldGeneration;
    secState[slot] = (byte) state;
    if (state == 1) {
      System.arraycopy(tmp, 0, secBits, slot << 6, 64);
    }
    return slot;
  }

  private static final ThreadLocal<long[]> TLS_BITS = ThreadLocal.withInitial(() -> new long[64]);

  public static long packSection(int sx, int sy, int sz) {
    return ((sx & 0x3FFFFFL) << 42) | ((sz & 0x3FFFFFL) << 20) | (sy & 0xFFFFFL);
  }

  private static int mixSlot(long key, int mask) {
    long h = key ^ (key >>> 33);
    h *= 0xff51afd7ed558ccdL;
    h ^= h >>> 33;
    return (int) h & mask;
  }

  private static int fastFloor(double v) {
    int i = (int) v;
    return v < i ? i - 1 : i;
  }
}
