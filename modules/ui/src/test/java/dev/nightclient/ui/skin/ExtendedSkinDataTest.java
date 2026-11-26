package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.*;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExtendedSkinDataTest {
  static final class Actions extends SkinViewTest.Actions {
    public List<InventorySlot> armor() {
      return Collections.singletonList(
          new InventorySlot(100, "minecraft:iron_helmet", "Helmet", 1, 12, 100));
    }
    public List<PotionView> potions() {
      return Collections.singletonList(new PotionView("speed", "Speed", 1, 0x3388ee, 90, true));
    }
    public VitalsView vitals() {
      return new VitalsView(13, 4.5f, true);
    }
    public TargetView target() {
      return new TargetView(12, "fixture", "Opponent", 14, 20, 3, 4, true);
    }
  }
  @Test
  void fullHudReplacementCanBindEveryNewSnapshotWithoutCallingTheBuiltinRenderer()
      throws Exception {
    SkinDocument document = NcParser.parse(
        "hud { repeat (source = armor, as = a) { text \"{{a.name}} {{a.damage}}\" }; repeat "
        + "(source = potions, as = p) { text \"{{p.name}} {{p.amplifier}}\" }; repeat (source = "
        + "target, as = t) { text \"{{t.name}} {{t.health}}\" }; text \"{{foodData.hunger}} "
        + "{{foodData.saturation}}\"; text \"{{targetData.absorption}}\" (if = targetVisible) }");
    new SkinValidator().validate(document);
    Settings settings = new Settings();
    SkinView view = new SkinView(document, settings, new Actions());
    SkinViewTest.Sink c = new SkinViewTest.Sink();
    assertTrue(view.renderHud(
        c, 640, 400, 1, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
    assertTrue(c.strings().contains("Helmet 12"));
    assertTrue(c.strings().contains("Speed 1"));
    assertTrue(c.strings().contains("Opponent 14.0"));
    assertTrue(c.strings().contains("13 4.5"));
    assertTrue(c.strings().contains("3.0"));
    assertEquals(0, c.clips);
  }
  @Test
  void aLocallyPlacedArmorWidgetDoesNotReuseTheGlobalHudOffsets() throws Exception {
    Settings settings = new Settings();
    settings.armor.enabled = true;
    settings.armor.offsetX = 999;
    settings.armor.offsetY = 999;
    SkinDocument document = NcParser.parse(
        "hud { armor { position = absolute; x = 20; y = 30; width = 200; height = 60 } }");
    SkinView view = new SkinView(document, settings, new Actions());
    SkinViewTest.Sink c = new SkinViewTest.Sink();
    view.renderHud(
        c, 640, 400, 1, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertEquals(1, c.stacks.size());
    assertEquals(999, settings.armor.offsetX);
    assertEquals(999, settings.armor.offsetY);
    assertEquals(0, c.clips);
  }
}
