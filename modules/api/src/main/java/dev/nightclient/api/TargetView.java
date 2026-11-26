package dev.nightclient.api;

public final class TargetView {
  public final int entityId;
  public final String uuid, name;
  public final float health, maxHealth, absorption, distance;
  public final boolean player;
  public final String headTexture;
  public final java.util.List<InventorySlot> equipment;

  public TargetView(int entityId, String uuid, String name, float health, float maxHealth,
      float absorption, float distance, boolean player) {
    this(entityId, uuid, name, health, maxHealth, absorption, distance, player, "",
        java.util.Collections.<InventorySlot>emptyList());
  }

  public TargetView(int entityId, String uuid, String name, float health, float maxHealth,
      float absorption, float distance, boolean player, String headTexture,
      java.util.List<InventorySlot> equipment) {
    this.entityId = entityId;
    this.uuid = uuid;
    this.name = name;
    this.health = health;
    this.maxHealth = maxHealth;
    this.absorption = absorption;
    this.distance = distance;
    this.player = player;
    this.headTexture = headTexture == null ? "" : headTexture;
    this.equipment = equipment == null
        ? java.util.Collections.<InventorySlot>emptyList()
        : java.util.Collections.unmodifiableList(new java.util.ArrayList<InventorySlot>(equipment));
  }
}
