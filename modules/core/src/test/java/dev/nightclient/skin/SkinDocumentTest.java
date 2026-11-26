package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Theme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinDocumentTest {
  @TempDir Path directory;
  private static Theme blurTheme() {
    Theme theme = Theme.defaults();
    theme.effects.backgroundBlur = true;
    return theme;
  }
  @Test
  void cascadeSpecificityStatesVariablesAndLiteralComponentIdsWork() throws Exception {
    SkinDocument d = SkinParser.parse(
        "<nightclient><style>:root{--accent:#9b8cff;color:#ffffff;} panel{radius:6px;} "
        + ".card{radius:8px;} #menu.card.protect{radius:12px;accent:var(--accent);} "
        + "#menu.card.protect:hover{radius:16px;} </style><menu><panel id='menu.card.protect' "
        + "class='card'>Привет</panel></menu></nightclient>");
    SkinDocument.Node n = d.menu().children.get(0);
    Theme base = Theme.defaults();
    assertEquals(12, d.theme(n, base, "normal").radius);
    assertEquals(16, d.theme(n, base, "hover").radius);
    assertEquals(0xff9b8cff, d.theme("menu.card.protect", base).accent);
    assertEquals(0xffffffff, d.theme(n, base, "normal").text);
    assertEquals("Привет", n.text);
    assertEquals("16px", d.styles(n, "hover").get("radius"));
    assertThrows(UnsupportedOperationException.class, () -> n.attributes.put("x", "1"));
    assertThrows(UnsupportedOperationException.class, () -> n.children.clear());
    assertThrows(UnsupportedOperationException.class, () -> d.styles(n, "normal").clear());
  }
  @Test
  void unsupportedSyntaxAndInvalidTypedValuesAreRejected() {
    for (String css : new String[] {"panel{wat:12;}", "panel{radius:NaN;}", "panel{color:#zzzzzz;}",
             "panel{width:banana;}", "panel{material:chrome;}",
             "panel{--a:var(--a);accent:var(--a);}", "panel > text{color:white;}",
             "panel:script{color:white;}", "panel{background:url(https://x);}"}) {
      assertThrows(IOException.class,
          ()
              -> SkinParser.parse(
                  "<nightclient><style>" + css + "</style><menu><panel/></menu></nightclient>"),
          css);
    }
    assertThrows(IOException.class,
        ()
            -> SkinParser.parse(
                "<nightclient><menu><script>alert(1)</script></menu></nightclient>"));
    assertThrows(IOException.class,
        ()
            -> SkinParser.parse(
                "<nightclient><menu><button onclick='evil'>A</button></menu></nightclient>"));
  }
  @Test
  void variableExpansionAndDeclarationCountsStayBounded() {
    StringBuilder css = new StringBuilder(":root{--a:");
    for (int i = 0; i < 1000; i++) css.append("var(--a)");
    css.append(";} panel{accent:var(--a);}");
    assertThrows(IOException.class,
        ()
            -> SkinParser.parse(
                "<nightclient><style>" + css + "</style><menu><panel/></menu></nightclient>"));
    StringBuilder many = new StringBuilder(":root{");
    for (int i = 0; i < 129; i++) many.append("--v").append(i).append(":1;");
    many.append("}");
    assertThrows(IOException.class,
        () -> SkinParser.parse("<nightclient><style>" + many + "</style><menu/></nightclient>"));
  }
  @Test
  void entitiesNamespacesAndExternalDeclarationsCannotReadFiles() {
    for (String source :
        new String[] {"<!DOCTYPE nightclient [<!ENTITY e SYSTEM "
                      + "'file:///etc/passwd'>]><nightclient><menu>&e;</menu></nightclient>",
            "<!DOCTYPE nightclient SYSTEM 'https://example.invalid/evil.dtd'><nightclient/>",
            "<nightclient xmlns='https://example.invalid'/>", "<?load evil?><nightclient/>"})
      assertThrows(IOException.class, () -> SkinParser.parse(source));
  }
  @Test
  void nodeDepthAndUtf8ByteLimitsAreEnforced() {
    StringBuilder many = new StringBuilder("<nightclient><menu>");
    for (int i = 0; i < 512; i++) many.append("<text/>");
    many.append("</menu></nightclient>");
    assertThrows(IOException.class, () -> SkinParser.parse(many.toString()));
    StringBuilder deep = new StringBuilder("<nightclient><menu>");
    for (int i = 0; i < 32; i++) deep.append("<panel>");
    for (int i = 0; i < 32; i++) deep.append("</panel>");
    deep.append("</menu></nightclient>");
    assertThrows(IOException.class, () -> SkinParser.parse(deep.toString()));
    StringBuilder bytes = new StringBuilder("<nightclient><menu><text>");
    for (int i = 0; i < 32768; i++) bytes.append('Я');
    bytes.append("</text></menu></nightclient>");
    assertThrows(IOException.class, () -> SkinParser.parse(bytes.toString()));
  }
  @Test
  void backdropDemandFollowsPaintedSurfacesAndHoverExceptions() throws Exception {
    Theme base = blurTheme();
    base.effects.backgroundBlur = false;
    SkinDocument text =
        SkinParser.parse("<nightclient><menu><text>No panel</text></menu></nightclient>");
    assertFalse(text.usesBackdrop(base));
    assertTrue(text.usesMenuBackdrop(blurTheme()), "Menu root paints its inherited panel surface");
    SkinDocument solid = SkinParser.parse(
        "<nightclient><style>panel{blur:false;}</style><menu><panel/></menu></nightclient>");
    assertFalse(solid.usesBackdrop(base));
    SkinDocument local = SkinParser.parse(
        "<nightclient><style>panel:hover{blur:true;}</style><menu><panel/></menu></nightclient>");
    assertTrue(local.usesBackdrop(base));
    SkinDocument zero = SkinParser.parse("<nightclient><style>panel{blur:true;opacity:0;}</"
                                         + "style><menu><panel/></menu></nightclient>");
    assertTrue(zero.usesBackdrop(base));
    SkinDocument hidden = SkinParser.parse("<nightclient><style>panel{blur:true;display:none;}</"
                                           + "style><menu><panel/></menu></nightclient>");
    assertFalse(hidden.usesBackdrop(base));
    SkinDocument disabled = SkinParser.parse(
        "<nightclient><style>panel{blur:false;}</style><menu><panel/></menu></nightclient>");
    assertFalse(disabled.usesBackdrop(base));
  }
  @Test
  void zeroBlurMixRemovesCaptureDemandAndChangingItInvalidatesTheDemandCache() throws Exception {
    SkinDocument document = NcParser.parse("menu { panel { width=100px; height=40px } }");
    Theme theme = blurTheme();
    theme.blurMix = 0;
    assertFalse(document.usesMenuBackdrop(theme));
    theme.blurMix = .5f;
    assertTrue(document.usesMenuBackdrop(theme));
    theme.blurMix = Float.NaN;
    assertFalse(document.usesMenuBackdrop(theme));
  }
  @Test
  void transparentHudAndSixQualityLevelsKeepExplicitBlurAndMatteSettings() throws Exception {
    SkinDocument d = NcParser.parse("theme { backgroundOpacity = 0; blurQuality = 6; grain = "
                                     + "true; grainAmount = .1 }\nhud { armor }");
    Theme base = blurTheme();
    Theme actual = d.theme(d.hud(), base, null);
    assertEquals(0, actual.backgroundOpacity);
    assertEquals(6, actual.blurQuality);
    assertTrue(actual.effects.grain);
    assertEquals(.1f, actual.effects.grainAmount, 1e-6f);
    assertTrue(d.usesHudBackdrop(base));
    assertTrue(d.usesHudBackdrop(base, id -> "armor".equals(id)));
    base.effects.backgroundBlur = false;
    assertFalse(d.usesHudBackdrop(base));
    assertFalse(d.usesHudBackdrop(base, id -> true));
  }
  @Test
  void hudBackdropWidgetCacheRecomputesWhenActivityOrThemeChanges() throws Exception {
    SkinDocument document = NcParser.parse("hud { armor }");
    Theme base = blurTheme();
    assertTrue(document.usesHudBackdrop(base, id -> "armor".equals(id)));
    assertFalse(document.usesHudBackdrop(base, id -> false));
    assertTrue(document.usesHudBackdrop(base, id -> "armor".equals(id)));
    base.effects.backgroundBlur = false;
    assertFalse(document.usesHudBackdrop(base, id -> "armor".equals(id)));
    base.effects.backgroundBlur = true;
    assertTrue(document.usesHudBackdrop(base, id -> "armor".equals(id)));
  }
  @Test
  void menuAndHudCaptureDemandDoNotLeakAcrossScopes() throws Exception {
    Theme base = blurTheme();
    base.effects.backgroundBlur = false;
    SkinDocument d = SkinParser.parse(
        "<nightclient><style>menu{blur:false;} hud{blur:true;} "
        + ".local{blur:true;}</style><menu><panel/></menu><hud><panel/></hud><component "
        + "name='menu.card.protect'><panel class='local'/></component></nightclient>");
    assertTrue(d.usesBackdrop(base));
    assertTrue(d.usesMenuBackdrop(base));
    assertTrue(d.usesHudBackdrop(base));
    SkinDocument hudOnly =
        SkinParser.parse("<nightclient><style>hud{blur:true;}</style><menu><panel/></"
                         + "menu><hud><panel/></hud></nightclient>");
    assertFalse(hudOnly.usesMenuBackdrop(base));
    assertTrue(hudOnly.usesHudBackdrop(base));
    base.effects.backgroundBlur = false;
    assertTrue(hudOnly.usesHudBackdrop(base),
        "Explicit per-surface blur remains enabled independently of the base");
    SkinDocument disabled = SkinParser.parse(
        "<nightclient><style>hud{blur:false;}</style><hud><panel/></hud></nightclient>");
    assertFalse(disabled.usesHudBackdrop(base));
  }
  @Test
  void menuBackdropIncludesIdAndElementReplacementSurfaces() throws Exception {
    Theme base = blurTheme();
    base.effects.backgroundBlur = false;
    for (String id : new String[] {"protect", "nav0", "element.button"}) {
      SkinDocument d = NcParser.parse(
          "menu { button (id = protect) }\ncomponent \"" + id + "\" { panel { blur = true } }");
      assertTrue(d.usesMenuBackdrop(base), id);
      assertFalse(d.usesHudBackdrop(base), id + " cannot mount without a HUD tree");
      base.effects.backgroundBlur = false;
      assertTrue(
          d.usesMenuBackdrop(base), "Explicit component blur is independent of the base setting");
      base.effects.backgroundBlur = true;
    }
  }
  @Test
  void cssHexRgbaAndBackgroundOnlyOpacityPreserveForeground() throws Exception {
    SkinDocument d =
        SkinParser.parse("<nightclient><style>panel{background:#1234;color:rgba(240,120,60,0.5);"
                         + "opacity:25%;}</style><menu><panel/></menu></nightclient>");
    Theme t = d.theme(d.menu().children.get(0), Theme.defaults(), null);
    assertEquals(0x44112233, t.background);
    assertEquals(0x80f0783c, t.text);
    assertEquals(.25f, t.backgroundOpacity);
  }
  @Test
  void cachedThemesFollowBaseChangesAndTimeWithoutAllocatingPerFrame() throws Exception {
    SkinDocument d = SkinParser.parse("<nightclient><style>panel{accent:#aabbcc;grain:true;grain-"
                                      + "amount:.1;}</style><menu><panel/></menu></nightclient>");
    SkinDocument.Node node = d.menu().children.get(0);
    Theme base = Theme.defaults();
    Theme one = d.theme(node, base, null);
    assertSame(one, d.theme(node, base, null));
    base.effects.time = 42;
    assertSame(one, d.theme(node, base, null));
    assertEquals(42, one.effects.time);
    assertTrue(one.effects.grain);
    assertEquals(.1f, one.effects.grainAmount, 1e-6f);
    base.text = 0xff010203;
    Theme two = d.theme(node, base, null);
    assertNotSame(one, two);
    assertEquals(base.text, two.text);
    d.clearCache();
    assertNotSame(two, d.theme(node, base, null));
  }
  @Test
  void badReloadKeepsLastWorkingDocumentAndShowsError() throws Exception {
    Path file = directory.resolve("skin.nc");
    Files.write(file, "menu { text \"A\"; }".getBytes(StandardCharsets.UTF_8));
    SkinManager manager = new SkinManager(directory);
    assertTrue(manager.reload());
    SkinDocument first = manager.document();
    Files.write(file, "<broken>".getBytes(StandardCharsets.UTF_8));
    assertFalse(manager.reload());
    assertSame(first, manager.document());
    assertEquals("error", manager.status());
    assertNotNull(manager.error());
    Files.write(file, "menu { text \"B\"; }".getBytes(StandardCharsets.UTF_8));
    assertTrue(manager.reload());
    assertNull(manager.error());
    assertNotSame(first, manager.document());
  }
  @Test
  void managerRejectsTraversalAndPreservesUserFilesWhenWritingExamples() throws Exception {
    SkinManager manager = new SkinManager(directory);
    assertFalse(manager.reload("../outside.nc"));
    assertFalse(manager.reload("skin.html"));
    Path file = directory.resolve("skin.nc");
    byte[] mine = "keep my file".getBytes(StandardCharsets.UTF_8);
    Files.write(file, mine);
    assertEquals(0, manager.writeExamples().size());
    assertArrayEquals(mine, Files.readAllBytes(file));
    Files.delete(file);
    assertEquals(1, manager.writeExamples().size());
    assertEquals(0, manager.writeExamples().size());
    byte[] shipped;
    try (java.io.InputStream in =
             getClass().getResourceAsStream("/assets/nightclient/skins/default.nc")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] block = new byte[4096];
      int count;
      while ((count = in.read(block)) >= 0) out.write(block, 0, count);
      shipped = out.toByteArray();
    }
    assertArrayEquals(DefaultSkin.source(), Files.readAllBytes(file));
    assertNotNull(NcParser.parse(Files.readAllBytes(file)).hud());
    Path alternate = directory.resolve("alternate");
    Files.createDirectories(alternate);
    SkinManager managerAlternate = new SkinManager(alternate);
    assertEquals(1, managerAlternate.writeExamples("default").size());
    assertArrayEquals(
        DefaultSkin.source("default"), Files.readAllBytes(alternate.resolve("skin.nc")));
    assertNotNull(NcParser.parse(Files.readAllBytes(alternate.resolve("skin.nc"))).menu());
  }
  @Test
  void shippedDocumentContainsMenuHudAndWorkingBindings() throws Exception {
    for (String name : new String[] {"default.nc"}) {
      byte[] bytes;
      try (java.io.InputStream in =
               getClass().getResourceAsStream("/assets/nightclient/skins/" + name)) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) >= 0) out.write(b, 0, n);
        bytes = out.toByteArray();
      }
      SkinDocument d = NcParser.parse(bytes);
      assertNotNull(d.menu());
      assertNotNull(DefaultSkin.document().hud());
      assertTrue(d.nodeCount() > 15);
      assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("controls"));
    }
  }
}
