package dev.nightclient.platform;

import dev.nightclient.runtime.ClientRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class NightClientMod implements ClientModInitializer {
  private static boolean reloadListenerRegistered, uiPrewarmed;
  private static Object lastConnection;

  @Override
  public void onInitializeClient() {
    dev.nightclient.platform.opt.NightNative.ensureLoaded();
    OptimizationBridge.initModCompat(FabricLoader.getInstance()::isModLoaded);
    GameBridge bridge = new GameBridge();
    dev.nightclient.config.FeatureSupport.configureForGameVersion(bridge.gameVersion());
    ClientRuntime.initialize(bridge);
  }

  public static void tick() {
    ClientRuntime runtime = ClientRuntime.get();
    if (runtime == null) runtime = ClientRuntime.initialize(new GameBridge());
    if (!reloadListenerRegistered) {
      ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
          .registerReloadListener((ResourceManagerReloadListener) manager -> {
            ClientRuntime current = ClientRuntime.get();
            if (current != null) current.resourceReload();
            LegacyUiRenderer.invalidate();
            FeatureBridge.resourceReload();
            CameraFeatureBridge.resourceReload();
            WorldFeatureBridge.resourceReload();
            MapTextureBackend.clear();
            PlayerPreviewBackend.clear();
            CosmeticsBridge.resourceReload();
            SaturationBackend.resourceReload();
            BackdropBackend.resourceReload();
            AtmosphereBackend.resourceReload();
            CustomModelBackend.resourceReload();
            OptimizationBridge.resetWorldCaches(null);
            dev.nightclient.platform.opt.NightNative.resetGpuMeshes();
            try {
              NativeCanvas.prewarm();
            } catch (Throwable ignored) {
            }
          });
      reloadListenerRegistered = true;
    }
    Minecraft minecraft = Minecraft.getInstance();
    SessionBridge.captureInitial();
    if (!uiPrewarmed && !GameBridge.isLoadingOverlay()) {
      uiPrewarmed = true;
      try {
        NativeCanvas.prewarm();
      } catch (Throwable ignored) {
      }
    }
    Object connection = minecraft.getConnection();
    if (lastConnection != null && connection == null) {
      runtime.onServerDisconnect();
    }
    lastConnection = connection;
    runtime.setWorldActive(minecraft.level != null && minecraft.player != null);
    OptimizationBridge.onClientTick();
    MapTextureBackend.flushRetired();
    runtime.tick();
    GameBridge.pollMenuKey();
    GameBridge.pollZoom();
  }

  public static void close() {
    ClientRuntime current = ClientRuntime.get();
    if (current != null) current.shutdown();
    LegacyUiRenderer.close();
    WorldFeatureBridge.close();
    MapTextureBackend.close();
    PlayerPreviewBackend.close();
    CosmeticsBridge.close();
    SaturationBackend.close();
    BackdropBackend.close();
    AtmosphereBackend.close();
    CustomModelBackend.close();
    CameraFeatureBridge.close();
    dev.nightclient.platform.opt.NightNative.shutdownGl();
  }
}
