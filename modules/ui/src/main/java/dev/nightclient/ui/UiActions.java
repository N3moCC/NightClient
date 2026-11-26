package dev.nightclient.ui;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface UiActions {
  void changed();

  default void changedLive() {
    changed();
  }

  default long settingsRevision() {
    return -1L;
  }

  String clipboard();

  void clipboard(String value);

  List<String> profiles();

  void saveProfile(String name);

  void loadProfile(String name);

  void deleteProfile(String name);

  String exportProfile();

  void importProfile(String json);

  void reset();

  default void resetTheme() {}

  void undo();

  boolean canUndo();

  void checkpoint();

  void close();

  default void reloadSkin() {}

  default void createSkinExamples() {}

  default dev.nightclient.skin.SkinDocument skinDocument() {
    return null;
  }

  default String skinStatus() {
    return "skin.nc";
  }

  List<Cooldown> cooldowns();

  default List<InventorySlot> inventory() {
    return Collections.emptyList();
  }

  default List<TntView> tnts() {
    return Collections.emptyList();
  }

  default boolean waypointAddHere() {
    return false;
  }

  default double[] playerPosition() {
    return null;
  }

  default String currentDimension() {
    return "";
  }

  default String currentWorldId() {
    return "";
  }

  default String currentWorldLabel() {
    return "";
  }

  default void renderMinimap(
      dev.nightclient.api.Canvas canvas, float x, float y, float w, float h, float scale,
      boolean editorPreview) {}

  default void openWorldMap() {}

  default void openMapSettings() {}

  default void openWaypointsMenu() {}

  default void clearCurrentWorldMapCache() {}

  default void clearAllMapCache() {}

  default List<InventorySlot> armor() {
    return Collections.emptyList();
  }

  default List<PotionView> potions() {
    return Collections.emptyList();
  }

  default VitalsView vitals() {
    return VitalsView.EMPTY;
  }

  default TargetView target() {
    return null;
  }

  default dev.nightclient.api.ViewmodelSelection viewmodelSelection(float mouseX, float mouseY) {
    return dev.nightclient.api.ViewmodelSelection.NONE;
  }

  default void requestViewmodelPick(float mouseX, float mouseY) {}

  default void hoverViewmodel(float mouseX, float mouseY) {}

  default boolean viewmodelEditorSupported() {
    return false;
  }

  default boolean fireVisible() {
    return false;
  }

  default boolean crosshairTargetHit() {
    return false;
  }

  default boolean leftMainArm() {
    return false;
  }

  default void setViewmodelEditing(boolean value) {}

  String gameVersion();

  default String menuKeyLabel() {
    return dev.nightclient.i18n.I18n.text(null, "binding.default.menu");
  }

  default String keyBindingLabel(String id) {
    if ("menu".equals(id)) return menuKeyLabel();
    String key = "binding.default." + id;
    return dev.nightclient.i18n.I18n.text(
        null, dev.nightclient.i18n.I18n.catalog(null).containsKey(key) ? key : "binding.unbound");
  }

  default void setKeyBinding(String id, String nativeKeyName) {}

  default TargetView localPlayer() {
    return null;
  }

  default dev.nightclient.api.PlayerSkin playerPreview() {
    return null;
  }

  default dev.nightclient.api.PlayerSkin capePreview(String capeId) {
    return null;
  }

  default String capePreviewKey(String capeId) {
    return capeId == null ? "" : capeId;
  }

  default void selectOfficialCape(String id) {}

  default void resetCapeToAccount() {}

  default void disableCape() {}

  default void applyCustomCapePath(String path) {}

  default void browseCustomCape() {}

  default void applySkinNickname(String nickname) {}

  default void applyCustomSkinPath(String path) {}

  default void browseCustomSkin() {}

  default void resetSkinToAccount() {}

  default void setCosmeticsSkinModel(String model) {}

  default void selectBuiltinWeapon(String id) {}

  default void selectWeaponMaterial(String id) {}

  default void resetWeaponCosmetic() {}

  default void applyCustomWeaponPath(String path) {}

  default void browseCustomWeapon() {}

  default void setCosmeticsWeaponTarget(String target) {}

  default void setCosmeticsWeaponColor(int argb) {}

  default void selectBuiltinArmor(String id) {}

  default void resetArmorCosmetic() {}

  default void applyCustomArmorPath(String path) {}

  default void browseCustomArmor() {}

  default void setCosmeticsArmorTrigger(String trigger) {}

  default void setCosmeticsArmorColor(int argb) {}

  default boolean cosmeticsLoading() {
    return false;
  }

  default String cosmeticsErrorKey() {
    return "";
  }

  default String cosmeticsSkinSummary() {
    return "";
  }

  default String cosmeticsCapeSummary() {
    return "";
  }

  default String cosmeticsWeaponSummary() {
    return "";
  }

  default String cosmeticsArmorSummary() {
    return "";
  }

  default Map<String, Object> networkInfo() {
    return Collections.emptyMap();
  }

  default int ping() {
    return 0;
  }

  default int fps() {
    return 0;
  }

  default Map<String, Object> keystrokes() {
    return Collections.emptyMap();
  }

  default Map<String, Object> coordinates() {
    return Collections.emptyMap();
  }

  default Map<String, Object> scoreboard() {
    return Collections.emptyMap();
  }

  default List<dev.nightclient.config.SavedAccount> savedAccounts() {
    return Collections.emptyList();
  }

  default String activeAccountName() {
    return "";
  }

  default String launcherAccountName() {
    return "";
  }

  default boolean isLauncherAccountActive() {
    return true;
  }

  default boolean canReconnectServer() {
    return false;
  }

  default String currentServerLabel() {
    return "";
  }

  default String accountErrorKey() {
    return "";
  }

  default void clearAccountError() {}

  default String switchSavedAccount(String id) {
    return "";
  }

  default String quickSwitchAccount(String rawName) {
    return "";
  }

  default boolean restoreLauncherAccount() {
    return false;
  }

  default String saveAccountDraft(String id, String name, String note, String skinName) {
    return "";
  }

  default boolean toggleAccountPin(String id) {
    return false;
  }

  default boolean deleteSavedAccount(String id) {
    return false;
  }

  default String generateRandomAccountName() {
    return dev.nightclient.config.SavedAccount.generateRandomNickname(null);
  }

  default String reconnectCurrentServer() {
    return "";
  }

  default int[] accountFace(String nickname) {
    return dev.nightclient.renderer.player.PlayerFacePainter.fallbackFace(nickname);
  }

  default dev.nightclient.api.PlayerSkin accountPreviewSkin(String nickname) {
    return dev.nightclient.renderer.player.DefaultSkins.classic();
  }

  default boolean openVanillaAccountScreen() {
    return false;
  }

  default List<Map<String, Object>> hwEvents() {
    return Collections.emptyList();
  }

  default boolean hwEventsLoading() {
    return false;
  }

  default String hwEventsSummary() {
    return "";
  }

  default void refreshHwEvents() {}

  default boolean joinHwEvent(String instanceOrServer) {
    return false;
  }

  default String crosshairBlockId() {
    return "";
  }

  default void previewClientSound(String kind) {}

  default void browseTntMp3(boolean explode) {}

  default void clearTntMp3(boolean explode) {}

  String saveStatus();
}
