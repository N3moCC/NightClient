package dev.nightclient.api;

public final class PotionView {
  public final String id, name;
  public final int amplifier, color;
  public final float durationSeconds;
  public final boolean beneficial;

  public PotionView(
      String id, String name, int amplifier, int color, float durationSeconds, boolean beneficial) {
    this.id = id;
    this.name = name;
    this.amplifier = amplifier;
    this.color = color;
    this.durationSeconds = durationSeconds;
    this.beneficial = beneficial;
  }
}
