package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.opt.NightNative;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class OptLegacyNativeImagePboMixin {
  @Shadow @Final private NativeImage.Format format;
  @Shadow @Final private int width;
  @Shadow @Final private int height;
  @Shadow private long pixels;

  @Shadow private void checkAllocated() {}
  @Shadow public abstract void close();
  @@PBO_LEGACY_SET_FILTER_SHADOW@@

  @Unique private static boolean nightclient$glBootstrapped;

  @Unique
  private static boolean nightclient$ensurePboReady() {
    if (NightNative.isPboReady()) return true;
    if (nightclient$glBootstrapped || !NightNative.isLoaded()) return false;
    nightclient$glBootstrapped = true;
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
      return NightNative.isPboReady();
    } catch (Throwable ignored) {
      return false;
    }
  }

  @Inject(method = "@@PBO_LEGACY_UPLOAD_DESC@@", at = @At("HEAD"), cancellable = true)
  private void nightclient$pboLegacyUpload(
      int mipLevel,
      int dstX,
      int dstY,
      int srcX,
      int srcY,
      int w,
      int h,
      @@PBO_LEGACY_EXTRA_PARAMS@@boolean autoClose,
      CallbackInfo ci) {
    if (!OptimizationBridge.opt166NativePboUploads) return;
    if (mipLevel < 0 || dstX < 0 || dstY < 0 || srcX < 0 || srcY < 0 || w <= 0 || h <= 0) return;
    if ((long) srcX + w > this.width || (long) srcY + h > this.height) return;
    if (w * h < 256 || this.pixels == 0L) return;
    if (!nightclient$ensurePboReady()) return;

    RenderSystem.assertOnRenderThreadOrInit();
    this.checkAllocated();
    int boundTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    if (boundTex <= 0) return;

    @@PBO_LEGACY_PRE_UPLOAD@@
    boolean ok = NightNative.uploadTexture2dPbo(
        boundTex, mipLevel, dstX, dstY, w, h, this.pixels, this.width, srcX, srcY, this.format.components());
    if (!ok) return;

    @@PBO_LEGACY_POST_UPLOAD@@
    if (autoClose) {
      this.close();
    }
    ci.cancel();
  }
}
