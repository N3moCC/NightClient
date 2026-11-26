package dev.nightclient.platform;

import dev.nightclient.platform.opt.BitGridOcclusionEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class WorldOcclusionSampler implements BitGridOcclusionEngine.SectionSampler {
  public static final WorldOcclusionSampler INSTANCE = new WorldOcclusionSampler();

  private WorldOcclusionSampler() {}

  @Override
  public int sampleSection(int secX, int secY, int secZ, long[] bits) {
    Minecraft mc = Minecraft.getInstance();
    ClientLevel level = mc.level;
    if (level == null) return 0;
    int secIdx = level.getSectionIndexFromSectionY(secY);
    if (secIdx < 0 || secIdx >= level.getSectionsCount()) return 0;
    LevelChunk chunk = level.getChunkSource().getChunk(secX, secZ, false);
    if (chunk == null) return 0;
    LevelChunkSection[] sections = chunk.getSections();
    if (sections == null || secIdx >= sections.length) return 0;
    LevelChunkSection section = sections[secIdx];
    if (section == null || section.hasOnlyAir()) return 0;

    int opaqueCount = 0;
    @@SAMPLER_POS_DECL@@
    for (int y = 0; y < 16; y++) {
      int yBase = y << 8;
      for (int z = 0; z < 16; z++) {
        int yzBase = yBase | (z << 4);
        for (int x = 0; x < 16; x++) {
          BlockState state = section.getBlockState(x, y, z);
          if (state.canOcclude() && @@STATE_SOLID_RENDER@@) {
            int idx = yzBase | x;
            bits[idx >>> 6] |= (1L << (idx & 63));
            opaqueCount++;
          }
        }
      }
    }
    if (opaqueCount == 0) return 0;
    if (opaqueCount == 4096) return 2;
    return 1;
  }
}
