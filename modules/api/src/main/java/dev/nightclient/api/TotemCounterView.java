package dev.nightclient.api;

public final class TotemCounterView {
  public final int entityId;
  public final double x;
  public final double y;
  public final double z;
  public final float bbHeight;
  public final int pops;
  public final float sincePopSeconds;
  public final boolean invisible;

  public TotemCounterView(
      int entityId,
      double x,
      double y,
      double z,
      float bbHeight,
      int pops,
      float sincePopSeconds,
      boolean invisible) {
    this.entityId = entityId;
    this.x = x;
    this.y = y;
    this.z = z;
    this.bbHeight = bbHeight;
    this.pops = Math.max(0, pops);
    this.sincePopSeconds = Float.isFinite(sincePopSeconds) ? Math.max(0f, sincePopSeconds) : 999f;
    this.invisible = invisible;
  }
}
