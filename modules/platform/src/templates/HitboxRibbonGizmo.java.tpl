package dev.nightclient.platform;
import dev.nightclient.renderer.WorldRibbon;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HitboxRibbonGizmo implements Gizmo {
    public interface OverlayEmitter {
        void emit(WorldRibbon.Quads quads);
    }
    private static final boolean BACKFACE_CULLED = detectBackfaceCulled();
    private static final double[] PROJ_SCRATCH = new double[5];
    private static final QuadSink SINK = new QuadSink();

    private static boolean detectBackfaceCulled() {
        try {
            return net.minecraft.client.renderer.RenderPipelines.DEBUG_FILLED_BOX.isCull();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static final class QuadSink implements WorldRibbon.Quads {
        GizmoPrimitives out;
        float opacity;
        boolean fullOpacity;
        boolean doubleSided;

        @Override
        public void quad(double ax, double ay, double az, double bx, double by, double bz,
                         double cx, double cy, double cz, double dx, double dy, double dz, int color) {
            int argb;
            if (fullOpacity) {
                if ((color >>> 24) == 0) return;
                argb = color;
            } else {
                int alpha = Math.max(0, Math.min(255, Math.round((color >>> 24) * opacity)));
                if (alpha == 0) return;
                argb = (alpha << 24) | (color & 0xffffff);
            }
            Vec3 va = new Vec3(ax, ay, az);
            Vec3 vb = new Vec3(bx, by, bz);
            Vec3 vc = new Vec3(cx, cy, cz);
            Vec3 vd = new Vec3(dx, dy, dz);
            out.addQuad(va, vb, vc, vd, argb);
            if (doubleSided) {
                out.addQuad(va, vd, vc, vb, argb);
            }
        }
    }

    private final AABB box;
    private final WorldRibbon.View view;
    private final boolean stroke;
    private final int color,fill;
    private final float width;
    private final Vec3 eye,end;
    private final OverlayEmitter overlay;
    public HitboxRibbonGizmo(AABB box,WorldRibbon.View view,boolean stroke,int color,int fill,float width,Vec3 eye,Vec3 end){this.box=box;this.view=view;this.stroke=stroke;this.color=color;this.fill=fill;this.width=width;this.eye=eye;this.end=end;this.overlay=null;}
    public HitboxRibbonGizmo(OverlayEmitter overlay){this.box=null;this.view=null;this.stroke=false;this.color=0;this.fill=0;this.width=1f;this.eye=null;this.end=null;this.overlay=overlay;}

    private static boolean isBoxVisible(WorldRibbon.View view, AABB box, Vec3 eye, Vec3 end, float width) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        if (eye != null && end != null) {
            if (eye.x < minX) minX = eye.x; else if (eye.x > maxX) maxX = eye.x;
            if (eye.y < minY) minY = eye.y; else if (eye.y > maxY) maxY = eye.y;
            if (eye.z < minZ) minZ = eye.z; else if (eye.z > maxZ) maxZ = eye.z;
            if (end.x < minX) minX = end.x; else if (end.x > maxX) maxX = end.x;
            if (end.y < minY) minY = end.y; else if (end.y > maxY) maxY = end.y;
            if (end.z < minZ) minZ = end.z; else if (end.z > maxZ) maxZ = end.z;
        }
        double cx = (minX + maxX) * 0.5;
        double cy = (minY + maxY) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        double hx = (maxX - minX) * 0.5;
        double hy = (maxY - minY) * 0.5;
        double hz = (maxZ - minZ) * 0.5;
        double radius = Math.sqrt(hx * hx + hy * hy + hz * hz);
        double[] proj = PROJ_SCRATCH;
        view.project(cx, cy, cz, proj);
        double fwd = proj[2];
        if (fwd + radius <= 0.06) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return true;
        int sw = mc.getWindow().getWidth();
        int sh = mc.getWindow().getHeight();
        if (sw <= 0 || sh <= 0) return true;
        double margin = Math.max(24.0, width * 2.0 + 16.0);
        if (fwd > 0.06 && proj[0] >= -margin && proj[0] <= sw + margin && proj[1] >= -margin && proj[1] <= sh + margin) {
            return true;
        }
        if (fwd - radius <= 0.06) return true;
        boolean allLeft = true, allRight = true, allTop = true, allBottom = true;
        for (int c = 0; c < 8; c++) {
            double px = (c & 1) == 0 ? minX : maxX;
            double py = (c & 2) == 0 ? minY : maxY;
            double pz = (c & 4) == 0 ? minZ : maxZ;
            view.project(px, py, pz, proj);
            if (proj[2] <= 0.06) return true;
            double sx = proj[0], sy = proj[1];
            if (sx >= -margin) allLeft = false;
            if (sx <= sw + margin) allRight = false;
            if (sy >= -margin) allTop = false;
            if (sy <= sh + margin) allBottom = false;
            if (!allLeft && !allRight && !allTop && !allBottom) return true;
        }
        return !(allLeft || allRight || allTop || allBottom);
    }

    @Override public void emit(final GizmoPrimitives out,final float opacity){
        if(out==null||opacity<=0.0f)return;
        final GizmoPrimitives prevOut=SINK.out;
        final float prevOpacity=SINK.opacity;
        final boolean prevFull=SINK.fullOpacity;
        final boolean prevDouble=SINK.doubleSided;
        SINK.out=out;
        SINK.opacity=opacity;
        SINK.fullOpacity=opacity>=1.0f;
        try{
            if(overlay!=null){
                SINK.doubleSided=BACKFACE_CULLED;
                overlay.emit(SINK);
                return;
            }
            boolean drawFill=(fill>>>24)!=0;
            boolean drawStroke=stroke&&(color>>>24)!=0;
            if((!drawFill&&!drawStroke)||box==null)return;
            if(view!=null&&!isBoxVisible(view,box,drawStroke?eye:null,drawStroke?end:null,width))return;
            if(drawFill){
                SINK.doubleSided=BACKFACE_CULLED;
                WorldFeatureBridge.drawBox(SINK,view,box,false,0,fill,width);
            }
            if(drawStroke){
                SINK.doubleSided=false;
                WorldFeatureBridge.drawBox(SINK,view,box,true,color,0,width);
                if(eye!=null&&end!=null)WorldRibbon.line(SINK,view,eye.x,eye.y,eye.z,end.x,end.y,end.z,width,color);
            }
        }finally{
            SINK.out=prevOut;
            SINK.opacity=prevOpacity;
            SINK.fullOpacity=prevFull;
            SINK.doubleSided=prevDouble;
        }
    }
}
