package dev.nightclient.platform.opt;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class NightNativeTest {

  private static long addressOf(ByteBuffer bb) throws Exception {
    try {
      Field f = java.nio.Buffer.class.getDeclaredField("address");
      f.setAccessible(true);
      return f.getLong(bb);
    } catch (Throwable ignored) {
      return NightNative.directBufferAddress(bb);
    }
  }

  @Test
  void loadsNativeLibraryAndRoundTripsZlibPackets() throws Exception {
    assertTrue(
        NightNative.ensureLoaded(),
        "Expected nightclient-native to load; status=" + NightNative.statusLabel());
    assertTrue(NightNative.isLoaded());

    byte[] payload = new byte[32768];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) ((i * 31 + (i >> 4)) & 0xFF);
    }

    int bound = NightNative.zlibCompressBound(payload.length);
    ByteBuffer src = ByteBuffer.allocateDirect(payload.length).order(ByteOrder.nativeOrder());
    ByteBuffer comp = ByteBuffer.allocateDirect(bound).order(ByteOrder.nativeOrder());
    ByteBuffer dst = ByteBuffer.allocateDirect(payload.length).order(ByteOrder.nativeOrder());
    src.put(payload).flip();

    long srcAddr = addressOf(src);
    long compAddr = addressOf(comp);
    long dstAddr = addressOf(dst);
    if (srcAddr == 0L || compAddr == 0L || dstAddr == 0L) {
      return;
    }

    NightNative.resetStats();
    int cLen = NightNative.zlibDeflate(srcAddr, payload.length, compAddr, bound, 4);
    assertTrue(cLen > 0 && cLen < payload.length, "Expected compression; cLen=" + cLen);

    int dLen = NightNative.zlibInflate(compAddr, cLen, dstAddr, payload.length);
    assertEquals(payload.length, dLen);

    byte[] restored = new byte[payload.length];
    dst.get(restored);
    assertArrayEquals(payload, restored);

    long[] stats = NightNative.readStatsSnapshot();
    assertTrue(stats[6] >= 1, "Expected packetsInflated >= 1");
    assertEquals(payload.length, stats[7], "Expected bytesInflated == payload.length");
    assertTrue(stats[8] >= 1, "Expected packetsDeflated >= 1");
  }
}
