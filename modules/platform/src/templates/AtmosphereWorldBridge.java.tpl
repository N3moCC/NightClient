package dev.nightclient.platform;

import dev.nightclient.renderer.atmosphere.AtmosphereEngine;
import dev.nightclient.renderer.atmosphere.AtmosphereWorldAccess;
import dev.nightclient.runtime.ClientRuntime;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
@@ATMO_WORLD_IMPORTS@@

public final class AtmosphereWorldBridge implements AtmosphereWorldAccess {
  public static final AtmosphereWorldBridge INSTANCE = new AtmosphereWorldBridge();

  private static final Map<Block, Byte> BLOCK_CLASS_CACHE = new IdentityHashMap<Block, Byte>();
  private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

  private static int timeTickDepth;

  private AtmosphereWorldBridge() {}

  public static void beginTimeTick() {
    timeTickDepth++;
  }

  public static void endTimeTick() {
    if (timeTickDepth > 0) timeTickDepth--;
  }

  public static boolean shouldForceNight(Object levelObj) {
    if (!(levelObj instanceof ClientLevel)) return false;
    ClientLevel level = (ClientLevel) levelObj;
    if (level.dimension() != Level.OVERWORLD) return false;
    ClientRuntime runtime = ClientRuntime.get();
    return runtime != null && AtmosphereEngine.shouldForceNight(runtime.settings(), true);
  }

  public static long forcedDayTime(Object levelObj) {
    if (timeTickDepth > 0 || !(levelObj instanceof ClientLevel)) return -1L;
    ClientLevel level = (ClientLevel) levelObj;
    if (level.dimension() != Level.OVERWORLD) return -1L;
    ClientRuntime runtime = ClientRuntime.get();
    return runtime != null ? AtmosphereEngine.forcedOverworldTime(runtime.settings(), true) : -1L;
  }

  public static long forcedClientDataTime(Object levelDataObj) {
    if (timeTickDepth > 0 || levelDataObj == null) return -1L;
    Minecraft mc = Minecraft.getInstance();
    ClientLevel level = mc != null ? mc.level : null;
    if (level == null || level.getLevelData() != levelDataObj) return -1L;
    if (level.dimension() != Level.OVERWORLD) return -1L;
    ClientRuntime runtime = ClientRuntime.get();
    return runtime != null ? AtmosphereEngine.forcedOverworldTime(runtime.settings(), true) : -1L;
  }

  public static long forcedActiveOverworldTime() {
    if (timeTickDepth > 0) return -1L;
    Minecraft mc = Minecraft.getInstance();
    ClientLevel level = mc != null ? mc.level : null;
    if (level == null || level.dimension() != Level.OVERWORLD) return -1L;
    ClientRuntime runtime = ClientRuntime.get();
    return runtime != null ? AtmosphereEngine.forcedOverworldTime(runtime.settings(), true) : -1L;
  }

  public static boolean shouldClearWeather(Object levelObj) {
    if (!(levelObj instanceof ClientLevel)) return false;
    ClientLevel level = (ClientLevel) levelObj;
    boolean overworld = level.dimension() == Level.OVERWORLD;
    ClientRuntime runtime = ClientRuntime.get();
    return runtime != null && AtmosphereEngine.shouldClearWeather(runtime.settings(), overworld);
  }

  public static boolean shouldReplaceVanillaClouds() {
    ClientRuntime runtime = ClientRuntime.get();
    return runtime != null && AtmosphereEngine.usesCustomClouds(
        runtime.settings(), INSTANCE.isOverworld());
  }

  @Override
  public Object worldIdentity() {
    return Minecraft.getInstance().level;
  }

  @Override
  public boolean isOverworld() {
    ClientLevel level = Minecraft.getInstance().level;
    return level != null && level.dimension() == Level.OVERWORLD;
  }

  @Override
  public long dayTime() {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) return 0L;
    return @@ATMO_DAY_TIME@@;
  }

  @Override
  public int renderDistanceChunks() {
    Minecraft mc = Minecraft.getInstance();
    if (mc.options == null) return 12;
    return @@ATMO_RENDER_DISTANCE@@;
  }

  @Override
  public boolean cameraSubmerged() {
    return AtmosphereBackend.isCameraSubmerged();
  }

  @Override
  public int skyLightAt(int x, int y, int z) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null || !level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
      return -1;
    }
    mutablePos.set(x, y, z);
    return level.getBrightness(LightLayer.SKY, mutablePos);
  }

  @Override
  public boolean scanColumn(int x, int camFootY, int z, int[] out) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null || !level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
      return false;
    }
    int minY = @@ATMO_MIN_Y@@;
    int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    out[0] = topY;
    out[3] = 0;
    out[4] = NO_GROUND_COVER;
    if (topY <= minY) {
      out[1] = minY;
      out[2] = 0;
      return true;
    }

    mutablePos.set(x, topY - 1, z);
    BlockState state = level.getBlockState(mutablePos);
    int type = classifyState(state);
    int groundY = topY;
    int groundCover = groundCover(level, state, type);
    int leafMask = 0;

    if (type == 3 || type == 4 || type == 6) {
      int floorLimit = Math.max(minY, topY - 41);
      int groundSearch = Math.max(floorLimit, topY - 5);
      for (int y = topY - 1; y >= groundSearch; y--) {
        mutablePos.set(x, y, z);
        BlockState block = level.getBlockState(mutablePos);
        if (block.isAir()) continue;
        int blockType = y == topY - 1 ? type : classifyState(block);
        if (blockType == 3) {
          type = 3;
          int layer = topY - 1 - y;
          if (layer < CANOPY_LAYERS) leafMask |= 1 << layer;
          groundY = floorLimit;
          groundCover = NO_GROUND_COVER;
          groundSearch = floorLimit;
          continue;
        }
        if (blockType == 4 || blockType == 6) continue;
        if (type == 3) {
          groundY = y + 1;
          groundCover = groundCover(level, block, blockType);
        }
        break;
      }
    }

    out[1] = groundY;
    out[2] = type;
    out[3] = leafMask;
    out[4] = groundCover;
    return true;
  }

  private int groundCover(ClientLevel level, BlockState state, int type) {
    if (type != 1 && type != 4 && type != 5) return NO_GROUND_COVER;
    VoxelShape shape = state.getShape(level, mutablePos);
    if (shape == Shapes.block()) return BROAD_GROUND_COVER;
    if (shape.isEmpty()
        || shape.min(Direction.Axis.X) > 0.125 || shape.max(Direction.Axis.X) < 0.875
        || shape.min(Direction.Axis.Z) > 0.125 || shape.max(Direction.Axis.Z) < 0.875) {
      return NO_GROUND_COVER;
    }
    double y = shape.max(Direction.Axis.Y);
    if (y <= 0.0 || y > 1.0) return NO_GROUND_COVER;
    int depth = (int) Math.round((1.0 - y) * 64.0);
    for (AABB box : shape.toAabbs()) {
      if (box.maxY == y && box.minX <= 0.0 && box.maxX >= 1.0
          && box.minZ <= 0.0 && box.maxZ >= 1.0) {
        return depth | BROAD_GROUND_COVER;
      }
    }
    return depth;
  }

  private static int classifyState(BlockState state) {
    if (state == null || state.isAir()) {
      return 0;
    }
    if (state.is(BlockTags.LEAVES)) {
      return 3;
    }
    if (state.is(BlockTags.LOGS)) {
      return 6;
    }
    Block block = state.getBlock();
    Byte kind = BLOCK_CLASS_CACHE.get(block);
    if (kind == null) {
      String path = @@ATMO_BLOCK_KEY_PATH@@;
      kind = classifyPath(path);
      if (BLOCK_CLASS_CACHE.size() > 2048) {
        BLOCK_CLASS_CACHE.clear();
      }
      BLOCK_CLASS_CACHE.put(block, kind);
    }
    if (kind == 3 || kind == 6) return kind;
    FluidState fluid = state.getFluidState();
    if (!fluid.isEmpty()) {
      return fluid.is(FluidTags.WATER) ? 2 : 0;
    }
    return kind;
  }

  private static byte classifyPath(String path) {
    if (path == null || path.isEmpty()) {
      return 1;
    }
    if (path.contains("leaves") || path.equals("nether_wart_block")
        || path.equals("warped_wart_block")) {
      return 3;
    }
    if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")
        || path.endsWith("_hyphae") || path.endsWith("mangrove_roots")) {
      return 6;
    }
    if (path.contains("planks")
        || path.contains("brick")
        || path.contains("cobble")
        || path.contains("wood")
        || path.contains("stairs")
        || path.contains("slab")
        || path.contains("wall")
        || path.contains("fence")
        || path.contains("door")
        || path.contains("trapdoor")
        || path.contains("glass")
        || path.contains("concrete")
        || path.contains("terracotta")
        || path.contains("wool")
        || path.contains("quartz")
        || path.contains("polished_")
        || path.contains("cut_")
        || path.contains("chiseled_")
        || path.contains("tiles")
        || path.contains("iron_block")
        || path.contains("copper")
        || path.contains("lantern")
        || path.contains("bookshelf")
        || path.contains("chest")
        || path.contains("barrel")) {
      return 5;
    }
    if (path.equals("snow")
        || path.equals("azalea")
        || path.equals("flowering_azalea")
        || path.contains("carpet")
        || path.contains("crop")
        || path.contains("wheat")
        || path.contains("flower")
        || path.contains("fern")
        || path.contains("bush")) {
      return 4;
    }
    return 1;
  }
}
