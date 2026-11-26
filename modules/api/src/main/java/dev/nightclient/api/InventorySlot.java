package dev.nightclient.api;

public final class InventorySlot {
  public final int slot;
  public final String itemId;
  public final String name;
  public final int count;
  public final int damage;
  public final int maxDamage;
  public final float damageFraction;
  public final boolean enchanted;

  public InventorySlot(int slot, String itemId, String name, int count, int damage, int maxDamage) {
    this(slot, itemId, name, count, damage, maxDamage, false);
  }

  public InventorySlot(int slot, String itemId, String name, int count, int damage, int maxDamage,
      boolean enchanted) {
    if ((slot < 0 || slot > 35) && (slot < 100 || slot > 103) && (slot < 200 || slot > 205)
        && (slot < 300 || slot > 355) && (slot < 400 || slot > 463))
      throw new IllegalArgumentException(
          "Inventory slot must be 0–35, armor slot 100–103, target equipment 200–205, tooltip peek 300–355, or map item 400–463");
    this.slot = slot;
    this.itemId = itemId == null ? "minecraft:air" : itemId;
    this.name = name == null ? "" : name;
    this.count = Math.max(0, count);
    this.maxDamage = Math.max(0, maxDamage);
    this.damage = Math.max(0, Math.min(this.maxDamage, damage));
    this.damageFraction = this.maxDamage == 0 ? 0 : (float) this.damage / this.maxDamage;
    this.enchanted = enchanted;
  }

  public boolean empty() {
    return count == 0 || "minecraft:air".equals(itemId);
  }
}
