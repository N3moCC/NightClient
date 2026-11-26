float csSaturate(float v) {
    return clamp(v, 0.0, 1.0);
}

float csHash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + vec3(33.33));
    return fract((p3.x + p3.y) * p3.z);
}

float csHash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 csHash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float csVnoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = csHash12(i);
    float b = csHash12(i + vec2(1.0, 0.0));
    float c = csHash12(i + vec2(0.0, 1.0));
    float d = csHash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float csFbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        v += a * csVnoise2(p);
        p = rot * p * 2.05 + vec2(17.1, 31.7);
        a *= 0.5;
    }
    return v;
}

vec3 csRotateY(vec3 v, float ang) {
    float c = cos(ang);
    float s = sin(ang);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

float csStarLayer(vec3 dir, float scale, float density, float twinkleTime) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    vec3 f = fract(p) - 0.5;
    vec3 rnd = csHash33(cell);
    float keep = step(1.0 - density, rnd.z);
    vec3 off = (rnd - 0.5) * 0.66;
    vec3 d = f - off;
    float distSq = dot(d, d);
    float size = mix(0.0005, 0.0032, rnd.x * rnd.x);
    float core = exp(-distSq / max(size, 0.0002));
    float twinkle = 0.70 + 0.30 * sin(twinkleTime * (1.8 + rnd.y * 3.4) + rnd.x * 62.83);
    return keep * core * twinkle;
}

vec3 csStarsAndMeteors(vec3 rd, float amount, float t) {
    if (amount <= 0.005) return vec3(0.0);
    float vis = smoothstep(-0.04, 0.22, rd.y);
    if (vis <= 0.001) return vec3(0.0);
    float s1 = csStarLayer(rd, 95.0, 0.14 * amount, t);
    float s2 = csStarLayer(rd, 175.0, 0.20 * amount, t * 1.3);
    vec3 tint1 = mix(vec3(0.78, 0.88, 1.0), vec3(1.0, 0.90, 0.78), csHash13(floor(rd * 95.0)));
    vec3 col = tint1 * s1 * 1.15 + vec3(0.86, 0.92, 1.0) * s2 * 0.62;

    float slot = floor(t * 0.28);
    float phase = fract(t * 0.28);
    if (phase < 0.26 && amount > 0.25) {
        float prog = phase / 0.26;
        vec3 seed = csHash33(vec3(slot, 13.7, 41.3));
        float az = seed.x * 6.2831853;
        float el = mix(0.24, 0.68, seed.y);
        vec3 startDir = normalize(vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el)));
        vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), startDir));
        vec3 up = cross(startDir, right);
        float tilt = mix(-0.75, -0.35, seed.z);
        vec3 travel = normalize(right * cos(tilt) + up * sin(tilt));
        vec3 head = normalize(startDir + travel * (prog * 0.42));
        vec3 toPx = rd - head;
        float along = dot(toPx, -travel);
        float perp = length(toPx + travel * along);
        float trail = smoothstep(0.14, 0.0, along) * step(0.0, along);
        float fade = sin(prog * 3.14159265);
        float streak = exp(-perp * 480.0) * trail * fade * amount;
        col += vec3(0.92, 0.97, 1.0) * streak * 1.8;
    }
    return col * vis;
}

vec3 csAuroraLayer(vec3 rd, vec3 accent, float amount, float t) {
    if (amount <= 0.005 || rd.y < 0.01) return vec3(0.0);
    vec2 skyUv = rd.xz / (rd.y + 0.28);
    vec3 total = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float wave = sin(skyUv.x * (1.25 + fi * 0.42) + t * (0.28 + fi * 0.09) + fi * 2.1) * 0.55
                   + sin(skyUv.x * 2.7 - t * 0.18 + fi) * 0.22;
        float bandDist = abs(skyUv.y - (wave + (fi - 1.0) * 0.72));
        float curtain = exp(-bandDist * (3.6 - fi * 0.45));
        float folds = 0.55 + 0.45 * csFbm2(vec2(skyUv.x * 3.8 + t * 0.16, skyUv.y * 1.4 - t * 0.10 + fi * 5.0));
        float vertical = smoothstep(0.03, 0.26, rd.y) * smoothstep(0.92, 0.25, rd.y);
        vec3 lowCol = mix(vec3(0.10, 0.96, 0.68), accent, 0.45);
        vec3 highCol = mix(vec3(0.56, 0.28, 0.98), accent, 0.35);
        vec3 ribbonCol = mix(lowCol, highCol, csSaturate(rd.y * 1.85 - 0.15 + fi * 0.12));
        total += ribbonCol * curtain * folds * vertical * (0.58 - fi * 0.11);
    }
    return total * amount * 1.15;
}

vec3 csCloudLayer(vec3 rd, vec3 topCol, vec3 horizonCol, vec3 accent, vec3 celDir, int kind, float amount, float t) {
    if (amount <= 0.005 || rd.y <= 0.005) return vec3(0.0);
    vec2 p = rd.xz / (rd.y + 0.18);
    vec2 drift = vec2(t * 0.045, t * 0.022);
    float mask = smoothstep(0.01, 0.20, rd.y);

    if (kind == 0) {
        vec3 gdir = normalize(vec3(rd.x * 0.78 + rd.y * 0.62, -rd.x * 0.62 + rd.y * 0.78, rd.z));
        float band = exp(-gdir.y * gdir.y * 7.5);
        vec2 gUv = vec2(atan(gdir.z, gdir.x) * 1.6, gdir.y * 3.2);
        float warp = csFbm2(gUv * 1.4 + vec2(t * 0.012, -t * 0.008));
        float neb = csFbm2(gUv * 2.6 + vec2(warp * 1.6, -warp * 1.1));
        float dust = smoothstep(0.36, 0.74, csFbm2(gUv * 4.2 + 9.4));
        float shape = band * smoothstep(0.28, 0.78, neb) * (1.0 - dust * 0.48);
        vec3 nebCol = mix(accent, mix(vec3(0.32, 0.80, 1.0), horizonCol * 1.35, 0.5), warp);
        return nebCol * shape * amount * 0.92 * smoothstep(-0.08, 0.16, rd.y);
    }

    if (kind == 3) {
        vec2 cp = p * 0.62 + drift;
        float base = csFbm2(cp);
        float detail = csFbm2(cp * 2.4 - drift * 0.6);
        float density = smoothstep(0.46 - amount * 0.16, 0.74, base * 0.76 + detail * 0.24);
        vec2 sunStep = normalize(celDir.xz + vec2(0.0001)) * 0.16;
        float litSample = csFbm2(cp + sunStep);
        float shade = csSaturate((base - litSample) * 3.8 + 0.58);
        vec3 cloudDark = mix(topCol * 0.75 + vec3(0.18, 0.28, 0.48), horizonCol, 0.45);
        vec3 cloudBright = mix(vec3(1.0, 0.99, 0.97), accent * 0.35 + vec3(0.72), 0.25);
        float rim = pow(csSaturate(dot(rd, celDir) * 0.5 + 0.5), 4.0);
        vec3 c = mix(cloudDark, cloudBright, shade) + vec3(1.0, 0.96, 0.88) * rim * 0.22;
        return c * density * mask * amount * 0.85;
    }

    if (kind == 2) {
        vec2 cp = vec2(p.x * 0.42, p.y * 1.15) + drift * vec2(1.4, 0.4);
        float f = csFbm2(cp);
        float streak = smoothstep(0.44 - amount * 0.14, 0.78, f);
        float sunGlow = pow(csSaturate(dot(rd, celDir) * 0.5 + 0.5), 3.2);
        vec3 col = mix(topCol * 0.65 + accent * 0.35, horizonCol * 1.25 + vec3(0.25, 0.10, 0.05), sunGlow);
        return col * streak * mask * amount * 0.70;
    }

    if (kind == 4) {
        vec2 cp = p * 0.58 + drift * 1.3;
        float f = csFbm2(cp);
        float veil = smoothstep(0.38 - amount * 0.14, 0.76, f);
        float moonRim = pow(csSaturate(dot(rd, celDir) * 0.5 + 0.5), 4.5);
        vec3 darkCloud = vec3(0.06, 0.015, 0.025);
        vec3 rimCol = mix(horizonCol, accent, 0.65) * 1.15;
        return mix(darkCloud, rimCol, moonRim * 0.85 + 0.12) * veil * mask * amount * 0.78;
    }

    vec2 cp = p * 0.48 + drift * 0.8;
    float f = csFbm2(cp);
    float soft = smoothstep(0.45 - amount * 0.14, 0.82, f);
    vec3 col = mix(horizonCol * 1.1, vec3(0.95, 0.94, 1.0), 0.35);
    return col * soft * mask * amount * 0.46;
}

vec3 csCompositeCelestial(
        vec3 sky, vec3 rd, vec3 celDir, float celSize, int tileIndex, int kind, vec3 celTint, float celGlow) {
    float alignment = dot(rd, celDir);
    float horizonVis = smoothstep(-0.08, 0.08, rd.y);
    if (horizonVis <= 0.001) return sky;

    float halo = pow(csSaturate(alignment * 0.5 + 0.5), 14.0) * 0.14 * celGlow;
    float innerHalo = pow(csSaturate(alignment), 48.0) * 0.18 * celGlow;
    sky += celTint * (halo + innerHalo) * horizonVis;

    if (alignment <= 0.25) return sky;

    vec3 worldUp = abs(celDir.y) > 0.96 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(worldUp, celDir));
    vec3 up = cross(celDir, right);

    float baseAng = (tileIndex == 0) ? 0.148 : 0.132;
    float angScale = baseAng * celSize;
    vec2 local = vec2(dot(rd, right), dot(rd, up)) / (alignment * max(angScale, 0.01));
    if (abs(local.x) >= 1.0 || abs(local.y) >= 1.0) return sky;

    vec2 tileUv = vec2(local.x * 0.5 + 0.5, 0.5 - local.y * 0.5);
    tileUv = clamp(tileUv, vec2(0.004), vec2(0.996));
    int row = tileIndex / 2;
    float tx = float(tileIndex - row * 2) * 0.5;
    float ty = float(row) * 0.5;
    vec4 tex = texture(Sampler2, vec2(tx, ty) + tileUv * 0.5);

    float edgeFade = smoothstep(1.0, 0.86, length(local));
    vec3 spriteCol = tex.rgb * celTint;

    if (tileIndex == 2 || tileIndex == 3) {
        float strength = (tileIndex == 2) ? 0.88 : 0.86;
        float a = csSaturate(tex.a) * edgeFade * horizonVis * strength;
        vec3 light = min(vec3(1.0), spriteCol * a);
        sky = 1.0 - (1.0 - sky) * (1.0 - light);
        sky += spriteCol * a * a * 0.12 * celGlow;
    } else if (kind == 5) {
        float a = csSaturate(tex.a) * edgeFade * horizonVis * 0.58;
        vec3 lifted = max(spriteCol, sky * 0.92);
        vec3 screened = 1.0 - (1.0 - sky) * (1.0 - min(vec3(1.0), lifted * 0.65));
        sky = mix(sky, screened, a);
    } else {
        float strength = (kind == 0) ? 0.84 : ((kind == 1) ? 0.74 : 0.82);
        float a = csSaturate(tex.a) * edgeFade * horizonVis * strength;
        float lum = dot(spriteCol, vec3(0.2126, 0.7152, 0.0722));
        float veil = csSaturate((0.38 - lum) / 0.38) * 0.70;
        vec3 veiled = mix(spriteCol, sky, veil);
        sky = mix(sky, veiled, a);
        sky += spriteCol * a * 0.07 * celGlow;
    }
    return sky;
}

void main() {
    vec2 uv = texCoord;
    vec3 scene = texture(Sampler0, uv).rgb;
    float depth = texture(Sampler1, uv).r;
    if (!depthIsFar(depth)) {
        OutColor = vec4(scene, 1.0);
        return;
    }

    vec3 eyeOffset = atmosphereEyeOffset();
    vec3 rd = atmosphereDirection(uv, eyeOffset);
    vec3 rotRd = csRotateY(rd, Time * RotationSpeed * 0.035);

    int kind = StyleKind;
    float up = csSaturate(rd.y);
    float below = csSaturate(-rd.y);
    float gradExp = kind == 3 ? 0.52 : (kind == 2 ? 0.62 : 0.58);
    vec3 sky = mix(HorizonColor, TopColor, pow(up, gradExp));

    float hBand = pow(1.0 - abs(rd.y), 4.2);
    vec3 hGlowCol = mix(HorizonColor, AccentColor, kind == 2 || kind == 4 ? 0.52 : 0.30);
    sky += hGlowCol * hBand * HorizonGlow * 0.42;

    vec3 nadirCol = mix(HorizonColor * 0.58, TopColor * 0.42, smoothstep(0.0, 0.55, below));
    sky = mix(sky, nadirCol, smoothstep(0.0, 0.24, below) * 0.90);

    vec3 celDir = normalize(CelestialDir);
    bool drawCel = CelestialEnabled > 0.5;
    if (kind == 0) {
        sky += csCloudLayer(rotRd, TopColor, HorizonColor, AccentColor, celDir, 0, Clouds, Time);
        sky += csStarsAndMeteors(rotRd, Stars, Time);
        if (drawCel) {
            sky = csCompositeCelestial(sky, rd, celDir, CelestialSize, AtlasTile, kind, CelestialTint, CelestialGlow);
        }
    } else if (kind == 1) {
        sky += csStarsAndMeteors(rotRd, Stars * 0.9, Time);
        if (drawCel) {
            sky = csCompositeCelestial(sky, rd, celDir, CelestialSize, AtlasTile, kind, CelestialTint, CelestialGlow);
        }
        sky += csAuroraLayer(rotRd, AccentColor, max(Clouds, 0.15), Time);
    } else if (kind == 2) {
        sky += csStarsAndMeteors(rotRd, Stars * 0.55 * smoothstep(0.18, 0.65, rd.y), Time);
        float sunScatter = pow(csSaturate(dot(rd, celDir) * 0.5 + 0.5), 3.0) * pow(1.0 - up, 1.8);
        sky += mix(HorizonColor, AccentColor, 0.4) * sunScatter * (0.35 + HorizonGlow * 0.45);
        if (drawCel) {
            sky = csCompositeCelestial(sky, rd, celDir, CelestialSize, AtlasTile, kind, CelestialTint, CelestialGlow);
        }
        vec3 cloudC = csCloudLayer(rotRd, TopColor, HorizonColor, AccentColor, celDir, 2, Clouds, Time);
        sky = mix(sky, sky * 0.55 + cloudC, csSaturate(length(cloudC) * 1.15));
    } else if (kind == 3) {
        sky += csStarsAndMeteors(rotRd, Stars * 0.18 * smoothstep(0.35, 0.85, rd.y), Time);
        if (drawCel) {
            sky = csCompositeCelestial(sky, rd, celDir, CelestialSize, AtlasTile, kind, CelestialTint, CelestialGlow);
        }
        vec3 cloudC = csCloudLayer(rotRd, TopColor, HorizonColor, AccentColor, celDir, 3, Clouds, Time);
        float cMask = csSaturate(length(cloudC) * 0.85);
        sky = mix(sky, cloudC / max(cMask, 0.001), cMask * 0.88);
    } else if (kind == 4) {
        sky += csStarsAndMeteors(rotRd, Stars * 0.85, Time);
        if (drawCel) {
            sky = csCompositeCelestial(sky, rd, celDir, CelestialSize, AtlasTile, kind, CelestialTint, CelestialGlow);
        }
        vec3 cloudC = csCloudLayer(rotRd, TopColor, HorizonColor, AccentColor, celDir, 4, Clouds, Time);
        sky = mix(sky, cloudC, csSaturate(length(cloudC) * 1.45));
    } else {
        sky += csStarsAndMeteors(rotRd, Stars * 0.65, Time);
        if (drawCel) {
            sky = csCompositeCelestial(sky, rd, celDir, CelestialSize, AtlasTile, kind, CelestialTint, CelestialGlow);
        }
        sky += csCloudLayer(rotRd, TopColor, HorizonColor, AccentColor, celDir, 5, Clouds, Time);
    }

    sky *= Brightness;
    sky = mix(sky, scene, SubmergedFactor * 0.85);

    float lum = dot(sky, vec3(0.2126, 0.7152, 0.0722));
    sky /= 1.0 + max(lum - 0.96, 0.0) * 1.35;
    sky += vec3((csHash12(gl_FragCoord.xy + vec2(91.3, 47.7)) - 0.5) * 0.0055);
    OutColor = vec4(clamp(sky, 0.0, 1.0), 1.0);
}
