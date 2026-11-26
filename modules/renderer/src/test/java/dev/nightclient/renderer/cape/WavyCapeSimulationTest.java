package dev.nightclient.renderer.cape;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

class WavyCapeSimulationTest {
  private static WavyCapePose basePose(long timeNanos) {
    WavyCapePose p = new WavyCapePose();
    p.entityId = 42;
    p.worldX = 10.0;
    p.worldY = 64.0;
    p.worldZ = 20.0;
    p.bodyRot = 0.0f;
    p.timeNanos = timeNanos;
    return p;
  }

  @Test
  void allStylesAndQualitiesBuildWatertightFiniteMeshesWithOutwardWinding() {
    String[] styles = {
      WavyCapeConfig.STYLE_SMOOTH, WavyCapeConfig.STYLE_CRISP,
      WavyCapeConfig.STYLE_BLOCKY, WavyCapeConfig.STYLE_SLIM
    };
    String[] qualities = {
      WavyCapeConfig.QUALITY_LOW, WavyCapeConfig.QUALITY_MEDIUM, WavyCapeConfig.QUALITY_HIGH
    };
    for (String style : styles) {
      for (String quality : qualities) {
        WavyCapeConfig cfg = new WavyCapeConfig(true, style, quality, WavyCapeConfig.PLAYERS_ALL,
            1.0f, 0.28f, 0.34f, 1.05f, 1.15f, 0.65f, 0.90f, true, true, 0.95f, 1.0f, 1.0f,
            0.14f, 0.45f, true, 64.0f);
        WavyCapeSimulation sim = new WavyCapeSimulation(7, cfg);
        WavyCapeMesh mesh = null;
        for (int f = 0; f < 30; f++) {
          WavyCapePose pose = basePose(f * 16_666_667L);
          pose.worldZ += f * 0.09;
          mesh = sim.updateAndBuild(pose, cfg);
        }
        assertNotNull(mesh);
        assertTrue(mesh.quadCount > 0, style + "/" + quality);
        assertFalse(mesh.hasNaN(), "NaN found in " + style + "/" + quality);

        for (int q = 0; q < mesh.quadCount; q++) {
          int p = q * 12;
          float x0 = mesh.xyz[p], y0 = mesh.xyz[p + 1], z0 = mesh.xyz[p + 2];
          float x1 = mesh.xyz[p + 3], y1 = mesh.xyz[p + 4], z1 = mesh.xyz[p + 5];
          float x2 = mesh.xyz[p + 6], y2 = mesh.xyz[p + 7], z2 = mesh.xyz[p + 8];
          float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
          float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;
          float cx = e1y * e2z - e1z * e2y;
          float cy = e1z * e2x - e1x * e2z;
          float cz = e1x * e2y - e1y * e2x;
          float anx = 0.25f * (mesh.nrm[p] + mesh.nrm[p + 3] + mesh.nrm[p + 6] + mesh.nrm[p + 9]);
          float any = 0.25f * (mesh.nrm[p + 1] + mesh.nrm[p + 4] + mesh.nrm[p + 7] + mesh.nrm[p + 10]);
          float anz = 0.25f * (mesh.nrm[p + 2] + mesh.nrm[p + 5] + mesh.nrm[p + 8] + mesh.nrm[p + 11]);
          float dot = cx * anx + cy * any + cz * anz;
          assertTrue(dot >= -1e-6f, "Inverted winding on quad " + q + " of " + style + "/" + quality + " dot=" + dot);

          int t = q * 8;
          for (int k = 0; k < 8; k++) {
            assertTrue(mesh.uv[t + k] >= 0.0f && mesh.uv[t + k] <= 1.0f, "UV out of range");
          }
          int c = q * 4;
          for (int k = 0; k < 4; k++) {
            assertTrue(mesh.shade[c + k] >= 0.5f && mesh.shade[c + k] <= 1.001f, "Shade out of range");
          }
        }
      }
    }
  }

  @Test
  void runningForwardBillowsCapeBackwardAndLiftsHem() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation idleSim = new WavyCapeSimulation(1, cfg);
    WavyCapeSimulation runSim = new WavyCapeSimulation(1, cfg);
    int midCol = cfg.gridCols() / 2;
    int hemRow = cfg.gridRows() - 1;

    for (int f = 0; f < 90; f++) {
      long t = f * 16_666_667L;
      WavyCapePose idlePose = basePose(t);
      idleSim.updateAndBuild(idlePose, cfg);

      WavyCapePose runPose = basePose(t);
      runPose.worldZ = 20.0 + (f * (1.0 / 60.0)) * 5.6;
      runPose.walkSpeed = 1.0f;
      runPose.walkPos = f * 0.35f;
      runSim.updateAndBuild(runPose, cfg);
    }

    float idleHemZ = idleSim.renderZ(midCol, hemRow);
    float runHemZ = runSim.renderZ(midCol, hemRow);
    float idleHemY = idleSim.renderY(midCol, hemRow);
    float runHemY = runSim.renderY(midCol, hemRow);
    assertTrue(runHemZ > idleHemZ + 0.20f,
        "Sprinting should billow hem backward (+Z): idle=" + idleHemZ + " run=" + runHemZ);
    assertTrue(runHemY < idleHemY - 0.08f,
        "Sprinting should lift hem upward (-Y): idle=" + idleHemY + " run=" + runHemY);
    assertTrue(runHemY > 0.28f,
        "Sprinting should stay a sculpted diagonal (~45-65 deg) rather than flattening horizontal: runHemY=" + runHemY);
  }

  @Test
  void extremeContinuousSpinNeverInvertsClothColumns() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation sim = new WavyCapeSimulation(9, cfg);
    WavyCapeMesh mesh = null;
    for (int f = 0; f < 60; f++) {
      WavyCapePose pose = basePose(f * 16_666_667L);
      pose.bodyRot = f * 7.5f;
      mesh = sim.updateAndBuild(pose, cfg);
    }
    assertNotNull(mesh);
    assertFalse(mesh.hasNaN());
    for (int r = 1; r < sim.gridRows(); r++) {
      for (int c = 0; c < sim.gridCols() - 1; c++) {
        float leftX = sim.renderX(c, r);
        float rightX = sim.renderX(c + 1, r);
        assertTrue(leftX > rightX + 0.01f,
            "Cloth column inverted/pinched at row=" + r + " col=" + c + " leftX=" + leftX + " rightX=" + rightX);
      }
    }
  }

  @Test
  void turningRightWhipsCapeToPlayersRightAndTurningLeftToPlayersLeft() {
    WavyCapeConfig cfg = new WavyCapeConfig(true, WavyCapeConfig.STYLE_SMOOTH,
        WavyCapeConfig.QUALITY_MEDIUM, WavyCapeConfig.PLAYERS_ALL,
        1.0f, 0.28f, 0.34f, 1.05f, 1.4f, 0.0f, 0.0f, false, false,
        0.9f, 1.0f, 1.0f, 0.0f, 0.4f, true, 64.0f);
    WavyCapeSimulation rightSim = new WavyCapeSimulation(2, cfg);
    WavyCapeSimulation leftSim = new WavyCapeSimulation(2, cfg);
    int midCol = cfg.gridCols() / 2;
    int hemRow = cfg.gridRows() - 1;

    for (int f = 0; f < 25; f++) {
      long t = f * 16_666_667L;
      WavyCapePose rightPose = basePose(t);
      rightPose.bodyRot = f * 6.0f;
      rightSim.updateAndBuild(rightPose, cfg);

      WavyCapePose leftPose = basePose(t);
      leftPose.bodyRot = -f * 6.0f;
      leftSim.updateAndBuild(leftPose, cfg);
    }

    assertTrue(rightSim.renderX(midCol, hemRow) < -0.05f,
        "Turning right should swing cape toward -X (player right), got " + rightSim.renderX(midCol, hemRow));
    assertTrue(leftSim.renderX(midCol, hemRow) > 0.05f,
        "Turning left should swing cape toward +X (player left), got " + leftSim.renderX(midCol, hemRow));
  }

  @Test
  void crouchingDrapesOverSlopedBackWithoutClippingTorso() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation sim = new WavyCapeSimulation(3, cfg);

    for (int f = 0; f < 60; f++) {
      WavyCapePose pose = basePose(f * 16_666_667L);
      pose.crouching = true;
      pose.bodyY = 3.2f;
      pose.bodyXRot = 0.5f;
      pose.rightLegY = 12.2f;
      pose.rightLegZ = 4.0f;
      pose.leftLegY = 12.2f;
      pose.leftLegZ = 4.0f;
      sim.updateAndBuild(pose, cfg);
    }

    int midCol = cfg.gridCols() / 2;
    float waistZ = sim.waistZ();
    assertTrue(waistZ > 0.42f, "Crouched waistZ should be pushed back: " + waistZ);
    for (int r = 1; r < cfg.gridRows(); r++) {
      float vy = sim.renderY(midCol, r);
      float vz = sim.renderZ(midCol, r);
      if (vy <= 0.85f) {
        assertTrue(vz >= sim.shoulderZ() - 0.02f, "Cloth clipped forward of shoulders at row " + r);
      }
      if (Math.abs(vy - 0.78f) < 0.08f) {
        assertTrue(vz >= waistZ - 0.02f,
            "Cloth clipped into crouched back at row " + r + " vz=" + vz + " waistZ=" + waistZ);
      }
    }
  }

  @Test
  void fallingUpdraftLiftsCapeWithoutExplodingOrFlippingOverFace() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation sim = new WavyCapeSimulation(4, cfg);
    int midCol = cfg.gridCols() / 2;
    int hemRow = cfg.gridRows() - 1;

    for (int f = 0; f < 60; f++) {
      WavyCapePose pose = basePose(f * 16_666_667L);
      pose.worldY = 120.0 - f * 0.25;
      sim.updateAndBuild(pose, cfg);
    }

    float hemY = sim.renderY(midCol, hemRow);
    float hemZ = sim.renderZ(midCol, hemRow);
    assertTrue(hemZ > 0.50f, "Falling should billow cape far behind shoulders: hemZ=" + hemZ);
    assertTrue(hemY >= -0.75f, "Cape must not flip wildly above head: hemY=" + hemY);
    for (int r = 1; r < sim.gridRows(); r++) {
      float vy = sim.renderY(midCol, r);
      float vz = sim.renderZ(midCol, r);
      if (vy < -0.10f) {
        assertTrue(vz >= 0.28f,
            "Rising cape during fall must stay cleanly behind the back of the head: row=" + r + " vy=" + vy + " vz=" + vz);
      }
    }
    assertFalse(sim.updateAndBuild(basePose(61 * 16_666_667L), cfg).hasNaN());
  }

  @Test
  void teleportDoesNotStretchOrProduceNaN() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation sim = new WavyCapeSimulation(5, cfg);
    WavyCapePose pose = basePose(0L);
    sim.updateAndBuild(pose, cfg);

    pose.timeNanos = 16_666_667L;
    pose.worldX = 5000.0;
    pose.worldY = 120.0;
    pose.worldZ = -9000.0;
    WavyCapeMesh mesh = sim.updateAndBuild(pose, cfg);
    assertFalse(mesh.hasNaN());

    int midCol = cfg.gridCols() / 2;
    float dy = sim.renderY(midCol, cfg.gridRows() - 1) - sim.renderY(midCol, 0);
    float dz = sim.renderZ(midCol, cfg.gridRows() - 1) - sim.renderZ(midCol, 0);
    float dist = (float) Math.sqrt(dy * dy + dz * dz);
    assertTrue(dist <= 1.08f, "Cape stretched after teleport: dist=" + dist);
  }

  @Test
  void horizontalSwimmingStreamsCapeAlongBodyNotUpwardSharkFin() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation sim = new WavyCapeSimulation(11, cfg);
    int midCol = cfg.gridCols() / 2;
    int hemRow = cfg.gridRows() - 1;

    for (int f = 0; f < 90; f++) {
      WavyCapePose pose = basePose(f * 16_666_667L);
      pose.worldZ = 20.0 + (f * (1.0 / 60.0)) * 4.5;
      pose.inWater = true;
      pose.swimAmount = 1.0f;
      sim.updateAndBuild(pose, cfg);
    }

    float hemY = sim.renderY(midCol, hemRow);
    float hemZ = sim.renderZ(midCol, hemRow);
    assertTrue(hemY > 0.72f,
        "Swimming cape should stream smoothly along body toward feet (+Y), got hemY=" + hemY);
    assertTrue(hemZ < 0.65f,
        "Swimming cape must NOT stick straight up like a shark fin (+Z), got hemZ=" + hemZ);
  }

  @Test
  void backPedalIntoInstantCrouchNeverCatapultsCapeUpward() {
    WavyCapeConfig cfg = WavyCapeConfig.defaults();
    WavyCapeSimulation sim = new WavyCapeSimulation(12, cfg);
    int midCol = cfg.gridCols() / 2;
    int hemRow = cfg.gridRows() - 1;

    for (int f = 0; f < 30; f++) {
      WavyCapePose pose = basePose(f * 16_666_667L);
      pose.worldZ = 20.0 - (f * (1.0 / 60.0)) * 4.3;
      pose.walkSpeed = 1.0f;
      pose.walkPos = f * 0.35f;
      sim.updateAndBuild(pose, cfg);
    }

    double startZ = 20.0 - 0.5 * 4.3;
    for (int f = 0; f < 30; f++) {
      WavyCapePose pose = basePose((30 + f) * 16_666_667L);
      pose.worldZ = startZ + (f * (1.0 / 60.0)) * 1.3;
      pose.crouching = true;
      pose.bodyY = 3.2f;
      pose.bodyXRot = 0.5f;
      pose.rightLegY = 12.2f;
      pose.rightLegZ = 4.0f;
      pose.leftLegY = 12.2f;
      pose.leftLegZ = 4.0f;
      pose.walkSpeed = 0.6f;
      pose.walkPos = f * 0.25f;
      sim.updateAndBuild(pose, cfg);

      float hemY = sim.renderY(midCol, hemRow);
      assertTrue(hemY > 0.35f,
          "Instant crouch must glide smoothly without catapulting hem upward! frame=" + f + " hemY=" + hemY);
    }
  }

  @Test
  void managerTracksMultipleEntitiesAndPrunesStaleOnes() {
    WavyCapeManager manager = new WavyCapeManager();
    WavyCapeConfig cfg = WavyCapeConfig.defaults();

    WavyCapePose p1 = basePose(1_000_000_000L);
    p1.entityId = 101;
    manager.updateAndBuild(p1, cfg);

    WavyCapePose p2 = basePose(1_000_000_000L);
    p2.entityId = 202;
    manager.updateAndBuild(p2, cfg);
    assertEquals(2, manager.activeCount());

    p1.timeNanos = 11_000_000_000L;
    manager.updateAndBuild(p1, cfg);
    assertEquals(1, manager.activeCount());
  }

  @Test
  void settingsPresetsAndValidationClampAllBounds() {
    Settings s = new Settings();
    assertNotNull(s.wavyCapes);
    assertFalse(s.wavyCapes.enabled);
    assertEquals("silk", s.wavyCapes.preset);

    s.wavyCapes.applyPreset("royal");
    assertEquals("royal", s.wavyCapes.preset);
    assertTrue(s.wavyCapes.lengthScale > 1.1f);
    assertTrue(s.wavyCapes.hemFlare > 0.2f);

    s.wavyCapes.applyPreset("voxel");
    assertEquals("blocky", s.wavyCapes.style);

    s.wavyCapes.gravity = Float.NaN;
    s.wavyCapes.wind = 999f;
    s.wavyCapes.style = "invalid";
    s.wavyCapes.quality = "ultra99";
    s.validate();
    assertEquals("smooth", s.wavyCapes.style);
    assertEquals("medium", s.wavyCapes.quality);
    assertEquals(1.0f, s.wavyCapes.gravity, 1e-4f);
    assertEquals(2.5f, s.wavyCapes.wind, 1e-4f);
  }
}
