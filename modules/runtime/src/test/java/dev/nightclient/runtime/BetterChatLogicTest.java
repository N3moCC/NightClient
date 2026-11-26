package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BetterChatLogicTest {
  @Test
  void formatsTimestampsAndCleansMessagesForCopy() {
    String hm = BetterChatLogic.formatTimestamp(0L, false);
    assertTrue(hm.matches("\\[\\d{2}:\\d{2}\\] "));
    String hms = BetterChatLogic.formatTimestamp(0L, true);
    assertTrue(hms.matches("\\[\\d{2}:\\d{2}:\\d{2}\\] "));

    String raw = "§8[§712:34:56§8] §c[Анархия] §fИгрок купил предмет §8[§7x4§8]";
    assertEquals("[Анархия] Игрок купил предмет", BetterChatLogic.cleanForCopy(raw));
    assertEquals(4, BetterChatLogic.extractStackCount(raw));
  }

  @Test
  void stacksIdenticalMessagesWithinWindowIgnoringTimestampsAndCounters() {
    BetterChatLogic.DuplicateTracker tracker = new BetterChatLogic.DuplicateTracker(5);
    assertEquals(1, tracker.record("§aВы получили 100 монет", "a").count);
    assertEquals(1, tracker.record("§7[12:00] Другое сообщение", "b").count);
    assertEquals(2, tracker.record("§8[§712:01§8] §aВы получили 100 монет", "c").count);
    assertEquals(3, tracker.record("§aВы получили 100 монет §8[§7x2§8]", "d").count);

    tracker.clear();
    assertEquals(1, tracker.record("§aВы получили 100 монет", "e").count);
  }

  @Test
  void decaysSlideOffsetSmoothlyToZero() {
    float cur = 12f;
    for (int i = 0; i < 60; i++) {
      cur = BetterChatLogic.advanceSlideOffset(cur, 0.016f, 1.0f);
    }
    assertEquals(0f, cur, 1e-4f);
  }

  @Test
  void hudAnchorAndCardBoundsContainFormattedTextAtAnyVanillaScale() {
    float[] anchor = BetterChatLogic.computeHudAnchor(0f, 1f, 4f, -40f, 1.0f, 960, 540);
    assertEquals(4f, anchor[0], 0.01f);
    assertEquals(500f, anchor[1], 0.01f);
    assertEquals(1.0f, anchor[2], 0.001f);

    float[] rounded = BetterChatLogic.computeCardBounds(
        anchor[0], anchor[1], 1.0f, 0.7, 320, 460, 4, 9, false, true, 960, 540);
    float textRight = rounded[4] + 460f * 0.7f * 1.0f;
    assertTrue(rounded[0] + rounded[2] >= textRight + 4f,
        "Rounded card right edge must contain widest formatted line even when vanillaScale < 1");

    float[] compact = BetterChatLogic.computeCardBounds(
        anchor[0], anchor[1], 1.25f, 1.0, 320, 260, 3, 10, true, true, 960, 540);
    float compactTextRight = compact[4] + 260f * 1.25f;
    assertTrue(compact[0] + compact[2] >= compactTextRight + 4f,
        "Compact card must contain wide formatted text with padding");
    assertTrue(compact[1] + compact[3] <= 540f, "Card must stay within screen height");

    double[] back = BetterChatLogic.screenToVanillaChat(
        compact[4] + 100.0 * 1.25, compact[5] - 18.0 * 1.25,
        compact[4], compact[5], 1.25f, 4.0f, 500.0f);
    assertEquals(104.0, back[0], 1e-4);
    assertEquals(482.0, back[1], 1e-4);
  }
}
