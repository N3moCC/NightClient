package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import org.junit.jupiter.api.Test;

class ModuleSearchTest {
  private static void render(SkinView view, double t) {
    view.renderMenu(new SkinViewTest.Sink(), 1100, 760, 0, 0, t);
  }

  private static void click(SkinView view, String id, int button) {
    float[] b = view.elementBounds(id);
    assertNotNull(b, "Missing clickable element: " + id);
    assertTrue(view.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, button), id);
    view.mouseReleased(b[0] + b[2] / 2, b[1] + b[3] / 2, button);
  }

  private static void setSearch(SkinView view, String query, double t) {
    view.searchQuery(query);
    render(view, t);
  }

  @Test
  void searchBySettingAndDescriptionAutoExpandsExactNestedStateAndRevertsCleanly() {
    Settings s = new Settings();
    s.language = "ru";
    s.menu.animationSpeed = 1000000f;
    s.atmosphere.mode = "dawn";
    s.tweaksy.fullBright.enabled = false;
    s.hwhelper.timers.enabled = false;
    s.itemScroller.wheel = false;

    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = new SkinView(DefaultSkin.document(), s, actions);
    view.page("all");
    render(view, 0);

    setSearch(view, "protect", 1);
    assertNotNull(view.elementBounds("all-protect"));
    assertNull(view.elementBounds("protect.maskPasswords"),
        "Searching module title directly should not force-expand the card");

    setSearch(view, "fullbright", 2);
    assertNotNull(view.elementBounds("all-tweaksy"));
    assertNotNull(view.elementBounds("toggle.tweaksy.fullBright.enabled"),
        "Tweaks card must auto-expand when searching for FullBright");

    setSearch(view, "brightness level", 3);
    assertNotNull(view.elementBounds("all-tweaksy"));
    assertNotNull(view.elementBounds("slider.tweaksy.fullBright.strength"),
        "Conditional setting matching search must be revealed inside expanded card");
    assertFalse(s.tweaksy.fullBright.enabled, "Search must never mutate user settings");

    setSearch(view, "иней", 4);
    assertNotNull(view.layoutBounds("all-atmosphere"));
    assertNotNull(view.layoutBounds("atmo.snowCover"),
        "Matched mode-gated Atmosphere sub-module must be transiently revealed");
    assertNotNull(view.elementBounds("atmosphere.snowCover.frost"),
        "Nested sub-module accordion must auto-expand and scroll matched slider into view");
    assertEquals("dawn", s.atmosphere.mode, "Search must not overwrite atmosphere.mode");

    setSearch(view, "цвет луны", 5);
    assertNotNull(view.layoutBounds("atmo.mysticSky"));
    assertNotNull(view.elementBounds("atmosphere.mysticSky.moonColor.hex"),
        "3-level nested inline color picker must auto-expand and scroll into view");

    setSearch(view, "лед", 6);
    assertNotNull(view.elementBounds("enchantGlint.preset"),
        "Search should also find choice option labels ('Неоновый лёд') and reveal conditional preset");
    assertNotNull(view.layoutBounds("atmosphere.snowCover.ice"),
        "Russian e/yo normalization should match ice inside snowCover");
    setSearch(view, "снег лед", 6.5);
    assertNotNull(view.elementBounds("atmosphere.snowCover.ice"),
        "Narrowing to 'снег лед' must scroll straight to the 'Лёд' slider");

    setSearch(view, "трапка", 7);
    assertNotNull(view.elementBounds("hwhelper.timers.traps"),
        "HolyWorld Helper must expand, reveal, and scroll matched conditional timer toggle into view");
    assertFalse(s.hwhelper.timers.enabled);

    setSearch(view, "режим колеса", 8);
    assertNotNull(view.elementBounds("itemScroller.wheelMode"),
        "Catalog-driven conditional control must be revealed and scrolled into view");
    assertFalse(s.itemScroller.wheel);

    setSearch(view, "быстрый перенос", 9);
    assertNotNull(view.elementBounds("all-itemScroller"));

    setSearch(view, "иней", 10);
    assertNotNull(view.elementBounds("atmosphere.snowCover.frost"));
    click(view, "atmo.head.snowCover", 0);
    render(view, 11);
    assertNull(view.layoutBounds("atmosphere.snowCover.frost"),
        "User can still manually collapse an auto-expanded sub-module during search");

    setSearch(view, "", 12);
    assertNull(view.layoutBounds("atmosphere.snowCover.frost"));
    assertNull(view.layoutBounds("toggle.tweaksy.fullBright.enabled"));
    assertNull(view.layoutBounds("hwhelper.timers.traps"));
    assertNull(view.layoutBounds("itemScroller.wheelMode"));
    assertEquals(0, actions.changes, "Deep search and auto-expand must never persist settings changes");
    assertTrue(s.interfaceState.expandedPanels.isEmpty(),
        "Search auto-expansion must not pollute persisted expandedPanels");
  }

  @Test
  void multiWordModulePlusSettingQueryAutoExpandsTargetModule() {
    Settings s = new Settings();
    s.language = "ru";
    s.menu.animationSpeed = 1000000f;
    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions());
    view.page("all");
    render(view, 0);

    setSearch(view, "protect пароли", 1);
    assertNotNull(view.elementBounds("all-protect"));
    assertNotNull(view.elementBounds("protect.maskPasswords"),
        "Multi-word module + setting query must expand Protect");

    setSearch(view, "атмосфера вулкан", 2);
    assertNotNull(view.elementBounds("atmosphere.sakuraSky.volcano"),
        "Multi-word query must reveal, expand, and scroll Sakura Sky volcano into view");
  }

  @Test
  void lanternThemeAlsoSupportsDeepSearchAndNestedAutoExpand() throws Exception {
    Settings s = new Settings();
    s.language = "ru";
    s.menu.animationSpeed = 1000000f;
    s.atmosphere.mode = "dawn";
    SkinDocument lantern = DefaultSkin.resolve("lantern", null);
    assertNotNull(lantern);
    SkinView view = new SkinView(lantern, s, new SkinViewTest.Actions());
    view.page("all");
    render(view, 0);

    setSearch(view, "иней", 1);
    assertNotNull(view.elementBounds("atmosphere.snowCover.frost"),
        "Lantern theme must also auto-expand and scroll nested sub-module setting into view");
  }
}
