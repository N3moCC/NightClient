package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TargetView;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudFrameCacheTest {
  private static final Object METRICS = new Object();

  @Test
  void staticHudReplaysIdenticalCommands() throws Exception {
    Settings settings = settings();
    SkinView view =
        new SkinView(document("hud { panel { width=200px; height=100px; text 'Hello' } }"),
            settings, new Data());
    DigestCanvas first = new DigestCanvas(), second = new DigestCanvas();
    assertTrue(view.renderHud(first, 800, 600, 1, null, null, null));
    assertEquals(0, view.hudFrameCacheHits(), "first frame must record, not hit");
    assertTrue(view.renderHud(second, 800, 600, 2, null, null, null));
    assertEquals(1, view.hudFrameCacheHits(), "identical static frame should hit");
    assertEquals(first.digest(), second.digest());
  }

  @Test
  void dataSettingsThemeLanguageResizeAndMetricsInvalidate() throws Exception {
    Settings settings = settings();
    Data data = new Data();
    SkinView view = new SkinView(document("hud { panel { width=200px; height=100px; text "
                                          + "'{{hud.width}}'; repeat(source=cooldowns,as=timer) { "
                                          + "text '{{timer.name}}' } text '{{target.name}}' } }"),
        settings, data);
    SkinView reference = new SkinView(view.document(), settings, data);
    for (int frame = 0; frame < 8; frame++) {
      double time = frame / 20.0;
      if (frame == 1) data.cooldowns.add(new Cooldown("a", "Alpha", "minecraft:stone", 10, 20));
      if (frame == 2) settings.language = "ru";
      if (frame == 3) settings.theme.accent = 0xff336699;
      if (frame == 4) settings.hud.width = 140;
      if (frame == 5) data.target = new TargetView(1, "uuid", "Mob", 10, 20, 0, 3, false);
      if (frame == 6) settings.target.enabled = !settings.target.enabled;
      DigestCanvas cached = new DigestCanvas(), plain = new DigestCanvas();
      settings.optimization.enabled = true;
      view.renderHud(
          cached, frame == 7 ? 900 : 800, 600, time, data.cooldowns, data.inventory, data.tnts);
      settings.optimization.enabled = false;
      reference.invalidateLayout();
      reference.renderHud(
          plain, frame == 7 ? 900 : 800, 600, time, data.cooldowns, data.inventory, data.tnts);
      assertEquals(plain.digest(), cached.digest(), "cache diverged at frame " + frame);
    }
    assertTrue(view.hudFrameCacheHits() > 0, "uniform frames should have hit");
    int hits = view.hudFrameCacheHits();
    DigestCanvas changedMetrics = new DigestCanvas() {
      @Override
      public Object textMetricsKey() {
        return new Object();
      }
    };
    settings.optimization.enabled = true;
    view.renderHud(changedMetrics, 800, 600, 8, data.cooldowns, data.inventory, data.tnts);
    assertEquals(
        hits, view.hudFrameCacheHits(), "a changed metrics key must not use the old display list");
  }

  @Test
  void animatedContentBypassesTheCache() throws Exception {
    Settings settings = new Settings();
    settings.optimization.enabled = true;
    settings.menu.animationSpeed = 1f;
    SkinView view = new SkinView(
        document("hud { panel { width=200px; height=100px; text 'Fade' } }"), settings, new Data());
    for (int frame = 0; frame < 6; frame++)
      view.renderHud(new DigestCanvas(), 800, 600, frame / 60.0, null, null, null);
    assertEquals(0, view.hudFrameCacheHits(), "entrance animation must be rendered live");
    view.renderHud(new DigestCanvas(), 800, 600, 2, null, null, null);
    view.renderHud(new DigestCanvas(), 800, 600, 3, null, null, null);
    assertTrue(view.hudFrameCacheHits() > 0, "settled frame should become cacheable");
  }

  @Test
  void marqueeBypassesTheCache() throws Exception {
    Settings settings = settings();
    settings.menu.animationSpeed = 1f;
    SkinView view = new SkinView(
        document(
            "hud { text 'A very long scrolling label' { width=20px; text-overflow=marquee } }"),
        settings, new Data());
    for (int frame = 0; frame < 6; frame++)
      view.renderHud(new DigestCanvas(), 800, 600, frame / 60.0, null, null, null);
    assertEquals(0, view.hudFrameCacheHits());
  }

  @Test
  void disabledAndHudCacheOffPathsNeverHit() throws Exception {
    Settings settings = settings();
    SkinView view = new SkinView(document("hud { text 'static' }"), settings, new Data());
    settings.optimization.enabled = false;
    for (int frame = 0; frame < 3; frame++)
      view.renderHud(new DigestCanvas(), 800, 600, frame, null, null, null);
    assertEquals(0, view.hudFrameCacheHits());
    settings.optimization.enabled = true;
    settings.optimization.hudCache = false;
    for (int frame = 0; frame < 3; frame++)
      view.renderHud(new DigestCanvas(), 800, 600, frame, null, null, null);
    assertEquals(0, view.hudFrameCacheHits());
  }

  @Test
  void pingOnlyChangeInvalidates() throws Exception {
    Settings settings = settings();
    Data data = new Data();
    data.ping = 20;
    SkinView view = new SkinView(document("hud { text '{{pingData.pingText}}' }"), settings, data);
    DigestCanvas first = new DigestCanvas();
    assertTrue(view.renderHud(first, 800, 600, 10, data.cooldowns, data.inventory, data.tnts));
    DigestCanvas second = new DigestCanvas();
    assertTrue(view.renderHud(second, 800, 600, 10, data.cooldowns, data.inventory, data.tnts));
    int hits = view.hudFrameCacheHits();
    assertTrue(hits > 0, "unchanged ping should hit");
    data.ping = 250;
    DigestCanvas changed = new DigestCanvas();
    assertTrue(view.renderHud(changed, 800, 600, 10, data.cooldowns, data.inventory, data.tnts));
    assertEquals(hits, view.hudFrameCacheHits(), "a ping-only change must miss the cache");
    assertNotEquals(second.digest(), changed.digest(), "payload should reflect the new ping");
  }

  @Test
  void inPlaceCooldownAndPingChangesInvalidate() throws Exception {
    Settings settings = settings();
    Data data = new Data();
    Cooldown cooldown = new Cooldown("pearl", "Pearl", "minecraft:ender_pearl", 10, 20);
    data.cooldowns.add(cooldown);
    SkinView view = new SkinView(
        document(
            "hud { text '{{= pingText }}' { x=0px } repeat(source=cooldownRows,as=timer) { text "
            + "'{{timer.name}} {{timer.remainingSeconds}}' { x=0px; y='{{= index*14 }}' } } }"),
        settings, data);
    SkinView reference = new SkinView(view.document(), settings, data);
    renderPair(view, reference, settings, data, 800, 600, 10);
    renderPair(view, reference, settings, data, 800, 600, 11);
    int hits = view.hudFrameCacheHits();
    assertTrue(hits > 0, "identical static frames should hit before the mutation");
    java.lang.reflect.Field seconds = Cooldown.class.getDeclaredField("remainingSeconds");
    java.lang.reflect.Field fraction = Cooldown.class.getDeclaredField("remainingFraction");
    seconds.setAccessible(true);
    fraction.setAccessible(true);
    seconds.setFloat(cooldown, 2f);
    fraction.setFloat(cooldown, 0.1f);
    data.ping = 250;
    DigestCanvas cached = new DigestCanvas(), plain = new DigestCanvas();
    settings.optimization.enabled = true;
    view.renderHud(cached, 800, 600, 12, data.cooldowns, data.inventory, data.tnts);
    settings.optimization.enabled = false;
    reference.invalidateLayout();
    reference.renderHud(plain, 800, 600, 12, data.cooldowns, data.inventory, data.tnts);
    assertEquals(plain.digest(), cached.digest(), "in-place data change must be rendered live");
    assertEquals(
        hits, view.hudFrameCacheHits(), "mutated cooldown/ping must miss the old display list");
  }

  @Test
  void retainedRowExitAnimationDoesNotFreezeTheCache() throws Exception {
    Settings settings = settings();
    settings.menu.animationSpeed = 1f;
    Data data = new Data();
    data.cooldowns.add(new Cooldown("pearl", "Pearl", "minecraft:ender_pearl", 2, 10));
    SkinView view =
        new SkinView(document("hud { repeat(source=cooldownRows,as=timer) { retainExit=true; "
                              + "exitDuration=160ms; text '{{timer.name}}' { width=200px } } }"),
            settings, data);
    SkinView reference = new SkinView(view.document(), settings, data);
    for (int frame = 0; frame < 6; frame++)
      renderPair(view, reference, settings, data, 800, 600, 10 + frame / 60.0);
    int hits = view.hudFrameCacheHits();
    data.cooldowns.clear();
    for (int frame = 0; frame < 30; frame++)
      renderPair(view, reference, settings, data, 800, 600, 20 + frame / 60.0);
    assertTrue(
        view.hudFrameCacheHits() > hits, "the cache must recover after the retained row expires");
  }

  @Test
  void bundledThemeHudMatchesReferenceAtDifferentTimes() throws Exception {
    Settings settings = new Settings();
    settings.optimization.enabled = true;
    settings.menu.animationSpeed = 1f;
    Data data = new Data();
    data.cooldowns.add(new Cooldown("pearl", "Pearl", "minecraft:ender_pearl", 7.2f, 15));
    data.target = new TargetView(3, "qa", "Mob", 14, 20, 2, 3, true);
    data.vitals = new VitalsView(16, 4.5f, true);
    for (String preset : DefaultSkin.presetIds()) {
      SkinDocument doc = DefaultSkin.document(preset);
      SkinView cachedView = new SkinView(doc, settings, data);
      SkinView referenceView = new SkinView(doc, settings, data);
      for (double time : new double[] {10, 20, 35}) {
        DigestCanvas cached = new DigestCanvas(), plain = new DigestCanvas();
        settings.optimization.enabled = true;
        cachedView.renderHud(cached, 1000, 700, time, data.cooldowns, data.inventory, data.tnts);
        settings.optimization.enabled = false;
        referenceView.invalidateLayout();
        referenceView.renderHud(plain, 1000, 700, time, data.cooldowns, data.inventory, data.tnts);
        assertEquals(
            plain.digest(), cached.digest(), "bundled theme " + preset + " froze time at " + time);
      }
      assertTrue(cachedView.hudFrameCacheHits() > 0,
          "bundled theme " + preset + " should reach the static fast path");
    }
  }

  @Test
  void saveStatusChangeInvalidatesTheDisplayList() throws Exception {
    Settings settings = settings();
    Data data = new Data();
    SkinView view = new SkinView(
        document("hud { text '{{saveStatus}}' { width=120px; height=10px } }"), settings, data);
    assertTrue(view.renderHud(new DigestCanvas(), 800, 600, 1, null, null, null));
    DigestCanvas cached = new DigestCanvas();
    assertTrue(view.renderHud(cached, 800, 600, 2, null, null, null));
    int hits = view.hudFrameCacheHits();
    assertTrue(hits > 0, "unchanged save status should hit");
    data.saveStatus = "saving...";
    DigestCanvas changed = new DigestCanvas();
    assertTrue(view.renderHud(changed, 800, 600, 3, null, null, null));
    assertEquals(
        hits, view.hudFrameCacheHits(), "save status is paint-visible and must invalidate");
    assertNotEquals(cached.digest(), changed.digest());
  }

  @Test
  void explicitRenderListsAreHashedInsteadOfActionGetters() throws Exception {
    Settings settings = settings();
    Data data = new Data();
    SkinView view = new SkinView(document("hud { repeat(source=cooldownRows,as=timer) { text "
                                          + "'{{timer.name}}' { width=120px } } }"),
        settings, data);
    List<Cooldown> painted = new ArrayList<Cooldown>();
    painted.add(new Cooldown("pearl", "Pearl", "minecraft:ender_pearl", 6, 10));
    assertTrue(view.renderHud(new DigestCanvas(), 800, 600, 1, painted, data.inventory, data.tnts));
    DigestCanvas cached = new DigestCanvas();
    assertTrue(view.renderHud(cached, 800, 600, 2, painted, data.inventory, data.tnts));
    int hits = view.hudFrameCacheHits();
    assertTrue(hits > 0, "the explicit static list should hit");
    painted.add(new Cooldown("gap", "Gap", "minecraft:stone", 4, 10));
    DigestCanvas changed = new DigestCanvas();
    assertTrue(view.renderHud(changed, 800, 600, 3, painted, data.inventory, data.tnts));
    assertEquals(hits, view.hudFrameCacheHits(), "mutating the painted list must invalidate");
    assertNotEquals(cached.digest(), changed.digest());
  }

  @Test
  void signatureAcceptsEveryRealHudDataShape() throws Exception {
    Settings settings = settings();
    Data data = new Data();
    data.cooldowns.add(new Cooldown("a", "A", "minecraft:stone", 2, 10));
    data.inventory.add(new InventorySlot(9, "minecraft:stone", "Stone", 4, 0, 0));
    data.target = new TargetView(5, "uuid", "Mob", 8, 20, 1, 2, false);
    data.vitals = new VitalsView(12, 3, true);
    data.ping = 42;
    data.network = Collections.<String, Object>singletonMap("available", Boolean.TRUE);
    long signature = HudFrameCache.signature(settings, data, data.cooldowns, data.inventory,
        data.tnts, 900, 600, true, new java.util.HashMap<String, Object>(), 0, 0, "hud", "", "", "",
        "", "", "", "", false, 0);
    assertNotEquals(0L, signature, "real HUD data shapes must remain hashable");
  }

  private static void renderPair(SkinView view, SkinView reference, Settings settings, Data data,
      int width, int height, double time) {
    DigestCanvas cached = new DigestCanvas(), plain = new DigestCanvas();
    settings.optimization.enabled = true;
    view.renderHud(cached, width, height, time, data.cooldowns, data.inventory, data.tnts);
    settings.optimization.enabled = false;
    reference.invalidateLayout();
    reference.renderHud(plain, width, height, time, data.cooldowns, data.inventory, data.tnts);
    assertEquals(plain.digest(), cached.digest(), "cache diverged at time " + time);
  }

  private static Settings settings() {
    Settings settings = new Settings();
    settings.optimization.enabled = true;
    settings.menu.animationSpeed = 1000000f;
    return settings;
  }

  private static SkinDocument document(String hud) throws Exception {
    return NcParser.parse(hud);
  }

  private static final class Data extends SkinViewTest.Actions {
    final List<Cooldown> cooldowns = new ArrayList<Cooldown>();
    final List<InventorySlot> inventory = new ArrayList<InventorySlot>();
    final List<dev.nightclient.api.TntView> tnts = new ArrayList<dev.nightclient.api.TntView>();
    TargetView target;
    VitalsView vitals = VitalsView.EMPTY;
    int ping;
    String saveStatus = "saved";
    Map<String, Object> network = Collections.emptyMap();
    @Override
    public String saveStatus() {
      return saveStatus;
    }
    @Override
    public List<Cooldown> cooldowns() {
      return cooldowns;
    }
    @Override
    public List<InventorySlot> inventory() {
      return inventory;
    }
    @Override
    public List<dev.nightclient.api.TntView> tnts() {
      return tnts;
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
  }

  private static class DigestCanvas implements Canvas {
    private final MessageDigest digest;
    DigestCanvas() {
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
      return METRICS;
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
