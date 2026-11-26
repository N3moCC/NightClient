package dev.nightclient.platform.mixin;
import dev.nightclient.platform.WorldFeatureBridge;
import dev.nightclient.platform.ItemPhysicsPose;
import dev.nightclient.platform.ItemPhysicsState;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
@@PHYSICS_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
    @Unique private ItemEntity nightclient$cachedEntity;
    @Unique private float nightclient$cachedDelta=Float.NaN;
    @Unique private ItemPhysicsPose nightclient$cachedPose;
    @@PHYSICS_EXTRACT@@
    @Redirect(method="@@ITEM_HOOK@@",at=@At(value="INVOKE",target="@@POSE_ROTATION_TARGET@@",ordinal=0))
    private void nightclient$rotation(PoseStack target,@@ROTATION_ARGS@@,@@ITEM_ARGS_NO_COMMA@@){if(!(@@PHYSICS_GET@@).enabled){@@ORIGINAL_ROTATION@@}}
    @Redirect(method="@@ITEM_HOOK@@",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(@@TRANSLATE_DESC@@)V",ordinal=0))
    private void nightclient$hover(PoseStack target,@@TRANSLATE_ARGS@@,@@ITEM_ARGS_NO_COMMA@@){
        ItemPhysicsPose physics=@@PHYSICS_GET@@;

        target.translate(x,physics.enabled&&physics.grounded?.0625:y,z);
        if(physics.enabled){pose=target;@@PHYSICS_ROTATE@@}
    }
    private ItemPhysicsPose extract(ItemEntity entity,float delta){
        WorldFeatureBridge.Policy policy=WorldFeatureBridge.policy();if(!policy.itemPhysics)return ItemPhysicsPose.NONE;
        if(entity==nightclient$cachedEntity&&Float.compare(delta,nightclient$cachedDelta)==0&&nightclient$cachedPose!=null)return nightclient$cachedPose;
        boolean ground=policy.grounded&&entity.@@GROUND_METHOD@@();
        float seed=(entity.getUUID().hashCode()&0xffff)*360f/65536f;
        float age=(entity.getAge()+Math.max(0,Math.min(1,delta)))*policy.rotationSpeed;
        ItemPhysicsPose next=new ItemPhysicsPose(true,ground,ground?90:age*9,ground?seed:seed+age*6,ground?0:age*3);
        nightclient$cachedEntity=entity;nightclient$cachedDelta=delta;nightclient$cachedPose=next;
        return next;
    }
}
