#!/usr/bin/env python3
"""Generate the same bounded Dual-Kawase filter in native shader dialects."""
import argparse
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FUNCTION=r'''vec4 ncKawaseSample(sampler2D image, vec2 uv, vec2 step, float up) {
    if(up < 0.5) {
        vec4 value=texture(image,uv)*4.0;
        value+=texture(image,uv+vec2(-step.x,-step.y));
        value+=texture(image,uv+vec2( step.x,-step.y));
        value+=texture(image,uv+vec2(-step.x, step.y));
        value+=texture(image,uv+vec2( step.x, step.y));
        return value/8.0;
    }
    vec4 value=texture(image,uv+vec2(-2.0*step.x,0.0));
    value+=texture(image,uv+vec2( 2.0*step.x,0.0));
    value+=texture(image,uv+vec2(0.0,-2.0*step.y));
    value+=texture(image,uv+vec2(0.0, 2.0*step.y));
    value+=texture(image,uv+vec2(-step.x,-step.y))*2.0;
    value+=texture(image,uv+vec2( step.x,-step.y))*2.0;
    value+=texture(image,uv+vec2(-step.x, step.y))*2.0;
    value+=texture(image,uv+vec2( step.x, step.y))*2.0;
    return value/12.0;
}
vec4 ncKawase(sampler2D image, vec2 uv, vec2 step, float up, float samples) {
    if(samples < 2.0) return ncKawaseSample(image,uv,step,up);
    vec2 texel=1.0/vec2(textureSize(image,0));
    vec4 value=vec4(0.0);
    if(samples < 5.0) {
        // Symmetric 2x2 integration of the source pixel footprint.
        for(int y=0;y<2;y++) for(int x=0;x<2;x++)
            value+=ncKawaseSample(image,uv+(vec2(x,y)-vec2(0.5))*0.5*texel,step,up);
        return value/4.0;
    }
    // Symmetric 3x3 integration. Constant loop bounds keep work bounded on every backend.
    for(int y=0;y<3;y++) for(int x=0;x<3;x++)
        value+=ncKawaseSample(image,uv+(vec2(x,y)-vec2(1.0))/3.0*texel,step,up);
    return value/9.0;
}
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'modules/renderer/build/generated/resources')
    args=parser.parse_args();base=args.output/'assets/nightclient/shaders';(base/'core').mkdir(parents=True,exist_ok=True);(base/'include').mkdir(parents=True,exist_ok=True)
    (base/'include/backdrop.glsl').write_text(FUNCTION)
    for dialect in ['ubo','vulkan','pearl']:
        version='330' if dialect in ('vulkan','pearl') else '150';explicit='layout(location = 0) ' if dialect=='pearl' else '';extension='#extension GL_ARB_separate_shader_objects : enable\n' if dialect=='pearl' else ''
        shader=f'''#version {version}
{extension}uniform sampler2D InSampler;
layout(std140) uniform NightBlurConfig {{ vec2 BlurStep; float Upsample; float KernelSamples; }};
{explicit}in vec2 texCoord;
{explicit}out vec4 fragColor;
{FUNCTION}
// The sampled scene is RGB; per-surface opacity is supplied by GUI vertices.
void main() {{ fragColor=vec4(ncKawase(InSampler,texCoord,BlurStep,Upsample,KernelSamples).rgb,1.0); }}
'''
        (base/f'core/backdrop_{dialect}.fsh').write_text(shader)
    print('Generated bounded integrated Dual-Kawase kernel and three native blur dialects')


if __name__=='__main__':main()
