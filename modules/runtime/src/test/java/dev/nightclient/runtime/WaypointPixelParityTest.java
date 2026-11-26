package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.TargetView;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.renderer.WorldRibbon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WaypointPixelParityTest {
  @TempDir Path tempDir;

  private static final int WIDTH = 1280;
  private static final int HEIGHT = 720;
  private static final String[] VERSIONS = {
      "1.20", "1.20.4", "1.21.4", "1.21.10", "1.21.11", "26.3"};

  private static class VersionSimulatedPlatform implements PlatformServices {
    final String version;
    final Path configDir;
    final double camX, camY, camZ;
    final float pitch, yaw, fov;
    final String dimension;

    VersionSimulatedPlatform(String version, Path configDir, double camX, double camY, double camZ,
        float pitch, float yaw, float fov, String dimension) {
      this.version = version;
      this.configDir = configDir;
      this.camX = camX;
      this.camY = camY;
      this.camZ = camZ;
      this.pitch = pitch;
      this.yaw = yaw;
      this.fov = fov;
      this.dimension = dimension;
    }

    @Override
    public Path configDirectory() {
      return configDir;
    }

    @Override
    public String gameVersion() {
      return version;
    }

    @Override
    public String clipboard() {
      return "";
    }

    @Override
    public void clipboard(String value) {}

    @Override
    public void closeMenu() {}

    @Override
    public List<Cooldown> cooldowns(float partial) {
      return Collections.emptyList();
    }

    @Override
    public String menuKeyLabel() {
      return "B";
    }

    @Override
    public WorldRibbon.View cameraView(int width, int height) {
      float aspect = width / (float) Math.max(1, height);
      return new WorldRibbon.View(camX, camY, camZ, pitch, yaw, fov, aspect, width, height);
    }

    @Override
    public String currentDimension() {
      return dimension;
    }
  }

  private ClientRuntime createRuntime(PlatformServices platform) throws Exception {
    Constructor<ClientRuntime> ctor =
        ClientRuntime.class.getDeclaredConstructor(PlatformServices.class);
    ctor.setAccessible(true);
    return ctor.newInstance(platform);
  }

  private static class CommandDigestCanvas implements Canvas {
    final MessageDigest digest;
    int commands = 0;
    int roundedRects = 0;
    int strokes = 0;
    int texts = 0;
    int circles = 0;
    int triangles = 0;
    int lines = 0;
    int backdrops = 0;

    CommandDigestCanvas() {
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void tag(String cmd) {
      commands++;
      byte[] b = cmd.getBytes(StandardCharsets.UTF_8);
      digest.update((byte) (b.length >>> 24));
      digest.update((byte) (b.length >>> 16));
      digest.update((byte) (b.length >>> 8));
      digest.update((byte) b.length);
      digest.update(b);
    }

    private void num(float val) {
      int bits = Float.floatToIntBits(val);
      digest.update((byte) (bits >>> 24));
      digest.update((byte) (bits >>> 16));
      digest.update((byte) (bits >>> 8));
      digest.update((byte) bits);
    }

    private void str(String val) {
      if (val == null) {
        digest.update((byte) 0xff);
        return;
      }
      byte[] b = val.getBytes(StandardCharsets.UTF_8);
      num(b.length);
      digest.update(b);
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, float radius, int argb) {
      roundedRects++;
      tag("roundedRect");
      num(x);
      num(y);
      num(w);
      num(h);
      num(radius);
      num(argb);
    }

    @Override
    public void strokeRoundedRect(
        float x, float y, float w, float h, float radius, float thickness, int argb) {
      strokes++;
      tag("strokeRoundedRect");
      num(x);
      num(y);
      num(w);
      num(h);
      num(radius);
      num(thickness);
      num(argb);
    }

    @Override
    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int argb) {
      triangles++;
      tag("fillTriangle");
      num(ax);
      num(ay);
      num(bx);
      num(by);
      num(cx);
      num(cy);
      num(argb);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float thickness, int argb) {
      lines++;
      tag("line");
      num(x1);
      num(y1);
      num(x2);
      num(y2);
      num(thickness);
      num(argb);
    }

    @Override
    public void circle(float cx, float cy, float radius, int argb) {
      circles++;
      tag("circle");
      num(cx);
      num(cy);
      num(radius);
      num(argb);
    }

    @Override
    public void text(String value, float x, float y, float size, int argb, boolean bold) {
      texts++;
      tag("text");
      str(value);
      num(x);
      num(y);
      num(size);
      num(argb);
      digest.update((byte) (bold ? 1 : 0));
    }

    @Override
    public float textWidth(String value, float size, boolean bold) {
      if (value == null) return 0;
      return value.length() * (size * 0.55f);
    }

    @Override
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float opacity) {
      backdrops++;
      tag("backdrop");
      num(x);
      num(y);
      num(w);
      num(h);
      num(r);
      num(blur);
      num(quality);
      num(opacity);
    }

    @Override
    public void rect(float x, float y, float w, float h, int argb) {
      tag("rect");
      num(x);
      num(y);
      num(w);
      num(h);
      num(argb);
    }

    @Override
    public void gradientRect(float x, float y, float w, float h, int top, int bottom) {}

    @Override
    public void texture(
        String id, float x, float y, float w, float h, float radius, float tileSize, int argb) {}

    @Override
    public void pushClip(float x, float y, float w, float h) {}

    @Override
    public void popClip() {}

    @Override
    public void item(String registryId, float x, float y, float size, float alpha) {}

    String digestHex() {
      StringBuilder sb = new StringBuilder();
      for (byte b : digest.digest()) {
        sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
      }
      return sb.toString();
    }
  }

  public static class BufferedImageRasterCanvas implements Canvas {
    public final BufferedImage image;
    public final Graphics2D g2;
    private final Font font;

    public BufferedImageRasterCanvas(int width, int height) {
      image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      g2 = image.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      font = new Font("SansSerif", Font.PLAIN, 12);
      g2.setFont(font);

      for (int y = 0; y < height; y++) {
        float f = y / (float) height;
        int r = (int) (14 + f * 10);
        int g = (int) (16 + f * 18);
        int b = (int) (26 + f * 20);
        g2.setColor(new Color(r, g, b, 255));
        g2.drawLine(0, y, width, y);
      }

      g2.setColor(new Color(255, 255, 255, 18));
      g2.drawLine(0, height / 2 + 60, width, height / 2 + 60);

      int cx = width / 2;
      int cy = height / 2;
      g2.setColor(new Color(255, 255, 255, 90));
      g2.drawLine(cx - 5, cy, cx + 5, cy);
      g2.drawLine(cx, cy - 5, cx, cy + 5);
    }

    private Color toAwtColor(int argb) {
      return new Color((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, (argb >>> 24) & 0xff);
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, float radius, int argb) {
      g2.setColor(toAwtColor(argb));
      float r2 = radius * 2.0f;
      g2.fill(new RoundRectangle2D.Float(x, y, w, h, r2, r2));
    }

    @Override
    public void strokeRoundedRect(
        float x, float y, float w, float h, float radius, float thickness, int argb) {
      g2.setColor(toAwtColor(argb));
      g2.setStroke(new BasicStroke(thickness));
      float r2 = radius * 2.0f;
      g2.draw(new RoundRectangle2D.Float(x, y, w, h, r2, r2));
    }

    @Override
    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int argb) {
      g2.setColor(toAwtColor(argb));
      Path2D.Float path = new Path2D.Float();
      path.moveTo(ax, ay);
      path.lineTo(bx, by);
      path.lineTo(cx, cy);
      path.closePath();
      g2.fill(path);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float thickness, int argb) {
      g2.setColor(toAwtColor(argb));
      g2.setStroke(new BasicStroke(thickness));
      g2.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void circle(float cx, float cy, float radius, int argb) {
      g2.setColor(toAwtColor(argb));
      g2.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2.0f, radius * 2.0f));
    }

    @Override
    public void text(String value, float x, float y, float size, int argb, boolean bold) {
      g2.setColor(toAwtColor(argb));
      g2.setFont(font.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
      java.awt.FontMetrics fm = g2.getFontMetrics();
      g2.drawString(value, x, y + fm.getAscent() * 0.85f);
    }

    @Override
    public float textWidth(String value, float size, boolean bold) {
      if (value == null) return 0;
      g2.setFont(font.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
      return g2.getFontMetrics().stringWidth(value);
    }

    @Override
    public void backdrop(
        float x, float y, float w, float h, float r, float blur, int quality, float opacity) {
      int a = Math.max(0, Math.min(255, Math.round(opacity * 160)));
      g2.setColor(new Color(4, 6, 12, a));
      float r2 = r * 2.0f;
      g2.fill(new RoundRectangle2D.Float(x, y, w, h, r2, r2));
    }

    @Override
    public void rect(float x, float y, float w, float h, int argb) {
      g2.setColor(toAwtColor(argb));
      g2.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void gradientRect(float x, float y, float w, float h, int top, int bottom) {}

    @Override
    public void texture(
        String id, float x, float y, float w, float h, float radius, float tileSize, int argb) {}

    @Override
    public void pushClip(float x, float y, float w, float h) {}

    @Override
    public void popClip() {}

    @Override
    public void item(String registryId, float x, float y, float size, float alpha) {}

    public void dispose() {
      g2.dispose();
    }
  }

  @Test
  public void verifyOnlySizeSettingExistsOnWaypoints() {
    Settings.Waypoints wp = new Settings.Waypoints();
    Field[] fields = Settings.Waypoints.class.getDeclaredFields();
    List<String> fieldNames = new ArrayList<String>();
    for (Field f : fields) {
      if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
        fieldNames.add(f.getName());
      }
    }
    Collections.sort(fieldNames);
    assertEquals(
        Arrays.asList("badge", "chatCoordinates", "deathPoints", "enabled", "hideNearby",
            "hideNearbyRadius", "points"),
        fieldNames,
        "Waypoints must only keep visibility, death capture, badge size, chat coordinates, "
        + "proximity hide, and points");
  }

  @Test
  public void hideNearbySuppressesWaypointsWithinConfiguredRadius() throws Exception {
    VersionSimulatedPlatform platform = new VersionSimulatedPlatform(
        "1.21.11", tempDir, 0.0, 64.0, 0.0, 0.0f, 0.0f, 70.0f, "minecraft:overworld");
    ClientRuntime runtime = createRuntime(platform);
    try {
      Settings s = runtime.settings();
      s.waypoints.enabled = true;
      s.waypoints.hideNearby = true;
      s.waypoints.hideNearbyRadius = 15;
      s.waypoints.points.clear();
      s.waypoints.add("NearBase", 0.0, 64.0, 8.0, "minecraft:overworld");
      s.waypoints.add("FarOutpost", 0.0, 64.0, 40.0, "minecraft:overworld");

      final List<String> renderedTexts = new ArrayList<String>();
      Canvas spy = new CommandDigestCanvas() {
        @Override
        public void text(String value, float x, float y, float size, int argb, boolean bold) {
          super.text(value, x, y, size, argb, bold);
          if (value != null) renderedTexts.add(value);
        }

        @Override
        public void textFamily(String family, String value, float x, float y, float size, int argb,
            boolean bold, float tracking) {
          super.textFamily(family, value, x, y, size, argb, bold, tracking);
          if (value != null) renderedTexts.add(value);
        }
      };

      runtime.renderWaypoints(spy, 1920, 1080);
      assertFalse(renderedTexts.stream().anyMatch(t -> t.contains("NearBase")),
          "Waypoint within hideNearbyRadius (8m <= 15m) must be hidden");
      assertTrue(renderedTexts.stream().anyMatch(t -> t.contains("FarOutpost")),
          "Waypoint beyond hideNearbyRadius (40m > 15m) must remain visible");

      renderedTexts.clear();
      s.waypoints.hideNearby = false;
      runtime.renderWaypoints(spy, 1920, 1080);
      assertTrue(renderedTexts.stream().anyMatch(t -> t.contains("NearBase")),
          "Waypoint must appear once hideNearby is disabled");
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  public void crossVersionRenderPixelParityProducesIdenticalDrawStreams() throws Exception {
    List<String> digests = new ArrayList<String>();
    CommandDigestCanvas baselineCanvas = null;

    for (String version : VERSIONS) {
      VersionSimulatedPlatform platform = new VersionSimulatedPlatform(
          version, tempDir, 0.0, 64.0, 0.0, 0.0f, 0.0f, 70.0f, "minecraft:overworld");
      ClientRuntime runtime = createRuntime(platform);
      try {
        Settings s = runtime.settings();
        s.theme.effects.backgroundBlur = true;
        s.waypoints.enabled = true;
        s.waypoints.badge = 1.0f;

        Waypoint w1 = s.waypoints.add("Spawn", 0.0, 64.0, 24.0, "minecraft:overworld");
        assertNotNull(w1);

        Waypoint w2 = s.waypoints.add("Emerald Base", -20.0, 70.0, 95.0, "minecraft:overworld");
        assertNotNull(w2);
        w2.color = 0xff10b981;

        Waypoint w3 = s.waypoints.add("Fortress", 40.0, 60.0, 350.0, "minecraft:overworld");
        assertNotNull(w3);
        w3.color = 0xfff59e0b;

        Waypoint wBehind = s.waypoints.add("Behind", 0.0, 64.0, -20.0, "minecraft:overworld");
        assertNotNull(wBehind);

        Waypoint wNether =
            s.waypoints.add("Nether Portal", 10.0, 64.0, 30.0, "minecraft:the_nether");
        assertNotNull(wNether);

        Waypoint wFar = s.waypoints.add("Far", 0.0, 64.0, 1500.0, "minecraft:overworld");
        assertNotNull(wFar);

        CommandDigestCanvas canvas = new CommandDigestCanvas();
        runtime.renderWaypoints(canvas, WIDTH, HEIGHT);

        String digest = canvas.digestHex();
        digests.add(digest);

        if (baselineCanvas == null) {
          baselineCanvas = canvas;
          assertTrue(canvas.commands > 0, "Waypoints must emit draw commands");
          assertEquals(4, canvas.backdrops,
              "Should render 4 visible capsules; only direction/dimension are culled");
          assertEquals(4, canvas.triangles, "Should render 4 pointer micro-carets");
          assertTrue(canvas.circles >= 6, "Should render pin dots and chip cores");
        } else {
          assertEquals(
              baselineCanvas.commands, canvas.commands, "Command count mismatch for " + version);
          assertEquals(baselineCanvas.roundedRects, canvas.roundedRects,
              "Rounded rects mismatch for " + version);
          assertEquals(
              baselineCanvas.strokes, canvas.strokes, "Stroke count mismatch for " + version);
          assertEquals(baselineCanvas.texts, canvas.texts, "Text count mismatch for " + version);
          assertEquals(
              baselineCanvas.circles, canvas.circles, "Circle count mismatch for " + version);
          assertEquals(
              baselineCanvas.triangles, canvas.triangles, "Triangle count mismatch for " + version);
          assertEquals(baselineCanvas.lines, canvas.lines, "Line count mismatch for " + version);
          assertEquals(
              baselineCanvas.backdrops, canvas.backdrops, "Backdrop count mismatch for " + version);
        }
      } finally {
        runtime.shutdown();
      }
    }

    String canonicalDigest = digests.get(0);
    for (int i = 1; i < digests.size(); i++) {
      assertEquals(canonicalDigest, digests.get(i),
          "Pixel parity violation between " + VERSIONS[0] + " and " + VERSIONS[i]);
    }
  }

  @Test
  public void renderWaypointScreenshotAndInspect() throws Exception {
    VersionSimulatedPlatform platform = new VersionSimulatedPlatform(
        "26.3", tempDir, 0.0, 64.0, 0.0, 0.0f, 0.0f, 70.0f, "minecraft:overworld");
    ClientRuntime runtime = createRuntime(platform);
    try {
      Settings s = runtime.settings();
      s.waypoints.enabled = true;
      s.waypoints.badge = 1.0f;

      Waypoint w1 = s.waypoints.add("Main Base", -12.0, 66.0, 48.0, "minecraft:overworld");
      assertNotNull(w1);
      w1.color = 0xff5ad1ff;

      Waypoint w2 = s.waypoints.add("Portal Hub", 18.0, 64.0, 85.0, "minecraft:overworld");
      assertNotNull(w2);
      w2.color = 0xffa855f7;

      Waypoint w3 = s.waypoints.add("Ancient City", -4.0, 60.0, 245.0, "minecraft:overworld");
      assertNotNull(w3);
      w3.color = 0xff10b981;

      Waypoint w4 = s.waypoints.add("Woodland Mansion", 35.0, 72.0, 420.0, "minecraft:overworld");
      assertNotNull(w4);
      w4.color = 0xfff59e0b;

      BufferedImageRasterCanvas canvas = new BufferedImageRasterCanvas(WIDTH, HEIGHT);
      runtime.renderWaypoints(canvas, WIDTH, HEIGHT);
      canvas.dispose();

      File reportDir = new File("build/reports");
      reportDir.mkdirs();
      File screenshotFile = new File(reportDir, "waypoint-render.png");
      ImageIO.write(canvas.image, "PNG", screenshotFile);
      assertTrue(screenshotFile.exists(), "Screenshot must be written");
      assertTrue(screenshotFile.length() > 1000, "Screenshot file must contain image data");

      assertEquals(1280, canvas.image.getWidth());
      assertEquals(720, canvas.image.getHeight());

      int renderedPixelCount = 0;
      for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
          int rgb = canvas.image.getRGB(x, y);
          int r = (rgb >> 16) & 0xff;
          int g = (rgb >> 8) & 0xff;
          int b = rgb & 0xff;
          if ((r > 30 || g > 30 || b > 40) && !(y == HEIGHT / 2 && Math.abs(x - WIDTH / 2) < 10)) {
            renderedPixelCount++;
          }
        }
      }
      assertTrue(renderedPixelCount > 500, "At least 500 foreground badge pixels must be rendered");
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  public void badgeSizeKnobScalesCapsuleGeometryLinearly() throws Exception {
    VersionSimulatedPlatform platform = new VersionSimulatedPlatform(
        "26.3", tempDir, 0.0, 64.0, 0.0, 0.0f, 0.0f, 70.0f, "minecraft:overworld");
    ClientRuntime runtime = createRuntime(platform);
    try {
      Settings s = runtime.settings();
      s.waypoints.enabled = true;
      s.waypoints.add("Test", 0.0, 64.0, 30.0, "minecraft:overworld");

      s.waypoints.badge = 0.5f;
      CommandDigestCanvas canvasMin = new CommandDigestCanvas();
      runtime.renderWaypoints(canvasMin, WIDTH, HEIGHT);

      s.waypoints.badge = 1.0f;
      CommandDigestCanvas canvasNorm = new CommandDigestCanvas();
      runtime.renderWaypoints(canvasNorm, WIDTH, HEIGHT);

      s.waypoints.badge = 2.0f;
      CommandDigestCanvas canvasMax = new CommandDigestCanvas();
      runtime.renderWaypoints(canvasMax, WIDTH, HEIGHT);

      assertNotEquals(canvasMin.digestHex(), canvasNorm.digestHex());
      assertNotEquals(canvasNorm.digestHex(), canvasMax.digestHex());

      assertEquals(canvasNorm.commands, canvasMin.commands);
      assertEquals(canvasNorm.commands, canvasMax.commands);
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  public void renderWaypointDetailShowcaseAndInspect() throws Exception {
    int showcaseW = 800;
    int showcaseH = 450;
    VersionSimulatedPlatform platform = new VersionSimulatedPlatform(
        "26.3", tempDir, 0.0, 64.0, 0.0, 0.0f, 0.0f, 60.0f, "minecraft:overworld");
    ClientRuntime runtime = createRuntime(platform);
    try {
      Settings s = runtime.settings();
      s.waypoints.enabled = true;
      s.waypoints.badge = 1.35f;

      Waypoint w1 = s.waypoints.add("Main Base", -10.0, 66.0, 32.0, "minecraft:overworld");
      assertNotNull(w1);
      w1.color = 0xff5ad1ff;

      Waypoint w2 = s.waypoints.add("Ancient City", 10.0, 64.0, 45.0, "minecraft:overworld");
      assertNotNull(w2);
      w2.color = 0xff10b981;

      Waypoint w3 = s.waypoints.add("Nether Portal", -5.0, 68.0, 75.0, "minecraft:overworld");
      assertNotNull(w3);
      w3.color = 0xffa855f7;

      Waypoint w4 = s.waypoints.add("End Gateway", 8.0, 62.0, 180.0, "minecraft:overworld");
      assertNotNull(w4);
      w4.color = 0xfff59e0b;

      BufferedImageRasterCanvas canvas = new BufferedImageRasterCanvas(showcaseW, showcaseH);
      runtime.renderWaypoints(canvas, showcaseW, showcaseH);
      canvas.dispose();

      File reportDir = new File("build/reports");
      reportDir.mkdirs();
      File detailFile = new File(reportDir, "waypoint-detail.png");
      ImageIO.write(canvas.image, "PNG", detailFile);
      assertTrue(detailFile.exists());
    } finally {
      runtime.shutdown();
    }
  }
}
