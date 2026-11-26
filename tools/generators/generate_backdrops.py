#!/usr/bin/env python3
"""Typed GL/native scene filtering, sharing one pass plan and one normalized kernel."""
import json
from pathlib import Path
import sys
_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

from generate_backdrop_assets import FUNCTION

ROOT = Path(__file__).resolve().parents[2]

def source(version):
    key = tuple(map(int, version.split('.')))
    if key < (1, 21, 6):
        result = (ROOT / 'modules/platform/src/templates/backends/LegacyBackdropBackend.java.tpl').read_text()
        imports = ''
        framebuffer = 'target.frameBufferId'
        if key == (1, 21, 5):
            imports = 'import com.mojang.blaze3d.opengl.GlDevice;\nimport com.mojang.blaze3d.opengl.GlTexture;\nimport com.mojang.blaze3d.systems.RenderSystem;'
            framebuffer = '((GlTexture)target.getColorTexture()).getFbo(((GlDevice)RenderSystem.getDevice()).directStateAccess(),target.getDepthTexture())'
        return result.replace('@@SOURCE_IMPORTS@@', imports).replace('@@SOURCE_FBO@@', framebuffer).replace('@@KERNEL@@', json.dumps(FUNCTION))
    pearl = key >= (26, 3)
    layouts = key >= (26, 2)
    color_state = key >= (26, 1)
    sampler_object = key >= (1, 21, 11)
    needs_vertex = key < (1, 21, 11)
    pipeline_pkg = 'com.mojang.renderpearl.api.pipeline' if pearl else 'com.mojang.blaze3d.pipeline'
    tex_pkg = 'com.mojang.renderpearl.api.textures' if pearl else 'com.mojang.blaze3d.textures'
    buffer_pkg = 'com.mojang.renderpearl.api.buffers' if pearl else 'com.mojang.blaze3d.buffers'
    commands_pkg = 'com.mojang.renderpearl.api.commands' if pearl else 'com.mojang.blaze3d.systems'
    uniform_pkg = pipeline_pkg if pearl else 'com.mojang.blaze3d.shaders'
    suffix = 'pearl' if pearl else ('vulkan' if layouts else 'ubo')
    imports = f'''import {pipeline_pkg}.RenderPipeline;
import {tex_pkg}.GpuTexture;
import {tex_pkg}.GpuTextureView;
import {buffer_pkg}.GpuBuffer;
import {commands_pkg}.RenderPass;
import {commands_pkg}.CommandEncoder;
import {uniform_pkg}.UniformType;
import {tex_pkg}.FilterMode;'''
    if not sampler_object:
        imports += f'\nimport {tex_pkg}.AddressMode;'
    if color_state:
        imports += f'\nimport {pipeline_pkg}.ColorTargetState;'
    else:
        imports += '\nimport com.mojang.blaze3d.platform.DepthTestFunction;'
    if layouts:
        imports += f'\nimport {pipeline_pkg}.BindGroupLayout;\nimport ' + (pipeline_pkg if pearl else 'com.mojang.blaze3d') + '.PrimitiveTopology;'
        binding = 'BindGroupLayout.builder().withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)' if pearl else 'BindGroupLayout.builder().withSampler("InSampler")'
        builder = f'.withBindGroupLayout({binding}.withUniform("NightBlurConfig", UniformType.UNIFORM_BUFFER).build())'
    else:
        imports += '\nimport com.mojang.blaze3d.vertex.DefaultVertexFormat;\nimport com.mojang.blaze3d.vertex.VertexFormat;'
        builder = '.withSampler("InSampler").withUniform("NightBlurConfig", UniformType.UNIFORM_BUFFER)'
    builder += '.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty())' if color_state else '.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withoutBlend()'
    builder += '.withCull(false)'
    builder += '.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)' if layouts else '.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)'
    target = 'mc.gameRenderer.mainRenderTarget()' if layouts else 'mc.getMainRenderTarget()'
    no_screen = '(mc.gui==null||mc.gui.screen()==null)' if layouts else '(mc.screen==null)'
    texture_settings = '' if sampler_object else 'texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);texture.setTextureFilter(FilterMode.LINEAR,false);'
    pass_create = 'encoder.createRenderPass(() -> "NightClient backdrop",target.view,Optional.empty())' if layouts else 'encoder.createRenderPass(() -> "NightClient backdrop",target.view,OptionalInt.empty())'
    pipeline_set = 'pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));' if pearl else 'pass.setPipeline(pipeline);'
    sampler = 'pass.setUniform("InSampler",source,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));' if pearl else ('pass.bindTexture("InSampler",source,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));' if sampler_object else 'pass.bindSampler("InSampler",source);')
    draw = 'pass.draw(3,1,0,0);' if layouts else 'pass.draw(0,3);'
    vertex_create = 'if(dummyVertex==null)dummyVertex=RenderSystem.getDevice().createBuffer(() -> "NightClient backdrop vertex binding",GpuBuffer.USAGE_VERTEX,64);' if needs_vertex else ''
    vertex_decl = 'private static GpuBuffer dummyVertex;' if needs_vertex else ''
    vertex_bind = 'pass.setVertexBuffer(0,dummyVertex);' if needs_vertex else ''
    vertex_close = 'if(dummyVertex!=null){dummyVertex.close();dummyVertex=null;}' if needs_vertex else ''
    top_down = 'false'
    prewarm_body = ('RenderSystem.getCompiledPipeline(pipeline());' if pearl else
                    'RenderSystem.getDevice().precompilePipeline(pipeline());')
    pipeline_method = '''    private static RenderPipeline pipeline(){{if(pipeline==null)pipeline=RenderPipeline.builder().withLocation(GameBridge.identifier("nightclient:pipeline/backdrop"))
            .withVertexShader(GameBridge.identifier("nightclient:core/fullscreen_{suffix}"))
            .withFragmentShader(GameBridge.identifier("nightclient:core/backdrop_{suffix}")){builder}.build();return pipeline;}}
    public static void prewarm(){{{prewarm_body}}}
'''.format(suffix=suffix,builder=builder,prewarm_body=prewarm_body)
    return f'''package dev.nightclient.platform;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
{imports}

/** Extraction allocates stable views; before-GUI execution fills them from a clean scene. */
public final class BackdropBackend {{
    public static volatile int resourceEpoch;
    private static RenderPipeline pipeline;
    private static long frame,captured=-1;
    private static volatile boolean invalidated;
    private static final List<Layer> layers=new ArrayList<Layer>();
    private static final ByteBuffer uniformData=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    {vertex_decl}
    private BackdropBackend() {{}}
{pipeline_method}    public static boolean topDown(){{return {top_down};}}
    public static void resourceReload(){{resourceEpoch++;invalidated=true;pipeline=null;}}
    public static void beginFrame(){{frame++;captured=-1;if(invalidated){{close();invalidated=false;}}else if(layers.size()>2){{for(int i=layers.size()-1;i>=0&&layers.size()>2;i--){{Layer layer=layers.get(i);if(layer.requested>=0&&frame-layer.requested>120){{layer.close();layers.remove(i);}}}}}}}}
    public static void beforeCanvas(){{}}
    public static GpuTextureView texture(float logicalRadius,int quality){{
        Minecraft mc=Minecraft.getInstance();
        RenderTarget target={target};GpuTexture source=target.getColorTexture();
        if(source==null||target.width<=0||target.height<=0)return null;
        float rawRadius=Math.max(0,logicalRadius*(float)mc.getWindow().getGuiScale());
        float radius=rawRadius<=0f?0f:Math.max(1f,(float)Math.round(rawRadius));
        boolean fast=OptimizationBridge.snapshotActive&&{no_screen};
        quality=fast?Math.min(2,BlurPlan.quality(quality)):BlurPlan.quality(quality);
        int count=fast?BlurPlan.fastLevels(radius,quality):BlurPlan.levels(radius,quality);if(count==0)return null;
        Layer selected=null;
        for(Layer layer:layers)if(layer.fast==fast&&layer.quality==quality&&Float.compare(layer.radius,radius)==0){{selected=layer;break;}}
        if(selected==null&&fast){{for(Layer layer:layers)if(layer.fast&&layer.requested==frame&&radius>=layer.radius*0.65f&&radius<=layer.radius*1.55f){{selected=layer;break;}}}}
        if(selected==null){{
            for(Layer layer:layers)if(layer.requested!=frame&&layer.fast==fast&&BlurPlan.fullResolution(layer.quality)==BlurPlan.fullResolution(quality)){{selected=layer;break;}}
            if(selected==null&&layers.size()<2){{selected=new Layer();layers.add(selected);}}
            if(selected==null){{for(Layer layer:layers)if(layer.requested!=frame){{selected=layer;break;}}}}
            if(selected==null){{selected=new Layer();layers.add(selected);}}
            if(selected.fast!=fast||BlurPlan.fullResolution(selected.quality)!=BlurPlan.fullResolution(quality))selected.close();
            selected.fast=fast;selected.radius=radius;selected.quality=quality;
        }}
        selected.requested=frame;
        if(selected.width!=target.width||selected.height!=target.height||selected.format!=source.getFormat()){{selected.close();selected.width=target.width;selected.height=target.height;selected.format=source.getFormat();}}
        int activeCount=selected.fast?BlurPlan.fastLevels(selected.radius,selected.quality):BlurPlan.levels(selected.radius,selected.quality);
        int activePasses=selected.fast?BlurPlan.fastPasses(selected.radius,selected.quality):BlurPlan.passes(selected.radius,selected.quality);
        while(selected.images.size()<activeCount){{int i=selected.images.size();int w=selected.fast?BlurPlan.fastSize(target.width,i):BlurPlan.size(target.width,i,selected.quality);int h=selected.fast?BlurPlan.fastSize(target.height,i):BlurPlan.size(target.height,i,selected.quality);selected.images.add(image("NightClient blur level "+i,w,h,source,GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_TEXTURE_BINDING));}}
        while(selected.passes.size()<activePasses)selected.passes.add(new Parameters());
        return selected.images.get(0).view;
    }}
    public static void capture(){{
        if(captured==frame)return;
        boolean requested=false;for(Layer layer:layers)if(layer.requested==frame){{requested=true;break;}}if(!requested)return;
        Minecraft mc=Minecraft.getInstance();
        RenderTarget target={target};GpuTexture source=target.getColorTexture();GpuTextureView sourceView=target.getColorTextureView();if(source==null||sourceView==null||target.width<=0||target.height<=0)return;
        pipeline();
        {vertex_create}
        CommandEncoder encoder=RenderSystem.getDevice().createCommandEncoder();
        for(Layer layer:layers)if(layer.requested==frame){{
            int count=layer.fast?BlurPlan.fastLevels(layer.radius,layer.quality):BlurPlan.levels(layer.radius,layer.quality);
            int parameter=0,samples=layer.fast?1:BlurPlan.samples(layer.quality);
            float offset=layer.fast?BlurPlan.fastOffset(layer.radius,count):BlurPlan.offset(layer.radius,count,layer.quality);
            GpuTextureView inputView=sourceView;int inputWidth=target.width,inputHeight=target.height;
            int firstDown=layer.fast?0:BlurPlan.firstDownLevel(layer.quality);
            for(int i=firstDown;i<count;i++){{Image output=layer.images.get(i);float stepOffset=(layer.fast&&i==0)?Math.max(1.0f,offset):offset;pass(encoder,inputView,inputWidth,inputHeight,output,stepOffset/inputWidth,stepOffset/inputHeight,false,samples,layer.passes.get(parameter++));inputView=output.view;inputWidth=output.width;inputHeight=output.height;}}
            for(int i=count-2;i>=0;i--){{Image output=layer.images.get(i);pass(encoder,inputView,inputWidth,inputHeight,output,offset/inputWidth,offset/inputHeight,true,samples,layer.passes.get(parameter++));inputView=output.view;inputWidth=output.width;inputHeight=output.height;}}
        }}
        captured=frame;
    }}
    private static Image image(String label,int w,int h,GpuTexture source,int usage){{GpuTexture texture=RenderSystem.getDevice().createTexture(label,usage,source.getFormat(),w,h,1,1);{texture_settings}return new Image(texture,RenderSystem.getDevice().createTextureView(texture),w,h);}}
    private static void pass(CommandEncoder encoder,GpuTextureView source,int sourceWidth,int sourceHeight,Image target,float dx,float dy,boolean up,int samples,Parameters parameters){{
        if(parameters.buffer==null)parameters.buffer=RenderSystem.getDevice().createBuffer(() -> "NightClient blur parameters",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,16);
        if(parameters.dx!=dx||parameters.dy!=dy||parameters.up!=up||parameters.samples!=samples){{uniformData.clear();uniformData.putFloat(dx).putFloat(dy).putFloat(up?1:0).putFloat(samples).flip();encoder.writeToBuffer(parameters.buffer.slice(),uniformData);parameters.dx=dx;parameters.dy=dy;parameters.up=up;parameters.samples=samples;}}
        try(RenderPass pass={pass_create}){{{pipeline_set}{sampler}pass.setUniform("NightBlurConfig",parameters.buffer);{vertex_bind}{draw}}}
    }}
    private static final class Parameters{{GpuBuffer buffer;float dx=Float.NaN,dy=Float.NaN;boolean up;int samples;void close(){{if(buffer!=null)buffer.close();buffer=null;dx=dy=Float.NaN;samples=0;}}}}
    private static final class Image{{final GpuTexture texture;final GpuTextureView view;final int width,height;Image(GpuTexture t,GpuTextureView v,int w,int h){{texture=t;view=v;width=w;height=h;}}void close(){{view.close();texture.close();}}}}
    private static final class Layer{{float radius;int quality,width,height;boolean fast;Object format;long requested=-1;final List<Image> images=new ArrayList<Image>();final List<Parameters> passes=new ArrayList<Parameters>();void close(){{for(Image image:images)image.close();images.clear();for(Parameters parameters:passes)parameters.close();passes.clear();width=height=0;format=null;}}}}
    public static void close(){{for(Layer layer:layers)layer.close();layers.clear();{vertex_close}pipeline=null;captured=-1;}}
}}
'''
