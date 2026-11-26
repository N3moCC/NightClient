package dev.nightclient.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.nightclient.runtime.bench.FrameTelemetryRecorder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
@@BENCH_EXTRA_IMPORTS@@

public final class BenchmarkDirector {
  private BenchmarkDirector() {}

  private static final class PassSpec {
    final String optMode;
    final int warmupFrames;
    final int measureFrames;
    final Path resultPath;
    final Path screenshotsDir;
    final Path jfrPath;
    final boolean captureScreenshots;

    PassSpec(String optMode, int warmupFrames, int measureFrames, Path resultPath,
        Path screenshotsDir, Path jfrPath, boolean captureScreenshots) {
      this.optMode = optMode;
      this.warmupFrames = warmupFrames;
      this.measureFrames = measureFrames;
      this.resultPath = resultPath;
      this.screenshotsDir = screenshotsDir;
      this.jfrPath = jfrPath;
      this.captureScreenshots = captureScreenshots;
    }
  }

  private static final FrameTelemetryRecorder RECORDER = new FrameTelemetryRecorder();
  private static final Set<Integer> SCREENSHOT_FRAMES = new HashSet<Integer>();
  private static final List<PassSpec> PASSES = new ArrayList<PassSpec>();
  private static int passIndex;

  private static volatile boolean automated;
  private static volatile boolean manualRunning;
  private static boolean initialized;
  private static boolean optionsApplied;
  private static boolean worldRequested;
  private static boolean sceneBuildDispatched;
  private static volatile boolean sceneBuildDone;
  private static boolean sceneBuilt;
  private static boolean completed;

  private static String sceneId = "S01";
  private static String worldType = "flat";
  private static long worldSeed = 100200300L;
  private static int renderDistance = 12;
  private static int warmupFrames = 180;
  private static int measureFrames = 480;
  private static String optMode = "baseline";
  private static Path resultPath;
  private static Path screenshotsDir;
  private static Path jfrPath;

  private static int worldWaitTicks;
  private static int postBuildSettleFrames;
  private static long postBuildStartNanos;
  private static boolean postBuildWarmedCandidate;
  private static int measuredFrameCursor;
  private static int postScreenshotStage;
  private static long postScreenshotStartNanos;
  private static long recordStartNanos;
  private static long measureStartNanos;
  private static long lastDynamicTickNanos;
  private static int dynamicTickIndex;

  static {
    String specProp = System.getProperty("nightclient.bench.spec", "").trim();
    if (!specProp.isEmpty()) {
      loadSpec(Paths.get(specProp));
    }
  }

  private static void loadSpec(Path path) {
    try {
      String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
      sceneId = str(obj, "sceneId", "S01");
      worldType = str(obj, "worldType", "flat");
      worldSeed = Long.parseLong(str(obj, "seed", "100200300"));
      renderDistance = integer(obj, "renderDistance", 12);
      warmupFrames = integer(obj, "warmupFrames", 180);
      measureFrames = integer(obj, "measureFrames", 480);
      optMode = str(obj, "optMode", "baseline");
      resultPath = Paths.get(str(obj, "resultPath", "ncbench_result.json"));
      screenshotsDir = Paths.get(str(obj, "screenshotsDir", "ncbench_shots"));
      String jfr = str(obj, "jfrPath", "");
      jfrPath = jfr.isEmpty() ? null : Paths.get(jfr);
      SCREENSHOT_FRAMES.clear();
      if (obj.has("screenshotFrames") && obj.get("screenshotFrames").isJsonArray()) {
        JsonArray arr = obj.getAsJsonArray("screenshotFrames");
        for (JsonElement el : arr) {
          SCREENSHOT_FRAMES.add(el.getAsInt());
        }
      }
      PASSES.clear();
      passIndex = 0;
      postBuildWarmedCandidate = false;
      if (obj.has("passes") && obj.get("passes").isJsonArray()) {
        JsonArray parr = obj.getAsJsonArray("passes");
        for (JsonElement pel : parr) {
          JsonObject po = pel.getAsJsonObject();
          String pm = str(po, "optMode", "baseline");
          int pw = integer(po, "warmupFrames", warmupFrames);
          int pmf = integer(po, "measureFrames", measureFrames);
          Path pr = Paths.get(str(po, "resultPath", "ncbench_result.json"));
          String ps = str(po, "screenshotsDir", "");
          Path psd = ps.isEmpty() ? null : Paths.get(ps);
          String pj = str(po, "jfrPath", "");
          Path pjp = pj.isEmpty() ? null : Paths.get(pj);
          boolean cap = po.has("captureScreenshots") && po.get("captureScreenshots").getAsBoolean();
          PASSES.add(new PassSpec(pm, pw, pmf, pr, psd, pjp, cap));
        }
      }
      automated = true;
      initialized = true;
      System.out.println("[NCBench] Loaded automated spec: scene=" + sceneId + " mode=" + optMode + " passes=" + PASSES.size());
    } catch (Throwable t) {
      System.err.println("[NCBench] Failed to load bench spec " + path + ": " + t);
      automated = false;
    }
  }

  public static boolean active() {
    return automated || manualRunning;
  }

  public static boolean isRecording() {
    return RECORDER.isRecording();
  }

  public static FrameTelemetryRecorder recorder() {
    return RECORDER;
  }

  public static boolean handleCommand(String command) {
    if (command == null) return false;
    String trimmed = command.trim();
    if (!trimmed.startsWith("/ncbench")) return false;
    Minecraft mc = Minecraft.getInstance();
    String[] parts = trimmed.split("\\s+");
    String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "help";
    if ("start".equals(sub)) {
      String id = parts.length > 2 ? parts[2].toUpperCase(Locale.ROOT) : "MANUAL";
      int frames = parts.length > 3 ? parseInt(parts[3], 480) : 480;
      sceneId = id;
      measureFrames = frames;
      warmupFrames = 60;
      manualRunning = true;
      completed = false;
      sceneBuilt = true;
      measuredFrameCursor = 0;
      resultPath = mc.gameDirectory.toPath().resolve("ncbench-" + id + ".json");
      screenshotsDir = mc.gameDirectory.toPath().resolve("ncbench-shots");
      jfrPath = mc.gameDirectory.toPath().resolve("ncbench-" + id + ".jfr");
      RECORDER.start(sceneId, OptimizationBridge.activeModeLabel(), warmupFrames, jfrPath);
      chat(mc, "[NCBench] Started recording " + frames + " frames (" + id + ", mode=" + OptimizationBridge.activeModeLabel() + ")");
      return true;
    }
    if ("stop".equals(sub)) {
      finishManual(mc);
      return true;
    }
    if ("build".equals(sub) && parts.length > 2) {
      String id = parts[2].toUpperCase(Locale.ROOT);
      MinecraftServer server = mc.getSingleplayerServer();
      if (server != null) {
        server.execute(() -> buildStressScene(server, id));
        chat(mc, "[NCBench] Building stress scene " + id + "...");
      } else {
        chat(mc, "[NCBench] Stress scene builder requires singleplayer integrated server.");
      }
      return true;
    }
    if ("mode".equals(sub) && parts.length > 2) {
      OptimizationBridge.setSystemModeOverride(parts[2]);
      chat(mc, "[NCBench] Switched optimization override to: " + parts[2]);
      return true;
    }
    chat(mc, "[NCBench] Usage: /ncbench start [sceneId] [frames] | stop | build <S01..S10> | mode <baseline|lossless|performance|competitive|only:OPT_XXX>");
    return true;
  }

  public static void onFrameStart(Minecraft mc) {
    if (completed) return;
    if (automated) {
      try {
        @@BENCH_KEEP_AWAKE@@
      } catch (Throwable ignored) {
      }
    }
    if (automated && !optionsApplied && mc.options != null) {
      optionsApplied = true;
      try {
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(Math.max(4, Math.min(32, renderDistance)));
        mc.options.pauseOnLostFocus = false;
      } catch (Throwable ignored) {
      }
    }

    if (automated && !worldRequested && mc.level == null && !GameBridge.isLoadingOverlay()) {
      Screen scr = @@BENCH_SCREEN@@;
      if (scr != null) {
        String sname = scr.getClass().getSimpleName();
        if (sname.contains("Title") || sname.contains("Onboarding") || sname.contains("Accessibility")) {
          worldRequested = true;
          launchOrCreateWorld(mc);
          return;
        }
      }
    }

    if (mc.level == null || mc.player == null) return;

    if (automated && !sceneBuilt) {
      MinecraftServer server = mc.getSingleplayerServer();
      if (server == null) return;
      worldWaitTicks++;
      if (!sceneBuildDispatched && worldWaitTicks >= 40) {
        sceneBuildDispatched = true;
        server.execute(() -> {
          try {
            buildStressScene(server, sceneId);
          } finally {
            sceneBuildDone = true;
          }
        });
      }
      if (sceneBuildDone) {
        if (postBuildStartNanos == 0L) {
          postBuildStartNanos = System.nanoTime();
          postBuildWarmedCandidate = false;
          OptimizationBridge.setSystemModeOverride("baseline");
        }
        driveDeterministicCamera(mc, postBuildSettleFrames % Math.max(1, measureFrames));
        postBuildSettleFrames++;
        long settleMs = (System.nanoTime() - postBuildStartNanos) / 1_000_000L;
        if (settleMs >= 1_500L) {
          if (!PASSES.isEmpty()) {
            String warmCand = null;
            for (int wi = 0; wi < PASSES.size(); wi++) {
              String wm = PASSES.get(wi).optMode;
              if (wm != null && !"baseline".equalsIgnoreCase(wm)) {
                warmCand = wm;
                break;
              }
            }
            if (warmCand != null && !warmCand.equals(optMode)) {
              optMode = warmCand;
              OptimizationBridge.setSystemModeOverride(warmCand);
            }
          } else if (!postBuildWarmedCandidate
              && optMode != null
              && !"baseline".equalsIgnoreCase(optMode)) {
            postBuildWarmedCandidate = true;
            OptimizationBridge.setSystemModeOverride(optMode);
          }
        }
        long minSettleMs = "S11".equals(sceneId) ? 12_000L : 3_200L;
        if (postBuildSettleFrames >= 240 && settleMs >= minSettleMs) {
          sceneBuilt = true;
          startNextPass(mc);
        }
      }
      return;
    }

    if (postScreenshotStage > 0) {
      int keyStep = postScreenshotStage <= 3 ? (measureFrames / 3) : ((measureFrames * 2) / 3);
      driveDeterministicCamera(mc, keyStep);
      return;
    }

    if (RECORDER.isRecording()) {
      if (automated) {
        driveDeterministicCamera(mc, measuredFrameCursor);
        driveDynamicLoad(mc);
      }
      RECORDER.onFrameBegin();
    }
  }

  public static void onFrameEnd(Minecraft mc, long gpuNanos) {
    if (completed) return;
    if (postScreenshotStage > 0) {
      if ("S09".equals(sceneId) && postScreenshotStage == 1
          && (System.nanoTime() - postScreenshotStartNanos) < 150_000_000L) {
        return;
      }
      if (postScreenshotStage == 3 || postScreenshotStage == 6) {
        captureScreenshot(mc, postScreenshotStage == 3 ? 1 : 2);
      }
      postScreenshotStage++;
      if (postScreenshotStage > 7) {
        postScreenshotStage = 0;
        advanceOrStop(mc);
      }
      return;
    }
    if (!RECORDER.isRecording()) return;
    RECORDER.onFrameEnd(gpuNanos);
    if (!RECORDER.isWarmingUp()) {
      if (measureStartNanos == 0L) {
        measureStartNanos = System.nanoTime();
      }
      measuredFrameCursor++;
      long elapsedMs = (System.nanoTime() - measureStartNanos) / 1_000_000L;
      boolean enoughFrames = (RECORDER.sampleCount() >= measureFrames && elapsedMs >= 1_500L)
          || RECORDER.sampleCount() >= 4000
          || (RECORDER.sampleCount() >= 120 && elapsedMs >= 25_000L);
      if (enoughFrames) {
        if (automated) {
          finishAutomated(mc);
        } else {
          finishManual(mc);
        }
      }
    }
  }

  private static void finishManual(Minecraft mc) {
    manualRunning = false;
    FrameTelemetryRecorder.Summary sum = RECORDER.stop();
    try {
      if (resultPath != null) sum.writeJson(resultPath, true);
    } catch (Throwable ignored) {
    }
    chat(mc, String.format(Locale.ROOT,
        "[NCBench] Done (%d frames): Avg=%.1f FPS | 1%%=%.1f | 0.1%%=%.1f | P99.9=%.2fms | Max=%.2fms | Alloc=%.1fMB/s",
        sum.frameCount, sum.avgFps, sum.low1PercentFps, sum.low01PercentFps,
        sum.p999FrameMs, sum.maxSpikeMs, sum.allocMbPerSec));
  }

  private static void finishAutomated(Minecraft mc) {
    FrameTelemetryRecorder.Summary sum = RECORDER.stop();
    try {
      if (resultPath != null) sum.writeJson(resultPath, true);
      System.out.println(String.format(Locale.ROOT,
          "[NCBench] FINISHED %s (%s): Avg=%.2f FPS | 1%%=%.2f | 0.1%%=%.2f | MaxSpike=%.2fms | GPU=%.2fms | Alloc=%.1fMB/s",
          sceneId, optMode, sum.avgFps, sum.low1PercentFps, sum.low01PercentFps,
          sum.maxSpikeMs, sum.avgGpuMs, sum.allocMbPerSec));
    } catch (Throwable t) {
      System.err.println("[NCBench] Failed writing telemetry JSON: " + t);
    }
    boolean captureNow = !PASSES.isEmpty()
        ? (passIndex < PASSES.size() && PASSES.get(passIndex).captureScreenshots && screenshotsDir != null)
        : (!SCREENSHOT_FRAMES.isEmpty() && screenshotsDir != null);
    if (captureNow) {
      if ("S09".equals(sceneId)) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
          server.execute(() -> {
            runCmd(server, "/fill -4 65 6 4 68 10 minecraft:stone");
            runCmd(server, "/scoreboard players reset * nc_board");
          });
        }
      }
      postScreenshotStartNanos = System.nanoTime();
      postScreenshotStage = 1;
    } else {
      advanceOrStop(mc);
    }
  }

  private static void advanceOrStop(Minecraft mc) {
    if (!PASSES.isEmpty() && passIndex + 1 < PASSES.size()) {
      passIndex++;
      startNextPass(mc);
    } else {
      completed = true;
      mc.stop();
    }
  }

  private static void startNextPass(Minecraft mc) {
    if (!PASSES.isEmpty() && passIndex < PASSES.size()) {
      String prevMode = optMode;
      PassSpec p = PASSES.get(passIndex);
      optMode = p.optMode;
      warmupFrames = p.warmupFrames;
      measureFrames = p.measureFrames;
      resultPath = p.resultPath;
      screenshotsDir = p.screenshotsDir;
      jfrPath = p.jfrPath;
      OptimizationBridge.setSystemModeOverride(optMode);
      if (("S01".equals(sceneId) || "S11".equals(sceneId) || "S12".equals(sceneId))
          && mc.levelRenderer != null
          && (needsTerrainRemesh(prevMode) != needsTerrainRemesh(optMode))) {
        @@BENCH_ALL_CHANGED@@
        warmupFrames = Math.max(warmupFrames, "S11".equals(sceneId) ? 720 : 320);
      }
      if ("S09".equals(sceneId)) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
          server.execute(() -> runCmd(server, "/scoreboard players reset * nc_board"));
        }
      } else if ("S05".equals(sceneId) && mc.particleEngine != null && mc.level != null) {
        mc.particleEngine.setLevel(mc.level);
      }
    } else if (PASSES.isEmpty()) {
      OptimizationBridge.setSystemModeOverride(optMode);
      if (("S01".equals(sceneId) || "S11".equals(sceneId) || "S12".equals(sceneId))
          && mc.levelRenderer != null
          && (needsTerrainRemesh("baseline") != needsTerrainRemesh(optMode))) {
        @@BENCH_ALL_CHANGED@@
        warmupFrames = Math.max(warmupFrames, "S11".equals(sceneId) ? 720 : 320);
      }
    }
    measuredFrameCursor = 0;
    dynamicTickIndex = 0;
    recordStartNanos = System.nanoTime();
    measureStartNanos = 0L;
    lastDynamicTickNanos = recordStartNanos;
    driveDeterministicCamera(mc, 0);
    RECORDER.start(sceneId, optMode, warmupFrames, jfrPath);
    System.out.println("[NCBench] Recording started: scene=" + sceneId + " pass=" + passIndex
        + " mode=" + optMode + " warmup=" + warmupFrames + " measure=" + measureFrames);
  }

  private static boolean needsTerrainRemesh(String mode) {
    if (mode == null || mode.isEmpty()) return false;
    try {
      return dev.nightclient.config.OptimizationFlags.resolve(
          true, "performance", false,
          dev.nightclient.config.OptimizationFlags.BitMask.EMPTY,
          null, null, mode)
          .has(dev.nightclient.config.OptimizationFlags.OPT_011_SMART_LEAVES_FACE_CULL);
    } catch (Throwable ignored) {
      String m = mode.toUpperCase(Locale.ROOT);
      return m.contains("OPT_011")
          || "ALL".equals(m) || "PERFORMANCE".equals(m) || "COMPETITIVE".equals(m);
    }
  }

  private static void captureScreenshot(Minecraft mc, int frameNo) {
    try {
      Files.createDirectories(screenshotsDir);
      RenderTarget target = @@BENCH_MAIN_TARGET@@;
      if (target != null) {
        File dir = screenshotsDir.toFile();
        Screenshot.grab(dir, target, msg -> {});
      }
    } catch (Throwable ignored) {
    }
  }

  private static void launchOrCreateWorld(Minecraft mc) {
    String folder = "nc_bench_" + sceneId.toLowerCase(Locale.ROOT);
    try {
      boolean exists = mc.getLevelSource().levelExists(folder);
      if (exists) {
        System.out.println("[NCBench] Opening existing benchmark world: " + folder);
        @@BENCH_OPEN_WORLD@@
      } else {
        System.out.println("[NCBench] Creating fresh deterministic world: " + folder + " (" + worldType + ")");
        WorldOptions worldOpts = new WorldOptions(worldSeed, false, false);
        boolean flat = "flat".equalsIgnoreCase(worldType);
        LevelSettings settings = @@BENCH_LEVEL_SETTINGS@@;
        @@BENCH_CREATE_FRESH@@
      }
    } catch (Throwable t) {
      System.err.println("[NCBench] World launch error: " + t);
      t.printStackTrace();
      mc.stop();
    }
  }

  private static void driveDeterministicCamera(Minecraft mc, int step) {
    if (mc.player == null) return;
    double t = step / (double) Math.max(1, measureFrames);
    double x = 0.5, y = 72.0, z = -12.0;
    float yaw = 0.0f, pitch = 12.0f;

    if ("S01".equals(sceneId)) {
      x = t * 220.0;
      y = 115.0 + Math.sin(t * Math.PI * 4.0) * 12.0;
      z = Math.cos(t * Math.PI * 2.0) * 40.0;
      yaw = (step >= measureFrames - 40 && step < measureFrames - 20)
          ? (float) ((t * 90.0 + 180.0) % 360.0)
          : (float) (-90.0 + Math.sin(t * Math.PI * 4.0) * 35.0);
      pitch = 16.0f + (float) (Math.sin(t * Math.PI * 6.0) * 8.0);
    } else if ("S02".equals(sceneId)) {
      x = Math.sin(t * Math.PI * 2.0) * 8.0;
      y = 66.0;
      z = -6.0;
      yaw = (float) (Math.sin(t * Math.PI * 2.0) * 28.0);
      pitch = 6.0f;
    } else if ("S03".equals(sceneId)) {
      x = Math.sin(t * Math.PI * 2.0) * 10.0;
      y = 70.0;
      z = -14.0;
      yaw = (float) (Math.sin(t * Math.PI * 2.0) * 25.0);
      pitch = 18.0f;
    } else if ("S04".equals(sceneId) || "S05".equals(sceneId) || "S06".equals(sceneId)
        || "S07".equals(sceneId) || "S08".equals(sceneId) || "S09".equals(sceneId)) {
      x = Math.sin(t * Math.PI * 2.0) * 6.0;
      y = 68.0;
      z = -10.0;
      yaw = (float) (Math.sin(t * Math.PI * 2.0) * 22.0);
      pitch = 14.0f;
    } else if ("S11".equals(sceneId)) {
      x = 0.5;
      y = 92.0;
      z = 0.5;
      yaw = (float) ((t * 360.0) - 180.0);
      pitch = 16.0f + (float) (Math.sin(t * Math.PI * 4.0) * 6.0);
    } else if ("S12".equals(sceneId)) {
      x = Math.sin(t * Math.PI * 2.0) * 8.0;
      y = 69.0;
      z = -14.0;
      yaw = (float) (Math.sin(t * Math.PI * 2.0) * 28.0);
      pitch = 12.0f;
    } else if ("S13".equals(sceneId)) {
      x = 0.5;
      y = 66.0;
      z = -4.0;
      yaw = (float) (Math.sin(t * Math.PI * 2.0) * 20.0);
      pitch = -8.0f;
    } else {
      x = 0.5;
      y = 65.5;
      z = 0.5;
      yaw = (float) (Math.sin(t * Math.PI * 2.0) * 30.0);
      pitch = 0.0f;
    }

    mc.player.setPos(x, y, z);
    mc.player.xo = x;
    mc.player.yo = y;
    mc.player.zo = z;
    mc.player.xOld = x;
    mc.player.yOld = y;
    mc.player.zOld = z;
    mc.player.setDeltaMovement(0.0, 0.0, 0.0);
    mc.player.setYRot(yaw);
    mc.player.setXRot(pitch);
    mc.player.yRotO = yaw;
    mc.player.xRotO = pitch;
  }

  private static void driveDynamicLoad(Minecraft mc) {
    long now = System.nanoTime();
    if (now - lastDynamicTickNanos < 50_000_000L) return;
    lastDynamicTickNanos = now;
    dynamicTickIndex++;
    MinecraftServer server = mc.getSingleplayerServer();
    if (server == null) return;

    if ("S05".equals(sceneId)) {
      server.execute(() -> {
        runCmd(server, "/particle minecraft:campfire_cosy_smoke 0 66 8 6 3 6 0.02 180 force");
        runCmd(server, "/particle minecraft:explosion 0 67 10 5 2 5 0.05 40 force");
        runCmd(server, "/particle minecraft:flame 0 66 6 6 2 6 0.02 120 force");
        if ((dynamicTickIndex & 3) == 0) {
          for (int s = 0; s < 10; s++) {
            runCmd(server, "/playsound minecraft:block.piston.extend master @a " + (s - 5) + " 66 8 0.3 1.0");
          }
        }
      });
    } else if ("S06".equals(sceneId)) {
      int idx = dynamicTickIndex;
      server.execute(() -> {
        runCmd(server, "/scoreboard players set Player_" + (idx % 12) + " nc_board " + (idx * 7 % 999));
      });
    } else if ("S09".equals(sceneId)) {
      int idx = dynamicTickIndex;
      boolean flip = (idx & 1) == 0;
      server.execute(() -> {
        runCmd(server, "/fill -4 65 6 4 68 10 " + (flip ? "minecraft:stone" : "minecraft:glass"));
        runCmd(server, "/scoreboard players set Uniq_" + idx + " nc_board " + idx);
      });
    }
  }

  private static void buildStressScene(MinecraftServer server, String id) {
    runCmd(server, "/gamemode creative @a");
    runCmd(server, "/gamerule commandModificationBlockLimit 250000");
    runCmd(server, "/gamerule doDaylightCycle false");
    runCmd(server, "/gamerule doWeatherCycle false");
    runCmd(server, "/gamerule doMobSpawning false");
    runCmd(server, "/time set 6000");
    runCmd(server, "/weather clear");

    if ("S01".equals(id) || "S11".equals(id)) {
      runCmd(server, "/tp @a 0 " + ("S11".equals(id) ? "92" : "115") + " 0");
      return;
    }

    runCmd(server, "/fill -28 63 -20 28 63 32 minecraft:stone");
    runCmd(server, "/fill -28 64 -20 28 76 32 minecraft:air");
    runCmd(server, "/kill @e[type=!player]");

    if ("S02".equals(id) || "S03".equals(id)) {
      runCmd(server, "/fill -11 64 4 11 67 15 minecraft:chest");
      runCmd(server, "/fill -11 68 4 11 68 14 minecraft:oak_sign{front_text:{messages:['\"NightClient\"','\"Bench Sign\"','\"Line Three\"','\"FPS Stress\"']}}");
      runCmd(server, "/fill -11 69 4 11 69 6 minecraft:white_banner");
      runCmd(server, "/fill -11 64 17 11 69 17 minecraft:stone");
      for (int i = 0; i < 60; i++) {
        int gx = -10 + (i % 15) * 1;
        int gy = 65 + (i / 15);
        runCmd(server, "/summon minecraft:item_frame " + gx + " " + gy + " 16 {Facing:2b,Fixed:1b,Item:{id:\"minecraft:diamond_sword\",@@ITEM_COUNT_NBT@@}}");
        runCmd(server, "/summon minecraft:armor_stand " + (-10 + (i % 10) * 2) + " 70 " + (5 + (i / 10) * 2) + " {NoGravity:1b}");
        runCmd(server, "/summon minecraft:text_display " + (-10 + (i % 10) * 2) + " 72 " + (5 + (i / 10) * 2) + " {text:'\"Bench Display #" + i + "\"'}");
      }
      if ("S02".equals(id)) {
        runCmd(server, "/fill -20 64 -1 20 75 2 minecraft:stone");
      }
    } else if ("S04".equals(id)) {
      runCmd(server, "/fill -12 64 2 12 67 22 minecraft:glass outline");
      for (int i = 0; i < 120; i++) {
        int gx = -10 + (i % 12) * 2;
        int gz = 4 + (i / 12) * 2;
        runCmd(server, "/summon minecraft:zombie " + gx + " 65 " + gz + " {NoAI:1b,PersistenceRequired:1b}");
        runCmd(server, "/summon minecraft:villager " + gx + " 65 " + (gz + 1) + " {NoAI:1b,PersistenceRequired:1b}");
      }
      String[] dropItems = {
          "minecraft:cobblestone", "minecraft:stone", "minecraft:dirt", "minecraft:oak_planks",
          "minecraft:diamond", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald",
          "minecraft:coal", "minecraft:redstone"
      };
      for (int i = 0; i < 400; i++) {
        int gx = i < 240 ? (-2 + (i % 4)) : (-10 + (i % 20));
        int gz = i < 240 ? (6 + ((i / 4) % 4)) : (4 + ((i / 20) % 16));
        String item = dropItems[i % dropItems.length];
        runCmd(server, "/summon minecraft:item " + gx + " 65 " + gz + " {Age:-32768s,PickupDelay:32767,Item:{id:\"" + item + "\",@@ITEM_COUNT_NBT@@}}");
      }
      for (int i = 0; i < 200; i++) {
        int gx = -8 + (i % 16);
        int gz = 5 + ((i / 16) % 12);
        runCmd(server, "/summon minecraft:experience_orb " + gx + " 65 " + gz);
      }
      for (int i = 0; i < 60; i++) {
        runCmd(server, "/summon minecraft:arrow " + (-4 + (i % 9)) + " 64.2 " + (6 + (i / 9)) + " {inGround:1b}");
      }
    } else if ("S05".equals(id)) {
      runCmd(server, "/weather rain");
      runCmd(server, "/fill -8 64 4 8 64 16 minecraft:campfire");
    } else if ("S06".equals(id)) {
      runCmd(server, "/scoreboard objectives add nc_board dummy \"NightClient Bench\"");
      runCmd(server, "/scoreboard objectives setdisplay sidebar nc_board");
      for (int i = 0; i < 15; i++) {
        runCmd(server, "/scoreboard players set Player_" + i + " nc_board " + (100 - i));
      }
      for (int i = 0; i < 100; i++) {
        int gx = -12 + (i % 10) * 2;
        int gz = 4 + (i / 10) * 2;
        runCmd(server, "/summon minecraft:armor_stand " + gx + " 64 " + gz + " {CustomName:'\"Player_" + i + "\"',CustomNameVisible:1b,NoGravity:1b}");
      }
    } else if ("S07".equals(id)) {
      runCmd(server, "/fill -10 64 4 10 64 14 minecraft:hopper");
      runCmd(server, "/fill -10 65 4 10 65 14 minecraft:redstone_wire");
      for (int i = 0; i < 150; i++) {
        int gx = -10 + (i % 15);
        int gz = 5 + (i / 15);
        runCmd(server, "/summon minecraft:sheep " + gx + " 66 " + gz);
      }
    } else if ("S08".equals(id)) {
      runCmd(server, "/fill -10 64 6 10 64 16 minecraft:chest");
      for (int i = 0; i < 180; i++) {
        int gx = -14 + (i % 18) * 2;
        int gz = 4 + (i / 18) * 2;
        runCmd(server, "/summon minecraft:zombie " + gx + " 65 " + gz + " {NoAI:1b,PersistenceRequired:1b}");
      }
    } else if ("S09".equals(id)) {
      runCmd(server, "/scoreboard objectives add nc_board dummy \"Cache Thrash\"");
      runCmd(server, "/scoreboard objectives setdisplay sidebar nc_board");
    } else if ("S10".equals(id)) {
      runCmd(server, "/fill -6 64 -6 6 70 6 minecraft:stone hollow");
    } else if ("S12".equals(id)) {
      runCmd(server, "/fill -24 63 -18 24 63 28 minecraft:quartz_block");
      runCmd(server, "/fill -20 64 20 20 74 22 minecraft:stone_bricks");
      runCmd(server, "/fill -18 65 18 18 72 19 minecraft:glass");
      runCmd(server, "/fill -22 64 0 -18 71 18 minecraft:oak_leaves[persistent=true]");
      runCmd(server, "/fill 18 64 0 22 71 18 minecraft:oak_leaves[persistent=true]");
      runCmd(server, "/fill -10 64 14 10 65 16 minecraft:chest");
      runCmd(server, "/fill -10 66 14 10 66 15 minecraft:oak_sign{front_text:{messages:['\"Hub Warp\"','\"PvP Arena\"','\"Online: 412\"','\"Click NPC\"']}}");
      runCmd(server, "/fill -10 67 14 10 67 14 minecraft:white_banner");
      for (int i = 0; i < 80; i++) {
        int gx = -16 + (i % 16) * 2;
        int gy = 67 + ((i / 16) % 5);
        int gz = 2 + (i / 16) * 3;
        runCmd(server, "/summon minecraft:text_display " + gx + " " + gy + " " + gz
            + " {billboard:\"center\",text:'\"Hub Info #" + i + " | Top Players\"'}");
      }
      for (int i = 0; i < 48; i++) {
        int gx = -15 + (i % 12) * 3;
        int gz = 1 + (i / 12) * 3;
        runCmd(server, "/summon minecraft:armor_stand " + gx + " 64 " + gz
            + " {CustomName:'\"NPC_" + i + "\"',CustomNameVisible:1b,NoGravity:1b,ShowArms:1b}");
      }
      runCmd(server, "/scoreboard objectives add nc_board dummy \"NightClient Hub\"");
      runCmd(server, "/scoreboard objectives setdisplay sidebar nc_board");
      for (int i = 0; i < 15; i++) {
        runCmd(server, "/scoreboard players set HubLine_" + i + " nc_board " + (100 - i));
      }
      runCmd(server, "/bossbar add nc:hub \"NightClient Hub Event\"");
      runCmd(server, "/bossbar set nc:hub players @a");
      runCmd(server, "/bossbar set nc:hub value 75");
    } else if ("S13".equals(id)) {
      runCmd(server, "/scoreboard objectives add nc_board dummy \"NightClient HUD\"");
      runCmd(server, "/scoreboard objectives setdisplay sidebar nc_board");
      for (int i = 0; i < 15; i++) {
        runCmd(server, "/scoreboard players set Stat_" + i + " nc_board " + (50 - i));
      }
      runCmd(server, "/bossbar add nc:sky \"NightClient Low-Load\"");
      runCmd(server, "/bossbar set nc:sky players @a");
      runCmd(server, "/bossbar set nc:sky value 60");
    }
  }

  private static void runCmd(MinecraftServer server, String cmd) {
    try {
      Commands commands = server.getCommands();
      CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
      commands.performPrefixedCommand(src, cmd);
    } catch (Throwable ignored) {
    }
  }

  private static void chat(Minecraft mc, String msg) {
    try {
      if (mc.player != null) {
        @@BENCH_CHAT@@
      }
    } catch (Throwable ignored) {
    }
    System.out.println(msg);
  }

  private static String str(JsonObject o, String key, String def) {
    return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
  }

  private static int integer(JsonObject o, String key, int def) {
    return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : def;
  }

  private static int parseInt(String s, int def) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
