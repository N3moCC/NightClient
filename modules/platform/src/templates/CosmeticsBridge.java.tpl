package dev.nightclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.@@COSMETICS_IDENTIFIER@@;

public final class CosmeticsBridge {
    private static final ThreadLocal<Boolean> SUSPEND=new ThreadLocal<Boolean>();

    private static final class Snapshot {
        final int[] skinPixels;
        final int skinW, skinH, skinRev;
        final boolean capeOverride, capeSuppressed;
        final int[] capePixels;
        final int capeW, capeH, capeRev;
        final Boolean slimOverride;

        Snapshot(int[] skinPixels,int skinW,int skinH,int skinRev,boolean capeOverride,boolean capeSuppressed,
                 int[] capePixels,int capeW,int capeH,int capeRev,Boolean slimOverride){
            this.skinPixels=skinPixels;this.skinW=skinW;this.skinH=skinH;this.skinRev=skinRev;
            this.capeOverride=capeOverride;this.capeSuppressed=capeSuppressed;
            this.capePixels=capePixels;this.capeW=capeW;this.capeH=capeH;this.capeRev=capeRev;
            this.slimOverride=slimOverride;
        }
    }

    private static volatile Snapshot current=new Snapshot(null,0,0,0,false,false,null,0,0,0,null);
    private static @@COSMETICS_IDENTIFIER@@ skinId;
    private static @@COSMETICS_IDENTIFIER@@ capeId;
    private static DynamicTexture uploadedSkin;
    private static int uploadedSkinRev=-1;
    private static DynamicTexture uploadedCape;
    private static int uploadedCapeRev=-1;

    private CosmeticsBridge(){}

    public static void update(int[] skinPixels,int skinW,int skinH,int skinRev,boolean capeOverride,
                              boolean capeSuppressed,int[] capePixels,int capeW,int capeH,int capeRev,
                              Boolean slimOverride){
        current=new Snapshot(skinPixels,skinW,skinH,skinRev,capeOverride,capeSuppressed,capePixels,capeW,capeH,capeRev,slimOverride);
    }

    public static void suspend(boolean value){
        if(value)SUSPEND.set(Boolean.TRUE);
        else SUSPEND.remove();
    }

    public static boolean appliesTo(Object entity){
        if(Boolean.TRUE.equals(SUSPEND.get()))return false;
        Minecraft mc=Minecraft.getInstance();
        return mc!=null && entity!=null && entity==mc.player;
    }

    public static boolean capeSuppressed(){return current.capeSuppressed;}

    public static boolean capeOverridden(){return current.capeOverride;}

    public static Boolean slimOverride(){return current.slimOverride;}

    public static @@COSMETICS_IDENTIFIER@@ skinTexture(){
        Snapshot s=current;
        if(s.skinPixels==null || s.skinW<=0 || s.skinH<=0 || (long)s.skinW*s.skinH>s.skinPixels.length){
            if(uploadedSkin!=null && RenderSystem.isOnRenderThread())releaseSkin();
            return null;
        }
        @@COSMETICS_IDENTIFIER@@ id=skinResource();
        if(uploadedSkin!=null && uploadedSkinRev==s.skinRev){
            Minecraft mc=Minecraft.getInstance();
            TextureManager manager=mc==null?null:mc.getTextureManager();
            if(manager!=null && manager.getTexture(id)==uploadedSkin)return id;
        }
        if(!RenderSystem.isOnRenderThread())return null;
        DynamicTexture next=uploadTexture(id,s.skinPixels,s.skinW,s.skinH,"nightclient:cosmetics/skin");
        if(next==null)return null;
        uploadedSkin=next;
        uploadedSkinRev=s.skinRev;
        return id;
    }

    public static @@COSMETICS_IDENTIFIER@@ capeTexture(){
        Snapshot s=current;
        if(s.capeSuppressed || !s.capeOverride || s.capePixels==null || s.capeW<=0 || s.capeH<=0 || (long)s.capeW*s.capeH>s.capePixels.length){
            if(uploadedCape!=null && RenderSystem.isOnRenderThread())releaseCape();
            return null;
        }
        @@COSMETICS_IDENTIFIER@@ id=capeResource();
        if(uploadedCape!=null && uploadedCapeRev==s.capeRev){
            Minecraft mc=Minecraft.getInstance();
            TextureManager manager=mc==null?null:mc.getTextureManager();
            if(manager!=null && manager.getTexture(id)==uploadedCape)return id;
        }
        if(!RenderSystem.isOnRenderThread())return null;
        DynamicTexture next=uploadTexture(id,s.capePixels,s.capeW,s.capeH,"nightclient:cosmetics/cape");
        if(next==null)return null;
        uploadedCape=next;
        uploadedCapeRev=s.capeRev;
        return id;
    }

    private static void releaseSkin(){
        uploadedSkin=null;uploadedSkinRev=-1;
        Minecraft mc=Minecraft.getInstance();
        if(mc!=null && mc.getTextureManager()!=null)mc.getTextureManager().release(skinResource());
    }

    private static void releaseCape(){
        uploadedCape=null;uploadedCapeRev=-1;
        Minecraft mc=Minecraft.getInstance();
        if(mc!=null && mc.getTextureManager()!=null)mc.getTextureManager().release(capeResource());
    }

    public static void resourceReload(){
        uploadedSkin=null;uploadedSkinRev=-1;
        uploadedCape=null;uploadedCapeRev=-1;
    }

    public static void close(){
        if(RenderSystem.isOnRenderThread()){
            if(uploadedSkin!=null)releaseSkin();
            if(uploadedCape!=null)releaseCape();
        }else{
            uploadedSkin=null;uploadedSkinRev=-1;
            uploadedCape=null;uploadedCapeRev=-1;
        }
    }

    private static @@COSMETICS_IDENTIFIER@@ parseIdentifier(String id){
        return @@COSMETICS_IDENTIFIER_PARSE@@;
    }

    private static @@COSMETICS_IDENTIFIER@@ skinResource(){
        if(skinId==null)skinId=parseIdentifier("nightclient:cosmetics/skin");
        return skinId;
    }

    private static @@COSMETICS_IDENTIFIER@@ capeResource(){
        if(capeId==null)capeId=parseIdentifier("nightclient:cosmetics/cape");
        return capeId;
    }

    private static DynamicTexture uploadTexture(@@COSMETICS_IDENTIFIER@@ id,int[] pixels,int w,int h,final String label){
        Minecraft mc=Minecraft.getInstance();
        if(mc==null || mc.getTextureManager()==null)return null;
        NativeImage image=new NativeImage(w,h,false);
        DynamicTexture texture=null;
        try{
            for(int y=0;y<h;y++){
                int row=y*w;
                for(int x=0;x<w;x++){
                    int pixel=pixels[row+x];
                    @@COSMETICS_SET_PIXEL@@
                }
            }
            texture=new DynamicTexture(@@COSMETICS_DYN_ARGS@@ image);
            texture.upload();
            mc.getTextureManager().register(id,texture);
            return texture;
        }catch(Throwable failure){
            if(texture!=null){
                try{texture.close();}catch(Throwable ignored){}
            }else{
                try{image.close();}catch(Throwable ignored){}
            }
            return null;
        }
    }

    public static String chooseImageFile(String title){
        String lang=dev.nightclient.runtime.ClientRuntime.language();
        return openNativeFileDialog(
            sanitizeTitle(title,dev.nightclient.i18n.I18n.text(lang,"filePicker.default_image_title")),
            new String[]{"*.png","*.jpg","*.jpeg","*.webp","*.bmp"},
            dev.nightclient.i18n.I18n.text(lang,"filePicker.images")+" (*.png, *.jpg, *.jpeg, *.webp, *.bmp)");
    }

    public static String chooseAudioFile(String title){
        String lang=dev.nightclient.runtime.ClientRuntime.language();
        return openNativeFileDialog(
            sanitizeTitle(title,dev.nightclient.i18n.I18n.text(lang,"filePicker.default_audio_title")),
            new String[]{"*.mp3","*.wav"},
            dev.nightclient.i18n.I18n.text(lang,"filePicker.audio")+" (*.mp3, *.wav)");
    }

    private static String openNativeFileDialog(String safeTitle,String[] patterns,String filterDesc){
        try{
            Class<?> tinyfd=Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Class<?> pointerBuffer=Class.forName("org.lwjgl.PointerBuffer");
            Method method=tinyfd.getMethod("tinyfd_openFileDialog",CharSequence.class,CharSequence.class,pointerBuffer,CharSequence.class,boolean.class);
            Object picked=null;
            boolean invoked=false;
            try{
                Class<?> stackClass=Class.forName("org.lwjgl.system.MemoryStack");
                Object stack=stackClass.getMethod("stackPush").invoke(null);
                try{
                    Object filters=null;
                    if(patterns!=null && patterns.length>0){
                        filters=stackClass.getMethod("mallocPointer",int.class).invoke(stack,patterns.length);
                        Method utf8=stackClass.getMethod("UTF8",CharSequence.class);
                        Method put=pointerBuffer.getMethod("put",ByteBuffer.class);
                        for(String pattern:patterns)put.invoke(filters,utf8.invoke(stack,pattern));
                        pointerBuffer.getMethod("flip").invoke(filters);
                    }
                    picked=method.invoke(null,safeTitle,"",filters,filterDesc,false);
                    invoked=true;
                }finally{
                    stackClass.getMethod("close").invoke(stack);
                }
            }catch(Throwable stackFailure){
                if(!invoked){
                    picked=method.invoke(null,safeTitle,"",null,filterDesc,false);
                    invoked=true;
                }
            }
            if(invoked)return picked==null?"":picked.toString().trim();
        }catch(Throwable ignored){}
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(!os.contains("mac")){
            try{
                Class<?> ge=Class.forName("java.awt.GraphicsEnvironment");
                if(!Boolean.TRUE.equals(ge.getMethod("isHeadless").invoke(null))){
                    Class<?> fdClass=Class.forName("java.awt.FileDialog");
                    Class<?> frameClass=Class.forName("java.awt.Frame");
                    Object dialog=fdClass.getConstructor(frameClass,String.class,int.class).newInstance(null,safeTitle,0);
                    fdClass.getMethod("setVisible",boolean.class).invoke(dialog,Boolean.TRUE);
                    Object file=fdClass.getMethod("getFile").invoke(dialog);
                    Object dir=fdClass.getMethod("getDirectory").invoke(dialog);
                    fdClass.getMethod("dispose").invoke(dialog);
                    return file==null?"":(dir==null?file.toString():dir.toString()+file.toString());
                }
            }catch(Throwable ignored){}
        }
        return "__NO_PICKER__";
    }

    private static String sanitizeTitle(String title,String fallback){
        if(title==null || title.trim().isEmpty())return fallback;
        StringBuilder out=new StringBuilder();
        for(int i=0;i<title.length() && out.length()<64;i++){
            char c=title.charAt(i);
            if(c>=' ' && c!='"' && c!='\'' && c!='`' && c!='\\' && c!='$')out.append(c);
        }
        String text=out.toString().trim();
        return text.isEmpty()?fallback:text;
    }
}
