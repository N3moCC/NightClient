package dev.nightclient.platform;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nightclient.platform.mixin.OptGlBufferAccessor;
import dev.nightclient.platform.mixin.OptRenderSetupAccessor;
import dev.nightclient.platform.mixin.OptRenderTypeStateAccessor;
import dev.nightclient.platform.opt.NightNative;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;

public final class NativeGpuBridge {
  private NativeGpuBridge() {}

  private static final int MAX_ENTITY_INSTANCES = 32768;
  private static final int ENTITY_INSTANCE_BYTES = 112;
  private static final int MAX_TEXT_INSTANCES = 16384;
  private static final int TEXT_INSTANCE_BYTES = 96;
  private static final int FRAME_PARAMS_BYTES = 144;

  private static long entityBufAddr;
  private static long textBufAddr;
  private static long paramsBufAddr;
  private static long scratchMeshAddr;
  private static int scratchMeshCapBytes;

  private static int entityCount;
  private static int textCount;
  private static int flushCounter;

  private static boolean glInitAttempted;
  private static boolean irisChecked;
  private static Object irisApiInstance;
  private static Method irisIsShaderInUse;
  private static boolean irisShaderActiveThisFrame;
  private static long irisCheckedFrame = -1L;

  private static long meshGen = 1L;

  public static final class EntityRtMeta {
    final RenderType rt;
    final int flags;
    final boolean eligible;
    GlTextureView sampler0View;
    EntityRtMeta(RenderType rt, int flags, boolean eligible, GlTextureView sampler0View) {
      this.rt = rt;
      this.flags = flags;
      this.eligible = eligible;
      this.sampler0View = sampler0View;
    }

    int glTexId() {
      GlTextureView tv = this.sampler0View;
      if (tv == null || tv.isClosed()) {
        tv = resolveSampler0View(this.rt);
        this.sampler0View = tv;
      }
      return tv != null ? tv.texture().glId() : 0;
    }
  }

  private static final class TextRtMeta {
    final RenderType rt;
    final int flags;
    final boolean hasTex;
    final boolean eligible;
    GlTextureView sampler0View;
    TextRtMeta(RenderType rt, int flags, boolean hasTex, boolean eligible, GlTextureView sampler0View) {
      this.rt = rt;
      this.flags = flags;
      this.hasTex = hasTex;
      this.eligible = eligible;
      this.sampler0View = sampler0View;
    }

    int glTexId() {
      if (!this.hasTex) return 0;
      GlTextureView tv = this.sampler0View;
      if (tv == null || tv.isClosed()) {
        tv = resolveSampler0View(this.rt);
        this.sampler0View = tv;
      }
      return tv != null ? tv.texture().glId() : 0;
    }
  }

  private static final IdentityHashMap<RenderType, EntityRtMeta> ENTITY_RT_CACHE =
      new IdentityHashMap<RenderType, EntityRtMeta>(64);
  private static final IdentityHashMap<RenderType, TextRtMeta> TEXT_RT_CACHE =
      new IdentityHashMap<RenderType, TextRtMeta>(32);

  private static int curGlTexId;
  private static int curFlags;
  private static float curU0;
  private static float curV0;
  private static float curUScale = 1.0f;
  private static float curVScale = 1.0f;
  private static MultiBufferSource.BufferSource curFallbackSource;
  private static RenderType curFallbackRt;
  private static TextureAtlasSprite curFallbackSprite;
  private static VertexConsumer curFallbackVc;

  public static final VertexConsumer ENTITY_COLLECTOR = new FallbackEntityCollector();

  private static final class FallbackEntityCollector implements VertexConsumer {
    private VertexConsumer fallback() {
      VertexConsumer vc = curFallbackVc;
      if (vc == null && curFallbackSource != null && curFallbackRt != null) {
        vc = curFallbackSource.getBuffer(curFallbackRt);
        if (curFallbackSprite != null) {
          vc = curFallbackSprite.wrap(vc);
        }
        curFallbackVc = vc;
      }
      return vc;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.addVertex(x, y, z);
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setColor(r, g, b, a);
      return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setColor(argb);
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setUv(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setUv1(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setUv2(u, v);
      return this;
    }

    @Override
    public VertexConsumer setNormal(float nx, float ny, float nz) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setNormal(nx, ny, nz);
      return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
      VertexConsumer vc = fallback();
      if (vc != null) vc.setLineWidth(width);
      return this;
    }
  }

  public static long meshGeneration() {
    long ng = NightNative.meshGeneration();
    if (ng != meshGen) {
      ENTITY_RT_CACHE.clear();
      TEXT_RT_CACHE.clear();
      entityCount = 0;
      textCount = 0;
      meshGen = ng;
    }
    return meshGen;
  }

  public static void resetResourceCaches() {
    ENTITY_RT_CACHE.clear();
    TEXT_RT_CACHE.clear();
    entityCount = 0;
    textCount = 0;
    NightNative.resetGpuMeshes();
    meshGen = NightNative.meshGeneration();
  }

  public static boolean ensureGlInit() {
    if (NightNative.isGpuInstancerReady()) {
      if (entityBufAddr == 0L || textBufAddr == 0L || paramsBufAddr == 0L) {
        allocateStagingBuffers();
      }
      return entityBufAddr != 0L && textBufAddr != 0L && paramsBufAddr != 0L;
    }
    if (glInitAttempted || !NightNative.isLoaded()) return false;
    GpuDevice dev = RenderSystem.tryGetDevice();
    if (!(dev instanceof GlDevice)) return false;
    glInitAttempted = true;
    try {
      FunctionProvider fp = GL.getFunctionProvider();
      if (fp == null) return false;
      String[] names = NightNative.GL_FUNCTION_NAMES;
      long mem = MemoryUtil.nmemAlloc((long) names.length * 8L);
      if (mem == 0L) return false;
      try {
        for (int i = 0; i < names.length; i++) {
          MemoryUtil.memPutLong(mem + (long) i * 8L, fp.getFunctionAddress(names[i]));
        }
        NightNative.initGl(mem, names.length);
      } finally {
        MemoryUtil.nmemFree(mem);
      }
      if (NightNative.isGpuInstancerReady()) {
        allocateStagingBuffers();
      }
      return NightNative.isGpuInstancerReady();
    } catch (Throwable t) {
      System.err.println("[NightClient] NativeGpuBridge GL init failed: " + t);
      return false;
    }
  }

  private static void allocateStagingBuffers() {
    if (entityBufAddr == 0L) {
      entityBufAddr = MemoryUtil.nmemAlloc((long) MAX_ENTITY_INSTANCES * ENTITY_INSTANCE_BYTES);
    }
    if (textBufAddr == 0L) {
      textBufAddr = MemoryUtil.nmemAlloc((long) MAX_TEXT_INSTANCES * TEXT_INSTANCE_BYTES);
    }
    if (paramsBufAddr == 0L) {
      paramsBufAddr = MemoryUtil.nmemAlloc(FRAME_PARAMS_BYTES);
    }
  }

  private static boolean isIrisShaderActive() {
    long fi = OptimizationBridge.frameIndex;
    if (irisCheckedFrame == fi) return irisShaderActiveThisFrame;
    irisCheckedFrame = fi;
    if (!irisChecked) {
      irisChecked = true;
      if (FabricLoader.getInstance().isModLoaded("iris")) {
        try {
          Class<?> apiCls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
          irisApiInstance = apiCls.getMethod("getInstance").invoke(null);
          irisIsShaderInUse = apiCls.getMethod("isShaderPackInUse");
        } catch (Throwable ignored) {
          irisApiInstance = null;
          irisIsShaderInUse = null;
        }
      }
    }
    if (irisApiInstance != null && irisIsShaderInUse != null) {
      try {
        irisShaderActiveThisFrame = Boolean.TRUE.equals(irisIsShaderInUse.invoke(irisApiInstance));
        return irisShaderActiveThisFrame;
      } catch (Throwable ignored) {
      }
    }
    irisShaderActiveThisFrame = false;
    return false;
  }

  public static boolean isEntityInstancingActive() {
    if (!OptimizationBridge.opt165NativeGpuInstancing) return false;
    if (!ensureGlInit() || entityBufAddr == 0L) return false;
    return !isIrisShaderActive();
  }

  public static boolean isTextInstancingActive() {
    if (!OptimizationBridge.opt165NativeGpuInstancing) {
      return false;
    }
    if (!ensureGlInit() || textBufAddr == 0L) return false;
    return !isIrisShaderActive();
  }

  public static EntityRtMeta resolveEntityRt(RenderType rt) {
    if (rt == null) return null;
    EntityRtMeta cached = ENTITY_RT_CACHE.get(rt);
    if (cached != null) {
      return cached.eligible ? cached : null;
    }
    EntityRtMeta computed = inspectEntityRt(rt);
    ENTITY_RT_CACHE.put(rt, computed);
    return computed.eligible ? computed : null;
  }

  private static EntityRtMeta inspectEntityRt(RenderType rt) {
    try {
      if (rt.mode() != VertexFormat.Mode.QUADS
          || rt.format() != DefaultVertexFormat.NEW_ENTITY
          || rt.isOutline()) {
        return new EntityRtMeta(rt, 0, false, null);
      }
      RenderPipeline pipe = rt.pipeline();
      if (pipe == null
          || !pipe.isWriteColor()
          || !pipe.isWriteDepth()) {
        return new EntityRtMeta(rt, 0, false, null);
      }
      boolean isBlended = false;
      if (pipe.getBlendFunction().isPresent()) {
        BlendFunction bf = pipe.getBlendFunction().get();
        if (!BlendFunction.TRANSLUCENT.equals(bf)) {
          return new EntityRtMeta(rt, 0, false, null);
        }
        isBlended = true;
      }
      RenderSetup setup = ((OptRenderTypeStateAccessor) (Object) rt).nightclient$state();
      if (setup == null) return new EntityRtMeta(rt, 0, false, null);
      OptRenderSetupAccessor sa = (OptRenderSetupAccessor) (Object) setup;
      if (sa.nightclient$textureTransform() != TextureTransform.DEFAULT_TEXTURING
          || sa.nightclient$outputTarget() != OutputTarget.MAIN_TARGET) {
        return new EntityRtMeta(rt, 0, false, null);
      }

      int flags = 0;
      if (pipe.isCull()) flags |= NightNative.ENT_FLAG_CULL_FACE;
      if (isBlended) flags |= NightNative.ENT_FLAG_BLEND;
      if (!sa.nightclient$useLightmap()) flags |= NightNative.ENT_FLAG_EMISSIVE;
      if (!sa.nightclient$useOverlay()) flags |= NightNative.ENT_FLAG_NO_OVERLAY;

      ShaderDefines defines = pipe.getShaderDefines();
      if (defines != null) {
        if (defines.flags().contains("APPLY_TEXTURE_MATRIX")) {
          return new EntityRtMeta(rt, 0, false, null);
        }
        if (defines.flags().contains("NO_CARDINAL_LIGHTING")) {
          flags |= NightNative.ENT_FLAG_NO_CARDINAL_LIGHT;
        }
        if (defines.flags().contains("PER_FACE_LIGHTING")) {
          flags |= NightNative.ENT_FLAG_PER_FACE_LIGHT;
        }
        if (defines.flags().contains("EMISSIVE")) {
          flags |= NightNative.ENT_FLAG_EMISSIVE;
        }
        if (defines.flags().contains("NO_OVERLAY")) {
          flags |= NightNative.ENT_FLAG_NO_OVERLAY;
        }
        String alpha = defines.values().get("ALPHA_CUTOUT");
        if (alpha != null) {
          if ("0.1".equals(alpha) || "0.1F".equalsIgnoreCase(alpha)) {
            flags |= NightNative.ENT_FLAG_CUTOUT_01;
          } else if ("0.5".equals(alpha) || "0.5F".equalsIgnoreCase(alpha)) {
            flags |= NightNative.ENT_FLAG_CUTOUT_05;
          } else {
            return new EntityRtMeta(rt, 0, false, null);
          }
        }
      }

      GlTextureView tv = resolveSampler0View(rt);
      if (tv == null) {
        return new EntityRtMeta(rt, 0, false, null);
      }
      return new EntityRtMeta(rt, flags, true, tv);
    } catch (Throwable ignored) {
      return new EntityRtMeta(rt, 0, false, null);
    }
  }

  private static GlTextureView resolveSampler0View(RenderType rt) {
    try {
      RenderSetup setup = ((OptRenderTypeStateAccessor) (Object) rt).nightclient$state();
      if (setup == null) return null;
      Map<String, RenderSetup.TextureAndSampler> textures = setup.getTextures();
      if (textures == null) return null;
      RenderSetup.TextureAndSampler ts = textures.get("Sampler0");
      if (ts == null) return null;
      GpuTextureView tv = ts.textureView();
      if (tv instanceof GlTextureView && !tv.isClosed()) {
        return (GlTextureView) tv;
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  public static void beginEntityBatch(
      EntityRtMeta meta,
      MultiBufferSource.BufferSource fallbackSource,
      RenderType rt) {
    curGlTexId = meta.glTexId();
    curFlags = meta.flags;
    curU0 = 0.0f;
    curV0 = 0.0f;
    curUScale = 1.0f;
    curVScale = 1.0f;
    curFallbackSource = fallbackSource;
    curFallbackRt = rt;
    curFallbackSprite = null;
    curFallbackVc = null;
  }

  public static void setEntitySprite(TextureAtlasSprite sprite) {
    if (sprite != curFallbackSprite) {
      curFallbackSprite = sprite;
      curFallbackVc = null;
      if (sprite != null) {
        curU0 = sprite.getU0();
        curV0 = sprite.getV0();
        curUScale = sprite.getU1() - curU0;
        curVScale = sprite.getV1() - curV0;
      } else {
        curU0 = 0.0f;
        curV0 = 0.0f;
        curUScale = 1.0f;
        curVScale = 1.0f;
      }
    }
  }

  public static void beginEntitySubmit(
      EntityRtMeta meta,
      TextureAtlasSprite sprite,
      MultiBufferSource.BufferSource fallbackSource,
      RenderType rt) {
    beginEntityBatch(meta, fallbackSource, rt);
    if (sprite != null) {
      setEntitySprite(sprite);
    }
  }

  public static void endEntitySubmit() {
    curFallbackSource = null;
    curFallbackRt = null;
    curFallbackSprite = null;
    curFallbackVc = null;
    curGlTexId = 0;
  }

  private static long ensureScratch(int bytes) {
    if (scratchMeshCapBytes < bytes) {
      int next = Math.max(bytes, Math.max(65536, scratchMeshCapBytes << 1));
      if (scratchMeshAddr != 0L) MemoryUtil.nmemFree(scratchMeshAddr);
      scratchMeshAddr = MemoryUtil.nmemAlloc(next);
      scratchMeshCapBytes = scratchMeshAddr != 0L ? next : 0;
    }
    return scratchMeshAddr;
  }

  public static int registerModelPart(List<ModelPart.Cube> cubes) {
    if (cubes == null || cubes.isEmpty() || !NightNative.isGpuInstancerReady()) return 0;
    int totalQuads = 0;
    for (int c = 0; c < cubes.size(); c++) {
      ModelPart.Cube cube = cubes.get(c);
      if (cube == null || cube.polygons == null) continue;
      for (int p = 0; p < cube.polygons.length; p++) {
        ModelPart.Polygon poly = cube.polygons[p];
        if (poly != null && poly.vertices() != null && poly.vertices().length == 4 && poly.normal() != null) {
          totalQuads++;
        }
      }
    }
    if (totalQuads == 0) return 0;
    int vertCount = totalQuads << 2;
    long dst = ensureScratch(vertCount * 32);
    if (dst == 0L) return 0;

    long ptr = dst;
    for (int c = 0; c < cubes.size(); c++) {
      ModelPart.Cube cube = cubes.get(c);
      if (cube == null || cube.polygons == null) continue;
      for (int p = 0; p < cube.polygons.length; p++) {
        ModelPart.Polygon poly = cube.polygons[p];
        if (poly == null || poly.vertices() == null || poly.vertices().length != 4 || poly.normal() == null) {
          continue;
        }
        Vector3fc n = poly.normal();
        float nx = n.x(), ny = n.y(), nz = n.z();
        ModelPart.Vertex[] verts = poly.vertices();
        for (int v = 0; v < 4; v++) {
          ModelPart.Vertex mv = verts[v];
          MemoryUtil.memPutFloat(ptr, @@MODEL_VERTEX_X@@);
          MemoryUtil.memPutFloat(ptr + 4L, @@MODEL_VERTEX_Y@@);
          MemoryUtil.memPutFloat(ptr + 8L, @@MODEL_VERTEX_Z@@);
          MemoryUtil.memPutFloat(ptr + 12L, mv.u());
          MemoryUtil.memPutFloat(ptr + 16L, mv.v());
          MemoryUtil.memPutFloat(ptr + 20L, nx);
          MemoryUtil.memPutFloat(ptr + 24L, ny);
          MemoryUtil.memPutFloat(ptr + 28L, nz);
          ptr += 32L;
        }
      }
    }
    return NightNative.registerEntityMesh(dst, vertCount);
  }

  public static boolean submitEntityPart(
      int meshId, Matrix4fc pose, int packedLight, int packedOverlay, int argbColor) {
    int idx = entityCount;
    if (idx >= MAX_ENTITY_INSTANCES || curGlTexId <= 0 || entityBufAddr == 0L) return false;
    long p = entityBufAddr + (long) idx * ENTITY_INSTANCE_BYTES;
    pose.getToAddress(p);
    MemoryUtil.memPutFloat(p + 64L, curU0);
    MemoryUtil.memPutFloat(p + 68L, curV0);
    MemoryUtil.memPutFloat(p + 72L, curUScale);
    MemoryUtil.memPutFloat(p + 76L, curVScale);
    MemoryUtil.memPutInt(p + 80L, meshId);
    MemoryUtil.memPutInt(p + 84L, curGlTexId);
    MemoryUtil.memPutInt(p + 88L, packedLight);
    MemoryUtil.memPutInt(p + 92L, packedOverlay);
    MemoryUtil.memPutInt(p + 96L, argbColor);
    MemoryUtil.memPutInt(p + 100L, curFlags);
    MemoryUtil.memPutInt(p + 104L, 0);
    MemoryUtil.memPutInt(p + 108L, 0);
    entityCount = idx + 1;
    return true;
  }

  private static TextRtMeta resolveTextRt(RenderType rt, boolean hasUv) {
    if (rt == null) return null;
    TextRtMeta cached = TEXT_RT_CACHE.get(rt);
    if (cached != null) return cached.eligible ? cached : null;
    TextRtMeta computed = inspectTextRt(rt, hasUv);
    TEXT_RT_CACHE.put(rt, computed);
    return computed.eligible ? computed : null;
  }

  private static TextRtMeta inspectTextRt(RenderType rt, boolean hasUv) {
    try {
      if (rt.mode() != VertexFormat.Mode.QUADS || rt.isOutline()) {
        return new TextRtMeta(rt, 0, hasUv, false, null);
      }
      RenderPipeline pipe = rt.pipeline();
      if (pipe == null) return new TextRtMeta(rt, 0, hasUv, false, null);
      RenderSetup setup = ((OptRenderTypeStateAccessor) (Object) rt).nightclient$state();
      if (setup == null) return new TextRtMeta(rt, 0, hasUv, false, null);
      OptRenderSetupAccessor sa = (OptRenderSetupAccessor) (Object) setup;
      if (sa.nightclient$textureTransform() != TextureTransform.DEFAULT_TEXTURING
          || sa.nightclient$outputTarget() != OutputTarget.MAIN_TARGET
          || !sa.nightclient$useLightmap()) {
        return new TextRtMeta(rt, 0, hasUv, false, null);
      }

      int flags = 0;
      if (pipe.getDepthTestFunction() == DepthTestFunction.NO_DEPTH_TEST) {
        flags |= NightNative.TXT_FLAG_SEE_THROUGH;
      }
      String rtName = rt.toString();
      if ((rtName != null && rtName.contains("polygon_offset"))
          || pipe.getDepthBiasScaleFactor() != 0.0f
          || pipe.getDepthBiasConstant() != 0.0f) {
        flags |= NightNative.TXT_FLAG_POLYGON_OFFSET;
      }
      if (rtName != null && rtName.contains("intensity")) {
        flags |= NightNative.TXT_FLAG_INTENSITY;
      }

      VertexFormat fmt = rt.format();
      if (fmt != DefaultVertexFormat.POSITION_COLOR_LIGHTMAP
          && fmt != DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
        return new TextRtMeta(rt, 0, hasUv, false, null);
      }

      GlTextureView tv = resolveSampler0View(rt);
      if (tv == null) {
        if (fmt == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP
            || (rtName != null && rtName.contains("background"))) {
          flags |= NightNative.TXT_FLAG_NO_TEX;
          return new TextRtMeta(rt, flags, false, true, null);
        }
        return new TextRtMeta(rt, 0, hasUv, false, null);
      }
      return new TextRtMeta(rt, flags, true, true, tv);
    } catch (Throwable ignored) {
      return new TextRtMeta(rt, 0, hasUv, false, null);
    }
  }

  public static boolean trySubmitTextBucket(
      TextQuadCache.Bucket b, Matrix4fc pose, int lightCoords) {
    if (!isTextInstancingActive() || b == null || b.quadCount <= 0) return false;
    int idx = textCount;
    if (idx >= MAX_TEXT_INSTANCES) return false;

    TextRtMeta meta = resolveTextRt(b.renderType, b.hasUv);
    if (meta == null) return false;

    int glTexId = meta.glTexId();
    if (meta.hasTex && glTexId <= 0) return false;

    long gen = meshGeneration();
    int meshId = b.nativeMeshId;
    if (meshId <= 0 || b.nativeMeshGen != gen) {
      meshId = registerTextBucket(b.xyzuv, b.abgrs, b.quadCount);
      b.nativeMeshId = meshId;
      b.nativeMeshGen = gen;
    }
    if (meshId <= 0) return false;

    long p = textBufAddr + (long) idx * TEXT_INSTANCE_BYTES;
    pose.getToAddress(p);
    MemoryUtil.memPutInt(p + 64L, meshId);
    MemoryUtil.memPutInt(p + 68L, glTexId);
    MemoryUtil.memPutInt(p + 72L, lightCoords);
    MemoryUtil.memPutInt(p + 76L, meta.flags);
    MemoryUtil.memPutInt(p + 80L, 0);
    MemoryUtil.memPutInt(p + 84L, 0);
    MemoryUtil.memPutInt(p + 88L, 0);
    MemoryUtil.memPutInt(p + 92L, 0);
    textCount = idx + 1;
    return true;
  }

  private static int registerTextBucket(float[] xyzuv, int[] abgrs, int quadCount) {
    int vertCount = quadCount << 2;
    long dst = ensureScratch(vertCount * 24);
    if (dst == 0L) return 0;
    long ptr = dst;
    int fi = 0;
    for (int vi = 0; vi < vertCount; vi++) {
      MemoryUtil.memPutFloat(ptr, xyzuv[fi]);
      MemoryUtil.memPutFloat(ptr + 4L, xyzuv[fi + 1]);
      MemoryUtil.memPutFloat(ptr + 8L, xyzuv[fi + 2]);
      MemoryUtil.memPutFloat(ptr + 12L, xyzuv[fi + 3]);
      MemoryUtil.memPutFloat(ptr + 16L, xyzuv[fi + 4]);
      MemoryUtil.memPutInt(ptr + 20L, abgrs[vi]);
      fi += 5;
      ptr += 24L;
    }
    return NightNative.registerTextMesh(dst, vertCount);
  }

  public static void flushFrame() {
    int ec = entityCount;
    int tc = textCount;
    entityCount = 0;
    textCount = 0;
    if ((ec <= 0 && tc <= 0) || paramsBufAddr == 0L || !NightNative.isGpuInstancerReady()) {
      return;
    }
    try {
      GpuDevice rawDev = RenderSystem.tryGetDevice();
      if (!(rawDev instanceof GlDevice)) return;
      GlDevice device = (GlDevice) rawDev;

      Minecraft mc = Minecraft.getInstance();
      RenderTarget target = mc.getMainRenderTarget();
      if (target == null) return;
      GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
          ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
      GpuTextureView depthView = target.useDepth
          ? (RenderSystem.outputDepthTextureOverride != null
              ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
          : null;
      if (!(colorView instanceof GlTextureView) || colorView.isClosed()) return;
      int fboId = ((GlTextureView) colorView).getFbo(
          device.directStateAccess(),
          depthView != null ? depthView.texture() : null);
      int vpW = colorView.getWidth(0);
      int vpH = colorView.getHeight(0);

      GpuBufferSlice projSlice = RenderSystem.getProjectionMatrixBuffer();
      GpuBufferSlice lightSlice = RenderSystem.getShaderLights();
      GpuBufferSlice fogSlice = RenderSystem.getShaderFog();
      if (projSlice == null || !(projSlice.buffer() instanceof GlBuffer)) return;

      int projUbo = ((OptGlBufferAccessor) (Object) projSlice.buffer()).nightclient$handle();
      long projOff = projSlice.offset();
      long projLen = projSlice.length();

      int lightUbo = 0;
      long lightOff = 0L, lightLen = 0L;
      if (lightSlice != null && lightSlice.buffer() instanceof GlBuffer) {
        lightUbo = ((OptGlBufferAccessor) (Object) lightSlice.buffer()).nightclient$handle();
        lightOff = lightSlice.offset();
        lightLen = lightSlice.length();
      }

      int fogUbo = 0;
      long fogOff = 0L, fogLen = 0L;
      if (fogSlice != null && fogSlice.buffer() instanceof GlBuffer) {
        fogUbo = ((OptGlBufferAccessor) (Object) fogSlice.buffer()).nightclient$handle();
        fogOff = fogSlice.offset();
        fogLen = fogSlice.length();
      }

      GameRenderer gr = mc.gameRenderer;
      if (gr == null) return;
      GpuTextureView ovView = gr.overlayTexture().getTextureView();
      GpuTextureView lmView = gr.lightTexture().getTextureView();
      if (!(ovView instanceof GlTextureView) || !(lmView instanceof GlTextureView)) return;
      int overlayTexId = ((GlTextureView) ovView).texture().glId();
      int lightmapTexId = ((GlTextureView) lmView).texture().glId();

      long p = paramsBufAddr;
      MemoryUtil.memPutLong(p, projOff);
      MemoryUtil.memPutLong(p + 8L, projLen);
      MemoryUtil.memPutLong(p + 16L, lightOff);
      MemoryUtil.memPutLong(p + 24L, lightLen);
      MemoryUtil.memPutLong(p + 32L, fogOff);
      MemoryUtil.memPutLong(p + 40L, fogLen);
      MemoryUtil.memPutInt(p + 48L, fboId);
      MemoryUtil.memPutInt(p + 52L, vpW);
      MemoryUtil.memPutInt(p + 56L, vpH);
      MemoryUtil.memPutInt(p + 60L, projUbo);
      MemoryUtil.memPutInt(p + 64L, lightUbo);
      MemoryUtil.memPutInt(p + 68L, fogUbo);
      MemoryUtil.memPutInt(p + 72L, overlayTexId);
      MemoryUtil.memPutInt(p + 76L, lightmapTexId);
      RenderSystem.getModelViewMatrix().getToAddress(p + 80L);

      NightNative.flushFrame(p, entityBufAddr, ec, textBufAddr, tc);
      if (++flushCounter == 120
          && (Boolean.getBoolean("nightclient.native.debug")
              || System.getProperty("nightclient.bench.spec") != null)) {
        long[] st = new long[10];
        long stMem = MemoryUtil.nmemAlloc(80L);
        if (stMem != 0L) {
          try {
            NightNative.readStats(stMem, 10);
            for (int i = 0; i < 10; i++) st[i] = MemoryUtil.memGetLong(stMem + (long) i * 8L);
          } finally {
            MemoryUtil.nmemFree(stMem);
          }
        }
        System.out.println(
            "[NightClient] nightclient-native active @120f: entInst="
                + st[0] + " (draws=" + st[1] + "), txtInst=" + st[2] + " (draws=" + st[3]
                + "), pbo=" + st[4] + ", pktInflate=" + st[6] + " (lastFrame ec=" + ec + ", tc=" + tc + ")");
      }
    } catch (Throwable t) {
      System.err.println("[NightClient] NativeGpuBridge flush failed: " + t);
    }
  }
}
