package dev.nightclient.renderer;

import dev.nightclient.api.Canvas;
import dev.nightclient.renderer.Icons;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
class IconGeometryParityTest {
  static final class Sink implements Canvas {
    final MessageDigest hash;
    final ByteBuffer bytes = ByteBuffer.allocate(4);
    int commands;
    Sink() throws Exception {
      hash = MessageDigest.getInstance("SHA-256");
    }
    void command(String kind, int color, float... values) {
      hash.update(kind.getBytes(StandardCharsets.UTF_8));
      integer(color);
      for (float f : values) integer(Float.floatToIntBits(f));
      commands++;
    }
    void integer(int n) {
      bytes.clear();
      bytes.putInt(n);
      hash.update(bytes.array());
    }
    public void rect(float x, float y, float w, float h, int c) {
      command("rect", c, x, y, w, h);
    }
    public void roundedRect(float x, float y, float w, float h, float r, int c) {
      command("round", c, x, y, w, h, r);
    }
    public void strokeRoundedRect(float x, float y, float w, float h, float r, float t, int c) {
      command("stroke", c, x, y, w, h, r, t);
    }
    public void gradientRect(float x, float y, float w, float h, int a, int b) {
      throw new AssertionError();
    }
    public void line(float x, float y, float xx, float yy, float t, int c) {
      command("line", c, x, y, xx, yy, t);
    }
    public void circle(float x, float y, float r, int c) {
      command("circle", c, x, y, r);
    }
    public void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int c) {
      command("triangle", c, ax, ay, bx, by, cx, cy);
    }
    public void text(String s, float x, float y, float z, int c, boolean b) {
      throw new AssertionError();
    }
    public float textWidth(String s, float z, boolean b) {
      throw new AssertionError();
    }
    public void pushClip(float x, float y, float w, float h) {
      throw new AssertionError();
    }
    public void popClip() {
      throw new AssertionError();
    }
    public void item(String s, float x, float y, float z, float a) {
      throw new AssertionError();
    }
    public void backdrop(float x, float y, float w, float h, float r, float b, int q, float a) {
      throw new AssertionError();
    }
    public void texture(String s, float x, float y, float w, float h, float r, float t, int c) {
      throw new AssertionError();
    }
  }
  @org.junit.jupiter.api.Test
  void allSvgCommandsMatchTheEstablishedGeometryAcrossScaleAndStroke() throws Exception {
    Sink sink = new Sink();
    for (String id : Icons.ids()) {
      sink.hash.update(id.getBytes(StandardCharsets.UTF_8));
      for (float size : new float[] {7.5f, 24, 64})
        for (float stroke : new float[] {.5f, 1.35f, 3})
          Icons.draw(sink, id, 13.2f, 19.5f, size, 0xff123456, stroke);
    }
    StringBuilder hash = new StringBuilder();
    for (byte b : sink.hash.digest())
      hash.append(String.format("%02x", b & 255));
    org.junit.jupiter.api.Assertions.assertEquals(112, Icons.ids().size());
    org.junit.jupiter.api.Assertions.assertEquals(36684, sink.commands);
    org.junit.jupiter.api.Assertions.assertEquals(
        "8e6556b563d8bfcc44407edf24aa76e995f35cae3740a785f76e3c5027500d44", hash.toString());
  }
}
