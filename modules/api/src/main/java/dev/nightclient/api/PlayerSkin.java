package dev.nightclient.api;

public interface PlayerSkin {
  int BODY_SIZE = 64;
  int CAPE_WIDTH = 64;
  int CAPE_HEIGHT = 32;
  int MAX_TEXTURE_SIZE = 4096;

  int[] bodyPixels();

  default int bodyWidth() {
    return BODY_SIZE;
  }

  default int bodyHeight() {
    return BODY_SIZE;
  }

  int[] capePixels();

  default int capeWidth() {
    return CAPE_WIDTH;
  }

  default int capeHeight() {
    return CAPE_HEIGHT;
  }

  boolean slimModel();
}
