package dev.nightclient.renderer.atmosphere;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

final class AtmosphereEngineTest {
  private static class FakeWorld implements AtmosphereWorldAccess {
    final Object id = new Object();
    boolean overworld = true;
    long time = 6000L;

    @Override
    public Object worldIdentity() {
      return id;
    }

    @Override
    public boolean isOverworld() {
      return overworld;
    }

    @Override
    public long dayTime() {
      return time;
    }

    @Override
    public int renderDistanceChunks() {
      return 12;
    }

    @Override
    public boolean cameraSubmerged() {
      return false;
    }

    @Override
    public int skyLightAt(int x, int y, int z) {
      return 15;
    }

    @Override
    public boolean scanColumn(int x, int camFootY, int z, int[] outTopGroundType) {
      outTopGroundType[3] = 0;
      outTopGroundType[4] = 0;
      int modX = ((x % 16) + 16) % 16;
      int modZ = ((z % 16) + 16) % 16;
      if (modX == 0 && modZ == 0) {
        outTopGroundType[0] = 72;
        outTopGroundType[1] = 64;
        outTopGroundType[2] = 3;
        outTopGroundType[3] = 15;
      } else if (modX == 5) {
        outTopGroundType[0] = 68;
        outTopGroundType[1] = 68;
        outTopGroundType[2] = 5;
      } else if (modZ == 12) {
        outTopGroundType[0] = 63;
        outTopGroundType[1] = 63;
        outTopGroundType[2] = 2;
        outTopGroundType[4] = NO_GROUND_COVER;
      } else {
        outTopGroundType[0] = 64;
        outTopGroundType[1] = 64;
        outTopGroundType[2] = 1;
      }
      return true;
    }
  }

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
  void columnFixedPointHeightRoundTrip() {
    for (float y : new float[] {-64.0f, 0.0f, 63.5f, 128.25f, 319.75f}) {
      int u16 = AtmosphereColumns.encodeHeightFixed(y);
      float decoded = AtmosphereColumns.decodeHeightFixed(u16);
      assertEquals(y, decoded, 0.02f);
    }
  }

  @Test
  void oklabMixMatchesEndpointsAndStaysInSrgbBounds() {
    float[] out = new float[3];
    AtmospherePresets.oklabMix(0.8f, 0.5f, 0.3f, 0.2f, 0.4f, 0.9f, 0.0f, out);
    assertEquals(0.8f, out[0], 0.002f);
    assertEquals(0.5f, out[1], 0.002f);
    assertEquals(0.3f, out[2], 0.002f);

    AtmospherePresets.oklabMix(0.8f, 0.5f, 0.3f, 0.2f, 0.4f, 0.9f, 1.0f, out);
    assertEquals(0.2f, out[0], 0.002f);
    assertEquals(0.4f, out[1], 0.002f);
    assertEquals(0.9f, out[2], 0.002f);

    AtmospherePresets.oklabMix(1.0f, 0.68f, 0.48f, 0.16f, 0.19f, 0.38f, 0.42f, out);
    for (float c : out) {
      assertTrue(c >= 0.0f && c <= 1.0f);
    }
  }

  @Test
  void matrixInversionRoundTripsPerspectiveViewProj() {
    float[] vp = new float[16];
    float[] inv = new float[16];
    float[] prod = new float[16];
    AtmosphereEngine.multiplyMat4(perspectiveMat4(), identityMat4(), vp);
    assertTrue(AtmosphereEngine.invertMat4(vp, inv));
    AtmosphereEngine.multiplyMat4(vp, inv, prod);
    float[] id = identityMat4();
    for (int i = 0; i < 16; i++) {
      assertEquals(id[i], prod[i], 1.0e-4f);
    }
  }

  @Test
  void autumnPrecipitationKeepsCanopySampleGridAlignedAcrossBlockBoundaries() {
    Settings settings = new Settings();
    settings.atmosphere.enabled = true;
    settings.atmosphere.mode = "autumn";
    FakeWorld world = new FakeWorld();
    AtmosphereEngine engine = new AtmosphereEngine();
    int gridOrigin = 16 * Float.BYTES;
    int fallDetail = 36 * Float.BYTES;

    double[][] cameraPositions = {{-1.25, -0.75}, {-0.75, -1.25}};
    for (double[] position : cameraPositions) {
      double x = position[0];
      double z = position[1];
      AtmosphereEngine.FramePlan plan = engine.prepareFrame(
          settings, world, 1280, 720, x, 65.6, z, 0f, 0f, identityMat4(), perspectiveMat4(), 0);
      float parityX = plan.precipUbo.getFloat(fallDetail + 17 * Float.BYTES);
      float parityZ = plan.precipUbo.getFloat(fallDetail + 18 * Float.BYTES);
      assertEquals(-34, x + plan.precipUbo.getFloat(gridOrigin) - parityX, 1.0e-5);
      assertEquals(-34, z + plan.precipUbo.getFloat(gridOrigin + 2 * Float.BYTES) - parityZ, 1.0e-5);
    }
  }

  @Test
  void engineProducesValidPlansAcrossAllSevenModes() {
    Settings s = new Settings();
    FakeWorld world = new FakeWorld();
    AtmosphereEngine engine = new AtmosphereEngine();

    AtmosphereEngine.FramePlan disabledPlan =
        engine.prepareFrame(
            s, world, 1920, 1080, 10.5, 65.6, -20.25, 10f, 45f, identityMat4(), perspectiveMat4(), 0);
    assertFalse(disabledPlan.active);

    s.atmosphere.enabled = true;
    String[] modes = {"dawn", "dust", "snow", "sakura", "autumn", "fireflies", "mystic"};
    for (String mode : modes) {
      s.atmosphere.mode = mode;
      AtmosphereEngine.FramePlan plan =
          engine.prepareFrame(
              s, world, 1920, 1080, 10.5, 65.6, -20.25, 10f, 45f, identityMat4(), perspectiveMat4(), 0);
      assertTrue(plan.active, mode);
      if ("dawn".equals(mode)) {
        assertTrue(plan.isDawn);
        assertEquals(AtmosphereEngine.DAWN_UBO_BYTES, plan.dawnUbo.remaining());
      } else {
        assertFalse(plan.isDawn);
        assertEquals(AtmosphereEngine.MOOD_UBO_BYTES, plan.moodUbo.remaining());
      }
      if ("snow".equals(mode) || "sakura".equals(mode) || "autumn".equals(mode)
          || "mystic".equals(mode)) {
        assertTrue(plan.drawPrecip, mode + " should emit procedural precipitation");
        assertTrue(plan.precipVertexCount > 0);
        assertEquals(AtmosphereEngine.PRECIP_UBO_BYTES, plan.precipUbo.remaining());
      }
      if ("sakura".equals(mode) || "fireflies".equals(mode) || "mystic".equals(mode)) {
        assertTrue(plan.runBokeh, mode + " should run quarter-res bokeh");
      }
    }

    s.atmosphere.mode = "mystic";
    assertTrue(AtmosphereEngine.shouldForceNight(s, true));
    assertFalse(AtmosphereEngine.shouldForceNight(s, false));
    s.atmosphere.mysticWorld.alwaysNight = false;
    assertFalse(AtmosphereEngine.shouldForceNight(s, true));
  }

  @Test
  void leafLayersAndGroundCoverSurviveRollingGrid() {
    AtmosphereColumns columns = new AtmosphereColumns();
    AtmosphereWorldAccess world = new FakeWorld() {
      @Override
      public boolean scanColumn(int x, int camFootY, int z, int[] out) {
        if (x == 0 && z == 0) {
          out[0] = 92;
          out[1] = 64;
          out[2] = 3;
          out[3] = (1 << 20) | (1 << 11) | (1 << 0);
          out[4] = BROAD_GROUND_COVER | 4;
          return true;
        }
        if (x == 1 && z == 0) {
          out[0] = 65;
          out[1] = 65;
          out[2] = 6;
          out[3] = 0;
          out[4] = NO_GROUND_COVER;
          return true;
        }
        if (x == 2 && z == 0) {
          out[0] = 65;
          out[1] = 65;
          out[2] = 5;
          out[3] = 0;
          out[4] = 0;
          return true;
        }
        return super.scanColumn(x, camFootY, z, out);
      }
    };

    columns.update(world, 0.5, 65.6, 0.5);
    int cx = -columns.originX();
    int cz = -columns.originZ();
    int texBase = cz * AtmosphereColumns.TEX_WIDTH + (cx << 1);
    int texel0 = columns.argbTexels()[texBase];
    int texel1 = columns.argbTexels()[texBase + 1];
    assertEquals(0x01E31008, texel1);
    assertEquals(63.9375f, AtmosphereColumns.decodeHeightFixed(
        ((texel0 & 0xFF) << 8) | (texel0 >>> 24)));
    assertEquals(0x00860000, columns.argbTexels()[texBase + 3]);
    assertEquals(0x00A50000, columns.argbTexels()[texBase + 5]);

    java.nio.ByteBuffer rgba = java.nio.ByteBuffer.allocateDirect(
        AtmosphereColumns.TEX_WIDTH * AtmosphereColumns.TEX_HEIGHT * 4);
    columns.writeRgbaBytes(rgba);
    int bOffset = (texBase + 1) * 4;
    assertEquals(0xE3, rgba.get(bOffset) & 0xFF);
    assertEquals(0x10, rgba.get(bOffset + 1) & 0xFF);
    assertEquals(0x08, rgba.get(bOffset + 2) & 0xFF);
    assertEquals(0x01, rgba.get(bOffset + 3) & 0xFF);

    columns.update(world, 15.5, 65.6, -15.5);
    int shiftedBase = -columns.originZ() * AtmosphereColumns.TEX_WIDTH - columns.originX() * 2;
    assertEquals(texel0, columns.argbTexels()[shiftedBase]);
    assertEquals(texel1, columns.argbTexels()[shiftedBase + 1]);
    assertEquals(0x00860000, columns.argbTexels()[shiftedBase + 3]);
    assertEquals(0x00A50000, columns.argbTexels()[shiftedBase + 5]);
    for (int cz2 = 0; cz2 < AtmosphereColumns.GRID_SIZE; cz2++) {
      for (int cx2 = 0; cx2 < AtmosphereColumns.GRID_SIZE; cx2++) {
        int wx = columns.originX() + cx2;
        int wz = columns.originZ() + cz2;
        assertTrue(columns.isValidWorld(wx, wz), "Shifted column should be immediately valid");
        int st = columns.surfaceTypeWorld(wx, wz);
        assertTrue(st > 0, "Shifted column surface type should be non-zero");
      }
    }
  }

  @Test
  void mysticMoonSizeReversedDepthAndPresetsMatchParityContracts() {
    assertTrue(AtmospherePresets.DUST.skyMix >= 0.75f);
    assertTrue(AtmospherePresets.AUTUMN.skyMix >= 0.75f);

    Settings s = new Settings();
    s.atmosphere.enabled = true;
    FakeWorld world = new FakeWorld();
    world.time = 18000L;
    AtmosphereEngine engine = new AtmosphereEngine();

    for (int depthMode = 0; depthMode <= 3; depthMode++) {
      s.atmosphere.mode = "dawn";
      AtmosphereEngine.FramePlan dawnPlan = engine.prepareFrame(
          s, world, 1920, 1080, 0.5, 65.6, 0.5, 0f, 0f, identityMat4(), perspectiveMat4(), depthMode);
      assertEquals((float) depthMode, dawnPlan.dawnUbo.getFloat(31 * 4), 1.0e-5f);

      s.atmosphere.mode = "mystic";
      s.atmosphere.mysticSky.moon = true;
      s.atmosphere.mysticSky.moonSize = 100f;
      AtmosphereEngine.FramePlan mysticPlan = engine.prepareFrame(
          s, world, 1920, 1080, 0.5, 65.6, 0.5, 0f, 0f, identityMat4(), perspectiveMat4(), depthMode);
      assertEquals((float) depthMode, mysticPlan.moodUbo.getFloat(51 * 4), 1.0e-5f);
      assertEquals(0.042f, mysticPlan.moodUbo.getFloat(96 * 4), 1.0e-5f);
      assertEquals((float) depthMode, mysticPlan.precipUbo.getFloat(31 * 4), 1.0e-5f);
    }
  }

  @Test
  void skyLightProjectionIgnoresTranslationsAndChangesInAltitude() {
    Settings settings = new Settings();
    settings.atmosphere.enabled = true;
    settings.atmosphere.mode = "dawn";
    FakeWorld world = new FakeWorld();
    world.time = 100L;
    AtmosphereEngine engine = new AtmosphereEngine();
    float[] view = {
      0f, 0f, -1f, 0f,
      0f, 1f, 0f, 0f,
      1f, 0f, 0f, 0f,
      0f, 0f, 0f, 1f
    };
    AtmosphereEngine.FramePlan plan = engine.prepareFrame(
        settings, world, 1280, 720, 0.5, 65.6, 0.5, 0f, 0f, view, perspectiveMat4(), 0);
    int sunScreen = 32 * Float.BYTES;
    float sunX = plan.dawnUbo.getFloat(sunScreen);
    float sunY = plan.dawnUbo.getFloat(sunScreen + Float.BYTES);
    assertTrue(plan.dawnUbo.getFloat(sunScreen + 2 * Float.BYTES) > 0.9f);

    view[12] = 4f;
    view[13] = -12f;
    view[14] = 6f;
    plan = engine.prepareFrame(
        settings, world, 1280, 720, 0.5, 165.6, 0.5, 0f, 0f, view, perspectiveMat4(), 0);
    assertEquals(sunX, plan.dawnUbo.getFloat(sunScreen), 1.0e-6f);
    assertEquals(sunY, plan.dawnUbo.getFloat(sunScreen + Float.BYTES), 1.0e-6f);
  }

  @Test
  void fogBaseRemainsAtAWorldHeightBeforeTerrainColumnsLoad() {
    Settings settings = new Settings();
    settings.atmosphere.enabled = true;
    settings.atmosphere.mode = "sakura";
    FakeWorld world = new FakeWorld() {
      @Override
      public boolean scanColumn(int x, int camFootY, int z, int[] out) {
        return false;
      }
    };
    AtmosphereEngine engine = new AtmosphereEngine();
    int groundY = 27 * Float.BYTES;
    for (double height : new double[] {65.6, 165.6}) {
      AtmosphereEngine.FramePlan plan = engine.prepareFrame(
          settings, world, 1280, 720, 0.5, height, 0.5, 0f, 0f,
          identityMat4(), perspectiveMat4(), 0);
      assertFalse(engine.columns().hasValid());
      assertEquals(64.0f, plan.moodUbo.getFloat(groundY), 1.0e-6f);
    }
  }

  @Test
  void offscreenSunScreenPreservesRadialDirectionWithoutSquareClamping() {
    Settings s = new Settings();
    s.atmosphere.enabled = true;
    s.atmosphere.mode = "dawn";
    FakeWorld world = new FakeWorld();
    world.time = 100L;
    AtmosphereEngine engine = new AtmosphereEngine();

    float[] proj = new float[] {
      1.0f, 0f, 0f, 0f,
      0f, 1.0f, 0f, 0f,
      0f, 0f, -1.002f, -1f,
      0f, 0f, -0.2002f, 0f
    };
    float fx = 0.14f, fy = -0.38f, fz = 0.914f;
    float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
    fx /= fLen; fy /= fLen; fz /= fLen;
    float rxB = -fz, rzB = fx;
    float rLen = (float) Math.hypot(rxB, rzB);
    rxB /= rLen; rzB /= rLen;
    float ux = -rzB * fy;
    float uy = rzB * fx - rxB * fz;
    float uz = rxB * fy;
    float[] viewRot = new float[] {
      rxB, ux, -fx, 0f,
      0f,  uy, -fy, 0f,
      rzB, uz, -fz, 0f,
      0f,  0f,  0f, 1f
    };

    float celAngle = AtmospherePresets.dawnTimeRadians(world.time);
    float sign = -Math.sin(celAngle) < 0.0 ? -1.0f : 1.0f;
    float sunX = (float) Math.cos(0.11f) * sign;
    float sunY = (float) Math.sin(0.11f);
    float camSpaceX = rxB * sunX;
    float camSpaceY = ux * sunX + uy * sunY;

    AtmosphereEngine.FramePlan plan = engine.prepareFrame(
        s, world, 1920, 1080, 0.5, 65.6, 0.5, 0f, 0f, viewRot, proj, 0);
    float sx = plan.dawnUbo.getFloat(32 * 4);
    float syScreen = plan.dawnUbo.getFloat(33 * 4);
    float rx = sx - 0.5f;
    float ry = syScreen - 0.5f;
    float len = (float) Math.hypot(rx, ry);
    assertEquals(3.5f, len, 1.0e-3f, "Offscreen sun should be radially capped on circle R=3.5");
    assertEquals(camSpaceY / camSpaceX, ry / rx, 1.0e-3f, "Offscreen sun angle must remain unbent");
  }

  @Test
  void worldTimeAndWeatherPoliciesRespectModesAndDimensions() {
    Settings s = new Settings();
    assertEquals(-1L, AtmosphereEngine.forcedOverworldTime(s, true));
    assertFalse(AtmosphereEngine.shouldClearWeather(s, true));

    s.worldTime.enabled = true;
    s.worldTime.time = "day";
    s.worldTime.noRain = true;
    assertEquals(AtmosphereEngine.FORCED_DAY_TICKS, AtmosphereEngine.forcedOverworldTime(s, true));
    assertEquals(-1L, AtmosphereEngine.forcedOverworldTime(s, false), "Only overrides Overworld clock");
    assertTrue(AtmosphereEngine.shouldClearWeather(s, true));
    assertTrue(AtmosphereEngine.shouldClearWeather(s, false));

    s.worldTime.time = "night";
    s.worldTime.noRain = false;
    assertEquals(AtmosphereEngine.FORCED_NIGHT_TICKS, AtmosphereEngine.forcedOverworldTime(s, true));
    assertFalse(AtmosphereEngine.shouldClearWeather(s, true));

    s.worldTime.time = "off";
    s.worldTime.noRain = true;
    assertEquals(-1L, AtmosphereEngine.forcedOverworldTime(s, true));
    assertTrue(AtmosphereEngine.shouldClearWeather(s, true));

    s.atmosphere.enabled = true;
    s.atmosphere.mode = "mystic";
    s.atmosphere.mysticWorld.alwaysNight = true;
    s.worldTime.time = "day";
    assertEquals(AtmosphereEngine.FORCED_DAY_TICKS, AtmosphereEngine.forcedOverworldTime(s, true));

    s.worldTime.time = "off";
    assertEquals(AtmosphereEngine.FORCED_NIGHT_TICKS, AtmosphereEngine.forcedOverworldTime(s, true));
  }

  @Test
  void customSkyPresetsTimeChangerNoRenderWeatherAndVectorAtlasWorkEndToEnd() throws Exception {
    Settings s = new Settings();
    FakeWorld world = new FakeWorld();
    AtmosphereEngine engine = new AtmosphereEngine();

    s.noRender.weather = true;
    assertFalse(AtmosphereEngine.shouldClearWeather(s, true));
    s.noRender.enabled = true;
    assertTrue(AtmosphereEngine.shouldClearWeather(s, true));
    assertTrue(AtmosphereEngine.shouldClearWeather(s, false));

    s.customSky.enabled = true;
    s.customSky.timeMode = "sunset";
    assertEquals(AtmosphereEngine.FORCED_SUNSET_TICKS, AtmosphereEngine.forcedOverworldTime(s, true));
    s.customSky.timeMode = "custom";
    s.customSky.customTime = 13500;
    assertEquals(13500L, AtmosphereEngine.forcedOverworldTime(s, true));

    s.customSky.style = "vanilla";
    AtmosphereEngine.FramePlan vanillaPlan = engine.prepareFrame(
        s, world, 1280, 720, 0.5, 65.6, 0.5, 0f, 0f, identityMat4(), perspectiveMat4(), 0);
    assertFalse(vanillaPlan.active);
    assertFalse(vanillaPlan.drawCustomSky);

    for (String style : new String[] {"galaxy", "aurora", "sunset", "anime", "blood_moon", "pastel"}) {
      s.customSky.style = style;
      AtmosphereEngine.FramePlan plan = engine.prepareFrame(
          s, world, 1280, 720, 0.5, 65.6, 0.5, 0f, 0f, identityMat4(), perspectiveMat4(), 0);
      assertTrue(plan.active, "Custom Sky active for " + style);
      assertTrue(plan.drawCustomSky, "Custom Sky pass active for " + style);
      assertFalse(plan.drawAtmosphere, "Atmosphere stays off when only Custom Sky is enabled");
      assertEquals(AtmosphereEngine.CUSTOM_SKY_UBO_BYTES, plan.customSkyUbo.remaining());
    }

    world.overworld = false;
    s.customSky.endAndNether = false;
    assertFalse(AtmosphereEngine.isCustomSkyActive(s, false));
    s.customSky.endAndNether = true;
    assertTrue(AtmosphereEngine.isCustomSkyActive(s, false));

    java.io.InputStream atlasStream =
        AtmosphereEngine.class.getResourceAsStream("/assets/nightclient/textures/sky/celestials.png");
    assertNotNull(atlasStream, "Bundled vector celestial atlas PNG must exist");
    java.awt.image.BufferedImage atlas = javax.imageio.ImageIO.read(atlasStream);
    atlasStream.close();
    assertEquals(1024, atlas.getWidth());
    assertEquals(1024, atlas.getHeight());
    for (int ty = 0; ty < 2; ty++) {
      for (int tx = 0; tx < 2; tx++) {
        int opaque = 0;
        for (int y = ty * 512; y < (ty + 1) * 512; y += 8) {
          for (int x = tx * 512; x < (tx + 1) * 512; x += 8) {
            if (((atlas.getRGB(x, y) >>> 24) & 255) > 20) opaque++;
          }
        }
        assertTrue(opaque > 200, "Tile (" + tx + "," + ty + ") must contain vector artwork");
      }
    }
  }
}
