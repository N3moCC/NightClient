package dev.nightclient.renderer.cape;

public final class WavyCapeSimulation {
  private static final float BASE_WIDTH = 10.0f / 16.0f;
  private static final float BASE_LENGTH = 16.0f / 16.0f;
  private static final float FIXED_DT = 1.0f / 120.0f;
  private static final int MAX_SUBSTEPS = 4;
  private static final int CONSTRAINT_ITERS = 6;
  private static final float GRAVITY_BASE = 9.2f;

  private int cols;
  private int rows;
  private int count;

  private float[] x, y, z;
  private float[] px, py, pz;
  private float[] sx, sy, sz;
  private float[] rx, ry, rz;
  private float[] ox, oy, oz;
  private float[] nx, ny, nz;
  private float[] vShade;
  private float[] rowSegX;
  private float[] rowHemW;
  private float[] pinX, pinY, pinZ;
  private float segY;
  private float lastWidthScale = -1.0f;
  private float lastLengthScale = -1.0f;
  private float lastHemFlare = -1.0f;

  private boolean initialized;
  private long lastTimeNanos;
  private float accumulator;
  private float simTime;
  private final float entityPhase;

  private double prevWorldX, prevWorldY, prevWorldZ;
  private float prevBodyRot;
  private float smoothVx, smoothVy, smoothVz;
  private float prevSmoothVx, prevSmoothVy, prevSmoothVz;
  private float smoothAx, smoothAy, smoothAz;
  private float smoothOmega;
  private float prevBodyXRot;
  private float prevBodyY;
  private float prevSwimAmount;
  private boolean hasPrevPose;

  private float shoulderY;
  private float shoulderZ;
  private float waistY;
  private float waistZ;
  private float torsoHalfW;

  private final WavyCapeMesh mesh;
  private final float[] blkLx = new float[17], blkLy = new float[17], blkLz = new float[17];
  private final float[] blkRx = new float[17], blkRy = new float[17], blkRz = new float[17];
  private final float[] blkBx = new float[16], blkBy = new float[16], blkBz = new float[16];
  private final float[] blkOlx = new float[17], blkOly = new float[17], blkOlz = new float[17];
  private final float[] blkOrx = new float[17], blkOry = new float[17], blkOrz = new float[17];

  public WavyCapeSimulation(int entityId, WavyCapeConfig config) {
    this.entityPhase = ((entityId * 2654435761L) & 0xffffL) * (6.2831853f / 65536.0f);
    this.mesh = new WavyCapeMesh(420);
    resizeGrid(config.gridCols(), config.gridRows(), config);
  }

  private void resizeGrid(int newCols, int newRows, WavyCapeConfig config) {
    if (cols == newCols && rows == newRows && x != null) {
      updateRestMetrics(config);
      return;
    }
    cols = newCols;
    rows = newRows;
    count = cols * rows;
    x = new float[count];
    y = new float[count];
    z = new float[count];
    px = new float[count];
    py = new float[count];
    pz = new float[count];
    sx = new float[count];
    sy = new float[count];
    sz = new float[count];
    rx = new float[count];
    ry = new float[count];
    rz = new float[count];
    ox = new float[count];
    oy = new float[count];
    oz = new float[count];
    nx = new float[count];
    ny = new float[count];
    nz = new float[count];
    vShade = new float[count];
    rowSegX = new float[rows];
    rowHemW = new float[rows];
    for (int r = 0; r < rows; r++) {
      rowHemW[r] = (float) Math.pow(r / (float) (rows - 1), 1.15);
    }
    pinX = new float[cols];
    pinY = new float[cols];
    pinZ = new float[cols];
    initialized = false;
    updateRestMetrics(config);
  }

  private void updateRestMetrics(WavyCapeConfig config) {
    if (lastWidthScale == config.widthScale && lastLengthScale == config.lengthScale
        && lastHemFlare == config.hemFlare) {
      return;
    }
    lastWidthScale = config.widthScale;
    lastLengthScale = config.lengthScale;
    lastHemFlare = config.hemFlare;
    float width = BASE_WIDTH * config.widthScale;
    float length = BASE_LENGTH * config.lengthScale;
    segY = length / (rows - 1);
    for (int r = 0; r < rows; r++) {
      float v = r / (float) (rows - 1);
      float rowW = width * (1.0f + config.hemFlare * 0.32f * v * v);
      rowSegX[r] = rowW / (cols - 1);
    }
  }

  private void sanitizePose(WavyCapePose p) {
    if (!Double.isFinite(p.worldX)) p.worldX = hasPrevPose ? prevWorldX : 0.0;
    if (!Double.isFinite(p.worldY)) p.worldY = hasPrevPose ? prevWorldY : 0.0;
    if (!Double.isFinite(p.worldZ)) p.worldZ = hasPrevPose ? prevWorldZ : 0.0;
    if (!Float.isFinite(p.bodyRot)) p.bodyRot = hasPrevPose ? prevBodyRot : 0.0f;
    if (!Float.isFinite(p.bodyX)) p.bodyX = 0.0f;
    if (!Float.isFinite(p.bodyY)) p.bodyY = hasPrevPose ? prevBodyY : 0.0f;
    if (!Float.isFinite(p.bodyZ)) p.bodyZ = 0.0f;
    if (!Float.isFinite(p.bodyXRot)) p.bodyXRot = hasPrevPose ? prevBodyXRot : 0.0f;
    if (!Float.isFinite(p.bodyYRot)) p.bodyYRot = 0.0f;
    if (!Float.isFinite(p.bodyZRot)) p.bodyZRot = 0.0f;
    if (!Float.isFinite(p.rightLegY)) p.rightLegY = 12.0f;
    if (!Float.isFinite(p.rightLegZ)) p.rightLegZ = 0.0f;
    if (!Float.isFinite(p.rightLegXRot)) p.rightLegXRot = 0.0f;
    if (!Float.isFinite(p.rightLegZRot)) p.rightLegZRot = 0.0f;
    if (!Float.isFinite(p.leftLegY)) p.leftLegY = 12.0f;
    if (!Float.isFinite(p.leftLegZ)) p.leftLegZ = 0.0f;
    if (!Float.isFinite(p.leftLegXRot)) p.leftLegXRot = 0.0f;
    if (!Float.isFinite(p.leftLegZRot)) p.leftLegZRot = 0.0f;
    if (!Float.isFinite(p.swimAmount)) p.swimAmount = 0.0f;
    if (!Float.isFinite(p.walkPos)) p.walkPos = 0.0f;
    if (!Float.isFinite(p.walkSpeed)) p.walkSpeed = 0.0f;
    if (!Float.isFinite(p.rainLevel)) p.rainLevel = 0.0f;
    if (!Float.isFinite(p.thunderLevel)) p.thunderLevel = 0.0f;
  }

  public WavyCapeMesh updateAndBuild(WavyCapePose pose, WavyCapeConfig config) {
    sanitizePose(pose);
    resizeGrid(config.gridCols(), config.gridRows(), config);
    computePinAndTorso(pose, config);

    if (!initialized) {
      resetCloth(pose, config);
      initialized = true;
      lastTimeNanos = pose.timeNanos;
      prevWorldX = pose.worldX;
      prevWorldY = pose.worldY;
      prevWorldZ = pose.worldZ;
      prevBodyRot = pose.bodyRot;
      prevBodyXRot = pose.bodyXRot;
      prevBodyY = pose.bodyY;
      prevSwimAmount = pose.swimAmount;
      hasPrevPose = true;
      buildMesh(config);
      return mesh;
    }

    long elapsedNanos = pose.timeNanos - lastTimeNanos;
    if (elapsedNanos < 100_000L) {
      if (elapsedNanos < 0L) {
        lastTimeNanos = pose.timeNanos;
      }
      buildMesh(config);
      return mesh;
    }

    lastTimeNanos = pose.timeNanos;
    float frameDt = elapsedNanos * 1.0e-9f;
    if (frameDt > 0.05f) {
      frameDt = 0.05f;
      smoothVx *= 0.5f;
      smoothVy *= 0.5f;
      smoothVz *= 0.5f;
      smoothOmega *= 0.5f;
    }

    updateKinematics(pose, frameDt);
    absorbPoseTransition(pose, config);

    accumulator += frameDt;
    int steps = 0;
    while (accumulator >= FIXED_DT && steps < MAX_SUBSTEPS) {
      System.arraycopy(x, 0, sx, 0, count);
      System.arraycopy(y, 0, sy, 0, count);
      System.arraycopy(z, 0, sz, 0, count);
      stepPhysics(pose, config, FIXED_DT);
      accumulator -= FIXED_DT;
      simTime += FIXED_DT;
      steps++;
    }
    if (accumulator > FIXED_DT) {
      accumulator = FIXED_DT;
    }

    float alpha = clamp(accumulator / FIXED_DT, 0.0f, 1.0f);
    interpolateRenderPositions(alpha);
    buildMesh(config);
    return mesh;
  }

  public int gridCols() {
    return cols;
  }

  public int gridRows() {
    return rows;
  }

  public float renderX(int col, int row) {
    return rx[row * cols + col];
  }

  public float renderY(int col, int row) {
    return ry[row * cols + col];
  }

  public float renderZ(int col, int row) {
    return rz[row * cols + col];
  }

  public float currentX(int col, int row) {
    return x[row * cols + col];
  }

  public float currentY(int col, int row) {
    return y[row * cols + col];
  }

  public float currentZ(int col, int row) {
    return z[row * cols + col];
  }

  public float shoulderZ() {
    return shoulderZ;
  }

  public float waistZ() {
    return waistZ;
  }

  private void computePinAndTorso(WavyCapePose pose, WavyCapeConfig config) {
    float bx = pose.bodyX / 16.0f;
    float by = pose.bodyY / 16.0f;
    float bz = pose.bodyZ / 16.0f;

    float cosX = (float) Math.cos(pose.bodyXRot);
    float sinX = (float) Math.sin(pose.bodyXRot);
    float cosY = (float) Math.cos(pose.bodyYRot);
    float sinY = (float) Math.sin(pose.bodyYRot);

    float width = BASE_WIDTH * config.widthScale;
    float jacketMargin = pose.showJacket ? 0.017f : 0.006f;
    float armorOffsetY = pose.hasChestArmor ? -0.044f : 0.0f;
    float armorOffsetZ = pose.hasChestArmor ? 0.064f : 0.0f;
    float localZ = (2.0f / 16.0f) + jacketMargin + armorOffsetZ;
    float localY = armorOffsetY;

    for (int c = 0; c < cols; c++) {
      float u = c / (float) (cols - 1);
      float lx = (0.5f - u) * width;
      float y1 = localY * cosX - localZ * sinX;
      float z1 = localY * sinX + localZ * cosX;
      float x2 = lx * cosY + z1 * sinY;
      float z2 = -lx * sinY + z1 * cosY;
      pinX[c] = bx + x2;
      pinY[c] = by + y1;
      pinZ[c] = bz + z2;
    }

    shoulderY = by + (localY * cosX - localZ * sinX);
    shoulderZ = bz + (localY * sinX + localZ * cosX);

    float waistLocalY = 12.0f / 16.0f;
    float waistLocalZ = (2.0f / 16.0f) + jacketMargin + (pose.hasChestArmor ? 0.058f : 0.0f);
    waistY = by + (waistLocalY * cosX - waistLocalZ * sinX);
    waistZ = bz + (waistLocalY * sinX + waistLocalZ * cosX);
    torsoHalfW = (4.0f / 16.0f) + (pose.showJacket ? 0.016f : 0.0f)
        + (pose.hasChestArmor ? 0.062f : 0.0f);
  }

  private void resetCloth(WavyCapePose pose, WavyCapeConfig config) {
    float length = BASE_LENGTH * config.lengthScale;
    for (int r = 0; r < rows; r++) {
      float v = r / (float) (rows - 1);
      float rowW = BASE_WIDTH * config.widthScale * (1.0f + config.hemFlare * 0.32f * v * v);
      for (int c = 0; c < cols; c++) {
        int i = r * cols + c;
        float u = c / (float) (cols - 1);
        if (r == 0) {
          x[i] = pinX[c];
          y[i] = pinY[c];
          z[i] = pinZ[c];
        } else {
          float fold = (float) Math.sin(u * 6.2831853f + entityPhase) * 0.008f * v;
          x[i] = (0.5f - u) * rowW;
          y[i] = pinY[c] + length * v;
          float backLine = Math.max(pinZ[c], waistZ);
          z[i] = pinZ[c] + (backLine - pinZ[c]) * Math.min(1.0f, v * 1.35f) + 0.025f * v + fold;
        }
        px[i] = x[i];
        py[i] = y[i];
        pz[i] = z[i];
        sx[i] = x[i];
        sy[i] = y[i];
        sz[i] = z[i];
        rx[i] = x[i];
        ry[i] = y[i];
        rz[i] = z[i];
      }
    }
    for (int iter = 0; iter < 4; iter++) {
      satisfyConstraints(pose, config, true);
    }
    System.arraycopy(x, 0, px, 0, count);
    System.arraycopy(y, 0, py, 0, count);
    System.arraycopy(z, 0, pz, 0, count);
    System.arraycopy(x, 0, sx, 0, count);
    System.arraycopy(y, 0, sy, 0, count);
    System.arraycopy(z, 0, sz, 0, count);
    System.arraycopy(x, 0, rx, 0, count);
    System.arraycopy(y, 0, ry, 0, count);
    System.arraycopy(z, 0, rz, 0, count);
  }

  private void updateKinematics(WavyCapePose pose, float dt) {
    if (!Double.isFinite(pose.worldX) || !Double.isFinite(pose.worldY)
        || !Double.isFinite(pose.worldZ) || !Float.isFinite(pose.bodyRot)) {
      return;
    }
    if (!hasPrevPose) {
      prevWorldX = pose.worldX;
      prevWorldY = pose.worldY;
      prevWorldZ = pose.worldZ;
      prevBodyRot = pose.bodyRot;
      prevBodyXRot = pose.bodyXRot;
      prevBodyY = pose.bodyY;
      prevSwimAmount = pose.swimAmount;
      hasPrevPose = true;
      return;
    }

    double dx = pose.worldX - prevWorldX;
    double dy = pose.worldY - prevWorldY;
    double dz = pose.worldZ - prevWorldZ;
    prevWorldX = pose.worldX;
    prevWorldY = pose.worldY;
    prevWorldZ = pose.worldZ;

    float dYawDeg = wrapDegrees(pose.bodyRot - prevBodyRot);
    prevBodyRot = pose.bodyRot;

    double sqDist = dx * dx + dy * dy + dz * dz;
    if (sqDist > 16.0) {
      smoothVx = 0.0f;
      smoothVy = 0.0f;
      smoothVz = 0.0f;
      prevSmoothVx = 0.0f;
      prevSmoothVy = 0.0f;
      prevSmoothVz = 0.0f;
      smoothAx = 0.0f;
      smoothAy = 0.0f;
      smoothAz = 0.0f;
      smoothOmega = 0.0f;
      return;
    }

    float invDt = 1.0f / Math.max(0.001f, dt);
    float ivx = (float) (dx * invDt);
    float ivy = (float) (dy * invDt);
    float ivz = (float) (dz * invDt);

    float speed = (float) Math.sqrt(ivx * ivx + ivy * ivy + ivz * ivz);
    if (speed > 26.0f) {
      float scale = 26.0f / speed;
      ivx *= scale;
      ivy *= scale;
      ivz *= scale;
    }

    float velBlend = 1.0f - (float) Math.exp(-dt * 24.0f);
    prevSmoothVx = smoothVx;
    prevSmoothVy = smoothVy;
    prevSmoothVz = smoothVz;
    smoothVx += (ivx - smoothVx) * velBlend;
    smoothVy += (ivy - smoothVy) * velBlend;
    smoothVz += (ivz - smoothVz) * velBlend;

    float rawAx = clamp((smoothVx - prevSmoothVx) * invDt, -28.0f, 28.0f);
    float rawAy = clamp((smoothVy - prevSmoothVy) * invDt, -28.0f, 28.0f);
    float rawAz = clamp((smoothVz - prevSmoothVz) * invDt, -28.0f, 28.0f);
    float accBlend = 1.0f - (float) Math.exp(-dt * 18.0f);
    smoothAx += (rawAx - smoothAx) * accBlend;
    smoothAy += (rawAy - smoothAy) * accBlend;
    smoothAz += (rawAz - smoothAz) * accBlend;

    if (Math.abs(dYawDeg) > 125.0f) {
      dYawDeg = 0.0f;
    }
    float rawOmega = clamp((float) Math.toRadians(dYawDeg) * invDt, -11.0f, 11.0f);
    float angBlend = 1.0f - (float) Math.exp(-dt * 20.0f);
    smoothOmega += (rawOmega - smoothOmega) * angBlend;
  }

  private void stepPhysics(WavyCapePose pose, WavyCapeConfig config, float dt) {
    computeVertexNormals(x, y, z);

    float yawRad = (float) Math.toRadians(pose.bodyRot);
    float sinYaw = (float) Math.sin(yawRad);
    float cosYaw = (float) Math.cos(yawRad);

    float forwardSpeed = smoothVx * (-sinYaw) + smoothVz * cosYaw;
    float leftSpeed = smoothVx * cosYaw + smoothVz * sinYaw;
    float upSpeed = smoothVy;

    float accelForward = smoothAx * (-sinYaw) + smoothAz * cosYaw;
    float accelLeft = smoothAx * cosYaw + smoothAz * sinYaw;
    float accelUp = smoothAy;

    float airX = -leftSpeed;
    float airY = upSpeed;
    float airZ = forwardSpeed;

    float horizSpeed = (float) Math.sqrt(forwardSpeed * forwardSpeed + leftSpeed * leftSpeed);
    float speedNorm = Math.min(1.85f, horizSpeed / 5.6f);

    boolean underwater = config.waterPhysics && pose.inWater;
    float dampRate = 1.55f + config.damping * 5.2f + (underwater ? 5.2f : 0.0f);
    float velKeep = (float) Math.exp(-dampRate * dt);

    float gMag = GRAVITY_BASE * config.gravity * (underwater ? 0.38f : 1.0f);
    float swimRad = clamp(pose.swimAmount, 0.0f, 1.0f) * 1.48f;
    float cosSwim = (float) Math.cos(swimRad);
    float sinSwim = (float) Math.sin(swimRad);
    float bodyAirY = airY * cosSwim + airZ * sinSwim;
    float bodyAirZ = airZ * cosSwim - airY * sinSwim;

    float gx = 0.0f;
    float gy = gMag * cosSwim;
    float gz = -gMag * sinSwim * (underwater ? 0.16f : 0.42f);
    if (pose.swimAmount > 0.05f) {
      gy += pose.swimAmount * (3.6f + Math.max(0.0f, forwardSpeed) * 1.15f);
      gz += pose.swimAmount * (underwater ? 1.10f : 0.55f);
    }

    float weatherMult = config.weatherWind
        ? (1.0f + clamp(pose.rainLevel, 0.0f, 1.0f) * 0.70f
            + clamp(pose.thunderLevel, 0.0f, 1.0f) * 1.15f)
        : 1.0f;
    float windAmp = config.wind * weatherMult * (underwater ? 0.45f : 1.0f);
    float dragK = config.drag * (underwater ? 1.25f : 1.0f);
    float flutter = config.flutter;
    float turnK = config.turnSwing;

    float effAirZ = bodyAirZ >= 0.0f
        ? (bodyAirZ / (1.0f + 0.085f * bodyAirZ))
        : (bodyAirZ * 0.42f / (1.0f + 0.09f * Math.abs(bodyAirZ)));
    float effAirX = airX / (1.0f + 0.075f * Math.abs(airX));
    float effAirY = bodyAirY / (1.0f + 0.065f * Math.abs(bodyAirY));

    float omega = smoothOmega;
    float effOmega = omega / (1.0f + 0.10f * Math.abs(omega));
    float effOmegaSq = Math.min(15.0f, effOmega * effOmega);

    float gust = 0.60f
        + 0.26f * (float) Math.sin(simTime * 0.67f + entityPhase)
        + 0.14f * (float) Math.cos(simTime * 1.13f + entityPhase * 2.3f);

    float strideX = (float) Math.sin(pose.walkPos) * pose.walkSpeed * 0.75f;
    float strideZ = (0.5f + 0.5f * (float) Math.cos(pose.walkPos * 2.0f)) * pose.walkSpeed * 0.55f;

    float dt2 = dt * dt;
    float invDt = 1.0f / dt;
    float bodyCenterX = pose.bodyX / 16.0f;
    float bodyCenterZ = pose.bodyZ / 16.0f;

    for (int r = 1; r < rows; r++) {
      float vFrac = r / (float) (rows - 1);
      float hemW = rowHemW[r];
      for (int c = 0; c < cols; c++) {
        int i = r * cols + c;
        float uFrac = c / (float) (cols - 1);
        float edgeFactor = Math.abs(uFrac - 0.5f) * 2.0f;

        float curX = x[i], curY = y[i], curZ = z[i];
        float pvx = (curX - px[i]) * invDt;
        float pvy = (curY - py[i]) * invDt;
        float pvz = (curZ - pz[i]) * invDt;

        float fx = gx;
        float fy = gy;
        float fz = gz;

        fx += -clamp(accelLeft, -12.0f, 12.0f) * 0.16f * vFrac;
        fy += clamp(accelUp, -12.0f, 12.0f) * 0.12f * vFrac;
        fz += clamp(accelForward, -12.0f, 12.0f) * 0.18f * vFrac;

        float turnRamp = (0.14f + 0.86f * vFrac) * Math.min(1.0f, vFrac * 2.2f);
        float relX = curX - bodyCenterX;
        float relZ = curZ - bodyCenterZ;
        float leverZ = Math.max(0.24f, relZ + 0.16f * vFrac);
        fx += -effOmega * leverZ * 4.6f * turnK * turnRamp;
        fz += effOmega * relX * 0.65f * turnK * turnRamp;
        float cent = effOmegaSq * turnK * (0.16f + 0.84f * vFrac);
        fz += cent * 0.42f;
        fx += relX * cent * 0.32f;

        float vnx = nx[i], vny = ny[i], vnz = nz[i];
        float rvx = effAirX - pvx * 0.32f;
        float rvy = effAirY - pvy * 0.32f;
        float rvz = effAirZ - pvz * 0.32f;
        float vn = rvx * vnx + rvy * vny + rvz * vnz;
        float pressure = vn * (0.95f + 0.28f * Math.abs(vn)) * dragK;
        fx += vnx * pressure * 0.88f;
        fy += vny * pressure * 0.50f;
        fz += vnz * pressure;

        if (effAirZ > 0.0f) {
          fz += effAirZ * (1.12f + 0.48f * vFrac) * dragK;
          fy -= effAirZ * (0.14f + 0.16f * vFrac) * dragK;
          fz += effAirZ * 0.34f * (1.0f - edgeFactor) * vFrac * dragK;
        } else if (effAirZ < 0.0f) {
          float backAmt = -effAirZ;
          fx += (uFrac < 0.5f ? 1.0f : -1.0f) * backAmt * 0.36f * edgeFactor * vFrac * dragK;
        }

        if (effAirX != 0.0f) {
          fx += effAirX * (1.85f + 0.80f * vFrac) * dragK;
          fz += Math.abs(effAirX) * 0.52f * vFrac * dragK;
        }

        if (effAirY < -0.35f) {
          float fallAmt = Math.min(10.5f, -effAirY);
          float fallSat = fallAmt / (1.0f + 0.09f * fallAmt);
          fy -= fallSat * (1.58f + 0.36f * vFrac) * dragK;
          fz += fallSat * (2.05f + 0.65f * vFrac) * dragK;
          float aboveShoulder = (shoulderY + 0.04f) - curY;
          if (aboveShoulder > 0.0f) {
            fy += aboveShoulder * (9.5f + 8.5f * aboveShoulder);
            fz += aboveShoulder * 5.0f;
          }
          float fallWave = (float) Math.sin(
              simTime * 11.4f - vFrac * 6.8f + uFrac * 3.8f + entityPhase);
          float fallWaveY = (float) Math.cos(
              simTime * 9.6f - vFrac * 5.8f - uFrac * 2.4f + entityPhase * 1.3f);
          float fallWaveX = (float) Math.cos(
              simTime * 8.4f + vFrac * 5.0f - uFrac * 3.0f + entityPhase);
          fz += fallWave * fallSat * 0.58f * flutter * vFrac;
          fy += fallWaveY * fallSat * 0.68f * flutter * (0.25f + 0.75f * vFrac);
          fx += fallWaveX * fallSat * 0.28f * flutter * vFrac;
        } else if (effAirY > 0.35f) {
          fy += Math.min(7.5f, effAirY) * 0.85f * dragK;
        }

        if (speedNorm > 0.03f && flutter > 0.01f) {
          float sp1 = simTime * (5.2f + speedNorm * 5.6f) - vFrac * (5.2f + speedNorm * 2.1f)
              + uFrac * 2.5f + entityPhase;
          float sp2 = simTime * (8.5f + speedNorm * 6.4f) - vFrac * 8.8f - uFrac * 3.6f
              + entityPhase * 1.7f;
          float turbZ = flutter * speedNorm
              * (0.64f * (float) Math.sin(sp1) + 0.36f * (float) Math.sin(sp2))
              * (0.28f + 0.72f * vFrac);
          float turbY = flutter * speedNorm
              * (0.58f * (float) Math.cos(sp1) + 0.32f * (float) Math.cos(sp2))
              * (0.24f + 0.76f * vFrac);
          float turbX = flutter * speedNorm * 0.38f
              * (float) Math.cos(sp1 * 0.82f + uFrac * 3.0f) * vFrac;
          fz += turbZ * 3.85f * dragK;
          fy += turbY * 2.15f * dragK;
          fx += turbX * 2.25f * dragK;
        }

        float w1 = (float) Math.sin(
            simTime * (2.5f + 0.85f * windAmp) - vFrac * 4.3f + uFrac * 2.3f + entityPhase);
        float w2 = (float) Math.sin(
            simTime * (4.6f + 1.25f * windAmp) - vFrac * 7.4f - uFrac * 3.2f + entityPhase * 1.4f);
        float wEdge = (float) Math.sin(
            simTime * 1.75f + uFrac * 4.0f - vFrac * 2.4f + entityPhase * 0.7f);
        if (underwater) {
          float uw = (float) Math.sin(simTime * 1.65f - vFrac * 3.8f + uFrac * 2.1f + entityPhase);
          float uw2 = (float) Math.cos(simTime * 1.25f - vFrac * 3.0f + entityPhase);
          fz += (0.85f + 0.72f * uw) * (0.30f + 0.70f * hemW);
          fy += uw2 * 0.55f * hemW;
          fx += uw2 * 0.75f * hemW;
        } else {
          float breezeBase = windAmp * gust * (0.42f + 0.58f * hemW);
          fz += breezeBase * 2.40f
              + windAmp * flutter * (0.72f * w1 + 0.38f * w2) * 3.15f * (0.25f + 0.75f * hemW);
          fx += windAmp * (0.58f * wEdge + 0.35f * flutter * w2) * 1.55f * hemW;
        }
        fz += 0.34f * (0.28f + 0.72f * vFrac);

        if (pose.walkSpeed > 0.04f) {
          fx += strideX * vFrac;
          fz += strideZ * vFrac;
        }

        float vdx = (curX - px[i]) * velKeep;
        float vdy = (curY - py[i]) * velKeep;
        float vdz = (curZ - pz[i]) * velKeep;
        float vMagSq = vdx * vdx + vdy * vdy + vdz * vdz;
        final float maxStep = 0.085f;
        if (vMagSq > maxStep * maxStep) {
          float vScale = maxStep / (float) Math.sqrt(vMagSq);
          vdx *= vScale;
          vdy *= vScale;
          vdz *= vScale;
        }
        float nextX = curX + vdx + fx * dt2;
        float nextY = curY + vdy + fy * dt2;
        float nextZ = curZ + vdz + fz * dt2;

        px[i] = curX;
        py[i] = curY;
        pz[i] = curZ;
        x[i] = nextX;
        y[i] = nextY;
        z[i] = nextZ;
      }
    }

    for (int iter = 0; iter < CONSTRAINT_ITERS; iter++) {
      boolean runCollision = config.bodyCollision && (iter >= CONSTRAINT_ITERS - 2);
      satisfyConstraints(pose, config, runCollision);
    }

    enforceMaxVerticalStretch();
    if (config.bodyCollision) {
      applyBodyAndLegCollision(pose, config);
    }
    pinTopRow();
  }

  private void pinTopRow() {
    for (int c = 0; c < cols; c++) {
      x[c] = pinX[c];
      y[c] = pinY[c];
      z[c] = pinZ[c];
      px[c] = pinX[c];
      py[c] = pinY[c];
      pz[c] = pinZ[c];
    }
  }

  private void satisfyConstraints(WavyCapePose pose, WavyCapeConfig config, boolean collide) {
    pinTopRow();

    float bendK = 0.07f + config.stiffness * 0.66f;
    float horizBendK = bendK * 0.85f;
    float shearK = 0.68f;

    for (int r = 0; r < rows; r++) {
      float sxRow = rowSegX[r];
      float sxRowNext = r + 1 < rows ? 0.5f * (sxRow + rowSegX[r + 1]) : sxRow;
      float diagRest = (float) Math.sqrt(sxRowNext * sxRowNext + segY * segY);
      int rowOff = r * cols;

      for (int c = 0; c < cols; c++) {
        int i = rowOff + c;
        boolean pinnedA = (r == 0);

        if (c + 1 < cols && r > 0) {
          solveLink(i, i + 1, sxRow, false, 0.92f);
        }
        if (r + 1 < rows) {
          solveLink(i, i + cols, segY, pinnedA, 1.0f);
        }
        if (r + 1 < rows && c + 1 < cols) {
          solveLink(i, i + cols + 1, diagRest, pinnedA, shearK);
        }
        if (r + 1 < rows && c > 0) {
          solveLink(i, i + cols - 1, diagRest, pinnedA, shearK);
        }
        if (r + 2 < rows) {
          solveLink(i, i + cols * 2, segY * 2.0f, pinnedA, bendK);
        }
        if (c + 2 < cols && r > 0) {
          float yokeBend = r <= 5 ? Math.min(0.88f, horizBendK + 0.24f) : horizBendK;
          solveLink(i, i + 2, sxRow * 2.0f, false, yokeBend);
        }
      }

      if (r > 0) {
        float minRatio = r <= 4 ? 0.56f : 0.40f;
        float minDx = sxRow * minRatio;
        for (int c = 0; c < cols - 1; c++) {
          int a = rowOff + c;
          int b = a + 1;
          float dx = x[a] - x[b];
          if (dx < minDx) {
            float push = 0.5f * (minDx - dx);
            x[a] += push;
            x[b] -= push;
          }
        }
      }
    }

    if (collide) {
      applyBodyAndLegCollision(pose, config);
    }
  }

  private void solveLink(int a, int b, float rest, boolean aPinned, float stiffness) {
    float dx = x[b] - x[a];
    float dy = y[b] - y[a];
    float dz = z[b] - z[a];
    float distSq = dx * dx + dy * dy + dz * dz;
    if (distSq < 1e-10f) return;
    float dist = (float) Math.sqrt(distSq);
    float diff = (dist - rest) / dist * stiffness;
    if (aPinned) {
      x[b] -= dx * diff;
      y[b] -= dy * diff;
      z[b] -= dz * diff;
    } else {
      float half = diff * 0.5f;
      x[a] += dx * half;
      y[a] += dy * half;
      z[a] += dz * half;
      x[b] -= dx * half;
      y[b] -= dy * half;
      z[b] -= dz * half;
    }
  }

  private void absorbPoseTransition(WavyCapePose pose, WavyCapeConfig config) {
    float dPitch = Math.abs(pose.bodyXRot - prevBodyXRot);
    float dSwim = Math.abs(pose.swimAmount - prevSwimAmount);
    float dBodyY = (pose.bodyY - prevBodyY) / 16.0f;
    prevBodyXRot = pose.bodyXRot;
    prevBodyY = pose.bodyY;
    prevSwimAmount = pose.swimAmount;

    if (dPitch > 0.10f || dSwim > 0.10f || Math.abs(dBodyY) > 0.08f) {
      for (int i = cols; i < count; i++) {
        y[i] += dBodyY;
        py[i] += dBodyY;
        px[i] = x[i] - (x[i] - px[i]) * 0.30f;
        py[i] = y[i] - (y[i] - py[i]) * 0.30f;
        pz[i] = z[i] - (z[i] - pz[i]) * 0.30f;
      }
      if (config.bodyCollision) {
        applyBodyAndLegCollision(pose, config);
      }
      for (int iter = 0; iter < 2; iter++) {
        enforceMaxVerticalStretch();
      }
      for (int i = cols; i < count; i++) {
        px[i] = x[i] - (x[i] - px[i]) * 0.25f;
        py[i] = y[i] - (y[i] - py[i]) * 0.25f;
        pz[i] = z[i] - (z[i] - pz[i]) * 0.25f;
      }
    }
  }

  private void enforceMaxVerticalStretch() {
    float maxLen = segY * 1.018f;
    float maxLenSq = maxLen * maxLen;
    for (int r = 0; r < rows - 1; r++) {
      int rowOff = r * cols;
      for (int c = 0; c < cols; c++) {
        int a = rowOff + c;
        int b = a + cols;
        float dx = x[b] - x[a];
        float dy = y[b] - y[a];
        float dz = z[b] - z[a];
        float dSq = dx * dx + dy * dy + dz * dz;
        if (dSq > maxLenSq) {
          float scale = maxLen / (float) Math.sqrt(dSq);
          float nxB = x[a] + dx * scale;
          float nyB = y[a] + dy * scale;
          float nzB = z[a] + dz * scale;
          px[b] += (nxB - x[b]) * 0.75f;
          py[b] += (nyB - y[b]) * 0.75f;
          pz[b] += (nzB - z[b]) * 0.75f;
          x[b] = nxB;
          y[b] = nyB;
          z[b] = nzB;
        }
      }
    }
  }

  private void applyBodyAndLegCollision(WavyCapePose pose, WavyCapeConfig config) {
    float torsoSpanY = Math.max(0.20f, waistY - shoulderY);
    float maxUpY = shoulderY - 0.72f * BASE_LENGTH * config.lengthScale;
    float headHalfW = 0.295f;
    float headBackZ = Math.max(shoulderZ + 0.025f, 0.320f);

    float legRadius = 0.148f + (pose.hasChestArmor ? 0.016f : 0.0f);
    float legLen = 0.72f;
    float rHipX = -2.0f / 16.0f;
    float rHipY = pose.rightLegY / 16.0f;
    float rHipZ = pose.rightLegZ / 16.0f;
    float rDirY = (float) Math.cos(pose.rightLegXRot);
    float rDirZ = (float) Math.sin(pose.rightLegXRot);

    float lHipX = 2.0f / 16.0f;
    float lHipY = pose.leftLegY / 16.0f;
    float lHipZ = pose.leftLegZ / 16.0f;
    float lDirY = (float) Math.cos(pose.leftLegXRot);
    float lDirZ = (float) Math.sin(pose.leftLegXRot);

    float maxReach = BASE_LENGTH * config.lengthScale * 1.18f;

    for (int r = 1; r < rows; r++) {
      int rowOff = r * cols;
      for (int c = 0; c < cols; c++) {
        int i = rowOff + c;
        float vx = x[i], vy = y[i], vz = z[i];
        float oldY = vy, oldZ = vz;

        if (vy < maxUpY) {
          vy = maxUpY;
        }
        if (vy < shoulderY + 0.06f) {
          float neckT = clamp((shoulderY + 0.06f - vy) / 0.16f, 0.0f, 1.0f);
          float reqZ = shoulderZ + (headBackZ - shoulderZ) * neckT;
          float ax = Math.abs(vx);
          if (ax < headHalfW + 0.10f) {
            float xBlend = ax <= headHalfW ? 1.0f : 1.0f - (ax - headHalfW) / 0.10f;
            float minZ = shoulderZ + (reqZ - shoulderZ) * xBlend;
            if (vz < minZ) {
              vz = minZ;
            }
          } else if (vz < shoulderZ - 0.01f) {
            vz = shoulderZ - 0.01f;
          }
        }

        if (vy >= shoulderY - 0.06f && vy <= waistY + 0.12f) {
          float t = clamp((vy - shoulderY) / torsoSpanY, 0.0f, 1.08f);
          float backZ = shoulderZ + t * (waistZ - shoulderZ) + 0.008f;
          float ax = Math.abs(vx);
          if (ax < torsoHalfW + 0.06f) {
            float blend = ax <= torsoHalfW ? 1.0f : 1.0f - (ax - torsoHalfW) / 0.06f;
            float minZ = backZ * blend + (backZ - 0.12f) * (1.0f - blend);
            if (vz < minZ) {
              vz = minZ;
            }
          }
        }

        if (vy > waistY - 0.12f) {
          vz = pushBehindLeg(vx, vy, vz, rHipX, rHipY, rHipZ, rDirY, rDirZ, legLen, legRadius);
          vz = pushBehindLeg(vx, vy, vz, lHipX, lHipY, lHipZ, lDirY, lDirZ, legLen, legRadius);
        }

        float dzMax = shoulderZ + maxReach;
        if (vz > dzMax) vz = dzMax;
        if (vx < -maxReach) vx = -maxReach;
        else if (vx > maxReach) vx = maxReach;

        x[i] = vx;
        y[i] = vy;
        z[i] = vz;
        if (vz > oldZ) {
          pz[i] += (vz - oldZ) * 0.92f;
        }
        if (vy != oldY) {
          py[i] += (vy - oldY) * 0.92f;
        }
      }
    }
  }

  private static float pushBehindLeg(float vx, float vy, float vz, float hipX, float hipY,
      float hipZ, float dirY, float dirZ, float legLen, float radius) {
    float dx = vx - hipX;
    if (Math.abs(dx) >= radius) return vz;
    float dy = vy - hipY;
    float dz = vz - hipZ;
    float t = dy * dirY + dz * dirZ;
    if (t < -0.04f || t > legLen + 0.06f) return vz;
    float tc = clamp(t, 0.0f, legLen);
    float qy = hipY + tc * dirY;
    float qz = hipZ + tc * dirZ;
    float distY = vy - qy;
    float distZ = vz - qz;
    float distSq = dx * dx + distY * distY + distZ * distZ;
    if (distSq >= radius * radius) return vz;
    float remSq = radius * radius - dx * dx - distY * distY * 0.45f;
    if (remSq <= 0.0f) return vz;
    float targetZ = qz + (float) Math.sqrt(remSq) + 0.010f;
    return vz < targetZ ? targetZ : vz;
  }

  private void interpolateRenderPositions(float alpha) {
    if (alpha >= 0.999f) {
      System.arraycopy(x, 0, rx, 0, count);
      System.arraycopy(y, 0, ry, 0, count);
      System.arraycopy(z, 0, rz, 0, count);
      return;
    }
    float inv = 1.0f - alpha;
    for (int i = 0; i < count; i++) {
      rx[i] = sx[i] * inv + x[i] * alpha;
      ry[i] = sy[i] * inv + y[i] * alpha;
      rz[i] = sz[i] * inv + z[i] * alpha;
    }
    for (int c = 0; c < cols; c++) {
      rx[c] = pinX[c];
      ry[c] = pinY[c];
      rz[c] = pinZ[c];
    }
  }

  private void computeVertexNormals(float[] gx, float[] gy, float[] gz) {
    for (int r = 0; r < rows; r++) {
      int rPrev = r > 0 ? r - 1 : 0;
      int rNext = r + 1 < rows ? r + 1 : rows - 1;
      for (int c = 0; c < cols; c++) {
        int cPrev = c > 0 ? c - 1 : 0;
        int cNext = c + 1 < cols ? c + 1 : cols - 1;
        int i = r * cols + c;
        int iL = r * cols + cPrev;
        int iR = r * cols + cNext;
        int iT = rPrev * cols + c;
        int iB = rNext * cols + c;

        float ux = gx[iR] - gx[iL], uy = gy[iR] - gy[iL], uz = gz[iR] - gz[iL];
        float vx = gx[iB] - gx[iT], vy = gy[iB] - gy[iT], vz = gz[iB] - gz[iT];

        float fnx = vy * uz - vz * uy;
        float fny = vz * ux - vx * uz;
        float fnz = vx * uy - vy * ux;
        float len = (float) Math.sqrt(fnx * fnx + fny * fny + fnz * fnz);
        if (len < 1e-6f) {
          nx[i] = 0.0f;
          ny[i] = 0.0f;
          nz[i] = 1.0f;
        } else {
          float inv = 1.0f / len;
          nx[i] = fnx * inv;
          ny[i] = fny * inv;
          nz[i] = fnz * inv;
        }
      }
    }
  }

  private void computeFoldShading(WavyCapeConfig config) {
    float strength = config.foldShading;
    if (strength <= 0.001f) {
      for (int i = 0; i < count; i++) vShade[i] = 1.0f;
      return;
    }
    float segSq = segY * segY + rowSegX[0] * rowSegX[0];
    float invSegSq = segSq > 1e-6f ? 1.0f / segSq : 1.0f;

    for (int r = 0; r < rows; r++) {
      float collarAo = r == 0 ? 0.08f : (r == 1 ? 0.04f : 0.0f);
      int rPrev = r > 0 ? r - 1 : 0;
      int rNext = r + 1 < rows ? r + 1 : rows - 1;
      for (int c = 0; c < cols; c++) {
        int i = r * cols + c;
        int cPrev = c > 0 ? c - 1 : 0;
        int cNext = c + 1 < cols ? c + 1 : cols - 1;
        int iL = r * cols + cPrev;
        int iR = r * cols + cNext;
        int iT = rPrev * cols + c;
        int iB = rNext * cols + c;

        float lx = 0.25f * (rx[iL] + rx[iR] + rx[iT] + rx[iB]) - rx[i];
        float ly = 0.25f * (ry[iL] + ry[iR] + ry[iT] + ry[iB]) - ry[i];
        float lz = 0.25f * (rz[iL] + rz[iR] + rz[iT] + rz[iB]) - rz[i];
        float curvature = (lx * nx[i] + ly * ny[i] + lz * nz[i]) * invSegSq;
        float creaseShadow = clamp(curvature * 0.085f, -0.02f, 0.26f);
        float shade = 1.0f - strength * (Math.max(0.0f, creaseShadow) + collarAo);
        vShade[i] = clamp(shade, 0.70f, 1.0f);
      }
    }
  }

  private void buildMesh(WavyCapeConfig config) {
    computeVertexNormals(rx, ry, rz);
    computeFoldShading(config);

    if (WavyCapeConfig.STYLE_BLOCKY.equals(config.style)) {
      buildBlockyMesh(config);
    } else {
      buildContinuousMesh(config);
    }
  }

  private void buildContinuousMesh(WavyCapeConfig config) {
    float thickness = config.effectiveThickness();
    for (int i = 0; i < count; i++) {
      ox[i] = rx[i] + nx[i] * thickness;
      oy[i] = ry[i] + ny[i] * thickness;
      oz[i] = rz[i] + nz[i] * thickness;
    }

    int gridQuads = (cols - 1) * (rows - 1);
    int totalQuads = gridQuads * 2 + (rows - 1) * 2 + (cols - 1) * 2;
    mesh.reset(totalQuads);

    boolean smoothNormals = !WavyCapeConfig.STYLE_CRISP.equals(config.style);
    float innerDim = 1.0f - 0.08f * config.foldShading;
    float invCols = 1.0f / (cols - 1);
    float invRows = 1.0f / (rows - 1);

    for (int r = 0; r < rows - 1; r++) {
      float v0 = (1.0f + r * invRows * 16.0f) / 32.0f;
      float v1 = (1.0f + (r + 1) * invRows * 16.0f) / 32.0f;
      int row0 = r * cols;
      int row1 = row0 + cols;
      for (int c = 0; c < cols - 1; c++) {
        float u0 = (1.0f + c * invCols * 10.0f) / 64.0f;
        float u1 = (1.0f + (c + 1) * invCols * 10.0f) / 64.0f;
        int tl = row0 + c;
        int tr = tl + 1;
        int bl = row1 + c;
        int br = bl + 1;
        if (smoothNormals) {
          mesh.addQuad(
              ox[tr], oy[tr], oz[tr], nx[tr], ny[tr], nz[tr], u1, v0, vShade[tr],
              ox[tl], oy[tl], oz[tl], nx[tl], ny[tl], nz[tl], u0, v0, vShade[tl],
              ox[bl], oy[bl], oz[bl], nx[bl], ny[bl], nz[bl], u0, v1, vShade[bl],
              ox[br], oy[br], oz[br], nx[br], ny[br], nz[br], u1, v1, vShade[br]);
        } else {
          mesh.addFlatQuad(
              ox[tr], oy[tr], oz[tr], u1, v0, vShade[tr],
              ox[tl], oy[tl], oz[tl], u0, v0, vShade[tl],
              ox[bl], oy[bl], oz[bl], u0, v1, vShade[bl],
              ox[br], oy[br], oz[br], u1, v1, vShade[br],
              0.0f, 0.0f, 1.0f);
        }
      }
    }

    for (int r = 0; r < rows - 1; r++) {
      float v0 = (1.0f + r * invRows * 16.0f) / 32.0f;
      float v1 = (1.0f + (r + 1) * invRows * 16.0f) / 32.0f;
      int row0 = r * cols;
      int row1 = row0 + cols;
      for (int c = 0; c < cols - 1; c++) {
        float u0 = (22.0f - c * invCols * 10.0f) / 64.0f;
        float u1 = (22.0f - (c + 1) * invCols * 10.0f) / 64.0f;
        int tl = row0 + c;
        int tr = tl + 1;
        int bl = row1 + c;
        int br = bl + 1;
        float sTL = vShade[tl] * innerDim;
        float sTR = vShade[tr] * innerDim;
        float sBL = vShade[bl] * innerDim;
        float sBR = vShade[br] * innerDim;
        if (smoothNormals) {
          mesh.addQuad(
              rx[tl], ry[tl], rz[tl], -nx[tl], -ny[tl], -nz[tl], u0, v0, sTL,
              rx[tr], ry[tr], rz[tr], -nx[tr], -ny[tr], -nz[tr], u1, v0, sTR,
              rx[br], ry[br], rz[br], -nx[br], -ny[br], -nz[br], u1, v1, sBR,
              rx[bl], ry[bl], rz[bl], -nx[bl], -ny[bl], -nz[bl], u0, v1, sBL);
        } else {
          mesh.addFlatQuad(
              rx[tl], ry[tl], rz[tl], u0, v0, sTL,
              rx[tr], ry[tr], rz[tr], u1, v0, sTR,
              rx[br], ry[br], rz[br], u1, v1, sBR,
              rx[bl], ry[bl], rz[bl], u0, v1, sBL,
              0.0f, 0.0f, -1.0f);
        }
      }
    }

    float uLeftIn = 0.15f / 64.0f;
    float uLeftOut = 0.85f / 64.0f;
    for (int r = 0; r < rows - 1; r++) {
      float v0 = (1.05f + r * invRows * 15.9f) / 32.0f;
      float v1 = (1.05f + (r + 1) * invRows * 15.9f) / 32.0f;
      int top = r * cols;
      int bot = top + cols;
      mesh.addFlatQuad(
          ox[top], oy[top], oz[top], uLeftOut, v0, vShade[top],
          rx[top], ry[top], rz[top], uLeftIn, v0, vShade[top],
          rx[bot], ry[bot], rz[bot], uLeftIn, v1, vShade[bot],
          ox[bot], oy[bot], oz[bot], uLeftOut, v1, vShade[bot],
          1.0f, 0.0f, 0.0f);
    }

    int lastCol = cols - 1;
    float uRightOut = 11.15f / 64.0f;
    float uRightIn = 11.85f / 64.0f;
    for (int r = 0; r < rows - 1; r++) {
      float v0 = (1.05f + r * invRows * 15.9f) / 32.0f;
      float v1 = (1.05f + (r + 1) * invRows * 15.9f) / 32.0f;
      int top = r * cols + lastCol;
      int bot = top + cols;
      mesh.addFlatQuad(
          rx[top], ry[top], rz[top], uRightIn, v0, vShade[top],
          ox[top], oy[top], oz[top], uRightOut, v0, vShade[top],
          ox[bot], oy[bot], oz[bot], uRightOut, v1, vShade[bot],
          rx[bot], ry[bot], rz[bot], uRightIn, v1, vShade[bot],
          -1.0f, 0.0f, 0.0f);
    }

    float vTopIn = 0.15f / 32.0f;
    float vTopOut = 0.85f / 32.0f;
    for (int c = 0; c < cols - 1; c++) {
      float u0 = (1.05f + c * invCols * 9.9f) / 64.0f;
      float u1 = (1.05f + (c + 1) * invCols * 9.9f) / 64.0f;
      int tl = c;
      int tr = c + 1;
      mesh.addFlatQuad(
          rx[tr], ry[tr], rz[tr], u1, vTopIn, vShade[tr],
          rx[tl], ry[tl], rz[tl], u0, vTopIn, vShade[tl],
          ox[tl], oy[tl], oz[tl], u0, vTopOut, vShade[tl],
          ox[tr], oy[tr], oz[tr], u1, vTopOut, vShade[tr],
          0.0f, -1.0f, 0.0f);
    }

    int lastRowOff = (rows - 1) * cols;
    for (int c = 0; c < cols - 1; c++) {
      float u0 = (11.05f + c * invCols * 9.9f) / 64.0f;
      float u1 = (11.05f + (c + 1) * invCols * 9.9f) / 64.0f;
      int bl = lastRowOff + c;
      int br = bl + 1;
      mesh.addFlatQuad(
          ox[br], oy[br], oz[br], u1, vTopOut, vShade[br],
          ox[bl], oy[bl], oz[bl], u0, vTopOut, vShade[bl],
          rx[bl], ry[bl], rz[bl], u0, vTopIn, vShade[bl],
          rx[br], ry[br], rz[br], u1, vTopIn, vShade[br],
          0.0f, 1.0f, 0.0f);
    }
  }

  private void buildBlockyMesh(WavyCapeConfig config) {
    final int bands = 16;
    mesh.reset(bands * 4 + 2);
    float thickness = config.effectiveThickness();
    float innerDim = 1.0f - 0.08f * config.foldShading;

    for (int sIdx = 0; sIdx <= bands; sIdx++) {
      float vFrac = sIdx / (float) bands;
      blkLx[sIdx] = sampleGrid(rx, 0, vFrac);
      blkLy[sIdx] = sampleGrid(ry, 0, vFrac);
      blkLz[sIdx] = sampleGrid(rz, 0, vFrac);
      blkRx[sIdx] = sampleGrid(rx, cols - 1, vFrac);
      blkRy[sIdx] = sampleGrid(ry, cols - 1, vFrac);
      blkRz[sIdx] = sampleGrid(rz, cols - 1, vFrac);
    }

    for (int b = 0; b < bands; b++) {
      float ux = 0.5f * ((blkRx[b] - blkLx[b]) + (blkRx[b + 1] - blkLx[b + 1]));
      float uy = 0.5f * ((blkRy[b] - blkLy[b]) + (blkRy[b + 1] - blkLy[b + 1]));
      float uz = 0.5f * ((blkRz[b] - blkLz[b]) + (blkRz[b + 1] - blkLz[b + 1]));
      float vx = 0.5f * ((blkLx[b + 1] - blkLx[b]) + (blkRx[b + 1] - blkRx[b]));
      float vy = 0.5f * ((blkLy[b + 1] - blkLy[b]) + (blkRy[b + 1] - blkRy[b]));
      float vz = 0.5f * ((blkLz[b + 1] - blkLz[b]) + (blkRz[b + 1] - blkRz[b]));
      float bnx = vy * uz - vz * uy;
      float bny = vz * ux - vx * uz;
      float bnz = vx * uy - vy * ux;
      float len = (float) Math.sqrt(bnx * bnx + bny * bny + bnz * bnz);
      if (len < 1e-6f) {
        blkBx[b] = 0.0f; blkBy[b] = 0.0f; blkBz[b] = 1.0f;
      } else {
        float inv = 1.0f / len;
        blkBx[b] = bnx * inv; blkBy[b] = bny * inv; blkBz[b] = bnz * inv;
      }
    }

    for (int sIdx = 0; sIdx <= bands; sIdx++) {
      float snx, sny, snz;
      if (sIdx == 0) {
        snx = blkBx[0]; sny = blkBy[0]; snz = blkBz[0];
      } else if (sIdx == bands) {
        snx = blkBx[bands - 1]; sny = blkBy[bands - 1]; snz = blkBz[bands - 1];
      } else {
        snx = blkBx[sIdx - 1] + blkBx[sIdx];
        sny = blkBy[sIdx - 1] + blkBy[sIdx];
        snz = blkBz[sIdx - 1] + blkBz[sIdx];
        float len = (float) Math.sqrt(snx * snx + sny * sny + snz * snz);
        if (len < 1e-6f) {
          snx = blkBx[sIdx]; sny = blkBy[sIdx]; snz = blkBz[sIdx];
        } else {
          float inv = 1.0f / len;
          snx *= inv; sny *= inv; snz *= inv;
        }
      }
      float oxOff = snx * thickness, oyOff = sny * thickness, ozOff = snz * thickness;
      blkOlx[sIdx] = blkLx[sIdx] + oxOff;
      blkOly[sIdx] = blkLy[sIdx] + oyOff;
      blkOlz[sIdx] = blkLz[sIdx] + ozOff;
      blkOrx[sIdx] = blkRx[sIdx] + oxOff;
      blkOry[sIdx] = blkRy[sIdx] + oyOff;
      blkOrz[sIdx] = blkRz[sIdx] + ozOff;
    }

    for (int b = 0; b < bands; b++) {
      float vMid = (b + 0.5f) / bands;
      float s = sampleGrid(vShade, cols / 2, vMid);
      float sIn = s * innerDim;

      float v0 = (1.0f + b) / 32.0f;
      float v1 = (2.0f + b) / 32.0f;
      float vRim0 = (1.05f + b) / 32.0f;
      float vRim1 = (1.95f + b) / 32.0f;

      float ltx = blkLx[b], lty = blkLy[b], ltz = blkLz[b];
      float rtx = blkRx[b], rty = blkRy[b], rtz = blkRz[b];
      float lbx = blkLx[b + 1], lby = blkLy[b + 1], lbz = blkLz[b + 1];
      float rbx = blkRx[b + 1], rby = blkRy[b + 1], rbz = blkRz[b + 1];

      float oltx = blkOlx[b], olty = blkOly[b], oltz = blkOlz[b];
      float ortx = blkOrx[b], orty = blkOry[b], ortz = blkOrz[b];
      float olbx = blkOlx[b + 1], olby = blkOly[b + 1], olbz = blkOlz[b + 1];
      float orbx = blkOrx[b + 1], orby = blkOry[b + 1], orbz = blkOrz[b + 1];

      mesh.addFlatQuad(
          ortx, orty, ortz, 11.0f / 64.0f, v0, s,
          oltx, olty, oltz, 1.0f / 64.0f, v0, s,
          olbx, olby, olbz, 1.0f / 64.0f, v1, s,
          orbx, orby, orbz, 11.0f / 64.0f, v1, s,
          blkBx[b], blkBy[b], blkBz[b]);
      mesh.addFlatQuad(
          ltx, lty, ltz, 22.0f / 64.0f, v0, sIn,
          rtx, rty, rtz, 12.0f / 64.0f, v0, sIn,
          rbx, rby, rbz, 12.0f / 64.0f, v1, sIn,
          lbx, lby, lbz, 22.0f / 64.0f, v1, sIn,
          -blkBx[b], -blkBy[b], -blkBz[b]);
      mesh.addFlatQuad(
          oltx, olty, oltz, 0.85f / 64.0f, vRim0, s,
          ltx, lty, ltz, 0.15f / 64.0f, vRim0, s,
          lbx, lby, lbz, 0.15f / 64.0f, vRim1, s,
          olbx, olby, olbz, 0.85f / 64.0f, vRim1, s,
          1.0f, 0.0f, 0.0f);
      mesh.addFlatQuad(
          rtx, rty, rtz, 11.85f / 64.0f, vRim0, s,
          ortx, orty, ortz, 11.15f / 64.0f, vRim0, s,
          orbx, orby, orbz, 11.15f / 64.0f, vRim1, s,
          rbx, rby, rbz, 11.85f / 64.0f, vRim1, s,
          -1.0f, 0.0f, 0.0f);
    }

    float topShade = sampleGrid(vShade, cols / 2, 0.0f);
    mesh.addFlatQuad(
        blkRx[0], blkRy[0], blkRz[0], 10.95f / 64.0f, 0.15f / 32.0f, topShade,
        blkLx[0], blkLy[0], blkLz[0], 1.05f / 64.0f, 0.15f / 32.0f, topShade,
        blkOlx[0], blkOly[0], blkOlz[0], 1.05f / 64.0f, 0.85f / 32.0f, topShade,
        blkOrx[0], blkOry[0], blkOrz[0], 10.95f / 64.0f, 0.85f / 32.0f, topShade,
        0.0f, -1.0f, 0.0f);

    float botShade = sampleGrid(vShade, cols / 2, 1.0f);
    mesh.addFlatQuad(
        blkOrx[bands], blkOry[bands], blkOrz[bands], 20.95f / 64.0f, 0.85f / 32.0f, botShade,
        blkOlx[bands], blkOly[bands], blkOlz[bands], 11.05f / 64.0f, 0.85f / 32.0f, botShade,
        blkLx[bands], blkLy[bands], blkLz[bands], 11.05f / 64.0f, 0.15f / 32.0f, botShade,
        blkRx[bands], blkRy[bands], blkRz[bands], 20.95f / 64.0f, 0.15f / 32.0f, botShade,
        0.0f, 1.0f, 0.0f);
  }

  private float sampleGrid(float[] arr, int col, float vFrac) {
    float f = clamp(vFrac, 0.0f, 1.0f) * (rows - 1);
    int r0 = (int) f;
    int r1 = Math.min(rows - 1, r0 + 1);
    float t = f - r0;
    return arr[r0 * cols + col] * (1.0f - t) + arr[r1 * cols + col] * t;
  }

  private static float wrapDegrees(float deg) {
    float v = deg % 360.0f;
    if (v >= 180.0f) v -= 360.0f;
    if (v < -180.0f) v += 360.0f;
    return v;
  }

  private static float clamp(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
  }
}
