void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = TV_PROJ_MAT * viewPos;

    vViewPos = viewPos.xyz;
    vNormal = normalize(mat3(NormalMat) * Normal);
    vUv = UV0;
    vColor = Color;

    int u1x = UV1.x & 0xFFFF;
    int u1y = UV1.y & 0xFFFF;
    int u2x = UV2.x & 0xFFFF;
    int u2y = UV2.y & 0xFFFF;

    float metallic = float(u1x & 255) / 255.0;
    float roughness = float((u1x >> 8) & 127) / 127.0;
    float emissiveStr = (float(u1y & 255) / 255.0) * 4.0;
    float tintMask = float((u1y >> 8) & 127) / 127.0;

    float emR = float(u2x & 255) / 255.0;
    float emG = float((u2x >> 8) & 127) / 127.0;
    float emB = float(u2y & 255) / 255.0;
    float flagsVal = float((u2y >> 8) & 127);

    vMat = vec4(metallic, roughness, emissiveStr, tintMask);
    vEmissive = vec4(emR, emG, emB, flagsVal);
}
