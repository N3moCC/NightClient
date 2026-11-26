package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatTagDetectorTest {
  @Test
  void detectsHolyWorldAndStandardCombatBars() {
    CombatTagDetector.CombatMatch hw =
        CombatTagDetector.parseCombatText("§cВы в бою! §fНе выходите: §e14 сек.");
    assertTrue(hw.active);
    assertEquals(14, hw.remainingSeconds);

    CombatTagDetector.CombatMatch ft =
        CombatTagDetector.parseCombatText("§x§f§f§3§3§3§3Режим боя §8| §fОсталось: §c00:25");
    assertTrue(ft.active);
    assertEquals(25, ft.remainingSeconds);

    CombatTagDetector.CombatMatch rw =
        CombatTagDetector.parseCombatText("§c[КТ] §fОсталось 9с");
    assertTrue(rw.active);
    assertEquals(9, rw.remainingSeconds);

    CombatTagDetector.CombatMatch en =
        CombatTagDetector.parseCombatText("§cIn Combat §7- §f12s left");
    assertTrue(en.active);
    assertEquals(12, en.remainingSeconds);
  }

  @Test
  void rejectsBossRaidAndEventBars() {
    assertFalse(CombatTagDetector.parseCombatText("§5Дракон Края").active);
    assertFalse(CombatTagDetector.parseCombatText("§cБосс: Скелет-воин 15 сек").active);
    assertFalse(CombatTagDetector.parseCombatText("§6Аирдроп появится через 25 сек").active);
    assertFalse(CombatTagDetector.parseCombatText("§eРейд · Осталось: 10").active);
    assertFalse(CombatTagDetector.parseCombatText("§bPvP арена открыта").active);
  }

  @Test
  void blocksLeaveAndRealmSwitchCommandsIncludingRussianLayoutTypos() {
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/hub"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/lobby"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/an12"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/server anarchy3"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/spawn"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/essentials:home"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand(".рги"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/рги"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/дщиин"));

    assertFalse(CombatTagDetector.isBlockedLeaveCommand("привет /hub"));
    assertFalse(CombatTagDetector.isBlockedLeaveCommand("/msg Friend help"));
    assertFalse(CombatTagDetector.isBlockedLeaveCommand("/ec"));
    assertFalse(CombatTagDetector.isBlockedLeaveCommand("/heal"));
  }

  @Test
  void rejectsZeroTimersPlayerChatOwnFeedbackAndStatScoreboardLines() {
    assertEquals(-1, CombatTagDetector.extractSeconds("кт: 0с"));
    assertEquals(-1, CombatTagDetector.extractSeconds("00:00"));
    assertFalse(CombatTagDetector.parseCombatText("§c[КТ] §f0с").active);
    assertFalse(CombatTagDetector.parseCombatText("§cБой: 00:00").active);

    assertFalse(CombatTagDetector.parseChatCombatText(
        "§c[Safe CT Leave] §fВыход заблокирован — активен КТ (15с)!").active);
    assertFalse(CombatTagDetector.parseChatCombatText(
        "§c[Safe CT Leave] §fLeave blocked — Combat Tag active (15s)!").active);
    assertFalse(CombatTagDetector.parseChatCombatText("[G] Steve: го пвп 1 на 1").active);
    assertFalse(CombatTagDetector.parseChatCombatText("[L] Alex: у меня кт 12с не выходи").active);
    assertFalse(CombatTagDetector.parseChatCombatText("<Notch> вы в бою не выходите 10 сек").active);
    assertFalse(CombatTagDetector.parseChatCombatText("Player123 » режим боя 15с").active);

    CombatTagDetector.CombatMatch serverChat = CombatTagDetector.parseChatCombatText(
        "§c[AntiRelog] §fВы вошли в режим боя! Не выходите 15 сек.");
    assertTrue(serverChat.active);
    assertEquals(15, serverChat.remainingSeconds);

    assertFalse(CombatTagDetector.parseScoreboardCombatText("§fPvP: §a12").active);
    assertFalse(CombatTagDetector.parseScoreboardCombatText("§fПвП: §e3").active);
    assertFalse(CombatTagDetector.parseScoreboardCombatText("§fБой: §70").active);
    CombatTagDetector.CombatMatch sbReal =
        CombatTagDetector.parseScoreboardCombatText("§cКТ: §f14с");
    assertTrue(sbReal.active);
    assertEquals(14, sbReal.remainingSeconds);

    assertTrue(CombatTagDetector.isCombatEndText("§aВы вышли из боя!"));
    assertTrue(CombatTagDetector.isCombatEndText("§aБой окончен, можно выходить"));
    assertTrue(CombatTagDetector.isCombatEndText("§aCombat expired — safe to log out"));
    assertFalse(CombatTagDetector.isCombatEndText("[G] Player: вы вышли из боя"));
  }

  @Test
  void pendingLeaveGuardAllowsCleanWindowAndAbortsOnMidLeaveHitOrCt() {
    PendingLeaveGuard guard = new PendingLeaveGuard();
    long t0 = 1_000_000_000L;
    final int[] executed = {0};
    PendingLeaveGuard.Snapshot base =
        new PendingLeaveGuard.Snapshot(false, 20f, 0, 0L, 0L, 0L, 0L, 0L, true, false);

    guard.begin("disconnect", () -> executed[0]++, t0, 50, base);
    assertTrue(guard.isActive());
    assertEquals(PendingLeaveGuard.Outcome.WAITING, guard.poll(t0 + 100_000_000L, base));
    assertEquals(PendingLeaveGuard.Outcome.EXECUTE, guard.poll(t0 + 250_000_000L, base));
    Runnable action = guard.takeReplay();
    action.run();
    assertEquals(1, executed[0]);
    assertFalse(guard.isActive());

    guard.begin("disconnect", () -> executed[0]++, t0, 80, base);
    PendingLeaveGuard.Snapshot hitSnap =
        new PendingLeaveGuard.Snapshot(false, 16f, 10, t0 + 90_000_000L, 0L, 0L, 0L, 0L, true, false);
    assertEquals(PendingLeaveGuard.Outcome.ABORTED_HIT, guard.poll(t0 + 100_000_000L, hitSnap));
    assertFalse(guard.isActive());

    guard.begin("command", () -> executed[0]++, t0, 60, base);
    PendingLeaveGuard.Snapshot hpDrop =
        new PendingLeaveGuard.Snapshot(false, 18.5f, 0, 0L, 0L, 0L, 0L, 0L, true, false);
    assertEquals(PendingLeaveGuard.Outcome.ABORTED_HIT, guard.poll(t0 + 50_000_000L, hpDrop));

    guard.begin("disconnect", () -> executed[0]++, t0, 50, base);
    PendingLeaveGuard.Snapshot fallSnap =
        new PendingLeaveGuard.Snapshot(
            false, 17f, 10, 0L, 0L, 0L, t0 + 40_000_000L, t0 + 45_000_000L, true, false);
    assertEquals(PendingLeaveGuard.Outcome.WAITING, guard.poll(t0 + 100_000_000L, fallSnap));
    assertEquals(PendingLeaveGuard.Outcome.EXECUTE, guard.poll(t0 + 240_000_000L, fallSnap));
    guard.clear();

    guard.begin("window", () -> executed[0]++, t0, 50, base);
    PendingLeaveGuard.Snapshot ctSnap =
        new PendingLeaveGuard.Snapshot(true, 20f, 0, 0L, 0L, t0 + 80_000_000L, 0L, 0L, true, false);
    assertEquals(PendingLeaveGuard.Outcome.ABORTED_CT, guard.poll(t0 + 90_000_000L, ctSnap));

    guard.begin("disconnect", () -> executed[0]++, t0, 50, base);
    PendingLeaveGuard.Snapshot nettyRace =
        new PendingLeaveGuard.Snapshot(
            false, 20f, 0, 0L, 0L, 0L, t0 + 230_000_000L, 0L, true, false);
    assertEquals(PendingLeaveGuard.Outcome.WAITING, guard.poll(t0 + 240_000_000L, nettyRace));
    guard.clear();

    guard.begin("disconnect", () -> executed[0]++, t0, 50, base);
    PendingLeaveGuard.Snapshot closedPause =
        new PendingLeaveGuard.Snapshot(false, 20f, 0, 0L, 0L, 0L, 0L, 0L, false, false);
    assertEquals(PendingLeaveGuard.Outcome.CANCELLED, guard.poll(t0 + 50_000_000L, closedPause));

    guard.begin("disconnect", () -> executed[0]++, t0, 50, base);
    PendingLeaveGuard.Snapshot shiftSnap =
        new PendingLeaveGuard.Snapshot(false, 20f, 0, 0L, 0L, 0L, 0L, 0L, true, true);
    assertEquals(PendingLeaveGuard.Outcome.EXECUTE, guard.poll(t0 + 10_000_000L, shiftSnap));
  }

  @Test
  void combatTagDetectsDuelsBossBarsAndAllRuEnBlockedCommands() {
    CombatTagDetector.CombatMatch duel = CombatTagDetector.parseBossBarText("§cДуэль против Steve");
    assertTrue(duel.active);
    CombatTagDetector.CombatMatch pvpBar = CombatTagDetector.parseBossBarText("§cПвП §f18");
    assertTrue(pvpBar.active);
    assertEquals(18, pvpBar.remainingSeconds);
    assertFalse(CombatTagDetector.parseBossBarText("§bPvP арена открыта").active);

    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/limbo"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/logout"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/grief3"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/фт15"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/пкшуа"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/дшьищ"));
    assertTrue(CombatTagDetector.isBlockedLeaveCommand("/дщпщге"));

    assertFalse(CombatTagDetector.isBlockedLeaveCommand("/anvil"));
    assertFalse(CombatTagDetector.isBlockedLeaveCommand("/answer yes"));
    assertFalse(CombatTagDetector.isBlockedLeaveCommand("/announce hi"));

    assertFalse(CombatTagDetector.parseChatCombatText(
        "§c[PVP Safe] §fНельзя использовать эту команду в режиме PvP!").active);
  }
}
