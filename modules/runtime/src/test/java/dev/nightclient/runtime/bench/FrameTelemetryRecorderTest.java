package dev.nightclient.runtime.bench;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrameTelemetryRecorderTest {
  @TempDir Path tempDir;

  @Test
  void recordsWarmupSamplesPercentilesAndExportsJson() throws Exception {
    FrameTelemetryRecorder recorder = new FrameTelemetryRecorder(256);
    Path jfr = tempDir.resolve("sample.jfr");
    recorder.start("S01", "only:OPT_026", 3, jfr);
    assertTrue(recorder.isWarmingUp());
    for (int i = 0; i < 3; i++) {
      recorder.onFrameBegin();
      recorder.onRenderBegin();
      recorder.onRenderEnd();
      recorder.onFrameEnd(500_000L);
    }
    assertFalse(recorder.isWarmingUp());
    assertEquals(0, recorder.sampleCount());

    for (int i = 0; i < 50; i++) {
      recorder.onFrameBegin();
      recorder.onRenderBegin();
      recorder.addDrawCall(120);
      recorder.addCulledEntity();
      recorder.onRenderEnd();
      recorder.addTickNanos(100_000L);
      recorder.onFrameEnd(1_000_000L);
    }
    FrameTelemetryRecorder.Summary summary = recorder.stop();
    assertEquals(50, summary.frameCount);
    assertTrue(summary.avgFps > 0);
    assertTrue(summary.maxSpikeMs >= summary.p99FrameMs);
    assertTrue(summary.p99FrameMs >= summary.medianFrameMs);
    assertEquals(1.0, summary.avgDrawCalls, 1e-6);
    assertEquals(120.0, summary.avgTriangles, 1e-6);
    assertEquals(1.0, summary.avgCulledEntities, 1e-6);
    assertEquals(1.0, summary.avgGpuMs, 1e-6);

    Path out = tempDir.resolve("summary.json");
    summary.writeJson(out, true);
    String json = Files.readString(out);
    assertTrue(json.contains("\"scenarioId\": \"S01\""));
    assertTrue(json.contains("\"low01PercentFps\""));
    assertTrue(json.contains("\"frameMs\": ["));
    assertTrue(Files.exists(jfr) && Files.size(jfr) > 0, "JFR recording should be written on JDK 17/21/25");
  }
}
