package dev.nightclient.renderer;
import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
class RoundedMeshParityTest {
  public static String digest() throws Exception {
    MessageDigest hash = MessageDigest.getInstance("SHA-256");
    for (float w : new float[] {.5f, 5, 17.5f, 99})
      for (float h : new float[] {.5f, 6, 22.2f})
        for (float r : new float[] {.1f, .75f, 6, 30})
          for (float stroke : new float[] {0, .25f, 2})
            for (float density : new float[] {1, 1.5f, 3})
              for (int color : new int[] {0, 0x0700ff80, 0xffffffff, 0x8080f010})
                for (boolean gradient : new boolean[] {false, true}) {
                  float[] mesh = new float[RoundedQuadMesh.MAX_FLOATS + 8];
                  int count = RoundedQuadMesh.append(mesh, 8, 10.25f, -4.7f, w, h, r, stroke,
                      density, color, gradient ? 0xff123456 : color, gradient ? 0x6401ff04 : color,
                      gradient ? 0xeffeed01 : color);
                  for (int i = 0; i < count; i++) {
                    int n = Float.floatToIntBits(mesh[i]);
                    hash.update((byte) (n >>> 24));
                    hash.update((byte) (n >>> 16));
                    hash.update((byte) (n >>> 8));
                    hash.update((byte) n);
                  }
                }
    StringBuilder out = new StringBuilder();
    for (byte n : hash.digest()) out.append(String.format("%02x", n & 255));
    return out.toString();
  }
  @Test
  void solidAndGradientMeshesPreservePackaged0114Coordinates() throws Exception {
    assertEquals("ca5b53af8a8d6d6b8bf929cb032b2a81af943b0528d154afcee6beeca8d5ddb7", digest());
  }
}
