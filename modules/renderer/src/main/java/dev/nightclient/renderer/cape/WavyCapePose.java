package dev.nightclient.renderer.cape;

public final class WavyCapePose {
  public int entityId;
  public double worldX;
  public double worldY;
  public double worldZ;
  public float bodyRot;

  public float bodyX;
  public float bodyY;
  public float bodyZ;
  public float bodyXRot;
  public float bodyYRot;
  public float bodyZRot;

  public float rightLegY = 12.0f;
  public float rightLegZ = 0.0f;
  public float rightLegXRot = 0.0f;
  public float rightLegZRot = 0.0f;

  public float leftLegY = 12.0f;
  public float leftLegZ = 0.0f;
  public float leftLegXRot = 0.0f;
  public float leftLegZRot = 0.0f;

  public boolean crouching;
  public boolean hasChestArmor;
  public boolean showJacket = true;
  public boolean inWater;
  public float swimAmount;
  public float walkPos;
  public float walkSpeed;
  public float rainLevel;
  public float thunderLevel;
  public long timeNanos;
}
