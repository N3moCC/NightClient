package dev.nightclient.platform;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TntView;
import dev.nightclient.api.Keys;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.renderer.SurfacePainter;
import dev.nightclient.platform.mixin.CooldownEntryAccessor;
import dev.nightclient.platform.mixin.CooldownStateAccessor;
import dev.nightclient.runtime.ClientRuntime;
import dev.nightclient.runtime.PlatformServices;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.@@IDENTIFIER@@;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
@@GAME_IMPORTS@@

public final class GameBridge implements PlatformServices {
    public static String title(){return dev.nightclient.i18n.I18n.text(ClientRuntime.language(),"app.name");}
    private static KeyMapping menuKey;
    private static KeyMapping waypointKey;
    private static KeyMapping worldMapKey;
    private static KeyMapping mapSettingsKey;
    private static KeyMapping mapEnlargeKey;
    private static KeyMapping waypointsMenuKey;
    private static final Map<String,KeyMapping> moduleKeys=new HashMap<String,KeyMapping>();
    private static final Map<String,String> moduleKeyNames=new HashMap<String,String>();
    private final Map<Object, ItemInfo> groupItems = new HashMap<Object, ItemInfo>();
    private static final Map<Object, ItemInfo> rememberedItems = new HashMap<Object, ItemInfo>();
    private WeakReference<Object> previousPlayer;
    private int previousCooldownTick=Integer.MIN_VALUE;
    private int lastRememberTick=Integer.MIN_VALUE;
    private static final class ItemInfo {
        final String id,name;
        ItemInfo(String id,String name){this.id=id;this.name=name;}
    }
    public static void rememberCooldownItem(ItemStack stack){
        if(stack==null || stack.isEmpty())return;
        @@REMEMBER_COOLDOWN_ITEM@@
    }
    @@KEY_CATEGORY_DECLARATION@@
    public static KeyMapping menuKey() {
        if(menuKey==null)menuKey=@@KEY_CONSTRUCTION@@;
        return menuKey;
    }
    public static KeyMapping waypointKey() {
        if(waypointKey==null)waypointKey=@@WAYPOINT_KEY_CONSTRUCTION@@;
        return waypointKey;
    }
    public static KeyMapping worldMapKey() {
        if(worldMapKey==null)worldMapKey=@@WORLD_MAP_KEY_CONSTRUCTION@@;
        return worldMapKey;
    }
    public static KeyMapping mapSettingsKey() {
        if(mapSettingsKey==null)mapSettingsKey=@@MAP_SETTINGS_KEY_CONSTRUCTION@@;
        return mapSettingsKey;
    }
    public static KeyMapping mapEnlargeKey() {
        if(mapEnlargeKey==null)mapEnlargeKey=@@MAP_ENLARGE_KEY_CONSTRUCTION@@;
        return mapEnlargeKey;
    }
    public static KeyMapping waypointsMenuKey() {
        if(waypointsMenuKey==null)waypointsMenuKey=@@WAYPOINTS_MENU_KEY_CONSTRUCTION@@;
        return waypointsMenuKey;
    }
    public static void pollMenuKey(){
        pollModuleKeys();
        while(menuKey().consumeClick()){
            Minecraft mc=Minecraft.getInstance();
            if(@@SCREEN_ACCESS@@==null && mc.player!=null)openMenu();
        }
        while(waypointKey().consumeClick()){
            Minecraft mc=Minecraft.getInstance();
            if(!FeaturePolicy.isBlocked("waypoints") && @@SCREEN_ACCESS@@==null && mc.player!=null)openWaypointCreate();
        }
        while(worldMapKey().consumeClick()){
            Minecraft mc=Minecraft.getInstance();
            ClientRuntime runtime=ClientRuntime.get();
            if(!FeaturePolicy.isBlocked("worldMap") && @@SCREEN_ACCESS@@==null && mc.player!=null && runtime!=null && runtime.settings().worldMap!=null && runtime.settings().worldMap.enabled)openWorldMapScreen();
        }
        while(mapSettingsKey().consumeClick()){
            Minecraft mc=Minecraft.getInstance();
            if(!(FeaturePolicy.isBlocked("minimap") && FeaturePolicy.isBlocked("worldMap")) && @@SCREEN_ACCESS@@==null && mc.player!=null)openMapSettingsScreen();
        }
        while(waypointsMenuKey().consumeClick()){
            Minecraft mc=Minecraft.getInstance();
            if(!FeaturePolicy.isBlocked("waypoints") && @@SCREEN_ACCESS@@==null && mc.player!=null)openWaypointsMenuScreen();
        }
        boolean enlargeClick=false;
        while(mapEnlargeKey().consumeClick())enlargeClick=true;
        Minecraft mcEnlarge=Minecraft.getInstance();
        ClientRuntime rtEnlarge=ClientRuntime.get();
        if(rtEnlarge!=null){
            boolean inGame=!FeaturePolicy.isBlocked("minimap") && @@SCREEN_ACCESS_ENLARGE@@==null && mcEnlarge.player!=null;
            rtEnlarge.updateMapEnlargeKey(inGame && mapEnlargeKey().isDown(), inGame && enlargeClick);
        }
    }
    private static KeyMapping moduleKey(String id){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null)return null;
        String name=runtime.settings().moduleBindings.get(id);if(name==null)name="key.keyboard.unknown";
        KeyMapping mapping=moduleKeys.get(id);
        if(mapping==null){mapping=@@MODULE_KEY_CONSTRUCTION@@;moduleKeys.put(id,mapping);}
        if(!name.equals(moduleKeyNames.get(id))){mapping.setDown(false);try{mapping.setKey(InputConstants.getKey(name));}catch(IllegalArgumentException invalid){mapping.setKey(InputConstants.getKey("key.keyboard.unknown"));}moduleKeyNames.put(id,name);KeyMapping.resetMapping();}
        return mapping;
    }
    private static void pollModuleKeys(){
        ClientRuntime runtime=ClientRuntime.get();Minecraft mc=Minecraft.getInstance();if(runtime==null)return;
        if(moduleKeys.isEmpty() && runtime.settings().moduleBindings.isEmpty())return;
        java.util.Set<String> ids=new java.util.HashSet<String>(moduleKeys.keySet());ids.addAll(runtime.settings().moduleBindings.keySet());
        for(String id:ids){KeyMapping mapping=moduleKey(id);if(mapping==null)continue;while(mapping.consumeClick())if(mc.player!=null&&@@SCREEN_ACCESS@@==null&&mc.isWindowActive())runtime.toggleModule(id);}
    }
    public static KeyMapping zoomKey(){return FeatureBridge.zoomKey();}
    public static void pollZoom(){FeatureBridge.pollZoom();WorldFeatureBridge.tick();CameraFeatureBridge.poll();}
    public static KeyMapping freelookKey(){return CameraFeatureBridge.freelookKey();}
    @Override public String ownNickname(){return FeatureBridge.ownNickname();}
    @Override public boolean crosshairVisible(){return FeatureBridge.crosshairVisible();}
    @Override public boolean crosshairTargetHit(){return FeatureBridge.crosshairTargetHit();}
    @Override public boolean fireVisible(){return WorldFeatureBridge.fireVisible();}
    @Override public boolean leftMainArm(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT;}
    public static ItemStack cachedInventoryStack(int slot){return slot>=400?WorldMapBridge.cachedRadarItemStack(slot):slot>=300?WorldFeatureBridge.cachedTooltipStack(slot):slot>=200?WorldFeatureBridge.cachedTargetStack(slot):slot>=100?WorldFeatureBridge.cachedArmorStack(slot):FeatureBridge.inventoryStack(slot);}
    @Override public List<InventorySlot> inventory(float partialTick){return FeatureBridge.inventory();}
    @Override public List<TntView> tnts(float partialTick,int width,int height){return FeatureBridge.tnts(partialTick,width,height);}
    @Override public boolean waypointAddHere(){return WorldFeatureBridge.markHere();}
    @Override public double[] playerPosition(){return WorldFeatureBridge.playerPosition();}
    @Override public dev.nightclient.renderer.WorldRibbon.View cameraView(int width,int height){return WorldFeatureBridge.cameraView(width,height);}
    @Override public String currentDimension(){return WorldFeatureBridge.currentDimension();}
    @Override public String currentWorldId(){return WorldFeatureBridge.currentWorldId();}
    @Override public String currentWorldLabel(){return WorldFeatureBridge.currentWorldLabel();}
    @Override public List<InventorySlot> armor(float partialTick){return WorldFeatureBridge.armor(partialTick);}
    @Override public List<dev.nightclient.api.PotionView> potions(float partialTick){return WorldFeatureBridge.potions(partialTick);}
    @Override public dev.nightclient.api.VitalsView vitals(float partialTick){return WorldFeatureBridge.vitals(partialTick);}
    @Override public dev.nightclient.api.TargetView localPlayer(float partialTick){return WorldFeatureBridge.localPlayer(partialTick);}
    @Override public dev.nightclient.api.PlayerSkin playerPreview(){return PlayerPreviewSkinReader.read();}
    @Override public dev.nightclient.api.TargetView target(float partialTick){return WorldFeatureBridge.target(partialTick);}
    @Override public List<dev.nightclient.api.PlayerHealthView> playerHealths(float partialTick){return WorldFeatureBridge.playerHealths(partialTick);}
    @Override public List<dev.nightclient.api.TotemCounterView> totemCounters(float partialTick){return WorldFeatureBridge.totemCounters(partialTick);}
    @Override public List<dev.nightclient.api.PlayerPingView> playerPings(float partialTick){return WorldFeatureBridge.playerPings(partialTick);}
    @Override public List<dev.nightclient.runtime.ExplosionRingLogic.HazardBadge> explosionBadges(float partialTick){return WorldFeatureBridge.explosionBadges(partialTick);}
    @Override public dev.nightclient.runtime.CombatTagDetector.CombatStatus combatStatus(){return WorldFeatureBridge.combatStatus();}
    @Override public dev.nightclient.runtime.BetterChatLogic.ChatToastStatus chatToast(){return WorldFeatureBridge.chatToast();}
    @Override public void playClientSound(String soundId,float volume,float pitch){WorldFeatureBridge.playClientSound(soundId,volume,pitch);}
    @Override public String crosshairBlockId(){return WorldFeatureBridge.crosshairBlockId();}
    @Override public dev.nightclient.api.ViewmodelSelection viewmodelSelection(float x,float y){return CameraFeatureBridge.selection(x,y);}
    @Override public void requestViewmodelPick(float x,float y){CameraFeatureBridge.requestPick(x,y);}
    @Override public boolean viewmodelEditorSupported(){return CameraFeatureBridge.supported();}
    @Override public void setViewmodelEditing(boolean value){CameraFeatureBridge.editing(value);}

    public static boolean isLoadingOverlay(){
        Minecraft mc=Minecraft.getInstance();
        return @@OVERLAY_ACCESS@@!=null;
    }

    public static void openMenu(){
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {if(ClientRuntime.get()!=null && @@OVERLAY_ACCESS@@==null && !(@@SCREEN_ACCESS@@ instanceof NightScreen))@@SET_SCREEN_OPEN@@;});
    }
    public static void openAccountManager(final Screen parent){
        if(FeaturePolicy.isBlocked("accountSwitcher"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            ClientRuntime runtime=ClientRuntime.get();
            if(FeaturePolicy.isBlocked("accountSwitcher") || runtime==null || @@OVERLAY_ACCESS@@!=null)return;
            if(runtime.settings()!=null && runtime.settings().accountSwitcher!=null
                    && "classic".equals(runtime.settings().accountSwitcher.layout)){
                @@SET_VANILLA_ACCOUNT_OPEN@@;
                return;
            }
            if(!(@@SCREEN_ACCESS@@ instanceof NightScreen))@@SET_SCREEN_PARENT_OPEN@@;
            runtime.menu().openAccountManagerStandalone();
        });
    }
    public static void openAccountManagerModern(final Screen parent){
        if(FeaturePolicy.isBlocked("accountSwitcher"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            ClientRuntime runtime=ClientRuntime.get();
            if(FeaturePolicy.isBlocked("accountSwitcher") || runtime==null || @@OVERLAY_ACCESS@@!=null)return;
            @@SET_SCREEN_PARENT_OPEN@@;
            runtime.menu().openAccountManagerStandalone();
        });
    }
    @Override public boolean openVanillaAccountScreen(){
        if(FeaturePolicy.isBlocked("accountSwitcher"))return false;
        final Minecraft mc=Minecraft.getInstance();
        if(mc==null)return false;
        Screen current=@@SCREEN_ACCESS@@;
        final Screen parent=current instanceof NightScreen ? ((NightScreen)current).parentScreen() : current;
        mc.execute(() -> {
            if(FeaturePolicy.isBlocked("accountSwitcher") || ClientRuntime.get()==null || @@OVERLAY_ACCESS@@!=null)return;
            @@SET_VANILLA_ACCOUNT_OPEN@@;
        });
        return true;
    }
    public static void openWaypointManager(){
        if(FeaturePolicy.isBlocked("waypoints"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            ClientRuntime runtime=ClientRuntime.get();
            if(FeaturePolicy.isBlocked("waypoints") || runtime==null || @@OVERLAY_ACCESS@@!=null || mc.player==null)return;
            if(!(@@SCREEN_ACCESS@@ instanceof NightScreen))@@SET_SCREEN_OPEN@@;
            runtime.menu().openWaypointManagerStandalone();
        });
    }
    public static void openWaypointCreate(){
        if(FeaturePolicy.isBlocked("waypoints"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            ClientRuntime runtime=ClientRuntime.get();
            if(FeaturePolicy.isBlocked("waypoints") || runtime==null || @@OVERLAY_ACCESS@@!=null || mc.player==null)return;
            double[] pos=WorldFeatureBridge.playerPosition();
            double px=pos!=null&&pos.length>=3?pos[0]:0.0;
            double py=pos!=null&&pos.length>=3?pos[1]:64.0;
            double pz=pos!=null&&pos.length>=3?pos[2]:0.0;
            Screen parent=null;
            @@SET_WAYPOINTS_CREATE_SCREEN_OPEN@@;
        });
    }
    public static void openWaypointCreateAt(double x,double y,double z){
        if(FeaturePolicy.isBlocked("waypoints"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            ClientRuntime runtime=ClientRuntime.get();
            if(FeaturePolicy.isBlocked("waypoints") || runtime==null || @@OVERLAY_ACCESS@@!=null || mc.player==null)return;
            double px=x,py=y,pz=z;
            Screen parent=null;
            @@SET_WAYPOINTS_CREATE_SCREEN_OPEN@@;
        });
    }
    @Override public void openWaypointCreate(double x,double y,double z){openWaypointCreateAt(x,y,z);}
    @Override public dev.nightclient.api.MapRadarModels.RadarSnapshot mapRadarSnapshot(float partialTick){return WorldMapBridge.radarSnapshot(partialTick);}
    public static void openWorldMapScreen(){
        if(FeaturePolicy.isBlocked("worldMap"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {if(!FeaturePolicy.isBlocked("worldMap") && ClientRuntime.get()!=null && mc.player!=null && @@OVERLAY_ACCESS@@==null && !(@@SCREEN_ACCESS@@ instanceof NightMapScreen))@@SET_MAP_SCREEN_OPEN@@;});
    }
    public static void closeWorldMapScreen(){
        Minecraft mc=Minecraft.getInstance();
        if(@@SCREEN_ACCESS@@ instanceof NightMapScreen)@@SET_SCREEN_CLOSE@@;
        while(worldMapKey().consumeClick()){}
    }
    @Override public void openWorldMap(){openWorldMapScreen();}
    @Override public void closeWorldMap(){closeWorldMapScreen();}
    @Override public boolean isWorldMapOpen(){Minecraft mc=Minecraft.getInstance();return @@SCREEN_ACCESS@@ instanceof NightMapScreen;}
    public static void openMapSettingsScreen(){
        if(FeaturePolicy.isBlocked("minimap") && FeaturePolicy.isBlocked("worldMap"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {if(!(FeaturePolicy.isBlocked("minimap") && FeaturePolicy.isBlocked("worldMap")) && ClientRuntime.get()!=null && mc.player!=null && @@OVERLAY_ACCESS@@==null && !(@@SCREEN_ACCESS@@ instanceof NightMapSettingsScreen))@@SET_MAP_SETTINGS_SCREEN_OPEN@@;});
    }
    public static void closeMapSettingsScreen(){
        Minecraft mc=Minecraft.getInstance();
        if(@@SCREEN_ACCESS@@ instanceof NightMapSettingsScreen)@@SET_SCREEN_CLOSE@@;
        while(mapSettingsKey().consumeClick()){}
    }
    public static void openWaypointsMenuScreen(){
        if(FeaturePolicy.isBlocked("waypoints"))return;
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {if(!FeaturePolicy.isBlocked("waypoints") && ClientRuntime.get()!=null && mc.player!=null && @@OVERLAY_ACCESS@@==null && !(@@SCREEN_ACCESS@@ instanceof NightWaypointsScreen))@@SET_WAYPOINTS_SCREEN_OPEN@@;});
    }
    public static void closeWaypointsMenuScreen(){
        Minecraft mc=Minecraft.getInstance();
        if(@@SCREEN_ACCESS@@ instanceof NightWaypointsScreen)@@SET_SCREEN_CLOSE@@;
        while(waypointsMenuKey().consumeClick()){}
        while(waypointKey().consumeClick()){}
    }
    public static void openHudEditorScreen(){
        final Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            ClientRuntime runtime=ClientRuntime.get();
            if(runtime==null || @@OVERLAY_ACCESS@@!=null || mc.player==null)return;
            if(!(@@SCREEN_ACCESS@@ instanceof NightScreen))@@SET_SCREEN_OPEN@@;
            runtime.menu().openEditor();
        });
    }
    @Override public void openMapSettings(){openMapSettingsScreen();}
    @Override public void closeMapSettings(){closeMapSettingsScreen();}
    @Override public void openWaypointsMenu(){openWaypointsMenuScreen();}
    @Override public void closeWaypointsMenu(){closeWaypointsMenuScreen();}
    @Override public void openHudEditor(){openHudEditorScreen();}
    @Override public void rescanLoadedMapChunks(){WorldMapBridge.rescanLoadedChunks();}
    @Override public Map<String,Object> networkInfo(){return WorldFeatureBridge.networkInfo();}
    @Override public int ping(){return WorldFeatureBridge.ping();}
    @Override public int fps(){return WorldFeatureBridge.fps();}
    @Override public Map<String,Object> keystrokes(){return WorldFeatureBridge.keystrokes();}
    @Override public Map<String,Object> coordinates(){return WorldFeatureBridge.coordinates();}
    @Override public Map<String,Object> scoreboard(){return WorldFeatureBridge.scoreboard();}
    @Override public int mainHandCount(){return WorldFeatureBridge.mainHandCount();}
    @Override public boolean isInCombat(){return WorldFeatureBridge.isInCombat();}
    @Override public boolean isInLobby(){return WorldFeatureBridge.isInLobby();}
    @Override public String openContainerTitle(){return WorldFeatureBridge.openContainerTitle();}
    @Override public List<String> containerSlotNames(){return WorldFeatureBridge.containerSlotNames();}
    @Override public List<dev.nightclient.runtime.PlatformServices.ContainerSlot> containerSlots(){return WorldFeatureBridge.containerSlots();}
    @Override public boolean clickContainerSlot(int slotIndex){return WorldFeatureBridge.clickContainerSlot(slotIndex);}
    @Override public boolean closeContainer(){return WorldFeatureBridge.closeContainer();}
    @Override public boolean useSelectorItem(){return WorldFeatureBridge.useSelectorItem();}
    @Override public void sendChatMessage(String message){WorldFeatureBridge.sendChatMessage(message);}
    @Override public void sendFeedback(String message){WorldFeatureBridge.sendChatMessage(message);}
    @Override public void sendCommandToServer(String command){WorldFeatureBridge.sendCommandToServer(command);}
    @Override public boolean sendPluginMessage(String channel,byte[] payload){return LiteApiBridge.send(channel,payload);}
    @Override public String lastChatMessage(){return WorldFeatureBridge.lastChatMessage();}
    @Override public Object lastChatToken(){return WorldFeatureBridge.lastChatToken();}
    @Override public boolean hasRecentChatMessage(String needle){return WorldFeatureBridge.hasRecentChatMessage(needle);}
    @Override public Object currentWorldToken(){return WorldFeatureBridge.currentWorldToken();}
    @Override public Path configDirectory(){return FabricLoader.getInstance().getConfigDir();}
    @Override public void updateCosmetics(int[] skinPixels,int skinW,int skinH,int skinRev,boolean capeOverride,boolean capeSuppressed,int[] capePixels,int capeW,int capeH,int capeRev,Boolean slimOverride){CosmeticsBridge.update(skinPixels,skinW,skinH,skinRev,capeOverride,capeSuppressed,capePixels,capeW,capeH,capeRev,slimOverride);}
    @Override public void updateCosmeticModels(dev.nightclient.renderer.model.CosmeticModelMesh weaponMesh,String weaponTarget,int weaponColor,float weaponScale,boolean weaponFirstPerson,boolean weaponThirdPerson,dev.nightclient.renderer.model.CosmeticModelMesh armorMesh,String armorTrigger,int armorSlotMask,boolean armorHideVanilla,int armorColor,float armorScale){CustomModelBridge.update(weaponMesh,weaponTarget,weaponColor,weaponScale,weaponFirstPerson,weaponThirdPerson,armorMesh,armorTrigger,armorSlotMask,armorHideVanilla,armorColor,armorScale);}
    @Override public String chooseImageFile(String title){return CosmeticsBridge.chooseImageFile(title);}
    @Override public String chooseModelFile(String title){return CustomModelBridge.chooseModelFile(title);}
    @Override public String chooseAudioFile(String title){return CosmeticsBridge.chooseAudioFile(title);}
    @Override public String launcherNickname(){return SessionBridge.launcherNickname();}
    @Override public boolean isLauncherSessionActive(){return SessionBridge.isLauncherActive();}
    @Override public boolean switchOfflineSession(String nickname,java.util.UUID offlineUuid){return SessionBridge.switchOffline(nickname,offlineUuid);}
    @Override public boolean restoreLauncherSession(){return SessionBridge.restoreLauncher();}
    @Override public boolean canReconnectServer(){return SessionBridge.canReconnectServer();}
    @Override public String currentServerLabel(){return SessionBridge.currentServerLabel();}
    @Override public boolean reconnectCurrentServer(){return SessionBridge.reconnectCurrentServer();}
    @Override public String gameVersion(){return "@@VERSION@@";}
    @Override public String clipboard(){return Minecraft.getInstance().keyboardHandler.getClipboard();}
    @Override public void clipboard(String value){Minecraft.getInstance().keyboardHandler.setClipboard(value==null?"":value);}
    @Override public String menuKeyLabel(){return menuKey().getTranslatedKeyMessage().getString();}
    private static KeyMapping binding(String id){if(id.startsWith("module:"))return moduleKey(id.substring(7));return "menu".equals(id)?menuKey():"zoom".equals(id)?zoomKey():"freelook".equals(id)?freelookKey():"waypoint".equals(id)?waypointKey():"worldMap".equals(id)?worldMapKey():"mapSettings".equals(id)?mapSettingsKey():"mapEnlarge".equals(id)?mapEnlargeKey():"waypointsMenu".equals(id)?waypointsMenuKey():null;}
    @Override public String keyBindingLabel(String id){
        if(id!=null&&id.startsWith("rawkey:")){
            String raw=id.substring(7);
            if(raw.isEmpty()||"key.keyboard.unknown".equals(raw))return "";
            try{return InputConstants.getKey(raw).getDisplayName().getString();}
            catch(IllegalArgumentException invalid){return "";}
        }
        KeyMapping mapping=binding(id);return mapping==null||mapping.isUnbound()?"":mapping.getTranslatedKeyMessage().getString();
    }
    @Override public void setKeyBinding(String id,String nativeKeyName){KeyMapping mapping=binding(id);if(mapping==null)return;InputConstants.Key key=InputConstants.getKey(nativeKeyName);mapping.setDown(false);mapping.setKey(key);KeyMapping.resetMapping();Minecraft.getInstance().options.save();}
    @Override public void hoverViewmodel(float x,float y){CameraFeatureBridge.hover(x,y);}
    @Override public void closeMenu(){
        Minecraft mc=Minecraft.getInstance();
        if(@@SCREEN_ACCESS@@ instanceof NightScreen){
            Screen parent=((NightScreen)@@SCREEN_ACCESS@@).parentScreen();
            @@SET_SCREEN_CLOSE_PARENT@@;
        }
        while(menuKey().consumeClick()){}
    }
    public static int mouseButton(int button){@@NORMALIZE_BUTTON@@}
    public static int modifiers(int nativeModifiers){@@NORMALIZE_MODIFIERS@@}
    public static int scrollModifiers(){
        return (@@SCROLL_MODIFIER_OWNER@@.hasControlDown()?Keys.MOD_CONTROL:0)
            | (@@SCROLL_MODIFIER_OWNER@@.hasShiftDown()?Keys.MOD_SHIFT:0);
    }
    public static int key(int nativeKey,int shortcutKey){@@NORMALIZE_KEY@@}
    public static void textFocus(Screen owner,boolean focused){@@TEXT_FOCUS@@}
    private static final java.util.concurrent.ConcurrentHashMap<String,@@IDENTIFIER@@> IDENTIFIERS=new java.util.concurrent.ConcurrentHashMap<String,@@IDENTIFIER@@>(128);
    public static @@IDENTIFIER@@ identifier(String id){
        @@IDENTIFIER@@ cached=IDENTIFIERS.get(id);
        if(cached!=null)return cached;
        @@IDENTIFIER@@ parsed=@@IDENTIFIER_PARSE@@;
        if(IDENTIFIERS.size()<2048)IDENTIFIERS.putIfAbsent(id,parsed);
        return parsed;
    }
    private static final java.util.Map<String,ItemStack> VARIANT_STACKS=new java.util.HashMap<String,ItemStack>();
    private static Item itemForId(String id){return @@REGISTRY@@.@@REGISTRY_GET@@(identifier(id));}
    public static ItemStack stack(String id){
        if(Minecraft.getInstance().player==null||id==null||id.isEmpty())return ItemStack.EMPTY;
        int hash=id.indexOf('#');
        if(hash<0){
            Item item=itemForId(id);
            return item==null || item==Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
        ItemStack cached=VARIANT_STACKS.get(id);
        if(cached!=null)return cached;
        String base=id.substring(0,hash);
        String variant=id.substring(hash+1);
        Item item=itemForId(base);
        if(item==null || item==Items.AIR)return ItemStack.EMPTY;
        ItemStack stack=new ItemStack(item);
        if(item instanceof net.minecraft.world.item.PotionItem && !variant.isEmpty()){
            try{@@APPLY_POTION_VARIANT@@}catch(Throwable ignored){}
        }
        if(VARIANT_STACKS.size()>64)VARIANT_STACKS.clear();
        VARIANT_STACKS.put(id,stack);
        return stack;
    }
    private static String itemId(Item item){return @@REGISTRY@@.getKey(item).toString();}
    private static String itemName(Item item){return item.getDefaultInstance().getHoverName().getString();}
    private static boolean hasActiveCooldowns(float partialTick){
        ClientRuntime runtime=ClientRuntime.get();
        if(runtime!=null && runtime.hwhelper()!=null && runtime.hwhelper().explosiveThingCooldown().hasActive(System.currentTimeMillis()))return true;
        Minecraft mc=Minecraft.getInstance();
        CooldownStateAccessor state=(CooldownStateAccessor)(Object)mc.player.getCooldowns();
        float delta=partialTick(mc,partialTick);
        for(Object value:state.nightclient$cooldowns().values()){
            CooldownEntryAccessor timer=(CooldownEntryAccessor)value;
            if(timer.nightclient$endTime()-state.nightclient$tickCount()>delta && timer.nightclient$endTime()-timer.nightclient$startTime()>0)return true;
        }
        return false;
    }
    @Override public List<Cooldown> cooldowns(float partialTick){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null){groupItems.clear();rememberedItems.clear();previousPlayer=null;previousCooldownTick=Integer.MIN_VALUE;lastRememberTick=Integer.MIN_VALUE;return Collections.emptyList();}
        if(previousPlayer==null || previousPlayer.get()!=mc.player){groupItems.clear();rememberedItems.clear();previousPlayer=new WeakReference<Object>(mc.player);previousCooldownTick=Integer.MIN_VALUE;lastRememberTick=Integer.MIN_VALUE;}
        ItemCooldowns nativeCooldowns=mc.player.getCooldowns();
        CooldownStateAccessor state=(CooldownStateAccessor)(Object)nativeCooldowns;
        @@REMEMBER_INVENTORY_ITEMS@@
        Map<?,?> active=state.nightclient$cooldowns();
        if(active.isEmpty()){groupItems.clear();previousCooldownTick=Integer.MIN_VALUE;return Collections.emptyList();}
        @@SCAN_GROUPS@@
        float delta=partialTick(mc,partialTick);
        List<Cooldown> result=new ArrayList<Cooldown>(active.size());
        for(Map.Entry<?,?> entry:active.entrySet()){
            CooldownEntryAccessor timer=(CooldownEntryAccessor)entry.getValue();
            int duration=timer.nightclient$endTime()-timer.nightclient$startTime();
            float remaining=(timer.nightclient$endTime()-state.nightclient$tickCount())-delta;
            if(duration<=0 || remaining<=0)continue;
            @@COOLDOWN_KEY@@
            result.add(new Cooldown(id,name,icon,remaining/20f,duration/20f));
        }
        return result;
    }
    private static float partialTick(Minecraft mc,float value){return mc.isPaused() || Float.isNaN(value)?0:Math.max(0,Math.min(1,value));}

    public static boolean backdropNeeded(){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null)return false;
        Minecraft mc=Minecraft.getInstance();
        dev.nightclient.config.Settings s=runtime.settings();
        if(@@SCREEN_ACCESS@@ instanceof NightScreen && (runtime.menu().usesBackdrop() || @@FALLBACK_GUI_BLUR_NEEDED@@))return true;
        if(@@SCREEN_ACCESS@@ instanceof net.minecraft.client.gui.screens.ChatScreen && runtime.chatHudUsesBackdrop())return true;
        if(@@SCREEN_ACCESS@@ instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen && !FeaturePolicy.isBlocked("tooltips") && s.tooltips!=null && s.tooltips.enabled && !"vanilla".equals(s.tooltips.style) && SurfacePainter.usesBackdrop(s.styleTheme("tooltips")))return true;
        if(mc.player==null)return false;
        if(s.hud.hideWithGameHud && @@GAME_HUD_HIDDEN@@)return false;
        if(runtime.skinHudUsesBackdrop())return true;
        if(!FeaturePolicy.isBlocked("hud") && s.hud.enabled && s.hud.background && (!s.hud.hideWhenEmpty || hasActiveCooldowns(0)) && SurfacePainter.usesBackdrop(s.styleTheme("cooldowns")))return true;
        if(!FeaturePolicy.isBlocked("inventory") && s.inventory.enabled && SurfacePainter.usesBackdrop(s.styleTheme("inventory")))return true;
        if(!FeaturePolicy.isBlocked("minimap") && s.minimap!=null && s.minimap.enabled && s.minimap.frameOpacity>0.01f && SurfacePainter.usesBackdrop(s.styleTheme("minimap")))return true;
        if(!FeaturePolicy.isBlocked("armor") && s.armor.enabled && SurfacePainter.usesBackdrop(s.styleTheme("armor")))return true;
        if(!FeaturePolicy.isBlocked("potions") && s.potions.enabled && SurfacePainter.usesBackdrop(s.styleTheme("potions")))return true;
        if(!FeaturePolicy.isBlocked("target") && s.target.enabled && SurfacePainter.usesBackdrop(s.styleTheme("target")))return true;
        if(!FeaturePolicy.isBlocked("scoreboard") && s.scoreboard!=null && s.scoreboard.enabled && SurfacePainter.usesBackdrop(s.styleTheme("scoreboard")))return true;
        if(!FeaturePolicy.isBlocked("coordinates") && s.coordinates!=null && s.coordinates.enabled && SurfacePainter.usesBackdrop(s.styleTheme("coordinates")))return true;
        if(!FeaturePolicy.isBlocked("keystrokes") && s.keystrokes!=null && s.keystrokes.enabled && SurfacePainter.usesBackdrop(s.styleTheme("keystrokes")))return true;
        if(!FeaturePolicy.isBlocked("ping") && s.ping!=null && s.ping.enabled && SurfacePainter.usesBackdrop(s.styleTheme("ping")))return true;
        if(!FeaturePolicy.isBlocked("fps") && s.fps!=null && s.fps.enabled && SurfacePainter.usesBackdrop(s.styleTheme("fps")))return true;
        if(WorldFeatureBridge.betterChatNeedsBackdrop())return true;
        return !FeaturePolicy.isBlocked("tnt") && s.tnt.enabled && FeatureBridge.hasTnt() && SurfacePainter.usesBackdrop(s.styleTheme("tnt"));
    }
    @@F1_HUD_FALLBACK@@
    public static void renderHud(@@CONTEXT@@ graphics,int width,int height,float partialTick){
        ClientRuntime runtime=ClientRuntime.get();
        Minecraft mc=Minecraft.getInstance();
        if(runtime==null || mc.player==null)return;
        if(runtime.isChatHudEditing())return;
        if(runtime.settings().hud.hideWithGameHud && @@GAME_HUD_HIDDEN@@)return;
        runtime.prepareHudFrame(partialTick,width,height);
        if(!runtime.shouldRenderHud())return;
        @@FLUSH_GUI@@
        NativeCanvas canvas=new NativeCanvas(graphics,width,height);
        try{runtime.renderHud(canvas,width,height,partialTick);}finally{canvas.finish();}
    }

    public static void renderChatEditor(Object owner,@@CONTEXT@@ graphics,int width,int height,float mouseX,float mouseY){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null||!runtime.isChatHudEditing())return;
        @@FLUSH_GUI@@
        NativeCanvas canvas=new NativeCanvas(graphics,width,height);
        try{runtime.renderChatHudEditor(owner,canvas,width,height,mouseX,mouseY);}finally{canvas.finish();}
    }
    public static void renderChatChrome(Object owner,@@CONTEXT@@ graphics,int width,int height,float mouseX,float mouseY){
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null||Minecraft.getInstance().player==null)return;
        @@FLUSH_GUI@@
        NativeCanvas canvas=new NativeCanvas(graphics,width,height);
        try{runtime.renderChatHudChrome(owner,canvas,width,height,mouseX,mouseY);}finally{canvas.finish();}
    }
}
