package dev.nightclient.ui;

import dev.nightclient.api.Canvas;
import dev.nightclient.api.Keys;
import dev.nightclient.api.MapRadarModels;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.renderer.map.MapColorShading;
import dev.nightclient.renderer.map.RegionCacheManager;
import dev.nightclient.renderer.map.WorldMapRenderer;

public final class WorldMapController {
  public interface Host {
    RegionCacheManager mapCache();

    MapRadarModels.RadarSnapshot radarSnapshot();

    void openWaypointCreate(double x, double y, double z);

    void clipboard(String value);

    void teleportTo(int x, int y, int z);

    void checkpoint();

    void changed();

    void closeWorldMap();

    default void openMapSettings() {}

    default void openWaypointsMenu() {}

    default void clearCurrentWorldCache() {}
  }

  private final Settings settings;
  private final Host host;
  private final WorldMapRenderer.ViewState state = new WorldMapRenderer.ViewState();

  private boolean open;
  private boolean dragging;
  private float dragLastX, dragLastY;
  private int viewportW = 1, viewportH = 1;
  private float lastMouseX, lastMouseY;

  public WorldMapController(Settings settings, Host host) {
    this.settings = settings;
    this.host = host;
  }

  public WorldMapRenderer.ViewState viewState() {
    return state;
  }

  public boolean isOpen() {
    return open;
  }

  public void onOpen() {
    open = true;
    dragging = false;
    state.subWorldMenuOpen = false;
    state.contextMenuOpen = false;
    state.confirmClearCache = false;
    state.followPlayer = true;
    MapRadarModels.RadarSnapshot radar = host.radarSnapshot();
    if (radar != null && radar.inWorld) {
      state.centerX = radar.playerX;
      state.centerZ = radar.playerZ;
    }
  }

  public void onClose() {
    open = false;
    dragging = false;
    state.subWorldMenuOpen = false;
    state.contextMenuOpen = false;
    state.confirmClearCache = false;
  }

  public void render(
      Canvas canvas, int width, int height, float mouseX, float mouseY, double timeSeconds) {
    viewportW = Math.max(1, width);
    viewportH = Math.max(1, height);
    lastMouseX = mouseX;
    lastMouseY = mouseY;
    MapRadarModels.RadarSnapshot radar = host.radarSnapshot();
    if (state.followPlayer && !dragging && radar != null && radar.inWorld) {
      state.centerX = radar.playerX;
      state.centerZ = radar.playerZ;
    }
    WorldMapRenderer.render(
        canvas, settings, host.mapCache(), radar, state, width, height, mouseX, mouseY,
        timeSeconds);
  }

  public boolean mouseClicked(double mx, double my, int button) {
    float x = (float) mx, y = (float) my;
    lastMouseX = x;
    lastMouseY = y;

    for (int i = state.hits.size() - 1; i >= 0; i--) {
      WorldMapRenderer.HitBox hit = state.hits.get(i);
      if (hit.contains(x, y)) {
        if (button == 0) {
          handleAction(hit.action);
        }
        return true;
      }
    }

    if (button == 0) {
      state.contextMenuOpen = false;
      state.subWorldMenuOpen = false;
      state.confirmClearCache = false;
      dragging = true;
      dragLastX = x;
      dragLastY = y;
      return true;
    }

    if (button == 1) {
      state.subWorldMenuOpen = false;
      openContextMenu(x, y);
      return true;
    }

    return false;
  }

  public boolean mouseReleased(double mx, double my, int button) {
    if (button == 0 && dragging) {
      dragging = false;
      return true;
    }
    return false;
  }

  public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    if (!dragging || button != 0) return false;
    float x = (float) mx, y = (float) my;
    float moveX = x - dragLastX;
    float moveY = y - dragLastY;
    dragLastX = x;
    dragLastY = y;
    float z = Math.max(0.08f, state.zoom);
    state.centerX -= moveX / z;
    state.centerZ -= moveY / z;
    state.followPlayer = false;
    return true;
  }

  public boolean mouseScrolled(double mx, double my, double vertical, int modifiers) {
    if (vertical == 0) return false;
    float x = (float) mx, y = (float) my;
    float oldZoom = Math.max(0.08f, Math.min(8.0f, state.zoom));
    float factor = vertical > 0 ? 1.24f : (1f / 1.24f);
    float newZoom = Math.max(0.08f, Math.min(8.0f, oldZoom * factor));
    if (Math.abs(newZoom - oldZoom) < 1e-4f) return true;

    float halfW = viewportW * 0.5f;
    float halfH = viewportH * 0.5f;
    double worldUnderX = state.centerX + (x - halfW) / oldZoom;
    double worldUnderZ = state.centerZ + (y - halfH) / oldZoom;
    state.zoom = newZoom;
    state.centerX = worldUnderX - (x - halfW) / newZoom;
    state.centerZ = worldUnderZ - (y - halfH) / newZoom;
    state.followPlayer = false;
    state.contextMenuOpen = false;
    return true;
  }

  public boolean keyPressed(int key, int modifiers) {
    if (key == Keys.ESCAPE) {
      if (state.contextMenuOpen) {
        state.contextMenuOpen = false;
        return true;
      }
      if (state.subWorldMenuOpen) {
        state.subWorldMenuOpen = false;
        return true;
      }
      host.closeWorldMap();
      return true;
    }
    float panStep = 64f / Math.max(0.08f, state.zoom);
    if (key == 'W' || key == Keys.UP) {
      state.centerZ -= panStep;
      state.followPlayer = false;
      return true;
    }
    if (key == 'S' || key == Keys.DOWN) {
      state.centerZ += panStep;
      state.followPlayer = false;
      return true;
    }
    if (key == 'A' || key == Keys.LEFT) {
      state.centerX -= panStep;
      state.followPlayer = false;
      return true;
    }
    if (key == 'D' || key == Keys.RIGHT) {
      state.centerX += panStep;
      state.followPlayer = false;
      return true;
    }
    if (key == 'C' || key == ' ') {
      centerOnPlayer();
      return true;
    }
    if (key == '=' || key == '+' || key == 334) {
      mouseScrolled(viewportW * 0.5, viewportH * 0.5, 1.0, modifiers);
      return true;
    }
    if (key == '-' || key == 333) {
      mouseScrolled(viewportW * 0.5, viewportH * 0.5, -1.0, modifiers);
      return true;
    }
    return false;
  }

  private void centerOnPlayer() {
    MapRadarModels.RadarSnapshot radar = host.radarSnapshot();
    if (radar != null && radar.inWorld) {
      state.centerX = radar.playerX;
      state.centerZ = radar.playerZ;
      state.followPlayer = true;
    }
  }

  private void openContextMenu(float sx, float sy) {
    float halfW = viewportW * 0.5f;
    float halfH = viewportH * 0.5f;
    float z = Math.max(0.08f, state.zoom);
    int bx = (int) Math.floor(state.centerX + (sx - halfW) / z);
    int bz = (int) Math.floor(state.centerZ + (sy - halfH) / z);
    RegionCacheManager cache = host.mapCache();
    short surfaceY = cache != null ? cache.heightAt(bx, bz) : MapColorShading.UNEXPLORED_HEIGHT;
    MapRadarModels.RadarSnapshot radar = host.radarSnapshot();
    int by = surfaceY != MapColorShading.UNEXPLORED_HEIGHT
        ? (surfaceY + 1)
        : (radar != null && radar.inWorld ? (int) Math.round(radar.playerY) : 64);

    state.contextMenuOpen = true;
    state.contextScreenX = sx;
    state.contextScreenY = sy;
    state.contextBlockX = bx;
    state.contextBlockY = by;
    state.contextBlockZ = bz;
    state.contextWaypointId = state.hoveredWaypointId == null ? "" : state.hoveredWaypointId;
  }

  private void handleAction(String action) {
    if (action == null || action.isEmpty() || "noop".equals(action)) return;
    if (!"clear-cache".equals(action)) state.confirmClearCache = false;
    if ("close".equals(action)) {
      host.closeWorldMap();
      return;
    }
    if ("open-settings".equals(action)) {
      host.closeWorldMap();
      host.openMapSettings();
      return;
    }
    if ("open-waypoints".equals(action)) {
      if (FeaturePolicy.isBlocked("waypoints")) return;
      host.closeWorldMap();
      host.openWaypointsMenu();
      return;
    }
    if ("clear-cache".equals(action)) {
      if (!state.confirmClearCache) {
        state.confirmClearCache = true;
      } else {
        state.confirmClearCache = false;
        host.clearCurrentWorldCache();
      }
      return;
    }
    if ("center".equals(action)) {
      centerOnPlayer();
      return;
    }
    if ("toggle-subworlds".equals(action)) {
      state.subWorldMenuOpen = !state.subWorldMenuOpen;
      state.contextMenuOpen = false;
      return;
    }
    if ("toggle-grid".equals(action)) {
      host.checkpoint();
      settings.worldMap.showGrid = !settings.worldMap.showGrid;
      host.changed();
      return;
    }
    if ("toggle-waypoints".equals(action)) {
      if (FeaturePolicy.isBlocked("waypoints")) return;
      host.checkpoint();
      settings.worldMap.showWaypoints = !settings.worldMap.showWaypoints;
      host.changed();
      return;
    }
    if ("toggle-players".equals(action)) {
      if (FeaturePolicy.isBlocked("worldMap.players")) return;
      host.checkpoint();
      settings.worldMap.showPlayers = !settings.worldMap.showPlayers;
      host.changed();
      return;
    }
    if ("toggle-invis".equals(action)) {
      if (FeaturePolicy.isBlocked("worldMap.invisibles")) return;
      host.checkpoint();
      settings.worldMap.showInvisiblePlayers = !settings.worldMap.showInvisiblePlayers;
      host.changed();
      return;
    }
    if ("toggle-mobs".equals(action)) {
      if (FeaturePolicy.isBlocked("worldMap.mobs")) return;
      host.checkpoint();
      settings.worldMap.showMobs = !settings.worldMap.showMobs;
      host.changed();
      return;
    }
    if ("toggle-items".equals(action)) {
      if (FeaturePolicy.isBlocked("worldMap.items")) return;
      host.checkpoint();
      settings.worldMap.showItems = !settings.worldMap.showItems;
      host.changed();
      return;
    }
    if (action.startsWith("select-world:")) {
      String targetId = action.substring(13);
      RegionCacheManager cache = host.mapCache();
      if (cache != null) cache.selectManualSubWorld(targetId, null);
      state.subWorldMenuOpen = false;
      return;
    }
    if ("ctx-create-wp".equals(action)) {
      state.contextMenuOpen = false;
      if (FeaturePolicy.isBlocked("waypoints")) return;
      int bx = state.contextBlockX, by = state.contextBlockY, bz = state.contextBlockZ;
      host.closeWorldMap();
      host.openWaypointCreate(bx + 0.5, by, bz + 0.5);
      return;
    }
    if ("ctx-temp-wp".equals(action)) {
      if (FeaturePolicy.isBlocked("waypoints")) {
        state.contextMenuOpen = false;
        return;
      }
      MapRadarModels.RadarSnapshot radar = host.radarSnapshot();
      RegionCacheManager cache = host.mapCache();
      String dim = radar != null && !radar.dimension.isEmpty()
          ? radar.dimension
          : (cache != null ? cache.identity().dimension() : Waypoint.DEFAULT_DIMENSION);
      String worldId = cache != null && !cache.identity().worldId().isEmpty()
          ? cache.identity().worldId()
          : (radar != null ? radar.worldId : "");
      String worldLabel = cache != null && !cache.identity().worldLabel().isEmpty()
          ? cache.identity().worldLabel()
          : (radar != null ? radar.worldLabel : "");
      host.checkpoint();
      settings.waypoints.enabled = true;
      Waypoint wp = settings.waypoints.add("★ " + state.contextBlockX + " " + state.contextBlockZ,
          state.contextBlockX + 0.5, state.contextBlockY, state.contextBlockZ + 0.5, dim, worldId,
          worldLabel);
      if (wp != null) {
        wp.temp = true;
        wp.color = 0xFF38BDF8;
        host.changed();
      }
      state.contextMenuOpen = false;
      return;
    }
    if ("ctx-copy-coords".equals(action)) {
      host.clipboard(state.contextBlockX + " " + state.contextBlockY + " " + state.contextBlockZ);
      state.contextMenuOpen = false;
      return;
    }
    if ("ctx-teleport".equals(action)) {
      int bx = state.contextBlockX, by = state.contextBlockY, bz = state.contextBlockZ;
      state.contextMenuOpen = false;
      host.teleportTo(bx, by, bz);
      host.closeWorldMap();
      return;
    }
    if (action.startsWith("ctx-wp-toggle:")) {
      String id = action.substring(14);
      Waypoint wp = settings.waypoints.find(id);
      if (wp != null) {
        host.checkpoint();
        wp.visible = !wp.visible;
        host.changed();
      }
      state.contextMenuOpen = false;
      return;
    }
    if (action.startsWith("ctx-wp-delete:")) {
      String id = action.substring(14);
      host.checkpoint();
      if (settings.waypoints.remove(id)) host.changed();
      state.contextMenuOpen = false;
    }
  }
}
