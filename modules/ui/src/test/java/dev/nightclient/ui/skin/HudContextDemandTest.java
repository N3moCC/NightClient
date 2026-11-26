package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.NcParser;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HudContextDemandTest {
  @Test
  void HiddenHudWidgetsDoNotFetchGameDataOrMenuMetadata() {
    final int[] requests = new int[7];
    SkinViewTest.Actions actions = new SkinViewTest.Actions() {
      @Override
      public List<String> profiles() {
        requests[0]++;
        return Collections.emptyList();
      }
      @Override
      public String keyBindingLabel(String target) {
        requests[1]++;
        return "";
      }
      @Override
      public boolean canUndo() {
        requests[2]++;
        return false;
      }
      @Override
      public List<InventorySlot> armor() {
        requests[3]++;
        return Collections.emptyList();
      }
      @Override
      public List<PotionView> potions() {
        requests[4]++;
        return Collections.emptyList();
      }
      @Override
      public TargetView target() {
        requests[5]++;
        return null;
      }
      @Override
      public String saveStatus() {
        requests[6]++;
        return "saved";
      }
    };
    Settings settings = new Settings();
    SkinView view = new SkinView(DefaultSkin.document(), settings, actions);
    java.util.Arrays.fill(requests, 0);
    for (int frame = 0; frame < 5; frame++)
      view.renderHud(new SkinViewTest.Sink(), 1000, 700, frame / 60d, Collections.emptyList(),
          Collections.emptyList(), Collections.emptyList());
    assertArrayEquals(new int[7], requests);
  }
  @Test
  void CustomHudStillResolvesMenuMetadataAndChangesLanguageOnTheNextFrame() throws Exception {
    final int[] calls = {0};
    SkinViewTest.Actions actions = new SkinViewTest.Actions() {
      @Override
      public List<String> profiles() {
        calls[0]++;
        return Collections.singletonList("duel");
      }
      @Override
      public String gameVersion() {
        return "26.3";
      }
    };
    Settings settings = new Settings();
    settings.language = "en";
    settings.menu.animationSpeed = 1000000f;
    SkinView view =
        new SkinView(NcParser.parse("hud { text '{{doneLabel}} / {{gameVersion}}' { height=20px } "
                                     + "text '{{profilesEmpty}}' { height=20px } }"),
            settings, actions);
    SkinViewTest.Sink first = new SkinViewTest.Sink();
    view.renderHud(first, 1000, 700, 1, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertTrue(first.strings().contains("Done / 26.3"));
    assertTrue(first.strings().contains("false"));
    assertEquals(1, calls[0]);
    settings.language = "ru";
    SkinViewTest.Sink second = new SkinViewTest.Sink();
    view.renderHud(second, 1000, 700, 2, Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());
    assertTrue(second.strings().contains("Готово / 26.3"));
    assertEquals(1, calls[0], "Profiles retain their existing invalidation lifecycle");
  }
}
