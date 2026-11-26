package dev.nightclient.platform;

import dev.nightclient.config.SavedAccount;
import dev.nightclient.platform.mixin.SessionAccessor;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
@@SESSION_BRIDGE_IMPORTS@@

public final class SessionBridge {
    private static boolean captured;
    private static boolean launcherActive = true;
    private static String launcherName = "";
    private static User initialUser;
    @@SESSION_INITIAL_FIELDS@@

    private SessionBridge() {}

    public static void captureInitial() {
        if (captured) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        SessionAccessor acc = (SessionAccessor) (Object) mc;
        User current = acc.nightclient$user();
        if (current == null) return;
        initialUser = current;
        launcherName = current.getName() == null ? "" : current.getName();
        @@SESSION_CAPTURE_INITIAL@@
        captured = true;
    }

    public static String launcherNickname() {
        captureInitial();
        return launcherName;
    }

    public static boolean isLauncherActive() {
        captureInitial();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && initialUser != null) {
            User current = ((SessionAccessor) (Object) mc).nightclient$user();
            if (current != null) {
                return current == initialUser;
            }
        }
        return launcherActive;
    }

    public static boolean switchOffline(final String rawName, final UUID uuid) {
        final String name = SavedAccount.normalizeNickname(rawName);
        if (name == null || uuid == null) return false;
        captureInitial();
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        Runnable apply = new Runnable() {
            @Override public void run() {
                SessionAccessor acc = (SessionAccessor) (Object) mc;
                User offline = @@SESSION_CREATE_OFFLINE_USER@@;
                acc.nightclient$setUser(offline);
                @@SESSION_APPLY_OFFLINE_SERVICES@@
                launcherActive = false;
            }
        };
        if (mc.isSameThread()) apply.run();
        else mc.execute(apply);
        return true;
    }

    public static boolean restoreLauncher() {
        captureInitial();
        if (!captured || initialUser == null) return false;
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        Runnable restore = new Runnable() {
            @Override public void run() {
                SessionAccessor acc = (SessionAccessor) (Object) mc;
                acc.nightclient$setUser(initialUser);
                @@SESSION_RESTORE_INITIAL_SERVICES@@
                launcherActive = true;
            }
        };
        if (mc.isSameThread()) restore.run();
        else mc.execute(restore);
        return true;
    }

    public static boolean canReconnectServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && !mc.isLocalServer() && mc.getCurrentServer() != null;
    }

    public static String currentServerLabel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "";
        ServerData data = mc.getCurrentServer();
        if (data == null) return "";
        return data.ip != null && !data.ip.isEmpty() ? data.ip : (data.name == null ? "" : data.name);
    }

    public static boolean reconnectCurrentServer() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.isLocalServer()) return false;
        final ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isEmpty()) return false;
        Runnable reconnect = new Runnable() {
            @Override public void run() {
                @@SESSION_DISCONNECT_WORLD@@
                Screen parent = new JoinMultiplayerScreen(new TitleScreen());
                @@SESSION_START_CONNECTING@@
            }
        };
        if (mc.isSameThread()) reconnect.run();
        else mc.execute(reconnect);
        return true;
    }
}
