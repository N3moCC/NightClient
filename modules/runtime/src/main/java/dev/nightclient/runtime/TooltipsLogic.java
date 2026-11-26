package dev.nightclient.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.i18n.I18n;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TooltipsLogic {
  public enum Category {
    NONE,
    SHULKER,
    BUNDLE,
    TALISMAN,
    SPHERE,
    RAID,
    PVP,
    UNIQUE,
    DONATE,
    RARE_VANILLA
  }

  public static final class HwItemInfo {
    public static final HwItemInfo NONE = new HwItemInfo(Category.NONE, 0, "", 0f);

    public final Category category;
    public final int accentColor;
    public final String badgeKey;
    public final String badgeRu;
    public final String badgeEn;
    public final float raidRadius;

    public HwItemInfo(Category category, int accentColor, String badgeKey, float raidRadius) {
      this.category = category;
      this.accentColor = accentColor;
      this.badgeKey = badgeKey == null ? "" : badgeKey;
      this.raidRadius = raidRadius;
      this.badgeRu = formatBadge("ru", this.badgeKey, raidRadius);
      this.badgeEn = formatBadge("en", this.badgeKey, raidRadius);
    }

    public HwItemInfo(
        Category category, int accentColor, String badgeRu, String badgeEn, float raidRadius) {
      this.category = category;
      this.accentColor = accentColor;
      this.badgeKey = "";
      this.badgeRu = badgeRu == null ? "" : badgeRu;
      this.badgeEn = badgeEn == null ? "" : badgeEn;
      this.raidRadius = raidRadius;
    }

    public boolean hasAccent() {
      return (accentColor >>> 24) != 0;
    }

    public String badge(boolean ru) {
      return badge(ru ? "ru" : "en");
    }

    public String badge(String language) {
      if (!badgeKey.isEmpty()) return formatBadge(language, badgeKey, raidRadius);
      return "ru".equals(I18n.language(language)) ? badgeRu : badgeEn;
    }

    private static String formatBadge(String language, String key, float raidRadius) {
      if (key == null || key.isEmpty()) return "";
      if ("tooltips.badge.raid".equals(key)) {
        return I18n.format(language, key,
            Collections.<String, Object>singletonMap("radius", Integer.valueOf(Math.round(raidRadius))));
      }
      return I18n.text(language, key);
    }
  }

  public static final class AuctionPriceInfo {
    public final double totalPrice;
    public final double unitPrice;
    public final int unitCount;
    public final String currency;
    public final boolean fromShulker;

    public AuctionPriceInfo(
        double totalPrice, double unitPrice, int unitCount, String currency, boolean fromShulker) {
      this.totalPrice = totalPrice;
      this.unitPrice = unitPrice;
      this.unitCount = unitCount;
      this.currency = currency == null ? "$" : currency;
      this.fromShulker = fromShulker;
    }
  }

  public static final class PeekEntry {
    public final int slotIndex;
    public final int cacheSlot;
    public final String itemId;
    public final String displayName;
    public final int count;
    public final int damage;
    public final int maxDamage;
    public final boolean enchanted;

    public PeekEntry(int slotIndex, int cacheSlot, String itemId, String displayName, int count,
        int damage, int maxDamage, boolean enchanted) {
      this.slotIndex = slotIndex;
      this.cacheSlot = cacheSlot;
      this.itemId = itemId == null ? "" : itemId;
      this.displayName = displayName == null ? "" : displayName;
      this.count = Math.max(1, count);
      this.damage = Math.max(0, damage);
      this.maxDamage = Math.max(0, maxDamage);
      this.enchanted = enchanted;
    }

    public boolean empty() {
      return itemId.isEmpty() || "minecraft:air".equals(itemId);
    }
  }

  private static final Pattern COLOR_CODES =
      Pattern.compile("(?:§[0-9a-fk-orxA-FK-ORX]|&[0-9a-fk-orA-FK-OR])");
  private static final Pattern PRICE_LINE = Pattern.compile(
      "(?:цена|стоимость|выкуп|ставка|price|cost|buy)\\s*[:\\-]?\\s*([0-9][0-9\\s.,_']*\\s*(?:кк|kk|млн|млрд|[kKmMbBкКмМ])?)\\s*([₽$€]|монет\\w*|coins?|руб\\w*|сапфир\\w*|токин\\w*|токен\\w*)?",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern VANILLA_SHULKER_COUNT =
      Pattern.compile("^.+\\s+x\\d+$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern VANILLA_SHULKER_MORE = Pattern.compile(
      "^(?:and\\s+\\d+\\s+more\\.{0,3}|и\\s+ещ[её]\\s+\\d+\\.{0,3})$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Map<String, Integer> SHULKER_DYE_COLORS = new LinkedHashMap<String, Integer>();

  static {
    SHULKER_DYE_COLORS.put("minecraft:shulker_box", 0xffa678d6);
    SHULKER_DYE_COLORS.put("minecraft:white_shulker_box", 0xffe8eef7);
    SHULKER_DYE_COLORS.put("minecraft:light_gray_shulker_box", 0xffb3bac4);
    SHULKER_DYE_COLORS.put("minecraft:gray_shulker_box", 0xff707985);
    SHULKER_DYE_COLORS.put("minecraft:black_shulker_box", 0xff525866);
    SHULKER_DYE_COLORS.put("minecraft:brown_shulker_box", 0xffa8744e);
    SHULKER_DYE_COLORS.put("minecraft:red_shulker_box", 0xffe85858);
    SHULKER_DYE_COLORS.put("minecraft:orange_shulker_box", 0xfff5923e);
    SHULKER_DYE_COLORS.put("minecraft:yellow_shulker_box", 0xfff7d44c);
    SHULKER_DYE_COLORS.put("minecraft:lime_shulker_box", 0xff7fe04c);
    SHULKER_DYE_COLORS.put("minecraft:green_shulker_box", 0xff54ab5c);
    SHULKER_DYE_COLORS.put("minecraft:cyan_shulker_box", 0xff2cc4d6);
    SHULKER_DYE_COLORS.put("minecraft:light_blue_shulker_box", 0xff5ab4f5);
    SHULKER_DYE_COLORS.put("minecraft:blue_shulker_box", 0xff547bf2);
    SHULKER_DYE_COLORS.put("minecraft:purple_shulker_box", 0xffa45ef2);
    SHULKER_DYE_COLORS.put("minecraft:magenta_shulker_box", 0xffd95ed4);
    SHULKER_DYE_COLORS.put("minecraft:pink_shulker_box", 0xfff582b8);
  }

  private TooltipsLogic() {}

  public static String stripColors(String text) {
    if (text == null || text.isEmpty()) return "";
    return COLOR_CODES.matcher(text).replaceAll("").trim();
  }

  public static boolean isShulkerId(String itemId) {
    return itemId != null && itemId.endsWith("shulker_box");
  }

  public static boolean isBundleId(String itemId) {
    return itemId != null && (itemId.equals("minecraft:bundle") || itemId.endsWith("_bundle"));
  }

  public static int shulkerDyeColor(String itemId) {
    if (itemId == null) return 0xffa678d6;
    Integer c = SHULKER_DYE_COLORS.get(itemId.toLowerCase(Locale.ROOT));
    return c != null ? c.intValue() : 0xffa678d6;
  }

  public static HwItemInfo classifyItem(
      String itemId, String displayName, List<String> loreLines, String bukkitMeta) {
    String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
    String cleanName = stripColors(displayName).toLowerCase(Locale.ROOT);
    StringBuilder full = new StringBuilder(cleanName);
    if (loreLines != null) {
      for (int i = 0; i < loreLines.size(); i++) {
        String l = stripColors(loreLines.get(i)).toLowerCase(Locale.ROOT);
        if (!l.isEmpty()) full.append(' ').append(l);
      }
    }
    if (bukkitMeta != null && !bukkitMeta.isEmpty()) {
      full.append(' ').append(bukkitMeta.toLowerCase(Locale.ROOT));
    }
    String text = full.toString();

    if (cleanName.contains("сфера") || cleanName.contains("sphere")
        || containsAny(text, "сфера андромеды", "сфера пандоры", "сфера титана", "сфера осириса",
            "сфера аполлона", "сфера астрея", "сфера химеры", "сфера медузы")) {
      return new HwItemInfo(Category.SPHERE, 0xffc07cff, "tooltips.badge.sphere", 0f);
    }

    if (cleanName.contains("талисман") || cleanName.contains("talisman")
        || containsAny(text, "талисман карателя", "талисман крушителя", "талисман грани",
            "талисман гармонии", "талисман феникса", "талисман стихий", "талисман эгиды",
            "талисман тритона", "талисман ехидны", "талисман демона")) {
      return new HwItemInfo(Category.TALISMAN, 0xffffbe3b, "tooltips.badge.talisman", 0f);
    }

    if (id.contains("tnt") || cleanName.contains("c4") || cleanName.contains("с4")
        || cleanName.contains("взрывная волна") || cleanName.contains("пластид")
        || cleanName.contains("динами")) {
      ExplosionRingLogic.TntSpec spec = ExplosionRingLogic.inferFromText(cleanName, text);
      if (spec.custom) {
        return new HwItemInfo(Category.RAID, 0xffff4d6d, "tooltips.badge.raid", spec.radius);
      }
    }

    if (containsAny(cleanName, "трапка", "стан", "ком снега", "снежок заморозки", "прощальный гул",
        "сваппер", "рюкзак", "отмычк", "взрывная трапка", "огненный смерч", "взрывная штучка",
        "явная пыль", "божий тотем", "зелье киллера", "зелье медика", "зелье агента",
        "зелье победителя")) {
      return new HwItemInfo(Category.PVP, 0xff38d9ff, "tooltips.badge.pvp", 0f);
    }

    if (containsAny(cleanName, "крушител", "мега-бульдозер", "бульдозер", "кирка 3x3", "кирка 3х3",
        "бур", "разрушител", "карател", "арбалет", "молот")) {
      return new HwItemInfo(Category.UNIQUE, 0xff5eead4, "tooltips.badge.unique", 0f);
    }

    if (containsAny(cleanName, "кейс", "ключ", "сапфир", "рубин", "талисманный", "мифический",
        "легендарный", "донат")) {
      return new HwItemInfo(Category.DONATE, 0xfff472b6, "tooltips.badge.donate", 0f);
    }

    if (isShulkerId(id)) {
      return new HwItemInfo(Category.SHULKER, shulkerDyeColor(id), "tooltips.badge.shulker", 0f);
    }
    if (isBundleId(id)) {
      return new HwItemInfo(Category.BUNDLE, 0xffd49a6a, "tooltips.badge.bundle", 0f);
    }

    if (id.contains("netherite") || "minecraft:elytra".equals(id) || "minecraft:mace".equals(id)
        || "minecraft:trident".equals(id)) {
      return new HwItemInfo(Category.RARE_VANILLA, 0xff9b8cff, "", 0f);
    }
    if ("minecraft:enchanted_golden_apple".equals(id) || "minecraft:totem_of_undying".equals(id)
        || "minecraft:nether_star".equals(id) || "minecraft:beacon".equals(id)) {
      return new HwItemInfo(Category.RARE_VANILLA, 0xfff5c249, "", 0f);
    }
    if (id.contains("diamond")) {
      return new HwItemInfo(Category.RARE_VANILLA, 0xff56d4f0, "", 0f);
    }

    return HwItemInfo.NONE;
  }

  public static AuctionPriceInfo parseAuctionPrice(
      List<String> loreLines, int stackCount, int shulkerTotalItems) {
    if (loreLines == null || loreLines.isEmpty()) return null;
    for (int i = 0; i < loreLines.size(); i++) {
      String clean = stripColors(loreLines.get(i));
      if (clean.isEmpty()) continue;
      String lower = clean.toLowerCase(Locale.ROOT);
      if (lower.contains("за 1 шт") || lower.contains("per item") || lower.contains("за шт")) {
        continue;
      }
      Matcher m = PRICE_LINE.matcher(clean);
      if (!m.find()) continue;
      double total = parseHumanNumber(m.group(1));
      if (total <= 0.0) continue;
      String cur = m.group(2);
      String currency = normalizeCurrency(cur, clean);
      if (shulkerTotalItems > 1) {
        return new AuctionPriceInfo(total, total / shulkerTotalItems, shulkerTotalItems, currency, true);
      }
      if (stackCount > 1) {
        return new AuctionPriceInfo(total, total / stackCount, stackCount, currency, false);
      }
      return null;
    }
    return null;
  }

  public static String formatAuctionPriceLine(AuctionPriceInfo info, boolean ru) {
    return formatAuctionPriceLine(info, ru ? "ru" : "en");
  }

  public static String formatAuctionPriceLine(AuctionPriceInfo info, String language) {
    if (info == null || info.unitCount <= 1) return "";
    String lang = I18n.language(language);
    String cur = localizeCurrency(info.currency, lang);
    String unit = formatCompactPrice(info.unitPrice) + (cur.isEmpty() ? "" : " " + cur);
    if (info.fromShulker) {
      Map<String, Object> args = new LinkedHashMap<String, Object>(4);
      args.put("count", formatIntSpaced(info.unitCount));
      args.put("unit", unit);
      return I18n.format(lang, "tooltips.line.shulker_price", args);
    }
    return I18n.format(lang, "tooltips.line.unit_price",
        Collections.<String, Object>singletonMap("unit", unit));
  }

  public static String formatDurabilityLine(int damage, int maxDamage, boolean ru) {
    return formatDurabilityLine(damage, maxDamage, ru ? "ru" : "en");
  }

  public static String formatDurabilityLine(int damage, int maxDamage, String language) {
    if (maxDamage <= 0) return "";
    String lang = I18n.language(language);
    int remaining = Math.max(0, maxDamage - Math.max(0, damage));
    int pct = Math.max(0, Math.min(100, Math.round((remaining * 100f) / maxDamage)));
    String colorCode = pct >= 60 ? "§a" : pct >= 25 ? "§e" : "§c";
    Map<String, Object> args = new LinkedHashMap<String, Object>(8);
    args.put("color", colorCode);
    args.put("remaining", Integer.valueOf(remaining));
    args.put("max", Integer.valueOf(maxDamage));
    args.put("pct", Integer.valueOf(pct));
    return I18n.format(lang, "tooltips.line.durability", args);
  }

  private static String localizeCurrency(String raw, String lang) {
    if (raw == null || raw.isEmpty()) return "";
    if ("__coins__".equals(raw)) return I18n.text(lang, "tooltips.currency.coins");
    if ("__sapphires__".equals(raw)) return I18n.text(lang, "tooltips.currency.sapphires");
    return raw;
  }

  public static boolean isVanillaShulkerContentLine(String rawLine) {
    String clean = stripColors(rawLine);
    if (clean.isEmpty()) return false;
    if (VANILLA_SHULKER_MORE.matcher(clean).matches()) return true;
    if (VANILLA_SHULKER_COUNT.matcher(clean).matches()) {
      String lower = clean.toLowerCase(Locale.ROOT);
      if (lower.contains("цена") || lower.contains("price") || lower.contains("урон")
          || lower.contains("радиус") || lower.contains("эффект")) {
        return false;
      }
      return true;
    }
    return false;
  }

  public static List<PeekEntry> compactEntries(List<PeekEntry> rawSlots) {
    if (rawSlots == null || rawSlots.isEmpty()) return Collections.emptyList();
    Map<String, PeekEntry> merged = new LinkedHashMap<String, PeekEntry>();
    for (int i = 0; i < rawSlots.size(); i++) {
      PeekEntry e = rawSlots.get(i);
      if (e == null || e.empty()) continue;
      String key = e.itemId + "|" + stripColors(e.displayName) + "|" + e.enchanted
          + "|" + (e.maxDamage > 0 ? e.damage : 0);
      PeekEntry prev = merged.get(key);
      if (prev == null) {
        merged.put(key, new PeekEntry(merged.size(), e.cacheSlot, e.itemId, e.displayName, e.count,
            e.damage, e.maxDamage, e.enchanted));
      } else {
        merged.put(key, new PeekEntry(prev.slotIndex, prev.cacheSlot, prev.itemId, prev.displayName,
            prev.count + e.count, prev.damage, prev.maxDamage, prev.enchanted));
      }
    }
    List<PeekEntry> out = new ArrayList<PeekEntry>(merged.values());
    Collections.sort(out, new Comparator<PeekEntry>() {
      @Override
      public int compare(PeekEntry a, PeekEntry b) {
        int c = Integer.compare(b.count, a.count);
        if (c != 0) return c;
        return a.itemId.compareTo(b.itemId);
      }
    });
    return out;
  }

  public static String formatItemCount(int count) {
    if (count <= 1) return "";
    if (count >= 10000) return String.format(Locale.ROOT, "%.0fk", count / 1000.0);
    if (count >= 1000) return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
    return Integer.toString(count);
  }

  public static String formatCompactPrice(double value) {
    if (value >= 1_000_000_000.0) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
    if (value >= 1_000_000.0) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
    if (value >= 10_000.0) return formatIntSpaced(Math.round(value));
    if (value >= 100.0 || Math.abs(value - Math.rint(value)) < 0.05) {
      return formatIntSpaced(Math.round(value));
    }
    return String.format(Locale.ROOT, "%.1f", value);
  }

  public static String formatIntSpaced(long n) {
    String s = Long.toString(Math.abs(n));
    StringBuilder out = new StringBuilder();
    if (n < 0) out.append('-');
    int len = s.length();
    for (int i = 0; i < len; i++) {
      if (i > 0 && (len - i) % 3 == 0) out.append(' ');
      out.append(s.charAt(i));
    }
    return out.toString();
  }

  public static double parseHumanNumber(String raw) {
    if (raw == null || raw.isEmpty()) return -1.0;
    String s = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("'", "");
    double mult = 1.0;
    if (s.endsWith("млрд")) { mult = 1_000_000_000.0; s = s.substring(0, s.length() - 4); }
    else if (s.endsWith("млн")) { mult = 1_000_000.0; s = s.substring(0, s.length() - 3); }
    else if (s.endsWith("кк") || s.endsWith("kk")) { mult = 1_000_000.0; s = s.substring(0, s.length() - 2); }
    else if (s.endsWith("b")) { mult = 1_000_000_000.0; s = s.substring(0, s.length() - 1); }
    else if (s.endsWith("m") || s.endsWith("м")) { mult = 1_000_000.0; s = s.substring(0, s.length() - 1); }
    else if (s.endsWith("k") || s.endsWith("к")) { mult = 1_000.0; s = s.substring(0, s.length() - 1); }

    int dots = countChar(s, '.');
    int commas = countChar(s, ',');
    if (dots > 0 && commas > 0) {
      if (s.lastIndexOf('.') > s.lastIndexOf(',')) {
        s = s.replace(",", "");
      } else {
        s = s.replace(".", "").replace(',', '.');
      }
    } else {
      if (dots > 1 || (dots == 1 && s.length() - s.indexOf('.') - 1 == 3 && mult == 1.0)) {
        s = s.replace(".", "");
      }
      if (commas > 1 || (commas == 1 && s.length() - s.indexOf(',') - 1 == 3 && mult == 1.0)) {
        s = s.replace(",", "");
      }
      s = s.replace(',', '.');
    }
    try {
      double val = Double.parseDouble(s);
      return Double.isFinite(val) && val > 0 ? val * mult : -1.0;
    } catch (NumberFormatException ignored) {
      return -1.0;
    }
  }

  private static String normalizeCurrency(String captured, String fullLine) {
    if (captured != null && !captured.trim().isEmpty()) {
      String c = captured.trim();
      String l = c.toLowerCase(Locale.ROOT);
      if (l.startsWith("монет") || l.startsWith("coin")) return "__coins__";
      if (l.startsWith("руб") || c.equals("₽")) return "₽";
      if (l.startsWith("сапфир")) return "__sapphires__";
      return c;
    }
    if (fullLine.contains("$")) return "$";
    if (fullLine.contains("₽")) return "₽";
    if (fullLine.contains("|❘|")) return "|❘|";
    return "$";
  }

  private static int countChar(String s, char ch) {
    int c = 0;
    for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ch) c++;
    return c;
  }

  private static boolean containsAny(String haystack, String... needles) {
    for (int i = 0; i < needles.length; i++) {
      if (haystack.contains(needles[i])) return true;
    }
    return false;
  }

  public static final int SPAWNER_HIGHLIGHT_FARMER = 0x6600FF00;
  public static final int SPAWNER_HIGHLIGHT_NO_FARMER = 0x66FF0000;

  private static final Pattern EXCHANGE_UNIT_PATTERN = Pattern.compile(
      "(?:курс|цена|за\\s*1\\s*коин)[^0-9]*([0-9][0-9\\s.,_']*(?:[kKmMbBкКмМ]|млн|млрд)?)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern EXCHANGE_PER_COIN_PREFIX = Pattern.compile(
      "([0-9][0-9\\s.,_']*(?:[kKmMbBкКмМ]|млн|млрд)?)\\s*(?:¤|монет\\w*)?\\s*за\\s*1\\s*коин",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern EXCHANGE_AMOUNT_PATTERN = Pattern.compile(
      "(?:доступно|ожидается|количество|коинов|объ[её]м)[^0-9]*([0-9][0-9\\s.,_']*)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern KNOCKBACK_LINE_PATTERN = Pattern.compile(
      "^(?:[▪•·\\-*+]\\s*)?(?:отдача|knockback)(?:\\s+[ivx0-9]+)?\\s*$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Map<String, String> RU_MOB_NAMES = buildRuMobNames();
  private static final Map<String, String> RU_VANILLA_ENCHANTS = buildRuVanillaEnchants();
  private static final Map<String, String> RU_CUSTOM_ENCHANTS = buildRuCustomEnchants();
  private static final Map<String, String> EN_CUSTOM_ENCHANTS = buildEnCustomEnchants();
  private static final Map<String, String> RU_SWORD_MATERIALS = buildRuSwordMaterials();
  private static final Map<String, String> EN_SWORD_MATERIALS = buildEnSwordMaterials();

  private static Map<String, String> buildRuMobNames() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("zombie", "Зомби");
    m.put("skeleton", "Скелет");
    m.put("creeper", "Крипер");
    m.put("spider", "Паук");
    m.put("cave_spider", "Пещерный паук");
    m.put("enderman", "Эндермен");
    m.put("blaze", "Ифрит");
    m.put("wither_skeleton", "Визер-скелет");
    m.put("magma_cube", "Магмовый куб");
    m.put("slime", "Слизень");
    m.put("pig", "Свинья");
    m.put("cow", "Корова");
    m.put("sheep", "Овца");
    m.put("chicken", "Курица");
    m.put("witch", "Ведьма");
    m.put("iron_golem", "Железный голем");
    m.put("guardian", "Страж");
    m.put("drowned", "Утопленник");
    m.put("husk", "Кадавр");
    m.put("stray", "Зимогор");
    m.put("phantom", "Фантом");
    m.put("pillager", "Разбойник");
    m.put("vindicator", "Поборник");
    m.put("evoker", "Вызыватель");
    m.put("hoglin", "Хоглин");
    m.put("piglin", "Пиглин");
    m.put("zombified_piglin", "Зомбифицированный пиглин");
    m.put("ghast", "Гаст");
    m.put("shulker", "Шалкер");
    m.put("silverfish", "Чешуйница");
    m.put("villager", "Житель");
    m.put("breeze", "Вихрь");
    m.put("bogged", "Болотник");
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, String> buildRuVanillaEnchants() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("sharpness", "Острота");
    m.put("smite", "Небесная кара");
    m.put("bane_of_arthropods", "Бич членистоногих");
    m.put("looting", "Добыча");
    m.put("sweeping_edge", "Разящий клинок");
    m.put("sweeping", "Разящий клинок");
    m.put("fire_aspect", "Заговор огня");
    m.put("knockback", "Отдача");
    m.put("unbreaking", "Прочность");
    m.put("mending", "Починка");
    m.put("vanishing_curse", "Проклятие утраты");
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, String> buildRuCustomEnchants() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("mob-farmer-enchant", "Фармер");
    m.put("critical-enchant-custom", "Критический");
    m.put("destroyer-enchant-custom", "Разрушитель");
    m.put("rich-enchant-custom", "Богач");
    m.put("exp-enchant-custom", "Опытный");
    m.put("drill-enchant-custom", "Бур");
    m.put("foundry-enchant-custom", "Плавка");
    m.put("internal-enchant-custom", "Внутренний");
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, String> buildEnCustomEnchants() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("mob-farmer-enchant", "Farmer");
    m.put("critical-enchant-custom", "Critical");
    m.put("destroyer-enchant-custom", "Destroyer");
    m.put("rich-enchant-custom", "Rich");
    m.put("exp-enchant-custom", "Experienced");
    m.put("drill-enchant-custom", "Drill");
    m.put("foundry-enchant-custom", "Foundry");
    m.put("internal-enchant-custom", "Internal");
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, String> buildRuSwordMaterials() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("netherite_sword", "Незеритовый меч");
    m.put("diamond_sword", "Алмазный меч");
    m.put("iron_sword", "Железный меч");
    m.put("golden_sword", "Золотой меч");
    m.put("stone_sword", "Каменный меч");
    m.put("wooden_sword", "Деревянный меч");
    return Collections.unmodifiableMap(m);
  }

  private static Map<String, String> buildEnSwordMaterials() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("netherite_sword", "Netherite Sword");
    m.put("diamond_sword", "Diamond Sword");
    m.put("iron_sword", "Iron Sword");
    m.put("golden_sword", "Golden Sword");
    m.put("stone_sword", "Stone Sword");
    m.put("wooden_sword", "Wooden Sword");
    return Collections.unmodifiableMap(m);
  }

  public static final class SpawnerEnchant {
    public final String key;
    public final int level;
    public final boolean custom;
    public final boolean farmer;

    public SpawnerEnchant(String key, int level, boolean custom, boolean farmer) {
      this.key = key == null ? "" : key;
      this.level = level;
      this.custom = custom;
      this.farmer = farmer;
    }
  }

  public static final class GoldenSpawnerInfo {
    public final String mobType;
    public final int killsTotal;
    public final int killsRemaining;
    public final int totalEggs;
    public final boolean hasSword;
    public final String swordId;
    public final String swordCustomName;
    public final boolean hasFarmerEnchant;
    public final List<SpawnerEnchant> enchants;

    public GoldenSpawnerInfo(String mobType, int killsTotal, int killsRemaining, int totalEggs,
        boolean hasSword, String swordId, String swordCustomName, boolean hasFarmerEnchant,
        List<SpawnerEnchant> enchants) {
      this.mobType = mobType == null ? "" : mobType;
      this.killsTotal = killsTotal;
      this.killsRemaining = killsRemaining;
      this.totalEggs = totalEggs;
      this.hasSword = hasSword;
      this.swordId = swordId == null ? "" : swordId;
      this.swordCustomName = swordCustomName == null ? "" : swordCustomName;
      this.hasFarmerEnchant = hasFarmerEnchant;
      this.enchants = enchants == null ? Collections.<SpawnerEnchant>emptyList() : enchants;
    }
  }

  public static GoldenSpawnerInfo parseGoldenSpawner(
      String mobType, int killsTotal, int killsRemaining, int totalEggs, String swordJson) {
    boolean hasSword = false;
    String swordId = "";
    String swordName = "";
    boolean hasFarmer = false;
    List<SpawnerEnchant> enchants = new ArrayList<SpawnerEnchant>();

    if (swordJson != null && !swordJson.trim().isEmpty()) {
      try {
        JsonElement rootEl = new JsonParser().parse(swordJson);
        if (rootEl != null && rootEl.isJsonObject()) {
          JsonObject root = rootEl.getAsJsonObject();
          swordId = readJsonString(root, "id");
          if (swordId.isEmpty()) swordId = readJsonString(root, "type");
          if (!swordId.isEmpty() || root.has("components") || root.has("tag") || root.has("meta")) {
            hasSword = true;
          }

          JsonObject components = readJsonObject(root, "components");
          if (components != null) {
            if (components.has("minecraft:custom_name")) {
              swordName = extractComponentPlainText(components.get("minecraft:custom_name"));
            }
            JsonObject enchObj = readJsonObject(components, "minecraft:enchantments");
            if (enchObj != null) {
              JsonObject levels = readJsonObject(enchObj, "levels");
              collectEnchantMap(levels != null ? levels : enchObj, false, enchants);
            }
            JsonObject customData = readJsonObject(components, "minecraft:custom_data");
            if (customData != null) {
              hasFarmer |= collectBukkitCustomEnchants(customData, enchants);
            }
          }

          JsonObject tag = readJsonObject(root, "tag");
          if (tag != null) {
            JsonObject display = readJsonObject(tag, "display");
            if (swordName.isEmpty() && display != null && display.has("Name")) {
              swordName = extractComponentPlainText(display.get("Name"));
            }
            if (tag.has("Enchantments") && tag.get("Enchantments").isJsonArray()) {
              for (JsonElement el : tag.getAsJsonArray("Enchantments")) {
                if (el != null && el.isJsonObject()) {
                  JsonObject eo = el.getAsJsonObject();
                  String eid = normalizeEnchantKey(readJsonString(eo, "id"));
                  int lvl = readJsonInt(eo, "lvl", 1);
                  if (!eid.isEmpty()) {
                    enchants.add(new SpawnerEnchant(eid, lvl, false, false));
                  }
                }
              }
            }
            hasFarmer |= collectBukkitCustomEnchants(tag, enchants);
          }

          JsonObject meta = readJsonObject(root, "meta");
          if (meta != null) {
            if (swordName.isEmpty() && meta.has("display-name")) {
              swordName = extractComponentPlainText(meta.get("display-name"));
            }
            JsonObject metaEnch = readJsonObject(meta, "enchants");
            if (metaEnch != null) {
              collectEnchantMap(metaEnch, false, enchants);
            }
            hasFarmer |= collectBukkitCustomEnchants(meta, enchants);
          }
          if (!hasFarmer && swordJson.toLowerCase(Locale.ROOT).contains("mob-farmer-enchant")) {
            hasFarmer = true;
          }
        }
      } catch (Throwable ignored) {
        if (swordJson.toLowerCase(Locale.ROOT).contains("sword")) hasSword = true;
        if (swordJson.toLowerCase(Locale.ROOT).contains("mob-farmer-enchant")) hasFarmer = true;
      }
    }

    return new GoldenSpawnerInfo(
        mobType == null ? "" : mobType.trim(),
        Math.max(0, killsTotal),
        Math.max(0, killsRemaining),
        Math.max(0, totalEggs),
        hasSword,
        swordId,
        stripColors(swordName),
        hasFarmer,
        enchants);
  }

  public static int spawnerSlotHighlightColor(boolean hasMarker, String swordJson) {
    if (!hasMarker || swordJson == null || swordJson.trim().isEmpty()) return 0;
    GoldenSpawnerInfo info = parseGoldenSpawner("", 0, 0, 0, swordJson);
    if (!info.hasSword) return 0;
    return info.hasFarmerEnchant ? SPAWNER_HIGHLIGHT_FARMER : SPAWNER_HIGHLIGHT_NO_FARMER;
  }

  public static List<String> formatGoldenSpawnerLines(GoldenSpawnerInfo info, String language) {
    if (info == null) return Collections.emptyList();
    String lang = I18n.language(language);
    boolean ru = "ru".equals(lang);
    List<String> out = new ArrayList<String>();
    out.add(I18n.text(lang, "tooltips.spawner.header"));
    if (!info.mobType.isEmpty()) {
      String mobDisplay = formatMobName(info.mobType, ru);
      out.add(I18n.format(lang, "tooltips.spawner.mob",
          Collections.<String, Object>singletonMap("mob", mobDisplay)));
    }
    if (info.killsTotal > 0 || info.killsRemaining > 0) {
      Map<String, Object> args = new LinkedHashMap<String, Object>(4);
      args.put("total", formatIntSpaced(info.killsTotal));
      args.put("remaining", formatIntSpaced(info.killsRemaining));
      out.add(I18n.format(lang, "tooltips.spawner.kills", args));
    }
    if (info.totalEggs > 0) {
      out.add(I18n.format(lang, "tooltips.spawner.eggs",
          Collections.<String, Object>singletonMap("eggs", formatIntSpaced(info.totalEggs))));
    }
    if (info.hasSword) {
      String matLabel = formatSwordMaterial(info.swordId, ru);
      String display;
      if (!info.swordCustomName.isEmpty() && !matLabel.isEmpty()
          && !info.swordCustomName.equalsIgnoreCase(matLabel)) {
        display = info.swordCustomName + " §8(" + matLabel + ")";
      } else if (!info.swordCustomName.isEmpty()) {
        display = info.swordCustomName;
      } else {
        display = matLabel.isEmpty() ? (ru ? "Меч" : "Sword") : matLabel;
      }
      out.add(I18n.format(lang, "tooltips.spawner.sword",
          Collections.<String, Object>singletonMap("sword", display)));
      if (!info.enchants.isEmpty()) {
        out.add(I18n.text(lang, "tooltips.spawner.enchants"));
        for (int i = 0; i < info.enchants.size(); i++) {
          SpawnerEnchant se = info.enchants.get(i);
          String name = formatEnchantName(se.key, se.custom, ru);
          String roman = se.level > 0 ? " " + toRoman(se.level) : "";
          String color = se.farmer ? "§a§l" : (se.custom ? "§6" : "§b");
          out.add("  §8▪ " + color + name + roman);
        }
      }
    } else {
      out.add(I18n.text(lang, "tooltips.spawner.no_sword"));
    }
    return out;
  }

  public static List<String> formatSpecialCompassLines(
      long delayUntilMs, boolean hasTarget, int tx, Integer ty, int tz, long nowMs, String language) {
    String lang = I18n.language(language);
    boolean ru = "ru".equals(lang);
    List<String> out = new ArrayList<String>(2);
    long remainingMs = delayUntilMs - nowMs;
    if (remainingMs <= 0L) {
      out.add(I18n.text(lang, "tooltips.compass.ready"));
    } else {
      long totalSec = (remainingMs + 999L) / 1000L;
      long min = totalSec / 60L;
      long sec = totalSec % 60L;
      String timeStr;
      if (min > 0L) {
        timeStr = ru ? (min + " мин " + sec + " сек") : (min + "m " + sec + "s");
      } else {
        timeStr = ru ? (sec + " сек") : (sec + "s");
      }
      out.add(I18n.format(lang, "tooltips.compass.cooldown",
          Collections.<String, Object>singletonMap("time", timeStr)));
    }
    if (hasTarget) {
      String coords = ty != null ? (tx + ", " + ty + ", " + tz) : (tx + ", " + tz);
      out.add(I18n.format(lang, "tooltips.compass.target",
          Collections.<String, Object>singletonMap("coords", coords)));
    }
    return out;
  }

  public static final class ExchangeOrderInfo {
    public final double orderRate;
    public final double diffPercent;
    public final double coinAmount;
    public final double expectedPayout;
    public final int rateLineIndex;

    public ExchangeOrderInfo(double orderRate, double diffPercent, double coinAmount,
        double expectedPayout, int rateLineIndex) {
      this.orderRate = orderRate;
      this.diffPercent = diffPercent;
      this.coinAmount = coinAmount;
      this.expectedPayout = expectedPayout;
      this.rateLineIndex = rateLineIndex;
    }
  }

  public static ExchangeOrderInfo parseExchangeOrder(List<String> rawLines, long marketRate) {
    if (rawLines == null || rawLines.isEmpty() || marketRate <= 0L) return null;
    double orderRate = -1.0;
    int rateLineIdx = -1;
    double coinAmount = -1.0;

    for (int i = 0; i < rawLines.size(); i++) {
      String clean = stripColors(rawLines.get(i));
      if (clean.isEmpty()) continue;
      String lower = clean.toLowerCase(Locale.ROOT);
      if (orderRate <= 0.0) {
        Matcher mPrefix = EXCHANGE_PER_COIN_PREFIX.matcher(clean);
        if (mPrefix.find()) {
          double val = parseHumanNumber(mPrefix.group(1));
          if (val > 0.0) {
            orderRate = val;
            rateLineIdx = i;
          }
        } else if (lower.contains("курс") || lower.contains("за 1 коин")) {
          Matcher mUnit = EXCHANGE_UNIT_PATTERN.matcher(clean);
          if (mUnit.find()) {
            double val = parseHumanNumber(mUnit.group(1));
            if (val > 0.0) {
              orderRate = val;
              rateLineIdx = i;
            }
          }
        }
      }
      if (coinAmount <= 0.0
          && (lower.contains("доступно") || lower.contains("ожидается")
              || lower.contains("количество") || lower.contains("коинов"))) {
        Matcher mAmt = EXCHANGE_AMOUNT_PATTERN.matcher(clean);
        if (mAmt.find()) {
          double val = parseHumanNumber(mAmt.group(1));
          if (val > 0.0) {
            coinAmount = val;
          }
        }
      }
    }
    if (orderRate <= 0.0) return null;
    double diffPct = ((orderRate - marketRate) / (double) marketRate) * 100.0;
    double payout = coinAmount > 0.0 ? coinAmount * orderRate : -1.0;
    return new ExchangeOrderInfo(orderRate, diffPct, coinAmount, payout, rateLineIdx);
  }

  public static String formatExchangeDiffSuffix(ExchangeOrderInfo info, String language) {
    if (info == null) return "";
    String lang = I18n.language(language);
    String color;
    if (info.diffPercent <= -5.0) color = "§a";
    else if (info.diffPercent >= 5.0) color = "§c";
    else color = "§e";
    String pctStr = String.format(Locale.ROOT, "%+.1f", info.diffPercent);
    Map<String, Object> args = new LinkedHashMap<String, Object>(4);
    args.put("color", color);
    args.put("pct", pctStr);
    return " " + I18n.format(lang, "tooltips.exchange.diff", args);
  }

  public static String formatExchangePayoutLine(ExchangeOrderInfo info, String language) {
    if (info == null || info.expectedPayout <= 0.0) return "";
    String lang = I18n.language(language);
    return I18n.format(lang, "tooltips.exchange.payout",
        Collections.<String, Object>singletonMap("amount", formatCompactPrice(info.expectedPayout)));
  }

  public static boolean isSwordId(String itemId) {
    if (itemId == null) return false;
    String l = itemId.toLowerCase(Locale.ROOT);
    return l.endsWith("_sword") || l.equals("sword");
  }

  public static boolean isKnockbackEnchantLine(String rawLine) {
    String clean = stripColors(rawLine);
    if (clean.isEmpty()) return false;
    return KNOCKBACK_LINE_PATTERN.matcher(clean).matches();
  }

  private static boolean collectBukkitCustomEnchants(JsonObject container, List<SpawnerEnchant> out) {
    boolean foundFarmer = false;
    JsonObject pbv = readJsonObject(container, "PublicBukkitValues");
    if (pbv == null) pbv = readJsonObject(container, "publicBukkitValues");
    if (pbv == null) return false;
    for (Map.Entry<String, JsonElement> entry : pbv.entrySet()) {
      String rawKey = entry.getKey();
      if (rawKey == null) continue;
      String shortKey = normalizeEnchantKey(rawKey);
      if (shortKey.equals("custom-enchantments") && entry.getValue() != null) {
        String val = entry.getValue().isJsonPrimitive()
            ? entry.getValue().getAsString()
            : entry.getValue().toString();
        foundFarmer |= parseCustomEnchantBlob(val, out);
        continue;
      }
      if (RU_CUSTOM_ENCHANTS.containsKey(shortKey) || shortKey.contains("enchant")) {
        int lvl = entry.getValue() != null && entry.getValue().isJsonPrimitive()
            ? safeParseInt(entry.getValue().getAsString(), 1)
            : 1;
        boolean farmer = shortKey.equals("mob-farmer-enchant");
        if (farmer) foundFarmer = true;
        out.add(new SpawnerEnchant(shortKey, lvl, true, farmer));
      }
    }
    return foundFarmer;
  }

  private static boolean parseCustomEnchantBlob(String blob, List<SpawnerEnchant> out) {
    if (blob == null || blob.trim().isEmpty()) return false;
    boolean foundFarmer = false;
    String s = blob.trim();
    if (s.startsWith("{")) {
      try {
        JsonElement el = new JsonParser().parse(s);
        if (el != null && el.isJsonObject()) {
          for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            String k = normalizeEnchantKey(e.getKey());
            int lvl = e.getValue() != null && e.getValue().isJsonPrimitive()
                ? safeParseInt(e.getValue().getAsString(), 1)
                : 1;
            boolean farmer = k.equals("mob-farmer-enchant");
            if (farmer) foundFarmer = true;
            out.add(new SpawnerEnchant(k, lvl, true, farmer));
          }
          return foundFarmer;
        }
      } catch (Throwable ignored) {
      }
    }
    String[] parts = s.split("[,;\\n]");
    for (String part : parts) {
      String p = part.trim();
      if (p.isEmpty()) continue;
      String[] kv = p.split("[:=]");
      String k = normalizeEnchantKey(kv[0]);
      int lvl = kv.length > 1 ? safeParseInt(kv[1].trim(), 1) : 1;
      boolean farmer = k.equals("mob-farmer-enchant");
      if (farmer) foundFarmer = true;
      out.add(new SpawnerEnchant(k, lvl, true, farmer));
    }
    return foundFarmer;
  }

  private static void collectEnchantMap(JsonObject obj, boolean custom, List<SpawnerEnchant> out) {
    if (obj == null) return;
    for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
      String key = normalizeEnchantKey(e.getKey());
      if (key.isEmpty() || key.equals("show_in_tooltip")) continue;
      int lvl = e.getValue() != null && e.getValue().isJsonPrimitive()
          ? safeParseInt(e.getValue().getAsString(), 1)
          : 1;
      out.add(new SpawnerEnchant(key, lvl, custom, key.equals("mob-farmer-enchant")));
    }
  }

  private static String normalizeEnchantKey(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT);
    int colon = s.lastIndexOf(':');
    if (colon >= 0 && colon + 1 < s.length()) {
      s = s.substring(colon + 1);
    }
    return s;
  }

  private static String formatMobName(String rawMob, boolean ru) {
    String key = normalizeEnchantKey(rawMob).replace(' ', '_');
    if (ru) {
      String mapped = RU_MOB_NAMES.get(key);
      if (mapped != null) return mapped;
    }
    return titleCaseWords(key.replace('_', ' '));
  }

  private static String formatSwordMaterial(String swordId, boolean ru) {
    String key = normalizeEnchantKey(swordId);
    String mapped = ru ? RU_SWORD_MATERIALS.get(key) : EN_SWORD_MATERIALS.get(key);
    if (mapped != null) return mapped;
    return key.isEmpty() ? "" : titleCaseWords(key.replace('_', ' '));
  }

  private static String formatEnchantName(String key, boolean custom, boolean ru) {
    if (custom) {
      String mapped = ru ? RU_CUSTOM_ENCHANTS.get(key) : EN_CUSTOM_ENCHANTS.get(key);
      if (mapped != null) return mapped;
    } else if (ru) {
      String mapped = RU_VANILLA_ENCHANTS.get(key);
      if (mapped != null) return mapped;
    }
    return titleCaseWords(key.replace('_', ' ').replace('-', ' '));
  }

  private static String titleCaseWords(String text) {
    if (text == null || text.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(text.length());
    boolean cap = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        cap = true;
        sb.append(c);
      } else {
        sb.append(cap ? Character.toUpperCase(c) : c);
        cap = false;
      }
    }
    return sb.toString();
  }

  private static String toRoman(int num) {
    if (num <= 0) return "";
    switch (num) {
      case 1: return "I";
      case 2: return "II";
      case 3: return "III";
      case 4: return "IV";
      case 5: return "V";
      case 6: return "VI";
      case 7: return "VII";
      case 8: return "VIII";
      case 9: return "IX";
      case 10: return "X";
      default: return Integer.toString(num);
    }
  }

  private static String extractComponentPlainText(JsonElement el) {
    if (el == null || el.isJsonNull()) return "";
    if (el.isJsonPrimitive()) {
      String s = el.getAsString();
      if (s.startsWith("{") || s.startsWith("[")) {
        try {
          return extractComponentPlainText(new JsonParser().parse(s));
        } catch (Throwable ignored) {
          return s;
        }
      }
      return s;
    }
    StringBuilder sb = new StringBuilder();
    if (el.isJsonArray()) {
      for (JsonElement child : el.getAsJsonArray()) {
        sb.append(extractComponentPlainText(child));
      }
      return sb.toString();
    }
    if (el.isJsonObject()) {
      JsonObject o = el.getAsJsonObject();
      if (o.has("text") && o.get("text").isJsonPrimitive()) {
        sb.append(o.get("text").getAsString());
      }
      if (o.has("extra") && o.get("extra").isJsonArray()) {
        for (JsonElement child : o.getAsJsonArray("extra")) {
          sb.append(extractComponentPlainText(child));
        }
      }
    }
    return sb.toString();
  }

  private static JsonObject readJsonObject(JsonObject obj, String key) {
    if (obj == null || !obj.has(key)) return null;
    JsonElement el = obj.get(key);
    return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
  }

  private static String readJsonString(JsonObject obj, String key) {
    if (obj == null || !obj.has(key)) return "";
    JsonElement el = obj.get(key);
    return el != null && el.isJsonPrimitive() ? el.getAsString() : "";
  }

  private static int readJsonInt(JsonObject obj, String key, int fallback) {
    if (obj == null || !obj.has(key)) return fallback;
    JsonElement el = obj.get(key);
    if (el == null || !el.isJsonPrimitive()) return fallback;
    return safeParseInt(el.getAsString(), fallback);
  }

  private static int safeParseInt(String raw, int fallback) {
    if (raw == null) return fallback;
    String s = raw.trim();
    if (s.endsWith("s") || s.endsWith("b") || s.endsWith("L") || s.endsWith("l")) {
      s = s.substring(0, s.length() - 1);
    }
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
