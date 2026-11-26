package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Theme;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SkinThemeResolverTest {
  @Test
  void framesObservePaletteMaterialAndMotionChangesWhileReusingStableRecipes() throws Exception {
    SkinDocument document = NcParser.parse("menu { panel { accent = #abcdef; text \"A\" } }");
    SkinDocument.Node panel = document.menu().children.get(0);
    SkinDocument.ThemeResolver resolver = document.themeResolver();
    Theme base = Theme.defaults();
    resolver.beginFrame();
    Theme first = resolver.theme(panel, base, "");
    assertSame(first, resolver.theme(panel, base, ""));
    base.effects.time = 12.5;
    assertSame(first, resolver.theme(panel, base, ""));
    assertEquals(12.5, first.effects.time);
    base.text = 0xff102030;
    base.effects.grainAmount = .31f;
    base.motion.enterScale = .76f;
    resolver.beginFrame();
    Theme changed = resolver.theme(panel, base, "");
    assertNotSame(first, changed);
    assertEquals(base.text, changed.text);
    assertEquals(.31f, changed.effects.grainAmount);
    assertEquals(.76f, changed.motion.enterScale);
    resolver.beginFrame();
    assertSame(changed, resolver.theme(panel, base, ""));
  }
  @Test
  void boundRecipesStillChangeWithinAFrameAndFollowHoverStates() throws Exception {
    SkinDocument document =
        NcParser.parse("menu { panel { accent = \"{{color}}\"; hover { radius = 15px } } }");
    SkinDocument.Node panel = document.menu().children.get(0);
    SkinDocument.ThemeResolver resolver = document.themeResolver();
    Theme base = Theme.defaults();
    resolver.beginFrame();
    Theme first =
        resolver.resolvedTheme(panel, base, "", Collections.singletonMap("accent", "#123456"));
    Theme second =
        resolver.resolvedTheme(panel, base, "", Collections.singletonMap("accent", "#654321"));
    assertEquals(0xff123456, first.accent);
    assertEquals(0xff654321, second.accent);
    assertSame(second,
        resolver.resolvedTheme(panel, base, "", Collections.singletonMap("accent", "#654321")));
    Theme hovered =
        resolver.resolvedTheme(panel, base, "hover", Collections.singletonMap("accent", "#654321"));
    assertEquals(15, hovered.radius);
    assertEquals(0xff654321, hovered.accent);
  }
  @Test
  void indexedBindingsUseOnlyTheirPrefixAndDoNotRetainScratchValues() throws Exception {
    SkinDocument document =
        NcParser.parse("menu { panel { accent = \"{{color}}\"; radius = \"{{rounding}}\" } }");
    SkinDocument.Node panel = document.menu().children.get(0);
    SkinDocument.ThemeBindingPlan plan = document.themeBindingPlan(panel, "");
    assertSame(plan, document.themeBindingPlan(panel, ""));
    assertEquals(2, plan.size());
    SkinDocument.ThemeResolver resolver = document.themeResolver();
    Theme base = Theme.defaults();
    resolver.beginFrame();
    String[] scratch = new String[plan.size() + 3];
    for (int i = 0; i < plan.size(); i++)
      scratch[i] = "accent".equals(plan.key(i)) ? "#123456" : "8";
    Theme first = resolver.resolvedTheme(plan, base, scratch);
    assertEquals(0xff123456, first.accent);
    assertEquals(8, first.radius);
    java.util.Map<String, String> equivalent = new java.util.LinkedHashMap<String, String>();
    for (int i = 0; i < plan.size(); i++) {
      equivalent.put(plan.key(i), scratch[i]);
      assertTrue(plan.expression(i).contains("{{"));
    }
    assertSame(first, resolver.resolvedTheme(panel, base, "", equivalent));
    for (int i = 0; i < plan.size(); i++)
      scratch[i] = "accent".equals(plan.key(i)) ? "#654321" : "12";
    Theme second = resolver.resolvedTheme(plan, base, scratch);
    assertNotSame(first, second);
    assertEquals(0xff654321, second.accent);
    assertEquals(12, second.radius);
    assertEquals(0xff123456, first.accent);
    assertEquals(8, first.radius);
    for (int i = 0; i < plan.size(); i++) scratch[i] = equivalent.get(plan.key(i));
    assertSame(first, resolver.resolvedTheme(plan, base, scratch));
  }
  @Test
  void indexedBindingsRemainStateSpecificAndRejectMismatchedPlans() throws Exception {
    SkinDocument document =
        NcParser.parse("menu { panel { accent = \"{{color}}\"; hover { radius = 15px } } }");
    SkinDocument.Node panel = document.menu().children.get(0);
    SkinDocument.ThemeBindingPlan normal = document.themeBindingPlan(panel, ""),
                                  hover = document.themeBindingPlan(panel, "hover");
    Theme base = Theme.defaults();
    SkinDocument.ThemeResolver resolver = document.themeResolver();
    resolver.beginFrame();
    assertNotSame(normal, hover);
    assertEquals(15, resolver.resolvedTheme(hover, base, new String[] {"#123456"}).radius);
    assertNull(document.themeBindingPlan(document.menu(), ""));
    SkinDocument other = NcParser.parse("menu { panel }");
    assertThrows(IllegalArgumentException.class,
        () -> other.themeResolver().resolvedTheme(normal, base, new String[] {"#123456"}));
    assertThrows(
        IllegalArgumentException.class, () -> resolver.resolvedTheme(normal, base, new String[0]));
  }
  @Test
  void cachedPaintColorsPreserveRgbaNumericValuesAndPerCallFallbacks() {
    for (int pass = 0; pass < 3; pass++) {
      assertEquals(0x44112233, SkinDocument.color("#1234", 0));
      assertEquals(0x80112233, SkinDocument.color("#11223380", 0));
      assertEquals(0x80f0783c, SkinDocument.color("rgba(240,120,60,0.5)", 0));
      assertEquals(0x00ffffff, SkinDocument.color("transparent", 0));
      assertEquals(0xff102030, SkinDocument.color(Integer.toString(0xff102030), 0));
      assertEquals(0xff102030, SkinDocument.color(Double.toString(0xff102030), 0));
      assertEquals(pass, SkinDocument.color("invalid", pass));
      assertEquals(pass, SkinDocument.color(null, pass));
    }
  }
  @Test
  void independentRenderersAndOrdinaryResolutionRetainTheirOwnMutationBoundaries()
      throws Exception {
    SkinDocument document = NcParser.parse("menu { panel }");
    SkinDocument.Node panel = document.menu().children.get(0);
    SkinDocument.ThemeResolver one = document.themeResolver(), two = document.themeResolver();
    Theme base = Theme.defaults();
    one.beginFrame();
    Theme first = one.theme(panel, base, "");
    assertSame(first, document.theme(panel, base, ""));
    base.text = 0xff112233;
    Theme immediate = document.theme(panel, base, "");
    assertEquals(base.text, immediate.text);
    two.beginFrame();
    assertSame(immediate, two.theme(panel, base, ""));
    one.beginFrame();
    assertSame(immediate, one.theme(panel, base, ""));
    document.invalidate();
    assertNotSame(immediate, one.theme(panel, base, ""));
  }
  @Test
  void resolverBeforeFirstFrameRemainsMutationSensitiveAndRejectsNullBases() throws Exception {
    SkinDocument document = NcParser.parse("menu { panel }");
    SkinDocument.Node panel = document.menu().children.get(0);
    SkinDocument.ThemeResolver resolver = document.themeResolver();
    Theme base = Theme.defaults();
    Theme first = resolver.theme(panel, base, "");
    base.accent = 0xff102030;
    assertNotSame(first, resolver.theme(panel, base, ""));
    assertThrows(IllegalArgumentException.class, () -> resolver.theme(panel, null, ""));
  }
}
