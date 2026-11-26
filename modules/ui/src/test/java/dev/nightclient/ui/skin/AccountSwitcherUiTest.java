package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.SavedAccount;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.AccountOverlayRenderer;
import dev.nightclient.ui.MenuController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountSwitcherUiTest {
  private static class AccountActions extends SkinViewTest.Actions {
    final List<SavedAccount> list = new ArrayList<SavedAccount>();
    String active = "MainLicense";
    boolean launcherActive = true;
    boolean canReconnect = true;
    int reconnectCount;
    String error = "";

    AccountActions() {
      SavedAccount a = new SavedAccount("00000000000000a1", "FrostBlade99", "Anarchy #1", "");
      list.add(a);
    }

    @Override public List<SavedAccount> savedAccounts() { return new ArrayList<SavedAccount>(list); }
    @Override public String activeAccountName() { return active; }
    @Override public String launcherAccountName() { return "MainLicense"; }
    @Override public boolean isLauncherAccountActive() { return launcherActive; }
    @Override public boolean canReconnectServer() { return canReconnect; }
    @Override public String currentServerLabel() { return "mc.holyworld.ru"; }
    @Override public String accountErrorKey() { return error; }
    @Override public void clearAccountError() { error = ""; }
    @Override public String switchSavedAccount(String id) {
      for (SavedAccount a : list) {
        if (a.id.equals(id)) {
          active = a.name;
          launcherActive = false;
          return "";
        }
      }
      return "";
    }
    @Override public boolean restoreLauncherAccount() {
      active = "MainLicense";
      launcherActive = true;
      return true;
    }
    @Override public String saveAccountDraft(String id, String name, String note, String skinName) {
      String clean = SavedAccount.normalizeNickname(name);
      if (clean == null) {
        error = "accounts.err.invalid_name";
        return error;
      }
      SavedAccount acc = new SavedAccount("00000000000000b2", clean, note, skinName);
      list.add(acc);
      error = "";
      return "";
    }
    @Override public boolean toggleAccountPin(String id) {
      for (SavedAccount a : list) {
        if (a.id.equals(id)) {
          a.pinned = !a.pinned;
          return true;
        }
      }
      return false;
    }
    @Override public boolean deleteSavedAccount(String id) {
      return list.removeIf(a -> a.id.equals(id));
    }
    @Override public String reconnectCurrentServer() {
      reconnectCount++;
      return "";
    }
  }

  private static void click(SkinView v, String id) {
    float[] b = v.elementBounds(id);
    assertNotNull(b, id);
    assertTrue(v.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, 0), id);
    v.mouseReleased(b[0] + b[2] / 2, b[1] + b[3] / 2, 0);
  }

  @Test
  void modernAndClassicLayoutsSwitchCleanlyAndManageAccounts() throws Exception {
    SkinDocument doc = DefaultSkin.document();
    new SkinValidator().validate(doc);

    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    AccountActions actions = new AccountActions();
    SkinView view = new SkinView(doc, settings, actions);

    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    view.openAccountManager(true);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0.1);
    assertTrue(view.isAccountManagerOpen());
    assertNotNull(view.layoutBounds("account-modern-body"));
    assertNotNull(view.elementBounds("account-3d-preview"));
    assertNotNull(view.elementBounds("account-modern-switch"));
    assertNull(view.layoutBounds("account-classic-body"));

    click(view, "account-row-00000000000000a1");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0.2);
    click(view, "account-modern-switch");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0.3);
    assertEquals("FrostBlade99", actions.active);
    assertFalse(actions.launcherActive);

    click(view, "account-mode-classic");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0.4);
    assertEquals("classic", settings.accountSwitcher.layout);
    assertNotNull(view.layoutBounds("account-classic-body"));
    assertNotNull(view.elementBounds("account-classic-switch"));
    assertNull(view.layoutBounds("account-modern-body"));

    click(view, "account-launcher-classic");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0.5);
    click(view, "account-classic-switch");
    assertTrue(actions.launcherActive);
    assertEquals("MainLicense", actions.active);

    MenuController controller = new MenuController(settings, actions);
    controller.onOpen();
    controller.openAccountManagerStandalone();
    SkinViewTest.Sink standaloneSink = new SkinViewTest.Sink();
    controller.render(standaloneSink, 1000, 700, 500, 350, 1.0);
    assertTrue(standaloneSink.strings().contains("MainLicense"));
  }

  @Test
  void overlayPillPositionsHitTestsAndRespectsProtectMasking() {
    Settings settings = new Settings();
    AccountOverlayRenderer renderer = new AccountOverlayRenderer();
    SkinViewTest.Sink sink = new SkinViewTest.Sink();

    renderer.render(sink, 800, 500, 0, 0, settings, "SecretMain", true, null, true);
    assertTrue(renderer.isVisible());
    float[] b = renderer.lastBounds();
    assertNotNull(b);
    assertTrue(b[0] > 500f, "TitleScreen modern auto should default to top-right");
    assertEquals(10f, b[1], 0.01f);
    assertTrue(renderer.hitTest(b[0] + b[2] * 0.5f, b[1] + b[3] * 0.5f));
    assertFalse(renderer.hitTest(20f, 400f));
    assertTrue(sink.strings().contains("SecretMain"));

    settings.protect.enabled = true;
    settings.protect.hideNickname = true;
    settings.protect.replacement = "HiddenPro";
    SkinViewTest.Sink maskedSink = new SkinViewTest.Sink();
    renderer.render(maskedSink, 800, 500, 0, 0, settings, "SecretMain", false, null, false);
    assertTrue(renderer.isVisible());
    assertEquals(8f, renderer.lastBounds()[0], 0.01f);
    assertEquals(6f, renderer.lastBounds()[1], 0.01f);
    assertFalse(maskedSink.strings().contains("SecretMain"));
    assertTrue(maskedSink.strings().contains("HiddenPro"));

    settings.accountSwitcher.multiplayerPosition = "headerRight";
    renderer.render(new SkinViewTest.Sink(), 800, 500, 0, 0, settings, "SecretMain", false, null, false);
    assertTrue(renderer.isVisible());
    assertTrue(renderer.lastBounds()[0] > 500f);

    settings.accountSwitcher.multiplayerScreen = false;
    renderer.render(new SkinViewTest.Sink(), 800, 500, 0, 0, settings, "SecretMain", false, null, false);
    assertFalse(renderer.isVisible());
  }

  @Test
  void classicLayoutDelegatesToNativeVanillaScreenWhenSupported() throws Exception {
    SkinDocument doc = DefaultSkin.document();
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    final int[] vanillaOpens = {0};
    AccountActions actions = new AccountActions() {
      @Override public boolean openVanillaAccountScreen() {
        vanillaOpens[0]++;
        return true;
      }
    };
    SkinView view = new SkinView(doc, settings, actions);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    view.openAccountManager(true);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0.1);
    assertTrue(view.isAccountManagerOpen());

    click(view, "account-mode-classic");
    assertEquals("classic", settings.accountSwitcher.layout);
    assertEquals(1, vanillaOpens[0]);
    assertFalse(view.isAccountManagerOpen(),
        "Modal should yield cleanly when platform opens native NightVanillaAccountScreen");
  }

  @Test
  void allAccountSwitcherTemplateAndUiKeysAreLocalizedWithoutHardcodedLabels() throws Exception {
    java.nio.file.Path root = java.nio.file.Paths.get("").toAbsolutePath();
    while (root != null && !java.nio.file.Files.exists(root.resolve("modules/platform/src/templates"))) {
      root = root.getParent();
    }
    assertNotNull(root, "Could not locate repository root");
    java.nio.file.Path vanillaTpl =
        root.resolve("modules/platform/src/templates/NightVanillaAccountScreen.java.tpl");
    java.nio.file.Path mixinTpl =
        root.resolve("modules/platform/src/templates/ScreenAccountOverlayMixin.java.tpl");
    String vanillaSrc = new String(
        java.nio.file.Files.readAllBytes(vanillaTpl), java.nio.charset.StandardCharsets.UTF_8);
    String mixinSrc = new String(
        java.nio.file.Files.readAllBytes(mixinTpl), java.nio.charset.StandardCharsets.UTF_8);

    assertFalse(vanillaSrc.contains("literal(\"Rnd\")"),
        "Vanilla account form must not hardcode untranslated 'Rnd' button");
    assertTrue(vanillaSrc.contains("accounts.save_btn"));
    assertTrue(vanillaSrc.contains("accounts.cancel_btn"));
    assertTrue(vanillaSrc.contains("accounts.unpin_btn"));
    assertTrue(vanillaSrc.contains("accounts.restore_short_btn"));
    assertTrue(vanillaSrc.contains("accounts.mode_modern_btn"));

    java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"(accounts\\.[a-z0-9_.]+)\"");
    for (String src : new String[] {vanillaSrc, mixinSrc}) {
      java.util.regex.Matcher m = p.matcher(src);
      while (m.find()) {
        String key = m.group(1);
        for (String lang : new String[] {"en", "ru"}) {
          String val = dev.nightclient.i18n.I18n.text(lang, key);
          assertNotNull(val, "Missing " + lang + " key: " + key);
          assertNotEquals(key, val, "Unresolved " + lang + " i18n key: " + key);
          assertFalse(val.trim().isEmpty(), "Empty " + lang + " i18n key: " + key);
        }
      }
    }

    assertEquals("Save", dev.nightclient.i18n.I18n.text("en", "accounts.save_btn"));
    assertEquals("Сохранить", dev.nightclient.i18n.I18n.text("ru", "accounts.save_btn"));
    assertEquals("Cancel", dev.nightclient.i18n.I18n.text("en", "accounts.cancel_btn"));
    assertEquals("Отмена", dev.nightclient.i18n.I18n.text("ru", "accounts.cancel_btn"));
    assertEquals("Unpin", dev.nightclient.i18n.I18n.text("en", "accounts.unpin_btn"));
    assertEquals("Открепить", dev.nightclient.i18n.I18n.text("ru", "accounts.unpin_btn"));
    assertEquals("Login", dev.nightclient.i18n.I18n.text("en", "accounts.quick_switch"));
    assertEquals("Войти", dev.nightclient.i18n.I18n.text("ru", "accounts.quick_switch"));
  }
}
