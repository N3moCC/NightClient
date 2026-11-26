package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.FontAtlas;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class HudBuildCacheParityTest {
  private static final Object METRICS = new Object();
  private static final float WIDTH = 1000, HEIGHT = 700;

  @Test
  void cachedHudBuildsMatchUncachedBuildsAcrossLiveDataAndPresentationChanges() throws Exception {
    Settings settings = new Settings();
    settings.inventory.enabled = settings.armor.enabled = settings.tnt.enabled =
        settings.potions.enabled = settings.target.enabled = true;
    SkinDocument document = DefaultSkin.document();
    settings.skinDocument = document;
    Data data = new Data();
    SkinView cached = new SkinView(document, settings, data),
             reference = new SkinView(document, settings, data);
    float width = WIDTH, height = HEIGHT;
    for (int frame = 0; frame < 90; frame++) {
      double time = frame / 60.0;
      data.advance(time);
      if (frame == 18) data.clearCooldowns();
      if (frame == 30) data.seedCooldowns();
      if (frame == 36) data.target = null;
      if (frame == 46) data.seedTarget();
      if (frame == 52) data.clearPotions();
      if (frame == 62) data.seedPotions();
      if (frame == 68) data.clearTnts();
      if (frame == 76) data.seedTnts();
      if (frame == 10) settings.language = "en";
      if (frame == 22) settings.theme.accent = 0xff2f6f4f;
      if (frame == 34) settings.menu.animationSpeed = 1000000f;
      if (frame == 42) {
        settings.menu.animationSpeed = 1f;
        settings.menu.animationSpeed = .55f;
      }
      if (frame == 50) {
        width = 900;
        height = 620;
      }
      if (frame == 58) {
        settings.language = "ru";
        settings.theme.backgroundOpacity = .81f;
      }
      if (frame == 60) settings.tnt.range = 2;
      if (frame == 62) settings.tnt.range = 64;
      if (frame == 66) settings.theme.fontFamily = "studio-sans";
      if (frame == 74) {
        width = WIDTH;
        height = HEIGHT;
        settings.menu.animationSpeed = 1.4f;
      }
      if (frame == 82) {
        settings.theme.radius = 7f;
        settings.menu.animationSpeed = 1000000f;
      }
      if (frame == 88) {
        settings.menu.animationSpeed = 1f;
        settings.theme.fontFamily = "onest";
      }
      DigestCanvas before = new DigestCanvas(), after = new DigestCanvas();
      reference.invalidateLayout();
      assertTrue(cached.renderHud(before, Math.round(width), Math.round(height), time,
          data.cooldowns(), data.inventory(), data.tnts()));
      assertTrue(reference.renderHud(after, Math.round(width), Math.round(height), time,
          data.cooldowns(), data.inventory(), data.tnts()));
      assertEquals(before.digest(), after.digest(), "draw commands diverged at frame " + frame);
      assertEquals(cached.hudExitDuration(), reference.hudExitDuration(),
          "exit timing diverged at frame " + frame);
    }
  }

  private static final class Data extends SkinViewTest.Actions {
    final List<Cooldown> cooldowns = new ArrayList<Cooldown>();
    final List<InventorySlot> inventory = new ArrayList<InventorySlot>();
    final List<InventorySlot> armor = new ArrayList<InventorySlot>();
    final List<TntView> tnts = new ArrayList<TntView>();
    final List<PotionView> potions = new ArrayList<PotionView>();
    TargetView target;
    private int lastTick = Integer.MIN_VALUE;
    Data() {
      seedCooldowns();
      seedTnts();
      seedPotions();
      seedInventory();
      seedTarget();
    }
    void seedCooldowns() {
      cooldowns.add(new Cooldown("pearl", "Pearl", "minecraft:ender_pearl", 7.2f, 15));
      cooldowns.add(new Cooldown("apple", "Golden apple", "minecraft:golden_apple", 19, 30));
    }
    void clearCooldowns() {
      cooldowns.clear();
    }
    void seedTnts() {
      tnts.add(new TntView(1, 0, 0, 2.3f, 4, false));
      tnts.add(new TntView(2, 0, 0, 3.6f, 7, false));
    }
    void clearTnts() {
      tnts.clear();
    }
    void seedPotions() {
      potions.add(new PotionView("minecraft:speed", "Speed", 1, 0x7cafc6, 82, true));
      potions.add(new PotionView("minecraft:strength", "Strength", 0, 0x932423, 9, true));
    }
    void clearPotions() {
      potions.clear();
    }
    void seedInventory() {
      for (int slot = 9; slot < 36; slot++)
        inventory.add(new InventorySlot(slot, "minecraft:ender_pearl", "Pearl", 16, 0, 0, false));
      for (int slot = 0; slot < 4; slot++)
        armor.add(
            new InventorySlot(100 + slot, "minecraft:diamond_boots", "Boots", 1, 10, 100, true));
    }
    void seedTarget() {
      target = new TargetView(3, "qa-player", "Opponent", 14, 20, 2, 3, true, "", armor);
    }
    void advance(double seconds) {
      int tick = (int) Math.floor(seconds * 20);
      if (tick == lastTick) return;
      lastTick = tick;
      for (int i = 0; i < cooldowns.size(); i++) {
        Cooldown value = cooldowns.get(i);
        cooldowns.set(i,
            new Cooldown(value.key, value.name, value.itemId, 1 + ((tick * 7 + i * 3) % 50) / 4f,
                value.durationSeconds));
      }
      for (int i = 0; i < potions.size(); i++) {
        PotionView value = potions.get(i);
        potions.set(i,
            new PotionView(value.id, value.name, value.amplifier, value.color,
                5 + ((tick * 5 + i) % 60) / 3f, value.beneficial));
      }
      for (int i = 0; i < tnts.size(); i++) {
        TntView value = tnts.get(i);
        tnts.set(i,
            new TntView(value.id, value.screenX, value.screenY, 1 + ((tick * 3 + i) % 40) / 10f,
                2 + ((tick + i) % 30) / 5f, false));
      }
      for (int i = 0; i < inventory.size(); i++) {
        InventorySlot value = inventory.get(i);
        inventory.set(i,
            new InventorySlot(value.slot,
                tick % 24 == 0 && i == 0 ? "minecraft:golden_apple" : value.itemId, "Item",
                1 + (tick + i) % 64, 0, 0, tick % 24 == 0));
      }
      for (int i = 0; i < armor.size(); i++) {
        InventorySlot value = armor.get(i);
        armor.set(i,
            new InventorySlot(
                value.slot, value.itemId, "Boots", 1, (tick + i * 11) % 100, 100, true));
      }
      if (target != null)
        target = new TargetView(3, "qa-player", tick % 16 < 8 ? "Opponent" : "Opponent II",
            4 + (tick % 16) / 2f, 20, 2, 2 + (tick % 20) * .1f, true, "", armor);
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
  }

  private static final class DigestCanvas implements Canvas {
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
