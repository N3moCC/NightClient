package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Theme;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinResolvedThemeCacheTest {
  @Test
  void resolvedThemesReuseUntilBindingsBaseOrStateChangeAndKeepTheClockLive() throws Exception {
    SkinDocument document =
        NcParser.parse("menu { panel (id=box) { background='{{theme.accent}}'; hover { "
                        + "color='{{theme.text}}' } } }");
    SkinDocument.Node node = document.menu().children.get(0);
    Theme base = Theme.defaults();
    Map<String, String> values = new HashMap<String, String>();
    values.put("background", "#123456");
    Theme first = document.resolvedTheme(node, base, "", values);
    assertSame(first, document.resolvedTheme(node, base, "", values));
    base.effects.time = 19;
    assertSame(first, document.resolvedTheme(node, base, "", values));
    assertEquals(19, first.effects.time);
    values.put("background", "#654321");
    Theme second = document.resolvedTheme(node, base, "", values);
    assertNotSame(first, second);
    assertEquals(0xff654321, second.surface);
    base.backgroundOpacity = .5f;
    Theme third = document.resolvedTheme(node, base, "", values);
    assertNotSame(second, third);
    assertEquals(.5f, third.backgroundOpacity);
    base.glassMix = .4f;
    Theme material = document.resolvedTheme(node, base, "", values);
    assertNotSame(third, material);
    assertEquals(.4f, material.glassMix);
    values.put("color", "#abcdef");
    Theme hover = document.resolvedTheme(node, base, "hover", values);
    assertEquals(0xffabcdef, hover.text);
    assertSame(material, document.resolvedTheme(node, base, "", values));
    document.clearCache();
    assertNotSame(material, document.resolvedTheme(node, base, "", values));
  }
  @Test
  void dynamicThemeInheritanceDoesNotAddParentsPaintBoxToChildStyles() throws Exception {
    SkinDocument document =
        NcParser.parse("theme { glassMix='{{=theme.backgroundOpacity}}' } menu { panel { "
                        + "background='{{theme.accent}}'; text 'label' } }");
    SkinDocument.Node panel = document.menu().children.get(0), label = panel.children.get(0);
    assertTrue(document.themeBindings(label, "").containsKey("glass-mix"));
    assertTrue(document.themeBindings(label, "").containsKey("background"));
    assertFalse(document.styles(label, "").containsKey("background"),
        "A child inherits the theme palette, not its parent's drawing surface");
    Map<String, String> values = new HashMap<String, String>();
    values.put("glass-mix", ".4");
    values.put("background", "#224466");
    Theme child = document.resolvedTheme(label, Theme.defaults(), "", values);
    assertEquals(.4f, child.glassMix);
    assertEquals(0xff224466, child.surface);
  }
  @Test
  void themeCopiesPreserveValuesAndOwnMutableSubObjects() {
    Theme base = Theme.defaults();
    base.accent = 0xff123456;
    base.glassMix = .37f;
    base.effects.time = 8;
    base.effects.grain = true;
    base.motion.duration = .4f;
    Theme copy = base.copy();
    assertEquals(base.accent, copy.accent);
    assertEquals(.37f, copy.glassMix);
    assertEquals(8, copy.effects.time);
    assertEquals(.4f, copy.motion.duration);
    assertNotSame(base.effects, copy.effects);
    assertNotSame(base.motion, copy.motion);
    copy.effects.grain = false;
    copy.motion.duration = .2f;
    assertTrue(base.effects.grain);
    assertEquals(.4f, base.motion.duration);
  }
  @Test
  void reusableFrameMapDoesNotBecomeTheCachedThemeKey() throws Exception {
    SkinDocument document =
        NcParser.parse("menu { panel { background='{{theme.accent}}'; color='{{theme.text}}' } }");
    SkinDocument.Node node = document.menu().children.get(0);
    Theme base = Theme.defaults();
    Map<String, String> scratch = new HashMap<String, String>();
    scratch.put("background", "#123456");
    scratch.put("color", "#abcdef");
    Theme first = document.resolvedTheme(node, base, "", scratch);
    scratch.clear();
    scratch.put("width", "250px");
    scratch.put("color", new String("#abcdef"));
    scratch.put("background", new String("#123456"));
    assertSame(first, document.resolvedTheme(node, base, "", scratch),
        "Only required resolved properties determine the cached theme");
    scratch.put("background", "#654321");
    Theme changed = document.resolvedTheme(node, base, "", scratch);
    assertNotSame(first, changed);
    assertEquals(0xff123456, first.surface);
    assertEquals(0xff654321, changed.surface);
    scratch.clear();
    scratch.put("color", "#abcdef");
    scratch.put("background", "#123456");
    assertSame(first, document.resolvedTheme(node, base, "", scratch),
        "Another repeat instance must not overwrite the previous immutable key");
  }
  @Test
  void alternatingMenuAndHudOverridesReuseTheirOwnThemesAndLiveClocks() throws Exception {
    SkinDocument document = NcParser.parse("menu { panel { background='{{theme.accent}}' } }");
    SkinDocument.Node node = document.menu().children.get(0);
    Theme[] bases = {Theme.defaults(), Theme.defaults(), Theme.defaults(), Theme.defaults()},
            resolved = new Theme[4], staticThemes = new Theme[4];
    Map<String, String> values = new HashMap<String, String>();
    values.put("background", "#123456");
    for (int i = 0; i < bases.length; i++) {
      bases[i].backgroundOpacity = .2f + i * .2f;
      staticThemes[i] = document.theme(node, bases[i], "");
      resolved[i] = document.resolvedTheme(node, bases[i], "", values);
    }
    for (int pass = 0; pass < 4; pass++)
      for (int i = 0; i < bases.length; i++) {
        bases[i].effects.time = pass * 10 + i;
        assertSame(staticThemes[i], document.theme(node, bases[i], ""));
        assertSame(resolved[i], document.resolvedTheme(node, bases[i], "", values));
        assertEquals(bases[i].backgroundOpacity, resolved[i].backgroundOpacity);
        assertEquals(pass * 10 + i, resolved[i].effects.time);
      }
  }
  @Test
  void directNestedThemeMutationIsDetectedWithoutAnInvalidationSignal() throws Exception {
    SkinDocument document = NcParser.parse("menu { panel { color='{{theme.text}}' } }");
    SkinDocument.Node node = document.menu().children.get(0);
    Theme base = Theme.defaults();
    Map<String, String> values = new HashMap<String, String>();
    values.put("color", "#abcdef");
    Theme first = document.resolvedTheme(node, base, "", values);
    base.effects.grainAmount = .0731f;
    Theme effect = document.resolvedTheme(node, base, "", values);
    assertNotSame(first, effect);
    assertEquals(.0731f, effect.effects.grainAmount);
    base.motion.duration = .451f;
    Theme animation = document.resolvedTheme(node, base, "", values);
    assertNotSame(effect, animation);
    assertEquals(.451f, animation.motion.duration);
    base.effects.time = 72;
    assertSame(animation, document.resolvedTheme(node, base, "", values));
    assertEquals(72, animation.effects.time);
  }
}
