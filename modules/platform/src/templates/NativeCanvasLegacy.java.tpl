package dev.nightclient.platform;

import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.renderer.TexturedMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
@@CANVAS_IMPORTS@@

public final class NativeCanvas extends GeometryCanvas {
    private final @@CONTEXT@@ graphics;
    private FontAtlas glyphAtlas=FontAtlas.get();
    private final float density;
    public NativeCanvas(@@CONTEXT@@ graphics,int width,int height){super(width,height);this.graphics=graphics;density=(float)Math.max(1,Minecraft.getInstance().getWindow().getGuiScale());BackdropBackend.beforeCanvas();invalidateNativeBuffers();syncNativeTextures();syncNativeBlend();@@LEGACY_BEGIN@@}
    public static void prewarm(){
        preloadFont(FontAtlas.get());preloadFont(FontAtlas.get("studio-sans"));preloadFont(FontAtlas.get("studio-sans-medium"));
        try{LegacyUiRenderer.prewarm();}catch(Throwable ignored){}
        try{BackdropBackend.prewarm();}catch(Throwable ignored){}
        try{ViewmodelOutlineBackend.prewarm();}catch(Throwable ignored){}
        try{dev.nightclient.renderer.Icons.prewarmAsync((float)Math.max(1,Minecraft.getInstance().getWindow().getGuiScale()));}catch(Throwable ignored){}
    }
    private static void preloadFont(FontAtlas atlas){
        try{Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(atlas.textureResourceId()));}catch(Throwable ignored){}
    }

    private static void invalidateNativeBuffers(){@@INVALIDATE_NATIVE_BUFFERS@@}

    private static void syncNativeTextures(){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean extra=active!=GL13.GL_TEXTURE0 && active!=GL13.GL_TEXTURE1;
        int activeBinding=extra?GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D):0;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);int texture0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);int texture1=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GlStateManager._activeTexture(GL13.GL_TEXTURE1);GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(0);GlStateManager._bindTexture(texture0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(0);GlStateManager._bindTexture(texture1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);GlStateManager._activeTexture(active);

        if(extra && active>=GL13.GL_TEXTURE0 && active<GL13.GL_TEXTURE0+@@NATIVE_TEXTURE_COUNT@@){GlStateManager._bindTexture(0);GlStateManager._bindTexture(activeBinding);}
    }

    private static void syncNativeBlend(){@@SYNC_NATIVE_BLEND@@}
    private void suspendNative(){LegacyUiRenderer.suspend(clip());invalidateNativeBuffers();syncNativeTextures();syncNativeBlend();}
    private static void resumeNative(){invalidateNativeBuffers();syncNativeTextures();syncNativeBlend();LegacyUiRenderer.resume();}
    @@NO_WORLD_MENU_CLEAR_METHOD@@
    @Override protected boolean usesGpuRounded(){return true;}
    @Override protected void drawRounded(float[] v,int length,float[] clip){LegacyUiRenderer.drawRounded(v,length,clip);}
    @Override protected void draw(float[] v,int length,float[] clip){LegacyUiRenderer.draw(v,length,clip);}
    @Override protected void drawInvertedRect(float x,float y,float w,float h,float opacity,float[] clip){LegacyUiRenderer.drawInvertedRect(x,y,w,h,opacity,clip);}
    @Override protected void drawBackdrop(float x,float y,float w,float h,float radius,float blurRadius,int quality,float opacity,float[] clip){
        int texture=BackdropBackend.texture(blurRadius,quality);if(texture==0)return;
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,radius,Math.round(opacity*255)<<24|0xffffff,x/width,1-y/height,(x+w)/width,1-(y+h)/height);
        if(vertices.length>0)LegacyUiRenderer.drawTexture(vertices,texture,clip);
    }
    @Override protected void drawBackdropCorners(float x,float y,float w,float h,float tl,float tr,float bl,float br,float blurRadius,int quality,float opacity,float[] clip){
        int texture=BackdropBackend.texture(blurRadius,quality);if(texture==0)return;
        float[] vertices=TexturedMesh.roundedRectCorners(x,y,w,h,tl,tr,bl,br,Math.round(opacity*255)<<24|0xffffff,x/width,1-y/height,(x+w)/width,1-(y+h)/height);
        if(vertices.length>0)LegacyUiRenderer.drawTexture(vertices,texture,clip);
    }
    @Override protected void drawTexture(String id,float x,float y,float w,float h,float radius,float tileSize,int argb,float[] clip){
        drawTextureCorners(id,x,y,w,h,radius,radius,radius,radius,tileSize,argb,clip);
    }
    @Override protected void drawTextureCorners(String id,float x,float y,float w,float h,float tl,float tr,float bl,float br,float tileSize,int argb,float[] clip){
        float[] vertices=TexturedMesh.roundedRectCorners(x,y,w,h,tl,tr,bl,br,argb,0,0,tileSize>0?w/tileSize:1,tileSize>0?h/tileSize:1);
        if(vertices.length>0)LegacyUiRenderer.drawTexture(vertices,nativeTexture(id),clip);
    }
    private static int nativeTexture(String id){@@NATIVE_TEXTURE_LOAD@@return @@NATIVE_TEXTURE_ID@@;}
    @Override public void gameSprite(String id,float x,float y,float w,float h,float u,float v,float rw,float rh,float sw,float sh,int argb){
        if(id==null||w<=0||h<=0||rw<=0||rh<=0||sw<=0||sh<=0||(argb>>>24)==0||!clipVisible())return;
        String resource;
        if("minecraft:hud/hotbar".equals(id)){resource="@@HOTBAR_RESOURCE@@";sw=@@HOTBAR_ATLAS_WIDTH@@;sh=@@HOTBAR_ATLAS_HEIGHT@@;}
        else if("minecraft:hud/effect_background".equals(id)){resource="@@EFFECT_BG_RESOURCE@@";u+=@@EFFECT_BG_U@@;v+=@@EFFECT_BG_V@@;sw=@@EFFECT_BG_ATLAS_WIDTH@@;sh=@@EFFECT_BG_ATLAS_HEIGHT@@;}
        else{int colon=id.indexOf(':');resource=(colon<0?"minecraft":id.substring(0,colon))+":textures/gui/sprites/"+(colon<0?id:id.substring(colon+1))+".png";}
        flush();int texture=nativeTexture(resource);
        if("minecraft:hud/effect_background".equals(id))LegacyUiRenderer.drawTextureNearest(TexturedMesh.roundedRect(x,y,w,h,0,argb,u/sw,v/sh,(u+rw)/sw,(v+rh)/sh),texture,clip());
        else LegacyUiRenderer.drawTexture(TexturedMesh.roundedRect(x,y,w,h,0,argb,u/sw,v/sh,(u+rw)/sw,(v+rh)/sh),texture,clip());
    }
    @Override public boolean entityHead(dev.nightclient.api.TargetView target,float x,float y,float size,float alpha){
        if(target==null||!target.player||target.headTexture.isEmpty()||size<=0||alpha<=0||!clipVisible())return false;
        final int texture;
        try{texture=nativeTexture(target.headTexture);}catch(Throwable ignored){return false;}
        if(texture==0)return false;
        flush();
        int tint=Math.round(Math.max(0,Math.min(1,alpha))*255)<<24|0xffffff;
        for(float u:new float[]{.125f,.625f})LegacyUiRenderer.drawTextureNearest(TexturedMesh.roundedRect(x,y,size,size,0,tint,u,.125f,u+.125f,.25f),texture,clip());
        return true;
    }
    @Override public boolean skinHead(String skinTextureId,float x,float y,float size,float radius,float alpha,int tintRgb){
        if(skinTextureId==null||skinTextureId.isEmpty()||size<=0||alpha<=0||!clipVisible())return false;
        final int texture;
        try{texture=nativeTexture(skinTextureId);}catch(Throwable ignored){return false;}
        if(texture==0)return false;
        flush();
        int tint=(Math.round(Math.max(0,Math.min(1,alpha))*255)<<24)|(tintRgb&0xffffff);
        for(float u:new float[]{.125f,.625f})LegacyUiRenderer.drawTextureNearest(TexturedMesh.roundedRect(x,y,size,size,radius,tint,u,.125f,u+.125f,.25f),texture,clip());
        return true;
    }
    @Override public boolean textureRegion(String textureId,float x,float y,float w,float h,float radius,float u0,float v0,float u1,float v1,float alpha,int tintRgb){
        if(textureId==null||textureId.isEmpty()||w<=0||h<=0||u1<=u0||v1<=v0||alpha<=0||!clipVisible())return false;
        final int texture;
        try{texture=nativeTexture(textureId);}catch(Throwable ignored){return false;}
        if(texture==0)return false;
        flush();
        int tint=(Math.round(Math.max(0,Math.min(1,alpha))*255)<<24)|(tintRgb&0xffffff);
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,radius,tint,u0,v0,u1,v1);
        if(vertices.length==0)return false;
        LegacyUiRenderer.drawTextureNearest(vertices,texture,clip());
        return true;
    }
    @Override public void mapQuad(String cacheKey,long revision,int[] pixels,int texWidth,int texHeight,float x,float y,float w,float h,float radius,float uCenter,float vCenter,float uHalfSpan,float vHalfSpan,float angleRad,boolean nearest,int argb){
        if(pixels==null||texWidth<=0||texHeight<=0||w<=0||h<=0||(argb>>>24)==0||!clipVisible())return;
        int texture=MapTextureBackend.getOrUpload(cacheKey,revision,pixels,texWidth,texHeight);
        if(texture==0)return;
        flush();
        float[] vertices=TexturedMesh.roundedRectRotatedUv(x,y,w,h,radius,argb,uCenter,vCenter,uHalfSpan,vHalfSpan,angleRad);
        if(vertices.length==0)return;
        if(nearest)LegacyUiRenderer.drawTextureNearest(vertices,texture,clip());
        else LegacyUiRenderer.drawTexture(vertices,texture,clip());
    }
    @Override public boolean supportsIconAtlas(){return true;}
    @Override public float iconPixelDensity(){return density;}
    @Override public void iconQuad(String cacheKey,long revision,int[] pixels,int texWidth,int texHeight,float x,float y,float w,float h,int argb){
        if(pixels==null||texWidth<=0||texHeight<=0||w<=0||h<=0||(argb>>>24)==0||!clipVisible())return;
        MapTextureBackend.IconSlot slot=MapTextureBackend.getOrPackIcon(cacheKey,revision,pixels,texWidth,texHeight);
        if(slot==null||slot.texId==0)return;
        flush();
        float[] vertices=TexturedMesh.roundedRect(x,y,w,h,0,argb,slot.u0,slot.v0,slot.u1,slot.v1);
        if(vertices.length>0)LegacyUiRenderer.drawTexture(vertices,slot.texId,clip());
    }
    @Override public void playerPreview(dev.nightclient.api.PlayerSkin skin,float x,float y,float w,float h,float yaw,float pitch,float zoom,float time,float wind,float yawVelocity,float alpha){
        if(skin==null||w<=0||h<=0||alpha<=0||!clipVisible())return;
        int texture=PlayerPreviewBackend.render(skin,yaw,pitch,zoom,time,wind,yawVelocity,w,h,density);
        if(texture==0)return;
        flush();
        int tint=Math.round(Math.max(0,Math.min(1,alpha))*255)<<24|0xffffff;
        LegacyUiRenderer.drawTexture(TexturedMesh.roundedRect(x,y,w,h,0,tint,0,0,1,1),texture,clip());
    }
    @Override public void capePreview(String cacheKey,dev.nightclient.api.PlayerSkin skin,float x,float y,float w,float h,float alpha){
        if(skin==null||(skin.capePixels()==null&&!dev.nightclient.renderer.player.PlayerPreviewRenderer.hasModelCard(skin))||w<=0||h<=0||alpha<=0||!clipVisible())return;
        int texture=PlayerPreviewBackend.renderCapeCard(cacheKey,skin,w,h,density);
        if(texture==0)return;
        flush();
        int tint=Math.round(Math.max(0,Math.min(1,alpha))*255)<<24|0xffffff;
        LegacyUiRenderer.drawTexture(TexturedMesh.roundedRect(x,y,w,h,0,tint,0,0,1,1),texture,clip());
    }

    @Override protected void finished(){try{LegacyUiRenderer.end();}finally{invalidateNativeBuffers();syncNativeTextures();syncNativeBlend();}}
    @Override public void hungerIcon(float x,float y,float size,int fill){if(size<=0||!clipVisible())return;flush();suspendNative();try{VanillaHudBridge.hungerIcon(graphics,x,y,size,fill);}finally{resumeNative();}}
    @Override public boolean vanillaCrosshair(float x,float y,float size,float opacity){if(size<=0||opacity<=0||!clipVisible())return true;flush();suspendNative();try{CrosshairBridge.draw(graphics,x,y,size,opacity);return true;}finally{resumeNative();}}
    @Override public void viewmodelOutline(String hand,int argb){flush();int texture=ViewmodelOutlineBackend.texture(hand);if(texture!=0)LegacyUiRenderer.drawTexture(TexturedMesh.roundedRect(0,0,width,height,0,argb,0,1,1,0),texture,clip());}
    @Override protected boolean hasNativeTextFallback(){return true;}
    @Override public float gameTextWidth(String value,float scale){return value==null?0:Minecraft.getInstance().font.width(value)*scale;}
    @Override public void gameText(String value,float x,float y,float scale,int argb,boolean shadow){
        if(value==null||value.isEmpty()||scale<=0||!clipVisible())return;
        flush();suspendNative();
        try{@@GAME_TEXT@@}finally{resumeNative();}
    }
    @Override protected float rasterDensity(){return density;}
    @Override protected void beginText(FontAtlas atlas){if(glyphCount()>0 && glyphAtlas!=atlas)flushText();if(glyphCount()==0){glyphAtlas=atlas;beginGlyphs();}}
    @Override protected void endText(){}
    @Override protected void flushText(){if(glyphCount()>0){LegacyUiRenderer.drawText(glyphData(),glyphCount(),clip(),glyphAtlas.textureResourceId());beginGlyphs();}}
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
        if(stack.isEmpty() || !clipVisible())return;
        suspendNative();
        LegacyUiRenderer.ItemSnapshot backdrop=LegacyUiRenderer.captureItem(x,y,size,alpha);
        try{@@LEGACY_ITEM@@}finally{resumeNative();LegacyUiRenderer.compositeItem(backdrop,clip());}
    }
}
