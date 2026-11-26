package dev.nightclient.renderer.map;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.MapRadarModels;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import dev.nightclient.config.Waypoint;
import dev.nightclient.renderer.SurfacePainter;
import dev.nightclient.renderer.TexturedMesh;
import java.util.List;

public final class MinimapRenderer {
  private static final String[] COMPASS_LABELS = {"N", "E", "S", "W"};
  private static final float[] COMPASS_WX = {0f, 1f, 0f, -1f};
  private static final float[] COMPASS_WZ = {-1f, 0f, 1f, 0f};
  private static final String[] MULT_BADGES = new String[129];
  private static final String[] PLUS_BADGES = new String[65];
  private static final String[] ASCII_UPPER = new String[128];
  private static final ThreadLocal<float[]> SCRATCH_BOUNDS =
      new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
          return new float[5];
        }
      };
  private static final ThreadLocal<float[]> SCRATCH_PT =
      new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
          return new float[2];
        }
      };
  private static volatile CoordCache coordCache;

  static {
    for (int i = 0; i < MULT_BADGES.length; i++) MULT_BADGES[i] = "×" + i;
    for (int i = 0; i < PLUS_BADGES.length; i++) PLUS_BADGES[i] = "+" + i;
    for (int i = 0; i < ASCII_UPPER.length; i++) {
      ASCII_UPPER[i] = String.valueOf((char) Character.toUpperCase(i));
    }
  }

  private static final class CoordCache {
    final int x, y, z;
    final String text;

    CoordCache(int x, int y, int z, String text) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.text = text;
    }
  }

  private MinimapRenderer() {}

  public static float[] computeBounds(Settings settings, int viewportW, int viewportH) {
    return computeBounds(settings, viewportW, viewportH, 0f);
  }

  public static float[] computeBounds(
      Settings settings, int viewportW, int viewportH, float enlargeProgress) {
    float[] out = new float[5];
    computeBoundsInto(settings, viewportW, viewportH, enlargeProgress, out);
    return out;
  }

  private static void computeBoundsInto(
      Settings settings, int viewportW, int viewportH, float enlargeProgress, float[] out) {
    Settings.Minimap cfg = settings.minimap;
    float baseW = Math.max(72f, cfg.size);
    float baseH = baseW + (cfg.showCoords ? 16f : 0f);
    float baseS = Math.max(0.01f, Math.min(cfg.scale, Math.min(viewportW / baseW, viewportH / baseH)));
    float drawW = baseW * baseS;
    float drawH = baseH * baseS;
    float x = clamp(cfg.anchorX * (viewportW - drawW) + cfg.offsetX, 0f,
        Math.max(0f, viewportW - drawW));
    float y = clamp(cfg.anchorY * (viewportH - drawH) + cfg.offsetY, 0f,
        Math.max(0f, viewportH - drawH));

    float ep = clamp(enlargeProgress, 0f, 1f);
    if (ep > 0.0001f) {
      float maxFit = Math.max(80f, Math.min(viewportW - 12f, viewportH - 28f));
      float targetW = Math.min(maxFit, Math.max(drawW * 1.25f, cfg.enlargeSize));
      float targetS = Math.max(baseS, targetW / baseW);
      float targetUiS = Math.min(targetS, baseS * 1.18f);
      float targetCoordsH = cfg.showCoords ? 16f * targetUiS : 0f;
      float targetH = targetW + targetCoordsH;
      if (targetH > viewportH) {
        targetH = viewportH;
        targetW = Math.max(48f, targetH - targetCoordsH);
        targetS = Math.max(baseS, targetW / baseW);
      }

      float slackX = viewportW - drawW;
      float slackY = viewportH - drawH;
      float idealEndX;
      if (slackX <= 1f) {
        idealEndX = (viewportW - targetW) * 0.5f;
      } else if (x <= slackX * 0.25f) {
        idealEndX = x;
      } else if (x >= slackX * 0.75f) {
        idealEndX = (x + drawW) - targetW;
      } else {
        idealEndX = x - (targetW - drawW) * 0.5f;
      }
      float idealEndY;
      if (slackY <= 1f) {
        idealEndY = (viewportH - targetH) * 0.5f;
      } else if (y <= slackY * 0.25f) {
        idealEndY = y;
      } else if (y >= slackY * 0.75f) {
        idealEndY = (y + drawH) - targetH;
      } else {
        idealEndY = y - (targetH - drawH) * 0.5f;
      }
      float endX = clamp(idealEndX, 0f, Math.max(0f, viewportW - targetW));
      float endY = clamp(idealEndY, 0f, Math.max(0f, viewportH - targetH));

      out[0] = x + (endX - x) * ep;
      out[1] = y + (endY - y) * ep;
      out[2] = drawW + (targetW - drawW) * ep;
      out[3] = drawH + (targetH - drawH) * ep;
      out[4] = baseS + (targetS - baseS) * ep;
      return;
    }
    out[0] = x;
    out[1] = y;
    out[2] = drawW;
    out[3] = drawH;
    out[4] = baseS;
  }

  public static void renderLive(Canvas canvas, Settings settings, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, int viewportW, int viewportH, double timeSeconds) {
    renderLive(canvas, settings, cache, radar, viewportW, viewportH, 0f, timeSeconds);
  }

  public static void renderLive(Canvas canvas, Settings settings, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, int viewportW, int viewportH, float enlargeProgress,
      double timeSeconds) {
    if (FeaturePolicy.isBlocked("minimap") || canvas == null || settings == null || settings.minimap == null || !settings.minimap.enabled)
      return;
    float ep = clamp(enlargeProgress, 0f, 1f);
    float[] b = SCRATCH_BOUNDS.get();
    computeBoundsInto(settings, viewportW, viewportH, ep, b);
    renderAt(canvas, settings, cache, radar, b[0], b[1], b[2], b[3], b[4], false, ep, timeSeconds);
  }

  public static void renderAt(Canvas canvas, Settings settings, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, float x, float y, float w, float h, float scale,
      boolean editorPreview, double timeSeconds) {
    renderAt(canvas, settings, cache, radar, x, y, w, h, scale, editorPreview, 0f, timeSeconds);
  }

  public static void renderAt(Canvas canvas, Settings settings, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, float x, float y, float w, float h, float scale,
      boolean editorPreview, float enlargeProgress, double timeSeconds) {
    if (FeaturePolicy.isBlocked("minimap") || canvas == null || settings == null || settings.minimap == null || w <= 4f || h <= 4f)
      return;
    Settings.Minimap cfg = settings.minimap;
    Theme theme = settings.styleTheme("minimap");
    float alpha = clamp(cfg.opacity, 0.05f, 1f);
    float s = Math.max(0.25f, scale);
    float ep = clamp(enlargeProgress, 0f, 1f);
    float restS = Math.max(0.25f, Math.min(s, cfg.scale));
    float targetUiS = Math.min(s, restS * 1.18f);
    float uiS = ep > 0.0001f ? (restS + (targetUiS - restS) * ep) : s;

    String shape = normalizeShape(cfg.shape);
    int polySides = polygonSides(shape);
    boolean circle = "circle".equals(shape);
    boolean sharp = "sharp".equals(shape);

    float coordsReserve = cfg.showCoords ? 15f * uiS : 0f;
    float mapSize = Math.min(w, cfg.showCoords ? Math.max(24f, h - coordsReserve) : h);
    float mx = x + (w - mapSize) * 0.5f;
    float my = y;
    float outerRadius = circle ? mapSize * 0.5f
        : (sharp ? Math.max(1.2f, 1.8f * uiS) : Math.min(mapSize * 0.18f, 7.0f * uiS));
    float pad = Math.max(1.0f, 1.25f * uiS);
    float innerSize = Math.max(8f, mapSize - pad * 2f);
    float ix = mx + pad;
    float iy = my + pad;
    float innerRadius = polySides > 0 ? -polySides
        : (circle ? innerSize * 0.5f : Math.max(0.8f, outerRadius - pad));
    float cx = ix + innerSize * 0.5f;
    float cy = iy + innerSize * 0.5f;

    float frameAlpha = clamp(cfg.frameOpacity * alpha, 0f, 1f);
    if (polySides > 0) {
      if (frameAlpha > 0.01f) {
        drawPolygonFill(canvas, cx, cy + 1.4f * uiS, mapSize * 0.5f + 1.8f * uiS, polySides,
            Colors.alpha(0x28020408, frameAlpha));
        drawPolygonFill(canvas, cx, cy + 0.7f * uiS, mapSize * 0.5f + 0.8f * uiS, polySides,
            Colors.alpha(0x4403050A, frameAlpha));
        int bezelBg = Colors.mix(0xFF0A0D14, theme.surface | 0xFF000000, 0.28f);
        drawPolygonFill(
            canvas, cx, cy, mapSize * 0.5f, polySides, Colors.alpha(bezelBg, frameAlpha * 0.90f));
      }
      drawPolygonFill(canvas, cx, cy, innerSize * 0.5f, polySides,
          Colors.alpha(0xFF0D111A, alpha * 0.86f));
    } else {
      if (frameAlpha > 0.01f) {
        canvas.roundedRect(mx - 1.5f * uiS, my + 1.2f * uiS, mapSize + 3.0f * uiS,
            mapSize + 2.8f * uiS, outerRadius + 1.5f * uiS, Colors.alpha(0x24020408, frameAlpha));
        canvas.roundedRect(mx - 0.6f * uiS, my + 0.6f * uiS, mapSize + 1.2f * uiS,
            mapSize + 1.1f * uiS, outerRadius + 0.6f * uiS, Colors.alpha(0x4203050A, frameAlpha));
        if (SurfacePainter.usesBackdrop(theme)) {
          canvas.backdrop(mx, my, mapSize, mapSize, outerRadius, Math.max(4f, theme.blurRadius),
              theme.blurQuality, frameAlpha);
        }
        int bezelBg = Colors.mix(0xFF0A0D14, theme.surface | 0xFF000000, 0.28f);
        canvas.roundedRect(
            mx, my, mapSize, mapSize, outerRadius, Colors.alpha(bezelBg, frameAlpha * 0.90f));
      }
      canvas.roundedRect(
          ix, iy, innerSize, innerSize, innerRadius, Colors.alpha(0xFF0D111A, alpha * 0.86f));
    }

    boolean hasLiveWorld = radar != null && radar.inWorld && cache != null && !editorPreview;
    double playerX = hasLiveWorld ? radar.playerX : 128.0;
    double playerY = hasLiveWorld ? radar.playerY : 68.0;
    double playerZ = hasLiveWorld ? radar.playerZ : -84.0;
    float playerYaw = hasLiveWorld ? radar.playerYaw : -155f;

    float effectiveZoom = Math.max(0.35f, Math.min(4.0f, cfg.zoom));
    if (enlargeProgress > 0.001f) {
      float targetZoom = Math.max(0.35f, Math.min(2.5f, effectiveZoom * cfg.enlargeZoom));
      effectiveZoom = effectiveZoom + (targetZoom - effectiveZoom) * enlargeProgress;
    }
    float blocksAcross = 112f / effectiveZoom;
    float halfBlocks = blocksAcross * 0.5f;
    float angleRad = cfg.lockNorth ? 0f : (float) Math.toRadians(playerYaw - 180.0);
    float cosA = (float) Math.cos(angleRad);
    float sinA = (float) Math.sin(angleRad);

    int rStyle = MapColorShading.parseReliefStyle(cfg.reliefStyle);
    if (hasLiveWorld) {
      RegionCacheManager.MinimapWindow win = cache.composeMinimapWindow(
          playerX, playerZ, cfg.terrainShading, cfg.blockLight, rStyle, cfg.reliefStrength);
      float uCenter = (float) ((playerX - win.originBlockX) / (double) win.size);
      float vCenter = (float) ((playerZ - win.originBlockZ) / (double) win.size);
      float halfSpan = Math.min(0.48f, halfBlocks / (float) win.size);
      float logicalPxPerBlock = innerSize / Math.max(1f, blocksAcross);
      boolean nearest = cfg.lockNorth && logicalPxPerBlock >= 0.80f;
      canvas.mapQuad(win.cacheKey, win.revision, win.pixels, win.texSize, win.texSize, ix, iy,
          innerSize, innerSize, innerRadius, uCenter, vCenter, halfSpan, halfSpan, angleRad,
          nearest, Colors.alpha(0xFFFFFFFF, alpha));
    } else {
      int[] demo = cache != null ? cache.demoTerrainPixels()
                                 : RegionCacheManager.sharedDemoTerrainPixels();
      float halfSpan = Math.min(0.46f, halfBlocks / 192f);
      canvas.mapQuad("minimap_demo_island", 1L, demo, 192, 192, ix, iy, innerSize, innerSize,
          innerRadius, 0.5f, 0.5f, halfSpan, halfSpan, angleRad, false,
          Colors.alpha(0xFFFFFFFF, alpha));
    }

    canvas.pushClip(ix, iy, innerSize, innerSize);
    try {
      if (cfg.chunkGrid) {
        drawChunkGrid(
            canvas, cx, cy, innerSize, shape, playerX, playerZ, halfBlocks, cosA, sinA, alpha);
      }
      if (hasLiveWorld) {
        drawMobs(canvas, cfg, radar.mobs, cx, cy, innerSize, shape, playerX, playerY, playerZ,
            halfBlocks, cosA, sinA, uiS, alpha, timeSeconds);
        drawItems(canvas, cfg, radar.items, cx, cy, innerSize, shape, playerX, playerZ, halfBlocks,
            cosA, sinA, uiS, alpha);
        drawPlayers(canvas, cfg, theme, radar.players, cx, cy, innerSize, shape, playerX, playerZ,
            halfBlocks, cosA, sinA, uiS, alpha, timeSeconds);
      } else {
        drawDemoEntities(canvas, cfg, theme, cx, cy, innerSize, uiS, alpha, timeSeconds);
      }
    } finally {
      canvas.popClip();
    }

    if (!FeaturePolicy.isBlocked("waypoints") && cfg.showWaypoints && settings.waypoints != null && settings.waypoints.enabled
        && settings.waypoints.points != null && hasLiveWorld) {
      drawWaypoints(canvas, cfg, settings.waypoints.points, radar.worldId, radar.dimension, cx, cy,
          innerSize, shape, playerX, playerZ, halfBlocks, cosA, sinA, uiS, alpha);
    }

    if (frameAlpha > 0.01f) {
      int rimColor = Colors.mix(theme.border | 0xFF000000, 0xFFFFFFFF, 0.08f);
      if (polySides > 0) {
        drawPolygonStroke(canvas, cx, cy, innerSize * 0.5f, polySides, Math.max(0.8f, 1.0f * uiS),
            Colors.alpha(0x46000000, frameAlpha));
        drawPolygonStroke(canvas, cx, cy, mapSize * 0.5f, polySides, Math.max(0.65f, 0.75f * uiS),
            Colors.alpha(rimColor, frameAlpha * 0.52f));
      } else {
        canvas.strokeRoundedRect(ix + 0.35f, iy + 0.35f, innerSize - 0.7f, innerSize - 0.7f,
            Math.max(0.5f, innerRadius - 0.3f), Math.max(0.8f, 1.0f * uiS),
            Colors.alpha(0x44000000, frameAlpha));
        canvas.strokeRoundedRect(mx, my, mapSize, mapSize, outerRadius, Math.max(0.65f, 0.75f * uiS),
            Colors.alpha(rimColor, frameAlpha * 0.50f));
      }
    }

    float selfAngle = cfg.lockNorth ? (float) Math.toRadians(playerYaw - 180.0) : 0f;
    String selfSkin = findSelfSkin(radar);
    drawSelfMarker(canvas, cfg.arrowStyle, cx, cy, 4.15f * uiS * cfg.arrowScale, selfAngle,
        cfg.arrowColor | 0xFF000000, theme.accent | 0xFF000000, selfSkin, alpha);

    if (cfg.showCompass) {
      drawCompass(canvas, cx, cy, mapSize * 0.5f, shape, outerRadius, cosA, sinA, uiS, alpha);
    }

    if (cfg.showCoords) {
      float barY = my + mapSize + 3.6f * uiS;
      float barH = Math.max(10.0f, 11.4f * uiS);
      float barR = Math.min(barH * 0.5f, 4.0f * uiS);
      int cxBlock = (int) Math.floor(playerX);
      int cyBlock = (int) Math.floor(playerY);
      int czBlock = (int) Math.floor(playerZ);
      CoordCache cc = coordCache;
      String coords;
      if (cc != null && cc.x == cxBlock && cc.y == cyBlock && cc.z == czBlock) {
        coords = cc.text;
      } else {
        coords = cxBlock + "  " + cyBlock + "  " + czBlock;
        coordCache = new CoordCache(cxBlock, cyBlock, czBlock, coords);
      }
      float fontSize = 5.8f * uiS;
      float textW = canvas.textWidthFamily("studio-sans-medium", coords, fontSize, true, 0f);
      float barW = Math.min(mapSize, Math.max(Math.min(mapSize, 62f * uiS), textW + 12f * uiS));
      float barX = mx + (mapSize - barW) * 0.5f;
      if (frameAlpha > 0.01f) {
        canvas.roundedRect(barX - 0.5f * uiS, barY + 0.6f * uiS, barW + 1.0f * uiS,
            barH + 0.8f * uiS, barR + 0.5f * uiS, Colors.alpha(0x30020408, frameAlpha));
        if (SurfacePainter.usesBackdrop(theme)) {
          canvas.backdrop(barX, barY, barW, barH, barR, Math.max(4f, theme.blurRadius),
              theme.blurQuality, frameAlpha);
        }
        int pillBg = Colors.mix(0xFF0A0D14, theme.surface | 0xFF000000, 0.28f);
        canvas.roundedRect(
            barX, barY, barW, barH, barR, Colors.alpha(pillBg, frameAlpha * 0.82f));
        int pillRim = Colors.mix(theme.border | 0xFF000000, 0xFFFFFFFF, 0.08f);
        canvas.strokeRoundedRect(barX, barY, barW, barH, barR, Math.max(0.55f, 0.65f * uiS),
            Colors.alpha(pillRim, frameAlpha * 0.42f));
      }
      float tx = barX + (barW - textW) * 0.5f;
      float ty = barY + (barH - fontSize) * 0.5f - 0.15f * uiS;
      canvas.textFamily("studio-sans-medium", coords, tx, ty, fontSize,
          Colors.alpha(theme.text | 0xFF000000, alpha * 0.94f), true, 0f);
    }
  }

  private static void drawChunkGrid(Canvas canvas, float cx, float cy, float innerSize,
      String shape, double playerX, double playerZ, float halfBlocks, float cosA, float sinA,
      float alpha) {
    float halfPx = innerSize * 0.5f;
    float pxPerBlock = halfPx / halfBlocks;
    if (pxPerBlock < 0.35f) return;
    int spanChunks = (int) Math.ceil((halfBlocks * 1.45f) / 16.0) + 1;
    int baseCx = ((int) Math.floor(playerX)) >> 4;
    int baseCz = ((int) Math.floor(playerZ)) >> 4;
    int lineColor = Colors.alpha(0x30FFFFFF, alpha);
    float extent = halfBlocks * 1.5f;
    boolean radialClip = !"square".equals(shape) && !"sharp".equals(shape);

    for (int d = -spanChunks; d <= spanChunks; d++) {
      float wx = (float) (((baseCx + d) << 4) - playerX);
      drawRotatedGridLine(canvas, cx, cy, halfPx, halfBlocks, cosA, sinA, wx, -extent, wx, extent,
          radialClip, shape, lineColor);
      float wz = (float) (((baseCz + d) << 4) - playerZ);
      drawRotatedGridLine(canvas, cx, cy, halfPx, halfBlocks, cosA, sinA, -extent, wz, extent, wz,
          radialClip, shape, lineColor);
    }
  }

  private static void drawRotatedGridLine(Canvas canvas, float cx, float cy, float halfPx,
      float halfBlocks, float cosA, float sinA, float x1, float z1, float x2, float z2,
      boolean radialClip, String shape, int color) {
    float nx1 = (x1 * cosA + z1 * sinA) / halfBlocks;
    float ny1 = (-x1 * sinA + z1 * cosA) / halfBlocks;
    float nx2 = (x2 * cosA + z2 * sinA) / halfBlocks;
    float ny2 = (-x2 * sinA + z2 * cosA) / halfBlocks;
    if (radialClip) {
      float maxR = "diamond".equals(shape) ? 0.70f : ("hexagon".equals(shape) ? 0.86f : 0.94f);
      float dx = nx2 - nx1, dy = ny2 - ny1;
      float a = dx * dx + dy * dy;
      if (a < 1e-6f) return;
      float b = 2f * (nx1 * dx + ny1 * dy);
      float c = nx1 * nx1 + ny1 * ny1 - maxR * maxR;
      float disc = b * b - 4f * a * c;
      if (disc <= 0f) return;
      float sqrt = (float) Math.sqrt(disc);
      float t0 = Math.max(0f, (-b - sqrt) / (2f * a));
      float t1 = Math.min(1f, (-b + sqrt) / (2f * a));
      if (t1 <= t0) return;
      canvas.line(cx + (nx1 + dx * t0) * halfPx, cy + (ny1 + dy * t0) * halfPx,
          cx + (nx1 + dx * t1) * halfPx, cy + (ny1 + dy * t1) * halfPx, 0.55f, color);
    } else {
      canvas.line(cx + nx1 * halfPx, cy + ny1 * halfPx, cx + nx2 * halfPx, cy + ny2 * halfPx,
          0.55f, color);
    }
  }

  private static void drawMobs(Canvas canvas, Settings.Minimap cfg,
      List<MapRadarModels.MobEntry> mobs, float cx, float cy, float innerSize, String shape,
      double playerX, double playerY, double playerZ, float halfBlocks, float cosA, float sinA,
      float s, float alpha, double timeSeconds) {
    if (mobs == null || mobs.isEmpty()) return;
    float halfPx = innerSize * 0.5f;
    boolean iconStyle = !"dots".equals(cfg.mobIconStyle);
    float densityFactor = clamp(innerSize / 150f, 0.68f, 0.90f);
    float mScale = s * cfg.mobScale * densityFactor;

    for (int i = 0; i < mobs.size(); i++) {
      MapRadarModels.MobEntry m = mobs.get(i);
      if (!isMobVisible(cfg, m.kind)) continue;

      float dx = (float) ((m.x - playerX) / halfBlocks);
      float dz = (float) ((m.z - playerZ) / halfBlocks);
      float nx = dx * cosA + dz * sinA;
      float ny = -dx * sinA + dz * cosA;
      if (!isInsideShape(nx, ny, shape, 0.91f)) continue;
      float sx = cx + nx * halfPx;
      float sy = cy + ny * halfPx;
      MapEntityIconPainter.drawEntity(
          canvas, m, sx, sy, mScale, alpha, iconStyle, cfg.showMobNames, playerY, timeSeconds);
    }
  }

  static boolean isMobVisible(Settings.Minimap cfg, int kind) {
    switch (kind) {
      case MapRadarModels.MOB_TNT:
      case MapRadarModels.MOB_CRYSTAL:
        return !FeaturePolicy.isBlocked("minimap.explosives") && cfg.showExplosives;
      case MapRadarModels.MOB_BOSS:
      case MapRadarModels.MOB_HOSTILE:
        return !FeaturePolicy.isBlocked("minimap.mobs") && cfg.showHostileMobs;
      case MapRadarModels.MOB_NEUTRAL:
        return !FeaturePolicy.isBlocked("minimap.mobs") && cfg.showNeutralMobs;
      case MapRadarModels.MOB_PASSIVE:
        return !FeaturePolicy.isBlocked("minimap.mobs") && cfg.showPassiveMobs;
      case MapRadarModels.MOB_OTHER:
        return !FeaturePolicy.isBlocked("minimap.mobs") && cfg.showOtherEntities;
      default:
        return !FeaturePolicy.isBlocked("minimap.mobs") && cfg.showOtherEntities;
    }
  }

  private static void drawItems(Canvas canvas, Settings.Minimap cfg,
      List<MapRadarModels.ItemEntry> items, float cx, float cy, float innerSize, String shape,
      double playerX, double playerZ, float halfBlocks, float cosA, float sinA, float s,
      float alpha) {
    if (FeaturePolicy.isBlocked("minimap.items") || !cfg.showItems || items == null || items.isEmpty()) return;
    float halfPx = innerSize * 0.5f;
    boolean icons = !"dots".equals(cfg.itemStyle);
    float densityFactor = clamp(innerSize / 145f, 0.74f, 0.95f);
    float iconSize = Math.max(7.5f, 10.5f * s * cfg.itemScale * densityFactor);

    for (int i = 0; i < items.size(); i++) {
      MapRadarModels.ItemEntry item = items.get(i);
      if (cfg.onlyValuableItems && !item.valuable) continue;
      float dx = (float) ((item.x - playerX) / halfBlocks);
      float dz = (float) ((item.z - playerZ) / halfBlocks);
      float nx = dx * cosA + dz * sinA;
      float ny = -dx * sinA + dz * cosA;
      if (!isInsideShape(nx, ny, shape, 0.90f)) continue;
      float sx = cx + nx * halfPx;
      float sy = cy + ny * halfPx;

      if (!icons) {
        float r = (item.valuable ? 2.5f : 1.75f) * s * cfg.itemScale;
        int col = item.valuable ? 0xFFFACC15 : 0xFF93C5FD;
        canvas.circle(sx, sy, r + 0.65f * s, Colors.alpha(0xCC080B12, alpha));
        canvas.circle(sx, sy, r, Colors.alpha(col, alpha));
        continue;
      }

      if (cfg.itemBackground) {
        float backR = iconSize * 0.56f;
        if (item.valuable) {
          canvas.circle(sx, sy, backR + 1.1f * s, Colors.alpha(0x88FACC15, alpha));
        }
        canvas.circle(sx, sy, backR, Colors.alpha(0xB8090D16, alpha));
      }

      float ix = sx - iconSize * 0.5f;
      float iy = sy - iconSize * 0.5f;
      if (item.cacheSlot >= 400) {
        canvas.inventoryItem(item.asInventorySlot(), ix, iy, iconSize, alpha);
      } else if (!item.itemId.isEmpty()) {
        canvas.item(item.itemId, ix, iy, iconSize, alpha);
      }
      if (item.totalCount > 1 || item.mergedStacks > 1) {
        String badge;
        if (item.mergedStacks > 1 && item.totalCount <= item.mergedStacks) {
          badge = item.mergedStacks < PLUS_BADGES.length
              ? PLUS_BADGES[item.mergedStacks]
              : ("+" + item.mergedStacks);
        } else {
          badge = item.totalCount < MULT_BADGES.length
              ? MULT_BADGES[item.totalCount]
              : ("×" + item.totalCount);
        }
        float fs = 4.6f * s * cfg.itemScale;
        float bw = canvas.textWidthFamily("studio-sans-medium", badge, fs, true, 0f);
        float bx = sx + iconSize * 0.12f;
        float by = sy + iconSize * 0.12f;
        canvas.roundedRect(bx - 1.2f * s, by - 0.6f * s, bw + 2.4f * s, fs + 1.2f * s,
            1.6f * s, Colors.alpha(0xD6080B12, alpha));
        canvas.textFamily("studio-sans-medium", badge, bx, by, fs,
            Colors.alpha(item.valuable ? 0xFFFDE047 : 0xFFF1F5F9, alpha), true, 0f);
      }
    }
  }

  private static void drawPlayers(Canvas canvas, Settings.Minimap cfg, Theme theme,
      List<MapRadarModels.PlayerEntry> players, float cx, float cy, float innerSize, String shape,
      double playerX, double playerZ, float halfBlocks, float cosA, float sinA, float s,
      float alpha, double timeSeconds) {
    if (FeaturePolicy.isBlocked("minimap.players") || !cfg.showPlayers || players == null || players.isEmpty()) return;
    float halfPx = innerSize * 0.5f;
    float densityFactor = clamp(innerSize / 140f, 0.78f, 0.96f);
    float headSize = 10.2f * s * cfg.playerScale * densityFactor;
    float invisPulse = 0.68f + 0.32f * (float) Math.sin(timeSeconds * 5.0);

    for (int i = 0; i < players.size(); i++) {
      MapRadarModels.PlayerEntry p = players.get(i);
      if (p.self) continue;
      if (p.invisible && (!cfg.showInvisiblePlayers || FeaturePolicy.isBlocked("minimap.invisibles"))) continue;

      float dx = (float) ((p.x - playerX) / halfBlocks);
      float dz = (float) ((p.z - playerZ) / halfBlocks);
      float nx = dx * cosA + dz * sinA;
      float ny = -dx * sinA + dz * cosA;
      if (!isInsideShape(nx, ny, shape, 0.90f)) continue;
      float sx = cx + nx * halfPx;
      float sy = cy + ny * halfPx;

      float pAlpha = p.invisible ? alpha * 0.80f : (p.sneaking ? alpha * 0.85f : alpha);
      float snappedHead = Math.max(7.5f, headSize);
      float hx = sx - snappedHead * 0.5f;
      float hy = sy - snappedHead * 0.5f;
      float framePad = 1.0f;

      boolean useHead = cfg.showPlayerHeads && !p.skinTextureId.isEmpty();
      float markerHalf;
      if (useHead) {
        markerHalf = snappedHead * 0.5f + framePad;
        if (p.invisible) {
          canvas.roundedRect(hx - framePad - 1.2f * s, hy - framePad - 1.2f * s,
              snappedHead + (framePad + 1.2f * s) * 2f, snappedHead + (framePad + 1.2f * s) * 2f,
              2.2f * s, Colors.alpha(0x8838BDF8, alpha * invisPulse));
        }
        canvas.roundedRect(hx - framePad, hy - framePad, snappedHead + framePad * 2f,
            snappedHead + framePad * 2f, 1.2f,
            Colors.alpha(p.invisible ? 0xEE0C2238 : 0xEE090D16, alpha));
        boolean drawn = canvas.skinHead(p.skinTextureId, hx, hy, snappedHead, 0f, pAlpha,
            p.invisible ? 0xB8E8FF : 0xFFFFFF);
        if (!drawn) {
          canvas.roundedRect(hx, hy, snappedHead, snappedHead, 0.8f,
              Colors.alpha(p.invisible ? 0xFF38BDF8 : (theme.accent | 0xFF000000), pAlpha));
        }
        canvas.strokeRoundedRect(hx - framePad, hy - framePad, snappedHead + framePad * 2f,
            snappedHead + framePad * 2f, 1.2f, 0.7f,
            Colors.alpha(p.invisible ? 0xFF67E8F9 : 0xB8FFFFFF, alpha));
      } else {
        float r = 3.1f * s * cfg.playerScale;
        markerHalf = r + 0.8f * s;
        if (p.invisible) {
          canvas.circle(sx, sy, r + 1.8f * s, Colors.alpha(0x6638BDF8, alpha * invisPulse));
        }
        canvas.circle(sx, sy, r + 0.8f * s, Colors.alpha(0xE6090D16, alpha));
        canvas.circle(sx, sy, r,
            Colors.alpha(p.invisible ? 0xFF38BDF8 : 0xFFF8FAFC, pAlpha));
      }

      if (cfg.showPlayerNames && !p.name.isEmpty()) {
        float nfs = 4.6f * s * Math.max(0.8f, Math.min(1.2f, cfg.playerScale));
        String label = p.name.length() > 14 ? p.name.substring(0, 14) : p.name;
        float nw = canvas.textWidthFamily("studio-sans-medium", label, nfs, false, 0f);
        float pillW = nw + 3.4f * s;
        float pillH = nfs + 1.6f * s;
        float px = sx - pillW * 0.5f;
        float py = sy + markerHalf + 1.1f * s;
        canvas.roundedRect(px, py, pillW, pillH, 1.8f * s,
            Colors.alpha(p.invisible ? 0xC80A233A : 0xB8080B12, alpha));
        canvas.textFamily("studio-sans-medium", label, px + 1.7f * s, py + 0.75f * s, nfs,
            Colors.alpha(p.invisible ? 0xFF7DD3FC : 0xFFF8FAFC, alpha), false, 0f);
      }
    }
  }

  private static void drawWaypoints(Canvas canvas, Settings.Minimap cfg, List<Waypoint> points,
      String worldId, String dimension, float cx, float cy, float innerSize, String shape,
      double playerX, double playerZ, float halfBlocks, float cosA, float sinA, float s,
      float alpha) {
    float halfPx = innerSize * 0.5f;
    float[] pt = SCRATCH_PT.get();
    for (int i = 0; i < points.size(); i++) {
      Waypoint wp = points.get(i);
      if (wp == null || !wp.visible) continue;
      if (dimension != null && !dimension.isEmpty() && wp.dimension != null
          && !dimension.equalsIgnoreCase(wp.dimension)) {
        continue;
      }
      if (worldId != null && !worldId.isEmpty() && !wp.matchesWorld(worldId)) continue;

      float dx = (float) ((wp.x - playerX) / halfBlocks);
      float dz = (float) ((wp.z - playerZ) / halfBlocks);
      float nx = dx * cosA + dz * sinA;
      float ny = -dx * sinA + dz * cosA;
      boolean inside = isInsideShape(nx, ny, shape, 0.92f);
      if (!inside && !cfg.offscreenWaypoints) continue;

      float px, py;
      if (inside) {
        px = cx + nx * halfPx;
        py = cy + ny * halfPx;
      } else {
        clampToShapeRim(nx, ny, shape, halfPx - 1.2f * s, 0f, pt);
        px = cx + pt[0];
        py = cy + pt[1];
      }

      int wpCol = wp.death ? 0xFFEF4444 : (wp.color | 0xFF000000);
      float badgeSize = (inside ? 8.2f : 6.8f) * s;
      float halfB = badgeSize * 0.5f;
      float r = wp.death ? halfB : 2.3f * s;
      canvas.roundedRect(px - halfB - 0.8f * s, py - halfB - 0.8f * s, badgeSize + 1.6f * s,
          badgeSize + 1.6f * s, r + 0.6f * s, Colors.alpha(0xDC080B12, alpha));
      canvas.roundedRect(px - halfB, py - halfB, badgeSize, badgeSize, r,
          Colors.alpha(wpCol, alpha));
      canvas.strokeRoundedRect(px - halfB, py - halfB, badgeSize, badgeSize, r,
          Math.max(0.55f, 0.65f * s), Colors.alpha(0xD8FFFFFF, alpha));
      if (wp.death) {
        drawDeathCross(canvas, px, py, badgeSize * 0.24f, Math.max(0.9f, 1.05f * s),
            Colors.alpha(0xFFFFFFFF, alpha));
      } else if (wp.temp) {
        drawStarGlyph(canvas, px, py, badgeSize * 0.34f, Colors.alpha(0xFFFFFFFF, alpha));
      } else {
        String glyph = initialOf(wp.displayName());
        float gfs = (inside ? 5.2f : 4.5f) * s;
        float gw = canvas.textWidthFamily("studio-sans-medium", glyph, gfs, true, 0f);
        canvas.textFamily("studio-sans-medium", glyph, px - gw * 0.5f, py - gfs * 0.52f, gfs,
            Colors.alpha(0xFFFFFFFF, alpha), true, 0f);
      }
    }
  }

  private static void drawCompass(Canvas canvas, float cx, float cy, float outerHalf, String shape,
      float outerRadius, float cosA, float sinA, float s, float alpha) {
    float fsN = 5.2f * s;
    float fsOther = 4.6f * s;
    float rimOffset = outerHalf - 4.4f * s;
    float[] pt = SCRATCH_PT.get();
    for (int i = 0; i < 4; i++) {
      float nx = COMPASS_WX[i] * cosA + COMPASS_WZ[i] * sinA;
      float ny = -COMPASS_WX[i] * sinA + COMPASS_WZ[i] * cosA;
      clampToShapeRim(nx, ny, shape, rimOffset, Math.max(0f, outerRadius - 3.0f * s), pt);
      float px = cx + pt[0];
      float py = cy + pt[1];
      boolean north = i == 0;
      float pill = (north ? 7.4f : 6.4f) * s;
      float halfP = pill * 0.5f;
      float rad = north ? 2.4f * s : 2.1f * s;
      canvas.roundedRect(px - halfP, py - halfP, pill, pill, rad,
          Colors.alpha(north ? 0xCC180A0D : 0xA80A0D14, alpha));
      if (north) {
        canvas.strokeRoundedRect(px - halfP, py - halfP, pill, pill, rad,
            Math.max(0.5f, 0.55f * s), Colors.alpha(0x88F87171, alpha));
      } else {
        canvas.strokeRoundedRect(px - halfP, py - halfP, pill, pill, rad,
            Math.max(0.45f, 0.5f * s), Colors.alpha(0x28FFFFFF, alpha));
      }
      float fs = north ? fsN : fsOther;
      float tw = canvas.textWidthFamily("studio-sans-medium", COMPASS_LABELS[i], fs, true, 0f);
      canvas.textFamily("studio-sans-medium", COMPASS_LABELS[i], px - tw * 0.5f, py - fs * 0.48f, fs,
          Colors.alpha(north ? 0xFFF87171 : 0xD8E2E8F0, alpha), true, 0f);
    }
  }

  public static void drawSelfMarker(Canvas canvas, String style, float cx, float cy, float size,
      float angleRad, int fillArgb, int accentArgb, String selfSkinId, float alpha) {
    String st = style == null ? "classic" : style;
    if ("head".equals(st)) {
      drawSelfHeadMarker(canvas, cx, cy, size, angleRad, fillArgb, accentArgb, selfSkinId, alpha);
      return;
    }
    if ("dot".equals(st)) {
      drawSelfDotConeMarker(canvas, cx, cy, size, angleRad, fillArgb, accentArgb, alpha);
      return;
    }
    if ("pointer".equals(st)) {
      drawSelfPointerMarker(canvas, cx, cy, size, angleRad, fillArgb, accentArgb, alpha);
      return;
    }
    if ("modern".equals(st)) {
      drawSelfModernDeltaMarker(canvas, cx, cy, size, angleRad, fillArgb, accentArgb, alpha);
      return;
    }
    drawPlayerChevron(canvas, cx, cy, size, angleRad, fillArgb, accentArgb, alpha);
  }

  public static void drawPlayerChevron(Canvas canvas, float cx, float cy, float size,
      float angleRad, int fillArgb, int accentArgb, float alpha) {
    float cos = (float) Math.cos(angleRad);
    float sin = (float) Math.sin(angleRad);

    float shY = cy + size * 0.16f;
    fillChevronShape(canvas, cx, shY, size * 1.32f, 0.84f, 0.80f, 0.24f, cos, sin,
        Colors.alpha(0x55000000, alpha), Colors.alpha(0x55000000, alpha));

    fillChevronShape(canvas, cx, cy, size * 1.24f, 0.82f, 0.78f, 0.26f, cos, sin,
        Colors.alpha(0xEC06080E, alpha), Colors.alpha(0xEC06080E, alpha));

    int leftCol = Colors.alpha(fillArgb, alpha);
    int rightCol = Colors.alpha(Colors.mix(fillArgb, accentArgb, 0.34f), alpha);
    fillChevronShape(canvas, cx, cy, size, 0.74f, 0.70f, 0.28f, cos, sin, leftCol, rightCol);

    float tipX = cx + sin * (size * 0.82f);
    float tipY = cy - cos * (size * 0.82f);
    float midX = cx - sin * (size * 0.12f);
    float midY = cy + cos * (size * 0.12f);
    canvas.line(tipX, tipY, midX, midY, Math.max(0.55f, size * 0.10f),
        Colors.alpha(0x88FFFFFF, alpha));
  }

  private static void drawSelfModernDeltaMarker(Canvas canvas, float cx, float cy, float size,
      float angleRad, int fillArgb, int accentArgb, float alpha) {
    float cos = (float) Math.cos(angleRad);
    float sin = (float) Math.sin(angleRad);

    canvas.circle(cx, cy, size * 0.88f, Colors.alpha(accentArgb, alpha * 0.22f));
    fillChevronShape(canvas, cx, cy + size * 0.10f, size * 1.30f, 0.96f, 0.64f, 0.08f, cos, sin,
        Colors.alpha(0x50000000, alpha), Colors.alpha(0x50000000, alpha));
    fillChevronShape(canvas, cx, cy, size * 1.24f, 0.94f, 0.62f, 0.10f, cos, sin,
        Colors.alpha(0xF0070910, alpha), Colors.alpha(0xF0070910, alpha));
    int wingL = Colors.alpha(Colors.mix(fillArgb, accentArgb, 0.15f), alpha);
    int wingR = Colors.alpha(Colors.mix(fillArgb, accentArgb, 0.55f), alpha);
    fillChevronShape(canvas, cx, cy, size * 1.04f, 0.88f, 0.56f, 0.12f, cos, sin, wingL, wingR);
    fillChevronShape(canvas, cx, cy, size * 0.58f, 0.72f, 0.48f, 0.08f, cos, sin,
        Colors.alpha(0xFFFFFFFF, alpha), Colors.alpha(0xE6FFFFFF, alpha));
  }

  private static void drawSelfPointerMarker(Canvas canvas, float cx, float cy, float size,
      float angleRad, int fillArgb, int accentArgb, float alpha) {
    float cos = (float) Math.cos(angleRad);
    float sin = (float) Math.sin(angleRad);

    float tipX = cx + sin * (size * 1.38f), tipY = cy - cos * (size * 1.38f);
    float tailX = cx - sin * (size * 0.92f), tailY = cy + cos * (size * 0.92f);
    float side = size * 0.52f;
    float lx = cx - cos * side, ly = cy - sin * side;
    float rx = cx + cos * side, ry = cy + sin * side;

    float bTipX = cx + sin * (size * 1.60f), bTipY = cy - cos * (size * 1.60f);
    float bTailX = cx - sin * (size * 1.12f), bTailY = cy + cos * (size * 1.12f);
    float bSide = size * 0.70f;
    float blx = cx - cos * bSide, bly = cy - sin * bSide;
    float brx = cx + cos * bSide, bry = cy + sin * bSide;
    int dark = Colors.alpha(0xEC06080E, alpha);
    canvas.fillPolygon(cx, cy, new float[] {bTipX, bTipY, brx, bry, bTailX, bTailY, blx, bly}, dark);

    canvas.fillPolygon(cx, cy, new float[] {tipX, tipY, rx, ry, tailX, tailY, lx, ly},
        Colors.alpha(fillArgb, alpha));
    canvas.fillPolygon((tipX + rx + cx) / 3f, (tipY + ry + cy) / 3f,
        new float[] {tipX, tipY, rx, ry, cx, cy},
        Colors.alpha(Colors.mix(fillArgb, accentArgb, 0.35f), alpha));
    int tailCol = Colors.alpha(Colors.mix(0xFF1E293B, accentArgb, 0.45f), alpha);
    canvas.fillPolygon((tailX + rx + lx) / 3f, (tailY + ry + ly) / 3f,
        new float[] {tailX, tailY, lx, ly, rx, ry}, tailCol);
    canvas.circle(cx, cy, size * 0.30f, Colors.alpha(0xFF0A0D14, alpha));
    canvas.circle(cx, cy, size * 0.18f, Colors.alpha(0xFFFFFFFF, alpha));
  }

  private static void drawSelfHeadMarker(Canvas canvas, float cx, float cy, float size,
      float angleRad, int fillArgb, int accentArgb, String selfSkinId, float alpha) {
    float cos = (float) Math.cos(angleRad);
    float sin = (float) Math.sin(angleRad);
    float head = size * 1.85f;
    float r = head * 0.5f;

    float tipD = r + size * 0.85f;
    float baseD = r + size * 0.10f;
    float wing = size * 0.46f;
    float tx = cx + sin * tipD, ty = cy - cos * tipD;
    float bx = cx + sin * baseD, by = cy - cos * baseD;
    float lx = bx - cos * wing, ly = by - sin * wing;
    float rx = bx + cos * wing, ry = by + sin * wing;
    float btx = cx + sin * (tipD + 1.1f), bty = cy - cos * (tipD + 1.1f);
    float blx = lx - cos * 0.9f, bly = ly - sin * 0.9f;
    float brx = rx + cos * 0.9f, bry = ry + sin * 0.9f;
    canvas.fillPolygon((btx + blx + brx) / 3f, (bty + bly + bry) / 3f,
        new float[] {btx, bty, brx, bry, blx, bly}, Colors.alpha(0xE807090F, alpha));
    canvas.fillPolygon((tx + lx + rx) / 3f, (ty + ly + ry) / 3f,
        new float[] {tx, ty, rx, ry, lx, ly}, Colors.alpha(fillArgb, alpha));

    float pad = Math.max(0.9f, size * 0.22f);
    float rad = Math.max(1.8f, head * 0.28f);
    canvas.roundedRect(cx - r - pad, cy - r - pad, head + pad * 2f, head + pad * 2f, rad + pad,
        Colors.alpha(0xEE080B12, alpha));
    boolean drawn = selfSkinId != null && !selfSkinId.isEmpty()
        && canvas.skinHead(selfSkinId, cx - r, cy - r, head, rad, alpha, 0xFFFFFF);
    if (!drawn) {
      canvas.roundedRect(cx - r, cy - r, head, head, rad, Colors.alpha(accentArgb, alpha));
    }
    canvas.strokeRoundedRect(cx - r - pad, cy - r - pad, head + pad * 2f, head + pad * 2f,
        rad + pad, Math.max(0.75f, size * 0.16f), Colors.alpha(fillArgb, alpha * 0.92f));
  }

  private static void drawSelfDotConeMarker(Canvas canvas, float cx, float cy, float size,
      float angleRad, int fillArgb, int accentArgb, float alpha) {
    float coneLen = size * 2.55f;
    float halfSpread = 0.52f;
    int steps = 16;
    int coneCol = Colors.alpha(Colors.mix(fillArgb, accentArgb, 0.35f), alpha * 0.28f);
    int innerCone = Colors.alpha(fillArgb, alpha * 0.18f);
    float[] outerPts = new float[(steps + 2) * 2];
    float[] innerPts = new float[(steps + 2) * 2];
    outerPts[0] = cx;
    outerPts[1] = cy;
    innerPts[0] = cx;
    innerPts[1] = cy;
    for (int i = 0; i <= steps; i++) {
      float a = angleRad - halfSpread + (2f * halfSpread * i) / steps;
      float sa = (float) Math.sin(a), ca = (float) Math.cos(a);
      outerPts[(i + 1) * 2] = cx + sa * coneLen;
      outerPts[(i + 1) * 2 + 1] = cy - ca * coneLen;
      innerPts[(i + 1) * 2] = cx + sa * (coneLen * 0.55f);
      innerPts[(i + 1) * 2 + 1] = cy - ca * (coneLen * 0.55f);
    }
    canvas.fillPolygon(cx, cy, outerPts, coneCol);
    canvas.fillPolygon(cx, cy, innerPts, innerCone);
    float dotR = size * 0.62f;
    canvas.circle(cx, cy, dotR + size * 0.34f, Colors.alpha(accentArgb, alpha * 0.32f));
    canvas.circle(cx, cy, dotR + size * 0.22f, Colors.alpha(0xEC06080E, alpha));
    canvas.circle(cx, cy, dotR, Colors.alpha(fillArgb, alpha));
    canvas.circle(cx, cy, dotR * 0.42f, Colors.alpha(0xFFFFFFFF, alpha));
  }

  private static void fillChevronShape(Canvas canvas, float cx, float cy, float size, float wingW,
      float wingBack, float notchBack, float cos, float sin, int leftCol, int rightCol) {
    float tipX = cx + sin * size;
    float tipY = cy - cos * size;
    float leftX = cx + (-wingW * cos - wingBack * sin) * size;
    float leftY = cy + (-wingW * sin + wingBack * cos) * size;
    float rightX = cx + (wingW * cos - wingBack * sin) * size;
    float rightY = cy + (wingW * sin + wingBack * cos) * size;
    float notchX = cx - sin * (size * notchBack);
    float notchY = cy + cos * (size * notchBack);
    canvas.fillPolygon(
        notchX, notchY, new float[] {tipX, tipY, rightX, rightY, notchX, notchY, leftX, leftY},
        leftCol);
    if (rightCol != leftCol) {
      float rx = (tipX + rightX + notchX) / 3f, ry = (tipY + rightY + notchY) / 3f;
      canvas.fillPolygon(rx, ry, new float[] {tipX, tipY, rightX, rightY, notchX, notchY}, rightCol);
    }
  }

  static void drawDeathCross(Canvas canvas, float cx, float cy, float half, float stroke, int argb) {
    canvas.line(cx - half, cy - half, cx + half, cy + half, stroke, argb);
    canvas.line(cx + half, cy - half, cx - half, cy + half, stroke, argb);
  }

  static void drawStarGlyph(Canvas canvas, float cx, float cy, float outerR, int argb) {
    float innerR = outerR * 0.36f;
    float[] pts = {
        cx, cy - outerR,
        cx + innerR, cy - innerR,
        cx + outerR, cy,
        cx + innerR, cy + innerR,
        cx, cy + outerR,
        cx - innerR, cy + innerR,
        cx - outerR, cy,
        cx - innerR, cy - innerR
    };
    canvas.fillPolygon(cx, cy, pts, argb);
  }

  private static String findSelfSkin(MapRadarModels.RadarSnapshot radar) {
    if (radar == null || radar.players == null) return "";
    for (int i = 0; i < radar.players.size(); i++) {
      MapRadarModels.PlayerEntry p = radar.players.get(i);
      if (p != null && p.self) return p.skinTextureId;
    }
    return "";
  }

  private static void drawDemoEntities(Canvas canvas, Settings.Minimap cfg, Theme theme, float cx,
      float cy, float innerSize, float s, float alpha, double timeSeconds) {
    float r = innerSize * 0.26f;
    if (!FeaturePolicy.isBlocked("minimap.players") && cfg.showPlayers) {
      float px = cx + r * 0.75f, py = cy - r * 0.55f;
      float hs = 9f * s * cfg.playerScale;
      canvas.roundedRect(px - hs * 0.5f - 1f, py - hs * 0.5f - 1f, hs + 2f, hs + 2f, 2.5f * s,
          Colors.alpha(0xE0090D16, alpha));
      canvas.roundedRect(px - hs * 0.5f, py - hs * 0.5f, hs, hs, 2f * s,
          Colors.alpha(theme.accent | 0xFF000000, alpha));
      if (cfg.showPlayerNames) {
        float fs = 5f * s;
        String n = "Alex";
        float nw = canvas.textWidthFamily("studio-sans-medium", n, fs, false, 0f);
        canvas.roundedRect(px - nw * 0.5f - 2f * s, py + hs * 0.5f + 1.5f * s, nw + 4f * s,
            fs + 1.8f * s, 1.8f * s, Colors.alpha(0xCC090D16, alpha));
        canvas.textFamily("studio-sans-medium", n, px - nw * 0.5f, py + hs * 0.5f + 2.3f * s, fs,
            Colors.alpha(0xFFF8FAFC, alpha), false, 0f);
      }
      if (!FeaturePolicy.isBlocked("minimap.invisibles") && cfg.showInvisiblePlayers) {
        float ix = cx - r * 0.72f, iy = cy + r * 0.42f;
        canvas.roundedRect(ix - hs * 0.5f - 1.5f, iy - hs * 0.5f - 1.5f, hs + 3f, hs + 3f,
            3f * s, Colors.alpha(0x8838BDF8, alpha));
        canvas.roundedRect(ix - hs * 0.5f, iy - hs * 0.5f, hs, hs, 2f * s,
            Colors.alpha(0xBB67E8F9, alpha));
      }
    }
    if (!FeaturePolicy.isBlocked("minimap.mobs") && cfg.showHostileMobs) {
      MapRadarModels.MobEntry creeper = new MapRadarModels.MobEntry(
          1, MapRadarModels.MOB_HOSTILE, "minecraft:creeper", "Creeper", false, 0, 68, 0, 0f);
      MapEntityIconPainter.drawEntity(canvas, creeper, cx + r * 0.65f, cy + r * 0.58f,
          s * cfg.mobScale, alpha, !"dots".equals(cfg.mobIconStyle), false, 68, timeSeconds);
    }
    if (!FeaturePolicy.isBlocked("minimap.items") && cfg.showItems) {
      float lx = cx - r * 0.45f, ly = cy - r * 0.65f;
      if (cfg.itemBackground) {
        canvas.circle(lx, ly, 4.2f * s, Colors.alpha(0x88FACC15, alpha));
        canvas.circle(lx, ly, 3.0f * s, Colors.alpha(0xFF0B0F18, alpha));
      }
      canvas.circle(lx, ly, 2.0f * s, Colors.alpha(0xFFFDE047, alpha));
    }
  }

  static String normalizeShape(String s) {
    if ("circle".equals(s) || "sharp".equals(s) || "diamond".equals(s) || "hexagon".equals(s)
        || "octagon".equals(s)) {
      return s;
    }
    return "square";
  }

  static int polygonSides(String shape) {
    if ("diamond".equals(shape)) return 4;
    if ("hexagon".equals(shape)) return 6;
    if ("octagon".equals(shape)) return 8;
    return 0;
  }

  private static void drawPolygonFill(
      Canvas canvas, float cx, float cy, float half, int sides, int argb) {
    float[] pts = TexturedMesh.polygonContour(cx, cy, half, half, sides);
    canvas.fillPolygon(cx, cy, pts, argb);
  }

  private static void drawPolygonStroke(
      Canvas canvas, float cx, float cy, float half, int sides, float thickness, int argb) {
    float[] pts = TexturedMesh.polygonContour(cx, cy, half, half, sides);
    int n = pts.length / 2;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      canvas.line(
          pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1], thickness, argb);
    }
  }

  static boolean isInsideShape(float nx, float ny, String shape, float limit) {
    if ("circle".equals(shape)) return nx * nx + ny * ny <= limit * limit;
    if ("diamond".equals(shape)) return Math.abs(nx) + Math.abs(ny) <= limit;
    if ("hexagon".equals(shape)) {
      float ax = Math.abs(nx), ay = Math.abs(ny);
      return ay <= 0.90f * limit && (ax + ay * 0.533f) <= limit;
    }
    if ("octagon".equals(shape)) {
      float ax = Math.abs(nx), ay = Math.abs(ny);
      return ax <= limit && ay <= limit && (ax + ay) <= 1.56f * limit;
    }
    return Math.abs(nx) <= limit && Math.abs(ny) <= limit;
  }

  static void clampToShapeRim(
      float nx, float ny, String shape, float rimRadius, float cornerRadius, float[] out) {
    float ax = Math.abs(nx), ay = Math.abs(ny);
    if ("circle".equals(shape)) {
      float len = (float) Math.sqrt(nx * nx + ny * ny);
      float inv = len > 1e-4f ? rimRadius / len : 0f;
      out[0] = nx * inv;
      out[1] = ny * inv;
      return;
    }
    if ("diamond".equals(shape)) {
      float sum = ax + ay;
      float inv = sum > 1e-4f ? rimRadius / sum : 0f;
      out[0] = nx * inv;
      out[1] = ny * inv;
      return;
    }
    if ("hexagon".equals(shape)) {
      float metric = Math.max(ay / 0.90f, ax + ay * 0.533f);
      float inv = metric > 1e-4f ? rimRadius / metric : 0f;
      out[0] = nx * inv;
      out[1] = ny * inv;
      return;
    }
    if ("octagon".equals(shape)) {
      float metric = Math.max(Math.max(ax, ay), (ax + ay) / 1.56f);
      float inv = metric > 1e-4f ? rimRadius / metric : 0f;
      out[0] = nx * inv;
      out[1] = ny * inv;
      return;
    }
    float maxAxis = Math.max(ax, ay);
    float inv = maxAxis > 1e-4f ? rimRadius / maxAxis : 0f;
    float sx = nx * inv;
    float sy = ny * inv;
    if ("square".equals(shape) && cornerRadius > 1f) {
      float limit = rimRadius - cornerRadius * 0.35f;
      if (Math.abs(sx) > limit && Math.abs(sy) > limit) {
        float ox = Math.abs(sx) - limit;
        float oy = Math.abs(sy) - limit;
        float d = (float) Math.sqrt(ox * ox + oy * oy);
        float cr = cornerRadius * 0.35f;
        if (d > cr && d > 1e-4f) {
          sx = Math.signum(sx) * (limit + ox / d * cr);
          sy = Math.signum(sy) * (limit + oy / d * cr);
        }
      }
    }
    out[0] = sx;
    out[1] = sy;
  }

  private static String initialOf(String text) {
    if (text == null) return "W";
    int len = text.length();
    int idx = 0;
    while (idx < len && text.charAt(idx) <= ' ') idx++;
    if (idx >= len) return "W";
    char ch = text.charAt(idx);
    if (ch < 128) return ASCII_UPPER[ch];
    int cp = text.codePointAt(idx);
    return new String(Character.toChars(Character.toUpperCase(cp)));
  }

  private static float clamp(float v, float min, float max) {
    return Math.max(min, Math.min(max, v));
  }
}
