package dev.nightclient.platform;

import dev.nightclient.api.PickMask;
import dev.nightclient.api.ViewmodelSelection;
import dev.nightclient.api.ViewmodelProjection;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.runtime.ClientRuntime;
import dev.nightclient.platform.mixin.ViewmodelGameRendererAccessor;
import dev.nightclient.platform.mixin.ViewmodelTargetAccessor;
@@PROJECTION_IMPORT@@
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
@@IMPORTS@@

public final class CameraFeatureBridge {

    private static final float HAND_ANCHOR_X=.56f,HAND_ANCHOR_Y=-.40f,HAND_ANCHOR_Z=-.72f;
    private static KeyMapping freelookKey;
    private static Object previousPlayer,previousLevel;
    private static CameraType savedCameraType;
    private static boolean held,editing,pickRequested,capturing,closeRequested,hoverActive;
    private static int captureHand=-1,pendingReads;
    private static long epoch,revision;
    private static PickMask mainMask,offMask;
    private static ViewmodelProjection mainProjection,offProjection;
    private static float yawOffset,pitchOffset;
    private static long previousCameraTime;
    private static RenderTarget maskTarget;
    private CameraFeatureBridge(){}
    public static KeyMapping freelookKey(){if(freelookKey==null)freelookKey=@@FREELOOK_KEY@@;return freelookKey;}
    public static void poll(){
        Minecraft mc=Minecraft.getInstance();ClientRuntime runtime=ClientRuntime.get();
        if(previousPlayer!=mc.player||previousLevel!=mc.level){restoreCameraType(mc);previousPlayer=mc.player;previousLevel=mc.level;held=false;yawOffset=pitchOffset=0;previousCameraTime=0;stopEditing();}

        boolean allowed=!FeaturePolicy.isBlocked("freelook")&&runtime!=null&&runtime.settings().freelook.enabled&&mc.player!=null&&mc.level!=null&&@@SCREEN@@==null&&mc.isWindowActive();
        boolean down=allowed&&freelookKey().isDown();
        if(down&&!held){savedCameraType=mc.options.getCameraType();if(savedCameraType.isFirstPerson())mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
        else if(!down&&held)restoreCameraType(mc);
        held=down;
        if(!allowed){restoreCameraType(mc);yawOffset=pitchOffset=0;previousCameraTime=0;}
        if(editing&&!supported()){if(pendingReads!=0||mainMask!=null||offMask!=null||hoverActive)stopEditing();else pickRequested=false;}
    }
    private static void restoreCameraType(Minecraft mc){

        CameraType saved=savedCameraType;if(saved==null)return;savedCameraType=null;

        if(saved.isFirstPerson()){yawOffset=pitchOffset=0;previousCameraTime=0;}
        if(mc.options.getCameraType()!=saved)mc.options.setCameraType(saved);
    }
    public static boolean interceptTurn(Entity player,double yaw,double pitch){
        poll();Minecraft mc=Minecraft.getInstance();if(!held||player!=mc.player)return false;
        Settings.Freelook settings=ClientRuntime.get().settings().freelook;
        yawOffset+=(float)(yaw*.15*settings.sensitivity);pitchOffset=Math.max(-90,Math.min(90,player.getViewXRot(1)+pitchOffset+(float)(pitch*.15*settings.sensitivity)))-player.getViewXRot(1);return true;
    }
    public static void cameraFrame(){
        poll();long now=System.nanoTime();float delta=previousCameraTime==0?0:Math.max(0,Math.min(.1f,(now-previousCameraTime)/1e9f));previousCameraTime=now;
        ClientRuntime runtime=ClientRuntime.get();if(runtime==null||held)return;
        Settings.Freelook s=runtime.settings().freelook;
        if(!s.smoothReturn){yawOffset=pitchOffset=0;return;}
        float decay=(float)Math.exp(-s.returnSpeed*delta);yawOffset*=decay;pitchOffset*=decay;
        if(Math.abs(yawOffset)<.001f)yawOffset=0;if(Math.abs(pitchOffset)<.001f)pitchOffset=0;
    }
    public static float cameraYaw(Entity entity,float original){poll();return entity==Minecraft.getInstance().player?original+yawOffset:original;}
    public static float cameraPitch(Entity entity,float original){poll();return entity==Minecraft.getInstance().player?Math.max(-90,Math.min(90,original+pitchOffset)):original;}
    public static float aspect(float original){ClientRuntime runtime=ClientRuntime.get();return !FeaturePolicy.isBlocked("aspectRatio")&&runtime!=null&&runtime.settings().aspectRatio.enabled?runtime.settings().aspectRatio.ratio:original;}
    public static int swingDuration(Entity entity,int original){ClientRuntime r=ClientRuntime.get();return !FeaturePolicy.isBlocked("swing")&&r!=null&&entity==Minecraft.getInstance().player&&r.settings().swing.enabled?Math.max(1,Math.round(original/r.settings().swing.speed)):original;}
    public static float equip(float original){ClientRuntime r=ClientRuntime.get();return !FeaturePolicy.isBlocked("swing")&&r!=null&&r.settings().swing.enabled&&!r.settings().swing.equip?0:original;}
    public static boolean customSwing(PoseStack pose,HumanoidArm arm,float progress){
        ClientRuntime r=ClientRuntime.get();if(FeaturePolicy.isBlocked("swing")||r==null||!r.settings().swing.enabled)return false;Settings.Swing s=r.settings().swing;
        if("vanilla".equals(s.style)&&s.amplitude==1)return false;
        float sign=arm==HumanoidArm.RIGHT?1:-1,t=(float)Math.sin(Math.PI*Math.sqrt(Math.max(0,progress))),a=(float)Math.sin(progress*progress*Math.PI),strength=s.amplitude;
        if("smooth".equals(s.style)){float p=progress*progress*(3-2*progress);t=(float)Math.sin(Math.PI*p);rotate(pose,-60*t*strength,12*sign*t*strength,8*sign*t*strength);}
        else if("swipe".equals(s.style)){pose.translate(.15f*sign*t*strength,0,0);rotate(pose,-25*t*strength,30*sign*t*strength,60*sign*t*strength);}
        else if("spin".equals(s.style)){rotate(pose,0,0,360*progress*strength);}
        else if("custom".equals(s.style)){rotate(pose,s.rotateX*t*strength,s.rotateY*t*strength,s.rotateZ*t*strength);}
        else rotate(pose,-80*t*strength,-20*sign*a*strength,-20*sign*t*strength);
        return true;
    }
    public static boolean skipHand(InteractionHand hand){return capturing&&captureHand!=(hand==InteractionHand.MAIN_HAND?0:1);}
    public static boolean beginHand(PoseStack pose,InteractionHand hand){
        ClientRuntime r=ClientRuntime.get();if(FeaturePolicy.isBlocked("viewmodel")||r==null)return false;
        Settings.HandTransform h=hand==InteractionHand.MAIN_HAND?r.settings().viewmodel.main:r.settings().viewmodel.off;
        float side=hand==InteractionHand.MAIN_HAND?@@MAIN_ARM_SIGN@@:@@OFF_ARM_SIGN@@;
        if(editing&&!capturing&&supported())captureProjection(pose,hand,h,r.settings().viewmodel.enabled&&h.enabled,side);
        if(!r.settings().viewmodel.enabled||!h.enabled)return false;
        pose.pushPose();pose.translate(h.x,h.y,h.z);
        pose.translate(HAND_ANCHOR_X*side,HAND_ANCHOR_Y,HAND_ANCHOR_Z);rotate(pose,h.rotateX,h.rotateY,h.rotateZ);pose.scale(h.scaleX,h.scaleY,h.scaleZ);pose.translate(-HAND_ANCHOR_X*side,-HAND_ANCHOR_Y,-HAND_ANCHOR_Z);return true;
    }
    public static void endHand(PoseStack pose,boolean transformed){if(transformed)pose.popPose();}
    private static void rotate(PoseStack pose,float x,float y,float z){@@ROTATE@@}
    public static boolean supported(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&mc.options.getCameraType().isFirstPerson()&&!@@HIDDEN@@;}
    public static void editing(boolean value){if(editing==value)return;editing=value;if(!value)stopEditing();}
    public static void requestPick(float x,float y){if(editing&&supported()&&Float.isFinite(x)&&Float.isFinite(y))pickRequested=true;}

    public static void hover(float x,float y){
        if(!editing||!supported()||!Float.isFinite(x)||!Float.isFinite(y)){hoverActive=false;ViewmodelOutlineBackend.hide();return;}
        hoverActive=true;
        outlineMouseX=x;outlineMouseY=y;
    }
    private static float outlineMouseX,outlineMouseY;
    public static ViewmodelSelection selection(float x,float y){

        if(offMask!=null&&offMask.contains(x,y))return new ViewmodelSelection("off",offMask,revision,mainMask,offMask,mainProjection,offProjection);
        if(mainMask!=null&&mainMask.contains(x,y))return new ViewmodelSelection("main",mainMask,revision,mainMask,offMask,mainProjection,offProjection);
        return new ViewmodelSelection("",null,revision,mainMask,offMask,mainProjection,offProjection);
    }
    public static boolean capturing(){return capturing;}
    private static void stopEditing(){epoch++;pickRequested=false;hoverActive=false;mainMask=offMask=null;mainProjection=offProjection=null;ViewmodelOutlineBackend.hide();}
    private static void invalidate(){epoch++;pickRequested=false;hoverActive=false;mainMask=offMask=null;mainProjection=offProjection=null;closeRequested=true;if(pendingReads==0)release();}
    private static void release(){if(maskTarget!=null){maskTarget.destroyBuffers();maskTarget=null;}ViewmodelOutlineBackend.close();closeRequested=false;}
    public static void close(){restoreCameraType(Minecraft.getInstance());editing=false;held=false;yawOffset=pitchOffset=0;invalidate();}
    public static void resourceReload(){invalidate();}
    public static void capture(GameRenderer renderer,Runnable replay){
        if(capturing||!editing||(!pickRequested&&!hoverActive)||!supported()||pendingReads!=0)return;
        final boolean readRequested=pickRequested;pickRequested=false;
        Minecraft mc=Minecraft.getInstance();RenderTarget main=@@MAIN_TARGET@@;if(main.width<1||main.height<1||(long)main.width*main.height>16777216){revision++;return;}
        final int logicalWidth=Math.max(1,mc.getWindow().getGuiScaledWidth()),logicalHeight=Math.max(1,mc.getWindow().getGuiScaledHeight());
        @@BEFORE_CAPTURE@@
        capturing=true;
        try{
            if(maskTarget==null||maskTarget.width!=main.width||maskTarget.height!=main.height){if(maskTarget!=null){maskTarget.destroyBuffers();maskTarget=null;}ViewmodelOutlineBackend.hide();maskTarget=@@TARGET_CREATE@@;}
            closeRequested=false;final long ticket=epoch,newRevision=revision+1;final PickMask[] completed=new PickMask[2];
            for(int hand=0;hand<2;hand++){
                captureHand=hand;@@CLEAR_TARGET@@
                @@TARGET_BIND@@
                replay.run();@@FLUSH_HAND@@
                if(hoverActive&&hand==0)ViewmodelOutlineBackend.captureMain(maskTarget);
                if(!readRequested)continue;
                final int index=hand;pendingReads++;
                final boolean[] readDone=new boolean[1];
                try{
                    readMask(maskTarget,logicalWidth,logicalHeight,mask->{
                        try{if(mask!=null&&ticket==epoch&&editing&&supported()){completed[index]=mask;if(completed[0]!=null&&completed[1]!=null){mainMask=completed[0];offMask=completed[1];revision=newRevision;}}}
                        finally{if(!readDone[0]){readDone[0]=true;pendingReads--;if(closeRequested&&pendingReads==0)release();}}
                    });
                }catch(RuntimeException ex){
                    if(!readDone[0]){readDone[0]=true;pendingReads--;if(closeRequested&&pendingReads==0)release();}
                    throw ex;
                }
            }
            if(hoverActive)ViewmodelOutlineBackend.finish(maskTarget,outlineMouseX/logicalWidth,1-outlineMouseY/logicalHeight);
        }finally{capturing=false;captureHand=-1;@@RESTORE_TARGET@@}
    }
    @@READ_MASK@@
    @@DEPTH_PICK@@
    private static void captureProjection(PoseStack pose,InteractionHand hand,Settings.HandTransform h,boolean enabled,float side){
        float[] matrix=new float[16];@@PROJECT_HAND@@
        Minecraft mc=Minecraft.getInstance();ViewmodelProjection value=new ViewmodelProjection(matrix,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),HAND_ANCHOR_X*side,HAND_ANCHOR_Y,HAND_ANCHOR_Z,enabled?h.x:0,enabled?h.y:0,enabled?h.z:0,enabled?h.rotateX:0,enabled?h.rotateY:0,enabled?h.rotateZ:0);
        if(hand==InteractionHand.MAIN_HAND)mainProjection=value;else offProjection=value;
    }
    private static float[] multiply(float[] a,float[] b){float[] result=new float[16];for(int col=0;col<4;col++)for(int row=0;row<4;row++)for(int k=0;k<4;k++)result[col*4+row]+=a[k*4+row]*b[col*4+k];return result;}
}
