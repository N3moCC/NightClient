package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class TooltipsLogicTest {
  @Test
  void classifiesHolyWorldSpecialItemsAndShulkers() {
    TooltipsLogic.HwItemInfo talisman = TooltipsLogic.classifyItem(
        "minecraft:totem_of_undying", "§6§lТалисман Крушителя", Collections.<String>emptyList(), "");
    assertEquals(TooltipsLogic.Category.TALISMAN, talisman.category);
    assertEquals("ТАЛИСМАН", talisman.badge(true));

    TooltipsLogic.HwItemInfo sphere = TooltipsLogic.classifyItem(
        "minecraft:player_head", "§dСфера Андромеды", Collections.<String>emptyList(), "");
    assertEquals(TooltipsLogic.Category.SPHERE, sphere.category);
    assertEquals("SPHERE", sphere.badge(false));

    TooltipsLogic.HwItemInfo raid = TooltipsLogic.classifyItem(
        "minecraft:tnt", "§cВзрывная волна", Collections.<String>emptyList(), "");
    assertEquals(TooltipsLogic.Category.RAID, raid.category);
    assertEquals(12.0f, raid.raidRadius, 0.01f);

    TooltipsLogic.HwItemInfo pvp = TooltipsLogic.classifyItem(
        "minecraft:netherite_scrap", "§bТрапка", Collections.<String>emptyList(), "");
    assertEquals(TooltipsLogic.Category.PVP, pvp.category);

    TooltipsLogic.HwItemInfo shulker = TooltipsLogic.classifyItem(
        "minecraft:cyan_shulker_box", "Шалкеровый ящик", Collections.<String>emptyList(), "");
    assertEquals(TooltipsLogic.Category.SHULKER, shulker.category);
    assertEquals(TooltipsLogic.shulkerDyeColor("minecraft:cyan_shulker_box"), shulker.accentColor);
  }

  @Test
  void parsesAuctionPerItemAndShulkerUnitPrices() {
    List<String> stackLore = Arrays.asList("§7Продавец: Steve", "§eЦена: 640 000 $");
    TooltipsLogic.AuctionPriceInfo perItem = TooltipsLogic.parseAuctionPrice(stackLore, 64, 0);
    assertNotNull(perItem);
    assertEquals(10000.0, perItem.unitPrice, 0.01);
    assertFalse(perItem.fromShulker);
    assertTrue(TooltipsLogic.formatAuctionPriceLine(perItem, true).contains("10 000"));

    List<String> shulkerLore = Arrays.asList("§eЦена: 1.728M монет");
    TooltipsLogic.AuctionPriceInfo shulkerPrice =
        TooltipsLogic.parseAuctionPrice(shulkerLore, 1, 1728);
    assertNotNull(shulkerPrice);
    assertEquals(1000.0, shulkerPrice.unitPrice, 0.5);
    assertTrue(shulkerPrice.fromShulker);
    assertTrue(TooltipsLogic.formatAuctionPriceLine(shulkerPrice, true).contains("1 728"));
  }

  @Test
  void compactsDuplicateShulkerEntriesAndDetectsVanillaShulkerLines() {
    List<TooltipsLogic.PeekEntry> raw = Arrays.asList(
        new TooltipsLogic.PeekEntry(0, 300, "minecraft:golden_apple", "Golden Apple", 64, 0, 0, false),
        new TooltipsLogic.PeekEntry(1, 301, "minecraft:totem_of_undying", "Totem", 1, 0, 0, false),
        new TooltipsLogic.PeekEntry(2, 302, "minecraft:golden_apple", "Golden Apple", 64, 0, 0, false),
        new TooltipsLogic.PeekEntry(3, 303, "minecraft:totem_of_undying", "Totem", 1, 0, 0, false));
    List<TooltipsLogic.PeekEntry> compact = TooltipsLogic.compactEntries(raw);
    assertEquals(2, compact.size());
    assertEquals("minecraft:golden_apple", compact.get(0).itemId);
    assertEquals(128, compact.get(0).count);
    assertEquals(2, compact.get(1).count);

    assertTrue(TooltipsLogic.isVanillaShulkerContentLine("Золотое яблоко x64"));
    assertTrue(TooltipsLogic.isVanillaShulkerContentLine("и ещё 22..."));
    assertTrue(TooltipsLogic.isVanillaShulkerContentLine("and 14 more..."));
    assertFalse(TooltipsLogic.isVanillaShulkerContentLine("Цена: 500 000 $"));
  }

  @Test
  void formatsDurabilityWithThresholds() {
    assertTrue(TooltipsLogic.formatDurabilityLine(100, 1000, true).contains("§a900"));
    assertTrue(TooltipsLogic.formatDurabilityLine(600, 1000, true).contains("§e400"));
    assertTrue(TooltipsLogic.formatDurabilityLine(900, 1000, false).contains("§c100"));
  }

  @Test
  void parsesGoldenSpawnersSpecialCompassCoinExchangeAndKnockback() {
    String farmerSwordJson =
        "{\"id\":\"minecraft:netherite_sword\",\"components\":{"
            + "\"minecraft:custom_name\":\"{\\\"text\\\":\\\"Фарм Меч\\\"}\","
            + "\"minecraft:enchantments\":{\"levels\":{\"minecraft:sharpness\":5,\"minecraft:looting\":3}},"
            + "\"minecraft:custom_data\":{\"PublicBukkitValues\":{"
            + "\"holyworld:mob-farmer-enchant\":2,\"holyworld:critical-enchant-custom\":1}}}}";
    TooltipsLogic.GoldenSpawnerInfo spFarmer =
        TooltipsLogic.parseGoldenSpawner("blaze", 12500, 500, 42, farmerSwordJson);
    assertTrue(spFarmer.hasSword);
    assertTrue(spFarmer.hasFarmerEnchant);
    assertEquals(TooltipsLogic.SPAWNER_HIGHLIGHT_FARMER,
        TooltipsLogic.spawnerSlotHighlightColor(true, farmerSwordJson));
    List<String> lines = TooltipsLogic.formatGoldenSpawnerLines(spFarmer, "ru");
    String joined = String.join("\n", lines);
    assertTrue(joined.contains("Ифрит"));
    assertTrue(joined.contains("Фармер II"));
    assertTrue(joined.contains("Острота V"));

    String plainSwordJson = "{\"id\":\"minecraft:diamond_sword\",\"components\":{}}";
    assertEquals(TooltipsLogic.SPAWNER_HIGHLIGHT_NO_FARMER,
        TooltipsLogic.spawnerSlotHighlightColor(true, plainSwordJson));
    assertEquals(0, TooltipsLogic.spawnerSlotHighlightColor(false, farmerSwordJson));

    List<String> readyCompass =
        TooltipsLogic.formatSpecialCompassLines(1000L, true, 150, 64, -320, 2000L, "ru");
    assertTrue(readyCompass.get(0).contains("Готов"));
    assertTrue(readyCompass.get(1).contains("150, 64, -320"));
    List<String> cdCompass =
        TooltipsLogic.formatSpecialCompassLines(125_000L, true, -10, null, 20, 0L, "ru");
    assertTrue(cdCompass.get(0).contains("2 мин 5 сек"));
    assertTrue(cdCompass.get(1).contains("-10, 20"));

    List<String> exLines = Arrays.asList(
        "§6Заявка на продажу",
        "§7Курс: §e90 000 ¤ за 1 коин",
        "§7Доступно: §f150");
    TooltipsLogic.ExchangeOrderInfo ex = TooltipsLogic.parseExchangeOrder(exLines, 100_000L);
    assertNotNull(ex);
    assertEquals(1, ex.rateLineIndex);
    assertEquals(-10.0, ex.diffPercent, 0.01);
    assertEquals(13_500_000.0, ex.expectedPayout, 1.0);
    assertTrue(TooltipsLogic.formatExchangeDiffSuffix(ex, "ru").contains("§a-10.0%"));
    assertTrue(TooltipsLogic.formatExchangePayoutLine(ex, "ru").contains("13.50M"));

    assertTrue(TooltipsLogic.isSwordId("minecraft:netherite_sword"));
    assertFalse(TooltipsLogic.isSwordId("minecraft:stick"));
    assertTrue(TooltipsLogic.isKnockbackEnchantLine("§7Отдача II"));
    assertTrue(TooltipsLogic.isKnockbackEnchantLine("Knockback I"));
    assertFalse(TooltipsLogic.isKnockbackEnchantLine("§7Острота V"));
  }
}
