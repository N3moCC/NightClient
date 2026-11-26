package dev.nightclient.platform;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalInt;
@@IMPORTS@@

public final class ViewmodelOutlineBackend {
    private static Image main,outline;
    private static RenderPipeline pipeline;
    private static GpuBuffer parameters;
    private static boolean ready;
    public static long renderedFrames;
    private static final ByteBuffer data=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    @@VERTEX_DECL@@
    private ViewmodelOutlineBackend(){}
    private static RenderPipeline pipeline(){
        if(pipeline==null)pipeline=RenderPipeline.builder().withLocation(GameBridge.identifier("nightclient:pipeline/viewmodel_outline"))
            .withVertexShader(GameBridge.identifier("nightclient:core/fullscreen_@@SUFFIX@@"))
            .withFragmentShader(GameBridge.identifier("nightclient:core/viewmodel_outline_@@SUFFIX@@"))@@PIPELINE_BUILDER@@.build();
        return pipeline;
    }
    public static void prewarm(){@@PREWARM@@}
    public static void captureMain(RenderTarget target){
        ready=false;GpuTexture source=target.getColorTexture();
        if(main==null||main.width!=target.width||main.height!=target.height||main.texture.getFormat()!=source.getFormat()){
            imagesClose();main=image("NightClient hand outline main",target.width,target.height,source,GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING);
            outline=image("NightClient hand outline output",target.width,target.height,source,GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_COPY_SRC);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source,main.texture,0,0,0,0,0,target.width,target.height);
    }
    public static void finish(RenderTarget target,float mouseU,float mouseV){
        if(main==null)return;
        @@MASK_SAMPLER_SETTINGS@@
        pipeline();
        if(parameters==null)parameters=RenderSystem.getDevice().createBuffer(()->"NightClient outline pointer",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,16);
        @@VERTEX_CREATE@@
        CommandEncoder encoder=RenderSystem.getDevice().createCommandEncoder();
        data.clear();data.putFloat(mouseU).putFloat(mouseV).putFloat(1.6f*(float)Minecraft.getInstance().getWindow().getGuiScale()).putFloat(0).flip();
        encoder.writeToBuffer(parameters.slice(),data);
        try(RenderPass pass=@@PASS_CREATE@@){@@SET_PIPELINE@@@@SAMPLERS@@pass.setUniform("NightOutlineConfig",parameters);@@VERTEX_BIND@@@@DRAW@@}
        ready=true;renderedFrames++;
    }
    public static GpuTextureView texture(String hand){return ready&&"hover".equals(hand)?outline.view:null;}
    public static void hide(){ready=false;}
    private static Image image(String name,int width,int height,GpuTexture source,int usage){
        GpuTexture texture=RenderSystem.getDevice().createTexture(name,usage,source.getFormat(),width,height,1,1);@@TEXTURE_SETTINGS@@
        return new Image(texture,RenderSystem.getDevice().createTextureView(texture),width,height);
    }
    private static final class Image {final GpuTexture texture;final GpuTextureView view;final int width,height;Image(GpuTexture t,GpuTextureView v,int w,int h){texture=t;view=v;width=w;height=h;}void close(){view.close();texture.close();}}
    private static void imagesClose(){ready=false;if(main!=null)main.close();if(outline!=null)outline.close();main=outline=null;}
    public static void close(){imagesClose();if(parameters!=null)parameters.close();parameters=null;@@VERTEX_CLOSE@@pipeline=null;}
}
