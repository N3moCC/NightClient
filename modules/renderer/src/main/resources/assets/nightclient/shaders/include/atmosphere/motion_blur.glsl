float ncMbIgn(vec2 px, float phase) {
    vec2 p = px + vec2(phase * 37.0, phase * 17.0);
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

vec3 ncMbSrgbToLinear(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(2.2));
}

vec3 ncMbLinearToSrgb(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
}

void main() {
    vec4 centerSample = textureLod(Sampler0, texCoord, 0.0);
    float rawDepth = textureLod(Sampler1, texCoord, 0.0).r;
    bool isSky = depthIsFar(rawDepth);

    if (isSky && BlurSky < 0.5) {
        OutColor = centerSample;
        return;
    }

    float clipZ = ndcDepth(rawDepth);
    vec4 curClip = vec4(texCoord * 2.0 - 1.0, clipZ, 1.0);
    vec4 prevClip = ReprojMat * curClip;
    if (prevClip.w <= 0.0001) {
        OutColor = centerSample;
        return;
    }

    vec2 prevUv = (prevClip.xy / prevClip.w) * 0.5 + 0.5;
    vec2 velocity = (prevUv - texCoord) * BlurScale;
    float speed = length(velocity);
    if (speed < 0.00045) {
        OutColor = centerSample;
        return;
    }

    float limited = (speed * MaxSpan) / (speed + MaxSpan);
    vec2 span = velocity * (limited / speed);

    int tapCount = clamp(int(SampleCount + 0.5), 4, 16);
    float jitter = ncMbIgn(gl_FragCoord.xy, FramePhase) - 0.5;

    vec3 accum = vec3(0.0);
    float totalWeight = 0.0;
    for (int i = 0; i < 16; ++i) {
        if (i >= tapCount) break;
        float frac = ((float(i) + 0.5 + jitter) / float(tapCount)) - 0.5;
        vec2 sampleUv = clamp(texCoord + span * frac, vec2(0.0005), vec2(0.9995));
        vec3 sRgb = textureLod(Sampler0, sampleUv, 0.0).rgb;
        vec3 sLin = ncMbSrgbToLinear(sRgb);

        float distNorm = abs(frac) * 2.0;
        float w = 1.0 - 0.25 * distNorm * distNorm;
        if (!isSky) {
            float sampleDepth = textureLod(Sampler1, sampleUv, 0.0).r;
            if (depthIsFar(sampleDepth)) {
                w *= 0.45;
            }
        }
        accum += sLin * w;
        totalWeight += w;
    }

    vec3 blended = totalWeight > 0.0001 ? ncMbLinearToSrgb(accum / totalWeight) : centerSample.rgb;
    OutColor = vec4(blended, centerSample.a);
}
