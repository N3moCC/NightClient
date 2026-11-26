package dev.nightclient.api;

public final class Cooldown {
  public final String key;
  public final String name;
  public final String itemId;
  public final float remainingSeconds;
  public final float durationSeconds;
  public final float remainingFraction;

  public Cooldown(
      String key, String name, String itemId, float remainingSeconds, float durationSeconds) {
    this.key = key;
    this.name = name;
    this.itemId = itemId;
    this.remainingSeconds = Math.max(0, remainingSeconds);
    this.durationSeconds = Math.max(0.05f, durationSeconds);
    this.remainingFraction = Math.max(0, Math.min(1, this.remainingSeconds / this.durationSeconds));
  }
}
