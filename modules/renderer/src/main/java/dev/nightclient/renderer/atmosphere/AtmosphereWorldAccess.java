package dev.nightclient.renderer.atmosphere;

public interface AtmosphereWorldAccess {
  int CANOPY_LAYERS = 24;
  int NO_GROUND_COVER = -1;
  int GROUND_COVER_DEPTH_MASK = 0x7F;
  int BROAD_GROUND_COVER = 0x80;

  Object worldIdentity();

  boolean isOverworld();

  long dayTime();

  int renderDistanceChunks();

  boolean cameraSubmerged();

  int skyLightAt(int x, int y, int z);

  boolean scanColumn(int x, int camFootY, int z, int[] outTopGroundType);
}
