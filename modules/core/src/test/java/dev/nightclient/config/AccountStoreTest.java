package dev.nightclient.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountStoreTest {
  @Test
  void offlineUuidMatchesVanillaOfflinePlayerFormulaAndNicknameRules() {
    UUID expected =
        UUID.nameUUIDFromBytes("OfflinePlayer:FrostBlade99".getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, SavedAccount.offlineUuid("FrostBlade99"));
    assertEquals(expected.toString().replace("-", ""), SavedAccount.undashedUuid(expected));
    assertEquals(expected.toString().substring(0, 8), SavedAccount.shortUuid(expected));

    assertTrue(SavedAccount.isValidNickname("abc"));
    assertTrue(SavedAccount.isValidNickname("Frost_Blade_1234"));
    assertFalse(SavedAccount.isValidNickname("ab"));
    assertFalse(SavedAccount.isValidNickname("TooLongNickname17"));
    assertFalse(SavedAccount.isValidNickname("Bad Name"));
    assertFalse(SavedAccount.isValidNickname("НикКириллица"));

    Random rng = new Random(42L);
    for (int i = 0; i < 64; i++) {
      String generated = SavedAccount.generateRandomNickname(rng);
      assertTrue(SavedAccount.isValidNickname(generated), generated);
    }
  }

  @Test
  void storeRoundTripsDeduplicatesRecoversAndStaysIsolatedFromConfigProfiles(@TempDir Path dir)
      throws Exception {
    try (AccountStore store = new AccountStore(dir);
         ConfigStore configStore = new ConfigStore(dir)) {
      SavedAccount first = new SavedAccount("", "FrostBlade99", "HolyWorld #3", "Notch");
      first.pinned = true;
      first.useCount = 4;
      first.lastUsedAt = 123456L;
      SavedAccount duplicate = new SavedAccount("", "frostblade99", "Dup", "");
      SavedAccount second = new SavedAccount("", "SolarWisp", "Farm", "");

      store.saveNow(Arrays.asList(first, duplicate, second));
      assertTrue(Files.isRegularFile(store.file()));

      List<SavedAccount> loaded = store.load();
      assertEquals(2, loaded.size());
      assertEquals("FrostBlade99", loaded.get(0).name);
      assertEquals("HolyWorld #3", loaded.get(0).note);
      assertEquals("Notch", loaded.get(0).skinName);
      assertTrue(loaded.get(0).pinned);
      assertEquals(4, loaded.get(0).useCount);
      assertEquals("SolarWisp", loaded.get(1).name);

      Settings settings = new Settings();
      String exported = configStore.encode(settings);
      assertFalse(exported.contains("FrostBlade99"));
      assertFalse(exported.contains("SolarWisp"));
      assertEquals("modern", settings.accountSwitcher.layout);
      assertTrue(settings.accountSwitcher.enabled);

      Files.write(store.file(), "{not valid json".getBytes(StandardCharsets.UTF_8));
      List<SavedAccount> recovered = store.load();
      assertTrue(recovered.isEmpty());
      assertEquals("recovered", store.status());
    }
  }
}
