package dev.nightclient.renderer.atmosphere;

import dev.nightclient.config.Settings;
import java.util.Arrays;

public final class AtmosphereSimulator {
  public static final int MAX_LAMPS = 16;
  private static final float TAU = 6.2831853f;

  private Object lastWorldIdentity;
  private int lastKind = Integer.MIN_VALUE;
  private double lastTimeSec = Double.NaN;

  private static final class WispState {
    boolean active;
    double wx, wy, wz;
    double vx, vz;
    float age;
    float life;
    int seed;
  }

  private final WispState[] wisps = {
    new WispState(), new WispState(), new WispState(), new WispState()
  };

  private boolean ghostActive;
  private double ghostWx, ghostWy, ghostWz;
  private float ghostAge;
  private float ghostLife;
  private float ghostMelt;
  private float ghostSeed;
  private float ghostCooldown = 8.0f;

  private int lightningIndex;
  private float lightningTimer = 6.0f;
  private float lightningAge = -1.0f;
  private float lightningDuration = 0.45f;

  private int swarmIndex;
  private float swarmTimer = 12.0f;
  private float swarmAge = -1.0f;

  public final float[] lampsUbo = new float[MAX_LAMPS * 8];
  public int lampCount;
  public float lampReach;
  public final float[] closestTwoLitLamps = new float[8];
  private final float[] wispRgbScratch = new float[3];

  public int activeWispCount;
  public float ghostRelX, ghostRelY, ghostRelZ, ghostAlpha;
  public float ghostSeedOut;
  public float lightningPacked = -1.0f;
  public float batSwarmPacked = -1.0f;

  public void reset() {
    for (int i = 0; i < wisps.length; i++) {
      wisps[i].active = false;
    }
    ghostActive = false;
    ghostCooldown = 6.0f;
    lightningAge = -1.0f;
    lightningTimer = 5.0f;
    swarmAge = -1.0f;
    swarmTimer = 10.0f;
    clearOutputs();
  }

  private void clearOutputs() {
    lampCount = 0;
    lampReach = 0.0f;
    activeWispCount = 0;
    ghostAlpha = 0.0f;
    lightningPacked = -1.0f;
    batSwarmPacked = -1.0f;
    Arrays.fill(lampsUbo, 0.0f);
    Arrays.fill(closestTwoLitLamps, 0.0f);
  }

  public void update(
      Settings.Atmosphere cfg,
      int kind,
      AtmosphereWorldAccess world,
      AtmosphereColumns columns,
      double camX,
      double camY,
      double camZ,
      float lookX,
      float lookY,
      float lookZ,
      double timeSec) {
    Object worldId = world != null ? world.worldIdentity() : null;
    if (worldId != lastWorldIdentity || kind != lastKind) {
      reset();
      lastWorldIdentity = worldId;
      lastKind = kind;
      lastTimeSec = timeSec;
    }
    float dt = 0.016f;
    if (!Double.isNaN(lastTimeSec)) {
      double rawDt = timeSec - lastTimeSec;
      if (rawDt >= 0.0 && rawDt <= 0.25) {
        dt = (float) rawDt;
      }
    }
    lastTimeSec = timeSec;

    clearOutputs();
    if (cfg == null || columns == null || !columns.hasValid() || kind != AtmospherePresets.KIND_MYSTIC) {
      return;
    }

    Settings.Atmosphere.MysticLife mlife = cfg.mysticLife != null ? cfg.mysticLife : new Settings.Atmosphere.MysticLife();
    Settings.Atmosphere.MysticSky msky = cfg.mysticSky != null ? cfg.mysticSky : new Settings.Atmosphere.MysticSky();

    updateLightning(msky.lightning, columns.isCave(), dt);
    updateBatSwarms(mlife.batColony, msky.moon, dt);
    updateGhost(
        mlife.ghosts,
        columns,
        camX,
        camY,
        camZ,
        lookX,
        lookY,
        lookZ,
        dt,
        !columns.isCave());
    updateWisps(mlife.wisps ? mlife.wispCount : 0, columns, camX, camY, camZ, (float) timeSec, dt);
    packWispLamps(mlife.wispsColor, camX, camY, camZ, (float) timeSec);
  }

  private void packWispLamps(
      int wispArgb, double camX, double camY, double camZ, float timeSec) {
    float[] wispRgb = wispRgbScratch;
    AtmospherePresets.unpackRgb(wispArgb, wispRgb);
    int wCount = 0;
    float maxReach = 0.0f;
    float bestD0 = Float.MAX_VALUE;
    float bestD1 = Float.MAX_VALUE;
    int bestIdx0 = -1;
    int bestIdx1 = -1;

    for (int i = 0; i < wisps.length; i++) {
      WispState w = wisps[i];
      if (!w.active) continue;
      float wx = (float) (w.wx - camX);
      float wy = (float) (w.wy - camY);
      float wz = (float) (w.wz - camZ);
      float env =
          AtmospherePresets.clamp01(w.age / 0.8f)
              * AtmospherePresets.clamp01((w.life - w.age) / 1.0f);
      float pulse = 0.85f + 0.15f * (float) Math.sin(timeSec * 3.2f + (w.seed & 255) * 0.13f);
      float gain = env * pulse * 1.15f;
      float radius = 5.5f;

      int base = wCount * 8;
      lampsUbo[base] = wx;
      lampsUbo[base + 1] = wy;
      lampsUbo[base + 2] = wz;
      lampsUbo[base + 3] = radius;
      lampsUbo[base + 4] = wispRgb[0] * gain;
      lampsUbo[base + 5] = wispRgb[1] * gain;
      lampsUbo[base + 6] = wispRgb[2] * gain;
      lampsUbo[base + 7] = (float) ((15 << 20) | (w.seed & 0xFFFFF)) / 16777216.0f;

      float d = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
      if (d + radius > maxReach) maxReach = d + radius;
      if (d < bestD0) {
        bestD1 = bestD0;
        bestIdx1 = bestIdx0;
        bestD0 = d;
        bestIdx0 = base;
      } else if (d < bestD1) {
        bestD1 = d;
        bestIdx1 = base;
      }
      wCount++;
    }
    activeWispCount = wCount;
    lampCount = wCount;
    lampReach = wCount > 0 ? maxReach : 0.0f;
    if (bestIdx0 >= 0) {
      closestTwoLitLamps[0] = lampsUbo[bestIdx0];
      closestTwoLitLamps[1] = lampsUbo[bestIdx0 + 1];
      closestTwoLitLamps[2] = lampsUbo[bestIdx0 + 2];
      closestTwoLitLamps[3] = 1.0f;
    }
    if (bestIdx1 >= 0) {
      closestTwoLitLamps[4] = lampsUbo[bestIdx1];
      closestTwoLitLamps[5] = lampsUbo[bestIdx1 + 1];
      closestTwoLitLamps[6] = lampsUbo[bestIdx1 + 2];
      closestTwoLitLamps[7] = 1.0f;
    }
  }

  private void updateWisps(
      int targetCountRaw,
      AtmosphereColumns columns,
      double camX,
      double camY,
      double camZ,
      float timeSec,
      float dt) {
    int targetCount = Math.max(0, Math.min(4, targetCountRaw));
    if (targetCount <= 0 || columns.isCave()) {
      for (int i = 0; i < wisps.length; i++) {
        wisps[i].active = false;
      }
      return;
    }
    for (int i = 0; i < wisps.length; i++) {
      WispState w = wisps[i];
      if (i >= targetCount) {
        w.active = false;
        continue;
      }
      if (!w.active) {
        int timeBits = Float.floatToIntBits(timeSec);
        int seed = mixBits(timeBits ^ (i * 0x9E3779B9) ^ (int) (Math.round(camX * 7.0 + camZ * 13.0)));
        float angle = hashFloat(seed, 1) * TAU;
        float dist = 7.0f + 13.0f * hashFloat(seed, 2);
        double wx = camX + Math.cos(angle) * dist;
        double wz = camZ + Math.sin(angle) * dist;
        int bx = AtmosphereColumns.floorInt(wx);
        int bz = AtmosphereColumns.floorInt(wz);
        if (!columns.isValidWorld(bx, bz)) continue;
        w.active = true;
        w.wx = wx;
        w.wz = wz;
        w.wy = columns.groundYWorld(bx, bz) + 1.15 + 0.45 * hashFloat(seed, 3);
        w.vx = 0.0;
        w.vz = 0.0;
        w.age = 0.0f;
        w.life = 9.0f + 7.0f * hashFloat(seed, 4);
        w.seed = seed & 0xFFFFF;
      }
      w.age += dt;
      double dx = w.wx - camX;
      double dz = w.wz - camZ;
      double d2 = dx * dx + dz * dz;
      if (w.age >= w.life || d2 > 30.0 * 30.0) {
        w.active = false;
        continue;
      }
      double dist = Math.sqrt(Math.max(0.001, d2));
      double wanderA = timeSec * 0.7f + (w.seed & 255) * 0.17;
      double ax = Math.cos(wanderA) * 0.8;
      double az = Math.sin(wanderA * 1.3) * 0.8;
      if (dist < 6.5) {
        double flee = (6.5 - dist) * 2.4 / dist;
        ax += dx * flee;
        az += dz * flee;
      }
      w.vx = (w.vx + ax * dt) * Math.max(0.0, 1.0 - 1.8 * dt);
      w.vz = (w.vz + az * dt) * Math.max(0.0, 1.0 - 1.8 * dt);
      w.wx += w.vx * dt;
      w.wz += w.vz * dt;
      int bx = AtmosphereColumns.floorInt(w.wx);
      int bz = AtmosphereColumns.floorInt(w.wz);
      if (columns.isValidWorld(bx, bz)) {
        double targetY =
            columns.groundYWorld(bx, bz)
                + 1.1
                + 0.28 * Math.sin(timeSec * 1.6f + (w.seed & 63));
        w.wy += (targetY - w.wy) * Math.min(1.0, dt * 3.0);
      }
    }
  }

  private void updateGhost(
      float ghostKnob,
      AtmosphereColumns columns,
      double camX,
      double camY,
      double camZ,
      float lookX,
      float lookY,
      float lookZ,
      float dt,
      boolean outdoor) {
    if (ghostKnob <= 0.01f || !outdoor) {
      ghostActive = false;
      ghostAlpha = 0.0f;
      return;
    }
    float rate = AtmospherePresets.clamp01(ghostKnob / 100.0f);
    if (!ghostActive) {
      ghostCooldown -= dt * (0.35f + 1.35f * rate);
      if (ghostCooldown <= 0.0f) {
        int seed =
            mixBits(
                Float.floatToIntBits((float) camX * 13.7f + (float) camZ * 7.3f)
                    ^ (int) (lastTimeSec * 97.0));
        float backAngle = (float) Math.atan2(lookZ, lookX) + (hashFloat(seed, 1) - 0.5f) * 2.1f;
        float dist = 13.0f + 9.0f * hashFloat(seed, 2);
        double gx = camX + Math.cos(backAngle) * dist;
        double gz = camZ + Math.sin(backAngle) * dist;
        int bx = AtmosphereColumns.floorInt(gx);
        int bz = AtmosphereColumns.floorInt(gz);
        if (columns.isValidWorld(bx, bz) && columns.surfaceTypeWorld(bx, bz) != 0) {
          ghostActive = true;
          ghostWx = gx;
          ghostWz = gz;
          ghostWy = columns.groundYWorld(bx, bz);
          ghostAge = 0.0f;
          ghostLife = 8.0f + 6.0f * hashFloat(seed, 3);
          ghostMelt = 0.0f;
          ghostSeed = hashFloat(seed, 4);
        } else {
          ghostCooldown = 2.0f;
        }
      }
      ghostAlpha = 0.0f;
      return;
    }

    ghostAge += dt;
    float rx = (float) (ghostWx - camX);
    float ry = (float) (ghostWy - camY);
    float rz = (float) (ghostWz - camZ);
    float dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
    if (ghostAge >= ghostLife || dist < 4.5f || dist > 30.0f) {
      ghostActive = false;
      ghostCooldown = 14.0f - 8.0f * rate;
      ghostAlpha = 0.0f;
      return;
    }

    float cy = ry + 1.1f;
    float cLen = Math.max(0.001f, (float) Math.sqrt(rx * rx + cy * cy + rz * rz));
    float gazeDot = (rx * lookX + cy * lookY + rz * lookZ) / cLen;
    if (gazeDot > 0.90f) {
      ghostMelt += dt * 1.8f;
    }
    if (ghostMelt >= 1.0f) {
      ghostActive = false;
      ghostCooldown = 16.0f - 9.0f * rate;
      ghostAlpha = 0.0f;
      return;
    }

    float env =
        AtmospherePresets.clamp01(ghostAge / 1.4f)
            * AtmospherePresets.clamp01((ghostLife - ghostAge) / 1.6f)
            * (1.0f - ghostMelt);
    ghostRelX = rx;
    ghostRelY = ry;
    ghostRelZ = rz;
    ghostAlpha = env;
    ghostSeedOut = ghostSeed;
  }

  private void updateLightning(float lightningKnob, boolean isCave, float dt) {
    if (lightningKnob <= 0.01f || isCave) {
      lightningAge = -1.0f;
      lightningPacked = -1.0f;
      return;
    }
    float rate = AtmospherePresets.clamp01(lightningKnob / 100.0f);
    if (lightningAge >= 0.0f) {
      lightningAge += dt;
      if (lightningAge >= lightningDuration) {
        lightningAge = -1.0f;
        int seed = mixBits(lightningIndex * 0x45D9F3B + 17);
        lightningTimer = (14.0f - 10.5f * rate) * (0.55f + 0.9f * hashFloat(seed, 1));
        lightningPacked = -1.0f;
      } else {
        float u = lightningAge / lightningDuration;
        float stroke1 = (float) Math.exp(-Math.pow((u - 0.14) / 0.09, 2.0));
        float stroke2 = 0.72f * (float) Math.exp(-Math.pow((u - 0.44) / 0.13, 2.0));
        float amp = AtmospherePresets.clamp01(Math.max(stroke1, stroke2));
        lightningPacked = (float) lightningIndex + amp * 0.998f;
      }
    } else {
      lightningTimer -= dt;
      if (lightningTimer <= 0.0f) {
        lightningIndex = (lightningIndex + 1) & 4095;
        lightningAge = 0.0f;
        lightningDuration = 0.42f + 0.18f * hashFloat(mixBits(lightningIndex + 99), 2);
        lightningPacked = (float) lightningIndex + 0.01f;
      } else {
        lightningPacked = -1.0f;
      }
    }
  }

  private void updateBatSwarms(float swarmKnob, boolean moonOn, float dt) {
    if (swarmKnob <= 0.01f || !moonOn) {
      swarmAge = -1.0f;
      batSwarmPacked = -1.0f;
      return;
    }
    float rate = AtmospherePresets.clamp01(swarmKnob / 100.0f);
    if (swarmAge >= 0.0f) {
      swarmAge += dt;
      if (swarmAge >= 8.0f) {
        swarmAge = -1.0f;
        int seed = mixBits(swarmIndex * 0x27D4EB2D + 31);
        swarmTimer = (26.0f - 19.0f * rate) * (0.6f + 0.8f * hashFloat(seed, 1));
        batSwarmPacked = -1.0f;
      } else {
        float across = AtmospherePresets.clamp01(swarmAge / 8.0f);
        batSwarmPacked = (float) swarmIndex + across * 0.998f;
      }
    } else {
      swarmTimer -= dt;
      if (swarmTimer <= 0.0f) {
        swarmIndex = (swarmIndex + 1) & 4095;
        swarmAge = 0.0f;
        batSwarmPacked = (float) swarmIndex;
      } else {
        batSwarmPacked = -1.0f;
      }
    }
  }

  public static int qualityTier(String q) {
    if ("low".equals(q)) return 0;
    if ("high".equals(q)) return 2;
    return 1;
  }

  public static int mixBits(int x) {
    x ^= x >>> 16;
    x *= 0x7FEB352D;
    x ^= x >>> 15;
    x *= 0x846CA68B;
    x ^= x >>> 16;
    return x;
  }

  private static float hashFloat(int h, int stream) {
    int v = mixBits(h ^ (stream * 0x632BE5AB));
    return ((v >>> 8) & 0xFFFFFF) * (1.0f / 16777216.0f);
  }
}
