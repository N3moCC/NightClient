package dev.nightclient.platform;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public final class ItemOpacity {
  private static final ThreadLocal<Float> extracting = new ThreadLocal<Float>();
  private static final ReferenceQueue<Object> collected = new ReferenceQueue<Object>();
  private static final Map<IdentityReference, Float> poses =
      new HashMap<IdentityReference, Float>();
  private static volatile boolean hasEntries;

  private ItemOpacity() {}

  public static Float push(float alpha) {
    Float previous = extracting.get();
    extracting.set(Float.isNaN(alpha) ? 0 : Math.max(0, Math.min(1, alpha)));
    return previous;
  }

  public static void pop(Float previous) {
    if (previous == null)
      extracting.remove();
    else
      extracting.set(previous);
  }

  public static void capture(Object pose) {
    Float alpha = extracting.get();
    if (alpha == null || alpha >= 1 || pose == null) return;
    synchronized (ItemOpacity.class) {
      drain();
      poses.put(new IdentityReference(pose, collected), alpha);
      hasEntries = true;
    }
  }

  public static int color(Object pose, int premultipliedColor) {
    if (!hasEntries || pose == null) return premultipliedColor;
    Float alpha;
    synchronized (ItemOpacity.class) {
      drain();
      if (poses.isEmpty()) {
        hasEntries = false;
        return premultipliedColor;
      }
      alpha = poses.get(new IdentityReference(pose, null));
    }
    if (alpha == null) return premultipliedColor;
    return Math.round((premultipliedColor >>> 24) * alpha) << 24
        | Math.round(((premultipliedColor >>> 16) & 255) * alpha) << 16
        | Math.round(((premultipliedColor >>> 8) & 255) * alpha) << 8
        | Math.round((premultipliedColor & 255) * alpha);
  }

  private static void drain() {
    IdentityReference reference;
    while ((reference = (IdentityReference) collected.poll()) != null) poses.remove(reference);
    if (poses.isEmpty()) hasEntries = false;
  }

  private static final class IdentityReference extends WeakReference<Object> {
    private final int hash;

    IdentityReference(Object value, ReferenceQueue<Object> queue) {
      super(value, queue);
      hash = System.identityHashCode(value);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) return true;
      if (!(other instanceof IdentityReference)) return false;
      Object value = get();
      return value != null && value == ((IdentityReference) other).get();
    }
  }
}
