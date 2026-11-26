bool depthIsReversed() {
    return ReversedDepth == 1 || ReversedDepth == 3;
}

float ndcDepth(float depth) {
    bool zeroToOne = ReversedDepth == 1 || ReversedDepth == 2;
    return zeroToOne ? depth : depth * 2.0 - 1.0;
}

bool depthIsFar(float depth) {
    return depthIsReversed() ? depth <= 0.0 : depth >= 1.0;
}

vec4 unprojectAtmosphere(vec2 uv, float depth) {
    return InvViewProj * vec4(uv * 2.0 - 1.0, ndcDepth(depth), 1.0);
}

vec3 atmosphereEyeOffset() {
    vec4 eye = InvViewProj[2];
    return abs(eye.w) > 0.000001 ? eye.xyz / eye.w : vec3(0.0);
}

vec3 atmosphereDirection(vec2 uv, vec3 eyeOffset) {
    vec4 nearPoint = unprojectAtmosphere(uv, depthIsReversed() ? 1.0 : 0.0);
    return normalize(nearPoint.xyz - eyeOffset * nearPoint.w);
}

vec3 relativePosFromDepth(vec2 uv, float depth) {
    vec4 point = unprojectAtmosphere(uv, depth);
    return point.xyz / max(point.w, 0.000001);
}

vec3 worldPosFromDepth(vec2 uv, float depth) {
    return CamPos + relativePosFromDepth(uv, depth);
}

float depthMatch(vec2 uv, float centerDepth, float centerViewZ) {
    float sampled = textureLod(Sampler1, uv, 0.0).r;
    if (depthIsFar(sampled) != depthIsFar(centerDepth)) return 0.0;
    if (depthIsFar(sampled)) return 1.0;
    float viewZ = 1.0 / max(unprojectAtmosphere(uv, sampled).w, 0.000001);
    return 1.0 - smoothstep(max(0.08, centerViewZ * 0.015),
        max(0.22, centerViewZ * 0.06), abs(viewZ - centerViewZ));
}

float skySourceVisibility(vec2 uv, vec2 texel) {
    if (any(lessThan(uv,vec2(0.0))) || any(greaterThan(uv,vec2(1.0)))) return 0.0;
    float seen = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 sampleUv = clamp(uv + vec2(x, y) * texel, vec2(0.0), vec2(1.0));
            seen += depthIsFar(textureLod(Sampler1, sampleUv, 0.0).r) ? 1.0 : 0.0;
        }
    }
    return seen / 9.0;
}

float visibleBeforeSurface(float along, float surface, float softness) {
    return smoothstep(0.0, softness, surface - along);
}

float heightFogIntegral(float eyeY, float dirY, float distance, float base, float thickness) {
    float start = eyeY - base;
    float end = start + dirY * distance;
    float low = min(start, end);
    float high = max(start, end);
    float height = high - low;
    float k = 2.0 / max(thickness, 1.0);
    if (height < 0.001) return distance * exp(-k * max(0.5 * (start + end), 0.0));

    float below = clamp(-low, 0.0, height);
    float above = height - below;
    float slope = k * above;
    float falloff = slope < 0.002 ? 1.0 - 0.5 * slope : (1.0 - exp(-slope)) / slope;
    float air = above * exp(-k * max(low, 0.0)) * falloff;
    return distance * clamp((below + air) / height, 0.0, 1.0);
}
