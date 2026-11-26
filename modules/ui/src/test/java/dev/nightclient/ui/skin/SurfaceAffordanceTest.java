package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurfaceAffordanceTest {
  static final class Sink extends SkinViewTest.Sink {
    final List<float[]> shapes = new ArrayList<float[]>();
    @Override
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      super.roundedRect(x, y, w, h, r, c);
      shapes.add(new float[] {x, y, w, h, r, c});
    }
    @Override
    public void rect(float x, float y, float w, float h, int c) {
      super.rect(x, y, w, h, c);
      shapes.add(new float[] {x, y, w, h, 0, c});
    }
  }
  @Test
  void colourSwatchUsesDeclaredCornersAndKeepsAlphaPreviewInsideItsBounds() throws Exception {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    s.theme.accent = 0x803366cc;
    SkinView v =
        new SkinView(NcParser.parse("component 'well' { color-swatch(bind=theme.accent) { x=10px; "
                                     + "y=12px; width=12px; height=10px; borderRadius=1.25px } }"),
            s, new SkinViewTest.Actions());
    Sink sink = new Sink();
    v.beginFrame(-1, -1, 0);
    v.renderComponent("well", sink, 0, 0, 80, 60, 0, null);
    assertTrue(sink.shapes.stream().anyMatch(
        a -> a[0] == 10 && a[1] == 12 && a[2] == 12 && a[3] == 10 && a[4] == 1.25f));
    for (float[] a : sink.shapes) {
      assertTrue(a[0] >= 10 && a[1] >= 12 && a[0] + a[2] <= 22 && a[1] + a[3] <= 22);
    }
  }
  @Test
  void popupFlipsBesideTriggerWhenRequestedSideWouldCrossViewport() throws Exception {
    Settings s = new Settings();
    s.menu.animationSpeed = 1000000f;
    SkinView v = new SkinView(
        NcParser.parse(
            "menu { layout=absolute; button(id=trigger) { x=150px; y=35px; width=30px; height=20px "
            + "} panel(id=popup,anchor=trigger) { position=overlay; anchorSide=right; "
            + "anchorAlign=start; anchorGap=4px; width=60px; height=40px } }"),
        s, new SkinViewTest.Actions());
    v.renderMenu(new Sink(), 200, 100, -1, -1, 0);
    assertArrayEquals(new float[] {86, 35, 60, 40}, v.layoutBounds("popup"), .01f);
  }
}
