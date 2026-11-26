package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizationFlagsTest {
  @Test
  void all112BasePointsAreRegisteredWithUniqueCodes() {
    assertTrue(OptimizationFlags.registeredCount() >= 112);
    for (int id = 1; id <= 114; id++) {
      OptimizationFlags.Spec spec = OptimizationFlags.byId(id);
      assertNotNull(spec, "Missing OPT_" + id);
      assertEquals(spec, OptimizationFlags.byCode(spec.code));
      assertTrue(spec.group >= 1 && spec.group <= 10);
      assertNotNull(spec.tier);
    }
  }

  @Test
  void profilesRespectTierBoundariesAndAutoCompatibility() {
    OptimizationFlags.BitMask lossless = OptimizationFlags.maskForProfile("lossless");
    assertTrue(lossless.has(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION));
    assertTrue(lossless.has(OptimizationFlags.OPT_132_ALL_ALTITUDE_HORIZON_OCCLUSION));
    assertTrue(lossless.has(OptimizationFlags.OPT_144_TEXT_DISPLAY_HOLOGRAM_CACHE));
    assertTrue(lossless.has(OptimizationFlags.OPT_147_CROSS_ERA_STATIC_VRAM_BATCH));
    assertFalse(lossless.has(OptimizationFlags.OPT_119_RETAINED_HUD_FBO_CACHE));
    assertFalse(lossless.has(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING));
    assertFalse(lossless.has(OptimizationFlags.OPT_011_SMART_LEAVES_FACE_CULL));

    OptimizationFlags.BitMask perf = OptimizationFlags.maskForProfile("performance");
    assertTrue(perf.has(OptimizationFlags.OPT_011_SMART_LEAVES_FACE_CULL));
    assertTrue(perf.has(OptimizationFlags.OPT_119_RETAINED_HUD_FBO_CACHE));
    assertTrue(perf.has(OptimizationFlags.OPT_132_ALL_ALTITUDE_HORIZON_OCCLUSION));
    assertTrue(perf.has(OptimizationFlags.OPT_144_TEXT_DISPLAY_HOLOGRAM_CACHE));
    assertTrue(perf.has(OptimizationFlags.OPT_147_CROSS_ERA_STATIC_VRAM_BATCH));
    assertFalse(perf.has(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING));

    OptimizationFlags.BitMask comp = OptimizationFlags.maskForProfile("competitive");
    assertTrue(comp.has(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING));
    assertTrue(comp.has(OptimizationFlags.OPT_104_ADAPTIVE_QUALITY_GOVERNOR));

    Set<String> loaded = new HashSet<String>();
    loaded.add("sodium");
    loaded.add("entityculling");
    OptimizationFlags.BitMask conflicts = OptimizationFlags.computeConflictMask(loaded::contains);
    assertTrue(conflicts.has(OptimizationFlags.OPT_001_FACE_BUCKETS));
    assertTrue(conflicts.has(OptimizationFlags.OPT_132_ALL_ALTITUDE_HORIZON_OCCLUSION));
    assertTrue(conflicts.has(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION));
    assertFalse(conflicts.has(OptimizationFlags.OPT_026_ENTITY_SHADOW_CULL));
    assertFalse(conflicts.has(OptimizationFlags.OPT_116_FAST_MODEL_CUBE_EMIT));
    assertFalse(conflicts.has(OptimizationFlags.OPT_119_RETAINED_HUD_FBO_CACHE));
    assertFalse(conflicts.has(OptimizationFlags.OPT_144_TEXT_DISPLAY_HOLOGRAM_CACHE));
    assertFalse(conflicts.has(OptimizationFlags.OPT_147_CROSS_ERA_STATIC_VRAM_BATCH));

    OptimizationFlags.BitMask resolved = OptimizationFlags.resolve(
        true, "competitive", true, conflicts, Collections.emptySet(), Collections.emptySet(), null);
    assertFalse(resolved.has(OptimizationFlags.OPT_001_FACE_BUCKETS));
    assertFalse(resolved.has(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION));
    assertTrue(resolved.has(OptimizationFlags.OPT_026_ENTITY_SHADOW_CULL));
  }

  @Test
  void systemModeSupportsBaselineOnlyAndLeaveOneOutAblation() {
    OptimizationFlags.BitMask base = OptimizationFlags.resolve(
        true, "all", false, OptimizationFlags.BitMask.EMPTY, null, null, "baseline");
    assertEquals(0, base.cardinality());

    OptimizationFlags.BitMask only = OptimizationFlags.resolve(
        false, "lossless", false, OptimizationFlags.BitMask.EMPTY, null, null, "only:OPT_026,OPT_042");
    assertTrue(only.has(OptimizationFlags.OPT_026_ENTITY_SHADOW_CULL));
    assertTrue(only.has(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE));
    assertTrue(only.has(OptimizationFlags.OPT_110_FRAME_SNAPSHOT_AUTO_COMPAT));
    assertFalse(only.has(OptimizationFlags.OPT_027_ITEM_XP_CLUSTERING));

    OptimizationFlags.BitMask without = OptimizationFlags.resolve(
        true, "all", false, OptimizationFlags.BitMask.EMPTY, null, null, "without:OPT_026");
    assertFalse(without.has(OptimizationFlags.OPT_026_ENTITY_SHADOW_CULL));
    assertTrue(without.has(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE));
  }
}
