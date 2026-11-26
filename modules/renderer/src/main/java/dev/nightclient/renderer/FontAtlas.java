package dev.nightclient.renderer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class FontAtlas {
  public interface GlyphConsumer {
    void glyph(float x, float y, float width, float height, int pixelX, int pixelY, int pixelWidth,
        int pixelHeight);
  }

  private static final String DEFAULT_TEXTURE = "nightclient:textures/gui/font.png";
  private static final FontAtlas INSTANCE =
      new FontAtlas("/assets/nightclient/font/atlas.properties", DEFAULT_TEXTURE);
  private static final ConcurrentHashMap<String, FontAtlas> FAMILIES =
      new ConcurrentHashMap<String, FontAtlas>();
  private static final FontAtlas STUDIO_SANS = loadBundledFamily("studio-sans");
  private static final FontAtlas STUDIO_SANS_MEDIUM = loadBundledFamily("studio-sans-medium");
  private final Glyph[][] glyphs = new Glyph[2][65536];
  private final Glyph[][] smallGlyphs = new Glyph[2][65536];
  private final int width;
  private final int height;
  private final float emSize;
  private final float smallEmSize;
  private final float ascent;
  private final float lineHeight;
  private final String textureResourceId;

  private final ThreadLocal<WidthCache> widthCache = new ThreadLocal<WidthCache>() {
    @Override
    protected WidthCache initialValue() {
      return new WidthCache();
    }
  };

  private FontAtlas(String resource, String defaultTexture) {
    Properties properties = new Properties();
    try (InputStream stream = FontAtlas.class.getResourceAsStream(resource)) {
      if (stream == null) throw new IOException("Missing bundled font metrics");
      properties.load(stream);
    } catch (IOException error) {
      throw new IllegalStateException("Cannot load NightClient font", error);
    }
    width = Integer.parseInt(properties.getProperty("width"));
    height = Integer.parseInt(properties.getProperty("height"));
    emSize = Float.parseFloat(properties.getProperty("em"));
    smallEmSize = Float.parseFloat(properties.getProperty("smallEm", "24"));
    ascent = Float.parseFloat(properties.getProperty("ascent"));
    lineHeight = Float.parseFloat(properties.getProperty("lineHeight"));
    textureResourceId = properties.getProperty("texture", defaultTexture);
    if (!textureResourceId.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")
        || textureResourceId.contains(".."))
      throw new IllegalStateException("Invalid font atlas texture resource");
    for (String key : properties.stringPropertyNames()) {
      boolean small = key.startsWith("sr.") || key.startsWith("sb.");
      if (!small && !key.startsWith("r.") && !key.startsWith("b.")) continue;
      int code = Integer.parseInt(key.substring(small ? 3 : 2));
      String[] v = properties.getProperty(key).split(",");
      (small ? smallGlyphs : glyphs)[key.charAt(small ? 1 : 0) == 'b' ? 1 : 0][code] =
          new Glyph(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]),
              Integer.parseInt(v[3]), Float.parseFloat(v[4]), Float.parseFloat(v[5]),
              Float.parseFloat(v[6]));
    }
  }

  public static FontAtlas get() {
    return INSTANCE;
  }

  private static FontAtlas loadBundledFamily(String family) {
    String resource = "/assets/nightclient/font/" + family + "/atlas.properties";
    FontAtlas loaded = INSTANCE;
    try (InputStream stream = FontAtlas.class.getResourceAsStream(resource)) {
      if (stream != null)
        loaded = new FontAtlas(resource, "nightclient:textures/gui/fonts/" + family + ".png");
    } catch (IOException error) {
      throw new IllegalStateException("Cannot inspect font family: " + family, error);
    }
    FAMILIES.put(family, loaded);
    return loaded;
  }

  public static FontAtlas get(String family) {
    if (family == null || family.isEmpty() || "default".equals(family) || "onest".equals(family))
      return INSTANCE;
    if ("studio-sans".equals(family)) return STUDIO_SANS;
    if ("studio-sans-medium".equals(family)) return STUDIO_SANS_MEDIUM;
    FontAtlas existing = FAMILIES.get(family);
    if (existing != null) return existing;
    if (!family.matches("[a-z][a-z0-9-]{0,63}")) return INSTANCE;
    String resource = "/assets/nightclient/font/" + family + "/atlas.properties";
    FontAtlas loaded = INSTANCE;
    try (InputStream stream = FontAtlas.class.getResourceAsStream(resource)) {
      if (stream != null)
        loaded = new FontAtlas(resource, "nightclient:textures/gui/fonts/" + family + ".png");
    } catch (IOException error) {
      throw new IllegalStateException("Cannot inspect font family: " + family, error);
    }
    if (FAMILIES.size() < 64) FAMILIES.putIfAbsent(family, loaded);
    return loaded;
  }

  public String textureResourceId() {
    return textureResourceId;
  }

  public int atlasWidth() {
    return width;
  }

  public int atlasHeight() {
    return height;
  }

  public float baselineRatio() {
    return emSize > 0 ? ascent / emSize : 0.85f;
  }

  public boolean hasGlyph(int codePoint, boolean bold) {
    return codePoint >= 0 && codePoint < 65536 && glyphs[bold ? 1 : 0][codePoint] != null;
  }

  public static boolean isIgnorableFormatChar(int codePoint) {
    return codePoint == 0xFE0E || codePoint == 0xFE0F || codePoint == 0x200B
        || codePoint == 0x200C || codePoint == 0x200D || codePoint == 0x200E
        || codePoint == 0x200F || codePoint == 0x2060 || codePoint == 0xFEFF
        || codePoint == 0x00AD;
  }

  public static boolean isTrailingClusterCodePoint(int codePoint) {
    if ((codePoint >= 0xFE00 && codePoint <= 0xFE0F)
        || (codePoint >= 0xE0100 && codePoint <= 0xE01EF)
        || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF)
        || codePoint == 0x20E3) {
      return true;
    }
    int type = Character.getType(codePoint);
    return type == Character.NON_SPACING_MARK
        || type == Character.ENCLOSING_MARK
        || type == Character.COMBINING_SPACING_MARK;
  }

  public boolean supportsAll(String text, boolean bold) {
    if (text == null || text.isEmpty()) return true;
    Glyph[] table = glyphs[bold ? 1 : 0];
    int len = text.length();
    for (int i = 0; i < len;) {
      char c = text.charAt(i);
      if (Character.isSurrogate(c)) return false;
      if (c == '\n') {
        i++;
        continue;
      }
      if (isTrailingClusterCodePoint(c) || isIgnorableFormatChar(c)) return false;
      if (table[c] == null) return false;
      i++;
    }
    return true;
  }

  public float width(String text, float size, boolean bold) {
    return width(text, size, bold, 0);
  }

  public float width(String text, float size, boolean bold, float tracking) {
    if (text == null || text.isEmpty()) return 0;
    if (text.length() > 256) return measure(text, size, bold, tracking);
    WidthCache cache = widthCache.get();
    int sizeBits = Float.floatToIntBits(size), trackingBits = Float.floatToIntBits(tracking);
    int hash = text.hashCode();
    hash = 31 * hash + sizeBits;
    hash = 31 * hash + trackingBits;
    hash = 31 * hash + (bold ? 1 : 0);
    int slot = hash & (WidthCache.SIZE - 1);
    if (cache.text[slot] != null && cache.text[slot].equals(text) && cache.size[slot] == sizeBits
        && cache.tracking[slot] == trackingBits && cache.bold[slot] == bold)
      return cache.value[slot];
    float value = measure(text, size, bold, tracking);
    cache.text[slot] = text;
    cache.size[slot] = sizeBits;
    cache.tracking[slot] = trackingBits;
    cache.bold[slot] = bold;
    cache.value[slot] = value;
    return value;
  }

  private float measure(String text, float size, boolean bold, float tracking) {
    float current = 0, max = 0, scale = size / emSize;
    boolean previous = false;
    int len = text.length();
    for (int i = 0; i < len;) {
      char c = text.charAt(i);
      int code;
      if (Character.isSurrogate(c)) {
        code = text.codePointAt(i);
        i += Character.charCount(code);
      } else {
        code = c;
        i++;
      }
      if (code == '\n') {
        max = Math.max(max, current);
        current = 0;
        previous = false;
        continue;
      }
      if (isIgnorableFormatChar(code)) continue;
      Glyph g = glyph(code, bold);
      if (previous) current += tracking;
      current += g.advance * scale;
      previous = true;
    }
    return Math.max(max, current);
  }

  private static final class WidthCache {
    static final int SIZE = 2048;
    final String[] text = new String[SIZE];
    final int[] size = new int[SIZE];
    final int[] tracking = new int[SIZE];
    final boolean[] bold = new boolean[SIZE];
    final float[] value = new float[SIZE];
  }

  public void visitGlyphs(
      String text, float x, float y, float size, boolean bold, GlyphConsumer consumer) {
    visitGlyphs(text, x, y, size, bold, 0, consumer);
  }

  public void visitGlyphs(String text, float x, float y, float size, boolean bold, float tracking,
      GlyphConsumer consumer) {
    visitGlyphs(text, x, y, size, bold, tracking, 1, consumer);
  }

  public void visitGlyphs(String text, float x, float y, float size, boolean bold, float tracking,
      float density, GlyphConsumer consumer) {
    if (text == null || text.isEmpty() || size <= 0) return;
    boolean small = size * Math.max(1, density) <= 28;
    float scale = size / emSize, rasterScale = size / (small ? smallEmSize : emSize);
    float penX = x, penY = y + ascent * scale;
    boolean previous = false;
    int len = text.length();
    for (int i = 0; i < len;) {
      char c = text.charAt(i);
      int code;
      if (Character.isSurrogate(c)) {
        code = text.codePointAt(i);
        i += Character.charCount(code);
      } else {
        code = c;
        i++;
      }
      if (code == '\n') {
        penX = x;
        penY += lineHeight * scale;
        previous = false;
        continue;
      }
      if (isIgnorableFormatChar(code)) continue;
      Glyph metrics = glyph(code, bold), g = small ? smallGlyph(code, bold) : metrics;
      if (previous) penX += tracking;

      if (g.w > 0 && g.h > 0)
        consumer.glyph(penX + (g.bearingX - 1) * rasterScale, penY + (g.bearingY - 1) * rasterScale,
            (g.w + 2) * rasterScale, (g.h + 2) * rasterScale, g.x - 1, g.y - 1, g.w + 2, g.h + 2);
      penX += metrics.advance * scale;
      previous = true;
    }
  }

  private Glyph smallGlyph(int code, boolean bold) {
    Glyph g = code < 65536 ? smallGlyphs[bold ? 1 : 0][code] : null;
    return g == null ? smallGlyphs[bold ? 1 : 0]['?'] : g;
  }

  private Glyph glyph(int code, boolean bold) {
    Glyph g = code < 65536 ? glyphs[bold ? 1 : 0][code] : null;
    return g == null ? glyphs[bold ? 1 : 0]['?'] : g;
  }

  private static final class Glyph {
    final int x, y, w, h;
    final float bearingX, bearingY, advance;

    Glyph(int x, int y, int w, int h, float bearingX, float bearingY, float advance) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.bearingX = bearingX;
      this.bearingY = bearingY;
      this.advance = advance;
    }
  }
}
