package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.config.Settings;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeLifecycleTest {
  @TempDir Path directory;
  private ClientRuntime runtime() throws Exception {
    return runtime(Collections.<Cooldown>emptyList());
  }
  private ClientRuntime runtime(final List<Cooldown> data) throws Exception {
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    return constructor.newInstance(new PlatformServices() {
      public Path configDirectory() {
        return directory;
      }
      public String gameVersion() {
        return "fixture";
      }
      public String clipboard() {
        return "";
      }
      public void clipboard(String value) {}
      public void closeMenu() {}
      public List<Cooldown> cooldowns(float partial) {
        return data;
      }
      public String menuKeyLabel() {
        return "R SHIFT";
      }
    });
  }
  @Test
  void resetAndUndoPreserveEveryAddedModule() throws Exception {
    ClientRuntime runtime = runtime();
    try {
      Settings s = runtime.settings();
      s.armor.enabled = s.potions.enabled = s.food.enabled = s.target.enabled = true;
      s.tweaksy.commandFix.enabled = s.itemScroller.enabled = s.hitColor.enabled =
          s.hitbox.enabled = true;
      s.noRender.enabled = s.noRender.waterOverlay = s.noRender.waterFog = s.noRender.lavaFog =
          s.itemPhysics.enabled = s.tweaksy.fullBright.enabled = s.viewmodel.enabled = true;
      s.aspectRatio.enabled = s.swing.enabled = s.freelook.enabled = true;
      s.viewmodel.off.rotateY = 123;
      s.target.style = "zakohp";
      s.moduleBindings.put("saturation", "key.keyboard.g");
      runtime.changed();
      String original = runtime.exportProfile();
      runtime.reset();
      assertFalse(runtime.settings().tweaksy.commandFix.enabled);
      assertFalse(runtime.settings().viewmodel.enabled);
      assertTrue(runtime.settings().moduleBindings.isEmpty());
      runtime.undo();
      assertEquals(original, runtime.exportProfile());
      assertEquals("key.keyboard.g", runtime.settings().moduleBindings.get("saturation"));
      assertEquals("/login Ник Пароль", runtime.fixCommand("/дщпшт Ник Пароль"));
    } finally {
      runtime.shutdown();
    }
  }
  @Test
  void paletteOnlyCustomFileKeepsTheBundledMenuAndHud() throws Exception {
    ClientRuntime runtime = runtime();
    try {
      assertNotNull(runtime.skinDocument().menu());
      assertNotNull(runtime.skinDocument().hud());
      Path skin = directory.resolve("nightclient/skin.nc");
      Files.createDirectories(skin.getParent());
      Files.write(skin,
          "theme { accent = #ff7788 }\nstyle element button { borderRadius = 2px }".getBytes(
              StandardCharsets.UTF_8));
      runtime.settings().skin.enabled = true;
      runtime.reloadSkin();
      assertNotNull(runtime.skinDocument().menu());
      assertNotNull(runtime.skinDocument().hud());
      assertSame(runtime.skinDocument(), runtime.skinDocument());
      assertEquals(0xffff7788, runtime.settings().styleTheme("cooldowns").accent);
    } finally {
      runtime.shutdown();
    }
  }
  @Test
  void appearanceResetPreservesGameplayBindingsAndHudPlacementAndCanBeUndone() throws Exception {
    ClientRuntime runtime = runtime();
    try {
      Settings s = runtime.settings();
      s.saturation.enabled = true;
      s.saturation.strength = 1.7f;
      s.protect.enabled = true;
      s.protect.replacement = "Private";
      s.armor.offsetX = 37;
      s.armor.offsetY = 48;
      s.armor.scale = 1.7f;
      s.moduleBindings.put("zoom", "key.keyboard.g");
      s.menu.scale = 1.4f;
      s.menu.hudScale = 1.8f;
      s.theme.accent = 0xff223344;
      s.theme.backgroundOpacity = .55f;
      s.theme.effects.grain = true;
      Path skin = directory.resolve("nightclient/skin.nc");
      Files.createDirectories(skin.getParent());
      Files.write(skin, "theme { accent = #ff7788 }".getBytes(StandardCharsets.UTF_8));
      s.skin.enabled = true;
      runtime.reloadSkin();
      runtime.changed();
      String before = runtime.exportProfile();
      runtime.resetTheme();
      assertTrue(s.saturation.enabled);
      assertEquals(1.7f, s.saturation.strength);
      assertTrue(s.protect.enabled);
      assertEquals("Private", s.protect.replacement);
      assertEquals(37, s.armor.offsetX);
      assertEquals(48, s.armor.offsetY);
      assertEquals(1.7f, s.armor.scale);
      assertEquals("key.keyboard.g", s.moduleBindings.get("zoom"));
      assertEquals(new Settings.Menu().scale, s.menu.scale);
      assertEquals(new Settings.Menu().hudScale, s.menu.hudScale);
      assertEquals(dev.nightclient.config.Theme.defaults().accent, s.theme.accent);
      assertFalse(s.skin.enabled);
      assertTrue(Files.exists(skin));
      assertTrue(runtime.canUndo());
      runtime.undo();
      assertEquals(before, runtime.exportProfile());
    } finally {
      runtime.shutdown();
    }
  }
  @Test
  void defaultEmptyHudDoesNotCaptureSceneAndFoodWithoutWorldDoesNotDraw() throws Exception {
    ClientRuntime runtime = runtime();
    try {
      assertFalse(runtime.shouldRenderHud());
      assertFalse(runtime.skinHudUsesBackdrop());
      runtime.settings().theme.effects.backgroundBlur = true;
      runtime.settings().inventory.enabled = true;
      runtime.changed();
      assertTrue(runtime.shouldRenderHud());
      assertTrue(runtime.skinHudUsesBackdrop());
      runtime.settings().inventory.opacity = 0;
      runtime.changed();
      assertTrue(runtime.shouldRenderHud());
      assertTrue(runtime.skinHudUsesBackdrop());
      runtime.settings().inventory.enabled = false;
      runtime.settings().food.enabled = true;
      runtime.changed();
      assertFalse(runtime.shouldRenderHud());
      assertFalse(runtime.skinHudUsesBackdrop());
    } finally {
      runtime.shutdown();
    }
  }
  @Test
  void lastTimerKeepsItsNativeRenderAndBackdropUntilTheDeclaredExitCanFinish() throws Exception {
    List<Cooldown> data = new java.util.ArrayList<Cooldown>();
    data.add(new Cooldown("a", "Shield", "minecraft:shield", 2, 10));
    ClientRuntime runtime = runtime(data);
    try {
      runtime.settings().theme.effects.backgroundBlur = true;
      dev.nightclient.api.Canvas canvas =
          (dev.nightclient.api.Canvas) java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(), new Class<?>[] {dev.nightclient.api.Canvas.class},
              (proxy, method, args) -> {
                if (method.getReturnType() == float.class) return 20f;
                if (method.getReturnType() == boolean.class) return false;
                return null;
              });
      runtime.renderHud(canvas, 640, 400, 0);
      assertTrue(runtime.shouldRenderHud());
      data.clear();
      assertTrue(runtime.shouldRenderHud());
      assertTrue(runtime.skinHudUsesBackdrop());
      runtime.settings().menu.animationSpeed = 1000000f;
      assertFalse(runtime.shouldRenderHud());
      runtime.settings().menu.animationSpeed = 1f;
      runtime.setWorldActive(false);
      assertFalse(runtime.shouldRenderHud());
    } finally {
      runtime.shutdown();
    }
  }
  @Test
  void hudEditorOwnsTheOnlyHudPassAndNormalRenderingResumesAfterClosing() throws Exception {
    ClientRuntime runtime =
        runtime(Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 2, 10)));
    try {
      final int[] draws = {0};
      dev.nightclient.api.Canvas canvas =
          (dev.nightclient.api.Canvas) java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(), new Class<?>[] {dev.nightclient.api.Canvas.class},
              (proxy, method, args) -> {
                if (method.getReturnType() == float.class) return 20f;
                if (method.getReturnType() == boolean.class) return false;
                draws[0]++;
                return null;
              });
      runtime.settings().menu.animationSpeed = 1000000f;
      assertTrue(runtime.shouldRenderHud());
      runtime.renderHud(canvas, 640, 400, 0);
      assertTrue(draws[0] > 0);
      draws[0] = 0;
      runtime.menu().onOpen();
      runtime.menu().openEditor();
      assertFalse(runtime.shouldRenderHud());
      runtime.renderHud(canvas, 640, 400, 0);
      assertEquals(0, draws[0]);
      runtime.renderMenu(canvas, 640, 400, 0, 0);
      assertTrue(draws[0] > 0);
      draws[0] = 0;
      runtime.menu().onClose();
      assertTrue(runtime.shouldRenderHud());
      runtime.renderHud(canvas, 640, 400, 0);
      assertTrue(draws[0] > 0);
    } finally {
      runtime.shutdown();
    }
  }
  @Test
  void protectMasksRussianLayoutAuthArgumentsWithoutChangingTheTypedToken() throws Exception {
    ClientRuntime runtime = runtime();
    try {
      runtime.settings().tweaksy.commandFix.enabled = runtime.settings().protect.enabled = true;
      runtime.changed();
      String original = "/дщпшт  fixture_secret";
      String masked = runtime.maskPasswordInput(original);
      assertTrue(masked.startsWith("/дщпшт  "));
      assertEquals(original.length(), masked.length());
      assertFalse(masked.contains("fixture_secret"));
      assertEquals("/login  fixture_secret", runtime.fixCommand(original));
      assertFalse(runtime.maskPasswordInput("/login fixture_secret").contains("fixture_secret"));
      runtime.settings().tweaksy.commandFix.enabled = false;
      assertFalse(runtime.maskPasswordInput("/login fixture_secret").contains("fixture_secret"));
    } finally {
      runtime.shutdown();
    }
  }
}
