package dev.nightclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nightclient.api.PlayerSkin;
import dev.nightclient.renderer.player.PlayerPreviewRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.@@IDENTIFIER@@;

public final class PlayerPreviewBackend {
    private static final PlayerPreviewRenderer WORKER_RENDERER=new PlayerPreviewRenderer();
    private static final long ACTIVE_INTERVAL=16000000L, IDLE_INTERVAL=33000000L;
    private static final int CARD_CACHE_LIMIT=256;
    private static final int MAX_CARD_UPLOADS_PER_FRAME=2;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t=new Thread(r,"NightClient-PreviewWorker");
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY,Thread.NORM_PRIORITY-1));
            return t;
        }
    });
    private static final List<@@IDENTIFIER@@> RETIRE_QUEUE=new ArrayList<@@IDENTIFIER@@>();
    private static DynamicTexture texture;
    private static @@IDENTIFIER@@ textureId;
    private static int textureW, textureH;
    private static int previewSequence=1;
    private static int cardSequence=1;
    private static int cardUploadsThisFrame;
    private static final AtomicLong GENERATION=new AtomicLong(1L);
    private static final AtomicBoolean PREVIEW_BUSY=new AtomicBoolean();
    private static final AtomicLong PREVIEW_SEQ=new AtomicLong();
    private static volatile PreviewFrame completedPreview;
    private static long uploadedPreviewSeq;
    private static int[] workerPixelsA=new int[0], workerPixelsB=new int[0];
    private static boolean workerUseA=true;

    private static final class PreviewFrame {
        final int[] pixels;
        final int width, height;
        final long seq, generation;
        PreviewFrame(int[] pixels,int width,int height,long seq,long generation) {
            this.pixels=pixels; this.width=width; this.height=height;
            this.seq=seq; this.generation=generation;
        }
    }

    private static final class CardPixels {
        final int[] pixels;
        final int width, height;
        final long generation;
        CardPixels(int[] pixels,int width,int height,long generation) {
            this.pixels=pixels; this.width=width; this.height=height; this.generation=generation;
        }
    }

    private static final class CardTexture {
        final DynamicTexture texture;
        final @@IDENTIFIER@@ id;
        CardTexture(DynamicTexture texture, @@IDENTIFIER@@ id) { this.texture=texture; this.id=id; }
    }
    private static final Map<String,CardTexture> CARD_CACHE=new LinkedHashMap<String,CardTexture>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,CardTexture> eldest) {
            if(size()<=CARD_CACHE_LIMIT) return false;
            CardTexture value=eldest.getValue();
            if(value!=null && value.id!=null) RETIRE_QUEUE.add(value.id);
            return true;
        }
    };
    private static final ConcurrentHashMap<String,CardPixels> READY_CARDS=new ConcurrentHashMap<String,CardPixels>();
    private static final Set<String> IN_FLIGHT_CARDS=ConcurrentHashMap.newKeySet();
    private static PlayerSkin lastSkin;
    private static long lastRender;
    private static int lastTargetW, lastTargetH;
    private static float lastYaw=Float.NaN,lastPitch=Float.NaN,lastZoom=Float.NaN;

    private PlayerPreviewBackend() {}

    private static int quantizeDim(int value,int min,int max) {
        int q=((value+8)>>4)<<4;
        return Math.max(min,Math.min(max,q));
    }

    public static void flushRetired() {
        cardUploadsThisFrame=0;
        if(RETIRE_QUEUE.isEmpty()) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null || mc.getTextureManager()==null) { RETIRE_QUEUE.clear(); return; }
        for(int i=0;i<RETIRE_QUEUE.size();i++) {
            try { mc.getTextureManager().release(RETIRE_QUEUE.get(i)); } catch(Throwable ignored) {}
        }
        RETIRE_QUEUE.clear();
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        completedPreview=null;
        uploadedPreviewSeq=0L;
        READY_CARDS.clear();
        IN_FLIGHT_CARDS.clear();
        if(textureId!=null) { RETIRE_QUEUE.add(textureId); textureId=null; }
        texture=null; textureW=0; textureH=0; lastTargetW=0; lastTargetH=0; lastSkin=null;
        for(CardTexture c:CARD_CACHE.values()) if(c!=null && c.id!=null) RETIRE_QUEUE.add(c.id);
        CARD_CACHE.clear();
    }

    public static void close() {
        clear();
        flushRetired();
        workerPixelsA=new int[0];
        workerPixelsB=new int[0];
    }

    public static DynamicTexture render(final PlayerSkin skin,final float yaw,final float pitch,final float zoom,final float time,final float wind,final float yawVelocity,float width,float height,float density) {
        if(skin==null || width<2f || height<2f) return texture;
        int[] size=PlayerPreviewRenderer.previewSize(width,height,density);
        final int targetW=quantizeDim(size[0],16,1024);
        final int targetH=quantizeDim(size[1],16,1024);
        if(targetW<2 || targetH<2) return texture;
        if(texture!=null && textureId!=null) {
            try {
                if(Minecraft.getInstance().getTextureManager().getTexture(textureId)!=texture) { texture=null; textureW=0; textureH=0; uploadedPreviewSeq=0L; }
            } catch(Throwable ignored) { texture=null; textureW=0; textureH=0; uploadedPreviewSeq=0L; }
        }
        long currentGen=GENERATION.get();
        PreviewFrame ready=completedPreview;
        if(ready!=null && ready.generation==currentGen && ready.seq!=uploadedPreviewSeq) {
            upload(ready.pixels,ready.width,ready.height);
            uploadedPreviewSeq=ready.seq;
        }
        long now=System.nanoTime();
        boolean changed=skin!=lastSkin || targetW!=lastTargetW || targetH!=lastTargetH || texture==null;
        boolean poseChanged=Float.compare(yaw,lastYaw)!=0 || Float.compare(pitch,lastPitch)!=0 || Float.compare(zoom,lastZoom)!=0;
        if(!changed && !poseChanged && skin.capePixels()==null) return texture;
        boolean moving=Math.abs(yawVelocity)>.2f;
        long interval=moving?ACTIVE_INTERVAL:IDLE_INTERVAL;
        if(!changed && now-lastRender<interval) return texture;
        if(!PREVIEW_BUSY.compareAndSet(false,true)) return texture;
        lastSkin=skin;
        lastTargetW=targetW; lastTargetH=targetH;
        lastYaw=yaw; lastPitch=pitch; lastZoom=zoom;
        lastRender=now;
        final long gen=currentGen;
        WORKER.execute(new Runnable() {
            @Override public void run() {
                try {
                    if(GENERATION.get()!=gen) return;
                    int count=targetW*targetH;
                    int[] buf;
                    if(workerUseA) {
                        if(workerPixelsA.length<count) workerPixelsA=new int[count];
                        buf=workerPixelsA;
                    } else {
                        if(workerPixelsB.length<count) workerPixelsB=new int[count];
                        buf=workerPixelsB;
                    }
                    workerUseA=!workerUseA;
                    WORKER_RENDERER.render(skin,yaw,pitch,zoom,time,wind,yawVelocity,targetW,targetH,buf);
                    if(GENERATION.get()==gen) {
                        completedPreview=new PreviewFrame(buf,targetW,targetH,PREVIEW_SEQ.incrementAndGet(),gen);
                    }
                } catch(Throwable ignored) {
                } finally {
                    PREVIEW_BUSY.set(false);
                }
            }
        });
        return texture;
    }

    private static void upload(int[] srcPixels,int width,int height) {
        if(srcPixels==null || srcPixels.length<width*height) return;
        if(texture==null || width!=textureW || height!=textureH) {
            if(textureId!=null) {
                RETIRE_QUEUE.add(textureId);
                textureId=null;
            }
            texture=null;
            textureW=width; textureH=height;
            String name="nightclient:player_preview_"+(previewSequence++);
            texture=new DynamicTexture(name,width,height,false);
            textureId=GameBridge.identifier(name);
            Minecraft.getInstance().getTextureManager().register(textureId,texture);
        }
        NativeImage image=texture.getPixels();
        if(image==null || image.getWidth()!=width || image.getHeight()!=height) return;
        for(int y=0;y<height;y++) {
            int row=y*width;
            for(int x=0;x<width;x++) image.setPixel(x,y,srcPixels[row+x]);
        }
        texture.upload();
    }

    public static DynamicTexture renderCapeCard(String cacheKey,final PlayerSkin skin,float width,float height,float density) {
        if(skin==null || (skin.capePixels()==null && !PlayerPreviewRenderer.hasModelCard(skin)) || width<2f || height<2f) return null;
        int[] size=PlayerPreviewRenderer.capeCardSize(width,height,density);
        final int targetW=quantizeDim(size[0],16,256);
        final int targetH=quantizeDim(size[1],16,256);
        if(targetW<2 || targetH<2) return null;
        final String key=(cacheKey==null?"cape":cacheKey)+"@"+targetW+"x"+targetH;
        CardTexture entry=CARD_CACHE.get(key);
        if(entry!=null && entry.texture!=null) {
            try {
                if(Minecraft.getInstance().getTextureManager().getTexture(entry.id)==entry.texture) return entry.texture;
            } catch(Throwable ignored) {}
            CARD_CACHE.remove(key);
        }
        final long gen=GENERATION.get();
        CardPixels ready=READY_CARDS.get(key);
        if(ready!=null) {
            if(ready.generation!=gen) {
                READY_CARDS.remove(key,ready);
            } else {
                if(cardUploadsThisFrame>=MAX_CARD_UPLOADS_PER_FRAME) return null;
                READY_CARDS.remove(key,ready);
                cardUploadsThisFrame++;
                String name="nightclient:cape_card_"+(cardSequence++);
                DynamicTexture card=new DynamicTexture(name,ready.width,ready.height,false);
                @@IDENTIFIER@@ id=GameBridge.identifier(name);
                Minecraft.getInstance().getTextureManager().register(id,card);
                NativeImage image=card.getPixels();
                if(image!=null && image.getWidth()==ready.width && image.getHeight()==ready.height) {
                    for(int y=0;y<ready.height;y++) {
                        int row=y*ready.width;
                        for(int x=0;x<ready.width;x++) image.setPixel(x,y,ready.pixels[row+x]);
                    }
                    card.upload();
                }
                CARD_CACHE.put(key,new CardTexture(card,id));
                return card;
            }
        }
        if(READY_CARDS.size()<64 && IN_FLIGHT_CARDS.add(key)) {
            WORKER.execute(new Runnable() {
                @Override public void run() {
                    try {
                        if(GENERATION.get()!=gen) return;
                        int[] buf=new int[targetW*targetH];
                        PlayerPreviewRenderer.renderCosmeticCard(skin,targetW,targetH,buf);
                        if(GENERATION.get()==gen) READY_CARDS.put(key,new CardPixels(buf,targetW,targetH,gen));
                    } catch(Throwable ignored) {
                    } finally {
                        IN_FLIGHT_CARDS.remove(key);
                    }
                }
            });
        }
        return null;
    }
}
