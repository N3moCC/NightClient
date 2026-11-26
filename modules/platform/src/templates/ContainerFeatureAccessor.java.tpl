package dev.nightclient.platform.mixin;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import @@CLICK_TYPE_IMPORT@@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(AbstractContainerScreen.class)
public interface ContainerFeatureAccessor {
    @Accessor("hoveredSlot") Slot nightclient$hoveredSlot();
    @Accessor("menu") AbstractContainerMenu nightclient$menu();
    @Accessor("leftPos") int nightclient$leftPos();
    @Accessor("topPos") int nightclient$topPos();
    @Invoker("isHovering") boolean nightclient$isHovering(Slot slot,double x,double y);
    @Invoker("slotClicked") void nightclient$quickMove(Slot slot,int index,int button,@@CLICK_TYPE@@ type);
}
