package dev.nightclient.platform.mixin;

import dev.nightclient.platform.GameBridge;
import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
@@MOUSE_ACCOUNT_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseAccountOverlayMixin {
    @Shadow private double xpos;
    @Shadow private double ypos;

    @@MOUSE_ACCOUNT_INJECT@@
}
