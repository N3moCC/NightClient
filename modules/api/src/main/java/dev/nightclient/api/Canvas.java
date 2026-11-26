package dev.nightclient.api;

public interface Canvas {
  default Object textMetricsKey() {
    return null;
  }

  default boolean vanillaCrosshair(float x, float y, float size, float opacity) {
    return false;
  }

  default void viewmodelOutline(String hand, int argb) {}

  void rect(float x, float y, float width, float height, int argb);

  default void fillTriangle(float ax, float ay, float bx, float by, float cx, float cy, int argb) {}

  default void fillPolygon(float centerX, float centerY, float[] xy, int argb) {
    if (xy == null || xy.length < 6) return;
    int n = xy.length / 2;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      fillTriangle(centerX, centerY, xy[i * 2], xy[i * 2 + 1], xy[j * 2], xy[j * 2 + 1], argb);
    }
  }

  default void invertRect(float x, float y, float width, float height, float alpha) {
    rect(x, y, width, height, Colors.alpha(0xffffffff, alpha));
  }

  void roundedRect(float x, float y, float width, float height, float radius, int argb);

  default void roundedRectCorners(
      float x, float y, float w, float h, float tl, float tr, float bl, float br, int color) {
    roundedRect(x, y, w, h, Math.min(Math.min(tl, tr), Math.min(bl, br)), color);
  }

  default void strokeRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, float thickness, int color) {
    strokeRoundedRect(x, y, w, h, Math.min(Math.min(tl, tr), Math.min(bl, br)), thickness, color);
  }

  default void gradientRoundedRectCorners(float x, float y, float w, float h, float tl, float tr,
      float bl, float br, int ctl, int ctr, int cbl, int cbr) {
    gradientRect(x, y, w, h, ctl, cbl);
  }

  void strokeRoundedRect(
      float x, float y, float width, float height, float radius, float thickness, int argb);

  void gradientRect(float x, float y, float width, float height, int topArgb, int bottomArgb);

  default void gradientRoundedRect(
      float x, float y, float width, float height, float radius, int leftArgb, int rightArgb) {
    gradientRect(x, y, width, height, leftArgb, rightArgb);
  }

  void line(float x1, float y1, float x2, float y2, float thickness, int argb);

  void circle(float centerX, float centerY, float radius, int argb);

  void text(String value, float x, float y, float size, int argb, boolean bold);

  float textWidth(String value, float size, boolean bold);

  default void textFamily(String family, String value, float x, float y, float size, int argb,
      boolean bold, float tracking) {
    textTracked(value, x, y, size, argb, bold, tracking);
  }

  default float textWidthFamily(
      String family, String value, float size, boolean bold, float tracking) {
    return textWidthTracked(value, size, bold, tracking);
  }

  default void gameText(String value, float x, float y, float scale, int argb, boolean shadow) {
    float pen = x;
    for (FormattedText.Run run : FormattedText.runs(value, argb)) {
      text(run.text, pen, y, 9 * scale, run.color, run.bold);
      pen += textWidth(run.text, 9 * scale, run.bold);
    }
  }

  default float gameTextWidth(String value, float scale) {
    float width = 0;
    for (FormattedText.Run run : FormattedText.runs(value, 0xffffffff))
      width += textWidth(run.text, 9 * scale, run.bold);
    return width;
  }

  default void hungerIcon(float x, float y, float size, int fill) {}

  void pushClip(float x, float y, float width, float height);

  void popClip();

  void item(String registryId, float x, float y, float size, float alpha);

  default void inventoryItem(InventorySlot slot, float x, float y, float size, float alpha) {
    if (slot != null && !slot.empty()) item(slot.itemId, x, y, size, alpha);
  }

  default boolean entityHead(TargetView target, float x, float y, float size, float alpha) {
    return false;
  }

  default boolean skinHead(String skinTextureId, float x, float y, float size, float radius,
      float alpha, int tintRgb) {
    if (skinTextureId == null || skinTextureId.isEmpty() || size <= 0 || alpha <= 0) return false;
    return entityHead(new TargetView(0, "", skinTextureId, 20f, 20f, 0f, 0f, true), x, y, size,
        alpha);
  }

  default boolean textureRegion(String textureId, float x, float y, float width, float height,
      float radius, float u0, float v0, float u1, float v1, float alpha, int tintRgb) {
    return false;
  }

  default void mapQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
      float x, float y, float width, float height, float radius, float uCenter, float vCenter,
      float uHalfSpan, float vHalfSpan, float angleRad, boolean nearest, int argb) {}

  default boolean supportsIconAtlas() {
    return false;
  }

  default float iconPixelDensity() {
    return 1.0f;
  }

  default void iconQuad(String cacheKey, long revision, int[] pixels, int texWidth, int texHeight,
      float x, float y, float width, float height, int argb) {
    mapQuad(cacheKey, revision, pixels, texWidth, texHeight, x, y, width, height, 0f,
        0.5f, 0.5f, 0.5f, 0.5f, 0f, false, argb);
  }

  default void playerPreview(PlayerSkin skin, float x, float y, float width, float height,
      float yaw, float pitch, float zoom, float time, float wind, float yawVelocity,
      float opacity) {}

  default void capePreview(String cacheKey, PlayerSkin skin, float x, float y, float width,
      float height, float opacity) {}

  void backdrop(float x, float y, float width, float height, float radius, float blurRadius,
      int quality, float opacity);

  default void backdropCorners(float x, float y, float w, float h, float tl, float tr, float bl,
      float br, float blurRadius, int quality, float opacity) {
    backdrop(
        x, y, w, h, Math.max(Math.max(tl, tr), Math.max(bl, br)), blurRadius, quality, opacity);
  }

  void texture(String resourceId, float x, float y, float width, float height, float radius,
      float tileSize, int argb);

  default void textureCorners(String resourceId, float x, float y, float width, float height,
      float tl, float tr, float bl, float br, float tileSize, int argb) {
    texture(resourceId, x, y, width, height, Math.max(Math.max(tl, tr), Math.max(bl, br)), tileSize,
        argb);
  }

  default void gameSprite(String spriteId, float x, float y, float width, float height, float u,
      float v, float regionWidth, float regionHeight, float sourceWidth, float sourceHeight,
      int argb) {}

  default void textTracked(
      String value, float x, float y, float size, int argb, boolean bold, float tracking) {
    float pen = x;
    for (int i = 0; value != null && i < value.length();) {
      int cp = value.codePointAt(i);
      i += Character.charCount(cp);
      String glyph = new String(Character.toChars(cp));
      text(glyph, pen, y, size, argb, bold);
      pen += textWidth(glyph, size, bold) + tracking;
    }
  }

  default float textWidthTracked(String value, float size, boolean bold, float tracking) {
    return value == null || value.isEmpty() ? 0
                                            : textWidth(value, size, bold)
            + Math.max(0, value.codePointCount(0, value.length()) - 1) * tracking;
  }
}
