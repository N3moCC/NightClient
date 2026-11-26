package dev.nightclient.renderer.map;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.MapRadarModels;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MapEntityIconPainter {
  private static final int SPRITE_SIZE = 16;
  private static final int OUTLINE_COL = 0xE80A0C12;
  private static final Map<String, int[]> SPRITE_CACHE = new HashMap<String, int[]>();

  private MapEntityIconPainter() {}

  public static void drawEntity(Canvas canvas, MapRadarModels.MobEntry m, float sx, float sy,
      float scale, float alpha, boolean iconStyle, boolean showName, double playerY,
      double timeSeconds) {
    if (canvas == null || m == null || alpha <= 0.01f) return;
    boolean explosive =
        m.kind == MapRadarModels.MOB_TNT || m.kind == MapRadarModels.MOB_CRYSTAL;
    boolean boss = m.kind == MapRadarModels.MOB_BOSS;
    float pulse = 0.72f + 0.28f * (float) Math.sin(timeSeconds * 7.5);

    if (!iconStyle && !explosive) {
      int col = categoryColor(m.kind);
      float r = (boss ? 3.4f : (m.kind == MapRadarModels.MOB_OTHER ? 1.85f : 2.25f)) * scale;
      if (boss) {
        canvas.circle(sx, sy, r + 2.0f * scale * pulse, Colors.alpha(col, alpha * 0.35f));
      }
      canvas.circle(sx, sy, r + 0.75f * scale, Colors.alpha(0xDC080B12, alpha));
      canvas.circle(sx, sy, r, Colors.alpha(col, alpha));
      return;
    }

    String id = normalizeType(m.entityType);
    String spriteKey = resolveSpriteKey(id, m.kind);
    boolean hasComposed = m.composedPixels != null && m.composedSize > 0
        && m.composedPixels.length == m.composedSize * m.composedSize;
    boolean xpOrb = "experience_orb".equals(id);
    boolean compactProjectile = "arrow".equals(id) || "spectral_arrow".equals(id)
        || "snowball".equals(id) || "egg".equals(id) || "fishing_bobber".equals(id)
        || "llama_spit".equals(id) || "shulker_bullet".equals(id);
    float baseSize;
    if (boss) {
      baseSize = hasComposed ? 18.0f : 15.0f;
    } else if (explosive) {
      baseSize = 13.0f;
    } else if (xpOrb) {
      baseSize = hasComposed ? 12.0f : 9.0f;
    } else if (compactProjectile) {
      baseSize = hasComposed ? 13.0f : 10.5f;
    } else if (hasComposed) {
      baseSize = 15.5f;
    } else {
      baseSize = 11.5f;
    }
    if (m.baby) baseSize *= 0.80f;

    float iconAlpha = alpha;
    if (!boss && !explosive && Math.abs(m.y - playerY) > 14.0) {
      iconAlpha *= 0.72f;
    }

    float size = Math.max(8.0f, baseSize * scale);
    float x = sx - size * 0.5f;
    float y = sy - size * 0.5f;

    if (explosive) {
      int aura = m.kind == MapRadarModels.MOB_TNT ? 0xFFF97316 : 0xFFE879F9;
      canvas.circle(sx, sy, size * 0.58f + 1.8f * scale * pulse, Colors.alpha(aura, alpha * 0.34f));
    } else if (boss) {
      canvas.circle(sx, sy, size * 0.50f + 1.8f * scale * pulse,
          Colors.alpha(0xFFE879F9, alpha * 0.30f));
    }

    boolean drawnAuthentic = false;

    if (m.itemIconId != null && !m.itemIconId.isEmpty()) {
      canvas.circle(sx, sy + size * 0.08f, size * 0.38f, Colors.alpha(0x3D000000, iconAlpha));
      canvas.item(m.itemIconId, x, y, size, iconAlpha);
      drawnAuthentic = true;
    }

    if (!drawnAuthentic && hasComposed) {
      canvas.circle(sx, sy + size * 0.08f, size * 0.32f, Colors.alpha(0x38000000, iconAlpha));
      int tint = (Math.round(Math.max(0f, Math.min(1f, iconAlpha)) * 255f) << 24)
          | (m.tintRgb & 0xFFFFFF);
      String key = m.composedKey.isEmpty() ? ("ent24_" + spriteKey) : m.composedKey;
      long rev = m.composedRevision > 0L ? m.composedRevision : 1L;
      boolean useNearest = size >= m.composedSize;
      canvas.mapQuad(key, rev, m.composedPixels, m.composedSize, m.composedSize, x, y, size, size,
          0f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, useNearest, tint);
      drawnAuthentic = true;
    }

    if (!drawnAuthentic && m.textureId != null && !m.textureId.isEmpty()
        && m.u1 > m.u0 && m.v1 > m.v0) {
      if (xpOrb) {
        canvas.circle(sx, sy, size * 0.45f, Colors.alpha(0x66081208, iconAlpha));
        boolean ok = canvas.textureRegion(m.textureId, x, y, size, size, 0f,
            m.u0, m.v0, m.u1, m.v1, iconAlpha, m.tintRgb);
        if (ok) drawnAuthentic = true;
      } else {
        float framePad = boss ? 1.25f : 1.0f;
        float radius = Math.max(1.0f, Math.min(2.0f, size * 0.14f));
        canvas.roundedRect(x - framePad, y - framePad, size + framePad * 2f, size + framePad * 2f,
            radius + 0.4f, Colors.alpha(0xEE090D16, iconAlpha));
        boolean ok = canvas.textureRegion(m.textureId, x, y, size, size, 0f,
            m.u0, m.v0, m.u1, m.v1, iconAlpha, m.tintRgb);
        if (ok) {
          if (m.overlays != null) {
            for (int i = 0; i < m.overlays.length; i++) {
              MapRadarModels.OverlayLayer ov = m.overlays[i];
              if (ov == null || ov.textureId.isEmpty() || ov.u1 <= ov.u0 || ov.v1 <= ov.v0) {
                continue;
              }
              float ox = x + ov.dx0 * size;
              float oy = y + ov.dy0 * size;
              float ow = (ov.dx1 - ov.dx0) * size;
              float oh = (ov.dy1 - ov.dy0) * size;
              if (ow <= 0.1f || oh <= 0.1f) continue;
              canvas.textureRegion(ov.textureId, ox, oy, ow, oh, 0f,
                  ov.u0, ov.v0, ov.u1, ov.v1, iconAlpha, ov.tintRgb);
            }
          }
          canvas.strokeRoundedRect(x - framePad, y - framePad, size + framePad * 2f,
              size + framePad * 2f, radius + 0.4f, boss ? 0.95f : 0.65f,
              Colors.alpha(categoryBorderColor(m.kind), iconAlpha));
          drawnAuthentic = true;
        }
      }
    }

    if (!drawnAuthentic) {
      canvas.roundedRect(x + 1.0f, y + 1.5f, size - 2.0f, size - 1.5f,
          Math.max(1.5f, size * 0.16f), Colors.alpha(0x55000000, iconAlpha));
      int[] pixels = getOrBuildSprite(spriteKey, id, m.kind);
      int tint = (Math.round(Math.max(0f, Math.min(1f, iconAlpha)) * 255f) << 24) | 0xFFFFFF;
      canvas.mapQuad("mob16_" + spriteKey, 2L, pixels, SPRITE_SIZE, SPRITE_SIZE, x, y, size, size,
          0f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, true, tint);
    }

    if ((showName || boss) && !explosive && m.name != null && !m.name.isEmpty()) {
      float nfs = 4.8f * Math.max(0.8f, Math.min(1.25f, scale));
      String label = m.name.length() > 14 ? m.name.substring(0, 14) : m.name;
      float nw = canvas.textWidthFamily("studio-sans-medium", label, nfs, false, 0f);
      float pw = nw + 3.6f * scale;
      float ph = nfs + 1.8f * scale;
      float px = sx - pw * 0.5f;
      float py = y + size + 1.6f * scale;
      canvas.roundedRect(px, py, pw, ph, 1.8f * scale, Colors.alpha(0xCC090D16, iconAlpha));
      canvas.textFamily("studio-sans-medium", label, px + 1.8f * scale, py + 0.8f * scale, nfs,
          Colors.alpha(boss ? 0xFFF0ABFC : 0xFFE2E8F0, iconAlpha), false, 0f);
    }
  }

  private static int categoryBorderColor(int kind) {
    switch (kind) {
      case MapRadarModels.MOB_BOSS: return 0xEAE879F9;
      case MapRadarModels.MOB_HOSTILE: return 0xC8EF4444;
      case MapRadarModels.MOB_NEUTRAL: return 0xB8F59E0B;
      case MapRadarModels.MOB_PASSIVE: return 0xA84ADE80;
      case MapRadarModels.MOB_TNT: return 0xE8F97316;
      case MapRadarModels.MOB_CRYSTAL: return 0xE8E879F9;
      default: return 0x9E94A3B8;
    }
  }

  public static synchronized int[] spriteForType(String entityType, int kind) {
    String id = normalizeType(entityType);
    return getOrBuildSprite(resolveSpriteKey(id, kind), id, kind);
  }

  private static String resolveSpriteKey(String id, int kind) {
    if (kind == MapRadarModels.MOB_TNT) return "tnt";
    if (kind == MapRadarModels.MOB_CRYSTAL) return "end_crystal";
    if (id.isEmpty()) return "kind_" + kind;
    return id;
  }

  private static synchronized int[] getOrBuildSprite(String key, String id, int kind) {
    int[] existing = SPRITE_CACHE.get(key);
    if (existing != null) return existing;
    int[] g = new int[SPRITE_SIZE * SPRITE_SIZE];
    paintEntity16(g, key, id, kind);
    addSilhouetteOutline(g);
    if (SPRITE_CACHE.size() > 512) SPRITE_CACHE.clear();
    SPRITE_CACHE.put(key, g);
    return g;
  }

  private static void paintEntity16(int[] g, String key, String id, int kind) {
    if ("tnt".equals(key)) { paintTnt(g); return; }
    if ("end_crystal".equals(key)) { paintEndCrystal(g); return; }
    if (id.contains("tnt_minecart")) { paintMinecart(g, 0xFFDC2626, 0xFFF8FAFC); return; }
    if (id.contains("chest_minecart")) { paintMinecart(g, 0xFFB4782C, 0xFF784A16); return; }
    if (id.contains("hopper_minecart")) { paintMinecart(g, 0xFF475569, 0xFF1E293B); return; }
    if (id.contains("furnace_minecart")) { paintMinecart(g, 0xFF6B7280, 0xFFF97316); return; }
    if (id.contains("minecart")) { paintMinecart(g, 0, 0); return; }
    if (id.contains("chest_boat") || id.contains("chest_raft")) { paintBoat(g, true); return; }
    if (id.contains("boat") || id.contains("raft")) { paintBoat(g, false); return; }
    if (id.contains("armor_stand")) { paintArmorStand(g); return; }

    if (id.contains("ender_dragon")) { paintEnderDragon(g); return; }
    if ("wither".equals(id)) { paintWither(g); return; }
    if (id.contains("warden")) { paintWarden(g); return; }
    if (id.contains("elder_guardian")) { paintGuardian(g, true); return; }

    if (id.contains("creeper")) { paintCreeper(g); return; }
    if (id.contains("zombie_villager")) { paintVillagerLike(g, 0xFF568247, 0xFF3A5C2E, 0xFFDC2626, 0, 0); return; }
    if (id.contains("zombie_horse")) { paintHorse(g, 0xFF5A8A4A, 0xFF39592E, 0xFF1A2814, false); return; }
    if (id.contains("skeleton_horse")) { paintHorse(g, 0xFFD5D7DC, 0xFF9CA3AF, 0xFF1F242B, false); return; }
    if (id.contains("drowned")) { paintZombie(g, 0xFF6BA89E, 0xFF3E706A, 0xFF67E8F9, 0xFF285858); return; }
    if (id.contains("husk")) { paintZombie(g, 0xFFA8986E, 0xFF7A6D4E, 0xFF231F18, 0xFF695D42); return; }
    if (id.contains("zombie_nautilus")) { paintNautilus(g, true); return; }
    if (id.contains("zombified_piglin")) { paintPiglin(g, true, false); return; }
    if (id.contains("zombie")) { paintZombie(g, 0xFF5E944B, 0xFF3F6832, 0xFF1A2616, 0xFF477239); return; }
    if (id.contains("wither_skeleton")) { paintSkeleton(g, 0xFF3A3D45, 0xFF23262C, 0xFF101216, 0xFF9CA3AF); return; }
    if (id.contains("stray")) { paintSkeleton(g, 0xFF9DB5BE, 0xFF6D838C, 0xFF1A252B, 0xFFE0F2FE); return; }
    if (id.contains("bogged")) { paintSkeleton(g, 0xFF8E9C7E, 0xFF5B694E, 0xFF1A2216, 0xFFDC2626); return; }
    if (id.contains("parched")) { paintSkeleton(g, 0xFFCDB98C, 0xFF9A865C, 0xFF2E2618, 0xFF1F1A12); return; }
    if (id.contains("skeleton")) { paintSkeleton(g, 0xFFDADCE0, 0xFFB0B4BC, 0xFF22262E, 0xFF22262E); return; }
    if (id.contains("cave_spider")) { paintSpider(g, 0xFF1C303A, 0xFF112028, 0xFFEF4444); return; }
    if (id.contains("spider")) { paintSpider(g, 0xFF3B3332, 0xFF241F1E, 0xFFEF4444); return; }
    if (id.contains("enderman")) { paintEnderman(g); return; }
    if (id.contains("blaze")) { paintBlaze(g); return; }
    if (id.contains("happy_ghast")) { paintGhast(g, true); return; }
    if (id.contains("ghast")) { paintGhast(g, false); return; }
    if (id.contains("magma_cube")) { paintMagmaCube(g); return; }
    if (id.contains("slime")) { paintSlime(g); return; }
    if (id.contains("piglin_brute")) { paintPiglin(g, false, true); return; }
    if (id.contains("piglin")) { paintPiglin(g, false, false); return; }
    if (id.contains("zoglin")) { paintHoglin(g, true); return; }
    if (id.contains("hoglin")) { paintHoglin(g, false); return; }
    if (id.contains("strider")) { paintStrider(g); return; }
    if (id.contains("guardian")) { paintGuardian(g, false); return; }
    if (id.contains("shulker")) { paintShulker(g); return; }
    if (id.contains("phantom")) { paintPhantom(g); return; }
    if (id.contains("ravager")) { paintRavager(g); return; }
    if (id.contains("breeze")) { paintBreeze(g); return; }
    if (id.contains("creaking")) { paintCreaking(g); return; }
    if (id.contains("silverfish")) { paintBug(g, false); return; }
    if (id.contains("endermite")) { paintBug(g, true); return; }
    if (id.contains("vex")) { paintSpirit(g, true); return; }
    if (id.contains("allay")) { paintSpirit(g, false); return; }

    if (id.contains("witch")) { paintWitch(g); return; }
    if (id.contains("wandering_trader")) { paintVillagerLike(g, 0xFFC69470, 0xFFA27050, 0xFF16A34A, 0xFF1D4ED8, 0xFFFACC15); return; }
    if (id.contains("pillager")) { paintVillagerLike(g, 0xFF969B9C, 0xFF727779, 0xFF1F2937, 0xFF4B2828, 0); return; }
    if (id.contains("vindicator")) { paintVillagerLike(g, 0xFF969B9C, 0xFF727779, 0xFF2563EB, 0, 0); return; }
    if (id.contains("evoker") || id.contains("illusioner")) { paintVillagerLike(g, 0xFF969B9C, 0xFF727779, 0xFF16A34A, 0xFF1F2937, 0xFFFACC15); return; }
    if (id.contains("villager")) { paintVillagerLike(g, 0xFFC79570, 0xFFA47251, 0xFF16A34A, 0, 0); return; }
    if (id.contains("iron_golem")) { paintIronGolem(g); return; }
    if (id.contains("snow_golem")) { paintSnowGolem(g); return; }
    if (id.contains("copper_golem")) { paintCopperGolem(g); return; }

    if (id.contains("pig")) { paintPig(g); return; }
    if (id.contains("mooshroom")) { paintCow(g, true); return; }
    if (id.contains("cow")) { paintCow(g, false); return; }
    if (id.contains("sheep")) { paintSheep(g); return; }
    if (id.contains("chicken")) { paintChicken(g); return; }
    if (id.contains("wolf")) { paintWolf(g); return; }
    if (id.contains("fox")) { paintFox(g); return; }
    if (id.contains("ocelot")) { paintCat(g, 0xFFE8BE5A, 0xFFC29032, 0xFF22C55E); return; }
    if (id.contains("cat")) { paintCat(g, 0xFFD49A6A, 0xFF9B6842, 0xFF38BDF8); return; }
    if (id.contains("rabbit")) { paintRabbit(g); return; }
    if (id.contains("donkey") || id.contains("mule")) { paintHorse(g, 0xFF8C7B6E, 0xFF5A4C43, 0xFF1C1917, true); return; }
    if (id.contains("horse")) { paintHorse(g, 0xFF9E663C, 0xFF54321B, 0xFF1C1917, false); return; }
    if (id.contains("llama")) { paintLlama(g, id.contains("trader")); return; }
    if (id.contains("camel_husk")) { paintCamel(g, true); return; }
    if (id.contains("camel")) { paintCamel(g, false); return; }
    if (id.contains("goat")) { paintGoat(g); return; }
    if (id.contains("frog")) { paintFrog(g); return; }
    if (id.contains("axolotl")) { paintAxolotl(g); return; }
    if (id.contains("armadillo")) { paintArmadillo(g); return; }
    if (id.contains("sniffer")) { paintSniffer(g); return; }
    if (id.contains("bat")) { paintBat(g); return; }
    if (id.contains("bee")) { paintBee(g); return; }
    if (id.contains("parrot")) { paintParrot(g); return; }
    if (id.contains("panda")) { paintPanda(g); return; }
    if (id.contains("polar_bear")) { paintPolarBear(g); return; }
    if (id.contains("turtle")) { paintTurtle(g); return; }
    if (id.contains("dolphin")) { paintDolphin(g); return; }
    if (id.contains("glow_squid")) { paintSquid(g, true); return; }
    if (id.contains("squid")) { paintSquid(g, false); return; }
    if (id.contains("nautilus")) { paintNautilus(g, false); return; }
    if (id.contains("pufferfish")) { paintPufferfish(g); return; }
    if (id.contains("salmon")) { paintFish(g, 0xFFB84C4C, 0xFF6B8E78); return; }
    if (id.contains("tropical_fish")) { paintFish(g, 0xFFF97316, 0xFFF8FAFC); return; }
    if (id.contains("tadpole")) { paintFish(g, 0xFF5B483A, 0xFF3E3026); return; }
    if (id.contains("cod")) { paintFish(g, 0xFFC2A87E, 0xFF8C7352); return; }

    int base = categoryColor(kind);
    int shade = Colors.mix(base, 0xFF000000, 0.24f);
    rect(g, 3, 3, 10, 10, base);
    rect(g, 3, 3, 10, 2, shade);
    symRect(g, 4, 7, 2, 2, 0xFF141820);
    symRect(g, 4, 7, 1, 1, 0xFFF8FAFC);
    rect(g, 6, 10, 4, 1, shade);
  }

  private static void paintTnt(int[] g) {
    rect(g, 2, 3, 12, 11, 0xFFDB3328);
    rect(g, 2, 3, 12, 2, 0xFFB8241C);
    rect(g, 2, 12, 12, 2, 0xFFB8241C);
    for (int x = 4; x <= 12; x += 3) {
      rect(g, x, 3, 1, 2, 0xFF921B14);
      rect(g, x, 12, 1, 2, 0xFF921B14);
    }
    rect(g, 2, 6, 12, 5, 0xFFF1F5F9);
    rect(g, 2, 6, 12, 1, 0xFFCBD5E1);
    rect(g, 3, 7, 3, 1, 0xFF111827);
    rect(g, 4, 8, 1, 2, 0xFF111827);
    rect(g, 7, 7, 1, 3, 0xFF111827);
    px(g, 8, 8, 0xFF111827);
    rect(g, 9, 7, 1, 3, 0xFF111827);
    rect(g, 11, 7, 3, 1, 0xFF111827);
    rect(g, 12, 8, 1, 2, 0xFF111827);
    rect(g, 7, 1, 2, 2, 0xFFFACC15);
    px(g, 8, 1, 0xFFFFFFFF);
  }

  private static void paintEndCrystal(int[] g) {
    rect(g, 6, 2, 4, 1, 0xFFE2E8F0);
    rect(g, 6, 13, 4, 1, 0xFFE2E8F0);
    symRect(g, 4, 3, 2, 1, 0xFFCBD5E1);
    symRect(g, 4, 12, 2, 1, 0xFFCBD5E1);
    symRect(g, 3, 4, 1, 2, 0xFFCBD5E1);
    symRect(g, 3, 10, 1, 2, 0xFFCBD5E1);
    symRect(g, 2, 6, 1, 4, 0xFFE2E8F0);
    rect(g, 4, 5, 8, 6, 0xFF9333EA);
    rect(g, 5, 4, 6, 8, 0xFFC026D3);
    rect(g, 6, 5, 4, 6, 0xFFE879F9);
    rect(g, 6, 5, 2, 2, 0xFFFDF4FF);
  }

  private static void paintBoat(int[] g, boolean chest) {
    rect(g, 2, 7, 12, 4, 0xFF9C6F3E);
    rect(g, 3, 11, 10, 2, 0xFF7A532B);
    rect(g, 3, 8, 10, 2, 0xFF5C3D1E);
    rect(g, 2, 7, 12, 1, 0xFFC08E55);
    symRect(g, 1, 9, 1, 2, 0xFFD4A36A);
    if (chest) {
      rect(g, 5, 4, 6, 5, 0xFFC2822E);
      rect(g, 5, 4, 6, 1, 0xFFDCA146);
      rect(g, 5, 6, 6, 1, 0xFF5C3A14);
      rect(g, 7, 5, 2, 2, 0xFFE5E7EB);
    }
  }

  private static void paintMinecart(int[] g, int cargoMain, int cargoAccent) {
    if (cargoMain != 0) {
      rect(g, 4, 3, 8, 4, cargoMain);
      if (cargoAccent != 0) rect(g, 4, 5, 8, 2, cargoAccent);
    }
    rect(g, 2, 6, 12, 5, 0xFF64748B);
    rect(g, 3, 7, 10, 3, 0xFF334155);
    rect(g, 2, 6, 12, 1, 0xFFCBD5E1);
    rect(g, 3, 10, 10, 1, 0xFF475569);
    symRect(g, 4, 11, 2, 2, 0xFF1E293B);
    symRect(g, 4, 11, 1, 1, 0xFF94A3B8);
  }

  private static void paintArmorStand(int[] g) {
    rect(g, 7, 2, 2, 2, 0xFFC2955B);
    rect(g, 3, 5, 10, 2, 0xFFC2955B);
    rect(g, 6, 7, 4, 4, 0xFFA67B46);
    rect(g, 7, 8, 2, 2, 0xFF7D5A30);
    symRect(g, 6, 11, 1, 2, 0xFFC2955B);
    rect(g, 4, 13, 8, 1, 0xFF94A3B8);
  }

  private static void paintEnderDragon(int[] g) {
    symRect(g, 3, 2, 2, 3, 0xFF4B5563);
    rect(g, 3, 4, 10, 8, 0xFF1F2028);
    rect(g, 4, 4, 8, 2, 0xFF313340);
    rect(g, 5, 9, 6, 5, 0xFF2A2B36);
    symRect(g, 6, 11, 1, 1, 0xFF0B0C10);
    symRect(g, 4, 7, 3, 2, 0xFFF0ABFC);
    symRect(g, 5, 7, 1, 2, 0xFFC026D3);
  }

  private static void paintWither(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFF2C2E34);
    rect(g, 3, 3, 10, 3, 0xFF3B3E47);
    rect(g, 3, 6, 10, 1, 0xFF1B1D22);
    symRect(g, 4, 7, 3, 2, 0xFF93C5FD);
    symRect(g, 5, 7, 1, 2, 0xFFE0F2FE);
    rect(g, 6, 10, 4, 1, 0xFF14161B);
    rect(g, 4, 12, 8, 1, 0xFF181A20);
  }

  private static void paintWarden(int[] g) {
    symRect(g, 1, 2, 3, 5, 0xFF14B8A6);
    symRect(g, 2, 3, 1, 3, 0xFF67E8F9);
    rect(g, 4, 3, 8, 10, 0xFF0E3442);
    rect(g, 5, 4, 6, 2, 0xFF155E75);
    rect(g, 5, 2, 6, 2, 0xFFCBD5C0);
    rect(g, 5, 7, 6, 5, 0xFF06151C);
    rect(g, 6, 8, 4, 1, 0xFF22D3EE);
    rect(g, 6, 10, 4, 1, 0xFF22D3EE);
  }

  private static void paintGuardian(int[] g, boolean elder) {
    int body = elder ? 0xFFC8C2B8 : 0xFF4F8E88;
    int dark = elder ? 0xFF99938B : 0xFF32635E;
    int spike = elder ? 0xFF7B6C8E : 0xFFD97736;
    symRect(g, 1, 2, 2, 2, spike);
    symRect(g, 1, 12, 2, 2, spike);
    rect(g, 3, 3, 10, 10, body);
    rect(g, 4, 4, 8, 2, dark);
    rect(g, 5, 6, 6, 4, 0xFFF8FAFC);
    rect(g, 6, 7, 4, 2, elder ? 0xFF6366F1 : 0xFFDC2626);
    rect(g, 7, 7, 2, 2, 0xFF111827);
  }

  private static void paintCreeper(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFF53B44B);
    symRect(g, 2, 2, 3, 2, 0xFF65C75C);
    symRect(g, 5, 3, 2, 2, 0xFF3E9438);
    symRect(g, 2, 10, 2, 3, 0xFF429C3B);
    rect(g, 6, 2, 4, 2, 0xFF46A33F);
    symRect(g, 4, 5, 2, 2, 0xFF101510);
    rect(g, 6, 7, 4, 3, 0xFF101510);
    symRect(g, 5, 9, 1, 3, 0xFF101510);
  }

  private static void paintZombie(int[] g, int skin, int hair, int eye, int shade) {
    rect(g, 2, 2, 12, 12, skin);
    rect(g, 2, 2, 12, 3, hair);
    symRect(g, 2, 5, 1, 2, hair);
    symRect(g, 4, 7, 2, 2, eye);
    rect(g, 6, 10, 4, 2, shade);
    rect(g, 6, 11, 4, 1, hair);
  }

  private static void paintSkeleton(int[] g, int bone, int shade, int eye, int accent) {
    rect(g, 2, 2, 12, 12, bone);
    rect(g, 2, 2, 12, 2, shade);
    symRect(g, 2, 11, 2, 3, shade);
    symRect(g, 4, 7, 3, 2, eye);
    if (accent != eye) symRect(g, 5, 7, 1, 1, accent);
    rect(g, 7, 9, 2, 1, shade);
    rect(g, 4, 11, 8, 2, eye);
    px(g, 6, 11, bone);
    px(g, 9, 11, bone);
  }

  private static void paintSpider(int[] g, int body, int dark, int eye) {
    rect(g, 2, 3, 12, 10, body);
    rect(g, 3, 3, 10, 2, dark);
    rect(g, 3, 11, 10, 2, dark);
    symRect(g, 4, 6, 2, 2, eye);
    symRect(g, 5, 6, 1, 1, 0xFFFCA5A5);
    symRect(g, 3, 9, 1, 1, eye);
    symRect(g, 5, 9, 1, 1, eye);
    symRect(g, 6, 5, 1, 1, 0xFFB91C1C);
    symRect(g, 5, 11, 2, 2, 0xFF141111);
  }

  private static void paintEnderman(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFF18181F);
    rect(g, 3, 3, 10, 2, 0xFF23232D);
    symRect(g, 3, 7, 4, 2, 0xFFF5D0FE);
    symRect(g, 4, 7, 2, 2, 0xFFD946EF);
  }

  private static void paintBlaze(int[] g) {
    symRect(g, 1, 4, 1, 7, 0xFFFDE047);
    rect(g, 3, 2, 10, 11, 0xFFF59E0B);
    rect(g, 3, 2, 10, 3, 0xFFD97706);
    rect(g, 3, 10, 10, 3, 0xFFB45309);
    symRect(g, 4, 7, 3, 2, 0xFFFEF08A);
    symRect(g, 5, 7, 1, 2, 0xFF1C1917);
  }

  private static void paintGhast(int[] g, boolean happy) {
    rect(g, 2, 2, 12, 12, 0xFFF1F5F9);
    rect(g, 2, 12, 12, 2, 0xFFCBD5E1);
    if (happy) {
      rect(g, 2, 3, 12, 3, 0xFF78350F);
      symRect(g, 4, 3, 3, 3, 0xFF38BDF8);
      symRect(g, 4, 7, 2, 2, 0xFF1E293B);
      rect(g, 6, 10, 4, 1, 0xFF334155);
      symRect(g, 5, 9, 1, 1, 0xFF334155);
    } else {
      symRect(g, 4, 6, 3, 1, 0xFF334155);
      symRect(g, 5, 7, 1, 3, 0xFF94A3B8);
      rect(g, 6, 10, 4, 3, 0xFF7F1D1D);
      rect(g, 7, 11, 2, 1, 0xFFEF4444);
    }
  }

  private static void paintSlime(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFF7CE06C);
    rect(g, 3, 3, 10, 10, 0xFF5DBE4E);
    rect(g, 4, 4, 8, 8, 0xFF4CA840);
    symRect(g, 4, 6, 2, 2, 0xFF1B3A16);
    rect(g, 8, 9, 2, 2, 0xFF1B3A16);
  }

  private static void paintMagmaCube(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFF3A1A16);
    rect(g, 2, 6, 12, 1, 0xFF52231D);
    rect(g, 2, 10, 12, 1, 0xFF52231D);
    symRect(g, 4, 7, 3, 2, 0xFFF97316);
    symRect(g, 5, 7, 1, 2, 0xFFFEF08A);
    rect(g, 5, 11, 6, 1, 0xFFEA580C);
  }

  private static void paintPiglin(int[] g, boolean zombified, boolean brute) {
    int skin = zombified ? 0xFFA28A7E : 0xFFE8A488;
    int snout = zombified ? 0xFF8C7268 : 0xFFD9876E;
    symRect(g, 1, 6, 2, 5, Colors.mix(skin, 0xFF000000, 0.14f));
    rect(g, 3, 2, 10, 12, skin);
    if (zombified) {
      rect(g, 8, 2, 5, 7, 0xFF5E8C4B);
      rect(g, 9, 4, 3, 3, 0xFF3D5E30);
    } else if (brute) {
      rect(g, 3, 3, 10, 2, 0xFF292524);
      rect(g, 6, 3, 4, 1, 0xFFFACC15);
    }
    symRect(g, 4, 6, 2, 2, 0xFFF8FAFC);
    symRect(g, 4, 6, 1, 2, zombified ? 0xFF166534 : 0xFF1C1917);
    rect(g, 5, 9, 6, 4, snout);
    symRect(g, 6, 10, 1, 2, 0xFF4A251B);
    symRect(g, 4, 10, 1, 3, 0xFFF8FAFC);
  }

  private static void paintHoglin(int[] g, boolean zoglin) {
    int skin = zoglin ? 0xFF9C7A72 : 0xFFC77B63;
    int mane = zoglin ? 0xFF4C7242 : 0xFF3B2822;
    rect(g, 6, 1, 4, 3, mane);
    rect(g, 2, 3, 12, 10, skin);
    symRect(g, 1, 4, 2, 3, Colors.mix(skin, 0xFF000000, 0.18f));
    symRect(g, 4, 6, 2, 2, 0xFFF8FAFC);
    symRect(g, 5, 6, 1, 2, 0xFF1C1917);
    rect(g, 4, 9, 8, 4, Colors.mix(skin, 0xFF000000, 0.16f));
    symRect(g, 2, 8, 2, 5, 0xFFF8FAFC);
  }

  private static void paintStrider(int[] g) {
    symRect(g, 1, 4, 2, 1, 0xFFD4D4D8);
    symRect(g, 1, 7, 2, 1, 0xFFD4D4D8);
    symRect(g, 1, 10, 2, 1, 0xFFD4D4D8);
    rect(g, 3, 3, 10, 10, 0xFF9E3F4B);
    rect(g, 3, 3, 10, 3, 0xFF7A2D38);
    symRect(g, 4, 7, 2, 2, 0xFFF8FAFC);
    symRect(g, 5, 7, 1, 2, 0xFF1F2937);
    rect(g, 5, 11, 6, 1, 0xFF5E2028);
  }

  private static void paintShulker(int[] g) {
    rect(g, 2, 2, 12, 4, 0xFF93589D);
    rect(g, 2, 11, 12, 3, 0xFF7B4385);
    rect(g, 4, 6, 8, 5, 0xFFE9DFB8);
    symRect(g, 5, 7, 2, 2, 0xFF1F2937);
    symRect(g, 5, 7, 1, 1, 0xFFF8FAFC);
  }

  private static void paintPhantom(int[] g) {
    symRect(g, 1, 6, 3, 4, 0xFF3B4D7A);
    rect(g, 3, 4, 10, 8, 0xFF475C8F);
    rect(g, 4, 4, 8, 3, 0xFFCBD5E1);
    symRect(g, 4, 8, 3, 2, 0xFF4ADE80);
    symRect(g, 5, 8, 1, 1, 0xFFDCFCE7);
  }

  private static void paintRavager(int[] g) {
    symRect(g, 1, 2, 2, 5, 0xFFD6D3D1);
    rect(g, 3, 3, 10, 10, 0xFF6B7280);
    rect(g, 3, 3, 10, 3, 0xFF4B5563);
    rect(g, 3, 6, 10, 1, 0xFF1F2937);
    symRect(g, 4, 7, 2, 2, 0xFFEF4444);
    rect(g, 6, 8, 4, 4, 0xFF525864);
  }

  private static void paintBreeze(int[] g) {
    rect(g, 3, 3, 10, 10, 0xFF9EA8CE);
    rect(g, 2, 5, 12, 2, 0xFFCBD5F5);
    symRect(g, 4, 7, 3, 2, 0xFF38BDF8);
    symRect(g, 5, 7, 1, 1, 0xFFE0F2FE);
    rect(g, 5, 11, 6, 2, 0xFF67E8F9);
  }

  private static void paintCreaking(int[] g) {
    rect(g, 3, 2, 10, 12, 0xFF44403C);
    rect(g, 4, 2, 3, 12, 0xFF57534E);
    rect(g, 10, 3, 2, 10, 0xFF292524);
    px(g, 5, 6, 0xFFF97316);
    px(g, 6, 6, 0xFFFDE047);
    px(g, 10, 5, 0xFFF97316);
    px(g, 10, 9, 0xFFF97316);
    px(g, 11, 9, 0xFFFDE047);
  }

  private static void paintBug(int[] g, boolean endermite) {
    int c1 = endermite ? 0xFF4C2A5E : 0xFF787E87;
    int c2 = endermite ? 0xFF2E173B : 0xFF52575E;
    int eye = endermite ? 0xFFE879F9 : 0xFF111827;
    rect(g, 3, 5, 10, 6, c1);
    rect(g, 4, 4, 8, 8, c1);
    rect(g, 4, 6, 8, 1, c2);
    rect(g, 4, 9, 8, 1, c2);
    symRect(g, 5, 7, 2, 2, eye);
  }

  private static void paintSpirit(int[] g, boolean vex) {
    int skin = vex ? 0xFF7B93B2 : 0xFF5EEAD4;
    int hair = vex ? 0xFF4C6382 : 0xFF0EA5E9;
    int eye = vex ? 0xFFF87171 : 0xFF0F172A;
    rect(g, 3, 3, 10, 10, skin);
    rect(g, 3, 3, 10, 3, hair);
    symRect(g, 4, 7, 2, 2, eye);
    if (!vex) symRect(g, 4, 7, 1, 1, 0xFFE0F2FE);
  }

  private static void paintVillagerLike(int[] g, int skin, int nose, int iris, int hat, int trim) {
    rect(g, 3, 2, 10, 12, skin);
    if (hat != 0) {
      rect(g, 2, 1, 12, 4, hat);
      if (trim != 0) rect(g, 3, 4, 10, 1, trim);
    }
    rect(g, 4, 6, 8, 1, 0xFF3E2723);
    symRect(g, 4, 7, 3, 2, 0xFFF8FAFC);
    symRect(g, 5, 7, 2, 2, iris);
    rect(g, 7, 8, 2, 5, nose);
    rect(g, 7, 12, 2, 1, Colors.mix(nose, 0xFF000000, 0.22f));
  }

  private static void paintWitch(int[] g) {
    rect(g, 6, 1, 4, 2, 0xFF27173D);
    rect(g, 4, 3, 8, 2, 0xFF2E1A47);
    rect(g, 2, 5, 12, 1, 0xFF22C55E);
    rect(g, 3, 6, 10, 8, 0xFFC29B7B);
    rect(g, 4, 7, 8, 1, 0xFF3E2723);
    symRect(g, 4, 8, 3, 2, 0xFFF8FAFC);
    symRect(g, 5, 8, 2, 2, 0xFF7E22CE);
    rect(g, 7, 9, 2, 4, 0xFF9C7558);
    px(g, 7, 11, 0xFF5B3F2E);
  }

  private static void paintIronGolem(int[] g) {
    rect(g, 3, 2, 10, 12, 0xFFD6D0C8);
    rect(g, 3, 2, 10, 2, 0xFFB8B0A6);
    rect(g, 3, 3, 3, 2, 0xFF4D7C0F);
    px(g, 11, 4, 0xFF4D7C0F);
    rect(g, 4, 6, 8, 1, 0xFF78716C);
    symRect(g, 4, 7, 3, 2, 0xFF1C1917);
    symRect(g, 5, 7, 1, 2, 0xFFDC2626);
    rect(g, 7, 8, 2, 5, 0xFFA8A29E);
  }

  private static void paintSnowGolem(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFFD97706);
    rect(g, 4, 2, 2, 12, 0xFFEA580C);
    rect(g, 10, 2, 2, 12, 0xFFEA580C);
    symRect(g, 4, 5, 2, 2, 0xFF1C1917);
    rect(g, 4, 9, 8, 2, 0xFF1C1917);
    px(g, 6, 9, 0xFFD97706);
    px(g, 9, 10, 0xFFD97706);
  }

  private static void paintCopperGolem(int[] g) {
    rect(g, 7, 1, 2, 3, 0xFFE07A47);
    rect(g, 3, 4, 10, 9, 0xFFC86D44);
    rect(g, 3, 4, 4, 3, 0xFF5BA89B);
    symRect(g, 4, 7, 2, 2, 0xFF34D399);
    rect(g, 7, 8, 2, 4, 0xFFA6522C);
  }

  private static void paintPig(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFFF4A5AA);
    rect(g, 2, 2, 12, 2, 0xFFE68C94);
    symRect(g, 2, 6, 3, 2, 0xFFF8FAFC);
    symRect(g, 2, 6, 2, 2, 0xFF1F1616);
    rect(g, 5, 8, 6, 5, 0xFFEA848D);
    rect(g, 5, 8, 6, 1, 0xFFFBC4C8);
    symRect(g, 6, 9, 1, 2, 0xFF6B2830);
  }

  private static void paintCow(int[] g, boolean mooshroom) {
    int coat = mooshroom ? 0xFFB02A2A : 0xFF5C4332;
    int spot = mooshroom ? 0xFFE5E7EB : 0xFFD6CFC7;
    symRect(g, 1, 2, 2, 3, 0xFFD6D3D1);
    rect(g, 3, 3, 10, 11, coat);
    rect(g, 8, 3, 4, 4, spot);
    symRect(g, 3, 6, 2, 2, 0xFFF8FAFC);
    symRect(g, 3, 6, 1, 2, 0xFF1C1917);
    rect(g, 4, 9, 8, 5, 0xFFD2C7BF);
    symRect(g, 5, 10, 1, 2, 0xFF3F3630);
  }

  private static void paintSheep(int[] g) {
    rect(g, 2, 2, 12, 12, 0xFFF1F5F9);
    rect(g, 2, 2, 12, 2, 0xFFDCE2EA);
    rect(g, 4, 5, 8, 8, 0xFFD5A98A);
    symRect(g, 4, 7, 2, 2, 0xFFF8FAFC);
    symRect(g, 4, 7, 1, 2, 0xFF1E293B);
    rect(g, 6, 10, 4, 2, 0xFFF4A3A8);
  }

  private static void paintChicken(int[] g) {
    rect(g, 3, 2, 10, 11, 0xFFF8FAFC);
    rect(g, 3, 2, 10, 2, 0xFFE2E8F0);
    symRect(g, 4, 5, 2, 2, 0xFF0F172A);
    rect(g, 5, 7, 6, 3, 0xFFEAB308);
    rect(g, 5, 9, 6, 1, 0xFFCA8A04);
    rect(g, 6, 10, 4, 4, 0xFFDC2626);
  }

  private static void paintWolf(int[] g) {
    symRect(g, 2, 1, 3, 3, 0xFFCBD5E1);
    symRect(g, 3, 2, 1, 2, 0xFF94A3B8);
    rect(g, 2, 4, 12, 10, 0xFFE2E8F0);
    rect(g, 2, 4, 12, 2, 0xFFCBD5E1);
    symRect(g, 4, 7, 2, 2, 0xFFF8FAFC);
    symRect(g, 5, 7, 1, 2, 0xFF0F172A);
    rect(g, 5, 9, 6, 5, 0xFFF8FAFC);
    rect(g, 6, 9, 4, 2, 0xFF1E293B);
    rect(g, 6, 12, 4, 1, 0xFF64748B);
  }

  private static void paintFox(int[] g) {
    symRect(g, 2, 1, 3, 3, 0xFFEA580C);
    symRect(g, 3, 2, 1, 2, 0xFF1C1917);
    rect(g, 2, 4, 12, 9, 0xFFF97316);
    rect(g, 2, 10, 12, 3, 0xFFF8FAFC);
    symRect(g, 4, 7, 2, 2, 0xFFF8FAFC);
    symRect(g, 5, 7, 1, 2, 0xFF0F172A);
    rect(g, 5, 9, 6, 5, 0xFFF97316);
    rect(g, 6, 11, 4, 3, 0xFFF8FAFC);
    rect(g, 6, 10, 4, 2, 0xFF1C1917);
  }

  private static void paintCat(int[] g, int coat, int stripe, int eye) {
    symRect(g, 2, 2, 3, 3, coat);
    symRect(g, 3, 3, 1, 1, 0xFFF4A3A8);
    rect(g, 2, 5, 12, 8, coat);
    rect(g, 6, 5, 4, 2, stripe);
    symRect(g, 4, 7, 2, 2, eye);
    symRect(g, 5, 7, 1, 2, 0xFF0F172A);
    rect(g, 6, 10, 4, 3, 0xFFF8FAFC);
    rect(g, 7, 10, 2, 1, 0xFFF472B6);
  }

  private static void paintRabbit(int[] g) {
    symRect(g, 4, 1, 2, 5, 0xFFB59472);
    symRect(g, 4, 2, 1, 3, 0xFFE8B4B8);
    rect(g, 3, 6, 10, 8, 0xFFC8A986);
    symRect(g, 4, 8, 2, 2, 0xFF1C1917);
    rect(g, 7, 10, 2, 2, 0xFFF472B6);
  }

  private static void paintHorse(int[] g, int coat, int mane, int eye, boolean longEars) {
    symRect(g, 3, longEars ? 1 : 2, 2, longEars ? 4 : 3, coat);
    rect(g, 6, 2, 4, 3, mane);
    rect(g, 3, 4, 10, 10, coat);
    rect(g, 7, 5, 2, 5, 0xFFF1F5F9);
    symRect(g, 4, 6, 2, 2, eye);
    rect(g, 4, 10, 8, 4, Colors.mix(coat, 0xFF000000, 0.25f));
    symRect(g, 5, 11, 1, 2, 0xFF1C1917);
  }

  private static void paintLlama(int[] g, boolean trader) {
    symRect(g, 3, 1, 2, 4, 0xFFE5D4B8);
    rect(g, 3, 4, 10, 10, 0xFFEFE1C6);
    if (trader) rect(g, 3, 4, 10, 2, 0xFF1D4ED8);
    symRect(g, 4, 7, 2, 2, 0xFF1C1917);
    rect(g, 5, 9, 6, 5, 0xFFD9C2A0);
    rect(g, 6, 10, 4, 2, 0xFF5C4738);
  }

  private static void paintCamel(int[] g, boolean husk) {
    int coat = husk ? 0xFFA38F6D : 0xFFD9A962;
    symRect(g, 2, 3, 2, 2, coat);
    rect(g, 3, 3, 10, 11, coat);
    symRect(g, 4, 6, 2, 1, 0xFF1C1917);
    rect(g, 4, 9, 8, 5, Colors.mix(coat, 0xFF000000, 0.14f));
    symRect(g, 5, 10, 1, 2, 0xFF3E2E1E);
  }

  private static void paintGoat(int[] g) {
    symRect(g, 4, 1, 2, 4, 0xFF57534E);
    symRect(g, 1, 5, 2, 2, 0xFFE7E5E4);
    rect(g, 3, 4, 10, 10, 0xFFF5F5F4);
    symRect(g, 4, 7, 2, 2, 0xFFFACC15);
    symRect(g, 4, 7, 2, 1, 0xFF1C1917);
    rect(g, 6, 11, 4, 3, 0xFFD6D3D1);
  }

  private static void paintFrog(int[] g) {
    symRect(g, 3, 3, 3, 3, 0xFFEA8A3A);
    symRect(g, 4, 4, 1, 1, 0xFF1C1917);
    rect(g, 2, 6, 12, 7, 0xFFDD7A2A);
    rect(g, 3, 10, 10, 3, 0xFFF5D5A8);
    rect(g, 4, 9, 8, 1, 0xFF7C3F14);
  }

  private static void paintAxolotl(int[] g) {
    symRect(g, 1, 4, 2, 2, 0xFFEC4899);
    symRect(g, 1, 7, 2, 2, 0xFFEC4899);
    symRect(g, 1, 10, 2, 2, 0xFFEC4899);
    rect(g, 3, 4, 10, 9, 0xFFFBCFE8);
    symRect(g, 4, 7, 2, 2, 0xFF1E293B);
    rect(g, 6, 10, 4, 1, 0xFFDB2777);
  }

  private static void paintArmadillo(int[] g) {
    rect(g, 3, 3, 10, 10, 0xFFB08474);
    rect(g, 3, 3, 10, 4, 0xFF8A6254);
    symRect(g, 2, 3, 2, 3, 0xFFD4A596);
    symRect(g, 4, 8, 2, 2, 0xFF1C1917);
    rect(g, 6, 11, 4, 2, 0xFF785245);
  }

  private static void paintSniffer(int[] g) {
    symRect(g, 1, 6, 2, 6, 0xFF991B1B);
    rect(g, 3, 3, 10, 11, 0xFFB91C1C);
    rect(g, 3, 3, 10, 3, 0xFF4D7C0F);
    symRect(g, 4, 7, 2, 2, 0xFF1C1917);
    rect(g, 5, 9, 6, 5, 0xFFEAB308);
    symRect(g, 6, 10, 1, 2, 0xFF422006);
  }

  private static void paintBat(int[] g) {
    symRect(g, 2, 2, 3, 4, 0xFF57463B);
    symRect(g, 3, 3, 1, 2, 0xFF8C705E);
    rect(g, 3, 5, 10, 8, 0xFF47382F);
    symRect(g, 4, 8, 2, 2, 0xFFFACC15);
    symRect(g, 5, 8, 1, 1, 0xFF1C1917);
    rect(g, 6, 11, 4, 1, 0xFFF8FAFC);
  }

  private static void paintBee(int[] g) {
    symRect(g, 5, 1, 1, 2, 0xFF1C1917);
    rect(g, 2, 3, 12, 10, 0xFFFACC15);
    rect(g, 2, 3, 12, 2, 0xFF422006);
    rect(g, 2, 11, 12, 2, 0xFF422006);
    symRect(g, 3, 5, 3, 5, 0xFF0F172A);
    symRect(g, 3, 5, 2, 2, 0xFF38BDF8);
  }

  private static void paintParrot(int[] g) {
    rect(g, 6, 1, 4, 3, 0xFFEF4444);
    rect(g, 3, 4, 10, 9, 0xFFDC2626);
    symRect(g, 4, 6, 2, 3, 0xFFF8FAFC);
    symRect(g, 4, 7, 1, 1, 0xFF0F172A);
    rect(g, 6, 8, 4, 4, 0xFF334155);
    rect(g, 7, 11, 2, 2, 0xFF1E293B);
  }

  private static void paintPanda(int[] g) {
    symRect(g, 2, 2, 3, 3, 0xFF1C1917);
    rect(g, 2, 4, 12, 10, 0xFFF8FAFC);
    symRect(g, 3, 6, 3, 3, 0xFF1C1917);
    symRect(g, 4, 7, 1, 1, 0xFFF8FAFC);
    rect(g, 6, 10, 4, 2, 0xFF1C1917);
  }

  private static void paintPolarBear(int[] g) {
    symRect(g, 2, 2, 3, 3, 0xFFE2E8F0);
    rect(g, 2, 4, 12, 10, 0xFFF8FAFC);
    symRect(g, 4, 7, 2, 2, 0xFF1E293B);
    rect(g, 5, 9, 6, 5, 0xFFE2E8F0);
    rect(g, 6, 10, 4, 2, 0xFF1E293B);
  }

  private static void paintTurtle(int[] g) {
    rect(g, 3, 4, 10, 9, 0xFF4CA844);
    rect(g, 3, 4, 10, 2, 0xFF3B8534);
    symRect(g, 4, 7, 2, 2, 0xFF111827);
    symRect(g, 4, 7, 1, 1, 0xFFF8FAFC);
    rect(g, 5, 11, 6, 1, 0xFF2D6A27);
  }

  private static void paintDolphin(int[] g) {
    rect(g, 3, 3, 10, 10, 0xFF94A3B8);
    rect(g, 3, 10, 10, 3, 0xFFE2E8F0);
    symRect(g, 4, 7, 2, 2, 0xFF0F172A);
    rect(g, 6, 10, 4, 4, 0xFF7D8FA3);
  }

  private static void paintSquid(int[] g, boolean glow) {
    int body = glow ? 0xFF22A699 : 0xFF3B4D6B;
    int spot = glow ? 0xFF67E8F9 : 0xFF52678A;
    rect(g, 3, 2, 10, 10, body);
    symRect(g, 4, 3, 2, 2, spot);
    symRect(g, 4, 7, 3, 3, 0xFFF8FAFC);
    symRect(g, 5, 8, 2, 2, 0xFF0F172A);
    for (int x = 3; x <= 11; x += 2) rect(g, x, 12, 1, 2, body);
  }

  private static void paintNautilus(int[] g, boolean zombie) {
    int shell = zombie ? 0xFF6F8A5B : 0xFFD9C7B0;
    int stripe = zombie ? 0xFF475C38 : 0xFFA66B4F;
    rect(g, 3, 3, 10, 10, shell);
    rect(g, 4, 4, 8, 2, stripe);
    rect(g, 5, 8, 6, 4, 0xFF5C4B42);
    symRect(g, 5, 8, 2, 2, zombie ? 0xFF67E8F9 : 0xFF111827);
  }

  private static void paintPufferfish(int[] g) {
    symRect(g, 1, 4, 1, 1, 0xFFEAB308);
    symRect(g, 1, 10, 1, 1, 0xFFEAB308);
    rect(g, 3, 3, 10, 10, 0xFFFACC15);
    rect(g, 3, 9, 10, 4, 0xFFFEF3C7);
    symRect(g, 4, 6, 2, 2, 0xFF0F172A);
    rect(g, 6, 9, 4, 1, 0xFFB45309);
  }

  private static void paintFish(int[] g, int top, int belly) {
    rect(g, 3, 4, 10, 8, top);
    rect(g, 3, 9, 10, 3, belly);
    symRect(g, 4, 6, 2, 2, 0xFF0F172A);
    symRect(g, 4, 6, 1, 1, 0xFFF8FAFC);
  }

  private static void px(int[] g, int x, int y, int argb) {
    if (x >= 0 && x < SPRITE_SIZE && y >= 0 && y < SPRITE_SIZE) {
      g[y * SPRITE_SIZE + x] = argb;
    }
  }

  private static void rect(int[] g, int x, int y, int w, int h, int argb) {
    int x1 = Math.max(0, x), y1 = Math.max(0, y);
    int x2 = Math.min(SPRITE_SIZE, x + w), y2 = Math.min(SPRITE_SIZE, y + h);
    for (int py = y1; py < y2; py++) {
      int row = py * SPRITE_SIZE;
      for (int px = x1; px < x2; px++) {
        g[row + px] = argb;
      }
    }
  }

  private static void symRect(int[] g, int x, int y, int w, int h, int argb) {
    rect(g, x, y, w, h, argb);
    rect(g, SPRITE_SIZE - x - w, y, w, h, argb);
  }

  private static void addSilhouetteOutline(int[] g) {
    boolean[] opaque = new boolean[SPRITE_SIZE * SPRITE_SIZE];
    for (int i = 0; i < g.length; i++) {
      opaque[i] = (g[i] >>> 24) > 0;
    }
    for (int y = 0; y < SPRITE_SIZE; y++) {
      for (int x = 0; x < SPRITE_SIZE; x++) {
        int idx = y * SPRITE_SIZE + x;
        if (opaque[idx]) continue;
        boolean neighbor =
            (x > 0 && opaque[idx - 1])
                || (x < SPRITE_SIZE - 1 && opaque[idx + 1])
                || (y > 0 && opaque[idx - SPRITE_SIZE])
                || (y < SPRITE_SIZE - 1 && opaque[idx + SPRITE_SIZE]);
        if (neighbor) {
          g[idx] = OUTLINE_COL;
        }
      }
    }
  }

  public static int categoryColor(int kind) {
    switch (kind) {
      case MapRadarModels.MOB_BOSS:
        return 0xFFE879F9;
      case MapRadarModels.MOB_HOSTILE:
        return 0xFFEF4444;
      case MapRadarModels.MOB_NEUTRAL:
        return 0xFFFBBF24;
      case MapRadarModels.MOB_PASSIVE:
        return 0xFF4ADE80;
      case MapRadarModels.MOB_TNT:
        return 0xFFF97316;
      case MapRadarModels.MOB_CRYSTAL:
        return 0xFFD946EF;
      default:
        return 0xFF60A5FA;
    }
  }

  private static String normalizeType(String type) {
    if (type == null || type.isEmpty()) return "";
    String lower = type.toLowerCase(Locale.ROOT);
    int colon = lower.indexOf(':');
    return colon >= 0 ? lower.substring(colon + 1) : lower;
  }
}
