package dev.nightclient.platform.mixin;

import dev.nightclient.platform.BenchmarkDirector;
import dev.nightclient.platform.GpuTimerProbe;
import dev.nightclient.platform.OptimizationBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class OptFrameHookMixin {
  @Inject(method = "runTick(Z)V", at = @At("HEAD"))
  private void nightclient$optFrameHead(boolean renderLevel, CallbackInfo callback) {
    Minecraft mc = (Minecraft) (Object) this;
    double cx = 0.0, cy = 0.0, cz = 0.0, lx = 0.0, ly = 0.0, lz = 1.0;
    try {
      if (mc.gameRenderer != null) {
        Camera cam = mc.gameRenderer.@@MAIN_CAMERA@@;
        if (cam != null) {
          Vec3 pos = cam.@@CAM_POS@@;
          if (pos != null) {
            cx = pos.x;
            cy = pos.y;
            cz = pos.z;
          }
          Vector3fc look = cam.@@CAM_LOOK@@;
          if (look != null) {
            lx = look.x();
            ly = look.y();
            lz = look.z();
          }
        }
      }
    } catch (Throwable ignored) {
    }
    OptimizationBridge.resetWorldCaches(mc.level);
    OptimizationBridge.beginFrame(cx, cy, cz, lx, ly, lz);
    if (mc.level != null && !BenchmarkDirector.active() && OptimizationBridge.consumeChunkRebuildRequest()) {
      try {
        @@BENCH_ALL_CHANGED@@
      } catch (Throwable ignored) {
      }
    }
    if (BenchmarkDirector.active()) {
      BenchmarkDirector.onFrameStart(mc);
      if (BenchmarkDirector.isRecording()) {
        GpuTimerProbe.beginFrame();
      }
    }
  }

  @Inject(method = "runTick(Z)V", at = @At("TAIL"))
  private void nightclient$optFrameTail(boolean renderLevel, CallbackInfo callback) {
    if (!BenchmarkDirector.active()) return;
    long gpuNanos = 0L;
    if (BenchmarkDirector.isRecording()) {
      GpuTimerProbe.endFrame();
      gpuNanos = GpuTimerProbe.lastGpuNanos();
    }
    BenchmarkDirector.onFrameEnd((Minecraft) (Object) this, gpuNanos);
  }
}
