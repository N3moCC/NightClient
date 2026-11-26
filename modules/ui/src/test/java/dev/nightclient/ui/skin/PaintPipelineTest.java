package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaintPipelineTest {
  @Test
  void structuralHudRecipesKeepBoundEnterMotionAndChildTransforms() throws Exception {
    Settings settings = new Settings();
    SkinView view = new SkinView(
        NcParser.parse("hud { layout=absolute; column { width=100px; height=25px; "
                        + "animateEnter=true; enterOffsetX='{{offset}}'; enterDuration=1s; "
                        + "easing=linear; text 'child' { height=12px; padding=0px } } }"),
        settings, new SkinViewTest.Actions());
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public Map<String, Object> context() {
        return Collections.<String, Object>singletonMap("offset", 20);
      }
    });
    SkinViewTest.Sink initial = new SkinViewTest.Sink(), middle = new SkinViewTest.Sink(),
                      settled = new SkinViewTest.Sink();
    view.renderHud(initial, 150, 100, 0, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    view.renderHud(middle, 150, 100, .5, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    view.renderHud(settled, 150, 100, 1.1, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(20, initial.text.get(0).x - settled.text.get(0).x, .001f);
    assertEquals(10, middle.text.get(0).x - settled.text.get(0).x, .001f);
    assertEquals(0, initial.clips);
    assertEquals(0, middle.clips);
    assertEquals(0, settled.clips);
  }
  @Test
  void itemThemeFastPathKeepsCurrentResourceIds() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    final java.util.List<String> drawn = new java.util.ArrayList<String>();
    final java.util.Map<String, Object> state = new java.util.HashMap<String, Object>();
    state.put("margin", 40);
    state.put("resource", "minecraft:stick");
    SkinViewTest.Sink sink = new SkinViewTest.Sink() {
      @Override
      public void item(String id, float x, float y, float size, float alpha) {
        drawn.add(id);
        super.item(id, x, y, size, alpha);
      }
    };
    SkinView view =
        new SkinView(NcParser.parse("hud { item(src='{{resource}}') { position=absolute; x=5px; "
                                     + "y=0px; width=10px; height=10px } }"),
            settings, new SkinViewTest.Actions());
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public Map<String, Object> context() {
        return state;
      }
    });
    view.renderHud(sink, 150, 100, 0, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    state.put("resource", "minecraft:diamond");
    view.renderHud(sink, 150, 100, 1, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    state.put("resource", "not an id");
    view.renderHud(sink, 150, 100, 2, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(java.util.Arrays.asList("minecraft:stick", "minecraft:diamond"), drawn);
    assertEquals(0, sink.clips);
  }
  @Test
  void structuralTagsWithAnyStateSurfaceContinuePaintingThatSurface() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    final int[] fills = {0};
    SkinViewTest.Sink sink = new SkinViewTest.Sink() {
      @Override
      public void roundedRect(float x, float y, float w, float h, float radius, int color) {
        if (color == 0xff123456) fills[0]++;
        super.roundedRect(x, y, w, h, radius, color);
      }
    };
    SkinView view = new SkinView(
        NcParser.parse(
            "hud { column { width=80px; height=20px; background=#123456; opacity=100%; "
            + "surfaceOpacity=100%; borderWidth=0px; text 'visible' { height=12px } } }"),
        settings, new SkinViewTest.Actions());
    view.renderHud(sink, 150, 100, 0, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertEquals(1, fills[0]);
    assertTrue(sink.strings().contains("visible"));
    assertEquals(0, sink.clips);
  }
}
