package dev.nightclient.platform;

import dev.nightclient.config.FeaturePolicy;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
@@LITEAPI_PAYLOAD_IMPORTS@@

public final class LiteApiPayload implements CustomPacketPayload {
    public static final int MAX_BYTES = 262144;
    private final byte[] data;

    public LiteApiPayload(byte[] data) {
        this.data = data == null ? new byte[0] : data;
    }

    public byte[] data() {
        return data;
    }

@@LITEAPI_PAYLOAD_BODY@@
}
