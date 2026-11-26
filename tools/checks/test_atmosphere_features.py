#!/usr/bin/env python3
"""Atmosphere feature contracts across all 35 supported Minecraft releases, shaders, i18n, and NC."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

ROOT = Path(__file__).resolve().parents[2]

from build import generate_atmosphere_features


class AtmosphereFeatureContractTests(unittest.TestCase):
    def test_every_supported_release_generates_clean_bridges_and_mixins(self):
        versions = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())
        expected = [row['minecraft'] for row in json.loads((ROOT / 'modules/platform/compatibility/versions.json').read_text())]
        self.assertEqual(list(versions), expected)
        for version in versions:
            key = tuple(map(int, version.split('.')))
            with tempfile.TemporaryDirectory(prefix='nightclient-atmo-') as directory:
                output = Path(directory)
                generate_atmosphere_features(version, output)
                bridge_dir = output / 'dev/nightclient/platform'
                mixin_dir = bridge_dir / 'mixin'

                world_bridge = (bridge_dir / 'AtmosphereWorldBridge.java').read_text()
                backend = (bridge_dir / 'AtmosphereBackend.java').read_text()
                render_mixin = (mixin_dir / 'AtmosphereRenderMixin.java').read_text()
                time_mixin = (mixin_dir / 'AtmosphereTimeMixin.java').read_text()
                client_level_mixin = (mixin_dir / 'AtmosphereClientLevelMixin.java').read_text()
                clock_mixin = (mixin_dir / 'AtmosphereClockMixin.java').read_text()

                for name, src in [
                    ('AtmosphereWorldBridge', world_bridge),
                    ('AtmosphereBackend', backend),
                    ('AtmosphereRenderMixin', render_mixin),
                    ('AtmosphereTimeMixin', time_mixin),
                    ('AtmosphereClientLevelMixin', client_level_mixin),
                    ('AtmosphereClockMixin', clock_mixin),
                ]:
                    self.assertNotIn('@@', src, f'{version} {name}')
                    self.assertNotIn('throughWalls', src, f'{version} {name}')
                    self.assertNotIn('setAlwaysOnTop', src, f'{version} {name}')

                if key < (1, 21, 6):
                    self.assertIn('GL_UNIFORM_BUFFER', backend, version)
                    self.assertIn('GL_UNPACK_ROW_LENGTH', backend, version)
                    self.assertIn('uploadRgba2D', backend, version)
                    if version == '1.21.5':
                        self.assertIn('GlTexture', backend, version)
                    else:
                        self.assertIn('getDepthTextureId()', backend, version)
                else:
                    self.assertIn('RenderPipeline', backend, version)
                    if key >= (26, 2):
                        self.assertIn('isZZeroToOne() ? 1 : 3', backend, version)
                        self.assertIn('(revDepth == 1 || revDepth == 3) ? pipePrecipGe : pipePrecipLe', backend, version)
                    if key >= (26, 1):
                        self.assertIn('GREATER_THAN_OR_EQUAL', backend, version)
                        self.assertIn('LESS_THAN_OR_EQUAL', backend, version)
                        self.assertIn('Math.abs(projection[11] + 1.0f) > 0.05f', backend, version)
                        self.assertIn('Math.abs(projection[15]) > 0.05f', backend, version)
                        self.assertTrue((mixin_dir / 'AtmosphereProjectionMixin.java').is_file(), version)
                    else:
                        self.assertIn('LEQUAL_DEPTH_TEST', backend, version)
                    if key >= (26, 3):
                        self.assertIn('com.mojang.renderpearl.api.pipeline', backend, version)
                        self.assertIn('_pearl', backend, version)
                    elif key >= (26, 2):
                        self.assertIn('_vulkan', backend, version)
                    else:
                        self.assertIn('_ubo', backend, version)

                if key >= (26, 2):
                    self.assertIn('method = "render(', render_mixin, version)
                else:
                    self.assertIn('method = "renderLevel(', render_mixin, version)

                if key >= (26, 1):
                    self.assertIn('getOverworldClockTime()J', time_mixin, version)
                    self.assertIn('getDefaultClockTime()J', time_mixin, version)
                    if key >= (26, 3):
                        self.assertIn('AttributeTrackSampler', clock_mixin, version)
                        self.assertIn('ClockInstance;totalTicks()J', clock_mixin, version)
                    else:
                        self.assertIn('ClientClockManager', clock_mixin, version)
                        self.assertIn('getTotalTicks', clock_mixin, version)
                else:
                    self.assertIn('getDayTime()J', time_mixin, version)
                    self.assertIn('ClientLevelData', clock_mixin, version)
                    self.assertIn('tickTime()V', client_level_mixin, version)
                self.assertIn('setSkyFlashTime(I)V', client_level_mixin, version)
                self.assertIn('getRainLevel(F)F', time_mixin, version)
                self.assertIn('getThunderLevel(F)F', time_mixin, version)
                self.assertIn('shouldClearWeather', time_mixin, version)

    def test_i18n_catalog_and_nc_sync(self):
        en = json.loads((ROOT / 'modules/core/src/main/resources/assets/nightclient/i18n/en.json').read_text())
        ru = json.loads((ROOT / 'modules/core/src/main/resources/assets/nightclient/i18n/ru.json').read_text())
        self.assertEqual(set(en.keys()), set(ru.keys()))
        self.assertIn('module.atmosphere.label', en)
        self.assertIn('module.customSky.label', en)
        self.assertIn('choice.customSky.style.galaxy', en)
        self.assertIn('choice.customSky.timeMode.day', en)
        self.assertIn('choice.customSky.timeMode.night', en)
        self.assertIn('field.noRender.weather', en)
        self.assertIn('choice.atmosphere.mode.sakura', en)
        self.assertIn('choice.atmosphere.mode.mystic', en)

        catalog = json.loads((ROOT / 'modules/core/src/main/resources/assets/nightclient/ui/catalog.json').read_text())
        ids = [m['id'] for m in catalog['modules']]
        self.assertIn('atmosphere', ids)
        self.assertIn('customSky', ids)
        self.assertNotIn('worldTime', ids)

        bundled_nc = (ROOT / 'modules/core/src/main/resources/assets/nightclient/skins/default.nc').read_bytes()
        theme_nc = (ROOT / 'themes/default/default.nc').read_bytes()
        self.assertEqual(bundled_nc, theme_nc)
        self.assertLess(len(bundled_nc), 256 * 1024)

    def test_generated_atmosphere_shaders_present(self):
        shader_dir = ROOT / 'modules/renderer/build/generated/resources/assets/nightclient/shaders/core'
        if not shader_dir.exists():
            return
        for dialect in ('ubo', 'vulkan', 'pearl'):
            for stage in (
                'dawn',
                'custom_sky',
                'motion_blur',
                'bokeh_extract',
                'bokeh_disc',
                'bokeh_streak',
                'bokeh_composite',
                'mood_dust',
                'mood_snow',
                'mood_sakura',
                'mood_autumn',
                'mood_fireflies',
                'mood_mystic',
            ):
                p = shader_dir / f'atmosphere_{stage}_{dialect}.fsh'
                self.assertTrue(p.is_file(), str(p))
            precip_vsh = shader_dir / f'atmosphere_precip_{dialect}.vsh'
            self.assertTrue(precip_vsh.is_file())
            self.assertIn('#define ReversedDepth', precip_vsh.read_text())
            self.assertTrue((shader_dir / f'atmosphere_precip_{dialect}.fsh').is_file())
            self.assertFalse((shader_dir / f'atmosphere_props_{dialect}.vsh').exists())
            self.assertFalse((shader_dir / f'atmosphere_props_{dialect}.fsh').exists())


if __name__ == '__main__':
    unittest.main()
