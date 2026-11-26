package dev.nightclient.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.renderer.model.CosmeticModelMesh;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
@@CM_BRIDGE_IMPORTS@@

public final class CustomModelBridge {
  public static final int MAX_WORLD_DRAWS = 64;
  public static final int MAX_HAND_DRAWS = 8;
  public static final int UBO_STRIDE = 256;

  private static final long START_NANOS = System.nanoTime();
  private static final float[] IDENTITY_MAT = {
      1f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f,
      0f, 0f, 1f, 0f,
      0f, 0f, 0f, 1f
  };

  public static final class Snapshot {
    public final CosmeticModelMesh weaponMesh;
    public final String weaponTarget;
    public final int weaponColor;
    public final float weaponScale;
    public final boolean weaponFirstPerson;
    public final boolean weaponThirdPerson;
    public final CosmeticModelMesh armorMesh;
    public final String armorTrigger;
    public final int armorSlotMask;
    public final boolean armorHideVanilla;
    public final int armorColor;
    public final float armorScale;

    Snapshot(
        CosmeticModelMesh weaponMesh,
        String weaponTarget,
        int weaponColor,
        float weaponScale,
        boolean weaponFirstPerson,
        boolean weaponThirdPerson,
        CosmeticModelMesh armorMesh,
        String armorTrigger,
        int armorSlotMask,
        boolean armorHideVanilla,
        int armorColor,
        float armorScale) {
      this.weaponMesh = weaponMesh;
      this.weaponTarget = weaponTarget == null ? "swords" : weaponTarget;
      this.weaponColor = weaponColor;
      this.weaponScale = weaponScale;
      this.weaponFirstPerson = weaponFirstPerson;
      this.weaponThirdPerson = weaponThirdPerson;
      this.armorMesh = armorMesh;
      this.armorTrigger = armorTrigger == null ? "always" : armorTrigger;
      this.armorSlotMask = armorSlotMask;
      this.armorHideVanilla = armorHideVanilla;
      this.armorColor = armorColor;
      this.armorScale = armorScale;
    }
  }

  public static final class DrawCmd {
    public CosmeticModelMesh mesh;
    public int boneId;
    public final float[] modelView = new float[16];
    public final float[] normalMat = new float[16];
    public float lightR = 1f, lightG = 1f, lightB = 1f, hurtAlpha = 0f;
    public float accentR = 0.22f, accentG = 0.78f, accentB = 1f, timeSec = 0f;
    public float auraBoost = 1f, emissiveBoost = 1f;
  }

  private static volatile Snapshot current =
      new Snapshot(null, "swords", 0x38D4FF, 1.0f, true, true, null, "always", 0xF, true, 0x38D4FF, 1.0f);

  public static final DrawCmd[] WORLD_DRAWS = new DrawCmd[MAX_WORLD_DRAWS];
  public static final DrawCmd[] HAND_DRAWS = new DrawCmd[MAX_HAND_DRAWS];
  public static final float[] WORLD_PROJ = new float[16];
  public static final float[] HAND_PROJ = new float[16];

  private static int worldDrawCount;
  private static int handDrawCount;
  private static boolean worldPassActive;

  private static final FloatBuffer TEMP_FB =
      ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
  private static final float[] TMP_GLOBAL_MV = new float[16];
  private static final float[] TMP_POSE = new float[16];
  private static final float[] TMP_LOCAL = new float[16];
  private static final float[] TMP_COMBINED = new float[16];

  private static ItemStack pendingFpStack = ItemStack.EMPTY;
  private static boolean pendingFpRightHand = true;
  private static Object pending3pEntity;
  private static ItemStack pending3pStack = ItemStack.EMPTY;
  private static HumanoidArm pending3pArm = HumanoidArm.RIGHT;

  static {
    for (int i = 0; i < MAX_WORLD_DRAWS; i++) WORLD_DRAWS[i] = new DrawCmd();
    for (int i = 0; i < MAX_HAND_DRAWS; i++) HAND_DRAWS[i] = new DrawCmd();
    System.arraycopy(IDENTITY_MAT, 0, WORLD_PROJ, 0, 16);
    System.arraycopy(IDENTITY_MAT, 0, HAND_PROJ, 0, 16);
  }

  private CustomModelBridge() {}

  public static void update(
      CosmeticModelMesh weaponMesh,
      String weaponTarget,
      int weaponColor,
      float weaponScale,
      boolean weaponFirstPerson,
      boolean weaponThirdPerson,
      CosmeticModelMesh armorMesh,
      String armorTrigger,
      int armorSlotMask,
      boolean armorHideVanilla,
      int armorColor,
      float armorScale) {
    current =
        new Snapshot(
            weaponMesh,
            weaponTarget,
            weaponColor,
            weaponScale,
            weaponFirstPerson,
            weaponThirdPerson,
            null,
            "always",
            0,
            false,
            armorColor,
            armorScale);
  }

  public static Snapshot snapshot() {
    return current;
  }

  public static void beginWorldPass(float[] projection) {
    worldDrawCount = 0;
    worldPassActive = true;
    if (projection != null && projection.length >= 16) {
      System.arraycopy(projection, 0, WORLD_PROJ, 0, 16);
    }
  }

  public static void endWorldPass() {
    worldPassActive = false;
    worldDrawCount = 0;
  }

  public static int worldDrawCount() {
    return worldDrawCount;
  }

  public static void beginHandPass() {
    handDrawCount = 0;
  }

  public static void endHandPass() {
    handDrawCount = 0;
  }

  public static int handDrawCount() {
    return handDrawCount;
  }

  public static void stashFirstPersonContext(ItemStack stack, boolean rightHand) {
    pendingFpStack = stack == null ? ItemStack.EMPTY : stack;
    pendingFpRightHand = rightHand;
  }

  public static void clearFirstPersonContext() {
    pendingFpStack = ItemStack.EMPTY;
  }

  public static boolean capturePendingFirstPerson(PoseStack poseStack, int packedLight) {
    return captureFirstPersonWeapon(poseStack, pendingFpStack, pendingFpRightHand, packedLight);
  }

  public static void stashThirdPersonContext(Object entityOrState, ItemStack stack, HumanoidArm arm) {
    pending3pEntity = entityOrState;
    pending3pStack = stack;
    pending3pArm = arm == null ? HumanoidArm.RIGHT : arm;
  }

  public static void clearThirdPersonContext() {
    pending3pEntity = null;
    pending3pStack = ItemStack.EMPTY;
  }

  public static boolean capturePendingThirdPerson(PoseStack poseStack, int packedLight) {
    if (pending3pEntity == null) return false;
    ItemStack stack = pending3pStack;
    if (stack == null) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.player == null || !isLocalPlayer(pending3pEntity)) return false;
      EquipmentSlot slot =
          pending3pArm == mc.player.getMainArm() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
      stack = mc.player.getItemBySlot(slot);
    }
    return captureThirdPersonWeapon(poseStack, pending3pEntity, pending3pArm, stack, packedLight);
  }

  public static boolean isLocalPlayer(Object entityOrState) {
    if (entityOrState == null) return false;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) return false;
    @@CM_IS_LOCAL_PLAYER@@
  }

  public static boolean matchesWeapon(ItemStack stack, String target) {
    if (stack == null || stack.isEmpty()) return false;
    if ("all".equals(target)) return true;
    String rawPath = @@CM_ITEM_PATH_EXPR@@;
    if (rawPath == null || rawPath.isEmpty()) return false;
    String path = rawPath.toLowerCase(Locale.ROOT);
    boolean isSword = path.endsWith("_sword") || "sword".equals(path);
    if ("swords".equals(target)) return isSword;
    boolean isMelee =
        isSword
            || path.endsWith("_axe")
            || "trident".equals(path)
            || "mace".equals(path)
            || path.endsWith("_spear");
    if ("weapons".equals(target) || "melee".equals(target)) return isMelee;
    if ("tools".equals(target)) {
      return isMelee
          || path.endsWith("_pickaxe")
          || path.endsWith("_shovel")
          || path.endsWith("_hoe")
          || "shears".equals(path)
          || "bow".equals(path)
          || "crossbow".equals(path);
    }
    return isSword;
  }

  public static boolean captureFirstPersonWeapon(
      PoseStack poseStack, ItemStack stack, boolean rightHand, int packedLight) {
    Snapshot s = current;
    if (s.weaponMesh == null || !s.weaponFirstPerson || poseStack == null) return false;
    if (!matchesWeapon(stack, s.weaponTarget)) return false;

    @@CM_READ_HAND_PROJ@@
    readGlobalModelView(TMP_GLOBAL_MV);
    readPoseMatrix(poseStack, TMP_POSE);
    mulMat4(TMP_GLOBAL_MV, TMP_POSE, TMP_COMBINED);

    float scale = 0.64f * clampScale(s.weaponScale);
    buildFirstPersonLocal(rightHand, scale, TMP_LOCAL);

    if (handDrawCount < MAX_HAND_DRAWS) {
      DrawCmd cmd = HAND_DRAWS[handDrawCount++];
      fillDrawCmd(cmd, s.weaponMesh, CosmeticModelMesh.BONE_WEAPON, TMP_COMBINED, TMP_LOCAL, packedLight, s.weaponColor, 0f);
    }
    return true;
  }

  public static boolean captureThirdPersonWeapon(
      PoseStack poseStack,
      Object entityOrState,
      HumanoidArm arm,
      ItemStack stack,
      int packedLight) {
    if (!worldPassActive) return false;
    Snapshot s = current;
    if (s.weaponMesh == null || !s.weaponThirdPerson || poseStack == null) return false;
    if (!isLocalPlayer(entityOrState)) return false;
    if (!matchesWeapon(stack, s.weaponTarget)) return false;

    readGlobalModelView(TMP_GLOBAL_MV);
    readPoseMatrix(poseStack, TMP_POSE);
    mulMat4(TMP_GLOBAL_MV, TMP_POSE, TMP_COMBINED);

    float scale = 0.78f * clampScale(s.weaponScale);
    buildThirdPersonLocal(arm != HumanoidArm.LEFT, scale, TMP_LOCAL);

    if (worldDrawCount < MAX_WORLD_DRAWS) {
      DrawCmd cmd = WORLD_DRAWS[worldDrawCount++];
      fillDrawCmd(cmd, s.weaponMesh, CosmeticModelMesh.BONE_WEAPON, TMP_COMBINED, TMP_LOCAL, packedLight, s.weaponColor, localPlayerHurtAlpha());
    }
    return true;
  }

  public static int activeArmorSlotMaskForLocal(Object entityOrState) {
    return 0;
  }

  public static boolean shouldHideVanillaSlot(int activeSlotMask, EquipmentSlot slot) {
    return false;
  }

  public static int captureArmorSet(
      PoseStack poseStack, Object entityOrState, HumanoidModel<?> model, int packedLight) {
    return 0;
  }

  private static void captureArmorBone(
      PoseStack poseStack,
      ModelPart part,
      CosmeticModelMesh mesh,
      int boneId,
      int packedLight,
      int accentRgb,
      float hurtAlpha) {
    if (part == null || !mesh.hasBone(boneId) || worldDrawCount >= MAX_WORLD_DRAWS) return;
    poseStack.pushPose();
    try {
      part.translateAndRotate(poseStack);
      readPoseMatrix(poseStack, TMP_POSE);
    } finally {
      poseStack.popPose();
    }
    mulMat4(TMP_GLOBAL_MV, TMP_POSE, TMP_COMBINED);
    DrawCmd cmd = WORLD_DRAWS[worldDrawCount++];
    fillDrawCmd(cmd, mesh, boneId, TMP_COMBINED, TMP_LOCAL, packedLight, accentRgb, hurtAlpha);
  }

  private static void fillDrawCmd(
      DrawCmd cmd,
      CosmeticModelMesh mesh,
      int boneId,
      float[] parentMv,
      float[] localMat,
      int packedLight,
      int accentRgb,
      float hurtAlpha) {
    cmd.mesh = mesh;
    cmd.boneId = boneId;
    mulMat4(parentMv, localMat, cmd.modelView);
    computeNormalMatrix(cmd.modelView, cmd.normalMat);

    int block = (packedLight & 0xFFFF) >> 4;
    int sky = (packedLight >>> 20) & 0xF;
    float bl = Math.max(0f, Math.min(1f, block / 15f));
    float sl = Math.max(0f, Math.min(1f, sky / 15f));
    float lum = Math.max(0.24f, Math.min(1.0f, Math.max(bl * 0.96f, sl * 0.92f) + 0.16f));
    float warm = Math.max(0f, bl - sl * 0.85f);
    cmd.lightR = Math.min(1.12f, lum * (1.0f + 0.09f * warm));
    cmd.lightG = Math.min(1.06f, lum * (1.0f + 0.02f * warm));
    cmd.lightB = Math.max(0.18f, lum * (1.0f - 0.09f * warm));
    cmd.hurtAlpha = hurtAlpha;

    cmd.accentR = ((accentRgb >>> 16) & 0xFF) / 255f;
    cmd.accentG = ((accentRgb >>> 8) & 0xFF) / 255f;
    cmd.accentB = (accentRgb & 0xFF) / 255f;
    cmd.timeSec = (float) (((System.nanoTime() - START_NANOS) % 3_600_000_000_000L) * 1e-9d);
    cmd.auraBoost = 1.0f;
    cmd.emissiveBoost = 1.0f;
  }

  public static void packDrawUbo(
      DrawCmd cmd, float[] fallbackProj, ByteBuffer dst, int byteOffset, boolean isAdditivePass) {
    int p = byteOffset;
    for (int i = 0; i < 16; i++) {
      dst.putFloat(p, cmd.modelView[i]);
      p += 4;
    }
    for (int i = 0; i < 16; i++) {
      dst.putFloat(p, cmd.normalMat[i]);
      p += 4;
    }
    dst.putFloat(p, cmd.lightR); p += 4;
    dst.putFloat(p, cmd.lightG); p += 4;
    dst.putFloat(p, cmd.lightB); p += 4;
    dst.putFloat(p, cmd.hurtAlpha); p += 4;

    dst.putFloat(p, cmd.accentR); p += 4;
    dst.putFloat(p, cmd.accentG); p += 4;
    dst.putFloat(p, cmd.accentB); p += 4;
    dst.putFloat(p, cmd.timeSec); p += 4;

    dst.putFloat(p, 1.0f); p += 4;
    dst.putFloat(p, 0.18f); p += 4;
    dst.putFloat(p, 0.15f); p += 4;
    dst.putFloat(p, isAdditivePass ? 1.0f : 0.0f); p += 4;

    dst.putFloat(p, cmd.auraBoost); p += 4;
    dst.putFloat(p, cmd.emissiveBoost); p += 4;
    dst.putFloat(p, 0.0f); p += 4;
    dst.putFloat(p, 0.0f); p += 4;

    float[] proj = fallbackProj != null ? fallbackProj : IDENTITY_MAT;
    for (int i = 0; i < 16; i++) {
      dst.putFloat(p, proj[i]);
      p += 4;
    }
  }

  private static float localPlayerHurtAlpha() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) return 0f;
    return mc.player.hurtTime > 0 ? 0.42f : 0f;
  }

  private static boolean triggerMatches(String trigger, boolean slotEquipped, boolean anyArmor) {
    if ("always".equals(trigger)) return true;
    if ("any_armor".equals(trigger)) return anyArmor;
    return slotEquipped;
  }

  private static int slotBit(EquipmentSlot slot) {
    if (slot == EquipmentSlot.HEAD) return 1;
    if (slot == EquipmentSlot.CHEST) return 2;
    if (slot == EquipmentSlot.LEGS) return 4;
    if (slot == EquipmentSlot.FEET) return 8;
    return 0;
  }

  private static float clampScale(float s) {
    if (Float.isNaN(s) || Float.isInfinite(s)) return 1f;
    return Math.max(0.25f, Math.min(2.5f, s));
  }

  private static void buildFirstPersonLocal(boolean rightHand, float scale, float[] out) {
    float tx = rightHand ? 0.05f : -0.05f;
    float ty = -0.045f;
    float tz = -0.05f;
    float ax = (float) Math.toRadians(14.0);
    float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
    out[0] = scale;
    out[1] = 0f;
    out[2] = 0f;
    out[3] = 0f;

    out[4] = 0f;
    out[5] = cx * scale;
    out[6] = sx * scale;
    out[7] = 0f;

    out[8] = 0f;
    out[9] = -sx * scale;
    out[10] = cx * scale;
    out[11] = 0f;

    out[12] = tx;
    out[13] = ty;
    out[14] = tz;
    out[15] = 1f;
  }

  private static void buildThirdPersonLocal(boolean rightHand, float scale, float[] out) {
    float ax = (float) Math.toRadians(-10.0);
    float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
    out[0] = scale;
    out[1] = 0f;
    out[2] = 0f;
    out[3] = 0f;

    out[4] = 0f;
    out[5] = cx * scale;
    out[6] = sx * scale;
    out[7] = 0f;

    out[8] = 0f;
    out[9] = -sx * scale;
    out[10] = cx * scale;
    out[11] = 0f;

    out[12] = 0f;
    out[13] = -0.082f;
    out[14] = 0.090f;
    out[15] = 1f;
  }

  private static void buildArmorBoneLocal(float scale, float[] out) {
    for (int i = 0; i < 16; i++) out[i] = 0f;
    out[0] = scale;
    out[5] = -scale;
    out[10] = -scale;
    out[15] = 1f;
  }

  private static void readPoseMatrix(PoseStack poseStack, float[] out) {
    @@CM_READ_POSE_MATRIX@@
  }

  private static void readGlobalModelView(float[] out) {
    @@CM_READ_CURRENT_MV@@
  }

  private static void mulMat4(float[] a, float[] b, float[] out) {
    for (int col = 0; col < 4; col++) {
      int c = col * 4;
      float b0 = b[c], b1 = b[c + 1], b2 = b[c + 2], b3 = b[c + 3];
      out[c]     = a[0] * b0 + a[4] * b1 + a[8]  * b2 + a[12] * b3;
      out[c + 1] = a[1] * b0 + a[5] * b1 + a[9]  * b2 + a[13] * b3;
      out[c + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3;
      out[c + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
    }
  }

  private static void computeNormalMatrix(float[] m, float[] out) {
    float a00 = m[0], a01 = m[4], a02 = m[8];
    float a10 = m[1], a11 = m[5], a12 = m[9];
    float a20 = m[2], a21 = m[6], a22 = m[10];

    float c00 = a11 * a22 - a12 * a21;
    float c01 = -(a10 * a22 - a12 * a20);
    float c02 = a10 * a21 - a11 * a20;

    float c10 = -(a01 * a22 - a02 * a21);
    float c11 = a00 * a22 - a02 * a20;
    float c12 = -(a00 * a21 - a01 * a20);

    float c20 = a01 * a12 - a02 * a11;
    float c21 = -(a00 * a12 - a02 * a10);
    float c22 = a00 * a11 - a01 * a10;

    float det = a00 * c00 + a01 * c01 + a02 * c02;
    if (Math.abs(det) < 1e-12f) {
      System.arraycopy(IDENTITY_MAT, 0, out, 0, 16);
      return;
    }
    float inv = 1.0f / det;
    out[0] = c00 * inv; out[1] = c10 * inv; out[2] = c20 * inv; out[3] = 0f;
    out[4] = c01 * inv; out[5] = c11 * inv; out[6] = c21 * inv; out[7] = 0f;
    out[8] = c02 * inv; out[9] = c12 * inv; out[10] = c22 * inv; out[11] = 0f;
    out[12] = 0f;       out[13] = 0f;       out[14] = 0f;        out[15] = 1f;
  }

  public static String chooseModelFile(String title) {
    String lang = dev.nightclient.runtime.ClientRuntime.language();
    String safeTitle =
        sanitizeTitle(title, dev.nightclient.i18n.I18n.text(lang, "filePicker.default_model_title"));
    String[] patterns = {"*.glb", "*.obj"};
    String filterDesc = dev.nightclient.i18n.I18n.text(lang, "filePicker.models") + " (*.glb, *.obj)";
    try {
      Class<?> tinyfd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
      Class<?> pointerBuffer = Class.forName("org.lwjgl.PointerBuffer");
      Method method =
          tinyfd.getMethod(
              "tinyfd_openFileDialog",
              CharSequence.class,
              CharSequence.class,
              pointerBuffer,
              CharSequence.class,
              boolean.class);
      Object picked = null;
      boolean invoked = false;
      try {
        Class<?> stackClass = Class.forName("org.lwjgl.system.MemoryStack");
        Object stack = stackClass.getMethod("stackPush").invoke(null);
        try {
          Object filters = stackClass.getMethod("mallocPointer", int.class).invoke(stack, patterns.length);
          Method utf8 = stackClass.getMethod("UTF8", CharSequence.class);
          Method put = pointerBuffer.getMethod("put", ByteBuffer.class);
          for (String pattern : patterns) {
            put.invoke(filters, utf8.invoke(stack, pattern));
          }
          pointerBuffer.getMethod("flip").invoke(filters);
          picked = method.invoke(null, safeTitle, "", filters, filterDesc, false);
          invoked = true;
        } finally {
          stackClass.getMethod("close").invoke(stack);
        }
      } catch (Throwable stackFailure) {
        if (!invoked) {
          picked = method.invoke(null, safeTitle, "", null, filterDesc, false);
          invoked = true;
        }
      }
      if (invoked) {
        return picked == null ? "" : picked.toString().trim();
      }
    } catch (Throwable ignored) {
    }
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("mac")) {
      try {
        Class<?> ge = Class.forName("java.awt.GraphicsEnvironment");
        if (!Boolean.TRUE.equals(ge.getMethod("isHeadless").invoke(null))) {
          Class<?> fdClass = Class.forName("java.awt.FileDialog");
          Class<?> frameClass = Class.forName("java.awt.Frame");
          Object dialog =
              fdClass.getConstructor(frameClass, String.class, int.class).newInstance(null, safeTitle, 0);
          fdClass.getMethod("setVisible", boolean.class).invoke(dialog, Boolean.TRUE);
          Object file = fdClass.getMethod("getFile").invoke(dialog);
          Object dir = fdClass.getMethod("getDirectory").invoke(dialog);
          fdClass.getMethod("dispose").invoke(dialog);
          return file == null ? "" : (dir == null ? file.toString() : dir.toString() + file.toString());
        }
      } catch (Throwable ignored) {
      }
    }
    return "__NO_PICKER__";
  }

  private static String sanitizeTitle(String title, String fallback) {
    if (title == null || title.trim().isEmpty()) return fallback;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < title.length() && out.length() < 64; i++) {
      char c = title.charAt(i);
      if (c >= ' ' && c != '"' && c != '\'' && c != '`' && c != '\\' && c != '$') out.append(c);
    }
    String text = out.toString().trim();
    return text.isEmpty() ? fallback : text;
  }
}
