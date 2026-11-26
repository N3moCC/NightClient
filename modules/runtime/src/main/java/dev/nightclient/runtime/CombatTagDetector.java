package dev.nightclient.runtime;

import dev.nightclient.config.CommandLayoutFix;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CombatTagDetector {
  private CombatTagDetector() {}

  public static final class CombatMatch {
    public static final CombatMatch NONE = new CombatMatch(false, -1);
    public final boolean active;
    public final int remainingSeconds;

    public CombatMatch(boolean active, int remainingSeconds) {
      this.active = active;
      this.remainingSeconds = remainingSeconds;
    }
  }

  public static final class CombatStatus {
    public static final CombatStatus NONE =
        new CombatStatus(false, -1, Float.POSITIVE_INFINITY, "");
    public final boolean active;
    public final int remainingSeconds;
    public final float blockedAgoSeconds;
    public final String blockedReason;

    public CombatStatus(
        boolean active, int remainingSeconds, float blockedAgoSeconds, String blockedReason) {
      this.active = active;
      this.remainingSeconds = remainingSeconds;
      this.blockedAgoSeconds = blockedAgoSeconds;
      this.blockedReason = blockedReason == null ? "" : blockedReason;
    }
  }

  private static final Pattern FORMAT_STRIP =
      Pattern.compile("(?:§x(?:§[0-9a-fA-F]){6})|§[0-9a-fk-orA-FK-OR]|&[0-9a-fk-orA-FK-OR]");
  private static final Pattern MM_SS = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
  private static final Pattern SEC_UNIT =
      Pattern.compile("(\\d{1,3})(?:[.,]\\d+)?\\s*(?:сек\\.?|с\\.?|sec(?:onds?)?|s\\b)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BARE_NUM =
      Pattern.compile("(?<![#№a-zA-Zа-яА-ЯёЁ])\\b(\\d{1,3})\\b");

  private static final Pattern CT_TOKEN = Pattern.compile(
      "(?:^|[\\s\\[\\(\\|•·:-])(?:кт|ct|pvp|пвп)(?:$|[\\s\\]\\)\\|•·:!-])",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern PLAYER_CHAT_PREFIX = Pattern.compile(
      "^(?:"
          + "<[^>]{1,32}>\\s*"
          + "|\\[(?:g|l|г|л|global|local|глобал|локал|лс|pm|msg|clan|клан|party|пати|trade|торг|vip|mvp|helper|mod|admin|youtube|yt|tiktok|media)[^\\]]*\\]\\s*"
          + "|[a-zA-Z0-9_]{3,16}\\s*(?:»|->|→)\\s*"
          + ")",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern OWN_MOD_PREFIX = Pattern.compile(
      "^\\[(?:safe ct leave|pvp safe|nightclient|tapemouse)\\]",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern PVP_BOSSBAR_PATTERN = Pattern.compile(
      "(?:pvp|пвп)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern FIRST_INT = Pattern.compile("(\\d+)");

  private static final Pattern COMBAT_BLOCKED_CMD = Pattern.compile(
      "^/(?:hub|an|grief|limbo|logout|lobby|рги|фт|пкшуа|дшьищ|дщпщге|дщиин)(?:[-_]?\\d+)?(?:\\s|$)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final String[] COMBAT_END_PHRASES = {
      "no longer in combat",
      "not in combat",
      "out of combat",
      "left combat",
      "combat ended",
      "combat expired",
      "combat over",
      "safe to log out",
      "you can now log out",
      "больше не в бою",
      "не в бою",
      "вышли из боя",
      "вышел из боя",
      "вышли из режима боя",
      "вышел из режима боя",
      "бой окончен",
      "бой завершен",
      "бой завершён",
      "режим боя окончен",
      "режим боя заверш",
      "кт окончен",
      "кт закончил",
      "можете выходить",
      "можно выходить"
  };

  private static final String[] STRONG_CT_PHRASES = {
      "вы в бою",
      "вы в режиме боя",
      "режим боя",
      "до конца боя",
      "не выходите",
      "не покидайте",
      "выход = смерть",
      "выход - смерть",
      "антирелог",
      "antirelog",
      "combatlog",
      "combat tag",
      "combat-tag",
      "in combat",
      "combat timer",
      "combat mode",
      "do not log out",
      "don't log out",
      "don't leave",
      "do not leave",
      "pvp mode",
      "режим пвп",
      "пвп режим",
      "пвп таймер",
      "pvp timer"
  };

  private static final String[] SOFT_CT_PHRASES = {
      "в бою",
      "боя:",
      "бой:",
      "бой ",
      " боя",
      "combat:",
      "combat ",
      "pvp:",
      "пвп:",
      "кт:",
      "ct:"
  };

  private static final String[] FALSE_POSITIVE_WORDS = {
      "босс",
      "boss",
      "дракон",
      "dragon",
      "визер",
      "иссушитель",
      "wither",
      "рейд",
      "raid",
      "аирдроп",
      "airdrop",
      "мистик",
      "вулкан",
      "маяк",
      "ивент",
      "event",
      "кейс",
      "case",
      "аукцион",
      "auction",
      "голосовани",
      "перезагрузк",
      "рестарт",
      "restart"
  };

  private static final Set<String> BLOCKED_COMMANDS = new HashSet<String>(Arrays.asList(
      "hub",
      "lobby",
      "l",
      "leave",
      "quit",
      "exit",
      "disconnect",
      "reconnect",
      "server",
      "srv",
      "spawn",
      "sp",
      "home",
      "homes",
      "warp",
      "warps",
      "tpa",
      "tpaccept",
      "tpyes",
      "tp",
      "rtp",
      "wild",
      "suicide",
      "kill",
      "anarchy",
      "an",
      "grief",
      "limbo",
      "logout",
      "duel",
      "duels",
      "sw",
      "bw"));

  private static final Pattern NUMBERED_REALM_CMD =
      Pattern.compile("^(?:an|anarchy|grief|lobby|hub|limbo|logout|srv|server)(?:[-_]?\\d+)?$");

  public static String stripFormatting(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    return FORMAT_STRIP.matcher(raw).replaceAll("").trim();
  }

  public static boolean isCombatEndText(String raw) {
    String clean = stripFormatting(raw);
    if (clean.isEmpty()) return false;
    String lower = clean.toLowerCase(Locale.ROOT);
    if (OWN_MOD_PREFIX.matcher(lower).find()) return false;
    if (PLAYER_CHAT_PREFIX.matcher(clean).find()) return false;
    for (String ended : COMBAT_END_PHRASES) {
      if (lower.contains(ended)) return true;
    }
    return false;
  }

  public static CombatMatch parseCombatText(String raw) {
    String clean = stripFormatting(raw);
    if (clean.isEmpty()) return CombatMatch.NONE;
    String lower = clean.toLowerCase(Locale.ROOT);
    if (OWN_MOD_PREFIX.matcher(lower).find()) return CombatMatch.NONE;

    for (String ended : COMBAT_END_PHRASES) {
      if (lower.contains(ended)) return CombatMatch.NONE;
    }

    for (String phrase : STRONG_CT_PHRASES) {
      if (lower.contains(phrase)) {
        return new CombatMatch(true, extractSeconds(lower));
      }
    }

    if (lower.contains("дуэль") && !lower.contains("заверш") && !lower.contains("оконч")) {
      return new CombatMatch(true, extractSeconds(lower));
    }

    for (String reject : FALSE_POSITIVE_WORDS) {
      if (lower.contains(reject)) return CombatMatch.NONE;
    }

    boolean matched = false;
    for (String phrase : SOFT_CT_PHRASES) {
      if (lower.contains(phrase)) {
        matched = true;
        break;
      }
    }
    if (!matched && CT_TOKEN.matcher(lower).find()) {
      int sec = extractSeconds(lower);
      if (sec > 0 || lower.contains("осталось") || lower.contains("left")
          || lower.contains("таймер") || lower.contains("timer")) {
        return new CombatMatch(true, sec);
      }
      return CombatMatch.NONE;
    }

    if (!matched) return CombatMatch.NONE;
    int sec = extractSeconds(lower);
    if (sec <= 0 && !lower.contains("в бою") && !lower.contains("осталось")
        && !lower.contains("left")) {
      return CombatMatch.NONE;
    }
    return new CombatMatch(true, sec);
  }

  public static CombatMatch parseScoreboardCombatText(String raw) {
    String clean = stripFormatting(raw);
    if (clean.isEmpty()) return CombatMatch.NONE;
    String lower = clean.toLowerCase(Locale.ROOT);
    if (OWN_MOD_PREFIX.matcher(lower).find()) return CombatMatch.NONE;
    for (String ended : COMBAT_END_PHRASES) {
      if (lower.contains(ended)) return CombatMatch.NONE;
    }
    for (String reject : FALSE_POSITIVE_WORDS) {
      if (lower.contains(reject)) return CombatMatch.NONE;
    }
    int explicitSec = extractExplicitTimeSeconds(lower);
    for (String phrase : STRONG_CT_PHRASES) {
      if (lower.contains(phrase)) {
        if (explicitSec > 0) return new CombatMatch(true, explicitSec);
        int sec = extractSeconds(lower);
        if (sec > 0) return new CombatMatch(true, sec);
        if (lower.contains("вы в бою") || lower.contains("in combat")) {
          return new CombatMatch(true, -1);
        }
        return CombatMatch.NONE;
      }
    }
    if (explicitSec <= 0) return CombatMatch.NONE;
    for (String phrase : SOFT_CT_PHRASES) {
      if (lower.contains(phrase)) {
        return new CombatMatch(true, explicitSec);
      }
    }
    if (CT_TOKEN.matcher(lower).find()) {
      return new CombatMatch(true, explicitSec);
    }
    return CombatMatch.NONE;
  }

  public static CombatMatch parseChatCombatText(String raw) {
    String clean = stripFormatting(raw);
    if (clean.isEmpty()) return CombatMatch.NONE;
    String lower = clean.toLowerCase(Locale.ROOT);
    if (OWN_MOD_PREFIX.matcher(lower).find()) return CombatMatch.NONE;
    if (PLAYER_CHAT_PREFIX.matcher(clean).find()) return CombatMatch.NONE;
    for (String ended : COMBAT_END_PHRASES) {
      if (lower.contains(ended)) return CombatMatch.NONE;
    }
    for (String reject : FALSE_POSITIVE_WORDS) {
      if (lower.contains(reject)) return CombatMatch.NONE;
    }
    for (String phrase : STRONG_CT_PHRASES) {
      if (lower.contains(phrase)) {
        return new CombatMatch(true, extractSeconds(lower));
      }
    }
    return CombatMatch.NONE;
  }

  public static int extractExplicitTimeSeconds(String lowerText) {
    if (lowerText == null || lowerText.isEmpty()) return -1;
    Matcher mmss = MM_SS.matcher(lowerText);
    if (mmss.find()) {
      try {
        int m = Integer.parseInt(mmss.group(1));
        int s = Integer.parseInt(mmss.group(2));
        int total = m * 60 + s;
        if (m >= 0 && m <= 5 && s >= 0 && s < 60 && total > 0) {
          return total;
        }
      } catch (NumberFormatException ignored) {
      }
    }
    Matcher sec = SEC_UNIT.matcher(lowerText);
    if (sec.find()) {
      try {
        int val = Integer.parseInt(sec.group(1));
        if (val > 0 && val <= 300) return val;
      } catch (NumberFormatException ignored) {
      }
    }
    return -1;
  }

  public static int extractSeconds(String lowerText) {
    if (lowerText == null || lowerText.isEmpty()) return -1;
    int explicit = extractExplicitTimeSeconds(lowerText);
    if (explicit > 0) return explicit;
    Matcher bare = BARE_NUM.matcher(lowerText);
    while (bare.find()) {
      try {
        int val = Integer.parseInt(bare.group(1));
        if (val > 0 && val <= 180) return val;
      } catch (NumberFormatException ignored) {
      }
    }
    return -1;
  }

  public static String normalizeLeaveCommand(String raw) {
    if (raw == null) return "";
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return "";
    String candidate = trimmed;
    if (candidate.startsWith(".")) {
      candidate = "/" + candidate.substring(1);
    }
    if (candidate.startsWith("/")) {
      String fixed = CommandLayoutFix.fix(candidate);
      if (fixed != null && fixed.startsWith("/")) candidate = fixed;
      return candidate;
    }
    return "";
  }

  public static CombatMatch parseBossBarText(String raw) {
    CombatMatch std = parseCombatText(raw);
    if (std.active) return std;
    String clean = stripFormatting(raw);
    if (clean.isEmpty()) return CombatMatch.NONE;
    String lower = clean.toLowerCase(Locale.ROOT);
    if (OWN_MOD_PREFIX.matcher(lower).find()) return CombatMatch.NONE;
    for (String ended : COMBAT_END_PHRASES) {
      if (lower.contains(ended)) return CombatMatch.NONE;
    }
    for (String reject : FALSE_POSITIVE_WORDS) {
      if (lower.contains(reject)) return CombatMatch.NONE;
    }
    if (lower.contains("арена") || lower.contains("arena")) return CombatMatch.NONE;
    if (PVP_BOSSBAR_PATTERN.matcher(clean).find()) {
      Matcher m = FIRST_INT.matcher(clean);
      int sec = -1;
      if (m.find()) {
        try {
          int v = Integer.parseInt(m.group(1));
          if (v > 0 && v <= 300) sec = v;
        } catch (NumberFormatException ignored) {
        }
      }
      if (sec > 0 || !lower.contains("открыт")) {
        return new CombatMatch(true, sec);
      }
    }
    return CombatMatch.NONE;
  }

  public static boolean isBlockedLeaveCommand(String raw) {
    if (raw == null) return false;
    String trimmed = raw.trim();
    if (COMBAT_BLOCKED_CMD.matcher(trimmed).find()) return true;
    String candidate = normalizeLeaveCommand(raw);
    if (!candidate.startsWith("/")) return false;
    if (COMBAT_BLOCKED_CMD.matcher(candidate).find()) return true;
    String rest = candidate.substring(1).trim();
    if (rest.isEmpty()) return false;
    int sp = rest.indexOf(' ');
    String cmd = (sp < 0 ? rest : rest.substring(0, sp)).toLowerCase(Locale.ROOT);
    int colon = cmd.indexOf(':');
    if (colon >= 0 && colon + 1 < cmd.length()) {
      cmd = cmd.substring(colon + 1);
    }
    return BLOCKED_COMMANDS.contains(cmd) || NUMBERED_REALM_CMD.matcher(cmd).matches();
  }
}
