package dev.nightclient.runtime;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SoundControlLogic {
  public static final List<String> POPULAR_BLOCKS = Settings.SoundControl.POPULAR_BLOCKS;

  private SoundControlLogic() {}

  public static final class SoundReplacement {
    public final String soundId;
    public final float volume;
    public final float pitch;

    public SoundReplacement(String soundId, float volume, float pitch) {
      this.soundId = soundId;
      this.volume = volume;
      this.pitch = pitch;
    }
  }

  public static final class Mp3Playback {
    public final String filePath;
    public final float volume;
    public final float pitch;
    public final boolean explosion;

    public Mp3Playback(String filePath, float volume, float pitch, boolean explosion) {
      this.filePath = filePath;
      this.volume = volume;
      this.pitch = pitch;
      this.explosion = explosion;
    }
  }

  public static final class MentionToast {
    public final String senderHint;
    public final String snippet;
    public final long createdNanos;

    public MentionToast(String senderHint, String snippet, long createdNanos) {
      this.senderHint = senderHint == null ? "" : senderHint;
      this.snippet = snippet == null ? "" : snippet;
      this.createdNanos = createdNanos;
    }
  }

  public static String normalizeBlockQuery(String raw) {
    return Settings.SoundControl.normalizeBlockQuery(raw);
  }

  public static String displayBlockName(String blockId, boolean ru) {
    return Settings.SoundControl.displayBlockName(blockId, ru);
  }

  public static String resolvePresetSoundId(String preset, String customId) {
    if ("custom".equals(preset)) {
      if (customId == null || customId.trim().isEmpty()) return "minecraft:block.note_block.pling";
      String c = customId.trim().toLowerCase(Locale.ROOT);
      return c.indexOf(':') >= 0 ? c : ("minecraft:" + c);
    }
    if ("amethyst".equals(preset)) return "minecraft:block.amethyst_block.chime";
    if ("anvil".equals(preset)) return "minecraft:block.anvil.land";
    if ("bell".equals(preset)) return "minecraft:block.bell.use";
    if ("warden_sonic".equals(preset)) return "minecraft:entity.warden.sonic_boom";
    if ("thunder".equals(preset)) return "minecraft:entity.lightning_bolt.thunder";
    if ("trident".equals(preset)) return "minecraft:item.trident.thunder";
    if ("experience".equals(preset)) return "minecraft:entity.experience_orb.pickup";
    if ("chime".equals(preset)) return "minecraft:block.note_block.chime";
    if ("levelup".equals(preset)) return "minecraft:entity.player.levelup";
    if ("bass".equals(preset)) return "minecraft:block.note_block.bass";
    return "minecraft:block.note_block.pling";
  }

  public static Mp3Playback resolveCustomTntMp3(Settings.SoundControl cfg, String soundId) {
    if (FeaturePolicy.isBlocked("soundControl") || cfg == null || !cfg.enabled || !cfg.customTnt || soundId == null) return null;
    String s = normalizeSoundPath(soundId);
    if ("entity.tnt.primed".equals(s) || "entity.creeper.primed".equals(s)) {
      if ("mp3".equals(cfg.tntFusePreset) && cfg.tntFuseMp3 != null
          && !cfg.tntFuseMp3.trim().isEmpty()) {
        return new Mp3Playback(cfg.tntFuseMp3.trim(), cfg.tntVolume, cfg.tntPitch, false);
      }
      return null;
    }
    if (s.startsWith("entity.generic.explode") || s.startsWith("entity.dragon_fireball.explode")) {
      if ("mp3".equals(cfg.tntExplodePreset) && cfg.tntExplodeMp3 != null
          && !cfg.tntExplodeMp3.trim().isEmpty()) {
        return new Mp3Playback(
            cfg.tntExplodeMp3.trim(), cfg.tntVolume * cfg.explosionVolume, cfg.tntPitch, true);
      }
      return null;
    }
    return null;
  }

  public static SoundReplacement resolveCustomTntReplacement(
      Settings.SoundControl cfg, String soundId) {
    if (FeaturePolicy.isBlocked("soundControl") || cfg == null || !cfg.enabled || !cfg.customTnt || soundId == null) return null;
    String s = normalizeSoundPath(soundId);
    if ("entity.tnt.primed".equals(s) || "entity.creeper.primed".equals(s)) {
      if ("vanilla".equals(cfg.tntFusePreset) || "mp3".equals(cfg.tntFusePreset)) return null;
      String target = resolvePresetSoundId(cfg.tntFusePreset, cfg.customTntSoundId);
      return new SoundReplacement(target, cfg.tntVolume, cfg.tntPitch);
    }
    if (s.startsWith("entity.generic.explode") || s.startsWith("entity.dragon_fireball.explode")) {
      if ("vanilla".equals(cfg.tntExplodePreset) || "mute".equals(cfg.tntExplodePreset)
          || "mp3".equals(cfg.tntExplodePreset))
        return null;
      String target = resolvePresetSoundId(cfg.tntExplodePreset, cfg.customTntSoundId);
      return new SoundReplacement(target, cfg.tntVolume, Math.max(0.5f, cfg.tntPitch * 0.85f));
    }
    return null;
  }

  public static float computeVolumeMultiplier(
      Settings.SoundControl cfg, String soundId, String worldBlockPath) {
    return computeVolumeMultiplier(cfg, soundId, worldBlockPath, true);
  }

  public static float computeVolumeMultiplier(
      Settings.SoundControl cfg, String soundId, String worldBlockPath, boolean allowMp3Mute) {
    if (FeaturePolicy.isBlocked("soundControl") || cfg == null || !cfg.enabled || soundId == null || soundId.isEmpty()) return 1.0f;
    String s = normalizeSoundPath(soundId);
    float mult = 1.0f;

    if (allowMp3Mute && resolveCustomTntMp3(cfg, soundId) != null) {
      return 0.0f;
    }

    if (s.contains("explode") || s.equals("item.mace.smash_ground_heavy")) {
      if (cfg.customTnt && "mute".equals(cfg.tntExplodePreset)) return 0.0f;
      mult *= cfg.explosionVolume;
    }

    if (s.equals("item.totem.use")) {
      mult *= cfg.totemVolume;
    }

    if (s.startsWith("weather.") || s.startsWith("entity.lightning_bolt.")) {
      mult *= cfg.weatherVolume;
    }

    if (s.startsWith("block.portal.") || s.startsWith("block.end_portal")
        || s.startsWith("block.beacon.") || s.startsWith("block.conduit.")) {
      mult *= cfg.portalBeaconVolume;
    }

    if (cfg.muteBlocks && cfg.mutedBlocks != null && !cfg.mutedBlocks.isEmpty()) {
      if (isActionSelectedForSound(cfg, s)
          && matchesMutedBlock(cfg.mutedBlocks, s, worldBlockPath)) {
        mult *= cfg.blockVolume;
      }
    }

    return Math.max(0.0f, mult);
  }

  static boolean isActionSelectedForSound(Settings.SoundControl cfg, String soundPath) {
    if (soundPath.endsWith(".step") || soundPath.endsWith(".fall") || soundPath.endsWith(".land")) {
      return cfg.filterStep;
    }
    if (soundPath.endsWith(".break") || soundPath.endsWith(".shatter")) {
      return cfg.filterBreak;
    }
    if (soundPath.endsWith(".place")) {
      return cfg.filterPlace;
    }
    return cfg.filterHit;
  }

  static boolean matchesMutedBlock(
      List<String> mutedBlocks, String soundPath, String worldBlockPath) {
    if (!soundPath.startsWith("block.") && !soundPath.startsWith("liquid.")) {
      return false;
    }
    String cleanBlock = worldBlockPath == null ? "" : normalizeBlockQuery(worldBlockPath);
    for (int i = 0; i < mutedBlocks.size(); i++) {
      String target = mutedBlocks.get(i);
      if (target == null || target.isEmpty()) continue;
      if (!cleanBlock.isEmpty() && blockMatchesTarget(cleanBlock, target)) {
        return true;
      }
      if (soundMatchesTarget(soundPath, target, cleanBlock)) {
        return true;
      }
    }
    return false;
  }

  private static boolean blockMatchesTarget(String actualBlock, String target) {
    if (actualBlock.equals(target)) return true;
    if ("shulker_box".equals(target) && actualBlock.endsWith("shulker_box")) return true;
    if ("glass".equals(target) && actualBlock.contains("glass")) return true;
    if ("anvil".equals(target) && actualBlock.endsWith("anvil")) return true;
    if ("piston".equals(target) && actualBlock.contains("piston")) return true;
    if ("chest".equals(target) && ("chest".equals(actualBlock) || "trapped_chest".equals(actualBlock)))
      return true;
    if ((target.endsWith("_door") || "door".equals(target)) && actualBlock.endsWith("_door"))
      return true;
    if ((target.endsWith("_trapdoor") || "trapdoor".equals(target))
        && actualBlock.endsWith("_trapdoor"))
      return true;
    if ((target.endsWith("_planks") || "planks".equals(target)) && actualBlock.endsWith("_planks"))
      return true;
    if (target.endsWith("_wool") && actualBlock.endsWith("_wool")) return true;
    return false;
  }

  private static boolean soundMatchesTarget(String soundPath, String target, String cleanBlock) {
    if (!soundPath.startsWith("block.") && !soundPath.startsWith("liquid.")) return false;
    if (soundPath.startsWith("block." + target + ".")) return true;
    if ((target.endsWith("_wool") || "wool".equals(target)) && soundPath.startsWith("block.wool."))
      return true;
    if (cleanBlock.isEmpty() && (target.endsWith("_planks") || "planks".equals(target))
        && soundPath.startsWith("block.wood."))
      return true;
    if ("nether_portal".equals(target) && soundPath.startsWith("block.portal.")) return true;
    if ("piston".equals(target) && soundPath.startsWith("block.piston.")) return true;
    if ("sticky_piston".equals(target) && soundPath.startsWith("block.piston.")) return true;
    if ("amethyst_block".equals(target) && soundPath.startsWith("block.amethyst")) return true;
    if ("sculk_sensor".equals(target) && soundPath.startsWith("block.sculk")) return true;
    if ("cobweb".equals(target) && soundPath.startsWith("block.cobweb")) return true;
    if ("water".equals(target)
        && (soundPath.startsWith("block.water.") || soundPath.startsWith("liquid.water")))
      return true;
    if ("lava".equals(target)
        && (soundPath.startsWith("block.lava.") || soundPath.startsWith("liquid.lava")))
      return true;
    if ((target.endsWith("_door") || "door".equals(target))
        && (soundPath.startsWith("block.wooden_door.") || soundPath.startsWith("block.iron_door.")
            || soundPath.startsWith("block.copper_door.")
            || soundPath.startsWith("block.cherry_wood_door.")
            || soundPath.startsWith("block.bamboo_wood_door.")
            || soundPath.startsWith("block.nether_wood_door."))) {
      return true;
    }
    if ((target.endsWith("_trapdoor") || "trapdoor".equals(target))
        && soundPath.contains("trapdoor.")) {
      return true;
    }
    return false;
  }

  public static boolean isTntCountdownTick(int fuseTicks) {
    return fuseTicks == 30 || fuseTicks == 22 || fuseTicks == 15 || fuseTicks == 10
        || fuseTicks == 6 || fuseTicks == 3 || fuseTicks == 1;
  }

  public static float tntCountdownPitch(int fuseTicks) {
    float t = 1.0f - Math.max(0.0f, Math.min(1.0f, fuseTicks / 30.0f));
    return 1.10f + t * 0.85f;
  }

  public static boolean matchesChatMention(
      String rawMessage, String ownNick, String customKeywordsCsv) {
    if (rawMessage == null || rawMessage.isEmpty()) return false;
    String clean = stripFormatting(rawMessage).trim();
    if (clean.isEmpty()) return false;

    String body = clean;
    if (ownNick != null && !ownNick.isEmpty()) {
      int sep = findChatAuthorSeparator(clean);
      if (sep > 0) {
        String header = clean.substring(0, sep);
        if (containsWholeTokenIgnoreCase(header, ownNick)
            && !header.toLowerCase(Locale.ROOT).contains("->")
            && !header.toLowerCase(Locale.ROOT).contains("вам")) {
          return false;
        }
        body = clean.substring(sep + 1).trim();
      }
      if (containsWholeTokenIgnoreCase(body, ownNick)) {
        return true;
      }
    }

    if (customKeywordsCsv != null && !customKeywordsCsv.trim().isEmpty()) {
      String[] parts = customKeywordsCsv.split("[,;]+");
      String lowerBody = body.toLowerCase(Locale.ROOT);
      for (int i = 0; i < parts.length; i++) {
        String kw = parts[i].trim().toLowerCase(Locale.ROOT);
        if (kw.length() >= 2 && lowerBody.contains(kw)) {
          return true;
        }
      }
    }
    return false;
  }

  private static int findChatAuthorSeparator(String clean) {
    int limit = Math.min(clean.length(), 48);
    for (int i = 0; i < limit; i++) {
      char c = clean.charAt(i);
      if (c == '>' || c == ':' || c == '»' || c == '➝') return i;
    }
    return -1;
  }

  static boolean containsWholeTokenIgnoreCase(String text, String token) {
    if (text == null || token == null || token.isEmpty()) return false;
    String lowerText = text.toLowerCase(Locale.ROOT);
    String lowerToken = token.toLowerCase(Locale.ROOT);
    int idx = 0;
    while ((idx = lowerText.indexOf(lowerToken, idx)) >= 0) {
      boolean startOk = idx == 0 || !isNickChar(lowerText.charAt(idx - 1));
      int end = idx + lowerToken.length();
      boolean endOk = end >= lowerText.length() || !isNickChar(lowerText.charAt(end));
      if (startOk && endOk) return true;
      idx = idx + 1;
    }
    return false;
  }

  private static boolean isNickChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_'
        || (c >= 'а' && c <= 'я') || c == 'ё';
  }

  public static String stripFormatting(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '§' && i + 1 < s.length()) {
        i++;
        continue;
      }
      out.append(c);
    }
    return out.toString();
  }

  private static String normalizeSoundPath(String soundId) {
    String s = soundId.trim().toLowerCase(Locale.ROOT);
    int colon = s.indexOf(':');
    return colon >= 0 ? s.substring(colon + 1) : s;
  }
}
