package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.PotionView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PotionsCompletenessTest {
  @Test
  void allEffectsRemainVisibleRegardlessOfConfiguredHeightAndFormat() {
    List<PotionView> effects = new ArrayList<PotionView>();
    for (int i = 0; i < 24; i++)
      effects.add(
          new PotionView("example:effect_" + i, "Effect " + i, 0, 0x55cc88, 120, i % 2 == 0));

    for (String format : new String[] {"list", "compact", "pills"}) {
      Settings settings = new Settings();
      settings.language = "en";
      settings.potions.enabled = true;
      settings.potions.height = 32;
      settings.potions.offsetY = 0;
      settings.potions.format = format;
      SkinView view = new SkinView(DefaultSkin.document(), settings, new SkinViewTest.Actions() {
        @Override
        public List<PotionView> potions() {
          return effects;
        }
      });
      for (int[] viewport : new int[][] {{1000, 700}, {420, 240}}) {
        SkinViewTest.Sink sink = new SkinViewTest.Sink();
        assertTrue(view.renderHudWidgetInstant("potions", sink, viewport[0], viewport[1], false));
        List<String> labels = sink.strings();
        for (PotionView effect : effects)
          assertTrue(
              labels.contains(effect.name), format + " dropped " + effect.name + ": " + labels);
        float[] bounds = view.hudBoundsInstant("potions", viewport[0], viewport[1], false);
        assertTrue(bounds[1] >= 0, format + " top is visible");
        assertTrue(bounds[1] + bounds[3] <= viewport[1] + .01f, format + " fits the viewport");
        assertEquals(0, sink.clips);
      }
    }
  }

  @Test
  void vanillaPotionsSupportsHorizontalVerticalAndOptionalTimeAndLevel() {
    List<PotionView> effects = new ArrayList<PotionView>();
    effects.add(new PotionView("minecraft:speed", "Speed", 1, 0x7cafc6, 82, true));
    effects.add(new PotionView("minecraft:strength", "Strength", 0, 0x932423, 9, true));

    Settings s = new Settings();
    s.language = "en";
    s.potions.enabled = true;
    s.potions.format = "vanilla";
    s.potions.horizontal = true;
    s.potions.time = true;
    s.potions.level = true;
    s.validate();
    assertEquals("vanilla", s.potions.format);

    SkinView view = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public List<PotionView> potions() {
        return effects;
      }
    });
    SkinViewTest.Sink hWithTime = new SkinViewTest.Sink();
    assertTrue(view.renderHudWidgetInstant("potions", hWithTime, 1000, 700, false));
    float[] hb = view.hudBoundsInstant("potions", 1000, 700, false);
    assertTrue(hb[2] > hb[3], "horizontal vanilla row should be wider than tall");
    assertTrue(hWithTime.strings().contains("1:22"), "time enabled must render duration");
    assertTrue(hWithTime.strings().contains("II"), "level enabled must render roman level badge");
    assertFalse(hWithTime.strings().contains("Potions"), "vanilla mode hides panel title");

    s.potions.time = false;
    SkinView viewNoTime = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public List<PotionView> potions() {
        return effects;
      }
    });
    SkinViewTest.Sink hNoTime = new SkinViewTest.Sink();
    assertTrue(viewNoTime.renderHudWidgetInstant("potions", hNoTime, 1000, 700, false));
    float[] hbNoTime = viewNoTime.hudBoundsInstant("potions", 1000, 700, false);
    assertTrue(hbNoTime[3] < hb[3], "disabling time shrinks vanilla horizontal height");
    assertFalse(hNoTime.strings().contains("1:22"), "disabling time must hide duration");

    s.potions.horizontal = false;
    s.potions.time = true;
    SkinView viewVert = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public List<PotionView> potions() {
        return effects;
      }
    });
    SkinViewTest.Sink vSink = new SkinViewTest.Sink();
    assertTrue(viewVert.renderHudWidgetInstant("potions", vSink, 1000, 700, false));
    float[] vb = viewVert.hudBoundsInstant("potions", 1000, 700, false);
    assertTrue(vb[3] > hb[3], "vertical vanilla column should be taller than horizontal row");
    assertTrue(vSink.strings().contains("1:22"), "vertical mode with time shows duration");
    assertTrue(vSink.strings().contains("9s"), "vertical mode shows second effect duration");

    s.potions.time = false;
    SkinView viewVertNoTime = new SkinView(DefaultSkin.document(), s, new SkinViewTest.Actions() {
      @Override
      public List<PotionView> potions() {
        return effects;
      }
    });
    float[] vbNoTime = viewVertNoTime.hudBoundsInstant("potions", 1000, 700, false);
    assertTrue(vbNoTime[2] < vb[2], "disabling time shrinks vanilla vertical width to 24px slot");

    UiCatalog catalog = new UiCatalog(s);
    assertNotNull(catalog.control("potions.horizontal"));
    assertTrue(catalog.control("potions.format").options.contains("vanilla"));
  }
}
