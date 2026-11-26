package dev.nightclient.platform.mixin;

import dev.nightclient.config.DisplayProtection;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import dev.nightclient.platform.GameBridge;
import dev.nightclient.platform.NativeCanvas;
import dev.nightclient.platform.NightScreen;
import dev.nightclient.platform.NightVanillaAccountScreen;
import dev.nightclient.runtime.AccountManager;
import dev.nightclient.runtime.ClientRuntime;
import dev.nightclient.ui.AccountButtonLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
@@SCREEN_IMPORTS@@
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenAccountOverlayMixin {
    @Unique
    private static final boolean NIGHTCLIENT_IAS_LOADED =
            FabricLoader.getInstance().isModLoaded("ias");

    @Shadow public int width;
    @Shadow public int height;
    @Shadow protected Font font;
    @Shadow public abstract List<? extends GuiEventListener> children();

    @@MIXIN_ADD_WIDGET_INVOKER@@

    @Unique private Button nightclient$accountBtn;
    @Unique private final List<int[]> nightclient$obstacles = new ArrayList<int[]>();
    @Unique private int nightclient$lastLayoutHash;
    @Unique private AccountButtonLayout.Placement nightclient$classicPlacement;

    @Inject(method = "@@SCREEN_RENDER_METHOD@@", at = @At("TAIL"))
    private void nightclient$renderAccountOverlay(@@CONTEXT@@ graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        Object self = this;
        boolean isTitle = self instanceof TitleScreen;
        boolean isMulti = !isTitle && self instanceof JoinMultiplayerScreen;
        if (!isTitle && !isMulti) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Screen activeScreen = @@SCREEN_ACCESS@@;
        if (activeScreen instanceof NightScreen || activeScreen instanceof NightVanillaAccountScreen) return;
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime == null || runtime.settings() == null || runtime.settings().accountSwitcher == null) return;

        Settings settings = runtime.settings();
        Settings.AccountSwitcher cfg = settings.accountSwitcher;
        boolean showOnScreen = !FeaturePolicy.isBlocked("accountSwitcher") && cfg.enabled && (isTitle ? cfg.titleScreen : cfg.multiplayerScreen);
        if (!showOnScreen) {
            runtime.hideScreenAccountOverlay();
            if (nightclient$accountBtn != null) nightclient$accountBtn.visible = false;
            return;
        }

        boolean classicMode = "classic".equals(cfg.layout);
        String prefPos = isTitle ? cfg.titlePosition : cfg.multiplayerPosition;
        List<? extends GuiEventListener> kids = this.children();
        int obsHash = nightclient$collectObstacles(kids);

        if (!classicMode) {
            if (nightclient$accountBtn != null) nightclient$accountBtn.visible = false;
            @@FLUSH_GUI@@
            NativeCanvas canvas = new NativeCanvas(graphics, width, height);
            try {
                runtime.renderScreenAccountOverlay(canvas, width, height, mouseX, mouseY, isTitle,
                        nightclient$obstacles, NIGHTCLIENT_IAS_LOADED);
            } finally {
                canvas.finish();
            }
            return;
        }

        runtime.hideScreenAccountOverlay();
        boolean justAdded = false;
        if (nightclient$accountBtn == null || !kids.contains(nightclient$accountBtn)) {
            final Screen owner = (Screen) (Object) this;
            nightclient$accountBtn = NightVanillaAccountScreen.createVanillaButton(
                    -1000, -1000, 20, 20, @@VANILLA_EMPTY_COMPONENT@@, new Button.OnPress() {
                        @Override public void onPress(Button btn) {
                            GameBridge.openAccountManager(owner);
                        }
                    });
            nightclient$accountBtn.visible = true;
            nightclient$addWidget(nightclient$accountBtn);
            nightclient$lastLayoutHash = 0;
            justAdded = true;
        }
        boolean wasVisible = nightclient$accountBtn.visible;
        nightclient$accountBtn.visible = true;

        AccountManager mgr = runtime.accountManager();
        String rawNick = mgr != null ? mgr.activeNickname() : "Player";
        boolean launcher = mgr == null || mgr.isLauncherActive();
        String displayNick = nightclient$maskNick(settings, rawNick);

        int desiredW;
        if (isTitle) {
            desiredW = 20;
        } else {
            int maxW = Math.max(76, Math.min(134, width / 2 - AccountButtonLayout.TITLE_HALF_CLEARANCE - 12));
            int textW = this.font != null ? this.font.width(displayNick) : displayNick.length() * 6;
            desiredW = Math.max(72, Math.min(maxW, 26 + textW + 8));
        }

        int layoutHash = obsHash * 31 + width * 17 + height * 13
                + (prefPos == null ? 0 : prefPos.hashCode()) * 7 + desiredW;
        if (nightclient$classicPlacement == null || layoutHash != nightclient$lastLayoutHash) {
            nightclient$classicPlacement = AccountButtonLayout.place(
                    width, height, desiredW, 20, isTitle, true, prefPos, nightclient$obstacles);
            nightclient$lastLayoutHash = layoutHash;
            @@BTN_SET_POS@@
            nightclient$accountBtn.setWidth(nightclient$classicPlacement.width);
        }

        if (justAdded || !wasVisible) return;

        int bx = nightclient$classicPlacement.x;
        int by = nightclient$classicPlacement.y;
        int bw = nightclient$classicPlacement.width;
        int bh = nightclient$classicPlacement.height;

        int[] face = mgr != null ? mgr.faceFor(rawNick)
                : dev.nightclient.renderer.player.PlayerFacePainter.fallbackFace(rawNick);
        if (bw <= 26) {
            int fx = bx + (bw - 16) / 2;
            int fy = by + (bh - 16) / 2;
            NightVanillaAccountScreen.drawFace(graphics, face, fx, fy, 2);
        } else {
            int fx = bx + 3;
            int fy = by + (bh - 16) / 2;
            NightVanillaAccountScreen.drawFace(graphics, face, fx, fy, 2);
            if (this.font != null) {
                boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
                String clipped = nightclient$clip(displayNick, bw - 27);
                NightVanillaAccountScreen.drawVanillaText(graphics, this.font, clipped,
                        bx + 22, by + (bh - 8) / 2, hovered ? 0xFFFFFFA0 : 0xFFE0E0E0);
            }
        }

        if (isTitle && !NIGHTCLIENT_IAS_LOADED && this.font != null) {
            int statusY = AccountButtonLayout.titleStatusTextY(width, height, nightclient$obstacles, false);
            if (statusY >= 0) {
                String lang = settings.language;
                String status = I18n.text(lang, "accounts.logged_in_as") + ": " + displayNick
                        + " (" + I18n.text(lang, launcher ? "accounts.launcher_badge" : "accounts.offline_badge") + ")";
                NightVanillaAccountScreen.drawVanillaCentered(graphics, this.font, status,
                        width / 2, statusY, launcher ? 0xFFA8F0B8 : 0xFFFFE082);
            }
        }
    }

    @Unique
    private int nightclient$collectObstacles(List<? extends GuiEventListener> kids) {
        nightclient$obstacles.clear();
        int hash = 1;
        for (int i = 0; i < kids.size(); i++) {
            Object child = kids.get(i);
            if (child == nightclient$accountBtn || !(child instanceof AbstractWidget)) continue;
            AbstractWidget w = (AbstractWidget) child;
            if (!w.visible) continue;
            int wx = @@WIDGET_GET_X_W@@;
            int wy = @@WIDGET_GET_Y_W@@;
            int ww = w.getWidth();
            int wh = w.getHeight();
            if (ww > 0 && wh > 0) {
                nightclient$obstacles.add(new int[] {wx, wy, ww, wh});
                hash = hash * 31 + wx;
                hash = hash * 31 + wy;
                hash = hash * 31 + ww;
                hash = hash * 31 + wh;
            }
        }
        return hash;
    }

    @Unique
    private static String nightclient$maskNick(Settings settings, String raw) {
        String name = raw == null || raw.trim().isEmpty() ? "Player" : raw.trim();
        if (settings.accountSwitcher != null && settings.accountSwitcher.hideAltsInProtect
                && settings.protect != null && settings.protect.enabled && settings.protect.hideNickname) {
            return DisplayProtection.maskNickname(name, name, settings.protect);
        }
        return name;
    }

    @Unique
    private String nightclient$clip(String text, int maxW) {
        if (this.font == null || text == null || maxW <= 4) return "";
        if (this.font.width(text) <= maxW) return text;
        String ell = "...";
        int ew = this.font.width(ell);
        if (maxW <= ew) return ell;
        return this.font.plainSubstrByWidth(text, maxW - ew) + ell;
    }
}
