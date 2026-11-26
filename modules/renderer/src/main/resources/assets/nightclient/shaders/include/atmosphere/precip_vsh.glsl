uint mixBits(uint x) {
x ^=x >> 16;
x*=0x7feb352dU;
x ^=x >> 15;
x*=0x846ca68bU;
x ^=x >> 16;
return x;
}
vec3 random3(uint seed) {
uint a=mixBits(seed);
uint b=mixBits(a ^ 0x9e3779b9U);
uint c=mixBits(b ^ 0x85ebca6bU);
return vec3(float(a >> 8),float(b >> 8),float(c >> 8))*(1.0/16777216.0);
}
struct Fall {
vec3 wind;
vec3 sway;
float gust;
float radius;
vec2 lies;
};
vec3 gustAt(float t) {
return vec3(sin(t*0.31)*1.6 + sin(t*0.83)*0.5,sin(t*0.47)*0.3,cos(t*0.27)*1.2);
}
vec3 flutterAt(float t,float phase) {
return vec3(sin(t*0.9 + phase)*0.35,sin(t*1.9 + 1.7*phase)*0.12,
cos(t*0.7 + 2.3*phase)*0.3);
}
const vec2 SNOW_WIND=vec2(0.8944272,0.4472136);
bool snowClump(vec3 g) { return fract(g.z*61.0) < FallDetail[0].y;}
const float LEAF_CLEARANCE = 0.012;

float rollingLeafVisibility(float pixels, vec3 toLeaf, vec3 normal) {
    float incidence = abs(dot(normal, toLeaf)) / max(length(toLeaf), 0.001);
    return smoothstep(0.65, 1.9, pixels * incidence);
}
Fall snowFall(float t,vec3 g) {
float phase=g.y*6.2831853;
float wind=1.0 + FallDetail[0].z;
bool clump=snowClump(g);
Fall f;
f.wind=vec3(0.4*wind,clump?-1.7:-1.3,0.2*wind)*(0.75 + 0.5*g.x);
f.sway=vec3(sin(t*0.7 + phase),cos(t*0.5 + 1.3*phase)*0.4,
sin(t*0.6 + 2.1*phase))*(clump?0.26:0.18);
f.gust=wind;
f.radius=(0.018 + 0.022*g.z)*(1.0 + FallDetail[0].x)*(clump?2.3:1.0);
f.lies=vec2(0.7,0.0);
return f;
}
const float BLIZZARD_W=0.1047;
float blizzard(float t) {
float s=sin(BLIZZARD_W*t);
float s2=s*s;
return s2*s2*s2*s2;
}
float blizzardTravel(float t) {
float x=BLIZZARD_W*t;
return (35.0*x - 28.0*sin(2.0*x) + 7.0*sin(4.0*x) - 1.3333333*sin(6.0*x)
+ 0.125*sin(8.0*x))/(128.0*BLIZZARD_W);
}
Fall petalFall(float t,vec3 g) {
Fall f;
f.wind=vec3(0.45,-0.55,0.25)*(0.8 + 0.4*g.x);
f.sway=flutterAt(t,g.y*6.2831853)
+ vec3(f.wind.x,0.0,f.wind.z)*(4.0*FallDetail[0].z*blizzardTravel(t)*Speed);
f.gust=0.6;
f.radius=(0.055 + 0.035*g.z)*(1.0 + FallDetail[0].x);
f.lies=vec2(2.5,4.0);
return f;
}
Fall fallOf(float t,vec3 g) {
if (Kind==1) return snowFall(t,g);
return petalFall(t,g);
}
vec3 inAir(float t,vec3 h,vec3 g) {
Fall f=fallOf(t,g);
vec3 world=h*BoxSize + (f.wind*t + gustAt(t)*f.gust)*Speed + f.sway;
return mod(world - BoxOrigin + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
}
bool onMap(ivec2 c) { return c.x >=0 && c.y >=0 && c.x < 64 && c.y < 64;}
float leafLandingFade(vec3 rel, float radius) {
    ivec2 c = ivec2(floor(rel.xz - GridOrigin.xz));
    vec4 column = ncColumnAt(Columns,c);
    if (column.a < 0.5) return 1.0;
    float surface = column.g - (column.b == 2.0 ? 1.0/9.0 : 0.0);
    float height = rel.y - (surface + GridOrigin.y);
    return smoothstep(-0.025, max(0.10, radius * 0.75), height);
}

float rollingSupportEdge(ivec2 cell, float height, float distance, float margin) {
    if (distance >= margin) return 1.0;
    vec4 neighbor = ncColumnAt(Columns,cell);
    bool sameLevel = ncGroundCoverAllowed(Columns,cell) && abs(neighbor.g - height) <= 1.0/32.0;
    return sameLevel ? 1.0 : smoothstep(0.0, margin, distance);
}

float rollingSupportFade(vec2 rel, ivec2 cell, float height, float radius) {
    vec2 within = rel - GridOrigin.xz - vec2(cell);
    vec2 edge = min(within, 1.0 - within);
    ivec2 side = ivec2(2.0 * step(vec2(0.5), within) - 1.0);
    float margin = min(radius + 0.02, 0.45);
    return rollingSupportEdge(cell + ivec2(side.x, 0), height, edge.x, margin)
        * rollingSupportEdge(cell + ivec2(0, side.y), height, edge.y, margin)
        * rollingSupportEdge(cell + side, height, max(edge.x, edge.y), margin);
}
const vec2 W_WIND=vec2(0.8335,0.5524);
const float HALLOW_PI=3.14159265;
const vec2 HALLOW_CORNERS[6]=vec2[6](vec2(-1.0,-1.0),vec2(1.0,-1.0),vec2(1.0,1.0),
vec2(-1.0,-1.0),vec2(1.0,1.0),vec2(-1.0,1.0));
vec2 hallowWorld(vec2 relXZ) { return mod(FallDetail[3].xy + relXZ - GridOrigin.xz,4096.0);}
float hallowGustPhase(vec2 world,float t) { return 0.45*t - 0.06*dot(world,W_WIND);}
float hallowGustCarry(float th) {
return 35.0*th/128.0 - 7.0*sin(2.0*th)/32.0 + 7.0*sin(4.0*th)/128.0
- sin(6.0*th)/96.0 + sin(8.0*th)/1024.0;
}
float rampG(float s) { return s <=0.0?0.0:(s >=1.0?s - 0.5:0.5*s*s);}
float fogRamp(float roY,float rdY,float t,float top,float w) {
float a=(top - roY)/w + 0.5;
float b=-rdY/w;
if (abs(b) < 1.0e-4) return clamp(a,0.0,1.0)*t;
return (rampG(a + b*t) - rampG(a))/b;
}
float halloweenFogAlpha(vec3 dir,float d) {
vec4 fog=FallDetail[4];
if (fog.z <=0.0) return 0.0;
float height=max(fog.y,0.1);
float through=fogRamp(0.0,dir.y,d,fog.x + height,0.6*height);
return (1.0 - exp(-0.25*fog.z*through))*(1.0 - fog.w*(1.0 - smoothstep(4.0,10.0,d)));
}
float hallowSeen(vec3 dir,float d) {
return exp(-FallDetail[3].z*d)*(1.0 - halloweenFogAlpha(dir,d));
}
vec3 hallowLit(vec3 rel,vec3 normal) {
vec4 moon=FallDetail[2];
vec3 lit=FallDetail[7].w*0.55*vec3(0.80,0.88,1.05)
*(1.0 + 0.6*moon.w*abs(dot(normal,moon.xyz)));
for (int j=5;j < 7;++j) {
vec4 lamp=FallDetail[j];
if (lamp.w <=0.0) continue;
vec3 to=lamp.xyz - rel;
float d2=dot(to,to);
float x=min(d2/36.0,1.0);
lit +=lamp.w*FallDetail[7].rgb*(1.4*(1.0 - x*x)*(1.0 - x*x)/(d2 + 0.5));
}
return lit;
}
vec3 hallowTurn(vec3 v,vec3 axis,float angle) {
float c=cos(angle);
float s=sin(angle);
return v*c + cross(axis,v)*s + axis*(dot(axis,v)*(1.0 - c));
}
void hallowFacing(vec3 dir,out vec3 a,out vec3 b) {
vec3 right=cross(dir,vec3(0.0,1.0,0.0));
float across=length(right);
a=across > 0.001?right/across:vec3(1.0,0.0,0.0);
b=cross(a,dir);
}
vec3 hallowLeafAt(float t,vec3 h,vec3 g) {
vec3 wind=vec3(0.45,-0.98,0.30)*(0.8 + 0.4*g.x);
vec3 world=h*BoxSize + wind*(t*Speed) + flutterAt(t,g.y*6.2831853)*vec3(0.6,0.0,0.6);
vec3 rel=mod(world - BoxOrigin + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
float carry=hallowGustCarry(hallowGustPhase(hallowWorld(rel.xz),t));
rel.xz=mod(rel.xz + W_WIND*(2.8*FallDetail[0].y*(0.7 + 0.6*g.z)*carry/0.45) + 0.5*BoxSize,BoxSize)
- 0.5*BoxSize;
return rel;
}
float hallowCanopy(ivec2 c) {
if (!onMap(c)) return 0.5;
vec4 t=ncColumnAt(Columns, c);
return t.a > 0.5 && abs(t.b - 3.0) < 0.5?1.0:0.0;
}
void hallowFalling(int particle,int corner) {
vec3 h=random3(uint(particle)*2u + 1u);
vec3 g=random3(uint(particle)*2u + 2u);
vec3 k=random3(uint(particle)*2u + 0x6A09E667u);
vec3 rel=hallowLeafAt(Time,h,g);
float radius=(0.06 + 0.04*g.z)*(1.0 + FallDetail[0].x);
float landing=leafLandingFade(rel,radius);
if (landing <=0.002) return;
vec2 cell=rel.xz - GridOrigin.xz - 0.5;
ivec2 c0=ivec2(floor(cell));
vec2 f=cell - vec2(c0);
float trees=mix(mix(hallowCanopy(c0),hallowCanopy(c0 + ivec2(1,0)),f.x),
mix(hallowCanopy(c0 + ivec2(0,1)),hallowCanopy(c0 + ivec2(1,1)),f.x),f.y);
float share=mix(0.35,1.0,trees);
float dist=length(rel);
float pixels=radius/max(dist*PixelWorld,0.000001);
float extent=max(radius,1.35*dist*PixelWorld);
vec3 dir=rel/max(dist,0.0001);
float fade=(1.0 - smoothstep(0.34*BoxSize,0.49*BoxSize,max(max(abs(rel.x),abs(rel.y)),abs(rel.z))))
*smoothstep(0.6,1.5,dist)*(1.0 - smoothstep(28.0,50.0,pixels))
*(1.0 - smoothstep(share - 0.05,share,k.x))*landing;
if (fade <=0.002) return;
fade*=hallowSeen(dir,dist);
if (fade <=0.002) return;
vec3 a;
vec3 b;
if (pixels < 3.0) {
hallowFacing(dir,a,b);
} else {
float fast=FallDetail[3].w;
float turn=g.y < 0.5?-1.0:1.0;
float yaw=h.x*6.2831853 + fast*(0.3 + 0.5*g.y)*turn;
float over=k.y < 0.5?fast*(2.5 + 4.0*g.x)*turn + k.z*6.2831853
:0.9*sin(fast*(2.2 + 1.6*g.x) + k.z*6.2831853);
float rock=0.5*sin(fast*(1.7 + g.y) + h.z*9.0);
vec3 a0=vec3(cos(rock),sin(rock),0.0);
vec3 b0=vec3(sin(rock)*sin(over),-cos(rock)*sin(over),cos(over));
float cy=cos(yaw);
float sy=sin(yaw);
a=vec3(a0.x*cy + a0.z*sy,a0.y,-a0.x*sy + a0.z*cy);
b=vec3(b0.x*cy + b0.z*sy,b0.y,-b0.x*sy + b0.z*cy);
}
vec3 normal=cross(a,b);
vec2 q=HALLOW_CORNERS[corner];
gl_Position=ViewProj*vec4(rel + (a*q.x + b*q.y)*extent,1.0);
precipUv=q;
float back=FallDetail[2].w*pow(max(dot(dir,FallDetail[2].xyz),0.0),3.0);
precipLook=vec4(back,pixels,0.0,0.0);
hallowLook=vec4(0.0,k.z,dot(normal,-dir),fade);
hallowLight=hallowLit(rel,normal);
}
void hallowSkitter(int particle,int corner) {
const float LIVES=3.0;
vec3 h=random3(uint(particle)*2u + 1u);
vec3 g=random3(uint(particle)*2u + 2u);
vec2 slot=mod(h.xz*BoxSize - BoxOrigin.xz + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
float u=hallowGustPhase(hallowWorld(slot),Time)/HALLOW_PI + floor(g.x*LIVES);
float life=floor(u/LIVES);
u -=life*LIVES;
vec3 r=random3(mixBits(uint(particle) ^ 0xA511E9B3u) + uint(int(life))*0x9E3779B9u);
float speed=1.6*FallDetail[0].y*(0.6 + 0.8*h.y);
float perFront=speed*(35.0*HALLOW_PI/128.0)/0.45;
float travel=speed*hallowGustCarry(HALLOW_PI*u)/0.45;
float radius=(0.055 + 0.035*g.z)*(1.0 + FallDetail[0].x);
vec2 rest=slot + vec2(-W_WIND.y,W_WIND.x)*((r.x - 0.5)*4.0) - W_WIND*(0.5*LIVES*perFront);
ivec2 cr=ivec2(floor(rest - GridOrigin.xz));
if (!onMap(cr)) return;
vec4 start=ncColumnAt(Columns, cr);
if (!ncGroundCoverAllowed(Columns,cr)) return;
float ground=start.g;
float reach=travel;
for (int s=1;s <=24;++s) {
float along=float(s)*0.45;
if (along > travel + radius) break;
ivec2 cs=ivec2(floor(rest + W_WIND*along - GridOrigin.xz));
bool blocked=!onMap(cs);
if (!blocked) {
vec4 col=ncColumnAt(Columns, cs);
blocked=!ncGroundCoverAllowed(Columns,cs) || abs(col.g - ground) > 1.0/32.0;
}
if (blocked) {
vec2 enters=(vec2(cs) + GridOrigin.xz - rest)/W_WIND;
reach=min(travel,max(enters.x,enters.y) - radius*0.9);
break;
}
}
reach=max(reach,0.0);
vec2 at=rest + W_WIND*reach;
float over=HALLOW_PI*(1.0 + floor(h.x*3.0))*reach/max(perFront,0.001);
float yaw=r.y*6.2831853;
vec3 axis=vec3(W_WIND.y,0.0,-W_WIND.x);
vec3 a=hallowTurn(vec3(cos(yaw),0.0,sin(yaw)),axis,over);
vec3 b=hallowTurn(vec3(-sin(yaw),0.0,cos(yaw)),axis,over);
vec3 approxRel=vec3(at.x,ground + GridOrigin.y,at.y);
float dist=length(approxRel);
float pixels=radius/max(dist*PixelWorld,0.000001);
float halfH=(abs(a.y) + abs(b.y))*radius;
vec3 rel=vec3(at.x,ground + GridOrigin.y + halfH + LEAF_CLEARANCE,at.y);
dist=length(rel);
vec3 dir=rel/max(dist,0.0001);
float fade=smoothstep(0.0,0.2,u)*(1.0 - smoothstep(LIVES - 0.2,LIVES,u))
*(1.0 - smoothstep(0.32*BoxSize,0.46*BoxSize,max(abs(slot.x),abs(slot.y))))
*(1.0 - smoothstep(0.38*BoxSize,0.49*BoxSize,max(abs(at.x),abs(at.y))))
*smoothstep(0.4,1.0,dist)*(1.0 - smoothstep(28.0,50.0,pixels))
*rollingLeafVisibility(pixels,rel,cross(a,b));
if (fade <=0.002) return;
fade*=hallowSeen(dir,dist);
if (fade <=0.002) return;
vec3 normal=cross(a,b);
vec2 q=HALLOW_CORNERS[corner];
gl_Position=ViewProj*vec4(rel + (a*q.x + b*q.y)*radius,1.0);
precipUv=q;
float back=FallDetail[2].w*pow(max(dot(dir,FallDetail[2].xyz),0.0),3.0);
precipLook=vec4(back,pixels,0.0,0.0);
hallowLook=vec4(1.0,r.z,dot(normal,-dir),fade);
hallowLight=hallowLit(rel,normal);
}
void hallowWing(vec3 pos,vec3 velocity,vec3 accel,float span,float beat,float side,int corner,
float group,float variety,float fade) {
vec3 heading=normalize(velocity);
vec3 level=cross(heading,vec3(0.0,1.0,0.0));
float across=length(level);
level=across > 0.001?level/across:vec3(1.0,0.0,0.0);
float bank=clamp(dot(accel,level)*0.08,-0.7,0.7);
vec3 out1=hallowTurn(level,heading,bank);
vec3 up=cross(out1,heading);
float flap=0.9*sin(beat);
vec3 wing=side*out1*cos(flap) + up*sin(flap);
float dist=length(pos);
vec3 dir=pos/max(dist,0.0001);
float pixels=span/max(dist*PixelWorld,0.000001);
float grow=max(1.0,1.3/max(pixels,0.001));
vec2 q=HALLOW_CORNERS[corner];
float along=q.x*0.5 + 0.5;
gl_Position=ViewProj*vec4(pos + (wing*along + heading*(q.y*0.55))*span*grow,1.0);
precipUv=vec2(along,q.y);
float back=FallDetail[2].w*pow(max(dot(dir,FallDetail[2].xyz),0.0),4.0)*0.25;
precipLook=vec4(back,pixels,0.0,0.0);
hallowLook=vec4(group,variety,0.0,fade*hallowSeen(dir,dist));
hallowLight=hallowLit(pos,up);
}
void hallowBat(int index,int corner) {
int bat=index/2;
float side=index - bat*2==0?-1.0:1.0;
vec3 h=random3(uint(bat)*2u + 0x7F4A7C15u);
vec3 g=random3(uint(bat)*2u + 0x7F4A7C16u);
vec4 lamp=FallDetail[5 + (bat - (bat/2)*2)];
vec3 home;
float loop;
float fade=1.0;
if (h.x < 0.34 && lamp.w > 0.0) {
home=lamp.xyz + vec3(0.0,2.0 + 2.0*h.y,0.0);
loop=2.0 + 2.0*h.z;
} else {
vec2 xz=mod(h.yz*48.0 - BoxOrigin.xz + 24.0,48.0) - 24.0;
ivec2 c=ivec2(floor(xz - GridOrigin.xz));
float floorY=-1.62;
if (onMap(c)) {
vec4 col=ncColumnAt(Columns, c);
if (col.a > 0.5) floorY=col.r + GridOrigin.y;
}
home=vec3(xz.x,floorY + 3.0 + 7.0*g.x,xz.y);
loop=3.0 + 5.0*g.y;
fade=1.0 - smoothstep(15.0,21.0,max(abs(xz.x),abs(xz.y)));
}
float t=Time;
float w1=6.0/loop*(0.8 + 0.3*g.z);
float w2=w1*(1.3 + 0.4*h.y);
float w3=w1*(0.7 + 0.5*h.z);
float p1=h.z*6.2831853;
float p2=g.x*6.2831853;
float p3=g.y*6.2831853;
vec3 pos=home + vec3(loop*sin(w1*t + p1),0.8*sin(w3*t + p3),0.8*loop*sin(w2*t + p2));
vec3 vel=vec3(loop*w1*cos(w1*t + p1),0.8*w3*cos(w3*t + p3),0.8*loop*w2*cos(w2*t + p2));
vec3 acc=-vec3(loop*w1*w1*sin(w1*t + p1),0.8*w3*w3*sin(w3*t + p3),
0.8*loop*w2*w2*sin(w2*t + p2));
float j=4.3 + 2.0*g.y;
pos +=0.35*vec3(sin(j*t + 1.3),0.6*sin(1.37*j*t),cos(1.13*j*t + 0.7));
vel +=0.35*j*vec3(cos(j*t + 1.3),0.822*cos(1.37*j*t),-1.13*sin(1.13*j*t + 0.7));
acc -=0.35*j*j*vec3(sin(j*t + 1.3),1.126*sin(1.37*j*t),1.277*cos(1.13*j*t + 0.7));
fade*=smoothstep(0.6,1.5,length(pos));
if (fade <=0.002) return;
float beat=FallDetail[3].w*6.2831853*(9.0 + 3.0*g.x) + h.y*6.2831853;
hallowWing(pos,vel,acc,0.18 + 0.05*g.z,beat,side,corner,2.0,h.z,fade);
}
void hallowColony(int index,int corner) {
vec4 moon=FallDetail[2];
float event=FallDetail[0].w;
if (moon.w <=0.0 || event < 0.0) return;
float across=fract(event)/0.999;
int bat=index/2;
float side=index - bat*2==0?-1.0:1.0;
vec3 e=random3(uint(floor(event))*0x51u + 0x1F123BB5u);
vec3 b=random3(uint(bat)*2u + 0x2B992DDFu);
vec3 m=normalize(moon.xyz);
vec3 right=normalize(cross(m,vec3(0.0,1.0,0.0)));
vec3 up=cross(right,m);
float slant=(e.x - 0.5)*1.4;
vec3 lane=(right*cos(slant) + up*sin(slant))*(e.y < 0.5?-1.0:1.0);
vec3 bend=cross(m,lane);
float range=20.0 + 15.0*e.z;
float span=range*1.8;
float s=across*1.45 - 0.45*b.x;
float along=(s - 0.5)*span;
float rate=span*1.45/8.0;
float wave=along*0.12 + e.x*6.2831853;
vec3 pos=m*(range + (b.z - 0.5)*3.0) + lane*along
+ bend*(1.8*sin(wave) + (b.y - 0.5)*2.4 + 0.25*sin(Time*(3.0 + 2.0*b.z) + b.x*9.0));
vec3 vel=lane*rate + bend*(1.8*0.12*rate*cos(wave));
vec3 acc=-bend*(1.8*0.0144*rate*rate*sin(wave));
float fade=smoothstep(0.0,0.06,s)*(1.0 - smoothstep(0.94,1.0,s));
if (fade <=0.002) return;
float beat=FallDetail[3].w*6.2831853*(9.0 + 3.0*b.y) + b.z*6.2831853;
hallowWing(pos,vel,acc,0.2 + 0.1*b.y,beat,side,corner,3.0,b.x,fade);
}
bool hallowWall(ivec2 c,float top) {
if (!onMap(c)) return false;
vec4 col=ncColumnAt(Columns, c);
return col.a > 0.5 && abs(col.b - 5.0) < 0.5 && col.r >=top + 2.0;
}
void hallowWeb(int index,int corner) {
float window=floor(FallDetail[0].z);
float density=fract(FallDetail[0].z)/0.999;
if (window < 1.0 || density <=0.0) return;
int side=int(window);
int cz=index/side;
ivec2 c=ivec2(32 - side/2 + (index - cz*side),32 - side/2 + cz);
uvec2 world=uvec2(mod(FallDetail[3].xy + vec2(c),4096.0));
vec3 h=random3(world.x*0x8DA6B343u ^ world.y*0xD8163841u ^ 0x2C1B3C6Du);
if (h.x >=density*0.08) return;
vec4 me=ncColumnAt(Columns, c);
if (me.a < 0.5 || abs(me.b - 2.0) < 0.5) return;
bool east=hallowWall(c + ivec2(1,0),me.r);
bool west=hallowWall(c - ivec2(1,0),me.r);
bool south=hallowWall(c + ivec2(0,1),me.r);
bool north=hallowWall(c - ivec2(0,1),me.r);
bool alongX=east || west;
bool alongZ=south || north;
if (!alongX && !alongZ) return;
float wx=east && (!west || h.y < 0.5)?1.0:-1.0;
float wz=south && (!north || h.z < 0.5)?1.0:-1.0;
float size=0.6 + 0.4*h.y;
vec3 cellMin=vec3(float(c.x) + GridOrigin.x,me.r + GridOrigin.y,float(c.y) + GridOrigin.z);
vec3 centre;
vec3 a;
vec3 b;
if (alongX && alongZ) {
vec3 inside=cellMin + vec3(wx > 0.0?1.0:0.0,0.0,wz > 0.0?1.0:0.0);
float reach=0.6*size;
vec3 onX=inside + vec3(0.0,0.0,-wz*reach);
vec3 onZ=inside + vec3(-wx*reach,0.0,0.0);
centre=0.5*(onX + onZ) + vec3(0.0,0.25 + 0.9*h.z + 0.5*size,0.0);
a=0.5*(onZ - onX);
b=vec3(0.0,0.5*size,0.0);
} else {
vec3 toWall=alongX?vec3(wx,0.0,0.0):vec3(0.0,0.0,wz);
vec3 lengthwise=alongX?vec3(0.0,0.0,1.0):vec3(1.0,0.0,0.0);
vec3 foot=cellMin + vec3(0.5,0.0,0.5) + toWall*0.5 + lengthwise*((h.z - 0.5)*0.5);
float lean=0.7*size;
centre=foot + (vec3(0.0,1.0,0.0) - toWall)*(0.5*lean);
a=lengthwise*(0.5*size);
b=(vec3(0.0,1.0,0.0) + toWall)*(0.5*lean);
}
float dist=length(centre);
vec3 dir=centre/max(dist,0.0001);
float edge=0.5*window;
float fade=(1.0 - smoothstep(0.65*edge,edge - 0.5,max(abs(centre.x),abs(centre.z))))
*(1.0 - smoothstep(12.0,16.0,dist))*smoothstep(0.5,1.2,dist);
if (fade <=0.002) return;
fade*=hallowSeen(dir,dist);
vec4 moon=FallDetail[2];
float silver=0.05*FallDetail[7].w
+ moon.w*(0.6*pow(max(dot(dir,moon.xyz),0.0),3.0) + 0.05);
vec3 light=vec3(silver)*vec3(0.85,0.92,1.0);
for (int j=5;j < 7;++j) {
vec4 lamp=FallDetail[j];
if (lamp.w <=0.0) continue;
vec3 to=lamp.xyz - centre;
float d2=dot(to,to);
float forward=0.3 + 0.7*pow(max(dot(dir,to*inversesqrt(max(d2,0.0001))),0.0),2.0);
light +=lamp.w*FallDetail[7].rgb*(0.8*forward/(1.0 + d2/2.25));
}
vec2 q=HALLOW_CORNERS[corner];
gl_Position=ViewProj*vec4(centre + a*q.x + b*q.y,1.0);
precipUv=q;
precipLook=vec4(0.0,0.0,FallDetail[3].w,0.0);
hallowLook=vec4(4.0,h.z,alongX && alongZ?1.0:0.0,fade);
hallowLight=light;
}
void halloweenAir(int particle,int corner) {
float p=float(particle);
vec4 ends=FallDetail[1];
if (p < ends.x) hallowFalling(particle,corner);
else if (p < ends.y) hallowSkitter(particle,corner);
else if (p < ends.z) hallowBat(particle - int(ends.y),corner);
else if (p < ends.w) hallowColony(particle - int(ends.z),corner);
else hallowWeb(particle - int(ends.w),corner);
}
float snowDriftFloor(vec2 at) {
ivec2 c=ivec2(floor(at - GridOrigin.xz));
if (!onMap(c)) return -1000.0;
vec4 top=ncColumnAt(Columns, c);
if (top.a < 0.5 || abs(top.b - 2.0) < 0.5) return -1000.0;
return abs(top.b - 3.0) < 0.5?top.g:top.r;
}
void snowDrift(int index,int corner) {
vec3 h=random3(uint(index)*2u + 1u);
vec3 g=random3(uint(index)*2u + 2u);
float wind=max(1.0 + FallDetail[0].z,0.0);
float gust=smoothstep(0.3,0.9,0.5 + 0.5*sin(Time*0.19 + g.z*0.9)*sin(Time*0.083 + 1.3));
float show=FallDetail[0].w*gust*clamp(wind*0.8,0.0,1.0);
if (show <=0.004) return;
vec3 dir=vec3(SNOW_WIND.x,0.0,SNOW_WIND.y);
float run=(3.0 + 3.0*g.x)*wind*Speed;
vec3 rel=mod(h*BoxSize + dir*(run*Time) - BoxOrigin + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
float len=0.7 + 1.3*g.y;
float ground=snowDriftFloor(rel.xz);
float ahead=snowDriftFloor(rel.xz + dir.xz*(len*0.5));
float behind=snowDriftFloor(rel.xz - dir.xz*(len*0.5));
if (ground < -999.0 || abs(ahead - ground) > 0.3 || abs(behind - ground) > 0.3) return;
rel.y=ground + GridOrigin.y + 0.06 + 0.3*g.z*g.z;
float dist=length(rel);
float fade=(1.0 - smoothstep(0.32*BoxSize,0.5*BoxSize,max(abs(rel.x),abs(rel.z))))
*smoothstep(1.0,2.5,dist)*show*exp(-FallDetail[1].z*dist);
vec3 up=cross(dir,rel/max(dist,0.0001));
float across=length(up);
fade*=smoothstep(0.08,0.25,across);
if (fade <=0.002) return;
up=up/max(across,0.0001);
if (up.y < 0.0) up=-up;
float thick=max(0.07 + 0.08*g.z,1.5*dist*PixelWorld);
const vec2 CORNERS[6]=vec2[6](vec2(-1.0,-1.0),vec2(1.0,-1.0),vec2(1.0,1.0),
vec2(-1.0,-1.0),vec2(1.0,1.0),vec2(-1.0,1.0));
vec2 q=CORNERS[corner];
gl_Position=ViewProj*vec4(rel + dir*(q.x*len*0.5) + up*(q.y*thick),1.0);
precipUv=q;
precipLook=vec4(h.x,2.0,fade,g.y);
}
void petalSeeds(int particle,inout vec3 at,inout vec3 g,out float flower,out float rank) {
uint flock=uint(particle) >> 2u;
vec3 k=random3(flock*2u + 0x51ed27u);
if (k.x < 0.15) {
at=random3(flock*2u + 0x2545f49u) + (at - 0.5)*(0.3/BoxSize);
g=vec3(k.y,k.z,g.z);
}
vec3 r=random3(uint(particle)*2u + 0x6d2b79f5u);
flower=step(r.x,FallDetail[0].y);
rank=r.y;
}
float petalShare(ivec2 c,float clock,float rank) {
float trees=0.0;
for (int i=0;i < 5;++i) {
ivec2 at=c + (i==1?ivec2(2,0):i==2?ivec2(-2,0):i==3?ivec2(0,2)
:i==4?ivec2(0,-2):ivec2(0));
if (!onMap(at)) continue;
vec4 column=ncColumnAt(Columns, at);
if (column.a > 0.5 && abs(column.b - 3.0) < 0.5) trees +=0.2;
}
float more=FallDetail[1].x;
float near=FallDetail[0].w;
float share=(1.0 + more*blizzard(clock))*(1.0 + near*trees)/((1.0 + more)*(1.0 + near));
return clamp((share - rank)*25.0 + 0.5,0.0,1.0);
}
vec3 rotateAbout(vec3 v,vec3 axis,float angle) {
float c=cos(angle);
float s=sin(angle);
return v*c + cross(axis,v)*s + axis*dot(axis,v)*(1.0 - c);
}
float autumnLeafTopped(vec2 rel) {
ivec2 c=ivec2(floor(rel - GridOrigin.xz));
if (!onMap(c)) return 0.0;
vec4 top=ncColumnAt(Columns, c);
return top.a > 0.5 && abs(top.b - 3.0) < 0.5?1.0:0.0;
}
float autumnTrees(vec2 rel) {
vec2 grid=GridOrigin.xz - FallDetail[4].yz;
vec2 p=(rel - grid - 1.5)*0.5;
vec2 i=floor(p);
vec2 f=smoothstep(0.0,1.0,p - i);
vec2 at=i*2.0 + 1.5 + grid;
return mix(mix(autumnLeafTopped(at),autumnLeafTopped(at + vec2(2.0,0.0)),f.x),
mix(autumnLeafTopped(at + vec2(0.0,2.0)),autumnLeafTopped(at + vec2(2.0)),f.x),f.y);
}
vec4 autumnWhirl(out float turn) {
turn=0.0;
float often=FallDetail[4].x;
if (often <=0.0) return vec4(0.0);
float period=mix(80.0,20.0,often);
float cycle=floor(Time/period);
float u=(Time - cycle*period)/7.0;
vec3 h=random3(uint(cycle)*3u + 77u);
if (u >=1.0 || h.x > 0.5 + 0.5*often) return vec4(0.0);
vec3 at=vec3(h.y,0.0,h.z)*BoxSize;
at=mod(at - BoxOrigin + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
float off=length(at.xz);
if (off < 4.0 || off > 12.0) return vec4(0.0);
ivec2 c=ivec2(floor(at.xz - GridOrigin.xz));
if (!onMap(c)) return vec4(0.0);
vec4 top=ncColumnAt(Columns, c);
if (top.a < 0.5) return vec4(0.0);
at.y=top.g + GridOrigin.y;
turn=12.566371*smoothstep(0.0,1.0,u);
return vec4(at,smoothstep(0.0,0.25,u)*(1.0 - smoothstep(0.65,1.0,u)));
}
vec3 autumnWhirled(vec3 rel,vec4 whirl,float turn,float lift) {
if (whirl.w <=0.0) return rel;
vec2 d=rel.xz - whirl.xz;
float held=whirl.w*exp(-dot(d,d)/5.0)*(1.0 - smoothstep(3.0,7.0,rel.y - whirl.y));
if (held <=0.001) return rel;
float c=cos(turn);
float s=sin(turn);
vec2 carried=mix(rel.xz,whirl.xz + vec2(c*d.x - s*d.y,s*d.x + c*d.y),held);
return vec3(carried.x,rel.y + held*lift,carried.y);
}
float saturate1(float v) { return clamp(v,0.0,1.0);}
vec3 autumnAir(float t,vec3 h,vec3 g,bool glides,float rate,float swing,float phase,vec2 heading) {
vec3 wind=vec3(0.7,0.0,0.35)*(0.8 + 0.4*g.x) - vec3(0.0,rate,0.0);
vec3 sway;
if (glides) {
float th=t*swing + phase;
sway=vec3(heading.x,0.0,heading.y)*(0.6*sin(th)) - vec3(0.0,0.12*cos(2.0*th),0.0);
} else {
sway=vec3(sin(t*0.9 + phase)*0.35,0.0,cos(t*0.7 + 2.3*phase)*0.3);
}
vec3 world=h*BoxSize + (wind*t + gustAt(t)*0.6)*Speed + sway;
return mod(world - BoxOrigin + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
}
void autumnLeafQuad(vec3 rel,vec3 along,vec3 across,float extent,int corner,float open) {
const vec2 CORNERS[6]=vec2[6](vec2(-1.0,-1.0),vec2(1.0,-1.0),vec2(1.0,1.0),
vec2(-1.0,-1.0),vec2(1.0,1.0),vec2(-1.0,1.0));
vec2 q=CORNERS[corner];
gl_Position=ViewProj*vec4(rel + (across*q.x + along*q.y)*extent,1.0);
precipUv=q;
vec3 dir=rel/max(length(rel),0.0001);
vec3 rawNormal=cross(across,along);
float facingSign=mix(-1.0,1.0,smoothstep(-0.18,0.18,dot(rawNormal,-dir)));
vec3 normal=rawNormal*facingSign;
vec3 sun=FallDetail[1].xyz;
float lit=dot(normal,sun);
precipAutumn.x=mix(0.35,saturate1(lit),abs(facingSign));
precipAutumn.y=saturate1(-lit)*(0.25 + 0.75*pow(saturate1(dot(dir,sun)),3.0));
precipAutumn.w=open;
}

float autumnGroundPile(ivec2 cell, vec2 local, float height) {
    float strength = 0.0;
    for (int z = -1; z <= 1; ++z) {
        for (int x = -1; x <= 1; ++x) {
            if (x == 0 && z == 0) continue;
            ivec2 stepTo = ivec2(x,z);
            float distance = length(max(abs(local - 0.5 - vec2(stepTo)) - 0.5,0.0));
            if (distance >= 0.65) continue;
            vec4 neighbor = ncColumnAt(Columns,cell + stepTo);
            if (neighbor.a <= 0.5 || neighbor.b == 2.0) continue;
            float top = neighbor.b == 3.0 ? neighbor.g : neighbor.r;
            float barrier = smoothstep(0.08,0.65,top - height)*(1.0 - smoothstep(0.0,0.65,distance));
            strength = max(strength,barrier);
        }
    }
    return strength;
}

bool autumnSameSupport(ivec2 cell, float height) {
    return ncBroadGroundCover(Columns,cell) && abs(ncColumnAt(Columns,cell).g - height) < 0.01;
}

float autumnSupportScale(ivec2 cell, vec2 local, vec2 silhouette, float height) {
    vec2 edge = min(local,1.0 - local);
    ivec2 side = ivec2(2.0*step(vec2(0.5),local) - 1.0);
    vec2 fit = edge/max(silhouette,vec2(0.001));
    float scale = 1.0;
    if (fit.x < 1.0 && !autumnSameSupport(cell + ivec2(side.x,0),height)) scale = fit.x;
    if (fit.y < scale && !autumnSameSupport(cell + ivec2(0,side.y),height)) scale = fit.y;
    float diagonal = max(fit.x,fit.y);
    if (diagonal < scale && !autumnSameSupport(cell + side,height)) scale = diagonal;
    return scale;
}

void autumnGroundLeaf(int index, int corner) {
    int side = int(FallDetail[5].w);
    if (side <= 0 || index >= side*side*9) return;
    int tile = index/9;
    int slot = index%9;
    ivec2 cell = ivec2(tile%side,tile/side) + ivec2((64 - side)/2);
    uvec2 world = uvec2((ivec2(FallDetail[5].xy) + cell) & ivec2(1048575));
    uint seed = mixBits(world.x*0x9e3779b9u ^ world.y*0x85ebca6bu ^ uint(slot)*0xc2b2ae35u);
    vec3 h = random3(seed ^ 0x7f4a7c15u);
    if (h.x >= Ground*(1.48 + 0.65*FallDetail[5].z) || !ncBroadGroundCover(Columns,cell)) return;

    vec4 column = ncColumnAt(Columns,cell);
    vec3 g = random3(seed ^ 0xd1b54a35u);
    vec2 local = (vec2(slot%3,slot/3) + 0.5 + (g.xy - 0.5)*0.36)/3.0;
    vec2 xz = GridOrigin.xz + vec2(cell) + local;
    vec3 support = vec3(xz.x,column.g + GridOrigin.y,xz.y);
    float detail = ncAutumnGroundDetail(support,PixelWorld);
    if (detail <= 0.002) return;

    float trees = autumnTrees(xz);
    float share = Ground*(0.48 + trees);
    if (h.x > share - 0.07 && FallDetail[5].z > 0.0)
        share += Ground*0.65*FallDetail[5].z*autumnGroundPile(cell,local,column.g);
    float presence = 1.0 - smoothstep(clamp(share,0.0,0.96) - 0.07,clamp(share,0.0,0.96),h.x);
    float opacity = detail*presence;
    if (opacity <= 0.002) return;

    float angle = g.z*6.2831853;
    vec2 along = vec2(cos(angle),sin(angle));
    vec2 across = vec2(-along.y,along.x);
    float species = h.y < 0.45 ? 0.0 : (h.y < 0.75 ? 1.0 : 2.0);
    float halfWidth = species < 0.5 ? 0.94 : (species < 1.5 ? 0.76 : 0.70);
    float radius = mix(0.10,0.15,h.z);
    vec2 silhouette = radius*(abs(across)*halfWidth + abs(along));
    radius *= autumnSupportScale(cell,local,silhouette + 0.012,column.g);
    if (radius < 0.058) return;

    const vec2 CORNERS[6] = vec2[6](vec2(-1.0,-1.0),vec2(1.0,-1.0),vec2(1.0,1.0),
        vec2(-1.0,-1.0),vec2(1.0,1.0),vec2(-1.0,1.0));
    vec2 q = CORNERS[corner]*1.30;
    vec2 offset = radius*(across*q.x + along*q.y);
    gl_Position = ViewProj*vec4(support + vec3(offset.x,LEAF_CLEARANCE,offset.y),1.0);
    precipUv = q;
    precipLook = vec4(random3(seed ^ 0x94d049bbu).x,species,opacity,1.0);
    float openness = 1.0 - 0.70*trees;
    float daylight = FallDetail[1].w;
    float flatSun = saturate1(FallDetail[1].y);
    float light = mix(0.24,0.64,daylight) + 0.19*daylight*flatSun*openness;
    precipAutumn = vec4(light,length(support),(h.z - 0.5)*0.90,openness);
}

void autumnParticle(int particle,int corner) {
int dynamicCount=int(FallDetail[4].w);
if (particle >=dynamicCount) {
autumnGroundLeaf(particle - dynamicCount,corner);
return;
}
int falling=int(FallDetail[3].x);
int rolling=int(FallDetail[3].y);
int threads=int(FallDetail[3].z);
float turn;
vec4 whirl=autumnWhirl(turn);
if (particle >=falling + rolling + threads) {
if (whirl.w <=0.0) return;
int index=particle - falling - rolling - threads;
vec3 h=random3(uint(index)*2u + 120001u);
vec3 g=random3(uint(index)*2u + 120002u);
float up=whirl.w*(0.05 + 4.5*h.y);
float r=0.2 + 0.6*h.x*h.x + 0.3*up;
float a=h.z*6.2831853 + turn*(1.0 + 0.8/r) + Time*0.6;
vec3 rel=whirl.xyz + vec3(cos(a)*r,up + 0.05,sin(a)*r);
float dist=length(rel);
float radius=(0.07 + 0.035*g.z)*(1.0 + FallDetail[0].x);
float extent=max(radius,1.3*dist*PixelWorld);
float pixels=radius/max(dist*PixelWorld,0.000001);
float fade=smoothstep(0.0,0.25,whirl.w)*smoothstep(0.6,1.5,dist)*(1.0 - smoothstep(28.0,50.0,pixels));
if (fade <=0.002) return;
vec3 axis=normalize(vec3(cos(g.x*6.2831853),0.6*(g.y - 0.5),sin(g.x*6.2831853)));
float tumble=Time*(3.0 + 3.0*g.y) + h.x*6.2831853;
float yaw=g.z*6.2831853;
autumnLeafQuad(rel,rotateAbout(vec3(cos(yaw),0.0,sin(yaw)),axis,tumble),
rotateAbout(vec3(-sin(yaw),0.0,cos(yaw)),axis,tumble),extent*1.45,corner,
1.0 - 0.7*autumnTrees(rel.xz));
precipLook=vec4(g.y,h.y < 0.45?0.0:h.y < 0.75?1.0:2.0,fade,0.0);
precipAutumn.z=(g.z - 0.5)*1.4;
return;
}
if (particle >=falling + rolling) {
int index=particle - falling - rolling;
vec3 h=random3(uint(index)*2u + 90001u);
vec3 g=random3(uint(index)*2u + 90002u);
vec3 world=h*BoxSize + (vec3(0.55,0.0,0.3)*(0.7 + 0.6*g.x)*Time + gustAt(Time)*0.8)*Speed
+ vec3(0.0,sin(Time*0.21 + g.y*6.2831853)*0.8,0.0);
vec3 rel=mod(world - BoxOrigin + 0.5*BoxSize,BoxSize) - 0.5*BoxSize;
ivec2 c=ivec2(floor(rel.xz - GridOrigin.xz));
if (!onMap(c)) return;
vec4 top=ncColumnAt(Columns, c);
if (top.a < 0.5) return;
float above=rel.y - (top.r + GridOrigin.y);
float dist=length(rel);
float fade=smoothstep(0.8,1.6,above)*(1.0 - smoothstep(7.0,9.0,above))
*(1.0 - smoothstep(0.32*BoxSize,0.5*BoxSize,max(max(abs(rel.x),abs(rel.y)),abs(rel.z))))
*smoothstep(1.0,2.0,dist)
*smoothstep(0.0,0.2,fract(Time*0.02 + g.z))*(1.0 - smoothstep(0.8,1.0,fract(Time*0.02 + g.z)));
if (fade <=0.002) return;
float heading=g.z*6.2831853 + 0.3*sin(Time*0.3 + h.x*9.0);
vec3 along=normalize(vec3(cos(heading),0.25*sin(Time*0.5 + h.y*7.0),sin(heading)));
float half_=0.35 + 0.55*g.z;
vec3 dir=rel/max(dist,0.0001);
vec3 across=cross(along,dir);
across/=max(length(across),0.0001);
float drawn=max(0.002,1.2*dist*PixelWorld);
const vec2 CORNERS[6]=vec2[6](vec2(-1.0,-1.0),vec2(1.0,-1.0),vec2(1.0,1.0),
vec2(-1.0,-1.0),vec2(1.0,1.0),vec2(-1.0,1.0));
vec2 q=CORNERS[corner];
gl_Position=ViewProj*vec4(rel + along*(q.x*half_) + across*(q.y*drawn),1.0);
precipUv=q;
vec3 sun=FallDetail[1].xyz;
vec3 curl=(random3(uint(index)*2u + 90003u) - 0.5)*2.0 + vec3(0.0,0.3,0.0);
float a=dot(sun + dir,along);
float b=dot(sun + dir,curl);
precipLook=vec4(h.x,3.0,fade,pow(saturate1(dot(dir,sun)),2.0));
precipAutumn=vec4(a,b,0.002/drawn,1.0);
return;
}
vec3 h;
vec3 g;
vec3 k;
vec3 rel;
float dist;
float radius;
float fade;
vec3 along;
vec3 across;
float open=1.0;
    bool groundLeaf = particle >= falling;
    if (groundLeaf) {
        int index = particle - falling;
        h = random3(uint(index) * 2u + 40001u);
        g = random3(uint(index) * 2u + 40002u);
        k = random3(uint(index) * 2u + 50001u);
        const float w = 0.2618;
        float t = Time * Speed - g.x * 1.5;
        float travel = 0.375 * t - sin(2.0 * w * t) / (4.0 * w) + sin(4.0 * w * t) / (32.0 * w);
        vec2 way = normalize(vec2(0.83, 0.55) + (g.yz - 0.5) * 0.6);
        vec2 world = h.xz * BoxSize + way * (travel * (0.4 + 0.35 * g.y));
        vec2 flat2 = mod(world - BoxOrigin.xz + 0.5 * BoxSize, BoxSize) - 0.5 * BoxSize;
        ivec2 c = ivec2(floor(flat2 - GridOrigin.xz));
        if (!onMap(c)) return;
        vec4 top = ncColumnAt(Columns, c);
        if (!ncGroundCoverAllowed(Columns,c)) return;
        radius = (0.07 + 0.035 * g.z) * (1.0 + FallDetail[0].x);
        float roll = travel / (0.375 * 3.14159265 / w) * (1.0 + floor(g.z * 2.0));
        float angle = (0.62 + 0.37*k.x)*sin(3.14159265*roll + g.x*6.2831853);
        float yaw = h.y * 6.2831853;
        vec3 axis = vec3(-way.y, 0.0, way.x);
        along = rotateAbout(vec3(cos(yaw), 0.0, sin(yaw)), axis, angle);
        across = rotateAbout(vec3(-sin(yaw), 0.0, cos(yaw)), axis, angle);
        float halfH = (abs(along.y) + abs(across.y)) * radius * 1.45;
        rel = vec3(flat2.x, top.g + GridOrigin.y + halfH + LEAF_CLEARANCE, flat2.y);
        rel = autumnWhirled(rel, whirl, turn, 1.5 + 2.0 * k.x);
        open = 1.0 - 0.7 * autumnTrees(rel.xz);
        dist = length(rel);
        fade = rollingSupportFade(flat2, c, top.g, radius * 1.45);
    } else {
        h = random3(uint(particle) * 2u + 1u);
        g = random3(uint(particle) * 2u + 2u);
        k = random3(uint(particle) * 2u + 60001u);
        radius = (0.075 + 0.04 * g.z) * (1.0 + FallDetail[0].x);
        bool glides = k.x < 0.6;
        float rate = glides ? 0.55 + 0.25 * g.x : 0.9 + 0.4 * g.x;
        float swing = 1.1 + 0.6 * g.y;
        float phase = h.y * 6.2831853;
        vec2 heading = vec2(cos(k.y * 6.2831853), sin(k.y * 6.2831853));
        rel = autumnAir(Time, h, g, glides, rate, swing, phase, heading);
        float landing = leafLandingFade(rel, radius);
        if (landing <= 0.002) return;
        float trees = autumnTrees(rel.xz);
        open = 1.0 - 0.7 * trees;
        fade = saturate1((mix(0.7, 1.0, trees) - k.z) * 12.0) * landing;
        rel = autumnWhirled(rel, whirl, turn, 2.0 + 2.0 * k.x);
        float yaw = h.x * 6.2831853;
        vec3 along0 = vec3(cos(yaw), 0.0, sin(yaw));
        vec3 across0 = vec3(-sin(yaw), 0.0, cos(yaw));
        if (glides) {
            float th = Time * swing + phase;
            vec3 axis = vec3(-heading.y, 0.0, heading.x);
            float tilt = 0.6 * sin(th) + 0.12 * sin(Time * 3.1 + phase);
            vec3 spin = vec3(0.0, 1.0, 0.0);
            float wander = 0.4 * sin(Time * 0.37 + phase);
            along = rotateAbout(rotateAbout(along0, spin, wander), axis, tilt);
            across = rotateAbout(rotateAbout(across0, spin, wander), axis, tilt);
        } else {
            vec3 axis = normalize(vec3(cos(k.y * 6.2831853), 0.5 * (g.y - 0.5), sin(k.y * 6.2831853)));
            float angle = Time * (2.5 + 3.0 * g.z) * (h.z < 0.5 ? -1.0 : 1.0) + h.y * 6.2831853;
            along = rotateAbout(along0, axis, angle);
            across = rotateAbout(across0, axis, angle);
        }
        dist = length(rel);
    }
    float extent = groundLeaf ? radius : max(radius, 1.3 * dist * PixelWorld);
    float pixels = radius / max(dist * PixelWorld, 0.000001);
    if (groundLeaf) fade *= rollingLeafVisibility(pixels, rel, cross(across, along));
    fade *= (1.0 - smoothstep(0.32 * BoxSize, 0.5 * BoxSize, max(max(abs(rel.x), abs(rel.y)), abs(rel.z))))
        * smoothstep(0.6, 1.5, dist) * (1.0 - smoothstep(28.0, 50.0, pixels));
    if (fade <= 0.002) return;
    autumnLeafQuad(rel, along, across, extent * 1.45, corner, open);
float species=k.y < 0.45?0.0:k.y < 0.75?1.0:2.0;
precipLook=vec4(g.y,species,fade,0.0);
precipAutumn.z=(h.z - 0.5)*1.4;
}
void ncPrecipVertexMain() {
int particle=TV_VERTEX_ID/6;
int corner=TV_VERTEX_ID - particle*6;
precipUv=vec2(0.0);
precipLook=vec4(0.0);
hallowLook=vec4(0.0);
hallowLight=vec3(0.0);
precipAutumn=vec4(0.0);
gl_Position=vec4(0.0,0.0,0.0,1.0);
if (Kind==6) {
halloweenAir(particle,corner);
return;
}
if (Kind==1 && FallDetail[0].w > 0.0 && float(particle) >=FallDetail[1].x) {
snowDrift(particle,corner);
return;
}
if (Kind==4) {
autumnParticle(particle,corner);
return;
}
bool flutters=Kind !=1;
vec3 h=random3(uint(particle)*2u + 1u);
vec3 g=random3(uint(particle)*2u + 2u);
vec3 at=h;
float flower=0.0;
float rank=0.0;
if (Kind==3) petalSeeds(particle,at,g,flower,rank);
Fall f=fallOf(Time,g);
if (flower > 0.5) f.radius*=1.9;
vec3 rel=inAir(Time,at,g);
float clock=Time;
float lying=-1.0;
float landing=flutters?leafLandingFade(rel,f.radius):1.0;
if (landing <=0.002) return;
ivec2 c=ivec2(floor(rel.xz - GridOrigin.xz));
if (!flutters && onMap(c)) {
vec4 top=ncColumnAt(Columns, c);
float floorY=top.r;
float under=floorY - (rel.y - GridOrigin.y);
        if (top.a > 0.5 && under > 0.0) {
            float since = under / max(-f.wind.y * Speed, 0.01);
            float keep = Ground * (abs(top.b - 2.0) < 0.5 ? f.lies.y : f.lies.x);
            if (since >= keep) return;
            clock = Time - since;
            rel = inAir(clock, at, g);
            ivec2 landed = ivec2(floor(rel.xz - GridOrigin.xz));
            float landFloor = floorY;
            if (landed != c) {
                if (!onMap(landed)) return;
                vec4 there = ncColumnAt(Columns, landed);
                if (there.a < 0.5) return;
                float tf = there.r;
                if (abs(tf - floorY) > 0.65) return;
                landFloor = tf;
            }
            rel.y = landFloor + GridOrigin.y;
            lying = since / max(keep, 0.001);
        }
}
float dist=length(rel);
float radius=f.radius;
float pixels=radius/max(dist*PixelWorld,0.000001);
float extent=max(radius,1.3*dist*PixelWorld);
float fade=(1.0 - smoothstep(0.32*BoxSize,0.5*BoxSize,
max(max(abs(rel.x),abs(rel.y)),abs(rel.z))))
*smoothstep(0.6,1.5,dist)*(1.0 - smoothstep(28.0,50.0,pixels))*landing;
if (lying >=0.0) fade*=1.0 - smoothstep(0.35,1.0,lying);
if (Kind==1) fade*=exp(-FallDetail[1].z*dist);
if (Kind==3 && fade > 0.002) {
ivec2 sc=ivec2(floor(rel.xz - GridOrigin.xz));
if (onMap(sc)) fade*=petalShare(sc,clock,rank);
}
if (fade <=0.002) return;
vec3 dir=rel/max(dist,0.0001);
vec3 right=cross(dir,vec3(0.0,1.0,0.0));
float across=length(right);
right=across > 0.001?right/across:vec3(1.0,0.0,0.0);
vec3 up=cross(right,dir);
const vec2 CORNERS[6]=vec2[6](vec2(-1.0,-1.0),vec2(1.0,-1.0),vec2(1.0,1.0),
vec2(-1.0,-1.0),vec2(1.0,1.0),vec2(-1.0,1.0));
vec2 q=CORNERS[corner];
vec2 shape=q*1.25;
float face=1.0;
vec3 lift=lying >=0.0?vec3(0.0,extent,0.0):vec3(0.0);
if (flutters) {
float turning=flower > 0.5?clock*0.3:clock;
float whirling=flower > 0.5?clock*2.4:clock;
face=abs(cos(turning*(1.2 + 1.4*g.x) + h.y*11.0));
float spin=(h.z < 0.5?-1.0:1.0)*whirling*(0.5 + 0.6*g.y) + h.x*6.2831853;
shape=vec2(q.x*mix(0.32,1.0,face),q.y*(flower > 0.5?1.0:1.55));
float cs=cos(spin);
float sn=sin(spin);
shape=vec2(cs*shape.x - sn*shape.y,sn*shape.x + cs*shape.y);
}
precipUv=flutters?q:q*1.25;
float look=Kind==1 && snowClump(g)?1.0:0.0;
if (Kind==1 && lying < 0.0 && look < 0.5) {
vec3 v=f.wind*Speed;
vec2 seen=vec2(dot(v,right),dot(v,up));
float run=min(length(seen)*(0.5/80.0)/extent,4.0);
if (run > 0.05) {
vec2 way=seen/length(seen);
precipUv=vec2(q.x*(1.25 + run),q.y*1.25);
shape=way*precipUv.x + vec2(-way.y,way.x)*precipUv.y;
look=3.0 + run;
}
}
gl_Position=ViewProj*vec4(rel + lift + (right*shape.x + up*shape.y)*extent,1.0);
precipLook=vec4(h.x,Kind==3?flower:look,fade,flutters?face:pixels);
}

void main() {
    precipClip = vec4(0.0, 0.0, 0.0, 1.0);
    ncPrecipVertexMain();
    precipClip = gl_Position;
}
