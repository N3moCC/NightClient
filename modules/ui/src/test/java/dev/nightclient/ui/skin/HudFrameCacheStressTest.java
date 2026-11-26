package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.NcParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HudFrameCacheStressTest {
  private static final Object METRICS = new Object();
  private static final String[] ITEMS = {"minecraft:stone", "minecraft:ender_pearl",
      "minecraft:golden_apple", "minecraft:totem_of_undying"};
  private static final String[] LANGUAGES = {"ru", "en"};

  @Test
  void randomizedHudDataAndSettingsStayInParity() throws Exception {
    Settings settings = staticSettings();
    Data data = new Data();
    Random random = new Random(0x5EEDC0DEL);
    SkinView cached = new SkinView(DefaultSkin.document(), settings, data);
    SkinView live = new SkinView(DefaultSkin.document(), settings, data);

    for (int step = 0; step < 72; step++) {
      if (step % 6 == 0) mutate(settings, data, random, step);
      int width = step % 18 < 9 ? 960 : 1180;
      int height = step % 24 < 12 ? 640 : 720;
      renderPair(cached, live, settings, data, width, height, 20 + step * .031);
    }
    assertTrue(cached.hudFrameCacheHits() > 0, "holding a random state for a few frames must hit");

    int hits = cached.hudFrameCacheHits();
    Object alternateMetrics = new Object();
    StressDigestCanvas cachedMetrics = new StressDigestCanvas(alternateMetrics);
    StressDigestCanvas liveMetrics = new StressDigestCanvas(alternateMetrics);
    settings.optimization.enabled = true;
    cached.renderHud(cachedMetrics, 1000, 700, 99, data.cooldowns, data.inventory, data.tnts);
    settings.optimization.enabled = false;
    live.invalidateLayout();
    live.renderHud(liveMetrics, 1000, 700, 99, data.cooldowns, data.inventory, data.tnts);
    assertEquals(
        liveMetrics.digest(), cachedMetrics.digest(), "alternate metrics key changed layout");
    assertEquals(
        hits, cached.hudFrameCacheHits(), "a different metrics key must miss the old list");
    settings.optimization.enabled = true;
  }

  @Test
  void retainedCooldownExitIsNeverFrozen() throws Exception {
    Settings settings = staticSettings();
    settings.menu.animationSpeed = 1f;
    settings.hud.showHeader = false;
    settings.hud.background = false;
    Data data = new Data();
    data.cooldowns.add(new Cooldown("pearl", "Pearl", "minecraft:ender_pearl", 8.4f, 15));
    SkinView cached = new SkinView(DefaultSkin.document(), settings, data);
    SkinView live = new SkinView(DefaultSkin.document(), settings, data);

    for (int frame = 0; frame < 12; frame++)
      renderPair(cached, live, settings, data, 1000, 700, 10 + frame * .05);
    int settledHits = cached.hudFrameCacheHits();
    assertTrue(settledHits > 0, "settled cooldown row should reach the cache");

    data.cooldowns.clear();
    renderPair(cached, live, settings, data, 1000, 700, 10.7);
    int removalHits = cached.hudFrameCacheHits();
    for (int frame = 0; frame < 4; frame++)
      renderPair(cached, live, settings, data, 1000, 700, 10.75 + frame * .04);
    assertEquals(
        removalHits, cached.hudFrameCacheHits(), "exit frames must render live, not replay");
    assertEquals(settledHits, removalHits, "the removal frame itself is not a cache hit");

    renderPair(cached, live, settings, data, 1000, 700, 11.0);
    renderPair(cached, live, settings, data, 1000, 700, 11.05);
    assertTrue(cached.hudFrameCacheHits() > removalHits,
        "after expiry the static frame should cache again");
  }

  @Test
  void marqueeAndNumericMotionStayLive() throws Exception {
    Settings settings = staticSettings();
    settings.menu.animationSpeed = 1f;
    settings.theme.motion.numericMotion = "roll";
    Data data = new Data();
    data.ping = 20;
    SkinDocument document = NcParser.parse("hud { panel { width=220px; height=80px; "
        + "text 'A very long scrolling label that must overflow its box' "
        + "{ x=0px; y=0px; width=28px; height=10px; text-overflow=marquee; scroll-speed=12; "
          + "scroll-pause=2400ms } "
        + "text '{{pingData.pingText}}' { x=0px; y=20px; width=100px; height=10px; "
        + "textMotion=numeric; animateValue=true; transitionDuration=180ms } } }");
    SkinView cached = new SkinView(document, settings, data);
    SkinView live = new SkinView(document, settings, data);

    for (int frame = 0; frame < 8; frame++)
      renderPair(cached, live, settings, data, 900, 600, 10 + frame * .06);
    assertEquals(0, cached.hudFrameCacheHits(), "marquee frames must not be cached");

    data.ping = 90;
    for (int frame = 0; frame < 10; frame++)
      renderPair(cached, live, settings, data, 900, 600, 10.5 + frame * .04);
    assertEquals(0, cached.hudFrameCacheHits(), "numeric motion beside a marquee must stay live");
  }

  @Test
  void numericTextMotionIsNotStoredMidTransition() throws Exception {
    Settings settings = staticSettings();
    settings.menu.animationSpeed = 1f;
    settings.theme.motion.numericMotion = "roll";
    Data data = new Data();
    data.ping = 20;
    SkinDocument document = NcParser.parse(
        "hud { text '{{pingData.pingText}}' { x=0px; y=0px; width=100px; height=10px; "
        + "textMotion=numeric; animateValue=true; transitionDuration=180ms } }");
    SkinView cached = new SkinView(document, settings, data);
    SkinView live = new SkinView(document, settings, data);

    renderPair(cached, live, settings, data, 900, 600, 1);
    renderPair(cached, live, settings, data, 900, 600, 1.4);
    renderPair(cached, live, settings, data, 900, 600, 1.5);
    int settledHits = cached.hudFrameCacheHits();
    assertTrue(settledHits > 0, "stable numeric text should be cacheable");

    data.ping = 90;
    for (int frame = 0; frame < 6; frame++)
      renderPair(cached, live, settings, data, 900, 600, 1.2 + frame * .04);
    assertEquals(
        settledHits, cached.hudFrameCacheHits(), "numeric interpolation must not be stored");

    renderPair(cached, live, settings, data, 900, 600, 1.6);
    renderPair(cached, live, settings, data, 900, 600, 1.7);
    assertTrue(cached.hudFrameCacheHits() > settledHits, "settled numeric text should cache again");
  }

  @Test
  void cacheGateOffIsAStrictLiveReference() throws Exception {
    Settings settings = staticSettings();
    Data data = new Data();
    mutate(settings, data, new Random(7), 0);
    SkinView view = new SkinView(DefaultSkin.document(), settings, data);
    settings.optimization.enabled = false;
    for (int frame = 0; frame < 7; frame++)
      view.renderHud(new StressDigestCanvas(), 1000, 700, 5 + frame * .1, data.cooldowns,
          data.inventory, data.tnts);
    assertEquals(0, view.hudFrameCacheHits());
    settings.optimization.enabled = true;
    settings.optimization.hudCache = false;
    for (int frame = 0; frame < 7; frame++)
      view.renderHud(new StressDigestCanvas(), 1000, 700, 6 + frame * .1, data.cooldowns,
          data.inventory, data.tnts);
    assertEquals(0, view.hudFrameCacheHits(), "hudCache=false must never hit");
  }

  private static void renderPair(SkinView cached, SkinView live, Settings settings, Data data,
      int width, int height, double time) {
    StressDigestCanvas cachedCanvas = new StressDigestCanvas();
    StressDigestCanvas liveCanvas = new StressDigestCanvas();
    settings.optimization.enabled = true;
    cached.renderHud(cachedCanvas, width, height, time, data.cooldowns, data.inventory, data.tnts);
    settings.optimization.enabled = false;
    live.invalidateLayout();
    live.renderHud(liveCanvas, width, height, time, data.cooldowns, data.inventory, data.tnts);
    settings.optimization.enabled = true;
    assertEquals(liveCanvas.digest(), cachedCanvas.digest(), "cache diverged at time " + time);
  }

  private static Settings staticSettings() {
    Settings settings = new Settings();
    settings.optimization.enabled = true;
    settings.menu.animationSpeed = 1000000f;
    return settings;
  }

  private static void mutate(Settings settings, Data data, Random random, int step) {
    data.cooldowns.clear();
    int cooldownCount = random.nextInt(4);
    for (int i = 0; i < cooldownCount; i++)
      data.cooldowns.add(new Cooldown("timer-" + step + "-" + i,
          i == 0 ? "Very long cooldown label " + step : "Timer " + step + " " + i,
          ITEMS[random.nextInt(ITEMS.length)], random.nextFloat() * 20, 20));

    data.inventory.clear();
    for (int i = 0; i < 1 + random.nextInt(5); i++)
      data.inventory.add(new InventorySlot(i, ITEMS[random.nextInt(ITEMS.length)], "Item " + i,
          1 + random.nextInt(64), random.nextInt(30), 100, random.nextBoolean()));

    data.armor.clear();
    for (int i = 0; i < random.nextInt(4); i++)
      data.armor.add(
          new InventorySlot(100 + i, ITEMS[i % ITEMS.length], "Armor " + i, 1, i * 7, 100));

    data.potions.clear();
    for (int i = 0; i < random.nextInt(3); i++)
      data.potions.add(new PotionView("effect-" + i, "Effect " + step + " " + i, random.nextInt(3),
          0xff336699, 5 + random.nextFloat() * 120, random.nextBoolean()));

    data.tnts.clear();
    for (int i = 0; i < random.nextInt(3); i++)
      data.tnts.add(new TntView(step * 10 + i, 10 + random.nextFloat() * 500,
          10 + random.nextFloat() * 400, .1f + random.nextFloat() * 4, random.nextFloat() * 60));

    data.target = random.nextBoolean()
        ? null
        : new TargetView(step, "entity-" + step, "Target " + step, random.nextFloat() * 20, 20,
              random.nextFloat() * 2, random.nextFloat() * 20, random.nextBoolean(), "",
              data.armor);
    data.vitals = new VitalsView(
        random.nextInt(21), random.nextFloat() * 5, random.nextBoolean(), random.nextBoolean());
    data.ping = 5 + random.nextInt(300);
    Map<String, Object> network = new LinkedHashMap<String, Object>();
    network.put("ping", data.ping);
    network.put("available", random.nextBoolean());
    network.put("connected", random.nextBoolean());
    network.put("serverAddress", "mc.example." + (step % 3) + ":25565");
    data.network = network;

    Map<String, Object> keys = new LinkedHashMap<String, Object>();
    keys.put("w", random.nextBoolean());
    keys.put("a", random.nextBoolean());
    keys.put("s", random.nextBoolean());
    keys.put("d", random.nextBoolean());
    keys.put("lmb", random.nextBoolean());
    keys.put("rmb", random.nextBoolean());
    keys.put("space", random.nextBoolean());
    keys.put("lmbCps", random.nextInt(20));
    keys.put("rmbCps", random.nextInt(20));
    keys.put("lmbText", random.nextInt(20) + " CPS");
    keys.put("rmbText", random.nextInt(20) + " CPS");
    data.keystrokes = keys;

    Map<String, Object> coordinates = new LinkedHashMap<String, Object>();
    coordinates.put("x", Integer.toString(random.nextInt(2000) - 1000));
    coordinates.put("y", Integer.toString(random.nextInt(256)));
    coordinates.put("z", Integer.toString(random.nextInt(2000) - 1000));
    data.coordinates = coordinates;

    Map<String, Object> scoreboard = new LinkedHashMap<String, Object>();
    scoreboard.put("title", "Board " + step);
    List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 1 + random.nextInt(6); i++) {
      Map<String, Object> line = new LinkedHashMap<String, Object>();
      line.put("text", "Line " + step + " / " + i);
      line.put("score", i);
      line.put("hasScore", random.nextBoolean());
      lines.add(line);
    }
    scoreboard.put("lines", lines);
    data.scoreboard = scoreboard;
    data.gameVersion = "26." + (step % 4);

    settings.language = LANGUAGES[random.nextInt(LANGUAGES.length)];
    settings.theme.accent = 0xff000000 | random.nextInt(0xffffff);
    settings.theme.secondaryAccent = 0xff000000 | random.nextInt(0xffffff);
    settings.theme.backgroundOpacity = .35f + random.nextFloat() * .65f;
    settings.menu.hudScale = .8f + random.nextFloat() * .5f;
    settings.hud.enabled = random.nextInt(5) != 0;
    settings.hud.hideWhenEmpty = random.nextBoolean();
    settings.hud.showHeader = random.nextBoolean();
    settings.hud.background = random.nextBoolean();
    settings.hud.icons = random.nextBoolean();
    settings.hud.names = random.nextBoolean();
    settings.hud.time = random.nextBoolean();
    settings.hud.progress = random.nextBoolean();
    settings.hud.horizontal = random.nextBoolean();
    settings.hud.maxVisible = 1 + random.nextInt(8);
    settings.hud.width = 128 + random.nextInt(80);
    settings.hud.fontScale = .8f + random.nextFloat() * .5f;
    settings.hud.rowHeight = 30 + random.nextInt(20);
    settings.hud.sort = random.nextBoolean() ? "remaining" : "name";
    settings.inventory.enabled = random.nextBoolean();
    settings.inventory.rows = 1 + random.nextInt(3);
    settings.inventory.hotbar = random.nextBoolean();
    settings.inventory.counts = random.nextBoolean();
    settings.inventory.durability = random.nextBoolean();
    settings.tnt.enabled = random.nextBoolean();
    settings.tnt.range = 16 + random.nextInt(80);
    settings.armor.enabled = random.nextBoolean();
    settings.armor.horizontal = random.nextBoolean();
    settings.armor.durability = random.nextBoolean();
    settings.armor.percent = random.nextBoolean();
    settings.armor.names = random.nextBoolean();
    settings.potions.enabled = random.nextBoolean();
    settings.potions.time = random.nextBoolean();
    settings.potions.level = random.nextBoolean();
    settings.potions.icons = random.nextBoolean();
    settings.potions.format = random.nextBoolean() ? "list"
        : random.nextBoolean()                     ? "compact"
                                                   : "pills";
    settings.target.enabled = random.nextBoolean();
    settings.target.style = random.nextBoolean() ? "card" : "compact";
    settings.target.absorption = random.nextBoolean();
    settings.target.distance = random.nextBoolean();
    settings.scoreboard.enabled = random.nextBoolean();
    settings.scoreboard.hideScores = random.nextBoolean();
    settings.scoreboard.cleanAds = random.nextBoolean();
    settings.scoreboard.background = random.nextBoolean();
    settings.keystrokes.enabled = random.nextBoolean();
    settings.keystrokes.mouseButtons = random.nextBoolean();
    settings.keystrokes.space = random.nextBoolean();
    settings.keystrokes.cps = random.nextBoolean();
    settings.keystrokes.background = random.nextBoolean();
    settings.coordinates.enabled = random.nextBoolean();
    settings.coordinates.background = random.nextBoolean();
    settings.ping.enabled = random.nextBoolean();
    settings.ping.colored = random.nextBoolean();
    settings.ping.serverIp = random.nextBoolean();
    settings.ping.background = random.nextBoolean();
  }

  private static final class Data extends SkinViewTest.Actions {
    final List<Cooldown> cooldowns = new ArrayList<Cooldown>();
    final List<InventorySlot> inventory = new ArrayList<InventorySlot>();
    final List<InventorySlot> armor = new ArrayList<InventorySlot>();
    final List<TntView> tnts = new ArrayList<TntView>();
    final List<PotionView> potions = new ArrayList<PotionView>();
    TargetView target;
    VitalsView vitals = VitalsView.EMPTY;
    int ping;
    Map<String, Object> network = Collections.emptyMap();
    Map<String, Object> keystrokes = Collections.emptyMap();
    Map<String, Object> coordinates = Collections.emptyMap();
    Map<String, Object> scoreboard = Collections.emptyMap();
    String gameVersion = "26.3";

    @Override
    public List<Cooldown> cooldowns() {
      return cooldowns;
    }
    @Override
    public List<InventorySlot> inventory() {
      return inventory;
    }
    @Override
    public List<InventorySlot> armor() {
      return armor;
    }
    @Override
    public List<TntView> tnts() {
      return tnts;
    }
    @Override
    public List<PotionView> potions() {
      return potions;
    }
    @Override
    public TargetView target() {
      return target;
    }
    @Override
    public VitalsView vitals() {
      return vitals;
    }
    @Override
    public int ping() {
      return ping;
    }
    @Override
    public Map<String, Object> networkInfo() {
      return network;
    }
    @Override
    public Map<String, Object> keystrokes() {
      return keystrokes;
    }
    @Override
    public Map<String, Object> coordinates() {
      return coordinates;
    }
    @Override
    public Map<String, Object> scoreboard() {
      return scoreboard;
    }
    @Override
    public String gameVersion() {
      return gameVersion;
    }
  }

  private static final class StressDigestCanvas implements Canvas {
    private final Object metrics;
    private final MessageDigest digest;

    StressDigestCanvas() {
      this(METRICS);
    }

    StressDigestCanvas(Object metrics) {
      this.metrics = metrics;
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException failure) {
        throw new AssertionError(failure);
      }
    }

    private void tag(int value) {
      digest.update((byte) value);
    }
    private void integer(int value) {
      digest.update((byte) (value >>> 24));
      digest.update((byte) (value >>> 16));
      digest.update((byte) (value >>> 8));
      digest.update((byte) value);
    }
    private void number(float value) {
      integer(Float.floatToIntBits(value));
    }
    private void text(String value) {
      byte[] bytes = (value == null ? "<null>" : value).getBytes(StandardCharsets.UTF_8);
      integer(bytes.length);
      digest.update(bytes);
    }
    private void rectCommand(int tag, float x, float y, float w, float h, int color) {
      tag(tag);
      number(x);
      number(y);
      number(w);
      number(h);
      integer(color);
    }
    String digest() {
      byte[] bytes = digest.digest();
      StringBuilder out = new StringBuilder();
      for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255));
      return out.toString();
    }

    @Override
    public Object textMetricsKey() {
      return metrics;
    }
    @Override
    public void rect(float x, float y, float w, float h, int color) {
      rectCommand(1, x, y, w, h, color);
    }
    @Override
    public void invertRect(float x, float y, float w, float h, float alpha) {
      tag(2);
      number(x);
      number(y);
      number(w);
      number(h);
      number(alpha);
    }
    @Override
    public void roundedRect(float x, float y, float w, float h, float radius, int color) {
      tag(3);
      number(x);
      number(y);
      number(w);
      number(h);
      number(radius);
      integer(color);
    }
    @Override
    public void roundedRectCorners(
        float x, float y, float w, float h, float tl, float tr, float bl, float br, int color) {
      tag(4);
      number(x);
      number(y);
      number(w);
      number(h);
      number(tl);
      number(tr);
      number(bl);
      number(br);
      integer(color);
    }
    @Override
    public void strokeRoundedRect(
        float x, float y, float w, float h, float radius, float thickness, int color) {
      tag(5);
      number(x);
      number(y);
      number(w);
      number(h);
      number(radius);
      number(thickness);
      integer(color);
    }
    @Override
    public void strokeRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
        float bl, float br, float thickness, int color) {
      tag(6);
      number(x);
      number(y);
      number(w);
      number(h);
      number(tl);
      number(tr);
      number(bl);
      number(br);
      number(thickness);
      integer(color);
    }
    @Override
    public void gradientRect(float x, float y, float w, float h, int top, int bottom) {
      tag(7);
      number(x);
      number(y);
      number(w);
      number(h);
      integer(top);
      integer(bottom);
    }
    @Override
    public void gradientRoundedRect(
        float x, float y, float w, float h, float radius, int left, int right) {
      tag(8);
      number(x);
      number(y);
      number(w);
      number(h);
      number(radius);
      integer(left);
      integer(right);
    }
    @Override
    public void gradientRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
        float bl, float br, int ctl, int ctr, int cbl, int cbr) {
      tag(9);
      number(x);
      number(y);
      number(w);
      number(h);
      number(tl);
      number(tr);
      number(bl);
      number(br);
      integer(ctl);
      integer(ctr);
      integer(cbl);
      integer(cbr);
    }
    @Override
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
      tag(10);
      number(x1);
      number(y1);
      number(x2);
      number(y2);
      number(thickness);
      integer(color);
    }
    @Override
    public void circle(float x, float y, float radius, int color) {
      tag(11);
      number(x);
      number(y);
      number(radius);
      integer(color);
    }
    @Override
    public void fillTriangle(
        float ax, float ay, float bx, float by, float cx, float cy, int color) {
      tag(12);
      number(ax);
      number(ay);
      number(bx);
      number(by);
      number(cx);
      number(cy);
      integer(color);
    }
    @Override
    public void text(String value, float x, float y, float size, int color, boolean bold) {
      tag(13);
      text(value);
      number(x);
      number(y);
      number(size);
      integer(color);
      integer(bold ? 1 : 0);
    }
    @Override
    public void textTracked(
        String value, float x, float y, float size, int color, boolean bold, float tracking) {
      tag(14);
      text(value);
      number(x);
      number(y);
      number(size);
      integer(color);
      integer(bold ? 1 : 0);
      number(tracking);
    }
    @Override
    public void textFamily(String family, String value, float x, float y, float size, int color,
        boolean bold, float tracking) {
      tag(15);
      text(family);
      text(value);
      number(x);
      number(y);
      number(size);
      integer(color);
      integer(bold ? 1 : 0);
      number(tracking);
    }
    @Override
    public void gameText(String value, float x, float y, float scale, int color, boolean shadow) {
      tag(16);
      text(value);
      number(x);
      number(y);
      number(scale);
      integer(color);
      integer(shadow ? 1 : 0);
    }
    @Override
    public float textWidth(String value, float size, boolean bold) {
      return FontAtlas.get().width(value, size, bold);
    }
    @Override
    public float textWidthTracked(String value, float size, boolean bold, float tracking) {
      return FontAtlas.get().width(value, size, bold, tracking);
    }
    @Override
    public float textWidthFamily(
        String family, String value, float size, boolean bold, float tracking) {
      return FontAtlas.get(family).width(value, size, bold, tracking);
    }
    @Override
    public float gameTextWidth(String value, float scale) {
      return FontAtlas.get().width(value, 9 * scale, false);
    }
    @Override
    public void pushClip(float x, float y, float w, float h) {
      tag(17);
      number(x);
      number(y);
      number(w);
      number(h);
    }
    @Override
    public void popClip() {
      tag(18);
    }
    @Override
    public void item(String id, float x, float y, float size, float alpha) {
      tag(19);
      text(id);
      number(x);
      number(y);
      number(size);
      number(alpha);
    }
    @Override
    public void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
      tag(20);
      integer(slot == null ? 0 : slot.slot);
      text(slot == null ? null : slot.itemId);
      text(slot == null ? null : slot.name);
      integer(slot == null ? 0 : slot.count);
      integer(slot == null ? 0 : slot.damage);
      integer(slot == null ? 0 : slot.maxDamage);
      number(x);
      number(y);
      number(size);
      number(alpha);
    }
    @Override
    public boolean entityHead(TargetView value, float x, float y, float size, float alpha) {
      tag(21);
      integer(value == null ? 0 : value.entityId);
      text(value == null ? null : value.name);
      number(value == null ? 0 : value.health);
      number(x);
      number(y);
      number(size);
      number(alpha);
      return false;
    }
    @Override
    public boolean vanillaCrosshair(float x, float y, float size, float opacity) {
      tag(22);
      number(x);
      number(y);
      number(size);
      number(opacity);
      return false;
    }
    @Override
    public void viewmodelOutline(String hand, int color) {
      tag(23);
      text(hand);
      integer(color);
    }
    @Override
    public void hungerIcon(float x, float y, float size, int fill) {
      tag(24);
      number(x);
      number(y);
      number(size);
      integer(fill);
    }
    @Override
    public void backdrop(
        float x, float y, float w, float h, float radius, float blur, int quality, float opacity) {
      tag(25);
      number(x);
      number(y);
      number(w);
      number(h);
      number(radius);
      number(blur);
      integer(quality);
      number(opacity);
    }
    @Override
    public void backdropCorners(float x, float y, float w, float h, float tl, float tr, float bl,
        float br, float blur, int quality, float opacity) {
      tag(26);
      number(x);
      number(y);
      number(w);
      number(h);
      number(tl);
      number(tr);
      number(bl);
      number(br);
      number(blur);
      integer(quality);
      number(opacity);
    }
    @Override
    public void texture(
        String id, float x, float y, float w, float h, float radius, float tile, int color) {
      tag(27);
      text(id);
      number(x);
      number(y);
      number(w);
      number(h);
      number(radius);
      number(tile);
      integer(color);
    }
    @Override
    public void gameSprite(String id, float x, float y, float w, float h, float u, float v,
        float rw, float rh, float sw, float sh, int color) {
      tag(28);
      text(id);
      number(x);
      number(y);
      number(w);
      number(h);
      number(u);
      number(v);
      number(rw);
      number(rh);
      number(sw);
      number(sh);
      integer(color);
    }
  }
}
