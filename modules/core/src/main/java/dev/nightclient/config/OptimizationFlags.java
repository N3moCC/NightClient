package dev.nightclient.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class OptimizationFlags {
  public static final int MAX_SLOTS = 192;

  public enum Tier {
    INFRA,
    LOSSLESS,
    SMART_VISUAL,
    AGGRESSIVE
  }

  public static final class Spec {
    public final int id;
    public final String code;
    public final int group;
    public final Tier tier;
    public final String name;
    public final String[] conflictingMods;

    Spec(int id, int group, Tier tier, String name, String... conflictingMods) {
      this.id = id;
      this.code = String.format(Locale.ROOT, "OPT_%03d", id);
      this.group = group;
      this.tier = tier;
      this.name = name;
      this.conflictingMods = conflictingMods == null ? new String[0] : conflictingMods;
    }
  }

  public static final int OPT_001_FACE_BUCKETS = 1;
  public static final int OPT_002_GREEDY_MERGING = 2;
  public static final int OPT_003_COMPACT_VERTEX = 3;
  public static final int OPT_004_REGIONAL_MDI = 4;
  public static final int OPT_005_GPU_HIZ_OCCLUSION = 5;
  public static final int OPT_006_STAGING_RING = 6;
  public static final int OPT_007_PREDICTIVE_CHUNK_UPLOAD = 7;
  public static final int OPT_008_SECTION_SNAPSHOT_POOL = 8;
  public static final int OPT_009_SIMD_SWAR_PACKING = 9;
  public static final int OPT_010_TRANSLUCENT_RADIX_SORT = 10;
  public static final int OPT_011_SMART_LEAVES_FACE_CULL = 11;
  public static final int OPT_012_SMART_ANIMATED_TEXTURES = 12;
  public static final int OPT_013_FRONT_TO_BACK_EARLY_Z = 13;
  public static final int OPT_014_BITMASK_OCCUPANCY_4X4X4 = 14;
  public static final int OPT_015_VRAM_SLAB_DEFRAG = 15;
  public static final int OPT_016_GPU_COMPUTE_CULLING = 16;
  public static final int OPT_017_EDGE_DISTANCE_LOD = 17;
  public static final int OPT_018_FAST_OCEAN_FLUID_MESH = 18;
  public static final int OPT_019_SHARED_QUAD_IBO = 19;
  public static final int OPT_020_VULKAN_MT_CMDBUF_CACHE = 20;
  public static final int OPT_021_GL_BINARY_CACHE_NO_ERR = 21;
  public static final int OPT_022_EMPTY_GLOW_FBO_BYPASS = 22;

  public static final int OPT_023_EARLY_ENTITY_CULL = 23;
  public static final int OPT_024_ASYNC_3D_DDA_OCCLUSION = 24;
  public static final int OPT_025_SECTION_ENTITY_FRUSTUM = 25;
  public static final int OPT_026_ENTITY_SHADOW_CULL = 26;
  public static final int OPT_027_ITEM_XP_CLUSTERING = 27;
  public static final int OPT_028_ENTITY_TRANSFORM_BATCH = 28;
  public static final int OPT_029_ENTITY_DISTANCE_LOD = 29;
  public static final int OPT_030_OCCLUDED_ANIM_THROTTLE = 30;
  public static final int OPT_031_DIRECT_ENTITY_RENDERER_FLAGS = 31;
  public static final int OPT_032_NAMETAG_OCCLUSION_CACHE = 32;
  public static final int OPT_033_STUCK_PROJECTILE_CLUSTER = 33;
  public static final int OPT_034_ARMOR_EMPTY_SLOT_SKIP = 34;
  public static final int OPT_035_COMPOSITE_MOB_TEXTURE_BAKE = 35;
  public static final int OPT_036_PRE_SETUP_ANIM_SKIP = 36;
  public static final int OPT_037_DISPLAY_ENTITY_OPT = 37;
  public static final int OPT_038_FLAT_MODEL_PART_ARRAY = 38;
  public static final int OPT_039_LEASH_BEAM_CULL = 39;

  public static final int OPT_040_STATIC_BE_CHUNK_BAKE = 40;
  public static final int OPT_041_EARLY_BE_CULL_OCCLUSION = 41;
  public static final int OPT_042_SIGN_TEXT_CULL_CACHE = 42;
  public static final int OPT_043_ITEM_FRAME_BACKFACE_CULL = 43;
  public static final int OPT_044_BEACON_CONDUIT_BOOK_CULL = 44;
  public static final int OPT_045_BANNER_COMPOSITE_BAKE = 45;
  public static final int OPT_046_SKULL_BATCH_OVERLAY_LOD = 46;
  public static final int OPT_047_DIRECT_BE_RENDERER_FILTER = 47;
  public static final int OPT_048_FALLING_PISTON_BATCH = 48;
  public static final int OPT_049_END_PORTAL_SHADER_LOD = 49;
  public static final int OPT_050_SPAWNER_VAULT_INNER_CULL = 50;
  public static final int OPT_051_DECORATED_POT_BELL_BAKE = 51;
  public static final int OPT_052_WORLD_BORDER_OUTLINE_CACHE = 52;

  public static final int OPT_053_O1_PARTICLE_PRIORITY_RING = 53;
  public static final int OPT_054_PARTICLE_RENDER_FRUSTUM_OCC = 54;
  public static final int OPT_055_ASYNC_PARTICLE_TICK = 55;
  public static final int OPT_056_FAST_PARTICLE_COLLISION = 56;
  public static final int OPT_057_PARTICLE_LIGHT_CACHE = 57;
  public static final int OPT_058_EMPTY_PARTICLE_SKIP_INSTANCING = 58;

  public static final int OPT_059_IMMEDIATE_DRAW_BATCHING = 59;
  public static final int OPT_060_POOLED_RING_GPU_BUFFERS = 60;
  public static final int OPT_061_FONT_GLYPH_RUN_CACHE = 61;
  public static final int OPT_062_RETAINED_VANILLA_HUD_TAB = 62;
  public static final int OPT_063_TEXT_WIDTH_FPS_STR_CACHE = 63;
  public static final int OPT_064_INVENTORY_SLOT_BATCH_SCISSOR = 64;
  public static final int OPT_065_IDLE_DEBUG_TOAST_UNFOCUSED = 65;
  public static final int OPT_066_CHAT_LINE_WRAP_CACHE = 66;
  public static final int OPT_067_ITEMSTACK_TOOLTIP_CACHE = 67;
  public static final int OPT_068_LAZY_SEARCH_TREE = 68;

  public static final int OPT_069_DIRTY_LIGHTMAP_CACHE = 69;
  public static final int OPT_070_FAST_SKY_FOG_SAMPLER = 70;
  public static final int OPT_071_PER_FRAME_SKY_FRUSTUM_CACHE = 71;
  public static final int OPT_072_FLAT_CLIENT_LIGHT_ENGINE = 72;
  public static final int OPT_073_BIOME_VORONOI_SECTION_CACHE = 73;
  public static final int OPT_074_CLOUD_WEATHER_HEIGHTMAP_CACHE = 74;

  public static final int OPT_075_BLOCKSTATE_NEIGHBOR_DEDUP = 75;
  public static final int OPT_076_ZERO_ALLOC_POS_DIRECTION_POOLS = 76;
  public static final int OPT_077_UNROLLED_PALETTE_BIT_UNPACK = 77;
  public static final int OPT_078_DETERMINISTIC_LEAK_CLEANUP = 78;
  public static final int OPT_079_ASYNC_SKIN_DECODE_BUDGET = 79;
  public static final int OPT_080_L1_FAST_MATH_LUT = 80;
  public static final int OPT_081_COMPILED_ASSET_DISK_CACHE = 81;
  public static final int OPT_082_LAZY_DFU_BOOTSTRAP = 82;
  public static final int OPT_083_LANGUAGE_ONLY_RELOAD = 83;
  public static final int OPT_084_PARALLEL_MIPMAP_STITCH = 84;

  public static final int OPT_085_CHUNK_PACKET_BURST_SMOOTH = 85;
  public static final int OPT_086_ZERO_COPY_PACKET_COMPRESS = 86;
  public static final int OPT_087_INPLACE_PACKET_CIPHER = 87;
  public static final int OPT_088_OUTBOUND_PACKET_COALESCE = 88;
  public static final int OPT_089_ENTITY_MOVE_PACKET_COALESCE = 89;
  public static final int OPT_090_INCREMENTAL_SCOREBOARD_DIFF = 90;

  public static final int OPT_091_FAST_CUBE_VOXEL_COLLISION = 91;
  public static final int OPT_092_SOUND_SPAM_DIST_CULL = 92;
  public static final int OPT_093_SINGLE_PASS_REDSTONE = 93;
  public static final int OPT_094_DENSITY_NOISE_OPT = 94;
  public static final int OPT_095_PARALLEL_CHUNK_IO_AUTOSAVE = 95;
  public static final int OPT_096_EVENT_AI_AREA_MAP = 96;
  public static final int OPT_097_SLEEPING_HOPPERS_BE = 97;
  public static final int OPT_098_FAST_EXPLOSION_RAYCAST = 98;
  public static final int OPT_099_ASYNC_WORLD_EXIT = 99;

  public static final int OPT_100_HYBRID_CPU_THREAD_PRIORITY = 100;
  public static final int OPT_101_FENCE_FRAME_PACING = 101;
  public static final int OPT_102_REDUNDANT_GPU_STATE_FILTER = 102;
  public static final int OPT_103_HIGH_HZ_MOUSE_COALESCE = 103;
  public static final int OPT_104_ADAPTIVE_QUALITY_GOVERNOR = 104;
  public static final int OPT_105_TEMPORAL_FRAME_BUDGET_SCHED = 105;
  public static final int OPT_106_ASYNC_COSMETIC_STREAM_BUDGET = 106;
  public static final int OPT_107_JVM_GC_OFFHEAP_GUARD = 107;
  public static final int OPT_108_ZERO_ALLOC_EVENT_HYGIENE = 108;
  public static final int OPT_109_HW_BOUND_AUTO_TUNER = 109;
  public static final int OPT_110_FRAME_SNAPSHOT_AUTO_COMPAT = 110;
  public static final int OPT_111_INGAME_TELEMETRY_BENCH = 111;
  public static final int OPT_112_OFFLINE_JVM_BENCH_CI = 112;

  public static final int OPT_113_CLIENT_MOB_PUSH_SKIP = 113;
  public static final int OPT_114_IDLE_DEBUG_SUBSCRIBER_SKIP = 114;
  public static final int OPT_115_ASCII_BIDI_FASTPATH = 115;
  public static final int OPT_116_FAST_MODEL_CUBE_EMIT = 116;
  public static final int OPT_117_FAST_BAKED_QUAD_EMIT = 117;
  public static final int OPT_118_FAST_MODEL_PART_ROTATE = 118;

  public static final int OPT_119_RETAINED_HUD_FBO_CACHE = 119;
  public static final int OPT_130_TERRAIN_FACE_BUCKET_CULL = 130;
  public static final int OPT_131_REGIONAL_MULTIDRAW_TERRAIN = 131;
  public static final int OPT_132_ALL_ALTITUDE_HORIZON_OCCLUSION = 132;
  public static final int OPT_134_COMPACT_CHUNK_VERTEX_FORMAT = 134;
  public static final int OPT_136_OFFHEAP_FLAT_CHUNK_MESHER = 136;
  public static final int OPT_139_WATER_GLASS_INTERIOR_CULL = 139;
  public static final int OPT_144_TEXT_DISPLAY_HOLOGRAM_CACHE = 144;
  public static final int OPT_146_IMMEDIATE_DRAW_BATCHING = 146;
  public static final int OPT_147_CROSS_ERA_STATIC_VRAM_BATCH = 147;
  public static final int OPT_150_SKIN_ATLAS_BATCHING = 150;
  public static final int OPT_157_INTERNAL_3D_UPSCALING_CAS = 157;
  public static final int OPT_158_HALF_RES_WEATHER_PARTICLES = 158;
  public static final int OPT_162_INSTANCED_PARTICLE_BILLBOARDS = 162;

  public static final int OPT_165_NATIVE_GPU_INSTANCING = 165;
  public static final int OPT_166_NATIVE_PBO_UPLOADS = 166;
  public static final int OPT_167_NATIVE_PACKET_CODEC = 167;

  private static final Spec[] SPECS = new Spec[MAX_SLOTS];
  private static final Map<String, Spec> BY_CODE = new HashMap<String, Spec>();

  static {
    reg(1, 1, Tier.LOSSLESS, "6+1 Directional Face-Bucket Culling", "sodium");
    reg(2, 1, Tier.LOSSLESS, "Coplanar / Greedy Quad Merging", "sodium");
    reg(3, 1, Tier.LOSSLESS, "Compact 16-20B Terrain Vertex Format", "sodium");
    reg(4, 1, Tier.LOSSLESS, "Regional Mega-Buffers + MDI", "sodium");
    reg(5, 1, Tier.LOSSLESS, "Hybrid GPU Hi-Z Pyramid + Async CPU Occlusion", "sodium", "nvidium");
    reg(6, 1, Tier.LOSSLESS, "Zero-Stall Persistent Mapped Staging Ring", "sodium");
    reg(7, 1, Tier.LOSSLESS, "Frame-Budgeted Predictive Chunk Upload", "sodium");
    reg(8, 1, Tier.LOSSLESS, "Versioned Immutable Section Snapshot Pool", "sodium");
    reg(9, 1, Tier.LOSSLESS, "SIMD / Branchless SWAR Vertex Packing & AO", "sodium");
    reg(10, 1, Tier.LOSSLESS, "Temporal-Coherent Radix Translucent Sorting", "sodium");
    reg(11, 1, Tier.SMART_VISUAL, "Smart Depth-Limited Leaves & Block-Face Culling", "moreculling");
    reg(12, 1, Tier.LOSSLESS, "Visibility-Tracked Smart Animated Textures", "sodium");
    reg(13, 1, Tier.LOSSLESS, "Front-to-Back Region Ordering + Early-Z Pre-Pass", "sodium");
    reg(14, 1, Tier.LOSSLESS, "4x4x4 Bitmask Occupancy Pyramid", "sodium");
    reg(15, 1, Tier.LOSSLESS, "Incremental VRAM Slab Allocator & Defragmenter", "sodium");
    reg(16, 1, Tier.LOSSLESS, "Cross-Vendor GPU Compute Culling", "sodium", "nvidium");
    reg(17, 1, Tier.SMART_VISUAL, "Edge-Distance Simplified Geometry LOD");
    reg(18, 1, Tier.LOSSLESS, "Fast Ocean / Source-Fluid Meshing Path", "sodium");
    reg(19, 1, Tier.LOSSLESS, "Global Shared Static Quad Index Buffer", "sodium");
    reg(20, 1, Tier.LOSSLESS, "Vulkan 26.x Multithreaded Secondary CmdBuffers & VkPipelineCache");
    reg(21, 1, Tier.LOSSLESS, "GL Program Binary Disk Cache & glGetError Strip");
    reg(22, 1, Tier.LOSSLESS, "Zero-Copy Post-Process & Empty Glow-FBO Bypass");

    reg(23, 2, Tier.SMART_VISUAL, "Early Entity Culling Before extractRenderState");
    reg(24, 2, Tier.LOSSLESS, "Async 3D-DDA Occlusion on 1-bit/block L1 Grid", "entityculling");
    reg(25, 2, Tier.LOSSLESS, "Section-Level Hierarchical Entity Frustum Pre-Cull");
    reg(26, 2, Tier.SMART_VISUAL, "Entity Shadow Culling & Fast Flat Shadow");
    reg(27, 2, Tier.AGGRESSIVE, "Dropped Item & XP Orb Visual Clustering");
    reg(28, 2, Tier.LOSSLESS, "Entity Transform Fast-Path & Static Entity Batching");
    reg(29, 2, Tier.SMART_VISUAL, "Distance Entity LOD");
    reg(30, 2, Tier.LOSSLESS, "Occluded / Distant Client Animation Throttle");
    reg(31, 2, Tier.LOSSLESS, "Direct O(1) EntityRenderer Cache & Lock-Free Flags", "badoptimizations");
    reg(32, 2, Tier.SMART_VISUAL, "Smart Nametag Occlusion + Layout/Width Cache");
    reg(33, 2, Tier.SMART_VISUAL, "Stuck Projectile / Arrow Cluster Culling");
    reg(34, 2, Tier.LOSSLESS, "Equipment / Armor Empty-Slot Skip & Trim Cache");
    reg(35, 2, Tier.LOSSLESS, "Composite Layered Mob Texture Baking");
    reg(36, 2, Tier.LOSSLESS, "Pre-setupAnim Skeletal Math Skip");
    reg(37, 2, Tier.SMART_VISUAL, "Display Entities Optimization");
    reg(38, 2, Tier.LOSSLESS, "Flat Pre-Order ModelPart Array Traversal", "sodium");
    reg(39, 2, Tier.SMART_VISUAL, "Leash / Fishing Line / Guardian Beam Cull");

    reg(40, 3, Tier.LOSSLESS, "Static BlockEntity Chunk Baking", "enhancedblockentities");
    reg(41, 3, Tier.SMART_VISUAL, "Early BlockEntity Cull Before extractRenderState + Occlusion");
    reg(42, 3, Tier.SMART_VISUAL, "Sign Text Distance + Backface Culling + Text Cache");
    reg(43, 3, Tier.SMART_VISUAL, "ItemFrame Backface, Occlusion & Inner-Item Cull", "moreculling");
    reg(44, 3, Tier.SMART_VISUAL, "Beacon Beam / Conduit / Enchanting Book / Campfire Cull");
    reg(45, 3, Tier.LOSSLESS, "Banner Composite Texture Baking");
    reg(46, 3, Tier.SMART_VISUAL, "Player Head / Skull Batching & Overlay LOD");
    reg(47, 3, Tier.LOSSLESS, "Direct O(1) BlockEntityRenderer Cache & Non-Rendering Filter", "badoptimizations");
    reg(48, 3, Tier.LOSSLESS, "FallingBlock & Moving Piston Batching");
    reg(49, 3, Tier.SMART_VISUAL, "End Portal / End Gateway Layered Shader LOD");
    reg(50, 3, Tier.SMART_VISUAL, "Spawner / Trial Spawner / Vault Inner-Entity Cull");
    reg(51, 3, Tier.LOSSLESS, "Decorated Pot / Bell / Lectern / Bookshelf Static Baking", "enhancedblockentities");
    reg(52, 3, Tier.LOSSLESS, "World Border & Block Selection Outline Geometry Cache");

    reg(53, 4, Tier.SMART_VISUAL, "Zero-Alloc O(1) Particle Admission & Priority Ring");
    reg(54, 4, Tier.LOSSLESS, "Particle Render Frustum + Distance + Occlusion Cull", "particle_core");
    reg(55, 4, Tier.LOSSLESS, "Async Double-Buffered Particle Physics Tick", "asyncparticles");
    reg(56, 4, Tier.SMART_VISUAL, "Fast Particle Collision Skip (No VoxelShape)");
    reg(57, 4, Tier.LOSSLESS, "Per-Block Particle Light Cache");
    reg(58, 4, Tier.LOSSLESS, "Empty Particle Queue Fast-Skip + GPU Billboard Instancing", "badoptimizations");

    reg(59, 5, Tier.LOSSLESS, "Universal Immediate-Mode Draw Batching", "immediatelyfast");
    reg(60, 5, Tier.LOSSLESS, "Pooled Ring GPU Buffers for Dynamic Geometry", "immediatelyfast");
    reg(61, 5, Tier.LOSSLESS, "Font Glyph-Run & Baked Text Mesh Cache", "immediatelyfast");
    reg(62, 5, Tier.SMART_VISUAL, "Event-Driven Retained Vanilla HUD / Tab / Scoreboard", "exordium");
    reg(63, 5, Tier.LOSSLESS, "Text Width Cache & Zero-Alloc Debug/FPS Strings", "badoptimizations");
    reg(64, 5, Tier.LOSSLESS, "Container / Inventory Slot Batching & Scissor Culling", "immediatelyfast");
    reg(65, 5, Tier.LOSSLESS, "Idle Debug Renderers, Empty Toast Skip & Unfocused Throttle", "badoptimizations", "dynamic_fps");
    reg(66, 5, Tier.LOSSLESS, "Chat History Component & Line-Wrap Cache");
    reg(67, 5, Tier.LOSSLESS, "ItemStack / DataComponents Tooltip Cache");
    reg(68, 5, Tier.LOSSLESS, "Lazy Background Recipe Book & Creative Search Index");

    reg(69, 6, Tier.LOSSLESS, "Dirty-Tracked Lightmap Texture Cache", "badoptimizations");
    reg(70, 6, Tier.LOSSLESS, "Fast Sky & Fog Color Sampler (No 216-Loop)", "badoptimizations");
    reg(71, 6, Tier.LOSSLESS, "Per-Frame Sky Angle, Fog & Frustum Cache", "badoptimizations");
    reg(72, 6, Tier.LOSSLESS, "Flat-Array Stateless Client Light Engine", "scalablelux", "starlight");
    reg(73, 6, Tier.LOSSLESS, "Biome Voronoi & Block-Tint Section Cache");
    reg(74, 6, Tier.LOSSLESS, "Cached Cloud Mesh & Fast Weather Heightmap");

    reg(75, 7, Tier.LOSSLESS, "BlockState Neighbor Table & Model Deduplication", "ferritecore");
    reg(76, 7, Tier.LOSSLESS, "Zero-Alloc MutableBlockPos, Direction.VALUES & Scratch Pools");
    reg(77, 7, Tier.LOSSLESS, "Unrolled / Vectorized PalettedContainer Bit-Unpack");
    reg(78, 7, Tier.LOSSLESS, "Deterministic World & Resource-Reload Leak Cleanup", "memoryleakfix");
    reg(79, 7, Tier.LOSSLESS, "Async Skin/Texture Decode & Budgeted GPU Upload");
    reg(80, 7, Tier.LOSSLESS, "L1-Friendly Fast Math LUT (4096-entry sin/cos)");
    reg(81, 7, Tier.LOSSLESS, "Persistent Compiled Model & Atlas Disk Cache", "dashloader");
    reg(82, 7, Tier.LOSSLESS, "Lazy DFU & Deferred Bootstrap Init", "lazydfu", "modernfix");
    reg(83, 7, Tier.LOSSLESS, "Language-Only Instant Reload", "languagereload");
    reg(84, 7, Tier.LOSSLESS, "Parallel Mipmap Generation & Atlas Stitching");

    reg(85, 8, Tier.LOSSLESS, "Chunk-Packet Burst Smoothing & Async Section Decode");
    reg(86, 8, Tier.LOSSLESS, "Native / Zero-Copy Packet Compression", "krypton");
    reg(87, 8, Tier.LOSSLESS, "Zero-Copy In-Place Packet Cipher Acceleration", "krypton");
    reg(88, 8, Tier.LOSSLESS, "Outbound Packet Coalescing & Tick Flush Batching", "krypton");
    reg(89, 8, Tier.LOSSLESS, "Client Entity-Move & Metadata Packet Coalescing");
    reg(90, 8, Tier.LOSSLESS, "Incremental Scoreboard / Team / Tab-List Diffing");

    reg(91, 9, Tier.LOSSLESS, "Fast Cube VoxelShape & Collision Bypass", "lithium");
    reg(92, 9, Tier.SMART_VISUAL, "SoundEngine Spam Deduplication & Distance Pre-Cull");
    reg(93, 9, Tier.LOSSLESS, "Single-Pass Iterative Redstone Propagation", "alternate-current", "lithium");
    reg(94, 9, Tier.LOSSLESS, "Density Function & Terrain Noise Optimization", "noisium");
    reg(95, 9, Tier.LOSSLESS, "Parallel Chunk I/O & Smooth Autosave Distribution", "c2me", "smoothchunk");
    reg(96, 9, Tier.LOSSLESS, "Event-Driven Mob AI & Spatial Area-Map Tracker", "lithium", "vmp");
    reg(97, 9, Tier.LOSSLESS, "Sleeping Hoppers & Inactive BlockEntity Tick Skip", "lithium");
    reg(98, 9, Tier.LOSSLESS, "Fast Explosion Exposure Raycast & Block-Break Batching", "lithium");
    reg(99, 9, Tier.LOSSLESS, "Async Non-Blocking World Exit / Save", "fastquit");

    reg(100, 10, Tier.LOSSLESS, "Hybrid-CPU Aware Thread Pool & Priority Tuning", "threadtweak");
    reg(101, 10, Tier.LOSSLESS, "Low-Latency Fence Frame Pacing & Render-Ahead Limiter");
    reg(102, 10, Tier.LOSSLESS, "Redundant GL/GPU State & UBO Dirty-Hash Filter");
    reg(103, 10, Tier.LOSSLESS, "High-Polling-Rate (4K/8K Hz) Mouse Input Coalescing");
    reg(104, 10, Tier.AGGRESSIVE, "Dynamic Adaptive Quality Governor (PID Low-FPS Guard)");
    reg(105, 10, Tier.LOSSLESS, "Central Temporal Frame-Budget Scheduler");
    reg(106, 10, Tier.LOSSLESS, "Async Cosmetic / Cape / 3D-Model Streaming Budget");
    reg(107, 10, Tier.LOSSLESS, "JVM GC & Off-Heap Guard");
    reg(108, 10, Tier.INFRA, "Zero-Alloc Event Bus & JIT C2 Monomorphic Hygiene");
    reg(109, 10, Tier.SMART_VISUAL, "Hardware-Aware CPU-Bound vs GPU-Bound Auto-Tuner");
    reg(110, 10, Tier.INFRA, "Zero-Overhead FrameSnapshot + Auto-Compatibility Matrix");
    reg(111, 10, Tier.INFRA, "In-Game Telemetry Profiler & 10-Scene Stress Suite");
    reg(112, 10, Tier.INFRA, "Offline JVM Micro-Benchmark Suite & CI Regression Guard");

    reg(113, 2, Tier.LOSSLESS, "Client-Side Non-Player Mob pushEntities() Skip");
    reg(114, 5, Tier.LOSSLESS, "Idle ClientDebugSubscriber & Debug Value Access Skip");
    reg(115, 5, Tier.LOSSLESS, "Pure-LTR / ASCII Fast-Path Bypassing ICU Bidi");
    reg(116, 2, Tier.LOSSLESS, "Flattened ModelPart$Cube Batch Vertex Emitter");
    reg(117, 2, Tier.LOSSLESS, "Zero-Alloc Fast BakedQuad Vertex Emitter", "sodium");
    reg(118, 2, Tier.LOSSLESS, "Axis-Specialized Zero-Alloc ModelPart.translateAndRotate", "sodium");

    reg(119, 5, Tier.SMART_VISUAL, "Retained Vanilla HUD FBO Cache", "exordium");
    reg(130, 1, Tier.LOSSLESS, "6+1 Directional Face-Bucket Terrain Culling", "sodium");
    reg(131, 1, Tier.LOSSLESS, "Regional Mega-VBO + MultiDraw Terrain Engine", "sodium");
    reg(132, 1, Tier.LOSSLESS, "All-Altitude 2D Horizon / Section Occlusion Culling", "sodium", "nvidium");
    reg(134, 1, Tier.LOSSLESS, "Compact Terrain Vertex Format", "sodium");
    reg(136, 1, Tier.LOSSLESS, "Zero-Alloc Off-Heap Flat Chunk Mesher", "sodium");
    reg(139, 1, Tier.SMART_VISUAL, "Water & Glass Interior / Backface Culling", "moreculling");
    reg(144, 2, Tier.LOSSLESS, "TextDisplay / Hologram Retained Quad & VBO Cache");
    reg(146, 5, Tier.LOSSLESS, "Universal Immediate-Mode Draw Batching", "immediatelyfast");
    reg(147, 3, Tier.LOSSLESS, "Cross-Era Static Entity & BlockEntity VRAM Batching (1.20-26.3)", "enhancedblockentities");
    reg(150, 2, Tier.LOSSLESS, "Player & NPC Dynamic Skin Atlas Batching");
    reg(157, 1, Tier.AGGRESSIVE, "Internal 3D Resolution Scaling + CAS Spatial Sharpening", "iris");
    reg(158, 4, Tier.SMART_VISUAL, "Half-Resolution Weather & Heavy Particle Pass");
    reg(162, 4, Tier.LOSSLESS, "GPU Instanced Particle Billboards");

    reg(165, 2, Tier.LOSSLESS, "Native Rust GPU-Instanced Entity & 3D Text Renderer (MDI + SSBO)");
    reg(166, 7, Tier.LOSSLESS, "Native Persistent-Mapped PBO Async Texture Upload Ring");
    reg(167, 8, Tier.LOSSLESS, "Native Zero-Copy Off-Heap SIMD Netty Packet Codec (zlib-rs)", "krypton");
  }

  private static void reg(int id, int group, Tier tier, String name, String... conflicts) {
    Spec spec = new Spec(id, group, tier, name, conflicts);
    SPECS[id] = spec;
    BY_CODE.put(spec.code, spec);
  }

  public static Spec registerDynamic(int id, int group, Tier tier, String name, String... conflicts) {
    if (id < 113 || id >= MAX_SLOTS) {
      throw new IllegalArgumentException("Dynamic optimization ID must be in [113, " + (MAX_SLOTS - 1) + "]");
    }
    Spec spec = new Spec(id, group, tier, name, conflicts);
    SPECS[id] = spec;
    BY_CODE.put(spec.code, spec);
    return spec;
  }

  public static Spec byId(int id) {
    return id >= 0 && id < MAX_SLOTS ? SPECS[id] : null;
  }

  public static Spec byCode(String code) {
    if (code == null) return null;
    return BY_CODE.get(code.trim().toUpperCase(Locale.ROOT));
  }

  public static int registeredCount() {
    int count = 0;
    for (Spec spec : SPECS) {
      if (spec != null) count++;
    }
    return count;
  }

  public static Map<String, Spec> allByCode() {
    return Collections.unmodifiableMap(BY_CODE);
  }

  public static final class BitMask {
    public final long w0;
    public final long w1;
    public final long w2;

    public static final BitMask EMPTY = new BitMask(0L, 0L, 0L);

    public BitMask(long w0, long w1, long w2) {
      this.w0 = w0;
      this.w1 = w1;
      this.w2 = w2;
    }

    public boolean has(int id) {
      if (id < 0 || id >= MAX_SLOTS) return false;
      if (id < 64) return (w0 & (1L << id)) != 0L;
      if (id < 128) return (w1 & (1L << (id - 64))) != 0L;
      return (w2 & (1L << (id - 128))) != 0L;
    }

    public int cardinality() {
      return Long.bitCount(w0) + Long.bitCount(w1) + Long.bitCount(w2);
    }

    public BitMask with(int id) {
      if (id < 0 || id >= MAX_SLOTS) return this;
      if (id < 64) return new BitMask(w0 | (1L << id), w1, w2);
      if (id < 128) return new BitMask(w0, w1 | (1L << (id - 64)), w2);
      return new BitMask(w0, w1, w2 | (1L << (id - 128)));
    }

    public BitMask without(int id) {
      if (id < 0 || id >= MAX_SLOTS) return this;
      if (id < 64) return new BitMask(w0 & ~(1L << id), w1, w2);
      if (id < 128) return new BitMask(w0, w1 & ~(1L << (id - 64)), w2);
      return new BitMask(w0, w1, w2 & ~(1L << (id - 128)));
    }

    public BitMask andNot(BitMask other) {
      return new BitMask(w0 & ~other.w0, w1 & ~other.w1, w2 & ~other.w2);
    }
  }

  public static BitMask computeConflictMask(Predicate<String> modLoaded) {
    if (modLoaded == null) return BitMask.EMPTY;
    long w0 = 0L, w1 = 0L, w2 = 0L;
    for (int i = 0; i < MAX_SLOTS; i++) {
      Spec spec = SPECS[i];
      if (spec == null || spec.conflictingMods.length == 0) continue;
      boolean conflict = false;
      for (String mod : spec.conflictingMods) {
        try {
          if (modLoaded.test(mod)) {
            conflict = true;
            break;
          }
        } catch (Throwable ignored) {
        }
      }
      if (conflict) {
        if (i < 64) w0 |= (1L << i);
        else if (i < 128) w1 |= (1L << (i - 64));
        else w2 |= (1L << (i - 128));
      }
    }
    return new BitMask(w0, w1, w2);
  }

  public static BitMask resolve(
      boolean moduleEnabled,
      String profile,
      boolean autoCompatibility,
      BitMask conflictMask,
      Set<String> enabledOverrides,
      Set<String> disabledOverrides,
      String systemModeOverride) {
    String mode = systemModeOverride != null ? systemModeOverride.trim() : "";
    if (!mode.isEmpty()) {
      return resolveSystemMode(mode, autoCompatibility ? conflictMask : BitMask.EMPTY);
    }
    if (!moduleEnabled) return BitMask.EMPTY;
    BitMask base = maskForProfile(profile);
    if (enabledOverrides != null) {
      for (String code : enabledOverrides) {
        Spec s = byCode(code);
        if (s != null) base = base.with(s.id);
      }
    }
    if (disabledOverrides != null) {
      for (String code : disabledOverrides) {
        Spec s = byCode(code);
        if (s != null) base = base.without(s.id);
      }
    }
    if (autoCompatibility && conflictMask != null) {
      base = base.andNot(conflictMask);
    }
    return base;
  }

  public static BitMask maskForProfile(String profile) {
    String p = profile == null ? "balanced" : profile.trim().toLowerCase(Locale.ROOT);
    long w0 = 0L, w1 = 0L, w2 = 0L;
    for (int i = 0; i < MAX_SLOTS; i++) {
      Spec spec = SPECS[i];
      if (spec == null) continue;
      boolean include = false;
      if ("all".equals(p) || "competitive".equals(p) || "extreme".equals(p)) {
        include = true;
      } else if ("performance".equals(p) || "ultra".equals(p)) {
        include = spec.tier != Tier.AGGRESSIVE;
      } else if ("lossless".equals(p)) {
        include = spec.tier == Tier.LOSSLESS || spec.tier == Tier.INFRA;
      } else {
        include = spec.tier == Tier.LOSSLESS || spec.tier == Tier.INFRA || spec.tier == Tier.SMART_VISUAL;
      }
      if (include) {
        if (i < 64) w0 |= (1L << i);
        else if (i < 128) w1 |= (1L << (i - 64));
        else w2 |= (1L << (i - 128));
      }
    }
    return new BitMask(w0, w1, w2);
  }

  private static BitMask resolveSystemMode(String mode, BitMask conflictMask) {
    String lower = mode.toLowerCase(Locale.ROOT);
    if ("baseline".equals(lower) || "off".equals(lower) || "none".equals(lower)) {
      return BitMask.EMPTY;
    }
    if ("all".equals(lower) || "lossless".equals(lower) || "performance".equals(lower)
        || "balanced".equals(lower) || "competitive".equals(lower)) {
      BitMask m = maskForProfile(lower);
      return conflictMask != null ? m.andNot(conflictMask) : m;
    }
    if (lower.startsWith("only:")) {
      BitMask m = BitMask.EMPTY.with(OPT_110_FRAME_SNAPSHOT_AUTO_COMPAT);
      for (String token : mode.substring(5).split(",")) {
        Spec s = byCode(token);
        if (s != null) m = m.with(s.id);
      }
      return conflictMask != null ? m.andNot(conflictMask) : m;
    }
    if (lower.startsWith("without:")) {
      BitMask m = maskForProfile("all");
      for (String token : mode.substring(8).split(",")) {
        Spec s = byCode(token);
        if (s != null) m = m.without(s.id);
      }
      return conflictMask != null ? m.andNot(conflictMask) : m;
    }
    if (lower.startsWith("profile:")) {
      String rest = mode.substring(8);
      String[] parts = rest.split("\\+", 2);
      BitMask m = maskForProfile(parts[0]);
      if (parts.length == 2) {
        for (String token : parts[1].split(",")) {
          if (token.startsWith("-")) {
            Spec s = byCode(token.substring(1));
            if (s != null) m = m.without(s.id);
          } else {
            Spec s = byCode(token);
            if (s != null) m = m.with(s.id);
          }
        }
      }
      return conflictMask != null ? m.andNot(conflictMask) : m;
    }
    return BitMask.EMPTY;
  }

  public static Set<String> parseCodeSet(String csv) {
    if (csv == null || csv.trim().isEmpty()) return Collections.emptySet();
    Set<String> out = new HashSet<String>();
    for (String part : Arrays.asList(csv.split(","))) {
      String trimmed = part.trim().toUpperCase(Locale.ROOT);
      if (!trimmed.isEmpty()) out.add(trimmed);
    }
    return out;
  }

  private OptimizationFlags() {}
}
