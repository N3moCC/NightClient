package dev.nightclient.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.PotionView;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HwPvpAndEventsLogic {
  private static final Pattern SERVER_NUM_PATTERN = Pattern.compile("(\\d+)\\s*$");
  private static final Pattern HOLO_MMSS = Pattern.compile("(\\d{1,2}):(\\d{2})(?!\\d)");
  private static final Pattern HOLO_SEC = Pattern.compile(
      "(\\d{1,2}(?:[.,]\\d)?)\\s*(?:сек(?:унд[аы]?)?|с|sec|s)(?![a-zа-яё0-9])",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern CHAT_COORDS = Pattern.compile(
      "\\[?\\s*(-?\\d{1,5})\\s*[,;\\s]+(\\d{1,3})\\s*[,;\\s]+(-?\\d{1,5})\\s*\\]?");
  private static final Pattern STUN_WORD = Pattern.compile(
      "(?<![a-zа-яё0-9])(?:стан|stun|оглуш\\w*)(?![a-zа-яё0-9])",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private HwPvpAndEventsLogic() {}

  public static final class ServerInfo {
    public final String serverKey;
    public final String displayName;
    public final String mode;
    public final int anarchyNumber;
    public final boolean lite121;

    public ServerInfo(String serverKey, String displayName, String mode, int anarchyNumber,
        boolean lite121) {
      this.serverKey = serverKey;
      this.displayName = displayName;
      this.mode = mode;
      this.anarchyNumber = anarchyNumber;
      this.lite121 = lite121;
    }
  }

  public static final class EventItem {
    public final String instanceId;
    public final String eventTypeId;
    public final String title;
    public final String serverKey;
    public final String serverName;
    public final String mode;
    public final int anarchyNumber;
    public final boolean lite121;
    public final String rarityTier;
    public final String rawRarity;
    public final int rarityColor;
    public final long firstSeenMs;
    public final String coordsHint;

    public EventItem(String instanceId, String eventTypeId, String title, String serverKey,
        String serverName, String mode, int anarchyNumber, boolean lite121, String rarityTier,
        String rawRarity, int rarityColor, long firstSeenMs, String coordsHint) {
      this.instanceId = instanceId;
      this.eventTypeId = eventTypeId;
      this.title = title;
      this.serverKey = serverKey;
      this.serverName = serverName;
      this.mode = mode;
      this.anarchyNumber = anarchyNumber;
      this.lite121 = lite121;
      this.rarityTier = rarityTier;
      this.rawRarity = rawRarity;
      this.rarityColor = rarityColor;
      this.firstSeenMs = firstSeenMs;
      this.coordsHint = coordsHint == null ? "" : coordsHint;
    }

    public String rarityBadge(boolean ru) {
      return rarityBadge(ru ? "ru" : "en");
    }

    public String rarityBadge(String language) {
      String lang = I18n.language(language);
      if ("legendary".equals(rarityTier)) return I18n.text(lang, "hwEvents.rarity_badge.legendary");
      if ("mythic".equals(rarityTier)) return I18n.text(lang, "hwEvents.rarity_badge.mythic");
      if ("rare".equals(rarityTier)) return I18n.text(lang, "hwEvents.rarity_badge.rare");
      return I18n.text(lang, "hwEvents.rarity_badge.common");
    }

    public String elapsedText(long nowMs, boolean ru) {
      return elapsedText(nowMs, ru ? "ru" : "en");
    }

    public String elapsedText(long nowMs, String language) {
      String lang = I18n.language(language);
      long sec = Math.max(0L, (nowMs - firstSeenMs) / 1000L);
      long min = sec / 60L;
      long rem = sec % 60L;
      if (min > 0L) {
        Map<String, Object> args = new LinkedHashMap<String, Object>(4);
        args.put("min", Long.valueOf(min));
        args.put("sec", Long.valueOf(rem));
        return I18n.format(lang, "hwEvents.elapsed_min_sec", args);
      }
      return I18n.format(lang, "units.duration.seconds",
          Collections.<String, Object>singletonMap("value", Long.valueOf(sec)));
    }

    public String localizedTitle(String language) {
      if (title != null && title.startsWith("hwEvents.type.")) {
        return I18n.text(language, title);
      }
      if (title == null || title.isEmpty()) {
        return fallbackEventTitle(eventTypeId, language);
      }
      return title;
    }

    public String localizedServerName(String language) {
      if (serverName != null && serverName.startsWith("__lite121__:")) {
        return I18n.format(language, "hwEvents.server.lite121",
            Collections.<String, Object>singletonMap("num", serverName.substring("__lite121__:".length())));
      }
      if (serverName != null && serverName.startsWith("__cat__:")) {
        int sep = serverName.indexOf(':', "__cat__:".length());
        if (sep > 0) {
          String cat = serverName.substring("__cat__:".length(), sep);
          String num = serverName.substring(sep + 1);
          return I18n.text(language, "hwhelper.cat." + cat) + " #" + num;
        }
      }
      return serverName;
    }

    public boolean canAutoJoin() {
      return !lite121 && anarchyNumber >= 1;
    }
  }

  public static Map<String, ServerInfo> parseServersJson(String json) {
    if (json == null || json.trim().isEmpty()) return Collections.emptyMap();
    JsonElement rootElem = new JsonParser().parse(json);
    if (rootElem == null || !rootElem.isJsonObject()) return Collections.emptyMap();
    JsonObject obj = rootElem.getAsJsonObject();
    Map<String, ServerInfo> out = new LinkedHashMap<String, ServerInfo>();
    for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
      String key = e.getKey();
      if (key == null || e.getValue() == null || !e.getValue().isJsonPrimitive()) continue;
      String name = e.getValue().getAsString();
      out.put(key, classifyServer(key, name));
    }
    return out;
  }

  public static ServerInfo classifyServer(String serverKey, String rawName) {
    String key = serverKey == null ? "" : serverKey.trim();
    String upper = key.toUpperCase(Locale.ROOT);
    boolean lite121 = upper.startsWith("LITE_NEW_ANARCHY_")
        || (rawName != null && rawName.contains("1.21"));
    boolean classicLite = upper.startsWith("LITE_ANARCHY_");

    int num = -1;
    Matcher m = SERVER_NUM_PATTERN.matcher(key);
    if (m.find()) {
      try {
        num = Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignored) {
      }
    }

    String name = (rawName == null || rawName.trim().isEmpty()) ? fallbackServerName(key, num) : rawName.trim();
    String lowerName = name.toLowerCase(Locale.ROOT);
    String mode;
    if (lite121) {
      mode = "lite121";
    } else if (!classicLite && !lowerName.contains("лайт")) {
      mode = "other";
    } else if (lowerName.contains("соло")) {
      mode = "solo";
    } else if (lowerName.contains("дуо")) {
      mode = "duo";
    } else if (lowerName.contains("трио")) {
      mode = "trio";
    } else if (lowerName.contains("клан")) {
      mode = "clan";
    } else if (num >= 1) {
      String cat = HwHelperManager.catOf(num);
      mode = "clans".equals(cat) ? "clan" : cat;
    } else {
      mode = "other";
    }
    return new ServerInfo(key, name, mode, num, lite121);
  }

  private static String fallbackServerName(String key, int num) {
    if (key.toUpperCase(Locale.ROOT).startsWith("LITE_NEW_ANARCHY_") && num > 0) {
      return "__lite121__:" + num;
    }
    if (key.toUpperCase(Locale.ROOT).startsWith("LITE_ANARCHY_") && num > 0) {
      return "__cat__:" + HwHelperManager.catOf(num) + ":" + num;
    }
    return key;
  }

  public static List<EventItem> parseEventsJson(String json, Map<String, ServerInfo> servers,
      Map<String, Long> firstSeenByInstance, Map<String, String> chatCoordsByType, long nowMs) {
    if (json == null || json.trim().isEmpty()) return Collections.emptyList();
    JsonElement rootElem = new JsonParser().parse(json);
    if (rootElem == null || !rootElem.isJsonObject()) return Collections.emptyMap().isEmpty()
        ? Collections.<EventItem>emptyList()
        : Collections.<EventItem>emptyList();
    JsonObject root = rootElem.getAsJsonObject();
    List<EventItem> list = new ArrayList<EventItem>();

    for (Map.Entry<String, JsonElement> serverEntry : root.entrySet()) {
      String serverKey = serverEntry.getKey();
      JsonElement arrElem = serverEntry.getValue();
      if (serverKey == null || arrElem == null || !arrElem.isJsonArray()) continue;

      ServerInfo sInfo = servers != null ? servers.get(serverKey) : null;
      if (sInfo == null) sInfo = classifyServer(serverKey, "");
      if ("other".equals(sInfo.mode)) continue;

      JsonArray arr = arrElem.getAsJsonArray();
      for (int i = 0; i < arr.size(); i++) {
        JsonElement itemElem = arr.get(i);
        if (itemElem == null || !itemElem.isJsonObject()) continue;
        JsonObject ev = itemElem.getAsJsonObject();
        String id = getStr(ev, "id", "EVENT");
        String instanceId = getStr(ev, "instanceId", serverKey + ":" + id + ":" + i);
        String displayName = getStr(ev, "displayName", getStr(ev, "title", ""));
        String rawRare = getStr(ev, "rare", getStr(ev, "rarity", "default"));
        if (ev.has("metadata") && ev.get("metadata").isJsonObject()) {
          JsonObject meta = ev.getAsJsonObject("metadata");
          displayName = getStr(meta, "displayName", getStr(meta, "title", displayName));
          rawRare = getStr(meta, "rare", getStr(meta, "rarity", rawRare));
        }
        if (displayName.isEmpty()) {
          displayName = fallbackEventTitleKey(id);
        }

        String rarityTier = classifyRarityTier(rawRare, id, displayName);
        int rarityColor = rarityColorFor(rarityTier);

        long seenAt = nowMs;
        if (firstSeenByInstance != null) {
          Long prev = firstSeenByInstance.get(instanceId);
          if (prev != null) {
            seenAt = prev.longValue();
          } else {
            firstSeenByInstance.put(instanceId, Long.valueOf(nowMs));
          }
        }

        String coords = chatCoordsByType != null ? chatCoordsByType.get(id.toUpperCase(Locale.ROOT)) : "";
        list.add(new EventItem(instanceId, id, displayName, serverKey, sInfo.displayName,
            sInfo.mode, sInfo.anarchyNumber, sInfo.lite121, rarityTier, rawRare, rarityColor,
            seenAt, coords));
      }
    }

    Collections.sort(list, new Comparator<EventItem>() {
      @Override
      public int compare(EventItem a, EventItem b) {
        int ra = rarityRank(a.rarityTier);
        int rb = rarityRank(b.rarityTier);
        if (ra != rb) return Integer.compare(ra, rb);
        if (a.lite121 != b.lite121) return a.lite121 ? 1 : -1;
        return Integer.compare(a.anarchyNumber, b.anarchyNumber);
      }
    });
    return list;
  }

  public static List<EventItem> filterEvents(
      List<EventItem> all, String modeFilter, String rarityFilter) {
    if (all == null || all.isEmpty()) return Collections.emptyList();
    String mf = modeFilter == null ? "all" : modeFilter;
    String rf = rarityFilter == null ? "all" : rarityFilter;
    if ("all".equals(mf) && "all".equals(rf)) return all;
    List<EventItem> out = new ArrayList<EventItem>(all.size());
    for (int i = 0; i < all.size(); i++) {
      EventItem ev = all.get(i);
      if (!"all".equals(mf) && !mf.equals(ev.mode)) continue;
      if ("legendary".equals(rf) && !"legendary".equals(ev.rarityTier)) continue;
      if ("mythic".equals(rf)
          && !("legendary".equals(ev.rarityTier) || "mythic".equals(ev.rarityTier))) {
        continue;
      }
      if ("rare".equals(rf) && "common".equals(ev.rarityTier)) continue;
      out.add(ev);
    }
    return out;
  }

  public static String classifyRarityTier(String rawRare, String id, String title) {
    String r = (rawRare == null ? "" : rawRare).toLowerCase(Locale.ROOT);
    String t = ((id == null ? "" : id) + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
    if (r.contains("legend") || r.contains("легенд") || t.contains("легенд")) return "legendary";
    if (r.contains("myth") || r.contains("миф") || r.contains("zajit") || r.contains("epic")
        || t.contains("миф") || t.contains("корабль") || t.contains("маяк")) {
      return "mythic";
    }
    if (!r.isEmpty() && !"default".equals(r) && !"common".equals(r) && !"normal".equals(r)) {
      return "rare";
    }
    if (t.contains("вулкан") || t.contains("метеор") || t.contains("алтарь")
        || t.contains("санта")) {
      return "rare";
    }
    return "common";
  }

  public static int rarityColorFor(String tier) {
    if ("legendary".equals(tier)) return 0xFFF59E0B;
    if ("mythic".equals(tier)) return 0xFFE879F9;
    if ("rare".equals(tier)) return 0xFF38BDF8;
    return 0xFF94A3B8;
  }

  private static int rarityRank(String tier) {
    if ("legendary".equals(tier)) return 0;
    if ("mythic".equals(tier)) return 1;
    if ("rare".equals(tier)) return 2;
    return 3;
  }

  public static String fallbackEventTitle(String id) {
    return fallbackEventTitle(id, "ru");
  }

  public static String fallbackEventTitle(String id, String language) {
    String key = fallbackEventTitleKey(id);
    return key.startsWith("hwEvents.type.") ? I18n.text(language, key) : key;
  }

  private static String fallbackEventTitleKey(String id) {
    if (id == null) return "hwEvents.type.default";
    String u = id.toUpperCase(Locale.ROOT);
    if (u.contains("PARCEL")) return "hwEvents.type.parcel";
    if (u.contains("SHIP")) return "hwEvents.type.ship";
    if (u.contains("GOLDEN")) return "hwEvents.type.golden";
    if (u.contains("BEACON")) return "hwEvents.type.beacon";
    if (u.contains("VULCAN") || u.contains("VOLCAN")) return "hwEvents.type.vulcan";
    if (u.contains("METEOR")) return "hwEvents.type.meteor";
    if (u.contains("ALTAR")) return "hwEvents.type.altar";
    if (u.contains("SANTA")) return "hwEvents.type.santa";
    if (u.contains("CHEST") || u.contains("DEATH")) return "hwEvents.type.chest";
    return id;
  }

  public static void observeEventChatLine(String rawLine, Map<String, String> coordsByTypeOut) {
    if (rawLine == null || coordsByTypeOut == null) return;
    String clean = SoundControlLogic.stripFormatting(rawLine).trim();
    if (clean.isEmpty()) return;
    String lower = clean.toLowerCase(Locale.ROOT);
    String typeKey = null;
    if (lower.contains("посылк")) typeKey = "PARCELS";
    else if (lower.contains("корабл")) typeKey = "SHIP";
    else if (lower.contains("золотая лихорадк")) typeKey = "GOLDEN_FORTRESS";
    else if (lower.contains("маяк")) typeKey = "BEACON_DEATH";
    else if (lower.contains("вулкан")) typeKey = "VULCAN";
    else if (lower.contains("метеор")) typeKey = "METEOR";
    else if (lower.contains("алтар")) typeKey = "ALTAR";
    if (typeKey == null) return;

    Matcher cm = CHAT_COORDS.matcher(clean);
    if (cm.find()) {
      coordsByTypeOut.put(typeKey, cm.group(1) + ", " + cm.group(2) + ", " + cm.group(3));
    }
  }

  private static String getStr(JsonObject obj, String key, String def) {
    if (obj == null || !obj.has(key)) return def;
    JsonElement el = obj.get(key);
    return (el != null && el.isJsonPrimitive()) ? el.getAsString() : def;
  }

  public enum HwConsumableKind {
    TRAP("hwhelper.timer.trap", 0xFFF97316),
    EXPLOSIVE_TRAP("hwhelper.timer.explosive_trap", 0xFFEF4444),
    STUN("hwhelper.timer.stun", 0xFFFACC15),
    SNOWBALL("hwhelper.timer.snowball", 0xFF38BDF8),
    FAREWELL("hwhelper.timer.farewell", 0xFFC084FC);

    public final String labelKey;
    public final String labelRu;
    public final String labelEn;
    public final int color;

    HwConsumableKind(String labelKey, int color) {
      this.labelKey = labelKey;
      this.labelRu = I18n.text("ru", labelKey);
      this.labelEn = I18n.text("en", labelKey);
      this.color = color;
    }

    public String label(String language) {
      return I18n.text(language, labelKey);
    }
  }

  public static final class HwActiveTimer {
    public final HwConsumableKind kind;
    public final double x;
    public final double y;
    public final double z;
    public final boolean personal;
    public final float remainingSeconds;
    public final float totalSeconds;

    public HwActiveTimer(HwConsumableKind kind, double x, double y, double z, boolean personal,
        float remainingSeconds, float totalSeconds) {
      this.kind = kind;
      this.x = x;
      this.y = y;
      this.z = z;
      this.personal = personal;
      this.remainingSeconds = remainingSeconds;
      this.totalSeconds = Math.max(1f, totalSeconds);
    }
  }

  private static final class TimerEntry {
    String key;
    HwConsumableKind kind;
    double x, y, z;
    boolean personal;
    boolean fromHologram;
    long lastSeenAtMs;
    long expiresAtMs;
    float totalSeconds;
  }

  private static final class PendingUse {
    HwConsumableKind kind;
    int slot;
    String itemId;
    int countBefore;
    double x, y, z;
    long sentAtMs;
  }

  public static final class HwTimerTracker {
    private static final long PENDING_CONFIRM_WINDOW_MS = 550L;
    private static final long HOLO_STALE_MS = 1200L;

    private final Map<String, TimerEntry> entries = new LinkedHashMap<String, TimerEntry>();
    private PendingUse pending;
    private boolean hadFreezeCombo = false;
    private boolean hadPearlChorusLock = false;

    public synchronized void clear() {
      entries.clear();
      pending = null;
      hadFreezeCombo = false;
      hadPearlChorusLock = false;
    }

    public synchronized void beginPendingUse(HwConsumableKind kind, int slot, String itemId,
        int countBefore, boolean alreadyOnCooldown, double x, double y, double z,
        Settings.HwTimers cfg, long nowMs) {
      if (kind == null || cfg == null || !cfg.enabled || !isKindEnabled(cfg, kind)) return;
      if (alreadyOnCooldown || countBefore <= 0) return;
      PendingUse p = new PendingUse();
      p.kind = kind;
      p.slot = slot;
      p.itemId = itemId == null ? "" : itemId;
      p.countBefore = countBefore;
      p.x = x;
      p.y = y;
      p.z = z;
      p.sentAtMs = nowMs;
      pending = p;
    }

    public synchronized boolean hasPendingUse() {
      return pending != null;
    }

    public synchronized int pendingSlot() {
      return pending != null ? pending.slot : -1;
    }

    public synchronized boolean pollPendingUse(String currentItemId, int currentCount,
        boolean onCooldownNow, Settings.HwTimers cfg, long nowMs) {
      PendingUse p = pending;
      if (p == null) return false;
      if (cfg == null || !cfg.enabled || !isKindEnabled(cfg, p.kind)) {
        pending = null;
        return false;
      }
      if (nowMs - p.sentAtMs > PENDING_CONFIRM_WINDOW_MS) {
        pending = null;
        return false;
      }
      String curId = currentItemId == null ? "" : currentItemId;
      boolean consumed = currentCount <= 0
          || !curId.equals(p.itemId)
          || currentCount < p.countBefore;
      if (consumed || onCooldownNow) {
        pending = null;
        if (!hasNearbyHologram(p.kind, p.x, p.y, p.z, 36.0, nowMs)) {
          onOwnConsumableUsed(p.kind, p.x, p.y, p.z, cfg, nowMs);
        }
        return true;
      }
      return false;
    }

    public synchronized void onOwnConsumableUsed(
        HwConsumableKind kind, double x, double y, double z, Settings.HwTimers cfg, long nowMs) {
      if (kind == null || cfg == null || !cfg.enabled || !isKindEnabled(cfg, kind)) return;
      if (hasNearbyHologram(kind, x, y, z, 36.0, nowMs)) return;
      float dur = defaultDuration(cfg, kind);
      String key = "own:" + kind.name();
      putOrUpdate(key, kind, x, y + 1.1, z, false, false, dur, dur, nowMs);
    }

    public synchronized void onHologramText(
        String rawText, double x, double y, double z, Settings.HwTimers cfg, long nowMs) {
      if (rawText == null || rawText.isEmpty() || cfg == null || !cfg.enabled) return;
      String clean = SoundControlLogic.stripFormatting(rawText).trim();
      if (clean.length() < 3 || clean.length() > 96) return;
      String lower = clean.toLowerCase(Locale.ROOT);
      if (lower.contains("ивент") || lower.contains("посылк") || lower.contains("корабл")
          || lower.contains("спавнер") || lower.contains("аукцион") || lower.contains("кейс")
          || lower.contains("восстанов") || lower.contains("осталось до")
          || lower.contains("до рестарт") || lower.contains("до вайп")) {
        return;
      }

      HwConsumableKind kind = null;
      if (lower.contains("взрывн") && lower.contains("трапк")) {
        kind = HwConsumableKind.EXPLOSIVE_TRAP;
      } else if (lower.contains("трапк") || lower.contains("клетк")) {
        kind = HwConsumableKind.TRAP;
      } else if (STUN_WORD.matcher(lower).find()) {
        kind = HwConsumableKind.STUN;
      } else if (lower.contains("замороз") || lower.contains("ком снега")) {
        kind = HwConsumableKind.SNOWBALL;
      } else if (lower.contains("прощальн")) {
        kind = HwConsumableKind.FAREWELL;
      }
      if (kind == null || !isKindEnabled(cfg, kind)) return;

      float secs = parseSeconds(lower);
      if (secs <= 0.1f || secs > 60.0f) return;

      String ownKey = "own:" + kind.name();
      TimerEntry own = entries.get(ownKey);
      if (own != null && distSq(own.x, own.y, own.z, x, y, z) <= 49.0) {
        entries.remove(ownKey);
      }
      if (pending != null && pending.kind == kind
          && distSq(pending.x, pending.y, pending.z, x, y, z) <= 49.0) {
        pending = null;
      }

      int gx = (int) Math.round(x * 0.5);
      int gy = (int) Math.round(y * 0.5);
      int gz = (int) Math.round(z * 0.5);
      String key = "holo:" + kind.name() + ":" + gx + ":" + gy + ":" + gz;
      float baseTotal = Math.max(secs, defaultDuration(cfg, kind));
      putOrUpdate(key, kind, x, y, z, false, true, secs, baseTotal, nowMs);
    }

    public synchronized void pruneMissingHolograms(long nowMs) {
      Iterator<Map.Entry<String, TimerEntry>> it = entries.entrySet().iterator();
      while (it.hasNext()) {
        TimerEntry e = it.next().getValue();
        if (e.fromHologram && nowMs - e.lastSeenAtMs > HOLO_STALE_MS) {
          it.remove();
        }
      }
    }

    public synchronized void syncPersonalEffects(List<PotionView> potions,
        List<Cooldown> cooldowns, double px, double py, double pz, Settings.HwTimers cfg,
        long nowMs) {
      if (cfg == null || !cfg.enabled) {
        hadFreezeCombo = false;
        hadPearlChorusLock = false;
        return;
      }

      float blindRem = 0f;
      float slowRem = 0f;
      if (potions != null) {
        for (int i = 0; i < potions.size(); i++) {
          PotionView p = potions.get(i);
          if (p == null || p.id == null || p.durationSeconds <= 0f) continue;
          String id = EffectAlertLogic.normalizeEffectId(p.id);
          if ("blindness".equals(id)) blindRem = Math.max(blindRem, p.durationSeconds);
          if (("slowness".equals(id) || "movement_slowdown".equals(id)) && p.amplifier >= 3) {
            slowRem = Math.max(slowRem, p.durationSeconds);
          }
        }
      }
      boolean freezeNow = blindRem > 0.2f && slowRem > 0.2f;
      if (freezeNow && cfg.snowball) {
        float rem = Math.min(blindRem, slowRem);
        float tot = Math.max(rem, cfg.snowballSeconds);
        putOrUpdate("self:SNOWBALL", HwConsumableKind.SNOWBALL, px, py + 1.2, pz, true, false, rem,
            tot, nowMs);
      } else if (!freezeNow && hadFreezeCombo) {
        entries.remove("self:SNOWBALL");
      }
      hadFreezeCombo = freezeNow;

      float pearlRem = 0f;
      float chorusRem = 0f;
      if (cooldowns != null) {
        for (int i = 0; i < cooldowns.size(); i++) {
          Cooldown c = cooldowns.get(i);
          if (c == null || c.itemId == null) continue;
          if (c.itemId.endsWith("ender_pearl")) pearlRem = Math.max(pearlRem, c.remainingSeconds);
          else if (c.itemId.endsWith("chorus_fruit")) chorusRem = Math.max(chorusRem, c.remainingSeconds);
        }
      }
      boolean stunLockNow =
          pearlRem >= 2.0f && chorusRem >= 2.0f && Math.abs(pearlRem - chorusRem) <= 0.35f;
      if (stunLockNow && cfg.stuns) {
        if (!hadPearlChorusLock && pearlRem >= 9.0f) {
          putOrUpdate("self:STUN", HwConsumableKind.STUN, px, py + 1.2, pz, true, false, pearlRem,
              Math.max(pearlRem, cfg.stunSeconds), nowMs);
        } else if (entries.containsKey("self:STUN")) {
          TimerEntry ex = entries.get("self:STUN");
          ex.expiresAtMs = nowMs + Math.round(pearlRem * 1000f);
          ex.x = px;
          ex.y = py + 1.2;
          ex.z = pz;
        }
      } else if (!stunLockNow && hadPearlChorusLock) {
        entries.remove("self:STUN");
      }
      hadPearlChorusLock = stunLockNow;
    }

    public synchronized List<HwActiveTimer> snapshot(Settings.HwTimers cfg, long nowMs) {
      if (cfg == null || !cfg.enabled || entries.isEmpty()) return Collections.emptyList();
      List<HwActiveTimer> out = new ArrayList<HwActiveTimer>(entries.size());
      Iterator<Map.Entry<String, TimerEntry>> it = entries.entrySet().iterator();
      while (it.hasNext()) {
        TimerEntry e = it.next().getValue();
        float rem = (e.expiresAtMs - nowMs) / 1000.0f;
        if (rem <= 0.04f || !isKindEnabled(cfg, e.kind)) {
          it.remove();
          continue;
        }
        out.add(new HwActiveTimer(
            e.kind, e.x, e.y, e.z, e.personal, rem, Math.max(rem, e.totalSeconds)));
      }
      return out;
    }

    private boolean hasNearbyHologram(
        HwConsumableKind kind, double x, double y, double z, double maxDistSq, long nowMs) {
      for (TimerEntry e : entries.values()) {
        if (e.fromHologram && e.kind == kind && e.expiresAtMs > nowMs
            && distSq(e.x, e.y, e.z, x, y, z) <= maxDistSq) {
          return true;
        }
      }
      return false;
    }

    private static double distSq(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx, dy = ay - by, dz = az - bz;
      return dx * dx + dy * dy + dz * dz;
    }

    private void putOrUpdate(String key, HwConsumableKind kind, double x, double y, double z,
        boolean personal, boolean fromHologram, float remSec, float totalSec, long nowMs) {
      TimerEntry e = entries.get(key);
      if (e == null) {
        if (entries.size() >= 16) {
          Iterator<String> it = entries.keySet().iterator();
          if (it.hasNext()) {
            it.next();
            it.remove();
          }
        }
        e = new TimerEntry();
        e.key = key;
        e.totalSeconds = totalSec;
        entries.put(key, e);
      }
      e.kind = kind;
      e.x = x;
      e.y = y;
      e.z = z;
      e.personal = personal;
      e.fromHologram = fromHologram;
      e.lastSeenAtMs = nowMs;
      e.expiresAtMs = nowMs + Math.round(remSec * 1000f);
      if (remSec > e.totalSeconds) e.totalSeconds = remSec;
    }
  }

  public static boolean isKindEnabled(Settings.HwTimers cfg, HwConsumableKind kind) {
    if (cfg == null || kind == null) return false;
    switch (kind) {
      case TRAP:
        return cfg.traps;
      case EXPLOSIVE_TRAP:
        return cfg.explosiveTraps;
      case STUN:
        return cfg.stuns;
      case SNOWBALL:
        return cfg.snowball;
      case FAREWELL:
        return cfg.farewell;
      default:
        return false;
    }
  }

  private static float defaultDuration(Settings.HwTimers cfg, HwConsumableKind kind) {
    switch (kind) {
      case EXPLOSIVE_TRAP:
      case TRAP:
        return cfg.trapSeconds;
      case STUN:
        return cfg.stunSeconds;
      case SNOWBALL:
        return cfg.snowballSeconds;
      case FAREWELL:
        return 8.0f;
      default:
        return 15.0f;
    }
  }

  public static float parseSeconds(String lowerText) {
    Matcher mmss = HOLO_MMSS.matcher(lowerText);
    if (mmss.find()) {
      try {
        int m = Integer.parseInt(mmss.group(1));
        int s = Integer.parseInt(mmss.group(2));
        return m * 60f + s;
      } catch (NumberFormatException ignored) {
      }
    }
    Matcher sec = HOLO_SEC.matcher(lowerText);
    if (sec.find()) {
      try {
        return Float.parseFloat(sec.group(1).replace(',', '.'));
      } catch (NumberFormatException ignored) {
      }
    }
    return -1f;
  }

  public static HwConsumableKind classifyHwConsumable(String itemId, String displayName, String lore) {
    String cleanName = SoundControlLogic.stripFormatting(displayName == null ? "" : displayName)
        .toLowerCase(Locale.ROOT);
    String cleanLore = SoundControlLogic.stripFormatting(lore == null ? "" : lore)
        .toLowerCase(Locale.ROOT);
    String combined = cleanName + " " + cleanLore;

    if (cleanName.contains("взрывная трапка")
        || (cleanName.contains("трапк") && combined.contains("взрыв"))) {
      return HwConsumableKind.EXPLOSIVE_TRAP;
    }
    if (cleanName.contains("трапка") || cleanName.contains("обсидиановая трапка")) {
      return HwConsumableKind.TRAP;
    }
    if (STUN_WORD.matcher(cleanName).find()
        || (itemId != null && itemId.contains("nether_star") && combined.contains("оглуш"))) {
      return HwConsumableKind.STUN;
    }
    if (cleanName.contains("ком снега") || cleanName.contains("снежок заморозки")
        || (itemId != null && itemId.contains("snowball") && combined.contains("замороз"))) {
      return HwConsumableKind.SNOWBALL;
    }
    if (cleanName.contains("прощальный гул")) {
      return HwConsumableKind.FAREWELL;
    }
    return null;
  }

  private static final Pattern TREASURE_COORDS_3 = Pattern.compile(
      "по\\s+координатам\\s*:?\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern TREASURE_COORDS_2 = Pattern.compile(
      "по\\s+координатам\\s*:?\\s*(-?\\d+)\\s*,\\s*(-?\\d+)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern DRILL_NBT_PATTERN = Pattern.compile(
      "drill[^0-9]*([12])", Pattern.CASE_INSENSITIVE);
  private static final Pattern DRILL_LORE_II = Pattern.compile(
      "(?:^|\\s)(?:бур|drill)\\s*(?:ii|2)(?:$|\\s)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern DRILL_LORE_I = Pattern.compile(
      "(?:^|\\s)(?:бур|drill)\\s*(?:i|1)(?:$|\\s)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public static int[] parseTreasureCompassCoords(String rawLine) {
    if (rawLine == null || rawLine.isEmpty()) return null;
    String clean = SoundControlLogic.stripFormatting(rawLine).trim();
    if (clean.isEmpty()) return null;
    String lower = clean.toLowerCase(Locale.ROOT);
    if (!lower.contains("сокровище по координатам") && !lower.contains("стрелка компаса")) {
      return null;
    }
    Matcher m3 = TREASURE_COORDS_3.matcher(clean);
    if (m3.find()) {
      try {
        return new int[] {
            Integer.parseInt(m3.group(1)),
            Integer.parseInt(m3.group(2)),
            Integer.parseInt(m3.group(3))
        };
      } catch (NumberFormatException ignored) {
      }
    }
    Matcher m2 = TREASURE_COORDS_2.matcher(clean);
    if (m2.find()) {
      try {
        return new int[] {
            Integer.parseInt(m2.group(1)),
            Integer.MIN_VALUE,
            Integer.parseInt(m2.group(2))
        };
      } catch (NumberFormatException ignored) {
      }
    }
    return null;
  }

  public static int detectDrillLevel(
      String itemId, String customEnchantsRaw, String drillKeyRaw, String loreRaw) {
    if (itemId == null || !itemId.toLowerCase(Locale.ROOT).endsWith("_pickaxe")) {
      return 0;
    }
    if (drillKeyRaw != null && !drillKeyRaw.trim().isEmpty()) {
      String d = drillKeyRaw.trim();
      if (d.startsWith("2")) return 2;
      if (d.startsWith("1")) return 1;
    }
    if (customEnchantsRaw != null && !customEnchantsRaw.isEmpty()) {
      Matcher m = DRILL_NBT_PATTERN.matcher(customEnchantsRaw);
      if (m.find()) {
        return "2".equals(m.group(1)) ? 2 : 1;
      }
      if (customEnchantsRaw.toLowerCase(Locale.ROOT).contains("drill")) {
        return 1;
      }
    }
    if (loreRaw != null && !loreRaw.isEmpty()) {
      String cleanLore = SoundControlLogic.stripFormatting(loreRaw);
      if (DRILL_LORE_II.matcher(cleanLore).find()) return 2;
      if (DRILL_LORE_I.matcher(cleanLore).find()) return 1;
    }
    return 0;
  }

  public static double[] computeDrillBox(
      int bx, int by, int bz, int faceNormalX, int faceNormalY, int faceNormalZ, int depth) {
    int d = Math.max(1, Math.min(2, depth));
    int minX = bx;
    int maxX = bx + 1;
    int minY = by;
    int maxY = by + 1;
    int minZ = bz;
    int maxZ = bz + 1;

    if (faceNormalX != 0) {
      int inward = -faceNormalX;
      if (inward > 0) maxX += (d - 1);
      else minX -= (d - 1);
      minY -= 1;
      maxY += 1;
      minZ -= 1;
      maxZ += 1;
    } else if (faceNormalY != 0) {
      int inward = -faceNormalY;
      if (inward > 0) maxY += (d - 1);
      else minY -= (d - 1);
      minX -= 1;
      maxX += 1;
      minZ -= 1;
      maxZ += 1;
    } else {
      int inward = -faceNormalZ;
      if (inward > 0) maxZ += (d - 1);
      else minZ -= (d - 1);
      minX -= 1;
      maxX += 1;
      minY -= 1;
      maxY += 1;
    }
    return new double[] {minX, minY, minZ, maxX, maxY, maxZ};
  }

  public static boolean isJakeLuminaire(String itemId, String kringeType, String displayName) {
    if (itemId == null || !itemId.toLowerCase(Locale.ROOT).endsWith("jack_o_lantern")) {
      return false;
    }
    if (kringeType != null && "JakeLuminaire".equalsIgnoreCase(kringeType.trim())) {
      return true;
    }
    String clean = SoundControlLogic.stripFormatting(displayName == null ? "" : displayName)
        .toLowerCase(Locale.ROOT);
    return clean.contains("светильник джейка") || clean.contains("jake");
  }

  public static final class TrapActionTimer {
    public static final long DURATION_MS = 11_000L;

    private boolean prevExpCd;
    private int prevExpCount = -1;
    private boolean prevTrapCd;
    private int prevTrapCount = -1;

    private HwConsumableKind activeKind;
    private long expiresAtMs;
    private boolean wasShowing;

    public void reset() {
      prevExpCd = false;
      prevExpCount = -1;
      prevTrapCd = false;
      prevTrapCount = -1;
      activeKind = null;
      expiresAtMs = 0L;
      wasShowing = false;
    }

    public HwConsumableKind pollInventory(boolean expOnCooldown, int expCount,
        boolean trapOnCooldown, int trapCount, long nowMs) {
      HwConsumableKind triggered = null;
      if (prevExpCount >= 0 && !prevExpCd && expOnCooldown && expCount < prevExpCount) {
        activeKind = HwConsumableKind.EXPLOSIVE_TRAP;
        expiresAtMs = nowMs + DURATION_MS;
        triggered = HwConsumableKind.EXPLOSIVE_TRAP;
      }
      if (prevTrapCount >= 0 && !prevTrapCd && trapOnCooldown && trapCount < prevTrapCount) {
        activeKind = HwConsumableKind.TRAP;
        expiresAtMs = nowMs + DURATION_MS;
        triggered = HwConsumableKind.TRAP;
      }
      prevExpCd = expOnCooldown;
      prevExpCount = expCount;
      prevTrapCd = trapOnCooldown;
      prevTrapCount = trapCount;
      return triggered;
    }

    public void trigger(HwConsumableKind kind, long nowMs) {
      if (kind == HwConsumableKind.TRAP || kind == HwConsumableKind.EXPLOSIVE_TRAP) {
        activeKind = kind;
        expiresAtMs = nowMs + DURATION_MS;
      }
    }

    public String nextActionbarText(long nowMs, String language) {
      if (activeKind != null) {
        long remMs = expiresAtMs - nowMs;
        if (remMs > 0L) {
          wasShowing = true;
          String lang = I18n.language(language);
          String secStr = String.format(Locale.ROOT, "%.1f", remMs / 1000.0);
          String key = activeKind == HwConsumableKind.EXPLOSIVE_TRAP
              ? "hwhelper.actionbar.explosive_trap"
              : "hwhelper.actionbar.trap";
          return I18n.format(lang, key, Collections.<String, Object>singletonMap("sec", secStr));
        }
        activeKind = null;
        expiresAtMs = 0L;
      }
      if (wasShowing) {
        wasShowing = false;
        return "";
      }
      return null;
    }
  }

  public static final double ESP_WALL_INSET = 0.008;
  public static final double ESP_FLOOR_LIFT = 0.02;

  public static double[] computeExplosiveTrapCheckBox(int bx, int by, int bz) {
    return new double[] {bx - 3.0, by - 3.0, bz - 3.0, bx + 4.0, by + 4.0, bz + 4.0};
  }

  public static double[] computeExplosiveTrapMainBox(int bx, int by, int bz) {
    return new double[] {
        bx - 3.0 + ESP_WALL_INSET, by + ESP_FLOOR_LIFT, bz - 3.0 + ESP_WALL_INSET,
        bx + 4.0 - ESP_WALL_INSET, by + 4.0 - ESP_WALL_INSET, bz + 4.0 - ESP_WALL_INSET
    };
  }

  public static double[] computeExplosiveTrapLowerBox(int bx, int by, int bz) {
    return new double[] {
        bx - 3.0 + ESP_WALL_INSET, by - 3.0 + ESP_WALL_INSET, bz - 3.0 + ESP_WALL_INSET,
        bx + 4.0 - ESP_WALL_INSET, by + ESP_FLOOR_LIFT, bz + 4.0 - ESP_WALL_INSET
    };
  }

  public static double[] computeNormalTrapBox(int bx, int by, int bz) {
    return new double[] {
        bx - 2.0 + ESP_WALL_INSET, by + ESP_FLOOR_LIFT, bz - 2.0 + ESP_WALL_INSET,
        bx + 3.0 - ESP_WALL_INSET, by + 4.0 - ESP_WALL_INSET, bz + 3.0 - ESP_WALL_INSET
    };
  }

  private static final Pattern ITEM_COOLDOWN_LORE_PATTERN = Pattern.compile(
      "(?:перезарядк[а-яё]*|задержк[а-яё]*|кулдаун|кд|cooldown)\\s*[:\\-–]?\\s*(\\d{1,2}:\\d{2}|\\d{1,3}(?:[.,]\\d)?)\\s*(?:сек[а-яё]*|с\\.?|sec|s)?",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern CHAT_COOLDOWN_PREFIX = Pattern.compile(
      "(?:подожд[а-яё]*|перезаря[дж][а-яё]*|задержк[а-яё]*|кулдаун|кд|cooldown|wait|повторн[а-яё]*\\s+использ[а-яё]*|сможете\\s+использ[а-яё]*|нельзя\\s+использ[а-яё]*|использовать\\s+через|осталось\\s+\\d)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile(
      "(?i)(?:&x(?:&[0-9a-f]){6}|§x(?:§[0-9a-f]){6}|&#[0-9a-f]{6}|&[0-9a-fk-or])");

  public static String normalizeHwItemText(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    String stripped = BUKKIT_HEX_PATTERN.matcher(SoundControlLogic.stripFormatting(raw)).replaceAll("");
    StringBuilder out = new StringBuilder(stripped.length());
    for (int i = 0; i < stripped.length(); i++) {
      char c = stripped.charAt(i);
      switch (c) {
        case 'ᴀ': out.append('а'); break;
        case 'ʙ': out.append('в'); break;
        case 'ᴄ': out.append('с'); break;
        case 'ᴅ': out.append('д'); break;
        case 'ᴇ': out.append('е'); break;
        case 'ғ': out.append('ф'); break;
        case 'ɢ': out.append('г'); break;
        case 'ʜ': out.append('н'); break;
        case 'ɪ': out.append('и'); break;
        case 'ᴊ': out.append('й'); break;
        case 'ᴋ': out.append('к'); break;
        case 'ʟ': out.append('л'); break;
        case 'ᴍ': out.append('м'); break;
        case 'ɴ': out.append('н'); break;
        case 'ᴏ': out.append('о'); break;
        case 'ᴘ': out.append('р'); break;
        case 'ǫ': out.append('q'); break;
        case 'ʀ': out.append('я'); break;
        case 'ѕ': case 'ꜱ': out.append('с'); break;
        case 'ᴛ': out.append('т'); break;
        case 'ᴜ': out.append('у'); break;
        case 'ᴠ': out.append('в'); break;
        case 'ᴡ': out.append('ш'); break;
        case 'ʏ': out.append('у'); break;
        case 'ᴢ': out.append('з'); break;
        case 'ᴤ': out.append('г'); break;
        case 'ᴨ': out.append('п'); break;
        case 'ᴫ': out.append('л'); break;
        case 'ё': case 'Ё': out.append('е'); break;
        default: out.append(Character.toLowerCase(c)); break;
      }
    }
    return out.toString();
  }

  public static boolean isExplosiveThing(String itemId, String displayName, String lore) {
    return isExplosiveThing(itemId, displayName, lore, "", false);
  }

  public static boolean isExplosiveThing(
      String itemId, String displayName, String lore, String nbtType, boolean onHolyWorld) {
    if (itemId == null) return false;
    String lowerId = itemId.toLowerCase(Locale.ROOT);
    boolean isFireCharge = lowerId.endsWith("fire_charge");
    boolean isExplosiveAlt = lowerId.endsWith("firework_star")
        || lowerId.endsWith(":tnt") || lowerId.equals("tnt")
        || lowerId.endsWith("gunpowder");
    if (!isFireCharge && !isExplosiveAlt) return false;

    String normName = normalizeHwItemText(displayName);
    String normLore = normalizeHwItemText(lore);
    String combined = normName + " " + normLore;
    if (combined.contains("смерч") || combined.contains("трапк")) {
      return false;
    }

    if ((combined.contains("взрывн") && (combined.contains("штуч") || combined.contains("штук")))
        || combined.contains("штучк")
        || combined.contains("explosive thing")
        || combined.contains("explosive_thing")) {
      return true;
    }

    if (nbtType != null && !nbtType.isEmpty()) {
      String lowerNbt = nbtType.toLowerCase(Locale.ROOT);
      if (!lowerNbt.contains("trap") && !lowerNbt.contains("tornado") && !lowerNbt.contains("smerch")
          && (lowerNbt.contains("explosive") || lowerNbt.contains("shtuch")
              || lowerNbt.contains("thing") || lowerNbt.contains("boom")
              || lowerNbt.contains("blast") || lowerNbt.contains("fire_charge")
              || lowerNbt.contains("firecharge") || lowerNbt.contains("bomb"))) {
        return true;
      }
    }

    if (!isFireCharge) return false;

    if (combined.contains("взрыв")) {
      return true;
    }
    return onHolyWorld;
  }

  public static float parseItemCooldownSeconds(String lore) {
    if (lore == null || lore.isEmpty()) return -1f;
    String clean = BUKKIT_HEX_PATTERN.matcher(SoundControlLogic.stripFormatting(lore)).replaceAll("");
    Matcher m = ITEM_COOLDOWN_LORE_PATTERN.matcher(clean);
    if (m.find()) {
      String rawVal = m.group(1);
      if (rawVal.indexOf(':') >= 0) {
        float secs = parseSeconds(rawVal);
        if (secs >= 0.5f && secs <= 300f) return secs;
      } else {
        try {
          float val = Float.parseFloat(rawVal.replace(',', '.'));
          if (val >= 0.5f && val <= 300f) return val;
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return -1f;
  }

  public static float parseServerCooldownNotice(String rawText) {
    if (rawText == null || rawText.isEmpty()) return -1f;
    String clean = BUKKIT_HEX_PATTERN.matcher(SoundControlLogic.stripFormatting(rawText))
        .replaceAll("").trim();
    if (clean.length() < 4 || clean.length() > 160) return -1f;
    if (!CHAT_COOLDOWN_PREFIX.matcher(clean).find()) return -1f;
    float secs = parseSeconds(clean.toLowerCase(Locale.ROOT));
    return (secs >= 0.2f && secs <= 300f) ? secs : -1f;
  }

  public static final class ExplosiveThingCooldownTracker {
    private static final long PENDING_CONFIRM_WINDOW_MS = 650L;
    private static final long VANILLA_GRACE_MS = 160L;
    private static final long CHAT_CORRELATE_WINDOW_MS = 2600L;
    private static final double EXPLOSION_CORRELATE_DIST_SQ = 36.0;
    private static final float DEFAULT_COOLDOWN_SECONDS = 15.0f;

    private boolean pendingActive;
    private int pendingSlot = -1;
    private int pendingCountBefore;
    private int pendingTotalCountBefore;
    private long pendingSentAtMs;
    private boolean confirmedConsumed;
    private long confirmedAtMs;
    private float pendingLoreCooldown = -1f;

    private long lastRightClickMs;
    private String lastItemId = "minecraft:fire_charge";
    private String lastDisplayName = "Взрывная штучка";
    private float learnedDurationSec = -1f;

    private long activeExpiresAtMs;
    private float activeDurationSec;

    public synchronized void clear() {
      pendingActive = false;
      pendingSlot = -1;
      pendingCountBefore = 0;
      pendingTotalCountBefore = 0;
      pendingSentAtMs = 0L;
      confirmedConsumed = false;
      confirmedAtMs = 0L;
      pendingLoreCooldown = -1f;
      lastRightClickMs = 0L;
      lastItemId = "minecraft:fire_charge";
      activeExpiresAtMs = 0L;
      activeDurationSec = 0f;
    }

    public synchronized void onRightClick(String itemId, String displayName, String lore,
        int slot, int countBefore, boolean alreadyOnVanillaCooldown, long nowMs) {
      onRightClick(itemId, displayName, lore, "", false, slot, countBefore, countBefore,
          alreadyOnVanillaCooldown, nowMs);
    }

    public synchronized void onRightClick(String itemId, String displayName, String lore,
        String nbtType, boolean onHolyWorld, int slot, int slotCountBefore, int totalCountBefore,
        boolean alreadyOnVanillaCooldown, long nowMs) {
      if (!isExplosiveThing(itemId, displayName, lore, nbtType, onHolyWorld)) return;
      lastRightClickMs = nowMs;
      if (itemId != null && !itemId.isEmpty()) {
        lastItemId = itemId;
      }
      String clean = BUKKIT_HEX_PATTERN.matcher(
          SoundControlLogic.stripFormatting(displayName == null ? "" : displayName)).replaceAll("").trim();
      if (!clean.isEmpty() && !clean.equalsIgnoreCase("Fire Charge") && !clean.equalsIgnoreCase("Огненный заряд")) {
        lastDisplayName = clean;
      } else if (lastDisplayName == null || lastDisplayName.isEmpty()) {
        lastDisplayName = "Взрывная штучка";
      }
      float loreSec = parseItemCooldownSeconds(lore);
      if (loreSec > 0f) {
        pendingLoreCooldown = loreSec;
        learnedDurationSec = loreSec;
      }
      if (alreadyOnVanillaCooldown || (slotCountBefore <= 0 && totalCountBefore <= 0)) return;
      if (pendingActive && (nowMs - pendingSentAtMs) <= PENDING_CONFIRM_WINDOW_MS) {
        if (confirmedConsumed) return;
        pendingCountBefore = Math.max(pendingCountBefore, slotCountBefore);
        pendingTotalCountBefore = Math.max(pendingTotalCountBefore, Math.max(slotCountBefore, totalCountBefore));
        return;
      }
      pendingActive = true;
      pendingSlot = slot;
      pendingCountBefore = slotCountBefore;
      pendingTotalCountBefore = Math.max(slotCountBefore, totalCountBefore);
      pendingSentAtMs = nowMs;
      confirmedConsumed = false;
      confirmedAtMs = 0L;
    }

    public synchronized int pendingSlot() {
      return pendingActive ? pendingSlot : -1;
    }

    public synchronized boolean hasPending() {
      return pendingActive;
    }

    public synchronized void onNearbyExplosion(
        double ex, double ey, double ez, double px, double py, double pz, long nowMs) {
      if (!pendingActive || confirmedConsumed) return;
      if (nowMs - pendingSentAtMs > PENDING_CONFIRM_WINDOW_MS) return;
      double dx = ex - px, dy = ey - py, dz = ez - pz;
      if (dx * dx + dy * dy + dz * dz <= EXPLOSION_CORRELATE_DIST_SQ) {
        confirmedConsumed = true;
        confirmedAtMs = nowMs;
      }
    }

    public synchronized void pollInventory(String currentItemId, int currentCount,
        boolean vanillaOnCooldown, long nowMs) {
      pollInventory(currentItemId, currentCount, currentCount, vanillaOnCooldown, nowMs);
    }

    public synchronized void pollInventory(String currentItemId, int currentSlotCount,
        int currentTotalCount, boolean vanillaOnCooldown, long nowMs) {
      if (vanillaOnCooldown) {
        pendingActive = false;
        confirmedConsumed = false;
        activeExpiresAtMs = 0L;
        return;
      }
      if (!pendingActive) return;
      if (!confirmedConsumed) {
        if (nowMs - pendingSentAtMs > PENDING_CONFIRM_WINDOW_MS) {
          pendingActive = false;
          return;
        }
        String trackedTail = lastItemId != null && lastItemId.indexOf(':') >= 0
            ? lastItemId.substring(lastItemId.indexOf(':') + 1)
            : (lastItemId == null ? "fire_charge" : lastItemId);
        boolean slotConsumed = currentSlotCount < pendingCountBefore
            || currentSlotCount <= 0
            || currentItemId == null
            || !currentItemId.toLowerCase(Locale.ROOT).endsWith(trackedTail.toLowerCase(Locale.ROOT));
        boolean totalConsumed = pendingTotalCountBefore > 0 && currentTotalCount < pendingTotalCountBefore;
        if (totalConsumed || (pendingTotalCountBefore <= pendingCountBefore && slotConsumed)) {
          confirmedConsumed = true;
          confirmedAtMs = nowMs;
        }
      }
      if (confirmedConsumed && nowMs - confirmedAtMs >= VANILLA_GRACE_MS) {
        float dur = pendingLoreCooldown > 0f
            ? pendingLoreCooldown
            : (learnedDurationSec > 0f ? learnedDurationSec : DEFAULT_COOLDOWN_SECONDS);
        startSynthetic(dur, dur, nowMs);
        pendingActive = false;
        confirmedConsumed = false;
      }
    }

    public synchronized void onServerCooldownMessage(String rawText, long nowMs) {
      if (nowMs - lastRightClickMs > CHAT_CORRELATE_WINDOW_MS && activeExpiresAtMs <= nowMs) {
        return;
      }
      float secs = parseServerCooldownNotice(rawText);
      if (secs <= 0f) return;
      if (secs > learnedDurationSec) learnedDurationSec = secs;
      float total = Math.max(secs, learnedDurationSec > 0f ? learnedDurationSec : secs);
      startSynthetic(secs, total, nowMs);
      pendingActive = false;
      confirmedConsumed = false;
    }

    private void startSynthetic(float remainingSec, float totalSec, long nowMs) {
      activeDurationSec = Math.max(remainingSec, totalSec);
      activeExpiresAtMs = nowMs + Math.round(remainingSec * 1000f);
    }

    public synchronized boolean hasActive(long nowMs) {
      return activeExpiresAtMs > nowMs + 40L;
    }

    public synchronized List<Cooldown> mergeInto(List<Cooldown> base, long nowMs) {
      String trackedTail = lastItemId != null && lastItemId.indexOf(':') >= 0
          ? lastItemId.substring(lastItemId.indexOf(':') + 1).toLowerCase(Locale.ROOT)
          : "fire_charge";
      if (base != null) {
        for (int i = 0; i < base.size(); i++) {
          Cooldown c = base.get(i);
          if (c == null) continue;
          String itemLow = c.itemId == null ? "" : c.itemId.toLowerCase(Locale.ROOT);
          String keyLow = c.key == null ? "" : c.key.toLowerCase(Locale.ROOT);
          if (itemLow.endsWith("fire_charge") || keyLow.endsWith("fire_charge")
              || itemLow.endsWith(trackedTail) || keyLow.endsWith(trackedTail)) {
            activeExpiresAtMs = 0L;
            pendingActive = false;
            confirmedConsumed = false;
            return base;
          }
        }
      }
      if (activeExpiresAtMs <= nowMs + 40L) {
        activeExpiresAtMs = 0L;
        return base == null ? Collections.<Cooldown>emptyList() : base;
      }
      float remSec = (activeExpiresAtMs - nowMs) / 1000f;
      float durSec = Math.max(remSec, activeDurationSec > 0f ? activeDurationSec : remSec);
      List<Cooldown> merged = new ArrayList<Cooldown>((base == null ? 0 : base.size()) + 1);
      if (base != null) merged.addAll(base);
      merged.add(new Cooldown(
          "hw:explosive_thing", lastDisplayName,
          lastItemId == null || lastItemId.isEmpty() ? "minecraft:fire_charge" : lastItemId,
          remSec, durSec));
      return merged;
    }
  }
}
