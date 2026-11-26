package dev.nightclient.renderer.player;

import dev.nightclient.api.PlayerSkin;
import dev.nightclient.renderer.model.CosmeticModelMesh;
import java.util.ArrayList;
import java.util.List;

public final class PlayerPreviewRenderer {
  public static final int MAX_PREVIEW_PIXELS = 1_200_000;
  public static final int MAX_CARD_PIXELS = 48_000;
  private static final float MODEL_HEIGHT = 32f;
  private static final float CAMERA_DISTANCE = 78f;
  private static final int CAPE_COLUMNS = 9;
  private static final int CAPE_ROWS = 13;
  private static final float CAPE_TOP = 24f;
  private static final float CAPE_WIDTH = 10f;
  private static final float CAPE_LENGTH = 16f;
  private static final float CAPE_THICKNESS = 1.05f;
  private static final int CAPE_VERTEX_COUNT = CAPE_COLUMNS * CAPE_ROWS;
  private static final int CAPE_GRID_FACES = (CAPE_COLUMNS - 1) * (CAPE_ROWS - 1);
  private static final int CAPE_FACE_COUNT =
      CAPE_GRID_FACES * 2 + (CAPE_ROWS - 1) * 2 + (CAPE_COLUMNS - 1) * 2;
  private static final ThreadLocal<PlayerPreviewRenderer> CARD_RENDERER =
      new ThreadLocal<PlayerPreviewRenderer>() {
        @Override
        protected PlayerPreviewRenderer initialValue() {
          return new PlayerPreviewRenderer();
        }
      };

  private final List<Face> bodyFaces = new ArrayList<Face>();
  private final Cape cape = new Cape();
  private final Face[] capeFaces = new Face[CAPE_FACE_COUNT];
  private final float[] capeOutX = new float[CAPE_VERTEX_COUNT];
  private final float[] capeOutY = new float[CAPE_VERTEX_COUNT];
  private final float[] capeOutZ = new float[CAPE_VERTEX_COUNT];
  private final Face[] frameFaces = new Face[512];
  private final int[] order = new int[512];
  private final float[] orderDepth = new float[512];
  private final float[] projection = new float[4];

  private boolean meshSlim;
  private boolean meshArmored;
  private boolean meshBuilt;
  private int[] bodyPixels;
  private int[] capePixels;
  private int bodyW = 64, bodyH = 64, capeW = 64, capeH = 32;

  private int[] pixels = new int[0];
  private float[] depth = new float[0];
  private int bufferW, bufferH;

  private float lastTime = -1f;

  public PlayerPreviewRenderer() {
    buildBody(false);
    buildCapeFaces();
    cape.reset();
  }

  public static int[] previewSize(float width, float height, float density) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(density) || width <= 0
        || height <= 0)
      return new int[] {0, 0};
    double w = Math.max(2, (double) width * Math.max(1, density));
    double h = Math.max(2, (double) height * Math.max(1, density));
    double factor = Math.min(1, Math.sqrt(MAX_PREVIEW_PIXELS / (w * h)));
    int resultWidth = Math.max(2, (int) Math.min(MAX_PREVIEW_PIXELS / 2, Math.floor(w * factor)));
    int resultHeight =
        Math.max(2, (int) Math.min(MAX_PREVIEW_PIXELS / resultWidth, Math.floor(h * factor)));
    return new int[] {resultWidth, resultHeight};
  }

  public static int[] capeCardSize(float width, float height, float density) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(density) || width <= 0
        || height <= 0)
      return new int[] {0, 0};
    double w = Math.max(2, Math.ceil((double) width * Math.max(1f, density)));
    double h = Math.max(2, Math.ceil((double) height * Math.max(1f, density)));
    double factor = Math.min(1d, Math.sqrt(MAX_CARD_PIXELS / (w * h)));
    int resultWidth = Math.max(2, (int) Math.min(MAX_CARD_PIXELS / 2, Math.floor(w * factor)));
    int resultHeight =
        Math.max(2, (int) Math.min(MAX_CARD_PIXELS / resultWidth, Math.floor(h * factor)));
    return new int[] {resultWidth, resultHeight};
  }

  public static void renderCapeCard(
      int[] capePixels, int capeW, int capeH, int width, int height, int[] out) {
    if (out == null || width <= 0 || height <= 0 || out.length < width * height) return;
    java.util.Arrays.fill(out, 0, width * height, 0);
    if (capePixels == null || capeW <= 0 || capeH <= 0 || capePixels.length < capeW * capeH)
      return;
    CARD_RENDERER.get().renderCard(capePixels, capeW, capeH, width, height, out);
  }

  public static boolean hasModelCard(PlayerSkin skin) {
    return skin instanceof PlayerSkinData && ((PlayerSkinData) skin).cardModel != null;
  }

  public static void renderCosmeticCard(PlayerSkin skin, int width, int height, int[] out) {
    if (out == null || width <= 0 || height <= 0 || out.length < width * height) return;
    java.util.Arrays.fill(out, 0, width * height, 0);
    if (skin == null) return;
    if (skin instanceof PlayerSkinData) {
      PlayerSkinData data = (PlayerSkinData) skin;
      if (data.cardModel != null) {
        CARD_RENDERER.get().renderModelCard(
            data.cardModel, data.cardAccentColor, data.cardIsArmor, width, height, out);
        return;
      }
    }
    if (skin.capePixels() != null) {
      renderCapeCard(skin.capePixels(), skin.capeWidth(), skin.capeHeight(), width, height, out);
    }
  }

  private void renderCard(int[] capePx, int cw, int ch, int width, int height, int[] out) {
    this.capePixels = capePx;
    this.capeW = cw;
    this.capeH = ch;
    cape.reset();
    cape.step(0.035f, 0.55f, 0.65f, 0f);
    updateCapeFaces();

    boolean supersample = (long) width * height <= MAX_CARD_PIXELS;
    int rw = supersample ? width * 2 : width;
    int rh = supersample ? height * 2 : height;
    ensureBuffers(rw, rh);
    java.util.Arrays.fill(pixels, 0);
    java.util.Arrays.fill(depth, 0f);

    float cx = rw * 0.50f;
    float screenCy = rh * 0.48f;
    drawCardShadow(cx, rh * 0.89f, rw, rh);

    float yaw = (float) Math.toRadians(152f);
    float pitch = (float) Math.toRadians(11f);
    float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
    float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
    float fov =
        Math.min(0.84f * rh * CAMERA_DISTANCE / 16.6f, 0.82f * rw * CAMERA_DISTANCE / 11.8f);

    int count = 0;
    for (int i = 0; i < capeFaces.length; i++) {
      Face face = capeFaces[i];
      if (transform(face, cy, sy, cp, sp, cx, screenCy, fov, -2.92f)) frameFaces[count++] = face;
    }
    sortByDepth(count);
    for (int i = 0; i < count; i++) rasterize(frameFaces[order[i]], rw, rh);
    if (supersample)
      downsample(width, height, out);
    else
      System.arraycopy(pixels, 0, out, 0, width * height);
  }

  public void render(PlayerSkin skin, float yawDeg, float pitchDeg, float zoom, float timeSeconds,
      float wind, float yawVelocity, int width, int height, int[] out) {
    if (out == null || width <= 0 || height <= 0 || out.length < width * height) return;
    java.util.Arrays.fill(out, 0, width * height, 0);
    if (skin == null) return;

    bodyPixels = skin.bodyPixels();
    capePixels = skin.capePixels();
    bodyW = Math.max(1, skin.bodyWidth());
    bodyH = Math.max(1, skin.bodyHeight());
    capeW = Math.max(1, skin.capeWidth());
    capeH = Math.max(1, skin.capeHeight());
    if (bodyPixels == null || bodyPixels.length < bodyW * bodyH) return;
    if (capePixels != null && capePixels.length < capeW * capeH) capePixels = null;

    boolean hideCoveredSkin = false;
    int activeArmorSlots = 0;
    if (skin instanceof PlayerSkinData) {
      PlayerSkinData d = (PlayerSkinData) skin;
      if (d.armorMesh != null && d.armorSlotMask != 0) {
        activeArmorSlots = d.armorSlotMask;
        hideCoveredSkin = true;
      }
    }

    if (!meshBuilt || meshSlim != skin.slimModel() || meshArmored != hideCoveredSkin) {
      if (hideCoveredSkin) {
        buildArmoredUnderBody(skin.slimModel());
      } else {
        buildBody(skin.slimModel());
      }
      meshSlim = skin.slimModel();
      meshArmored = hideCoveredSkin;
      meshBuilt = true;
    }

    float time = Math.max(0f, timeSeconds);
    float dt = lastTime < 0 ? 0f : time - lastTime;
    if (dt < 0f || dt > 0.35f) {
      cape.reset();
      dt = 0f;
    }
    cape.step(Math.min(dt, 0.05f), time, clamp(wind, 0f, 2f), yawVelocity);
    lastTime = time;
    updateCapeFaces();

    boolean supersample = (long) width * height <= MAX_PREVIEW_PIXELS;
    int rw = supersample ? width * 2 : width;
    int rh = supersample ? height * 2 : height;
    ensureBuffers(rw, rh);
    java.util.Arrays.fill(pixels, 0);
    java.util.Arrays.fill(depth, 0f);

    float zoomValue = clamp(zoom <= 0f ? 1f : zoom, 0.35f, 3.5f);
    float yaw = (float) Math.toRadians(yawDeg);
    float pitch = (float) Math.toRadians(clamp(pitchDeg, -32f, 42f));
    float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
    float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
    float fov =
        Math.min(0.80f * rh * CAMERA_DISTANCE / MODEL_HEIGHT, 0.78f * rw * CAMERA_DISTANCE / 24f);
    fov *= zoomValue;
    float cx = rw * 0.5f;
    float screenCy = rh * 0.51f;

    int count = 0;
    for (int i = 0; i < bodyFaces.size(); i++) {
      Face face = bodyFaces.get(i);
      if (transform(face, cy, sy, cp, sp, cx, screenCy, fov, 0f)) frameFaces[count++] = face;
    }
    if (capePixels != null) {
      float capeZOffset = (activeArmorSlots & PlayerSkinData.SLOT_CHEST) != 0 ? 1.35f : 0f;
      for (int i = 0; i < capeFaces.length; i++) {
        Face face = capeFaces[i];
        if (transform(face, cy, sy, cp, sp, cx, screenCy, fov, capeZOffset)) frameFaces[count++] = face;
      }
    }
    sortByDepth(count);
    for (int i = 0; i < count; i++) rasterize(frameFaces[order[i]], rw, rh);

    if (skin instanceof PlayerSkinData) {
      PlayerSkinData data = (PlayerSkinData) skin;
      if (data.armorMesh != null && data.armorSlotMask != 0) {
        renderAttachedArmor(data.armorMesh, data.armorColor, data.armorScale, data.armorSlotMask,
            skin.slimModel(), cy, sy, cp, sp, cx, screenCy, fov, rw, rh);
      }
      if (data.weaponMesh != null) {
        renderAttachedWeapon(data.weaponMesh, data.weaponColor, data.weaponScale, skin.slimModel(),
            cy, sy, cp, sp, cx, screenCy, fov, rw, rh);
      }
    }

    if (supersample)
      downsample(width, height, out);
    else
      System.arraycopy(pixels, 0, out, 0, width * height);
  }

  private void renderModelCard(CosmeticModelMesh mesh, int accentColor, boolean isArmor,
      int width, int height, int[] out) {
    if (mesh == null || mesh.preview == null || mesh.preview.triangleCount <= 0) return;
    boolean supersample = (long) width * height <= MAX_CARD_PIXELS;
    int rw = supersample ? width * 2 : width;
    int rh = supersample ? height * 2 : height;
    ensureBuffers(rw, rh);
    java.util.Arrays.fill(pixels, 0);
    java.util.Arrays.fill(depth, 0f);

    float cx = rw * 0.50f;
    float screenCy = rh * 0.51f;
    drawCardShadow(cx, rh * 0.90f, rw, rh);

    if (isArmor) {
      float yaw = (float) Math.toRadians(-24f);
      float pitch = (float) Math.toRadians(10f);
      float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
      float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
      float fov = Math.min(0.84f * rh * CAMERA_DISTANCE / 34f, 0.84f * rw * CAMERA_DISTANCE / 22f);
      bodyPixels = DefaultSkins.classic().bodyPixels();
      bodyW = 64;
      bodyH = 64;
      buildArmoredUnderBody(false);
      int count = 0;
      for (int i = 0; i < bodyFaces.size(); i++) {
        Face face = bodyFaces.get(i);
        if (transform(face, cy, sy, cp, sp, cx, screenCy, fov, 0f)) frameFaces[count++] = face;
      }
      sortByDepth(count);
      for (int i = 0; i < count; i++) rasterize(frameFaces[order[i]], rw, rh);
      renderAttachedArmor(mesh, accentColor, 1f, PlayerSkinData.SLOT_ALL, false,
          cy, sy, cp, sp, cx, screenCy, fov, rw, rh);
    } else {
      float yaw = (float) Math.toRadians(104f);
      float pitch = (float) Math.toRadians(8f);
      float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
      float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
      float midY = (mesh.minY + mesh.maxY) * 0.5f;
      float midZ = (mesh.minZ + mesh.maxZ) * 0.5f;
      float spanY = Math.max(0.5f, mesh.maxY - mesh.minY);
      float spanZ = Math.max(0.25f, mesh.maxZ - mesh.minZ);
      float scale = Math.min(31f / spanY, 21f / spanZ);
      float fov = Math.min(0.90f * rh * CAMERA_DISTANCE / 30f, 0.90f * rw * CAMERA_DISTANCE / 22f);
      float tiltX = (float) Math.toRadians(34f);
      float cxr = (float) Math.cos(tiltX), sxr = (float) Math.sin(tiltX);
      renderPreviewMeshPass(mesh.preview, accentColor, -1, 0,
          0f, -midY * scale, -midZ * scale, scale,
          1f, 0f, 0f, 0f, cxr, -sxr, 0f, sxr, cxr,
          0f, MODEL_HEIGHT * 0.5f, 0f,
          cy, sy, cp, sp, cx, screenCy, fov, rw, rh, false);
      renderPreviewMeshPass(mesh.preview, accentColor, -1, 0,
          0f, -midY * scale, -midZ * scale, scale,
          1f, 0f, 0f, 0f, cxr, -sxr, 0f, sxr, cxr,
          0f, MODEL_HEIGHT * 0.5f, 0f,
          cy, sy, cp, sp, cx, screenCy, fov, rw, rh, true);
    }

    if (supersample)
      downsample(width, height, out);
    else
      System.arraycopy(pixels, 0, out, 0, width * height);
  }

  private void renderAttachedArmor(CosmeticModelMesh mesh, int accentColor, float armorScale,
      int slotMask, boolean slim, float cy, float sy, float cp, float sp, float cx, float screenCy,
      float fov, int rw, int rh) {
    if (mesh == null || mesh.preview == null || mesh.preview.triangleCount <= 0) return;
    float s = 16f * clamp(armorScale, 0.5f, 1.8f);
    for (int pass = 0; pass < 2; pass++) {
      boolean additive = pass == 1;
      renderPreviewMeshPass(mesh.preview, accentColor, slotMask, slim ? 1 : 0,
          0f, 0f, 0f, s,
          1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f,
          0f, 0f, 0f,
          cy, sy, cp, sp, cx, screenCy, fov, rw, rh, additive);
    }
  }

  private void renderAttachedWeapon(CosmeticModelMesh mesh, int accentColor, float weaponScale,
      boolean slim, float cy, float sy, float cp, float sp, float cx, float screenCy, float fov,
      int rw, int rh) {
    if (mesh == null || mesh.preview == null || mesh.preview.triangleCount <= 0) return;
    float s = 13.8f * clamp(weaponScale, 0.25f, 2.5f);
    float handX = slim ? -5.5f : -6.0f;
    float handY = 13.6f;
    float handZ = 1.1f;
    float rx = (float) Math.toRadians(76f);
    float crx = (float) Math.cos(rx), srx = (float) Math.sin(rx);
    float m00 = 1f, m01 = 0f,   m02 = 0f;
    float m10 = 0f, m11 = crx,  m12 = -srx;
    float m20 = 0f, m21 = srx,  m22 = crx;

    for (int pass = 0; pass < 2; pass++) {
      renderPreviewMeshPass(mesh.preview, accentColor, -1, 0,
          0f, 0f, 0f, s,
          m00, m01, m02, m10, m11, m12, m20, m21, m22,
          handX, handY, handZ,
          cy, sy, cp, sp, cx, screenCy, fov, rw, rh, pass == 1);
    }
  }

  private void renderPreviewMeshPass(CosmeticModelMesh.PreviewMesh pm, int accentColor,
      int armorSlotMask, int slimFlag, float preX, float preY, float preZ, float scale,
      float r00, float r01, float r02, float r10, float r11, float r12, float r20, float r21,
      float r22, float postX, float postY, float postZ, float cy, float sy, float cp, float sp,
      float cx, float screenCy, float fov, int rw, int rh, boolean additivePass) {
    int accR = (accentColor >> 16) & 255;
    int accG = (accentColor >> 8) & 255;
    int accB = accentColor & 255;
    float[] pos = pm.positions;
    float[] nrm = pm.normals;
    float[] uvs = pm.uvs;
    int[] texPx = pm.texturePixels;
    int texW = pm.textureWidth;
    int texH = pm.textureHeight;
    boolean hasTex = uvs != null && texPx != null && texW > 1 && texH > 1 && texPx.length >= texW * texH;
    int[] cols = pm.colors;
    byte[] bones = pm.bones;
    int[] mats = pm.materialBits;
    int triCount = pm.triangleCount;

    for (int t = 0; t < triCount; t++) {
      int mb = mats[t];
      int blendMode = (mb >>> 24) & 3;
      boolean isAdd = blendMode == CosmeticModelMesh.BLEND_ADDITIVE;
      if (isAdd != additivePass) continue;

      int bone = bones[t];
      float bx = postX, by = postY, bz = postZ;
      if (armorSlotMask >= 0) {
        if (!isBoneEnabled(bone, armorSlotMask)) continue;
        switch (bone) {
          case CosmeticModelMesh.BONE_HEAD:
          case CosmeticModelMesh.BONE_BODY:
            bx = 0f; by = 24f; bz = 0f;
            break;
          case CosmeticModelMesh.BONE_RIGHT_ARM:
            bx = slimFlag != 0 ? -4.5f : -5f; by = 22f; bz = 0f;
            break;
          case CosmeticModelMesh.BONE_LEFT_ARM:
            bx = slimFlag != 0 ? 4.5f : 5f; by = 22f; bz = 0f;
            break;
          case CosmeticModelMesh.BONE_RIGHT_LEG:
          case CosmeticModelMesh.BONE_RIGHT_BOOT:
            bx = -2f; by = 12f; bz = 0f;
            break;
          case CosmeticModelMesh.BONE_LEFT_LEG:
          case CosmeticModelMesh.BONE_LEFT_BOOT:
            bx = 2f; by = 12f; bz = 0f;
            break;
          default:
            bx = 0f; by = 24f; bz = 0f;
            break;
        }
      }

      int o9 = t * 9;
      float vnx0, vny0, vnz0, vnx1, vny1, vnz1, vnx2, vny2, vnz2;
      {
        float lnx = nrm[o9], lny = nrm[o9 + 1], lnz = nrm[o9 + 2];
        float wnx = r00 * lnx + r01 * lny + r02 * lnz;
        float wny = r10 * lnx + r11 * lny + r12 * lnz;
        float wnz = r20 * lnx + r21 * lny + r22 * lnz;
        vnx0 = wnx * cy + wnz * sy;
        float zt = -wnx * sy + wnz * cy;
        vny0 = wny * cp - zt * sp;
        vnz0 = wny * sp + zt * cp;
      }
      {
        float lnx = nrm[o9 + 3], lny = nrm[o9 + 4], lnz = nrm[o9 + 5];
        float wnx = r00 * lnx + r01 * lny + r02 * lnz;
        float wny = r10 * lnx + r11 * lny + r12 * lnz;
        float wnz = r20 * lnx + r21 * lny + r22 * lnz;
        vnx1 = wnx * cy + wnz * sy;
        float zt = -wnx * sy + wnz * cy;
        vny1 = wny * cp - zt * sp;
        vnz1 = wny * sp + zt * cp;
      }
      {
        float lnx = nrm[o9 + 6], lny = nrm[o9 + 7], lnz = nrm[o9 + 8];
        float wnx = r00 * lnx + r01 * lny + r02 * lnz;
        float wny = r10 * lnx + r11 * lny + r12 * lnz;
        float wnz = r20 * lnx + r21 * lny + r22 * lnz;
        vnx2 = wnx * cy + wnz * sy;
        float zt = -wnx * sy + wnz * cy;
        vny2 = wny * cp - zt * sp;
        vnz2 = wny * sp + zt * cp;
      }
      float vnz = (vnz0 + vnz1 + vnz2) * 0.3333333f;
      if (!isAdd && vnz <= -0.08f && vnz0 <= -0.02f && vnz1 <= -0.02f && vnz2 <= -0.02f) {
        vnx0 = -vnx0; vny0 = -vny0; vnz0 = -vnz0;
        vnx1 = -vnx1; vny1 = -vny1; vnz1 = -vnz1;
        vnx2 = -vnx2; vny2 = -vny2; vnz2 = -vnz2;
        vnz = -vnz;
      }

      float sx0, sy0, iz0, sx1, sy1, iz1, sx2, sy2, iz2;
      {
        float lx = pos[o9] * scale + preX, ly = pos[o9 + 1] * scale + preY, lz = pos[o9 + 2] * scale + preZ;
        float px = r00 * lx + r01 * ly + r02 * lz + bx;
        float py = r10 * lx + r11 * ly + r12 * lz + by - MODEL_HEIGHT * 0.5f;
        float pz = r20 * lx + r21 * ly + r22 * lz + bz;
        float rx = px * cy + pz * sy, rz = -px * sy + pz * cy;
        float ry = py * cp - rz * sp, rz2 = py * sp + rz * cp;
        float camZ = CAMERA_DISTANCE - rz2;
        if (camZ < 2f) continue;
        iz0 = 1f / camZ;
        sx0 = cx + rx * fov * iz0;
        sy0 = screenCy - ry * fov * iz0;
      }
      {
        float lx = pos[o9 + 3] * scale + preX, ly = pos[o9 + 4] * scale + preY, lz = pos[o9 + 5] * scale + preZ;
        float px = r00 * lx + r01 * ly + r02 * lz + bx;
        float py = r10 * lx + r11 * ly + r12 * lz + by - MODEL_HEIGHT * 0.5f;
        float pz = r20 * lx + r21 * ly + r22 * lz + bz;
        float rx = px * cy + pz * sy, rz = -px * sy + pz * cy;
        float ry = py * cp - rz * sp, rz2 = py * sp + rz * cp;
        float camZ = CAMERA_DISTANCE - rz2;
        if (camZ < 2f) continue;
        iz1 = 1f / camZ;
        sx1 = cx + rx * fov * iz1;
        sy1 = screenCy - ry * fov * iz1;
      }
      {
        float lx = pos[o9 + 6] * scale + preX, ly = pos[o9 + 7] * scale + preY, lz = pos[o9 + 8] * scale + preZ;
        float px = r00 * lx + r01 * ly + r02 * lz + bx;
        float py = r10 * lx + r11 * ly + r12 * lz + by - MODEL_HEIGHT * 0.5f;
        float pz = r20 * lx + r21 * ly + r22 * lz + bz;
        float rx = px * cy + pz * sy, rz = -px * sy + pz * cy;
        float ry = py * cp - rz * sp, rz2 = py * sp + rz * cp;
        float camZ = CAMERA_DISTANCE - rz2;
        if (camZ < 2f) continue;
        iz2 = 1f / camZ;
        sx2 = cx + rx * fov * iz2;
        sy2 = screenCy - ry * fov * iz2;
      }

      float area = (sx1 - sx0) * (sy2 - sy0) - (sy1 - sy0) * (sx2 - sx0);
      if (area > -0.05f && area < 0.05f) continue;
      int minX = Math.max(0, (int) Math.floor(Math.min(sx0, Math.min(sx1, sx2))));
      int maxX = Math.min(rw - 1, (int) Math.ceil(Math.max(sx0, Math.max(sx1, sx2))));
      int minY = Math.max(0, (int) Math.floor(Math.min(sy0, Math.min(sy1, sy2))));
      int maxY = Math.min(rh - 1, (int) Math.ceil(Math.max(sy0, Math.max(sy1, sy2))));
      if (minX > maxX || minY > maxY) continue;

      int base = cols[t];
      int br = (base >> 16) & 255, bg = (base >> 8) & 255, bb = base & 255;
      int ba = base >>> 24;
      float metallic = (mb & 255) / 255f;
      float emissive = ((mb >>> 8) & 255) / 255f;
      float tint = ((mb >>> 16) & 255) / 255f;

      int tr = Math.round(br * (1f - tint) + (br * accR / 255f) * tint);
      int tg = Math.round(bg * (1f - tint) + (bg * accG / 255f) * tint);
      int tb = Math.round(bb * (1f - tint) + (bb * accB / 255f) * tint);

      float invArea = 1f / area;
      if (isAdd) {
        float fresnel = (float) Math.pow(Math.max(0f, Math.abs(vnz)), 1.35f);
        float boost = fresnel * (ba / 255f) * 0.85f;
        int addR = Math.min(255, Math.round(accR * boost));
        int addG = Math.min(255, Math.round(accG * boost));
        int addB = Math.min(255, Math.round(accB * boost));
        int addA = Math.min(255, Math.round(ba * fresnel * 0.72f));
        if (addA <= 2) continue;
        for (int py = minY; py <= maxY; py++) {
          float sampleY = py + 0.5f;
          int row = py * rw;
          for (int px = minX; px <= maxX; px++) {
            float sampleX = px + 0.5f;
            float w0 = ((sx1 - sampleX) * (sy2 - sampleY) - (sy1 - sampleY) * (sx2 - sampleX)) * invArea;
            float w1 = ((sx2 - sampleX) * (sy0 - sampleY) - (sy2 - sampleY) * (sx0 - sampleX)) * invArea;
            float w2 = 1f - w0 - w1;
            if (w0 < -0.001f || w1 < -0.001f || w2 < -0.001f) continue;
            float iz = w0 * iz0 + w1 * iz1 + w2 * iz2;
            int idx = row + px;
            if (iz <= depth[idx]) continue;
            int dst = pixels[idx];
            int da = dst >>> 24;
            int dr = Math.min(255, ((dst >> 16) & 255) + addR);
            int dg = Math.min(255, ((dst >> 8) & 255) + addG);
            int db = Math.min(255, (dst & 255) + addB);
            int oa = Math.min(255, da + addA);
            pixels[idx] = (oa << 24) | (dr << 16) | (dg << 8) | db;
          }
        }
      } else {
        float emR = tint > 0.2f ? accR : br;
        float emG = tint > 0.2f ? accG : bg;
        float emB = tint > 0.2f ? accB : bb;
        if (emissive > 0.94f && tint > 0.5f) {
          float core = Math.max(0f, Math.min(1f, (Math.abs(vnz) - 0.15f) / 0.55f));
          emR = emR + (255f - emR) * core;
          emG = emG + (252f - emG) * core;
          emB = emB + (255f - emB) * core;
        }
        float emAddR = emissive > 0.05f ? emR * emissive * 1.35f : 0f;
        float emAddG = emissive > 0.05f ? emG * emissive * 1.35f : 0f;
        float emAddB = emissive > 0.05f ? emB * emissive * 1.35f : 0f;

        float l0 = vertexLight(vnx0, vny0, vnz0, metallic);
        float s0 = vertexSpec(vnx0, vny0, vnz0, metallic);
        float l1 = vertexLight(vnx1, vny1, vnz1, metallic);
        float s1 = vertexSpec(vnx1, vny1, vnz1, metallic);
        float l2 = vertexLight(vnx2, vny2, vnz2, metallic);
        float s2 = vertexSpec(vnx2, vny2, vnz2, metallic);

        int o6 = t * 6;
        boolean triTextured = hasTex && o6 + 5 < uvs.length && uvs[o6] >= 0f;
        if (triTextured) {
          float tu0 = uvs[o6], tv0 = uvs[o6 + 1];
          float tu1 = uvs[o6 + 2], tv1 = uvs[o6 + 3];
          float tu2 = uvs[o6 + 4], tv2 = uvs[o6 + 5];
          float mr0 = (tr / 255f) * l0, mg0 = (tg / 255f) * l0, mb0 = (tb / 255f) * l0;
          float mr1 = (tr / 255f) * l1, mg1 = (tg / 255f) * l1, mb1 = (tb / 255f) * l1;
          float mr2 = (tr / 255f) * l2, mg2 = (tg / 255f) * l2, mb2 = (tb / 255f) * l2;
          int twMax = texW - 1, thMax = texH - 1;
          for (int py = minY; py <= maxY; py++) {
            float sampleY = py + 0.5f;
            int row = py * rw;
            for (int px = minX; px <= maxX; px++) {
              float sampleX = px + 0.5f;
              float w0 = ((sx1 - sampleX) * (sy2 - sampleY) - (sy1 - sampleY) * (sx2 - sampleX)) * invArea;
              float w1 = ((sx2 - sampleX) * (sy0 - sampleY) - (sy2 - sampleY) * (sx0 - sampleX)) * invArea;
              float w2 = 1f - w0 - w1;
              if (w0 < -0.001f || w1 < -0.001f || w2 < -0.001f) continue;
              float iz = w0 * iz0 + w1 * iz1 + w2 * iz2;
              int idx = row + px;
              if (iz <= depth[idx]) continue;
              float u = w0 * tu0 + w1 * tu1 + w2 * tu2;
              float v = w0 * tv0 + w1 * tv1 + w2 * tv2;
              int tx = (int) (u * twMax + 0.5f);
              int ty = (int) (v * thMax + 0.5f);
              if (tx < 0) tx = 0; else if (tx > twMax) tx = twMax;
              if (ty < 0) ty = 0; else if (ty > thMax) ty = thMax;
              int texel = texPx[ty * texW + tx];
              int texA = texel >>> 24;
              int outA = (ba * texA + 127) / 255;
              if (outA == 0) continue;
              int texR = (texel >>> 16) & 255;
              int texG = (texel >>> 8) & 255;
              int texB = texel & 255;
              float spc = w0 * s0 + w1 * s1 + w2 * s2;
              int sr = Math.min(255, Math.max(0, (int) (texR * (w0 * mr0 + w1 * mr1 + w2 * mr2) + spc + emAddR + 0.5f)));
              int sg = Math.min(255, Math.max(0, (int) (texG * (w0 * mg0 + w1 * mg1 + w2 * mg2) + spc + emAddG + 0.5f)));
              int sb = Math.min(255, Math.max(0, (int) (texB * (w0 * mb0 + w1 * mb1 + w2 * mb2) + spc + emAddB + 0.5f)));
              int finalColor = (outA << 24) | (sr << 16) | (sg << 8) | sb;
              pixels[idx] = outA >= 250 ? finalColor : blend(finalColor, outA, pixels[idx]);
              if (outA >= 200) depth[idx] = iz;
            }
          }
        } else {
          float vr0 = Math.min(255f, tr * l0 + s0 + emAddR);
          float vg0 = Math.min(255f, tg * l0 + s0 + emAddG);
          float vb0 = Math.min(255f, tb * l0 + s0 + emAddB);

          float vr1 = Math.min(255f, tr * l1 + s1 + emAddR);
          float vg1 = Math.min(255f, tg * l1 + s1 + emAddG);
          float vb1 = Math.min(255f, tb * l1 + s1 + emAddB);

          float vr2 = Math.min(255f, tr * l2 + s2 + emAddR);
          float vg2 = Math.min(255f, tg * l2 + s2 + emAddG);
          float vb2 = Math.min(255f, tb * l2 + s2 + emAddB);

          for (int py = minY; py <= maxY; py++) {
            float sampleY = py + 0.5f;
            int row = py * rw;
            for (int px = minX; px <= maxX; px++) {
              float sampleX = px + 0.5f;
              float w0 = ((sx1 - sampleX) * (sy2 - sampleY) - (sy1 - sampleY) * (sx2 - sampleX)) * invArea;
              float w1 = ((sx2 - sampleX) * (sy0 - sampleY) - (sy2 - sampleY) * (sx0 - sampleX)) * invArea;
              float w2 = 1f - w0 - w1;
              if (w0 < -0.001f || w1 < -0.001f || w2 < -0.001f) continue;
              float iz = w0 * iz0 + w1 * iz1 + w2 * iz2;
              int idx = row + px;
              if (iz <= depth[idx]) continue;
              int sr = Math.min(255, Math.max(0, (int) (w0 * vr0 + w1 * vr1 + w2 * vr2 + 0.5f)));
              int sg = Math.min(255, Math.max(0, (int) (w0 * vg0 + w1 * vg1 + w2 * vg2 + 0.5f)));
              int sb = Math.min(255, Math.max(0, (int) (w0 * vb0 + w1 * vb1 + w2 * vb2 + 0.5f)));
              int finalColor = (ba << 24) | (sr << 16) | (sg << 8) | sb;
              pixels[idx] = ba >= 250 ? finalColor : blend(finalColor, ba, pixels[idx]);
              if (ba >= 200) depth[idx] = iz;
            }
          }
        }
      }
    }
  }

  private static float fastPowInt(float x, int exp) {
    float r = 1f;
    float b = x;
    int e = exp;
    while (e > 0) {
      if ((e & 1) != 0) r *= b;
      b *= b;
      e >>>= 1;
    }
    return r;
  }

  private static float vertexLight(float vnx, float vny, float vnz, float metallic) {
    float ndotv = Math.max(0f, vnz);
    float key = Math.max(0f, vnx * -0.46f + vny * 0.64f + vnz * 0.61f);
    float fill = Math.max(0f, vnx * 0.68f + vny * 0.12f + vnz * 0.44f);
    float oneMinusN = 1f - ndotv;
    float rim = oneMinusN * oneMinusN * (float) Math.sqrt(oneMinusN)
        * (0.35f + 0.65f * Math.max(0f, vny));
    float envHorizon = Math.max(0f, 1f - Math.abs(vny * 0.85f - 0.12f));
    float envBase = Math.max(0f, vny * 0.55f + 0.45f);
    float envBand = envBase * (float) Math.sqrt(envBase) + 0.28f * envHorizon * envHorizon * envHorizon;
    float diffuseTerm = (0.24f + 0.72f * key + 0.22f * fill) * (1f - metallic * 0.42f);
    float envTerm = metallic * (0.28f + 0.52f * envBand);
    return diffuseTerm + envTerm + rim * (0.22f + 0.25f * metallic);
  }

  private static float vertexSpec(float vnx, float vny, float vnz, float metallic) {
    float ndotv = Math.max(0f, vnz);
    float oneMinusN = 1f - ndotv;
    float rim = oneMinusN * oneMinusN * (float) Math.sqrt(oneMinusN)
        * (0.35f + 0.65f * Math.max(0f, vny));
    float h1 = Math.max(0f, vnx * -0.26f + vny * 0.42f + vnz * 0.87f);
    float h2 = Math.max(0f, vnx * 0.42f + vny * 0.22f + vnz * 0.88f);
    float spec1 = fastPowInt(h1, metallic > 0.5f ? 36 : 12);
    float spec2 = fastPowInt(h2, metallic > 0.5f ? 18 : 8) * 0.38f;
    return (spec1 * 1.35f + spec2) * (0.25f + 0.95f * metallic) * 165f + rim * metallic * 38f;
  }

  private static boolean isBoneEnabled(int bone, int slotMask) {
    switch (bone) {
      case CosmeticModelMesh.BONE_HEAD:
        return (slotMask & PlayerSkinData.SLOT_HEAD) != 0;
      case CosmeticModelMesh.BONE_BODY:
      case CosmeticModelMesh.BONE_RIGHT_ARM:
      case CosmeticModelMesh.BONE_LEFT_ARM:
        return (slotMask & PlayerSkinData.SLOT_CHEST) != 0;
      case CosmeticModelMesh.BONE_RIGHT_LEG:
      case CosmeticModelMesh.BONE_LEFT_LEG:
        return (slotMask & PlayerSkinData.SLOT_LEGS) != 0;
      case CosmeticModelMesh.BONE_RIGHT_BOOT:
      case CosmeticModelMesh.BONE_LEFT_BOOT:
        return (slotMask & PlayerSkinData.SLOT_BOOTS) != 0;
      default:
        return true;
    }
  }

  private void ensureBuffers(int width, int height) {
    if (bufferW == width && bufferH == height) return;
    bufferW = width;
    bufferH = height;
    pixels = new int[width * height];
    depth = new float[width * height];
  }

  private void sortByDepth(int count) {
    for (int i = 0; i < count; i++) {
      order[i] = i;
      orderDepth[i] = frameFaces[i].viewZ;
    }
    for (int i = 1; i < count; i++) {
      int value = order[i];
      float key = orderDepth[i];
      int j = i - 1;
      while (j >= 0 && orderDepth[j] < key) {
        order[j + 1] = order[j];
        orderDepth[j + 1] = orderDepth[j];
        j--;
      }
      order[j + 1] = value;
      orderDepth[j + 1] = key;
    }
  }

  private float[] project(float x, float y, float z, float cy, float sy, float cp, float sp,
      float cx, float screenCy, float fov) {
    float rx = x * cy + z * sy;
    float rz = -x * sy + z * cy;
    float ry = y - MODEL_HEIGHT * 0.5f;
    float ry2 = ry * cp - rz * sp;
    float rz2 = ry * sp + rz * cp;
    float cameraZ = Math.max(1f, CAMERA_DISTANCE - rz2);
    float inv = 1f / cameraZ;
    projection[0] = cx + rx * fov * inv;
    projection[1] = screenCy - ry2 * fov * inv;
    projection[2] = cameraZ;
    projection[3] = inv;
    return projection;
  }

  private boolean transform(Face face, float cy, float sy, float cp, float sp, float cx,
      float screenCy, float fov, float pivotZ) {
    float nx = face.nx * cy + face.nz * sy;
    float nz0 = -face.nx * sy + face.nz * cy;
    float ny = face.ny * cp - nz0 * sp;
    float nz = face.ny * sp + nz0 * cp;
    face.viewNx = nx;
    face.viewNy = ny;
    face.viewNz = nz;
    if (face.cull && nz <= 0.002f) return false;

    float avg = 0f;
    for (int i = 0; i < 4; i++) {
      float px = face.x[i];
      float py = face.y[i] - MODEL_HEIGHT * 0.5f;
      float pz = face.z[i] - pivotZ;
      float rx = px * cy + pz * sy;
      float rz = -px * sy + pz * cy;
      float ry = py * cp - rz * sp;
      float rz2 = py * sp + rz * cp;
      float cameraZ = CAMERA_DISTANCE - rz2;
      if (cameraZ < 2f) return false;
      float inv = 1f / cameraZ;
      face.sx[i] = cx + rx * fov * inv;
      face.sy[i] = screenCy - ry * fov * inv;
      face.invZ[i] = inv;
      face.uoz[i] = face.u[i] * inv;
      face.voz[i] = face.v[i] * inv;
      avg += cameraZ;
    }
    face.viewZ = avg * 0.25f;
    face.light = lighting(face, nz);
    return true;
  }

  private void drawCardShadow(float x, float y, int width, int height) {
    float rx = Math.max(6f, width * 0.34f);
    float ry = Math.max(2f, height * 0.055f);
    int x0 = Math.max(0, (int) (x - rx)), x1 = Math.min(width, (int) (x + rx) + 1);
    int y0 = Math.max(0, (int) (y - ry)), y1 = Math.min(height, (int) (y + ry) + 1);
    for (int py = y0; py < y1; py++) {
      float dy = (py + 0.5f - y) / ry;
      for (int px = x0; px < x1; px++) {
        float dx = (px + 0.5f - x) / rx;
        float d = dx * dx + dy * dy;
        if (d >= 1f) continue;
        float alpha = 0.28f * (1f - d) * (1f - d);
        int a = (int) (alpha * 255f);
        if (a > 0) {
          int index = py * width + px;
          pixels[index] = blend(0xff04060a, a, pixels[index]);
        }
      }
    }
  }

  private static float lighting(Face face, float nz) {
    float nx = face.viewNx, ny = face.viewNy, nzz = face.viewNz;
    float key = Math.max(0f, nx * -0.42f + ny * 0.68f + nzz * 0.60f);
    float fill = Math.max(0f, nx * 0.66f + ny * 0.14f + nzz * 0.46f);
    float rim = Math.max(0f, nx * -0.28f + ny * 0.36f + nzz * -0.89f);
    float edge = 1f - Math.min(1f, Math.abs(nz));
    float ao = 0.84f + 0.16f * (face.ny * 0.5f + 0.5f);
    float light = 0.47f + 0.62f * key + 0.16f * fill + 0.17f * rim + 0.08f * edge;
    if (face.cape) {
      light += 0.13f + 0.18f * face.viewNx + 0.05f * face.viewNy;
      if (nz < 0f) light *= 0.90f;
    }
    light *= ao;
    return light;
  }

  private void drawShadow(float x, float y, int width, int height, float fov) {
    float rx = Math.max(9f, width * 0.23f);
    float ry = Math.max(4f, height * 0.055f);
    int x0 = Math.max(0, (int) (x - rx)), x1 = Math.min(width, (int) (x + rx) + 1);
    int y0 = Math.max(0, (int) (y - ry)), y1 = Math.min(height, (int) (y + ry) + 1);
    for (int py = y0; py < y1; py++) {
      float dy = (py + 0.5f - y) / ry;
      for (int px = x0; px < x1; px++) {
        float dx = (px + 0.5f - x) / rx;
        float d = dx * dx + dy * dy;
        if (d >= 1f) continue;
        float alpha = 0.30f * (1f - d) * (1f - d);
        int a = (int) (alpha * 255f);
        if (a <= 0) continue;
        int index = py * width + px;
        pixels[index] = blend(0xff05080c, a, pixels[index]);
      }
    }
  }

  private void rasterize(Face face, int width, int height) {
    triangle(face, 0, 1, 2, width, height);
    triangle(face, 0, 2, 3, width, height);
  }

  private void triangle(Face face, int i0, int i1, int i2, int width, int height) {
    float x0 = face.sx[i0], y0 = face.sy[i0];
    float x1 = face.sx[i1], y1 = face.sy[i1];
    float x2 = face.sx[i2], y2 = face.sy[i2];
    float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    if (area > -0.0001f && area < 0.0001f) return;
    float invArea = 1f / area;
    int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
    int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
    int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
    int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
    if (minX > maxX || minY > maxY) return;

    float iz0 = face.invZ[i0], iz1 = face.invZ[i1], iz2 = face.invZ[i2];
    float u0 = face.uoz[i0], u1 = face.uoz[i1], u2 = face.uoz[i2];
    float v0 = face.voz[i0], v1 = face.voz[i1], v2 = face.voz[i2];
    float light = face.light;
    boolean cape = face.cape;
    int[] texture = cape ? capePixels : bodyPixels;
    int texW = cape ? capeW : bodyW;
    int texH = cape ? capeH : bodyH;
    int texMaskX = texW - 1, texMaskY = texH - 1;

    for (int py = minY; py <= maxY; py++) {
      float sampleY = py + 0.5f;
      int row = py * width;
      for (int px = minX; px <= maxX; px++) {
        float sampleX = px + 0.5f;
        float w0 = ((x1 - sampleX) * (y2 - sampleY) - (y1 - sampleY) * (x2 - sampleX)) * invArea;
        float w1 = ((x2 - sampleX) * (y0 - sampleY) - (y2 - sampleY) * (x0 - sampleX)) * invArea;
        float w2 = 1f - w0 - w1;
        if (w0 < -0.0001f || w1 < -0.0001f || w2 < -0.0001f) continue;
        float iz = w0 * iz0 + w1 * iz1 + w2 * iz2;
        if (iz <= 0f) continue;
        int index = row + px;
        if (iz <= depth[index]) continue;
        float u = (w0 * u0 + w1 * u1 + w2 * u2) / iz;
        float v = (w0 * v0 + w1 * v1 + w2 * v2) / iz;
        int tx = (int) (u * texW);
        int ty = (int) (v * texH);
        if (tx < 0)
          tx = 0;
        else if (tx > texMaskX)
          tx = texMaskX;
        if (ty < 0)
          ty = 0;
        else if (ty > texMaskY)
          ty = texMaskY;
        int texel = texture[ty * texW + tx];
        int alpha = texel >>> 24;
        if (alpha == 0) continue;
        int rgb = shade(texel, light);
        pixels[index] = blend(rgb, alpha, pixels[index]);
        if (alpha >= 200) depth[index] = iz;
      }
    }
  }

  private void downsample(int width, int height, int[] out) {
    int at = 0;
    for (int y = 0; y < height; y++) {
      int row0 = (y * 2) * bufferW;
      int row1 = row0 + bufferW;
      for (int x = 0; x < width; x++) {
        int a = pixels[row0 + x * 2], b = pixels[row0 + x * 2 + 1];
        int c = pixels[row1 + x * 2], d = pixels[row1 + x * 2 + 1];
        int sa = (a >>> 24) + (b >>> 24) + (c >>> 24) + (d >>> 24);
        if (sa == 0) {
          out[at++] = 0;
        } else {
          int sr = ((a >> 16 & 255) * (a >>> 24) + (b >> 16 & 255) * (b >>> 24)
                       + (c >> 16 & 255) * (c >>> 24) + (d >> 16 & 255) * (d >>> 24))
              / sa;
          int sg = ((a >> 8 & 255) * (a >>> 24) + (b >> 8 & 255) * (b >>> 24)
                       + (c >> 8 & 255) * (c >>> 24) + (d >> 8 & 255) * (d >>> 24))
              / sa;
          int sb = ((a & 255) * (a >>> 24) + (b & 255) * (b >>> 24) + (c & 255) * (c >>> 24)
                       + (d & 255) * (d >>> 24))
              / sa;
          out[at++] = (sa / 4 & 255) << 24 | sr << 16 | sg << 8 | sb;
        }
      }
    }
  }

  private static int shade(int argb, float light) {
    int r = (int) ((argb >> 16 & 255) * light);
    int g = (int) ((argb >> 8 & 255) * light);
    int b = (int) ((argb & 255) * light);
    return (argb & 0xff000000) | (r > 255 ? 255 : r) << 16 | (g > 255 ? 255 : g) << 8
        | (b > 255 ? 255 : b);
  }

  private static int blend(int src, int alpha, int dst) {
    if (alpha >= 255) return src;
    int inv = 255 - alpha;
    int dstAlpha = dst >>> 24;
    int a = alpha + (dstAlpha * inv + 127) / 255;
    if (a == 0) return 0;
    int r = ((src >> 16 & 255) * alpha * 255 + (dst >> 16 & 255) * dstAlpha * inv) / (a * 255);
    int g = ((src >> 8 & 255) * alpha * 255 + (dst >> 8 & 255) * dstAlpha * inv) / (a * 255);
    int b = ((src & 255) * alpha * 255 + (dst & 255) * dstAlpha * inv) / (a * 255);
    return a << 24 | r << 16 | g << 8 | b;
  }

  private static float clamp(float v, float lo, float hi) {
    return v < lo ? lo : v > hi ? hi : v;
  }

  private void buildBody(boolean slim) {
    bodyFaces.clear();
    int arm = slim ? 3 : 4;
    addCube(-4, 24, -4, 4, 32, 4, 0, 0, 8, 8, 8);
    addCube(-4, 12, -2, 4, 24, 2, 16, 16, 8, 12, 4);
    addCube(-4 - arm, 12, -2, -4, 24, 2, 40, 16, arm, 12, 4);
    addCube(4, 12, -2, 4 + arm, 24, 2, 32, 48, arm, 12, 4);
    addCube(-4f, 0, -2, 0f, 12, 2, 0, 16, 4, 12, 4);
    addCube(0f, 0, -2, 4f, 12, 2, 16, 48, 4, 12, 4);

    addCube(-4.25f, 23.75f, -4.25f, 4.25f, 32.25f, 4.25f, 32, 0, 8, 8, 8);
    addCube(-4.25f, 11.75f, -2.25f, 4.25f, 24.25f, 2.25f, 16, 32, 8, 12, 4);
    addCube(-4.25f - arm, 11.75f, -2.25f, -3.75f, 24.25f, 2.25f, 40, 32, arm, 12, 4);
    addCube(3.75f, 11.75f, -2.25f, 4.25f + arm, 24.25f, 2.25f, 48, 48, arm, 12, 4);
    addCube(-4.25f, -0.25f, -2.25f, 0f, 12.25f, 2.25f, 0, 32, 4, 12, 4);
    addCube(0f, -0.25f, -2.25f, 4.25f, 12.25f, 2.25f, 0, 48, 4, 12, 4);
    meshBuilt = true;
  }

  private void buildArmoredUnderBody(boolean slim) {
    bodyFaces.clear();
    int arm = slim ? 3 : 4;
    addCube(-3.35f, 24.1f, -3.35f, 3.35f, 31.4f, 3.35f, 0, 0, 8, 8, 8);
    addCube(-3.15f, 12.2f, -1.55f, 3.15f, 23.8f, 1.55f, 16, 16, 8, 12, 4);
    addCube(-3.65f - arm, 12.3f, -1.50f, -4.35f, 23.6f, 1.50f, 40, 16, arm, 12, 4);
    addCube(4.35f, 12.3f, -1.50f, 3.65f + arm, 23.6f, 1.50f, 32, 48, arm, 12, 4);
    addCube(-3.45f, 0.3f, -1.50f, -0.45f, 11.8f, 1.50f, 0, 16, 4, 12, 4);
    addCube(0.45f, 0.3f, -1.50f, 3.45f, 11.8f, 1.50f, 16, 48, 4, 12, 4);
  }

  private void addCube(float x0, float y0, float z0, float x1, float y1, float z1, int u, int v,
      int w, int h, int d) {
    bodyFaces.add(
        face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u + d, v + d, w, h, 0, 0, 1, true));
    bodyFaces.add(face(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u + d * 2 + w, v + d, w, h,
        0, 0, -1, true));
    bodyFaces.add(
        face(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u, v + d, d, h, 1, 0, 0, true));
    bodyFaces.add(face(
        x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u + d + w, v + d, d, h, -1, 0, 0, true));
    bodyFaces.add(
        face(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u + d, v, w, d, 0, 1, 0, true));
    bodyFaces.add(
        face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u + d + w, v, w, d, 0, -1, 0, true));
  }

  private Face face(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
      float z2, float x3, float y3, float z3, int u, int v, int w, int h, float nx, float ny,
      float nz, boolean cull) {
    Face face = new Face(false);
    face.x[0] = x0;
    face.y[0] = y0;
    face.z[0] = z0;
    face.x[1] = x1;
    face.y[1] = y1;
    face.z[1] = z1;
    face.x[2] = x2;
    face.y[2] = y2;
    face.z[2] = z2;
    face.x[3] = x3;
    face.y[3] = y3;
    face.z[3] = z3;
    float u0 = u / 64f, u1 = (u + w) / 64f;
    float v0 = v / 64f, v1 = (v + h) / 64f;
    face.u[0] = u0;
    face.v[0] = v1;
    face.u[1] = u1;
    face.v[1] = v1;
    face.u[2] = u1;
    face.v[2] = v0;
    face.u[3] = u0;
    face.v[3] = v0;
    face.nx = nx;
    face.ny = ny;
    face.nz = nz;
    face.cull = cull;
    return face;
  }

  private void buildCapeFaces() {
    int index = 0;
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
        Face face = new Face(true);
        float u0 = (1f + (1f - col / (float) (CAPE_COLUMNS - 1)) * 10f) / 64f;
        float u1 = (1f + (1f - (col + 1) / (float) (CAPE_COLUMNS - 1)) * 10f) / 64f;
        float v0 = (1f + row / (float) (CAPE_ROWS - 1) * 16f) / 32f;
        float v1 = (1f + (row + 1) / (float) (CAPE_ROWS - 1) * 16f) / 32f;
        setFaceUv(face, u0, v0, u1, v0, u1, v1, u0, v1, true);
        capeFaces[index++] = face;
      }
    }
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
        Face face = new Face(true);
        float u0 = (12f + col / (float) (CAPE_COLUMNS - 1) * 10f) / 64f;
        float u1 = (12f + (col + 1) / (float) (CAPE_COLUMNS - 1) * 10f) / 64f;
        float v0 = (1f + row / (float) (CAPE_ROWS - 1) * 16f) / 32f;
        float v1 = (1f + (row + 1) / (float) (CAPE_ROWS - 1) * 16f) / 32f;
        setFaceUv(face, u0, v0, u0, v1, u1, v1, u1, v0, true);
        capeFaces[index++] = face;
      }
    }
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      Face face = new Face(true);
      float v0 = (1.05f + row / (float) (CAPE_ROWS - 1) * 15.9f) / 32f;
      float v1 = (1.05f + (row + 1) / (float) (CAPE_ROWS - 1) * 15.9f) / 32f;
      float uOut = 11.15f / 64f, uIn = 11.85f / 64f;
      setFaceUv(face, uOut, v0, uOut, v1, uIn, v1, uIn, v0, true);
      capeFaces[index++] = face;
    }
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      Face face = new Face(true);
      float v0 = (1.05f + row / (float) (CAPE_ROWS - 1) * 15.9f) / 32f;
      float v1 = (1.05f + (row + 1) / (float) (CAPE_ROWS - 1) * 15.9f) / 32f;
      float uIn = 0.15f / 64f, uOut = 0.85f / 64f;
      setFaceUv(face, uIn, v0, uIn, v1, uOut, v1, uOut, v0, true);
      capeFaces[index++] = face;
    }
    for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
      Face face = new Face(true);
      float u0 = (1.05f + (1f - col / (float) (CAPE_COLUMNS - 1)) * 9.9f) / 64f;
      float u1 = (1.05f + (1f - (col + 1) / (float) (CAPE_COLUMNS - 1)) * 9.9f) / 64f;
      float vIn = 0.15f / 32f, vOut = 0.85f / 32f;
      setFaceUv(face, u0, vOut, u0, vIn, u1, vIn, u1, vOut, true);
      capeFaces[index++] = face;
    }
    for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
      Face face = new Face(true);
      float u0 = (11.05f + (1f - col / (float) (CAPE_COLUMNS - 1)) * 9.9f) / 64f;
      float u1 = (11.05f + (1f - (col + 1) / (float) (CAPE_COLUMNS - 1)) * 9.9f) / 64f;
      float vIn = 0.15f / 32f, vOut = 0.85f / 32f;
      setFaceUv(face, u0, vIn, u0, vOut, u1, vOut, u1, vIn, true);
      capeFaces[index++] = face;
    }
  }

  private static void setFaceUv(Face face, float u0, float v0, float u1, float v1, float u2,
      float v2, float u3, float v3, boolean cull) {
    face.u[0] = u0;
    face.v[0] = v0;
    face.u[1] = u1;
    face.v[1] = v1;
    face.u[2] = u2;
    face.v[2] = v2;
    face.u[3] = u3;
    face.v[3] = v3;
    face.cull = cull;
  }

  private void updateCapeFaces() {
    for (int row = 0; row < CAPE_ROWS; row++) {
      int topRow = Math.max(0, row - 1);
      int botRow = Math.min(CAPE_ROWS - 1, row + 1);
      for (int col = 0; col < CAPE_COLUMNS; col++) {
        int leftCol = Math.max(0, col - 1);
        int rightCol = Math.min(CAPE_COLUMNS - 1, col + 1);
        int i = cape.index(col, row);
        int il = cape.index(leftCol, row), ir = cape.index(rightCol, row);
        int it = cape.index(col, topRow), ib = cape.index(col, botRow);
        float ax = cape.x[ir] - cape.x[il], ay = cape.y[ir] - cape.y[il],
              az = cape.z[ir] - cape.z[il];
        float dx = cape.x[ib] - cape.x[it], dy = cape.y[ib] - cape.y[it],
              dz = cape.z[ib] - cape.z[it];
        float nx = ay * dz - az * dy;
        float ny = az * dx - ax * dz;
        float nz = ax * dy - ay * dx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) {
          nx = 0f;
          ny = 0f;
          nz = -1f;
          len = 1f;
        }
        float scale = CAPE_THICKNESS / len;
        capeOutX[i] = cape.x[i] + nx * scale;
        capeOutY[i] = cape.y[i] + ny * scale;
        capeOutZ[i] = cape.z[i] + nz * scale;
      }
    }

    int index = 0;
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
        int a = cape.index(col, row), b = cape.index(col + 1, row);
        int c = cape.index(col + 1, row + 1), d = cape.index(col, row + 1);
        assignQuad(capeFaces[index++], capeOutX, capeOutY, capeOutZ, a, b, c, d, 0f, 0f, -1f);
      }
    }
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
        int a = cape.index(col, row), b = cape.index(col + 1, row);
        int c = cape.index(col + 1, row + 1), d = cape.index(col, row + 1);
        assignQuad(capeFaces[index++], cape.x, cape.y, cape.z, a, d, c, b, 0f, 0f, 1f);
      }
    }
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      int top = cape.index(0, row), bot = cape.index(0, row + 1);
      assignRimQuad(capeFaces[index++], capeOutX[top], capeOutY[top], capeOutZ[top], capeOutX[bot],
          capeOutY[bot], capeOutZ[bot], cape.x[bot], cape.y[bot], cape.z[bot], cape.x[top],
          cape.y[top], cape.z[top], -1f, 0f, 0f);
    }
    int lastCol = CAPE_COLUMNS - 1;
    for (int row = 0; row < CAPE_ROWS - 1; row++) {
      int top = cape.index(lastCol, row), bot = cape.index(lastCol, row + 1);
      assignRimQuad(capeFaces[index++], cape.x[top], cape.y[top], cape.z[top], cape.x[bot],
          cape.y[bot], cape.z[bot], capeOutX[bot], capeOutY[bot], capeOutZ[bot], capeOutX[top],
          capeOutY[top], capeOutZ[top], 1f, 0f, 0f);
    }
    for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
      int left = cape.index(col, 0), right = cape.index(col + 1, 0);
      assignRimQuad(capeFaces[index++], capeOutX[left], capeOutY[left], capeOutZ[left],
          cape.x[left], cape.y[left], cape.z[left], cape.x[right], cape.y[right], cape.z[right],
          capeOutX[right], capeOutY[right], capeOutZ[right], 0f, 1f, 0f);
    }
    int lastRow = CAPE_ROWS - 1;
    for (int col = 0; col < CAPE_COLUMNS - 1; col++) {
      int left = cape.index(col, lastRow), right = cape.index(col + 1, lastRow);
      assignRimQuad(capeFaces[index++], cape.x[left], cape.y[left], cape.z[left], capeOutX[left],
          capeOutY[left], capeOutZ[left], capeOutX[right], capeOutY[right], capeOutZ[right],
          cape.x[right], cape.y[right], cape.z[right], 0f, -1f, 0f);
    }
  }

  private static void assignQuad(Face face, float[] vx, float[] vy, float[] vz, int i0, int i1,
      int i2, int i3, float fnx, float fny, float fnz) {
    assignRimQuad(face, vx[i0], vy[i0], vz[i0], vx[i1], vy[i1], vz[i1], vx[i2], vy[i2], vz[i2],
        vx[i3], vy[i3], vz[i3], fnx, fny, fnz);
  }

  private static void assignRimQuad(Face face, float x0, float y0, float z0, float x1, float y1,
      float z1, float x2, float y2, float z2, float x3, float y3, float z3, float fnx, float fny,
      float fnz) {
    face.x[0] = x0;
    face.y[0] = y0;
    face.z[0] = z0;
    face.x[1] = x1;
    face.y[1] = y1;
    face.z[1] = z1;
    face.x[2] = x2;
    face.y[2] = y2;
    face.z[2] = z2;
    face.x[3] = x3;
    face.y[3] = y3;
    face.z[3] = z3;
    float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
    float bx = x3 - x0, by = y3 - y0, bz = z3 - z0;
    float nx = ay * bz - az * by;
    float ny = az * bx - ax * bz;
    float nz = ax * by - ay * bx;
    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
    if (len < 1e-5f) {
      face.nx = fnx;
      face.ny = fny;
      face.nz = fnz;
    } else {
      face.nx = nx / len;
      face.ny = ny / len;
      face.nz = nz / len;
    }
  }

  private static final class Face {
    final boolean cape;
    final float[] x = new float[4], y = new float[4], z = new float[4];
    final float[] u = new float[4], v = new float[4];
    final float[] sx = new float[4], sy = new float[4];
    final float[] invZ = new float[4], uoz = new float[4], voz = new float[4];
    float nx, ny, nz;
    float viewNx, viewNy, viewNz;
    float viewZ;
    float light;
    boolean cull;

    Face(boolean cape) {
      this.cape = cape;
    }
  }

  private static final class Cape {
    final float[] x = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] y = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] z = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] px = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] py = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] pz = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] restX = new float[CAPE_COLUMNS * CAPE_ROWS];
    final float[] restY = new float[CAPE_COLUMNS * CAPE_ROWS];

    Cape() {
      reset();
    }

    int index(int col, int row) {
      return row * CAPE_COLUMNS + col;
    }

    void reset() {
      for (int row = 0; row < CAPE_ROWS; row++) {
        for (int col = 0; col < CAPE_COLUMNS; col++) {
          int i = index(col, row);
          float across = col / (float) (CAPE_COLUMNS - 1);
          float down = row / (float) (CAPE_ROWS - 1);
          float fold = (float) Math.sin(across * Math.PI * 2.7f + 0.55f);
          x[i] = -CAPE_WIDTH * 0.5f + CAPE_WIDTH * across + 0.16f * fold * down;
          y[i] = CAPE_TOP - CAPE_LENGTH * down
              + 0.30f * (float) Math.sin(across * Math.PI * 3.1f) * down;
          z[i] = -2.32f - down * down * 0.55f - 0.52f * fold * down
              - 0.16f * Math.abs(across - 0.5f) * down;
          px[i] = x[i];
          py[i] = y[i];
          pz[i] = z[i];
          restX[i] = CAPE_WIDTH / (CAPE_COLUMNS - 1);
          restY[i] = CAPE_LENGTH / (CAPE_ROWS - 1);
        }
      }
    }

    void step(float dt, float time, float wind, float yawVelocity) {
      if (dt <= 0f) return;
      int steps = dt < 0.026f ? 1 : 2;
      float step = dt / steps;
      for (int s = 0; s < steps; s++) {
        integrate(step, time + s * step, wind, yawVelocity);
        for (int iteration = 0; iteration < 3; iteration++) constraints();
      }
    }

    private void integrate(float dt, float time, float wind, float yawVelocity) {
      float dt2 = dt * dt;
      for (int row = 1; row < CAPE_ROWS; row++) {
        for (int col = 0; col < CAPE_COLUMNS; col++) {
          int i = index(col, row);
          float vx = (x[i] - px[i]) * 0.982f;
          float vy = (y[i] - py[i]) * 0.982f;
          float vz = (z[i] - pz[i]) * 0.982f;
          px[i] = x[i];
          py[i] = y[i];
          pz[i] = z[i];
          float gust =
              wind * (5.2f + 3.2f * (float) Math.sin(time * 2.3f + row * 0.48f + col * 0.63f));
          x[i] += vx - yawVelocity * 0.00055f * dt2 * (row + 1);
          y[i] += vy - 29f * dt2;
          z[i] += vz - gust * dt2;
        }
      }
    }

    private void constraints() {
      for (int col = 0; col < CAPE_COLUMNS; col++) {
        int i = index(col, 0);
        float across = col / (float) (CAPE_COLUMNS - 1);
        x[i] = -CAPE_WIDTH * 0.5f + CAPE_WIDTH * across;
        y[i] = CAPE_TOP;
        z[i] = -2.32f;
        px[i] = x[i];
        py[i] = y[i];
        pz[i] = z[i];
      }
      for (int pass = 0; pass < 2; pass++) {
        for (int row = 0; row < CAPE_ROWS; row++) {
          for (int col = 0; col < CAPE_COLUMNS; col++) {
            int i = index(col, row);
            if (col + 1 < CAPE_COLUMNS && row > 0) link(i, index(col + 1, row), restX[i], false);
            if (row + 1 < CAPE_ROWS) link(i, index(col, row + 1), restY[i], row == 0);
            if (col + 1 < CAPE_COLUMNS && row + 1 < CAPE_ROWS) {
              float rest = (float) Math.sqrt(restX[i] * restX[i] + restY[i] * restY[i]);
              link(i, index(col + 1, row + 1), rest, row == 0);
            }
            if (col > 0 && row + 1 < CAPE_ROWS) {
              float rest = (float) Math.sqrt(restX[i] * restX[i] + restY[i] * restY[i]);
              link(i, index(col - 1, row + 1), rest, row == 0);
            }
          }
        }
      }
      for (int i = CAPE_COLUMNS; i < x.length; i++) {
        if (x[i] < -7.5f)
          x[i] = -7.5f;
        else if (x[i] > 7.5f)
          x[i] = 7.5f;
        if (y[i] < 1.0f)
          y[i] = 1.0f;
        else if (y[i] > CAPE_TOP)
          y[i] = CAPE_TOP;
        if (z[i] > -2.28f)
          z[i] = -2.28f;
        else if (z[i] < -14f)
          z[i] = -14f;
      }
    }

    private void link(int a, int b, float rest, boolean aPinned) {
      float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
      float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length < 1e-5f) return;
      float correction = (length - rest) / length;
      if (!aPinned) {
        float share = 0.5f * correction;
        x[a] += dx * share;
        y[a] += dy * share;
        z[a] += dz * share;
      }
      float shareB = aPinned ? correction : 0.5f * correction;
      x[b] -= dx * shareB;
      y[b] -= dy * shareB;
      z[b] -= dz * shareB;
    }
  }
}
