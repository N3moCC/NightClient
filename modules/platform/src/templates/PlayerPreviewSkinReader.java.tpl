package dev.nightclient.platform;

import dev.nightclient.api.PlayerSkin;
import dev.nightclient.renderer.player.DefaultSkins;
import dev.nightclient.renderer.player.PlayerSkinData;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.@@IDENTIFIER@@;

public final class PlayerPreviewSkinReader {
    private static final ExecutorService IO_WORKER=Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t=new Thread(r,"NightClient-SkinReader");
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY,Thread.NORM_PRIORITY-2));
            return t;
        }
    });
    private static final AtomicBoolean LOADING=new AtomicBoolean();
    private static volatile String bodyKey="", capeKey="";
    private static volatile long bodyStamp=Long.MIN_VALUE, capeStamp=Long.MIN_VALUE;
    private static volatile boolean slimModel;
    private static volatile PlayerSkinData cached;
    private static long nextCheck;
    private static volatile Path cachedAssetsDir;
    private static volatile boolean assetsDirResolved;

    private PlayerPreviewSkinReader() {}

    public static PlayerSkin read() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null) return DefaultSkins.forModel(false);
        @@IDENTIFIER@@ skin;
        @@IDENTIFIER@@ cape;
        final boolean slim;
        CosmeticsBridge.suspend(true);
        try {
            skin=@@PREVIEW_SKIN_ID@@;
            cape=@@PREVIEW_CAPE_ID@@;
            slim=@@PREVIEW_SLIM@@;
        } finally {
            CosmeticsBridge.suspend(false);
        }
        final String skinId=skin==null?"":skin.toString();
        final String capeId=cape==null?"":cape.toString();
        long now=System.nanoTime();
        boolean identityChanged=!skinId.equals(bodyKey) || !capeId.equals(capeKey) || slimModel!=slim;
        PlayerSkinData current=cached;
        if((current==null || identityChanged || now-nextCheck>=0L) && LOADING.compareAndSet(false,true)) {
            nextCheck=now+1000000000L;
            final Path gameDir=mc.gameDirectory==null?null:mc.gameDirectory.toPath().toAbsolutePath();
            IO_WORKER.execute(new Runnable() {
                @Override public void run() {
                    try {
                        Path bodyPath=texturePath(gameDir,"skins",skinId);
                        Path capePath=texturePath(gameDir,"capes",capeId);
                        long bodyTime=stamp(bodyPath), capeTime=stamp(capePath);
                        if(cached!=null && skinId.equals(bodyKey) && capeId.equals(capeKey)
                                && bodyTime==bodyStamp && capeTime==capeStamp && slimModel==slim) {
                            return;
                        }
                        int[] body=readBody(bodyPath);
                        PlayerSkinData next=body==null
                                ? DefaultSkins.forModel(slim)
                                : new PlayerSkinData(body,readCape(capePath),slim);
                        bodyKey=skinId; capeKey=capeId; bodyStamp=bodyTime; capeStamp=capeTime; slimModel=slim;
                        cached=next;
                    } catch(Throwable ignored) {
                    } finally {
                        LOADING.set(false);
                    }
                }
            });
        }
        return current!=null ? current : DefaultSkins.forModel(slim);
    }

    private static Path resolveAssetsDir(Path gameDir) {
        if(assetsDirResolved) return cachedAssetsDir;
        assetsDirResolved=true;
        if(gameDir==null) return null;
        for(Path base=gameDir;base!=null;base=base.getParent()) {
            Path candidate=base.resolve("assets");
            if(Files.isDirectory(candidate)) {
                cachedAssetsDir=candidate;
                return candidate;
            }
        }
        return null;
    }

    private static Path texturePath(Path gameDir,String directory,String id) {
        int slash=id.lastIndexOf('/');
        if(slash<0 || slash+1>=id.length()) return null;
        String hash=id.substring(slash+1);
        if(hash.indexOf('.')>=0 || hash.length()<2) return null;
        Path assetsDir=resolveAssetsDir(gameDir);
        if(assetsDir==null) return null;
        Path root=assetsDir.resolve(directory);
        Path direct=root.resolve(hash);
        if(Files.isRegularFile(direct)) return direct;
        Path nested=root.resolve(hash.substring(0,2)).resolve(hash);
        if(Files.isRegularFile(nested)) return nested;
        Path png=root.resolve(hash+".png");
        if(Files.isRegularFile(png)) return png;
        return null;
    }

    private static long stamp(Path path) {
        if(path==null) return -1L;
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch(Exception ignored) { return -1L; }
    }

    private static int[] readBody(Path path) {
        BufferedImage image=readImage(path);
        if(image==null) return null;
        int w=image.getWidth(), h=image.getHeight();
        if(w==64 && h==32) return legacyBody(image);
        if(w!=64 || h!=64) return null;
        int[] out=new int[PlayerSkin.BODY_SIZE*PlayerSkin.BODY_SIZE];
        for(int y=0;y<64;y++) for(int x=0;x<64;x++) out[y*64+x]=image.getRGB(x,y);
        return out;
    }

    private static int[] readCape(Path path) {
        BufferedImage image=readImage(path);
        if(image==null || image.getWidth()!=PlayerSkin.CAPE_WIDTH || image.getHeight()!=PlayerSkin.CAPE_HEIGHT) return null;
        int[] out=new int[PlayerSkin.CAPE_WIDTH*PlayerSkin.CAPE_HEIGHT];
        for(int y=0;y<PlayerSkin.CAPE_HEIGHT;y++) for(int x=0;x<PlayerSkin.CAPE_WIDTH;x++) out[y*PlayerSkin.CAPE_WIDTH+x]=image.getRGB(x,y);
        return out;
    }

    private static BufferedImage readImage(Path path) {
        if(path==null) return null;
        try(InputStream stream=Files.newInputStream(path)) { return ImageIO.read(stream); }
        catch(Exception ignored) { return null; }
    }

    private static int[] legacyBody(BufferedImage image) {
        int[] out=new int[PlayerSkin.BODY_SIZE*PlayerSkin.BODY_SIZE];
        copy(image,out,0,0,0,0,64,16);
        copy(image,out,0,16,0,16,16,16);
        copy(image,out,16,16,16,16,24,16);
        copy(image,out,40,16,40,16,16,16);
        mirror(out,40,16,32,48,16,16);
        mirror(out,0,16,16,48,16,16);
        return out;
    }

    private static void copy(BufferedImage image,int[] out,int sx,int sy,int dx,int dy,int w,int h) {
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) out[(dy+y)*64+dx+x]=image.getRGB(sx+x,sy+y);
    }

    private static void mirror(int[] out,int sx,int sy,int dx,int dy,int w,int h) {
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) out[(dy+y)*64+dx+x]=out[(sy+y)*64+sx+(w-1-x)];
    }
}
