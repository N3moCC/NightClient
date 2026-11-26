#!/usr/bin/env python3
"""Freelook hold-to-third-person generation contract across the release matrix."""
import json
import tempfile
import unittest
from pathlib import Path
import sys
_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

from generate_camera_features import generate_camera_features

ROOT=Path(__file__).resolve().parents[2]
BRIDGE=Path('dev/nightclient/platform/CameraFeatureBridge.java')

class FreelookPerspectiveContract(unittest.TestCase):
 def generate(self,version):
  with tempfile.TemporaryDirectory(prefix='nightclient-freelook-') as directory:
   generate_camera_features(version,Path(directory))
   return (Path(directory)/BRIDGE).read_text()
 def test_camera_type_api_is_direct_across_every_supported_release(self):
  versions=json.loads((ROOT/'modules/platform/compatibility/bridges.json').read_text())
  for version in versions:
   source=self.generate(version)
   self.assertNotIn('@@',source,version)
   self.assertIn('import net.minecraft.client.CameraType;',source,version)
   self.assertIn('savedCameraType=mc.options.getCameraType();',source,version)
   self.assertIn('mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);',source,version)
   self.assertIn('if(saved.isFirstPerson()){yawOffset=pitchOffset=0;previousCameraTime=0;}',source,version)
   self.assertIn('if(mc.options.getCameraType()!=saved)mc.options.setCameraType(saved);',source,version)
   self.assertNotIn('!mc.options.getCameraType().isFirstPerson()',source,version)
 def test_hold_transitions_are_edge_triggered_and_restore_all_exits(self):
  for version in ('1.20','1.21.5','26.3'):
   source=self.generate(version)
   poll=source.split('public static void poll(){',1)[1].split('private static void restoreCameraType',1)[0]
   self.assertIn('if(down&&!held){',poll,version)
   self.assertIn('else if(!down&&held)restoreCameraType(mc);',poll,version)
   self.assertIn('if(previousPlayer!=mc.player||previousLevel!=mc.level){restoreCameraType(mc);',poll,version)
   self.assertIn('if(!allowed){restoreCameraType(mc);yawOffset=pitchOffset=0;previousCameraTime=0;}',poll,version)
   close=source.split('public static void close(){',1)[1].split('\n',1)[0]
   self.assertIn('restoreCameraType(Minecraft.getInstance())',close,version)
   self.assertNotIn('options.save',source,version)
 def test_tick_poll_releases_when_no_camera_frame_is_drawn(self):
  client=(ROOT/'modules/platform/src/main/java/dev/nightclient/platform/NightClientMod.java').read_text()
  bridge=(ROOT/'modules/platform/src/templates/GameBridge.java.tpl').read_text()
  self.assertIn('GameBridge.pollZoom();',client)
  poll_zoom=bridge.split('public static void pollZoom(){',1)[1].split('\n',1)[0]
  self.assertIn('CameraFeatureBridge.poll();',poll_zoom)

if __name__=='__main__':unittest.main()
