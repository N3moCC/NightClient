package dev.nightclient.platform;

import dev.nightclient.api.Keys;
import dev.nightclient.config.DisplayProtection;
import dev.nightclient.config.SavedAccount;
import dev.nightclient.config.Settings;
import dev.nightclient.i18n.I18n;
import dev.nightclient.renderer.player.PlayerFacePainter;
import dev.nightclient.runtime.AccountManager;
import dev.nightclient.runtime.ClientRuntime;
import java.util.ArrayList;
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

public final class NightVanillaAccountScreen extends Screen {
    private static final int MODE_LIST = 0;
    private static final int MODE_ADD = 1;
    private static final int MODE_EDIT = 2;
    private static final int ROW_HEIGHT = 26;

    private final Screen parent;
    private int mode = MODE_LIST;
    private String searchQuery = "";
    private String selectedId = "";
    private String editAccountId = "";
    private String draftName = "";
    private String draftNote = "";
    private String draftSkin = "";
    private String statusText = "";
    private boolean statusIsError;
    private double scrollOffset;
    private long lastClickMs;
    private int lastClickIndex = -1;

    private EditBox searchBox;
    private EditBox nameBox;
    private EditBox noteBox;
    private EditBox skinBox;

    private Button switchBtn;
    private Button reconnectBtn;
    private Button editBtn;
    private Button pinBtn;
    private Button deleteBtn;
    private Button quickBtn;

    private int listLeft, listRight, listTop, listBottom;
    private final List<Entry> entries = new ArrayList<Entry>();

    private static final class Entry {
        final String id;
        final String rawName;
        final String displayName;
        final String meta;
        final String skinSource;
        final boolean launcher;
        final boolean active;
        final boolean pinned;

        Entry(String id, String rawName, String displayName, String meta, String skinSource,
              boolean launcher, boolean active, boolean pinned) {
            this.id = id;
            this.rawName = rawName;
            this.displayName = displayName;
            this.meta = meta;
            this.skinSource = skinSource;
            this.launcher = launcher;
            this.active = active;
            this.pinned = pinned;
        }
    }

    public NightVanillaAccountScreen(Screen parent) {
        super(literal(tr("accounts.title")));
        this.parent = parent instanceof NightScreen ? ((NightScreen) parent).parentScreen() : parent;
        syncSelectionToActive();
    }

    public Screen parentScreen() {
        return parent;
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
        return rt != null && rt.settings() != null ? rt.settings().language : "en";
    }

    private static String tr(String key) {
        return I18n.text(lang(), key);
    }

    private static AccountManager manager() {
        ClientRuntime rt = ClientRuntime.get();
        return rt != null ? rt.accountManager() : null;
    }

    private static String maskIfProtected(String raw) {
        String name = raw == null || raw.isEmpty() ? "Player" : raw;
        ClientRuntime rt = ClientRuntime.get();
        if (rt == null || rt.settings() == null) return name;
        Settings s = rt.settings();
        if (s.accountSwitcher != null && s.accountSwitcher.hideAltsInProtect
                && s.protect != null && s.protect.enabled && s.protect.hideNickname) {
            return DisplayProtection.maskNickname(name, name, s.protect);
        }
        return name;
    }

    private void syncSelectionToActive() {
        AccountManager mgr = manager();
        if (mgr == null) {
            selectedId = "";
            return;
        }
        if (mgr.isLauncherActive()) {
            selectedId = "";
            return;
        }
        String active = mgr.activeNickname();
        List<SavedAccount> list = mgr.accounts();
        for (int i = 0; i < list.size(); i++) {
            SavedAccount a = list.get(i);
            if (a.name != null && a.name.equalsIgnoreCase(active)) {
                selectedId = a.id;
                return;
            }
        }
        selectedId = "";
    }

    private void rebuildEntries() {
        entries.clear();
        AccountManager mgr = manager();
        if (mgr == null) return;
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        String activeNick = mgr.activeNickname();
        boolean launcherActive = mgr.isLauncherActive();
        String launcherName = mgr.launcherNickname();
        if (launcherName == null || launcherName.isEmpty()) launcherName = activeNick;
        String launcherDisplay = maskIfProtected(launcherName);
        if (q.isEmpty()
                || launcherName.toLowerCase(Locale.ROOT).contains(q)
                || tr("accounts.launcher_title").toLowerCase(Locale.ROOT).contains(q)) {
            entries.add(new Entry("", launcherName, launcherDisplay, tr("accounts.launcher_title"),
                    launcherName, true, launcherActive, true));
        }

        List<SavedAccount> saved = mgr.accounts();
        for (int i = 0; i < saved.size(); i++) {
            SavedAccount a = saved.get(i);
            if (!q.isEmpty()) {
                String nm = a.name == null ? "" : a.name.toLowerCase(Locale.ROOT);
                String nt = a.note == null ? "" : a.note.toLowerCase(Locale.ROOT);
                if (!nm.contains(q) && !nt.contains(q)) continue;
            }
            boolean isActive = !launcherActive && a.name != null && a.name.equalsIgnoreCase(activeNick);
            String display = maskIfProtected(a.name);
            String meta = a.note != null && !a.note.trim().isEmpty()
                    ? a.note.trim() : tr("accounts.offline_badge");
            String skinSrc = a.skinName != null && !a.skinName.trim().isEmpty() ? a.skinName.trim() : a.name;
            entries.add(new Entry(a.id, a.name, display, meta, skinSrc, false, isActive, a.pinned));
        }

        boolean found = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(selectedId)) {
                found = true;
                break;
            }
        }
        if (!found && !entries.isEmpty()) {
            selectedId = entries.get(0).id;
        }
        clampScroll();
        refreshButtonStates();
    }

    private Entry selectedEntry() {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(selectedId)) return entries.get(i);
        }
        return entries.isEmpty() ? null : entries.get(0);
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
        noteBox = null;
        skinBox = null;
        switchBtn = null;
        reconnectBtn = null;
        editBtn = null;
        pinBtn = null;
        deleteBtn = null;
        quickBtn = null;

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
        int listW = totalW;
        listTop = 56;
        listBottom = Math.max(listTop + 44, this.height - 68);

        String fullMode = tr("accounts.mode_modern_btn");
        String shortMode = tr("accounts.mode_modern");
        int titleHalf = textWidth(tr("accounts.title")) / 2;
        int availRight = Math.max(30, this.width / 2 - titleHalf - 16);
        int fullModeW = textWidth(fullMode) + 14;
        String modeLabel = fullModeW <= availRight ? fullMode : shortMode;
        int modeW = Math.max(30, Math.min(availRight, textWidth(modeLabel) + 14));
        addControl(makeButton(this.width - modeW - 8, 6, modeW, 20, literal(modeLabel), new Button.OnPress() {
            @Override public void onPress(Button b) {
                switchToModernUi();
            }
        }));

        String quickLabel = tr("accounts.quick_switch");
        int quickW = Math.max(54, Math.min(92, textWidth(quickLabel) + 16));
        int searchW = Math.max(100, listW - quickW - 4);
        searchBox = new EditBox(this.font, listLeft, 30, searchW, 20, literal(tr("accounts.search_placeholder")));
        searchBox.setMaxLength(16);
        searchBox.setValue(searchQuery);
        updateBoxPlaceholder(searchBox, searchQuery, "accounts.search_placeholder");
        searchBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                searchQuery = value == null ? "" : value;
                updateBoxPlaceholder(searchBox, searchQuery, "accounts.search_placeholder");
                rebuildEntries();
            }
        });
        addControl(searchBox);

        quickBtn = makeButton(listLeft + searchW + 4, 30, quickW, 20, literal(quickLabel), new Button.OnPress() {
            @Override public void onPress(Button b) {
                performQuickSwitch();
            }
        });
        addControl(quickBtn);

        int row1Y = this.height - 52;
        int row2Y = this.height - 28;

        switchBtn = makeButton(startX, row1Y, bw, 20, literal(tr("accounts.switch_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                performSwitchSelected();
            }
        });
        addControl(switchBtn);

        addControl(makeButton(startX + (bw + gap), row1Y, bw, 20, literal(tr("accounts.add_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                openForm(null);
            }
        }));

        addControl(makeButton(startX + (bw + gap) * 2, row1Y, bw, 20, literal(tr("accounts.random_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                AccountManager mgr = manager();
                if (mgr == null) return;
                String generated = mgr.generateRandomNickname();
                searchQuery = generated;
                if (searchBox != null) {
                    searchBox.setValue(generated);
                    updateBoxPlaceholder(searchBox, generated, "accounts.search_placeholder");
                }
                setStatus("", false);
                rebuildEntries();
            }
        }));

        reconnectBtn = makeButton(startX + (bw + gap) * 3, row1Y, bw, 20, literal(tr("accounts.reconnect_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                performReconnect();
            }
        });
        addControl(reconnectBtn);

        editBtn = makeButton(startX, row2Y, bw, 20, literal(tr("accounts.edit_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                Entry sel = selectedEntry();
                if (sel != null && !sel.launcher) {
                    openForm(sel.id);
                }
            }
        });
        addControl(editBtn);

        pinBtn = makeButton(startX + (bw + gap), row2Y, bw, 20, literal(tr("accounts.pin_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                Entry sel = selectedEntry();
                AccountManager mgr = manager();
                if (sel != null && !sel.launcher && mgr != null) {
                    mgr.togglePin(sel.id);
                    rebuildEntries();
                }
            }
        });
        addControl(pinBtn);

        deleteBtn = makeButton(startX + (bw + gap) * 2, row2Y, bw, 20, literal(tr("accounts.delete_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                performDeleteSelected();
            }
        });
        addControl(deleteBtn);

        addControl(makeButton(startX + (bw + gap) * 3, row2Y, bw, 20, literal(tr("accounts.back_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                onClose();
            }
        }));

        rebuildEntries();
    }

    private int formFieldWidth() {
        return Math.min(256, Math.max(180, this.width - 32));
    }

    private void initFormMode() {
        int fieldW = formFieldWidth();
        int leftX = (this.width - fieldW) / 2;
        int centerY = this.height / 2;

        String rndLabel = tr("accounts.random_btn");
        int rndW = Math.max(66, Math.min(96, textWidth(rndLabel) + 14));
        int nameW = Math.max(88, fieldW - rndW - 4);
        nameBox = new EditBox(this.font, leftX, centerY - 46, nameW, 20, literal(tr("accounts.name_label")));
        nameBox.setMaxLength(16);
        nameBox.setValue(draftName);
        updateBoxPlaceholder(nameBox, draftName, "accounts.name_placeholder");
        nameBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftName = value == null ? "" : value;
                updateBoxPlaceholder(nameBox, draftName, "accounts.name_placeholder");
            }
        });
        addControl(nameBox);

        addControl(makeButton(leftX + nameW + 4, centerY - 46, rndW, 20, literal(rndLabel), new Button.OnPress() {
            @Override public void onPress(Button b) {
                AccountManager mgr = manager();
                if (mgr == null) return;
                draftName = mgr.generateRandomNickname();
                if (nameBox != null) {
                    nameBox.setValue(draftName);
                    updateBoxPlaceholder(nameBox, draftName, "accounts.name_placeholder");
                }
                setStatus("", false);
            }
        }));

        noteBox = new EditBox(this.font, leftX, centerY - 8, fieldW, 20, literal(tr("accounts.note_label")));
        noteBox.setMaxLength(32);
        noteBox.setValue(draftNote);
        updateBoxPlaceholder(noteBox, draftNote, "accounts.note_placeholder");
        noteBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftNote = value == null ? "" : value;
                updateBoxPlaceholder(noteBox, draftNote, "accounts.note_placeholder");
            }
        });
        addControl(noteBox);

        skinBox = new EditBox(this.font, leftX, centerY + 30, fieldW - 26, 20, literal(tr("accounts.skin_label")));
        skinBox.setMaxLength(16);
        skinBox.setValue(draftSkin);
        updateBoxPlaceholder(skinBox, draftSkin, "accounts.skin_placeholder");
        skinBox.setResponder(new java.util.function.Consumer<String>() {
            @Override public void accept(String value) {
                draftSkin = value == null ? "" : value;
                updateBoxPlaceholder(skinBox, draftSkin, "accounts.skin_placeholder");
            }
        });
        addControl(skinBox);

        int halfW = (fieldW - 6) / 2;
        addControl(makeButton(leftX, centerY + 62, halfW, 20, literal(tr("accounts.save_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                submitForm();
            }
        }));

        addControl(makeButton(leftX + halfW + 6, centerY + 62, fieldW - halfW - 6, 20, literal(tr("accounts.cancel_btn")), new Button.OnPress() {
            @Override public void onPress(Button b) {
                mode = MODE_LIST;
                setStatus("", false);
                rebuildScreen();
            }
        }));

        focusBox(nameBox);
    }

    private void openForm(String accountId) {
        setStatus("", false);
        AccountManager mgr = manager();
        if (accountId == null || accountId.isEmpty() || mgr == null) {
            mode = MODE_ADD;
            editAccountId = "";
            draftName = searchQuery != null && SavedAccount.isValidNickname(searchQuery.trim())
                    ? searchQuery.trim() : "";
            draftNote = "";
            draftSkin = "";
        } else {
            SavedAccount existing = mgr.findById(accountId);
            if (existing == null) return;
            mode = MODE_EDIT;
            editAccountId = existing.id;
            draftName = existing.name == null ? "" : existing.name;
            draftNote = existing.note == null ? "" : existing.note;
            draftSkin = existing.skinName == null ? "" : existing.skinName;
        }
        rebuildScreen();
    }

    private void submitForm() {
        AccountManager mgr = manager();
        if (mgr == null) return;
        String err = mgr.saveDraft(editAccountId, draftName, draftNote, draftSkin);
        if (err != null && !err.isEmpty()) {
            setStatus(tr(err), true);
            return;
        }
        String savedName = SavedAccount.normalizeNickname(draftName);
        if (savedName != null) {
            List<SavedAccount> list = mgr.accounts();
            for (int i = 0; i < list.size(); i++) {
                if (savedName.equalsIgnoreCase(list.get(i).name)) {
                    selectedId = list.get(i).id;
                    break;
                }
            }
        }
        mode = MODE_LIST;
        setStatus("", false);
        rebuildScreen();
    }

    private void refreshButtonStates() {
        AccountManager mgr = manager();
        Entry sel = selectedEntry();
        boolean isSaved = sel != null && !sel.launcher;
        if (switchBtn != null) {
            switchBtn.active = sel != null;
            int btnW = switchBtn.getWidth();
            String label = sel != null && sel.launcher
                    ? fitButtonLabel(tr("accounts.restore_btn"), tr("accounts.restore_short_btn"), btnW)
                    : tr("accounts.switch_btn");
            switchBtn.setMessage(literal(label));
        }
        if (reconnectBtn != null) {
            reconnectBtn.active = SessionBridge.canReconnectServer();
        }
        if (editBtn != null) editBtn.active = isSaved;
        if (pinBtn != null) {
            pinBtn.active = isSaved;
            pinBtn.setMessage(literal(isSaved && sel.pinned
                    ? tr("accounts.unpin_btn") : tr("accounts.pin_btn")));
        }
        if (deleteBtn != null) deleteBtn.active = isSaved;
        if (quickBtn != null) {
            quickBtn.active = searchQuery != null && !searchQuery.trim().isEmpty();
        }
    }

    private void setStatus(String text, boolean error) {
        this.statusText = text == null ? "" : text;
        this.statusIsError = error;
    }

    private void switchToModernUi() {
        ClientRuntime rt = ClientRuntime.get();
        if (rt != null && rt.settings() != null) {
            if (rt.settings().accountSwitcher == null) {
                rt.settings().accountSwitcher = new Settings.AccountSwitcher();
            }
            rt.settings().accountSwitcher.layout = "modern";
            rt.changed();
        }
        GameBridge.openAccountManagerModern(parent);
    }

    private void performSwitchSelected() {
        AccountManager mgr = manager();
        Entry sel = selectedEntry();
        if (mgr == null || sel == null) return;
        if (sel.launcher) {
            if (mgr.restoreLauncher()) {
                setStatus(tr("accounts.status.restored"), false);
                rebuildEntries();
                maybeCloseAfterSwitch();
            }
        } else {
            String err = mgr.switchSaved(sel.id);
            if (err != null && !err.isEmpty()) {
                setStatus(tr(err), true);
            } else {
                setStatus(tr("accounts.status.switched") + ": " + maskIfProtected(sel.rawName), false);
                rebuildEntries();
                maybeCloseAfterSwitch();
            }
        }
    }

    private void performQuickSwitch() {
        AccountManager mgr = manager();
        if (mgr == null) return;
        String typed = searchQuery == null ? "" : searchQuery.trim();
        if (typed.isEmpty()) {
            performSwitchSelected();
            return;
        }
        String err = mgr.quickSwitch(typed);
        if (err != null && !err.isEmpty()) {
            setStatus(tr(err), true);
            return;
        }
        searchQuery = "";
        if (searchBox != null) {
            searchBox.setValue("");
            updateBoxPlaceholder(searchBox, "", "accounts.search_placeholder");
        }
        syncSelectionToActive();
        setStatus(tr("accounts.status.switched") + ": " + maskIfProtected(mgr.activeNickname()), false);
        rebuildEntries();
        maybeCloseAfterSwitch();
    }

    private void performReconnect() {
        AccountManager mgr = manager();
        if (mgr == null) return;
        String err = mgr.reconnect();
        if (err != null && !err.isEmpty()) {
            setStatus(tr(err), true);
        }
    }

    private void performDeleteSelected() {
        AccountManager mgr = manager();
        Entry sel = selectedEntry();
        if (mgr == null || sel == null || sel.launcher) return;
        if (mgr.delete(sel.id)) {
            selectedId = "";
            setStatus("", false);
            rebuildEntries();
        }
    }

    private void maybeCloseAfterSwitch() {
        ClientRuntime rt = ClientRuntime.get();
        if (rt != null && rt.settings() != null && rt.settings().accountSwitcher != null
                && rt.settings().accountSwitcher.closeOnSwitch) {
            onClose();
        }
    }

    private static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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
        int idx = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(selectedId)) {
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

    @Override
    public void tick() {
        super.tick();
        @@VANILLA_EDITBOX_TICK@@
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            @@SET_SCREEN_CLOSE_PARENT@@;
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
        drawCentered(graphics, tr("accounts.title"), this.width / 2, 10, 0xFFFFFFFF);

        drawFill(graphics, listLeft, listTop, listRight, listBottom, 0xB0000000);
        drawFill(graphics, listLeft, listTop, listRight, listTop + 1, 0xFF555555);
        drawFill(graphics, listLeft, listBottom - 1, listRight, listBottom, 0xFF555555);
        drawFill(graphics, listLeft, listTop, listLeft + 1, listBottom, 0xFF333333);
        drawFill(graphics, listRight - 1, listTop, listRight, listBottom, 0xFF333333);

        AccountManager mgr = manager();
        if (entries.isEmpty()) {
            String empty = searchQuery != null && !searchQuery.trim().isEmpty()
                    ? tr("accounts.empty_search") : tr("accounts.empty");
            drawCentered(graphics, clip(empty, listRight - listLeft - 16),
                    this.width / 2, (listTop + listBottom) / 2 - 4, 0xFFA0A0A0);
        } else {
            double maxSc = maxScroll();
            boolean hasScrollbar = maxSc > 0.5;
            int rowRight = listRight - (hasScrollbar ? 10 : 4);
            int rowLeft = listLeft + 4;
            int rowW = Math.max(40, rowRight - rowLeft);

            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                int ry = listTop + 2 + i * ROW_HEIGHT - (int) Math.round(scrollOffset);
                int rh = ROW_HEIGHT - 2;
                if (ry + rh <= listTop + 1 || ry >= listBottom - 1) continue;

                boolean selected = e.id.equals(selectedId);
                boolean hovered = mouseX >= rowLeft && mouseX <= rowRight
                        && mouseY >= Math.max(listTop + 1, ry)
                        && mouseY <= Math.min(listBottom - 1, ry + rh);

                int cTop = Math.max(listTop + 1, ry);
                int cBot = Math.min(listBottom - 1, ry + rh);
                if (selected) {
                    drawFill(graphics, rowLeft, cTop, rowRight, cBot, 0xCC121212);
                    if (ry >= listTop + 1) drawFill(graphics, rowLeft, ry, rowRight, ry + 1, 0xFFFFFFFF);
                    if (ry + rh <= listBottom - 1) drawFill(graphics, rowLeft, ry + rh - 1, rowRight, ry + rh, 0xFFFFFFFF);
                    drawFill(graphics, rowLeft, cTop, rowLeft + 1, cBot, 0xFFFFFFFF);
                    drawFill(graphics, rowRight - 1, cTop, rowRight, cBot, 0xFFFFFFFF);
                } else if (hovered) {
                    drawFill(graphics, rowLeft, cTop, rowRight, cBot, 0x55404040);
                }

                int faceX = rowLeft + 5;
                int faceY = ry + (rh - 16) / 2;
                if (faceY >= listTop + 1 && faceY + 16 <= listBottom - 1) {
                    drawFill(graphics, faceX - 1, faceY - 1, faceX + 17, faceY + 17,
                            e.active ? (e.launcher ? 0xFF55FF55 : 0xFFFFD866) : 0xFF3A3A3A);
                    int[] face = mgr != null ? mgr.faceFor(e.skinSource) : PlayerFacePainter.fallbackFace(e.rawName);
                    drawFace(graphics, face, faceX, faceY, 2);
                }

                int textX = faceX + 22;
                String tag = e.active ? "[" + tr("accounts.active_badge") + "]"
                        : (e.launcher ? "[" + tr("accounts.launcher_badge") + "]" : "");
                int tagW = tag.isEmpty() ? 0 : this.font.width(tag) + 6;
                int maxTextW = Math.max(30, rowW - (textX - rowLeft) - tagW - 6);

                String prefix = (!e.launcher && e.pinned) ? "* " : "";
                int nameColor = e.active
                        ? (e.launcher ? 0xFF55FF55 : 0xFFFFD866)
                        : (selected ? 0xFFFFFFFF : 0xFFE6E6E6);
                if (ry + 3 >= listTop + 1 && ry + 12 <= listBottom - 1) {
                    drawString(graphics, clip(prefix + e.displayName, maxTextW), textX, ry + 3, nameColor);
                }
                if (ry + 13 >= listTop + 1 && ry + 22 <= listBottom - 1) {
                    drawString(graphics, clip(e.meta, maxTextW), textX, ry + 13, 0xFF9E9E9E);
                }
                if (!tag.isEmpty() && ry + 8 >= listTop + 1 && ry + 17 <= listBottom - 1) {
                    int tagColor = e.launcher ? 0xFF55FF55 : 0xFFFFD866;
                    drawString(graphics, tag, rowRight - tagW + 2, ry + 8, tagColor);
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
        }

        if (!statusText.isEmpty()) {
            drawCentered(graphics, clip(statusText, this.width - 20),
                    this.width / 2, listBottom + 4, statusIsError ? 0xFFFF6666 : 0xFF55FF55);
        } else if (mgr != null) {
            String activeText = tr("accounts.logged_in_as") + ": " + maskIfProtected(mgr.activeNickname())
                    + " (" + tr(mgr.isLauncherActive() ? "accounts.launcher_badge" : "accounts.offline_badge") + ")";
            drawCentered(graphics, clip(activeText, this.width - 20),
                    this.width / 2, listBottom + 4, 0xFFA8A8A8);
        }
    }

    private void renderFormOverlay(@@CONTEXT@@ graphics) {
        int fieldW = formFieldWidth();
        int leftX = (this.width - fieldW) / 2;
        int centerY = this.height / 2;

        drawCentered(graphics, tr(mode == MODE_EDIT ? "accounts.edit_title" : "accounts.create_title"),
                this.width / 2, centerY - 76, 0xFFFFFFFF);
        drawString(graphics, clip(tr("accounts.name_label"), fieldW), leftX, centerY - 58, 0xFFD0D0D0);
        drawString(graphics, clip(tr("accounts.note_label"), fieldW), leftX, centerY - 20, 0xFFD0D0D0);
        drawString(graphics, clip(tr("accounts.skin_label"), fieldW), leftX, centerY + 18, 0xFFD0D0D0);

        String previewName = !draftSkin.trim().isEmpty() ? draftSkin.trim()
                : (!draftName.trim().isEmpty() ? draftName.trim() : "Player");
        AccountManager mgr = manager();
        int[] face = mgr != null ? mgr.faceFor(previewName) : PlayerFacePainter.fallbackFace(previewName);
        int fx = leftX + fieldW - 18;
        int fy = centerY + 32;
        drawFill(graphics, fx - 1, fy - 1, fx + 17, fy + 17, 0xFF555555);
        drawFace(graphics, face, fx, fy, 2);

        if (!statusText.isEmpty()) {
            drawCentered(graphics, clip(statusText, this.width - 24),
                    this.width / 2, centerY + 88, statusIsError ? 0xFFFF6666 : 0xFF55FF55);
        }
    }

    private boolean handleListClick(double mouseX, double mouseY, int rawButton) {
        if (mode != MODE_LIST || GameBridge.mouseButton(rawButton) != 0) return false;
        if (mouseX < listLeft + 2 || mouseX > listRight - 2 || mouseY < listTop + 1 || mouseY > listBottom - 1) {
            return false;
        }
        int relY = (int) Math.floor(mouseY - (listTop + 2) + scrollOffset);
        if (relY < 0) return false;
        int idx = relY / ROW_HEIGHT;
        if (idx < 0 || idx >= entries.size()) return false;
        Entry clicked = entries.get(idx);
        long now = System.currentTimeMillis();
        boolean isDouble = idx == lastClickIndex && (now - lastClickMs) <= 320L;
        lastClickIndex = idx;
        lastClickMs = now;
        selectedId = clicked.id;
        playClickSound();
        refreshButtonStates();
        if (isDouble) {
            performSwitchSelected();
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
                mode = MODE_LIST;
                setStatus("", false);
                rebuildScreen();
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
        if (normalizedKey == Keys.ENTER) {
            if (searchBox != null && searchBox.isFocused() && !searchQuery.trim().isEmpty()) {
                performQuickSwitch();
            } else {
                performSwitchSelected();
            }
            return true;
        }
        if (normalizedKey == Keys.UP || normalizedKey == Keys.DOWN) {
            if (entries.isEmpty()) return false;
            int idx = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id.equals(selectedId)) {
                    idx = i;
                    break;
                }
            }
            idx += normalizedKey == Keys.UP ? -1 : 1;
            if (idx < 0) idx = entries.size() - 1;
            if (idx >= entries.size()) idx = 0;
            selectedId = entries.get(idx).id;
            ensureSelectedVisible();
            refreshButtonStates();
            return true;
        }
        if (normalizedKey == Keys.DELETE && (searchBox == null || !searchBox.isFocused())) {
            performDeleteSelected();
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
        if (noteBox != null) setBoxFocus(noteBox, hitBox(noteBox, mx, my));
        if (skinBox != null) setBoxFocus(skinBox, hitBox(skinBox, mx, my));
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

    private int textWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return this.font != null ? this.font.width(text) : text.length() * 6;
    }

    private String fitButtonLabel(String full, String shortFallback, int buttonW) {
        int maxTextW = Math.max(20, buttonW - 10);
        if (textWidth(full) <= maxTextW) return full;
        if (shortFallback != null && !shortFallback.isEmpty() && textWidth(shortFallback) <= maxTextW) {
            return shortFallback;
        }
        return clip(shortFallback != null && !shortFallback.isEmpty() ? shortFallback : full, maxTextW);
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

    public static Button createVanillaButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
        return @@VANILLA_MAKE_BUTTON@@;
    }

    private static Button makeButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
        return createVanillaButton(x, y, w, h, label, onPress);
    }

    private void addControl(net.minecraft.client.gui.components.AbstractWidget w) {
        @@VANILLA_ADD_WIDGET@@;
    }

    public static void drawVanillaText(@@CONTEXT@@ graphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        @@VANILLA_TEXT_STATIC@@
    }

    public static void drawVanillaCentered(@@CONTEXT@@ graphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        @@VANILLA_CENTERED_TEXT_STATIC@@
    }

    public static void drawVanillaFill(@@CONTEXT@@ graphics, int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        @@VANILLA_FILL@@
    }

    private void drawFill(@@CONTEXT@@ graphics, int x1, int y1, int x2, int y2, int color) {
        drawVanillaFill(graphics, x1, y1, x2, y2, color);
    }

    private void drawString(@@CONTEXT@@ graphics, String text, int x, int y, int color) {
        drawVanillaText(graphics, this.font, text, x, y, color);
    }

    private void drawCentered(@@CONTEXT@@ graphics, String text, int x, int y, int color) {
        drawVanillaCentered(graphics, this.font, text, x, y, color);
    }

    public static void drawFace(@@CONTEXT@@ graphics, int[] face64, int x, int y, int pixelScale) {
        if (face64 == null || face64.length < 64 || pixelScale <= 0) return;
        for (int row = 0; row < 8; row++) {
            int base = row * 8;
            int ry = y + row * pixelScale;
            int col = 0;
            while (col < 8) {
                int color = face64[base + col] | 0xFF000000;
                int end = col + 1;
                while (end < 8 && (face64[base + end] | 0xFF000000) == color) end++;
                int x1 = x + col * pixelScale;
                int x2 = x + end * pixelScale;
                int y1 = ry;
                int y2 = ry + pixelScale;
                @@VANILLA_FILL@@
                col = end;
            }
        }
    }

    @@VANILLA_INPUT_OVERRIDES@@
}
