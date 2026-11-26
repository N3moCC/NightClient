package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkinContextAliasTest {
  @Test
  void siblingsKeepTheirOwnDimensionsAroundABoxIndependentChild() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    SkinView view = new SkinView(
        NcParser.parse("menu { let scale='1'; layout=absolute; panel { layout=absolute; "
                        + "width=80px; height=60px; rect { width=10px; height=10px; fill=#ffffff } "
                        + "text '{{boxWidth}}' { y=12px; width=33px; height=12px } text "
                        + "'{{boxHeight}}' { y=30px; width=40px; height=17px } } }"),
        settings, new SkinViewTest.Actions());
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    view.renderMenu(sink, 200, 100, 190, 90, 1);
    assertTrue(sink.strings().contains("33.0"));
    assertTrue(sink.strings().contains("17.0"));
  }
  @Test
  void hoverOnlyBindingsStillHaveTheirOwnBox() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    final List<Integer> fills = new ArrayList<Integer>();
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { let scale='1'; layout=absolute; rect { width=30px; height=20px; fill=#ff0000; "
            + "hover { fill='{{= boxWidth>50 ? \\\"#ff0000\\\" : \\\"#00ff00\\\"}}' } } }"),
        settings, new SkinViewTest.Actions());
    SkinViewTest.Sink sink = new SkinViewTest.Sink() {
      @Override
      public void roundedRect(float x, float y, float w, float h, float radius, int color) {
        fills.add(color);
        super.roundedRect(x, y, w, h, radius, color);
      }
    };
    view.renderMenu(sink, 200, 100, 4, 4, 1);
    assertTrue(fills.contains(0xff00ff00));
  }
}
