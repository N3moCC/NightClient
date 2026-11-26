package dev.nightclient.runtime.bench;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FrameTelemetryRecorder {
  public static final int DEFAULT_CAPACITY = 65536;

  private final int capacity;
  private final long[] frameNanos;
  private final long[] cpuRenderNanos;
  private final long[] cpuTickNanos;
  private final long[] gpuNanos;
  private final int[] drawCalls;
  private final int[] triangles;
  private final int[] culledEntities;
  private final int[] culledBlockEntities;
  private final int[] culledParticles;

  private int count;
  private int warmupFramesRemaining;
  private boolean recording;
  private String scenarioId = "manual";
  private String modeLabel = "default";

  private long startWallNanos;
  private long endWallNanos;
  private long startGcCount;
  private long startGcTimeMs;
  private long startAllocBytes;
  private long peakHeapBytes;
  private long lastFrameEndNanos;
  private long currentFrameStartNanos;
  private long currentRenderStartNanos;
  private long currentRenderAccumNanos;
  private long currentTickAccumNanos;
  private int currentDrawCalls;
  private int currentTriangles;
  private int currentCulledEntities;
  private int currentCulledBlockEntities;
  private int currentCulledParticles;

  private final List<GarbageCollectorMXBean> gcBeans;
  private final MemoryMXBean memoryBean;
  private final com.sun.management.ThreadMXBean sunThreadBean;
  private long renderThreadId = -1L;

  private Object jfrRecording;
  private Path jfrOutputPath;

  public FrameTelemetryRecorder() {
    this(DEFAULT_CAPACITY);
  }

  public FrameTelemetryRecorder(int capacity) {
    this.capacity = Math.max(64, capacity);
    this.frameNanos = new long[this.capacity];
    this.cpuRenderNanos = new long[this.capacity];
    this.cpuTickNanos = new long[this.capacity];
    this.gpuNanos = new long[this.capacity];
    this.drawCalls = new int[this.capacity];
    this.triangles = new int[this.capacity];
    this.culledEntities = new int[this.capacity];
    this.culledBlockEntities = new int[this.capacity];
    this.culledParticles = new int[this.capacity];
    this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    this.memoryBean = ManagementFactory.getMemoryMXBean();
    java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
    this.sunThreadBean =
        (base instanceof com.sun.management.ThreadMXBean)
            ? (com.sun.management.ThreadMXBean) base
            : null;
  }

  public void start(String scenarioId, String modeLabel, int warmupFrames, Path jfrPath) {
    this.scenarioId = scenarioId == null ? "manual" : scenarioId;
    this.modeLabel = modeLabel == null ? "default" : modeLabel;
    this.warmupFramesRemaining = Math.max(0, warmupFrames);
    this.count = 0;
    this.recording = true;
    this.renderThreadId = Thread.currentThread().getId();
    this.lastFrameEndNanos = 0L;
    this.currentRenderAccumNanos = 0L;
    this.currentTickAccumNanos = 0L;
    this.currentDrawCalls = 0;
    this.currentTriangles = 0;
    this.currentCulledEntities = 0;
    this.currentCulledBlockEntities = 0;
    this.currentCulledParticles = 0;
    this.jfrOutputPath = jfrPath;
    this.jfrRecording = null;
    if (this.warmupFramesRemaining == 0) {
      beginMeasurementWindow();
    }
  }

  private void beginMeasurementWindow() {
    this.startWallNanos = System.nanoTime();
    this.startGcCount = totalGcCount();
    this.startGcTimeMs = totalGcTimeMs();
    this.startAllocBytes = threadAllocatedBytes(renderThreadId);
    this.peakHeapBytes = currentHeapBytes();
    if (jfrOutputPath != null) {
      startJfr();
    }
  }

  public boolean isRecording() {
    return recording;
  }

  public boolean isWarmingUp() {
    return recording && warmupFramesRemaining > 0;
  }

  public int sampleCount() {
    return count;
  }

  public void onFrameBegin() {
    if (!recording) return;
    currentFrameStartNanos = System.nanoTime();
    currentRenderAccumNanos = 0L;
    currentTickAccumNanos = 0L;
    currentDrawCalls = 0;
    currentTriangles = 0;
    currentCulledEntities = 0;
    currentCulledBlockEntities = 0;
    currentCulledParticles = 0;
  }

  public void onRenderBegin() {
    if (!recording) return;
    currentRenderStartNanos = System.nanoTime();
  }

  public void onRenderEnd() {
    if (!recording || currentRenderStartNanos == 0L) return;
    currentRenderAccumNanos += Math.max(0L, System.nanoTime() - currentRenderStartNanos);
    currentRenderStartNanos = 0L;
  }

  public void addTickNanos(long nanos) {
    if (!recording || nanos <= 0L) return;
    currentTickAccumNanos += nanos;
  }

  public void addDrawCall(int tris) {
    if (!recording) return;
    currentDrawCalls++;
    if (tris > 0) currentTriangles += tris;
  }

  public void addCulledEntity() {
    if (recording) currentCulledEntities++;
  }

  public void addCulledBlockEntity() {
    if (recording) currentCulledBlockEntities++;
  }

  public void addCulledParticle() {
    if (recording) currentCulledParticles++;
  }

  public void onFrameEnd(long lastGpuNanos) {
    if (!recording) return;
    long now = System.nanoTime();
    if (warmupFramesRemaining > 0) {
      warmupFramesRemaining--;
      lastFrameEndNanos = now;
      if (warmupFramesRemaining == 0) {
        beginMeasurementWindow();
      }
      return;
    }
    long delta = lastFrameEndNanos > 0L ? (now - lastFrameEndNanos) : (now - currentFrameStartNanos);
    lastFrameEndNanos = now;
    if (delta <= 0L) delta = 1L;
    if (count < capacity) {
      int idx = count++;
      frameNanos[idx] = delta;
      cpuRenderNanos[idx] = currentRenderAccumNanos;
      cpuTickNanos[idx] = currentTickAccumNanos;
      gpuNanos[idx] = Math.max(0L, lastGpuNanos);
      drawCalls[idx] = currentDrawCalls;
      triangles[idx] = currentTriangles;
      culledEntities[idx] = currentCulledEntities;
      culledBlockEntities[idx] = currentCulledBlockEntities;
      culledParticles[idx] = currentCulledParticles;
    }
    if ((count & 63) == 0) {
      long heap = currentHeapBytes();
      if (heap > peakHeapBytes) peakHeapBytes = heap;
    }
  }

  public Summary stop() {
    if (!recording) {
      return computeSummary();
    }
    recording = false;
    endWallNanos = System.nanoTime();
    stopJfr();
    return computeSummary();
  }

  public Summary computeSummary() {
    int n = count;
    long wallNanos = Math.max(1L, (endWallNanos > startWallNanos ? endWallNanos : System.nanoTime()) - startWallNanos);
    double elapsedSec = wallNanos / 1e9;
    long gcCount = Math.max(0L, totalGcCount() - startGcCount);
    long gcTimeMs = Math.max(0L, totalGcTimeMs() - startGcTimeMs);
    long allocBytes = Math.max(0L, threadAllocatedBytes(renderThreadId) - startAllocBytes);
    double allocMbPerSec = (allocBytes / (1024.0 * 1024.0)) / Math.max(0.001, elapsedSec);
    double peakHeapMb = peakHeapBytes / (1024.0 * 1024.0);

    if (n == 0) {
      return new Summary(
          scenarioId, modeLabel, 0, elapsedSec, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          gcCount, gcTimeMs, allocMbPerSec, peakHeapMb, new long[0]);
    }

    long[] sorted = Arrays.copyOf(frameNanos, n);
    Arrays.sort(sorted);

    long totalFrameNanos = 0L;
    long totalRenderNanos = 0L;
    long totalTickNanos = 0L;
    long totalGpuNanos = 0L;
    int gpuSamples = 0;
    long totalDraws = 0L;
    long totalTris = 0L;
    long totalCulledEnt = 0L;
    long totalCulledBe = 0L;
    long totalCulledPart = 0L;
    for (int i = 0; i < n; i++) {
      totalFrameNanos += frameNanos[i];
      totalRenderNanos += cpuRenderNanos[i];
      totalTickNanos += cpuTickNanos[i];
      if (gpuNanos[i] > 0L) {
        totalGpuNanos += gpuNanos[i];
        gpuSamples++;
      }
      totalDraws += drawCalls[i];
      totalTris += triangles[i];
      totalCulledEnt += culledEntities[i];
      totalCulledBe += culledBlockEntities[i];
      totalCulledPart += culledParticles[i];
    }

    double avgFrameMs = (totalFrameNanos / (double) n) / 1e6;
    double avgFps = 1000.0 / Math.max(0.0001, avgFrameMs);
    double medianMs = percentileMs(sorted, 0.50);
    double p95Ms = percentileMs(sorted, 0.95);
    double p99Ms = percentileMs(sorted, 0.99);
    double p999Ms = percentileMs(sorted, 0.999);
    double maxSpikeMs = sorted[n - 1] / 1e6;

    double low1Fps = tailLowFps(sorted, 0.01);
    double low01Fps = tailLowFps(sorted, 0.001);

    double avgCpuRenderMs = (totalRenderNanos / (double) n) / 1e6;
    double avgCpuTickMs = (totalTickNanos / (double) n) / 1e6;
    double avgGpuMs = gpuSamples > 0 ? (totalGpuNanos / (double) gpuSamples) / 1e6 : 0.0;

    return new Summary(
        scenarioId,
        modeLabel,
        n,
        elapsedSec,
        avgFps,
        avgFrameMs,
        medianMs,
        p95Ms,
        p99Ms,
        p999Ms,
        maxSpikeMs,
        low1Fps,
        low01Fps,
        avgCpuRenderMs,
        avgCpuTickMs,
        avgGpuMs,
        totalDraws / (double) n,
        totalTris / (double) n,
        totalCulledEnt / (double) n,
        totalCulledBe / (double) n,
        gcCount,
        gcTimeMs,
        allocMbPerSec,
        peakHeapMb,
        Arrays.copyOf(frameNanos, n));
  }

  private static double percentileMs(long[] sorted, double p) {
    int idx = (int) Math.min(sorted.length - 1, Math.max(0, Math.floor(p * (sorted.length - 1))));
    return sorted[idx] / 1e6;
  }

  private static double tailLowFps(long[] sorted, double fraction) {
    int tailCount = Math.max(1, (int) Math.ceil(sorted.length * fraction));
    long sum = 0L;
    for (int i = sorted.length - tailCount; i < sorted.length; i++) {
      sum += sorted[i];
    }
    double tailMeanMs = (sum / (double) tailCount) / 1e6;
    return 1000.0 / Math.max(0.0001, tailMeanMs);
  }

  private long totalGcCount() {
    long total = 0L;
    for (GarbageCollectorMXBean bean : gcBeans) {
      long c = bean.getCollectionCount();
      if (c > 0L) total += c;
    }
    return total;
  }

  private long totalGcTimeMs() {
    long total = 0L;
    for (GarbageCollectorMXBean bean : gcBeans) {
      long t = bean.getCollectionTime();
      if (t > 0L) total += t;
    }
    return total;
  }

  private long threadAllocatedBytes(long threadId) {
    if (sunThreadBean == null || threadId <= 0L) return 0L;
    try {
      long bytes = sunThreadBean.getThreadAllocatedBytes(threadId);
      return Math.max(0L, bytes);
    } catch (Throwable ignored) {
      return 0L;
    }
  }

  private long currentHeapBytes() {
    try {
      return Math.max(0L, memoryBean.getHeapMemoryUsage().getUsed());
    } catch (Throwable ignored) {
      return 0L;
    }
  }

  private void startJfr() {
    try {
      Class<?> recClass = Class.forName("jdk.jfr.Recording");
      Object rec = recClass.getConstructor().newInstance();
      enableJfrEvent(recClass, rec, "jdk.ExecutionSample", 10L);
      enableJfrEvent(recClass, rec, "jdk.ObjectAllocationInNewTLAB", -1L);
      enableJfrEvent(recClass, rec, "jdk.ObjectAllocationOutsideTLAB", -1L);
      enableJfrEvent(recClass, rec, "jdk.GarbageCollection", -1L);
      enableJfrEvent(recClass, rec, "jdk.JavaMonitorEnter", 5L);
      recClass.getMethod("start").invoke(rec);
      jfrRecording = rec;
    } catch (Throwable ignored) {
      jfrRecording = null;
    }
  }

  private static void enableJfrEvent(Class<?> recClass, Object rec, String name, long periodMs) {
    try {
      Object settings = recClass.getMethod("enable", String.class).invoke(rec, name);
      if (periodMs > 0L && settings != null) {
        settings
            .getClass()
            .getMethod("withPeriod", java.time.Duration.class)
            .invoke(settings, java.time.Duration.ofMillis(periodMs));
      }
    } catch (Throwable ignored) {
    }
  }

  private void stopJfr() {
    if (jfrRecording == null) return;
    try {
      Class<?> recClass = jfrRecording.getClass();
      recClass.getMethod("stop").invoke(jfrRecording);
      if (jfrOutputPath != null) {
        if (jfrOutputPath.getParent() != null) Files.createDirectories(jfrOutputPath.getParent());
        recClass.getMethod("dump", Path.class).invoke(jfrRecording, jfrOutputPath);
      }
      recClass.getMethod("close").invoke(jfrRecording);
    } catch (Throwable ignored) {
    } finally {
      jfrRecording = null;
    }
  }

  public static final class Summary {
    public final String scenarioId;
    public final String modeLabel;
    public final int frameCount;
    public final double elapsedSec;
    public final double avgFps;
    public final double avgFrameMs;
    public final double medianFrameMs;
    public final double p95FrameMs;
    public final double p99FrameMs;
    public final double p999FrameMs;
    public final double maxSpikeMs;
    public final double low1PercentFps;
    public final double low01PercentFps;
    public final double avgCpuRenderMs;
    public final double avgCpuTickMs;
    public final double avgGpuMs;
    public final double avgDrawCalls;
    public final double avgTriangles;
    public final double avgCulledEntities;
    public final double avgCulledBlockEntities;
    public final long gcCount;
    public final long gcPauseMs;
    public final double allocMbPerSec;
    public final double peakHeapMb;
    public final long[] rawFrameNanos;

    Summary(
        String scenarioId,
        String modeLabel,
        int frameCount,
        double elapsedSec,
        double avgFps,
        double avgFrameMs,
        double medianFrameMs,
        double p95FrameMs,
        double p99FrameMs,
        double p999FrameMs,
        double maxSpikeMs,
        double low1PercentFps,
        double low01PercentFps,
        double avgCpuRenderMs,
        double avgCpuTickMs,
        double avgGpuMs,
        double avgDrawCalls,
        double avgTriangles,
        double avgCulledEntities,
        double avgCulledBlockEntities,
        long gcCount,
        long gcPauseMs,
        double allocMbPerSec,
        double peakHeapMb,
        long[] rawFrameNanos) {
      this.scenarioId = scenarioId;
      this.modeLabel = modeLabel;
      this.frameCount = frameCount;
      this.elapsedSec = elapsedSec;
      this.avgFps = avgFps;
      this.avgFrameMs = avgFrameMs;
      this.medianFrameMs = medianFrameMs;
      this.p95FrameMs = p95FrameMs;
      this.p99FrameMs = p99FrameMs;
      this.p999FrameMs = p999FrameMs;
      this.maxSpikeMs = maxSpikeMs;
      this.low1PercentFps = low1PercentFps;
      this.low01PercentFps = low01PercentFps;
      this.avgCpuRenderMs = avgCpuRenderMs;
      this.avgCpuTickMs = avgCpuTickMs;
      this.avgGpuMs = avgGpuMs;
      this.avgDrawCalls = avgDrawCalls;
      this.avgTriangles = avgTriangles;
      this.avgCulledEntities = avgCulledEntities;
      this.avgCulledBlockEntities = avgCulledBlockEntities;
      this.gcCount = gcCount;
      this.gcPauseMs = gcPauseMs;
      this.allocMbPerSec = allocMbPerSec;
      this.peakHeapMb = peakHeapMb;
      this.rawFrameNanos = rawFrameNanos;
    }

    public String toJson(boolean includeRawFrames) {
      StringBuilder sb = new StringBuilder(1024);
      sb.append("{\n");
      field(sb, "scenarioId", scenarioId, true);
      field(sb, "modeLabel", modeLabel, true);
      field(sb, "frameCount", frameCount, true);
      field(sb, "elapsedSec", elapsedSec, true);
      field(sb, "avgFps", avgFps, true);
      field(sb, "avgFrameMs", avgFrameMs, true);
      field(sb, "medianFrameMs", medianFrameMs, true);
      field(sb, "p95FrameMs", p95FrameMs, true);
      field(sb, "p99FrameMs", p99FrameMs, true);
      field(sb, "p999FrameMs", p999FrameMs, true);
      field(sb, "maxSpikeMs", maxSpikeMs, true);
      field(sb, "low1PercentFps", low1PercentFps, true);
      field(sb, "low01PercentFps", low01PercentFps, true);
      field(sb, "avgCpuRenderMs", avgCpuRenderMs, true);
      field(sb, "avgCpuTickMs", avgCpuTickMs, true);
      field(sb, "avgGpuMs", avgGpuMs, true);
      field(sb, "avgDrawCalls", avgDrawCalls, true);
      field(sb, "avgTriangles", avgTriangles, true);
      field(sb, "avgCulledEntities", avgCulledEntities, true);
      field(sb, "avgCulledBlockEntities", avgCulledBlockEntities, true);
      field(sb, "gcCount", gcCount, true);
      field(sb, "gcPauseMs", gcPauseMs, true);
      field(sb, "allocMbPerSec", allocMbPerSec, true);
      field(sb, "peakHeapMb", peakHeapMb, includeRawFrames);
      if (includeRawFrames) {
        sb.append("  \"frameMs\": [");
        for (int i = 0; i < rawFrameNanos.length; i++) {
          if (i > 0) sb.append(',');
          sb.append(String.format(Locale.ROOT, "%.4f", rawFrameNanos[i] / 1e6));
        }
        sb.append("]\n");
      }
      sb.append("}\n");
      return sb.toString();
    }

    public void writeJson(Path path, boolean includeRawFrames) throws IOException {
      if (path.getParent() != null) Files.createDirectories(path.getParent());
      try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
        writer.write(toJson(includeRawFrames));
      }
    }

    private static void field(StringBuilder sb, String key, String value, boolean comma) {
      sb.append("  \"").append(key).append("\": \"")
          .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
          .append(comma ? "\",\n" : "\"\n");
    }

    private static void field(StringBuilder sb, String key, long value, boolean comma) {
      sb.append("  \"").append(key).append("\": ").append(value).append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder sb, String key, double value, boolean comma) {
      double clean = Double.isFinite(value) ? value : 0.0;
      sb.append("  \"").append(key).append("\": ")
          .append(String.format(Locale.ROOT, "%.4f", clean))
          .append(comma ? ",\n" : "\n");
    }
  }
}
