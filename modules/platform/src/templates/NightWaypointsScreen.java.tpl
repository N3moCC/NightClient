package dev.nightclient.platform;

import dev.nightclient.api.Keys;
import dev.nightclient.config.Settings;
import dev.nightclient.config.Waypoint;
import dev.nightclient.i18n.I18n;
import dev.nightclient.runtime.ClientRuntime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
@@SCREEN_IMPORTS@@
@@VANILLA_SCREEN_IMPORTS@@

public final class NightWaypointsScreen extends Screen {
    private static final int MODE_LIST = 0;
    private static final int MODE_ADD = 1;
    private static final int MODE_EDIT = 2;
    private static final int ROW_HEIGHT = 26;

    private static final int[] PALETTE = {
            0xFF38BDF8, 0xFF4ADE80, 0xFFFACC15, 0xFFF97316, 0xFFEF4444, 0xFFE879F9,
            0xFFA855F7, 0xFF6366F1, 0xFF14B8A6, 0xFFF472B6, 0xFFFFFFFF, 0xFF94A3B8
    };

    private final Screen parent;
    private final boolean closeAfterForm;
    private int mode = MODE_LIST;
    private boolean allWorlds;
    private String searchQuery = "";
    private String selectedId = "";
    private double scrollOffset;
    private long lastClickMs;
    private int lastClickIndex = -1;

    private String editingId = "";
    private String draftName = "";
    private String draftX = "0";
    private String draftY = "64";
    private String draftZ = "0";
    private int draftColor = PALETTE[0];
    private boolean draftTemp;

    private String statusText = "";
    private long statusUntilMs;

    private EditBox searchBox;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    private Button filterBtn;
    private Button tpBtn;
    private Button editBtn;
    private Button toggleBtn;
    private Button shareBtn;
    private Button clearTempBtn;
    private Button deleteBtn;
    private Button tempBtn;

    private int listLeft, listRight, listTop, listBottom;
    private int swatchLeft, swatchTop, swatchSize, swatchGap;
    private final List<Waypoint> rows = new ArrayList<Waypoint>();

    public NightWaypointsScreen() {
        this(null, false);
    }

    private NightWaypointsScreen(Screen parent, boolean closeAfterForm) {
        super(literal(tr("waypointsMenu.title")));
        this.parent = parent;
        this.closeAfterForm = closeAfterForm;
    }

    public static NightWaypointsScreen forCreate(Screen parent, double x, double y, double z) {
        NightWaypointsScreen screen = new NightWaypointsScreen(parent, true);
        screen.mode = MODE_ADD;
        screen.editingId = "";
        screen.draftName = "";
        screen.draftX = Integer.toString((int) Math.round(x));
        screen.draftY = Integer.toString((int) Math.round(y));
        screen.draftZ = Integer.toString((int) Math.round(z));
        ClientRuntime rt = ClientRuntime.get();
        int count = (rt != null && rt.settings() != null && rt.settings().waypoints != null)
                ? rt.settings().waypoints.points.size() : 0;
        screen.draftColor = PALETTE[count % PALETTE.length];
        screen.draftTemp = false;
        return screen;
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

    private boolean isTextFocused() {
        if (mode != MODE_LIST) return true;
        return searchBox != null && searchBox.isFocused();
    }

    private void rebuildScreen() {
        this.setFocused(null);
        @@VANILLA_CLEAR_WIDGETS@@
        init();
    }

    @Override
    protected void init() {
        searchBox = null;
        nameBox = null;
        xBox = null;
        yBox = null;
        zBox = null;
        filterBtn = null;
        tpBtn = null;
        editBtn = null;
        toggleBtn = null;
        shareBtn = null;
        clearTempBtn = null;
        deleteBtn = null;
        tempBtn = null;

        if (mode == MODE_LIST) {
            initListMode();
        } else {
            initFormMode();
        }
    }

    private void initListMode() {
        int gap = 4;
        int maxTotalW = Math.min(376, Math.max(220, this.width - 16));
        int bw = Math.max(52, (maxTotalW - gap * 3) / 4);
        int totalW = bw * 4 + gap * 3;
        int startX = (this.width - totalW) / 2;
        listLeft = startX;
        listRight = startX + totalW;
        listTop = 56;
        listBottom = Math.max(listTop + 44, this.height - 68);

        String filterText = tr(allWorlds ? "waypointsMenu.filter.all" : "waypointsMenu.filter.current");
        int titleHalf = textWidth(tr("waypointsMenu.title")) / 2;
        int availRight = Math.max(44, this.width / 2 - titleHalf - 16);
        int filterW = Math.max(44, Math.min(availRight, textWidth(filterText) + 14));
        filterBtn = makeButton(this.width - filterW - 8, 6, filterW, 20, literal(clip(filterText, filterW - 8)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        allWorlds = !allWorlds;
                        scrollOffset = 0.0;
                        rebuildScreen();
                    }
                });
        addControl(filterBtn);

        String addLabel = "+ " + tr("waypointsMenu.add");
        int addW = Math.max(56, Math.min(96, textWidth(addLabel) + 16));
        int searchW = Math.max(100, totalW - addW - 4);
        searchBox = new EditBox(this.font, listLeft, 30, searchW, 20, literal(tr("waypointsMenu.search_placeholder")));
        searchBox.setMaxLength(32);
        searchBox.setValue(searchQuery);
        updateBoxPlaceholder(searchBox, searchQuery, "waypointsMenu.search_placeholder");
        searchBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                searchQuery = value == null ? "" : value;
                updateBoxPlaceholder(searchBox, searchQuery, "waypointsMenu.search_placeholder");
                rebuildRows();
            }
        });
        addControl(searchBox);

        addControl(makeButton(listLeft + searchW + 4, 30, addW, 20, literal(clip(addLabel, addW - 8)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        openAddForm();
                    }
                }));

        int row1Y = this.height - 52;
        int row2Y = this.height - 28;

        tpBtn = makeButton(startX, row1Y, bw, 20, literal(fitLabel(tr("waypointsMenu.teleport"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        performTeleportSelected();
                    }
                });
        addControl(tpBtn);

        editBtn = makeButton(startX + (bw + gap), row1Y, bw, 20, literal(fitLabel(tr("waypointsMenu.edit"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        openEditSelected();
                    }
                });
        addControl(editBtn);

        toggleBtn = makeButton(startX + (bw + gap) * 2, row1Y, bw, 20, literal(fitLabel(tr("waypointsMenu.toggle"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        toggleSelectedVisibility();
                    }
                });
        addControl(toggleBtn);

        shareBtn = makeButton(startX + (bw + gap) * 3, row1Y, bw, 20, literal(fitLabel(tr("waypointsMenu.share"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        shareSelected();
                    }
                });
        addControl(shareBtn);

        addControl(makeButton(startX, row2Y, bw, 20, literal(fitLabel(tr("waypointsMenu.add"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        openAddForm();
                    }
                }));

        clearTempBtn = makeButton(startX + (bw + gap), row2Y, bw, 20, literal(fitLabel(tr("waypointsMenu.clear_temp"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        clearTempWaypoints();
                    }
                });
        addControl(clearTempBtn);

        deleteBtn = makeButton(startX + (bw + gap) * 2, row2Y, bw, 20, literal(fitLabel(tr("waypointsMenu.delete"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        deleteSelected();
                    }
                });
        addControl(deleteBtn);

        addControl(makeButton(startX + (bw + gap) * 3, row2Y, bw, 20, literal(fitLabel(tr("waypointsMenu.done"), bw)),
                new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        onClose();
                    }
                }));

        rebuildRows();
    }

    private int formFieldWidth() {
        return Math.min(256, Math.max(180, this.width - 32));
    }

    private void initFormMode() {
        int fieldW = formFieldWidth();
        int leftX = (this.width - fieldW) / 2;
        int centerY = this.height / 2;

        final String namePlaceholder = tr("waypointsMenu.placeholder.name");
        nameBox = new EditBox(this.font, leftX, centerY - 54, fieldW, 20, literal(tr("waypointsMenu.field.name")));
        nameBox.setMaxLength(32);
        nameBox.setValue(draftName);
        if (draftName.isEmpty()) nameBox.setSuggestion(namePlaceholder);
        nameBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftName = value == null ? "" : value;
                nameBox.setSuggestion(draftName.isEmpty() ? namePlaceholder : "");
            }
        });
        addControl(nameBox);

        int cGap = 4;
        int cw = (fieldW - cGap * 2) / 3;
        int cw3 = fieldW - cw * 2 - cGap * 2;
        xBox = new EditBox(this.font, leftX, centerY - 16, cw, 20, literal("X"));
        xBox.setMaxLength(10);
        xBox.setValue(draftX);
        xBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftX = value == null ? "" : value;
            }
        });
        addControl(xBox);

        yBox = new EditBox(this.font, leftX + cw + cGap, centerY - 16, cw, 20, literal("Y"));
        yBox.setMaxLength(8);
        yBox.setValue(draftY);
        yBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftY = value == null ? "" : value;
            }
        });
        addControl(yBox);

        zBox = new EditBox(this.font, leftX + (cw + cGap) * 2, centerY - 16, cw3, 20, literal("Z"));
        zBox.setMaxLength(10);
        zBox.setValue(draftZ);
        zBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftZ = value == null ? "" : value;
            }
        });
        addControl(zBox);

        swatchGap = 4;
        swatchSize = Math.min(16, Math.max(10, (fieldW - swatchGap * (PALETTE.length - 1)) / PALETTE.length));
        int totalSw = PALETTE.length * swatchSize + (PALETTE.length - 1) * swatchGap;
        swatchLeft = leftX + (fieldW - totalSw) / 2;
        swatchTop = centerY + 20;

        tempBtn = makeButton(leftX, centerY + 42, fieldW, 20, literal(tempButtonText()), new Button.OnPress() {
            @Override public void onPress(Button b) {
                draftTemp = !draftTemp;
                if (tempBtn != null) tempBtn.setMessage(literal(tempButtonText()));
            }
        });
        addControl(tempBtn);

        int halfW = (fieldW - 6) / 2;
        addControl(makeButton(leftX, centerY + 68, halfW, 20, literal(tr("waypointsMenu.save")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                submitForm();
            }
        }));
        addControl(makeButton(leftX + halfW + 6, centerY + 68, fieldW - halfW - 6, 20,
                literal(tr("waypointsMenu.cancel")), new Button.OnPress() {
                    @Override public void onPress(Button b) {
                        cancelForm();
                    }
                }));

        focusBox(nameBox);
    }

    private String tempButtonText() {
        return tr("waypointsMenu.field.temp") + ": " + tr(draftTemp ? "mapSettings.on" : "mapSettings.off");
    }

    private void openAddForm() {
        double[] pos = WorldFeatureBridge.playerPosition();
        double px = pos != null && pos.length >= 3 ? pos[0] : 0.0;
        double py = pos != null && pos.length >= 3 ? pos[1] : 64.0;
        double pz = pos != null && pos.length >= 3 ? pos[2] : 0.0;
        ClientRuntime rt = ClientRuntime.get();
        int count = (rt != null && rt.settings() != null && rt.settings().waypoints != null)
                ? rt.settings().waypoints.points.size() : 0;
        mode = MODE_ADD;
        editingId = "";
        draftName = "";
        draftX = Integer.toString((int) Math.round(px));
        draftY = Integer.toString((int) Math.round(py));
        draftZ = Integer.toString((int) Math.round(pz));
        draftColor = PALETTE[count % PALETTE.length];
        draftTemp = false;
        rebuildScreen();
    }

    private void openEditSelected() {
        Waypoint wp = selectedWaypoint();
        if (wp == null) return;
        mode = MODE_EDIT;
        editingId = wp.id;
        draftName = wp.label == null ? "" : wp.label;
        draftX = Integer.toString((int) Math.round(wp.x));
        draftY = Integer.toString((int) Math.round(wp.y));
        draftZ = Integer.toString((int) Math.round(wp.z));
        draftColor = wp.color | 0xFF000000;
        draftTemp = wp.temp;
        rebuildScreen();
    }

    private void submitForm() {
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return;
        double x = parseDouble(draftX, 0.0);
        double y = parseDouble(draftY, 64.0);
        double z = parseDouble(draftZ, 0.0);
        String name = draftName == null ? "" : draftName.trim();
        if (name.isEmpty()) name = Math.round(x) + ", " + Math.round(z);

        rt.checkpoint();
        rt.settings().waypoints.enabled = true;
        if (editingId.isEmpty()) {
            Waypoint wp = rt.settings().waypoints.add(name, x, y, z,
                    rt.currentDimension(), rt.currentWorldId(), rt.currentWorldLabel());
            if (wp != null) {
                wp.color = draftColor | 0xFF000000;
                wp.temp = draftTemp;
                selectedId = wp.id;
            }
        } else {
            Waypoint wp = rt.settings().waypoints.find(editingId);
            if (wp != null) {
                wp.label = name;
                wp.x = x;
                wp.y = y;
                wp.z = z;
                wp.color = draftColor | 0xFF000000;
                wp.temp = draftTemp;
            }
        }
        rt.settings().validate();
        rt.changed();

        if (closeAfterForm) {
            onClose();
            return;
        }
        mode = MODE_LIST;
        showStatus(tr("waypointsMenu.status.saved"));
        rebuildScreen();
    }

    private void cancelForm() {
        if (closeAfterForm) {
            onClose();
            return;
        }
        mode = MODE_LIST;
        rebuildScreen();
    }

    private void rebuildRows() {
        rows.clear();
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null || rt.settings().waypoints == null) {
            refreshButtonStates();
            return;
        }
        String curWorld = rt.currentWorldId();
        String curDim = rt.currentDimension();
        String needle = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Waypoint> all = rt.settings().waypoints.points;
        for (int i = 0; i < all.size(); i++) {
            Waypoint wp = all.get(i);
            if (wp == null) continue;
            if (!allWorlds) {
                if (curDim != null && !curDim.isEmpty() && wp.dimension != null
                        && !curDim.equalsIgnoreCase(wp.dimension)) {
                    continue;
                }
                if (curWorld != null && !curWorld.isEmpty() && !wp.matchesWorld(curWorld)) continue;
            }
            if (!needle.isEmpty()) {
                String hay = (wp.displayName() + " " + Math.round(wp.x) + " " + Math.round(wp.z))
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            rows.add(wp);
        }

        boolean found = false;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(selectedId)) {
                found = true;
                break;
            }
        }
        if (!found) {
            selectedId = rows.isEmpty() ? "" : rows.get(0).id;
        }
        clampScroll();
        refreshButtonStates();
    }

    private Waypoint selectedWaypoint() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(selectedId)) return rows.get(i);
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean hasTempWaypoints() {
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null || rt.settings().waypoints == null) return false;
        for (int i = 0; i < rt.settings().waypoints.points.size(); i++) {
            Waypoint wp = rt.settings().waypoints.points.get(i);
            if (wp != null && wp.temp) return true;
        }
        return false;
    }

    private void refreshButtonStates() {
        boolean hasSel = selectedWaypoint() != null;
        if (tpBtn != null) tpBtn.active = hasSel;
        if (editBtn != null) editBtn.active = hasSel;
        if (toggleBtn != null) toggleBtn.active = hasSel;
        if (shareBtn != null) shareBtn.active = hasSel;
        if (deleteBtn != null) deleteBtn.active = hasSel;
        if (clearTempBtn != null) clearTempBtn.active = hasTempWaypoints();
    }

    private void performTeleportSelected() {
        Waypoint wp = selectedWaypoint();
        if (wp == null) return;
        WorldFeatureBridge.sendCommandToServer(
                "tp " + Math.round(wp.x) + " " + Math.round(wp.y) + " " + Math.round(wp.z));
        onClose();
    }

    private void toggleSelectedVisibility() {
        Waypoint wp = selectedWaypoint();
        ClientRuntime rt = ClientRuntime.get();
        if (wp == null || rt == null) return;
        rt.checkpoint();
        wp.visible = !wp.visible;
        rt.changed();
        rebuildRows();
    }

    private void shareSelected() {
        Waypoint wp = selectedWaypoint();
        ClientRuntime rt = ClientRuntime.get();
        if (wp == null || rt == null) return;
        String text = wp.displayName() + " (" + Math.round(wp.x) + " " + Math.round(wp.y) + " "
                + Math.round(wp.z) + ")";
        rt.clipboard(text);
        showStatus(tr("waypointsMenu.status.shared"));
    }

    private void deleteSelected() {
        Waypoint wp = selectedWaypoint();
        ClientRuntime rt = ClientRuntime.get();
        if (wp == null || rt == null || rt.settings() == null) return;
        rt.checkpoint();
        if (rt.settings().waypoints.remove(wp.id)) {
            rt.changed();
            selectedId = "";
            showStatus(tr("waypointsMenu.status.deleted"));
            rebuildRows();
        }
    }

    private void clearTempWaypoints() {
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return;
        rt.checkpoint();
        boolean removed = false;
        Iterator<Waypoint> it = rt.settings().waypoints.points.iterator();
        while (it.hasNext()) {
            Waypoint wp = it.next();
            if (wp != null && wp.temp) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            rt.changed();
            showStatus(tr("waypointsMenu.status.cleared_temp"));
            rebuildRows();
        }
    }

    private void showStatus(String msg) {
        statusText = msg == null ? "" : msg;
        statusUntilMs = System.currentTimeMillis() + 2400L;
    }

    @Override
    public void tick() {
        super.tick();
        @@VANILLA_WP_EDITBOX_TICK@@
    }

    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                @@SET_SCREEN_CLOSE_PARENT@@;
            }
        } else {
            GameBridge.closeWaypointsMenuScreen();
        }
    }

    @Override
    public void @@SCREEN_RENDER_METHOD@@(@@CONTEXT@@ graphics, int mouseX, int mouseY, float delta) {
        @@VANILLA_RENDER_BG@@
        super.@@SCREEN_RENDER_METHOD@@(graphics, mouseX, mouseY, delta);
        if (mode == MODE_LIST) {
            renderListOverlay(graphics, mouseX, mouseY);
        } else {
            renderFormOverlay(graphics);
        }
    }

    private void renderListOverlay(@@CONTEXT@@ graphics, int mouseX, int mouseY) {
        drawCentered(graphics, tr("waypointsMenu.title"), this.width / 2, 10, 0xFFFFFFFF);

        drawFill(graphics, listLeft, listTop, listRight, listBottom, 0xB0000000);
        drawFill(graphics, listLeft, listTop, listRight, listTop + 1, 0xFF555555);
        drawFill(graphics, listLeft, listBottom - 1, listRight, listBottom, 0xFF555555);
        drawFill(graphics, listLeft, listTop, listLeft + 1, listBottom, 0xFF333333);
        drawFill(graphics, listRight - 1, listTop, listRight, listBottom, 0xFF333333);

        if (rows.isEmpty()) {
            drawCentered(graphics, clip(tr("waypointsMenu.empty"), listRight - listLeft - 16),
                    this.width / 2, (listTop + listBottom) / 2 - 4, 0xFFA0A0A0);
        } else {
            double[] pos = WorldFeatureBridge.playerPosition();
            double maxSc = maxScroll();
            boolean hasScrollbar = maxSc > 0.5;
            int rowLeft = listLeft + 4;
            int rowRight = listRight - (hasScrollbar ? 10 : 4);
            int rowW = Math.max(40, rowRight - rowLeft);

            for (int i = 0; i < rows.size(); i++) {
                Waypoint wp = rows.get(i);
                int ry = listTop + 2 + i * ROW_HEIGHT - (int) Math.round(scrollOffset);
                int rh = ROW_HEIGHT - 2;
                if (ry + rh <= listTop + 1 || ry >= listBottom - 1) continue;

                boolean selected = wp.id.equals(selectedId);
                int cTop = Math.max(listTop + 1, ry);
                int cBot = Math.min(listBottom - 1, ry + rh);
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

                int boxX = rowLeft + 5;
                int boxY = ry + (rh - 16) / 2;
                if (boxY >= listTop + 1 && boxY + 16 <= listBottom - 1) {
                    int col = wp.death ? 0xFFEF4444 : (wp.color | 0xFF000000);
                    drawFill(graphics, boxX - 1, boxY - 1, boxX + 17, boxY + 17,
                            wp.visible ? 0xFFFFFFFF : 0xFF3A3A3A);
                    drawFill(graphics, boxX, boxY, boxX + 16, boxY + 16, wp.visible ? col : 0xFF2B2E36);
                    String glyph = wp.death ? "X" : (wp.temp ? "*" : initialOf(wp.displayName()));
                    drawCentered(graphics, glyph, boxX + 8, boxY + 4, wp.visible ? 0xFFFFFFFF : 0xFF8A909C);
                }

                int textX = boxX + 22;
                String stateTag = wp.visible ? "[" + tr("mapSettings.on") + "]" : "[" + tr("mapSettings.off") + "]";
                int tagW = textWidth(stateTag) + 6;
                int maxTextW = Math.max(30, rowW - (textX - rowLeft) - tagW - 6);

                String name = wp.displayName();
                if (wp.temp) name += " *";
                String coords = Math.round(wp.x) + ", " + Math.round(wp.y) + ", " + Math.round(wp.z);
                if (pos != null && pos.length >= 3) {
                    int dist = (int) Math.round(Math.hypot(wp.x - pos[0], wp.z - pos[2]));
                    coords += "  (" + dist + tr("hud.unit.distanceSymbol") + ")";
                }

                int nameColor = !wp.visible ? 0xFF888888 : (selected ? 0xFFFFFFFF : 0xFFE6E6E6);
                if (ry + 3 >= listTop + 1 && ry + 12 <= listBottom - 1) {
                    drawString(graphics, clip(name, maxTextW), textX, ry + 3, nameColor);
                }
                if (ry + 13 >= listTop + 1 && ry + 22 <= listBottom - 1) {
                    drawString(graphics, clip(coords, maxTextW), textX, ry + 13, 0xFF9E9E9E);
                }
                if (ry + 8 >= listTop + 1 && ry + 17 <= listBottom - 1) {
                    drawString(graphics, stateTag, rowRight - tagW + 2, ry + 8,
                            wp.visible ? 0xFF55FF55 : 0xFFFF6666);
                }
            }

            if (hasScrollbar) {
                int sbX = listRight - 6;
                int trackH = Math.max(10, listBottom - listTop - 2);
                int thumbH = Math.max(18, (int) (trackH * (double) trackH / (rows.size() * ROW_HEIGHT + 4)));
                thumbH = Math.min(trackH, thumbH);
                int thumbY = listTop + 1 + (int) ((trackH - thumbH) * (scrollOffset / maxSc));
                drawFill(graphics, sbX, listTop + 1, sbX + 5, listBottom - 1, 0xFF000000);
                drawFill(graphics, sbX, thumbY, sbX + 5, thumbY + thumbH, 0xFF808080);
                drawFill(graphics, sbX, thumbY, sbX + 4, thumbY + thumbH - 1, 0xFFC0C0C0);
            }
        }

        if (!statusText.isEmpty() && System.currentTimeMillis() <= statusUntilMs) {
            drawCentered(graphics, clip(statusText, this.width - 20), this.width / 2, listBottom + 4, 0xFF55FF55);
        } else {
            ClientRuntime rt = ClientRuntime.get();
            String worldLbl = rt != null ? rt.currentWorldLabel() : "";
            String dim = rt != null ? shortDim(rt.currentDimension()) : "";
            String info = (worldLbl != null && !worldLbl.isEmpty() ? worldLbl : "")
                    + (!dim.isEmpty() ? (worldLbl != null && !worldLbl.isEmpty() ? " (" + dim + ")" : dim) : "");
            if (!info.isEmpty()) {
                drawCentered(graphics, clip(info, this.width - 20), this.width / 2, listBottom + 4, 0xFFA8A8A8);
            }
        }
    }

    private void renderFormOverlay(@@CONTEXT@@ graphics) {
        int fieldW = formFieldWidth();
        int leftX = (this.width - fieldW) / 2;
        int centerY = this.height / 2;

        drawCentered(graphics, tr(mode == MODE_EDIT ? "waypointsMenu.edit" : "waypointsMenu.add"),
                this.width / 2, centerY - 82, 0xFFFFFFFF);
        drawString(graphics, clip(tr("waypointsMenu.field.name"), fieldW), leftX, centerY - 66, 0xFFD0D0D0);
        drawString(graphics, clip(tr("waypointsMenu.field.coords"), fieldW), leftX, centerY - 28, 0xFFD0D0D0);
        drawString(graphics, clip(tr("waypointsMenu.field.color"), fieldW), leftX, centerY + 8, 0xFFD0D0D0);

        int curCol = draftColor | 0xFF000000;
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = swatchLeft + i * (swatchSize + swatchGap);
            boolean active = PALETTE[i] == curCol;
            drawFill(graphics, sx, swatchTop, sx + swatchSize, swatchTop + swatchSize,
                    active ? 0xFFFFFFFF : 0xFF3A3A3A);
            drawFill(graphics, sx + 1, swatchTop + 1, sx + swatchSize - 1, swatchTop + swatchSize - 1, PALETTE[i]);
        }
    }

    private boolean handleListClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mode != MODE_LIST) {
            if (mouseY >= swatchTop - 2 && mouseY <= swatchTop + swatchSize + 2) {
                for (int i = 0; i < PALETTE.length; i++) {
                    int sx = swatchLeft + i * (swatchSize + swatchGap);
                    if (mouseX >= sx - 1 && mouseX <= sx + swatchSize + 1) {
                        draftColor = PALETTE[i];
                        playClickSound();
                        return true;
                    }
                }
            }
            return false;
        }
        if (mouseX < listLeft + 2 || mouseX > listRight - 2 || mouseY < listTop + 1 || mouseY > listBottom - 1) {
            return false;
        }
        int relY = (int) Math.floor(mouseY - (listTop + 2) + scrollOffset);
        if (relY < 0) return false;
        int idx = relY / ROW_HEIGHT;
        if (idx < 0 || idx >= rows.size()) return false;

        Waypoint clicked = rows.get(idx);
        int rowLeft = listLeft + 4;
        int boxX = rowLeft + 5;
        if (mouseX >= boxX - 2 && mouseX <= boxX + 18) {
            ClientRuntime rt = ClientRuntime.get();
            if (rt != null) {
                rt.checkpoint();
                clicked.visible = !clicked.visible;
                rt.changed();
            }
            selectedId = clicked.id;
            playClickSound();
            refreshButtonStates();
            return true;
        }

        long now = System.currentTimeMillis();
        boolean isDouble = idx == lastClickIndex && (now - lastClickMs) <= 320L;
        lastClickIndex = idx;
        lastClickMs = now;
        selectedId = clicked.id;
        playClickSound();
        refreshButtonStates();
        if (isDouble) {
            openEditSelected();
        }
        return true;
    }

    private boolean handleListScroll(double mouseX, double mouseY, double vertical) {
        if (mode != MODE_LIST) return false;
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset -= vertical * 18.0;
            clampScroll();
            return true;
        }
        return false;
    }

    private boolean handleCustomKey(int normalizedKey) {
        if (normalizedKey == Keys.ESCAPE) {
            if (mode != MODE_LIST) {
                cancelForm();
            } else {
                onClose();
            }
            return true;
        }
        if (mode != MODE_LIST) {
            if (normalizedKey == Keys.ENTER) {
                submitForm();
                return true;
            }
            return false;
        }
        if (normalizedKey == Keys.UP || normalizedKey == Keys.DOWN) {
            if (rows.isEmpty()) return false;
            int idx = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).id.equals(selectedId)) {
                    idx = i;
                    break;
                }
            }
            idx += normalizedKey == Keys.UP ? -1 : 1;
            if (idx < 0) idx = rows.size() - 1;
            if (idx >= rows.size()) idx = 0;
            selectedId = rows.get(idx).id;
            ensureSelectedVisible();
            refreshButtonStates();
            return true;
        }
        if (normalizedKey == Keys.DELETE && (searchBox == null || !searchBox.isFocused())) {
            deleteSelected();
            return true;
        }
        if (normalizedKey == Keys.ENTER && (searchBox == null || !searchBox.isFocused())) {
            openEditSelected();
            return true;
        }
        return false;
    }

    private void syncFormBoxFocusOnClick(double mx, double my) {
        if (mode == MODE_LIST) {
            if (searchBox != null && !hitBox(searchBox, mx, my)) {
                setBoxFocus(searchBox, false);
            }
            return;
        }
        if (nameBox != null) setBoxFocus(nameBox, hitBox(nameBox, mx, my));
        if (xBox != null) setBoxFocus(xBox, hitBox(xBox, mx, my));
        if (yBox != null) setBoxFocus(yBox, hitBox(yBox, mx, my));
        if (zBox != null) setBoxFocus(zBox, hitBox(zBox, mx, my));
    }

    private static boolean hitBox(EditBox box, double mx, double my) {
        int bx = @@WIDGET_GET_X_BOX@@;
        int by = @@WIDGET_GET_Y_BOX@@;
        return mx >= bx && mx <= bx + box.getWidth() && my >= by && my <= by + box.getHeight();
    }

    private void focusBox(EditBox box) {
        if (box == null) return;
        this.setFocused(box);
        setBoxFocus(box, true);
    }

    private static void setBoxFocus(EditBox box, boolean focused) {
        @@VANILLA_EDITBOX_SET_FOCUS@@
    }

    private double maxScroll() {
        int contentH = rows.size() * ROW_HEIGHT + 4;
        int viewH = Math.max(1, listBottom - listTop);
        return Math.max(0.0, contentH - viewH);
    }

    private void clampScroll() {
        double max = maxScroll();
        if (scrollOffset < 0.0) scrollOffset = 0.0;
        if (scrollOffset > max) scrollOffset = max;
    }

    private void ensureSelectedVisible() {
        int idx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(selectedId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;
        int top = idx * ROW_HEIGHT;
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

    private void updateBoxPlaceholder(EditBox box, String currentValue, String placeholderKey) {
        if (box == null) return;
        if (currentValue != null && !currentValue.isEmpty()) {
            box.setSuggestion("");
        } else {
            box.setSuggestion(clip(tr(placeholderKey), Math.max(20, box.getWidth() - 12)));
        }
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

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String shortDim(String dim) {
        if (dim == null || dim.isEmpty()) return "";
        if (dim.endsWith("overworld")) return tr("dimension.overworld");
        if (dim.endsWith("the_nether")) return tr("dimension.nether");
        if (dim.endsWith("the_end")) return tr("dimension.end");
        int c = dim.indexOf(':');
        return c >= 0 ? dim.substring(c + 1) : dim;
    }

    private static String initialOf(String text) {
        if (text == null || text.trim().isEmpty()) return "W";
        String t = text.trim();
        return new String(Character.toChars(Character.toUpperCase(t.codePointAt(0))));
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

    @@WAYPOINTS_INPUT_METHODS@@
}
