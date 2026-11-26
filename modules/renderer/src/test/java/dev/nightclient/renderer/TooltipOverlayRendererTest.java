package dev.nightclient.renderer;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.config.Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TooltipOverlayRendererTest {

  private static final class RecordingCanvas implements Canvas {
    int inventoryItems;
    int rects;
    int texts;

    @Override public void rect(float x, float y, float w, float h, int c) { rects++; }
    @Override public void roundedRect(float x, float y, float w, float h, float r, int c) { rects++; }
    @Override public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {}
    @Override public void gradientRect(float x, float y, float w, float h, int a, int b) {}
    @Override public void line(float x1, float y1, float x2, float y2, float t, int c) {}
    @Override public void circle(float cx, float cy, float r, int c) {}
    @Override public void text(String v, float x, float y, float s, int c, boolean b) { texts++; }
    @Override public float textWidth(String v, float s, boolean b) { return v == null ? 0 : v.length() * s * 0.55f; }
    @Override public void pushClip(float x, float y, float w, float h) {}
    @Override public void popClip() {}
    @Override public void item(String id, float x, float y, float s, float a) {}
    @Override public void inventoryItem(InventorySlot slot, float x, float y, float s, float a) { inventoryItems++; }
    @Override public void backdrop(float x, float y, float w, float h, float r, float b, int q, float o) {}
    @Override public void texture(String id, float x, float y, float w, float h, float r, float t, int c) {}
  }

  @Test
  void hoveringEmptyAndFullShulkerAndBundleRendersWithoutThrowingOnCacheSlots() {
    Theme theme = new Theme();

    TooltipOverlayRenderer.ContainerPeek emptyShulker = new TooltipOverlayRenderer.ContainerPeek(
        "Shulker Box", "minecraft:purple_shulker_box", 350, 0xff9333ea, false, false, false, 0, 27,
        0f, Collections.<TooltipOverlayRenderer.SlotEntry>emptyList());
    RecordingCanvas c1 = new RecordingCanvas();
    TooltipOverlayRenderer.renderContainerPeek(c1, theme, emptyShulker, 200, 150, 110, 32, 640, 360, true);
    assertEquals(1, c1.inventoryItems, "header shulker icon (slot 350) must render cleanly");
    assertTrue(c1.texts >= 2);

    List<TooltipOverlayRenderer.SlotEntry> entries = new ArrayList<TooltipOverlayRenderer.SlotEntry>();
    for (int i = 0; i < 27; i++) {
      InventorySlot inv = new InventorySlot(
          300 + i, "minecraft:totem_of_undying", "Totem", i == 0 ? 1 : 64, i == 1 ? 20 : 0,
          i == 1 ? 100 : 0, i % 3 == 0);
      entries.add(new TooltipOverlayRenderer.SlotEntry(i, inv, i == 0 ? "" : "64"));
    }
    TooltipOverlayRenderer.ContainerPeek fullShulker = new TooltipOverlayRenderer.ContainerPeek(
        "PvP Kit", "minecraft:red_shulker_box", 350, 0xffef4444, false, false, true, 27, 27, 0f,
        entries);
    RecordingCanvas c2 = new RecordingCanvas();
    TooltipOverlayRenderer.renderContainerPeek(c2, theme, fullShulker, 200, 150, 110, 32, 640, 360, true);
    assertEquals(28, c2.inventoryItems, "header icon + all 27 shulker slots must render cleanly");

    TooltipOverlayRenderer.ContainerPeek bundle = new TooltipOverlayRenderer.ContainerPeek(
        "Bundle", "minecraft:bundle", 350, 0xffd49a6a, true, true, false, 5, 5, 0.75f,
        entries.subList(0, 5));
    RecordingCanvas c3 = new RecordingCanvas();
    TooltipOverlayRenderer.renderContainerPeek(c3, theme, bundle, 200, 150, 110, 32, 640, 360, false);
    assertEquals(6, c3.inventoryItems);
  }
}
