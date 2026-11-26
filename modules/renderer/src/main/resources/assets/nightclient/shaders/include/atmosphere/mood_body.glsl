const int PARTICLE_CELLS=30;
const vec2 POISSON[8]=vec2[8](
vec2(-0.326,-0.406),vec2(-0.840,-0.074),vec2(-0.696,0.457),vec2(-0.203,0.621),
vec2(0.962,-0.195),vec2(0.473,-0.480),vec2(0.519,0.767),vec2(0.185,-0.893)
);
float saturate(float v) { return clamp(v,0.0,1.0);}
vec3 saturate3(vec3 v) { return clamp(v,vec3(0.0),vec3(1.0));}
vec2 groundPatternXZ(vec3 hit) { return mod(hit.xz,512.0); }
float luminance(vec3 c) { return dot(c,vec3(0.2126,0.7152,0.0722));}
float hash12(vec2 p) {
vec3 p3=fract(vec3(p.xyx)*0.1031);
p3 +=dot(p3,p3.yzx + vec3(33.33));
return fract((p3.x + p3.y)*p3.z);
}
float hash13(vec3 p3) {
p3=fract(p3*0.1031);
p3 +=dot(p3,p3.zyx + 31.32);
return fract((p3.x + p3.y)*p3.z);
}
vec3 hash33(vec3 p3) {
p3=fract(p3*vec3(0.1031,0.1030,0.0973));
p3 +=dot(p3,p3.yxz + 33.33);
return fract((p3.xxy + p3.yxx)*p3.zyx);
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
float vnoise(vec2 p) {
vec2 i=floor(p);
vec2 f=p - i;
f=f*f*(3.0 - 2.0*f);
float a=hash12(i);
float b=hash12(i + vec2(1.0,0.0));
float c=hash12(i + vec2(0.0,1.0));
float d=hash12(i + vec2(1.0,1.0));
return mix(mix(a,b,f.x),mix(c,d,f.x),f.y)*2.0 - 1.0;
}
vec3 worldAt(vec2 uv) {
float depth=textureLod(Sampler1,uv,0.0).r;
if (depthIsFar(depth)) {
vec3 eye=atmosphereEyeOffset();
return CamPos + eye + atmosphereDirection(uv,eye)*4000.0;
}
return worldPosFromDepth(uv,depth);
}
float tangentScore(float center,float nearDepth,float farDepth) {
    if (depthIsFar(nearDepth)) return 1.0e6;
    float stepDepth=nearDepth - center;
    float error=depthIsFar(farDepth)?abs(stepDepth):abs(farDepth - nearDepth - stepDepth);
    return abs(stepDepth) + 4.0*error;
}
vec3 surfaceTangent(vec2 uv,vec2 stepUv,float depth,vec3 pos) {
    vec2 edge=0.5/Resolution;
    vec2 plusUv=clamp(uv + stepUv,edge,1.0 - edge);
    vec2 minusUv=clamp(uv - stepUv,edge,1.0 - edge);
    float plus=textureLod(Sampler1,plusUv,0.0).r;
    float minus=textureLod(Sampler1,minusUv,0.0).r;
    float stepSize=length(stepUv)*0.5;
    float plusScore=1.0e6;
    float minusScore=1.0e6;
    if (length(plusUv - uv) > stepSize)
        plusScore=tangentScore(depth,plus,textureLod(Sampler1,clamp(uv + 2.0*stepUv,edge,1.0 - edge),0.0).r);
    if (length(minusUv - uv) > stepSize)
        minusScore=tangentScore(depth,minus,textureLod(Sampler1,clamp(uv - 2.0*stepUv,edge,1.0 - edge),0.0).r);
    if (min(plusScore,minusScore) >= 1.0e6) return vec3(0.0);
    return plusScore <= minusScore?worldPosFromDepth(plusUv,plus) - pos:pos - worldPosFromDepth(minusUv,minus);
}
vec3 surfaceNormal(vec2 uv,vec3 pos,float depth,vec3 rd) {
    vec2 texel=1.0/Resolution;
    vec3 dx=surfaceTangent(uv,vec2(texel.x,0.0),depth,pos);
    vec3 dy=surfaceTangent(uv,vec2(0.0,texel.y),depth,pos);
    vec3 n=cross(dx,dy);
    float len=length(n);
    if (len <= max(0.000001,0.001*length(dx)*length(dy))) return -rd;
    n/=len;
    return dot(n,rd) > 0.0?-n:n;
}
float supportOcclusion(vec3 hit,float footprint);
float ambientOcclusion(vec2 uv,vec3 pos,vec3 normal,float dist) {
float stable=supportOcclusion(pos,dist*PixelWorld);
if (stable >= 0.0) return stable;
float radiusPx=clamp(0.55/(dist*PixelWorld),3.0,26.0);
vec2 px=vec2(radiusPx)/max(Resolution,vec2(1.0));
float occluded=0.0;
for (int i=0;i < 8;i++) {
vec2 s=clamp(uv + POISSON[i]*px,vec2(0.001),vec2(0.999));
vec3 toTap=worldAt(s) - pos;
float reach=length(toTap);
float rise=dot(toTap,normal)/max(reach,0.0001);
occluded +=saturate((rise - 0.15)/0.5)*(1.0 - smoothstep(0.7,1.6,reach));
}
return occluded/8.0;
}
vec3 vnoiseD(vec2 p) {
vec2 i=floor(p);
vec2 f=p - i;
vec2 u=f*f*(3.0 - 2.0*f);
vec2 du=6.0*f*(1.0 - f);
float a=hash12(i);
float b=hash12(i + vec2(1.0,0.0));
float c=hash12(i + vec2(0.0,1.0));
float d=hash12(i + vec2(1.0,1.0));
float k=a - b - c + d;
float v=a + (b - a)*u.x + (c - a)*u.y + k*u.x*u.y;
return vec3(v*2.0 - 1.0,vec2(b - a + k*u.y,c - a + k*u.x)*du*2.0);
}
vec3 driftNoise(vec2 xz) {
vec2 p=xz*2.2;
vec3 a=vnoiseD(p);
vec3 b=vnoiseD(p*2.3 + 7.1);
return vec3(a.x*0.65 + b.x*0.35,(a.yz*0.65 + b.yz*0.45)*2.2);
}
float heightIntegral(vec3 ro,vec3 rd,float dist,float base,float thickness) {
return heightFogIntegral(ro.y,rd.y,dist,base,thickness);
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
        sum += (depthIsFar(textureLod(Sampler1, s, 0.0).r) ? 1.0 : 0.0) * weight;
        total += weight;
        weight *= 0.925;
        pos += stepUv;
    }
    return sum / max(total, 0.0001);
}
vec3 sampleSceneSoft(vec2 uv,float radiusPx,float depth,float viewZ) {
vec3 center=textureLod(Sampler0,uv,0.0).rgb;
if (radiusPx < 0.5) return center;
vec2 px=vec2(radiusPx)/max(Resolution,vec2(1.0));
vec3 acc=center*0.2;
float weightSum=0.2;
for (int i=0;i < 8;i++) {
vec2 s=clamp(uv + POISSON[i]*px,vec2(0.001),vec2(0.999));
float weight=0.1*depthMatch(s,depth,viewZ);
acc +=textureLod(Sampler0,s,0.0).rgb*weight;
weightSum+=weight;
}
return acc/weightSum;
}
const vec3 MOON_DIR=vec3(0.0181,0.3873,0.9218);
const float FUJI_AZ=2.3;
const float FUJI_DIST=6000.0;
const float FUJI_R=3000.0;
const float FUJI_H=1000.0;
const float FUJI_BASE=-60.0;
float fujiHeight(vec2 p, out float groove) {
    float r = length(p) / FUJI_R;
    float cone = FUJI_H * pow(max(1.0 - r, 0.0), 1.82);
    cone = min(cone, FUJI_H * 0.945) - FUJI_H * 0.028 * (1.0 - smoothstep(0.0, 0.042, r));
    float flank = exp(-dot(p - vec2(1180.0, -520.0), p - vec2(1180.0, -520.0)) / (850.0 * 850.0));
    float h = cone + FUJI_H * 0.11 * flank;
    float ang = atan(p.y, p.x);
    float wave = vnoise(vec2(ang * 5.0, r * 7.0)) * 2.2;
    groove = (1.0 - smoothstep(0.0, 0.42, abs(sin(ang * 26.0 + wave))))
        * smoothstep(0.04, 0.22, r) * (1.0 - smoothstep(0.48, 0.88, r));
    h -= FUJI_H * 0.042 * groove;
    h += FUJI_H * 0.065 * (vnoise(p / 460.0) * 0.6 + vnoise(p / 170.0) * 0.4)
        * smoothstep(0.28, 0.78, r) * (1.0 - smoothstep(0.78, 1.0, r));
    return h + FUJI_BASE;
}

vec4 fuji(vec3 rd) {
    float s = length(rd.xz);
    if (s < 0.001 || rd.y > 0.22 || rd.y < -0.14) return vec4(0.0);
    vec2 d2 = rd.xz / s;
    vec2 centre = vec2(cos(FUJI_AZ), sin(FUJI_AZ)) * FUJI_DIST;
    float b = dot(centre, d2);
    float disc = b * b - (dot(centre, centre) - FUJI_R * FUJI_R);
    if (disc <= 0.0) return vec4(0.0);
    float u = max(b - sqrt(disc), 0.0);
    float uEnd = b + sqrt(disc);
    float rise = rd.y / s;
    float envPrev = u;
    for (int i = 0; i < 64; ++i) {
        float r = length(d2 * u - centre) / FUJI_R;
        float bound = u * rise - (FUJI_H * min(pow(max(1.0 - r, 0.0), 1.82) + 0.11, 0.95) + 70.0 + FUJI_BASE);
        if (bound < 1.5 * u * PixelWorld) break;
        envPrev = u;
        u += max(bound * 0.45, 4.0 + u * 0.002);
        if (u > uEnd) return vec4(0.0);
    }
    u = envPrev;
    float groove;
    float best = 1.0e9;
    float bestU = u;
    float prevU = u;
    float prevGap = 1.0e9;
    bool hit = false;
    for (int i = 0; i < 64; ++i) {
        float gap = u * rise - fujiHeight(d2 * u - centre, groove);
        if (gap < 0.0) {
            hit = true;
            u = prevGap < 1.0e8 ? prevU + (u - prevU) * prevGap / max(prevGap - gap, 0.0001) : u;
            break;
        }
        float pixels = gap / max(u * PixelWorld, 0.0001);
        if (pixels < best) {
            best = pixels;
            bestU = u;
        }
        prevU = u;
        prevGap = gap;
        u += max(gap * 0.45, 4.0 + u * 0.002);
        if (u > uEnd) break;
    }
    float horizFade = smoothstep(-0.12, -0.005, rd.y);
    float alpha = (hit ? 1.0 : 1.0 - smoothstep(0.0, 1.5, best)) * horizFade;
    if (alpha <= 0.001) return vec4(0.0);
    if (!hit) u = bestU;
    vec2 p = d2 * u - centre;
    float h = fujiHeight(p, groove);
    float unused;
    const float e = 8.0;
    float hx = fujiHeight(p + vec2(e, 0.0), unused);
    float hz = fujiHeight(p + vec2(0.0, e), unused);
    vec3 n = normalize(vec3(h - hx, e, h - hz));
    float relH = saturate((h - FUJI_BASE) / FUJI_H);
    float line = 0.54 + 0.07 * vnoise(p / 145.0) - groove * 0.14;
    float snow = smoothstep(line, line + 0.045, relH) * smoothstep(0.32, 0.62, n.y);
    vec3 albedo = mix(vec3(0.16, 0.14, 0.22), vec3(0.92, 0.90, 0.98), snow);
    float lit = max(dot(n, MOON_DIR), 0.0);
    vec3 light = vec3(0.78, 0.80, 0.98) * lit * 0.95 + SkyTop * 1.15
        + vec3(0.34, 0.19, 0.28) * (0.6 - 0.4 * n.y);
    vec3 col = albedo * light;
    float alpineVeil = exp(-pow((relH - 0.28) / 0.12, 2.0)) * (0.35 + 0.35 * vnoise(p / 350.0 + vec2(Time * 0.02, 0.0)));
    col = mix(col, mix(SkyHorizon, vec3(0.74, 0.64, 0.82), 0.4), saturate(alpineVeil * 0.45));
    return vec4(mix(col, SkyHorizon * 0.88, 0.16 + 0.42 * (1.0 - relH)), alpha);
}

const float PAGODA_AZ = 2.68;
float nearHills(vec2 d) {
    return 0.002 + 0.026 * smoothstep(0.2, 0.85, 0.5 + 0.5 * (vnoise(d * 7.0 + 41.0) * 0.7 + vnoise(d * 19.0 + 7.0) * 0.3));
}

vec3 sakuraHorizon(vec3 rd, vec3 grad) {
    float s = length(rd.xz);
    float e = rd.y / max(s, 0.001);
    if (e > 0.095 || e < -0.15) return grad;
    vec2 d = rd.xz / max(s, 0.001);
    float pix = 1.5 * PixelWorld;
    float horizFade = smoothstep(-0.14, -0.005, e);

    float farR = 0.011 + 0.036 * pow(0.5 + 0.5 * (vnoise(d * 4.5 + 3.0) * 0.7 + vnoise(d * 11.0 + 11.0) * 0.3), 1.5);
    vec3 farCol = mix(SkyHorizon * 0.82, vec3(0.24, 0.17, 0.32), 0.38);
    grad = mix(grad, farCol, smoothstep(-pix, pix, farR - e) * horizFade * 0.88);

    float midR = 0.006 + 0.024 * pow(0.5 + 0.5 * (vnoise(d * 8.0 + 19.0) * 0.65 + vnoise(d * 21.0 + 2.0) * 0.35), 1.4);
    vec3 midCol = mix(SkyHorizon * 0.62, vec3(0.13, 0.10, 0.20), 0.52);
    grad = mix(grad, midCol, smoothstep(-pix, pix, midR - e) * horizFade * 0.94);

    float nearR = nearHills(d) + 0.0015 * (0.5 + 0.5 * vnoise(d * 150.0));
    vec3 nearCol = mix(SkyHorizon * 0.48, vec3(0.065, 0.048, 0.095), 0.66);
    grad = mix(grad, nearCol, smoothstep(-pix, pix, nearR - e) * horizFade);

    vec2 toward = vec2(cos(PAGODA_AZ), sin(PAGODA_AZ));
    const float H = 0.052;
    float qx = (toward.x * d.y - toward.y * d.x) / H;
    if (dot(d, toward) <= 0.0 || abs(qx) >= 0.62) return grad;

    vec2 gt = vec2(qx - 0.34, (e - nearHills(normalize(toward + vec2(-toward.y, toward.x) * 0.34 * H)) + 0.001) / H);
    if (abs(gt.x) < 0.20 && gt.y > -0.05 && gt.y < 0.32) {
        float ax = abs(gt.x);
        float pillar = step(0.036, ax) * step(ax, 0.052) * step(0.0, gt.y) * step(gt.y, 0.225);
        float archUp = 0.022 * pow(clamp(ax / 0.145, 0.0, 1.0), 2.2);
        float kasagi = step(ax, 0.145) * step(0.215 + archUp, gt.y) * step(gt.y, 0.242 + archUp * 1.15);
        float nuki   = step(ax, 0.105) * step(0.165, gt.y) * step(gt.y, 0.180);
        float gaku   = step(ax, 0.014) * step(0.180, gt.y) * step(gt.y, 0.218);
        float torii = clamp(max(max(pillar, kasagi), max(nuki, gaku)) * horizFade, 0.0, 1.0);
        vec3 toriiCol = mix(vec3(0.06, 0.035, 0.055), SkyHorizon * 0.42, 0.28);
        grad = mix(grad, toriiCol, torii);
    }

    if (abs(qx) < 0.32) {
        vec2 q = vec2(qx, (e - nearHills(toward) + 0.002) / H);
        if (q.y > -0.08 && q.y < 1.08) {
            float x = abs(q.x);
            float body = (x < 0.18 && q.y < 0.0 && q.y > -0.04) ? 1.0 : 0.0;
            float window = 0.0;
            for (int i = 0; i < 5; ++i) {
                float fi = float(i);
                float y0 = fi * 0.148;
                float wb = 0.108 * (1.0 - 0.095 * fi);
                float wr = 0.245 * (1.0 - 0.095 * fi);
                float ry = q.y - y0;
                if (ry >= 0.0 && ry < 0.165) {
                    if (x < wb && ry < 0.098) {
                        body = 1.0;
                        float lit = step(0.35, fract(fi * 0.618 + 0.25));
                        window = max(window, lit * step(x, 0.034) * step(0.022, ry) * step(ry, 0.072));
                    }
                    float t = x / wr;
                    if (t < 1.0) {
                        float soriCurl = 0.018 * t * t * t;
                        float under = 0.095 - 0.030 * t * t + soriCurl;
                        float over  = 0.152 - 0.046 * t + soriCurl * 1.1;
                        if (ry > under - 0.011 * (1.0 - t) && ry < over) body = 1.0;
                    }
                }
            }
            if (q.y > 0.745 && q.y < 1.02) {
                float ring = 0.014 + 0.013 * step(0.45, fract((q.y - 0.78) * 42.0)) * step(q.y, 0.94);
                float jewel = step(length(q - vec2(0.0, 0.99)), 0.018);
                if (x < ring || jewel > 0.5) body = 1.0;
            }
            vec3 tower = mix(vec3(0.045, 0.032, 0.058), SkyHorizon * 0.45, 0.32);
            tower = mix(tower, vec3(1.0, 0.66, 0.34) * (0.82 + 0.18 * sin(Time * 2.1 + q.y * 28.0)), window);
            grad = mix(grad, tower, body * horizFade);
        }
    }
    return grad;
}
vec3 nightStars(vec3 rd,vec3 grad);
vec3 sakuraSky(vec3 rd,vec3 grad,float up) {
grad +=vec3(1.0,0.42,0.62)*pow(1.0 - up,8.0)*0.14*smoothstep(-0.15,0.0,rd.y);
float stars=MoodDetail[0].z;
if (stars > 0.0) grad=mix(grad,nightStars(rd,grad),stars);
float clouds=MoodDetail[0].y;
float moonDot=dot(rd,MOON_DIR);
if (moonDot > 0.99) {
vec3 moonRight=normalize(cross(MOON_DIR,vec3(0.0,1.0,0.0)));
vec3 moonUp=cross(moonRight,MOON_DIR);
vec2 face=vec2(dot(rd,moonRight),dot(rd,moonUp))/0.032;
float r=length(face);
float apart=max(2.0*(1.0 - moonDot),0.0);
float ang=sqrt(apart);
float red=(ang - 0.05)/0.012,green=(ang - 0.085)/0.014;
vec3 corona=vec3(0.70,0.80,1.0)*exp(-ang/0.018)*0.16
+ vec3(1.0,0.55,0.45)*exp(-red*red)*0.08
+ vec3(0.55,0.95,0.70)*exp(-green*green)*0.04;
grad +=corona*(0.4 + 1.6*clouds);
grad +=vec3(1.0,0.82,0.90)*exp(-apart*260.0)*0.22;
if (r < 1.0) {
float disc=1.0 - smoothstep(0.93,1.0,r);
float maria=vnoise(face*2.5 + 3.0)*0.5 + vnoise(face*6.0 + 9.0)*0.25;
grad=mix(grad,vec3(1.0,0.95,0.90)*(0.86 + 0.14*maria),disc);
}
}
if (MoodDetail[0].w > 0.5) {
vec4 volcano=fuji(rd);
grad=mix(grad,volcano.rgb,volcano.a);
}
if (MoodDetail[1].w > 0.5) grad=sakuraHorizon(rd,grad);
if (rd.y > 0.02 && clouds > 0.0) {
vec2 p=rd.xz/(rd.y + 0.25)*0.7 + vec2(Time*0.008,Time*0.003);
float wisp=smoothstep(0.25,0.85,snoise(vec3(p,1.7)))*smoothstep(0.02,0.2,rd.y);
float rim=wisp*(1.0 - wisp)*4.0*pow(saturate(moonDot),60.0);
grad=mix(grad,mix(SkyTop,vec3(0.95,0.62,0.78),0.55),wisp*clouds);
grad +=vec3(0.85,0.88,1.0)*rim*0.5*min(clouds*2.5,1.0);
}
return grad;
}
vec3 nightStars(vec3 rd,vec3 grad) {
if (rd.y <=0.0) return grad;
vec3 sp=rd*220.0;
vec3 starCell=floor(sp);
float star=hash13(starCell);
if (star > 0.9975) {
vec3 at=starCell + 0.5 + (hash33(starCell) - 0.5)*0.6;
float twinkle=0.6 + 0.4*sin(Time*(1.5 + star*40.0) + star*90.0);
grad +=vec3(1.0,0.92,0.96)*(1.0 - smoothstep(0.0,0.35,length(sp - at)))
*twinkle*smoothstep(0.08,0.3,rd.y)*0.9;
}
return grad;
}
vec3 snowSky(vec3 rd,vec3 grad) {
if (rd.y <=0.0) return grad;
vec2 p=rd.xz/(rd.y + 0.18)*1.1 + vec2(Time*0.02,Time*0.007);
float c=snoise(vec3(p,Time*0.03))*0.6 + snoise(vec3(p*2.6,5.0 - Time*0.04))*0.4;
float cloud=smoothstep(-0.35,0.55,c)*smoothstep(0.0,0.12,rd.y);
return mix(grad,grad*vec3(0.80,0.82,0.86),cloud*0.55);
}
const vec2 HALLOW_WIND=vec2(0.8336,0.5524);
float hallowHashUnit(uint h) {
h ^=h >> 16u;
h*=0x7FEB352Du;
h ^=h >> 15u;
h*=0x846CA68Bu;
h ^=h >> 16u;
return float(h >> 8u)*(1.0/16777216.0);
}
float hallowCell(vec3 c,uint salt) {
uvec3 u=uvec3(ivec3(c) + 65536);
return hallowHashUnit((u.x*0x8DA6B343u) ^ (u.y*0xD8163841u) ^ (u.z*0xCB1AB31Fu) ^ (salt*0x9E3779B9u));
}
vec3 hallowCell3(vec3 c,uint salt) {
return vec3(hallowCell(c,salt),hallowCell(c,salt + 1u),hallowCell(c,salt + 2u));
}
float hallowPhase(float mu,float g) {
float g2=g*g;
return (1.0 - g2)/pow(max(1.0 + g2 - 2.0*g*mu,0.0001),1.5);
}
float hallowNoise(vec2 p) {
vec2 i=floor(p);
vec2 f=p - i;
f=f*f*(3.0 - 2.0*f);
uvec2 c=uvec2(ivec2(i) + 65536);
uint x0=c.x*0x8DA6B343u;
uint x1=x0 + 0x8DA6B343u;
uint y0=c.y*0xD8163841u;
uint y1=y0 + 0xD8163841u;
float a=hallowHashUnit(x0 ^ y0);
float b=hallowHashUnit(x1 ^ y0);
float c0=hallowHashUnit(x0 ^ y1);
float d=hallowHashUnit(x1 ^ y1);
return mix(mix(a,b,f.x),mix(c0,d,f.x),f.y)*2.0 - 1.0;
}
const vec4 HALLOW_MARE[10] = vec4[10](
    vec4(-0.34,  0.42, 0.31, 0.23),
    vec4(-0.62,  0.04, 0.19, 0.42),
    vec4( 0.22,  0.48, 0.21, 0.18),
    vec4( 0.48,  0.08, 0.24, 0.21),
    vec4( 0.74,  0.26, 0.10, 0.15),
    vec4( 0.64, -0.24, 0.14, 0.20),
    vec4(-0.18, -0.34, 0.22, 0.14),
    vec4(-0.52, -0.38, 0.11, 0.11),
    vec4( 0.02,  0.18, 0.26, 0.11),
    vec4( 0.04,  0.76, 0.42, 0.08)
);
const float HALLOW_MARE_TONE[10] = float[10](0.70, 0.76, 0.68, 0.67, 0.65, 0.74, 0.79, 0.76, 0.81, 0.84);
const vec2 HALLOW_TYCHO = vec2(-0.16, -0.66);
const vec2 HALLOW_COPERNICUS = vec2(-0.31, 0.14);
const vec2 HALLOW_KEPLER = vec2(-0.58, 0.11);
vec3 hallowMoonColor() {
vec3 c=SkySun/max(luminance(SkySun),0.001)*0.92;
return c*mix(vec3(1.0,0.74,0.48),vec3(1.0),smoothstep(0.0,0.35,normalize(SunDir).y));
}
float hallowFlash(out float bearing) {
float x=MoodDetail[2].x;
bearing=0.0;
if (x < 0.0) return 0.0;
float k=floor(x);
bearing=hallowCell(vec3(k,0.0,0.0),7u)*6.2831853 - 3.14159265;
return min(fract(x)/0.999,1.0);
}
float hallowFlashNear(vec3 rd,float bearing) {
float apart=atan(rd.z,rd.x) - bearing;
apart=mod(apart + 3.14159265,6.2831853) - 3.14159265;
return exp(-apart*apart*6.0);
}
vec4 hallowMoonDisc(vec3 rd,bool fine) {
float radius=MoodDetail[1].x;
if (radius <=0.0) return vec4(0.0);
vec3 m=normalize(SunDir);
if (dot(rd,m) < cos(radius*1.3)) return vec4(0.0);
vec3 right=normalize(cross(m,vec3(0.0,1.0,0.0)));
vec3 up=cross(right,m);
vec2 face=vec2(dot(rd,right),dot(rd,up))/radius;
float r=length(face);
float px=PixelWorld/radius;
float cover=1.0 - smoothstep(1.0 - px,1.0 + px,r);
if (cover <=0.0) return vec4(0.0);
float pixels=1.0/max(px,0.0001);
float ragged=fine?smoothstep(25.0,40.0,pixels):0.0;
float sea=1.0e3;
float tone=0.0;
for (int i=0;i < 10;++i) {
vec4 mare=HALLOW_MARE[i];
float sd=(length((face - mare.xy)/mare.zw) - 1.0)*min(mare.z,mare.w);
sd +=0.045*hallowNoise(face*4.5 + float(i)*7.3);
if (ragged > 0.0) sd +=ragged*0.025*hallowNoise(face*12.0 + float(i)*3.7);
float h=saturate(0.5 + 0.5*(sea - sd)/0.15);
sea=mix(sea,sd,h) - 0.15*h*(1.0 - h);
tone=mix(tone,0.55 + (HALLOW_MARE_TONE[i] - 0.66)*(0.10/0.18),h);
}
float albedo=mix(1.0,tone,1.0 - smoothstep(-0.08,0.06 + px,sea));
albedo*=0.93 + 0.07*hallowNoise(face*4.0 + 17.0);
vec2 toTycho=face - HALLOW_TYCHO;
float tycho=length(toTycho);
albedo +=0.32*exp(-tycho*tycho/0.0016);
vec2 toCopernicus=face - HALLOW_COPERNICUS;
albedo +=0.20*exp(-dot(toCopernicus,toCopernicus)/0.0012);
vec2 toKepler=face - HALLOW_KEPLER;
albedo +=0.14*exp(-dot(toKepler,toKepler)/0.0008);
float rays=fine?smoothstep(30.0,60.0,pixels):0.0;
if (rays > 0.0) {
float a=atan(toTycho.y,toTycho.x);
albedo +=rays*0.10*pow(abs(cos(8.0*a + 0.6)),40.0)*exp(-tycho/0.55)
*smoothstep(0.06,0.15,tycho);
}
float limb=0.9 + 0.1*sqrt(max(1.0 - r*r,0.0));
return vec4(hallowMoonColor()*min(albedo,1.2)*limb,cover);
}
vec3 hallowHalo(float ang,vec3 moonCol,float cloud) {
float halo=MoodDetail[1].y;
if (halo <=0.0 || MoodDetail[1].x <=0.0) return vec3(0.0);
float aureole=(exp(-ang/0.025)*0.25 + exp(-ang/0.12)*0.06)*(1.0 - 0.5*cloud);
float x=(ang - 0.384)/0.010;
vec3 ring=mix(vec3(1.0,0.70,0.52),vec3(0.86,0.92,1.0),smoothstep(-1.2,1.2,x))*exp(-x*x)*0.035;
return (moonCol*aureole + ring*luminance(moonCol))*halo;
}
vec2 hallowCloudUv(vec3 rd) {
return (rd.xz/(rd.y + 0.12)*0.9 + HALLOW_WIND*(Time*0.008*Speed))*2.4;
}
float hallowClouds(vec2 p,int octaves,bool warp,float contrast) {
float lo=mix(0.75,-0.2,MoodDetail[1].w);
float hi=lo + 0.28;
if (warp) p +=vec2(hallowNoise(p*0.35 + 3.1),hallowNoise(p*0.35 + 7.7))*0.9;
float total=octaves <=2?0.81:(octaves==3?1.0022:1.1214);
float scale=0.5*contrast/total;
float rest=total;
float n=0.0;
float amp=0.5;
for (int i=0;i < 4;++i) {
if (i >=octaves) break;
n +=hallowNoise(p)*amp;
rest -=amp;
if (0.5 + (n + rest)*scale <=lo) return 0.0;
if (0.5 + (n - rest)*scale >=hi) return 1.0;
p=mat2(0.8,0.6,-0.6,0.8)*p*2.07 + vec2(1.7,9.2);
amp*=0.62;
}
return smoothstep(lo,hi,0.5 + n*scale);
}
vec3 hallowStars(vec3 rd, float ang) {
    float share = MoodDetail[1].z;
    if (share <= 0.0 || rd.y <= 0.0) return vec3(0.0);
    float seen = smoothstep(0.08, 0.45, ang) * smoothstep(0.0, 0.18, rd.y);
    vec3 col = vec3(0.0);
    vec3 sp = rd * 220.0;
    vec3 cell = floor(sp);
    float star = hallowCell(cell, 31u);
    float rare = 0.0045 * share + 0.012 * share * share;
    if (star > 1.0 - rare && seen > 0.0) {
        vec3 h = hallowCell3(cell, 32u);
        vec3 at = cell + 0.5 + (h - 0.5) * 0.56;
        float u = (star - (1.0 - rare)) / rare;
        float bright = 0.14 + 0.92 * pow(u, 4.6);
        vec3 tint = mix(vec3(1.0, 0.80, 0.64), vec3(0.78, 0.87, 1.0), h.x);
        float twinkle = 1.0 - mix(0.18, 0.58, 1.0 - rd.y) * (0.5 + 0.5 * sin(Time * (6.0 + 12.0 * h.y) + h.z * 60.0));
        float size = max(0.32, 0.85 * 220.0 * PixelWorld);
        vec3 delta = sp - at;
        float dist = length(delta);
        float core = 1.0 - smoothstep(0.0, size, dist);
        float glint = (exp(-abs(delta.x) * 4.5 / size) * exp(-abs(delta.y) * 1.4 / size)
            + exp(-abs(delta.y) * 4.5 / size) * exp(-abs(delta.x) * 1.4 / size)) * smoothstep(0.72, 1.0, u) * 0.55;
        col = tint * (bright * twinkle * (core + glint) * seen * 0.36 / size);
    }
    vec3 planet = normalize(vec3(0.30, 0.45, -0.84));
    float off = max(1.0 - dot(rd, planet), 0.0);
    col += vec3(1.0, 0.92, 0.80) * 0.9 * exp(-off / (PixelWorld * PixelWorld * 0.9)) * min(share * 4.0, 1.0);
    return col;
}
vec3 hallowMeteor(vec3 rd) {
float often=MoodDetail[2].w;
if (often <=0.0 || rd.y < 0.15) return vec3(0.0);
float epoch=floor(Time/23.0);
if (hallowCell(vec3(epoch,0.0,0.0),41u) >=often) return vec3(0.0);
vec3 h=hallowCell3(vec3(epoch,0.0,0.0),42u);
float t=(Time - epoch*23.0 - h.x*22.0)/0.7;
if (t < 0.0 || t > 1.0) return vec3(0.0);
float az=h.y*6.2831853;
float el=0.55 + 0.5*h.z;
vec3 start=vec3(cos(az)*cos(el),sin(el),sin(az)*cos(el));
vec3 down=normalize(vec3(0.0,-1.0,0.0) + start*start.y);
float turn=(hallowCell(vec3(epoch,0.0,0.0),45u) - 0.5)*2.0;
vec3 dir=normalize(down*cos(turn) + cross(start,down)*sin(turn));
vec3 head=normalize(start + dir*(0.28*t));
if (dot(rd,head) < 0.99) return vec3(0.0);
dir=normalize(dir - head*dot(dir,head));
vec3 rel=rd - head*dot(rd,head);
float along=dot(rel,dir);
float across=length(rel - dir*along);
float tail=0.015 + 0.11*sin(3.14159265*t);
float width=max(0.0007,PixelWorld*0.7);
float line=exp(-across*across/(width*width))
*(along < 0.0?sqrt(1.0 - saturate(-along/tail)):exp(-along*along/(width*width)));
return vec3(0.85,0.92,1.0)*(line*sin(3.14159265*t)*2.0);
}
float hallowSegment(vec2 p,vec2 a,vec2 b) {
vec2 pa=p - a;
vec2 ba=b - a;
float h=clamp(dot(pa,ba)/max(dot(ba,ba),1.0e-10),0.0,1.0);
return length(pa - ba*h);
}
vec3 mysticHorizonTone(vec3 rd, vec3 grad) {
    float share = MoodDetail[2].y;
    if (share <= 0.0 || rd.y < -0.08 || rd.y > 0.042) return grad;
    float az = atan(rd.z, rd.x);
    float y = rd.y;
    float pw = PixelWorld;
    float tall = sqrt(share);
    float ridge = (0.0035 + 0.0085 * (hallowNoise(vec2(az * 2.8, 7.7)) * 0.7 + hallowNoise(vec2(az * 10.5, 2.2)) * 0.3 + 0.4)) * tall;
    float hill  = (0.0072 + 0.0092 * hallowNoise(vec2(az * 4.8, 1.3)) + 0.0042 * hallowNoise(vec2(az * 16.0, 5.1))) * tall;
    float far = 1.0 - smoothstep(ridge - pw, ridge + pw, y);
    float near = 1.0 - smoothstep(hill - pw, hill + pw, y);
    if (y < hill + 0.025 && y > hill - 0.005) {
        const float cellW = 0.014;
        float cell = floor(az / cellW);
        float d = 1.0;
        float crown = 0.0;
        for (int k = -1; k <= 1; ++k) {
            float id = cell + float(k);
            vec3 h = hallowCell3(vec3(id, 0.0, 0.0), 51u);
            if (h.x >= 0.58 * share) continue;
            float size = 0.009 + 0.009 * h.z;
            vec2 p = vec2(az - (id + 0.22 + 0.56 * h.y) * cellW, y - hill + 0.002);
            vec2 mid = vec2((h.y - 0.5) * 0.25 * size, size * 0.55);
            vec2 top = vec2((h.z - 0.5) * 0.32 * size, size);
            float tree = min(
                hallowSegment(p, vec2(0.0), mid) - mix(0.00055, 0.00036, saturate(p.y / max(size * 0.55, 0.001))),
                hallowSegment(p, mid, top) - mix(0.00036, 0.00018, saturate((p.y - size * 0.55) / max(size * 0.45, 0.001))));
            for (int b = 0; b < 5; ++b) {
                float fb = float(b);
                vec2 from = mix(mid * 0.72, top, fb / 4.2);
                float side = mod(fb + floor(h.z * 2.0), 2.0) < 0.5 ? -1.0 : 1.0;
                float reach = size * (0.46 - 0.06 * fb) * (0.78 + 0.44 * fract(h.z * 7.0 + fb * 0.37));
                vec2 elbow = from + vec2(side * 0.58, 0.42) * reach;
                vec2 twig  = elbow + vec2(side * 0.85, 0.62 - 0.35 * fract(fb * 0.37)) * (reach * 0.55);
                tree = min(tree, hallowSegment(p, from, elbow) - 0.00022);
                tree = min(tree, hallowSegment(p, elbow, twig) - 0.00014);
            }
            d = min(d, tree);
            vec2 q = (p - top * 0.80) / vec2(size * 0.45, size * 0.34);
            crown = max(crown, (1.0 - smoothstep(0.35, 1.0, length(q))) * 0.36);
        }
        near = max(near, max(1.0 - smoothstep(-0.5 * pw, 0.5 * pw, d), crown));
    }
    vec3 dark = mix(vec3(0.008, 0.010, 0.016), SkyHorizon, 0.2);
    vec3 col = mix(mix(dark, grad, 0.5), dark, near);
    float cover = max(far, near);
    col = mix(col, grad, 1.0 - smoothstep(-0.07, 0.004, y));
    return mix(grad, col, cover);
}
vec3 mysticSky(vec3 rd,vec3 grad) {
vec3 m=normalize(SunDir);
float mu=dot(rd,m);
float ang=acos(clamp(mu,-1.0,1.0));
vec3 moonCol=hallowMoonColor();
float quality=MoodDetail[6].w;
float bearing;
float flash=hallowFlash(bearing);
float flashNear=flash > 0.0?flash*hallowFlashNear(rd,bearing):0.0;
grad +=vec3(0.75,0.8,1.0)*(0.06*flashNear*(1.0 - smoothstep(0.0,0.3,abs(rd.y))));
float cloud=0.0;
if (rd.y > 0.0 && MoodDetail[1].w > 0.0) {
cloud=hallowClouds(hallowCloudUv(rd),2 + int(quality + 0.5),quality > 0.5,smoothstep(0.0,0.1,rd.y))
*smoothstep(0.0,0.05,rd.y);
}
vec3 sky=grad + hallowStars(rd,ang) + hallowMeteor(rd);
vec4 disc=hallowMoonDisc(rd,true);
sky=mix(sky,disc.rgb,disc.a);
sky +=hallowHalo(ang,moonCol,cloud);
if (cloud > 0.0) {
float through=exp(-4.0*cloud);
vec3 body=mix(vec3(0.085,0.093,0.118),vec3(0.06,0.066,0.085),smoothstep(0.5,1.0,cloud));
body +=moonCol*((mu > 0.5?hallowPhase(mu,0.65):0.6)*0.004*(1.0 - 0.6*cloud));
body +=vec3(0.75,0.8,1.0)*(0.9*flashNear*cloud*(1.0 - smoothstep(0.05,0.45,rd.y)));
float edge=4.0*cloud*(1.0 - cloud);
vec3 lining=mu > 0.9?moonCol*(edge*(pow(mu,400.0)*1.1 + pow(mu,40.0)*0.12)):vec3(0.0);
if (ang < 0.06) {
float veil=edge*(1.0 - smoothstep(0.03,0.06,ang));
vec3 rings=0.5 + 0.5*cos(ang/0.012*6.2831853 - vec3(0.0,0.8,1.6));
lining +=moonCol*rings*(veil*0.12*exp(-ang/0.03));
}
sky=sky*through + body*(1.0 - through) + lining;
}
return mysticHorizonTone(rd,sky);
}
vec3 mysticSkyCheap(vec3 rd) {
vec3 m=normalize(SunDir);
float mu=dot(rd,m);
float up=saturate(rd.y);
float sunOn=saturate(luminance(SkySun)*4.0);
vec3 grad=mix(SkyHorizon,SkyTop,pow(up,0.5));
grad=mix(grad,SkySun,sunOn*pow(saturate(mu*0.5 + 0.5),5.0)*pow(1.0 - up,3.0)*0.5);
grad +=hallowHalo(acos(clamp(mu,-1.0,1.0)),hallowMoonColor(),0.0);
vec4 disc=hallowMoonDisc(rd,false);
return mix(grad,disc.rgb,disc.a);
}
vec3 moodSky(vec3 rd,float mu) {
float up=saturate(rd.y);
float sunOn=saturate(luminance(SkySun)*4.0);
vec3 grad=mix(SkyHorizon,SkyTop,pow(up,0.5));
if (sunOn > 0.0) {
float band=pow(1.0 - up,3.0);
float sunNear=pow(saturate(mu*0.5 + 0.5),5.0);
grad=mix(grad,SkySun,sunOn*sunNear*band*0.5);
grad +=SkySun*henyeyGreenstein(mu,0.75)*0.012*sunOn;
float angSq=max(2.0*(1.0 - mu),0.0);
grad +=SkySun*exp(-angSq*3500.0)*1.3*sunOn;
}
grad=mix(grad,SkyHorizon*0.55,saturate(-rd.y)*0.85);
if (Kind==1) return snowSky(rd,grad);
if (Kind==3) return sakuraSky(rd,grad,up);
if (Kind==5) return nightStars(rd,grad);
if (Kind==6) return mysticSky(rd,grad);
return grad;
}
vec3 particles(vec3 ro,vec3 rd,float sceneDist,vec3 sun,float lit,float shaft) {
float cell,reach,radius,density,alpha,jitter;
vec3 wind;
if (Kind==5) {
cell=1.5;reach=24.0;radius=0.035;jitter=0.3;
density=0.06;alpha=1.5;wind=vec3(0.04,0.0,0.03);
} else {
cell=0.36;reach=9.0;radius=0.008;jitter=0.6;
density=0.3;alpha=0.5;wind=vec3(0.06,0.03,0.04);
}
density*=Amount*1.6;
vec3 gust=vec3(sin(Time*0.31)*1.6 + sin(Time*0.83)*0.5,sin(Time*0.47)*0.3,
cos(Time*0.27)*1.2)*0.15;
vec3 shift=wind*Time*Speed + gust*Speed;
vec3 gustDir=vec3(0.0);
float streak=0.0;
if (Kind==0) {
const float w=0.2618;
const float peak=1.8;
gustDir=normalize(vec3(0.83,0.0,0.55));
float s=sin(Time*w);
float blow=s*s*s*s;
float travel=0.375*Time - sin(2.0*w*Time)/(4.0*w) + sin(4.0*w*Time)/(32.0*w);
shift +=gustDir*(peak*travel*Speed);
gustDir -=rd*dot(gustDir,rd);
float across3=length(gustDir);
gustDir/=max(across3,0.001);
streak=peak*blow*Speed*0.01*across3;
}
float forward=pow(saturate(dot(rd,sun)),6.0);
float limit=min(reach,sceneDist + cell);
vec3 origin=(ro - shift)/cell;
vec3 c=floor(origin);
vec3 safeRd=vec3(abs(rd.x) < 0.00001?0.00001:rd.x,abs(rd.y) < 0.00001?0.00001:rd.y,
abs(rd.z) < 0.00001?0.00001:rd.z);
vec3 stepDir=sign(safeRd);
vec3 tDelta=abs(cell/safeRd);
vec3 tMax=((c + max(stepDir,vec3(0.0))) - origin)*cell/safeRd;
vec3 sum=vec3(0.0);
float t=0.0;
for (int i=0;i < PARTICLE_CELLS;++i) {
if (t > limit) break;
float h=hash13(c);
if (h < density) {
vec3 h3=hash33(c + 7.0);
vec3 center=(c + 0.5 + (h3 - 0.5)*jitter)*cell + shift;
if (Kind==5) {
center +=vec3(sin(Time*0.35 + h*7.0),sin(Time*0.5 + h*3.0)*0.5,
cos(Time*0.3 + h*5.0))*(0.2*cell);
} else {
center +=vec3(sin(Time*0.7 + h*6.3),cos(Time*0.5 + h*4.1),
sin(Time*0.6 + h*9.7))*(0.12*cell);
}
float low=Kind==5?(1.0 - smoothstep(GroundY + 3.0,GroundY + 6.0,center.y))
*smoothstep(GroundY - 2.0,GroundY,center.y)
:1.0;
float along=dot(center - ro,rd);
vec3 offset=ro + rd*along - center;
float d=length(offset);
float size=radius*(Kind==0?0.5 + h3.x:1.0);
float r=max(size,1.3*along*PixelWorld);
float bound=Kind==5?6.0*r:r + streak;
if (low > 0.001 && along > 0.05 && along < sceneDist && d < bound) {
float pixels=r/max(along*PixelWorld,0.000001);
if (Kind==0 && streak > 0.0005) {
float run=clamp(dot(offset,gustDir),-streak,streak);
d=length(offset - gustDir*run);
}
float body;
if (Kind==5) {
body=exp(-(d*d)/(r*r))*1.2 + exp(-(d*d)/(r*r*9.0))*0.25;
} else {
float inner=mix(0.35,0.05,smoothstep(4.0,22.0,pixels));
body=1.0 - smoothstep(r*inner,r,d);
body*=mix(1.0,0.5,smoothstep(6.0,26.0,pixels));
if (Kind==0) body*=r/(r + streak*0.7);
}
float fade=1.0 - smoothstep(reach*0.3,reach,along)*0.85;
fade*=smoothstep(0.7,1.6,along)*(1.0 - smoothstep(28.0,50.0,pixels));
float occluded=visibleBeforeSurface(along,sceneDist,0.25);
vec3 shade=ParticleColor;
float bright;
if (Kind==5) {
float pulse=smoothstep(0.2,0.9,sin(Time*(0.7 + 0.6*h3.x) + h*40.0));
shade=mix(ParticleColor,vec3(1.0,0.95,0.45),h3.y*0.5);
bright=pulse*(0.8 + 0.5*h3.z);
} else {
float glint=0.65 + 0.35*sin(Time*(2.0 + 3.0*h3.x) + h*30.0);
bright=lit*glint*(1.0 + 3.0*forward)*shaft;
}
sum +=shade*(body*bright*alpha*fade*occluded*low);
}
}
if (tMax.x < tMax.y && tMax.x < tMax.z) {
t=tMax.x;tMax.x +=tDelta.x;c.x +=stepDir.x;
} else if (tMax.y < tMax.z) {
t=tMax.y;tMax.y +=tDelta.y;c.y +=stepDir.y;
} else {
t=tMax.z;tMax.z +=tDelta.z;c.z +=stepDir.z;
}
}
return sum;
}
vec3 hotaru(vec3 ro,vec3 rd,float sceneDist) {
const float cell=3.0;
const float reach=24.0;
float density=0.24*Amount*1.6;
vec3 gust=vec3(sin(Time*0.31)*1.6 + sin(Time*0.83)*0.5,sin(Time*0.47)*0.3,
cos(Time*0.27)*1.2)*0.15;
vec3 shift=vec3(0.04,0.0,0.03)*Time*Speed + gust*Speed;
float rdy=abs(rd.y) < 0.00001?0.00001:rd.y;
float t0=(GroundY - 2.5 - ro.y)/rdy;
float t1=(GroundY + 6.5 - ro.y)/rdy;
float tIn=max(min(t0,t1),0.0);
float limit=min(min(reach,sceneDist + cell),max(t0,t1));
if (tIn >=limit) return vec3(0.0);
vec3 start=ro + rd*tIn;
vec3 origin=(start - shift)/cell;
vec3 c=floor(origin);
vec3 safeRd=vec3(abs(rd.x) < 0.00001?0.00001:rd.x,rdy,abs(rd.z) < 0.00001?0.00001:rd.z);
vec3 stepDir=sign(safeRd);
vec3 tDelta=abs(cell/safeRd);
vec3 tMax=tIn + ((c + max(stepDir,vec3(0.0))) - origin)*cell/safeRd;
vec3 sum=vec3(0.0);
float t=tIn;
for (int i=0;i < PARTICLE_CELLS;++i) {
if (t > limit) break;
float h=hash13(c);
if (h < density) {
vec3 h3=hash33(c + 7.0);
vec3 center=(c + 0.5 + (h3 - 0.5)*0.3)*cell + shift
+ vec3(sin(Time*0.35 + h*7.0),sin(Time*0.5 + h*3.0)*0.5,
cos(Time*0.3 + h*5.0))*(0.1*cell);
float low=(1.0 - smoothstep(GroundY + 3.0,GroundY + 6.0,center.y))
*smoothstep(GroundY - 2.0,GroundY,center.y);
float along=dot(center - ro,rd);
float d=length(ro + rd*along - center);
float r=max(0.035,1.3*along*PixelWorld);
if (low > 0.001 && along > 0.05 && along < sceneDist && d < 6.0*r) {
float pixels=r/max(along*PixelWorld,0.000001);
float body=exp(-(d*d)/(r*r))*1.2 + exp(-(d*d)/(r*r*9.0))*0.25;
float fade=(1.0 - smoothstep(reach*0.3,reach,along)*0.85)
*smoothstep(0.7,1.6,along)*(1.0 - smoothstep(28.0,50.0,pixels));
float occluded=visibleBeforeSurface(along,sceneDist,0.25);
float pulse=smoothstep(0.2,0.9,sin(Time*(0.7 + 0.6*h3.x) + h*40.0));
vec3 shade=mix(vec3(0.75,1.0,0.45),vec3(1.0,0.95,0.45),h3.y*0.5);
sum +=shade*(body*pulse*(0.8 + 0.5*h3.z)*1.5*fade*occluded*low);
}
}
if (tMax.x < tMax.y && tMax.x < tMax.z) {
t=tMax.x;tMax.x +=tDelta.x;c.x +=stepDir.x;
} else if (tMax.y < tMax.z) {
t=tMax.y;tMax.y +=tDelta.y;c.y +=stepDir.y;
} else {
t=tMax.z;tMax.z +=tDelta.z;c.z +=stepDir.z;
}
}
return sum;
}
float lensSpecks(vec2 p,float fill) {
vec2 id=floor(p);
vec3 h=hash33(vec3(id,5.0));
if (h.x > fill) return 0.0;
float r=0.12 + 0.24*hash12(id + 11.0);
vec2 at=0.5 + (h.yz - 0.5)*(1.0 - 2.0*r);
float d=length(p - id - at)/r;
return smoothstep(1.0,0.75,d)*(0.45 + 0.55*smoothstep(0.45,0.9,d));
}
const float GHOST_AT[4]=float[4](0.55,0.2,-0.3,-0.7);
const float GHOST_R[4]=float[4](0.045,0.08,0.035,0.11);
const float GHOST_GAIN[4]=float[4](0.35,0.15,0.28,0.12);
const vec3 GHOST_TINT[4]=vec3[4](
vec3(1.0,0.8,0.55),vec3(0.7,0.95,0.75),vec3(1.0,0.7,0.8),vec3(0.65,0.8,1.0)
);
vec3 sampleScene(vec2 uv,float softPx,float depth,float viewZ) {
vec3 c=sampleSceneSoft(uv,softPx,depth,viewZ);
if (Chroma <=0.0) return c;
vec2 offset=uv - 0.5;
vec2 pull=offset*(0.012*Chroma*dot(offset,offset));
vec2 red=clamp(uv + pull,vec2(0.001),vec2(0.999));
vec2 blue=clamp(uv - pull,vec2(0.001),vec2(0.999));
c.r=mix(c.r,textureLod(Sampler0,red,0.0).r,depthMatch(red,depth,viewZ));
c.b=mix(c.b,textureLod(Sampler0,blue,0.0).b,depthMatch(blue,depth,viewZ));
return c;
}
vec3 gradeColor(vec3 c) {
c*=GradeParams.x;
float l=luminance(c);
c=mix(vec3(l),c,GradeParams.z);
c=max((c - 0.5)*GradeParams.y + 0.5,0.0);
l=luminance(c);
vec3 tint=mix(ShadowTint,LightTint,smoothstep(0.08,0.85,l));
c*=tint/max(luminance(tint),0.05);
c +=ShadowTint*GradeParams.w*(1.0 - smoothstep(0.0,0.5,l));
return c;
}
vec4 mapAt(vec3 at) {
if (HasMap==0) return vec4(0.0);
ivec2 c=ivec2(floor(at.xz - CamPos.xz - MapOrigin.xz));
if (c.x < 0 || c.y < 0 || c.x >=64 || c.y >=64) return vec4(0.0);
return ncColumnAt(Sampler3,c);
}
vec3 renderedBlockProbe(vec3 hit) {
    vec3 eye=CamPos + atmosphereEyeOffset();
    vec3 boundary=round(hit);
    vec3 gap=abs(hit - boundary);
    int axis=gap.x < gap.y?(gap.x < gap.z?0:2):(gap.y < gap.z?1:2);
    float tolerance=clamp(length(hit - eye)*0.00007,0.0004,0.006);
    if (gap[axis] < tolerance) hit[axis]=boundary[axis] + sign(hit[axis] - eye[axis])*0.0015;
    return hit;
}
ivec2 columnCell(vec2 xz) {
    return ivec2(floor(xz - CamPos.xz - MapOrigin.xz));
}
bool mapCanopyContains(vec3 hit) {
    if (HasMap == 0) return false;
    vec3 probe=renderedBlockProbe(hit);
    ivec2 cell=columnCell(probe.xz);
    vec4 column=ncColumnAt(Sampler3,cell);
    int layer=int(column.r - 1.0 - floor(probe.y));
    return column.a > 0.5 && ncColumnHasLeaf(Sampler3,cell,layer);
}
bool mapHasColumn(vec3 hit) {
    return mapAt(renderedBlockProbe(hit)).a > 0.5;
}
float surfaceMapFade(vec3 hit) {
    vec2 distance=abs(hit.xz - CamPos.xz);
    return 1.0 - smoothstep(25.0,30.5,max(distance.x,distance.y));
}
float supportPlaneWeight(vec3 hit,ivec2 cell,vec4 column,float footprint) {
    if (HasMap == 0 || column.a < 0.5 || !ncGroundCoverAllowed(Sampler3,cell)
        || hit.y >= CamPos.y + atmosphereEyeOffset().y) return 0.0;
    float range=clamp(0.004 + footprint*0.24,0.004,0.038);
    return 1.0 - smoothstep(range*0.5,range,abs(hit.y - column.g));
}
vec3 exactSupportHit(vec3 hit,vec3 origin,vec3 ray,ivec2 cell,vec4 column) {
    if (ray.y >= -0.0001) return hit;
    vec2 xz=origin.xz + ray.xz*((column.g - origin.y)/ray.y);
    ivec2 exactCell=columnCell(xz);
    if (exactCell != cell) {
        vec4 neighbor=ncColumnAt(Sampler3,exactCell);
        if (neighbor.a < 0.5 || !ncGroundCoverAllowed(Sampler3,exactCell)
            || abs(neighbor.g - column.g) >= 1.0/64.0) return hit;
    }
    return vec3(xz.x,column.g,xz.y);
}
float supportEdgeWeight(ivec2 cell,float y,float distance,float width) {
    if (distance >= width) return 1.0;
    vec4 neighbor=ncColumnAt(Sampler3,cell);
    bool continuous=neighbor.a > 0.5 && ncGroundCoverAllowed(Sampler3,cell)
        && abs(neighbor.g - y) < 1.0/32.0;
    return continuous?1.0:smoothstep(0.0,width,distance);
}
float groundCoverSurface(vec3 hit,float footprint) {
    vec2 coord=hit.xz - CamPos.xz - MapOrigin.xz;
    ivec2 cell=ivec2(floor(coord));
    vec4 column=ncColumnAt(Sampler3,cell);
    float plane=supportPlaneWeight(hit,cell,column,footprint);
    if (plane <= 0.0) return 0.0;
    vec2 within=fract(coord);
    vec2 edge=min(within,1.0 - within);
    ivec2 side=ivec2(step(vec2(0.5),within))*2 - 1;
    float width=0.055;
    float perimeter=supportEdgeWeight(cell + ivec2(side.x,0),column.g,edge.x,width)
        * supportEdgeWeight(cell + ivec2(0,side.y),column.g,edge.y,width)
        * supportEdgeWeight(cell + side,column.g,max(edge.x,edge.y),width);
    return plane*perimeter*surfaceMapFade(hit);
}
float supportBlocker(ivec2 cell,float height,float distance) {
    if (distance >= 0.78) return 0.0;
    vec4 column=ncColumnAt(Sampler3,cell);
    if (column.a < 0.5 || column.b == 2.0) return 0.0;
    float top=column.b == 3.0?column.g:column.r;
    return smoothstep(0.06,0.65,top - height)*(1.0 - smoothstep(0.0,0.78,distance));
}
float supportOcclusion(vec3 hit,float footprint) {
    vec2 coord=hit.xz - CamPos.xz - MapOrigin.xz;
    ivec2 cell=ivec2(floor(coord));
    vec4 column=ncColumnAt(Sampler3,cell);
    if (supportPlaneWeight(hit,cell,column,footprint) < 0.5) return -1.0;
    vec2 local=fract(coord) - 0.5;
    float sum=0.0;
    for (int z=-1;z<=1;++z) {
        for (int x=-1;x<=1;++x) {
            if (x==0 && z==0) continue;
            ivec2 neighbor=ivec2(x,z);
            float distance=length(max(abs(local - vec2(neighbor)) - 0.5,0.0));
            sum+=supportBlocker(cell + neighbor,column.g,distance);
        }
    }
    return min(0.4,sum*0.19)*surfaceMapFade(hit);
}
float foliageGreenScore(vec3 c, bool relaxCanopy) {
float peak = max(c.r, max(c.g, c.b));
float trough = min(c.r, min(c.g, c.b));
float sat = (peak - trough) / max(peak, 0.001);
vec3 hue = c / max(peak, 0.02);
float baseGreen = smoothstep(-0.008, 0.050, hue.g - max(hue.r * 0.96, hue.b * 0.92))
    * smoothstep(0.06, 0.20, sat)
    * smoothstep(0.002, 0.009, peak);
if (!relaxCanopy) return baseGreen;
float notBrownLog = 1.0 - smoothstep(1.10, 1.24, c.r / max(c.g, 0.005)) * smoothstep(1.15, 1.35, c.g / max(c.b, 0.005));
float notSnowOrStone = smoothstep(0.035, 0.12, sat) * (1.0 - smoothstep(0.55, 0.78, luminance(c)) * (1.0 - smoothstep(0.06, 0.14, sat)));
return max(baseGreen, notBrownLog * notSnowOrStone * step(c.b * 0.78, c.g) * step(c.r * 0.86, c.g));
}
float openSky(vec3 at,float y) {
vec4 column=mapAt(at);
if (column.a < 0.5 || y > column.r - 0.6) return 1.0;
bool canopy=abs(column.b - 3.0) < 0.5 && y > column.g - 0.6;
return canopy?0.35:0.0;
}
float greenness(vec3 c) {
return foliageGreenScore(c, false);
}
const vec3 BLOSSOM=vec3(0.965,0.765,0.835);
vec3 blossomTint() { return 1.0 + MoodDetail[1].xyz;}
float pinkness(vec3 c) {
float top=max(c.r,0.001);
return smoothstep(0.12,0.22,(c.r - c.g)/top)*smoothstep(0.03,0.09,(c.b - c.g)/top)
*step(c.b,c.r)*smoothstep(0.004,0.016,c.r);
}
float blossomLeaf(vec3 c, vec3 hit, out float pink) {
    pink = 0.0;
    if (!mapCanopyContains(hit)) return 0.0;
    pink = pinkness(c);
    float leaf = max(pink, foliageGreenScore(c, true));
    return leaf * surfaceMapFade(hit);
}
vec4 blossomFlower(vec2 q,float fill,float pixel,vec3 tint) {
    vec2 cell = floor(q);
    vec3 pattern = hash33(vec3(cell, 9.0));
    float present = smoothstep(pattern.x, pattern.x + 0.10, fill);
    if (present <= 0.0) return vec4(0.0);

    vec3 variation = hash33(vec3(cell, 17.0));
    bool whole = variation.y < 0.28;
    float size = mix(0.22, 0.32, variation.x) * (whole ? 1.0 : 0.90);
    vec2 local = (q - cell - 0.50 - (variation.yz - 0.5) * 0.16) / size;
    if (dot(local, local) > 1.44) return vec4(0.0);
    float angle = pattern.y * 6.2831853;
    vec2 turned = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * local;
    vec4 art = whole ? cherryBlossom(turned, pattern.z, pixel / size, tint)
        : cherryPetal(turned, pattern.z, pixel / size, tint);
    art.a *= present;
    return art;
}

vec3 voxelFaceNormal(vec3 hit,vec3 normal,vec3 rd) {
    vec3 gap=abs(hit - round(hit));
    vec3 score=gap + 0.001*(1.0 - abs(normal));
    int axis=score.x < score.y?(score.x < score.z?0:2):(score.y < score.z?1:2);
    if (gap[axis] > 0.006) return normal;
    vec3 face=vec3(0.0);
    face[axis]=rd[axis] > 0.0?-1.0:1.0;
    return face;
}
vec3 sakuraFoliage(vec3 c,vec3 material,vec3 hit,vec3 normal,vec3 rd,float dist) {
    float pink;
    float leaf = blossomLeaf(material,hit,pink);
    if (leaf <= 0.0) return c;

    float bloom = MoodDetail[0].x;
    vec3 tint = blossomTint();
    float light = luminance(c);
    vec3 leafyRose = mix(vec3(0.61, 0.33, 0.45), vec3(0.94, 0.70, 0.76), smoothstep(0.025, 0.50, light)) * tint;
    leafyRose *= light * 1.19 / max(luminance(leafyRose), 0.05);
    vec3 canopy = mix(c, leafyRose, mix(0.79, 0.26, pink) * leaf);
    if (bloom <= 0.0) return canopy;

    normal=voxelFaceNormal(hit,normal,rd);
    vec3 face = abs(normal);
    bool horizontal = face.y >= max(face.x, face.z);
    vec2 surface = horizontal ? hit.xz : (face.x >= face.z ? hit.zy : hit.xy);
    vec3 block = mod(floor(renderedBlockProbe(hit)),256.0);
    float cluster = smoothstep(0.27, 0.78, 0.5 + 0.5 * vnoise(mod(surface,256.0) * 2.1 + block.y * 0.7));
    float fill = bloom * mix(0.18, 0.78, cluster);
    float pixel = dist * PixelWorld / max(abs(dot(normal, rd)), 0.22);
    float detail = 1.0 - smoothstep(0.012, 0.026, pixel);
    float petalLight = clamp(0.10 + light * 1.65, 0.10, 1.10);
    vec3 petal = BLOSSOM * tint * petalLight;
    vec3 soft = mix(canopy, petal, fill * 0.15 * leaf);
    if (detail <= 0.0) return soft;

    float layer = horizontal ? block.y : (face.x >= face.z ? block.x : block.z);
    vec2 q = mod(surface, 256.0) * 8.0 + vec2(layer * 0.41, layer * 0.63);
    vec4 art = blossomFlower(q, fill, pixel * 8.0, tint);
    vec3 close = mix(canopy, art.rgb * petalLight, 0.73 * leaf * art.a);
    return mix(soft, close, detail);
}
vec3 screenBlend(vec3 base,vec3 light) {
return 1.0 - (1.0 - saturate3(base))*(1.0 - saturate3(light));
}
float hazeThickness() {
if (Kind==5 || Kind==6) return 10.0;
if (Kind==3) return 16.0;
return 26.0;
}
float hallowRampG(float s) { return s <=0.0?0.0:(s >=1.0?s - 0.5:0.5*s*s);}
float hallowFogRamp(float roY,float rdY,float t,float top,float w) {
float a=(top - roY)/w + 0.5;
float b=-rdY/w;
if (abs(b) < 1.0e-4) return clamp(a,0.0,1.0)*t;
return (hallowRampG(a + b*t) - hallowRampG(a))/b;
}
float hallowFogPatch(vec2 xz) {
vec2 q=xz - HALLOW_WIND*(Time*0.3*Speed);
vec2 slow=xz - HALLOW_WIND*(Time*0.15*Speed);
vec2 warp=vec2(0.75,-0.55)*sin(dot(slow,vec2(0.021,0.017)) + 1.3*sin(dot(slow,vec2(-0.013,0.025)) + 0.7));
float n=hallowNoise(q*0.07 + warp);
n=MoodDetail[6].w > 1.5?n*0.65 + hallowNoise(q*0.19 + warp*0.5)*0.35:n*0.85;
return saturate(0.5 + 0.5*n*1.3);
}
vec4 mysticMist(vec3 ro,vec3 rd,float dist,vec3 hazeTint) {
float amount=MoodDetail[3].x;
if (amount <=0.0) return vec4(hazeTint,0.0);
float height=MoodDetail[3].y;
float floorY=MoodDetail[3].z;
float tendrils=MoodDetail[3].w;
float topMax=floorY + 2.0*height;
float t=min(dist,160.0);
if (ro.y > topMax && rd.y >=-0.002) return vec4(hazeTint,0.0);
float enter=ro.y > topMax?(topMax - ro.y)/rd.y:0.0;
if (enter >=t) return vec4(hazeTint,0.0);
float leave=rd.y > 0.0?min((topMax - ro.y)/rd.y,t):t;
float n=0.5;
if (tendrils > 0.0) {
n=hallowFogPatch((ro + rd*leave).xz);
if (MoodDetail[6].w > 1.5) n=0.5*(n + hallowFogPatch((ro + rd*enter).xz));
}
float top=floorY + height*mix(1.0,0.3 + 1.4*n,tendrils);
float thick=amount*mix(1.0,0.25 + 1.5*n,tendrils);
float depth=hallowFogRamp(ro.y,rd.y,t,top,0.6*height);
float indoor=MoodDetail[4].w*(1.0 - smoothstep(4.0,10.0,dist));
float alpha=(1.0 - exp(-0.25*thick*depth))*(1.0 - indoor);
if (alpha <=0.0) return vec4(hazeTint,0.0);
float mu=dot(rd,normalize(SunDir));
float level=MoodDetail[5].w > 0.0?MoodDetail[5].w:0.3;
float lit=level*(2.0 + 2.0*min(MoodDetail[0].x,1.5));
vec3 tint=HazeColor*(lit*(0.9 + 0.2*saturate(rd.y))) + SkySun*(hallowPhase(mu,0.6)*0.05);
float above=smoothstep(top - 0.5,top + 0.5,ro.y)*tendrils;
tint*=mix(1.0,mix(0.8,1.15,n),above);
float bearing;
float flash=hallowFlash(bearing);
if (flash > 0.0) tint +=vec3(0.75,0.8,1.0)*(flash*0.25*hallowFlashNear(rd,bearing));
return vec4(tint,alpha);
}
float sakuraMist(vec3 ro,vec3 rd,float dist) {
float thick=MoodDetail[2].w;
if (thick <=0.0) return 0.0;
float integ=heightIntegral(ro,rd,min(dist,160.0),GroundY - 1.2,3.0);
vec3 probe=ro + rd*clamp(dist*0.5,2.0,40.0);
float banks=0.5 + 0.5*vnoise(probe.xz*0.09 + vec2(Time*0.025,Time*0.01));
return 1.0 - exp(-0.05*thick*banks*integ);
}
float waterAt(vec3 hit,vec3 normal,vec3 material,float dist,float groundPlane) {
    if (SnowCover <= 0.0 || normal.y <= 0.9 || groundPlane >= 0.5) return 0.0;
    vec4 column=mapAt(hit);
    if (column.b == 6.0 || (column.b == 3.0 && mapCanopyContains(hit))) return 0.0;
    float still=(1.0 - smoothstep(0.003,0.006,abs(fract(hit.y) - 0.88789)))
        *(1.0 - smoothstep(40.0,56.0,dist));
    float blue=0.0;
    if (column.b == 2.0 || column.a < 0.5)
        blue=smoothstep(0.06,0.16,material.b - max(material.r,material.g*0.92));
    return max(still,blue);
}
vec4 lyingPetal(vec2 p,float cells,float fill,float px,vec3 tint) {
    vec2 q = p * cells;
    vec2 first = floor(q - 0.5);
    vec4 laid = vec4(0.0);
    for (int y = 0; y < 2; ++y) {
        for (int x = 0; x < 2; ++x) {
            vec2 cell = first + vec2(x, y);
            vec3 h = hash33(vec3(mod(cell, 256.0 * cells), 31.0));
            float present = smoothstep(h.x, h.x + 0.09, fill);
            if (present <= 0.0) continue;
            float size = mix(0.27, 0.40, h.z);
            vec2 center = cell + 0.5 + (fract(h.yz * 7.31) - 0.5) * 0.23;
            vec2 local = (q - center) / size;
            if (dot(local, local) > 1.44) continue;
            float angle = h.y * 6.2831853;
            vec2 turned = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * local;
            vec4 petal = cherryPetal(turned, h.z, px * cells / size, tint);
            petal.a *= present * 0.92;
            petal.rgb = mix(petal.rgb, petal.rgb * vec3(0.76, 0.73, 0.62), smoothstep(0.83, 1.0, h.z));
            laid.rgb = mix(laid.rgb, petal.rgb, petal.a);
            laid.a = petal.a + laid.a * (1.0 - petal.a);
        }
    }
    return vec4(laid.rgb / max(laid.a, 0.001), laid.a);
}
void sakuraSurface(inout vec3 scene,inout float sceneLum,vec3 material,vec3 hit,vec3 normal,vec3 rd,
vec3 sun,float dist,float water,float groundPixel) {
scene=sakuraFoliage(scene,material,hit,normal,rd,dist);
if (water <=0.0) return;
float mirror=MoodDetail[2].z;
if (mirror > 0.0) {
vec2 rip=vec2(vnoise(hit.xz*1.3 + vec2(Time*0.3,0.0)),
vnoise(hit.xz*1.3 + vec2(5.0,-Time*0.25)))*0.05;
vec3 surface=normalize(vec3(rip.x,1.0,rip.y));
vec3 refl=reflect(rd,surface);
refl.y=abs(refl.y);
float fresnel=0.04 + 0.96*pow(1.0 - saturate(dot(-rd,surface)),5.0);
vec3 evening=moodSky(refl,dot(refl,sun))*0.9;
float path=MoodDetail[2].y;
if (path > 0.0) {
float off=2.0*(1.0 - dot(refl,MOON_DIR));
float along=exp(-off/0.03);
if (along > 0.003) {
vec2 g=hit.xz*14.0 + vec2(Time*0.6,Time*0.2);
vec2 cell=floor(g);
float pick=hash12(cell);
vec2 off=(g - cell - 0.3 - 0.4*vec2(hash12(cell + 5.3),hash12(cell + 9.1)))*vec2(0.6,1.4);
float glint=step(0.8,pick)*pow(0.5 + 0.5*sin(Time*(3.0 + pick*6.0) + pick*50.0),8.0)
*(1.0 - smoothstep(0.08,0.28,length(off)));
evening +=vec3(0.95,0.93,1.0)*along*(0.3 + 3.5*glint)*path;
}
}
scene=mix(scene,evening,saturate(fresnel + 0.15)*water*mirror);
sceneLum=luminance(scene);
}
float afloat=MoodDetail[2].x*Ground;
if (afloat > 0.0 && dist < 48.0) {
float eddy=vnoise(hit.xz*0.15 + 3.0)*0.8;
vec2 at=hit.xz + vec2(-0.5,0.85)*eddy + vec2(Time*0.04,Time*0.025);
float raft=smoothstep(0.1,0.7,vnoise(vec2(at.x*0.12 + at.y*0.07,at.y*0.45 - at.x*0.2)));
float fill=saturate((0.07 + 0.9*raft)*afloat);
float detail=1.0 - smoothstep(0.012,0.034,groundPixel);
vec3 tint=blossomTint();
float light=max(sceneLum*1.6,0.1);
vec3 average=BLOSSOM*vec3(0.98,0.84,0.91)*tint*light;
vec3 under=mix(scene,average*0.85,saturate(raft*raft*0.7*afloat));
vec3 laid=mix(under,average,fill*0.34);
if (detail > 0.0) {
vec4 petal=lyingPetal(at,9.0,fill,max(2.0*groundPixel,0.001),tint);
laid=mix(laid,mix(under,petal.rgb*light,petal.a),detail);
}
scene=mix(scene,laid,0.88*water*(1.0 - smoothstep(ViewDistance*0.55,ViewDistance*0.95,dist)));
sceneLum=luminance(scene);
}
}
const vec2 SNOW_WIND=vec2(0.8944272,0.4472136);
const vec3 SNOW_LIGHT=vec3(0.4506,0.8211,0.3505);
float snowGlint=0.0;
float snowMapFade(vec3 at) {
if (HasMap==0) return 0.0;
vec2 c=at.xz - CamPos.xz - MapOrigin.xz;
return smoothstep(1.0,6.0,min(min(c.x,c.y),min(64.0 - c.x,64.0 - c.y)));
}
float snowOpen(vec3 at,float y) {
float fade=snowMapFade(at);
if (fade <=0.0) return 1.0;
float open=openSky(at,y);
float share=open >=1.0?1.0:mix(MoodDetail[0].y,1.0,open > 0.0?0.62:0.0);
return mix(1.0,share,fade);
}
float snowPatches(float share,float noise) {
return saturate((share*1.3 - noise)/0.3);
}
float snowThaw(vec2 xz,float drift) {
float thaw=vnoise(xz*0.12)*0.5 + 0.5 + 0.15*drift;
return smoothstep(1.0 - 1.2*Ground,1.15 - 1.2*Ground,thaw);
}
float snowWallAt(ivec2 c,float y) {
if (c.x < 0 || c.y < 0 || c.x >=64 || c.y >=64) return 0.0;
vec4 column=ncColumnAt(Sampler3,c);
return column.a > 0.5 && column.g > y + 0.5?1.0:0.0;
}
float snowWallSpan(ivec2 c,ivec2 axis,float at,float y) {
float f=fract(at) - 0.5;
ivec2 lo=f < 0.0?c - axis:c;
float a=snowWallAt(lo - axis,y);
float b=snowWallAt(lo,y);
float d=snowWallAt(lo + axis,y);
float e=snowWallAt(lo + axis*2,y);
float taper=mix(smoothstep(0.0,0.5,0.5 - abs(f)),1.0,f < 0.0?b:d);
return mix((a + b + d)/3.0,(b + d + e)/3.0,f < 0.0?f + 1.0:f)*taper;
}
vec3 snowWallUpwind(vec3 at,float y,float reach) {
vec2 o=at.xz - CamPos.xz - MapOrigin.xz;
ivec2 c=ivec2(floor(o));
vec2 delta=1.0/SNOW_WIND;
vec2 side=(o - vec2(c))*delta;
float t=reach + 1.0;
float high=0.0;
bool acrossX=false;
for (int i=0;i < 6;++i) {
acrossX=side.x < side.y;
if (acrossX) {
t=side.x;
side.x +=delta.x;
c.x -=1;
} else {
t=side.y;
side.y +=delta.y;
c.y -=1;
}
if (t > reach || c.x < 0 || c.y < 0 || c.x >=64 || c.y >=64) {
t=reach + 1.0;
break;
}
vec4 column=ncColumnAt(Sampler3,c);
if (column.a > 0.5 && column.g > y + 0.5) {
high=column.g - y;
break;
}
}
if (high <=0.0) return vec3(reach + 1.0,0.0,0.0);
vec2 met=o - SNOW_WIND*t;
float width=acrossX?snowWallSpan(c,ivec2(0,1),met.y,y):snowWallSpan(c,ivec2(1,0),met.x,y);
return vec3(t,high,width);
}
vec2 snowDriftAt(float away,float wall,float width) {
float k=MoodDetail[0].w*2.0*(0.4 + 0.6*width);
float high=min(wall,1.0);
float deep=high*0.55*k;
float run=min(1.2 + 1.8*high*k,4.5);
float x=saturate(away/run);
return vec2(deep*(1.0 - x)*(1.0 - x),-2.0*deep/run*(1.0 - x));
}
void snowIce(inout vec3 scene,vec3 hit,vec3 rd,float dist,float water,float level) {
float k=MoodDetail[2].x;
if (k <=0.0) return;
vec3 snowWhite=mix(vec3(0.22,0.25,0.33),vec3(0.93,0.95,1.0),level);
float frozen=1.0;
if (k < 0.999) {
float sheet=vnoise(hit.xz*0.09)*0.5 + 0.5 + 0.12*vnoise(hit.xz*0.6);
frozen=smoothstep(1.0 - 1.15*k,1.12 - 1.15*k,sheet);
if (frozen <=0.0) return;
}
vec3 ice=mix(scene*vec3(0.5,0.72,0.9),vec3(0.6,0.74,0.84)*mix(0.22,1.0,level),0.72);
vec3 refl=reflect(rd,vec3(0.0,1.0,0.0));
vec3 sky=mix(SkyHorizon,SkyTop,sqrt(saturate(refl.y)))*mix(0.3,1.0,level);
float fresnel=0.03 + 0.97*pow(1.0 - saturate(-rd.y),5.0);
ice=mix(ice,sky,fresnel*0.75);
if (dist < 40.0) {
vec2 p=hit.xz*0.45;
p +=vec2(vnoise(p*1.7),vnoise(p*1.7 + 3.1))*0.3;
vec2 n=floor(p);
vec2 f=p - n;
float d1=8.0;
float d2=8.0;
for (int j=-1;j <=1;++j) {
for (int i=-1;i <=1;++i) {
vec2 cell=vec2(float(i),float(j));
vec2 at=cell + vec2(hash12(n + cell),hash12(n + cell + 17.3)) - f;
float d=dot(at,at);
if (d < d1) {
d2=d1;
d1=d;
} else if (d < d2) {
d2=d;
}
}
}
float edge=sqrt(d2) - sqrt(d1);
float width=max(0.02,1.2*dist*PixelWorld*0.45);
float crack=(1.0 - smoothstep(0.0,width,edge))*smoothstep(0.15,0.45,vnoise(p*0.6 + 4.0))
*(1.0 - smoothstep(24.0,40.0,dist));
ice=mix(ice,snowWhite*0.9,crack*0.5);
}
float powder=vnoise(hit.xz*0.35)*0.6 + vnoise(hit.xz*1.4 + 5.0)*0.4;
ice=mix(ice,snowWhite,smoothstep(0.1,0.55,powder)*0.9);
scene=mix(scene,ice,frozen*water);
}
float snowOnLeaves(vec3 hit,vec3 normal,float dist) {
float k=MoodDetail[1].w;
if (k <=0.0 || dist > 34.0 || abs(normal.y) > 0.5) return 0.0;
vec4 column=mapAt(renderedBlockProbe(hit));
if (!mapCanopyContains(hit)) return 0.0;
vec2 face=mod(vec2(abs(normal.x) > abs(normal.z)?hit.z:hit.x,hit.y),256.0);
float n=vnoise(face*4.0)*0.6 + vnoise(face*9.0 + 3.7)*0.4*(1.0 - smoothstep(0.012,0.036,dist*PixelWorld));
float upper=smoothstep(0.2,0.85,fract(face.y));
float want=k*upper*(0.55 + 0.45*smoothstep(column.g,column.r,hit.y));
return smoothstep(1.0 - want,1.08 - want,n*0.5 + 0.5)*(1.0 - smoothstep(24.0,34.0,dist))
*snowMapFade(hit);
}
float snowClouds(vec3 hit) {
float k=MoodDetail[2].z;
if (k <=0.0) return 1.0;
vec2 p=hit.xz/48.0 - SNOW_WIND*(Time*Speed*0.05*max(1.0 + MoodDetail[2].w,0.3));
return 1.0 + k*(0.06 - 0.2*smoothstep(-0.35,0.55,vnoise(p)));
}
void snowSettle(inout vec3 scene,inout float sceneLum,vec2 uv,vec3 hit,vec3 normal,vec3 rd,
float dist,float near,float water,float occluded) {
float deep=max(Ground - 1.0,0.0);
float level=smoothstep(0.02,0.5,sceneLum);
bool mapped=HasMap !=0 && dist < 34.0;
float upFace=smoothstep(0.55,0.85,normal.y)*(1.0 - water);
float sideFace=1.0 - smoothstep(0.2,0.5,abs(normal.y));
float below=1.0 - fract(hit.y);
if (below > 0.985) below=0.0;
if (below < 0.03 && normal.y > 0.0 && normal.y < 0.85 && water <=0.0) sideFace=1.0;
float lipScale=(1.0 + deep)*(1.0 + MoodDetail[1].x);
bool draped=sideFace > 0.0 && below < 0.19*lipScale;
if (water > 0.0) snowIce(scene,hit,rd,dist,water,level);
if (sideFace > 0.0) {
float sideLevel=max(smoothstep(0.02,0.5,sceneLum*2.4),smoothstep(0.1,0.8,luminance(HazeColor)));
vec3 sideWhite=mix(vec3(0.22,0.25,0.33),vec3(0.93,0.95,1.0),sideLevel);
float thaw=Ground < 1.0?snowThaw(hit.xz,driftNoise(hit.xz).x):1.0;
vec3 face=abs(normal.x) > abs(normal.z)?vec3(sign(normal.x),0.0,0.0)
:vec3(0.0,0.0,sign(normal.z));
if (draped) {
float edgeY=hit.y + below;
float openLip=snowOpen(hit + face*0.5,edgeY);
float thick=(0.13 + 0.06*vnoise(hit.xz*3.0 + hit.y*0.5))*lipScale;
float lip=sideFace*(1.0 - smoothstep(thick*0.75,thick,below))*thaw*openLip;
if (lip > 0.001) {
vec2 upUv=uv + vec2(0.0,(below*1.3 + 0.08)/max(dist*PixelWorld,0.00001)/max(Resolution.y,1.0));
vec3 aboveEdge=worldAt(clamp(upUv,vec2(0.001),vec2(0.999)));
if (aboveEdge.y > edgeY + 0.03 && distance(aboveEdge.xz,hit.xz) < 0.2) lip=0.0;
float crest=1.0 - below/max(thick,0.001);
vec3 lipSnow=sideWhite*mix(vec3(0.62,0.70,0.9),vec3(1.05),smoothstep(0.0,0.7,crest));
scene=mix(scene,lipSnow,lip*SnowCover);
}
}
float lee=dot(face.xz,SNOW_WIND);
if (mapped && lee > 0.0 && MoodDetail[0].w > 0.0) {
vec4 front=mapAt(hit + face*0.5);
vec4 wall=mapAt(hit - face*0.5);
float rise=hit.y - front.g;
if (front.a > 0.5 && wall.a > 0.5 && rise > -0.1 && rise < 1.8) {
float along=dot(hit.xz,vec2(-face.z,face.x));
vec2 onMap=hit.xz - CamPos.xz - MapOrigin.xz;
ivec2 wallCell=ivec2(floor(onMap - face.xz*0.5));
float width=face.x !=0.0?snowWallSpan(wallCell,ivec2(0,1),onMap.y,front.g)
:snowWallSpan(wallCell,ivec2(1,0),onMap.x,front.g);
float crest=snowDriftAt(0.0,wall.g - front.g,width).x*smoothstep(0.05,0.5,lee)
*(0.8 + 0.25*vnoise(vec2(along*0.9,2.7)) + 0.06*vnoise(vec2(along*4.0,5.1)));
float banked=(1.0 - smoothstep(crest - 0.05,crest,rise))*step(0.03,crest)
*snowMapFade(hit)*(1.0 - smoothstep(24.0,34.0,dist));
vec3 bank=sideWhite*mix(vec3(0.86,0.9,0.98),vec3(1.0),
smoothstep(0.0,1.0,rise/max(crest,0.01)));
scene=mix(scene,bank,banked*SnowCover*thaw);
}
}
if (mapped) {
float caught=snowOnLeaves(hit,face,dist);
if (caught > 0.0) {
vec3 clump=sideWhite*(0.8 + 0.2*smoothstep(0.2,0.9,fract(hit.y)));
scene=mix(scene,clump,caught*SnowCover*thaw);
}
}
}
if (upFace > 0.0) {
vec3 snowWhite=mix(vec3(0.22,0.25,0.33),vec3(0.93,0.95,1.0),level);
float cold=saturate(occluded*1.4)*MoodDetail[0].z;
vec3 drift=dist < 80.0?driftNoise(hit.xz)*(1.0 - smoothstep(50.0,80.0,dist)):vec3(0.0);
float thaw=Ground < 1.0?snowThaw(hit.xz,drift.x):1.0;
float openUp=snowOpen(hit,hit.y);
vec2 bank=vec2(0.0);
if (mapped && MoodDetail[0].w > 0.0 && dist < 24.0) {
vec3 wall=snowWallUpwind(hit,hit.y,4.5);
if (wall.y > 0.0) bank=snowDriftAt(wall.x,wall.y,wall.z)*(1.0 - smoothstep(16.0,24.0,dist));
}
float filled=saturate(bank.x*4.0);
cold*=1.0 - filled;
vec2 slope=drift.yz*0.315 + SNOW_WIND*bank.y;
float grainy=MoodDetail[1].y;
if (grainy > 0.0 && dist < 32.0) {
float phase=dot(hit.xz,SNOW_WIND)*14.0 + drift.x*3.0;
slope +=SNOW_WIND*(cos(phase)*0.3*grainy*saturate(0.4 + 0.8*drift.x)
*(1.0 - smoothstep(16.0,32.0,dist)));
}
float driftLight=0.9;
if (dist < 50.0) {
vec3 shape=normalize(vec3(-slope.x,1.0,-slope.y));
float lit=0.45 + 0.55*max(dot(shape,SNOW_LIGHT),0.0);
driftLight=mix(lit,0.9,smoothstep(18.0,50.0,dist)*(1.0 - filled));
}
vec3 snow=snowWhite*mix(vec3(0.72,0.80,0.97),vec3(1.0),driftLight)
*(0.86 + 0.12*driftLight);
snow*=1.0 + Relight*0.1*near;
float snowGrainFade=1.0 - smoothstep(0.012,0.036,dist*PixelWorld);
if (grainy > 0.0 && snowGrainFade > 0.0) {
float grain=vnoise(mod(hit.xz*4.5 + floor(hit.y)*3.1,256.0));
snow*=1.0 + grain*0.035*grainy*snowGrainFade;
}
snow*=mix(vec3(1.0),ShadowTint*(0.6/max(luminance(ShadowTint),0.05)),cold);
float sparkle=1.0 + MoodDetail[1].z;
float glint=0.0;
if (sparkle > 0.0 && dist < 40.0) {
vec2 g=hit.xz*14.0;
vec3 h3=hash33(vec3(floor(g),floor(hit.y)) + 0.37);
if (h3.x > 0.7) {
vec3 facet=normalize(vec3((h3.y - 0.5)*1.4,0.9,(h3.z - 0.5)*1.4));
float mirror=pow(saturate(dot(reflect(rd,facet),SNOW_LIGHT)),24.0);
vec2 spot=fract(g) - 0.25 - 0.5*fract(h3.yz*7.3);
float size=max(0.2,1.2*dist*PixelWorld*14.0);
float point=1.0 - smoothstep(size*0.45,size,length(spot));
glint=mirror*point*sparkle*level*(1.0 - smoothstep(14.0,40.0,dist));
}
}
float patchy=saturate(drift.x*0.35 + 0.5 + (hash12(floor(hit.xz*8.0)) - 0.5)*0.3);
float lay=mix(0.94 + 0.06*drift.x,1.0,saturate(deep + filled));
float cover=upFace*lay*SnowCover*thaw*snowPatches(openUp,patchy);
scene=mix(scene,snow,cover);
snowGlint=glint*cover;
}
scene*=snowClouds(hit);
sceneLum=luminance(scene);
}
const float SNOW_SHELL[3]=float[3](14.0,21.0,31.0);
const float SNOW_SHELL_CELL[3]=float[3](0.9,1.2,1.6);
vec3 snowCurtain(vec3 color,vec3 ro,vec3 rd,float dist,float sigma,float thickness) {
float amount=MoodDetail[2].y;
float across=length(rd.xz);
if (amount <=0.0 || across < 0.05) return color;
float wind=1.0 + MoodDetail[2].w;
vec3 shift=vec3(0.4*wind,-1.3,0.2*wind)*(Time*Speed);
float lit=max(min(luminance(HazeColor)*1.1,1.0),0.15 + 0.85*luminance(color));
for (int i=0;i < 3;++i) {
float t=SNOW_SHELL[i]/across;
if (t > dist || t > 120.0) break;
float cell=SNOW_SHELL_CELL[i];
vec3 c=floor((ro + rd*t - shift)/cell);
if (hash13(c + float(i)*31.0) >=0.45*amount) continue;
vec3 h3=hash33(c + 5.0);
vec3 centre=(c + 0.5 + (h3 - 0.5)*0.7)*cell + shift;
float along=dot(centre - ro,rd);
if (along >=dist) continue;
float r=max(0.03,1.3*along*PixelWorld);
float body=1.0 - smoothstep(r*0.35,r,length(ro + rd*along - centre));
if (body <=0.0) continue;
float air=exp(-sigma*heightIntegral(ro,rd,along,GroundY - 2.0,thickness));
color=mix(color,ParticleColor*lit,body*air*(0.55 + 0.35*h3.x));
}
return color;
}
vec3 snowAir(vec3 color,vec3 ro,vec3 rd,float dist,float sigma,float thickness,float fog) {
color=mix(color,vec3(0.99),saturate(snowGlint)*(1.0 - fog));
return snowCurtain(color,ro,rd,dist,sigma,thickness);
}
float groundSpecks(vec3 hit,vec3 normal,float dist,float water,float fine,float share,
out float face,out float tint) {
tint=0.0;
face=smoothstep(0.75,0.92,normal.y)*(1.0 - water)
*(1.0 - smoothstep(fine*1.25,fine*2.1,dist));
if (face <=0.0) return 0.0;
vec2 speck=floor(hit.xz*fine);
float pick=hash12(speck + floor(hit.y)*17.0);
float heap=smoothstep(-0.2,0.6,vnoise(hit.xz*0.18));
tint=hash12(speck + 3.1);
return step(1.0 - (0.03 + 0.09*heap)*share,pick)*face;
}
void sakuraSettle(inout vec3 scene,inout float sceneLum,vec3 hit,
float dist,float water,float groundPixel) {
float face=(1.0 - water)*groundCoverSurface(hit,dist*PixelWorld)
*(1.0 - smoothstep(ViewDistance*0.65,ViewDistance,dist));
if (face <=0.0) return;
vec2 pattern=groundPatternXZ(hit);
float heap=smoothstep(-0.2,0.6,vnoise(pattern*0.18));
float fill=saturate(Ground*(0.15 + 0.55*heap));
vec3 tint=blossomTint();
float light=max(sceneLum*1.5,0.08);
float cover=fill*0.34;
vec3 average=BLOSSOM*vec3(0.98,0.84,0.91)*tint*light;
float detail=1.0 - smoothstep(0.012,0.034,groundPixel);
vec3 laid=mix(scene,average,cover);
if (detail > 0.0) {
vec4 petal=lyingPetal(pattern,10.0,fill,max(2.0*groundPixel,0.001),tint);
laid=mix(laid,mix(scene,petal.rgb*light,petal.a),detail);
}
float carpet=saturate(Ground - 1.0)*0.35*(0.4 + 0.6*heap);
laid=mix(laid,average,carpet);
scene=mix(scene,laid,face*min(SnowCover*1.3,1.0));
sceneLum=luminance(scene);
}
vec3 autumnTone(float pick) {
if (pick < 0.20) return vec3(0.78,0.16,0.07);
if (pick < 0.46) return vec3(0.90,0.42,0.08);
if (pick < 0.74) return vec3(0.95,0.70,0.15);
if (pick < 0.88) return vec3(0.70,0.68,0.18);
return vec3(0.50,0.30,0.12);
}
vec2 autumnTree(vec2 xz) {
vec2 p=xz/4.5;
vec2 base=floor(p - 0.5);
vec2 best=base;
float bestD=1.0e9;
for (int j=0;j < 2;++j) {
for (int i=0;i < 2;++i) {
vec2 c=mod(base + vec2(float(i),float(j)),256.0);
vec2 d=p - (base + vec2(float(i),float(j)) + 0.2 + 0.6*hash33(vec3(c,0.37)).xy);
float dd=dot(d,d);
if (dd < bestD) { bestD=dd;best=c;}
}
}
return best;
}
vec3 autumnCanopy(vec3 c,vec3 hit,vec3 block,float pick,float pixel) {
    vec3 tone = mix(vec3(0.86,0.32,0.06), vec3(0.96,0.66,0.12), pick);
    tone = mix(tone, vec3(0.72,0.14,0.06), smoothstep(0.74,0.84,pick));
    float fine = 1.0 - smoothstep(0.008,0.034,pixel);
    float shade = 1.20;
    if (MoodDetail[0].x > 0.0 || MoodDetail[0].z > 0.0) {
        vec2 tree = autumnTree(block.xz + 0.5);
        vec3 byTree = autumnTone(hash12(tree + 0.71));
        tone = mix(tone, byTree, MoodDetail[0].x);
        if (hash12(tree + 5.3) < MoodDetail[0].z) {
            vec3 dry = mix(tone, vec3(0.52,0.30,0.13), 0.60);
            float spread = fine > 0.0 ? vnoise(mod(hit.xz*4.2 + hit.y*2.7,256.0)) : 0.0;
            float leafy = mix(0.5, smoothstep(-0.36,0.36,spread), fine);
            tone = mix(vec3(0.30,0.25,0.21), dry, leafy);
            shade = mix(0.90, 1.20, leafy);
        }
    }
    if (fine > 0.0) {
        vec3 grain = mod(hit*5.5,256.0);
        shade += 0.07*fine*vnoise(grain.xz + grain.y*1.7);
    }
    return tone*(luminance(c)*shade/max(luminance(tone),0.05));
}
vec4 autumnLawn(vec3 c,vec3 hit,float pick,float pixel) {
    float dry = MoodDetail[1].y;
    if (dry <= 0.0) return vec4(c,0.0);
    float patchy = smoothstep(-0.45,0.45,vnoise(mod(hit.xz,256.0)*0.07) + (pick - 0.5)*0.3);
    float amount = saturate(dry*(0.45 + 1.4*patchy));
    float fine = 1.0 - smoothstep(0.010,0.036,pixel);
    float shade = 1.12;
    if (fine > 0.0) shade += 0.09*fine*vnoise(mod(hit.xz*4.5 + hit.y*1.3,256.0) + 9.1);
    vec3 straw = mix(vec3(0.80,0.68,0.24), vec3(0.85,0.78,0.48), smoothstep(0.55,0.95,patchy));
    straw = mix(straw, vec3(0.55,0.42,0.20), smoothstep(0.75,0.97,pick)*0.6);
    straw *= luminance(c)*shade/max(luminance(straw),0.05);
    return vec4(straw,amount);
}
vec2 autumnPuddle(vec2 xz,float lawn,float footprint) {
    vec3 broad = vnoiseD(xz*0.31 + 3.7);
    vec3 fine = vnoiseD(xz*1.07 + 9.1);
    float n = 0.7*broad.x + 0.3*fine.x;
    vec2 gradient = 0.217*broad.yz + 0.321*fine.yz;
    float aa = clamp(footprint*length(gradient),0.008,0.07);
    float edge = 0.6 - 0.55*MoodDetail[1].z + 0.3*lawn;
    return vec2(smoothstep(edge - aa,edge + 0.03 + aa,n),
        smoothstep(edge - 0.05 - aa,edge + aa,n));
}
vec4 overLayer(vec4 under,vec4 over) {
float a=over.a + under.a*(1.0 - over.a);
return vec4((over.rgb*over.a + under.rgb*under.a*(1.0 - over.a))/max(a,0.0001),a);
}
vec4 autumnRidge(vec3 rd) {
if (MoodDetail[2].z < 0.5 || rd.y > 0.06 || rd.y < -0.10) return vec4(0.0);
vec2 d=normalize(rd.xz + vec2(0.0001, 0.0));
float aa=PixelWorld;
float hFade=smoothstep(-0.09, -0.005, rd.y);
float far=0.020 + 0.011*vnoise(d*2.8 + 1.4) + 0.005*vnoise(d*9.5 + 4.0);
float mid=0.012 + 0.008*vnoise(d*4.6 + 6.2) + 0.003*vnoise(d*24.0 + 1.1);
float near=0.0065 + 0.0045*vnoise(d*7.5 + 8.3) + 0.0024*abs(sin(atan(d.y, d.x)*220.0 + vnoise(d*40.0)*3.0))*(0.5 + 0.5*vnoise(d*90.0));
float inFar=1.0 - smoothstep(far - aa,far + aa,rd.y);
float inMid=1.0 - smoothstep(mid - aa,mid + aa,rd.y);
float inNear=1.0 - smoothstep(near - aa,near + aa,rd.y);
if (inFar <=0.0 && inMid <=0.0 && inNear <=0.0) return vec4(0.0);
vec3 farCol=mix(SkyHorizon,vec3(0.46,0.49,0.64),0.48);
vec3 midCol=mix(SkyHorizon,vec3(0.42,0.28,0.26),0.55);
float m=vnoise(d*55.0 + 3.3)*0.5 + 0.5;
vec3 forest=mix(vec3(0.46,0.16,0.06),vec3(0.64,0.44,0.12),m);
forest*=0.82 + 0.28*saturate(dot(d,normalize(SunDir.xz + vec2(0.0001,0.0))));
forest=mix(forest,SkyHorizon,0.18);
vec3 res=mix(farCol, midCol, inMid);
res=mix(res, forest, inNear);
return vec4(res, max(inFar, max(inMid, inNear)) * hFade);
}
vec4 autumnCirrus(vec3 rd,float mu) {
float amount=MoodDetail[2].x;
if (amount <=0.0 || rd.y <=0.0) return vec4(0.0);
vec2 p=rd.xz/(rd.y + 0.12);
vec2 q=vec2(p.x*0.8 + p.y*0.45,p.y*0.22 - p.x*0.12)*1.4 + vec2(Time*0.010,Time*0.003);
float n=vnoise(q)*0.65 + vnoise(vec2(q.x*6.0,q.y*1.6) + 5.3)*0.35;
float t=mix(0.55,-0.15,amount);
float c=smoothstep(t,t + 0.5,n)*smoothstep(0.03,0.25,rd.y);
if (c <=0.0) return vec4(0.0);
float sunOn=saturate(luminance(SkySun)*4.0);
float toSun=pow(saturate(mu*0.5 + 0.5),6.0)*sunOn;
vec3 col=mix(vec3(0.94,0.93,0.95),vec3(1.0,0.86,0.62),toSun);
col +=SkySun*(c*(1.0 - c)*4.0*henyeyGreenstein(mu,0.8)*0.03*sunOn);
return vec4(col,c*0.75);
}
float segmentDistance(vec2 p,vec2 a,vec2 b) {
vec2 ab=b - a;
float t=saturate(dot(p - a,ab)/max(dot(ab,ab),1.0e-10));
return length(p - a - ab*t);
}
vec4 autumnBirds(vec3 rd) {
float often=MoodDetail[2].y;
if (often <=0.0 || rd.y < 0.05) return vec4(0.0);
float period=mix(150.0,45.0,often);
float cycle=floor(Time/period);
float u=(Time - cycle*period)/40.0;
if (u >=1.0 || hash12(vec2(cycle,3.7)) > 0.4 + 0.6*often) return vec4(0.0);
vec3 h=hash33(vec3(cycle,1.3,7.1));
float way=h.x < 0.5?-1.0:1.0;
float az=h.y*6.2831853 + way*1.4*(u - 0.5);
float el=0.25 + 0.35*h.z;
vec3 flock=vec3(cos(el)*cos(az),sin(el),cos(el)*sin(az));
float facing=dot(rd,flock);
if (facing < 0.99) return vec4(0.0);
vec3 ahead=vec3(-sin(az),0.0,cos(az))*way;
vec3 skyUp=normalize(vec3(0.0,1.0,0.0) - flock*flock.y);
vec2 lp=vec2(dot(rd,ahead),dot(rd,skyUp))/facing;
float span=0.011 + 0.004*h.x;
float level=sin(el),upright=cos(el);
const float AWAY=1.2;
vec2 heading=vec2(cos(AWAY),-sin(AWAY)*level);
vec2 wing=vec2(-sin(AWAY),-cos(AWAY)*level);
float aa=PixelWorld;
int count=9 + int(h.y*6.0);
float cover=0.0;
for (int i=0;i < 15;++i) {
if (i >=count) break;
float k=float((i + 1)/2);
float side=(i - 2*(i/2))==0?1.0:-1.0;
vec2 at=(-heading*1.25 + wing*side*0.9)*(k*span);
at +=vec2(sin(Time*0.7 + float(i)*1.9),cos(Time*0.5 + float(i)*2.3))*span*0.15;
vec2 b=lp - at;
if (dot(b,b) > span*span) continue;
float beat=sin(Time*6.2 + float(i)*0.85 + h.z*20.0);
vec2 raise=vec2(0.0,beat*upright*span);
vec2 elbow=wing*(0.24*span) + vec2(0.0, 0.06*span);
vec2 tip=wing*(0.52*span) - heading*(0.14*span);
float bodyW=segmentDistance(b,heading*(-0.32*span),heading*(0.30*span));
float wingR=min(segmentDistance(b,vec2(0.0),elbow + raise*0.14),
segmentDistance(b,elbow + raise*0.14,tip + raise*0.32));
float wingL=min(segmentDistance(b,vec2(0.0),-vec2(wing.x,-wing.y)*(0.24*span) + vec2(0.0,0.06*span) + raise*0.14),
segmentDistance(b,-vec2(wing.x,-wing.y)*(0.24*span) + vec2(0.0,0.06*span) + raise*0.14,-wing*(0.52*span) - heading*(0.14*span) + raise*0.32));
float d=min(bodyW,min(wingR,wingL));
float w=max(0.05*span,0.6*aa);
cover=max(cover,(1.0 - smoothstep(w - 0.5*aa,w + 0.5*aa,d))*min(1.0,0.05*span/w + 0.35));
}
return vec4(mix(vec3(0.10,0.09,0.09),SkyHorizon,0.25),cover*0.9);
}
vec4 autumnSkyOver(vec3 rd,float mu) {
vec4 over=autumnRidge(rd);
vec4 cirrus=autumnCirrus(rd,mu);
if (cirrus.a > 0.0) over=overLayer(over,cirrus);
vec4 birds=autumnBirds(rd);
if (birds.a > 0.0) over=overLayer(over,birds);
return over;
}
vec3 autumnDew(vec3 hit,vec3 rd,vec3 sun,float groundPixel) {
    const float radius = 0.12;
    float pixel = groundPixel*20.0;
    float resolved = 1.0 - smoothstep(0.28,0.62,pixel);
    if (resolved <= 0.0) return vec3(0.0);
    float size = sqrt(radius*radius + pixel*pixel);
    float coverage = resolved*radius*radius/(size*size);
    vec2 q = hit.xz*20.0;
    vec2 first = floor(q - 0.5);
    float light = 0.0;
    for (int y = 0; y < 2; ++y) {
        for (int x = 0; x < 2; ++x) {
            vec2 cell = first + vec2(x,y);
            if (hash12(cell + 0.5) < 1.0 - 0.15*MoodDetail[1].w) continue;
            vec2 at = cell + 0.3 + 0.4*vec2(hash12(cell + 1.3),hash12(cell + 2.9));
            float drop = 1.0 - smoothstep(0.16,1.0,length(q - at)/size);
            if (drop <= 0.0) continue;
            vec3 facet = normalize(vec3(hash12(cell + 4.1) - 0.5,0.5,hash12(cell + 6.7) - 0.5));
            float glint = pow(saturate(dot(reflect(rd,facet),sun)),24.0);
            light += (0.12 + 2.6*glint)*drop;
        }
    }
    return SkySun*light*coverage;
}
vec3 autumnMirror(vec3 hit,vec3 refl,vec3 sky) {
mat4 viewProj=mat4(MoodDetail[4],MoodDetail[5],MoodDetail[6],MoodDetail[7]);
vec3 reflected=sky;
float remaining=1.0;
float t=0.25;
for (int i=0;i < 9;++i) {
vec3 p=hit + refl*t;
vec4 clip=viewProj*vec4(p - CamPos,1.0);
if (clip.w <=0.0) break;
vec2 at=clip.xy/clip.w*0.5 + 0.5;
if (at.x < 0.0 || at.y < 0.0 || at.x > 1.0 || at.y > 1.0) break;
float depth=textureLod(Sampler1,at,0.0).r;
if (!depthIsFar(depth)) {
vec3 there=worldPosFromDepth(at,depth);
float scene=length(there - CamPos);
float ray=length(p - CamPos);
float gap=ray - scene;
vec2 edge=min(at,1.0 - at);
float confidence=smoothstep(0.035,0.16,gap)
    *(1.0 - smoothstep(0.42 + 0.25*t,0.62 + 0.35*t,gap))
    *smoothstep(0.05,0.20,there.y - hit.y)
    *smoothstep(0.0,0.075,min(edge.x,edge.y));
if (confidence > 0.0) {
    float weight=0.52*confidence*remaining;
    reflected += (textureLod(Sampler0,at,0.0).rgb - sky)*weight;
    remaining -=weight;
}
}
t*=1.55;
}
return reflected;
}
void autumnSurface(inout vec3 scene,inout float sceneLum,vec3 materialColor,vec3 hit,vec3 normal,
vec3 rd,vec3 sun,float dist,float water,float groundPixel) {
vec3 c=scene;
vec3 block=mod(floor(renderedBlockProbe(hit)),256.0);
float pick=hash13(block);
float pixel=dist*PixelWorld;
float support=groundCoverSurface(hit,pixel);
float verified=mapHasColumn(hit)?surfaceMapFade(hit):0.0;
float green=greenness(materialColor);
float upward=smoothstep(0.32,0.88,normal.y);
float flank=1.0 - smoothstep(0.25,0.78,abs(normal.y));
float below=1.0 - smoothstep(-0.78,-0.32,normal.y);
float high=smoothstep(GroundY + 0.4,GroundY + 3.2,hit.y);
float crown=max(below,max(flank*(0.38 + 0.35*high),upward*high*0.24));
bool inCanopy=verified > 0.0 && mapCanopyContains(hit);
float exact=inCanopy?foliageGreenScore(materialColor,true):0.0;
float leaf=mix(green*crown,exact,verified)*(1.0 - water);
float lawn=green*saturate(support + upward*(1.0 - crown)*(1.0 - verified))*(1.0 - water);
if (leaf > 0.0) {
float evergreen=MoodDetail[0].y*smoothstep(0.7,0.9,materialColor.b/max(materialColor.r,0.001));
float turned=leaf*0.90*(1.0 - evergreen);
vec3 canopy=autumnCanopy(c,hit,block,pick,pixel/max(abs(dot(normal,rd)),0.36));
float against=pow(saturate(dot(rd,sun)),3.0)*saturate(0.3 - dot(normal,sun));
scene += (canopy - c)*turned + canopy*SkySun*(0.28*against*turned);
}
if (lawn > 0.0) {
vec4 straw=autumnLawn(c,hit,pick,pixel/max(abs(rd.y),0.36));
scene += (straw.rgb - c)*lawn*straw.a;
}
float level=support*(1.0 - water);
if (MoodDetail[1].z > 0.0 && level > 0.0 && dist < 64.0) {
vec2 pud=autumnPuddle(hit.xz,lawn,groundPixel)*level*step(0.3,openSky(hit,hit.y))
*(1.0 - smoothstep(48.0,64.0,dist));
if (pud.y > 0.0) {
scene*=1.0 - 0.2*pud.y;
if (pud.x > 0.0) {
vec2 rip=vec2(sin(dot(hit.xz,vec2(5.3,2.1)) + Time*1.9),
sin(dot(hit.xz,vec2(-1.7,6.1)) - Time*1.6))*0.025;
vec3 surface=normalize(vec3(rip.x,1.0,rip.y));
vec3 refl=reflect(rd,surface);
refl.y=abs(refl.y);
float rmu=dot(refl,sun);
vec3 sky=moodSky(refl,rmu) + SkySun*pow(saturate(rmu),320.0)*3.0;
sky=autumnMirror(hit,refl,sky);
float fresnel=0.03 + 0.97*pow(1.0 - saturate(dot(-rd,surface)),5.0);
vec3 under=mix(scene*0.4,vec3(0.20,0.15,0.10)*luminance(scene)*2.0,0.6);
scene=mix(scene,mix(under,sky,saturate(fresnel*1.4 + 0.25)),pud.x);
}
}
}
if (MoodDetail[1].w > 0.0 && lawn > 0.22 && level > 0.0 && dist < 26.0)
scene +=autumnDew(hit,rd,sun,groundPixel)*lawn*level*smoothstep(0.22,0.42,lawn)
    *smoothstep(0.05,0.3,luminance(c))*(1.0 - smoothstep(18.0,26.0,dist));
sceneLum=luminance(scene);
}
float autumnWall(vec3 hit) {
    vec2 grid = hit.xz - CamPos.xz - MapOrigin.xz;
    ivec2 cell = ivec2(floor(grid));
    vec2 local = fract(grid) - 0.5;
    float wall = 0.0;
    for (int z = -1; z <= 1; ++z) {
        for (int x = -1; x <= 1; ++x) {
            if (x == 0 && z == 0) continue;
            ivec2 neighbor = ivec2(x,z);
            float distance = length(max(abs(local - vec2(neighbor)) - 0.5,0.0));
            wall = max(wall,supportBlocker(cell + neighbor,hit.y,distance));
        }
    }
    return wall;
}
float autumnLeafTopped(vec2 xz) {
vec4 column=mapAt(vec3(xz.x,0.0,xz.y));
return column.a > 0.5 && abs(column.b - 3.0) < 0.5?1.0:0.0;
}
float autumnUnderTrees(vec3 hit) {
vec2 p=hit.xz*0.5 - 0.5;
vec2 i=floor(p);
vec2 f=smoothstep(0.0,1.0,p - i);
vec2 at=i*2.0 + 1.0;
return mix(mix(autumnLeafTopped(at),autumnLeafTopped(at + vec2(2.0,0.0)),f.x),
mix(autumnLeafTopped(at + vec2(0.0,2.0)),autumnLeafTopped(at + vec2(2.0)),f.x),f.y);
}
struct GroundFootprint {
    float radius;
    float sampleWidth;
    vec2 sampleOffset;
};
vec4 autumnFloatingLeaves(vec2 pattern,float cover,GroundFootprint groundSamples,out float shadow) {
    const float cells = 2.6;
    const vec2 offset = vec2(0.37,0.61);
    vec2 q = pattern*cells + offset;
    vec2 base = floor(q - 0.5);
    vec4 found = vec4(0.0);
    float pigmentWeight = 0.0;
    shadow = 0.0;
    for (int j = 0; j < 2; ++j) {
        for (int i = 0; i < 2; ++i) {
            vec2 cell = base + vec2(i, j);
            vec3 h = hash33(vec3(cell,5.9));
            float presence = 1.0 - smoothstep(cover - 0.08, cover, h.x);
            if (presence <= 0.0) continue;
            vec3 k = fract(h*vec3(7.31,13.7,5.3) + h.yzx);
            float size = 0.39 + 0.14*h.z;
            vec2 center = cell + 0.5 + 0.28*(vec2(h.z,k.z) - 0.5);
            vec2 delta = q - center;
            float reach = size + max(0.80*groundSamples.sampleWidth*cells,0.12)
                + length(groundSamples.sampleOffset)*cells;
            if (dot(delta,delta) > reach*reach) continue;
            vec2 turn = normalize(k.xy - 0.5 + 0.001);
            mat2 rotation=mat2(turn.x,turn.y,-turn.y,turn.x);
            vec2 local=rotation*delta/size;
            float species = fract(h.x*13.7 + h.z);
            float footprint=groundSamples.sampleWidth*cells/size;
            vec2 stepUv=rotation*groundSamples.sampleOffset*cells/size;
            vec2 a=local - stepUv;
            float blade=botanicalLeafBlade(a,species);
            float stem=botanicalLeafStem(a);
            float coverage=botanicalGroundCoverage(blade,stem,footprint);
            if (dot(stepUv,stepUv) > 0.000001) {
                vec2 b=local + stepUv;
                float otherBlade=botanicalLeafBlade(b,species);
                float otherStem=botanicalLeafStem(b);
                coverage=0.5*(coverage + botanicalGroundCoverage(otherBlade,otherStem,footprint));
                blade=0.5*(blade + otherBlade);
                stem=0.5*(stem + otherStem);
            }
            float distance = min(blade, stem - 0.026);
            float contact = (1.0 - smoothstep(0.0,0.16,distance))*(1.0 - coverage);
            shadow = max(shadow,contact*presence);
            float alpha = coverage*presence;
            if (alpha <= 0.0) continue;

            float seed = fract(h.z*11.3 + h.y);
            float shade = botanicalLeafShade(local, species, distance, h.y, footprint);
            vec2 leafBlock = floor((center - offset)/cells) + 0.5;
            vec3 treeTone = autumnTone(hash12(autumnTree(leafBlock) + 0.71));
            vec3 color = mix(autumnTone(seed),treeTone,0.5)*(0.85 + 0.3*fract(seed*7.7));
            float depth = fract(h.y*3.17 + h.z*5.3);
            float weight = alpha*(0.8 + 0.4*depth);
            shade *= 0.88 + 0.22*depth;
            found.rgb += color*shade*weight;
            pigmentWeight += weight;
            found.a += alpha*(1.0 - found.a);
        }
    }
    return vec4(found.rgb/max(pigmentWeight,0.0001), found.a);
}
float autumnLitterPresence(float cover) {
    const float fade = 0.08;
    float t = saturate(cover/fade);
    return max(0.0,cover - fade) + fade*t*t*t*(1.0 - 0.5*t);
}
void autumnSettle(inout vec3 scene,inout float sceneLum,vec3 hit,vec3 normal,float dist,
float water,float materialLum,GroundFootprint groundSamples) {
    bool afloat = water > 0.5;
    float surface = afloat ? smoothstep(0.90,0.99,normal.y) : groundCoverSurface(hit,dist*PixelWorld);
    if (surface <= 0.0) return;
    float opacity = surface*saturate(SnowCover*1.5);
    if (!afloat && ncBroadGroundCover(Sampler3,columnCell(hit.xz))) {
        opacity *= 1.0 - ncAutumnGroundDetail(hit - CamPos,PixelWorld);
    }
    if (opacity <= 0.0) return;

    float mapWeight = mapHasColumn(hit) ? surfaceMapFade(hit) : 0.0;
    vec2 pattern = groundPatternXZ(hit);
    float heap = smoothstep(-0.2,0.6,vnoise(pattern*0.18));
    float trees = mapWeight > 0.0 ? autumnUnderTrees(hit)*mapWeight : 0.0;
    float piles = !afloat && MoodDetail[1].x*mapWeight > 0.0
        ? MoodDetail[1].x*autumnWall(hit)*mapWeight : 0.0;
    float base = 0.18*Ground*(0.4 + 1.2*heap);
    float under = afloat ? 0.0 : min(base*(2.2 + 4.0*piles) + 0.9*trees*Ground,0.97);
    float over = min((base*(0.8 + 2.0*piles) + 0.4*trees*Ground)*(afloat ? 0.5 : 1.0),0.9);
    float light = afloat ? max(sceneLum*1.5,0.3) : max(materialLum*1.65,0.08);
    float presenceUnder = autumnLitterPresence(under);
    float presenceOver = autumnLitterPresence(over);
    float meanLow = 0.114*presenceUnder;
    float meanHigh = 0.237*presenceOver;
    vec4 low = vec4(vec3(0.42,0.27,0.13)*meanLow,meanLow);
    vec3 treeTone = autumnTone(hash12(autumnTree(pattern) + 0.71));
    vec4 high = vec4(mix(vec3(0.814,0.468,0.116),treeTone,0.5)*meanHigh,meanHigh);
    float shadow = 0.08*presenceOver;
    if (afloat) {
        float detail = 1.0 - smoothstep(0.011,0.036,groundSamples.radius);
        if (detail > 0.0) {
            vec2 drift = vec2(Time*0.04,Time*0.025);
            float coverShadow;
            vec4 art = autumnFloatingLeaves(pattern + drift,over,groundSamples,coverShadow);
            high = mix(high,vec4(art.rgb*art.a,art.a),detail);
            shadow = mix(shadow,coverShadow,detail);
        }
        high.rgb *= vec3(0.8,0.72,0.7);
    }
    scene = scene*(1.0 - opacity*low.a) + low.rgb*light*opacity;
    scene *= 1.0 - 0.35*shadow*opacity;
    scene = scene*(1.0 - opacity*high.a) + high.rgb*light*opacity;
    sceneLum = luminance(scene);
}
float autumnMistDepth(vec3 ro,vec3 rd,float dist) {
    float amount = MoodDetail[2].w;
    if (amount <= 0.0) return 0.0;
    float integ = heightIntegral(ro,rd,min(dist,220.0),GroundY - 1.0,4.0);
    vec3 probe = ro + rd*clamp(dist*0.5,2.0,48.0);
    float banks = 0.45 + 0.55*smoothstep(-0.5,0.5,vnoise(probe.xz*0.05 + vec2(Time*0.02,Time*0.008)));
    return 0.031*amount*banks*integ;
}
const vec3 HALLOW_RUST=vec3(0.48,0.24,0.09);
const vec3 HALLOW_OCHRE=vec3(0.55,0.42,0.17);
const vec3 HALLOW_UMBER=vec3(0.30,0.20,0.10);
float mysticGreen(vec3 c) {
return foliageGreenScore(c, false);
}
float mysticFaceShade(vec3 n) {
return n.y > 0.5?1.0:(n.y < -0.5?0.5:(abs(n.z) > abs(n.x)?0.8:0.6));
}
vec3 mysticFoliage(vec3 c,vec2 uv,vec3 hit,vec3 normal,float dist,float l,out float leaf) {
leaf=0.0;
float wither=MoodDetail[0].w;
if (wither <= 0.0) return c;
bool inCanopy=mapCanopyContains(hit);
float green=foliageGreenScore(c, inCanopy);
if (green <=0.0) return c;
float patches=hallowNoise(mod(hit.xz,256.0)*0.07 + hit.y*0.1 + 13.1)*0.5 + 0.5;
float turn=green*(1.0 - smoothstep(wither - 0.15,wither + 0.1,patches));
if (inCanopy) {
leaf=1.0;
} else if (mapHasColumn(hit)) {
leaf=0.0;
} else {
float side=turn > 0.0?smoothstep(0.85,0.95,max(abs(normal.x),abs(normal.z))):0.0;
if (side > 0.0 && fract(hit.y) > 0.78) {
side*=smoothstep(GroundY + 0.5,GroundY + 1.8,hit.y);
}
float under=1.0 - smoothstep(-0.7,-0.35,normal.y);
float top=smoothstep(0.65,0.9,normal.y)*smoothstep(GroundY + 0.5,GroundY + 2.2,hit.y);
leaf=max(max(side,under),top);
}
if (turn <=0.0) return c;
vec3 turned=vec3(0.47,0.42,0.25)*(0.8 + 0.35*patches);
turned*=l*1.3/max(luminance(turned),0.05);
if (leaf > 0.0) {
vec3 cell=mod(hit - normal*0.02,256.0);
float t=saturate(hallowNoise(cell.xz*0.11 + cell.y*0.05)*0.8 + 0.5);
vec3 tone=mix(HALLOW_RUST,HALLOW_OCHRE,smoothstep(0.3,0.5,t));
tone=mix(tone,HALLOW_UMBER,smoothstep(0.55,0.75,t));
tone*=0.92 + 0.16*hallowCell(floor(cell),61u);
tone*=l*(normal.y > 0.5?2.2:1.5)/max(luminance(tone),0.05);
float bare=wither*0.3;
float fine=1.0 - smoothstep(0.012,0.036,dist*PixelWorld);
float gap=mix(bare,smoothstep(0.5 - bare*0.5,0.5 + bare*0.5,hallowNoise(cell.xz*2.6 + cell.y*1.7)*0.5 + 0.5)*bare*1.4,fine);
tone*=1.0 - 0.45*gap;
vec3 fir=mix(vec3(l),c,0.7)*0.85;
turned=mix(turned,mix(tone,fir,smoothstep(0.65,0.85,c.b/max(c.r,0.001))),leaf);
}
return mix(c,turned,turn);
}
float hallowGlint(vec3 rd,vec3 hit,float along,float across) {
vec3 m=normalize(SunDir);
if (dot(vec3(rd.x,-rd.y,rd.z),m) < 1.0 - 3.0*along) return 0.0;
vec3 h=normalize(m - rd);
vec2 bearing=normalize(m.xz + vec2(0.00001,0.0));
float ha=dot(h.xz,bearing);
float hc=h.x*bearing.y - h.z*bearing.x;
float d=exp(-(ha*ha/(along*along) + hc*hc/(across*across)));
if (d < 0.002) return 0.0;
float sparkle=pow(saturate(hallowNoise(hit.xz*7.0 + Time*vec2(1.3,0.7))*0.5 + 0.5),4.0);
return d*(0.4 + 1.2*sparkle);
}
vec4 hallowLeaves(vec2 xz,float share,float pixel,out float contact) {
    contact = 1.0;
    vec4 found = vec4(0.0);
    vec2 first = floor(xz * 3.0 - 0.5);
    for (int j = 0; j < 2; ++j) {
        for (int i = 0; i < 2; ++i) {
            vec2 id = first + vec2(i, j);
            float pick = hallowCell(vec3(id, 0.0), 71u);
            float present = smoothstep(pick, pick + 0.09, share);
            if (present <= 0.0) continue;
            for (int k = 0; k < 2; ++k) {
                vec3 h = hallowCell3(vec3(id, float(k)), 72u);
                if (k == 1 && h.z > 0.55) break;
                vec2 center = (id + 0.5 + (h.xy - 0.5) * 0.5) / 3.0;
                float angle = h.z * 31.4159 + float(k) * 2.1;
                float size = 0.065 + 0.07 * fract(h.x * 13.7);
                vec2 rel = xz - center;
                float reach = size + max(0.025, pixel);
                if (dot(rel, rel) > reach * reach) continue;
                vec2 local = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * rel / size;
                float species = fract(h.x * 5.3);
                float seed = fract(h.y * 17.3 + h.z * 3.1);
                float blade = witheredLeafBlade(local, species, seed);
                float stem = botanicalLeafStem(local);
                float distance = min(blade, stem - 0.026);
                float shadow = 0.16 * (1.0 - smoothstep(0.0, 0.025, distance * size));
                contact = min(contact, 1.0 - present * shadow);
                float cover = botanicalGroundCoverage(blade, stem, pixel / size) * present;
                if (cover <= 0.0) continue;
                vec3 color = witheredLeafPalette(seed) * botanicalLeafShade(local, species, distance, seed, pixel / size);
                found.rgb = mix(found.rgb, color, cover);
                found.a = cover + found.a * (1.0 - cover);
            }
        }
    }
    return vec4(found.rgb / max(found.a, 0.001), found.a);
}
void mysticSurface(inout vec3 scene,inout float sceneLum,out vec3 base,out vec2 wet,out float crown,
vec2 uv,vec3 hit,vec3 normal,vec3 rd,float dist,float near,float water,float groundPixel) {
base=scene;
wet=vec2(0.0);
crown=0.0;
if (SnowCover <=0.0) return;
float mx=max(scene.r,max(scene.g,scene.b));
float sat=(mx - min(scene.r,min(scene.g,scene.b)))/max(mx,0.001);
float warm=step(scene.b,scene.g)*step(scene.g,scene.r);
float floorLum=mix(0.30,0.12,smoothstep(0.80,0.92,sat));
float selfLit=max(smoothstep(0.75,0.95,sceneLum),
warm*smoothstep(0.45,0.80,sat)*smoothstep(floorLum,floorLum + 0.22,sceneLum));
float level=MoodDetail[5].w > 0.0?MoodDetail[5].w:0.3;
vec3 albedo=scene/max(level*mysticFaceShade(normal),0.03);
bool snowy=luminance(albedo) > 0.6 && sat < 0.15;
float origGreen=mysticGreen(scene);
float leaf;
vec3 withered=mysticFoliage(scene,uv,hit,normal,dist,sceneLum,leaf);
scene=mix(withered,scene,selfLit);
crown=leaf;
float up=groundCoverSurface(hit,dist*PixelWorld);
float skyOver=HasMap !=0?openSky(hit,hit.y):1.0 - MoodDetail[4].w*(1.0 - smoothstep(4.0,10.0,dist));
float indoor=skyOver;
float roofless=HasMap !=0?step(0.1,skyOver):skyOver;
vec3 moonCol=hallowMoonColor();
bool moonOn=MoodDetail[1].x > 0.0;
float lake=water;
if (lake <=0.0 && normal.y > 0.97 && dist > 40.0)
lake=smoothstep(0.25,0.4,(scene.b - max(scene.r,0.92*scene.g))/max(scene.b,0.01));
float damp=MoodDetail[4].x*up*(1.0 - smoothstep(40.0,48.0,dist))*indoor
*(1.0 - lake)*(1.0 - leaf)*(1.0 - smoothstep(0.0,0.25,selfLit))*(snowy?0.0:1.0);
if (damp > 0.0) {
float l=luminance(scene);
scene=mix(vec3(l),scene,1.0 + 0.15*damp)*(1.0 - 0.28*damp);
if (dist < 64.0) {
float glance=pow(1.0 - saturate(-rd.y),5.0);
scene +=(SkyHorizon*0.25*glance + (moonOn?moonCol*0.05*hallowGlint(rd,hit,0.3,0.2):vec3(0.0)))
*damp;
}
}
float puddle=0.0;
if (damp > 0.0 && MoodDetail[4].z > 0.0 && normal.y > 0.97 && dist < 40.0
&& origGreen < 0.3) {
float n=hallowNoise(hit.xz*0.35)*0.65 + hallowNoise(hit.xz*0.9)*0.35;
float edge=0.7 - MoodDetail[4].z*0.6;
puddle=smoothstep(edge,edge + 0.06,n)*(1.0 - smoothstep(32.0,40.0,dist))*indoor;
if (puddle > 0.0) {
vec2 rip=vec2(hallowNoise(hit.xz*2.1 + vec2(Time*0.3,0.0)),hallowNoise(hit.xz*2.1 + vec2(5.0,-Time*0.25)))*0.015;
vec3 surface=normalize(vec3(rip.x,1.0,rip.y));
vec3 refl=reflect(rd,surface);
refl.y=abs(refl.y);
float fresnel=0.04 + 0.96*pow(1.0 - saturate(dot(-rd,surface)),5.0);
float rim=4.0*puddle*(1.0 - puddle);
vec3 pool=scene*0.45*(1.0 - 0.4*rim);
pool=mix(pool,mysticSkyCheap(refl),saturate(fresnel + 0.3));
if (moonOn) pool +=moonCol*(0.8*hallowGlint(rd,hit,0.12,0.06));
scene=mix(scene,pool,puddle);
}
}
if (lake > 0.0 && MoodDetail[4].y > 0.0) {
vec2 rip=vec2(hallowNoise(hit.xz*1.3 + vec2(Time*0.3,0.0)),hallowNoise(hit.xz*1.3 + vec2(5.0,-Time*0.25)))*0.05;
vec3 surface=normalize(vec3(rip.x,1.0,rip.y));
vec3 refl=reflect(rd,surface);
refl.y=abs(refl.y);
float fresnel=0.04 + 0.96*pow(1.0 - saturate(dot(-rd,surface)),5.0);
float mirror=lake*MoodDetail[4].y;
scene=mix(scene,mysticSkyCheap(refl)*0.9,saturate(fresnel + 0.15)*mirror);
if (moonOn) scene +=moonCol*(0.8*hallowGlint(rd,hit,0.18,0.05)*mirror);
}
float litter=Ground*up*(1.0 - lake)*(1.0 - leaf)*(1.0 - smoothstep(0.0,0.25,selfLit))*roofless
*(snowy?0.0:1.0)*(1.0 - smoothstep(36.0,48.0,dist));
if (litter > 0.0) {
vec2 pattern=groundPatternXZ(hit);
float heap=smoothstep(-0.3,0.5,hallowNoise(pattern*0.18));
float share=saturate(Ground*(0.08 + 0.85*heap*heap));
float detail=MoodDetail[6].w > 1.5?14.0:(MoodDetail[6].w > 0.5?10.0:0.0);
float shapes=(1.0 - smoothstep(detail - 3.0,detail,dist))*(1.0 - smoothstep(0.018,0.046,groundPixel));
float light=luminance(scene)/0.24;
vec3 average=vec3(0.33,0.20,0.09)*light;
float expected=share*0.16;
vec3 tinted=mix(scene,average,expected*0.9)*(1.0 - 0.08*expected);
if (shapes > 0.0) {
float contact;
vec4 leaves=hallowLeaves(pattern,share,max(2.0*groundPixel,0.001),contact);
vec3 lying=mix(scene*contact,leaves.rgb*light,leaves.a);
scene=mix(tinted,lying,shapes);
} else {
scene=tinted;
}
}
base=scene;
wet=vec2(damp,puddle);
float wrapLit=saturate((dot(normal,LightDir) + 0.15)/1.15);
float cloudShade=1.0;
if (MoodDetail[5].x > 0.0 && MoodDetail[1].w > 0.0 && moonOn && MoodDetail[6].w > 0.5) {
vec2 at=hallowCloudUv(normalize(SunDir)) + (hit.xz - CamPos.xz)*(0.9*2.4/250.0);
cloudShade=1.0 - 0.6*MoodDetail[5].x*hallowClouds(at,2,false,1.0);
}
vec3 key=1.0 + MoodDetail[0].x*near*(LightGain*wrapLit*cloudShade - 0.32*(1.0 - wrapLit));
float shade=MoodDetail[6].w > 0.5?MoodDetail[0].z*near*(1.0 - selfLit):0.0;
float ao=shade > 0.0?ambientOcclusion(uv,hit,normal,dist):0.0;
vec3 cold=mix(vec3(1.0),ShadowTint/max(luminance(ShadowTint),0.05)*0.4,
saturate(shade*pow(max(ao,0.0),0.6)*1.6));
scene*=mix(key*cold,vec3(1.0),selfLit);
float bearing;
float flash=hallowFlash(bearing);
if (flash > 0.0) {
vec3 toFlash=normalize(vec3(cos(bearing),0.35,sin(bearing)));
scene +=min(albedo,vec3(1.0))*vec3(0.8,0.85,1.0)
*(flash*0.1*max(dot(normal,toFlash),0.0)*indoor);
}
sceneLum=luminance(scene);
}
vec3 mysticNight(vec3 scene,float crown) {
float l=luminance(scene);
float s=MoodDetail[0].y*(1.0 - smoothstep(0.015,0.16,l))*(1.0 - 0.6*crown);
return mix(scene,l*vec3(0.78,0.90,1.18),s*0.75);
}
float mysticLampFog(vec3 lampAt) {
float amount=MoodDetail[3].x;
if (amount <=0.0) return 1.0;
float height=MoodDetail[3].y;
float y=CamPos.y + lampAt.y;
float density=amount*saturate((MoodDetail[3].z + height - y)/(0.6*height) + 0.5)
*(1.0 - 0.9*MoodDetail[4].w);
return 1.0 + 2.0*density;
}
uint lampBits(int i) {
return uint(round(Lamps[2*i + 1].w*16777216.0));
}
vec3 lampAlbedo(vec3 c) {
float peak=max(c.r,max(c.g,c.b));
return c*min(2.5,0.8/max(peak,0.001)) + 0.015;
}
vec3 lampLight(vec3 at,vec3 normal) {
vec3 sum=vec3(0.0);
for (int i=0;i < 16;++i) {
if (i >=LampCount) break;
vec4 lamp=Lamps[2*i];
vec3 toLamp=lamp.xyz - at;
float d2=dot(toLamp,toLamp);
float r2=lamp.w*lamp.w;
if (d2 >=r2) continue;
float facing=saturate((dot(normal,toLamp)*inversesqrt(max(d2,0.0001)) + 0.2)/1.2);
float x=d2/r2;
float window=(1.0 - x*x)*(1.0 - x*x);
sum +=Lamps[2*i + 1].rgb*(facing*window/(d2 + 0.5));
}
return sum;
}
float lampRank(int i,int n,float d) {
int wisps=int(MoodDetail[6].x + 0.5);
if (i < wisps) return 1.0;
n +=wisps;
if (i >=n) return 0.0;
return n < LampCount?saturate(length(Lamps[2*n].xyz) - d):1.0;
}
vec3 mysticLampLight(vec3 scene,vec3 base,vec2 wet,vec3 at,vec3 normal,vec3 rd,float dist) {
float level=max(MoodDetail[5].w,0.03);
float shade=normal.y > 0.5?1.0:(normal.y < -0.5?0.5:(abs(normal.z) > abs(normal.x)?0.8:0.6));
vec3 albedo=base/(level*shade);
float own=max(albedo.r,max(albedo.g,albedo.b));
albedo=clamp(albedo,0.0,1.2)*smoothstep(1.8,1.1,own);
float indoor=1.0 - MoodDetail[4].w*(1.0 - smoothstep(4.0,10.0,dist));
bool glints=wet.x > 0.0 && normal.y > 0.9;
vec3 diffuse=vec3(0.0);
vec3 spec=vec3(0.0);
for (int i=0;i < 16;++i) {
if (i >=LampCount) break;
vec4 lamp=Lamps[2*i];
vec3 toLamp=lamp.xyz - at;
float d2=dot(toLamp,toLamp);
float r2=lamp.w*lamp.w;
if (d2 >=r2) continue;
vec3 colour=Lamps[2*i + 1].rgb;
float peak=max(colour.r,max(colour.g,colour.b));
float x=d2/r2;
float window=(1.0 - x*x)*(1.0 - x*x);
float atten=window/(d2 + 0.5) - 0.02/max(peak,0.02);
if (atten <=0.0) continue;
vec3 l=toLamp*inversesqrt(max(d2,0.0001));
float facing=saturate((dot(normal,l) + 0.05)/1.05);
if (facing <=0.0) continue;
diffuse +=colour*(facing*atten);
float glint=glints?lampRank(i,8,length(lamp.xyz)):0.0;
if (glint > 0.0) {
vec3 h=normalize(l - rd);
float nh=saturate(dot(normal,h));
float rough=mix(0.35,0.08,saturate(wet.y));
float a2=rough*rough*rough*rough;
float k=nh*nh*(a2 - 1.0) + 1.0;
float ggx=min(a2/(3.14159265*k*k),60.0);
spec +=colour*(ggx*atten*wet.x*0.6*glint*saturate(dot(normal,l)));
}
}
return scene + (albedo*diffuse + spec)*indoor;
}
const float LAMP_HALO=10.0;
vec3 lampGlow(vec3 rd,vec3 eyeOffset,float dist,float sigma,float thickness) {
float k=2.0/max(thickness,1.0);
vec3 sum=vec3(0.0);
for (int i=0;i < 16;++i) {
if (i >=LampCount) break;
vec4 lamp=Lamps[2*i];
vec3 at=lamp.xyz - eyeOffset;
float along=dot(at,rd);
float visibility=visibleBeforeSurface(along,dist,0.25);
if (visibility <= 0.0) continue;
float h2=max(dot(at,at) - along*along,0.0);
float r2=lamp.w*lamp.w;
if (h2 >=r2) continue;
float chord=sqrt(r2 - h2);
float enter=max(along - chord,0.0) - along;
float leave=min(along + chord,dist) - along;
if (leave <=enter) continue;
float soft=sqrt(h2 + 0.12);
float seen=(atan(leave/soft) - atan(enter/soft))/soft;
float air=exp(-clamp(k*max(CamPos.y + lamp.y - (GroundY - 2.0),0.0),0.0,60.0));
if (Kind==6) {
air*=mysticLampFog(lamp.xyz);
}
float edge=1.0 - h2/r2;
sum +=Lamps[2*i + 1].rgb*(seen*air*edge*edge*visibility);
}
return sum*sigma*LAMP_HALO;
}
float snowFrostCover(vec2 corner,float knob,out float crystal) {
    crystal=0.0;
    float reach=saturate(knob*2.2)*(0.9 + 0.1*sin(Time*0.21));
    float creep=max(knob - 0.45,0.0)*0.6;
    float edge=smoothstep(0.42 - creep,1.05 - creep,length(corner))*reach;
    float rise=edge*1.4 - 0.25;
    if (rise + 0.5*reach <=0.35) return 0.0;
    vec2 angDir=normalize(corner + vec2(0.0001));
    float fern=abs(snoise(vec3(corner*11.0 + angDir*1.4,3.7)));
    float facet=1.0 - abs(snoise(vec3(corner*34.0,9.1)));
    crystal=(1.0 - fern)*0.55 + facet*0.45 - 0.5;
    if (rise + crystal*0.5*reach <=0.35) return 0.0;
    return smoothstep(0.35,0.9,saturate(rise + crystal*0.5*reach));
}
vec3 snowFrost(vec3 color,vec2 corner) {
    if (SnowCover <=0.0) return color;
    float crystal;
    float frost=snowFrostCover(corner,MoodDetail[0].x,crystal);
    if (frost <=0.0) return color;
    float air=luminance(HazeColor);
    vec3 frosted=mix(color,HazeColor*1.08,0.65) + vec3(crystal*0.11*air);
    return mix(color,frosted,frost*0.9);
}
vec3 dustGlare(vec3 color,vec2 corner,vec2 uv,float aspect,float mu,float sunOnScreen) {
    float glare=sunOnScreen*saturate(Vignette*2.2);
    if (glare > 0.001) {
        vec2 sunC=(SunScreen.xy - 0.5)*vec2(mix(1.0, aspect, 0.65), 1.0);
        vec3 flare=vec3(0.0);
        for (int i=0;i < 4;++i) {
            vec2 off=(corner - sunC*GHOST_AT[i])/GHOST_R[i];
            float dg=mix(length(off), max(abs(off.x)*0.8660254 + abs(off.y)*0.5, abs(off.y)), 0.72);
            float disc=smoothstep(1.0,0.68,dg)*(0.58 + 0.42*smoothstep(0.55,0.94,dg));
            flare +=SkySun*GHOST_TINT[i]*(disc*GHOST_GAIN[i]);
        }
        float nearSun=pow(saturate(mu*0.5 + 0.5),24.0);
        vec2 glass=uv*vec2(aspect,1.0);
        float specks=lensSpecks(glass*22.0,0.3) + lensSpecks(glass*57.0 + 3.7,0.2)*0.6;
        flare +=SkySun*(specks*nearSun*0.6);
        color=mix(color,color*0.85 + SkySun*0.15,glare*0.25);
        color +=flare*glare;
    }
    return color;
}
vec3 mysticWispCores(vec3 color,vec3 rd,vec3 eyeOffset,float dist) {
float wisps=MoodDetail[6].x;
if (wisps < 0.5) return color;
float swell=1.0 + 2.0*MoodDetail[3].x;
float reach2=4.0*swell*swell;
for (int i=0;i < 4;++i) {
if (float(i) >=wisps || i >=LampCount) break;
vec3 at=Lamps[2*i].xyz - eyeOffset;
float t0=dot(at,rd);
if (t0 <=0.05 || dot(at,at) - t0*t0 > reach2) continue;
float seen=visibleBeforeSurface(t0,dist,0.20);
if (seen <=0.0) continue;
vec3 off=rd*t0 - at;
off.y*=0.75;
float h2=dot(off,off);
float rc=max(0.08,1.5*t0*PixelWorld);
float heart=exp(-h2/(rc*rc));
float aura=exp(-h2/0.1)*0.45;
float glow=exp(-h2/(1.5*swell*swell))*0.1;
float sum=heart*2.2 + aura + glow;
if (sum < 0.002) continue;
vec3 tint=Lamps[2*i + 1].rgb*1.8;
vec3 hot=mix(tint,vec3(max(luminance(tint),0.0)*1.6),heart*0.75);
color=screenBlend(color,saturate3(hot*(sum*seen)));
}
return color;
}
vec3 mysticSpectre(vec3 color,vec3 ro,vec3 rd,vec3 eyeOffset,float dist) {
float alpha=MoodDetail[7].w;
if (alpha <=0.0) return color;
vec3 base=MoodDetail[7].xyz - eyeOffset;
float height=max(MoodDetail[6].z,0.5);
float a=dot(rd.xz,rd.xz);
if (a < 1.0e-6) return color;
float tc=dot(rd.xz,base.xz)/a;
float off2=max(dot(base.xz,base.xz) - tc*tc*a,0.0);
if (tc <=0.0 || off2 > 0.16 || tc >= dist) return color;
float y=(rd.y*tc - base.y)/height;
if (y < 0.0 || y > 1.02) return color;
vec3 forward=atmosphereDirection(vec2(0.5),eyeOffset);
alpha*=1.0 - smoothstep(0.906,0.990,dot(forward,normalize(base + vec3(0.0,0.55*height,0.0))));
if (alpha <=0.001) return color;
float seed=MoodDetail[6].y*61.0;
vec3 at=ro + rd*tc;
float stir=snoise(vec3(at.xz*1.9,at.y*1.2 - Time*0.55 + seed));
float cloakRipple=0.045*sin(y*16.0 - Time*2.2 + seed) + 0.03*cos(atan(at.z - ro.z, at.x - ro.x)*5.0);
float hemFringe=smoothstep(0.0,0.22,y + 0.08*sin(atan(rd.z,rd.x)*9.0 + Time*1.5 + seed));
float width=mix(0.30,0.15,smoothstep(0.06,0.52,y)) + 0.11*smoothstep(0.48,0.68,y)
- 0.18*smoothstep(0.72,0.82,y) + cloakRipple;
width*=1.0 + 0.24*stir;
float body=2.0*sqrt(max(width*width - off2,0.0))*step(y,0.82);
vec3 toHead=base + vec3(0.0,0.88*height,0.0);
float along=dot(toHead,rd);
float headR=0.120*(1.0 + 0.10*stir);
float head=2.0*sqrt(max(headR*headR - (dot(toHead,toHead) - along*along),0.0));
float through=(body + head)/sqrt(a);
float shown=(1.0 - exp(-2.35*through*(0.75 + 0.25*stir)))*hemFringe
*visibleBeforeSurface(tc,dist,0.20);
vec3 fog=gradeColor(HazeColor*1.1 + SkySun*0.2);
return mix(color,fog,shown*alpha*0.6);
}
GroundFootprint groundFootprint(vec3 rd,vec3 rayDx,vec3 rayDy,float dist) {
float ry=rd.y < 0.0?min(rd.y,-0.015):max(rd.y,0.015);
vec2 planeDx=rayDx.xz - rd.xz*(rayDx.y/ry);
vec2 planeDy=rayDy.xz - rd.xz*(rayDy.y/ry);
float x=dot(vec2(planeDx.x,planeDy.x),vec2(planeDx.x,planeDy.x));
float y=dot(vec2(planeDx.y,planeDy.y),vec2(planeDx.y,planeDy.y));
float xy=planeDx.x*planeDx.y + planeDy.x*planeDy.y;
float gap=sqrt((x - y)*(x - y) + 4.0*xy*xy);
float major=0.5*(x + y + gap);
float minor=0.5*max(x + y - gap,0.0);
float width=dist*sqrt(major);
float thin=dist*sqrt(minor);
float split=smoothstep(1.30,2.40,width/max(thin,0.0001))*smoothstep(0.004,0.012,width);
vec2 axis=x >= y?vec2(major - y,xy):vec2(xy,major - x);
axis/=max(length(axis),0.0000001);
return GroundFootprint(0.5*width,max(0.001,max(thin,width*(1.0 - 0.50*split))),axis*(0.24*width*split));
}
void main() {
vec2 uv=texCoord;
float aspect=Resolution.x/max(Resolution.y,1.0);
float depth=textureLod(Sampler1,uv,0.0).r;
bool isSky=depthIsFar(depth);
vec3 eyeOffset=atmosphereEyeOffset();
vec3 ro=CamPos + eyeOffset;
vec3 rd=atmosphereDirection(uv,eyeOffset);
vec3 offset=isSky?rd*4000.0:relativePosFromDepth(uv,depth) - eyeOffset;
vec3 hit=ro + offset;
float skyMask=isSky?1.0:0.0;
float dist=length(offset);
float viewZ=1.0/max(unprojectAtmosphere(uv,depth).w,0.000001);
float up=saturate(rd.y);
GroundFootprint footprint=GroundFootprint(0.0,0.001,vec2(0.0));
if (Kind==3 || Kind==4 || Kind==6) {
footprint=groundFootprint(rd,dFdx(rd),dFdy(rd),dist);
}
float groundPixel=footprint.radius;
vec3 sun=normalize(SunDir);
float mu=dot(rd,sun);
float sunOn=saturate(luminance(SkySun)*4.0);
float thickness=hazeThickness();
float integ=heightIntegral(ro,rd,min(dist,400.0),GroundY - 2.0,thickness);
float sigma=Haze*0.012;
float farHaze=smoothstep(ViewDistance*0.55,ViewDistance*0.98,dist)*(1.0 - skyMask);
float optical=sigma*integ*(1.0 + 0.6*farHaze);
if (Kind==4) optical=optical*0.72 + autumnMistDepth(ro,rd,dist);
float fogAmount=saturate(1.0 - exp(-optical));
fogAmount*=mix(1.0,1.0 - 0.85*pow(up,0.6),skyMask);
vec2 sampleUv=uv;
if (Shimmer > 0.0 && !isSky) {
float low=1.0 - smoothstep(-0.08,0.35,rd.y);
float farness=smoothstep(3.0,28.0,dist);
float n=snoise(vec3(uv.x*16.0*aspect,uv.y*16.0 - Time*Speed*0.8,Time*0.3));
vec2 shifted=uv + vec2(n*0.3,n)*(0.007*Shimmer*low*farness);
shifted=clamp(shifted,vec2(0.001),vec2(0.999));
sampleUv=mix(uv,shifted,depthMatch(shifted,depth,viewZ));
}
float softPx=3.5*saturate(fogAmount*1.4)*(1.0 - skyMask);
vec3 scene=sampleScene(sampleUv,Kind==1?softPx*0.6:softPx,depth,viewZ);
float sceneLum=luminance(scene);
float near=1.0 - smoothstep(ViewDistance*0.65,ViewDistance*1.0,dist);
bool farIdle=near <=0.0 && (Kind==0 || Kind==5) && LampCount==0;
if (!isSky && !farIdle && (Relight > 0.0 || Occlusion > 0.0 || SnowCover > 0.0 || LampCount > 0)) {
ivec2 groundCell=columnCell(hit.xz);
vec4 groundColumn=ncColumnAt(Sampler3,groundCell);
float groundPlane=supportPlaneWeight(hit,groundCell,groundColumn,dist*PixelWorld);
vec3 groundNormal=vec3(0.0,1.0,0.0);
vec3 normal=groundPlane > 0.999?groundNormal:normalize(mix(surfaceNormal(uv,hit,depth,rd),groundNormal,groundPlane));
if (groundPlane > 0.999) hit=exactSupportHit(hit,ro,rd,groundCell,groundColumn);
vec3 materialColor=textureLod(Sampler0,uv,0.0).rgb;
float water=waterAt(hit,normal,materialColor,dist,groundPlane);
vec3 mysticBase=vec3(0.0);
vec2 mysticWet=vec2(0.0);
float mysticCrown=0.0;
if (Kind==3) sakuraSurface(scene,sceneLum,materialColor,hit,normal,rd,sun,dist,water,groundPixel);
else if (Kind==4) autumnSurface(scene,sceneLum,materialColor,hit,normal,rd,sun,dist,water,groundPixel);
else if (Kind==6) mysticSurface(scene,sceneLum,mysticBase,mysticWet,mysticCrown,uv,hit,normal,rd,dist,near,water,groundPixel);
float facing=max(dot(normal,normalize(LightDir)),0.0);
vec3 light=1.0 + Relight*near*(LightGain*facing - 0.30*(1.0 - facing));
scene*=light;
float occluded=0.0;
if (Occlusion > 0.0 && near > 0.0) {
occluded=ambientOcclusion(uv,hit,normal,dist);
scene*=1.0 - Occlusion*near*occluded*0.75;
}
if (SnowCover > 0.0 && Ground > 0.0) {
if (Kind==1) snowSettle(scene,sceneLum,uv,hit,normal,rd,dist,near,water,occluded);
else if (Kind==3) sakuraSettle(scene,sceneLum,hit,dist,water,groundPixel);
else if (Kind==4) autumnSettle(scene,sceneLum,hit,normal,dist,water,luminance(materialColor),footprint);
}
if (LampCount > 0) {
if (dist < LampReach) {
if (Kind==6) scene=mysticLampLight(scene,mysticBase,mysticWet,hit - CamPos,normal,rd,dist);
else scene +=lampAlbedo(scene)*lampLight(hit - CamPos,normal);
}
sceneLum=luminance(scene);
}
if (Kind==6) {
scene=mysticNight(scene,mysticCrown);
sceneLum=luminance(scene);
}
}
vec3 skyCol=isSky?moodSky(rd,mu):vec3(0.0);
vec4 skyOver=Kind==4 && isSky?autumnSkyOver(rd,mu):vec4(0.0);
vec3 color=mix(scene,skyCol,skyMask*SkyMix);
if (skyOver.a > 0.0) color=mix(color,skyOver.rgb,skyOver.a*skyMask);
vec3 glow=vec3(0.0);
if (Lights > 0.001) {
vec4 bokeh=textureLod(Sampler2,uv,0.0);
float sourceDist=BokehFocus*bokeh.a/max(1.0 - bokeh.a,0.0001);
float seen=isSky?1.0:1.0 - smoothstep(max(0.20,dist*0.025),max(0.55,dist*0.07),sourceDist - dist);
glow=bokeh.rgb*Lights*seen;
}
vec3 hazeTint=HazeColor*(0.85 + 0.15*saturate(rd.y + 0.5));
float hazeSun=sunOn > 0.0?henyeyGreenstein(mu,0.7)*0.035*sunOn:0.0;
hazeTint +=SkySun*hazeSun;
hazeTint +=glow*1.5;
color=mix(color,hazeTint,fogAmount);
float hallowFog=0.0;
if (Kind==6) {
vec4 mist=mysticMist(ro,rd,dist,hazeTint);
color=mix(color,mist.rgb,mist.a);
hallowFog=mist.a;
}
else if (Kind==1) color=snowAir(color,ro,rd,dist,sigma,thickness,fogAmount);
else if (Kind==3) color=mix(color,mix(hazeTint,vec3(0.62,0.64,0.80),0.4),sakuraMist(ro,rd,dist));
if (LampCount > 0) color +=lampGlow(rd,eyeOffset,dist,sigma,thickness);
float shaft=1.0;
float sunRad=length(SunScreen.xy - vec2(0.5));
float sunOnScreen=SunScreen.z*(1.0 - smoothstep(1.1,2.6,sunRad));
if (sunOnScreen > 0.001 && (Rays > 0.001 || Kind==0)) {
sunOnScreen*=skySourceVisibility(SunScreen.xy,3.0/Resolution);
}
if (Rays > 0.001 && sunOnScreen > 0.001 && sunOn > 0.0) {
float jitter=hash12(gl_FragCoord.yx + vec2(17.0,59.0));
float mask=godRaysMask(uv,SunScreen.xy,jitter);
float angular=pow(saturate(mu*0.5 + 0.5),3.0);
float texture3=0.75 + 0.25*snoise(vec3(rd*6.0 + vec3(0.0,Time*0.1,0.0)));
float rays=mask*angular*sunOnScreen*Rays*texture3;
float foregroundFog=Kind==6?saturate(fogAmount*2.0 + hallowFog):fogAmount;
rays*=mix(foregroundFog,1.0,skyMask);
shaft=mix(1.0,0.35 + 0.9*mask*angular,sunOnScreen);
float dy=rd.y - sun.y;
float angSq=max(2.0*(1.0 - mu),0.0);
float disc=Kind==6?0.0:exp(-angSq*2600.0);
float dx2=max(angSq - dy*dy,0.0);
float streak=exp(-(dy*dy*850.0 + dx2*34.0));
vec3 sunGlow=SkySun*(disc + streak*0.35)*skyMask;
sunGlow*=sunOnScreen*Rays;
color=screenBlend(color,SkySun*rays*0.8 + sunGlow);
}
color=mix(color,gradeColor(color),Grade);
if (Lights > 0.001) color=screenBlend(color,glow);
if (Kind==6) {
color=mysticWispCores(color,rd,eyeOffset,dist);
color=mysticSpectre(color,ro,rd,eyeOffset,dist);
}
if (Amount > 0.001) {
if (Kind==3) {
color +=hotaru(ro,rd,dist);
} else {
float lit=0.3 + 0.7*sceneLum;
color +=particles(ro,rd,dist,sun,lit,shaft);
}
}
vec2 corner=(uv - 0.5)*vec2(mix(1.0,aspect,0.65),1.0);
if (Kind==1) color=snowFrost(color,corner);
else if (Kind==0 && sunOnScreen > 0.001 && sunOn > 0.0)
color=dustGlare(color,corner,uv,aspect,mu,sunOnScreen);
color*=1.0 - Vignette*smoothstep(0.4,1.25,length(corner)*1.35);
float grain=hash12(floor(gl_FragCoord.xy) + vec2(17.0,74.0)) - 0.5;
color +=grain*(Kind==6?MoodDetail[2].z*0.035:0.006)*(1.2 - saturate(luminance(color)));
float outLum=luminance(color);
color/=1.0 + max(outLum - 0.985,0.0)*1.6;
OutColor=vec4(saturate3(color),1.0);
}
