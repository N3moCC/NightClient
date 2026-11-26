package dev.nightclient.cosmetics;

import dev.nightclient.i18n.I18n;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CosmeticModels {
  public static final String KIND_WEAPON = "weapon";
  public static final String KIND_ARMOR = "armor";
  public static final String DEFAULT_WEAPON = "classic";
  public static final String DEFAULT_MATERIAL = "diamond";

  public static final class Entry {
    public final String id;
    public final String kind;
    public final String nameKey;
    public final String subtitleKey;
    public final String nameEn;
    public final String nameRu;
    public final String subtitleEn;
    public final String subtitleRu;
    public final int defaultAccentColor;
    public final int triangles;
    public final String resourcePath;

    Entry(String id, String kind, int defaultAccentColor, int triangles, String resourcePath) {
      this.id = id;
      this.kind = kind;
      this.nameKey = "cosmetics.models." + id + ".name";
      this.subtitleKey = "cosmetics.models." + id + ".subtitle";
      this.nameEn = I18n.text("en", this.nameKey);
      this.nameRu = I18n.text("ru", this.nameKey);
      this.subtitleEn = I18n.text("en", this.subtitleKey);
      this.subtitleRu = I18n.text("ru", this.subtitleKey);
      this.defaultAccentColor = defaultAccentColor;
      this.triangles = triangles;
      this.resourcePath = resourcePath;
    }

    public String name(String language) {
      return I18n.text(language, nameKey);
    }

    public String subtitle(String language) {
      return I18n.text(language, subtitleKey);
    }

    public boolean isArmor() {
      return KIND_ARMOR.equals(kind);
    }

    public String polyBadge() {
      if (triangles >= 1000) {
        return String.format(Locale.ROOT, "%.1fK", triangles / 1000.0);
      }
      return Integer.toString(triangles);
    }
  }

  public static final class Material {
    public final String id;
    public final String nameKey;
    public final String nameEn;
    public final String nameRu;
    public final String primaryHex;
    public final String secondaryHex;
    public final int primaryColor;

    Material(String id, String primaryHex, String secondaryHex, int primaryColor) {
      this.id = id;
      this.nameKey = "cosmetics.materials." + id + ".name";
      this.nameEn = I18n.text("en", this.nameKey);
      this.nameRu = I18n.text("ru", this.nameKey);
      this.primaryHex = primaryHex;
      this.secondaryHex = secondaryHex;
      this.primaryColor = primaryColor;
    }

    public String name(String language) {
      return I18n.text(language, nameKey);
    }
  }

  private static final List<Entry> WEAPONS;
  private static final List<Entry> ARMORS;
  private static final List<Material> MATERIALS;
  private static final Map<String, Entry> BY_ID;
  private static final Map<String, Material> MATERIALS_BY_ID;

  static {
    List<Entry> w = new ArrayList<Entry>();
    w.add(e("classic", KIND_WEAPON, 0xff33ebcb, 760));
    w.add(e("shortsword", KIND_WEAPON, 0xff33ebcb, 620));
    w.add(e("katana", KIND_WEAPON, 0xff33ebcb, 680));
    w.add(e("claymore", KIND_WEAPON, 0xff33ebcb, 940));
    w.add(e("scimitar", KIND_WEAPON, 0xff33ebcb, 820));
    w.add(e("rapier", KIND_WEAPON, 0xff33ebcb, 580));
    w.add(e("cleaver", KIND_WEAPON, 0xff33ebcb, 980));
    w.add(e("dagger", KIND_WEAPON, 0xff33ebcb, 520));
    w.add(e("flamberge", KIND_WEAPON, 0xff33ebcb, 860));
    w.add(e("splitblade", KIND_WEAPON, 0xff33ebcb, 880));

    List<Entry> a = new ArrayList<Entry>();

    List<Material> m = new ArrayList<Material>();
    m.add(mat("diamond", "#33EBCB", "#1A8A88", 0xff33ebcb));
    m.add(mat("netherite", "#52464E", "#D6A84E", 0xff52464e));
    m.add(mat("emerald", "#1FD655", "#E3BE42", 0xff1fd655));
    m.add(mat("gold", "#F6D038", "#B8730E", 0xfff6d038));
    m.add(mat("iron", "#CFD6E0", "#7A828E", 0xffcfd6e0));
    m.add(mat("amethyst", "#A968F2", "#6B35B0", 0xffa968f2));
    m.add(mat("copper", "#DF784A", "#4DB89B", 0xffdf784a));
    m.add(mat("redstone", "#EB2B32", "#48404F", 0xffeb2b32));
    m.add(mat("prismarine", "#52B3A6", "#D8B752", 0xff52b3a6));
    m.add(mat("sculk", "#152530", "#1CE0E8", 0xff1ce0e8));

    WEAPONS = Collections.unmodifiableList(w);
    ARMORS = Collections.unmodifiableList(a);
    MATERIALS = Collections.unmodifiableList(m);

    Map<String, Entry> map = new LinkedHashMap<String, Entry>();
    for (Entry entry : w) map.put(entry.id, entry);
    for (Entry entry : a) map.put(entry.id, entry);
    BY_ID = Collections.unmodifiableMap(map);

    Map<String, Material> matMap = new LinkedHashMap<String, Material>();
    for (Material mat : m) matMap.put(mat.id, mat);
    MATERIALS_BY_ID = Collections.unmodifiableMap(matMap);
  }

  private static Entry e(String id, String kind, int accent, int tris) {
    return new Entry(id, kind, accent, tris, "procedural:sword:" + id);
  }

  private static Material mat(String id, String primaryHex, String secondaryHex, int primaryColor) {
    return new Material(id, primaryHex, secondaryHex, primaryColor);
  }

  private CosmeticModels() {}

  public static List<Entry> weapons() {
    return WEAPONS;
  }

  public static List<Entry> armors() {
    return ARMORS;
  }

  public static List<Material> materials() {
    return MATERIALS;
  }

  public static Entry byId(String id) {
    return id == null ? null : BY_ID.get(id);
  }

  public static Entry weaponById(String id) {
    Entry entry = byId(id);
    return entry != null && KIND_WEAPON.equals(entry.kind) ? entry : null;
  }

  public static Entry armorById(String id) {
    Entry entry = byId(id);
    return entry != null && KIND_ARMOR.equals(entry.kind) ? entry : null;
  }

  public static Material materialById(String id) {
    return id == null ? null : MATERIALS_BY_ID.get(id);
  }

  public static Material resolveMaterial(String id) {
    Material found = materialById(id);
    return found != null ? found : MATERIALS_BY_ID.get(DEFAULT_MATERIAL);
  }
}
