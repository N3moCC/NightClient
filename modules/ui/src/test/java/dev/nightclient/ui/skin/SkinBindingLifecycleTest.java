package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import org.junit.jupiter.api.Test;

class SkinBindingLifecycleTest {
  private static final class Actions extends SkinViewTest.Actions {
    final Settings settings;
    float undoStrength = .75f;
    Actions(Settings settings) {
      this.settings = settings;
    }
    @Override
    public void checkpoint() {
      super.checkpoint();
      undoStrength = settings.saturation.strength;
    }
    @Override
    public void undo() {
      settings.saturation.strength = undoStrength;
    }
  }
  private static final String PICKER =
      "component \"color-picker\" { column { width=250px; height=190px; "
      + "color-plane (id=picker-plane,bind=\"{{colorBind}}\") { height=60px }; "
      + "hue-strip (id=picker-hue,bind=\"{{colorBind}}\") { height=20px }; "
      + "alpha-strip (id=picker-alpha,bind=\"{{colorBind}}\") { height=20px }; "
      + "input (id=picker-hex,bind=\"{{colorBind}}\",format=hex) { height=30px }; "
      + "button \"Done\" (id=picker-done,action=color-close) { height=30px } } }\n";
  private static SkinView view(String source, Settings settings, Actions actions) throws Exception {
    settings.menu.animationSpeed = 1000000f;
    SkinDocument document = NcParser.parse(source);
    new SkinValidator().validate(document);
    return new SkinView(document, settings, actions);
  }
  private static SkinViewTest.Sink render(SkinView view) {
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    assertTrue(view.renderMenu(sink, 800, 700, 0, 0, 1));
    return sink;
  }
  private static float[] click(SkinView view, String id) {
    render(view);
    float[] bounds = view.elementBounds(id);
    assertNotNull(bounds, id);
    assertTrue(view.mouseClicked(bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2, 0), id);
    view.mouseReleased(0, 0, 0);
    return bounds;
  }
  private static void editPicker(SkinView view, Actions actions) {
    click(view, "picker-plane");
    assertTrue(view.keyPressed(262, 0, 1));
    click(view, "picker-hue");
    assertTrue(view.keyPressed(262, 0, 0));
    click(view, "picker-alpha");
    assertTrue(view.keyPressed(263, 0, 0));
    click(view, "picker-hex");
    assertTrue(view.keyPressed(65, 0, 2));
    actions.clipboard = "#2288ccaa";
    assertTrue(view.keyPressed(86, 0, 2));
    click(view, "picker-done");
  }
  @Test
  void localControlsUpdatePresentationWithoutSavingOrReplacingSettingsUndo() throws Exception {
    Settings settings = new Settings();
    settings.saturation.strength = 1.7f;
    Actions actions = new Actions(settings);
    SkinView view =
        view("state { expanded=false; filter=\"\"; offset=0; mode=\"one\"; secondary=false }\n"
                + "menu { padding=8px; gap=2px; "
                + "toggle (id=local-toggle,bind=ui.expanded) { height=34px }; "
                + "switch (bind=ui.secondary) { height=34px }; "
                + "input (id=local-input,bind=ui.filter) { height=34px }; "
                + "slider (id=local-slider,bind=ui.offset,min=0,max=10,step=1) { height=34px }; "
                + "choice (id=local-choice,bind=ui.mode,options=\"one|two\") { height=34px }; "
                + "color (id=local-color,bind=ui.brushColor) { height=34px }; "
                + "text \"{{ui.expanded}} {{ui.filter}} {{ui.offset|fixed:1}} {{ui.mode}} "
                  + "{{ui.secondary}}\"; "
                + "text \"{{ui.brushColor}}\" }\n"
                + "component \"element.switch\" { button (id=mounted-switch,action=activate) { "
                  + "height=34px } }\n"
                + PICKER,
            settings, actions);
    click(view, "local-toggle");
    click(view, "mounted-switch");
    click(view, "local-input");
    view.charTyped('x', 0);
    view.charTyped('y', 0);
    float[] slider = click(view, "local-slider");
    assertTrue(view.mouseClicked(slider[0] + slider[2] / 2, slider[1] + slider[3] / 2, 0));
    assertTrue(view.mouseDragged(slider[0] + slider[2] - 1, slider[1] + slider[3] / 2, 0, 1, 0));
    view.mouseReleased(0, 0, 0);
    assertTrue(view.keyPressed(269, 0, 0));
    click(view, "local-choice");
    click(view, "local-color");
    editPicker(view, actions);
    SkinViewTest.Sink sink = render(view);
    assertTrue(sink.strings().contains("true xy 10.0 two true"), sink.strings().toString());
    assertFalse(sink.strings().contains("-1"), "The local brush color must actually change");
    assertEquals(0, actions.changes);
    assertEquals(0, actions.checkpoints);
    assertEquals(1.7f, settings.saturation.strength, .0001f);
    actions.undo();
    assertEquals(.75f, settings.saturation.strength, .0001f,
        "Local controls must preserve the previous settings Undo");
  }
  @Test
  void settingsControlsStillCheckpointAndSaveThroughPointerKeyboardAndComponents()
      throws Exception {
    Settings settings = new Settings();
    Actions actions = new Actions(settings);
    SkinView view = view("menu { padding=8px; gap=2px; "
            + "toggle (id=setting-toggle,bind=protect.enabled) { height=34px }; "
            + "switch (bind=inventory.enabled) { height=34px }; "
            + "input (id=setting-input,bind=protect.replacement) { height=34px }; "
            + "slider (id=setting-slider,bind=saturation.strength,min=0,max=2,step=.1) { "
              + "height=34px }; "
            + "choice (id=setting-choice,bind=target.style,options=\"card|zakohp\") { height=34px "
              + "}; "
            + "color (id=setting-color,bind=theme.accent) { height=34px } }\n"
            + "component \"element.switch\" { button (id=mounted-switch,action=activate) { "
              + "height=34px } }\n"
            + PICKER,
        settings, actions);
    for (String id : new String[] {"setting-toggle", "mounted-switch", "setting-choice"}) {
      int changes = actions.changes, checkpoints = actions.checkpoints;
      click(view, id);
      assertEquals(changes + 1, actions.changes, id);
      assertEquals(checkpoints + 1, actions.checkpoints, id);
    }
    assertTrue(settings.protect.enabled);
    assertTrue(settings.inventory.enabled);
    assertEquals("zakohp", settings.target.style);
    int changes = actions.changes, checkpoints = actions.checkpoints;
    click(view, "setting-input");
    view.keyPressed(65, 0, 2);
    view.charTyped('X', 0);
    assertEquals("X", settings.protect.replacement);
    assertTrue(actions.changes > changes);
    assertEquals(checkpoints + 1, actions.checkpoints);
    click(view, "setting-slider");
    float beforeKey = settings.saturation.strength;
    changes = actions.changes;
    checkpoints = actions.checkpoints;
    assertTrue(view.keyPressed(262, 0, 0));
    assertTrue(settings.saturation.strength > beforeKey);
    assertEquals(changes + 1, actions.changes);
    assertEquals(checkpoints + 1, actions.checkpoints);
    actions.undo();
    assertEquals(beforeKey, settings.saturation.strength, .0001f);
    click(view, "setting-color");
    changes = actions.changes;
    checkpoints = actions.checkpoints;
    editPicker(view, actions);
    assertEquals(0xaa2288cc, settings.theme.accent);
    assertTrue(actions.changes > changes);
    assertTrue(actions.checkpoints > checkpoints);
  }
  @Test
  void localBrushEditingStillCheckpointsAndSavesTheActualCrosshairStroke() throws Exception {
    Settings settings = new Settings();
    settings.crosshair.colorMode = "pixels";
    Actions actions = new Actions(settings);
    SkinView view = view("menu { hue-strip (id=brush,bind=ui.brushColor) { height=20px }; "
            + "color-plane (id=brush-plane,bind=ui.brushColor) { height=40px }; "
            + "pixel-grid (id=pixels) { width=155px; height=155px } }",
        settings, actions);
    click(view, "brush");
    click(view, "brush-plane");
    assertEquals(0, actions.changes);
    assertEquals(0, actions.checkpoints);
    settings.crosshair.pattern.clear();
    click(view, "pixels");
    assertEquals(1, actions.changes);
    assertEquals(1, actions.checkpoints);
    int center = settings.crosshair.pattern.size / 2;
    assertNotEquals(0, settings.crosshair.pattern.pixel(center, center));
  }
}
