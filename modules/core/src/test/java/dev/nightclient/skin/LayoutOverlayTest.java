package dev.nightclient.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LayoutOverlayTest {
  @Test
  void layoutRulesOverrideInlineGeometryButKeepInlineVisuals() throws Exception {
    SkinDocument base = NcParser.parse(("menu {\n"
        + "  rect (id = \"box\") { x = 10px; width = 20px; background = \"#ff0000\" }\n"
        + "}\n")
            .getBytes(StandardCharsets.UTF_8));
    SkinDocument layout = NcParser.parse(("style id box { x = 30px }\n"
        + "style element menu { width = 100px }\n")
            .getBytes(StandardCharsets.UTF_8));

    SkinDocument composed = SkinDocument.layer(base, layout);
    SkinDocument.Node box = composed.menu().children.get(0);
    assertEquals("30px", composed.styles(box, "").get("x"));
    assertEquals("20px", composed.styles(box, "").get("width"));
    assertEquals("#ff0000", composed.styles(box, "").get("background"));
    assertEquals("100px", composed.styles(composed.menu(), "").get("width"));
  }
}
