package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.SavedAccount;
import dev.nightclient.config.Settings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountManagerTest {
  private static final class FakePlatform implements PlatformServices {
    final Path dir;
    String launcher = "RealLicense";
    String active = "RealLicense";
    UUID activeUuid = UUID.randomUUID();
    boolean launcherActive = true;
    boolean canReconnect = true;
    boolean inCombat;
    int reconnectCalls;

    FakePlatform(Path dir) {
      this.dir = dir;
    }

    @Override public Path configDirectory() { return dir; }
    @Override public String ownNickname() { return active; }
    @Override public String launcherNickname() { return launcher; }
    @Override public boolean isLauncherSessionActive() { return launcherActive; }
    @Override public boolean switchOfflineSession(String nickname, UUID offlineUuid) {
      active = nickname;
      activeUuid = offlineUuid;
      launcherActive = false;
      return true;
    }
    @Override public boolean restoreLauncherSession() {
      active = launcher;
      launcherActive = true;
      return true;
    }
    @Override public boolean canReconnectServer() { return canReconnect; }
    @Override public String currentServerLabel() { return "mc.holyworld.ru"; }
    @Override public boolean isInCombat() { return inCombat; }
    @Override public boolean reconnectCurrentServer() {
      reconnectCalls++;
      return true;
    }
  }

  private static final class DirectExecutor extends AbstractExecutorService {
    private boolean shutdown;
    @Override public void shutdown() { shutdown = true; }
    @Override public List<Runnable> shutdownNow() { shutdown = true; return java.util.Collections.emptyList(); }
    @Override public boolean isShutdown() { return shutdown; }
    @Override public boolean isTerminated() { return shutdown; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    @Override public void execute(Runnable command) { if (!shutdown) command.run(); }
  }

  @Test
  void managesOfflineAccountsSwitchesRestoresAndRespectsSafeCtLeave(@TempDir Path dir) {
    FakePlatform platform = new FakePlatform(dir);
    Settings settings = new Settings();
    settings.safeCtLeave.enabled = true;
    int[] changed = {0};

    AccountManager manager = new AccountManager(
        dir, platform, settings, () -> changed[0]++, url -> { throw new IOException("offline"); },
        new DirectExecutor(), new Random(7L));
    try {
      assertTrue(manager.isLauncherActive());
      assertEquals("RealLicense", manager.launcherNickname());

      assertEquals("accounts.err.invalid_name", manager.saveDraft("", "x", "", ""));
      assertEquals("", manager.saveDraft("", "TwinkOne", "Anarchy #1", ""));
      assertEquals("accounts.err.duplicate", manager.saveDraft("", "twinkone", "", ""));
      assertEquals("", manager.saveDraft("", "TwinkTwo", "Farm", ""));

      List<SavedAccount> list = manager.accounts();
      assertEquals(2, list.size());

      SavedAccount two = list.stream().filter(a -> a.name.equals("TwinkTwo")).findFirst().get();
      assertTrue(manager.togglePin(two.id));
      assertEquals("TwinkTwo", manager.accounts().get(0).name);

      assertEquals("", manager.switchSaved(two.id));
      assertFalse(manager.isLauncherActive());
      assertEquals("TwinkTwo", manager.activeNickname());
      assertEquals(SavedAccount.offlineUuid("TwinkTwo"), platform.activeUuid);
      assertEquals(1, changed[0]);

      platform.inCombat = true;
      assertEquals("accounts.err.combat", manager.reconnect());
      assertEquals(0, platform.reconnectCalls);

      platform.inCombat = false;
      assertEquals("", manager.reconnect());
      assertEquals(1, platform.reconnectCalls);

      assertEquals("", manager.quickSwitch("QuickAlt99"));
      assertEquals("QuickAlt99", manager.activeNickname());
      assertEquals(3, manager.accounts().size());

      assertTrue(manager.restoreLauncher());
      assertTrue(manager.isLauncherActive());
      assertEquals("RealLicense", manager.activeNickname());

      assertTrue(manager.delete(two.id));
      assertEquals(2, manager.accounts().size());
    } finally {
      manager.shutdown();
    }
  }

  @Test
  void fetchesAndCachesPublicMojangSkinPreviewWithoutNetwork(@TempDir Path dir) throws Exception {
    FakePlatform platform = new FakePlatform(dir);
    Settings settings = new Settings();
    byte[] png = solidSkinPng(64, 64, 0x33, 0x99, 0xcc);
    String texturesJson =
        "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/demo\"}}}";
    String b64 = Base64.getEncoder().encodeToString(texturesJson.getBytes(StandardCharsets.UTF_8));
    String profileJson = "{\"properties\":[{\"name\":\"textures\",\"value\":\"" + b64 + "\"}]}";

    AccountManager manager = new AccountManager(dir, platform, settings, () -> {}, url -> {
      if (url.contains("api.mojang.com")) return "{\"id\":\"0123456789abcdef0123456789abcdef\"}".getBytes(StandardCharsets.UTF_8);
      if (url.contains("sessionserver.mojang.com")) return profileJson.getBytes(StandardCharsets.UTF_8);
      if (url.contains("textures.minecraft.net")) return png;
      throw new IOException("unexpected " + url);
    }, new DirectExecutor(), new Random(1L));
    try {
      manager.faceFor("DemoSkinUser");
      int[] face = manager.faceFor("DemoSkinUser");
      assertEquals(64, face.length);
      assertEquals(0xff3399cc, face[0]);
      assertNotNull(manager.previewFor("DemoSkinUser"));
    } finally {
      manager.shutdown();
    }
  }

  private static byte[] solidSkinPng(int w, int h, int r, int g, int b) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    for (int y = 0; y < h; y++) {
      raw.write(0);
      for (int x = 0; x < w; x++) {
        raw.write(r);
        raw.write(g);
        raw.write(b);
        raw.write(255);
      }
    }
    Deflater def = new Deflater();
    def.setInput(raw.toByteArray());
    def.finish();
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    while (!def.finished()) compressed.write(buf, 0, def.deflate(buf));
    def.end();

    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
    writeInt(ihdr, w);
    writeInt(ihdr, h);
    ihdr.write(new byte[] {8, 6, 0, 0, 0});
    writeChunk(png, "IHDR", ihdr.toByteArray());
    writeChunk(png, "IDAT", compressed.toByteArray());
    writeChunk(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data)
      throws IOException {
    writeInt(out, data.length);
    byte[] tag = type.getBytes(StandardCharsets.US_ASCII);
    out.write(tag);
    out.write(data);
    CRC32 crc = new CRC32();
    crc.update(tag);
    crc.update(data);
    writeInt(out, (int) crc.getValue());
  }

  private static void writeInt(ByteArrayOutputStream out, int v) {
    out.write((v >>> 24) & 0xff);
    out.write((v >>> 16) & 0xff);
    out.write((v >>> 8) & 0xff);
    out.write(v & 0xff);
  }
}
