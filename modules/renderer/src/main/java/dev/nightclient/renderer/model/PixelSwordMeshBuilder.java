package dev.nightclient.renderer.model;

import dev.nightclient.cosmetics.CosmeticModels;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PixelSwordMeshBuilder {
  public static final int GRID_SIZE = 24;
  private static final float INV_SQRT2 = 0.70710678f;

  private enum PatternStyle {
    CRYSTAL_FACET,
    FORGED_NETHERITE,
    POLISHED_METAL,
    OXIDIZED_COPPER,
    GLOW_VEIN,
    PRISMARINE_SCALE
  }

  private static final class FormSpec {
    final String id;
    final String[] rows;
    final float gripU;
    final float gripV;
    final float pixelSize;
    final float thickness;

    FormSpec(String id, float gripU, float gripV, float pixelSize, float thickness, String[] rows) {
      if (rows.length != GRID_SIZE) {
        throw new IllegalArgumentException("Form " + id + " must have " + GRID_SIZE + " rows");
      }
      for (int r = 0; r < GRID_SIZE; r++) {
        if (rows[r].length() != GRID_SIZE) {
          throw new IllegalArgumentException(
              "Form " + id + " row " + r + " length=" + rows[r].length());
        }
      }
      this.id = id;
      this.gripU = gripU;
      this.gripV = gripV;
      this.pixelSize = pixelSize;
      this.thickness = thickness;
      this.rows = rows;
    }

    char pixelAt(int u, int v) {
      if (u < 0 || u >= GRID_SIZE || v < 0 || v >= GRID_SIZE) return '.';
      return rows[GRID_SIZE - 1 - v].charAt(u);
    }
  }

  private static final class MaterialPalette {
    final String id;
    final PatternStyle pattern;
    final int bladeOutline;
    final int bladeDark;
    final int bladeMid;
    final int bladeLight;
    final int bladeHighlight;
    final int bladeAccent;
    final int guardDark;
    final int guardMid;
    final int guardLight;
    final int pommelGem;
    final int gripDark;
    final int gripMid;
    final int gripLight;

    final float bladeMetallic;
    final float bladeRoughness;
    final float bladeEmissive;
    final float accentMetallic;
    final float accentRoughness;
    final float accentEmissive;
    final int accentEmissiveRgb;
    final float guardMetallic;
    final float guardRoughness;

    MaterialPalette(
        String id,
        PatternStyle pattern,
        int bladeOutline,
        int bladeDark,
        int bladeMid,
        int bladeLight,
        int bladeHighlight,
        int bladeAccent,
        int guardDark,
        int guardMid,
        int guardLight,
        int pommelGem,
        int gripDark,
        int gripMid,
        int gripLight,
        float bladeMetallic,
        float bladeRoughness,
        float bladeEmissive,
        float accentMetallic,
        float accentRoughness,
        float accentEmissive,
        int accentEmissiveRgb,
        float guardMetallic,
        float guardRoughness) {
      this.id = id;
      this.pattern = pattern;
      this.bladeOutline = bladeOutline;
      this.bladeDark = bladeDark;
      this.bladeMid = bladeMid;
      this.bladeLight = bladeLight;
      this.bladeHighlight = bladeHighlight;
      this.bladeAccent = bladeAccent;
      this.guardDark = guardDark;
      this.guardMid = guardMid;
      this.guardLight = guardLight;
      this.pommelGem = pommelGem;
      this.gripDark = gripDark;
      this.gripMid = gripMid;
      this.gripLight = gripLight;
      this.bladeMetallic = bladeMetallic;
      this.bladeRoughness = bladeRoughness;
      this.bladeEmissive = bladeEmissive;
      this.accentMetallic = accentMetallic;
      this.accentRoughness = accentRoughness;
      this.accentEmissive = accentEmissive;
      this.accentEmissiveRgb = accentEmissiveRgb;
      this.guardMetallic = guardMetallic;
      this.guardRoughness = guardRoughness;
    }
  }

  private static final Map<String, FormSpec> FORMS;
  private static final Map<String, MaterialPalette> MATERIALS;

  static {
    Map<String, FormSpec> f = new LinkedHashMap<String, FormSpec>();

    f.put("classic", new FormSpec("classic", 4.6f, 4.6f, 0.0365f, 0.044f, new String[] {
      "........................",
      "....................oooo",
      "...................ohlho",
      "..................ohlhlo",
      ".................ohlalmo",
      "................ohlalmdo",
      "...............ohlalmdo.",
      "..............ohlalmdo..",
      ".............ohlalmdo...",
      "............ohlalmdo....",
      "...........ohlalmdo.....",
      "..........ohlalmdo......",
      "....GG...ohlalmdo.......",
      "...GtgG.ohlalmdo........",
      "...GgtGohlalmdo.........",
      "....Ggtgalmdo...........",
      ".....Ggtgldo............",
      "....WwsGgtgG............",
      "...WwsW.GgtgG...........",
      "..WwsW...GgtgG..........",
      ".GGsW.....GgtG..........",
      "GpgG.......GG...........",
      "GgpG....................",
      ".GG.....................",
    }));

    f.put("shortsword", new FormSpec("shortsword", 5.2f, 5.2f, 0.0355f, 0.044f, new String[] {
      "........................",
      "........................",
      "........................",
      "........................",
      "........................",
      "...............ooooo....",
      "..............ohhlho....",
      ".............ohhlalmo...",
      "............ohhlalmdo...",
      "...........ohhlalmdo....",
      "....GG....ohhlalmdo.....",
      "...GtgG..ohhlalmdo......",
      "...GgtG.ohhlalmdo.......",
      "....GgtGohhalmdo........",
      ".....Ggtgalmdo..........",
      "......Ggtgldo...........",
      ".....WwsGgtgG...........",
      "....WwsW.GgtgG..........",
      "...WwsW...GgtgG.........",
      "..GGsW.....GgtgG........",
      ".GpgG.......GgtG........",
      ".GgpG........GG.........",
      "..GG....................",
      "........................",
    }));

    f.put("katana", new FormSpec("katana", 4.6f, 4.6f, 0.0372f, 0.036f, new String[] {
      ".............ooo........",
      "............ohho........",
      "...........ohlmo........",
      "..........ohlmo.........",
      "..........ohmdo.........",
      ".........ohamdo.........",
      ".........ohmdo..........",
      "........ohmdo...........",
      "........ohamdo..........",
      "........ohmdo...........",
      ".......ohmdo............",
      ".......ohmdo............",
      ".......ohmdo............",
      "......ohmdo.............",
      "......ohlmo.............",
      ".....GGhmdo.............",
      ".....GtgGdo.............",
      "....WwsgtgG.............",
      "...WwsW.GG..............",
      "..WwsW..................",
      ".WwsW...................",
      "GpgW....................",
      "GgpG....................",
      ".GG.....................",
    }));

    f.put("claymore", new FormSpec("claymore", 4.2f, 4.2f, 0.0380f, 0.048f, new String[] {
      ".....................ooo",
      "....................ohho",
      "...................ohlho",
      "..................ohhlho",
      ".................ohhamlo",
      "................ohhlamdo",
      "...............ohhlamddo",
      ".....GG.......ohhlamddo.",
      "....GtgG.....ohhlamddo..",
      "....GgtG....ohhlamddo...",
      ".....GgtG..ohhlamddo....",
      "......GgtGohhlamddo.....",
      ".......Ggtgpamddo.......",
      "........Ggtamddo........",
      "........Ggtgldo.........",
      ".......WwsGgtgG.........",
      "......WwsW.GgtgG........",
      ".....WwsW...GgtgG.......",
      "....WwsW.....GgtgG......",
      "...WwsW.......GgtG......",
      ".GGwsW.........GtgG.....",
      "GppgG...........GG......",
      "GgpgG...................",
      ".GGG....................",
    }));

    f.put("scimitar", new FormSpec("scimitar", 4.8f, 4.8f, 0.0368f, 0.042f, new String[] {
      "..............ooooo.....",
      "............oohhhhlo....",
      "...........ohhhllmmmo...",
      "..........ohhlalmmddo...",
      "..........ohhlalmddo....",
      ".........ohhlalmddo.....",
      ".........ohlalmddo......",
      ".........ohlalmdo.......",
      ".........ohlmdo.........",
      ".........ohlmdo.........",
      "........ohlmdo..........",
      "........ohlmdo..........",
      "....GG..ohlmdo..........",
      "...GtgGohlmdo...........",
      "....Ggtgaldo............",
      ".....Ggtgdo.............",
      "....WwsGgtgG............",
      "...WwsW..GgtG...........",
      "..WwsW....GtgG..........",
      "..WsW......GG...........",
      ".GpgG...................",
      "GgpG....................",
      "GggG....................",
      ".GG.....................",
    }));

    f.put("rapier", new FormSpec("rapier", 4.6f, 4.6f, 0.0372f, 0.036f, new String[] {
      "......................oo",
      ".....................oho",
      "....................ohlo",
      "...................ohmo.",
      "..................ohmo..",
      ".................ohmo...",
      "................ohmo....",
      "...............ohmo.....",
      "..............ohmo......",
      ".............ohmo.......",
      "............ohmo........",
      "...........ohmo.........",
      ".....GG...ohmo..........",
      "....GtgG.ohmo...........",
      "...GgtGGohmo............",
      "..GgtG.Ggtg.............",
      ".GgtG.WwsGtgG...........",
      ".GtG.WwsW.GgtG..........",
      ".GtGWwsW...GgtG.........",
      "..GGwsW.....GtgG........",
      "..GpgG.......GG.........",
      ".GgpG...................",
      "..GG....................",
      "........................",
    }));

    f.put("cleaver", new FormSpec("cleaver", 4.6f, 4.6f, 0.0370f, 0.048f, new String[] {
      "................oooo....",
      "...............ohhhlo...",
      "..............ohhhllmo..",
      ".............ohhllooldo.",
      "............ohhhlo..odmo",
      "...........ohhhlmo..odmo",
      "..........ohhhlalmooodo.",
      ".........ohhhlalmmdddo..",
      "........ohhhlalmmdddo...",
      ".......ohhhlalmmdddo....",
      "......ohhhlalmmdddo.....",
      ".....ohhhlalmmdddo......",
      "......oohlalmmddo.......",
      ".......ooolmmddo........",
      "......GGgtgmdoo.........",
      ".....GtgtgtgG...........",
      "....WwsGgtgG............",
      "...WwsW.GgtgG...........",
      "..WwsW...GGG............",
      ".GGsW...................",
      "GppgG...................",
      "GgpgG...................",
      ".GGG....................",
      "........................",
    }));

    f.put("dagger", new FormSpec("dagger", 5.8f, 5.8f, 0.0345f, 0.038f, new String[] {
      "........................",
      "........................",
      "........................",
      "........................",
      "........................",
      ".................oo.....",
      "................oho.....",
      "...............ohlo.....",
      "..............ohlmo.....",
      ".............ohalmdo....",
      "............ohalmdo.....",
      "...........ohalmdo......",
      "..........ohalmdo.......",
      ".........ohlmdo.........",
      "......GG.ohmdo..........",
      "......GtgGmdo...........",
      ".......GgtgG............",
      "......WwsGgtG...........",
      ".....WwsW.GgtG..........",
      "....WwsW...GG...........",
      "...GtgW.................",
      "..Gg.gG.................",
      "..GgpGG.................",
      "...GGG..................",
    }));

    f.put("flamberge", new FormSpec("flamberge", 4.5f, 4.5f, 0.0372f, 0.044f, new String[] {
      ".....................ooo",
      "....................ohho",
      "..................oohlho",
      ".................ohhlmo.",
      "................ohlmdoo.",
      "................ohamdo..",
      "..............oohalmdo..",
      ".............ohhlmdoo...",
      "............ohlmdoo.....",
      "............ohamdo......",
      "..........oohalmdo......",
      ".........ohhlmdoo.......",
      "....GG..ohlmdoo.........",
      "...GtgG.ohamdo..........",
      "...GgtGohalmdo..........",
      "....Ggtgalmdo...........",
      ".....Ggtgldo............",
      "....WwsGgtgG............",
      "...WwsW.GgtgG...........",
      "..WwsW...GgtgG..........",
      ".GGsW.....GgtG..........",
      "GpgG.......GG...........",
      "GgpG....................",
      ".GG.....................",
    }));

    f.put("splitblade", new FormSpec("splitblade", 4.5f, 4.5f, 0.0370f, 0.044f, new String[] {
      "..................ooo...",
      ".................ohho...",
      "................ohlmo...",
      "...............ohlmo..oo",
      "..............ohlmo..oho",
      ".............ohlmo..omdo",
      "............ohlmo..omdo.",
      "...........ohlmo..omdo..",
      "..........ohlmo..omdo...",
      ".........ohlmo..omdo....",
      "........ohlmo..omdo.....",
      ".......ohlmoooomdo......",
      "...GG..ohlaaammdo.......",
      "..GtgG.ohlaammdo........",
      "..GgtG.ohlammdo.........",
      "...GgtGohammdo..........",
      "....Ggtgpmdo............",
      "...WwsGgtgG.............",
      "..WwsW.GgtgG............",
      ".WwsW...GgtgG...........",
      "GpgW.....GgtG...........",
      "GgpG......GG............",
      ".GG.....................",
      "........................",
    }));

    FORMS = Collections.unmodifiableMap(f);

    Map<String, MaterialPalette> m = new LinkedHashMap<String, MaterialPalette>();

    m.put("diamond", new MaterialPalette(
        "diamond", PatternStyle.CRYSTAL_FACET,
        0x082A30, 0x126F78, 0x24C4B6, 0x52EFE0, 0xCCFFF8, 0x84FFF1,
        0x0B383E, 0x1A8482, 0x4BEAD9, 0x6FFAF0,
        0x36210E, 0x6B4721, 0x926537,
        0.52f, 0.18f, 0.0f,
        0.60f, 0.14f, 0.06f, 0x70FFF2,
        0.54f, 0.24f));

    m.put("netherite", new MaterialPalette(
        "netherite", PatternStyle.FORGED_NETHERITE,
        0x100D11, 0x282126, 0x423840, 0x62555E, 0x8F7F8A, 0xDFAF4B,
        0x1A151A, 0x382F36, 0xCF9E3E, 0xF5CB5C,
        0x221116, 0x45232D, 0x663844,
        0.60f, 0.30f, 0.0f,
        0.82f, 0.18f, 0.05f, 0xF2C252,
        0.62f, 0.28f));

    m.put("emerald", new MaterialPalette(
        "emerald", PatternStyle.CRYSTAL_FACET,
        0x052810, 0x0B6B2B, 0x17BF4C, 0x4BE878, 0xC6FFD4, 0x78FFA0,
        0x3C2A07, 0xA67F1C, 0xE8C446, 0x45F278,
        0x26180B, 0x4D3219, 0x704B27,
        0.48f, 0.16f, 0.0f,
        0.56f, 0.14f, 0.06f, 0x54FF88,
        0.72f, 0.22f));

    m.put("gold", new MaterialPalette(
        "gold", PatternStyle.POLISHED_METAL,
        0x472804, 0xB06E0B, 0xEBC022, 0xFCE552, 0xFFFAD6, 0xFFEE7E,
        0x593106, 0xC48014, 0xF7D33E, 0xE63042,
        0x36200D, 0x69421E, 0x8F5E30,
        0.76f, 0.18f, 0.0f,
        0.80f, 0.15f, 0.04f, 0xFFF080,
        0.76f, 0.20f));

    m.put("iron", new MaterialPalette(
        "iron", PatternStyle.POLISHED_METAL,
        0x20242A, 0x6E7682, 0xB6BFC9, 0xDCE3EC, 0xFFFFFF, 0xCBD5E1,
        0x2B3038, 0x636C7A, 0xA3AEBE, 0xDCE4EE,
        0x38230F, 0x6D4822, 0x946738,
        0.72f, 0.22f, 0.0f,
        0.76f, 0.18f, 0.0f, 0xFFFFFF,
        0.66f, 0.26f));

    m.put("amethyst", new MaterialPalette(
        "amethyst", PatternStyle.CRYSTAL_FACET,
        0x210C3A, 0x592A96, 0x9452E3, 0xC28CFF, 0xF3E2FF, 0xD8B8FF,
        0x30164C, 0x713DAD, 0xB07AEF, 0xE8C8FF,
        0x1D1A24, 0x3D364A, 0x5B526E,
        0.48f, 0.16f, 0.05f,
        0.54f, 0.14f, 0.22f, 0xD4A0FF,
        0.52f, 0.26f));

    m.put("copper", new MaterialPalette(
        "copper", PatternStyle.OXIDIZED_COPPER,
        0x38170A, 0x964222, 0xD66B3B, 0xF29460, 0xFFD2AE, 0x45BE9E,
        0x15423A, 0x348775, 0x64CEB2, 0x5CE0BD,
        0x301B10, 0x5C351F, 0x7E4C30,
        0.68f, 0.26f, 0.0f,
        0.38f, 0.48f, 0.0f, 0x4CC2A3,
        0.50f, 0.36f));

    m.put("redstone", new MaterialPalette(
        "redstone", PatternStyle.GLOW_VEIN,
        0x2E0406, 0x880C13, 0xD81B22, 0xFF4747, 0xFFB5B5, 0xFF5C5C,
        0x1A171E, 0x383240, 0x5C5568, 0xFF3038,
        0x1C1619, 0x382C32, 0x54434C,
        0.48f, 0.22f, 0.08f,
        0.42f, 0.16f, 0.45f, 0xFF3338,
        0.54f, 0.32f));

    m.put("prismarine", new MaterialPalette(
        "prismarine", PatternStyle.PRISMARINE_SCALE,
        0x0E2928, 0x246864, 0x409E93, 0x72CFC0, 0xCCFFF4, 0x9EFFEE,
        0x163839, 0x326E6C, 0xDEB94E, 0xB8FFF2,
        0x113134, 0x225B5E, 0x358285,
        0.50f, 0.22f, 0.03f,
        0.44f, 0.18f, 0.26f, 0xA0FFF0,
        0.62f, 0.24f));

    m.put("sculk", new MaterialPalette(
        "sculk", PatternStyle.GLOW_VEIN,
        0x050B10, 0x0C1A24, 0x142C3B, 0x1E4658, 0x4EEAFF, 0x18E4F2,
        0x09141C, 0x142B38, 0x22D6E6, 0x38F6FF,
        0x4A463B, 0xA8A18C, 0xDCD6C2,
        0.54f, 0.28f, 0.02f,
        0.35f, 0.15f, 0.65f, 0x18F0FF,
        0.50f, 0.26f));

    MATERIALS = Collections.unmodifiableMap(m);
  }

  private PixelSwordMeshBuilder() {}

  public static CosmeticModelMesh build(String formId, String materialId) {
    FormSpec form = FORMS.get(formId);
    if (form == null) form = FORMS.get(CosmeticModels.DEFAULT_WEAPON);
    MaterialPalette mat = MATERIALS.get(materialId);
    if (mat == null) mat = MATERIALS.get(CosmeticModels.DEFAULT_MATERIAL);
    return buildInternal(form, mat);
  }

  public static boolean hasForm(String formId) {
    return formId != null && FORMS.containsKey(formId);
  }

  public static boolean hasMaterial(String materialId) {
    return materialId != null && MATERIALS.containsKey(materialId);
  }

  public static int[] rasterizeSprite24(String formId, String materialId) {
    FormSpec form = FORMS.get(formId);
    if (form == null) form = FORMS.get(CosmeticModels.DEFAULT_WEAPON);
    MaterialPalette mat = MATERIALS.get(materialId);
    if (mat == null) mat = MATERIALS.get(CosmeticModels.DEFAULT_MATERIAL);
    int[] out = new int[GRID_SIZE * GRID_SIZE];
    PixelProps props = new PixelProps();
    for (int row = 0; row < GRID_SIZE; row++) {
      int v = GRID_SIZE - 1 - row;
      for (int u = 0; u < GRID_SIZE; u++) {
        char code = form.pixelAt(u, v);
        if (code == '.') continue;
        resolvePixel(code, u, v, mat, props);
        out[row * GRID_SIZE + u] = 0xFF000000 | (props.rgb & 0xFFFFFF);
      }
    }
    return out;
  }

  private static final class PixelProps {
    int rgb;
    float metallic;
    float roughness;
    float emissive;
    int emissiveRgb;
  }

  private static void resolvePixel(
      char code, int u, int v, MaterialPalette mat, PixelProps out) {
    int baseRgb;
    float metallic;
    float roughness;
    float emissive = 0f;
    int emissiveRgb = 0;

    switch (code) {
      case 'o':
        baseRgb = mat.bladeOutline;
        metallic = mat.bladeMetallic * 0.65f;
        roughness = Math.min(1f, mat.bladeRoughness + 0.16f);
        break;
      case 'd':
        baseRgb = mat.bladeDark;
        metallic = mat.bladeMetallic * 0.90f;
        roughness = Math.min(1f, mat.bladeRoughness + 0.06f);
        emissive = mat.bladeEmissive * 0.5f;
        emissiveRgb = mat.accentEmissiveRgb;
        break;
      case 'm':
        baseRgb = mat.bladeMid;
        metallic = mat.bladeMetallic;
        roughness = mat.bladeRoughness;
        emissive = mat.bladeEmissive;
        emissiveRgb = mat.accentEmissiveRgb;
        break;
      case 'l':
        baseRgb = mat.bladeLight;
        metallic = Math.min(1f, mat.bladeMetallic + 0.05f);
        roughness = Math.max(0.08f, mat.bladeRoughness - 0.04f);
        emissive = mat.bladeEmissive * 1.15f;
        emissiveRgb = mat.accentEmissiveRgb;
        break;
      case 'h':
        baseRgb = mat.bladeHighlight;
        metallic = Math.min(1f, mat.bladeMetallic + 0.08f);
        roughness = Math.max(0.06f, mat.bladeRoughness - 0.06f);
        emissive = mat.bladeEmissive * 1.35f;
        emissiveRgb = mat.accentEmissiveRgb;
        break;
      case 'a':
        baseRgb = mat.bladeAccent;
        metallic = mat.accentMetallic;
        roughness = mat.accentRoughness;
        emissive = mat.accentEmissive;
        emissiveRgb = mat.accentEmissiveRgb;
        break;
      case 'G':
        baseRgb = mat.guardDark;
        metallic = mat.guardMetallic * 0.70f;
        roughness = Math.min(1f, mat.guardRoughness + 0.14f);
        break;
      case 'g':
        baseRgb = mat.guardMid;
        metallic = mat.guardMetallic;
        roughness = mat.guardRoughness;
        break;
      case 't':
        baseRgb = mat.guardLight;
        metallic = Math.min(1f, mat.guardMetallic + 0.10f);
        roughness = Math.max(0.10f, mat.guardRoughness - 0.06f);
        break;
      case 'p':
        baseRgb = mat.pommelGem;
        metallic = mat.accentMetallic;
        roughness = mat.accentRoughness;
        emissive = mat.accentEmissive * 0.90f;
        emissiveRgb = mat.accentEmissiveRgb != 0 ? mat.accentEmissiveRgb : mat.pommelGem;
        break;
      case 'W':
        baseRgb = mat.gripDark;
        metallic = 0.06f;
        roughness = 0.88f;
        break;
      case 'w':
        baseRgb = mat.gripMid;
        metallic = 0.06f;
        roughness = 0.84f;
        break;
      case 's':
        baseRgb = mat.gripLight;
        metallic = 0.08f;
        roughness = 0.78f;
        break;
      default:
        baseRgb = mat.bladeMid;
        metallic = mat.bladeMetallic;
        roughness = mat.bladeRoughness;
        break;
    }

    boolean bladeBody = (code == 'd' || code == 'm' || code == 'l' || code == 'h');
    int hash = mixHash(u, v);
    if (bladeBody) {
      int diag = u + v;
      int cross = u - v;
      switch (mat.pattern) {
        case CRYSTAL_FACET: {
          int band = Math.floorMod(u * 2 - v, 6);
          if (band == 0) {
            baseRgb = shiftBrightness(baseRgb, 1.10f);
          } else if (band == 3) {
            baseRgb = shiftBrightness(baseRgb, 0.89f);
          }
          if ((hash & 15) == 0 && (code == 'l' || code == 'm')) {
            baseRgb = lerpRgb(baseRgb, mat.bladeHighlight, 0.55f);
          } else if ((hash & 15) == 7 && code == 'm') {
            baseRgb = lerpRgb(baseRgb, mat.bladeDark, 0.25f);
          }
          break;
        }
        case FORGED_NETHERITE: {
          int n = (hash & 7) - 3;
          float factor = 1.0f + n * 0.036f;
          baseRgb = shiftBrightness(baseRgb, factor);
          if ((diag % 9 == 0) && cross == 0 && code == 'l') {
            baseRgb = lerpRgb(baseRgb, mat.bladeAccent, 0.42f);
          }
          break;
        }
        case POLISHED_METAL: {
          int sheen = Math.floorMod(diag + (cross >> 1), 8);
          if (sheen == 1 || sheen == 2) {
            baseRgb = shiftBrightness(baseRgb, 1.08f);
          } else if (sheen == 5) {
            baseRgb = shiftBrightness(baseRgb, 0.91f);
          }
          if ((hash & 7) == 0) {
            baseRgb = shiftBrightness(baseRgb, 0.95f);
          }
          break;
        }
        case OXIDIZED_COPPER: {
          int n = (hash & 7) - 3;
          baseRgb = shiftBrightness(baseRgb, 1.0f + n * 0.030f);
          boolean nearGuardOrEdge = (diag <= 22 && (hash & 3) == 0)
              || (code == 'd' && (hash & 7) == 2)
              || ((hash & 15) == 5 && code == 'm');
          if (nearGuardOrEdge) {
            baseRgb = lerpRgb(baseRgb, mat.bladeAccent, 0.78f);
            metallic = mat.accentMetallic;
            roughness = mat.accentRoughness;
          }
          break;
        }
        case GLOW_VEIN: {
          boolean vein = "sculk".equals(mat.id)
              ? (((u * 3 + v * 5) % 11 == 0) || ((hash & 15) == 3 && code != 'd'))
              : (((hash & 15) == 1 || (hash & 15) == 9) && code != 'd');
          if (vein) {
            baseRgb = lerpRgb(baseRgb, mat.bladeAccent, "sculk".equals(mat.id) ? 0.88f : 0.65f);
            emissive = Math.max(emissive, mat.accentEmissive * 0.85f);
            emissiveRgb = mat.accentEmissiveRgb;
          } else {
            int n = (hash & 7) - 3;
            baseRgb = shiftBrightness(baseRgb, 1.0f + n * 0.03f);
          }
          break;
        }
        case PRISMARINE_SCALE: {
          int tile = Math.floorMod(u + v * 2, 5);
          if (tile == 0) {
            baseRgb = tintShift(baseRgb, -12, +14, +16);
          } else if (tile == 2) {
            baseRgb = tintShift(baseRgb, +6, +12, -10);
          } else if (tile == 4) {
            baseRgb = shiftBrightness(baseRgb, 0.90f);
          }
          if ((hash & 15) == 4 && code == 'l') {
            baseRgb = lerpRgb(baseRgb, mat.bladeAccent, 0.55f);
            emissive = Math.max(emissive, mat.accentEmissive * 0.70f);
            emissiveRgb = mat.accentEmissiveRgb;
          }
          break;
        }
        default:
          break;
      }
    } else if (code == 'w' || code == 's') {
      if (((u + v) & 1) == 0) baseRgb = shiftBrightness(baseRgb, 0.94f);
    }

    out.rgb = baseRgb;
    out.metallic = clamp01(metallic);
    out.roughness = clamp01(roughness);
    out.emissive = Math.max(0f, Math.min(4f, emissive));
    out.emissiveRgb = emissiveRgb;
  }

  private static CosmeticModelMesh buildInternal(FormSpec form, MaterialPalette mat) {
    int quadCount = 0;
    for (int v = 0; v < GRID_SIZE; v++) {
      for (int u = 0; u < GRID_SIZE; u++) {
        if (form.pixelAt(u, v) == '.') continue;
        quadCount += 2;
        if (form.pixelAt(u - 1, v) == '.') quadCount++;
        if (form.pixelAt(u + 1, v) == '.') quadCount++;
        if (form.pixelAt(u, v - 1) == '.') quadCount++;
        if (form.pixelAt(u, v + 1) == '.') quadCount++;
      }
    }

    int totalVerts = quadCount * 4;
    int totalTris = quadCount * 2;
    int totalIndices = totalTris * 3;

    byte[] vertexBytes = new byte[totalVerts * CosmeticModelMesh.VERTEX_STRIDE];
    ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.nativeOrder());
    int[] indices = new int[totalIndices];

    float[] pPos = new float[totalTris * 9];
    float[] pNrm = new float[totalTris * 9];
    int[] pCol = new int[totalTris];
    byte[] pBone = new byte[totalTris];
    int[] pMat = new int[totalTris];

    float halfT = form.thickness * 0.5f;
    float s = form.pixelSize * INV_SQRT2;

    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

    int[] cursors = new int[3];
    PixelProps props = new PixelProps();

    for (int v = 0; v < GRID_SIZE; v++) {
      for (int u = 0; u < GRID_SIZE; u++) {
        char code = form.pixelAt(u, v);
        if (code == '.') continue;
        resolvePixel(code, u, v, mat, props);

        float du0 = u - form.gripU;
        float dv0 = v - form.gripV;
        float du1 = du0 + 1f;
        float dv1 = dv0 + 1f;

        float y00 = (du0 + dv0) * s;
        float z00 = (du0 - dv0) * s;
        float y10 = (du1 + dv0) * s;
        float z10 = (du1 - dv0) * s;
        float y11 = (du1 + dv1) * s;
        float z11 = (du1 - dv1) * s;
        float y01 = (du0 + dv1) * s;
        float z01 = (du0 - dv1) * s;

        if (-halfT < minX) minX = -halfT;
        if (halfT > maxX) maxX = halfT;
        float yMinC = Math.min(Math.min(y00, y10), Math.min(y11, y01));
        float yMaxC = Math.max(Math.max(y00, y10), Math.max(y11, y01));
        float zMinC = Math.min(Math.min(z00, z10), Math.min(z11, z01));
        float zMaxC = Math.max(Math.max(z00, z10), Math.max(z11, z01));
        if (yMinC < minY) minY = yMinC;
        if (yMaxC > maxY) maxY = yMaxC;
        if (zMinC < minZ) minZ = zMinC;
        if (zMaxC > maxZ) maxZ = zMaxC;

        emitQuad(vb, indices, pPos, pNrm, pCol, pBone, pMat, cursors,
            halfT, y00, z00, halfT, y01, z01, halfT, y11, z11, halfT, y10, z10,
            1f, 0f, 0f, props, 1.00f);

        emitQuad(vb, indices, pPos, pNrm, pCol, pBone, pMat, cursors,
            -halfT, y00, z00, -halfT, y10, z10, -halfT, y11, z11, -halfT, y01, z01,
            -1f, 0f, 0f, props, 0.98f);

        if (form.pixelAt(u - 1, v) == '.') {
          emitQuad(vb, indices, pPos, pNrm, pCol, pBone, pMat, cursors,
              -halfT, y00, z00, -halfT, y01, z01, halfT, y01, z01, halfT, y00, z00,
              0f, -INV_SQRT2, -INV_SQRT2, props, 0.80f);
        }
        if (form.pixelAt(u + 1, v) == '.') {
          emitQuad(vb, indices, pPos, pNrm, pCol, pBone, pMat, cursors,
              -halfT, y10, z10, halfT, y10, z10, halfT, y11, z11, -halfT, y11, z11,
              0f, INV_SQRT2, INV_SQRT2, props, 0.88f);
        }
        if (form.pixelAt(u, v - 1) == '.') {
          emitQuad(vb, indices, pPos, pNrm, pCol, pBone, pMat, cursors,
              -halfT, y00, z00, halfT, y00, z00, halfT, y10, z10, -halfT, y10, z10,
              0f, -INV_SQRT2, INV_SQRT2, props, 0.74f);
        }
        if (form.pixelAt(u, v + 1) == '.') {
          emitQuad(vb, indices, pPos, pNrm, pCol, pBone, pMat, cursors,
              -halfT, y01, z01, -halfT, y11, z11, halfT, y11, z11, halfT, y01, z01,
              0f, INV_SQRT2, -INV_SQRT2, props, 0.92f);
        }
      }
    }

    CosmeticModelMesh.SubMesh[] subMeshes = new CosmeticModelMesh.SubMesh[] {
        new CosmeticModelMesh.SubMesh(
            CosmeticModelMesh.BONE_WEAPON, 0, totalIndices, CosmeticModelMesh.BLEND_OPAQUE, false)
    };
    CosmeticModelMesh.PreviewMesh preview = new CosmeticModelMesh.PreviewMesh(
        totalTris, pPos, pNrm, pCol, pBone, pMat);

    return new CosmeticModelMesh(
        vertexBytes,
        indices,
        totalVerts,
        subMeshes,
        new int[] {0xFFFFFFFF},
        1,
        1,
        minX, minY, minZ,
        maxX, maxY, maxZ,
        preview);
  }

  private static void emitQuad(
      ByteBuffer vb,
      int[] indices,
      float[] pPos,
      float[] pNrm,
      int[] pCol,
      byte[] pBone,
      int[] pMat,
      int[] cursors,
      float x0, float y0, float z0,
      float x1, float y1, float z1,
      float x2, float y2, float z2,
      float x3, float y3, float z3,
      float nx, float ny, float nz,
      PixelProps props,
      float faceShade) {
    float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
    float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;
    float cx = e1y * e2z - e1z * e2y;
    float cy = e1z * e2x - e1x * e2z;
    float cz = e1x * e2y - e1y * e2x;
    if (cx * nx + cy * ny + cz * nz < 0f) {
      float tx = x1, ty = y1, tz = z1;
      x1 = x3; y1 = y3; z1 = z3;
      x3 = tx; y3 = ty; z3 = tz;
    }

    int shadedRgb = shiftBrightness(props.rgb, faceShade);
    int r = (shadedRgb >>> 16) & 255;
    int g = (shadedRgb >>> 8) & 255;
    int b = shadedRgb & 255;
    int argb = 0xFF000000 | (r << 16) | (g << 8) | b;

    float er = ((props.emissiveRgb >>> 16) & 255) / 255f;
    float eg = ((props.emissiveRgb >>> 8) & 255) / 255f;
    float eb = (props.emissiveRgb & 255) / 255f;

    short uv1x = CosmeticModelMesh.packByteAnd7Bit(props.metallic, props.roughness);
    short uv1y = CosmeticModelMesh.packByteAnd7Bit(Math.min(1f, props.emissive * 0.25f), 0f);
    short uv2x = CosmeticModelMesh.packByteAnd7Bit(er, eg);
    short uv2y = CosmeticModelMesh.packByteAndFlags7(eb, 0);

    byte bnx = CosmeticModelMesh.packSignedNormal(nx);
    byte bny = CosmeticModelMesh.packSignedNormal(ny);
    byte bnz = CosmeticModelMesh.packSignedNormal(nz);

    int baseV = cursors[0];
    putVertex(vb, x0, y0, z0, r, g, b, 0f, 0f, uv1x, uv1y, uv2x, uv2y, bnx, bny, bnz);
    putVertex(vb, x1, y1, z1, r, g, b, 1f, 0f, uv1x, uv1y, uv2x, uv2y, bnx, bny, bnz);
    putVertex(vb, x2, y2, z2, r, g, b, 1f, 1f, uv1x, uv1y, uv2x, uv2y, bnx, bny, bnz);
    putVertex(vb, x3, y3, z3, r, g, b, 0f, 1f, uv1x, uv1y, uv2x, uv2y, bnx, bny, bnz);
    cursors[0] = baseV + 4;

    int ic = cursors[1];
    indices[ic] = baseV;
    indices[ic + 1] = baseV + 1;
    indices[ic + 2] = baseV + 2;
    indices[ic + 3] = baseV;
    indices[ic + 4] = baseV + 2;
    indices[ic + 5] = baseV + 3;
    cursors[1] = ic + 6;

    float previewMet = props.metallic * 0.24f;
    float previewEm = Math.min(1f, props.emissive * 0.42f);
    int matBits = CosmeticModelMesh.clampByte(Math.round(previewMet * 255f))
        | (CosmeticModelMesh.clampByte(Math.round(previewEm * 255f)) << 8)
        | (CosmeticModelMesh.BLEND_OPAQUE << 24);

    int t0 = cursors[2];
    int o0 = t0 * 9;
    pPos[o0] = x0; pPos[o0 + 1] = y0; pPos[o0 + 2] = z0;
    pPos[o0 + 3] = x1; pPos[o0 + 4] = y1; pPos[o0 + 5] = z1;
    pPos[o0 + 6] = x2; pPos[o0 + 7] = y2; pPos[o0 + 8] = z2;
    pNrm[o0] = nx; pNrm[o0 + 1] = ny; pNrm[o0 + 2] = nz;
    pNrm[o0 + 3] = nx; pNrm[o0 + 4] = ny; pNrm[o0 + 5] = nz;
    pNrm[o0 + 6] = nx; pNrm[o0 + 7] = ny; pNrm[o0 + 8] = nz;
    pCol[t0] = argb;
    pBone[t0] = CosmeticModelMesh.BONE_WEAPON;
    pMat[t0] = matBits;

    int t1 = t0 + 1;
    int o1 = t1 * 9;
    pPos[o1] = x0; pPos[o1 + 1] = y0; pPos[o1 + 2] = z0;
    pPos[o1 + 3] = x2; pPos[o1 + 4] = y2; pPos[o1 + 5] = z2;
    pPos[o1 + 6] = x3; pPos[o1 + 7] = y3; pPos[o1 + 8] = z3;
    pNrm[o1] = nx; pNrm[o1 + 1] = ny; pNrm[o1 + 2] = nz;
    pNrm[o1 + 3] = nx; pNrm[o1 + 4] = ny; pNrm[o1 + 5] = nz;
    pNrm[o1 + 6] = nx; pNrm[o1 + 7] = ny; pNrm[o1 + 8] = nz;
    pCol[t1] = argb;
    pBone[t1] = CosmeticModelMesh.BONE_WEAPON;
    pMat[t1] = matBits;

    cursors[2] = t0 + 2;
  }

  private static void putVertex(
      ByteBuffer vb,
      float x, float y, float z,
      int r, int g, int b,
      float u, float v,
      short uv1x, short uv1y, short uv2x, short uv2y,
      byte nx, byte ny, byte nz) {
    vb.putFloat(x).putFloat(y).putFloat(z);
    vb.put((byte) r).put((byte) g).put((byte) b).put((byte) 255);
    vb.putFloat(u).putFloat(v);
    vb.putShort(uv1x).putShort(uv1y);
    vb.putShort(uv2x).putShort(uv2y);
    vb.put(nx).put(ny).put(nz).put((byte) 0);
  }

  private static int mixHash(int u, int v) {
    int h = u * 374761393 + v * 668265263;
    h = (h ^ (h >>> 13)) * 1274126177;
    return h ^ (h >>> 16);
  }

  private static int shiftBrightness(int rgb, float factor) {
    int r = CosmeticModelMesh.clampByte(Math.round(((rgb >>> 16) & 255) * factor));
    int g = CosmeticModelMesh.clampByte(Math.round(((rgb >>> 8) & 255) * factor));
    int b = CosmeticModelMesh.clampByte(Math.round((rgb & 255) * factor));
    return (r << 16) | (g << 8) | b;
  }

  private static int tintShift(int rgb, int dr, int dg, int db) {
    int r = CosmeticModelMesh.clampByte(((rgb >>> 16) & 255) + dr);
    int g = CosmeticModelMesh.clampByte(((rgb >>> 8) & 255) + dg);
    int b = CosmeticModelMesh.clampByte((rgb & 255) + db);
    return (r << 16) | (g << 8) | b;
  }

  private static int lerpRgb(int a, int b, float t) {
    int ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
    int br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
    int r = CosmeticModelMesh.clampByte(Math.round(ar + (br - ar) * t));
    int g = CosmeticModelMesh.clampByte(Math.round(ag + (bg - ag) * t));
    int bl = CosmeticModelMesh.clampByte(Math.round(ab + (bb - ab) * t));
    return (r << 16) | (g << 8) | bl;
  }

  private static float clamp01(float v) {
    return v < 0f ? 0f : (v > 1f ? 1f : v);
  }
}
