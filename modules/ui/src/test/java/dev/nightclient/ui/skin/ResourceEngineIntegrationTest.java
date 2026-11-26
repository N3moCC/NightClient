package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import org.junit.jupiter.api.Test;

class ResourceEngineIntegrationTest {
  @Test
  void bootstrapDefaultsNeverOverrideSavedPaletteAndEffects() throws Exception {
    Settings settings = new Settings();
    settings.theme.accent = 0xffab1234;
    settings.theme.backgroundOpacity = .31f;
    settings.theme.effects.backgroundBlur = false;
    settings.theme.effects.grain = true;
    SkinDocument document = DefaultSkin.document();
    assertEquals(settings.theme.accent, document.theme(document.menu(), settings.theme, "").accent);
    assertEquals(
        .31f, document.theme(document.menu(), settings.theme, "").backgroundOpacity, .0001f);
    assertFalse(document.theme(document.menu(), settings.theme, "").effects.backgroundBlur);
    SkinDocument user = DefaultSkin.resolve(NcParser.parse("theme { accent = #123456 }"));
    assertEquals(0xff123456, user.theme(user.menu(), settings.theme, "").accent);
    assertEquals(.31f, user.theme(user.menu(), settings.theme, "").backgroundOpacity, .0001f);
  }
  @Test
  void customHudMarkupControlsRealEditorBoundsWithoutJavaLayoutChanges() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinDocument document = DefaultSkin.resolve(
        NcParser.parse("hud(auto-layout=false) { armor(id=armor,source=game) }\ncomponent "
                        + "\"hud.armor\" { panel (id = armor-frame) { position = absolute; x = "
                        + "17px; y = 29px; width = 123px; height = 47px; hudScale = 1.7 } }"));
    SkinView view = new SkinView(document, settings, new SkinViewTest.Actions());
    assertArrayEquals(
        new float[] {17, 29, 123, 47, 1.7f}, view.hudBounds("armor", 640, 400, 1, true), .001f);
    view.renderHudEditor(new SkinViewTest.Sink(), 640, 400, 1);
    assertArrayEquals(new float[] {17, 29, 123, 47}, view.layoutBounds("armor-frame"), .001f);
  }
  @Test
  void computedVariablesUseLocalBoxAndReadonlyExpressionsKeepBooleanPipes() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinDocument document =
        NcParser.parse("menu { width = 240px; height = 100px; layout = absolute; rect (id = "
                        + "quarter, if = \"{{= false || true}}\") { let quarter = \"boxWidth/4\"; "
                        + "width = \"{{= vars.quarter}}\"; height = 10px } }");
    new SkinValidator().validate(document);
    SkinView view = new SkinView(document, settings, new SkinViewTest.Actions());
    view.renderMenu(new SkinViewTest.Sink(), 640, 400, 0, 0, 1);
    assertEquals(60, view.layoutBounds("quarter")[2], .001f);
  }
}
