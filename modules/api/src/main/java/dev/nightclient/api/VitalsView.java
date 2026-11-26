package dev.nightclient.api;

public final class VitalsView {
  public final int hunger;
  public final float saturation;
  public final boolean available, hungerVisible;
  public static final VitalsView EMPTY = new VitalsView(0, 0, false);

  public VitalsView(int hunger, float saturation, boolean available) {
    this(hunger, saturation, available, true);
  }

  public VitalsView(int hunger, float saturation, boolean available, boolean hungerVisible) {
    this.hunger = hunger;
    this.saturation = saturation;
    this.available = available;
    this.hungerVisible = hungerVisible;
  }
}
