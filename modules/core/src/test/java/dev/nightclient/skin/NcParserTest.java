package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Theme;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NcParserTest {
  @org.junit.jupiter.api.Test
  void fullReplacementDoesNotInheritTheDefaultRootAnchors() throws Exception {
    SkinDocument full = DefaultSkin.resolve(NcParser.parse(
        "style element menu { position = absolute; x = 6%; y = 8%; width = 88%; height = 84% "
        + "}\nmenu { text \"Replacement\" }\nhud { text \"Independent\" }"));
    org.junit.jupiter.api.Assertions.assertFalse(
        full.styles(full.menu(), "").containsKey("anchor-x"));
    org.junit.jupiter.api.Assertions.assertFalse(
        full.styles(full.menu(), "").containsKey("anchor-y"));
    org.junit.jupiter.api.Assertions.assertFalse(full.styles(full.hud(), "").containsKey("layout"));
    SkinDocument partial =
        DefaultSkin.resolve(NcParser.parse("style element menu { radius = 3 }"));
    org.junit.jupiter.api.Assertions.assertEquals(
        ".5", partial.styles(partial.menu(), "").get("anchor-x"));
  }
  @Test
  void buildsNativeTreeBindingsAndStylesWithoutMarkupOrCssSerialization() throws Exception {
    SkinDocument document = NcParser.parse(
        "theme { accent = #bcaaff; fontSize = 16 }\nmenu {\n container row {\n text \"Привет\" { "
        + "fontSize = 26 }\n toggle \"Насыщенность\" (bind=saturation.enabled)\n slider \"Цвета\" "
        + "(bind=saturation.strength,min=0,max=2,step=.05)\n }\n}\nhud { cooldowns; inventory; tnt "
        + "}\ncomponent \"menu.card.protect\" { text \"Имя\" }");
    SkinDocument.Node row = document.menu().children.get(0), text = row.children.get(0);
    assertEquals("row", row.tag);
    assertEquals("Привет", text.text);
    assertEquals("26", text.inlineStyles.get("font-size"));
    assertFalse(text.attributes.containsKey("style"));
    assertEquals("saturation.enabled", row.children.get(1).attributes.get("bind"));
    assertEquals(".05", row.children.get(2).attributes.get("step"));
    assertEquals(3, document.hud().children.size());
    assertNotNull(document.component("menu.card.protect"));
    assertEquals(0xffbcaaff, document.theme(text, Theme.defaults(), null).accent);
    assertThrows(UnsupportedOperationException.class, () -> text.inlineStyles.put("radius", "99"));
  }

  @Test
  void typedRulesFollowElementClassIdAndLocalInteractionPriority() throws Exception {
    SkinDocument document = NcParser.parse(
        "style element button { radius=2; hover { radius=4 } }\nstyle class card { radius=6 "
        + "}\nstyle id \"menu.card.protect\" { radius=8; hover { radius=10; focus { radius=12 } } "
        + "}\nmenu { button \"A\" (id=\"menu.card.protect\",style=card,action=close) { active { "
        + "radius=14 } } }");
    SkinDocument.Node button = document.menu().children.get(0);
    Theme base = Theme.defaults();
    assertEquals(8, document.theme(button, base, null).radius);
    assertEquals(10, document.theme(button, base, "hover").radius);
    assertEquals(12, document.theme(button, base, "hover focus").radius);
    assertEquals(14, document.theme(button, base, "hover focus active").radius);
    assertTrue(button.classes.contains("card"));
    assertFalse(button.attributes.containsKey("style"));
  }

  @Test
  void normalInlineDeclarationsOverrideRulesAndAreStoredAsNativeData() throws Exception {
    SkinDocument document = NcParser.parse(
        "style id foo { radius=40 }\nmenu { panel (id=foo) { radius=3; hover { radius=5 } } }");
    SkinDocument.Node node = document.menu().children.get(0);
    assertEquals(3, document.theme(node, Theme.defaults(), null).radius);
    assertEquals(5, document.theme(node, Theme.defaults(), "hover").radius);
    assertEquals("3", document.styles(node, null).get("radius"));
  }

  @Test
  void nativeVariablesResolveOnceAndCarryThroughSharedTheme() throws Exception {
    SkinDocument document =
        NcParser.parse("theme { let tone=#8af3c980; let alias=$tone; accent=$alias; opacity=25%; "
                        + "blur=false }\nstyle class lit { borderColor=$tone; grain=true }\nmenu { "
                        + "panel (style=lit) }\nhud { inventory }");
    SkinDocument.Node node = document.menu().children.get(0);
    Theme base = Theme.defaults(), resolved = document.theme(node, base, null);
    assertEquals(0x808af3c9, resolved.accent);
    assertEquals(.25f, resolved.backgroundOpacity);
    assertEquals(0x808af3c9, resolved.border);
    assertTrue(resolved.effects.grain);
    assertFalse(resolved.effects.backgroundBlur);
    assertSame(resolved, document.theme(node, base, null));
    assertEquals(0x808af3c9, document.theme(document.hud(), base, null).accent);
  }

  @Test
  void commentsEscapesTemplatesAndCyrillicKeepTheirMeaning() throws Exception {
    SkinDocument document =
        NcParser.parse("// comment\r\ntheme { accent=#aabbcc } // another\r\nmenu {\r\n text "
                        + "\"Кириллица \\\"и\\\" \\u0416\\n{{item.name}} // текст\"\r\n}");
    assertEquals("Кириллица \"и\" Ж\n{{item.name}} // текст", document.menu().children.get(0).text);
    assertEquals(4, document.menu().children.get(0).sourceLine);
  }

  @Test
  void optionalSemicolonsAndMultilineHeadersDoNotConsumeLeafNodes() throws Exception {
    SkinDocument document = NcParser.parse("menu\n{\n panel\n { text \"A\" }\n text \"B\"\n text "
                                            + "\"C\"; text \"D\"\n}\nhud { crosshair }");
    assertEquals(4, document.menu().children.size());
    assertEquals("B", document.menu().children.get(1).text);
    assertEquals("crosshair", document.hud().children.get(0).tag);
  }

  @Test
  void invalidSyntaxAndTypedValuesReportLineAndColumn() {
    IOException syntax =
        assertThrows(IOException.class, () -> NcParser.parse("menu {\n text \"A\"\n unknown\n}"));
    assertTrue(syntax.getMessage().startsWith("NC line 3, column 2:"), syntax.getMessage());
    IOException color =
        assertThrows(IOException.class, () -> NcParser.parse("theme {\n accent = #zzzzzz\n}"));
    assertTrue(color.getMessage().startsWith("NC line 2, column 11:"), color.getMessage());
    for (String source : new String[] {"theme { radius=NaN }", "menu { panel { width=banana } }",
             "theme { material=chrome }", "style class card { frosty=true }",
             "menu { text \"Unclosed }", "menu { text \"A\" text \"B\" }",
             "menu { panel (onclick=evil) }", "menu { panel (id=x,id=y) }"}) {
      IOException failure = assertThrows(IOException.class, () -> NcParser.parse(source), source);
      assertTrue(failure.getMessage().contains("column"));
    }
  }

  @Test
  void removedMaterialAndDecorativeEffectRecipesFailDuringParsing() {
    for (String property : new String[] {"material=solid", "material=outline", "material=leather",
             "material=glass", "variant=pill", "leather=true", "glow=true", "shadows=true",
             "accentLight=true", "shadowRadius=4"})
      assertThrows(
          IOException.class, () -> NcParser.parse("theme { " + property + " } menu {}"), property);
  }

  @Test
  void minecraftTextShadowIsARegularPrimitiveProperty() throws Exception {
    SkinDocument document = NcParser.parse("menu { game-text 'A' { shadow=1 } }");
    assertEquals("1", document.styles(document.menu().children.get(0), "").get("shadow"));
  }

  @Test
  void validatesUnusedRulesSoBadComponentStylesCannotFailDuringRendering() {
    assertThrows(IOException.class,
        () -> NcParser.parse("style id \"menu.card.protect\" { radius=$unknown }\nmenu {}"));
    assertThrows(IOException.class,
        ()
            -> NcParser.parse(
                "theme { let radius=banana }\nstyle class unused { radius=$radius }\nmenu {}"));
    assertThrows(IOException.class,
        ()
            -> NcParser.parse(
                "theme { let first=$second; let second=$first; accent=$first }\nmenu {}"));
  }

  @Test
  void onlyNativeDeclarationsAreAcceptedAndCannotExecuteCodeOrLoadResources() {
    for (String source :
        new String[] {"<nightclient><menu/></nightclient>", "theme { accent: #aabbcc; }",
            "style .card { radius=4 }", "theme { accent=\"var(--color)\" }",
            "theme { background=\"url(file:///etc/passwd)\" }", "script \"java.lang.Runtime\"",
            "menu { include \"https://example.invalid\" }", "menu { button (onClick=exec) }"})
      assertThrows(IOException.class, () -> NcParser.parse(source), source);
  }

  @Test
  void structurePropertyRuleValueAndByteLimitsAreEnforced() {
    StringBuilder many = new StringBuilder("menu {\n");
    for (int i = 0; i < NcParser.MAX_NODES; i++) many.append("text \"A\"\n");
    many.append('}');
    assertThrows(IOException.class, () -> NcParser.parse(many.toString()));
    StringBuilder deep = new StringBuilder("menu {");
    for (int i = 0; i < 31; i++) deep.append("panel {");
    for (int i = 0; i < 31; i++) deep.append('}');
    deep.append('}');
    assertThrows(IOException.class, () -> NcParser.parse(deep.toString()));
    StringBuilder properties = new StringBuilder("theme {");
    for (int i = 0; i < 129; i++) properties.append("let p").append(i).append("=1;");
    properties.append('}');
    assertThrows(IOException.class, () -> NcParser.parse(properties.toString()));
    StringBuilder rules = new StringBuilder();
    for (int i = 0; i <= NcParser.MAX_RULES; i++) rules.append("style class a { radius=1 }\n");
    assertThrows(IOException.class, () -> NcParser.parse(rules.toString()));
    StringBuilder longText = new StringBuilder("menu { text \"");
    for (int i = 0; i < 4097; i++) longText.append('a');
    longText.append("\" }");
    assertThrows(IOException.class, () -> NcParser.parse(longText.toString()));
    byte[] large = new byte[NcParser.MAX_BYTES + 1];
    assertThrows(IOException.class, () -> NcParser.parse(large));
    assertThrows(IOException.class, () -> NcParser.parse(new byte[] {(byte) 0xc3, (byte) 0x28}));
  }

  @Test
  void duplicateRootsComponentsAndIdsAreRejected() {
    for (String source : new String[] {"theme {} theme {}", "menu {} menu {}", "hud {} hud {}",
             "component \"card\" {} component \"card\" {}",
             "menu { panel (id=card); text \"A\" (id=card) }", "menu { hud {} }"})
      assertThrows(IOException.class, () -> NcParser.parse(source), source);
  }

  @Test
  void shippedNativeDocumentBuildsCompleteTree() throws Exception {
    for (String name : new String[] {"default.nc"}) {
      byte[] bytes;
      try (
          InputStream input = getClass().getResourceAsStream("/assets/nightclient/skins/" + name)) {
        assertNotNull(input, name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) out.write(buffer, 0, count);
        bytes = out.toByteArray();
      }
      SkinDocument document = NcParser.parse(bytes);
      assertNotNull(document.menu());
      assertNotNull(DefaultSkin.document().hud());
      assertTrue(document.nodeCount() > 15);
      assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("controls"));
    }
  }
  @Test
  void bundledInterfaceIsCompleteBoundedAndUsesTheSameNativeContractAsUserDocuments()
      throws Exception {
    SkinDocument document = DefaultSkin.document();
    assertNotNull(document.menu());
    assertNotNull(document.hud());
    assertNotNull(document.component("element.toggle"));
    assertNotNull(document.component("color-picker"));
    assertNotNull(document.component("editor.viewmodel-gizmo"));
    byte[] source = DefaultSkin.source();
    assertTrue(source.length < NcParser.MAX_BYTES);
    assertNotNull(NcParser.parse(source).hud());
    Theme values = Theme.defaults();
    assertFalse(values.name.isEmpty());
    assertFalse(values.fontFamily.isEmpty());
    assertEquals(DefaultSkin.canonicalPreset(""), DefaultSkin.canonicalPreset("default"));
    assertThrows(IllegalArgumentException.class, () -> DefaultSkin.document("unknown"));
  }
  @Test
  void defaultIsTheOnlyBundledThemeAndSafeLoadingMigratesRetiredIds() throws Exception {
    assertArrayEquals(new String[] {"default"}, DefaultSkin.presetIds());
    assertEquals("default", DefaultSkin.presetOptions());
    assertEquals("NightClient", DefaultSkin.presetLabels());
    assertEquals("default", DefaultSkin.canonicalPreset("default"));
    for (String retired : new String[] {"studio", "rivet", "kinetic", "parallax"}) {
      assertThrows(IllegalArgumentException.class, () -> DefaultSkin.canonicalPreset(retired));
      assertSame(DefaultSkin.document(), DefaultSkin.documentSafe(retired));
      assertArrayEquals(DefaultSkin.source(), DefaultSkin.sourceSafe(retired));
    }
  }
  @Test
  void acceptsStableScopedInstancesOverlayAnchorsAndSurfaceCorners() throws Exception {
    SkinDocument document = NcParser.parse(
        "state { expanded=false } menu { repeat(source=modules,as=module,key=\"{{module.id}}\") { "
        + "layout=masonry; masonryStable=true; columns=2; div(scope=\"module.{{module.id}}\") { "
        + "button(action=\"toggle:{{module.bind}}\",secondary-action=\"local-toggle:expanded\","
        + "middle-action=\"bind-module:{{module.id}}\") { height=18px } div(if=\"local.expanded\") "
        + "{ height=auto; radiusTopLeft=4px; radiusBottomRight=0px } } } panel(anchor=trigger) { "
        + "position=overlay; anchorSide=below; anchorAlign=end; anchorGap=4px; zIndex=20; "
        + "scrollDuration=80ms } }");
    assertEquals("masonry", document.styles(document.menu().children.get(0), "").get("layout"));
    assertEquals("{{module.id}}", document.menu().children.get(0).attribute("key", ""));
  }
}
