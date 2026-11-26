package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.platform.opt.NightNative;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.CompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompressionDecoder.class)
public abstract class OptNativeCompressionDecoderMixin {
  @Shadow private int threshold;
  @Shadow private boolean validateDecompressed;

  @Unique
  private static int nightclient$readVarInt(ByteBuf buf) {
    int out = 0;
    int shift = 0;
    while (buf.isReadable()) {
      byte b = buf.readByte();
      out |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) return out;
      shift += 7;
      if (shift > 35) return -1;
    }
    return -1;
  }

  @Inject(
      method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$nativeDecode(
      ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
    if (!OptimizationBridge.opt167NativePacketCodec || !NightNative.isLoaded()) return;
    if (in == null || in.readableBytes() == 0 || !in.hasMemoryAddress()) return;

    int savedReader = in.readerIndex();
    int uncompressedLen = nightclient$readVarInt(in);
    if (uncompressedLen <= 0) {
      in.readerIndex(savedReader);
      return;
    }
    if (this.validateDecompressed && (uncompressedLen < this.threshold || uncompressedLen > 8388608)) {
      in.readerIndex(savedReader);
      return;
    }
    int srcLen = in.readableBytes();
    if (srcLen <= 0) {
      in.readerIndex(savedReader);
      return;
    }

    ByteBuf dst = ctx.alloc().directBuffer(uncompressedLen);
    if (!dst.hasMemoryAddress()) {
      dst.release();
      in.readerIndex(savedReader);
      return;
    }

    long srcAddr = in.memoryAddress() + in.readerIndex();
    long dstAddr = dst.memoryAddress();
    int written = NightNative.zlibInflate(srcAddr, srcLen, dstAddr, uncompressedLen);
    if (written == uncompressedLen) {
      in.readerIndex(in.writerIndex());
      dst.writerIndex(uncompressedLen);
      out.add(dst);
      ci.cancel();
      return;
    }

    dst.release();
    in.readerIndex(savedReader);
  }
}
