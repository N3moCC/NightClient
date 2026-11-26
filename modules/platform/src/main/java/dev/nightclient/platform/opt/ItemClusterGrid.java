package dev.nightclient.platform.opt;

public final class ItemClusterGrid {
  private static final int CAP = 4096;
  private static final int MASK = CAP - 1;

  private final long[] keys = new long[CAP];
  private final long[] stamps = new long[CAP];
  private final int[] counts = new int[CAP];
  private long generation = 1L;

  public void beginFrame(long frameIndex) {
    generation = frameIndex == 0L ? 1L : frameIndex;
  }

  public boolean shouldCull(int bx, int by, int bz, int typeHash, int maxPerCell) {
    if (maxPerCell <= 0) return false;
    long posKey = ((long) (bx & 0xFFFFF) << 44)
        | ((long) (bz & 0xFFFFF) << 24)
        | ((long) (by & 0xFFF) << 12)
        | (typeHash & 0xFFFL);
    int slot = mix(posKey) & MASK;
    for (int p = 0; p < 4; p++) {
      int idx = (slot + p) & MASK;
      if (stamps[idx] != generation) {
        stamps[idx] = generation;
        keys[idx] = posKey;
        counts[idx] = 1;
        return false;
      }
      if (keys[idx] == posKey) {
        int next = ++counts[idx];
        return next > maxPerCell;
      }
    }
    return false;
  }

  private static int mix(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return (int) (z ^ (z >>> 31));
  }
}
