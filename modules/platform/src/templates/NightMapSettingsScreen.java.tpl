package dev.nightclient.platform;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nightclient.api.Keys;
import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import dev.nightclient.runtime.ClientRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
@@SCREEN_IMPORTS@@
@@VANILLA_SCREEN_IMPORTS@@

public final class NightMapSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 24;

    private static final String[] SHAPES = {
            "square", "sharp", "circle", "diamond", "hexagon", "octagon"
    };
    private static final String[] RELIEF_STYLES = {
            "classic", "vivid", "contour", "flat"
    };
    private static final String[] ARROW_STYLES = {
            "classic", "modern", "pointer", "head", "dot"
    };
    private static final int[] ARROW_COLORS = {
            0xFFFFFFFF, 0xFFEF4444, 0xFF38BDF8, 0xFF4ADE80, 0xFFFACC15, 0xFFA855F7, 0xFFF97316
    };
    private static final String[] ENLARGE_MODES = {"hold", "toggle"};
    private static final String[] ITEM_STYLES = {"icons", "dots"};
    private static final String[] MOB_STYLES = {"icons", "dots"};

    private static final int KIND_TOGGLE = 0;
    private static final int KIND_CHOICE = 1;
    private static final int KIND_SLIDER = 2;
    private static final int KIND_COLOR = 3;
    private static final int KIND_KEY = 4;
    private static final int KIND_CACHE_PAIR = 5;

    private static final class Entry {
        final String id;
        final String label;
        final int kind;
        final boolean boolVal;
        final String valueText;
        final int colorVal;
        final float sliderFrac;
        final float min, max, step;

        Entry(String id, String label, int kind, boolean boolVal, String valueText, int colorVal,
              float value, float min, float max, float step) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.boolVal = boolVal;
            this.valueText = valueText == null ? "" : valueText;
            this.colorVal = colorVal;
            this.min = min;
            this.max = max;
            this.step = step;
            this.sliderFrac = max > min ? Math.max(0f, Math.min(1f, (value - min) / (max - min))) : 0f;
        }
    }

    private int tab;
    private int selectedIndex = -1;
    private double scrollOffset;
    private int listLeft, listRight, listTop, listBottom;
    private final Button[] tabBtns = new Button[5];
    private final int[] tabBtnX = new int[5];
    private final int[] tabBtnW = new int[5];
    private final List<Entry> entries = new ArrayList<Entry>();

    private String draggingSliderId = "";
    private int dragLeft, dragWidth;
    private float dragMin, dragMax, dragStep;

    private String capturingBind = "";
    private boolean confirmClearWorld;
    private boolean confirmClearAll;
    private String statusText = "";
    private long statusUntilMs;

    public NightMapSettingsScreen() {
        super(literal(tr("mapSettings.title")));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component literal(String text) {
        return @@VANILLA_COMPONENT@@;
    }

    private static String lang() {
        ClientRuntime rt = ClientRuntime.get();
        return rt != null && rt.settings() != null ? I18n.language(rt.settings().language) : "en";
    }

    private static String tr(String key) {
        return I18n.text(lang(), key);
    }

    private static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public boolean isBindingCapture() {
        return !capturingBind.isEmpty();
    }

    public boolean captureBinding(int normalizedKey, String nativeKeyName) {
        if (capturingBind.isEmpty()) return false;
        String id = capturingBind;
        capturingBind = "";
        ClientRuntime rt = ClientRuntime.get();
        if (rt != null && normalizedKey != Keys.ESCAPE) {
            String target = (normalizedKey == Keys.BACKSPACE || normalizedKey == Keys.DELETE)
                    ? "key.keyboard.unknown" : nativeKeyName;
            rt.setKeyBinding(id, target);
            playClickSound();
        }
        rebuildEntries();
        return true;
    }

    @Override
    protected void init() {
        int gap = 4;
        int maxTotalW = Math.min(376, Math.max(240, this.width - 16));
        int footBw = Math.max(52, (maxTotalW - gap * 3) / 4);
        int totalW = footBw * 4 + gap * 3;
        int startX = (this.width - totalW) / 2;

        listLeft = startX;
        listRight = startX + totalW;
        listTop = 56;
        listBottom = Math.max(listTop + 44, this.height - 44);

        String[] tabKeys = {
                "mapSettings.tab.general",
                "mapSettings.tab.relief",
                "mapSettings.tab.radar",
                "mapSettings.tab.world",
                "mapSettings.tab.keys"
        };
        int availTabsW = totalW - gap * (tabKeys.length - 1);
        int baseTabW = availTabsW / tabKeys.length;
        int remTabs = availTabsW - baseTabW * tabKeys.length;
        int tx = startX;
        for (int i = 0; i < tabKeys.length; i++) {
            final int idx = i;
            int w = baseTabW + (i < remTabs ? 1 : 0);
            tabBtnX[i] = tx;
            tabBtnW[i] = w;
            Button b = makeButton(tx, 30, w, 20, literal(fitLabel(tr(tabKeys[i]), w)), new Button.OnPress() {
                @Override public void onPress(Button btn) {
                    tab = idx;
                    scrollOffset = 0.0;
                    selectedIndex = -1;
                    draggingSliderId = "";
                    capturingBind = "";
                    confirmClearWorld = false;
                    confirmClearAll = false;
                    refreshTabStates();
                    rebuildEntries();
                }
            });
            tabBtns[i] = b;
            addControl(b);
            tx += w + gap;
        }
        refreshTabStates();

        int footY = this.height - 28;
        addControl(makeButton(startX, footY, footBw, 20, literal(fitLabel(tr("mapSettings.open_editor"), footBw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        GameBridge.closeMapSettingsScreen();
                        GameBridge.openHudEditorScreen();
                    }
                }));
        Button wpBtn = makeButton(startX + (footBw + gap), footY, footBw, 20,
                literal(fitLabel(tr("mapSettings.open_waypoints"), footBw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        if (FeaturePolicy.isBlocked("waypoints")) return;
                        GameBridge.closeMapSettingsScreen();
                        GameBridge.openWaypointsMenuScreen();
                    }
                });
        wpBtn.visible = !FeaturePolicy.isBlocked("waypoints");
        addControl(wpBtn);
        Button wmBtn = makeButton(startX + (footBw + gap) * 2, footY, footBw, 20,
                literal(fitLabel(tr("mapSettings.open_worldmap"), footBw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        if (FeaturePolicy.isBlocked("worldMap")) return;
                        GameBridge.closeMapSettingsScreen();
                        GameBridge.openWorldMapScreen();
                    }
                });
        wmBtn.visible = !FeaturePolicy.isBlocked("worldMap");
        addControl(wmBtn);
        addControl(makeButton(startX + (footBw + gap) * 3, footY, footBw, 20,
                literal(fitLabel(tr("mapSettings.done"), footBw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        onClose();
                    }
                }));

        rebuildEntries();
    }

    private void refreshTabStates() {
        for (int i = 0; i < tabBtns.length; i++) {
            if (tabBtns[i] != null) tabBtns[i].active = (i != tab);
        }
    }

    private void rebuildEntries() {
        entries.clear();
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return;
        Settings.Minimap m = rt.settings().minimap;
        Settings.WorldMap w = rt.settings().worldMap;
        String l = lang();

        if (tab == 0) {
            if (!FeaturePolicy.isBlocked("minimap")) {
                entries.add(toggle(l, "m.enabled", "module.minimap.label", m.enabled));
                entries.add(choice(l, "m.shape", "field.minimap.shape", "choice.minimap.shape." + m.shape));
                entries.add(slider(l, "m.size", "field.minimap.size", m.size, 84f, 220f, 2f, "%dpx"));
                entries.add(slider(l, "m.zoom", "field.minimap.zoom", m.zoom, 0.5f, 3.0f, 0.05f, "%.2fx"));
                entries.add(toggle(l, "m.lockNorth", "field.minimap.lockNorth", m.lockNorth));
                entries.add(toggle(l, "m.showCompass", "field.minimap.showCompass", m.showCompass));
                entries.add(toggle(l, "m.showCoords", "field.minimap.showCoords", m.showCoords));
                entries.add(toggle(l, "m.chunkGrid", "field.minimap.chunkGrid", m.chunkGrid));
                entries.add(slider(l, "m.frameOpacity", "field.minimap.frameOpacity", m.frameOpacity, 0f, 1f, 0.05f, "%.0f%%"));
                entries.add(choice(l, "m.enlargeMode", "field.minimap.enlargeMode", "choice.minimap.enlargeMode." + m.enlargeMode));
                entries.add(slider(l, "m.enlargeSize", "field.minimap.enlargeSize", m.enlargeSize, 180f, 440f, 4f, "%dpx"));
                entries.add(slider(l, "m.enlargeZoom", "field.minimap.enlargeZoom", m.enlargeZoom, 0.35f, 2.0f, 0.05f, "%.2fx"));
            }
        } else if (tab == 1) {
            if (!FeaturePolicy.isBlocked("minimap")) {
                entries.add(toggle(l, "m.terrainShading", "field.minimap.terrainShading", m.terrainShading));
                if (m.terrainShading) {
                    entries.add(choice(l, "m.reliefStyle", "field.minimap.reliefStyle", "choice.minimap.reliefStyle." + m.reliefStyle));
                    entries.add(slider(l, "m.reliefStrength", "field.minimap.reliefStrength", m.reliefStrength, 0.5f, 4.0f, 0.05f, "%.2f"));
                }
                entries.add(toggle(l, "m.blockLight", "field.minimap.blockLight", m.blockLight));
                entries.add(toggle(l, "m.biomeColors", "field.minimap.biomeColors", m.biomeColors));
                entries.add(choice(l, "m.arrowStyle", "field.minimap.arrowStyle", "choice.minimap.arrowStyle." + m.arrowStyle));
                entries.add(slider(l, "m.arrowScale", "field.minimap.arrowScale", m.arrowScale, 0.6f, 2.0f, 0.05f, "%.2fx"));
                entries.add(colorRow(l, "m.arrowColor", "field.minimap.arrowColor", m.arrowColor));
            }
        } else if (tab == 2) {
            if (!FeaturePolicy.isBlocked("minimap")) {
                if (!FeaturePolicy.isBlocked("minimap.players")) {
                    entries.add(toggle(l, "m.showPlayers", "field.minimap.showPlayers", m.showPlayers));
                    if (m.showPlayers) {
                        entries.add(toggle(l, "m.showPlayerHeads", "field.minimap.showPlayerHeads", m.showPlayerHeads));
                        entries.add(toggle(l, "m.showPlayerNames", "field.minimap.showPlayerNames", m.showPlayerNames));
                        if (!FeaturePolicy.isBlocked("minimap.invisibles")) {
                            entries.add(toggle(l, "m.showInvisiblePlayers", "field.minimap.showInvisiblePlayers", m.showInvisiblePlayers));
                        }
                        entries.add(slider(l, "m.playerScale", "field.minimap.playerScale", m.playerScale, 0.6f, 1.8f, 0.05f, "%.2fx"));
                    }
                }
                if (!FeaturePolicy.isBlocked("minimap.mobs")) {
                    entries.add(choice(l, "m.mobIconStyle", "field.minimap.mobIconStyle", "choice.minimap.mobIconStyle." + m.mobIconStyle));
                    entries.add(slider(l, "m.mobScale", "field.minimap.mobScale", m.mobScale, 0.6f, 1.8f, 0.05f, "%.2fx"));
                    entries.add(toggle(l, "m.showHostileMobs", "field.minimap.showHostileMobs", m.showHostileMobs));
                    entries.add(toggle(l, "m.showNeutralMobs", "field.minimap.showNeutralMobs", m.showNeutralMobs));
                    entries.add(toggle(l, "m.showPassiveMobs", "field.minimap.showPassiveMobs", m.showPassiveMobs));
                    entries.add(toggle(l, "m.showOtherEntities", "field.minimap.showOtherEntities", m.showOtherEntities));
                    entries.add(toggle(l, "m.showMobNames", "field.minimap.showMobNames", m.showMobNames));
                }
                if (!FeaturePolicy.isBlocked("minimap.explosives")) {
                    entries.add(toggle(l, "m.showExplosives", "field.minimap.showExplosives", m.showExplosives));
                }
                if (!FeaturePolicy.isBlocked("minimap.items")) {
                    entries.add(toggle(l, "m.showItems", "field.minimap.showItems", m.showItems));
                    if (m.showItems) {
                        entries.add(choice(l, "m.itemStyle", "field.minimap.itemStyle", "choice.minimap.itemStyle." + m.itemStyle));
                        entries.add(toggle(l, "m.itemBackground", "field.minimap.itemBackground", m.itemBackground));
                        entries.add(toggle(l, "m.onlyValuableItems", "field.minimap.onlyValuableItems", m.onlyValuableItems));
                        entries.add(slider(l, "m.itemScale", "field.minimap.itemScale", m.itemScale, 0.6f, 1.8f, 0.05f, "%.2fx"));
                    }
                }
                if (!FeaturePolicy.isBlocked("waypoints")) {
                    entries.add(toggle(l, "m.showWaypoints", "field.minimap.showWaypoints", m.showWaypoints));
                    if (m.showWaypoints) {
                        entries.add(toggle(l, "m.offscreenWaypoints", "field.minimap.offscreenWaypoints", m.offscreenWaypoints));
                    }
                }
            }
        } else if (tab == 3) {
            if (!FeaturePolicy.isBlocked("worldMap")) {
                entries.add(toggle(l, "w.enabled", "module.worldMap.label", w.enabled));
                entries.add(toggle(l, "w.diskCache", "field.worldMap.diskCache", w.diskCache));
                entries.add(toggle(l, "w.terrainShading", "field.worldMap.terrainShading", w.terrainShading));
                entries.add(toggle(l, "w.blockLight", "field.worldMap.blockLight", w.blockLight));
                entries.add(toggle(l, "w.showGrid", "field.worldMap.showGrid", w.showGrid));
                entries.add(toggle(l, "w.showCoordinatesBar", "field.worldMap.showCoordinatesBar", w.showCoordinatesBar));
                if (!FeaturePolicy.isBlocked("waypoints")) {
                    entries.add(toggle(l, "w.showWaypoints", "field.worldMap.showWaypoints", w.showWaypoints));
                }
                if (!FeaturePolicy.isBlocked("worldMap.players")) {
                    entries.add(toggle(l, "w.showPlayers", "field.worldMap.showPlayers", w.showPlayers));
                    if (w.showPlayers && !FeaturePolicy.isBlocked("worldMap.invisibles")) {
                        entries.add(toggle(l, "w.showInvisiblePlayers", "field.worldMap.showInvisiblePlayers", w.showInvisiblePlayers));
                    }
                }
                if (!FeaturePolicy.isBlocked("worldMap.mobs")) {
                    entries.add(toggle(l, "w.showMobs", "field.worldMap.showMobs", w.showMobs));
                    entries.add(toggle(l, "w.showOtherEntities", "field.worldMap.showOtherEntities", w.showOtherEntities));
                }
                if (!FeaturePolicy.isBlocked("worldMap.items")) {
                    entries.add(toggle(l, "w.showItems", "field.worldMap.showItems", w.showItems));
                }
                entries.add(toggle(l, "w.teleportCommand", "field.worldMap.teleportCommand", w.teleportCommand));
                entries.add(new Entry("cache.pair", "", KIND_CACHE_PAIR, false, "", 0, 0f, 0f, 1f, 0.1f));
            }
        } else {
            if (!FeaturePolicy.isBlocked("worldMap")) entries.add(keyRow(l, "worldMap", "ui.world_map_key"));
            if (!(FeaturePolicy.isBlocked("minimap") && FeaturePolicy.isBlocked("worldMap"))) entries.add(keyRow(l, "mapSettings", "ui.map_settings_key"));
            if (!FeaturePolicy.isBlocked("minimap")) entries.add(keyRow(l, "mapEnlarge", "ui.map_enlarge_key"));
            if (!FeaturePolicy.isBlocked("waypoints")) {
                entries.add(keyRow(l, "waypointsMenu", "ui.waypoints_menu_key"));
                entries.add(keyRow(l, "waypoint", "ui.quick_waypoint_key"));
            }
        }

        if (selectedIndex >= entries.size()) selectedIndex = entries.size() - 1;
        clampScroll();
    }

    private static Entry toggle(String l, String id, String labelKey, boolean on) {
        return new Entry(id, I18n.text(l, labelKey), KIND_TOGGLE, on, "", 0, 0f, 0f, 1f, 0.1f);
    }

    private static Entry choice(String l, String id, String labelKey, String choiceKey) {
        return new Entry(id, I18n.text(l, labelKey), KIND_CHOICE, false, I18n.text(l, choiceKey),
                0, 0f, 0f, 1f, 0.1f);
    }

    private static Entry colorRow(String l, String id, String labelKey, int argb) {
        return new Entry(id, I18n.text(l, labelKey), KIND_COLOR, false, "", argb, 0f, 0f, 1f, 0.1f);
    }

    private static Entry slider(String l, String id, String labelKey, float value, float min, float max, float step, String fmt) {
        String valText;
        if ("%dpx".equals(fmt)) valText = Math.round(value) + "px";
        else if ("%.0f%%".equals(fmt)) valText = Math.round(value * 100f) + "%";
        else valText = String.format(Locale.ROOT, fmt, value);
        return new Entry(id, I18n.text(l, labelKey), KIND_SLIDER, false, valText, 0, value, min, max, step);
    }

    private static Entry keyRow(String l, String bindId, String labelKey) {
        return new Entry("bind:" + bindId, I18n.text(l, labelKey), KIND_KEY, false, "", 0, 0f, 0f, 1f, 0.1f);
    }

    @Override
    public void onClose() {
        GameBridge.closeMapSettingsScreen();
    }

    @Override
    public void @@SCREEN_RENDER_METHOD@@(@@CONTEXT@@ graphics, int mouseX, int mouseY, float delta) {
        @@VANILLA_RENDER_BG@@
        super.@@SCREEN_RENDER_METHOD@@(graphics, mouseX, mouseY, delta);
        renderOverlay(graphics, mouseX, mouseY);
    }

    private void renderOverlay(@@CONTEXT@@ graphics, int mouseX, int mouseY) {
        drawCentered(graphics, tr("mapSettings.title"), this.width / 2, 10, 0xFFFFFFFF);

        if (tab >= 0 && tab < tabBtnX.length && tabBtnW[tab] > 4) {
            drawFill(graphics, tabBtnX[tab] + 1, 49, tabBtnX[tab] + tabBtnW[tab] - 1, 50, 0xFFFFFFFF);
        }

        drawFill(graphics, listLeft, listTop, listRight, listBottom, 0xB0000000);
        drawFill(graphics, listLeft, listTop, listRight, listTop + 1, 0xFF555555);
        drawFill(graphics, listLeft, listBottom - 1, listRight, listBottom, 0xFF555555);
        drawFill(graphics, listLeft, listTop, listLeft + 1, listBottom, 0xFF333333);
        drawFill(graphics, listRight - 1, listTop, listRight, listBottom, 0xFF333333);

        double maxSc = maxScroll();
        boolean hasScrollbar = maxSc > 0.5;
        int rowLeft = listLeft + 4;
        int rowRight = listRight - (hasScrollbar ? 10 : 4);
        int rowW = Math.max(40, rowRight - rowLeft);
        ClientRuntime rt = ClientRuntime.get();

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int ry = listTop + 2 + i * ROW_HEIGHT - (int) Math.round(scrollOffset);
            int rh = ROW_HEIGHT - 2;
            if (ry + rh <= listTop + 1 || ry >= listBottom - 1) continue;

            int cTop = Math.max(listTop + 1, ry);
            int cBot = Math.min(listBottom - 1, ry + rh);
            boolean textFits = ry + 6 >= listTop + 1 && ry + 15 <= listBottom - 1;

            if (e.kind == KIND_CACHE_PAIR) {
                renderCachePairRow(graphics, rowLeft, rowRight, ry, rh, cTop, cBot, textFits, mouseX, mouseY);
                continue;
            }

            boolean dragging = e.kind == KIND_SLIDER && e.id.equals(draggingSliderId);
            boolean capturing = e.kind == KIND_KEY && e.id.substring(5).equals(capturingBind);
            boolean selected = i == selectedIndex || dragging || capturing;
            boolean hovered = mouseX >= rowLeft && mouseX <= rowRight
                    && mouseY >= cTop && mouseY <= cBot;

            if (selected) {
                drawFill(graphics, rowLeft, cTop, rowRight, cBot, 0xCC121212);
                if (ry >= listTop + 1) drawFill(graphics, rowLeft, ry, rowRight, ry + 1, 0xFFFFFFFF);
                if (ry + rh <= listBottom - 1) drawFill(graphics, rowLeft, ry + rh - 1, rowRight, ry + rh, 0xFFFFFFFF);
                drawFill(graphics, rowLeft, cTop, rowLeft + 1, cBot, 0xFFFFFFFF);
                drawFill(graphics, rowRight - 1, cTop, rowRight, cBot, 0xFFFFFFFF);
            } else if (hovered) {
                drawFill(graphics, rowLeft, cTop, rowRight, cBot, 0x55404040);
            }

            int labelX = rowLeft + 6;
            int labelY = ry + (rh - 8) / 2;
            int labelColor = (selected || hovered) ? 0xFFFFFFFF : 0xFFE6E6E6;

            if (e.kind == KIND_TOGGLE) {
                String val = e.boolVal ? "[" + tr("mapSettings.on") + "]" : "[" + tr("mapSettings.off") + "]";
                int valW = textWidth(val);
                int valX = rowRight - valW - 6;
                if (textFits) {
                    drawString(graphics, clip(e.label, Math.max(24, valX - labelX - 8)), labelX, labelY, labelColor);
                    drawString(graphics, val, valX, labelY, e.boolVal ? 0xFF55FF55 : 0xFFFF6666);
                }
            } else if (e.kind == KIND_CHOICE) {
                String val = e.valueText;
                int valW = textWidth(val);
                int valX = rowRight - valW - 6;
                if (textFits) {
                    drawString(graphics, clip(e.label, Math.max(24, valX - labelX - 8)), labelX, labelY, labelColor);
                    drawString(graphics, val, valX, labelY, 0xFFFFD866);
                }
            } else if (e.kind == KIND_SLIDER) {
                int valColW = 36;
                int barW = Math.min(112, Math.max(66, rowW / 3));
                int barX = rowRight - valColW - barW - 8;
                int barY = ry + (rh - 10) / 2;
                int barH = 10;
                if (textFits) {
                    drawString(graphics, clip(e.label, Math.max(24, barX - labelX - 8)), labelX, labelY, labelColor);
                    String v = clip(e.valueText, valColW);
                    drawString(graphics, v, rowRight - textWidth(v) - 6, labelY, dragging ? 0xFFFFD866 : 0xFFFFFFFF);
                }
                if (barY >= listTop + 1 && barY + barH <= listBottom - 1) {
                    int borderCol = (hovered || dragging) ? 0xFFFFFFFF : 0xFF555555;
                    drawFill(graphics, barX, barY, barX + barW, barY + barH, borderCol);
                    drawFill(graphics, barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF1A1A1A);
                    int innerW = Math.max(1, barW - 2);
                    int fillW = Math.round(innerW * e.sliderFrac);
                    if (fillW > 0) {
                        drawFill(graphics, barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFF4A5B78);
                    }
                    int knobW = 6;
                    int knobX = barX + 1 + Math.round((innerW - knobW) * e.sliderFrac);
                    drawFill(graphics, knobX, barY + 1, knobX + knobW, barY + barH - 1, dragging ? 0xFFFFFFFF : 0xFF808080);
                    if (!dragging) {
                        drawFill(graphics, knobX, barY + 1, knobX + knobW - 1, barY + barH - 2, 0xFFC0C0C0);
                    }
                }
            } else if (e.kind == KIND_COLOR) {
                int sw = 12;
                int sg = 3;
                int totalSwW = ARROW_COLORS.length * sw + (ARROW_COLORS.length - 1) * sg;
                int startSwX = rowRight - totalSwW - 6;
                int swY = ry + (rh - sw) / 2;
                if (textFits) {
                    drawString(graphics, clip(e.label, Math.max(24, startSwX - labelX - 8)), labelX, labelY, labelColor);
                }
                if (swY >= listTop + 1 && swY + sw <= listBottom - 1) {
                    int curCol = e.colorVal | 0xFF000000;
                    for (int ci = 0; ci < ARROW_COLORS.length; ci++) {
                        int sx = startSwX + ci * (sw + sg);
                        boolean active = ARROW_COLORS[ci] == curCol;
                        drawFill(graphics, sx, swY, sx + sw, swY + sw, active ? 0xFFFFFFFF : 0xFF3A3A3A);
                        drawFill(graphics, sx + 1, swY + 1, sx + sw - 1, swY + sw - 1, ARROW_COLORS[ci]);
                    }
                }
            } else if (e.kind == KIND_KEY) {
                String bindId = e.id.substring(5);
                String rawKey = capturing ? tr("mapSettings.press_key")
                        : (rt != null ? rt.keyBindingLabel(bindId) : "?");
                String shown = "[" + rawKey + "]";
                int valW = textWidth(shown);
                int valX = rowRight - valW - 6;
                if (textFits) {
                    drawString(graphics, clip(e.label, Math.max(24, valX - labelX - 8)), labelX, labelY, labelColor);
                    drawString(graphics, shown, valX, labelY, capturing ? 0xFFFFD866 : 0xFFE6E6E6);
                }
            }
        }

        if (hasScrollbar) {
            int sbX = listRight - 6;
            int trackH = Math.max(10, listBottom - listTop - 2);
            int thumbH = Math.max(18, (int) (trackH * (double) trackH / (entries.size() * ROW_HEIGHT + 4)));
            thumbH = Math.min(trackH, thumbH);
            int thumbY = listTop + 1 + (int) ((trackH - thumbH) * (scrollOffset / maxSc));
            drawFill(graphics, sbX, listTop + 1, sbX + 5, listBottom - 1, 0xFF000000);
            drawFill(graphics, sbX, thumbY, sbX + 5, thumbY + thumbH, 0xFF808080);
            drawFill(graphics, sbX, thumbY, sbX + 4, thumbY + thumbH - 1, 0xFFC0C0C0);
        }

        if (!statusText.isEmpty() && System.currentTimeMillis() <= statusUntilMs) {
            drawCentered(graphics, clip(statusText, this.width - 20), this.width / 2, listBottom + 4, 0xFF55FF55);
        }
    }

    private void renderCachePairRow(@@CONTEXT@@ graphics, int rowLeft, int rowRight, int ry, int rh,
                                    int cTop, int cBot, boolean textFits, int mouseX, int mouseY) {
        int gap = 6;
        int halfW = (rowRight - rowLeft - gap) / 2;
        int x1 = rowLeft;
        int x2 = rowLeft + halfW + gap;
        int w2 = rowRight - x2;
        drawCacheBox(graphics, x1, halfW, ry, rh, cTop, cBot, textFits, mouseX, mouseY,
                confirmClearWorld ? tr("mapSettings.confirm_clear_world") : tr("worldMap.clear_world_cache"),
                confirmClearWorld);
        drawCacheBox(graphics, x2, w2, ry, rh, cTop, cBot, textFits, mouseX, mouseY,
                confirmClearAll ? tr("mapSettings.confirm_clear_all") : tr("worldMap.clear_all_cache"),
                confirmClearAll);
    }

    private void drawCacheBox(@@CONTEXT@@ graphics, int bx, int bw, int ry, int rh, int cTop, int cBot,
                              boolean textFits, int mouseX, int mouseY, String label, boolean confirm) {
        boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= cTop && mouseY <= cBot;
        int bg = confirm ? 0xCC5A1A1A : (hov ? 0xCC3D1C1C : 0xB8221414);
        int rim = confirm ? 0xFFFF6666 : (hov ? 0xFFFFFFFF : 0xFF555555);
        drawFill(graphics, bx, cTop, bx + bw, cBot, bg);
        if (ry >= listTop + 1) drawFill(graphics, bx, ry, bx + bw, ry + 1, rim);
        if (ry + rh <= listBottom - 1) drawFill(graphics, bx, ry + rh - 1, bx + bw, ry + rh, rim);
        drawFill(graphics, bx, cTop, bx + 1, cBot, rim);
        drawFill(graphics, bx + bw - 1, cTop, bx + bw, cBot, rim);
        if (textFits) {
            drawCentered(graphics, clip(label, bw - 8), bx + bw / 2, ry + (rh - 8) / 2,
                    confirm ? 0xFFFFFFFF : 0xFFFF8A80);
        }
    }

    private boolean handleListClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (mouseX < listLeft + 2 || mouseX > listRight - 2 || mouseY < listTop + 1 || mouseY > listBottom - 1) {
            confirmClearWorld = false;
            confirmClearAll = false;
            return false;
        }
        int relY = (int) Math.floor(mouseY - (listTop + 2) + scrollOffset);
        if (relY < 0) return false;
        int idx = relY / ROW_HEIGHT;
        if (idx < 0 || idx >= entries.size()) return false;

        selectedIndex = idx;
        Entry e = entries.get(idx);
        boolean hasScrollbar = maxScroll() > 0.5;
        int rowLeft = listLeft + 4;
        int rowRight = listRight - (hasScrollbar ? 10 : 4);
        int rowW = Math.max(40, rowRight - rowLeft);

        if (e.kind == KIND_CACHE_PAIR) {
            if (button != 0) return false;
            int mid = rowLeft + (rowRight - rowLeft) / 2;
            playClickSound();
            if (mouseX < mid) {
                confirmClearAll = false;
                if (!confirmClearWorld) {
                    confirmClearWorld = true;
                } else {
                    confirmClearWorld = false;
                    ClientRuntime rt = ClientRuntime.get();
                    if (rt != null) rt.clearCurrentWorldMapCache();
                    showStatus(tr("worldMap.cache_cleared_world"));
                }
            } else {
                confirmClearWorld = false;
                if (!confirmClearAll) {
                    confirmClearAll = true;
                } else {
                    confirmClearAll = false;
                    ClientRuntime rt = ClientRuntime.get();
                    if (rt != null) rt.clearAllMapCache();
                    showStatus(tr("worldMap.cache_cleared_all"));
                }
            }
            return true;
        }

        confirmClearWorld = false;
        confirmClearAll = false;

        if (e.kind == KIND_SLIDER) {
            if (button != 0) return false;
            int valColW = 36;
            int barW = Math.min(112, Math.max(66, rowW / 3));
            int barX = rowRight - valColW - barW - 8;
            ClientRuntime rt = ClientRuntime.get();
            if (rt != null) rt.checkpoint();
            draggingSliderId = e.id;
            dragLeft = barX;
            dragWidth = barW;
            dragMin = e.min;
            dragMax = e.max;
            dragStep = e.step;
            updateSliderFromX((float) mouseX);
            playClickSound();
            return true;
        }

        if (e.kind == KIND_COLOR) {
            ClientRuntime rt = ClientRuntime.get();
            if (rt == null || rt.settings() == null) return false;
            int sw = 12;
            int sg = 3;
            int totalSwW = ARROW_COLORS.length * sw + (ARROW_COLORS.length - 1) * sg;
            int startSwX = rowRight - totalSwW - 6;
            int picked = -1;
            for (int ci = 0; ci < ARROW_COLORS.length; ci++) {
                int sx = startSwX + ci * (sw + sg);
                if (mouseX >= sx - 1 && mouseX <= sx + sw + 1) {
                    picked = ARROW_COLORS[ci];
                    break;
                }
            }
            rt.checkpoint();
            if (picked != -1) {
                rt.settings().minimap.arrowColor = picked;
            } else {
                rt.settings().minimap.arrowColor = stepArrowColor(rt.settings().minimap.arrowColor, button == 1 ? -1 : 1);
            }
            rt.settings().validate();
            rt.changed();
            playClickSound();
            rebuildEntries();
            return true;
        }

        if (e.kind == KIND_KEY) {
            if (button != 0) return false;
            capturingBind = e.id.substring(5);
            playClickSound();
            return true;
        }

        applyEntryAction(e, button == 1 ? -1 : 1);
        playClickSound();
        rebuildEntries();
        return true;
    }

    private boolean handleListDrag(double mouseX, double mouseY, int button) {
        if (button != 0 || draggingSliderId.isEmpty()) return false;
        updateSliderFromX((float) mouseX);
        return true;
    }

    private boolean handleListRelease(double mouseX, double mouseY, int button) {
        if (button == 0 && !draggingSliderId.isEmpty()) {
            draggingSliderId = "";
            return true;
        }
        return false;
    }

    private boolean handleListScroll(double mouseX, double mouseY, double vertical) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset -= vertical * 18.0;
            clampScroll();
            return true;
        }
        return false;
    }

    private boolean handleCustomKey(int normalizedKey) {
        if (normalizedKey == Keys.ESCAPE) {
            if (!capturingBind.isEmpty()) {
                capturingBind = "";
                return true;
            }
            onClose();
            return true;
        }
        if (entries.isEmpty()) return false;
        if (normalizedKey == Keys.UP || normalizedKey == Keys.DOWN) {
            int idx = selectedIndex < 0 ? 0 : selectedIndex;
            idx += normalizedKey == Keys.UP ? -1 : 1;
            if (idx < 0) idx = entries.size() - 1;
            if (idx >= entries.size()) idx = 0;
            selectedIndex = idx;
            ensureSelectedVisible();
            return true;
        }
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            Entry e = entries.get(selectedIndex);
            if ((normalizedKey == Keys.LEFT || normalizedKey == Keys.RIGHT) && e.kind == KIND_SLIDER) {
                int dir = normalizedKey == Keys.LEFT ? -1 : 1;
                stepSlider(e, dir);
                playClickSound();
                rebuildEntries();
                return true;
            }
            if ((normalizedKey == Keys.LEFT || normalizedKey == Keys.RIGHT) && e.kind == KIND_CHOICE) {
                applyEntryAction(e, normalizedKey == Keys.LEFT ? -1 : 1);
                playClickSound();
                rebuildEntries();
                return true;
            }
            if (normalizedKey == Keys.ENTER || normalizedKey == ' ') {
                if (e.kind == KIND_KEY) {
                    capturingBind = e.id.substring(5);
                } else if (e.kind == KIND_TOGGLE || e.kind == KIND_CHOICE || e.kind == KIND_COLOR) {
                    applyEntryAction(e, 1);
                    rebuildEntries();
                }
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private void updateSliderFromX(float mx) {
        float frac = Math.max(0f, Math.min(1f, (mx - dragLeft) / Math.max(1f, (float) dragWidth)));
        float raw = dragMin + frac * (dragMax - dragMin);
        float stepped = dragStep > 0f ? Math.round(raw / dragStep) * dragStep : raw;
        applySliderValue(draggingSliderId, Math.max(dragMin, Math.min(dragMax, stepped)));
        rebuildEntries();
    }

    private void stepSlider(Entry e, int dir) {
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return;
        rt.checkpoint();
        float cur = e.min + e.sliderFrac * (e.max - e.min);
        float next = Math.max(e.min, Math.min(e.max, cur + dir * e.step));
        applySliderValue(e.id, next);
    }

    private void applySliderValue(String id, float value) {
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return;
        Settings.Minimap m = rt.settings().minimap;
        if ("m.size".equals(id)) m.size = Math.round(value);
        else if ("m.zoom".equals(id)) m.zoom = value;
        else if ("m.frameOpacity".equals(id)) m.frameOpacity = value;
        else if ("m.enlargeSize".equals(id)) m.enlargeSize = Math.round(value);
        else if ("m.enlargeZoom".equals(id)) m.enlargeZoom = value;
        else if ("m.reliefStrength".equals(id)) m.reliefStrength = value;
        else if ("m.arrowScale".equals(id)) m.arrowScale = value;
        else if ("m.playerScale".equals(id)) m.playerScale = value;
        else if ("m.mobScale".equals(id)) m.mobScale = value;
        else if ("m.itemScale".equals(id)) m.itemScale = value;
        rt.settings().validate();
        rt.changed();
    }

    private void applyEntryAction(Entry e, int dir) {
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return;
        rt.checkpoint();
        Settings.Minimap m = rt.settings().minimap;
        Settings.WorldMap w = rt.settings().worldMap;
        String id = e.id;
        if ("m.enabled".equals(id)) m.enabled = !m.enabled;
        else if ("m.shape".equals(id)) m.shape = stepChoice(SHAPES, m.shape, dir);
        else if ("m.lockNorth".equals(id)) m.lockNorth = !m.lockNorth;
        else if ("m.showCompass".equals(id)) m.showCompass = !m.showCompass;
        else if ("m.showCoords".equals(id)) m.showCoords = !m.showCoords;
        else if ("m.chunkGrid".equals(id)) m.chunkGrid = !m.chunkGrid;
        else if ("m.enlargeMode".equals(id)) m.enlargeMode = stepChoice(ENLARGE_MODES, m.enlargeMode, dir);
        else if ("m.terrainShading".equals(id)) m.terrainShading = !m.terrainShading;
        else if ("m.reliefStyle".equals(id)) m.reliefStyle = stepChoice(RELIEF_STYLES, m.reliefStyle, dir);
        else if ("m.blockLight".equals(id)) m.blockLight = !m.blockLight;
        else if ("m.biomeColors".equals(id)) m.biomeColors = !m.biomeColors;
        else if ("m.arrowStyle".equals(id)) m.arrowStyle = stepChoice(ARROW_STYLES, m.arrowStyle, dir);
        else if ("m.arrowColor".equals(id)) m.arrowColor = stepArrowColor(m.arrowColor, dir);
        else if ("m.showPlayers".equals(id)) m.showPlayers = !m.showPlayers;
        else if ("m.showPlayerHeads".equals(id)) m.showPlayerHeads = !m.showPlayerHeads;
        else if ("m.showPlayerNames".equals(id)) m.showPlayerNames = !m.showPlayerNames;
        else if ("m.showInvisiblePlayers".equals(id)) m.showInvisiblePlayers = !m.showInvisiblePlayers;
        else if ("m.mobIconStyle".equals(id)) m.mobIconStyle = stepChoice(MOB_STYLES, m.mobIconStyle, dir);
        else if ("m.showHostileMobs".equals(id)) m.showHostileMobs = !m.showHostileMobs;
        else if ("m.showNeutralMobs".equals(id)) m.showNeutralMobs = !m.showNeutralMobs;
        else if ("m.showPassiveMobs".equals(id)) m.showPassiveMobs = !m.showPassiveMobs;
        else if ("m.showOtherEntities".equals(id)) m.showOtherEntities = !m.showOtherEntities;
        else if ("m.showMobNames".equals(id)) m.showMobNames = !m.showMobNames;
        else if ("m.showExplosives".equals(id)) m.showExplosives = !m.showExplosives;
        else if ("m.showItems".equals(id)) m.showItems = !m.showItems;
        else if ("m.itemStyle".equals(id)) m.itemStyle = stepChoice(ITEM_STYLES, m.itemStyle, dir);
        else if ("m.itemBackground".equals(id)) m.itemBackground = !m.itemBackground;
        else if ("m.onlyValuableItems".equals(id)) m.onlyValuableItems = !m.onlyValuableItems;
        else if ("m.showWaypoints".equals(id)) m.showWaypoints = !m.showWaypoints;
        else if ("m.offscreenWaypoints".equals(id)) m.offscreenWaypoints = !m.offscreenWaypoints;
        else if ("w.enabled".equals(id)) w.enabled = !w.enabled;
        else if ("w.diskCache".equals(id)) w.diskCache = !w.diskCache;
        else if ("w.terrainShading".equals(id)) w.terrainShading = !w.terrainShading;
        else if ("w.blockLight".equals(id)) w.blockLight = !w.blockLight;
        else if ("w.showGrid".equals(id)) w.showGrid = !w.showGrid;
        else if ("w.showCoordinatesBar".equals(id)) w.showCoordinatesBar = !w.showCoordinatesBar;
        else if ("w.showWaypoints".equals(id)) w.showWaypoints = !w.showWaypoints;
        else if ("w.showPlayers".equals(id)) w.showPlayers = !w.showPlayers;
        else if ("w.showInvisiblePlayers".equals(id)) w.showInvisiblePlayers = !w.showInvisiblePlayers;
        else if ("w.showMobs".equals(id)) w.showMobs = !w.showMobs;
        else if ("w.showOtherEntities".equals(id)) w.showOtherEntities = !w.showOtherEntities;
        else if ("w.showItems".equals(id)) w.showItems = !w.showItems;
        else if ("w.teleportCommand".equals(id)) w.teleportCommand = !w.teleportCommand;
        rt.settings().validate();
        rt.changed();
    }

    private static String stepChoice(String[] options, String current, int dir) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) {
                return options[(i + dir + options.length) % options.length];
            }
        }
        return options[0];
    }

    private static int stepArrowColor(int current, int dir) {
        int c = current | 0xFF000000;
        for (int i = 0; i < ARROW_COLORS.length; i++) {
            if (ARROW_COLORS[i] == c) {
                return ARROW_COLORS[(i + dir + ARROW_COLORS.length) % ARROW_COLORS.length];
            }
        }
        return ARROW_COLORS[0];
    }

    private void showStatus(String msg) {
        statusText = msg == null ? "" : msg;
        statusUntilMs = System.currentTimeMillis() + 2400L;
    }

    private double maxScroll() {
        int contentH = entries.size() * ROW_HEIGHT + 4;
        int viewH = Math.max(1, listBottom - listTop);
        return Math.max(0.0, contentH - viewH);
    }

    private void clampScroll() {
        double max = maxScroll();
        if (scrollOffset < 0.0) scrollOffset = 0.0;
        if (scrollOffset > max) scrollOffset = max;
    }

    private void ensureSelectedVisible() {
        if (selectedIndex < 0) return;
        int top = selectedIndex * ROW_HEIGHT;
        int bottom = top + ROW_HEIGHT;
        int viewH = Math.max(1, listBottom - listTop - 4);
        if (top < scrollOffset) scrollOffset = top;
        else if (bottom > scrollOffset + viewH) scrollOffset = bottom - viewH;
        clampScroll();
    }

    private int textWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return this.font != null ? this.font.width(text) : text.length() * 6;
    }

    private String fitLabel(String text, int buttonW) {
        return clip(text, Math.max(20, buttonW - 8));
    }

    private String clip(String text, int maxW) {
        if (text == null || maxW <= 4) return "";
        if (this.font == null) return text;
        if (this.font.width(text) <= maxW) return text;
        String ell = "...";
        int ew = this.font.width(ell);
        if (maxW <= ew) return ell;
        return this.font.plainSubstrByWidth(text, maxW - ew) + ell;
    }

    private static Button makeButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
        return NightVanillaAccountScreen.createVanillaButton(x, y, w, h, label, onPress);
    }

    private void addControl(net.minecraft.client.gui.components.AbstractWidget w) {
        @@VANILLA_ADD_WIDGET@@;
    }

    private void drawFill(@@CONTEXT@@ graphics, int x1, int y1, int x2, int y2, int color) {
        NightVanillaAccountScreen.drawVanillaFill(graphics, x1, y1, x2, y2, color);
    }

    private void drawString(@@CONTEXT@@ graphics, String text, int x, int y, int color) {
        NightVanillaAccountScreen.drawVanillaText(graphics, this.font, text, x, y, color);
    }

    private void drawCentered(@@CONTEXT@@ graphics, String text, int x, int y, int color) {
        NightVanillaAccountScreen.drawVanillaCentered(graphics, this.font, text, x, y, color);
    }

    @@MAP_SETTINGS_INPUT_METHODS@@
}
