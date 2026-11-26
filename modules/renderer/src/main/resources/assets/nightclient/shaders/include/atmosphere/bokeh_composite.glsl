void main() {
    vec3 scene = texture(Sampler0, texCoord).rgb;
    vec3 glow = texture(Sampler1, texCoord).rgb * BokComp0.x;
    float lum = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    OutColor = vec4(clamp(scene + glow / (1.0 + lum), 0.0, 1.0), 1.0);
}
