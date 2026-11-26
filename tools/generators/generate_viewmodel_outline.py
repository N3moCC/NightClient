#!/usr/bin/env python3
"""Bounded live hand contour pass. Hover and alpha classification never leave the GPU."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

FUNCTION=r'''float ncHandAlpha(vec2 uv, bool offHand) {
    if(any(lessThan(uv,vec2(0.0))) || any(greaterThan(uv,vec2(1.0)))) return 0.0;
    float offAlpha=texture(OffSampler,uv).a;
    return offHand ? offAlpha : texture(MainSampler,uv).a*(1.0-step(0.0001,offAlpha));
}
float ncHandOutline(vec2 uv, vec2 pointer, float radius) {
    if(any(lessThan(pointer,vec2(0.0))) || any(greaterThanEqual(pointer,vec2(1.0)))) return 0.0;
    bool offHand=texture(OffSampler,pointer).a>0.0001;
    if(!offHand && texture(MainSampler,pointer).a<=0.0001) return 0.0;

    if(!offHand && texture(OffSampler,uv).a>0.0001) return 0.0;
    float center=ncHandAlpha(uv,offHand);
    if(center>0.0001) return 0.0;
    vec2 texel=1.0/vec2(textureSize(MainSampler,0));
    float core=0.0,halo=0.0;
    for(int i=0;i<12;i++) {
        float angle=float(i)*0.5235987756;
        vec2 direction=vec2(cos(angle),sin(angle))*texel;
        core=max(core,ncHandAlpha(uv+direction*radius,offHand));
        halo+=ncHandAlpha(uv+direction*(radius*3.0),offHand);
    }
    return min(0.98,core*0.90+halo*(0.32/12.0));
}
'''

def outline_source(version):
    v=tuple(map(int,version.split('.')))
    if v<(1,21,6):
        source=(ROOT/'modules/platform/src/templates/LegacyViewmodelOutlineBackend.java.tpl').read_text()
        imports=''
        fbo='target.frameBufferId';texture='target.getColorTextureId()'
        if v==(1,21,5):
            imports='import com.mojang.blaze3d.opengl.GlDevice;\nimport com.mojang.blaze3d.opengl.GlTexture;\n'
            fbo='((GlTexture)target.getColorTexture()).getFbo(((GlDevice)RenderSystem.getDevice()).directStateAccess(),target.getDepthTexture())'
            texture='((GlTexture)target.getColorTexture()).glId()'
        return source.replace('@@SOURCE_IMPORTS@@',imports).replace('@@SOURCE_FBO@@',fbo).replace('@@SOURCE_TEXTURE@@',texture).replace('@@KERNEL@@',json.dumps(FUNCTION))
    pearl=v>=(26,3);layouts=v>=(26,2);color=v>=(26,1);samplers=v>=(1,21,11);vertex=not samplers
    pipeline='com.mojang.renderpearl.api.pipeline' if pearl else 'com.mojang.blaze3d.pipeline'
    tex='com.mojang.renderpearl.api.textures' if pearl else 'com.mojang.blaze3d.textures'
    buf='com.mojang.renderpearl.api.buffers' if pearl else 'com.mojang.blaze3d.buffers'
    cmd='com.mojang.renderpearl.api.commands' if pearl else 'com.mojang.blaze3d.systems'
    uniform=pipeline if pearl else 'com.mojang.blaze3d.shaders'
    imports='\n'.join('import '+p+';' for p in [pipeline+'.RenderPipeline',tex+'.GpuTexture',tex+'.GpuTextureView',tex+'.FilterMode',buf+'.GpuBuffer',cmd+'.CommandEncoder',cmd+'.RenderPass',uniform+'.UniformType'])
    if not samplers:imports+='\nimport '+tex+'.AddressMode;'
    if color:imports+='\nimport '+pipeline+'.ColorTargetState;'
    else:imports+='\nimport com.mojang.blaze3d.platform.DepthTestFunction;'
    if layouts:
        imports+='\nimport '+pipeline+'.BindGroupLayout;\nimport '+(pipeline if pearl else 'com.mojang.blaze3d')+'.PrimitiveTopology;'
        binding='BindGroupLayout.builder()'+('.withUniform("MainSampler",UniformType.COMBINED_IMAGE_SAMPLER).withUniform("OffSampler",UniformType.COMBINED_IMAGE_SAMPLER)' if pearl else '.withSampler("MainSampler").withSampler("OffSampler")')
        builder='.withBindGroupLayout('+binding+'.withUniform("NightOutlineConfig",UniformType.UNIFORM_BUFFER).build())'
    else:
        imports+='\nimport com.mojang.blaze3d.vertex.DefaultVertexFormat;\nimport com.mojang.blaze3d.vertex.VertexFormat;'
        builder='.withSampler("MainSampler").withSampler("OffSampler").withUniform("NightOutlineConfig",UniformType.UNIFORM_BUFFER)'
    builder+=('.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty())' if color else '.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withoutBlend()')+'.withCull(false)'
    builder+='.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)' if layouts else '.withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES)'
    sampler=lambda name,view:('pass.setUniform("'+name+'",'+view+',RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));' if pearl else ('pass.bindTexture("'+name+'",'+view+',RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));' if samplers else 'pass.bindSampler("'+name+'",'+view+');'))
    suffix='pearl' if pearl else ('vulkan' if layouts else 'ubo')
    values={'IMPORTS':imports,'PIPELINE_BUILDER':builder,'SUFFIX':suffix,
      'PREWARM':'RenderSystem.getCompiledPipeline(pipeline());' if pearl else 'RenderSystem.getDevice().precompilePipeline(pipeline());',
      'PASS_CREATE':'encoder.createRenderPass(()->"NightClient live hand outline",outline.view,'+('Optional.empty()' if layouts else 'OptionalInt.empty()')+')',
      'SET_PIPELINE':'pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));' if pearl else 'pass.setPipeline(pipeline);',
      'SAMPLERS':sampler('MainSampler','main.view')+sampler('OffSampler','target.getColorTextureView()'),
      'DRAW':'pass.draw(3,1,0,0);' if layouts else 'pass.draw(0,3);',
      'TEXTURE_SETTINGS':'' if samplers else 'texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);texture.setTextureFilter(FilterMode.LINEAR,false);',
      'MASK_SAMPLER_SETTINGS':'' if samplers else 'target.getColorTexture().setTextureFilter(FilterMode.LINEAR,false);',
      'VERTEX_DECL':'private static GpuBuffer vertex;' if vertex else '',
      'VERTEX_CREATE':'if(vertex==null)vertex=RenderSystem.getDevice().createBuffer(()->"NightClient outline vertex binding",GpuBuffer.USAGE_VERTEX,64);' if vertex else '',
      'VERTEX_BIND':'pass.setVertexBuffer(0,vertex);' if vertex else '',
      'VERTEX_CLOSE':'if(vertex!=null){vertex.close();vertex=null;}' if vertex else ''}
    source=(ROOT/'modules/platform/src/templates/NativeViewmodelOutlineBackend.java.tpl').read_text()
    for k,value in values.items():source=source.replace('@@'+k+'@@',value)
    return source
