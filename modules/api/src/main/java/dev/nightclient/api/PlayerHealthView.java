package dev.nightclient.api;

public final class PlayerHealthView {
  public final int entityId;
  public final double x;
  public final double y;
  public final double z;
  public final float bbHeight;
  public final float health;
  public final float maxHealth;
  public final float absorption;
  public final boolean invisible;
  public final boolean enchanted;

  public PlayerHealthView(
      int entityId,
      double x,
      double y,
      double z,
      float bbHeight,
      float health,
      float maxHealth,
      float absorption,
      boolean invisible,
      boolean enchanted) {
    this.entityId = entityId;
    this.x = x;
    this.y = y;
    this.z = z;
    this.bbHeight = bbHeight;
    this.health = Math.max(0f, health);
    this.maxHealth = Math.max(1f, maxHealth);
    this.absorption = Math.max(0f, absorption);
    this.invisible = invisible;
    this.enchanted = enchanted;
  }
}
