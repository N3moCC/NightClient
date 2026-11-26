package dev.nightclient.runtime;

import dev.nightclient.api.Cooldown;
import dev.nightclient.api.InventorySlot;
import dev.nightclient.api.PotionView;
import dev.nightclient.api.TargetView;
import dev.nightclient.api.TntView;
import dev.nightclient.api.VitalsView;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface PlatformServices {
  public static class ContainerSlot {
    public final int index;
    public final String name;
    public final String serverType;
    public final String server;
    public final String lore;
    public final String itemId;

    public ContainerSlot(int index, String name, String serverType, String server) {
      this(index, name, serverType, server, "", "");
    }

    public ContainerSlot(
        int index, String name, String serverType, String server, String lore) {
      this(index, name, serverType, server, lore, "");
    }

    public ContainerSlot(int index, String name, String serverType, String server, String lore,
        String itemId) {
      this.index = index;
      this.name = name != null ? name : "";
      this.serverType = serverType != null ? serverType : "";
      this.server = server != null ? server : "";
      this.lore = lore != null ? lore : "";
      this.itemId = itemId != null ? itemId : "";
    }
  }
  default Path configDirectory() {
    return java.nio.file.Paths.get(".");
  }

  default String gameVersion() {
    return "test";
  }

  default String clipboard() {
    return "";
  }

  default void clipboard(String value) {}

  default void closeMenu() {}

  default List<Cooldown> cooldowns(float partialTick) {
    return Collections.emptyList();
  }

  default String menuKeyLabel() {
    return "R SHIFT";
  }

  default String keyBindingLabel(String id) {
    return "menu".equals(id) ? menuKeyLabel() : "";
  }

  default void setKeyBinding(String id, String nativeKeyName) {}

  default List<InventorySlot> inventory(float partialTick) {
    return Collections.emptyList();
  }

  default List<TntView> tnts(float partialTick, int width, int height) {
    return Collections.emptyList();
  }

  default boolean waypointAddHere() {
    return false;
  }

  default double[] playerPosition() {
    return null;
  }

  default dev.nightclient.renderer.WorldRibbon.View cameraView(int width, int height) {
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

  default String ownNickname() {
    return "";
  }

  default boolean crosshairVisible() {
    return true;
  }

  default boolean crosshairTargetHit() {
    return false;
  }

  default boolean fireVisible() {
    return false;
  }

  default boolean leftMainArm() {
    return false;
  }

  default List<InventorySlot> armor(float partialTick) {
    return Collections.emptyList();
  }

  default List<PotionView> potions(float partialTick) {
    return Collections.emptyList();
  }

  default VitalsView vitals(float partialTick) {
    return VitalsView.EMPTY;
  }

  default TargetView localPlayer(float partialTick) {
    return null;
  }

  default dev.nightclient.api.PlayerSkin playerPreview() {
    return null;
  }

  default TargetView target(float partialTick) {
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

  default void setViewmodelEditing(boolean value) {}

  default int ping() {
    return 0;
  }

  default int fps() {
    return 0;
  }

  default Map<String, Object> networkInfo() {
    return Collections.emptyMap();
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

  default int mainHandCount() {
    return 0;
  }

  default boolean isInCombat() {
    return false;
  }

  default boolean isInLobby() {
    return false;
  }

  default String openContainerTitle() {
    return null;
  }

  default List<String> containerSlotNames() {
    return Collections.emptyList();
  }

  default List<ContainerSlot> containerSlots() {
    return Collections.emptyList();
  }

  default boolean clickContainerSlot(int slotIndex) {
    return false;
  }

  default boolean closeContainer() {
    return false;
  }

  default boolean useSelectorItem() {
    return false;
  }

  default List<dev.nightclient.api.PlayerHealthView> playerHealths(float partialTick) {
    return Collections.emptyList();
  }

  default List<dev.nightclient.api.TotemCounterView> totemCounters(float partialTick) {
    return Collections.emptyList();
  }

  default List<dev.nightclient.api.PlayerPingView> playerPings(float partialTick) {
    return Collections.emptyList();
  }

  default List<ExplosionRingLogic.HazardBadge> explosionBadges(float partialTick) {
    return Collections.emptyList();
  }

  default CombatTagDetector.CombatStatus combatStatus() {
    return CombatTagDetector.CombatStatus.NONE;
  }

  default BetterChatLogic.ChatToastStatus chatToast() {
    return null;
  }

  default void playClientSound(String soundId, float volume, float pitch) {}

  default String crosshairBlockId() {
    return "";
  }

  default void sendChatMessage(String message) {}

  default void sendFeedback(String message) {
    sendChatMessage(message);
  }

  default void sendCommandToServer(String command) {}

  default String lastChatMessage() {
    return "";
  }

  default Object lastChatToken() {
    return null;
  }

  default boolean hasRecentChatMessage(String needle) {
    String last = lastChatMessage();
    return last != null && needle != null
        && last.toLowerCase(java.util.Locale.ROOT)
               .contains(needle.toLowerCase(java.util.Locale.ROOT));
  }

  default Object currentWorldToken() {
    return null;
  }

  default void openWaypointCreate(double x, double y, double z) {}

  default dev.nightclient.api.MapRadarModels.RadarSnapshot mapRadarSnapshot(float partialTick) {
    return dev.nightclient.api.MapRadarModels.RadarSnapshot.EMPTY;
  }

  default void openWorldMap() {}

  default void closeWorldMap() {}

  default boolean isWorldMapOpen() {
    return false;
  }

  default void openMapSettings() {}

  default void closeMapSettings() {}

  default void openWaypointsMenu() {}

  default void closeWaypointsMenu() {}

  default void openHudEditor() {}

  default void rescanLoadedMapChunks() {}

  default void updateCosmetics(int[] skinPixels, int skinW, int skinH, int skinRev,
      boolean capeOverride, boolean capeSuppressed, int[] capePixels, int capeW, int capeH,
      int capeRev, Boolean slimOverride) {}

  default void updateCosmeticModels(
      dev.nightclient.renderer.model.CosmeticModelMesh weaponMesh,
      String weaponTarget, int weaponColor, float weaponScale,
      boolean weaponFirstPerson, boolean weaponThirdPerson,
      dev.nightclient.renderer.model.CosmeticModelMesh armorMesh,
      String armorTrigger, int armorSlotMask, boolean armorHideVanilla,
      int armorColor, float armorScale) {}

  default String chooseImageFile(String title) {
    return null;
  }

  default String chooseModelFile(String title) {
    return null;
  }

  default String chooseAudioFile(String title) {
    return null;
  }

  default String launcherNickname() {
    return ownNickname();
  }

  default boolean isLauncherSessionActive() {
    return true;
  }

  default boolean switchOfflineSession(String nickname, java.util.UUID offlineUuid) {
    return false;
  }

  default boolean restoreLauncherSession() {
    return false;
  }

  default boolean canReconnectServer() {
    return false;
  }

  default String currentServerLabel() {
    return "";
  }

  default boolean reconnectCurrentServer() {
    return false;
  }

  default boolean openVanillaAccountScreen() {
    return false;
  }

  default boolean sendPluginMessage(String channel, byte[] payload) {
    return false;
  }
}
