float ncHandAlpha(vec2 uv, bool offHand) {
    if(any(lessThan(uv,vec2(0.0))) || any(greaterThan(uv,vec2(1.0)))) return 0.0;
    float offAlpha=texture(OffSampler,uv).a;
    return offHand ? offAlpha : texture(MainSampler,uv).a*(1.0-step(0.0001,offAlpha));
}
float ncHandOutline(vec2 uv, vec2 pointer, float radius) {
    if(any(lessThan(pointer,vec2(0.0))) || any(greaterThanEqual(pointer,vec2(1.0)))) return 0.0;
    bool offHand=texture(OffSampler,pointer).a>0.0001;
    if(!offHand && texture(MainSampler,pointer).a<=0.0001) return 0.0;

    if(!offHand && texture(OffSampler,uv).a>0.0001) return 0.0;
    float center=ncHandAlpha(uv,offHand);
    if(center>0.0001) return 0.0;
    vec2 texel=1.0/vec2(textureSize(MainSampler,0));
    float core=0.0,halo=0.0;
    for(int i=0;i<12;i++) {
        float angle=float(i)*0.5235987756;
        vec2 direction=vec2(cos(angle),sin(angle))*texel;
        core=max(core,ncHandAlpha(uv+direction*radius,offHand));
        halo+=ncHandAlpha(uv+direction*(radius*3.0),offHand);
    }
    return min(0.98,core*0.90+halo*(0.32/12.0));
}
