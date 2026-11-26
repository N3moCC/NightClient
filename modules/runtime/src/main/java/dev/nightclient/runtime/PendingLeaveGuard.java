package dev.nightclient.runtime;

public final class PendingLeaveGuard {
  public static final long MIN_WINDOW_MS = 220L;
  public static final long MAX_WINDOW_MS = 450L;

  public enum Outcome {
    IDLE,
    WAITING,
    EXECUTE,
    ABORTED_HIT,
    ABORTED_CT,
    CANCELLED
  }

  public static final class Snapshot {
    public final boolean combatActive;
    public final float totalHealth;
    public final int hurtTime;
    public final long lastPvpHitNanos;
    public final long lastUnattributedHurtNanos;
    public final long lastPacketCtNanos;
    public final long lastNettyDamageNanos;
    public final long lastConfirmedNonPvpNanos;
    public final boolean screenStillValid;
    public final boolean shiftBypassHeld;

    public Snapshot(
        boolean combatActive,
        float totalHealth,
        int hurtTime,
        long lastPvpHitNanos,
        long lastUnattributedHurtNanos,
        long lastPacketCtNanos,
        long lastNettyDamageNanos,
        long lastConfirmedNonPvpNanos,
        boolean screenStillValid,
        boolean shiftBypassHeld) {
      this.combatActive = combatActive;
      this.totalHealth = totalHealth;
      this.hurtTime = hurtTime;
      this.lastPvpHitNanos = lastPvpHitNanos;
      this.lastUnattributedHurtNanos = lastUnattributedHurtNanos;
      this.lastPacketCtNanos = lastPacketCtNanos;
      this.lastNettyDamageNanos = lastNettyDamageNanos;
      this.lastConfirmedNonPvpNanos = lastConfirmedNonPvpNanos;
      this.screenStillValid = screenStillValid;
      this.shiftBypassHeld = shiftBypassHeld;
    }
  }

  private boolean active;
  private String kind = "";
  private Runnable replay;
  private long startNanos;
  private long deadlineNanos;
  private float baselineHealth;
  private int baselineHurtTime;
  private long baselinePvpHitNanos;
  private long baselineUnattributedHurtNanos;
  private long baselinePacketCtNanos;
  private long baselineNettyDamageNanos;

  public static long computeWindowMs(int pingMs) {
    long clampedPing = Math.max(0L, Math.min(400L, pingMs));
    long window = clampedPing * 2L + 120L;
    return Math.max(MIN_WINDOW_MS, Math.min(MAX_WINDOW_MS, window));
  }

  public boolean isActive() {
    return active;
  }

  public String kind() {
    return kind;
  }

  public void begin(String leaveKind, Runnable replayAction, long nowNanos, int pingMs, Snapshot baseline) {
    if (active) {
      return;
    }
    this.active = true;
    this.kind = leaveKind == null ? "" : leaveKind;
    this.replay = replayAction;
    this.startNanos = nowNanos;
    this.deadlineNanos = nowNanos + computeWindowMs(pingMs) * 1_000_000L;
    this.baselineHealth = baseline.totalHealth;
    this.baselineHurtTime = baseline.hurtTime;
    this.baselinePvpHitNanos = baseline.lastPvpHitNanos;
    this.baselineUnattributedHurtNanos = baseline.lastUnattributedHurtNanos;
    this.baselinePacketCtNanos = baseline.lastPacketCtNanos;
    this.baselineNettyDamageNanos = baseline.lastNettyDamageNanos;
  }

  public Runnable takeReplay() {
    Runnable action = this.replay;
    clear();
    return action;
  }

  public void clear() {
    this.active = false;
    this.kind = "";
    this.replay = null;
    this.startNanos = 0L;
    this.deadlineNanos = 0L;
    this.baselineHealth = 0f;
    this.baselineHurtTime = 0;
    this.baselinePvpHitNanos = 0L;
    this.baselineUnattributedHurtNanos = 0L;
    this.baselinePacketCtNanos = 0L;
    this.baselineNettyDamageNanos = 0L;
  }

  public Outcome poll(long nowNanos, Snapshot current) {
    if (!active) {
      return Outcome.IDLE;
    }
    if (current.shiftBypassHeld) {
      return Outcome.EXECUTE;
    }
    if (!current.screenStillValid) {
      clear();
      return Outcome.CANCELLED;
    }
    boolean freshNonPvp =
        current.lastConfirmedNonPvpNanos >= startNanos
            && (nowNanos - current.lastConfirmedNonPvpNanos) < 250_000_000L;

    if (current.combatActive
        || (current.lastPacketCtNanos != baselinePacketCtNanos
            && current.lastPacketCtNanos >= startNanos)) {
      clear();
      return Outcome.ABORTED_CT;
    }
    if ((current.lastPvpHitNanos != baselinePvpHitNanos && current.lastPvpHitNanos >= startNanos)
        || (current.lastUnattributedHurtNanos != baselineUnattributedHurtNanos
            && current.lastUnattributedHurtNanos >= startNanos)) {
      clear();
      return Outcome.ABORTED_HIT;
    }
    if (!freshNonPvp
        && (current.totalHealth < baselineHealth - 0.05f || current.hurtTime > baselineHurtTime)) {
      clear();
      return Outcome.ABORTED_HIT;
    }

    boolean unclassifiedNettyDamage =
        current.lastNettyDamageNanos != baselineNettyDamageNanos
            && current.lastNettyDamageNanos >= startNanos
            && current.lastConfirmedNonPvpNanos < current.lastNettyDamageNanos;
    if (unclassifiedNettyDamage) {
      if ((nowNanos - current.lastNettyDamageNanos) < 90_000_000L) {
        return Outcome.WAITING;
      }
      clear();
      return Outcome.ABORTED_HIT;
    }

    if (nowNanos >= deadlineNanos) {
      return Outcome.EXECUTE;
    }
    return Outcome.WAITING;
  }
}
