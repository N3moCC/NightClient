package dev.nightclient.platform;

import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PlayerHealthView;
import dev.nightclient.api.PlayerPingView;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TotemCounterView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.api.TargetView;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.runtime.BetterChatLogic;
import dev.nightclient.runtime.ClientRuntime;
import dev.nightclient.runtime.ChatCoordinateParser;
import dev.nightclient.runtime.CombatTagDetector;
import dev.nightclient.runtime.PendingLeaveGuard;
import dev.nightclient.runtime.ExplosionRingLogic;
import dev.nightclient.runtime.HwPvpAndEventsLogic;
import dev.nightclient.runtime.ItemScrollerLogic;
import dev.nightclient.runtime.SoundControlLogic;
import dev.nightclient.runtime.TapeMouseLogic;
import dev.nightclient.runtime.TooltipsLogic;
import dev.nightclient.renderer.TooltipOverlayRenderer;
import dev.nightclient.platform.mixin.ContainerFeatureAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
@@WORLD_IMPORTS@@

public final class WorldFeatureBridge {
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final ItemStack[] armorStacks=new ItemStack[4];
    private static List<InventorySlot> armor=Collections.emptyList();
    private static List<PotionView> potions=Collections.emptyList();
    private static VitalsView vitals=VitalsView.EMPTY;
    private static Object world,player;
    private static int tick=Integer.MIN_VALUE;
    private static int snapshotFlags=-1;
    private static LivingEntity attacked;
    private static long attackNanos;
    private static final Map<Integer,LivingEntity> damageTargets=new LinkedHashMap<Integer,LivingEntity>();
    private static Object gestureScreen;
    private static ItemStack gestureItem=ItemStack.EMPTY;
    private static final java.util.Set<Integer> dragged=new java.util.HashSet<Integer>();
    private static long lastScrollNanos,lastDragNanos;
    private static volatile Policy policy=Policy.EMPTY;
    private static long policyRevision=Long.MIN_VALUE;
    private static int policyTick=Integer.MIN_VALUE;
    private static boolean deathLatched=false;
    private static double prevPlayerX=0,prevPlayerZ=0;
    private static double playerSpeedBps=0;
    private static final Map<UUID,Long> enchantedAppleUntil=new HashMap<UUID,Long>();
    private static final Map<UUID,Integer> totemPops=new HashMap<UUID,Integer>();
    private static final Map<UUID,Long> totemLastPopMs=new HashMap<UUID,Long>();
    private static final List<Long> lmbClicks=new ArrayList<Long>();
    private static final List<Long> rmbClicks=new ArrayList<Long>();
    private static boolean prevAttackDown=false,prevUseDown=false;
    private static Object spawnLockedLevel;
    private static String spawnLockedKey="";
    private static volatile long lastPvpHitNanos=0L;
    private static volatile long lastLeaveBlockedNanos=0L;
    private static volatile String lastLeaveBlockedReason="";
    private static long lastCtWarnChatNanos=0L;
    private static Object lastPauseDisconnectBtn=null;
    private static net.minecraft.network.chat.Component lastPauseDisconnectOrig=null;
    private static volatile int reloadGeneration=0;
    private static final java.util.regex.Pattern MC_COLOR_PATTERN=java.util.regex.Pattern.compile("§[0-9a-fk-orxA-FK-ORX]");
    private static final java.util.regex.Pattern AMP_COLOR_PATTERN=java.util.regex.Pattern.compile("&[0-9a-fk-orA-FK-OR]");
    private static final java.util.regex.Pattern JSON_TEXT_PATTERN=java.util.regex.Pattern.compile("\"(?:text|translate)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final java.util.regex.Pattern REALM_LEAD_PATTERN=java.util.regex.Pattern.compile("^[\\s▍▎▏▌▋▊▉█•·»›|:–—-]+");
    private static final java.util.regex.Pattern REALM_HASH_PATTERN=java.util.regex.Pattern.compile(".*#\\d+.*");
    private static final Map<Integer,Boolean> losCache=new HashMap<Integer,Boolean>();
    private static int losCacheTick=Integer.MIN_VALUE;
    private static double losCamX=Double.NaN,losCamY=Double.NaN,losCamZ=Double.NaN;
    private static boolean chatPrevScissor=false;
    private static final int[] chatPrevScissorBox=new int[4];
    private static final BetterChatLogic.DuplicateTracker chatDedup=new BetterChatLogic.DuplicateTracker(10);
    private static float chatSlideOffset=0f,chatBoxW=0f,chatBoxH=0f,chatBoxAlpha=0f;
    private static float chatTextOriginX=4f,chatTextOriginBottomY=0f,chatHudScale=1f;
    private static boolean chatTransformActive=false,chatClipActive=false,chatLastOriginValid=false;
    private static int chatClipX1=0,chatClipY1=0,chatClipX2=0,chatClipY2=0;
    private static long chatLastRenderNanos=0L;
    private static boolean chatCardActive=false;
    private static String savedChatDraft="";
    private static boolean chatJustSubmitted=false;
    private static String chatToastText="";
    private static long chatToastNanos=0L;
    private static boolean blockLerpValid=false;
    private static double blockLerpMinX,blockLerpMinY,blockLerpMinZ,blockLerpMaxX,blockLerpMaxY,blockLerpMaxZ;
    private static long blockLerpLastNanos=0L;
    private static double glintAccumMillis=0.0;
    private static long glintLastRealMs=0L;
    private static long glintLastUploadMs=0L;
    private static int glintLastColor=0;
    private static float glintLastIntensity=-1f;
    private static boolean glintActiveOverride=false;
    private static Object glintLastResourceManager=null;
    private static final Map<String,int[]> glintBasePixels=new HashMap<String,int[]>();
    private static final Map<String,int[]> glintBaseDims=new HashMap<String,int[]>();
    private static final Map<String,net.minecraft.client.renderer.texture.DynamicTexture> glintTextures=new HashMap<String,net.minecraft.client.renderer.texture.DynamicTexture>();
    private static final Map<Integer,Integer> tntMaxFuse=new HashMap<Integer,Integer>();
    private static ExplosionRingLogic.TntSpec recentHeldTntSpec=null;
    private static long recentHeldTntMs=0L;
    private static final List<net.minecraft.core.BlockPos> cachedAnchors=new ArrayList<net.minecraft.core.BlockPos>();
    private static int cachedAnchorTick=Integer.MIN_VALUE;
    private static volatile List<ExplosionRingLogic.HazardBadge> latestExplosionBadges=Collections.emptyList();
    private static final ItemStack[] tooltipItemCache=new ItemStack[56];
    private static ItemStack hoveredTooltipStack=ItemStack.EMPTY;
    private static TooltipsLogic.HwItemInfo hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;
    private static long hoveredTooltipNanos=0L;
    private static ItemStack lockedPeekStack=ItemStack.EMPTY;
    private static float lockedAnchorX=0f,lockedAnchorY=0f,lockedAnchorW=0f,lockedAnchorH=0f;
    private static float lastTooltipRectX=0f,lastTooltipRectY=0f,lastTooltipRectW=0f,lastTooltipRectH=0f;
    private static long lastTooltipRectNanos=0L;
    private static boolean altTogglePrevDown=false;
    private static boolean altFlippedPeekMode=false;
    private static final float[] lastLockedPeekBounds=new float[4];
    private static boolean lastLockedPeekBoundsValid=false;
    private static Object lastCenteredContainerScreen=null;
    private static volatile long packetCtSetNanos=0L;
    private static volatile int packetCtDurationSec=0;
    private static volatile long lastOutgoingPlayerAttackNanos=0L;
    private static volatile int lastOutgoingAttackedPlayerId=Integer.MIN_VALUE;
    private static volatile int localPlayerEntityId=Integer.MIN_VALUE;
    private static volatile long lastUnattributedHurtNanos=0L;
    private static volatile long lastNettyDamageNanos=0L;
    private static volatile long lastConfirmedNonPvpDamageNanos=0L;
    private static volatile boolean bossBarHadCt=false;
    private static volatile boolean suppressOwnChatCt=false;
    private static boolean bypassNextLeave=false;
    private static final PendingLeaveGuard pendingLeaveGuard=new PendingLeaveGuard();
    private static final class BrokenBlockEntry {
        final String path;
        final long expiresNanos;
        BrokenBlockEntry(String path,long expiresNanos){this.path=path;this.expiresNanos=expiresNanos;}
    }
    private static final Map<Long,BrokenBlockEntry> recentBrokenBlocks=new LinkedHashMap<Long,BrokenBlockEntry>(24,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,BrokenBlockEntry> eldest){return size()>24;}
    };
    private static final java.util.IdentityHashMap<Object,Integer> customTntSoundTags=new java.util.IdentityHashMap<Object,Integer>();
    private static final Map<Integer,Integer> tntLastCountdownTick=new HashMap<Integer,Integer>();
    private static int lastHwHoloScanTick=Integer.MIN_VALUE;
    private static int lastTapeHurtTime=0;
    private static net.minecraft.client.KeyMapping tapeCustomMapping=null;
    private static String tapeCustomMappingKey="";
    private static boolean armorHitColorActive=false;
    private static final dev.nightclient.runtime.TabAnimationLogic tabAnimator=new dev.nightclient.runtime.TabAnimationLogic();
    private static long lastPvpCmdWarnNanos=0L;
    private static volatile String lastTabHeader="";
    private static final java.util.regex.Pattern SENSITIVE_HISTORY_CMD=java.util.regex.Pattern.compile("(?i)^/(?:login|l|reg|register|changepass|changepassword|cp|auth|2fa|pin)\\b.*");

    private WorldFeatureBridge(){}

    public static final class Policy {
        static final Policy EMPTY=new Policy(null);
        public final boolean hitColor,hitColorArmor,hitboxFill,noHurtCam,ownNametag,invisibleArmorNametag,invisibleArmorHitbox,fullBright,waterOverlay,waterFog,lavaFog,itemPhysics,grounded,hitbox,players,mobs,items,eyeLine,lowFire,hideFire,noTotem,noPumpkin,noPowderSnow,noBlockOverlay,noPortal,noNausea,noVignette,noBossBar,blockOverlay,explosionRing,enchantGlint,hwHelperEsp;
        public final int hurtColor,boxColor;
        public final float illumination,rotationSpeed,lineWidth,range,fireOffset;
        Policy(Settings s){
            hitColor=!FeaturePolicy.isBlocked("hitColor")&&s!=null&&s.hitColor.enabled&&!"hitbox".equals(s.hitColor.mode);
            hitColorArmor=hitColor&&s.hitColor.armor;
            hitboxFill=!FeaturePolicy.isBlocked("hitColor")&&s!=null&&s.hitColor.enabled&&!"entity".equals(s.hitColor.mode);
            hurtColor=s==null?0xffff0000:(Math.round(((s.hitColor.color>>>24)&255)*s.hitColor.opacity)<<24)|(s.hitColor.color&0xffffff);
            boolean tweaksy=!FeaturePolicy.isBlocked("tweaksy")&&s!=null&&s.tweaksy!=null&&s.tweaksy.enabled;
            boolean nr=!FeaturePolicy.isBlocked("noRender")&&s!=null&&s.noRender!=null&&s.noRender.enabled;
            noHurtCam=nr&&s.noRender.hurtCam;
            ownNametag=tweaksy&&!FeaturePolicy.isBlocked("tweaksy.ownNametag")&&s.tweaksy.ownNametag!=null&&s.tweaksy.ownNametag.enabled;
            invisibleArmorNametag=tweaksy&&!FeaturePolicy.isBlocked("tweaksy.invisibleArmorNametag")&&s.tweaksy.invisibleArmorNametag!=null&&s.tweaksy.invisibleArmorNametag.enabled;
            invisibleArmorHitbox=invisibleArmorNametag&&s.tweaksy.invisibleArmorNametag.hitbox;
            fullBright=tweaksy&&!FeaturePolicy.isBlocked("tweaksy.fullBright")&&s.tweaksy.fullBright.enabled;illumination=s==null?1:s.tweaksy.fullBright.strength;
            waterOverlay=nr&&s.noRender.waterOverlay;
            waterFog=nr&&s.noRender.waterFog;
            lavaFog=nr&&s.noRender.lavaFog;
            boolean nrFire=nr&&!FeaturePolicy.isBlocked("lowFire")&&s.noRender.fire;
            boolean lowFireMod=!FeaturePolicy.isBlocked("lowFire")&&s!=null&&s.lowFire.enabled;
            lowFire=nrFire||lowFireMod;
            hideFire=(nrFire&&s.noRender.hideFire)||(lowFireMod&&s.lowFire.hidden);
            fireOffset=nrFire?s.noRender.fireOffset:(lowFireMod?s.lowFire.offsetY:0);
            noTotem=nr&&s.noRender.totem;noPumpkin=nr&&s.noRender.pumpkin;noPowderSnow=nr&&s.noRender.powderSnow;
            noBlockOverlay=nr&&s.noRender.blockOverlay;noPortal=nr&&s.noRender.portal;noNausea=nr&&s.noRender.nausea;
            noVignette=nr&&s.noRender.vignette;noBossBar=nr&&s.noRender.bossBar;
            blockOverlay=!FeaturePolicy.isBlocked("blockOverlay")&&s!=null&&s.blockOverlay!=null&&s.blockOverlay.enabled;
            explosionRing=!FeaturePolicy.isBlocked("explosionRing")&&s!=null&&s.explosionRing!=null&&s.explosionRing.enabled;
            hwHelperEsp=!FeaturePolicy.isBlocked("hwhelper.zones")&&s!=null&&s.hwhelper!=null&&s.hwhelper.enabled&&s.hwhelper.esp!=null&&s.hwhelper.esp.enabled;
            enchantGlint=!FeaturePolicy.isBlocked("enchantGlint")&&s!=null&&s.enchantGlint!=null&&s.enchantGlint.enabled;
            itemPhysics=!FeaturePolicy.isBlocked("itemPhysics")&&s!=null&&s.itemPhysics.enabled;grounded=s!=null&&s.itemPhysics.grounded;rotationSpeed=s==null?1:s.itemPhysics.rotationSpeed;
            hitbox=!FeaturePolicy.isBlocked("hitbox")&&s!=null&&s.hitbox.enabled;players=s!=null&&s.hitbox.players;mobs=s!=null&&s.hitbox.mobs;items=s!=null&&s.hitbox.items;
            eyeLine=s!=null&&s.hitbox.eyeLine;boxColor=s==null?-1:s.hitbox.color;
            lineWidth=s==null?1:s.hitbox.lineWidth;range=s==null?64:s.hitbox.range;
        }
    }
    public static Policy policy(){return policy;}
    public static boolean beginArmorHitColor(boolean hurt){boolean prev=armorHitColorActive;armorHitColorActive=hurt&&policy.hitColorArmor;return prev;}
    public static void endArmorHitColor(boolean prev){armorHitColorActive=prev;}
    public static boolean isArmorHitColorActive(){return armorHitColorActive&&policy.hitColorArmor;}
    public static int reloadGeneration(){return reloadGeneration;}
    public static void resourceReload(){
        reloadGeneration++;
        glintBasePixels.clear();glintBaseDims.clear();glintTextures.clear();
        glintLastResourceManager=null;glintLastIntensity=-1f;
    }
    public static void close(){
        if(glintActiveOverride){
            Minecraft mc=Minecraft.getInstance();
            if(mc!=null&&mc.getTextureManager()!=null&&com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){
                String[] paths=new String[]{@@GLINT_TEXTURE_PATHS@@};
                for(String p:paths){try{mc.getTextureManager().release(parseWorldId(p));}catch(Throwable ignored){}}
            }
            glintActiveOverride=false;
        }
        glintBasePixels.clear();glintBaseDims.clear();glintTextures.clear();
        glintLastResourceManager=null;glintLastIntensity=-1f;
        clear();
    }
    public static void tick(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        long revision=(runtime==null?0:runtime.settingsRevision())^(FeaturePolicy.revision()*31L);int playerTick=mc.player==null?0:mc.player.tickCount;
        if(policyRevision!=revision){policy=new Policy(runtime==null?null:runtime.settings());policyRevision=revision;policyTick=playerTick;}
        if(world!=mc.level||player!=mc.player){clear();world=mc.level;player=mc.player;}
        if(mc.player==null||mc.level==null||runtime==null)return;
        Settings s=runtime.settings();
        if(FeaturePolicy.isBlocked("damageSplash")||!s.damageSplash.enabled){damageTargets.clear();runtime.damageIndicators().clear();}
        else if(tick!=playerTick){
            double damageTime=ClientRuntime.nowSeconds();
            for(java.util.Iterator<Map.Entry<Integer,LivingEntity>> it=damageTargets.entrySet().iterator();it.hasNext();){
                Map.Entry<Integer,LivingEntity> row=it.next();LivingEntity entity=row.getValue();
                if(!runtime.damageIndicators().sample(row.getKey(),entity.getHealth()+entity.getAbsorptionAmount(),entity.getX(),entity.getY()+entity.getBbHeight()*.8,entity.getZ(),damageTime))it.remove();
            }
        }
        boolean dead=mc.player.getHealth()<=0;
        if(dead&&!deathLatched){
            deathLatched=true;
            if(!FeaturePolicy.isBlocked("waypoints")&&s.waypoints!=null&&s.waypoints.deathPoints){
                if(s.waypoints.addDeath(mc.player.getX(),mc.player.getY(),mc.player.getZ(),@@DIMENSION@@,currentWorldId(),currentWorldLabel())!=null)runtime.changed();
            }
        }else if(!dead)deathLatched=false;
        if(tick!=playerTick){
            double dx=mc.player.getX()-prevPlayerX;
            double dz=mc.player.getZ()-prevPlayerZ;
            playerSpeedBps=Math.sqrt(dx*dx+dz*dz)*20.0;
            prevPlayerX=mc.player.getX();
            prevPlayerZ=mc.player.getZ();

            if(mc.options!=null){
                boolean attackDown=mc.options.keyAttack.isDown();
                if(attackDown&&!prevAttackDown)lmbClicks.add(System.currentTimeMillis());
                prevAttackDown=attackDown;

                boolean useDown=mc.options.keyUse.isDown();
                if(useDown&&!prevUseDown)rmbClicks.add(System.currentTimeMillis());
                prevUseDown=useDown;
            }

            long cutoff=System.currentTimeMillis()-1000L;
            lmbClicks.removeIf(t->t<cutoff);
            rmbClicks.removeIf(t->t<cutoff);

            if(!totemPops.isEmpty()&&(s.totemCounter==null||s.totemCounter.resetOnDeath)){
                for(Player other:mc.level.players()){
                    if(other!=null&&(!other.isAlive()||other.getHealth()<=0f)){
                        UUID id=other.getUUID();
                        totemPops.remove(id);
                        totemLastPopMs.remove(id);
                    }
                }
            }
            if(@@SCREEN@@ instanceof AbstractContainerScreen&&!FeaturePolicy.isBlocked("hwhelper.aucprice")&&runtime.hwhelper()!=null&&s.hwhelper!=null&&s.hwhelper.enabled&&s.hwhelper.aucprice.enabled){
                formatScreenTitle(@@SCREEN@@,runtime);
            }
            localPlayerEntityId=mc.player.getId();
            if(!mc.player.isAlive()||mc.player.getHealth()<=0f){
                packetCtSetNanos=0L;packetCtDurationSec=0;lastPvpHitNanos=0L;lastUnattributedHurtNanos=0L;bossBarHadCt=false;pendingLeaveGuard.clear();
            }else{
                pollPendingSafeLeave(mc,runtime);
                if(mc.player==null||mc.level==null){clear();world=mc.level;player=mc.player;return;}
            }
            syncPauseScreenButton(mc,runtime);
            syncEnchantGlint(mc,runtime);
            trackHeldTntItem(mc,runtime);
            tickTntCountdownSounds(mc,runtime);
            pollPendingHwUse(mc,runtime);
            pollExplosiveThingCooldown(mc,runtime);
            pollHwTrapActionTimer(mc,runtime);
            syncHwHolograms(mc,runtime);
            tickTapeMouse(mc,runtime);
            WorldMapBridge.tick();
            if(mc.player==null||mc.level==null){clear();world=mc.level;player=mc.player;return;}
        }
        if(mc.player==null||mc.level==null)return;
        boolean needPotions=(!FeaturePolicy.isBlocked("potions")&&s.potions.enabled)
            ||(!FeaturePolicy.isBlocked("effectAlert")&&s.effectAlert!=null&&s.effectAlert.enabled)
            ||(!FeaturePolicy.isBlocked("hwhelper.timers")&&s.hwhelper!=null&&s.hwhelper.enabled&&s.hwhelper.timers!=null&&s.hwhelper.timers.enabled&&s.hwhelper.timers.snowball);
        boolean needArmor=!FeaturePolicy.isBlocked("armor")&&s.armor.enabled;
        int flags=(needArmor?1:0)|(needPotions?2:0)|(!FeaturePolicy.isBlocked("food")&&s.food.enabled?4:0);
        if(tick==mc.player.tickCount&&flags==snapshotFlags)return;tick=mc.player.tickCount;snapshotFlags=flags;
        if(needArmor){
            List<InventorySlot> next=new ArrayList<InventorySlot>(4);
            for(int i=0;i<4;i++){
                ItemStack stack=mc.player.getItemBySlot(ARMOR[i]);armorStacks[i]=stack.isEmpty()?ItemStack.EMPTY:stack.copy();
                next.add(new InventorySlot(100+i,itemId(stack),stack.isEmpty()?"":stack.getHoverName().getString(),stack.getCount(),stack.getDamageValue(),stack.getMaxDamage(),stack.isEnchanted()));
            }
            armor=Collections.unmodifiableList(next);
        }else{armor=Collections.emptyList();Arrays.fill(armorStacks,null);}
        if(needPotions){
            List<PotionView> next=new ArrayList<PotionView>();
            for(MobEffectInstance instance:mc.player.getActiveEffects()){
                MobEffect effect=@@EFFECT_VALUE@@;
                next.add(new PotionView(@@EFFECT_ID@@,effect.getDisplayName().getString(),instance.getAmplifier(),effect.getColor(),@@EFFECT_DURATION@@,effect.isBeneficial()));
            }
            Collections.sort(next,(a,b)->a.id.compareTo(b.id));potions=Collections.unmodifiableList(next);
        }else potions=Collections.emptyList();
        vitals=new VitalsView(mc.player.getFoodData().getFoodLevel(),mc.player.getFoodData().getSaturationLevel(),true,hungerVisible(mc));
        if(!( @@SCREEN@@ instanceof AbstractContainerScreen)){bulkDrag=false;bulkDropHeld=false;gestureScreen=null;gestureItem=ItemStack.EMPTY;dragged.clear();lastDragNanos=0L;hoveredTooltipStack=ItemStack.EMPTY;hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;lockedPeekStack=ItemStack.EMPTY;lastLockedPeekBoundsValid=false;lastCenteredContainerScreen=null;altFlippedPeekMode=false;altTogglePrevDown=false;}
    }
    private static void clear(){armorHitColorActive=false;tabAnimator.reset();lastPvpCmdWarnNanos=0L;lastTabHeader="";armor=Collections.emptyList();potions=Collections.emptyList();vitals=VitalsView.EMPTY;Arrays.fill(armorStacks,null);Arrays.fill(tooltipItemCache,null);tick=Integer.MIN_VALUE;attacked=null;attackNanos=0;lastPvpHitNanos=0L;packetCtSetNanos=0L;packetCtDurationSec=0;lastOutgoingPlayerAttackNanos=0L;lastOutgoingAttackedPlayerId=Integer.MIN_VALUE;localPlayerEntityId=Integer.MIN_VALUE;lastUnattributedHurtNanos=0L;lastNettyDamageNanos=0L;lastConfirmedNonPvpDamageNanos=0L;bossBarHadCt=false;suppressOwnChatCt=false;bypassNextLeave=false;pendingLeaveGuard.clear();lastPauseDisconnectBtn=null;lastPauseDisconnectOrig=null;bulkDrag=false;bulkDropHeld=false;gestureScreen=null;gestureItem=ItemStack.EMPTY;dragged.clear();lastDragNanos=0L;deathLatched=false;spawnLockedLevel=null;spawnLockedKey="";enchantedAppleUntil.clear();totemPops.clear();totemLastPopMs.clear();damageTargets.clear();losCache.clear();losCacheTick=Integer.MIN_VALUE;blockLerpValid=false;tntMaxFuse.clear();tntLastCountdownTick.clear();recentBrokenBlocks.clear();customTntSoundTags.clear();cachedAnchors.clear();cachedAnchorTick=Integer.MIN_VALUE;latestExplosionBadges=Collections.emptyList();lastHwHoloScanTick=Integer.MIN_VALUE;hoveredTooltipStack=ItemStack.EMPTY;hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;lockedPeekStack=ItemStack.EMPTY;lastLockedPeekBoundsValid=false;lastCenteredContainerScreen=null;releaseTapeMouseKeys(Minecraft.getInstance());WavyCapeBridge.clear();WorldMapBridge.clear();ClientRuntime runtime=ClientRuntime.get();if(runtime!=null){runtime.damageIndicators().clear();runtime.explosionLearner().clear();if(runtime.hwhelper()!=null)runtime.hwhelper().trapActionTimer().reset();}}
    public static List<InventorySlot> armor(float delta){tick();return armor;}
    public static List<PotionView> potions(float delta){tick();return potions;}
    private static boolean hungerVisible(Minecraft mc){return mc.player!=null&&mc.getCameraEntity()==mc.player&&!@@FOOD_HUD_HIDDEN@@&&mc.gameMode!=null&&mc.gameMode.canHurtPlayer()&&!(mc.player.getVehicle() instanceof LivingEntity&&((LivingEntity)mc.player.getVehicle()).showVehicleHealth());}
    public static VitalsView vitals(float delta){
        tick();boolean visible=hungerVisible(Minecraft.getInstance());

        if(vitals.available&&vitals.hungerVisible!=visible)vitals=new VitalsView(vitals.hunger,vitals.saturation,true,visible);
        return vitals;
    }
    public static ItemStack cachedArmorStack(int slot){return slot>=100&&slot<=103&&armorStacks[slot-100]!=null?armorStacks[slot-100]:ItemStack.EMPTY;}
    public static ItemStack cachedTooltipStack(int slot){
        int idx=slot-300;
        return idx>=0&&idx<tooltipItemCache.length&&tooltipItemCache[idx]!=null?tooltipItemCache[idx]:ItemStack.EMPTY;
    }
    public static ItemStack cachedTargetStack(int slot){
        if(attacked==null||slot<200||slot>205)return ItemStack.EMPTY;
        EquipmentSlot[] slots={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};return attacked.getItemBySlot(slots[slot-200]);
    }
    private static String itemId(ItemStack stack){return stack.isEmpty()?"minecraft:air":@@ITEM_REGISTRY@@.getKey(stack.getItem()).toString();}
    public static void attack(Player source,Entity entity){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(source!=mc.player||runtime==null)return;
        if(!FeaturePolicy.isBlocked("damageSplash")&&runtime.settings().damageSplash.enabled&&entity instanceof LivingEntity&&entity!=source){
            LivingEntity living=(LivingEntity)entity;
            boolean critical=source.fallDistance>0&&!source.@@GROUND_METHOD@@()&&!source.isInWater()&&!source.onClimbable()&&!source.isPassenger()&&!source.isSprinting()&&source.getAttackStrengthScale(.5f)>.9f&&!source.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
            double damageTime=ClientRuntime.nowSeconds();float health=living.getHealth()+living.getAbsorptionAmount();
            runtime.damageIndicators().sample(entity.getId(),health,entity.getX(),entity.getY()+entity.getBbHeight()*.8,entity.getZ(),damageTime);
            runtime.damageIndicators().attack(entity.getId(),health,critical,damageTime);
            damageTargets.remove(entity.getId());
            if(damageTargets.size()>=64)damageTargets.remove(damageTargets.keySet().iterator().next());
            damageTargets.put(entity.getId(),living);
        }
        if(entity instanceof LivingEntity&&entity!=source){
            if(entity instanceof Player&&isRealPlayer(mc,(Player)entity)){
                lastOutgoingPlayerAttackNanos=System.nanoTime();
                lastOutgoingAttackedPlayerId=entity.getId();
            }
            if(!FeaturePolicy.isBlocked("target")&&runtime.settings().target.enabled){attacked=(LivingEntity)entity;attackNanos=System.nanoTime();world=mc.level;player=mc.player;}
        }else if(!FeaturePolicy.isBlocked("target")&&runtime.settings().target.enabled){attacked=null;attackNanos=0;}
    }
    public static boolean hasFullArmor(Player playerEntity){
        if(playerEntity==null)return false;
        for(int i=0;i<ARMOR.length;i++)if(playerEntity.getItemBySlot(ARMOR[i]).isEmpty())return false;
        return true;
    }
    public static boolean isInvisibleArmorPlayer(Entity entity){
        return entity instanceof Player&&entity.isAlive()&&!entity.isSpectator()&&entity.isInvisible()&&hasFullArmor((Player)entity);
    }
    public static boolean shouldForceShowNametag(LivingEntity entity){
        Minecraft mc=Minecraft.getInstance();Policy p=policy;
        if(mc.player==null||entity==null||!entity.isAlive()||entity.isSpectator())return false;
        if(entity==mc.player)return p.ownNametag&&!@@FOOD_HUD_HIDDEN@@&&!mc.options.getCameraType().isFirstPerson()&&!(@@SCREEN@@ instanceof AbstractContainerScreen);
        if(entity==mc.getCameraEntity())return false;
        return p.invisibleArmorNametag&&isInvisibleArmorPlayer(entity);
    }
    private static boolean hasPlayerLineOfSight(Minecraft mc,Vec3 from,Player p,Vec3 pos){
        if(mc.level==null||mc.player==null||from==null)return true;
        int curTick=mc.player==null?0:mc.player.tickCount;
        if(curTick!=losCacheTick||from.x!=losCamX||from.y!=losCamY||from.z!=losCamZ){
            losCache.clear();losCacheTick=curTick;losCamX=from.x;losCamY=from.y;losCamZ=from.z;
        }
        int id=p.getId();
        Boolean cached=losCache.get(id);
        if(cached!=null)return cached.booleanValue();
        double h=Math.max(0.4,p.getBbHeight());
        Vec3 head=new Vec3(pos.x,pos.y+h*0.85,pos.z);
        boolean visible=isClipClear(mc,from,head)||isClipClear(mc,from,new Vec3(pos.x,pos.y+h*0.52,pos.z));
        if(losCache.size()<128)losCache.put(id,visible);
        return visible;
    }
    private static boolean isClipClear(Minecraft mc,Vec3 from,Vec3 to){
        if(from.distanceToSqr(to)<0.04)return true;
        net.minecraft.world.phys.HitResult hit=mc.level.clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        return hit==null||hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    public static List<PlayerHealthView> playerHealths(float delta){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||mc.level==null)return Collections.emptyList();
        Settings.PlayerHealth cfg=runtime.settings().playerHealth;
        if(FeaturePolicy.isBlocked("playerHealth")||cfg==null||!cfg.enabled)return Collections.emptyList();
        @@CAMERA_POS_SETUP@@
        double maxDistSq=(double)cfg.maxDistance*(double)cfg.maxDistance;
        long nowMs=System.nanoTime()/1000000L;
        List<PlayerHealthView> list=new ArrayList<PlayerHealthView>();
        boolean allowInvis=!FeaturePolicy.isBlocked("playerHealth.invisibles")&&cfg.showInvisible;
        for(Player p:mc.level.players()){
            if(p==null||p==mc.player||!p.isAlive()||p.isSpectator())continue;
            boolean invis=p.isInvisible();
            if(invis&&!allowInvis)continue;
            if(distanceSquared(p,mc.player)>maxDistSq)continue;
            Vec3 pos=p.getPosition(delta);
            if(!hasPlayerLineOfSight(mc,camPos,p,pos))continue;
            float hp=Math.max(0f,p.getHealth()),maxHp=Math.max(1f,p.getMaxHealth()),abs=Math.max(0f,p.getAbsorptionAmount());
            Long until=enchantedAppleUntil.get(p.getUUID());
            boolean recentEnchanted=until!=null&&until>nowMs;
            boolean enchanted=abs>4.0f||recentEnchanted||(p.hasEffect(@@RESISTANCE_EFFECT@@)&&p.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE));
            list.add(new PlayerHealthView(p.getId(),pos.x,pos.y,pos.z,p.getBbHeight(),hp,maxHp,abs,invis,enchanted));
        }
        return list;
    }
    public static void onEntityEvent(Entity entity,byte eventId){
        if(!(entity instanceof Player))return;
        UUID uuid=entity.getUUID();
        if(eventId==35){
            Integer prev=totemPops.get(uuid);
            totemPops.put(uuid,prev==null?1:prev.intValue()+1);
            totemLastPopMs.put(uuid,System.nanoTime()/1000000L);
        }else if(eventId==3){
            ClientRuntime runtime=ClientRuntime.get();
            if(runtime==null||runtime.settings().totemCounter==null||runtime.settings().totemCounter.resetOnDeath){
                totemPops.remove(uuid);
                totemLastPopMs.remove(uuid);
            }
        }
    }
    public static List<TotemCounterView> totemCounters(float delta){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||mc.level==null)return Collections.emptyList();
        Settings.TotemCounter cfg=runtime.settings().totemCounter;
        if(FeaturePolicy.isBlocked("totemCounter")||cfg==null||!cfg.enabled)return Collections.emptyList();
        if(!cfg.showZero&&totemPops.isEmpty())return Collections.emptyList();
        @@CAMERA_POS_SETUP@@
        double maxDistSq=(double)cfg.maxDistance*(double)cfg.maxDistance;
        long nowMs=System.nanoTime()/1000000L;
        List<TotemCounterView> list=new ArrayList<TotemCounterView>();
        boolean allowInvis=!FeaturePolicy.isBlocked("totemCounter.invisibles")&&cfg.showInvisible;
        for(Player p:mc.level.players()){
            if(p==null||p==mc.player||!p.isAlive()||p.isSpectator())continue;
            boolean invis=p.isInvisible();
            if(invis&&!allowInvis)continue;
            Integer count=totemPops.get(p.getUUID());
            int pops=count==null?0:count.intValue();
            if(pops<=0&&!cfg.showZero)continue;
            if(distanceSquared(p,mc.player)>maxDistSq)continue;
            Vec3 pos=p.getPosition(delta);
            if(!hasPlayerLineOfSight(mc,camPos,p,pos))continue;
            Long lastMs=totemLastPopMs.get(p.getUUID());
            float sinceSec=lastMs==null?999f:Math.max(0f,(nowMs-lastMs.longValue())/1000f);
            list.add(new TotemCounterView(p.getId(),pos.x,pos.y,pos.z,p.getBbHeight(),pops,sinceSec,invis));
        }
        return list;
    }
    public static List<PlayerPingView> playerPings(float delta){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||mc.level==null||mc.getConnection()==null)return Collections.emptyList();
        Settings.PlayerPing cfg=runtime.settings().playerPing;
        if(!dev.nightclient.runtime.TabPingLogic.isWorldActive(cfg))return Collections.emptyList();
        @@CAMERA_POS_SETUP@@
        double maxDistSq=(double)cfg.maxDistance*(double)cfg.maxDistance;
        List<PlayerPingView> list=new ArrayList<PlayerPingView>();
        boolean allowInvis=!FeaturePolicy.isBlocked("playerPing.invisibles")&&cfg.showInvisible;
        for(Player p:mc.level.players()){
            if(p==null||!p.isAlive()||p.isSpectator())continue;
            boolean self=p==mc.player;
            if(self&&(!cfg.showSelf||mc.options.getCameraType().isFirstPerson()))continue;
            boolean invis=p.isInvisible();
            if(invis&&!self&&!allowInvis)continue;
            if(!self&&distanceSquared(p,mc.player)>maxDistSq)continue;
            net.minecraft.client.multiplayer.PlayerInfo info=mc.getConnection().getPlayerInfo(p.getUUID());
            if(info==null)continue;
            Vec3 pos=p.getPosition(delta);
            if(!self&&!hasPlayerLineOfSight(mc,camPos,p,pos))continue;
            list.add(new PlayerPingView(p.getId(),pos.x,pos.y,pos.z,p.getBbHeight(),Math.max(0,info.getLatency()),invis,self));
        }
        return list;
    }
    public static boolean isTabPingActive(){
        ClientRuntime runtime=ClientRuntime.get();
        return runtime!=null&&dev.nightclient.runtime.TabPingLogic.isTabActive(runtime.settings().playerPing);
    }
    public static int tabPingReserveWidth(int original){
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null)return original;
        Settings.PlayerPing cfg=runtime.settings().playerPing;
        if(!dev.nightclient.runtime.TabPingLogic.isTabActive(cfg))return original;
        Minecraft mc=Minecraft.getInstance();
        if(mc.font==null||mc.getConnection()==null)return original;
        int maxW=0;
        int seen=0;
        for(net.minecraft.client.multiplayer.PlayerInfo info:@@TAB_ONLINE_PLAYERS@@){
            if(info==null)continue;
            int lat=info.getLatency();
            if(lat<0)continue;
            int w=mc.font.width(dev.nightclient.runtime.TabPingLogic.formatLabel(lat));
            if(w>maxW)maxW=w;
            if(++seen>=120)break;
        }
        if(maxW==0)maxW=mc.font.width("99ms");
        return dev.nightclient.runtime.TabPingLogic.reserveColumnWidth(cfg,original,maxW);
    }
    public static boolean renderTabPing(@@CONTEXT@@ graphics,int slotWidth,int x,int y,net.minecraft.client.multiplayer.PlayerInfo info){
        if(graphics==null||info==null)return false;
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null)return false;
        Settings.PlayerPing cfg=runtime.settings().playerPing;
        if(!dev.nightclient.runtime.TabPingLogic.isTabActive(cfg))return false;
        int ping=info.getLatency();
        if(ping<0)return false;
        Minecraft mc=Minecraft.getInstance();
        if(mc.font==null)return false;
        String label=dev.nightclient.runtime.TabPingLogic.formatLabel(ping);
        int color=dev.nightclient.runtime.TabPingLogic.colorFor(ping,cfg.colored);
        int textW=mc.font.width(label);
        int drawX=dev.nightclient.runtime.TabPingLogic.computeTextX(x,slotWidth,textW,cfg.tabKeepBars);
        @@TAB_PING_DRAW_TEXT@@
        return !cfg.tabKeepBars;
    }
    public static TargetView localPlayer(float delta){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return null;
        String skin=@@LOCAL_PLAYER_SKIN@@;
        return new TargetView(mc.player.getId(),mc.player.getUUID().toString(),FeatureBridge.ownNickname(),mc.player.getHealth(),mc.player.getMaxHealth(),mc.player.getAbsorptionAmount(),0,true,skin,Collections.<InventorySlot>emptyList());
    }
    public static TargetView target(float delta){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        boolean zako=runtime!=null&&"zakohp".equals(runtime.settings().target.style);
        if(FeaturePolicy.isBlocked("target")||runtime==null||!runtime.settings().target.enabled||mc.player==null||world!=mc.level||attacked==null||(!(zako&&attacked instanceof Player)&&(@@ENTITY_REMOVED@@||!attacked.isAlive()))||(System.nanoTime()-attackNanos)*1e-9>(zako?10:runtime.settings().target.displaySeconds)){attacked=null;return null;}
        List<InventorySlot> gear=new ArrayList<InventorySlot>();
        EquipmentSlot[] slots={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<slots.length;i++){ItemStack stack=attacked.getItemBySlot(slots[i]);gear.add(new InventorySlot(200+i,itemId(stack),stack.getHoverName().getString(),stack.getCount(),stack.getDamageValue(),stack.getMaxDamage(),stack.isEnchanted()));}
        String skin=attacked instanceof net.minecraft.client.player.AbstractClientPlayer?@@TARGET_SKIN@@:"";
        return new TargetView(attacked.getId(),attacked.getUUID().toString(),attacked.getDisplayName().getString(),attacked.getHealth(),attacked.getMaxHealth(),attacked.getAbsorptionAmount(),(float)Math.sqrt(distanceSquared(attacked,mc.player)),attacked instanceof Player,skin,gear);
    }
    public static String fixCommand(String command){
        clearChatDraft();
        if(BenchmarkDirector.handleCommand(command))return "";
        ClientRuntime runtime=ClientRuntime.get();
        if(shouldBlockCommand(command))return "";
        if(runtime==null||command==null||!(command.startsWith("/")||command.startsWith(".")))return command;
        boolean wasTape=runtime.settings().tapeMouse!=null&&runtime.settings().tapeMouse.enabled;
        String result=runtime.fixCommand(command);
        if(wasTape&&(runtime.settings().tapeMouse==null||!runtime.settings().tapeMouse.enabled)){
            releaseTapeMouseKeys(Minecraft.getInstance());
        }
        return result;
    }
    public static void addRecentChat(String text){
        if(text==null)return;
        String trimmed=text.trim();
        if(trimmed.isEmpty())return;
        if(SENSITIVE_HISTORY_CMD.matcher(trimmed).matches()){
            ClientRuntime runtime=ClientRuntime.get();
            if(runtime!=null&&!FeaturePolicy.isBlocked("protect")&&runtime.settings().protect!=null&&runtime.settings().protect.enabled&&runtime.settings().protect.maskPasswords)return;
        }
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.gui==null)return;
        try{@@CHAT_COMPONENT@@.addRecentChat(trimmed);}catch(Throwable ignored){}
    }
    public static void onTabHeader(net.minecraft.network.chat.Component header){
        if(header==null){lastTabHeader="";return;}
        String raw=header.getString();
        lastTabHeader=raw==null?"":MC_COLOR_PATTERN.matcher(raw).replaceAll("").trim();
    }
    public static boolean isInLobby(){
        String header=lastTabHeader;
        if(header!=null&&!header.isEmpty()){
            String lower=header.toLowerCase(Locale.ROOT);
            if(lower.contains("лобби")||lower.contains("lobby"))return true;
        }
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.player==null||mc.level==null)return false;
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        for(int i=0;i<9;i++){
            ItemStack s=inv.getItem(i);
            if(s.isEmpty())continue;
            String joined=(s.getHoverName().getString()+" "+readLore(s)).toLowerCase(Locale.ROOT);
            if(joined.contains("выбор режима")||joined.contains("выбрать режим")||joined.contains("выбор сервера"))return true;
        }
        return false;
    }
    public static boolean noHurtCam(){return policy.noHurtCam;}
    public static HumanoidArm mainLeftArm(Object entity){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||entity!=mc.player)return null;
        Settings s=runtime.settings();
        if(FeaturePolicy.isBlocked("tweaksy.mainLeftFix")||s==null||s.tweaksy==null||!s.tweaksy.enabled||s.tweaksy.mainLeftFix==null||!s.tweaksy.mainLeftFix.enabled)return null;
        return @@MAIN_HAND_GET@@;
    }
    public static boolean scroll(double amount){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||!(@@SCREEN@@ instanceof AbstractContainerScreen))return false;
        Settings.ItemScroller cfg=runtime.settings().itemScroller;
        if(!ItemScrollerLogic.canScroll(cfg,amount))return false;
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)(Object)@@SCREEN@@;Slot slot=screen.nightclient$hoveredSlot();
        if(slot==null||!slot.isActive()||!slot.hasItem()||!slot.mayPickup(mc.player)||!@@CARRIED@@.isEmpty())return false;
        long now=System.nanoTime();if(!ItemScrollerLogic.cooldownElapsed(lastScrollNanos,now,cfg.cooldownMs))return true;
        lastScrollNanos=now;
        if(ItemScrollerLogic.wheelMovesMatching(cfg))return bulk(screen,slot,false,cfg.matchNbt);
        screen.nightclient$quickMove(slot,slot.index,0,@@CLICK_TYPE@@.QUICK_MOVE);return true;
    }
    private static boolean bulkDrag,bulkDropHeld;
    private static boolean sameStack(ItemStack a,ItemStack b){return @@STACK_MATCH@@;}
    private static boolean matchesStack(ItemStack a,ItemStack b,boolean matchNbt){
        if(a.isEmpty()||b.isEmpty())return false;
        return matchNbt?sameStack(a,b):a.getItem()==b.getItem();
    }
    private static boolean textFocused(net.minecraft.client.gui.components.events.GuiEventListener node,int depth){
        if(node==null||depth>8)return false;
        if(node instanceof net.minecraft.client.gui.components.EditBox)return ((net.minecraft.client.gui.components.EditBox)node).isFocused();
        if(node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler){int count=0;for(net.minecraft.client.gui.components.events.GuiEventListener child:((net.minecraft.client.gui.components.events.ContainerEventHandler)node).children()){if(++count>128)break;if(textFocused(child,depth+1))return true;}}
        return false;
    }

    private static boolean bulk(ContainerFeatureAccessor screen,Slot hovered,boolean drop,boolean matchNbt){
        Minecraft mc=Minecraft.getInstance();
        if(hovered==null||!hovered.isActive()||!hovered.hasItem()||!hovered.mayPickup(mc.player)||!@@CARRIED@@.isEmpty())return false;
        return bulkMatching(screen,hovered,hovered.getItem().copy(),drop,matchNbt);
    }
    private static boolean bulkMatching(ContainerFeatureAccessor screen,Slot hovered,ItemStack selected,boolean drop,boolean matchNbt){
        Minecraft mc=Minecraft.getInstance();
        if(hovered==null||!hovered.isActive()||selected==null||selected.isEmpty())return false;
        List<Slot> slots=new ArrayList<Slot>();
        for(Slot slot:screen.nightclient$menu().slots)if(slot.container==hovered.container&&slot.isActive()&&slot.hasItem()&&slot.mayPickup(mc.player)&&matchesStack(selected,slot.getItem(),matchNbt))slots.add(slot);
        for(Slot slot:slots)if(slot.hasItem()&&matchesStack(selected,slot.getItem(),matchNbt))screen.nightclient$quickMove(slot,slot.index,drop?1:0,drop?@@CLICK_TYPE@@.THROW:@@CLICK_TYPE@@.QUICK_MOVE);
        return !slots.isEmpty();
    }
    private static boolean shiftPlace(ContainerFeatureAccessor screen,Slot hovered,ItemStack selected,boolean matchNbt){
        Minecraft mc=Minecraft.getInstance();
        if(hovered==null||!hovered.isActive()||hovered.hasItem()||selected==null||selected.isEmpty()||!hovered.mayPlace(selected))return false;
        screen.nightclient$quickMove(hovered,hovered.index,0,@@CLICK_TYPE@@.PICKUP);
        List<Slot> slots=new ArrayList<Slot>();
        for(Slot slot:screen.nightclient$menu().slots)if(slot.container!=hovered.container&&slot.isActive()&&slot.hasItem()&&slot.mayPickup(mc.player)&&slot.mayPlace(selected)&&matchesStack(selected,slot.getItem(),matchNbt))slots.add(slot);
        for(Slot slot:slots)if(slot.hasItem()&&matchesStack(selected,slot.getItem(),matchNbt))screen.nightclient$quickMove(slot,slot.index,0,@@CLICK_TYPE@@.QUICK_MOVE);
        return true;
    }
    public static boolean click(Object current,int button,double x,double y,boolean shift,boolean control){
        releaseDrag();Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("itemScroller")||runtime==null||!runtime.settings().itemScroller.enabled||mc.player==null||button!=0||!shift)return false;
        Settings.ItemScroller cfg=runtime.settings().itemScroller;
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)current;
        if(control){
            if(!ItemScrollerLogic.canMoveMatching(cfg))return false;
            boolean used=bulk(screen,slotAt(screen,x,y),false,cfg.matchNbt);
            if(used){gestureScreen=current;bulkDrag=true;}
            return used;
        }
        if(!@@CARRIED@@.isEmpty()){
            if(!ItemScrollerLogic.canShiftPlace(cfg))return false;
            boolean used=shiftPlace(screen,slotAt(screen,x,y),@@CARRIED@@.copy(),cfg.matchNbt);
            if(used){gestureScreen=current;bulkDrag=true;}
            return used;
        }
        beginDrag(current,button,x,y,shift);return false;
    }
    public static boolean dropMatching(int action,boolean shift,boolean control){
        if(action==0){boolean used=bulkDropHeld;bulkDropHeld=false;return used;}
        if(bulkDropHeld)return true;
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(action!=1||!shift||!control||runtime==null||mc.player==null||!(@@SCREEN@@ instanceof AbstractContainerScreen)||textFocused((net.minecraft.client.gui.components.events.GuiEventListener)@@SCREEN@@,0))return false;
        Settings.ItemScroller cfg=runtime.settings().itemScroller;
        if(!ItemScrollerLogic.canDropMatching(cfg))return false;
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)(Object)@@SCREEN@@;
        bulkDropHeld=bulk(screen,slotAt(screen,mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/Math.max(1,mc.getWindow().getScreenWidth()),mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/Math.max(1,mc.getWindow().getScreenHeight())),true,cfg.matchNbt);return bulkDropHeld;
    }
    private static Slot slotAt(ContainerFeatureAccessor screen,double x,double y){
        for(Slot slot:screen.nightclient$menu().slots)if(slot.isActive()&&screen.nightclient$isHovering(slot,x,y))return slot;
        return null;
    }

    public static boolean drag(Object current,int button,double x,double y,boolean shift){
        if(bulkDrag&&gestureScreen==current&&button==0)return true;
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||button!=0||!shift||!ItemScrollerLogic.canDragMove(runtime.settings().itemScroller)){releaseDrag();return false;}
        Settings.ItemScroller cfg=runtime.settings().itemScroller;
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)current;Slot slot=slotAt(screen,x,y);
        if(!@@CARRIED@@.isEmpty()||slot==null||!slot.hasItem()||!slot.mayPickup(mc.player))return false;
        if(gestureScreen!=current){gestureScreen=current;gestureItem=ItemStack.EMPTY;dragged.clear();lastDragNanos=0L;}
        if(cfg.dragOnlyMatching){
            if(gestureItem.isEmpty())gestureItem=slot.getItem().copy();
            else if(!matchesStack(gestureItem,slot.getItem(),cfg.matchNbt))return true;
        }
        if(dragged.contains(slot.index))return true;
        if(cfg.dragCooldownMs>0){
            long now=System.nanoTime();
            if(!ItemScrollerLogic.cooldownElapsed(lastDragNanos,now,cfg.dragCooldownMs))return true;
            lastDragNanos=now;
        }
        dragged.add(slot.index);
        screen.nightclient$quickMove(slot,slot.index,0,@@CLICK_TYPE@@.QUICK_MOVE);return true;
    }
    public static void beginDrag(Object current,int button,double x,double y,boolean shift){
        releaseDrag();Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||button!=0||!shift||!ItemScrollerLogic.canDragMove(runtime.settings().itemScroller))return;
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)current;Slot slot=slotAt(screen,x,y);
        if(slot==null||!slot.hasItem()||!slot.mayPickup(mc.player)||!@@CARRIED@@.isEmpty())return;
        gestureScreen=current;gestureItem=slot.getItem().copy();dragged.add(slot.index);lastDragNanos=System.nanoTime();
    }

    public static boolean drag(Object current,int button){Minecraft mc=Minecraft.getInstance();ContainerFeatureAccessor screen=(ContainerFeatureAccessor)current;Slot slot=screen.nightclient$hoveredSlot();if(slot==null)return false;return drag(current,button,@@SLOT_SCREEN_X@@,@@SLOT_SCREEN_Y@@,@@SHIFT_DOWN@@);}
    public static void beginDrag(Object current,int button){Minecraft mc=Minecraft.getInstance();ContainerFeatureAccessor screen=(ContainerFeatureAccessor)current;Slot slot=screen.nightclient$hoveredSlot();if(slot!=null)beginDrag(current,button,@@SLOT_SCREEN_X@@,@@SLOT_SCREEN_Y@@,@@SHIFT_DOWN@@);}
    public static boolean fireVisible(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&mc.player.isOnFire()&&!mc.player.isSpectator()&&mc.options.getCameraType().isFirstPerson();}
    public static boolean releaseDrag(){boolean consumed=bulkDrag;bulkDrag=false;gestureScreen=null;gestureItem=ItemStack.EMPTY;dragged.clear();lastDragNanos=0L;return consumed;}
    public static boolean boxEligible(Entity entity,Policy p){
        if(entity==null||!entity.isAlive())return false;
        if(entity instanceof Player){
            if(entity.isInvisible()){if(!(p.invisibleArmorHitbox&&isInvisibleArmorPlayer(entity)))return false;}
            else if(!((p.hitbox||p.hitboxFill)&&p.players))return false;
        }else{
            if(!(p.hitbox||p.hitboxFill))return false;
            if(entity instanceof ItemEntity){if(!p.items)return false;}
            else if(!(entity instanceof LivingEntity&&p.mobs))return false;
            if(entity.isInvisible())return false;
        }
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||(entity==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson()))return false;
        double range=p.range;
        return distanceSquared(entity,mc.player)<=range*range;
    }
    public static boolean boxOutline(Entity entity,Policy p){
        return p.hitbox||(p.invisibleArmorHitbox&&isInvisibleArmorPlayer(entity));
    }
    private static double distanceSquared(Entity a,Entity b){double dx=a.getX()-b.getX(),dy=a.getY()-b.getY(),dz=a.getZ()-b.getZ();return dx*dx+dy*dy+dz*dz;}
    private static int boxFill(Entity entity,Policy p){return p.hitboxFill&&entity instanceof LivingEntity&&((LivingEntity)entity).hurtTime>0?p.hurtColor:0;}
    static void drawBox(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,AABB b,boolean stroke,int color,int fill,float width){
        double minX=b.minX,minY=b.minY,minZ=b.minZ,maxX=b.maxX,maxY=b.maxY,maxZ=b.maxZ;
        if(view!=null){
            double cx=(minX+maxX)*0.5,cy=(minY+maxY)*0.5,cz=(minZ+maxZ)*0.5;
            double bound=0.5*((maxX-minX)+(maxY-minY)+(maxZ-minZ));
            if(view.forward(cx,cy,cz)+bound<=0.06)return;
        }
        if((fill>>>24)!=0){
            out.quad(minX,minY,minZ,minX,maxY,minZ,maxX,maxY,minZ,maxX,minY,minZ,fill);
            out.quad(minX,minY,maxZ,maxX,minY,maxZ,maxX,maxY,maxZ,minX,maxY,maxZ,fill);
            out.quad(minX,minY,minZ,minX,minY,maxZ,minX,maxY,maxZ,minX,maxY,minZ,fill);
            out.quad(maxX,minY,minZ,maxX,maxY,minZ,maxX,maxY,maxZ,maxX,minY,maxZ,fill);
            out.quad(minX,maxY,minZ,minX,maxY,maxZ,maxX,maxY,maxZ,maxX,maxY,minZ,fill);
            out.quad(minX,minY,minZ,maxX,minY,minZ,maxX,minY,maxZ,minX,minY,maxZ,fill);
        }
        if(!stroke||(color>>>24)==0)return;
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,minY,minZ,maxX,minY,minZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,minY,maxZ,maxX,minY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,maxY,minZ,maxX,maxY,minZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,maxY,maxZ,maxX,maxY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,minY,minZ,minX,maxY,minZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,minY,maxZ,minX,maxY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,maxX,minY,minZ,maxX,maxY,minZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,maxX,minY,maxZ,maxX,maxY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,minY,minZ,minX,minY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,minX,maxY,minZ,minX,maxY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,maxX,minY,minZ,maxX,minY,maxZ,width,color);
        dev.nightclient.renderer.WorldRibbon.line(out,view,maxX,maxY,minZ,maxX,maxY,maxZ,width,color);
    }
    @@HITBOX_BUFFER_ACQUIRE@@
    public static dev.nightclient.renderer.WorldRibbon.View cameraView(int width,int height){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return null;
        @@CAMERA_VIEW_SETUP@@
        return view;
    }
    public static double[] playerPosition(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return null;
        return new double[]{mc.player.getX(),mc.player.getY(),mc.player.getZ()};
    }
    public static String currentDimension(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return "";
        return @@DIMENSION@@;
    }
    private static String rawSpawnKey(Minecraft mc){
        if(mc==null||mc.level==null||mc.level.getLevelData()==null)return "";
        try{@@WORLD_SPAWN_KEY@@}catch(Throwable ignored){return "";}
    }
    private static String lockedSpawnKey(Minecraft mc){
        if(mc==null||mc.level==null)return "";
        if(spawnLockedLevel!=mc.level){
            spawnLockedLevel=mc.level;
            spawnLockedKey="";
        }
        if(spawnLockedKey.isEmpty()||"0_0_0".equals(spawnLockedKey)){
            String current=rawSpawnKey(mc);
            if(current!=null&&!current.isEmpty())spawnLockedKey=current;
        }
        return spawnLockedKey;
    }
    private static long currentBiomeZoomSeed(Minecraft mc){
        if(mc==null||mc.level==null)return 0L;
        try{
            net.minecraft.world.level.biome.BiomeManager bm=mc.level.getBiomeManager();
            if(bm!=null)return ((dev.nightclient.platform.mixin.BiomeManagerAccessor)(Object)bm).nightclient$biomeZoomSeed();
        }catch(Throwable ignored){}
        return 0L;
    }
    private static String sanitizeWorldSlug(String raw){
        if(raw==null)return "";
        String trimmed=raw.trim();
        if(trimmed.isEmpty())return "";
        StringBuilder slug=new StringBuilder();
        boolean lossless=true;
        for(int i=0;i<trimmed.length();i++){
            char c=trimmed.charAt(i);
            if((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'||c=='.'||c=='-'){
                slug.append(c);
            }else if(c>='A'&&c<='Z'){
                slug.append((char)(c+32));
            }else if(Character.isWhitespace(c)){
                if(slug.length()>0&&slug.charAt(slug.length()-1)!='_')slug.append('_');
                lossless=false;
            }else{
                lossless=false;
            }
        }
        String base=slug.toString();
        if(base.length()>48)base=base.substring(0,48);
        if(lossless&&!base.isEmpty())return base;
        int hash=0x811c9dc5;
        for(int i=0;i<trimmed.length();i++){
            hash^=trimmed.charAt(i);
            hash*=0x01000193;
        }
        String hex=String.format(Locale.ROOT,"%08x",hash);
        return (base.isEmpty()?"w":base)+"_"+hex;
    }
    private static String normalizeServerHost(String ip){
        if(ip==null)return "";
        String host=ip.trim().toLowerCase(Locale.ROOT);
        if(host.endsWith(":25565"))host=host.substring(0,host.length()-6);
        while(host.endsWith("."))host=host.substring(0,host.length()-1);
        StringBuilder clean=new StringBuilder();
        for(int i=0;i<host.length();i++){
            char c=host.charAt(i);
            if((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='.'||c=='-'||c=='_'||c==':')clean.append(c);
        }
        return clean.toString();
    }
    private static String detectScoreboardRealmLabel(){
        try{
            Map<String,Object> sb=scoreboard();
            if(sb==null||sb.isEmpty())return "";
            Object linesObj=sb.get("lines");
            if(!(linesObj instanceof List))return "";
            List<?> lines=(List<?>)linesObj;
            String hashCandidate="";
            for(int i=0;i<lines.size();i++){
                Object entry=lines.get(i);
                String raw=null;
                if(entry instanceof Map){
                    Object t=((Map<?,?>)entry).get("text");
                    if(t!=null)raw=t.toString();
                }else if(entry instanceof String){
                    raw=(String)entry;
                }
                if(raw==null||raw.isEmpty())continue;
                String clean=REALM_LEAD_PATTERN.matcher(stripMcColors(raw)).replaceFirst("").trim();
                if(clean.length()<2||clean.length()>40)continue;
                String lower=clean.toLowerCase(Locale.ROOT);
                if(lower.contains(".ru")||lower.contains(".net")||lower.contains(".com")||lower.contains(".gg")||lower.contains("http"))continue;
                for(String prefix:new String[]{"режим:","анархия:","сервер:","лобби:","мир:","mode:","realm:","server:","lobby:"}){
                    int idx=lower.indexOf(prefix);
                    if(idx>=0){
                        String val=clean.substring(idx+prefix.length()).trim();
                        if(val.length()>=2&&val.length()<=28)return val;
                    }
                }
                if(hashCandidate.isEmpty()&&clean.indexOf('#')>=0&&REALM_HASH_PATTERN.matcher(clean).matches()
                    &&!lower.contains("онлайн")&&!lower.contains("online")&&!lower.contains("пинг")&&!lower.contains("ping")
                    &&!lower.contains("монет")&&!lower.contains("баланс")&&!lower.contains("$")&&!lower.contains("%")){
                    hashCandidate=clean.length()>28?clean.substring(0,28).trim():clean;
                }
            }
            return hashCandidate;
        }catch(Throwable ignored){
            return "";
        }
    }
    static long biomeZoomSeedForMap(Minecraft mc){return currentBiomeZoomSeed(mc);}
    static String spawnKeyForMap(Minecraft mc){return lockedSpawnKey(mc);}
    public static String currentWorldId(){
        ClientRuntime rt=ClientRuntime.get();
        if(rt!=null&&rt.mapCache()!=null&&rt.mapCache().identity().isActive()){
            String wid=rt.mapCache().identity().worldId();
            if(wid!=null&&!wid.isEmpty())return wid;
        }
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return "";
        net.minecraft.client.server.IntegratedServer sp=mc.getSingleplayerServer();
        if(sp!=null){
            String folder="";
            try{
                java.nio.file.Path root=sp.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                if(root!=null){
                    java.nio.file.Path norm=root.normalize();
                    if(norm.getFileName()!=null)folder=norm.getFileName().toString();
                }
            }catch(Throwable ignored){}
            if(folder.isEmpty()){
                try{folder=sp.getWorldData().getLevelName();}catch(Throwable ignored){}
            }
            String slug=sanitizeWorldSlug(folder);
            return slug.isEmpty()?"sp:default":("sp:"+slug);
        }
        net.minecraft.client.multiplayer.ServerData srv=mc.getCurrentServer();
        String host=srv==null?"":normalizeServerHost(srv.ip);
        if(host.isEmpty())host="server";
        long seed=currentBiomeZoomSeed(mc);
        if(seed!=0L)return "mp:"+host+"/s:"+Long.toHexString(seed);
        String spawn=lockedSpawnKey(mc);
        if(!spawn.isEmpty())return "mp:"+host+"/p:"+spawn;
        return "mp:"+host;
    }
    public static String currentWorldLabel(){
        ClientRuntime rt=ClientRuntime.get();
        if(rt!=null&&rt.mapCache()!=null&&rt.mapCache().identity().isActive()){
            String lbl=rt.mapCache().identity().worldLabel();
            if(lbl!=null&&!lbl.isEmpty())return lbl;
        }
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return "";
        net.minecraft.client.server.IntegratedServer sp=mc.getSingleplayerServer();
        if(sp!=null){
            try{
                String name=sp.getWorldData().getLevelName();
                if(name!=null&&!name.trim().isEmpty())return name.trim();
            }catch(Throwable ignored){}
            return dev.nightclient.i18n.I18n.text(ClientRuntime.language(),"world.singleplayer");
        }
        net.minecraft.client.multiplayer.ServerData srv=mc.getCurrentServer();
        String host=srv==null?"":normalizeServerHost(srv.ip);
        if(host.isEmpty())host=dev.nightclient.i18n.I18n.text(ClientRuntime.language(),"world.server");
        String realm=detectScoreboardRealmLabel();
        if(!realm.isEmpty())return host+" · "+realm;
        long seed=currentBiomeZoomSeed(mc);
        if(seed!=0L){
            String hex=Long.toHexString(seed);
            if(hex.length()>6)hex=hex.substring(0,6);
            return host+" · #"+hex;
        }
        return host;
    }
    public static boolean markHere(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("waypoints")||mc.player==null||mc.level==null||runtime==null)return false;
        double x=mc.player.getX(),y=mc.player.getY(),z=mc.player.getZ();
        if(@@SCREEN@@==null&&mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult){
            net.minecraft.world.phys.BlockHitResult bhr=(net.minecraft.world.phys.BlockHitResult)mc.hitResult;
            if(bhr.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){
                net.minecraft.core.BlockPos pos=bhr.getBlockPos();
                x=pos.getX()+0.5;y=pos.getY()+1.0;z=pos.getZ()+0.5;
            }
        }
        runtime.settings().waypoints.enabled=true;
        dev.nightclient.config.Waypoint added=runtime.settings().waypoints.add("",x,y,z,@@DIMENSION@@,currentWorldId(),currentWorldLabel());
        if(added!=null){runtime.changed();return true;}
        return false;
    }
    private static String itemMetadata(ItemStack stack){
        if(stack==null||stack.isEmpty())return "";
        StringBuilder metadata=new StringBuilder();
        try{Object tag=@@EXTRACT_STACK_TAG@@;if(tag!=null)metadata.append(tag); }catch(Throwable ignored){}
        try{@@ITEM_COMPONENTS_APPEND@@}catch(Throwable ignored){}
        return metadata.toString().toLowerCase(Locale.ROOT);
    }
    private static boolean hasMarker(String metadata,String key,String value){
        if(metadata==null||metadata.isEmpty())return false;
        String normalized=metadata.toLowerCase(Locale.ROOT).replace("\"", "").replace("'", "").replace(" ", "");
        String marker=value.toLowerCase(Locale.ROOT);
        return normalized.contains(key.toLowerCase(Locale.ROOT)+":{name:"+marker+"}")
            ||normalized.contains(key.toLowerCase(Locale.ROOT)+":{type:"+marker+"}")
            ||normalized.contains(key.toLowerCase(Locale.ROOT)+"={name:"+marker+"}")
            ||normalized.contains(key.toLowerCase(Locale.ROOT)+"={type:"+marker+"}");
    }
    private static String stripMcColors(String text){
        if(text==null||text.isEmpty())return "";
        String s=text.indexOf('\u00a7')>=0?MC_COLOR_PATTERN.matcher(text).replaceAll(""):text;
        if(s.indexOf('&')>=0)s=AMP_COLOR_PATTERN.matcher(s).replaceAll("");
        return s.trim();
    }
    private static String itemStackIconId(ItemStack stack){
        if(stack==null||stack.isEmpty())return "";
        String id=itemId(stack);
        if("minecraft:potion".equals(id)||"minecraft:splash_potion".equals(id)||"minecraft:lingering_potion".equals(id)){
            try{
                int rgb=@@EXTRACT_POTION_COLOR@@;
                if(rgb!=0&&rgb!=-1)return id+"#color:"+(rgb&0xFFFFFF);
            }catch(Throwable ignored){}
        }
        return id;
    }
    public static int ping(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.getConnection()==null)return 0;
        net.minecraft.client.multiplayer.PlayerInfo info=mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info!=null?Math.max(0,info.getLatency()):0;
    }
    public static int fps(){
        @@FPS_CODE@@
    }
    public static Map<String,Object> networkInfo(){
        Minecraft mc=Minecraft.getInstance();Map<String,Object> map=new LinkedHashMap<String,Object>();
        boolean worldActive=mc.player!=null&&mc.level!=null,connected=worldActive&&mc.getConnection()!=null;
        net.minecraft.client.multiplayer.PlayerInfo info=connected?mc.getConnection().getPlayerInfo(mc.player.getUUID()):null;
        boolean local=worldActive&&mc.getCurrentServer()==null;
        map.put("connected",connected);map.put("available",local||info!=null);
        map.put("ping",info==null?(local?0:-1):Math.max(0,info.getLatency()));
        map.put("serverAddress",worldActive?(mc.getCurrentServer()==null?dev.nightclient.i18n.I18n.text(ClientRuntime.language(),"world.singleplayer"):mc.getCurrentServer().ip):"");
        return map;
    }
    public static Map<String,Object> keystrokes(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.options==null)return Collections.emptyMap();
        Map<String,Object> map=new LinkedHashMap<String,Object>();
        map.put("w",mc.options.keyUp.isDown());
        map.put("a",mc.options.keyLeft.isDown());
        map.put("s",mc.options.keyDown.isDown());
        map.put("d",mc.options.keyRight.isDown());
        map.put("space",mc.options.keyJump.isDown());
        map.put("lmb",mc.options.keyAttack.isDown());
        map.put("rmb",mc.options.keyUse.isDown());
        map.put("cps_lmb",lmbClicks.size());
        map.put("cps_rmb",rmbClicks.size());
        return map;
    }
    public static Map<String,Object> coordinates(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return Collections.emptyMap();
        Map<String,Object> map=new LinkedHashMap<String,Object>();
        map.put("x",String.format(Locale.ROOT,"%.1f",mc.player.getX()));
        map.put("y",String.format(Locale.ROOT,"%.1f",mc.player.getY()));
        map.put("z",String.format(Locale.ROOT,"%.1f",mc.player.getZ()));
        map.put("dimension",currentDimension());
        map.put("facing",mc.player.getDirection()!=null?mc.player.getDirection().getName():"");
        map.put("speed",String.format(Locale.ROOT,"%.2f",playerSpeedBps));
        return map;
    }
    public static Map<String,Object> scoreboard(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return Collections.emptyMap();
        Scoreboard scoreboard=mc.level.getScoreboard();
        if(scoreboard==null)return Collections.emptyMap();
        @@SCOREBOARD_CODE@@
    }
    private static String formattedText(net.minecraft.network.chat.Component component){
        if(component==null)return "";final StringBuilder text=new StringBuilder();
        component.visit((style,value)->{
            if(value.isEmpty())return java.util.Optional.empty();
            text.append('\u00a7').append('r');
            net.minecraft.network.chat.TextColor color=style.getColor();
            if(color!=null){String hex=String.format(Locale.ROOT,"%06x",color.getValue()&0xffffff);text.append('\u00a7').append('x');for(int i=0;i<hex.length();i++)text.append('\u00a7').append(hex.charAt(i));}
            if(style.isBold())text.append('\u00a7').append('l');
            if(style.isItalic())text.append('\u00a7').append('o');
            if(style.isUnderlined())text.append('\u00a7').append('n');
            if(style.isStrikethrough())text.append('\u00a7').append('m');
            text.append(value);return java.util.Optional.empty();
        },net.minecraft.network.chat.Style.EMPTY);
        return text.toString();
    }
    public static int mainHandCount(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return 1;
        ItemStack stack=mc.player.getMainHandItem();
        return stack.isEmpty()?1:stack.getCount();
    }
    private static String readActionBarText(Minecraft mc){
        if(mc==null||mc.gui==null)return "";
        Object hud=@@HUD_INSTANCE@@;
        if(!(hud instanceof dev.nightclient.platform.mixin.GuiOverlayAccessor))return "";
        dev.nightclient.platform.mixin.GuiOverlayAccessor acc=(dev.nightclient.platform.mixin.GuiOverlayAccessor)hud;
        if(acc.nightclient$overlayMessageTime()<=0)return "";
        net.minecraft.network.chat.Component comp=acc.nightclient$overlayMessageString();
        return comp!=null?comp.getString():"";
    }
    private static boolean isRealPlayer(Minecraft mc,Player other){
        if(mc==null||other==null||other==mc.player||!other.isAlive()||other.isSpectator())return false;
        if(mc.getConnection()!=null){
            return mc.getConnection().getPlayerInfo(other.getUUID())!=null;
        }
        return true;
    }
    private static int localPingMs(Minecraft mc){
        if(mc==null||mc.player==null||mc.getConnection()==null)return 0;
        net.minecraft.client.multiplayer.PlayerInfo info=mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info!=null?Math.max(0,info.getLatency()):0;
    }
    private static boolean isRangedThreatItem(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        net.minecraft.world.item.Item item=stack.getItem();
        return item instanceof net.minecraft.world.item.ProjectileWeaponItem
            || item instanceof net.minecraft.world.item.TridentItem
            || item instanceof net.minecraft.world.item.EnderpearlItem
            || item instanceof net.minecraft.world.item.SnowballItem
            || item instanceof net.minecraft.world.item.EggItem;
    }
    private static boolean isNonPvpDamageType(String msgId){
        if(msgId==null||msgId.isEmpty())return false;
        switch(msgId){
            case "fall":
            case "inFire":
            case "onFire":
            case "lava":
            case "hotFloor":
            case "inWall":
            case "cramming":
            case "drown":
            case "starve":
            case "cactus":
            case "sweetBerryBush":
            case "freeze":
            case "stalagmite":
            case "fallingBlock":
            case "anvil":
            case "fallingStalactite":
            case "flyIntoWall":
            case "outOfWorld":
            case "dryout":
            case "outsideBorder":
            case "mob":
            case "sting":
            case "wither":
            case "sonic_boom":
            case "warden":
                return true;
            default:
                return false;
        }
    }
    public static CombatTagDetector.CombatStatus combatStatus(){
        Minecraft mc=Minecraft.getInstance();
        float blockedAgo=lastLeaveBlockedNanos==0L?Float.POSITIVE_INFINITY:(float)((System.nanoTime()-lastLeaveBlockedNanos)*1e-9);
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()!=null||!mc.player.isAlive()||mc.player.getHealth()<=0f){
            return new CombatTagDetector.CombatStatus(false,-1,blockedAgo,lastLeaveBlockedReason);
        }
        int bestRemaining=-1;
        boolean active=false;
        if(mc.gui!=null){
            Object bossOverlay=@@BOSS_OVERLAY@@;
            if(bossOverlay instanceof dev.nightclient.platform.mixin.BossHealthOverlayAccessor){
                Map<UUID,net.minecraft.client.gui.components.LerpingBossEvent> map=((dev.nightclient.platform.mixin.BossHealthOverlayAccessor)bossOverlay).nightclient$events();
                if(map!=null){
                    for(net.minecraft.client.gui.components.LerpingBossEvent bossEvent:map.values()){
                        if(bossEvent==null||bossEvent.getName()==null)continue;
                        CombatTagDetector.CombatMatch m=CombatTagDetector.parseBossBarText(bossEvent.getName().getString());
                        if(m.active){
                            active=true;
                            if(m.remainingSeconds>0&&(bestRemaining<0||m.remainingSeconds>bestRemaining))bestRemaining=m.remainingSeconds;
                        }
                    }
                }
            }
            String actionBar=readActionBarText(mc);
            if(!actionBar.isEmpty()){
                CombatTagDetector.CombatMatch m=CombatTagDetector.parseCombatText(actionBar);
                if(m.active){
                    active=true;
                    if(m.remainingSeconds>0&&(bestRemaining<0||m.remainingSeconds>bestRemaining))bestRemaining=m.remainingSeconds;
                }
            }
        }
        if(!active){
            try{
                Map<String,Object> sb=scoreboard();
                Object linesObj=sb!=null?sb.get("lines"):null;
                if(linesObj instanceof List){
                    for(Object entry:(List<?>)linesObj){
                        String line=entry instanceof Map?String.valueOf(((Map<?,?>)entry).get("text")):String.valueOf(entry);
                        CombatTagDetector.CombatMatch m=CombatTagDetector.parseScoreboardCombatText(line);
                        if(m.active){
                            active=true;
                            if(m.remainingSeconds>0&&(bestRemaining<0||m.remainingSeconds>bestRemaining))bestRemaining=m.remainingSeconds;
                        }
                    }
                }
            }catch(Throwable ignored){}
        }
        ClientRuntime runtime=ClientRuntime.get();
        Settings.SafeCtLeave ctCfg=runtime!=null?runtime.settings().safeCtLeave:null;
        if(packetCtSetNanos!=0L&&packetCtDurationSec>0){
            double elapsed=(System.nanoTime()-packetCtSetNanos)*1e-9;
            if(elapsed>=0&&elapsed<packetCtDurationSec){
                active=true;
                int rem=Math.max(1,(int)Math.ceil(packetCtDurationSec-elapsed));
                if(bestRemaining<0||rem>bestRemaining)bestRemaining=rem;
            }
        }
        if(!active&&ctCfg!=null&&ctCfg.detectPvpHits&&lastPvpHitNanos!=0L){
            double elapsed=(System.nanoTime()-lastPvpHitNanos)*1e-9;
            int limit=ctCfg.fallbackSeconds;
            if(elapsed>=0&&elapsed<limit){
                active=true;
                bestRemaining=Math.max(1,(int)Math.ceil(limit-elapsed));
            }
        }
        return new CombatTagDetector.CombatStatus(active,bestRemaining,blockedAgo,lastLeaveBlockedReason);
    }
    private static boolean hasNearbyLeaveThreat(Minecraft mc,Settings.SafeCtLeave cfg){
        if(mc==null||mc.player==null||mc.level==null||cfg==null)return false;
        long now=System.nanoTime();
        if(lastOutgoingPlayerAttackNanos!=0L&&(now-lastOutgoingPlayerAttackNanos)<650_000_000L)return true;
        if(lastUnattributedHurtNanos!=0L&&(now-lastUnattributedHurtNanos)<450_000_000L)return true;
        double r=Math.max(3.0,cfg.dangerRadius);
        double rSq=r*r;
        boolean realPlayerNear=false;
        for(Player other:mc.level.players()){
            if(!isRealPlayer(mc,other))continue;
            double dSq=distanceSquared(other,mc.player);
            if(dSq<=rSq)return true;
            if(dSq<=256.0&&(isRangedThreatItem(other.getMainHandItem())||isRangedThreatItem(other.getOffhandItem())))return true;
            if(dSq<=576.0)realPlayerNear=true;
        }
        for(Entity e:mc.level.entitiesForRendering()){
            if(e==null||e==mc.player||!e.isAlive())continue;
            if(e instanceof net.minecraft.world.entity.projectile.Projectile){
                net.minecraft.world.entity.projectile.Projectile proj=(net.minecraft.world.entity.projectile.Projectile)e;
                if(proj.getOwner()!=mc.player&&distanceSquared(e,mc.player)<=256.0&&e.getDeltaMovement().lengthSqr()>0.0025)return true;
            }else if(realPlayerNear){
                if(e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal&&distanceSquared(e,mc.player)<=42.25)return true;
                if(e instanceof net.minecraft.world.entity.item.PrimedTnt&&distanceSquared(e,mc.player)<=56.25){
                    net.minecraft.world.entity.item.PrimedTnt tnt=(net.minecraft.world.entity.item.PrimedTnt)e;
                    if(@@TNT_FUSE_GET@@<=30)return true;
                }
            }
        }
        return false;
    }
    private static PendingLeaveGuard.Snapshot captureLeaveSnapshot(Minecraft mc,Settings.SafeCtLeave cfg,boolean combatActive){
        float hp=mc!=null&&mc.player!=null?mc.player.getHealth()+mc.player.getAbsorptionAmount():0f;
        int hurt=mc!=null&&mc.player!=null?mc.player.hurtTime:0;
        boolean screenOk=!"disconnect".equals(pendingLeaveGuard.kind())||(mc!=null&&@@SCREEN@@ instanceof net.minecraft.client.gui.screens.PauseScreen);
        return new PendingLeaveGuard.Snapshot(
            combatActive,hp,hurt,lastPvpHitNanos,lastUnattributedHurtNanos,packetCtSetNanos,
            lastNettyDamageNanos,lastConfirmedNonPvpDamageNanos,screenOk,isShiftBypassHeld(cfg));
    }
    private static void pollPendingSafeLeave(Minecraft mc,ClientRuntime runtime){
        if(!pendingLeaveGuard.isActive())return;
        if(mc==null||mc.player==null||mc.level==null||!mc.player.isAlive()||mc.player.getHealth()<=0f){
            pendingLeaveGuard.clear();
            return;
        }
        Settings.SafeCtLeave cfg=runtime!=null?runtime.settings().safeCtLeave:null;
        if(cfg==null||!cfg.enabled){
            pendingLeaveGuard.clear();
            return;
        }
        CombatTagDetector.CombatStatus st=combatStatus();
        PendingLeaveGuard.Outcome outcome=pendingLeaveGuard.poll(System.nanoTime(),captureLeaveSnapshot(mc,cfg,st.active));
        if(outcome==PendingLeaveGuard.Outcome.ABORTED_HIT||outcome==PendingLeaveGuard.Outcome.ABORTED_CT){
            if(outcome==PendingLeaveGuard.Outcome.ABORTED_HIT){
                long now=System.nanoTime();
                if(lastPvpHitNanos==0L||(now-lastPvpHitNanos)>1_000_000_000L)lastPvpHitNanos=now;
            }
            CombatTagDetector.CombatStatus after=combatStatus();
            notifyCtBlocked(outcome==PendingLeaveGuard.Outcome.ABORTED_HIT?"hit":"ct",Math.max(after.remainingSeconds,0));
        }else if(outcome==PendingLeaveGuard.Outcome.EXECUTE){
            Runnable replay=pendingLeaveGuard.takeReplay();
            if(replay!=null){
                bypassNextLeave=true;
                try{replay.run();}finally{bypassNextLeave=false;}
            }
        }
    }
    private static boolean hasAggressiveRealPlayerNearby(Minecraft mc,double maxDistSq){
        if(mc==null||mc.player==null||mc.level==null)return false;
        Vec3 myEyes=mc.player.getEyePosition(1.0f);
        for(Player other:mc.level.players()){
            if(!isRealPlayer(mc,other))continue;
            double dSq=distanceSquared(other,mc.player);
            if(dSq>maxDistSq)continue;
            if(@@OTHER_SWINGING@@)return true;
            Vec3 look=other.getViewVector(1.0f);
            Vec3 toMe=myEyes.subtract(other.getEyePosition(1.0f)).normalize();
            if(look.dot(toMe)>=0.55&&hasPlayerLineOfSight(mc,myEyes,other,other.position()))return true;
        }
        return false;
    }
    public static void onHurtAnimationPacket(int entityId){
        long now=System.nanoTime();
        if(entityId==lastOutgoingAttackedPlayerId&&lastOutgoingAttackedPlayerId!=Integer.MIN_VALUE
            &&(now-lastOutgoingPlayerAttackNanos)<650_000_000L){
            lastPvpHitNanos=now;
            return;
        }
        if(entityId!=localPlayerEntityId)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null)return;
        if(!mc.isSameThread()){
            if((now-lastConfirmedNonPvpDamageNanos)>200_000_000L){
                lastNettyDamageNanos=now;
            }
            return;
        }
        if(mc.player==null||mc.level==null)return;
        if((now-lastConfirmedNonPvpDamageNanos)<250_000_000L)return;
        lastUnattributedHurtNanos=now;
        if(pendingLeaveGuard.isActive()){
            pollPendingSafeLeave(mc,ClientRuntime.get());
        }
    }
    public static void onDamageEventPacket(net.minecraft.network.protocol.game.ClientboundDamageEventPacket packet){
        if(packet==null)return;
        long now=System.nanoTime();
        int entityId=packet.entityId();
        int causeId=packet.sourceCauseId();
        if(entityId==lastOutgoingAttackedPlayerId&&lastOutgoingAttackedPlayerId!=Integer.MIN_VALUE
            &&(now-lastOutgoingPlayerAttackNanos)<650_000_000L
            &&(causeId==localPlayerEntityId||causeId<0)){
            lastPvpHitNanos=now;
            return;
        }
        if(entityId!=localPlayerEntityId)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null)return;
        if(!mc.isSameThread()){
            if(causeId==localPlayerEntityId){
                lastConfirmedNonPvpDamageNanos=now;
            }else{
                lastNettyDamageNanos=now;
            }
            return;
        }
        if(mc.player==null||mc.level==null)return;
        net.minecraft.world.damagesource.DamageSource src=null;
        try{src=packet.getSource(mc.level);}catch(Throwable ignored){}
        Entity cause=src!=null?src.getEntity():null;
        Entity direct=src!=null?src.getDirectEntity():null;
        if(cause==null&&direct instanceof net.minecraft.world.entity.projectile.Projectile){
            cause=((net.minecraft.world.entity.projectile.Projectile)direct).getOwner();
        }
        if((cause instanceof Player&&cause!=mc.player&&isRealPlayer(mc,(Player)cause))
            ||(direct instanceof Player&&direct!=mc.player&&isRealPlayer(mc,(Player)direct))){
            lastPvpHitNanos=now;
            if(pendingLeaveGuard.isActive())pollPendingSafeLeave(mc,ClientRuntime.get());
            return;
        }
        if(cause==mc.player||cause!=null){
            lastConfirmedNonPvpDamageNanos=now;
            return;
        }
        if(direct!=null
            &&!(direct instanceof Player)
            &&!(direct instanceof net.minecraft.world.entity.projectile.Projectile)
            &&!(direct instanceof net.minecraft.world.entity.item.PrimedTnt)
            &&!(direct instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal)){
            lastConfirmedNonPvpDamageNanos=now;
            return;
        }
        String msgId=src!=null?src.getMsgId():"";
        if(isNonPvpDamageType(msgId)){
            lastConfirmedNonPvpDamageNanos=now;
            return;
        }
        lastUnattributedHurtNanos=now;
        if(pendingLeaveGuard.isActive()){
            pollPendingSafeLeave(mc,ClientRuntime.get());
            return;
        }
        boolean obviousEnv=mc.player.fallDistance>2.5f||mc.player.isOnFire()||mc.player.isInLava()||mc.player.getFoodData().getFoodLevel()<=0;
        if(!obviousEnv){
            boolean explosion="explosion".equals(msgId)||"explosion.player".equals(msgId)||"badRespawnPoint".equals(msgId);
            if(hasAggressiveRealPlayerNearby(mc,explosion?144.0:30.25)){
                lastPvpHitNanos=now;
            }
        }
    }
    public static void onActionBarPacket(String text){
        if(text==null||text.isEmpty())return;
        onPacketCombatText(text);
        ClientRuntime rt=ClientRuntime.get();
        if(rt!=null&&rt.hwhelper()!=null){
            rt.hwhelper().explosiveThingCooldown().onServerCooldownMessage(text,System.currentTimeMillis());
        }
    }
    public static void onPacketCombatText(String text){
        if(text==null||text.isEmpty())return;
        if(CombatTagDetector.isCombatEndText(text)){
            packetCtSetNanos=0L;packetCtDurationSec=0;lastPvpHitNanos=0L;return;
        }
        CombatTagDetector.CombatMatch m=CombatTagDetector.parseCombatText(text);
        if(m.active){
            packetCtSetNanos=System.nanoTime();
            ClientRuntime rt=ClientRuntime.get();
            int fb=(rt!=null&&rt.settings().safeCtLeave!=null)?rt.settings().safeCtLeave.fallbackSeconds:15;
            packetCtDurationSec=m.remainingSeconds>0?m.remainingSeconds:fb;
            Minecraft mc=Minecraft.getInstance();
            if(mc!=null&&mc.isSameThread()&&pendingLeaveGuard.isActive()){
                pollPendingSafeLeave(mc,rt);
            }
        }
    }
    public static void onChatCombatText(String text){
        if(text==null||text.isEmpty()||suppressOwnChatCt)return;
        if(CombatTagDetector.isCombatEndText(text)){
            packetCtSetNanos=0L;packetCtDurationSec=0;lastPvpHitNanos=0L;return;
        }
        CombatTagDetector.CombatMatch m=CombatTagDetector.parseChatCombatText(text);
        if(m.active){
            packetCtSetNanos=System.nanoTime();
            ClientRuntime rt=ClientRuntime.get();
            int fb=(rt!=null&&rt.settings().safeCtLeave!=null)?rt.settings().safeCtLeave.fallbackSeconds:15;
            packetCtDurationSec=m.remainingSeconds>0?m.remainingSeconds:fb;
            Minecraft mc=Minecraft.getInstance();
            if(mc!=null&&mc.isSameThread()&&pendingLeaveGuard.isActive()){
                pollPendingSafeLeave(mc,rt);
            }
        }
    }
    public static void onBossBarPacketUpdated(){
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.gui==null)return;
        Object bossOverlay=@@BOSS_OVERLAY@@;
        if(!(bossOverlay instanceof dev.nightclient.platform.mixin.BossHealthOverlayAccessor))return;
        Map<UUID,net.minecraft.client.gui.components.LerpingBossEvent> map=((dev.nightclient.platform.mixin.BossHealthOverlayAccessor)bossOverlay).nightclient$events();
        if(map==null)return;
        boolean foundCt=false;
        for(net.minecraft.client.gui.components.LerpingBossEvent bossEvent:map.values()){
            if(bossEvent==null||bossEvent.getName()==null)continue;
            String title=bossEvent.getName().getString();
            CombatTagDetector.CombatMatch bm=CombatTagDetector.parseBossBarText(title);
            if(bm.active){
                foundCt=true;
                packetCtSetNanos=System.nanoTime();
                ClientRuntime rt=ClientRuntime.get();
                int fb=(rt!=null&&rt.settings().safeCtLeave!=null)?rt.settings().safeCtLeave.fallbackSeconds:15;
                packetCtDurationSec=bm.remainingSeconds>0?bm.remainingSeconds:fb;
            }
            onPacketCombatText(title);
        }
        if(foundCt){
            bossBarHadCt=true;
        }else if(bossBarHadCt){
            bossBarHadCt=false;
            packetCtSetNanos=0L;
            packetCtDurationSec=0;
            lastPvpHitNanos=0L;
        }
    }
    private static boolean isShiftBypassHeld(Settings.SafeCtLeave cfg){
        if(cfg==null||!cfg.shiftBypass)return false;
        Minecraft mc=Minecraft.getInstance();
        return @@SHIFT_DOWN@@;
    }
    private static void notifyCtBlocked(String reason,int remainingSeconds){
        lastLeaveBlockedNanos=System.nanoTime();
        lastLeaveBlockedReason=reason==null?"":reason;
        long now=System.nanoTime();
        if(now-lastCtWarnChatNanos<1200000000L)return;
        lastCtWarnChatNanos=now;
        ClientRuntime runtime=ClientRuntime.get();
        String lang=runtime==null?ClientRuntime.language():dev.nightclient.i18n.I18n.language(runtime.settings().language);
        boolean bypass=runtime!=null&&runtime.settings().safeCtLeave!=null&&runtime.settings().safeCtLeave.shiftBypass;
        String timer=remainingSeconds>0?dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.msg.timer",Collections.<String,Object>singletonMap("sec",Integer.valueOf(remainingSeconds))):"";
        String hint=bypass?dev.nightclient.i18n.I18n.text(lang,"safeCtLeave.msg.shift_hint"):"";
        Map<String,Object> args=new LinkedHashMap<String,Object>(4);
        args.put("timer",timer);
        args.put("hint",hint);
        String msg=dev.nightclient.i18n.I18n.format(lang,"hit".equals(reason)?"safeCtLeave.msg.hit_abort":"safeCtLeave.msg.blocked",args);
        suppressOwnChatCt=true;
        try{sendChatMessage(msg);}finally{suppressOwnChatCt=false;}
    }
    private static boolean isPauseDisconnectButton(Minecraft mc,Object buttonObj){
        if(mc==null||!(@@SCREEN@@ instanceof net.minecraft.client.gui.screens.PauseScreen))return false;
        if(!(buttonObj instanceof net.minecraft.client.gui.components.AbstractWidget))return false;
        Object screen=@@SCREEN@@;
        try{
            for(java.lang.reflect.Field f:net.minecraft.client.gui.screens.PauseScreen.class.getDeclaredFields()){
                if(net.minecraft.client.gui.components.Button.class.isAssignableFrom(f.getType())){
                    f.setAccessible(true);
                    if(f.get(screen)==buttonObj)return true;
                }
            }
        }catch(Throwable ignored){}
        if(buttonObj==lastPauseDisconnectBtn)return true;
        net.minecraft.network.chat.Component msg=((net.minecraft.client.gui.components.AbstractWidget)buttonObj).getMessage();
        if(msg!=null){
            String full=msg.toString().toLowerCase(Locale.ROOT);
            if(full.contains("menu.disconnect")||full.contains("menu.returntomenu"))return true;
            String plain=stripMcColors(msg.getString()).toLowerCase(Locale.ROOT);
            if(plain.equals("disconnect")||plain.equals("отключиться")||plain.contains("save and quit")||plain.contains("сохранить и выйти"))return true;
        }
        return false;
    }
    private static void syncPauseScreenButton(Minecraft mc,ClientRuntime runtime){
        if(mc==null||runtime==null)return;
        if(!(@@SCREEN@@ instanceof net.minecraft.client.gui.screens.PauseScreen)){
            lastPauseDisconnectBtn=null;lastPauseDisconnectOrig=null;
            return;
        }
        net.minecraft.client.gui.components.AbstractWidget btn=null;
        Object screen=@@SCREEN@@;
        try{
            for(java.lang.reflect.Field f:net.minecraft.client.gui.screens.PauseScreen.class.getDeclaredFields()){
                if(net.minecraft.client.gui.components.Button.class.isAssignableFrom(f.getType())){
                    f.setAccessible(true);
                    Object v=f.get(screen);
                    if(v instanceof net.minecraft.client.gui.components.AbstractWidget){btn=(net.minecraft.client.gui.components.AbstractWidget)v;break;}
                }
            }
        }catch(Throwable ignored){}
        if(btn==null&&screen instanceof net.minecraft.client.gui.screens.Screen){
            for(net.minecraft.client.gui.components.events.GuiEventListener child:((net.minecraft.client.gui.screens.Screen)screen).children()){
                if(child instanceof net.minecraft.client.gui.components.Button&&isPauseDisconnectButton(mc,child)){
                    btn=(net.minecraft.client.gui.components.AbstractWidget)child;break;
                }
            }
        }
        if(btn==null)return;
        if(lastPauseDisconnectBtn!=btn){
            lastPauseDisconnectBtn=btn;
            lastPauseDisconnectOrig=btn.getMessage();
        }
        Settings.SafeCtLeave cfg=runtime.settings().safeCtLeave;
        CombatTagDetector.CombatStatus st=!FeaturePolicy.isBlocked("safeCtLeave")&&cfg!=null&&cfg.enabled&&cfg.blockDisconnect?combatStatus():CombatTagDetector.CombatStatus.NONE;
        String lang=dev.nightclient.i18n.I18n.language(runtime.settings().language);
        if(st.active){
            String timer=st.remainingSeconds>0?dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.msg.timer",Collections.<String,Object>singletonMap("sec",Integer.valueOf(st.remainingSeconds))):"";
            String text=dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.btn.blocked",Collections.<String,Object>singletonMap("timer",timer));
            btn.setMessage(net.minecraft.network.chat.Component.nullToEmpty(text));
        }else if(pendingLeaveGuard.isActive()&&"disconnect".equals(pendingLeaveGuard.kind())){
            String text=dev.nightclient.i18n.I18n.text(lang,"safeCtLeave.btn.checking");
            btn.setMessage(net.minecraft.network.chat.Component.nullToEmpty(text));
        }else if(lastPauseDisconnectOrig!=null&&btn.getMessage()!=lastPauseDisconnectOrig){
            btn.setMessage(lastPauseDisconnectOrig);
        }
    }
    public static boolean shouldBlockDisconnect(Object buttonObj,Runnable replayAction){
        if(bypassNextLeave)return false;
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||mc.level==null||mc.getSingleplayerServer()!=null)return false;
        Settings.SafeCtLeave cfg=runtime.settings().safeCtLeave;
        if(FeaturePolicy.isBlocked("safeCtLeave")||cfg==null||!cfg.enabled||!cfg.blockDisconnect)return false;
        if(!isPauseDisconnectButton(mc,buttonObj))return false;
        if(isShiftBypassHeld(cfg)){pendingLeaveGuard.clear();return false;}
        CombatTagDetector.CombatStatus st=combatStatus();
        if(st.active){
            pendingLeaveGuard.clear();
            lastLeaveBlockedNanos=System.nanoTime();
            lastLeaveBlockedReason="disconnect";
            if(cfg.confirmDisconnect&&replayAction!=null){
                final net.minecraft.client.gui.screens.Screen prevScreen=@@SCREEN@@;
                String lang=dev.nightclient.i18n.I18n.language(runtime.settings().language);
                String timer=st.remainingSeconds>0?dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.msg.timer",Collections.<String,Object>singletonMap("sec",Integer.valueOf(st.remainingSeconds))):"";
                net.minecraft.network.chat.Component title=net.minecraft.network.chat.Component.nullToEmpty(dev.nightclient.i18n.I18n.text(lang,"safeCtLeave.confirm.title"));
                net.minecraft.network.chat.Component message=net.minecraft.network.chat.Component.nullToEmpty(dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.confirm.message",Collections.<String,Object>singletonMap("timer",timer)));
                net.minecraft.network.chat.Component yes=net.minecraft.network.chat.Component.nullToEmpty(dev.nightclient.i18n.I18n.text(lang,"safeCtLeave.confirm.yes"));
                net.minecraft.network.chat.Component no=net.minecraft.network.chat.Component.nullToEmpty(dev.nightclient.i18n.I18n.text(lang,"safeCtLeave.confirm.no"));
                net.minecraft.client.gui.screens.Screen nextScreen=new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{
                    if(confirmed){
                        bypassNextLeave=true;
                        try{replayAction.run();}finally{bypassNextLeave=false;}
                    }else{
                        net.minecraft.client.gui.screens.Screen back=prevScreen;
                        net.minecraft.client.gui.screens.Screen nextScreen2=back;
                        setMinecraftScreen(mc,nextScreen2);
                    }
                },title,message,yes,no);
                setMinecraftScreen(mc,nextScreen);
                return true;
            }
            notifyCtBlocked("disconnect",st.remainingSeconds);
            return true;
        }
        if(cfg.dangerGuard&&hasNearbyLeaveThreat(mc,cfg)){
            if(!pendingLeaveGuard.isActive()){
                pendingLeaveGuard.begin("disconnect",replayAction,System.nanoTime(),localPingMs(mc),captureLeaveSnapshot(mc,cfg,false));
            }
            return true;
        }
        return false;
    }
    public static boolean shouldBlockWindowClose(){
        if(bypassNextLeave)return false;
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||mc.level==null||mc.getSingleplayerServer()!=null)return false;
        Settings.SafeCtLeave cfg=runtime.settings().safeCtLeave;
        if(FeaturePolicy.isBlocked("safeCtLeave")||cfg==null||!cfg.enabled||!cfg.blockWindowClose)return false;
        if(isShiftBypassHeld(cfg)){pendingLeaveGuard.clear();return false;}
        CombatTagDetector.CombatStatus st=combatStatus();
        if(st.active){
            pendingLeaveGuard.clear();
            notifyCtBlocked("window",st.remainingSeconds);
            return true;
        }
        if(cfg.dangerGuard&&hasNearbyLeaveThreat(mc,cfg)){
            if(!pendingLeaveGuard.isActive()){
                pendingLeaveGuard.begin("window",()->mc.stop(),System.nanoTime(),localPingMs(mc),captureLeaveSnapshot(mc,cfg,false));
            }
            return true;
        }
        return false;
    }
    public static boolean shouldBlockCommand(String command){
        if(bypassNextLeave)return false;
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||mc.player==null||mc.level==null||mc.getSingleplayerServer()!=null)return false;
        Settings.SafeCtLeave cfg=runtime.settings().safeCtLeave;
        if(FeaturePolicy.isBlocked("safeCtLeave")||cfg==null||!cfg.enabled||!cfg.blockCommands)return false;
        if(!CombatTagDetector.isBlockedLeaveCommand(command))return false;
        if(isShiftBypassHeld(cfg)){pendingLeaveGuard.clear();return false;}
        CombatTagDetector.CombatStatus st=combatStatus();
        if(st.active){
            pendingLeaveGuard.clear();
            lastLeaveBlockedNanos=System.nanoTime();
            lastLeaveBlockedReason="command";
            long now=System.nanoTime();
            if(now-lastPvpCmdWarnNanos>=3_000_000_000L){
                lastPvpCmdWarnNanos=now;
                String lang=dev.nightclient.i18n.I18n.language(runtime.settings().language);
                String timer=st.remainingSeconds>0?dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.msg.timer",Collections.<String,Object>singletonMap("sec",Integer.valueOf(st.remainingSeconds))):"";
                String msg=dev.nightclient.i18n.I18n.format(lang,"safeCtLeave.msg.pvp_command",Collections.<String,Object>singletonMap("timer",timer));
                suppressOwnChatCt=true;
                try{sendChatMessage(msg);}finally{suppressOwnChatCt=false;}
            }
            return true;
        }
        if(cfg.dangerGuard&&hasNearbyLeaveThreat(mc,cfg)){
            if(!pendingLeaveGuard.isActive()){
                final String replayCmd=CombatTagDetector.normalizeLeaveCommand(command);
                pendingLeaveGuard.begin("command",()->sendCommandToServer(replayCmd),System.nanoTime(),localPingMs(mc),captureLeaveSnapshot(mc,cfg,false));
            }
            return true;
        }
        return false;
    }
    public static boolean isInCombat(){
        return combatStatus().active;
    }
    private static void setMinecraftScreen(Minecraft mc,net.minecraft.client.gui.screens.Screen nextScreen){
        if(mc==null)return;
        @@SET_SCREEN@@
    }
    private static void setActionbarMessage(Minecraft mc,String msg){
        if(mc==null||msg==null)return;
        @@SET_OVERLAY_MESSAGE@@
    }
    @@NBT_HELPERS@@
    private static net.minecraft.nbt.CompoundTag extractStackNbt(ItemStack stack){
        if(stack==null||stack.isEmpty())return null;
        try{
            Object tagObj=@@EXTRACT_STACK_TAG@@;
            if(tagObj instanceof net.minecraft.nbt.CompoundTag)return (net.minecraft.nbt.CompoundTag)tagObj;
        }catch(Throwable ignored){}
        return null;
    }
    private static net.minecraft.nbt.CompoundTag extractBukkitValues(ItemStack stack){
        net.minecraft.nbt.CompoundTag root=extractStackNbt(stack);
        if(root==null)return null;
        if(root.contains("PublicBukkitValues"))return nbtCompound(root,"PublicBukkitValues");
        if(root.contains("publicBukkitValues"))return nbtCompound(root,"publicBukkitValues");
        return null;
    }
    private static String readBukkitKey(net.minecraft.nbt.CompoundTag pbv,String shortKey){
        if(pbv==null)return "";
        for(String k:nbtKeys(pbv)){
            if(k.equals(shortKey)||k.endsWith(":"+shortKey)){
                String val=nbtString(pbv,k);
                if(!val.isEmpty())return val;
            }
        }
        return "";
    }
    private static int readBukkitInt(net.minecraft.nbt.CompoundTag pbv,String shortKey){
        if(pbv==null)return 0;
        for(String k:nbtKeys(pbv)){
            if(k.equals(shortKey)||k.endsWith(":"+shortKey)){
                int v=nbtInt(pbv,k);
                if(v!=0)return v;
                String s=nbtString(pbv,k);
                if(!s.isEmpty()){try{return Integer.parseInt(s.trim());}catch(NumberFormatException ignored){}}
            }
        }
        return 0;
    }
    private static long readBukkitLong(net.minecraft.nbt.CompoundTag pbv,String shortKey){
        if(pbv==null)return 0L;
        for(String k:nbtKeys(pbv)){
            if(k.equals(shortKey)||k.endsWith(":"+shortKey)){
                long v=nbtLong(pbv,k);
                if(v!=0L)return v;
                int iv=nbtInt(pbv,k);
                if(iv!=0)return iv;
                String s=nbtString(pbv,k);
                if(!s.isEmpty()){try{return Long.parseLong(s.trim());}catch(NumberFormatException ignored){}}
            }
        }
        return 0L;
    }
    private static net.minecraft.nbt.CompoundTag extractGoldenSpawnerTag(ItemStack stack){
        net.minecraft.nbt.CompoundTag root=extractStackNbt(stack);
        if(root!=null&&hasBukkitKey(root,"gs_marker"))return root;
        net.minecraft.nbt.CompoundTag pbv=extractBukkitValues(stack);
        if(pbv!=null&&hasBukkitKey(pbv,"gs_marker"))return pbv;
        return null;
    }
    private static boolean hasBukkitKey(net.minecraft.nbt.CompoundTag pbv,String shortKey){
        if(pbv==null)return false;
        for(String k:nbtKeys(pbv)){
            if(k.equals(shortKey)||k.endsWith(":"+shortKey))return true;
        }
        return false;
    }
    public static void renderSlotHighlight(@@CONTEXT@@ graphics,Slot slot){
        if(graphics==null||slot==null||!slot.hasItem())return;
        ItemStack stack=slot.getItem();
        if(stack.getItem()!=net.minecraft.world.item.Items.SPAWNER)return;
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null)return;
        Settings.Tooltips tt=runtime.settings().tooltips;
        Settings.HwHelper hw=runtime.settings().hwhelper;
        boolean hwTooltipsActive=tt!=null&&tt.hwTooltips&&((!FeaturePolicy.isBlocked("tooltips")&&tt.enabled)||(!FeaturePolicy.isBlocked("hwhelper")&&hw!=null&&hw.enabled));
        if(!hwTooltipsActive||!tt.hwSpawnerHighlight)return;
        net.minecraft.nbt.CompoundTag spTag=extractGoldenSpawnerTag(stack);
        if(spTag==null)return;
        String swordJson=readBukkitKey(spTag,"gs_sword_item");
        int color=TooltipsLogic.spawnerSlotHighlightColor(true,swordJson);
        if((color>>>24)!=0){
            @@SLOT_FILL_CALL@@
        }
    }
    public static boolean isTabAnimationEnabled(){
        ClientRuntime runtime=ClientRuntime.get();
        return !FeaturePolicy.isBlocked("tabAnimation")&&runtime!=null&&runtime.settings().tabAnimation!=null&&runtime.settings().tabAnimation.enabled;
    }
    public static boolean isTabAnimationActive(){
        return !FeaturePolicy.isBlocked("tabAnimation")&&tabAnimator.progress()>0.001f;
    }
    public static boolean renderAnimatedTabList(@@CONTEXT@@ graphics,net.minecraft.client.gui.components.PlayerTabOverlay tabList){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("tabAnimation")||runtime==null||runtime.settings().tabAnimation==null||!runtime.settings().tabAnimation.enabled||graphics==null||tabList==null){
            return false;
        }
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.level==null||mc.options==null)return false;
        Scoreboard sb=mc.level.getScoreboard();
        net.minecraft.world.scores.Objective obj=@@TAB_LIST_OBJECTIVE@@;
        boolean manyPlayers=mc.getConnection()!=null&&mc.getConnection().getListedOnlinePlayers().size()>1;
        boolean targetOpen=mc.options.keyPlayerList.isDown()&&(mc.getSingleplayerServer()==null||manyPlayers||obj!=null);
        dev.nightclient.runtime.TabAnimationLogic.Frame frame=tabAnimator.update(targetOpen,runtime.settings().tabAnimation,System.nanoTime());
        if(!frame.visible){
            tabList.setVisible(false);
            return true;
        }
        int screenW=mc.getWindow().getGuiScaledWidth();
        float cx=screenW*0.5f;
        tabList.setVisible(true);
        @@TAB_ANIM_PUSH_TRANSFORM@@
        try{
            @@TAB_ANIM_DRAW@@
        }finally{
            @@TAB_ANIM_POP_TRANSFORM@@
        }
        return true;
    }
    public static String openContainerTitle(){
        Minecraft mc=Minecraft.getInstance();
        return @@SCREEN@@ instanceof AbstractContainerScreen?@@SCREEN@@.getTitle().getString():null;
    }
    private static String readBukkitValue(ItemStack stack, String key){
        if(stack==null||stack.isEmpty())return "";
        try{
            Object tagObj = @@EXTRACT_STACK_TAG@@;
            if(tagObj==null)return "";
            @@READ_BUKKIT_STRING@@
            String s = tagObj.toString();
            int idx = s.indexOf(key);
            if(idx>=0){
                int colon = s.indexOf(':', idx + key.length());
                if(colon>=0){
                    int start = colon + 1;
                    while(start<s.length()&&(s.charAt(start)==' '||s.charAt(start)=='"'))start++;
                    int end = start;
                    while(end<s.length()&&s.charAt(end)!='"'&&s.charAt(end)!=','&&s.charAt(end)!='}'&&s.charAt(end)!='\n')end++;
                    if(end>start)return s.substring(start, end);
                }
            }
        }catch(Throwable ignored){}
        return "";
    }
    private static String readFirstBukkitValue(ItemStack stack, String... keys){
        for(String key : keys){
            String value = readBukkitValue(stack, key);
            if(value != null && !value.isEmpty()) return value;
        }
        return "";
    }
    private static String extractJsonComponentText(String raw){
        if(raw==null||raw.isEmpty())return "";
        if(raw.charAt(0)!='{'&&raw.charAt(0)!='[')return raw;
        StringBuilder out=new StringBuilder();
        java.util.regex.Matcher m=JSON_TEXT_PATTERN.matcher(raw);
        while(m.find()){
            String piece=m.group(1).replace("\\\"","\"").replace("\\\\","\\");
            out.append(piece);
        }
        return out.length()>0?out.toString():raw;
    }
    private static String readLore(ItemStack stack){
        if(stack==null||stack.isEmpty())return "";
        try{
            @@READ_LORE_BODY@@
        }catch(Throwable ignored){}
        return "";
    }
    public static List<dev.nightclient.runtime.PlatformServices.ContainerSlot> containerSlots(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!(@@SCREEN@@ instanceof AbstractContainerScreen))return Collections.emptyList();
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)(Object)@@SCREEN@@;
        Object playerInv = @@PLAYER_INVENTORY@@;
        List<dev.nightclient.runtime.PlatformServices.ContainerSlot> list=new ArrayList<dev.nightclient.runtime.PlatformServices.ContainerSlot>();
        List<Slot> slots = screen.nightclient$menu().slots;
        for(int i = 0; i < slots.size(); i++){
            Slot slot = slots.get(i);
            if(slot.container == playerInv) continue;
            ItemStack stack = slot.getItem();
            if(stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            String serverType = readFirstBukkitValue(stack, "advancedserverselecter:server-type", "advancedserverselector:server-type", "server-type");
            String server = readFirstBukkitValue(stack, "advancedserverselecter:server", "advancedserverselector:server", "server");
            String lore = readLore(stack);
            list.add(new dev.nightclient.runtime.PlatformServices.ContainerSlot(i, name, serverType, server, lore, itemId(stack)));
        }
        return list;
    }
    public static List<String> containerSlotNames(){
        Minecraft mc=Minecraft.getInstance();
        if(!(@@SCREEN@@ instanceof AbstractContainerScreen))return Collections.emptyList();
        ContainerFeatureAccessor screen=(ContainerFeatureAccessor)(Object)@@SCREEN@@;
        List<String> names=new ArrayList<String>();
        for(Slot slot:screen.nightclient$menu().slots){
            ItemStack stack=slot.getItem();
            names.add(stack.isEmpty()?"":stack.getHoverName().getString());
        }
        return names;
    }
    public static boolean clickContainerSlot(int slotIndex){
        Minecraft mc=Minecraft.getInstance();
        if(@@SCREEN@@ instanceof AbstractContainerScreen){
            ContainerFeatureAccessor screen=(ContainerFeatureAccessor)(Object)@@SCREEN@@;
            if(slotIndex>=0&&slotIndex<screen.nightclient$menu().slots.size()){
                Slot slot=screen.nightclient$menu().slots.get(slotIndex);
                screen.nightclient$quickMove(slot,slot.index,0,@@CLICK_TYPE@@.PICKUP);
                return true;
            }
        }
        return false;
    }
    public static boolean closeContainer(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!(@@SCREEN@@ instanceof AbstractContainerScreen))return false;
        mc.player.closeContainer();
        return true;
    }
    public static boolean useSelectorItem(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(@@SCREEN@@ instanceof AbstractContainerScreen)return false;
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        int match=-1;
        for(int i=0;i<9;i++){
            ItemStack s=inv.getItem(i);
            if(s.isEmpty())continue;
            String joined=(s.getHoverName().getString()+" "+readLore(s)+" "+itemId(s)).toLowerCase(Locale.ROOT);
            if(joined.contains("выбор режима")||joined.contains("выбрать режим")||joined.contains("server")||joined.contains("menu")||s.getItem()==net.minecraft.world.item.Items.COMPASS){
                match=i;
                if(joined.contains("выбор режима")||joined.contains("выбрать режим"))break;
            }
        }
        if(match<0)return false;
        @@SET_SELECTED_SLOT@@
        @@USE_MAIN_HAND_ITEM@@
        return true;
    }
    public static void sendChatMessage(String message){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null&&message!=null){
            @@SEND_CLIENT_MESSAGE@@
        }
    }
    public static void sendCommandToServer(String command){
        if(command==null||command.isEmpty())return;
        Minecraft mc=Minecraft.getInstance();
        try{
            @@SEND_COMMAND_TO_SERVER@@
        }catch(Throwable ignored){}
    }
    public static String lastChatMessage(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui==null)return "";
        Object chat=@@CHAT_COMPONENT@@;
        if(!(chat instanceof dev.nightclient.platform.mixin.ChatLinesAccessor))return "";
        List<?> list=((dev.nightclient.platform.mixin.ChatLinesAccessor)chat).nightclient$allMessages();
        if(list==null||list.isEmpty())return "";
        int limit=Math.min(list.size(),5);
        for(int i=0;i<limit;i++){
            String text=extractMessageText(list.get(i));
            if(text!=null&&!text.isEmpty())return text;
        }
        return "";
    }
    public static Object lastChatToken(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui==null)return null;
        Object chat=@@CHAT_COMPONENT@@;
        if(!(chat instanceof dev.nightclient.platform.mixin.ChatLinesAccessor))return null;
        List<?> list=((dev.nightclient.platform.mixin.ChatLinesAccessor)chat).nightclient$allMessages();
        return list!=null&&!list.isEmpty()?list.get(0):null;
    }
    public static boolean hasRecentChatMessage(String needle){
        if(needle==null||needle.isEmpty())return false;
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui==null)return false;
        Object chat=@@CHAT_COMPONENT@@;
        if(!(chat instanceof dev.nightclient.platform.mixin.ChatLinesAccessor))return false;
        List<?> list=((dev.nightclient.platform.mixin.ChatLinesAccessor)chat).nightclient$allMessages();
        if(list==null||list.isEmpty())return false;
        String lowerNeedle=needle.toLowerCase(Locale.ROOT);
        int max=Math.min(list.size(),20);
        for(int i=0;i<max;i++){
            String text=extractMessageText(list.get(i));
            if(text!=null&&text.toLowerCase(Locale.ROOT).contains(lowerNeedle))return true;
        }
        return false;
    }
    public static Object currentWorldToken(){
        Minecraft mc=Minecraft.getInstance();
        return mc.level;
    }
    public static void formatTooltipLines(ItemStack stack,List<net.minecraft.network.chat.Component> lines){
        if(lines==null||lines.isEmpty())return;
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null)return;
        try{formatTooltipLinesMutable(stack,lines,runtime);}catch(UnsupportedOperationException ignored){}
    }
    private static void formatTooltipLinesMutable(ItemStack stack,List<net.minecraft.network.chat.Component> lines,ClientRuntime runtime){
        Minecraft mc=Minecraft.getInstance();
        boolean inContainer=@@SCREEN@@ instanceof AbstractContainerScreen;
        String screenTitle=inContainer?((AbstractContainerScreen<?>)@@SCREEN@@).getTitle().getString():null;
        if(inContainer&&!FeaturePolicy.isBlocked("hwhelper.aucprice")&&runtime.hwhelper()!=null){
            if(screenTitle==null||(!screenTitle.contains("Покупка")&&!screenTitle.contains("Биржа"))){
                for(int i=0;i<lines.size();i++){
                    net.minecraft.network.chat.Component line=lines.get(i);
                    if(line==null)continue;
                    String raw=line.getString();
                    String formatted=runtime.hwhelper().formatTooltipPrice(raw);
                    if(formatted!=null&&!formatted.equals(raw)){
                        lines.set(i,line.copy().append(net.minecraft.network.chat.Component.nullToEmpty(formatted.substring(raw.length()))));
                    }
                }
            }
        }
        Settings.Tooltips tt=runtime.settings().tooltips;
        Settings.HwHelper hwCfg=runtime.settings().hwhelper;
        boolean tooltipsEnabled=!FeaturePolicy.isBlocked("tooltips")&&tt!=null&&tt.enabled;
        boolean hwTooltipsActive=tt!=null&&tt.hwTooltips&&(tooltipsEnabled||(!FeaturePolicy.isBlocked("hwhelper")&&hwCfg!=null&&hwCfg.enabled));
        if((!tooltipsEnabled&&!hwTooltipsActive)||stack==null||stack.isEmpty())return;
        String lang=dev.nightclient.i18n.I18n.language(runtime.settings().language);
        String id=itemId(stack);
        if(hwTooltipsActive&&tt.hwKnockbackHighlight&&TooltipsLogic.isSwordId(id)){
            String warnPrefix=dev.nightclient.i18n.I18n.text(lang,"tooltips.knockback.warn");
            for(int i=1;i<lines.size();i++){
                net.minecraft.network.chat.Component line=lines.get(i);
                if(line!=null&&TooltipsLogic.isKnockbackEnchantLine(line.getString())){
                    String clean=TooltipsLogic.stripColors(line.getString());
                    lines.set(i,net.minecraft.network.chat.Component.nullToEmpty(warnPrefix+"§c"+clean));
                }
            }
        }
        boolean isContainer=TooltipsLogic.isShulkerId(id)||TooltipsLogic.isBundleId(id);
        int shulkerTotalItems=0;
        boolean hasContainerItems=false;
        if(isContainer&&((tooltipsEnabled&&tt.shulkerPeek)||(hwTooltipsActive&&tt.hwAuctionPrice))){
            List<ItemStack> contents=new ArrayList<ItemStack>();
            boolean[] isBundle=new boolean[1];
            float[] fullness=new float[1];
            if(extractContainerStacks(stack,contents,isBundle,fullness)){
                for(int ci=0;ci<contents.size();ci++){
                    ItemStack cs=contents.get(ci);
                    if(cs!=null&&!cs.isEmpty()){
                        hasContainerItems=true;
                        shulkerTotalItems+=cs.getCount();
                    }
                }
            }
        }
        if(tooltipsEnabled&&tt.shulkerPeek&&tt.hideVanillaList&&TooltipsLogic.isShulkerId(id)&&hasContainerItems&&isContainerPeekTriggered(mc,tt)){
            for(int i=lines.size()-1;i>=1;i--){
                net.minecraft.network.chat.Component c=lines.get(i);
                if(c!=null&&TooltipsLogic.isVanillaShulkerContentLine(c.getString())){
                    lines.remove(i);
                }
            }
        }
        List<String> rawLines=new ArrayList<String>(lines.size());
        boolean hasExchangeMarker=false;
        for(int i=0;i<lines.size();i++){
            net.minecraft.network.chat.Component c=lines.get(i);
            String s=c==null?"":c.getString();
            rawLines.add(s);
            if(!hasExchangeMarker&&s.toLowerCase(Locale.ROOT).contains("за 1 коин"))hasExchangeMarker=true;
        }
        if(hwTooltipsActive){
            String meta=itemMetadata(stack);
            TooltipsLogic.HwItemInfo info=TooltipsLogic.classifyItem(id,stack.getHoverName().getString(),rawLines,meta);
            if(info.category==TooltipsLogic.Category.RAID&&info.raidRadius>0.5f){
                String radLine=dev.nightclient.i18n.I18n.format(lang,"tooltips.line.raid_radius",Collections.<String,Object>singletonMap("radius",Integer.valueOf(Math.round(info.raidRadius))));
                lines.add(net.minecraft.network.chat.Component.nullToEmpty(radLine));
            }
            if(tt.hwAuctionPrice&&inContainer){
                TooltipsLogic.AuctionPriceInfo priceInfo=TooltipsLogic.parseAuctionPrice(rawLines,stack.getCount(),shulkerTotalItems);
                if(priceInfo!=null){
                    String priceLine=TooltipsLogic.formatAuctionPriceLine(priceInfo,lang);
                    if(!priceLine.isEmpty())lines.add(net.minecraft.network.chat.Component.nullToEmpty(priceLine));
                }
            }
            if(tt.hwCoinExchange&&inContainer&&runtime.hwhelper()!=null&&(hasExchangeMarker||(screenTitle!=null&&screenTitle.contains("Биржа")))){
                TooltipsLogic.ExchangeOrderInfo exInfo=TooltipsLogic.parseExchangeOrder(rawLines,runtime.hwhelper().rate());
                if(exInfo!=null){
                    if(exInfo.rateLineIndex>=0&&exInfo.rateLineIndex<lines.size()){
                        String suffix=TooltipsLogic.formatExchangeDiffSuffix(exInfo,lang);
                        if(!suffix.isEmpty()){
                            lines.set(exInfo.rateLineIndex,lines.get(exInfo.rateLineIndex).copy().append(net.minecraft.network.chat.Component.nullToEmpty(suffix)));
                        }
                    }
                    String payoutLine=TooltipsLogic.formatExchangePayoutLine(exInfo,lang);
                    if(!payoutLine.isEmpty())lines.add(net.minecraft.network.chat.Component.nullToEmpty(payoutLine));
                }
            }
            if(tt.hwGoldenSpawners&&stack.getItem()==net.minecraft.world.item.Items.SPAWNER){
                net.minecraft.nbt.CompoundTag spTag=extractGoldenSpawnerTag(stack);
                if(spTag!=null){
                    String mobType=readBukkitKey(spTag,"gs_mob_type");
                    int killsTotal=(int)Math.min(Integer.MAX_VALUE,readBukkitLong(spTag,"gs_kills_total"));
                    int killsRem=(int)Math.min(Integer.MAX_VALUE,readBukkitLong(spTag,"gs_kills_remaining"));
                    int totalEggs=readBukkitInt(spTag,"gs_total_eggs");
                    String swordJson=readBukkitKey(spTag,"gs_sword_item");
                    TooltipsLogic.GoldenSpawnerInfo spInfo=TooltipsLogic.parseGoldenSpawner(mobType,killsTotal,killsRem,totalEggs,swordJson);
                    List<String> spLines=TooltipsLogic.formatGoldenSpawnerLines(spInfo,lang);
                    for(int si=0;si<spLines.size();si++)lines.add(net.minecraft.network.chat.Component.nullToEmpty(spLines.get(si)));
                }
            }
            if(tt.hwSpecialCompass&&stack.getItem()==net.minecraft.world.item.Items.COMPASS){
                net.minecraft.nbt.CompoundTag root=extractStackNbt(stack);
                net.minecraft.nbt.CompoundTag pbv=extractBukkitValues(stack);
                net.minecraft.nbt.CompoundTag radar=null;
                if(root!=null&&root.contains("region-radar"))radar=nbtCompound(root,"region-radar");
                else if(pbv!=null){
                    for(String k:nbtKeys(pbv)){
                        if(k.equals("region-radar")||k.endsWith(":region-radar")){radar=nbtCompound(pbv,k);break;}
                    }
                }
                if(radar!=null&&!radar.isEmpty()){
                    long delayMs=nbtLong(radar,"delay");
                    boolean hasTarget=radar.contains("target");
                    int tx=0,tz=0;Integer ty=null;
                    if(hasTarget){
                        net.minecraft.nbt.CompoundTag tgt=nbtCompound(radar,"target");
                        tx=nbtInt(tgt,"x");
                        tz=nbtInt(tgt,"z");
                        if(tgt.contains("y"))ty=Integer.valueOf(nbtInt(tgt,"y"));
                    }
                    List<String> compLines=TooltipsLogic.formatSpecialCompassLines(delayMs,hasTarget,tx,ty,tz,System.currentTimeMillis(),lang);
                    for(int ci=0;ci<compLines.size();ci++)lines.add(net.minecraft.network.chat.Component.nullToEmpty(compLines.get(ci)));
                }
            }
        }
        if(tooltipsEnabled&&tt.showDurability&&stack.isDamageableItem()&&stack.getMaxDamage()>0){
            boolean alreadyHasDur=false;
            for(int i=1;i<rawLines.size();i++){
                String lower=TooltipsLogic.stripColors(rawLines.get(i)).toLowerCase(Locale.ROOT);
                if(lower.startsWith("durability:")||lower.startsWith("прочность:")){alreadyHasDur=true;break;}
            }
            if(!alreadyHasDur){
                String durLine=TooltipsLogic.formatDurabilityLine(stack.getDamageValue(),stack.getMaxDamage(),lang);
                if(!durLine.isEmpty())lines.add(net.minecraft.network.chat.Component.nullToEmpty(durLine));
            }
        }
    }
    private static boolean isContainerPeekTriggered(Minecraft mc,Settings.Tooltips tt){
        if(tt==null||!tt.enabled||!tt.shulkerPeek)return false;
        if("shift".equals(tt.peekTrigger))return @@SHIFT_DOWN@@;
        return true;
    }
    public static void onContainerScreenInit(Object screenObj){
        if(screenObj==null||screenObj==lastCenteredContainerScreen)return;
        lastCenteredContainerScreen=screenObj;
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("tweaksy.cursorFix")||runtime==null||runtime.settings().tweaksy==null||!runtime.settings().tweaksy.enabled
            ||runtime.settings().tweaksy.cursorFix==null||!runtime.settings().tweaksy.cursorFix.enabled)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null||mc.mouseHandler==null)return;
        double cx=mc.getWindow().getScreenWidth()/2.0;
        double cy=mc.getWindow().getScreenHeight()/2.0;
        try{
            if(mc.mouseHandler instanceof dev.nightclient.platform.mixin.MouseHandlerAccessor){
                ((dev.nightclient.platform.mixin.MouseHandlerAccessor)mc.mouseHandler).nightclient$setXpos(cx);
                ((dev.nightclient.platform.mixin.MouseHandlerAccessor)mc.mouseHandler).nightclient$setYpos(cy);
            }
            @@CURSOR_FIX_CENTER_CALL@@
        }catch(Throwable ignored){}
    }
    public static boolean beginContainerTooltip(@@CONTEXT@@ graphics,Object screenObj,int mouseX,int mouseY){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("tooltips")||runtime==null||runtime.settings().tooltips==null||!runtime.settings().tooltips.enabled){
            hoveredTooltipStack=ItemStack.EMPTY;hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;return false;
        }
        Minecraft mc=Minecraft.getInstance();
        boolean ctrlDown=@@CTRL_DOWN@@;
        if(ctrlDown&&!lockedPeekStack.isEmpty()&&lastLockedPeekBoundsValid
            &&mouseX>=lastLockedPeekBounds[0]&&mouseX<=lastLockedPeekBounds[0]+lastLockedPeekBounds[2]
            &&mouseY>=lastLockedPeekBounds[1]&&mouseY<=lastLockedPeekBounds[1]+lastLockedPeekBounds[3]){
            renderContainerPeekOverlay(graphics,screenObj,mouseX,mouseY);
            return true;
        }
        if(!(screenObj instanceof ContainerFeatureAccessor)){
            hoveredTooltipStack=ItemStack.EMPTY;hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;return false;
        }
        Slot slot=((ContainerFeatureAccessor)screenObj).nightclient$hoveredSlot();
        if(slot==null||!slot.hasItem()){
            hoveredTooltipStack=ItemStack.EMPTY;hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;return false;
        }
        ItemStack stack=slot.getItem();
        setHoveredTooltipContext(stack);
        return false;
    }
    private static void setHoveredTooltipContext(ItemStack stack){
        if(stack==null||stack.isEmpty()){
            hoveredTooltipStack=ItemStack.EMPTY;hoveredHwInfo=TooltipsLogic.HwItemInfo.NONE;hoveredTooltipNanos=0L;return;
        }
        hoveredTooltipStack=stack.copy();
        hoveredTooltipNanos=System.nanoTime();
        String id=itemId(stack);
        String loreStr=readLore(stack);
        List<String> loreLines=loreStr.isEmpty()?Collections.<String>emptyList():Arrays.asList(loreStr.split("\\n"));
        hoveredHwInfo=TooltipsLogic.classifyItem(id,stack.getHoverName().getString(),loreLines,itemMetadata(stack));
    }
    public static void recordTooltipRect(int x,int y,int w,int h){
        lastTooltipRectX=x;lastTooltipRectY=y;lastTooltipRectW=w;lastTooltipRectH=h;
        lastTooltipRectNanos=System.nanoTime();
    }
    public static boolean renderCustomTooltipBackdrop(@@CONTEXT@@ graphics,int x,int y,int w,int h){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("tooltips")||runtime==null||graphics==null)return false;
        Settings.Tooltips tt=runtime.settings().tooltips;
        if(tt==null||!tt.enabled||"vanilla".equals(tt.style))return false;
        Minecraft mc=Minecraft.getInstance();
        int screenW=mc.getWindow().getGuiScaledWidth();
        int screenH=mc.getWindow().getGuiScaledHeight();
        boolean hw="hw".equals(tt.style);
        String lang=runtime.settings().language;
        int accent=0;
        String badge="";
        boolean freshContainerHover=(@@SCREEN@@ instanceof AbstractContainerScreen)&&(System.nanoTime()-hoveredTooltipNanos)<120_000_000L;
        if(hw&&tt.hwTooltips&&freshContainerHover&&hoveredHwInfo!=null&&hoveredHwInfo!=TooltipsLogic.HwItemInfo.NONE){
            if(tt.hwRarityBorder&&hoveredHwInfo.hasAccent())accent=hoveredHwInfo.accentColor;
            if(tt.showItemHeader)badge=hoveredHwInfo.badge(lang);
        }
        @@FLUSH_GUI@@
        NativeCanvas canvas=new NativeCanvas(graphics,screenW,screenH);
        try{runtime.renderTooltipBackdrop(canvas,x,y,w,h,accent,hw,badge);}
        finally{canvas.finish();}
        return true;
    }
    public static void renderContainerPeekOverlay(@@CONTEXT@@ graphics,Object screenObj,int mouseX,int mouseY){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("tooltips")||runtime==null||graphics==null)return;
        Settings.Tooltips tt=runtime.settings().tooltips;
        if(tt==null||!tt.enabled||!tt.shulkerPeek)return;
        Minecraft mc=Minecraft.getInstance();
        boolean ctrlDown=@@CTRL_DOWN@@;
        boolean altDown=@@ALT_DOWN@@;
        if(altDown&&!altTogglePrevDown){altFlippedPeekMode=!altFlippedPeekMode;}
        altTogglePrevDown=altDown;
        ItemStack target=ItemStack.EMPTY;
        boolean locked=false;
        float ax=mouseX+12f,ay=mouseY-12f,aw=120f,ah=36f;
        boolean recentRect=(System.nanoTime()-lastTooltipRectNanos)<60000000L&&lastTooltipRectW>0f;
        if(recentRect){ax=lastTooltipRectX;ay=lastTooltipRectY;aw=lastTooltipRectW;ah=lastTooltipRectH;}
        if(ctrlDown&&!lockedPeekStack.isEmpty()){
            target=lockedPeekStack;
            locked=true;
            ax=lockedAnchorX;ay=lockedAnchorY;aw=lockedAnchorW;ah=lockedAnchorH;
        }else{
            if(screenObj instanceof ContainerFeatureAccessor){
                Slot slot=((ContainerFeatureAccessor)screenObj).nightclient$hoveredSlot();
                if(slot!=null&&slot.hasItem())target=slot.getItem();
            }
            if(!target.isEmpty()&&ctrlDown&&isPeekableContainer(target)){
                lockedPeekStack=target.copy();
                lockedAnchorX=ax;lockedAnchorY=ay;lockedAnchorW=aw;lockedAnchorH=ah;
                locked=true;
            }else if(!ctrlDown){
                lockedPeekStack=ItemStack.EMPTY;
                lastLockedPeekBoundsValid=false;
            }
        }
        if(target.isEmpty()||!isPeekableContainer(target)){lastLockedPeekBoundsValid=false;return;}
        if(!locked&&!isContainerPeekTriggered(mc,tt)){lastLockedPeekBoundsValid=false;return;}
        List<ItemStack> rawStacks=new ArrayList<ItemStack>();
        boolean[] isBundle=new boolean[1];
        float[] fullness=new float[1];
        boolean extracted=extractContainerStacks(target,rawStacks,isBundle,fullness);
        String containerId=itemId(target);
        if(!extracted&&!TooltipsLogic.isShulkerId(containerId)&&!TooltipsLogic.isBundleId(containerId))return;
        boolean bundle=isBundle[0]||TooltipsLogic.isBundleId(containerId);
        int maxSlots=bundle?Math.max(1,rawStacks.size()):Math.max(27,rawStacks.size());
        if(!bundle&&maxSlots>27)maxSlots=27;
        Arrays.fill(tooltipItemCache,null);
        tooltipItemCache[50]=target.copy();
        List<TooltipsLogic.PeekEntry> rawEntries=new ArrayList<TooltipsLogic.PeekEntry>();
        int usedSlots=0;
        for(int i=0;i<rawStacks.size()&&i<48;i++){
            ItemStack s=rawStacks.get(i);
            if(s==null||s.isEmpty())continue;
            usedSlots++;
            tooltipItemCache[i]=s.copy();
            rawEntries.add(new TooltipsLogic.PeekEntry(i,300+i,itemId(s),s.getHoverName().getString(),s.getCount(),s.getDamageValue(),s.getMaxDamage(),s.isEnchanted()));
        }
        boolean defaultCompact="compact".equals(tt.peekMode);
        boolean compact=bundle|| (altFlippedPeekMode?!defaultCompact:defaultCompact);
        List<TooltipsLogic.PeekEntry> displayList=compact?TooltipsLogic.compactEntries(rawEntries):rawEntries;
        List<TooltipOverlayRenderer.SlotEntry> renderEntries=new ArrayList<TooltipOverlayRenderer.SlotEntry>(displayList.size());
        for(int i=0;i<displayList.size();i++){
            TooltipsLogic.PeekEntry e=displayList.get(i);
            InventorySlot invSlot=new InventorySlot(e.cacheSlot,e.itemId,e.displayName,e.count,e.damage,e.maxDamage,e.enchanted);
            renderEntries.add(new TooltipOverlayRenderer.SlotEntry(e.slotIndex,invSlot,TooltipsLogic.formatItemCount(e.count)));
        }
        int accent=0;
        if(tt.colorByShulker&&TooltipsLogic.isShulkerId(containerId)){
            accent=TooltipsLogic.shulkerDyeColor(containerId);
        }else if(bundle){
            accent=0xffd49a6a;
        }
        TooltipOverlayRenderer.ContainerPeek peek=new TooltipOverlayRenderer.ContainerPeek(
            TooltipsLogic.stripColors(target.getHoverName().getString()),containerId,350,accent,bundle,compact,locked,usedSlots,maxSlots,fullness[0],renderEntries);
        int screenW=mc.getWindow().getGuiScaledWidth();
        int screenH=mc.getWindow().getGuiScaledHeight();
        @@FLUSH_GUI@@
        int hoveredCacheSlot=-1;
        NativeCanvas canvas=new NativeCanvas(graphics,screenW,screenH);
        try{
            hoveredCacheSlot=runtime.renderContainerPeek(
                canvas,peek,ax,ay,aw,ah,screenW,screenH,mouseX,mouseY,lastLockedPeekBounds);
            lastLockedPeekBoundsValid=locked;
        }finally{canvas.finish();}
        if(locked&&hoveredCacheSlot>=300&&hoveredCacheSlot<348){
            ItemStack inner=tooltipItemCache[hoveredCacheSlot-300];
            if(inner!=null&&!inner.isEmpty()){
                setHoveredTooltipContext(inner);
                @@RENDER_STACK_TOOLTIP@@
            }
        }
    }
    private static boolean isPeekableContainer(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        String id=itemId(stack);
        if(TooltipsLogic.isShulkerId(id)||TooltipsLogic.isBundleId(id))return true;
        return id.endsWith("_chest")||id.equals("minecraft:chest")||id.equals("minecraft:barrel")||id.equals("minecraft:dispenser")||id.equals("minecraft:dropper")||id.equals("minecraft:hopper");
    }
    private static boolean extractContainerStacks(ItemStack stack,List<ItemStack> out,boolean[] isBundleOut,float[] fullnessOut){
        if(stack==null||stack.isEmpty())return false;
        try{
            @@EXTRACT_CONTAINER_ITEMS@@
        }catch(Throwable ignored){}
        return false;
    }
    private static void formatScreenTitle(Object screenObj,ClientRuntime runtime){
        if(!(screenObj instanceof net.minecraft.client.gui.screens.Screen))return;
        net.minecraft.client.gui.screens.Screen screen=(net.minecraft.client.gui.screens.Screen)screenObj;
        String titleStr=screen.getTitle().getString();
        if(titleStr!=null&&titleStr.contains("Аукцион")&&!titleStr.contains("1 |❘|")){
            String formatted=runtime.hwhelper().formatContainerTitle(titleStr);
            try{
                for(java.lang.reflect.Field f:net.minecraft.client.gui.screens.Screen.class.getDeclaredFields()){
                    if(net.minecraft.network.chat.Component.class.isAssignableFrom(f.getType())){
                        f.setAccessible(true);
                        Object val=f.get(screen);
                        if(val==screen.getTitle()){
                            f.set(screen,screen.getTitle().copy().append(net.minecraft.network.chat.Component.nullToEmpty(formatted.substring(titleStr.length()))));
                            break;
                        }
                    }
                }
            }catch(Throwable ignored){}
        }
    }
    public static boolean onBetterChatClear(boolean clearSentMsgHistory){
        ClientRuntime runtime=ClientRuntime.get();
        if(!FeaturePolicy.isBlocked("betterChat")&&runtime!=null&&runtime.settings().betterChat!=null&&runtime.settings().betterChat.enabled&&runtime.settings().betterChat.keepHistory&&clearSentMsgHistory){
            return true;
        }
        if(!clearSentMsgHistory){
            chatDedup.clear();
            chatSlideOffset=0f;chatBoxH=0f;chatBoxW=0f;chatBoxAlpha=0f;chatCardActive=false;chatLastOriginValid=false;
        }
        return false;
    }
    public static int betterChatHistoryLimit(int original){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled)return original;
        return Math.max(original,runtime.settings().betterChat.historyLimit);
    }
    public static boolean betterChatHideSignatureBar(){
        ClientRuntime runtime=ClientRuntime.get();
        return !FeaturePolicy.isBlocked("betterChat")&&runtime!=null&&runtime.settings().betterChat!=null&&runtime.settings().betterChat.enabled&&runtime.settings().betterChat.hideSignatureBar;
    }
    private static float betterChatVanillaTextX(double vanillaScale){
        return @@BETTER_CHAT_VANILLA_TEXT_X@@;
    }
    private static void enableBetterChatGlScissor(Minecraft mc,int x1,int y1,int x2,int y2){
        chatPrevScissor=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX,chatPrevScissorBox);
        double gs=mc.getWindow().getGuiScale();
        int fbH=mc.getWindow().getHeight();
        int sx=Math.max(0,(int)Math.floor(x1*gs));
        int sw=Math.max(0,(int)Math.ceil((x2-x1)*gs));
        int sh=Math.max(0,(int)Math.ceil((y2-y1)*gs));
        int sy=Math.max(0,fbH-(int)Math.ceil(y2*gs));
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glScissor(sx,sy,sw,sh);
    }
    private static void disableBetterChatGlScissor(){
        org.lwjgl.opengl.GL11.glScissor(chatPrevScissorBox[0],chatPrevScissorBox[1],chatPrevScissorBox[2],chatPrevScissorBox[3]);
        if(chatPrevScissor)org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        else org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
    }
    private static int measureLineWidth(net.minecraft.client.gui.Font font,Object entry){
        if(font==null||entry==null)return 0;
        if(entry instanceof net.minecraft.util.FormattedCharSequence)return font.width((net.minecraft.util.FormattedCharSequence)entry);
        if(entry instanceof net.minecraft.network.chat.Component)return font.width((net.minecraft.network.chat.Component)entry);
        if(entry instanceof String)return font.width((String)entry);
        @@MEASURE_GUI_MESSAGE_WIDTH@@
        return font.width(extractMessageText(entry));
    }
    private static double[] unmapBetterChatPointer(Object chatObj,double mouseX,double mouseY){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled)return new double[]{mouseX,mouseY};
        Minecraft mc=Minecraft.getInstance();
        int screenW=mc.getWindow().getGuiScaledWidth();
        int screenH=mc.getWindow().getGuiScaledHeight();
        double vanillaScale=(chatObj instanceof dev.nightclient.platform.mixin.ChatLinesAccessor)
            ?((dev.nightclient.platform.mixin.ChatLinesAccessor)chatObj).nightclient$chatScale():1.0;
        float vanillaTextX=betterChatVanillaTextX(vanillaScale);
        float vanillaBottomY=screenH-40f;
        float ox,oy,hs;
        if(chatLastOriginValid&&chatTextOriginBottomY>0f){
            ox=chatTextOriginX;oy=chatTextOriginBottomY;hs=chatHudScale;
        }else{
            Settings.BetterChat cfg=runtime.settings().betterChat;
            float[] anchor=BetterChatLogic.computeHudAnchor(cfg.anchorX,cfg.anchorY,cfg.offsetX,cfg.offsetY,cfg.scale,screenW,screenH);
            boolean drawCard="rounded".equals(cfg.backgroundStyle)||"compact".equals(cfg.backgroundStyle);
            hs=anchor[2];
            ox=Math.max(2f,anchor[0])+(drawCard?6f*hs:0f);
            oy=Math.max(18f,Math.min(screenH-2f,anchor[1]))-(drawCard?4f*hs:0f);
        }
        return BetterChatLogic.screenToVanillaChat(mouseX,mouseY,ox,oy,hs,vanillaTextX,vanillaBottomY);
    }
    public static double betterChatUnmapMouseX(Object chatObj,double mouseX){
        return unmapBetterChatPointer(chatObj,mouseX,0.0)[0];
    }
    public static double betterChatUnmapMouseY(Object chatObj,double mouseY){
        return unmapBetterChatPointer(chatObj,0.0,mouseY)[1];
    }
    @@BETTER_CHAT_CLICK_MATRIX_METHOD@@
    public static boolean betterChatNeedsBackdrop(){
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled)return false;
        Settings.BetterChat cfg=runtime.settings().betterChat;
        return chatCardActive&&cfg.blur&&cfg.backgroundOpacity>0.02f&&("rounded".equals(cfg.backgroundStyle)||"compact".equals(cfg.backgroundStyle));
    }
    public static BetterChatLogic.ChatToastStatus chatToast(){
        if(chatToastNanos==0L||chatToastText.isEmpty())return null;
        float age=(float)((System.nanoTime()-chatToastNanos)*1e-9);
        if(age>=2.2f){chatToastText="";chatToastNanos=0L;return null;}
        return new BetterChatLogic.ChatToastStatus(chatToastText,age);
    }
    public static net.minecraft.network.chat.Component prepareBetterChatMessage(Object chatObj,net.minecraft.network.chat.Component message){
        if(message==null)return null;
        String rawPlain=message.getString();
        onChatCombatText(rawPlain);
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime!=null){
            runtime.onIncomingChatLine(rawPlain);
            handleTreasureCompassChat(Minecraft.getInstance(),runtime,rawPlain);
            if(runtime.hwhelper()!=null){
                runtime.hwhelper().explosiveThingCooldown().onServerCooldownMessage(rawPlain,System.currentTimeMillis());
            }
        }
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled)return message;
        Settings.BetterChat cfg=runtime.settings().betterChat;
        String norm=BetterChatLogic.normalizeMessage(rawPlain);
        int stackCount=1;
        if(cfg.stackDuplicates&&!norm.isEmpty()&&chatObj instanceof dev.nightclient.platform.mixin.ChatLinesAccessor){
            dev.nightclient.platform.mixin.ChatLinesAccessor access=(dev.nightclient.platform.mixin.ChatLinesAccessor)chatObj;
            BetterChatLogic.StackMatch match=chatDedup.record(rawPlain,null);
            if(match.count>1){
                List<?> all=access.nightclient$allMessages();
                if(all!=null&&!all.isEmpty()){
                    int limit=Math.min(all.size(),14);
                    for(int i=0;i<limit;i++){
                        String existing=BetterChatLogic.normalizeMessage(extractMessageText(all.get(i)));
                        if(existing.equals(norm)){
                            all.remove(i);
                            access.nightclient$rescaleChat();
                            stackCount=match.count;
                            break;
                        }
                    }
                }
            }
        }
        if(cfg.animation&&chatObj instanceof dev.nightclient.platform.mixin.ChatLinesAccessor){
            dev.nightclient.platform.mixin.ChatLinesAccessor access=(dev.nightclient.platform.mixin.ChatLinesAccessor)chatObj;
            float hs=Math.max(0.5f,Math.min(2.0f,cfg.scale));
            float lineStep=Math.max(8f,(float)(@@CHAT_LINE_HEIGHT@@*Math.max(0.25,access.nightclient$chatScale()))*hs);
            chatSlideOffset=Math.min(lineStep*3.5f,chatSlideOffset+lineStep);
        }
        if(!cfg.timestamps&&stackCount<=1)return message;
        net.minecraft.network.chat.MutableComponent out=net.minecraft.network.chat.Component.nullToEmpty("").copy();
        if(cfg.timestamps){
            String ts=BetterChatLogic.formatTimestamp(System.currentTimeMillis(),cfg.timestampSeconds);
            String inner=ts.length()>=4?ts.substring(1,ts.length()-2):ts.trim();
            out.append(net.minecraft.network.chat.Component.nullToEmpty("§8[§7"+inner+"§8] "));
        }
        out.append(message.copy());
        if(stackCount>1){
            out.append(net.minecraft.network.chat.Component.nullToEmpty(" §8[§7x"+stackCount+"§8]"));
        }
        return out;
    }
    private static int lineAddedTime(Object entry){
        if(entry==null)return -1;
        @@LINE_ADDED_TIME_BODY@@
    }
    private static boolean isLineEndOfEntry(Object entry){
        if(entry==null)return true;
        @@LINE_END_OF_ENTRY_BODY@@
    }
    public static boolean beginBetterChatRender(@@CONTEXT@@ graphics,Object chatObj,int tickCount){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled||graphics==null){
            chatCardActive=false;chatSlideOffset=0f;chatTransformActive=false;chatClipActive=false;
            return false;
        }
        Settings.BetterChat cfg=runtime.settings().betterChat;
        Minecraft mc=Minecraft.getInstance();
        int screenW=mc.getWindow().getGuiScaledWidth();
        int screenH=mc.getWindow().getGuiScaledHeight();
        long now=System.nanoTime();
        float dt=chatLastRenderNanos==0L?0.016f:(float)Math.max(0.0,Math.min(0.1,(now-chatLastRenderNanos)*1e-9));
        chatLastRenderNanos=now;
        chatSlideOffset=cfg.animation?BetterChatLogic.advanceSlideOffset(chatSlideOffset,dt,cfg.animationSpeed):0f;
        boolean drawCard="rounded".equals(cfg.backgroundStyle)||"compact".equals(cfg.backgroundStyle);
        float[] anchor=BetterChatLogic.computeHudAnchor(cfg.anchorX,cfg.anchorY,cfg.offsetX,cfg.offsetY,cfg.scale,screenW,screenH);
        float hudScale=anchor[2];
        double vanillaScale=1.0;
        int lineH=9;
        int chatWidth=320;
        int visibleCount=0;
        float maxAlpha=0f;
        int maxTextW=0;
        boolean compact="compact".equals(cfg.backgroundStyle);
        if(chatObj instanceof dev.nightclient.platform.mixin.ChatLinesAccessor){
            dev.nightclient.platform.mixin.ChatLinesAccessor access=(dev.nightclient.platform.mixin.ChatLinesAccessor)chatObj;
            List<?> lines=access.nightclient$visibleLines();
            vanillaScale=access.nightclient$chatScale();
            lineH=@@CHAT_LINE_HEIGHT@@;
            chatWidth=access.nightclient$chatWidth();
            int maxLines=access.nightclient$linesPerPage();
            int scroll=Math.max(0,access.nightclient$scrollPosition());
            boolean focused=access.nightclient$chatFocused();
            if(lines!=null&&!lines.isEmpty()&&vanillaScale>0&&maxLines>0){
                int count=Math.min(lines.size()-scroll,maxLines);
                for(int i=0;i<count;i++){
                    Object entry=lines.get(i+scroll);
                    float lineAlpha=1f;
                    if(!focused){
                        int added=lineAddedTime(entry);
                        if(added>=0){
                            int age=tickCount-added;
                            if(age>=200)continue;
                            double t=Math.max(0.0,Math.min(1.0,(1.0-age/200.0)*10.0));
                            lineAlpha=(float)(t*t);
                        }
                    }
                    if(lineAlpha<=0.01f)continue;
                    visibleCount++;
                    if(lineAlpha>maxAlpha)maxAlpha=lineAlpha;
                    if(drawCard&&mc.font!=null){
                        int w=measureLineWidth(mc.font,entry);
                        if(w>maxTextW)maxTextW=w;
                    }
                }
            }
        }
        float[] card=BetterChatLogic.computeCardBounds(
            anchor[0],anchor[1],hudScale,vanillaScale,chatWidth,maxTextW,visibleCount,lineH,compact,drawCard,screenW,screenH);
        chatHudScale=hudScale;
        chatTextOriginX=card[4];
        chatTextOriginBottomY=card[5];
        chatClipActive=false;
        if(drawCard){
            if(visibleCount>0&&maxAlpha>0.02f){
                float rate=14f*Math.max(0.5f,cfg.animationSpeed);
                float targetW=card[2],targetH=card[3];
                chatBoxH=targetH>chatBoxH?targetH:BetterChatLogic.smoothApproach(chatBoxH,targetH,dt,rate);
                chatBoxW=targetW>chatBoxW?targetW:BetterChatLogic.smoothApproach(chatBoxW,targetW,dt,rate);
                chatBoxAlpha=BetterChatLogic.smoothApproach(chatBoxAlpha,maxAlpha,dt,16f);
                float maxScreenW=Math.max(48f,screenW-4f);
                float clampedBottomY=Math.max(18f,Math.min(screenH-2f,anchor[1]));
                float renderW=Math.min(Math.max(chatBoxW,targetW),maxScreenW);
                float renderH=Math.min(Math.max(chatBoxH,targetH),Math.max(16f,clampedBottomY-2f));
                float renderBx=Math.max(2f,Math.min(anchor[0],screenW-renderW-2f));
                float renderBy=Math.max(2f,clampedBottomY-renderH);
                chatTextOriginX=renderBx+6f*hudScale;
                chatTextOriginBottomY=clampedBottomY-4f*hudScale;
                chatCardActive=cfg.blur&&cfg.backgroundOpacity>0.02f&&chatBoxAlpha>0.03f;
                @@FLUSH_GUI@@
                NativeCanvas canvas=new NativeCanvas(graphics,screenW,screenH);
                try{runtime.renderBetterChatBackdrop(canvas,renderBx,renderBy,renderW,renderH,chatBoxAlpha);}
                finally{canvas.finish();}
                chatClipX1=(int)Math.floor(renderBx);
                chatClipY1=(int)Math.floor(renderBy);
                chatClipX2=(int)Math.ceil(renderBx+renderW);
                chatClipY2=(int)Math.ceil(renderBy+renderH);
                chatClipActive=(chatClipX2>chatClipX1+2&&chatClipY2>chatClipY1+2);
            }else{
                chatBoxAlpha=BetterChatLogic.smoothApproach(chatBoxAlpha,0f,dt,16f);
                if(chatBoxAlpha<=0.02f){chatBoxH=0f;chatBoxW=0f;chatBoxAlpha=0f;chatCardActive=false;}
            }
        }else{
            chatCardActive=false;chatBoxH=0f;chatBoxW=0f;chatBoxAlpha=0f;
        }
        chatTransformActive=true;
        chatLastOriginValid=true;
        float vanillaTextX=betterChatVanillaTextX(vanillaScale);
        float vanillaBottomY=screenH-40f;
        @@BETTER_CHAT_PUSH_TRANSFORM@@
        return true;
    }
    public static void endBetterChatRender(@@CONTEXT@@ graphics){
        if(!chatTransformActive||graphics==null)return;
        @@BETTER_CHAT_POP_TRANSFORM@@
        chatTransformActive=false;
        chatClipActive=false;
    }
    public static int modifyBetterChatFill(int x1,int y1,int x2,int y2,int color){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled)return color;
        Settings.BetterChat cfg=runtime.settings().betterChat;
        int w=Math.abs(x2-x1);
        if(w<=4){
            if(x1<0&&cfg.hideSignatureBar)return 0;
            return color;
        }
        if("transparent".equals(cfg.backgroundStyle)||"rounded".equals(cfg.backgroundStyle)||"compact".equals(cfg.backgroundStyle))return 0;
        int alpha=(color>>>24)&255;
        if(alpha==0)return 0;
        int scaled=Math.max(0,Math.min(255,Math.round(alpha*(cfg.backgroundOpacity/0.5f))));
        return (scaled<<24)|(color&0x00ffffff);
    }
    public static boolean handleChatCopyClick(Object screen,double mouseX,double mouseY,int button){
        Minecraft mc=Minecraft.getInstance();
        boolean trigger=button==1||(button==0&&@@SHIFT_DOWN@@);
        if(!trigger)return false;
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled||!runtime.settings().betterChat.copyOnClick)return false;
        if(mc.gui==null||mc.player==null)return false;
        Object chat=@@CHAT_COMPONENT@@;
        if(!(chat instanceof dev.nightclient.platform.mixin.ChatLinesAccessor))return false;
        dev.nightclient.platform.mixin.ChatLinesAccessor access=(dev.nightclient.platform.mixin.ChatLinesAccessor)chat;
        double scale=access.nightclient$chatScale();
        double[] mapped=unmapBetterChatPointer(chat,mouseX,mouseY);
        double vx=mapped[0],vy=mapped[1];
        float vanillaTextX=betterChatVanillaTextX(scale);
        double maxSpan=Math.ceil(access.nightclient$chatWidth()/Math.max(0.01,scale))*scale+16.0;
        if(scale<=0||vx<vanillaTextX-8||vx>vanillaTextX+maxSpan)return false;
        List<?> messages=access.nightclient$visibleLines();
        if(messages==null||messages.isEmpty())return false;
        int index=ChatCoordinateParser.clickedLine(vy,mc.getWindow().getGuiScaledHeight(),scale,
            @@CHAT_LINE_HEIGHT@@,access.nightclient$linesPerPage(),access.nightclient$scrollPosition(),messages.size());
        if(index<0||index>=messages.size())return false;
        int top=index;
        while(top+1<messages.size()&&!isLineEndOfEntry(messages.get(top+1)))top++;
        int bottom=index;
        while(bottom>0&&!isLineEndOfEntry(messages.get(bottom)))bottom--;
        StringBuilder full=new StringBuilder();
        for(int i=top;i>=bottom;i--){
            String part=extractMessageText(messages.get(i));
            if(part!=null&&!part.isEmpty()){
                if(full.length()>0&&full.charAt(full.length()-1)!=' '&&part.charAt(0)!=' ')full.append(' ');
                full.append(part);
            }
        }
        String clean=BetterChatLogic.cleanForCopy(full.toString());
        if(clean.isEmpty())return false;
        mc.keyboardHandler.setClipboard(clean);
        chatToastText=dev.nightclient.i18n.I18n.text(runtime.settings().language,"betterChat.copied_toast");
        chatToastNanos=System.nanoTime();
        return true;
    }
    public static void onChatScreenInit(Object screen){
        chatJustSubmitted=false;
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled||!runtime.settings().betterChat.keepDraft)return;
        if(savedChatDraft==null||savedChatDraft.isEmpty())return;
        if(!(screen instanceof dev.nightclient.platform.mixin.ChatScreenAccessor))return;
        net.minecraft.client.gui.components.EditBox input=((dev.nightclient.platform.mixin.ChatScreenAccessor)screen).nightclient$input();
        if(input!=null&&input.getValue().isEmpty()){
            input.setValue(savedChatDraft);
        }
    }
    public static void onChatScreenRemoved(Object screen){
        if(chatJustSubmitted){chatJustSubmitted=false;savedChatDraft="";return;}
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled||!runtime.settings().betterChat.keepDraft){
            savedChatDraft="";return;
        }
        if(!(screen instanceof dev.nightclient.platform.mixin.ChatScreenAccessor))return;
        if(FeatureBridge.maskingChatPassword()){savedChatDraft="";return;}
        net.minecraft.client.gui.components.EditBox input=((dev.nightclient.platform.mixin.ChatScreenAccessor)screen).nightclient$input();
        if(input!=null){
            String val=input.getValue();
            savedChatDraft=val==null?"":val;
        }
    }
    public static void clearChatDraft(){
        savedChatDraft="";
        chatJustSubmitted=true;
    }
    public static boolean handleChatCoordinateClick(Object screen,double mouseX,double mouseY,int button){
        if(button!=0)return false;
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("waypoints")||runtime==null||runtime.settings().waypoints==null||!runtime.settings().waypoints.chatCoordinates)return false;
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui==null||mc.player==null)return false;
        Object chat=@@CHAT_COMPONENT@@;
        if(chat==null)return false;
        dev.nightclient.platform.mixin.ChatLinesAccessor access=(dev.nightclient.platform.mixin.ChatLinesAccessor)chat;
        double scale=access.nightclient$chatScale();
        double[] mapped=unmapBetterChatPointer(chat,mouseX,mouseY);
        double vx=mapped[0],vy=mapped[1];
        float vanillaTextX=betterChatVanillaTextX(scale);
        if(scale<=0 || (vx-vanillaTextX)/scale<0)return false;
        List<?> messages=access.nightclient$visibleLines();
        if(messages==null)return false;
        int index=ChatCoordinateParser.clickedLine(vy,mc.getWindow().getGuiScaledHeight(),scale,
            @@CHAT_LINE_HEIGHT@@,access.nightclient$linesPerPage(),access.nightclient$scrollPosition(),messages.size());
        if(index<0)return false;
        String text=extractMessageText(messages.get(index));
        if(text==null||text.isEmpty())return false;
        double localX=(vx-vanillaTextX)/scale;
        int character=0;
        while(character<text.length() && mc.font.width(text.substring(0,character+1))<=localX)character++;
        if(character>=text.length())return false;
        double[] coords=ChatCoordinateParser.parseAt(text,mc.player.getY(),character);
        if(coords==null)return false;
        GameBridge.openWaypointCreateAt(coords[0],coords[1],coords[2]);
        return true;
    }
    private static String extractFormattedSeq(net.minecraft.util.FormattedCharSequence seq){
        if(seq==null)return "";
        final StringBuilder sb=new StringBuilder();
        seq.accept((idx,st,cp)->{sb.appendCodePoint(cp);return true;});
        return sb.toString();
    }
    private static String extractMessageText(Object entry){
        if(entry==null)return "";
        if(entry instanceof net.minecraft.network.chat.Component)return ((net.minecraft.network.chat.Component)entry).getString();
        if(entry instanceof String)return (String)entry;
        if(entry instanceof net.minecraft.util.FormattedCharSequence)return extractFormattedSeq((net.minecraft.util.FormattedCharSequence)entry);
        @@EXTRACT_GUI_MESSAGE_TEXT@@
        return "";
    }
    public static long glintMillis(){
        long now=System.currentTimeMillis();
        ClientRuntime runtime=ClientRuntime.get();
        Settings.EnchantGlint cfg=runtime==null?null:runtime.settings().enchantGlint;
        if(FeaturePolicy.isBlocked("enchantGlint")||cfg==null||!cfg.enabled){
            glintLastRealMs=now;
            glintAccumMillis=now;
            return now;
        }
        if(glintLastRealMs==0L){
            glintLastRealMs=now;
            glintAccumMillis=now;
        }else{
            long dt=Math.max(0L,Math.min(250L,now-glintLastRealMs));
            glintLastRealMs=now;
            glintAccumMillis+=dt*(double)Math.max(0.1f,cfg.speed);
        }
        return (long)glintAccumMillis;
    }
    private static void syncEnchantGlint(Minecraft mc,ClientRuntime runtime){
        if(mc==null||mc.getTextureManager()==null||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
        Settings.EnchantGlint cfg=runtime==null?null:runtime.settings().enchantGlint;
        String[] paths=new String[]{@@GLINT_TEXTURE_PATHS@@};
        if(FeaturePolicy.isBlocked("enchantGlint")||cfg==null||!cfg.enabled){
            if(glintActiveOverride){
                for(String p:paths){
                    try{mc.getTextureManager().release(parseWorldId(p));}catch(Throwable ignored){}
                }
                glintTextures.clear();
                glintActiveOverride=false;
                glintLastIntensity=-1f;
            }
            return;
        }
        Object rm=mc.getResourceManager();
        if(rm!=glintLastResourceManager){
            glintBasePixels.clear();
            glintBaseDims.clear();
            glintTextures.clear();
            glintLastResourceManager=rm;
            glintLastIntensity=-1f;
        }
        int themeAccent=runtime.settings().theme!=null?runtime.settings().theme.accent:0xff7c5cff;
        int targetColor=ExplosionRingLogic.resolveGlintColor(cfg,themeAccent,ClientRuntime.nowSeconds());
        float intensity=Math.max(0f,Math.min(1.5f,cfg.intensity));
        boolean needsCheck=false;
        for(String p:paths){
            net.minecraft.client.renderer.texture.DynamicTexture dt=glintTextures.get(p);
            if(dt==null||mc.getTextureManager().getTexture(parseWorldId(p))!=dt){needsCheck=true;break;}
        }
        if(!needsCheck&&targetColor==glintLastColor&&Math.abs(intensity-glintLastIntensity)<0.005f)return;
        glintLastColor=targetColor;
        glintLastIntensity=intensity;
        glintLastUploadMs=System.currentTimeMillis();
        float cr=((targetColor>>>16)&255)/255f;
        float cg=((targetColor>>>8)&255)/255f;
        float cb=(targetColor&255)/255f;
        for(String path:paths){
            try{
                @@COSMETICS_IDENTIFIER@@ id=parseWorldId(path);
                int[] base=glintBasePixels.get(path);
                int[] dims=glintBaseDims.get(path);
                if(base==null||dims==null){
                    try(java.io.InputStream in=@@OPEN_RESOURCE_STREAM@@){
                        com.mojang.blaze3d.platform.NativeImage src=com.mojang.blaze3d.platform.NativeImage.read(in);
                        try{
                            int w=src.getWidth(),h=src.getHeight();
                            base=new int[w*h];
                            dims=new int[]{w,h};
                            for(int y=0;y<h;y++){
                                int row=y*w;
                                for(int x=0;x<w;x++){
                                    base[row+x]=@@NATIVE_GET_ARGB@@;
                                }
                            }
                            glintBasePixels.put(path,base);
                            glintBaseDims.put(path,dims);
                        }finally{src.close();}
                    }
                }
                int w=dims[0],h=dims[1];
                boolean hide=intensity<=0.01f;
                net.minecraft.client.renderer.texture.DynamicTexture cachedDyn=glintTextures.get(path);
                net.minecraft.client.renderer.texture.AbstractTexture existing=mc.getTextureManager().getTexture(id);
                if(cachedDyn!=null&&existing==cachedDyn){
                    com.mojang.blaze3d.platform.NativeImage image=cachedDyn.getPixels();
                    if(image!=null&&image.getWidth()==w&&image.getHeight()==h){
                        fillGlintImage(image,base,w,h,hide,cr,cg,cb,intensity);
                        cachedDyn.upload();
                        glintActiveOverride=true;
                        continue;
                    }
                }
                com.mojang.blaze3d.platform.NativeImage image=new com.mojang.blaze3d.platform.NativeImage(w,h,false);
                boolean owned=false;
                try{
                    fillGlintImage(image,base,w,h,hide,cr,cg,cb,intensity);
                    net.minecraft.client.renderer.texture.DynamicTexture dyn=@@GLINT_MAKE_TEXTURE@@;
                    owned=true;
                    dyn.upload();
                    mc.getTextureManager().register(id,dyn);
                    glintTextures.put(path,dyn);
                    glintActiveOverride=true;
                }finally{
                    if(!owned)image.close();
                }
            }catch(Throwable ignored){}
        }
    }
    private static void fillGlintImage(com.mojang.blaze3d.platform.NativeImage image,int[] base,int w,int h,boolean hide,float cr,float cg,float cb,float intensity){
        for(int y=0;y<h;y++){
            int row=y*w;
            for(int x=0;x<w;x++){
                int pixel;
                if(hide){
                    pixel=0x00000000;
                }else{
                    int orig=base[row+x];
                    int oa=(orig>>>24)&255;
                    int or=(orig>>>16)&255;
                    int og=(orig>>>8)&255;
                    int ob=orig&255;
                    float lum=Math.max(or,Math.max(og,ob))/255f;
                    float boost=lum*intensity;
                    int nr=Math.max(0,Math.min(255,Math.round(cr*boost*255f)));
                    int ng=Math.max(0,Math.min(255,Math.round(cg*boost*255f)));
                    int nb=Math.max(0,Math.min(255,Math.round(cb*boost*255f)));
                    int na=Math.max(0,Math.min(255,Math.round(oa*Math.min(1f,intensity))));
                    pixel=(na<<24)|(nr<<16)|(ng<<8)|nb;
                }
                @@COSMETICS_SET_PIXEL@@
            }
        }
    }
    private static @@COSMETICS_IDENTIFIER@@ parseWorldId(String id){
        return @@COSMETICS_IDENTIFIER_PARSE@@;
    }
    private static void trackHeldTntItem(Minecraft mc,ClientRuntime runtime){
        if(mc==null||mc.player==null||runtime==null)return;
        Settings.ExplosionRing cfg=runtime.settings().explosionRing;
        if(FeaturePolicy.isBlocked("explosionRing")||cfg==null||!cfg.enabled)return;
        runtime.explosionLearner().syncScope(currentWorldId());
        ItemStack main=mc.player.getMainHandItem();
        ItemStack off=mc.player.getOffhandItem();
        ItemStack tntStack=isTntStack(main)?main:isTntStack(off)?off:ItemStack.EMPTY;
        if(!tntStack.isEmpty()){
            String name=tntStack.getHoverName().getString();
            String lore=readLore(tntStack);
            ExplosionRingLogic.TntSpec spec=ExplosionRingLogic.inferFromText(name,lore);
            if(spec.custom){
                recentHeldTntSpec=spec;
                recentHeldTntMs=System.currentTimeMillis();
            }
        }
    }
    private static boolean isTntStack(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        String id=itemId(stack);
        if(id.contains("tnt"))return true;
        String name=ExplosionRingLogic.stripColors(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
        return name.contains("c4")||name.contains("с4")||name.contains("взрывная волна")||name.contains("динамит");
    }
    private static void notifyExplosiveThingExplosion(ClientRuntime runtime,double ex,double ey,double ez,long nowMs){
        if(runtime==null||runtime.hwhelper()==null)return;
        HwPvpAndEventsLogic.ExplosiveThingCooldownTracker tracker=runtime.hwhelper().explosiveThingCooldown();
        if(!tracker.hasPending())return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.player==null)return;
        tracker.onNearbyExplosion(ex,ey,ez,mc.player.getX(),mc.player.getY(),mc.player.getZ(),nowMs);
    }
    public static void onExplosionPacket(net.minecraft.network.protocol.game.ClientboundExplodePacket packet){
        if(packet==null)return;
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null)return;
        boolean learnExp=runtime.settings().explosionRing!=null&&runtime.settings().explosionRing.autoLearnRadius;
        boolean trackHwExp=runtime.hwhelper()!=null&&runtime.hwhelper().explosiveThingCooldown().hasPending();
        if(!learnExp&&!trackHwExp)return;
        try{
            @@EXPLODE_PACKET_BODY@@
        }catch(Throwable ignored){}
    }
    public static List<ExplosionRingLogic.HazardBadge> explosionBadges(float delta){
        return latestExplosionBadges;
    }
    private static int playerBlastEpf(Player player){
        if(player==null)return 0;
        int epf=0;
        try{
            for(int i=0;i<ARMOR.length;i++){
                ItemStack stack=player.getItemBySlot(ARMOR[i]);
                if(stack==null||stack.isEmpty())continue;
                @@ARMOR_EPF_LOOP@@
            }
        }catch(Throwable ignored){}
        return Math.min(20,epf);
    }
    private static final class HologramSample {
        final double x,y,z;
        final String text;
        HologramSample(double x,double y,double z,String text){this.x=x;this.y=y;this.z=z;this.text=text;}
    }
    private static String nearbyTntHologramText(List<HologramSample> holograms,Entity tnt){
        if(holograms==null||holograms.isEmpty()||tnt==null)return "";
        double tx=tnt.getX(),ty=tnt.getY(),tz=tnt.getZ();
        StringBuilder sb=null;
        for(int i=0;i<holograms.size();i++){
            HologramSample h=holograms.get(i);
            double dx=h.x-tx,dy=h.y-ty,dz=h.z-tz;
            if(dx*dx+dz*dz>6.25||dy<-1.0||dy>3.5)continue;
            if(sb==null)sb=new StringBuilder();
            else sb.append(' ');
            sb.append(h.text);
        }
        return sb==null?"":sb.toString();
    }
    private static void drawCornerBox(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,AABB b,int color,int fill,float width){
        if((fill>>>24)!=0)drawBox(out,view,b,false,0,fill,width);
        double sx=Math.min(0.28,(b.maxX-b.minX)*0.28);
        double sy=Math.min(0.28,(b.maxY-b.minY)*0.28);
        double sz=Math.min(0.28,(b.maxZ-b.minZ)*0.28);
        double[] xs={b.minX,b.maxX},ys={b.minY,b.maxY},zs={b.minZ,b.maxZ};
        for(int xi=0;xi<2;xi++)for(int yi=0;yi<2;yi++)for(int zi=0;zi<2;zi++){
            double x=xs[xi],y=ys[yi],z=zs[zi];
            double dx=xi==0?sx:-sx,dy=yi==0?sy:-sy,dz=zi==0?sz:-sz;
            dev.nightclient.renderer.WorldRibbon.line(out,view,x,y,z,x+dx,y,z,width,color);
            dev.nightclient.renderer.WorldRibbon.line(out,view,x,y,z,x,y+dy,z,width,color);
            dev.nightclient.renderer.WorldRibbon.line(out,view,x,y,z,x,y,z+dz,width,color);
        }
    }
    private static void drawWorldRing(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,double cx,double cy,double cz,double radius,float width,int color,int segments){
        dev.nightclient.renderer.WorldRibbon.groundRing(out,view,cx,cy,cz,radius,width,color,segments);
    }
    private static void drawWorldDisc(dev.nightclient.renderer.WorldRibbon.Quads out,double cx,double cy,double cz,double radius,int fillColor,int segments){
        dev.nightclient.renderer.WorldRibbon.groundDisc(out,cx,cy,cz,radius,fillColor,segments);
    }
    private static void drawEspCylinderWall(dev.nightclient.renderer.WorldRibbon.Quads out,double cx,double minY,double maxY,double cz,double radius,int fillColor,int segments){
        if((fillColor>>>24)==0||radius<=0.01||maxY<=minY)return;
        int n=Math.max(12,Math.min(72,segments));
        double step=(Math.PI*2.0)/n;
        double px=cx+radius,pz=cz;
        for(int i=1;i<=n;i++){
            double a=i*step;
            double nx=cx+Math.cos(a)*radius;
            double nz=cz+Math.sin(a)*radius;
            out.quad(px,minY,pz,nx,minY,nz,nx,maxY,nz,px,maxY,pz,fillColor);
            px=nx;pz=nz;
        }
    }
    private static void drawEspCylinderRibs(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,double cx,double minY,double maxY,double cz,double radius,float lineWidth,int strokeColor,int ribs){
        if((strokeColor>>>24)==0||radius<=0.01||maxY<=minY||ribs<=0)return;
        double step=(Math.PI*2.0)/ribs;
        for(int i=0;i<ribs;i++){
            double a=i*step;
            double x=cx+Math.cos(a)*radius;
            double z=cz+Math.sin(a)*radius;
            dev.nightclient.renderer.WorldRibbon.line(out,view,x,minY,z,x,maxY,z,lineWidth,strokeColor);
        }
    }
    private static void drawEspCircleLine(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,double cx,double y,double cz,double radius,float lineWidth,int strokeColor,int segments){
        if((strokeColor>>>24)==0||radius<=0.01)return;
        int n=Math.max(16,Math.min(72,segments));
        double step=(Math.PI*2.0)/n;
        double px=cx+radius,pz=cz;
        for(int i=1;i<=n;i++){
            double a=i*step;
            double nx=cx+Math.cos(a)*radius;
            double nz=cz+Math.sin(a)*radius;
            dev.nightclient.renderer.WorldRibbon.line(out,view,px,y,pz,nx,y,nz,lineWidth,strokeColor);
            px=nx;pz=nz;
        }
    }
    private static void drawMultiHeightBlastRings(Minecraft mc,dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,double cx,double cy,double cz,double radius,float lethalR,float fuseR,boolean showFill,float fillOpacity,boolean showLethal,boolean showFuse,float lineWidth,int color){
        double baseY=cy+0.04;
        double bandH=Math.max(2.2,Math.min(3.4,radius*0.35));
        double topY=baseY+bandH;
        int rgb=color&0xffffff;
        int segs=Math.max(28,Math.min(64,dev.nightclient.renderer.WorldRibbon.groundRingSegments(view,cx,baseY,cz,radius)));
        float thinW=Math.max(1.0f,Math.min(2.2f,lineWidth*0.85f));
        int strokeCol=(0xe4<<24)|rgb;
        int topCol=(0x96<<24)|rgb;
        if(showFill&&fillOpacity>0.005f){
            int wallA=Math.max(6,Math.min(180,Math.round(fillOpacity*0.75f*255f)));
            drawEspCylinderWall(out,cx,baseY,topY,cz,radius,(wallA<<24)|rgb,segs);
        }
        drawEspCircleLine(out,view,cx,baseY,cz,radius,thinW,strokeCol,segs);
        drawEspCircleLine(out,view,cx,topY,cz,radius,Math.max(0.85f,thinW*0.72f),topCol,segs);
        if(showLethal&&lethalR>0.4f&&lethalR<radius-0.3f){
            int lRgb=ExplosionRingLogic.COLOR_LETHAL&0xffffff;
            int lSegs=Math.max(24,Math.min(56,dev.nightclient.renderer.WorldRibbon.groundRingSegments(view,cx,baseY+0.015,cz,lethalR)));
            double lTopY=baseY+bandH*0.65;
            if(showFill&&fillOpacity>0.005f){
                int lWallA=Math.max(4,Math.min(140,Math.round(fillOpacity*0.50f*255f)));
                drawEspCylinderWall(out,cx,baseY,lTopY,cz,lethalR,(lWallA<<24)|lRgb,lSegs);
            }
            drawEspCircleLine(out,view,cx,baseY+0.015,cz,lethalR,Math.max(0.9f,thinW*0.85f),(0xdc<<24)|lRgb,lSegs);
        }
        if(showFuse&&fuseR>0.15f){
            int fSegs=Math.max(20,Math.min(56,dev.nightclient.renderer.WorldRibbon.groundRingSegments(view,cx,baseY+0.025,cz,fuseR)));
            drawEspCircleLine(out,view,cx,baseY+0.025,cz,fuseR,Math.max(0.95f,thinW*0.85f),(0xee<<24)|0xffffff,fSegs);
        }
    }
    static void emitWorldOverlays(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,float delta){
        Minecraft mc=Minecraft.getInstance();
        ClientRuntime runtime=ClientRuntime.get();
        if(mc==null||mc.level==null||mc.player==null||runtime==null)return;
        emitBlockOverlay(mc,runtime,out,view);
        emitHwHelperEsp(mc,runtime,out,view,delta);
        emitExplosionRings(mc,runtime,out,view,delta);
    }
    private static boolean hasEnemyInAabb(Minecraft mc,AABB area){
        if(mc==null||mc.level==null||mc.player==null)return false;
        for(Player other:mc.level.players()){
            if(other==null||other==mc.player||!other.isAlive()||other.isSpectator())continue;
            if(area.intersects(other.getBoundingBox()))return true;
        }
        return false;
    }
    private static boolean hasEnemyInRadius(Minecraft mc,double cx,double cy,double cz,double radius){
        if(mc==null||mc.level==null||mc.player==null)return false;
        double rSq=radius*radius;
        for(Player other:mc.level.players()){
            if(other==null||other==mc.player||!other.isAlive()||other.isSpectator())continue;
            double dx=other.getX()-cx,dy=other.getY()-cy,dz=other.getZ()-cz;
            if(dx*dx+dy*dy+dz*dz<=rSq)return true;
        }
        return false;
    }
    private static boolean isHoldingItem(Player player,net.minecraft.world.item.Item item){
        if(player==null||item==null)return false;
        return player.getMainHandItem().getItem()==item||player.getOffhandItem().getItem()==item;
    }
    private static String extractKringeType(ItemStack st){
        if(st==null||st.isEmpty())return "";
        String kringeType="";
        net.minecraft.nbt.CompoundTag root=extractStackNbt(st);
        if(root!=null&&root.contains("kringeItems")){
            kringeType=nbtString(nbtCompound(root,"kringeItems"),"type");
        }
        if(kringeType.isEmpty()){
            net.minecraft.nbt.CompoundTag pbv=extractBukkitValues(st);
            if(pbv!=null){
                if(pbv.contains("kringeItems")){
                    kringeType=nbtString(nbtCompound(pbv,"kringeItems"),"type");
                }
                if(kringeType.isEmpty()){
                    String t=readBukkitKey(pbv,"kringeitems:type");
                    if(!t.isEmpty())kringeType=t;
                }
                if(kringeType.isEmpty()){
                    String t=readBukkitKey(pbv,"custom_item");
                    if(!t.isEmpty())kringeType=t;
                }
            }
        }
        return kringeType;
    }
    private static boolean isHoldingJakeLuminaire(Player player){
        if(player==null)return false;
        ItemStack[] hands=new ItemStack[]{player.getMainHandItem(),player.getOffhandItem()};
        for(ItemStack st:hands){
            if(st==null||st.isEmpty()||st.getItem()!=net.minecraft.world.item.Items.JACK_O_LANTERN)continue;
            if(HwPvpAndEventsLogic.isJakeLuminaire(itemId(st),extractKringeType(st),st.getHoverName().getString()))return true;
        }
        return false;
    }
    private static void drawHwEspBox(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,AABB box,int rgb,float fillOpacity,float lineWidth){
        int cRgb=rgb&0xffffff;
        int strokeCol=(0xe4<<24)|cRgb;
        int fillA=Math.max(0,Math.min(200,Math.round(fillOpacity*255f)));
        int fillCol=fillA>0?((fillA<<24)|cRgb):0;
        drawBox(out,view,box,true,strokeCol,fillCol,lineWidth);
    }
    private static void drawHwEspLowerExtension(dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,AABB b,int rgb,float fillOpacity,float lineWidth){
        int cRgb=rgb&0xffffff;
        int strokeCol=(0x90<<24)|cRgb;
        int fillA=Math.max(0,Math.min(120,Math.round(fillOpacity*0.45f*255f)));
        if(fillA>0){
            int fillCol=(fillA<<24)|cRgb;
            out.quad(b.minX,b.minY,b.minZ,b.minX,b.maxY,b.minZ,b.maxX,b.maxY,b.minZ,b.maxX,b.minY,b.minZ,fillCol);
            out.quad(b.minX,b.minY,b.maxZ,b.maxX,b.minY,b.maxZ,b.maxX,b.maxY,b.maxZ,b.minX,b.maxY,b.maxZ,fillCol);
            out.quad(b.minX,b.minY,b.minZ,b.minX,b.minY,b.maxZ,b.minX,b.maxY,b.maxZ,b.minX,b.maxY,b.minZ,fillCol);
            out.quad(b.maxX,b.minY,b.minZ,b.maxX,b.maxY,b.minZ,b.maxX,b.maxY,b.maxZ,b.maxX,b.minY,b.maxZ,fillCol);
            out.quad(b.minX,b.minY,b.minZ,b.maxX,b.minY,b.minZ,b.maxX,b.minY,b.maxZ,b.minX,b.minY,b.maxZ,fillCol);
        }
        float subW=Math.max(0.9f,lineWidth*0.75f);
        double[] xs={b.minX,b.maxX},zs={b.minZ,b.maxZ};
        for(int i=0;i<2;i++)for(int j=0;j<2;j++){
            dev.nightclient.renderer.WorldRibbon.line(out,view,xs[i],b.minY,zs[j],xs[i],b.maxY,zs[j],subW,strokeCol);
        }
        dev.nightclient.renderer.WorldRibbon.line(out,view,b.minX,b.minY,b.minZ,b.maxX,b.minY,b.minZ,subW,strokeCol);
        dev.nightclient.renderer.WorldRibbon.line(out,view,b.minX,b.minY,b.maxZ,b.maxX,b.minY,b.maxZ,subW,strokeCol);
        dev.nightclient.renderer.WorldRibbon.line(out,view,b.minX,b.minY,b.minZ,b.minX,b.minY,b.maxZ,subW,strokeCol);
        dev.nightclient.renderer.WorldRibbon.line(out,view,b.maxX,b.minY,b.minZ,b.maxX,b.minY,b.maxZ,subW,strokeCol);
    }
    private static void emitHwHelperEsp(Minecraft mc,ClientRuntime runtime,dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,float delta){
        Settings.HwHelper hw=runtime.settings().hwhelper;
        if(FeaturePolicy.isBlocked("hwhelper.zones")||hw==null||!hw.enabled||hw.esp==null||!hw.esp.enabled||@@FOOD_HUD_HIDDEN@@)return;
        Settings.HwEsp esp=hw.esp;
        Player player=mc.player;
        Vec3 pos=player.getPosition(delta);
        int bx=(int)Math.floor(player.getX());
        int by=(int)Math.floor(player.getY());
        int bz=(int)Math.floor(player.getZ());

        if(esp.stun&&isHoldingItem(player,net.minecraft.world.item.Items.NETHER_STAR)){
            AABB stunBox=new AABB(pos.x-15.0,pos.y-15.0,pos.z-15.0,pos.x+15.0,pos.y+15.0,pos.z+15.0);
            int rgb=hasEnemyInAabb(mc,stunBox)?esp.enemyColor:esp.idleColor;
            drawHwEspBox(out,view,stunBox,rgb,esp.fillOpacity,esp.lineWidth);
        }

        if(esp.explosiveTrap&&isHoldingItem(player,net.minecraft.world.item.Items.PRISMARINE_SHARD)){
            double[] chk=HwPvpAndEventsLogic.computeExplosiveTrapCheckBox(bx,by,bz);
            double[] main=HwPvpAndEventsLogic.computeExplosiveTrapMainBox(bx,by,bz);
            double[] lower=HwPvpAndEventsLogic.computeExplosiveTrapLowerBox(bx,by,bz);
            AABB checkArea=new AABB(chk[0],chk[1],chk[2],chk[3],chk[4],chk[5]);
            AABB mainArea=new AABB(main[0],main[1],main[2],main[3],main[4],main[5]);
            AABB lowerArea=new AABB(lower[0],lower[1],lower[2],lower[3],lower[4],lower[5]);
            int rgb=hasEnemyInAabb(mc,checkArea)?esp.enemyColor:esp.idleColor;
            drawHwEspBox(out,view,mainArea,rgb,esp.fillOpacity,esp.lineWidth);
            drawHwEspLowerExtension(out,view,lowerArea,rgb,esp.fillOpacity,esp.lineWidth);
        }

        if(esp.trap&&isHoldingItem(player,net.minecraft.world.item.Items.POPPED_CHORUS_FRUIT)){
            double[] tb=HwPvpAndEventsLogic.computeNormalTrapBox(bx,by,bz);
            AABB trapCheck=new AABB(bx-2.0,by,bz-2.0,bx+3.0,by+4.0,bz+3.0);
            AABB trapDraw=new AABB(tb[0],tb[1],tb[2],tb[3],tb[4],tb[5]);
            int rgb=hasEnemyInAabb(mc,trapCheck)?esp.enemyColor:esp.idleColor;
            drawHwEspBox(out,view,trapDraw,rgb,esp.fillOpacity,esp.lineWidth);
        }

        if(esp.jakeLuminaire&&isHoldingJakeLuminaire(player)){
            int rgb=(hasEnemyInRadius(mc,pos.x,pos.y,pos.z,15.0)?esp.enemyColor:esp.idleColor)&0xffffff;
            int segs=Math.max(48,dev.nightclient.renderer.WorldRibbon.groundRingSegments(view,pos.x,pos.y+0.06,pos.z,15.0));
            if(esp.fillOpacity>0.005f){
                int wallA=Math.max(6,Math.min(180,Math.round(esp.fillOpacity*0.85f*255f)));
                drawEspCylinderWall(out,pos.x,pos.y,pos.y+2.2,pos.z,15.0,(wallA<<24)|rgb,segs);
            }
            drawEspCircleLine(out,view,pos.x,pos.y+0.04,pos.z,15.0,esp.lineWidth,(0xe4<<24)|rgb,segs);
            drawEspCircleLine(out,view,pos.x,pos.y+2.2,pos.z,15.0,Math.max(0.9f,esp.lineWidth*0.7f),(0x99<<24)|rgb,segs);
        }

        if(esp.drillHelper&&mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult){
            net.minecraft.world.phys.BlockHitResult bhr=(net.minecraft.world.phys.BlockHitResult)mc.hitResult;
            if(bhr.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){
                ItemStack main=player.getMainHandItem();
                if(!main.isEmpty()&&(main.is(net.minecraft.tags.ItemTags.PICKAXES)||itemId(main).endsWith("_pickaxe"))){
                    net.minecraft.nbt.CompoundTag pbv=extractBukkitValues(main);
                    net.minecraft.nbt.CompoundTag root=extractStackNbt(main);
                    String customEnch=readBukkitKey(pbv,"custom-enchantments");
                    if(customEnch.isEmpty())customEnch=readBukkitKey(root,"custom-enchantments");
                    String drillKey=readBukkitKey(pbv,"drill-enchant-custom");
                    if(drillKey.isEmpty())drillKey=readBukkitKey(root,"drill-enchant-custom");
                    int depth=HwPvpAndEventsLogic.detectDrillLevel(itemId(main),customEnch,drillKey,readLore(main));
                    if(depth>0){
                        net.minecraft.core.BlockPos targetPos=bhr.getBlockPos();
                        net.minecraft.core.Direction dir=bhr.getDirection();
                        double[] b=HwPvpAndEventsLogic.computeDrillBox(
                            targetPos.getX(),targetPos.getY(),targetPos.getZ(),
                            dir.getStepX(),dir.getStepY(),dir.getStepZ(),depth);
                        AABB drillAabb=new AABB(b[0],b[1],b[2],b[3],b[4],b[5]).inflate(0.004);
                        drawHwEspBox(out,view,drillAabb,esp.drillColor,esp.fillOpacity,esp.lineWidth);
                    }
                }
            }
        }
    }
    private static void emitBlockOverlay(Minecraft mc,ClientRuntime runtime,dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view){
        Settings.BlockOverlay cfg=runtime.settings().blockOverlay;
        if(FeaturePolicy.isBlocked("blockOverlay")||cfg==null||!cfg.enabled||@@FOOD_HUD_HIDDEN@@){blockLerpValid=false;return;}
        if(!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult)){blockLerpValid=false;return;}
        net.minecraft.world.phys.BlockHitResult bhr=(net.minecraft.world.phys.BlockHitResult)mc.hitResult;
        if(bhr.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK){blockLerpValid=false;return;}
        net.minecraft.core.BlockPos pos=bhr.getBlockPos();
        net.minecraft.world.level.block.state.BlockState state=mc.level.getBlockState(pos);
        if(state.isAir()||!mc.level.getWorldBorder().isWithinBounds(pos)){blockLerpValid=false;return;}
        net.minecraft.world.phys.shapes.VoxelShape shape=state.getShape(mc.level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(mc.player));
        if(shape.isEmpty()){blockLerpValid=false;return;}
        List<AABB> boxes=cfg.exactShape?shape.toAabbs():Collections.singletonList(shape.bounds());
        if(boxes.isEmpty()){blockLerpValid=false;return;}
        AABB union=shape.bounds().move(pos.getX(),pos.getY(),pos.getZ());
        long now=System.nanoTime();
        float dt=blockLerpLastNanos==0L?0.016f:(float)Math.max(0.0,Math.min(0.1,(now-blockLerpLastNanos)*1e-9));
        blockLerpLastNanos=now;
        double shiftX=0,shiftY=0,shiftZ=0;
        if(cfg.smooth){
            if(!blockLerpValid){
                blockLerpMinX=union.minX;blockLerpMinY=union.minY;blockLerpMinZ=union.minZ;
                blockLerpMaxX=union.maxX;blockLerpMaxY=union.maxY;blockLerpMaxZ=union.maxZ;
                blockLerpValid=true;
            }else{
                double t=1.0-Math.exp(-dt*cfg.smoothSpeed);
                blockLerpMinX+=(union.minX-blockLerpMinX)*t;
                blockLerpMinY+=(union.minY-blockLerpMinY)*t;
                blockLerpMinZ+=(union.minZ-blockLerpMinZ)*t;
                blockLerpMaxX+=(union.maxX-blockLerpMaxX)*t;
                blockLerpMaxY+=(union.maxY-blockLerpMaxY)*t;
                blockLerpMaxZ+=(union.maxZ-blockLerpMaxZ)*t;
            }
            shiftX=((blockLerpMinX+blockLerpMaxX)-(union.minX+union.maxX))*0.5;
            shiftY=((blockLerpMinY+blockLerpMaxY)-(union.minY+union.maxY))*0.5;
            shiftZ=((blockLerpMinZ+blockLerpMaxZ)-(union.minZ+union.maxZ))*0.5;
        }else{
            blockLerpValid=false;
        }
        int baseRgb=(cfg.useThemeAccent&&runtime.settings().theme!=null?runtime.settings().theme.accent:cfg.color)&0x00ffffff;
        float pulseMult=cfg.pulse?(0.82f+0.18f*(float)Math.sin(ClientRuntime.nowSeconds()*3.6)):1.0f;
        int strokeAlpha=Math.max(0,Math.min(255,Math.round(cfg.outlineOpacity*pulseMult*255f)));
        int fillAlpha=Math.max(0,Math.min(255,Math.round(cfg.fillOpacity*pulseMult*255f)));
        int strokeColor=(strokeAlpha<<24)|baseRgb;
        int fillColor=(fillAlpha<<24)|baseRgb;
        String mode=cfg.mode==null?"both":cfg.mode;
        boolean doStroke=!"fill".equals(mode);
        boolean doFill=!"outline".equals(mode);
        boolean corners="corners".equals(mode);
        if(cfg.smooth&&boxes.size()==1){
            AABB b=new AABB(blockLerpMinX,blockLerpMinY,blockLerpMinZ,blockLerpMaxX,blockLerpMaxY,blockLerpMaxZ).inflate(0.0025);
            if(corners)drawCornerBox(out,view,b,strokeColor,doFill?fillColor:0,cfg.lineWidth);
            else drawBox(out,view,b,doStroke,strokeColor,doFill?fillColor:0,cfg.lineWidth);
        }else{
            for(int i=0;i<boxes.size();i++){
                AABB b=boxes.get(i).move(pos.getX()+shiftX,pos.getY()+shiftY,pos.getZ()+shiftZ).inflate(0.0025);
                if(corners)drawCornerBox(out,view,b,strokeColor,doFill?fillColor:0,cfg.lineWidth);
                else drawBox(out,view,b,doStroke,strokeColor,doFill?fillColor:0,cfg.lineWidth);
            }
        }
    }
    private static void emitExplosionRings(Minecraft mc,ClientRuntime runtime,dev.nightclient.renderer.WorldRibbon.Quads out,dev.nightclient.renderer.WorldRibbon.View view,float delta){
        Settings.ExplosionRing cfg=runtime.settings().explosionRing;
        if(FeaturePolicy.isBlocked("explosionRing")||cfg==null||!cfg.enabled){latestExplosionBadges=Collections.emptyList();return;}
        ExplosionRingLogic.ServerExplosionLearner learner=runtime.explosionLearner();
        learner.syncScope(currentWorldId());
        double maxDistSq=(double)cfg.maxDistance*(double)cfg.maxDistance;
        float playerHp=Math.max(0.5f,mc.player.getHealth()+mc.player.getAbsorptionAmount());
        float armorVal=mc.player.getArmorValue();
        float toughness=0f;
        try{toughness=(float)mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);}catch(Throwable ignored){}
        int epf=playerBlastEpf(mc.player);
        MobEffectInstance resistInst=mc.player.getEffect(@@RESISTANCE_EFFECT@@);
        int resistAmp=resistInst==null?-1:resistInst.getAmplifier();
        long nowMs=System.currentTimeMillis();
        List<ExplosionRingLogic.HazardBadge> badges=new ArrayList<ExplosionRingLogic.HazardBadge>();
        List<net.minecraft.world.entity.item.PrimedTnt> tnts=null;
        List<HologramSample> holograms=null;
        int ringCount=0;
        for(Entity entity:mc.level.entitiesForRendering()){
            if(entity==null||!entity.isAlive())continue;
            double dSq=distanceSquared(entity,mc.player);
            if(dSq>maxDistSq){
                if(cfg.showTnt&&dSq<=maxDistSq+64.0){
                    Entity e=entity;
                    @@HOLOGRAM_TEXT_EXPR@@
                    if(s!=null&&!s.isEmpty()){
                        if(holograms==null)holograms=new ArrayList<HologramSample>();
                        holograms.add(new HologramSample(e.getX(),e.getY(),e.getZ(),s));
                    }
                }
                continue;
            }
            if(cfg.showTnt&&entity instanceof net.minecraft.world.entity.item.PrimedTnt){
                if(tnts==null)tnts=new ArrayList<net.minecraft.world.entity.item.PrimedTnt>();
                if(tnts.size()<32)tnts.add((net.minecraft.world.entity.item.PrimedTnt)entity);
            }else if(cfg.showCrystals&&ringCount<32&&entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal){
                Vec3 pos=entity.getPosition(delta);
                float radius=ExplosionRingLogic.VANILLA_CRYSTAL_POWER*2.0f;
                float power=ExplosionRingLogic.VANILLA_CRYSTAL_POWER;
                double dist=Math.sqrt(dSq);
                float dmg=ExplosionRingLogic.estimateBlastDamage(dist,power,1.0f,armorVal,toughness,epf,resistAmp,cfg.damageMultiplier);
                float lethalR=ExplosionRingLogic.computeLethalRadius(power,playerHp,armorVal,toughness,epf,resistAmp,cfg.damageMultiplier);
                int color=ExplosionRingLogic.threatColor(dist,radius,lethalR,dmg,playerHp);
                drawMultiHeightBlastRings(mc,out,view,pos.x,pos.y,pos.z,radius,lethalR,-1f,cfg.showFill,cfg.fillOpacity*0.85f,cfg.showLethalRing,false,cfg.lineWidth,color);
                if(dist<=radius+2.0){
                    badges.add(new ExplosionRingLogic.HazardBadge(pos.x,pos.y+2.1,pos.z,"Crystal",-1f,radius,dmg,dmg>=playerHp*0.92f,false,color));
                }
                ringCount++;
            }else if(cfg.showTnt){
                Entity e=entity;
                @@HOLOGRAM_TEXT_EXPR@@
                if(s!=null&&!s.isEmpty()){
                    if(holograms==null)holograms=new ArrayList<HologramSample>();
                    holograms.add(new HologramSample(e.getX(),e.getY(),e.getZ(),s));
                }
            }
        }
        if(tnts!=null){
            for(int ti=0;ti<tnts.size()&&ringCount<32;ti++){
                net.minecraft.world.entity.item.PrimedTnt tnt=tnts.get(ti);
                double dSq=distanceSquared(tnt,mc.player);
                Vec3 pos=tnt.getPosition(delta);
                int fuse=Math.max(0,@@TNT_FUSE_GET@@);
                Integer prevMax=tntMaxFuse.get(tnt.getId());
                int maxFuse=prevMax==null?Math.max(80,fuse):Math.max(prevMax.intValue(),fuse);
                if(tntMaxFuse.size()>128)tntMaxFuse.clear();
                tntMaxFuse.put(tnt.getId(),maxFuse);
                String customName=tnt.getCustomName()!=null?tnt.getCustomName().getString():"";
                String holo=nearbyTntHologramText(holograms,tnt);
                ExplosionRingLogic.TntSpec spec=ExplosionRingLogic.inferFromText(customName,holo);
                if(!spec.custom&&recentHeldTntSpec!=null&&(nowMs-recentHeldTntMs)<=6000L&&dSq<=49.0){
                    spec=recentHeldTntSpec;
                }
                learner.trackTnt(tnt.getId(),pos.x,pos.y,pos.z,spec.signature,nowMs);
                float radius=learner.resolveRadius(spec.signature,spec.radius,cfg.customTntRadius,cfg.autoLearnRadius);
                float power=radius*0.5f;
                float totalMult=cfg.damageMultiplier*spec.damageMultiplier;
                double dist=Math.sqrt(dSq);
                float dmg=ExplosionRingLogic.estimateBlastDamage(dist,power,1.0f,armorVal,toughness,epf,resistAmp,totalMult);
                float lethalR=ExplosionRingLogic.computeLethalRadius(power,playerHp,armorVal,toughness,epf,resistAmp,totalMult);
                int color=ExplosionRingLogic.threatColor(dist,radius,lethalR,dmg,playerHp);
                float fuseR=-1f;
                if(cfg.showFuseRing&&maxFuse>0){
                    float smoothFuse=Math.max(0f,fuse-delta);
                    float progress=Math.max(0.03f,Math.min(1.0f,smoothFuse/(float)maxFuse));
                    fuseR=(float)(radius*progress);
                }
                drawMultiHeightBlastRings(mc,out,view,pos.x,pos.y,pos.z,radius,lethalR,fuseR,cfg.showFill,cfg.fillOpacity,cfg.showLethalRing,cfg.showFuseRing&&maxFuse>0,cfg.lineWidth,color);
                badges.add(new ExplosionRingLogic.HazardBadge(pos.x,pos.y+1.25,pos.z,spec.label,fuse/20.0f,radius,dmg,dmg>=playerHp*0.92f,learner.hasLearned(spec.signature),color));
                ringCount++;
            }
        }
        if(cfg.showAnchors&&!currentDimension().contains("the_nether")){
            if(Math.abs(mc.player.tickCount-cachedAnchorTick)>=5){
                cachedAnchorTick=mc.player.tickCount;
                cachedAnchors.clear();
                int px=(int)Math.floor(mc.player.getX()),py=(int)Math.floor(mc.player.getY()),pz=(int)Math.floor(mc.player.getZ());
                int scanR=Math.min(14,(int)cfg.maxDistance);
                net.minecraft.core.BlockPos.MutableBlockPos mp=new net.minecraft.core.BlockPos.MutableBlockPos();
                for(int dx=-scanR;dx<=scanR&&cachedAnchors.size()<12;dx++){
                    for(int dy=-5;dy<=5&&cachedAnchors.size()<12;dy++){
                        for(int dz=-scanR;dz<=scanR&&cachedAnchors.size()<12;dz++){
                            mp.set(px+dx,py+dy,pz+dz);
                            net.minecraft.world.level.block.state.BlockState bs=mc.level.getBlockState(mp);
                            if(bs.getBlock() instanceof net.minecraft.world.level.block.RespawnAnchorBlock
                                &&bs.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE)>0){
                                cachedAnchors.add(mp.immutable());
                            }
                        }
                    }
                }
            }
            for(int i=0;i<cachedAnchors.size()&&ringCount<32;i++){
                net.minecraft.core.BlockPos ap=cachedAnchors.get(i);
                double ax=ap.getX()+0.5,ay=ap.getY()+0.06,az=ap.getZ()+0.5;
                double ddx=ax-mc.player.getX(),ddy=ay-mc.player.getY(),ddz=az-mc.player.getZ();
                double dSq=ddx*ddx+ddy*ddy+ddz*ddz;
                if(dSq>maxDistSq)continue;
                float radius=ExplosionRingLogic.VANILLA_ANCHOR_POWER*2.0f;
                float power=ExplosionRingLogic.VANILLA_ANCHOR_POWER;
                double dist=Math.sqrt(dSq);
                float dmg=ExplosionRingLogic.estimateBlastDamage(dist,power,1.0f,armorVal,toughness,epf,resistAmp,cfg.damageMultiplier);
                float lethalR=ExplosionRingLogic.computeLethalRadius(power,playerHp,armorVal,toughness,epf,resistAmp,cfg.damageMultiplier);
                int color=ExplosionRingLogic.threatColor(dist,radius,lethalR,dmg,playerHp);
                drawMultiHeightBlastRings(mc,out,view,ax,ap.getY(),az,radius,lethalR,-1f,cfg.showFill,cfg.fillOpacity*0.85f,cfg.showLethalRing,false,cfg.lineWidth,color);
                if(dist<=radius+2.0){
                    badges.add(new ExplosionRingLogic.HazardBadge(ax,ap.getY()+1.35,az,"Anchor",-1f,radius,dmg,dmg>=playerHp*0.92f,false,color));
                }
                ringCount++;
            }
        }
        latestExplosionBadges=badges.isEmpty()?Collections.<ExplosionRingLogic.HazardBadge>emptyList():Collections.unmodifiableList(badges);
    }
    public static void onBeforeDestroyBlock(net.minecraft.core.BlockPos pos){
        if(pos==null)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.level==null)return;
        net.minecraft.world.level.block.state.BlockState st=mc.level.getBlockState(pos);
        if(st==null||st.isAir())return;
        @@COSMETICS_IDENTIFIER@@ key=@@BLOCK_REGISTRY@@.getKey(st.getBlock());
        if(key!=null)recentBrokenBlocks.put(Long.valueOf(pos.asLong()),new BrokenBlockEntry(key.getPath(),System.nanoTime()+1_500_000_000L));
    }
    public static String crosshairBlockId(){
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.level==null||!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult))return "";
        net.minecraft.world.phys.BlockHitResult bhr=(net.minecraft.world.phys.BlockHitResult)mc.hitResult;
        if(bhr.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return "";
        net.minecraft.world.level.block.state.BlockState st=mc.level.getBlockState(bhr.getBlockPos());
        if(st==null||st.isAir())return "";
        @@COSMETICS_IDENTIFIER@@ key=@@BLOCK_REGISTRY@@.getKey(st.getBlock());
        return key==null?"":key.getPath();
    }
    public static void playClientSound(String soundId,float volume,float pitch){
        if(soundId==null||soundId.isEmpty()||volume<=0.001f)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.getSoundManager()==null)return;
        try{
            @@COSMETICS_IDENTIFIER@@ id=GameBridge.identifier(soundId);
            net.minecraft.sounds.SoundEvent ev=net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev,pitch,volume));
        }catch(Throwable ignored){}
    }
    public static net.minecraft.client.resources.sounds.SoundInstance replacePlayedSound(net.minecraft.client.resources.sounds.SoundInstance sound){
        if(sound==null)return null;
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("soundControl")||runtime==null||runtime.settings().soundControl==null||!runtime.settings().soundControl.enabled)return sound;
        try{
            Object loc=@@SOUND_INSTANCE_ID@@;
            if(loc==null)return sound;
            String soundId=loc.toString();
            SoundControlLogic.Mp3Playback mp3=SoundControlLogic.resolveCustomTntMp3(runtime.settings().soundControl,soundId);
            if(mp3!=null){
                float atten=1.0f;
                Minecraft mc=Minecraft.getInstance();
                if(mc!=null){
                    if(mc.options!=null){
                        try{
                            atten*=mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
                            atten*=mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.BLOCKS);
                        }catch(Throwable ignored){}
                    }
                    if(mc.player!=null&&sound.getAttenuation()!=net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE){
                        double dx=sound.getX()-mc.player.getX(),dy=sound.getY()-mc.player.getY(),dz=sound.getZ()-mc.player.getZ();
                        double dist=Math.sqrt(dx*dx+dy*dy+dz*dz);
                        double maxRange=mp3.explosion?64.0:20.0;
                        float distScale=(float)Math.max(0.0,1.0-(dist/maxRange));
                        atten*=distScale;
                    }
                }
                float effVol=mp3.volume*atten;
                boolean played=true;
                if(effVol>0.005f){
                    played=runtime.playCustomAudio(mp3.filePath,effVol,mp3.pitch);
                }
                if(customTntSoundTags.size()>64)customTntSoundTags.clear();
                customTntSoundTags.put(sound,Integer.valueOf(played?1:4));
                return sound;
            }
            SoundControlLogic.SoundReplacement rep=SoundControlLogic.resolveCustomTntReplacement(runtime.settings().soundControl,soundId);
            if(rep==null)return sound;
            String normId=soundId.toLowerCase(Locale.ROOT);
            int colonIdx=normId.indexOf(':');
            if(colonIdx>=0)normId=normId.substring(colonIdx+1);
            boolean isExplosion=normId.startsWith("entity.generic.explode")||normId.startsWith("entity.dragon_fireball.explode");
            @@COSMETICS_IDENTIFIER@@ targetId=GameBridge.identifier(rep.soundId);
            net.minecraft.sounds.SoundEvent ev=net.minecraft.sounds.SoundEvent.createVariableRangeEvent(targetId);
            net.minecraft.client.resources.sounds.SoundInstance replaced=new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                ev,sound.getSource(),rep.volume,rep.pitch,
                net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(),
                sound.getX(),sound.getY(),sound.getZ());
            if(customTntSoundTags.size()>64)customTntSoundTags.clear();
            customTntSoundTags.put(replaced,Integer.valueOf(isExplosion?3:2));
            return replaced;
        }catch(Throwable ignored){
            return sound;
        }
    }
    public static float scalePlayedSoundVolume(net.minecraft.client.resources.sounds.SoundInstance sound,float origVolume){
        if(sound==null||origVolume<=0.0f)return origVolume;
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime==null)return origVolume;
        Settings.SoundControl cfg=runtime.settings().soundControl;
        if(FeaturePolicy.isBlocked("soundControl")||cfg==null||!cfg.enabled)return origVolume;
        try{
            Integer tag=customTntSoundTags.get(sound);
            if(tag!=null){
                int t=tag.intValue();
                if(t==1)return 0.0f;
                if(t==2)return origVolume;
                if(t==3)return Math.max(0.0f,Math.min(2.0f,origVolume*cfg.explosionVolume));
            }
            Object loc=@@SOUND_INSTANCE_ID@@;
            if(loc==null)return origVolume;
            String soundId=loc.toString();
            String normPath=soundId.toLowerCase(Locale.ROOT);
            int ci=normPath.indexOf(':');
            if(ci>=0)normPath=normPath.substring(ci+1);
            String blockPath="";
            if((normPath.startsWith("block.")||normPath.startsWith("liquid."))&&cfg.muteBlocks&&cfg.mutedBlocks!=null&&!cfg.mutedBlocks.isEmpty()&&sound.getAttenuation()!=net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE){
                Minecraft mc=Minecraft.getInstance();
                if(mc!=null&&mc.level!=null){
                    int bx=(int)Math.floor(sound.getX()),by=(int)Math.floor(sound.getY()),bz=(int)Math.floor(sound.getZ());
                    net.minecraft.core.BlockPos bp=new net.minecraft.core.BlockPos(bx,by,bz);
                    Long bpKey=Long.valueOf(bp.asLong());
                    BrokenBlockEntry broken=recentBrokenBlocks.get(bpKey);
                    long nowNanos=System.nanoTime();
                    if(broken!=null&&broken.expiresNanos>=nowNanos&&broken.path!=null&&!broken.path.isEmpty()){
                        blockPath=broken.path;
                    }else{
                        if(broken!=null)recentBrokenBlocks.remove(bpKey);
                        net.minecraft.world.level.block.state.BlockState st=mc.level.getBlockState(bp);
                        if(st==null||st.isAir()){
                            st=mc.level.getBlockState(new net.minecraft.core.BlockPos(bx,(int)Math.floor(sound.getY()-0.35),bz));
                        }
                        if(st!=null&&!st.isAir()){
                            @@COSMETICS_IDENTIFIER@@ bk=@@BLOCK_REGISTRY@@.getKey(st.getBlock());
                            if(bk!=null)blockPath=bk.getPath();
                        }
                    }
                }
            }
            boolean allowMp3Mute=tag==null||tag.intValue()!=4;
            float mult=SoundControlLogic.computeVolumeMultiplier(cfg,soundId,blockPath,allowMp3Mute);
            if(mult<=0.0005f)return 0.0f;
            if(Math.abs(mult-1.0f)<0.001f)return origVolume;
            return Math.max(0.0f,Math.min(2.0f,origVolume*mult));
        }catch(Throwable ignored){
            return origVolume;
        }
    }
    private static void tickTntCountdownSounds(Minecraft mc,ClientRuntime runtime){
        Settings.SoundControl cfg=runtime.settings().soundControl;
        if(FeaturePolicy.isBlocked("soundControl")||cfg==null||!cfg.enabled||!cfg.customTnt||!cfg.tntCountdownTicks||mc.level==null||mc.player==null)return;
        for(Entity e:mc.level.entitiesForRendering()){
            if(!(e instanceof net.minecraft.world.entity.item.PrimedTnt)||!e.isAlive())continue;
            if(distanceSquared(e,mc.player)>400.0)continue;
            net.minecraft.world.entity.item.PrimedTnt tnt=(net.minecraft.world.entity.item.PrimedTnt)e;
            int fuse=@@TNT_FUSE_GET@@;
            if(!SoundControlLogic.isTntCountdownTick(fuse))continue;
            Integer prev=tntLastCountdownTick.get(Integer.valueOf(tnt.getId()));
            if(prev!=null&&prev.intValue()==fuse)continue;
            if(tntLastCountdownTick.size()>64)tntLastCountdownTick.clear();
            tntLastCountdownTick.put(Integer.valueOf(tnt.getId()),Integer.valueOf(fuse));
            playClientSound("minecraft:block.note_block.hat",Math.min(1.5f,cfg.tntVolume*0.65f),SoundControlLogic.tntCountdownPitch(fuse));
        }
    }
    public static boolean shouldSuppressUnfocusedPause(){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("tapeMouse")||runtime==null||runtime.settings().tapeMouse==null)return false;
        Settings.TapeMouse tm=runtime.settings().tapeMouse;
        return tm.enabled&&tm.workInBackground;
    }
    private static void tickTapeMouse(Minecraft mc,ClientRuntime runtime){
        Settings.TapeMouse cfg=runtime.settings().tapeMouse;
        boolean inWorld=mc!=null&&mc.player!=null&&mc.level!=null;
        boolean screenOpen=inWorld&&@@SCREEN@@!=null;
        boolean tookDamage=inWorld&&mc.player.hurtTime>lastTapeHurtTime&&mc.player.hurtTime>=8;
        lastTapeHurtTime=inWorld?mc.player.hurtTime:0;
        boolean wasEnabled=cfg!=null&&cfg.enabled;
        TapeMouseLogic.TickStep step=runtime.tapeMouseLogic().step(cfg,inWorld,screenOpen,tookDamage);
        if(wasEnabled&&cfg!=null&&!cfg.enabled&&tookDamage){
            runtime.changed();
            sendChatMessage(dev.nightclient.i18n.I18n.text(runtime.settings().language,"tapeMouse.msg.damage_stop"));
        }
        if(step.releaseHold)releaseTapeMouseKeys(mc);
        if(!inWorld||cfg==null||(!step.holdDown&&!step.triggerClick))return;
        String act=cfg.action;
        if("attack".equals(act)){
            if(mc instanceof dev.nightclient.platform.mixin.MinecraftTapeAccessor){
                dev.nightclient.platform.mixin.MinecraftTapeAccessor acc=(dev.nightclient.platform.mixin.MinecraftTapeAccessor)mc;
                if(step.triggerClick){
                    acc.nightclient$setMissTime(0);
                    acc.nightclient$startAttack();
                }else if(step.holdDown){
                    mc.options.keyAttack.setDown(true);
                    acc.nightclient$continueAttack(true);
                }
            }
        }else if("use".equals(act)){
            if(mc instanceof dev.nightclient.platform.mixin.MinecraftTapeAccessor){
                dev.nightclient.platform.mixin.MinecraftTapeAccessor acc=(dev.nightclient.platform.mixin.MinecraftTapeAccessor)mc;
                if(step.triggerClick){
                    acc.nightclient$setRightClickDelay(0);
                    acc.nightclient$startUseItem();
                }else if(step.holdDown){
                    mc.options.keyUse.setDown(true);
                }
            }
        }else{
            net.minecraft.client.KeyMapping km=resolveTapeKeyMapping(mc,cfg);
            if(km!=null){
                if(step.holdDown){
                    tapeCustomMapping=km;
                    km.setDown(true);
                }else if(step.triggerClick){
                    try{
                        net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(km.saveString()));
                    }catch(Throwable ignored){}
                }
            }
        }
    }
    private static net.minecraft.client.KeyMapping resolveTapeKeyMapping(Minecraft mc,Settings.TapeMouse cfg){
        if(mc==null||mc.options==null||cfg==null)return null;
        if("forward".equals(cfg.action))return mc.options.keyUp;
        if("jump".equals(cfg.action))return mc.options.keyJump;
        if("sneak".equals(cfg.action))return mc.options.keyShift;
        if("sprint".equals(cfg.action))return mc.options.keySprint;
        if("drop".equals(cfg.action))return mc.options.keyDrop;
        String custom=cfg.customKey==null?"":cfg.customKey;
        for(net.minecraft.client.KeyMapping km:mc.options.keyMappings){
            if(km!=null&&(custom.equalsIgnoreCase(km.saveString())||custom.equalsIgnoreCase(km.getName())))return km;
        }
        return null;
    }
    private static void releaseTapeMouseKeys(Minecraft mc){
        if(mc==null||mc.options==null)return;
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyDrop.setDown(false);
        if(tapeCustomMapping!=null){
            tapeCustomMapping.setDown(false);
            tapeCustomMapping=null;
        }
    }
    private static ItemStack hwPendingStackSnapshot=ItemStack.EMPTY;
    private static boolean isStackOnCooldown(Player player,ItemStack stack){
        if(player==null||stack==null||stack.isEmpty())return false;
        try{return @@ITEM_ON_COOLDOWN@@;}catch(Throwable ignored){return false;}
    }
    private static boolean isItemOnCooldown(Minecraft mc,net.minecraft.world.item.Item item){
        if(mc==null||mc.player==null||item==null)return false;
        try{return @@ITEM_COOLDOWN_BY_ITEM@@;}catch(Throwable ignored){return false;}
    }
    private static int countInventoryItem(Minecraft mc,net.minecraft.world.item.Item item){
        if(mc==null||mc.player==null||item==null)return 0;
        int total=0;
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        for(int i=0;i<36;i++){
            ItemStack st=inv.getItem(i);
            if(!st.isEmpty()&&st.getItem()==item)total+=st.getCount();
        }
        ItemStack off=mc.player.getOffhandItem();
        if(!off.isEmpty()&&off.getItem()==item)total+=off.getCount();
        return total;
    }
    private static void pollHwTrapActionTimer(Minecraft mc,ClientRuntime runtime){
        if(mc==null||mc.player==null||runtime==null||runtime.hwhelper()==null)return;
        Settings.HwHelper hw=runtime.settings().hwhelper;
        HwPvpAndEventsLogic.TrapActionTimer actionTimer=runtime.hwhelper().trapActionTimer();
        if(FeaturePolicy.isBlocked("hwhelper.timers")||hw==null||!hw.enabled||hw.timers==null||!hw.timers.enabled||!hw.timers.actionbarTimer){
            actionTimer.reset();
            return;
        }
        long nowMs=System.currentTimeMillis();
        boolean expCd=hw.timers.explosiveTraps&&isItemOnCooldown(mc,net.minecraft.world.item.Items.PRISMARINE_SHARD);
        int expCount=countInventoryItem(mc,net.minecraft.world.item.Items.PRISMARINE_SHARD);
        boolean trapCd=hw.timers.traps&&isItemOnCooldown(mc,net.minecraft.world.item.Items.POPPED_CHORUS_FRUIT);
        int trapCount=countInventoryItem(mc,net.minecraft.world.item.Items.POPPED_CHORUS_FRUIT);
        HwPvpAndEventsLogic.HwConsumableKind triggered=actionTimer.pollInventory(expCd,expCount,trapCd,trapCount,nowMs);
        if(triggered!=null){
            runtime.hwhelper().timerTracker().onOwnConsumableUsed(
                triggered,mc.player.getX(),mc.player.getY(),mc.player.getZ(),hw.timers,nowMs);
        }
        String bar=actionTimer.nextActionbarText(nowMs,runtime.settings().language);
        if(bar!=null){
            setActionbarMessage(mc,bar);
        }
    }
    private static void handleTreasureCompassChat(Minecraft mc,ClientRuntime runtime,String rawPlain){
        if(mc==null||mc.player==null||mc.level==null||runtime==null||rawPlain==null||rawPlain.isEmpty())return;
        Settings.HwHelper hw=runtime.settings().hwhelper;
        if(FeaturePolicy.isBlocked("hwhelper.compassWaypoint")||FeaturePolicy.isBlocked("waypoints")||hw==null||!hw.enabled||!hw.compassWaypoint)return;
        int[] coords=HwPvpAndEventsLogic.parseTreasureCompassCoords(rawPlain);
        if(coords==null)return;
        Settings.Waypoints wps=runtime.settings().waypoints;
        if(wps==null)return;
        int wx=coords[0];
        int wy=coords[1]==Integer.MIN_VALUE?(int)Math.floor(mc.player.getY()):coords[1];
        int wz=coords[2];
        String dim=@@DIMENSION@@;
        String worldId=currentWorldId();
        String worldLabel=currentWorldLabel();
        if(wps.points!=null){
            for(Iterator<Waypoint> it=wps.points.iterator();it.hasNext();){
                Waypoint existing=it.next();
                if(existing!=null&&("Сокровище".equals(existing.label)||"Treasure".equals(existing.label))
                    &&dim.equals(existing.dimension)&&existing.matchesWorld(worldId)){
                    it.remove();
                }
            }
        }
        String lang=dev.nightclient.i18n.I18n.language(runtime.settings().language);
        String wpLabel="ru".equals(lang)?"Сокровище":"Treasure";
        Waypoint created=wps.add(wpLabel,wx+0.5,wy,wz+0.5,dim,worldId,worldLabel);
        if(created!=null){
            created.color=0xfff59e0b;
            created.icon="minecraft:compass";
            wps.enabled=true;
            runtime.changed();
            Map<String,Object> args=new LinkedHashMap<String,Object>(4);
            args.put("x",Integer.valueOf(wx));
            args.put("y",Integer.valueOf(wy));
            args.put("z",Integer.valueOf(wz));
            sendChatMessage(dev.nightclient.i18n.I18n.format(lang,"hwhelper.msg.treasure_waypoint",args));
        }
    }
    private static void pollPendingHwUse(Minecraft mc,ClientRuntime runtime){
        if(mc==null||mc.player==null||runtime==null||runtime.hwhelper()==null)return;
        HwPvpAndEventsLogic.HwTimerTracker tracker=runtime.hwhelper().timerTracker();
        if(!tracker.hasPendingUse())return;
        Settings.HwHelper hw=runtime.settings().hwhelper;
        if(FeaturePolicy.isBlocked("hwhelper.timers")||hw==null||!hw.enabled||hw.timers==null||!hw.timers.enabled)return;
        int slot=tracker.pendingSlot();
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        ItemStack cur=slot==40?mc.player.getOffhandItem():(slot>=0&&slot<36?inv.getItem(slot):ItemStack.EMPTY);
        String curId=cur==null||cur.isEmpty()?"":itemId(cur);
        int curCount=cur==null||cur.isEmpty()?0:cur.getCount();
        boolean cd=isStackOnCooldown(mc.player,cur)||isStackOnCooldown(mc.player,hwPendingStackSnapshot);
        tracker.pollPendingUse(curId,curCount,cd,hw.timers,System.currentTimeMillis());
        if(!tracker.hasPendingUse())hwPendingStackSnapshot=ItemStack.EMPTY;
    }
    private static void syncHwHolograms(Minecraft mc,ClientRuntime runtime){
        if(mc==null||mc.level==null||mc.player==null||runtime==null||runtime.hwhelper()==null)return;
        Settings.HwHelper hw=runtime.settings().hwhelper;
        if(FeaturePolicy.isBlocked("hwhelper.timers")||hw==null||!hw.enabled||hw.timers==null||!hw.timers.enabled)return;
        if(Math.abs(mc.player.tickCount-lastHwHoloScanTick)<4)return;
        lastHwHoloScanTick=mc.player.tickCount;
        long nowMs=System.currentTimeMillis();
        HwPvpAndEventsLogic.HwTimerTracker tracker=runtime.hwhelper().timerTracker();
        for(Entity e:mc.level.entitiesForRendering()){
            if(e==null||!e.isAlive()||distanceSquared(e,mc.player)>1764.0)continue;
            @@HOLOGRAM_TEXT_EXPR@@
            if(s!=null&&!s.isEmpty()){
                tracker.onHologramText(s,e.getX(),e.getY(),e.getZ(),hw.timers,nowMs);
            }
        }
        tracker.pruneMissingHolograms(nowMs);
    }
    private static boolean isHolyWorldServer(){
        String srv=SessionBridge.currentServerLabel();
        if(srv==null||srv.isEmpty())return false;
        String lower=srv.toLowerCase(Locale.ROOT);
        return lower.contains("holyworld")||lower.contains("holy-world")||lower.contains("play.hw");
    }
    private static boolean isExplosiveThingStack(ItemStack st,boolean onHolyWorld){
        if(st==null||st.isEmpty())return false;
        net.minecraft.world.item.Item it=st.getItem();
        if(it!=net.minecraft.world.item.Items.FIRE_CHARGE
            &&it!=net.minecraft.world.item.Items.FIREWORK_STAR
            &&it!=net.minecraft.world.item.Items.TNT
            &&it!=net.minecraft.world.item.Items.GUNPOWDER){
            return false;
        }
        return HwPvpAndEventsLogic.isExplosiveThing(
            itemId(st),st.getHoverName().getString(),readLore(st),extractKringeType(st),onHolyWorld);
    }
    private static int countExplosiveThings(Minecraft mc,boolean onHolyWorld,boolean[] anyOnVanillaCdOut){
        if(mc==null||mc.player==null)return 0;
        int total=0;
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        for(int i=0;i<36;i++){
            ItemStack st=inv.getItem(i);
            if(isExplosiveThingStack(st,onHolyWorld)){
                total+=st.getCount();
                if(anyOnVanillaCdOut!=null&&!anyOnVanillaCdOut[0]&&isStackOnCooldown(mc.player,st)){
                    anyOnVanillaCdOut[0]=true;
                }
            }
        }
        ItemStack off=mc.player.getOffhandItem();
        if(isExplosiveThingStack(off,onHolyWorld)){
            total+=off.getCount();
            if(anyOnVanillaCdOut!=null&&!anyOnVanillaCdOut[0]&&isStackOnCooldown(mc.player,off)){
                anyOnVanillaCdOut[0]=true;
            }
        }
        return total;
    }
    private static void pollExplosiveThingCooldown(Minecraft mc,ClientRuntime runtime){
        if(mc==null||mc.player==null||runtime==null||runtime.hwhelper()==null)return;
        HwPvpAndEventsLogic.ExplosiveThingCooldownTracker tracker=runtime.hwhelper().explosiveThingCooldown();
        if(!tracker.hasPending())return;
        boolean onHw=isHolyWorldServer();
        boolean[] stackCd=new boolean[]{false};
        int totalCount=countExplosiveThings(mc,onHw,stackCd);
        int slot=tracker.pendingSlot();
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        ItemStack cur=slot==40?mc.player.getOffhandItem():(slot>=0&&slot<36?inv.getItem(slot):ItemStack.EMPTY);
        String curId=cur==null||cur.isEmpty()?"":itemId(cur);
        int curCount=cur==null||cur.isEmpty()?0:cur.getCount();
        boolean vanillaCd=stackCd[0]||isItemOnCooldown(mc,net.minecraft.world.item.Items.FIRE_CHARGE)||isStackOnCooldown(mc.player,cur);
        tracker.pollInventory(curId,curCount,totalCount,vanillaCd,System.currentTimeMillis());
    }
    private static void registerPendingHwUse(Minecraft mc,ClientRuntime runtime,Player player,int slot,ItemStack stack,double tx,double ty,double tz){
        if(mc==null||player!=mc.player||runtime==null||runtime.hwhelper()==null||stack==null||stack.isEmpty())return;
        GameBridge.rememberCooldownItem(stack);
        String id=itemId(stack);
        String name=stack.getHoverName().getString();
        String lore=readLore(stack);
        boolean onCd=isStackOnCooldown(player,stack);
        boolean onHw=isHolyWorldServer();
        String nbtType=extractKringeType(stack);
        if(HwPvpAndEventsLogic.isExplosiveThing(id,name,lore,nbtType,onHw)){
            boolean[] stackCd=new boolean[]{false};
            int totalCount=countExplosiveThings(mc,onHw,stackCd);
            boolean fireCd=onCd||stackCd[0]||isItemOnCooldown(mc,net.minecraft.world.item.Items.FIRE_CHARGE);
            runtime.hwhelper().explosiveThingCooldown().onRightClick(
                id,name,lore,nbtType,onHw,slot,stack.getCount(),totalCount,fireCd,System.currentTimeMillis());
        }
        Settings.HwHelper hw=runtime.settings().hwhelper;
        if(FeaturePolicy.isBlocked("hwhelper.timers")||hw==null||!hw.enabled||hw.timers==null||!hw.timers.enabled)return;
        HwPvpAndEventsLogic.HwConsumableKind kind=HwPvpAndEventsLogic.classifyHwConsumable(id,name,lore);
        if(kind==null)return;
        if(onCd)return;
        hwPendingStackSnapshot=stack.copy();
        runtime.hwhelper().timerTracker().beginPendingUse(
            kind,slot,id,stack.getCount(),false,tx,ty,tz,hw.timers,System.currentTimeMillis());
    }
    public static void onUseHandItem(Player player,net.minecraft.world.InteractionHand hand){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(mc==null||player!=mc.player||runtime==null||hand==null)return;
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        int slot=hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@@GET_SELECTED_SLOT@@;
        registerPendingHwUse(mc,runtime,player,slot,player.getItemInHand(hand),player.getX(),player.getY(),player.getZ());
    }
    public static void onUseHandItemOn(net.minecraft.client.player.LocalPlayer player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(mc==null||player!=mc.player||runtime==null||hand==null||hit==null)return;
        if(mc.level!=null&&!player.isShiftKeyDown()){
            net.minecraft.world.level.block.Block blk=mc.level.getBlockState(hit.getBlockPos()).getBlock();
            if(blk instanceof net.minecraft.world.level.block.BaseEntityBlock
                ||blk instanceof net.minecraft.world.level.block.CraftingTableBlock
                ||blk instanceof net.minecraft.world.level.block.AnvilBlock
                ||blk instanceof net.minecraft.world.level.block.EnderChestBlock){
                return;
            }
        }
        net.minecraft.world.entity.player.Inventory inv=@@PLAYER_INVENTORY@@;
        int slot=hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@@GET_SELECTED_SLOT@@;
        net.minecraft.core.BlockPos bp=hit.getBlockPos();
        registerPendingHwUse(mc,runtime,player,slot,player.getItemInHand(hand),bp.getX()+0.5,bp.getY()+1.0,bp.getZ()+0.5);
    }
    @@HITBOX_RENDER@@
}
