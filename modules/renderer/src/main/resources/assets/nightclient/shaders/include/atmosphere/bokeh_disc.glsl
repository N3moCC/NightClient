void main() {
    vec2 uv = texCoord;
    vec2 texel = 1.0 / max(OutSize, vec2(1.0));
    vec3 sum = vec3(0.0);
    float depthSum = 0.0;
    float depthWeight = 0.0;
    for (int y = -6; y <= 6; ++y) {
        for (int x = -6; x <= 6; ++x) {
            vec2 offset = vec2(float(x), float(y));
            float radial = length(offset);
            float hexDist = max(abs(offset.x) * 0.8660254 + abs(offset.y) * 0.5, abs(offset.y));
            vec4 s = texture(Sampler0, uv + offset * texel);
            float coc = clamp(2.0 - 1.0 / max(s.a, 0.0001), 0.0, 1.0);
            float radius = 0.72 + coc * MaxRadius;
            float r = mix(radial, hexDist * 1.04, smoothstep(1.2, 3.0, radius));
            float inside = 1.0 - smoothstep(radius - 0.65, radius + 0.55, r);
            float rim = 0.85 + 0.72 * smoothstep(radius * 0.52, radius * 0.96, r);
            float area = 2.95 * (radius + 0.25) * (radius + 0.25);
            vec3 contribution = s.rgb * (inside * rim / area);
            float weight = dot(contribution, vec3(0.2126,0.7152,0.0722));
            sum += contribution;
            depthSum += s.a * weight;
            depthWeight += weight;
        }
    }
    OutColor = vec4(sum * Gain, depthSum / max(depthWeight,0.000001));
}
