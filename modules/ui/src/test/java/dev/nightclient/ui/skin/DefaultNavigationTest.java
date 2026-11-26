package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class DefaultNavigationTest {
  private void click(SkinView v, String id) {
    click(v, id, id.startsWith("expand.") ? 1 : 0);
  }
  private void click(SkinView v, String id, int button) {
    float[] b = v.elementBounds(id);
    assertNotNull(b, id);
    assertTrue(v.mouseClicked(b[0] + b[2] / 2, b[1] + b[3] / 2, button));
    v.mouseReleased(0, 0, button);
  }
  @Test
  void allCategoryIncludesHudModulesAndSupportsSearchingThem() {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    SkinView v = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions());
    v.page("all");
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    assertNotNull(v.layoutBounds("all-armor"), "HUD modules should appear in All category");
    click(v, "module-search");
    for (char c : "armor".toCharArray()) v.charTyped(c, 0);
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    assertNotNull(v.elementBounds("all-armor"));
    assertNull(v.elementBounds("hud-armor"));
    v.page("hud");
    v.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .4);
    assertNotNull(v.elementBounds("hud-armor"));
  }
  @Test
  void oneShippedDocumentKeepsSearchNavigationAndRealFeatureBindings() throws Exception {
    SkinDocument document = DefaultSkin.document();
    new SkinValidator().validate(document);
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = new SkinView(document, settings, actions);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    assertNotNull(view.elementBounds("saturation"));
    assertNull(view.elementBounds("saturation.strength"));
    assertNotNull(view.elementBounds("module-search"));
    click(view, "saturation");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .05);
    assertTrue(settings.saturation.enabled);
    assertNull(view.elementBounds("saturation.strength"));
    click(view, "expand.saturation");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .1);
    assertNotNull(view.elementBounds("saturation.strength"));
    click(view, "expand.saturation");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .15);
    click(view, "nav-player");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .18);
    click(view, "expand.protect");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    assertNotNull(view.elementBounds("protect.replacement"));
    click(view, "nav-camera");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .25);
    click(view, "module-search");
    for (char c : "zoom".toCharArray()) view.charTyped(c, 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .4);
    assertNotNull(view.elementBounds("zoom"));
    assertNull(view.elementBounds("protect"));
    assertEquals(4, actions.changes,
        "Module toggle and expanded panel persistence update saved settings; search and navigation "
        + "stay transient");
    assertEquals(Boolean.FALSE, settings.interfaceState.expandedPanels.get("module.saturation"));
    assertEquals(Boolean.TRUE, settings.interfaceState.expandedPanels.get("module.protect"));
    for (int i = 0; i < 4; i++) view.keyPressed(259, 0, 0);
    view.page("hud");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .6);
    click(view, "module-search");
    for (char c : "food".toCharArray()) view.charTyped(c, 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .7);
    assertNotNull(view.elementBounds("hud-food"));
    assertTrue(new UiCatalog(settings).controls("food").isEmpty());
    assertNull(document.component("editor-target-food"));
  }
  @Test
  void declaredStatesAreTypedBoundedAndActionsCannotCreateUndeclaredState() throws Exception {
    SkinDocument d = NcParser.parse("state { expanded=false; filter=\"\"; offset=0 }\nmenu { "
                                     + "button \"Open\" (id=open,action=ui-toggle:expanded); text "
                                     + "\"Visible\" (if=ui.expanded); input (bind=ui.filter) }");
    new SkinValidator().validate(d);
    SkinView v = new SkinView(d, new Settings(), new SkinViewTest.Actions());
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    v.renderMenu(sink, 500, 400, 0, 0, 0);
    assertFalse(sink.strings().contains("Visible"));
    click(v, "open");
    sink = new SkinViewTest.Sink();
    v.renderMenu(sink, 500, 400, 0, 0, .1);
    assertTrue(sink.strings().contains("Visible"));
    for (String bad : Arrays.asList("state { a=NaN }\nmenu {}",
             "state { a=false }\nmenu { button (action=ui-toggle:missing) }",
             "state { a=false }\nmenu { button (action=ui-set:a:garbage) }"))
      assertThrows(
          java.io.IOException.class, () -> new SkinValidator().validate(NcParser.parse(bad)));
  }
  @Test
  void emptySearchIsScopedToTheActiveCategoryAndDisappearsForMatchingHudModules() {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinViewTest.Actions actions = new SkinViewTest.Actions();
    SkinView view = new SkinView(DefaultSkin.document(), settings, actions);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    click(view, "module-search");
    for (char c : "unmatched-module-query".toCharArray()) view.charTyped(c, 0);
    SkinViewTest.Sink empty = new SkinViewTest.Sink();
    view.renderMenu(empty, 1000, 700, 0, 0, .1);
    assertTrue(empty.strings().contains(I18n.text(settings.language, "ui.no_results")));
    assertNull(view.elementBounds("saturation"));
    view.page("hud");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    click(view, "module-search");
    view.keyPressed(65, 0, 2);
    for (char c : "armor".toCharArray()) view.charTyped(c, 0);
    SkinViewTest.Sink matching = new SkinViewTest.Sink();
    view.renderMenu(matching, 1000, 700, 0, 0, .3);
    assertNotNull(view.elementBounds("hud-armor"));
    assertFalse(matching.strings().contains(I18n.text(settings.language, "ui.no_results")));
    assertEquals(0, actions.changes, "Search never changes the saved module settings");
  }
  @Test
  void defaultTimerTreeRespectsActualOrderCapAndContainsNoFoodWidget() throws Exception {
    {
      Settings s = new Settings();
      s.hud.maxVisible = 1;
      s.menu.animationSpeed = 1000000f;
      SkinView v = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions());
      SkinViewTest.Sink sink = new SkinViewTest.Sink();
      v.renderHud(sink, 1000, 700, 0,
          Arrays.asList(new Cooldown("a", "Later", "minecraft:shield", 8, 10),
              new Cooldown("b", "Soon", "minecraft:ender_pearl", 2, 10)),
          Collections.emptyList(), Collections.emptyList());
      assertTrue(sink.strings().contains("Later"));
      assertFalse(sink.strings().contains("Soon"));
      assertTrue(sink.strings().contains(I18n.text(s.language, "hud.cooldowns.title")));
    }
  }
}
