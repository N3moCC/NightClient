package dev.nightclient.platform;

import com.mojang.blaze3d.vertex.VertexConsumer;
import @@PIPELINE_PACKAGE@@.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import @@GUI_STATE_PACKAGE@@.GuiElementRenderState;

public final class GuiMeshState implements GuiElementRenderState {
    @@INVERSE_PIPELINE@@
    public static void prewarm(){@@INVERSE_PREWARM@@}
    private final float[] v;
    private final boolean inverse;
    private final ScreenRectangle bounds,scissor;
    public GuiMeshState(float[] v,Matrix3x2f pose,float[] clip,int width,int height){
        this(v,pose,clip,width,height,false);
    }
    public GuiMeshState(float[] v,Matrix3x2f pose,float[] clip,int width,int height,boolean inverse){
        this.inverse=inverse;
        this.v=v;float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
        boolean identity=pose.m00()==1f && pose.m01()==0f && pose.m10()==0f && pose.m11()==1f && pose.m20()==0f && pose.m21()==0f;
        if(identity){
            for(int i=0;i<v.length;i+=6){float x=v[i],y=v[i+1];if(x<left)left=x;if(x>right)right=x;if(y<top)top=y;if(y>bottom)bottom=y;}
        }else{
            float m00=pose.m00(),m10=pose.m10(),m20=pose.m20(),m01=pose.m01(),m11=pose.m11(),m21=pose.m21();
            for(int i=0;i<v.length;i+=6){float x=v[i],y=v[i+1],tx=m00*x+m10*y+m20,ty=m01*x+m11*y+m21;v[i]=tx;v[i+1]=ty;if(tx<left)left=tx;if(tx>right)right=tx;if(ty<top)top=ty;if(ty>bottom)bottom=ty;}
        }
        int gl=(int)Math.floor(left),gt=(int)Math.floor(top);
        ScreenRectangle geometryBounds=new ScreenRectangle(gl,gt,Math.max(1,(int)Math.ceil(right)-gl),Math.max(1,(int)Math.ceil(bottom)-gt));
        scissor=scissor(pose,clip,width,height);
        bounds=scissor==null?geometryBounds:geometryBounds.intersection(scissor);
    }
    static ScreenRectangle scissor(Matrix3x2f pose,float[] clip,int width,int height){
        if(clip==null)return null;
        int cx=(int)Math.floor(clip[0]),cy=(int)Math.floor(clip[1]),cw=Math.max(0,(int)Math.ceil(clip[2])-cx),ch=Math.max(0,(int)Math.ceil(clip[3])-cy);
        if(pose.m00()==1f && pose.m01()==0f && pose.m10()==0f && pose.m11()==1f && pose.m20()==0f && pose.m21()==0f){
            int rx=Math.max(0,cx),ry=Math.max(0,cy),r1=Math.min(width,cx+cw),b1=Math.min(height,cy+ch);
            return r1>rx && b1>ry?new ScreenRectangle(rx,ry,r1-rx,b1-ry):new ScreenRectangle(0,0,0,0);
        }
        ScreenRectangle transformed=new ScreenRectangle(cx,cy,cw,ch).transformAxisAligned(pose);
        ScreenRectangle clipped=transformed.intersection(new ScreenRectangle(0,0,width,height));

        return clipped==null?new ScreenRectangle(0,0,0,0):clipped;
    }
    static boolean visible(ScreenRectangle rectangle){return rectangle==null || rectangle.width()>0 && rectangle.height()>0;}
    public boolean visible(){return bounds!=null && visible(bounds) && visible(scissor);}
    @Override public RenderPipeline pipeline(){return inverse?INVERSE:RenderPipelines.GUI;}
    @Override public TextureSetup textureSetup(){return TextureSetup.noTexture();}
    @Override public ScreenRectangle scissorArea(){return scissor;}
    @Override public ScreenRectangle bounds(){return bounds;}
    private void put(VertexConsumer out,int index,float z){out.addVertex(v[index],v[index+1],z).setColor(v[index+2],v[index+3],v[index+4],v[index+5]);}
    @Override public void buildVertices(VertexConsumer out@@BUILD_DEPTH_ARGUMENT@@){

        for(int i=0;i<v.length;i+=18){put(out,i,@@BUILD_DEPTH@@);put(out,i+12,@@BUILD_DEPTH@@);put(out,i+6,@@BUILD_DEPTH@@);put(out,i+6,@@BUILD_DEPTH@@);}
    }
}
