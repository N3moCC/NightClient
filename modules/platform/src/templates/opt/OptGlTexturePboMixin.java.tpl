package dev.nightclient.platform.mixin;

@@PBO_GL_ENCODER_IMPORT@@
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.opt.NightNative;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@@PBO_GL_ENCODER_MIXIN@@
public abstract class OptGlTexturePboMixin {
  @Shadow private boolean inRenderPass;

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

  @Inject(
      method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIIIIIII)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$pboWriteToTexture(
      GpuTexture dest,
      NativeImage src,
      int mipLevel,
      int depthOrLayer,
      int dstX,
      int dstY,
      int width,
      int height,
      int srcX,
      int srcY,
      CallbackInfo ci) {
    if (!OptimizationBridge.opt166NativePboUploads) return;
    if (this.inRenderPass || dest == null || src == null) return;
    if (width * height < 256 || depthOrLayer != 0) return;
    if (!(dest instanceof GlTexture) || dest.isClosed()) return;
    int usage = dest.usage();
    if ((usage & 1) == 0 || (usage & 16) != 0) return;
    if (mipLevel < 0 || mipLevel >= dest.getMipLevels()) return;
    if (srcX < 0 || srcY < 0 || dstX < 0 || dstY < 0) return;
    if (srcX + width > src.getWidth() || srcY + height > src.getHeight()) return;
    if (dstX + width > dest.getWidth(mipLevel) || dstY + height > dest.getHeight(mipLevel)) return;
    if (!nightclient$ensurePboReady()) return;

    long srcPtr = src.getPointer();
    if (srcPtr == 0L) return;
    int glTexId = ((GlTexture) dest).glId();
    int components = src.format().components();
    if (NightNative.uploadTexture2dPbo(
        glTexId, mipLevel, dstX, dstY, width, height, srcPtr, src.getWidth(), srcX, srcY, components)) {
      ci.cancel();
    }
  }
}
