void main() {
vec2 uv=texCoord;
float texel=1.0/OutSize.x;
vec3 sum=vec3(0.0);
float total=0.0;
float depthSum=0.0;
float depthWeight=0.0;
for (int i=-12;i <=12;++i) {
float w=exp(-abs(float(i))*0.21);
vec4 sampleColor=texture(Sampler0,uv + vec2(float(i)*Step*texel,0.0));
float luma=dot(sampleColor.rgb,vec3(0.2126,0.7152,0.0722))*w;
depthSum+=sampleColor.a*luma;
depthWeight+=luma;
sum+=sampleColor.rgb*w;
total +=w;
}
vec4 extra=texture(Sampler1,uv);
float streakDepth=depthSum/max(depthWeight,0.000001);
vec3 streak=sum/total*Gain*Tint;
float streakLuma=dot(streak,vec3(0.2126,0.7152,0.0722));
float extraLuma=dot(extra.rgb,vec3(0.2126,0.7152,0.0722))*Mix;
float depth=(streakLuma*streakDepth + extraLuma*extra.a)/max(streakLuma + extraLuma,0.000001);
OutColor=vec4(streak + extra.rgb*Mix,depth);
}
