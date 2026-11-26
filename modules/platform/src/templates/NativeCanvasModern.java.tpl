package dev.nightclient.platform;

import dev.nightclient.platform.mixin.GuiStateAccessor;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.renderer.TexturedMesh;
import java.util.Arrays;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.@@IDENTIFIER@@;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import @@TEXTURE_PACKAGE@@.GpuTextureView;
import @@TEXTURE_PACKAGE@@.FilterMode;
@@CANVAS_IMPORTS@@

public final class NativeCanvas extends GeometryCanvas {
    private final @@CONTEXT@@ graphics;
    private FontAtlas glyphAtlas=FontAtlas.get();
    private final float density;
    public NativeCanvas(@@CONTEXT@@ graphics,int width,int height){super(width,height);this.graphics=graphics;density=(float)Math.max(1,Minecraft.getInstance().getWindow().getGuiScale());}
    public static void prewarm(){
        preloadFont(FontAtlas.get());preloadFont(FontAtlas.get("studio-sans"));preloadFont(FontAtlas.get("studio-sans-medium"));
        try{GuiTextureMeshState.prewarm();}catch(Throwable ignored){}
        try{GuiMeshState.prewarm();}catch(Throwable ignored){}
        try{BackdropBackend.prewarm();}catch(Throwable ignored){}
        try{ViewmodelOutlineBackend.prewarm();}catch(Throwable ignored){}
        try{dev.nightclient.renderer.Icons.prewarmAsync((float)Math.max(1,Minecraft.getInstance().getWindow().getGuiScale()));}catch(Throwable ignored){}
    }
    private static void preloadFont(FontAtlas atlas){
        try{Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(atlas.textureResourceId()));}catch(Throwable ignored){}
    }
    private void submit(GuiMeshState state){if(state.visible())((GuiStateAccessor)(Object)graphics).nightclient$state().@@SUBMIT_GUI@@(state);}
    private void submit(GuiTextureMeshState state){if(state.visible())((GuiStateAccessor)(Object)graphics).nightclient$state().@@SUBMIT_GUI@@(state);}
    @Override protected boolean usesGpuRounded(){return true;}
    @Override protected void drawRounded(float[] v,int length,float[] clip){submit(new GuiTextureMeshState(Arrays.copyOf(v,length),graphics.pose(),clip,TextureSetup.noTexture(),width,height,false,false,true));}
    @Override protected void draw(float[] v,int length,float[] clip){
        submit(new GuiMeshState(Arrays.copyOf(v,length),graphics.pose(),clip,width,height));
    }
    @Override protected void drawInvertedRect(float x,float y,float w,float h,float opacity,float[] clip){
        float a=Math.round(opacity*255)/255f;
        float[] vertices={x,y,a,a,a,a, x+w,y,a,a,a,a, x+w,y+h,a,a,a,a,
            x,y,a,a,a,a, x+w,y+h,a,a,a,a, x,y+h,a,a,a,a};
        submit(new GuiMeshState(vertices,graphics.pose(),clip,width,height,true));
    }
    @Override protected void drawBackdrop(float x,float y,float w,float h,float radius,float blurRadius,int quality,float opacity,float[] clip){
        GpuTextureView view=BackdropBackend.texture(blurRadius,quality);if(view==null)return;
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,radius,Math.round(opacity*255)<<24|0xffffff,0,0,1,1);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip,@@BACKDROP_TEXTURE_SETUP@@,width,height,true,BackdropBackend.topDown()));
    }
    @Override protected void drawBackdropCorners(float x,float y,float w,float h,float tl,float tr,float bl,float br,float blurRadius,int quality,float opacity,float[] clip){
        GpuTextureView view=BackdropBackend.texture(blurRadius,quality);if(view==null)return;
        float[] vertices=TexturedMesh.roundedRectCorners(x,y,w,h,tl,tr,bl,br,Math.round(opacity*255)<<24|0xffffff,0,0,1,1);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip,@@BACKDROP_TEXTURE_SETUP@@,width,height,true,BackdropBackend.topDown()));
    }
    @Override protected void drawTexture(String id,float x,float y,float w,float h,float radius,float tileSize,int argb,float[] clip){
        drawTextureCorners(id,x,y,w,h,radius,radius,radius,radius,tileSize,argb,clip);
    }
    @Override protected void drawTextureCorners(String id,float x,float y,float w,float h,float tl,float tr,float bl,float br,float tileSize,int argb,float[] clip){
        AbstractTexture texture=Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(id));
        @@MATERIAL_SAMPLER_SETUP@@
        float[] vertices=TexturedMesh.roundedRectCorners(x,y,w,h,tl,tr,bl,br,argb,0,0,tileSize>0?w/tileSize:1,tileSize>0?h/tileSize:1);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip,@@MATERIAL_TEXTURE_SETUP@@,width,height,false,false));
    }
    @Override public void gameSprite(String id,float x,float y,float w,float h,float u,float v,float rw,float rh,float sw,float sh,int argb){
        if(id==null||w<=0||h<=0||rw<=0||rh<=0||sw<=0||sh<=0||(argb>>>24)==0||!clipVisible())return;
        String resource;
        if("minecraft:hud/hotbar".equals(id)){resource="@@HOTBAR_RESOURCE@@";sw=@@HOTBAR_ATLAS_WIDTH@@;sh=@@HOTBAR_ATLAS_HEIGHT@@;}
        else if("minecraft:hud/effect_background".equals(id)){resource="@@EFFECT_BG_RESOURCE@@";u+=@@EFFECT_BG_U@@;v+=@@EFFECT_BG_V@@;sw=@@EFFECT_BG_ATLAS_WIDTH@@;sh=@@EFFECT_BG_ATLAS_HEIGHT@@;}
        else{int colon=id.indexOf(':');resource=(colon<0?"minecraft":id.substring(0,colon))+":textures/gui/sprites/"+(colon<0?id:id.substring(colon+1))+".png";}
        flush();AbstractTexture texture=Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(resource));
        @@SPRITE_SAMPLER_SETUP@@
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,0,argb,u/sw,v/sh,(u+rw)/sw,(v+rh)/sh);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@SPRITE_TEXTURE_SETUP@@,width,height,false,false));
    }
    @Override public boolean entityHead(dev.nightclient.api.TargetView target,float x,float y,float size,float alpha){
        if(target==null||!target.player||target.headTexture.isEmpty()||size<=0||alpha<=0||!clipVisible())return false;
        final AbstractTexture texture;
        try{texture=Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(target.headTexture));}catch(Throwable ignored){return false;}
        if(texture==null)return false;
        flush();
        @@HEAD_SAMPLER_SETUP@@
        int tint=Math.round(Math.max(0,Math.min(1,alpha))*255)<<24|0xffffff;
        for(float u:new float[]{.125f,.625f}){
            float[] vertices=TexturedMesh.roundedRect(x,y,size,size,0,tint,u,.125f,u+.125f,.25f);
            submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@HEAD_TEXTURE_SETUP@@,width,height,false,false));
        }
        return true;
    }
    @Override public boolean skinHead(String skinTextureId,float x,float y,float size,float radius,float alpha,int tintRgb){
        if(skinTextureId==null||skinTextureId.isEmpty()||size<=0||alpha<=0||!clipVisible())return false;
        final AbstractTexture texture;
        try{texture=Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(skinTextureId));}catch(Throwable ignored){return false;}
        if(texture==null)return false;
        flush();
        @@HEAD_SAMPLER_SETUP@@
        int tint=(Math.round(Math.max(0,Math.min(1,alpha))*255)<<24)|(tintRgb&0xffffff);
        for(float u:new float[]{.125f,.625f}){
            float[] vertices=TexturedMesh.roundedRect(x,y,size,size,radius,tint,u,.125f,u+.125f,.25f);
            submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@HEAD_TEXTURE_SETUP@@,width,height,false,false));
        }
        return true;
    }
    @Override public boolean textureRegion(String textureId,float x,float y,float w,float h,float radius,float u0,float v0,float u1,float v1,float alpha,int tintRgb){
        if(textureId==null||textureId.isEmpty()||w<=0||h<=0||u1<=u0||v1<=v0||alpha<=0||!clipVisible())return false;
        final AbstractTexture texture;
        try{texture=Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(textureId));}catch(Throwable ignored){return false;}
        if(texture==null)return false;
        flush();
        @@HEAD_SAMPLER_SETUP@@
        int tint=(Math.round(Math.max(0,Math.min(1,alpha))*255)<<24)|(tintRgb&0xffffff);
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,radius,tint,u0,v0,u1,v1);
        if(vertices.length==0)return false;
        submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@HEAD_TEXTURE_SETUP@@,width,height,false,false));
        return true;
    }
    @Override public void mapQuad(String cacheKey,long revision,int[] pixels,int texWidth,int texHeight,float x,float y,float w,float h,float radius,float uCenter,float vCenter,float uHalfSpan,float vHalfSpan,float angleRad,boolean nearest,int argb){
        if(pixels==null||texWidth<=0||texHeight<=0||w<=0||h<=0||(argb>>>24)==0||!clipVisible())return;
        DynamicTexture texture=MapTextureBackend.getOrUpload(cacheKey,revision,pixels,texWidth,texHeight);
        if(texture==null)return;
        flush();
        float[] vertices=TexturedMesh.roundedRectRotatedUv(x,y,w,h,radius,argb,uCenter,vCenter,uHalfSpan,vHalfSpan,angleRad);
        if(vertices.length==0)return;
        if(nearest){
            @@MAP_NEAREST_SAMPLER_SETUP@@
            submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@MAP_NEAREST_TEXTURE_SETUP@@,this.width,this.height,false,false));
        }else{
            @@PREVIEW_SAMPLER_SETUP@@
            submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@PREVIEW_TEXTURE_SETUP@@,this.width,this.height,false,false));
        }
    }
    @Override public boolean supportsIconAtlas(){return true;}
    @Override public float iconPixelDensity(){return density;}
    @Override public void iconQuad(String cacheKey,long revision,int[] pixels,int texWidth,int texHeight,float x,float y,float w,float h,int argb){
        if(pixels==null||texWidth<=0||texHeight<=0||w<=0||h<=0||(argb>>>24)==0||!clipVisible())return;
        MapTextureBackend.IconSlot slot=MapTextureBackend.getOrPackIcon(cacheKey,revision,pixels,texWidth,texHeight);
        if(slot==null)return;
        DynamicTexture texture=slot.texture;
        flush();
        @@PREVIEW_SAMPLER_SETUP@@
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,0,argb,slot.u0,slot.v0,slot.u1,slot.v1);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@PREVIEW_TEXTURE_SETUP@@,this.width,this.height,false,false));
    }
    @Override protected void finished(){MapTextureBackend.flushIconAtlas();}
    @Override public void playerPreview(dev.nightclient.api.PlayerSkin skin,float x,float y,float w,float h,float yaw,float pitch,float zoom,float time,float wind,float yawVelocity,float alpha){
        if(skin==null||w<=0||h<=0||alpha<=0||!clipVisible())return;
        DynamicTexture texture=PlayerPreviewBackend.render(skin,yaw,pitch,zoom,time,wind,yawVelocity,w,h,density);
        if(texture==null)return;
        flush();
        @@PREVIEW_SAMPLER_SETUP@@
        int tint=Math.round(Math.max(0,Math.min(1,alpha))*255)<<24|0xffffff;
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,0,tint,0,0,1,1);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@PREVIEW_TEXTURE_SETUP@@,this.width,this.height,false,false));
    }
    @Override public void capePreview(String cacheKey,dev.nightclient.api.PlayerSkin skin,float x,float y,float w,float h,float alpha){
        if(skin==null||(skin.capePixels()==null&&!dev.nightclient.renderer.player.PlayerPreviewRenderer.hasModelCard(skin))||w<=0||h<=0||alpha<=0||!clipVisible())return;
        DynamicTexture texture=PlayerPreviewBackend.renderCapeCard(cacheKey,skin,w,h,density);
        if(texture==null)return;
        flush();
        @@PREVIEW_SAMPLER_SETUP@@
        int tint=Math.round(Math.max(0,Math.min(1,alpha))*255)<<24|0xffffff;
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,0,tint,0,0,1,1);
        if(vertices.length>0)submit(new GuiTextureMeshState(vertices,graphics.pose(),clip(),@@PREVIEW_TEXTURE_SETUP@@,this.width,this.height,false,false));
    }

    private void pushNativeClip(){float[] c=clip();if(c!=null)graphics.enableScissor((int)Math.floor(c[0]),(int)Math.floor(c[1]),(int)Math.ceil(c[2]),(int)Math.ceil(c[3]));}
    private void popNativeClip(){if(clip()!=null)graphics.disableScissor();}
    @Override public void hungerIcon(float x,float y,float size,int fill){if(size<=0||!clipVisible())return;flush();pushNativeClip();try{VanillaHudBridge.hungerIcon(graphics,x,y,size,fill);}finally{popNativeClip();}}
    @Override public boolean vanillaCrosshair(float x,float y,float size,float opacity){if(size<=0||opacity<=0||!clipVisible()||!GuiMeshState.visible(GuiMeshState.scissor(graphics.pose(),clip(),width,height)))return true;flush();pushNativeClip();try{CrosshairBridge.draw(graphics,x,y,size,opacity);return true;}finally{popNativeClip();}}
    @Override public void viewmodelOutline(String hand,int argb){flush();GpuTextureView view=ViewmodelOutlineBackend.texture(hand);if(view!=null)submit(new GuiTextureMeshState(TexturedMesh.roundedRect(0,0,width,height,0,argb,0,0,1,1),graphics.pose(),clip(),@@BACKDROP_TEXTURE_SETUP@@,width,height,true,false));}
    @Override protected boolean hasNativeTextFallback(){return true;}
    @Override public float gameTextWidth(String value,float scale){return value==null?0:Minecraft.getInstance().font.width(value)*scale;}
    @Override public void gameText(String value,float x,float y,float scale,int argb,boolean shadow){
        if(value==null||value.isEmpty()||scale<=0||!clipVisible()||!GuiMeshState.visible(GuiMeshState.scissor(graphics.pose(),clip(),width,height)))return;
        flush();pushNativeClip();graphics.pose().pushMatrix();
        try{graphics.pose().translate(x,y);graphics.pose().scale(scale,scale);@@GAME_TEXT@@}
        finally{graphics.pose().popMatrix();popNativeClip();}
    }
    @Override protected float rasterDensity(){return density;}
    @Override protected void beginText(FontAtlas atlas){if(glyphCount()>0 && glyphAtlas!=atlas)flushText();if(glyphCount()==0){glyphAtlas=atlas;beginGlyphs();}}
    @Override protected void endText(){}
    @Override protected void flushText(){
        if(glyphCount()==0)return;
        AbstractTexture texture=Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(glyphAtlas.textureResourceId()));
        @@FONT_SAMPLER_SETUP@@
        submit(new GuiTextureMeshState(Arrays.copyOf(glyphData(),glyphCount()),graphics.pose(),clip(),@@FONT_TEXTURE_SETUP@@,width,height,false,false));
        beginGlyphs();
    }
    @Override protected void glyph(float x,float y,float w,float h,int px,int py,int pw,int ph,int color){
        if(glyphCount()+48>65520)flushText();
        float u=px/(float)glyphAtlas.atlasWidth(),v=py/(float)glyphAtlas.atlasHeight();
        float u1=(px+pw)/(float)glyphAtlas.atlasWidth(),v1=(py+ph)/(float)glyphAtlas.atlasHeight();
        glyphQuad(x,y,w,h,u,v,u1,v1,color);
    }
    @Override protected void drawItem(String id,float x,float y,float size,float alpha){
        drawStack(GameBridge.stack(id),x,y,size,alpha);
    }
    @Override public void inventoryItem(dev.nightclient.api.InventorySlot slot,float x,float y,float size,float alpha){
        if(slot==null || size<=0 || alpha<=0 || !clipVisible())return;
        flush();drawStack(GameBridge.cachedInventoryStack(slot.slot),x,y,size,alpha);
    }
    private void drawStack(ItemStack stack,float x,float y,float size,float alpha){
        if(stack.isEmpty() || !clipVisible() || !GuiMeshState.visible(GuiMeshState.scissor(graphics.pose(),clip(),width,height)))return;
        Float previousOpacity=ItemOpacity.push(alpha);
        try{
            pushNativeClip();
            try{
                graphics.pose().pushMatrix();
                try{graphics.pose().translate(x,y);graphics.pose().scale(size/16,size/16);graphics.@@ITEM_METHOD@@(stack,0,0);}
                finally{graphics.pose().popMatrix();}
            }finally{popNativeClip();}
        }finally{ItemOpacity.pop(previousOpacity);}
    }
}
