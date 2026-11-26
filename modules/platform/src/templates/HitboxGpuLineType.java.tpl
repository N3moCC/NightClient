package dev.nightclient.platform;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import dev.nightclient.platform.mixin.HitboxPipelineAccessor;
import java.util.OptionalInt;
import java.util.OptionalDouble;

public final class HitboxGpuLineType extends RenderType {
    private final RenderPipeline pipeline;
    private final float width;
    public HitboxGpuLineType(float width){
        super("nightclient_hitbox",1536,false,false,()->{},()->{});this.width=width;
        pipeline=RenderPipeline.builder(HitboxPipelineAccessor.nightclient$quads())
            .withLocation(GameBridge.identifier("nightclient:pipeline/hitbox_"+Math.round(width*4)+"_depth"))
            .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false).withCull(false).build();
    }
    public RenderTarget getRenderTarget(){return Minecraft.getInstance().getMainRenderTarget();}
    public RenderPipeline getRenderPipeline(){return pipeline;}
    public RenderPipeline pipeline(){return pipeline;}
    public VertexFormat format(){return pipeline.getVertexFormat();}
    public VertexFormat.Mode mode(){return pipeline.getVertexFormatMode();}
    public void draw(MeshData mesh){
        @@BOX_DRAW_PREPARE@@
        try(MeshData owned=mesh){
            GpuBuffer vertices=format().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            GpuBuffer indices;VertexFormat.IndexType type;
            if(mesh.indexBuffer()==null){RenderSystem.AutoStorageIndexBuffer sequential=RenderSystem.getSequentialBuffer(mesh.drawState().mode());indices=sequential.getBuffer(mesh.drawState().indexCount());type=sequential.type();}
            else{indices=format().uploadImmediateIndexBuffer(mesh.indexBuffer());type=mesh.drawState().indexType();}
            RenderTarget target=getRenderTarget();
            try(RenderPass pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(@@BOX_PASS_ARGS@@)){
                pass.setPipeline(pipeline);@@BOX_DRAW_UNIFORMS@@
                pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indices,type);@@BOX_DRAW_INDEXED@@
            }
        }finally{@@BOX_DRAW_RESTORE@@}
    }
}
