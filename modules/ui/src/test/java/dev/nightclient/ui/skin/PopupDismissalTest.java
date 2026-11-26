package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Keys;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.SkinParser;
import dev.nightclient.skin.NcParser;
import dev.nightclient.ui.MenuController;
import org.junit.jupiter.api.Test;

class PopupDismissalTest {
  @Test
  void outsideClickDismissesOnlyTheTopPopupAndNeverActivatesWhatIsBehind() throws Exception {
    SkinDocument doc = NcParser.parse(
        "state { outer=true; inner=true } menu { x=0px; y=0px; anchorX=0; anchorY=0; width=320px; "
        + "height=220px; layout=absolute; button 'Behind' "
        + "(id=behind,action='toggle:saturation.enabled') { width=40px; height=20px } "
        + "panel(id=outer,if='ui.outer',dismiss-action='ui-set:outer:false') { position=overlay; "
        + "x=50px; y=50px; width=200px; height=120px; zIndex=10 } "
        + "panel(id=inner,if='ui.inner',dismiss-action='ui-set:inner:false') { position=overlay; "
        + "x=100px; y=75px; width=80px; height=60px; zIndex=20 } }");
    new SkinValidator().validate(doc);
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView view = new SkinView(doc, settings, new SkinViewTest.Actions());
    SkinViewTest.Sink canvas = new SkinViewTest.Sink();
    view.renderMenu(canvas, 320, 220, -1, -1, 0);
    assertTrue(view.mouseClicked(60, 60, 0));
    view.mouseReleased(60, 60, 0);
    view.renderMenu(canvas, 320, 220, -1, -1, 1);
    assertNull(view.layoutBounds("inner"));
    assertNotNull(view.layoutBounds("outer"));
    assertFalse(settings.saturation.enabled);
    assertTrue(view.mouseClicked(10, 10, 0));
    view.mouseReleased(10, 10, 0);
    view.renderMenu(canvas, 320, 220, -1, -1, 2);
    assertNull(view.layoutBounds("outer"));
    assertFalse(
        settings.saturation.enabled, "The dismissal click must not activate the underlying module");
    assertTrue(view.mouseClicked(10, 10, 0));
    assertTrue(settings.saturation.enabled);
  }

  @Test
  void escapeBlursInputThenDismissesPopupBeforeClosingTheMenu() throws Exception {
    final SkinDocument doc = NcParser.parse(
        "state { opened=true } menu { x=0px; y=0px; anchorX=0; anchorY=0; width=320px; "
        + "height=220px; layout=absolute; "
        + "panel(id=dialog,if='ui.opened',dismiss-action='ui-set:opened:false') { "
        + "position=overlay; x=50px; y=50px; width=160px; height=80px; "
        + "input(id=name,bind=protect.replacement) { width=100px; height=20px } } }");
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.skin.enabled = true;
    SkinViewTest.Actions actions = new SkinViewTest.Actions() {
      @Override
      public SkinDocument skinDocument() {
        return doc;
      }
    };
    MenuController menu = new MenuController(settings, actions);
    menu.onOpen();
    SkinViewTest.Sink canvas = new SkinViewTest.Sink();
    menu.render(canvas, 640, 400, -1, -1, 0);
    float[] input = menu.elementBounds("name");
    assertNotNull(input);
    menu.mouseClicked(input[0] + 5, input[1] + 5, 0);
    menu.mouseReleased(input[0] + 5, input[1] + 5, 0);
    assertTrue(menu.isTextFocused());
    assertTrue(menu.keyPressed(Keys.ESCAPE, 0));
    assertFalse(menu.isTextFocused());
    assertEquals(0, actions.closed);
    menu.render(canvas, 640, 400, -1, -1, 1);
    assertNotNull(menu.elementBounds("name"));
    assertTrue(menu.keyPressed(Keys.ESCAPE, 0));
    menu.render(canvas, 640, 400, -1, -1, 2);
    assertNull(menu.elementBounds("name"));
    assertEquals(0, actions.closed);
    assertTrue(menu.keyPressed(Keys.ESCAPE, 0));
    assertEquals(1, actions.closed);
  }

  @Test
  void dismissalActionsUseTheSameBoundedDeclarativeValidationAsButtons() throws Exception {
    new SkinValidator().validate(SkinParser.parse(
        "<nightclient><menu><panel dismiss-action='close'/></menu></nightclient>"));
    assertThrows(java.io.IOException.class,
        ()
            -> new SkinValidator().validate(
                NcParser.parse("menu { panel(dismiss-action='execute:remote') {} }")));
    StringBuilder oversized = new StringBuilder();
    for (int i = 0; i < 513; i++) oversized.append('x');
    assertThrows(java.io.IOException.class,
        () -> NcParser.parse("menu { panel(dismiss-action='" + oversized + "') {} }"));
    assertThrows(java.io.IOException.class,
        ()
            -> SkinParser.parse("<nightclient><menu><panel dismiss-action='" + oversized
                + "'/></menu></nightclient>"));
  }
}
