float spriteMask(float distance, float pixel) {
    float width = max(0.54 * pixel, 0.008);
    return 1.0 - smoothstep(-width, width, distance);
}

float spriteSegment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    return length(p - a - ab * clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0));
}

float spriteEllipse(vec2 p, vec2 radii) {
    return (length(p / radii) - 1.0) * min(radii.x, radii.y);
}

vec4 cherryPetal(vec2 p, float seed, float pixel, vec3 tint) {
    p.x -= (2.0 * seed - 1.0) * (0.14 * p.y + 0.07 * p.y * p.y);
    float distance = spriteEllipse(p - vec2(0.0, 0.07), vec2(0.55 + 0.20 * clamp(p.y, -0.7, 0.7), 0.80));
    float cleft = length(p - vec2(-0.035, 0.90)) - 0.13;
    distance = max(distance, -cleft);

    vec3 base = mix(vec3(0.77, 0.38, 0.51), vec3(0.89, 0.59, 0.68), seed);
    vec3 tip = mix(vec3(0.96, 0.73, 0.80), vec3(1.0, 0.91, 0.90), seed);
    vec3 color = mix(base, tip, smoothstep(-0.70, 0.54, p.y + 0.40 * p.x));
    float fold = spriteMask(abs(p.x + 0.08 * p.y + 0.10) - 0.030, pixel);
    float detail = 1.0 - smoothstep(0.035, 0.14, pixel);
    color += vec3(0.12, 0.10, 0.08) * fold * detail * smoothstep(-0.60, 0.20, p.y);
    return vec4(color * tint, spriteMask(distance, pixel));
}

vec4 cherryBlossom(vec2 p, float seed, float pixel, vec3 tint) {
    float r = length(p);
    float angle = atan(p.y, p.x + 0.00001) + seed * 6.2831853;
    const float sector = 1.25663706;
    float petalAngle = mod(angle + sector * 0.5, sector) - sector * 0.5;
    vec2 q = r * vec2(sin(petalAngle), cos(petalAngle));
    float distance = spriteEllipse(q - vec2(0.0, 0.49), vec2(0.277, 0.39));
    distance = max(distance, 0.10 - length(q - vec2(0.016, 0.90)));
    distance = min(distance, r - 0.22);

    vec3 base = vec3(0.85, 0.49, 0.60);
    vec3 tip = mix(vec3(0.96, 0.73, 0.80), vec3(1.0, 0.91, 0.90), seed);
    vec3 color = mix(base, tip, smoothstep(0.17, 0.76, r));
    color *= 0.96 + 0.07 * smoothstep(-0.23, 0.18, q.x);
    color = mix(color, vec3(0.69, 0.32, 0.44), 0.76 * spriteMask(r - 0.16, pixel));
    float grain = min(length(p - vec2(-0.07, 0.05)),
        min(length(p - vec2(0.068, 0.04)), length(p - vec2(0.008, -0.076))));
    float detail = 1.0 - smoothstep(0.04, 0.17, pixel);
    color = mix(color, vec3(0.98, 0.82, 0.56), spriteMask(grain - 0.040, pixel) * detail);
    return vec4(color * tint, spriteMask(distance, pixel));
}

float pointedLeaf(vec2 p, vec2 dir, float extent, float width) {
    float y = dot(p, dir);
    float x = abs(p.x * dir.y - p.y * dir.x);
    float t = clamp(y / extent, 0.0, 1.0);
    float span = width * 4.0 * t * (1.0 - t) * (1.15 - 0.28 * t);
    return max(x - span, max(-y, y - extent));
}

float botanicalLeafBlade(vec2 p, float species) {
    float distance;
    if (species < 0.40) {
        vec2 q = vec2(abs(p.x), p.y + 0.30);
        float mid = pointedLeaf(q, vec2(0.0, 1.0), 1.22, 0.27);
        float side = pointedLeaf(q, vec2(0.83, 0.558), 0.99, 0.25);
        float base = pointedLeaf(q, vec2(1.0, 0.0), 0.58, 0.18);
        distance = min(min(mid, side), min(base, length(q - vec2(0.0, 0.20)) - 0.24));
    } else if (species < 0.74) {
        float y = (p.y - 0.02) / 0.86;
        float x = abs(p.x - 0.05 * y * y);
        float blade = max(1.0 - y * y, 0.0);
        float lobe = 0.75 + 0.25 * cos(y * 12.0 - 0.4);
        distance = max(x - 0.56 * blade * lobe * (1.0 + 0.16 * y), (abs(y) - 1.0) * 0.86);
    } else {
        float y = (p.y - 0.02) / 0.84;
        float x = abs(p.x - 0.12 * y * (y + 0.10));
        float width = 0.52 * max(1.0 - y * y, 0.0) * (1.0 - 0.32 * y);
        distance = max(x - width, (abs(y) - 1.0) * 0.84);
    }
    return distance;
}

float botanicalLeafStem(vec2 p) {
    return spriteSegment(p, vec2(0.0, -0.17), vec2(-0.055, -0.96));
}

float botanicalLeaf(vec2 p, float species) {
    return min(botanicalLeafBlade(p, species), botanicalLeafStem(p) - 0.026);
}

float witheredLeafBlade(vec2 p, float species, float seed) {
    float distance = botanicalLeafBlade(p, species);
    vec2 bite = vec2(seed < 0.5 ? -0.48 : 0.48, fract(seed * 7.9) - 0.50);
    if (seed > 0.60) distance = max(distance, (0.075 + 0.13 * seed) - length(p - bite));
    return distance;
}

float witheredLeaf(vec2 p, float species, float seed) {
    return min(witheredLeafBlade(p, species, seed), botanicalLeafStem(p) - 0.026);
}

float botanicalGroundCoverage(float blade, float stem, float pixel) {
    float stemCoverage = spriteMask(stem - 0.026, pixel) - spriteMask(stem + 0.026, pixel);
    return max(spriteMask(blade, pixel), stemCoverage);
}

float botanicalLeafShade(vec2 p, float species, float distance, float seed, float pixel) {
    float detail = 1.0 - smoothstep(0.035, 0.24, pixel);
    float rib = spriteSegment(p, vec2(-0.045, -0.91), vec2(0.04, 0.77));
    float fork = spriteSegment(vec2(abs(p.x), p.y), vec2(0.0, -0.16),
        species < 0.40 ? vec2(0.63, 0.25) : vec2(0.39, 0.41));
    float vein = spriteMask(min(rib, fork + 0.015) - 0.029, pixel) * detail;
    float edge = smoothstep(-0.14, -0.02, distance) * detail;
    float plane = smoothstep(-0.37, 0.35, p.x * (seed < 0.5 ? -1.0 : 1.0) - 0.24 * p.y);
    return 0.83 + 0.26 * plane + 0.16 * vein - 0.21 * edge;
}

vec3 autumnLeafPalette(float seed) {
    if (seed < 0.22) return vec3(0.72, 0.24, 0.14);
    if (seed < 0.42) return vec3(0.87, 0.41, 0.16);
    if (seed < 0.70) return vec3(0.88, 0.62, 0.25);
    if (seed < 0.85) return vec3(0.68, 0.59, 0.28);
    return vec3(0.53, 0.34, 0.17);
}

vec3 witheredLeafPalette(float seed) {
    if (seed < 0.31) return vec3(0.41, 0.28, 0.19);
    if (seed < 0.69) return vec3(0.59, 0.33, 0.19);
    if (seed < 0.87) return vec3(0.62, 0.49, 0.28);
    return vec3(0.28, 0.21, 0.18);
}
