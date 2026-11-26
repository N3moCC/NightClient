package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.config.Settings;
import dev.nightclient.renderer.WorldRibbon;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageIndicatorsTest {
  private static final class Draws {
    final List<String> labels = new ArrayList<String>();
    final List<Integer> colors = new ArrayList<Integer>();
    int lines;
    Canvas canvas() {
      return (Canvas) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {Canvas.class}, (p, m, a) -> {
            if (m.getName().equals("textFamily")) {
              labels.add((String) a[1]);
              colors.add((Integer) a[5]);
            }
            if (m.getName().equals("line")) lines++;
            if (m.getReturnType() == float.class) {
              if (m.getName().startsWith("textWidth")) return 15f;
              return 0f;
            }
            if (m.getReturnType() == boolean.class) return false;
            return null;
          });
    }
  }
  @Test
  void waitsForActualHealthLossIncludingAbsorptionAndNeverInventsHitDamage() {
    DamageIndicators d = new DamageIndicators();
    d.attack(7, 24, true, 1);
    assertTrue(d.sample(7, 24, 0, 64, 8, 1.1));
    assertEquals(0, d.splashCount(), "An attack animation alone is not proof of damage");
    assertFalse(d.sample(7, 19.5f, 0, 64, 8, 1.2));
    assertEquals(1, d.splashCount());
    assertFalse(d.sample(7, 15, 0, 64, 8, 1.3));
    assertEquals(1, d.splashCount(), "One health update is emitted once");
    Settings s = new Settings();
    s.damageSplash.enabled = true;
    s.damageSplash.scale = 1;
    s.menu.hudScale = 1;
    Draws out = new Draws();
    d.render(out.canvas(), s, new WorldRibbon.View(0, 64, 0, 0, 0, 70, 16f / 9, 1280, 720), 1280,
        720, 1.3);
    assertEquals(java.util.Arrays.asList("4.5", "4.5"), out.labels);
    assertEquals(0xffffd75f, (int) out.colors.get(1), "Critical damage uses its critical visual");
    assertTrue(out.lines > 0, "Icon option submits actual vector geometry");
    assertFalse(d.visible(3, s.damageSplash.duration));
    assertEquals(0, d.splashCount());
  }
  @Test
  void expiredMissesAndWorldClearsCannotAppearAsFutureDamage() {
    DamageIndicators d = new DamageIndicators();
    d.attack(7, 20, false, 0);
    assertFalse(d.sample(7, 10, 0, 64, 8, 2));
    assertEquals(0, d.splashCount());
    d.attack(8, 20, false, 3);
    d.sample(8, 16, 0, 64, 8, 3.1);
    d.clear();
    assertEquals(0, d.pendingCount());
    assertEquals(0, d.splashCount());
    assertFalse(d.sample(8, 10, 0, 64, 8, 3.2));
    for (int i = 0; i < 100; i++) d.attack(i, 20, false, 4);
    assertEquals(64, d.pendingCount(), "Pending entities are bounded during crowded combat");
  }
  @Test
  void customColorsIconsCritToggleAndDisabledStateUseActualSettings() {
    DamageIndicators d = new DamageIndicators();
    d.attack(1, 20, true, 1);
    d.sample(1, 12, 0, 64, 8, 1.05);
    Settings s = new Settings();
    s.damageSplash.enabled = true;
    s.damageSplash.icons = false;
    s.damageSplash.crits = false;
    s.damageSplash.colorMode = "custom";
    s.damageSplash.color = 0xff2277bb;
    Draws out = new Draws();
    d.render(out.canvas(), s, new WorldRibbon.View(0, 64, 0, 0, 0, 70, 16f / 9, 1280, 720), 1280,
        720, 1.1);
    assertEquals("8", out.labels.get(1));
    assertEquals(0xff2277bb, (int) out.colors.get(1));
    assertEquals(0, out.lines);
    s.damageSplash.enabled = false;
    out = new Draws();
    d.render(out.canvas(), s, null, 1280, 720, 1.2);
    assertEquals(0, d.splashCount());
    assertTrue(out.labels.isEmpty());
  }
}
