package dev.nightclient.platform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightclient.renderer.model.CosmeticModelMesh;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
@@CM_NATIVE_IMPORTS@@

public final class CustomModelBackend {
  private static final int MAX_CACHED_MESHES = 16;
  private static final int MAX_UBO_SLOTS = 128;

  private static final class GpuMesh {
    int meshId;
    GpuBuffer vbo;
    GpuBuffer ibo;
    GpuTexture tex;
    GpuTextureView texView;
    long lastUsed;
    long lastPassToken;

    void dispose() {
      if (texView != null) {
        texView.close();
        texView = null;
      }
      if (tex != null) {
        tex.close();
        tex = null;
      }
      if (ibo != null) {
        ibo.close();
        ibo = null;
      }
      if (vbo != null) {
        vbo.close();
        vbo = null;
      }
      meshId = 0;
    }
  }

  private static final GpuMesh[] CACHE = new GpuMesh[MAX_CACHED_MESHES];
  private static long useClock;
  private static long passClock;

  private static volatile boolean invalidated;
  @@CM_PIPE_DECLS@@

  private static GpuBuffer drawUbo;
  private static final ByteBuffer UBO_STAGING =
      ByteBuffer.allocateDirect(MAX_UBO_SLOTS * CustomModelBridge.UBO_STRIDE)
          .order(ByteOrder.nativeOrder());
  private static final int[] SLOT_NORMAL = new int[CustomModelBridge.MAX_WORLD_DRAWS];
  private static final int[] SLOT_ADDITIVE = new int[CustomModelBridge.MAX_WORLD_DRAWS];
  private static final GpuMesh[] RESOLVED_MESHES = new GpuMesh[CustomModelBridge.MAX_WORLD_DRAWS];

  static {
    for (int i = 0; i < MAX_CACHED_MESHES; i++) CACHE[i] = new GpuMesh();
  }

  private CustomModelBackend() {}

  public static void resourceReload() {
    invalidated = true;
  }

  public static void flushWorld() {
    int count = CustomModelBridge.worldDrawCount();
    if (count <= 0) {
      CustomModelBridge.endWorldPass();
      return;
    }
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.level == null) return;
      RenderTarget target = @@CM_MAIN_TARGET@@;
      if (target == null || target.width <= 1 || target.height <= 1) return;
      GpuTextureView colorView = target.getColorTextureView();
      GpuTextureView depthView = target.getDepthTextureView();
      if (colorView == null || depthView == null) return;
      renderQueue(CustomModelBridge.WORLD_DRAWS, count, colorView, depthView, "NightClient 3D World");
    } finally {
      CustomModelBridge.endWorldPass();
    }
  }

  public static void flushHands() {
    flushHandsWithDepth(null);
  }

  public static void flushHandsWithDepth(GpuTextureView explicitDepth) {
    int count = CustomModelBridge.handDrawCount();
    if (count <= 0) {
      CustomModelBridge.endHandPass();
      return;
    }
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.level == null) return;
      RenderTarget target = @@CM_MAIN_TARGET@@;
      if (target == null || target.width <= 1 || target.height <= 1) return;
      GpuTextureView colorView = @@CM_HAND_COLOR_VIEW@@;
      GpuTextureView depthView = explicitDepth != null ? explicitDepth : @@CM_HAND_DEPTH_VIEW@@;
      if (colorView == null || depthView == null) return;
      renderQueue(CustomModelBridge.HAND_DRAWS, count, colorView, depthView, "NightClient 3D Hand");
    } finally {
      CustomModelBridge.endHandPass();
    }
  }

  private static void renderQueue(
      CustomModelBridge.DrawCmd[] cmds,
      int count,
      GpuTextureView colorView,
      GpuTextureView depthView,
      final String label) {
    GpuBufferSlice projSlice = RenderSystem.getProjectionMatrixBuffer();
    if (projSlice == null) return;

    if (invalidated) {
      disposeGpu();
      invalidated = false;
    }
    ensurePipelines();

    CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
    if (drawUbo == null) {
      drawUbo =
          RenderSystem.getDevice()
              .createBuffer(
                  () -> "NightClient 3D Draw UBO",
                  GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                  MAX_UBO_SLOTS * CustomModelBridge.UBO_STRIDE);
    }

    UBO_STAGING.clear();
    long currentPass = ++passClock;
    int usedSlots = 0;
    for (int i = 0; i < count; i++) {
      CustomModelBridge.DrawCmd cmd = cmds[i];
      CosmeticModelMesh mesh = cmd.mesh;
      SLOT_NORMAL[i] = -1;
      SLOT_ADDITIVE[i] = -1;
      RESOLVED_MESHES[i] = null;
      if (mesh == null) continue;
      boolean needsNormal =
          hasSubMeshForPass(mesh, cmd.boneId, CosmeticModelMesh.BLEND_OPAQUE)
              || hasSubMeshForPass(mesh, cmd.boneId, CosmeticModelMesh.BLEND_TRANSLUCENT);
      boolean needsAdditive =
          hasSubMeshForPass(mesh, cmd.boneId, CosmeticModelMesh.BLEND_ADDITIVE);
      if (!needsNormal && !needsAdditive) continue;

      GpuMesh gpu = ensureMesh(encoder, mesh, currentPass);
      if (gpu == null || gpu.vbo == null || gpu.ibo == null || gpu.texView == null) continue;
      RESOLVED_MESHES[i] = gpu;

      if (needsNormal && usedSlots < MAX_UBO_SLOTS) {
        SLOT_NORMAL[i] = usedSlots;
        CustomModelBridge.packDrawUbo(
            cmd, null, UBO_STAGING, usedSlots * CustomModelBridge.UBO_STRIDE, false);
        usedSlots++;
      }
      if (needsAdditive && usedSlots < MAX_UBO_SLOTS) {
        SLOT_ADDITIVE[i] = usedSlots;
        CustomModelBridge.packDrawUbo(
            cmd, null, UBO_STAGING, usedSlots * CustomModelBridge.UBO_STRIDE, true);
        usedSlots++;
      }
    }
    if (usedSlots == 0) return;

    int uploadBytes = usedSlots * CustomModelBridge.UBO_STRIDE;
    UBO_STAGING.position(0);
    UBO_STAGING.limit(uploadBytes);
    encoder.writeToBuffer(drawUbo.slice(0, uploadBytes), UBO_STAGING);

    int revDepth = @@CM_REVERSED_DEPTH@@;
    RenderPipeline pipeOpaqueCullActive = @@CM_PIPE_OPAQUE_CULL@@;
    RenderPipeline pipeOpaqueNoCullActive = @@CM_PIPE_OPAQUE_NOCULL@@;
    RenderPipeline pipeTranslucentActive = @@CM_PIPE_TRANSLUCENT@@;
    RenderPipeline pipeAdditiveActive = @@CM_PIPE_ADDITIVE@@;

    try (RenderPass pass = @@CM_CREATE_PASS@@) {
      for (int p = 0; p < 3; p++) {
        boolean isAdditive = p == CosmeticModelMesh.BLEND_ADDITIVE;
        for (int i = 0; i < count; i++) {
          GpuMesh gpu = RESOLVED_MESHES[i];
          if (gpu == null) continue;
          int uboSlot = isAdditive ? SLOT_ADDITIVE[i] : SLOT_NORMAL[i];
          if (uboSlot < 0) continue;

          CustomModelBridge.DrawCmd cmd = cmds[i];
          CosmeticModelMesh mesh = cmd.mesh;
          CosmeticModelMesh.SubMesh[] subs = mesh.subMeshes;
          for (int s = 0; s < subs.length; s++) {
            CosmeticModelMesh.SubMesh sub = subs[s];
            if (sub.boneId != cmd.boneId || sub.blendMode != p || sub.indexCount <= 0) continue;

            RenderPipeline activePipe;
            if (p == CosmeticModelMesh.BLEND_OPAQUE) {
              activePipe = sub.doubleSided ? pipeOpaqueNoCullActive : pipeOpaqueCullActive;
            } else if (p == CosmeticModelMesh.BLEND_TRANSLUCENT) {
              activePipe = pipeTranslucentActive;
            } else {
              activePipe = pipeAdditiveActive;
            }

            @@CM_SET_PIPE@@
            pass.setUniform("Projection", projSlice);
            pass.setUniform(
                "NightModelDraw",
                drawUbo.slice(
                    uboSlot * CustomModelBridge.UBO_STRIDE, CustomModelBridge.UBO_STRIDE));
            @@CM_BIND_TEXTURE@@
            @@CM_BIND_VERTEX@@
            @@CM_BIND_INDEX@@
            @@CM_DRAW_INDEXED@@
          }
        }
      }
    } finally {
      for (int i = 0; i < count; i++) RESOLVED_MESHES[i] = null;
    }
  }

  private static boolean hasSubMeshForPass(CosmeticModelMesh mesh, int boneId, int pass) {
    CosmeticModelMesh.SubMesh[] subs = mesh.subMeshes;
    for (int i = 0; i < subs.length; i++) {
      CosmeticModelMesh.SubMesh sub = subs[i];
      if (sub.boneId == boneId && sub.blendMode == pass && sub.indexCount > 0) return true;
    }
    return false;
  }

  private static GpuMesh ensureMesh(CommandEncoder encoder, CosmeticModelMesh mesh, long currentPass) {
    useClock++;
    int freeIdx = -1;
    int oldestIdx = -1;
    for (int i = 0; i < MAX_CACHED_MESHES; i++) {
      GpuMesh e = CACHE[i];
      if (e.meshId == mesh.meshId && e.vbo != null) {
        e.lastUsed = useClock;
        e.lastPassToken = currentPass;
        return e;
      }
      if (e.vbo == null && freeIdx < 0) freeIdx = i;
      if (e.lastPassToken != currentPass && (oldestIdx < 0 || e.lastUsed < CACHE[oldestIdx].lastUsed)) {
        oldestIdx = i;
      }
    }
    int slotIdx = freeIdx >= 0 ? freeIdx : oldestIdx;
    if (slotIdx < 0) return null;
    CACHE[slotIdx].dispose();
    GpuMesh slot = new GpuMesh();
    CACHE[slotIdx] = slot;

    GpuBuffer vbo =
        RenderSystem.getDevice()
            .createBuffer(
                () -> "NightClient 3D VBO #" + mesh.meshId,
                GpuBuffer.USAGE_VERTEX,
                mesh.copyVerticesToDirectBuffer());
    GpuBuffer ibo =
        RenderSystem.getDevice()
            .createBuffer(
                () -> "NightClient 3D IBO #" + mesh.meshId,
                GpuBuffer.USAGE_INDEX,
                mesh.copyIndicesToDirectBuffer());

    int rw = Math.max(1, mesh.textureWidth);
    int rh = Math.max(1, mesh.textureHeight);
    GpuTexture tex =
        RenderSystem.getDevice()
            .createTexture(
                "NightClient 3D Tex #" + mesh.meshId,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                @@CM_RGBA8_FORMAT@@,
                rw,
                rh,
                1,
                1);
    @@CM_TEX_SETUP@@
    GpuTextureView texView = RenderSystem.getDevice().createTextureView(tex);
    try (NativeImage img = new NativeImage(rw, rh, false)) {
      int[] px = mesh.texturePixels;
      int idx = 0;
      for (int y = 0; y < rh; y++) {
        for (int x = 0; x < rw; x++) {
          int argb = (px != null && idx < px.length) ? px[idx] : 0xFFFFFFFF;
          img.setPixel(x, y, argb);
          idx++;
        }
      }
      encoder.writeToTexture(tex, img);
    }

    slot.meshId = mesh.meshId;
    slot.vbo = vbo;
    slot.ibo = ibo;
    slot.tex = tex;
    slot.texView = texView;
    slot.lastUsed = useClock;
    slot.lastPassToken = currentPass;
    return slot;
  }

  private static void ensurePipelines() {
    if (@@CM_PIPE_CHECK@@) return;
    @@CM_BUILD_PIPELINES@@
  }

  private static void disposeGpu() {
    for (int i = 0; i < MAX_CACHED_MESHES; i++) CACHE[i].dispose();
    if (drawUbo != null) {
      drawUbo.close();
      drawUbo = null;
    }
    @@CM_CLEAR_PIPELINES@@
  }

  public static void close() {
    disposeGpu();
  }
}
