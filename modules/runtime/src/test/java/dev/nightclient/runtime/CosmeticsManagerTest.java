package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.api.PlayerSkin;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.player.DefaultSkins;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CosmeticsManagerTest {
  private static final class DirectExecutor extends AbstractExecutorService {
    @Override
    public void shutdown() {}

    @Override
    public java.util.List<Runnable> shutdownNow() {
      return java.util.Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  @Test
  void appliesOfficialCapeNicknameSkinCustom4kSkinAndPushesPlatformUpdates(@TempDir Path temp)
      throws Exception {
    Settings settings = new Settings();
    AtomicInteger changedCalls = new AtomicInteger();
    AtomicInteger platformSkinW = new AtomicInteger();
    AtomicInteger platformCapeW = new AtomicInteger();
    final Boolean[] lastSlim = {null};
    final boolean[] lastCapeSuppressed = {false};

    PlatformServices platform = new PlatformServices() {
      @Override
      public Path configDirectory() {
        return temp;
      }

      @Override
      public String gameVersion() {
        return "1.21.11";
      }

      @Override
      public void closeMenu() {}

      @Override
      public String keyBindingLabel(String id) {
        return "";
      }

      @Override
      public void setKeyBinding(String id, String keyName) {}

      @Override
      public void updateCosmetics(int[] skinPixels, int skinW, int skinH, int skinRev,
          boolean capeOverride, boolean capeSuppressed, int[] capePixels, int capeW, int capeH,
          int capeRev, Boolean slimOverride) {
        platformSkinW.set(skinW);
        platformCapeW.set(capePixels == null ? 0 : capeW);
        lastSlim[0] = slimOverride;
        lastCapeSuppressed[0] = capeSuppressed;
      }
    };

    byte[] nickSkinPng = solidPng(128, 128, 0xffd05050);
    byte[] officialCapePng = mojangCapePng(128, 64, 0xff2266cc);

    String payloadJson =
        "{\"textures\":{\"SKIN\":{\"url\":\"https://example.invalid/nick.png\",\"metadata\":{\"model\":\"slim\"}}}}";
    String sessionJson = "{\"properties\":[{\"name\":\"textures\",\"value\":\""
        + Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
        + "\"}]}";

    CosmeticsManager.HttpFetcher fetcher = url -> {
      if (url.contains("api.mojang.com") && url.endsWith("Notch"))
        return "{\"id\":\"069a79f444e94726a5befca90e38aaf5\"}".getBytes(StandardCharsets.UTF_8);
      if (url.contains("sessionserver.mojang.com"))
        return sessionJson.getBytes(StandardCharsets.UTF_8);
      if ("https://example.invalid/nick.png".equals(url)) return nickSkinPng;
      if (url.contains("5e6f3193e74cd16cdd6637d9bae5484e3a37ff2a14c2d157c659a07810b1bdca"))
        return officialCapePng;
      throw new java.io.IOException("Unexpected URL in test: " + url);
    };

    CosmeticsManager manager = new CosmeticsManager(
        temp, platform, settings, changedCalls::incrementAndGet, fetcher, new DirectExecutor());

    manager.selectOfficialCape("copper");
    manager.tick();
    PlayerSkin preview;
    if (dev.nightclient.cosmetics.OfficialCapes.isEnabled()) {
      assertEquals("official", settings.cosmetics.capeMode);
      assertEquals(128, platformCapeW.get());
      assertFalse(lastCapeSuppressed[0]);

      preview = manager.composePreview(DefaultSkins.classic());
      assertNotNull(preview.capePixels());
      assertEquals(128, preview.capeWidth());
      assertEquals(0xff2266cc, preview.capePixels()[4 * 128 + 4]);
    } else {
      assertEquals("default", settings.cosmetics.capeMode);
      assertEquals(0, platformCapeW.get());
      assertFalse(lastCapeSuppressed[0]);
    }

    manager.applySkinNickname("Notch");
    manager.tick();
    assertEquals("nick", settings.cosmetics.skinMode);
    assertEquals(128, platformSkinW.get());
    assertEquals(Boolean.TRUE, lastSlim[0]);
    preview = manager.composePreview(DefaultSkins.classic());
    assertEquals(128, preview.bodyWidth());
    assertTrue(preview.slimModel());

    manager.disableCape();
    manager.tick();
    assertTrue(lastCapeSuppressed[0]);
    assertNull(manager.composePreview(DefaultSkins.classic()).capePixels());

    Path customSkin = temp.resolve("hero.png");
    java.nio.file.Files.write(customSkin, solidPng(512, 512, 0xff33aa44));
    manager.applyCustomSkinPath(customSkin.toString());
    manager.setSkinModel("classic");
    manager.tick();
    assertEquals(512, platformSkinW.get());
    assertEquals(Boolean.FALSE, lastSlim[0]);
    preview = manager.composePreview(DefaultSkins.slim());
    assertEquals(512, preview.bodyWidth());
    assertFalse(preview.slimModel());

    if (dev.nightclient.cosmetics.OfficialCapes.isEnabled()) {
      assertNotNull(manager.capeThumbnail("copper"));
      assertEquals(128, manager.capeThumbnail("copper").capeWidth());
      assertTrue(manager.capeThumbnailKey("copper").startsWith("cape:copper:"));
    } else {
      assertNull(manager.capeThumbnail("copper"));
    }
  }

  @Test
  void selects3dWeaponsAndArmorImportsCustomObjAndPushesPlatformUpdates(@TempDir Path temp)
      throws Exception {
    Settings settings = new Settings();
    AtomicInteger changedCalls = new AtomicInteger();
    final dev.nightclient.renderer.model.CosmeticModelMesh[] pushedWeapon = {null};
    final dev.nightclient.renderer.model.CosmeticModelMesh[] pushedArmor = {null};
    final String[] pushedTarget = {""};
    final String[] pushedTrigger = {""};
    final int[] pushedWeaponColor = {0};
    final int[] pushedArmorMask = {0};
    final float[] pushedWeaponScale = {0f};
    final boolean[] pushedHideVanilla = {false};

    PlatformServices platform = new PlatformServices() {
      @Override
      public Path configDirectory() {
        return temp;
      }

      @Override
      public void updateCosmeticModels(
          dev.nightclient.renderer.model.CosmeticModelMesh weaponMesh,
          String weaponTarget,
          int weaponColor,
          float weaponScale,
          boolean weaponFirstPerson,
          boolean weaponThirdPerson,
          dev.nightclient.renderer.model.CosmeticModelMesh armorMesh,
          String armorTrigger,
          int armorSlotMask,
          boolean armorHideVanilla,
          int armorColor,
          float armorScale) {
        pushedWeapon[0] = weaponMesh;
        pushedTarget[0] = weaponTarget;
        pushedWeaponColor[0] = weaponColor;
        pushedWeaponScale[0] = weaponScale;
        pushedArmor[0] = armorMesh;
        pushedTrigger[0] = armorTrigger;
        pushedArmorMask[0] = armorSlotMask;
        pushedHideVanilla[0] = armorHideVanilla;
      }
    };

    CosmeticsManager manager = new CosmeticsManager(
        temp, platform, settings, changedCalls::incrementAndGet, url -> new byte[0],
        new DirectExecutor());

    manager.selectBuiltinWeapon("katana");
    manager.selectWeaponMaterial("netherite");
    manager.selectBuiltinArmor("realistic_iron_armor");
    manager.tick();

    assertEquals("builtin", settings.cosmetics.weaponMode);
    assertEquals("katana", settings.cosmetics.builtinWeapon);
    assertEquals("netherite", settings.cosmetics.weaponMaterial);
    assertEquals("none", settings.cosmetics.armorMode);
    assertNotNull(pushedWeapon[0]);
    assertTrue(pushedWeapon[0].triangleCount >= 200);
    int netheriteMeshId = pushedWeapon[0].meshId;
    assertNull(pushedArmor[0]);
    assertEquals(0, pushedArmorMask[0]);
    assertFalse(pushedHideVanilla[0]);

    String netheriteCardKey = manager.capeThumbnailKey("model:katana");
    assertTrue(netheriteCardKey.contains("model:katana:netherite:"));

    manager.selectWeaponMaterial("emerald");
    settings.cosmetics.weaponScale = 1.25f;
    manager.setWeaponTarget("weapons");
    manager.syncFromSettings();
    manager.tick();

    assertEquals("emerald", settings.cosmetics.weaponMaterial);
    assertNotNull(pushedWeapon[0]);
    assertFalse(netheriteMeshId == pushedWeapon[0].meshId, "Changing material must rebuild/swap sword mesh");
    assertEquals(1.25f, pushedWeaponScale[0], 0.001f);
    assertEquals("weapons", pushedTarget[0]);
    assertNull(pushedArmor[0]);
    assertEquals(0, pushedArmorMask[0]);

    PlayerSkin preview = manager.composePreview(DefaultSkins.classic());
    assertTrue(preview instanceof dev.nightclient.renderer.player.PlayerSkinData);
    dev.nightclient.renderer.player.PlayerSkinData data =
        (dev.nightclient.renderer.player.PlayerSkinData) preview;
    assertEquals(pushedWeapon[0], data.weaponMesh);
    assertNull(data.armorMesh);

    PlayerSkin weaponCard = manager.capeThumbnail("model:katana");
    assertNotNull(weaponCard);
    assertTrue(dev.nightclient.renderer.player.PlayerPreviewRenderer.hasModelCard(weaponCard));
    String emeraldCardKey = manager.capeThumbnailKey("model:katana");
    assertTrue(emeraldCardKey.contains("model:katana:emerald:"));
    assertFalse(netheriteCardKey.equals(emeraldCardKey), "Card preview key must update on material change");
    int[] renderedCard = new int[64 * 86];
    dev.nightclient.renderer.player.PlayerPreviewRenderer.renderCosmeticCard(
        manager.capeThumbnail("model:katana"), 64, 86, renderedCard);
    int opaquePixels = 0;
    for (int px : renderedCard) if ((px >>> 24) > 0) opaquePixels++;
    assertTrue(opaquePixels > 250, "2D weapon card must render visible geometry: " + opaquePixels);

    Path customObj = temp.resolve("my_saber.obj");
    java.nio.file.Files.write(customObj,
        ("o blade\nv 0 0 0\nv 0.1 0 0\nv 0.1 1.4 0\nv 0 1.4 0\nf 1 2 3 4\n")
            .getBytes(StandardCharsets.UTF_8));
    manager.applyCustomWeaponPath(customObj.toString());
    manager.tick();
    assertEquals("custom", settings.cosmetics.weaponMode);
    assertNotNull(pushedWeapon[0]);
    assertEquals(2, pushedWeapon[0].triangleCount);

    manager.resetWeapon();
    manager.resetArmor();
    manager.tick();
    assertNull(pushedWeapon[0]);
    assertNull(pushedArmor[0]);

    manager.selectWeaponMaterial("diamond");
    manager.tick();
    assertEquals("builtin", settings.cosmetics.weaponMode);
    assertEquals("diamond", settings.cosmetics.weaponMaterial);
    assertNotNull(pushedWeapon[0]);
  }

  private static byte[] solidPng(int w, int h, int argb) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    img.setRGB(0, 0, argb);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  private static byte[] mojangCapePng(int w, int h, int argb) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    int fx1 = (w * 11) / 64, fy1 = (h * 17) / 32;
    for (int y = h / 32; y < fy1; y++)
      for (int x = w / 64; x < fx1; x++) img.setRGB(x, y, argb);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }
}
