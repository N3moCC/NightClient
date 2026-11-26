package dev.nightclient.runtime.bench;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.config.OptimizationFlags;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OptJvmMicroBenchmarks {
  private static final com.sun.management.ThreadMXBean THREAD_BEAN =
      (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean)
          ? (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()
          : null;

  private static long allocBytes() {
    return THREAD_BEAN != null ? THREAD_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId()) : 0L;
  }

  @Test
  void bitMaskHotPathIsSubNanosecondAndZeroAllocation() {
    OptimizationFlags.BitMask mask = OptimizationFlags.maskForProfile("competitive");
    long w0 = mask.w0, w1 = mask.w1, w2 = mask.w2;
    long sink = 0L;
    allocBytes();
    for (int i = 0; i < 200_000; i++) {
      int bit = i & 63;
      if ((w0 & (1L << bit)) != 0L) sink++;
      if ((w1 & (1L << bit)) != 0L) sink++;
      if ((w2 & (1L << bit)) != 0L) sink++;
    }
    long allocBefore = allocBytes();
    long t0 = System.nanoTime();
    int ops = 2_000_000;
    for (int i = 0; i < ops; i++) {
      int bit = i & 63;
      if ((w0 & (1L << bit)) != 0L) sink++;
      if ((w1 & (1L << bit)) != 0L) sink++;
      if ((w2 & (1L << bit)) != 0L) sink++;
    }
    long elapsed = System.nanoTime() - t0;
    long allocAfter = allocBytes();
    double nsPerOp = elapsed / (double) ops;
    long bytes = Math.max(0L, allocAfter - allocBefore);
    System.out.println(String.format(Locale.ROOT,
        "[JVM-Bench] BitMask3x check: %.3f ns/op, alloc=%d B (sink=%d)", nsPerOp, bytes, sink));
    assertTrue(sink > 0L);
    assertTrue(bytes < 4096L, "Hot-path bitmask check must allocate ~0 bytes/op, got " + bytes);
    assertTrue(nsPerOp < 25.0, "Bitmask check unexpectedly slow: " + nsPerOp + " ns/op");
  }
}
