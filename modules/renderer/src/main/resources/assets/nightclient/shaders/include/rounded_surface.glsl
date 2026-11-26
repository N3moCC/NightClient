float nightRoundedCoverage(vec2 encoded) {
    float header = floor((encoded.x + 8.0) / 16.0);
    vec2 local = vec2(encoded.x - header * 16.0, encoded.y);
    float distance = length(local) - 1.0;
    float aa = max(fwidth(distance) * 0.5, 0.00001);
    float coverage = 1.0 - smoothstep(-aa, aa, distance);
    if (header > 0.0) {
        float stroke = (header - 1.0) / 2048.0;
        coverage *= smoothstep(-stroke - aa, -stroke + aa, distance);
    }
    return coverage;
}
