vec4 autumnGroundFragment(float vignette) {
    vec2 p = precipUv;
    float seed = precipLook.x;
    float type = precipLook.y;
    float species = type < 0.5 ? 0.25 : (type < 1.5 ? 0.60 : 0.86);
    p.x += precipAutumn.z * 0.22 * (p.y * p.y - 0.3);

    vec2 dx = dFdx(p), dy = dFdy(p);
    vec2 diagonal = dx * dx + dy * dy;
    float xy = dx.x * dx.y + dy.x * dy.y;
    float gap = length(vec2(diagonal.x - diagonal.y, 2.0 * xy));
    float major = 0.5 * (diagonal.x + diagonal.y + gap);
    float minor = 0.5 * max(diagonal.x + diagonal.y - gap, 0.0);
    float width = sqrt(major);
    float thin = sqrt(minor);
    float split = smoothstep(1.30, 2.40, width / max(thin, 0.0001));
    vec2 axis = diagonal.x >= diagonal.y ? vec2(major - diagonal.y, xy) : vec2(xy, major - diagonal.x);
    vec2 tap = axis * (0.24 * width * split / max(length(axis), 0.000001));
    float px = max(thin, width * (1.0 - 0.5 * split));
    float bladeA = botanicalLeafBlade(p - tap, species);
    float bladeB = botanicalLeafBlade(p + tap, species);
    float stemA = botanicalLeafStem(p - tap);
    float stemB = botanicalLeafStem(p + tap);
    float coverage = 0.5 * (botanicalGroundCoverage(bladeA, stemA, px) + botanicalGroundCoverage(bladeB, stemB, px));
    float distance = min(0.5 * (bladeA + bladeB), 0.5 * (stemA + stemB) - 0.026);
    vec3 pigment = autumnLeafPalette(seed) * (0.87 + 0.22 * fract(seed * 7.7));
    vec3 leaf = pigment * precipAutumn.x * botanicalLeafShade(p, species, distance, seed, width);
    float fog = 1.0 - exp(-precipAutumn.y * FallDetail[6].w);
    return vec4(mix(leaf, FallDetail[6].rgb, fog) * vignette, coverage * precipLook.z);
}

void autumnFragment() {
    vec2 uv = clamp((precipClip.xy / max(abs(precipClip.w), 0.0001)) * 0.5 + 0.5, vec2(0.001), vec2(0.999));
    vec2 corner = (uv - 0.5) * vec2(mix(1.0, Resolution.x / max(Resolution.y, 1.0), 0.65), 1.0);
    float vignette = 1.0 - Vignette * smoothstep(0.4, 1.25, length(corner) * 1.35);
    if (precipLook.y < 2.5 && precipLook.w > 0.5) {
        OutColor = autumnGroundFragment(vignette);
        return;
    }
    bool thread = precipLook.y > 2.5;
    float daylight = FallDetail[1].w;
    float air = thread ? dot(texture(Scene, uv).rgb, vec3(0.2126, 0.7152, 0.0722))
        : mix(0.12, 0.35, daylight) * mix(0.65, 1.0, precipAutumn.w);
    vec3 sun = FallDetail[2].rgb;
    float day = (thread ? smoothstep(0.04, 0.35, air) : daylight) * precipAutumn.w;
    float px = max(fwidth(precipUv.x), fwidth(precipUv.y));
    if (thread) {
        float s = precipUv.x;
        float glintAt = precipAutumn.x + precipAutumn.y * s;
        float glint = exp(-12.0 * glintAt * glintAt) * (0.2 + 0.8 * precipLook.w);
        vec3 iris = 0.8 + 0.2 * cos(6.2831853 * (s * 0.4 + precipLook.x + vec3(0.0, 0.33, 0.67)));
        float alpha = (1.0 - smoothstep(0.2, 1.0, abs(precipUv.y))) * (1.0 - smoothstep(0.75, 1.0, abs(s)))
            * precipLook.z * (0.1 + 0.2 * precipLook.w + 0.8 * glint * day) * min(1.0, 0.6 + precipAutumn.z);
        if (alpha < 0.004) discard;
        OutColor = vec4(mix(vec3(0.85, 0.85, 0.82) * (0.4 + 0.6 * air), sun * iris * 1.3, glint * day) * vignette, alpha);
        return;
    }
    float type = precipLook.y;
    float species = type < 0.5 ? 0.25 : (type < 1.5 ? 0.60 : 0.86);
    float seed = precipLook.x;
    float curl = precipAutumn.z;
    vec2 p = precipUv;
    p.x += curl * 0.22 * (p.y * p.y - 0.3);
    float simple = spriteEllipse(p, vec2(0.48, 0.82));
    float detail = FallDetail[3].w > 0.5 ? 1.0 - smoothstep(0.22, 0.52, px) : 0.0;
    float shape = detail > 0.0 ? mix(simple, botanicalLeaf(p, species), detail) : simple;
    float alpha = spriteMask(shape, px) * precipLook.z * 0.96;
    if (alpha < 0.004) discard;

    float tone = type < 0.5 ? seed * 0.59 : (type < 1.5 ? 0.56 + seed * 0.44 : 0.42 + seed * 0.43);
    vec3 albedo = autumnLeafPalette(tone);
    if (detail > 0.0) albedo *= mix(1.0, botanicalLeafShade(p, species, shape, seed, px), detail);
    albedo *= 1.0 + 0.16 * curl * p.x;
    float ambient = 0.26 + 0.58 * smoothstep(0.0, 0.60, air);
    vec3 color = albedo * (ambient + sun * (0.78 * precipAutumn.x * day));
    float through = precipAutumn.y * day * FallDetail[2].w;
    color += albedo * albedo * sun * (1.9 * through);
    OutColor = vec4(color * vignette, alpha);
}

vec4 flakeLook(vec2 o) {
    float pixels = precipLook.w;
    float dCirc = length(o);
    float crystalBlend = smoothstep(4.0, 9.0, pixels);
    float alphaSoft = (1.0 - smoothstep(0.19, 0.82, dCirc)) * 0.85;
    if (crystalBlend <= 0.01) {
        return vec4(Tint, alphaSoft);
    }
    float spin = Time * (0.35 + 0.85 * precipLook.x) + precipLook.x * 6.2831853;
    float cs = cos(spin), sn = sin(spin);
    vec2 p = vec2(cs * o.x - sn * o.y, sn * o.x + cs * o.y);
    float a = atan(p.y, p.x + 0.00001);
    const float sec = 1.04719755;
    float fa = abs(mod(a + 0.5 * sec, sec) - 0.5 * sec);
    vec2 q = vec2(cos(fa), sin(fa)) * length(p);
    float spine = spriteSegment(q, vec2(0.0), vec2(0.89, 0.0)) - mix(0.064, 0.032, q.x);
    float branch = spriteSegment(q, vec2(0.35, 0.0), vec2(0.60, 0.275)) - 0.041;
    float tip = spriteSegment(q, vec2(0.62, 0.0), vec2(0.79, 0.16)) - 0.034;
    float core = q.x - 0.19;
    float crystal = min(min(spine, branch), min(tip, core));
    float pixel = max(fwidth(o.x), fwidth(o.y));
    float alphaCrystal = spriteMask(crystal, pixel) * (0.96 - 0.20 * smoothstep(0.22, 0.90, dCirc));
    float alpha = mix(alphaSoft, alphaCrystal, crystalBlend);
    float glimmer = (1.0 - smoothstep(0.12, 0.65, dCirc)) * crystalBlend;
    return vec4(mix(Tint, vec3(0.99, 0.99, 1.0), 0.36 * glimmer), alpha);
}

float vignetteAt(vec2 uv) {
    vec2 corner = (uv - 0.5) * vec2(mix(1.0, Resolution.x / max(Resolution.y, 1.0), 0.65), 1.0);
    return 1.0 - Vignette * smoothstep(0.4, 1.25, length(corner) * 1.35);
}

const float HALLOW_PI = 3.14159265;
float hallowHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + vec3(33.33));
    return fract((p3.x + p3.y) * p3.z);
}
float hallowNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hallowHash(i), hallowHash(i + vec2(1.0, 0.0)), f.x),
        mix(hallowHash(i + vec2(0.0, 1.0)), hallowHash(i + vec2(1.0, 1.0)), f.x),
        f.y);
}
vec4 hallowLeafLook(vec2 p) {
    float seed = hallowLook.y;
    float pixels = precipLook.y;
    float species = fract(seed * 3.71);
    float pixel = max(fwidth(p.x), fwidth(p.y));
    vec3 albedo = witheredLeafPalette(fract(seed * 5.13));
    float detail = smoothstep(2.0, 6.0, pixels);
    p.x += 0.12 * (2.0 * seed - 1.0) * p.y;
    float distance = spriteEllipse(p, vec2(0.48, 0.82));
    if (detail > 0.0) {
        float leaf = witheredLeaf(p, species, seed);
        distance = mix(distance, leaf, detail);
        albedo *= mix(1.0, botanicalLeafShade(p, species, distance, seed, pixel), detail);
    }
    float edge = spriteMask(abs(distance) - 0.05, pixel) * detail;
    vec3 color = albedo * hallowLight;
    color += precipLook.x * (vec3(0.40, 0.45, 0.53) * edge + 0.17 * albedo);
    return vec4(color, spriteMask(distance, pixel) * 0.95);
}

vec4 hallowBatLook(vec2 o) {
    float u = o.x;
    float v = o.y;
    float detail = smoothstep(2.0, 5.0, precipLook.y);
    float silhouette = (1.0 - smoothstep(0.15, 0.83, abs(v))) * (1.0 - smoothstep(0.72, 1.0, u));
    vec3 shadow = vec3(0.044, 0.033, 0.041);
    if (detail <= 0.0) return vec4(shadow, silhouette * 0.88);
    const vec2 WRIST = vec2(0.42, 0.54);
    const vec2 TIP = vec2(1.0, 0.06);
    float s;
    float lead;
    if (u < WRIST.x) {
        s = u / WRIST.x;
        lead = mix(0.38, WRIST.y, s) + 0.08 * sin(HALLOW_PI * s);
    } else {
        s = (u - WRIST.x) / (1.0 - WRIST.x);
        lead = mix(WRIST.y, TIP.y, pow(s, 0.88)) + 0.04 * sin(HALLOW_PI * s);
    }
    float trail;
    if (u < 0.42) {
        s = u / 0.42;
        trail = mix(-0.62, -0.56, s) + 0.19 * pow(max(sin(HALLOW_PI * s), 0.0), 0.8);
    } else if (u < 0.72) {
        s = (u - 0.42) / 0.30;
        trail = mix(-0.56, -0.34, s) + 0.15 * pow(max(sin(HALLOW_PI * s), 0.0), 0.8);
    } else {
        s = (u - 0.72) / 0.28;
        trail = mix(-0.34, TIP.y, s) + 0.11 * pow(max(sin(HALLOW_PI * s), 0.0), 0.8);
    }
    float membrane = min(min(lead - v, v - trail), (1.0 - u) * 2.0);
    float body = (1.0 - length(vec2(u / 0.125, (v + 0.10) / 0.52))) * 0.10;
    float head = (1.0 - length(vec2(u / 0.095, (v - 0.48) / 0.135))) * 0.08;
    float ear  = (1.0 - length(vec2((u - 0.055) / 0.034, (v - 0.68) / 0.13))) * 0.045;
    float solid = max(body, max(head, ear));
    float inside = max(membrane, solid);
    float fw = max(fwidth(inside), 0.001);
    float cover = mix(silhouette * 0.88, smoothstep(-fw, fw, inside), detail);
    if (cover <= 0.0) return vec4(0.0);
    vec3 color = shadow * (0.45 + 1.2 * hallowLight);
    float back = precipLook.x;
    if (back > 0.002 && solid < 0.0) {
        float bone = min(
            min(spriteSegment(o, vec2(0.06, 0.38), WRIST), spriteSegment(o, WRIST, TIP)),
            min(spriteSegment(o, WRIST, vec2(0.42, -0.56)), spriteSegment(o, WRIST, vec2(0.72, -0.34))));
        float skin = smoothstep(0.016, 0.060, bone) * smoothstep(0.0, 0.09, membrane);
        skin *= 0.65 + 0.35 * smoothstep(trail, lead, v);
        color += back * skin * detail * vec3(0.43, 0.19, 0.15);
    }
    return vec4(color, cover);
}

vec4 hallowWebLook(vec2 o) {
    float seed = hallowLook.y;
    vec2 p = o - (vec2(fract(seed * 7.1), fract(seed * 3.3)) - 0.5) * 0.3;
    float r = length(p);
    float theta = atan(p.y, p.x + 0.00001);
    float spokes = floor(10.0 + 6.0 * fract(seed * 11.7));
    float sector = (theta / 6.2831853 + 1.0) * spokes + fract(seed * 5.9);
    float sid = floor(sector);
    float f = fract(sector) - 0.5 - (hallowHash(vec2(sid, seed * 29.0)) - 0.5) * 0.25;
    float px = max(fwidth(r), 0.0005);
    float thread = 0.0045;
    float coverage = min(1.0, thread / px);
    float line = px * 0.75 + thread * 0.5;
    float rIn = 0.11;
    float rOut = 0.82 + 0.1 * fract(seed * 2.7);
    float spacing = (rOut - rIn) / 10.0;
    float polygon = r * cos(HALLOW_PI / spokes - abs(f) * 6.2831853 / spokes) / cos(HALLOW_PI / spokes);
    float sag = spacing * 0.65 * (1.0 - 4.0 * f * f) * (p.y / max(r, 0.001));
    float wobble = 0.12 * sin(theta * 3.0 + seed * 40.0) + 0.08 * sin(theta * 7.0 + seed * 13.0);
    float ring = (polygon + sag - rIn) / spacing + theta / 6.2831853 + wobble;
    float toRing = abs(fract(ring + 0.5) - 0.5) * spacing;
    float torn = hallowHash(vec2(sid, seed * 91.0)) < 0.12 || hallowHash(vec2(sid, floor(ring) + seed * 53.0)) < 0.06 ? 1.0 : 0.0;
    float lines = smoothstep(2.5, 4.5, spacing / px);
    float spiral = mix(min(1.0, 2.0 * line / spacing), 1.0 - smoothstep(0.0, line, toRing), lines)
        * smoothstep(rIn - px, rIn + px, polygon) * (1.0 - smoothstep(rOut - px, rOut + px, polygon))
        * (1.0 - torn * lines);
    float toSpoke = abs(f) * 6.2831853 / spokes * r;
    vec2 towards = p / max(r, 0.001);
    float fast = hallowLook.z > 0.5 ? abs(towards.x) : abs(towards.y);
    float anchor = fast > 0.6 && hallowHash(vec2(sid + 0.5, seed * 17.0)) < 0.4 ? 2.0 : rOut + spacing;
    float spoke = (1.0 - smoothstep(0.0, line, toSpoke)) * (1.0 - smoothstep(anchor - px, anchor + px, polygon));
    float frame = 1.0 - smoothstep(0.0, line * 1.6, abs(polygon - rOut - spacing));
    float hub = (1.0 - smoothstep(rIn * 0.6, rIn, r)) * (0.25 + 0.3 * hallowNoise(p * 60.0));
    float bead = 0.0;
    if (fract(seed * 4.3) < 0.5 && lines > 0.0) {
        float along = sector * 3.0 + floor(ring) * 0.37;
        float spot = abs(fract(along) - 0.5) * spacing * 3.0;
        float id = hallowHash(vec2(floor(along), floor(ring)));
        bead = (1.0 - smoothstep(line, line + px, length(vec2(spot, toRing)) - 0.006)) * step(0.55, id) * spiral
            * lines * min(1.0, 0.012 / px);
        float shimmer = 0.5 + 0.5 * sin(precipLook.z * (0.6 + id) + id * 37.0);
        bead *= 0.70 + 0.70 * shimmer * shimmer;
    }
    float cover = max(max(spiral, spoke), max(frame, hub)) * coverage + bead;
    float shown = clamp(dot(hallowLight, vec3(0.2126, 0.7152, 0.0722)) * 5.0, 0.0, 1.0);
    if (cover * shown <= 0.004) return vec4(0.0);
    return vec4(hallowLight / max(shown, 0.05) * (1.0 + bead), clamp(cover * shown, 0.0, 1.0));
}

vec4 halloweenLook(vec2 o) {
    float group = hallowLook.x;
    vec4 look = group < 1.5 ? hallowLeafLook(o) : (group < 3.5 ? hallowBatLook(o) : hallowWebLook(o));
    return vec4(look.rgb, look.a * hallowLook.w);
}

vec4 clumpLook(vec2 o) {
    float pixels = precipLook.w;
    if (pixels < 3.0) return flakeLook(o);
    float spin = Time * (0.3 + 0.4 * precipLook.x) + precipLook.x * 6.2831853;
    float cs = cos(spin), sn = sin(spin);
    o = vec2(cs * o.x - sn * o.y, sn * o.x + cs * o.y);
    float d = min(
        min(length(o - vec2(0.22, 0.10)), length(o + vec2(0.21, 0.07)) * 1.08),
        min(length(o - vec2(-0.04, -0.25)) * 1.15, length(o - vec2(0.03, 0.24)) * 1.25));
    float alpha = (1.0 - smoothstep(0.24, 0.63, d)) * mix(0.92, 0.58, smoothstep(8.0, 30.0, pixels));
    vec3 color = mix(Tint, vec3(1.0), (1.0 - smoothstep(0.13, 0.56, d)) * 0.24);
    return vec4(color, alpha);
}

vec4 driftLook(vec2 o) {
    float along = 1.0 - smoothstep(0.3, 1.0, abs(o.x));
    float across = 1.0 - smoothstep(0.05, 1.0, abs(o.y));
    float strand = o.y - 0.22 * sin(o.x * 2.1 + precipLook.x * 6.2831853);
    float silk = 1.0 - smoothstep(0.02, 0.54, abs(strand));
    return vec4(Tint, along * across * mix(0.35, 1.0, silk) * 0.29);
}

vec4 streakLook(vec2 o) {
    float run = precipLook.y - 3.0;
    vec4 look = flakeLook(vec2(max(abs(o.x) - run, 0.0), o.y));
    look.a /= 1.0 + run * 0.5;
    return look;
}

vec4 snowLook(vec2 o) {
    if (precipLook.y > 2.5) return streakLook(o);
    if (precipLook.y > 1.5) return driftLook(o);
    if (precipLook.y > 0.5) return clumpLook(o);
    return flakeLook(o);
}

float snowFrostEdge(vec2 corner) {
    float knob = FallDetail[1].y;
    float reach = clamp(knob * 2.2, 0.0, 1.0) * (0.9 + 0.1 * sin(Time * 0.21));
    float creep = max(knob - 0.45, 0.0) * 0.6;
    float edge = smoothstep(0.42 - creep, 1.05 - creep, length(corner)) * reach;
    return smoothstep(0.35, 0.9, clamp(edge * 1.4 - 0.25, 0.0, 1.0));
}

void main() {
    vec2 uv = clamp((precipClip.xy / max(abs(precipClip.w), 0.0001)) * 0.5 + 0.5, vec2(0.001), vec2(0.999));
    if (Kind == 6) {
        vec4 hallow = halloweenLook(precipUv);
        if (hallow.a < 0.004) discard;
        OutColor = vec4(hallow.rgb * vignetteAt(uv), hallow.a);
        return;
    }
    if (Kind == 4) {
        autumnFragment();
        return;
    }
    vec4 look;
    if (Kind == 3) {
        float pixel = max(fwidth(precipUv.x), fwidth(precipUv.y));
        vec3 tint = Tint / vec3(1.0, 0.62, 0.80);
        look = precipLook.y > 0.5 ? cherryBlossom(precipUv, precipLook.x, pixel, tint)
            : cherryPetal(precipUv, precipLook.x, pixel, tint);
        look.rgb *= 0.84 + 0.16 * precipLook.w;
    } else {
        look = snowLook(precipUv);
    }
    float alpha = look.a * precipLook.z;
    if (alpha < 0.004) discard;
    vec3 behind = texture(Scene, uv).rgb;
    float lit = 0.4 + 0.6 * dot(behind, vec3(0.2126, 0.7152, 0.0722));
    if (Kind == 1) {
        if (FallDetail[1].w > 0.0) lit = max(min(FallDetail[1].w * 1.1, 1.0), 0.15 + 0.85 * (lit - 0.4) / 0.6);
        alpha *= 1.0 - 0.9 * snowFrostEdge((uv - 0.5) * vec2(mix(1.0, Resolution.x / max(Resolution.y, 1.0), 0.65), 1.0));
    }
    if (Kind == 3) lit = 0.56 + 0.44 * (lit - 0.4) / 0.6;
    OutColor = vec4(look.rgb * lit * vignetteAt(uv), alpha);
}
