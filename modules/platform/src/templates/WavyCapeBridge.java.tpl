package dev.nightclient.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.cape.WavyCapeConfig;
import dev.nightclient.renderer.cape.WavyCapeManager;
import dev.nightclient.renderer.cape.WavyCapeMesh;
import dev.nightclient.renderer.cape.WavyCapePose;
import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
@@WAVY_CAPE_BRIDGE_IMPORTS@@

public final class WavyCapeBridge {
    private static final WavyCapeManager MANAGER = new WavyCapeManager();
    private static final WavyCapePose POSE = new WavyCapePose();

    private WavyCapeBridge() {}

    public static boolean isEnabled() {
        if (FeaturePolicy.isBlocked("wavyCapes")) return false;
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime == null) return false;
        Settings.WavyCapes cfg = runtime.settings().wavyCapes;
        return cfg != null && cfg.enabled;
    }

    private static WavyCapeConfig currentConfig() {
        if (FeaturePolicy.isBlocked("wavyCapes")) return null;
        ClientRuntime runtime = ClientRuntime.get();
        if (runtime == null) return null;
        Settings.WavyCapes cfg = runtime.settings().wavyCapes;
        if (cfg == null || !cfg.enabled) return null;
        return WavyCapeConfig.fromSettings(cfg);
    }

    private static void populateModelParts(HumanoidModel<?> model, WavyCapePose pose) {
        if (model == null) return;
        pose.bodyX = model.body.x;
        pose.bodyY = model.body.y;
        pose.bodyZ = model.body.z;
        pose.bodyXRot = model.body.xRot;
        pose.bodyYRot = model.body.yRot;
        pose.bodyZRot = model.body.zRot;
        pose.rightLegY = model.rightLeg.y;
        pose.rightLegZ = model.rightLeg.z;
        pose.rightLegXRot = model.rightLeg.xRot;
        pose.rightLegZRot = model.rightLeg.zRot;
        pose.leftLegY = model.leftLeg.y;
        pose.leftLegZ = model.leftLeg.z;
        pose.leftLegXRot = model.leftLeg.xRot;
        pose.leftLegZRot = model.leftLeg.zRot;
    }

    private static void populateWeather(Minecraft mc, WavyCapePose pose, float partialTick) {
        if (mc != null && mc.level != null) {
            pose.rainLevel = mc.level.getRainLevel(partialTick);
            pose.thunderLevel = mc.level.getThunderLevel(partialTick);
        } else {
            pose.rainLevel = 0.0f;
            pose.thunderLevel = 0.0f;
        }
    }

    public static void clear() {
        MANAGER.clear();
    }

@@WAVY_CAPE_BRIDGE_ENTRY@@

    private static void emitMesh(PoseStack.Pose pose, VertexConsumer consumer, WavyCapeMesh mesh, int packedLight) {
        if (mesh == null || mesh.quadCount <= 0) return;
        final float[] xyz = mesh.xyz;
        final float[] nrm = mesh.nrm;
        final float[] uv = mesh.uv;
        final float[] shade = mesh.shade;
        final int overlay = OverlayTexture.NO_OVERLAY;
        final int totalVerts = mesh.quadCount * 4;
        for (int v = 0; v < totalVerts; v++) {
            int p = v * 3;
            int t = v * 2;
            int rgb = (int) (shade[v] * 255.0f + 0.5f);
            if (rgb < 0) rgb = 0;
            else if (rgb > 255) rgb = 255;
            @@WAVY_CAPE_EMIT_VERTEX@@
        }
    }
}
