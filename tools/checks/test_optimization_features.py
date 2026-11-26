#!/usr/bin/env python3
"""Optimization culling contracts: generated mixins per era and fail-open bridge decisions."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

ROOT = Path(__file__).resolve().parents[2]

from build import generate_optimization_features

OPTIMIZATION = {
    'OptimizationEntityCullMixin',
    'OptimizationBlockEntityCullMixin',
    'OptimizationParticleCullMixin',
    'OptimizationNametagCullMixin',
}
SUBMIT = {
    'OptimizationEntitySubmitCullMixin',
    'OptimizationBlockEntitySubmitCullMixin',
    'OptimizationParticleCullMixin',
    'OptimizationNametagCullMixin',
}


class OptimizationGenerationTests(unittest.TestCase):
    def test_every_supported_release_gets_only_its_era(self):
        versions = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())
        for version in versions:
            with tempfile.TemporaryDirectory(prefix='nightclient-opt-') as directory:
                output = Path(directory)
                generate_optimization_features(version, output)
                mixins = output / 'dev/nightclient/platform/mixin'
                generated = {path.stem for path in mixins.glob('Optimization*.java')}
                key = tuple(map(int, version.split('.')))
                expected = SUBMIT if key >= (1, 21, 9) else OPTIMIZATION
                self.assertEqual(generated, expected, version)
                for path in mixins.glob('Optimization*.java'):
                    source = path.read_text()
                    self.assertIn('package dev.nightclient.platform.mixin;', source, version)
                    self.assertNotIn('@@', source, version + ' ' + path.name)
                    if path.name == 'OptimizationParticleCullMixin.java':
                        if key >= (1, 21, 9):
                            self.assertIn('ParticleGroup', source, version)
                        else:
                            self.assertNotIn('ParticleGroup', source, version)
                    if key >= (1, 21, 9) and 'Submit' in path.name:
                        self.assertIn('state/level/CameraRenderState' if key >= (26, 1) else 'state/CameraRenderState', source, version)
                    if path.name == 'OptimizationEntitySubmitCullMixin.java':
                        self.assertIn('EntityTypes.PLAYER' if key >= (26, 2) else 'EntityType.PLAYER', source, version)
                    elif path.name in ('OptimizationEntityCullMixin.java', 'OptimizationEntitySubmitCullMixin.java', 'OptimizationNametagCullMixin.java'):
                        self.assertIn('entity.level()', source, version)
                        self.assertIn('isCurrentlyGlowing()', source, version)

    def test_legacy_descriptor_eras(self):
        with tempfile.TemporaryDirectory(prefix='nightclient-opt-') as directory:
            output = Path(directory)
            for version in ('1.20', '1.21.1', '1.21.2', '1.21.4', '1.21.8'):
                generate_optimization_features(version, output)
                source = (output / 'dev/nightclient/platform/mixin/OptimizationEntityCullMixin.java').read_text()
                key = tuple(map(int, version.split('.')))
                if key < (1, 21, 2):
                    self.assertIn('Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack', source, version)
                else:
                    self.assertIn('Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack', source, version)
                nametag = (output / 'dev/nightclient/platform/mixin/OptimizationNametagCullMixin.java').read_text()
                self.assertIn('shouldShowName(Lnet/minecraft/world/entity/Entity;', nametag, version)
                self.assertIn(')Z' if key < (1, 21, 2) else ';D)Z', nametag, version)

    def test_reused_output_drops_other_era_classes(self):
        with tempfile.TemporaryDirectory(prefix='nightclient-opt-') as directory:
            output = Path(directory)
            generate_optimization_features('26.3', output)
            self.assertTrue((output / 'dev/nightclient/platform/mixin/OptimizationEntitySubmitCullMixin.java').exists())
            generate_optimization_features('1.20.1', output)
            self.assertFalse((output / 'dev/nightclient/platform/mixin/OptimizationEntitySubmitCullMixin.java').exists())
            self.assertFalse((output / 'dev/nightclient/platform/mixin/OptimizationBlockEntitySubmitCullMixin.java').exists())


STUBS = {
    'dev/nightclient/config/Settings.java': '''package dev.nightclient.config;
public final class Settings {
  public Optimization optimization;
  public static final class Optimization {
    public boolean enabled;
    public String profile = "performance";
    public boolean autoCompatibility = true;
    public boolean undergroundCaveCulling = true;
    public boolean retainedTerrainCache = true;
    public boolean leafCulling = true;
    public boolean fastModelRendering = true;
    public boolean occlusionCulling = true;
    public boolean staticBlockEntityBatching = true;
    public boolean staticEntityBatching = true;
    public boolean hologramTextCache = true;
    public boolean nativeGpuInstancing = true;
    public boolean nativePboUploads = true;
    public boolean nativePacketCodec = true;
    public boolean emptyBlockEntitySkip = true;
    public boolean signTextCulling = true; public int signTextDistance = 28;
    public boolean itemClustering = false; public int itemClusterLimit = 3;
    public boolean nametagCulling = true; public int nametagDistance = 64;
    public boolean entityCulling = true; public int entityDistance = 96;
    public boolean blockEntityCulling = true; public int blockEntityDistance = 64;
    public boolean particleCulling = true; public int particleDistance = 48; public int particleLimit = 4000;
    public boolean fastParticlePhysics = true;
    public boolean hudCache = true;
    public boolean vanillaHudCache = true;
    public boolean scoreboardCache = true;
    public String enabledFlags = "";
    public String disabledFlags = "";
  }
}
''',
    'dev/nightclient/runtime/ClientRuntime.java': '''package dev.nightclient.runtime;
import dev.nightclient.config.Settings;
public final class ClientRuntime {
  public static ClientRuntime current;
  public static boolean explode;
  private final Settings settings;
  public ClientRuntime(Settings settings) { this.settings = settings; }
  public static ClientRuntime get() { if (explode) throw new IllegalStateException("runtime"); return current; }
  public Settings settings() { return settings; }
}
''',
    'BridgeHarness.java': '''import dev.nightclient.config.Settings;
import dev.nightclient.platform.OptimizationBridge;
import dev.nightclient.runtime.ClientRuntime;
public final class BridgeHarness {
  private static int checks;
  private static void check(String name, boolean expected, boolean actual) {
    checks++;
    if (expected != actual) throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
  }
  public static void main(String[] args) {
    Settings settings = new Settings();
    Settings.Optimization optimization = new Settings.Optimization();
    settings.optimization = optimization;
    ClientRuntime.current = new ClientRuntime(settings);
    check("disabled entity", false, OptimizationBridge.cullEntity(1.0e9, 0, true, false, false));
    check("disabled block entity", false, OptimizationBridge.cullBlockEntity(1.0e9, true));
    check("disabled nametag", false, OptimizationBridge.cullNametag(1.0e9, true, false, false));
    check("disabled particles", false, OptimizationBridge.particleCullingActive());
    check("disabled particle admission", false, OptimizationBridge.cullParticle(1.0e9, 99999));
    optimization.enabled = true;
    check("entity beyond", true, OptimizationBridge.cullEntity(97 * 97, 0, true, false, false));
    check("entity at edge", false, OptimizationBridge.cullEntity(96 * 96, 0, true, false, false));
    check("entity local", false, OptimizationBridge.cullEntity(1.0e9, 0, true, true, false));
    check("entity glowing", false, OptimizationBridge.cullEntity(1.0e9, 0, true, false, true));
    check("entity other level", false, OptimizationBridge.cullEntity(1.0e9, 0, false, false, false));
    check("entity preview origin", false, OptimizationBridge.cullEntity(0, 0, true, false, false));
    check("entity preview origin bbox", false, OptimizationBridge.cullEntity(0, 4096, true, false, false));
    check("entity bbox radius", false, OptimizationBridge.cullEntity(96 * 96 + 50, 100, true, false, false));
    check("entity bbox beyond", true, OptimizationBridge.cullEntity(97 * 97 + 50, 100, true, false, false));
    check("entity nms", false, OptimizationBridge.cullEntity(Double.NaN, 0, true, false, false));
    check("block entity beyond", true, OptimizationBridge.cullBlockEntity(65 * 65, true));
    check("block entity edge", false, OptimizationBridge.cullBlockEntity(64 * 64, true));
    check("block entity other level", false, OptimizationBridge.cullBlockEntity(1.0e9, false));
    check("nametag beyond", true, OptimizationBridge.cullNametag(65 * 65, true, false, false));
    check("nametag edge", false, OptimizationBridge.cullNametag(64 * 64, true, false, false));
    check("nametag local", false, OptimizationBridge.cullNametag(1.0e9, true, true, false));
    check("particles active", true, OptimizationBridge.particleCullingActive());
    check("particle beyond", true, OptimizationBridge.cullParticle(49 * 49, 0));
    check("particle edge", false, OptimizationBridge.cullParticle(48 * 48, 0));
    check("particle limit", true, OptimizationBridge.cullParticle(1, 4000));
    check("particle under limit", false, OptimizationBridge.cullParticle(1, 3999));
    optimization.entityDistance = 15;
    check("invalid entity distance", false, OptimizationBridge.cullEntity(1.0e9, 0, true, false, false));
    optimization.entityDistance = 96;
    optimization.blockEntityDistance = 999;
    check("invalid block entity distance", false, OptimizationBridge.cullBlockEntity(1.0e9, true));
    optimization.blockEntityDistance = 64;
    optimization.nametagDistance = 0;
    check("invalid nametag distance", false, OptimizationBridge.cullNametag(1.0e9, true, false, false));
    optimization.nametagDistance = 64;
    optimization.particleDistance = 7;
    check("invalid particle distance", false, OptimizationBridge.particleCullingActive());
    optimization.particleDistance = 48;
    optimization.particleLimit = 99;
    check("invalid particle limit", false, OptimizationBridge.particleCullingActive());
    optimization.particleLimit = 4000;
    // FrameSnapshot user-toggle gating checks
    OptimizationBridge.beginFrame(0, 64, 0, 0, 0, 1);
    check("snapshot occlusion default on", true, OptimizationBridge.opt024Occlusion);
    check("snapshot item clustering off in performance", false, OptimizationBridge.anyItemClusterOpt);
    check("snapshot static BE on", true, OptimizationBridge.isOptActive(dev.nightclient.config.OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE));
    check("snapshot terrain cache on", true, OptimizationBridge.opt132HorizonOcclusion);
    check("snapshot hologram cache on", true, OptimizationBridge.opt144TextDisplayCache);
    check("snapshot vanilla hud cache on", true, OptimizationBridge.opt119RetainedHud);
    check("snapshot static vram batch on", true, OptimizationBridge.opt147StaticVramBatch);
    optimization.occlusionCulling = false;
    optimization.staticBlockEntityBatching = false;
    optimization.retainedTerrainCache = false;
    optimization.hologramTextCache = false;
    optimization.vanillaHudCache = false;
    optimization.profile = "custom";
    optimization.itemClustering = true;
    OptimizationBridge.beginFrame(0, 64, 0, 0, 0, 1);
    check("snapshot occlusion toggled off", false, OptimizationBridge.opt024Occlusion);
    check("snapshot static BE toggled off", false, OptimizationBridge.isOptActive(dev.nightclient.config.OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE));
    check("snapshot static vram split off when BE off", false, OptimizationBridge.opt147StaticVramBatch);
    check("snapshot static entity still on when BE off", true, OptimizationBridge.opt028StaticEntityBake);
    check("snapshot terrain cache toggled off", false, OptimizationBridge.opt132HorizonOcclusion);
    check("snapshot hologram cache toggled off", false, OptimizationBridge.opt144TextDisplayCache);
    check("snapshot vanilla hud cache toggled off", false, OptimizationBridge.opt119RetainedHud);
    check("snapshot item clustering toggled on in custom", true, OptimizationBridge.anyItemClusterOpt);
    OptimizationBridge.resetSnapshot();
    settings.optimization = null;
    check("missing optimization", false, OptimizationBridge.cullEntity(1.0e9, 0, true, false, false));
    check("missing optimization particles", false, OptimizationBridge.particleCullingActive());
    ClientRuntime.explode = true;
    check("throwing runtime", false, OptimizationBridge.cullEntity(1.0e9, 0, true, false, false));
    check("throwing runtime particles", false, OptimizationBridge.particleCullingActive());
    ClientRuntime.current = null;
    ClientRuntime.explode = false;
    check("missing runtime", false, OptimizationBridge.cullNametag(1.0e9, true, false, false));
    System.out.println("bridge checks=" + checks);
  }
}
''',
}


class OptimizationBridgeTests(unittest.TestCase):
    def test_bridge_has_no_game_imports(self):
        source = (ROOT / 'modules/platform/src/main/java/dev/nightclient/platform/OptimizationBridge.java').read_text()
        self.assertNotIn('net.minecraft', source)
        self.assertNotIn('com.mojang', source)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_bridge_decisions_fail_open(self):
        with tempfile.TemporaryDirectory(prefix='nightclient-bridge-') as directory:
            root = Path(directory)
            for name, source in STUBS.items():
                path = root / 'src' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            bridge = root / 'src/dev/nightclient/platform/OptimizationBridge.java'
            bridge.parent.mkdir(parents=True, exist_ok=True)
            bridge.write_text((ROOT / 'modules/platform/src/main/java/dev/nightclient/platform/OptimizationBridge.java').read_text())
            flags = root / 'src/dev/nightclient/config/OptimizationFlags.java'
            flags.parent.mkdir(parents=True, exist_ok=True)
            flags.write_text((ROOT / 'modules/core/src/main/java/dev/nightclient/config/OptimizationFlags.java').read_text())
            policy = root / 'src/dev/nightclient/config/FeaturePolicy.java'
            policy.write_text((ROOT / 'modules/core/src/main/java/dev/nightclient/config/FeaturePolicy.java').read_text())
            opt_dir = ROOT / 'modules/platform/src/main/java/dev/nightclient/platform/opt'
            if opt_dir.is_dir():
                for opt_src in opt_dir.glob('*.java'):
                    dest = root / 'src/dev/nightclient/platform/opt' / opt_src.name
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    dest.write_text(opt_src.read_text())
            classes = root / 'classes'
            sources = [str(path) for path in (root / 'src').rglob('*.java')]
            subprocess.run(['javac', '-d', str(classes), *sources], check=True, capture_output=True, text=True)
            result = subprocess.run(['java', '-cp', str(classes), 'BridgeHarness'], check=True, capture_output=True, text=True)
            self.assertIn('bridge checks=', result.stdout)


if __name__ == '__main__':
    unittest.main()
