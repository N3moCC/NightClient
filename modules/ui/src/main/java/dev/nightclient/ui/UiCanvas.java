package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.config.Theme;

final class UiCanvas implements Canvas {
  final Canvas delegate;
  final float x, y, scale, opacity;
  final Theme theme;

  UiCanvas(Canvas delegate, float x, float y, float scale, float opacity) {
    this(delegate, x, y, scale, opacity, null);
  }

  UiCanvas(Canvas delegate, float x, float y, float scale, float opacity, Theme theme) {
    this.delegate = delegate;
    this.x = x;
    this.y = y;
    this.scale = scale;
    this.opacity = opacity;
    this.theme = theme;
  }

  float localX(double value) {
    return (float) ((value - x) / scale);
  }

  float localY(double value) {
    return (float) ((value - y) / scale);
  }

  public boolean vanillaCrosshair(float a, float b, float size, float alpha) {
    return delegate.vanillaCrosshair(x + a * scale, y + b * scale, size * scale, alpha * opacity);
  }

  public void viewmodelOutline(String hand, int argb) {
    delegate.viewmodelOutline(hand, color(argb));
  }

  private int color(int value) {
    return Colors.alpha(value, opacity);
  }

  public void rect(float a, float b, float w, float h, int c) {
    delegate.rect(x + a * scale, y + b * scale, w * scale, h * scale, color(c));
  }

  public void fillTriangle(float a, float b, float c, float d, float e, float f, int argb) {
    delegate.fillTriangle(x + a * scale, y + b * scale, x + c * scale, y + d * scale, x + e * scale,
        y + f * scale, color(argb));
  }

  public void fillPolygon(float centerX, float centerY, float[] xy, int argb) {
    if (xy == null || xy.length < 6) return;
    float[] scaled = new float[xy.length];
    for (int i = 0; i + 1 < xy.length; i += 2) {
      scaled[i] = x + xy[i] * scale;
      scaled[i + 1] = y + xy[i + 1] * scale;
    }
    delegate.fillPolygon(x + centerX * scale, y + centerY * scale, scaled, color(argb));
  }

  public void invertRect(float a, float b, float w, float h, float alpha) {
    delegate.invertRect(x + a * scale, y + b * scale, w * scale, h * scale, alpha * opacity);
  }

  public void roundedRect(float a, float b, float w, float h, float r, int c) {
    delegate.roundedRect(x + a * scale, y + b * scale, w * scale, h * scale, r * scale, color(c));
  }

  public void roundedRectCorners(
      float a, float b, float w, float h, float tl, float tr, float bl, float br, int c) {
    delegate.roundedRectCorners(x + a * scale, y + b * scale, w * scale, h * scale, tl * scale,
        tr * scale, bl * scale, br * scale, color(c));
  }

  public void strokeRoundedRectCorners(
      float a, float b, float w, float h, float tl, float tr, float bl, float br, float t, int c) {
    delegate.strokeRoundedRectCorners(x + a * scale, y + b * scale, w * scale, h * scale,
        tl * scale, tr * scale, bl * scale, br * scale, t * scale, color(c));
  }

  public void gradientRoundedRectCorners(float a, float b, float w, float h, float tl, float tr,
      float bl, float br, int ctl, int ctr, int cbl, int cbr) {
    delegate.gradientRoundedRectCorners(x + a * scale, y + b * scale, w * scale, h * scale,
        tl * scale, tr * scale, bl * scale, br * scale, color(ctl), color(ctr), color(cbl),
        color(cbr));
  }

  public void strokeRoundedRect(float a, float b, float w, float h, float r, float t, int c) {
    delegate.strokeRoundedRect(
        x + a * scale, y + b * scale, w * scale, h * scale, r * scale, t * scale, color(c));
  }

  public void gradientRect(float a, float b, float w, float h, int top, int bottom) {
    delegate.gradientRect(
        x + a * scale, y + b * scale, w * scale, h * scale, color(top), color(bottom));
  }

  public void gradientRoundedRect(
      float a, float b, float w, float h, float r, int left, int right) {
    delegate.gradientRoundedRect(
        x + a * scale, y + b * scale, w * scale, h * scale, r * scale, color(left), color(right));
  }

  public void line(float a, float b, float c, float d, float t, int color) {
    delegate.line(
        x + a * scale, y + b * scale, x + c * scale, y + d * scale, t * scale, color(color));
  }

  public void circle(float a, float b, float r, int c) {
    delegate.circle(x + a * scale, y + b * scale, r * scale, color(c));
  }

  public void text(String value, float a, float b, float size, int c, boolean bold) {
    if (theme == null)
      delegate.text(value, x + a * scale, y + b * scale, size * scale, color(c), bold);
    else
      delegate.textFamily(theme.fontFamily, value, x + a * scale, y + b * scale, size * scale,
          color(c), bold || "bold".equals(theme.fontWeight), theme.fontTracking * scale);
  }

  public float textWidth(String value, float size, boolean bold) {
    return (theme == null
                   ? delegate.textWidth(value, size * scale, bold)
                   : delegate.textWidthFamily(theme.fontFamily, value, size * scale,
                         bold || "bold".equals(theme.fontWeight), theme.fontTracking * scale))
        / scale;
  }

  public void textTracked(
      String value, float a, float b, float size, int c, boolean bold, float tracking) {
    textFamily(theme == null ? "onest" : theme.fontFamily, value, a, b, size, c, bold, tracking);
  }

  public float textWidthTracked(String value, float size, boolean bold, float tracking) {
    return textWidthFamily(theme == null ? "onest" : theme.fontFamily, value, size, bold, tracking);
  }

  public void textFamily(String family, String value, float a, float b, float size, int c,
      boolean bold, float tracking) {
    delegate.textFamily(family, value, x + a * scale, y + b * scale, size * scale, color(c), bold,
        tracking * scale);
  }

  public float textWidthFamily(
      String family, String value, float size, boolean bold, float tracking) {
    return delegate.textWidthFamily(family, value, size * scale, bold, tracking * scale) / scale;
  }

  public void gameText(String value, float a, float b, float textScale, int color, boolean shadow) {
    delegate.gameText(value, x + a * scale, y + b * scale, textScale * scale, color(color), shadow);
  }

  public float gameTextWidth(String value, float textScale) {
    return delegate.gameTextWidth(value, textScale * scale) / scale;
  }

  public void hungerIcon(float a, float b, float size, int fill) {
    delegate.hungerIcon(x + a * scale, y + b * scale, size * scale, fill);
  }

  public void backdrop(
      float a, float b, float w, float h, float r, float blur, int quality, float alpha) {
    delegate.backdrop(x + a * scale, y + b * scale, w * scale, h * scale, r * scale, blur * scale,
        quality, alpha * opacity);
  }

  public void backdropCorners(float a, float b, float w, float h, float tl, float tr, float bl,
      float br, float blur, int quality, float alpha) {
    delegate.backdropCorners(x + a * scale, y + b * scale, w * scale, h * scale, tl * scale,
        tr * scale, bl * scale, br * scale, blur * scale, quality, alpha * opacity);
  }

  public boolean entityHead(
      dev.nightclient.api.TargetView target, float a, float b, float size, float alpha) {
    return delegate.entityHead(target, x + a * scale, y + b * scale, size * scale, alpha * opacity);
  }

  public boolean skinHead(String skinTextureId, float a, float b, float size, float radius,
      float alpha, int tintRgb) {
    return delegate.skinHead(skinTextureId, x + a * scale, y + b * scale, size * scale,
        radius * scale, alpha * opacity, tintRgb);
  }

  public boolean textureRegion(String textureId, float a, float b, float w, float h, float r,
      float u0, float v0, float u1, float v1, float alpha, int tintRgb) {
    return delegate.textureRegion(textureId, x + a * scale, y + b * scale, w * scale, h * scale,
        r * scale, u0, v0, u1, v1, alpha * opacity, tintRgb);
  }

  public void mapQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
      float a, float b, float w, float h, float r, float uCenter, float vCenter, float uHalfSpan,
      float vHalfSpan, float angleRad, boolean nearest, int argb) {
    delegate.mapQuad(cacheKey, revision, pixels, texWidth, texHeight, x + a * scale, y + b * scale,
        w * scale, h * scale, r < 0f ? r : r * scale, uCenter, vCenter, uHalfSpan, vHalfSpan,
        angleRad, nearest, color(argb));
  }

  public boolean supportsIconAtlas() {
    return delegate.supportsIconAtlas();
  }

  public float iconPixelDensity() {
    float tier = scale > 1.15f ? 1.5f : 1.0f;
    return delegate.iconPixelDensity() * tier;
  }

  public void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
      float a, float b, float w, float h, int argb) {
    delegate.iconQuad(cacheKey, revision, pixels, texWidth, texHeight, x + a * scale,
        y + b * scale, w * scale, h * scale, color(argb));
  }

  public void playerPreview(dev.nightclient.api.PlayerSkin skin, float a, float b, float w, float h,
      float yaw, float pitch, float zoom, float time, float wind, float yawVelocity, float alpha) {
    delegate.playerPreview(skin, x + a * scale, y + b * scale, w * scale, h * scale, yaw, pitch,
        zoom, time, wind, yawVelocity, alpha * opacity);
  }

  public void capePreview(String cacheKey, dev.nightclient.api.PlayerSkin skin, float a, float b,
      float w, float h, float alpha) {
    delegate.capePreview(
        cacheKey, skin, x + a * scale, y + b * scale, w * scale, h * scale, alpha * opacity);
  }

  public void texture(
      String id, float a, float b, float w, float h, float r, float tile, int argb) {
    delegate.texture(id, x + a * scale, y + b * scale, w * scale, h * scale, r * scale,
        tile * scale, color(argb));
  }

  public void textureCorners(String id, float a, float b, float w, float h, float tl, float tr,
      float bl, float br, float tile, int argb) {
    delegate.textureCorners(id, x + a * scale, y + b * scale, w * scale, h * scale, tl * scale,
        tr * scale, bl * scale, br * scale, tile * scale, color(argb));
  }

  public void gameSprite(String id, float a, float b, float w, float h, float u, float v, float rw,
      float rh, float sw, float sh, int argb) {
    delegate.gameSprite(
        id, x + a * scale, y + b * scale, w * scale, h * scale, u, v, rw, rh, sw, sh, color(argb));
  }

  public void pushClip(float a, float b, float w, float h) {
    delegate.pushClip(x + a * scale, y + b * scale, w * scale, h * scale);
  }

  public void popClip() {
    delegate.popClip();
  }

  public void item(String id, float a, float b, float size, float alpha) {
    delegate.item(id, x + a * scale, y + b * scale, size * scale, alpha * opacity);
  }

  public void inventoryItem(InventorySlot slot, float a, float b, float size, float alpha) {
    delegate.inventoryItem(slot, x + a * scale, y + b * scale, size * scale, alpha * opacity);
  }
}
