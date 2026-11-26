package dev.nightclient.renderer.atmosphere;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class MotionBlurEngine {
  public static final int UBO_BYTES = 96;

  private static final double TELEPORT_DIST_SQ = 256.0;
  private static final double MAX_VALID_DT_SEC = 0.25;
  private static final double MIN_VALID_DT_SEC = 1.0e-5;
  private static final float MAX_SHUTTER_SCALE = 3.2f;
  private static final float TARGET_FRAME_SEC = 1.0f / 60.0f;

  private final float[] curViewProj = new float[16];
  private final float[] invCurViewProj = new float[16];
  private final float[] prevViewProj = new float[16];
  private final float[] transMat = new float[16];
  private final float[] tmpMat = new float[16];
  private final float[] reprojMat = new float[16];

  private boolean hasHistory;
  private Object lastWorldId;
  private double prevCamX;
  private double prevCamY;
  private double prevCamZ;
  private long lastFrameNanos;
  private int frameCounter;

  public void reset() {
    hasHistory = false;
    lastWorldId = null;
    lastFrameNanos = 0L;
    frameCounter = 0;
  }

  public static int sampleCountForQuality(String quality) {
    if ("low".equals(quality)) return 8;
    if ("medium".equals(quality)) return 12;
    return 16;
  }

  public boolean prepareFrame(
      Settings.MotionBlur cfg,
      Object worldId,
      double camX,
      double camY,
      double camZ,
      float[] viewRotColMajor,
      float[] projColMajor,
      int reversedDepth,
      long nowNanos,
      ByteBuffer uboOut) {
    if (FeaturePolicy.isBlocked("motionBlur") || cfg == null || !cfg.enabled || cfg.strength <= 0.0f || worldId == null || uboOut == null
        || viewRotColMajor == null || projColMajor == null
        || viewRotColMajor.length < 16 || projColMajor.length < 16
        || !Double.isFinite(camX) || !Double.isFinite(camY) || !Double.isFinite(camZ)) {
      reset();
      return false;
    }

    AtmosphereEngine.multiplyMat4(projColMajor, viewRotColMajor, curViewProj);
    if (!AtmosphereEngine.invertMat4(curViewProj, invCurViewProj)) {
      reset();
      return false;
    }

    if (!hasHistory || worldId != lastWorldId) {
      commitHistory(worldId, camX, camY, camZ, nowNanos);
      return false;
    }

    double dx = camX - prevCamX;
    double dy = camY - prevCamY;
    double dz = camZ - prevCamZ;
    double distSq = dx * dx + dy * dy + dz * dz;

    double dtSec = (lastFrameNanos > 0L && nowNanos > lastFrameNanos)
        ? (nowNanos - lastFrameNanos) * 1.0e-9
        : TARGET_FRAME_SEC;

    if (!Double.isFinite(distSq) || distSq > TELEPORT_DIST_SQ || dtSec > MAX_VALID_DT_SEC) {
      commitHistory(worldId, camX, camY, camZ, nowNanos);
      return false;
    }

    Arrays.fill(transMat, 0.0f);
    transMat[0] = 1.0f;
    transMat[5] = 1.0f;
    transMat[10] = 1.0f;
    transMat[15] = 1.0f;
    transMat[12] = (float) dx;
    transMat[13] = (float) dy;
    transMat[14] = (float) dz;

    AtmosphereEngine.multiplyMat4(prevViewProj, transMat, tmpMat);
    AtmosphereEngine.multiplyMat4(tmpMat, invCurViewProj, reprojMat);

    double safeDt = Math.max(MIN_VALID_DT_SEC, dtSec);
    float strengthNorm = Math.max(0.01f, Math.min(1.0f, cfg.strength / 100.0f));
    float shutter = (float) Math.min(strengthNorm * (TARGET_FRAME_SEC / safeDt), MAX_SHUTTER_SCALE);
    float blurScale = shutter * 0.55f;
    float samples = (float) sampleCountForQuality(cfg.quality);
    float blurSky = cfg.blurSky ? 1.0f : 0.0f;
    float phase = (frameCounter & 63) / 64.0f;
    float maxUvSpan = 0.085f + 0.055f * strengthNorm;

    uboOut.clear();
    for (int i = 0; i < 16; i++) {
      uboOut.putFloat(reprojMat[i]);
    }
    uboOut.putFloat(blurScale);
    uboOut.putFloat(samples);
    uboOut.putFloat((float) reversedDepth);
    uboOut.putFloat(blurSky);
    uboOut.putFloat(phase);
    uboOut.putFloat(maxUvSpan);
    uboOut.putFloat(0.0f);
    uboOut.putFloat(0.0f);
    uboOut.flip();

    commitHistory(worldId, camX, camY, camZ, nowNanos);
    frameCounter++;
    return true;
  }

  private void commitHistory(Object worldId, double camX, double camY, double camZ, long nowNanos) {
    System.arraycopy(curViewProj, 0, prevViewProj, 0, 16);
    lastWorldId = worldId;
    prevCamX = camX;
    prevCamY = camY;
    prevCamZ = camZ;
    lastFrameNanos = nowNanos;
    hasHistory = true;
  }
}
