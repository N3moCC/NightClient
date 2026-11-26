package dev.nightclient.platform;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
@@TEXT_CACHE_EXTRA_IMPORTS@@

public final class TextQuadCache {
  private TextQuadCache() {}

  private static final int CAP = 4096;
  private static final int MASK = CAP - 1;
  private static final int SEEN_CAP = 8192;
  private static final int SEEN_MASK = SEEN_CAP - 1;
  private static final Matrix4f IDENTITY = new Matrix4f();

  public static final class Bucket {
    public final RenderType renderType;
    public final float[] xyzuv;
    public final int[] abgrs;
    public final int[] argbs;
    public final int quadCount;
    public final boolean hasUv;
    public final boolean flatZ0;
    public int nativeMeshId;
    public long nativeMeshGen;

    Bucket(
        RenderType renderType,
        float[] xyzuv,
        int[] abgrs,
        int[] argbs,
        int quadCount,
        boolean hasUv,
        boolean flatZ0) {
      this.renderType = renderType;
      this.xyzuv = xyzuv;
      this.abgrs = abgrs;
      this.argbs = argbs;
      this.quadCount = quadCount;
      this.hasUv = hasUv;
      this.flatZ0 = flatZ0;
    }
  }

  public static final class Entry {
    final FormattedCharSequence string;
    final Component component;
    final int compHash;
    final float x;
    final float y;
    final int color;
    final int bgColor;
    final boolean dropShadow;
    final Font.DisplayMode displayMode;
    final Bucket[] buckets;

    Entry(
        FormattedCharSequence string,
        Component component,
        int compHash,
        float x,
        float y,
        int color,
        int bgColor,
        boolean dropShadow,
        Font.DisplayMode displayMode,
        Bucket[] buckets) {
      this.string = string;
      this.component = component;
      this.compHash = compHash;
      this.x = x;
      this.y = y;
      this.color = color;
      this.bgColor = bgColor;
      this.dropShadow = dropShadow;
      this.displayMode = displayMode;
      this.buckets = buckets;
    }
  }

  private static final Entry[] TABLE = new Entry[CAP];
  private static final int[] SEEN_HASH = new int[SEEN_CAP];
  private static final Entry[] COMP_TABLE = new Entry[CAP];
  private static final int[] COMP_SEEN_HASH = new int[SEEN_CAP];
  private static Font cachedFont;

  public static void clear() {
    Arrays.fill(TABLE, null);
    Arrays.fill(SEEN_HASH, 0);
    Arrays.fill(COMP_TABLE, null);
    Arrays.fill(COMP_SEEN_HASH, 0);
  }

  public static Entry getOrBuild(
      Font font,
      FormattedCharSequence string,
      float x,
      float y,
      int color,
      boolean dropShadow,
      Font.DisplayMode displayMode,
      int bgColor) {
    if (font == null || string == null || displayMode == null) return null;
    if (font != cachedFont) {
      clear();
      cachedFont = font;
    }
    int idHash = System.identityHashCode(string);
    int hash = idHash * 31
        ^ Float.floatToRawIntBits(x) * 17
        ^ Float.floatToRawIntBits(y) * 131
        ^ color * 7
        ^ bgColor * 19
        ^ (dropShadow ? 0x5A5A : 0)
        ^ (displayMode.ordinal() << 24);
    hash ^= hash >>> 16;
    int slot = hash & MASK;

    Entry e = TABLE[slot];
    if (e != null
        && e.string == string
        && e.x == x
        && e.y == y
        && e.color == color
        && e.bgColor == bgColor
        && e.dropShadow == dropShadow
        && e.displayMode == displayMode) {
      return e;
    }
    int slot2 = (slot + 1) & MASK;
    Entry e2 = TABLE[slot2];
    if (e2 != null
        && e2.string == string
        && e2.x == x
        && e2.y == y
        && e2.color == color
        && e2.bgColor == bgColor
        && e2.dropShadow == dropShadow
        && e2.displayMode == displayMode) {
      return e2;
    }

    int idTag = idHash ^ (hash << 1) | 1;
    int seenSlot = hash & SEEN_MASK;
    if (SEEN_HASH[seenSlot] != idTag) {
      SEEN_HASH[seenSlot] = idTag;
      return null;
    }

    Bucket[] buckets = capture(font, string, x, y, color, dropShadow, displayMode, bgColor);
    if (buckets == null) return null;
    Entry created = new Entry(string, null, 0, x, y, color, bgColor, dropShadow, displayMode, buckets);
    if (e == null) {
      TABLE[slot] = created;
    } else if (e2 == null) {
      TABLE[slot2] = created;
    } else {
      TABLE[slot] = created;
    }
    return created;
  }

  public static Entry getOrBuildComponent(
      Font font,
      Component component,
      float x,
      float y,
      int color,
      boolean dropShadow,
      Font.DisplayMode displayMode,
      int bgColor) {
    if (font == null || component == null || displayMode == null) return null;
    if (font != cachedFont) {
      clear();
      cachedFont = font;
    }
    int cHash = component.hashCode();
    int hash = cHash * 31
        ^ Float.floatToRawIntBits(x) * 17
        ^ Float.floatToRawIntBits(y) * 131
        ^ color * 7
        ^ bgColor * 19
        ^ (dropShadow ? 0x5A5A : 0)
        ^ (displayMode.ordinal() << 24);
    hash ^= hash >>> 16;
    int slot = hash & MASK;

    Entry e = COMP_TABLE[slot];
    if (e != null
        && e.compHash == cHash
        && e.x == x
        && e.y == y
        && e.color == color
        && e.bgColor == bgColor
        && e.dropShadow == dropShadow
        && e.displayMode == displayMode
        && (e.component == component || component.equals(e.component))) {
      return e;
    }
    int slot2 = (slot + 1) & MASK;
    Entry e2 = COMP_TABLE[slot2];
    if (e2 != null
        && e2.compHash == cHash
        && e2.x == x
        && e2.y == y
        && e2.color == color
        && e2.bgColor == bgColor
        && e2.dropShadow == dropShadow
        && e2.displayMode == displayMode
        && (e2.component == component || component.equals(e2.component))) {
      return e2;
    }

    int tag = cHash ^ (hash << 1) | 1;
    int seenSlot = hash & SEEN_MASK;
    if (COMP_SEEN_HASH[seenSlot] != tag) {
      COMP_SEEN_HASH[seenSlot] = tag;
      return null;
    }

    FormattedCharSequence seq = component.getVisualOrderText();
    Bucket[] buckets = capture(font, seq, x, y, color, dropShadow, displayMode, bgColor);
    if (buckets == null) return null;
    Entry created = new Entry(seq, component, cHash, x, y, color, bgColor, dropShadow, displayMode, buckets);
    if (e == null) {
      COMP_TABLE[slot] = created;
    } else if (e2 == null) {
      COMP_TABLE[slot2] = created;
    } else {
      COMP_TABLE[slot] = created;
    }
    return created;
  }

  public static void emitEntry(Entry entry, Matrix4fc pose, int lightCoords, ConsumerProvider provider) {
    Bucket[] buckets = entry.buckets;
    for (int i = 0; i < buckets.length; i++) {
      Bucket b = buckets[i];
      if (b.quadCount <= 0) continue;
      @@TEXT_CACHE_NATIVE_SUBMIT@@
      VertexConsumer vc = provider.get(b.renderType);
      if (vc == null) continue;
      if (vc instanceof FastEntityVertexWriter
          && ((FastEntityVertexWriter) vc).nightclient$emitFlatTextQuads(
              b.xyzuv, b.abgrs, b.quadCount, pose, lightCoords)) {
        continue;
      }
      emitFallback(vc, b, pose, lightCoords);
    }
  }

  private static void emitFallback(
      VertexConsumer vc, Bucket b, Matrix4fc pose, int lightCoords) {
    final float[] xyzuv = b.xyzuv;
    final int[] argbs = b.argbs;
    final int vertCount = b.quadCount << 2;
    final float m00 = pose.m00(), m10 = pose.m10(), m30 = pose.m30();
    final float m01 = pose.m01(), m11 = pose.m11(), m31 = pose.m31();
    final float m02 = pose.m02(), m12 = pose.m12(), m32 = pose.m32();
    int fi = 0;
    if (b.hasUv) {
      if (b.flatZ0) {
        for (int vi = 0; vi < vertCount; vi++) {
          float x = xyzuv[fi];
          float y = xyzuv[fi + 1];
          vc.addVertex(
                  m00 * x + m10 * y + m30,
                  m01 * x + m11 * y + m31,
                  m02 * x + m12 * y + m32)
              .setColor(argbs[vi])
              .setUv(xyzuv[fi + 3], xyzuv[fi + 4])
              .setLight(lightCoords);
          fi += 5;
        }
      } else {
        final float m20 = pose.m20(), m21 = pose.m21(), m22 = pose.m22();
        for (int vi = 0; vi < vertCount; vi++) {
          float x = xyzuv[fi];
          float y = xyzuv[fi + 1];
          float z = xyzuv[fi + 2];
          vc.addVertex(
                  m00 * x + m10 * y + m20 * z + m30,
                  m01 * x + m11 * y + m21 * z + m31,
                  m02 * x + m12 * y + m22 * z + m32)
              .setColor(argbs[vi])
              .setUv(xyzuv[fi + 3], xyzuv[fi + 4])
              .setLight(lightCoords);
          fi += 5;
        }
      }
    } else if (b.flatZ0) {
      for (int vi = 0; vi < vertCount; vi++) {
        float x = xyzuv[fi];
        float y = xyzuv[fi + 1];
        vc.addVertex(
                m00 * x + m10 * y + m30,
                m01 * x + m11 * y + m31,
                m02 * x + m12 * y + m32)
            .setColor(argbs[vi])
            .setLight(lightCoords);
        fi += 5;
      }
    } else {
      final float m20 = pose.m20(), m21 = pose.m21(), m22 = pose.m22();
      for (int vi = 0; vi < vertCount; vi++) {
        float x = xyzuv[fi];
        float y = xyzuv[fi + 1];
        float z = xyzuv[fi + 2];
        vc.addVertex(
                m00 * x + m10 * y + m20 * z + m30,
                m01 * x + m11 * y + m21 * z + m31,
                m02 * x + m12 * y + m22 * z + m32)
            .setColor(argbs[vi])
            .setLight(lightCoords);
        fi += 5;
      }
    }
  }

  @FunctionalInterface
  public interface ConsumerProvider {
    VertexConsumer get(RenderType rt);
  }

  private static final class CaptureBucket {
    final RenderType rt;
    float[] xyzuv = new float[80];
    int[] abgrs = new int[16];
    int[] argbs = new int[16];
    int vertCount;
    boolean hasUv;
    boolean flatZ0 = true;

    CaptureBucket(RenderType rt) {
      this.rt = rt;
    }

    void add(float x, float y, float z, float u, float v, int abgr, int argb, boolean uvSet) {
      int vc = this.vertCount;
      if (vc >= this.abgrs.length) {
        int nextLen = this.abgrs.length << 1;
        this.abgrs = Arrays.copyOf(this.abgrs, nextLen);
        this.argbs = Arrays.copyOf(this.argbs, nextLen);
        this.xyzuv = Arrays.copyOf(this.xyzuv, this.xyzuv.length << 1);
      }
      int fi = vc * 5;
      this.xyzuv[fi] = x;
      this.xyzuv[fi + 1] = y;
      this.xyzuv[fi + 2] = z;
      this.xyzuv[fi + 3] = u;
      this.xyzuv[fi + 4] = v;
      this.abgrs[vc] = abgr;
      this.argbs[vc] = argb;
      if (uvSet) this.hasUv = true;
      if (z != 0.0f) this.flatZ0 = false;
      this.vertCount = vc + 1;
    }
  }

  private static final class CaptureConsumer implements VertexConsumer {
    final List<CaptureBucket> buckets = new ArrayList<CaptureBucket>(4);
    CaptureBucket active;
    boolean hasPending;
    float px, py, pz, pu, pv;
    int pAbgr;
    int pArgb;
    boolean pHasUv;
    boolean failed;

    void reset() {
      buckets.clear();
      active = null;
      hasPending = false;
      failed = false;
    }

    void flushPending() {
      if (hasPending && active != null) {
        active.add(px, py, pz, pu, pv, pAbgr, pArgb, pHasUv);
        hasPending = false;
      }
    }

    CaptureConsumer select(RenderType rt) {
      flushPending();
      if (active != null && active.rt == rt) {
        return this;
      }
      for (int i = 0; i < buckets.size(); i++) {
        CaptureBucket b = buckets.get(i);
        if (b.rt == rt) {
          active = b;
          return this;
        }
      }
      CaptureBucket created = new CaptureBucket(rt);
      buckets.add(created);
      active = created;
      return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      flushPending();
      px = x;
      py = y;
      pz = z;
      pu = 0.0f;
      pv = 0.0f;
      pAbgr = -1;
      pArgb = -1;
      pHasUv = false;
      hasPending = true;
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      int ri = r & 0xFF;
      int gi = g & 0xFF;
      int bi = b & 0xFF;
      int ai = a & 0xFF;
      pAbgr = ri | (gi << 8) | (bi << 16) | (ai << 24);
      pArgb = (ai << 24) | (ri << 16) | (gi << 8) | bi;
      return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
      pArgb = argb;
      pAbgr = ARGB.toABGR(argb);
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      pu = u;
      pv = v;
      pHasUv = true;
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this;
    }

    public VertexConsumer setUv3(float u, float v) {
      return this;
    }

    @Override
    public VertexConsumer setNormal(float nx, float ny, float nz) {
      return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
      return this;
    }
  }

  private static final CaptureConsumer CAPTURER = new CaptureConsumer();

  private static Bucket[] finalizeCapture() {
    CAPTURER.flushPending();
    if (CAPTURER.failed) return null;
    int n = CAPTURER.buckets.size();
    int nonEmpty = 0;
    for (int i = 0; i < n; i++) {
      int vc = CAPTURER.buckets.get(i).vertCount;
      if ((vc & 3) != 0) return null;
      if (vc > 0) nonEmpty++;
    }
    if (nonEmpty == 0) return new Bucket[0];
    Bucket[] out = new Bucket[nonEmpty];
    int oi = 0;
    for (int i = 0; i < n; i++) {
      CaptureBucket cb = CAPTURER.buckets.get(i);
      if (cb.vertCount == 0) continue;
      out[oi++] = new Bucket(
          cb.rt,
          Arrays.copyOf(cb.xyzuv, cb.vertCount * 5),
          Arrays.copyOf(cb.abgrs, cb.vertCount),
          Arrays.copyOf(cb.argbs, cb.vertCount),
          cb.vertCount >> 2,
          cb.hasUv,
          cb.flatZ0);
    }
    return out;
  }

  @@TEXT_CACHE_CAPTURE_BODY@@
}
