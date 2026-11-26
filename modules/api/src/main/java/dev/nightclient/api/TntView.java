package dev.nightclient.api;

public final class TntView {
  public final int id;
  public final float screenX;
  public final float screenY;
  public final float seconds;
  public final float distance;
  public final boolean visible;

  public TntView(int id, float screenX, float screenY, float seconds, float distance) {
    this(id, screenX, screenY, seconds, distance, true);
  }

  public TntView(
      int id, float screenX, float screenY, float seconds, float distance, boolean visible) {
    this.id = id;
    this.screenX = finite(screenX);
    this.screenY = finite(screenY);
    this.seconds = Math.max(0, finite(seconds));
    this.distance = Math.max(0, finite(distance));
    this.visible = visible && Float.isFinite(screenX) && Float.isFinite(screenY);
  }

  private static float finite(float value) {
    return Float.isFinite(value) ? value : 0;
  }
}
