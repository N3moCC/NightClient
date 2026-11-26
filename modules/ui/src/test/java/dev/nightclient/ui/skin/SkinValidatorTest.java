package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import dev.nightclient.skin.SkinManager;
import dev.nightclient.skin.SkinParser;
import dev.nightclient.skin.NcParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinValidatorTest {
  @TempDir Path directory;
  private SkinDocument document(String body) throws IOException {
    return SkinParser.parse("<nightclient><menu>" + body + "</menu></nightclient>");
  }
  @Test
  void bundledInterfacePassesTheSameSemanticValidatorAsUserSkins() throws Exception {
    SkinDocument preset = DefaultSkin.document();
    new SkinValidator().validate(preset);
    assertNotNull(preset.menu());
    assertNotNull(preset.hud());
    assertNotNull(preset.component("editor.viewmodel-gizmo"));
  }
  @Test
  void scopedStateAndActionChainsAreValidatedBeforePublication() throws Exception {
    SkinValidator validator = new SkinValidator();
    validator.validate(
        NcParser.parse("state { expanded=false; selected='first' } menu { "
                        + "repeat(source=modules,as=module,key=\"{{module.id}}\") { "
                        + "div(scope=\"module.{{module.id}}\") { "
                        + "button(action=\"toggle:{{module.bind}}\",secondary-action=\"local-"
                        + "toggle:expanded\",middle-action=\"bind-module:{{module.id}}\"); text "
                        + "\"Open\"(if=local.expanded) } } div(scope=choice) { "
                        + "button(action=\"sequence:local-set:selected:second;local-set:expanded:"
                        + "false\"); text \"{{local.selected}}\" } }"));
    for (String source :
        new String[] {"state { expanded=false } menu { button(action='local-toggle:expanded') }",
            "state { expanded=false } menu { div(scope=one) { button(action='local-toggle:typo') } "
            + "}",
            "state { expanded=false } menu { div(scope=one) { "
            + "button(action='local-set:expanded:abc') } }",
            "menu { button(action='sequence:close;execute:evil') }",
            "menu { button(action='toggle:saturation.strength') }"})
      assertThrows(IOException.class, () -> validator.validate(NcParser.parse(source)), source);
  }
  @Test
  void componentChoiceOptionsAndTargetEquipmentExposeOnlyTheirDocumentedFields() throws Exception {
    SkinValidator validator = new SkinValidator();
    validator.validate(NcParser.parse(
        "state { expanded=false } component 'element.choice' (scope='choice.{{bind}}') { "
        + "repeat(source=choiceOptions,as=option,key='{{option.value}}') { button "
        + "'{{option.label}}' "
        + "(action='sequence:set:{{bind}}:{{option.value}};local-set:expanded:false'); text "
        + "'{{option.selected}} {{option.index}}' } } hud { entity-head(source=targetData); "
        + "repeat(source=targetEquipment,as=stack) { item(source=stack) } }"));
    assertThrows(IOException.class,
        ()
            -> validator.validate(NcParser.parse("menu { repeat(source=choiceOptions,as=option) { "
                                                  + "text '{{option.arbitrary}}' } }")));
  }
  @Test
  void staticUnknownIconsBadActionsAndIncorrectControlTypesAreRejectedBeforeDrawing() {
    SkinValidator validator = new SkinValidator();
    for (String markup : new String[] {"<icon src='typo'/>",
             "<button action='execute:evil'>A</button>", "<toggle bind='saturation.strength'/>",
             "<slider bind='protect.enabled'/>", "<input bind='theme.accent'/>",
             "<toggle bind='schemaVersion'/>", "<toggle bind='theme.effects.time'/>",
             "<repeat source='remote'><text>A</text></repeat>", "<item src='INVALID SPACE'/>",
             "<slider bind='saturation.strength' min='NaN'/>", "<text>{{settings.unknown}}</text>",
             "<text>{{page|execute:1}}</text>", "<panel if='missing.setting'/>",
             "<repeat source='cooldowns' as='timer'><text>{{timer.notAField}}</text></repeat>"})
      assertThrows(IOException.class, () -> validator.validate(document(markup)), markup);
  }
  @Test
  void nativeControlsReadonlyDataAndKnownPrimitiveTemplatesAreAccepted() throws Exception {
    SkinValidator validator = new SkinValidator();
    validator.validate(
        document("<icon src='protect'/><toggle bind='protect.enabled'/><input "
                 + "bind='protect.replacement'/><input bind='theme.accent' format='hex'/><slider "
                 + "bind='saturation.strength' min='0' max='2'/><button "
                 + "action='page:style'>Style</button><text>{{label}} {{active}} {{ratio}} "
                 + "{{unit}}</text><repeat source='cooldowns' as='timer'><item "
                 + "src='{{timer.itemId}}'/><text>{{timer.name}} "
                 + "{{timer.remainingSeconds|fixed:1}}</text></repeat>"));
    assertTrue(SkinValidator.itemId("minecraft:ender_pearl"));
    assertFalse(SkinValidator.itemId("bad space"));
  }
  @Test
  void numericInputDisplayFormatsAcceptOnlyFixedZeroThroughThree() throws Exception {
    SkinValidator validator = new SkinValidator();
    for (int digits = 0; digits <= 3; digits++) {
      String attributes = "format='number' display-format='fixed:" + digits + "'";
      validator.validate(document("<input bind='viewmodel.main.x' " + attributes + "/>"));
      validator.validate(document("<input bind='theme.blurQuality' " + attributes + "/>"));
      validator.validate(NcParser.parse(
          "menu { input (bind = viewmodel.main.x, format = number, display-format = \"fixed:"
          + digits + "\") }"));
    }
    validator.validate(
        document("<input bind='{{viewmodelHandBind}}' format='number' display-format='fixed:2'/>"));
    for (String format : new String[] {
             "fixed:4", "fixed:-1", "fixed:01", "fixed:", "Fixed:2", "integer", "", "{{value}}"})
      assertThrows(IOException.class,
          ()
              -> validator.validate(
                  document("<input bind='viewmodel.main.x' format='number' display-format='"
                      + format + "'/>")),
          format);
  }
  @Test
  void displayFormatRequiresAnInputWithExplicitNumericFormatAndBind() throws Exception {
    SkinValidator validator = new SkinValidator();
    for (String markup :
        new String[] {"<input bind='protect.replacement' display-format='fixed:2'/>",
            "<input bind='theme.accent' format='hex' display-format='fixed:2'/>",
            "<input bind='protect.replacement' format='number' display-format='fixed:2'/>",
            "<input format='number' display-format='fixed:2'/>", "<input/>",
            "<repeat source='controls' as='control'><input bind='{{control.bind}}' "
            + "format='{{control.format}}' display-format='fixed:2'/></repeat>",
            "<slider bind='viewmodel.main.x' display-format='fixed:2'/>",
            "<toggle bind='protect.enabled' display-format='fixed:2'/>",
            "<text display-format='fixed:2'>Value</text>", "<panel display-format='fixed:2'/>"})
      assertThrows(IOException.class, () -> validator.validate(document(markup)), markup);
  }
  @Test
  void invalidDisplayFormatReloadReportsPositionAndKeepsWorkingDocument() throws Exception {
    SkinManager manager = new SkinManager(directory, new SkinValidator());
    Path file = directory.resolve("skin.nc");
    Files.write(file,
        "menu { input (bind = viewmodel.main.x, format = number, display-format = \"fixed:2\") }"
            .getBytes(StandardCharsets.UTF_8));
    assertTrue(manager.reload(), manager.error());
    SkinDocument good = manager.document();
    Files.write(file,
        ("menu {\n  input (bind = viewmodel.main.x, format = number, display-format = "
         + "\"fixed:4\");\n}\n").getBytes(StandardCharsets.UTF_8));
    assertFalse(manager.reload());
    assertSame(good, manager.document());
    assertTrue(manager.error().contains("line 2"), manager.error());
    assertTrue(manager.error().contains("column 3"), manager.error());
    assertTrue(manager.error().contains("display format"), manager.error());
  }
  @Test
  void semanticallyBadReloadKeepsPreviousRenderableDocument() throws Exception {
    SkinManager manager = new SkinManager(directory, new SkinValidator());
    Path file = directory.resolve("skin.nc");
    Files.write(file, "menu { icon (src = \"protect\"); }".getBytes(StandardCharsets.UTF_8));
    assertTrue(manager.reload());
    SkinDocument good = manager.document();
    Files.write(file, "menu { icon (src = \"does-not-exist\"); }".getBytes(StandardCharsets.UTF_8));
    assertFalse(manager.reload());
    assertSame(good, manager.document());
    assertTrue(manager.error().contains("Unknown SVG icon"));
  }
  @Test
  void defaultLayoutPassesSemanticValidation() throws Exception {
    new SkinValidator().validate(DefaultSkin.document());
  }
  @Test
  void graphicalPickerRejectsNonColorAndReadOnlyBindingsBeforeActivation() throws Exception {
    SkinValidator validator = new SkinValidator();
    for (String bind : new String[] {"saturation.strength", "protect.enabled", "targetData.health"})
      assertThrows(IOException.class,
          ()
              -> validator.validate(NcParser.parse("menu { color-plane (bind=" + bind + ") }")),
          bind);
    validator.validate(
        NcParser.parse("menu { color-plane (bind=theme.accent); hue-strip (bind=theme.accent); "
                        + "alpha-strip (bind=theme.accent); color-swatch (bind=theme.accent) }"));
  }
  @Test
  void resourceTranslationsHudRowsAndScopedExpressionsValidateTogether() throws Exception {
    SkinValidator validator = new SkinValidator();
    validator.validate(NcParser.parse(
        "menu { div { let tile-width = \"max(40,width/3)\"; width = \"{{= vars.tile_width}}\"; "
        + "text \"{{i18n.ui.find_a_feature}}\"; "
        + "text \"{{= width>300 || hudEditing ? 'wide|window' : 'small'}}\"; "
        + "repeat (source=inventoryCells,as=cell) { item (source=cell.slot); text \"{{= "
          + "cell.occupied || cell.stacked ? cell.count : 0|integer}}\" } "
        + "repeat (source=cooldownRows,as=timer) { text \"{{timer.remainingSeconds|duration}}\" } "
        + "repeat (source=potionRows,as=effect) { text \"{{effect.durationSeconds|clock}}\" } "
        + "choice (bind=target.style,options=\"card|zakohp\",option-labels=\"Card|ZakoHP\") } }"));
    validator.validate(dev.nightclient.skin.DefaultSkin.document());
  }
  @Test
  void expressionBranchesStylesAndReadonlyMapPathsAreCheckedBeforeActivation() {
    SkinValidator validator = new SkinValidator();
    for (String source :
        new String[] {"menu { text \"{{= false ? missing.setting : 1}}\" }",
            "menu { text \"{{= System.exit(0)}}\" }", "menu { text \"{{= targetData.getClass}}\" }",
            "menu { text \"{{i18n.ui.missing_translation_key}}\" }",
            "menu { input (bind=i18n.ui.find_a_feature) }",
            "menu { repeat (source=inventoryCells,as=cell) { text \"{{cell.slot.getClass}}\" } }",
            "menu { repeat (source=profiles,as=profile) { text \"{{profile.count}}\" } }",
            "menu { div { hover { width = \"{{= missing.setting}}\" } } }",
            "menu { div { let tile-width = \"missing.setting*2\"; width = \"{{= "
            + "vars.tile_width}}\" } }",
            "menu { choice (bind=target.style,options=\"card|zakohp\",option-labels=\"Only one\") "
            + "}",
            "menu { text (option-labels=\"A|B\") }"})
      assertThrows(IOException.class, () -> validator.validate(NcParser.parse(source)), source);
  }
  @Test
  void nativeNcReloadReportsTheSourcePositionOfAnInvalidBinding() throws Exception {
    SkinManager manager = new SkinManager(directory, new SkinValidator());
    Path file = directory.resolve("skin.nc");
    Files.write(file,
        "menu {\n  toggle \"Protect\" (bind = \"settings.missing\");\n}\n".getBytes(
            StandardCharsets.UTF_8));
    assertFalse(manager.reload());
    assertNull(manager.document());
    assertEquals("error", manager.status());
    assertTrue(manager.error().contains("NC"));
    assertTrue(manager.error().contains("line 2"), manager.error());
    assertTrue(manager.error().contains("column 3"), manager.error());
    assertTrue(manager.error().contains("settings.missing"));
  }
}
