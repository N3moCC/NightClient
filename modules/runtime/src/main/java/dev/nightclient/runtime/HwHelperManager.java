package dev.nightclient.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HwHelperManager {
  private static final String ENDPOINT = "https://api.holyworld.me/v1/coins-trades?limit=30";
  private static final String SERVERS_ENDPOINT = "https://api.holyworld.me/v1/servers";
  private static final String EVENTS_ENDPOINT = "https://api.holyworld.me/v1/events";
  private static final Pattern PRICE_PATTERN = Pattern.compile("([\\d][\\d.,\\s]*)\\D*¤");
  private static final String[] BLOCKED = {"Покупка", "Биржа"};

  private static final Pattern AN_PATTERN =
      Pattern.compile("^/(?:an|lt)(?:archy)?(?:\\s*(\\d+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern AH_SELL_PATTERN =
      Pattern.compile("^/(?:ah|ahs)\\s+(sell|sells)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PRICE_TOKEN_PATTERN =
      Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)(k|kk|kkk|m|b)?$", Pattern.CASE_INSENSITIVE);

  private final PlatformServices platform;
  private final Settings settings;
  private final Runnable onSettingsChanged;
  private final ScheduledExecutorService scheduler;

  private volatile long rate = 100_000L;
  private final HwPvpAndEventsLogic.HwTimerTracker timerTracker =
      new HwPvpAndEventsLogic.HwTimerTracker();
  private final HwPvpAndEventsLogic.TrapActionTimer trapActionTimer =
      new HwPvpAndEventsLogic.TrapActionTimer();
  private final HwPvpAndEventsLogic.ExplosiveThingCooldownTracker explosiveThingCooldown =
      new HwPvpAndEventsLogic.ExplosiveThingCooldownTracker();
  private volatile Map<String, HwPvpAndEventsLogic.ServerInfo> cachedServers =
      Collections.emptyMap();
  private volatile List<HwPvpAndEventsLogic.EventItem> cachedEvents = Collections.emptyList();
  private final Map<String, Long> eventFirstSeenMs = new java.util.concurrent.ConcurrentHashMap<String, Long>();
  private final Map<String, String> chatCoordsByType = new java.util.concurrent.ConcurrentHashMap<String, String>();
  private volatile long lastServersFetchMs = 0L;
  private volatile long lastEventsFetchMs = 0L;
  private volatile boolean eventsFetchInFlight = false;
  private volatile boolean initialEventsBatchSeen = false;
  private volatile String pendingEventAlertSound = null;

  private enum SwapState { IDLE, WAITING_HUB_WORLD, OPENING_MENU, WAITING_MENU_STEP }

  private SwapState swapState = SwapState.IDLE;
  private int targetAnka;
  private String targetCat;
  private String targetKey;
  private String activeCategory = "";
  private String foundCategory = "";
  private boolean fullScanAnnounced;
  private boolean scanCompletedNow;
  private final List<String> detectedCategories = new ArrayList<String>();
  private final Set<String> visitedCategories = new LinkedHashSet<String>();
  private Object prevWorldToken;
  private int swapTicks;
  private int stateTicks;
  private int delayTicks;
  private int openStage;
  private int menuSteps;
  private int reopenAttempts;
  private String lastMenuFingerprint = "";
  private String clickedFingerprint = "";
  private String reopenFromFingerprint = "";
  private String chatBeforeSwap = "";
  private Object chatTokenBeforeSwap;

  public HwHelperManager(PlatformServices platform, Settings settings) {
    this(platform, settings, null);
  }

  public HwHelperManager(
      PlatformServices platform, Settings settings, Runnable onSettingsChanged) {
    this.platform = platform;
    this.settings = settings;
    this.onSettingsChanged = onSettingsChanged;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "nightclient-hwhelper-aucprice");
      t.setDaemon(true);
      return t;
    });
    startRatePolling();
  }

  public void shutdown() {
    scheduler.shutdownNow();
  }

  private void startRatePolling() {
    scheduler.scheduleWithFixedDelay(this::fetchRate, 0, 60, TimeUnit.SECONDS);
    scheduler.scheduleWithFixedDelay(this::pollEventsPeriodic, 1, 3, TimeUnit.SECONDS);
  }

  public HwPvpAndEventsLogic.HwTimerTracker timerTracker() {
    return timerTracker;
  }

  public HwPvpAndEventsLogic.TrapActionTimer trapActionTimer() {
    return trapActionTimer;
  }

  public HwPvpAndEventsLogic.ExplosiveThingCooldownTracker explosiveThingCooldown() {
    return explosiveThingCooldown;
  }

  public List<HwPvpAndEventsLogic.EventItem> allEvents() {
    return cachedEvents;
  }

  public List<HwPvpAndEventsLogic.EventItem> filteredEvents() {
    if (settings.hwhelper == null || settings.hwhelper.events == null) return cachedEvents;
    return HwPvpAndEventsLogic.filterEvents(
        cachedEvents, settings.hwhelper.events.modeFilter, settings.hwhelper.events.rarityFilter);
  }

  public long lastEventsFetchMs() {
    return lastEventsFetchMs;
  }

  public boolean isEventsLoading() {
    return eventsFetchInFlight;
  }

  public void requestEventsRefresh() {
    if (eventsFetchInFlight) return;
    lastEventsFetchMs = 0L;
    scheduler.execute(this::fetchEventsNow);
  }

  public void observeChatLine(String line) {
    if (line == null || line.isEmpty() || FeaturePolicy.isBlocked("hwhelper")
        || settings.hwhelper == null || !settings.hwhelper.enabled)
      return;
    HwPvpAndEventsLogic.observeEventChatLine(line, chatCoordsByType);
  }

  public boolean joinEventByInstanceOrServer(String token) {
    if (token == null || token.isEmpty() || FeaturePolicy.isBlocked("hwhelper.events")
        || FeaturePolicy.isBlocked("hwhelper.answap")) return false;
    List<HwPvpAndEventsLogic.EventItem> snapshot = cachedEvents;
    HwPvpAndEventsLogic.EventItem match = null;
    for (int i = 0; i < snapshot.size(); i++) {
      HwPvpAndEventsLogic.EventItem ev = snapshot.get(i);
      if (token.equals(ev.instanceId) || token.equals(ev.serverKey)) {
        match = ev;
        break;
      }
    }
    if (match != null) {
      if (!match.canAutoJoin()) {
        String lang = lang();
        platform.sendFeedback(I18n.format(lang, "hwhelper.msg.lite121_manual",
            Collections.<String, Object>singletonMap("server", match.localizedServerName(lang))));
        return false;
      }
      initiateAnSwap(match.anarchyNumber);
      return true;
    }
    HwPvpAndEventsLogic.ServerInfo info = HwPvpAndEventsLogic.classifyServer(token, "");
    if (!info.lite121 && info.anarchyNumber >= 1) {
      initiateAnSwap(info.anarchyNumber);
      return true;
    }
    return false;
  }

  private void pollEventsPeriodic() {
    if (FeaturePolicy.isBlocked("hwhelper.events")
        || settings.hwhelper == null || !settings.hwhelper.enabled || settings.hwhelper.events == null
        || !settings.hwhelper.events.enabled) {
      return;
    }
    long now = System.currentTimeMillis();
    long intervalMs = Math.max(10, settings.hwhelper.events.pollIntervalSeconds) * 1000L;
    if (now - lastEventsFetchMs < intervalMs) return;
    fetchEventsNow();
  }

  private void fetchEventsNow() {
    if (eventsFetchInFlight) return;
    eventsFetchInFlight = true;
    try {
      long now = System.currentTimeMillis();
      if (cachedServers.isEmpty() || now - lastServersFetchMs > 300_000L) {
        String sJson = httpGetString(SERVERS_ENDPOINT);
        if (sJson != null && !sJson.isEmpty()) {
          Map<String, HwPvpAndEventsLogic.ServerInfo> parsed =
              HwPvpAndEventsLogic.parseServersJson(sJson);
          if (!parsed.isEmpty()) {
            cachedServers = parsed;
            lastServersFetchMs = now;
          }
        }
      }
      String eJson = httpGetString(EVENTS_ENDPOINT);
      if (eJson != null && !eJson.isEmpty()) {
        Set<String> prevIds = new LinkedHashSet<String>(eventFirstSeenMs.keySet());
        List<HwPvpAndEventsLogic.EventItem> next = HwPvpAndEventsLogic.parseEventsJson(
            eJson, cachedServers, eventFirstSeenMs, chatCoordsByType, now);
        Set<String> activeIds = new LinkedHashSet<String>();
        boolean newHighTier = false;
        for (int i = 0; i < next.size(); i++) {
          HwPvpAndEventsLogic.EventItem item = next.get(i);
          activeIds.add(item.instanceId);
          if (initialEventsBatchSeen && !prevIds.contains(item.instanceId)
              && ("legendary".equals(item.rarityTier) || "mythic".equals(item.rarityTier))) {
            newHighTier = true;
          }
        }
        eventFirstSeenMs.keySet().retainAll(activeIds);
        cachedEvents = Collections.unmodifiableList(next);
        lastEventsFetchMs = now;
        if (newHighTier && settings.hwhelper != null && settings.hwhelper.events != null
            && settings.hwhelper.events.soundAlert) {
          pendingEventAlertSound = "minecraft:block.amethyst_block.chime";
        }
        initialEventsBatchSeen = true;
      }
    } catch (Throwable ignored) {
    } finally {
      eventsFetchInFlight = false;
    }
  }

  private static String httpGetString(String endpoint) {
    HttpURLConnection conn = null;
    try {
      URL url = new URL(endpoint);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("User-Agent", "Mozilla/5.0");
      conn.setConnectTimeout(4500);
      conn.setReadTimeout(4500);
      try (BufferedReader reader = new BufferedReader(
               new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
        return sb.toString();
      }
    } catch (Throwable ignored) {
      return null;
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private void fetchRate() {
    boolean needForAuc = settings.hwhelper != null && settings.hwhelper.enabled
        && settings.hwhelper.aucprice != null && settings.hwhelper.aucprice.enabled;
    boolean needForExchange = settings.tooltips != null && settings.tooltips.enabled
        && settings.tooltips.hwTooltips && settings.tooltips.hwCoinExchange;
    if (!needForAuc && !needForExchange) {
      return;
    }
    HttpURLConnection conn = null;
    try {
      URL url = new URL(ENDPOINT);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("User-Agent", "Mozilla/5.0");
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      try (BufferedReader reader = new BufferedReader(
               new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
        JsonArray array = new JsonParser().parse(sb.toString()).getAsJsonArray();
        List<Long> trades = new ArrayList<Long>();
        for (int i = 0; i < array.size(); i++) {
          JsonElement elem = array.get(i);
          if (elem != null && elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("rate")) {
              trades.add(Math.round(obj.get("rate").getAsDouble()));
            }
          }
        }
        if (!trades.isEmpty()) {
          rate = calculateRate(trades);
        }
      }
    } catch (Throwable ignored) {
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  public static long calculateRate(List<Long> trades) {
    if (trades == null || trades.isEmpty()) return 100_000L;
    List<Long> sorted = new ArrayList<Long>(trades.size());
    for (Long val : trades) {
      if (val != null && val.longValue() > 0L) sorted.add(val);
    }
    if (sorted.isEmpty()) return 100_000L;
    Collections.sort(sorted, Comparator.reverseOrder());
    long median = sorted.get(sorted.size() / 2);
    List<Long> filtered = new ArrayList<Long>(sorted.size());
    for (Long val : sorted) {
      if (val <= median * 1.5) {
        filtered.add(val);
      }
    }
    if (filtered.isEmpty()) filtered = sorted;
    double sum = 0;
    for (Long v : filtered) sum += v;
    double avg = sum / filtered.size();
    long rounded = (long) ((avg + 999.0) / 1000.0) * 1000L;
    return rounded > 0L ? rounded : 1_000L;
  }

  public long rate() {
    return rate;
  }

  public String formatTooltipPrice(String line) {
    if (FeaturePolicy.isBlocked("hwhelper.aucprice")
        || settings.hwhelper == null || !settings.hwhelper.enabled
        || !settings.hwhelper.aucprice.enabled) {
      return line;
    }
    long currentRate = rate;
    if (currentRate <= 0L) return line;
    if (line == null || line.indexOf('¤') < 0 || line.contains("❘") || line.contains("|")
        || line.contains("(") || line.contains("§#") || line.contains("₽")) {
      return line;
    }
    for (String blocked : BLOCKED) {
      if (line.contains(blocked)) return line;
    }
    Matcher m = PRICE_PATTERN.matcher(line);
    if (!m.find()) return line;

    String rawNum = m.group(1);
    StringBuilder digitsBuf = new StringBuilder(rawNum.length());
    for (int i = 0; i < rawNum.length(); i++) {
      char c = rawNum.charAt(i);
      if (c >= '0' && c <= '9') digitsBuf.append(c);
    }
    if (digitsBuf.length() == 0) return line;
    try {
      long coins = Long.parseLong(digitsBuf.toString());
      double rubles = (double) coins / (double) currentRate;
      double rounded = Math.round(rubles * 10.0) / 10.0;
      long whole = (long) rounded;
      long frac = Math.round((rounded - whole) * 10.0);
      String formattedWhole = String.format(Locale.US, "%,d", whole);
      String rublesStr = frac == 0 ? formattedWhole : formattedWhole + "." + frac;
      String append = " §8(§e" + rublesStr + " §a|§e§l❘§r§a|§8)";
      return line + append;
    } catch (NumberFormatException e) {
      return line;
    }
  }

  public String formatContainerTitle(String title) {
    if (FeaturePolicy.isBlocked("hwhelper.aucprice")
        || settings.hwhelper == null || !settings.hwhelper.enabled
        || !settings.hwhelper.aucprice.enabled) {
      return title;
    }
    if (title == null || !title.contains("Аукцион") || title.contains("1 |❘|")) {
      return title;
    }
    return title + " | " + compact(rate) + " — 1 |❘|";
  }

  private static String num(double d) {
    if (d >= 100.0) return String.format(Locale.ROOT, "%.0f", d);
    if (d >= 10.0) return String.format(Locale.ROOT, "%.1f", d);
    return String.format(Locale.ROOT, "%.2f", d);
  }

  public static String compact(long val) {
    if (val >= 1_000_000_000_000L) return num(val / 1.0e12) + "T";
    if (val >= 1_000_000_000L) return num(val / 1.0e9) + "B";
    if (val >= 1_000_000L) return num(val / 1.0e6) + "M";
    if (val >= 1_000L) return num(val / 1.0e3) + "K";
    return String.valueOf(val);
  }

  public static String catOf(int number) {
    if (number <= 15) return "solo";
    if (number <= 31) return "duo";
    if (number <= 47) return "trio";
    return "clans";
  }

  public static String keyOf(int number) {
    return number == 1 ? "lanarchy" : "lanarchy" + number;
  }

  private String lang() {
    return I18n.language(settings != null ? settings.language : null);
  }

  public static String catName(int number) {
    if (number <= 15) return "СолоЛайт";
    if (number <= 31) return "ДуоЛайт";
    if (number <= 47) return "ТриоЛайт";
    return "КланЛайт";
  }

  public static String catName(int number, String language) {
    return I18n.text(language, "hwhelper.cat." + catOf(number));
  }

  public String handleCommand(String command) {
    if (command == null || FeaturePolicy.isBlocked("hwhelper")
        || settings.hwhelper == null || !settings.hwhelper.enabled) {
      return command;
    }
    String trimmed = command.trim();

    if (!FeaturePolicy.isBlocked("hwhelper.betterSell") && settings.hwhelper.betterSell.enabled) {
      Matcher sellMatcher = AH_SELL_PATTERN.matcher(trimmed);
      if (sellMatcher.matches()) {
        boolean stackTotal = "sells".equalsIgnoreCase(sellMatcher.group(1));
        String args = sellMatcher.group(2).trim();
        String modified = parseBetterSell(args, stackTotal);
        if (modified != null) {
          return "/ah sell " + modified;
        }
      }
    }

    if (!FeaturePolicy.isBlocked("hwhelper.answap") && settings.hwhelper.answap.enabled) {
      Matcher anMatcher = AN_PATTERN.matcher(trimmed);
      if (anMatcher.matches()) {
        String numStr = anMatcher.group(1);
        if (numStr == null || numStr.isEmpty()) {
          String alias = trimmed.toLowerCase(Locale.ROOT).startsWith("/lt") ? "lt" : "an";
          platform.sendFeedback(I18n.format(lang(), "hwhelper.msg.usage",
              Collections.<String, Object>singletonMap("alias", alias)));
          return "";
        }
        try {
          int num = Integer.parseInt(numStr);
          return initiateAnSwap(num);
        } catch (NumberFormatException ignored) {
        }
      }
    }

    return command;
  }

  private String parseBetterSell(String args, boolean stackTotal) {
    String[] tokens = args.split("[\\s|]+");
    boolean perUnit = stackTotal;
    boolean hadSuffix = false;
    Long unitPrice = null;
    StringBuilder trailing = new StringBuilder();

    for (String token : tokens) {
      if (token.isEmpty()) continue;
      String t = token.toLowerCase(Locale.ROOT);
      if ("each".equals(t) || "шт".equals(t) || "поштучно".equals(t)) {
        perUnit = true;
        continue;
      }
      if (!stackTotal && unitPrice == null && tokens.length > 1 && "1".equals(t)) {
        perUnit = true;
        continue;
      }
      if (unitPrice == null) {
        Matcher pm = PRICE_TOKEN_PATTERN.matcher(t);
        if (pm.matches()) {
          try {
            double base = Double.parseDouble(pm.group(1).replace(',', '.'));
            String suffix = pm.group(2);
            long multiplier = 1L;
            if (suffix != null) {
              hadSuffix = true;
              switch (suffix.toLowerCase(Locale.ROOT)) {
                case "k":
                  multiplier = 1_000L;
                  break;
                case "kk":
                case "m":
                  multiplier = 1_000_000L;
                  break;
                case "kkk":
                case "b":
                  multiplier = 1_000_000_000L;
                  break;
              }
            } else if (pm.group(1).indexOf('.') >= 0 || pm.group(1).indexOf(',') >= 0) {
              hadSuffix = true;
            }
            unitPrice = (long) Math.round(base * multiplier);
            continue;
          } catch (NumberFormatException ignored) {
          }
        }
      }
      if (!stackTotal) {
        if (trailing.length() > 0) trailing.append(' ');
        trailing.append(token);
      }
    }

    if (unitPrice == null) return null;
    if (perUnit) {
      int count = platform.mainHandCount();
      if (count <= 0) count = 1;
      long total = unitPrice > Long.MAX_VALUE / count ? Long.MAX_VALUE : unitPrice * count;
      return String.valueOf(total);
    }
    if (!hadSuffix) return null;
    if (trailing.length() > 0) {
      return unitPrice + " " + trailing;
    }
    return String.valueOf(unitPrice);
  }

  public String initiateAnSwap(int num) {
    if (FeaturePolicy.isBlocked("hwhelper.answap")) return "";
    if (swapState != SwapState.IDLE) {
      platform.sendFeedback(I18n.text(lang(), "hwhelper.msg.already_swapping"));
      return "";
    }
    if (num < 1 || num > 200) {
      platform.sendFeedback(I18n.format(lang(), "hwhelper.msg.invalid_number",
          Collections.<String, Object>singletonMap("num", Integer.valueOf(num))));
      return "";
    }
    if (platform.isInCombat()) {
      platform.sendFeedback(I18n.text(lang(), "hwhelper.msg.in_combat"));
      return "";
    }
    if (platform.currentWorldToken() == null) {
      platform.sendFeedback(I18n.text(lang(), "hwhelper.msg.not_connected"));
      return "";
    }

    targetAnka = num;
    targetCat = resolvePreferredCategory(num);
    targetKey = keyOf(num);
    activeCategory = "";
    foundCategory = "";
    fullScanAnnounced = false;
    scanCompletedNow = false;
    detectedCategories.clear();
    visitedCategories.clear();
    prevWorldToken = platform.currentWorldToken();
    chatBeforeSwap = safe(platform.lastChatMessage());
    chatTokenBeforeSwap = platform.lastChatToken();
    swapTicks = 0;
    stateTicks = 0;
    delayTicks = 0;
    openStage = 0;
    menuSteps = 0;
    reopenAttempts = 0;
    lastMenuFingerprint = "";
    clickedFingerprint = "";
    reopenFromFingerprint = "";

    List<PlatformServices.ContainerSlot> currentSlots = platform.containerSlots();
    if (hasMeaningfulSlots(currentSlots)) {
      swapState = SwapState.WAITING_MENU_STEP;
      stepOpenMenu(currentSlots);
      return "";
    }

    if (platform.isInLobby()) {
      platform.sendCommandToServer("lite");
      swapState = SwapState.OPENING_MENU;
      openStage = 0;
      stateTicks = 0;
      return "";
    }

    swapState = SwapState.WAITING_HUB_WORLD;
    platform.sendCommandToServer("hub");
    return "";
  }

  private boolean isNewChatContaining(String needle) {
    String latestChatMessage = safe(platform.lastChatMessage());
    if (latestChatMessage.isEmpty()) return false;
    String lower = stripFormatting(latestChatMessage).toLowerCase(Locale.ROOT);
    if (!lower.contains(needle)) return false;
    if (platform.lastChatToken() != null) {
      return platform.lastChatToken() != chatTokenBeforeSwap;
    }
    return !latestChatMessage.equalsIgnoreCase(chatBeforeSwap);
  }

  public void tick() {
    String alertSound = pendingEventAlertSound;
    if (alertSound != null) {
      pendingEventAlertSound = null;
      platform.playClientSound(alertSound, 0.9f, 1.25f);
    }
    if (swapState == SwapState.IDLE) return;
    if (swapState != SwapState.WAITING_HUB_WORLD && platform.currentWorldToken() == null) {
      resetSwap();
      return;
    }
    if (isNewChatContaining("выполняется подключение") || isNewChatContaining("подключение к")) {
      resetSwap();
      return;
    }
    if (isNewChatContaining("не удалось подключиться") || isNewChatContaining("переполнен")) {
      failSwap(I18n.format(lang(), "hwhelper.msg.connect_failed",
          Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
      return;
    }
    swapTicks++;
    stateTicks++;
    if (delayTicks > 0) {
      delayTicks--;
      return;
    }
    int maxTicks = !isCatalogScanned() ? 500 : 300;
    if (swapTicks > maxTicks) {
      String lang = lang();
      Map<String, Object> args = new LinkedHashMap<String, Object>(4);
      args.put("category", catName(targetAnka, lang));
      args.put("num", Integer.valueOf(targetAnka));
      failSwap(I18n.format(lang, "hwhelper.msg.timeout", args));
      return;
    }

    if (swapState == SwapState.WAITING_HUB_WORLD) {
      List<PlatformServices.ContainerSlot> open = platform.containerSlots();
      if (hasMeaningfulSlots(open)) {
        swapState = SwapState.WAITING_MENU_STEP;
        stateTicks = 0;
        stepOpenMenu(open);
        return;
      }
      Object currentWorld = platform.currentWorldToken();
      boolean worldChanged = currentWorld != null && currentWorld != prevWorldToken;
      boolean alreadyConnected =
          platform.hasRecentChatMessage("уже подключен") && isNewChatContaining("уже подключен");

      if (worldChanged || alreadyConnected || platform.isInLobby() || stateTicks >= 35) {
        platform.sendCommandToServer("lite");
        swapState = SwapState.OPENING_MENU;
        openStage = 0;
        stateTicks = 0;
        swapTicks = 0;
        return;
      }
      return;
    }

    if (swapState == SwapState.OPENING_MENU) {
      List<PlatformServices.ContainerSlot> slots = platform.containerSlots();
      if (hasMeaningfulSlots(slots)) {
        swapState = SwapState.WAITING_MENU_STEP;
        stateTicks = 0;
        stepOpenMenu(slots);
        return;
      }
      if (stateTicks == 12 && openStage == 0) {
        openStage = 1;
        platform.sendCommandToServer("menu");
        return;
      }
      if (stateTicks == 24 && openStage == 1) {
        openStage = 2;
        platform.useSelectorItem();
        return;
      }
      if (stateTicks >= 56) {
        failSwap(I18n.text(lang(), "hwhelper.msg.menu_open_failed"));
      }
      return;
    }

    if (swapState == SwapState.WAITING_MENU_STEP) {
      List<PlatformServices.ContainerSlot> slots = platform.containerSlots();
      if (!hasMeaningfulSlots(slots)) {
        if (slots.isEmpty() && stateTicks == 6 && needsMoreCategoryNavigation() && reopenAttempts < 8) {
          reopenAttempts++;
          activeCategory = "";
          clickedFingerprint = "";
          if (reopenAttempts <= 4) {
            platform.sendCommandToServer("lite");
          } else {
            platform.useSelectorItem();
          }
          swapState = SwapState.OPENING_MENU;
          openStage = 0;
          stateTicks = 0;
          delayTicks = 2;
          return;
        }
        if (stateTicks >= 35) {
          failSwap(I18n.text(lang(), "hwhelper.msg.menu_closed"));
        }
        return;
      }
      stepOpenMenu(slots);
    }
  }

  private boolean stepOpenMenu(List<PlatformServices.ContainerSlot> slots) {
    String fp = menuFingerprint(platform.openContainerTitle(), slots);
    if (!fp.equals(lastMenuFingerprint)) {
      lastMenuFingerprint = fp;
      if (!fp.equals(clickedFingerprint)) {
        stateTicks = 0;
      }
    }
    if (fp.equals(clickedFingerprint)) {
      if (stateTicks >= 40) {
        failSwap(I18n.format(lang(), "hwhelper.msg.menu_no_response",
            Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
        return true;
      }
      return true;
    }

    if (menuSteps >= 40) {
      failSwap(I18n.text(lang(), "hwhelper.msg.too_many_steps"));
      return true;
    }

    Map<Integer, PlatformServices.ContainerSlot> anarchySlots = extractAnarchySlots(slots);
    Map<String, PlatformServices.ContainerSlot> categoryMap = detectCategorySlots(slots);
    PlatformServices.ContainerSlot backSlot = findBackSlot(slots);
    boolean looksLikeCategoryMenu =
        categoryMap.size() > 1 && anarchySlots.size() <= 1 && !hasTaggedAnarchy(anarchySlots);
    if (!anarchySlots.isEmpty() && !looksLikeCategoryMenu) {
      String screenCat = !activeCategory.isEmpty()
          ? activeCategory
          : inferScreenCategory(platform.openContainerTitle(), slots, anarchySlots);
      Map<String, PlatformServices.ContainerSlot> inScreenTabs =
          findInScreenCategoryTabs(slots, screenCat);
      recordDetectedCategories(inScreenTabs.keySet());
      if (!screenCat.isEmpty()) {
        visitedCategories.add(screenCat);
        updateCatalogForCategory(screenCat, anarchySlots);
      }

      boolean allDetectedVisited =
          !detectedCategories.isEmpty() && !hasUnvisitedDetectedCategory();
      if (!isCatalogScanned() && !scanCompletedNow && allDetectedVisited) {
        completeFullScan();
      }

      PlatformServices.ContainerSlot serverSlot = anarchySlots.get(targetAnka);
      if (serverSlot == null) {
        serverSlot = findTargetServerSlot(slots, targetAnka, targetKey);
      }
      if (serverSlot != null) {
        if (!screenCat.isEmpty()) {
          foundCategory = screenCat;
        }
        if (isRestartingSlot(serverSlot)) {
          failSwap(I18n.format(lang(), "hwhelper.msg.restarting",
              Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
          return true;
        }
        if (platform.clickContainerSlot(serverSlot.index)) {
          resetSwap();
          return true;
        }
      }

      Map.Entry<String, PlatformServices.ContainerSlot> inScreenTab =
          findFirstUnvisitedTab(inScreenTabs, screenCat);
      if (serverSlot == null) {
        if (allDetectedVisited && foundCategory.isEmpty()) {
          if (stateTicks >= 10) {
            failSwap(I18n.format(lang(), "hwhelper.msg.not_found_tabs",
                Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
            return true;
          }
          return false;
        }
        if (detectedCategories.isEmpty() || hasUnvisitedDetectedCategory()
            || (!foundCategory.isEmpty() && !foundCategory.equals(screenCat))
            || (!isCatalogScanned() && reopenAttempts == 0)) {
          if (leaveSubmenuForNextCategory(fp, backSlot, inScreenTab, false)) {
            return true;
          }
        }
        if (stateTicks >= 20) {
          failSwap(I18n.format(lang(), "hwhelper.msg.not_found_checked",
              Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
          return true;
        }
        return false;
      }
    }

    if (!categoryMap.isEmpty()) {
      recordDetectedCategories(categoryMap.keySet());

      if (!isCatalogScanned() && !scanCompletedNow && !hasUnvisitedDetectedCategory()
          && !detectedCategories.isEmpty()) {
        completeFullScan();
        if (foundCategory.isEmpty()) {
          String mapped = lookupCatalogCategory(targetAnka);
          if (mapped != null) foundCategory = mapped;
        }
        if (foundCategory.isEmpty() || !categoryMap.containsKey(foundCategory)) {
          failSwap(I18n.format(lang(), "hwhelper.msg.not_found_tabs",
              Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
          return true;
        }
      }

      String chosenCat = "";
      if (!foundCategory.isEmpty() && categoryMap.containsKey(foundCategory)) {
        chosenCat = foundCategory;
      } else {
        chosenCat = pickNextCategory(categoryMap);
      }

      if (chosenCat == null || chosenCat.isEmpty()) {
        if (!isCatalogScanned() && !detectedCategories.isEmpty()) {
          markCatalogScanned();
        }
        failSwap(I18n.format(lang(), "hwhelper.msg.not_found_tabs",
            Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
        return true;
      }

      PlatformServices.ContainerSlot catSlot = categoryMap.get(chosenCat);
      if (catSlot != null && platform.clickContainerSlot(catSlot.index)) {
        activeCategory = chosenCat;
        reopenFromFingerprint = "";
        clickedFingerprint = fp;
        menuSteps++;
        stateTicks = 0;
        delayTicks = 3;
        return true;
      }
    }

    if (!activeCategory.isEmpty() && anarchySlots.isEmpty()
        && findMainMenuLiteSlot(slots) == null && stateTicks >= 10) {
      visitedCategories.add(activeCategory);
      if (!isCatalogScanned() && !scanCompletedNow
          && !detectedCategories.isEmpty() && !hasUnvisitedDetectedCategory()) {
        completeFullScan();
      }
      Map.Entry<String, PlatformServices.ContainerSlot> tab =
          findFirstUnvisitedTab(findInScreenCategoryTabs(slots, activeCategory), activeCategory);
      if (leaveSubmenuForNextCategory(fp, backSlot, tab, false)) {
        return true;
      }
    }

    PlatformServices.ContainerSlot directCatSlot = findCategorySlot(slots, targetCat);
    if (directCatSlot != null && platform.clickContainerSlot(directCatSlot.index)) {
      activeCategory = targetCat;
      clickedFingerprint = fp;
      menuSteps++;
      stateTicks = 0;
      delayTicks = 3;
      return true;
    }

    PlatformServices.ContainerSlot modeSlot = findMainMenuLiteSlot(slots);
    if (modeSlot != null && platform.clickContainerSlot(modeSlot.index)) {
      activeCategory = "";
      clickedFingerprint = fp;
      menuSteps++;
      stateTicks = 0;
      delayTicks = 3;
      return true;
    }

    if (stateTicks >= 20) {
      failSwap(I18n.format(lang(), "hwhelper.msg.not_found_changed",
          Collections.<String, Object>singletonMap("num", Integer.valueOf(targetAnka))));
      return true;
    }
    return false;
  }

  private boolean isCatalogScanned() {
    return settings.hwhelper != null && settings.hwhelper.answap != null
        && settings.hwhelper.answap.catalogScanned;
  }

  private String resolvePreferredCategory(int number) {
    String mapped = lookupCatalogCategory(number);
    return mapped != null && !mapped.isEmpty() ? mapped : catOf(number);
  }

  private String lookupCatalogCategory(int number) {
    if (settings.hwhelper == null || settings.hwhelper.answap == null
        || settings.hwhelper.answap.catalog == null) {
      return null;
    }
    return settings.hwhelper.answap.catalog.get(Integer.toString(number));
  }

  private void updateCatalogForCategory(
      String category, Map<Integer, PlatformServices.ContainerSlot> screenServers) {
    if (settings.hwhelper == null || settings.hwhelper.answap == null || category == null
        || category.isEmpty()) {
      return;
    }
    if (settings.hwhelper.answap.catalog == null) {
      settings.hwhelper.answap.catalog = new LinkedHashMap<String, String>();
    }
    Map<String, String> catalog = settings.hwhelper.answap.catalog;
    boolean changed = false;
    for (Integer num : screenServers.keySet()) {
      String key = Integer.toString(num);
      if (!category.equals(catalog.get(key))) {
        catalog.put(key, category);
        changed = true;
      }
    }
    String targetStr = Integer.toString(targetAnka);
    if (!screenServers.containsKey(targetAnka) && category.equals(catalog.get(targetStr))) {
      catalog.remove(targetStr);
      changed = true;
    }
    if (changed) persistCatalog();
  }

  private void markCatalogScanned() {
    if (settings.hwhelper == null || settings.hwhelper.answap == null) return;
    if (!settings.hwhelper.answap.catalogScanned) {
      settings.hwhelper.answap.catalogScanned = true;
      persistCatalog();
    }
  }

  private void persistCatalog() {
    settings.validate();
    if (onSettingsChanged != null) {
      onSettingsChanged.run();
    }
  }

  private void recordDetectedCategories(java.util.Collection<String> keys) {
    for (String key : keys) {
      if (key != null && !key.isEmpty() && !detectedCategories.contains(key)) {
        detectedCategories.add(key);
      }
    }
  }

  private void completeFullScan() {
    scanCompletedNow = true;
    markCatalogScanned();
    int count = settings.hwhelper != null && settings.hwhelper.answap != null
            && settings.hwhelper.answap.catalog != null
        ? settings.hwhelper.answap.catalog.size()
        : 0;
    platform.sendFeedback(I18n.format(lang(), "hwhelper.msg.catalog_saved",
        Collections.<String, Object>singletonMap("count", Integer.valueOf(count))));
  }

  private boolean needsMoreCategoryNavigation() {
    return !detectedCategories.isEmpty() && (hasUnvisitedDetectedCategory()
        || (!isCatalogScanned() && !scanCompletedNow)
        || !foundCategory.isEmpty());
  }

  private boolean leaveSubmenuForNextCategory(
      String fp,
      PlatformServices.ContainerSlot backSlot,
      Map.Entry<String, PlatformServices.ContainerSlot> inScreenTab,
      boolean closeOnlyReopen) {
    if (backSlot != null && platform.clickContainerSlot(backSlot.index)) {
      activeCategory = "";
      reopenFromFingerprint = "";
      clickedFingerprint = fp;
      menuSteps++;
      stateTicks = 0;
      delayTicks = 2;
      return true;
    }
    if (inScreenTab != null && platform.clickContainerSlot(inScreenTab.getValue().index)) {
      activeCategory = inScreenTab.getKey();
      reopenFromFingerprint = "";
      clickedFingerprint = fp;
      menuSteps++;
      stateTicks = 0;
      delayTicks = 2;
      return true;
    }
    if (reopenAttempts < 8 && !fp.equals(reopenFromFingerprint)) {
      boolean closed = platform.closeContainer();
      if (closeOnlyReopen && !closed) {
        return false;
      }
      reopenFromFingerprint = fp;
      reopenAttempts++;
      activeCategory = "";
      lastMenuFingerprint = "";
      clickedFingerprint = closed ? "" : fp;
      platform.sendCommandToServer("lite");
      if (closed) {
        swapState = SwapState.OPENING_MENU;
        openStage = 0;
      }
      menuSteps++;
      stateTicks = 0;
      delayTicks = 3;
      return true;
    }
    return false;
  }

  private boolean hasUnvisitedDetectedCategory() {
    for (String cat : detectedCategories) {
      if (!visitedCategories.contains(cat)) return true;
    }
    return false;
  }

  private String pickNextCategory(Map<String, PlatformServices.ContainerSlot> categoryMap) {
    String mapped = lookupCatalogCategory(targetAnka);
    if (mapped != null && !visitedCategories.contains(mapped) && categoryMap.containsKey(mapped)) {
      return mapped;
    }
    if (targetCat != null && !visitedCategories.contains(targetCat)
        && categoryMap.containsKey(targetCat)) {
      return targetCat;
    }
    String heuristic = catOf(targetAnka);
    if (!visitedCategories.contains(heuristic) && categoryMap.containsKey(heuristic)) {
      return heuristic;
    }
    String[] orderedCats = {"solo", "duo", "trio", "clans"};
    for (String cat : orderedCats) {
      if (!visitedCategories.contains(cat) && categoryMap.containsKey(cat)) {
        return cat;
      }
    }
    for (String cat : categoryMap.keySet()) {
      if (!visitedCategories.contains(cat)) return cat;
    }
    return null;
  }

  private static boolean hasMeaningfulSlots(List<PlatformServices.ContainerSlot> slots) {
    if (slots == null || slots.isEmpty()) return false;
    for (PlatformServices.ContainerSlot slot : slots) {
      if (slot == null) continue;
      String id = safe(slot.itemId).toLowerCase(Locale.ROOT);
      boolean glassPane = id.endsWith("glass_pane");
      if (!glassPane) return true;
      String cleanName = stripFormatting(slot.name);
      if (!cleanName.isEmpty() || !safe(slot.server).isEmpty() || !safe(slot.serverType).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRestartingSlot(PlatformServices.ContainerSlot slot) {
    if (slot == null) return false;
    String joined = (stripFormatting(slot.name) + " " + stripFormatting(slot.lore))
        .toLowerCase(Locale.ROOT);
    return joined.contains("обновить статус") || joined.contains("перезагружается")
        || joined.contains("тех. работы") || joined.contains("технические работы");
  }

  private static boolean isIgnoredTypeOrText(PlatformServices.ContainerSlot slot, String rawType) {
    if (isBackKeyword(rawType) || isIgnoredServiceType(rawType)) return true;
    String name = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
    String lore = stripFormatting(slot.lore).toLowerCase(Locale.ROOT);
    if (isCloseOrPageKeyword(name, lore)) return true;
    String joined = name + " " + lore;
    return (joined.contains("гриф") && !joined.contains("лайт"))
        || (joined.contains("grief") && !joined.contains("lite"));
  }

  private static boolean isIgnoredServiceType(String rawType) {
    if (rawType == null || rawType.isEmpty()) return false;
    return rawType.contains("close") || rawType.contains("exit") || rawType.contains("info")
        || rawType.contains("grief") || "none".equals(rawType) || rawType.contains("page")
        || rawType.contains("next") || rawType.contains("prev");
  }

  private static boolean isCloseOrPageKeyword(String cleanName, String cleanLore) {
    String combined = safe(cleanName) + " " + safe(cleanLore);
    return safe(cleanName).contains("закры") || safe(cleanName).contains("close")
        || combined.contains("страниц") || combined.contains("page")
        || safe(cleanName).contains("следующ") || safe(cleanName).contains("next")
        || safe(cleanName).contains("предыдущ") || safe(cleanName).contains("prev");
  }

  private static String matchSingleCategoryName(String text) {
    String clean = stripFormatting(safe(text)).toLowerCase(Locale.ROOT);
    if (clean.isEmpty()) return "";
    String[] orderedCats = {"solo", "duo", "trio", "clans"};
    String found = "";
    for (String cat : orderedCats) {
      if (matchesCategoryText(clean, cat)) {
        if (!found.isEmpty()) return "";
        found = cat;
      }
    }
    return found;
  }

  private static Map<String, PlatformServices.ContainerSlot> detectCategorySlots(
      List<PlatformServices.ContainerSlot> slots) {
    Map<String, PlatformServices.ContainerSlot> map =
        new LinkedHashMap<String, PlatformServices.ContainerSlot>();
    Set<Integer> mappedSlotIndices = new LinkedHashSet<Integer>();
    for (PlatformServices.ContainerSlot slot : slots) {
      if (!safe(slot.server).isEmpty() || extractAnarchyNumber(slot) > 0) continue;
      String rawType = safe(slot.serverType).trim().toLowerCase(Locale.ROOT);
      if (!rawType.isEmpty() && !isIgnoredTypeOrText(slot, rawType) && !map.containsKey(rawType)) {
        map.put(rawType, slot);
        mappedSlotIndices.add(slot.index);
      }
    }
    String[] orderedCats = {"solo", "duo", "trio", "clans"};
    for (PlatformServices.ContainerSlot slot : slots) {
      if (mappedSlotIndices.contains(slot.index) || !safe(slot.server).isEmpty()
          || extractAnarchyNumber(slot) > 0) {
        continue;
      }
      if (isBackKeyword(safe(slot.serverType)) || isIgnoredServiceType(safe(slot.serverType).toLowerCase(Locale.ROOT))) {
        continue;
      }
      String name = stripFormatting(slot.name);
      String lore = stripFormatting(slot.lore);
      String joined = (name + " " + lore).toLowerCase(Locale.ROOT);
      if (isCloseOrPageKeyword(name.toLowerCase(Locale.ROOT), lore.toLowerCase(Locale.ROOT))) {
        continue;
      }
      if ((joined.contains("гриф") && !joined.contains("лайт"))
          || (joined.contains("grief") && !joined.contains("lite"))) {
        continue;
      }
      for (String cat : orderedCats) {
        if (!map.containsKey(cat)
            && (matchesCategoryText(name, cat) || matchesCategoryText(lore, cat))) {
          map.put(cat, slot);
          mappedSlotIndices.add(slot.index);
          break;
        }
      }
    }
    return map;
  }

  private static Map<String, PlatformServices.ContainerSlot> findInScreenCategoryTabs(
      List<PlatformServices.ContainerSlot> slots, String screenCat) {
    Map<String, PlatformServices.ContainerSlot> tabs =
        new LinkedHashMap<String, PlatformServices.ContainerSlot>();
    for (PlatformServices.ContainerSlot slot : slots) {
      if (!safe(slot.server).isEmpty() || extractAnarchyNumber(slot) > 0) continue;
      String rawType = safe(slot.serverType).trim().toLowerCase(Locale.ROOT);
      if (!rawType.isEmpty()) {
        if (!isIgnoredTypeOrText(slot, rawType) && !rawType.equals(screenCat)
            && !tabs.containsKey(rawType)) {
          tabs.put(rawType, slot);
        }
        continue;
      }
      String name = stripFormatting(slot.name);
      String lore = stripFormatting(slot.lore);
      if (isCloseOrPageKeyword(name.toLowerCase(Locale.ROOT), lore.toLowerCase(Locale.ROOT))) {
        continue;
      }
      String cat = matchSingleCategoryName(name);
      if (!cat.isEmpty() && !cat.equals(screenCat) && !tabs.containsKey(cat)) {
        tabs.put(cat, slot);
      }
    }
    return tabs;
  }

  private Map.Entry<String, PlatformServices.ContainerSlot> findFirstUnvisitedTab(
      Map<String, PlatformServices.ContainerSlot> tabs, String screenCat) {
    for (Map.Entry<String, PlatformServices.ContainerSlot> entry : tabs.entrySet()) {
      String cat = entry.getKey();
      if (!cat.equals(screenCat) && !visitedCategories.contains(cat)) {
        return entry;
      }
    }
    return null;
  }

  private static boolean hasTaggedAnarchy(Map<Integer, PlatformServices.ContainerSlot> anarchySlots) {
    for (PlatformServices.ContainerSlot slot : anarchySlots.values()) {
      if (!safe(slot.server).trim().isEmpty()) return true;
    }
    return false;
  }

  private static Map<Integer, PlatformServices.ContainerSlot> extractAnarchySlots(
      List<PlatformServices.ContainerSlot> slots) {
    Map<Integer, PlatformServices.ContainerSlot> map =
        new LinkedHashMap<Integer, PlatformServices.ContainerSlot>();
    for (PlatformServices.ContainerSlot slot : slots) {
      int num = extractAnarchyNumber(slot);
      if (num >= 1 && !map.containsKey(num)) {
        map.put(num, slot);
      }
    }
    return map;
  }

  private static boolean isRangeMatch(String text, int numStart, int numEnd) {
    if (Pattern.compile("\\d\\s*[-–—]\\s*(?:#|№)?\\s*$")
        .matcher(text.substring(0, numStart))
        .find()) {
      return true;
    }
    return Pattern.compile("^\\s*(?:[-–—]\\s*(?:#|№)?\\s*\\d|\\.\\.\\s*(?:#|№)?\\s*\\d)")
        .matcher(text.substring(numEnd))
        .find();
  }

  private static int extractAnarchyNumber(PlatformServices.ContainerSlot slot) {
    if (slot == null) return -1;
    String srv = safe(slot.server).trim().toLowerCase(Locale.ROOT);
    if (!srv.isEmpty()) {
      Matcher m = Pattern.compile("^(?:lanarchy|lite)(\\d*)$").matcher(srv);
      if (m.matches()) {
        String digits = m.group(1);
        if (digits.isEmpty()) return 1;
        try {
          int val = Integer.parseInt(digits);
          return val >= 1 && val <= 256 ? val : -1;
        } catch (NumberFormatException ignored) {
          return -1;
        }
      }
    }
    String rawType = safe(slot.serverType).trim().toLowerCase(Locale.ROOT);
    if (!rawType.isEmpty() && !isBackKeyword(rawType)) {
      return -1;
    }
    String cleanName = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
    if (cleanName.isEmpty()) return -1;
    if ((cleanName.contains("гриф") && !cleanName.contains("лайт"))
        || (cleanName.contains("grief") && !cleanName.contains("lite"))) {
      return -1;
    }
    Matcher tagMatcher = Pattern.compile("(?:#|№)\\s*(\\d+)(?![\\d.])").matcher(cleanName);
    while (tagMatcher.find()) {
      if (isRangeMatch(cleanName, tagMatcher.start(1), tagMatcher.end(1))) continue;
      try {
        int val = Integer.parseInt(tagMatcher.group(1));
        if (val >= 1 && val <= 256) return val;
      } catch (NumberFormatException ignored) {
      }
    }
    String scrubbedName = cleanName
        .replaceAll("\\d+\\.\\d+(?:\\.\\d+)?", " ")
        .replaceAll("\\d+\\s*/\\s*\\d+", " ");
    Matcher wordMatcher =
        Pattern.compile("(?:лайт|lite|анарх\\S*|anarch\\S*)\\s*(\\d+)(?![\\d.])").matcher(scrubbedName);
    while (wordMatcher.find()) {
      if (isRangeMatch(scrubbedName, wordMatcher.start(1), wordMatcher.end(1))) continue;
      try {
        int val = Integer.parseInt(wordMatcher.group(1));
        if (val >= 1 && val <= 256) return val;
      } catch (NumberFormatException ignored) {
      }
    }
    String cleanLore = stripFormatting(slot.lore).toLowerCase(Locale.ROOT);
    if (!cleanLore.isEmpty()
        && !Pattern.compile("\\d\\s*[-–—]\\s*(?:#|№)?\\s*\\d").matcher(cleanName).find()) {
      if (!((cleanLore.contains("гриф") && !cleanLore.contains("лайт"))
              || (cleanLore.contains("grief") && !cleanLore.contains("lite")))) {
        Matcher loreTagMatcher = Pattern.compile("(?:#|№)\\s*(\\d+)(?!\\d)").matcher(cleanLore);
        while (loreTagMatcher.find()) {
          if (isRangeMatch(cleanLore, loreTagMatcher.start(1), loreTagMatcher.end(1))) continue;
          try {
            int val = Integer.parseInt(loreTagMatcher.group(1));
            if (val >= 1 && val <= 256) return val;
          } catch (NumberFormatException ignored) {
          }
        }
      }
    }
    return -1;
  }

  private static String inferScreenCategory(String title,
      List<PlatformServices.ContainerSlot> slots, Map<Integer, PlatformServices.ContainerSlot> anarchies) {
    String cleanTitle = stripFormatting(safe(title)).toLowerCase(Locale.ROOT);
    String[] orderedCats = {"solo", "duo", "trio", "clans"};
    for (String cat : orderedCats) {
      if (matchesCategoryText(cleanTitle, cat)) return cat;
    }
    for (PlatformServices.ContainerSlot slot : anarchies.values()) {
      String combined =
          (stripFormatting(slot.name) + " " + stripFormatting(slot.lore) + " " + safe(slot.serverType))
              .toLowerCase(Locale.ROOT);
      for (String cat : orderedCats) {
        if (cat.equalsIgnoreCase(safe(slot.serverType)) || matchesCategoryText(combined, cat)) {
          return cat;
        }
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      String srvType = safe(slot.serverType).trim().toLowerCase(Locale.ROOT);
      if (!srvType.isEmpty() && !isBackKeyword(srvType) && !isIgnoredServiceType(srvType)) {
        return srvType;
      }
    }
    return "";
  }

  private static boolean isBackPhrase(String text) {
    return text.contains("назад") || text.contains("вернут") || text.contains("возврат")
        || text.contains("обратно") || text.contains("в меню") || text.contains("к меню")
        || text.contains("главное меню") || text.contains("на главную")
        || text.contains("к категориям") || text.contains("выбор категор")
        || text.contains("к режимам") || text.contains("back") || text.contains("return")
        || text.contains("main menu");
  }

  private static PlatformServices.ContainerSlot findBackSlot(
      List<PlatformServices.ContainerSlot> slots) {
    for (PlatformServices.ContainerSlot slot : slots) {
      if (extractAnarchyNumber(slot) > 0) continue;
      if (isBackKeyword(safe(slot.server)) || isBackKeyword(safe(slot.serverType))) return slot;
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      if (extractAnarchyNumber(slot) > 0) continue;
      String cleanName = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
      String cleanLore = stripFormatting(slot.lore).toLowerCase(Locale.ROOT);
      if (cleanName.isEmpty() || isCloseOrPageKeyword(cleanName, cleanLore)) continue;
      if (isBackPhrase(cleanName) || cleanName.contains("◀") || cleanName.contains("←")
          || cleanName.contains("◄") || cleanName.startsWith("«")) {
        return slot;
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      if (extractAnarchyNumber(slot) > 0) continue;
      String srvType = safe(slot.serverType).trim().toLowerCase(Locale.ROOT);
      if (isIgnoredServiceType(srvType)) continue;
      String cleanName = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
      String cleanLore = stripFormatting(slot.lore).toLowerCase(Locale.ROOT);
      if (!matchSingleCategoryName(cleanName).isEmpty() || isCloseOrPageKeyword(cleanName, cleanLore)) {
        continue;
      }
      if (isBackPhrase(cleanLore)) {
        return slot;
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      if (extractAnarchyNumber(slot) > 0) continue;
      String srvType = safe(slot.serverType).trim().toLowerCase(Locale.ROOT);
      if (!srvType.isEmpty() && !isBackKeyword(srvType)) continue;
      String id = safe(slot.itemId).toLowerCase(Locale.ROOT);
      if (!id.endsWith("arrow") && !id.endsWith("_door")) continue;
      String cleanName = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
      String cleanLore = stripFormatting(slot.lore).toLowerCase(Locale.ROOT);
      if (!matchSingleCategoryName(cleanName).isEmpty() || isCloseOrPageKeyword(cleanName, cleanLore)) {
        continue;
      }
      return slot;
    }
    return null;
  }

  private static boolean isBackKeyword(String text) {
    String lower = safe(text).trim().toLowerCase(Locale.ROOT);
    return "back".equals(lower) || "return".equals(lower) || "menu".equals(lower)
        || "main".equals(lower) || "main_menu".equals(lower) || "mainmenu".equals(lower)
        || "categories".equals(lower) || "category".equals(lower) || "back_menu".equals(lower)
        || "prev_menu".equals(lower) || "go_back".equals(lower) || "home".equals(lower);
  }

  private void failSwap(String message) {
    platform.sendFeedback(message);
    resetSwap();
  }

  private void resetSwap() {
    swapState = SwapState.IDLE;
    swapTicks = 0;
    stateTicks = 0;
    delayTicks = 0;
    openStage = 0;
    menuSteps = 0;
    reopenAttempts = 0;
    activeCategory = "";
    foundCategory = "";
    fullScanAnnounced = false;
    scanCompletedNow = false;
    detectedCategories.clear();
    visitedCategories.clear();
    lastMenuFingerprint = "";
    clickedFingerprint = "";
    reopenFromFingerprint = "";
    prevWorldToken = null;
    chatBeforeSwap = "";
    chatTokenBeforeSwap = null;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String stripFormatting(String text) {
    if (text == null || text.isEmpty()) return "";
    return text.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
        .replaceAll("&[0-9a-fk-orA-FK-OR]", "")
        .trim();
  }

  private static String menuFingerprint(String title, List<PlatformServices.ContainerSlot> slots) {
    StringBuilder sb = new StringBuilder();
    sb.append(stripFormatting(safe(title))).append('|');
    for (PlatformServices.ContainerSlot slot : slots) {
      sb.append(slot.index)
          .append(':')
          .append(stripFormatting(slot.name))
          .append(':')
          .append(slot.serverType)
          .append(':')
          .append(slot.server)
          .append(';');
    }
    return sb.toString();
  }

  private static PlatformServices.ContainerSlot findMainMenuLiteSlot(
      List<PlatformServices.ContainerSlot> slots) {
    for (PlatformServices.ContainerSlot slot : slots) {
      String joined = (stripFormatting(slot.name) + " " + stripFormatting(slot.lore) + " "
          + safe(slot.serverType) + " " + safe(slot.server)).toLowerCase(Locale.ROOT);
      if ((joined.contains("лайт") || joined.contains("lite"))
          && joined.contains("1.16")
          && !joined.contains("1.20") && !joined.contains("1.21")
          && !joined.contains("гриф") && !joined.contains("grief")) {
        return slot;
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      String joined = (stripFormatting(slot.name) + " " + stripFormatting(slot.lore) + " "
          + safe(slot.serverType) + " " + safe(slot.server)).toLowerCase(Locale.ROOT);
      if ((joined.contains("лайт") || joined.contains("lite"))
          && !joined.contains("1.20") && !joined.contains("1.21")
          && !joined.contains("гриф") && !joined.contains("grief")) {
        return slot;
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      String joined = (stripFormatting(slot.name) + " " + stripFormatting(slot.lore) + " "
          + safe(slot.serverType) + " " + safe(slot.server)).toLowerCase(Locale.ROOT);
      if ((joined.contains("лайт") || joined.contains("lite"))
          && !joined.contains("гриф") && !joined.contains("grief")) {
        return slot;
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      String clean = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
      if (clean.contains("1.16") && !clean.contains("гриф")) return slot;
    }
    return null;
  }

  private static PlatformServices.ContainerSlot findCategorySlot(
      List<PlatformServices.ContainerSlot> slots, String cat) {
    for (PlatformServices.ContainerSlot slot : slots) {
      if (cat.equalsIgnoreCase(safe(slot.serverType))) return slot;
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      if (matchesCategoryText(stripFormatting(slot.name), cat)
          || matchesCategoryText(stripFormatting(slot.lore), cat)) {
        return slot;
      }
    }
    return null;
  }

  private static PlatformServices.ContainerSlot findTargetServerSlot(
      List<PlatformServices.ContainerSlot> slots, int anka, String key) {
    for (PlatformServices.ContainerSlot slot : slots) {
      if (key.equalsIgnoreCase(safe(slot.server))) return slot;
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      String cleanName = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
      String cleanLore = stripFormatting(slot.lore).toLowerCase(Locale.ROOT);
      String srv = safe(slot.server).toLowerCase(Locale.ROOT);
      if (matchesExactAnarchyTag(cleanName, anka) || matchesExactAnarchyTag(srv, anka)
          || matchesExactAnarchyTag(cleanLore, anka)) {
        return slot;
      }
    }
    for (PlatformServices.ContainerSlot slot : slots) {
      String cleanName = stripFormatting(slot.name).toLowerCase(Locale.ROOT);
      if (matchesServerStandaloneNumber(cleanName, anka)) return slot;
    }
    return null;
  }

  private static boolean matchesCategoryText(String text, String cat) {
    if (text == null || text.isEmpty()) return false;
    String lower = text.toLowerCase(Locale.ROOT);
    switch (cat) {
      case "solo":
        return lower.contains("соло") || lower.contains("solo");
      case "duo":
        return lower.contains("дуо") || lower.contains("duo");
      case "trio":
        return lower.contains("трио") || lower.contains("trio");
      case "clans":
        return lower.contains("клан") || lower.contains("clan");
      default:
        return false;
    }
  }

  static boolean matchesExactAnarchyTag(String text, int anka) {
    if (text == null || text.isEmpty()) return false;
    if (text.contains("гриф") && !text.contains("лайт")) return false;
    if (Pattern.compile("(?:#|№)\\s*" + anka + "(?!\\d)").matcher(text).find()) return true;
    if (Pattern.compile("(?:lanarchy|lite)\\s*" + (anka == 1 ? "(?!\\d)" : anka + "(?!\\d)"))
        .matcher(text)
        .find()) {
      return !text.contains("lanarchy") || anka != 1 || Pattern.compile("\\blanarchy\\b").matcher(text).find();
    }
    return false;
  }

  static boolean matchesServerStandaloneNumber(String text, int anka) {
    if (text == null || text.isEmpty()) return false;
    if (text.contains("гриф") && !text.contains("лайт")) return false;
    if (!(text.contains("лайт") || text.contains("lite") || text.contains("анарх")
            || text.contains("anarch") || text.contains("соло") || text.contains("дуо")
            || text.contains("трио") || text.contains("клан"))) {
      return false;
    }
    String scrubbed = text.replaceAll("\\d+\\.\\d+(?:\\.\\d+)?", " ")
        .replaceAll("\\d+\\s*/\\s*\\d+", " ");
    return Pattern.compile("(?<!\\d)" + anka + "(?!\\d)").matcher(scrubbed).find();
  }
}
