package dev.nightclient.platform;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;

public final class LegacyUiRenderer {
  private static int program, vao, vbo, vboCapacityBytes, font, screenUniform, texturedUniform,
      itemAlphaUniform;
  private static int itemBefore, itemAfter, itemWidth, itemHeight;
  private static volatile boolean invalidated;
  private static FloatBuffer upload = BufferUtils.createFloatBuffer(8192);
  private static State previous;
  private static int logicalWidth, logicalHeight;
  private static int explicitTarget = -1, targetWidth, targetHeight;
  private static final Map<String, Integer> materialTextures = new HashMap<String, Integer>();

  private LegacyUiRenderer() {}

  public static void prewarm() {
    if (program != 0 && !invalidated) return;
    State before = new State();
    try {
      if (invalidated) {
        dispose();
        invalidated = false;
      }
      if (program == 0) create();
    } finally {
      before.restore();
    }
  }

  public static void begin(int width, int height) {
    begin(width, height, -1, 0, 0);
  }

  public static void begin(
      int width, int height, int framebuffer, int pixelWidth, int pixelHeight) {
    previous = new State();
    logicalWidth = Math.max(1, width);
    logicalHeight = Math.max(1, height);
    explicitTarget = framebuffer;
    targetWidth = Math.max(1, pixelWidth);
    targetHeight = Math.max(1, pixelHeight);
    try {
      if (invalidated) {
        dispose();
        invalidated = false;
      }
      if (program == 0) create();
      bind();
    } catch (RuntimeException exception) {
      previous.restore();
      previous = null;
      throw exception;
    }
  }

  private static int drawTarget() {
    return explicitTarget < 0 ? previous.draw : explicitTarget;
  }

  private static int viewportX() {
    return explicitTarget < 0 ? previous.viewport[0] : 0;
  }

  private static int viewportY() {
    return explicitTarget < 0 ? previous.viewport[1] : 0;
  }

  private static int viewportWidth() {
    return explicitTarget < 0 ? previous.viewport[2] : targetWidth;
  }

  private static int viewportHeight() {
    return explicitTarget < 0 ? previous.viewport[3] : targetHeight;
  }

  private static void bindTarget() {
    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget());
    GL11.glViewport(viewportX(), viewportY(), viewportWidth(), viewportHeight());
  }

  private static void bind() {
    bindTarget();
    GL20.glUseProgram(program);
    GL30.glBindVertexArray(vao);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    GL11.glEnable(GL11.GL_BLEND);
    GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    GL14.glBlendFuncSeparate(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glDepthMask(false);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glDisable(GL11.GL_STENCIL_TEST);
    GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);

    if (previous.alphaTestSupported) GL11.glDisable(GL11.GL_ALPHA_TEST);
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    GL11.glColorMask(true, true, true, true);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
    if (previous.samplerSupported) GL33.glBindSampler(0, 0);
    GL20.glUniform2f(screenUniform, logicalWidth, logicalHeight);
  }

  public static void suspend(float[] rect) {
    if (previous != null) {
      previous.restore();
      bindTarget();
      clip(rect);
    }
  }

  public static void resume() {
    if (previous != null) {
      previous = new State(previous);
      State.enabled(GL11.GL_SCISSOR_TEST, previous.scissor);
      GL11.glScissor(previous.scissorBox[0], previous.scissorBox[1], previous.scissorBox[2],
          previous.scissorBox[3]);
    } else {
      previous = new State();
    }
    bind();
  }

  public static void end() {
    if (previous != null) {
      previous.restore();
      previous = null;
    }
  }

  public static void drawInvertedRect(
      float x, float y, float w, float h, float opacity, float[] rect) {
    capacity(48);
    inverseVertex(x, y, opacity);
    inverseVertex(x + w, y, opacity);
    inverseVertex(x + w, y + h, opacity);
    inverseVertex(x, y, opacity);
    inverseVertex(x + w, y + h, opacity);
    inverseVertex(x, y + h, opacity);
    upload.flip();
    GL14.glBlendFuncSeparate(
        GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
    try {
      submit(6, false, rect);
    } finally {
      GL14.glBlendFuncSeparate(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
  }

  private static void inverseVertex(float x, float y, float opacity) {
    upload.put(x).put(y).put(0).put(0).put(opacity).put(opacity).put(opacity).put(opacity);
  }

  public static ItemSnapshot captureItem(float x, float y, float size, float alpha) {
    if (alpha >= 1 || previous == null) return null;
    float sx = viewportWidth() / (float) logicalWidth,
          sy = viewportHeight() / (float) logicalHeight;

    float margin = size;
    int left = Math.max(viewportX(), (int) Math.floor(viewportX() + (x - margin) * sx));
    int right = Math.min(
        viewportX() + viewportWidth(), (int) Math.ceil(viewportX() + (x + size + margin) * sx));
    int bottom = Math.max(
        viewportY(), (int) Math.floor(viewportY() + viewportHeight() - (y + size + margin) * sy));
    int top = Math.min(viewportY() + viewportHeight(),
        (int) Math.ceil(viewportY() + viewportHeight() - (y - margin) * sy));
    if (right <= left || top <= bottom) return null;
    State beforeCopy = new State();
    try {
      ensureItemTextures(right - left, top - bottom);
      GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawTarget());
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemBefore);
      GL11.glCopyTexSubImage2D(
          GL11.GL_TEXTURE_2D, 0, 0, 0, left, bottom, right - left, top - bottom);
    } finally {
      beforeCopy.restore();
    }
    return new ItemSnapshot(left, bottom, right - left, top - bottom, (left - viewportX()) / sx,
        (viewportY() + viewportHeight() - top) / sy, (right - left) / sx, (top - bottom) / sy,
        Math.max(0, Math.min(1, alpha)));
  }

  private static void ensureItemTextures(int width, int height) {
    if (itemBefore != 0 && width <= itemWidth && height <= itemHeight) return;
    itemWidth = Math.max(itemWidth, width);
    itemHeight = Math.max(itemHeight, height);
    if (itemBefore == 0) {
      itemBefore = GL11.glGenTextures();
      itemAfter = GL11.glGenTextures();
    }
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    PixelUnpackState unpack = new PixelUnpackState();
    try {
      for (int texture : new int[] {itemBefore, itemAfter}) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, itemWidth, itemHeight, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
      }
    } finally {
      unpack.restore();
    }
  }

  public static void compositeItem(ItemSnapshot image, float[] rect) {
    if (image == null) return;
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawTarget());
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAfter);
    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, image.sourceX, image.sourceY,
        image.sourceWidth, image.sourceHeight);
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous.read);
    float u = image.sourceWidth / (float) itemWidth, v = image.sourceHeight / (float) itemHeight;
    capacity(48);
    itemVertex(image.x, image.y, 0, v);
    itemVertex(image.x + image.width, image.y, u, v);
    itemVertex(image.x + image.width, image.y + image.height, u, 0);
    itemVertex(image.x, image.y, 0, v);
    itemVertex(image.x + image.width, image.y + image.height, u, 0);
    itemVertex(image.x, image.y + image.height, 0, 0);
    upload.flip();
    clip(rect);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemBefore);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAfter);
    if (previous.samplerSupported) GL33.glBindSampler(1, 0);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL20.glUniform1i(texturedUniform, 2);
    GL20.glUniform1f(itemAlphaUniform, image.alpha);
    GL11.glDisable(GL11.GL_BLEND);
    try {
      uploadVbo();
      GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    } finally {
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
      GL20.glUniform1i(texturedUniform, 0);
    }
  }

  private static void itemVertex(float x, float y, float u, float v) {
    upload.put(x).put(y).put(u).put(v).put(1).put(1).put(1).put(1);
  }

  public static final class ItemSnapshot {
    final int sourceX, sourceY, sourceWidth, sourceHeight;
    final float x, y, width, height, alpha;

    ItemSnapshot(int sourceX, int sourceY, int sourceWidth, int sourceHeight, float x, float y,
        float width, float height, float alpha) {
      this.sourceX = sourceX;
      this.sourceY = sourceY;
      this.sourceWidth = sourceWidth;
      this.sourceHeight = sourceHeight;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.alpha = alpha;
    }
  }

  private static void clip(float[] rect) {
    if (rect == null) {
      State.enabled(GL11.GL_SCISSOR_TEST, previous.scissor);
      GL11.glScissor(previous.scissorBox[0], previous.scissorBox[1], previous.scissorBox[2],
          previous.scissorBox[3]);
      return;
    }
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    float sx = viewportWidth() / (float) logicalWidth,
          sy = viewportHeight() / (float) logicalHeight;
    int left = (int) Math.floor(rect[0] * sx), right = (int) Math.ceil(rect[2] * sx);
    int top = (int) Math.floor(rect[1] * sy), bottom = (int) Math.ceil(rect[3] * sy);
    int x = viewportX() + left, y = viewportY() + viewportHeight() - bottom;
    int x1 = x + Math.max(0, right - left), y1 = y + Math.max(0, bottom - top);
    if (previous.scissor) {
      x = Math.max(x, previous.scissorBox[0]);
      y = Math.max(y, previous.scissorBox[1]);
      x1 = Math.min(x1, previous.scissorBox[0] + previous.scissorBox[2]);
      y1 = Math.min(y1, previous.scissorBox[1] + previous.scissorBox[3]);
    }
    GL11.glScissor(x, y, Math.max(0, x1 - x), Math.max(0, y1 - y));
  }

  private static void capacity(int floats) {
    if (upload.capacity() < floats)
      upload = BufferUtils.createFloatBuffer(Integer.highestOneBit(floats - 1) << 1);
    upload.clear();
  }

  private static void uploadVbo() {
    int bytes = upload.remaining() * 4;
    if (bytes <= 0) return;
    if (bytes > vboCapacityBytes) {
      int next = Math.max(bytes, Math.max(32768, vboCapacityBytes * 2));
      vboCapacityBytes = next;
    }
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vboCapacityBytes, GL15.GL_STREAM_DRAW);
    GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, upload);
  }

  public static void draw(float[] vertices, int length, float[] rect) {
    capacity(length / 6 * 8);
    for (int i = 0; i < length; i += 6) {
      upload.put(vertices[i]).put(vertices[i + 1]).put(0).put(0);
      upload.put(vertices, i + 2, 4);
    }
    upload.flip();
    submit(length / 6, false, rect);
  }

  public static void drawRounded(float[] vertices, int length, float[] rect) {
    if (length == 0) return;
    capacity(length);
    upload.put(vertices, 0, length).flip();
    clip(rect);
    GL20.glUniform1i(texturedUniform, 4);
    try {
      uploadVbo();
      GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, length / 8);
    } finally {
      GL20.glUniform1i(texturedUniform, 0);
    }
  }

  public static void drawText(float[] vertices, int length, float[] rect) {
    capacity(length);
    upload.put(vertices, 0, length).flip();
    submit(length / 8, true, rect);
  }

  public static void drawText(float[] vertices, int length, float[] rect, String textureId) {
    if ("nightclient:textures/gui/font.png".equals(textureId)) {
      drawText(vertices, length, rect);
      return;
    }
    int texture = loadTexture(textureId, true);
    capacity(length);
    upload.put(vertices, 0, length).flip();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    try {
      submit(length / 8, true, rect);
    } finally {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
    }
  }

  public static void drawTexture(float[] vertices, int texture, float[] rect) {
    if (vertices.length == 0) return;
    capacity(vertices.length);
    upload.put(vertices).flip();
    clip(rect);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    GL20.glUniform1i(texturedUniform, 3);
    try {
      uploadVbo();
      GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices.length / 8);
    } finally {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
      GL20.glUniform1i(texturedUniform, 0);
    }
  }

  public static void drawTextureNearest(float[] vertices, int texture, float[] rect) {
    if (vertices.length == 0) return;
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    int previousMin = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
    int previousMag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    try {
      drawTexture(vertices, texture, rect);
    } finally {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMin);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMag);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
    }
  }

  public static int materialTexture(String id) {
    return loadTexture(id, false);
  }

  private static int loadTexture(String id, boolean clamp) {
    String key = (clamp ? "font:" : "material:") + id;
    Integer cached = materialTextures.get(key);
    if (cached != null) return cached;
    int split = id.indexOf(':');
    if (split <= 0 || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
      throw new IllegalArgumentException("Invalid material resource " + id);
    String resource = "/assets/" + id.substring(0, split) + "/" + id.substring(split + 1);
    try (InputStream stream = LegacyUiRenderer.class.getResourceAsStream(resource)) {
      if (stream == null) throw new IllegalStateException("Missing material texture " + id);
      BufferedImage image = ImageIO.read(stream);
      if (image == null) throw new IllegalStateException("Invalid material texture " + id);
      ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
      for (int y = 0; y < image.getHeight(); y++)
        for (int x = 0; x < image.getWidth(); x++) {
          int c = image.getRGB(x, y);
          pixels.put((byte) (c >>> 16)).put((byte) (c >>> 8)).put((byte) c).put((byte) (c >>> 24));
        }
      pixels.flip();
      int texture = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
      PixelUnpackState unpack = new PixelUnpackState();
      try {
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.getWidth(), image.getHeight(),
            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
            clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
            clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
      } catch (RuntimeException error) {
        GL11.glDeleteTextures(texture);
        throw error;
      } finally {
        unpack.restore();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
      }
      materialTextures.put(key, texture);
      return texture;
    } catch (java.io.IOException error) {
      throw new IllegalStateException("Cannot decode material " + id, error);
    }
  }

  private static void submit(int count, boolean textured, float[] rect) {
    clip(rect);
    GL20.glUniform1i(texturedUniform, textured ? 1 : 0);
    uploadVbo();
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
  }

  private static int shader(int kind, String source) {
    int id = GL20.glCreateShader(kind);
    GL20.glShaderSource(id, source);
    GL20.glCompileShader(id);
    if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
      String log = GL20.glGetShaderInfoLog(id);
      GL20.glDeleteShader(id);
      throw new IllegalStateException("NightClient GUI shader: " + log);
    }
    return id;
  }

  private static String roundedCoverage() {
    try (InputStream stream = LegacyUiRenderer.class.getResourceAsStream(
             "/assets/nightclient/shaders/include/rounded_surface.glsl")) {
      if (stream == null) throw new IllegalStateException("Missing analytic surface shader");
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[2048];
      int count;
      while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
      return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Cannot read analytic surface shader", failure);
    }
  }

  private static void create() {
    int vs = shader(GL20.GL_VERTEX_SHADER,
        "#version 150\n"
            + "in vec2 Position;in vec2 UV;in vec4 Color;uniform vec2 Screen;out vec2"
            + " texCoord;out vec4 vertexColor;void"
            + " main(){gl_Position=vec4(Position.x/Screen.x*2.0-1.0,1.0-Position.y/"
              + "Screen.y*2.0,0.0,1.0);texCoord=UV;vertexColor=Color;}");
    int fs = shader(GL20.GL_FRAGMENT_SHADER,
        "#version 150\n" + roundedCoverage() + "\n"
            + "in vec2 texCoord;in vec4 vertexColor;uniform sampler2D Font;uniform sampler2D"
            + " ItemAfter;uniform int Textured;uniform float ItemAlpha;out vec4 fragColor;void"
            + (" main(){fragColor=vertexColor;if(Textured==1)fragColor.a*=texture(Font,texCoord).a;"
               + "else")
            + (" if(Textured==2)fragColor=mix(texture(Font,texCoord),texture(ItemAfter,texCoord),"
               + "ItemAlpha);else")
            + " if(Textured==3)fragColor=texture(Font,texCoord)*vertexColor;else"
            + " if(Textured==4)fragColor.a*=nightRoundedCoverage(texCoord);}");
    program = GL20.glCreateProgram();
    GL20.glAttachShader(program, vs);
    GL20.glAttachShader(program, fs);
    GL20.glBindAttribLocation(program, 0, "Position");
    GL20.glBindAttribLocation(program, 1, "UV");
    GL20.glBindAttribLocation(program, 2, "Color");
    GL30.glBindFragDataLocation(program, 0, "fragColor");
    GL20.glLinkProgram(program);
    GL20.glDeleteShader(vs);
    GL20.glDeleteShader(fs);
    if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
      String log = GL20.glGetProgramInfoLog(program);
      dispose();
      throw new IllegalStateException("NightClient GUI pipeline: " + log);
    }
    screenUniform = GL20.glGetUniformLocation(program, "Screen");
    texturedUniform = GL20.glGetUniformLocation(program, "Textured");
    itemAlphaUniform = GL20.glGetUniformLocation(program, "ItemAlpha");
    vao = GL30.glGenVertexArrays();
    vbo = GL15.glGenBuffers();
    GL30.glBindVertexArray(vao);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    for (int i = 0; i < 3; i++) GL20.glEnableVertexAttribArray(i);
    GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 32, 0L);
    GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 32, 8L);
    GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 32, 16L);
    try (InputStream stream = LegacyUiRenderer.class.getResourceAsStream(
             "/assets/nightclient/textures/gui/font.png")) {
      if (stream == null) throw new IllegalStateException("Missing NightClient font atlas");
      BufferedImage image = ImageIO.read(stream);
      ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
      for (int y = 0; y < image.getHeight(); y++)
        for (int x = 0; x < image.getWidth(); x++) {
          int c = image.getRGB(x, y);
          pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) (c >>> 24));
        }
      pixels.flip();
      font = GL11.glGenTextures();
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      PixelUnpackState unpack = new PixelUnpackState();
      try {
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.getWidth(), image.getHeight(),
            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
      } finally {
        unpack.restore();
      }
    } catch (Exception e) {
      dispose();
      throw new IllegalStateException("Unable to load NightClient font atlas", e);
    }
    GL20.glUseProgram(program);
    GL20.glUniform1i(GL20.glGetUniformLocation(program, "Font"), 0);
    GL20.glUniform1i(GL20.glGetUniformLocation(program, "ItemAfter"), 1);
  }

  public static void invalidate() {
    invalidated = true;
  }

  public static void close() {
    if (program != 0) dispose();
  }

  private static void dispose() {
    for (int texture : materialTextures.values()) GL11.glDeleteTextures(texture);
    materialTextures.clear();
    if (itemBefore != 0) GL11.glDeleteTextures(itemBefore);
    if (itemAfter != 0) GL11.glDeleteTextures(itemAfter);
    if (font != 0) GL11.glDeleteTextures(font);
    if (vbo != 0) GL15.glDeleteBuffers(vbo);
    if (vao != 0) GL30.glDeleteVertexArrays(vao);
    if (program != 0) GL20.glDeleteProgram(program);
    font = vbo = vboCapacityBytes = vao = program = itemBefore = itemAfter = itemWidth = itemHeight = 0;
  }

  static final class PixelUnpackState {
    final int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
    final int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
    final int imageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
    final int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
    final int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
    final int skipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
    final int buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);

    PixelUnpackState() {
      GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
      GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
      GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
    }

    void restore() {
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
      GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
      GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, skipImages);
      GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
    }
  }

  static final class State {
    private static final IntBuffer STATE_INTS = BufferUtils.createIntBuffer(4);
    private static final ByteBuffer STATE_BYTES = BufferUtils.createByteBuffer(4);
    private static org.lwjgl.opengl.GLCapabilities cachedCaps;
    private static boolean cachedSamplerSupported;
    private static boolean cachedAlphaTestSupported;

    private static boolean isSamplerSupported() {
      org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();
      if (caps != cachedCaps) {
        cachedCaps = caps;
        cachedSamplerSupported = caps.OpenGL33;
        cachedAlphaTestSupported =
            !caps.OpenGL32
                || (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)
                        & GL32.GL_CONTEXT_CORE_PROFILE_BIT)
                    == 0;
      }
      return cachedSamplerSupported;
    }

    final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
              vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
              array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
              activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), texture0, texture1;
    final int read, draw;
    final boolean samplerSupported = isSamplerSupported();
    final int sampler0, sampler1, polygon;
    final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
              dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
              srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
              dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
              equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
              depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND),
                  depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                  cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
                  scissor,
                  depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                  logic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);

    final boolean alphaTestSupported = cachedAlphaTestSupported;
    final boolean alphaTest = alphaTestSupported && GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    final int alphaFunction =
        alphaTestSupported ? GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC) : GL11.GL_ALWAYS;
    final float alphaReference = alphaTestSupported ? GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF) : 0;
    final int[] viewport = new int[4], scissorBox = new int[4];
    final boolean[] colorMask = new boolean[4];

    State() {
      read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
      draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
      scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      STATE_INTS.clear();
      GL11.glGetIntegerv(GL11.GL_VIEWPORT, STATE_INTS);
      STATE_INTS.get(viewport);
      STATE_INTS.clear();
      GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, STATE_INTS);
      STATE_INTS.get(scissorBox);
      STATE_INTS.clear();
      GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, STATE_INTS);
      polygon = STATE_INTS.get(0);
      STATE_BYTES.clear();
      GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, STATE_BYTES);
      for (int i = 0; i < 4; i++) colorMask[i] = STATE_BYTES.get(i) != 0;
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      sampler0 = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      sampler1 = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
      GL13.glActiveTexture(activeTexture);
    }

    State(State outer) {
      read = outer.read;
      draw = outer.draw;
      scissor = outer.scissor;
      System.arraycopy(outer.viewport, 0, viewport, 0, 4);
      System.arraycopy(outer.scissorBox, 0, scissorBox, 0, 4);
      STATE_INTS.clear();
      GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, STATE_INTS);
      polygon = STATE_INTS.get(0);
      STATE_BYTES.clear();
      GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, STATE_BYTES);
      for (int i = 0; i < 4; i++) colorMask[i] = STATE_BYTES.get(i) != 0;
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      sampler0 = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      sampler1 = samplerSupported ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
      GL13.glActiveTexture(activeTexture);
    }

    private static void enabled(int token, boolean value) {
      if (value)
        GL11.glEnable(token);
      else
        GL11.glDisable(token);
    }

    void restore() {
      GL20.glUseProgram(program);
      GL30.glBindVertexArray(vao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
      GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
      if (samplerSupported) {
        GL33.glBindSampler(0, sampler0);
        GL33.glBindSampler(1, sampler1);
      }
      GL13.glActiveTexture(activeTexture);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, activeBinding);
      GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
      GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
      GL11.glDepthFunc(depthFunc);
      enabled(GL11.GL_BLEND, blend);
      enabled(GL11.GL_DEPTH_TEST, depth);
      enabled(GL11.GL_CULL_FACE, cull);
      enabled(GL11.GL_SCISSOR_TEST, scissor);
      enabled(GL11.GL_STENCIL_TEST, stencil);
      enabled(GL11.GL_COLOR_LOGIC_OP, logic);
      if (alphaTestSupported) {
        GL11.glAlphaFunc(alphaFunction, alphaReference);
        enabled(GL11.GL_ALPHA_TEST, alphaTest);
      }
      GL11.glDepthMask(depthMask);
      GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
      GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
      GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygon);
      GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
    }
  }
}
