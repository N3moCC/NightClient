package dev.nightclient.cosmetics;

import dev.nightclient.i18n.I18n;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OfficialCapes {
  public static final String TEXTURE_PREFIX = OfficialCapesCatalog.texturePrefix();

  public static final class Cape {
    public final String id;
    public final String category;
    public final int year;
    public final String nameKey;
    public final String nameEn;
    public final String nameRu;
    public final String primaryHex;
    public final String secondaryHex;
    public final String accentHex;
    public final String emblem;
    public final String textureHash;

    Cape(String id, String category, int year, String primaryHex, String secondaryHex,
        String accentHex, String emblem, String textureHash) {
      this.id = id;
      this.category = category;
      this.year = year;
      this.nameKey = "cosmetics.capes.c_" + id;
      this.nameEn = I18n.text("en", this.nameKey);
      this.nameRu = I18n.text("ru", this.nameKey);
      this.primaryHex = primaryHex;
      this.secondaryHex = secondaryHex;
      this.accentHex = accentHex;
      this.emblem = emblem;
      this.textureHash = textureHash;
    }

    public String name(String language) {
      return I18n.text(language, nameKey);
    }

    public String textureUrl() {
      return TEXTURE_PREFIX.isEmpty() || textureHash == null || textureHash.isEmpty()
          ? null
          : TEXTURE_PREFIX + textureHash;
    }

    public int primaryColor() {
      return parseColor(primaryHex, 0xff223042);
    }

    public int secondaryColor() {
      return parseColor(secondaryHex, 0xff162130);
    }

    public int accentColor() {
      return parseColor(accentHex, 0xffffffff);
    }
  }

  private static final List<Cape> ALL;
  private static final Map<String, Cape> BY_ID;

  static {
    List<Cape> list = OfficialCapesCatalog.createCatalog();
    ALL = Collections.unmodifiableList(list);
    Map<String, Cape> byId = new LinkedHashMap<String, Cape>();
    for (Cape cape : list) byId.put(cape.id, cape);
    BY_ID = Collections.unmodifiableMap(byId);
  }

  private OfficialCapes() {}

  static Cape entry(String id, String category, int year, String primaryHex, String secondaryHex,
      String accentHex, String emblem, String hash) {
    return new Cape(id, category, year, primaryHex, secondaryHex, accentHex, emblem, hash);
  }

  public static boolean isEnabled() {
    return OfficialCapesCatalog.enabled();
  }

  public static List<Cape> all() {
    return ALL;
  }

  public static Cape byId(String id) {
    return id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
  }

  public static List<Cape> filter(String category, String query, String language) {
    String cat = category == null ? "all" : category.trim().toLowerCase(Locale.ROOT);
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<Cape> out = new ArrayList<Cape>();
    for (Cape cape : ALL) {
      if (!"all".equals(cat) && !cat.isEmpty() && !cape.category.equals(cat)) continue;
      if (!needle.isEmpty()) {
        String loc = cape.name(language).toLowerCase(Locale.ROOT);
        String en = cape.nameEn.toLowerCase(Locale.ROOT);
        String ru = cape.nameRu.toLowerCase(Locale.ROOT);
        String idLower = cape.id.toLowerCase(Locale.ROOT);
        String yr = Integer.toString(cape.year);
        if (!loc.contains(needle) && !en.contains(needle) && !ru.contains(needle)
            && !idLower.contains(needle) && !yr.contains(needle))
          continue;
      }
      out.add(cape);
    }
    return out;
  }

  private static int parseColor(String hex, int fallback) {
    if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') return fallback;
    try {
      return 0xff000000 | Integer.parseInt(hex.substring(1), 16);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
