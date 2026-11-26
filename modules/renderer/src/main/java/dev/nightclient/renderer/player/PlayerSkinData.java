package dev.nightclient.renderer.player;

import dev.nightclient.api.PlayerSkin;
import dev.nightclient.renderer.model.CosmeticModelMesh;

public final class PlayerSkinData implements PlayerSkin {
  public static final int SLOT_HEAD = 1;
  public static final int SLOT_CHEST = 2;
  public static final int SLOT_LEGS = 4;
  public static final int SLOT_BOOTS = 8;
  public static final int SLOT_ALL = SLOT_HEAD | SLOT_CHEST | SLOT_LEGS | SLOT_BOOTS;

  public final int[] body;
  public final int bodyWidth;
  public final int bodyHeight;
  public final int[] cape;
  public final int capeWidth;
  public final int capeHeight;
  public final boolean slim;

  public final CosmeticModelMesh weaponMesh;
  public final int weaponColor;
  public final float weaponScale;
  public final CosmeticModelMesh armorMesh;
  public final int armorColor;
  public final float armorScale;
  public final int armorSlotMask;

  public final CosmeticModelMesh cardModel;
  public final int cardAccentColor;
  public final boolean cardIsArmor;

  public PlayerSkinData(int[] body, int[] cape, boolean slim) {
    this(body, BODY_SIZE, BODY_SIZE, cape, cape == null ? 0 : CAPE_WIDTH,
        cape == null ? 0 : CAPE_HEIGHT, slim);
  }

  public PlayerSkinData(int[] body, int bodyWidth, int bodyHeight, int[] cape, int capeWidth,
      int capeHeight, boolean slim) {
    this(body, bodyWidth, bodyHeight, cape, capeWidth, capeHeight, slim,
        null, 0xff38bdf8, 1f, null, 0xff38bdf8, 1f, SLOT_ALL, null, 0xff38bdf8, false);
  }

  private PlayerSkinData(int[] body, int bodyWidth, int bodyHeight, int[] cape, int capeWidth,
      int capeHeight, boolean slim, CosmeticModelMesh weaponMesh, int weaponColor,
      float weaponScale, CosmeticModelMesh armorMesh, int armorColor, float armorScale,
      int armorSlotMask, CosmeticModelMesh cardModel, int cardAccentColor, boolean cardIsArmor) {
    if (body == null || bodyWidth <= 0 || bodyHeight <= 0 || bodyWidth > MAX_TEXTURE_SIZE
        || bodyHeight > MAX_TEXTURE_SIZE || (long) bodyWidth * bodyHeight != body.length)
      throw new IllegalArgumentException(
          "body pixels do not match " + bodyWidth + "x" + bodyHeight);
    if (cape != null) {
      if (capeWidth <= 0 || capeHeight <= 0 || capeWidth > MAX_TEXTURE_SIZE
          || capeHeight > MAX_TEXTURE_SIZE || (long) capeWidth * capeHeight != cape.length)
        throw new IllegalArgumentException(
            "cape pixels do not match " + capeWidth + "x" + capeHeight);
    } else {
      capeWidth = 0;
      capeHeight = 0;
    }
    this.body = body;
    this.bodyWidth = bodyWidth;
    this.bodyHeight = bodyHeight;
    this.cape = cape;
    this.capeWidth = capeWidth;
    this.capeHeight = capeHeight;
    this.slim = slim;
    this.weaponMesh = weaponMesh;
    this.weaponColor = weaponColor;
    this.weaponScale = weaponScale;
    this.armorMesh = armorMesh;
    this.armorColor = armorColor;
    this.armorScale = armorScale;
    this.armorSlotMask = armorSlotMask;
    this.cardModel = cardModel;
    this.cardAccentColor = cardAccentColor;
    this.cardIsArmor = cardIsArmor;
  }

  public PlayerSkinData(int[] body, boolean slim) {
    this(body, null, slim);
  }

  public static PlayerSkinData forModelCard(CosmeticModelMesh mesh, int accentColor,
      boolean isArmor) {
    return new PlayerSkinData(new int[] {0}, 1, 1, null, 0, 0, false,
        null, 0xff38bdf8, 1f, null, 0xff38bdf8, 1f, SLOT_ALL, mesh, accentColor, isArmor);
  }

  public PlayerSkinData withCosmeticModels(CosmeticModelMesh weapon, int weaponColor,
      float weaponScale, CosmeticModelMesh armor, int armorColor, float armorScale,
      int armorSlotMask) {
    return new PlayerSkinData(body, bodyWidth, bodyHeight, cape, capeWidth, capeHeight, slim,
        weapon, weaponColor, weaponScale, armor, armorColor, armorScale, armorSlotMask,
        cardModel, cardAccentColor, cardIsArmor);
  }

  public PlayerSkinData withCape(int[] capePixels, int width, int height) {
    return new PlayerSkinData(body, bodyWidth, bodyHeight, capePixels, width, height, slim,
        weaponMesh, weaponColor, weaponScale, armorMesh, armorColor, armorScale, armorSlotMask,
        cardModel, cardAccentColor, cardIsArmor);
  }

  public PlayerSkinData withSlim(boolean value) {
    return value == slim
        ? this
        : new PlayerSkinData(body, bodyWidth, bodyHeight, cape, capeWidth, capeHeight, value,
            weaponMesh, weaponColor, weaponScale, armorMesh, armorColor, armorScale, armorSlotMask,
            cardModel, cardAccentColor, cardIsArmor);
  }

  @Override
  public int[] bodyPixels() {
    return body;
  }

  @Override
  public int bodyWidth() {
    return bodyWidth;
  }

  @Override
  public int bodyHeight() {
    return bodyHeight;
  }

  @Override
  public int[] capePixels() {
    return cape;
  }

  @Override
  public int capeWidth() {
    return cape == null ? CAPE_WIDTH : capeWidth;
  }

  @Override
  public int capeHeight() {
    return cape == null ? CAPE_HEIGHT : capeHeight;
  }

  @Override
  public boolean slimModel() {
    return slim;
  }
}
