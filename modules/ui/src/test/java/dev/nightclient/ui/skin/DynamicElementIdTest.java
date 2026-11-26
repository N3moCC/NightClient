package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import org.junit.jupiter.api.Test;

class DynamicElementIdTest {
  @Test
  void repeatedCardsWithChildrenHaveIndependentIdsInheritedStylesAndActions() throws Exception {
    String source = "style id protect { color=#ff1122 }\nmenu { repeat (source=modules, as=module) "
                    + "{ button (id=\"{{module.id}}\", action=\"open-module:{{module.id}}\") "
                    + "{height=34px; text \"{{module.label}}\"} } }";
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    SkinView v = new SkinView(NcParser.parse(source), s, new SkinViewTest.Actions());
    final java.util.Map<String, Integer> colours = new java.util.HashMap<String, Integer>();
    SkinViewTest.Sink sink = new SkinViewTest.Sink() {
      @Override
      public void textTracked(
          String value, float x, float y, float size, int colour, boolean bold, float tracking) {
        colours.put(value, colour);
        super.textTracked(value, x, y, size, colour, bold, tracking);
      }
    };
    v.renderMenu(sink, 700, 1600, 0, 0, 0);
    assertEquals(0xffff1122, colours.get("Protect"));
    assertNotEquals(0xffff1122, colours.get("Zoom"));
    float[] p = v.elementBounds("protect"), z = v.elementBounds("zoom");
    assertNotNull(p);
    assertNotNull(z);
    assertNotEquals(p[1], z[1]);
    assertTrue(v.mouseClicked(p[0] + 10, p[1] + 10, 0));
    v.renderMenu(sink, 700, 1600, 0, 0, 1);
    assertEquals("player", v.page(), "Opening Protect follows its declared category");
    assertEquals(0xffff1122, colours.get("Protect"));
    assertNotEquals(0xffff1122, colours.get("Zoom"));
  }
}
