#version 150
uniform sampler2D MainSampler;
uniform sampler2D OffSampler;
layout(std140) uniform NightOutlineConfig { vec2 MouseUV; float RadiusPixels; float Reserved; };
in vec2 texCoord;
out vec4 fragColor;
#moj_import <nightclient:viewmodel_outline.glsl>
void main() { fragColor=vec4(1.0,1.0,1.0,ncHandOutline(texCoord,MouseUV,RadiusPixels)); }
