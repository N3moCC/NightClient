package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiCatalogMetadataTest {
  @Test
  void ResourceMetadataChangesLanguageWithoutChangingActualSettingsBindings() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Module russian = catalog.module("crosshair");
    assertEquals(I18n.text("ru", russian.labelKey), russian.label);
    settings.language = "en";
    UiCatalog.Module english = catalog.module("crosshair");
    assertEquals(russian.bind, english.bind);
    assertEquals(russian.icon, english.icon);
    assertEquals(
        russian.label, english.label, "Module names keep their English identity in both languages");
    assertNotEquals(
        russian.summary, english.summary, "Descriptions still follow the chosen language");
    UiCatalog.Control mode = catalog.control("crosshair.colorMode");
    assertEquals("vanilla|custom|pixels", mode.options);
    assertEquals(3, mode.optionLabels.split("\\|", -1).length);
    assertNotEquals(mode.options, mode.optionLabels);
    assertEquals(I18n.text("en", mode.labelKey), mode.label);
    assertNull(catalog.control("undeclared.setting"));
  }

  @Test
  void TweaksyIsOneParentCardWithOnlyNoHurtCamOnByDefault() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Module tweaksy = catalog.module("tweaksy");
    assertNotNull(tweaksy);
    assertTrue(tweaksy.enabled);
    assertTrue(tweaksy.hasControls);
    assertTrue(catalog.modules(false).stream().noneMatch(module
        -> module.id.equals("commandfix") || module.id.equals("noFluid")
            || module.id.equals("fullBright") || module.id.equals("cursorFix")));
    assertEquals(8, catalog.controls("tweaksy").size());
    assertEquals("tweaksy.enabled", tweaksy.bind);
    assertFalse(settings.tweaksy.mainLeftFix.enabled);
    assertFalse(settings.tweaksy.commandFix.enabled);
    assertFalse(settings.tweaksy.cursorFix.enabled);
    assertFalse(settings.tweaksy.fullBright.enabled);
    assertNotNull(catalog.control("tweaksy.cursorFix.enabled"));
    SkinBindings bindings = new SkinBindings(settings);
    UiCatalog.Control strength = catalog.control("tweaksy.fullBright.strength");
    assertFalse(catalog.visible(strength, bindings, Collections.emptyMap()));
    settings.tweaksy.fullBright.enabled = true;
    assertTrue(catalog.visible(strength, bindings, Collections.emptyMap()));
  }

  @Test
  void FreelookCardBadgeShowsHoldKeyAndHidesVanillaUnboundLabels() {
    Settings settings = new Settings();
    dev.nightclient.ui.UiActions actions = (dev.nightclient.ui.UiActions) java.lang.reflect.Proxy
        .newProxyInstance(dev.nightclient.ui.UiActions.class.getClassLoader(),
            new Class<?>[] {dev.nightclient.ui.UiActions.class},
            (proxy, method, args) -> "keyBindingLabel".equals(method.getName())
                ? ("freelook".equals(args[0]) ? "L ALT" : "Не назначена")
                : null);
    UiCatalog catalog = new UiCatalog(settings, actions);
    assertEquals("L ALT", catalog.module("freelook").cardBindingLabel);
    assertEquals("", catalog.module("itemPhysics").cardBindingLabel);
  }

  @Test
  void FixedFoodAndEditorGeometryPoliciesComeFromTheResourceGroups() {
    UiCatalog catalog = new UiCatalog(new Settings());
    assertTrue(catalog.controls("food").isEmpty());
    assertTrue(catalog.editorControls("food").isEmpty());
    assertTrue(catalog.editorControls("armor").stream().anyMatch(
        control -> control.bind.equals("armor.durability")));
    assertFalse(catalog.editorControls("armor").stream().anyMatch(
        control -> control.bind.matches("armor.(offsetX|offsetY|width|height|scale)")));
    assertFalse(catalog.controls("armor").stream().anyMatch(
        control -> control.bind.matches("armor.(width|height)")));
    assertNull(catalog.control("armor.width"));
    assertNull(catalog.control("armor.height"));
    List<UiCatalog.Control> style = catalog.styleControls(false);
    assertEquals("theme.mode", style.get(0).bind);
    for (String bind : new String[] {
             "menu.scale", "menu.hudScale", "menu.fontScale", "menu.animationSpeed",
             "menu.chatHudEditor"})
      assertTrue(
          catalog.controls("menu").stream().anyMatch(control -> control.bind.equals(bind)), bind);
    assertFalse(catalog.controls("menu").stream().anyMatch(
                    control -> control.bind.equals("menu.vanillaBlur")),
        "Glass blur has one appearance control instead of a second retired menu");
    assertTrue(style.stream().anyMatch(control -> control.bind.equals("theme.customBackground")));
    assertTrue(
        style.stream().anyMatch(control -> control.bind.equals("theme.effects.backgroundBlur")));
    assertThrows(UnsupportedOperationException.class, () -> style.clear());
  }

  @Test
  void HitboxCatalogHasNoWallPenetratingVisibilityControl() {
    UiCatalog catalog = new UiCatalog(new Settings());
    assertNull(catalog.control("hitbox.throughWalls"));
    assertTrue(catalog.controls("hitbox").stream().noneMatch(
        control -> control.bind.equals("hitbox.throughWalls")));
    assertTrue(catalog.editorControls("hitbox").stream().noneMatch(
        control -> control.bind.equals("hitbox.throughWalls")));
  }

  @Test
  void HitColorArmorToggleIsExposedAndHiddenInHitboxOnlyMode() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Control armor = catalog.control("hitColor.armor");
    assertNotNull(armor);
    assertEquals("toggle", armor.kind);
    assertEquals("!hitColor.mode=hitbox", armor.condition);
    assertTrue(catalog.controls("hitColor").contains(armor));
    assertTrue(catalog.editorControls("hitColor").contains(armor));
    assertNotEquals(armor.labelKey, armor.label);

    SkinBindings bindings = new SkinBindings(settings);
    settings.hitColor.mode = "entity";
    assertTrue(catalog.visible(armor, bindings, Collections.emptyMap()));
    settings.hitColor.mode = "both";
    assertTrue(catalog.visible(armor, bindings, Collections.emptyMap()));
    settings.hitColor.mode = "hitbox";
    assertFalse(catalog.visible(armor, bindings, Collections.emptyMap()));
  }

  @Test
  void ItemScrollerExposesGestureAndWheelSettingsWithConditionalVisibility() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Module module = catalog.module("itemScroller");
    assertNotNull(module);
    assertTrue(module.hasControls);

    String[] expected = new String[] {
        "itemScroller.dragMatching",
        "itemScroller.dragOnlyMatching",
        "itemScroller.dragCooldownMs",
        "itemScroller.moveMatching",
        "itemScroller.dropMatching",
        "itemScroller.shiftPlace",
        "itemScroller.wheel",
        "itemScroller.wheelMode",
        "itemScroller.wheelDirection",
        "itemScroller.cooldownMs",
        "itemScroller.matchNbt",
    };
    assertEquals(expected.length, catalog.controls("itemScroller").size());
    assertEquals(expected.length, catalog.editorControls("itemScroller").size());
    for (String bind : expected) {
      UiCatalog.Control control = catalog.control(bind);
      assertNotNull(control, bind);
      assertTrue(catalog.controls("itemScroller").contains(control), bind);
      assertTrue(catalog.editorControls("itemScroller").contains(control), bind);
      assertNotEquals(control.labelKey, control.label, bind);
    }

    SkinBindings bindings = new SkinBindings(settings);
    UiCatalog.Control dragOnly = catalog.control("itemScroller.dragOnlyMatching");
    UiCatalog.Control dragDelay = catalog.control("itemScroller.dragCooldownMs");
    UiCatalog.Control wheelMode = catalog.control("itemScroller.wheelMode");
    UiCatalog.Control wheelDir = catalog.control("itemScroller.wheelDirection");
    UiCatalog.Control wheelDelay = catalog.control("itemScroller.cooldownMs");

    assertTrue(catalog.visible(dragOnly, bindings, Collections.emptyMap()));
    assertTrue(catalog.visible(dragDelay, bindings, Collections.emptyMap()));
    assertFalse(catalog.visible(wheelMode, bindings, Collections.emptyMap()));
    assertFalse(catalog.visible(wheelDir, bindings, Collections.emptyMap()));
    assertFalse(catalog.visible(wheelDelay, bindings, Collections.emptyMap()));

    settings.itemScroller.dragMatching = false;
    settings.itemScroller.wheel = true;
    assertFalse(catalog.visible(dragOnly, bindings, Collections.emptyMap()));
    assertFalse(catalog.visible(dragDelay, bindings, Collections.emptyMap()));
    assertTrue(catalog.visible(wheelMode, bindings, Collections.emptyMap()));
    assertTrue(catalog.visible(wheelDir, bindings, Collections.emptyMap()));
    assertTrue(catalog.visible(wheelDelay, bindings, Collections.emptyMap()));

    settings.language = "en";
    UiCatalog.Control wheelModeEn = catalog.control("itemScroller.wheelMode");
    assertEquals("stack|matching", wheelModeEn.options);
    assertEquals(2, wheelModeEn.optionLabels.split("\\|", -1).length);
    assertNotEquals(wheelModeEn.labelKey, wheelModeEn.label);
  }

  @Test
  void VisualEditorOnlyModulesKeepTheirSemanticIdentityWithoutASecondFeatureCard() {
    UiCatalog catalog = new UiCatalog(new Settings());
    UiCatalog.Module viewmodel = catalog.module("viewmodel");
    assertNotNull(viewmodel);
    assertEquals("viewmodel", viewmodel.id);
    assertEquals("viewmodel.enabled", viewmodel.bind);
    assertFalse(viewmodel.menuVisible);
    assertFalse(viewmodel.icon.isEmpty());
    assertFalse(viewmodel.label.isEmpty());
    assertTrue(catalog.modules(false).stream().noneMatch(module -> module.id.equals("viewmodel")));
    assertNotNull(catalog.control("viewmodel.main.rotateY"));
    assertTrue(catalog.editorControls("viewmodel")
            .stream()
            .anyMatch(control -> control.bind.equals("viewmodel.main.rotateY")));
    assertTrue(catalog.modules(true).stream().allMatch(module -> module.menuVisible));
  }

  @Test
  void CachedVisibleListsRefreshValuesAndLanguageWithoutCachingMutableSettingsOwners() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    List<UiCatalog.Module> features = catalog.modules(false), hud = catalog.modules(true);
    assertSame(features, catalog.modules(false));
    assertSame(hud, catalog.modules(true));
    UiCatalog.Module saturation = catalog.module("saturation");
    assertFalse(saturation.enabled);
    settings.saturation = new Settings.Saturation();
    settings.saturation.enabled = true;
    assertSame(saturation, catalog.module("saturation"));
    assertTrue(saturation.enabled);
    assertTrue(catalog.toggleModule("saturation"));
    assertFalse(settings.saturation.enabled);
    assertThrows(UnsupportedOperationException.class, () -> features.clear());
    settings.language = "en";
    List<UiCatalog.Module> english = catalog.modules(false);
    assertNotSame(features, english);
    assertEquals(saturation.label, catalog.module("saturation").label);
    assertNotEquals(saturation.summary, catalog.module("saturation").summary);
    assertSame(english, catalog.modules(false));
  }

  @Test
  void glassAndEditablePalettesAreTheOnlyAppearanceChoices() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    java.util.Set<String> exposed = new java.util.HashSet<String>();
    for (UiCatalog.Control control : catalog.styleControls(true)) exposed.add(control.bind);
    for (String bind : new String[] {"theme.mode", "theme.customBackground", "theme.customSurface",
             "theme.customRaised", "theme.customText", "theme.effects.backgroundBlur",
             "theme.blurRadius"})
      assertTrue(exposed.contains(bind), bind);
    for (String removed : new String[] {"theme.material", "theme.effects.glow",
             "theme.effects.shadows", "theme.effects.accentLight"})
      assertNull(catalog.control(removed), removed);
    for (String removed :
        new String[] {"menu.backdropDim", "menu.backgroundEnabled", "menu.reducedMotion"})
      assertNull(catalog.control(removed), removed);
    for (String grain : new String[] {
             "theme.effects.grain", "theme.effects.grainAmount", "theme.effects.grainScale"})
      assertNotNull(catalog.control(grain), grain);
  }

  @Test
  void DataDefinedVisibilityTracksTheRealDurabilitySetting() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Control percent = catalog.control("armor.percent");
    UiCatalog.Control position = catalog.control("armor.durabilityPosition");
    SkinBindings bindings = new SkinBindings(settings);
    assertEquals("below", settings.armor.durabilityPosition);
    assertEquals("below|above", position.options);
    assertTrue(catalog.controls("armor").contains(position));
    assertTrue(catalog.editorControls("armor").contains(position));
    assertTrue(catalog.visible(percent, bindings, Collections.emptyMap()));
    assertTrue(catalog.visible(position, bindings, Collections.emptyMap()));
    settings.armor.durability = false;
    assertFalse(catalog.visible(percent, bindings, Collections.emptyMap()));
    assertFalse(catalog.visible(position, bindings, Collections.emptyMap()));
  }

  @Test
  void LazyFrameRefreshStillReadsLiveValuesFromEveryFetchedModuleList() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    settings.saturation.enabled = true;
    assertTrue(catalog.module("saturation").enabled, "module() must refresh its own live value");
    settings.saturation.enabled = false;
    UiCatalog.Module row = null;
    for (UiCatalog.Module module : catalog.modules(false))
      if (module.id.equals("saturation")) row = module;
    assertNotNull(row);
    assertFalse(row.enabled, "modules() must refresh every row it returns");
    settings.saturation.enabled = true;
    boolean enabled = false;
    for (UiCatalog.Module module : catalog.modules(false))
      if (module.id.equals("saturation")) enabled = module.enabled;
    assertTrue(enabled);
    settings.language = "en";
    UiCatalog.Module english = catalog.module("saturation");
    assertNotSame(row, english, "Language changes rebuild the lazy metadata");
    assertEquals(row.label, english.label, "Stable module names do not become translated aliases");
    assertNotEquals(row.summary, english.summary, "Module descriptions remain localized");
  }

  @Test
  void customSkyAndNoRenderWeatherExposeOrganizedWorldControls() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    assertTrue(catalog.modules(false).stream().noneMatch(m -> "worldTime".equals(m.id)));
    assertTrue(catalog.modules(true).stream().noneMatch(m -> "worldTime".equals(m.id)));
    UiCatalog.Module sky = catalog.module("customSky");
    assertNotNull(sky);
    assertEquals("world", sky.category);
    assertEquals("customSky.enabled", sky.bind);
    assertEquals("custom-sky", sky.icon);
    assertFalse(sky.enabled);
    assertTrue(catalog.controls("customSky").size() >= 14);
    UiCatalog.Control style = catalog.control("customSky.style");
    assertNotNull(style);
    assertEquals("vanilla|galaxy|aurora|sunset|anime|blood_moon|pastel", style.options);
    assertEquals(7, style.optionLabels.split("\\|", -1).length);
    UiCatalog.Control timeMode = catalog.control("customSky.timeMode");
    assertNotNull(timeMode);
    assertEquals("server|day|sunset|night|midnight|custom", timeMode.options);
    assertEquals(6, timeMode.optionLabels.split("\\|", -1).length);
    UiCatalog.Control noRain = catalog.control("noRender.weather");
    assertNotNull(noRain);
    assertEquals("toggle", noRain.kind);

    assertNotNull(catalog.control("crosshair.targetHighlight"));
    assertNotNull(catalog.control("crosshair.targetColor"));
    assertNotNull(catalog.control("crosshair.targetMobs"));
    assertNotNull(catalog.control("crosshair.targetHostileOnly"));
    assertNotNull(catalog.control("crosshair.targetRange"));
    assertNotNull(catalog.control("crosshair.targetShapeEnabled"));
    UiCatalog.Control targetShape = catalog.control("crosshair.targetShape");
    assertNotNull(targetShape);
    assertEquals(9, targetShape.optionLabels.split("\\|", -1).length);
    assertNotNull(catalog.control("crosshair.targetScale"));
    assertNotNull(catalog.control("crosshair.targetSize"));
    assertNotNull(catalog.control("crosshair.targetGap"));
    assertNotNull(catalog.control("crosshair.targetThickness"));
    assertNotNull(catalog.control("crosshair.targetOutline"));
    assertNotNull(catalog.control("crosshair.targetCenterDot"));
    assertNotNull(catalog.control("crosshair.targetRotation"));
    assertNotNull(catalog.control("crosshair.targetAnimate"));
    assertNotNull(catalog.control("crosshair.targetAnimSpeed"));

    UiCatalog.Module playerPing = catalog.module("playerPing");
    assertEquals("world", playerPing.category);
    assertEquals("playerPing.enabled", playerPing.bind);
    assertTrue(catalog.controls("playerPing").size() >= 10);
    assertNotNull(catalog.control("playerPing.inWorld"));
    assertNotNull(catalog.control("playerPing.inTab"));
    assertNotNull(catalog.control("playerPing.tabKeepBars"));
    assertEquals("playerPing.inTab", catalog.control("playerPing.tabKeepBars").condition);
    assertEquals("playerPing.inWorld", catalog.control("playerPing.style").condition);

    UiCatalog.Module safeCt = catalog.module("safeCtLeave");
    assertEquals("player", safeCt.category);
    assertEquals("safeCtLeave.enabled", safeCt.bind);
    assertEquals("safe-ct-leave", safeCt.icon);
    assertTrue(catalog.controls("safeCtLeave").size() >= 7);

    UiCatalog.Module betterChat = catalog.module("betterChat");
    assertEquals("hud", betterChat.category);
    assertTrue(betterChat.hud);
    assertEquals("betterChat.enabled", betterChat.bind);
    assertEquals("better-chat", betterChat.icon);
    assertTrue(catalog.controls("betterChat").size() >= 13);
    assertNotNull(catalog.control("betterChat.scale"));
    assertNull(catalog.control("betterChat.offsetY"));
  }

  @Test
  void newHolyWorldSoundEffectTapeMouseAndCursorFixModulesAndDynamicEventsTabWork() throws Exception {
    Settings settings = new Settings();
    settings.menu.animationSpeed = 1000000f;
    UiCatalog catalog = new UiCatalog(settings);

    assertTrue(catalog.modules(false).stream().noneMatch(m -> "trapTimer".equals(m.id)));
    assertTrue(catalog.modules(true).stream().noneMatch(m -> "trapTimer".equals(m.id)));

    for (String id : new String[] {"soundControl", "effectAlert", "tapeMouse"}) {
      UiCatalog.Module m = catalog.module(id);
      assertNotNull(m, id);
      assertEquals(id, m.id);
      assertEquals("player", m.category);
      assertFalse(m.hud);
      assertTrue(m.hasControls, id);
    }
    assertTrue(catalog.modules(false).stream().noneMatch(m -> "cursorFix".equals(m.id)));
    assertNotNull(catalog.control("tweaksy.cursorFix.enabled"));

    assertNotNull(catalog.control("safeCtLeave.dangerGuard"));
    assertNotNull(catalog.control("safeCtLeave.dangerRadius"));
    assertNotNull(catalog.control("hwhelper.events.enabled"));
    assertNotNull(catalog.control("hwhelper.timers.enabled"));
    assertNull(catalog.control("hwhelper.binds.enabled"));

    assertEquals(I18n.catalog("ru").keySet(), I18n.catalog("en").keySet());

    dev.nightclient.skin.SkinDocument doc = dev.nightclient.skin.DefaultSkin.document();
    new SkinValidator().validate(doc);
    SkinView view = new SkinView(doc, settings, new SkinViewTest.Actions());
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, 0);
    assertNull(view.elementBounds("nav-events"));

    settings.hwhelper.enabled = true;
    settings.hwhelper.events.enabled = true;
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .2);
    float[] evNav = view.elementBounds("nav-events");
    assertNotNull(evNav, "Events sidebar tab appears when enabled in HW Helper");
    assertTrue(view.mouseClicked(evNav[0] + 8, evNav[1] + 8, 0));
    view.mouseReleased(0, 0, 0);
    assertEquals("events", view.page());

    settings.hwhelper.events.enabled = false;
    view.renderMenu(new SkinViewTest.Sink(), 1000, 700, 0, 0, .4);
    assertEquals("player", view.page());
    assertNull(view.elementBounds("nav-events"));
  }

  @Test
  void optimizationCatalogExposesAllProvenControlsAndProfiles() {
    Settings settings = new Settings();
    UiCatalog catalog = new UiCatalog(settings);
    UiCatalog.Module opt = catalog.module("optimization");
    assertNotNull(opt);
    assertTrue(opt.hasControls);
    assertEquals("performance", settings.optimization.profile);

    UiCatalog.Control profile = catalog.control("optimization.profile");
    assertNotNull(profile);
    assertEquals("lossless|performance|competitive|custom", profile.options);
    assertEquals(4, profile.optionLabels.split("\\|", -1).length);

    String[] expected = new String[] {
        "optimization.profile",
        "optimization.autoCompatibility",
        "optimization.undergroundCaveCulling",
        "optimization.retainedTerrainCache",
        "optimization.leafCulling",
        "optimization.fastModelRendering",
        "optimization.nativeGpuInstancing",
        "optimization.nativePboUploads",
        "optimization.nativePacketCodec",
        "optimization.occlusionCulling",
        "optimization.staticBlockEntityBatching",
        "optimization.staticEntityBatching",
        "optimization.hologramTextCache",
        "optimization.emptyBlockEntitySkip",
        "optimization.signTextCulling",
        "optimization.signTextDistance",
        "optimization.itemClustering",
        "optimization.itemClusterLimit",
        "optimization.nametagCulling",
        "optimization.nametagDistance",
        "optimization.entityCulling",
        "optimization.entityDistance",
        "optimization.blockEntityCulling",
        "optimization.blockEntityDistance",
        "optimization.particleCulling",
        "optimization.particleDistance",
        "optimization.particleLimit",
        "optimization.fastParticlePhysics",
        "optimization.hudCache",
        "optimization.vanillaHudCache",
        "optimization.scoreboardCache",
    };
    assertEquals(expected.length, catalog.controls("optimization").size());
    for (String bind : expected) {
      UiCatalog.Control c = catalog.control(bind);
      assertNotNull(c, bind);
      assertNotEquals(c.labelKey, c.label, bind);
    }

    settings.optimization.applyProfile("lossless");
    assertTrue(settings.optimization.occlusionCulling);
    assertTrue(settings.optimization.staticBlockEntityBatching);
    assertTrue(settings.optimization.retainedTerrainCache);
    assertTrue(settings.optimization.hologramTextCache);
    assertFalse(settings.optimization.vanillaHudCache);
    assertFalse(settings.optimization.leafCulling);
    assertFalse(settings.optimization.itemClustering);

    settings.optimization.applyProfile("performance");
    assertTrue(settings.optimization.vanillaHudCache);
    assertTrue(settings.optimization.retainedTerrainCache);
    assertTrue(settings.optimization.hologramTextCache);
    assertFalse(settings.optimization.itemClustering);

    settings.optimization.applyProfile("competitive");
    assertTrue(settings.optimization.itemClustering);
    assertTrue(settings.optimization.leafCulling);
    assertTrue(settings.optimization.vanillaHudCache);
  }
}
