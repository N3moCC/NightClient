package dev.nightclient.renderer.atmosphere;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

final class MotionBlurEngineTest {
  private static float[] identityMat4() {
    return new float[] {
      1f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f,
      0f, 0f, 1f, 0f,
      0f, 0f, 0f, 1f
    };
  }

  private static float[] perspectiveMat4() {
    return new float[] {
      1.2f, 0f, 0f, 0f,
      0f, 1.8f, 0f, 0f,
      0f, 0f, -1.002f, -1f,
      0f, 0f, -0.2002f, 0f
    };
  }

  @Test
  void primesHistoryThenPacksReprojectionAndResetsOnTeleportOrWorldSwitch() {
    MotionBlurEngine engine = new MotionBlurEngine();
    Settings.MotionBlur cfg = new Settings.MotionBlur();
    cfg.enabled = true;
    cfg.strength = 60.0f;
    cfg.quality = "high";
    cfg.blurSky = true;

    ByteBuffer ubo = ByteBuffer.allocateDirect(MotionBlurEngine.UBO_BYTES).order(ByteOrder.nativeOrder());
    Object worldA = new Object();
    Object worldB = new Object();
    float[] view = identityMat4();
    float[] proj = perspectiveMat4();

    assertFalse(engine.prepareFrame(cfg, worldA, 100.0, 64.0, -50.0, view, proj, 0, 1_000_000_000L, ubo));

    assertTrue(engine.prepareFrame(cfg, worldA, 100.2, 64.0, -50.0, view, proj, 1, 1_016_666_667L, ubo));
    assertEquals(MotionBlurEngine.UBO_BYTES, ubo.remaining());
    float[] mat = new float[16];
    for (int i = 0; i < 16; i++) {
      mat[i] = ubo.getFloat();
      assertTrue(Float.isFinite(mat[i]));
    }
    float blurScale = ubo.getFloat();
    float sampleCount = ubo.getFloat();
    float revDepth = ubo.getFloat();
    float blurSky = ubo.getFloat();
    assertTrue(blurScale > 0.1f && blurScale <= 2.0f);
    assertEquals(16.0f, sampleCount, 0.01f);
    assertEquals(1.0f, revDepth, 0.01f);
    assertEquals(1.0f, blurSky, 0.01f);

    assertFalse(engine.prepareFrame(cfg, worldA, 500.0, 64.0, -50.0, view, proj, 1, 1_033_333_334L, ubo));
    assertTrue(engine.prepareFrame(cfg, worldA, 500.1, 64.0, -50.0, view, proj, 1, 1_050_000_000L, ubo));

    assertFalse(engine.prepareFrame(cfg, worldB, 500.1, 64.0, -50.0, view, proj, 1, 1_066_666_667L, ubo));

    assertEquals(8, MotionBlurEngine.sampleCountForQuality("low"));
    assertEquals(12, MotionBlurEngine.sampleCountForQuality("medium"));
    assertEquals(16, MotionBlurEngine.sampleCountForQuality("high"));
  }

  @Test
  void atmosphereEngineActivatesPlanWhenOnlyMotionBlurIsEnabled() {
    AtmosphereEngine atmo = new AtmosphereEngine();
    Settings s = new Settings();
    s.atmosphere.enabled = false;
    s.customSky.enabled = false;
    s.motionBlur.enabled = true;
    s.motionBlur.strength = 50.0f;

    AtmosphereWorldAccess world = new AtmosphereWorldAccess() {
      final Object id = new Object();
      @Override public Object worldIdentity() { return id; }
      @Override public boolean isOverworld() { return true; }
      @Override public long dayTime() { return 6000L; }
      @Override public int renderDistanceChunks() { return 12; }
      @Override public boolean cameraSubmerged() { return false; }
      @Override public int skyLightAt(int x, int y, int z) { return 15; }
      @Override public boolean scanColumn(int x, int camFootY, int z, int[] out) { return false; }
    };

    float[] view = identityMat4();
    float[] proj = perspectiveMat4();

    AtmosphereEngine.FramePlan p0 = atmo.prepareFrame(s, world, 1920, 1080, 0, 64, 0, 0, 0, view, proj, 0);
    assertFalse(p0.active);
    assertFalse(p0.drawMotionBlur);

    AtmosphereEngine.FramePlan p1 = atmo.prepareFrame(s, world, 1920, 1080, 0.1, 64, 0, 0, 0, view, proj, 0);
    assertTrue(p1.active);
    assertTrue(p1.drawMotionBlur);
    assertFalse(p1.drawAtmosphere);
    assertFalse(p1.drawCustomSky);
  }
}
