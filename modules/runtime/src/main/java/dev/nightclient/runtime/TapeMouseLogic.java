package dev.nightclient.runtime;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;

public final class TapeMouseLogic {
  private final Random rng = new Random();
  private int cooldownTicks = 0;
  private boolean holding = false;

  public static final class CommandOutcome {
    public final boolean intercepted;
    public final boolean settingsChanged;
    public final String clientMessage;

    public CommandOutcome(boolean intercepted, boolean settingsChanged, String clientMessage) {
      this.intercepted = intercepted;
      this.settingsChanged = settingsChanged;
      this.clientMessage = clientMessage;
    }
  }

  public static final class TickStep {
    public static final TickStep IDLE = new TickStep(false, false, false);
    public final boolean holdDown;
    public final boolean triggerClick;
    public final boolean releaseHold;

    public TickStep(boolean holdDown, boolean triggerClick, boolean releaseHold) {
      this.holdDown = holdDown;
      this.triggerClick = triggerClick;
      this.releaseHold = releaseHold;
    }
  }

  public void reset() {
    cooldownTicks = 0;
    holding = false;
  }

  public boolean isHolding() {
    return holding;
  }

  public TickStep step(Settings.TapeMouse cfg, boolean inWorld, boolean screenOpen, boolean tookDamage) {
    if (cfg == null || !cfg.enabled || !inWorld || FeaturePolicy.isBlocked("tapeMouse")) {
      boolean rel = holding;
      holding = false;
      cooldownTicks = 0;
      return rel ? new TickStep(false, false, true) : TickStep.IDLE;
    }

    if (cfg.stopOnDamage && tookDamage) {
      cfg.enabled = false;
      boolean rel = holding;
      holding = false;
      cooldownTicks = 0;
      return rel ? new TickStep(false, false, true) : TickStep.IDLE;
    }

    if (cfg.pauseInScreens && screenOpen) {
      boolean rel = holding;
      holding = false;
      return rel ? new TickStep(false, false, true) : TickStep.IDLE;
    }

    if (cfg.delayTicks <= 0) {
      holding = true;
      cooldownTicks = 0;
      return new TickStep(true, false, false);
    }

    boolean rel = holding;
    holding = false;
    if (cooldownTicks > 0) {
      cooldownTicks--;
      return rel ? new TickStep(false, false, true) : TickStep.IDLE;
    }

    int jitter = cfg.jitterTicks > 0 ? rng.nextInt(cfg.jitterTicks + 1) : 0;
    cooldownTicks = Math.max(0, cfg.delayTicks - 1 + jitter);
    return new TickStep(false, true, rel);
  }

  public static CommandOutcome parseCommand(String raw, Settings.TapeMouse cfg, boolean ru) {
    return parseCommand(raw, cfg, ru ? "ru" : "en");
  }

  public static CommandOutcome parseCommand(String raw, Settings.TapeMouse cfg, String language) {
    if (raw == null || cfg == null) return null;
    String lang = I18n.language(language);
    String trimmed = raw.trim();
    if (trimmed.startsWith("/")) trimmed = trimmed.substring(1).trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (!lower.equals("tapemouse") && !lower.startsWith("tapemouse ")
        && !lower.equals("tm") && !lower.startsWith("tm ")) {
      return null;
    }

    String[] parts = trimmed.split("\\s+");
    if (parts.length == 1) {
      cfg.enabled = !cfg.enabled;
      String msg = cfg.enabled
          ? I18n.format(lang, "tapeMouse.msg.enabled",
                Collections.<String, Object>singletonMap("badge", formatBadgeText(cfg, lang)))
          : I18n.text(lang, "tapeMouse.msg.disabled");
      return new CommandOutcome(true, true, msg);
    }

    String arg1 = parts[1].toLowerCase(Locale.ROOT);
    if ("off".equals(arg1) || "stop".equals(arg1) || "disable".equals(arg1) || "выкл".equals(arg1)) {
      cfg.enabled = false;
      return new CommandOutcome(true, true, I18n.text(lang, "tapeMouse.msg.disabled"));
    }

    if ("on".equals(arg1) || "start".equals(arg1) || "enable".equals(arg1) || "вкл".equals(arg1)) {
      cfg.enabled = true;
      return new CommandOutcome(true, true,
          I18n.format(lang, "tapeMouse.msg.enabled",
              Collections.<String, Object>singletonMap("badge", formatBadgeText(cfg, lang))));
    }

    if ("list".equals(arg1) || "help".equals(arg1) || "?".equals(arg1)) {
      String help = I18n.format(lang, "tapeMouse.msg.help",
          Collections.<String, Object>singletonMap("badge", formatBadgeText(cfg, lang)));
      return new CommandOutcome(true, false, help);
    }

    Integer directDelay = tryParseInt(arg1);
    if (directDelay != null) {
      cfg.delayTicks = Math.max(0, Math.min(1200, directDelay.intValue()));
      cfg.enabled = true;
      return new CommandOutcome(true, true,
          I18n.format(lang, "tapeMouse.msg.active",
              Collections.<String, Object>singletonMap("badge", formatBadgeText(cfg, lang))));
    }

    applyActionToken(cfg, arg1);
    if (parts.length >= 3) {
      String dToken = parts[2].toLowerCase(Locale.ROOT);
      if ("hold".equals(dToken) || "зажать".equals(dToken)) {
        cfg.delayTicks = 0;
      } else {
        Integer d = tryParseInt(dToken);
        if (d != null) {
          cfg.delayTicks = Math.max(0, Math.min(1200, d.intValue()));
        }
      }
    }
    cfg.enabled = true;
    return new CommandOutcome(true, true,
        I18n.format(lang, "tapeMouse.msg.set",
            Collections.<String, Object>singletonMap("badge", formatBadgeText(cfg, lang))));
  }

  public static void applyActionToken(Settings.TapeMouse cfg, String token) {
    if (cfg == null || token == null) return;
    String t = token.trim().toLowerCase(Locale.ROOT);
    if ("left".equals(t) || "lmb".equals(t) || "attack".equals(t) || "лкм".equals(t)) {
      cfg.action = "attack";
    } else if ("right".equals(t) || "rmb".equals(t) || "use".equals(t) || "пкм".equals(t)) {
      cfg.action = "use";
    } else if ("forward".equals(t) || "up".equals(t) || "вперед".equals(t)) {
      cfg.action = "forward";
    } else if ("jump".equals(t) || "space".equals(t) || "прыжок".equals(t)) {
      cfg.action = "jump";
    } else if ("sneak".equals(t) || "shift".equals(t) || "шифт".equals(t)) {
      cfg.action = "sneak";
    } else if ("sprint".equals(t)) {
      cfg.action = "sprint";
    } else if ("drop".equals(t)) {
      cfg.action = "drop";
    } else {
      cfg.action = "custom";
      if (t.startsWith("key.")) {
        cfg.customKey = t;
      } else if (t.length() == 1 && ((t.charAt(0) >= 'a' && t.charAt(0) <= 'z')
          || (t.charAt(0) >= '0' && t.charAt(0) <= '9'))) {
        cfg.customKey = "key.keyboard." + t;
      } else if (t.startsWith("mouse")) {
        String num = t.substring(5);
        cfg.customKey = "key.mouse." + (num.isEmpty() ? "left" : num);
      } else {
        cfg.customKey = "key.keyboard." + t;
      }
    }
  }

  public static String formatBadgeText(Settings.TapeMouse cfg, boolean ru) {
    return formatBadgeText(cfg, ru ? "ru" : "en");
  }

  public static String formatBadgeText(Settings.TapeMouse cfg, String language) {
    if (cfg == null) return "";
    String lang = I18n.language(language);
    String act = formatActionShort(cfg, lang);
    String mode = cfg.delayTicks <= 0
        ? I18n.text(lang, "tapeMouse.badge.hold")
        : I18n.format(lang, "tapeMouse.badge.ticks",
              Collections.<String, Object>singletonMap("ticks", Integer.valueOf(cfg.delayTicks)));
    return act + " · " + mode;
  }

  public static String formatActionShort(Settings.TapeMouse cfg, boolean ru) {
    return formatActionShort(cfg, ru ? "ru" : "en");
  }

  public static String formatActionShort(Settings.TapeMouse cfg, String language) {
    String lang = I18n.language(language);
    if ("attack".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.attack");
    if ("use".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.use");
    if ("forward".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.forward");
    if ("jump".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.jump");
    if ("sneak".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.sneak");
    if ("sprint".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.sprint");
    if ("drop".equals(cfg.action)) return I18n.text(lang, "tapeMouse.action.drop");
    String k = cfg.customKey == null ? "" : cfg.customKey;
    if (k.startsWith("key.keyboard.")) return k.substring("key.keyboard.".length()).toUpperCase(Locale.ROOT);
    if (k.startsWith("key.mouse.")) return k.substring("key.mouse.".length()).toUpperCase(Locale.ROOT);
    return k.toUpperCase(Locale.ROOT);
  }

  private static Integer tryParseInt(String s) {
    try {
      return Integer.valueOf(Integer.parseInt(s));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
