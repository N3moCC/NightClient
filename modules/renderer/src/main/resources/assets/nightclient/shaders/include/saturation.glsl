vec3 ncSaturation(vec3 rgb, float strength) {
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    return clamp(vec3(luma) + (rgb - vec3(luma)) * strength, 0.0, 1.0);
}
