#!/usr/bin/env python3
"""Generate world features from exact, individually audited release descriptors."""
import argparse
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]
TEMPLATES=ROOT/'modules/platform/src/templates'
BASE_MIXINS=['ContainerFeatureAccessor','BiomeManagerAccessor','GuiOverlayAccessor','BossHealthOverlayAccessor','MouseHandlerAccessor','MinecraftTapeAccessor','WorldAttackMixin','CommandFixMixin','ItemScrollerMixin','ItemScrollerKeyMixin','ContainerDragMixin','NoFluidOverlayMixin','NoFluidFogMixin','LowFireOverlayMixin','HitColorMixin','HitColorArmorMixin','FullBrightMixin','NoHurtCamMixin','MainLeftFixMixin','ItemPhysicsMixin','WorldHitboxMixin','ItemStackTooltipMixin','TweaksyNametagMixin','TotemPopMixin','CosmeticsPlayerMixin','CosmeticsCapeVisibilityMixin','WavyCapeMixin','NoRenderTotemMixin','NoRenderHudMixin','NoRenderNauseaMixin','NoRenderBossBarMixin','SafeCtButtonMixin','SafeCtStopMixin','BetterChatMixin','BlockOutlineMixin','ExplosionPacketMixin','EnchantGlintMixin','ContainerTooltipMixin','PlayerTabPingMixin','SoundControlMixin','WorldMapChunkMixin']

def world_feature_mixins(version):
    v=tuple(map(int,version.split('.')))
    result=list(BASE_MIXINS)
    if v<(1,21,2):result.append('HitColorElytraMixin')
    if (1,21,2)<=v<(1,21,9):result.append('HitColorLivingMixin')
    if v>=(1,20):result.append('TooltipBackgroundMixin')
    if v>=(1,21,2):result.append('ItemPhysicsStateMixin')
    if (1,21,5)<=v<(1,21,11):result.append('HitboxPipelineAccessor')
    if v>=(1,20,2):result.append('LiteApiClientCommonMixin')
    if (1,20,2)<=v<(1,20,5):result.append('LiteApiCustomPayloadMixin')
    if v>=(1,20,5):result.append('LiteApiPayloadCodecMixin')
    return result

def generate_world_features(version,output):
    profiles=json.loads((ROOT/'modules/platform/compatibility/world-features.json').read_text())
    if version not in profiles:raise ValueError('Unverified world-feature release: '+version)
    p=profiles[version];v=tuple(map(int,version.split('.')))
    fire=json.loads((ROOT/'modules/platform/compatibility/low-fire.json').read_text())[version]
    modern_math=True;modern_state=v>=(1,21,2);gpu=v>=(1,21,5);gizmos=v>=(1,21,11);native=v>=(26,1)
    registry='net.minecraft.core.registries.BuiltInRegistries'
    click=p['clickType']
    values={
        'WORLD_IMPORTS':'import net.minecraft.world.inventory.'+click+';',
        'SCREEN':'mc.gui.screen()' if v>=(26,2) else 'mc.screen',
        'FOOD_HUD_HIDDEN':'mc.gui.hud.isHidden()' if v>=(26,2) else 'mc.options.hideGui',
        'ITEM_REGISTRY':registry+'.ITEM',
        'BLOCK_REGISTRY':registry+'.BLOCK',
        'EFFECT_VALUE':'instance.getEffect().value()' if p['potionsHolder'] else 'instance.getEffect()',
        'EFFECT_ID':registry+'.MOB_EFFECT.getKey(effect).toString()',
        'EFFECT_DURATION':'instance.isInfiniteDuration()?-1:instance.getDuration()/20f' if p['infiniteEffect'] else 'instance.getDuration()/20f',
        'CLICK_TYPE':click,'CLICK_TYPE_IMPORT':'net.minecraft.world.inventory.'+click,
        'SHIFT_DOWN':'mc.hasShiftDown()' if v>=(1,21,9) else 'net.minecraft.client.gui.screens.Screen.hasShiftDown()',
        'SAME_ITEM':'ItemStack.isSameItemSameComponents(a,b)' if v>=(1,20,5) else 'ItemStack.isSameItemSameTags(a,b)',
        'COMMAND_OWNER':p['commandOwner'].replace('/','.'),'COMMAND_SHORT':p['commandOwner'].rsplit('/',1)[-1],'COMMAND_HOOK':p['commandHook'],'MAIN_ARM_TARGET':'net.minecraft.world.entity.Avatar' if v>=(1,21,9) else 'net.minecraft.world.entity.player.Player','MAIN_HAND_GET':'mc.options.mainHand().get()','NO_HURT_CAM_DESC':'(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V' if native else '(Lcom/mojang/blaze3d/vertex/PoseStack;F)V','NO_HURT_CAM_ARGS':'net.minecraft.client.renderer.state.level.CameraRenderState state,com.mojang.blaze3d.vertex.PoseStack poseStack' if native else 'com.mojang.blaze3d.vertex.PoseStack poseStack,float partialTick',
        'DRAG_HOOK':p['dragHook'],'DRAG_ARGS':'net.minecraft.client.input.MouseButtonEvent event,double dx,double dy,' if v>=(1,21,9) else 'double mouseX,double mouseY,int button,double dx,double dy,',
        'DRAG_BUTTON':'dev.nightclient.platform.GameBridge.mouseButton(event.button())' if v>=(26,3) else 'event.button()' if v>=(1,21,9) else 'button',
        'CLICK_HOOK':'mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z' if v>=(1,21,9) else 'mouseClicked(DDI)Z',
        'CLICK_ARGS':'net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick,' if v>=(1,21,9) else 'double mouseX,double mouseY,int button,',
        'CLICK_BUTTON':'dev.nightclient.platform.GameBridge.mouseButton(event.button())' if v>=(26,3) else 'event.button()' if v>=(1,21,9) else 'button',
        'CLICK_X':'event.x()' if v>=(1,21,9) else 'mouseX',
        'CLICK_Y':'event.y()' if v>=(1,21,9) else 'mouseY',
        'INPUT_SHIFT':'(dev.nightclient.platform.GameBridge.modifiers(event.modifiers())&dev.nightclient.api.Keys.MOD_SHIFT)!=0' if v>=(1,21,9) else 'net.minecraft.client.gui.screens.Screen.hasShiftDown()',
        'INPUT_CONTROL':'(dev.nightclient.platform.GameBridge.modifiers(event.modifiers())&dev.nightclient.api.Keys.MOD_CONTROL)!=0' if v>=(1,21,9) else 'net.minecraft.client.gui.screens.Screen.hasControlDown()',
        'SCROLLER_WINDOW':'mc.getWindow().handle()' if v>=(1,21,9) else 'mc.getWindow().getWindow()',
        'SCROLLER_KEY_HOOK':'keyPress(JILnet/minecraft/client/input/KeyEvent;)V' if v>=(1,21,9) else 'keyPress(JIIII)V',
        'SCROLLER_KEY_ARGS':'long window,int action,net.minecraft.client.input.KeyEvent event,' if v>=(1,21,9) else 'long window,int key,int scanCode,int action,int modifiers,',
        'SCROLLER_KEY_MATCH':'mc.options.keyDrop.matches(event)' if v>=(1,21,9) else 'mc.options.keyDrop.matches(key,scanCode)',
        'STACK_MATCH':'ItemStack.isSameItemSameComponents(a,b)' if v>=(1,20,5) else 'a.getItem()==b.getItem()&&java.util.Objects.equals(a.getTag(),b.getTag())',
        'SCROLLER_KEY_MODS':'event.modifiers()' if v>=(1,21,9) else 'modifiers',
        'SLOT_SCREEN_X':'screen.nightclient$leftPos()+slot.x+8',
        'SLOT_SCREEN_Y':'screen.nightclient$topPos()+slot.y+8',
        'FIRE_HOOK':fire['fireHook'],
        'FIRE_TRANSLATION_HOOK':fire['translationHook'],
        'FIRE_TRANSLATION_TARGET':'L'+fire['translateOwner']+';translate'+fire['fireTranslate'],
        'FIRE_TRANSLATION_TYPE':'double' if fire['fireTranslate'].startswith('(DDD)') else 'float',
        'RELEASE_HOOK':'mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z' if v>=(1,21,9) else 'mouseReleased(DDI)Z',
        'WATER_HOOK':p['waterHook'],'FLUID_HOOK':p['fluidHook'],
        'OVERLAY_HOOK':p['overlayHook'],'OVERLAY_CALLBACK_IMPORT':'CallbackInfoReturnable' if v>=(1,21,11) else 'CallbackInfo',
        'OVERLAY_CALLBACK':'CallbackInfoReturnable<?> ' if v>=(1,21,11) else 'CallbackInfo ',
        'ABGR_COLOR':'' if v>=(1,21,5) else 'pixel=(pixel&0xff00ff00)|((pixel&255)<<16)|((pixel>>>16)&255);',
        'SET_PIXEL':'setPixel' if v>=(1,21,5) else 'setPixelRGBA',
        'CARRIED':'screen.nightclient$menu().getCarried()',
        'ENTITY_REMOVED':'attacked.isRemoved()',
        'LIGHT_OWNER':p['lightOwner'].replace('/','.'),'LIGHT_SHORT':p['lightOwner'].rsplit('/',1)[-1],
        'LIGHT_HOOK':p['lightHook'],'LIGHT_ARGS':'net.minecraft.client.renderer.state.LightmapRenderState state,' if native else 'float delta,',
        'ITEM_HOOK':p['itemHook'],'GROUND_METHOD':p['groundGetter'].split('(')[0],
        'QUAT_TYPE':'org.joml.Quaternionf' if modern_math else 'com.mojang.math.Quaternion',
        'QUAT_DESC':'Lorg/joml/Quaternionf;' if modern_math else 'Lcom/mojang/math/Quaternion;',
        'ROTATE_X':'com.mojang.math.Axis.XP.rotationDegrees(physics.x)' if modern_math else 'com.mojang.math.Vector3f.XP.rotationDegrees(physics.x)',
        'ROTATE_Y':'com.mojang.math.Axis.YP.rotationDegrees(physics.y)' if modern_math else 'com.mojang.math.Vector3f.YP.rotationDegrees(physics.y)',
        'ROTATE_Z':'com.mojang.math.Axis.ZP.rotationDegrees(physics.z)' if modern_math else 'com.mojang.math.Vector3f.ZP.rotationDegrees(physics.z)',
        'PHYSICS_IMPORTS':'import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;' if modern_state else '',
        'PHYSICS_EXTRACT':'''@Inject(method="extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",at=@At("TAIL"))
    private void nightclient$extract(ItemEntity entity,ItemEntityRenderState state,float delta,CallbackInfo callback){((ItemPhysicsState)(Object)state).nightclient$physics(extract(entity,delta));}''' if modern_state else '',
        'PHYSICS_GET':'((ItemPhysicsState)(Object)state).nightclient$physics()' if modern_state else 'extract(entity,delta)',
        'DEBUG_HOOK':p['debugHook'],
    }
    values['SET_PIXEL']='setPixel' if v>=(1,21,2) else 'setPixelRGBA'
    values['HIT_COLOR_INIT']='0xb2ff0000' if v>=(1,21,2) else '0xb20000ff'
    values['OVERLAY_UPLOAD']='texture.upload();' if gpu else 'int binding=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);try{texture.upload();}finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(binding);}'
    values['ABGR_COLOR']='' if v>=(1,21,2) else 'pixel=(pixel&0xff00ff00)|((pixel&255)<<16)|((pixel>>>16)&255);'
    values['HUD_OWNER']='net.minecraft.client.gui.Hud' if v>=(26,2) else 'net.minecraft.client.gui.Gui'
    values['ITEM_COMPONENTS_APPEND']='if(stack.getComponents()!=null)metadata.append(\' \').append(stack.getComponents());' if v>=(1,20,5) else ''
    values['READ_BUKKIT_STRING']=(
        'if(tagObj instanceof net.minecraft.nbt.CompoundTag){String val=((net.minecraft.nbt.CompoundTag)tagObj).getCompoundOrEmpty("PublicBukkitValues").getStringOr(key,"");if(!val.isEmpty())return val;}' if v>=(1,21,5)
        else 'if(tagObj instanceof net.minecraft.nbt.CompoundTag){net.minecraft.nbt.CompoundTag root=(net.minecraft.nbt.CompoundTag)tagObj;if(root.contains("PublicBukkitValues",10)){String val=root.getCompound("PublicBukkitValues").getString(key);if(!val.isEmpty())return val;}}'
    )
    values['HOLOGRAM_TEXT_EXPR']=(
        'String s=null;'
        'if(e instanceof net.minecraft.world.entity.Display.TextDisplay){net.minecraft.world.entity.Display.TextDisplay.TextRenderState trs=((net.minecraft.world.entity.Display.TextDisplay)e).textRenderState();if(trs!=null&&trs.text()!=null)s=trs.text().getString();}'
        'else if(e instanceof net.minecraft.world.entity.decoration.ArmorStand){net.minecraft.network.chat.Component cn=e.getCustomName();if(cn!=null)s=cn.getString();}'
    )
    values['ANY_HOLOGRAM_TEXT_EXPR']=(
        'String text=null;'
        'if(e instanceof net.minecraft.world.entity.Display.TextDisplay){net.minecraft.world.entity.Display.TextDisplay.TextRenderState trs=((net.minecraft.world.entity.Display.TextDisplay)e).textRenderState();if(trs!=null&&trs.text()!=null)text=trs.text().getString();}'
        'if((text==null||text.isEmpty())&&e.getCustomName()!=null)text=e.getCustomName().getString();'
    )
    gui_msg='net.minecraft.client.multiplayer.chat.GuiMessage' if v>=(26,1) else 'net.minecraft.client.GuiMessage'
    values['LINE_ADDED_TIME_BODY']=f'if(entry instanceof {gui_msg}.Line)return (({gui_msg}.Line)entry).addedTime();if(entry instanceof {gui_msg})return (({gui_msg})entry).addedTime();return -1;'
    values['LINE_END_OF_ENTRY_BODY']=f'if(entry instanceof {gui_msg}.Line)return (({gui_msg}.Line)entry).endOfEntry();return true;'
    values['MEASURE_GUI_MESSAGE_WIDTH']=f'if(entry instanceof {gui_msg}.Line)return font.width((({gui_msg}.Line)entry).content());if(entry instanceof {gui_msg})return font.width((({gui_msg})entry).content());'
    values['EXTRACT_GUI_MESSAGE_TEXT']=f'if(entry instanceof {gui_msg}.Line)return extractFormattedSeq((({gui_msg}.Line)entry).content());if(entry instanceof {gui_msg})return (({gui_msg})entry).content().getString();'
    values['FOG_TYPE']='FogType'
    values['FOG_IMPORT']='net.minecraft.world.level.material.FogType'
    values['FOG_REPLACE']='if(original==FogType.WATER&&policy.waterFog||original==FogType.LAVA&&policy.lavaFog)callback.setReturnValue(FogType.NONE);'
    cmd_returns_bool=p['commandHook'].endswith(')Z')
    values['COMMAND_CALLBACK_TYPE']='CallbackInfoReturnable' if cmd_returns_bool else 'CallbackInfo'
    values['COMMAND_CALLBACK_PARAM']='CallbackInfoReturnable<Boolean>' if cmd_returns_bool else 'CallbackInfo'
    values['COMMAND_CANCEL']='callback.setReturnValue(Boolean.TRUE);' if cmd_returns_bool else 'callback.cancel();'
    values['BLOCK_OVERLAY_HOOK']=('renderTex(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;)V' if v<(1,21,4)
        else 'renderTex(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V' if v<(26,2)
        else 'submitBlockSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V' if v==(26,2)
        else 'submitBlockSprite(Lnet/minecraft/resources/Identifier;FFFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V')
    totem_screen=v>=(1,21,6)
    values['TOTEM_OWNER']='net.minecraft.client.renderer.ScreenEffectRenderer' if totem_screen else 'net.minecraft.client.renderer.GameRenderer'
    values['TOTEM_SHORT']='ScreenEffectRenderer' if totem_screen else 'GameRenderer'
    values['TOTEM_HOOK']=('renderItemActivationAnimation(IIF)V' if v<(1,21)
        else 'renderItemActivationAnimation(Lnet/minecraft/client/gui/GuiGraphics;F)V' if v<(1,21,6)
        else 'renderItemActivationAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;F)V' if v<(1,21,9)
        else 'renderItemActivationAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/SubmitNodeCollector;)V' if v<(26,3)
        else 'renderItemActivationAnimation(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/SubmitNodeCollector;)V')
    nr_hud_modern=v>=(26,2)
    values['NORENDER_HUD_OWNER']='net.minecraft.client.gui.Hud' if nr_hud_modern else 'net.minecraft.client.gui.Gui'
    values['NORENDER_HUD_SHORT']='Hud' if nr_hud_modern else 'Gui'
    if v<(1,21,11):
        values['NORENDER_HUD_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.resources.ResourceLocation;'
        values['NORENDER_TEXTURE_OVERLAY_INJECT']='    @Inject(method="renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;F)V",at=@At("HEAD"),cancellable=true)\n    private void nightclient$textureOverlay(GuiGraphics graphics,ResourceLocation texture,float alpha,CallbackInfo callback){WorldFeatureBridge.Policy p=WorldFeatureBridge.policy();if(texture!=null){String path=texture.getPath();if((p.noPumpkin&&path.contains("pumpkinblur"))||(p.noPowderSnow&&path.contains("powder_snow")))callback.cancel();}}'
    elif v==(1,21,11):
        values['NORENDER_HUD_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.resources.Identifier;'
        values['NORENDER_TEXTURE_OVERLAY_INJECT']='    @Inject(method="renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V",at=@At("HEAD"),cancellable=true)\n    private void nightclient$textureOverlay(GuiGraphics graphics,Identifier texture,float alpha,CallbackInfo callback){WorldFeatureBridge.Policy p=WorldFeatureBridge.policy();if(texture!=null){String path=texture.getPath();if((p.noPumpkin&&path.contains("pumpkinblur"))||(p.noPowderSnow&&path.contains("powder_snow")))callback.cancel();}}'
    else:
        values['NORENDER_HUD_IMPORTS']='import net.minecraft.client.gui.GuiGraphicsExtractor;\nimport net.minecraft.resources.Identifier;'
        values['NORENDER_TEXTURE_OVERLAY_INJECT']='    @Inject(method="extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V",at=@At("HEAD"),cancellable=true)\n    private void nightclient$textureOverlay(GuiGraphicsExtractor graphics,Identifier texture,float alpha,CallbackInfo callback){WorldFeatureBridge.Policy p=WorldFeatureBridge.policy();if(texture!=null){String path=texture.getPath();if((p.noPumpkin&&path.contains("pumpkinblur"))||(p.noPowderSnow&&path.contains("powder_snow")))callback.cancel();}}'
    values['PORTAL_HOOK']=('renderPortalOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V' if v<(26,1)
        else 'extractPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V')
    values['VIGNETTE_HOOK']=('renderVignette(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;)V' if v<(26,1)
        else 'extractVignette(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/Entity;)V')
    nausea_in_hud=v>=(1,21,2)
    values['NAUSEA_OWNER']=values['NORENDER_HUD_OWNER'] if nausea_in_hud else 'net.minecraft.client.renderer.GameRenderer'
    values['NAUSEA_SHORT']=values['NORENDER_HUD_SHORT'] if nausea_in_hud else 'GameRenderer'
    values['NAUSEA_HOOK']=('renderConfusionOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V' if v<(26,1)
        else 'extractConfusionOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V')
    values['BOSS_BAR_HOOK']=('render(Lnet/minecraft/client/gui/GuiGraphics;)V' if v<(26,1)
        else 'extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V')
    skin='((net.minecraft.client.player.AbstractClientPlayer)attacked).getSkinTextureLocation()' if v<(1,20,2) else '((net.minecraft.client.player.AbstractClientPlayer)attacked).getSkin().texture()' if v<(1,21,9) else '((net.minecraft.client.player.AbstractClientPlayer)attacked).getSkin().body().texturePath()'
    values['TARGET_SKIN']=skin+'.toString()'
    values['LOCAL_PLAYER_SKIN']=skin.replace('((net.minecraft.client.player.AbstractClientPlayer)attacked)','mc.player')+'.toString()'
    cosmetics_id='Identifier' if v>=(1,21,11) else 'ResourceLocation'
    values['COSMETICS_IDENTIFIER']=cosmetics_id
    values['COSMETICS_IDENTIFIER_PARSE']=cosmetics_id+'.parse(id)' if v>=(1,21) else 'new '+cosmetics_id+'(id)'
    values['COSMETICS_SET_PIXEL']='image.setPixel(x,y,pixel);' if v>=(1,21,2) else 'pixel=(pixel&0xff00ff00)|((pixel&255)<<16)|((pixel>>>16)&255);image.setPixelRGBA(x,y,pixel);'
    values['COSMETICS_DYN_ARGS']='()->label,' if v>=(1,21,5) else ''
    if v<(1,20,2):
        values['COSMETICS_PLAYER_MIXIN_IMPORTS']='import net.minecraft.resources.ResourceLocation;'
        values['COSMETICS_PLAYER_MIXIN_BODY']='''    @Inject(method="getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;",at=@At("RETURN"),cancellable=true)
    private void nightclient$skinLoc(CallbackInfoReturnable<ResourceLocation> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        ResourceLocation id=CosmeticsBridge.skinTexture();
        if(id!=null)callback.setReturnValue(id);
    }
    @Inject(method="isSkinLoaded()Z",at=@At("RETURN"),cancellable=true)
    private void nightclient$skinLoaded(CallbackInfoReturnable<Boolean> callback){
        if(CosmeticsBridge.appliesTo(this) && CosmeticsBridge.skinTexture()!=null)callback.setReturnValue(Boolean.TRUE);
    }
    @Inject(method="getCloakTextureLocation()Lnet/minecraft/resources/ResourceLocation;",at=@At("RETURN"),cancellable=true)
    private void nightclient$cloakLoc(CallbackInfoReturnable<ResourceLocation> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        if(CosmeticsBridge.capeSuppressed()){callback.setReturnValue(null);return;}
        if(CosmeticsBridge.capeOverridden()){
            ResourceLocation id=CosmeticsBridge.capeTexture();
            if(id!=null)callback.setReturnValue(id);
        }
    }
    @Inject(method="isCapeLoaded()Z",at=@At("RETURN"),cancellable=true)
    private void nightclient$capeLoaded(CallbackInfoReturnable<Boolean> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        if(CosmeticsBridge.capeSuppressed()){callback.setReturnValue(Boolean.FALSE);return;}
        if(CosmeticsBridge.capeOverridden() && CosmeticsBridge.capeTexture()!=null)callback.setReturnValue(Boolean.TRUE);
    }
    @Inject(method="getElytraTextureLocation()Lnet/minecraft/resources/ResourceLocation;",at=@At("RETURN"),cancellable=true)
    private void nightclient$elytraLoc(CallbackInfoReturnable<ResourceLocation> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        if(CosmeticsBridge.capeSuppressed()){callback.setReturnValue(null);return;}
        if(CosmeticsBridge.capeOverridden()){
            ResourceLocation id=CosmeticsBridge.capeTexture();
            if(id!=null)callback.setReturnValue(id);
        }
    }
    @Inject(method="isElytraLoaded()Z",at=@At("RETURN"),cancellable=true)
    private void nightclient$elytraLoaded(CallbackInfoReturnable<Boolean> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        if(CosmeticsBridge.capeSuppressed()){callback.setReturnValue(Boolean.FALSE);return;}
        if(CosmeticsBridge.capeOverridden() && CosmeticsBridge.capeTexture()!=null)callback.setReturnValue(Boolean.TRUE);
    }
    @Inject(method="getModelName()Ljava/lang/String;",at=@At("RETURN"),cancellable=true)
    private void nightclient$modelName(CallbackInfoReturnable<String> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        Boolean slim=CosmeticsBridge.slimOverride();
        if(slim!=null)callback.setReturnValue(slim.booleanValue()?"slim":"default");
    }'''
    elif v<(1,21,9):
        values['COSMETICS_PLAYER_MIXIN_IMPORTS']='import java.util.Objects;\nimport net.minecraft.client.resources.PlayerSkin;\nimport net.minecraft.resources.ResourceLocation;\nimport org.spongepowered.asm.mixin.Unique;'
        values['COSMETICS_PLAYER_MIXIN_BODY']='''    @Unique private PlayerSkin nightclient$lastOrig;
    @Unique private ResourceLocation nightclient$lastBody;
    @Unique private ResourceLocation nightclient$lastCape;
    @Unique private boolean nightclient$lastSuppressed;
    @Unique private Boolean nightclient$lastSlim;
    @Unique private PlayerSkin nightclient$cachedSkin;
    @Inject(method="getSkin()Lnet/minecraft/client/resources/PlayerSkin;",at=@At("RETURN"),cancellable=true)
    private void nightclient$getSkin(CallbackInfoReturnable<PlayerSkin> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        PlayerSkin orig=callback.getReturnValue();
        if(orig==null)return;
        ResourceLocation bodyId=CosmeticsBridge.skinTexture();
        boolean suppressed=CosmeticsBridge.capeSuppressed();
        ResourceLocation capeId=!suppressed && CosmeticsBridge.capeOverridden()?CosmeticsBridge.capeTexture():null;
        Boolean slim=CosmeticsBridge.slimOverride();
        if(bodyId==null && !suppressed && capeId==null && slim==null)return;
        if(nightclient$cachedSkin!=null && nightclient$lastOrig==orig && Objects.equals(nightclient$lastBody,bodyId)
           && Objects.equals(nightclient$lastCape,capeId) && nightclient$lastSuppressed==suppressed
           && Objects.equals(nightclient$lastSlim,slim)){
            callback.setReturnValue(nightclient$cachedSkin);
            return;
        }
        ResourceLocation finalBody=bodyId!=null?bodyId:orig.texture();
        ResourceLocation finalCape=suppressed?null:(capeId!=null?capeId:orig.capeTexture());
        ResourceLocation finalElytra=suppressed?null:(capeId!=null?capeId:orig.elytraTexture());
        PlayerSkin.Model finalModel=slim!=null?(slim.booleanValue()?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE):orig.model();
        PlayerSkin next=new PlayerSkin(finalBody,orig.textureUrl(),finalCape,finalElytra,finalModel,orig.secure());
        nightclient$lastOrig=orig;nightclient$lastBody=bodyId;nightclient$lastCape=capeId;
        nightclient$lastSuppressed=suppressed;nightclient$lastSlim=slim;nightclient$cachedSkin=next;
        callback.setReturnValue(next);
    }'''
    else:
        values['COSMETICS_PLAYER_MIXIN_IMPORTS']='import java.util.Objects;\nimport net.minecraft.core.ClientAsset;\nimport net.minecraft.resources.'+cosmetics_id+';\nimport net.minecraft.world.entity.player.PlayerModelType;\nimport net.minecraft.world.entity.player.PlayerSkin;\nimport org.spongepowered.asm.mixin.Unique;'
        values['COSMETICS_PLAYER_MIXIN_BODY']='''    @Unique private PlayerSkin nightclient$lastOrig;
    @Unique private '''+cosmetics_id+''' nightclient$lastBody;
    @Unique private '''+cosmetics_id+''' nightclient$lastCape;
    @Unique private boolean nightclient$lastSuppressed;
    @Unique private Boolean nightclient$lastSlim;
    @Unique private PlayerSkin nightclient$cachedSkin;
    @Inject(method="getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;",at=@At("RETURN"),cancellable=true)
    private void nightclient$getSkin(CallbackInfoReturnable<PlayerSkin> callback){
        if(!CosmeticsBridge.appliesTo(this))return;
        PlayerSkin orig=callback.getReturnValue();
        if(orig==null)return;
        '''+cosmetics_id+''' bodyId=CosmeticsBridge.skinTexture();
        boolean suppressed=CosmeticsBridge.capeSuppressed();
        '''+cosmetics_id+''' capeId=!suppressed && CosmeticsBridge.capeOverridden()?CosmeticsBridge.capeTexture():null;
        Boolean slim=CosmeticsBridge.slimOverride();
        if(bodyId==null && !suppressed && capeId==null && slim==null)return;
        if(nightclient$cachedSkin!=null && nightclient$lastOrig==orig && Objects.equals(nightclient$lastBody,bodyId)
           && Objects.equals(nightclient$lastCape,capeId) && nightclient$lastSuppressed==suppressed
           && Objects.equals(nightclient$lastSlim,slim)){
            callback.setReturnValue(nightclient$cachedSkin);
            return;
        }
        ClientAsset.Texture finalBody=bodyId!=null?new ClientAsset.ResourceTexture(bodyId,bodyId):orig.body();
        ClientAsset.Texture finalCape=orig.cape(), finalElytra=orig.elytra();
        if(suppressed){
            finalCape=null;finalElytra=null;
        }else if(capeId!=null){
            ClientAsset.Texture custom=new ClientAsset.ResourceTexture(capeId,capeId);
            finalCape=custom;finalElytra=custom;
        }
        PlayerModelType finalModel=slim!=null?(slim.booleanValue()?PlayerModelType.SLIM:PlayerModelType.WIDE):orig.model();
        PlayerSkin next=new PlayerSkin(finalBody,finalCape,finalElytra,finalModel,orig.secure());
        nightclient$lastOrig=orig;nightclient$lastBody=bodyId;nightclient$lastCape=capeId;
        nightclient$lastSuppressed=suppressed;nightclient$lastSlim=slim;nightclient$cachedSkin=next;
        callback.setReturnValue(next);
    }'''
    values['CHAT_LINE_HEIGHT']='access.nightclient$lineHeight()'
    values['TOOLTIP_HOOK']=('getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;' if v>=(1,20,5) else 'getTooltipLines(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;')
    sidebar_obj='scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR)' if v>=(1,20,2) else 'scoreboard.getDisplayObjective(1)'
    if v>=(1,20,3):
        scoreboard_code='net.minecraft.world.scores.Objective objective='+sidebar_obj+';if(objective==null)return Collections.emptyMap();Map<String,Object> map=new java.util.LinkedHashMap<String,Object>();map.put("active",Boolean.TRUE);map.put("title",formattedText(objective.getDisplayName()));java.util.List<Map<String,Object>> lines=new java.util.ArrayList<Map<String,Object>>();for(net.minecraft.world.scores.PlayerScoreEntry entry:scoreboard.listPlayerScores(objective)){if(entry.isHidden())continue;String owner=entry.owner();net.minecraft.world.scores.PlayerTeam team=scoreboard.getPlayersTeam(owner);String text=formattedText(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team,entry.ownerName()));Map<String,Object> row=new java.util.LinkedHashMap<String,Object>();row.put("id",owner);row.put("text",text);row.put("score",entry.value());row.put("hasScore",true);lines.add(row);}lines.sort((a,b)->{int cmp=Integer.compare((Integer)b.get("score"),(Integer)a.get("score"));if(cmp!=0)return cmp;String oa=String.valueOf(a.get("id")),ob=String.valueOf(b.get("id"));cmp=String.CASE_INSENSITIVE_ORDER.compare(oa,ob);return cmp!=0?cmp:oa.compareTo(ob);});if(lines.size()>15)lines=lines.subList(0,15);map.put("lines",lines);return map;'
    else:
        scoreboard_code='net.minecraft.world.scores.Objective objective='+sidebar_obj+';if(objective==null)return Collections.emptyMap();Map<String,Object> map=new java.util.LinkedHashMap<String,Object>();map.put("active",Boolean.TRUE);map.put("title",formattedText(objective.getDisplayName()));java.util.List<Map<String,Object>> lines=new java.util.ArrayList<Map<String,Object>>();for(net.minecraft.world.scores.Score entry:scoreboard.getPlayerScores(objective)){String owner=entry.getOwner();net.minecraft.world.scores.PlayerTeam team=scoreboard.getPlayersTeam(owner);String text=formattedText(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team,net.minecraft.network.chat.Component.nullToEmpty(owner)));Map<String,Object> row=new java.util.LinkedHashMap<String,Object>();row.put("id",owner);row.put("text",text);row.put("score",entry.getScore());row.put("hasScore",true);lines.add(row);}lines.sort((a,b)->{int cmp=Integer.compare((Integer)b.get("score"),(Integer)a.get("score"));if(cmp!=0)return cmp;String oa=String.valueOf(a.get("id")),ob=String.valueOf(b.get("id"));cmp=String.CASE_INSENSITIVE_ORDER.compare(oa,ob);return cmp!=0?cmp:oa.compareTo(ob);});if(lines.size()>15)lines=lines.subList(0,15);map.put("lines",lines);return map;'
    values['SCOREBOARD_CODE']=scoreboard_code
    values['CHAT_COMPONENT']='mc.gui.hud.getChat()' if v>=(26,2) else 'mc.gui.getChat()'
    values['HUD_INSTANCE']='mc.gui.hud' if v>=(26,2) else 'mc.gui'
    values['CHAT_LINE_HEIGHT']='access.nightclient$lineHeight()'
    values['BOSS_OVERLAY']='mc.gui.hud.getBossOverlay()' if v>=(26,2) else 'mc.gui.getBossOverlay()'
    wf_context='GuiGraphicsExtractor' if v>=(26,1) else 'GuiGraphics'
    wf_context_import='net.minecraft.client.gui.'+wf_context
    values['CONTEXT']=wf_context
    values['FLUSH_GUI']='graphics.flush();' if wf_context=='GuiGraphics' and v<(1,21,6) else ''
    values['WORLD_IMPORTS']+='\nimport '+wf_context_import+';'
    values['SAFE_CT_BUTTON_PRESS_HOOK']='onPress(Lnet/minecraft/client/input/InputWithModifiers;)V' if v>=(1,21,9) else 'onPress()V'
    values['SAFE_CT_BUTTON_PRESS_ARGS']='net.minecraft.client.input.InputWithModifiers input,' if v>=(1,21,9) else ''
    values['SAFE_CT_BUTTON_REPLAY_ARGS']='input' if v>=(1,21,9) else ''
    if v>=(26,3):
        values['SAFE_CT_RESET_WINDOW_CLOSE']='try{java.lang.reflect.Field f=com.mojang.blaze3d.platform.Window.class.getDeclaredField("shouldClose");f.setAccessible(true);f.setBoolean(((Minecraft)(Object)this).getWindow(),false);}catch(Throwable ignored){}'
    elif v>=(1,21,9):
        values['SAFE_CT_RESET_WINDOW_CLOSE']='org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(((Minecraft)(Object)this).getWindow().handle(),false);'
    else:
        values['SAFE_CT_RESET_WINDOW_CLOSE']='org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(((Minecraft)(Object)this).getWindow().getWindow(),false);'
    values['BETTER_CHAT_VANILLA_TEXT_X']='(float)(4.0*vanillaScale)'
    if v>=(1,21,6):
        values['BETTER_CHAT_PUSH_TRANSFORM']=(
            'if(chatClipActive)graphics.enableScissor(chatClipX1,chatClipY1,chatClipX2,chatClipY2);'
            'graphics.pose().pushMatrix();'
            'graphics.pose().translate(chatTextOriginX,chatTextOriginBottomY+chatSlideOffset);'
            'if(chatHudScale!=1f)graphics.pose().scale(chatHudScale,chatHudScale);'
            'graphics.pose().translate(-vanillaTextX,-vanillaBottomY);'
        )
        values['BETTER_CHAT_POP_TRANSFORM']=(
            'graphics.pose().popMatrix();'
            'if(chatClipActive)graphics.disableScissor();'
        )
    else:
        values['BETTER_CHAT_PUSH_TRANSFORM']=(
            'if(chatClipActive)graphics.enableScissor(chatClipX1,chatClipY1,chatClipX2,chatClipY2);'
            'graphics.pose().pushPose();'
            'graphics.pose().translate(chatTextOriginX,chatTextOriginBottomY+chatSlideOffset,0f);'
            'if(chatHudScale!=1f)graphics.pose().scale(chatHudScale,chatHudScale,1f);'
            'graphics.pose().translate(-vanillaTextX,-vanillaBottomY,0f);'
        )
        values['BETTER_CHAT_POP_TRANSFORM']=(
            'graphics.pose().popPose();'
            'if(chatClipActive)graphics.disableScissor();'
        )
    if v>=(1,21,11):
        values['BETTER_CHAT_CLICK_MATRIX_METHOD']=(
            'public static void applyBetterChatClickMatrix(Object chatObj,org.joml.Matrix3x2f m){\n'
            '        ClientRuntime runtime=ClientRuntime.get();\n'
            '        if(FeaturePolicy.isBlocked("betterChat")||runtime==null||runtime.settings().betterChat==null||!runtime.settings().betterChat.enabled||m==null)return;\n'
            '        Minecraft mc=Minecraft.getInstance();\n'
            '        int screenW=mc.getWindow().getGuiScaledWidth();\n'
            '        int screenH=mc.getWindow().getGuiScaledHeight();\n'
            '        double vanillaScale=(chatObj instanceof dev.nightclient.platform.mixin.ChatLinesAccessor)\n'
            '            ?((dev.nightclient.platform.mixin.ChatLinesAccessor)chatObj).nightclient$chatScale():1.0;\n'
            '        float vanillaTextX=betterChatVanillaTextX(vanillaScale);\n'
            '        float vanillaBottomY=screenH-40f;\n'
            '        float ox,oy,hs;\n'
            '        if(chatLastOriginValid&&chatTextOriginBottomY>0f){\n'
            '            ox=chatTextOriginX;oy=chatTextOriginBottomY;hs=chatHudScale;\n'
            '        }else{\n'
            '            Settings.BetterChat cfg=runtime.settings().betterChat;\n'
            '            float[] anchor=BetterChatLogic.computeHudAnchor(cfg.anchorX,cfg.anchorY,cfg.offsetX,cfg.offsetY,cfg.scale,screenW,screenH);\n'
            '            boolean drawCard="rounded".equals(cfg.backgroundStyle)||"compact".equals(cfg.backgroundStyle);\n'
            '            hs=anchor[2];\n'
            '            ox=Math.max(2f,anchor[0])+(drawCard?6f*hs:0f);\n'
            '            oy=Math.max(18f,Math.min(screenH-2f,anchor[1]))-(drawCard?4f*hs:0f);\n'
            '        }\n'
            '        m.translate(ox,oy);\n'
            '        if(hs!=1f)m.scale(hs,hs);\n'
            '        m.translate(-vanillaTextX,-vanillaBottomY);\n'
            '    }'
        )
    else:
        values['BETTER_CHAT_CLICK_MATRIX_METHOD']=''
    click_unmap_hooks=(
        '\n    @ModifyVariable(method="getClickedComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;",at=@At("HEAD"),argsOnly=true,ordinal=0)\n'
        '    private double nightclient$unmapChatClickX(double mouseX){return WorldFeatureBridge.betterChatUnmapMouseX(this,mouseX);}\n'
        '    @ModifyVariable(method="getClickedComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;",at=@At("HEAD"),argsOnly=true,ordinal=1)\n'
        '    private double nightclient$unmapChatClickY(double mouseY){return WorldFeatureBridge.betterChatUnmapMouseY(this,mouseY);}'
    )
    if v>=(26,1):
        values['BETTER_CHAT_HISTORY_METHODS']='{"addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V","addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"}'
        values['BETTER_CHAT_ADD_HOOK']='addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V'
        values['BETTER_CHAT_TAG_HOOK']='@ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n    private net.minecraft.client.multiplayer.chat.GuiMessageTag nightclient$hideChatTag(net.minecraft.client.multiplayer.chat.GuiMessageTag tag){return WorldFeatureBridge.betterChatHideSignatureBar()?null:tag;}'
        values['BETTER_CHAT_IMPORTS']='import net.minecraft.client.gui.Font;\nimport net.minecraft.client.gui.GuiGraphicsExtractor;\nimport net.minecraft.client.multiplayer.chat.GuiMessageTag;\nimport net.minecraft.util.FormattedCharSequence;\nimport org.joml.Matrix3x2f;\nimport java.util.function.Consumer;'
        values['BETTER_CHAT_RENDER_HOOKS']=(
            '@Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("HEAD"))\n'
            '    private void nightclient$chatBegin(GuiGraphicsExtractor graphics,Font font,int tickCount,int mouseX,int mouseY,ChatComponent.DisplayMode mode,boolean flag,CallbackInfo cb){\n'
            '        nightclient$chatPushed=WorldFeatureBridge.beginBetterChatRender(graphics,this,tickCount);\n'
            '    }\n'
            '    @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("RETURN"))\n'
            '    private void nightclient$chatEnd(GuiGraphicsExtractor graphics,Font font,int tickCount,int mouseX,int mouseY,ChatComponent.DisplayMode mode,boolean flag,CallbackInfo cb){\n'
            '        if(nightclient$chatPushed){WorldFeatureBridge.endBetterChatRender(graphics);nightclient$chatPushed=false;}\n'
            '    }\n'
            '    @ModifyVariable(method="extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n'
            '    private ChatComponent.ChatGraphicsAccess nightclient$wrapChatGraphics(final ChatComponent.ChatGraphicsAccess delegate){\n'
            '        return new ChatComponent.ChatGraphicsAccess(){\n'
            '            public void updatePose(Consumer<Matrix3x2f> c){if(nightclient$chatPushed){delegate.updatePose(c);}else{delegate.updatePose(m->{WorldFeatureBridge.applyBetterChatClickMatrix(BetterChatMixin.this,m);c.accept(m);});}}\n'
            '            public void fill(int x1,int y1,int x2,int y2,int color){int c=WorldFeatureBridge.modifyBetterChatFill(x1,y1,x2,y2,color);if((c>>>24)!=0)delegate.fill(x1,y1,x2,y2,c);}\n'
            '            public boolean handleMessage(int y,float alpha,FormattedCharSequence line){return delegate.handleMessage(y,alpha,line);}\n'
            '            public void handleTag(int x1,int y1,int x2,int y2,float alpha,GuiMessageTag tag){if(!WorldFeatureBridge.betterChatHideSignatureBar())delegate.handleTag(x1,y1,x2,y2,alpha,tag);}\n'
            '            public void handleTagIcon(int x,int y,boolean focused,GuiMessageTag tag,GuiMessageTag.Icon icon){if(!WorldFeatureBridge.betterChatHideSignatureBar())delegate.handleTagIcon(x,y,focused,tag,icon);}\n'
            '        };\n'
            '    }'
        )
    elif v>=(1,21,11):
        values['BETTER_CHAT_HISTORY_METHODS']='{"addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V","addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V"}'
        values['BETTER_CHAT_ADD_HOOK']='addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V'
        values['BETTER_CHAT_TAG_HOOK']='@ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n    private net.minecraft.client.GuiMessageTag nightclient$hideChatTag(net.minecraft.client.GuiMessageTag tag){return WorldFeatureBridge.betterChatHideSignatureBar()?null:tag;}'
        values['BETTER_CHAT_IMPORTS']='import net.minecraft.client.gui.Font;\nimport net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.client.GuiMessageTag;\nimport net.minecraft.util.FormattedCharSequence;\nimport org.joml.Matrix3x2f;\nimport java.util.function.Consumer;'
        values['BETTER_CHAT_RENDER_HOOKS']=(
            '@Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",at=@At("HEAD"))\n'
            '    private void nightclient$chatBegin(GuiGraphics graphics,Font font,int tickCount,int mouseX,int mouseY,boolean focused,boolean changeCursor,CallbackInfo cb){\n'
            '        nightclient$chatPushed=WorldFeatureBridge.beginBetterChatRender(graphics,this,tickCount);\n'
            '    }\n'
            '    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",at=@At("RETURN"))\n'
            '    private void nightclient$chatEnd(GuiGraphics graphics,Font font,int tickCount,int mouseX,int mouseY,boolean focused,boolean changeCursor,CallbackInfo cb){\n'
            '        if(nightclient$chatPushed){WorldFeatureBridge.endBetterChatRender(graphics);nightclient$chatPushed=false;}\n'
            '    }\n'
            '    @ModifyVariable(method="render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n'
            '    private ChatComponent.ChatGraphicsAccess nightclient$wrapChatGraphics(final ChatComponent.ChatGraphicsAccess delegate){\n'
            '        return new ChatComponent.ChatGraphicsAccess(){\n'
            '            public void updatePose(Consumer<Matrix3x2f> c){if(nightclient$chatPushed){delegate.updatePose(c);}else{delegate.updatePose(m->{WorldFeatureBridge.applyBetterChatClickMatrix(BetterChatMixin.this,m);c.accept(m);});}}\n'
            '            public void fill(int x1,int y1,int x2,int y2,int color){int c=WorldFeatureBridge.modifyBetterChatFill(x1,y1,x2,y2,color);if((c>>>24)!=0)delegate.fill(x1,y1,x2,y2,c);}\n'
            '            public boolean handleMessage(int y,float alpha,FormattedCharSequence line){return delegate.handleMessage(y,alpha,line);}\n'
            '            public void handleTag(int x1,int y1,int x2,int y2,float alpha,GuiMessageTag tag){if(!WorldFeatureBridge.betterChatHideSignatureBar())delegate.handleTag(x1,y1,x2,y2,alpha,tag);}\n'
            '            public void handleTagIcon(int x,int y,boolean focused,GuiMessageTag tag,GuiMessageTag.Icon icon){if(!WorldFeatureBridge.betterChatHideSignatureBar())delegate.handleTagIcon(x,y,focused,tag,icon);}\n'
            '        };\n'
            '    }'
        )
    elif v>=(1,21,6):
        values['BETTER_CHAT_HISTORY_METHODS']='{"addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V","addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V"}'
        values['BETTER_CHAT_ADD_HOOK']='addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V'
        values['BETTER_CHAT_TAG_HOOK']='@ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n    private net.minecraft.client.GuiMessageTag nightclient$hideChatTag(net.minecraft.client.GuiMessageTag tag){return WorldFeatureBridge.betterChatHideSignatureBar()?null:tag;}'
        values['BETTER_CHAT_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['BETTER_CHAT_RENDER_HOOKS']=(
            '@Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",at=@At("HEAD"))\n'
            '    private void nightclient$chatBegin(GuiGraphics graphics,int tickCount,int mouseX,int mouseY,boolean focused,CallbackInfo cb){\n'
            '        nightclient$chatPushed=WorldFeatureBridge.beginBetterChatRender(graphics,this,tickCount);\n'
            '    }\n'
            '    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",at=@At("RETURN"))\n'
            '    private void nightclient$chatEnd(GuiGraphics graphics,int tickCount,int mouseX,int mouseY,boolean focused,CallbackInfo cb){\n'
            '        if(nightclient$chatPushed){WorldFeatureBridge.endBetterChatRender(graphics);nightclient$chatPushed=false;}\n'
            '    }\n'
            '    @Redirect(method={"render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V","method_71992(Lnet/minecraft/client/gui/GuiGraphics;IFFIIIIILnet/minecraft/client/GuiMessage$Line;IF)V"},at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))\n'
            '    private void nightclient$chatFill(GuiGraphics graphics,int x1,int y1,int x2,int y2,int color){\n'
            '        int c=WorldFeatureBridge.modifyBetterChatFill(x1,y1,x2,y2,color);\n'
            '        if((c>>>24)!=0)graphics.fill(x1,y1,x2,y2,c);\n'
            '    }'+click_unmap_hooks
        )
    elif v>=(1,20,5):
        values['BETTER_CHAT_HISTORY_METHODS']='{"addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V","addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V"}'
        values['BETTER_CHAT_ADD_HOOK']='addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V'
        values['BETTER_CHAT_TAG_HOOK']='@ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n    private net.minecraft.client.GuiMessageTag nightclient$hideChatTag(net.minecraft.client.GuiMessageTag tag){return WorldFeatureBridge.betterChatHideSignatureBar()?null:tag;}'
        values['BETTER_CHAT_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['BETTER_CHAT_RENDER_HOOKS']=(
            '@Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",at=@At("HEAD"))\n'
            '    private void nightclient$chatBegin(GuiGraphics graphics,int tickCount,int mouseX,int mouseY,boolean focused,CallbackInfo cb){\n'
            '        nightclient$chatPushed=WorldFeatureBridge.beginBetterChatRender(graphics,this,tickCount);\n'
            '    }\n'
            '    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",at=@At("RETURN"))\n'
            '    private void nightclient$chatEnd(GuiGraphics graphics,int tickCount,int mouseX,int mouseY,boolean focused,CallbackInfo cb){\n'
            '        if(nightclient$chatPushed){WorldFeatureBridge.endBetterChatRender(graphics);nightclient$chatPushed=false;}\n'
            '    }\n'
            '    @Redirect(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))\n'
            '    private void nightclient$chatFill(GuiGraphics graphics,int x1,int y1,int x2,int y2,int color){\n'
            '        int c=WorldFeatureBridge.modifyBetterChatFill(x1,y1,x2,y2,color);\n'
            '        if((c>>>24)!=0)graphics.fill(x1,y1,x2,y2,c);\n'
            '    }'+click_unmap_hooks
        )
    else:
        values['BETTER_CHAT_HISTORY_METHODS']='"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V"'
        values['BETTER_CHAT_ADD_HOOK']='addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V'
        values['BETTER_CHAT_TAG_HOOK']='@ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)\n    private net.minecraft.client.GuiMessageTag nightclient$hideChatTag(net.minecraft.client.GuiMessageTag tag){return WorldFeatureBridge.betterChatHideSignatureBar()?null:tag;}'
        values['BETTER_CHAT_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['BETTER_CHAT_RENDER_HOOKS']=(
            '@Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;III)V",at=@At("HEAD"))\n'
            '    private void nightclient$chatBegin(GuiGraphics graphics,int tickCount,int mouseX,int mouseY,CallbackInfo cb){\n'
            '        nightclient$chatPushed=WorldFeatureBridge.beginBetterChatRender(graphics,this,tickCount);\n'
            '    }\n'
            '    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;III)V",at=@At("RETURN"))\n'
            '    private void nightclient$chatEnd(GuiGraphics graphics,int tickCount,int mouseX,int mouseY,CallbackInfo cb){\n'
            '        if(nightclient$chatPushed){WorldFeatureBridge.endBetterChatRender(graphics);nightclient$chatPushed=false;}\n'
            '    }\n'
            '    @Redirect(method="render(Lnet/minecraft/client/gui/GuiGraphics;III)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))\n'
            '    private void nightclient$chatFill(GuiGraphics graphics,int x1,int y1,int x2,int y2,int color){\n'
            '        int c=WorldFeatureBridge.modifyBetterChatFill(x1,y1,x2,y2,color);\n'
            '        if((c>>>24)!=0)graphics.fill(x1,y1,x2,y2,c);\n'
            '    }'+click_unmap_hooks
        )
    values['SEND_CLIENT_MESSAGE']='mc.player.sendSystemMessage(net.minecraft.network.chat.Component.nullToEmpty(message));' if v>=(26,1) else 'mc.player.displayClientMessage(net.minecraft.network.chat.Component.nullToEmpty(message),false);'
    values['SET_SCREEN'] = 'mc.gui.setScreen(nextScreen);' if v >= (26, 2) else 'mc.setScreen(nextScreen);'
    values['SET_OVERLAY_MESSAGE'] = (
        'if(mc.gui!=null&&mc.gui.hud!=null)mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.nullToEmpty(msg),false);'
        if v >= (26, 2)
        else 'if(mc.gui!=null)mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.nullToEmpty(msg),false);'
    )
    values['SEND_COMMAND_TO_SERVER'] = 'if(mc.getConnection()!=null)mc.getConnection().sendCommand(command.startsWith("/")?command.substring(1):command);'
    values['EXTRACT_STACK_TAG'] = 'stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)!=null?stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag():null' if v >= (1, 20, 5) else 'stack.hasTag()?stack.getTag():null'
    if v >= (1, 21, 5):
        values['NBT_HELPERS'] = (
            'private static net.minecraft.nbt.CompoundTag nbtCompound(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?new net.minecraft.nbt.CompoundTag():tag.getCompoundOrEmpty(key);}\n'
            '    private static String nbtString(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?"":tag.getStringOr(key,"");}\n'
            '    private static int nbtInt(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?0:tag.getIntOr(key,0);}\n'
            '    private static long nbtLong(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?0L:tag.getLongOr(key,0L);}\n'
            '    private static java.util.Set<String> nbtKeys(net.minecraft.nbt.CompoundTag tag){return tag==null?Collections.<String>emptySet():tag.keySet();}'
        )
    else:
        values['NBT_HELPERS'] = (
            'private static net.minecraft.nbt.CompoundTag nbtCompound(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?new net.minecraft.nbt.CompoundTag():tag.getCompound(key);}\n'
            '    private static String nbtString(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?"":tag.getString(key);}\n'
            '    private static int nbtInt(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?0:tag.getInt(key);}\n'
            '    private static long nbtLong(net.minecraft.nbt.CompoundTag tag,String key){return tag==null?0L:tag.getLong(key);}\n'
            '    private static java.util.Set<String> nbtKeys(net.minecraft.nbt.CompoundTag tag){return tag==null?Collections.<String>emptySet():tag.getAllKeys();}'
        )
    values['ITEM_COOLDOWN_BY_ITEM'] = (
        'mc.player.getCooldowns().isOnCooldown(new ItemStack(item))' if v >= (1, 21, 2)
        else 'mc.player.getCooldowns().isOnCooldown(item)'
    )
    values['LEVEL_Y_BOUNDS'] = (
        'double worldMinY=mc.level.getMinY();double worldMaxY=mc.level.getMaxY();'
        if v >= (1, 21, 2)
        else 'double worldMinY=mc.level.getMinBuildHeight();double worldMaxY=mc.level.getMaxBuildHeight();'
    )
    values['TAB_LIST_OBJECTIVE'] = (
        'sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST)'
        if v >= (1, 20, 2)
        else 'sb.getDisplayObjective(0)'
    )
    values['TAB_ANIM_DRAW'] = (
        'tabList.extractRenderState(graphics,screenW,sb,obj);'
        if v >= (26, 1)
        else 'tabList.render(graphics,screenW,sb,obj);'
    )
    if v >= (1, 21, 6):
        values['TAB_ANIM_PUSH_TRANSFORM'] = (
            'graphics.pose().pushMatrix();'
            'if(frame.slideFraction>0.001f){graphics.pose().translate(0f,-frame.slideFraction*120f);}'
            'else{graphics.pose().translate(cx,0f);graphics.pose().scale(frame.scale,frame.scale);graphics.pose().translate(-cx,0f);}'
        )
        values['TAB_ANIM_POP_TRANSFORM'] = 'graphics.pose().popMatrix();'
    else:
        values['TAB_ANIM_PUSH_TRANSFORM'] = (
            'graphics.pose().pushPose();'
            'if(frame.slideFraction>0.001f){graphics.pose().translate(0f,-frame.slideFraction*120f,0f);}'
            'else{graphics.pose().translate(cx,0f,0f);graphics.pose().scale(frame.scale,frame.scale,1f);graphics.pose().translate(-cx,0f,0f);}'
        )
        values['TAB_ANIM_POP_TRANSFORM'] = 'graphics.pose().popPose();'
    values['SLOT_FILL_CALL'] = 'graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,color);'
    values['FPS_CODE'] = 'return Math.max(0,Minecraft.getInstance().getFps());'
    values['PLAYER_INVENTORY'] = 'mc.player.getInventory()'
    if v >= (1, 20, 5):
        values['READ_LORE_BODY'] = 'net.minecraft.world.item.component.ItemLore lore=stack.get(net.minecraft.core.component.DataComponents.LORE);if(lore==null)return "";StringBuilder sb=new StringBuilder();for(net.minecraft.network.chat.Component c:lore.lines()){if(sb.length()>0)sb.append(\'\\n\');sb.append(c.getString());}return sb.toString();'
        values['EXTRACT_POTION_COLOR'] = '(stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS)!=null?stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).getColor():-1)'
    else:
        values['READ_LORE_BODY'] = 'net.minecraft.nbt.CompoundTag tag=stack.getTag();if(tag==null||!tag.contains("display",10))return "";net.minecraft.nbt.CompoundTag disp=tag.getCompound("display");if(!disp.contains("Lore",9))return "";net.minecraft.nbt.ListTag list=disp.getList("Lore",8);StringBuilder sb=new StringBuilder();for(int li=0;li<list.size();li++){if(sb.length()>0)sb.append(\'\\n\');sb.append(extractJsonComponentText(list.getString(li)));}return sb.toString();'
        values['EXTRACT_POTION_COLOR'] = 'net.minecraft.world.item.alchemy.PotionUtils.getColor(stack)'
    values['SET_SELECTED_SLOT'] = 'inv.setSelectedSlot(match);' if v >= (1, 21, 5) else 'inv.selected=match;'
    values['GET_SELECTED_SLOT'] = 'inv.getSelectedSlot()' if v >= (1, 21, 5) else 'inv.selected'
    values['USE_MAIN_HAND_ITEM'] = 'mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND);'
    values['ITEM_ON_COOLDOWN'] = (
        'player.getCooldowns().isOnCooldown(stack)' if v >= (1, 21, 2)
        else 'player.getCooldowns().isOnCooldown(stack.getItem())'
    )
    values['HANDLE_CONTAINER_CLICK'] = 'handleContainerInput' if v >= (26, 1) else 'handleInventoryMouseClick'
    values['OTHER_SWINGING'] = 'other.isSwinging()||other.getSwingAnimation(1.0f)>0.01f' if v >= (26, 3) else 'other.swinging||other.attackAnim>0.01f'
    values['SOUND_ENGINE_PLAY_HOOK'] = 'play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;' if v >= (1, 21, 6) else 'play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V'
    values['SOUND_INSTANCE_ID'] = 'sound.getIdentifier()' if v >= (1, 21, 11) else 'sound.getLocation()'
    values['ACTION_BAR_PACKET_TEXT'] = 'packet.text()' if v >= (1, 20, 5) else 'packet.getText()'
    if v >= (26, 3):
        values['CURSOR_FIX_CENTER_CALL'] = 'com.mojang.blaze3d.platform.InputConstants.releaseMouse(mc.getWindow(),cx,cy);'
    elif v >= (1, 21, 9):
        values['CURSOR_FIX_CENTER_CALL'] = 'com.mojang.blaze3d.platform.InputConstants.grabOrReleaseMouse(mc.getWindow(),212993,cx,cy);'
    else:
        values['CURSOR_FIX_CENTER_CALL'] = 'com.mojang.blaze3d.platform.InputConstants.grabOrReleaseMouse(mc.getWindow().getWindow(),212993,cx,cy);'
    values['RENDER_STACK_TOOLTIP'] = 'graphics.setTooltipForNextFrame(mc.font,inner,mouseX,mouseY);' if v >= (1, 21, 6) else 'graphics.renderTooltip(mc.font,inner,mouseX,mouseY);'
    values['RESISTANCE_EFFECT'] = 'net.minecraft.world.effect.MobEffects.RESISTANCE' if v >= (1, 21, 5) else 'net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE'
    values['NAMETAG_DISTANCE_DESC'] = '' if v < (1, 21, 2) else 'D'
    values['NAMETAG_DISTANCE_ARG'] = '' if v < (1, 21, 2) else 'double distanceSq,'
    values['NAMETAG_DISTANCE_SQ'] = 'minecraft.player.distanceToSqr(entity)' if v < (1, 21, 2) else 'distanceSq'
    values['NAMETAG_GLOW'] = 'isCurrentlyGlowing'
    values['POSE_ROTATION_TARGET']=p['rotationTarget']
    values['ROTATION_ARGS']=('com.mojang.math.Axis axis,float angle' if v>=(26,3) else p['rotationTarget'].split('(',1)[1].split(';',1)[0][1:].replace('/','.')+' rotation')
    values['ORIGINAL_ROTATION']='target.rotate(axis,angle);' if v>=(26,3) else 'target.mulPose(rotation);'
    values['PHYSICS_ROTATE']='pose.rotate(com.mojang.math.Axis.YP,physics.y*(float)Math.PI/180);pose.rotate(com.mojang.math.Axis.XP,physics.x*(float)Math.PI/180);pose.rotate(com.mojang.math.Axis.ZP,physics.z*(float)Math.PI/180);' if v>=(26,3) else 'pose.mulPose('+values['ROTATE_Y']+');pose.mulPose('+values['ROTATE_X']+');pose.mulPose('+values['ROTATE_Z']+');'
    if v>=(1,21,9):
        camera='net.minecraft.client.renderer.state.level.CameraRenderState' if native else 'net.minecraft.client.renderer.state.CameraRenderState'
        item_args='ItemEntityRenderState state,PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector buffers,'+camera+' camera'
    elif modern_state:item_args='ItemEntityRenderState state,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light'
    else:item_args='ItemEntity entity,float yaw,float delta,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light'
    values['ITEM_ARGS']=item_args+',';values['ITEM_ARGS_NO_COMMA']=item_args
    float_translate=p['translateTarget']=='(FFF)V'
    values['TRANSLATE_DESC']='FFF' if float_translate else 'DDD'
    values['TRANSLATE_ARGS']='float x,float y,float z' if float_translate else 'double x,double y,double z'
    if float_translate:values['TRANSLATE_CALL']='target.translate(x,physics.enabled&&physics.grounded?.0625f:y,z);'
    else:values['TRANSLATE_CALL']='target.translate(x,physics.enabled&&physics.grounded?.0625:y,z);'

    if native:
        texture_package='com.mojang.renderpearl.api.textures' if v>=(26,3) else 'com.mojang.blaze3d.textures'
        values['LIGHT_IMPORTS']='import '+texture_package+'.GpuTexture;\nimport com.mojang.blaze3d.systems.RenderSystem;'
        values['LIGHT_FIELDS']='@Shadow @org.spongepowered.asm.mixin.Final private GpuTexture texture;'
        values['LIGHT_FORCE_REBUILD']='state.needsUpdate=true;'
        values['LIGHT_MARK_CLEAN']=''
        clear='new org.joml.Vector4f(policy.illumination,policy.illumination,policy.illumination,1)' if v>=(26,2) else '0xff000000|Math.round(policy.illumination*255)*0x010101'
        values['LIGHT_FILL']='RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture,'+clear+');'
    elif gpu:
        values['LIGHT_IMPORTS']='import com.mojang.blaze3d.textures.GpuTexture;\nimport com.mojang.blaze3d.systems.RenderSystem;'
        values['LIGHT_FIELDS']='@Shadow private boolean updateLightTexture;\n@Shadow @org.spongepowered.asm.mixin.Final private GpuTexture texture;'
        values['LIGHT_FORCE_REBUILD']='updateLightTexture=true;'
        values['LIGHT_MARK_CLEAN']='updateLightTexture=false;'
        values['LIGHT_FILL']='RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture,0xff000000|Math.round(policy.illumination*255)*0x010101);'
    elif modern_state:
        values['LIGHT_IMPORTS']='import com.mojang.blaze3d.pipeline.TextureTarget;'
        values['LIGHT_FIELDS']='@Shadow private boolean updateLightTexture;\n@Shadow @org.spongepowered.asm.mixin.Final private TextureTarget target;'
        values['LIGHT_FORCE_REBUILD']='updateLightTexture=true;'
        values['LIGHT_MARK_CLEAN']='updateLightTexture=false;'
        values['LIGHT_FILL']='''int drawFramebuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFramebuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        float[] color=new float[4];org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE,color);
        java.nio.ByteBuffer colorMask=org.lwjgl.BufferUtils.createByteBuffer(4);org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK,colorMask);
        boolean scissor=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        try{org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glColorMask(true,true,true,true);org.lwjgl.opengl.GL11.glClearColor(policy.illumination,policy.illumination,policy.illumination,1);org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);}
        finally{org.lwjgl.opengl.GL11.glColorMask(colorMask.get(0)!=0,colorMask.get(1)!=0,colorMask.get(2)!=0,colorMask.get(3)!=0);if(scissor)org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glClearColor(color[0],color[1],color[2],color[3]);org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,drawFramebuffer);org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,readFramebuffer);}'''
    else:
        values['LIGHT_IMPORTS']='import com.mojang.blaze3d.platform.NativeImage;\nimport net.minecraft.client.renderer.texture.DynamicTexture;'
        values['LIGHT_FIELDS']='@Shadow private boolean updateLightTexture;\n@Shadow @org.spongepowered.asm.mixin.Final private NativeImage lightPixels;\n@Shadow @org.spongepowered.asm.mixin.Final private DynamicTexture lightTexture;'
        values['LIGHT_FORCE_REBUILD']='updateLightTexture=true;'
        values['LIGHT_MARK_CLEAN']='updateLightTexture=false;'
        values['LIGHT_FILL']='int rgb=0xff000000|Math.round(policy.illumination*255)*0x010101;for(int y=0;y<16;y++)for(int x=0;x<16;x++)lightPixels.setPixelRGBA(x,y,rgb);lightTexture.upload();'

    camera_get='mc.gameRenderer.mainCamera()' if v>=(26,2) else 'mc.gameRenderer.getMainCamera()'
    camera_pos='camera.position()' if v>=(1,21,11) else 'camera.getPosition()'
    camera_pitch='camera.xRot()' if v>=(1,21,11) else 'camera.getXRot()'
    camera_yaw='camera.yRot()' if v>=(1,21,11) else 'camera.getYRot()'
    fov='camera.getFov()' if native else '(ClientRuntime.get()==null?70f:ClientRuntime.get().effectiveFov())'
    view_setup='net.minecraft.client.Camera camera='+camera_get+';Vec3 cameraPosition='+camera_pos+';int pixelWidth=mc.getWindow().getWidth(),pixelHeight=mc.getWindow().getHeight();dev.nightclient.renderer.WorldRibbon.View view=new dev.nightclient.renderer.WorldRibbon.View(cameraPosition.x,cameraPosition.y,cameraPosition.z,'+camera_pitch+','+camera_yaw+','+fov+',CameraFeatureBridge.aspect(pixelWidth/(float)Math.max(1,pixelHeight)),pixelWidth,pixelHeight);'
    values['HITBOX_BUFFER_ACQUIRE']=''
    values['CAMERA_VIEW_SETUP']='net.minecraft.client.Camera camera='+camera_get+';Vec3 cameraPosition='+camera_pos+';if(cameraPosition==null||(cameraPosition.x==0&&cameraPosition.y==0&&cameraPosition.z==0&&mc.player!=null))cameraPosition=mc.player.getEyePosition(1.0f);dev.nightclient.renderer.WorldRibbon.View view=new dev.nightclient.renderer.WorldRibbon.View(cameraPosition.x,cameraPosition.y,cameraPosition.z,'+camera_pitch+','+camera_yaw+','+fov+',CameraFeatureBridge.aspect(width/(float)Math.max(1,height)),width,height);'
    values['CAMERA_POS_SETUP']='net.minecraft.client.Camera camera='+camera_get+';Vec3 camPos='+camera_pos+';if(camPos==null||(camPos.x==0&&camPos.y==0&&camPos.z==0&&mc.player!=null))camPos=mc.player.getEyePosition(delta);'
    values['CAMERA_POSE_SETUP']='net.minecraft.client.Camera camera='+camera_get+';Vec3 camPos='+camera_pos+';if(camPos==null||(camPos.x==0&&camPos.y==0&&camPos.z==0&&mc.player!=null))camPos=mc.player.getEyePosition(delta);float camPitch='+camera_pitch+',camYaw='+camera_yaw+';'
    values['DIMENSION']='mc.level.dimension().identifier().toString()' if v>=(1,21,11) else 'mc.level.dimension().location().toString()'
    values['WORLD_SPAWN_KEY']=(
        'net.minecraft.world.level.storage.LevelData.RespawnData rd=mc.level.getLevelData().getRespawnData();net.minecraft.core.BlockPos p=rd==null?null:rd.pos();return p==null?"":(p.getX()+"_"+p.getY()+"_"+p.getZ());' if v>=(1,21,9)
        else 'net.minecraft.core.BlockPos p=mc.level.getLevelData().getSpawnPos();return p==null?"":(p.getX()+"_"+p.getY()+"_"+p.getZ());' if v>=(1,20,5)
        else 'return mc.level.getLevelData().getXSpawn()+"_"+mc.level.getLevelData().getYSpawn()+"_"+mc.level.getLevelData().getZSpawn();')
    values['WORLD_IMPORTS']+='\nimport net.minecraft.resources.'+cosmetics_id+';'
    values['ALT_DOWN']='mc.hasAltDown()' if v>=(1,21,9) else 'net.minecraft.client.gui.screens.Screen.hasAltDown()'
    values['CTRL_DOWN']='mc.hasControlDown()' if v>=(1,21,9) else 'net.minecraft.client.gui.screens.Screen.hasControlDown()'
    values['TNT_FUSE_GET']='tnt.getFuse()'
    if v<(1,21,2):
        values['BLOCK_OUTLINE_HOOK']='renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V'
        values['BLOCK_OUTLINE_ARGS']='com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vc,net.minecraft.world.entity.Entity entity,double x,double y,double z,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,'
    elif v<(1,21,9):
        values['BLOCK_OUTLINE_HOOK']='renderBlockOutline(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;Z)V'
        values['BLOCK_OUTLINE_ARGS']='net.minecraft.client.Camera cam,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,com.mojang.blaze3d.vertex.PoseStack pose,boolean translucent,'
    elif v<(26,1):
        values['BLOCK_OUTLINE_HOOK']='renderBlockOutline(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/client/renderer/state/LevelRenderState;)V'
        values['BLOCK_OUTLINE_ARGS']='net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,com.mojang.blaze3d.vertex.PoseStack pose,boolean translucent,net.minecraft.client.renderer.state.LevelRenderState state,'
    elif v<(26,2):
        values['BLOCK_OUTLINE_HOOK']='renderBlockOutline(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;)V'
        values['BLOCK_OUTLINE_ARGS']='net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,com.mojang.blaze3d.vertex.PoseStack pose,boolean translucent,net.minecraft.client.renderer.state.level.LevelRenderState state,'
    else:
        values['BLOCK_OUTLINE_HOOK']='submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V'
        values['BLOCK_OUTLINE_ARGS']='com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector collector,net.minecraft.client.renderer.state.level.LevelRenderState state,'
    if v>=(1,21,11):
        values['GLINT_OWNER']='net.minecraft.client.renderer.rendertype.TextureTransform'
        values['GLINT_SHORT']='TextureTransform'
        values['GLINT_METHOD']='setupGlintTexturing(F)Lorg/joml/Matrix4f;'
        values['GLINT_TIME_TARGET']='Lnet/minecraft/util/Util;getMillis()J'
    else:
        values['GLINT_OWNER']='net.minecraft.client.renderer.RenderStateShard'
        values['GLINT_SHORT']='RenderStateShard'
        values['GLINT_METHOD']='setupGlintTexturing(F)V'
        values['GLINT_TIME_TARGET']='Lnet/minecraft/Util;getMillis()J'
    values['GLINT_TEXTURE_PATHS']='"minecraft:textures/misc/enchanted_glint_item.png","minecraft:textures/misc/enchanted_glint_entity.png"'
    values['OPEN_RESOURCE_STREAM']='mc.getResourceManager().open(id)'
    values['NATIVE_GET_ARGB']='src.getPixel(x,y)' if v>=(1,21,2) else '((src.getPixelRGBA(x,y)&0xff00ff00)|((src.getPixelRGBA(x,y)&255)<<16)|((src.getPixelRGBA(x,y)>>>16)&255))'
    if v>=(1,21,11):
        values['GLINT_MAKE_TEXTURE']='new net.minecraft.client.renderer.texture.DynamicTexture(()->path,image){{if(existing!=null&&existing.getSampler()!=null)this.sampler=existing.getSampler();}}'
    elif v>=(1,21,5):
        values['GLINT_MAKE_TEXTURE']='new net.minecraft.client.renderer.texture.DynamicTexture(()->path,image){{setFilter(true,false);setClamp(false);}}'
    elif v>=(1,21,4):
        values['GLINT_MAKE_TEXTURE']='new net.minecraft.client.renderer.texture.DynamicTexture(image){{setFilter(true,false);setClamp(false);}}'
    else:
        values['GLINT_MAKE_TEXTURE']='new net.minecraft.client.renderer.texture.DynamicTexture(image){{setFilter(true,false);}}'
    if v<(1,21,2):
        values['EXPLODE_PACKET_BODY']='long nowMs=System.currentTimeMillis();double ex=packet.getX(),ey=packet.getY(),ez=packet.getZ();notifyExplosiveThingExplosion(runtime,ex,ey,ez,nowMs);if(learnExp){float pw=packet.getPower();float maxD=0f;for(net.minecraft.core.BlockPos bp:packet.getToBlow()){double dx=bp.getX()+0.5-ex,dy=bp.getY()+0.5-ey,dz=bp.getZ()+0.5-ez;float d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(d>maxD)maxD=d;}runtime.explosionLearner().recordExplosion(ex,ey,ez,pw,maxD,nowMs);}'
    elif v<(1,21,9):
        values['EXPLODE_PACKET_BODY']='long nowMs=System.currentTimeMillis();Vec3 c=packet.center();notifyExplosiveThingExplosion(runtime,c.x,c.y,c.z,nowMs);if(learnExp)runtime.explosionLearner().recordExplosion(c.x,c.y,c.z,0f,0f,nowMs);'
    else:
        values['EXPLODE_PACKET_BODY']='long nowMs=System.currentTimeMillis();Vec3 c=packet.center();notifyExplosiveThingExplosion(runtime,c.x,c.y,c.z,nowMs);if(learnExp){float pw=packet.radius();runtime.explosionLearner().recordExplosion(c.x,c.y,c.z,pw,0f,nowMs);}'
    if v>=(1,20,5):
        values['ARMOR_EPF_LOOP']='for(it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> e:stack.getEnchantments().entrySet()){String k=e.getKey().toString().toLowerCase(Locale.ROOT);int lvl=e.getIntValue();if(k.contains("blast_protection"))epf+=lvl*2;else if(k.contains("protection")&&!k.contains("fire")&&!k.contains("projectile"))epf+=lvl;}'
    else:
        values['ARMOR_EPF_LOOP']='net.minecraft.nbt.ListTag elist=stack.getEnchantmentTags();for(int ei=0;ei<elist.size();ei++){net.minecraft.nbt.CompoundTag et=elist.getCompound(ei);String k=et.getString("id").toLowerCase(Locale.ROOT);int lvl=et.getInt("lvl");if(k.contains("blast_protection"))epf+=lvl*2;else if(k.contains("protection")&&!k.contains("fire")&&!k.contains("projectile"))epf+=lvl;}'
    if v>=(26,1):
        values['EXTRACT_CONTAINER_ITEMS']='net.minecraft.world.item.component.ItemContainerContents cont=stack.get(net.minecraft.core.component.DataComponents.CONTAINER);if(cont!=null){net.minecraft.core.NonNullList<ItemStack> grid=net.minecraft.core.NonNullList.withSize(27,ItemStack.EMPTY);cont.copyInto(grid);out.addAll(grid);isBundleOut[0]=false;fullnessOut[0]=0f;return true;}net.minecraft.world.item.component.BundleContents bundle=stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);if(bundle!=null){for(net.minecraft.world.item.ItemStackTemplate tpl:bundle.items()){if(tpl!=null){ItemStack s=tpl.create();if(!s.isEmpty())out.add(s);}}isBundleOut[0]=true;fullnessOut[0]=bundle.weight().result().map(f->Math.max(0f,Math.min(1f,f.floatValue()))).orElse(0f);return true;}return false;'
    elif v>=(1,20,5):
        values['EXTRACT_CONTAINER_ITEMS']='net.minecraft.world.item.component.ItemContainerContents cont=stack.get(net.minecraft.core.component.DataComponents.CONTAINER);if(cont!=null){net.minecraft.core.NonNullList<ItemStack> grid=net.minecraft.core.NonNullList.withSize(27,ItemStack.EMPTY);cont.copyInto(grid);out.addAll(grid);isBundleOut[0]=false;fullnessOut[0]=0f;return true;}net.minecraft.world.item.component.BundleContents bundle=stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);if(bundle!=null){for(ItemStack s:bundle.items())if(s!=null&&!s.isEmpty())out.add(s);isBundleOut[0]=true;fullnessOut[0]=Math.max(0f,Math.min(1f,bundle.weight().floatValue()));return true;}return false;'
    else:
        values['EXTRACT_CONTAINER_ITEMS']='net.minecraft.nbt.CompoundTag tag=stack.getTag();if(tag==null)return false;if(tag.contains("BlockEntityTag",10)){net.minecraft.nbt.CompoundTag bet=tag.getCompound("BlockEntityTag");if(bet.contains("Items",9)){net.minecraft.nbt.ListTag list=bet.getList("Items",10);ItemStack[] grid=new ItemStack[27];Arrays.fill(grid,ItemStack.EMPTY);for(int li=0;li<list.size();li++){net.minecraft.nbt.CompoundTag entry=list.getCompound(li);int sl=entry.getByte("Slot")&255;ItemStack s=ItemStack.of(entry);if(!s.isEmpty()&&sl<27)grid[sl]=s;}for(int gi=0;gi<27;gi++)out.add(grid[gi]);isBundleOut[0]=false;fullnessOut[0]=0f;return true;}}if(tag.contains("Items",9)){net.minecraft.nbt.ListTag list=tag.getList("Items",10);float weight=0f;for(int li=0;li<list.size();li++){ItemStack s=ItemStack.of(list.getCompound(li));if(!s.isEmpty()){out.add(s);weight+=s.getCount()/(float)Math.max(1,s.getMaxStackSize());}}isBundleOut[0]=true;fullnessOut[0]=Math.max(0f,Math.min(1f,weight));return !out.isEmpty();}return false;'
    if v>=(26,1):
        values['CONTAINER_TOOLTIP_IMPORTS']='import net.minecraft.client.gui.GuiGraphicsExtractor;'
        values['CONTAINER_TOOLTIP_HOOK']='extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V'
        values['CONTAINER_SLOT_HOOK']='extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V'
        values['CONTAINER_SLOT_ARGS']='GuiGraphicsExtractor graphics,net.minecraft.world.inventory.Slot slot,int mouseX,int mouseY,'
        values['TOOLTIP_BG_IMPORTS']='import net.minecraft.client.gui.GuiGraphicsExtractor;\nimport net.minecraft.resources.Identifier;'
        values['TOOLTIP_BG_HOOK']='extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V'
        values['TOOLTIP_BG_ARGS']='GuiGraphicsExtractor graphics,int x,int y,int width,int height,Identifier bg,'
    elif v==(1,21,11):
        values['CONTAINER_TOOLTIP_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['CONTAINER_TOOLTIP_HOOK']='renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V'
        values['CONTAINER_SLOT_HOOK']='renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;II)V'
        values['CONTAINER_SLOT_ARGS']='GuiGraphics graphics,net.minecraft.world.inventory.Slot slot,int mouseX,int mouseY,'
        values['TOOLTIP_BG_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.resources.Identifier;'
        values['TOOLTIP_BG_HOOK']='renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/resources/Identifier;)V'
        values['TOOLTIP_BG_ARGS']='GuiGraphics graphics,int x,int y,int width,int height,Identifier bg,'
    elif v>=(1,21,6):
        values['CONTAINER_TOOLTIP_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['CONTAINER_TOOLTIP_HOOK']='renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V'
        values['CONTAINER_SLOT_HOOK']='renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V'
        values['CONTAINER_SLOT_ARGS']='GuiGraphics graphics,net.minecraft.world.inventory.Slot slot,'
        values['TOOLTIP_BG_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.resources.ResourceLocation;'
        values['TOOLTIP_BG_HOOK']='renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/resources/ResourceLocation;)V'
        values['TOOLTIP_BG_ARGS']='GuiGraphics graphics,int x,int y,int width,int height,ResourceLocation bg,'
    elif v>=(1,21,2):
        values['CONTAINER_TOOLTIP_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['CONTAINER_TOOLTIP_HOOK']='renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V'
        values['CONTAINER_SLOT_HOOK']='renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V'
        values['CONTAINER_SLOT_ARGS']='GuiGraphics graphics,net.minecraft.world.inventory.Slot slot,'
        values['TOOLTIP_BG_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.resources.ResourceLocation;'
        values['TOOLTIP_BG_HOOK']='renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIILnet/minecraft/resources/ResourceLocation;)V'
        values['TOOLTIP_BG_ARGS']='GuiGraphics graphics,int x,int y,int width,int height,int z,ResourceLocation bg,'
    elif v>=(1,20):
        values['CONTAINER_TOOLTIP_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['CONTAINER_TOOLTIP_HOOK']='renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V'
        values['CONTAINER_SLOT_HOOK']='renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V'
        values['CONTAINER_SLOT_ARGS']='GuiGraphics graphics,net.minecraft.world.inventory.Slot slot,'
        values['TOOLTIP_BG_IMPORTS']='import net.minecraft.client.gui.GuiGraphics;'
        values['TOOLTIP_BG_HOOK']='renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V'
        values['TOOLTIP_BG_ARGS']='GuiGraphics graphics,int x,int y,int width,int height,int z,'
    else:
        values['CONTAINER_TOOLTIP_IMPORTS']='import com.mojang.blaze3d.vertex.PoseStack;'
        values['CONTAINER_TOOLTIP_HOOK']='renderTooltip(Lcom/mojang/blaze3d/vertex/PoseStack;II)V'
        values['CONTAINER_SLOT_HOOK']='renderSlot(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/inventory/Slot;)V'
        values['CONTAINER_SLOT_ARGS']='PoseStack graphics,net.minecraft.world.inventory.Slot slot,'
        values['TOOLTIP_BG_IMPORTS']=''
        values['TOOLTIP_BG_HOOK']=''
        values['TOOLTIP_BG_ARGS']=''
    if gizmos:
        values['WORLD_IMPORTS']+='\nimport net.minecraft.gizmos.Gizmos;'
        values['BOX_IMPORTS']='import net.minecraft.client.renderer.culling.Frustum;'
        values['DEBUG_ARGS']='Frustum frustum,double cameraX,double cameraY,double cameraZ,float delta,'
        values['BOX_CALL']='WorldFeatureBridge.boxes(delta);'
        values['HITBOX_RENDER']='''public static void boxes(float delta){
        Minecraft mc=Minecraft.getInstance();Policy p=policy;if(!(p.hitbox||p.hitboxFill||p.invisibleArmorHitbox||p.blockOverlay||p.explosionRing||p.hwHelperEsp)||mc.level==null||mc.player==null)return;
        '''+view_setup+'''
        if(p.hitbox||p.hitboxFill||p.invisibleArmorHitbox){
            int count=0;for(Entity entity:mc.level.entitiesForRendering()){
                if(!boxEligible(entity,p))continue;boolean outline=boxOutline(entity,p);int fill=boxFill(entity,p);if(!outline&&(fill>>>24)==0)continue;if(++count>256)break;
                AABB bounds=entity.getBoundingBox().move(entity.getPosition(delta).subtract(entity.position()));
                Vec3 eye=p.hitbox&&p.eyeLine?entity.getEyePosition(delta):null,end=eye==null?null:eye.add(entity.getViewVector(delta).scale(2));
                Gizmos.addGizmo(new HitboxRibbonGizmo(bounds,view,outline,p.boxColor,fill,p.lineWidth,eye,end));
            }
        }
        if(p.blockOverlay||p.explosionRing||p.hwHelperEsp){
            final dev.nightclient.renderer.WorldRibbon.View overlayView=view;
            final float overlayDelta=delta;
            Gizmos.addGizmo(new HitboxRibbonGizmo(q->emitWorldOverlays(q,overlayView,overlayDelta)));
        }
    }'''
    else:
        values['WORLD_IMPORTS']+='\nimport com.mojang.blaze3d.vertex.PoseStack;\nimport com.mojang.blaze3d.vertex.VertexConsumer;\nimport net.minecraft.client.renderer.MultiBufferSource;'
        values['BOX_IMPORTS']='import com.mojang.blaze3d.vertex.PoseStack;\nimport net.minecraft.client.renderer.MultiBufferSource;\nimport net.minecraft.client.renderer.culling.Frustum;'
        desc=p['debugHook']
        values['DEBUG_ARGS']='PoseStack pose,'+('Frustum frustum,' if 'Frustum;' in desc else '')+'MultiBufferSource.BufferSource buffers,double cameraX,double cameraY,double cameraZ,'+('boolean flag,' if desc.endswith('Z)V') else '')
        values['BOX_CALL']='WorldFeatureBridge.boxes(pose,buffers,cameraX,cameraY,cameraZ);'
        acquire='return buffers.getBuffer(HitboxRenderTypes.lines(p.lineWidth));'
        values['HITBOX_BUFFER_ACQUIRE']='private static VertexConsumer boxBuffer(MultiBufferSource buffers,Policy p){'+acquire+'}'
        vertex='out.addVertex(pose.last().pose(),(float)x,(float)y,(float)z).setColor(color);' if v>=(1,21) else 'out.vertex(pose.last().pose(),(float)x,(float)y,(float)z).color((color>>>16)&255,(color>>>8)&255,color&255,(color>>>24)&255).endVertex();'
        values['HITBOX_RENDER']='''public static void boxes(final PoseStack pose,MultiBufferSource buffers,final double cameraX,final double cameraY,final double cameraZ){
        Minecraft mc=Minecraft.getInstance();Policy p=policy;if(!(p.hitbox||p.hitboxFill||p.invisibleArmorHitbox||p.blockOverlay||p.explosionRing||p.hwHelperEsp)||mc.level==null||mc.player==null)return;
        float delta=mc.'''+p['partialTick']+('();' if p['partialTick']=='getFrameTime' else '().getGameTimeDeltaPartialTick(false);')+'''
        '''+view_setup+'''
        // Empty fill-only frames must leave vanilla's pending batches untouched.
        dev.nightclient.renderer.WorldRibbon.Quads quads=new dev.nightclient.renderer.WorldRibbon.Quads(){private VertexConsumer out;public void quad(double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz,int color){if(out==null)out=boxBuffer(buffers,p);vertex(pose,out,ax-cameraX,ay-cameraY,az-cameraZ,color);vertex(pose,out,bx-cameraX,by-cameraY,bz-cameraZ,color);vertex(pose,out,cx-cameraX,cy-cameraY,cz-cameraZ,color);vertex(pose,out,dx-cameraX,dy-cameraY,dz-cameraZ,color);}};
        pose.pushPose();
        try{if(p.hitbox||p.hitboxFill||p.invisibleArmorHitbox){int count=0;for(Entity entity:mc.level.entitiesForRendering()){
            if(!boxEligible(entity,p))continue;boolean outline=boxOutline(entity,p);int fill=boxFill(entity,p);if(!outline&&(fill>>>24)==0)continue;if(++count>256)break;
            AABB b=entity.getBoundingBox().move(entity.getPosition(delta).subtract(entity.position()));
            drawBox(quads,view,b,outline,p.boxColor,fill,p.lineWidth);
            if(p.hitbox&&p.eyeLine){Vec3 a=entity.getEyePosition(delta),end=a.add(entity.getViewVector(delta).scale(2));dev.nightclient.renderer.WorldRibbon.line(quads,view,a.x,a.y,a.z,end.x,end.y,end.z,p.lineWidth,p.boxColor);}
        }}
        if(p.blockOverlay||p.explosionRing||p.hwHelperEsp)emitWorldOverlays(quads,view,delta);
        }finally{pose.popPose();}
    }
    private static void vertex(PoseStack pose,VertexConsumer out,double x,double y,double z,int color){'''+vertex+'}'
        values['BOX_TYPE_IMPORTS']='import com.mojang.blaze3d.vertex.DefaultVertexFormat;\nimport com.mojang.blaze3d.vertex.VertexFormat;'
        values['BOX_TYPE_SUPER']='"nightclient_hitbox",1536,false,false,()->{},()->{}' if gpu else '"nightclient_hitbox",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{}'
        values['BOX_TYPE_PREPARE']=''
        if gpu:values['BOX_TYPE_CREATE']='new HitboxGpuLineType(width)'
        else:
            fmt='DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS'
            values['BOX_TYPE_PREPARE']='final DepthTestStateShard depth=LEQUAL_DEPTH_TEST;'
            setup=['TRANSLUCENT_TRANSPARENCY','depth','NO_CULL','COLOR_WRITE','POSITION_COLOR_SHADER']
            state_setup=''.join(s+'.setupRenderState();'for s in setup)
            state_clear=''.join(s+'.clearRenderState();'for s in reversed(setup))
            values['BOX_TYPE_CREATE']='new RenderType("nightclient_hitbox",'+fmt+',1536,false,false,()->{'+state_setup+'},()->{'+state_clear+'}){}'
        if gpu:
            values['BOX_DRAW_PREPARE']='' if v==(1,21,5) else 'com.mojang.blaze3d.buffers.GpuBufferSlice transform=RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),new org.joml.Vector4f(1),'+('RenderSystem.getModelOffset()' if v<(1,21,9) else 'new org.joml.Vector3f()')+',RenderSystem.getTextureMatrix(),1);'
            values['BOX_PASS_ARGS']='target.getColorTexture(),OptionalInt.empty(),target.getDepthTexture(),OptionalDouble.empty()' if v==(1,21,5) else '()->"NightClient hitbox",RenderSystem.outputColorTextureOverride!=null?RenderSystem.outputColorTextureOverride:target.getColorTextureView(),OptionalInt.empty(),RenderSystem.outputDepthTextureOverride!=null?RenderSystem.outputDepthTextureOverride:target.getDepthTextureView(),OptionalDouble.empty()'
            values['BOX_DRAW_UNIFORMS']='' if v==(1,21,5) else 'RenderSystem.bindDefaultUniforms(pass);pass.setUniform("DynamicTransforms",transform);'
            values['BOX_DRAW_INDEXED']='pass.drawIndexed(0,mesh.drawState().indexCount());' if v==(1,21,5) else 'pass.drawIndexed(0,0,mesh.drawState().indexCount(),1);'
            values['BOX_DRAW_RESTORE']=''

    values['TAB_PING_IMPORTS']='import '+wf_context_import+';'
    values['TAB_RENDER_HOOK']=(
        'extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V' if v>=(26,1)
        else 'render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V')
    values['TAB_PING_ICON_HOOK']=(
        'extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V' if v>=(26,1)
        else 'renderPingIcon(Lnet/minecraft/client/gui/GuiGraphics;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V')
    values['TAB_ONLINE_PLAYERS']='mc.getConnection().getListedOnlinePlayers()'
    values['TAB_PING_DRAW_TEXT']=(
        'graphics.text(mc.font,label,drawX,y,color,true);' if v>=(26,1)
        else 'graphics.drawString(mc.font,label,drawX,y,color,true);')
    if v>=(26,1):
        values['TAB_NAME_DRAW_TARGET']='Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V'
        values['TAB_NAME_REDIRECT_RET']='void'
        values['TAB_NAME_REDIRECT_DEFER_RET']='return;'
        values['TAB_NAME_REDIRECT_FALLBACK']='graphics.text(font,name,x,y,color);'
        values['TAB_PING_FLUSH_NAME']='graphics.text(font,name,x,y,color);'
    elif v>=(1,21,6):
        values['TAB_NAME_DRAW_TARGET']='Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V'
        values['TAB_NAME_REDIRECT_RET']='void'
        values['TAB_NAME_REDIRECT_DEFER_RET']='return;'
        values['TAB_NAME_REDIRECT_FALLBACK']='graphics.drawString(font,name,x,y,color);'
        values['TAB_PING_FLUSH_NAME']='graphics.drawString(font,name,x,y,color);'
    else:
        values['TAB_NAME_DRAW_TARGET']='Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I'
        values['TAB_NAME_REDIRECT_RET']='int'
        values['TAB_NAME_REDIRECT_DEFER_RET']='return 0;'
        values['TAB_NAME_REDIRECT_FALLBACK']='return graphics.drawString(font,name,x,y,color);'
        values['TAB_PING_FLUSH_NAME']='graphics.drawString(font,name,x,y,color);'

    values['MAP_IMPORTS']=''
    values['MAP_CHUNK_STATUS']='net.minecraft.world.level.chunk.status.ChunkStatus' if v>=(1,20,5) else 'net.minecraft.world.level.chunk.ChunkStatus'
    values['MAP_MIN_Y']='level.getMinY()' if v>=(1,21,2) else 'level.getMinBuildHeight()'
    values['MAP_MAX_Y']='level.getMaxY()' if v>=(1,21,2) else 'level.getMaxBuildHeight()'
    values['MAP_SECTION_EMPTY']='section==null||section.hasOnlyAir()'
    values['MAP_BLOCK_REGISTRY']=registry+'.BLOCK'
    values['MAP_ITEM_REGISTRY']=registry+'.ITEM'
    values['MAP_PLAYER_SKIN']=(
        '((AbstractClientPlayer)p).getSkinTextureLocation().toString()' if v<(1,20,2)
        else '((AbstractClientPlayer)p).getSkin().texture().toString()' if v<(1,21,9)
        else '((AbstractClientPlayer)p).getSkin().body().texturePath().toString()')
    values['MAP_ENTITY_YAW']='e.getViewYRot(pt)'
    if v<(1,20,2):
        values['MAP_MIXIN_IMPORTS']='import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;\nimport net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;'
        values['MAP_CHUNK_HOOK']='handleLevelChunkWithLight(Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;)V'
        values['MAP_CHUNK_PACKET']='ClientboundLevelChunkWithLightPacket'
    else:
        values['MAP_MIXIN_IMPORTS']='import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;'
        values['MAP_CHUNK_HOOK']='handleLevelChunkWithLight(Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;)V'
        values['MAP_CHUNK_PACKET']='ClientboundLevelChunkWithLightPacket'
    values['MAP_CHUNK_X']='packet.x()' if v>=(26,3) else 'packet.getX()'
    values['MAP_CHUNK_Z']='packet.z()' if v>=(26,3) else 'packet.getZ()'
    values['MAP_SPAWN_POS']='packet.respawnData().pos()' if v>=(1,21,9) else 'packet.getPos()'
    values['MAP_CUSTOM_PAYLOAD_HOOK']=(
        '    @Inject(method="handleCustomPayload(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;)V",at=@At("HEAD"),cancellable=true)\n'
        '    private void nightclient$onCustomPayload(ClientboundCustomPayloadPacket packet,CallbackInfo callback){\n'
        '        if(packet==null)return;\n'
        '        try{\n'
        '            String ch=packet.getIdentifier().toString();\n'
        '            if(dev.nightclient.config.FeaturePolicy.CHANNEL.equals(ch)){\n'
        '                net.minecraft.network.FriendlyByteBuf buf=packet.getData();\n'
        '                int avail=Math.max(0,Math.min(262144,buf.readableBytes()));\n'
        '                byte[] bytes=new byte[avail];\n'
        '                buf.readBytes(bytes);\n'
        '                if(buf.readableBytes()>0)buf.skipBytes(buf.readableBytes());\n'
        '                LiteApiBridge.onPayload(bytes);\n'
        '                callback.cancel();\n'
        '                return;\n'
        '            }\n'
        '            if("xaeroworldmap:main".equals(ch)||"xaerominimap:main".equals(ch)){\n'
        '                net.minecraft.network.FriendlyByteBuf buf=packet.getData();\n'
        '                int idx=buf.readerIndex();\n'
        '                int avail=Math.min(32,buf.readableBytes());\n'
        '                byte[] bytes=new byte[avail];\n'
        '                buf.readBytes(bytes);\n'
        '                buf.readerIndex(idx);\n'
        '                WorldMapBridge.onCustomPayload(ch,bytes);\n'
        '            }\n'
        '        }catch(Throwable ignored){}\n'
        '    }\n'
        '    @Inject(method="onDisconnect(Lnet/minecraft/network/chat/Component;)V",at=@At("HEAD"))\n'
        '    private void nightclient$onDisconnect(net.minecraft.network.chat.Component reason,CallbackInfo callback){\n'
        '        LiteApiBridge.onDisconnect();\n'
        '    }' if v<(1,20,2) else '')
    if v<(1,20,2):
        values['LITEAPI_BRIDGE_IMPORTS']=(
            'import io.netty.buffer.Unpooled;\n'
            'import net.minecraft.network.FriendlyByteBuf;\n'
            'import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;')
        values['LITEAPI_SEND_BODY']=(
            'FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(payload.length));\n'
            '            buf.writeBytes(payload);\n'
            '            connection.send(new ServerboundCustomPayloadPacket(GameBridge.identifier(channel), buf));')
        values['LITEAPI_PAYLOAD_IMPORTS']=''
        values['LITEAPI_PAYLOAD_BODY']=''
        values['LITEAPI_DISCONNECT_IMPORT']=''
        values['LITEAPI_DISCONNECT_METHOD']=''
        values['LITEAPI_DISCONNECT_ARG']=''
    else:
        values['LITEAPI_BRIDGE_IMPORTS']='import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;'
        values['LITEAPI_SEND_BODY']='connection.send(new ServerboundCustomPayloadPacket(new LiteApiPayload(payload)));'
        if v<(1,20,5):
            values['LITEAPI_PAYLOAD_IMPORTS']=(
                'import net.minecraft.network.FriendlyByteBuf;\n'
                'import net.minecraft.resources.ResourceLocation;')
            values['LITEAPI_PAYLOAD_BODY']='''    public static LiteApiPayload read(FriendlyByteBuf buf) {
        int len = Math.max(0, Math.min(MAX_BYTES, buf.readableBytes()));
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        if (buf.readableBytes() > 0) buf.skipBytes(buf.readableBytes());
        return new LiteApiPayload(bytes);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(data);
    }

    @Override
    public ResourceLocation id() {
        return GameBridge.identifier(FeaturePolicy.CHANNEL);
    }'''
        else:
            values['LITEAPI_PAYLOAD_IMPORTS']=(
                'import io.netty.buffer.ByteBuf;\n'
                'import net.minecraft.network.codec.StreamCodec;')
            values['LITEAPI_PAYLOAD_BODY']='''    public static final CustomPacketPayload.Type<LiteApiPayload> TYPE =
        new CustomPacketPayload.Type<LiteApiPayload>(GameBridge.identifier(FeaturePolicy.CHANNEL));
    public static final StreamCodec<ByteBuf, LiteApiPayload> STREAM_CODEC =
        StreamCodec.of(LiteApiPayload::encode, LiteApiPayload::decode);

    private static void encode(ByteBuf buf, LiteApiPayload payload) {
        buf.writeBytes(payload.data);
    }

    private static LiteApiPayload decode(ByteBuf buf) {
        int len = Math.max(0, Math.min(MAX_BYTES, buf.readableBytes()));
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        if (buf.readableBytes() > 0) buf.skipBytes(buf.readableBytes());
        return new LiteApiPayload(bytes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }'''
        if v<(1,21):
            values['LITEAPI_DISCONNECT_IMPORT']='import net.minecraft.network.chat.Component;'
            values['LITEAPI_DISCONNECT_METHOD']='onDisconnect(Lnet/minecraft/network/chat/Component;)V'
            values['LITEAPI_DISCONNECT_ARG']='Component reason'
        else:
            values['LITEAPI_DISCONNECT_IMPORT']='import net.minecraft.network.DisconnectionDetails;'
            values['LITEAPI_DISCONNECT_METHOD']='onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V'
            values['LITEAPI_DISCONNECT_ARG']='DisconnectionDetails details'

    if v<(1,20,5):
        values['WAVY_CAPE_EMIT_VERTEX']=(
            'consumer.vertex(pose.pose(), xyz[p], xyz[p + 1], xyz[p + 2])'
            '.color(rgb, rgb, rgb, 255).uv(uv[t], uv[t + 1])'
            '.overlayCoords(overlay).uv2(packedLight)'
            '.normal(pose.normal(), nrm[p], nrm[p + 1], nrm[p + 2]).endVertex();')
    elif v<(1,21):
        values['WAVY_CAPE_EMIT_VERTEX']=(
            'consumer.vertex(pose, xyz[p], xyz[p + 1], xyz[p + 2])'
            '.color(rgb, rgb, rgb, 255).uv(uv[t], uv[t + 1])'
            '.overlayCoords(overlay).uv2(packedLight)'
            '.normal(pose, nrm[p], nrm[p + 1], nrm[p + 2]).endVertex();')
    else:
        values['WAVY_CAPE_EMIT_VERTEX']=(
            'consumer.addVertex(pose, xyz[p], xyz[p + 1], xyz[p + 2])'
            '.setColor(rgb, rgb, rgb, 255).setUv(uv[t], uv[t + 1])'
            '.setOverlay(overlay).setLight(packedLight)'
            '.setNormal(pose, nrm[p], nrm[p + 1], nrm[p + 2]);')

    if v<(1,21,2):
        cape_lookup=(
            'if (!player.isCapeLoaded()) return false;\n'
            '        ResourceLocation capeTex = player.getCloakTextureLocation();'
            if v<(1,20,2)
            else 'ResourceLocation capeTex = player.getSkin().capeTexture();')
        values['WAVY_CAPE_BRIDGE_IMPORTS']=(
            'import net.minecraft.client.player.AbstractClientPlayer;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.RenderType;\n'
            'import net.minecraft.resources.ResourceLocation;\n'
            'import net.minecraft.util.Mth;\n'
            'import net.minecraft.world.entity.EquipmentSlot;\n'
            'import net.minecraft.world.entity.player.PlayerModelPart;\n'
            'import net.minecraft.world.item.ItemStack;\n'
            'import net.minecraft.world.item.Items;')
        values['WAVY_CAPE_BRIDGE_ENTRY']=f'''    public static boolean renderLegacy(
            HumanoidModel<?> parentModel, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick) {{
        WavyCapeConfig config = currentConfig();
        if (config == null || player == null) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc != null && player == mc.player;
        if (WavyCapeConfig.PLAYERS_SELF.equals(config.players) && !isSelf) return false;
        if (!isSelf && mc != null && mc.player != null) {{
            double maxD = config.maxDistance;
            if (player.distanceToSqr(mc.player) > maxD * maxD) return false;
        }}
        if (player.isInvisible() || !player.isModelPartShown(PlayerModelPart.CAPE)) return false;
        {cape_lookup}
        if (capeTex == null) return false;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() == Items.ELYTRA) return false;

        boolean guiDoll = packedLight == 15728880 && mc != null && mc.screen != null;
        POSE.entityId = guiDoll ? (player.getId() ^ 0x40000000) : player.getId();
        POSE.worldX = guiDoll ? 0.0 : Mth.lerp((double) partialTick, player.xo, player.getX());
        POSE.worldY = guiDoll ? 0.0 : Mth.lerp((double) partialTick, player.yo, player.getY());
        POSE.worldZ = guiDoll ? 0.0 : Mth.lerp((double) partialTick, player.zo, player.getZ());
        POSE.bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        POSE.crouching = !guiDoll && player.isCrouching();
        POSE.hasChestArmor = !chest.isEmpty();
        POSE.showJacket = player.isModelPartShown(PlayerModelPart.JACKET);
        POSE.inWater = !guiDoll && player.isInWater();
        POSE.swimAmount = guiDoll ? 0.0f : player.getSwimAmount(partialTick);
        POSE.walkPos = guiDoll ? 0.0f : limbSwing;
        POSE.walkSpeed = guiDoll ? 0.0f : limbSwingAmount;
        populateModelParts(parentModel, POSE);
        if (guiDoll) {{
            POSE.rainLevel = 0.0f;
            POSE.thunderLevel = 0.0f;
        }} else {{
            populateWeather(mc, POSE, partialTick);
        }}
        POSE.timeNanos = System.nanoTime();

        WavyCapeMesh mesh = MANAGER.updateAndBuild(POSE, config);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(capeTex));
        emitMesh(poseStack.last(), consumer, mesh, packedLight);
        return true;
    }}'''
        values['WAVY_CAPE_MIXIN_IMPORTS']=(
            'import net.minecraft.client.model.PlayerModel;\n'
            'import net.minecraft.client.player.AbstractClientPlayer;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;')
        values['WAVY_CAPE_RENDER_LAYER_GENERICS']='AbstractClientPlayer, PlayerModel<AbstractClientPlayer>'
        values['WAVY_CAPE_MIXIN_BODY']='''    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At("HEAD"),
        cancellable = true)
    private void nightclient$wavyCape(
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo callback) {
        if (WavyCapeBridge.renderLegacy(
                this.getParentModel(), poseStack, bufferSource, packedLight,
                player, limbSwing, limbSwingAmount, partialTick)) {
            callback.cancel();
        }
    }'''
    elif v<(1,21,9):
        layer_type_pkg='net.minecraft.world.item.equipment.EquipmentModel' if v<(1,21,4) else 'net.minecraft.client.resources.model.EquipmentClientInfo'
        layer_type_short='EquipmentModel.LayerType' if v<(1,21,4) else 'EquipmentClientInfo.LayerType'
        chest_field='chestItem' if v<(1,21,4) else 'chestEquipment'
        values['WAVY_CAPE_BRIDGE_IMPORTS']=(
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.RenderType;\n'
            'import net.minecraft.client.renderer.entity.state.PlayerRenderState;\n'
            'import net.minecraft.resources.ResourceLocation;')
        values['WAVY_CAPE_BRIDGE_ENTRY']='''    public static boolean renderState(
            HumanoidModel<?> parentModel, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, PlayerRenderState state, ResourceLocation capeTex, boolean hasChestArmor) {
        WavyCapeConfig config = currentConfig();
        if (config == null || state == null || capeTex == null) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
        if (WavyCapeConfig.PLAYERS_SELF.equals(config.players) && !isSelf) return false;
        boolean guiDoll = poseStack.last().pose().m11() > 2.0f
                || (packedLight == 15728880 && mc != null && mc.screen != null)
                || (Math.abs(state.x) < 1e-4 && Math.abs(state.y) < 1e-4 && Math.abs(state.z) < 1e-4
                    && mc != null && mc.player != null
                    && (mc.player.getX() * mc.player.getX() + mc.player.getY() * mc.player.getY() + mc.player.getZ() * mc.player.getZ()) > 1.0);
        if (!isSelf && !guiDoll && mc != null && mc.player != null) {
            double dx = state.x - mc.player.getX();
            double dy = state.y - mc.player.getY();
            double dz = state.z - mc.player.getZ();
            double maxD = config.maxDistance;
            if (dx * dx + dy * dy + dz * dz > maxD * maxD) return false;
        }
        POSE.entityId = guiDoll ? (state.id ^ 0x40000000) : state.id;
        POSE.worldX = guiDoll ? 0.0 : state.x;
        POSE.worldY = guiDoll ? 0.0 : state.y;
        POSE.worldZ = guiDoll ? 0.0 : state.z;
        POSE.bodyRot = state.bodyRot;
        POSE.crouching = !guiDoll && state.isCrouching;
        POSE.hasChestArmor = hasChestArmor;
        POSE.showJacket = state.showJacket;
        POSE.inWater = !guiDoll && state.isInWater;
        POSE.swimAmount = guiDoll ? 0.0f : state.swimAmount;
        POSE.walkPos = guiDoll ? 0.0f : state.walkAnimationPos;
        POSE.walkSpeed = guiDoll ? 0.0f : state.walkAnimationSpeed;
        populateModelParts(parentModel, POSE);
        if (guiDoll) {
            POSE.rainLevel = 0.0f;
            POSE.thunderLevel = 0.0f;
        } else {
            populateWeather(mc, POSE, 1.0f);
        }
        POSE.timeNanos = System.nanoTime();

        WavyCapeMesh mesh = MANAGER.updateAndBuild(POSE, config);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(capeTex));
        emitMesh(poseStack.last(), consumer, mesh, packedLight);
        return true;
    }'''
        values['WAVY_CAPE_MIXIN_IMPORTS']=(
            'import net.minecraft.client.model.PlayerModel;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.entity.state.PlayerRenderState;\n'
            'import net.minecraft.resources.ResourceLocation;\n'
            'import net.minecraft.world.item.ItemStack;\n'
            f'import {layer_type_pkg};\n'
            'import org.spongepowered.asm.mixin.Shadow;')
        values['WAVY_CAPE_RENDER_LAYER_GENERICS']='PlayerRenderState, PlayerModel'
        values['WAVY_CAPE_MIXIN_BODY']=f'''    @Shadow
    private boolean hasLayer(ItemStack stack, {layer_type_short} layerType) {{
        return false;
    }}

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true)
    private void nightclient$wavyCape(
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            PlayerRenderState state, float yRot, float xRot, CallbackInfo callback) {{
        if (!WavyCapeBridge.isEnabled()) return;
        if (state.isInvisible || !state.showCape) return;
        ResourceLocation capeTex = state.skin.capeTexture();
        if (capeTex == null) return;
        if (this.hasLayer(state.{chest_field}, {layer_type_short}.WINGS)) return;
        boolean hasChestArmor = this.hasLayer(state.{chest_field}, {layer_type_short}.HUMANOID);
        if (WavyCapeBridge.renderState(
                this.getParentModel(), poseStack, bufferSource, packedLight,
                state, capeTex, hasChestArmor)) {{
            callback.cancel();
        }}
    }}'''
    else:
        cape_id_type='Identifier' if v>=(1,21,11) else 'ResourceLocation'
        cape_rt_import='net.minecraft.client.renderer.rendertype.RenderTypes' if v>=(1,21,11) else 'net.minecraft.client.renderer.RenderType'
        cape_rt_call=(
            'RenderTypes.entityCutoutCull(capeTex)' if v>=(26,1)
            else 'RenderTypes.entityCutout(capeTex)' if v>=(1,21,11)
            else 'RenderType.entityCutout(capeTex)')
        player_model_import='net.minecraft.client.model.player.PlayerModel' if v>=(1,21,11) else 'net.minecraft.client.model.PlayerModel'
        values['WAVY_CAPE_BRIDGE_IMPORTS']=(
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.entity.state.AvatarRenderState;\n'
            f'import {cape_rt_import};\n'
            f'import net.minecraft.resources.{cape_id_type};')
        values['WAVY_CAPE_BRIDGE_ENTRY']=f'''    public static boolean submitState(
            HumanoidModel<?> parentModel, PoseStack poseStack, SubmitNodeCollector collector,
            int packedLight, AvatarRenderState state, {cape_id_type} capeTex, boolean hasChestArmor) {{
        WavyCapeConfig config = currentConfig();
        if (config == null || state == null || capeTex == null) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
        if (WavyCapeConfig.PLAYERS_SELF.equals(config.players) && !isSelf) return false;
        boolean guiDoll = (poseStack.last().pose().m11() > 0.45f && state.swimAmount < 0.1f && state.shadowPieces.isEmpty())
                || (Math.abs(state.x) < 1e-4 && Math.abs(state.y) < 1e-4 && Math.abs(state.z) < 1e-4
                    && mc != null && mc.player != null
                    && (mc.player.getX() * mc.player.getX() + mc.player.getY() * mc.player.getY() + mc.player.getZ() * mc.player.getZ()) > 1.0);
        if (!isSelf && !guiDoll && mc != null && mc.player != null) {{
            double dx = state.x - mc.player.getX();
            double dy = state.y - mc.player.getY();
            double dz = state.z - mc.player.getZ();
            double maxD = config.maxDistance;
            if (dx * dx + dy * dy + dz * dz > maxD * maxD) return false;
        }}
        POSE.entityId = guiDoll ? (state.id ^ 0x40000000) : state.id;
        POSE.worldX = guiDoll ? 0.0 : state.x;
        POSE.worldY = guiDoll ? 0.0 : state.y;
        POSE.worldZ = guiDoll ? 0.0 : state.z;
        POSE.bodyRot = state.bodyRot;
        POSE.crouching = !guiDoll && state.isCrouching;
        POSE.hasChestArmor = hasChestArmor;
        POSE.showJacket = state.showJacket;
        POSE.inWater = !guiDoll && state.isInWater;
        POSE.swimAmount = guiDoll ? 0.0f : state.swimAmount;
        POSE.walkPos = guiDoll ? 0.0f : state.walkAnimationPos;
        POSE.walkSpeed = guiDoll ? 0.0f : state.walkAnimationSpeed;
        populateModelParts(parentModel, POSE);
        if (guiDoll) {{
            POSE.rainLevel = 0.0f;
            POSE.thunderLevel = 0.0f;
        }} else {{
            populateWeather(mc, POSE, 1.0f);
        }}
        POSE.timeNanos = System.nanoTime();

        final WavyCapeMesh baked = MANAGER.updateAndBuild(POSE, config).snapshot();
        final int light = packedLight;
        collector.submitCustomGeometry(poseStack, {cape_rt_call}, (pose, consumer) -> emitMesh(pose, consumer, baked, light));
        return true;
    }}'''
        values['WAVY_CAPE_MIXIN_IMPORTS']=(
            f'import {player_model_import};\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.entity.state.AvatarRenderState;\n'
            'import net.minecraft.client.resources.model.EquipmentClientInfo;\n'
            'import net.minecraft.core.ClientAsset;\n'
            'import net.minecraft.world.item.ItemStack;\n'
            'import org.spongepowered.asm.mixin.Shadow;')
        values['WAVY_CAPE_RENDER_LAYER_GENERICS']='AvatarRenderState, PlayerModel'
        values['WAVY_CAPE_MIXIN_BODY']='''    @Shadow
    private boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType layerType) {
        return false;
    }

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true)
    private void nightclient$wavyCape(
            PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
            AvatarRenderState state, float yRot, float xRot, CallbackInfo callback) {
        if (!WavyCapeBridge.isEnabled()) return;
        if (state.isInvisible || !state.showCape) return;
        ClientAsset.Texture capeAsset = state.skin.cape();
        if (capeAsset == null) return;
        if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) return;
        boolean hasChestArmor = this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID);
        if (WavyCapeBridge.submitState(
                this.getParentModel(), poseStack, collector, packedLight,
                state, capeAsset.texturePath(), hasChestArmor)) {
            callback.cancel();
        }
    }'''

    if v<(1,21,2):
        armor_model_sel=(
            'renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/HumanoidModel;ZFFFLjava/lang/String;)V'
            if v<(1,20,5)
            else 'renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/HumanoidModel;FFFLnet/minecraft/resources/ResourceLocation;)V'
            if v<(1,21)
            else 'renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/HumanoidModel;ILnet/minecraft/resources/ResourceLocation;)V')
        armor_model_buf=(
            'Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V'
            if v<(1,21)
            else 'Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V')
        armor_trim_sel=(
            'renderTrim(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/HumanoidModel;Z)V'
            if v<(1,20,5)
            else 'renderTrim(Lnet/minecraft/core/Holder;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/HumanoidModel;Z)V')
        armor_trim_sheet=(
            'Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet()Lnet/minecraft/client/renderer/RenderType;'
            if v<(1,20,2)
            else 'Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)Lnet/minecraft/client/renderer/RenderType;')
        armor_trim_redirect=(
            'private RenderType nightclient$trimRenderType(){return nightclient$hurt?RenderType.entityCutoutNoCullZOffset(Sheets.ARMOR_TRIMS_SHEET):Sheets.armorTrimsSheet();}'
            if v<(1,20,2)
            else 'private RenderType nightclient$trimRenderType(boolean decal){return nightclient$hurt?RenderType.entityCutoutNoCullZOffset(Sheets.ARMOR_TRIMS_SHEET):Sheets.armorTrimsSheet(decal);}')
        armor_trim_buf=(
            'Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V'
            if v<(1,21)
            else 'Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V')
        values['HIT_COLOR_ELYTRA_RENDER_TARGET']=(
            'Lnet/minecraft/client/model/ElytraModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V'
            if v<(1,21)
            else 'Lnet/minecraft/client/model/ElytraModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V')
        values['HIT_COLOR_ARMOR_TARGET']='HumanoidArmorLayer'
        values['HIT_COLOR_ARMOR_IMPORTS']=(
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.RenderType;\n'
            'import net.minecraft.client.renderer.Sheets;\n'
            'import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;\n'
            'import net.minecraft.resources.ResourceLocation;\n'
            'import net.minecraft.world.entity.LivingEntity;\n'
            'import org.spongepowered.asm.mixin.Unique;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;')
        values['HIT_COLOR_ARMOR_BODY']=f'''    @Unique private boolean nightclient$hurt;

    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"))
    private void nightclient$beginHurt(PoseStack poseStack,MultiBufferSource buffer,int packedLight,LivingEntity entity,float limbSwing,float limbSwingAmount,float partialTick,float ageInTicks,float netHeadYaw,float headPitch,CallbackInfo callback){{
        nightclient$hurt=WorldFeatureBridge.policy().hitColorArmor&&entity!=null&&(entity.hurtTime>0||entity.deathTime>0);
    }}

    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("RETURN"))
    private void nightclient$endHurt(CallbackInfo callback){{
        nightclient$hurt=false;
    }}

    @Redirect(method="{armor_model_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType nightclient$armorRenderType(ResourceLocation texture){{
        return nightclient$hurt?RenderType.entityCutoutNoCullZOffset(texture):RenderType.armorCutoutNoCull(texture);
    }}

    @ModifyArg(method="{armor_model_sel}",at=@At(value="INVOKE",target="{armor_model_buf}"),index=3)
    private int nightclient$armorOverlay(int overlay){{
        return nightclient$hurt?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }}

    @Redirect(method="{armor_trim_sel}",at=@At(value="INVOKE",target="{armor_trim_sheet}"))
    {armor_trim_redirect}

    @ModifyArg(method="{armor_trim_sel}",at=@At(value="INVOKE",target="{armor_trim_buf}"),index=3)
    private int nightclient$trimOverlay(int overlay){{
        return nightclient$hurt?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }}'''
    elif v<(1,21,9):
        eq_sel=(
            'renderLayers(Lnet/minecraft/world/item/equipment/EquipmentModel$LayerType;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V'
            if v<(1,21,4)
            else 'renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V')
        values['HIT_COLOR_ARMOR_TARGET']='EquipmentLayerRenderer'
        values['HIT_COLOR_ARMOR_IMPORTS']=(
            'import net.minecraft.client.renderer.RenderType;\n'
            'import net.minecraft.client.renderer.Sheets;\n'
            'import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;\n'
            'import net.minecraft.resources.ResourceLocation;')
        values['HIT_COLOR_ARMOR_BODY']=f'''    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType nightclient$armorRenderType(ResourceLocation texture){{
        return WorldFeatureBridge.isArmorHitColorActive()?RenderType.entityCutoutNoCullZOffset(texture):RenderType.armorCutoutNoCull(texture);
    }}

    @ModifyArg(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),index=3)
    private int nightclient$armorOverlay(int overlay){{
        return WorldFeatureBridge.isArmorHitColorActive()?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }}

    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType nightclient$trimRenderType(boolean decal){{
        return WorldFeatureBridge.isArmorHitColorActive()?RenderType.entityCutoutNoCullZOffset(Sheets.ARMOR_TRIMS_SHEET):Sheets.armorTrimsSheet(decal);
    }}

    @ModifyArg(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"),index=3)
    private int nightclient$trimOverlay(int overlay){{
        return WorldFeatureBridge.isArmorHitColorActive()?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }}'''
    elif v<(26,3):
        eq_id='Identifier' if v>=(1,21,11) else 'ResourceLocation'
        eq_rt_pkg='net.minecraft.client.renderer.rendertype' if v>=(1,21,11) else 'net.minecraft.client.renderer'
        eq_rt_path=eq_rt_pkg.replace('.','/')
        eq_rt_factory='RenderTypes' if v>=(1,21,11) else 'RenderType'
        eq_zoff='entityCutoutZOffset' if v>=(26,1) else 'entityCutoutNoCullZOffset'
        eq_sel=f'renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/{eq_id};II)V'
        eq_submit=f'Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;L{eq_rt_path}/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V'
        eq_rt_extra_import=f'import {eq_rt_pkg}.RenderTypes;\n' if eq_rt_factory=='RenderTypes' else ''
        values['HIT_COLOR_ARMOR_TARGET']='EquipmentLayerRenderer'
        values['HIT_COLOR_ARMOR_IMPORTS']=(
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.model.Model;\n'
            'import net.minecraft.client.renderer.Sheets;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;\n'
            'import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;\n'
            f'import {eq_rt_pkg}.RenderType;\n'
            f'{eq_rt_extra_import}'
            'import net.minecraft.client.resources.model.EquipmentClientInfo;\n'
            f'import net.minecraft.resources.{eq_id};\n'
            'import net.minecraft.resources.ResourceKey;\n'
            'import net.minecraft.world.item.ItemStack;\n'
            'import org.spongepowered.asm.mixin.Unique;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;')
        values['HIT_COLOR_ARMOR_BODY']=f'''    @Unique private boolean nightclient$hurt;

    @Inject(method="{eq_sel}",at=@At("HEAD"))
    private void nightclient$beginHurt(EquipmentClientInfo.LayerType layerType,ResourceKey<?> assetId,Model<?> model,Object state,ItemStack stack,PoseStack poseStack,SubmitNodeCollector collector,int packedLight,{eq_id} playerTexture,int outlineColor,int baseOrder,CallbackInfo callback){{
        nightclient$hurt=WorldFeatureBridge.policy().hitColorArmor&&state instanceof LivingEntityRenderState&&((LivingEntityRenderState)state).hasRedOverlay;
    }}

    @Inject(method="{eq_sel}",at=@At("RETURN"))
    private void nightclient$endHurt(CallbackInfo callback){{
        nightclient$hurt=false;
    }}

    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="L{eq_rt_path}/{eq_rt_factory};armorCutoutNoCull(Lnet/minecraft/resources/{eq_id};)L{eq_rt_path}/RenderType;"))
    private RenderType nightclient$armorRenderType({eq_id} texture){{
        return nightclient$hurt?{eq_rt_factory}.{eq_zoff}(texture):{eq_rt_factory}.armorCutoutNoCull(texture);
    }}

    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)L{eq_rt_path}/RenderType;"))
    private RenderType nightclient$trimRenderType(boolean decal){{
        return nightclient$hurt?{eq_rt_factory}.{eq_zoff}(Sheets.ARMOR_TRIMS_SHEET):Sheets.armorTrimsSheet(decal);
    }}

    @ModifyArg(method="{eq_sel}",at=@At(value="INVOKE",target="{eq_submit}"),index=5)
    private int nightclient$armorOverlay(int overlay){{
        return nightclient$hurt?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }}'''
    else:
        eq_sel='renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V'
        eq_submit='Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V'
        values['HIT_COLOR_ARMOR_TARGET']='EquipmentLayerRenderer'
        values['HIT_COLOR_ARMOR_IMPORTS']=(
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.model.Model;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;\n'
            'import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;\n'
            'import net.minecraft.client.renderer.rendertype.RenderType;\n'
            'import net.minecraft.client.renderer.rendertype.RenderTypes;\n'
            'import net.minecraft.client.resources.model.EquipmentClientInfo;\n'
            'import net.minecraft.resources.Identifier;\n'
            'import net.minecraft.resources.ResourceKey;\n'
            'import net.minecraft.world.item.ItemStack;\n'
            'import org.spongepowered.asm.mixin.Unique;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;')
        values['HIT_COLOR_ARMOR_BODY']=f'''    @Unique private boolean nightclient$hurt;
    @Unique private boolean nightclient$extraGlint;
    @Unique private int nightclient$layerCount;

    @Inject(method="{eq_sel}",at=@At("HEAD"))
    private void nightclient$beginHurt(EquipmentClientInfo.LayerType layerType,ResourceKey<?> assetId,Model<?> model,Object state,ItemStack stack,PoseStack poseStack,SubmitNodeCollector collector,int packedLight,Identifier playerTexture,int outlineColor,int baseOrder,CallbackInfo callback){{
        nightclient$hurt=WorldFeatureBridge.policy().hitColorArmor&&state instanceof LivingEntityRenderState&&((LivingEntityRenderState)state).hasRedOverlay;
        nightclient$extraGlint=false;
        nightclient$layerCount=0;
    }}

    @Inject(method="{eq_sel}",at=@At("RETURN"))
    private <S> void nightclient$endHurt(EquipmentClientInfo.LayerType layerType,ResourceKey<?> assetId,Model<? super S> model,S state,ItemStack stack,PoseStack poseStack,SubmitNodeCollector collector,int packedLight,Identifier playerTexture,int outlineColor,int baseOrder,CallbackInfo callback){{
        if(nightclient$extraGlint){{
            collector.order(baseOrder+nightclient$layerCount).submitModel(model,state,poseStack,RenderTypes.trimmedArmorGlint(),packedLight,OverlayTexture.NO_OVERLAY,-1,null,0);
        }}
        nightclient$hurt=false;
        nightclient$extraGlint=false;
        nightclient$layerCount=0;
    }}

    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType nightclient$armorRenderType(Identifier texture){{
        nightclient$layerCount++;
        return nightclient$hurt?RenderTypes.entityCutoutZOffset(texture):RenderTypes.armorCutoutNoCull(texture);
    }}

    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType nightclient$armorGlintRenderType(Identifier texture){{
        nightclient$layerCount++;
        if(nightclient$hurt){{
            nightclient$extraGlint=true;
            return RenderTypes.entityCutoutZOffset(texture);
        }}
        return RenderTypes.armorCutoutNoCullGlint(texture);
    }}

    @Redirect(method="{eq_sel}",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType nightclient$trimRenderType(Identifier texture,boolean decal){{
        return nightclient$hurt?RenderTypes.entityCutoutZOffset(texture):RenderTypes.armorTrim(texture,decal);
    }}

    @ModifyArg(method="{eq_sel}",at=@At(value="INVOKE",target="{eq_submit}"),index=5)
    private int nightclient$armorOverlay(int overlay){{
        return nightclient$hurt?OverlayTexture.pack(OverlayTexture.u(0.0F),OverlayTexture.v(true)):overlay;
    }}'''

    files=['WorldFeatureBridge','WorldMapBridge','CosmeticsBridge','WavyCapeBridge','LiteApiBridge','ItemPhysicsPose','ItemPhysicsState']+world_feature_mixins(version)
    if v>=(1,20,2):files.append('LiteApiPayload')
    if not gizmos:files.append('HitboxRenderTypes')
    else:files.append('HitboxRibbonGizmo')
    if gpu and not gizmos:files.append('HitboxGpuLineType')
    package=Path(output)/'dev/nightclient/platform'
    for name in files:
        source=(TEMPLATES/(name+'.java.tpl')).read_text()
        for key,value in values.items():source=source.replace('@@'+key+'@@',value)
        if name=='ItemPhysicsMixin':source=source.replace('target.translate(x,physics.enabled&&physics.grounded?.0625:y,z);',values['TRANSLATE_CALL'])
        if re.search(r'@@[A-Z_]+@@',source):raise ValueError('Unresolved world-feature template: '+version+': '+name)
        target=package/('mixin/'+name+'.java' if name in world_feature_mixins(version) else name+'.java')
        target.parent.mkdir(parents=True,exist_ok=True)
        if not target.exists()or target.read_text()!=source:target.write_text(source)
    active_mixins=set(world_feature_mixins(version))
    for stale in ('TrapBlockUpdateMixin','HitColorElytraMixin','HitColorLivingMixin','LiteApiClientCommonMixin','LiteApiCustomPayloadMixin','LiteApiPayloadCodecMixin'):
        if stale not in active_mixins:
            (package/('mixin/'+stale+'.java')).unlink(missing_ok=True)
    if v<(1,20,2):
        (package/'LiteApiPayload.java').unlink(missing_ok=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--version',required=True);parser.add_argument('--output',required=True,type=Path);args=parser.parse_args();generate_world_features(args.version,args.output)

if __name__=='__main__':main()
