package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import org.junit.jupiter.api.Test;

class SoundControlLogicTest {
  @Test
  void normalizesRussianAliasesAndMinecraftIdsCleanly() {
    assertEquals("obsidian", SoundControlLogic.normalizeBlockQuery("Обсидиан"));
    assertEquals("note_block", SoundControlLogic.normalizeBlockQuery("  нотный блок "));
    assertEquals("ender_chest", SoundControlLogic.normalizeBlockQuery("minecraft:ender_chest"));
    assertEquals("shulker_box", SoundControlLogic.normalizeBlockQuery("шалкер"));
    assertEquals("grass_block", SoundControlLogic.normalizeBlockQuery("трава"));
    assertEquals("deepslate_bricks", SoundControlLogic.normalizeBlockQuery("deepslate-bricks"));
  }

  @Test
  void mutesOrScalesChosenBlockSoundsByWorldBlockOrSoundFamilyAndActionFilter() {
    Settings.SoundControl cfg = new Settings.SoundControl();
    cfg.enabled = true;
    cfg.muteBlocks = true;
    cfg.blockVolume = 0.0f;
    cfg.mutedBlocks.add("obsidian");
    cfg.mutedBlocks.add("shulker_box");
    cfg.mutedBlocks.add("note_block");

    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.stone.break", "obsidian"),
        0.001f);
    assertEquals(1.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.stone.break", "stone"),
        0.001f);

    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(
            cfg, "minecraft:block.shulker_box.open", "red_shulker_box"),
        0.001f);

    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.note_block.pling", ""),
        0.001f);

    cfg.filterStep = false;
    assertEquals(1.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.stone.step", "obsidian"),
        0.001f);
    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.stone.break", "obsidian"),
        0.001f);

    cfg.blockVolume = 0.25f;
    assertEquals(0.25f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.stone.break", "obsidian"),
        0.001f);

    assertEquals(1.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:entity.player.attack.crit", "obsidian"),
        0.001f);
    assertEquals(1.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:item.totem.use", "obsidian"),
        0.001f);

    cfg.blockVolume = 0.0f;
    cfg.mutedBlocks.add("white_wool");
    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:block.wool.break", ""),
        0.001f);
  }

  @Test
  void replacesCustomTntFuseAndExplosionSoundsAndAcceleratesCountdownTicks() {
    Settings.SoundControl cfg = new Settings.SoundControl();
    cfg.enabled = true;
    cfg.customTnt = true;
    cfg.tntFusePreset = "bell";
    cfg.tntExplodePreset = "warden_sonic";
    cfg.tntVolume = 1.4f;
    cfg.tntPitch = 1.2f;

    SoundControlLogic.SoundReplacement fuse =
        SoundControlLogic.resolveCustomTntReplacement(cfg, "minecraft:entity.tnt.primed");
    assertNotNull(fuse);
    assertEquals("minecraft:block.bell.use", fuse.soundId);
    assertEquals(1.4f, fuse.volume, 0.001f);

    SoundControlLogic.SoundReplacement explode =
        SoundControlLogic.resolveCustomTntReplacement(cfg, "minecraft:entity.generic.explode");
    assertNotNull(explode);
    assertEquals("minecraft:entity.warden.sonic_boom", explode.soundId);

    cfg.tntExplodePreset = "mute";
    assertNull(
        SoundControlLogic.resolveCustomTntReplacement(cfg, "minecraft:entity.generic.explode"));
    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:entity.generic.explode", ""),
        0.001f);

    assertTrue(SoundControlLogic.isTntCountdownTick(30));
    assertTrue(SoundControlLogic.isTntCountdownTick(10));
    assertTrue(SoundControlLogic.isTntCountdownTick(1));
    assertFalse(SoundControlLogic.isTntCountdownTick(45));
    assertTrue(SoundControlLogic.tntCountdownPitch(1) > SoundControlLogic.tntCountdownPitch(30));
  }

  @Test
  void detectsChatMentionsWhileIgnoringOwnSentMessages() {
    assertTrue(SoundControlLogic.matchesChatMention(
        "§7[Игрок] §fAlex: привет NemoCC пошли на маяк", "NemoCC", ""));
    assertFalse(SoundControlLogic.matchesChatMention(
        "§7[Игрок] §fNemoCC: всем привет", "NemoCC", ""));
    assertFalse(SoundControlLogic.matchesChatMention("<NemoCC> продам трапки", "NemoCC", ""));
    assertTrue(SoundControlLogic.matchesChatMention(
        "§d[ЛС] Alex -> NemoCC: го дуэль", "NemoCC", ""));
    assertTrue(SoundControlLogic.matchesChatMention(
        "[Клан] Собираем пати на вулкан!", "NemoCC", "вулкан, аирдроп"));
    assertFalse(SoundControlLogic.matchesChatMention(
        "<Alex> привет NemoCCPro", "NemoCC", ""));
  }

  @Test
  void routesAndDecodesCustomTntMp3WithId3HeaderAndSilencesVanillaTnt() throws Exception {
    Settings.SoundControl cfg = new Settings.SoundControl();
    cfg.enabled = true;
    cfg.customTnt = true;
    cfg.tntFusePreset = "mp3";
    cfg.tntFuseMp3 = "fuse.mp3";
    cfg.tntExplodePreset = "mp3";
    cfg.tntExplodeMp3 = "boom.mp3";
    cfg.tntVolume = 1.25f;
    cfg.tntPitch = 1.1f;

    SoundControlLogic.Mp3Playback fuseMp3 =
        SoundControlLogic.resolveCustomTntMp3(cfg, "minecraft:entity.tnt.primed");
    assertNotNull(fuseMp3);
    assertEquals("fuse.mp3", fuseMp3.filePath);
    assertFalse(fuseMp3.explosion);
    assertNull(SoundControlLogic.resolveCustomTntReplacement(cfg, "minecraft:entity.tnt.primed"));
    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:entity.tnt.primed", ""), 0.001f);

    SoundControlLogic.Mp3Playback explodeMp3 =
        SoundControlLogic.resolveCustomTntMp3(cfg, "minecraft:entity.generic.explode");
    assertNotNull(explodeMp3);
    assertEquals("boom.mp3", explodeMp3.filePath);
    assertTrue(explodeMp3.explosion);
    assertNull(
        SoundControlLogic.resolveCustomTntReplacement(cfg, "minecraft:entity.generic.explode"));
    assertEquals(0.0f,
        SoundControlLogic.computeVolumeMultiplier(cfg, "minecraft:entity.generic.explode", ""),
        0.001f);

    byte[] id3 = new byte[22];
    id3[0] = 'I';
    id3[1] = 'D';
    id3[2] = '3';
    id3[3] = 3;
    id3[9] = 12;
    byte[] frame = new byte[417];
    frame[0] = (byte) 0xFF;
    frame[1] = (byte) 0xFB;
    frame[2] = (byte) 0x90;
    frame[3] = (byte) 0x00;
    byte[] mp3 = new byte[id3.length + frame.length * 2];
    System.arraycopy(id3, 0, mp3, 0, id3.length);
    System.arraycopy(frame, 0, mp3, id3.length, frame.length);
    System.arraycopy(frame, 0, mp3, id3.length + frame.length, frame.length);

    assertEquals(22, CustomAudioPlayer.skipId3v2Header(mp3));
    CustomAudioPlayer.DecodedClip clip = CustomAudioPlayer.decodeBytes(mp3, "boom.mp3");
    assertNotNull(clip);
    assertEquals(44100, clip.sampleRate);
    assertEquals(2, clip.channels);
    assertTrue(clip.frameCount >= 1152, "Decoded MP3 must yield real PCM frames: " + clip.frameCount);

    java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("tv-mp3-test");
    try {
      java.nio.file.Path src = dir.resolve("my boom!.mp3");
      java.nio.file.Files.write(src, mp3);
      CustomAudioPlayer player = new CustomAudioPlayer();
      try {
        String imported = player.importAudioFile(dir.resolve("sounds"), src.toString(), "tnt_explode");
        assertTrue(imported.endsWith(".mp3"));
        assertTrue(java.nio.file.Files.isRegularFile(dir.resolve("sounds").resolve(imported)));
        assertNotNull(player.loadClip(dir.resolve("sounds"), imported));
      } finally {
        player.shutdown();
      }
    } finally {
      java.nio.file.Files.walk(dir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(p -> {
            try {
              java.nio.file.Files.deleteIfExists(p);
            } catch (Exception ignored) {
            }
          });
    }
  }
}
