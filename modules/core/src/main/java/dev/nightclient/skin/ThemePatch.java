package dev.nightclient.skin;

import dev.nightclient.config.Theme;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ThemePatch {
  private static final java.util.Set<String> THEME_KEYS =
      keys("theme-name|blur|font-weight|font-family|easing|transition-duration|enter-duration|exit-"
           + "duration|animation-stagger|animate-layout|animate-value|enter-scale|hover-scale|"
           + "press-scale|enter-opacity|enter-offset-x|enter-offset-y");
  private static final java.util.Set<String> LAYOUT_KEYS =
      keys("shadow|content-opacity|radius-top-left|radius-top-right|radius-bottom-left|radius-"
           + "bottom-right|pixel-origin-x|fade-width|scroll-speed|line-start-x|line-start-y|line-"
           + "end-x|line-end-y|hud-scale|viewport-min-width|viewport-min-height|scrollbar-width|"
           + "scroll-step|columns|min-cell-width|width|height|min-width|min-height|max-width|max-"
           + "height|x|y|left|top|right|bottom|gap|anchor-x|anchor-y|anchor-gap|z-index|flex|grow|"
           + "line-height|font-size|font-scale|line-width|stroke-width|size");
  private static final java.util.regex.Pattern NUMERIC_COLOR =
      java.util.regex.Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?");
  private static final java.util.regex.Pattern FONT_FAMILY =
      java.util.regex.Pattern.compile("[a-z][a-z0-9-]{0,63}");
  private static final java.util.Set<String> REMOVED_SURFACE_KEYS =
      keys("material|variant|border-style|leather|leather-border|leather-size|leather-blur|shadows|"
           + "shadow-radius|shadow-offset-y|legacy-glow|legacy-glow-radius|glow|glow-radius|glow-"
           + "color|glow-strength|accent-light|accent-light-color|light-color|light-strength|light-"
           + "x|light-y|light-size|noise|noise-scale");
  private static final java.util.Set<String> DURATION_KEYS =
      keys("transition-duration|enter-duration|exit-duration|animation-stagger");
  private static final java.util.Set<String> ANIMATE_KEYS = keys("animate-layout|animate-value");
  private static final java.util.Set<String> SCALE_KEYS =
      keys("enter-scale|hover-scale|press-scale|enter-opacity|enter-offset-x|enter-offset-y");
  private static final java.util.Set<String> FILL_KEYS =
      keys("fill-top-left|fill-top-right|fill-bottom-left|fill-bottom-right");
  private static final java.util.Set<String> POSITION_VALUES = keys("relative|absolute|overlay");
  private static final java.util.Set<String> ANCHOR_SIDES = keys("below|above|left|right");
  private static final java.util.Set<String> ANCHOR_ALIGNS = keys("start|center|end");
  private static final java.util.Set<String> FONT_WEIGHTS =
      keys("regular|normal|400|semibold|bold|600");
  private static final java.util.Set<String> TEXT_MOTIONS = keys("none|numeric");
  private static final java.util.Set<String> TEXT_OVERFLOWS = keys("clip|ellipsis|marquee|wrap");

  private static java.util.Set<String> keys(String values) {
    return new java.util.HashSet<String>(java.util.Arrays.asList(values.split("\\|")));
  }

  final Map<String, String> source;
  private final Map<String, Object> values;
  final Map<String, Object> appliedValues;
  final boolean empty;
  Object cacheKey;

  ThemePatch(Map<String, String> source) throws IOException {
    this.source = Collections.unmodifiableMap(new LinkedHashMap<String, String>(source));
    Map<String, Object> typed = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, String> e : source.entrySet()) {
      String key = e.getKey(), v = e.getValue();
      if (REMOVED_SURFACE_KEYS.contains(key))
        throw new IOException("Removed surface property: " + key);
      if (v.contains("{{")) continue;
      if ("theme-name".equals(key))
        typed.put(key, v);
      else if (colorKey(key))
        typed.put(key, color(v));
      else if (booleanKey(key))
        typed.put(key, bool(v));
      else if (numberKey(key))
        typed.put(key, number(v));
      else if ("blur".equals(key))
        typed.put(key, isBoolean(v) ? bool(v) : number(v));
      else if ("font-weight".equals(key)) {
        if (!FONT_WEIGHTS.contains(v)) throw new IOException("Unsupported font weight: " + v);
        typed.put(key,
            ("regular".equals(v) || "normal".equals(v) || "400".equals(v)) ? "regular" : "bold");
      } else if ("font-family".equals(key)) {
        if (!FONT_FAMILY.matcher(v).matches()) throw new IOException("Invalid font family: " + v);
        typed.put(key, v);
      } else if ("easing".equals(key)) {
        if (!dev.nightclient.config.Easing.supported(v))
          throw new IOException("Unsupported easing: " + v);
        typed.put(key, v);
      } else if (DURATION_KEYS.contains(key)) {
        float duration = duration(v);
        typed.put(key, duration);
        if (duration < 0 || duration > 5)
          throw new IOException("Animation duration must be 0..5 seconds");
      } else if (ANIMATE_KEYS.contains(key))
        typed.put(key, bool(v));
      else if (SCALE_KEYS.contains(key))
        typed.put(key, number(v));
      else if ("scroll-duration".equals(key)) {
        float seconds = duration(v);
        if (seconds < 0 || seconds > 5)
          throw new IOException("Scroll duration must be 0..5 seconds");
      } else if ("scroll-pause".equals(key)) {
        float seconds = duration(v);
        if (seconds < 0 || seconds > 60)
          throw new IOException("Marquee pause must be 0..60 seconds");
      } else if (FILL_KEYS.contains(key))
        color(v);
      else if ("text-motion".equals(key) && !TEXT_MOTIONS.contains(v))
        throw new IOException("Unsupported text motion: " + v);
      else if ("text-overflow".equals(key) && !TEXT_OVERFLOWS.contains(v))
        throw new IOException("Unsupported text overflow: " + v);
      else if ("layout-reflow".equals(key) || "cast-effects".equals(key)
          || "masonry-stable".equals(key) || "intrinsic-height".equals(key)
          || "animate-enter".equals(key) || "retain-exit".equals(key))
        bool(v);
      else if ("position".equals(key) && !POSITION_VALUES.contains(v))
        throw new IOException("Unsupported position: " + v);
      else if ("anchor-side".equals(key) && !ANCHOR_SIDES.contains(v))
        throw new IOException("Unsupported anchor side: " + v);
      else if ("anchor-align".equals(key) && !ANCHOR_ALIGNS.contains(v))
        throw new IOException("Unsupported anchor alignment: " + v);
      else if (layoutNumberKey(key) && !v.equals("auto") && !v.startsWith("{{"))
        number(v);
    }
    values = Collections.unmodifiableMap(typed);
    Map<String, Object> applied = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> e : typed.entrySet())
      if (themeProperty(e.getKey())) applied.put(e.getKey(), e.getValue());
    appliedValues = applied.isEmpty() ? Collections.<String, Object>emptyMap()
                                      : Collections.unmodifiableMap(applied);
    empty = appliedValues.isEmpty();
  }

  static boolean themeProperty(String key) {
    return colorKey(key) && !key.equals("fill") && !key.equals("stroke")
        || booleanKey(key) && !key.equals("visible") || numberKey(key) || THEME_KEYS.contains(key);
  }

  private static boolean colorKey(String k) {
    return k.equals("background") || k.equals("background-color") || k.equals("surface")
        || k.equals("raised") || k.equals("color") || k.equals("text") || k.equals("muted")
        || k.equals("accent") || k.equals("secondary-accent") || k.equals("border-color")
        || k.equals("fill") || k.equals("stroke");
  }

  private static boolean booleanKey(String k) {
    return k.equals("grain") || k.equals("visible");
  }

  private static boolean numberKey(String k) {
    return k.equals("radius") || k.equals("border-radius") || k.equals("border-width")
        || k.equals("opacity") || k.equals("background-opacity") || k.equals("tint-opacity")
        || k.equals("surface-opacity") || k.equals("raised-opacity") || k.equals("blur-radius")
        || k.equals("blur-quality") || k.equals("blur-mix") || k.equals("glass-mix")
        || k.equals("accent-gradient") || k.equals("font-tracking") || k.equals("letter-spacing")
        || k.equals("grain-amount") || k.equals("grain-scale");
  }

  private static boolean layoutNumberKey(String k) {
    return LAYOUT_KEYS.contains(k);
  }

  static float duration(String value) throws IOException {
    value = value.trim();
    float multiplier = 1;
    if (value.endsWith("ms")) {
      value = value.substring(0, value.length() - 2);
      multiplier = .001f;
    } else if (value.endsWith("s"))
      value = value.substring(0, value.length() - 1);
    return number(value) * multiplier;
  }

  static float number(String value) throws IOException {
    value = value.trim();
    boolean percentage = value.endsWith("%");
    if (percentage)
      value = value.substring(0, value.length() - 1);
    else if (value.endsWith("px"))
      value = value.substring(0, value.length() - 2);
    try {
      float v = Float.parseFloat(value);
      if (!Float.isFinite(v) || Math.abs(v) > 100000) throw new NumberFormatException();
      return percentage ? v / 100 : v;
    } catch (NumberFormatException e) {
      throw new IOException("Invalid style number: " + value);
    }
  }

  private static boolean isBoolean(String v) {
    return v.equals("true") || v.equals("false") || v.equals("on") || v.equals("off");
  }

  static boolean bool(String value) throws IOException {
    if (value.equals("true") || value.equals("on")) return true;
    if (value.equals("false") || value.equals("off")) return false;
    throw new IOException("Invalid style boolean: " + value);
  }

  private static final java.util.concurrent.ConcurrentHashMap<String, Integer> COLORS =
      new java.util.concurrent.ConcurrentHashMap<String, Integer>();

  static int color(String value) throws IOException {
    boolean cacheable = value.length() <= 256;
    Integer known = cacheable ? COLORS.get(value) : null;
    if (known != null) return known;
    int parsed = parseColor(value);
    if (cacheable) {
      if (COLORS.size() >= 4096) COLORS.clear();
      COLORS.putIfAbsent(value, parsed);
    }
    return parsed;
  }

  private static int parseColor(String value) throws IOException {
    value = value.trim().toLowerCase(Locale.ROOT);
    if ("transparent".equals(value)) return 0x00ffffff;
    if ("white".equals(value)) return 0xffffffff;
    if ("black".equals(value)) return 0xff000000;
    if (!value.isEmpty()
        && (value.charAt(0) == '-' || value.charAt(0) >= '0' && value.charAt(0) <= '9')
        && NUMERIC_COLOR.matcher(value).matches()) {
      try {
        return (int) (long) Double.parseDouble(value);
      } catch (NumberFormatException failure) {
        throw new IOException("Invalid ARGB value", failure);
      }
    }
    if (value.startsWith("#")) {
      String h = value.substring(1);
      if (!shortHex(h)) throw new IOException("Invalid style color: " + value);
      if (h.length() == 3 || h.length() == 4) {
        StringBuilder out = new StringBuilder();
        for (char c : h.toCharArray()) out.append(c).append(c);
        h = out.toString();
      }
      try {
        if (h.length() == 6) return 0xff000000 | (int) Long.parseLong(h, 16);
        if (h.length() == 8) {
          long rgba = Long.parseLong(h, 16);
          return (int) ((rgba >>> 8) | ((rgba & 255) << 24));
        }
      } catch (NumberFormatException ignored) {
      }
    }
    boolean rgba = value.startsWith("rgba(");
    if (rgba || value.startsWith("rgb(")) {
      if (!value.endsWith(")")) throw new IOException("Invalid style color: " + value);
      String[] c = value.substring(rgba ? 5 : 4, value.length() - 1).split(",");
      if (c.length != (rgba ? 4 : 3)) throw new IOException("Invalid style color: " + value);
      int r = channel(c[0]), g = channel(c[1]), b = channel(c[2]),
          a = rgba ? Math.round(clamp(number(c[3]), 0, 1) * 255) : 255;
      return a << 24 | r << 16 | g << 8 | b;
    }
    throw new IOException("Invalid style color: " + value);
  }

  private static boolean shortHex(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) return false;
    }
    return true;
  }

  private static int channel(String value) throws IOException {
    return Math.round(
        clamp(value.trim().endsWith("%") ? number(value) * 255 : number(value), 0, 255));
  }

  private static float clamp(float v, float min, float max) {
    return Math.max(min, Math.min(max, v));
  }

  private float n(Object value, float min, float max) {
    return clamp(((Number) value).floatValue(), min, max);
  }

  int colorValue(String key, int fallback) {
    Object value = values.get(key);
    return value instanceof Integer ? (Integer) value : fallback;
  }

  boolean isEmpty() {
    return empty;
  }

  boolean affectsBackdrop() {
    return values.containsKey("blur") || values.containsKey("blur-radius")
        || values.containsKey("blur-mix");
  }

  static void applyDynamic(Theme t, String k, String raw) throws IOException {
    if (k == null || raw == null) return;
    if (REMOVED_SURFACE_KEYS.contains(k))
      throw new IOException("Removed surface property: " + k);
    if (raw.contains("{{")) return;
    switch (k) {
      case "theme-name":
        t.name = raw;
        return;
      case "font-family":
        if (!FONT_FAMILY.matcher(raw).matches()) throw new IOException("Invalid font family: " + raw);
        t.fontFamily = raw;
        return;
      case "font-weight":
        if (!FONT_WEIGHTS.contains(raw)) throw new IOException("Unsupported font weight: " + raw);
        t.fontWeight =
            ("regular".equals(raw) || "normal".equals(raw) || "400".equals(raw)) ? "regular"
                                                                                 : "bold";
        return;
      case "easing":
        if (!dev.nightclient.config.Easing.supported(raw))
          throw new IOException("Unsupported easing: " + raw);
        t.motion.easing = raw;
        return;
      case "transition-duration": {
        float d = duration(raw);
        if (d < 0 || d > 5) throw new IOException("Animation duration must be 0..5 seconds");
        t.motion.duration = d;
        return;
      }
      case "enter-duration": {
        float d = duration(raw);
        if (d < 0 || d > 5) throw new IOException("Animation duration must be 0..5 seconds");
        t.motion.enterDuration = d;
        return;
      }
      case "exit-duration": {
        float d = duration(raw);
        if (d < 0 || d > 5) throw new IOException("Animation duration must be 0..5 seconds");
        t.motion.exitDuration = d;
        return;
      }
      case "animation-stagger": {
        float d = duration(raw);
        if (d < 0 || d > 5) throw new IOException("Animation duration must be 0..5 seconds");
        t.motion.stagger = clamp(d, 0, .2f);
        return;
      }
      case "enter-scale":
        t.motion.enterScale = clamp(number(raw), .01f, 2);
        return;
      case "hover-scale":
        t.motion.hoverScale = clamp(number(raw), .5f, 1.5f);
        return;
      case "press-scale":
        t.motion.pressScale = clamp(number(raw), .5f, 1.5f);
        return;
      case "enter-opacity":
        t.motion.enterOpacity = clamp(number(raw), 0, 1);
        return;
      case "enter-offset-x":
        t.motion.enterOffsetX = clamp(number(raw), -100, 100);
        return;
      case "enter-offset-y":
        t.motion.enterOffsetY = clamp(number(raw), -100, 100);
        return;
      case "animate-layout":
        t.motion.layout = bool(raw);
        return;
      case "animate-value":
        t.motion.value = bool(raw);
        return;
      case "background":
      case "background-color":
        t.background = t.surface = t.raised = color(raw);
        return;
      case "surface":
        t.surface = color(raw);
        return;
      case "raised":
        t.raised = color(raw);
        return;
      case "color":
      case "text":
        t.text = color(raw);
        return;
      case "muted":
        t.muted = color(raw);
        return;
      case "accent":
        t.accent = color(raw);
        return;
      case "secondary-accent":
        t.secondaryAccent = color(raw);
        return;
      case "border-color":
        t.border = color(raw);
        return;
      case "radius":
      case "border-radius":
        t.radius = clamp(number(raw), 0, 80);
        return;
      case "border-width":
        t.borderWidth = clamp(number(raw), 0, 8);
        return;
      case "opacity":
      case "background-opacity":
        t.backgroundOpacity = clamp(number(raw), 0, 1);
        return;
      case "surface-opacity":
        t.surfaceOpacity = clamp(number(raw), 0, 1);
        return;
      case "raised-opacity":
        t.raisedOpacity = clamp(number(raw), 0, 1);
        return;
      case "tint-opacity":
        t.tintOpacity = clamp(number(raw), -1, 1);
        return;
      case "blur":
        if (isBoolean(raw))
          t.effects.backgroundBlur = bool(raw);
        else {
          t.blurRadius = clamp(number(raw), 0, 40);
          t.effects.backgroundBlur = t.blurRadius > 0;
        }
        return;
      case "blur-radius":
        t.blurRadius = clamp(number(raw), 0, 40);
        return;
      case "blur-quality":
        t.blurQuality = Math.round(clamp(number(raw), 1, 6));
        return;
      case "blur-mix":
        t.blurMix = clamp(number(raw), 0, 1);
        return;
      case "glass-mix":
        t.glassMix = clamp(number(raw), -1, 1);
        return;
      case "accent-gradient":
        t.accentGradient = clamp(number(raw), 0, 1);
        return;
      case "font-tracking":
      case "letter-spacing":
        t.fontTracking = clamp(number(raw), -1, 3);
        return;
      case "grain":
        t.effects.grain = bool(raw);
        return;
      case "grain-amount":
        t.effects.grainAmount = clamp(number(raw), 0, .15f);
        return;
      case "grain-scale":
        t.effects.grainScale = clamp(number(raw), .5f, 4);
        return;
      default:
        break;
    }
  }

  void apply(Theme t) {
    for (Map.Entry<String, Object> e : values.entrySet()) {
      String k = e.getKey();
      Object v = e.getValue();
      switch (k) {
        case "theme-name":
          t.name = (String) v;
          break;
        case "font-family":
          t.fontFamily = (String) v;
          break;
        case "transition-duration":
          t.motion.duration = n(v, 0, 5);
          break;
        case "enter-duration":
          t.motion.enterDuration = n(v, 0, 5);
          break;
        case "exit-duration":
          t.motion.exitDuration = n(v, 0, 5);
          break;
        case "animation-stagger":
          t.motion.stagger = n(v, 0, .2f);
          break;
        case "enter-scale":
          t.motion.enterScale = n(v, .01f, 2);
          break;
        case "easing":
          t.motion.easing = (String) v;
          break;
        case "hover-scale":
          t.motion.hoverScale = n(v, .5f, 1.5f);
          break;
        case "press-scale":
          t.motion.pressScale = n(v, .5f, 1.5f);
          break;
        case "enter-opacity":
          t.motion.enterOpacity = n(v, 0, 1);
          break;
        case "enter-offset-x":
          t.motion.enterOffsetX = n(v, -100, 100);
          break;
        case "enter-offset-y":
          t.motion.enterOffsetY = n(v, -100, 100);
          break;
        case "animate-layout":
          t.motion.layout = (Boolean) v;
          break;
        case "animate-value":
          t.motion.value = (Boolean) v;
          break;
        case "background":
        case "background-color":
          t.background = t.surface = t.raised = (Integer) v;
          break;
        case "surface":
          t.surface = (Integer) v;
          break;
        case "raised":
          t.raised = (Integer) v;
          break;
        case "color":
        case "text":
          t.text = (Integer) v;
          break;
        case "muted":
          t.muted = (Integer) v;
          break;
        case "accent":
          t.accent = (Integer) v;
          break;
        case "secondary-accent":
          t.secondaryAccent = (Integer) v;
          break;
        case "border-color":
          t.border = (Integer) v;
          break;
        case "radius":
        case "border-radius":
          t.radius = n(v, 0, 80);
          break;
        case "border-width":
          t.borderWidth = n(v, 0, 8);
          break;
        case "opacity":
        case "background-opacity":
          t.backgroundOpacity = n(v, 0, 1);
          break;
        case "surface-opacity":
          t.surfaceOpacity = n(v, 0, 1);
          break;
        case "raised-opacity":
          t.raisedOpacity = n(v, 0, 1);
          break;
        case "tint-opacity":
          t.tintOpacity = n(v, -1, 1);
          break;
        case "blur":
          if (v instanceof Boolean)
            t.effects.backgroundBlur = (Boolean) v;
          else {
            t.blurRadius = n(v, 0, 40);
            t.effects.backgroundBlur = t.blurRadius > 0;
          }
          break;
        case "blur-radius":
          t.blurRadius = n(v, 0, 40);
          break;
        case "blur-quality":
          t.blurQuality = Math.round(n(v, 1, 6));
          break;
        case "blur-mix":
          t.blurMix = n(v, 0, 1);
          break;
        case "glass-mix":
          t.glassMix = n(v, -1, 1);
          break;
        case "accent-gradient":
          t.accentGradient = n(v, 0, 1);
          break;
        case "font-tracking":
        case "letter-spacing":
          t.fontTracking = n(v, -1, 3);
          break;
        case "font-weight":
          t.fontWeight = (String) v;
          break;
        case "grain":
          t.effects.grain = (Boolean) v;
          break;
        case "grain-amount":
          t.effects.grainAmount = n(v, 0, .15f);
          break;
        case "grain-scale":
          t.effects.grainScale = n(v, .5f, 4);
          break;
        default:
          break;
      }
    }
  }
}
