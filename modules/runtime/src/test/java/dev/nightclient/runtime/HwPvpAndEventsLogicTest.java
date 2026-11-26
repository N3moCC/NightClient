package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.PotionView;
import dev.nightclient.config.Settings;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HwPvpAndEventsLogicTest {
  @Test
  void parsesHolyWorldServersAndEventsApiAndFiltersByModeAndRarity() {
    String serversJson = "{\n"
        + "  \"LITE_ANARCHY_12\": \"Соло Анархия #12\",\n"
        + "  \"LITE_ANARCHY_25\": \"Дуо Анархия #25\",\n"
        + "  \"LITE_ANARCHY_90\": \"Клан Анархия #90\",\n"
        + "  \"LITE_NEW_ANARCHY_4\": \"Лайт 1.21 #4\"\n"
        + "}";
    Map<String, HwPvpAndEventsLogic.ServerInfo> servers =
        HwPvpAndEventsLogic.parseServersJson(serversJson);
    assertEquals(4, servers.size());
    assertEquals("solo", servers.get("LITE_ANARCHY_12").mode);
    assertEquals(90, servers.get("LITE_ANARCHY_90").anarchyNumber);
    assertTrue(servers.get("LITE_NEW_ANARCHY_4").lite121);

    Map<String, String> chatCoords = new HashMap<String, String>();
    HwPvpAndEventsLogic.observeEventChatLine(
        "§6[Ивент] §fМаяк убийца появился на координатах: §e-450 72 1180", chatCoords);
    assertEquals("-450, 72, 1180", chatCoords.get("BEACON_DEATH"));

    String eventsJson = "{\n"
        + "  \"LITE_ANARCHY_12\": [{\"id\":\"BEACON_DEATH\",\"title\":\"Маяк убийца\",\"rare\":\"mythic\"}],\n"
        + "  \"LITE_ANARCHY_90\": [{\"id\":\"GOLDEN_FORTRESS\",\"title\":\"Золотая лихорадка\",\"rare\":\"legendary\"}],\n"
        + "  \"LITE_NEW_ANARCHY_4\": [{\"id\":\"PARCELS\",\"title\":\"Посылка\",\"rare\":\"default\"}]\n"
        + "}";
    Map<String, Long> firstSeen = new HashMap<String, Long>();
    List<HwPvpAndEventsLogic.EventItem> events = HwPvpAndEventsLogic.parseEventsJson(
        eventsJson, servers, firstSeen, chatCoords, 50_000L);
    assertEquals(3, events.size());
    assertEquals("legendary", events.get(0).rarityTier);
    assertEquals(90, events.get(0).anarchyNumber);
    assertTrue(events.get(0).canAutoJoin());

    assertEquals("mythic", events.get(1).rarityTier);
    assertEquals("-450, 72, 1180", events.get(1).coordsHint);

    assertFalse(events.get(2).canAutoJoin(), "1.21 server requires 1.21 selector");

    assertEquals(1, HwPvpAndEventsLogic.filterEvents(events, "solo", "all").size());
    assertEquals(1, HwPvpAndEventsLogic.filterEvents(events, "all", "legendary").size());
    assertEquals(2, HwPvpAndEventsLogic.filterEvents(events, "all", "mythic").size());
  }

  @Test
  void hwTimersNeverFalseTriggerAndTrackHologramsSnowballAndStunAccurately() {
    Settings.HwTimers cfg = new Settings.HwTimers();
    cfg.enabled = true;
    HwPvpAndEventsLogic.HwTimerTracker tracker = new HwPvpAndEventsLogic.HwTimerTracker();

    tracker.onHologramText("§eДо ивента §f[12 сек]", 10, 65, 10, cfg, 1000L);
    tracker.onHologramText("§6Аукцион обновится через 15 сек", 10, 65, 10, cfg, 1000L);
    tracker.onHologramText("§dДезориентация [12 сек]", 10, 65, 10, cfg, 1000L);
    assertTrue(tracker.snapshot(cfg, 1000L).isEmpty(),
        "Non-HW or unrelated server holograms must never create false timers");

    tracker.onHologramText("§cВзрывная трапка §7[§f18 сек§7]", 20, 64, -15, cfg, 1000L);
    tracker.onHologramText("§6Трапка §8· §e12 сек", 0, 64, 0, cfg, 1000L);
    List<HwPvpAndEventsLogic.HwActiveTimer> snap = tracker.snapshot(cfg, 1000L);
    assertEquals(2, snap.size());

    tracker.clear();
    tracker.syncPersonalEffects(
        Collections.singletonList(new PotionView("minecraft:slowness", "Замедление", 0, 0, 10f, false)),
        Collections.<Cooldown>emptyList(), 0, 64, 0, cfg, 2000L);
    assertTrue(tracker.snapshot(cfg, 2000L).isEmpty(), "Ordinary Slowness I must not false-trigger");

    tracker.syncPersonalEffects(
        Arrays.asList(
            new PotionView("minecraft:blindness", "Слепота", 0, 0, 8.5f, false),
            new PotionView("minecraft:slowness", "Замедление", 4, 0, 8.5f, false)),
        Collections.<Cooldown>emptyList(), 0, 64, 0, cfg, 2000L);
    List<HwPvpAndEventsLogic.HwActiveTimer> freezeSnap = tracker.snapshot(cfg, 2000L);
    assertEquals(1, freezeSnap.size());
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.SNOWBALL, freezeSnap.get(0).kind);

    tracker.clear();
    tracker.syncPersonalEffects(Collections.<PotionView>emptyList(),
        Collections.singletonList(
            new Cooldown("ep", "Перл", "minecraft:ender_pearl", 15f, 15f)),
        0, 64, 0, cfg, 3000L);
    assertTrue(tracker.snapshot(cfg, 3000L).isEmpty(),
        "Throwing an ender pearl alone must never false-trigger Stun");

    tracker.syncPersonalEffects(Collections.<PotionView>emptyList(),
        Arrays.asList(
            new Cooldown("ep", "Перл", "minecraft:ender_pearl", 14.8f, 15f),
            new Cooldown("cf", "Хорус", "minecraft:chorus_fruit", 14.8f, 15f)),
        0, 64, 0, cfg, 3000L);
    List<HwPvpAndEventsLogic.HwActiveTimer> stunSnap = tracker.snapshot(cfg, 3000L);
    assertEquals(1, stunSnap.size());
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.STUN, stunSnap.get(0).kind);
  }

  @Test
  void hwConsumableClassifierAndServerConfirmedPendingUsePreventFalseTimers() {
    assertNull(HwPvpAndEventsLogic.classifyHwConsumable("minecraft:potion", "Зелье восстановления II", ""));
    assertNull(HwPvpAndEventsLogic.classifyHwConsumable("minecraft:loom", "Ткацкий станок", ""));
    assertNull(HwPvpAndEventsLogic.classifyHwConsumable("minecraft:nether_star", "Осталось 5 сек", ""));
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.STUN,
        HwPvpAndEventsLogic.classifyHwConsumable("minecraft:nether_star", "§dСтан", ""));
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.TRAP,
        HwPvpAndEventsLogic.classifyHwConsumable("minecraft:prismarine_shard", "§6Трапка", ""));
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.EXPLOSIVE_TRAP,
        HwPvpAndEventsLogic.classifyHwConsumable("minecraft:prismarine_shard", "§cВзрывная трапка", ""));

    Settings.HwTimers cfg = new Settings.HwTimers();
    cfg.enabled = true;
    HwPvpAndEventsLogic.HwTimerTracker tracker = new HwPvpAndEventsLogic.HwTimerTracker();

    tracker.beginPendingUse(HwPvpAndEventsLogic.HwConsumableKind.STUN, 2, "minecraft:nether_star",
        3, false, 10, 64, 10, cfg, 1000L);
    assertTrue(tracker.hasPendingUse());
    tracker.pollPendingUse("minecraft:nether_star", 3, false, cfg, 1100L);
    assertTrue(tracker.snapshot(cfg, 1100L).isEmpty(), "Unconfirmed cast must not start timer yet");
    tracker.pollPendingUse("minecraft:nether_star", 3, false, cfg, 1700L);
    assertFalse(tracker.hasPendingUse(), "Expired unconfirmed pending use must be dropped");
    assertTrue(tracker.snapshot(cfg, 1700L).isEmpty(), "Rejected cast must NEVER spawn fake timer");

    tracker.beginPendingUse(HwPvpAndEventsLogic.HwConsumableKind.TRAP, 1, "minecraft:prismarine_shard",
        3, false, 15, 64, 15, cfg, 2000L);
    tracker.pollPendingUse("minecraft:prismarine_shard", 2, false, cfg, 2080L);
    assertFalse(tracker.hasPendingUse());
    List<HwPvpAndEventsLogic.HwActiveTimer> afterConsume = tracker.snapshot(cfg, 2080L);
    assertEquals(1, afterConsume.size());
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.TRAP, afterConsume.get(0).kind);

    tracker.clear();
    tracker.beginPendingUse(HwPvpAndEventsLogic.HwConsumableKind.STUN, 4, "minecraft:nether_star",
        1, false, 20, 64, 20, cfg, 3000L);
    tracker.pollPendingUse("minecraft:nether_star", 1, true, cfg, 3060L);
    List<HwPvpAndEventsLogic.HwActiveTimer> afterCd = tracker.snapshot(cfg, 3060L);
    assertEquals(1, afterCd.size());
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.STUN, afterCd.get(0).kind);

    tracker.clear();
    tracker.onHologramText("§6Трапка §7[§e14 сек§7]", 30, 64, 30, cfg, 5000L);
    assertEquals(1, tracker.snapshot(cfg, 5500L).size());
    tracker.pruneMissingHolograms(6500L);
    assertTrue(tracker.snapshot(cfg, 6500L).isEmpty(), "Despawned hologram timer must be pruned");
  }

  @Test
  void parsesTreasureCompassDrillBoxJakeLuminaireAndElevenSecondTrapTimer() {
    int[] c2 = HwPvpAndEventsLogic.parseTreasureCompassCoords(
        "§eСтрелка компаса указывает на сокровище по координатам: -420, 1337");
    assertNotNull(c2);
    assertEquals(-420, c2[0]);
    assertEquals(Integer.MIN_VALUE, c2[1]);
    assertEquals(1337, c2[2]);

    int[] c3 = HwPvpAndEventsLogic.parseTreasureCompassCoords(
        "Найдено сокровище по координатам: 10, 64, -25");
    assertNotNull(c3);
    assertEquals(64, c3[1]);
    assertNull(HwPvpAndEventsLogic.parseTreasureCompassCoords("Обычное сообщение в чате 10, 20"));

    assertEquals(2, HwPvpAndEventsLogic.detectDrillLevel(
        "minecraft:netherite_pickaxe", "drill:2", "", ""));
    assertEquals(1, HwPvpAndEventsLogic.detectDrillLevel(
        "minecraft:diamond_pickaxe", "", "", "§7Бур I\n§7Эффективность V"));
    assertEquals(0, HwPvpAndEventsLogic.detectDrillLevel(
        "minecraft:diamond_sword", "drill:2", "", "Бур II"));

    double[] drillBox = HwPvpAndEventsLogic.computeDrillBox(10, 64, 20, 0, 1, 0, 2);
    assertArrayEquals(new double[] {9, 63, 19, 12, 65, 22}, drillBox, 0.001);

    assertTrue(HwPvpAndEventsLogic.isJakeLuminaire(
        "minecraft:jack_o_lantern", "JakeLuminaire", "Тыква"));
    assertFalse(HwPvpAndEventsLogic.isJakeLuminaire(
        "minecraft:carved_pumpkin", "JakeLuminaire", "Тыква"));

    HwPvpAndEventsLogic.TrapActionTimer timer = new HwPvpAndEventsLogic.TrapActionTimer();
    assertNull(timer.pollInventory(false, 4, false, 3, 1000L));
    assertNull(timer.pollInventory(true, 4, false, 3, 1100L));
    assertNull(timer.pollInventory(false, 4, false, 3, 2000L));
    assertEquals(HwPvpAndEventsLogic.HwConsumableKind.EXPLOSIVE_TRAP,
        timer.pollInventory(true, 3, false, 3, 2100L));
    String activeBar = timer.nextActionbarText(2600L, "ru");
    assertNotNull(activeBar);
    assertTrue(activeBar.contains("10.5"));
    assertEquals("", timer.nextActionbarText(13_200L, "ru"));
    assertNull(timer.nextActionbarText(13_300L, "ru"));
  }

  @Test
  void computesNonZFightingExplosiveAndNormalTrapEspGeometry() {
    double[] chk = HwPvpAndEventsLogic.computeExplosiveTrapCheckBox(10, 64, -5);
    double[] main = HwPvpAndEventsLogic.computeExplosiveTrapMainBox(10, 64, -5);
    double[] lower = HwPvpAndEventsLogic.computeExplosiveTrapLowerBox(10, 64, -5);
    assertArrayEquals(new double[] {7.0, 61.0, -8.0, 14.0, 68.0, -1.0}, chk, 0.0001);
    assertTrue(main[1] > 64.0 && main[1] < 64.05);
    assertTrue(main[4] > 67.9 && main[4] < 68.0);
    assertTrue(main[0] > chk[0] && main[3] < chk[3]);
    assertTrue(main[2] > chk[2] && main[5] < chk[5]);
    assertEquals(main[1], lower[4], 0.0001);
    assertTrue(lower[1] > 61.0 && lower[1] < 61.05);

    double[] normal = HwPvpAndEventsLogic.computeNormalTrapBox(10, 64, -5);
    assertTrue(normal[0] > 8.0 && normal[3] < 13.0);
    assertTrue(normal[1] > 64.0 && normal[1] < 64.05);
    assertTrue(normal[4] > 67.9 && normal[4] < 68.0);
    assertTrue(normal[2] > -7.0 && normal[5] < -2.0);
  }

  @Test
  void tracksExplosiveThingCooldownWithoutDuplicatingVanillaCooldowns() {
    assertTrue(HwPvpAndEventsLogic.isExplosiveThing(
        "minecraft:fire_charge", "§c[★] Взрывная штучка", "§7Перезарядка: 12 сек"));
    assertTrue(HwPvpAndEventsLogic.isExplosiveThing(
        "minecraft:fire_charge", "&x&b&2&2&2&2&2ʙзᴘыʙнᴀя шᴛучᴋᴀ", ""));
    assertTrue(HwPvpAndEventsLogic.isExplosiveThing(
        "minecraft:fire_charge", "Огненный заряд", "", "ExplosiveThing", false));
    assertTrue(HwPvpAndEventsLogic.isExplosiveThing(
        "minecraft:fire_charge", "Огненный заряд", "", "", true));
    assertFalse(HwPvpAndEventsLogic.isExplosiveThing(
        "minecraft:fire_charge", "§6Огненный смерч", "§7Волна огня", "", true));
    assertFalse(HwPvpAndEventsLogic.isExplosiveThing(
        "minecraft:fire_charge", "§cВзрывная трапка", "", "", true));
    assertEquals(12.0f,
        HwPvpAndEventsLogic.parseItemCooldownSeconds("§7Перезарядка: 12 сек"), 0.01f);
    assertEquals(15.0f,
        HwPvpAndEventsLogic.parseItemCooldownSeconds("§7КД: 00:15"), 0.01f);
    assertEquals(8.5f,
        HwPvpAndEventsLogic.parseServerCooldownNotice("§cПодождите 8.5 сек. перед использованием"),
        0.01f);
    assertEquals(14.0f,
        HwPvpAndEventsLogic.parseServerCooldownNotice("§cВы сможете использовать этот предмет через 14 сек."),
        0.01f);

    HwPvpAndEventsLogic.ExplosiveThingCooldownTracker tracker =
        new HwPvpAndEventsLogic.ExplosiveThingCooldownTracker();
    tracker.onRightClick("minecraft:fire_charge", "§cВзрывная штучка", "§7КД: 12 сек", "", true,
        2, 3, 5, false, 1000L);
    tracker.onRightClick("minecraft:fire_charge", "§cВзрывная штучка", "§7КД: 12 сек", "", true,
        2, 2, 4, false, 1010L);
    tracker.pollInventory("minecraft:fire_charge", 2, 4, false, 1050L);
    tracker.onRightClick("minecraft:fire_charge", "§cВзрывная штучка", "§7КД: 12 сек", "", true,
        2, 2, 4, false, 1100L);
    assertFalse(tracker.hasActive(1110L));
    tracker.pollInventory("minecraft:fire_charge", 2, 4, false, 1250L);
    assertTrue(tracker.hasActive(1300L));
    List<Cooldown> merged = tracker.mergeInto(Collections.<Cooldown>emptyList(), 2250L);
    assertEquals(1, merged.size());
    assertEquals("minecraft:fire_charge", merged.get(0).itemId);
    assertEquals("Взрывная штучка", merged.get(0).name);
    assertEquals(11.0f, merged.get(0).remainingSeconds, 0.25f);

    List<Cooldown> vanilla = Collections.singletonList(
        new Cooldown("minecraft:fire_charge", "Взрывная штучка", "minecraft:fire_charge", 9f, 12f));
    List<Cooldown> deduped = tracker.mergeInto(vanilla, 2400L);
    assertEquals(1, deduped.size());
    assertFalse(tracker.hasActive(2400L));

    tracker.clear();
    tracker.onRightClick("minecraft:fire_charge", "&x&b&2&2&2&2&2Взрывная штучка", "§7КД: 10 сек",
        "", true, 0, 1, 1, false, 5000L);
    tracker.onNearbyExplosion(10.2, 64.5, 20.1, 10.0, 64.0, 20.0, 5040L);
    tracker.pollInventory("minecraft:fire_charge", 1, 1, false, 5250L);
    assertTrue(tracker.hasActive(5300L));
  }
}
