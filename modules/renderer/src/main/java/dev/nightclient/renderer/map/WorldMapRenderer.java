package dev.nightclient.renderer.map;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Colors;
import dev.nightclient.api.MapRadarModels;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Theme;
import dev.nightclient.config.Waypoint;
import dev.nightclient.i18n.I18n;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorldMapRenderer {
  private WorldMapRenderer() {}

  public static final class HitBox {
    public final String action;
    public final float x, y, w, h;

    public HitBox(String action, float x, float y, float w, float h) {
      this.action = action;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    public boolean contains(float mx, float my) {
      return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }
  }

  public static final class ViewState {
    public double centerX = 0.0;
    public double centerZ = 0.0;
    public float zoom = 1.0f;
    public boolean followPlayer = false;
    public boolean subWorldMenuOpen = false;
    public boolean contextMenuOpen = false;
    public float contextScreenX, contextScreenY;
    public int contextBlockX, contextBlockY, contextBlockZ;
    public String contextWaypointId = "";
    public String hoveredWaypointId = "";
    public boolean confirmClearCache = false;
    public final List<HitBox> hits = new ArrayList<HitBox>();
  }

  public static void render(Canvas canvas, Settings settings, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, ViewState state, int width, int height, float mouseX,
      float mouseY, double timeSeconds) {
    if (FeaturePolicy.isBlocked("worldMap") || canvas == null || settings == null || state == null || width <= 0 || height <= 0) return;
    state.hits.clear();
    state.hoveredWaypointId = "";

    Theme theme = settings.theme != null ? settings.theme : Theme.defaults();
    Settings.WorldMap wm = settings.worldMap != null ? settings.worldMap : new Settings.WorldMap();
    Settings.Minimap mm = settings.minimap != null ? settings.minimap : new Settings.Minimap();
    String lang = I18n.language(settings.language);
    int accent = theme.accent | 0xFF000000;
    int surface = Colors.mix(0xFF0C1018, theme.surface | 0xFF000000, 0.42f);
    int border = Colors.mix(theme.border | 0xFF000000, 0xFFFFFFFF, 0.12f);

    canvas.rect(0, 0, width, height, 0xFF090C12);
    float halfW = width * 0.5f;
    float halfH = height * 0.5f;
    float zoom = Math.max(0.08f, Math.min(8.0f, state.zoom));

    double minWorldX = state.centerX - halfW / zoom;
    double maxWorldX = state.centerX + halfW / zoom;
    double minWorldZ = state.centerZ - halfH / zoom;
    double maxWorldZ = state.centerZ + halfH / zoom;

    int minRx = MapRegionTile.blockToRegion((int) Math.floor(minWorldX)) - 1;
    int maxRx = MapRegionTile.blockToRegion((int) Math.ceil(maxWorldX)) + 1;
    int minRz = MapRegionTile.blockToRegion((int) Math.floor(minWorldZ)) - 1;
    int maxRz = MapRegionTile.blockToRegion((int) Math.ceil(maxWorldZ)) + 1;

    int mip = zoom >= 0.50f ? 0 : (zoom >= 0.25f ? 1 : (zoom >= 0.125f ? 2 : 3));
    boolean nearest = zoom >= 0.85f;
    int rStyle = MapColorShading.parseReliefStyle(mm.reliefStyle);
    float rStrength = mm.reliefStrength;
    String worldPrefix = cache != null ? (Long.toHexString(cache.activeEpoch()) + "_"
        + Integer.toHexString((cache.identity().serverKey() + "/" + cache.identity().subWorldDir()
            + "/" + cache.identity().dimension()).hashCode())) : "demo";

    if (cache != null && cache.identity().isActive()) {
      long frameSerial = cache.beginWorldMapFrame();
      long spanX = (long) maxRx - (long) minRx + 1L;
      long spanZ = (long) maxRz - (long) minRz + 1L;
      if (spanX > 0 && spanZ > 0 && spanX * spanZ <= 64L) {
        for (int rz = minRz; rz <= maxRz; rz++) {
          for (int rx = minRx; rx <= maxRx; rx++) {
            drawRegionTile(canvas, cache, wm, state, rx, rz, halfW, halfH, width, height, zoom,
                mip, nearest, rStyle, rStrength, worldPrefix, frameSerial);
          }
        }
      } else {
        List<Long> keys = cache.allKnownRegionKeys();
        for (int i = 0; i < keys.size(); i++) {
          long k = keys.get(i).longValue();
          int rx = (int) (k >> 32);
          int rz = (int) k;
          if (rx < minRx || rx > maxRx || rz < minRz || rz > maxRz) continue;
          drawRegionTile(canvas, cache, wm, state, rx, rz, halfW, halfH, width, height, zoom,
              mip, nearest, rStyle, rStrength, worldPrefix, frameSerial);
        }
      }
    }

    if (wm.showGrid) {
      drawWorldGrid(canvas, width, height, halfW, halfH, state.centerX, state.centerZ, zoom);
    }

    if (!FeaturePolicy.isBlocked("worldMap.mobs") && (wm.showMobs || wm.showOtherEntities) && radar != null && radar.inWorld
        && radar.mobs != null && zoom >= 0.25f) {
      boolean iconStyle = !"dots".equals(mm.mobIconStyle);
      boolean showNames = mm.showMobNames && zoom >= 0.75f;
      float mobScale = Math.max(0.75f, Math.min(1.6f, 0.95f * (float) Math.sqrt(zoom) * mm.mobScale));
      for (int i = 0; i < radar.mobs.size(); i++) {
        MapRadarModels.MobEntry m = radar.mobs.get(i);
        boolean isOther = m.kind == MapRadarModels.MOB_OTHER;
        if (isOther && !wm.showOtherEntities) continue;
        if (!isOther && !wm.showMobs) continue;
        float sx = (float) (halfW + (m.x - state.centerX) * zoom);
        float sy = (float) (halfH + (m.z - state.centerZ) * zoom);
        if (sx < -24 || sy < -24 || sx > width + 24 || sy > height + 24) continue;
        MapEntityIconPainter.drawEntity(
            canvas, m, sx, sy, mobScale, 1f, iconStyle, showNames, radar.playerY, timeSeconds);
      }
    }

    if (!FeaturePolicy.isBlocked("worldMap.items") && wm.showItems && radar != null && radar.inWorld && radar.items != null && zoom >= 0.25f) {
      float iconSize = Math.max(9f, Math.min(18f, 11.5f * (float) Math.sqrt(zoom)));
      for (int i = 0; i < radar.items.size(); i++) {
        MapRadarModels.ItemEntry item = radar.items.get(i);
        float sx = (float) (halfW + (item.x - state.centerX) * zoom);
        float sy = (float) (halfH + (item.z - state.centerZ) * zoom);
        if (sx < -24 || sy < -24 || sx > width + 24 || sy > height + 24) continue;
        if (mm.itemBackground) {
          float backR = iconSize * 0.58f;
          if (item.valuable) canvas.circle(sx, sy, backR + 1.5f, 0x99FACC15);
          canvas.circle(sx, sy, backR, 0xCC090D16);
        }
        float ix = sx - iconSize * 0.5f;
        float iy = sy - iconSize * 0.5f;
        if (item.cacheSlot >= 400) {
          canvas.inventoryItem(item.asInventorySlot(), ix, iy, iconSize, 1f);
        } else if (!item.itemId.isEmpty()) {
          canvas.item(item.itemId, ix, iy, iconSize, 1f);
        }
        if (item.totalCount > 1) {
          String count = "×" + item.totalCount;
          float cfs = 6.0f;
          float cw = canvas.textWidthFamily("studio-sans-medium", count, cfs, true, 0f);
          canvas.roundedRect(sx + iconSize * 0.14f - 1.5f, sy + iconSize * 0.14f - 0.8f,
              cw + 3f, cfs + 1.6f, 2f, 0xE0080B12);
          canvas.textFamily("studio-sans-medium", count, sx + iconSize * 0.14f,
              sy + iconSize * 0.14f, cfs, item.valuable ? 0xFFFDE047 : 0xFFF1F5F9, true, 0f);
        }
      }
    }

    if (!FeaturePolicy.isBlocked("waypoints") && wm.showWaypoints && settings.waypoints != null && settings.waypoints.points != null) {
      String worldId = radar != null ? radar.worldId : (cache != null ? cache.identity().worldId() : "");
      String dim = radar != null ? radar.dimension : (cache != null ? cache.identity().dimension() : "");
      for (int i = 0; i < settings.waypoints.points.size(); i++) {
        Waypoint wp = settings.waypoints.points.get(i);
        if (wp == null) continue;
        if (dim != null && !dim.isEmpty() && wp.dimension != null && !dim.equalsIgnoreCase(wp.dimension))
          continue;
        if (worldId != null && !worldId.isEmpty() && !wp.matchesWorld(worldId)) continue;
        float sx = (float) (halfW + (wp.x - state.centerX) * zoom);
        float sy = (float) (halfH + (wp.z - state.centerZ) * zoom);
        if (sx < -60 || sy < -40 || sx > width + 60 || sy > height + 40) continue;

        boolean allowMapHover = !state.contextMenuOpen && !state.subWorldMenuOpen && mouseY > 38f
            && mouseY < height - 34f;
        boolean hovered = allowMapHover && Math.hypot(mouseX - sx, mouseY - sy) <= 13f;
        if (hovered) state.hoveredWaypointId = wp.id;

        float wpAlpha = wp.visible ? 1f : 0.42f;
        int col = wp.death ? 0xFFEF4444 : (wp.color | 0xFF000000);
        float pinSize = hovered ? 15f : 12.5f;
        float halfP = pinSize * 0.5f;
        float r = wp.death ? halfP : 3.6f;

        if (hovered) {
          canvas.roundedRect(sx - halfP - 3f, sy - halfP - 3f, pinSize + 6f, pinSize + 6f,
              r + 3f, Colors.alpha(col, 0.35f));
        }
        canvas.roundedRect(sx - halfP - 1.2f, sy - halfP - 1.2f, pinSize + 2.4f, pinSize + 2.4f,
            r + 1f, Colors.alpha(0xE0070A10, wpAlpha));
        canvas.roundedRect(sx - halfP, sy - halfP, pinSize, pinSize, r, Colors.alpha(col, wpAlpha));
        canvas.strokeRoundedRect(sx - halfP, sy - halfP, pinSize, pinSize, r, 0.9f,
            Colors.alpha(0xE6FFFFFF, wpAlpha));

        if (wp.death) {
          MinimapRenderer.drawDeathCross(canvas, sx, sy, pinSize * 0.24f, 1.25f,
              Colors.alpha(0xFFFFFFFF, wpAlpha));
        } else if (wp.temp) {
          MinimapRenderer.drawStarGlyph(canvas, sx, sy, pinSize * 0.34f,
              Colors.alpha(0xFFFFFFFF, wpAlpha));
        } else {
          String glyph = initialOf(wp.displayName());
          float gfs = 7.0f;
          float gw = canvas.textWidthFamily("studio-sans-medium", glyph, gfs, true, 0f);
          canvas.textFamily("studio-sans-medium", glyph, sx - gw * 0.5f, sy - gfs * 0.52f, gfs,
              Colors.alpha(0xFFFFFFFF, wpAlpha), true, 0f);
        }

        String name = wp.displayName();
        float nfs = 6.8f;
        float nw = canvas.textWidthFamily("studio-sans-medium", name, nfs, hovered, 0f);
        float pillW = nw + 8f;
        float pillH = nfs + 4f;
        float px = sx - pillW * 0.5f;
        float py = sy + halfP + 3f;
        canvas.roundedRect(px, py, pillW, pillH, 3f, Colors.alpha(0xD8090D16, wpAlpha));
        canvas.strokeRoundedRect(px, py, pillW, pillH, 3f, 0.6f,
            Colors.alpha(hovered ? col : border, wpAlpha * 0.8f));
        canvas.textFamily("studio-sans-medium", name, px + 4f, py + 1.8f, nfs,
            Colors.alpha(0xFFF8FAFC, wpAlpha), hovered, 0f);
      }
    }

    if (radar != null && radar.inWorld) {
      if (!FeaturePolicy.isBlocked("worldMap.players") && wm.showPlayers && radar.players != null) {
        float invisPulse = 0.68f + 0.32f * (float) Math.sin(timeSeconds * 5.0);
        for (int i = 0; i < radar.players.size(); i++) {
          MapRadarModels.PlayerEntry p = radar.players.get(i);
          if (p.self) continue;
          if (p.invisible && (!wm.showInvisiblePlayers || FeaturePolicy.isBlocked("worldMap.invisibles"))) continue;
          float sx = (float) (halfW + (p.x - state.centerX) * zoom);
          float sy = (float) (halfH + (p.z - state.centerZ) * zoom);
          if (sx < -30 || sy < -30 || sx > width + 30 || sy > height + 30) continue;
          float hs = 14f;
          float hx = sx - hs * 0.5f, hy = sy - hs * 0.5f;
          if (p.invisible) {
            canvas.roundedRect(hx - 2.5f, hy - 2.5f, hs + 5f, hs + 5f, 3.0f,
                Colors.alpha(0x8838BDF8, invisPulse));
          }
          canvas.rect(hx - 1f, hy - 1f, hs + 2f, hs + 2f,
              p.invisible ? 0xEE0C2238 : 0xEE090D16);
          if (!canvas.skinHead(p.skinTextureId, hx, hy, hs, 0f, p.invisible ? 0.82f : 1f,
                  p.invisible ? 0xB8E8FF : 0xFFFFFF)) {
            canvas.rect(hx, hy, hs, hs, p.invisible ? 0xFF38BDF8 : accent);
          }
          canvas.strokeRoundedRect(hx - 1f, hy - 1f, hs + 2f, hs + 2f, 0.8f, 0.75f,
              p.invisible ? 0xFF67E8F9 : 0xC8FFFFFF);
          if (!p.name.isEmpty()) {
            float nfs = 6.8f;
            float nw = canvas.textWidthFamily("studio-sans-medium", p.name, nfs, false, 0f);
            float pw = nw + 7f, ph = nfs + 3.5f;
            canvas.roundedRect(sx - pw * 0.5f, hy + hs + 2.5f, pw, ph, 2.8f,
                p.invisible ? 0xDC0A233A : 0xD4090D16);
            canvas.textFamily("studio-sans-medium", p.name, sx - nw * 0.5f, hy + hs + 4.1f, nfs,
                p.invisible ? 0xFF7DD3FC : 0xFFF8FAFC, false, 0f);
          }
        }
      }
      float px = (float) (halfW + (radar.playerX - state.centerX) * zoom);
      float py = (float) (halfH + (radar.playerZ - state.centerZ) * zoom);
      if (px >= -20 && py >= -20 && px <= width + 20 && py <= height + 20) {
        String selfSkin = "";
        if (radar.players != null) {
          for (int i = 0; i < radar.players.size(); i++) {
            MapRadarModels.PlayerEntry p = radar.players.get(i);
            if (p.self && p.skinTextureId != null) {
              selfSkin = p.skinTextureId;
              break;
            }
          }
        }
        MinimapRenderer.drawSelfMarker(canvas, mm.arrowStyle, px, py,
            7.0f * Math.max(0.75f, mm.arrowScale), (float) Math.toRadians(radar.playerYaw - 180.0),
            mm.arrowColor | 0xFF000000, accent, selfSkin, 1f);
      }
    }

    drawTopToolbar(
        canvas, wm, cache, radar, state, width, mouseX, mouseY, lang, surface, border, accent);

    if (wm.showCoordinatesBar) {
      drawBottomBar(canvas, cache, radar, state, width, height, mouseX, mouseY, zoom, lang, surface,
          border);
    }

    if (state.subWorldMenuOpen && cache != null) {
      drawSubWorldMenu(canvas, cache, state, mouseX, mouseY, lang, surface, border, accent);
    }

    if (state.contextMenuOpen) {
      drawContextMenu(
          canvas, settings, state, width, height, mouseX, mouseY, lang, surface, border, accent);
    }
  }

  private static void drawRegionTile(Canvas canvas, RegionCacheManager cache, Settings.WorldMap wm,
      ViewState state, int rx, int rz, float halfW, float halfH, int width, int height, float zoom,
      int mip, boolean nearest, int rStyle, float rStrength, String worldPrefix, long frameSerial) {
    float sx0 = (float) (halfW + ((rx << MapRegionTile.REGION_SHIFT) - state.centerX) * zoom);
    float sy0 = (float) (halfH + ((rz << MapRegionTile.REGION_SHIFT) - state.centerZ) * zoom);
    float sx1 = (float) (halfW + (((rx + 1) << MapRegionTile.REGION_SHIFT) - state.centerX) * zoom);
    float sy1 = (float) (halfH + (((rz + 1) << MapRegionTile.REGION_SHIFT) - state.centerZ) * zoom);
    float fullW = sx1 - sx0;
    float fullH = sy1 - sy0;
    if (fullW <= 0f || fullH <= 0f || sx1 <= 0f || sy1 <= 0f || sx0 >= width || sy0 >= height) return;

    float qx0 = Math.max(0f, sx0);
    float qy0 = Math.max(0f, sy0);
    float qx1 = Math.min(width, sx1);
    float qy1 = Math.min(height, sy1);
    float qw = qx1 - qx0;
    float qh = qy1 - qy0;
    if (qw <= 0.001f || qh <= 0.001f) return;
    float u0 = (qx0 - sx0) / fullW;
    float u1 = (qx1 - sx0) / fullW;
    float v0 = (qy0 - sy0) / fullH;
    float v1 = (qy1 - sy0) / fullH;
    float uCenter = (u0 + u1) * 0.5f;
    float vCenter = (v0 + v1) * 0.5f;
    float uHalf = (u1 - u0) * 0.5f;
    float vHalf = (v1 - v0) * 0.5f;

    int[] pixels = null;
    int texSize = 0;
    long rev = 0L;
    int actualMip = mip;

    MapRegionTile tile = cache.getLoadedRegion(rx, rz);
    if (tile != null && tile.hasAnyPopulatedChunk()) {
      tile.markRendered(frameSerial);
      tile.ensureShadingMatches(wm.terrainShading, wm.blockLight, rStyle, rStrength);
      cache.syncOverview(tile);
      pixels = tile.pixelsForMip(mip);
      texSize = MapRegionTile.sizeForMip(mip);
      rev = tile.revision();
    } else {
      RegionCacheManager.OverviewTile ov = cache.getOverviewTile(rx, rz);
      if (mip >= 3 && ov != null
          && ov.matchesShading(wm.terrainShading, wm.blockLight, rStyle, rStrength)) {
        pixels = ov.pixelsForMip(mip);
        texSize = ov.sizeForMip(mip);
        rev = ov.revision;
      } else {
        tile = cache.getOrLoadRegion(rx, rz);
        if (tile != null && tile.hasAnyPopulatedChunk()) {
          tile.markRendered(frameSerial);
          tile.ensureShadingMatches(wm.terrainShading, wm.blockLight, rStyle, rStrength);
          cache.syncOverview(tile);
          pixels = tile.pixelsForMip(mip);
          texSize = MapRegionTile.sizeForMip(mip);
          rev = tile.revision();
        } else if (ov != null) {
          actualMip = mip >= 4 ? 4 : 3;
          pixels = ov.pixelsForMip(actualMip);
          texSize = ov.sizeForMip(actualMip);
          rev = ov.revision;
        }
      }
    }
    if (pixels == null || texSize <= 0) return;

    String key = "wm_" + worldPrefix + "_" + rx + "_" + rz + "_m" + actualMip;
    canvas.mapQuad(key, rev, pixels, texSize, texSize, qx0, qy0, qw, qh, 0f, uCenter, vCenter,
        uHalf, vHalf, 0f, nearest && actualMip == 0, 0xFFFFFFFF);
  }

  private static void drawWorldGrid(Canvas canvas, int width, int height, float halfW, float halfH,
      double centerX, double centerZ, float zoom) {
    int stepBlocks = zoom >= 0.45f ? 16 : (zoom >= 0.14f ? 64 : 512);
    int minX = (int) Math.floor((centerX - halfW / zoom) / stepBlocks) * stepBlocks;
    int maxX = (int) Math.ceil((centerX + halfW / zoom) / stepBlocks) * stepBlocks;
    int minZ = (int) Math.floor((centerZ - halfH / zoom) / stepBlocks) * stepBlocks;
    int maxZ = (int) Math.ceil((centerZ + halfH / zoom) / stepBlocks) * stepBlocks;

    for (int wx = minX; wx <= maxX; wx += stepBlocks) {
      float sx = (float) (halfW + (wx - centerX) * zoom);
      boolean regionLine = (wx & 511) == 0;
      canvas.line(sx, 0, sx, height, regionLine ? 0.9f : 0.5f,
          regionLine ? 0x427C9CFF : 0x1EFFFFFF);
    }
    for (int wz = minZ; wz <= maxZ; wz += stepBlocks) {
      float sy = (float) (halfH + (wz - centerZ) * zoom);
      boolean regionLine = (wz & 511) == 0;
      canvas.line(0, sy, width, sy, regionLine ? 0.9f : 0.5f,
          regionLine ? 0x427C9CFF : 0x1EFFFFFF);
    }
  }

  private static void drawTopToolbar(Canvas canvas, Settings.WorldMap wm, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, ViewState state, int width, float mouseX, float mouseY,
      String lang, int surface, int border, int accent) {
    float barH = 26f;
    float barY = 10f;
    float pad = 8f;

    String worldName = (cache != null && !cache.identity().worldLabel().isEmpty())
        ? cache.identity().worldLabel()
        : (radar != null && !radar.worldLabel.isEmpty() ? radar.worldLabel
                                                        : I18n.text(lang, "worldMap.unknown_world"));
    String dimShort = shortDimName(
        radar != null ? radar.dimension : (cache != null ? cache.identity().dimension() : ""),
        lang);
    String title = worldName + (dimShort.isEmpty() ? "" : (" · " + dimShort));
    float tfs = 7.4f;
    float tw = canvas.textWidthFamily("studio-sans-medium", title, tfs, true, 0f);
    float worldPillW = Math.min(width * 0.42f, tw + 28f);
    float worldPillX = 12f;
    boolean worldHover = mouseX >= worldPillX && mouseX <= worldPillX + worldPillW
        && mouseY >= barY && mouseY <= barY + barH;
    canvas.roundedRect(worldPillX, barY, worldPillW, barH, 8f,
        Colors.alpha(surface, worldHover ? 0.96f : 0.88f));
    canvas.strokeRoundedRect(worldPillX, barY, worldPillW, barH, 8f, 0.75f,
        Colors.alpha(state.subWorldMenuOpen ? accent : border, 0.85f));
    canvas.pushClip(worldPillX + 6f, barY, Math.max(0f, worldPillW - 22f), barH);
    try {
      canvas.textFamily("studio-sans-medium", title, worldPillX + 10f, barY + (barH - tfs) * 0.5f,
          tfs, 0xFFF8FAFC, true, 0f);
    } finally {
      canvas.popClip();
    }
    float chevX = worldPillX + worldPillW - 10f;
    float chevY = barY + barH * 0.5f;
    float chevDir = state.subWorldMenuOpen ? -1f : 1f;
    canvas.fillPolygon(chevX, chevY, new float[] {
        chevX - 2.8f, chevY - chevDir * 1.4f,
        chevX + 2.8f, chevY - chevDir * 1.4f,
        chevX, chevY + chevDir * 1.8f
    }, 0xFF94A3B8);
    state.hits.add(new HitBox("toggle-subworlds", worldPillX, barY, worldPillW, barH));

    String clearLabel = state.confirmClearCache
        ? I18n.text(lang, "worldMap.btn.confirm_clear")
        : I18n.text(lang, "worldMap.btn.clear_cache");
    List<String[]> buttonList = new ArrayList<String[]>(11);
    buttonList.add(new String[] {"center", I18n.text(lang, "worldMap.btn.center"), state.followPlayer ? "1" : "0"});
    buttonList.add(new String[] {"toggle-grid", I18n.text(lang, "worldMap.btn.grid"), wm.showGrid ? "1" : "0"});
    if (!FeaturePolicy.isBlocked("waypoints")) {
      buttonList.add(new String[] {"toggle-waypoints", I18n.text(lang, "worldMap.btn.waypoints"), wm.showWaypoints ? "1" : "0"});
    }
    if (!FeaturePolicy.isBlocked("worldMap.players")) {
      buttonList.add(new String[] {"toggle-players", I18n.text(lang, "worldMap.btn.players"), wm.showPlayers ? "1" : "0"});
      if (!FeaturePolicy.isBlocked("worldMap.invisibles")) {
        buttonList.add(new String[] {"toggle-invis", I18n.text(lang, "worldMap.btn.invisibles"), wm.showInvisiblePlayers ? "1" : "0"});
      }
    }
    if (!FeaturePolicy.isBlocked("worldMap.mobs")) {
      buttonList.add(new String[] {"toggle-mobs", I18n.text(lang, "worldMap.btn.mobs"), wm.showMobs ? "1" : "0"});
    }
    if (!FeaturePolicy.isBlocked("worldMap.items")) {
      buttonList.add(new String[] {"toggle-items", I18n.text(lang, "worldMap.btn.items"), wm.showItems ? "1" : "0"});
    }
    buttonList.add(new String[] {"clear-cache", clearLabel, state.confirmClearCache ? "2" : "0"});
    if (!FeaturePolicy.isBlocked("waypoints")) {
      buttonList.add(new String[] {"open-waypoints", "★", "0"});
    }
    if (!(FeaturePolicy.isBlocked("minimap") && FeaturePolicy.isBlocked("worldMap"))) {
      buttonList.add(new String[] {"open-settings", "⚙", "0"});
    }
    buttonList.add(new String[] {"close", "✕", "0"});
    String[][] buttons = buttonList.toArray(new String[buttonList.size()][]);
    float bfs = 6.8f;
    float gap = 4.5f;
    float minRightStart = worldPillX + worldPillW + 8f;
    float rightEdge = width - 12f;
    for (int i = buttons.length - 1; i >= 0; i--) {
      String btnId = buttons[i][0];
      boolean iconBtn = "open-waypoints".equals(btnId) || "open-settings".equals(btnId) || "close".equals(btnId);
      float bw = iconBtn
          ? 24f
          : canvas.textWidthFamily("studio-sans-medium", buttons[i][1], bfs, false, 0f) + 12f;
      float bx = rightEdge - bw;
      if (bx < minRightStart) break;
      boolean active = "1".equals(buttons[i][2]);
      boolean danger = "2".equals(buttons[i][2]);
      boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= barY
          && mouseY <= barY + barH;
      int bg = danger ? 0xFF7F1D1D
          : (active ? Colors.mix(surface, accent, 0.28f)
                    : (hov ? Colors.mix(surface, 0xFFFFFFFF, 0.08f) : surface));
      int rim = danger ? 0xFFEF4444 : (active ? accent : border);
      canvas.roundedRect(bx, barY, bw, barH, 7.5f, Colors.alpha(bg, 0.92f));
      canvas.strokeRoundedRect(bx, barY, bw, barH, 7.5f, 0.7f,
          Colors.alpha(rim, (active || danger) ? 0.96f : 0.72f));
      int fg = danger ? 0xFFFECACA : (active ? 0xFFFFFFFF : 0xFFCBD5E1);
      if ("open-waypoints".equals(buttons[i][0])) {
        MinimapRenderer.drawStarGlyph(canvas, bx + bw * 0.5f, barY + barH * 0.5f, 4.4f, fg);
      } else if ("open-settings".equals(buttons[i][0])) {
        dev.nightclient.renderer.Icons.draw(
            canvas, "sliders", bx + (bw - 10f) * 0.5f, barY + (barH - 10f) * 0.5f, 10f, fg, 1.6f);
      } else if ("close".equals(buttons[i][0])) {
        MinimapRenderer.drawDeathCross(canvas, bx + bw * 0.5f, barY + barH * 0.5f, 3.2f, 1.25f, fg);
      } else {
        float lw = canvas.textWidthFamily("studio-sans-medium", buttons[i][1], bfs, active || danger, 0f);
        canvas.textFamily("studio-sans-medium", buttons[i][1], bx + (bw - lw) * 0.5f,
            barY + (barH - bfs) * 0.5f, bfs, fg, active || danger, 0f);
      }
      state.hits.add(new HitBox(buttons[i][0], bx, barY, bw, barH));
      rightEdge = bx - gap;
    }
  }

  private static void drawBottomBar(Canvas canvas, RegionCacheManager cache,
      MapRadarModels.RadarSnapshot radar, ViewState state, int width, int height, float mouseX,
      float mouseY, float zoom, String lang, int surface, int border) {
    float halfW = width * 0.5f;
    float halfH = height * 0.5f;
    int cursorBx = (int) Math.floor(state.centerX + (mouseX - halfW) / zoom);
    int cursorBz = (int) Math.floor(state.centerZ + (mouseY - halfH) / zoom);
    short h = cache != null ? cache.heightAt(cursorBx, cursorBz) : MapColorShading.UNEXPLORED_HEIGHT;
    String yText = h == MapColorShading.UNEXPLORED_HEIGHT ? "~" : Short.toString(h);
    String distPart = "";
    if (radar != null && radar.inWorld) {
      int dist = (int) Math.round(Math.hypot(cursorBx - radar.playerX, cursorBz - radar.playerZ));
      distPart = "  ·  " + dist + I18n.text(lang, "hud.unit.distanceSymbol");
    }
    String zoomText = String.format(Locale.ROOT, "%.2fx", zoom);
    String info = "X: " + cursorBx + "  Y: " + yText + "  Z: " + cursorBz
        + "  ·  " + (cursorBx >> 4) + ", " + (cursorBz >> 4)
        + distPart + "  ·  " + zoomText + "  ·  " + I18n.text(lang, "worldMap.hint");

    float fs = 7.0f;
    float tw = canvas.textWidthFamily("studio-sans-medium", info, fs, false, 0f);
    float barW = Math.min(width - 24f, tw + 22f);
    float barH = 22f;
    float barX = (width - barW) * 0.5f;
    float barY = height - barH - 10f;
    canvas.roundedRect(barX, barY, barW, barH, 8f, Colors.alpha(surface, 0.88f));
    canvas.strokeRoundedRect(barX, barY, barW, barH, 8f, 0.7f, Colors.alpha(border, 0.78f));
    canvas.pushClip(barX + 6f, barY, Math.max(0f, barW - 12f), barH);
    try {
      float tx = tw + 12f <= barW ? (barX + (barW - tw) * 0.5f) : (barX + 8f);
      canvas.textFamily("studio-sans-medium", info, tx,
          barY + (barH - fs) * 0.5f, fs, 0xFFE2E8F0, false, 0f);
    } finally {
      canvas.popClip();
    }
  }

  private static void drawSubWorldMenu(Canvas canvas, RegionCacheManager cache, ViewState state,
      float mouseX, float mouseY, String lang, int surface, int border, int accent) {
    List<DiskRegionStore.SubWorldRecord> worlds = cache.identity().knownSubWorlds();
    String activeId = cache.identity().worldId();
    float x = 12f, y = 40f, w = 230f, rowH = 22f;
    int shown = Math.min(10, worlds.size());
    float h = Math.max(32f, 10f + Math.max(1, shown) * rowH);
    canvas.roundedRect(x, y, w, h, 8f, Colors.alpha(surface, 0.96f));
    canvas.strokeRoundedRect(x, y, w, h, 8f, 0.8f, Colors.alpha(border, 0.88f));
    state.hits.add(new HitBox("noop", x, y, w, h));

    if (shown == 0) {
      canvas.textFamily("studio-sans-medium", I18n.text(lang, "worldMap.no_subworlds"), x + 10f,
          y + 11f, 7f, 0xFF94A3B8, false, 0f);
      return;
    }
    for (int i = 0; i < shown; i++) {
      DiskRegionStore.SubWorldRecord rec = worlds.get(i);
      float ry = y + 5f + i * rowH;
      boolean current = activeId.equals(rec.worldId);
      boolean hov = mouseX >= x + 4f && mouseX <= x + w - 4f && mouseY >= ry
          && mouseY <= ry + rowH - 2f;
      if (current || hov) {
        canvas.roundedRect(x + 4f, ry, w - 8f, rowH - 2f, 5.5f,
            Colors.alpha(current ? accent : 0xFFFFFFFF, current ? 0.25f : 0.08f));
      }
      String label = (rec.label == null || rec.label.isEmpty()) ? rec.worldId : rec.label;
      if (label.length() > 32) label = label.substring(0, 31) + "…";
      canvas.textFamily("studio-sans-medium", label, x + 11f, ry + 5.5f, 7.0f,
          current ? 0xFFFFFFFF : 0xFFCBD5E1, current, 0f);
      state.hits.add(new HitBox("select-world:" + rec.worldId, x + 4f, ry, w - 8f, rowH - 2f));
    }
  }

  private static void drawContextMenu(Canvas canvas, Settings settings, ViewState state, int width,
      int height, float mouseX, float mouseY, String lang, int surface, int border, int accent) {
    List<String[]> items = new ArrayList<String[]>();
    if (!FeaturePolicy.isBlocked("waypoints")) {
      boolean onWaypoint = state.contextWaypointId != null && !state.contextWaypointId.isEmpty();
      if (onWaypoint) {
        items.add(new String[] {"ctx-wp-toggle:" + state.contextWaypointId,
            I18n.text(lang, "worldMap.ctx.toggle_waypoint")});
        items.add(new String[] {"ctx-wp-delete:" + state.contextWaypointId,
            I18n.text(lang, "worldMap.ctx.delete_waypoint")});
      } else {
        items.add(new String[] {"ctx-create-wp", I18n.text(lang, "worldMap.ctx.create_waypoint")});
        items.add(new String[] {"ctx-temp-wp", I18n.text(lang, "worldMap.ctx.temp_waypoint")});
      }
    }
    items.add(new String[] {"ctx-copy-coords", I18n.text(lang, "worldMap.ctx.copy_coords")});
    if (settings.worldMap != null && settings.worldMap.teleportCommand) {
      items.add(new String[] {"ctx-teleport", I18n.text(lang, "worldMap.ctx.teleport")});
    }

    float menuW = 158f;
    float rowH = 22f;
    float headerH = 18f;
    float menuH = headerH + items.size() * rowH + 8f;
    float mx = Math.max(8f, Math.min(width - menuW - 8f, state.contextScreenX));
    float my = Math.max(8f, Math.min(height - menuH - 8f, state.contextScreenY));

    canvas.roundedRect(mx, my, menuW, menuH, 8.5f, Colors.alpha(surface, 0.97f));
    canvas.strokeRoundedRect(mx, my, menuW, menuH, 8.5f, 0.85f, Colors.alpha(border, 0.92f));
    state.hits.add(new HitBox("noop", mx, my, menuW, menuH));

    String coords = state.contextBlockX + ", " + state.contextBlockY + ", " + state.contextBlockZ;
    canvas.textFamily("studio-sans-medium", coords, mx + 10f, my + 6f, 6.5f, 0xFF94A3B8, false, 0f);
    canvas.line(mx + 8f, my + headerH, mx + menuW - 8f, my + headerH, 0.6f, 0x26FFFFFF);

    for (int i = 0; i < items.size(); i++) {
      float ry = my + headerH + 4f + i * rowH;
      boolean hov = mouseX >= mx + 4f && mouseX <= mx + menuW - 4f && mouseY >= ry
          && mouseY <= ry + rowH - 2f;
      if (hov) {
        canvas.roundedRect(
            mx + 4f, ry, menuW - 8f, rowH - 2f, 5.5f, Colors.alpha(accent, 0.28f));
      }
      boolean danger = items.get(i)[0].startsWith("ctx-wp-delete");
      canvas.textFamily("studio-sans-medium", items.get(i)[1], mx + 10f, ry + 5.5f, 7.0f,
          danger ? 0xFFFCA5A5 : (hov ? 0xFFFFFFFF : 0xFFE2E8F0), hov, 0f);
      state.hits.add(new HitBox(items.get(i)[0], mx + 4f, ry, menuW - 8f, rowH - 2f));
    }
  }

  private static String shortDimName(String dim, String lang) {
    if (dim == null || dim.isEmpty()) return "";
    if (dim.endsWith("overworld")) return I18n.text(lang, "dimension.overworld");
    if (dim.endsWith("the_nether")) return I18n.text(lang, "dimension.nether");
    if (dim.endsWith("the_end")) return I18n.text(lang, "dimension.end");
    int c = dim.indexOf(':');
    return c >= 0 ? dim.substring(c + 1) : dim;
  }

  private static String initialOf(String text) {
    if (text == null || text.trim().isEmpty()) return "W";
    String t = text.trim();
    return new String(Character.toChars(Character.toUpperCase(t.codePointAt(0))));
  }
}
