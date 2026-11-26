package dev.nightclient.runtime;

import dev.nightclient.api.Canvas;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.ui.MenuController;
import dev.nightclient.ui.UiActions;
import dev.nightclient.ui.skin.SkinView;
import java.util.Collections;

final class ChatHudController {
  private final Settings settings;
  private final UiActions actions;
  private final MenuController menu;
  private Object owner;
  private SkinDocument document;
  private SkinView chrome;
  private boolean pointerCaptured;

  ChatHudController(Settings settings, UiActions actions, MenuController menu) {
    this.settings = settings;
    this.actions = actions;
    this.menu = menu;
  }

  private boolean enabled() {
    return settings.menu == null || settings.menu.chatHudEditor;
  }

  private void owner(Object next) {
    if (next == null) throw new IllegalArgumentException("Chat owner is required");
    if (owner != next) {
      close(owner);
      owner = next;
    }
  }

  private SkinView chrome() {
    SkinDocument current = actions.skinDocument();
    if (chrome == null || document != current) {
      document = current;
      chrome = new SkinView(current, settings, actions);
      chrome.host(new SkinView.Host() {
        public boolean action(String action) {
          if (!enabled() || !"chat-hud-edit".equals(action)) return false;
          if (editing())
            menu.leaveEditor();
          else
            menu.openEditor();
          return true;
        }

        public java.util.Map<String, Object> context() {
          return Collections.<String, Object>singletonMap("chatEditorActive", editing());
        }
      });
    }
    return chrome;
  }

  boolean editing() {
    if (!enabled()) {
      if (owner != null) close(owner);
      return false;
    }
    return owner != null && menu.isEditor();
  }

  boolean usesBackdrop() {
    if (!enabled()) return false;
    SkinDocument current = actions.skinDocument();
    return editing() ? current.usesBackdrop(settings.theme)
                     : current.usesComponentBackdrop("chat.hud", settings.theme);
  }

  void renderEditor(
      Object next, Canvas canvas, int width, int height, float mouseX, float mouseY, double time) {
    if (!enabled()) {
      close(owner);
      return;
    }
    owner(next);
    if (editing()) menu.renderEditorOverlay(canvas, width, height, mouseX, mouseY, time);
  }

  void renderChrome(
      Object next, Canvas canvas, int width, int height, float mouseX, float mouseY, double time) {
    if (!enabled()) {
      close(owner);
      return;
    }
    owner(next);
    SkinView view = chrome();
    view.beginFrame(mouseX, mouseY, time);
    view.renderComponent("chat.hud", canvas, 0, 0, width, height, time, null);
    view.finishFrame(canvas, width, height);
    if (editing() && view.hasInteractiveHit(mouseX, mouseY))
      actions.hoverViewmodel(Float.NaN, Float.NaN);
  }

  boolean mouseClicked(Object next, double x, double y, int button) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    if (chrome().mouseClicked((float) x, (float) y, button)) {
      pointerCaptured = true;
      return true;
    }
    if (!editing()) return false;

    menu.mouseClicked(x, y, button);
    pointerCaptured = true;
    return true;
  }

  boolean mouseDragged(Object next, double x, double y, int button, double dx, double dy) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    if (editing()) {
      menu.mouseDragged(x, y, button, dx, dy);
      return true;
    }
    if (chrome != null && chrome.mouseDragged((float) x, (float) y, button, dx, dy)) return true;
    return pointerCaptured;
  }

  boolean mouseReleased(Object next, double x, double y, int button) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    boolean used = pointerCaptured;
    pointerCaptured = false;
    if (chrome != null) used |= chrome.mouseReleased(x, y, button);
    if (editing()) {
      menu.mouseReleased(x, y, button);
      return true;
    }
    return used;
  }

  boolean mouseScrolled(Object next, double x, double y, double amount, int modifiers) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    if (editing()) {
      menu.mouseScrolled(x, y, amount, modifiers);
      return true;
    }
    return chrome != null && chrome.mouseScrolled((float) x, (float) y, amount);
  }

  boolean keyPressed(Object next, int key, int modifiers) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    if (!editing()) return false;
    menu.keyPressed(key, modifiers);
    return true;
  }

  boolean keyReleased(Object next, int key, int modifiers) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    if (!editing()) return false;
    menu.keyReleased(key, modifiers);
    return true;
  }

  boolean charTyped(Object next, int codePoint) {
    if (!enabled()) {
      close(owner);
      return false;
    }
    owner(next);
    if (!editing()) return false;
    menu.charTyped(codePoint);
    return true;
  }

  float[] elementBounds(String id) {
    if (!enabled()) return null;
    return chrome == null ? null : chrome.elementBounds(id);
  }

  void close() {
    close(owner);
  }

  void close(Object old) {
    if (owner == null || owner != old) return;
    if (menu.isEditor()) menu.leaveEditor();
    owner = null;
    chrome = null;
    document = null;
    pointerCaptured = false;
  }
}
