#!/usr/bin/env python3
"""Typed camera, actual-alpha viewmodel selection and animation bridges for every release."""
import argparse,json,re
from pathlib import Path
import sys
_TOOLS_DIR = Path(__file__).resolve().parents[1]
for _sub in ('', 'generators', 'matrix', 'checks', 'benchmarks', 'qa'):
    _p = str(_TOOLS_DIR / _sub if _sub else _TOOLS_DIR)
    if _p not in sys.path:
        sys.path.insert(0, _p)

from generate_viewmodel_outline import outline_source
ROOT=Path(__file__).resolve().parents[2]
TEMPLATES=ROOT/'modules/platform/src/templates'

def write(output,name,source,bridge=False):
 p=Path(output)/'dev/nightclient/platform'/('' if bridge else 'mixin')/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True)
 if not p.exists() or p.read_text()!=source:p.write_text(source)

def mixin(name,owner,body,imports=''):
 return 'package dev.nightclient.platform.mixin;\nimport dev.nightclient.platform.CameraFeatureBridge;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.*;\nimport org.spongepowered.asm.mixin.injection.callback.*;\n'+imports+'\n@Mixin('+owner+'.class) public abstract class '+name+' {\n'+body+'\n}\n'

def signature(args):return ','.join(t.replace('$','.')+' a'+str(i) for i,t in enumerate(args))
def calls(args):return ','.join('a'+str(i) for i in range(len(args)))

def generate_camera_features(version,output):
 profiles=json.loads((ROOT/'modules/platform/compatibility/camera-features.json').read_text());p=profiles[version];v=tuple(map(int,version.split('.')))
 bridge=json.loads((ROOT/'modules/platform/compatibility/bridges.json').read_text())[version]
 native=v>=(1,21,5);views=v>=(1,21,6);sdl=version=='26.3';camera_native=v>=(26,1);deferred=(1,21,9)<=v<=(26,1)
 tex='com.mojang.renderpearl.api.textures' if sdl else 'com.mojang.blaze3d.textures';buf='com.mojang.renderpearl.api.buffers' if sdl else 'com.mojang.blaze3d.buffers'
 category='KeyMapping.Category.register(GameBridge.identifier("nightclient:camera"))' if bridge['keyMappingCategory']=='Category' else '"key.categories.nightclient"'
 values={'FREELOOK_KEY':'new KeyMapping("key.nightclient.freelook",InputConstants.KEY_LALT,'+category+')',
  'SCREEN':'mc.gui.screen()' if v>=(26,2) else 'mc.screen','HIDDEN':'mc.gui.hud.isHidden()' if v>=(26,2) else 'mc.options.hideGui',
  'MAIN_ARM_SIGN':'(Minecraft.getInstance().player.getMainArm()==HumanoidArm.RIGHT?1:-1)',
  'OFF_ARM_SIGN':'(Minecraft.getInstance().player.getMainArm()==HumanoidArm.RIGHT?-1:1)',
  'ROTATE':''.join('if('+n+'!=0)pose.mulPose(com.mojang.math.Axis.'+axis+'P.rotationDegrees('+n+'));' for n,axis in [('x','X'),('y','Y'),('z','Z')]),
  'MAIN_TARGET':'mc.gameRenderer.mainRenderTarget()' if v>=(26,2) else 'mc.getMainRenderTarget()',
  'IMPORTS':'','DEPTH_PICK':'','PROJECTION_IMPORT':'import dev.nightclient.platform.mixin.ViewmodelCachedProjectionAccessor;' if (1,21,6)<=v<(26,1) else ''}
 projection_setup=''
 if v>=(26,1):
  projection='((ViewmodelGameRendererAccessor)(Object)Minecraft.getInstance().gameRenderer).nightclient$hudProjection().getMatrix(new org.joml.Matrix4f())'
 elif v>=(1,21,6):
  projection_setup='ViewmodelCachedProjectionAccessor cachedProjection=(ViewmodelCachedProjectionAccessor)(Object)((ViewmodelGameRendererAccessor)(Object)Minecraft.getInstance().gameRenderer).nightclient$hudProjection();'
  projection='cachedProjection.nightclient$matrix(cachedProjection.nightclient$width(),cachedProjection.nightclient$height(),cachedProjection.nightclient$fov())'
 else:projection='RenderSystem.getProjectionMatrix()'
 modelview='RenderSystem.getModelViewStack()' if v>=(1,20,5) else 'RenderSystem.getModelViewMatrix()'
 values['PROJECT_HAND']=projection_setup+'new org.joml.Matrix4f('+projection+').mul('+modelview+').mul(pose.last().pose()).get(matrix);'
 cap=p['capture'];args=signature(cap['args']);argnames=calls(cap['args'])
 accessor='package dev.nightclient.platform.mixin;\nimport net.minecraft.client.renderer.GameRenderer;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.gen.Accessor;\nimport org.spongepowered.asm.mixin.gen.Invoker;\n@Mixin(GameRenderer.class) public interface ViewmodelGameRendererAccessor {\n @Invoker("renderItemInHand") void nightclient$replay('+args+');\n'
 if v<=(26,1):accessor+=' @Accessor("renderBuffers") net.minecraft.client.renderer.RenderBuffers nightclient$buffers();\n'
 if deferred:accessor+=' @Accessor("featureRenderDispatcher") net.minecraft.client.renderer.feature.FeatureRenderDispatcher nightclient$features();\n'
 if v>=(26,1):accessor+=' @Accessor("hudProjection") net.minecraft.client.renderer.Projection nightclient$hudProjection();\n'
 elif v>=(1,21,6):accessor+=' @Accessor("hud3dProjectionMatrixBuffer") net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer nightclient$hudProjection();\n'
 accessor+='}\n';write(output,'ViewmodelGameRendererAccessor',accessor)
 if (1,21,6)<=v<(26,1):
  write(output,'ViewmodelCachedProjectionAccessor','''package dev.nightclient.platform.mixin;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CachedPerspectiveProjectionMatrixBuffer.class) public interface ViewmodelCachedProjectionAccessor {
 @Accessor("width") int nightclient$width();
 @Accessor("height") int nightclient$height();
 @Accessor("fov") float nightclient$fov();
 @Invoker("createProjectionMatrix") org.joml.Matrix4f nightclient$matrix(int width,int height,float fov);
}
''')
 else:
  stale=Path(output)/'dev/nightclient/platform/mixin/ViewmodelCachedProjectionAccessor.java'
  if stale.exists():stale.unlink()
 target='package dev.nightclient.platform.mixin;\nimport com.mojang.blaze3d.pipeline.RenderTarget;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.gen.Accessor;\n@Mixin(RenderTarget.class) public interface ViewmodelTargetAccessor {\n'
 if native:
  for name,t in [('colorTexture',tex+'.GpuTexture'),('depthTexture',tex+'.GpuTexture')]+([('colorTextureView',tex+'.GpuTextureView'),('depthTextureView',tex+'.GpuTextureView')] if views else []):
   target+=' @Accessor("'+name+'") void nightclient$'+name+'('+t+' value);\n'
 else:
  for name in ['colorTextureId','depthBufferId']:
   target+=' @Accessor("'+name+'") int nightclient$'+name+'();\n @Accessor("'+name+'") void nightclient$'+name+'(int value);\n'
 target+='}\n';write(output,'ViewmodelTargetAccessor',target)
 if native:
  values['IMPORTS']='import '+tex+'.GpuTexture;\n'+('import '+tex+'.GpuTextureView;\n' if views else '')+'import '+buf+'.GpuBuffer;\n'
  values['TARGET_CREATE']='new com.mojang.blaze3d.pipeline.TextureTarget("NightClient actual hand silhouette",main.width,main.height,'+('main.getColorTexture().getFormat(),main.getDepthTexture()==null?null:main.getDepthTexture().getFormat()' if sdl else 'true'+(',main.getColorTexture().getFormat()' if v>=(26,2) else ''))+')'
  values['BEFORE_CAPTURE']=('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$features().renderAllFeatures();' if deferred else '')+('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();' if v<=(26,1) else '')+'''
        final ViewmodelTargetAccessor attachments=(ViewmodelTargetAccessor)(Object)main;
        final GpuTexture originalColor=main.getColorTexture(),originalDepth=main.getDepthTexture();
'''+('final GpuTextureView originalColorView=main.getColorTextureView(),originalDepthView=main.getDepthTextureView();' if views else '')+('final GpuTextureView previousColorOverride=RenderSystem.outputColorTextureOverride,previousDepthOverride=RenderSystem.outputDepthTextureOverride;' if views and not sdl else '')
  values['TARGET_BIND']='attachments.nightclient$colorTexture(maskTarget.getColorTexture());attachments.nightclient$depthTexture(maskTarget.getDepthTexture());'+('attachments.nightclient$colorTextureView(maskTarget.getColorTextureView());attachments.nightclient$depthTextureView(maskTarget.getDepthTextureView());' if views else '')+('RenderSystem.outputColorTextureOverride=maskTarget.getColorTextureView();RenderSystem.outputDepthTextureOverride=maskTarget.getDepthTextureView();' if views and not sdl else '')
  values['RESTORE_TARGET']='attachments.nightclient$colorTexture(originalColor);attachments.nightclient$depthTexture(originalDepth);'+('attachments.nightclient$colorTextureView(originalColorView);attachments.nightclient$depthTextureView(originalDepthView);' if views else '')+('RenderSystem.outputColorTextureOverride=previousColorOverride;RenderSystem.outputDepthTextureOverride=previousDepthOverride;' if views and not sdl else '')
  values['CLEAR_TARGET']='if(hand==0)RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(maskTarget.getColorTexture(),'+('new org.joml.Vector4f(0)' if v>=(26,2) else '0')+',maskTarget.getDepthTexture(),'+('RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE' if v>=(26,2) else '1')+');else RenderSystem.getDevice().createCommandEncoder().clearColorTexture(maskTarget.getColorTexture(),'+('new org.joml.Vector4f(0)' if v>=(26,2) else '0')+');'
  values['FLUSH_HAND']=('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$features().renderAllFeatures();' if deferred else '')+('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();' if v<=(26,1) else '')
  mapped='encoder.readBuffer(buffer)' if version=='1.21.5' else ('buffer.map(true,false)' if v>=(26,2) else 'encoder.mapBuffer(buffer,true,false)')
  values['READ_MASK']='''private static void readMask(RenderTarget target,int logicalWidth,int logicalHeight,java.util.function.Consumer<PickMask> consumer){
        final int width=target.width,height=target.height;
        final GpuBuffer buffer=RenderSystem.getDevice().createBuffer(()->"NightClient hand-alpha readback",@@BUFFER_USAGE@@,width*height*4);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(target.getColorTexture(),buffer,0,()->{
            try(var mapped='''+mapped+'''){consumer.accept(PickMask.fromRgba(mapped.data(),width,height,logicalWidth,logicalHeight,true));}
            finally{buffer.close();}
        },0);
    }'''
  values['READ_MASK']=values['READ_MASK'].replace('@@BUFFER_USAGE@@','com.mojang.blaze3d.buffers.BufferType.PIXEL_PACK,com.mojang.blaze3d.buffers.BufferUsage.STREAM_READ' if version=='1.21.5' else 'GpuBuffer.USAGE_MAP_READ|GpuBuffer.USAGE_COPY_DST')
  if sdl:
   values['ROTATE']=''.join('if('+n+'!=0)pose.rotateDegrees(com.mojang.math.Axis.'+axis+'P,'+n+');' for n,axis in [('x','X'),('y','Y'),('z','Z')])
   values['DEPTH_PICK']='public static GpuTextureView depthForPick(GpuTextureView original){return capturing&&maskTarget!=null?maskTarget.getDepthTextureView():original;}'
   argnames='a0,a1,CameraFeatureBridge.depthForPick(a2)'
 else:
  values['IMPORTS']='import org.lwjgl.opengl.GL11;\nimport org.lwjgl.opengl.GL21;\nimport org.lwjgl.opengl.GL30;\nimport org.lwjgl.BufferUtils;\nimport java.nio.ByteBuffer;\nimport java.nio.IntBuffer;\nimport java.nio.FloatBuffer;\n'
  values['TARGET_CREATE']='new com.mojang.blaze3d.pipeline.TextureTarget(main.width,main.height,true'+(',false' if v<(1,21,2) else '')+')'
  values['BEFORE_CAPTURE']='''((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();
        final int drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        legacyViewportBuf.clear();GL11.glGetIntegerv(GL11.GL_VIEWPORT,legacyViewportBuf);
        legacyClearBuf.clear();GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,legacyClearBuf);
        final boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        legacyScissorBuf.clear();GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,legacyScissorBuf);
        legacyColorMaskBuf.clear();GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,legacyColorMaskBuf);
        final double depthClear=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        final ViewmodelTargetAccessor attachments=(ViewmodelTargetAccessor)(Object)main;
        final int originalFbo=main.frameBufferId,originalColor=attachments.nightclient$colorTextureId(),originalDepth=attachments.nightclient$depthBufferId();'''
  values['CLEAR_TARGET']='''maskTarget.bindWrite(true);RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);RenderSystem.clearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|(hand==0?GL11.GL_DEPTH_BUFFER_BIT:0));'''
  values['TARGET_BIND']='main.frameBufferId=maskTarget.frameBufferId;attachments.nightclient$colorTextureId(maskTarget.getColorTextureId());attachments.nightclient$depthBufferId(maskTarget.getDepthTextureId());'
  values['FLUSH_HAND']='((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();'
  values['RESTORE_TARGET']='''main.frameBufferId=originalFbo;attachments.nightclient$colorTextureId(originalColor);attachments.nightclient$depthBufferId(originalDepth);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glViewport(legacyViewportBuf.get(0),legacyViewportBuf.get(1),legacyViewportBuf.get(2),legacyViewportBuf.get(3));
            RenderSystem.colorMask(legacyColorMaskBuf.get(0)!=0,legacyColorMaskBuf.get(1)!=0,legacyColorMaskBuf.get(2)!=0,legacyColorMaskBuf.get(3)!=0);RenderSystem.depthMask(depthMask);GL11.glClearDepth(depthClear);if(scissor)RenderSystem.enableScissor(legacyScissorBuf.get(0),legacyScissorBuf.get(1),legacyScissorBuf.get(2),legacyScissorBuf.get(3));else RenderSystem.disableScissor();RenderSystem.clearColor(legacyClearBuf.get(0),legacyClearBuf.get(1),legacyClearBuf.get(2),legacyClearBuf.get(3));'''
  values['READ_MASK']='''private static final IntBuffer legacyViewportBuf=BufferUtils.createIntBuffer(4),legacyScissorBuf=BufferUtils.createIntBuffer(4);
    private static final FloatBuffer legacyClearBuf=BufferUtils.createFloatBuffer(4);
    private static final ByteBuffer legacyColorMaskBuf=BufferUtils.createByteBuffer(4);
    private static ByteBuffer legacyPixelsBuf;
    private static void readMask(RenderTarget target,int logicalWidth,int logicalHeight,java.util.function.Consumer<PickMask> consumer){
        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int needed=target.width*target.height*4;
        if(legacyPixelsBuf==null||legacyPixelsBuf.capacity()<needed)legacyPixelsBuf=BufferUtils.createByteBuffer(needed);
        legacyPixelsBuf.clear();legacyPixelsBuf.limit(needed);
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GL30.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,target.width,target.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,legacyPixelsBuf);consumer.accept(PickMask.fromRgba(legacyPixelsBuf,target.width,target.height,logicalWidth,logicalHeight,true));}
        finally{GL30.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);}
    }'''
 source=(TEMPLATES/'CameraFeatureBridge.java.tpl').read_text()
 for k,value in values.items():source=source.replace('@@'+k+'@@',value)
 if re.search(r'@@[A-Z_]+@@',source):raise ValueError('Unresolved camera template '+version)
 write(output,'CameraFeatureBridge',source,True)
 write(output,'ViewmodelOutlineBackend',outline_source(version),True)
 write(output,'ViewmodelCaptureMixin',mixin('ViewmodelCaptureMixin','net.minecraft.client.renderer.GameRenderer',' @Inject(method="'+cap['selector']+'",at=@At("HEAD")) private void nightclient$capture('+args+',CallbackInfo ci){CameraFeatureBridge.capture((net.minecraft.client.renderer.GameRenderer)(Object)this,()->((ViewmodelGameRendererAccessor)(Object)this).nightclient$replay('+argnames+'));}'))
 hand=p['hand'];ha=hand['args'];sig=signature(ha);pose='a'+str(ha.index('com.mojang.blaze3d.vertex.PoseStack'));interaction='a'+str(ha.index('net.minecraft.world.InteractionHand'))
 body=' private boolean nightclient$transformed;\n @Inject(method="'+hand['selector']+'",at=@At("HEAD"),cancellable=true) private void nightclient$transform('+sig+',CallbackInfo ci){nightclient$transformed=false;if(CameraFeatureBridge.skipHand('+interaction+')){ci.cancel();return;}nightclient$transformed=CameraFeatureBridge.beginHand('+pose+','+interaction+');}\n @Inject(method="'+hand['selector']+'",at=@At("RETURN")) private void nightclient$restore('+sig+',CallbackInfo ci){CameraFeatureBridge.endHand('+pose+',nightclient$transformed);nightclient$transformed=false;}\n @ModifyVariable(method="'+hand['selector']+'",at=@At("HEAD"),argsOnly=true,ordinal=3) private float nightclient$equip(float value){return CameraFeatureBridge.equip(value);}\n @Inject(method="'+p['attack']['selector']+'",at=@At("HEAD"),cancellable=true) private void nightclient$swing(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.world.entity.HumanoidArm arm,float progress,CallbackInfo ci){if(CameraFeatureBridge.customSwing(pose,arm,progress))ci.cancel();}'
 write(output,'ViewmodelHandMixin',mixin('ViewmodelHandMixin',p['handOwner'],body))
 cam=p['camera'];sel=cam['selector'];body=' @Inject(method="'+sel+'",at=@At("HEAD")) private void nightclient$camera(CallbackInfo ci){CameraFeatureBridge.cameraFrame();}\n'
 for name,outname in [('getViewYRot','Yaw'),('getViewXRot','Pitch')]:
  body+=' @Redirect(method="'+sel+'",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;'+name+'(F)F")) private float nightclient$'+outname+'(net.minecraft.world.entity.Entity entity,float partial){return CameraFeatureBridge.camera'+outname+'(entity,entity.'+name+'(partial));}\n'
 write(output,'CameraMixin',mixin('CameraMixin','net.minecraft.client.Camera',body))
 sel=p['mouse']['selector'];write(output,'FreelookMouseMixin',mixin('FreelookMouseMixin','net.minecraft.client.MouseHandler',' @Redirect(method="'+sel+'",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")) private void nightclient$turn(net.minecraft.client.player.LocalPlayer player,double yaw,double pitch){if(!CameraFeatureBridge.interceptTurn(player,yaw,pitch))player.turn(yaw,pitch);}'))
 if camera_native:
  body=' @ModifyArgs(method="setupPerspective(FFFFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V")) private void nightclient$aspect(org.spongepowered.asm.mixin.injection.invoke.arg.Args args){float width=args.get(3),height=args.get(4);args.set(3,CameraFeatureBridge.aspect(width/height)*height);}'
  body+=' @ModifyArg(method="createProjectionMatrixForCulling",at=@At(value="INVOKE",target="Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"),index=1) private float nightclient$cullingAspect(float original){return CameraFeatureBridge.aspect(original);}'
  owner='net.minecraft.client.Camera'
 else:
  owner='net.minecraft.client.renderer.GameRenderer';target='Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;' if v<(1,20,5) else 'Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;'
  body=' @ModifyArg(method="getProjectionMatrix",at=@At(value="INVOKE",target="'+target+'"),index=1) private float nightclient$aspect(float original){return CameraFeatureBridge.aspect(original);}'
 write(output,'AspectRatioMixin',mixin('AspectRatioMixin',owner,body))
 duration='getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I' if sdl else 'getCurrentSwingDuration()I'
 write(output,'SwingDurationMixin',mixin('SwingDurationMixin','net.minecraft.world.entity.LivingEntity',' @Inject(method="'+duration+'",at=@At("RETURN"),cancellable=true) private void nightclient$duration(CallbackInfoReturnable<Integer> cir){cir.setReturnValue(CameraFeatureBridge.swingDuration((net.minecraft.world.entity.Entity)(Object)this,cir.getReturnValue()));}'))
 screen=p['screenEffect']
 if screen:
  write(output,'ViewmodelScreenEffectMixin',mixin('ViewmodelScreenEffectMixin','net.minecraft.client.renderer.ScreenEffectRenderer',' @Inject(method="'+screen['selector']+'",at=@At("HEAD"),cancellable=true) private '+('static ' if screen['static'] else '')+'void nightclient$mask(CallbackInfo ci){if(CameraFeatureBridge.capturing())ci.cancel();}'))
 else:
  stale=Path(output)/'dev/nightclient/platform/mixin/ViewmodelScreenEffectMixin.java'
  if stale.exists():stale.unlink()

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--version',required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();generate_camera_features(args.version,args.output)
if __name__=='__main__':main()
