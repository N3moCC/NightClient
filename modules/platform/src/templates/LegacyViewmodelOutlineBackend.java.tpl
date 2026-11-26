package dev.nightclient.platform;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
@@SOURCE_IMPORTS@@

public final class ViewmodelOutlineBackend {
    private static int main,outline,width,height,program,vao,fbo,pointer,radius;
    private static boolean ready;
    public static long renderedFrames;
    private ViewmodelOutlineBackend(){}
    public static void prewarm(){if(program!=0)return;LegacyUiRenderer.State previous=new LegacyUiRenderer.State();try{initialize();}finally{previous.restore();}}
    public static void captureMain(RenderTarget target){
        ready=false;LegacyUiRenderer.State previous=new LegacyUiRenderer.State();
        try{
            if(program==0)initialize();
            if(width!=target.width||height!=target.height){imagesClose();width=target.width;height=target.height;main=image(width,height);outline=image(width,height);}
            GL13.glActiveTexture(GL13.GL_TEXTURE0);if(previous.samplerSupported)GL33.glBindSampler(0,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,main);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,@@SOURCE_FBO@@);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);
        }finally{previous.restore();}
    }
    public static void finish(RenderTarget target,float mouseU,float mouseV){
        if(main==0)return;LegacyUiRenderer.State previous=new LegacyUiRenderer.State();int offTexture=@@SOURCE_TEXTURE@@,minFilter=0,magFilter=0,wrapS=0,wrapT=0;boolean offSamplingSaved=false;
        try{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,outline,0);
            GL11.glViewport(0,0,width,height);GL20.glUseProgram(program);GL30.glBindVertexArray(vao);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);if(previous.alphaTestSupported)GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glColorMask(true,true,true,true);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,main);if(previous.samplerSupported)GL33.glBindSampler(0,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,offTexture);if(previous.samplerSupported)GL33.glBindSampler(1,0);
            minFilter=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);magFilter=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);
            wrapS=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S);wrapT=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T);offSamplingSaved=true;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL13.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL13.GL_CLAMP_TO_EDGE);
            GL20.glUniform2f(pointer,mouseU,mouseV);GL20.glUniform1f(radius,1.6f*(float)Minecraft.getInstance().getWindow().getGuiScale());GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);ready=true;renderedFrames++;
        }finally{try{if(offSamplingSaved){GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,offTexture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,minFilter);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,magFilter);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,wrapS);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,wrapT);}}finally{previous.restore();}}
    }
    public static int texture(String hand){return ready&&"hover".equals(hand)?outline:0;}
    public static void hide(){ready=false;}
    private static int image(int width,int height){
        int id=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);LegacyUiRenderer.PixelUnpackState unpack=new LegacyUiRenderer.PixelUnpackState();
        try{GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);}catch(RuntimeException e){GL11.glDeleteTextures(id);throw e;}finally{unpack.restore();}
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL13.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL13.GL_CLAMP_TO_EDGE);return id;
    }
    private static void initialize(){
        int v=compile(GL20.GL_VERTEX_SHADER,"#version 150\nout vec2 texCoord;void main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=uv;gl_Position=vec4(uv*2.0-1.0,0,1);}"),f=0;
        try{f=compile(GL20.GL_FRAGMENT_SHADER,"#version 150\nuniform sampler2D MainSampler;uniform sampler2D OffSampler;uniform vec2 MouseUV;uniform float RadiusPixels;in vec2 texCoord;out vec4 fragColor;\n"+@@KERNEL@@+"void main(){fragColor=vec4(1,1,1,ncHandOutline(texCoord,MouseUV,RadiusPixels));}");
            int next=GL20.glCreateProgram();GL20.glAttachShader(next,v);GL20.glAttachShader(next,f);GL20.glLinkProgram(next);
            if(GL20.glGetProgrami(next,GL20.GL_LINK_STATUS)==0){String message=GL20.glGetProgramInfoLog(next);GL20.glDeleteProgram(next);throw new IllegalStateException("NightClient hand outline link: "+message);}program=next;
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"MainSampler"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"OffSampler"),1);pointer=GL20.glGetUniformLocation(program,"MouseUV");radius=GL20.glGetUniformLocation(program,"RadiusPixels");vao=GL30.glGenVertexArrays();fbo=GL30.glGenFramebuffers();
        }finally{GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}
    }
    private static int compile(int kind,String source){int shader=GL20.glCreateShader(kind);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException("NightClient hand outline shader: "+error);}return shader;}
    private static void imagesClose(){if(main!=0)GL11.glDeleteTextures(main);if(outline!=0)GL11.glDeleteTextures(outline);main=outline=0;ready=false;}
    public static void close(){imagesClose();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);program=vao=fbo=width=height=0;}
}
