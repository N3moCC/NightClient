package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudFrameSnapshotTest {
  @TempDir Path directory;
  private static final class Platform implements PlatformServices {
    final Path directory;
    int cooldowns, inventory, tnts, armor, potions, vitals, target, crosshair;
    int networkInfo, keystrokes, coordinates, scoreboard, localPlayer, ping,
        gameVersion;
    boolean hasCooldowns = true;
    Platform(Path directory) {
      this.directory = directory;
    }
    public Path configDirectory() {
      return directory;
    }
    public String gameVersion() {
      gameVersion++;
      return "fixture";
    }
    public String clipboard() {
      return "";
    }
    public void clipboard(String value) {}
    public void closeMenu() {}
    public String menuKeyLabel() {
      return "R SHIFT";
    }
    public List<Cooldown> cooldowns(float partialTick) {
      cooldowns++;
      return hasCooldowns
          ? Collections.singletonList(new Cooldown("a", "Shield", "minecraft:shield", 2, 10))
          : Collections.<Cooldown>emptyList();
    }
    public List<InventorySlot> inventory(float partialTick) {
      inventory++;
      return Collections.singletonList(new InventorySlot(0, "minecraft:stone", "Stone", 1, 0, 0));
    }
    public List<TntView> tnts(float partialTick, int width, int height) {
      tnts++;
      return Collections.singletonList(new TntView(1, 0, 0, 2, 4));
    }
    public List<InventorySlot> armor(float partialTick) {
      armor++;
      return Collections.singletonList(
          new InventorySlot(100, "minecraft:diamond_helmet", "Helmet", 1, 0, 0));
    }
    public List<PotionView> potions(float partialTick) {
      potions++;
      return Collections.singletonList(
          new PotionView("minecraft:speed", "Speed", 0, 0xff88ff88, 10, true));
    }
    public VitalsView vitals(float partialTick) {
      vitals++;
      return new VitalsView(20, 5, true, true);
    }
    public TargetView target(float partialTick) {
      target++;
      return new TargetView(7, "uuid", "Target", 20, 20, 0, 3, false);
    }
    public boolean crosshairVisible() {
      crosshair++;
      return true;
    }
    public Map<String, Object> networkInfo() {
      networkInfo++;
      return Collections.singletonMap("connected", true);
    }
    public Map<String, Object> keystrokes() {
      keystrokes++;
      return Collections.emptyMap();
    }
    public Map<String, Object> coordinates() {
      coordinates++;
      return Collections.emptyMap();
    }
    public Map<String, Object> scoreboard() {
      scoreboard++;
      return Collections.emptyMap();
    }
    public TargetView localPlayer(float partialTick) {
      localPlayer++;
      return null;
    }
    public int ping() {
      ping++;
      return 42;
    }
    void reset() {
      cooldowns = inventory = tnts = armor = potions = vitals = target = crosshair = 0;
      networkInfo = keystrokes = coordinates = scoreboard = localPlayer = ping =
          gameVersion = 0;
    }
    void assertOneEach() {
      assertEquals(1, cooldowns, "cooldowns");
      assertEquals(1, inventory, "inventory");
      assertEquals(1, tnts, "tnts");
      assertEquals(1, armor, "armor");
      assertEquals(1, potions, "potions");
      assertEquals(1, vitals, "vitals");
      assertEquals(1, target, "target");
      assertEquals(1, crosshair, "crosshair");
    }
  }
  private ClientRuntime runtime(Platform platform) throws Exception {
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    return constructor.newInstance(platform);
  }
  private static Canvas canvas() {
    return (Canvas) Proxy.newProxyInstance(HudFrameSnapshotTest.class.getClassLoader(),
        new Class<?>[] {Canvas.class}, (proxy, method, args) -> {
          if (method.getReturnType() == float.class) return 20f;
          if (method.getReturnType() == boolean.class) return false;
          return null;
        });
  }
  private static Canvas metricsCanvas() {
    return (Canvas) Proxy.newProxyInstance(HudFrameSnapshotTest.class.getClassLoader(),
        new Class<?>[] {Canvas.class}, (proxy, method, args) -> {
          if (method.getName().equals("textMetricsKey")) return "fixture-metrics";
          if (method.getReturnType() == float.class) return 20f;
          if (method.getReturnType() == boolean.class) return false;
          return null;
        });
  }
  private static void allPanels(Settings settings) {
    settings.inventory.enabled = settings.tnt.enabled = settings.armor.enabled =
        settings.potions.enabled = settings.food.enabled = settings.target.enabled = true;
    settings.crosshair.enabled = true;
    settings.menu.animationSpeed = 1000000f;
  }

  @Test
  void oneSnapshotServesVisibilityAndRenderingForAFrame() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      allPanels(runtime.settings());
      runtime.changed();
      platform.reset();
      runtime.prepareHudFrame(.5f, 640, 400);
      assertTrue(runtime.shouldRenderHud());
      runtime.renderHud(canvas(), 640, 400, .5f);
      platform.assertOneEach();
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void hiddenFrameQueriesOnlyTheDataVisibilityActuallyNeeds() throws Exception {
    Platform platform = new Platform(directory);
    platform.hasCooldowns = false;
    ClientRuntime runtime = runtime(platform);
    try {
      runtime.settings().menu.animationSpeed = 1000000f;
      runtime.changed();
      platform.reset();
      runtime.prepareHudFrame(0, 640, 400);
      assertFalse(runtime.shouldRenderHud());
      assertEquals(1, platform.cooldowns);
      assertEquals(1, platform.vitals);
      assertEquals(0,
          platform.inventory + platform.tnts + platform.armor + platform.potions + platform.target
              + platform.crosshair);
      platform.reset();
      platform.hasCooldowns = true;
      runtime.prepareHudFrame(0, 640, 400);
      assertTrue(runtime.shouldRenderHud());
      assertEquals(1, platform.cooldowns);
      assertEquals(0,
          platform.inventory + platform.tnts + platform.armor + platform.potions + platform.vitals
              + platform.target + platform.crosshair);
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void directRenderStillPreparesOneFreshSnapshotPerCall() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      allPanels(runtime.settings());
      runtime.changed();
      platform.reset();
      runtime.renderHud(canvas(), 640, 400, .25f);
      platform.assertOneEach();
      platform.reset();
      runtime.renderHud(canvas(), 640, 400, .75f);
      platform.assertOneEach();
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void preparedFrameKeepsItsDataUntilTheFrameIsConsumed() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      allPanels(runtime.settings());
      runtime.changed();
      platform.reset();
      runtime.prepareHudFrame(.5f, 640, 400);
      assertTrue(runtime.shouldRenderHud());
      platform.hasCooldowns = false;
      runtime.renderHud(canvas(), 640, 400, .5f);
      platform.assertOneEach();
    } finally {
      runtime.shutdown();
    }
  }

  private static int hudCacheHits(ClientRuntime runtime) throws Exception {
    Field skinField = ClientRuntime.class.getDeclaredField("hudSkin");
    skinField.setAccessible(true);
    Object skin = skinField.get(runtime);
    assertNotNull(skin);
    Method hits = skin.getClass().getDeclaredMethod("hudFrameCacheHits");
    hits.setAccessible(true);
    return (Integer) hits.invoke(skin);
  }

  @Test
  void resourceReloadDropsTheHudDisplayList() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      allPanels(runtime.settings());
      runtime.settings().optimization.enabled = true;
      runtime.changed();
      for (int i = 0; i < 200 && !"saved".equals(runtime.saveStatusKey()); i++) Thread.sleep(10);
      assertEquals("saved", runtime.saveStatusKey(),
          "configuration write must settle before timing cache hits");
      Canvas canvas = metricsCanvas();
      runtime.renderHud(canvas, 640, 400, .5f);
      runtime.renderHud(canvas, 640, 400, .5f);
      assertEquals(1, hudCacheHits(runtime), "the second frame should hit");
      runtime.resourceReload();
      runtime.renderHud(canvas, 640, 400, .5f);
      assertEquals(1, hudCacheHits(runtime), "reload must force a fresh frame");
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void hudActionSnapshotsAreSharedBetweenSignatureAndContext() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      allPanels(runtime.settings());
      runtime.settings().optimization.enabled = true;
      runtime.changed();
      runtime.prepareHudFrame(.5f, 640, 400);
      assertTrue(runtime.shouldRenderHud());
      platform.reset();
      runtime.renderHud(metricsCanvas(), 640, 400, .5f);
      assertEquals(1, platform.networkInfo, "networkInfo");
      assertEquals(1, platform.keystrokes, "keystrokes");
      assertEquals(1, platform.coordinates, "coordinates");
      assertEquals(1, platform.scoreboard, "scoreboard");
      assertEquals(1, platform.localPlayer, "localPlayer");
      assertEquals(1, platform.ping, "ping");
      assertEquals(1, platform.gameVersion, "gameVersion");
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void resolvedSkinDocumentIsReusedUntilSettingsChange() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      SkinDocument first = runtime.skinDocument();
      assertSame(first, runtime.skinDocument());
      Path skin = directory.resolve("nightclient/skin.nc");
      Files.createDirectories(skin.getParent());
      Files.write(skin, "theme { accent = #ff7788 }".getBytes(StandardCharsets.UTF_8));
      runtime.settings().skin.enabled = true;
      runtime.reloadSkin();
      SkinDocument custom = runtime.skinDocument();
      assertNotSame(first, custom);
      assertSame(custom, runtime.skinDocument());
      runtime.settings().skin.enabled = false;
      runtime.changed();
      assertSame(first, runtime.skinDocument());
    } finally {
      runtime.shutdown();
    }
  }
}
