package dev.nightclient.platform.mixin;
import dev.nightclient.platform.ItemPhysicsPose;
import dev.nightclient.platform.ItemPhysicsState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(ItemEntityRenderState.class)
public abstract class ItemPhysicsStateMixin implements ItemPhysicsState {
    @Unique private ItemPhysicsPose nightclient$physics=ItemPhysicsPose.NONE;
    public ItemPhysicsPose nightclient$physics(){return nightclient$physics;}
    public void nightclient$physics(ItemPhysicsPose value){nightclient$physics=value;}
}
