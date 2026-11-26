package dev.nightclient.platform;

import com.mojang.blaze3d.platform.Window;
import dev.nightclient.config.Settings;
import dev.nightclient.runtime.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class RetainedHudState {
  private RetainedHudState() {}

  private static boolean valid;
  private static boolean wantRetainThisFrame;
  private static boolean reusedThisFrame;
  private static long evaluatedFrame = Long.MIN_VALUE;
  private static long lastFingerprint;
  private static Object lastWorldRef;

  public static void invalidate() {
    valid = false;
    wantRetainThisFrame = false;
    reusedThisFrame = false;
    lastFingerprint = 0L;
  }

  public static void markBuilt() {
    valid = true;
  }

  public static boolean wantRetainThisFrame() {
    return wantRetainThisFrame && evaluatedFrame == OptimizationBridge.frameIndex;
  }

  public static boolean reusedThisFrame() {
    return reusedThisFrame && evaluatedFrame == OptimizationBridge.frameIndex;
  }

  public static boolean evaluateAndCheckHit(
      Minecraft mc, boolean renderHud, boolean hasScreenOrOverlay, boolean debugShown,
      boolean hudHidden, int guiTicks) {
    evaluatedFrame = OptimizationBridge.frameIndex;
    reusedThisFrame = false;
    wantRetainThisFrame = false;

    if (!OptimizationBridge.opt119RetainedHud
        || !renderHud
        || hasScreenOrOverlay
        || debugShown
        || hudHidden
        || mc == null
        || mc.level == null
        || mc.player == null) {
      valid = false;
      return false;
    }

    LocalPlayer player = mc.player;
    if (player.hurtTime > 0 || player.getTicksFrozen() > 0 || player.isUsingItem()) {
      valid = false;
      return false;
    }

    ClientRuntime runtime = ClientRuntime.get();
    if (runtime != null) {
      Settings settings = runtime.settings();
      if (settings != null) {
        boolean tabAnimActive = settings.tabAnimation != null && settings.tabAnimation.enabled
            && (mc.options.keyPlayerList.isDown() || WorldFeatureBridge.isTabAnimationActive());
        boolean betterChatActive = settings.betterChat != null && settings.betterChat.enabled;
        boolean tntActive = settings.tnt != null && settings.tnt.enabled && FeatureBridge.hasTnt();
        if (tntActive || runtime.shouldRenderHud() || GameBridge.backdropNeeded()
            || tabAnimActive || betterChatActive) {
          valid = false;
          return false;
        }
      }
    }

    Window window = mc.getWindow();
    if (window == null) {
      valid = false;
      return false;
    }

    if (mc.level != lastWorldRef) {
      lastWorldRef = mc.level;
      valid = false;
    }

    long fp = computeFingerprint(mc, player, window, guiTicks);
    wantRetainThisFrame = true;
    if (valid && fp == lastFingerprint) {
      reusedThisFrame = true;
      return true;
    }
    lastFingerprint = fp;
    valid = false;
    return false;
  }

  private static long computeFingerprint(Minecraft mc, LocalPlayer player, Window window, int guiTicks) {
    long h = 0x9E3779B97F4A7C15L;
    h = mix(h, mc.level.getGameTime());
    h = mix(h, guiTicks);
    h = mix(h, ((long) window.getWidth() << 32) ^ ((long) window.getHeight() << 8) ^ window.getGuiScale());

    int slot = player.getInventory().getSelectedSlot();
    ItemStack main = player.getMainHandItem();
    ItemStack off = player.getOffhandItem();
    h = mix(h, ((long) slot << 40)
        ^ ((long) System.identityHashCode(main) << 8)
        ^ main.getCount()
        ^ ((long) main.getDamageValue() << 24));
    h = mix(h, ((long) System.identityHashCode(off) << 16) ^ off.getCount() ^ off.getDamageValue());

    int attackStep = (int) (player.getAttackStrengthScale(0.5f) * 32.0f);
    int combatAndUi = (mc.options.keyPlayerList.isDown() ? 1 : 0)
        | ((mc.options.getCameraType().ordinal() & 7) << 1)
        | ((attackStep & 63) << 4)
        | ((player.getFoodData().getFoodLevel() & 63) << 10)
        | ((player.getArmorValue() & 63) << 16)
        | ((player.experienceLevel & 1023) << 22);
    h = mix(h, combatAndUi);
    h = mix(h, ((long) Float.floatToRawIntBits(player.getHealth()) << 32)
        ^ (Float.floatToRawIntBits(player.getAbsorptionAmount()) & 0xFFFFFFFFL));
    h = mix(h, ((long) Float.floatToRawIntBits(player.experienceProgress) << 32)
        ^ (player.getAirSupply() & 0xFFFFFFFFL));
    return h;
  }

  private static long mix(long h, long v) {
    h ^= v;
    h *= 0xBF58476D1CE4E5B9L;
    return h ^ (h >>> 31);
  }
}
