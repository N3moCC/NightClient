package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.skin.DefaultSkin;
import dev.nightclient.skin.SkinDocument;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeaturePolicyUiTest {
  @BeforeEach
  void clearBefore() {
    FeaturePolicy.clear();
  }

  @AfterEach
  void clearAfter() {
    FeaturePolicy.clear();
  }

  @Test
  void blockedModulesAndSubFeaturesDisappearFromCatalogAndSearch() {
    Settings settings = new Settings();
    settings.language = "en";
    SkinDocument doc = DefaultSkin.document();
    UiCatalog catalog = new UiCatalog(settings);
    SkinBindings bindings = new SkinBindings(settings);
    ModuleSearchIndex index = new ModuleSearchIndex();

    assertNotNull(catalog.module("tapeMouse"));
    assertNotNull(catalog.module("minimap"));
    assertNotNull(catalog.control("minimap.showInvisiblePlayers"));
    assertTrue(catalog.controls("minimap").stream().anyMatch(c -> "minimap.showInvisiblePlayers".equals(c.bind)));

    index.ensureBuilt(doc, catalog, bindings, "en");
    assertTrue(index.matchesModule("tapeMouse", "tape"));
    assertTrue(index.matchesModule("minimap", "invisibles"));

    FeaturePolicy.setBlocked(Arrays.asList("tapeMouse", "minimap.invisibles", "waypoints", "armor"));

    assertNotEquals("tapeMouse", catalog.module("tapeMouse").id);
    assertTrue(catalog.allVisibleModules().stream().noneMatch(m -> "tapeMouse".equals(m.id)));
    assertTrue(catalog.modules(false).stream().noneMatch(m -> "tapeMouse".equals(m.id)));
    assertTrue(catalog.modules(true).stream().noneMatch(m -> "armor".equals(m.id)));
    assertNotNull(catalog.module("minimap"));
    assertNull(catalog.control("minimap.showInvisiblePlayers"));
    assertTrue(catalog.controls("minimap").stream().noneMatch(c -> "minimap.showInvisiblePlayers".equals(c.bind)));
    assertNotNull(catalog.control("minimap.showPlayers"));

    catalog.toggleModule("tapeMouse");
    assertFalse(settings.tapeMouse.enabled);

    index.ensureBuilt(doc, catalog, bindings, "en");
    assertFalse(index.matchesModule("tapeMouse", "tape"));
    ModuleSearchIndex.SearchPlan tapePlan = index.plan("tape", "utility", catalog.modules(false));
    assertFalse(tapePlan.matchesModule("tapeMouse"));

    ModuleSearchIndex.SearchPlan invisPlan = index.plan("invisibles", "hud", catalog.modules(true));
    assertFalse(invisPlan.isControlRevealed("minimap.showInvisiblePlayers"));

    FeaturePolicy.clear();
    assertNotNull(catalog.module("tapeMouse"));
    assertNotNull(catalog.control("minimap.showInvisiblePlayers"));
    index.ensureBuilt(doc, catalog, bindings, "en");
    assertTrue(index.matchesModule("tapeMouse", "tape"));
  }
}
