package dev.nightclient.platform;

import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
@@LITEAPI_BRIDGE_IMPORTS@@

public final class LiteApiBridge {
    private LiteApiBridge() {}

    public static void onLogin() {
        Minecraft mc = Minecraft.getInstance();
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime == null || mc == null) return;
        boolean remote = mc.getSingleplayerServer() == null && !mc.isLocalServer();
        runtime.onServerJoin(remote);
    }

    public static void onDisconnect() {
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime != null) runtime.onServerDisconnect();
    }

    public static void onPayload(byte[] data) {
        if (data == null || data.length == 0) return;
        final byte[] copy = data;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.isSameThread()) {
            ClientRuntime runtime = ClientRuntime.get();
            if (runtime != null) runtime.onLiteApiPayload(copy);
        } else {
            mc.execute(() -> {
                ClientRuntime runtime = ClientRuntime.get();
                if (runtime != null) runtime.onLiteApiPayload(copy);
            });
        }
    }

    public static boolean send(String channel, byte[] payload) {
        if (channel == null || payload == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return false;
        try {
            @@LITEAPI_SEND_BODY@@
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
