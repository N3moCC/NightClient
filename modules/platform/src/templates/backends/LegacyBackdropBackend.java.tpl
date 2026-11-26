package dev.nightclient.platform;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
@@SOURCE_IMPORTS@@

public final class BackdropBackend {
    public static volatile int resourceEpoch;
    private static int base,width,height,program,vao,framebuffer,stepUniform,upUniform,samplesUniform;
    private static long frame,captured=-1;
    private static volatile boolean invalidated;
    private static final List<Layer> layers=new ArrayList<Layer>();
    private BackdropBackend() {}
    public static void prewarm(){if(program!=0)return;LegacyUiRenderer.State state=new LegacyUiRenderer.State();try{initialize();}finally{state.restore();}}
    public static void beginFrame(){frame++;captured=-1;if(invalidated){close();invalidated=false;}else if(layers.size()>2){for(int i=layers.size()-1;i>=0&&layers.size()>2;i--){Layer layer=layers.get(i);if(layer.used>=0&&frame-layer.used>120){for(int id:layer.images)GL11.glDeleteTextures(id);layer.images.clear();layers.remove(i);}}}}
    public static void resourceReload(){resourceEpoch++;invalidated=true;}
    public static void beforeCanvas(){if(captured!=frame && GameBridge.backdropNeeded())capture();}
    public static void capture(){
        if(captured==frame || !GameBridge.backdropNeeded())return;
        RenderTarget target=Minecraft.getInstance().getMainRenderTarget();
        if(target.width<=0||target.height<=0)return;
        LegacyUiRenderer.State state=new LegacyUiRenderer.State();
        try{
            if(program==0)initialize();
            if(width!=target.width||height!=target.height){destroyImages();width=target.width;height=target.height;base=texture(width,height);}
            GL13.glActiveTexture(GL13.GL_TEXTURE0);if(state.samplerSupported)GL33.glBindSampler(0,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,base);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,@@SOURCE_FBO@@);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);captured=frame;
        }finally{state.restore();}
    }
    public static int texture(float logicalRadius,int quality){
        beforeCanvas();if(captured!=frame)return 0;
        Minecraft mc=Minecraft.getInstance();
        float rawRadius=Math.max(0,logicalRadius*(float)mc.getWindow().getGuiScale());
        float radius=rawRadius<=0f?0f:Math.max(1f,(float)Math.round(rawRadius));
        boolean fast=OptimizationBridge.snapshotActive&&mc.screen==null;
        quality=fast?Math.min(2,BlurPlan.quality(quality)):BlurPlan.quality(quality);
        int initialCount=fast?BlurPlan.fastLevels(radius,quality):BlurPlan.levels(radius,quality);
        if(initialCount==0)return base;
        Layer selected=null;
        for(Layer layer:layers)if(layer.fast==fast&&layer.quality==quality&&Float.compare(layer.radius,radius)==0){selected=layer;break;}
        if(selected==null&&fast){for(Layer layer:layers)if(layer.fast&&layer.used==frame&&radius>=layer.radius*0.65f&&radius<=layer.radius*1.55f){selected=layer;break;}}
        if(selected==null){
            for(Layer layer:layers)if(layer.used!=frame&&layer.fast==fast&&BlurPlan.fullResolution(layer.quality)==BlurPlan.fullResolution(quality)){selected=layer;break;}
            if(selected==null&&layers.size()<2){selected=new Layer();layers.add(selected);}
            if(selected==null){for(Layer layer:layers)if(layer.used!=frame){selected=layer;break;}}
            if(selected==null){selected=new Layer();layers.add(selected);}
            if(selected.fast!=fast||BlurPlan.fullResolution(selected.quality)!=BlurPlan.fullResolution(quality)){for(int id:selected.images)GL11.glDeleteTextures(id);selected.images.clear();}
            selected.fast=fast;selected.radius=radius;selected.quality=quality;selected.processed=-1;
        }
        selected.used=frame;if(selected.processed==frame)return selected.images.get(0);
        LegacyUiRenderer.State state=new LegacyUiRenderer.State();
        try{
            int count=selected.fast?BlurPlan.fastLevels(selected.radius,selected.quality):BlurPlan.levels(selected.radius,selected.quality);if(count==0)return base;
            for(int i=selected.images.size();i<count;i++)selected.images.add(texture(selected.fast?BlurPlan.fastSize(width,i):BlurPlan.size(width,i,selected.quality),selected.fast?BlurPlan.fastSize(height,i):BlurPlan.size(height,i,selected.quality)));
            GL13.glActiveTexture(GL13.GL_TEXTURE0);if(state.samplerSupported)GL33.glBindSampler(0,0);
            GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);if(state.alphaTestSupported)GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glColorMask(true,true,true,true);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            GL20.glUniform1f(samplesUniform,selected.fast?1:BlurPlan.samples(selected.quality));
            float offset=selected.fast?BlurPlan.fastOffset(selected.radius,count):BlurPlan.offset(selected.radius,count,selected.quality);int input=base,inWidth=width,inHeight=height;
            int firstDown=selected.fast?0:BlurPlan.firstDownLevel(selected.quality);
            for(int i=firstDown;i<count;i++){int outWidth=selected.fast?BlurPlan.fastSize(width,i):BlurPlan.size(width,i,selected.quality),outHeight=selected.fast?BlurPlan.fastSize(height,i):BlurPlan.size(height,i,selected.quality);float stepOffset=(selected.fast&&i==0)?Math.max(1.0f,offset):offset;pass(input,selected.images.get(i),outWidth,outHeight,stepOffset/inWidth,stepOffset/inHeight,false);input=selected.images.get(i);inWidth=outWidth;inHeight=outHeight;}
            for(int i=count-2;i>=0;i--){int outWidth=selected.fast?BlurPlan.fastSize(width,i):BlurPlan.size(width,i,selected.quality),outHeight=selected.fast?BlurPlan.fastSize(height,i):BlurPlan.size(height,i,selected.quality);pass(input,selected.images.get(i),outWidth,outHeight,offset/inWidth,offset/inHeight,true);input=selected.images.get(i);inWidth=outWidth;inHeight=outHeight;}
            selected.processed=frame;return selected.images.get(0);
        }finally{state.restore();}
    }
    private static final class Layer{float radius;int quality;boolean fast;long used=-1,processed=-1;final List<Integer> images=new ArrayList<Integer>();}
    private static void pass(int source,int target,int w,int h,float dx,float dy,boolean up){
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);GL11.glViewport(0,0,w,h);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);GL20.glUniform2f(stepUniform,dx,dy);GL20.glUniform1f(upUniform,up?1:0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    private static int texture(int w,int h){
        int id=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);LegacyUiRenderer.PixelUnpackState unpack=new LegacyUiRenderer.PixelUnpackState();
        try{GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);}catch(RuntimeException error){GL11.glDeleteTextures(id);throw error;}finally{unpack.restore();}
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL13.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL13.GL_CLAMP_TO_EDGE);return id;
    }
    private static void initialize(){
        String vertex="#version 150\nout vec2 texCoord;void main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=uv;gl_Position=vec4(uv*2.0-1.0,0,1);}";
        String fragment="#version 150\nuniform sampler2D InSampler;uniform vec2 BlurStep;uniform float Upsample;uniform float KernelSamples;in vec2 texCoord;out vec4 fragColor;\n"+@@KERNEL@@+"void main(){fragColor=vec4(ncKawase(InSampler,texCoord,BlurStep,Upsample,KernelSamples).rgb,1.0);}";
        int v=compile(GL20.GL_VERTEX_SHADER,vertex),f=0;
        try{f=compile(GL20.GL_FRAGMENT_SHADER,fragment);int next=GL20.glCreateProgram();GL20.glAttachShader(next,v);GL20.glAttachShader(next,f);GL20.glLinkProgram(next);if(GL20.glGetProgrami(next,GL20.GL_LINK_STATUS)==0){String error=GL20.glGetProgramInfoLog(next);GL20.glDeleteProgram(next);throw new IllegalStateException("NightClient backdrop link: "+error);}program=next;stepUniform=GL20.glGetUniformLocation(program,"BlurStep");upUniform=GL20.glGetUniformLocation(program,"Upsample");samplesUniform=GL20.glGetUniformLocation(program,"KernelSamples");GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"InSampler"),0);vao=GL30.glGenVertexArrays();framebuffer=GL30.glGenFramebuffers();}finally{GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}
    }
    private static int compile(int type,String source){int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException("NightClient backdrop shader: "+log);}return shader;}
    private static void destroyImages(){if(base!=0)GL11.glDeleteTextures(base);base=0;for(Layer layer:layers)for(int id:layer.images)GL11.glDeleteTextures(id);layers.clear();captured=-1;}
    public static void close(){destroyImages();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(framebuffer!=0)GL30.glDeleteFramebuffers(framebuffer);program=vao=framebuffer=width=height=0;}
}
