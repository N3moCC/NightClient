#!/usr/bin/env python3
"""Hitbox geometry always respects world depth across the release matrix."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

ROOT=Path(__file__).resolve().parents[2]

from generate_world_features import generate_world_features


class HitboxDepthContract(unittest.TestCase):
 def test_generated_hitboxes_cannot_render_through_walls(self):
  versions=json.loads((ROOT/'modules/platform/compatibility/world-features.json').read_text())
  for version in versions:
   with tempfile.TemporaryDirectory(prefix='nightclient-hitbox-') as directory:
    generate_world_features(version,Path(directory))
    sources={path.name:path.read_text() for path in Path(directory).rglob('*.java')}
    for name,source in sources.items():
     self.assertNotIn('throughWalls',source,version+' '+name)
     self.assertNotIn('setAlwaysOnTop',source,version+' '+name)
     self.assertNotIn('NO_DEPTH_TEST',source,version+' '+name)
    bridge=sources.get('WorldFeatureBridge.java','')
    if 'HitboxRibbonGizmo.java' in sources:
     self.assertIn('Gizmos.addGizmo(new HitboxRibbonGizmo',bridge,version)
    elif 'HitboxGpuLineType.java' in sources:
     self.assertIn('DepthTestFunction.LEQUAL_DEPTH_TEST',sources['HitboxGpuLineType.java'],version)
    else:
     self.assertIn('LEQUAL_DEPTH_TEST',sources.get('HitboxRenderTypes.java',''),version)
     self.assertIn('HitboxRenderTypes.lines(p.lineWidth)',bridge,version)

if __name__=='__main__':unittest.main()
