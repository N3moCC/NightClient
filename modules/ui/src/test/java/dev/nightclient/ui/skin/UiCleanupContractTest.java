package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import org.junit.jupiter.api.Test;

class UiCleanupContractTest {
  private static void click(SkinView view, String id, int button) {
    float[] box = view.elementBounds(id);
    assertNotNull(box, id);
    assertTrue(view.mouseClicked(box[0] + box[2] / 2, box[1] + box[3] / 2, button), id);
    view.mouseReleased(box[0] + box[2] / 2, box[1] + box[3] / 2, button);
  }
  private static int actions(SkinDocument.Node node, String action) {
    int count = action.equals(node.attribute("action", "")) ? 1 : 0;
    for (SkinDocument.Node child : node.children) count += actions(child, action);
    return count;
  }
  @Test
  void HudCardsAreToggleOnlyAndThePageOwnsOneEditorEntry() throws Exception {
    SkinDocument document = DefaultSkin.document();
    new SkinValidator().validate(document);
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView view = new SkinView(document, settings, new SkinViewTest.Actions());
    view.page("hud");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    assertNotNull(view.elementBounds("edit-hud"));
    assertEquals(1, actions(document.menu(), "editor-open"));
    click(view, "module-search", 0);
    for (char c : "food".toCharArray()) view.charTyped(c, 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .1);
    float[] before = view.elementBounds("card.food");
    assertNotNull(before);
    assertNotNull(
        view.elementBounds("expand.food"), "Every module exposes its keybind inside its card");
    click(view, "hud-food", 1);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    assertTrue(view.elementBounds("card.food")[3] > before[3]);
    assertNotNull(view.elementBounds("bind-module.food"));
    assertNull(view.elementBounds("armor.width"));
    assertNull(view.elementBounds("armor.height"));
    boolean enabled = settings.food.enabled;
    click(view, "module-toggle.food", 0);
    assertEquals(!enabled, settings.food.enabled);
  }
  @Test
  void ArmorDimensionsRespectTheThemeCellLayoutAndUniformScale() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.menu.hudScale = 1;
    SkinView view = new SkinView(DefaultSkin.document(), settings, new SkinViewTest.Actions());
    float[] original = view.hudBoundsInstant("armor", 1000, 700, true);
    settings.armor.width = 490;
    settings.armor.height = 480;
    float[] wide = view.hudBoundsInstant("armor", 1000, 700, true);
    assertEquals(96, original[2], .001f);
    assertEquals(31, original[3], .001f);
    assertEquals(490, wide[2], .001f);
    assertEquals(31, wide[3], .001f, "Horizontal cell height is content-derived");
    settings.armor.scale = 2;
    float[] scaled = view.hudBoundsInstant("armor", 1000, 700, true);
    assertEquals(wide[2] * 2, scaled[2], .001f);
    assertEquals(wide[3] * 2, scaled[3], .001f);
    assertEquals(2, scaled[4], .001f);
    settings.armor.horizontal = false;
    float[] vertical = view.hudBoundsInstant("armor", 1000, 700, true);
    settings.armor.width = 80;
    settings.armor.height = 24;
    float[] compact = view.hudBoundsInstant("armor", 1000, 700, true);
    assertEquals(490 * .52f * 2, vertical[2], .001f);
    assertEquals(204, vertical[3], .001f);
    assertEquals(108, compact[2], .001f);
    assertEquals(204, compact[3], .001f);
    assertEquals(2, compact[4], .001f, "All four vertical slots retain the same uniform scale");
  }
  @Test
  void FeaturesWithoutControlsCannotExpandIntoAnEmptyDivider() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView view = new SkinView(DefaultSkin.document(), settings, new SkinViewTest.Actions());
    view.page("player");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    float[] before = view.elementBounds("card.itemScroller");
    assertNotNull(before);
    assertNotNull(
        view.elementBounds("expand.itemScroller"), "Module body exposes its toggle keybind");
    click(view, "player-itemScroller", 1);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .1);
    assertTrue(view.elementBounds("card.itemScroller")[3] > before[3]);
    assertNotNull(view.elementBounds("bind-module.itemScroller"));
  }
  private static boolean hasId(SkinDocument.Node node, String id) {
    if (id.equals(node.id)) return true;
    for (SkinDocument.Node child : node.children)
      if (hasId(child, id)) return true;
    return false;
  }
  @Test
  void unifiedSettingsOwnInlinePaletteAndCenteredProfileFormWithoutTheDeletedGlobalMenu()
      throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinDocument document = DefaultSkin.document();
    SkinView view = new SkinView(document, settings, new SkinViewTest.Actions());
    view.page("settings");
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    for (String removed :
        new String[] {"profile-card", "global-settings", "global-palettes", "palette-popup"})
      assertFalse(hasId(document.menu(), removed),
          removed + " has been removed from the declarative interface");
    assertNotNull(view.elementBounds("option.theme-mode.dark"));
    assertNotNull(view.elementBounds("option.theme-mode.light"));
    click(view, "option.theme-mode.custom", 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .1);
    assertEquals("custom", settings.theme.mode);
    click(view, "custom-background", 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    float[] dialog = view.elementBounds("color-dialog");
    float[] plane = view.elementBounds("color-plane");
    assertNotNull(dialog, "The custom colour control opens a floating colour-picker mini-menu");
    assertNotNull(plane);
    assertTrue(dialog[0] >= 0 && dialog[1] >= 0 && dialog[0] + dialog[2] <= 1000
        && dialog[1] + dialog[3] <= 700);
    click(view, "color-done", 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .25);
    assertNull(view.elementBounds("color-dialog"));
    click(view, "settings-interface", 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .3);
    assertNotNull(view.elementBounds("option.language.en"));
    assertNull(view.elementBounds("custom-background"));
    click(view, "settings-profiles", 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .4);
    click(view, "create-profile", 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .5);
    float[] popup = view.elementBounds("create-profile-modal");
    assertNotNull(popup);
    assertEquals(500, popup[0] + popup[2] / 2, .001f);
    assertEquals(350, popup[1] + popup[3] / 2, .001f);
    assertTrue(popup[0] >= 0 && popup[1] >= 0 && popup[0] + popup[2] <= 1000
        && popup[1] + popup[3] <= 700);
    assertNotNull(view.elementBounds("profile-name"));
    assertNotNull(view.elementBounds("save-profile"));
    assertTrue(view.mouseClicked(0, 0, 0), "Clicking outside dismisses the profile form");
    view.mouseReleased(0, 0, 0);
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .6);
    assertNull(view.elementBounds("create-profile-modal"));
    assertNotNull(view.elementBounds("create-profile"));
  }
}
