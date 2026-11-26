uvec4 ncColumnMetadata(sampler2D colTex, ivec2 c) {
    if (c.x < 0 || c.y < 0 || c.x >= 64 || c.y >= 64) return uvec4(0u);
    return uvec4(round(texelFetch(colTex, ivec2(c.x * 2 + 1, c.y), 0) * 255.0));
}
vec4 ncColumnAt(sampler2D colTex, ivec2 c) {
    uvec4 metadata = ncColumnMetadata(colTex, c);
    if ((metadata.r & 128u) == 0u) return vec4(0.0);
    vec4 a = texelFetch(colTex, ivec2(c.x * 2, c.y), 0);
    float topY = (floor(a.r * 255.0 + 0.5) * 256.0 + floor(a.g * 255.0 + 0.5)) * (1.0 / 64.0) - 128.0;
    float groundY = (floor(a.b * 255.0 + 0.5) * 256.0 + floor(a.a * 255.0 + 0.5)) * (1.0 / 64.0) - 128.0;
    float surfaceType = float(metadata.r & 7u);
    return vec4(topY, groundY, surfaceType, 1.0);
}
bool ncGroundCoverAllowed(sampler2D colTex, ivec2 c) {
    return (ncColumnMetadata(colTex, c).r & 32u) != 0u;
}
bool ncBroadGroundCover(sampler2D colTex, ivec2 c) {
    return (ncColumnMetadata(colTex, c).r & 64u) != 0u;
}

float ncAutumnGroundDetail(vec3 toSupport, float pixelWorld) {
    float distance = length(toSupport);
    float width = distance * distance * pixelWorld / max(abs(toSupport.y),0.001);
    return (1.0 - smoothstep(8.0,11.5,distance)) * (1.0 - smoothstep(0.045,0.085,width));
}
bool ncColumnHasLeaf(sampler2D colTex, ivec2 c, int layer) {
    if (layer < 0 || layer >= 24) return false;
    uvec4 metadata = ncColumnMetadata(colTex, c);
    uint layers = (metadata.g << 16u) | (metadata.b << 8u) | metadata.a;
    return (layers & (1u << uint(layer))) != 0u;
}
