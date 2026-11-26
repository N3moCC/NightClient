package dev.nightclient.platform;

import com.mojang.blaze3d.vertex.VertexConsumer;
import @@PIPELINE_PACKAGE@@.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import @@GUI_STATE_PACKAGE@@.GuiElementRenderState;

public final class GuiTextureMeshState implements GuiElementRenderState {
    @@ROUNDED_PIPELINE@@
    public static void prewarm(){@@ROUNDED_PREWARM@@}
    private final float[] v;
    private final boolean analytic;
    private final ScreenRectangle bounds,scissor;
    private final TextureSetup textures;
    public GuiTextureMeshState(float[] vertices,Matrix3x2f pose,float[] clip,TextureSetup textures,int screenWidth,int screenHeight,boolean backdrop,boolean topDown){
        this(vertices,pose,clip,textures,screenWidth,screenHeight,backdrop,topDown,false);
    }
    public GuiTextureMeshState(float[] vertices,Matrix3x2f pose,float[] clip,TextureSetup textures,int screenWidth,int screenHeight,boolean backdrop,boolean topDown,boolean analytic){
        this.analytic=analytic;this.v=vertices;this.textures=textures;float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
        boolean identity=pose.m00()==1f && pose.m01()==0f && pose.m10()==0f && pose.m11()==1f && pose.m20()==0f && pose.m21()==0f;
        if(identity && !backdrop){
            for(int i=0;i<v.length;i+=8){float x=v[i],y=v[i+1];if(x<left)left=x;if(x>right)right=x;if(y<top)top=y;if(y>bottom)bottom=y;}
        }else{
            float m00=pose.m00(),m10=pose.m10(),m20=pose.m20(),m01=pose.m01(),m11=pose.m11(),m21=pose.m21();
            float invW=backdrop?1f/Math.max(1,screenWidth):0f,invH=backdrop?1f/Math.max(1,screenHeight):0f;
            for(int i=0;i<v.length;i+=8){float x=v[i],y=v[i+1],tx=identity?x:m00*x+m10*y+m20,ty=identity?y:m01*x+m11*y+m21;v[i]=tx;v[i+1]=ty;if(backdrop){v[i+2]=tx*invW;v[i+3]=topDown?ty*invH:1f-ty*invH;}if(tx<left)left=tx;if(tx>right)right=tx;if(ty<top)top=ty;if(ty>bottom)bottom=ty;}
        }
        int gl=(int)Math.floor(left),gt=(int)Math.floor(top);
        ScreenRectangle geometryBounds=new ScreenRectangle(gl,gt,Math.max(1,(int)Math.ceil(right)-gl),Math.max(1,(int)Math.ceil(bottom)-gt));
        scissor=GuiMeshState.scissor(pose,clip,screenWidth,screenHeight);
        ScreenRectangle screenRect=new ScreenRectangle(0,0,Math.max(1,screenWidth),Math.max(1,screenHeight));
        ScreenRectangle effectiveClip=scissor==null?screenRect:scissor;
        bounds=geometryBounds.intersection(effectiveClip);
    }
    public boolean visible(){return bounds!=null && GuiMeshState.visible(bounds) && GuiMeshState.visible(scissor);}
    @Override public RenderPipeline pipeline(){return analytic?ROUNDED:RenderPipelines.GUI_TEXTURED;}
    @Override public TextureSetup textureSetup(){return textures;}
    @Override public ScreenRectangle scissorArea(){return scissor;}
    @Override public ScreenRectangle bounds(){return bounds;}
    private void put(VertexConsumer out,int index,float z){out.addVertex(v[index],v[index+1],z).setUv(v[index+2],v[index+3]).setColor(v[index+4],v[index+5],v[index+6],v[index+7]);}
    @Override public void buildVertices(VertexConsumer out@@BUILD_DEPTH_ARGUMENT@@){for(int i=0;i<v.length;i+=24){put(out,i,@@BUILD_DEPTH@@);put(out,i+16,@@BUILD_DEPTH@@);put(out,i+8,@@BUILD_DEPTH@@);put(out,i+8,@@BUILD_DEPTH@@);}}
}
