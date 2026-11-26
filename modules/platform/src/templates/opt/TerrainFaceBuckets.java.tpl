package dev.nightclient.platform;

import java.util.Arrays;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;

public final class TerrainFaceBuckets {
  public static volatile long bufferGen = 1L;
  public static volatile long visibleGen = 1L;

  public interface SliceCacheHolder {
    Object[] nightclient$getCachedSlices();
    long nightclient$getCachedSliceGen();
    void nightclient$setCachedSlices(Object[] slices, long gen);
  }

  private TerrainFaceBuckets() {}

  public static final class SkyEmptyCache {
    private static final int SKY_SIZE = 8192;
    private static final int SKY_MASK = SKY_SIZE - 1;
    private static final long EMPTY_KEY = Long.MIN_VALUE;
    private static final Predicate<BlockState> HAS_FLUID = state -> !state.getFluidState().isEmpty();

    private final long[] keys = new long[SKY_SIZE];
    private final byte[] vals = new byte[SKY_SIZE];
    private Object lastWorld;
    private long lastClearFrame = -1000L;

    public SkyEmptyCache() {
      Arrays.fill(keys, EMPTY_KEY);
    }

    public void beginFrame(Object world, long frameIndex) {
      if (world != lastWorld || (frameIndex - lastClearFrame) >= 40L) {
        Arrays.fill(keys, EMPTY_KEY);
        lastWorld = world;
        lastClearFrame = frameIndex;
      }
    }

    public boolean isSealedUnderground(
        ClientLevel level,
        long sectionNode,
        int ox,
        int oy,
        int oz,
        LayerLightEventListener skyListener) {
      long k = sectionNode == EMPTY_KEY ? EMPTY_KEY + 1L : sectionNode;
      int slot = (int) ((k ^ (k >>> 32)) * 0x9E3779B97F4A7C15L >>> 51) & SKY_MASK;
      if (keys[slot] == k) {
        return vals[slot] == 1;
      }

      boolean sealed = false;
      DataLayer sky = skyListener.getDataLayerData(SectionPos.of(sectionNode));
      if (sky == null || sky.isEmpty()) {
        if (oy < 0) {
          sealed = true;
        } else {
          LevelChunk chunk = level.getChunkSource().getChunkNow(ox >> 4, oz >> 4);
          if (chunk != null) {
            LevelChunkSection[] secs = chunk.getSections();
            int idx = level.getSectionIndexFromSectionY(oy >> 4);
            boolean waterHere = idx >= 0 && idx < secs.length && secs[idx].maybeHas(HAS_FLUID);
            boolean waterAbove = (idx + 1) >= 0 && (idx + 1) < secs.length && secs[idx + 1].maybeHas(HAS_FLUID);
            sealed = !waterHere && !waterAbove;
          }
        }
      }

      keys[slot] = k;
      vals[slot] = (byte) (sealed ? 1 : 2);
      return sealed;
    }
  }
}
