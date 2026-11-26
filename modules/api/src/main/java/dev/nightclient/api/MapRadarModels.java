package dev.nightclient.api;

import java.util.Collections;
import java.util.List;

public final class MapRadarModels {
  private MapRadarModels() {}

  public static final int MOB_HOSTILE = 0;
  public static final int MOB_PASSIVE = 1;
  public static final int MOB_TNT = 2;
  public static final int MOB_CRYSTAL = 3;
  public static final int MOB_NEUTRAL = 4;
  public static final int MOB_BOSS = 5;
  public static final int MOB_OTHER = 6;

  public static final class PlayerEntry {
    public final String uuid;
    public final String name;
    public final String skinTextureId;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final boolean invisible;
    public final boolean sneaking;
    public final boolean self;
    public final float health;
    public final float maxHealth;

    public PlayerEntry(String uuid, String name, String skinTextureId, double x, double y, double z,
        float yaw, boolean invisible, boolean sneaking, boolean self, float health,
        float maxHealth) {
      this.uuid = uuid == null ? "" : uuid;
      this.name = name == null ? "" : name;
      this.skinTextureId = skinTextureId == null ? "" : skinTextureId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.invisible = invisible;
      this.sneaking = sneaking;
      this.self = self;
      this.health = health;
      this.maxHealth = Math.max(1f, maxHealth);
    }
  }

  public static final class ItemEntry {
    public final int entityId;
    public final int cacheSlot;
    public final String itemId;
    public final String displayName;
    public final int totalCount;
    public final int mergedStacks;
    public final double x;
    public final double y;
    public final double z;
    public final boolean valuable;

    public ItemEntry(int entityId, int cacheSlot, String itemId, String displayName, int totalCount,
        int mergedStacks, double x, double y, double z, boolean valuable) {
      this.entityId = entityId;
      this.cacheSlot = cacheSlot;
      this.itemId = itemId == null ? "" : itemId;
      this.displayName = displayName == null ? "" : displayName;
      this.totalCount = Math.max(1, totalCount);
      this.mergedStacks = Math.max(1, mergedStacks);
      this.x = x;
      this.y = y;
      this.z = z;
      this.valuable = valuable;
    }

    public InventorySlot asInventorySlot() {
      return new InventorySlot(cacheSlot, itemId, displayName, totalCount, 0, 0, false);
    }
  }

  public static final class OverlayLayer {
    public final String textureId;
    public final float u0;
    public final float v0;
    public final float u1;
    public final float v1;
    public final float dx0;
    public final float dy0;
    public final float dx1;
    public final float dy1;
    public final int tintRgb;

    public OverlayLayer(String textureId, float u0, float v0, float u1, float v1) {
      this(textureId, u0, v0, u1, v1, 0f, 0f, 1f, 1f, 0xFFFFFF);
    }

    public OverlayLayer(String textureId, float u0, float v0, float u1, float v1, float dx0,
        float dy0, float dx1, float dy1, int tintRgb) {
      this.textureId = textureId == null ? "" : textureId;
      this.u0 = u0;
      this.v0 = v0;
      this.u1 = u1;
      this.v1 = v1;
      this.dx0 = dx0;
      this.dy0 = dy0;
      this.dx1 = dx1;
      this.dy1 = dy1;
      this.tintRgb = tintRgb & 0xFFFFFF;
    }
  }

  public static final class MobEntry {
    public final int entityId;
    public final int kind;
    public final String entityType;
    public final String name;
    public final boolean baby;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final String textureId;
    public final float u0;
    public final float v0;
    public final float u1;
    public final float v1;
    public final String itemIconId;
    public final OverlayLayer[] overlays;
    public final int tintRgb;
    public final String composedKey;
    public final int[] composedPixels;
    public final int composedSize;
    public final long composedRevision;

    public MobEntry(int entityId, int kind, double x, double y, double z, float yaw) {
      this(entityId, kind, "", "", false, x, y, z, yaw, "", 0f, 0f, 0f, 0f, "", null, 0xFFFFFF,
          "", null, 0, 0L);
    }

    public MobEntry(int entityId, int kind, String entityType, String name, boolean baby,
        double x, double y, double z, float yaw) {
      this(entityId, kind, entityType, name, baby, x, y, z, yaw, "", 0f, 0f, 0f, 0f, "", null,
          0xFFFFFF, "", null, 0, 0L);
    }

    public MobEntry(int entityId, int kind, String entityType, String name, boolean baby,
        double x, double y, double z, float yaw, String textureId, float u0, float v0, float u1,
        float v1) {
      this(entityId, kind, entityType, name, baby, x, y, z, yaw, textureId, u0, v0, u1, v1, "",
          null, 0xFFFFFF, "", null, 0, 0L);
    }

    public MobEntry(int entityId, int kind, String entityType, String name, boolean baby,
        double x, double y, double z, float yaw, String textureId, float u0, float v0, float u1,
        float v1, String itemIconId, OverlayLayer[] overlays, int tintRgb) {
      this(entityId, kind, entityType, name, baby, x, y, z, yaw, textureId, u0, v0, u1, v1,
          itemIconId, overlays, tintRgb, "", null, 0, 0L);
    }

    public MobEntry(int entityId, int kind, String entityType, String name, boolean baby,
        double x, double y, double z, float yaw, String textureId, float u0, float v0, float u1,
        float v1, String itemIconId, OverlayLayer[] overlays, int tintRgb, String composedKey,
        int[] composedPixels, int composedSize, long composedRevision) {
      this.entityId = entityId;
      this.kind = kind;
      this.entityType = entityType == null ? "" : entityType;
      this.name = name == null ? "" : name;
      this.baby = baby;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.textureId = textureId == null ? "" : textureId;
      this.u0 = u0;
      this.v0 = v0;
      this.u1 = u1;
      this.v1 = v1;
      this.itemIconId = itemIconId == null ? "" : itemIconId;
      this.overlays = overlays;
      this.tintRgb = tintRgb & 0xFFFFFF;
      this.composedKey = composedKey == null ? "" : composedKey;
      this.composedPixels = composedPixels;
      this.composedSize = composedSize;
      this.composedRevision = composedRevision;
    }
  }

  public static final class RadarSnapshot {
    public static final RadarSnapshot EMPTY = new RadarSnapshot(false, 0, 64, 0, 0,
        "minecraft:overworld", "", "", "", Collections.<PlayerEntry>emptyList(),
        Collections.<ItemEntry>emptyList(), Collections.<MobEntry>emptyList());

    public final boolean inWorld;
    public final double playerX;
    public final double playerY;
    public final double playerZ;
    public final float playerYaw;
    public final String dimension;
    public final String biomeName;
    public final String worldId;
    public final String worldLabel;
    public final List<PlayerEntry> players;
    public final List<ItemEntry> items;
    public final List<MobEntry> mobs;

    public RadarSnapshot(boolean inWorld, double playerX, double playerY, double playerZ,
        float playerYaw, String dimension, String biomeName, String worldId, String worldLabel,
        List<PlayerEntry> players, List<ItemEntry> items, List<MobEntry> mobs) {
      this.inWorld = inWorld;
      this.playerX = playerX;
      this.playerY = playerY;
      this.playerZ = playerZ;
      this.playerYaw = playerYaw;
      this.dimension = dimension == null ? "minecraft:overworld" : dimension;
      this.biomeName = biomeName == null ? "" : biomeName;
      this.worldId = worldId == null ? "" : worldId;
      this.worldLabel = worldLabel == null ? "" : worldLabel;
      this.players = players == null ? Collections.<PlayerEntry>emptyList() : players;
      this.items = items == null ? Collections.<ItemEntry>emptyList() : items;
      this.mobs = mobs == null ? Collections.<MobEntry>emptyList() : mobs;
    }
  }
}
