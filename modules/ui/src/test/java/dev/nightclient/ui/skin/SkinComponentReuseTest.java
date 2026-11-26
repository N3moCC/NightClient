package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SkinComponentReuseTest {
  private static class StableSink extends SkinViewTest.Sink {
    static final Object METRICS = new Object();
    public Object textMetricsKey() {
      return METRICS;
    }
  }
  private static SkinView view(String content, Settings settings) throws Exception {
    return new SkinView(
        NcParser.parse(
            "hud { layout=absolute; cooldowns(id=sample) } component 'sample' { " + content + " }"),
        settings, new SkinViewTest.Actions());
  }
  private static StableSink draw(SkinView view, double time) {
    StableSink sink = new StableSink();
    view.renderHud(sink, 300, 200, time, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    return sink;
  }
  @Test
  void cachedBindingsObserveSettingsMutationAndKeepAnimationProgress() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView retained = view("panel(id=frame) { width='{{hud.width}}'; height=20px; text "
                             + "'{{hud.width}}' { height=12px } }",
        settings);
    assertTrue(draw(retained, 0).strings().contains("148.0"));
    assertTrue(draw(retained, .1).strings().contains("148.0"));
    settings.hud.width = 199;
    assertTrue(draw(retained, .2).strings().contains("199.0"));
    assertEquals(199, retained.layoutBounds("frame")[2], .001f);
    settings.menu.animationSpeed = 1f;
    settings.hud.width = 250;
    draw(retained, .3);
    float first = retained.layoutBounds("frame")[2];
    draw(retained, .34);
    assertTrue(retained.layoutBounds("frame")[2] >= first);
  }
  @Test
  void clockExpressionsAndUnknownFontMetricsNeverFreeze() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView clock = view("text '{{time}}' { height=12px }", settings);
    assertTrue(draw(clock, 1).strings().contains("1.0"));
    assertTrue(draw(clock, 2).strings().contains("2.0"));
    SkinView sizing =
        view("text 'Width'(id=measured) { height=12px; width='{{= textWidth(\\\"word\\\",12)}}' }",
            settings);
    SkinViewTest.Sink first = new SkinViewTest.Sink() {
      public float textWidth(String value, float size, boolean bold) {
        return 27;
      }
    };
    SkinViewTest.Sink second = new SkinViewTest.Sink() {
      public float textWidth(String value, float size, boolean bold) {
        return 43;
      }
    };
    sizing.renderHud(first, 300, 200, 0, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(27, sizing.layoutBounds("measured")[2], .001f);
    sizing.renderHud(second, 300, 200, .1, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(43, sizing.layoutBounds("measured")[2], .001f);
  }
  @Test
  void equalLeafOutputsStillUseTheCurrentRowsAndVariableContext() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    settings.saturation.strength = 1;
    final java.util.List<dev.nightclient.api.InventorySlot> slots =
        new java.util.ArrayList<dev.nightclient.api.InventorySlot>();
    slots.add(new dev.nightclient.api.InventorySlot(9, "minecraft:stone", "Stone", 2, 0, 0));
    SkinView retained = new SkinView(
        NcParser.parse("hud { cooldowns(id=sample) } component 'sample' { "
                        + "repeat(source=inventoryCells,as=row,key='{{row.slotId}}') { div { let "
                        + "sum='saturation.strength+row.count'; text "
                        + "'{{=vars.sum}}'(if=row.occupied) { width=30px; height=12px } } } }"),
        settings, new SkinViewTest.Actions() {
          public java.util.List<dev.nightclient.api.InventorySlot> inventory() {
            return slots;
          }
        });
    StableSink first = new StableSink();
    retained.renderHud(first, 300, 200, 0, Collections.emptyList(), slots, Collections.emptyList());
    assertTrue(first.strings().contains("3.0"));
    settings.saturation.strength = 2;
    slots.set(0, new dev.nightclient.api.InventorySlot(9, "minecraft:stone", "Stone", 1, 0, 0));
    StableSink second = new StableSink();
    retained.renderHud(
        second, 300, 200, .1, Collections.emptyList(), slots, Collections.emptyList());
    assertTrue(second.strings().contains("3.0"));
    assertFalse(second.strings().contains("4.0"));
  }
  @Test
  void mutableThemeFieldsAreComparedExactlyWhileItsClockMayAdvance() {
    Settings settings = new Settings();
    ThemeSnapshot snapshot = new ThemeSnapshot();
    assertFalse(snapshot.update(settings.theme));
    assertTrue(snapshot.update(settings.theme));
    settings.theme.effects.time = 3;
    assertTrue(snapshot.update(settings.theme));
    settings.theme.effects.grain = true;
    assertFalse(snapshot.update(settings.theme));
    assertTrue(snapshot.update(settings.theme));
    settings.theme.motion.enterDuration += .01f;
    assertFalse(snapshot.update(settings.theme));
    settings.theme.fontFamily = "another";
    assertFalse(snapshot.update(settings.theme));
  }
}
