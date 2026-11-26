package dev.nightclient.renderer.cape;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class WavyCapeManager {
  private static final long STALE_TTL_NANOS = 6_000_000_000L;
  private static final long PRUNE_INTERVAL_NANOS = 2_000_000_000L;

  private static final class Entry {
    final WavyCapeSimulation simulation;
    long lastSeenNanos;

    Entry(WavyCapeSimulation simulation, long nowNanos) {
      this.simulation = simulation;
      this.lastSeenNanos = nowNanos;
    }
  }

  private final Map<Integer, Entry> entries = new HashMap<Integer, Entry>();
  private long lastPruneNanos;

  public synchronized WavyCapeMesh updateAndBuild(WavyCapePose pose, WavyCapeConfig config) {
    long now = pose.timeNanos;
    if (now - lastPruneNanos > PRUNE_INTERVAL_NANOS || now < lastPruneNanos) {
      prune(now);
      lastPruneNanos = now;
    }
    Entry entry = entries.get(pose.entityId);
    if (entry == null) {
      entry = new Entry(new WavyCapeSimulation(pose.entityId, config), now);
      entries.put(pose.entityId, entry);
    } else {
      entry.lastSeenNanos = now;
    }
    return entry.simulation.updateAndBuild(pose, config);
  }

  public synchronized int activeCount() {
    return entries.size();
  }

  public synchronized void clear() {
    entries.clear();
  }

  private void prune(long nowNanos) {
    if (entries.isEmpty()) return;
    Iterator<Map.Entry<Integer, Entry>> it = entries.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Integer, Entry> e = it.next();
      long age = nowNanos - e.getValue().lastSeenNanos;
      if (age > STALE_TTL_NANOS || age < -STALE_TTL_NANOS) {
        it.remove();
      }
    }
  }
}
