package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.Keys;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatHudLifecycleTest {
  @TempDir Path directory;
  private static final class Platform implements PlatformServices {
    final Path directory;
    int closed;
    boolean editing;
    int tntSnapshots;
    Platform(Path directory) {
      this.directory = directory;
    }
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
    public void closeMenu() {
      closed++;
    }
    public List<Cooldown> cooldowns(float partialTick) {
      return Collections.emptyList();
    }
    public String menuKeyLabel() {
      return "R SHIFT";
    }
    public void setViewmodelEditing(boolean value) {
      editing = value;
    }
    public boolean leftMainArm() {
      return true;
    }
    public List<dev.nightclient.api.TntView> tnts(float partial, int width, int height) {
      tntSnapshots++;
      return Collections.emptyList();
    }
  }
  private static Canvas canvas() {
    return (Canvas) Proxy.newProxyInstance(ChatHudLifecycleTest.class.getClassLoader(),
        new Class<?>[] {Canvas.class}, (proxy, method, args) -> {
          if (method.getReturnType() == float.class) return 20f;
          if (method.getReturnType() == boolean.class) return false;
          return null;
        });
  }
  private ClientRuntime runtime(Platform platform) throws Exception {
    Constructor<ClientRuntime> constructor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    constructor.setAccessible(true);
    ClientRuntime runtime = constructor.newInstance(platform);
    runtime.settings().menu.animationSpeed = 1000000f;
    return runtime;
  }
  private static void paint(ClientRuntime runtime, Object owner) {
    runtime.renderChatHudChrome(owner, canvas(), 640, 400, -1, -1);
  }
  private static void toggle(ClientRuntime runtime, Object owner) {
    float[] box = runtime.chatHudElementBounds("chat-hud-edit");
    assertNotNull(box);
    assertTrue(runtime.chatHudMouseClicked(owner, box[0] + box[2] / 2, box[1] + box[3] / 2, 0));
    assertTrue(runtime.chatHudMouseReleased(owner, box[0] + box[2] / 2, box[1] + box[3] / 2, 0));
  }

  @Test
  void sameChatOwnsEditorAndEscapeReturnsInputWithoutClosingScreen() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    Object chat = new Object();
    try {
      paint(runtime, chat);
      assertFalse(runtime.isChatHudEditing());
      assertFalse(runtime.chatHudUsesBackdrop(),
          "A solid chat affordance must not capture the scene just because the menu uses glass");
      assertFalse(runtime.chatHudCharTyped(chat, 'a'));
      assertFalse(runtime.chatHudMouseClicked(chat, 100, 100, 0));
      toggle(runtime, chat);
      assertTrue(runtime.isChatHudEditing());
      assertTrue(platform.editing);
      runtime.renderChatHudEditor(chat, canvas(), 640, 400, 100, 100);
      assertTrue(platform.tntSnapshots > 0);
      assertTrue(runtime.chatHudCharTyped(chat, 'a'));
      assertTrue(runtime.chatHudKeyPressed(chat, Keys.ENTER, 0));
      assertTrue(runtime.chatHudKeyPressed(chat, Keys.ESCAPE, 0));
      assertFalse(runtime.isChatHudEditing());
      assertFalse(platform.editing);
      assertEquals(0, platform.closed, "Leaving an overlay must not close or replace native chat");
      assertFalse(runtime.chatHudCharTyped(chat, 'a'));
      assertFalse(runtime.chatHudKeyPressed(chat, Keys.ENTER, 0));
      assertTrue(runtime.leftMainArm());
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void staleChatRemovalCannotCloseANewerChatEditor() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    Object first = new Object(), second = new Object();
    try {
      paint(runtime, first);
      toggle(runtime, first);
      assertTrue(runtime.isChatHudEditing());
      paint(runtime, second);
      assertFalse(runtime.isChatHudEditing());
      assertFalse(platform.editing);
      toggle(runtime, second);
      runtime.chatHudClosed(first);
      assertTrue(runtime.isChatHudEditing());
      runtime.chatHudClosed(second);
      assertFalse(runtime.isChatHudEditing());
      assertFalse(platform.editing);
      assertEquals(0, platform.closed);
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void chatButtonHitRegionComesFromReplaceableNcInsteadOfNativeCoordinates() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    Object chat = new Object();
    try {
      Path file = directory.resolve("nightclient/skin.nc");
      Files.createDirectories(file.getParent());
      Files.write(file,
          ("component \"chat.hud\" { button (id = \"chat-hud-edit\", action = \"chat-hud-edit\") { "
           + "position = absolute; left = 37px; top = 29px; width = 31px; height = 23px } }")
              .getBytes(StandardCharsets.UTF_8));
      runtime.settings().skin.enabled = true;
      runtime.reloadSkin();
      paint(runtime, chat);
      assertArrayEquals(
          new float[] {37, 29, 31, 23}, runtime.chatHudElementBounds("chat-hud-edit"), .001f);
      toggle(runtime, chat);
      assertTrue(runtime.isChatHudEditing());
      runtime.close();
      assertFalse(runtime.isChatHudEditing());
      assertEquals(0, platform.closed);
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void disconnectReleasesEditorAndPendingPointerWithoutAffectingNativeChat() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    Object chat = new Object();
    try {
      paint(runtime, chat);
      toggle(runtime, chat);
      assertTrue(runtime.chatHudMouseClicked(chat, 100, 100, 0));
      runtime.setWorldActive(false);
      assertFalse(runtime.isChatHudEditing());
      assertFalse(platform.editing);
      paint(runtime, chat);
      assertFalse(runtime.chatHudMouseReleased(chat, 100, 100, 0));
      assertEquals(0, platform.closed);
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void noWorldFrameCannotCloseAnEditorOwnedByTheOrdinaryMenu() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    try {
      runtime.menu().openEditor();
      runtime.setWorldActive(false);
      assertTrue(runtime.menu().isEditor());
      assertTrue(platform.editing);
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void customChatGlassRequestsItsOwnBackdropWhenOrdinaryMenuBlurIsDisabled() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    Object chat = new Object();
    try {
      Path file = directory.resolve("nightclient/skin.nc");
      Files.createDirectories(file.getParent());
      Files.write(file,
          ("component \"chat.hud\" { button (id = \"chat-hud-edit\", action = \"chat-hud-edit\") { "
           + "blur = true; blurRadius = 12px; width = 22px; height = 22px } }")
              .getBytes(StandardCharsets.UTF_8));
      runtime.settings().skin.enabled = true;
      runtime.settings().menu.vanillaBlur = false;
      runtime.reloadSkin();
      paint(runtime, chat);
      assertFalse(runtime.isChatHudEditing());
      assertTrue(runtime.chatHudUsesBackdrop());
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void disablingChatHudEditorSettingHidesChromeAndExitsActiveEditor() throws Exception {
    Platform platform = new Platform(directory);
    ClientRuntime runtime = runtime(platform);
    Object chat = new Object();
    try {
      assertTrue(runtime.settings().menu.chatHudEditor, "chatHudEditor must default to enabled");
      paint(runtime, chat);
      assertNotNull(runtime.chatHudElementBounds("chat-hud-edit"));
      toggle(runtime, chat);
      assertTrue(runtime.isChatHudEditing());
      assertTrue(platform.editing);

      runtime.settings().menu.chatHudEditor = false;
      assertFalse(runtime.isChatHudEditing());
      assertFalse(platform.editing);
      assertFalse(runtime.chatHudUsesBackdrop());
      assertNull(runtime.chatHudElementBounds("chat-hud-edit"));
      assertFalse(runtime.chatHudMouseClicked(chat, 100, 100, 0));
      assertFalse(runtime.chatHudKeyPressed(chat, Keys.ESCAPE, 0));
      assertFalse(runtime.chatHudCharTyped(chat, 'a'));

      Object secondChat = new Object();
      paint(runtime, secondChat);
      assertNull(runtime.chatHudElementBounds("chat-hud-edit"));
      assertFalse(runtime.isChatHudEditing());
      assertEquals(0, platform.closed);
    } finally {
      runtime.shutdown();
    }
  }
}
