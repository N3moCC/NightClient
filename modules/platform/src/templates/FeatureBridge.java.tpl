package dev.nightclient.platform;

import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TntView;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.runtime.ClientRuntime;
import dev.nightclient.platform.mixin.ChatScreenAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
@@FEATURE_IMPORTS@@

public final class FeatureBridge {
    private static KeyMapping zoomKey;
    private static Object inventoryPlayer,tntPlayer,tntLevel;
    private static int inventoryTick=Integer.MIN_VALUE,tntTick=Integer.MIN_VALUE;
    private static final ItemStack[] stacks=new ItemStack[36];
    private static List<InventorySlot> slots=Collections.emptyList();
    private static final List<PrimedTnt> primed=new ArrayList<PrimedTnt>(32);
    private static final Comparator<PrimedTnt> nearestTnt=new Comparator<PrimedTnt>(){public int compare(PrimedTnt a,PrimedTnt b){
        Entity player=Minecraft.getInstance().player;double da=distanceSquared(a,player),db=distanceSquared(b,player);
        int distance=Double.compare(da,db);return distance==0?Integer.compare(a.getId(),b.getId()):distance;
    }};
    private static final PriorityQueue<PrimedTnt> candidates=new PriorityQueue<PrimedTnt>(32,Collections.reverseOrder(nearestTnt));
    private static final ThreadLocal<LinkedHashMap<FormattedCharSequence,SequenceCache>> protectedSequences=new ThreadLocal<LinkedHashMap<FormattedCharSequence,SequenceCache>>(){
        @Override protected LinkedHashMap<FormattedCharSequence,SequenceCache> initialValue(){return new LinkedHashMap<FormattedCharSequence,SequenceCache>(64,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<FormattedCharSequence,SequenceCache> entry){return size()>256;}
        };}
    };
    private static final class SequenceCache{
        final String nickname,alias;final FormattedCharSequence output;
        SequenceCache(String nickname,String alias,FormattedCharSequence output){this.nickname=nickname;this.alias=alias;this.output=output;}
    }
    private FeatureBridge(){}

    public static KeyMapping zoomKey(){
        if(zoomKey==null)zoomKey=@@ZOOM_KEY@@;
        return zoomKey;
    }
    public static void pollZoom(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();

        if(inventoryPlayer!=null && (inventoryPlayer!=mc.player || runtime==null || FeaturePolicy.isBlocked("inventory") || !runtime.settings().inventory.enabled))clearInventory();
        if(tntPlayer!=null && (tntPlayer!=mc.player || tntLevel!=mc.level || runtime==null || FeaturePolicy.isBlocked("tnt") || !runtime.settings().tnt.enabled))clearTnt();
        if(runtime!=null)runtime.setZoomHeld(!FeaturePolicy.isBlocked("zoom") && runtime.settings().zoom.enabled && mc.level!=null && mc.player!=null && @@SCREEN@@==null && mc.isWindowActive() && zoomKey().isDown());
    }
    private static void clearInventory(){Arrays.fill(stacks,null);slots=Collections.emptyList();inventoryPlayer=null;inventoryTick=Integer.MIN_VALUE;}
    private static void clearTnt(){primed.clear();candidates.clear();tntPlayer=null;tntLevel=null;tntTick=Integer.MIN_VALUE;}
    public static void resourceReload(){protectedSequences.remove();}
    public static boolean zoomScroll(double vertical){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("zoom") || runtime==null || !runtime.settings().zoom.enabled || !runtime.settings().zoom.scroll || mc.player==null || mc.level==null || @@SCREEN@@!=null || !mc.isWindowActive() || !zoomKey().isDown())return false;
        runtime.setZoomHeld(true);runtime.adjustZoom(vertical);return true;
    }
    public static double zoomFov(double original){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null)return original;
        pollZoom();float factor=runtime.zoomFactor();
        double result=factor<=1.00001f?original:Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(original)*.5)/factor));
        runtime.effectiveFov((float)result);return result;
    }
    public static double zoomSensitivity(double original){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null)return original;
        pollZoom();float factor=runtime.zoomFactor();if(factor<=1.00001f)return original;

        return ((original*.6+.2)/Math.cbrt(factor)-.2)/.6;
    }
    public static boolean customCrosshair(){
        ClientRuntime runtime=ClientRuntime.get();
        return !FeaturePolicy.isBlocked("crosshair") && runtime!=null && runtime.settings().crosshair.enabled && crosshairVisible();
    }
    public static boolean crosshairVisible(){Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();return mc.player!=null && mc.level!=null && (mc.options.getCameraType().isFirstPerson() || (!FeaturePolicy.isBlocked("crosshair") && runtime!=null && runtime.settings().crosshair.enabled && runtime.settings().crosshair.thirdPerson));}
    public static boolean crosshairTargetHit(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("crosshair") || mc.player==null || mc.level==null || runtime==null)return false;
        dev.nightclient.config.Settings.Crosshair cfg=runtime.settings().crosshair;
        if(!cfg.enabled || (!cfg.targetHighlight && !cfg.targetShapeEnabled))return false;
        if(cfg.targetRange>0.01f){
            return hasCrosshairTargetInRange(mc,cfg.targetRange,cfg.targetMobs,cfg.targetHostileOnly);
        }
        Entity target=mc.crosshairPickEntity;
        if(target==null && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult)target=((net.minecraft.world.phys.EntityHitResult)mc.hitResult).getEntity();
        return isCrosshairTargetEntity(mc,target,cfg.targetMobs,cfg.targetHostileOnly);
    }
    private static boolean isCrosshairTargetEntity(Minecraft mc,Entity target,boolean mobs,boolean hostileOnly){
        if(target==null || target==mc.player || !target.isAlive() || target.isSpectator())return false;
        if(target.isInvisible() && !WorldFeatureBridge.isInvisibleArmorPlayer(target))return false;
        if(target instanceof net.minecraft.world.entity.player.Player)return true;
        if(!mobs || !(target instanceof net.minecraft.world.entity.LivingEntity))return false;
        return !hostileOnly || target instanceof net.minecraft.world.entity.monster.Enemy;
    }
    private static boolean hasCrosshairTargetInRange(Minecraft mc,double maxRange,boolean mobs,boolean hostileOnly){
        net.minecraft.world.phys.Vec3 eye=mc.player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 look=mc.player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end=eye.add(look.x*maxRange,look.y*maxRange,look.z*maxRange);
        double bestDistSq=maxRange*maxRange;
        net.minecraft.world.phys.HitResult blockHit=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(blockHit!=null && blockHit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){
            end=blockHit.getLocation();
            bestDistSq=eye.distanceToSqr(end);
        }
        net.minecraft.world.phys.AABB search=mc.player.getBoundingBox().expandTowards(look.x*maxRange,look.y*maxRange,look.z*maxRange).inflate(1.0D);
        for(Entity e:mc.level.getEntities(mc.player,search,ent->isCrosshairTargetEntity(mc,ent,mobs,hostileOnly))){
            net.minecraft.world.phys.AABB box=e.getBoundingBox().inflate((double)e.getPickRadius());
            if(box.contains(eye))return true;
            java.util.Optional<net.minecraft.world.phys.Vec3> hit=box.clip(eye,end);
            if(hit.isPresent() && eye.distanceToSqr(hit.get())<=bestDistSq)return true;
        }
        return false;
    }
    public static String ownNickname(){return Minecraft.getInstance().getUser().getName();}
    public static String protectInput(EditBox owner,String original){
        ClientRuntime runtime=ClientRuntime.get();Minecraft mc=Minecraft.getInstance();
        if(runtime==null || !( @@SCREEN@@ instanceof ChatScreen) || ((ChatScreenAccessor)(Object)@@SCREEN@@).nightclient$input()!=owner)return original;
        return runtime.maskPasswordInput(original);
    }
    public static boolean maskingChatPassword(){
        ClientRuntime runtime=ClientRuntime.get();Minecraft mc=Minecraft.getInstance();
        if(runtime==null || !( @@SCREEN@@ instanceof ChatScreen))return false;
        EditBox input=((ChatScreenAccessor)(Object)@@SCREEN@@).nightclient$input();
        if(input==null)return false;String candidate=input.getValue()+"x";
        return !candidate.equals(runtime.maskPasswordInput(candidate));
    }
    public static String protectSuggestion(EditBox owner,String original){
        if(original==null)return null;
        Minecraft mc=Minecraft.getInstance();
        return @@SCREEN@@ instanceof ChatScreen && ((ChatScreenAccessor)(Object)@@SCREEN@@).nightclient$input()==owner && maskingChatPassword()?null:original;
    }
    public static String protectText(String original){
        ClientRuntime runtime=ClientRuntime.get();return runtime==null?original:runtime.maskNickname(original);
    }
    public static FormattedCharSequence protectText(FormattedCharSequence original){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("protect") || original==null || runtime==null || !runtime.settings().protect.enabled || !runtime.settings().protect.hideNickname)return original;
        final String nick=ownNickname();
        if(nick==null || nick.isEmpty())return original;
        final String alias=runtime.settings().protect.replacement==null||runtime.settings().protect.replacement.isEmpty()?"NightProtect":runtime.settings().protect.replacement;
        LinkedHashMap<FormattedCharSequence,SequenceCache> cache=protectedSequences.get();SequenceCache cached=cache.get(original);
        if(cached!=null && cached.nickname.equals(nick) && cached.alias.equals(alias))return cached.output;
        final StringBuilder text=new StringBuilder();final List<Style> styles=new ArrayList<Style>();
        original.accept((index,style,codepoint)->{text.appendCodePoint(codepoint);styles.add(style);if(Character.charCount(codepoint)==2)styles.add(style);return true;});
        final String source=text.toString(),masked=runtime.maskNickname(source);
        if(source.equals(masked)){cache.put(original,new SequenceCache(nick,alias,original));return original;}

        final List<FormattedCharSequence> parts=new ArrayList<FormattedCharSequence>();
        int sourceIndex=0;
        while(sourceIndex<source.length()){
            int matchEnd=dev.nightclient.config.DisplayProtection.findNicknameEnd(source,sourceIndex,nick);
            if(matchEnd<0){
                int cp=source.codePointAt(sourceIndex),length=Character.charCount(cp);
                parts.add(FormattedCharSequence.forward(source.substring(sourceIndex,sourceIndex+length),styles.get(sourceIndex)));
                sourceIndex+=length;continue;
            }
            parts.add(FormattedCharSequence.forward(alias,styles.get(sourceIndex)));
            sourceIndex=matchEnd;
        }
        FormattedCharSequence result=FormattedCharSequence.composite(parts);cache.put(original,new SequenceCache(nick,alias,result));return result;
    }
    public static FormattedText protectText(FormattedText original){
        ClientRuntime runtime=ClientRuntime.get();
        if(FeaturePolicy.isBlocked("protect") || original==null || runtime==null || !runtime.settings().protect.enabled || !runtime.settings().protect.hideNickname)return original;
        final List<FormattedCharSequence> sequences=new ArrayList<FormattedCharSequence>();
        original.visit((style,text)->{sequences.add(FormattedCharSequence.forward(text,style));return Optional.empty();},Style.EMPTY);
        FormattedCharSequence source=FormattedCharSequence.composite(sequences),masked=protectText(source);
        if(masked==source)return original;
        final List<FormattedText> parts=new ArrayList<FormattedText>();
        masked.accept((index,style,codepoint)->{parts.add(FormattedText.of(new String(Character.toChars(codepoint)),style));return true;});
        return FormattedText.composite(parts);
    }
    private static boolean isNameBoundary(String text,int start,int length){
        return (start==0 || !isNameChar(text.charAt(start-1))) && (start+length==text.length() || !isNameChar(text.charAt(start+length)));
    }
    private static boolean isNameChar(char c){return c=='_' || Character.isLetterOrDigit(c);}

    public static List<InventorySlot> inventory(){prepareInventory();return slots;}
    public static ItemStack inventoryStack(int slot){prepareInventory();return slot>=0 && slot<36 && stacks[slot]!=null?stacks[slot]:ItemStack.EMPTY;}
    private static void prepareInventory(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(mc.player==null || runtime==null || FeaturePolicy.isBlocked("inventory") || !runtime.settings().inventory.enabled){
            if(inventoryPlayer!=null)clearInventory();return;
        }
        if(inventoryPlayer==mc.player && inventoryTick==mc.player.tickCount)return;
        inventoryPlayer=mc.player;inventoryTick=mc.player.tickCount;
        Inventory inventory=@@INVENTORY@@;List<InventorySlot> next=new ArrayList<InventorySlot>(36);
        for(int slot=0;slot<36;slot++){
            ItemStack stack=inventory.getItem(slot);stacks[slot]=stack.isEmpty()?ItemStack.EMPTY:stack.copy();
            if(stack.isEmpty())next.add(new InventorySlot(slot,"minecraft:air","",0,0,0));
            else next.add(new InventorySlot(slot,@@ITEM_ID@@,stack.getHoverName().getString(),stack.getCount(),stack.getDamageValue(),stack.getMaxDamage(),stack.isEnchanted()));
        }
        slots=Collections.unmodifiableList(next);
    }

    private static void prepareTnt(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(mc.player==null || mc.level==null || runtime==null || FeaturePolicy.isBlocked("tnt") || !runtime.settings().tnt.enabled){clearTnt();return;}
        if(tntPlayer==mc.player && tntLevel==mc.level && tntTick==mc.player.tickCount)return;
        tntPlayer=mc.player;tntLevel=mc.level;tntTick=mc.player.tickCount;primed.clear();candidates.clear();
        double range=runtime.settings().tnt.range,rangeSquared=range*range;
        for(Entity entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof PrimedTnt) || !entity.isAlive())continue;
            double dx=entity.getX()-mc.player.getX(),dy=entity.getY()-mc.player.getY(),dz=entity.getZ()-mc.player.getZ();
            if(dx*dx+dy*dy+dz*dz>rangeSquared)continue;
            PrimedTnt tnt=(PrimedTnt)entity;
            if(candidates.size()<32)candidates.add(tnt);
            else if(nearestTnt.compare(tnt,candidates.peek())<0){candidates.poll();candidates.add(tnt);}
        }
        primed.addAll(candidates);candidates.clear();Collections.sort(primed,nearestTnt);
    }
    public static boolean hasTnt(){prepareTnt();return !primed.isEmpty();}
    private static double distanceSquared(Entity a,Entity b){double x=a.getX()-b.getX(),y=a.getY()-b.getY(),z=a.getZ()-b.getZ();return x*x+y*y+z*z;}
    public static List<TntView> tnts(float partialTick,int width,int height){
        prepareTnt();if(primed.isEmpty())return Collections.emptyList();
        Minecraft mc=Minecraft.getInstance();
        float delta=mc.isPaused() || !Float.isFinite(partialTick)?0:Math.max(0,Math.min(1,partialTick));
        Vec3 player=mc.player.getPosition(delta);
        List<TntView> result=new ArrayList<TntView>(primed.size());
        for(PrimedTnt tnt:primed){
            if(!tnt.isAlive() || tnt.@@TNT_FUSE@@()<=0)continue;
            Vec3 position=tnt.getPosition(delta);
            double x=position.x()-player.x(),y=position.y()-player.y(),z=position.z()-player.z();

            result.add(new TntView(tnt.getId(),0,0,Math.max(0,(tnt.@@TNT_FUSE@@()-delta)/20f),(float)Math.sqrt(x*x+y*y+z*z),false));
        }
        return result;
    }
}
