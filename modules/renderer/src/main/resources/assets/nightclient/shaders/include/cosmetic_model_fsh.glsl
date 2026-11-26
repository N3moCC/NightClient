void main() {
    vec4 tex = texture(Sampler0, vUv);
    vec4 base = tex * vColor;
    if (base.a <= 0.004) discard;

    float metallic = clamp(vMat.x, 0.0, 1.0);
    float roughness = clamp(vMat.y, 0.05, 1.0);
    float emissiveStr = vMat.z;
    float tintMask = clamp(vMat.w, 0.0, 1.0);
    int flags = int(vEmissive.w + 0.5);
    bool isPlasmaCore = (flags & 1) != 0;
    bool isFresnelAura = (flags & 2) != 0;
    bool isPulseGlow = (flags & 4) != 0;

    vec3 N = normalize(vNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(-vViewPos);
    float NdotV = max(0.0, dot(N, V));

    float time = AccentColor.a;
    vec3 accent = AccentColor.rgb;
    float pulse = isPulseGlow ? (0.93 + 0.07 * sin(time * 9.5 + vViewPos.y * 14.0)) : 1.0;

    if (HurtColor.a > 0.5 || isFresnelAura) {
        float edgeFalloff = pow(NdotV, 1.55);
        vec3 glowCol = mix(base.rgb * max(vEmissive.rgb, vec3(0.25)), accent, tintMask);
        float strength = max(0.55, emissiveStr * 0.55) * pulse * ExtraParams.x;
        float alpha = clamp(base.a * edgeFalloff * 1.15, 0.0, 0.92);
        if (alpha <= 0.003) discard;
        vec3 outRgb = glowCol * (alpha * strength);
        OutColor = vec4(outRgb, alpha);
        return;
    }

    vec3 albedo = mix(base.rgb, base.rgb * accent, tintMask * (isPlasmaCore ? 0.45 : 0.85));

    vec3 Lkey = normalize(vec3(0.45, 0.78, 0.43));
    vec3 Lfill = normalize(vec3(-0.62, 0.28, 0.73));
    vec3 Lrim = normalize(vec3(0.10, 0.48, -0.87));

    float diffKey = max(0.0, dot(N, Lkey));
    float diffFill = max(0.0, dot(N, Lfill));
    float rimTerm = pow(1.0 - NdotV, 2.6);
    float hemi = 0.55 + 0.45 * N.y;

    vec3 Hkey = normalize(Lkey + V);
    vec3 Hfill = normalize(Lfill + V);
    float shininess = mix(140.0, 8.0, roughness * roughness);
    float specKey = pow(max(0.0, dot(N, Hkey)), shininess);
    float specFill = pow(max(0.0, dot(N, Hfill)), shininess * 0.6) * 0.45;

    vec3 F0 = mix(vec3(0.04), albedo, metallic);
    vec3 fresnel = F0 + (vec3(1.0) - F0) * pow(1.0 - NdotV, 4.0);

    vec3 worldLight = max(LightTint.rgb, vec3(0.14));
    vec3 diffuseLit = albedo * (1.0 - metallic * 0.62) * (0.34 * hemi + 0.72 * diffKey + 0.24 * diffFill);
    vec3 specEnv = mix(vec3(0.18, 0.22, 0.28), vec3(0.88, 0.92, 1.0), clamp(N.y * 0.5 + 0.5, 0.0, 1.0));
    vec3 specLit = (specKey * 1.35 + specFill) * fresnel * mix(1.0, 1.9, metallic)
        + fresnel * specEnv * metallic * (0.42 * (1.0 - roughness * 0.65));
    vec3 rimLit = (vec3(0.16, 0.20, 0.26) + accent * 0.14 * tintMask) * rimTerm * (0.35 + 0.45 * max(0.0, dot(N, Lrim)));

    vec3 lit = (diffuseLit + specLit + rimLit) * worldLight;

    if (emissiveStr > 0.01) {
        vec3 emCol = mix(vEmissive.rgb, accent, tintMask);
        if (isPlasmaCore) {
            vec3 hotCore = mix(accent, vec3(1.0, 0.99, 1.0), smoothstep(0.18, 0.72, NdotV));
            emCol = hotCore;
        }
        lit += emCol * (emissiveStr * 0.78 * pulse * ExtraParams.y);
    }

    if (LightTint.a > 0.001) {
        lit = mix(lit, HurtColor.rgb, clamp(LightTint.a, 0.0, 0.75));
    }

    lit = lit / (vec3(1.0) + lit * 0.22);
    OutColor = vec4(lit, base.a);
}
