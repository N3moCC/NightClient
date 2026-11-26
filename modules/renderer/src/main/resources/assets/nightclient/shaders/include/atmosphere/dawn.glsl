const vec2 POISSON[8]=vec2[8](
vec2(-0.326,-0.406),vec2(-0.840,-0.074),vec2(-0.696,0.457),vec2(-0.203,0.621),
vec2(0.962,-0.195),vec2(0.473,-0.480),vec2(0.519,0.767),vec2(0.185,-0.893)
);
float saturate(float v) {
return clamp(v,0.0,1.0);
}
float luminance(vec3 c) {
return dot(c,vec3(0.2126,0.7152,0.0722));
}
float hash12(vec2 p) {
vec3 p3=fract(vec3(p.xyx)*0.1031);
p3 +=dot(p3,p3.yzx + vec3(33.33));
return fract((p3.x + p3.y)*p3.z);
}
vec3 srgbToLinear(vec3 c) {
return vec3(
c.r <=0.04045?c.r/12.92:pow((c.r + 0.055)/1.055,2.4),
c.g <=0.04045?c.g/12.92:pow((c.g + 0.055)/1.055,2.4),
c.b <=0.04045?c.b/12.92:pow((c.b + 0.055)/1.055,2.4)
);
}
vec3 linearToSrgb(vec3 c) {
c=clamp(c,0.0,1.0);
return vec3(
c.r <=0.0031308?c.r*12.92:1.055*pow(c.r,0.4166666666666667) - 0.055,
c.g <=0.0031308?c.g*12.92:1.055*pow(c.g,0.4166666666666667) - 0.055,
c.b <=0.0031308?c.b*12.92:1.055*pow(c.b,0.4166666666666667) - 0.055
);
}
vec3 linearToOklab(vec3 c) {
float l=0.4122214708*c.r + 0.5363325363*c.g + 0.0514459929*c.b;
float m=0.2119034982*c.r + 0.6806995451*c.g + 0.1073969566*c.b;
float s=0.0883024619*c.r + 0.2817188376*c.g + 0.6299787005*c.b;
float l_=sign(l)*pow(abs(l),0.3333333333333333);
float m_=sign(m)*pow(abs(m),0.3333333333333333);
float s_=sign(s)*pow(abs(s),0.3333333333333333);
return vec3(
0.2104542553*l_ + 0.7936177850*m_ - 0.0040720468*s_,
1.9779984951*l_ - 2.4285922050*m_ + 0.4505937099*s_,
0.0259040371*l_ + 0.7827717662*m_ - 0.8086757660*s_
);
}
vec3 oklabToLinear(vec3 c) {
float l_=c.x + 0.3963377774*c.y + 0.2158037573*c.z;
float m_=c.x - 0.1055613458*c.y - 0.0638541728*c.z;
float s_=c.x - 0.0894841775*c.y - 1.2914855480*c.z;
float l=l_*l_*l_;
float m=m_*m_*m_;
float s=s_*s_*s_;
return vec3(
4.0767416621*l - 3.3077115913*m + 0.2309699292*s,
-1.2684380046*l + 2.6097574011*m - 0.3413193965*s,
-0.0041960863*l - 0.7034186147*m + 1.7076147010*s
);
}
vec3 oklabMix(vec3 a,vec3 b,float t) {
vec3 la=linearToOklab(srgbToLinear(a));
vec3 lb=linearToOklab(srgbToLinear(b));
return linearToSrgb(oklabToLinear(mix(la,lb,saturate(t))));
}
vec4 permute4(vec4 x) {
return mod(((x*34.0) + 1.0)*x,289.0);
}
vec4 taylorInvSqrt4(vec4 r) {
return 1.79284291400159 - 0.85373472095314*r;
}
float snoise(vec3 v) {
const vec2 C=vec2(1.0/6.0,1.0/3.0);
const vec4 D=vec4(0.0,0.5,1.0,2.0);
vec3 i=floor(v + dot(v,C.yyy));
vec3 x0=v - i + dot(i,C.xxx);
vec3 g=step(x0.yzx,x0.xyz);
vec3 l=1.0 - g;
vec3 i1=min(g.xyz,l.zxy);
vec3 i2=max(g.xyz,l.zxy);
vec3 x1=x0 - i1 + C.xxx;
vec3 x2=x0 - i2 + 2.0*C.xxx;
vec3 x3=x0 - 1.0 + 3.0*C.xxx;
i=mod(i,289.0);
vec4 p=permute4(permute4(permute4(
i.z + vec4(0.0,i1.z,i2.z,1.0))
+ i.y + vec4(0.0,i1.y,i2.y,1.0))
+ i.x + vec4(0.0,i1.x,i2.x,1.0));
float n_=1.0/7.0;
vec3 ns=n_*D.wyz - D.xzx;
vec4 j=p - 49.0*floor(p*ns.z*ns.z);
vec4 x_=floor(j*ns.z);
vec4 y_=floor(j - 7.0*x_);
vec4 x=x_*ns.x + ns.yyyy;
vec4 y=y_*ns.x + ns.yyyy;
vec4 h=1.0 - abs(x) - abs(y);
vec4 b0=vec4(x.xy,y.xy);
vec4 b1=vec4(x.zw,y.zw);
vec4 s0=floor(b0)*2.0 + 1.0;
vec4 s1=floor(b1)*2.0 + 1.0;
vec4 sh=-step(h,vec4(0.0));
vec4 a0=b0.xzyw + s0.xzyw*sh.xxyy;
vec4 a1=b1.xzyw + s1.xzyw*sh.zzww;
vec3 p0=vec3(a0.xy,h.x);
vec3 p1=vec3(a0.zw,h.y);
vec3 p2=vec3(a1.xy,h.z);
vec3 p3=vec3(a1.zw,h.w);
vec4 norm=taylorInvSqrt4(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
p0*=norm.x;
p1*=norm.y;
p2*=norm.z;
p3*=norm.w;
vec4 m=max(0.6 - vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0);
m=m*m;
return 42.0*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
}
float fbm(vec3 p) {
float sum=0.6*snoise(p);
sum +=0.3*snoise(p*2.11 + vec3(19.1,7.3,5.2));
return sum;
}
float henyeyGreenstein(float mu,float g) {
float g2=g*g;
return (1.0 - g2)/pow(max(1.0 + g2 - 2.0*g*mu,0.0001),1.5);
}
float godRaysMask(vec2 uv, vec2 sunUv, float jitter) {
    vec2 toSun = sunUv - uv;
    float sunDist = length(toSun);
    vec2 dir = toSun / max(sunDist, 0.0001);
    float marchDist = min(sunDist, 0.72);
    vec2 stepUv = dir * (marchDist / 16.0);
    vec2 pos = uv + stepUv * jitter;
    float sum = 0.0;
    float weight = 1.0;
    float total = 0.0;
    for (int i = 0; i < 16; i++) {
        vec2 s = clamp(pos, vec2(0.001), vec2(0.999));
        sum += (depthIsFar(texture(Sampler1, s).r) ? 1.0 : 0.0) * weight;
        total += weight;
        weight *= 0.925;
        pos += stepUv;
    }
    return sum / max(total, 0.0001);
}
vec3 sampleSceneSoft(vec2 uv,float radiusPx,float rotation,float depth,float viewZ) {
vec3 center=texture(Sampler0,uv).rgb;
if (radiusPx < 0.5) {
return center;
}
vec2 px=vec2(radiusPx)/max(Resolution,vec2(1.0));
float ca=cos(rotation);
float sa=sin(rotation);
mat2 basis=mat2(ca,sa,-sa,ca);
vec3 acc=center*0.2;
float weightSum=0.2;
for (int i=0;i < 8;i++) {
vec2 s=clamp(uv + basis*POISSON[i]*px,vec2(0.001),vec2(0.999));
float weight=0.1*depthMatch(s,depth,viewZ);
acc +=texture(Sampler0,s).rgb*weight;
weightSum+=weight;
}
return acc/weightSum;
}
vec3 atmoSky(vec3 rd,float mu) {
float up=saturate(rd.y);
float below=saturate(-rd.y);
vec3 grad=oklabMix(PalHorizonCool,PalZenith,pow(up,0.56));
float band=pow(1.0 - up,2.8);
float sunNear=pow(saturate(mu*0.5 + 0.5),3.2);
grad=oklabMix(grad,PalHorizonWarm,saturate(sunNear*(0.34 + 0.66*band)));
grad +=PalRay*(henyeyGreenstein(mu,0.68)*0.052 + exp(-max(1.0 - mu,0.0)*18.0)*0.09);
grad=mix(grad,oklabMix(PalFogCool,PalHorizonCool,0.45)*0.94,smoothstep(0.0,0.22,below)*0.82);
return grad;
}
void main() {
vec2 uv=texCoord;
float depth=texture(Sampler1,uv).r;
float validDepth=depthIsFar(depth)?0.0:1.0;
float skyMask=1.0 - validDepth;
vec3 eyeOffset=atmosphereEyeOffset();
vec3 ro=CamPos + eyeOffset;
vec3 rd=atmosphereDirection(uv,eyeOffset);
float sceneDist=skyMask > 0.0?4000.0:length(relativePosFromDepth(uv,depth) - eyeOffset);
float viewZ=1.0/max(unprojectAtmosphere(uv,depth).w,0.000001);
float edgeFade=smoothstep(0.0,7.5,sceneDist);
float fogThickness=FogMaxHeight - FogMinHeight;
float probeDist=min(sceneDist,220.0)*0.5;
if (abs(rd.y) > 0.001) {
float toTop=(FogMaxHeight - ro.y)/rd.y;
if (rd.y < 0.0) probeDist=mix(clamp(toTop,0.0,2.0*probeDist),2.0*probeDist,0.5);
else if (toTop > 0.0) probeDist=min(probeDist,toTop*0.5);
}
float caveFade=smoothstep(FogMinHeight - 40.0,FogMinHeight - 12.0,ro.y);
float densityMod=1.0;
if (caveFade > 0.0001) {
vec3 probe=ro + rd*probeDist;
vec3 drift=vec3(Time*1.05,Time*0.16,Time*0.58);
float macro=fbm(probe*0.045 + drift*0.6);
float sheets=snoise(vec3(probe.x*0.052,probe.y*0.155,probe.z*0.052) + drift*0.35);
vec3 probeNear=ro + rd*min(probeDist*0.45,60.0);
float detail=snoise(probeNear*0.21 - drift*0.5);
float turbulence=macro*0.62 + sheets*0.28 + detail*0.10;
densityMod=mix(0.55,1.50,saturate(turbulence*0.5 + 0.5));
densityMod=mix(densityMod,1.0,smoothstep(120.0,220.0,probeDist));
}
float sigma=FogDensity*0.058*densityMod*caveFade;
float fogPath=heightFogIntegral(ro.y,rd.y,min(sceneDist,mix(4000.0,900.0,validDepth)),FogMinHeight,fogThickness);
float fogAmount=(1.0 - exp(-sigma*fogPath))*mix(1.0,edgeFade,validDepth);
vec3 sun=normalize(SunDir);
float mu=dot(rd,sun);
float lowSun=saturate(1.0 - abs(sun.y)*2.2);
float horizonGlow=pow(saturate(1.0 - abs(rd.y)*1.30),2.3);
vec3 skyCol=atmoSky(rd,mu);
float sunward=pow(saturate(mu*0.5 + 0.5),3.4);
float vert=saturate(rd.y*1.9 + 0.55);
vec3 shadowSide=mix(PalFogCool*0.92,PalFogCool*1.10,vert);
vec3 dawnSide=mix(PalFogWarm,PalHorizonWarm,pow(saturate(mu),2.4));
float bleed=saturate(sunward*(0.30 + 0.70*lowSun));
vec3 fogTint=oklabMix(shadowSide,dawnSide,bleed);
float forward=henyeyGreenstein(mu,0.56)*0.085;
vec3 bloomGlow=PalRay*forward*(0.35 + 0.65*lowSun)*horizonGlow;
fogTint +=bloomGlow*0.85;
float tintLum=luminance(fogTint);
fogTint/=1.0 + max(tintLum - 0.865,0.0)*2.4;
float skyBlend=mix(0.84,1.0,pow(1.0 - saturate(rd.y),2.0));
float blurDrive=max(fogAmount,skyMask*skyBlend*0.45);
float blurRadius=Softness*7.5*blurDrive;
float rotation=hash12(gl_FragCoord.xy)*6.2831853;
vec3 scene=sampleSceneSoft(uv,blurRadius,rotation,depth,viewZ);
vec3 color=mix(scene,skyCol,skyMask*skyBlend);
vec3 veiled=color*mix(vec3(1.0),vec3(0.90,0.94,1.03),fogAmount*0.30);
color=mix(veiled,fogTint,fogAmount);
float rays=0.0;
vec3 sunGlow=vec3(0.0);
float screenRad=length(SunScreen.xy - vec2(0.5));
float rayWeight=SunScreen.z*(1.0 - smoothstep(1.1,2.6,screenRad));
if (GodRays > 0.001 && rayWeight > 0.001) {
rayWeight*=skySourceVisibility(SunScreen.xy,3.0/Resolution);
float jitter=hash12(gl_FragCoord.yx + vec2(17.0,59.0));
float mask=godRaysMask(uv,SunScreen.xy,jitter);
float angular=pow(saturate(mu*0.5 + 0.5),3.0);
rays=mask*angular*rayWeight*GodRays;
rays*=mix(fogAmount,1.0,skyMask);
float dy=rd.y - sun.y;
float angSq=max(2.0*(1.0 - mu),0.0);
float disc=exp(-angSq*2600.0);
float dx2=max(angSq - dy*dy,0.0);
float streak=exp(-(dy*dy*850.0 + dx2*34.0));
float glowVis=rayWeight*GodRays*(0.35 + 0.65*lowSun);
sunGlow=PalRay*(disc*1.05 + streak*0.38)*skyMask*glowVis;
}
vec3 rayGlow=clamp(PalRay*rays*0.85 + sunGlow,vec3(0.0),vec3(1.0));
color=1.0 - (1.0 - clamp(color,vec3(0.0),vec3(1.0)))*(1.0 - rayGlow);
float outLum=luminance(color);
color/=1.0 + max(outLum - 0.985,0.0)*1.6;
color +=vec3((hash12(gl_FragCoord.xy + vec2(311.7,74.3)) - 0.5)*0.0078);
OutColor=vec4(clamp(color,0.0,1.0),1.0);
}
