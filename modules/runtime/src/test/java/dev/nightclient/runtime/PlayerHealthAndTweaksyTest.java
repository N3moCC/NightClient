package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.PlayerHealthView;
import dev.nightclient.api.TotemCounterView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.WorldRibbon;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerHealthAndTweaksyTest {
  @TempDir Path directory;

  private ClientRuntime createRuntime(final List<PlayerHealthView> healths) throws Exception {
    return createRuntime(healths, Collections.<TotemCounterView>emptyList());
  }

  private ClientRuntime createRuntime(
      final List<PlayerHealthView> healths, final List<TotemCounterView> totems) throws Exception {
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    return constructor.newInstance(new PlatformServices() {
      @Override
      public Path configDirectory() {
        return directory;
      }

      @Override
      public String gameVersion() {
        return "fixture";
      }

      @Override
      public String clipboard() {
        return "";
      }

      @Override
      public void clipboard(String value) {}

      @Override
      public void closeMenu() {}

      @Override
      public List<Cooldown> cooldowns(float partial) {
        return Collections.emptyList();
      }

      @Override
      public String menuKeyLabel() {
        return "R SHIFT";
      }

      @Override
      public List<PlayerHealthView> playerHealths(float partialTick) {
        return healths;
      }

      @Override
      public List<TotemCounterView> totemCounters(float partialTick) {
        return totems;
      }

      @Override
      public WorldRibbon.View cameraView(int width, int height) {
        return new WorldRibbon.View(0, 64, 0, 0, 0, 70, 16f / 9, width, height);
      }
    });
  }

  @Test
  void settingsValidationClampsPlayerHealthAndInitializesTweaksyNametags() {
    Settings settings = new Settings();
    settings.playerHealth.style = "unknown";
    settings.playerHealth.height = 9f;
    settings.playerHealth.scale = .1f;
    settings.playerHealth.maxDistance = 500;
    settings.tweaksy.ownNametag = null;
    settings.tweaksy.invisibleArmorNametag = null;

    settings.validate();

    assertEquals("pill", settings.playerHealth.style);
    assertEquals(1.5f, settings.playerHealth.height, .001f);
    assertEquals(.6f, settings.playerHealth.scale, .001f);
    assertEquals(128, settings.playerHealth.maxDistance);
    assertNotNull(settings.tweaksy.ownNametag);
    assertNotNull(settings.tweaksy.invisibleArmorNametag);
    assertFalse(settings.tweaksy.ownNametag.enabled);
    assertFalse(settings.tweaksy.invisibleArmorNametag.enabled);
    assertFalse(settings.tweaksy.invisibleArmorNametag.hitbox);
  }

  @Test
  void playerHealthRendersWithoutHudWidgetsAndDrawsAbsorptionAndEnchanted() throws Exception {
    List<PlayerHealthView> list = Arrays.asList(
        new PlayerHealthView(1, 0, 64, 8, 1.8f, 16.5f, 20f, 8f, false, true),
        new PlayerHealthView(2, 1.5, 64, 12, 1.8f, 20f, 20f, 0f, true, false));
    ClientRuntime runtime = createRuntime(list);
    try {
      Settings s = runtime.settings();
      s.hud.enabled = false;
      s.skin.enabled = false;
      s.inventory.enabled = false;
      s.tnt.enabled = false;
      s.armor.enabled = false;
      s.potions.enabled = false;
      s.food.enabled = false;
      s.target.enabled = false;
      s.scoreboard.enabled = false;
      s.keystrokes.enabled = false;
      s.coordinates.enabled = false;
      s.ping.enabled = false;
      s.fps.enabled = false;
      s.playerHealth.enabled = true;
      s.playerHealth.showAbsorption = true;
      s.playerHealth.showEnchanted = true;
      s.playerHealth.showInvisible = true;
      runtime.changed();

      assertTrue(runtime.shouldRenderHud(),
          "Enabled playerHealth must keep HUD pass active even when all 2D HUD widgets are off");

      final List<String> labels = new ArrayList<String>();
      final int[] gradients = new int[1];
      Canvas canvas = (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if ("textFamily".equals(m.getName())) labels.add((String) a[1]);
            if ("gradientRoundedRect".equals(m.getName())) gradients[0]++;
            if (m.getReturnType() == float.class) {
              return m.getName().startsWith("textWidth") ? 20f : 0f;
            }
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });

      runtime.prepareHudFrame(0f, 1280, 720);
      runtime.renderHud(canvas, 1280, 720, 0f);

      assertTrue(labels.contains("16.5"), "First player HP label rendered");
      assertTrue(labels.contains(" +8"), "Absorption bonus label rendered");
      assertTrue(labels.contains("20"), "Invisible player HP label rendered");
      assertTrue(gradients[0] >= 3, "HP + absorption gradient segments rendered");
    } finally {
      runtime.close();
    }
  }

  @Test
  void playerHealthHeartsStyleDrawsBaseAndGoldenAbsorptionHearts() throws Exception {
    List<PlayerHealthView> list = Collections.singletonList(
        new PlayerHealthView(1, 0, 64, 8, 1.8f, 15.0f, 20f, 4f, false, true));
    ClientRuntime runtime = createRuntime(list);
    try {
      Settings s = runtime.settings();
      s.hud.enabled = false;
      s.skin.enabled = false;
      s.inventory.enabled = false;
      s.tnt.enabled = false;
      s.armor.enabled = false;
      s.potions.enabled = false;
      s.food.enabled = false;
      s.target.enabled = false;
      s.scoreboard.enabled = false;
      s.keystrokes.enabled = false;
      s.coordinates.enabled = false;
      s.ping.enabled = false;
      s.fps.enabled = false;
      s.playerHealth.enabled = true;
      s.playerHealth.style = "hearts";
      s.playerHealth.showAbsorption = true;
      s.playerHealth.showEnchanted = true;
      runtime.changed();

      assertEquals("hearts", s.playerHealth.style);

      final int[] triangles = new int[1];
      final int[] roundedRects = new int[1];
      final List<Integer> fillColors = new ArrayList<Integer>();
      Canvas canvas = (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if ("fillTriangle".equals(m.getName())) {
              triangles[0]++;
              fillColors.add((Integer) a[a.length - 1]);
            }
            if ("circle".equals(m.getName())) {
              fillColors.add((Integer) a[a.length - 1]);
            }
            if ("roundedRect".equals(m.getName())) {
              roundedRects[0]++;
            }
            if (m.getReturnType() == float.class) {
              return m.getName().startsWith("textWidth") ? 20f : 0f;
            }
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });

      runtime.prepareHudFrame(0f, 1280, 720);
      runtime.renderHud(canvas, 1280, 720, 0f);

      assertEquals(0, roundedRects[0], "Hearts style must not draw a translucent glow background box");
      assertTrue(triangles[0] >= 12, "Heart shapes draw triangles for base + absorption hearts");
      boolean hasRed = fillColors.stream().anyMatch(c -> (c & 0xffffff) == 0xff4258);
      boolean hasGold = fillColors.stream().anyMatch(c -> (c & 0xffffff) == 0xfacc15);
      assertTrue(hasRed, "Base red hearts drawn");
      assertTrue(hasGold, "Golden absorption hearts drawn");
    } finally {
      runtime.close();
    }
  }

  @Test
  void totemCounterRendersUnderNametagWithIconAndRespectsShowZero() throws Exception {
    List<TotemCounterView> totems = Arrays.asList(
        new TotemCounterView(1, 0, 64, 8, 1.8f, 3, 0.25f, false),
        new TotemCounterView(2, 2, 64, 10, 1.8f, 0, 999f, false));
    ClientRuntime runtime = createRuntime(Collections.<PlayerHealthView>emptyList(), totems);
    try {
      Settings s = runtime.settings();
      s.hud.enabled = false;
      s.skin.enabled = false;
      s.inventory.enabled = false;
      s.tnt.enabled = false;
      s.armor.enabled = false;
      s.potions.enabled = false;
      s.food.enabled = false;
      s.target.enabled = false;
      s.scoreboard.enabled = false;
      s.keystrokes.enabled = false;
      s.coordinates.enabled = false;
      s.ping.enabled = false;
      s.fps.enabled = false;
      s.playerHealth.enabled = false;
      s.totemCounter.enabled = true;
      s.totemCounter.showZero = false;
      s.totemCounter.height = 5f;
      s.totemCounter.scale = 0.2f;
      s.totemCounter.maxDistance = 300;
      s.validate();
      assertEquals(0.8f, s.totemCounter.height, 0.001f);
      assertEquals(0.6f, s.totemCounter.scale, 0.001f);
      assertEquals(128, s.totemCounter.maxDistance);
      s.totemCounter.height = 0f;
      s.totemCounter.scale = 1f;
      runtime.changed();

      assertTrue(runtime.shouldRenderHud(),
          "Enabled totemCounter must keep HUD pass active independently");

      final List<String> labels = new ArrayList<String>();
      final List<String> items = new ArrayList<String>();
      Canvas canvas = (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if ("textFamily".equals(m.getName())) labels.add((String) a[1]);
            if ("item".equals(m.getName())) items.add((String) a[0]);
            if (m.getReturnType() == float.class) {
              return m.getName().startsWith("textWidth") ? 14f : 0f;
            }
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });

      runtime.prepareHudFrame(0f, 1280, 720);
      runtime.renderHud(canvas, 1280, 720, 0f);

      assertTrue(labels.contains("\u00d73"), "Popped totem count rendered");
      assertFalse(labels.contains("\u00d70"), "Zero pops hidden when showZero=false");
      assertTrue(items.contains("minecraft:totem_of_undying"), "Totem item icon rendered");

      labels.clear();
      s.totemCounter.showZero = true;
      runtime.changed();
      runtime.prepareHudFrame(0f, 1280, 720);
      runtime.renderHud(canvas, 1280, 720, 0f);
      assertTrue(labels.contains("\u00d70"), "Zero pops shown when showZero=true");
    } finally {
      runtime.close();
    }
  }
}
