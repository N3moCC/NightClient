package dev.nightclient.ui.skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

final class HudAutoLayout {
  static final class Cache {
    private List<Box> previous;
    private float width, height, gap;
    void place(List<Box> boxes, float width, float height, float gap) {
      boolean same = previous != null && previous.size() == boxes.size() && this.width == width
          && this.height == height && this.gap == gap;
      for (int i = 0; same && i < boxes.size(); i++) {
        Box a = boxes.get(i), b = previous.get(i);
        same = a.id.equals(b.id) && a.desiredX == b.desiredX && a.desiredY == b.desiredY
            && a.width == b.width && a.height == b.height && a.order == b.order
            && a.pinned == b.pinned;
      }
      if (same) {
        for (int i = 0; i < boxes.size(); i++) {
          boxes.get(i).x = previous.get(i).x;
          boxes.get(i).y = previous.get(i).y;
        }
        return;
      }
      HudAutoLayout.place(boxes, width, height, gap);
      this.width = width;
      this.height = height;
      this.gap = gap;
      previous = new ArrayList<Box>(boxes.size());
      for (Box box : boxes) {
        Box copy = new Box(
            box.id, box.desiredX, box.desiredY, box.width, box.height, box.order, box.pinned);
        copy.x = box.x;
        copy.y = box.y;
        previous.add(copy);
      }
    }
  }
  static final class Box {
    final String id;
    final float desiredX, desiredY, width, height;
    final long order;
    final boolean pinned;
    float x, y;
    Box(String id, float x, float y, float width, float height, long order, boolean pinned) {
      this.id = id;
      desiredX = this.x = x;
      desiredY = this.y = y;
      this.width = width;
      this.height = height;
      this.order = order;
      this.pinned = pinned;
    }
  }
  private HudAutoLayout() {}

  static void place(List<Box> boxes, float viewportWidth, float viewportHeight, float gap) {
    List<Box> ordered = new ArrayList<Box>(boxes);
    Collections.sort(ordered, new Comparator<Box>() {
      public int compare(Box a, Box b) {
        if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
        return Long.compare(b.order, a.order);
      }
    });
    List<Box> placed = new ArrayList<Box>();
    boolean collided = false;
    for (Box box : ordered) {
      float margin = boxMargin(box);
      float right = Math.max(margin, viewportWidth - margin - box.width),
            bottom = Math.max(margin, viewportHeight - margin - box.height);
      float preferredX = clamp(box.desiredX, margin, right),
            preferredY = clamp(box.desiredY, margin, bottom);
      box.x = preferredX;
      box.y = preferredY;
      if (!box.pinned && !placed.isEmpty()
          && overlap(box, preferredX, preferredY, placed, gap) > 0) {
        TreeSet<Float> xs = new TreeSet<Float>(), ys = new TreeSet<Float>();
        xs.add(preferredX);
        xs.add(margin);
        xs.add(right);
        ys.add(preferredY);
        ys.add(margin);
        ys.add(bottom);
        for (Box other : placed) {
          float pairGap = pairGap(box, other, gap);
          xs.add(clamp(other.x - box.width - pairGap, margin, right));
          xs.add(clamp(other.x + other.width + pairGap, margin, right));
          ys.add(clamp(other.y - box.height - pairGap, margin, bottom));
          ys.add(clamp(other.y + other.height + pairGap, margin, bottom));
        }
        double bestOverlap = Double.POSITIVE_INFINITY, bestDistance = Double.POSITIVE_INFINITY;
        for (float y : ys)
          for (float x : xs) {
            double overlap = overlap(box, x, y, placed, gap);
            double dx = x - preferredX, dy = y - preferredY, distance = dx * dx * 4 + dy * dy;
            if (overlap < bestOverlap || overlap == bestOverlap && distance < bestDistance) {
              bestOverlap = overlap;
              bestDistance = distance;
              box.x = x;
              box.y = y;
            }
          }
      }
      collided |= overlap(box, box.x, box.y, placed, gap) > 0;
      placed.add(box);
    }
    if (collided) {
      float[] greedy = new float[ordered.size() * 2];
      for (int i = 0; i < ordered.size(); i++) {
        greedy[i * 2] = ordered.get(i).x;
        greedy[i * 2 + 1] = ordered.get(i).y;
      }
      int fixed = 0;
      while (fixed < ordered.size() && ordered.get(fixed).pinned) fixed++;
      if (fixed == 0 && !ordered.isEmpty() && ordered.get(0).order > 0) fixed = 1;
      placed.clear();
      boolean fixedOverlap = false;
      for (int i = 0; i < fixed; i++) {
        Box box = ordered.get(i);
        fixedOverlap |= overlap(box, box.x, box.y, placed, gap) > 0;
        placed.add(box);
      }
      if (fixedOverlap
          || !repack(ordered, fixed, placed, viewportWidth, viewportHeight, gap, new int[] {512}))
        for (int i = 0; i < ordered.size(); i++) {
          ordered.get(i).x = greedy[i * 2];
          ordered.get(i).y = greedy[i * 2 + 1];
        }
    }
  }
  private static final class Candidate {
    final float x, y;
    final double distance;
    Candidate(float x, float y, float desiredX, float desiredY) {
      this.x = x;
      this.y = y;
      double dx = x - desiredX, dy = y - desiredY;
      distance = dx * dx * 4 + dy * dy;
    }
  }
  private static boolean repack(List<Box> boxes, int index, List<Box> placed, float width,
      float height, float gap, int[] budget) {
    if (index == boxes.size()) return true;
    if (--budget[0] < 0) return false;
    Box box = boxes.get(index);
    float margin = boxMargin(box);
    float right = Math.max(margin, width - margin - box.width),
          bottom = Math.max(margin, height - margin - box.height);
    float preferredX = clamp(box.desiredX, margin, right),
          preferredY = clamp(box.desiredY, margin, bottom);
    TreeSet<Float> xs = new TreeSet<Float>(), ys = new TreeSet<Float>();
    xs.add(preferredX);
    xs.add(margin);
    xs.add(right);
    ys.add(preferredY);
    ys.add(margin);
    ys.add(bottom);
    for (Box other : placed) {
      float pairGap = pairGap(box, other, gap);
      xs.add(clamp(other.x - box.width - pairGap, margin, right));
      xs.add(clamp(other.x + other.width + pairGap, margin, right));
      ys.add(clamp(other.y - box.height - pairGap, margin, bottom));
      ys.add(clamp(other.y + other.height + pairGap, margin, bottom));
    }
    List<Candidate> candidates = new ArrayList<Candidate>();
    for (float y : ys)
      for (float x : xs)
        if (overlap(box, x, y, placed, gap) == 0)
          candidates.add(new Candidate(x, y, preferredX, preferredY));
    Collections.sort(candidates, new Comparator<Candidate>() {
      public int compare(Candidate a, Candidate b) {
        return Double.compare(a.distance, b.distance);
      }
    });
    int count = Math.min(24, candidates.size());
    for (int i = 0; i < count && budget[0] >= 0; i++) {
      Candidate candidate = candidates.get(i);
      box.x = candidate.x;
      box.y = candidate.y;
      placed.add(box);
      if (repack(boxes, index + 1, placed, width, height, gap, budget)) return true;
      placed.remove(placed.size() - 1);
    }
    return false;
  }
  private static float boxMargin(Box box) {
    return box.pinned || box.order > 0 ? 0f : 8f;
  }
  private static float pairGap(Box a, Box b, float gap) {
    return a.pinned || a.order > 0 || b.pinned || b.order > 0 ? 0f : gap;
  }
  private static double overlap(Box box, float x, float y, List<Box> placed, float gap) {
    double overlap = 0;
    for (Box other : placed) {
      float pairGap = pairGap(box, other, gap);
      float w = Math.max(0,
          Math.min(x + box.width, other.x + other.width + pairGap)
              - Math.max(x, other.x - pairGap));
      float h = Math.max(0,
          Math.min(y + box.height, other.y + other.height + pairGap)
              - Math.max(y, other.y - pairGap));
      overlap += w * h;
    }
    return overlap;
  }
  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}
