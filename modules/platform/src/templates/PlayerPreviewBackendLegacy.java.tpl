package dev.nightclient.platform;

import dev.nightclient.api.PlayerSkin;
import dev.nightclient.renderer.player.PlayerPreviewRenderer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

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
    private static int texture, textureW, textureH, bufferW, bufferH;
    private static IntBuffer buffer;
    private static IntBuffer cardBuffer;
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

    private static final Map<String,Integer> CARD_CACHE=new LinkedHashMap<String,Integer>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Integer> eldest) {
            if(size()<=CARD_CACHE_LIMIT) return false;
            Integer tex=eldest.getValue();
            if(tex!=null && tex.intValue()!=0) GL11.glDeleteTextures(tex.intValue());
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
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        completedPreview=null;
        uploadedPreviewSeq=0L;
        READY_CARDS.clear();
        IN_FLIGHT_CARDS.clear();
        if(texture!=0) {
            try { GL11.glDeleteTextures(texture); } catch(Throwable ignored) {}
            texture=0;
        }
        textureW=0; textureH=0; bufferW=0; bufferH=0; lastTargetW=0; lastTargetH=0; lastSkin=null;
        for(Integer tex:CARD_CACHE.values()) {
            if(tex!=null && tex.intValue()!=0) {
                try { GL11.glDeleteTextures(tex.intValue()); } catch(Throwable ignored) {}
            }
        }
        CARD_CACHE.clear();
    }

    public static void close() {
        clear();
        buffer=null;
        cardBuffer=null;
        workerPixelsA=new int[0];
        workerPixelsB=new int[0];
    }

    public static int render(final PlayerSkin skin,final float yaw,final float pitch,final float zoom,final float time,final float wind,final float yawVelocity,float width,float height,float density) {
        if(skin==null || width<2f || height<2f) return texture;
        int[] size=PlayerPreviewRenderer.previewSize(width,height,density);
        final int targetW=quantizeDim(size[0],16,1024);
        final int targetH=quantizeDim(size[1],16,1024);
        if(targetW<2 || targetH<2) return texture;
        long currentGen=GENERATION.get();
        PreviewFrame ready=completedPreview;
        if(ready!=null && ready.generation==currentGen && ready.seq!=uploadedPreviewSeq) {
            upload(ready.pixels,ready.width,ready.height);
            uploadedPreviewSeq=ready.seq;
        }
        long now=System.nanoTime();
        boolean changed=skin!=lastSkin || targetW!=lastTargetW || targetH!=lastTargetH || texture==0;
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
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LegacyUiRenderer.PixelUnpackState unpack=new LegacyUiRenderer.PixelUnpackState();
        try {
            if(texture==0) {
                texture=GL11.glGenTextures();
                if(texture==0) return;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            }
            int count=width*height;
            if(buffer==null || buffer.capacity()<count) buffer=BufferUtils.createIntBuffer(count);
            buffer.clear();
            for(int i=0;i<count;i++) {
                int c=srcPixels[i];
                buffer.put((c&0xFF00FF00)|((c&0xFF)<<16)|((c>>>16)&0xFF));
            }
            buffer.flip();
            if(width!=bufferW || height!=bufferH) {
                bufferW=width;bufferH=height;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
            }
            textureW=width;textureH=height;
        } finally {
            unpack.restore();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);
        }
    }

    public static int renderCapeCard(String cacheKey,final PlayerSkin skin,float width,float height,float density) {
        if(skin==null || (skin.capePixels()==null && !PlayerPreviewRenderer.hasModelCard(skin)) || width<2f || height<2f) return 0;
        int[] size=PlayerPreviewRenderer.capeCardSize(width,height,density);
        final int targetW=quantizeDim(size[0],16,256);
        final int targetH=quantizeDim(size[1],16,256);
        if(targetW<2 || targetH<2) return 0;
        final String key=(cacheKey==null?"cape":cacheKey)+"@"+targetW+"x"+targetH;
        Integer existing=CARD_CACHE.get(key);
        if(existing!=null && existing.intValue()!=0) return existing.intValue();
        final long gen=GENERATION.get();
        CardPixels ready=READY_CARDS.get(key);
        if(ready!=null) {
            if(ready.generation!=gen) {
                READY_CARDS.remove(key,ready);
            } else {
                if(cardUploadsThisFrame>=MAX_CARD_UPLOADS_PER_FRAME) return 0;
                READY_CARDS.remove(key,ready);
                cardUploadsThisFrame++;
                int tex=GL11.glGenTextures();
                if(tex==0) return 0;
                int count=ready.width*ready.height;
                int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                LegacyUiRenderer.PixelUnpackState unpack=new LegacyUiRenderer.PixelUnpackState();
                try {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                    if(cardBuffer==null || cardBuffer.capacity()<count) cardBuffer=BufferUtils.createIntBuffer(count);
                    cardBuffer.clear();
                    for(int i=0;i<count;i++) {
                        int c=ready.pixels[i];
                        cardBuffer.put((c&0xFF00FF00)|((c&0xFF)<<16)|((c>>>16)&0xFF));
                    }
                    cardBuffer.flip();
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,ready.width,ready.height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,cardBuffer);
                } catch(RuntimeException ex) {
                    GL11.glDeleteTextures(tex);
                    throw ex;
                } finally {
                    unpack.restore();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);
                }
                CARD_CACHE.put(key,Integer.valueOf(tex));
                return tex;
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
        return 0;
    }
}
