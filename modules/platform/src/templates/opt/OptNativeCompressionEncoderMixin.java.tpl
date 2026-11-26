package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.opt.NightNative;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.CompressionEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompressionEncoder.class)
public abstract class OptNativeCompressionEncoderMixin {
  @Shadow private int threshold;

  @Unique
  private static void nightclient$writeVarInt(ByteBuf buf, int value) {
    int v = value;
    while ((v & ~0x7F) != 0) {
      buf.writeByte((v & 0x7F) | 0x80);
      v >>>= 7;
    }
    buf.writeByte(v);
  }

  @Inject(
      method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$nativeEncode(
      ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out, CallbackInfo ci) {
    if (!OptimizationBridge.opt167NativePacketCodec || !NightNative.isLoaded()) return;
    if (msg == null || out == null || !msg.hasMemoryAddress() || !out.hasMemoryAddress()) return;

    int srcLen = msg.readableBytes();
    if (srcLen < this.threshold || srcLen <= 0 || srcLen > 8388608) return;

    int savedOutWriter = out.writerIndex();
    nightclient$writeVarInt(out, srcLen);
    int bound = NightNative.zlibCompressBound(srcLen);
    out.ensureWritable(bound);
    if (!out.hasMemoryAddress()) {
      out.writerIndex(savedOutWriter);
      return;
    }

    long srcAddr = msg.memoryAddress() + msg.readerIndex();
    long dstAddr = out.memoryAddress() + out.writerIndex();
    int written = NightNative.zlibDeflate(srcAddr, srcLen, dstAddr, out.writableBytes(), 4);
    if (written > 0) {
      msg.readerIndex(msg.writerIndex());
      out.writerIndex(out.writerIndex() + written);
      ci.cancel();
      return;
    }
    out.writerIndex(savedOutWriter);
  }
}
