#!/usr/bin/env python3
"""Resource/adapter contract: one live GPU kernel, no hover CPU readback path."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
import sys
_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

from generate_viewmodel_outline import FUNCTION,outline_source
from generate_resources import resource_assets

ROOT=Path(__file__).resolve().parents[2]

class ViewmodelOutlineContract(unittest.TestCase):
 def test_shader_resources_use_the_exact_shared_kernel(self):
  base=ROOT/'modules/renderer/src/main/resources/assets/nightclient/shaders'
  self.assertEqual(FUNCTION,(base/'include/viewmodel_outline.glsl').read_text())
  for dialect in ('ubo','vulkan','pearl'):
   fs=(base/('core/viewmodel_outline_'+dialect+'.fsh')).read_text()
   if dialect=='pearl':
    self.assertNotIn('#moj_import',fs)
    self.assertIn(FUNCTION.rstrip(),fs)
   else:
    self.assertIn('#moj_import <nightclient:viewmodel_outline.glsl>',fs)
   self.assertIn('ncHandOutline(texCoord,MouseUV,RadiusPixels)',fs)
 def test_native_pipeline_shader_dependencies_are_mounted(self):
  mounted=set(resource_assets())
  for version in ('1.21.6','1.21.11','26.1','26.2','26.3'):
   source=outline_source(version)
   for stage,extension in (('Vertex','vsh'),('Fragment','fsh')):
    name=re.search(r'with'+stage+r'Shader\(GameBridge.identifier\("nightclient:([^"\n]+)"\)',source)
    self.assertIsNotNone(name,version+' '+stage)
    self.assertIn('shaders/'+name.group(1)+'.'+extension,mounted,version+' '+stage)
 def test_renderpearl_shaderc_can_compile_both_outline_stages(self):
  compiler=shutil.which('glslc')
  if not compiler:self.skipTest('shaderc CLI is unavailable')
  base=ROOT/'modules/renderer/src/main/resources/assets/nightclient/shaders/core'
  vertex=ROOT/'modules/renderer/build/generated/resources/assets/nightclient/shaders/core/fullscreen_pearl.vsh'
  with tempfile.TemporaryDirectory(prefix='nightclient-outline-shader-') as temporary:
   for stage,path in (('frag',base/'viewmodel_outline_pearl.fsh'),('vert',vertex)):
    result=subprocess.run([compiler,'-fshader-stage='+stage,'--target-env=vulkan1.1',
      '-fauto-bind-uniforms','-fauto-map-locations',str(path),'-o',str(Path(temporary)/(stage+'.spv'))],
      capture_output=True,text=True)
    self.assertEqual(0,result.returncode,result.stdout+result.stderr)
 def test_every_backend_uses_bounded_images_without_readback(self):
  for version in ('1.20','1.21.5','1.21.6','1.21.11','26.1','26.2','26.3'):
   source=outline_source(version)
   self.assertNotRegex(source,r'@@[A-Z_]+@@')
   self.assertNotIn('copyTextureToBuffer',source)
   self.assertNotIn('glReadPixels',source)
   self.assertIn('captureMain',source)
   self.assertIn('ready&&"hover".equals(hand)',source)
 def test_live_hover_never_requests_a_cpu_pick(self):
  source=(ROOT/'modules/platform/src/templates/CameraFeatureBridge.java.tpl').read_text()
  method=source.split('public static void hover(float x,float y){',1)[1].split('private static float outlineMouseX',1)[0]
  self.assertNotIn('pickRequested=',method)
  self.assertNotIn('requestPick(',method)
  self.assertIn('final boolean readRequested=pickRequested',source)
  self.assertIn('if(!readRequested)continue;',source)
 def test_contour_has_no_interior_fill_and_excludes_occluding_offhand(self):
  self.assertIn('if(center>0.0001) return 0.0;',FUNCTION)
  self.assertIn('if(!offHand && texture(OffSampler,uv).a>0.0001) return 0.0;',FUNCTION)
  self.assertIn('for(int i=0;i<12;i++)',FUNCTION)
 def test_all_packaged_assets_and_pearl_shaders_are_mounted_and_prewarm_waits_for_overlay(self):
  mounted=set(resource_assets())
  for required in ('shaders/core/rounded_surface_pearl.fsh','shaders/core/fullscreen_pearl.vsh',
                   'shaders/core/saturation_pearl.fsh','shaders/core/backdrop_pearl.fsh',
                   'skins/default.nc','skins/hud.nc','ui/catalog.json'):
   self.assertIn(required,mounted)
  client=(ROOT/'modules/platform/src/main/java/dev/nightclient/platform/NightClientMod.java').read_text()
  self.assertIn('!uiPrewarmed && !GameBridge.isLoadingOverlay()',client)
 def test_legacy_ui_renderer_resume_refreshes_saved_gl_program_and_pipeline_state(self):
  legacy=(ROOT/'modules/platform/src/main/java/dev/nightclient/platform/LegacyUiRenderer.java').read_text()
  self.assertIn('previous = new State(previous);',legacy)
  self.assertIn('GL11.glDepthFunc(depthFunc);',legacy)

if __name__=='__main__':unittest.main()
