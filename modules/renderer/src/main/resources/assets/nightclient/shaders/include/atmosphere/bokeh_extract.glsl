float luminance(vec3 c) { return dot(c,vec3(0.2126,0.7152,0.0722)); }
void main() {
    vec2 uv = texCoord;
    float depth = textureLod(Sampler1, uv, 0.0).r;
    if (depthIsFar(depth)) {
        OutColor = vec4(0.0);
        return;
    }
    float viewZ = 1.0 / max(unprojectAtmosphere(uv,depth).w,0.000001);
    vec3 c = textureLod(Sampler0,uv,0.0).rgb;
    for (int y = -1; y <= 1; y += 2) {
        for (int x = -1; x <= 1; x += 2) {
            vec2 tapUv = clamp(uv + SourceTexel * vec2(x,y), vec2(0.0), vec2(1.0));
            vec3 tap = textureLod(Sampler0,tapUv,0.0).rgb * depthMatch(tapUv,depth,viewZ);
            if (luminance(tap) > luminance(c)) c = tap;
        }
    }
    float lum = luminance(c);
    float peak = max(c.r,max(c.g,c.b));
    float saturation = (peak - min(c.r,min(c.g,c.b))) / max(peak,0.001);
    float weight = smoothstep(Threshold,Threshold + 0.18,lum) * (0.35 + 0.65 * saturation);
    float dist = length(relativePosFromDepth(uv,depth) - atmosphereEyeOffset());
    float coc = clamp((dist - Focus) / max(dist,0.001),0.0,1.0);
    weight *= 0.25 + 0.75*coc;
    OutColor = vec4(c * weight, dist / (dist + Focus));
}
