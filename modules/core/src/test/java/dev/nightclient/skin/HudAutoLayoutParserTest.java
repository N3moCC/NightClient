package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HudAutoLayoutParserTest {
  @Test
  void hudPlacementPolicySurvivesNativeNcParameterParsing() throws Exception {
    assertEquals("true",
        NcParser.parse("hud(auto-layout=true) {}").hud().attribute("auto-layout", "false"));
    assertEquals("false",
        NcParser.parse("hud(auto-layout=false) {}").hud().attribute("auto-layout", "true"));
    assertEquals("false", NcParser.parse("hud {}").hud().attribute("auto-layout", "false"));
  }
}
