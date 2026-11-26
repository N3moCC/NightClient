package dev.nightclient.platform.opt;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class Phase2OptEnginesTest {
  private static final com.sun.management.ThreadMXBean THREAD_BEAN =
      (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean)
          ? (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()
          : null;

  private static long allocBytes() {
    return THREAD_BEAN != null ? THREAD_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId()) : 0L;
  }

  @Test
  void bitGridOcclusionHidesBehindWallAndFailsOpenWhenUnobstructed() {
    BitGridOcclusionEngine engine = new BitGridOcclusionEngine();
    BitGridOcclusionEngine.SectionSampler wallSampler = (sx, sy, sz, bits) -> {
      if (sy != 0 || sz != 0) return 0;
      for (int y = 0; y < 16; y++) {
        for (int z = 0; z <= 2; z++) {
          for (int x = 0; x < 16; x++) {
            int idx = (y << 8) | (z << 4) | x;
            bits[idx >>> 6] |= (1L << (idx & 63));
          }
        }
      }
      return 1;
    };

    engine.beginFrame(1L, 8.5, 5.5, -5.0);
    assertTrue(engine.isOccluded(100L, 8.5, 5.5, -5.0, 8, 5, 6, 9, 6, 7, wallSampler));
    assertFalse(engine.isOccluded(101L, 8.5, 5.5, -5.0, 8, 5, -2, 9, 6, -1, wallSampler));
    assertFalse(engine.isOccluded(102L, 8.5, 5.5, -5.0, 8, 5, -5, 9, 6, -4, wallSampler));

    allocBytes();
    for (int i = 0; i < 20_000; i++) {
      engine.rayHitsOpaque(8.5, 5.5, -5.0, 8.5, 5.5, 6.5, wallSampler);
    }
    long a0 = allocBytes();
    long t0 = System.nanoTime();
    int hits = 0;
    for (int i = 0; i < 50_000; i++) {
      if (engine.rayHitsOpaque(8.5, 5.5, -5.0, 8.5, 5.5, 6.5, wallSampler)) hits++;
    }
    long dt = System.nanoTime() - t0;
    long alloc = Math.max(0L, allocBytes() - a0);
    assertEquals(50_000, hits);
    assertTrue(alloc < 4096L, "3D-DDA raycast must allocate ~0 bytes/ray, got " + alloc);
    System.out.println(String.format(Locale.ROOT,
        "[JVM-Bench] BitGridOcclusion 3D-DDA: %.1f ns/ray, alloc=%d B", dt / 50_000.0, alloc));
  }

  @Test
  void itemClusterGridCapsIdenticalDropsPerCellWithZeroAlloc() {
    ItemClusterGrid grid = new ItemClusterGrid();
    grid.beginFrame(1L);
    assertFalse(grid.shouldCull(5, 65, 5, 42, 3));
    assertFalse(grid.shouldCull(5, 65, 5, 42, 3));
    assertFalse(grid.shouldCull(5, 65, 5, 42, 3));
    assertTrue(grid.shouldCull(5, 65, 5, 42, 3), "4th identical item in same cell must cull");
    assertFalse(grid.shouldCull(5, 65, 5, 99, 3));
    grid.beginFrame(2L);
    assertFalse(grid.shouldCull(5, 65, 5, 42, 3));

    allocBytes();
    long a0 = allocBytes();
    for (int i = 0; i < 50_000; i++) {
      grid.shouldCull(i & 31, 65, (i >> 5) & 31, i & 7, 3);
    }
    assertTrue(Math.max(0L, allocBytes() - a0) < 4096L);
  }
}
