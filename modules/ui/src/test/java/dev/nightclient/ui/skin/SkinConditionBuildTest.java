package dev.nightclient.ui.skin;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import dev.nightclient.skin.NcParser;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkinConditionBuildTest {
  @Test
  void aConditionUsingLocalVariablesStillCreatesItsLexicalScope() throws Exception {
    SkinView view = new SkinView(
        NcParser.parse(
            "menu { let gate='false'; text 'own'(if='{{= vars.gate}}') { let gate='true'; "
            + "height=12px } text 'hidden'(if='{{= vars.gate}}') { height=12px } }"),
        new Settings(), new SkinViewTest.Actions());
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    view.renderMenu(sink, 200, 100, 0, 0, 1);
    assertTrue(sink.strings().contains("own"));
    assertFalse(sink.strings().contains("hidden"));
  }
  @Test
  void aScopedConditionUsesItsOwnStateBeforeEvaluatingVisibility() throws Exception {
    SkinView view = new SkinView(
        NcParser.parse("state { visible=true } menu { text "
                        + "'local'(scope='condition',if='local.visible') { height=12px } }"),
        new Settings(), new SkinViewTest.Actions());
    view.host(new SkinView.Host() {
      public boolean action(String action) {
        return false;
      }
      public Map<String, Object> context() {
        return Collections.<String, Object>singletonMap(
            "local", Collections.singletonMap("visible", false));
      }
    });
    SkinViewTest.Sink sink = new SkinViewTest.Sink();
    view.renderMenu(sink, 200, 100, 0, 0, 1);
    assertTrue(sink.strings().contains("local"));
  }
}
