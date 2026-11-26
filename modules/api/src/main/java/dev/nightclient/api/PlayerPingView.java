package dev.nightclient.api;

public final class PlayerPingView {
  public final int entityId;
  public final double x;
  public final double y;
  public final double z;
  public final float bbHeight;
  public final int pingMs;
  public final boolean invisible;
  public final boolean self;

  public PlayerPingView(
      int entityId,
      double x,
      double y,
      double z,
      float bbHeight,
      int pingMs,
      boolean invisible,
      boolean self) {
    this.entityId = entityId;
    this.x = x;
    this.y = y;
    this.z = z;
    this.bbHeight = bbHeight;
    this.pingMs = Math.max(0, pingMs);
    this.invisible = invisible;
    this.self = self;
  }
}
